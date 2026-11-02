# Lucento Android — Initial Principal Engineer Audit

**Date:** 2026-10-31 · **Auditor:** Claude (principal engineer takeover, audit-only session)
**Checkout:** `feature/ai-development-workflow` at `7928c63` (= `origin/main`), with uncommitted local changes to
`CLAUDE.md`, `.gitignore`, `settings.gradle.kts` and untracked workflow folders. Recovery stash
`stash@{0}: backup-before-sync-origin-main-2026-10-30` is intact.
**Boundaries kept:** no source, schema, config or data changes; no commits, pushes, merges or deploys. The only file
written to the repo is this report. Emulator font scale and density were changed for testing and then restored.

> **Part II (same day) extends this audit** with the owner's added requirements: the Google Sign-In regression,
> Supabase and Vercel access, two-device sync QA, onboarding integrity, and cognitive load (§14–§21). The roadmap in
> §12 is superseded by `docs/handover/NEXT_TASKS.md`. The issue register is `docs/handover/KNOWN_ISSUES.md`.

## Evidence labels

| Label | Meaning |
|---|---|
| **Verified** | Observed this session: a command or test I ran, an emulator screen I saw, or a read-only backend API response. |
| **Verified (CI)** | Seen in GitHub Actions logs for `main`, not reproduced locally. |
| **Code-only** | Established by reading the current source. Not executed. |
| **Inferred** | Reasoned from code or documentation. Not directly observed. |
| **Unverified** | Not checked. |
| **Blocked** | Attempted, but denied by permissions or tooling. |

---

## 1. Executive assessment

Lucento's engineering foundation is better than most early-stage finance apps. Money is integer minor units with
exact arithmetic. Transfers are atomic and idempotent. Room migrations are explicit and tested, with no destructive
fallback. Every user table has RLS, and the 585-test JVM suite passes. The sync engine has been designed with
care: server clock, revisions, keyset paging and tombstones.

It is **not production-ready**. The highest risks are operational and lifecycle problems, not the core arithmetic:

1. **Local debug builds point at the production Supabase project** (Verified). The handover says QA ran against
   `lucento-test`. Today, any developer or QA sign-in from this machine writes to live data.
2. **Sign-out is not robust under interruption.** The work runs in a cancellable `viewModelScope`. Clearing the
   in-memory session waits behind the sync lock. The launch-time "finish the wipe" path never clears a session that
   was written back. The server revoke is in-memory, best-effort, and never retried (Code-only).
3. **Amount parsing accepts ambiguous separators.** `"12,50"` becomes $1,250.00 in a dot-decimal locale, and
   `"12.50"` becomes €1,250.00 in a comma-decimal locale (Verified by execution). The review screen reduces the
   risk, but nothing rejects the input.
4. **The sync pull is not transactional.** Its documentation claims more atomicity than the code provides
   (Code-only).
5. **Accessibility at large font sizes is broken.** At 2.0× font / 360dp, navigation labels overlap and money
   amounts are truncated (Verified on emulator).
6. **The release pipeline is unproven.** CI release builds carry no Supabase configuration. Production migration
   version numbers differ from the repo for the last three files, and no release build has been run against
   production.

On the product side, the app is coherent and calm, but it reads as a set of cards rather than a guided financial
picture. Budget semantics confuse on the Budget screen. Settings is one very long page with duplicated controls and
repeated privacy copy. Modules switched off during onboarding are hard to rediscover. Almost all UI text is
hardcoded in Kotlin, which blocks localization for a "global" app.

**Recommendation:** fix the P0/P1 lifecycle and environment issues (§12) before any beta expansion, then start a
structured UX pass. A rewrite is not warranted.

---

## 2. Current architecture (Verified by inspection)

- **Single module** `:app`, about 34.5k lines of Kotlin in `app/src/main`, 82 JVM test files and 4 instrumented test
  files. The layering follows CLAUDE.md: `core/`, `data/{local,preferences,repository,sync,backup,ai,usage}`,
  pure-Kotlin `domain/`, and `feature/<name>/` with a Screen and ViewModel each.
- **Dependency injection:** manual `AppContainer` in `LucentoApplication.kt` with lazy singletons. This is adequate
  at the current size, but the container mixes construction with process-level orchestration (three
  application-scoped coroutines in `onCreate`).
- **State:** ViewModels `combine` Room `Flow`s into `StateFlow` (`WhileSubscribed(5s)`). Most screens observe the
  **entire** transactions table and aggregate in Kotlin (§7, N-12).
- **Navigation:** Navigation Compose with type-safe `@Serializable` routes in `navigation/LucentoRoot.kt` (660
  lines). The dock is Home · Transactions · (+) · Budget · Wallets.
- **Data:** Room v9 with 9 entities. SQL triggers maintain the `dirty` flag and tombstones (`SyncTriggers.ensure` in
  `onOpen`). DataStore holds preferences. Synchronous `SharedPreferences.commit()` is used for the session, wipe
  marker and crash flag.
- **Sync:** `SyncRepository` (882 lines) does push → pull → dedup under `SyncFlight`, a process mutex plus a file
  lock. WorkManager provides a 30-min periodic job (`KEEP`), manual and launch sync (`KEEP`), and upload-after-write
  (`APPEND_OR_REPLACE`). The server enforces last-write-wins with revisions via triggers.
- **Auth:** supabase-kt 3.1.4 with a custom `SessionStore` (commit-durable). Sign-out uses a hand-rolled
  `POST /auth/v1/logout?scope=local`.
- **Stack versions:** AGP 8.7.3, Kotlin 2.0.21, KSP 2.0.21-1.0.28, Compose BOM 2024.12.01, Room 2.6.1, Gradle
  8.11.1. These are mutually compatible but about a year old. Upgrade them deliberately later (P3).

**Architecture verdict:** sound boundaries with good testability seams: the `SyncRemote` interface,
`TransactionRunner`, and pure domain calculators. The main structural debts are a monolithic sync engine, very large
screen files (`HomeScreen.kt` is 1,286 lines and `TransactionComposerScreen.kt` 849), whole-table reactive queries,
and hardcoded strings.

---

## 3. Verified implementation status

| Area | Status | Evidence |
|---|---|---|
| Unit tests | **585/585 pass**, 83 suites, 0 skipped | Verified: `./gradlew testDebugUnitTest assembleDebug`. The test task executed (not from cache), on Android Studio JBR (JDK 21) with jvmTarget 17. |
| Debug build | Builds; APK 70.7 MB | Verified |
| Room migrations 1→9 | `MigrationTest` 12 tests passed | Verified (CI), run 40903063139 on `main`, Android 11 emulator |
| Screenshot tour | 1 test ran in CI; failures are swallowed by `|| echo` | Verified (CI) and workflow inspection |
| App launch, API 36 | Home, Transactions, Budget, Wallets, Composer, profile sheet and Settings render while signed out | Verified (emulator) |
| Large font, 2.0× / 360dp | Broken (see N-25) | Verified (emulator) |
| Production Supabase | 11 migrations applied; RLS enabled on all 9 public tables; no edge functions | Verified (MCP read-only) |
| `lucento-test` project | Not accessible from this account | Blocked |
| Vercel website | Project `lucento-website` exists; deployments and runtime errors unavailable | Blocked (403, team scope) |
| Sign-in, sync, backup, restore on device | Deliberately **not** exercised, because the debug build targets production | Unverified |

**Documentation discrepancy:** `CURRENT_STATUS.md` calls the release app `app.lucento`. The code and CI enforce
`com.lucento.app`. An old `app.lucento` v0.1.0 (installed 2026-10-20) is on the emulator, probably an earlier
prototype. The owner should confirm the intended store application ID.

