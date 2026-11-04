-- Lucento production rollout: 20261101120000 → 20261101180000 → 20261102120000
-- Runbook: docs/handover/PRODUCTION_MIGRATION.md. Run each STEP separately in the Supabase SQL editor of the
-- production project ("lucento"). STEP 0 and STEP 2 are read-only. STEP 1 is one transaction: it applies all three
-- or none. Already applied and verified on lucento-test.

-------------------------------------------------------------------------------------------------------------------
-- STEP 0 — pre-checks (read-only)
-------------------------------------------------------------------------------------------------------------------

-- 0a. Migration history. Expect 11 rows ending at 20261029030820 (versions drift from repo filenames, N-07) and none
--     of 20261101120000 / 20261101180000 / 20261102120000.
select version, name from supabase_migrations.schema_migrations order by version;

-- 0b. None of the new objects exist yet. Expect 0 rows from both queries. If any appear, stop: STEP 1 would fail on
--     "constraint already exists" (and roll back cleanly) — report what is there before going on.
select conrelid::regclass as table_name, conname
from pg_constraint
where connamespace = 'public'::regnamespace and contype = 'c'
  and conname in (
    'wallets_currency_iso','wallets_type_known','wallets_opening_bounded','wallets_repayment_known','wallets_rate_range',
    'wallets_term_positive','wallets_installment_positive','categories_kind_known','transactions_currency_iso',
    'transactions_type_known','transactions_amount_range','budgets_currency_iso','budgets_limit_range',
    'goals_currency_iso','goals_target_range','recurring_currency_iso','recurring_type_known','recurring_frequency_known',
    'recurring_interval_positive','recurring_amount_range','recurring_interest_range','recurring_next_index_nonnegative',
    'budgets_month_format','budgets_effective_from_format','budgets_single_scope','wallets_institution_id_length')
order by 1, 2;

select table_name, column_name
from information_schema.columns
where table_schema = 'public'
  and ((table_name = 'budgets' and column_name in ('month','effective_from'))
    or (table_name = 'wallets' and column_name = 'institution_id'));

-- 0c. Live rows that already break a rule. Expect every count to be 0.
--     The constraints are NOT VALID, so applying them never fails on old data, but Postgres does check them when an
--     existing row is UPDATEd: a live invalid row would make that row's next sync push fail. "(rule) is false" is
--     exactly what a CHECK rejects (a NULL result passes).
select 'wallets' as table_name, count(*) as live_invalid from public.wallets
 where deleted_at is null and (
       currency_code ~ '^[A-Z]{3}$'
   and type in ('CASH','BANK','E_WALLET','SAVINGS','CREDIT_CARD','LOAN','OTHER_ASSET')
   and abs(opening_balance_minor) <= 1000000000000000
   and (repayment is null or repayment in ('ONE_TIME','MONTHLY'))
   and (interest_rate_bps is null or interest_rate_bps between 0 and 10000)
   and (term_months is null or term_months >= 1)
   and (installment_minor is null or installment_minor between 1 and 1000000000000000)) is false
union all
select 'categories', count(*) from public.categories
 where deleted_at is null and (kind in ('EXPENSE','INCOME')) is false
union all
select 'transactions', count(*) from public.transactions
 where deleted_at is null and (
       currency_code ~ '^[A-Z]{3}$'
   and type in ('EXPENSE','INCOME','TRANSFER_OUT','TRANSFER_IN','ADJUSTMENT_IN','ADJUSTMENT_OUT')
   and amount_minor between 0 and 1000000000000000) is false
union all
select 'budgets', count(*) from public.budgets
 where deleted_at is null and (
       currency_code ~ '^[A-Z]{3}$'
   and limit_minor between 0 and 1000000000000000) is false
union all
select 'goals', count(*) from public.goals
 where deleted_at is null and (
       currency_code ~ '^[A-Z]{3}$'
   and target_minor between 0 and 1000000000000000) is false
union all
select 'recurring', count(*) from public.recurring
 where deleted_at is null and (
       currency_code ~ '^[A-Z]{3}$'
   and type in ('EXPENSE','INCOME')
   and frequency in ('WEEKLY','MONTHLY','YEARLY')
   and interval_count >= 1
   and amount_minor between 0 and 1000000000000000
   and interest_minor between 0 and 1000000000000000
   and next_index >= 0) is false;

