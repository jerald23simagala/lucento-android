# Existing Architecture Audit — what Mission Control would stand on

Discovery date: 2026-11-09. Read-only: repository files, Supabase catalog queries and aggregate counts on
production (`hmtcuttadbgbldvzsfhz`), Vercel project listing, Resend account listing. No customer rows were read and
nothing was written anywhere.

Status vocabulary used below: **Implemented** (in code and live) · **Built, not live** (code exists, not running in
production) · **Partial** · **Missing** · **Not feasible as briefed**.

---

## 1. Headline findings

1. **Lucento is pre-launch.** Production holds 5 auth accounts (all email-confirmed; email + Google providers),
   0 entitlement rows, 0 purchase links, 0 usage events, 3 account-email claims, a 13 MB database. A full business
   intelligence suite would chart near-empty series for months. The first build should be the operational core
   (entitlements, support, health, audit), with BI growing as data arrives.
2. **Three promises in the published Privacy Policy block three requested modules as briefed.**
   `feature/legal/LegalDocuments.kt` (mirrored at lucentoapp.com/privacy) says:
   - *"Database rules let only your signed-in account — and people you share with through Lucento Shared — read or
     change your records."* → **Customer Investigation** (reading wallets, transactions) contradicts this today.
   - *"We send emails only about your account."* (and `docs/EMAILS.md`: "no newsletters, no marketing") →
     **Marketing Command Center** contradicts this today.
   - *"Usage counts aren't linked to your account."* → per-user **Product Intelligence** (feature usage per customer)
     contradicts this today.
   Each needs a policy change, a consent mechanism in the Android app, and a release before the module can run on real
   customers. These are founder decisions (FOUNDER_DECISIONS.md FD-1 to FD-4).
3. **Complimentary Pro already has the right shape.** `public.entitlements` has a `GRANT` source, kept separate from
   the Play sources, and `has_capability` ORs across rows, so revoking a grant can never remove a purchase. What's
   missing is history, the identity of whoever granted it, reasons, and the constraint that blocks a "Lifetime"
   complimentary grant (see §4).
4. **Real recurring revenue is possible.** The brief assumes Lucento sells only lifetime purchases. It sells three
   products: `lucento_pro` (subscription, monthly ₱149 / annual ₱1,190 with a 7-day trial), `lucento_pro_lifetime`
   (one-time, ₱3,490, founder offer ₱2,990), and `lucento_cloud` (add-on subscription, ₱59 / ₱490). MRR is real once
   subscriptions sell. Lifetime sales must stay out of MRR, and the add-on that Lifetime owners buy after their
   included year *is* MRR.
5. **No revenue amounts are stored anywhere.** The billing endpoints store product, status, dates, order id and a
   token hash, but no price, currency, tax or country. Revenue has to come from Google Play (Orders API, financial
   reports), never from Lucento's own tables.
6. **Infrastructure risks that matter more than a dashboard:**
   - Supabase production runs on the **Free plan**: no usable backups or point-in-time recovery for a product that
     sells purchases and holds people's ledgers (verify in the dashboard; see WHAT_WE_MISSED #1).
   - Resend runs on the **free tier**: 100 emails/day and 3,000/month, shared with another product (KlaroPH) on the
     same account. Each email sign-up costs two emails (verify + welcome), so about 50 sign-ups a day hit the cap at
     launch. Supabase's auth email limit is still the default 30/hour.
   - Vercel team `jerald23simagala's projects` looks like a personal Hobby team (the name is the Hobby default; the
     API didn't return the plan). Hobby is for non-commercial use, and the billing endpoints sell Pro. Verify the plan.
7. **The brief's stack assumptions differ from the repos.** The website is **Astro 5** on Vercel, not Next.js. The AI
   features use **Google Gemini** through the website's proxy, not OpenAI. There are **no Supabase Edge Functions**:
   server logic lives in Vercel functions under `lucento-website/src/pages/api/`.

---

## 2. Repositories and deployments

| Repo / project | What it is | Notes for Mission Control |
|---|---|---|
| `lucento-android` | Kotlin, Compose, Room; the app | Owns the Supabase migrations (`supabase/migrations/`), SQL verification (`tools/sql-verify`, PGlite), legal texts |
| `lucento-website` | Astro 5 + `@astrojs/vercel`; site + server API | `api/ask`, `api/interpret` (Gemini), `api/billing/{verify,rtdn,voided}`, `api/account/welcome`; daily cron `/api/billing/voided` 03:17 UTC |
| `lucento-studio` | Design studio and critic workflow | Design review loop available for the prototype |
| Vercel `lucento-website` | `prj_6nk9mu4QUYB7uJNAVOp6j3m7Zo53` | Production at www.lucentoapp.com; holds the service-role key, Play service account, Resend key, Gemini key |
| Vercel `klaroph-app` | A different product, same team | Mission Control must never get a token that can touch it |
| `lucento-mission-control` | Doesn't exist | To be created on approval |

DNS for `lucentoapp.com` is at GoDaddy, not Vercel. `mission.lucentoapp.com` needs a CNAME there.

## 3. Supabase

**Projects.** Production `lucento` (`hmtcuttadbgbldvzsfhz`, ap-northeast-2, Postgres 17, `ACTIVE_HEALTHY`, Free
plan). Test `lucento-test` (`ogenvdopfgocjdkcrsny`, used by debug builds). The Supabase MCP currently points at
production.

**Schemas.** `public` (app data), `lucento_private` (plan limits and helper functions), plus Supabase's own (`auth`,
`storage`, `realtime`, `vault`, `cron`, `graphql`). Installed extensions: `pg_cron` 1.6.4, `pg_stat_statements`,
`pgcrypto`, `supabase_vault`, `uuid-ossp`. Available but not installed: `pgaudit`, `pg_net`, `pgmq`, `pgsodium`,
`wrappers`.

