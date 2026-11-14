-- Lucento security remediation — production apply, prepared 2026-11-10 (docs/security/OWNER_ACTIONS_REQUIRED.md §13).
-- Project: lucento (hmtcuttadbgbldvzsfhz). How: Supabase → SQL Editor → New query → paste this whole file → Run.
-- All four migrations run in ONE transaction: if any statement fails, nothing changes. Then tell Claude "applied" and it
-- runs the read-only checks of §14.
-- Already done before this file (2026-11-10, owner decision): 12-month GRANT rows for jerald23simagala@gmail.com and
-- rachelretas20@gmail.com, so both keep syncing under the Cloud write gate.
begin;

-- ==================================================================================== 20261111090000_cloud_write_gate_and_quotas.sql
-- Security audit F-01 (docs/security/FINAL_SECURITY_AUDIT.md, docs/security/REMEDIATION_REPORT.md): the paid Cloud plan
-- is enforced by the database, and what one account (or a caller who isn't signed in) can store is bounded.
--
--   1. Cloud write gate. Every INSERT and UPDATE policy of the eight synced tables (wallets, categories, transactions,
--      budgets, goals, goal_contributions, recurring, ledger_settings) also requires the CLOUD capability, read from
--      public.entitlements on the server (lucento_private.has_capability) — never from the request:
--        * an account writing its own ledger needs CLOUD itself;
--        * a Partner writing the Owner's ledger (transactions only, as before) needs the OWNER's CLOUD, resolved from
--          the membership row (public.space_members), never from an argument. The approved lenient rule
--          (docs/MONETIZATION.md §6.4: "the existing Partner stays and keeps syncing" after the Owner's plan lapses) is
--          the plan_limits switch 'partner_sync_after_owner_lapse' (1 = lenient, the default; 0 = strict).
--      SELECT and DELETE are not gated: a lapsed account still restores (reads) everything and can clean up (hard
--      DELETE). A refused write fails with SQLSTATE 42501 (new row violates row-level security policy); the app pauses
--      uploads and keeps every local change (SyncRepository, docs/SYNC.md "Plan refusals").
--   2. Size bounds, as CHECK constraints on live rows (NOT VALID, like every financial check: legacy rows are left alone
--      and a soft delete always succeeds): notes ≤ 500 characters, names ≤ 100, ids ≤ 128, short codes ≤ 64. The app
--      writes notes ≤ 200 and names ≤ 40 (TransactionRepository.MAX_NOTE, *Repository.MAX_NAME).
--   3. Per-account row ceilings and a per-statement batch cap, enforced by statement-level AFTER triggers on a counter
--      row per (account, table). The counter is changed with INSERT … ON CONFLICT DO UPDATE, which takes that row's lock
--      until the transaction ends, so two concurrent inserts for one account are serialised and can't both slip under
--      the ceiling (no COUNT(*) race). Limits live in lucento_private.plan_limits (remote configuration). Refusals:
--      SQLSTATE LU001 (ceiling) and LU002 (batch too large).
--   4. usage_events: the anonymous INSERT grant and its WITH CHECK (true) policy are gone. Counts go through
--      public.record_usage_event, a SECURITY DEFINER function with a per-install daily cap AND a project-wide daily cap,
--      so inventing new install ids can't raise the total that can be stored.
--
-- Not destructive: no row is changed or removed; policies are replaced with stricter ones of the same names.
-- Before production: run docs/security/OWNER_ACTIONS_REQUIRED.md §13's pre-check (accounts that sync without CLOUD).

-- ================================================================================================ 1. Cloud write gate

-- Does the signed-in caller hold CLOUD now? One question per statement in a policy ((select …) is an initPlan).
create or replace function lucento_private.caller_has_cloud()
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select coalesce(lucento_private.has_capability((select auth.uid()), 'CLOUD'), false)
$$;

-- The Shared Spaces the caller may WRITE to as a Partner: their memberships (public.space_members, written only by the
-- invitation RPCs) whose Owner holds CLOUD — or every membership while the lenient lapse rule is on. Answers only about
-- the caller; the Owner's id comes from the membership row, never from the request.
create or replace function lucento_private.cloud_partner_spaces()
returns uuid[]
language sql
stable
security definer
set search_path = ''
as $$
  select coalesce(array_agg(m.owner_id), '{}'::uuid[])
  from public.space_members m
  where m.partner_id = (select auth.uid())
    and (
      lucento_private.has_capability(m.owner_id, 'CLOUD')
      or lucento_private.plan_limit('partner_sync_after_owner_lapse', 1) = 1
    )
$$;

revoke all on function lucento_private.caller_has_cloud(), lucento_private.cloud_partner_spaces() from public, anon;
-- Policies run with the querying role's privileges, so authenticated must be able to execute what they call.
grant execute on function lucento_private.caller_has_cloud(), lucento_private.cloud_partner_spaces() to authenticated;

insert into lucento_private.plan_limits (key, value_int) values
  ('partner_sync_after_owner_lapse', 1)
on conflict (key) do nothing;

-- Owner policies: same names, roles and USING as before (20261028190000, 20261026120000); WITH CHECK adds CLOUD.
drop policy if exists "wallets_insert_own" on public.wallets;
drop policy if exists "wallets_update_own" on public.wallets;
create policy "wallets_insert_own" on public.wallets for insert
  with check ((select auth.uid()) = user_id and (select lucento_private.caller_has_cloud()));
create policy "wallets_update_own" on public.wallets for update
  using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id and (select lucento_private.caller_has_cloud()));

drop policy if exists "categories_insert_own" on public.categories;
drop policy if exists "categories_update_own" on public.categories;
create policy "categories_insert_own" on public.categories for insert
  with check ((select auth.uid()) = user_id and (select lucento_private.caller_has_cloud()));
create policy "categories_update_own" on public.categories for update
  using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id and (select lucento_private.caller_has_cloud()));

