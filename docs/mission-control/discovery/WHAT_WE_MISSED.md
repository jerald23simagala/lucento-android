# What We Missed — Additional Founder Capabilities

Capabilities that aren't in the brief, ranked by business value for Lucento now (pre-launch, one founder, Google
Play only, PH-first with a global listing). Each states what it does, why Lucento needs it, the problem it solves,
integrations, data, complexity (S/M/L) and priority.

**Verdicts:**
- **Now:** before or with Mission Control Phase 1. Several aren't Mission Control features at all; they're launch
  risks discovered during the audit.
- **Later:** a named phase.
- **Not at all:** with the reason.

---

## Implement now

### 1. Backups and disaster recovery — **Now (launch blocker, independent of Mission Control)**
- **What:** real database backups, a tested restore, and a visible "last backup / last restore test" on Operations.
- **Why Lucento needs it:** production is on the Supabase **Free plan**. Lucento holds people's synced ledgers and
  their purchase entitlements. Losing the database means losing who paid for Lifetime. The public deletion page also
  promises backups "expire within 30 days", which presumes backups exist.
- **Problem solved:** unrecoverable data loss; an unverifiable promise.
- **Integrations / data:** Supabase Pro (daily backups; PITR add-on later), a weekly encrypted `pg_dump` to a locked
  Google Cloud Storage bucket, a monthly restore test into a scratch project.
- **Complexity:** S (plan change) + M (dump and restore-test job).

### 2. Email capacity and deliverability guard — **Now (launch blocker)**
- **What:** move Resend off the free tier; raise Supabase's auth email rate limit from the default 30/hour; Resend
  webhooks into Mission Control; quota alerts at 70 %.
- **Why:** each email sign-up costs two emails (verify + welcome) out of a **100/day** cap that is shared with KlaroPH
  on the same Resend account. A launch-day spike silently stops sign-ups, and password resets fail with them.
- **Problem solved:** invisible sign-up failure at the worst possible moment.
- **Integrations:** Resend (plan, webhooks, usage), Supabase Auth settings.
- **Complexity:** S.

### 3. Daily metric snapshots — **Now (Phase 1)**
- **What:** a nightly job that writes aggregates to `mc.daily_metrics` (DATA_ARCHITECTURE.md §6).
- **Why:** `usage_events` are purged after 90 days and entitlement rows are updated in place. Without snapshots, there
  is **no history**: every trend chart in the brief would be impossible for data older than 90 days, and the launch
  period is the history most worth having.
- **Problem solved:** permanent loss of the launch-period trends.
- **Integrations / data:** pg_cron; existing tables only.
- **Complexity:** S.

### 4. Purchase ownership and entitlement support tools — **Now (Phase 1)**
- **What:** resolve `linked_elsewhere` / `review_required` purchase links; re-verify a purchase with Google on demand;
  see every entitlement row with its source.
- **Why:** `apply_play_purchase` already returns `review_required` with "needs support" in the design, and the
  account-switch wipe makes "I bought it but the app says Free" the most likely early ticket. Today the only tool is
  raw SQL on production.
- **Problem solved:** paying customers locked out of what they bought; risky manual fixes.
- **Integrations:** Play Developer API (existing service account logic), `mc` functions.
- **Complexity:** M.

### 5. Cost and quota watch — **Now (Phase 1)**
- **What:** one panel of every metered limit: Resend daily/monthly, Supabase DB size/MAU/egress, Vercel invocations,
  Gemini spend for the customer proxy, Play API quota. Thresholds raise alerts.
- **Why:** Lucento's free tier, AI allowances and Lifetime promises (12 months of online services) make costs per user
  the real unit-economics question. A runaway Gemini bill or a quota wall hits a solo founder hard.
- **Integrations:** Resend usage, Supabase Management API, Vercel usage, Google Cloud billing export (Gemini).
- **Complexity:** M.

### 6. Security event monitoring — **Now (Phase 1)**
- **What:** alerts for admin sign-in failures, new admins, break-glass, rate-limit refusals, and new Supabase advisor
  findings (RLS, exposed functions, leaked-password protection).
- **Why:** Mission Control itself is the new attack surface; the advisors already exist and cost nothing to poll.
- **Complexity:** S.

## Implement later (with a named phase)

### 7. Incident management with in-app service notices and kill switches — **Phase 3**
- **What:** declare incidents, post a public message, and flip server-side kill switches (pause AI answers, pause
  Shared invites, put verification into "accept Play's local reading" mode) through a `remote_config` table read by
  the website proxy and the app.
- **Why:** Lucento depends on Gemini capacity (already seen to fail), Play, and Supabase. Without a switch, the
  options in an outage are a code deploy or silence.
- **Integrations:** website proxy, an app release to read notices and flags.
- **Complexity:** M.

### 8. In-app "Contact support" with a consented diagnostics bundle — **Phase 2**
- **What:** a Help screen that creates a ticket with app version, Android version, sync state per table, plan source
  and recent error codes, shown to the person before sending.
- **Why:** an offline-first app's bugs live on the phone. Asking customers for versions and screenshots by email
  doubles every ticket's length.
- **Integrations:** an app release, `public.create_support_request`.
- **Complexity:** M.

