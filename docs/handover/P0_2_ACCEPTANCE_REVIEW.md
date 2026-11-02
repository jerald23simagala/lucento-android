# P0-2 — Acceptance review and device QA preparation

**Date:** 2026-11-01 · **Scope:** review only. No implementation code, schema, configuration or data changed; nothing
committed; stash intact. The only file written is this one.

**Evidence labels:**

| Label | Meaning |
|---|---|
| **Verified** | Run or observed this session |
| **Code-only** | Read in source or migrations, not run |
| **Unverified** | Not checked |

## 1. Detection coverage

**Rule as implemented** (`SyncRepository.probeAccount`, `SyncRepository.kt:197`): the account "has data" when it has a
live (`deleted_at is null`) **wallet** (archived ones included) or a live, **active expense category**. This is
exactly `RestorePlan.hasData` (`domain/Restore.kt:14`), applied to server rows. It was aligned on purpose: a wider probe
sent a user to "Welcome back" and then to "nothing to restore" (QA finding M2).

**Two safety nets sit behind the probe.** Even when it answers "empty", (a) the first sync's own pull-first test,
`remoteHasData`, checks live rows in *every* synced table (`SyncRepository.kt:186`), and (b) onboarding re-checks in
`finish()`.

### The five questions

| Question | Answer | Evidence |
|---|---|---|
| **Budgets without wallets?** | Possible, but only on a category. A budget row has no wallet link; its FK is to a category (`create_sync_tables.sql`, budgets: `references categories … on delete cascade`; local `BudgetEntity` FK `CASCADE`). A budget on an **active** expense category means the account has one, so it is detected. A budget on an **archived** category (the user "deleted" a used category, so it was archived, `CategoryRepository.remove`, line 82) is ignored by every calculation (`docs/CALCULATIONS.md` row "Monthly budget": archived never count) and, with no wallet, the probe says empty. | Code-only |
| **Goals without wallets?** | **Yes.** `goals.wallet_id` is nullable (`on delete set null`; local `GoalEntity` FK `SET_NULL`). A goal needs no wallet or category. Onboarding forces at least one expense category, so a goal-only account would need the user to later archive/delete every category and wallet. | Code-only |
| **Transactions without an active spending category?** | **Detected anyway.** A transaction's wallet is `NOT NULL` with an FK (`transactions` → wallets `on delete restrict`; local `RESTRICT`), and `WalletRepository.delete` refuses a wallet that has transactions (`WalletRepository.kt:149`). So live transactions imply a wallet row, which the probe counts (including archived wallets). Income-only users hold a wallet too. | Code-only |
| **Can soft-deleted records affect detection?** | **No.** The probe filters `deleted_at is null`. Test `an account whose records were all deleted counts as empty` shows an account with only a tombstoned wallet is "empty" while the row still exists on the fake server. | Verified (unit test) |
| **Can RLS or network failures give a misleading empty result?** | **No path found.** (1) Network, server and expired-token errors raise, so the result is `Unavailable`, tested twice (`a network failure during the check is never treated as a new account`, `a failing account check during the first sync claims nothing and uploads nothing`). (2) A request without a user session uses the anon role; the migration revokes all table privileges from `anon`, and a live call on `lucento-test` with only the publishable key returned **HTTP 401, not an empty list**. (3) With a valid session, RLS limits rows to the caller's own and the query is filtered by that same `user_id`, so "empty" can only mean the caller has none. Not runtime-tested: a revoked session with a stale cached user (code reading says the 401 raises). | Verified (unit tests, live 401 on test); rest Code-only |

### Potential misclassification (valid account judged "new")

| Scenario | Likelihood | Consequence |
|---|---|---|
| **A. Goals only** (no wallet, no active expense category) | Very low | Set up as new. The first sync's `remoteHasData` still pulls first, the goals arrive (matched by `client_uuid`, not duplicated). No wallet exists on the account, so there is nothing to duplicate. Picks create new categories; the goal's data is untouched. **Benign.** |
| **B. Budgets only, on archived categories** | Very low | Same as A. The budgets are ignored by every total anyway. |
| **C. Live transactions or schedules under a *soft-deleted* wallet** (device A deleted an empty wallet; device B added a transaction to it offline, so the server holds a deleted wallet with live children) | Low, needs a concurrent delete and add | The probe says empty, so onboarding sets up as new and creates a guided-setup wallet. The first sync then pulls the children and "restores" the deleted parent (`resolveWallet`) — so the account ends up with the restored wallet **plus** the new one (a duplicate). |
| **D. Income categories only** | n/a | Correct: that is exactly what a fresh install uploads. |

