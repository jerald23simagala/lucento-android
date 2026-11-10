# Lucento — Owner actions required before the public release

**Date:** 2026-11-10 · Companion to [REMEDIATION_REPORT.md](REMEDIATION_REPORT.md) and
[RELEASE_READINESS_CHECKLIST.md](RELEASE_READINESS_CHECKLIST.md).

These steps need your consoles, your production projects or a real payment, so the engineering work couldn't do them.

> **Owner decisions, 2026-11-10:** D-1 lenient Partner rule kept (accepted revenue leakage) · §1 Gemini stays on the free
> tier at launch, disclosed in the policy and every AI consent (accepted risk; paid tier once there is a user base) ·
> §2 Confirm email is ON (owner, and the catalog shows every account confirmed after sign-up, none auto-confirmed) ·
> §6 production stays on Supabase Free for now · §8 test purchases allowed during internal testing · §13 approved: grants
> added for jerald23simagala@gmail.com and rachelretas20@gmail.com (12 months, done 2026-11-10); the migrations are
> applied by pasting `docs/security/PRODUCTION_APPLY_2026-11-10.sql` (the MCP calls were declined twice).

**Done 2026-11-10:** §13 on production (owner pasted the file) and §14 (all checks pass, see
RELEASE_READINESS_CHECKLIST A1). lucento-test still needs the same file pasted once.

**Not done yet unless the boxes above say so.** Each section says what to do, click by click, and what evidence closes it. Put screenshots
in `docs/security/evidence/` (create the folder) with the file name given. Tick the checklist only with that evidence.

**Order matters for three of them:** §13 (production migrations) → then deploy the website → then upload the app build.
§9–§11 (real purchases) come after all three.

| § | Action | Blocks release | Time |
|---|---|---|---|
| 1 | Gemini key on the paid tier (F-05) | **Yes** | 5 min |
| 2 | Supabase "Confirm email" ON (F-06) | **Yes** | 5 min |
| 3 | Leaked-password protection ON (F-06) | Recommended | 2 min |
| 4 | Redirect allow-list (F-06) | Recommended | 3 min |
| 5 | Supabase usage alerts (F-01) | **Yes** (with §6) | 5 min |
| 6 | Supabase Free vs paid plan decision (F-01) | **Yes** (a decision) | — |
| 7 | Vercel Firewall rate limits (F-11) | Recommended | 10 min |
| 8 | Google Play license testers (F-12) | Before §9 | 5 min |
| 9 | Internal-track purchase verification (LP-K12) | **Yes** for paid launch | 30 min |
| 10 | Real purchase acknowledgement | **Yes** for paid launch | with §9 |
| 11 | Refund and RTDN verification (F-02) | **Yes** for paid launch | 20 min |
| 12 | Pub/Sub OIDC push authentication (F-10) | No (hardening) | 15 min |
| 13 | Apply the four new migrations (test, then production) | **Yes** | 30 min |
| 14 | Production catalog verification | **Yes** | 10 min (Claude can run it) |

---

## 1. Gemini key on Google's paid tier (F-05)

On the free tier Google may use prompts — including the financial summaries Ask Lucento sends — to improve its products.

1. Open https://aistudio.google.com/apikey and sign in with the Google account that created Lucento's key.
2. Find the key Vercel uses (Vercel → lucento-website → Settings → Environment Variables → the Gemini key's last 4
   characters match). Note the **Project** name next to it in AI Studio.
3. Open https://console.cloud.google.com/billing/linkedaccount and choose that project in the project picker at the top.
4. The page must show a **billing account linked**. If it says "This project has no billing account", click **Link a
   billing account**, choose or create one, and confirm.
5. Recommended: **Billing → Budgets & alerts → Create budget**, a monthly amount you're comfortable with, alerts at 50 %
   and 90 % to your email.

**Evidence:** screenshot of step 4 → `evidence/gemini-billing.png`. Then add one line to `docs/ASK.md`:
"Gemini key on the paid tier since <date> (billing account linked)".

## 2. Supabase "Confirm email" ON (F-06)

Shared invitations are accepted by whoever holds a **confirmed** matching email. With confirmation off, anyone could
register an invitee's address and join their space.

1. Open https://supabase.com/dashboard → project **lucento** (`hmtcuttadbgbldvzsfhz`).
2. Left menu **Authentication → Sign In / Providers** (on some dashboards: **Providers → Email**).
3. **Confirm email**: must be **ON**. **Secure email change**: **ON**. **Minimum password length**: 8 or more.
4. Click **Save** if you changed anything.
5. Repeat on **lucento-test** so the two projects behave the same.

