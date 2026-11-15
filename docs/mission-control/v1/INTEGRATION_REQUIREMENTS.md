# Integration Requirements — V1

Each integration lists what V1 uses, the credential, the minimum permission, and the owner's setup steps. All
credentials live in Vercel environment variables (Production only) for the Mission Control project. None reaches the
browser.

## 1. Supabase (production project)

**V1 uses:** admin sign-in; every read and write through `mc_api` functions; hourly and daily jobs (pg_cron).

| Requirement | Detail |
|---|---|
| Admin account | A dedicated admin login in production Auth, separate from your customer account (e.g. an `ops@` address you control), with a **TOTP factor** enrolled (Supabase MFA, available on Free) |
| Allowlist | One row in `mc.admins` for that account, inserted by you in the SQL editor. Mission Control also checks `MC_ADMIN_EMAILS` |
| Exposed schema | Add `mc_api` to *Settings › API › Exposed schemas*, so Mission Control can call its functions with your own session. `mc` (the data) stays unexposed |
| Keys | The **publishable key** only (as the app already uses). **No service-role or secret key in V1**, unless FD-V6 (email edits) is approved |
| Migrations | The V1 migration files (DATABASE_PROPOSAL.md), applied by you to lucento-test first, then production |
| Optional (FD-V9) | A personal access token for the Management API, only to show recent errors. It's account-wide (it can manage projects), so V1 works without it and links to the dashboard's Logs instead |

Environment: `NEXT_PUBLIC_SUPABASE_URL`, `NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY`, `MC_ADMIN_EMAILS`, optional
`SUPABASE_MANAGEMENT_TOKEN`. Preview deployments use lucento-test values.

## 2. Google Play (amounts for Revenue)

**V1 uses:** the Orders API (`orders.batchget`, up to 1,000 order ids per call) to fetch the listing price, tax and
total for order ids already stored in `purchase_links`, plus each live subscription's latest order id (read with
`purchases.subscriptionsv2.get`). It runs once a day and on demand. No website change needed.

| Requirement | Detail |
|---|---|
| Service account | **Recommended:** a new one for Mission Control. *Play Console › Users and permissions › Invite* with **View app information** and **View financial data, orders and cancellation survey responses**. No order-management permission |
| API | Google Play Android Developer API enabled in the service account's Google Cloud project (already enabled for the website's account) |
| Fee assumption | Confirm enrolment in Play's 15 % service-fee tier; the net estimate uses it |

Environment: `PLAY_SERVICE_ACCOUNT_JSON`, `PLAY_PACKAGE_NAME=com.lucento.app`.

## 3. Firebase Crashlytics

**V1 uses:** daily queries over the Crashlytics BigQuery export for crash-free users and sessions, top issues, affected
users and versions.

| Requirement | Detail |
|---|---|
| Release builds report crashes | OT-18 (CI secret `GOOGLE_SERVICES_JSON`) done, and a release with it installed |
| BigQuery export | *Firebase console › Project settings › Integrations › BigQuery › Link*: enable **Crashlytics** and **Sessions**. Batch (daily) export works in the BigQuery sandbox, with no billing |
| Service account | BigQuery Data Viewer on the export dataset + BigQuery Job User |
| Expectation | Data appears up to 48 hours after linking. Numbers cover opted-in installs only |

Until then the Infrastructure page shows "Crashlytics: integration required" with these steps. Environment:
`GCP_BIGQUERY_SA_JSON`, `CRASHLYTICS_DATASET`.

## 4. Vercel

**V1 uses:** `GET /v6/deployments` and `GET /v13/deployments/{id}` for `lucento-website` and Mission Control's own project.

| Requirement | Detail |
|---|---|
| Token | An access token scoped to the team, with an expiry (90 days). Vercel tokens aren't read-only, so Mission Control's code only calls GET endpoints, filtered by project id (KlaroPH never shown) |
| Plan | Confirm the plan allows commercial use (FD-V1) |
| Mission Control project | A new project from the `lucento-mission-control` repo, domain `mission.lucentoapp.com` (CNAME at GoDaddy), Deployment Protection on previews |

Environment: `VERCEL_TOKEN`, `VERCEL_TEAM_ID`, `VERCEL_PROJECT_IDS`.

## 5. Resend

**V1 uses:** delivery webhooks; the emails list for recent sends; sending ticket replies and one-off transactional
emails. No receiving (support arrives through the app, FD-V4).

| Requirement | Detail |
|---|---|
| Plan | **Free tier for now (FD-V1).** 100/day and 3,000/month, shared with KlaroPH. Enter those limits in Mission Control Settings (Resend documents no usage API); the meter warns at 70 % and 90 % |
| Webhook | *Resend › Webhooks › Add*: `https://mission.lucentoapp.com/api/webhooks/resend`, events `email.sent`, `email.delivered`, `email.delivery_delayed`, `email.bounced`, `email.complained`, `email.failed`. Signing secret → `RESEND_WEBHOOK_SECRET` |
| Sending key | A new key with **sending access limited to `send.lucentoapp.com`** → `RESEND_SEND_KEY` |
| Reading key | A full-access key used only for listing emails → `RESEND_READ_KEY` (never used to send) |
| Senders | Replies from `Lucento Support <support@send.lucentoapp.com>`, Reply-To `lucentoapp@gmail.com`, `[LC-1042]` in the subject so answers are easy to match |

## 6. GitHub Actions backup (FD-V13)

| Requirement | Detail |
|---|---|
| Where | A workflow in the `lucento-mission-control` repo (private), weekly |
| Secrets (owner adds) | `SUPABASE_DB_URL`: production connection string (Supabase › Connect › Session pooler), `BACKUP_AGE_PUBLIC_KEY`: the public half of an encryption key you generate and keep offline, `MC_INGEST_SECRET`: to report the result to Mission Control |
| Output | An encrypted dump kept as a workflow artifact for 90 days; size and result shown on Infrastructure |
| Restore | Rehearsed once before launch, documented in the repo |

## 7. Android app (approved for FD-V4 and FD-V7)

No new credential: the app already holds the publishable key. It calls `public.submit_support_request` (signed in or
out) and `public.report_client` (signed in). See PRODUCT_SCOPE.md "Android app changes".

## 8. Not used in V1

Gemini or any AI, Play earnings reports, Play Reviews, Play vitals, the Supabase metrics endpoint, Vercel log drains,
Resend broadcasts, audiences and receiving.

## Owner setup checklist (Phase 4, in order)

1. Decisions recorded (done, 2026-11-09).
2. Check the Vercel plan allows commercial use (no upgrades otherwise, FD-V1).
3. GitHub: create the `lucento-mission-control` repository (private); add the backup secrets.
4. Supabase: create the admin login, enrol TOTP, apply the migrations (test → production), add the `mc_api` exposed
   schema, insert your `mc.admins` row.
5. Google Play: invite the Mission Control service account.
6. Firebase: link BigQuery (Crashlytics + Sessions); grant the service account.
7. Vercel: create the token and the project; set the environment variables; add the domain.
8. Resend: keys, webhook, plan limits in Settings.
9. After the app release with the support form and policy text is live: turn on Records view in Settings.
