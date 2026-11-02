# P0-2 — Device QA results

**Date:** 2026-11-01 · **Build:** debug `0.5.0-debug` from the working tree (P0-2 + P0-2.1), installed fresh.
**Backend:** `lucento-test` (`ogenvdopfgocjdkcrsny`) only. Production was not touched.
**Follow-up run (same day, after account B was confirmed):** O-10 and E-2, shots B-30…B-51.
**Final-acceptance run (same day, build with the F-7 fix):** F-7 device check and process death during a restore,
shots B-60…B-76, §10. Summary in `P0_2_FINAL_ACCEPTANCE_REPORT.md`.
**Evidence:** `artifacts/p0-2-device-qa/`. That folder holds `screenshots/` (89 files, prefix A = device A, B = device
B), `scenario-log.txt` (the per-scenario record written during the run) and `00-baseline-existing-qa.txt`. Pulled
database copies and QA passwords stayed in the session scratchpad and are not in the repo.

Every PASS below is backed by **local database counts and ID fingerprints** (pulled from the device with `run-as`)
**and server-side counts and ID fingerprints** (read-only SQL on `lucento-test`), not by the screen alone.

## 1. Environment

| Item | Value |
|---|---|
| Device A | `Lucento_P0_QA_NewUser`: new AVD, API 36.1 `google_apis_playstore` x86_64, hardware profile copied from the existing Medium Phone AVD |
| Device B | `Lucento_P0_QA_ReturningUser`: same configuration |
| Existing AVDs | Untouched. Medium Phone API 36.1 was stopped gracefully (`emu kill`) to free RAM, then restarted at the end; its data was not cleared. `Lucento_API28` was not started for testing, not changed, not uninstalled. |
| TEST badge | Visible on every app screen on both devices (accessibility label "Test environment build"). It is covered by modal bottom sheets and dialogs, which are separate windows. |
| Target | Debug build → `lucento-test` (Build-verified, see P0-2.1 report). Session key observed on device: `sb-ogenvdopfgocjdkcrsny-supabase-co-session`. |

**Emulator defect hit (not Lucento).** Android Emulator 37.2.12 with fresh API 36.1 images aborts with
`Assertion failed: !rcEnc->featureInfo()->hasReadColorBufferDma` whenever the guest reads a frame back:
- surfaceflinger region sampling for the gesture handle;
- the `screencap` tool;
- system_server's task-snapshot persister, which runs when an app is backgrounded or killed.

The existing AVD avoids it because it resumes from an older Quick Boot snapshot. The `-gpu` modes `auto`, `host`,
`swiftshader_indirect` and `guest`, and `-feature -GLDMA,-GLDMA2`, did not prevent it.

Workarounds used:
- **3-button navigation** removes region sampling.
- **Host-side screenshots** via `adb emu screenrecord screenshot` replace `screencap`.
- **Host GPU** keeps the CPU load sane; software rendering pushed the guest load to 25 and caused one input ANR, during
  which Lucento's main thread was verifiably idle in `epoll_wait`.

App resets (clear or uninstall) still restart the emulator system once; each was waited out and recorded.

## 2. QA accounts (no credentials recorded)

| Account | Role | Notes |
|---|---|---|
| A, `211a8bb2` (`…+lucento-qa-a@gmail.com`) | New user, then returning user | Created through the app's email sign-up on device A; you confirmed it from the inbox. |
| B, `ae2e7a97` (`…+lucento-qa-b@gmail.com`) | Account switch / isolation, then E-2 | Created through the test project's auth API. Confirmed at 12:25:51 UTC from a dashboard-sent magic link; the link's redirect landed on `localhost:3000` (finding F-8), but the server recorded the confirmation. |
| `fe0d6ae4`, `4972e508` | Pre-existing QA accounts | **Not used:** their passwords aren't available to me and weren't requested. Used as the "existing QA data intact" control. |

Passwords were generated at random into the session scratchpad, typed on the device from the file, and never printed.

## 3. Database snapshots (server, read-only SQL)

| Snapshot | wallets | categories | transactions | budgets | goals | schedules | deleted rows |
|---|---|---|---|---|---|---|---|
| A0, account A just created | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| After O-01 finish | 0 | 18 (12 chosen + 6 income seeds with deterministic `seed-…` ids) | 0 | 0 | 0 | 0 | 0 |
| **A1**, after seeding on device A | 3 | 19 | 4 (2 expenses + 2 transfer legs) | 2 | 1 | 1 | 0 |
| A1 rechecked after O-03, O-02, O-08, O-05/06, E-1, O-07 | identical counts and id-md5 every time | | | | | | 0 |
| **A2**, after the combine flow | 4 | 19 live / 3 deleted | 5 | 2 live / 2 deleted | 1 | 1 | 5 (folded copies) |