**Evidence:** screenshot of the Email settings → `evidence/supabase-confirm-email.png`.

## 3. Leaked-password protection ON (F-06)

The app checks passwords against Pwned Passwords itself, but a direct API sign-up skips the app.

1. Same project → **Authentication → Attack Protection** (older dashboards: **Providers → Email → Password security**).
2. Turn **Prevent use of leaked passwords** ON → **Save**. (It needs the Supabase Pro plan on some accounts; if the
   switch is greyed out, note that and go on — the app's own check still covers the app's sign-up.)

**Evidence:** screenshot → `evidence/supabase-leaked-passwords.png`, or a note that the plan doesn't offer it. Then ask
Claude to re-run the security advisor: `auth_leaked_password_protection` should be gone.

## 4. Redirect allow-list (F-06)

1. Same project → **Authentication → URL Configuration**.
2. **Site URL** = `https://www.lucentoapp.com`.
3. **Redirect URLs**: only `https://www.lucentoapp.com/...` addresses (for example `https://www.lucentoapp.com/auth/confirmed`).
   Delete any `localhost`, `127.0.0.1` or `*.vercel.app` entry.

**Evidence:** screenshot → `evidence/supabase-redirects.png`.

## 5. Supabase usage alerts (F-01)

The database limits are now enforced (row ceilings, Cloud gate), but you should still hear about growth before the Free
plan's 500 MB fills.

1. https://supabase.com/dashboard → your **organization** → **Usage**: note the database size today.
2. **Organization → Billing → Spend cap / Usage alerts** (the wording moves): turn on email alerts for database size.
   On the Free plan, if there is no alert setting, put a weekly calendar reminder to open **Usage** instead.
3. Read-only runbook if the project ever goes read-only: Supabase emails you; sync pauses (each phone keeps its data and
   its unsynced changes); upgrade the plan or ask Claude to find the largest ledgers with a read-only query
   (`select user_id, table_name, row_count from lucento_private.ledger_row_counts order by row_count desc limit 20`).

**Evidence:** screenshot of the alert setting (or the reminder) → `evidence/supabase-usage-alerts.png`.

## 6. Supabase Free or paid plan — a decision (F-01)

Free: 500 MB database, the project pauses after a week without activity, no daily backups, read-only when full. Pro:
8 GB, daily backups, a spend cap, no pausing. With the new row ceilings one account can no longer fill the database, but a
launch with real users and money records usually wants backups.

**Decide and record it** in `docs/handover/NEXT_TASKS.md` with the date: "Production stays on Free with alerts (§5)" or
"Production moved to Pro on <date> with spend cap on".

## 7. Vercel Firewall rate limits (F-11)

The database now limits each account (purchase checks 60/day, AI by plan). An IP-level limit stops floods of requests
that aren't signed in at all, before they cost function time.

1. https://vercel.com → **lucento-website** → **Firewall** → **Configure** (or **Rules**) → **New Rule**.
2. Name `API rate limit`. **If** `Request Path` `starts with` `/api/`.
3. **Then** `Rate Limit`: **Fixed window**, **60 requests** per **60 seconds**, key **IP Address**, action **Deny**
   (answers 429). 60/min is far above what one phone sends, so households and offices on one network aren't affected.
4. Click **Save**, then **Review Changes → Publish**.
5. Optional second rule, `starts with /api/billing/verify`, 20 per 60 s.
6. Leave **Bot Protection** / **Attack Challenge Mode** off for `/api/billing/rtdn` and `/api/billing/voided`: Google
   and Vercel Cron must reach them without a browser challenge.

**Evidence:** screenshot of the published rules → `evidence/vercel-firewall.png`.

## 8. Google Play license testers (F-12)

License testers buy without being charged. The server now refuses test purchases unless the project allows them.

1. https://play.google.com/console → **Settings** (left, bottom) → **License testing**.
2. The list should hold **only** your own test Google accounts. Remove anyone else. **Save**.
3. While you test on the internal track (§9–§11), allow test purchases on production. Supabase → lucento → **SQL Editor**:
   ```sql
   update lucento_private.plan_limits set value_int = 1 where key = 'allow_test_purchases';
   ```
   (On **lucento-test** run the same line once and leave it at 1.)
