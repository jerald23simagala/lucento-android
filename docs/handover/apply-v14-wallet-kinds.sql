-- Lucento tester round (app database v14): one paste for the Supabase SQL editor (owner to-do OT-38).
-- Apply to lucento-test first, then to production, before a build with investments or scheduled transfers syncs there.
-- How: Supabase dashboard -> the project -> SQL Editor -> New query -> paste this whole file -> Run.
-- It is the migration supabase/migrations/20261119090000_wallet_kinds_and_spending_money.sql, wrapped so that it changes everything or nothing,
-- records itself in the migration history when the project keeps one, and then checks itself.
-- Strictly additive and safe to run twice. Built from the migration file; if that file changes, rebuild this one.

begin;

-- Tester round (2026-11-17): investment wallets, wallet kinds and icons, Safe to spend's spending-money choice, and
-- scheduled transfers. App database v14 (docs/CALCULATIONS.md "Safe to spend", "Wallet kinds", "Scheduled transfers").
--
-- Strictly additive. Nothing is dropped, rewritten or revoked, and every existing row keeps every value: the new columns
-- are null, which reads as "no kind, the type's icon, the type's spending default".
--
--   wallets.type            — also 'INVESTMENT': stocks, funds, MP2, time deposits… (an asset money moves into and out of).
--   wallets.kind            — what kind of investment, owned thing or loan it is ('stocks', 'mp2', 'bnpl'…); a label only.
--   wallets.icon_key        — the icon chosen for a wallet without a bank logo; display only.
--   wallets.spendable       — counts towards Safe to spend's money on hand; null = the type's default.
--   wallets.spendable_credit_minor — a credit card's part of its credit counted as spending money.
--   recurring.type          — also 'TRANSFER_OUT': a scheduled transfer from wallet_id to to_wallet_id (no category).
--
-- Rollout (docs/SYNC.md): a v14 phone sends the four wallet keys only in a batch where a wallet uses one, so a phone that
-- never sets them keeps syncing with a project without this migration. An investment wallet or a scheduled transfer is
-- refused by the type checks below until this is applied: apply it before a v14 build reaches people.
--
-- Same shape as the earlier financial checks (20261101180000, 20261105090000, 20261106090000): soft-deleted rows are never
-- checked, and NOT VALID leaves existing rows alone.

-- ------------------------------------------------------------------------------------------------ wallets
alter table public.wallets
  add column if not exists kind text,
  add column if not exists icon_key text,
  add column if not exists spendable boolean,
  add column if not exists spendable_credit_minor bigint;

alter table public.wallets drop constraint if exists wallets_type_known;
alter table public.wallets add constraint wallets_type_known check (
  deleted_at is not null
  or (type in ('CASH','BANK','E_WALLET','SAVINGS','INVESTMENT','CREDIT_CARD','LOAN','RECEIVABLE','OTHER_ASSET'))
) not valid;

-- Keys as the app writes them: lowercase letters, digits and underscores, at most 32 characters.
alter table public.wallets drop constraint if exists wallets_kind_known;
alter table public.wallets add constraint wallets_kind_known check (
  deleted_at is not null or kind is null or kind ~ '^[a-z0-9_]{1,32}$'
) not valid;

alter table public.wallets drop constraint if exists wallets_icon_key_known;
alter table public.wallets add constraint wallets_icon_key_known check (
  deleted_at is not null or icon_key is null or icon_key ~ '^[a-z0-9_]{1,32}$'
) not valid;

-- Zero is "none"; the same upper bound as every other amount.
alter table public.wallets drop constraint if exists wallets_spendable_credit_range;
alter table public.wallets add constraint wallets_spendable_credit_range check (
  deleted_at is not null or spendable_credit_minor is null or spendable_credit_minor between 0 and 1000000000000000
) not valid;

-- ------------------------------------------------------------------------------------------------ recurring
alter table public.recurring drop constraint if exists recurring_type_known;
alter table public.recurring add constraint recurring_type_known check (
  deleted_at is not null or (type in ('EXPENSE','INCOME','TRANSFER_OUT'))
) not valid;

-- A scheduled transfer always names where the money goes, and is never a category's spending or income.
alter table public.recurring drop constraint if exists recurring_transfer_shape;
alter table public.recurring add constraint recurring_transfer_shape check (
  deleted_at is not null or type <> 'TRANSFER_OUT' or (to_wallet_id is not null and to_wallet_id <> wallet_id and category_id is null)
) not valid;

-- ==================================================================================================== bookkeeping
-- Recorded under the repository's own version, so the history matches supabase/migrations/. Skipped quietly on a
-- project without a migration history table.
do $$
begin
  if to_regclass('supabase_migrations.schema_migrations') is null then
    raise notice 'No migration history table on this project; nothing recorded.';
  elsif exists (
    select 1 from information_schema.columns
    where table_schema = 'supabase_migrations' and table_name = 'schema_migrations' and column_name = 'created_by'
  ) then
    insert into supabase_migrations.schema_migrations (version, name, statements, created_by)
    values ('20261119090000', 'wallet_kinds_and_spending_money', '{}', 'owner-sql-editor')
    on conflict (version) do nothing;
  else
    insert into supabase_migrations.schema_migrations (version, name, statements)
    values ('20261119090000', 'wallet_kinds_and_spending_money', '{}')
    on conflict (version) do nothing;
  end if;
end $$;

commit;

-- ==================================================================================================== check
-- One row; every column should say true.
select
  (select count(*) from information_schema.columns
     where table_schema = 'public' and table_name = 'wallets'
       and column_name in ('kind', 'icon_key', 'spendable', 'spendable_credit_minor')) = 4 as wallet_columns,
  coalesce((select pg_get_constraintdef(oid) like '%INVESTMENT%' from pg_constraint
     where conrelid = 'public.wallets'::regclass and conname = 'wallets_type_known'), false) as investment_type,
  (select count(*) from pg_constraint where conrelid = 'public.wallets'::regclass
     and conname in ('wallets_kind_known', 'wallets_icon_key_known', 'wallets_spendable_credit_range')) = 3 as wallet_checks,
  coalesce((select pg_get_constraintdef(oid) like '%TRANSFER_OUT%' from pg_constraint
     where conrelid = 'public.recurring'::regclass and conname = 'recurring_type_known'), false) as transfer_type,
  exists (select 1 from pg_constraint
     where conrelid = 'public.recurring'::regclass and conname = 'recurring_transfer_shape') as transfer_shape;