drop policy if exists "transactions_insert_own" on public.transactions;
drop policy if exists "transactions_update_own" on public.transactions;
create policy "transactions_insert_own" on public.transactions for insert
  with check ((select auth.uid()) = user_id and (select lucento_private.caller_has_cloud()));
create policy "transactions_update_own" on public.transactions for update
  using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id and (select lucento_private.caller_has_cloud()));

drop policy if exists "budgets_insert_own" on public.budgets;
drop policy if exists "budgets_update_own" on public.budgets;
create policy "budgets_insert_own" on public.budgets for insert
  with check ((select auth.uid()) = user_id and (select lucento_private.caller_has_cloud()));
create policy "budgets_update_own" on public.budgets for update
  using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id and (select lucento_private.caller_has_cloud()));

drop policy if exists "goals_insert_own" on public.goals;
drop policy if exists "goals_update_own" on public.goals;
create policy "goals_insert_own" on public.goals for insert
  with check ((select auth.uid()) = user_id and (select lucento_private.caller_has_cloud()));
create policy "goals_update_own" on public.goals for update
  using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id and (select lucento_private.caller_has_cloud()));

drop policy if exists "goal_contributions_insert_own" on public.goal_contributions;
drop policy if exists "goal_contributions_update_own" on public.goal_contributions;
create policy "goal_contributions_insert_own" on public.goal_contributions for insert
  with check ((select auth.uid()) = user_id and (select lucento_private.caller_has_cloud()));
create policy "goal_contributions_update_own" on public.goal_contributions for update
  using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id and (select lucento_private.caller_has_cloud()));

drop policy if exists "recurring_insert_own" on public.recurring;
drop policy if exists "recurring_update_own" on public.recurring;
create policy "recurring_insert_own" on public.recurring for insert to authenticated
  with check ((select auth.uid()) = user_id and (select lucento_private.caller_has_cloud()));
create policy "recurring_update_own" on public.recurring for update to authenticated
  using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id and (select lucento_private.caller_has_cloud()));

drop policy if exists "ledger_settings_insert_own" on public.ledger_settings;
create policy "ledger_settings_insert_own" on public.ledger_settings for insert to authenticated
  with check (user_id = (select auth.uid()) and (select lucento_private.caller_has_cloud()));

-- Partner policies (20261105120000_lucento_shared.sql): USING keeps every membership (a Partner always reaches the rows
-- of their space, e.g. to read them); WITH CHECK requires a space the Partner may write to.
drop policy if exists "transactions_insert_partner" on public.transactions;
drop policy if exists "transactions_update_partner" on public.transactions;
create policy "transactions_insert_partner" on public.transactions for insert to authenticated
  with check (user_id = any ((select lucento_private.cloud_partner_spaces())::uuid[]));
create policy "transactions_update_partner" on public.transactions for update to authenticated
  using (user_id = any ((select lucento_private.partner_spaces())::uuid[]))
  with check (user_id = any ((select lucento_private.cloud_partner_spaces())::uuid[]));

-- ================================================================================================ 2. size bounds
-- Live rows only and NOT VALID (20261101180000): a soft delete always succeeds, legacy rows are not rechecked.

alter table public.wallets drop constraint if exists wallets_text_bounded;
alter table public.wallets add constraint wallets_text_bounded check (
  deleted_at is not null or (char_length(id) <= 128 and char_length(name) <= 100)
) not valid;

alter table public.categories drop constraint if exists categories_text_bounded;
alter table public.categories add constraint categories_text_bounded check (
  deleted_at is not null or (char_length(id) <= 128 and char_length(name) <= 100 and char_length(icon_key) <= 64)
) not valid;

alter table public.transactions drop constraint if exists transactions_text_bounded;
alter table public.transactions add constraint transactions_text_bounded check (
  deleted_at is not null or (
    char_length(id) <= 128 and char_length(note) <= 500
    and (transfer_group is null or char_length(transfer_group) <= 128)
  )
) not valid;

alter table public.budgets drop constraint if exists budgets_text_bounded;
alter table public.budgets add constraint budgets_text_bounded check (
  deleted_at is not null or char_length(id) <= 128
) not valid;

