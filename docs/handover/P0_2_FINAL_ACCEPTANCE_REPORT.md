# P0-2 — Final acceptance report

**Date:** 2026-11-01 · **Branch:** `feature/ai-development-workflow`. Uncommitted: nothing committed, pushed or merged;
recovery stash intact.
**Environment:** `lucento-test` only. Production was neither read nor changed.
**Scope:** P0-2 safe onboarding, cloud-data detection, duplicate prevention and account-switch isolation, including
the P0-2.1 corrections. P0-3 (amount parsing) was not started.

**Evidence:**
- Device QA: `P0_2_DEVICE_QA_RESULTS.md` §4, §5 and §10.
- Raw log: `artifacts/p0-2-device-qa/scenario-log.txt` (103 screenshots).
- Corrections: `P0_2_1_ACCEPTANCE_CORRECTIONS.md`.

**Status labels used:** PASS · PASS WITH DOCUMENTED LIMITATION · DEFERRED · NOT VERIFIED · BLOCKED.

## 1. Executive summary

P0-2's implementation is complete. The independent QA review found **no blocking defect**.

Verified on device with database evidence:
- New and returning users.
- Failed account checks.
- Consent before combining data.
- Isolation when switching accounts on one phone.
- Recovery from a restore killed half-way.

Automated tests cover the rest. The owner has accepted three validation limitations (§7, §8 and §4): no
physical-device background-restore test, Settings account-check failure covered by automated tests only, and the
empty-account switch covered by automated tests only.

**Recommendation: P0-2 — CLOSED WITH DOCUMENTED VALIDATION LIMITATIONS.**

Two items stay outside P0-2:
- **F-8**, the auth email redirect, is a separate **release blocker**.
- Account B's session cleanup is **pending your authorization**.

## 2. Completed implementation

| Area | What exists now |
|---|---|
| Account check (P0-2, P0-2.1) | During onboarding the signed-in account is checked for live wallets (archived included), active spending categories, transactions, schedules and budgets.<br>Any error means "couldn't check", never "new". The restore applies the same rule. |
| Onboarding routing | **Has data** → restore ("Welcome back").<br>**Empty** → new setup.<br>**Couldn't check** → Try again: nothing is set up, uploaded or bound. Onboarding stays unfinished. |
| No premature sync | Launch sync, upload-after-write and the background worker all wait for onboarding to finish (`backgroundSyncAllowed`). |
| First sign-in data safety | Pull before upload, with a first-pull marker. Typed category names are kept; account categories are never removed by onboarding picks. |
| Settings combine | Asks before joining a phone with records to an account with data, and the question can't be bypassed. The dialog discloses the combine effects. |
| **F-7** next account after sign-out | The wipe resets the account's setup, so the next account goes through onboarding (§4). |
| Sign-out wipe safety | Not cancellable, with a marker resumed on launch. The server revoke still runs if the screen closes. |
| Fresh onboarding state | Activity-scoped ViewModels are keyed by a setup generation. The generation changes only when a finished setup is wiped, so a wipe during onboarding never starts a second restore beside the first. |
| Release build guard | The build fails if a Supabase key is a secret or service-role key. Debug can't point at production. |

## 3. O-10 and E-2

| ID | Scenario | Status |
|---|---|---|
| **O-10** | Phone signed in to A and synced → Disconnect → sign in B: A's records, binding and session removed; server logout 204; B received only its own 6 income categories; A unchanged | **PASS** |
| **E-2** | Sign in → force-stop → offline → finish: "We couldn't reach your account…" with Try again; nothing set up, uploaded or bound; onboarding unfinished. Offline retry repeats the message; online it continues; device = server, no duplicates | **PASS** |

When the sign-in check has just found the account empty, finishing doesn't check again. This is intended: the first
sync still pulls before it uploads, and no extra checks were added.

## 4. F-7 — implementation and evidence

**Root cause.**
- Onboarding completion was one device-wide preference.
- The sign-out wipe returned the database to fresh-install state but left that flag, the category-setup flag and the
  guided-setup step in place.
- So the next account skipped onboarding: an empty account had no spending categories, and Settings synced a
  returning account directly.

**Fix** (Architect review before; QA review after). The wipe's last step resets the account's setup in one
preference edit (`UserPreferencesRepository.resetAccountSetup`):
- **Cleared:** onboarding and category setup become not done; guided-setup step, default wallet, display name,
  acknowledged budget month and Smarter reading.
