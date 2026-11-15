# Product Scope — Mission Control V1

**One job:** let one founder see how Lucento is doing, look after customers and their Pro access, answer support, and
notice when something is broken, safely and without SQL in the production editor.

**Size discipline:** every V1 feature either replaces a manual production-SQL task, answers a question you'd otherwise
answer in another console, or is required by security. Anything else is listed under "Not in V1".

## A. Overview

**In V1**
- Date filter: Today · 7 days · 30 days · 90 days · Custom, with the previous period for comparison.
- User numbers: total registered, new today, new this month, active today (DAU), active 30 days (MAU), Free, Pro (paid),
  Complimentary, Trial. DAU and MAU carry the label "signed-in accounts" and a definition on hover.
- Revenue numbers: Lifetime sales (count and amount), gross revenue, estimated net (labelled EST), MRR (subscriptions
  only; shows ₱0 when there are none), refunds.
- Charts (Chart.js): registrations over time; revenue per month as two series, **subscription billings** and **Lifetime
  sales**; Free / Pro / Complimentary distribution.
- Operational summary: open tickets, crash-free users (or "integration required"), email quota today, service status.

**Rules:** no number without a source; an unavailable metric shows what's needed; MRR never includes Lifetime.

## B. Users

**In V1**
- Directory: search by email or user id; filters for plan, status, activity and app version; columns Email ·
  Registered · Status · Plan · Last activity · App version (reported by the app from the release that adds the support
  form, FD-V7; "not reported" for older installs).
- Profile:
  - Account: id, email, provider, registered, confirmed, last sign-in, last activity, status.
  - Plan: live capabilities and every entitlement row with its source (Play or Complimentary).
  - Entitlement history: every complimentary grant with reason, admin and dates, plus Play status changes as recorded.
  - Usage counts: wallets, transactions, budgets, goals, debts and loans, recurring, categories.
  - Sync: last synchronization overall and per table.
  - Tickets for this account; audit entries for this account.
- Actions:
  1. **Grant complimentary Pro:** 30 days · 90 days · 1 year · Lifetime. Reason required; admin and time recorded.
     Lifetime = phone features with no end date and online services for 12 months, the same shape as paid Lifetime
     (FD-V5).
  2. **Revoke complimentary Pro:** ends complimentary rows only; paid Play entitlements are never touched.
  3. **Suspend** and **Reactivate**.
  4. **Edit profile information:** none in V1 (FD-V6). There's no profile table; email changes are made in the
     Supabase dashboard and noted in the ticket.
  5. **Initiate account deletion:** 24-hour cool-off, cancellable, then the same cascade as the in-app Delete account.
  6. **Resolve a purchase link** (added): move a Play purchase that's attached to the wrong account, with an Owner
     override of the 30-day rule, recorded with evidence. This is the support case most likely after launch.

**Not in V1:** bulk actions, custom-date grants, partial capability grants, password-reset emails, data export.

## C. Support

**Intake (FD-V4): an in-app support form writing to Supabase.**
- In the app: *Settings › Help › Contact support*. Fields: topic (Account · Billing and Pro · Sync · Data recovery ·
  Bug · Idea · Other), message, and (signed out only) an email address. Before sending, the person sees exactly what
  is attached: app version, Android version, phone model, sync on/off, last sync time, plan. They can untick it.
- Signed-in requests link to the account automatically. Signed-out requests are allowed with an email, rate-limited to
  3 per install per day (FD-V4b).
- The app calls `public.submit_support_request(...)`; the request lands as a ticket in Mission Control within seconds.
  Offline: the app keeps it and sends it when it's back online.
- After sending, the app says: "Thanks. We'll reply by email to <address>."
- Gmail (`lucentoapp@gmail.com`) stays available; you can log any email, Play review or message as a manual ticket.

**In Mission Control**
- Tickets: number (LC-1001…), channel (In-app / Manual), customer (linked account or email only), subject/topic,
  status (Open · In Progress · Awaiting Customer · Resolved), priority (Normal / Urgent), created, last activity.
- Inbox: search (message, email, ticket number), filter by status, sort by last activity.
- Ticket view: conversation, the attached device and sync details, internal notes (never sent), reply, change status,
  resolve, customer panel with a link to the profile.
- **Replies go by email** (FD-V4a) from `Lucento Support <support@send.lucentoapp.com>`, Reply-To
  `lucentoapp@gmail.com`, with `[LC-1042]` in the subject. Customer answers arrive in Gmail; you paste or forward the
  relevant part into the ticket (no inbound email automation in V1).
- New ticket manually; three saved replies; summary strip (open, in progress, awaiting customer, resolved in 7 days,
  median first response).

**Not in V1:** in-app reply thread, attachments or screenshots from the app, automatic email-to-ticket, SLAs,
assignment (one admin), AI suggestions.

## D. User investigation

