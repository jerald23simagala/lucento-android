-- Lucento cloud sync schema. Mirrors the Room entities that are user-owned financial data.
-- Primary keys are the same client-generated UUID strings (`client_uuid`) used locally, so a row has one
-- stable identity across every device. Deletions are soft (deleted_at) so a tombstone can propagate instead
-- of a hard delete that a stale device could otherwise resurrect.
--
-- Conflict policy (see docs/SYNC.md): last-write-wins per row using the client-supplied `updated_at`
-- timestamp. The database enforces it too, via the BEFORE UPDATE trigger in
-- 20261024101635_enforce_last_write_wins.sql, so a stale push can never clobber a newer row.
-- (Comment corrected after this migration was applied; the applied DDL is unchanged.)

create table if not exists public.wallets (
  id text primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  name text not null,
  type text not null,
  currency_code text not null,
  opening_balance_minor bigint not null,
  sort_order integer not null default 0,
  archived boolean not null default false,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  deleted_at timestamptz,
  unique (user_id, id)
);
create index if not exists idx_wallets_user_updated on public.wallets (user_id, updated_at);

create table if not exists public.categories (
  id text primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  name text not null,
  kind text not null,
  icon_key text not null,
  sort_order integer not null default 0,
  archived boolean not null default false,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  deleted_at timestamptz,
  unique (user_id, id)
);
create index if not exists idx_categories_user_updated on public.categories (user_id, updated_at);

create table if not exists public.transactions (
  id text primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  type text not null,
  amount_minor bigint not null,
  currency_code text not null,
  wallet_id text not null,
  category_id text,
  occurred_on bigint not null,
  note text not null default '',
  transfer_group text,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  deleted_at timestamptz,
  unique (user_id, id),
  foreign key (user_id, wallet_id) references public.wallets (user_id, id) on delete restrict,
  foreign key (user_id, category_id) references public.categories (user_id, id) on delete set null
);
create index if not exists idx_transactions_user_updated on public.transactions (user_id, updated_at);
create index if not exists idx_transactions_wallet on public.transactions (wallet_id);
create index if not exists idx_transactions_transfer_group on public.transactions (transfer_group);

create table if not exists public.budgets (
  id text primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  category_id text,
  currency_code text not null,
  limit_minor bigint not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  deleted_at timestamptz,
  unique (user_id, id),
  foreign key (user_id, category_id) references public.categories (user_id, id) on delete cascade
);
create index if not exists idx_budgets_user_updated on public.budgets (user_id, updated_at);

create table if not exists public.goals (
  id text primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  name text not null,
  target_minor bigint not null,
  currency_code text not null,
  target_date bigint,
  wallet_id text,
  archived boolean not null default false,
  scene_key text,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  deleted_at timestamptz,
  unique (user_id, id),
  foreign key (user_id, wallet_id) references public.wallets (user_id, id) on delete set null
);
create index if not exists idx_goals_user_updated on public.goals (user_id, updated_at);

-- Note: a goal's `photo_path` (a local file path to the user's own photo) is intentionally NOT synced —
-- it is meaningless on another device. Only the automatic `scene_key` picture travels with the goal.

create table if not exists public.goal_contributions (
  id text primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  goal_id text not null,
  amount_minor bigint not null,
  occurred_on bigint not null,
  note text not null default '',
  created_at timestamptz not null,
  updated_at timestamptz not null,
  deleted_at timestamptz,
  unique (user_id, id),
  foreign key (user_id, goal_id) references public.goals (user_id, id) on delete cascade
);
create index if not exists idx_goal_contributions_user_updated on public.goal_contributions (user_id, updated_at);
create index if not exists idx_goal_contributions_goal on public.goal_contributions (goal_id);
