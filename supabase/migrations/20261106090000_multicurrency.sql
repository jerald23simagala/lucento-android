-- Multicurrency (docs/CALCULATIONS.md "Exchange rates", docs/MULTICURRENCY.md): wallets in other currencies valued in
-- one main currency by the average-rate method. App database v13.
--
-- Strictly additive. Nothing is dropped, rewritten or revoked from the existing tables, and every existing row keeps
-- every value: the new columns are null (or false) and existing rows follow their wallet's average.
--
--   transactions.base_amount_minor / base_currency / fx_source  — a movement's value in the main currency, the currency
--       it is in (stored with it, so it can never be read in another), and where it came from.
--   wallets.fx_basis_rate  — the rate a foreign wallet's opening balance is valued at, as an exact decimal string.
--   wallets.fx_revalue     — revalue the wallet at each month end.
--   ledger_settings        — the ledger's main currency, set once by its first phone and read by every other phone of the
--       ledger (a reinstall, a second phone, a Partner in a Shared Space). It can't be changed: no update or delete
--       grant or policy exists, and the account's deletion removes it.
--
-- Rollout (docs/SYNC.md): a one-currency ledger's v13 phone sends exactly what v12 sent, so it keeps syncing with a
-- project without this migration. A ledger with a wallet in another currency needs it before such a phone syncs.

-- ------------------------------------------------------------------------------------------------ transactions
alter table public.transactions
  add column if not exists base_amount_minor bigint,
  add column if not exists base_currency text,
  add column if not exists fx_source text;

-- All three or none; the value is in another currency than the row's own; a source this version knows. Live rows only
-- (a soft delete always succeeds) and NOT VALID, like the other financial checks (20261102005820). The "is not null"
-- tests are explicit: a CHECK passes on NULL, so without them a value missing its currency would be accepted.
alter table public.transactions drop constraint if exists transactions_fx_known;
alter table public.transactions add constraint transactions_fx_known check (
  deleted_at is not null
  or (base_amount_minor is null and base_currency is null and fx_source is null)
  or (
    base_amount_minor is not null and base_currency is not null and fx_source is not null
    and base_amount_minor >= 0
    and base_currency ~ '^[A-Z]{3}$'
    and base_currency <> currency_code
    and fx_source in ('MARKET', 'ENTERED', 'LAST_RATE', 'AVERAGE', 'TRANSFER')
  )
) not valid;

-- ------------------------------------------------------------------------------------------------ wallets
alter table public.wallets
  add column if not exists fx_basis_rate text,
  add column if not exists fx_revalue boolean not null default false;

-- A positive decimal written without an exponent ("56.1234", "0.00350877192982"), as the app stores it.
alter table public.wallets drop constraint if exists wallets_fx_basis_known;
alter table public.wallets add constraint wallets_fx_basis_known check (
  deleted_at is not null
  or fx_basis_rate is null
  or (fx_basis_rate ~ '^[0-9]{1,9}(\.[0-9]{1,20})?$' and fx_basis_rate !~ '^0+(\.0+)?$')
) not valid;

-- ------------------------------------------------------------------------------------------------ ledger settings
create table if not exists public.ledger_settings (
  user_id uuid primary key references auth.users (id) on delete cascade,
  main_currency text not null check (main_currency ~ '^[A-Z]{3}$'),
  created_at timestamptz not null default now()
);

alter table public.ledger_settings enable row level security;

-- One policy per action (no multiple permissive policies): the account reads its own row, and a Partner the row of the
-- Shared Space they belong to (lucento_private.partner_spaces(), 20261105120000_lucento_shared.sql).
drop policy if exists "ledger_settings_select" on public.ledger_settings;
create policy "ledger_settings_select" on public.ledger_settings for select to authenticated
  using (user_id = (select auth.uid()) or user_id = any ((select lucento_private.partner_spaces())::uuid[]));
drop policy if exists "ledger_settings_insert_own" on public.ledger_settings;
create policy "ledger_settings_insert_own" on public.ledger_settings for insert to authenticated
  with check (user_id = (select auth.uid()));

-- Least privilege whatever the project's default privileges are (as 20261105120100_lucento_shared_privileges.sql):
-- read and insert for signed-in users, nothing else, nothing for anon. No UPDATE or DELETE: the main currency is fixed.
revoke all on table public.ledger_settings from anon, authenticated;
grant select, insert on table public.ledger_settings to authenticated;