-------------------------------------------------------------------------------------------------------------------
-- STEP 1 — apply (one transaction; the three migration files verbatim, in order)
-------------------------------------------------------------------------------------------------------------------
begin;

-- ===== 20261101120000_financial_check_constraints.sql =====
alter table public.wallets
  add constraint wallets_currency_iso check (currency_code ~ '^[A-Z]{3}$') not valid,
  add constraint wallets_type_known check (type in ('CASH','BANK','E_WALLET','SAVINGS','CREDIT_CARD','LOAN','OTHER_ASSET')) not valid,
  add constraint wallets_opening_bounded check (abs(opening_balance_minor) <= 1000000000000000) not valid,
  add constraint wallets_repayment_known check (repayment is null or repayment in ('ONE_TIME','MONTHLY')) not valid,
  add constraint wallets_rate_range check (interest_rate_bps is null or interest_rate_bps between 0 and 10000) not valid,
  add constraint wallets_term_positive check (term_months is null or term_months >= 1) not valid,
  add constraint wallets_installment_positive check (installment_minor is null or installment_minor between 1 and 1000000000000000) not valid;

alter table public.categories
  add constraint categories_kind_known check (kind in ('EXPENSE','INCOME')) not valid;

alter table public.transactions
  add constraint transactions_currency_iso check (currency_code ~ '^[A-Z]{3}$') not valid,
  add constraint transactions_type_known check (type in ('EXPENSE','INCOME','TRANSFER_OUT','TRANSFER_IN','ADJUSTMENT_IN','ADJUSTMENT_OUT')) not valid,
  add constraint transactions_amount_range check (amount_minor between 0 and 1000000000000000) not valid;

alter table public.budgets
  add constraint budgets_currency_iso check (currency_code ~ '^[A-Z]{3}$') not valid,
  add constraint budgets_limit_range check (limit_minor between 0 and 1000000000000000) not valid;

alter table public.goals
  add constraint goals_currency_iso check (currency_code ~ '^[A-Z]{3}$') not valid,
  add constraint goals_target_range check (target_minor between 0 and 1000000000000000) not valid;

alter table public.recurring
  add constraint recurring_currency_iso check (currency_code ~ '^[A-Z]{3}$') not valid,
  add constraint recurring_type_known check (type in ('EXPENSE','INCOME')) not valid,
  add constraint recurring_frequency_known check (frequency in ('WEEKLY','MONTHLY','YEARLY')) not valid,
  add constraint recurring_interval_positive check (interval_count >= 1) not valid,
  add constraint recurring_amount_range check (amount_minor between 0 and 1000000000000000) not valid,
  add constraint recurring_interest_range check (interest_minor between 0 and 1000000000000000) not valid,
  add constraint recurring_next_index_nonnegative check (next_index >= 0) not valid;

