# Security Considerations — V1

Mission Control can read customers' accounts and change what they're entitled to. V1 keeps the controls few, strong and
enforced in the database, so a bug in the web app can't bypass them.

## 1. Core design: the database decides

```
Browser ── session cookie ──▶ Mission Control (Next.js server, Vercel)
                                 · checks: signed in, MFA (aal2), email allow-listed, session not idle/expired
                                 · calls Supabase RPC  mc_api.<action>(...)  WITH THE ADMIN'S OWN ACCESS TOKEN
Postgres (production)
  mc_api.<action>   SECURITY DEFINER, search_path = ''
     1. mc.require_admin()   → auth.uid() is in mc.admins, active
                             → auth.jwt() ->> 'aal' = 'aal2'
     2. mc.rate_limit(action)
     3. validate, read "before", change, read "after"
     4. insert mc.audit_log  (same transaction: no action without its record)
```

- **No service-role key for database work.** Mission Control uses the publishable key plus your signed-in session,
  exactly like the app does. A leaked Mission Control environment can't read the database without your password *and*
  your authenticator.
- `mc` (tables) isn't exposed through the API at all; `mc_api` (functions only) is, with `EXECUTE` granted to
  `authenticated` and every function starting with `mc.require_admin()`. A customer who calls one is refused, and the
  refusal is logged.
- The Mission Control server repeats the checks (defence in depth) and adds idle and absolute session limits, which the
  Free plan's Auth can't enforce.

## 2. Admin identity

| Control | V1 |
|---|---|
| Dedicated founder authentication | A separate admin login (not your customer account) in production Auth; Mission Control only offers sign-in, never sign-up |
| MFA | TOTP required: Mission Control refuses to load data at `aal1`, and every `mc_api` function rejects tokens without `aal2` |
| Allowlist | `mc.admins` (database) **and** `MC_ADMIN_EMAILS` (server env). Both must match |
| No public registration | Mission Control has no sign-up page. Production Auth must stay open for the app, which is why the allowlist, not sign-up, is the gate |
| Session expiry | Mission Control signs you out after 30 minutes idle or 12 hours, whichever comes first (a signed, HttpOnly cookie records both times) |
| Step-up | Lifetime grants, deletion, suspension and record viewing ask for a fresh TOTP code (a Supabase MFA challenge) if the last one is older than 10 minutes |

**Trade-off (FD-V2).** Keeping the admin login in the production project is simpler (one project, one migration
history), and safe because the admin account has no database privileges of its own: power lives in `mc_api` functions
that check `mc.admins`. The alternative, a separate Supabase project just for admin sign-in, isolates the admin
identity completely but adds a second project and token verification across projects. Recommended for V1: the same
project.

## 3. Least privilege

| Credential | Can do | Can't do |
|---|---|---|
| Publishable key + admin session | Call `mc_api` functions | Read any table directly (RLS and no `mc` exposure) |
| `RESEND_SEND_KEY` | Send from `send.lucentoapp.com` | Send as KlaroPH; read logs |
| `RESEND_READ_KEY` | List emails | Used by sending code |
| `PLAY_SERVICE_ACCOUNT_JSON` | Read orders and purchase state | Refund, cancel, change the store listing |
| `VERCEL_TOKEN` | Called only with GET by the code | (Token itself is broad: kept server-side, 90-day expiry) |
| BigQuery service account | Read the Crashlytics dataset | Anything else in Google Cloud |
| No secret/service-role key | — | — (added only if FD-V6 approves email edits) |

The browser bundle holds only the Supabase URL and publishable key. CI fails the build if any other secret name
appears in client code.

## 4. Destructive and sensitive actions