ID fingerprints for A1 (md5 of sorted live ids, first 8 characters): wallets `31c80571`, categories `a10808ec`,
transactions `c9d64d41`, budgets `9a719cf3`, goals `5d628b9e`, schedules `d0392eea`.

**Pre-existing QA data:** row count, last change and md5(id:revision) for all seven of their tables were identical
before and after the run.

## 4. Onboarding scenarios

| ID | Scenario | Starting condition → action | Expected | Actual | Result |
|---|---|---|---|---|---|
| O-01 | New install, new account | Device A fresh → normal path → create account A → confirm → sign in on the sync step → **double-tap** "Start using Lucento" | Check = empty, stays on setup; categories created once; sync on at finish | No sync before finish (server 0 rows); one completion; device = server = 18 categories, 0 duplicate names, 0 dirty | **PASS** |
| O-02 | Restore path | Device B cleared → "I already have an account" → sign in A | Download, then Home | Device fingerprint = A1; net worth $2,427.06 equals device A | **PASS** |
| O-03 | Returning user on the normal path | Device B fresh → normal path → sign in A on the sync step | "Welcome back" restore; picks not applied | "Welcome back … instead of starting over"; device = server = A1 in all 6 tables; picks not applied; custom category present; transfer whole | **PASS** |
| O-04 / O-14 | Local data, then sign-in (Settings combine) | Device B offline setup with local records → Settings sign-in A | Confirmation before upload; both kept; totals explained | See §5 | **PASS** (with finding F-2) |
| O-05 | Interrupted first sync | Device B, network throttled, airplane mode on during "Restoring your account" | Retry converges; not marked done | FAILED screen with corrected copy and the way out. Partial local copy (3 wallets, 0 transactions); claim set, first-pull marker **not** set, onboarding **not** complete; server = A1 | **PASS** |
| O-06 | Failed sync, then retry | Network restored → Try again | Same end state as a clean sync | Device = A1; 6 local seeds merged by deterministic id; marker set; onboarding complete; 0 dirty; server = A1 | **PASS** |
| O-07 | Repeated onboarding | Device B re-onboarded against A six times | Account counts unchanged | Server = A1 after every one | **PASS** |
| O-08 | Kill during onboarding | Force-stop at "Welcome back" | Reopens without duplicating; route recomputed | Killed with onboarding **not** complete; relaunch → Home with no input; onboarding complete; device = server = A1. **What this exercised:** a force-stop clears the saved onboarding state, so the relaunch took the *start-up account check* path, not the resume-from-RESTORING branch. The download had already finished. System process death during a download was not tested (the emulator bug makes backgrounding unreliable). | **PASS** (start-up re-check path) |
| O-09 | Reinstall and recover | Uninstall + reinstall, restore path | Everything returns | Device = A2 | **PASS** |
| O-10 | Switch accounts | Device B held A2 (bound, 0 unsynced) → Settings → Disconnect → sign in B from Settings | Wipe of A, then B; nothing of A reaches B | Prompt accurate for a synced phone (B-33). After disconnect: 0 wallets, transactions, budgets, goals and schedules; 6 fresh income seeds; binding keys and stored session gone; server `POST /auth/v1/logout` 204. Sign in B → "Synced — 6 sent, 0 received". Server B: 6 income categories, all `seed-<B uid>-…` ids, 0 other rows, 0 ids shared with A. Account A fingerprints unchanged. Device: 0 `QA-` names, bound to B. Finding F-7 | **PASS** |
| O-11 | Custom categories | `QA-Boat fund` (typed, unused) created on the account's first device → restore on B | Kept | Present on server and on every restored device. **Safe path only:** the risky case, a *second* phone's own typed, unused category surviving first sign-in into an existing account (`dropUnusedLocalCategories`), was **not** exercised on device; it is unit-tested. | **PASS** (safe path) |
| O-12 | Wallets, budgets, goals, schedules, transfers | Restore of A1 / A2 | All present once, same content | ID fingerprints equal (A1); for A2, **content hashes** (names, opening balances, budget limits, amounts, dates, wallet and category references) are identical on server, device A and device B (`artifacts/p0-2-device-qa/01-end-of-run-fingerprints.txt`); transfer has 2 legs, 1 amount; net worth $2,427.06 on both devices at A1 | **PASS** |
| O-15 | Cross-device convergence after the combine | Device A held A1 and never saw the combine → launch | Pulls the combine; references follow the folded categories | Device A content = server = device B in all 6 tables, including the replaced Rent budget and moved references; 0 dirty, 0 crashes | **PASS** |
| O-13 | Initial balance accuracy | Wallet balance typed `12,50` | — | Saved as **$1,250.00** (known N-02; the preview shows it before saving). P0-3 not started. | **FAIL (known)** |

