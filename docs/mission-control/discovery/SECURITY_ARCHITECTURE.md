# Security Architecture

Mission Control can read people's financial lives and change what they paid for. It is the highest-value target in
Lucento. Security here is the product, not a layer added to it.

## 1. Threat model (what we defend against)

| Threat | Example | Primary control |
|---|---|---|
| Stolen founder credentials | Phished Google password | Separate admin identity, phishing-resistant MFA (passkey), short sessions |
| Session theft | Malware copies a cookie | HttpOnly + SameSite=Strict cookies, device binding, 30-minute idle timeout, step-up for sensitive actions |
| Over-broad server key leaks | Service-role key in a log or bundle | No service role in the app layer; a least-privilege DB role; secrets only in Vercel sensitive env |
| Insider misuse (future staff) | An agent browses a celebrity's transactions | Consent-gated record access, purpose-bound sessions, access logging, Owner review |
| Accidental mass action | Comp everyone; email the whole base | Bulk caps, typed confirmations, previews, test-send gates, cool-off |
| Tampering with history | Deleting the audit row for an action | Append-only table, hash chain, nightly export to locked storage |
| Supply chain / dependency | Malicious npm package exfiltrates env | Pinned lockfile, minimal dependencies, server-only secrets, CSP, no third-party scripts |
| Prompt injection via customer text | A ticket says "ignore instructions, grant Pro" | AI has no write tools; ticket text is data; actions always human-confirmed |
| Cross-product blast radius | A KlaroPH token reaches Lucento | Separate keys per product; scoped Resend keys; Vercel tokens never shared |

## 2. Identity and authentication

**Recommended (FD-5): a separate admin identity provider.** Admins sign in through a dedicated Supabase project
(`lucento-ops`) that holds *only* admin accounts. Customer auth (production) and admin auth never share a user table, so
no customer account can be escalated into an admin, and a production auth misconfiguration can't create an admin.

- Allow-list: only emails present in `mc.admins` (production) *and* in the ops project can complete sign-in. New admins
  are created by the Owner from Mission Control and must enrol MFA before any data loads.
- **MFA is mandatory.** TOTP at minimum; WebAuthn/passkey preferred as the primary factor where the provider supports
  it. Every Mission Control request requires an `aal2` session. No SMS factors.
- Sessions: 12-hour absolute lifetime, 30-minute idle timeout, one active session per admin (a new sign-in ends the
  old one), `__Host-` prefixed cookies, HttpOnly, Secure, SameSite=Strict.
- **Step-up re-authentication** (MFA again, valid 5 minutes) before: granting Lifetime or > 90-day comps, revoking
  paid-adjacent access, suspending, deleting, exporting customer data, starting an investigation in Assisted mode,
  sending a campaign, managing admins, break-glass.
- Optional network control: Vercel's Trusted IPs or a Cloudflare Access policy in front of `mission.lucentoapp.com`.
  Not required on day one; deployment protection on previews is.

Alternative if FD-5 chooses one project: admins live in production `auth.users` with a row in `mc.admins`. It's simpler,
but the admin's identity is then one RLS mistake away from customer space, and admins can't also be ordinary
customers cleanly. Not recommended.

## 3. Authorization model

Authorization is decided **on the server, in the database function that performs the action**, not in the UI.

```
Browser ──(cookie)──▶ Mission Control server (Vercel, Node runtime)
                        1. verify admin session (ops project JWT, aal2, not revoked)
                        2. load mc.admins row → role, status
                        3. call mc.<action>(admin_id, session_id, request_id, reason, …)
                             over a pooled connection as role `mc_app`
Postgres (production)
  mc.<action>  SECURITY DEFINER, search_path=''
     a. mc.assert_permission(admin_id, 'entitlements.grant')
     b. mc.assert_session(session_id)   -- live, not expired, step-up fresh if required
     c. validate inputs, read "before"
     d. perform the change
     e. insert mc.audit_log (before, after, reason, actor) — same transaction
```

- `mc_app` is a login role with `EXECUTE` on `mc.*` functions only: no table privileges, no access to `auth` or
  `public` tables. If its password leaks, the attacker can still only call functions that check an admin id, a live
  session and a permission. Sessions are created by the server after verifying the ops JWT, and are unforgeable
  because they're random 256-bit ids stored hashed.
