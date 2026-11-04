-- Budget lifecycle (app schema v10) and wallet institution identity.
--
-- budgets.month           "yyyy-MM": an amount for exactly that month (a "this month only" change); null otherwise.
-- budgets.effective_from  "yyyy-MM": first month of a recurring amount; null = every month (all rows written before v10).
-- A row is one or the other, never both. limit_minor 0 is meaningful only on these rows ("no amount that month" /
-- "stopped from that month"); the existing 0..1e15 range check already allows it.
--
-- wallets.institution_id  id from the bundled institution registry (display only: logo/name), or null.
--
-- v10 clients always send month and effective_from on budget rows, so this migration must reach a project before a
-- v10 build syncs against it. Older clients never send these columns, and an upsert leaves absent columns unchanged,
-- so an old client editing a row cannot clear them. No grant or policy changes: table-level grants and the existing
-- RLS policies cover new columns.

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
