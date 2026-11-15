# Existing Architecture Audit — Mission Control V1

Verified 2026-11-09, read-only: repository files, Supabase catalog queries and aggregate counts on production
(`hmtcuttadbgbldvzsfhz`), aggregate log queries, and Vercel and Resend account listings. No customer row was read and
nothing was written. The longer audit from the parked full-vision discovery is in
[../discovery/EXISTING_ARCHITECTURE_AUDIT.md](../discovery/EXISTING_ARCHITECTURE_AUDIT.md); this document covers what V1
needs.

## Summary

| # | Area the brief asked about | What exists (verified) | V1 consequence |
|---|---|---|---|
| 1 | Supabase schema | Production project `lucento` (Postgres 17, ap-northeast-2, **Free plan**). `public`: wallets, categories, transactions, budgets, goals, goal_contributions, recurring, ledger_settings, shared_spaces, space_members, space_invites, entitlements, purchase_links, ai_usage, usage_events, account_emails. `lucento_private.plan_limits`. RLS on every table. No Edge Functions. One pg_cron job | Admin data fits in the same project, in a private `mc` schema |
| 2 | Authentication | Supabase Auth, email/password + Google. 5 accounts, all confirmed. 0 MFA factors. `auth.users` has `banned_until`, `last_sign_in_at`; `auth.sessions` has `refreshed_at`, `user_agent` | Suspension and an activity signal are possible without an app change |
| 3 | User profiles | **No profile table.** Identity is `auth.users` (email, Google name and picture in metadata). The only user-chosen labels are Shared Space names | "Edit profile" has almost nothing to edit (FD-V6) |
| 4 | Subscriptions and entitlements | `public.entitlements`: sources `PLAY_SUBSCRIPTION`, `PLAY_LIFETIME`, `PLAY_LIFETIME_SERVICES`, `PLAY_CLOUD_ADDON`, `GRANT`; capabilities `LOCAL_PRO`, `CLOUD`, `AI_PRO`, `SHARED`. Server-written only. **0 rows in production.** A `GRANT` must have an expiry (constraint). One GRANT row per account per product | Complimentary Pro already has its own source. Lifetime comps need the constraint relaxed. A history table is needed |
| 5 | Google Play billing | Live on the website (`api/billing/verify`, `rtdn`, `voided`). Stores product, status, dates, order id, token hash. **Stores no price, currency or tax.** No real purchases yet | Counts come from Lucento's DB; amounts must come from Google Play |
| 6 | Analytics | `usage_events`: opt-in, anonymous install id, 8 onboarding events, 90-day purge. **0 rows in production.** No per-account activity, no app version | DAU/MAU must be defined as signed-in activity (see DATA_SOURCE_INVENTORY.md) |
| 7 | Transaction and wallet structures | Synced tables mirror Room; amounts are `bigint` minor units with a currency code; soft deletes (`deleted_at`); `server_updated_at`, `revision` on every row; transfers are two legs sharing `transfer_group` | Counts and "last synced" per table are exact for people who sync |
| 8 | Synchronization | Opt-in. Last-write-wins enforced by triggers; a rejected stale write leaves no server trace | "Last sync" is visible; sync *failures* aren't |
| 9 | Firebase | Project `lucento` (`app/google-services.json` locally). Crashlytics is opt-in, sanitized, no user ids. Release builds include it only once CI has the `GOOGLE_SERVICES_JSON` secret (owner step OT-18, unconfirmed). **No REST API returns Crashlytics aggregates** | Crash data needs the BigQuery export (owner setup) |
| 10 | Resend | Domain `send.lucentoapp.com` verified, open/click tracking off. **Free tier: 100/day, 3,000/month, shared with KlaroPH** (`klaroph.com` on the same account). 0 webhooks, receiving off. Senders: Supabase Auth over SMTP (verify, reset) and the website API (welcome, Pro confirmation) | Delivery data needs webhooks. The quota meter must count KlaroPH's mail too |
| 11 | Support and feedback | **None.** Support is the Gmail inbox `lucentoapp@gmail.com`. No in-app contact, no ticket store | A minimal ticket store is proposed (DATABASE_PROPOSAL.md) |

## Corrections to the brief

- **The website is Astro 5, not Next.js** (`lucento-website/package.json`). Mission Control can still be Next.js; it just
  shares no framework code with the site.
- **The AI features use Google Gemini**, not OpenAI. Irrelevant to V1, which has no AI.
- **Subscriptions exist** (`lucento_pro` monthly and annual; `lucento_cloud` add-on), so MRR will be a real number once
  Pro is on sale. Lifetime (`lucento_pro_lifetime`) is one-time and never part of MRR.

## Verified details that shape V1

**Activity without an app change.** Two server-side signals exist:
- `auth.sessions.refreshed_at`: the app's Supabase client refreshes its token while in use (tokens last an hour), so a
  recent refresh means "the app was open and signed in". The column is overwritten, so an hourly job must record it.
- API edge logs carry the signed-in account id (`request.sb.jwt.authorization.payload.subject`) and a country, but logs
  are reachable only through the Management API with an account-wide token, and Free-plan logs are kept for a day.

V1 uses the first (in Postgres, no new credential).

**App version is not on the server.** The app's API calls identify as `ktor-client`; the Supabase client sends only its
own library version (`supabase-kt/3.1.4`). Showing "App version" per user needs the app to report it: an Android
change, which this brief excludes without explicit approval (FD-V7).

**Suspension and deletion work in SQL.** `delete_my_account()` already deletes from `auth.users` inside a security-definer
function, and every user-owned table cascades. An admin deletion can reuse the same path. Suspension can set
`auth.users.banned_until` the same way; whether Supabase Auth honours a SQL-set ban on token refresh must be confirmed
on lucento-test before V1 relies on it (otherwise the Auth Admin API with a secret key is the fallback).

**Lifetime complimentary Pro needs only a database change.** The app reads `expires_at = null` as "never expires"
(`SupabaseEntitlementRemote.kt`), so relaxing the `entitlements_grant_expires` constraint is enough. A *new* source name
would break installed apps (`EntitlementSource.valueOf` would fail on it), so V1 keeps the existing `GRANT` source.

**Purchase disputes have no tool.** `apply_play_purchase` returns `linked_elsewhere` or `review_required` when a purchase
is attached to another account. The design says "support path", but the only tool today is SQL in the production editor.

**The privacy policy limits record access.** The in-app policy (`feature/legal/LegalDocuments.kt`, mirrored at
lucentoapp.com/privacy) says: "Database rules let only your signed-in account — and people you share with through
Lucento Shared — read or change your records." Module D (read-only investigation of wallets and transactions)
contradicts this unless the policy changes and the customer agrees (FD-V3).

**Infrastructure accounts.**
- Vercel team `jerald23simagala's projects`: `lucento-website` and `klaroph-app`. The plan wasn't returned by the API.
  The team name is the Hobby default, and Hobby is for non-commercial use.
- Resend: one account for both products. Received emails also count toward the quota.
- Supabase production on the Free plan: no usable backups, 1-day log retention, no session time-box settings.

## Risks found that matter before launch (whether or not V1 is built)

1. **No backups** of production (Supabase Free).
2. **Email cap**: 100/day across Lucento *and* KlaroPH. Each email sign-up uses two (verify + welcome); support replies
   and inbound support mail would add more.
3. **Vercel plan** may not allow commercial use.
