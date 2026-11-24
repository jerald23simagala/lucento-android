# sql-verify: Supabase migrations on real Postgres, offline

Replays every file in `supabase/migrations/` on [PGlite](https://pglite.dev) (Postgres 17 compiled to WebAssembly,
running in-process — no Docker, no server, no network after install). It first creates a small stand-in for what
Supabase provides (the `anon`, `authenticated` and `service_role` roles, `auth.users`, `auth.uid()`), then runs a SQL
suite that acts as different signed-in users and checks what each one can read, write and call.

Use it before a migration goes anywhere near `lucento-test`. A migration whose security is wrong should fail here first.

## Run

```sh
cd tools/sql-verify
npm ci                 # once; versions are pinned in package.json and package-lock.json
npm run verify         # every *_suite.sql, lucento-test's default privileges (new functions executable by their owner only)
npm run verify:stock   # the same under Supabase's stock defaults (everything new granted to anon and authenticated)
DATABASE_URL=postgres://postgres:postgres@localhost:5432/postgres npm run concurrency   # needs a THROWAWAY Postgres 15+
```

Exit code 0 means every check passed. Each suite runs on a fresh database; the output lists every check, for example
`ok  C9 partner updates/deletes of wallets … affect 0 rows`, and ends with the total (13 suites, 573 checks on
2026-11-22, both variants). Run both variants: a migration must be safe whichever default privileges the project has.

`node run.mjs <migrations dir> <suite.sql> [skip …]` runs one suite. By default it skips `usage_events_purge_schedule`,
because PGlite has no `pg_cron`.

`concurrency.mjs` races several real sessions against one limit (PGlite has a single connection): two first inserts of a
new account, two transactions for the last places under a row ceiling, twenty writers at once, and forty anonymous usage
counts against the daily total. For Marketing approvals (MC1–MC5) it races an approval against Claude's new version in
both orders, two approvals of one post, two pushes of one pack, and Claude's feedback against a request being written:
each time the second session waits (for the marketing lock; two calls by the same admin already queue one step earlier,
on the gate's hourly counter row), then acts on what the first committed. MC6 races two auto-posting claims; MC7 races
the owner's done against Claude moving the same task to Waiting, in both orders (the second gets `stale` or
`not_allowed`, never a lost update). It creates and drops its own database. CI runs it against a Postgres 17 service container
(`.github/workflows/android.yml`, job `sql-verify`); locally any throwaway server works (the `embedded-postgres` npm
package was used on Windows). Never point it at lucento-test or production.

`catch_up_check.mjs` (run by hand: `node catch_up_check.mjs`) pastes the owner's SQL files the way the owner will: the
lucento-test catch-up, `APPLY_MISSION_CONTROL.sql` and its VERIFY, and (part D) `APPLY_MARKETING.sql` twice on top with
both VERIFY files all true; it also checks that `APPLY_MARKETING.sql` is current
(`python tools/supabase/build_marketing_apply.py --check`). Part E pastes `APPLY_AUTOPOST.sql` on top; part F pastes
`APPLY_TASKS.sql` twice, makes the tasks key, pastes `APPLY_AUTOPOST.sql` again after it (the key must survive), and
checks that all four VERIFY files stay true.

## Suites

- `autopost_suite.sql` — Mission Control's auto-posting for Marketing (docs/mission-control/v1/MARKETING_AUTOPOST.md),
  62 checks: the `autopost` secret purpose; customers refused first by all 9 page functions, anon by none, signed-in
  callers by none of the 3 ingest functions; the claim needing both the tick and the app secret; the label-to-platform
  map and due times; connections (step-up, ciphertext only, options of the right kind) and the destinations picked
  from them, refreshed or cleared on reconnecting; due_soon left out for posts Mission Control will post; jobs created
  within their lead time, stepped under a lease (lease_lost), preparing, ready, posted, sent to drafts, retried up to
  3 attempts, uncertain (recorded from a lapsed lease, confirmed by the owner), missed; the post marked out with what
  the owner finishes by hand; cancellation when undone or revised; launch rules; off; the clock's guards and setup
  refusals; the board, the summary (no token anywhere), disconnecting and the audit; and the security review's fixes:
  a step taken at its slot whose run never reported becomes uncertain (never handed out again) while one taken before
  only prepared and goes out again, "Try again" clears the lease, "post this one by hand" is busy while a step is under
  way, a renewal is saved only for its own account, Meta is kept without its user token, a cancelled job starts over,
  turning off stops the waiting steps; and the design review's: a post already in the apps by hand gets no job and a
  launch post waits (the card says why), a Check's answer is kept and cleared, Settings knows when something is due;
  and round 2's: every stop records why, a stopped step can be sent now (only when it can go) or taken by hand, the
  summary counts the accounts that can post, an account to reconnect posts nothing until reconnected, a platform
  disconnected before its slot is asked about, one Mission Control posts later isn't; and round 3's: a two-platform
  label is asked about by the platform still the owner's ("Instagram Reels"), the summary keeps an approved post's
  problems until handled (a stop past its window too) and says how the clock is, a post the owner confirmed says so;
  and round 4's: each by-hand platform answered on its own, "Don't post it there", "It's posted" taken back, the stall
  counted from when the work became due, History with the automatic steps, the questions in the post's own order. Race MC6 in `concurrency.mjs`: two claims at once hand each job out once.

