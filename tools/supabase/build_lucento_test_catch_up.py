"""
Builds docs/mission-control/LUCENTO_TEST_CATCH_UP.sql: the migrations lucento-test lacked before Mission Control, in
order, in one transaction, with a guard that refuses production and a checklist at the end.

    python tools/supabase/build_lucento_test_catch_up.py

Checked by tools/sql-verify/catch_up_check.mjs. Never edit the output by hand: change the migrations and regenerate.
"""
import pathlib

root = pathlib.Path(__file__).resolve().parents[2]
mig = root / 'supabase' / 'migrations'
out = root / 'docs' / 'mission-control' / 'LUCENTO_TEST_CATCH_UP.sql'

files = [
    '20261108090000_play_purchases.sql',
    '20261110090000_account_emails.sql',
    '20261111090000_cloud_write_gate_and_quotas.sql',
    '20261111100000_play_purchase_integrity.sql',
    '20261111110000_seed_ids_and_default_privileges.sql',
    '20261111120000_request_quotas.sql',
    '20261113120000_purchase_checks.sql',
]

header = """-- lucento-test catch-up, before Mission Control (2026-11-15). Paste into lucento-test ONLY, then run
-- APPLY_MISSION_CONTROL.sql (lucento-mission-control/DEPLOYMENT.md, part A).
--
-- Why: APPLY_MISSION_CONTROL.sql stopped on lucento-test with "column e.test_purchase does not exist". lucento-test
-- never got the migrations production received on 2026-11-10 and 2026-11-13 (docs/security/OWNER_ACTIONS_REQUIRED.md
-- §13 says "lucento-test still needs the same file pasted once"), and maybe not the two before them. This file brings
-- it level with production:
--   20261108090000 play purchases · 20261110090000 account emails · 20261111090000 Cloud write gate and quotas ·
--   20261111100000 purchase integrity · 20261111110000 seed ids and default privileges · 20261111120000 request quotas ·
--   20261113120000 purchase checks.
-- Every one of them can run again harmlessly (create-if-missing, create-or-replace, drop-then-create), so it doesn't
-- matter which of them lucento-test already has. All in ONE transaction: if anything fails, nothing changes.
-- Generated from supabase/migrations/ (do not edit here, edit those).
--
-- What changes for people on lucento-test: an account without the Cloud plan can no longer upload (it still downloads;
-- the app pauses uploads and keeps every change on the phone). The last result lists any account this affects: give it
-- complimentary Pro from Mission Control once it's set up, or tell Claude. Test purchases stay allowed here (§8.3).

begin;

-- Refuse to run on production, which already has all of this: its history records the 2026-11-10 production apply.
do $guard$
declare
  v_prod boolean := false;
begin
  if pg_catalog.to_regclass('supabase_migrations.schema_migrations') is not null
     and exists (select 1 from information_schema.columns
                 where table_schema = 'supabase_migrations' and table_name = 'schema_migrations' and column_name = 'created_by') then
    execute 'select exists (select 1 from supabase_migrations.schema_migrations where version = $1 and created_by = $2)'
      into v_prod using '20261111090000', 'owner-sql-editor-2026-11-10';
  end if;
  if v_prod then
    raise exception 'This looks like PRODUCTION (it has the 2026-11-10 production apply). This file is for lucento-test only. Nothing was changed.';
  end if;
end
$guard$;
"""

parts = [header]
for f in files:
    body = (mig / f).read_text(encoding='utf-8').rstrip('\n') + '\n'
    parts.append('\n-- ' + '=' * 101 + '\n-- ' + f + '\n-- ' + '=' * 101 + '\n\n' + body)

footer = """
-- ====================================================================================================
-- lucento-test settings
-- ====================================================================================================

-- License-tester purchases stay allowed on the test project (OWNER_ACTIONS_REQUIRED.md §8.3).
update lucento_private.plan_limits set value_int = 1 where key = 'allow_test_purchases';

commit;

-- What's in place now: every row should say yes, except the last two, which are information for the next steps.
select item, result from (values
  (1, 'play purchases (20261108)',
      case when exists (select 1 from pg_catalog.pg_proc p join pg_catalog.pg_namespace n on n.oid = p.pronamespace
                        where n.nspname = 'public' and p.proname = 'revoke_play_purchase') then 'yes' else 'NO' end),
  (2, 'account emails (20261110)', case when pg_catalog.to_regclass('public.account_emails') is not null then 'yes' else 'NO' end),
  (3, 'Cloud write gate and quotas (20261111 09)',
      case when pg_catalog.to_regclass('lucento_private.ledger_row_counts') is not null then 'yes' else 'NO' end),
  (4, 'purchase integrity (20261111 10)',
      case when exists (select 1 from information_schema.columns
                        where table_schema = 'public' and table_name = 'entitlements' and column_name = 'test_purchase') then 'yes' else 'NO' end),
  (5, 'seed ids and default privileges (20261111 11)',
      case when exists (select 1 from pg_catalog.pg_constraint where conname = 'categories_seed_id_owner') then 'yes' else 'NO' end),
  (6, 'request quotas (20261111 12)', case when pg_catalog.to_regclass('lucento_private.request_quota') is not null then 'yes' else 'NO' end),
  (7, 'purchase checks (20261113)', case when pg_catalog.to_regclass('lucento_private.purchase_checks') is not null then 'yes' else 'NO' end),
  (8, 'test purchases allowed here',
      case when (select value_int from lucento_private.plan_limits where key = 'allow_test_purchases') = 1 then 'yes' else 'NO' end),
  (9, 'pg_cron switched on (needed before Mission Control)',
      case when exists (select 1 from pg_catalog.pg_extension where extname = 'pg_cron') then 'yes'
           else 'not yet: Database > Extensions > pg_cron' end),
  (10, 'accounts that sync but have no Cloud plan (their uploads now pause)',
      (select case when count(*) = 0 then 'none'
                   else count(*)::text || ': ' || string_agg(coalesce(u.email, s.user_id::text), ', ' order by u.email) end
       from (select user_id from public.wallets union select user_id from public.transactions
             union select user_id from public.categories) s
       left join auth.users u on u.id = s.user_id
       where not lucento_private.has_capability(s.user_id, 'CLOUD')))
) as t(n, item, result)
order by n;
"""
parts.append(footer)
out.write_text(''.join(parts), encoding='utf-8', newline='\n')
print(out, sum(1 for _ in out.open(encoding='utf-8')), 'lines')
