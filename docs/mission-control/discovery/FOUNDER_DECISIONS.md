# Founder Decisions

Decisions that change what gets built, what customers are promised, or what Lucento pays for. Each has options, a
recommendation and what it blocks. Nothing here has been decided. Reply with the id and your choice (for example
"FD-1: A") and Claude records it here before any work starts.

**Urgency:**
- **Before launch:** matters even if Mission Control is never built.
- **Before Phase 1.**
- **Later:** can wait for its phase.

---

## Before launch (independent of Mission Control)

### FD-8 · Supabase plan for production
- **A (recommended):** Supabase Pro now (daily backups, no pausing, higher limits, the metrics endpoint), then the
  PITR add-on once revenue covers it.
- **B:** stay on Free and run your own weekly `pg_dump` only.
- **Why it matters:** production holds ledgers and purchase entitlements with no backups on Free. The deletion page
  states a backup retention that should match reality.
- **Blocks:** WHAT_WE_MISSED #1, the restore test, the Operations backup tile.

### FD-7 · Vercel plan and the Mission Control domain
- Confirm the team's plan. Hobby is for non-commercial use, and the website's billing endpoints sell Pro.
- **A (recommended):** Pro for the team; Mission Control as a second project at `mission.lucentoapp.com` (a CNAME at
  GoDaddy).
- **B:** a separate Vercel account for Mission Control.
- **Blocks:** Phase 1 deployment, deployment protection on previews.

### FD-14 · Separate Lucento from KlaroPH in shared accounts
- Resend and Vercel are shared with KlaroPH.
- **A (recommended):** a separate Resend account (or team) for Lucento; keep Vercel shared but give Mission Control a
  token whose code only queries Lucento project ids.
- **B:** keep everything shared and scope keys only.
- **Why:** a KlaroPH complaint spike or key leak shouldn't touch Lucento's sign-up emails, and Lucento's quota
  shouldn't be spent by another product.

### Resend plan (part of FD-4's neighbourhood but needed regardless)
- Upgrade from the free tier before launch: 100/day is about 50 email sign-ups a day. Also raise Supabase Auth's email
  rate limit from 30/hour. **Recommended: yes, now.**

## Before Phase 1

### FD-17 · Scope and sequence
- **A (recommended):** the phased plan in IMPLEMENTATION_ROADMAP.md. The operational core first (entitlements, audit,
  health, snapshots), then support, revenue, consented investigation, intelligence, and marketing last.
- **B:** build every module at once (as briefed).
- **C:** core only, revisit the rest after 6 months of data.

### FD-5 · Admin identity
- **A (recommended):** a separate Supabase project (`lucento-ops`) used only for admin sign-in, with mandatory MFA
  (passkey or TOTP).
- **B:** admins in production `auth.users`, flagged in `mc.admins`.
- **Trade-off:** A costs a second (free-tier) project and a bit of setup; B puts admin identity one RLS mistake away from
  customer space.

### FD-6 · Mission Control framework
- **A (recommended):** Next.js (App Router, TypeScript) on Vercel. App-like screens (dense tables, side panels,
  optimistic updates, server actions) suit it, and the hiring pool is large.
- **B:** Astro + React islands, matching the website's toolchain.
- Either way: a new repo `lucento-mission-control`, its own Vercel project, and no code shared with the website except
  copied pure modules (Play state mapping).

### FD-9 · What "Lifetime complimentary Pro" means
The schema requires every `GRANT` to expire, and paid Lifetime is "phone features forever + 12 months of online
services".
- **A (recommended):** mirror paid Lifetime: a new source `GRANT_LIFETIME` (LOCAL_PRO, never expires) plus a 12-month
  `GRANT` for CLOUD, AI_PRO and SHARED. Needs an app release that accepts the new source before the migration.
- **B:** let `GRANT` have no expiry, covering all four capabilities forever (ongoing server cost per account, forever).
- **C:** no Lifetime comps; use 1-year grants and renew.

### FD-16 · Retention periods
Proposed: audit log 7 years for privileged actions, 2 years for read-access entries; tickets 2 years after closing
(address erased on account deletion); email events 90 days; investigations 7 years (metadata only).
- **A (recommended):** as proposed.
- **B:** shorter (for example 1 year everywhere), less evidence, less to protect.