- `marketing_suite.sql` — Mission Control's Marketing approvals (docs/mission-control/v1/MARKETING_APPROVALS.md), 75
  checks: who may call what (customers refused first by all 11 page functions, anon by none of them, signed-in callers by
  none of the 4 ingest functions; the app's and the backup's secrets refused by Claude's, the marketing secret by the
  console's file check); pushes (created, unchanged, version_changed, revised, version_out_of_order, withdrawn,
  truth_failed, phase_required, slot outside the pack, contact details, quotas: 1 MB, 40 posts, 10 files, 20 versions,
  30 pushes an hour, the 700 MB storage budget, another environment); NFC text; files (reuse across versions, pending,
  ready, already, mismatch and a new pathname, unknown, clean-up that spares the version in the apps); every decision
  and its refusals (stale_version, bad_state, not_ready, note rules and contact details, tags); in the apps, removed,
  posted by hand; the derived flags around launch (any time on launch day, too early, apps stale, due soon, missed,
  uploading and media missing); launch set and cleared (step-up, Manila time, limits); Claude's feedback (cursor, reset,
  500 at a time, "Claude saw it", the shared lock); Claude's notes (once per version); redaction; the append-only log
  and immutable versions; numbers; the badge; the audit; retention.

- `mission_control_suite.sql` — Mission Control V1 (docs/mission-control/v1), 90 checks: the gate (customers, aal1,
  deactivated admins and anon refused; refusals logged at most 10 an hour; catalog-wide: anon runs only the ingest
  functions, PUBLIC nothing, no API role on any `mc` table); the append-only audit log (UPDATE, DELETE, TRUNCATE refused
  for its owner too); step-up from Auth's own session record and from the token's `amr`; complimentary grants (90 days,
  Lifetime's two rows, overlapping and revoked grants, PLAY_* rows untouched byte for byte, hand-written rows imported
  once and never undone, expiry); suspension (ban, sessions and refresh tokens ended); deletion (cool-off, the same
  cascade as `delete_my_account`, cancellation, audit kept); moving a purchase (step-up, rows move, conflicts and refunds
  refused); the support form (signed out and in, idempotent retries, per-install limits, the shared signed-out cap, the
  diagnostics allow-list, nothing readable back); `report_client`; the records view (setting, step-up, notes masked,
  balances per docs/CALCULATIONS.md, another admin's session refused); activity, Overview, the nightly roll-up;
  Resend events (KlaroPH's anonymous); Google Play orders (test orders excluded, unknown orders not asked again, MRR,
  reported net).

- `tasks_suite.sql` — Mission Control › Tasks, the owner's to-do list (docs/mission-control/v1/OWNER_TASKS.md), 32
  checks: the fifth secret purpose `tasks`; a customer refused by all five page functions, the page's functions for
  signed-in callers only and Claude's four for anon only, no table readable; a push creates tasks (open, or waiting with
  what it waits for), changes nothing when nothing changed, makes a new version with its change note and never changes a
  status; all or nothing, with the problem named by index and code; 29 broken write-ups each refused (unknown fields,
  short or long text, http, localhost, an address or a user name in a link, too many steps, minutes, waiting rules, a
  status on push, a bad date, control characters, key-like text); 1 to 40 tasks a push; the board's order, write-ups
  and history; a stale row version refused; done, ask, note, not needed and reopen by the owner (audited, CRLF kept as
  LF, key-like notes refused, a question needs words); Claude's read position (oldest first, open questions, read again
  with since=0, a position past the end starts over); Claude's answer, wait and open, check (done only), reopen and
  done (with a reason), unknown codes and actions; the closed list without write-ups; one task with its history and
  versions; Overview's summary (overdue, due within two days, added and answered since the board was opened); the badge
  beside the earlier ones; 300 actions an hour; history and versions append-only.

- `security_remediation_suite.sql` — the security remediation (docs/security/REMEDIATION_REPORT.md), 78 checks: the
  Cloud write gate (Free refused, Pro accepted, a lapsed account reads and cleans up, renewal resumes, a Partner on the
  Owner's plan under the lenient and the strict rule, a removed Partner, a stranger), size bounds, the batch cap and row
  ceilings with exact counters, `record_usage_event`'s caps, permanent refunds after account deletion, account binding
  (`other_account`), test purchases, seed-id ownership, the per-account request quota, and the new default privileges.
  The audit's probes X1, X2, X3, X8a and X8b are named as such.

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
- `user_settings_suite.sql` — settings that follow the account (docs/SYNC.md "Settings"), 23 checks: each account reads
  and writes only its own row (a Partner and the Owner never see each other's); the compare-and-set rule on the
  PostgREST upsert path (kept on top of the revision read, skipped on an older one or none, whatever the times; a
  revision the phone sends is ignored); the CLOUD gate on insert and update, reading left open; a JSON object of at most
  16 KB; no deletes; `anon` sees nothing; account deletion removes the row.

## What this is not

- Not Supabase. PostgREST, GoTrue and the real `auth` schema are absent, so request-level behaviour (headers, JWT
  verification, the REST upsert path) isn't exercised.
- Not a replacement for applying the migration to `lucento-test` and testing the app against it. Owners apply
  migrations (see `docs/handover/NEXT_TASKS.md`); production is never touched from here.
