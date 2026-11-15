# Database Proposal — V1

For approval. Nothing here has been applied anywhere. All of it goes into the existing production Supabase project (and
lucento-test first), as new migration files that you apply, following the existing rule "never edit applied DDL".

## 1. Where the files live (FD-V8)

**Recommended:** in `lucento-android/supabase/migrations/`, the database's only migration history. These files touch
no Android code; they just keep one ordered history for one database. The offline checks go in `tools/sql-verify`.
Alternative: a `supabase/` folder in the new `lucento-mission-control` repo, which splits one database's history across
two repos.

## 2. Schemas

| Schema | Holds | Exposed to the API | Grants |
|---|---|---|---|
| `mc` | Mission Control tables and private helper functions | No | None to `anon` / `authenticated` |
| `mc_api` | Only the functions Mission Control calls | Yes (added to exposed schemas) | `EXECUTE` to `authenticated`; each function calls `mc.require_admin()` first |

## 3. Tables (`mc`)

```text
admins              user_id uuid pk → auth.users · email · display_name · active bool · created_at

audit_log           id bigint identity pk · at timestamptz · admin_id uuid · action text · target_user_id uuid
                    · target_ref text · reason text · result text check (ok, refused, failed, pending)
                    · before jsonb · after jsonb · detail text
                    -- no UPDATE/DELETE grants; trigger raises on update or delete

entitlement_grants  id uuid pk · user_id → auth.users (cascade) · kind check (days_30, days_90, year_1, lifetime)
                    · starts_at · ends_at (null for lifetime's phone features) · services_end_at
                    · reason text not null · note text · ticket_id · granted_by → admins · granted_at
                    · revoked_at · revoked_by · revoke_reason

deletion_requests   id · user_id · requested_by · reason · requested_at · run_after (requested_at + 24 h)
                    · cancelled_at · executed_at

account_activity    user_id · day date · signals text[] (refresh, sync, ai) · pk (user_id, day)
                    -- kept 35 days; feeds DAU/MAU and "last activity"

daily_metrics       day · key · value numeric · pk (day, key)
                    -- nightly totals so charts keep history (usage_events purge at 90 days)

tickets             id bigint identity (shown as LC-1001…) · channel check (in_app, manual) · user_id (nullable)
                    · requester_email · topic · subject · status check (open, in_progress, awaiting_customer, resolved)
                    · priority · install_id (in-app only) · diagnostics jsonb (allow-listed keys, ≤ 2 KB)
                    · created_at · first_response_at · resolved_at · last_activity_at
ticket_messages     id · ticket_id · kind check (inbound, reply, note) · author_admin_id · body_text (≤ 5,000)
                    · resend_email_id · created_at
records_access      id · admin_id · user_id · reason (ticket number or text) · opened_at · closed_at
                    -- one row per Records session; each tab and note reveal is also in audit_log

account_clients     user_id pk → auth.users (cascade) · app_version · version_code · android_sdk · device_model
                    · first_seen_at · last_seen_at        -- written by public.report_client (FD-V7)

job_runs            id · job (backup, play_orders, crashlytics, …) · started_at · finished_at · result · detail
                    -- the weekly backup workflow reports here (FD-V13)

email_events        resend_event_id pk · email_id · type · at · recipient · from_domain · subject · reason
                    -- 90-day retention; recipient kept for bounces and search

play_orders         order_id pk · purchase_token_hash · product_id · base_plan_id · region_code · currency
                    · listing_price_micros · tax_micros · total_micros · state · created_at · refunded_at · fetched_at

settings            key pk · value jsonb · updated_by · updated_at
                    -- resend_daily_limit, resend_monthly_limit, google_fee_rate, timezone, records_view_enabled

rate_counters       admin_id · action · hour timestamptz · count · pk (admin_id, action, hour)
```

## 4. Change to an existing table (FD-V5)

`public.entitlements`: replace the constraint `entitlements_grant_expires` so a `GRANT` row may have no expiry. Nothing
else changes. Installed apps already read a missing expiry as "never".

How complimentary Lifetime is written, mirroring paid Lifetime without a new source name (a new name would break
installed apps):
- a `GRANT` row with `product_id = 'lucento_pro_lifetime'`, grants `LOCAL_PRO`, no expiry;
- a `GRANT` row with `product_id = null`, grants `CLOUD AI_PRO SHARED`, expiring after 12 months.

