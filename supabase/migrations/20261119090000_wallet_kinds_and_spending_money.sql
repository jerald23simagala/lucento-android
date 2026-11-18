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
