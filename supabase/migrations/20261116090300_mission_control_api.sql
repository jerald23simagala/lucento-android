-- Mission Control V1, part 4 of 4: the functions Mission Control calls (docs/mission-control/v1/DATABASE_PROPOSAL.md §5)
-- and the database's schedule (§6).
--
-- Every mc_api function below starts with mc.gate() (part 1): an active admin, with a verified authenticator code, within
-- the hourly limit; a fresh code (step-up) for 1-year and Lifetime grants, suspension, deletion, moving a purchase and
-- opening records. Every change and every view of a customer's details writes mc.audit_log in the same transaction.
-- They answer {ok: true, …} or {ok: false, error} and never raise for an expected refusal, so the refusal's record
-- commits. Amounts are Google's micros, returned as text: Mission Control does exact integer arithmetic on them.
--
-- The ingest functions are the only ones anon may call: the webhook and job routes (Resend events, Play orders, the
-- weekly backup's result) pass the ingest secret (part 1), which each checks first.
--
-- Strictly additive: new functions and four pg_cron jobs (only where pg_cron exists).

-- ================================================================================================ helpers
-- Per-table counts and last synced write, for the profile's usage and sync panels.
create or replace function mc.sync_tables(p_user uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  t text;
  v_out jsonb := '[]'::jsonb;
  v_live bigint;
  v_deleted bigint;
  v_last timestamptz;
begin
  foreach t in array array['wallets', 'categories', 'transactions', 'budgets', 'goals', 'goal_contributions', 'recurring'] loop
    execute format(
      'select count(*) filter (where deleted_at is null), count(*) filter (where deleted_at is not null), max(server_updated_at)
       from public.%I where user_id = $1', t)
      into v_live, v_deleted, v_last using p_user;
    v_out := v_out || jsonb_build_object('table', t, 'live', v_live, 'deleted', v_deleted, 'last', v_last);
  end loop;
  return v_out;
end;
$$;

-- Google Play amounts for orders in [p_start, p_end), per currency: 'lifetime' or 'subscriptions'. Test orders never
-- count. Net is what Google reports per order (developerRevenueInBuyerCurrency: after tax, its fee and partial refunds);
-- an order without it is estimated as total − tax − Google's fee (Settings, 15 % by default), and net_reported says
-- whether every order in the sum was reported.
create or replace function mc.order_amounts(p_start timestamptz, p_end timestamptz, p_kind text)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  with rate as (
    select coalesce((select (s.value #>> '{}')::numeric from mc.settings s where s.key = 'google_fee_rate'), 0.15) as r
  )
  select coalesce(jsonb_agg(jsonb_build_object(
      'currency', x.currency, 'orders', x.orders,
      'gross_micros', x.gross::text, 'tax_micros', x.tax::text,
      'net_micros', x.net::text, 'net_reported', x.reported,
      'refunded_micros', x.refunded::text) order by x.gross desc), '[]'::jsonb)
  from (
    select o.currency, count(*) as orders, coalesce(sum(o.total_micros), 0)::numeric as gross,
           coalesce(sum(o.tax_micros), 0)::numeric as tax, coalesce(sum(o.refunded_micros), 0)::numeric as refunded,
           coalesce(sum(coalesce(o.developer_revenue_micros::numeric,
             round((coalesce(o.total_micros, 0) - coalesce(o.tax_micros, 0)) * (1 - (select r from rate))))), 0) as net,
           bool_and(o.developer_revenue_micros is not null) as reported
    from mc.play_orders o
    where not o.test and o.currency is not null and o.ordered_at >= p_start and o.ordered_at < p_end
      and ((p_kind = 'lifetime' and o.product_id = 'lucento_pro_lifetime')
           or (p_kind = 'subscriptions' and o.product_id in ('lucento_pro', 'lucento_cloud')))
    group by o.currency
  ) x
$$;

-- Monthly recurring revenue (DATA_SOURCE_INVENTORY.md): live paid subscriptions (active, in grace, or cancelled but paid
-- up; never trials, holds or pauses), each at its latest known order's price less tax, an annual plan ÷ 12. Lifetime is
-- never part of it. Subscriptions with no order amount yet are counted as unpriced instead of guessed.
create or replace function mc.mrr()
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  with live as (
    select e.purchase_token_hash, e.base_plan_id
    from public.entitlements e
    where e.source in ('PLAY_SUBSCRIPTION', 'PLAY_CLOUD_ADDON') and e.status in ('ACTIVE', 'GRACE', 'CANCELLED_ACTIVE')
      and (e.expires_at is null or e.expires_at > now()) and not e.test_purchase
  ), priced as (
    select l.*, o.currency, o.total_micros - coalesce(o.tax_micros, 0) as net_micros
    from live l
    left join lateral (
      select p.currency, p.total_micros, p.tax_micros from mc.play_orders p
      where p.purchase_token_hash = l.purchase_token_hash and p.total_micros is not null
      order by p.ordered_at desc nulls last limit 1
    ) o on true
  )
  select jsonb_build_object(
    'subscriptions', (select count(*) from live),
    'unpriced', (select count(*) from priced where currency is null),
    'by_currency', coalesce((
      select jsonb_agg(jsonb_build_object('currency', y.currency, 'subscriptions', y.n, 'mrr_micros', y.mrr::text) order by y.mrr desc)
      from (
        select p.currency, count(*) as n,
               round(sum(case when coalesce(p.base_plan_id, '') ~* '(annual|year)' then p.net_micros / 12.0 else p.net_micros end)) as mrr
        from priced p where p.currency is not null group by p.currency
      ) y), '[]'::jsonb)
  )
$$;

-- The revenue numbers shared by Overview and Revenue, for [p_start, p_end).
create or replace function mc.revenue_block(p_start timestamptz, p_end timestamptz)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select jsonb_build_object(
    'orders_connected', exists (select 1 from mc.play_orders),
    'last_orders_sweep', (select max(r.finished_at) from mc.job_runs r where r.job = 'play_orders' and r.result = 'ok'),
    'fee_rate', coalesce((select (s.value #>> '{}')::numeric from mc.settings s where s.key = 'google_fee_rate'), 0.15),
    'lifetime', jsonb_build_object(
      'count', (select count(distinct e.purchase_token_hash) from public.entitlements e
                where e.source = 'PLAY_LIFETIME' and not e.test_purchase and e.status <> 'REVOKED'
                  and e.starts_at >= p_start and e.starts_at < p_end),
      'owners', (select count(distinct e.user_id) from public.entitlements e
                 where e.source = 'PLAY_LIFETIME' and not e.test_purchase and mc.is_live(e.status, e.expires_at)),
      'amounts', mc.order_amounts(p_start, p_end, 'lifetime')),
    'subscriptions', jsonb_build_object(
      'new', (select count(distinct e.purchase_token_hash) from public.entitlements e
              where e.source in ('PLAY_SUBSCRIPTION', 'PLAY_CLOUD_ADDON') and not e.test_purchase
                and e.starts_at >= p_start and e.starts_at < p_end),
      'trials', (select count(*) from public.entitlements e
                 where e.source = 'PLAY_SUBSCRIPTION' and e.status = 'TRIAL' and not e.test_purchase
                   and (e.expires_at is null or e.expires_at > now())),
      'cloud_addon_live', (select count(*) from public.entitlements e
                           where e.source = 'PLAY_CLOUD_ADDON' and not e.test_purchase and mc.is_live(e.status, e.expires_at)
                             and e.status <> 'TRIAL'),
      'amounts', mc.order_amounts(p_start, p_end, 'subscriptions'),
      'mrr', mc.mrr()),
    'complimentary', jsonb_build_object(
      'accounts', (select count(distinct g.user_id) from mc.entitlement_grants g
                   where g.revoked_at is null and g.starts_at <= now() and (g.ends_at is null or g.services_end_at > now())
                     and not mc.is_excluded(g.user_id)),
      'granted_in_period', (select count(*) from mc.entitlement_grants g
                            where g.kind <> 'imported' and g.granted_at >= p_start and g.granted_at < p_end)),
    'refunds', jsonb_build_object(
      'count', (select count(*) from lucento_private.revoked_purchases rp where rp.revoked_at >= p_start and rp.revoked_at < p_end),
      'amounts', coalesce((
        select jsonb_agg(jsonb_build_object('currency', o.currency, 'orders', o.n, 'refunded_micros', o.amount::text))
        from (select p.currency, count(*) as n, coalesce(sum(p.refunded_micros), 0) as amount from mc.play_orders p
              where not p.test and p.refunded_at >= p_start and p.refunded_at < p_end group by p.currency) o), '[]'::jsonb)),
    'test_purchases', (select count(distinct e.purchase_token_hash) from public.entitlements e
                       where e.test_purchase and e.starts_at >= p_start and e.starts_at < p_end)
  )
$$;

-- The account summary a ticket shows beside the conversation.
create or replace function mc.customer_panel(p_user uuid)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select jsonb_build_object(
    'id', u.id, 'email', u.email, 'created_at', u.created_at, 'status', mc.status_of(u.id), 'plan', mc.plan_of(u.id),
    'last_seen_at', mc.last_seen(u.id), 'last_sync_at', mc.last_sync(u.id),
    'client', (select jsonb_build_object('app_version', c.app_version, 'version_code', c.version_code,
                 'android_sdk', c.android_sdk, 'device_model', c.device_model, 'last_seen_at', c.last_seen_at)
               from mc.account_clients c where c.user_id = u.id),
    -- "I bought Pro but it says Free": a purchase made for this account that another account holds.
    'purchases_elsewhere', (select count(*) from public.purchase_links l
                            where l.obfuscated_account_id = encode(sha256(convert_to(lower(u.id::text), 'UTF8')), 'hex')
                              and l.user_id <> u.id),
    'open_tickets', (select count(*) from mc.tickets t where t.user_id = u.id and t.status <> 'resolved')
  )
  from auth.users u where u.id = p_user
$$;

-- ================================================================================================ users
create or replace function mc_api.list_users(
  p_query text default null, p_plan text default null, p_status text default null, p_activity text default null,
  p_version text default null, p_limit integer default 50, p_offset integer default 0
)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('users.list');
  v_limit integer := least(greatest(coalesce(p_limit, 50), 1), 200);
  v_offset integer := greatest(coalesce(p_offset, 0), 0);
  v_q text := nullif(btrim(coalesce(p_query, '')), '');
  v_today_start timestamptz := date_trunc('day', now() at time zone mc.tz()) at time zone mc.tz();
  v_total integer;
  v_rows jsonb;
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  with base as (
    select u.id, u.email, u.created_at, u.raw_user_meta_data ->> 'full_name' as name,
           mc.status_of(u.id) as status, mc.plan_of(u.id) as plan, mc.last_seen(u.id) as last_seen_at,
           c.app_version, c.version_code,
           exists (select 1 from mc.admins a where a.user_id = u.id) as is_admin,
           (select i.label from mc.internal_accounts i where i.user_id = u.id) as internal_label
    from auth.users u
    left join mc.account_clients c on c.user_id = u.id
    where v_q is null or u.email ilike mc.like_any(v_q) or u.id::text = lower(v_q)
  ), filtered as (
    select b.* from base b
    where (p_plan is null or b.plan = p_plan or (p_plan = 'paid' and b.plan in ('lifetime', 'subscription')))
      and (p_status is null or b.status = p_status)
      and (p_version is null or b.app_version = p_version or (p_version = 'not_reported' and b.app_version is null))
      and (p_activity is null
           or (p_activity = 'today' and b.last_seen_at >= v_today_start)
           or (p_activity = '7d' and b.last_seen_at >= now() - interval '7 days')
           or (p_activity = '30d' and b.last_seen_at >= now() - interval '30 days')
           or (p_activity = 'inactive_30d' and (b.last_seen_at is null or b.last_seen_at < now() - interval '30 days')))
  ), numbered as (
    select f.*, row_number() over (order by f.created_at desc, f.id) as rn from filtered f
  )
  select (select count(*) from filtered)::integer,
         coalesce((select jsonb_agg(to_jsonb(n) - 'rn' order by n.rn) from numbered n where n.rn > v_offset and n.rn <= v_offset + v_limit), '[]'::jsonb)
    into v_total, v_rows;
  return jsonb_build_object('ok', true, 'total', v_total, 'rows', v_rows, 'limit', v_limit, 'offset', v_offset,
    'versions', (select coalesce(jsonb_agg(distinct c.app_version), '[]'::jsonb) from mc.account_clients c));
end;
$$;

create or replace function mc_api.user_profile(p_user uuid)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_recent boolean;
  v_refusal text;
  v_u auth.users%rowtype;
  v_hash text;
begin
  -- An open is logged once per admin and account every 10 minutes, and only those count toward the hourly limit, so
  -- reloading a profile while working on it doesn't use the allowance up.
  select exists (select 1 from mc.audit_log l where l.action = 'access.profile_open' and l.admin_id = auth.uid()
                 and l.target_user_id = p_user and l.result = 'ok' and l.at > now() - interval '10 minutes')
    into v_recent;
  v_refusal := mc.gate('access.profile_open', false, case when v_recent then null else 30 end);
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  select * into v_u from auth.users u where u.id = p_user;
  if not found then
    return mc.failed('not_found', 'There is no account with that id');
  end if;
  if not v_recent then
    perform mc.audit('access.profile_open', p_user, null, null, 'ok');
  end if;
  v_hash := encode(sha256(convert_to(lower(p_user::text), 'UTF8')), 'hex');

  return jsonb_build_object(
    'ok', true,
    'account', jsonb_build_object(
      'id', v_u.id, 'email', v_u.email, 'name', coalesce(v_u.raw_user_meta_data ->> 'full_name', v_u.raw_user_meta_data ->> 'name'),
      'created_at', v_u.created_at, 'email_confirmed_at', v_u.email_confirmed_at, 'last_sign_in_at', v_u.last_sign_in_at,
      'banned_until', v_u.banned_until, 'status', mc.status_of(p_user), 'last_seen_at', mc.last_seen(p_user),
      'providers', (select coalesce(jsonb_agg(distinct i.provider), '[]'::jsonb) from auth.identities i where i.user_id = p_user),
      'sessions', (select count(*) from auth.sessions s where s.user_id = p_user),
      'is_admin', exists (select 1 from mc.admins a where a.user_id = p_user),
      'internal_label', (select i.label from mc.internal_accounts i where i.user_id = p_user),
      'deletion', (select jsonb_build_object('id', d.id, 'requested_at', d.requested_at, 'run_after', d.run_after,
                     'reason', d.reason, 'cancelled_at', d.cancelled_at, 'executed_at', d.executed_at,
                     'attempts', d.attempts, 'last_error', d.last_error,
                     'requested_by', (select a.display_name from mc.admins a where a.user_id = d.requested_by))
                   from mc.deletion_requests d where d.user_id = p_user order by d.requested_at desc limit 1)),
    'plan', jsonb_build_object(
      'label', mc.plan_of(p_user),
      'capabilities', (select coalesce(jsonb_agg(c order by c), '[]'::jsonb)
                       from unnest(array['LOCAL_PRO', 'CLOUD', 'AI_PRO', 'SHARED']) c
                       where lucento_private.has_capability(p_user, c))),
    'entitlements', (select coalesce(jsonb_agg(jsonb_build_object(
        'source', e.source, 'status', e.status, 'grants', e.grants, 'product_id', e.product_id, 'base_plan_id', e.base_plan_id,
        'token_hash', e.purchase_token_hash, 'starts_at', e.starts_at, 'expires_at', e.expires_at,
        'verified_at', e.verified_at, 'test_purchase', e.test_purchase, 'live', mc.is_live(e.status, e.expires_at))
        order by mc.is_live(e.status, e.expires_at) desc, e.verified_at desc), '[]'::jsonb)
      from public.entitlements e where e.user_id = p_user),
    'purchases', (select coalesce(jsonb_agg(jsonb_build_object(
        'token_hash', l.purchase_token_hash, 'order_id', l.order_id, 'product_id', l.product_id, 'linked_at', l.linked_at,
        'relinked_count', l.relinked_count,
        'bought', case when l.obfuscated_account_id is null then 'signed_out'
                       when l.obfuscated_account_id = v_hash then 'this_account' else 'other_account' end,
        'refunded', exists (select 1 from lucento_private.revoked_purchases rp where rp.purchase_token_hash = l.purchase_token_hash),
        'order', (select jsonb_build_object('currency', o.currency, 'total_micros', o.total_micros::text, 'tax_micros', o.tax_micros::text,
                    'state', o.state, 'ordered_at', o.ordered_at)
                  from mc.play_orders o where o.order_id = l.order_id))
        order by l.linked_at desc), '[]'::jsonb)
      from public.purchase_links l where l.user_id = p_user),
    'purchases_elsewhere', (select coalesce(jsonb_agg(jsonb_build_object(
        'token_hash', l.purchase_token_hash, 'order_id', l.order_id, 'product_id', l.product_id, 'linked_at', l.linked_at,
        'linked_to', l.user_id, 'linked_to_email', (select u.email from auth.users u where u.id = l.user_id),
        'relinked_count', l.relinked_count)), '[]'::jsonb)
      from public.purchase_links l where l.obfuscated_account_id = v_hash and l.user_id <> p_user),
    'grants', (select coalesce(jsonb_agg(jsonb_build_object(
        'id', g.id, 'kind', g.kind, 'grants', g.grants, 'starts_at', g.starts_at, 'ends_at', g.ends_at,
        'services_end_at', g.services_end_at, 'reason', g.reason, 'note', g.note,
        'ticket', case when g.ticket_id is not null then 'LC-' || g.ticket_id end, 'ticket_id', g.ticket_id,
        'granted_by', (select a.display_name from mc.admins a where a.user_id = g.granted_by), 'granted_at', g.granted_at,
        'revoked_at', g.revoked_at, 'revoked_by', (select a.display_name from mc.admins a where a.user_id = g.revoked_by),
        'revoke_reason', g.revoke_reason,
        'running', g.revoked_at is null and g.starts_at <= now() and (g.ends_at is null or g.services_end_at > now()))
        order by g.granted_at desc), '[]'::jsonb)
      from mc.entitlement_grants g where g.user_id = p_user),
    'sync', jsonb_build_object('last', mc.last_sync(p_user), 'tables', mc.sync_tables(p_user)),
    'usage', jsonb_build_object(
      'wallets', (select count(*) from public.wallets w where w.user_id = p_user and w.deleted_at is null),
      'debts_and_loans', (select count(*) from public.wallets w where w.user_id = p_user and w.deleted_at is null
                          and w.type in ('CREDIT_CARD', 'LOAN', 'RECEIVABLE')),
      'transactions', (select count(*) from public.transactions t where t.user_id = p_user and t.deleted_at is null),
      'budgets', (select count(*) from public.budgets b where b.user_id = p_user and b.deleted_at is null),
      'goals', (select count(*) from public.goals g where g.user_id = p_user and g.deleted_at is null),
      'recurring', (select count(*) from public.recurring r where r.user_id = p_user and r.deleted_at is null),
      'categories', (select count(*) from public.categories c where c.user_id = p_user and c.deleted_at is null),
      'main_currency', (select s.main_currency from public.ledger_settings s where s.user_id = p_user)),
    'shared', jsonb_build_object(
      'owns_space', exists (select 1 from public.shared_spaces s where s.owner_id = p_user),
      'partners', (select count(*) from public.space_members m where m.owner_id = p_user),
      'pending_invites', (select count(*) from public.space_invites i where i.owner_id = p_user and i.status = 'pending'),
      'member_of', (select coalesce(jsonb_agg(jsonb_build_object('owner_id', m.owner_id, 'joined_at', m.joined_at,
                      'owner_email', (select u.email from auth.users u where u.id = m.owner_id))), '[]'::jsonb)
                    from public.space_members m where m.partner_id = p_user)),
    'ai', jsonb_build_object(
      'today', coalesce((select a.requests from public.ai_usage a where a.user_id = p_user and a.day = (now() at time zone 'utc')::date), 0),
      'month', coalesce((select sum(a.requests) from public.ai_usage a where a.user_id = p_user
                         and a.day >= date_trunc('month', now() at time zone 'utc')::date), 0)),
    'client', (select jsonb_build_object('app_version', c.app_version, 'version_code', c.version_code, 'android_sdk', c.android_sdk,
                 'device_model', c.device_model, 'first_seen_at', c.first_seen_at, 'last_seen_at', c.last_seen_at)
               from mc.account_clients c where c.user_id = p_user),
    'activity', jsonb_build_object(
      'days_30', (select count(*) from mc.account_activity a where a.user_id = p_user and a.day > (now() at time zone mc.tz())::date - 30),
      'recent', (select coalesce(jsonb_agg(jsonb_build_object('day', a.day, 'signals', a.signals) order by a.day desc), '[]'::jsonb)
                 from (select * from mc.account_activity x where x.user_id = p_user order by x.day desc limit 35) a)),
    'tickets', (select coalesce(jsonb_agg(jsonb_build_object('id', t.id, 'number', 'LC-' || t.id, 'subject', t.subject,
                  'status', t.status, 'priority', t.priority, 'created_at', t.created_at, 'last_activity_at', t.last_activity_at)
                  order by t.last_activity_at desc), '[]'::jsonb)
                from mc.tickets t where t.user_id = p_user),
    'records_access', (select coalesce(jsonb_agg(jsonb_build_object('opened_at', r.opened_at, 'closed_at', r.closed_at,
                         'reason', r.reason, 'admin', (select a.display_name from mc.admins a where a.user_id = r.admin_id))
                         order by r.opened_at desc), '[]'::jsonb)
                       from (select * from mc.records_access x where x.user_id = p_user order by x.opened_at desc limit 10) r),
    'audit', (select coalesce(jsonb_agg(jsonb_build_object('id', l.id, 'at', l.at, 'action', l.action, 'result', l.result,
                'reason', l.reason, 'target_ref', l.target_ref, 'detail', l.detail,
                'admin', (select a.display_name from mc.admins a where a.user_id = l.admin_id))
                order by l.id desc), '[]'::jsonb)
              from (select * from mc.audit_log x where x.target_user_id = p_user order by x.id desc limit 50) l)
  );
end;
$$;

-- Complimentary Pro (FD-V5): 30 days, 90 days or 1 year of everything, or Lifetime (phone features forever, online
-- services for 365 days, as paid Lifetime). Starts now. A 1-year or Lifetime grant needs a fresh authenticator code.
create or replace function mc_api.grant_pro(p_user uuid, p_kind text, p_reason text, p_note text default null, p_ticket bigint default null)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text;
  v_now timestamptz := now();
  v_ends timestamptz;
  v_services timestamptz;
  v_before jsonb;
  v_after jsonb;
  v_id uuid;
begin
  -- The gate first, whatever the input (nobody but an admin learns how input is checked).
  v_refusal := mc.gate('grants.write', coalesce(p_kind in ('year_1', 'lifetime'), false), 20);
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  if p_kind is null or p_kind not in ('days_30', 'days_90', 'year_1', 'lifetime') then
    return mc.failed('invalid', 'Choose 30 days, 90 days, 1 year or Lifetime');
  end if;
  if p_reason is null or char_length(btrim(p_reason)) < 3 then
    return mc.failed('reason_required', 'Write the reason for this grant');
  end if;
  if char_length(p_reason) > 500 or char_length(coalesce(p_note, '')) > 1000 then
    return mc.failed('invalid', 'The reason or note is too long');
  end if;
  if not exists (select 1 from auth.users u where u.id = p_user) then
    return mc.failed('not_found', 'There is no account with that id');
  end if;
  if exists (select 1 from mc.admins a where a.user_id = p_user) then
    return mc.failed('admin_account', 'Admin logins don''t hold plans');
  end if;
  if mc.status_of(p_user) = 'deletion_scheduled' then
    return mc.failed('deletion_scheduled', 'This account is scheduled for deletion');
  end if;
  if p_ticket is not null and not exists (select 1 from mc.tickets t where t.id = p_ticket) then
    return mc.failed('invalid_ticket', 'There is no ticket LC-' || p_ticket);
  end if;

  v_ends := case p_kind
    when 'days_30' then v_now + interval '30 days'
    when 'days_90' then v_now + interval '90 days'
    when 'year_1' then v_now + interval '1 year'
  end;
  v_services := coalesce(v_ends, v_now + interval '365 days');
  v_before := mc.grant_rows(p_user);
  insert into mc.entitlement_grants (user_id, kind, starts_at, ends_at, services_end_at, reason, note, ticket_id, granted_by, granted_at)
    values (p_user, p_kind, v_now, v_ends, v_services, btrim(p_reason), nullif(btrim(coalesce(p_note, '')), ''), p_ticket, auth.uid(), v_now)
    returning id into v_id;
  v_after := mc.recompute_grants(p_user);
  perform mc.audit('grants.grant', p_user, v_id::text, btrim(p_reason), 'ok', v_before, v_after,
    p_kind || case when p_ticket is not null then ' for LC-' || p_ticket else '' end);
  return jsonb_build_object('ok', true, 'grant_id', v_id, 'ends_at', v_ends, 'services_end_at', v_services, 'rows', v_after);
end;
$$;

-- Ends one complimentary grant now. Paid rows (PLAY_*) are never touched; the answer lists the ones that stay.
create or replace function mc_api.revoke_grant(p_grant uuid, p_reason text)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('grants.write', false, 20);
  v_g mc.entitlement_grants%rowtype;
  v_before jsonb;
  v_after jsonb;
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  if p_reason is null or char_length(btrim(p_reason)) < 3 or char_length(p_reason) > 500 then
    return mc.failed('reason_required', 'Write the reason for ending this grant');
  end if;
  select * into v_g from mc.entitlement_grants g where g.id = p_grant for update;
  if not found then
    return mc.failed('not_found', 'There is no such grant');
  end if;
  if v_g.revoked_at is not null then
    return mc.failed('already_revoked', 'This grant has already ended');
  end if;
  v_before := mc.grant_rows(v_g.user_id);
  update mc.entitlement_grants g set revoked_at = now(), revoked_by = auth.uid(), revoke_reason = btrim(p_reason) where g.id = p_grant;
  v_after := mc.recompute_grants(v_g.user_id);
  perform mc.audit('grants.revoke', v_g.user_id, p_grant::text, btrim(p_reason), 'ok', v_before, v_after, v_g.kind);
  return jsonb_build_object('ok', true, 'rows', v_after,
    'paid_rows_kept', (select count(*) from public.entitlements e
                       where e.user_id = v_g.user_id and e.source <> 'GRANT' and mc.is_live(e.status, e.expires_at)));
end;
$$;

-- Suspension: Auth refuses the account's sign-in and token refresh (banned_until), and its sessions end now, so the
-- refresh tokens die at once; an access token already issued lapses within the hour. The app keeps working on the
-- phone with what it has; sync, AI answers, Shared and restoring purchases from the account stop.
create or replace function mc_api.suspend(p_user uuid, p_reason text)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('accounts.suspend', true, 5);
  v_u auth.users%rowtype;
  v_sessions integer;
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  if p_reason is null or char_length(btrim(p_reason)) < 3 or char_length(p_reason) > 500 then
    return mc.failed('reason_required', 'Write the reason for suspending this account');
  end if;
  select * into v_u from auth.users u where u.id = p_user for update;
  if not found then
    return mc.failed('not_found', 'There is no account with that id');
  end if;
  if exists (select 1 from mc.admins a where a.user_id = p_user) then
    return mc.failed('admin_account', 'Admin logins can''t be suspended here');
  end if;
  if v_u.banned_until is not null and v_u.banned_until > now() then
    return mc.failed('already_suspended', 'This account is already suspended');
  end if;
  update auth.users u set banned_until = now() + interval '100 years' where u.id = p_user;
  delete from auth.sessions s where s.user_id = p_user;
  get diagnostics v_sessions = row_count;
  perform mc.audit('accounts.suspend', p_user, null, btrim(p_reason), 'ok',
    jsonb_build_object('banned_until', v_u.banned_until),
    jsonb_build_object('banned_until', now() + interval '100 years', 'sessions_ended', v_sessions));
  return jsonb_build_object('ok', true, 'sessions_ended', v_sessions);
end;
$$;

create or replace function mc_api.reactivate(p_user uuid, p_reason text)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('accounts.suspend', true, 5);
  v_u auth.users%rowtype;
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  if p_reason is null or char_length(btrim(p_reason)) < 3 or char_length(p_reason) > 500 then
    return mc.failed('reason_required', 'Write the reason for reactivating this account');
  end if;
  select * into v_u from auth.users u where u.id = p_user for update;
  if not found then
    return mc.failed('not_found', 'There is no account with that id');
  end if;
  if v_u.banned_until is null or v_u.banned_until <= now() then
    return mc.failed('not_suspended', 'This account isn''t suspended');
  end if;
  update auth.users u set banned_until = null where u.id = p_user;
  perform mc.audit('accounts.reactivate', p_user, null, btrim(p_reason), 'ok',
    jsonb_build_object('banned_until', v_u.banned_until), jsonb_build_object('banned_until', null));
  return jsonb_build_object('ok', true);
end;
$$;

-- Deletion with a 24-hour cool-off (cancellable), then the same cascade as the in-app Delete account (part 3's job).
-- p_confirm must be the part of the account's email before the @, typed by the admin.
create or replace function mc_api.schedule_deletion(p_user uuid, p_reason text, p_confirm text)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('accounts.delete', true, 3);
  v_u auth.users%rowtype;
  v_expected text;
  v_id uuid;
  v_run timestamptz := now() + interval '24 hours';
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  if p_reason is null or char_length(btrim(p_reason)) < 3 or char_length(p_reason) > 500 then
    return mc.failed('reason_required', 'Write the reason for deleting this account');
  end if;
  select * into v_u from auth.users u where u.id = p_user;
  if not found then
    return mc.failed('not_found', 'There is no account with that id');
  end if;
  if exists (select 1 from mc.admins a where a.user_id = p_user) then
    return mc.failed('admin_account', 'Admin logins can''t be deleted here');
  end if;
  v_expected := coalesce(nullif(split_part(lower(coalesce(v_u.email, '')), '@', 1), ''), 'delete');
  if lower(btrim(coalesce(p_confirm, ''))) <> v_expected then
    return mc.failed('confirm_mismatch', 'Type the part of the email before the @ to confirm');
  end if;
  if exists (select 1 from mc.deletion_requests d where d.user_id = p_user and d.cancelled_at is null and d.executed_at is null) then
    return mc.failed('already_scheduled', 'A deletion is already scheduled for this account');
  end if;
  insert into mc.deletion_requests (user_id, requested_by, reason, run_after)
    values (p_user, auth.uid(), btrim(p_reason), v_run) returning id into v_id;
  perform mc.audit('accounts.deletion_scheduled', p_user, v_id::text, btrim(p_reason), 'pending', null,
    jsonb_build_object('run_after', v_run));
  return jsonb_build_object('ok', true, 'request_id', v_id, 'run_after', v_run);
end;
$$;

create or replace function mc_api.cancel_deletion(p_request uuid, p_reason text)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('accounts.delete_cancel', false, 20);
  v_d mc.deletion_requests%rowtype;
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  if p_reason is null or char_length(btrim(p_reason)) < 3 or char_length(p_reason) > 500 then
    return mc.failed('reason_required', 'Write the reason for cancelling');
  end if;
  select * into v_d from mc.deletion_requests d where d.id = p_request for update;
  if not found then
    return mc.failed('not_found', 'There is no such deletion request');
  end if;
  if v_d.executed_at is not null then
    return mc.failed('already_executed', 'This account has already been deleted');
  end if;
  if v_d.cancelled_at is not null then
    return mc.failed('already_cancelled', 'This deletion was already cancelled');
  end if;
  update mc.deletion_requests d set cancelled_at = now(), cancelled_by = auth.uid(), cancel_reason = btrim(p_reason) where d.id = p_request;
  perform mc.audit('accounts.deletion_cancelled', v_d.user_id, p_request::text, btrim(p_reason), 'ok');
  return jsonb_build_object('ok', true);
end;
$$;

-- Moves a Google Play purchase to another account (the support case DATABASE_PROPOSAL.md §5 calls "resolve a purchase
-- link"): the owner's override of the 30-day rule in apply_play_purchase, recorded with evidence. The purchase's rows
-- move with it; a target account that already holds a live plan of the same kind is refused rather than overwritten.
create or replace function mc_api.resolve_purchase_link(p_token_hash text, p_to_user uuid, p_reason text, p_evidence text)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('purchases.relink', true, 10);
  v_link public.purchase_links%rowtype;
  v_before jsonb;
  v_after jsonb;
  v_conflict text;
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  if p_token_hash is null or p_token_hash !~ '^[0-9a-f]{64}$' then
    return mc.failed('invalid', 'That isn''t a purchase reference');
  end if;
  if p_reason is null or char_length(btrim(p_reason)) < 3 or char_length(p_reason) > 500 then
    return mc.failed('reason_required', 'Write the reason for moving this purchase');
  end if;
  if p_evidence is null or char_length(btrim(p_evidence)) < 10 or char_length(p_evidence) > 500 then
    return mc.failed('evidence_required', 'Describe the evidence (for example the order number the customer sent)');
  end if;
  if not exists (select 1 from auth.users u where u.id = p_to_user) then
    return mc.failed('not_found', 'There is no account with that id');
  end if;
  if exists (select 1 from mc.admins a where a.user_id = p_to_user) then
    return mc.failed('admin_account', 'Admin logins don''t hold plans');
  end if;
  select * into v_link from public.purchase_links l where l.purchase_token_hash = p_token_hash for update;
  if not found then
    return mc.failed('not_found', 'No account holds that purchase');
  end if;
  if v_link.user_id = p_to_user then
    return mc.failed('already_linked', 'That account already holds this purchase');
  end if;
  if exists (select 1 from lucento_private.revoked_purchases rp where rp.purchase_token_hash = p_token_hash) then
    return mc.failed('refunded', 'This purchase was refunded; there is nothing to move');
  end if;
  -- Google Play says it was bought for a different account: moving it here would be undone when that account's phone
  -- next checks it (apply_play_purchase's own rule), so it is refused (security review M4).
  if v_link.obfuscated_account_id is not null
     and v_link.obfuscated_account_id <> encode(sha256(convert_to(lower(p_to_user::text), 'UTF8')), 'hex') then
    return mc.failed('bought_for_other_account', 'Google Play says this was bought for a different account, so it can''t be moved here');
  end if;
  -- The target's own rows of the same kind: one from another purchase that hasn't ended (live, on hold, paused,
  -- pending) stops the move, so a paying customer's plan is never deleted to make room.
  select string_agg(t.source || ' (' || t.status || ')', ', ') into v_conflict
    from public.entitlements m
    join public.entitlements t on t.user_id = p_to_user and t.source = m.source
      and coalesce(t.product_id, '') = coalesce(m.product_id, '')
    where m.user_id = v_link.user_id and m.purchase_token_hash = p_token_hash
      and t.purchase_token_hash is distinct from p_token_hash and t.status not in ('EXPIRED', 'REVOKED');
  if v_conflict is not null then
    return mc.failed('target_has_plan', 'The other account already has ' || v_conflict);
  end if;

  v_before := jsonb_build_object('from_user', v_link.user_id, 'to_user', p_to_user, 'linked_at', v_link.linked_at,
    'relinked_count', v_link.relinked_count,
    'rows', (select coalesce(jsonb_agg(jsonb_build_object('source', e.source, 'status', e.status, 'expires_at', e.expires_at)), '[]'::jsonb)
             from public.entitlements e where e.user_id = v_link.user_id and e.purchase_token_hash = p_token_hash),
    -- The target's ended rows of the same kind that the move replaces.
    'replaced', (select coalesce(jsonb_agg(jsonb_build_object('source', t.source, 'status', t.status, 'expires_at', t.expires_at,
                   'token_hash', t.purchase_token_hash)), '[]'::jsonb)
                 from public.entitlements m
                 join public.entitlements t on t.user_id = p_to_user and t.source = m.source
                   and coalesce(t.product_id, '') = coalesce(m.product_id, '')
                 where m.user_id = v_link.user_id and m.purchase_token_hash = p_token_hash
                   and t.purchase_token_hash is distinct from p_token_hash));
  delete from public.entitlements t
    using public.entitlements m
    where t.user_id = p_to_user and m.user_id = v_link.user_id and m.purchase_token_hash = p_token_hash
      and t.source = m.source and coalesce(t.product_id, '') = coalesce(m.product_id, '')
      and t.purchase_token_hash is distinct from p_token_hash;
  update public.entitlements e set user_id = p_to_user, verified_at = now()
    where e.user_id = v_link.user_id and e.purchase_token_hash = p_token_hash;
  update public.purchase_links l
    set user_id = p_to_user, linked_at = now(), relinked_count = l.relinked_count + 1
    where l.purchase_token_hash = p_token_hash;
  v_after := jsonb_build_object('user', p_to_user,
    'rows', (select coalesce(jsonb_agg(jsonb_build_object('source', e.source, 'status', e.status, 'expires_at', e.expires_at)), '[]'::jsonb)
             from public.entitlements e where e.user_id = p_to_user and e.purchase_token_hash = p_token_hash));
  perform mc.audit('purchases.relink_out', v_link.user_id, p_token_hash, btrim(p_reason), 'ok', v_before, v_after, left(btrim(p_evidence), 1000));
  perform mc.audit('purchases.relink_in', p_to_user, p_token_hash, btrim(p_reason), 'ok', v_before, v_after, left(btrim(p_evidence), 1000));
  return jsonb_build_object('ok', true, 'from_user', v_link.user_id, 'rows', v_after -> 'rows');
end;
$$;

-- Marks an account as internal (the founder's own, a test account): left out of every metric. Null label: a customer again.
create or replace function mc_api.set_internal(p_user uuid, p_label text, p_reason text)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('accounts.internal', false, 30);
  v_before text;
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  if p_reason is null or char_length(btrim(p_reason)) < 3 or char_length(p_reason) > 500 then
    return mc.failed('reason_required', 'Write the reason');
  end if;
  if not exists (select 1 from auth.users u where u.id = p_user) then
    return mc.failed('not_found', 'There is no account with that id');
  end if;
  select i.label into v_before from mc.internal_accounts i where i.user_id = p_user;
  if nullif(btrim(coalesce(p_label, '')), '') is null then
    delete from mc.internal_accounts i where i.user_id = p_user;
  else
    if char_length(btrim(p_label)) > 60 then
      return mc.failed('invalid', 'Keep the label under 60 characters');
    end if;
    insert into mc.internal_accounts as i (user_id, label, added_by) values (p_user, btrim(p_label), auth.uid())
      on conflict (user_id) do update set label = excluded.label;
  end if;
  perform mc.audit('accounts.internal', p_user, null, btrim(p_reason), 'ok',
    jsonb_build_object('label', v_before), jsonb_build_object('label', nullif(btrim(coalesce(p_label, '')), '')));
  return jsonb_build_object('ok', true);
end;
$$;

-- ================================================================================================ overview
-- Everything on the Overview page for the days p_from..p_to (inclusive, in Mission Control's time zone), with the
-- same number of days before it for comparison. Each block names its source; a source that isn't connected says so.
create or replace function mc_api.overview(p_from date default null, p_to date default null)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('overview.read');
  v_tz text := mc.tz();
  v_today date := (now() at time zone mc.tz())::date;
  v_from date;
  v_to date;
  v_days integer;
  v_start timestamptz;
  v_end timestamptz;
  v_pstart timestamptz;
  v_month_start timestamptz;
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  v_to := least(coalesce(p_to, v_today), v_today);
  v_from := coalesce(p_from, v_to - 29);
  if v_from > v_to then
    return mc.failed('invalid', 'The start date is after the end date');
  end if;
  if v_to - v_from > 400 then
    return mc.failed('invalid', 'Choose at most 400 days');
  end if;
  v_days := v_to - v_from + 1;
  v_start := v_from::timestamp at time zone v_tz;
  v_end := (v_to + 1)::timestamp at time zone v_tz;
  v_pstart := (v_from - v_days)::timestamp at time zone v_tz;
  v_month_start := date_trunc('month', v_today::timestamp) at time zone v_tz;

  -- Today's activity is only as fresh as the last hourly run: refresh it when that's more than 10 minutes old.
  if not exists (select 1 from mc.job_runs r where r.job = 'activity' and r.result = 'ok' and r.started_at > now() - interval '10 minutes') then
    perform mc.capture_activity();
  end if;

  return jsonb_build_object(
    'ok', true,
    'range', jsonb_build_object('from', v_from, 'to', v_to, 'days', v_days, 'prev_from', v_from - v_days, 'prev_to', v_from - 1,
                                'timezone', v_tz),
    'users', jsonb_build_object(
      'total', (select count(*) from auth.users u where not mc.is_excluded(u.id)),
      'new_today', (select count(*) from auth.users u where u.created_at >= v_today::timestamp at time zone v_tz and not mc.is_excluded(u.id)),
      'new_this_month', (select count(*) from auth.users u where u.created_at >= v_month_start and not mc.is_excluded(u.id)),
      'new_in_period', (select count(*) from auth.users u where u.created_at >= v_start and u.created_at < v_end and not mc.is_excluded(u.id)),
      'new_prev_period', (select count(*) from auth.users u where u.created_at >= v_pstart and u.created_at < v_start and not mc.is_excluded(u.id)),
      'unconfirmed', (select count(*) from auth.users u where u.email_confirmed_at is null and not mc.is_excluded(u.id)),
      'dau', (select count(distinct a.user_id) from mc.account_activity a where a.day = v_today and not mc.is_excluded(a.user_id)),
      'dau_yesterday', (select count(distinct a.user_id) from mc.account_activity a where a.day = v_today - 1 and not mc.is_excluded(a.user_id)),
      'mau', (select count(distinct a.user_id) from mc.account_activity a where a.day > v_today - 30 and not mc.is_excluded(a.user_id)),
      -- The first run that really captured (not one paused while extended data was off).
      'activity_since', (select min(r.started_at) from mc.job_runs r where r.job = 'activity' and r.detail ->> 'paused' is null),
      'active_in_period', (select count(distinct a.user_id) from mc.account_activity a where a.day between v_from and v_to and not mc.is_excluded(a.user_id)),
      'plans', (select jsonb_object_agg(p.plan, p.n) from (
                  select mc.plan_of(u.id) as plan, count(*) as n from auth.users u where not mc.is_excluded(u.id) group by 1) p)),
    'series', jsonb_build_object(
      'registrations', (select coalesce(jsonb_agg(jsonb_build_object('day', d.day, 'value', coalesce(r.n, 0)) order by d.day), '[]'::jsonb)
                        from (select generate_series(v_from::timestamp, v_to::timestamp, interval '1 day')::date as day) d
                        left join (select (u.created_at at time zone v_tz)::date as day, count(*) as n from auth.users u
                                   where u.created_at >= v_start and u.created_at < v_end and not mc.is_excluded(u.id) group by 1) r
                          on r.day = d.day),
      -- Active accounts per day: the activity log for its 35 days, the nightly totals before that.
      'active', (select coalesce(jsonb_agg(jsonb_build_object('day', d.day, 'value',
                   case when d.day > v_today - 35 then (select count(distinct a.user_id) from mc.account_activity a
                                                         where a.day = d.day and not mc.is_excluded(a.user_id))
                        else (select m.value from mc.daily_metrics m where m.day = d.day and m.key = 'dau') end) order by d.day), '[]'::jsonb)
                 from (select generate_series(v_from::timestamp, v_to::timestamp, interval '1 day')::date as day) d)),
    'revenue', mc.revenue_block(v_start, v_end),
    'revenue_prev', mc.revenue_block(v_pstart, v_start),
    'ops', jsonb_build_object(
      'extended_data_enabled', mc.setting_on('extended_data_enabled'),
      'tickets_open', (select count(*) from mc.tickets t where t.status in ('open', 'in_progress')),
      'tickets_awaiting', (select count(*) from mc.tickets t where t.status = 'awaiting_customer'),
      'tickets_urgent', (select count(*) from mc.tickets t where t.status <> 'resolved' and t.priority = 'urgent'),
      'emails_today', (select count(*) from mc.email_events e where e.type in ('email.sent', 'email.received')
                       and e.at >= date_trunc('day', now() at time zone 'utc') at time zone 'utc'),
      'email_daily_limit', mc.setting_int('resend_daily_limit', 100),
      'email_webhook_seen', (select max(e.received_at) from mc.email_events e),
      'services', (select coalesce(jsonb_object_agg(s.service, jsonb_build_object(
                     'status', case when s.checked_at < now() - interval '10 minutes' then 'unknown' else s.status end,
                     'checked_at', s.checked_at, 'detail', s.detail)), '{}'::jsonb) from mc.service_checks s),
      'last_backup', (select jsonb_build_object('at', coalesce(r.finished_at, r.started_at), 'result', r.result, 'detail', r.detail)
                      from mc.job_runs r where r.job = 'backup' order by r.started_at desc limit 1))
  );
end;
$$;

-- Small counts for the navigation: open tickets, services that need a look.
create or replace function mc_api.nav_badges()
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('nav.read');
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  return jsonb_build_object('ok', true,
    'extended_data_enabled', mc.setting_on('extended_data_enabled'),
    'open_tickets', (select count(*) from mc.tickets t where t.status in ('open', 'in_progress')),
    'services_attention', (select count(*) from mc.service_checks s
                           where s.status in ('degraded', 'down') and s.checked_at >= now() - interval '10 minutes'),
    'records_view_enabled', coalesce((select (s.value #>> '{}')::boolean from mc.settings s where s.key = 'records_view_enabled'), false));
end;
$$;

-- ================================================================================================ support
create or replace function mc_api.tickets_list(
  p_status text default null, p_query text default null, p_limit integer default 50, p_offset integer default 0
)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('support.read');
  v_limit integer := least(greatest(coalesce(p_limit, 50), 1), 200);
  v_offset integer := greatest(coalesce(p_offset, 0), 0);
  v_q text := nullif(btrim(coalesce(p_query, '')), '');
  v_number bigint;
  v_rows jsonb;
  v_total integer;
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  if v_q ~* '^(lc-)?\d{1,12}$' then
    v_number := regexp_replace(v_q, '\D', '', 'g')::bigint;
  end if;
  with base as (
    select t.*, coalesce(u.email, t.requester_email) as customer_email
    from mc.tickets t left join auth.users u on u.id = t.user_id
    where (p_status is null or t.status = p_status or (p_status = 'active' and t.status <> 'resolved'))
      and (v_q is null or t.id = v_number
           or coalesce(u.email, t.requester_email) ilike mc.like_any(v_q)
           or t.subject ilike mc.like_any(v_q)
           or exists (select 1 from mc.ticket_messages m where m.ticket_id = t.id and m.body_text ilike mc.like_any(v_q)))
  )
  select (select count(*) from base)::integer,
         coalesce((select jsonb_agg(jsonb_build_object('id', b.id, 'number', 'LC-' || b.id, 'channel', b.channel, 'topic', b.topic,
                     'subject', b.subject, 'status', b.status, 'priority', b.priority, 'customer_email', b.customer_email, 'verified', b.requester_verified,
                     'user_id', b.user_id, 'created_at', b.created_at, 'last_activity_at', b.last_activity_at,
                     'last_kind', (select m.kind from mc.ticket_messages m where m.ticket_id = b.id and m.kind <> 'note' order by m.id desc limit 1))
                     order by b.last_activity_at desc)
                   from (select * from base order by last_activity_at desc limit v_limit offset v_offset) b), '[]'::jsonb)
    into v_total, v_rows;
  return jsonb_build_object('ok', true, 'total', v_total, 'rows', v_rows,
    'summary', jsonb_build_object(
      'open', (select count(*) from mc.tickets t where t.status = 'open'),
      'in_progress', (select count(*) from mc.tickets t where t.status = 'in_progress'),
      'awaiting_customer', (select count(*) from mc.tickets t where t.status = 'awaiting_customer'),
      'resolved_7d', (select count(*) from mc.tickets t where t.status = 'resolved' and t.resolved_at > now() - interval '7 days'),
      'median_first_response_minutes', (select round(extract(epoch from percentile_cont(0.5) within group (order by t.first_response_at - t.created_at)) / 60)
                                        from mc.tickets t where t.first_response_at is not null and t.created_at > now() - interval '30 days')));
end;
$$;

create or replace function mc_api.ticket_get(p_ticket bigint)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('support.read');
  v_t mc.tickets%rowtype;
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  select * into v_t from mc.tickets t where t.id = p_ticket;
  if not found then
    return mc.failed('not_found', 'There is no ticket LC-' || coalesce(p_ticket::text, '?'));
  end if;
  return jsonb_build_object('ok', true,
    'ticket', jsonb_build_object('id', v_t.id, 'number', 'LC-' || v_t.id, 'channel', v_t.channel, 'topic', v_t.topic,
      'subject', v_t.subject, 'status', v_t.status, 'priority', v_t.priority, 'user_id', v_t.user_id,
      'requester_email', v_t.requester_email, 'verified', v_t.requester_verified, 'diagnostics', v_t.diagnostics, 'source_note', v_t.source_note,
      'created_at', v_t.created_at, 'first_response_at', v_t.first_response_at, 'resolved_at', v_t.resolved_at,
      'last_activity_at', v_t.last_activity_at,
      'reply_to', coalesce((select u.email from auth.users u where u.id = v_t.user_id), v_t.requester_email)),
    'messages', (select coalesce(jsonb_agg(jsonb_build_object('id', m.id, 'kind', m.kind, 'body', m.body_text,
                   'author', (select a.display_name from mc.admins a where a.user_id = m.author_admin_id),
                   'recipient', m.recipient, 'delivery', m.delivery, 'error', m.error, 'created_at', m.created_at) order by m.id), '[]'::jsonb)
                 from mc.ticket_messages m where m.ticket_id = v_t.id),
    'customer', case when v_t.user_id is not null then mc.customer_panel(v_t.user_id) end,
    -- A request sent signed out, from an address that has an account: offer to link it.
    'matching_account', case when v_t.user_id is null and v_t.requester_email is not null then
                          (select jsonb_build_object('id', u.id, 'email', u.email) from auth.users u
                           where lower(u.email) = lower(v_t.requester_email) limit 1) end);
end;
$$;

-- A ticket the admin logs by hand: an email to lucentoapp@gmail.com, a Play review, a message elsewhere. An address that
-- belongs to an account is linked to it.
create or replace function mc_api.ticket_create(
  p_email text, p_topic text, p_subject text, p_body text, p_source_note text default null, p_priority text default 'normal'
)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('support.write', false, 120);
  v_email text := lower(btrim(coalesce(p_email, '')));
  v_user uuid;
  v_id bigint;
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  if char_length(v_email) > 254 or v_email !~ '^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$' then
    return mc.failed('invalid_email', 'Enter the customer''s email address');
  end if;
  if p_topic is null or p_topic not in ('account', 'billing', 'sync', 'data_recovery', 'bug', 'idea', 'other') then
    return mc.failed('invalid', 'Choose a topic');
  end if;
  if p_subject is null or char_length(btrim(p_subject)) not between 1 and 200 then
    return mc.failed('invalid', 'Write a subject (up to 200 characters)');
  end if;
  if p_body is null or char_length(btrim(p_body)) not between 1 and 5000 then
    return mc.failed('invalid', 'Paste the message (up to 5,000 characters)');
  end if;
  if coalesce(p_priority, 'normal') not in ('normal', 'urgent') then
    return mc.failed('invalid', 'Unknown priority');
  end if;
  select u.id into v_user from auth.users u where lower(u.email) = v_email limit 1;
  insert into mc.tickets (channel, user_id, requester_email, topic, subject, priority, source_note, created_by)
    values ('manual', v_user, case when v_user is null then v_email end, p_topic, btrim(p_subject), coalesce(p_priority, 'normal'),
            nullif(left(btrim(coalesce(p_source_note, '')), 200), ''), auth.uid())
    returning id into v_id;
  insert into mc.ticket_messages (ticket_id, kind, body_text) values (v_id, 'inbound', btrim(p_body));
  perform mc.audit('support.ticket_create', v_user, 'LC-' || v_id, null, 'ok');
  return jsonb_build_object('ok', true, 'id', v_id, 'number', 'LC-' || v_id, 'linked', v_user is not null);
end;
$$;

-- Adds to the conversation: a reply (sent by email by Mission Control right after this call, which records the result
-- with ticket_reply_result), a note (never sent), or an inbound message (the customer's emailed answer, pasted in).
-- A reply moves the ticket to Awaiting customer; an inbound message moves an awaiting ticket back to Open.
create or replace function mc_api.ticket_add_message(p_ticket bigint, p_kind text, p_body text)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text;
  v_t mc.tickets%rowtype;
  v_recipient text;
  v_id bigint;
begin
  v_refusal := mc.gate(case when p_kind = 'reply' then 'support.reply' else 'support.write' end, false,
                       case when p_kind = 'reply' then 40 else 120 end);
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  if p_kind is null or p_kind not in ('reply', 'note', 'inbound') then
    return mc.failed('invalid', 'Unknown message kind');
  end if;
  if p_body is null or char_length(btrim(p_body)) not between 1 and 5000 then
    return mc.failed('invalid', 'Write the message (up to 5,000 characters)');
  end if;
  select * into v_t from mc.tickets t where t.id = p_ticket for update;
  if not found then
    return mc.failed('not_found', 'There is no ticket LC-' || coalesce(p_ticket::text, '?'));
  end if;
  if p_kind = 'reply' then
    v_recipient := coalesce((select u.email from auth.users u where u.id = v_t.user_id), v_t.requester_email);
    if v_recipient is null then
      return mc.failed('no_address', 'This ticket has no email address to reply to');
    end if;
  end if;
  insert into mc.ticket_messages (ticket_id, kind, author_admin_id, body_text, recipient, delivery)
    values (v_t.id, p_kind, case when p_kind <> 'inbound' then auth.uid() end, btrim(p_body), v_recipient,
            case when p_kind = 'reply' then 'sending' end)
    returning id into v_id;
  update mc.tickets t set
    last_activity_at = now(),
    first_response_at = case when p_kind = 'reply' then coalesce(t.first_response_at, now()) else t.first_response_at end,
    status = case when p_kind = 'reply' then 'awaiting_customer'
                  when p_kind = 'inbound' and t.status in ('awaiting_customer', 'resolved') then 'open'
                  else t.status end,
    resolved_at = case when p_kind = 'inbound' and t.status = 'resolved' then null else t.resolved_at end
    where t.id = v_t.id;
  perform mc.audit('support.' || p_kind, v_t.user_id, 'LC-' || v_t.id, null, case when p_kind = 'reply' then 'pending' else 'ok' end);
  return jsonb_build_object('ok', true, 'message_id', v_id, 'recipient', v_recipient, 'number', 'LC-' || v_t.id,
    -- A subject the sender typed goes back only to a verified sender: Lucento's domain never carries an unverified
    -- stranger's words to someone's inbox (security review L7).
    'subject', '[LC-' || v_t.id || '] ' || case when v_t.requester_verified then v_t.subject else 'Your message to Lucento Support' end);
end;
$$;

-- Sending a reply again (its email failed or was never confirmed): counted against the same hourly limit and audited
-- before Mission Control sends it (security review L5).
create or replace function mc_api.ticket_reply_retry(p_message bigint)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('support.reply', false, 40);
  v_m mc.ticket_messages%rowtype;
  v_t mc.tickets%rowtype;
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  select * into v_m from mc.ticket_messages m where m.id = p_message and m.kind = 'reply' for update;
  if not found then
    return mc.failed('not_found', 'There is no such reply');
  end if;
  if v_m.delivery is not null and v_m.delivery not in ('failed', 'sending') then
    return mc.failed('already_sent', 'That reply was already sent');
  end if;
  select * into v_t from mc.tickets t where t.id = v_m.ticket_id;
  update mc.ticket_messages m set delivery = 'sending', error = null where m.id = v_m.id;
  perform mc.audit('support.reply_retry', v_t.user_id, 'LC-' || v_t.id, null, 'pending');
  return jsonb_build_object('ok', true, 'message_id', v_m.id, 'recipient', v_m.recipient, 'body', v_m.body_text,
    'number', 'LC-' || v_t.id,
    'subject', '[LC-' || v_t.id || '] ' || case when v_t.requester_verified then v_t.subject else 'Your message to Lucento Support' end);
end;
$$;

-- Deletes a ticket that is spam, with its messages (security review M2). The audit keeps that it happened, not what
-- it said.
create or replace function mc_api.ticket_delete(p_ticket bigint, p_reason text)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('support.delete', false, 120);
  v_t mc.tickets%rowtype;
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  if p_reason is null or char_length(btrim(p_reason)) < 3 or char_length(p_reason) > 500 then
    return mc.failed('reason_required', 'Write why this ticket is being deleted');
  end if;
  select * into v_t from mc.tickets t where t.id = p_ticket for update;
  if not found then
    return mc.failed('not_found', 'There is no ticket LC-' || coalesce(p_ticket::text, '?'));
  end if;
  delete from mc.tickets t where t.id = v_t.id;
  perform mc.audit('support.ticket_delete', v_t.user_id, 'LC-' || v_t.id, btrim(p_reason), 'ok', null, null, v_t.channel);
  return jsonb_build_object('ok', true);
end;
$$;

-- What happened to a reply's email: Resend's id when it was accepted, or the error.
create or replace function mc_api.ticket_reply_result(p_message bigint, p_resend_email_id text, p_error text default null)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('support.reply_result', false, 200);
  v_m mc.ticket_messages%rowtype;
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  select * into v_m from mc.ticket_messages m where m.id = p_message and m.kind = 'reply' for update;
  if not found then
    return mc.failed('not_found', 'There is no such reply');
  end if;
  update mc.ticket_messages m set
    resend_email_id = coalesce(left(p_resend_email_id, 100), m.resend_email_id),
    delivery = case when p_error is null and p_resend_email_id is not null then 'sent' else 'failed' end,
    error = left(p_error, 300)
    where m.id = p_message;
  perform mc.audit('support.reply_sent', (select t.user_id from mc.tickets t where t.id = v_m.ticket_id), 'LC-' || v_m.ticket_id,
    null, case when p_error is null and p_resend_email_id is not null then 'ok' else 'failed' end, null, null, left(p_error, 300));
  return jsonb_build_object('ok', true);
end;
$$;

create or replace function mc_api.ticket_update(p_ticket bigint, p_status text default null, p_priority text default null)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('support.write', false, 120);
  v_t mc.tickets%rowtype;
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  if (p_status is not null and p_status not in ('open', 'in_progress', 'awaiting_customer', 'resolved'))
     or (p_priority is not null and p_priority not in ('normal', 'urgent')) then
    return mc.failed('invalid', 'Unknown status or priority');
  end if;
  select * into v_t from mc.tickets t where t.id = p_ticket for update;
  if not found then
    return mc.failed('not_found', 'There is no ticket LC-' || coalesce(p_ticket::text, '?'));
  end if;
  update mc.tickets t set
    status = coalesce(p_status, t.status),
    priority = coalesce(p_priority, t.priority),
    resolved_at = case when coalesce(p_status, t.status) = 'resolved' then coalesce(t.resolved_at, now()) else null end,
    last_activity_at = now()
    where t.id = v_t.id;
  perform mc.audit('support.update', v_t.user_id, 'LC-' || v_t.id, null, 'ok',
    jsonb_build_object('status', v_t.status, 'priority', v_t.priority),
    jsonb_build_object('status', coalesce(p_status, v_t.status), 'priority', coalesce(p_priority, v_t.priority)));
  return jsonb_build_object('ok', true);
end;
$$;

-- Links a ticket that came without an account (signed out, or logged by hand) to the account it belongs to.
create or replace function mc_api.ticket_link_account(p_ticket bigint, p_user uuid)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('support.write', false, 120);
  v_t mc.tickets%rowtype;
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  select * into v_t from mc.tickets t where t.id = p_ticket for update;
  if not found then
    return mc.failed('not_found', 'There is no ticket LC-' || coalesce(p_ticket::text, '?'));
  end if;
  if v_t.user_id is not null then
    return mc.failed('already_linked', 'This ticket is already linked to an account');
  end if;
  if not exists (select 1 from auth.users u where u.id = p_user) then
    return mc.failed('not_found', 'There is no account with that id');
  end if;
  update mc.tickets t set user_id = p_user, requester_email = null, last_activity_at = now() where t.id = v_t.id;
  perform mc.audit('support.link_account', p_user, 'LC-' || v_t.id, null, 'ok',
    jsonb_build_object('requester_email_domain', split_part(v_t.requester_email, '@', 2)), jsonb_build_object('verified', false));
  return jsonb_build_object('ok', true);
end;
$$;

-- ================================================================================================ emails
create or replace function mc_api.email_overview(p_type text default null, p_query text default null, p_limit integer default 100)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('emails.read');
  v_limit integer := least(greatest(coalesce(p_limit, 100), 1), 500);
  v_q text := nullif(btrim(coalesce(p_query, '')), '');
  v_day_start timestamptz := date_trunc('day', now() at time zone 'utc') at time zone 'utc';
  v_month_start timestamptz := date_trunc('month', now() at time zone 'utc') at time zone 'utc';
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  return jsonb_build_object('ok', true,
    'extended_data_enabled', mc.setting_on('extended_data_enabled'),
    -- Resend's quota counts every email on the account, sent and received, for both products; its day is UTC's.
    'quota', jsonb_build_object(
      'today', (select count(*) from mc.email_events e where e.type in ('email.sent', 'email.received') and e.at >= v_day_start),
      'month', (select count(*) from mc.email_events e where e.type in ('email.sent', 'email.received') and e.at >= v_month_start),
      -- Lucento's own mail by its sending domain: the recipient is only kept once extended data is on.
      'today_lucento', (select count(*) from mc.email_events e where e.type = 'email.sent' and e.at >= v_day_start
                        and (e.from_domain = 'lucentoapp.com' or e.from_domain like '%.lucentoapp.com')),
      'daily_limit', mc.setting_int('resend_daily_limit', 100),
      'monthly_limit', mc.setting_int('resend_monthly_limit', 3000),
      'first_event_at', (select min(e.at) from mc.email_events e),
      'last_event_at', (select max(e.received_at) from mc.email_events e)),
    'counts_7d', (select coalesce(jsonb_object_agg(x.type, x.n), '{}'::jsonb) from (
                    select e.type, count(*) as n from mc.email_events e where e.at > now() - interval '7 days' group by e.type) x),
    'events', (select coalesce(jsonb_agg(jsonb_build_object('event_id', e.event_id, 'email_id', e.email_id, 'type', e.type, 'at', e.at,
                 'from_domain', e.from_domain, 'recipient', e.recipient, 'subject', e.subject, 'reason', e.reason) order by e.at desc), '[]'::jsonb)
               from (select * from mc.email_events x
                     where (p_type is null or x.type = p_type)
                       and (v_q is null or x.recipient ilike mc.like_any(v_q) or x.subject ilike mc.like_any(v_q))
                     order by x.at desc limit v_limit) e),
    'bounces', (select coalesce(jsonb_agg(jsonb_build_object('type', e.type, 'at', e.at, 'recipient', e.recipient, 'subject', e.subject,
                  'reason', e.reason) order by e.at desc), '[]'::jsonb)
                from (select * from mc.email_events x where x.type in ('email.bounced', 'email.complained', 'email.failed', 'email.suppressed')
                      and (x.from_domain = 'lucentoapp.com' or x.from_domain like '%.lucentoapp.com') order by x.at desc limit 50) e),
    'sends', (select coalesce(jsonb_agg(jsonb_build_object('id', s.id, 'template', s.template, 'recipient', s.recipient,
                'status', s.status, 'error', s.error, 'created_at', s.created_at,
                'sent_by', (select a.display_name from mc.admins a where a.user_id = s.sent_by)) order by s.created_at desc), '[]'::jsonb)
              from (select * from mc.email_sends x order by x.created_at desc limit 20) s));
end;
$$;

-- Prepares one email from a fixed template: to an account's verified address, or 'test' to the admin's own. Mission
-- Control sends it right after and records the result with email_send_result.
create or replace function mc_api.email_send_prepare(p_template text, p_user uuid default null)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('emails.send', false, 20);
  v_recipient text;
  v_id uuid;
  v_context jsonb := '{}'::jsonb;
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  if p_template is null or p_template not in ('complimentary_pro', 'about_purchase', 'deletion_scheduled', 'test') then
    return mc.failed('invalid', 'Unknown template');
  end if;
  if p_template = 'test' then
    select a.email into v_recipient from mc.admins a where a.user_id = auth.uid();
  else
    select u.email into v_recipient from auth.users u where u.id = p_user and u.email_confirmed_at is not null;
    if v_recipient is null then
      return mc.failed('no_verified_email', 'This account has no verified email address');
    end if;
    if p_template = 'complimentary_pro' then
      v_context := (select jsonb_build_object('kind', g.kind, 'ends_at', g.ends_at, 'services_end_at', g.services_end_at)
                    from mc.entitlement_grants g
                    where g.user_id = p_user and g.revoked_at is null and (g.ends_at is null or g.services_end_at > now())
                    order by g.granted_at desc limit 1);
      if v_context is null then
        return mc.failed('no_grant', 'This account has no running complimentary grant');
      end if;
    elsif p_template = 'deletion_scheduled' then
      v_context := (select jsonb_build_object('run_after', d.run_after) from mc.deletion_requests d
                    where d.user_id = p_user and d.cancelled_at is null and d.executed_at is null);
      if v_context is null then
        return mc.failed('no_deletion', 'No deletion is scheduled for this account');
      end if;
    end if;
  end if;
  insert into mc.email_sends (user_id, template, recipient, sent_by) values (p_user, p_template, v_recipient, auth.uid())
    returning id into v_id;
  perform mc.audit('emails.send', p_user, v_id::text, null, 'pending', null, null, p_template);
  return jsonb_build_object('ok', true, 'send_id', v_id, 'recipient', v_recipient, 'context', v_context);
end;
$$;

create or replace function mc_api.email_send_result(p_send uuid, p_resend_email_id text, p_error text default null)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('emails.send_result', false, 200);
  v_s mc.email_sends%rowtype;
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  select * into v_s from mc.email_sends s where s.id = p_send and s.sent_by = auth.uid() for update;
  if not found then
    return mc.failed('not_found', 'There is no such email');
  end if;
  update mc.email_sends s set
    status = case when p_error is null and p_resend_email_id is not null then 'sent' else 'failed' end,
    resend_email_id = left(p_resend_email_id, 100), error = left(p_error, 300)
    where s.id = p_send;
  perform mc.audit('emails.send_result', v_s.user_id, p_send::text, null,
    case when p_error is null and p_resend_email_id is not null then 'ok' else 'failed' end, null, null, coalesce(left(p_error, 300), v_s.template));
  return jsonb_build_object('ok', true);
end;
$$;

-- ================================================================================================ revenue
create or replace function mc_api.revenue(p_from date default null, p_to date default null)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('revenue.read');
  v_tz text := mc.tz();
  v_today date := (now() at time zone mc.tz())::date;
  v_to date := least(coalesce(p_to, v_today), v_today);
  v_from date := coalesce(p_from, v_to - 29);
  v_start timestamptz;
  v_end timestamptz;
  v_year_start timestamptz;
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  if v_from > v_to or v_to - v_from > 400 then
    return mc.failed('invalid', 'Choose a range of at most 400 days');
  end if;
  v_start := v_from::timestamp at time zone v_tz;
  v_end := (v_to + 1)::timestamp at time zone v_tz;
  v_year_start := (date_trunc('month', v_today::timestamp) - interval '11 months') at time zone v_tz;
  return jsonb_build_object('ok', true,
    'extended_data_enabled', mc.setting_on('extended_data_enabled'),
    'range', jsonb_build_object('from', v_from, 'to', v_to, 'timezone', v_tz),
    'summary', mc.revenue_block(v_start, v_end),
    -- Twelve months of Google Play amounts: subscription billings and Lifetime sales, kept apart, per currency.
    'monthly', (select coalesce(jsonb_agg(jsonb_build_object('month', x.month, 'currency', x.currency, 'kind', x.kind,
                  'orders', x.n, 'gross_micros', x.gross::text) order by x.month, x.kind), '[]'::jsonb)
                from (select to_char(o.ordered_at at time zone v_tz, 'YYYY-MM') as month, o.currency,
                             case when o.product_id = 'lucento_pro_lifetime' then 'lifetime' else 'subscriptions' end as kind,
                             count(*) as n, coalesce(sum(o.total_micros), 0) as gross
                      from mc.play_orders o where not o.test and o.ordered_at >= v_year_start group by 1, 2, 3) x),
    -- The same twelve months as counts, from Lucento's own records (available before the Play connection).
    'monthly_counts', (select coalesce(jsonb_agg(jsonb_build_object('month', x.month, 'kind', x.kind, 'purchases', x.n)
                         order by x.month, x.kind), '[]'::jsonb)
                       from (select to_char(e.starts_at at time zone v_tz, 'YYYY-MM') as month,
                                    case when e.source = 'PLAY_LIFETIME' then 'lifetime' else 'subscriptions' end as kind,
                                    count(distinct e.purchase_token_hash) as n
                             from public.entitlements e
                             where e.source in ('PLAY_LIFETIME', 'PLAY_SUBSCRIPTION', 'PLAY_CLOUD_ADDON') and not e.test_purchase
                               and e.starts_at >= v_year_start group by 1, 2) x),
    'recent_purchases', (select coalesce(jsonb_agg(r order by r.linked_at desc), '[]'::jsonb) from (
        select l.linked_at, l.product_id, l.order_id, l.purchase_token_hash as token_hash, l.user_id,
               (select u.email from auth.users u where u.id = l.user_id) as email,
               (select e.status from public.entitlements e where e.purchase_token_hash = l.purchase_token_hash
                order by e.verified_at desc limit 1) as status,
               (select e.base_plan_id from public.entitlements e where e.purchase_token_hash = l.purchase_token_hash
                and e.base_plan_id is not null limit 1) as base_plan_id,
               coalesce((select bool_or(e.test_purchase) from public.entitlements e where e.purchase_token_hash = l.purchase_token_hash), false) as test,
               exists (select 1 from lucento_private.revoked_purchases rp where rp.purchase_token_hash = l.purchase_token_hash) as refunded,
               (select jsonb_build_object('currency', o.currency, 'total_micros', o.total_micros::text, 'tax_micros', o.tax_micros::text,
                  'state', o.state) from mc.play_orders o where o.order_id = l.order_id) as amount
        from public.purchase_links l order by l.linked_at desc limit 25) r),
    'refunds', (select coalesce(jsonb_agg(jsonb_build_object('revoked_at', rp.revoked_at, 'token_hash', rp.purchase_token_hash,
                  'product_id', (select c.product_id from lucento_private.purchase_checks c where c.purchase_token_hash = rp.purchase_token_hash),
                  'amount', (select jsonb_build_object('currency', o.currency, 'refunded_micros', o.refunded_micros::text, 'total_micros', o.total_micros::text)
                             from mc.play_orders o where o.purchase_token_hash = rp.purchase_token_hash order by o.ordered_at desc limit 1))
                  order by rp.revoked_at desc), '[]'::jsonb)
                from (select * from lucento_private.revoked_purchases x order by x.revoked_at desc limit 25) rp),
    'grants_ending', (select coalesce(jsonb_agg(jsonb_build_object('user_id', g.user_id, 'email', (select u.email from auth.users u where u.id = g.user_id),
                        'kind', g.kind, 'services_end_at', g.services_end_at, 'ends_at', g.ends_at, 'reason', g.reason) order by g.services_end_at), '[]'::jsonb)
                      from mc.entitlement_grants g
                      where g.revoked_at is null and g.services_end_at > now() and g.services_end_at <= now() + interval '30 days'),
    'grant_reasons', (select coalesce(jsonb_agg(jsonb_build_object('reason', x.reason, 'grants', x.n) order by x.n desc), '[]'::jsonb)
                      from (select g.reason, count(*) as n from mc.entitlement_grants g
                            where g.revoked_at is null and (g.ends_at is null or g.services_end_at > now())
                            group by g.reason order by count(*) desc limit 8) x));
end;
$$;

-- ================================================================================================ infrastructure
-- The database's own health, timed by Mission Control.
create or replace function mc_api.probe()
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('infra.read');
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  return jsonb_build_object('ok', true, 'now', now(), 'db_size_bytes', pg_database_size(current_database()),
    'postgres', current_setting('server_version'));
end;
$$;

-- Mission Control's server records what it just checked (Auth, the platforms, Vercel, Resend, Crashlytics, Play).
create or replace function mc_api.record_service_checks(p_checks jsonb)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('infra.write', false, 240);
  v_c jsonb;
  v_n integer := 0;
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  if jsonb_typeof(p_checks) <> 'array' or jsonb_array_length(p_checks) > 20 then
    return mc.failed('invalid', 'Expected a list of checks');
  end if;
  for v_c in select * from jsonb_array_elements(p_checks) loop
    if v_c ->> 'service' in ('supabase_db', 'supabase_auth', 'supabase_platform', 'vercel_platform', 'vercel_deployments',
                              'resend', 'crashlytics', 'play')
       and v_c ->> 'status' in ('ok', 'degraded', 'down', 'not_configured') then
      insert into mc.service_checks as s (service, status, checked_at, latency_ms, detail)
        values (v_c ->> 'service', v_c ->> 'status', now(),
                case when (v_c ->> 'latency_ms') ~ '^\d{1,7}$' then (v_c ->> 'latency_ms')::integer end,
                case when jsonb_typeof(v_c -> 'detail') = 'object' and pg_column_size(v_c -> 'detail') <= 4000 then v_c -> 'detail' else '{}'::jsonb end)
        on conflict (service) do update
          set status = excluded.status, checked_at = excluded.checked_at, latency_ms = excluded.latency_ms, detail = excluded.detail;
      v_n := v_n + 1;
    end if;
  end loop;
  return jsonb_build_object('ok', true, 'recorded', v_n);
end;
$$;

create or replace function mc_api.infra_status()
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('infra.read');
  v_cron jsonb := null;
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  -- pg_cron's own record of Mission Control's jobs, where pg_cron exists.
  if exists (select 1 from pg_catalog.pg_extension x where x.extname = 'pg_cron') then
    execute $q$
      select coalesce(jsonb_agg(jsonb_build_object('job', j.jobname, 'schedule', j.schedule, 'active', j.active,
        'last_status', (select d.status from cron.job_run_details d where d.jobid = j.jobid order by d.start_time desc limit 1),
        'last_start', (select d.start_time from cron.job_run_details d where d.jobid = j.jobid order by d.start_time desc limit 1))
        order by j.jobname), '[]'::jsonb)
      from cron.job j where j.jobname like 'mc-%'
    $q$ into v_cron;
  end if;
  return jsonb_build_object('ok', true,
    'checks', (select coalesce(jsonb_object_agg(s.service, jsonb_build_object(
                 'status', case when s.service <> 'backup' and s.checked_at < now() - interval '10 minutes' then 'unknown' else s.status end,
                 'recorded_status', s.status, 'checked_at', s.checked_at, 'latency_ms', s.latency_ms, 'detail', s.detail)), '{}'::jsonb)
               from mc.service_checks s),
    'jobs', (select coalesce(jsonb_object_agg(x.job, jsonb_build_object('started_at', x.started_at, 'finished_at', x.finished_at,
               'result', x.result, 'detail', x.detail)), '{}'::jsonb)
             from (select distinct on (r.job) r.* from mc.job_runs r order by r.job, r.started_at desc) x),
    'backups', (select coalesce(jsonb_agg(jsonb_build_object('started_at', r.started_at, 'finished_at', r.finished_at, 'result', r.result,
                  'detail', r.detail) order by r.started_at desc), '[]'::jsonb)
                from (select * from mc.job_runs x where x.job = 'backup' order by x.started_at desc limit 8) r),
    'cron', v_cron,
    'db_size_bytes', pg_database_size(current_database()));
end;
$$;

-- ================================================================================================ records (FD-V3)
-- Opening a customer's records: only when the setting is on (after the privacy-policy release, FD-V15), with a reason
-- and a fresh authenticator code, for 30 minutes at most. Read-only.
create or replace function mc_api.open_records(p_user uuid, p_reason text)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text;
  v_id uuid;
  v_expires timestamptz := now() + interval '30 minutes';
  v_session uuid := case when auth.jwt() ->> 'session_id' ~ '^[0-9a-fA-F-]{36}$' then (auth.jwt() ->> 'session_id')::uuid end;
begin
  -- The gate first, so only an admin learns whether the view is on (security review L1); the fresh code after that,
  -- so nobody is asked for one when the view is off anyway.
  v_refusal := mc.gate('access.records_open', false, 30);
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  if not mc.setting_on('records_view_enabled') then
    perform mc.log_refusal(auth.uid(), 'access.records_open', 'records_view_disabled');
    return mc.refused('records_view_disabled');
  end if;
  if not mc.fresh_mfa(coalesce(auth.jwt(), '{}'::jsonb)) then
    perform mc.log_refusal(auth.uid(), 'access.records_open', 'step_up_required');
    return mc.refused('step_up_required');
  end if;
  if p_reason is null or char_length(btrim(p_reason)) < 3 or char_length(p_reason) > 200 then
    return mc.failed('reason_required', 'Write why you need to see these records (usually the ticket number)');
  end if;
  if not exists (select 1 from auth.users u where u.id = p_user) then
    return mc.failed('not_found', 'There is no account with that id');
  end if;
  update mc.records_access r set closed_at = now()
    where r.admin_id = auth.uid() and r.user_id = p_user and r.closed_at is null;
  insert into mc.records_access (admin_id, session_id, user_id, reason, expires_at)
    values (auth.uid(), v_session, p_user, btrim(p_reason), v_expires)
    returning id into v_id;
  perform mc.audit('access.records_open', p_user, v_id::text, btrim(p_reason), 'ok');
  return jsonb_build_object('ok', true, 'access_id', v_id, 'expires_at', v_expires);
end;
$$;

-- The access, if it belongs to the caller's own sign-in session (the one that gave the fresh code, security review
-- L2), is still open, and the setting is still on.
create or replace function mc.records_session(p_access uuid)
returns mc.records_access
language sql
stable
security definer
set search_path = ''
as $$
  select r.* from mc.records_access r
  where r.id = p_access and r.admin_id = auth.uid() and r.closed_at is null and r.expires_at > now()
    and r.session_id = case when auth.jwt() ->> 'session_id' ~ '^[0-9a-fA-F-]{36}$' then (auth.jwt() ->> 'session_id')::uuid end
    and mc.setting_on('records_view_enabled')
$$;

-- One page of one kind of record. Notes are never in this answer: only whether there is one (reveal_note shows it).
create or replace function mc_api.user_records(p_access uuid, p_kind text, p_page integer default 0)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('access.records_read', false, 600);
  v_r mc.records_access;
  v_page integer := greatest(coalesce(p_page, 0), 0);
  v_rows jsonb;
  v_total bigint;
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  v_r := mc.records_session(p_access);
  if v_r.id is null then
    perform mc.log_refusal(auth.uid(), 'access.records_view', 'records_session_closed');
    return mc.refused('records_session_closed');
  end if;
  if p_kind is null or p_kind not in ('wallets', 'transactions', 'budgets', 'goals', 'debts', 'recurring', 'categories') then
    return mc.failed('invalid', 'Unknown kind of record');
  end if;

  if p_kind in ('wallets', 'debts') then
    select count(*), coalesce(jsonb_agg(x order by x.sort_order, x.name), '[]'::jsonb) into v_total, v_rows from (
      select w.id, w.name, w.type, w.currency_code, w.opening_balance_minor, w.archived, w.sort_order, w.classification,
             w.credit_limit_minor, w.interest_rate_bps, w.due_day, w.updated_at,
             -- docs/CALCULATIONS.md "Wallet balance": an asset counts inflows up; a card or loan counts what's owed.
             case when w.type = 'OTHER_ASSET' then w.opening_balance_minor
                  else w.opening_balance_minor + (case when w.type in ('CREDIT_CARD', 'LOAN') then -1 else 1 end) * coalesce((
                    select sum(case when t.type in ('INCOME', 'TRANSFER_IN', 'ADJUSTMENT_IN') then t.amount_minor else -t.amount_minor end)
                    from public.transactions t
                    where t.user_id = v_r.user_id and t.wallet_id = w.id and t.deleted_at is null), 0)
             end::text as balance_minor,
             (select count(*) from public.transactions t where t.user_id = v_r.user_id and t.wallet_id = w.id and t.deleted_at is null) as transactions
      from public.wallets w
      where w.user_id = v_r.user_id and w.deleted_at is null
        and (p_kind = 'wallets' or w.type in ('CREDIT_CARD', 'LOAN', 'RECEIVABLE'))
    ) x;
  elsif p_kind = 'transactions' then
    select count(*) into v_total from public.transactions t where t.user_id = v_r.user_id and t.deleted_at is null;
    select coalesce(jsonb_agg(x order by x.occurred_on desc, x.created_at desc), '[]'::jsonb) into v_rows from (
      select t.id, t.type, t.amount_minor::text as amount_minor, t.currency_code, t.occurred_on,
             (date '1970-01-01' + t.occurred_on::integer) as date, t.transfer_group, t.created_at, t.updated_at,
             t.base_amount_minor::text as base_amount_minor, t.base_currency,
             (select w.name from public.wallets w where w.user_id = t.user_id and w.id = t.wallet_id) as wallet,
             (select c.name from public.categories c where c.user_id = t.user_id and c.id = t.category_id) as category,
             char_length(t.note) > 0 as has_note
      from public.transactions t
      where t.user_id = v_r.user_id and t.deleted_at is null
      order by t.occurred_on desc, t.created_at desc
      limit 50 offset v_page * 50
    ) x;
  elsif p_kind = 'budgets' then
    select count(*), coalesce(jsonb_agg(x order by x.month desc nulls last, x.category), '[]'::jsonb) into v_total, v_rows from (
      select b.id, b.month, b.effective_from, b.currency_code, b.limit_minor::text as limit_minor,
             (select c.name from public.categories c where c.user_id = b.user_id and c.id = b.category_id) as category
      from public.budgets b where b.user_id = v_r.user_id and b.deleted_at is null
    ) x;
  elsif p_kind = 'goals' then
    select count(*), coalesce(jsonb_agg(x order by x.archived, x.name), '[]'::jsonb) into v_total, v_rows from (
      select g.id, g.name, g.currency_code, g.target_minor::text as target_minor, g.archived,
             case when g.target_date is not null then (date '1970-01-01' + g.target_date::integer) end as target_date,
             (select coalesce(sum(c.amount_minor), 0) from public.goal_contributions c
              where c.user_id = g.user_id and c.goal_id = g.id and c.deleted_at is null)::text as saved_minor
      from public.goals g where g.user_id = v_r.user_id and g.deleted_at is null
    ) x;
  elsif p_kind = 'recurring' then
    select count(*), coalesce(jsonb_agg(x order by x.paused, x.name), '[]'::jsonb) into v_total, v_rows from (
      select r.id, r.name, r.type, r.amount_minor::text as amount_minor, r.currency_code, r.frequency, r.interval_count, r.paused,
             (date '1970-01-01' + r.start_on::integer) as start_on,
             case when r.end_on is not null then (date '1970-01-01' + r.end_on::integer) end as end_on,
             (select w.name from public.wallets w where w.user_id = r.user_id and w.id = r.wallet_id) as wallet
      from public.recurring r where r.user_id = v_r.user_id and r.deleted_at is null
    ) x;
  else
    select count(*), coalesce(jsonb_agg(x order by x.kind, x.sort_order), '[]'::jsonb) into v_total, v_rows from (
      select c.id, c.name, c.kind, c.archived, c.sort_order
      from public.categories c where c.user_id = v_r.user_id and c.deleted_at is null
    ) x;
  end if;

  perform mc.audit('access.records_view', v_r.user_id, p_access::text, v_r.reason, 'ok', null, null, p_kind || ' page ' || v_page);
  return jsonb_build_object('ok', true, 'kind', p_kind, 'page', v_page, 'total', v_total, 'rows', v_rows,
    'expires_at', v_r.expires_at, 'sync', mc.sync_tables(v_r.user_id));
end;
$$;

create or replace function mc_api.reveal_note(p_access uuid, p_transaction text)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('access.note_reveal', false, 120);
  v_r mc.records_access;
  v_note text;
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  v_r := mc.records_session(p_access);
  if v_r.id is null then
    perform mc.log_refusal(auth.uid(), 'access.note_reveal', 'records_session_closed');
    return mc.refused('records_session_closed');
  end if;
  select t.note into v_note from public.transactions t where t.user_id = v_r.user_id and t.id = p_transaction and t.deleted_at is null;
  if not found then
    return mc.failed('not_found', 'There is no such transaction');
  end if;
  perform mc.audit('access.note_reveal', v_r.user_id, left(p_transaction, 200), v_r.reason, 'ok');
  return jsonb_build_object('ok', true, 'note', v_note);
end;
$$;

create or replace function mc_api.close_records(p_access uuid)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('access.records_close');
  v_user uuid;
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  update mc.records_access r set closed_at = now()
    where r.id = p_access and r.admin_id = auth.uid() and r.closed_at is null
    returning r.user_id into v_user;
  if v_user is not null then
    perform mc.audit('access.records_close', v_user, p_access::text, null, 'ok');
  end if;
  return jsonb_build_object('ok', true);
end;
$$;

-- ================================================================================================ search
-- The top bar's search: an email or account id, a ticket number or text, an order id.
create or replace function mc_api.search(p_query text)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_refusal text := mc.gate('search', false, 600);
  v_q text := nullif(btrim(coalesce(p_query, '')), '');
  v_number bigint;
begin
  if v_refusal is not null then
    return mc.refused(v_refusal);
  end if;
  if v_q is null or char_length(v_q) < 2 or char_length(v_q) > 200 then
    return jsonb_build_object('ok', true, 'users', '[]'::jsonb, 'tickets', '[]'::jsonb, 'orders', '[]'::jsonb);
  end if;
  if v_q ~* '^(lc-)?\d{1,12}$' then
    v_number := regexp_replace(v_q, '\D', '', 'g')::bigint;
  end if;
  return jsonb_build_object('ok', true,
    'users', (select coalesce(jsonb_agg(jsonb_build_object('id', u.id, 'email', u.email, 'plan', mc.plan_of(u.id), 'status', mc.status_of(u.id))), '[]'::jsonb)
              from (select * from auth.users x where x.email ilike mc.like_any(v_q) or x.id::text = lower(v_q) order by x.created_at desc limit 8) u),
    'tickets', (select coalesce(jsonb_agg(jsonb_build_object('id', t.id, 'number', 'LC-' || t.id, 'subject', t.subject, 'status', t.status)), '[]'::jsonb)
                from (select * from mc.tickets x where x.id = v_number or x.subject ilike mc.like_any(v_q)
                        or x.requester_email ilike mc.like_any(v_q)
                      order by x.last_activity_at desc limit 8) t),
    'orders', (select coalesce(jsonb_agg(jsonb_build_object('order_id', l.order_id, 'product_id', l.product_id, 'user_id', l.user_id,
                 'email', (select u.email from auth.users u where u.id = l.user_id))), '[]'::jsonb)
               from (select * from public.purchase_links x where x.order_id ilike mc.like_any(v_q) limit 5) l));
end;
$$;

-- ================================================================================================ ingest (anon + secret)
-- Resend's webhook, already signature-checked by Mission Control's route and reduced to the fields kept here.
-- KlaroPH's mail (another domain on the same Resend account) is counted, with no recipient, subject or bounce reason
-- (a reason can quote the address). Lucento's own recipients and subjects are kept only once the updated privacy policy
-- is live (extended_data_enabled, security review M6); until then every event is counted without them.
create or replace function mc_api.ingest_email_events(p_secret text, p_events jsonb)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_e jsonb;
  v_domain text;
  v_lucento boolean;
  v_keep boolean := mc.setting_on('extended_data_enabled');
  v_type text;
  v_n integer := 0;
  v_one integer;
begin
  if not mc.ingest_secret_ok(p_secret, 'app') then
    perform mc.log_refusal(null, 'ingest.email_events', 'bad_secret');
    return mc.refused('bad_secret');
  end if;
  if jsonb_typeof(p_events) <> 'array' or jsonb_array_length(p_events) > 100 then
    return mc.failed('invalid', 'Expected at most 100 events');
  end if;
  for v_e in select * from jsonb_array_elements(p_events) loop
    v_type := v_e ->> 'type';
    if v_e ->> 'event_id' is null or char_length(v_e ->> 'event_id') > 100 or v_type is null
       or v_type not in ('email.sent', 'email.delivered', 'email.delivery_delayed', 'email.bounced', 'email.complained',
                         'email.failed', 'email.opened', 'email.clicked', 'email.received', 'email.scheduled', 'email.suppressed')
       or (v_e ->> 'at') !~ '^\d{4}-\d{2}-\d{2}' then
      continue;
    end if;
    v_domain := lower(left(nullif(v_e ->> 'from_domain', ''), 253));
    v_lucento := v_domain is not null and (v_domain = 'lucentoapp.com' or v_domain like '%.lucentoapp.com');
    insert into mc.email_events (event_id, email_id, type, at, from_domain, recipient, subject, reason)
      values (v_e ->> 'event_id', left(v_e ->> 'email_id', 100), v_type, (v_e ->> 'at')::timestamptz, v_domain,
              case when v_lucento and v_keep then lower(left(v_e ->> 'recipient', 254)) end,
              case when v_lucento and v_keep then left(v_e ->> 'subject', 300) end,
              case when v_lucento and v_keep then left(v_e ->> 'reason', 500) end)
      on conflict (event_id) do nothing;
    get diagnostics v_one = row_count;
    v_n := v_n + v_one;
    if v_one > 0 and v_e ->> 'email_id' is not null then
      update mc.ticket_messages m set delivery = case v_type
          when 'email.delivered' then 'delivered' when 'email.delivery_delayed' then 'delayed' when 'email.bounced' then 'bounced'
          when 'email.complained' then 'complained' when 'email.failed' then 'failed' when 'email.suppressed' then 'failed' else m.delivery end
        where m.resend_email_id = v_e ->> 'email_id'
          and v_type in ('email.delivered', 'email.delivery_delayed', 'email.bounced', 'email.complained', 'email.failed', 'email.suppressed')
          and not (m.delivery = 'delivered' and v_type = 'email.delivery_delayed');
    end if;
  end loop;
  return jsonb_build_object('ok', true, 'inserted', v_n);
end;
$$;

-- The orders whose amounts Mission Control should fetch from Google Play: first orders of purchases Lucento knows about,
-- never fetched, or fetched more than a week ago while they could still change (a refund). Google's orders carry no
-- test marker, so each one comes with Lucento's own: whether the purchase was a licence tester's. None until the
-- updated privacy policy (which discloses keeping the amounts) is live (security review M6).
create or replace function mc_api.play_orders_wanted(p_secret text, p_limit integer default 1000)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
begin
  if not mc.ingest_secret_ok(p_secret, 'app') then
    perform mc.log_refusal(null, 'ingest.play_orders_wanted', 'bad_secret');
    return mc.refused('bad_secret');
  end if;
  if not mc.setting_on('extended_data_enabled') then
    return jsonb_build_object('ok', true, 'paused', true, 'orders', '[]'::jsonb);
  end if;
  return jsonb_build_object('ok', true, 'paused', false, 'orders', (
    select coalesce(jsonb_agg(jsonb_build_object('order_id', l.order_id, 'token_hash', l.purchase_token_hash, 'product_id', l.product_id,
             'test', coalesce((select bool_or(e.test_purchase) from public.entitlements e where e.purchase_token_hash = l.purchase_token_hash), false))), '[]'::jsonb)
    from (select pl.* from public.purchase_links pl
          left join mc.play_orders o on o.order_id = pl.order_id
          where pl.order_id is not null
            and (o.order_id is null or (o.fetched_at < now() - interval '7 days' and o.ordered_at > now() - interval '400 days'))
          order by o.fetched_at nulls first
          limit least(greatest(coalesce(p_limit, 1000), 1), 1000)) l));
end;
$$;

create or replace function mc_api.ingest_play_orders(p_secret text, p_orders jsonb)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_o jsonb;
  v_n integer := 0;
  v_micros constant text := '^\d{1,18}$';
begin
  if not mc.ingest_secret_ok(p_secret, 'app') then
    perform mc.log_refusal(null, 'ingest.play_orders', 'bad_secret');
    return mc.refused('bad_secret');
  end if;
  if jsonb_typeof(p_orders) <> 'array' or jsonb_array_length(p_orders) > 1000 then
    return mc.failed('invalid', 'Expected at most 1,000 orders');
  end if;
  for v_o in select * from jsonb_array_elements(p_orders) loop
    -- An order Google doesn't know is kept as NOT_FOUND (no amounts), so the sweep stops asking for it.
    if v_o ->> 'order_id' is null or char_length(v_o ->> 'order_id') > 100
       or (coalesce(v_o ->> 'currency', '') !~ '^[A-Z]{3}$' and coalesce(v_o ->> 'state', '') <> 'NOT_FOUND') then
      continue;
    end if;
    insert into mc.play_orders as p (order_id, purchase_token_hash, product_id, base_plan_id, currency,
        listing_price_micros, tax_micros, total_micros, refunded_micros, developer_revenue_micros, state, test, ordered_at,
        refunded_at, fetched_at)
      values (v_o ->> 'order_id',
              case when v_o ->> 'token_hash' ~ '^[0-9a-f]{64}$' then v_o ->> 'token_hash' end,
              left(v_o ->> 'product_id', 100), left(v_o ->> 'base_plan_id', 100),
              case when v_o ->> 'currency' ~ '^[A-Z]{3}$' then v_o ->> 'currency' end,
              case when v_o ->> 'listing_price_micros' ~ v_micros then (v_o ->> 'listing_price_micros')::bigint end,
              case when v_o ->> 'tax_micros' ~ v_micros then (v_o ->> 'tax_micros')::bigint end,
              case when v_o ->> 'total_micros' ~ v_micros then (v_o ->> 'total_micros')::bigint end,
              case when v_o ->> 'refunded_micros' ~ v_micros then (v_o ->> 'refunded_micros')::bigint end,
              case when v_o ->> 'developer_revenue_micros' ~ '^-?\d{1,18}$' then (v_o ->> 'developer_revenue_micros')::bigint end,
              left(v_o ->> 'state', 40), coalesce(v_o ->> 'test' = 'true', false),
              case when v_o ->> 'ordered_at' ~ '^\d{4}-\d{2}-\d{2}' then (v_o ->> 'ordered_at')::timestamptz end,
              case when v_o ->> 'refunded_at' ~ '^\d{4}-\d{2}-\d{2}' then (v_o ->> 'refunded_at')::timestamptz end,
              now())
      on conflict (order_id) do update set
        purchase_token_hash = coalesce(excluded.purchase_token_hash, p.purchase_token_hash),
        product_id = coalesce(excluded.product_id, p.product_id), base_plan_id = coalesce(excluded.base_plan_id, p.base_plan_id),
        currency = coalesce(excluded.currency, p.currency),
        listing_price_micros = coalesce(excluded.listing_price_micros, p.listing_price_micros),
        tax_micros = coalesce(excluded.tax_micros, p.tax_micros), total_micros = coalesce(excluded.total_micros, p.total_micros),
        refunded_micros = coalesce(excluded.refunded_micros, p.refunded_micros),
        developer_revenue_micros = coalesce(excluded.developer_revenue_micros, p.developer_revenue_micros),
        state = coalesce(excluded.state, p.state),
        test = excluded.test, ordered_at = coalesce(excluded.ordered_at, p.ordered_at),
        refunded_at = coalesce(excluded.refunded_at, p.refunded_at), fetched_at = now();
    v_n := v_n + 1;
  end loop;
  return jsonb_build_object('ok', true, 'stored', v_n);
end;
$$;

-- A job outside the database reports its run: the weekly backup (GitHub Actions), the Play orders sweep and the
-- Crashlytics query (Mission Control's daily route).
create or replace function mc_api.ingest_job_run(
  p_secret text, p_job text, p_result text, p_started_at timestamptz, p_finished_at timestamptz, p_detail jsonb default '{}'::jsonb
)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_detail jsonb := case when jsonb_typeof(p_detail) = 'object' and pg_column_size(p_detail) <= 4000 then p_detail else '{}'::jsonb end;
begin
  -- The backup holds its own secret, good only for reporting itself (security review L10).
  if not mc.ingest_secret_ok(p_secret, case when p_job = 'backup' then 'backup' else 'app' end) then
    perform mc.log_refusal(null, 'ingest.job_run', 'bad_secret');
    return mc.refused('bad_secret');
  end if;
  if p_job is null or p_job not in ('backup', 'play_orders', 'crashlytics') or p_result is null or p_result not in ('ok', 'failed') then
    return mc.failed('invalid', 'Unknown job or result');
  end if;
  insert into mc.job_runs (job, started_at, finished_at, result, detail)
    values (p_job, coalesce(p_started_at, now()), coalesce(p_finished_at, now()), p_result, v_detail);
  if p_job = 'backup' then
    insert into mc.service_checks as s (service, status, checked_at, detail)
      values ('backup', case when p_result = 'ok' then 'ok' else 'down' end, coalesce(p_finished_at, now()), v_detail)
      on conflict (service) do update set status = excluded.status, checked_at = excluded.checked_at, detail = excluded.detail;
  end if;
  return jsonb_build_object('ok', true);
end;
$$;

-- The daily route's checks (Crashlytics, Play, Vercel), recorded without an admin session.
create or replace function mc_api.ingest_service_checks(p_secret text, p_checks jsonb)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_c jsonb;
  v_n integer := 0;
begin
  if not mc.ingest_secret_ok(p_secret, 'app') then
    perform mc.log_refusal(null, 'ingest.service_checks', 'bad_secret');
    return mc.refused('bad_secret');
  end if;
  if jsonb_typeof(p_checks) <> 'array' or jsonb_array_length(p_checks) > 20 then
    return mc.failed('invalid', 'Expected a list of checks');
  end if;
  for v_c in select * from jsonb_array_elements(p_checks) loop
    if v_c ->> 'service' in ('supabase_platform', 'vercel_platform', 'vercel_deployments', 'resend', 'crashlytics', 'play')
       and v_c ->> 'status' in ('ok', 'degraded', 'down', 'not_configured') then
      insert into mc.service_checks as s (service, status, checked_at, latency_ms, detail)
        values (v_c ->> 'service', v_c ->> 'status', now(),
                case when (v_c ->> 'latency_ms') ~ '^\d{1,7}$' then (v_c ->> 'latency_ms')::integer end,
                case when jsonb_typeof(v_c -> 'detail') = 'object' and pg_column_size(v_c -> 'detail') <= 4000 then v_c -> 'detail' else '{}'::jsonb end)
        on conflict (service) do update
          set status = excluded.status, checked_at = excluded.checked_at, latency_ms = excluded.latency_ms, detail = excluded.detail;
      v_n := v_n + 1;
    end if;
  end loop;
  return jsonb_build_object('ok', true, 'recorded', v_n);
end;
$$;

-- ================================================================================================ privileges
revoke all on all functions in schema mc from public, anon, authenticated, service_role;
revoke all on function
  mc_api.list_users(text, text, text, text, text, integer, integer),
  mc_api.user_profile(uuid),
  mc_api.grant_pro(uuid, text, text, text, bigint),
  mc_api.revoke_grant(uuid, text),
  mc_api.suspend(uuid, text),
  mc_api.reactivate(uuid, text),
  mc_api.schedule_deletion(uuid, text, text),
  mc_api.cancel_deletion(uuid, text),
  mc_api.resolve_purchase_link(text, uuid, text, text),
  mc_api.set_internal(uuid, text, text),
  mc_api.overview(date, date),
  mc_api.nav_badges(),
  mc_api.tickets_list(text, text, integer, integer),
  mc_api.ticket_get(bigint),
  mc_api.ticket_create(text, text, text, text, text, text),
  mc_api.ticket_add_message(bigint, text, text),
  mc_api.ticket_reply_result(bigint, text, text),
  mc_api.ticket_update(bigint, text, text),
  mc_api.ticket_link_account(bigint, uuid),
  mc_api.ticket_reply_retry(bigint),
  mc_api.ticket_delete(bigint, text),
  mc_api.email_overview(text, text, integer),
  mc_api.email_send_prepare(text, uuid),
  mc_api.email_send_result(uuid, text, text),
  mc_api.revenue(date, date),
  mc_api.probe(),
  mc_api.record_service_checks(jsonb),
  mc_api.infra_status(),
  mc_api.open_records(uuid, text),
  mc_api.user_records(uuid, text, integer),
  mc_api.reveal_note(uuid, text),
  mc_api.close_records(uuid),
  mc_api.search(text),
  mc_api.ingest_email_events(text, jsonb),
  mc_api.play_orders_wanted(text, integer),
  mc_api.ingest_play_orders(text, jsonb),
  mc_api.ingest_job_run(text, text, text, timestamptz, timestamptz, jsonb),
  mc_api.ingest_service_checks(text, jsonb)
  from public, anon, authenticated, service_role;

grant execute on function
  mc_api.list_users(text, text, text, text, text, integer, integer),
  mc_api.user_profile(uuid),
  mc_api.grant_pro(uuid, text, text, text, bigint),
  mc_api.revoke_grant(uuid, text),
  mc_api.suspend(uuid, text),
  mc_api.reactivate(uuid, text),
  mc_api.schedule_deletion(uuid, text, text),
  mc_api.cancel_deletion(uuid, text),
  mc_api.resolve_purchase_link(text, uuid, text, text),
  mc_api.set_internal(uuid, text, text),
  mc_api.overview(date, date),
  mc_api.nav_badges(),
  mc_api.tickets_list(text, text, integer, integer),
  mc_api.ticket_get(bigint),
  mc_api.ticket_create(text, text, text, text, text, text),
  mc_api.ticket_add_message(bigint, text, text),
  mc_api.ticket_reply_result(bigint, text, text),
  mc_api.ticket_update(bigint, text, text),
  mc_api.ticket_link_account(bigint, uuid),
  mc_api.ticket_reply_retry(bigint),
  mc_api.ticket_delete(bigint, text),
  mc_api.email_overview(text, text, integer),
  mc_api.email_send_prepare(text, uuid),
  mc_api.email_send_result(uuid, text, text),
  mc_api.revenue(date, date),
  mc_api.probe(),
  mc_api.record_service_checks(jsonb),
  mc_api.infra_status(),
  mc_api.open_records(uuid, text),
  mc_api.user_records(uuid, text, integer),
  mc_api.reveal_note(uuid, text),
  mc_api.close_records(uuid),
  mc_api.search(text)
  to authenticated;

grant execute on function
  mc_api.ingest_email_events(text, jsonb),
  mc_api.play_orders_wanted(text, integer),
  mc_api.ingest_play_orders(text, jsonb),
  mc_api.ingest_job_run(text, text, text, timestamptz, timestamptz, jsonb),
  mc_api.ingest_service_checks(text, jsonb)
  to anon;

-- ================================================================================================ schedule (pg_cron, UTC)
-- 00:20 in Manila is 16:20 UTC. Rescheduling under the same names replaces a job, so applying this twice is harmless.
do $$
begin
  if exists (select 1 from pg_catalog.pg_extension x where x.extname = 'pg_cron') then
    perform cron.schedule('mc-activity', '5 * * * *', 'select mc.capture_activity()');
    perform cron.schedule('mc-grants', '10 * * * *', 'select mc.expire_grants()');
    perform cron.schedule('mc-deletions', '*/15 * * * *', 'select mc.run_due_deletions()');
    perform cron.schedule('mc-daily', '20 16 * * *', 'select mc.daily_rollup()');
  else
    -- Production has pg_cron (20261029010000). Without it, deletions and retention would never run: say so loudly.
    raise warning 'pg_cron is not installed: Mission Control''s jobs (activity, grant expiry, deletions, retention) are not scheduled';
  end if;
end;
$$;