alter table public.goals drop constraint if exists goals_text_bounded;
alter table public.goals add constraint goals_text_bounded check (
  deleted_at is not null or (
    char_length(id) <= 128 and char_length(name) <= 100 and (scene_key is null or char_length(scene_key) <= 64)
  )
) not valid;

alter table public.goal_contributions drop constraint if exists goal_contributions_text_bounded;
alter table public.goal_contributions add constraint goal_contributions_text_bounded check (
  deleted_at is not null or (char_length(id) <= 128 and char_length(note) <= 500)
) not valid;

alter table public.recurring drop constraint if exists recurring_text_bounded;
alter table public.recurring add constraint recurring_text_bounded check (
  deleted_at is not null or (char_length(id) <= 128 and char_length(name) <= 100)
) not valid;

-- ================================================================================================ 3. row ceilings
-- One counter per (account, table): rows stored, soft-deleted ones included (they occupy space until hard-deleted).
create table if not exists lucento_private.ledger_row_counts (
  user_id uuid not null references auth.users (id) on delete cascade,
  table_name text not null,
  row_count bigint not null default 0 check (row_count >= 0),
  primary key (user_id, table_name)
);
alter table lucento_private.ledger_row_counts enable row level security;
revoke all on table lucento_private.ledger_row_counts from public, anon, authenticated;

-- Remote configuration. Generous for real use (200,000 transactions is 50 a day for eleven years), small enough that
-- no single account can fill the project.
insert into lucento_private.plan_limits (key, value_int) values
  ('max_rows_wallets', 1000),
  ('max_rows_categories', 2000),
  ('max_rows_transactions', 200000),
  ('max_rows_budgets', 50000),
  ('max_rows_goals', 1000),
  ('max_rows_goal_contributions', 50000),
  ('max_rows_recurring', 5000),
  -- Rows one INSERT or UPDATE statement may write. The app sends at most 500 per request (SyncRepository.PUSH_CHUNK).
  ('max_rows_per_write', 1000)
on conflict (key) do nothing;

-- Existing rows, counted once.
insert into lucento_private.ledger_row_counts (user_id, table_name, row_count)
select user_id, t, n from (
  select user_id, 'wallets' as t, count(*) as n from public.wallets group by user_id
  union all select user_id, 'categories', count(*) from public.categories group by user_id
  union all select user_id, 'transactions', count(*) from public.transactions group by user_id
  union all select user_id, 'budgets', count(*) from public.budgets group by user_id
  union all select user_id, 'goals', count(*) from public.goals group by user_id
  union all select user_id, 'goal_contributions', count(*) from public.goal_contributions group by user_id
  union all select user_id, 'recurring', count(*) from public.recurring group by user_id
) existing
on conflict (user_id, table_name) do update set row_count = excluded.row_count;

-- AFTER INSERT, once per statement, with the statement's inserted rows (an upsert's updated rows are not in it).
create or replace function lucento_private.enforce_row_quota()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_batch bigint;
  v_limit bigint;
  v_over uuid;
begin
  select count(*) into v_batch from new_rows;
  if v_batch = 0 then
    return null;
  end if;
  -- The batch cap is for statements a client sends (trigger depth 1); a cascade of a hard delete is never capped.
  if pg_catalog.pg_trigger_depth() = 1 and v_batch > lucento_private.plan_limit('max_rows_per_write', 1000) then
    raise exception 'lucento_limit:batch_too_large' using errcode = 'LU002',
      hint = 'Send at most max_rows_per_write rows per request.';
  end if;
  v_limit := lucento_private.plan_limit('max_rows_' || tg_table_name, 200000);
  -- The upsert locks each account's counter row until commit: concurrent writers of one account queue here.
  with added as (
    select n.user_id, count(*) as k from new_rows n group by n.user_id
  ), counted as (
    insert into lucento_private.ledger_row_counts as c (user_id, table_name, row_count)
    select a.user_id, tg_table_name, a.k from added a
    on conflict (user_id, table_name) do update set row_count = c.row_count + excluded.row_count
    returning c.user_id, c.row_count
  )
  select counted.user_id into v_over from counted where counted.row_count > v_limit limit 1;
  if v_over is not null then
    raise exception 'lucento_limit:rows_%', tg_table_name using errcode = 'LU001',
      hint = 'This ledger holds the most rows of this kind one account may store.';
  end if;
  return null;
end;
$$;

-- AFTER DELETE, once per statement (hard deletes: cleanup by the account, cascades, account deletion).
create or replace function lucento_private.release_row_quota()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  update lucento_private.ledger_row_counts c
    set row_count = greatest(0, c.row_count - d.k)
    from (select o.user_id, count(*) as k from old_rows o group by o.user_id) d
    where c.user_id = d.user_id and c.table_name = tg_table_name;
  return null;
end;
$$;

