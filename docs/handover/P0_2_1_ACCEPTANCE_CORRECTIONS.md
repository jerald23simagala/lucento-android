# P0-2.1 — Acceptance corrections

**Date:** 2026-11-01 · **Branch:** `feature/ai-development-workflow` (uncommitted) · **Environment:** `lucento-test` only.
Follows `P0_2_ACCEPTANCE_REVIEW.md` (approved corrections 1 and 2; task 3 recorded).

**Evidence labels:**

| Label | Meaning |
|---|---|
| **Implemented** | Code changed |
| **Unit-tested** | JVM test written and passing |
| **Build-verified** | Built and inspected |
| **Runtime-tested / DB-verified** | See `P0_2_DEVICE_QA_RESULTS.md` |

## 1. Expanded account detection — Implemented, Unit-tested, Runtime-tested

**Rule.** The account "has data" when it has any of these live (`deleted_at is null`) rows of the signed-in user:

| Order | Check | Requests |
|---|---|---|
| 1 | a wallet (archived included) | always |
| 2 | an active expense category | only if 1 found nothing |
| 3 | a transaction | only if 1–2 found nothing |
| 4 | a scheduled payment (`recurring`) | only if 1–3 found nothing |
| 5 | a budget | only if 1–4 found nothing |

The checks stop at the first hit, so an account with a wallet costs one request (tested). Goals stay excluded, as the
review found: a goal-only account sets up as new and its goals arrive with the first sync without duplicates.
Ownership is enforced twice: the query filters on the authenticated `user_id`, and RLS restricts it to `auth.uid()`.
Any *error* (network, expired session, missing grant / permission refusal) is `AccountProbe.Unavailable`, never
"empty". **Qualification (QA review):** an RLS policy *mismatch* on a SELECT returns zero rows without an error, so it
would read as `Empty`. That can only happen if the owner-only policy were broken, because the session's own user id is
queried. Pull-before-upload in the first sync limits the damage even then.

**Consistency with the restore (found while implementing).** The restore's own rule (`RestorePlan.hasData`) read only
*active* wallets (`WalletRepository.observeWallets`), while the server check also counts archived ones. An
archived-wallet-only account would have shown "Welcome back" and then "nothing to restore". Both sides now count the
same things:
- `RestorePlan.hasData(wallets, categories, otherRecords)`.
- `otherRecords` comes from `SyncRepository.localHasRestorableRecords()`: any wallet including archived, a transaction,
  a schedule or a budget.

Pulling a transaction, schedule or budget always brings its parent wallet or category with it (`resolveWallet`,
`resolveCategory`), so the restore side is never weaker than the check.

**Files.**
- `data/sync/SyncRepository.kt`: `probeAccount`, `localHasRestorableRecords`, `localHasFinancialData` now built on it.
- `domain/Restore.kt`: `hasData(…, otherRecords)`.
- `feature/onboarding/OnboardingViewModel.kt`: the restore passes `otherRecords`.
- `feature/onboarding/OnboardingScreen.kt`: the "nothing to restore" text now lists transactions, budgets and scheduled
  payments too.

**New tests** (`OnboardingDataSafetyTest`, 9; `RestorePlanTest`, assertions added to an existing test):

| Test | Shows |
|---|---|
| transactions are detected when their wallet was deleted on another device | transaction-only (the only data-model-possible shape) |
| scheduled payments are detected when their wallet was deleted on another device | schedule-only |
| budgets are detected when their category is archived and there is no wallet | budget-only |
| deleted transactions, schedules and budgets do not count | soft deletes ignored |
| an authentication failure during the check is never treated as a new account | auth failure → RETRY |
| a permission refusal on any table is never treated as a new account | RLS/permission refusal on each of the 5 tables → Unavailable |
| the check stops at the first hit - an account with a wallet costs one request | request economy |
| an archived-wallet-only account passes both the check and the restore | consistency |
| a budget-only account passes both the check and the restore | consistency, downloaded once |

