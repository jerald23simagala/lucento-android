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
