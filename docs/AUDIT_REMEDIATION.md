# Audit remediation — October 2026

Two independent audits of `feature/dashboard-refresh` @ `acf0c37` were checked against the source, then fixed in
priority order. **UX** = the UI/UX audit (72/100); **CTO** = the CTO/architecture/QA audit ("pre-beta, no P0").
Both audits ran without an Android SDK; this pass ran the real Gradle build and test suite, and read-only queries
against the live Supabase project.

> **27 Oct 2026 — UI/UX changes reverted at the owner's request.** Every screen, copy and UI-test change from this
> pass was restored to `acf0c37`. What remains: `BudgetBurn.daysLeft` (domain only, not yet shown on any screen) with
> its unit test, the unapplied Supabase privilege migration, and this register.

Status key: **Fixed ✓** (fixed and verified by tests/build) · **Fixed** (fixed, not verified on a device) ·
**Partial** · **Deferred** · **Rejected** (with rationale) · **Decision** (needs the product owner).

## Baseline (before changes)

- `./gradlew testDebugUnitTest` on the Windows dev machine (JBR 21, `-Xmx2g`; the default `-Xmx4g` crashed the JVM
  with OOM on this 16 GB machine — see the stray `hs_err_pid*.log`): **446 tests, 0 failures**, sync and capture
  included. So CTO ARCH-001 ("sync/AI tests don't run") applies only to the `tools/jvm-harness` fallback.
- Live Supabase (`lucento`, read-only queries): RLS on all 8 tables; every policy is `auth.uid() = user_id` with a
  `with check` on insert/update; `anon` has no table grants. Advisors: `consume_ai_quota` is a SECURITY DEFINER
  callable by `authenticated` (intended — it is the quota RPC), leaked-password protection is off.

## Consolidated register

