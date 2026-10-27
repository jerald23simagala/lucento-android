-- Recurring schedules and debt terms join cloud sync (app database v9; see docs/SYNC.md). Additive only: one new table
-- and four nullable columns on wallets. Nothing existing is dropped, rewritten or loosened.
--
-- 1. public.recurring mirrors the Room `recurring` table, keyed by the schedule's client uuid like every synced row.
--    Debt payments carry to_wallet_id (the card or loan paid) and interest_minor. It gets the same owner-only RLS,
--    authenticated-only grants, last-write-wins / revision trigger and server-time stamp as the other tables.
-- 2. public.wallets gains a card's or loan's optional terms (local `debt_terms`, one per wallet), so they travel with
--    their wallet. All nullable: rows from builds before v9 simply have none, and those builds never send the columns,
--    so an upsert from them leaves the stored terms as they are.

create table if not exists public.recurring (
  id text primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  name text not null,
  type text not null,
  amount_minor bigint not null,
  currency_code text not null,
  wallet_id text not null,
  category_id text,
  frequency text not null,
  interval_count integer not null,
  start_on bigint not null,
  end_on bigint,
  next_index integer not null default 0,
  paused boolean not null default false,
  to_wallet_id text,
  interest_minor bigint not null default 0,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  deleted_at timestamptz,
  server_updated_at timestamptz not null default now(),
  revision bigint not null default 1,
  base_revision bigint,
  unique (user_id, id),
  foreign key (user_id, wallet_id) references public.wallets (user_id, id) on delete restrict,
  foreign key (user_id, to_wallet_id) references public.wallets (user_id, id) on delete restrict,
  -- Only category_id is cleared: user_id is part of the key and must never be nulled.
  foreign key (user_id, category_id) references public.categories (user_id, id) on delete set null (category_id)
);
create index if not exists idx_recurring_user_server_updated on public.recurring (user_id, server_updated_at, id);
create index if not exists idx_recurring_user_wallet on public.recurring (user_id, wallet_id);
create index if not exists idx_recurring_user_to_wallet on public.recurring (user_id, to_wallet_id);
create index if not exists idx_recurring_user_category on public.recurring (user_id, category_id);

alter table public.recurring enable row level security;
create policy "recurring_select_own" on public.recurring for select to authenticated using ((select auth.uid()) = user_id);
create policy "recurring_insert_own" on public.recurring for insert to authenticated with check ((select auth.uid()) = user_id);
create policy "recurring_update_own" on public.recurring for update to authenticated
  using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy "recurring_delete_own" on public.recurring for delete to authenticated using ((select auth.uid()) = user_id);

grant select, insert, update, delete on table public.recurring to authenticated;
revoke all on table public.recurring from anon;

create trigger trg_recurring_lww before update on public.recurring
  for each row execute function public.lucento_reject_stale_update();
create trigger trg_recurring_stamp before insert or update on public.recurring
  for each row execute function public.lucento_stamp_server_time();

alter table public.wallets
  add column if not exists interest_rate_bps integer,
  add column if not exists repayment text,
  add column if not exists term_months integer,
  add column if not exists installment_minor bigint;
