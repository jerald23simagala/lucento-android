# Account deletion

Google Play requires an app that lets people create an account to let them delete it, both inside the app and from a
web page that works without the app. Lucento offers both, built on 2026-11-07 on `feature/play-readiness`.

## In the app

**Settings › Cloud sync › Delete account** appears whenever someone is signed in, whatever their plan.

1. A confirmation dialog (`feature/account/DeleteAccountDialog`) says what is deleted:
   - the account and its cloud records;
   - the Shared Space, if they own one; their membership, if they are a Partner;
   - this phone's records, with a reminder to save a backup file first.

   A live Google Play subscription or Cloud add-on adds two things: a "doesn't cancel your subscription" note and a
   **Manage subscription in Google Play** button. Lifetime adds "restore it later on a new account". The dialog comes
   from `DeletionPlan.of(EntitlementState)`.
2. **Delete account** stays disabled until "I understand this can't be undone" is ticked. While the call runs, nothing
   in the dialog can be tapped.
3. `AuthRepository.deleteAccount()` calls `public.delete_my_account()`. It is never retried automatically.
4. Only after the server confirms is the phone wiped. The wipe uses the same order as a sign-out (`AccountBoundary`):
   - a durable wipe marker;
   - the local session;
   - the ledger;
   - the server revoke, which finds the sessions already gone. `SessionRevoker` treats that 4xx as done.
5. If the call fails, or the phone is offline or signed out, nothing changes on the phone. The reason appears in the
   dialog (`DeleteAccountFailures`):
   - "Already deleted" (`P0002`) counts as done, so a retry after a lost answer finishes the job.
   - A timeout says the deletion may have happened and that trying again is safe.

## On the server

`supabase/migrations/20261109090000_account_deletion.sql` adds one function:

- **Caller.** Only `authenticated` may run it. It takes no argument, so an account can only delete itself.
- **What it does.** It runs `delete from auth.users where id = auth.uid()`.
- **What goes with it.** Every user-owned table already cascades from `auth.users`. On lucento-test, 2026-11-07:
  - wallets, categories, transactions, budgets, goals, goal_contributions, recurring and ledger_settings;
  - entitlements, purchase_links and ai_usage;
  - shared_spaces (with space_members and space_invites) and space_members;
  - Supabase Auth's own identities, sessions and factors.
- **What stays.**
  - Transactions a Partner added to an Owner's ledger belong to that ledger. Their `created_by` keeps the bare id.
  - `usage_events` carry a random install id, not the account.
- **Purchases.** Deleting `purchase_links` frees the Play purchase. The same Google account can restore Lifetime on a
  new Lucento account through the first-verification path of `apply_play_purchase`.
- **Tests.** `tools/sql-verify/account_deletion_suite.sql` runs 10 checks, all passing under both privilege defaults:
  - D1–D2: anon and token-less calls are refused;
  - D3–D4: nothing of the account is left in any public table or in auth.users;
  - D5: other accounts are untouched;
  - D6: a second call reports "already deleted";
  - D7: a late sync with the old token can't recreate data;
  - D8: Lifetime restores on a new account;
  - D9–D10: a Partner or an Owner deletes.

**The owner applies the migration** to lucento-test, then production (NEXT_TASKS OT-15). Until it is on a project, the
app answers "Couldn't delete your account right now… email lucentoapp@gmail.com" and changes nothing.

## Without the app

`https://www.lucentoapp.com/delete-account`, on the lucento-website page `src/pages/delete-account.astro`, is the
**Delete account URL** in Play Console › App content › Data safety. It gives:

- the in-app steps;
- an email request to lucentoapp@gmail.com, sent from the account's address, deleted within 30 days and confirmed by
  reply;
- what is deleted;
- what is kept and for how long:
  - Google Play's own purchase records;
  - routine database backups, which expire within 30 days;
  - usage counts, deleted after 90 days;
  - the person's own backup files.

**Handling an email request.** Reply to the sender's address to confirm. Then find the user in Supabase:
Authentication › Users, search by email. Delete it, which runs the same cascade, and reply when done. Keep no copy of
the request beyond the reply thread.

## Not covered

- **Revoked purchases.** A purchase that was refunded and then restored on a new account isn't refused by the
  database's sticky-refund rule, because the refunded row was deleted with the old account. Two things limit this:
  Google Play stops listing a refunded purchase on the phone, and the daily voided-purchases check (last 30 days)
  revokes it again by its token hash.
- **Device tests.** No end-to-end run against a real signed-in account yet. The dialog is covered by
  `DeleteAccountDialogTest` (androidTest) and the logic by `AccountDeletionTest` (JVM).
