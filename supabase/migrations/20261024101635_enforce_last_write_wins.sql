-- Server-side last-write-wins guard (see docs/SYNC.md "Conflict resolution"). PostgREST upserts cannot carry a
-- WHERE clause, so this trigger is what actually stops an older client write from overwriting a newer row:
-- an UPDATE whose updated_at is older than the stored one is silently skipped (the row keeps its newer
-- values). Equal timestamps are allowed so a retried push is idempotent. Also forbids changing user_id.
create or replace function public.lucento_reject_stale_update()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if new.user_id is distinct from old.user_id then
    raise exception 'user_id cannot change';
  end if;
  if new.updated_at < old.updated_at then
    return null;
  end if;
  return new;
end;
$$;

create trigger trg_wallets_lww before update on public.wallets
  for each row execute function public.lucento_reject_stale_update();
create trigger trg_categories_lww before update on public.categories
  for each row execute function public.lucento_reject_stale_update();
create trigger trg_transactions_lww before update on public.transactions
  for each row execute function public.lucento_reject_stale_update();
create trigger trg_budgets_lww before update on public.budgets
  for each row execute function public.lucento_reject_stale_update();
create trigger trg_goals_lww before update on public.goals
  for each row execute function public.lucento_reject_stale_update();
create trigger trg_goal_contributions_lww before update on public.goal_contributions
  for each row execute function public.lucento_reject_stale_update();
