# Lucento — Security remediation report

**Date:** 2026-11-10 · **Engineer:** Claude · **Source:** [FINAL_SECURITY_AUDIT.md](FINAL_SECURITY_AUDIT.md) (2026-11-09)
**Branches (local, committed, not pushed):** `lucento-android` `security/release-remediation` (from `origin/main`
`5d665d8`); `lucento-website` `security/release-hardening` (from `origin/main` `90566a8`).
**Owner steps:** [OWNER_ACTIONS_REQUIRED.md](OWNER_ACTIONS_REQUIRED.md) · **Checklist:** [RELEASE_READINESS_CHECKLIST.md](RELEASE_READINESS_CHECKLIST.md)

**Update 2026-11-10 (later):** the owner applied the four migrations to **production** (SQL Editor) after two GRANT
rows were added for the two accounts that synced without a plan; Claude's read-only checks all passed (checklist A1).
Branches pushed for review. lucento-test is not migrated yet. Nothing has been bought.

---

## 1. Summary

| ID | Severity | Status | Where |
|---|---|---|---|
| F-01 | High | **Fixed in code, tested offline and on real Postgres. Not applied to any project.** | migration `20261111090000`, app sync |
| F-02 | Medium | **Fixed, tested offline. Not applied.** | migration `20261111100000` |
| F-03 | Medium | **Fixed (server + app), tested offline. Not applied.** | migration `20261111100000`, app billing, website |
| F-04 | Medium | **Fixed**: Astro 7.4.8, `@astrojs/vercel` 11.0.20; 0 high/critical in production deps. Not deployed. | website |
| F-05 | Medium | **Owner action** (§1) | Google Cloud |
| F-06 | Medium | **Owner action** (§2–§4) | Supabase Auth |
| F-07 | Low | **Fixed** (server guard). Not applied. | migration `20261111110000` |
| F-08 | Low | **Fixed**, unit-tested; live check after deploy | website |
| F-09 | Low | **Fixed**, e2e-tested on the built site with the real headers | website |
| F-10 | Low | **Prepared**: OIDC verification built and tested, off until the owner configures it (§12) | website |
| F-11 | Low | **Fixed in part**: per-account verify quota (DB) + method guard; IP limits are an owner action (§7) | migration `20261111120000`, website |
| F-12 | Low | **Fixed**: test purchases refused unless allowed, and marked | migration `20261111100000`, website, app |
| F-13 | Low | **Fixed**, tested under both privilege modes. Not applied. | migration `20261111110000` |
| F-14 | Low | **Investigated**: all 23 migrations present in production; manifest + reconciliation SQL ready (owner) | [MIGRATION_MANIFEST_2026-11-10.md](MIGRATION_MANIFEST_2026-11-10.md) |
| F-15 | Low | **Fixed**: SHA-pinned actions, Dependabot ×2, website CI, SQL job in Android CI; Android OSV scan done | both repos |
| F-16 | Low | **Evaluated, not changed** (§3.16) — product decision | — |
| F-17 | Low | **Deferred to after release** (§3.17) | — |

**Tests, all executed on 2026-11-10 on this machine:**

| Suite | Result |
|---|---|
| `tools/sql-verify` `npm run verify` (7 suites, every migration replayed per suite, lucento-test privileges) | **258/258 passed** (180 existing + 78 new) |
| `tools/sql-verify` `npm run verify:stock` (Supabase stock privileges) | **258/258 passed** |
| `tools/sql-verify` `npm run concurrency` (embedded Postgres 17.6, real sessions) | **4/4 passed**, three runs in a row |
| Android `./gradlew testDebugUnitTest` | **1,253 tests, 0 failures** (15 new) |
| Android `assembleDebug`, `assembleRelease` | **passed**; release APK inspected (§5.2) |
| Android `lintDebug` | 1 error in this PC's untracked `local.properties` only; none in changed code (§5.2) |
| Android dependency scan (OSV, release classpath) | **248 packages, 0 known vulnerabilities** (§5.3) |
| Website `npm run verify` (tokens, ESLint, `astro check`, unit tests, build) | **passed**; 0 errors, 0 warnings; **44/44** unit tests (13 new) |
| Website `npx playwright test` | **55/55 passed** (16 new) |
| Website `npm audit --omit=dev --audit-level=high` | **exit 0** (was 1 critical, 6 high) |

