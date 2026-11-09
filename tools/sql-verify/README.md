# sql-verify: Supabase migrations on real Postgres, offline

Replays every file in `supabase/migrations/` on [PGlite](https://pglite.dev) (Postgres 17 compiled to WebAssembly,
running in-process — no Docker, no server, no network after install). It first creates a small stand-in for what
Supabase provides (the `anon`, `authenticated` and `service_role` roles, `auth.users`, `auth.uid()`), then runs a SQL
suite that acts as different signed-in users and checks what each one can read, write and call.

Use it before a migration goes anywhere near `lucento-test`. A migration whose security is wrong should fail here first.

## Run

```sh
cd tools/sql-verify
npm install            # once; @electric-sql/pglite is pinned in package.json
npm run verify         # lucento-test's default privileges (new functions executable by their owner only)
npm run verify:stock   # Supabase's stock defaults (everything new granted to anon and authenticated)
```

Exit code 0 means every check passed. The last lines list each check, for example `ok  C9 partner updates/deletes of
wallets … affect 0 rows`. Run both variants: a migration must be safe whichever default privileges the project has.

`node run.mjs <migrations dir> <suite.sql> [skip …]` runs a different suite. By default it skips
`usage_events_purge_schedule`, because PGlite has no `pg_cron`.

## Suites

- `account_emails_suite.sql` — account emails (docs/EMAILS.md), 16 checks: only the service role claims an email; each
  email is claimed once per account and reference (welcome, each purchase); a released claim can be taken again; bad
  input refused; deleting the account removes its rows.
- `account_deletion_suite.sql` — account deletion (docs/ACCOUNT_DELETION.md), 10 checks: only a signed-in account can call
  `delete_my_account`, and only for itself; nothing of it is left in any public table or `auth.users`; other accounts
  untouched; a late sync with the old token can't recreate data; a Lifetime purchase restores on a new account; a
  Partner and an Owner deleting leave the other person's ledger intact.
- `play_purchases_suite.sql` — Lucento Pro Phase 4 (`docs/monetization/SERVER_CONTRACT.md` §3–§5), 30 checks: only the
  service role writes purchases; Lifetime's forever row and its 365-day services row from the purchase time; the linking
  rule (another account refused, a move after 30 days when bought for that account, a third move needs support);
  subscriptions through trial, grace, hold, expiry and resubscription, with a stale ending never undoing a newer purchase;
  refunds final; pending payments grant nothing; malformed input refused.
- `entitlements_suite.sql` — Lucento Pro tables and the AI allowance, 38 checks.
- `shared_space_suite.sql` — Lucento Shared (`docs/shared/SECURITY_MODEL.md`), 69 checks. It covers invitations
  (verified email, capacity, duplicates, expiry, withdrawal) and atomic acceptance. It covers what a Partner may read and
  write: transactions yes, the financial structure no, including upserts refused as a whole statement. It also checks
  server-stamped `created_by`/`updated_by`, stale-edit and replayed-push handling, immediate revocation on removal and
  leaving, strangers and `anon` seeing nothing, and that a solo account behaves exactly as before.

## What this is not

- Not Supabase. PostgREST, GoTrue and the real `auth` schema are absent, so request-level behaviour (headers, JWT
  verification, the REST upsert path) isn't exercised.
- Not a replacement for applying the migration to `lucento-test` and testing the app against it. Owners apply
  migrations (see `docs/handover/NEXT_TASKS.md`); production is never touched from here.