-- ===== 20261101180000_check_constraints_live_rows.sql =====
alter table public.wallets drop constraint if exists wallets_currency_iso;
alter table public.wallets add constraint wallets_currency_iso check (deleted_at is not null or (currency_code ~ '^[A-Z]{3}$')) not valid;
alter table public.wallets drop constraint if exists wallets_type_known;
alter table public.wallets add constraint wallets_type_known check (deleted_at is not null or (type in ('CASH','BANK','E_WALLET','SAVINGS','CREDIT_CARD','LOAN','OTHER_ASSET'))) not valid;
alter table public.wallets drop constraint if exists wallets_opening_bounded;
alter table public.wallets add constraint wallets_opening_bounded check (deleted_at is not null or (abs(opening_balance_minor) <= 1000000000000000)) not valid;
alter table public.wallets drop constraint if exists wallets_repayment_known;
alter table public.wallets add constraint wallets_repayment_known check (deleted_at is not null or (repayment is null or repayment in ('ONE_TIME','MONTHLY'))) not valid;
alter table public.wallets drop constraint if exists wallets_rate_range;
alter table public.wallets add constraint wallets_rate_range check (deleted_at is not null or (interest_rate_bps is null or interest_rate_bps between 0 and 10000)) not valid;
alter table public.wallets drop constraint if exists wallets_term_positive;
alter table public.wallets add constraint wallets_term_positive check (deleted_at is not null or (term_months is null or term_months >= 1)) not valid;
alter table public.wallets drop constraint if exists wallets_installment_positive;
alter table public.wallets add constraint wallets_installment_positive check (deleted_at is not null or (installment_minor is null or installment_minor between 1 and 1000000000000000)) not valid;
alter table public.categories drop constraint if exists categories_kind_known;
alter table public.categories add constraint categories_kind_known check (deleted_at is not null or (kind in ('EXPENSE','INCOME'))) not valid;
alter table public.transactions drop constraint if exists transactions_currency_iso;
alter table public.transactions add constraint transactions_currency_iso check (deleted_at is not null or (currency_code ~ '^[A-Z]{3}$')) not valid;
alter table public.transactions drop constraint if exists transactions_type_known;
alter table public.transactions add constraint transactions_type_known check (deleted_at is not null or (type in ('EXPENSE','INCOME','TRANSFER_OUT','TRANSFER_IN','ADJUSTMENT_IN','ADJUSTMENT_OUT'))) not valid;
alter table public.transactions drop constraint if exists transactions_amount_range;
alter table public.transactions add constraint transactions_amount_range check (deleted_at is not null or (amount_minor between 0 and 1000000000000000)) not valid;
alter table public.budgets drop constraint if exists budgets_currency_iso;
alter table public.budgets add constraint budgets_currency_iso check (deleted_at is not null or (currency_code ~ '^[A-Z]{3}$')) not valid;
alter table public.budgets drop constraint if exists budgets_limit_range;
alter table public.budgets add constraint budgets_limit_range check (deleted_at is not null or (limit_minor between 0 and 1000000000000000)) not valid;
alter table public.goals drop constraint if exists goals_currency_iso;
alter table public.goals add constraint goals_currency_iso check (deleted_at is not null or (currency_code ~ '^[A-Z]{3}$')) not valid;
alter table public.goals drop constraint if exists goals_target_range;
alter table public.goals add constraint goals_target_range check (deleted_at is not null or (target_minor between 0 and 1000000000000000)) not valid;
alter table public.recurring drop constraint if exists recurring_currency_iso;
alter table public.recurring add constraint recurring_currency_iso check (deleted_at is not null or (currency_code ~ '^[A-Z]{3}$')) not valid;
alter table public.recurring drop constraint if exists recurring_type_known;
alter table public.recurring add constraint recurring_type_known check (deleted_at is not null or (type in ('EXPENSE','INCOME'))) not valid;
alter table public.recurring drop constraint if exists recurring_frequency_known;
alter table public.recurring add constraint recurring_frequency_known check (deleted_at is not null or (frequency in ('WEEKLY','MONTHLY','YEARLY'))) not valid;
alter table public.recurring drop constraint if exists recurring_interval_positive;
alter table public.recurring add constraint recurring_interval_positive check (deleted_at is not null or (interval_count >= 1)) not valid;
alter table public.recurring drop constraint if exists recurring_amount_range;
alter table public.recurring add constraint recurring_amount_range check (deleted_at is not null or (amount_minor between 0 and 1000000000000000)) not valid;
alter table public.recurring drop constraint if exists recurring_interest_range;
alter table public.recurring add constraint recurring_interest_range check (deleted_at is not null or (interest_minor between 0 and 1000000000000000)) not valid;
alter table public.recurring drop constraint if exists recurring_next_index_nonnegative;
alter table public.recurring add constraint recurring_next_index_nonnegative check (deleted_at is not null or (next_index >= 0)) not valid;

-- ===== 20261102120000_budget_lifecycle_and_institutions.sql =====
alter table public.budgets
  add column if not exists month text,
  add column if not exists effective_from text;

alter table public.budgets
  add constraint budgets_month_format check (month is null or month ~ '^\d{4}-(0[1-9]|1[0-2])$'),
  add constraint budgets_effective_from_format check (effective_from is null or effective_from ~ '^\d{4}-(0[1-9]|1[0-2])$'),
  add constraint budgets_single_scope check (month is null or effective_from is null);

alter table public.wallets
  add column if not exists institution_id text;

alter table public.wallets
  add constraint wallets_institution_id_length check (institution_id is null or char_length(institution_id) between 1 and 128);

commit;

-- Make PostgREST see the new columns immediately (Supabase also does this on DDL; harmless to repeat).
notify pgrst, 'reload schema';

-------------------------------------------------------------------------------------------------------------------
-- STEP 2 — verify (read-only)
-------------------------------------------------------------------------------------------------------------------

