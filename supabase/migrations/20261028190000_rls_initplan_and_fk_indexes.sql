-- Performance follow-up for the original six sync tables (wallets, categories, transactions, budgets,
-- goals, goal_contributions). Not applied by the app. Commit only.
--
-- 1. Recreate each owner policy so auth.uid() is evaluated once per statement, (select auth.uid()),
--    instead of once per row. The role, command, and both the USING and WITH CHECK expressions stay
--    the same, including update checks on the existing row and the row being written.
-- 2. Covering indexes for composite foreign keys that had none. user_id → auth.users is already
--    covered by each table's unique (user_id, id).

drop policy if exists "wallets_select_own" on public.wallets;
drop policy if exists "wallets_insert_own" on public.wallets;
drop policy if exists "wallets_update_own" on public.wallets;
drop policy if exists "wallets_delete_own" on public.wallets;
create policy "wallets_select_own" on public.wallets for select using ((select auth.uid()) = user_id);
create policy "wallets_insert_own" on public.wallets for insert with check ((select auth.uid()) = user_id);
create policy "wallets_update_own" on public.wallets for update using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy "wallets_delete_own" on public.wallets for delete using ((select auth.uid()) = user_id);

drop policy if exists "categories_select_own" on public.categories;
drop policy if exists "categories_insert_own" on public.categories;
drop policy if exists "categories_update_own" on public.categories;
drop policy if exists "categories_delete_own" on public.categories;
create policy "categories_select_own" on public.categories for select using ((select auth.uid()) = user_id);
create policy "categories_insert_own" on public.categories for insert with check ((select auth.uid()) = user_id);
create policy "categories_update_own" on public.categories for update using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy "categories_delete_own" on public.categories for delete using ((select auth.uid()) = user_id);

drop policy if exists "transactions_select_own" on public.transactions;
drop policy if exists "transactions_insert_own" on public.transactions;
drop policy if exists "transactions_update_own" on public.transactions;
drop policy if exists "transactions_delete_own" on public.transactions;
create policy "transactions_select_own" on public.transactions for select using ((select auth.uid()) = user_id);
create policy "transactions_insert_own" on public.transactions for insert with check ((select auth.uid()) = user_id);
create policy "transactions_update_own" on public.transactions for update using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy "transactions_delete_own" on public.transactions for delete using ((select auth.uid()) = user_id);

drop policy if exists "budgets_select_own" on public.budgets;
drop policy if exists "budgets_insert_own" on public.budgets;
drop policy if exists "budgets_update_own" on public.budgets;
drop policy if exists "budgets_delete_own" on public.budgets;
create policy "budgets_select_own" on public.budgets for select using ((select auth.uid()) = user_id);
create policy "budgets_insert_own" on public.budgets for insert with check ((select auth.uid()) = user_id);
create policy "budgets_update_own" on public.budgets for update using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy "budgets_delete_own" on public.budgets for delete using ((select auth.uid()) = user_id);

drop policy if exists "goals_select_own" on public.goals;
drop policy if exists "goals_insert_own" on public.goals;
drop policy if exists "goals_update_own" on public.goals;
drop policy if exists "goals_delete_own" on public.goals;
create policy "goals_select_own" on public.goals for select using ((select auth.uid()) = user_id);
create policy "goals_insert_own" on public.goals for insert with check ((select auth.uid()) = user_id);
create policy "goals_update_own" on public.goals for update using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy "goals_delete_own" on public.goals for delete using ((select auth.uid()) = user_id);

drop policy if exists "goal_contributions_select_own" on public.goal_contributions;
drop policy if exists "goal_contributions_insert_own" on public.goal_contributions;
drop policy if exists "goal_contributions_update_own" on public.goal_contributions;
drop policy if exists "goal_contributions_delete_own" on public.goal_contributions;
create policy "goal_contributions_select_own" on public.goal_contributions for select using ((select auth.uid()) = user_id);
create policy "goal_contributions_insert_own" on public.goal_contributions for insert with check ((select auth.uid()) = user_id);
create policy "goal_contributions_update_own" on public.goal_contributions for update using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy "goal_contributions_delete_own" on public.goal_contributions for delete using ((select auth.uid()) = user_id);

create index if not exists idx_transactions_user_wallet on public.transactions (user_id, wallet_id);
create index if not exists idx_transactions_user_category on public.transactions (user_id, category_id);
create index if not exists idx_budgets_user_category on public.budgets (user_id, category_id);
create index if not exists idx_goals_user_wallet on public.goals (user_id, wallet_id);
create index if not exists idx_goal_contributions_user_goal on public.goal_contributions (user_id, goal_id);