- **Kept:** theme, currency, modules, consent and seen notices.

Lucento then returns to onboarding, which checks the next account. After a wipe, Settings doesn't sync
(`syncAfterAccountWipe`). Dialog copy says setup starts again.

**Evidence.**

| Path | Device | Automated | Status |
|---|---|---|---|
| Returning account after a switch | **F7-D1:** A restored and then signed out in the same app process → fresh onboarding, not the stale finished screen; server logout 204.<br>**F7-D2:** B (has data) restored with only its own records; A and B unchanged on the server.<br>Re-checked on the final build (B-77). | `OnboardingDataSafetyTest`: returning B → RESTORE, B's records only, nothing uploaded into B | **PASS** |
| Empty account after a switch | Not run: needs a third QA account, which is not authorized | `OnboardingDataSafetyTest`: empty B → NEW_SETUP, only B's categories; `LocalLedgerResetTest`: the real wipe resets setup | **PASS WITH DOCUMENTED LIMITATION** (automated only) |
| Failed check after a switch | Same safeguard as E-2 (device PASS) | `OnboardingDataSafetyTest`: RETRY, nothing claimed or uploaded | **PASS** |
| Wipe safety and onboarding state | Upgrade install keeps an onboarded user in the main app (U-1) | `LocalLedgerResetTest` (3), `AccountSetupResetTest` (4), `WipeOrderTest` cancellation test | **PASS** |

**Is another device test essential?** No. The empty-account path shares every component with two device-verified
paths:
- the wipe and fresh onboarding (F7-D1);
- NEW_SETUP for an empty account (O-01, E-2).

Only their combination is unexercised on device, and the automated test drives that combination through the real sync
engine. To close even that gap, authorize one more QA account on `lucento-test` and I'll run it.

## 5. Restore unit tests

These are the two cases named in the handover: the restore side of a **transaction-only** and a **schedule-only**
account (`OnboardingDataSafetyTest`).

Each test checks that:
- the account check says "has data";
- the restore's own rule passes after a full download;
- the record arrives exactly once with the right amount;
- nothing is uploaded back (server id:revision unchanged);
- the deleted parent wallet isn't revived.

**Status: PASS.**

## 6. Process death during a restore (emulator)

The network was slowed to GSM speed with GPRS delay. After each run, account A's server fingerprints (id and revision
of every row) were compared.

| Run | Kill | State at kill | After relaunch | Status |
|---|---|---|---|---|
| PD-1 | SIGKILL as "Restoring" appeared | Session stored, no binding, 0 rows | Start-up check → "Welcome back" → device = account A in all 6 tables, 0 duplicates | **PASS** |
| PD-2 | SIGKILL 8 s into the download | 4 wallets, 2 budgets, 25 categories (6 transient duplicates), 0 transactions; bound, first pull not done, onboarding unfinished | "Welcome back" → device = account A exactly, duplicates merged; server unchanged (no upload) | **PASS** |
| PD-3 | Backgrounded, then killed | Same as PD-2 (the emulator bug also restarted the system process) | Same clean recovery; server unchanged | **PASS** |

**Overall: PASS WITH DOCUMENTED LIMITATION.** These were emulator runs. A foreground kill and a system restart don't
include Android restoring the app's saved screen state (§7).

## 7. Physical-device background restore — DEFERRED / NOT VERIFIED ON PHYSICAL DEVICE

**What it is.** Android kills Lucento in the background during a restore, then restores its saved screen state when the
user returns. `OnboardingViewModel` then resumes the restore from its saved `RESTORING` stage.

**Why it isn't verified.**
- On these API 36 emulators, backgrounding an app restarts the system process (emulator GPU bug ENV-1), so the
  saved-state branch can't be reached.
- No physical device is available.
- As instructed, no further emulator workaround or fault switch was attempted.

**Risk assessment (QA review).** The branch runs only in a fresh process, where nothing else syncs:
- Background sync waits for onboarding.
- The resumed restore re-checks the account boundary before syncing.
- **If killed mid-pull,** the first-pull marker is still unset, so the retry pulls first again, and rows are upserted
  by id. PD-2 shows this recovery.
- **Worst case** is a spinner, bounded by the 30 s request timeout, or the failed or sign-in screen with Try again.
  QA found no path to data loss or duplicate upload.

**Status: DEFERRED.** This is an owner-accepted limitation. Run it on the first available physical device.

## 8. Settings account-check failure — automated coverage accepted; device test not performed