**Tables (production, row counts 2026-11-09).** RLS is enabled on every one; 45 policies in `public`.

| Table | Rows | Owner | Client access | Mission Control relevance |
|---|---|---|---|---|
| `wallets` | 9 | user | own rows (sync) | product usage counts; investigation (needs consent) |
| `categories` | 41 | user | own | usage |
| `transactions` | 51 | user (Shared: owner's ledger, `created_by` actor) | own + space | usage; investigation |
| `budgets` | 9 | user | own | usage |
| `goals` / `goal_contributions` | 3 / 0 | user | own | usage |
| `recurring` | 6 | user | own | usage |
| `ledger_settings` | 0 | user | own | settings inspection |
| `shared_spaces` / `space_members` / `space_invites` | 1 / 0 / 0 | owner | security-definer RPCs | Shared support cases |
| `entitlements` | 0 | server | select own | **core of Module 2** |
| `purchase_links` | 0 | server | none | purchase ownership disputes |
| `ai_usage` | 7 | server | select own | AI adoption and cost |
| `usage_events` | 0 | anonymous | insert only | activation funnel (anonymous) |
| `account_emails` | 3 | server | none | transactional email ledger |
| `lucento_private.plan_limits` | 4 | server | none | remote quotas (AI, partners) |

Every user-owned table cascades from `auth.users` on delete (`docs/ACCOUNT_DELETION.md`). Sync rows carry
`server_updated_at` (database clock), `revision` and `base_revision` (`20261025145902_server_clock_sync_cursor.sql`), plus
`deleted_at` tombstones. That gives a real, server-side "last synced" signal per account and table.

**Functions and triggers.** Last-write-wins and server-stamp triggers on every synced table; actor stamping on
`transactions`; Shared RPCs (`create_shared_space`, `create_space_invite`, `accept_space_invite`, …); entitlement
helpers (`has_capability`, `plan_limit`, `max_partners`, `consume_ai_request`, `consume_ai_quota`); purchase writers
(`apply_play_purchase`, `revoke_play_purchase`, service role only); `delete_my_account`; `claim_account_email` /
`release_account_email`; `purge_usage_events` and `delete_usage_events`.

**Scheduled jobs.** One: `purge-usage-events`, daily at 03:15 UTC, active.

**Edge Functions.** None.

**Not visible from the MCP:** the plan's backup settings, auth configuration (SMTP sender, rate limits, templates),
API request metrics. Those need the dashboard or the Management API with a personal access token.

## 4. Identity, entitlements and the complimentary path

- **Auth.** Supabase Auth, email/password (with a Have I Been Pwned k-anonymity check) and Google. 0 MFA factors on
  production. Signing out or switching accounts wipes the phone's ledger (`AccountBoundary`).
- **Entitlement model** (`20261107090000_entitlements.sql`). One row per `(user_id, source, coalesce(product_id,''))`.
  Sources: `PLAY_SUBSCRIPTION`, `PLAY_LIFETIME`, `PLAY_LIFETIME_SERVICES`, `PLAY_CLOUD_ADDON`, `GRANT`. Grants are a
  subset of `LOCAL_PRO`, `CLOUD`, `AI_PRO` and `SHARED`. Only the service role writes. The app reads its own rows and
  caches them sealed with the Android keystore.
- **What a complimentary grant can do today:** an owner-run SQL insert of a `GRANT` row. That is how the 30-day test
  grant was given (memory, 2026-11-09).
- **Gaps for Module 2:**
  - `entitlements_grant_expires` requires every `GRANT` to have `expires_at`, so a **Lifetime complimentary grant is
    impossible** without a schema change (FD-9).
  - The unique index allows **one `GRANT` row per account**. Extending or stacking grants overwrites in place and
    loses history. Mission Control needs its own grant ledger (`mc.entitlement_grants`) that derives the single live
    row.
  - `note` (≤ 200 characters) is the only "why". There is no granted-by, granted-at (only `starts_at` and
    `updated_at`), reason code, or revocation record.
  - Revoking a grant is safe by construction: Play rows are separate rows, and `has_capability` checks any live row.
- **Purchase ownership.** `apply_play_purchase` can return `review_required` (third move of a purchase between
  accounts), whose resolution says "needs support". No support tool exists for it. This is a concrete day-one Mission
  Control job.

## 5. Google Play billing

- **Implemented and live** (website #11–#12): `POST /api/billing/verify` (Play Developer API `subscriptionsv2.get` /
  `productsv2.getproductpurchasev2`), `POST /api/billing/rtdn` (Pub/Sub push, token-protected), and
  `GET /api/billing/voided` (daily Voided Purchases API sweep). All re-verify with Google and never trust the client.
- **Not yet exercised against real Play purchases.** The Play Console checklist (`docs/monetization/PLAY_CONSOLE_SETUP.md`)
  and licence-tester run are still open owner steps.
- **Stored:** product, base plan, status, start and expiry, order id (in `purchase_links`), token hash, an obfuscated
  account id. **Not stored:** price, currency, tax, region, Google's fee, payout.
- **Revenue sources to add:** the Orders API (`orders.get` / `orders.batchget`: line-item listing price, tax and total
  per order), monthly earnings and sales reports in the developer's Cloud Storage bucket, and the Play Console UI.
  Payouts land in a bank account, which no API reaches.

## 6. Analytics and event tracking

- **Implemented, but no data in production.** `usage_events`: opt-in (off by default), a random install id, 8
  allowlisted events (`onboarding_started`, `onboarding_step_viewed`, `first_entry_saved`, `d1_return`, `d7_return`,
  `first_week_card_step_done`, `budget_suggestion_shown`, `budget_suggestion_accepted`), props limited to `step`, `ms`,
  `day_index`, `count`, 90-day purge, and opting out deletes the rows. No account link by policy.
- **Consequences:**
  - DAU/WAU/MAU of *all users* can't be measured from Lucento's own data. Local-only users (no account) are
    invisible by design. Play Console's statistics are the only all-install source.
  - An activation funnel exists for opted-in installs only. It's a sample, not a census, and should be labelled that
    way.
  - Feature adoption (wallets, budgets, goals, debts, recurring, AI) can be counted **server-side for synced accounts**
    from row counts and `ai_usage`, with no new tracking. It covers only people who chose sync.
  - Raw events are purged after 90 days, so nothing preserves history. Daily aggregate snapshots are needed before any
    trend chart can exist (WHAT_WE_MISSED #3).
- **Not present:** app version, device or country per account. `usage_events.app_version` is per install and
  anonymous.

## 7. Sync

Opt-in (`docs/SYNC.md`). Room is the source of truth. The Supabase tables mirror it per user, with last-write-wins
enforced by triggers, a server-clock cursor, and soft deletes. Lucento Shared lets a Partner write into an Owner's
ledger, with `created_by` stamped by trigger. Sync failures are visible on the phone only. **No server-side sync error
log exists**: a rejected stale write is silently ignored by the LWW trigger, and PostgREST errors surface only in the
Supabase API logs. A "sync health" view for support would need either API log access (Management API / log explorer)
or a small client-reported sync status (needs consent wording).

## 8. Firebase Crashlytics

- `core/crash/CrashReporting.kt`: opt-in (off by default), sanitized (no exception messages, no user id, no custom
  keys), so crashes **can't be tied to a customer**. That's by design and should stay.
- Release builds include Crashlytics only when CI has the `GOOGLE_SERVICES_JSON` secret (owner step OT-18, PR #31). It's
  unconfirmed whether production releases ship with it yet.
- **There is no public REST API for Crashlytics aggregates.** Programmatic access means the Crashlytics → BigQuery
  export (daily batch free in the BigQuery sandbox; streaming needs the Blaze plan), plus Firebase sessions export for
  crash-free users. Alternative or complement: the **Play Developer Reporting API** (Android vitals: crash rate, ANR
  rate, error issues and clusters, by version and device), which covers every user who shares diagnostics with Google,
  regardless of Lucento's opt-in.

## 9. Vercel

- Team `team_HlvPJQ8bo4gEihyecw135oKl` with two projects: `lucento-website` and `klaroph-app`.
- One cron (voided purchases). No Vercel Web Analytics or Speed Insights on the site.
- The REST API exposes deployments, build events, runtime logs and project domains. The MCP connector works for
  read-only listing. A Mission Control token should be a team-scoped access token with an expiry. Vercel tokens can't
  be made read-only per project, which is why the token stays server-side and is used by read-only code paths only
  (see SECURITY_ARCHITECTURE.md §7).

## 10. Resend

- Domains: `send.lucentoapp.com` (verified, sending on, receiving off, **open and click tracking off**) and
  `klaroph.com` (another product).
- 0 webhooks, 0 topics, 1 segment ("General", created for KlaroPH in March), 0 contacts.
- Usage: 15 emails sent this period; limits 100/day, 3,000/month, 1,000 contacts, 3 segments, 10 requests/second.
- **Consequences:**
  - Delivery, bounce and complaint monitoring needs webhooks (none exist).
  - Opens and clicks aren't tracked, by choice. Turning tracking on adds tracking pixels and rewritten links to
    account emails, which the Privacy Policy would have to disclose. Recommendation: keep it off for transactional
    mail.
  - Marketing needs Topics (unsubscribe preferences), a consented contact list, a separate subdomain, and a paid plan.
- Transactional senders: Supabase Auth over SMTP (verify, reset password) and the website over the API (welcome, Pro
  confirmation, deduplicated through `account_emails`).

## 11. Support and feedback

- **Missing as a system.** Support is the Gmail inbox `lucentoapp@gmail.com` (named in the policy, the deletion page,
  and email reply-to). There's no in-app feedback or contact form and no ticket store. The account-deletion-by-email
  procedure is manual (find the user in Supabase Authentication › Users, delete).
- Resend now offers inbound email and inboxes, which could feed a ticket store (`support@lucentoapp.com`). That needs
  receiving turned on for a domain (an MX record at GoDaddy).

## 12. Security posture relevant to an admin console

- Good foundations: RLS everywhere, `auth.uid() = user_id` policies, grants to `authenticated` only, service-role-only
  writers, `search_path = ''` on definer functions, an offline SQL verification suite, CI permission allow-lists.
- No admin concept exists: no admin table, no audit log of operator actions, no MFA on any account.
- Today every privileged operation is the owner pasting SQL into the production SQL editor. That's the riskiest
  "admin console" possible: no confirmation, no audit, no least privilege. Mission Control's first value is replacing
  it.

## 13. Integration inventory

| Integration | State | Credentials Mission Control would need | Feasibility |
|---|---|---|---|
| Supabase DB (production) | Implemented | A dedicated least-privilege Postgres role (not the service role) for `mc` functions; the service role only inside narrowly scoped server functions that call Auth admin APIs | Feasible |
| Supabase Auth admin (ban, recovery, email change, delete) | Not used | Service-role/secret key, server-side only | Feasible |
| Supabase Management API (logs, usage, advisors) | Not used | Personal access token (account-wide!) | Feasible; token scope is broad, so isolate it |
| Supabase Metrics endpoint (Prometheus) | Not used | Secret API key | Verify availability on the Free plan |
| Google Play Developer API (purchases) | Implemented (website) | Existing service account | Feasible |
| Play Orders API (amounts, tax) | Not used | Same service account + "View financial data" permission | Feasible |
| Play financial reports (Cloud Storage) | Not used | Service account with access to the `pubsite_prod_…` bucket | Feasible, monthly granularity |
| Play Developer Reporting API (vitals) | Not used | Service account + Reporting API enabled | Feasible |
| Play Reviews API | Not used | Same service account ("Reply to reviews") | Feasible |
| Firebase Crashlytics | Partial (SDK; release secret pending) | BigQuery export + service account | Feasible via BigQuery only |
| Vercel REST | Not used | Team-scoped token with expiry | Feasible (read paths) |
| Resend (send, logs, webhooks, broadcasts, inbound) | Partial (send only) | A new key per purpose; webhook signing secret | Feasible; plan limits |
| Gemini | Implemented (proxy) | Reuse the key or issue a separate one for Mission Control | Feasible |
| OpenAI | **Not present** | n/a | Not needed (FD-12) |
| Next.js | **Not present** (website is Astro) | n/a | Mission Control may still choose Next.js (FD-6) |

## 14. Architectural constraints Mission Control must respect

1. Room stays the source of truth. Mission Control can only see what people chose to sync, and must never imply
   otherwise ("No cloud data: this account syncs nothing" is a valid, common state).
2. Supabase schema changes ship as new migration files in `lucento-android/supabase/migrations/`. The owner applies
   them; never edit applied DDL. Mission Control's schema (`mc`) follows the same rule, even though its app code lives
   in a separate repo.
3. Every user-owned table keeps RLS with grants to `authenticated` only. The `mc` schema is invisible to `anon` and
   `authenticated`.
4. Only the publishable key may reach any client. Mission Control's browser bundle holds **no** Supabase key at all.
5. The money rules apply to revenue: amounts in minor units (or micros from Play) with an ISO 4217 code, no floating
   point, no implicit currency conversion. A PHP total of USD sales must state its rate and source.
6. Account deletion must keep matching `delete_my_account`'s cascade and the published deletion page.