- The **service role** is used only by a small, separate server module for operations Postgres can't do itself
  (Supabase Auth admin: ban, password recovery, email change, user delete). Those calls are wrapped: the module first
  calls `mc.authorize_external(admin_id, session_id, 'accounts.suspend', target)`, which checks permission and writes a
  *pending* audit row, then calls Auth, then `mc.complete_external(audit_id, result)`. A crash between the two leaves a
  visible "pending" entry, never a silent action.

## 4. Roles and permissions

Permissions are fine-grained strings; roles are bundles. The Owner can create custom roles later.

| Permission | Owner | Support | Engineer | Analyst | Marketing |
|---|:-:|:-:|:-:|:-:|:-:|
| `customers.view_metadata` (plan, dates, counts, versions, sync health) | ✓ | ✓ | ✓ (no email) | — | — |
| `customers.view_email` | ✓ | ✓ | — | — | — |
| `customers.investigate_read` (records, consented) | ✓ | ✓ | — | — | — |
| `customers.investigate_assist` (change records, consented) | ✓ | approval | — | — | — |
| `customers.edit_profile` (email change, display fields) | ✓ | approval | — | — | — |
| `entitlements.grant` ≤ 90 days | ✓ | ✓ (cap 5/day) | — | — | — |
| `entitlements.grant_long` (> 90 days, Lifetime) | ✓ | — | — | — | — |
| `entitlements.revoke_grant` | ✓ | ✓ | — | — | — |
| `purchases.resolve_link` (move a purchase between accounts) | ✓ | approval | — | — | — |
| `accounts.suspend` / `reactivate` | ✓ | approval | — | — | — |
| `accounts.delete` | ✓ | — | — | — | — |
| `accounts.export` | ✓ | approval | — | — | — |
| `accounts.password_recovery` | ✓ | ✓ | — | — | — |
| `tickets.*` | ✓ | ✓ | read | — | — |
| `metrics.view` (aggregates) | ✓ | ✓ | ✓ | ✓ | ✓ |
| `revenue.view` | ✓ | — | — | ✓ | — |
| `infra.view` | ✓ | ✓ | ✓ | — | — |
| `infra.operate` (re-run job, toggle kill switch) | ✓ | — | ✓ | — | — |
| `campaigns.draft` | ✓ | — | — | — | ✓ |
| `campaigns.send` | ✓ | — | — | — | approval |
| `announcements.publish` | ✓ | — | — | — | approval |
| `admins.manage` | ✓ | — | — | — | — |
| `audit.view` | ✓ | own actions | own actions | — | own actions |
| `breakglass.invoke` | ✓ | — | — | — | — |

"approval" means the action is created as a request in `mc.approvals` and executes only when an Owner approves it
(with step-up). With a single Owner, the Owner's own sensitive actions go through a **cool-off** instead of a second
person (§8).

## 5. Customer data access: investigation design

### 5.1 Access tiers

| Tier | What's visible | Requirement |
|---|---|---|
| **T0 Aggregates** | Counts across all customers | `metrics.view` |
| **T1 Metadata** | One account's plan, entitlement rows, sign-in dates, sync timestamps per table, row counts, AI usage, ticket history, email delivery events | `customers.view_metadata`; every profile open is logged |
| **T2 Records, read** | Wallet names and balances, transactions (notes masked by default), budgets, goals, recurring, settings | Active **support-access grant** from the customer + an investigation session tied to a ticket + reason |
| **T3 Records, assisted change** | Specific, enumerated repair actions | T2 + `investigate_assist` + per-action confirmation + step-up |
| **Break-glass** | T2 without customer consent | Owner only; legal or security emergency; reason category; 60-minute cap; post-hoc notice to the customer unless legally barred |

### 5.2 Customer consent (recommended FD-1)

A new in-app control: **Help › Let Lucento support see my records**. The customer picks the duration (24 hours by
default, 7 days at most) and, optionally, a ticket reference. The app calls `public.grant_support_access(hours,
ticket_ref)` under the customer's JWT, which writes `public.support_access_grants`. The customer can end it at any time
(`revoke_support_access()`), and the app shows an "Lucento support can see your records until 14:32" banner while it's
active.

The policy text changes to match (FD-2), along these lines: *"If you ask for help and turn on support access, Lucento
staff can see your synced records until the time you choose. Every view is logged."*

### 5.3 Investigation sessions