4. **When testing is over, before the public release** (about 3 minutes):
   1. Open https://supabase.com/dashboard and sign in.
   2. Click the project **lucento** (not lucento-test).
   3. In the left menu click **SQL Editor** (the `>_` icon), then **+ New query** at the top left.
   4. Paste the two statements below into the empty editor.
   5. Click **Run** (bottom right), or press Ctrl + Enter.
   6. Check the results panel: the first statement says `Success. 1 row affected`; the second says how many test plans it
      ended (any number, 0 included, is fine).
   7. Paste and run this to confirm: `select value_int from lucento_private.plan_limits where key = 'allow_test_purchases';`
      It must show **0**. Take a screenshot of it.
   8. Tell Claude "test purchases are off" so the checklist can be ticked.
   ```sql
   update lucento_private.plan_limits set value_int = 0 where key = 'allow_test_purchases';
   -- End the plans test purchases granted (marked test_purchase = true):
   update public.entitlements set status = 'EXPIRED', expires_at = now() where test_purchase and status <> 'REVOKED';
   ```

**Evidence:** screenshot of the tester list → `evidence/play-license-testers.png`; the two SQL results pasted into
`evidence/test-purchases-closed.txt`.

## 9. Internal-track purchase verification (LP-K12)

Prerequisites: §13 applied to production, the website deployed from `security/release-hardening`, the release build
from `security/release-remediation` uploaded to **Internal testing**, `docs/monetization/PLAY_CONSOLE_SETUP.md` done.

1. On your phone, install from the internal-testing link; sign in to Lucento with your own account.
2. Lucento Pro page → buy **Lucento Pro monthly** (license tester: no charge).
3. Within a minute the Pro page shows Pro. Ask Claude to run (read-only):
   `select source, status, test_purchase, expires_at from public.entitlements where user_id = '<your user id>';`
   Expected: a `PLAY_SUBSCRIPTION` row, `ACTIVE`, `test_purchase = true`.
4. Repeat for **Lifetime** (expected two rows: `PLAY_LIFETIME` forever, `PLAY_LIFETIME_SERVICES` for 365 days).
5. **F-03 check:** on a second phone signed in to the *same Google Play account* but a *different* Lucento account, open
   the Pro page. Expected: it doesn't take your purchase; the note says it was made with a different Lucento account.
   Ask Claude to confirm the second account has **no** entitlement rows.

**Evidence:** screenshots of the Pro page on both phones → `evidence/purchase-1.png`, `evidence/purchase-other-account.png`;
the query results → `evidence/purchase-rows.txt`.

## 10. Real purchase acknowledgement

1. Play Console → **Order management**: find the §9 orders. Each should show **Acknowledged** within minutes (Play refunds
   unacknowledged purchases after 3 days).
2. Optionally make one real purchase with a real card from a non-tester account to see a real order end to end.

**Evidence:** screenshot of the order list showing "Acknowledged" → `evidence/purchase-acknowledged.png`.

## 11. Refund and RTDN verification (F-02)

1. Play Console → **Order management** → the Lifetime order → **Refund** → tick **Remove entitlement** → confirm.
2. Within a few minutes the RTDN push arrives: Vercel → lucento-website → **Logs**, filter `/api/billing/rtdn` → a 200.
3. Ask Claude to run (read-only): the entitlement rows show `REVOKED`, and
   `select count(*) from lucento_private.revoked_purchases;` is at least 1.
4. Open the Pro page on the phone and tap **Restore purchases**: it should show the plan as ended. Write down whether
   Google Play still lists the refunded purchase on the phone (Play Store → profile → **Payments & subscriptions →
   Budget & history**) in `docs/monetization/PLAY_CONSOLE_SETUP.md`. This settles how serious F-02 would have been;
   the fix holds either way.
5. The next morning, Vercel → **Cron Jobs** → `/api/billing/voided` → last run 200, `seen` ≥ 1.

**Evidence:** `evidence/refund-rtdn.png` (the 200 log line), `evidence/refund-rows.txt` (query results).

## 12. Pub/Sub OIDC push authentication (F-10) — hardening, can follow the release

The push endpoint now verifies Google-signed tokens once two variables are set; the query-string token keeps working
until you remove it.

1. https://console.cloud.google.com → the Play project → **Pub/Sub → Subscriptions** → the Lucento push subscription →
   **Edit**.
2. Tick **Enable authentication**. **Service account**: create or choose one, e.g. `rtdn-push@<project>.iam.gserviceaccount.com`
   (it needs no roles). **Audience**: `https://www.lucentoapp.com/api/billing/rtdn`. **Update**.
3. Vercel → lucento-website → **Settings → Environment Variables** (Production):
   `LUCENTO_RTDN_OIDC_AUDIENCE` = `https://www.lucentoapp.com/api/billing/rtdn`,
   `LUCENTO_RTDN_OIDC_SERVICE_ACCOUNT` = the service account email. **Redeploy**.
