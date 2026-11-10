# Lucento — Final Production Security Audit

**Date:** 2026-11-09 · **Auditor:** Claude (independent review, audit only, no code changed)
**Scope:** `lucento-android` (branch `feature/facebook-community`, HEAD `6e5dac9`), `lucento-website` (branch
`feature/account-emails`, HEAD `fa30b48`, and the live site www.lucentoapp.com), the production Supabase project
`hmtcuttadbgbldvzsfhz` (metadata only), and `supabase/migrations/` (23 files).
**Companion:** [RELEASE_READINESS_CHECKLIST.md](RELEASE_READINESS_CHECKLIST.md)

---

> **Remediation status, 2026-11-10** ([REMEDIATION_REPORT.md](REMEDIATION_REPORT.md)). The findings below are the audit
> as written on 2026-11-09 and are kept unchanged. Since then: F-01, F-02, F-03, F-07, F-12, F-13 are fixed in four new
> migrations (258/258 offline checks under both privilege modes, concurrency races on real Postgres) and in the app
> (1,253 unit tests); F-04, F-08, F-09 are fixed on the website (Astro 7.4.8, 0 high/critical production advisories,
> 55/55 e2e); F-10 is built and waits for Pub/Sub configuration; F-11 has a per-account limit, the IP limit is a Vercel
> setting; F-14 is investigated (all migrations present in production, manifest written); F-15 is done; F-16 evaluated,
> F-17 deferred. **Nothing is applied to Supabase or deployed yet**, and F-05/F-06 still need the owner
> ([OWNER_ACTIONS_REQUIRED.md](OWNER_ACTIONS_REQUIRED.md)). **The release decision stays NOT APPROVED** until the
> migrations are on production and verified, and F-05/F-06 are confirmed.

## 1. Executive summary

**Can Lucento be trusted with people's financial data? On confidentiality, yes, based on what could be tested.** I found
no way for one account to read or change another account's money records. Every table has row-level security, the
signed-out role can't read anything, the paid-plan and email functions can be called only by the server, and the
Shared (couples) model is enforced in the database, not in the app. I attacked it as a Partner, a removed Partner and
an outsider, and every attempt was refused (§5). No secret is in the APK, in either repository or in their git history.

**It is not ready to release today.** That isn't because user data can leak. It's because of abuse and billing gaps:

1. **HIGH — one free account, or a caller who isn't signed in at all, can fill the production database** (F-01). The
   database accepts unlimited rows from any account, and the paid Cloud plan isn't checked on the server. Production
   runs on Supabase's Free plan, which goes read-only for *everyone* once it is full. Cloud sync would stop for all
   users. Their data stays safe on their phones, but the service is down until someone steps in.
2. **MEDIUM — two billing defects let Pro end up on the wrong account or survive a refund** (F-02, F-03). Both were
   reproduced against the real migrations.
3. **MEDIUM — the website runs framework versions with published critical and high advisories** (F-04). I found no
   exploitable path in Lucento's use of them, but they are on the request path of the billing and AI endpoints.
4. **MEDIUM — two things I couldn't see must be confirmed.** First, that the Gemini key is on Google's paid tier
   (F-05); otherwise the financial summaries Ask Lucento sends may be used by Google. Second, that Supabase email
   confirmation is on (F-06), which the Shared invitation security depends on.

**Decision: NOT APPROVED** (§8). F-01 can be fixed in one migration plus a plan decision. With F-01–F-03 fixed and
F-04–F-06 confirmed, my recommendation would be **APPROVED WITH CONDITIONS**.

Counts: **0 Critical · 1 High · 5 Medium · 11 Low · 7 Informational.**

---

## 2. Method, evidence and limits