---

## 4. Historical Grok findings — validation

| ID / finding | Classification | Severity | Files | Evidence and explanation | Recommended resolution |
|---|---|---|---|---|---|
| **R10-01** Server refresh token stays valid after interrupted sign-out | **Confirmed** (Code-only) | Medium | `data/sync/AuthRepository.kt:233-277`, `AccountBoundary.kt:75-83` | The ended token is held only in `@Volatile endedAccessToken`, which is lost on process death. The revoke runs last, has a 5-second cap, and is never retried. **New detail:** the revoke authenticates with the access token, so an *expired* access token makes `/logout` return 401 and the refresh token is never revoked. Exploitation requires the refresh token to have been exfiltrated beforehand, because the local copy is deleted. | Persist a non-secret "revoke pending" record, retry it on launch and on connectivity, and use the refresh token in an in-memory-only call, or accept the risk explicitly (owner decision). |
| **Large-font / 360dp layout** | **Confirmed** (Verified) | High | `navigation/NavigationDock.kt`, `feature/home/HomeScreen.kt` | At font 2.0 with density 480: dock labels overlap ("Home"/"Transactions", "Budget"/"Wallets"); amounts truncate ("$2,20…", "$1,370.(" clipped); progress labels clip vertically; "Insights" breaks mid-word; the mascot covers the month dropdown arrow. | Icon-only dock or a single-line `maxLines` with an accessible label above 1.3×; amounts must never truncate (use reflow, `autoSize` or stacking); adapt the layout by `fontScale`. |
| **Offline sign-out does not revoke** | **Confirmed** (Code-only) | Low–Medium | `AuthRepository.kt:260` | `if (... !isOnline()) return`. Nothing is queued. The GoTrue session timebox and inactivity settings could not be checked (SQL and auth config inaccessible). Supabase refresh tokens do not expire on their own by default (Inferred from platform docs). | Same as R10-01: a pending-revoke queue. |
| **Auto-refresh race after local clear** | **Partially confirmed** (Code-only, not reproduced) | Medium | `SettingsViewModel.kt:202-227`, `AuthRepository.kt:241-251`, `LocalLedgerReset.kt` | The tap commits the marker and deletes the stored session. The in-memory supabase-kt session stays live until `auth.clearSession()`, which runs **inside `SyncFlight.exclusive`** and can wait behind a running sync. An auto-refresh in that window can re-persist a session. The window is widened by N-03 (cancellation). On relaunch, `resumeIfInterrupted()` wipes the ledger but **does not clear the stored session**, so the user could come back signed in with sync off and an empty ledger. | Call `auth.clearSession()` (and cancel WorkManager) *before* taking the sync lock, run sign-out in an application scope, and have the resume path also clear the stored session. Add a deterministic test. |
| **`remoteHasData` omits budgets, goals, recurring** | **Partially confirmed** | Low | `SyncRepository.kt:166-169, 882` | The probe checks only `wallets`, `categories` and `transactions`, and it also counts soft-deleted rows. Budgets and recurring rows need category or wallet parents, and every synced account uploads seed categories, so a real false-empty is unlikely. Related: `syncedUserId` is stored *before* the first pull, so a failure mid-first-sync skips `dropUnusedLocalCategories` on retry (later dedup mitigates). | Probe all tables and ignore `deleted_at` rows; set the owner only after the first pull succeeds. |
| **First-sign-in deletes local categories** | **Confirmed, narrow** (Code-only) | Low–Medium | `SyncRepository.kt:180-199, 876-880` | A never-synced, unreferenced local category is deleted **even if the account has no category of that name**, for example a custom "Pets" category created before signing in. Referenced categories are safe and fold later. That is silent loss of user-created structure. | Delete only same-name duplicates; upload uniquely named ones. Add a unit test. |
| **Live/release validation absent** | **Confirmed, and worse than reported** | High | `local.properties`, `.github/workflows/android.yml` | Local debug points at production (N-01). CI never injects `LUCENTO_SUPABASE_*`, so CI release APKs ship with sync disabled (N-24). Production migration versions differ from the repo (N-07). | See §12 P0/P1. |
| Onboarding-entry upload after write | Unverified | — | `LocalWriteUploads.kt` | `uploadRequests` fires on outbox growth. Unit-tested (`LocalWriteUploadsTest`); not on device. | Device test on `lucento-test`. |
| Empty-account defaults / no-income account / in-use categories | Unverified on device; unit logic exists | Medium | `SyncRepository.kt` | `FirstSignInCategories` and R9-03 logic are covered by JVM tests. | Device test matrix. |
| Launch with sync disabled | Code-only, correct | — | `LucentoApplication.kt:88-92`, `SyncOnLaunch.kt` | Guarded by `isEnabled()` and `shouldSyncOnLaunch`. | — |
| Profile email line (R6-08) | Code present (Code-only) | Low | `feature/profile/*:178` | `profileAccountLine(signedInEmail)` exists; only the signed-out variant was seen. | Device check when signed in. |
| Amount select-all (LQA-012/021) | Code present (Code-only) | Low | `core/designsystem/component/AmountTextField.kt:31` | `TextRange(0, len)` on focus. Not observed. | Compose UI test. |
| TalkBack live behaviour | Unverified | High (a11y) | — | Not tested. | TalkBack pass (P1). |
| Instrumented `MigrationTest` | **Already resolved** | — | `androidTest/.../MigrationTest.kt` | Verified (CI): 12 tests on `main`. | Also run on API 28 and 36 locally. |
| Guided-setup steps 9–14 / onboarding step 1 events | Unverified | Low | `domain/UsageCounts*` | Usage counts are opt-in and the production `usage_events` table has 0 rows. | Verify on the test project. |
| **R9-02** marker cleared while ledger visible | Code-only (fix present); not reproduced | Medium | `LucentoApplication.kt:58, 237-240` | The wipe now finishes synchronously in `onCreate` before any Activity. This blocks the main thread with `runBlocking`, a potential ANR on large databases. | Keep; add a timing test; consider a splash-gated wipe. |
| **R6-07** false "two devices" toast | Unverified | Low | `SyncModels.kt:314-330` | Conflict counting moved to `countsAsConflict` (`ConflictCountTest`). No UI copy found to check. | Device regression. |
| Full `./gradlew test` not run | Still true | Low | — | I ran `testDebugUnitTest` only. | Run `test` (debug + release) in CI. |
| Cross-user REST RLS probe | Not performed | High | `supabase/migrations/*` | Repo policies look correct (owner-only, `authenticated` grants). Live policies could not be read (SQL blocked). `list_tables` shows RLS on. | pgTAP and REST probe on `lucento-test`. |
| Voice, receipt scan, debt flows, Crashlytics, live HIBP | Unverified | Medium | — | Not exercised. | Device pass. |
| LUC-SYNC-01/02/03 | Unverified | — | — | Definitions not in repo. | Owner to supply. |
| **LQA-009** tombstone prune | **Not a defect** (Code-only analysis) | — | `SyncRepository.kt:264`, `SyncTombstoneEntity.kt:56` | Pruning removes only **local, already-pushed** tombstones after 30 days. The server keeps soft-deleted rows indefinitely (no purge on sync tables), so devices offline for more than 30 days still learn about deletions through the pull. | Close. |
| `SessionStoreTest` asserted wrong key | **Already resolved** | — | `test/.../SessionStoreTest.kt:63-69` | The test now compares against supabase-kt's own `createDefaultSettingsKey`. | Close. |
| Backup cross-install merge | Unverified on device | Medium | `data/backup/RoomBackupStore.kt` | `BackupTest` passes (JVM). | Device test. |
| Usage allowlist checks names only | **Confirmed** | Low | `20261028180000_usage_events.sql` | `CHECK (event in …)`; `props` limited to 512 bytes; no step validation. Also `insert … with check (true)` for `anon`, so anyone holding the publishable key can insert unlimited rows. | Validate `props` shape; add rate limiting or an RPC; prune dead event names. |
| `onboarding_completed` not allowed | **Confirmed** | Low | same | Not in the allowlist. The allowlist also contains `first_week_card_step_done` and the `budget_suggestion_*` events, whose UI was reverted. | Product decision on funnel events. |
| Test residue qa1/qa2 | Unverified | Low | — | Production has 8 wallets, 40 categories, 31 transactions and 5 recurring rows (counts only). Ownership is unknown. | Owner to authorize inspection and cleanup. |
| Historical process risks | **Confirmed** | Medium | GitHub | On 2026-10-29 the intermediate `main` merge CI runs for #9, #13, #7, #11 and #6 were **cancelled**; only the final #12 merge ran fully. PR #10 is still open. | Merge one PR at a time with a green `main` between merges. |