**What it is.** In Settings, a phone that has never synced signs in, and the account check fails. Expected: nothing is
uploaded, the new session is dropped, and the user sees "Couldn't reach your account…".

**Coverage.**
- `firstSyncDecision`: a failed check means disconnect, for every local state.
- "A failing account check during the first sync claims nothing and uploads nothing" (an injected failing remote).

`SettingsViewModel` itself isn't JVM-testable; its short glue was code-reviewed by QA. No debug fault switch was added,
and none exists in release.

**Status: NOT VERIFIED on device.** Automated coverage is accepted by the owner for this decision.

## 9. F-8 — authentication email redirect (separate release blocker)

| Item | Finding |
|---|---|
| `lucento-test` | Every confirmation and magic-link email redirects to `http://localhost:3000`, which is a dead page; the confirmation itself succeeds. Lucento passes no redirect, so the project's Site URL default applies (edge logs, including an in-app sign-up). |
| App and repo | No hardcoded localhost, no `emailRedirectTo`, no auth deep link, no `supabase/config.toml`. |
| Production page | `https://www.lucentoapp.com/auth/confirmed` answers 200 and tells the user to sign in on the phone. Its script reads only error fields and clears the token from the address bar (`history.replaceState`). |
| Production Supabase URL Configuration | **NOT VERIFIED.** The connector only has access to the test organisation. It is not assumed correct. |

**Release prerequisite.** These need your action and approval; nothing was changed.
1. In production, read Authentication → URL Configuration: the Site URL and Redirect URLs.
2. Set the Site URL to `https://www.lucentoapp.com/auth/confirmed` and add it to Redirect URLs, on production and on
   `lucento-test`.
3. Send one confirmation to a QA address on each project and confirm it lands on that page.

Android deep links are unaffected: Lucento has none, and Google sign-in uses no redirect.

**Status: BLOCKED** (pending owner verification and approval). This is outside P0-2's implementation.

## 10. Account B session cleanup

**Current state.** QA account B (`ae2e7a97`, `lucento-test`) has **5 active sessions**:
- the browser session created by the magic link, whose token appeared in a screenshot and which still has a live
  refresh token;
- four emulator sign-ins orphaned by QA app resets. None are in use; the emulator is signed out.

**Proposed action.** Sign out all of B's sessions: Dashboard → Authentication → Users → B → sign out, or
`auth.admin.signOut(..., 'global')`. This does not delete the account or touch its data.

**Status: BLOCKED — PENDING USER AUTHORIZATION.** Nothing was revoked.

## 11. Current test and build results

All of these were re-run for this report with `--rerun-tasks` (nothing reused from earlier runs).

| Check | Result |
|---|---|
| `testDebugUnitTest` | **634 passed, 0 failed, 0 errors, 0 skipped.** Same count as the previous report. The 14 new tests since P0-2.1 (620): 2 restore, 4 F-7, 3 `LocalLedgerResetTest`, 4 `AccountSetupResetTest`, 1 `WipeOrderTest`. |
| `lintDebug` | 0 errors, 9 warnings (pre-existing count) |
| `assembleDebug`, `assembleRelease` (`-x uploadCrashlyticsMappingFileRelease`) | Both succeed |
| Debug APK | Dex references `lucento-test` only |
| Release APK | Dex references production only |
| Secrets in APKs | No `sb_secret_`, `service_role` or JWT-like strings in either |
| Release key guard | **Executed for the first time:** a fake service-role JWT fails the build; a fake `sb_secret_` key fails; a publishable-style key passes. Run in an isolated scratch copy with fake keys; the real `local.properties` was untouched. |

## 12. Independent QA review

Three read-only reviews were run:
- An Architect review **before** the F-7 implementation found two blockers: a stale onboarding ViewModel, and a left-over wipe marker. Both are fixed.
- A QA review **after** the implementation found two issues: the test didn't cover production wiring, and a wipe inside onboarding swapped ViewModels. Both are fixed.
- A final QA review **for this closure** follows.

**Verdict of the closure review: close with documented validation limitations. No blocking findings.** It assessed:
- account isolation;
- wipe ordering and cancellation safety;
- the onboarding lifecycle;
- restore concurrency (all syncs serialised by `SyncFlight`; one restore per onboarding generation);
- failed checks;
- data preservation.

It judged the deferred physical-device test an acceptable risk (§7).