| Activity | What was done | Result |
|---|---|---|
| Source review | All 7 website API routes and server modules, all 23 migrations, the Android manifest, build, sync, session, billing, entitlement, crash, logging and backup code | Findings below |
| Production catalog (read-only metadata, no user rows) | Supabase security advisor, `pg_policies`, table grants, function ACLs and `search_path`, triggers, default ACLs, cron, storage buckets, views, Realtime publication | Matches the repository's intent (§6). Migration history lists only 11 of 23 files (F-14) |
| Behavioural RLS tests (offline) | `tools/sql-verify` replays every migration on PGlite (Postgres 17 in-process) and acts as different signed-in users | **180/180 existing checks pass** under both privilege modes (Shared 69, entitlements 38, Play purchases 30, account emails 16, deletion 10, multicurrency 17) |
| Adversarial probes (offline) | 19 new attack probes written for this audit (scratchpad `audit_probes.sql`, not committed) | 5 confirmed, 11 blocked, 1 by design (§5) |
| Live website (unauthenticated only) | Response headers, every API route without and with a bogus token, sensitive paths, CORS preflight | 401 everywhere it should be; F-08, F-09 |
| Secrets | Pattern scan of tracked files and the **full git history** of both repos; a built release APK's dex strings | No secret found (§6) |
| Dependencies | `npm audit` (website); version review of `gradle/libs.versions.toml` | F-04; Android not CVE-scanned (limit) |

**What I could not verify (no access, or out of the authorised scope):**

- **Production behaviour.** I ran no impersonation tests there and read no user rows. RLS behaviour comes from the
  offline replay; production was compared by catalog. *The Supabase connector now reaches production (it used to
  reach only lucento-test), so take care with it.*
- **Supabase Auth settings:** email confirmation, OTP and link expiry, rate limits, redirect allow-list, JWT lifetime,
  minimum password length.
- **Google Play Console, Pub/Sub push settings, the service account's roles, the Gemini key's billing tier, Resend key
  scoping, Vercel environment-variable scoping (Production vs Preview) and deployment protection.**
- **A real Google Play purchase, refund or notification.** None exists yet (KNOWN_ISSUES LP-K12).
- **Dynamic analysis of the APK** (on-device instrumentation, a rooted-device extraction test) and a CVE scan of the
  Android dependency tree (no OSV or Dependency-Check tool installed).

---

## 3. Security scorecard

There is no numeric score. A number would imply a weighting method I can't defend. Each area is rated on what was
tested: **Strong** (controls verified, no open finding above Low) · **Adequate** (sound design, Medium findings or
untested parts) · **Needs work** (an open High).

| Area | Rating | Basis |
|---|---|---|
| Android app | **Strong** | One exported component (launcher); no cleartext; system CAs only; backup off; R8 on; no secrets; debug-only test store and simulator. Low: on-device data is plaintext inside the sandbox; hide-in-recents is off by default |
| Supabase (platform) | **Adequate** | No Edge Functions, buckets, views or Realtime. Auth settings unverified (F-06); leaked-password check off; migration history drift (F-14) |
| Database and RLS | **Needs work** | Isolation verified (180 + 11 checks), but writes are unbounded and not plan-gated (F-01) |
| Backend APIs | **Adequate** | Every route authenticates; nothing trusted from the client; no injection surface. Low: OPTIONS crash, no IP rate limit, static RTDN token |
| Google Play Billing | **Adequate** | Server is the authority; clients can't call the grant functions; product and package checked. Two linking defects (F-02, F-03) |
| Website | **Adequate** | Static pages, no secrets in the bundle, HSTS. Vulnerable framework versions (F-04); no CSP or framing headers |
| Secrets management | **Strong** | Secrets only in Vercel env and CI secrets; the build refuses a service-role key; history clean |
| Data privacy | **Adequate** | Opt-ins honoured; account deletion cascades; the policy lists every path. Gemini tier unverified (F-05) |
| Dependencies | **Adequate** | Website: 11 advisories (F-04). Android: current Billing 9.1, older Compose and Kotlin; no automated scanning (F-15) |
| Release configuration | **Strong** | targetSdk 36, minSdk 26, minify and shrink, signing from CI secrets, a permission allow-list in CI, debug never pointed at production |

---

## 4. Findings

### 4.1 Critical and high

#### F-01 — HIGH — Any account, or a caller who isn't signed in, can write unlimited data; the paid Cloud plan isn't enforced on the server

- **Component:** Supabase RLS on the sync tables; `public.usage_events`.
- **Location:** `supabase/migrations/20261028190000_rls_initplan_and_fk_indexes.sql:15,24,33,42,51,60` (insert
  policies check only `auth.uid() = user_id`); `20261026120000_recurring_and_debt_terms_sync.sql:47`;
  `20261028180000_usage_events.sql:35-42` (`grant insert … to anon`, `with check (true)`). Already known internally
  as KNOWN_ISSUES **LP-K6** ("decide before Play launch"); this audit raises its severity.