---

## 5. Newly discovered issues

| ID | Issue | Evidence | Severity |
|---|---|---|---|
| **N-01** | `local.properties` sets `LUCENTO_SUPABASE_URL` to **production** `hmtcuttadbgbldvzsfhz`. Every local debug build signs in and syncs against live data. This contradicts the handover's test-project QA claims. | Verified (host only; no secrets printed) | **P0** |
| **N-02** | `MoneyParser` removes grouping separators without validating group size. `"12,50"` → 125000 minor units (USD, `.`); `"1,5"` → $15.00; `"12,5,0"` is accepted; `"12.50"` → €1,250.00 (`,` locale). No tests cover this. All amount entry flows through this parser. | Verified (executed the compiled class) | **P1** |
| **N-03** | Sign-out runs in `viewModelScope`. Leaving Settings cancels it mid-flow (the mutex wait is cancellable). The ledger stays visible until relaunch, the in-memory session survives, and see the R10-01 and race rows above. | Code-only | **P1** |
| **N-04** | The server revoke uses a possibly expired access token, gets 401, and fails silently. | Code-only / Inferred | P1 (with R10-01) |
| **N-05** | The pull applies rows one by one with no Room transaction and does read-then-write without isolation. A failure mid-pull leaves a partial state (a transfer can briefly have one leg). A UI edit between `findByClientUuid` and `update` can be overwritten (lost update). The class docstring ("every local row exactly as they were") is inaccurate. | Code-only | **P1** |
| **N-06** | No server-side `CHECK` constraints on `amount_minor > 0`, `type`, `currency_code` format or `kind`. Pulled rows bypass repository validation. `TransactionEntity.toDomain()` silently maps unknown types to `EXPENSE`, which is a forward-compatibility hazard. | Code-only | P2 |
| **N-07** | Production migration versions for `usage_events`, `rls_initplan_and_fk_indexes` and `usage_events_purge_schedule` are `20261029030807/14/20`; the repo files are `20261028180000/190000` and `20261029010000`. `supabase db push` would treat them as unapplied, and non-idempotent `create policy` statements would fail. | Verified (MCP `list_migrations`) | **P1** (ops) |
| **N-08** | Production security advisor: **leaked-password protection disabled**. The app does its own client-side HIBP check, but that can be bypassed by calling GoTrue directly. Also flagged: `delete_usage_events` (SECURITY DEFINER) is executable by `anon` (intentional) and `consume_ai_quota` by `authenticated` (intentional). | Verified (advisors) | P2 / owner |
| **N-09** | `usage_events` accepts unlimited anonymous inserts. | Code-only | P3 |
| **N-10** | `res/values/strings.xml` has 3 strings. Essentially all UI copy is hardcoded in Kotlin with no plurals ("1 days left" on Home today). This blocks localization and RTL review. | Verified | P2 |
| **N-11** | Home's loading state renders a 1dp `Spacer`, so the screen is blank while loading. | Code-only (`HomeScreen.kt:201`) | P2 |
| **N-12** | 12 ViewModels observe `SELECT * FROM transactions`. Home collects it twice, and the detail screen loads all rows to show one. Every write re-emits the full list to every active collector. | Code-only | P2 |
| **N-13** | Cold start on the emulator: 9.4 s on first launch after install, then 3.6–3.8 s. This is a debug, non-R8 build with no baseline profile, so it is not representative of release. | Verified (debug only) | P2 |
| **N-14** | The Budget hero says "$1,370 left of $2,200" while the budgeted rows show "$2,000 left" and "$200 left", because unbudgeted Restaurants spend ($830) counts in Spent. This matches `docs/CALCULATIONS.md`, but the two numbers contradict each other on one screen. | Verified (screen) | Owner decision |
| **N-15** | On the last day of the month Home shows "100% of month", "1 days left" and "About $1,370.00 a day keeps you within budget". That is technically correct but not useful guidance. | Verified (screen) | P3 |
| **N-16** | Unused, uniquely named local categories are deleted at first sign-in (detail in §4). | Code-only | P2 |
| **N-17** | No optional app lock, no `FLAG_SECURE` or recents privacy, and the refresh token is plaintext in private SharedPreferences. Acceptable at OS-level encryption, but a common expectation for finance apps. | Code-only | P2 (owner) |
| **N-18** | CI swallows `ScreenshotTourTest` failures (`|| echo`). | Verified (workflow) | P3 |
| **N-19** | Local toolchain drift: an uncommitted foojay plugin in `settings.gradle.kts` and an untracked `gradle-daemon-jvm.properties` (toolchain 21) conflict with CLAUDE.md's JDK 17. `hs_err_pid*.log` JVM crash dumps are in the repo root. | Verified | P3 |
| **N-20** | A Replace restore on a synced phone pushes deletions for every row not in the file and stamps restored rows as newest, so it overwrites cloud data from other devices. This is documented in `docs/BACKUP.md`; the warning copy was not checked on device. | Code-only | P2 |
| **N-21** | Modules disabled by the onboarding "focus" (Goals, Scheduled, Debts on this install) disappear from Home's Quick links. The only way back is Profile → Enabled features. | Verified (screen + `HomeScreen.kt:900-907`) | P2 |
| **N-22** | Appearance and Enabled features appear in both the profile sheet and Settings. Privacy reassurance is repeated in the composer, profile sheet and three Settings rows. Settings is one scroll of roughly 10 screens mixing money, modules, account, data and privacy. | Verified (screens) | P2 |
| **N-23** | The single-item Quick links card leaves large dead space; Home has no recent activity; the Wallets info card puts "Scan a receipt" inside reconciliation advice. | Verified (screens) | P2 |
| **N-24** | CI never sets `LUCENTO_SUPABASE_URL/ANON_KEY`, so CI-built release APKs and AABs ship with sync "not configured". There is no defined release-config path. | Code-only (workflow grep) | **P1** |
| **N-25** | Large-font defects (see "Large-font / 360dp layout" in §4). | Verified | **P1** |
| **N-26** | `finishInterruptedWipeBeforeUi` runs `runBlocking` on the main thread in `Application.onCreate`, an ANR risk on slow devices or large ledgers. | Code-only | P3 |

---

## 6. Security assessment

**Strong:**
- Every user table has owner-only RLS and grants to `authenticated` only. `anon` is revoked. TRUNCATE, TRIGGER and
  REFERENCES are revoked. LWW triggers forbid changing `user_id`. `search_path=''` is set on functions (Code-only;
  live RLS flags Verified).
