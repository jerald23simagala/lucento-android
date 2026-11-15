# Lucento Mission Control — Product Vision

## The one-sentence version

Mission Control is the place where Lucento's founder answers three questions every morning: **is anything broken, does
anyone need me, and is the business moving** — and then acts on the answer with every action checked, attributable and
reversible where possible.

## Why it exists

Today Lucento is operated through seven consoles (Supabase, Google Play, Firebase, Vercel, Resend, GoDaddy, Gmail) and
the Supabase SQL editor. The SQL editor is the riskiest tool a founder can operate a financial product with: it has no
confirmation step, no audit trail and no notion of "only this customer". Comping a tester's Pro access today means
hand-writing an `insert` into production.

Mission Control replaces that with a purpose-built operating surface. Its first job is not to look impressive. It is to
make the common operational tasks safe, and to make the business legible as it grows from 5 accounts to thousands.

## Who uses it

| User | Now | Later | What they need |
|---|---|---|---|
| **Founder / Owner** | Yes (sole operator) | Yes | Everything; approves sensitive actions; owns the audit trail |
| **Support agent** | — | First hire | Tickets, customer metadata, entitlement fixes within limits, consented investigation |
| **Engineer (contract)** | Occasionally | Yes | Infrastructure, crash and sync diagnostics; no customer records by default |
| **Analyst / advisor** | — | Possibly | Aggregates only, never individual customers |
| **Marketing help** | — | Possibly | Campaign drafts and analytics; sending needs Owner approval |

Design for one operator now, with roles already in the data model so a second person never forces a rewrite.

## Product principles

1. **Observe everything, reveal only what's needed.** The default view of a customer is operational metadata (plan,
   sync health, versions, ticket history), never their financial records. Records open only through a consented,
   time-boxed, logged investigation.
2. **Every number has a source and a freshness.** Each metric shows where it came from (Supabase, Play Orders API, Play
   earnings report, Crashlytics export…), when it was computed, and whether it's exact, estimated or sampled. A
   metric that can't be computed says so; it is never zero-filled or modelled into existence.
3. **Money is reported the way an accountant would.** Gross customer payments, tax, Google's fee, estimated net,
   reported earnings and payouts are different numbers with different sources and dates. MRR counts subscriptions
   only. Lifetime sales are one-time revenue, always reported separately.
4. **Actions are proposals until confirmed.** Every write shows what changes (before and after), asks for a reason,
   records who did it, and returns a receipt in the audit log. Destructive actions add re-authentication and, where it
   protects the customer, a cool-off period.
5. **The customer can always tell who did what.** Administrator activity is labelled as such everywhere: in Mission
   Control's own timelines, in the audit log, and (where it changes their data) in what the customer sees.
6. **Consent is enforced in the database, not in the UI.** Marketing eligibility, support access to records, and
   analytics linkage are columns and checks the server enforces. A UI bug can't send a campaign to people who never
   opted in.
7. **Link out before rebuilding.** Where Google Play Console, Firebase or Vercel already do something well (pricing
   experiments, release tracks, crash stack exploration), Mission Control summarizes and deep-links instead of
   cloning.
8. **AI explains; it doesn't invent.** Founder Intelligence works over a catalogue of defined metrics and cites each
   one it uses. It never sees customer ledgers.

## What it is responsible for

**A. Business intelligence.** Accounts, active synced accounts, paying customers by product, MRR (subscriptions),
lifetime sales, refunds, conversion, retention cohorts, country mix (from Play), all with definitions in one
place (DATA_ARCHITECTURE.md §5).

**B. Operational management.** The customer directory and profile; entitlements, including complimentary grants with
history; support tickets; consented investigations; purchase-ownership disputes; data-subject requests (export,
deletion); in-app announcements; later, consented marketing.

**C. Technical operations.** Real health checks for Supabase, the website API, Resend and Google Play notifications;
crash and ANR trends from Play vitals and Crashlytics; deployments; scheduled-job monitoring; incidents with a public
status message; cost and quota watch.

## What it is not

- Not a second app backend. The Android app never calls Mission Control.
- Not a way to sign in as a customer. There is no impersonation, ever.
- Not a CRM, data warehouse, or general BI tool. When Lucento outgrows Postgres aggregates, export to a warehouse.
- Not a place where customer financial data is copied. It reads in place, through functions that log the read.

## Shape of the product

```
Mission Control (mission.lucentoapp.com, its own Vercel project and repo)
├── Today            founder briefing · attention queue · health strip · key numbers
├── Customers        directory · profile · entitlements · investigation (consented)
├── Support          inbox · tickets · templates · data requests
├── Revenue          MRR vs lifetime · Play reconciliation · refunds · complimentary tracker
├── Product          activation funnel · feature adoption (synced accounts) · versions · reviews
├── Operations       health · incidents · crashes · deployments · jobs · costs & quotas
├── Messages         in-app announcements · campaigns (consented, later) · email deliverability
├── Intelligence     daily briefing archive · anomalies · ask (cited)
└── Governance       audit log · admins & roles · approvals · settings
```

## How success is measured

| Outcome | Measure | Target after Phase 2 |
|---|---|---|
| No raw SQL in production for routine work | Owner SQL-editor writes per month | 0 for comps, revocations, deletions, purchase disputes |
| Customers get answers fast | Median first response | < 24 h |
| Problems are seen before customers report them | Incidents first detected by Mission Control vs. by a ticket | > 80 % |
| Money is trusted | Monthly Mission Control revenue vs. the Play earnings report | Variance explained to the cent |
| Accountability | Privileged actions with actor + reason + audit entry | 100 % |
| Founder time | Daily check-in time | ≤ 10 minutes |

## The bet

A founder alone can run a global finance app if the operating system around it is honest, quiet when things are fine,
and precise when they're not. Mission Control should feel like a calm instrument panel, not a casino of charts.