- **Evidence:** Offline probe **X8a**: one Free account (no entitlement) inserted 3,000 transactions with
  2,000-character notes in **one** statement. **X8b**: the `anon` role inserted 3,000 `usage_events` rows in one
  request. The production catalog has the same policies and grants. `has_capability(…,'CLOUD')` appears in no policy
  (grep of all migrations). Production is on the Supabase Free plan (owner notes).
- **Exploitation:** The publishable key is in every APK, by design. An attacker signs up (free, or Google sign-in), or
  doesn't sign up at all for `usage_events`, and loops `POST /rest/v1/transactions` with large rows. At about 6 MB per
  request, the 500 MB Free-plan database fills in under 100 requests. Supabase then puts the project in read-only
  mode, so sign-in still works but every sync write, invite, purchase confirmation and AI quota update fails for all
  users. Separately, any Free user with a modified app (or `curl`) gets cloud sync, a paid service, for nothing.
- **Impact:** A service-wide outage that is easy to trigger and needs a manual fix (delete rows or upgrade the plan),
  plus lost revenue. No data exposure: each person's ledger stays on their phone.
- **Remediation (one migration plus one owner decision):**
  1. Add `lucento_private.has_capability((select auth.uid()), 'CLOUD')` to the INSERT and UPDATE policies of the seven
     sync tables and `ledger_settings`. Partners keep writing on the Owner's plan: use the Owner's capability for the
     `*_partner` policies. Keep SELECT and DELETE ungated so a lapsed account can still restore and clean up.
  2. Add size bounds as CHECK constraints: `char_length(note) <= 500`, and name ≤ 100 on wallets and categories.
     Add a per-account row ceiling (a BEFORE INSERT trigger counting the account's rows, e.g. 200,000 transactions)
     so even a paying abuser is bounded.
  3. `usage_events`: move the insert behind a SECURITY DEFINER function that rate-limits per `install_id` and per
     day, or drop the anon grant until the counts backend is actually used (memory says it is opt-in).
  4. Operational: a Supabase usage alert, and either a paid plan with a spend cap or a documented runbook for
     read-only mode.
- **Blocks release:** **Yes.**

### 4.2 Medium

#### F-02 — MEDIUM — A refunded Lifetime purchase can be restored on a new account after the refunded account is deleted

- **Component:** `public.apply_play_purchase`; account deletion cascade.
- **Location:** `supabase/migrations/20261108090000_play_purchases.sql:59-61` (the "refunds stay final" check reads
  only `public.entitlements`); `20261107090000_entitlements.sql:21,58` (`entitlements` and `purchase_links` cascade on
  user delete); `20261109090000_account_deletion.sql:29`; `lucento-website/src/pages/api/billing/voided.ts:21` (looks
  back only 30 days).
- **Evidence:** Offline probe **X1**: account A buys Lifetime, gets refunded (`revoke_play_purchase` → `REVOKED`), then
  calls `delete_my_account()`. A new account D submits the same token and gets `result ok, status ACTIVE`. Deletion
  removed the only record that the token was refunded.
- **Exploitation:** Buy Lifetime, request a refund, wait more than 30 days (out of the daily voided-purchase window),
  delete the Lucento account, create a new one, and send the old purchase token to `/api/billing/verify` from a
  modified client. **This works only if Play's `productsv2` still reports a refunded purchase as `PURCHASED`.** That
  depends on whether the refund removed the entitlement, and it is unverified (no real purchase exists yet).
- **Impact:** Lost revenue (refund-and-keep). No effect on other users.
- **Remediation:** Keep a refund record that survives account deletion. Add a table
  `lucento_private.revoked_purchases(purchase_token_hash primary key, revoked_at)` with no account column (so no
  personal data, and nothing to cascade). Write it in `revoke_play_purchase` and check it in `apply_play_purchase`.
  Add the case to `play_purchases_suite.sql`. Always tick "remove entitlement" when refunding in Play Console.
- **Blocks release:** Yes for paid launch (cheap to fix). Not a user-safety issue.

#### F-03 — MEDIUM — The first account to verify a purchase gets it, even when Play says it was bought for another account

- **Component:** `public.apply_play_purchase` (first-link branch); app purchase handling.
- **Location:** `supabase/migrations/20261108090000_play_purchases.sql:73-80` (first link inserts without comparing
  `p_obfuscated_account_id`); the app sends every Play purchase without filtering by account
  (`app/src/main/java/com/lucento/app/feature/pro/ProViewModel.kt:118` sets the id only at purchase time).
- **Evidence:** Offline probe **X2**: a subscription whose obfuscated id is `sha256(B)` was verified first by account
  C and linked to C (`ACTIVE`). The real buyer B then got `linked_elsewhere`, and the rule moves it only after 30 days.
- **Realistic trigger (no attacker needed):** Two Lucento accounts on phones with the **same Google Play account**,
  which is common for couples, Lucento's own audience. B buys on phone 1. If B's verification is delayed (offline,
  `play_unavailable`), phone 2, signed in as C, sees the same purchase from Play and verifies it first. C gets Pro and
  B is locked out for 30 days. A token thief could do the same deliberately.
- **Remediation:** In the first-link branch, refuse with `result: 'other_account'` (or `review_required`) when
  `p_obfuscated_account_id` is present and isn't `sha256(lower(p_user))`. Link only when it matches, or when Play
  carries no id (purchases made while signed out). In the app, skip verification of a purchase whose
  `accountIdentifiers.obfuscatedAccountId` names another account. Add both cases to the SQL suite.
- **Blocks release:** Yes for paid launch.

#### F-04 — MEDIUM — The website's framework and adapter versions have published critical and high advisories

- **Component:** `lucento-website` dependencies (`package.json`: `astro 5.18.2`, `@astrojs/vercel 8.2.11`).
- **Evidence (`npm audit`, 2026-11-09):** 11 advisories (1 critical, 6 high). The relevant ones are Astro "RCE through
  AVIF image optimization" (GHSA-26w7-cxv4-gfx2, <7.2.8), "Host header SSRF in prerendered error page fetch"
  (GHSA-2pvr-wf23-7pc7, <6.4.6), "Reflected XSS via unescaped slot name" (GHSA-8hv8-536x-4wqp), several
  spread-attribute XSS issues, and `@astrojs/vercel` "Unauthenticated Path Override via `x-astro-path`"
  (GHSA-mr6q-rp88-fx84, <10.0.2). Also `sharp` (libvips CVEs), `path-to-regexp` ReDoS and `zod` memory exhaustion.
- **Exploitability as deployed:** Low. There is no runtime image endpoint (`/_image` and `/_vercel/image` return 404),
  no middleware for a path override to bypass (each API route authenticates itself), pages are prerendered from
  first-party content, and Vercel routes by its own Host. I found **no working exploit**.
- **Why it still matters:** The same runtime serves `/api/billing/*`, `/api/ask` and `/api/interpret`, which hold the
  service-role key, the Play service account and the Resend key in their environment. One missed condition in a
  framework bug there is a backend compromise.
- **Remediation:** Upgrade to Astro 7.4.x and `@astrojs/vercel` 11.x on a branch (major versions: run
  `npm run verify` and the Playwright suite), and add `npm audit --omit=dev` to a website CI workflow (F-15).
- **Blocks release:** Recommended before release. Low practical risk today.

#### F-05 — MEDIUM — Unverified: the Gemini key may be on Google's free tier, where prompts can be used by Google

- **Component:** `/api/ask`, `/api/interpret` → Gemini API.
- **Evidence:** `docs/CAPTURE.md:140` itself says "For real users, turn on billing for that project (paid tier): on the
  free tier Google may use prompts to improve its products". Nothing records that billing is on. Ask Lucento v2/v3
  sends balances, budgets, debts, goals and **the last 60 days of transactions with descriptions**
  (`lucento-website/src/pages/api/ask.ts:5-12`). The privacy policy (`LegalDocuments.kt`) names Gemini but doesn't
  say whether Google may use or human-review the content.
- **Impact:** If the key is on the free tier, people's financial records may be used to improve Google products and
  read by human reviewers. Their consent screen and the privacy policy don't cover that, which is a Play Data Safety
  and privacy compliance risk.
- **Remediation:** Owner check: Google Cloud Console → the key's project → Billing is linked (paid tier). Record the
  result in docs/ASK.md. Keep a budget alert.
- **Blocks release:** Yes until confirmed (a 5-minute check).

#### F-06 — MEDIUM — Supabase Auth hardening is unverified, and Shared invitations depend on it

- **Component:** Supabase Auth (production).
- **Evidence:** Advisor: **Leaked password protection disabled** (the app's own Pwned Passwords check,
  `data/sync/PwnedPasswords.kt`, is client-side and skipped by a direct API sign-up). An invitation is accepted by
  whoever holds a **confirmed** email matching it (`20261105120000_lucento_shared.sql:109,452`). If "Confirm email"
  were off, Supabase would mark every sign-up confirmed, and an attacker could register the invitee's address, accept
  the invitation and read the Owner's whole ledger. Indirect evidence says confirmation is on (a confirmation template
  exists, and the owner received sign-up confirmation emails, OT-19), but I couldn't see the setting.
