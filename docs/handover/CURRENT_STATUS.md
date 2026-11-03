# Lucento Android — Current Status

**Source:** `LUCENTO_FINAL_HANDOVER.pdf`, compiled October 30, 2026 (PT)

**Repository:** `jerald23simagala/lucento-android`

**Handover baseline:** `7928c63` (merge of PR #12)

**Current local branch:** `feature/ai-development-workflow`, based on `7928c63`

> This is a snapshot from the consolidated handover, not a fresh code audit. Verify all code and backend claims against the current checkout before acting.

## Update — 2026-11-01 (Claude), uncommitted on `feature/ai-development-workflow`

Verified this session unless noted. Details: `CLAUDE_INITIAL_AUDIT.md`, `P0_2_ONBOARDING_IMPLEMENTATION_REPORT.md`.

| Work | State | Evidence |
|---|---|---|
| **Owner review round (2026-11-03)**: Home order hero → shortcuts → Monthly budget. Budget card: ring as wide as the formula under it; visible formula Budget − Used (= Budgeted + Unbudgeted) = Remaining − Bills due = Safe to spend (tinted, signed; past months end at Remaining) from `HomeStateBuilder.budgetFormula`; "To watch" is a panel as tall as the left half (`MatchedHeightPair`), up to 4 rows with "$x left/over". Budget hero always shows "Spent = budgeted + unbudgeted". Guide budget step: category tile + name left, amount right, live "Monthly budget" total (`GuidedSetup.draftTotal`). Onboarding: icon tiles on step titles, theme icon tiles, summary card, restyled shared currency list (Popular / All, symbol tiles, one scrolling list with pinned search). CALCULATIONS.md: spent = budgeted + unbudgeted, Home formula row, guide total row | Implemented, uncommitted on `feature/core-screens-refinement` | 829/829 unit tests, lint 0 errors, `ScreenshotTourTest` PASS on API 28 (new shots 03b, 04c); device checks 411dp, 360dp @ 1.0/1.3/1.5, day + night (`artifacts/core-screens/review2/`, `r2-*.png`). API 36 still blocked (CS-7) |
| **Onboarding stepper (2026-11-03)**: `StageIndicator` rebuilt as a connected stepper (Personalize · Set up · Get started with Tune / Wallet / Sparkles icons that turn into checks; active stage on a pale-sky pill with a halo; connecting lines fill with in-stage progress; inline, labels-under or single-caption layout by measured fit). State from `SetupProgress` (`domain/`): current stage, furthest stage reached (onboarding `furthest_step` saved state; guide ViewModel), progress. Skip moved under Continue. One outlined icon family on focus options, currency/appearance, backup choices and summary rows | Implemented, uncommitted on `feature/core-screens-refinement` | `SetupProgressTest` (7); on API 28: tour PASS, forward/back/Get started states, 360dp@1.3, 320dp@2.0, night (`artifacts/onboarding-stepper/`). API 36 not usable here (KNOWN_ISSUES CS-7) |
| **Core screens refinement, implementation (2026-11-03)**: Dashboard, Transactions, Budget, Wallets per `docs/design/CORE_SCREENS_REFINEMENT.md` (§6 "As built" lists decisions D1–D3, spec corrections and deviations). Design system: `CardOverline`, `StatTile` / `HeroStatTile`, `ListSectionHeader`, `TrendLine`, `ViewToggle`, tonal `ChoicePill`, `screenGutter()` (560dp column ≥ 600dp), Bold section titles app-wide. Home: safe to spend (current month, budget basis), To watch from `plan.allocatedLines`, off-module tiles + sheet. Budget: "Safe per day" (= Home's safe to spend ÷ days left, D1). Wallets: 6-month trend, month-on-month pill (`comparableTrendPercent`), split bar, filters, card view persisted (`wallets_view_mode`). Google "G" button (D3). CALCULATIONS.md safe-to-spend wording corrected (code unchanged) | Implemented, uncommitted on `feature/core-screens-refinement` (from `feature/unified-redesign`) | See the final gate in NEXT_TASKS "Core screens refinement"; API 28 emulator checks (`artifacts/core-screens/`); emulator-5556 (API 36.1) unusable this session (ENV-1 crash loop) |
| **Core screens visual refinement, design phase (2026-11-03)**: high-fidelity designs for Dashboard, Transactions, Budget and Wallets (plus the Wallets card view, the night Dashboard and a shared-patterns sheet) on the Design canvas; implementation spec in `docs/design/CORE_SCREENS_REFINEMENT.md` | Design only; no app code changed; spec uncommitted on `feature/unified-redesign` | Canvas https://claude.ai/artifact/MocjXVBtQG5uW38yUQm9kE (private until shared); every figure maps to an existing calculation (`BudgetBurn`, `BalanceHistory.lastMonths`, `CashflowCalculator.safeToSpend`) |
| Initial audit and Part II extension | Done | `CLAUDE_INITIAL_AUDIT.md` |
| **Redesign close-out (2026-11-03)**: fresh-install pass, read-only QA review (no blocker; 1 High documented by owner decision, 2 Medium-High/Medium + 5 Low fixed), production migration runbook, final 23-item report | Committed on `feature/unified-redesign` (owner-authorized), not pushed. Production migrations applied by the owner 2026-11-03; backup-file restore verified by the owner; trademark review skipped (owner) | **784/784** unit tests, lint 0 errors / 9 warnings, debug + release built (release 51.10 MB, +2.03 MB vs 2026-11-01); fresh install on emulator-5556 (`artifacts/fresh-install-2026-11-03/`, 0 crashes); QA B restored by sign-in, fingerprints match; `PRODUCTION_MIGRATION.md` + `production-migration-2026-11-03.sql`; report `REDESIGN_FINAL_REPORT.md` |
| Dashboard to owner reference + Figma (header, sticky month row, Net Wallet Balance card, budget card with top categories, shortcuts, scheduled, recent; filtered category route) | Done locally, uncommitted on `feature/unified-redesign` | **779/779** unit tests; emulator screenshots (`artifacts/dashboard-figma/`) |
| Redesign Parts D + E: milestones, + spotlight, guide completion, dashboard redesign | Done locally, uncommitted | **775/775** unit tests; emulator Home + milestones (`artifacts/dashboard-redesign/`) |
| Redesign Part B: onboarding (workspace choice, 3 stages, mandatory focus, "How do you pay?", 3 backup paths, file restore) | Done locally, uncommitted | **767/767** unit tests; emulator fresh + returning paths (`artifacts/onboarding-redesign/`) |
| Wallet card look (owner direction D3): drawn chip, 22sp balance | Done locally, uncommitted | Emulator (`artifacts/wallet-identity/card-chip-*.png`) |
| Redesign Part A: wallet/banking identity (bundled registry + art, picker, institution card face, avatars, sync/backup) | Done locally, uncommitted | **762/762** unit tests, emulator: picker/search/preview/list/detail/liability/200% (`artifacts/wallet-identity/`), server `institution_id` verified on `lucento-test`, APK +1.47 MB measured |
| Redesign Part C: month-specific budgets (DB v10, Budget sheet scope choice + confirmation, sync/backup scope) | Done locally, uncommitted; migration on `lucento-test` (owner), production pending (RD-C1); two-emulator PASS | **755/755** unit tests, lint 0 errors, debug + release built, `check_migrations.py` 9→10 OK, MigrationTest 13/13 on emulator-5556, sheet checked on emulator without saving |
| `lucento-test` access via the claude.ai Supabase connector | Working; the connector currently sees only org "Lucento Test" (production no longer visible to it) | Verified (MCP) |
| P0-1: debug → `lucento-test`, release → production (`LUCENTO_DEBUG_SUPABASE_*` vs `LUCENTO_SUPABASE_*`; build fails if debug points at production) | Done locally | Verified (BuildConfig and APK dex) |
| P0-1.1: TEST badge in debug builds (`EnvironmentBadge`) | Done locally | Verified (emulator; absent from release dex) |
| P0-2: safe onboarding, cloud-data detection, duplicate prevention | Done locally, awaiting acceptance | See P0-2.1 row |
| P0-2.1: account detection adds transactions, schedules and budgets (restore rule aligned, archived wallets count); accurate combine-dialog wording; P1-8 budget-conflict task recorded | Done locally (2026-11-01) | **620/620** unit tests (+9), lint 0 errors, debug and release built, debug → test / release → production verified in APK dex |
| P0-3 global amount parsing: one rule set in `MoneyParser` (locale decimal trusted, the other mark validated as thousands or decimal by the currency's precision, `Ambiguous` / `WrongCurrency` results with one shared message); composer, AI and receipt paths aligned; `12,50` fixed everywhere | Done locally. **PASS** recommended | **671/671** unit tests, lint 0 errors, debug and release built and scanned, device checks (wallet, guide, composer, de-DE); QA: FAIL with 4 blockers → fixed → re-check PASS WITH NOTES; `P0_3_AMOUNT_PARSING_REPORT.md` |
| P0-2 final acceptance: F-7 fixed (the next account after a sign-out goes through onboarding), 2 restore tests, process death during a restore, release secret-key guard, F-8 investigation | Done locally. Recommended: **CLOSED WITH DOCUMENTED VALIDATION LIMITATIONS** (physical-device background restore deferred; Settings failure and empty-account switch automated only). F-8 is a separate release blocker; Account B session cleanup awaits authorization | Re-run with `--rerun-tasks`: **634/634** unit tests, lint 0 errors, debug and release built and scanned, release key guard failure cases executed; closure QA review: no blocking findings; `P0_2_FINAL_ACCEPTANCE_REPORT.md` |
| Stabilization cycle: CI #108 verified, toolchain, two-emulator sync QA (2 defects fixed: offline delete vs newer edit, duplicate budgets), P2-1/2/4/5/6 done, P2-7/P2-8 partial, P2-11 REST probe + pgTAP suite, emulator accessibility audit, P3 copy fixes, independent QA (no critical; high/medium fixed) | Done locally, uncommitted on `main` | **732/732** unit tests (final lint/build: see NEXT_TASKS); evidence in `artifacts/sync-qa/`, `artifacts/p2-ux/`, `artifacts/security/`, `artifacts/accessibility/` |
| Master backlog run: session coordinator (R10-01/N-03/N-04, offline revoke), P0-1 CI release config + debug AI URL, P1-5 transactional pull, P1-8 latest-edit budget fold, P2-9 constraints on `lucento-test` + pulled/pushed row checks, P1-7 large text/narrow width, P2-3 budget split, P2-10 app lock + hide in recents, composer picker fix, QA notes 1–4 and 9–12 | Done locally; QA review **PASS WITH NOTES** | **700/700** unit tests, lint 0 errors, debug and release built, release permissions within the allow-list, no server keys in dex; device evidence in `artifacts/p0-session-revoke/`, `artifacts/p1-7-accessibility/`, `artifacts/p2-10-app-lock/`. Status table and decision register: `NEXT_TASKS.md` |
| P0-2 device QA on two new API 36 emulators against `lucento-test` | 12 of 13 onboarding scenarios PASS (O-10 added in the follow-up), O-13 FAIL (known N-02); E-1 and E-2 PASS; Settings combine flow PASS including both consent-bypass paths; pre-existing QA data fingerprints unchanged (re-checked). New findings F-7 and F-8 | `P0_2_DEVICE_QA_RESULTS.md`, evidence in `artifacts/p0-2-device-qa/` |

Not committed, pushed or merged. Recovery stash intact. Debug builds now sync only with `lucento-test`. QA accounts on
`lucento-test`: A `211a8bb2` (holds QA data, snapshot A2, plus one master-run check row "P29 check coffee" −$3.25) and B
`ae2e7a97` (confirmed; 18 categories from E-2; sessions signed out with approval). New AVDs
`Lucento_P0_QA_NewUser` and `Lucento_P0_QA_ReturningUser` exist; run them with `-gpu host` and 3-button navigation
(emulator GPU bug, see the QA results §1).

## 1. Executive status

The report records 585 passing `testDebugUnitTest` tests and a final QA device run passing selected sign-out, session-upgrade, upload-on-write, launch-sync, category-deduplication, backup-merge, and UI-baseline checks. These checks used **debug builds against `lucento-test`**, not the live Supabase project.

The release application (`app.lucento`) had **not** been validated against the live `lucento` project. Several device workflows remained untested. Open risks include interrupted sign-out/server-token revocation, offline sign-out, large-font and narrow-screen layout, first-sign-in data preservation, and unverified accessibility/device workflows.

## 2. Stack and architecture recorded in the report

- Native Android: Kotlin, Jetpack Compose, Material 3.
- Minimum SDK 26; compile/target SDK 35; JDK 17; Gradle 8.11.1.
- Room v10 (`MIGRATION_9_10`: `budgets.month`, `budgets.effective_from`, `wallets.institution_id`; redesign Part C/A).
- `supabase-kt` 3.1.4 (PostgREST and Auth). The report recommends pinning this version because session-key derivation changed during earlier work.
- Room/local persistence; Supabase sync; WorkManager; tombstones; last-write-wins timestamps.
- WorkManager: periodic sync every 30 minutes (`KEEP`); one-off manual sync for Sync now and launch; upload-after-local-write uses `APPEND_OR_REPLACE` and requires network.
- Integrations: Supabase Auth/PostgREST; direct GoTrue logout POST; Gemini interpret endpoint (opt-in); Crashlytics (opt-in); HIBP k-anonymity check; speech features.
- Live Supabase project: `lucento`, ref `hmtcuttadbgbldvzsfhz`, Seoul region, Free plan.
- Test Supabase project: `lucento-test`, ref `ogenvdopfgocjdkcrsny`.

Never put credentials, tokens, service-role keys, or passwords in source control, prompts, logs, or APKs.

## 3. Changes reported as merged by `7928c63`

| PR | Merge | Reported change |
|---|---|---|
| #4 | `988c79c` | Release-blocker fixes: days-left calculation, goal-contribution delete confirmation, recurring cap removal, sign-out wipe/unbind, data-retention notices, parser cues, amount-field select-all, Crashlytics opt-in, HIBP check. |
| #5 | `709d29b` | First-run redesign and related work; UI redesign later reverted, non-UI work retained as described. |
| #9 | `6a80c45` | Upload-on-local-write via `LocalWriteUploads.kt`, WorkManager scheduling, application hook. |
| #13 | `1974727` | Hand-merged UI restoration to `988c79c`, retaining non-UI work and usage-counts switch. |
| #7 | `ce6dc5d` | Durable session store, session-key migration, wipe marker/order, sign-out handling. |
| #11 | `7783777` | One-off launch sync when signed in with sync enabled. |
| #6 | `16f7345` | JSON backup/restore, Merge/Replace preview, settings backup section, DAO queries, related fixes. |
| #12 | `7928c63` | Pull-first first sign-in; remove never-synced unused local categories except kinds missing from account. |

The report states the net diff from `988c79c` to `7928c63` was 88 files, +6,495/-112 lines, with no Room schema or Supabase migration changes after the earlier baseline.

## 4. UI/UX baseline and current authority

The report identifies `988c79c` (shown in PR #7 as `cce97c0`) as the restored visual baseline after PR #5's UI redesign was reverted by PR #13.

**Policy precedence:** The PDF's historical “no UI/UX changes without approval” restriction is superseded by the owner's later explicit direction and current `CLAUDE.md`. Claude has broad creative authority to critique and improve screens, onboarding, navigation, information architecture, components, and interactions. Existing screens, screenshots, and `988c79c` are reference material, not immutable constraints.

Keep product coherence and functional completeness. Preserve engineering, financial-data, privacy, security, and offline-first requirements. Surface high-impact product-scope or core financial-concept decisions rather than making irreversible changes casually.

## 5. QA evidence recorded at `7928c63`

Final QA used emulators on API 36 and API 28, a debug build, and `lucento-test`.

Reported passed:
- 585 `testDebugUnitTest` tests.
- APK dex contained only the test project reference.
- Schema/migration diff was empty.
- Session upgrade (R6-13).
- Upload-on-write (R6-03).
- Launch sync without overlapping workers (R6-11).
- Sign-out kill safety (R6-01, twice).
- R9-01 timing checks at 150/400/600 ms.
- Restore after wipe (R6-15).
- First-sign-in category handling (R6-14).
- Backup merge (R6-02).
- Online sign-out deleted server session.
- UI comparison against `cce97c0`, with expected Settings differences for usage switch, footer, and backup section.

Earlier Phase 2 and R1–R5 checks were performed on earlier PR heads and were not rerun on `7928c63`. Some results were code-only or inferred, not device-verified. See `KNOWN_ISSUES.md`.

## 6. Current local workflow state

At handover preparation:
- Local branch: `feature/ai-development-workflow`.
- Branch base and `origin/main`: `7928c63`.
- Local workflow changes include `CLAUDE.md`, `.gitignore`, `.claude/`, `artifacts/`, `docs/handover/`, and `docs/ui-ux-audit/`.
- Recovery stash `backup-before-sync-origin-main-2026-10-30` is retained.
- `.claude/agents/lucento-architect.md` and `.claude/agents/lucento-qa.md` are read-only specialist agents. Main Claude Code session owns implementation.
- `.claude/launch.json` contains a local website dev configuration; preserve unless owner requests a change.

Do not drop the recovery stash, commit, push, merge, delete branches, or modify live Supabase data without explicit authorization.

## 7. Operating principles

1. Read `CLAUDE.md` before making changes.
2. Inspect actual code at current HEAD before accepting this report's claims.
3. Preserve evidence labels: Verified, Code-only, Inferred, Unverified.
4. Store money as integer minor units (`Long`) with ISO 4217 currency; no floating-point money arithmetic or implicit currency conversion.
5. Preserve offline-first behavior, Room migration safety, sync correctness, security, and privacy.
6. Never commit secrets or local credentials; `google-services.json` is not in the repo by design.
7. Make focused, testable changes and report exactly which tests were and were not executed.
8. Use Architect and QA agents for independent read-only reviews when useful.