**Additional scenario E (detection or initial-sync failure):**

| ID | Case | Result |
|---|---|---|
| E-1 | Restore failure, then "Use Lucento without this account" | **PASS.** FAILED screen (shot B-10); partial copy 3 wallets / 4 transactions / 25 categories (6 transient duplicate seed names); confirmation dialog with the planned copy (B-11); "Continue without it" removed only the partial copy, cleared binding and session, back on the sync step with "Not connected. Your account keeps everything…" (B-12); server = A1. |
| E-2 | Sync-step "couldn't check" state (probe Unavailable) | **PASS** (follow-up run). Method:<br>1. Sign in B (empty) on the sync step.<br>2. Force-stop, which drops the remembered "new" answer and keeps the session.<br>3. Turn on airplane mode, relaunch, walk the setup again and finish. `finish()` must re-check, and the check fails.<br>Result: "We couldn't reach your account to see what's already in it, so nothing has been set up or uploaded yet…" with Try again / Continue without syncing (B-48). Picks **not** applied, onboarding **not** complete, sync not on, no binding. Try again offline repeats the message; online it re-checks → Empty → finish. Device = server: 18 categories, 0 duplicates, id md5 `9c86b78a`.<br>**Note:** a first attempt (airplane on right after a successful empty check) finished offline instead. This is by design: `finish()` skips the re-check when the sign-in check already found this account empty (`OnboardingViewModel.kt:476`), and the first sync still pulls before uploading. |

## 5. Settings combine flow (O-14)

| Step | Expected | Actual | Result |
|---|---|---|---|
| Sign in A from Settings on a phone with local records | Dialog before any upload | Dialog with the P0-2.1 wording (B-15); server = A1 while it was open | PASS |
| "Don't connect" | Signed out, nothing uploaded | "Not connected. This phone's records stay here, and nothing was uploaded."; session key gone; local unchanged | PASS |
| Kill on the dialog, relaunch, **Sync now** | Asks again, no upload | Dialog re-asked; app shows "22 changes waiting to sync" (B-18) = 1 wallet + 1 transaction + 2 budgets + 18 categories. My DB helper's "dirty 20" counts wallets, categories and transactions only, so there's no discrepancy. Nothing uploaded. | PASS (H2 fix) |
| Kill on the dialog, relaunch, **Disconnect → Sync first** | Asks again, no upload | Dialog re-asked; nothing uploaded (B-20) | PASS |
| **Add to account** | Both kept; categories combined; one budget amount kept | "9 sent, 30 received". Server: 4 wallets, 5 transactions, 19 live categories (3 folded), budgets Groceries=300 (account's) and **Rent=500 (phone's replaced 900)** | PASS; behaviour exactly as the dialog discloses; arbitrary keeper = P1-8 |
| Repeat **Sync now** | Idempotent | "0 sent, 0 received", identical counts | PASS |

## 6. Acceptance criteria