---

## 2. What changed, per finding

### F-01 — Unbounded writes; Cloud plan not enforced (High)

**Root cause.** The sync tables' INSERT/UPDATE policies checked only `auth.uid() = user_id`; the plan lived only in the app
(`ProGates.syncAllowedByPlan`). Nothing bounded row size or count, and `usage_events` accepted `WITH CHECK (true)` inserts
from `anon`.

**Fix — `supabase/migrations/20261111090000_cloud_write_gate_and_quotas.sql`:**
- `lucento_private.caller_has_cloud()` and `lucento_private.cloud_partner_spaces()` (SECURITY DEFINER, empty
  `search_path`, answer only about the caller). The Owner of a Partner's space comes from `public.space_members`
  (written only by the invitation RPCs), never from an argument.
- INSERT/UPDATE policies of `wallets`, `categories`, `transactions`, `budgets`, `goals`, `goal_contributions`,
  `recurring`, `ledger_settings` require CLOUD in `WITH CHECK` (USING unchanged, so a refused update is a 42501 error,
  not a silent no-op). Partner policies on `transactions` require the Owner's CLOUD, or the lenient switch
  `plan_limits.partner_sync_after_owner_lapse = 1` (default — see §4, decision D-1). SELECT and DELETE untouched.
- CHECK constraints on live rows (NOT VALID): notes ≤ 500, names ≤ 100, ids ≤ 128, short codes ≤ 64.
- Row ceilings: `lucento_private.ledger_row_counts` (backfilled from existing rows), maintained by statement-level
  AFTER INSERT/DELETE triggers with transition tables. The increment is `INSERT … ON CONFLICT DO UPDATE … RETURNING`,
  which holds the account's counter-row lock until commit, so concurrent writers queue and can't both pass the ceiling
  (no COUNT(*) race; O(1) per statement). Limits in `plan_limits` (`max_rows_transactions` 200,000 etc.). A batch cap of
  1,000 rows per client statement (trigger depth 1 only, so cascades of account deletion are never capped).
- `usage_events`: anon/authenticated INSERT grant and the `WITH CHECK (true)` policy removed.
  `public.record_usage_event(...)` (SECURITY DEFINER) stores a count only under a per-install daily cap (50) **and** a
  project-wide daily cap (20,000), so inventing install ids can't raise the total. Over-cap counts are dropped quietly
  (`false`), never an error the app retries.

**App — 42501 handling** (`app/.../data/sync/`): `SupabaseSyncRemote` turns 42501 and LU001 into
`UploadRefusedException`; `SyncRepository` stops uploading for the cycle, still pulls, keeps every refused row and
tombstone pending, stores and shows a plain message, and returns `SyncResult.UploadsPaused`. It waits 30 minutes before
trying again **unless the plan changes** (`ProGates.cloudPlanFingerprint`), so a renewal resumes at once and an
unchanged plan is never retried in a loop. `SyncWorker` answers `success()` (never `retry()`), Settings and onboarding's
restore handle the new result, and the app re-reads its plan when a refusal first happens. Uploads go in chunks of 500.
`PulledRowRules.uploadProblem` holds back a row that breaks a server bound (it stays pending alone). Usage counts call
`record_usage_event` (`SupabaseUsageRemote`).

**Tests added:** `security_remediation_suite.sql` G1–G27, B1–B6, Q1–Q9, U1–U7, X8a, X8a-pro, X8b;
`concurrency.mjs` C1–C4; `UploadRefusalTest` (8): refusal keeps changes and explains, no retry under the same plan,
renewal resumes, lapsed account pulls, refused deletion stays queued, storage ceiling, chunking 500/500/203, oversize
row held back; `PurchaseAccountBindingTest` fingerprint test.