-- AFTER UPDATE, once per statement: the same batch cap for upserts whose rows all existed.
create or replace function lucento_private.enforce_write_batch()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  -- Only statements a client sends (trigger depth 1): an ON DELETE SET NULL cascade may touch many rows legitimately.
  if pg_catalog.pg_trigger_depth() = 1
     and (select count(*) from new_rows) > lucento_private.plan_limit('max_rows_per_write', 1000) then
    raise exception 'lucento_limit:batch_too_large' using errcode = 'LU002',
      hint = 'Send at most max_rows_per_write rows per request.';
  end if;
  return null;
end;
$$;

revoke all on function lucento_private.enforce_row_quota(), lucento_private.release_row_quota(),
  lucento_private.enforce_write_batch() from public, anon, authenticated;

do $$
declare
  t text;
begin
  foreach t in array array['wallets', 'categories', 'transactions', 'budgets', 'goals', 'goal_contributions', 'recurring']
  loop
    execute format('drop trigger if exists trg_%1$s_row_quota on public.%1$I', t);
    execute format('create trigger trg_%1$s_row_quota after insert on public.%1$I referencing new table as new_rows '
                   'for each statement execute function lucento_private.enforce_row_quota()', t);
    execute format('drop trigger if exists trg_%1$s_row_release on public.%1$I', t);
    execute format('create trigger trg_%1$s_row_release after delete on public.%1$I referencing old table as old_rows '
                   'for each statement execute function lucento_private.release_row_quota()', t);
    execute format('drop trigger if exists trg_%1$s_write_batch on public.%1$I', t);
    execute format('create trigger trg_%1$s_write_batch after update on public.%1$I referencing new table as new_rows '
                   'for each statement execute function lucento_private.enforce_write_batch()', t);
  end loop;
end
$$;

-- ================================================================================================ 4. usage_events
-- No client writes the table any more; the app calls record_usage_event (SupabaseUsageRemote).
drop policy if exists "usage_events: insert" on public.usage_events;
revoke all on table public.usage_events from public, anon, authenticated;

create table if not exists lucento_private.usage_event_quota (
  day date not null,
  -- The all-zero id is the project-wide total for the day.
  install_id uuid not null,
  n integer not null default 0,
  primary key (day, install_id)
);
alter table lucento_private.usage_event_quota enable row level security;
revoke all on table lucento_private.usage_event_quota from public, anon, authenticated;

insert into lucento_private.plan_limits (key, value_int) values
  ('usage_events_per_install_daily', 50),
  ('usage_events_daily_total', 20000)
on conflict (key) do nothing;

