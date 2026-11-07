-- Account deletion (Google Play's account-deletion requirement; docs/ACCOUNT_DELETION.md).
--
-- public.delete_my_account(): the signed-in person deletes their own Lucento account. It takes no argument, so an
-- account can only ever delete itself. Deleting the auth.users row removes everything stored under the account in the
-- same transaction, through the foreign keys that already cascade (queried on lucento-test 2026-11-07):
--   * the synced ledger: wallets, categories, transactions, budgets, goals, goal_contributions, recurring, ledger_settings;
--   * Lucento Pro: entitlements and purchase_links (so a Google Play purchase can be restored on a new account later);
--   * the AI allowance counters (ai_usage);
--   * Lucento Shared: an Owner's space with its memberships and invitations; a Partner's membership. Transactions a
--     Partner added to an Owner's ledger belong to that ledger and stay; their created_by keeps the bare id;
--   * Supabase Auth's own rows (identities, sessions, factors).
-- Usage counts (usage_events) carry a random install id, not the account, and are not touched here.
--
-- Additive: one new function. Nothing existing changes.

create or replace function public.delete_my_account()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid := auth.uid();
begin
  if v_user is null then
    raise exception 'Sign in to delete your account' using errcode = '28000';
  end if;

  delete from auth.users u where u.id = v_user;
  if not found then
    -- A token that outlived its account (a second tap, or a second phone): nothing is left to delete.
    raise exception 'This account was already deleted' using errcode = 'P0002';
  end if;

  return jsonb_build_object('deleted', true);
end;
$$;

comment on function public.delete_my_account() is
  'Deletes the calling account and, through cascading foreign keys, everything stored under it. docs/ACCOUNT_DELETION.md';

revoke all on function public.delete_my_account() from public, anon;
grant execute on function public.delete_my_account() to authenticated;
