# P0-2 — Safe onboarding, existing cloud data detection and duplicate prevention

**Date:** 2026-10-31/11-01 · **Branch:** `feature/ai-development-workflow` (uncommitted, awaiting review)
**Environment:** debug builds point at `lucento-test` (P0-1). No production change of any kind was made.

**Evidence labels:**

| Label | Meaning |
|---|---|
| **Verified** | Run this session |
| **Code-only** | Read, not run |
| **Unverified** | Not checked |

## 1. Original failure mechanism

1. A returning user who signed in on the **normal setup path** instead of choosing "I already have an account" was
   never checked for cloud data. `connect()` turned sync on, and `finish()` applied the onboarding category picks and
   marked onboarding complete. Guided setup then created wallets and budgets with new ids, and the first background
   sync pushed them next to the account's own. The result was duplicate wallets, so net worth was counted twice.
2. That first sync's pull-first branch then **deleted the user's freshly picked categories** when they were unused
   (`dropUnusedLocalCategories`).
3. **The ownership claim was the only "first sync" signal.** `syncedUserId` was set before the first pull, so a first
   sync that failed half-way skipped the pull-first branch on retry and uploaded everything.
4. **Sync could start mid-onboarding** (found by the Architect review). The launch sync, upload-after-write and
   `SyncWorker` checked only "sync enabled", not "onboarding complete". After a force-stop mid-onboarding, the account
   could be pulled into the unfinished setup, and `finish()`'s `applySelection` then archived or deleted, with
   tombstones, the account's own unselected categories. Those deletions propagate to every device.
5. Account detection (`remoteHasData`) looked at three tables only and counted soft-deleted rows as data.

## 2. Root cause

The decision "is this account new?" was never made explicitly. It was implied by the ownership claim and by which
screen the user happened to choose, and onboarding completion wasn't gated on it.

## 3. Architecture changes (Code-only unless stated)

| Area | Change | Files |
|---|---|---|
| Detection | `SyncRemote.hasLiveRows(table, user, kind, activeOnly)`: `deleted_at is null`, limit 1; throws on error, never reports "empty". `SyncRepository.probeAccount()` → `NotConfigured / NotSignedIn / Empty / HasData / Unavailable`. It uses exactly the restore's rule (`RestorePlan.hasData`): a live wallet, or an active spending category. | `SyncRemote.kt`, `SyncRepository.kt` |
| First-sync state | New `initialPullDoneFor` marker, set only after the first pull and category tidy-up succeed. The claim (`syncedUserId`) stays before the pull, so a half-finished pull still triggers the account-boundary wipe if a different account signs in next. Legacy rule: no marker, owner = user and `lastSyncedAtMillis` set means done (the marker is then written); otherwise pull first. `clearBinding` clears the marker. | `SyncPreferences.kt`, `SyncRepository.kt` |
| Pull-first trigger | `remoteHasData` checks live rows in every synced table. | `SyncRepository.kt` |
| Category rule | Unused, never-synced local categories are dropped only if they **copy an active account category** (same kind and normalized name), or are a **Lucento preset/default** while the account has that kind (R6-14 and R9-03 kept). **A name the user typed is never dropped.** | `SyncRepository.kt` (`FirstSignInCategories`) |
| Onboarding | Routing via the pure `setupRoute()` (LOCAL / NEW_SETUP / RESTORE / RETRY). The account is checked on sign-in, at start-up when already signed in (scenario D), and as a **gate in `finish()`**. Sync is turned on only at finish or when a restore starts. `finish()` never completes on top of a partial restore without a session. "Welcome back" restore; Try again; a confirmed "Use Lucento without this account" way out; FAILED copy corrected. | `SetupRoute.kt` (new), `OnboardingViewModel.kt`, `OnboardingScreen.kt` |
| Category selection | `applySelection(keepAccountCategories = true)` from onboarding never removes a category that came from the account. The Budget screen's setup is unchanged. | `CategoryRepository.kt` |
| Background sync | `backgroundSyncAllowed(enabled, onboardingComplete)` gates the launch sync, upload-after-write and `SyncWorker`. The restore syncs directly. | `SyncOnLaunch.kt`, `SyncWorker.kt`, `LucentoApplication.kt` |
| Settings (scenario C) | Pure `firstSyncDecision()` → SYNC / ASK_MERGE / DISCONNECT, used by sign-in, **Sync now** and the sign-out dialog's **Sync first**, so a never-synced phone with its own records never uploads into an account with data without the user agreeing. A failed check means no upload and the new session is dropped. New MERGE dialog. | `FirstSyncDecision.kt` (new), `SettingsViewModel.kt`, `SettingsScreen.kt` |

No Room or Supabase schema change. No migration.