Shorter grants write the single `product_id = null` row with all four capabilities. Both rows are always *derived*
from `mc.entitlement_grants` by `mc.recompute_grants(user_id)`, so extending, overlapping or revoking grants can't drift.
To verify on lucento-test before shipping: the app shows Pro from a GRANT row carrying a `product_id`.

## 4b. Functions the app calls (`public`, for FD-V4 and FD-V7)

| Function | Caller | Does |
|---|---|---|
| `submit_support_request(topic, message, email, install_id, diagnostics)` | `anon` and `authenticated` | Creates an in-app ticket. Signed in: links `auth.uid()` and the account's email (the `email` argument is ignored). Signed out: `email` required and format-checked. Limits: 3 per install id per day, 10 per account per day, message ≤ 5,000 characters, diagnostics keys allow-listed. Returns the ticket number. Security definer; writes `mc.tickets` without exposing the schema |
| `report_client(app_version, version_code, android_sdk, device_model)` | `authenticated` | Upserts `mc.account_clients` for `auth.uid()`; ignored if called more than once an hour |

Neither function returns any other customer's data, and neither lets the caller read tickets back.

## 5. Functions (`mc_api`, each audited)

| Function | Does |
|---|---|
| `overview(from, to)` | All Overview numbers with their sources |
| `list_users(query, plan, status, active, limit, offset)` | Directory rows |
| `user_profile(user_id)` | Profile, entitlements, grant history, counts, sync, tickets (logs `access.profile_open`) |
| `grant_pro(user_id, kind, reason, note, ticket_id)` / `revoke_grant(grant_id, reason)` | Writes `entitlement_grants`, recomputes GRANT rows |
| `resolve_purchase_link(token_hash, to_user, reason, evidence)` | Moves a purchase link and its PLAY_* rows (Owner override of the 30-day rule) |
| `suspend(user_id, reason)` / `reactivate(user_id, reason)` | Sets or clears `auth.users.banned_until` (verify on test that Auth honours it) |
| `schedule_deletion(user_id, reason)` / `cancel_deletion(id, reason)` | Cool-off request; a pg_cron job runs due ones through the same cascade as `delete_my_account` (shared private function) |
| `open_records(user_id, reason)` / `user_records(access_id, kind, page)` / `close_records(access_id)` | Records tab: opening requires a reason, `aal2` within 10 minutes and the `records_view_enabled` setting; every call is logged |
| `tickets_*` | List, get, create manual ticket, add reply or note, set status (replies are sent by Mission Control through Resend and recorded with their email id) |
| `email_events(filters)`, `quota()` | Emails module |
| `revenue(from, to)` | Revenue module from entitlements + `play_orders` |
| `audit(filters)` | Audit Logs |
| `probe()` | Health probe: returns now() and database size |

Writes from webhooks and jobs (Resend events, Play orders, the backup workflow's result) come through route handlers that hold a
**separate, single-purpose secret** and call `mc_api.ingest_*` functions checking that secret, so no admin session or
service-role key is needed for them.

## 6. Jobs (pg_cron)

| Job | When | Does |
|---|---|---|
| `mc-activity` | Hourly | Accounts with `auth.sessions.refreshed_at`, synced writes or AI usage in the last hour → `account_activity` |
| `mc-daily` | 00:20 Manila | Totals into `daily_metrics`; prune `account_activity` > 35 days and `email_events` > 90 days |
| `mc-grants` | Hourly | Recompute GRANT rows whose grant has ended |
| `mc-deletions` | Every 15 min | Execute due, uncancelled deletion requests |

Play orders and Crashlytics are fetched by Mission Control (Vercel cron), since they need Google credentials.

## 7. Room for later (not built)

Roles (a `role` column on `admins` plus a permission check in `require_admin`), a hash-chained audit log with off-site
export, incidents, feature flags (`settings` already holds key/values), reconciliation (`play_earnings_lines`
alongside `play_orders`), campaigns and consent (`communication_prefs`), AI briefings (reading `daily_metrics`).

## 8. Privacy effects to note in the policy

New per-account data: `account_activity` (days active, 35 days) and Play order amounts. Recommended one-line policy
additions with FD-V3: staff can see account and plan details to provide support; daily activity is kept for 35 days;
Lucento records purchase amounts reported by Google Play.
