# Feature Catalog

Every requested feature and every proposed addition, by module.

**Priority:** **P0** first release (Phase 1–2) · **P1** soon after launch · **P2** when data or headcount justifies it
· **P3** later · **✕** not recommended.

**Readiness:**
- **R** ready: the data exists today.
- **I** needs new server instrumentation (an `mc` migration or a website change, no app release).
- **A** needs an Android app release.
- **C** needs a consent mechanism and a Privacy Policy change (FD-2).
- **X** needs an external permission or a plan upgrade.

Features marked ★ aren't in the brief.

## 1. Today (Founder Overview)

| Feature | Pri | Ready | Notes |
|---|---|---|---|
| Health strip (every service, real checks) | P0 | I | INTEGRATION_BLUEPRINT.md §7 |
| Attention queue: alerts, unassigned tickets, pending approvals, expiring comps, failed jobs | P0 | I | Replaces "recent operational alerts" + "recent tickets" with one ranked list |
| Accounts: total, new, confirmed, active synced (1/7/30 d) | P0 | R | Never labelled DAU/MAU |
| Plan mix: free, trial, paying (by product), complimentary | P0 | R | |
| MRR (subscriptions only) beside Lifetime sales (one-time) | P1 | I X | Zero is shown as ₱0 MRR, not hidden |
| Installs and active devices (Play) | P1 | X | Play stats reports |
| Crash-free users (Crashlytics, opted-in) + crash rate (Play vitals, all) | P1 | X | Both, labelled by population |
| Growth chart (weekly accounts), revenue chart (MRR vs one-time) | P1 | I | Needs `daily_metrics` history |
| Retention cohorts (synced accounts) | P2 | I | Needs months of data |
| Geographic distribution | P1 | X | Play stats (installs) + order region (customers) |
| Date range + comparison period on every chart | P0 | — | |
| ★ Daily briefing card (from Intelligence) | P2 | I | |
| ★ "What changed since you last looked" | P1 | I | Diffs since the admin's last session |

## 2. Customers (User Management)

| Feature | Pri | Ready | Notes |
|---|---|---|---|
| Directory: search by email, user id, order id, token hash prefix | P0 | R | Exact email match by default; partial match needs `customers.view_email` |
| Filters: plan, entitlement source, joined, last synced, provider, suspended | P0 | R | |
| Filters: country | P2 | X | Paying customers only (order region) |
| Filters: app version, device class, engagement | P1 | A I C | Needs `report_client` (DATA_ARCHITECTURE.md §4) |
| Profile: account facts (id, email, joined, confirmed, last sign-in, provider, status) | P0 | R | |
| Profile: product usage counts (wallets, transactions, budgets, goals, debts, recurring, categories) | P0 | R | Counts only, T1 metadata; synced accounts only |
| Profile: sync health per table (last server write, rows, tombstones) | P0 | R | |
| Profile: entitlements (all rows, live capability set, source labels) | P0 | R | |
| Profile: Google Play purchases (product, state, order id, token hash, link history) | P0 | R | |
| Profile: purchase amounts and refunds | P1 | I X | From `play_orders` |
| Profile: AI usage (requests per day, plan allowance) | P0 | R | |
| Profile: Shared Space (role, partner labels, invites) | P0 | R | |
| Profile: tickets, emails sent, admin actions on this account | P0 | I | |
| **Grant complimentary Pro**: 30 d, 90 d, 1 yr, Lifetime, custom; reason code, note, admin, timestamp, expiry preview | P0 | I | Lifetime needs FD-9 |
| Extend / shorten / revoke a grant | P0 | I | Never touches PLAY_* rows |
| Partial grants (e.g. AI only, Shared only) | P1 | I | The schema already supports grant subsets |
| ★ Resolve purchase link (`linked_elsewhere`, `review_required`) | P0 | I | A real gap: the DB says "support path" but none exists |
| ★ Re-verify a purchase with Google now | P0 | I | Calls the website's verify logic |
| Edit permitted account info (email change with confirmation to both addresses) | P2 | I | Through Auth admin; rare |
| Suspend / reactivate | P1 | I | Supabase ban (`banned_until`); offline app keeps working (FD-13) |
| Initiate account deletion (with cool-off) | P0 | I | Replaces the manual dashboard procedure in ACCOUNT_DELETION.md |
| Trigger password recovery email | P0 | I | Counts toward the email limits |
| Export customer data (JSON + CSV archive) | P1 | I | Data-subject access requests |
| ★ Internal notes and VIP flag on an account | P1 | I | |
| ★ Data-subject request tracker (access, deletion, correction; 30-day clock) | P1 | I | Philippine DPA / GDPR obligations |

## 3. Customer Investigation

