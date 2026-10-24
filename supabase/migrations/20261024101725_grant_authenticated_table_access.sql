-- Signed-in users need table privileges for RLS to even be evaluated; this project does not auto-grant on new
-- public tables. Only `authenticated` is granted — `anon` (signed-out) gets nothing at all. Row visibility is
-- still decided entirely by the RLS policies in 20261024085649_enable_rls_policies.sql.
grant select, insert, update, delete on table
  public.wallets, public.categories, public.transactions, public.budgets, public.goals, public.goal_contributions
  to authenticated;
revoke all on table
  public.wallets, public.categories, public.transactions, public.budgets, public.goals, public.goal_contributions
  from anon;