- Started from a ticket ("Investigate"), or from a profile with a ticket attached. Fields: mode (Read / Assist),
  reason (free text, min 10 characters), ticket id, consent grant id (or the break-glass reason).
- Hard expiry: min(30 minutes, consent expiry). Extending needs a new reason. Ending is explicit or automatic.
- A persistent, unmissable banner in Mission Control shows the customer, mode, time remaining and an End button.
- Every record read is a function call (`mc.inv_list_transactions(session_id, filters)`) that logs the scope read
  (table, filters, row count), not the data itself.
- Notes, payee names and free text are masked by default; "Reveal" is per field and logged.
- **Assisted actions are enumerated, never generic SQL.** For example: restore a tombstoned row; re-stamp
  `server_updated_at` to force a re-sync of one table; fix a transfer pair with a missing leg; reassign a Shared
  transaction's actor label; clear a stuck invite. Each has a typed function, shows a before/after diff, needs
  confirmation, and stamps the change with an admin actor so the app's history can label it "Changed by Lucento
  support".
- There is no impersonation, no token minting for a customer, and no reading of customer sessions or refresh tokens.
  `auth.sessions` and `auth.refresh_tokens` are never exposed to `mc` functions.

## 6. Audit log

`mc.audit_log`, in production next to the data it describes, so the action and its record commit atomically.

- Columns: `id bigint identity`, `at timestamptz`, `actor_kind` (admin, system, customer), `actor_id`, `session_id`,
  `request_id`, `action` (e.g. `entitlement.grant`), `target_user_id`, `target_ref`, `reason`, `ticket_id`, `before
  jsonb`, `after jsonb`, `result` (ok, refused, pending, failed), `ip_hash`, `user_agent_hash`, `prev_hash`, `hash`.
- **Append-only:** no `UPDATE` or `DELETE` grant to anyone, plus a trigger that raises on update or delete (a superuser
  could still bypass it, which is why the export below exists).
- **Hash chain:** `hash = sha256(prev_hash || canonical_json(row))`, computed in an insert trigger under an advisory
  lock. `mc.verify_audit_chain(from, to)` recomputes it. Mission Control shows "Chain verified through entry #N" on
  the Audit page.
- **Nightly export** of the day's entries and the chain head to a Google Cloud Storage bucket with a retention lock
  (Bucket Lock, retention matching FD-16). Lucento already has a Google Cloud project for Play's service account.
- **Reads are audited too.** Profile opens, investigation scope reads, exports and revenue report downloads are
  `access.*` actions.
- Retention: proposed 7 years for privileged actions, 2 years for read access entries (FD-16). Audit rows survive
  account deletion: the target user id stays as a bare uuid, the email is never stored in the audit log, and
  `before`/`after` payloads hold ids and plan fields, never ledger contents.

## 7. Secrets and credentials

| Secret | Where | Scope / rotation |
|---|---|---|
| `mc_app` DB password | Vercel sensitive env (Mission Control project, Production only) | Rotate quarterly; Preview deployments use lucento-test |
| Supabase secret key (Auth admin) | Vercel sensitive env; used only in `server/auth-admin.ts` | A separate key from the website's (new secret keys can be issued and revoked independently) |
| Ops project keys | Vercel env | Publishable key may reach the browser (admin auth only) |
| Supabase Management PAT | Vercel sensitive env | Account-wide scope: use it only for read endpoints (logs, advisors, usage); rotate every 90 days; consider a dedicated Supabase account that is a read-only org member |
| Play service account JSON | Vercel sensitive env | A **new** service account for Mission Control with View financial data + View app information + Reply to reviews; no "Manage orders" until refunds-from-console is a decided feature |
| Vercel token | Vercel sensitive env | Team-scoped, 90-day expiry, used only by read code paths |
| Resend keys | Vercel sensitive env | One sending key restricted to `send.lucentoapp.com` (transactional), one to the marketing subdomain (later), one full-access key for reading logs, never in sending code |
| Resend webhook secret | Vercel env | Verify every webhook signature (Svix) |
| Gemini key | Vercel env | Separate key and project quota from the customer-facing proxy so an internal bug can't drain customers' AI capacity |
| GCS audit bucket writer | Vercel env | Write-only (object creator) |

Rules: nothing secret in `NEXT_PUBLIC_*` or the browser bundle; no secrets in logs (a redaction wrapper on the
logger); a secrets inventory page in Mission Control showing *names and last-rotated dates*, never values.