| Feature | Pri | Ready | Notes |
|---|---|---|---|
| Metadata investigation (T1): everything in the profile, inside a ticket | P0 | R | No consent needed beyond today's policy, but FD-2 should still mention operational metadata |
| Records investigation (T2): wallets, transactions, budgets, goals, debts, recurring, settings | P1 | A C | Needs the support-access consent in the app |
| Masked notes with per-field reveal | P1 | A C | |
| Session banner, timer, auto-expiry, explicit end | P1 | I | |
| Assisted actions (T3), enumerated: restore tombstone, force re-sync of a table, repair transfer pair, clear stuck invite | P2 | A C I | Each a typed function with before/after |
| Admin-attributed changes visible to the customer ("Changed by Lucento support") | P2 | A | An app change to label actor = support |
| Break-glass | P1 | I | Owner only |
| Impersonation / sign in as the customer | ✕ | — | Never |

## 4. Support Center

| Feature | Pri | Ready | Notes |
|---|---|---|---|
| Ticket inbox fed by email (support@ via Resend inbound) | P0 | X I | Or Gmail forwarding as a stopgap (FD-11) |
| ★ In-app "Contact support" with an allow-listed diagnostics preview | P1 | A C | The biggest support quality lever |
| Categories (the 11 requested), priority, assignment, statuses | P0 | I | |
| Conversation history, internal notes, attachments (private bucket, scanned) | P0 | I | |
| Response templates with variables | P0 | I | |
| "Investigate user" inside the ticket (side panel, keeps context) | P0 | I | T1 now, T2 after consent ships |
| Ticket analytics: first response, resolution, open, unassigned, by category, recurring issues | P1 | I | |
| ★ SLA timers and "waiting on customer" auto-close after 7 days with a reminder | P1 | I | |
| ★ Link tickets to incidents (bulk reply when resolved) | P1 | I | |
| ★ Play Store reviews as a channel (reply from Mission Control) | P1 | X | Reviews API |
| AI classification and suggested replies (labelled, editable, never auto-sent) | P2 | I | |
| AI root-cause clustering across tickets | P3 | I | Needs volume |
| ★ Help center articles (public, on the website) linked from replies | P2 | — | Website change |

## 5. Operations (Infrastructure Monitoring)

| Feature | Pri | Ready | Notes |
|---|---|---|---|
| Unified health with real probes, history, uptime % | P0 | I | |
| Supabase: availability, DB size, connections, slow queries, advisors | P0 | I | |
| Supabase: API/auth request volume and errors | P1 | X | Management API PAT |
| Supabase: Edge Function failures | ✕ | — | None exist; Vercel functions instead |
| Sync failures | P1 | A I | Client-reported counters + API logs |
| Crashlytics: crash-free users and sessions, issues, versions, devices | P1 | X | BigQuery export |
| Play vitals: crash and ANR rate | P1 | X | Reporting API |
| Vercel: deployments, failures, production deployment, function errors | P0 | X | Token |
| Vercel: traffic, bandwidth | P2 | X | Plan-dependent |
| Resend: sent, delivered, bounced, complained, failed; searchable log | P0 | I | Webhooks |
| Resend: opens, clicks | ✕ (transactional) / P3 (marketing) | C | Off by design |
| ★ Scheduled job monitor (pg_cron, Vercel cron, snapshot, imports) | P0 | I | |
| ★ Incident management + in-app service notice | P1 | A I | |
| ★ Cost and quota watch (Resend caps, Gemini spend, Supabase size, Vercel usage) | P0 | I | Resend's 100/day cap is a launch risk |
| ★ Kill switches (AI features, invites, purchases verification fallback) | P1 | A I | `remote_config` read by the proxy and the app |
| ★ Backup verification (last backup, last restore test) | P0 | X | Needs the Pro plan |
| ★ Data integrity monitors (transfer legs, orphaned rows, entitlement anomalies) | P1 | I | |
| ★ Security events (failed admin logins, new admin, break-glass, advisor findings) | P0 | I | |

## 6. Messages (Marketing Command Center)

| Feature | Pri | Ready | Notes |
|---|---|---|---|
| ★ In-app announcements (version-targeted, dismissible) | P1 | A I | No email consent needed; a policy mention |
| Consent capture (product news, off by default) | P2 | A C | The prerequisite for everything below |
| Segments: all consented, free, Pro, complimentary, new, inactive, returning, not synced recently, app version, country | P2 | C I | Every segment is ∩ consented |
| Campaign builder: templates, desktop and mobile preview, test send, schedule, send now | P2 | C X | Resend Broadcasts on a marketing subdomain |
| Guardrails: preview, test send, typed count, cancellable queue, caps | P2 | I | Built with the builder, not later |
| Campaign history and performance (delivery, bounces, unsubscribes) | P2 | I | |
| Opens and clicks | P3 | C | Only if FD-4 accepts tracking and the policy says so |
| Campaign-attributed purchases | P3 | I | Last-touch within 7 days, labelled as attribution, not causation |
| Lifecycle automation (welcome series, win-back) | P3 | C | After a year of manual campaigns |

