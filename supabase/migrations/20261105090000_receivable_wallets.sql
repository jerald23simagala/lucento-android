-- Owner review round 10 (2026-11-04): a Receivable wallet — money someone owes the user, a loan in reverse.
--
-- public.wallets accepts the new type 'RECEIVABLE'. A receivable carries a loan's optional terms in the existing
-- columns (interest_rate_bps, repayment, term_months, installment_minor); nothing new is stored. Its collections are
-- ordinary public.recurring rows of type INCOME whose to_wallet_id is the receivable (a debt payment is EXPENSE with
-- to_wallet_id the card or loan), so that table needs no change either.
--
-- Same shape as 20261101180000_check_constraints_live_rows.sql: soft-deleted rows are never checked, and NOT VALID
-- leaves existing rows alone. Apply this before an app that can create receivables syncs one, or the push is refused.

alter table public.wallets drop constraint if exists wallets_type_known;
alter table public.wallets add constraint wallets_type_known check (
  deleted_at is not null or (type in ('CASH','BANK','E_WALLET','SAVINGS','CREDIT_CARD','LOAN','RECEIVABLE','OTHER_ASSET'))
) not valid;
