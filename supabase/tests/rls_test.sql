-- P2-11: Row Level Security for every user-owned table, as pgTAP tests.
--
-- Run against a disposable local database with the Supabase CLI: `supabase start` then `supabase test db`.
-- Never run against production. Everything happens inside one transaction that is rolled back: the two users and
-- their rows exist only for the duration of the test.
--
-- What it proves, for wallets, categories, transactions, budgets, goals, goal_contributions and recurring:
--   * RLS is enabled, and anon has no table privileges;
--   * a signed-in user sees, updates and deletes only their own rows;
--   * a user can't insert a row for someone else (RLS, 42501), or move their own row to someone else (sync trigger, P0001);
--   * ai_usage is readable only by its owner and not writable by clients.

begin;
create extension if not exists pgtap with schema extensions;
select plan(54);

-- Two users. Rows are created as the table owner (RLS does not apply), then the tests switch roles.
insert into auth.users (id, instance_id, aud, role, email)
values ('aaaaaaaa-0000-4000-8000-00000000000a', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 'rls-a@example.test'),
       ('bbbbbbbb-0000-4000-8000-00000000000b', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 'rls-b@example.test');

do $$
declare
  u uuid;
  tag text;
begin
  foreach u in array array['aaaaaaaa-0000-4000-8000-00000000000a'::uuid, 'bbbbbbbb-0000-4000-8000-00000000000b'::uuid] loop
    tag := left(u::text, 1);
    insert into public.wallets (id, user_id, name, type, currency_code, opening_balance_minor, created_at, updated_at)
      values ('w-' || tag, u, 'Cash', 'CASH', 'USD', 0, now(), now());
    insert into public.categories (id, user_id, name, kind, icon_key, created_at, updated_at)
      values ('c-' || tag, u, 'Food', 'EXPENSE', 'food', now(), now());
    insert into public.transactions (id, user_id, type, amount_minor, currency_code, wallet_id, category_id, occurred_on, created_at, updated_at)
      values ('t-' || tag, u, 'EXPENSE', 1250, 'USD', 'w-' || tag, 'c-' || tag, 20000, now(), now());
    insert into public.budgets (id, user_id, category_id, currency_code, limit_minor, created_at, updated_at)
      values ('b-' || tag, u, 'c-' || tag, 'USD', 30000, now(), now());
    insert into public.goals (id, user_id, name, target_minor, currency_code, created_at, updated_at)
      values ('g-' || tag, u, 'Trip', 100000, 'USD', now(), now());
    insert into public.goal_contributions (id, user_id, goal_id, amount_minor, occurred_on, created_at, updated_at)
      values ('gc-' || tag, u, 'g-' || tag, 5000, 20000, now(), now());
    insert into public.recurring (id, user_id, name, type, amount_minor, currency_code, wallet_id, frequency, interval_count, start_on, created_at, updated_at)
      values ('r-' || tag, u, 'Rent', 'EXPENSE', 90000, 'USD', 'w-' || tag, 'MONTHLY', 1, 20000, now(), now());
    insert into public.ai_usage (user_id, day, requests) values (u, current_date, 1);
  end loop;
end $$;

-- ---------------------------------------------------------------- structure (8 tables x 2 = 16)
select ok((select relrowsecurity from pg_class where oid = ('public.' || t)::regclass), t || ': RLS enabled')
from unnest(array['wallets','categories','transactions','budgets','goals','goal_contributions','recurring','ai_usage']) t;
select ok(not has_table_privilege('anon', 'public.' || t, 'SELECT,INSERT,UPDATE,DELETE'), t || ': anon has no privileges')
from unnest(array['wallets','categories','transactions','budgets','goals','goal_contributions','recurring','ai_usage']) t;

-- ---------------------------------------------------------------- as user A
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"aaaaaaaa-0000-4000-8000-00000000000a","role":"authenticated"}', true);

-- reads: only A's own row (7 tables x 2 = 14)
select is((select count(*)::int from public.wallets), 1, 'wallets: A sees one row');
select is((select count(*)::int from public.wallets where user_id = 'bbbbbbbb-0000-4000-8000-00000000000b'), 0, 'wallets: A sees none of B');
select is((select count(*)::int from public.categories), 1, 'categories: A sees one row');
select is((select count(*)::int from public.categories where user_id = 'bbbbbbbb-0000-4000-8000-00000000000b'), 0, 'categories: A sees none of B');
select is((select count(*)::int from public.transactions), 1, 'transactions: A sees one row');
select is((select count(*)::int from public.transactions where user_id = 'bbbbbbbb-0000-4000-8000-00000000000b'), 0, 'transactions: A sees none of B');
select is((select count(*)::int from public.budgets), 1, 'budgets: A sees one row');
select is((select count(*)::int from public.budgets where user_id = 'bbbbbbbb-0000-4000-8000-00000000000b'), 0, 'budgets: A sees none of B');
select is((select count(*)::int from public.goals), 1, 'goals: A sees one row');
select is((select count(*)::int from public.goals where user_id = 'bbbbbbbb-0000-4000-8000-00000000000b'), 0, 'goals: A sees none of B');
select is((select count(*)::int from public.goal_contributions), 1, 'goal_contributions: A sees one row');
select is((select count(*)::int from public.goal_contributions where user_id = 'bbbbbbbb-0000-4000-8000-00000000000b'), 0, 'goal_contributions: A sees none of B');
select is((select count(*)::int from public.recurring), 1, 'recurring: A sees one row');
select is((select count(*)::int from public.recurring where user_id = 'bbbbbbbb-0000-4000-8000-00000000000b'), 0, 'recurring: A sees none of B');