| Action | Confirmation | Extra |
|---|---|---|
| Grant 30 / 90 days | Reason (required) + confirm | — |
| Grant 1 year / Lifetime | Reason + step-up | Lifetime shows what it costs in online services |
| Revoke complimentary | Reason + confirm | The dialog lists the paid entitlements that stay |
| Suspend / reactivate | Reason + step-up | Explains the offline effect: the app keeps working locally; sign-in, sync, AI, Shared and purchase restore stop |
| Delete account | Typed confirmation (the email's local part) + reason + step-up | Runs after a **24-hour cool-off**, cancellable; same cascade as the in-app deletion |
| View records | A reason (usually the ticket) + step-up | Read-only; every tab opened and note revealed is logged; off until the policy update ships |
| Send an individual email | Preview + confirm | Recipient must be an existing account's verified address; fixed templates only |

Rate limits (per admin, per hour, enforced in `mc.rate_limit`): 20 grants or revocations, 30 profile opens, 5
suspensions, 3 deletions, 20 emails. Exceeding one is refused and logged.

## 5. Audit log

- `mc.audit_log` records the administrator, action, affected user, timestamp, reason, result (ok, refused, failed),
  and before/after values for every change. Profile opens and record views are logged as `access.*` entries.
- **Not editable:** no `UPDATE` or `DELETE` privilege for any API role, plus a trigger that raises on update or delete.
  Mission Control has no edit UI for it. (Someone with the database owner's password could still alter it. A
  hash chain and an off-site export are documented as future work.)
- Entries survive account deletion: they keep the bare user id and never store record contents or the email address.

## 6. Customer records and the privacy promise

The current policy says only the account (and Shared partners) can read a customer's records. The founder chose
disclosure over a consent step (FD-V3), so:

- **Account metadata** (plan, entitlements, counts, sync times, status, app version, tickets): visible to the admin.
- **Record contents** (wallet names and balances, transactions and notes, budgets, goals, debts, recurring): viewable
  when handling support. Opening them needs a reason (usually the ticket number) and a fresh authenticator code; notes
  stay masked until revealed; every tab opened and every note revealed is in the audit log.
- **Disclosure:** the website /privacy and the in-app policy text change together in the app release that adds the
  support form (FD-V15), for example: "To help with a problem, Lucento staff can view your account and synced records.
  Every view is logged." The `records_view_enabled` setting stays off until that release is live, so Mission Control
  never does more than the published policy says.
- Never: customer passwords, tokens or sessions; signing in as a customer; changing records while presenting it as the
  customer's own action.

## 7. Web hardening

- Next.js server components and server actions; data responses `Cache-Control: no-store`.
- Strict Content-Security-Policy (self + Supabase + chart library), `frame-ancestors 'none'`, HSTS, Referrer-Policy
  `no-referrer`.
- CSRF: SameSite=Strict cookies + origin checks on actions. Webhooks verify Resend's signature and are idempotent.
- No analytics or third-party scripts. Dependencies pinned; Dependabot alerts on.
- Preview deployments point at lucento-test and sit behind Vercel's deployment protection.
- Marketing media (MARKETING_APPROVALS.md §6): `img-src` and `media-src` add exactly one origin, the private Blob
  store's own (`https://<store id>.private.blob.vercel-storage.com`, built from a validated `BLOB_STORE_ID`); nothing is
  wildcarded. Files are reached through short-lived signed links for this environment's pathnames only.
- Claude's tool calls `/api/marketing/pack`, `/upload`, `/uploaded` and `/feedback` with a bearer secret of its own
  (checked by the database, `mc.ingest_secret_ok('marketing')`); those four exact paths skip the cookie gate, anything
  else under `/api/marketing` is gated. The tool's secret can't mark a file ready: only the console's own secret can.
- `?next=` after sign-in keeps the admin on Mission Control: same-origin paths only, judged after URL normalisation (so
  `/.//host`, `/a/..//host` and `/%2e//host` can't become `//host`), never back into sign-in or `/api`; the proxy also
  refuses any redirect that would leave its own origin.

## 8. Testing the controls (Phase 4)

On lucento-test, scripted SQL tests in `tools/sql-verify` (the existing offline harness) for: a non-admin calling each
`mc_api` function is refused; `aal1` tokens are refused; audit rows can't be updated or deleted; record reads fail
without a reason, without a fresh `aal2`, or with the setting off; `submit_support_request` enforces its rate limits
and never reveals other tickets; revoking a grant never touches PLAY_* rows; deletion parity with `delete_my_account`.
Plus a browser test that no secret appears in client bundles. No destructive operation runs against production during
testing.
