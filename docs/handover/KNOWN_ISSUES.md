# Lucento Android — Known Issues Register

**Updated:** 2026-10-31 by Claude. Each item was revalidated against `7928c63` (see
`CLAUDE_INITIAL_AUDIT.md` §4–§5 and §14–§19 for the evidence). The original PDF-derived list is folded in;
resolved items are kept at the bottom for traceability.

**Evidence labels:**

| Label | Meaning |
|---|---|
| **Verified** | Run or observed this session |
| **Verified (CI)** | Seen in GitHub Actions logs |
| **Code-only** | Read in source, not run |
| **Inferred** | Reasoned, not observed |
| **Unverified** | Not checked |
| **Blocked** | Attempted, but denied by permissions or tooling |

## Open — P0

| ID | Issue | Evidence | Next step |
|---|---|---|---|
| N-01 | ~~Local debug builds target **production** Supabase~~. **Fixed locally 2026-10-31, uncommitted, awaiting review:** debug reads `LUCENTO_DEBUG_SUPABASE_*` (lucento-test), release keeps `LUCENTO_SUPABASE_*`; the build fails if the debug URL is production | Verified (BuildConfig and APK dex for both variants) | Owner review |
| N-24 | ~~CI release builds carry no Supabase config (sync disabled)~~. **Fixed locally (master run):** the release step reads GitHub secrets; with repo variable `LUCENTO_REQUIRE_RELEASE_CONFIG=true` a release missing the Supabase URL, publishable key or Google web client id fails | Verified (Gradle guard executed locally) | Owner added the repository variables 2026-11-02; confirm with the first Actions run |
| N-02 | ~~`MoneyParser` accepts ambiguous separators: `"12,50"` → $1,250.00; `"12.50"` (comma locale) → €1,250.00~~. **Fixed locally in P0-3 (uncommitted):** one rule set, `Ambiguous` result, composer/AI/receipt aligned; device-verified (en-US, de-DE) | Verified (unit + device) | Owner review |
| P0-3-DATA | ~~Historical review of amounts typed before P0-3~~. **Cancelled by the owner** (QA-Cash is test data; left unmodified) | — | — |
| P0-3-L1 | ~~`ReceiptParser` keeps its own OCR heuristics~~. **Aligned:** a parity test proves every receipt reading is one `MoneyParser` allows (now asserts at least 30 comparisons); the one receipt convention ("Rp 25.000" = 25,000) is documented | Verified (unit) | — |
| P0-3-L2 | The wallet sheet's header preview shows $0.00 while the amount is ambiguous or invalid (pre-existing); the field's message explains | Verified (device) | P3 polish |
| P0-3-L3 | Lakh/crore or "bn" scales aren't read by the composer (it asks instead) | Code-only | Product decision |
| N-03 | ~~Sign-out runs in a cancellable `viewModelScope`~~. **Fixed locally:** NonCancellable wipe; the session is recorded and cleared on the confirm tap | Verified (unit + device kill test) | Owner review |
| R10-01 | ~~Server session not revoked after interrupted sign-out; no retry~~. **Fixed locally:** `PendingRevoke` record (by JWT `session_id`) + `RevokeWorker` (network constraint, backoff), now also queued at tap time | Verified (device: offline revoke 204, kill mid sign-out 204) | Owner review |
| N-04 | ~~The revoke uses a possibly expired access token~~. **Fixed locally:** raw-HTTP refresh first when expired; GoTrue "gone" codes end the record | Verified (unit) | Owner review |
| — | ~~Auto-refresh can re-persist a session; the launch resume path doesn't clear it~~. **Fixed locally:** auto-refresh is stopped first; launch `dropIfEnded` removes a stored session that was recorded for revoking | Verified (unit) | Owner review |
| — | ~~Offline sign-out never revokes~~. **Fixed locally** (WorkManager retry) | Verified (device) | Owner review |
| P0-2.1-A | ~~Account detection ignored transactions, schedules and budgets; the restore read active wallets only~~. **Fixed locally in P0-2.1:** detection covers wallets (archived included), active expense categories, transactions, schedules and budgets; the restore applies the same rule | Verified (unit tests + device O-02/O-03/O-09) | Owner review |
| F-2 | On a never-synced phone, the sign-out prompt says it removes "the account's wallets…", but they are the phone's own never-uploaded records; "Sign out anyway" deletes them (the unsynced count is shown) | Verified (device, shot B-19) | Owner decision |
| F-3 | The guided-setup welcome says the data stays only on this phone while sync is on | Verified (device, shot A-25) | P3 copy |
| C-1 / P1-8 | ~~The combine fold keeps an arbitrary budget amount~~. **Fixed locally (owner: keep the latest edit):** a set amount beats 0, the latest `updated_at` wins, a tie goes to the smaller sync id; order-independent, also for 3+ copies (QA note 1) | Verified (unit) | Owner review |
| P0-2-RISK-1 | **Accepted validation limitations** for the P0-2 closure (`P0_2_FINAL_ACCEPTANCE_REPORT.md` §7, §8, §4):<br>• physical-device background (saved-state) restore: **DEFERRED** (emulators restart the system process; no physical device);<br>• Settings account-check failure: **NOT VERIFIED** on device, automated coverage accepted;<br>• empty account after a switch: automated only (a third QA account isn't authorized);<br>• a second phone's typed, unused category: automated only.<br>Passed on device: E-2, O-10, F-7 returning account, process death during a restore (emulator), upgrade install. | Accepted limitation | Run the saved-state restore on the first physical device |
| QA-N1 | ~~A failed wipe marker crashed the sign-out~~. **Fixed:** the session is cleared only after the marker is written; otherwise a message | Code-only (QA re-review PASS) | — |
| QA-N2 | Pre-existing: if the synchronous launch resume of an interrupted wipe throws, its fallback (LucentoApplication ~84) can race an onboarding saved-state restore. The result is a confusing return to onboarding; no data loss | Code-only (QA review) | Low |
| QA-N3 | A cancelled or failed onboarding restore leaves the sync switch on until `finish()` normalises it. Harmless: background sync waits for onboarding | Code-only (QA review) | Low |
| QA-N4 | No ViewModel-level tests for `OnboardingViewModel` and `SettingsViewModel`; their glue is code-reviewed only | Code-only (QA review) | P1/P2 test debt |
| F-7 | ~~After a sign-out wipe, onboarding stays complete, so a newly signed-in empty account starts with 0 spending categories~~. **Fixed locally (uncommitted):** the wipe resets the account's setup, so the next account goes through onboarding (restore, setup or Try again) | Verified (unit + device returning-account path); empty account unit-only | Owner review |
| F-8 | Auth email links redirected to `localhost:3000`. **Owner configured** Site URL/redirect `https://www.lucentoapp.com/auth/confirmed` on both projects (2026-11-01). **Not independently verified** | Owner statement | One QA confirmation email per project before release |
| AUTH-B | ~~QA account B had 5 live sessions~~. **Done with owner approval:** all signed out (no account or data deleted) | Verified (SQL) | — |
| F7-L1 | Earlier generations' Activity-scoped ViewModels (onboarding, app, editor) stay in memory until the Activity ends | Code-only | Low; idle, no writes after a finished run |
| F7-L2 | The anonymous usage-count install id (opt-in) is device-level, so two accounts on one phone share it | Code-only | Low; documented |
| ENV-1 | Emulator 37.2.12 with fresh API 36.1 images aborts on GPU readback (`hasReadColorBufferDma`): compositor, `screencap`, task snapshots. Not Lucento. Workaround: `-gpu host`, 3-button navigation, host screenshots | Verified | Tooling |
| O-03 | ~~Normal onboarding path doesn't detect an existing cloud account~~. **Fixed locally in P0-2 (2026-11-01, uncommitted):** account check on sign-in, at start-up and as a gate in `finish()`; returning accounts are restored | Verified (unit tests); device run pending | Owner review |
| P0-2-A | ~~Launch/background sync could run mid-onboarding, letting onboarding's category choices tombstone the account's categories~~. **Fixed locally in P0-2:** `backgroundSyncAllowed` gate, plus `applySelection(keepAccountCategories)` | Verified (unit tests) | Owner review |

## Open — P1

| ID | Issue | Evidence | Next step |
|---|---|---|---|
| G-01 / P1-1 | Google one-tap fails on this machine's debug build: `NoCredentialException` (GetGoogleIdOperation 28433). Code intact; the test project's Google provider is enabled. **Cause:** the debug signing SHA-1 `49:AF:14:81:8D:53:F5:DF:68:42:03:3E:3C:1E:2E:DA:AA:AB:8C:75` for package `com.lucento.app.debug` isn't registered as an Android OAuth client in the Google Cloud project of the web client in use | Verified (device log) | **Owner:** Google Cloud > Credentials > Create OAuth client (Android): package `com.lucento.app.debug` + that SHA-1. For release: `com.lucento.app` + the release/Play app-signing SHA-1 |
| N-16 / O-11 | ~~First sign-in deletes unused, uniquely named local categories~~. **Fixed locally in P0-2:** user-typed names are never dropped | Verified (unit tests) | Owner review |
| O-05 | ~~Retry after a failed first sync skips the first-sign-in branch~~. **Fixed locally in P0-2:** separate `initialPullDoneFor` marker (claim unchanged, legacy rule for existing installs) | Verified (unit tests) | Owner review |
| — | ~~`remoteHasData` probes three tables and counts soft-deleted rows~~. **Fixed locally in P0-2:** live rows only, all tables | Verified (unit tests) | Owner review |
| P0-2-B | New in P0-2: "Use Lucento without this account" removes a partially downloaded copy during onboarding (confirmed by the user; uses the existing sign-out wipe) | Code-only | Owner decision |
| P0-2-C | Goal-only cloud accounts count as empty for onboarding (the restore's own rule); goals arrive with the first sync | Code-only (by design) | — |
| N-05 | ~~Pull not transactional~~. **Fixed locally (P1-5):** per-row Room transaction; queued deletes re-check `updated_at` | Verified (unit) | Owner review |
| N-07 | Production migration versions differ from repo filenames for the last three migrations | Verified | P1-6 |
| N-25 | ~~At 2.0x font / 360dp: dock labels overlap, amounts truncate~~. **Fixed locally (P1-7):** icon-only dock when labels don't fit; amounts shrink (never wrap or clip); Home stacks figures and uses font-scaled breakpoints | Verified (emulator 2.0x, 360dp at 1.0x/1.3x; `artifacts/p1-7-accessibility/`) | TalkBack pass still open |
| — | Two-device sync never validated on device | Unverified | P1-3 |
| — | Release build never run against production | Unverified | P1-6 |
| — | TalkBack, voice, receipt OCR, debt flows untested | Unverified | P1-7 / device pass |
| — | Cross-user RLS probe not performed (repo policies look correct; RLS enabled on all 9 production tables) | Verified (flags) / Unverified (probe) | P2-11 |
| O-08 | Onboarding state uses `SavedStateHandle` and is lost on force-stop. **Mitigated in P0-2:** the start-up check and the finish gate recompute the route; `finish()` never sets up as new on top of a partial restore | Code-only | Device run pending |
| N-18 | ~~CI ignores screenshot-tour failures~~. Already fixed on `main` (`exit 1`) | Verified (workflow) | — |
| — | Process: intermediate `main` merge CI runs were cancelled on 2026-10-29; PR #10 still open | Verified (`gh`) | P1-6 |

## Residual risks from the QA review of the master run (PASS WITH NOTES)

| ID | Issue | Evidence | Next step |
|---|---|---|---|
| QA-R5 | `AMOUNT_UNVERIFIED` (a remote amount not found in the text) is a warning, not blocking | Code-only | Product decision |
| QA-R6 | The 8 s inline revoke cap can't interrupt a blocking `HttpURLConnection` (its timeouts bound it at about 30 s; the worker retries) | Code-only | Low |
| QA-R7 | If writing the revoke record itself fails (disk full), the session is still cleared locally but not revoked on the server | Code-only | Low |
| QA-R8 | Rotated tokens from a refresh that succeeded before a failed logout aren't saved; the retry relies on GoTrue's refresh-token reuse handling | Code-only | Low |
| QA-R13 | `RevokeScheduler` uses KEEP, so a new record can wait behind an existing backoff; the inline drain usually covers it | Code-only | Low |
| QA-R14 | Dismissing a switch prompt runs in a cancellable coroutine (pre-existing) | Code-only | Low |
| P1-7-L1 | Transactions type tabs are tight at 360dp/1.3x (still readable) | Verified (screen) | P3 polish |

## Open — P2 / P3

| ID | Issue | Evidence |
|---|---|---|
| N-06 | ~~No server CHECK constraints~~. **Done on `lucento-test` only** (22 NOT VALID constraints, live rows only); pulled *and pushed* rows are checked by `PulledRowRules`, so one invalid local row no longer blocks a batch. Production needs owner approval | Verified (test: invalid live row blocked, soft delete accepted, app write accepted) |
| N-08 | Production leaked-password protection disabled (advisor) | Verified |
| N-09 | `usage_events` accepts unlimited anonymous inserts; allowlist checks names only; `onboarding_completed` not allowed; dead event names remain | Code-only |
| N-10 | Only 3 entries in `strings.xml`; no plurals ("1 days left"); blocks localization | Verified |
| N-11 | Home loading state is blank | Code-only |
| N-12 | 12 ViewModels observe the whole `transactions` table | Code-only |
| N-13 | Cold start 3.6–3.8 s (debug emulator); release unmeasured | Verified (debug) |
| N-14 | ~~Budget hero vs. category rows don't reconcile~~. **D1:** the Budget summary now says "Spent = $X in your plan + $Y outside it" when there is outside-plan spending | Code-only (build) |
| N-15 | Last-day pace message not useful | Verified (screen) |
| N-17 | ~~No app lock or recents privacy~~. **Done locally (P2-10, D8):** optional lock (fingerprint/face/screen lock, 60 s grace, turns itself off if the phone loses its screen lock) and Hide in recent apps (`FLAG_SECURE`) | Verified (emulator; recents blanking verified by the window flag only, since host screenshots bypass it) |
| N-20 | Backup Replace on a synced phone deletes cloud rows not in the file (documented) | Code-only |
| N-21 | Disabled modules are hard to rediscover | Verified |
| N-22 | Duplicated controls and repeated privacy copy; Settings very long | Verified |
| N-23 | Sparse Quick links card, no recent activity on Home | Verified |
| N-19 | Local toolchain drift (JDK 21 daemon file, foojay plugin uncommitted) and `hs_err` logs | Verified |
| N-26 | Main-thread `runBlocking` wipe in `Application.onCreate` | Code-only |
| S-05 / S-06 | Whole-row LWW; a newer edit resurrects a deleted record (by design) | Code-only; owner decisions D2, D3 |
| — | Device workflows not yet run on `7928c63`: onboarding-entry upload, empty-account defaults, no-income account, profile email line, amount select-all, onboarding events 1 and 9–14, backup cross-install merge | Unverified |
| — | R9-02 (marker cleared while ledger visible): fix present, not reproduced | Code-only |
| — | R6-07 false "two devices" toast | Unverified |
| — | qa1/qa2 test residue (ownership of production rows unknown) | Unverified |

## Access blockers

| Blocker | Cause | Fix |
|---|---|---|
| `lucento-test` not reachable via MCP | Project is in a Supabase org not granted to the claude.ai connector (only org "Lucento" is visible) | Move the project into "Lucento", or reconnect the connector including its org |
| Production `execute_sql` denied | Claude Code auto-mode permission classifier | Allow `mcp__claude_ai_Supabase__execute_sql` or approve per call |
| Vercel deployments and logs 403 | Connector lacks team scope `jerald23simagalas-projects` | Approve the `authorize_team_access` consent link |
| GitHub MCP | Connector failed (bad auth header); `gh` CLI works | Reconnect the GitHub connector (optional) |

## Resolved / closed

| Item | Resolution | Evidence |
|---|---|---|
| Instrumented `MigrationTest` 1→9 unverified | 12 tests pass on `main` CI (Android 11 emulator) | Verified (CI) |
| `SessionStoreTest` asserted the wrong key | Now asserts against supabase-kt's `createDefaultSettingsKey` | Code-only |
| LQA-009 tombstone prune | Not a defect: only local, already-pushed tombstones are pruned; the server keeps soft deletes | Code-only analysis |
| "Release app id `app.lucento`" | Code and CI enforce `com.lucento.app`; the doc was inaccurate (owner to confirm the store ID, D6) | Verified |

## Test data and safety notes

Never clear QA data without confirming account, project and owner approval. Never retrieve, print or commit
passwords or tokens (the handover noted a qa1 password once appeared in tool output; rotation unverified).