- **Remediation (owner, dashboard):** Authentication → Sign In / Providers → Email: **Confirm email ON**, **Secure
  email change ON**, minimum password length ≥ 8, **Leaked password protection ON**. URL configuration: the redirect
  allow-list holds only `https://www.lucentoapp.com/auth/confirmed` (and the app's own scheme if any). Rate limits:
  keep the sign-up and email limits. Record the settings in docs/SYNC.md.
- **Blocks release:** Confirming "Confirm email ON" does. The rest is hardening.

### 4.3 Low

| ID | Finding | Location / evidence | Remediation | Blocks |
|---|---|---|---|---|
| F-07 | **Another account can claim a user's predictable seed category id** and break their sync. Seed ids are `seed-<userId>-<kind>-<slug>` and `categories.id` is a *global* primary key; the sync upserts `onConflict = "id"`. Probe **X3**: account C created `seed-<B>-income-salary`, and B's upsert failed with 42501, so B's transactions that reference it fail too. The attacker needs B's user id before B's first sync, and user ids are shown to Shared partners and invitees (`get_my_shared_space`). | `data/sync/SyncRepository.kt:372`; `20261024085632_create_sync_tables.sql` (`id text primary key`); `data/sync/SyncRemote.kt:78` | Derive seed ids from an unguessable value (`UUID.nameUUIDFromBytes(userId + secret salt)` or random, deduplicated by name), or move to `(user_id, id)` keys in a later schema version | No |
| F-08 | **`OPTIONS` on any API route crashes the function** (`500 FUNCTION_INVOCATION_FAILED`). It leaks nothing, but anyone can run up errors and invocations. | Live: `curl -X OPTIONS https://www.lucentoapp.com/api/ask` | Export an `ALL`/`OPTIONS` handler returning 405 (or 204 without CORS headers) | No |
| F-09 | **No browser security headers** on the website: no CSP, `X-Frame-Options`/`frame-ancestors`, `X-Content-Type-Options`, `Referrer-Policy` or `Permissions-Policy` (HSTS is present). | Live headers; `vercel.json` has no `headers` | Add a `headers` block in `vercel.json`: `frame-ancestors 'none'`, `nosniff`, `strict-origin-when-cross-origin`, and a CSP matching the site's inline scripts (hashes) | No |
| F-10 | **The Pub/Sub push is authenticated by a static token in the query string**, which ends up in request logs and is never rotated. | `src/pages/api/billing/rtdn.ts:26` | Turn on Pub/Sub push authentication (OIDC) and verify the Google-signed JWT (`aud`, `email` of the push service account); keep the token as a second factor | No |
| F-11 | **No per-IP or per-account request limit on `/api/billing/verify`.** Each call spends Google Play Developer API quota, so a signed-in script can exhaust it and block real verifications. The AI endpoints are bounded per account by the database, but the Free AI allowance comes back with a new account (deleting an account cascades `ai_usage`). | `src/pages/api/billing/verify.ts:37-56`; `20261027090000_ai_usage_quota.sql:7` | Vercel WAF rate-limit rules on `/api/*` (e.g. 30/min/IP); a per-account daily verify counter; optionally keep a hashed-email AI counter that outlives deletion | No |
| F-12 | **License-tester (test) purchases grant real Pro in production.** `testPurchase` is read but never stored or refused. | `src/server/play-state.ts:108,144`; `billing.ts:100-110` | Store `test_purchase` on the entitlement row, or refuse test purchases when an env flag says production; keep the Play license-tester list to the owner | No |
| F-13 | **Default privileges give `anon` TRUNCATE, REFERENCES, TRIGGER and MAINTAIN on any new `public` table** created by `postgres`. Every current table revokes them explicitly (verified in the catalog), but one forgotten `revoke` in a future migration exposes TRUNCATE, which RLS doesn't cover. | Production `pg_default_acl`: `postgres public r {…anon=Dxtm…}` | `alter default privileges for role postgres in schema public revoke all on tables from anon, authenticated;` in a new migration | No |
| F-14 | **Production's migration history doesn't record what is deployed**: `list_migrations` shows 11 versions (to `usage_events_purge_schedule`) while objects from all 23 exist, because they were applied by pasted SQL. It can't be proven that production equals the repo. I compared the security-relevant catalog by hand: it matches. | Supabase `list_migrations` | Back-fill `supabase_migrations.schema_migrations` rows for the applied files, or keep a dated manifest with a catalog checksum per production apply | No |
| F-15 | **Supply-chain hygiene:** GitHub Actions are pinned by tag (`actions/checkout@v4`, `reactivecircus/android-emulator-runner@v2`), not by commit SHA; no Dependabot, OSV or Dependency-Check; no Gradle dependency verification; **the website repository has no CI at all.** | `.github/workflows/android.yml:29,279`; no `lucento-website/.github` | Pin third-party actions by SHA; add `dependabot.yml` to both repos; add a website workflow (`npm ci && npm run verify && npm audit --omit=dev`); consider `gradle --write-verification-metadata sha256` | No |
| F-16 | **Hide in recents (FLAG_SECURE) is off by default**, so balances show in the recent-apps preview and screenshots, and screen-recording apps can capture them. | `data/preferences/UserPreferencesRepository.kt:118` (`hideInRecents = false`) | Consider defaulting to on, or offering it in onboarding's privacy step | No |
| F-17 | **On-device data is plaintext inside the app sandbox**: Room database, the Supabase refresh token in `SharedPreferences`, and the JSON backup and CSV exports saved wherever the person chooses. Safe from other apps (sandbox, `allowBackup=false`); exposed on a rooted or forensically imaged phone, and through any backup file the person shares. | `data/sync/SessionStore.kt:58`; `feature/settings/SettingsScreen.kt:143-146` | Optional password-encrypted backup files (AES-GCM with a PBKDF2/Argon2 key); a notice when exporting; SQLCipher only if the threat model needs it | No |

### 4.4 Informational

| ID | Observation |
|---|---|
| I-1 | **Partners see and edit every transaction in the space, including wallets the Owner classified as their own** (probe X5; documented: classification is "never an access rule", `20261105120000_lucento_shared.sql:192-198`). It is correct by design, but the UI should never suggest that an "Owner's personal" wallet is private from the Partner. |
| I-2 | **Partner-written text reaches the Owner's Ask Lucento prompt** (transaction notes, payees). The prompts treat labels as data, and the app recomputes every number, so a Partner can only shape the wording of advice, never inject amounts. |
| I-3 | **Local Pro features can be unlocked by a modified APK.** This is inherent to offline features. The entitlement cache is HMAC-sealed with an Android Keystore key, which stops casual tampering. Server-paid services (AI, Shared) are enforced on the server; sync isn't yet (F-01). |
| I-4 | **A removed Partner keeps the shared ledger on their phone until the app next reaches the server**, which then wipes it (`SharedSpaceRepository.endedByOwner`). Server access ends at once (probe X7). A local copy, or a backup they exported earlier, can't be recalled. Worth one line in the Shared FAQ. |
| I-5 | Warning logs pass exception objects to `Log.w` (`feature/onboarding/OnboardingViewModel.kt:321,390,503,680`). Logcat isn't readable by other apps, and I saw no amounts in these messages. Low value, no action needed. |
| I-6 | The advisor's 14 "SECURITY DEFINER executable by authenticated" warnings are the intended RPCs. Each one reviewed takes the caller from `auth.uid()`, sets `search_path = ''` and validates its arguments. `delete_usage_events` (anon) deletes only rows for a random install id the caller already knows. |
| I-7 | Several CHECK constraints are `NOT VALID` (legacy rows unchecked; new writes checked). This is about data integrity, not security. |

---

## 5. Attack scenario assessment

"Tested" means reproduced against the real migrations on PGlite (probe ids X…, existing suites C…, F…, P…) or
against the live site. "Reviewed" means established from code only.

| # | Scenario | Possible? | Evidence | Mitigation |
|---|---|---|---|---|
| 1 | User A reads User B's records | **No** (tested) | RLS on all 17 tables; existing suite + `rls_test.sql`; anon has no SELECT anywhere (production catalog) | — |
| 2 | A Partner (Companion) becomes the Owner (Manager) | **No** (tested) | X4a (no wallet edits), X4g (can't remove the Owner), X4h (can't invite); roles live only in `shared_spaces` / `space_members`, which have no client write grant | — |
| 3 | A removed Partner keeps access | **Server: no** (tested X7, F1, F2). **Local copy: until next sync** (reviewed) | Policies call `partner_spaces()` on every query | I-4 |
| 4 | Change an API request to reach another wallet | **No** (tested) | Inserts and updates are checked against `auth.uid()`; FKs `(user_id, wallet_id)` tie a transaction to the same ledger's wallet; `user_id` can't change (X4c) | — |
| 5 | Manipulate one's own Pro entitlement | **Server rows: no** (tested P1–P4: clients can't call the grant functions; `entitlements` is SELECT-only). **Cloud sync without paying: yes** (X8a) | F-01 |
| 6 | Extract data from a compromised device | Root or physical extraction: **yes, plaintext** (reviewed). Other apps: **no** (sandbox, one exported activity, private FileProvider, backup off) | F-17 |
| 7 | Reuse an expired session | **No** (reviewed) | JWTs are checked by Supabase Auth on every proxy call; sign-out revokes server-side with durable retries (`SessionRevocation.kt`); a deleted account's token fails FK and RLS checks |
| 8 | Abuse public endpoints | **Yes, for resource exhaustion** (tested X8b; live probes) | `usage_events` anon inserts; no IP rate limit; OPTIONS crash | F-01, F-08, F-11 |
| 9 | Exploit invitations | **No** (tested H0, H1, G0) | Expired, revoked and declined invitations are refused; the email must be *verified* and match; one pending invitation per Partner seat; locks against races. **Depends on email confirmation being on** | F-06 |
| 10 | Extract backend credentials from the APK | **No** (tested) | Release dex contains only the publishable key and proxy URLs; no `service_role`, `sb_secret_`, OAuth secret, private key or Resend key; the build fails on a service-role key | — |
| 11 | Exploit transaction sync | **Partly** | Clock skew is clamped (+5 min) and LWW revisions are server-stamped; attribution is stamped by the server (X6). **But** seed-id squatting breaks a victim's sync (X3) | F-07 |
| 12 | Manipulate purchase verification requests | **The request is never trusted** (reviewed): Play is queried server-side; product and package must match. **Linking defects confirmed** (X1, X2) | F-02, F-03 |
| 13 | Read sensitive data through database functions | **No** (tested X4d–X4f) | Every DEFINER function answers only about the caller; `has_capability`, `plan_limit` and the billing and email functions aren't callable by clients (production ACLs verified) | — |
| 14 | Bypass client restrictions by calling the API directly | **Shared, AI and entitlements: blocked server-side. Sync volume and the plan: not** | F-01 |

