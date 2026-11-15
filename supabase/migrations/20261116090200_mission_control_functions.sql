-- Mission Control V1, part 3 of 4: internal helpers, the database's own jobs, and the two functions the phone calls
-- (docs/mission-control/v1/DATABASE_PROPOSAL.md §4–§6, PRODUCT_SCOPE.md "Android app changes").
--
--   mc.*                        private: no API role can call them (the mc schema isn't reachable).
--   public.submit_support_request  anon and authenticated: the app's Help › Contact support form (FD-V4, FD-V4b).
--   public.report_client           authenticated: the app's version and phone model, once a day (FD-V7).
-- Neither public function returns anything about anyone else, and neither lets the caller read a ticket back.
--
-- Strictly additive: new functions only.

-- ------------------------------------------------------------------------------------------------ plan, status, activity
-- A row grants something now: the statuses and dates the app and lucento_private.has_capability read (no offline grace).
create or replace function mc.is_live(p_status text, p_expires_at timestamptz)
returns boolean
language sql
stable
set search_path = ''
as $$
  select p_status in ('ACTIVE', 'TRIAL', 'GRACE', 'CANCELLED_ACTIVE') and (p_expires_at is null or p_expires_at > now())
$$;

-- The account's plan in Mission Control's words (DATA_SOURCE_INVENTORY.md "Free / Pro / Complimentary"):
--   lifetime, subscription   paid Pro (Google Play), the two kinds kept apart
--   trial                    a subscription in its free trial
--   test                     a licence tester's purchase (grants Pro where test purchases are allowed; never revenue)
--   complimentary            a live GRANT row and nothing paid
--   free                     none of these
create or replace function mc.plan_of(p_user uuid)
returns text
language sql
stable
security definer
set search_path = ''
as $$
  with live as (
    select e.source, e.status, e.test_purchase
    from public.entitlements e
    where e.user_id = p_user and mc.is_live(e.status, e.expires_at)
  )
  select case
    when exists (select 1 from live l where l.source = 'PLAY_LIFETIME' and not l.test_purchase) then 'lifetime'
    when exists (select 1 from live l where l.source in ('PLAY_SUBSCRIPTION', 'PLAY_CLOUD_ADDON') and l.status <> 'TRIAL' and not l.test_purchase) then 'subscription'
    when exists (select 1 from live l where l.source = 'PLAY_SUBSCRIPTION' and l.status = 'TRIAL' and not l.test_purchase) then 'trial'
    when exists (select 1 from live l where l.source like 'PLAY%' and l.test_purchase) then 'test'
    when exists (select 1 from live l where l.source = 'GRANT') then 'complimentary'
    else 'free'
  end
$$;

-- active, unconfirmed (email not verified), suspended, deletion_scheduled; null when the account doesn't exist.
create or replace function mc.status_of(p_user uuid)
returns text
language sql
stable
security definer
set search_path = ''
as $$
  select case
    when exists (select 1 from mc.deletion_requests d where d.user_id = u.id and d.cancelled_at is null and d.executed_at is null) then 'deletion_scheduled'
    when u.banned_until is not null and u.banned_until > now() then 'suspended'
    when u.email_confirmed_at is null then 'unconfirmed'
    else 'active'
  end
  from auth.users u where u.id = p_user
$$;

-- The newest synced write of the account, across every synced table (uses the (user_id, server_updated_at) indexes).
create or replace function mc.last_sync(p_user uuid)
returns timestamptz
language sql
stable
security definer
set search_path = ''
as $$
  select greatest(
    (select max(x.server_updated_at) from public.wallets x where x.user_id = p_user),
    (select max(x.server_updated_at) from public.categories x where x.user_id = p_user),
    (select max(x.server_updated_at) from public.transactions x where x.user_id = p_user),
    (select max(x.server_updated_at) from public.budgets x where x.user_id = p_user),
    (select max(x.server_updated_at) from public.goals x where x.user_id = p_user),
    (select max(x.server_updated_at) from public.goal_contributions x where x.user_id = p_user),
    (select max(x.server_updated_at) from public.recurring x where x.user_id = p_user)
  )
$$;

-- When the account last showed any sign of use: a sign-in, a token refresh, a synced write or a day in the activity log.
create or replace function mc.last_seen(p_user uuid)
returns timestamptz
language sql
stable
security definer
set search_path = ''
as $$
  select greatest(
    (select u.last_sign_in_at from auth.users u where u.id = p_user),
    (select max(greatest(s.refreshed_at at time zone 'UTC', s.updated_at, s.created_at)) from auth.sessions s where s.user_id = p_user),
    mc.last_sync(p_user),
    (select (max(a.day)::timestamp at time zone mc.tz()) from mc.account_activity a where a.user_id = p_user)
  )
$$;

-- ------------------------------------------------------------------------------------------------ complimentary Pro
-- The account's GRANT rows, as Mission Control shows them.
create or replace function mc.grant_rows(p_user uuid)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select coalesce(jsonb_agg(jsonb_build_object(
      'product_id', e.product_id, 'status', e.status, 'grants', e.grants,
      'starts_at', e.starts_at, 'expires_at', e.expires_at) order by e.product_id nulls first), '[]'::jsonb)
  from public.entitlements e where e.user_id = p_user and e.source = 'GRANT'
$$;

-- Writes the account's GRANT rows from the grants still running (DATABASE_PROPOSAL.md §4):
--   * any running Lifetime grant: a phone row (product lucento_pro_lifetime, LOCAL_PRO, no expiry), like paid Lifetime;
--   * a services row (no product): the capabilities of every running grant (a Lifetime grant contributes its online
--     services only), until the latest end among them.
-- A row with nothing behind it any more ends: REVOKED when a grant was taken back early, EXPIRED when time ran out.
-- Paid rows (PLAY_*) are never read or written here. Accounts without any grant in Mission Control are left alone, so a
-- GRANT row the owner writes by hand is never undone by a job. Returns the GRANT rows afterwards.
create or replace function mc.recompute_grants(p_user uuid)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_now timestamptz := now();
  v_lifetime_start timestamptz;
  v_caps text[];
  v_end timestamptz;
  v_start timestamptz;
  v_cut_short boolean;
begin
  -- One recompute per account at a time (two admins, or a grant racing the hourly job).
  perform pg_advisory_xact_lock(hashtextextended('mc.recompute_grants:' || p_user::text, 0));
  if not exists (select 1 from mc.entitlement_grants g where g.user_id = p_user) then
    return mc.grant_rows(p_user);
  end if;

  select min(g.starts_at) into v_lifetime_start
    from mc.entitlement_grants g
    where g.user_id = p_user and g.revoked_at is null and g.kind = 'lifetime' and g.starts_at <= v_now;

  select array(
           select distinct c
           from mc.entitlement_grants g,
                unnest(case when g.kind = 'lifetime' then array_remove(g.grants, 'LOCAL_PRO') else g.grants end) c
           where g.user_id = p_user and g.revoked_at is null and g.starts_at <= v_now and g.services_end_at > v_now
             and not (v_lifetime_start is not null and c = 'LOCAL_PRO')
           order by c),
         max(g.services_end_at), min(g.starts_at)
    into v_caps, v_end, v_start
    from mc.entitlement_grants g
    where g.user_id = p_user and g.revoked_at is null and g.starts_at <= v_now and g.services_end_at > v_now;

  -- Was a grant that would still be running taken back?
  select exists (
    select 1 from mc.entitlement_grants g
    where g.user_id = p_user and g.revoked_at is not null and g.starts_at <= v_now
      and (g.ends_at is null or g.services_end_at > v_now)
  ) into v_cut_short;

  -- The services row.
  if cardinality(v_caps) > 0 then
    insert into public.entitlements as e (user_id, source, status, grants, product_id, starts_at, expires_at, verified_at, note)
      values (p_user, 'GRANT', 'ACTIVE', v_caps, null, v_start, v_end, v_now, 'Complimentary (Mission Control)')
      on conflict (user_id, source, (coalesce(product_id, ''))) do update
        set status = 'ACTIVE', grants = excluded.grants, starts_at = excluded.starts_at, expires_at = excluded.expires_at,
            verified_at = excluded.verified_at, note = excluded.note, test_purchase = false;
  else
    update public.entitlements e
      set status = case when v_cut_short then 'REVOKED' else 'EXPIRED' end,
          expires_at = least(coalesce(e.expires_at, v_now), v_now),
          verified_at = v_now
      where e.user_id = p_user and e.source = 'GRANT' and e.product_id is null and mc.is_live(e.status, e.expires_at);
    -- Rows already past their date only change their word, not their dates.
    update public.entitlements e
      set status = case when v_cut_short then 'REVOKED' else 'EXPIRED' end, verified_at = v_now
      where e.user_id = p_user and e.source = 'GRANT' and e.product_id is null
        and e.status in ('ACTIVE', 'TRIAL', 'GRACE', 'CANCELLED_ACTIVE');
  end if;

  -- The phone row.
  if v_lifetime_start is not null then
    insert into public.entitlements as e (user_id, source, status, grants, product_id, starts_at, expires_at, verified_at, note)
      values (p_user, 'GRANT', 'ACTIVE', array['LOCAL_PRO'], 'lucento_pro_lifetime', v_lifetime_start, null, v_now,
              'Complimentary (Mission Control)')
      on conflict (user_id, source, (coalesce(product_id, ''))) do update
        set status = 'ACTIVE', grants = excluded.grants, starts_at = excluded.starts_at, expires_at = null,
            verified_at = excluded.verified_at, note = excluded.note, test_purchase = false;
  else
    -- A Lifetime grant only ever ends by being taken back.
    update public.entitlements e
      set status = 'REVOKED', expires_at = v_now, verified_at = v_now
      where e.user_id = p_user and e.source = 'GRANT' and e.product_id = 'lucento_pro_lifetime'
        and e.status in ('ACTIVE', 'TRIAL', 'GRACE', 'CANCELLED_ACTIVE');
  end if;

  return mc.grant_rows(p_user);
end;
$$;

-- ------------------------------------------------------------------------------------------------ account deletion
-- The same statement as public.delete_my_account (20261109090000): deleting the auth.users row removes everything the
-- account owns through the cascading foreign keys, in one transaction.
create or replace function mc.delete_account_now(p_user uuid)
returns text
language plpgsql
volatile
security definer
set search_path = ''
as $$
begin
  delete from auth.users u where u.id = p_user;
  if not found then
    return 'already_gone';
  end if;
  return 'deleted';
end;
$$;

-- ------------------------------------------------------------------------------------------------ jobs (pg_cron, part 4)
-- Hourly: which signed-in accounts showed activity since the last run. auth.sessions.refreshed_at is overwritten on
-- every refresh, so it has to be read at least hourly to keep each day; synced writes and AI requests are read the same
-- way. Admin logins are never recorded.
create or replace function mc.capture_activity()
returns integer
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_tz text := mc.tz();
  v_since timestamptz;
  v_run bigint;
  v_n integer;
begin
  select coalesce(max(r.started_at) - interval '15 minutes', now() - interval '2 hours') into v_since
    from mc.job_runs r where r.job = 'activity' and r.result = 'ok';
  insert into mc.job_runs (job, result) values ('activity', 'running') returning id into v_run;

  with signals as (
    select s.user_id, greatest(s.refreshed_at at time zone 'UTC', s.updated_at, s.created_at) as at, 'refresh'::text as signal
      from auth.sessions s
      where greatest(s.refreshed_at at time zone 'UTC', s.updated_at, s.created_at) >= v_since
    union all
    select u.id, u.last_sign_in_at, 'sign_in' from auth.users u where u.last_sign_in_at >= v_since
    union all
    select u.id, ls.at, 'sync' from auth.users u cross join lateral (select mc.last_sync(u.id) as at) ls where ls.at >= v_since
    union all
    select a.user_id, a.day::timestamp at time zone 'UTC', 'ai' from public.ai_usage a
      where a.day >= (v_since at time zone 'UTC')::date and a.requests > 0
  )
  insert into mc.account_activity as aa (user_id, day, signals)
  select g.user_id, (g.at at time zone v_tz)::date, array_agg(distinct g.signal order by g.signal)
  from signals g
  where g.at is not null
    and exists (select 1 from auth.users u where u.id = g.user_id)
    and not exists (select 1 from mc.admins a where a.user_id = g.user_id)
  group by g.user_id, (g.at at time zone v_tz)::date
  on conflict (user_id, day) do update
    set signals = array(select distinct s from unnest(aa.signals || excluded.signals) s order by s);
  get diagnostics v_n = row_count;

  update mc.job_runs set finished_at = now(), result = 'ok', detail = jsonb_build_object('days_written', v_n, 'since', v_since)
    where id = v_run;
  return v_n;
end;
$$;

-- Hourly: GRANT rows whose time ran out get their final word (cosmetic: an expired date already grants nothing).
create or replace function mc.expire_grants()
returns integer
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  r record;
  v_n integer := 0;
  v_run bigint;
begin
  insert into mc.job_runs (job, result) values ('grants', 'running') returning id into v_run;
  for r in
    select distinct e.user_id
    from public.entitlements e
    where e.source = 'GRANT' and e.status in ('ACTIVE', 'TRIAL', 'GRACE', 'CANCELLED_ACTIVE')
      and e.expires_at is not null and e.expires_at <= now()
      and exists (select 1 from mc.entitlement_grants g where g.user_id = e.user_id)
  loop
    perform mc.recompute_grants(r.user_id);
    v_n := v_n + 1;
  end loop;
  update mc.job_runs set finished_at = now(), result = 'ok', detail = jsonb_build_object('accounts', v_n) where id = v_run;
  return v_n;
end;
$$;

-- Every 15 minutes: deletions whose 24-hour cool-off has passed. Each runs on its own; one the database refuses is
-- tried three times, then waits for the admin with its error.
create or replace function mc.run_due_deletions()
returns integer
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  r mc.deletion_requests%rowtype;
  v_result text;
  v_n integer := 0;
  v_run bigint;
begin
  insert into mc.job_runs (job, result) values ('deletions', 'running') returning id into v_run;
  for r in
    select * from mc.deletion_requests d
    where d.cancelled_at is null and d.executed_at is null and d.run_after <= now() and d.attempts < 3
    order by d.run_after
    for update skip locked
  loop
    begin
      v_result := mc.delete_account_now(r.user_id);
      update mc.deletion_requests set executed_at = now(), result = v_result, attempts = r.attempts + 1 where id = r.id;
      perform mc.audit('accounts.deletion_executed', r.user_id, r.id::text, r.reason, 'ok', null, null, v_result, r.requested_by);
      v_n := v_n + 1;
    exception when others then
      update mc.deletion_requests set attempts = r.attempts + 1, last_error = left(sqlerrm, 300) where id = r.id;
      perform mc.audit('accounts.deletion_executed', r.user_id, r.id::text, r.reason, 'failed', null, null,
        left(sqlstate || ' ' || sqlerrm, 1000), r.requested_by);
    end;
  end loop;
  update mc.job_runs set finished_at = now(), result = 'ok', detail = jsonb_build_object('deleted', v_n) where id = v_run;
  return v_n;
end;
$$;

-- Nightly (00:20 in Mission Control's time zone): the day's totals into daily_metrics, then the retention rules.
create or replace function mc.daily_rollup(p_day date default null)
returns integer
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_tz text := mc.tz();
  v_day date := coalesce(p_day, (now() at time zone v_tz)::date - 1);
  v_start timestamptz := v_day::timestamp at time zone v_tz;
  v_end timestamptz := (v_day + 1)::timestamp at time zone v_tz;
  v_run bigint;
  v_n integer;
begin
  insert into mc.job_runs (job, result) values ('daily', 'running') returning id into v_run;

  with customers as (
    select u.id, u.created_at from auth.users u where not mc.is_excluded(u.id)
  ), plans as (
    select mc.plan_of(c.id) as plan from customers c where c.created_at < v_end
  ), m (key, value) as (
    values
      ('users_total', (select count(*) from customers c where c.created_at < v_end)::numeric),
      ('users_new', (select count(*) from customers c where c.created_at >= v_start and c.created_at < v_end)::numeric),
      ('dau', (select count(distinct a.user_id) from mc.account_activity a
               where a.day = v_day and not mc.is_excluded(a.user_id))::numeric),
      ('mau', (select count(distinct a.user_id) from mc.account_activity a
               where a.day between v_day - 29 and v_day and not mc.is_excluded(a.user_id))::numeric),
      ('plan_free', (select count(*) from plans p where p.plan = 'free')::numeric),
      ('plan_paid', (select count(*) from plans p where p.plan in ('lifetime', 'subscription'))::numeric),
      ('plan_lifetime', (select count(*) from plans p where p.plan = 'lifetime')::numeric),
      ('plan_subscription', (select count(*) from plans p where p.plan = 'subscription')::numeric),
      ('plan_trial', (select count(*) from plans p where p.plan = 'trial')::numeric),
      ('plan_test', (select count(*) from plans p where p.plan = 'test')::numeric),
      ('plan_complimentary', (select count(*) from plans p where p.plan = 'complimentary')::numeric),
      ('tickets_new', (select count(*) from mc.tickets t where t.created_at >= v_start and t.created_at < v_end)::numeric),
      ('tickets_open', (select count(*) from mc.tickets t where t.status <> 'resolved')::numeric),
      ('emails_sent', (select count(*) from mc.email_events e where e.type = 'email.sent' and e.at >= v_start and e.at < v_end)::numeric)
  )
  insert into mc.daily_metrics (day, key, value)
  select v_day, m.key, m.value from m
  on conflict (day, key) do update set value = excluded.value;
  get diagnostics v_n = row_count;

  -- Retention (DATABASE_PROPOSAL.md §3; the privacy policy states these).
  delete from mc.account_activity a where a.day < (now() at time zone v_tz)::date - 35;
  delete from mc.email_events e where e.at < now() - interval '90 days';
  delete from mc.rate_counters c where c.hour < now() - interval '2 days';
  delete from mc.job_runs j where j.started_at < now() - interval '90 days' and j.id <> v_run;
  delete from mc.tickets t where t.status = 'resolved' and t.resolved_at < now() - interval '365 days';
  delete from mc.email_sends s where s.created_at < now() - interval '365 days';
  update mc.records_access r set closed_at = r.expires_at where r.closed_at is null and r.expires_at < now();

  update mc.job_runs set finished_at = now(), result = 'ok', detail = jsonb_build_object('day', v_day, 'metrics', v_n)
    where id = v_run;
  return v_n;
end;
$$;

-- ------------------------------------------------------------------------------------------------ the phone: support
-- Topic labels, as the app shows them (and the ticket subject starts with).
create or replace function mc.topic_label(p_topic text)
returns text
language sql
immutable
set search_path = ''
as $$
  select case p_topic
    when 'account' then 'Account' when 'billing' then 'Billing and Pro' when 'sync' then 'Sync'
    when 'data_recovery' then 'Data recovery' when 'bug' then 'Bug' when 'idea' then 'Idea' else 'Other'
  end
$$;

-- What the app may attach to a request (the person sees all of it before sending and can leave it out). Anything else
-- is dropped, so the column can never become a channel for other data.
create or replace function mc.clean_diagnostics(p jsonb)
returns jsonb
language sql
immutable
set search_path = ''
as $$
  select coalesce((
    select jsonb_object_agg(d.key, d.value)
    from jsonb_each(case when jsonb_typeof(p) = 'object' then p else '{}'::jsonb end) d
    where (d.key in ('app_version', 'android_release', 'device_model', 'device_manufacturer', 'last_sync_at', 'plan', 'locale')
           and jsonb_typeof(d.value) = 'string' and char_length(d.value #>> '{}') between 1 and 80)
       or (d.key in ('version_code', 'android_sdk') and jsonb_typeof(d.value) = 'number' and (d.value #>> '{}') ~ '^\d{1,10}$')
       or (d.key in ('sync_enabled', 'signed_in') and jsonb_typeof(d.value) = 'boolean')
  ), '{}'::jsonb)
$$;

-- The app's Help › Contact support form (FD-V4). Signed in: the request links to the account and replies go to its email
-- (p_email is ignored). Signed out: an email address is required. Limits: 3 a day per install while signed out, 10 a day
-- per account, and a shared hourly cap on signed-out requests (Settings) so nobody can flood the inbox. p_client_ref is
-- the request's own id, made on the phone: sending it again (a retry after a lost answer) returns the same ticket.
-- Answers {ok, ticket, reply_to} or {ok: false, error}; never anything about another request.
create or replace function public.submit_support_request(
  p_topic text, p_message text, p_email text, p_install_id uuid, p_client_ref uuid, p_diagnostics jsonb default '{}'::jsonb
)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_email text;
  v_message text := btrim(coalesce(p_message, ''));
  v_existing bigint;
  v_id bigint;
  v_first_line text;
begin
  if p_topic is null or p_topic not in ('account', 'billing', 'sync', 'data_recovery', 'bug', 'idea', 'other') then
    return jsonb_build_object('ok', false, 'error', 'invalid_topic');
  end if;
  if char_length(v_message) < 2 then
    return jsonb_build_object('ok', false, 'error', 'message_empty');
  end if;
  if char_length(v_message) > 5000 then
    return jsonb_build_object('ok', false, 'error', 'message_too_long');
  end if;
  if p_install_id is null or p_client_ref is null then
    return jsonb_build_object('ok', false, 'error', 'invalid_request');
  end if;

  -- One decision at a time per install, so two taps can't both pass the limit.
  perform pg_advisory_xact_lock(hashtextextended('mc.support:' || p_install_id::text, 0));

  select t.id into v_existing from mc.tickets t where t.install_id = p_install_id and t.client_ref = p_client_ref;
  if found then
    return jsonb_build_object('ok', true, 'ticket', 'LC-' || v_existing, 'duplicate', true,
      'reply_to', (select coalesce(t.requester_email, u.email) from mc.tickets t left join auth.users u on u.id = t.user_id
                   where t.id = v_existing));
  end if;

  if v_uid is not null then
    select u.email into v_email from auth.users u where u.id = v_uid;
    if not found then
      return jsonb_build_object('ok', false, 'error', 'invalid_request');
    end if;
    if (select count(*) from mc.tickets t where t.user_id = v_uid and t.channel = 'in_app'
        and t.created_at > now() - interval '24 hours') >= 10 then
      return jsonb_build_object('ok', false, 'error', 'rate_limited');
    end if;
  end if;

  if v_uid is null or v_email is null then
    v_email := lower(btrim(coalesce(p_email, '')));
    if char_length(v_email) > 254 or v_email !~ '^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$' then
      return jsonb_build_object('ok', false, 'error', 'invalid_email');
    end if;
  end if;

  if v_uid is null then
    if (select count(*) from mc.tickets t where t.install_id = p_install_id and t.user_id is null
        and t.created_at > now() - interval '24 hours') >= 3 then
      return jsonb_build_object('ok', false, 'error', 'rate_limited');
    end if;
    perform pg_advisory_xact_lock(hashtextextended('mc.support:signed_out', 0));
    if (select count(*) from mc.tickets t where t.channel = 'in_app' and t.user_id is null
        and t.created_at > now() - interval '1 hour') >= mc.setting_int('support_signed_out_hourly_cap', 20) then
      return jsonb_build_object('ok', false, 'error', 'busy');
    end if;
  end if;

  v_first_line := btrim(split_part(regexp_replace(v_message, '[[:cntrl:]]+', E'\n', 'g'), E'\n', 1));
  insert into mc.tickets (channel, user_id, requester_email, topic, subject, install_id, client_ref, diagnostics)
    values ('in_app', v_uid, case when v_uid is null or (select u.email from auth.users u where u.id = v_uid) is null then v_email end,
            p_topic,
            left(mc.topic_label(p_topic) || case when v_first_line <> '' then ': ' || v_first_line else '' end, 120),
            p_install_id, p_client_ref, mc.clean_diagnostics(p_diagnostics))
    returning id into v_id;
  insert into mc.ticket_messages (ticket_id, kind, body_text) values (v_id, 'inbound', v_message);

  return jsonb_build_object('ok', true, 'ticket', 'LC-' || v_id, 'reply_to', v_email);
end;
$$;

-- ------------------------------------------------------------------------------------------------ the phone: version
-- Once a day for a signed-in account (FD-V7): the app's version, Android's API level and the phone model, so Mission
-- Control can say which version someone runs. Calls within the hour with nothing changed are ignored.
create or replace function public.report_client(
  p_app_version text, p_version_code integer, p_android_sdk integer, p_device_model text
)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_model text := left(btrim(regexp_replace(coalesce(p_device_model, ''), '[[:cntrl:]]+', ' ', 'g')), 80);
  v_row mc.account_clients%rowtype;
begin
  if v_uid is null then
    raise exception 'Sign in to report the app version' using errcode = '28000';
  end if;
  if p_app_version is null or p_app_version !~ '^[0-9A-Za-z][0-9A-Za-z._+-]{0,39}$'
     or p_version_code is null or p_version_code < 1
     or p_android_sdk is null or p_android_sdk not between 21 and 100 then
    return jsonb_build_object('ok', false, 'error', 'invalid');
  end if;
  if v_model = '' then
    v_model := 'unknown';
  end if;
  select * into v_row from mc.account_clients c where c.user_id = v_uid for update;
  if found and v_row.last_seen_at > now() - interval '1 hour' and v_row.app_version = p_app_version
     and v_row.version_code = p_version_code and v_row.android_sdk = p_android_sdk and v_row.device_model = v_model then
    return jsonb_build_object('ok', true, 'recorded', false);
  end if;
  insert into mc.account_clients as c (user_id, app_version, version_code, android_sdk, device_model, first_seen_at, last_seen_at)
    values (v_uid, p_app_version, p_version_code, p_android_sdk, v_model, now(), now())
    on conflict (user_id) do update
      set app_version = excluded.app_version, version_code = excluded.version_code, android_sdk = excluded.android_sdk,
          device_model = excluded.device_model, last_seen_at = excluded.last_seen_at;
  return jsonb_build_object('ok', true, 'recorded', true);
end;
$$;

-- ------------------------------------------------------------------------------------------------ privileges
revoke all on all functions in schema mc from public, anon, authenticated, service_role;
revoke all on function public.submit_support_request(text, text, text, uuid, uuid, jsonb),
  public.report_client(text, integer, integer, text)
  from public, anon, authenticated, service_role;
grant execute on function public.submit_support_request(text, text, text, uuid, uuid, jsonb) to anon, authenticated;
grant execute on function public.report_client(text, integer, integer, text) to authenticated;