| # | Criterion | Evidence | Status |
|---|---|---|---|
| 1 | New users initialize correctly | O-01 | **Met** |
| 2 | Returning users reliably detected | O-03, O-08 (normal path), O-02, O-09 (restore path). On device, only a wallet-holding account was detected; the P0-2.1 shapes (transaction-, schedule-, budget-only) are unit-tested | **Met** (P0-2.1 shapes unit-only) |
| 3 | No duplication by onboarding | O-02, O-03, O-05–O-09: server unchanged, ID fingerprints equal; content hashes equal for A2 | **Met** |
| 4 | Custom categories preserved | O-11, safe path; the risky local-category case is unit-only | **Partly met on device** |
| 5 | Failed detection doesn't create a new account | E-2: the failed check stopped on the sync step with nothing applied, uploaded or bound | **Met** (onboarding path; the Settings path is unit-only) |
| 6 | Failed sync doesn't complete onboarding | O-05 (flag absent while FAILED) | **Met** |
| 7 | Interrupted initialization safely retried | O-05 → O-06, O-08 | **Met** |
| 8 | Data not silently discarded | Combine keeps all financial records; a colliding budget amount *is* replaced, disclosed in the Settings dialog. Two-phone merges and backup merges fold budgets the same way **without** a disclosure | **Met for the combine flow; P1-8 open** |
| 9 | Combining requires explicit confirmation | O-14, including both bypass paths (Sync now, Sync first). The background worker and launch-sync bypass weren't exercised on device (sync stays off until consent); code gates them on `syncPreferences.isEnabled()` | **Met** (worker path code-only) |
| 10 | User told about duplicate totals | Dialog text | **Met** |
| 11 | Accounts can't access each other's records | O-10: after switching A → B on one phone, B holds only its own rows and A is unchanged; probe scoped by `user_id` and RLS (unit tests); pre-existing accounts' fingerprints unchanged (re-checked after the follow-up). A direct cross-user REST probe is still P2-11 | **Met** (switch path) |
| 12 | Existing QA data intact | Fingerprints identical before and after | **Met** |

## 7. Findings from device QA

| ID | Finding | Severity | Status |
|---|---|---|---|
| F-1 | `12,50` saved as $1,250.00 (N-02) | P0 (known) | P0-3, not started |
| F-2 | On a never-synced phone, the sign-out prompt says it "removes the account's wallets…", but these are the phone's own never-uploaded records. "Sign out anyway" deletes them; the unsynced count is shown. Copy inaccurate; consider disconnecting without a wipe when nothing was ever synced. | P2 | Open |
| F-3 | The guided-setup welcome says "This stays only on this phone unless cloud sync is on" while sync is on | P3 copy | Open |
| F-4 | The composer couldn't match "savings"/"cash" to wallets named `QA-Savings`/`QA-Cash` and asked the user to pick; correct and safe | — | Note |
| F-5 | The TEST badge is hidden behind modal sheets and dialogs | P3 | Note |
| F-6 | While a restore is partial, the phone briefly holds duplicate income seed names (local + pulled); they merge on retry and are never visible | — | Note |
| F-7 | After a sign-out wipe, onboarding stays complete. A newly signed-in empty account starts with **0 spending categories**: the mandatory category step is skipped. Home shows the "Add your first wallet" empty state, so the app is usable but the budget has nothing to plan with | P2 | **Fixed** (§10; returning-account path device-verified, empty-account path unit-tested) |
| F-8 | `lucento-test` auth emails (confirmation, magic link) redirect to `localhost:3000`, which fails to load; the confirmation itself succeeds. The access token lands in the URL fragment in the user's browser. Check production's Site URL and redirect allow-list before release (read-only check; owner approval to change) | P2 (test); P1 if production matches | Open |

## 8. QA review (read-only agent) and follow-up

**Verdict:** accept P0-2 **with conditions**. The detection rule, error mapping and combine wording were judged correct.

**Findings and what was done:**

| Finding | Action |
|---|---|
| 20 vs 22 pending count | Reconciled above: the helper excluded budgets |
| O-08 tested the start-up re-check path, not resume-from-RESTORING | Relabelled |
| O-11 tested the safe path only | Relabelled; criterion 4 downgraded |
| Criterion 5 had no device evidence (E-1 is a restore failure) | Downgraded to "not verified on device" |
| O-12 compared ids only | Added content hashes across server and both devices |
| End-of-run fingerprints only asserted | Recorded in `01-end-of-run-fingerprints.txt` |
| Cross-device convergence after the combine untested | Run as O-15 → PASS |
| "Any RLS error is Unavailable" overstated | Corrected in the P0-2.1 report: an RLS *mismatch* returns zero rows (Empty), not an error; only refusals and errors map to Unavailable. Ownership comes from the authenticated session, so a mismatch would need a broken policy. |
| P1-8 inaccuracies | Corrected in NEXT_TASKS: the keeper is deterministic (smallest `client_uuid`) but meaningless to the user; budget rows have no month, so a replaced limit applies to every month; effect on other devices holding the losing category's references |

**Closed in the follow-up run:** E-2 (PASS) and O-10 (PASS).

**Closed in the final-acceptance run:** the two restore JVM tests (transaction-only and schedule-only accounts), F-7, and
process death during a restore (foreground kill and system restart; §10).

