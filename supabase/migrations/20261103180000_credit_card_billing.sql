-- Credit card billing (app schema v11): a card's credit limit and the days of the month its statement closes and its
-- payment is due. They travel on the card's wallet row like its other terms (docs/SYNC.md).
--
-- wallets.credit_limit_minor  the limit in the wallet's currency, minor units (> 0), or null.
-- wallets.statement_day       1-31 (a shorter month uses its last day), or null.
-- wallets.due_day             1-31, or null.
--
-- v11 clients always send these columns on wallet rows, so this migration must reach a project before a v11 build syncs
-- against it. Older clients never send them, and an upsert leaves absent columns unchanged, so an old client editing a
-- card cannot clear them. No grant or policy changes: table-level grants and the existing RLS policies cover new
-- columns. Like every financial CHECK (20261101180000), the rules apply to live rows only.

alter table public.wallets
  add column if not exists credit_limit_minor bigint,
  add column if not exists statement_day smallint,
  add column if not exists due_day smallint;

alter table public.wallets
  add constraint wallets_credit_limit_range check (deleted_at is not null or (credit_limit_minor is null or credit_limit_minor between 1 and 1000000000000000)) not valid,
  add constraint wallets_statement_day_range check (deleted_at is not null or (statement_day is null or statement_day between 1 and 31)) not valid,
  add constraint wallets_due_day_range check (deleted_at is not null or (due_day is null or due_day between 1 and 31)) not valid;