4. Play Console → **Monetization setup → Real-time developer notifications → Send test notification**. Vercel Logs:
   `/api/billing/rtdn` answers 200 (a refusal logs `rtdn: push authentication refused (<reason>)`).
5. Only after a week of 200s: remove `?token=...` from the subscription's endpoint URL, delete `LUCENTO_RTDN_PUSH_TOKEN`
   from Vercel, redeploy, send another test notification (200 expected).

**Evidence:** `evidence/rtdn-oidc.png` (the 200 after step 4).

## 13. Apply the four new migrations — lucento-test first, then production

Files, in this order: `20261111090000_cloud_write_gate_and_quotas.sql`, `20261111100000_play_purchase_integrity.sql`,
`20261111110000_seed_ids_and_default_privileges.sql`, `20261111120000_request_quotas.sql`. All passed 258/258 offline
checks under both privilege modes and the concurrency races on Postgres 17.

**What changes for people:** an account without the Cloud plan can no longer upload (it still downloads, and the app
pauses uploads with a plain message, keeping every change on the phone). Anyone who syncs today **without** a plan row
would see uploads pause. So first:

1. **Pre-check (read-only), on each project.** *Production done 2026-11-10: 3 accounts sync, 2 had no plan → granted.* Ask Claude to run, or paste into the SQL Editor:
   ```sql
   with syncing as (
     select user_id from public.wallets union select user_id from public.transactions
     union select user_id from public.categories
   )
   select count(*) as accounts_that_sync,
          count(*) filter (where not lucento_private.has_capability(user_id, 'CLOUD')) as without_cloud_plan
   from syncing;
   ```
   If `without_cloud_plan` > 0, decide per account: give it a GRANT row (as you did for test accounts, LP-K5), or accept
   that its uploads pause. Ask Claude for the list of those account emails if you need it.
2. **lucento-test:** ask Claude to apply the four files with the Supabase MCP `apply_migration` (it records them in the
   history) — that is your authorisation for that project. Then set `allow_test_purchases` to 1 there (§8.3).
   Test a debug build: sync, a purchase on the test store, Shared invite.
3. **Production:** Supabase → project **lucento** → **SQL Editor** → **New query** → paste the whole of
   `docs/security/PRODUCTION_APPLY_2026-11-10.sql` → **Run**. It runs all four migrations in one transaction (all or
   nothing), records them under the repository versions, and turns test purchases on (§8). The last result lists four
   versions. Then tell Claude "applied" for §14. (Dry-run on a copy of today's production schema: applies cleanly.)
4. **Then** deploy the website (`security/release-hardening` merged to `main`): it sends the new `p_test_purchase`
   argument, which the old database function doesn't have, so the database must go first.
5. **Then** upload the release build.

**Evidence:** `list_migrations` output showing the four versions → `evidence/migrations-applied.txt`.

## 14. Production catalog verification

After §13 on production, ask Claude to run its read-only checks (no user rows):

- `pg_policies`: every INSERT/UPDATE policy on the eight synced tables mentions `caller_has_cloud` or
  `cloud_partner_spaces`; SELECT and DELETE policies unchanged.
- `usage_events`: no INSERT grant for `anon`/`authenticated`; `record_usage_event` executable by both.
- `pg_default_acl` for `postgres` in `public`: no `anon`/`authenticated` entry.
- Triggers `trg_*_row_quota`, `trg_*_row_release`, `trg_*_write_batch` on the seven tables.
- Seed-id squats left from before F-07 (should be 0):
  `select count(*) from public.categories where id like 'seed-%' and not starts_with(id, 'seed-' || user_id::text || '-');`
- The Supabase security advisor: no new warnings.
- Live: `curl -i -X OPTIONS https://www.lucentoapp.com/api/ask` → `204` with `Allow: POST, OPTIONS` (F-08), and
  `curl -sI https://www.lucentoapp.com/` shows `content-security-policy`, `x-frame-options: DENY`, `x-content-type-options`
  (F-09).

**Evidence:** the outputs → `evidence/production-catalog-<date>.txt`.

---

## Still yours to decide (not blocking)

- **Partners after the Owner's plan lapses** (`partner_sync_after_owner_lapse`, default 1 = the approved lenient rule in
  docs/MONETIZATION.md §6.4). Set it to 0 to make a Partner's uploads follow the Owner's plan:
  `update lucento_private.plan_limits set value_int = 0 where key = 'partner_sync_after_owner_lapse';`
- **Row ceilings** (`max_rows_*` in `lucento_private.plan_limits`, e.g. 200,000 transactions per account) can be raised
  for one heavy user only by raising them for all; tell Claude if a real ledger approaches one.