| ID | Source | Finding (evidence) | Verified? | Pri | Resolution | Acceptance / tests | Effort | Status |
|---|---|---|---|---|---|---|---|---|
| F-01 | UX UI-003 | Home/Budget show `daysInMonth − daysElapsed` days left, but the daily allowance divides by that **+1** (today included). Last day of month shows "0 days left" with a full-day allowance (`BudgetPlan.kt:262`, `HomeScreen.kt:549`, `BudgetScreen.kt:371`, `HomeState.kt:122`). | Confirmed | **P0** (wrong financial figure) | One `BudgetBurn.daysLeft` (today inclusive) used by the allowance and every label. | daysLeft = L−D+1 = allowance denominator; tests mid-month, last day, past, future month. | S | Partial: `BudgetBurn.daysLeft` + tests in domain; screens still show the old count (UI change reverted) |
| F-02 | CTO SEC-002 | Live RLS not verified by the auditor. | Verified live (read-only) | P1→closed | Policies and grants checked live (above). | — | — | Fixed ✓ (verified) |
| F-03 | new (this pass) | Live `authenticated` holds `TRUNCATE`/`TRIGGER`/`REFERENCES` on user tables (Supabase default privileges; the repo migration only intends CRUD). `TRUNCATE` ignores RLS. Not reachable through PostgREST today. | Confirmed live | P2 hardening | New migration revoking the extra privileges. **Not applied** to the live project (needs owner approval). | After applying: grants query shows only `DELETE,INSERT,SELECT,UPDATE`. | S | Partial (file written, not applied) |
| F-04 | CTO SEC-001 + SYNC-001 | Disconnect keeps the full ledger and the device stays bound to the first synced account; a second account gets "AccountMismatch" until app data is cleared (`SettingsViewModel.disconnectAccount`, `SyncPreferences`). | Confirmed | P1 | Disconnect now confirms and states plainly that data stays on the phone. A local **erase-and-unbind** flow conflicts with the CLAUDE.md rule "signing out never deletes local data" and is irreversible, so it waits for a product decision. | Dialog copy; no data touched. | M | Reverted (owner: no UI/UX changes) |
| F-05 | CTO REL-001 | No crash/ANR reporting. | Confirmed | P1 for public launch | Needs a vendor choice, a privacy-policy update and a network opt-in (CLAUDE.md: any non-sync network use needs its own consent). Not added. | — | M | Decision |
| F-06 | CTO REL-002 | Release signing / Google OAuth E2E environment-dependent. | Confirmed | P1 for Play upload | Owner action (secrets, SHA-1 in Google Cloud). | — | — | Deferred (owner) |
| F-07 | CTO QA-001 | Critical finance journeys not automated end-to-end. | Partly: JVM coverage is broad (446 tests incl. HomeStateBuilder, BudgetPlan, MoneyFlowAudit, sync engine) | P1 | Added day-boundary pace tests; emulator journey tests deferred. | — | L | Partial |
| F-08 | CTO ARCH-001 | `tools/jvm-harness` drifted (sync/AI don't compile there). | Confirmed by auditor; Gradle runs all of them locally | P2 | Gradle is the authority (CLAUDE.md already says the harness is a fallback). | — | S | Deferred |
| F-09 | UX UI-006 | Disabled "Notifications, coming soon" bell in the Home header — a control that does nothing (CLAUDE.md: no buttons that do nothing). | Confirmed | P1 | Removed. | Header: brand, sync chip, profile. | S | Reverted (owner: no UI/UX changes) |
| F-10 | UX UI-004 | Home loading state is a 1dp spacer (blank screen). | Confirmed | P1 | `LucentoWorkingIndicator` while loading. | Non-blank loading. | S | Reverted (owner: no UI/UX changes) |
| F-11 | UX UI-007 | Advice banner `maxLines = 1` ellipsizes the verdict and the daily allowance (font scale, long currencies). | Confirmed | P1 | Up to 2 lines each, wrap instead of ellipsis. | Allowance readable at 200% font. | S | Reverted (owner: no UI/UX changes) |
| F-12 | UX UI-005 | Home net worth: assets and liabilities only in the content description. | Confirmed | P1 | Assets and debts amounts shown under net worth (signed, tabular). | Visible A/L. | S | Reverted (owner: no UI/UX changes) |
| F-13 | UX UI-010 | PercentBar prints white labels on any fill (light/translucent fills → low contrast). | Confirmed | P2 | Label ink chosen from the fill's composited luminance. | Contrast in both themes. | S | Reverted (owner: no UI/UX changes) |
| F-14 | UX UI-009 (speech) | Speech recogniser forced to English, not disclosed. | Confirmed | P1 | Prompt and notice say "English for now". | — | S | Reverted (owner: no UI/UX changes) |
| F-15 | UX UI-009 (confidence) | "Binary confidence" in the composer. | Partly inaccurate: rows already separate blocking issues (red border, error line) from advisories (info line) | P3 | No new chip system. | — | — | Rejected (already present) |
| F-16 | UX UI-008 | README/DESIGN.md describe a Budgets dock; the app (and CLAUDE.md) ship Wallets in the dock. Home still says budgets "are on the Budgets tab". | Confirmed | P1 | Docs and copy aligned to the shipped dock. | Docs match dock. | S | Reverted (owner: no UI/UX changes) |
| F-17 | UX UI-001 | Budgets not in the dock. | Conflicts with a deliberate decision in `acf0c37` / CLAUDE.md (Wallets in dock; Budgets one tap from Home via the pace card and Quick links) | — | Not changed. | — | — | Decision |
| F-18 | UX UI-002 | `strings.xml` holds only `app_name`; copy is hardcoded. | Confirmed | P2 (P0 only if non-English launch locales) | Extraction is a multi-day sweep touching every screen and many tests; launch locales are undecided. | — | XL | Deferred |
| F-19 | CTO DATA-001 | LWW sync can drop the losing concurrent amount edit. | By design; conflicts are counted and reported | P2 | Document; merge UI later. | — | L | Deferred |
| F-20 | CTO ARCH-002 / PERF-001/002 | Oversized screens (HomeScreen 1151 LOC), unmeasured recomposition/paging. | Confirmed size; perf unmeasured | P3 | Refactor without measured benefit — not now. | — | L | Deferred |
| F-21 | Advisor | Leaked-password protection disabled (Supabase Auth). | Confirmed live | P2 | Dashboard toggle (owner). | — | S | Deferred (owner) |

## Conflicts resolved

- **Dock (UI-001 vs shipped IA).** The UX audit asks for Budgets back in the dock; the latest commit and CLAUDE.md
  deliberately chose Wallets. Changing the product's primary navigation is the owner's call, so only the documentation
  drift (UI-008) was fixed.
- **Shared-device privacy (SEC-001) vs "signing out never deletes local data".** A wipe option would reverse a
  documented rule and is irreversible; the safe, rule-compatible step (honest confirmation) shipped, the wipe waits.
- **Crash reporting (REL-001) vs privacy rules.** Any SDK is a new network path and dependency; needs consent design.

## Roadmap (execution order)

1. **A — financial correctness:** F-01. Security verification F-02, hardening file F-03.
2. **B — reliability:** F-04 (confirmation), F-07 tests.
3. **C — core UX:** F-09, F-10, F-11, F-12, F-14, F-16.
4. **D — polish:** F-13.
5. **E — verification:** `testDebugUnitTest`, `assembleDebug`, `lintDebug`.
6. **Next (decisions):** F-04 erase-and-unbind, F-05 crash reporting, F-17 dock, F-18 locales, apply F-03.