### Smallest safe correction (not applied)

**Add `transactions`, `recurring` and `budgets` to the probe's table list** (up to 3 more tiny requests, only
evaluated when wallets and expense categories came back empty, so the common paths cost nothing extra). Pulling any of
these brings back a parent wallet or category (`resolveWallet` / `resolveCategory`), so the restore's own
`RestorePlan.hasData` is true afterwards and the "Welcome back" / "nothing to restore" mismatch can't return. This closes
scenario C and the budget half of B.

**Leave `goals` out unless `RestorePlan.hasData` is extended too:** a goal-only restore would otherwise end in the
"nothing to restore" screen. Extending both rules to include goals is also small, but touches the restore screen, so I
did not propose it as the minimum. Scenario A is benign as analysed.

**Verdict:** the implemented rule is sufficient for every account the app can normally create, and its failures are
safe. It is **not** exhaustive against concurrent-delete edge cases. The correction above is cheap; I recommend it, but
it is your call.

## 2. Combine flow (Settings: this phone has records, the account has data)

Code paths: `firstSyncCheck` (`SettingsViewModel.kt:269`), `AccountPrompt.Kind.MERGE` (lines 232, 247), dialog in
`SettingsScreen.kt:154`, sync in `SyncRepository.kt:104-114`.

| Check | Result |
|---|---|
| Clear confirmation | **Yes.** Shown before any upload, for sign-in, **Sync now** and **Sync first**. "Don't connect" signs out and uploads nothing (`disconnectUnsynced`, line 285). Code-only |
| Both datasets preserved | **Mostly, with one exception (finding C-1).** Wallets, transactions, transfers, schedules, goals, budgets and contributions are matched by `client_uuid`, so nothing from either side is overwritten. **Same-name categories fold** (`mergeDuplicateCategories`, `SyncRepository.kt:274`): the copy's transactions and schedules move to the keeper, and if both copies have a budget in the same currency, the **copy's budget row is deleted and the keeper's amount wins** unless the keeper's is 0 (`CategoryDedup.mergedLimit`, line 286). The keeper is the copy with the smaller random `client_uuid` (`CategoryDedup.kt:23`), so which side's budget amount survives is **arbitrary**. |
| Duplicates not silently removed | **Wallets and transactions: correct.** Nothing is merged by name. **Categories: they are**, as above, and also unused preset or copy categories are tidied up by the first pull (`dropUnusedLocalCategories`, line 228). Those are not financial records, but the dialog says "nothing is deleted". |
| Duplicate totals explained | **Yes** in the dialog ("counted twice in your totals until you delete the extra copy"). It doesn't say *how* to find the copy, and it doesn't mention that two wallets each keep their own balance (net worth adds both openings). |
| Retry-safe | **Yes.** Double-tap guarded (`syncing`). Pushes upsert by id; pulls match by `client_uuid`; a failed sync retries from the same state. The consent is one-time because the claim (`syncedUserId`) is set on the first attempt; if the check itself fails nothing is claimed. Verified in unit tests for the engine; the Settings flow itself is not runnable without a device. |
| Failed combination leaves no partial state | **Partly.** Failing *before* the first pull (network, check) claims nothing and uploads nothing; sync stays enabled and the background retries once consent was given. A failure *during* the pull leaves some account rows on the phone and the first-pull marker unset, so the retry pulls first again and converges. There is no rollback (the pull isn't transactional, N-05). Acceptable, but not "no partial state". |

### Finding C-1 (Medium): the combine dialog promises more than the engine does

"Nothing is deleted" is not accurate for categories and, in one case, for a **budget amount**. This pre-dates P0-2 (the
fold has always run on every sync), but P0-2's dialog now asserts the opposite.
- **Smallest honest fix:** change the copy to say same-named categories are combined, and amounts for a category that
  both sides budgeted are kept from one side.
- **Better fix (small, needs approval):** when two budgets collide, keep the *larger* limit, or the one with the later
  `updatedAt`, instead of an arbitrary one. This changes sync behaviour for existing multi-device users, so I did not
  touch it.

## 3. Device QA plan (`lucento-test` only)

### 3.1 Fixtures and rules

- **Devices (nothing existing is touched).** Create two *new* AVDs from images already installed, e.g.
  `Lucento_QA_A` and `Lucento_QA_B` from `android-36.1;google_apis_playstore` (`avdmanager` is present). The existing
  API 36 emulator and the API 28 one stay as they are. API 28 has no Play Store: email sign-in only.
- **Accounts.** Two *new* QA accounts for destructive-looking cases, plus the existing QA accounts' data for the
  "returning account" cases. For email, Supabase on test requires confirmation (`mailer_autoconfirm: false`), so use real
  inboxes (a Gmail `+qa` alias works). **Credentials never go in chat or in tracked files:** type them on the device, or
  keep them in a git-ignored local file I read only to type them via `adb`.
- **Evidence capture (per scenario).** (1) screenshots; (2) `adb logcat -d | grep -c "FATAL EXCEPTION"` = 0; (3) pull the
  app database for counts: `adb exec-out run-as com.lucento.app.debug cat databases/lucento.db` (with `-wal` and `-shm`),
  then open with Python `sqlite3` and count rows per table; (4) server counts per `user_id` through read-only queries on
  the test project (needs your approval for read-only `execute_sql` on test, or I use the dashboard counts you read out).
- **Test data is identifiable:** names prefixed `QA-`. No deletion of existing QA rows.
- **Known fixture on test now** (row counts only, owners unknown): 2 users; 8 wallets, 98 categories, 13 transactions,
  4 budgets, 0 goals, 0 schedules. That is a ready "returning account with data" for scenarios 2, 4 and 9.

### 3.2 The 13 onboarding scenarios (audit §18)

"Fresh" = a newly created AVD, app freshly installed, onboarding not started. "DB" = counts from the pulled database.

| ID | Starting condition | Action | Expected | Data-integrity checks | Pass evidence |
|---|---|---|---|---|---|
| **O-01** New install, brand-new account | Fresh; new email account never used | Welcome → focus → … → sync step: create the account, confirm email, sign in → finish | Check says empty → normal setup; sync starts at finish | Server: exactly the seeded income categories plus the chosen expense ones, 0 wallets until guided setup; second sync adds nothing | Screens; DB vs server counts equal; no duplicate category names |
| **O-02** Existing account, new device, restore path | Fresh; account with data (existing QA) | Welcome → "I already have an account" → sign in | "Bringing your money across" → Home with the data | Wallet, transaction, budget counts equal server; each transfer has 2 legs; currency matches | DB = server per table; screenshots |
| **O-03** Existing account, normal path | Fresh; account with data | Welcome → *Continue*; at the sync step sign in | **"Welcome back"** restore, not setup; picks not applied | No new wallets or categories beyond the account's; onboarding completes only after the download; account's categories all present | Screens (welcome-back copy); DB = server; zero tombstones for the account's categories on the server |
| **O-04** Local data before sign-in | Existing install with data (signed out) | Settings → connect an account that has data | **Combine dialog** → confirm | Both sets present; wallets named alike appear twice; totals as warned | Counts: local + server = new total; dialog screenshot |
| **O-05** Interrupted first sync | Fresh or O-04 setup; airplane mode toggled mid-sync (or throttle) | Confirm sign-in, cut the network during the download, restore it | Retry converges; no duplicates; first-pull marker only set after success | Counts equal server after retry; no extra wallets; run Sync now 3× with no change | DB = server; sync report `pushed 0` on repeats |
| **O-06** Failed sync then retry | As O-05 but the server unreachable for a full attempt | Fail, then Try again | Setup not marked complete while failing; Try again works | Onboarding flag stays false until success | Screens: Try again; DB unchanged until success |
| **O-07** Repeated onboarding | Existing install; clear *only the new AVD's* app data and re-run, same account | Sign in again | Returning account is detected each time | Account row counts unchanged on the server | Server counts before/after identical |
| **O-08** Kill during onboarding | Fresh; signed in on the sync step | `adb shell am force-stop`, relaunch; also swipe away | Reopens without syncing; the account check re-routes (welcome-back if data) | Server counts unchanged during the kill; no background sync before finish (verify in logcat/server logs) | Logs show no sync before finish; counts unchanged |
| **O-09** Reinstall and recover | Fresh after uninstalling the *new* AVD's app | Reinstall, restore path | Everything returns | DB = server | As O-02 |
| **O-10** Switch accounts | Install with account A's synced data | Sign out, sign in as B (has data) | Wipe of A's data, B downloads; nothing of A reaches B | Server B has none of A's ids; A unchanged | Server per-user counts before/after |
| **O-11** Existing custom categories | Account with a custom category "QA-Custom"; fresh phone with a *typed* custom category "QA-Mine" unused | First sign-in | Account's category present; "QA-Mine" kept and uploaded; unused presets tidied | Category names on server and device; no deletion tombstones for the account's | Names list on both sides |
| **O-12** Wallets, budgets, goals, schedules, transfers | Account with all kinds (create via a QA device first) | Restore on another device | All present once | Per-table counts; transfers whole; budget limits equal; goals' saved amounts | DB = server per table; balances equal on both devices |
| **O-13** Initial balance accuracy | Fresh; guided setup balance `1,234.56` and `12,50` | Enter in the wallet balance step | `1234.56` accepted; `12,50` is **expected to misparse today** (N-02, P0-3 not started) | Stored `opening_balance_minor` | DB value; record the N-02 behaviour as a known failure |

**Settings combine flow (add as O-14).**
- *Start:* the existing API 36 install (signed out, 1 wallet, 2 transactions), **or** a fresh AVD with locally created
  records, and a QA account with data on test.
- *Action:* Settings → Continue with Google / email → account has data and the phone has records.
- *Expected:* the dialog appears *before any upload*; **Don't connect** leaves the phone signed out and the server
  unchanged; **Add to account** syncs.
- *Checks:* server counts = old account + phone's; same-name categories fold once; test the budget collision from C-1;
  tap **Sync now** with the dialog unanswered (it must re-ask); kill the app on the dialog.
- *Caution:* confirming uploads this phone's records into that QA account. Use a dedicated account and rows labelled `QA-`.

## 4. Google sign-in prerequisites (test project)

| Item | Finding |
|---|---|
| Google provider on `lucento-test` | **Enabled** (public auth settings: `google: true`, same as production). Verified |
| Email provider on `lucento-test` | Enabled; sign-up is open and **requires email confirmation**. Verified |
| Has Google ever worked on test? | **No evidence.** Auth logs for 10-28…10-31 show only email-password grants (88 successes, 6 failures on 10-29) and refreshes, **no `id_token` exchange**. Production last showed Google success on 2026-10-27. Verified (aggregated logs) |
| Web client ID on the test provider ("Authorized Client IDs") | **Cannot be read** (no MCP tool exposes it; needs the dashboard). The app sends one Web client ID for both builds. Unverified |
| Android OAuth client for the debug certificate | The debug certificate's SHA-1 on this machine is `49:AF:14:81:8D:53:F5:DF:68:42:03:3E:3C:1E:2E:DA:AA:AB:8C:75` (Verified, `signingReport`). `docs/SYNC.md` lists the same value as registered for the development machine; whether it is still registered for `com.lucento.app.debug` in Google Cloud is **Unverified**. (This fingerprint is public by design.) |
| Google consent screen | If publishing status is "Testing", only listed test users can sign in. Unverified |
| Emulator readiness | The API 36 emulator image has the Play Store, but **no Google account is added** (0 found), so the account picker has nothing to offer. Verified |
| `AI_INTERPRET_URL` / Smarter reading on test builds | Still points at the production proxy, so test-account tokens are rejected there. Out of scope for P0-2 QA |

**Owner actions needed for Google QA** (no credentials in chat):
1. In the **test** Supabase project: Authentication → Providers → Google, confirm **Authorized Client IDs** contains the
   same Web client ID as `LUCENTO_GOOGLE_WEB_CLIENT_ID`, and that "Skip nonce check" is off.
2. In **Google Cloud Console**: confirm an Android client for `com.lucento.app.debug` with the SHA-1 above, and that your
   QA Google account is a test user if the app is in Testing.
3. Add that Google account to the QA emulator (Settings → Passwords & accounts).

Email-only QA needs none of this and can start as soon as accounts exist.

## 5. Outstanding blockers

| # | Blocker | Needed from you |
|---|---|---|
| 1 | Two fresh emulators, without touching the existing ones | OK to create `Lucento_QA_A` and `_B` (several GB each) |
| 2 | QA accounts (email, confirmable) and/or a QA Google account | Create them; enter passwords on the device, not in chat |
| 3 | Server-side counts for evidence | Approval for read-only `execute_sql` on `lucento-test`, or counts read from the dashboard |
| 4 | Google sign-in readiness | Owner actions in §4 (optional for email QA) |
| 5 | Combine-dialog accuracy (C-1) and the optional probe correction (§1) | Decisions below |

## 6. Recommended next action

1. **Decide C-1:** I recommend fixing the dialog copy now (tiny, no behaviour change), and scheduling the budget-collision
   rule ("keep the larger or the later-edited limit") as a separate sync task since it changes existing sync behaviour.
2. **Decide the probe correction:** add `transactions`, `recurring`, `budgets` to the probe (recommended, low risk); leave
   goals as they are.
3. **Unblock device QA:** items 1–3 in §5. Email-only QA (O-01…O-14 except the Google variants) can run first.
4. After those, run the checklist and report each case as pass/fail with evidence. Production stays untouched.

I will make no code change until you decide items 1 and 2.
