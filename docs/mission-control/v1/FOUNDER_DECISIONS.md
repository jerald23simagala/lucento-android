# Founder Decisions — V1

Decided by the founder on 2026-11-09, through multiple-choice questions.

| Id | Decision | Answer | What it means for the build |
|---|---|---|---|
| FD-V1 | Plans before launch | **Stay on free tiers for now; upgrade depending on reception** | Supabase Free (no built-in backups: see FD-V13), Resend free (100/day shared with KlaroPH: Mission Control's quota meter and alerts matter), Vercel unchanged |
| FD-V2 | Admin login | **Production Supabase, separate admin email**, TOTP, allowlist | As designed |
| FD-V3 | Viewing customers' records | **Always allowed when handling support, disclosed in the privacy policy** | No customer consent step. Every view still needs a reason (usually a ticket), a fresh authenticator code, and is logged. Records view stays off until the policy update ships (FD-V15) |
| FD-V4 | Support intake | **A Supabase table fed by an in-app support form** (founder's proposal) | Android change approved for this purpose. Gmail stays as a secondary channel; Mission Control can log those as manual tickets. No Resend inbound |
| FD-V4a | How customers get replies | **By email** (Resend), customers answer by email | Small app change; replies use a few emails from the quota |
| FD-V4b | Who can send requests | **Everyone**; signed-out people type an email; rate-limited per install | Covers people who use Lucento without an account |
| FD-V7 | App version | **Yes: reported daily for signed-in accounts and attached to support requests** (app version, Android version, phone model) | Fills "App version" in Users; needs a policy line |
| FD-V5 | Complimentary Lifetime | **Same as paid Lifetime**: phone features forever, online services for 12 months | Constraint change on `public.entitlements` only |
| FD-V6 | Edit profile information | **Nothing in V1**; email changes in the Supabase dashboard | No service-role key in Mission Control |
| FD-V8 | Migration files | **`lucento-android/supabase/migrations/`** | One history for the one database |
| FD-V9 | Supabase recent errors | **No management token in V1**; link to Supabase › Logs | As designed |
| FD-V10 | Play service account | **A new one with financial view only** | Owner creates it in Phase 4 |
| FD-V11 | Framework | **Next.js + Tailwind + Chart.js** | As briefed |
| FD-V13 | Backups while on Free | **Weekly encrypted dump via GitHub Actions** (free) | Owner adds the database connection string and an encryption key as GitHub secrets; Mission Control shows the last backup |
| FD-V15 | Privacy-policy timing | **With the app release** that adds the support form: website /privacy and in-app text together | Records view enabled only after that release is live |
| FD-V12 | Next step | **Update the documents, then wait for the Phase 4 go-ahead** | Done; the go-ahead came 2026-11-15 ("Lets start building now") |

## Implementation notes (2026-11-15, Phase 4)

Engineering choices made while building, within the decisions above. Items marked **(owner to confirm)** affect the
privacy text and are confirmed when the policy update is approved (FD-V15).

| Note | What was built | Why |
|---|---|---|
| IN-1 | Backups are kept **28 days**, not 90 **(owner to confirm)** | lucentoapp.com/delete-account promises backup copies expire "within 30 days" |
| IN-2 | Support requests are deleted **with the account**, and **12 months after they're resolved** **(owner to confirm)** | The deletion promise covers everything stored under an account; signed-out requests need some end |
| IN-3 | Net revenue uses Google's **reported** developer revenue per order (`developerRevenueInBuyerCurrency`); only orders without it are estimated (EST) | The Orders API reports it; an estimate was the design's fallback |
| IN-4 | Subscription **renewals** are not in Revenue yet: only first orders are stored. A small website change (record each renewal's order id) is planned before the first renewals | Purchase tokens are kept only as hashes, and the Orders API can't list orders |
| IN-5 | The browser holds **no** Supabase key at all; every call goes through Mission Control's server with the admin's session | Stricter than "publishable key only" (SECURITY_CONSIDERATIONS §3) at no cost |
| IN-6 | Signed-out support requests share an hourly cap (Settings, default 20) on top of 3 per install a day | A new install id is free to invent; the cap bounds abuse of the open form |
| IN-7 | Hand-written GRANT rows (production: the owner's, the test account's and Rachel's) become "Before Mission Control" grants; recomputing writes the same row | So the first grant or revoke on those accounts can't undo them |
| IN-8 | A GRANT row written by hand later (outside Mission Control) is never touched by Mission Control's jobs | Grants should be given in Mission Control from now on |

## Risks accepted with FD-V1 (for the record)

- Email capacity: about 50 email sign-ups a day before verify/welcome emails start failing, shared with KlaroPH's
  sending. Mission Control's quota meter warns at 70 % and 90 %.
- Backups: the weekly dump (FD-V13) limits loss to at most a week; Supabase Pro would give daily backups.
- Vercel: the plan still needs checking for commercial use.