**Still open (conditions)** — see `P0_2_FINAL_ACCEPTANCE_REPORT.md` §10:
- The Settings "couldn't check" case. The check runs immediately after sign-in, so it can't be separated on device
  without a fault switch; it is unit-tested (`firstSyncDecision`: Unavailable → disconnect, nothing uploaded).
- Empty account after a switch, on device (needs a third QA account).
- The saved-state restore resume, on a physical device.
- Planned extras not run: O-05 "Sync now ×3", O-08 swipe-away, O-04 on an upgraded pre-P0-2 install (legacy marker
  path, unit-tested).
- Screenshot B-02 and others show the QA email alias; redact before committing artifacts.

## 10. Final-acceptance run: F-7 fix and process death during a restore

Debug build with the F-7 fix, installed on device B as an **upgrade**. Network slowed with `emu network speed gsm` and
`delay gprs` for the process-death runs. Server fingerprints are `md5(id:revision)`, so any upload would change them.

| ID | Scenario | Result | Evidence |
|---|---|---|---|
| U-1 | Upgrade from the pre-fix build | The existing install opened the main app (guided setup step), not onboarding | **PASS** · B-60 |
| F7-D1 | Account A restored in this app session, then Settings → Disconnect, then the next account signs in without restarting the app | New dialog copy (B-62). Same process (pid 23236) switched to a **fresh** onboarding step 1 (B-63), not the previous run's restore state. Local: 0 of A's rows, 6 income seeds, wipe marker cleared, setup flags reset, `setup_generation` written. Server `POST /auth/v1/logout` 204 (13:43:13Z), so the revoke ran although the screen switched mid-call | **PASS** |
| F7-D2 | …normal path → sign in B (has data) on the sync step | Restored straight to Home (B-65). Local = B's 18 categories (fp `9c86b78a`), 0 `QA-` names, every seed id keyed to B, `guide_step` absent (restore route; a new setup sets it), 0 dirty. Server: A and B unchanged | **PASS** |
| F7-D3 | …sign in an **empty** B after a switch | Not run: needs a third QA account (no unnecessary accounts). Covered by the F-7 unit test; NEW_SETUP itself is device-verified by O-01 and E-2 | **NOT VERIFIED on device** |
| PD-1 | SIGKILL the moment "Restoring" shows (before any row) | Relaunch: start-up check → "Welcome back" (B-72) → device = account A in all 6 tables, 0 dirty, 0 duplicates | **PASS** |
| PD-2 | SIGKILL 8 s into the download, with partial data (4 wallets, 2 budgets, 25 categories incl. 6 transient duplicate seeds, 0 transactions; bound, first pull not done, onboarding incomplete) | Relaunch: "Welcome back / Restoring" (B-73) → Home (B-74). Device = account A exactly; duplicates merged; server A and B unchanged, so nothing was uploaded or duplicated | **PASS** |
| PD-3 | Backgrounded mid-download, then `am kill` | The emulator bug also restarted `system_server`, so this was a system restart, not a saved-state process death. Same partial state as PD-2, and the same clean recovery (B-75, B-76) | **PASS** (as a system restart) |
| PD-4 | Saved-state resume (`OnboardingViewModel` init resuming a `RESTORING` stage after Android restores a killed background app) | Unreachable on these emulators: backgrounding restarts the system process (ENV-1). No physical device available; owner accepted the deferral | **DEFERRED / NOT VERIFIED ON PHYSICAL DEVICE** |

**Method limitations.** All device runs were on API 36.1 emulators (no physical device available). Foreground SIGKILL
reproduces a crash or low-memory kill without saved state. It does not reproduce Android restoring saved state.

Pre-existing QA accounts `fe0d6ae4` and `4972e508`: fingerprints still match the original baseline after this run.

## 9. Acceptance recommendation

**Superseded by `P0_2_FINAL_ACCEPTANCE_REPORT.md`:** P0-2 — CLOSED WITH DOCUMENTED VALIDATION LIMITATIONS (recommended,
pending owner review). The earlier recommendation follows for the record.

**Accept P0-2 with conditions.** The core guarantees were verified on device with database evidence:
- a returning user is detected and restored, not set up again;
- nothing is duplicated;
- a failed restore never completes onboarding and retries cleanly;
- combining needs consent, which can't be bypassed;
- a failed account check sets nothing up (E-2);
- switching accounts leaves nothing of the first account on the phone or in the second account (O-10);
- devices converge;
- existing QA data is untouched.

Before release, close the conditions above. O-13 / N-02 (amount parsing) is a separate release blocker under P0-3.