-- 2a. Expect 26 rows: 22 with validated = false whose definition starts "CHECK (deleted_at IS NOT NULL OR ...",
--     and the 4 lifecycle/institution rows with validated = true.
select conrelid::regclass as table_name, conname, convalidated as validated, pg_get_constraintdef(oid) as definition
from pg_constraint
where connamespace = 'public'::regnamespace and contype = 'c'
  and conname in (
    'wallets_currency_iso','wallets_type_known','wallets_opening_bounded','wallets_repayment_known','wallets_rate_range',
    'wallets_term_positive','wallets_installment_positive','categories_kind_known','transactions_currency_iso',
    'transactions_type_known','transactions_amount_range','budgets_currency_iso','budgets_limit_range',
    'goals_currency_iso','goals_target_range','recurring_currency_iso','recurring_type_known','recurring_frequency_known',
    'recurring_interval_positive','recurring_amount_range','recurring_interest_range','recurring_next_index_nonnegative',
    'budgets_month_format','budgets_effective_from_format','budgets_single_scope','wallets_institution_id_length')
order by 1, 2;

-- 2b. Expect 3 rows, all text, nullable.
select table_name, column_name, data_type, is_nullable
from information_schema.columns
where table_schema = 'public'
  and ((table_name = 'budgets' and column_name in ('month','effective_from'))
    or (table_name = 'wallets' and column_name = 'institution_id'))
order by 1, 2;

-- 2c. The app role can read and write the new columns (table-level grants). Expect true in all three.
select has_column_privilege('authenticated', 'public.budgets', 'month', 'SELECT, INSERT, UPDATE') as budgets_month,
       has_column_privilege('authenticated', 'public.budgets', 'effective_from', 'SELECT, INSERT, UPDATE') as budgets_effective_from,
       has_column_privilege('authenticated', 'public.wallets', 'institution_id', 'SELECT, INSERT, UPDATE') as wallets_institution_id;

-- 2d. RLS still on for the two altered tables. Expect true, true.
select relname, relrowsecurity from pg_class
where oid in ('public.budgets'::regclass, 'public.wallets'::regclass) order by relname;

-------------------------------------------------------------------------------------------------------------------
-- STEP 3 (optional, later) — validate the 22 NOT VALID constraints once 0c is all zeros. Each scans its table under
-- a SHARE UPDATE EXCLUSIVE lock (reads and writes continue). Not needed for the app to work.
-------------------------------------------------------------------------------------------------------------------
-- alter table public.wallets validate constraint wallets_currency_iso;
-- ... (one line per constraint name in 2a with validated = false)

-------------------------------------------------------------------------------------------------------------------
-- ROLLBACK — only if a problem appears BEFORE any v10 build has synced against production.
-- v10 clients always send budgets.month/effective_from; dropping those columns afterwards breaks their sync and
-- discards month-specific budgets and institution choices.
-------------------------------------------------------------------------------------------------------------------
-- begin;
-- alter table public.wallets drop constraint if exists wallets_institution_id_length;
-- alter table public.wallets drop column if exists institution_id;
-- alter table public.budgets
--   drop constraint if exists budgets_single_scope,
--   drop constraint if exists budgets_effective_from_format,
--   drop constraint if exists budgets_month_format;
-- alter table public.budgets drop column if exists month, drop column if exists effective_from;
-- alter table public.wallets
--   drop constraint if exists wallets_currency_iso, drop constraint if exists wallets_type_known,
--   drop constraint if exists wallets_opening_bounded, drop constraint if exists wallets_repayment_known,
--   drop constraint if exists wallets_rate_range, drop constraint if exists wallets_term_positive,
--   drop constraint if exists wallets_installment_positive;
-- alter table public.categories drop constraint if exists categories_kind_known;
-- alter table public.transactions
--   drop constraint if exists transactions_currency_iso, drop constraint if exists transactions_type_known,
--   drop constraint if exists transactions_amount_range;
-- alter table public.budgets drop constraint if exists budgets_currency_iso, drop constraint if exists budgets_limit_range;
-- alter table public.goals drop constraint if exists goals_currency_iso, drop constraint if exists goals_target_range;
-- alter table public.recurring
--   drop constraint if exists recurring_currency_iso, drop constraint if exists recurring_type_known,
--   drop constraint if exists recurring_frequency_known, drop constraint if exists recurring_interval_positive,
--   drop constraint if exists recurring_amount_range, drop constraint if exists recurring_interest_range,
--   drop constraint if exists recurring_next_index_nonnegative;
-- commit;
-- notify pgrst, 'reload schema';
