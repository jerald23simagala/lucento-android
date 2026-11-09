# Account emails

Lucento sends three emails, all about the person's own account. Nothing else: no newsletters, no marketing.

| Email | Sent by | When | Once? |
|---|---|---|---|
| **Verify your email** ("Welcome to Lucento — verify your email") | Supabase Auth, over SMTP through Resend (`send.lucentoapp.com`) | An email sign-up | Supabase's own rules (resend from the app's "Check your inbox") |
| **Welcome** ("Welcome to Lucento") | lucento-website `POST /api/account/welcome` → Resend API | The app asks after a sign-in; the site sends only when the address is verified and the account is less than 14 days old | Once per account (`public.account_emails`), whichever phone asks |
| **Pro confirmation** ("Welcome to Lucento Pro" · "Your Lucento Pro trial has started" · "Pro Lifetime is yours" · "Lucento Cloud is on") | lucento-website `POST /api/billing/verify` → Resend API | The first verification of a new purchase (ACTIVE or TRIAL, bought in the last 3 days) | Once per purchase token: renewals, re-checks and restores send nothing |

## Where things live

- Verify template: `supabase/templates/confirm-signup.html`, pasted into both projects (Authentication › Emails ›
  Templates › Confirm signup). Supabase fills `{{ .ConfirmationURL }}` and `{{ .Email }}`.
- Welcome and Pro templates: lucento-website `src/server/email-templates.ts` (HTML and plain text, unit-tested in
  `src/server/email.test.ts`). Images are PNGs in lucento-website `public/email/`, made from the mascot art (copies; the
  WebP originals are untouched).
- Sending and the once-only rule: lucento-website `src/server/email.ts`. It claims the email in `public.account_emails`
  (`claim_account_email`, service role only) before sending, and gives the claim back if Resend refuses it, so the next
  attempt retries.
- App: `data/account/WelcomeEmail.kt` asks once per signed-in account and remembers settled accounts on the device
  (`account_emails` shared preferences, account ids only). It sends only the account's own token.
- Database: `supabase/migrations/20261110090000_account_emails.sql`; offline checks
  `tools/sql-verify/account_emails_suite.sql` (16 checks).

## Rules for the copy

Same as the store listing and customer copy: no prices (Google Play's receipt has them), no "no ads" promise, never call
the on-phone reader AI, Lifetime always separates what's owned from the 12 months of online services, and every email
says why the person got it. Replies go to lucentoapp@gmail.com.

## Setup (owner)

1. Apply `20261110090000_account_emails.sql` to lucento-test, then production (SQL editor).
2. Resend › API Keys › create a key with **Sending access** limited to `send.lucentoapp.com`.
3. Vercel › lucento-website › Environment Variables (Production): `LUCENTO_RESEND_API_KEY` (the new key) and
   `LUCENTO_SUPABASE_SERVICE_ROLE_KEY` (production's secret key; billing needs it too). Optional: `LUCENTO_EMAIL_FROM`
   (default `Lucento <hello@send.lucentoapp.com>`), `LUCENTO_EMAIL_REPLY_TO` (default `lucentoapp@gmail.com`).
4. Both Supabase projects: the Confirm signup template and subject (OT-13), and the SMTP sender on
   `@send.lucentoapp.com`.
5. Redeploy the site. Check: sign up on a phone, verify, open Lucento signed in → the welcome arrives once.

Until steps 1–3 are done, `/api/account/welcome` answers 503 and the app simply asks again at the next launch; purchases
work as before without the confirmation.