The existing network-failure test still passes, and no existing test was weakened. The only edit to an existing
assertion is the goal/archived-category test, which still expects "empty" because the rule excludes those.

## 2. Combine-dialog wording — Implemented, Runtime-tested

The new text (`SettingsScreen.kt`) is shown before anything is uploaded:

> Your account already has Lucento data, and this phone has records of its own. Your wallets, transactions, goals and
> scheduled payments from both are kept and added together.
>
> Categories with the same name are combined into one. If both had a budget for that category, one of the two
> amounts is kept — check your budgets afterwards.
>
> Anything you recorded in both places will appear twice and be counted twice in your totals until you delete the
> extra copy.
>
> Choose Don't connect to leave this phone as it is, not signed in.

Buttons: **Add to account** / **Don't connect**. The sync behaviour is unchanged. On device, the collision played out
exactly as described: Rent kept the phone's 500 over the account's 900, while Groceries kept the account's 300 (see the
QA results).

## 3. Budget-collision task — recorded, not implemented

`NEXT_TASKS.md` → **P1-8 Budget conflicts when categories are combined**. It covers:
- Options A (larger amount), B (latest edit), C (ask the user) and D (keep both and flag; recommended, with B as the
  displayed default).
- Deterministic keeper choice.
- History, storage and migration implications.
- Cross-device consistency and backup merge.
- The test list.

It reflects your requirement that nothing be discarded silently.

## 4. Verification (Phase 2)

| Check | Result | Evidence |
|---|---|---|
| Focused tests (`OnboardingDataSafetyTest`, `RestorePlanTest`) | 35 + 5 passed | Verified |
| Full unit suite `testDebugUnitTest` | **620 passed, 0 failed, 0 errors** (611 before + **9 new**) | Verified |
| `compileDebugAndroidTestKotlin` | Succeeds | Verified |
| `lintDebug` | **0 errors**, 9 warnings, 78 informational (unchanged) | Verified |
| `assembleDebug`, `assembleRelease` (`-x uploadCrashlyticsMappingFileRelease`) | Both succeed | Verified |
| Debug target | `BuildConfig.SUPABASE_URL` ref `ogenvdopfgocjdkcrsny`; debug APK dex: test ref in 2 dex, production ref in 0 | Build-verified |
| Release target | ref `hmtcuttadbgbldvzsfhz`; release APK dex: production ref in 1 dex, test ref in 0 | Build-verified |
| Secrets | No `sb_secret_`/`service_role` in either APK; no publishable keys or JWTs in tracked files | Verified (scan) |
| Production | Not touched: no SQL, configuration or deploy. Production MCP access isn't even connected (the connector now sees only "Lucento Test"). | — |

**P0-2 test totals:** 26 tests from P0-2 plus 9 from P0-2.1 gives **35 in `OnboardingDataSafetyTest`**. The suite total
is **620**.

## 5. Final-acceptance additions (2026-11-01)

Details and statuses are in `P0_2_FINAL_ACCEPTANCE_REPORT.md`.

| Item | State |
|---|---|
| O-10 and E-2 | PASS on device |
| Restore tests for transaction-only and schedule-only accounts (the two named in the handover) | Implemented, passing |
| F-7: the next account after a sign-out goes through onboarding (restore, setup, or Try again) | Implemented (Architect- and QA-reviewed). Unit-tested (`LocalLedgerResetTest`, `AccountSetupResetTest`, `WipeOrderTest`, 4 F-7 cases). Returning-account path device-verified |
| Release-build guard against secret or service-role Supabase keys | Implemented (`app/build.gradle.kts`) |
| F-8 auth redirect | Test project redirects to `localhost:3000`; production NOT VERIFIED; remediation proposed, not applied |
| Suite | **634 passed**, lint 0 errors, debug and release built; debug → test, release → production in APK dex |