## 4. Initialization decision logic

| Situation | Outcome |
|---|---|
| A. Not signed in | Ordinary local setup (LOCAL). |
| A. Signed in, account empty (`Empty`) | New setup. Categories are created once (name-matched). Sync turns on at finish; the first sync pushes, with seeds getting deterministic per-account ids. |
| B. Signed in, account has data (`HasData`), normal path | Routed to the restore ("Welcome back"): download, then `completeRestore`. The onboarding picks are never applied. |
| B. Restore path | Unchanged pull-first restore. Onboarding completes only on success. |
| C. Settings, phone never synced, has records, account has data | MERGE prompt: both kept, nothing deleted. "Don't connect" signs out without touching anything. |
| D. Already signed in, onboarding unfinished (force-stop) | Quiet check at start-up routes to the restore, and `finish()` checks again. No background sync runs in the meantime. |
| D. Partial restore, session lost | `finish()` refuses to set up as new; the user signs in to finish, or leaves the account. |
| E. Check fails (`Unavailable`) | Never "new". Nothing is written, onboarding isn't completed, the sync step shows Try again, and "Continue without syncing" disconnects. When part of the account is already on the phone: "Use Lucento without this account", after a confirmation, removes that partial copy through the existing sign-out wipe. |
| User chose "Set up as new" after a restore found nothing | Remembered for that account (`KEY_NEW_FOR`), so it isn't offered again. |

## 5. Data-preservation strategy

- **Nothing is replaced on either side during onboarding.** A returning account is pulled (pulled rows match by
  `client_uuid`) and nothing from the onboarding session is applied on top of it.
- **Local and remote records are both kept** in scenario C, with the user's consent. Duplicates the user recorded in
  both places are not auto-merged, which is a deliberate choice for money; the dialog says so.
- **Category removal is narrowed** to unused copies and presets. User-typed names, referenced categories and
  account-backed categories are never removed by onboarding or first sign-in.
- **The only local removal added** is the confirmed "Use Lucento without this account". It removes a *partially
  downloaded copy* of cloud data during onboarding, before the user has data of their own on the phone, through the
  existing durable sign-out wipe. The account keeps everything. **I flag this for your review (§10).**

## 6. Idempotency strategy

Every write path already used stable ids, and P0-2 relies on them instead of names:
- Transactions: `client_uuid`. Transfers: group key `-out`/`-in`. Schedule occurrences: `occurrenceKey`.
- Seed categories: `seed-<uid>-<kind>-<slug>`.
- Pushes upsert on id; pulls match on `client_uuid`.

Repeating the check, the restore or the first sync converges: tested by running sync twice and after an injected
failure. Double-taps are guarded by the existing `connecting`, `restoring` and `finishing` flags.

## 7. Tests

New: `app/src/test/java/com/lucento/app/data/sync/OnboardingDataSafetyTest.kt` (26 tests). They assert record counts
and ids on the device and the fake server:

| Requirement | Test(s) |
|---|---|
| New account, empty cloud | `new account with an empty cloud is detected as empty` |
| Returning account with wallets / transactions / budgets / goals / schedules | `restoring a returning account preserves every record once, with the same ids` (counts, ids, whole transfer, custom category) |
| Existing custom categories preserved | `a category the user named is kept…`, `a local category named like an archived account category is kept`, `first sign-in category rule` |
| Existing cloud data without transactions detected | `an account with a wallet but no transactions is detected`; the rule boundary is in `the check uses the restore's own rule…` |
| Partial remote data detected | `partial remote data - a spending category alone - is detected` |
| Cloud detection network failure → never new | `a network failure during the check is never treated as a new account`, `a failing account check during the first sync claims nothing and uploads nothing` |
| Initial sync failure / interrupted initialization → safe retry | `an interrupted first sync pulls first again on retry and creates nothing extra` |
| Repeated initialization / repeated auth callback | sync run twice in the restore and interrupted tests; same ids and counts |
| Partial local data preserved | `local records on a phone that signs into an existing account are all kept alongside the account's` |
| Different user, no cross-account exposure | `a different account never receives this phone's records`, `detection is scoped to the signed-in account` |
| Onboarding never removes account categories | `onboarding's category choices never remove a category that came from the account` (+ the Budget-screen control test) |
| Settings consent / background gate | `settings asks before joining…`, `background sync never runs while onboarding is unfinished` |
| Upgrades | `a phone that already completed a sync before P0-2…`, `a phone whose first sync never finished before P0-2 pulls first` |