- No secrets in tracked files or history. Only a fake JWT appears in a test. `google-services.json` and
  `local.properties` are ignored (Verified by grep and `git log`).
- Release builds use R8 and are not debuggable. CI enforces the permission allow-list and application IDs.
  `allowBackup=false`.
- Google sign-in uses a hashed nonce. Debug-only diagnostics strip headers and tokens. The stored sync error is
  sanitized.
- The Smarter reading proxy (`lucento-website/src/pages/api/interpret.ts`, read-only review) verifies the user via
  `/auth/v1/user`, enforces a quota through the user's own JWT, keeps the Gemini key server-side, and never logs
  bodies.

**Weak:** N-01 (production in dev builds), R10-01/N-04 (no revoke retry), the session race and N-03, N-08 (leaked-password
protection off), no app lock (N-17), anonymous usage insert (N-09), and no automated cross-user RLS test.

## 7. Financial integrity assessment

**Strong (Code-only, plus 585 passing tests):** `Money` uses `Long` minor units with `addExact`/`subtractExact`/
`negateExact`. Currency mismatches throw. ISO fraction digits come from `java.util.Currency`. No floating point in
money paths (one `toFloat()` for a display fraction in `Insights.kt:120`). Transfers write two legs in one Room
transaction with idempotency keys checked inside the transaction. Batches validate first, then write atomically. Balance
adjustments read and insert in one transaction. Budgets are always derived (`BudgetPlanner`). Totals are per
currency.

**Gaps:**
- **N-02:** ambiguous amount parsing (the most direct threat to recorded amounts).
- **N-05, N-06:** sync can transiently desynchronize transfer legs and accepts unvalidated remote rows.
- Overflow throws, which is correct, but an `ArithmeticException` thrown inside a `combine` would crash the screen.
  There is no user-facing handling (Inferred; unrealistic amounts only).
- Last-write-wins is per row. A concurrent edit of a transfer on two devices resolves each leg separately; both legs
  share one `updatedAt` per edit, so the outcome is normally consistent (Inferred; untested).

## 8. Synchronization assessment

The design is thoughtful: server-stamped cursor with 60-s overlap, revisions for causal ordering, clamping of fast
client clocks, keyset paging, parent restoration instead of dropping children, a guarded `markPushed`, a
process-wide lock, deterministic seed-category IDs, and account-boundary protection against cross-account uploads.

**Risks:** non-transactional pull (N-05); the first-sync ordering of `setSyncedUserId`; the category drop rule
(N-16); the fake server in tests reimplements the trigger rules in Kotlin, so drift from the real SQL triggers is
untested (no pgTAP); WorkManager retries 5 times then fails, with only the stored error surfaced in Settings; and
there is no two-device end-to-end test. None of the device sync flows were exercised this session (Unverified).

## 9. UI/UX assessment

