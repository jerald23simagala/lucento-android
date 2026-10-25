-- Server clock for the pull cursor, and revisions for causal conflict resolution (see docs/SYNC.md "Clocks" and
-- "Conflict resolution"). Additive: three columns, one index per table, one new function, one trigger per table,
-- and a new body for lucento_reject_stale_update (migration 3). No row, policy or grant is removed or rewritten
-- beyond the defaults below; existing rows keep every value they had.
--
-- 1. server_updated_at — set by the database on every insert and update; clients page through it. Previously
--    clients pulled by `updated_at`, a device-written time, so a device whose clock ran behind produced rows other
--    devices' cursors had already passed and never pulled. Existing rows receive the time this migration runs.
-- 2. revision — incremented on every accepted write (existing rows start at 1). base_revision — sent by a client with
--    an update: the revision its edit was based on (nulled by every accepted update; it only carries the request).
--    Clients always send it, null included, so an update can never inherit a stored value.
--    * base_revision = current revision: a causal successor (the writer had seen the latest version). Accepted
--      whatever the device clock says — a device with a slow clock no longer loses edits it made after syncing.
--    * anything else (stale base, or none): a concurrent edit. Accepted only if its edit time `updated_at` is
--      strictly later than the stored row's; ties keep the stored row. Clients resolve conflicts with this same rule.
-- 3. updated_at more than 5 minutes ahead of the server is clamped to the server's time, so a fast clock can't make
--    rows unbeatable in concurrent conflicts.
--
-- BEFORE triggers fire alphabetically: trg_*_lww (accept/skip, revision) runs before trg_*_stamp (server time,
-- clamp); a skipped update never reaches the stamp.

alter table public.wallets
  add column if not exists server_updated_at timestamptz not null default now(),
  add column if not exists revision bigint not null default 1,
  add column if not exists base_revision bigint;
alter table public.categories
  add column if not exists server_updated_at timestamptz not null default now(),
  add column if not exists revision bigint not null default 1,
  add column if not exists base_revision bigint;
alter table public.transactions
  add column if not exists server_updated_at timestamptz not null default now(),
  add column if not exists revision bigint not null default 1,
  add column if not exists base_revision bigint;
alter table public.budgets
  add column if not exists server_updated_at timestamptz not null default now(),
  add column if not exists revision bigint not null default 1,
  add column if not exists base_revision bigint;
alter table public.goals
  add column if not exists server_updated_at timestamptz not null default now(),
  add column if not exists revision bigint not null default 1,
  add column if not exists base_revision bigint;
alter table public.goal_contributions
  add column if not exists server_updated_at timestamptz not null default now(),
  add column if not exists revision bigint not null default 1,
  add column if not exists base_revision bigint;

create index if not exists idx_wallets_user_server_updated on public.wallets (user_id, server_updated_at, id);
create index if not exists idx_categories_user_server_updated on public.categories (user_id, server_updated_at, id);
create index if not exists idx_transactions_user_server_updated on public.transactions (user_id, server_updated_at, id);
create index if not exists idx_budgets_user_server_updated on public.budgets (user_id, server_updated_at, id);
create index if not exists idx_goals_user_server_updated on public.goals (user_id, server_updated_at, id);
create index if not exists idx_goal_contributions_user_server_updated on public.goal_contributions (user_id, server_updated_at, id);

-- Replaces the body from 20261024101635_enforce_last_write_wins.sql; the trg_*_lww triggers keep calling it.
create or replace function public.lucento_reject_stale_update()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if new.user_id is distinct from old.user_id then
    raise exception 'user_id cannot change';
  end if;
  -- Clamp before comparing, so a fast clock can't win a conflict it would lose with a correct one.
  if new.updated_at > now() + interval '5 minutes' then
    new.updated_at := now();
  end if;
  if new.base_revision is distinct from old.revision then
    -- Concurrent (or base unknown): the later edit time wins; a tie keeps what is stored.
    if new.updated_at <= old.updated_at then
      return null;
    end if;
  end if;
  new.revision := old.revision + 1;
  new.base_revision := null;
  return new;
end;
$$;

create or replace function public.lucento_stamp_server_time()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  -- Only revision is reset on insert. base_revision must be left alone: in INSERT ... ON CONFLICT DO UPDATE this
  -- trigger also runs on the proposed row, and EXCLUDED.base_revision (the client's base) is read from it afterwards.
  if tg_op = 'INSERT' then
    new.revision := 1;
  end if;
  if new.updated_at > now() + interval '5 minutes' then
    new.updated_at := now();
  end if;
  new.server_updated_at := clock_timestamp();
  return new;
end;
$$;

create trigger trg_wallets_stamp before insert or update on public.wallets
  for each row execute function public.lucento_stamp_server_time();
create trigger trg_categories_stamp before insert or update on public.categories
  for each row execute function public.lucento_stamp_server_time();
create trigger trg_transactions_stamp before insert or update on public.transactions
  for each row execute function public.lucento_stamp_server_time();
create trigger trg_budgets_stamp before insert or update on public.budgets
  for each row execute function public.lucento_stamp_server_time();
create trigger trg_goals_stamp before insert or update on public.goals
  for each row execute function public.lucento_stamp_server_time();
create trigger trg_goal_contributions_stamp before insert or update on public.goal_contributions
  for each row execute function public.lucento_stamp_server_time();
