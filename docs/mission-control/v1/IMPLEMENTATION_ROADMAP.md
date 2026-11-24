# Implementation Roadmap — V1

Phases 1–3 (discovery, design, review) are this document set. Phase 4 starts only on your explicit approval; Phase 5
(deployment) needs a second explicit approval.

## Phase 4 — Implementation, in build order

Each step ends with a demonstrable result on lucento-test and a preview deployment. Estimates assume one engineer
working with Claude.

| Step | Build | Depends on | Size | Done when |
|---|---|---|---|---|
| 4.1 Foundation | Repo `lucento-mission-control` (Next.js, TypeScript, Tailwind, Chart.js); Supabase SSR auth; sign-in + TOTP; allowlist; idle/absolute session limits; the `mc` + `mc_api` migration with `admins`, `audit_log`, `settings`, `rate_counters`; Audit Logs and Settings pages | FD-V2, FD-V8; owner: admin login, TOTP, exposed schema | M | A non-admin and an `aal1` session are refused by both the app and the database; audit rows can't be updated or deleted; no secret in the client bundle (CI check) |
| 4.2 Users + entitlements | Directory, profile (no Records), grants (4 durations), revoke, resolve purchase link, suspend/reactivate, scheduled deletion; `entitlement_grants`, `deletion_requests`, the GRANT constraint change | 4.1, FD-V5, FD-V6 | M | On a debug phone against lucento-test: a grant shows Pro, revoke returns Free; a paid row survives a revoke; a suspended account can't sign in or refresh; deletion runs after the cool-off and matches `delete_my_account` |
| 4.3 Overview + activity | `account_activity` and `daily_metrics` jobs; Overview numbers and charts with sources and date filters | 4.2 | S–M | Numbers match hand-run SQL on test fixtures; unavailable metrics show their requirement |
| 4.4 Emails + Infrastructure + backups | Resend webhook ingest, quota meters (70 %/90 % warnings), events, bounces, test email, templated transactional sends; Supabase probe, Vercel deployments, Crashlytics BigQuery (or "integration required"); weekly encrypted backup workflow + `job_runs` | 4.1; owner: Resend keys, webhook, Vercel token, BigQuery link, backup secrets | M | A test email's events arrive within a minute; quota counts include every email on the account; a stale check shows Unknown; one backup restored into a scratch database |
| 4.5 Support (Mission Control side) | Ticket tables; `submit_support_request` with rate limits; manual tickets; replies by email with `[LC-…]`; notes; statuses; summary strip | 4.4, FD-V4 | M | A request submitted with curl as anon and as a signed-in test account becomes a ticket; limits refuse the 4th anon request; a reply reaches a test inbox |
| 4.6 Android release (approved) | In `lucento-android`, on a feature branch: Help › Contact support form with details preview and offline queue; daily `report_client`; privacy-policy text in `LegalDocuments.kt` (website /privacy updated in the same change); JVM tests; tour frame | 4.5, FD-V7, FD-V15 | M | Debug build against lucento-test: a request from the phone appears in Mission Control with its details; app version shows in Users; CI green; the owner tests on a phone before merge |
| 4.7 Revenue | Play Orders sweep into `play_orders`; Revenue module | 4.2; owner: Play service account | S–M | With licence-test purchases on lucento-test: one-time and recurring are separated, MRR excludes Lifetime and trials, refunds show |
| 4.8 Records (off until 4.6 ships) | `records_access`, Records tab with reason + step-up, masked notes, banner, logging | 4.2, FD-V3 | S | Reads fail without a reason, without fresh MFA or with the setting off; every tab and reveal is in the audit log |

**Total V1:** roughly 5–7 weeks of focused work, including the app release. Steps 4.1–4.3 alone (about 2 weeks)
already replace production SQL for entitlements and give a usable Overview.

**Testing rules:** everything on lucento-test first; production receives only migrations you apply and, after
deployment approval, read traffic plus actions you take yourself. No destructive operation is run against production
to test it.

## Phase 5 — Deployment (after a separate approval)

Prepared as `DEPLOYMENT.md` in the new repo: Vercel project settings, every environment variable with where to get it,
domain and DNS steps (GoDaddy CNAME), the production migration order, a smoke-test checklist, and a rollback (remove
the domain; the `mc` schema is additive and can stay dormant).

## After V1

Revisit in about 3 months of real data: alerts by email, a hash-chained audit export, Play earnings reconciliation,
in-app contact (app release), and the items in WHAT_WE_MISSED.md.
