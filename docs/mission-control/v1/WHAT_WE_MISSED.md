# What We Missed — V1

Things the brief doesn't mention that matter now, then the future opportunities it asked to have documented.

## Before launch (not Mission Control features, but found by the audit)

Decision FD-V1: stay on free tiers for now and upgrade depending on reception. V1 softens the risks with a weekly
encrypted backup (FD-V13) and quota warnings at 70 % and 90 %.

| # | Item | Why now | Effort |
|---|---|---|---|
| 1 | **Production backups:** move Supabase to Pro, or at least schedule an encrypted weekly dump | Production holds ledgers and purchase entitlements with no usable backup on Free. The public deletion page talks about backups expiring within 30 days | S |
| 2 | **Email capacity:** upgrade Resend; separate KlaroPH or accept the shared quota; raise Supabase Auth's 30/hour email limit | 100/day across two products. Each email sign-up costs two emails; support inbound and replies add more. Sign-ups fail silently at the cap | S |
| 3 | **Vercel plan check** | Commercial use (selling Pro) may not be allowed on Hobby | S |

## Small additions inside V1 (included in PRODUCT_SCOPE.md)

| Item | Why |
|---|---|
| Resolve purchase links | `apply_play_purchase` already hands `review_required` to "support", and no tool exists. Likely the first paid-customer ticket |
| Activity snapshots from day one | Without the hourly job, DAU/MAU history can't be recovered later; `usage_events` also purge at 90 days |
| Admin accounts excluded from metrics | Your admin login (and test accounts) would otherwise inflate user counts |
| Received mail counts toward the quota | Turning on support inbound uses the same Resend quota as sign-up emails |
| Deletion cool-off | Undo for the most irreversible action; matches the 30-day promise on the deletion page |
| Records view off until the policy ships | Mission Control never does more than the published policy says |
| Weekly encrypted backup (free) | Staying on Supabase Free means no built-in backups (FD-V13) |

## Worth deciding soon, after V1

| Item | Why | Needs |
|---|---|---|
| Alerts (email to you) when a service is down or the quota passes 80 % | Mission Control is only useful for problems if you look; an alert reaches you when you don't | Small job |
| In-app reply thread and screenshots in support requests | V1 replies by email and takes text only | App change |
| Play reviews in Support | Reviews are a public support channel for a consumer finance app | Play Reviews API permission |
| Data-subject request tracking | The deletion page promises 30 days; access requests have no process | Small table |
| Audit log hash chain + off-site copy | Makes tampering detectable even by the database owner | Small |

## Future opportunities (documented, not built)

| Opportunity | What it would add | Prerequisite |
|---|---|---|
| Marketing automation | Opt-in product news, lifecycle emails | Consent in the app, policy change, separate sending subdomain |
| AI founder intelligence | Daily briefing and questions answered from `daily_metrics`, every number cited | ≥ 30 days of snapshots |
| Feature flags / kill switches | Turn off AI answers or invites during an incident without a release | App reads a remote config |
| Advanced analytics and cohorts | Retention by signup month, conversion funnels | Activity history; decision on account-linked analytics |
| Referral management | Invite-a-friend rewards | In-app mechanics, abuse controls, Play policy check |
| Lifecycle management | At-risk and win-back flows; Lifetime owners' service-year renewals (first ones end Dec 2027) | Activity history, consented email |
| Incident management | Declare, post updates, notify affected users in the app | In-app notices |
| Financial reconciliation | Orders ↔ Play earnings reports ↔ payouts, month locked when reconciled | Earnings report import from Cloud Storage |
| Cost intelligence | Cost per active account and per AI request | Billing exports (Google Cloud, Vercel, Supabase, Resend) |
| Multi-administrator permissions | Support, engineer and analyst roles with approvals | A `role` on `mc.admins`; permission checks in `require_admin` |

## Things in the brief worth challenging

- **"DAU/MAU"**: honest only as "signed-in accounts active". People who use Lucento without an account are counted
  only by Google Play. The labels in V1 say so.
- **"App version" per user**: impossible without an app change. V1 shows "not reported" instead of guessing.
- **"Edit permitted profile information"**: there is no profile; email changes are rare and best left to the Supabase
  dashboard until they're common.
- **"Read-only investigation"**: breaks a published promise unless the policy changes first. Decided: disclose it in
  the policy (FD-V3), shipped with the support-form release.
- **"Net revenue"**: only Play's monthly earnings report has the real figure. V1 shows a labelled estimate and links
  to the report.
