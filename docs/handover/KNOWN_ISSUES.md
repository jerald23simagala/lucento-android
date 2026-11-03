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
| G-01 / P1-1 | Google one-tap shows `NoCredentialException` [28433] on the emulators. **Corrected diagnosis (2026-11-02):** neither QA emulator has a Google account (`dumpsys account`: 0), which alone produces 28433; the earlier "SHA-1 not registered" conclusion was not proven. The owner confirms the debug Android OAuth client (com.lucento.app.debug, SHA-1 49:AF:…:8C:75) is registered. Code reviewed: `setFilterByAuthorizedAccounts(false)`, Web client id as server client id, hashed nonce — correct | Verified (device accounts, code) | Test one-tap on a device that has a Google account (owner's Samsung); NOT TESTED on emulators |
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

## Found and fixed in the stabilization cycle (2026-11-02, two-emulator QA)

| ID | Issue | Evidence |
|---|---|---|
| SQ-C2 | ~~An offline delete uploaded later beat a newer edit from another device (violated D3)~~: tombstones were pushed with the upload time. **Fixed:** they carry the time of the delete | Verified (device retest, unit, mutation-checked) |
| SQ-C3 | ~~Two phones budgeting the same category kept two budget rows; each phone could show a different budget~~. **Fixed:** sync folds them (latest edit, same row everywhere) | Verified (device retest + server, unit) |
| SQ-NAV | ~~Back from a Settings page skipped the hub~~ (launchSingleTop replaced it). **Fixed** | Verified (device) |
| SQ-COPY | ~~Delete dialog said "from this device" with sync on; stale Google error stayed after switching to email; guide said "only on this phone" with sync on~~. **Fixed** | Code-only (build) |

## QA review of the stabilization cycle (read-only agent, 2026-11-02) — outcome

Fixed (high/medium): H1 pgTAP "move a row" tests now expect the sync trigger's P0001 ('user_id cannot change');
M1 the sheet's Try again uses `SyncScheduler.retryNow` (REPLACE when nothing runs) so WorkManager backoff can't swallow
it; M2 account-mismatch has its own copy and goes to Account & sync, and "keeps retrying" is gone; M3 Settings pages
reuse the hub's ViewModel (one account check, one prompt); M4 Home's module offers can be hidden ("Hide these");
M5 the budget-fold notice counts only an unsynced amount that Budget was showing (never a 0 row, never an archived
category); M6 the fold keeps the winning edit's own time; M7 a never-synced row being adopted is not "replaced".
Fixed (low): L1 no "Set up cloud sync" in builds without sync; L2 privacy summary counts Smarter reading only where it
is offered; L3 dismissing the notice subtracts only what was shown; L4 stray comments; L9 "Spendable now" only when it
differs from net worth. Tests: SyncEngineTest (fold notice rule, fold time, M7), SyncTransparencyTest (mismatch,
unconfigured), HomeOnTrackTest (L9).

## Open after the stabilization cycle

| ID | Issue | Evidence | Next step |
|---|---|---|---|
| SQ-F4 | A local row the server rules refuse (e.g. a corrupt amount) stays queued and the sync sheet says "1 change waiting … upload by themselves shortly", though it never will until fixed. Valid rows are not blocked | Verified (device F-4) | Name the held-back rows in the sheet (P2-2 follow-up) |
| SQ-F3 | Expired/revoked session on a device not exercised on device | NOT TESTED | Revoke one device's session on test and wait out the token (about 1 h) |
| SQ-TB | Full spoken TalkBack focus order not walked (adb can't drive TalkBack reliably on the emulator); label/target audit of 10 screens clean | Emulator only | Walk with TalkBack by hand on a device |
| SQ-RLS | pgTAP suite `supabase/tests/rls_test.sql` written but not executed (no Docker here); a write-path probe on test was declined by the session's permission system. Read-only cross-user REST probe PASS | Partly verified | Run `supabase test db` locally or in CI |
| SQ-IDX | Test-project advisor lists 10 unused indexes; FK indexes should stay; the old `*_user_updated` cursor indexes look superseded by the `server_updated_at` cursor | Advisor (test) | Decide on production with real statistics |
| QA-L6 | The server clamps a fast clock's `updated_at` but not `deleted_at`, and the client compares remote deletes on `deleted_at`, so a delete from a phone whose clock runs fast can beat newer edits on devices that pull before they push | Code-only (QA) | Compare on `updated_at`, or clamp `deleted_at` in a new migration (test first) |
| QA-L7 | The same deleted row can arrive by id (rejected) and in a page, queueing two deletes; the second adds a spurious conflict count (the P2-6 count is correct) | Code-only (QA) | De-duplicate queued deletes by table + id |
| QA-L8 | CSV export reads the shared transactions flow, which can replay the value from just before a save lands | Code-only (QA) | Read the DAO directly for one-shot exports |
| QA-L10 | An overpaid liability is announced as "minus liabilities −X" | Code-only (QA) | Word negative liabilities as credit |
| QA-T | No instrumented Room test for the three new DAO queries; ScreenshotTourTest doesn't visit the Settings hub, sync sheet or module offers | Code-only (QA) | Add tests |
| RD-C1 | **Rollout order (budget lifecycle, DB v10).** v10 always sends `budgets.month`/`effective_from`; a project without migration `20261102120000_budget_lifecycle_and_institutions.sql` rejects budget uploads (rows stay pending, nothing lost). Applied by the owner on `lucento-test` 2026-11-02 (columns + 4 CHECKs verified read-only) and **on production 2026-11-03** with `production-migration-2026-11-03.sql` (together with 20261101120000/180000) | Test verified; production owner-reported | Closed. Confirm the migration history was recorded (`supabase migration repair`) |
| RD-C2 | **High for mixed-version accounts.** A pre-v10 client on the same account treats all of a category's budget rows as one budget: it shows the oldest row (maybe a month-only or onward amount), its edit changes that row's amount, and its duplicate-category fold can merge rows of different scopes. Upserts from it never clear the new columns | Design; confirmed by code review (QA 2026-11-03) | Owner decision 2026-11-03: document only — every phone on an account must run v10 before using month-only/onward amounts (release notes, docs/SYNC.md, PRODUCTION_MIGRATION.md) |
| RD-C3 | ~~Two-device budget lifecycle~~ **PASS** on two emulators against `lucento-test` (month-only change, onward change with confirmation, earlier months unchanged on both phones and the server) | `artifacts/budget-lifecycle/two-device-log.txt` | Closed |
| RD-A1 | 16 institution slots fall back (14 SVGs use mask/filter/pattern/text/symbol/switch; 1 invalid colour; 1 path over AAPT's 32 KB string limit) — card shows the monogram, list the app icon or monogram | `docs/design/INSTITUTION_ASSETS.md` | Ask the asset researcher for flattened SVGs, then rerun the pipeline |
| RD-A2 | Wallet rows truncate long names and subtitles at 200% font (pre-existing row layout; institution names make subtitles longer) | Emulator (`partA-8`) | Let the subtitle wrap to two lines in `WalletRow` |
| RD-A3 | Dark theme not checked on device for the avatar hairline; TalkBack reading of the merged card label not listened to (text verified in the UI tree) | Emulator only | Check in the next device pass |
| RD-A4 | `CardNetworkIndicator` not built: wallets store no card network, and the design rules forbid network marks on the card face | Design decision | Only if a network field is wanted (new column + migration) |
| RD-A5 | APK grows by about 1.47 MB compressed (logos 0.68, app icons 0.68, vector icons 0.10, registry 0.02) | Measured with aapt2 on the release APK | Acceptable; 96px icons would save about 0.25 MB if needed |
| RD-B1 | ~~"Restore a backup file" during onboarding not run end to end on a device~~ **PASS**: tested by the owner 2026-11-03 | Owner device test | Closed |
| RD-B2 | A backup taken mid-guide restores its guide step, so that user resumes the guided setup (their own saved state) rather than landing on Home | Design | Acceptable; revisit if confusing |
| RD-DE1 | ~~Spotlight, Getting started and "You’re ready" not seen on a device for a new user~~ **PASS** in the 2026-11-03 fresh-install pass (ring, spotlight, Getting started 2 of 3, You’re ready with 3 milestones, First Step after the first save) | `artifacts/fresh-install-2026-11-03/` | Closed |
| RD-DE2 | The Home budget card no longer has the mascot or its speech; the mascot remains on onboarding, empty states and the guide | Design decision (remove narrative) | — |
| RD-DE3 | Emulator system_server restarted several times during this pass (host memory, ENV-1); one Lucento DeadSystemException, caused by the system dying, no app crash | Crash buffer | Tooling |
| RD-E2-1 | Month-end balances leave out wallets created after that month and archived wallets; months before any wallet existed are not plotted (the history shows only when ≥ 2 months exist) | Design (`BalanceHistory`) | — |
| RD-E2-2 | Figma MCP rate limit (Starter plan): only the header and the balance card came with full design context; the budget card, shortcuts and lists were matched from frame metadata and the reference image | Tool limit | Re-check against Figma when more MCP calls are available |
| RD-E2-3 | ~~Balance card without chip~~ The owner asked for a more realistic card: the dashboard card now has a gold chip and a contactless glyph (decorative, hidden from TalkBack), as do wallet cards (decision D3) | Design decision | — |
| RD-E2-4 | Every APK install on emulator-5556 now triggers a system_server restart (host memory pressure, ENV-1); device screenshots for this pass were taken between restarts | Environment | Free host memory or use a real device |
| RD-QA-1 | ~~Category fold compared budget amounts across scopes (every-month amount could overwrite a November amount)~~ **Fixed**: fold key includes the scope | Unit (`SyncEngineTest` scope fold) | Closed |
| RD-QA-2 | ~~A pulled `institution_id` outside the client pattern hid the whole wallet~~ **Fixed**: read as none, wallet kept. Server CHECK stays length-only (1–128) | Unit (`SyncEngineTest`, `PulledRowRulesTest`) | Closed |
| RD-QA-3 | ~~Backup format still version 1 with new keys~~ **Fixed**: version 2 with a 1→2 step; older apps now say "from a newer Lucento" | Unit (`BackupTest`) | Closed |
| RD-QA-4 | ~~TalkBack read an unsigned amount on institution cards~~; ~~a month removed on its own said "has its own amount"~~ and "Remove from <month> on" was unreachable there; ~~guide edit could be hidden by a month-only amount~~; `setMonthAmount` outside the transaction runner; pulled rows didn't check single scope; sheet amount lost on rotation; "Getting Consistent" copy said spending — **all fixed** | Unit (`BudgetChangeTest`, `PulledRowRulesTest`) + code | Closed |
| RD-QA-5 | ~~"Vs last month" % counted a new wallet's opening balance as growth~~ **Fixed**: no % when a wallet in that currency was added in between | Unit (`BalanceHistoryTest`) | Closed |
| RD-QA-6 | Milestones baseline: if Home records the silent baseline before a sync download or restore lands, later milestones may celebrate data that arrived from the account. New users see their setup milestones on "You’re ready" (verified), not as toasts | Code-only (QA risk) | Baseline again after the first pull completes |
| RD-QA-7 | About 305 bank logos and 304 official app icons are bundled for display | `docs/design/INSTITUTION_ASSETS.md` | Trademark/licensing review skipped by owner decision 2026-11-03 |
| RD-QA-8 | Two phones stopping the same category from the same month: "a set amount beats 0" lets an older amount win over a newer stop | Code-only (QA risk) | Revisit the fold rule for recurring 0 rows |
| RD-QA-9 | ~~The guide's wallet list showed a card's amount owed unsigned ("Visa Card $300.00")~~ **Fixed**: signed (−$300.00) | Fresh-install pass; code | Closed (device recheck in the next pass) |
| SQ-ENV | Emulator ANRs ("failed to complete startup", launcher and permission controller) right after guest restarts on API 36.1 images; Lucento itself recovered every time | Verified (load average ~25 after boot) | Tooling (ENV-1) |

## Core screens refinement (2026-11-03, `feature/core-screens-refinement`)

| ID | Issue | Evidence | Next step |
|---|---|---|---|
| CS-1 | ~~`ScreenshotTourTest` had been failing since the Part B onboarding (waited for "Get started"; also stale "days left", "gently concerned", "Insights" / "Debts and loans" descriptions)~~ **Repaired** with this work | Passes end to end on the API 28 emulator (`artifacts/core-screens/tour-411dp/`) | Confirm in CI on the next push (API 30 emulator) |
| CS-2 | emulator-5556 (`Lucento_P0_QA_ReturningUser`, API 36.1) crash-looped `system_server` every 4–5 min (task-snapshot GPU readback abort, host 1.7 GB free): ENV-1 / RD-E2-4. It was **stopped** to free memory; its data is on disk and it was not restarted. Validation used a new AVD `Lucento_CoreScreens_API28` (API 28, local data only, never signed in) | Verified (logcat crash buffer) | Restart it when needed with `-gpu host`; free host memory first |
| CS-3 | Pre-existing: screen titles truncate at font 2.0 / 320dp ("Dashb…", "Transact…") in `HomeHeader` / `LucentoTopBar`; the debug TEST badge overlaps the navigation buttons at font 2.0 | Emulator (`w320-f20-*`) | P3: let titles shrink or wrap at very large text |
| CS-4 | Pre-existing nit: a sticky day header on Transactions lets the day card's side borders show beside it while scrolling | Emulator | P3 |
| CS-5 | Not validated on device: ≥ 600dp windows (`screenGutter()` 560dp column, three-column card grid), live TalkBack traversal of the new tiles/charts (descriptions verified in the UI tree only), a physical phone | — | Next device pass |
| CS-7 | **API 36 emulators unusable here** (owner's primary test target). A fresh `Lucento_Stepper_API36` (android-36.1 google_apis_playstore r4, emulator 37.2.12) aborts SurfaceFlinger's RegionSampling thread every 20–60 s (`Assertion failed: !rcEnc->featureInfo()->hasReadColorBufferDma`), also with `-gpu swiftshader_indirect` and `-feature -GLDMA,-GLDMA2`; installs and in-guest `screencap` trigger it too. Same root cause as ENV-1 / CS-2, not host memory (6 GB free) | Verified (crash buffer) | Owner: update the Android Emulator and/or the API 36 system image (or provide a working API 36 device); until then device checks run on API 28 |
| CS-8 | Home budget card is taller than before (ring as wide as the left half plus a seven-line formula, ~360dp on a 411dp phone) and the "To watch" rows are spread with generous gaps when fewer than four categories have spending; both follow the owner's sizing request | Emulator (`r2-home-card-*.png`) | Owner review; if too tall, cap the ring (e.g. 140dp) or hide the Budgeted/Unbudgeted lines when unbudgeted is zero |
| CS-6 | The trend line was checked on device only with a flat series (emulator clock moved forward two months on the test AVD); non-flat scaling is covered by `TrendHeightsTest` | Emulator + unit | Look at a real account with history |

## Open — P2 / P3

| ID | Issue | Evidence |
|---|---|---|
| N-06 | ~~No server CHECK constraints~~. **Done on `lucento-test` only** (22 NOT VALID constraints, live rows only); pulled *and pushed* rows are checked by `PulledRowRules`, so one invalid local row no longer blocks a batch. Production needs owner approval | Verified (test: invalid live row blocked, soft delete accepted, app write accepted) |
| N-08 | Production leaked-password protection disabled (advisor) | Verified |
| N-09 | `usage_events` accepts unlimited anonymous inserts; allowlist checks names only; `onboarding_completed` not allowed; dead event names remain | Code-only |
| N-10 | Only 3 entries in `strings.xml`; no plurals. **Partly done (P2-7):** every count string checked; the three that could say "1 rows" / "1 of 1 transactions" / "in 1 months" fixed; the rest were already guarded. Moving strings to resources (localization) is not started | Verified (code audit) |
| N-11 | ~~Home loading state is blank~~. **Fixed (P2-1):** placeholder shapes ("Loading your dashboard") | Code-only (build) |
| N-12 | 13 ViewModels observed the whole `transactions` table. **Partly done (P2-8):** one shared whole-table query per change for all screens (`TransactionRepository.shareScope`), and scoped queries for Settings (count), Home usage counts (count) and the transaction page (one row + transfer leg). Paging of long lists (Paging 3) and the release startup / baseline-profile measurement (N-13) remain | Verified (unit, device detail page) |
| N-13 | Cold start 3.6–3.8 s (debug emulator); release unmeasured | Verified (debug) |
| N-14 | ~~Budget hero vs. category rows don't reconcile~~. **D1:** the Budget summary now says "Spent = $X in your plan + $Y outside it" when there is outside-plan spending | Code-only (build) |
| N-15 | ~~Last-day pace message not useful~~. **Fixed:** on the last day the line says "Last day of <month>: $X left in your budget" | Verified (unit) |
| N-17 | ~~No app lock or recents privacy~~. **Done locally (P2-10, D8):** optional lock (fingerprint/face/screen lock, 60 s grace, turns itself off if the phone loses its screen lock) and Hide in recent apps (`FLAG_SECURE`) | Verified (emulator; recents blanking verified by the window flag only, since host screenshots bypass it) |
| N-20 | Backup Replace on a synced phone deletes cloud rows not in the file (documented) | Code-only |
| N-21 | ~~Disabled modules are hard to rediscover~~. **Fixed (P2-5):** Home's Quick links list modules that are off with an inline Turn on (also turns on what they need, e.g. Multiple wallets for Debts) | Verified (unit) |
| N-22 | ~~Duplicated controls and repeated privacy copy; Settings very long~~. **Fixed (P2-4):** Settings is a hub with pages (Account & sync, Money, Your data, Privacy & security, Enabled features); one theme control (profile sheet); "what leaves this phone" said once | Verified (device) |
| N-23 | ~~Sparse Quick links card, no recent activity on Home~~. **Fixed (P2-1, P2-5):** Recent activity card (3 newest, transfers once); Quick links also offer modules that are off | Verified (device) |
| N-19 | ~~Local toolchain drift~~. **Resolved:** the foojay plugin was not needed (no project toolchains; Gradle 8.11 can't auto-provision daemon JVMs) and is removed; README documents JDK 17–21 for Gradle (Android Studio's bundled JDK 25 fails with only `25.0.3`); `gradle/gradle-daemon-jvm.properties` is a per-machine choice and gitignored. CI stays on JDK 17 | Verified (local builds with and without the file; CI #108) |
| N-26 | Main-thread `runBlocking` wipe in `Application.onCreate`. **Closed — by design:** it runs only when an interrupted sign-out wipe is pending (`finishInterruptedWipeBeforeUi` checks `isPending` first), so no screen can show the previous account's ledger (R9-02); normal launches never block | Code-only |
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