**Remaining risk.** A paying account can still store up to its ceilings (~200k transactions ≈ 100–150 MB at the largest
bounds); with production on the Free plan, a handful of abusive *paying* accounts could still fill 500 MB → owner §5/§6.
An app build from before this change uploads unchunked and inserts usage events directly; only internal testers have one.

### F-02 — Refund revived after account deletion (Medium)

**Root cause.** "Refunds stay final" read only `public.entitlements`, which cascades with the account.
**Fix — `20261111100000_play_purchase_integrity.sql`:** `lucento_private.revoked_purchases(purchase_token_hash pk,
revoked_at)` — no account column, no personal data, no cascade; backfilled from existing REVOKED rows.
`revoke_play_purchase` writes it (also for a purchase no account has verified yet); `apply_play_purchase` reads it and
stores REVOKED. **Tests:** P1–P5, X1 (refund → delete account → new account submits token → REVOKED, no capability).
**Remaining:** depends on RTDN/voided reaching the server once; the real refund path is unverified (owner §11).

### F-03 — First verification ignored the obfuscated account id (Medium)

**Root cause.** The first-link branch inserted a link without comparing Play's `obfuscatedExternalAccountId`.
**Fix:** `apply_play_purchase` answers `other_account` (and links/writes nothing) whenever Play's id is present and isn't
`sha256(lower(p_user))`, first link included; malformed ids are refused (22023). Signed-out purchases (no id) keep the
documented policy: the first verifying account. Website: `409 other_account`; RTDN ignores it. App: `PurchaseRecord`
carries Play's `accountIdentifiers.obfuscatedAccountId`; `EntitlementManager` never sends a purchase made for another
account, shows `VerifyOutcome.OTHER_ACCOUNT` with its own copy (`ProCopy`), and still acknowledges it (it's the buyer's
real purchase). **Tests:** X2, P6–P11; `PurchaseAccountBindingTest` (7). **Remaining:** the 30-day re-link rule is
unchanged; it is now reachable only for links made before this fix (none exist in production: no real purchase yet).

### F-04 — Website framework advisories (Medium)