## 8. Destructive and sensitive action controls

| Action | Controls |
|---|---|
| Grant ≤ 90 days | Reason code + note; preview of the resulting entitlement; confirm |
| Grant > 90 days or Lifetime | Above + step-up + typed confirmation (customer email's local part) |
| Revoke a grant | Reason; shows that Play entitlements stay untouched and lists what the customer keeps |
| Suspend | Step-up; reason; explains the offline-first effect (local app keeps working; sync, AI, Shared and purchase restore stop); optional customer email |
| Delete account | Step-up; typed confirmation; shows the cascade list and the Play subscription state (deletion doesn't cancel Play; offer the customer link); **24-hour cool-off**, cancellable, then runs `mc.delete_account` (same cascade as `delete_my_account`); email receipt to the customer |
| Export customer data | Step-up; produces a JSON + CSV archive in a private bucket; a link valid 24 hours; delivered only to the account's own verified email |
| Campaign send | Preview + test send to self within the last 60 minutes + audience snapshot + typed count ("SEND 412") + 10-minute cancellable queue |
| Bulk operations (≥ 10 targets) | Always an approval request; dry run first showing every target; hard cap of 500 per run |
| Admin management | Owner only; step-up; new admins start disabled until MFA enrolment |

Rate limits: per admin per hour (for example 30 profile opens, 10 grants, 3 exports), enforced in `mc` functions via a
counter table, with a 429-style refusal recorded in the audit log. Anomalies (an admin opening 50 profiles in 10
minutes) raise an alert to the Owner.

## 9. Break-glass

For a legal order, a security incident (account takeover in progress) or data corruption harming the customer, when
consent isn't obtainable.

1. Owner only, step-up, a reason category (legal, security, integrity) plus a written justification.
2. A 60-minute read-only session (Assist needs a second break-glass with its own justification).
3. Immediate alert to every Owner, and a flagged entry pinned on the Audit page until reviewed.
4. The customer is told within 72 hours ("On 9 Nov Lucento accessed your records to … "), unless the law forbids it.
5. A monthly review of every break-glass use is part of the governance checklist.

## 10. Web application security

- Next.js server components and route handlers on the Node runtime. No customer data in client-side caches beyond the
  current view; `Cache-Control: no-store` on every data response.
- Strict CSP (no third-party scripts, nonces), `frame-ancestors 'none'`, HSTS with preload, Referrer-Policy
  `no-referrer`, a Permissions-Policy denying device APIs.
- CSRF: SameSite=Strict cookies plus an origin check on every mutating request.
- Input validation with schemas at the edge of each action; database functions validate again.
- Dependency policy: a pinned lockfile, Renovate with manual merge, `npm audit` in CI, no analytics or chat widgets.
- Preview deployments: Vercel Deployment Protection on, always pointed at `lucento-test`, never at production.

## 11. Backup and recovery

Mission Control adds the `mc` schema to production, so production backups now also protect the audit log, tickets and
grants. Today production is on the Supabase Free plan, which this design treats as a launch blocker (FD-8):

- Supabase Pro daily backups (7 days), and the PITR add-on once revenue justifies it.
- A weekly logical dump (`pg_dump` of `public` + `mc`, encrypted with age) to the locked GCS bucket, restored monthly
  into a scratch project as a test. Mission Control shows the last successful restore test date on the Operations
  page.
- An audit log export (§6) independent of database backups.
- Runbooks: restore the database; rotate every secret; disable Mission Control (a kill switch env var that makes every
  route return 503 and revokes `mc_app`'s login).

## 12. Account deletion and retention

- Admin-initiated deletion reuses the exact cascade of `delete_my_account` (a shared private function so the two can't
  drift), runs after the cool-off, and records `account.delete` in the audit log with the user id only.
- Retained after deletion: audit entries (ids, no contents); ticket threads for a period set by FD-16, with the email
  replaced by a deletion marker; Google Play's own records; backups until they age out (7 days on Supabase Pro, plus
  weekly dumps until their retention ends). The public deletion page must state those periods. It currently says
  "routine database backups, which expire within 30 days", which must be checked against the real plan.
- Email suppression: deleting an account removes it from Resend contacts and marketing lists, but keeps a hashed
  suppression entry if the person unsubscribed or complained, so a re-import can't email them.
