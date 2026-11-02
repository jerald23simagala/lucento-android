# Lucento Android — Prioritized Next Tasks

**Updated:** 2026-10-31 by Claude after the initial audit and its Part II extension
(`docs/handover/CLAUDE_INITIAL_AUDIT.md`). This replaces the PDF-derived task list; its open items are folded in below.
**Baseline:** `7928c63` (`origin/main`), working on `feature/ai-development-workflow`.
**Issue IDs** refer to `KNOWN_ISSUES.md` (N-xx = new in this audit, O-xx / S-xx = QA matrices in the audit §17–§18).

> Production writes, production deploys, production env changes, merges and destructive Git operations need explicit
> owner approval.

## Execution status — master backlog run (2026-11-01, uncommitted on `feature/ai-development-workflow`)

Statuses below were verified against the code, tests and devices, not copied from older notes.

| Task | Status | Evidence |
|---|---|---|
| P0-1 debug → test, TEST badge | Done (earlier) | P0-1 notes |
| P0-1 CI release config + fail-if-missing | **Done locally**; variables added by the owner 2026-11-02; first Actions run pending | `app/build.gradle.kts` (`LUCENTO_REQUIRE_RELEASE_CONFIG`), `.github/workflows/android.yml` |
| P0-1 debug `AI_INTERPRET_URL` override | **Done locally**: debug uses only `LUCENTO_DEBUG_AI_INTERPRET_URL` (build fails if it's production); Smarter reading hidden when blank | `app/build.gradle.kts`, `SettingsScreen.kt` |
| P0-2 (original) sign-out and session coordinator (R10-01, N-03, N-04, offline revoke) | **Done locally, device-verified** (offline revoke, kill mid sign-out); Architect-reviewed; QA review interrupted by a usage limit, to resume | `data/sync/SessionRevocation.kt`, `SessionRevocationTest`, `artifacts/p0-session-revoke/` |
| P0-2 onboarding safety | Closed with documented limitations | `P0_2_FINAL_ACCEPTANCE_REPORT.md` |
| P0-3 amount parsing | PASS | `P0_3_AMOUNT_PARSING_REPORT.md` |
| P0-3 follow-up: ReceiptParser alignment | **Done**: parity test proves every receipt reading is one `MoneyParser` allows; the one deliberate receipt convention ("Rp 25.000" = 25,000) is documented | `ReceiptParserAlignmentTest` |
| P0-3 follow-up: device check of transfer, adjust, schedule, goal, debt | **Done** (all five on device); found and fixed a silent disabled button in Adjust balance | `artifacts/p0-3-amount-parsing/device-log.txt` |
| P0-3 historical balance review | **Cancelled by the owner** (QA-Cash is test data) | — |
| P0-4 / P1-2 / P1-4 onboarding detection, first-sign-in safety, onboarding QA | Done (P0-2 device QA O-01…O-15) | `P0_2_DEVICE_QA_RESULTS.md` |
| P1-5 transactional pull | **Done locally**: each pulled row's read-decide-write is one Room transaction; queued deletes re-read and skip a row edited since (D3) | `SyncRepository.kt`, P1-5 tests in `OnboardingDataSafetyTest` |
| P1-6 CI fails on screenshot-tour failure (N-18) | Already done on `main` (`exit 1`) | `android.yml` |
| P1-8 budget conflicts | **Done locally** with the owner's choice "keep the latest edit" | `CategoryDedup.mergedLimit`, `CALCULATIONS.md`, tests |
| QA-N1 sign-out marker failure crash | **Fixed** | `SettingsViewModel.confirmAccountPrompt` |
| QA review of the batch (P0 coordinator, P0-3 follow-ups, P1-5, P1-8, P2-9, P1-7, composer) | **PASS WITH NOTES** (no blockers). Fixed from its notes: 3+ copy budget fold (1), revoke queued at tap time (2), constraints for live rows only + outbound row check (3), Adjust live messages (4), redacted `PendingRevoke.toString` (9), receipt text not returned as typing (10), `FittingText` never wraps a number and refits on resize (11), CI requires the Google client id (12), non-vacuous receipt parity test. Rest logged as QA-R* in KNOWN_ISSUES | agent report; tests below |
| P1-1 Google sign-in | **Blocked on owner**: register the debug SHA-1 as an Android OAuth client (exact steps in KNOWN_ISSUES G-01 / P1-1) | device log |
| P1-3 two-device S-matrix | **Partly covered**: restore on a second device, F-7, O-15 convergence, live constraint write; the full S-01…S-20 matrix is not run | device logs |
| P1-6 migration drift (N-07), production smoke test | **Blocked**: needs production access and owner approval | — |
| P1-7 accessibility | **Done locally**: adaptive dock, `FittingText`, Home large-text layout. Device: 2.0x, 360dp at 1.0x/1.3x. TalkBack pass still open | `artifacts/p1-7-accessibility/` |
| P2-3 budget split (D1) | **Done locally**: summary says "Spent = in your plan + outside it" | `BudgetScreen.kt` |
| P2-9 server constraints | **Done on `lucento-test`** (two migrations: `20261101120000`, `20261101180000` live rows only); production waits for approval | SQL checks, live app write |
| P2-10 app lock + hide in recents (D8) | **Done locally**: new deps `androidx.biometric` 1.1.0 and `fragment-ktx` 1.8.5; CI allow-list adds `USE_BIOMETRIC`/`USE_FINGERPRINT` | `artifacts/p2-10-app-lock/`, `AppLockSessionTest` |
| Composer: dismissing the wallet picker lost the message | **Fixed** (typed/spoken text returns to the field; a receipt asks to rescan) | `ComposerQuickEntryTest` |
| P2-1, P2-2, P2-4…P2-8, P2-11, P3 | **Not started in this run** | — |

## Decision register

| ID | Decision | Answer | Date |
|---|---|---|---|
| P1-8 | Budget amount when categories combine | **Keep the latest edit** (set beats unset; tie → smaller sync id) | 2026-11-01 |
| D1 | Budget "Spent" | **All expenses, shown as in-plan + outside-plan** (P2-3) | 2026-11-01 |
| D2 | Conflict policy | **Whole-row latest wins**, plus a "changed on another device" notice (P2-6) | 2026-11-01 |
| D3 | Delete vs. later edit | **Newer action wins** (current) | 2026-11-01 |
| D4 | Wallet dedup on first sign-in | **Never merge; keep the warning** (current behaviour) | 2026-11-01 |
| D5 | R10-01 / offline revoke before beta | **Required before beta**, done locally (session coordinator) | 2026-11-01 |
| D6 | Release application ID | **`com.lucento.app`** (old `app.lucento` references are stale) | 2026-11-01 |
| D7 | Supabase plan | **Stay on Free**; leaked-password protection stays unavailable; keep the in-app checks | 2026-11-01 |
| D8 | App lock | **Optional lock + hide in recents** (P2-10) | 2026-11-01 |
| D9 | PR #10 (restore original onboarding) | **Close as superseded** — done by the owner | 2026-11-01 |
| D10 | Merged remote branches | **Delete merged branches** — 15 deleted; `release/v0.4.0`, `cursor/visual-style-refresh-0c6a`, `restore/original-onboarding` kept (unmerged) | 2026-11-01 |
| CI config | Release config in CI | Owner added **repository variables** `LUCENTO_SUPABASE_URL`, `LUCENTO_SUPABASE_PUBLISHABLE_KEY`, `LUCENTO_GOOGLE_WEB_CLIENT_ID` and `LUCENTO_REQUIRE_RELEASE_CONFIG=true` (public identifiers, so variables not secrets; Vercel keeps its own copies; `LUCENTO_SITE_URL` and `GEMINI_API_KEY` stay Vercel-only). Workflow and Gradle now use `LUCENTO_SUPABASE_PUBLISHABLE_KEY` (was `..._ANON_KEY`); the debug key stays `LUCENTO_DEBUG_SUPABASE_ANON_KEY`. Not yet proven by a GitHub Actions run | 2026-11-02 |
| AUTH-B | Sign out QA account B's sessions | **Approved and done** (not a deletion) | 2026-11-01 |
| F-8 | Auth email redirect | Owner set Site URL/redirect to `https://www.lucentoapp.com/auth/confirmed` on both projects. **Not independently verified** (needs one QA email per project) | 2026-11-01 |
| P2-9 | CHECK constraints | **Test project only** — applied to `lucento-test`; app write accepted live (EXPENSE 325 USD) | 2026-11-01 |
| B4 / P1-1 | Google one-tap | "Re-establish what worked." Diagnosed: this machine's debug SHA-1 is not registered — **owner action** (see KNOWN_ISSUES P1-1) | 2026-11-01 |

## Blocking prerequisites (owner actions)

| # | Action | Unblocks |
|---|---|---|
| B1 | Give the claude.ai Supabase connector access to `lucento-test`: move it into the "Lucento" org, or reconnect including its org (audit §15) | All QA on test |
| B2 | Provide test-project URL and publishable key for `local.properties` and CI | P0-1 |
| B3 | Approve the Vercel team consent link for `jerald23simagalas-projects` (audit §16) | Vercel audit, preview validation |
| B4 | Clarify the "Google Sign-In webhook" behaviour that used to work (audit §14.5) | P1-1 |
| B5 | Dedicated QA accounts (Google and email) on `lucento-test`; a second Play-Store device | P1-3, P1-4 |
| B6 | Optional: allow read-only `execute_sql` on production for catalog verification | P1-6 |

## P0 — Financial integrity, auth/session reliability, environment separation, security boundaries

**P0-1 Environment separation** (N-01, N-24). **Debug → test done locally on 2026-10-31 (uncommitted, awaiting
review).** The TEST badge (`EnvironmentBadge`, debug only) is also done locally. Still open: the CI release config
and fail-if-missing (N-24), and a debug override for `AI_INTERPRET_URL`.
- *Problem:* debug builds hit production; CI release builds ship without sync config.
- *Plan:* per-build-type config (debug → `lucento-test`, release → production from CI secrets); fail the release
  job when the config is missing; a visible TEST badge in debug; document it in `docs/SYNC.md`.
- *Depends on:* B2.
- *Risk:* low.
- *Validation:* `BuildConfig` host checked per variant in CI; a debug sign-in appears in test logs only.

**P0-2 Sign-out and session coordinator** (R10-01, N-03, N-04, auto-refresh race, offline revoke)
- *Plan:* an app-scoped, non-cancellable coordinator. Clear the in-memory session and cancel WorkManager *before*
  taking the sync lock. The launch resume path clears the stored session. A persisted, token-free revoke-pending
  marker retried on launch and on connectivity.
- *Risk:* medium (safety-critical). Get architect and QA review.
- *Validation:* JVM kill-point, cancellation, expired-token and offline tests; device kill test on test.

**P0-3 Strict amount parsing** (N-02, O-13). **Done locally 2026-11-01 (uncommitted), PASS recommended** — see
`P0_3_AMOUNT_PARSING_REPORT.md`. Follow-ups done 2026-11-01: `ReceiptParser` alignment (parity test) and device runs of
the transfer, adjust, schedule, goal and debt screens. (The historical balance review was cancelled by the owner.)

The original plan follows for the record.
- *Plan:* validate grouping (groups of 3); reject or confirm an ambiguous single separator; currency-aware.
- *Risk:* low (pure function).
- *Validation:* table tests for en-US, de-DE, fr-CH, JPY, BHD; composer, wallet-balance, budget and adjust flows.

**P0-4 Onboarding cloud-data detection** (O-03, O-07). **Implemented as "P0-2" on 2026-11-01** (uncommitted, awaiting
review). It also covers P1-2 (first-sign-in data safety) and the sync-before-onboarding trigger. See
`P0_2_ONBOARDING_IMPLEMENTATION_REPORT.md`. Remaining: on-device run of O-01…O-13 once a QA account and a clean
install are available.
- *Problem:* signing in on the normal setup path into an account with data completes onboarding locally, then
  duplicates wallets and deletes freshly picked categories.
- *Plan:* after any sign-in during onboarding, probe the account (all tables, ignoring soft-deleted rows). If it
  has data, route to the restore flow ("This account already has your data — bring it here") instead of new setup.
  Never create guided-setup wallets before the first pull.
- *Validation:* O-03 and O-07 on device; unit tests on the routing decision.

## P1 — Google sign-in, first-sign-in integrity, two-device validation, conflicts, release readiness

**P1-1 Google sign-in restoration (diagnose per layer)** (audit §14)
- Git history shows **no removed webhook or sign-in code**. The chain worked on production on 2026-10-27.
- *Plan:* B4 clarification → reproduce on API 36 Play-Store AVD with a test build → fix the failing layer using
  existing configuration (SHA-1 registration, test-project Google provider, Vercel Preview env) → clear stale
  sign-in errors between onboarding steps.
- *Depends on:* P0-1, B1, B3, B4.
- *Validation:* G1–G9 (audit §14.6) on test.

**P1-2 First-sign-in data safety** (N-16, O-05, O-11, `remoteHasData`). **Done within P0-2**; device-verified
2026-11-01 (O-05, O-06, O-11 PASS).

**P0-2 close-out (remaining):**
**Recommendation: P0-2 — CLOSED WITH DOCUMENTED VALIDATION LIMITATIONS** (`P0_2_FINAL_ACCEPTANCE_REPORT.md`), pending
the owner's review.
- **Accepted limitations:**
  - Physical-device background restore: DEFERRED. Run it on the first physical device.
  - Settings account-check failure: automated coverage accepted.
  - Empty account after a switch: automated only.
- **Release blocker, separate from P0-2:** F-8.
  - Read production's Authentication → URL Configuration.
  - Set the Site URL and redirect to `https://www.lucentoapp.com/auth/confirmed` on both projects.
  - Verify with one QA email each. This needs owner approval.
- **Pending authorization:**
  - AUTH-B: sign out QA account B's sessions. This is not an account deletion.
  - Optional: a third QA account for an empty-account device run.
- **Follow-ups (non-blocking):** QA-N1 to QA-N4 in `KNOWN_ISSUES.md`.
- **Done:** O-10, E-2, F-7 fix, the two restore tests, process death during a restore (emulator), and the release key
  guard (failure cases executed).
6. **F-2:** fix the never-synced sign-out copy, and consider disconnecting without a wipe when nothing was ever synced
   (owner decision).
7. **F-3:** fix the guided-setup welcome copy when sync is on.
- *Plan:* delete only same-name duplicate categories; upload uniquely named ones; probe all tables; record
  `syncedUserId` only after the first pull succeeds and re-run the pull-first branch on retry.
- *Validation:* unit tests plus O-04/O-05/O-11 on device.

**P1-3 Two-device sync program** (audit §17, S-01…S-20)
- Execute the matrix on test with two devices; record evidence; file defects.
- *Depends on:* P0-1, B1, B5.
- *Acceptance:* the global acceptance list in §17.

**P1-4 Onboarding QA program** (audit §18, O-01…O-13). Execute after P0-4 and P1-2.

**P1-5 Transactional pull and conflict predictability** (N-05, S-05, S-06, S-11)
- Wrap applies in Room transactions and re-read inside them; correct the docstring.
- Owner decisions on whole-row LWW and on edit-resurrects-delete (D2, D3).
- *Validation:* failure-injection tests; S-11 and S-12 on device.

**P1-6 Release readiness**
- Reconcile production migration version drift (N-07) with a CI migration-list check on test.
- Release smoke test on production with a dedicated account (owner-approved, after P0-1).
- Make CI fail on screenshot-tour failures (N-18).
- One PR at a time with a green `main`; resolve PR #10.

**P1-7 Accessibility at large text and narrow width** (N-25)
- Adaptive dock; amounts never truncate; `fontScale` breakpoints; TalkBack pass on the composer, review, budget
  and sign-out dialogs.

**P1-8 Budget conflicts when categories are combined** (from the P0-2 acceptance review, finding C-1). **Not started.**

*Problem (Code-only).* Sync folds same-name categories (`SyncRepository.mergeDuplicateCategories`, `CategoryDedup`).
- **Which copy survives means nothing to the user.** The keeper is deterministic, the copy with the smaller random
  `client_uuid` (`CategoryDedup.kt:23`), but the choice can tombstone the account's own category and budget on every
  device (device QA: the phone's Rent 500 replaced the account's Rent 900).
- **No month on budget rows.** Budget rows have no month (`BudgetDao`), so a replaced limit applies retroactively to
  every month's reports.
- **Other devices follow.** They pull the fold: references move to the keeper and the losing budget row is deleted.
  This converged correctly on device (QA O-15), but the losing value is gone everywhere.
- **One budget amount is discarded.** When both copies have a budget in the same currency, the copy's budget row is
  deleted and the keeper's amount wins unless it is 0 (`CategoryDedup.mergedLimit`). Which amount survives is therefore
  also arbitrary, and the other is lost without a trace.
- **It happens in every merge,** not only onboarding: two phones that set up the same category, a backup merge, or the
  P0-2 Settings combine flow. Since P0-2.1 the combine dialog says one amount may be kept; the behaviour is unchanged.

*Owner requirement.* Lucento must not silently discard financial information. Keep both original values where
possible. Any automatic resolution must be deterministic, documented in `docs/CALCULATIONS.md` and the same on every
device.

*Options to decide (design before code).*

| Option | Deterministic | Discards a value | Notes |
|---|---|---|---|
| **A. Keep the larger amount** | Yes | Yes (the smaller one) | Simple. A conservative plan can't be loosened unnoticed, but a deliberate cut is undone. |
| **B. Keep the latest edit** (`updated_at`, ties by id) | Yes, given the server's clock clamp | Yes (the older one) | Matches the user's last intent on one device. Clock skew is bounded by the server's 5-minute clamp. |
| **C. Ask the user** | — | No | A "budgets to review" item listing both amounts. Most honest, adds a decision; needs a place in the UI that survives sync. |
| **D. Keep both, flag the conflict** | Yes | No | Keep the keeper's amount *and* record the other (both values plus source) so nothing is lost. Show a review prompt; resolve to A/B only on user action. |

*Recommendation for the design review.* D, with B as the displayed default until the user reviews. It is
deterministic, loses nothing, and the decision is explicit.

*Implications to cover.*
- **Keeper choice:** already deterministic. Decide whether it should be *meaningful* instead (for example the account's
  existing row, or the oldest `created_at`), still identical on every device.
- **History:** transactions and schedules move to the keeper (no amounts change). Because budget rows carry no month,
  any replaced limit rewrites past months' budget-vs-spent views; decide whether limits need effective dates.
- **Storage:** recording the second value likely needs a new local column or table plus a Supabase migration (new
  file, RLS, grants). Room version bump with an explicit `Migration`, a migration test, and schema JSON.
- **Cross-device:** every device must reach the same keeper and amount from the same inputs, including when the
  conflict is seen by devices in a different order.
- **Backups:** `BackupEngine` merge uses the same fold; keep it consistent.

*Tests (to write with the change).*
- Two devices, same category name, different budgets in the same currency, in both orders: identical result.
- One budget 0, the other set; budgets in different currencies (no conflict).
- An archived category on one side.
- A three-way collision.
- Repeated syncs (idempotent).
- Backup merge with a conflicting budget.
- With option D: the record of the second value and the review state survive sync and reinstall.

## P2 — Product experience (cognitive load first)

- **P2-1 Home: "Am I on track?"** One pace visual and one sentence; available cash vs. net worth; recent activity
  and upcoming schedules; a skeleton loading state (N-11); remove the sparse Quick links card (N-23).
- **P2-2 Sync transparency and recovery UX.** One status chip (Synced · Syncing · Needs attention) with a details
  sheet; a first-sync "downloading / reconciling" state; a persistent failure state with Try again; no repeated
  toasts.
- **P2-3 Budget comprehension.** Planned / spent-in-plan / outside-plan (after decision D1).
- **P2-4 Settings information architecture.** Account & sync, Your data, Privacy and Modules as separate pages; one
  theme control; privacy copy said once (N-22).
- **P2-5 Module rediscovery.** "More" entries with an inline Turn on (N-21).
- **P2-6 Multi-device conflict experience.** Surface "changed on another device" only when a user edit was
  overridden.
- **P2-7 Strings to resources with plurals** (N-10).
- **P2-8 Scoped SQL queries and paging** instead of whole-table flows (N-12); measure release startup and add a
  baseline profile (N-13).
- **P2-9 Server CHECK constraints** (test first) and validation of pulled rows (N-06).
- **P2-10 Security options.** Leaked-password protection (N-08, plan-dependent); optional app lock and recents
  privacy (N-17).
- **P2-11** pgTAP RLS suite and cross-user REST probe on test.

## P3 — Refinement

Visual polish and a unified hero family; motion refinement; undo after save; copy fixes (N-15); usage-event
validation and rate limiting (N-09); toolchain alignment (N-19); dependency upgrades; remove the main-thread wipe
`runBlocking` (N-26); unused-index review.

## Owner decisions

| ID | Decision |
|---|---|
| D1 | Budget "Spent": all expenses vs. budgeted categories only (N-14) |
| D2 | Conflict policy: whole-row LWW (current) or field-level merge for concurrent edits (S-05) |
| D3 | Should a newer edit resurrect a deleted record (current design), or should deletes always win (S-06)? |
| D4 | Wallet dedup on first sign-in: never, prompt, or auto-match by name and type |
| D5 | Accept R10-01 / offline-revoke risk for beta, or require P0-2 first (recommended: require it) |
| D6 | Release application ID (`com.lucento.app` in code vs `app.lucento` in old docs) |
| D7 | Supabase plan for leaked-password protection and session controls |
| D8 | App lock as an optional feature |
| D9 | Fate of PR #10 (restore original onboarding) before onboarding redesign |
| D10 | Cleanup of qa1/qa2 residue and merged branches |

## Execution discipline

Main session implements. Architect and QA agents review P0-2, P0-4, P1-2 and P1-5 before merging. One coherent
change per branch, tests with every change, exact reporting of what ran, no production writes without approval.