## 7. Revenue & Monetization

| Feature | Pri | Ready | Notes |
|---|---|---|---|
| MRR (subscriptions + Cloud add-on) with plan and interval breakdown | P1 | I X | |
| ARR (run-rate, labelled) | P1 | I | |
| Lifetime sales (one-time) | P1 | I X | |
| Gross customer payments, tax, estimated Google fee, estimated net | P1 | I X | Orders API |
| Reported earnings (monthly) and payouts (manual) | P1 | X | Earnings reports |
| ★ Reconciliation: entitlements ↔ orders ↔ earnings, with discrepancies listed | P1 | I X | |
| Daily and monthly trends, geographic revenue | P1 | I X | |
| Purchase and refund history | P1 | I | |
| Revenue cohorts, free-to-Pro funnel | P2 | I | |
| ARPU (monthly), LTV (modelled, after 6 months) | P2 | I | |
| Complimentary tracker: total, granted, active, expired, reasons, estimated list value | P0 | I | Value is "foregone list value", never revenue |
| ★ Trial conversion (annual trial → paid) | P1 | I | |
| ★ Lifetime services renewal (Lifetime owners whose 12 months end; Cloud add-on uptake) | P1 | R | A Lucento-specific revenue moment |
| ★ Price change impact | P3 | X | Play price experiments are run in Play Console |
| ★ Forecast | P3 | I | Only with ≥ 6 months of history |

## 8. Product Intelligence

| Feature | Pri | Ready | Notes |
|---|---|---|---|
| Activation funnel (onboarding → first entry → D1 → D7) | P1 | R | Opted-in installs; sample-labelled |
| Feature adoption (synced accounts): wallets, budgets, goals, debts, recurring, Shared, AI | P1 | R | Aggregates |
| Feature adoption (all installs) | P2 | A | Extended anonymous allowlist |
| Offline usage share | P2 | A | "Never synced" installs vs synced; anonymous |
| AI entry usage | P1 | R | `ai_usage` |
| Retention cohorts, engagement trends | P2 | I | |
| User journeys | ✕ | — | Needs per-user event streams the policy rules out; funnels answer the real questions |
| Onboarding abandonment by step | P1 | R | `onboarding_step_viewed` |
| ★ Version adoption (Play stats + `report_client`) | P1 | X A | |
| ★ Store rating and review themes | P1 | X | |
| Recommendations from behaviour | P2 | I | Through Intelligence, cited |

## 9. Intelligence (AI Founder Intelligence)

| Feature | Pri | Ready | Notes |
|---|---|---|---|
| Daily founder briefing, every number cited | P2 | I | After `daily_metrics` has ≥ 30 days |
| Anomaly detection (rule-based first: thresholds, week-over-week, seasonality later) | P1 | I | Rules before ML; explainable |
| Actionable recommendations with "Open" links to the right screen | P2 | I | |
| Ask Lucento Intelligence (natural language over the metric catalogue) | P2 | I | |
| "At-risk users" list | P3 | I C | Account-level prediction touches the analytics consent question (FD-3) |
| Model never sees customer ledgers | P0 | — | Design rule |

## 10. Governance

| Feature | Pri | Ready | Notes |
|---|---|---|---|
| Admin accounts, roles, permissions | P0 | I | |
| MFA, step-up, session controls | P0 | I | |
| Immutable audit log with chain verification and export | P0 | I | |
| Data access log (reads) | P0 | I | Same log, `access.*` actions |
| Approvals and cool-off | P0 | I | |
| Scheduled operations (grant expiry, deletion after cool-off, scheduled campaigns) | P0 | I | |
| Bulk operations with dry run and caps | P1 | I | |
| Data exports (aggregates, CSV) | P1 | I | Logged |
| ★ Secrets inventory (names, rotation dates) | P1 | I | |
| ★ Monthly governance checklist (break-glass review, admin list, rotations, restore test) | P1 | I | |
| Compliance monitoring dashboards | P3 | — | A checklist does the job at this size |

## 11. Explicitly not recommended

| Idea | Why not |
|---|---|
| Impersonation / "log in as user" | Breaks attribution and the privacy promise; consented investigation covers the need |
| Affiliate management | No affiliate programme; Play's policies and the volume don't justify it |
| Referral management (now) | Needs in-app mechanics and fraud controls; revisit after 1,000 paying customers |
| Building pricing experiments | Play Console runs price experiments natively |
| Building release management | Play Console and CI already do it; Mission Control shows the tracks read-only |
| A data warehouse now | Postgres aggregates suffice for years at this scale |
| Real-time dashboards everywhere | Most numbers are daily by nature; real-time only for health |
| Feature request voting (now) | Low volume; capture requests as a ticket category and tag them |
| NPS surveys (now) | Needs in-app prompts and consent; Play ratings are the early signal |