### 9. Store review monitoring and replies — **Phase 2**
- **What:** Play reviews in Mission Control, reply from there, turn a review into a ticket.
- **Why:** in a consumer finance app, reviews are both the main acquisition signal and a support channel. A 1-star "I
  paid and it says Free" is a ticket with an audience.
- **Integrations:** Play Reviews API (new service account permission).
- **Complexity:** S.

### 10. Data-subject request tracker — **Phase 2**
- **What:** access, export, correction and deletion requests with a 30-day clock, verification state and a
  completion record.
- **Why:** the deletion page already promises "within 30 days". The Philippine Data Privacy Act, GDPR (for EU users
  of a global listing) and Google Play's deletion policy all expect a demonstrable process.
- **Complexity:** S.

### 11. Revenue reconciliation — **Phase 3**
- **What:** entitlements ↔ Play orders ↔ earnings report, month by month, with discrepancies explained and the month
  locked when reconciled.
- **Why:** the brief rightly says gross ≠ payout. Reconciliation is what makes the numbers trustworthy enough for
  taxes (BIR), an accountant, or an investor.
- **Integrations:** Orders API, earnings reports in Cloud Storage, manual payout entry.
- **Complexity:** M.

### 12. Lifetime services renewal tracking — **Phase 3**
- **What:** the cohort of Lifetime owners whose included 12 months of online services end each month, their Cloud
  add-on uptake, and what they lose (sync, AI, Shared).
- **Why:** the first wave (December 2026 buyers) reaches its end in December 2027. That's Lucento's biggest
  predictable revenue and churn moment, unique to its pricing.
- **Complexity:** S (the data is in `entitlements`).

### 13. Version adoption and release health — **Phase 3**
- **What:** version share (Play stats + `report_client`), crash/ANR rate per version, staged rollout status, with a
  "halt rollout" deep link.
- **Why:** sync and billing behaviour change with app versions; old versions can break against new migrations (the
  PGRST204 stall seen in November).
- **Complexity:** M.

### 14. Data integrity monitors — **Phase 4**
- **What:** nightly checks: transfer groups with ≠ 2 live legs, orphaned category references, entitlement rows past
  expiry still ACTIVE, GRANT rows not matching `entitlement_grants`, Shared transactions with impossible actors.
- **Why:** last-write-wins and offline edits can produce states no single device sees. Finding them before customers
  do is cheap.
- **Complexity:** M.

### 15. In-app announcements — **Phase 4**
- **What:** version- or plan-targeted notices in the app.
- **Why:** the safest customer communication channel. It needs no email consent and reaches local-only users who have
  no account at all.
- **Complexity:** M (an app release).

### 16. Unit economics — **Phase 5**
- **What:** cost per active synced account, per AI request, per Lifetime owner per year, against revenue per paying
  customer.
- **Why:** tells whether ₱59/₱490 for the Cloud add-on and the AI allowances are sustainable.
- **Complexity:** M (after #5 and #11).

## Not at all (for now)

| Idea | Why not |
|---|---|
| Affiliate management | No affiliate channel exists; Play billing makes payouts awkward; fraud risk outweighs value |
| Referral programme | Needs in-app mechanics, abuse controls and Play policy review; revisit after product–market fit |
| Feature-request voting portal | Volume is low; tag requests in tickets and count them. A public board creates promises |
| NPS surveys | Needs in-app prompts and consent; Play ratings and ticket themes are enough signal early on |
| Pricing experiments in Mission Control | Play Console runs price experiments natively; Mission Control should only report on them |
| Beta user management | Play testing tracks and Google Groups already do it; a list of tester accounts with comps is enough |
| Geographic expansion tooling | Play's country targeting and custom store listings cover it; Mission Control reports country performance |
| Customer "health scores" | Needs account-linked behavioural data the policy rules out (FD-3); sync recency is the honest proxy |
| Real-time everything | Most business metrics are daily by nature; real-time only where action is minutes away (health, incidents) |

## Assumptions in the brief worth challenging

1. **"Build all nine modules."** At 5 production accounts, most of the BI surface would chart nothing for months.
   Build the operational core first, and let data accumulate (via snapshots) before building views on it.
2. **"Investigate customers' data."** The current privacy promise forbids it. A consent-first design is better for
   the customer and safer for the founder: it turns "can staff see my money?" into a feature ("only if you let us,
   and you'll see it happened").
3. **"Marketing via Resend."** Lucento's brand is calm, private, no-nonsense. Start with in-app announcements and
   release notes. Add opt-in email only if it proves necessary, and keep it on a separate subdomain.
4. **"OpenAI."** The stack already runs on Gemini with working fallback logic. Use one AI vendor unless there's a
   reason, and keep Founder Intelligence away from customer data whatever the vendor.
5. **"Next.js because the website is Next.js."** It's Astro. Next.js may still be the right call for an app-like
   console (FD-6), but for its own reasons.
6. **"Lifetime only, so MRR is zero."** Subscriptions exist, and the Cloud add-on turns Lifetime owners into
   subscribers after a year. MRR will be real, which is exactly why it must be kept strictly apart from Lifetime sales.