**Non-blocking notes** (recorded in `KNOWN_ISSUES.md`, not changed here, because they concern code from before P0-2):
- **QA-N1.** If the wipe marker can't be written, Settings has already cleared the session, and the sign-out then
  crashes from an unhandled `check`. The outcome is safe (signed out, data and binding intact), but it is a crash.
- **QA-N2.** If the synchronous launch resume of an interrupted wipe throws, its fallback could race a saved-state
  restore. That would leave a confusing return to onboarding, with no data loss. Very unlikely.
- **QA-N3.** A cancelled or failed restore leaves the sync switch on until `finish()` normalises it. Harmless: background
  sync is gated.
- **QA-N4.** `OnboardingViewModel` and `SettingsViewModel` have no ViewModel-level tests. This is a regression risk for
  future refactors.

## 13. Remaining risks and limitations

| Item | Status | Note |
|---|---|---|
| Physical-device background restore | **DEFERRED** | §7; owner-accepted |
| Settings account-check failure on device | **NOT VERIFIED** | §8; automated coverage accepted |
| Empty account after a switch on device | **PASS WITH DOCUMENTED LIMITATION** | §4; automated only; a third account needs authorization |
| Second phone's typed, unused category on first sign-in | **PASS WITH DOCUMENTED LIMITATION** | Automated only (safe path device-verified) |
| F-8 production redirect | **BLOCKED** | Release prerequisite, §9 |
| Account B sessions | **BLOCKED** | Pending authorization, §10 |
| QA-N1…N4 | Non-blocking | §12 |
| Old keyed ViewModels kept until the Activity ends | Non-blocking | Idle after a finished run |
| Usage-count install id shared across accounts on one phone | Non-blocking | Device-level, opt-in |
| P1-8 budget collision when categories combine | Known | Disclosed in the dialog; design pending |
| F-2 / F-3 copy | Known | Owner decision / P3 |
| N-02 amount parsing (`12,50` → $1,250.00) | Out of P0-2 scope | P0-3, not started |
| All device runs on API 36.1 emulators | Limitation | No physical device |

## 14. P0-2 criteria and recommendation

| # | Criterion | Status |
|---|---|---|
| 1 | New users initialise correctly | **PASS** |
| 2 | Returning users reliably detected | **PASS WITH DOCUMENTED LIMITATION** (transaction-, schedule- and budget-only shapes automated only) |
| 3 | No duplication by onboarding | **PASS** |
| 4 | Custom categories preserved | **PASS WITH DOCUMENTED LIMITATION** (second-phone case automated only) |
| 5 | Failed account check never creates or sets up an account | **PASS WITH DOCUMENTED LIMITATION** (onboarding device PASS; Settings automated only) |
| 6 | Failed sync doesn't complete onboarding | **PASS** |
| 7 | Interrupted initialisation safely retried | **PASS WITH DOCUMENTED LIMITATION** (emulator; physical saved-state deferred) |
| 8 | Financial data never silently discarded | **PASS WITH DOCUMENTED LIMITATION** (combine budget collision disclosed; P1-8) |
| 9 | Combining requires explicit confirmation | **PASS** |
| 10 | User told about duplicate totals | **PASS** |
| 11 | Accounts can't access each other's records (switch isolation) | **PASS** |
| 12 | Existing QA data intact | **PASS** |
| 13 | Next account after sign-out is restored or set up (F-7) | **PASS WITH DOCUMENTED LIMITATION** (empty account automated only) |
| 14 | Restore killed half-way recovers without uploads or duplicates | **PASS WITH DOCUMENTED LIMITATION** (emulator) |
| 15 | Physical-device background restore | **DEFERRED** |
| 16 | Settings account-check failure on device | **NOT VERIFIED** (automated accepted) |
| 17 | Debug → test, release → production, no secrets, key guard | **PASS** |
| — | F-8 auth redirect (release prerequisite, outside P0-2) | **BLOCKED** |
| — | Account B session cleanup | **BLOCKED — pending user authorization** |

**Recommendation: P0-2 — CLOSED WITH DOCUMENTED VALIDATION LIMITATIONS.** It is supported by:
- the implementation;
- 634 passing automated tests, including the F-7, restore and wipe-safety cases;
- device evidence for every path except the three accepted limitations;
- three independent reviews, the last with no blocking finding.

**Not closed by this:**
- F-8, which must be verified and fixed before any release.
- Account B session revocation, which waits for your authorization.

**Next workstream (not started):** P0-3, strict amount parsing. Waiting for your review of this recommendation.
