# Lucento — Security Release Readiness Checklist

From [FINAL_SECURITY_AUDIT.md](FINAL_SECURITY_AUDIT.md) (2026-11-09). Tick an item only with its evidence: a link, a
command's output or a screenshot. **Status on 2026-11-09: NOT APPROVED. The blocking items are in section A.**

**Update 2026-11-10** ([REMEDIATION_REPORT.md](REMEDIATION_REPORT.md)): the engineering items are done and ticked with
their test evidence; everything that needs production, a console or a real payment is still open and is described step by
step in [OWNER_ACTIONS_REQUIRED.md](OWNER_ACTIONS_REQUIRED.md). **Status: NOT APPROVED** until A1's production items,
A3 and A4 are ticked.

Legend: **[B]** blocks release · **[R]** recommended before release · **[P]** can follow the release.

---

## A. Blocking items

### A1. [B] F-01 — Bound what one account can write, and check the Cloud plan on the server (engineering)

- [x] New migration: the INSERT and UPDATE policies of `wallets`, `categories`, `transactions`, `budgets`, `goals`,
      `goal_contributions`, `recurring` and `ledger_settings` also require `lucento_private.has_capability(…, 'CLOUD')`
      (the Owner's capability for the Partner policies). SELECT and DELETE stay ungated.
      *Evidence: `supabase/migrations/20261111090000_cloud_write_gate_and_quotas.sql`; suite checks G1–G27.*
- [x] CHECK constraints on free-text lengths (notes ≤ 500, names ≤ 100) and a per-account row ceiling trigger.
      *Evidence: B1–B6, Q1–Q9; `concurrency.mjs` C1–C3 on Postgres 17.6 (4/4, three runs).*
- [x] `usage_events`: anon insert replaced by a rate-limited SECURITY DEFINER function, or the anon grant removed.
      *Evidence: X8b, U1–U7, C4.*
- [x] New `tools/sql-verify` cases: a Free account's insert is refused, a Pro account's is accepted, a Partner writes on
      the Owner's plan, a lapsed account can still read and restore, and the bulk inserts of probes X8a and X8b are
      refused. `npm run verify` and `npm run verify:stock` both green.
      *Evidence: 258/258 checks in both modes, 2026-11-10 (`security_remediation_suite.sql`, 78 checks).*
- [x] The app handles a refused write (42501) on a lapsed plan without a retry loop (unit test).
      *Evidence: `UploadRefusalTest` (8 tests), part of 1,253 passing unit tests.*
- [x] Applied to lucento-test, then production. Production catalog re-checked (`pg_policies`). *Evidence: production applied by the owner (SQL Editor, `PRODUCTION_APPLY_2026-11-10.sql`) and checked read-only by Claude on 2026-11-10: 4 versions recorded; all 17 write policies of the 8 synced tables gated, no SELECT/DELETE policy gated; anon has no INSERT on usage_events and can call record_usage_event; 21 limit triggers; counters equal stored rows; refund table present; clients can’t call apply_play_purchase; no default ACL for anon/authenticated in public; 0 squatted seed ids; purge job scheduled; every syncing account holds CLOUD; security advisor: only the intended RPC warnings, private tables without policies, and leaked-password protection (still off). lucento-test not yet (no MCP access to it); apply the same file there before the next debug-build test.*
- [ ] Owner decision recorded: stay on Supabase Free with a usage alert and the read-only runbook, or move to a paid
      plan with a spend cap. *Decision 2026-11-10: stay on Free for now (owner). Usage alert/reminder still to set (§5).*

### A2. [B] F-02 and F-03 — Billing linking fixes, before the first real purchase (engineering)

- [x] `lucento_private.revoked_purchases(purchase_token_hash)`: written by `revoke_play_purchase`, checked by
      `apply_play_purchase`, and not deleted with the account. *Evidence: P1–P5.*
- [x] First link refused when Play's obfuscated account id names a different account. *Evidence: X2, P6–P11.*
- [x] The app doesn't send a purchase whose `obfuscatedAccountId` belongs to another account.
      *Evidence: `PurchaseAccountBindingTest` (7 tests).*
- [x] Suite cases for probes X1 and X2 pass. *Evidence: 258/258, 2026-11-10.*
- [x] Applied to production (with A1, OWNER_ACTIONS §13). *Evidence: as A1.*

### A3. [B] F-05 — Gemini key on the paid tier (owner, about 5 minutes)

1. Open https://aistudio.google.com/apikey and sign in with the account that made Lucento's key.
2. Find the key that Vercel uses. Note the **Project** name next to it.
3. Click the project name, or open https://console.cloud.google.com/billing and pick that project at the top.
4. Under **Billing**, check that the project shows a **linked billing account** (not "This project has no billing
   account").
5. If there is none, click **Link a billing account**, choose or create one, and confirm.
6. Optional: **Billing → Budgets & alerts → Create budget** with a monthly amount and an email alert.
7. Take a screenshot of the linked billing account and save it as `docs/security/evidence/gemini-billing.png`.

- [ ] Done. Screenshot saved.

### A4. [B] F-06 — Supabase Auth settings on production (owner, about 10 minutes)

1. Open https://supabase.com/dashboard and choose the **lucento** project (production, `hmtcuttadbgbldvzsfhz`).
2. Left menu: **Authentication → Sign In / Providers**. Under **Email**:
   - **Confirm email**: must be **ON**. *(Required: Shared invitations rely on it.)*
   - **Secure email change**: **ON**.
   - **Minimum password length**: 8 or more.
   - **Prevent use of leaked passwords**: turn **ON** and click **Save**. (Supabase moves this setting now and then:
     if it isn't on this page, look under **Authentication → Attack Protection** or **Password Security**.)
3. Left menu: **Authentication → URL Configuration**:
   - **Site URL**: `https://www.lucentoapp.com`.
   - **Redirect URLs**: only addresses on `www.lucentoapp.com` (for example `https://www.lucentoapp.com/auth/confirmed`).
     Remove any `localhost` or preview address.
4. Left menu: **Authentication → Rate Limits**: leave the defaults or lower them. Note the email limit (memory: 30/h).
5. Take screenshots of steps 2 and 3 into `docs/security/evidence/`.

- [ ] Confirm email ON (screenshot).
- [ ] Leaked password protection ON. The Supabase security advisor no longer lists `auth_leaked_password_protection`.
- [ ] Redirect allow-list checked.

---

## B. Recommended before release

- [x] **[R] F-04** Upgrade `astro` to 7.4.x and `@astrojs/vercel` to 11.x on a branch; `npm run verify` and
      `npm run test:e2e` green; `npm audit --omit=dev` shows no high or critical issue. *(Or the owner records an
      accepted risk with a date.)* *Evidence: website `security/release-hardening`: Astro 7.4.8, adapter 11.0.20; verify
      green; e2e 55/55; `npm audit --omit=dev --audit-level=high` exit 0 (4 moderate, build-time only). Not deployed.*
- [ ] **[R] F-08** API routes answer `OPTIONS` with 405 or 204. `curl -X OPTIONS https://www.lucentoapp.com/api/ask`
      no longer returns 500. *Code and unit tests done (`http.test.ts`); the live `curl` waits for the deploy.*
- [ ] **[R] F-09** `vercel.json` sends `X-Content-Type-Options: nosniff`, `Referrer-Policy`,
      `Content-Security-Policy` with `frame-ancestors 'none'`. Checked with `curl -sI`. *Done and e2e-tested on the built
      site with these headers (`e2e/security.spec.ts`, 8 tests); the live `curl -sI` waits for the deploy.*
- [ ] **[R] F-11** Vercel Firewall rate-limit rule on `/api/*` (for example 30 requests/min/IP). *(OWNER_ACTIONS §7;
      the per-account verify limit is built: R1–R4, `quota.test.ts`.)*
- [x] **[R] F-13** Migration revoking the default table privileges from `anon` and `authenticated` in `public`.
      *Evidence: `20261111110000_seed_ids_and_default_privileges.sql`; D1–D4 in both modes. Not applied yet.*
- [x] **[R] F-14** Production migration history matches `supabase/migrations/` (back-filled), or a dated apply
      manifest exists. *Evidence: [MIGRATION_MANIFEST_2026-11-10.md](MIGRATION_MANIFEST_2026-11-10.md) (back-fill SQL there
      is optional, owner's).*
- [x] **[R] F-15** Dependabot on both repositories; a website CI workflow (`npm ci && npm run verify && npm audit
      --omit=dev`); third-party actions pinned by SHA. *Evidence: both `.github/dependabot.yml`, website
      `.github/workflows/ci.yml`, Android `android.yml` (SHA pins + `sql-verify` job). First CI run happens on push.*
- [x] **[R]** Run an Android dependency CVE scan once (`osv-scanner --lockfile` on a Gradle lockfile, or OWASP
      Dependency-Check), and record the result. Not done in this audit. *Evidence: OSV database query of the 248-package
      release classpath, 0 known vulnerabilities (`evidence/android-osv-scan-2026-11-10.txt`).*

## C. Real-purchase verification (internal testing track)

From KNOWN_ISSUES LP-K12 and `docs/monetization/PLAY_CONSOLE_SETUP.md`. These steps also verify the billing parts of
this audit.

- [ ] A test purchase of each product: entitlement row written; acknowledged within 3 days.
- [ ] Submit the same token from a second Lucento account: `linked_elsewhere` (and after A2, `other_account`).
- [ ] Refund one purchase in Play Console **with "remove entitlement"**: RTDN → `REVOKED` within minutes; the next
      daily `voided` run reports it.
- [ ] After the refund, check what `purchases.productsv2` returns for the token (this decides F-02's real severity).
      Record it in docs/monetization.
- [ ] Pub/Sub push subscription posts with `Content-Type: application/json`, and the endpoint answers 200.
- [ ] License testers limited to the owner (F-12). *(Server side done: test purchases refused unless
      `allow_test_purchases` = 1, T1–T2; OWNER_ACTIONS §8.)*

## D. Already verified in this audit (keep green)

- [x] RLS on every table; `anon` has no read grant (production catalog, 2026-11-09).
- [x] Billing and email functions executable only by `service_role` (production ACLs).
- [x] All SECURITY DEFINER functions use `search_path = ''` (production `proconfig`).
- [x] No storage buckets, public views, Edge Functions or Realtime tables in production.
- [x] `tools/sql-verify`: 180/180 checks under both privilege defaults. *(2026-11-10: 258/258 with the new suite.)*
- [x] Shared space: Partner can't edit wallets, hard-delete, move rows, invite or remove; a removed Partner reads
      nothing at once (probes X4–X7).
- [x] No secret in either repository or its git history; release APK contains only the publishable key.
- [x] Release build: one exported activity, `allowBackup=false`, no cleartext, R8 on, permission allow-list in CI,
      test store and plan simulator debug-only.
- [x] Every website API route answers 401 without a valid token (live).
- [x] Privacy policy lists every network path (`LegalDocuments.kt`, VERSION 2026-11-09).

## E. After release

- [x] **[P] F-07** Unguessable seed category ids. *Done differently: a `seed-` id is accepted only under its own
      account (X3, S1–S2). Not applied yet.*
- [ ] **[P] F-10** Pub/Sub OIDC push authentication on `/api/billing/rtdn`. *(Verification built and tested,
      `pubsub-auth.test.ts`; switched on by OWNER_ACTIONS §12.)*
- [x] **[P] F-12** Store or refuse test purchases. *Evidence: T1–T2, website and app mappings. Not applied yet.*
- [ ] **[P] F-16** Hide-in-recents on by default, or offered during onboarding. *(Evaluated: not a default;
      onboarding offer recommended — REMEDIATION_REPORT §3.16.)*
- [ ] **[P] F-17** Optional password-protected backup files. *(Design in REMEDIATION_REPORT §3.17.)*
- [ ] **[P] I-1 / I-4** Shared copy: a Partner sees every wallet; a removed Partner's phone keeps the data until it
      next connects.
- [ ] Re-run the Supabase security advisor and `tools/sql-verify` after every migration, and this checklist before
      every major release.

---

**Sign-off for release** (only when A1–A4 are ticked):

| Role | Name | Date | Decision |
|---|---|---|---|
| Owner | | | |
| Security review | | | |