Evaluated on API 36 at 1080×2400, 420 dpi, light theme, signed out, populated ledger. Dark theme, TalkBack,
onboarding (that would require wiping the emulator's data) and the API 28 device were **not** inspected.

| Dimension | Assessment |
|---|---|
| Visual hierarchy | Clear hero numbers and good signed formatting ("−$830.00" plus color). The Home hero stacks two progress bars ("38% used" / "100% of month") that need a legend to decode. The mascot overlaps content. |
| Information architecture | The dock is sensible. Goals, Scheduled and Debts live behind Quick links and vanish when disabled (N-21). Settings duplicates profile-sheet controls (N-22). |
| Interaction efficiency | **The composer is the strongest screen:** natural-language, multi-entry, honest "read on this phone" labelling, with scan and voice inline. Swipe-to-edit on history is discoverable via a tip. |
| Cognitive load | Budget hero vs. category rows (N-14), "Net" chip with a wallet icon, and repeated privacy paragraphs. |
| Navigation clarity | Good at default scale; broken at large font (N-25). |
| Accessibility | Large-font failures are verified. 48dp targets look respected at default scale. TalkBack is untested. |
| Responsiveness | At 360dp with large text, amounts truncate. This is the most serious UI defect for a finance app. |
| Typography | Plus Jakarta Sans is pleasant. Large display numerals with smaller decimals work. There is no type scale adaptation for `fontScale`. |
| Color | Calm, coherent palette. Saturated blue hero cards on Budget and Wallets differ in treatment from Home's illustrated hero, so the screens feel like different products. |
| Consistency | Three different hero patterns (Home illustrated, Budget and Wallets blue blocks, Transactions bordered summary). Card-in-card nesting on Home. |

**Redesign opportunities (proposal, not implemented):**
1. **One "this month" story on Home:** a single pace visual (spent vs. time) with one plain-language sentence, then
   recent activity and upcoming scheduled items, replacing the side-by-side half-cards and the sparse Quick links.
2. **Unify hero treatment** across Home, Budget and Wallets as one component family.
3. **Budget screen:** show "Planned $2,200 · Spent in plan $0 · Spent outside plan $830" so the two totals
   reconcile visibly (pending N-14).
4. **Settings restructure:** separate Account & sync, Your data, Privacy, and Modules pages; remove duplicates from
   the profile sheet.
5. **Rediscovery:** "More" entries for disabled modules with an inline "Turn on".
6. **Adaptive layout for `fontScale` ≥ 1.3:** single-column stats, icon-only dock, non-truncating amounts.
7. **Move all copy into resources** with plurals (unblocks localization).

## 10. Testing and validation results

| Category | Executed? | Result |
|---|---|---|
| JVM unit tests (`testDebugUnitTest`) | **Executed** | **585 passed, 0 failed, 0 skipped** (83 suites, 3.8 s test time) |
| `assembleDebug` | **Executed** | Success |
| `MoneyParser` probe (compiled class, scratch harness outside the repo) | **Executed** | Ambiguous inputs accepted (N-02) |
| Emulator manual walkthrough (Medium Phone API 36.1) | **Executed** | 7 screens at default; Home and Composer at 2.0× / 360dp |
| Cold-start timing (`am start -W`) | **Executed** | 9.4 s first, 3.6–3.8 s after (debug) |
| Lint, release build, `./gradlew test` | Not executed locally | CI on `main` passed lint, release and AAB on 2026-10-29 (Verified CI) |
| `MigrationTest` (12), `ScreenshotTourTest` (1) | Not executed locally; **requires emulator** | Passed / ran in CI on Android 11 (Verified CI) |
| `RestoreFlowTest`, `CloudSyncSectionTest` | Inspected only; requires emulator | Unknown |
| API 28 emulator (Lucento_API28) | Not executed | — |
| Sign-in, sync, sign-out, backup/restore, two-device | Not executed; **requires backend** (and a test-project build) | — |
| TalkBack, voice, receipt OCR | Not executed; voice and camera best on a **physical device** | — |
| pgTAP / cross-user RLS | Not executed; requires test-project access | — |

## 11. External integration assessment

| Integration | Access | What I did | Findings |
|---|---|---|---|
| **Supabase `lucento` (prod)** | MCP: project list, migrations, advisors and table list work; **`execute_sql` denied** by the permission classifier (catalog-only SELECT) | Read-only metadata only | 11 migrations, version drift (N-07); RLS on 9/9 tables; advisors N-08; 16 unused indexes (INFO, expected at low volume); no edge functions; Postgres 17, ap-northeast-2. |
| **Supabase `lucento-test`** | **Blocked**: not listed for this account; "You do not have permission" | — | Needs an access grant (it may be in another org). |
| **Vercel** | Project list only; deployments and runtime errors return **403**: team scope `jerald23simagalas-projects` not authorized; Vercel CLI not installed | Listed projects; reviewed `/api/interpret` source from the local website repo | `lucento-website` and `klaroph-app` exist. Deployment health is unknown. |
| **Figma** | Authenticated (Starter, **View** seat) | `whoami` | No Figma file is referenced in the repo, so nothing to inspect. Provide file URLs. |
| **GitHub** | MCP server failed to connect (auth header error); **`gh` CLI works** | Read-only PR and CI inspection | PR #10 open; intermediate merge CI runs cancelled; `main` green. |

## 12. Prioritized implementation roadmap

> Superseded by the consolidated roadmap in `docs/handover/NEXT_TASKS.md` (Part II). Kept here as the original
> reasoning.

### P0 — Critical security, financial integrity, or data-loss risk

**P0-1 Separate environments (N-01, N-24).**
- *Problem:* dev builds hit production; release builds have no defined config.
- *Proposed solution:* point local debug at `lucento-test`. Add explicit build-type config (debug → test,
  release → prod from CI secrets). Fail release CI if the config is missing. Show a visible "TEST" badge in debug.
- *Dependencies:* owner provides test-project keys and CI secrets.
- *Risk:* low.
- *User benefit:* no QA pollution of user data.
- *Validation:* APK `BuildConfig` host per variant, checked in CI; a debug sign-in lands in the test project.

**P0-2 Robust sign-out (N-03, race, R10-01, N-04, offline).**
- *Problem:* interrupted or offline sign-out can leave a resurrected local session or an unrevoked server session.
- *Proposed solution:* move sign-out into an app-scoped, non-cancellable coordinator. Clear the in-memory session and
  cancel WorkManager before taking `SyncFlight`. The launch resume path clears the stored session. Add a persisted
  revoke-pending marker (no token values) that retries using the refresh token in memory, or delete the server
  session through an authenticated call made before the local clear, with a fallback queue.
- *Dependencies:* supabase-kt API review.
- *Risk:* medium, because this is safety-critical code. Get independent architect and QA review.
- *User benefit:* "Signed out" means signed out.
- *Validation:* JVM tests for kill at every step, cancellation, expired token and offline; a device kill test
  against `lucento-test`.

**P0-3 Strict amount parsing (N-02).**
- *Problem:* `12,50` → 1,250.00.
- *Proposed solution:* accept grouping only in valid groups of 3 digits. Treat a single separator followed by 1–2
  digits (currency-aware) as ambiguous; reject it or interpret it as decimal with an explicit confirmation. Add
  property tests.
- *Dependencies:* none.
- *Risk:* low (pure function).
- *User benefit:* prevents 100× entry errors.
- *Validation:* test table across `en-US`, `de-DE`, `fr-CH`, JPY and BHD.

### P1 — Functional and reliability

**P1-1 Transactional pull (N-05).**
- *Proposed solution:* wrap each page, or each table's apply, in `withTransaction`, re-reading local rows inside it.
  Correct the docstring.
- *Validation:* fake-server failure-injection test showing no single-leg transfer and no lost local edit.

**P1-2 Migration hygiene (N-07).**
- *Proposed solution:* reconcile repo filenames with production versions, documenting rather than editing applied
  DDL. Add a CI check that compares `supabase migration list` with the repo against the test project.

**P1-3 Large-font and narrow-screen accessibility (N-25).**
- *Proposed solution:* adaptive dock, never-truncate amount component, `fontScale` breakpoints.
- *Validation:* screenshot matrix at 1.0/1.3/2.0 × 360/411dp, in light and dark.

**P1-4 First-sign-in safety (N-16, `remoteHasData`, owner timing).**
- *Proposed solution:* delete only same-name duplicates; probe all tables, excluding soft-deleted rows; set the
  owner after the first successful pull.
- *Validation:* unit tests plus a device matrix on the test project.

**P1-5 TalkBack pass and live regions** on the composer, review, budget and sign-out dialogs.

**P1-6 Release smoke test against production** (owner-approved, dedicated account) after P0-1.

**P1-7 Merge discipline:** one PR at a time, green `main` between merges; close or merge PR #10.

### P2 — UX, architecture, performance

- **P2-1** Home redesign ("this month" story, recent and upcoming items, rediscoverable modules: N-21, N-23,
  N-11 loading state).
- **P2-2** Budget reconciliation presentation (after the N-14 decision).
- **P2-3** Settings information architecture split, removing duplication (N-22).
- **P2-4** Move strings into resources with plurals (N-10), the prerequisite for localization.
- **P2-5** SQL aggregation and scoped queries instead of whole-table observation (N-12), plus paging for history.
  *Validation:* a 20k-row seeded database with frame timing and memory measured.
- **P2-6** Release startup measurement, baseline profile, and lazy initialization of Supabase/ML Kit (N-13).
- **P2-7** Server `CHECK` constraints in a new migration on test first; client validation of pulled rows (N-06).
- **P2-8** Enable leaked-password protection (plan-dependent, N-08); optional app lock and recents privacy (N-17).
- **P2-9** pgTAP RLS tests and a cross-user REST probe on the test project; a two-device sync end-to-end test.
- **P2-10** Split `SyncRepository` into push, pull, first-sign-in and dedup units without behaviour change, guarded by
  the existing tests.

### P3 — Refinements

- Make CI fail on `ScreenshotTourTest` failures (N-18).
- Toolchain alignment: decide on JDK 17 vs 21 (N-19).
- Rate-limit usage events and prune dead event names (N-09).
- Dependency upgrades (AGP, Kotlin, Compose BOM, Room 2.7).
- Remove the main-thread `runBlocking` wipe (N-26).
- Copy fixes ("1 days left", last-day pace message: N-15).
- Unused-index review once traffic exists.

## 13. Decisions requiring product-owner input

1. **Budget "Spent" definition (N-14):** keep "all expenses count against the plan" with clearer presentation, or
   count only budgeted categories with a separate "unplanned" line.
2. **R10-01 / offline revoke:** accept for beta, or require P0-2 first (recommended: require it).
3. **Environment keys:** provide `lucento-test` access for this account/MCP, plus CI secrets for release
   configuration.
4. **Release application ID:** `com.lucento.app` (code) vs `app.lucento` (handover doc).
5. **Supabase plan:** leaked-password protection and session timebox controls; check current pricing first.
6. **Live smoke test approval** after environments are separated.
7. **App lock / biometric gate** as an optional feature.
8. **Onboarding direction:** PR #10 (restore original onboarding) is still open. Decide its fate before any UX
   redesign.
9. **Vercel access:** authorize team scope `jerald23simagalas-projects` if deployment auditing is wanted.
10. **Permission to run catalog-only SQL** on production (or the test project) to verify live policies, grants,
    triggers and the cron job.
11. **Cleanup of qa1/qa2 residue** and merged branches.

---

### Recommended first implementation

**P0-1 (environment separation)** first, because every later validation depends on it. Then **P0-3 (parser)**,
which is small, pure and high-value. Then **P0-2 (sign-out coordinator)**, with architect and QA review before
merging.

---

# Part II — Extended requirements (2026-10-31)

Scope: the owner's added requirements. I reused the Part I findings and inspected only what these questions
needed. No code, configuration, schema or data was changed. Production access stayed read-only: migration list,
extensions, schema listing, and **aggregated** edge logs (path, status and user-agent family only; no user
identifiers read).

## 14. Google Sign-In "webhook" regression investigation

### 14.1 What the Google sign-in architecture actually is (Verified by code and history)

There is **no webhook** in Lucento's Google sign-in, and as far as Git history shows there never was one. The flow is
a native ID-token exchange:

1. **Android:** `AuthRepository.signInWithGoogle` (`data/sync/AuthRepository.kt:94-132`) uses Credential Manager
   with `GetGoogleIdOption`, `setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)` and a SHA-256 nonce.
2. **Supabase Auth:** `auth.signInWith(IDToken) { provider = Google; nonce = raw }` calls
   `POST /auth/v1/token?grant_type=id_token`. Supabase validates the Google ID token against its configured
   Google provider (Authorized Client IDs). No redirect URL, deep link or callback is involved: the manifest has no
   `intent-filter` for auth, and supabase-kt's `handleDeeplinks` is never used.
3. **Vercel** is not part of sign-in. It hosts two related endpoints:
   - `https://www.lucentoapp.com/api/interpret`: the opt-in Smarter reading Gemini proxy. It *uses* the signed-in
     user's Supabase JWT, calling `/auth/v1/user` and then `rpc/consume_ai_quota`.
   - `https://www.lucentoapp.com/auth/confirmed`: the landing page for **email** confirmation links.
4. **Supabase server side:** production has **no** Database Webhooks (`pg_net` not installed; no
   `supabase_functions` schema), **no** edge functions, and no `profiles` table or `auth.users` trigger in the
   repo's migrations. No server-side "account initialization" step exists; initialization happens on the device
   (first sync).

### 14.2 History findings

| Search | Result | Evidence |
|---|---|---|
| Commits mentioning google/webhook/callback/oauth/redirect (all branches, both repos) | Only sign-in feature commits; nothing named webhook or callback | Verified (`git log --all --grep`) |
| Files ever named `*webhook*`, `*callback*`, `*oauth*`, `*deeplink*` | None in either repo (only two screenshot PNGs) | Verified |
| `GetGoogleIdOption`, `connectGoogleAccount` | Added once in `0e96b3e` ("add optional Supabase authentication and offline sync"), **never removed** | Verified (pickaxe) |
| Google sign-in in onboarding UI | `03d6347` (PR #5, "Shorten first run…") **removed** the onboarding Google button (`onGoogle = { viewModel.connectGoogle(context) }`); `ec33fe4` (PR #13 UI revert) **restored** it. HEAD has 11 onboarding references, the same as baseline `988c79c`. | Verified (`git show`, `git grep` per head) |
| Smarter reading (the Vercel-backed feature) | Toggle, ViewModel and remote interpreter present at HEAD, the same as `988c79c` | Verified |
| Website repo (`lucento-website`, 25 commits) | Only ever had `src/pages/auth/confirmed.astro` and `api/interpret` (moved to `src/pages/api/interpret.ts` in `5d976f5`). Neither was deleted. | Verified |
| Recovery stash `073d295` untracked tree | Agent files, handover docs, screenshots and the capture log. **No code.** | Verified (read-only `git show --stat`) |
| `applicationId` | `com.lucento.app` since the first commit (`eafaa4f`); never changed | Verified |

**Conclusion (Verified):** Git history does **not** support "a recent iteration removed the Google Sign-In webhook".
The only Google-related removal was PR #5 taking the Google button out of first-run onboarding, and PR #13 restored
it. I cannot name a "previous working commit plus the removing commit" because no removal of sign-in code exists at
HEAD.

### 14.3 Runtime evidence (Verified: production edge logs, aggregated)

| Day (UTC) | Google `token?grant_type=id_token` | Vercel proxy `/auth/v1/user` | Notes |
|---|---|---|---|
| 2026-10-26 | 0 | 0 | settings fetch only |
| **2026-10-27** | **9 × 200** (Android `ktor-client`, home network) | 7 × 200, **3 × 403** (`node` from AWS = Vercel) + 7 × `consume_ai_quota` 200 | The whole Google → Supabase → Vercel chain **worked on production** |
| 2026-10-28 | 0 | 0 | refresh tokens only |
| 2026-10-29 | 0 | 0 | 2 refreshes |
| 2026-10-30 | 0 | 0 | none |

After 2026-10-27, **no Google sign-in attempt reached production**: neither successes nor failures. Both
`www.lucentoapp.com/api/interpret` (GET → `405 {"error":"method_not_allowed"}`, matching the code) and
`/auth/confirmed` (200) are live today (Verified, unauthenticated GET only).

### 14.4 Most likely causes of the perceived regression (Inferred, ranked)

1. **Environment switching.** QA moved to `lucento-test` builds after 10-27 (per the handover), and production saw
   no more sign-ins. If `lucento-test`'s Google provider is not enabled, or lacks the Web client ID in "Authorized
   Client IDs", Google sign-in fails at Supabase for test builds. Separately, the Vercel proxy evidently validates
   against **production** (its calls appear in production logs), so a **test-project JWT is rejected** (401
   `sign_in_required`) and Smarter reading "breaks" on test builds. The three 403s on 10-27 fit tokens the
   production project didn't accept. *Needs:* `lucento-test` auth-provider settings and the Vercel env var names per
   environment (both blocked, see §15 and §16).
2. **Device without current Google Play services.** The UI-audit screenshots SCR-010 and SCR-025 show
   `TYPE_GET_CREDENTIAL_PROVIDER_CONFIGURATION_EXCEPTION: no provider dependencies found` on an emulator. The local
   `Lucento_API28` AVD uses a non-updatable `google_apis` image. Medium Phone API 36.1 has the Play Store (Verified).
3. **Signing-certificate mismatch.** Credential Manager requires an Android OAuth client for `com.lucento.app.debug`
   or `com.lucento.app` with the **SHA-1 of the signing key in use**. A different machine's debug keystore, or the
   CI or release key, yields Google error `[28444] Developer console is not set up correctly`. Unverifiable without
   Google Cloud Console access.
4. **Stale error copy.** SCR-025 shows an earlier Google error persisting into a later onboarding step (`syncMessage`
   isn't cleared when the step changes), which makes a fixed problem look current.

### 14.5 Recovery path (proposed; nothing implemented)

1. **Owner clarification (blocking):** which observable behaviour "worked before": Google account picker, sign-in
   completing, Smarter reading, or something in another product (for example KlaroPH)? Where, and on which build
   and device?
2. **Reproduce** on Medium Phone API 36.1 (Play Store) with a **test-environment debug build** after P0-1. Capture
   the debug suffix the app already appends to Google errors (`signInFailureMessage(..., debug = true)`). It
   identifies Credential Manager vs. Supabase failures without exposing tokens.
3. **Fix by layer, reusing existing configuration and code** (no new auth flow):
   - Credential Manager error 28444 → register the debug SHA-1 for `com.lucento.app.debug` in the existing Google
     Cloud project (owner action).
   - Supabase 400 "provider not enabled / bad audience" on `lucento-test` → enable the Google provider and add the
     existing Web client ID there (owner action; test project only).
   - Smarter reading 401 on test builds → give Vercel **Preview** its own `SUPABASE_URL` and
     `SUPABASE_PUBLISHABLE_KEY` pointing at `lucento-test`, and add a `LUCENTO_AI_INTERPRET_URL` debug override to a
     preview deployment. `app/build.gradle.kts` already supports that override.
4. **App hygiene in scope:** clear stale sign-in errors when the step changes (SCR-025).
5. **Validate** with the journey in §14.6, on test first. Production only after owner approval.

### 14.6 Google sign-in acceptance tests (none executed yet)

| # | Step | Expected | Layer |
|---|---|---|---|
| G1 | Tap Continue with Google (onboarding and Settings) | Account picker opens; cancel → no error shown | Android/GMS |
| G2 | Pick account | `token?grant_type=id_token` 200 in **test** logs; signed-in email shown | Supabase |
| G3 | First sign-in of a new Google user | Exactly one `auth.users` row; no duplicate identities | Supabase |
| G4 | Same Google account on device B | Same user id; AccountBoundary `Ready`; data pulls | App + Supabase |
| G5 | Relaunch app (cold) | Still signed in; no extra `id_token` call (refresh only) | App |
| G6 | Sign out → sign in again | Ledger wiped then repopulated from cloud; no duplicates | App |
| G7 | Airplane mode during exchange | Honest timeout message; retry works; no half state | App |
| G8 | Kill app during exchange | Relaunch is signed in or signed out, never stuck | App |
| G9 | Smarter reading on a test build | `/api/interpret` preview returns 200; quota row in **test** `ai_usage` | Vercel + Supabase |

## 15. Supabase access assessment

| Item | Finding | Evidence |
|---|---|---|
| Organizations visible to the connector | **One:** "Lucento" (`sgwqquhferjvhttcehzn`), containing only `lucento` (production) | Verified (`list_organizations`, `list_projects`) |
| `lucento-test` (`ogenvdopfgocjdkcrsny`) | `get_project` and `list_migrations` return *"You do not have permission to perform this action"* | Verified |
| **Root cause** | `lucento-test` belongs to a **different Supabase organization** that is not included in the claude.ai Supabase connector's authorization (the connector is granted per organization at OAuth time). | Inferred from the two facts above |
| **Owner action** | **Either** move `lucento-test` into the "Lucento" organization (Project Settings → General → Transfer project), **or** in claude.ai → Settings → Connectors → Supabase, disconnect and reconnect, selecting the organization that owns `lucento-test` as well. In both cases the signed-in Supabase user must be a member (Developer role or higher) of that organization. | — |
| Production SQL | `execute_sql` (catalog SELECT) was denied by the Claude Code **auto-mode permission classifier**, not by Supabase. Production log queries were allowed. | Verified |
| **Owner action** | To permit read-only catalog SQL on production, add an allow rule for `mcp__claude_ai_Supabase__execute_sql` in Claude Code settings, or approve per call. Writes remain gated by the owner's rules regardless. | — |
| Production auth configuration | No MCP tool exposes GoTrue settings (providers, redirect URLs, session timebox). Requires the dashboard, or the Management API with a personal access token. | — |

## 16. Vercel access assessment

| Item | Finding | Evidence |
|---|---|---|
| Visible projects | `lucento-website` (`prj_6nk9mu4QUYB7uJNAVOp6j3m7Zo53`), `klaroph-app`, account `team_HlvPJQ8bo4gEihyecw135oKl` | Verified |
| Deployments and runtime errors | 403: *"Trying to access resource under scope `jerald23simagalas-projects`. You must re-authenticate to this scope."* `list_teams` returns `[]`. | Verified |
| **Root cause** | The claude.ai Vercel connector's OAuth grant does not include the team scope `jerald23simagalas-projects`. Project listing works through the account, but team-scoped endpoints need an explicit team grant. | Verified (error text) |
| **Owner action** | Open the single-use consent link generated with `authorize_team_access` (valid 10 minutes; I'll regenerate on request) and approve. No SSO is required for this team. The Vercel CLI is not installed locally; optional fallback `npm i -g vercel` plus `vercel login`. | — |
| Live endpoints | `/api/interpret` and `/auth/confirmed` respond as the code expects | Verified (unauthenticated GET) |

## 17. Two-device synchronization QA matrix

**Status: NOT EXECUTED.** Two-device validation was **not** performed. It needs P0-1 (debug builds pointed at
`lucento-test`), test-project access (§15), and a dedicated QA account.

**Rig:** Device A = Medium Phone API 36.1 (Play Store; Google or email sign-in). Device B = a second Play-Store
AVD on API 34/35, or a physical phone. The `Lucento_API28` `google_apis` image should use **email** sign-in only.
**Evidence per case:** both devices' screens; counts and sums from a read-only test-project query by `user_id`;
`SyncReport` values; logcat.

**Global acceptance (every case):** no duplicates (count by `id` and by natural key); no missing rows; every
`transfer_group` has exactly one OUT and one IN with equal amount, currency and date; wallet balances equal on both
devices and match a server-side recomputation; no `user_id` other than the QA account; deleted rows absent unless the
documented "newer edit wins" rule applies; pending count returns to 0.

| ID | Scenario | Steps | Expected | Code-based prediction |
|---|---|---|---|---|
| S-01 | Baseline A → B | A: sign in, create 3 wallets (incl. a card or loan with terms), 2 custom categories, 20 transactions, 2 transfers, budgets, 1 goal with 2 contributions, 2 schedules (1 debt payment); Sync now. B: fresh install, "I already have an account", sign in. | All records present on B; balances equal; goal saved equal; debt terms present | Pass (engine unit-tested). Goal photo intentionally not synced. |
| S-02 | B → A create | B adds 5 transactions and 1 wallet; sync both | Present on A | Pass |
| S-03 | Edit propagation | A edits amount, category, date and note of 3 rows, plus a wallet name; sync | B shows edits | Pass |
| S-04 | Delete propagation | A deletes 2 transactions, 1 transfer, 1 schedule, 1 category in use (archive) | Gone on B; the transfer's two legs both gone | Pass; deleting a category in use archives it |
| S-05 | Same-row concurrent edit | Both offline: A sets amount 100, B sets note "x" on the same row; A syncs, then B | **Whole-row** last-writer-wins by edit time; one device's change is lost | Row-level LWW, no field merge. **Owner decision.** |
| S-06 | Edit vs. delete | A deletes row R offline; B edits R later; sync A then B | Per design, the **newer edit resurrects R** | Conflicts with "deleted records never reappear". **Owner decision.** |
| S-07 | Offline creates on both | Both offline, each adds 10 transactions and a category named "Pets"; reconnect | 20 transactions; one "Pets" after dedup; references moved | Pass (`CategoryDedup`) |
| S-08 | Same-name wallets | Both offline create wallet "Cash" | Two "Cash" wallets | By design (no wallet dedup). See O-07 for the onboarding case. |
| S-09 | Scheduled payment on both | Both confirm the same schedule occurrence offline | One transaction (deterministic `occurrenceKey`) | Pass predicted |
| S-10 | Transfer edit vs. delete | A edits transfer amount; B deletes it | Both legs survive or both go; never one leg | Consistent predicted (same timestamps per leg) |
| S-11 | Interrupt mid-pull | B pulls 2,000 rows; airplane mode mid-way; reconnect | Converges; no duplicates. A transient single-leg transfer is possible until retry. | N-05 risk |
| S-12 | Kill during sync | Force-stop B during push, then during pull; relaunch | Converges on the next sync; pending count correct | Cursor saved last; idempotent upserts |
| S-13 | Repeated syncs | Sync now ×10 on both | No changes after the first; no conflict-count growth | Pass predicted |
| S-14 | Clock skew | B's clock +2 h and −2 h; edit the same row | Server clamps >5 min ahead; causal edits accepted regardless of clock | Pass predicted for + skew; a slow-clock *concurrent* edit loses |
| S-15 | Large history | 20k transactions on A; first sync on B | Completes; UI stays responsive | N-12 risk (whole-table flows) |
| S-16 | Account boundary | B signed into QA-2 with unsynced rows, switches to QA-1 | Prompt; nothing of QA-2 reaches QA-1 | Pass (AccountBoundary) |
| S-17 | Sign-out on A while B syncs | A signs out; B continues | B unaffected; A wiped | Pass |
| S-18 | Backup Replace on synced A | Restore an older backup with Replace | Cloud rows not in the file are deleted on B | Documented behaviour; verify the warning copy |
| S-19 | Archive vs. use | A archives a category; B adds a transaction to it offline | Transaction kept; category stays archived | Verify |
| S-20 | Debt terms | A edits loan terms; B edits loan name | Terms ride on the wallet row: whole-row LWW applies | Verify |

## 18. Onboarding and duplicate-data QA matrix

**Status: NOT EXECUTED on device.** The predictions come from code reading (`OnboardingViewModel.kt`,
`SyncRepository.kt`, `AccountBoundary.kt`).

| ID | Scenario | Expected | Code-based prediction | Risk |
|---|---|---|---|---|
| O-01 | New install, new account | Seed income categories plus chosen expense categories; first sync uploads; no duplicate seeds on a second device (deterministic `seed-<uid>-…` ids) | Pass | — |
| O-02 | Existing account, new device, **restore path** | Download first; onboarding completes only on success (`runRestore`); empty account → EMPTY stage | Pass (Code-only) | — |
| O-03 | Existing account, new device, **normal setup path** (signs in on the sync step instead of "I already have an account") | App should detect cloud data and offer restore | **No detection.** Onboarding completes locally, then a background first sync pulls. Freshly *picked*, unused expense categories that the account lacks are **deleted** (N-16). Wallets added in guided setup **duplicate** cloud wallets (no wallet dedup), so net worth is double-counted. | **High** |
| O-04 | Local data before sign-in (offline user later signs into an existing account) | Reconcile without loss | Local rows upload; same-name categories fold; unused uniquely named categories deleted (N-16); duplicate wallets kept | Medium |
| O-05 | Interrupted first sync | Initialization not treated as complete | `syncedUserId` is set **before** the first pull. A failure means the retry skips the pull-first and category-drop logic and uploads local categories (dedup later folds same-name ones). | Medium (violates the requirement) |
| O-06 | Failed sync then retry | Same end state as a clean sync | Converges for rows; see O-05 for categories | Medium |
| O-07 | Repeated onboarding (data cleared, same account) | No duplicate seeds or wallets | Seeds dedupe; wallets created again in guided setup duplicate | Medium |
| O-08 | Kill during onboarding | Resume at the same step | Uses `SavedStateHandle`: survives system process death but **not** force-stop or swipe-away. RESTORING after a force-stop restarts at welcome with sync on and possibly partial data. | Medium |
| O-09 | Reinstall and recover | Restore path brings everything back | Pass predicted (O-02) | — |
| O-10 | Switch accounts | Prompt when unsynced; wipe before the new account syncs | Pass (AccountBoundary) | — |
| O-11 | Existing custom categories | Kept and uploaded | **Unused, uniquely named ones deleted** on first sign-in into an account with data (N-16) | Medium |
| O-12 | Existing wallets, budgets, goals, schedules, transfers | Kept, no duplicates | Kept; wallets not deduped; budgets for folded categories merged by `mergedLimit` | Medium |
| O-13 | Incorrect initial balance | Opening balances preserved exactly | Integer minor units; but an ambiguous separator in the balance field (N-02) | High (N-02) |

**Onboarding UX requirement mapping:** restore has distinct stages (WARN, SIGN_IN, RESTORING, EMPTY, FAILED, with
Try again). **Missing:** detection of cloud data on the normal path, a visible "downloading / reconciling" state
after onboarding, a first-sync failure state on Home (only a Settings line exists), and copy reassuring users that
data isn't lost while a sync is incomplete.

## 19. Cognitive load and UX assessment

Method: heuristic review of the screens captured in Part I plus code (tap counts estimated from implementation).
**Not user-tested.**

| Workflow | Decisions / taps (est.) | Assessment | Direction |
|---|---|---|---|
| Add expense | + → type "coffee 180" → send → Save: about 3 taps plus typing | **Excellent.** Multi-entry, honest local parsing. | Keep; add a last-used wallet default and a one-tap undo after save. |
| Set a budget | Budget → Set amount → type → Save: 4 | Good, but the total vs. per-category arithmetic confuses (N-14) | Show planned / spent-in-plan / outside-plan explicitly. |
| Rediscover a disabled module | Profile → Enabled features → toggle: 3, but **undiscoverable** | Poor (N-21) | "More" list showing off modules with inline Turn on. |
| Understand "can I spend?" | Read the Home hero: 2 bars, 3 stats, 1 message | Moderate. Two bars need decoding; the last-day message isn't actionable (N-15). | One pace visual and one sentence; daily allowance only when meaningful. |
| Check sync health | Home "On device" chip; details deep in Settings | Low transparency | Single status chip (Synced · Syncing · Needs attention) opening a sheet. |
| Sign out | Profile → Settings → scroll ~8 screens → Sign out → confirm | High cost, buried | Account page at the top of Settings. |
| Change theme | Available in the profile sheet **and** Settings | Duplication (N-22) | Keep one place. |

**Against the owner's criteria (H1–H10):** low decisions on entry (H1/H2 ✓); information density too high on the
Home hero and Settings (H3 ✗); visual competition from three hero styles and card-in-card nesting (H4 ✗);
comprehension without explanation fails for budget totals (H5 ✗); error likelihood high for amount separators
(H6 ✗, N-02); recovery has no undo after save (H7 ✗); readability fails at large text (H8 ✗, N-25); first-time
guided setup exists, but returning users see no change summary (H9 ~); consistency is weakened by mixed hero
patterns and repeated privacy copy (H10 ✗).

**Design principles to adopt (proposal):**
1. One primary question per screen: Home "Am I on track this month?", Wallets "What do I have and owe?", Budget
   "Where is my plan off?".
2. Progressive disclosure with a risk floor: never hide over-budget, negative available cash, or overdue
   schedules behind a tap.
3. Separate available cash from net worth on Home: spendable (cash and bank minus savings-funded bills) vs. assets
   minus liabilities.
4. One status channel for sync and background states. No repeated toasts; failures persist until resolved.
5. Plain-language copy in string resources (enables plurals and localization, N-10).

## 20. Updated priorities

Consolidated in `docs/handover/NEXT_TASKS.md`. Changes from Part I:
- **O-03** (no cloud-data detection on the normal onboarding path, leading to duplicate wallets and lost picks) is
  added at **P0**, because it can produce wrong net worth and lose user choices on a common path.
- **O-05** (ownership recorded before the first pull) joins the P1 first-sign-in work.
- The Google sign-in task is re-scoped as "diagnose and fix configuration per layer", because no removed code
  exists. It stays P1 per the owner, gated on clarification.
- Two-device QA becomes P1 execution work after P0-1 and test-project access.

## 21. Dependencies and permissions required

| Need | Owner action | Unblocks |
|---|---|---|
| `lucento-test` access | Move the project into the "Lucento" org, or reconnect the claude.ai Supabase connector including its organization | Two-device QA, onboarding QA, Google tests, pgTAP |
| Test keys in local and CI config | Provide the test project URL and publishable key (the publishable key is not secret, but keep it in `local.properties` or CI env) | P0-1 |
| Read-only production catalog SQL | Allow `mcp__claude_ai_Supabase__execute_sql` in Claude Code settings, or approve per call | Live RLS, grant, trigger and cron verification |
| Vercel team scope | Approve the `authorize_team_access` consent link | Deployment and env-name audit, preview validation |
| Google Cloud Console | Confirm Android OAuth clients and SHA-1s for `.debug` and release; Web client ID used by both Supabase projects | G1–G4 |
| Supabase test auth settings | Enable the Google provider on `lucento-test` with the same Web client ID | G2 on test |
| Second device | Second Play-Store AVD or a physical phone | §17 |
| QA accounts | Dedicated QA Google account and email account on `lucento-test` | §17, §18 |
| Clarification | What exactly the "Google Sign-In webhook" did | §14.5 |
