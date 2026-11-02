-- QA note 3 on P2-9: the CHECK constraints apply to live rows only. A soft delete (deleted_at set) of an old row that
-- predates the rules must always succeed, matching the app, which never blocks a pulled delete (PulledRowRules).
-- The applied migration 20261101120000 is not edited; each constraint is re-created here, still NOT VALID.

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
