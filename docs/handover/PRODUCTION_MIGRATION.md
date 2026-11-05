# Production migration runbook — 2026-11-03

## Pending: receivable wallets (owner round 10, branch `feature/owner-round-10`)

**Status: applied to production by the owner (2026-11-04); not yet on `lucento-test`.** Owner-run. No app schema
change (Room stays v11): only the server's list of known wallet types grows.

| Migration | Effect | Data risk |
|---|---|---|
| `20261105090000_receivable_wallets` | Re-creates `wallets_type_known` with `RECEIVABLE` added (live rows only, `NOT VALID`) | None: no row changes; existing rows aren't re-checked |

- **Apply to `lucento-test` before a round 10 debug build syncs a receivable there, and to production before a round 10
  release (or the test-signed APK) creates one while signed in.** Without it the server refuses that wallet's upload
  (check violation); it stays pending on the phone and nothing is lost, and it uploads once the migration is applied.
  Every other wallet and transaction keeps syncing.
- Apply: paste the file into the SQL editor (it is two statements). Verify with
  `select pg_get_constraintdef(oid) from pg_constraint where conname = 'wallets_type_known';` (the list includes
  `'RECEIVABLE'`) and record history with `supabase migration repair --status applied 20261105090000`.
- Smoke test: on a round 10 build, add a Receivable with monthly terms, sync, and check the wallet row's `type`.

---

## Credit card billing (app schema v11, branch `feature/core-screens-refinement`)

**Status: applied by the owner to `lucento-test` and production (2026-11-03)**, and sync checked on a phone (OT-1..3).
Owner-run, like the migrations below.

| Migration | Effect | Data risk |
|---|---|---|
| `20261103180000_credit_card_billing` | `wallets.credit_limit_minor` (bigint), `statement_day`, `due_day` (smallint), all nullable, + 3 range CHECKs on live rows (`NOT VALID`) | None: the new columns are null on every existing row |

- **Apply to `lucento-test` before signing a v11 debug build in against it, and to production before any v11 release.**
  v11 sends the three columns on every wallet row; without them PostgREST rejects the push (`PGRST204`) and wallet
  sync stalls (rows stay pending; nothing is lost).
- Apply: paste the file into the SQL editor as one `begin … commit` block. Verify with
  `select column_name, data_type, is_nullable from information_schema.columns where table_schema = 'public' and table_name = 'wallets' and column_name in ('credit_limit_minor','statement_day','due_day');`
  (3 rows, nullable) and record history with `supabase migration repair --status applied 20261103180000`.
- Smoke test: on a v11 build, give a credit card a limit and billing days, sync, and check the wallet row.

---


**Status: applied to production by the owner on 2026-11-03.** Confirm STEP 4 (history repair) was done.

**Owner-run.** Claude does not apply production migrations. Everything to paste is in
[production-migration-2026-11-03.sql](production-migration-2026-11-03.sql).

## What ships

| Order | Migration | Effect | Data risk |
|---|---|---|---|
| 1 | `20261101120000_financial_check_constraints` | 22 CHECK constraints (currency ISO, known enums, amount bounds) on wallets, categories, transactions, budgets, goals, recurring | None: `NOT VALID`, old rows not re-checked |
| 2 | `20261101180000_check_constraints_live_rows` | Re-creates the same 22 as `deleted_at is not null or (...)` so a soft delete of an old row always succeeds | None |
| 3 | `20261102120000_budget_lifecycle_and_institutions` | `budgets.month`, `budgets.effective_from`, `wallets.institution_id` (nullable text) + 4 format checks | None: new columns are null on every existing row |

All three have been on `lucento-test` since 2026-11-01/02. They passed the two-device lifecycle sync and the institution round trip there.

## Ordering against app releases

- **Apply before any v10 build (this branch) signs in against production.** v10 always sends `month`/`effective_from`/`institution_id`. Without the columns, PostgREST rejects the push (`PGRST204`) and sync stalls.
- Older builds keep syncing afterwards. They never send the new columns, and an upsert leaves the columns it omits unchanged.
- **Every phone on an account must run v10 before "Only <month>" or "<month> onward" budget amounts are used.** A pre-v10 phone shows those rows as ordinary monthly amounts and can overwrite them (RD-C2, owner decision 2026-11-03). Put this in the release notes.

## Steps (Supabase dashboard → production project → SQL editor)

1. **STEP 0, pre-checks (read-only).** Expected results:
   - **0a** lists 11 versions, none of them the three above.
   - **0b** returns 0 rows twice.
   - **0c** returns 0 in every row.
   - If **0c** is not zero, stop and send the counts. A live invalid row would fail its next sync push once the constraints exist. Fix the row or leave it as a known exception.
2. **STEP 1, apply.** Run it as one `begin … commit` block, so a failure leaves nothing applied. ADD COLUMN is metadata-only. The 4 new checks scan `budgets`/`wallets` briefly under an exclusive lock, which takes milliseconds at the current volume.
3. **STEP 2, verify.** Expected results:
   - **2a**: 26 rows (22 `validated = false`, 4 `true`).
   - **2b**: 3 text, nullable columns.
   - **2c**: true × 3.
   - **2d**: RLS true × 2.
4. **Record the history.** The SQL editor does not write `supabase_migrations.schema_migrations`. From a linked CLI, run:
   `supabase migration repair --status applied 20261101120000 20261101180000 20261102120000`
5. **Smoke test.** Sign a v10 build in on a production account. Change one budget with "This month only" and set a wallet's bank, then check the rows in the Table editor.
6. **STEP 3 (optional, later).** Run `VALIDATE CONSTRAINT` for the 22 constraints once 0c stays zero.

## Version drift (N-07), separate decision

Production records the earlier 3 migrations as `20261029030807/14/20`, while the repo files are `20261028180000`, `20261028190000` and `20261029010000`. Until this is reconciled, `supabase db push` would try to re-run them and fail on non-idempotent `create policy`. That is why this runbook uses the SQL editor rather than `db push`. If you want to reconcile it, run `supabase migration repair --status reverted 20261029030807 20261029030814 20261029030820` and then `supabase migration repair --status applied 20261028180000 20261028190000 20261029010000`. These commands touch the history only, not the schema.

## Rollback

The commented block at the end of the SQL file drops everything this rollout added. **Use it only if no v10 build has synced yet.** After a v10 build has synced, dropping the columns breaks v10 sync and discards month-specific budgets and wallet banks.