---

## 6. Positive findings

- **Isolation is enforced where it matters.** RLS is on every table, `anon` has no read grant anywhere, writes check
  `auth.uid()`, `user_id` can't change, transactions reference only the same ledger's wallets and categories, and hard
  DELETE is owner-only.
- **The Shared model is built properly:** membership lives in the database, the caller always comes from `auth.uid()`
  (never an argument), advisory locks prevent races, invitations require a verified email and expire after 7 days,
  removal takes effect immediately, attribution is stamped by the server, and the Owner-is-never-a-Partner rule is
  backed by a trigger.
- **Billing follows the right shape:** the server asks Google, the grant and revoke functions are `service_role` only
  (verified in production), purchase tokens are stored only as SHA-256, product and package are checked, there is a
  daily refund sweep plus RTDN, secrets are compared in constant time, and no request body is logged.
- **Endpoints fail closed:** every route answers 401 without a valid Supabase token, 503 when not configured, and
  `Cache-Control: no-store`. Astro's origin check blocks cross-site form posts.
- **Secrets hygiene:** history is clean in both repositories; the build refuses a service-role key and refuses a debug
  build pointed at the production project or proxy; keystores live outside the repositories; CI uses
  `pull_request` (not `pull_request_target`) with `contents: read`.
- **Android release hardening:** a single exported launcher activity, a non-exported FileProvider scoped to two cache
  folders, `allowBackup=false` plus data-extraction exclusions, `cleartextTrafficPermitted=false` with system CAs only,
  R8 minify and shrink, no WebView, a permission allow-list enforced in CI, test store and plan simulator only in
  `BuildConfig.DEBUG`, and a Keystore-sealed entitlement cache.