Changed: one R9-03 test in `SyncEngineTest` now uses real preset/default names ("Investments", "Gifts and
celebrations"). Its intent is unchanged; user-typed names are kept since P0-2.

**Results (Verified):** `./gradlew testDebugUnitTest` gives **611 tests, 0 failures, 0 errors** (585 existing + 26
new). `compileDebugAndroidTestKotlin` succeeds. Build and lint results are in §8.

**Not covered by automated tests:** `OnboardingViewModel` and `SettingsViewModel` themselves. They depend on
Context-bound classes and the project has no Robolectric. The decisions they make are extracted into pure functions
(`setupRoute`, `firstSyncDecision`, `backgroundSyncAllowed`) and those are tested.

## 8. Runtime verification

See §11 for the exact results of this session's build, lint and emulator run. **No sign-in flow was run on a
device.** No QA account credentials were available, and Supabase email sign-up requires email confirmation, so
scenarios A (signed in), B, C, D and E are unit-tested only.

## 9. Known limitations

- **Goal-only accounts count as empty.** This is the restore's own rule: they set up as new, and the goals arrive with
  the first sync without duplication.
- **Scenario C wallet duplicates are kept by design.** No name-based auto-merge for money; the user is told.
- **Pull is still not transactional** (N-05). A partial pull converges on retry, and the FAILED copy no longer claims
  "nothing changed".
- **Some progress survives only a system kill.** `KEY_NEW_FOR` and the restore stage live in `SavedStateHandle`.
  After a force-stop they are lost, but the start-up check and the finish gate recompute safely.
- **Broader sync risks are not addressed:** whole-row last-writer-wins (S-05) and edit-resurrects-delete (S-06). P0-2
  doesn't change them. Onboarding no longer feeds unconfirmed local edits into them, but they are **not fixed**.
- **The probe costs up to two small requests;** the first sync's pull-first check up to seven, stopping at the first
  hit.

## 10. Decisions for your review

1. **"Use Lucento without this account"** removes a partially downloaded copy of the account during onboarding,
   after a confirmation, using the existing sign-out wipe. The alternative was blocking until online, which the QA
   review rated as possibly permanent (H1).
2. **The narrowed first-sign-in category rule** keeps user-typed names, where R6-14 previously dropped them.
3. **Scenario C** asks before joining, and keeps both sides including duplicate wallets.

## 11. Session verification log

| Check | Result | Evidence |
|---|---|---|
| `./gradlew testDebugUnitTest` | **611 passed, 0 failed, 0 errors** (OnboardingDataSafetyTest 26/26) | Verified |
| `compileDebugAndroidTestKotlin` | Succeeds (`RestoreFlowTest`, `CloudSyncSectionTest` compile against the changed composables) | Verified (compile only; instrumented tests not run) |
| `assembleDebug` | Succeeds | Verified |
| `lintDebug` | **0 errors**, 9 warnings, 78 informational; none in a changed file | Verified |
| Emulator API 36 (Medium Phone 36.1), existing completed install, signed out | P0-2 build installed over it, cold-launched to Home with its data, profile sheet opened, **0 fatal exceptions** in logcat | Verified (smoke only) |
| Emulator API 28 | Not updated: its debug install is signed with another machine's key (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`); updating would mean uninstalling and losing that install's data | Not run |
| Onboarding UI on device (welcome-back restore, check states, leave-account dialog) | Not run: both emulators have completed-onboarding installs; reaching onboarding needs `pm clear` (deletes that local test data), which I didn't do without your approval | Unverified |
| Sign-in flows (Google or email) on `lucento-test`, MERGE dialog, two devices | Not run: no QA account credentials; email sign-up needs confirmation | Unverified |
| Production | Not touched (no SQL, no config, no deploy) | — |

**Specialist reviews (read-only):**
- **Architect review before implementation.** It found the mid-onboarding sync trigger, recommended the separate
  first-pull marker and its legacy rule, and advised against moving the claim. All three were adopted.
- **QA review after the first implementation.** Every finding was addressed:
  - H1 (stuck in onboarding): confirmed way out added.
  - H2 (Sync now bypassing consent): the decision is now used by sign-in, Sync now and Sync first.
  - M1 (finish on top of a partial restore without a session): blocked.
  - M2 (probe and RestorePlan disagree): one rule now.
  - L2 (welcome-back flag not reset): fixed.
  - L3 (archived categories): excluded.
  - Copy: raw error text removed from restore failures, merge dialog rewritten.
  - L1 (an account gaining data between check and finish) is accepted: the first sync's pull-first still merges
    safely.
  - L4 (request count) reduced to two probe requests.

**To run next (prerequisites):**
1. Your approval to `pm clear` the API 36 debug app, which deletes its local test data, or a fresh emulator.
2. A QA Google account on the API 36 emulator, or a confirmed QA email account on `lucento-test`.
3. Then run onboarding scenarios A–E and the Settings MERGE flow (audit §18, O-01…O-13).