**In V1**
- Inside the profile, a **Records** tab: wallets (name, type, currency, balance), transactions (date, category, wallet,
  amount; notes masked until revealed), budgets, goals, debts and loans, recurring, and sync status per table.
- Read-only. Opening it asks for a reason (usually the ticket number) and a fresh authenticator code; a banner shows
  whose records are open; every opened tab and every revealed note is logged. No customer consent step (FD-V3: staff
  access is disclosed in the privacy policy instead).
- Assisted actions: **none on ledger data in V1.** The framework (before/after, reason, audit, admin as actor) is used
  by the account and entitlement actions above. Candidates for your approval later: restore a deleted record; force one
  table to re-sync.

**Gate:** the Records view stays off in Settings until the app release carrying the updated privacy policy is live
(FD-V15).

## E. Infrastructure

**In V1**
- Supabase: database reachable (probe), latency, auth health, database size, platform status; recent errors only if
  FD-V9 allows a management token, otherwise a link to the Logs page.
- Crashlytics: crash-free users and sessions, top issues with affected users and versions, from the BigQuery export, or
  "integration required" with setup steps.
- Vercel: latest production deployment and its status for the website and Mission Control; recent failures.
- Resend: sent today and this month, delivered, bounced, failed; the daily quota meter, with warnings at 70 % and 90 %
  (the free tier stays for now, FD-V1).
- Backups: date, size and result of the last weekly encrypted dump (FD-V13); red if older than 8 days.
- Each service shows its last check time; a check older than 10 minutes shows "Unknown", never green.

**Not in V1:** incidents, alerts by email or push, uptime history beyond 7 days, logs search.

## F. Emails

**In V1**
- Quota meters: today and this month against the plan limits in Settings, counting every email on the Resend account
  (both products, sent and received).
- Recent events with filters (delivered, bounced, failed, complained, delayed) and search by recipient.
- Bounces list with the reason.
- Send a test email to your own admin address.
- Send an individual transactional email from fixed templates ("Your complimentary Pro is active", "About your
  purchase", "Your account deletion is scheduled") to an existing account's verified email, with preview and
  confirmation, logged.

**Not in V1:** campaigns, audiences, broadcasts, free-form emails to arbitrary addresses, editing the welcome and Pro
templates (they live in the website).

## G. Revenue

**In V1**
- Four clearly separated groups: **Paid one-time** (Lifetime), **Paid recurring** (subscriptions, Cloud add-on, MRR),
  **Complimentary** (count, reasons; never money), **Refunds**.
- Amounts from the Play Orders API: gross, tax, estimated Google fee, estimated net. All labelled EST, with "Reported
  figures are in Play Console › Download reports" and a link.
- Revenue per month (the two series), recent purchases, refunds, and complimentary grants ending in 30 days.
- A "Source of truth" note on every block.

**Not in V1:** reconciliation with earnings reports, payouts, cohorts, LTV, forecasts, currency conversion beyond
Play's own PHP amounts.

## H. Audit logs

**In V1**
- Every privileged action and every record view: administrator, action, affected user, timestamp, reason, result, and
  before/after for changes.
- Search (user, action, reason), filters (action type, result, date), entry detail. Read-only; no edit or delete
  anywhere.

## Settings

- Your admin profile, MFA status, active session and sign-out.
- Allowlist (read-only view; changes are made in SQL by you).
- Integration status: configured or not, last successful check (never values).
- Plan limits for the quota meters (Resend daily and monthly).
- Feature gate: Records view on or off (off until FD-V3).
- Reporting time zone (default Asia/Manila).

## Android app changes approved for V1 (FD-V4, FD-V7, FD-V15)

One app release, kept small:
1. *Settings › Help › Contact support*: the form above, with a preview of the attached details and an offline queue.
2. A once-a-day `report_client` call for signed-in accounts: app version, version code, Android version, phone model.
3. Privacy-policy text in `LegalDocuments.kt`, updated together with the website's /privacy:
   - staff can view an account's records when handling support, and every view is logged;
   - the app sends its version and phone model for signed-in accounts;
   - support requests store the message, the email (if signed out) and the attached details;
   - Mission Control keeps daily activity for 35 days and purchase amounts reported by Google Play.
4. JVM tests for the request builder and rate limiting; a screenshot-tour frame for the form.

Nothing else in the app changes.

## Backups (FD-V13)

A GitHub Actions workflow runs weekly: the Supabase CLI's `db dump` (roles, schema and data, including the accounts in
`auth`), encrypted with a public key whose private half only you hold, stored as a workflow artifact kept 90 days. It
reports its result to Mission Control. A restore is rehearsed once into lucento-test's staging copy before launch.

## Explicitly out of V1 (future opportunities)

Marketing automation, AI founder intelligence, feature flags, advanced analytics and cohorts, referrals, lifecycle
management, incidents, financial reconciliation, cost intelligence, multi-admin roles, in-app announcements, Play
reviews, alerts. The data model leaves room for each (DATABASE_PROPOSAL.md §6).
