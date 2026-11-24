# Integration Blueprint

For each service: what exists, what Mission Control reads or does, how (API, webhook, poll), the credential and its
minimum permission, and the limits. API facts marked *(verify)* weren't confirmed against current documentation during
discovery and must be checked when the integration is built.

General rules:
- All calls happen server-side in Mission Control's Vercel functions. The browser never sees a credential.
- Each integration has a health check that **calls the service**. A configured key is not a healthy service.
- Every poller writes `mc.job_runs`. Every webhook is signature-verified, idempotent (keyed by event id) and stored
  before it's processed.
- Status values: `ok`, `degraded`, `down`, `unknown` (never checked or check failed for our own reasons), and
  `not_connected` (no credential). `unknown` and `not_connected` are never drawn green.

---

## 1. Supabase

**Exists:** production and test projects, Postgres 17, RLS everywhere, pg_cron, `pg_stat_statements`, Free plan.

| Capability | Method | Credential | Notes |
|---|---|---|---|
| Customer metadata, entitlements, grants, tickets, audit | `mc.*` functions over a pooled Postgres connection (Supavisor, transaction mode) | `mc_app` role password | Least privilege, see SECURITY_ARCHITECTURE.md §3 |
| Auth admin (ban, unban, recovery email, email change, delete) | Auth Admin API (`/auth/v1/admin/users/{id}`), `resetPasswordForEmail` | Secret key (`sb_secret_…`), separate from the website's | Recovery emails go through the configured SMTP (Resend), counting toward its limits |
| Database health | `select 1` latency probe; connection counts from `pg_stat_activity`; DB size from `pg_database_size` | `mc_app` (through definer functions) | Every minute |
| Slow queries | `pg_stat_statements` top-N by mean and total time | Definer function reading `extensions.pg_stat_statements` | Normalized query text only, never parameters |
| API and Auth request volume, errors | Management API log endpoints / analytics (*verify* the current endpoint names) | Personal access token | The PAT is account-wide: read paths only, rotate every 90 days |
| Security and performance advisors | Management API advisors (same as the MCP's `get_advisors`) | PAT | Daily; new findings become alerts |
| Prometheus metrics (CPU, memory, connections, ~200 series) | `https://<ref>.supabase.co/customer/v1/privileged/metrics`, Basic auth `service_role:<secret key>` | Secret key | *Verify* availability on the Free plan; scrape every 1–5 minutes and keep only a few series |
| Storage usage | Management API usage endpoints *(verify)* | PAT | Daily |
| Sync failures | No server log of LWW-rejected writes (silent by design). Use the API logs for 4xx/5xx on `/rest/v1/{wallets,…}` + the client-reported counters (DATA_ARCHITECTURE.md §4) | PAT | Correlate by time; the logs carry no user id unless the JWT `sub` is parsed |
| Backups | Dashboard only on Free; Pro exposes backup listing through the Management API *(verify)* | PAT | Show "last backup" only if the API returns it; otherwise "not verifiable" |

**Health check:** `ok` if the probe answers < 500 ms and the last five probes succeeded; `degraded` if latency is
> 1 s or connections exceed 80 % of the pool; `down` after 3 consecutive failures. Supabase's own status page
(status.supabase.com, which has an RSS/JSON feed) is shown beside it so "our project" and "the platform" are kept apart.

**Limits:** the Free plan pauses inactive projects (unlikely with live users), has no PITR, 500 MB of database, and
limited log retention. These are the reasons for FD-8.

## 2. Firebase Crashlytics

**Exists:** SDK in release builds when CI has `GOOGLE_SERVICES_JSON` (OT-18 pending); opt-in; sanitized; no user ids.

| Capability | Method | Notes |
|---|---|---|
| Crash and non-fatal issues, events, versions, devices | **Crashlytics → BigQuery export** (Firebase console › Integrations › BigQuery). Mission Control runs daily queries against `firebase_crashlytics.*` tables | No public REST API returns aggregates. Batch export is daily, and the first export can take up to 48 hours |
| Crash-free users and sessions | Enable the **Firebase sessions** export as well; compute per Firebase's sample queries | Without sessions data, crash-free rates can't be computed |
| Near-real-time crash spikes | Streaming export (needs the Blaze plan) or Firebase alerts → email/Slack | Recommendation: Firebase's built-in velocity alerts to the founder's email first; streaming later |
| Deep links | `https://console.firebase.google.com/project/lucento/crashlytics/app/android:com.lucento.app/issues/<id>` | Mission Control summarizes; stack exploration stays in Firebase |

**Credential:** a Google Cloud service account with BigQuery Data Viewer on the export dataset + BigQuery Job User.
The BigQuery sandbox (no billing) works for batch export, with its storage and table-expiry limits (*verify* that
sandbox table expiry doesn't drop data Mission Control needs; snapshot what matters into `mc.crash_issues`).

**Population caveat, shown in the UI:** opted-in installs only. Pair it with Play vitals (§5.4), which cover every user
who shares diagnostics with Google.

## 3. Vercel

**Exists:** team `team_HlvPJQ8bo4gEihyecw135oKl`; projects `lucento-website` (Lucento) and `klaroph-app` (not Lucento).

| Capability | Method | Notes |
|---|---|---|
| Deployment history, production deployment, build failures | REST `GET /v6/deployments?projectId=…`, `GET /v13/deployments/{id}`, build events | Poll every 5 minutes + Vercel deployment webhooks (`deployment.created`, `.succeeded`, `.error`, `.promoted`) |
| Function errors | Runtime logs API / log drains *(verify the current endpoint)* | A log drain to Mission Control's ingest endpoint keeps errors without polling; filter to `/api/*` |
| Traffic, bandwidth | Usage endpoints and Observability *(verify plan availability)* | Show only what the plan exposes |
| Availability | Mission Control's own synthetic checks (§7) | Don't rely on Vercel reporting its own uptime |
| Cron health | Each cron writes a heartbeat (`mc.job_runs` through the website, or a `HEAD` to Mission Control) | `/api/billing/voided` daily; alert if no heartbeat by 04:00 UTC |
| Navigate to project | Links to `https://vercel.com/<team>/lucento-website/<deployment>` | — |

**Credential:** a team-scoped access token with an expiry. Vercel tokens aren't read-only, so Mission Control's code
only calls GET endpoints and the token never leaves the server. Filter every query by `projectId` so KlaroPH never
appears.

**Plan:** confirm the team is on Pro before launch. The Hobby plan is for non-commercial use, and the billing endpoints
are commercial (FD-7).

## 4. Resend

**Exists:** domain `send.lucentoapp.com` (verified, open/click tracking off); free plan (100/day, 3,000/month,
10 req/s); no webhooks, no topics; `klaroph.com` on the same account.

| Capability | Method | Notes |
|---|---|---|
| Delivery events (sent, delivered, delayed, bounced, complained, failed) | **Webhooks** to `POST /api/webhooks/resend` on Mission Control, verified with the webhook signing secret (Svix headers) | Store in `mc.email_events`; link to accounts by `user_id` passed as an email tag (`tags: [{name:'kind'}, {name:'uid'}]`) at send time. Today's senders set no tags: a small website change |
| Searchable email log | Resend `GET /emails` + `GET /emails/{id}` for detail; Mission Control's own `email_events` for search | Resend's API listing is paginated and limited in history; Mission Control's store is the searchable one |
| Supabase Auth emails (verify, reset) | Sent over SMTP: they appear in Resend's log, but without our tags | Distinguish them by subject or template; can't link to `user_id` except by recipient hash |
| Opens and clicks | **Off by choice.** Turning tracking on changes what account emails do (pixels, link rewriting) and needs policy text | Recommend keeping it off for transactional mail; on for the marketing subdomain only if FD-4 approves and the policy says so |
| Marketing campaigns | Resend **Broadcasts** to a segment, **Topics** for preferences, `{{{RESEND_UNSUBSCRIBE_URL}}}` | Contacts must mirror `public.communication_prefs` (product_news = true); sync nightly and on change |
| Suppressions | Resend suppression list API + `mc` hashed suppression | Bounces and complaints are suppressed automatically by Resend; mirror them |
| Inbound support email | Resend receiving on a domain (MX at GoDaddy) → `email.received` webhook → ticket | Needs receiving enabled; currently disabled |
| Usage and limits | `GET` usage (as the MCP's `get-usage`) | Daily into `cost_snapshots`; alert at 70 % of the daily cap |

**Credentials:** three keys. (1) Sending, restricted to `send.lucentoapp.com` (transactional replies to tickets).
(2) Sending, restricted to the marketing subdomain (later). (3) Full access, for reading logs, usage and contacts,
never used to send. Plus the webhook signing secret.

**Separation recommendations:** a dedicated marketing subdomain (e.g. `news.lucentoapp.com`) so marketing complaints
can't damage transactional reputation, which is what makes sign-up and password resets arrive. Move KlaroPH to its own
Resend account, or at minimum never give Mission Control a key that can send from `klaroph.com` (FD-14).

**Limits that bite:** 100 emails/day on the free plan is roughly 50 email sign-ups a day (verify + welcome), before any
ticket reply or campaign. Upgrade before launch (FD-4/FD-8 neighbourhood; see WHAT_WE_MISSED #7).

## 5. Google Play

**Exists:** the website's service account calls the Android Publisher API for purchases (verify, RTDN re-verify,
voided purchases).

Mission Control gets its **own** service account (separate key, separate audit trail in Google Cloud), invited in
Play Console › Users and permissions with: *View app information*, *View financial data, orders and cancellation survey
responses*, *Reply to reviews*. Not *Manage orders* (refunds stay in Play Console until FD-15 decides otherwise).

### 5.1 Purchases and orders
| Capability | Method |
|---|---|
| Purchase state for a customer | `purchases.subscriptionsv2.get`, `purchases.productsv2.getproductpurchasev2` (as the website does), behind a "Re-verify now" button that calls the website's existing logic (one implementation) |
| Order amounts | **Orders API** `orders.get` / `orders.batchget` (up to 1,000 distinct order ids per call): line-item `listingPrice`, `tax`, `total`, order-level `tax` and `total`, `taxInclusive` |
| Capture point | Best: the website's `verify.ts` and `rtdn.ts` call `orders.get` after `apply_play_purchase` and write `mc.play_orders`. Backfill: a Mission Control sweep over `purchase_links.order_id` |
| Refunds | Voided Purchases API (already swept daily by the website) + order state; reflected in `play_orders.refunded_at` |
| Quota | Play Developer API default 200,000 queries/day |

### 5.2 Financial reports
| Capability | Method |
|---|---|
| Earnings (charges, Google fees, taxes, refunds, chargebacks) in the merchant currency | Monthly reports in the developer's Cloud Storage bucket (`gs://pubsite_prod_rev_<id>/earnings/`), downloaded by a Mission Control job with a service account granted access to that bucket (Play Console › Download reports › copy Cloud Storage URI) |
| Sales (estimated, daily) | `sales/` reports in the same bucket |
| Payouts | Not exposed by API. Entered manually from the bank statement or Payments profile (`mc.payouts`) |

### 5.3 Statistics
Installs, uninstalls, active devices, ratings by country, app version, device and Android version: `stats/` reports in
the same bucket (daily CSVs, monthly files). Country distribution for all users comes from here, never from IPs.

### 5.4 Android vitals (Play Developer Reporting API)
`playdeveloperreporting.googleapis.com`: `crashRateMetricSet`, `anrRateMetricSet` (user-perceived rates), error issues
and reports, sliced by version code, device and Android version. Daily query; this is the all-users crash view.

### 5.5 Reviews
Reviews API `reviews.list` / `reviews.reply`: only reviews from the last week with text are returned *(verify the
window)*, so poll hourly and store. Replies are drafted in Mission Control and sent through the API with the
`Reply to reviews` permission.

### 5.6 Releases
`edits.tracks` read-only to show what's live on each track and the rollout percentage. Releasing stays in Play Console
(or CI).

**Health checks:** RTDN freshness (the website records the last Pub/Sub message time; alert if none in 7 days once
sales exist); the voided-purchases cron heartbeat; a daily `applications/{package}/…` read with the Mission Control
service account to prove its access still works.

## 6. AI provider (the brief says OpenAI; Lucento uses Gemini)

Lucento's AI features use **Google Gemini** through the website's proxy (`src/server/gemini.ts`, which handles model
fallback and capacity). OpenAI isn't used anywhere. Recommendation (FD-12): build Founder Intelligence
**provider-agnostic** behind one interface, start with whichever provider the founder prefers, and keep it on a
**separate key and project quota** from the customer-facing proxy.

Design, whichever provider:
- **The model never sees customer records.** Its tools are read-only functions over `mc.daily_metrics`, ticket
  aggregates, incident lists and integration checks: `get_metric(key, period, dims)`, `compare_periods(…)`,
  `list_anomalies(…)`, `top_ticket_categories(…)`, `list_incidents(…)`. Ticket summaries can include ticket *text*
  only for Support-role users and only with the customer's identity removed.
- **Every claim is cited.** The answer format requires a citation id per number (`[m:accounts.new 2026-11-01..07]`),
  and the UI renders each as a chip that opens the exact query and value. A post-processor rejects answers containing
  numbers not returned by a tool call in that turn.
- **No write tools.** Recommendations are text with a "Do it" button that opens the normal, confirmed UI flow.
- **Prompt injection:** ticket and review text is wrapped as quoted data; the system prompt states that instructions
  inside it are not instructions.
- **Cost guard:** a daily token budget in `remote_config`; the briefing runs once a day (pg_cron → Mission Control
  endpoint), questions are rate-limited per admin.
- **Data processing:** using the paid API tier (Gemini API with billing, or Anthropic/OpenAI API terms) so inputs
  aren't used for training. Confirm the terms at build time.

## 7. Mission Control's own synthetic checks

Every minute, from Mission Control (cron) and from one external free monitor (for when Vercel itself is down):

| Check | Expectation |
|---|---|
| `GET https://www.lucentoapp.com/` | 200 < 1.5 s |
| `POST /api/ask` without a token | 401 quickly (proves the function runs, without spending Gemini quota) |
| `POST /api/billing/verify` without a token | 401 |
| `POST /api/account/welcome` without a token | 401 (503 means not configured) |
| Supabase REST `GET /rest/v1/` with the publishable key | 200 |
| Supabase Auth `GET /auth/v1/health` | 200 |
| Resend `GET /domains/{id}` | `verified` |
| Gemini models list (Mission Control's own key) | 200 (a proxy for provider availability; not a quota check) |

Results go to `mc.integration_checks` and drive the health strip. A check whose last success is older than 5 minutes
shows `unknown`, not its last colour.