-- updates and deletes of B's rows touch nothing (7 + 7 = 14)
select is_empty($$ update public.wallets set name = 'x' where id = 'w-b' returning id $$, 'wallets: A cannot update B');
select is_empty($$ update public.categories set name = 'x' where id = 'c-b' returning id $$, 'categories: A cannot update B');
select is_empty($$ update public.transactions set amount_minor = 1 where id = 't-b' returning id $$, 'transactions: A cannot update B');
select is_empty($$ update public.budgets set limit_minor = 1 where id = 'b-b' returning id $$, 'budgets: A cannot update B');
select is_empty($$ update public.goals set name = 'x' where id = 'g-b' returning id $$, 'goals: A cannot update B');
select is_empty($$ update public.goal_contributions set amount_minor = 1 where id = 'gc-b' returning id $$, 'goal_contributions: A cannot update B');
select is_empty($$ update public.recurring set name = 'x' where id = 'r-b' returning id $$, 'recurring: A cannot update B');
select is_empty($$ delete from public.wallets where id = 'w-b' returning id $$, 'wallets: A cannot delete B');
select is_empty($$ delete from public.categories where id = 'c-b' returning id $$, 'categories: A cannot delete B');
select is_empty($$ delete from public.transactions where id = 't-b' returning id $$, 'transactions: A cannot delete B');
select is_empty($$ delete from public.budgets where id = 'b-b' returning id $$, 'budgets: A cannot delete B');
select is_empty($$ delete from public.goals where id = 'g-b' returning id $$, 'goals: A cannot delete B');
select is_empty($$ delete from public.goal_contributions where id = 'gc-b' returning id $$, 'goal_contributions: A cannot delete B');
select is_empty($$ delete from public.recurring where id = 'r-b' returning id $$, 'recurring: A cannot delete B');

-- inserting for B, or moving A's own row to B, is refused (42501 = RLS violation) (4)
select throws_ok(
    $$ insert into public.transactions (id, user_id, type, amount_minor, currency_code, wallet_id, occurred_on, created_at, updated_at)
       values ('t-forged', 'bbbbbbbb-0000-4000-8000-00000000000b', 'EXPENSE', 1, 'USD', 'w-b', 20000, now(), now()) $$,
    '42501', null, 'transactions: A cannot insert a row for B');
select throws_ok(
    $$ insert into public.wallets (id, user_id, name, type, currency_code, opening_balance_minor, created_at, updated_at)
       values ('w-forged', 'bbbbbbbb-0000-4000-8000-00000000000b', 'Forged', 'CASH', 'USD', 0, now(), now()) $$,
    '42501', null, 'wallets: A cannot insert a row for B');
-- Moving a row to another user is stopped by the sync trigger (lucento_reject_stale_update, a BEFORE trigger that runs
-- ahead of RLS WITH CHECK): it raises 'user_id cannot change' (P0001).
select throws_ok(
    $$ update public.transactions set user_id = 'bbbbbbbb-0000-4000-8000-00000000000b', updated_at = now() + interval '1 second' where id = 't-a' $$,
    'P0001', 'user_id cannot change', 'transactions: A cannot move a row to B');
select throws_ok(
    $$ update public.budgets set user_id = 'bbbbbbbb-0000-4000-8000-00000000000b', updated_at = now() + interval '1 second' where id = 'b-a' $$,
    'P0001', 'user_id cannot change', 'budgets: A cannot move a row to B');

-- ai_usage: read own only, no client writes (3)
select is((select count(*)::int from public.ai_usage), 1, 'ai_usage: A sees only their own allowance');
select throws_ok($$ update public.ai_usage set requests = 0 $$, '42501', null, 'ai_usage: A cannot reset the allowance');
select throws_ok($$ insert into public.ai_usage (user_id, day, requests) values ('aaaaaaaa-0000-4000-8000-00000000000a', current_date + 1, 0) $$,
    '42501', null, 'ai_usage: A cannot add allowance rows');

-- ---------------------------------------------------------------- B's rows are untouched (3)
reset role;
select is((select count(*)::int from public.transactions where id = 't-b' and amount_minor = 1250 and deleted_at is null), 1, 'B transaction unchanged');
select is((select count(*)::int from public.wallets where id = 'w-b' and name = 'Cash'), 1, 'B wallet unchanged');
select is((select count(*)::int from public.transactions where id = 't-forged'), 0, 'no forged row exists');

select * from finish();
rollback;