-- Stores one count when the install and the project are both under today's cap; otherwise drops it silently (false),
-- so a capped device never retries. The event allow-list and size checks are the table's own constraints (22023 /
-- 23514 for a malformed call). Callable by anon (counts never need an account) and authenticated.
create or replace function public.record_usage_event(
  p_install_id uuid,
  p_event text,
  p_props jsonb default '{}'::jsonb,
  p_app_version text default null
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_day date := (pg_catalog.now() at time zone 'utc')::date;
  v_total integer;
  v_mine integer;
begin
  if p_install_id is null or p_install_id = '00000000-0000-0000-0000-000000000000'::uuid then
    raise exception 'record_usage_event: install id required' using errcode = '22023';
  end if;
  -- The project-wide row first: every caller queues on it, so the day's total is exact under concurrency.
  insert into lucento_private.usage_event_quota as q (day, install_id, n)
    values (v_day, '00000000-0000-0000-0000-000000000000'::uuid, 1)
    on conflict (day, install_id) do update set n = q.n + 1
    returning q.n into v_total;
  if v_total > lucento_private.plan_limit('usage_events_daily_total', 20000) then
    return false;
  end if;
  insert into lucento_private.usage_event_quota as q (day, install_id, n)
    values (v_day, p_install_id, 1)
    on conflict (day, install_id) do update set n = q.n + 1
    returning q.n into v_mine;
  if v_mine > lucento_private.plan_limit('usage_events_per_install_daily', 50) then
    return false;
  end if;
  insert into public.usage_events (install_id, event, props, app_version)
    values (p_install_id, p_event, coalesce(p_props, '{}'::jsonb), p_app_version);
  return true;
end;
$$;

revoke all on function public.record_usage_event(uuid, text, jsonb, text) from public;
grant execute on function public.record_usage_event(uuid, text, jsonb, text) to anon, authenticated;

-- The daily purge (20261029010000, pg_cron) also drops quota rows older than two days.
create or replace function public.purge_usage_events()
returns void
language sql
set search_path = ''
as $$
  delete from public.usage_events
  where created_at < pg_catalog.now() - interval '90 days';
  delete from lucento_private.usage_event_quota
  where day < ((pg_catalog.now() at time zone 'utc')::date - 2);
$$;

revoke all on function public.purge_usage_events() from public, anon, authenticated;

-- ==================================================================================== 20261111100000_play_purchase_integrity.sql
-- Security audit F-02, F-03 and F-12 (docs/security/FINAL_SECURITY_AUDIT.md, docs/security/REMEDIATION_REPORT.md):
-- the integrity of Google Play purchases on the server. Replaces public.apply_play_purchase and
-- public.revoke_play_purchase (20261108090000_play_purchases.sql); service role only, as before.
--
--   F-02  A refund is permanent. lucento_private.revoked_purchases keeps the sha256 of every refunded purchase token,
--         with no account column (no personal data, and nothing that cascades when an account is deleted).
--         revoke_play_purchase writes it — also for a purchase no account holds yet — and apply_play_purchase reads it,
--         so a refunded purchase verified again (on the same account, a new account, after the refunded account was
--         deleted, or by a stale notification) is stored as REVOKED and grants nothing.
--   F-03  A purchase binds to the account it was made for. Play reports the obfuscated account id the app passed at
--         purchase (sha256 of the Lucento user id). When it is present and isn't the verifying account's, the answer is
--         'other_account' and nothing is linked or written — on the first verification too (it used to link to whoever
--         verified first). A purchase made while signed out carries no id; it links to the first account that verifies
--         it (docs/MONETIZATION.md §5.4, the explicit signed-out policy). The existing re-link rule is unchanged.
--   F-12  Test purchases (Play license testers) never grant a plan unless this project allows them:
--         lucento_private.plan_limits 'allow_test_purchases' (0 = refuse, the default; 1 = allow, for lucento-test and an
--         owner's internal-testing window). A refused one answers 'test_purchase_refused'. Rows that were granted carry
--         entitlements.test_purchase = true, so they can be found and ended in one statement.
--
-- Additive: one private table, one column, one plan_limits key; two functions replaced (one gains a defaulted argument,
-- so the endpoints deployed today keep calling it unchanged).

-- ------------------------------------------------------------------------------------------------ refund tombstones
create table if not exists lucento_private.revoked_purchases (
  purchase_token_hash text primary key check (purchase_token_hash ~ '^[0-9a-f]{64}$'),
  revoked_at timestamptz not null default now()
);
alter table lucento_private.revoked_purchases enable row level security;
revoke all on table lucento_private.revoked_purchases from public, anon, authenticated;

-- Refunds already recorded on entitlement rows.
insert into lucento_private.revoked_purchases (purchase_token_hash, revoked_at)
select e.purchase_token_hash, min(e.verified_at)
from public.entitlements e
where e.status = 'REVOKED' and e.purchase_token_hash is not null
group by e.purchase_token_hash
on conflict (purchase_token_hash) do nothing;

-- ------------------------------------------------------------------------------------------------ test purchases
alter table public.entitlements add column if not exists test_purchase boolean not null default false;

insert into lucento_private.plan_limits (key, value_int) values ('allow_test_purchases', 0)
on conflict (key) do nothing;

-- ------------------------------------------------------------------------------------------------ the row helper
-- As 20261108090000, plus the test flag (a row keeps the flag of the purchase that wrote it).
drop function if exists public.lucento_write_play_row(uuid, text, text, text[], text, text, text, timestamptz, timestamptz);
create or replace function public.lucento_write_play_row(
  p_user uuid, p_source text, p_status text, p_grants text[], p_product_id text, p_base_plan_id text,
  p_token_hash text, p_starts_at timestamptz, p_expires_at timestamptz, p_test_purchase boolean default false
)
returns void
language sql
security definer
set search_path = ''
as $$
  insert into public.entitlements as e
    (user_id, source, status, grants, product_id, base_plan_id, purchase_token_hash, starts_at, expires_at, verified_at,
     test_purchase)
  values
    (p_user, p_source, p_status, p_grants, p_product_id, p_base_plan_id, p_token_hash, p_starts_at, p_expires_at, now(),
     p_test_purchase)
  on conflict (user_id, source, (coalesce(product_id, ''))) do update
    set status = excluded.status,
        grants = excluded.grants,
        base_plan_id = excluded.base_plan_id,
        purchase_token_hash = excluded.purchase_token_hash,
        starts_at = excluded.starts_at,
        expires_at = excluded.expires_at,
        test_purchase = excluded.test_purchase,
        verified_at = now()
    where e.purchase_token_hash is not distinct from excluded.purchase_token_hash
       or excluded.status in ('ACTIVE', 'TRIAL', 'GRACE', 'CANCELLED_ACTIVE', 'PENDING')
       or e.status not in ('ACTIVE', 'TRIAL', 'GRACE', 'CANCELLED_ACTIVE', 'PENDING', 'ON_HOLD', 'PAUSED')
$$;

-- ------------------------------------------------------------------------------------------------ apply
drop function if exists public.apply_play_purchase(uuid, text, text, text, text, text, text, timestamptz, timestamptz);
create or replace function public.apply_play_purchase(
  -- The account verifying (verify.ts). Null: the account this purchase is already linked to (rtdn.ts re-verifying).
  p_user uuid,
  -- sha256 of the purchase token, lower-case hex. The token itself is never stored.
  p_token_hash text,
  p_order_id text,
  p_product_id text,
  -- What Play reports as the purchase's obfuscated external account id: sha256 of the Lucento user id the app passed at
  -- purchase, or null for a purchase made while signed out. Read from Play by the endpoint, never from the app.
  p_obfuscated_account_id text,
  -- Play's state in Lucento's vocabulary (the endpoint maps subscriptionsv2 / productsv2 states onto these).
  p_status text,
  p_base_plan_id text,
  -- When the purchase was made (Lifetime's included year counts from it, so a reinstall or a re-link can't restart it).
  p_purchased_at timestamptz,
  -- Subscriptions: the end of the current period. Ignored for Lifetime.
  p_expires_at timestamptz,
  -- Play marks license-tester purchases (subscriptionsv2 testPurchase, productsv2 testPurchaseContext).
  p_test_purchase boolean default false
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid := p_user;
  v_link public.purchase_links%rowtype;
  v_status text := p_status;
  v_starts timestamptz := coalesce(p_purchased_at, now());
  v_services_status text;
  v_test boolean := coalesce(p_test_purchase, false);
begin
  if p_token_hash is null or p_token_hash !~ '^[0-9a-f]{64}$' then
    raise exception 'apply_play_purchase: token hash must be 64 lower-case hex characters' using errcode = '22023';
  end if;
  if p_product_id is null or p_product_id not in ('lucento_pro', 'lucento_pro_lifetime', 'lucento_cloud') then
    raise exception 'apply_play_purchase: unknown product %', p_product_id using errcode = '22023';
  end if;
  if p_status is null or p_status not in ('ACTIVE', 'TRIAL', 'GRACE', 'CANCELLED_ACTIVE', 'ON_HOLD', 'PAUSED', 'PENDING', 'EXPIRED', 'REVOKED') then
    raise exception 'apply_play_purchase: unknown status %', p_status using errcode = '22023';
  end if;
  if p_product_id <> 'lucento_pro_lifetime' and v_status not in ('PENDING', 'REVOKED') and p_expires_at is null then
    raise exception 'apply_play_purchase: a subscription needs its period end' using errcode = '22023';
  end if;
  if p_obfuscated_account_id is not null and p_obfuscated_account_id !~ '^[0-9a-f]{64}$' then
    raise exception 'apply_play_purchase: obfuscated account id must be 64 lower-case hex characters' using errcode = '22023';
  end if;

  -- F-12: a license tester's purchase grants nothing on a project that doesn't allow them.
  if v_test and lucento_private.plan_limit('allow_test_purchases', 0) <> 1 then
    return jsonb_build_object('result', 'test_purchase_refused');
  end if;

  -- F-02: a refunded purchase stays refunded — a later verification, a stale notification, a move to another account,
  -- or a new account after the refunded one was deleted never revives it. Read before the linking rule.
  if exists (select 1 from lucento_private.revoked_purchases rp where rp.purchase_token_hash = p_token_hash)
     or exists (select 1 from public.entitlements e where e.purchase_token_hash = p_token_hash and e.status = 'REVOKED') then
    v_status := 'REVOKED';
  end if;

  -- ---------------------------------------------------------------------------------------------- the linking rule
  select * into v_link from public.purchase_links l where l.purchase_token_hash = p_token_hash for update;

  if v_user is null then
    -- A notification about a purchase no account has verified yet: nothing to write. The app verifies it when it opens.
    if not found then
      return jsonb_build_object('result', 'unlinked');
    end if;
    v_user := v_link.user_id;
  else
    -- F-03: Play says this purchase was made for a different Lucento account (two accounts on one Google Play account,
    -- or someone else's token). Never linked here, never written; the buyer's own account verifies it.
    if p_obfuscated_account_id is not null
       and p_obfuscated_account_id <> encode(sha256(convert_to(lower(v_user::text), 'UTF8')), 'hex') then
      return jsonb_build_object('result', 'other_account');
    end if;

    if not found then
      -- First verification: made for this account, or made while signed out (no id). A concurrent first verification of
      -- the same purchase loses the insert and is decided by the rule below, against the winner's row.
      insert into public.purchase_links (purchase_token_hash, order_id, product_id, user_id, obfuscated_account_id)
      values (p_token_hash, p_order_id, p_product_id, v_user, p_obfuscated_account_id)
      on conflict (purchase_token_hash) do nothing;
      select * into v_link from public.purchase_links l where l.purchase_token_hash = p_token_hash for update;
    end if;

    if v_link.user_id <> v_user then
      -- Another account holds it. It moves only when the purchase was made *for* this account (Play carries the hash the
      -- app sent) and the other link is older than 30 days; a third move needs support.
      if p_obfuscated_account_id is distinct from encode(sha256(convert_to(lower(v_user::text), 'UTF8')), 'hex')
         or v_link.linked_at > now() - interval '30 days' then
        return jsonb_build_object('result', 'linked_elsewhere');
      end if;
      if v_link.relinked_count >= 2 then
        return jsonb_build_object('result', 'review_required');
      end if;
      delete from public.entitlements e where e.user_id = v_link.user_id and e.purchase_token_hash = p_token_hash;
      update public.purchase_links l
        set user_id = v_user, linked_at = now(), relinked_count = l.relinked_count + 1,
            obfuscated_account_id = coalesce(p_obfuscated_account_id, l.obfuscated_account_id),
            order_id = coalesce(p_order_id, l.order_id)
        where l.purchase_token_hash = p_token_hash;
    elsif v_link.order_id is null or v_link.obfuscated_account_id is null then
      update public.purchase_links l
        set order_id = coalesce(l.order_id, p_order_id), obfuscated_account_id = coalesce(l.obfuscated_account_id, p_obfuscated_account_id)
        where l.purchase_token_hash = p_token_hash;
    end if;
  end if;

  -- ---------------------------------------------------------------------------------------------- the rows
  if p_product_id = 'lucento_pro_lifetime' then
    -- Every Pro feature on the phone, forever; and the online services for 365 days from the purchase (D-13).
    v_services_status := case
      when v_status in ('PENDING', 'REVOKED', 'EXPIRED') then v_status
      when v_starts + interval '365 days' > now() then 'ACTIVE'
      else 'EXPIRED'
    end;
    perform public.lucento_write_play_row(v_user, 'PLAY_LIFETIME', v_status, array['LOCAL_PRO'], p_product_id, null, p_token_hash, v_starts, null, v_test);
    perform public.lucento_write_play_row(v_user, 'PLAY_LIFETIME_SERVICES', v_services_status, array['CLOUD', 'AI_PRO', 'SHARED'],
      p_product_id, null, p_token_hash, v_starts, v_starts + interval '365 days', v_test);
  elsif p_product_id = 'lucento_pro' then
    perform public.lucento_write_play_row(v_user, 'PLAY_SUBSCRIPTION', v_status, array['LOCAL_PRO', 'CLOUD', 'AI_PRO', 'SHARED'],
      p_product_id, p_base_plan_id, p_token_hash, v_starts, p_expires_at, v_test);
  else
    perform public.lucento_write_play_row(v_user, 'PLAY_CLOUD_ADDON', v_status, array['CLOUD', 'AI_PRO', 'SHARED'],
      p_product_id, p_base_plan_id, p_token_hash, v_starts, p_expires_at, v_test);
  end if;

  return jsonb_build_object('result', 'ok', 'status', v_status);
end;
$$;

-- ------------------------------------------------------------------------------------------------ revoke
-- A refund or chargeback (Play's voided purchases): recorded permanently, then every row of that purchase ends, on
-- whichever account holds it. Returns how many entitlement rows changed, so the daily check can report what it caught.
create or replace function public.revoke_play_purchase(p_token_hash text)
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_n integer;
begin
  if p_token_hash is null or p_token_hash !~ '^[0-9a-f]{64}$' then
    raise exception 'revoke_play_purchase: token hash must be 64 lower-case hex characters' using errcode = '22023';
  end if;
  insert into lucento_private.revoked_purchases (purchase_token_hash) values (p_token_hash)
  on conflict (purchase_token_hash) do nothing;
  update public.entitlements e set status = 'REVOKED', verified_at = now()
    where e.purchase_token_hash = p_token_hash and e.status <> 'REVOKED';
  get diagnostics v_n = row_count;
  return v_n;
end;
$$;

revoke all on function public.apply_play_purchase(uuid, text, text, text, text, text, text, timestamptz, timestamptz, boolean),
  public.lucento_write_play_row(uuid, text, text, text[], text, text, text, timestamptz, timestamptz, boolean),
  public.revoke_play_purchase(text)
  from public, anon, authenticated;
grant execute on function public.apply_play_purchase(uuid, text, text, text, text, text, text, timestamptz, timestamptz, boolean),
  public.revoke_play_purchase(text)
  to service_role;

-- ==================================================================================== 20261111110000_seed_ids_and_default_privileges.sql
-- Security audit F-07 and F-13 (docs/security/FINAL_SECURITY_AUDIT.md, docs/security/REMEDIATION_REPORT.md).
--
--   F-07  Seed category ids are predictable: a fresh install's income categories get "seed-<user id>-<kind>-<slug>"
--         (SyncRepository.assignMissingCategoryIds) and categories.id is a global primary key, so an account that learnt
--         another's user id (a Shared Space shows it) could create that id first and break the other's sync (probe X3).
--         A "seed-" id is now accepted only under the account it names. The app's ids are unchanged, so every existing
--         row, phone and migration keeps working; ids that aren't seed ids (random UUIDs) are not affected.
--         NOT VALID like the other checks: a squatted row already stored is left for the owner to review
--         (docs/security/OWNER_ACTIONS_REQUIRED.md §14 lists the read-only query).
--   F-13  Supabase's stock default privileges give anon and authenticated every privilege (TRUNCATE, REFERENCES,
--         TRIGGER, MAINTAIN included) on each new table, sequence and function that postgres creates in public. Every
--         migration so far revokes and grants explicitly; this makes the safe outcome the default, so a future migration
--         that forgets its revoke exposes nothing. Existing objects keep exactly the grants they have.
--         Objects created by other roles (supabase_admin) keep their own defaults; only postgres may change postgres's.

-- ------------------------------------------------------------------------------------------------ F-07
alter table public.categories drop constraint if exists categories_seed_id_owner;
alter table public.categories add constraint categories_seed_id_owner check (
  id not like 'seed-%' or starts_with(id, 'seed-' || user_id::text || '-')
) not valid;

-- ------------------------------------------------------------------------------------------------ F-13
alter default privileges for role postgres in schema public revoke all on tables from anon, authenticated;
alter default privileges for role postgres in schema public revoke all on sequences from anon, authenticated;
alter default privileges for role postgres in schema public revoke all on functions from public, anon, authenticated;
-- A per-schema entry can only add to the global defaults, and PostgreSQL's global default lets PUBLIC execute every new
-- function: that one must be revoked globally (every schema; each migration already grants EXECUTE explicitly).
alter default privileges for role postgres revoke execute on functions from public;

-- ==================================================================================== 20261111120000_request_quotas.sql
-- Security audit F-11 (docs/security/REMEDIATION_REPORT.md): an account-level limit for the website endpoints that spend
-- a shared external quota. /api/billing/verify asks the Google Play Developer API on every call, so one signed-in script
-- could exhaust Lucento's Play quota and block real verifications. The AI endpoints are already bounded per account by
-- public.consume_ai_request (20261107090000_entitlements.sql) and need nothing here.
--
-- Per account, not per IP address: people behind one network (a household, an office, a carrier NAT) never share a
-- limit. An IP-level limit for requests that aren't signed in is the Vercel Firewall's job
-- (docs/security/OWNER_ACTIONS_REQUIRED.md §7).
--
-- Additive: one private table, one function, one plan_limits key; purge_usage_events also drops old counters.

create table if not exists lucento_private.request_quota (
  user_id uuid not null references auth.users (id) on delete cascade,
  kind text not null,
  day date not null,
  n integer not null default 0,
  primary key (user_id, kind, day)
);
alter table lucento_private.request_quota enable row level security;
revoke all on table lucento_private.request_quota from public, anon, authenticated;

insert into lucento_private.plan_limits (key, value_int) values ('billing_verify_daily', 60)
on conflict (key) do nothing;

-- Counts one request of p_kind for the signed-in caller today (UTC) and answers whether it is within the limit. The
-- refused request is counted too, so a client cannot probe for free. The endpoint calls it with the person's own token.
create or replace function public.consume_request_quota(p_kind text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_n integer;
  v_limit integer;
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  if p_kind is null or p_kind not in ('billing_verify') then
    raise exception 'consume_request_quota: unknown kind %', p_kind using errcode = '22023';
  end if;
  insert into lucento_private.request_quota as q (user_id, kind, day, n)
    values (v_uid, p_kind, (pg_catalog.now() at time zone 'utc')::date, 1)
    on conflict (user_id, kind, day) do update set n = q.n + 1
    returning q.n into v_n;
  v_limit := lucento_private.plan_limit(p_kind || '_daily', 60);
  return jsonb_build_object('allowed', v_n <= v_limit, 'used', v_n, 'limit', v_limit);
end;
$$;

revoke all on function public.consume_request_quota(text) from public, anon;
grant execute on function public.consume_request_quota(text) to authenticated;

-- The daily purge (pg_cron, 20261029010000) also drops request counters older than two days.
create or replace function public.purge_usage_events()
returns void
language sql
set search_path = ''
as $$
  delete from public.usage_events
  where created_at < pg_catalog.now() - interval '90 days';
  delete from lucento_private.usage_event_quota
  where day < ((pg_catalog.now() at time zone 'utc')::date - 2);
  delete from lucento_private.request_quota
  where day < ((pg_catalog.now() at time zone 'utc')::date - 2);
$$;

revoke all on function public.purge_usage_events() from public, anon, authenticated;

-- ==================================================================================== bookkeeping
-- Recorded under the repository's own versions, so the history matches supabase/migrations/ (audit F-14).
insert into supabase_migrations.schema_migrations (version, name, statements, created_by) values
  ('20261111090000', 'cloud_write_gate_and_quotas', '{}', 'owner-sql-editor-2026-11-10'),
  ('20261111100000', 'play_purchase_integrity', '{}', 'owner-sql-editor-2026-11-10'),
  ('20261111110000', 'seed_ids_and_default_privileges', '{}', 'owner-sql-editor-2026-11-10'),
  ('20261111120000', 'request_quotas', '{}', 'owner-sql-editor-2026-11-10')
on conflict (version) do nothing;

-- Owner decision 2026-11-10: allow Play license-tester purchases during internal testing. Switch off before the public
-- release (OWNER_ACTIONS §8.4).
update lucento_private.plan_limits set value_int = 1 where key = 'allow_test_purchases';

commit;

-- Quick confirmation (should list the four versions):
select version, name from supabase_migrations.schema_migrations where version like '20261111%' order by version;
