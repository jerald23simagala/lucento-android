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
