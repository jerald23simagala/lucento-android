# Data Source Inventory — the source of truth for every V1 number

Every metric Mission Control shows names its source in the UI. If a source isn't connected, the screen says what's
missing instead of showing a number.

**Availability:**
- **Now:** exists in production today.
- **Job:** needs a small Mission Control job or table, no external setup.
- **Setup:** needs an owner setup step or credential (INTEGRATION_REQUIREMENTS.md).
- **App:** needs an Android change (excluded from V1 unless you approve it).
- **No:** not obtainable.

## Users

| Metric | Definition in V1 | Source of truth | Availability |
|---|---|---|---|
| Total registered users | Accounts in `auth.users`, excluding Mission Control admins | Supabase Auth | Now |
| New users today / this month | `created_at` in the period (Manila time, configurable) | Supabase Auth | Now |
| Active users (DAU) | **Signed-in accounts** with app activity that day: a token refresh (`auth.sessions.refreshed_at`), a synced write (`server_updated_at`) or an AI request (`ai_usage`) | Hourly job → `mc.account_activity` | Job |
| Monthly active users (MAU) | Distinct accounts in `mc.account_activity` over the last 30 days | Same | Job (complete 30 days after the job starts) |
| People using Lucento without an account | Not knowable per person; Play Console's active-device count covers all installs | Google Play Console | No (V1 links out) |
| Free / Pro / Complimentary | Derived from live entitlement rows: **Pro (paid)** = any live PLAY_* row; **Complimentary** = a live GRANT row and no live paid row; **Free** = neither; **Trial** shown separately | `public.entitlements` | Now |
| Account status | Active · Unconfirmed (no `email_confirmed_at`) · Suspended (`banned_until` in the future) · Deletion scheduled (`mc.deletion_requests`) | Auth + `mc` | Now / Job |
| Last activity | Latest of the three activity signals above | `mc.account_activity` + live columns | Job |
| App version, Android version, phone model | `public.report_client` from the app, daily, signed-in accounts → `mc.account_clients` | **App** (approved, FD-V7): from the release that adds the support form; older installs show "not reported" |
| Wallets, transactions, budgets, goals, debts, recurring (counts) | Live, non-deleted rows per account | Synced tables | Now (people who sync only) |
| Last synchronization | Latest `server_updated_at` across the account's synced tables, and per table | Synced tables | Now |

**Caveat shown in the UI:** DAU and MAU count only signed-in accounts. Someone who uses Lucento without an account, or
signed in with sync off and no AI use, appears only through token refreshes.

## Revenue

| Metric | Definition | Source of truth | Availability |
|---|---|---|---|
| Purchases (count) | Distinct purchase tokens per product | `public.entitlements` / `purchase_links` | Now |
| Lifetime sales (count) | `lucento_pro_lifetime` purchases completed in the period, including ones refunded later (refunds have their own figure); not ones still waiting for payment *(changed 2026-11-15: was "not revoked", which disagreed with the amount)* | `public.entitlements` | Now |
| Lifetime sales (amount) | Σ order total − tax for Lifetime orders | **Google Play Orders API** → `mc.play_orders` | Setup |
| Gross revenue | Σ order totals including tax, all products, refunded orders included; never orders that weren't paid (pending or cancelled) | Orders API | Setup |
| Net revenue | What Lucento keeps: Google's reported developer revenue per order (after tax, its fee and partial refunds); without it an **estimate**, total − tax − Google's service fee (15 % assumed, configurable) for the share not refunded, labelled EST; an order refunded in full keeps nothing *(2026-11-15: refunds now count)*. Payouts are in Play's monthly earnings report | Orders API / Play Console (payouts) | Setup |
| MRR | Live paid subscriptions (ACTIVE, GRACE, CANCELLED_ACTIVE; not TRIAL, ON_HOLD, PAUSED), each at its latest order's price − tax, annual ÷ 12 | Entitlements + Orders API | Setup |
| Refunds | Purchases with status REVOKED (count); refunded amount from the order | Entitlements (count) / Orders API (amount) | Now / Setup |
| Complimentary users | Live GRANT rows | Entitlements + `mc.entitlement_grants` | Job |

Not a source for revenue: Supabase alone holds no amounts. Not in V1: Play's earnings and payout reports (Cloud
Storage bucket), reconciliation.

## Support

| Metric | Source | Availability |
|---|---|---|
| Tickets, messages, notes, statuses | `mc.tickets`, `mc.ticket_messages` | Job (new tables) |
| In-app support requests → tickets | `public.submit_support_request` from the app (FD-V4) | **App** (approved) |
| Email, Play review or message → ticket | Entered manually in Mission Control | Job |
| Replies | Sent by Mission Control through Resend; delivery from the webhook | Setup |
| Open tickets, awaiting customer, resolved this week, median first response | Ticket tables | Job |

## Infrastructure

| Item | Source | Availability |
|---|---|---|
| Supabase database availability, latency | Mission Control probe (`select 1` through an `mc_api` function) | Job |
| Supabase Auth availability | `GET /auth/v1/health` | Now |
| Database size | `pg_database_size` through an `mc_api` function | Job |
| Supabase project status | Supabase status page feed (platform) + own probes (project) | Now |
| Supabase recent errors | Management API logs endpoint (account-wide personal access token) | Setup (FD-V9) |
| Storage (file) usage | No Storage buckets are in use | — |
| Crash-free users / sessions, issues, affected users, version | Crashlytics → BigQuery export (+ sessions export for crash-free) | Setup |
| Latest production deployment, status, failures | Vercel REST API (`lucento-website`, and Mission Control itself) | Setup |
| Emails sent today / this month, deliveries, bounces, failures | Resend webhooks → `mc.email_events` | Setup |
| Quota consumption | Counted events (sent + received, **both products on the account**) vs plan limits entered in Settings. Resend documents no usage endpoint; the dashboard's Usage page is the reference | Setup + Settings |

## Investigation (record view)

| Data | Source | Availability |
|---|---|---|
| Wallets, transactions, budgets, goals, debts and loans, recurring | Synced tables, read through logged `mc_api` functions | Now (data) · **off until the policy update ships** (FD-V3, FD-V15) |
| Records that were never synced | On the phone only | No |
| Sync status per table | `server_updated_at`, `deleted_at` counts | Now |
