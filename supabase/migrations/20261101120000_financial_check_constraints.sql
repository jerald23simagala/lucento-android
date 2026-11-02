-- P2-9 (N-06): server-side validation of financial rows. Mirrors the app's own enums and money rules
-- (core/money, domain/Transaction.kt, Wallet.kt, Category.kt, Recurring.kt, Debt.kt). The same rules run on pulled
-- rows in the app (data/sync/PulledRowRules.kt), so an invalid row is neither written here nor applied there.
--
-- NOT VALID: the constraints check every new or updated row from now on, but existing rows are not re-checked when
-- this runs, so rolling it out can never fail on old data. Validate each one separately once the data is confirmed
-- clean (ALTER TABLE ... VALIDATE CONSTRAINT ...). Applied to lucento-test on 2026-11-01 (all existing test rows pass);
-- production waits for the owner's approval.
--
-- Amount bound: MoneyParser accepts at most 1e12 major units; in a 3-decimal currency that is 1e15 minor units.

-- wallets
alter table public.wallets
  add constraint wallets_currency_iso check (currency_code ~ '^[A-Z]{3}$') not valid,
  add constraint wallets_type_known check (type in ('CASH','BANK','E_WALLET','SAVINGS','CREDIT_CARD','LOAN','OTHER_ASSET')) not valid,
  add constraint wallets_opening_bounded check (abs(opening_balance_minor) <= 1000000000000000) not valid,
  add constraint wallets_repayment_known check (repayment is null or repayment in ('ONE_TIME','MONTHLY')) not valid,
  add constraint wallets_rate_range check (interest_rate_bps is null or interest_rate_bps between 0 and 10000) not valid,
  add constraint wallets_term_positive check (term_months is null or term_months >= 1) not valid,
  add constraint wallets_installment_positive check (installment_minor is null or installment_minor between 1 and 1000000000000000) not valid;

-- categories
alter table public.categories
  add constraint categories_kind_known check (kind in ('EXPENSE','INCOME')) not valid;

-- transactions
alter table public.transactions
  add constraint transactions_currency_iso check (currency_code ~ '^[A-Z]{3}$') not valid,
  add constraint transactions_type_known check (type in ('EXPENSE','INCOME','TRANSFER_OUT','TRANSFER_IN','ADJUSTMENT_IN','ADJUSTMENT_OUT')) not valid,
  add constraint transactions_amount_range check (amount_minor between 0 and 1000000000000000) not valid;

-- budgets
alter table public.budgets
  add constraint budgets_currency_iso check (currency_code ~ '^[A-Z]{3}$') not valid,
  add constraint budgets_limit_range check (limit_minor between 0 and 1000000000000000) not valid;

-- goals
alter table public.goals
  add constraint goals_currency_iso check (currency_code ~ '^[A-Z]{3}$') not valid,
  add constraint goals_target_range check (target_minor between 0 and 1000000000000000) not valid;

-- recurring
alter table public.recurring
  add constraint recurring_currency_iso check (currency_code ~ '^[A-Z]{3}$') not valid,
  add constraint recurring_type_known check (type in ('EXPENSE','INCOME')) not valid,
  add constraint recurring_frequency_known check (frequency in ('WEEKLY','MONTHLY','YEARLY')) not valid,
  add constraint recurring_interval_positive check (interval_count >= 1) not valid,
  add constraint recurring_amount_range check (amount_minor between 0 and 1000000000000000) not valid,
  add constraint recurring_interest_range check (interest_minor between 0 and 1000000000000000) not valid,
  add constraint recurring_next_index_nonnegative check (next_index >= 0) not valid;