**Fix:** `astro` 5.18.2 → **7.4.8**, `@astrojs/vercel` 8.2.11 → **11.0.20**, `@astrojs/check` → 0.9.12, sitemap 3.7.4
(current), all pinned exactly; `engines.node` ≥ 22.23.4 (Astro 7's floor). `path-to-regexp` overridden to 6.3.0 under
`@vercel/routing-utils` (same major; fixes GHSA-9wv6-86v2-598j). The site uses no `astro:assets` images, so the image
service is now the pass-through one: sharp/libvips left the server function (23 MB → 3 MB), which removes the RCE class
(GHSA-26w7-cxv4-gfx2) from the request path entirely. No code change was needed for Astro 6/7; every API route,
redirect and page builds, and the full e2e suite passes.
**Remaining advisories (all moderate, none on the request path):** `glob` 13.0.7 / `minimatch` 10.2.6 via `@vercel/nft`
(the adapter's build-time file tracer) — fixed only in their next majors; and `minimatch` in ESLint/typescript-eslint
(dev only). npm's suggested "fix" downgrades the adapter to 8.1.0 and was rejected. Dependabot will raise the adapter when
it moves.

### F-07 — Seed category id squatting (Low)
`categories_seed_id_owner` CHECK: a `seed-…` id is accepted only as `seed-<own user id>-…`. App ids unchanged, so no
client or Room migration. **Tests:** X3, S1, S2; app `uploadProblem` mirrors it. Squats from before: owner §14 query.

### F-08 — OPTIONS crashed the functions (Low)
`src/server/http.ts` `methodGuard`: every route exports `ALL`; OPTIONS → 204 with `Allow`, any other method → 405 with
`Allow`; never an `Access-Control-*` header. **Tests:** `http.test.ts` (2), `e2e/api.spec.ts` (405s, no CORS grant,
refusal without credentials; 19 tests). `astro dev` answers every OPTIONS itself (Vite's CORS middleware), so the live
204 is a post-deploy check (owner §14).

### F-09 — No browser security headers (Low)
`vercel.json`: nosniff, `Referrer-Policy`, `X-Frame-Options: DENY`, COOP, `Permissions-Policy`, and a CSP with
`default-src 'self'`, `object-src 'none'`, `base-uri`, `form-action`, `frame-src 'none'`, `frame-ancestors 'none'`;
`/api/*` gets `default-src 'none'` and `no-store`. Scripts are strict per page: the post-build step
(`scripts/csp.mjs`) writes a `<meta>` CSP with the sha256 of each inline script and handler, so no injected script runs;
browsers enforce both policies. Styles keep `'unsafe-inline'` (CSS custom properties in `style` attributes; no user
content). **Tests:** `scripts/csp.test.mjs` (4); `e2e/security.spec.ts` (8) serves the built output with the real headers
(`scripts/serve-static.mjs`): six pages, zero CSP violations, hashed handlers run, an injected script is refused, headers
present. **Note:** Vercel's preview toolbar (preview deployments only) will be blocked by the CSP.

### F-10 — Static Pub/Sub token (Low) — prepared
`src/server/pubsub-auth.ts` verifies Google's OIDC JWT: RS256 against Google's JWKS by `kid`, issuer, audience, exp/iat
(60 s skew), service-account email + `email_verified`. `rtdn.ts` requires it once `LUCENTO_RTDN_OIDC_AUDIENCE` and
`LUCENTO_RTDN_OIDC_SERVICE_ACCOUNT` are set, and keeps requiring the query token while it is set (two factors during the
switch). Off today, so nothing changes until the owner configures Pub/Sub (§12). **Tests:** `pubsub-auth.test.ts` (4).

### F-11 — No request limits (Low) — partly fixed
`20261111120000_request_quotas.sql`: `public.consume_request_quota('billing_verify')` (authenticated, `auth.uid()`),
60/account/day. `/api/billing/verify` calls it with the person's own token before Play is asked → `429 rate_limited`;
fails open if the counter is unreachable (a payer is never blocked by the counter). AI endpoints were already bounded per
account (`consume_ai_request`). Per account, not per IP, so shared networks aren't penalised. **Tests:** R1–R4,
`quota.test.ts` (3). **Remaining:** unauthenticated floods → Vercel Firewall rule (owner §7); a new account regains the
Free AI allowance (accepted, Low).

### F-12 — Test purchases granted real Pro (Low)
`apply_play_purchase(..., p_test_purchase default false)`: refused (`test_purchase_refused`) unless
`plan_limits.allow_test_purchases = 1` (default 0 on every project); granted rows carry `entitlements.test_purchase`.
Website passes Play's flag, answers 403; app shows `TEST_PURCHASE_REFUSED`. **Tests:** T1, T2, verifier mapping.
**Owner:** turn it on for internal testing and off (with the cleanup SQL) before launch (§8).

### F-13 — Default privileges (Low)
`alter default privileges for role postgres in schema public revoke all on tables / sequences / functions from anon,
authenticated`, plus the **global** `revoke execute on functions from public` (a per-schema entry can only add to
PostgreSQL's global default — found by check D2). Existing objects keep their grants. **Tests:** D1–D4 (a new table and
function after the migration grant nothing; `pg_default_acl` clean; existing grants intact).

### F-14 — Migration history drift (Low)
History: 11 rows, 3 under run-time versions. Catalog: a marker of each of the 23 repository migrations is present; the 4
new ones aren't. [MIGRATION_MANIFEST_2026-11-10.md](MIGRATION_MANIFEST_2026-11-10.md) has the evidence and a bookkeeping
back-fill (15 rows, no schema change) for the owner. Nothing was written.

### F-15 — Supply chain (Low)
Android CI: every action pinned by commit SHA (`checkout`, `setup-java`, `gradle/actions`, `upload-artifact`,
`android-emulator-runner`, `setup-node`); new job `sql-verify` (both privilege modes + concurrency on a Postgres 17
service); `.github/dependabot.yml` (Gradle grouped so the Kotlin toolchain moves together, Actions, npm). `tools/sql-verify`
lockfile now committed (was ignored) for `npm ci`. Website: new `.github/workflows/ci.yml` (`npm ci`, `npm run verify`,
`npm audit --omit=dev --audit-level=high`, Playwright), `.github/dependabot.yml`. Android dependency scan: §5.3.
Gradle dependency verification (`verification-metadata.xml`) not enabled: it needs a full `--write-verification-metadata`
run per dependency change and breaks Dependabot PRs until regenerated — recommended as a separate change.

### 3.16 F-16 — Hide in recents (evaluated)
`FLAG_SECURE` by default would block every screenshot (people share budgets and report bugs with them), screen readers'
screen-recognition features and the CI screenshot tour. The setting exists (Settings › Privacy). Recommendation: offer
it in onboarding's privacy step in a UX pass, not as a silent default. No change made.

### 3.17 F-17 — Encrypted backups (deferred)
Design for after release: an optional password on the JSON backup, AES-256-GCM with a PBKDF2-HMAC-SHA256 key
(≥ 600,000 iterations, random 16-byte salt and 12-byte nonce in a versioned header), `javax.crypto` only. It touches the
backup format, restore and Settings UI, so it isn't worth the release risk now. Data stays in the app sandbox with
backup off (the audit's assessment).

---

## 3. Files changed

**lucento-android:** `supabase/migrations/20261111090000_cloud_write_gate_and_quotas.sql`,
`20261111100000_play_purchase_integrity.sql`, `20261111110000_seed_ids_and_default_privileges.sql`,
`20261111120000_request_quotas.sql` (new); `tools/sql-verify/` `run.mjs` (all suites, fresh DB each), `standin.mjs`,
`concurrency.mjs`, `security_remediation_suite.sql` (new), `account_deletion_suite.sql` (fixtures hold CLOUD; D7 accepts
the gate's refusal), `package.json`, `package-lock.json`, `.gitignore`, `README.md`; app: `data/sync/SyncRepository.kt`,
`SyncRemote.kt`, `SyncModels.kt`, `SyncPreferences.kt`, `SyncWorker.kt`, `PulledRowRules.kt`,
`data/usage/SupabaseUsageRemote.kt`, `data/billing/PurchaseVerifier.kt`, `PlayPurchaseSource.kt`,
`data/entitlement/EntitlementManager.kt`, `domain/entitlement/PurchaseInterpreter.kt`, `ProGates.kt`,
`feature/pro/ProCopy.kt`, `feature/settings/SettingsViewModel.kt`, `feature/onboarding/OnboardingViewModel.kt`,
`LucentoApplication.kt`; tests `UploadRefusalTest.kt`, `PurchaseAccountBindingTest.kt` (new), `FakeSyncServer.kt`,
`FakeSyncPreferences.kt`; CI `.github/workflows/android.yml`, `.github/dependabot.yml`; docs `docs/security/*`,
`docs/SYNC.md`, `docs/monetization/SERVER_CONTRACT.md`, `docs/handover/*`.

**lucento-website:** `package.json`, `package-lock.json`, `astro.config.mjs`, `vercel.json`, `.env.example`,
`src/server/http.ts`, `quota.ts`, `pubsub-auth.ts` (new), `src/server/billing.ts`, all six `src/pages/api/**` routes,
`scripts/csp.mjs`, `serve-static.mjs` (new), tests `src/server/http.test.ts`, `quota.test.ts`, `pubsub-auth.test.ts`,
`scripts/csp.test.mjs`, `e2e/api.spec.ts`, `e2e/security.spec.ts` (new), `e2e/home.spec.ts` (one race fixed: the hash
check waits for the dialog's close event, which fires after it hides), `playwright.config.ts`, `.github/workflows/ci.yml`,
`.github/dependabot.yml`.

## 4. Decisions made, and the one to confirm

- **D-1 (confirm):** your brief says Partners inherit the Owner's Cloud capability; the approved rule
  (docs/MONETIZATION.md §6.4, "Partner protection") says a Partner keeps syncing after the Owner lapses. Both are built:
  the gate resolves the Owner's CLOUD, and `partner_sync_after_owner_lapse` (default **1 = approved lenient rule**) lets
  the Partner keep writing. One SQL line switches to strict (OWNER_ACTIONS "Still yours to decide"). Abuse stays bounded
  either way: a Partner exists only if the Owner once held SHARED, and ceilings apply.
- Signed-out purchases link to the first verifier (documented policy, unchanged).
- Refused uploads wait 30 min under an unchanged plan; a plan change resumes them at once.
- Test purchases are refused by default on every project (explicit allow).

## 5. Validation detail

### 5.1 SQL
Every migration replayed on PGlite (Postgres 17) per suite, both privilege modes: 258/258 each. Concurrency on embedded
Postgres 17.6: C1 first-insert race counted exactly; C2 two transactions for 10 places with 8 rows each → one commits, one
LU001; C3 twenty writers, 10 places → 3 stored, 17 LU001, counter exact; C4 forty anonymous counts, total 25 → 25.
Existing Shared-space scenarios (69 checks: Owner/Partner/removed Partner/stranger/anon, transaction ownership, wallet
access) and the deletion, entitlement, purchase, email and multicurrency suites all still pass.

### 5.2 Android
`testDebugUnitTest`: 1,253 tests, 0 failures. `assembleDebug` and `assembleRelease` (R8, `lintVitalRelease`): **passed**.
`lintDebug`: **1 error, not in the code** — `PropertyEscape` in this PC's untracked `local.properties` (`sdk.dir=C:/…`
needs `C\:`); CI generates its own and isn't affected. None of the 23 warnings is in a file this work changed.

Release APK (`app-release-unsigned.apk`, built locally) inspected with `aapt2` and a dex string scan:
- Permissions unchanged: INTERNET, ACCESS_NETWORK_STATE, USE_BIOMETRIC, USE_FINGERPRINT, BILLING, WAKE_LOCK,
  RECEIVE_BOOT_COMPLETED (+ the library's own not-exported receiver permission).
- Exported: `MainActivity` (launcher) only, plus four library components each guarded by a system permission
  (`REVOCATION_NOTIFICATION`, `BIND_JOB_SERVICE`, `DUMP` ×2). Not debuggable; `allowBackup=false`; network security config
  present.
- No `service_role`, `sb_secret_`, private key, `client_secret` or lucento-test URL in the dex; the only Supabase host is
  production's. The debug-only test store (`TestPurchaseSource`) and plan simulator (`DebugPlanSimulator`) are absent.

Not run here: instrumentation tests and `ScreenshotTourTest` (they need an emulator; CI's `migrations` job runs them).

### 5.3 Android dependency scan
The resolved release runtime classpath (248 packages) was queried against the OSV database: **0 known vulnerabilities**.
A control query (protobuf-java 3.21.0, okhttp 3.12.0) returned advisories, so the query works. Evidence:
`docs/security/evidence/android-osv-scan-2026-11-10.txt`. No dependency was upgraded.

### 5.4 Not verified (needs a project, a console or a real payment)
Behaviour on lucento-test or production; PostgREST's real HTTP status for 42501/LU001 on the phone; a real Play
purchase, acknowledgement, refund or RTDN; the OIDC push; the live OPTIONS/headers on Vercel; TalkBack reading the new
messages; instrumentation tests and the screenshot tour (need an emulator; CI runs them).

## 6. Production deployment requirements

1. Owner pre-check (OWNER_ACTIONS §13.1): accounts syncing without a CLOUD row.
2. Apply the four migrations to lucento-test, test a debug build; then production; then §14 catalog checks.
3. Merge and deploy the website (needs the migration first: it passes `p_test_purchase`).
4. Upload the release build (internal track), then §8–§11.

## 7. Recommendation

Engineering remediation of every remediable finding is complete and tested offline. **Decision: NOT APPROVED** today —
not for an open engineering defect, but because the High fix (F-01) and the billing fixes (F-02, F-03) are not yet
applied to production, and F-05/F-06 are unconfirmed. When OWNER_ACTIONS §1, §2, §5/§6, §13 and §14 are done, the release
moves to **APPROVED WITH CONDITIONS** (conditions: §9–§11 before the first paid sale; accepted residuals: moderate
build-time npm advisories, F-16, F-17, Free-plan capacity if you stay on Free).