- **Session lifecycle:** durable sign-out writes; a server revoke queued before the local wipe; a wipe marker that
  survives process death; switching accounts wipes the previous ledger (with confirmation when changes are unsynced).
- **Privacy:** crash reporting is off until chosen and carries no user id or keys; usage counts use a random install id
  with a 90-day purge running in production (`cron.job` verified); exchange rates send only currency codes; account
  deletion cascades every user-owned table; the in-app policy lists every network path.
- **Existing SQL suites** (180 checks) run every migration under both Supabase privilege defaults. This is unusually
  strong regression protection for RLS.

---

## 7. Remediation roadmap

| Order | Item | Effort | Owner of the action |
|---|---|---|---|
| 1 | **F-05**: confirm the Gemini key is on the paid tier | 5 min | Owner (Google Cloud Console) |
| 2 | **F-06**: confirm "Confirm email" ON; turn on leaked-password protection; check the redirect allow-list | 10 min | Owner (Supabase dashboard) |
| 3 | **F-01**: one migration (CLOUD capability in write policies, size CHECKs, row ceiling, `usage_events` behind a rate-limited function); a usage alert; decide plan or spend cap | 1 day + suites | Engineering + owner decision |
| 4 | **F-02, F-03**: refund tombstone table; obfuscated-id check on first link; app-side filter; new SQL suite cases | 0.5 day | Engineering |
| 5 | **F-04**: Astro 7 / `@astrojs/vercel` 11 upgrade on a branch, full verify + e2e | 0.5–1 day | Engineering |
| 6 | **F-08, F-09, F-11**: OPTIONS handler, security headers, Vercel WAF rate limits | 2 h | Engineering |
| 7 | **F-13, F-14, F-15**: default-ACL revoke migration, migration history back-fill, Dependabot + website CI + SHA-pinned actions | 0.5 day | Engineering |
| After launch | F-07 (seed ids), F-10 (Pub/Sub OIDC), F-12 (test purchases), F-16 (FLAG_SECURE default), F-17 (encrypted backups), I-1 / I-4 copy | — | Engineering / product |

Every SQL change should come with new cases in `tools/sql-verify` (the probes in §5 are a ready starting point) and
should pass both `npm run verify` and `npm run verify:stock` before it touches any project.

---

## 8. Production release decision

### NOT APPROVED

**Why:** F-01 is an open High: one free account, or a caller who isn't signed in, can make the production database
read-only for every user, and the paid Cloud service can be used for free. The rubric requires no unresolved High.

**What would change it to APPROVED WITH CONDITIONS:**

1. F-01 fixed: the migration applied, the suites green, and the production catalog re-checked.
2. F-02 and F-03 fixed before the first real purchase.
3. F-05 and F-06 confirmed by the owner (Gemini paid tier; Confirm email on).
4. F-04 upgraded, or explicitly accepted by the owner as a residual risk with a dated plan.
5. The real-purchase checks in LP-K12 (purchase, acknowledgement, RTDN, refund) completed on the internal track.

**What this audit doesn't say:** It doesn't certify production *behaviour* (only its catalog), Supabase Auth
settings, the Play or Google Cloud configuration, or the Android dependency tree's CVE status. These are listed in §2
and carried into the checklist.