### FD-18 · Google Play access for Mission Control
- **A (recommended):** a new service account, invited with *View app information*, *View financial data*, *Reply to
  reviews*; access to the reports bucket; the Reporting API enabled. No *Manage orders*.
- **B:** reuse the website's service account (simpler, but one key then does everything).
- Also confirm enrolment in Play's 15 % service fee tier, which the revenue estimates assume.

### FD-19 · Crash data route
- **A (recommended):** Play Developer Reporting API (all users, no extra cost) plus Crashlytics BigQuery batch export in
  the sandbox; Firebase velocity alerts by email for spikes.
- **B:** Play vitals only.
- **C:** the Blaze plan with streaming export (near-real-time, a small cost).

## Customer promises (each needs a Privacy Policy change and usually an app release)

### FD-1 · Can Lucento staff see a customer's records?
Today's policy: only the account and its Shared partners can read records.
- **A (recommended):** only with the customer's in-app, time-boxed consent (Help › Let support see my records), plus an
  Owner-only, logged break-glass for legal or security emergencies with a notice to the customer.
- **B:** never. Support works from metadata only (plan, sync times, counts, error codes).
- **C:** staff can access when handling a ticket, disclosed in the policy (simpler, weaker promise; not recommended).

### FD-2 · Policy updates and their timing
Changes needed depending on FD-1, FD-3 and FD-4: support access (if A or C); operational metadata per account (app
version, device class, last seen, sync error codes: the `report_client` call); Orders API data (price, tax, region per
purchase); product-news emails (if FD-4 A or B); Resend tracking (only if enabled). Recommended: one consolidated policy
update shipped with the app release that adds the consent switches, announced in-app.

### FD-3 · Product analytics model
- **A (recommended):** keep usage counts anonymous; extend the anonymous allowlist with feature events; measure
  per-account adoption only as server-side counts of synced data, shown in aggregate.
- **B:** add an opt-in "Help improve Lucento with my account's usage" that links events to the account (enables
  per-user journeys and at-risk lists; needs consent UI and policy text).
- **C:** account-linked analytics by default (not recommended; contradicts today's promise).

### FD-4 · Marketing email
- **A (recommended):** later, with an explicit "Product news" opt-in (off by default), a separate subdomain
  (`news.lucentoapp.com`), Resend Topics for unsubscribes, no open/click tracking at first. In-app announcements first.
- **B:** opt-in email from Phase 1.
- **C:** no marketing email at all. In-app announcements and store release notes only.
- **Note:** existing account holders didn't consent to marketing. They can't be emailed product news until they opt in.

### FD-13 · What "suspended" means
Lucento works offline, so suspension can't lock the app on the phone.
- **A (recommended):** suspension blocks sign-in and server features (sync, AI, Shared, purchase restore); local data
  keeps working; used only for abuse, fraud or a security hold; the customer is told why unless that's unsafe.
- **B:** don't offer suspension; use deletion or nothing.

### FD-15 · Admin-initiated deletion and refunds
- Cool-off before an admin deletion runs: **24 hours (recommended)**, or none.
- Should Mission Control cancel or revoke the customer's Play subscription through the API when deleting? **Recommended:
  no.** Tell the customer how to cancel in Play; refunds stay in Play Console.

## Business and accounting

### FD-10 · Reporting currency and revenue recognition
- Reporting currency: **PHP (recommended)**, the Play merchant currency for a PH developer. USD-priced orders shown
  with both values and the stated rate.
- Lifetime includes 12 months of online services. Recognize the whole sale on purchase (cash view) or defer part of it
  over 12 months (accrual view)?
- **Recommended:** Mission Control shows the cash view by default and can show the deferred view; ask your accountant
  which one tax filings use (BIR).

### FD-11 · Support tooling
- **A (recommended):** build the minimal inbox in Mission Control on Resend Inbound (`support@lucentoapp.com`), keeping
  the Gmail address as a forwarder.
- **B:** a hosted help desk (Help Scout, Plain, Front) with an API link to Mission Control for customer context. A
  monthly cost, less to build.
- **C:** stay on Gmail until volume demands more.

### FD-12 · AI provider for Founder Intelligence
- **A (recommended):** Gemini on a separate key and quota from the customer proxy (one vendor, existing fallback logic).
- **B:** Claude (Anthropic API).
- **C:** OpenAI (as in the brief).
- Whatever the vendor: aggregates only, cited answers, no write tools, paid-tier terms (no training on inputs).
