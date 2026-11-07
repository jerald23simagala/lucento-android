# Lucento Pro — server contract (Supabase + Vercel)

Status: **Phase 1–4 written.** The tables and the AI allowance are `supabase/migrations/20261107090000_entitlements.sql`
(applied to `lucento-test` 2026-11-06; `tools/sql-verify/entitlements_suite.sql`, 38 checks). Purchases are written by
`supabase/migrations/20261108090000_play_purchases.sql` (Phase 4, 2026-11-07; `play_purchases_suite.sql`, 30 checks; not
applied anywhere). The Vercel endpoints of §3–§5 are on `lucento-website` branch `feature/play-billing-verify` (Phase 4,
not deployed). The Google Play side is the owner's checklist in [PLAY_CONSOLE_SETUP.md](PLAY_CONSOLE_SETUP.md).
Everything here follows [../MONETIZATION.md](../MONETIZATION.md) §5.4–§5.6.

## 1. What lives where

| Piece | Where | Holds a secret? |
|---|---|---|
| `public.entitlements`, `public.purchase_links`, `lucento_private.plan_limits`, `has_capability`, plan-aware `max_partners`, `consume_ai_request` | Supabase (the migration) | No |
| `public.apply_play_purchase`, `public.revoke_play_purchase` (service role only) | Supabase (Phase 4 migration) | No |
| `api/billing/verify.ts`, `api/billing/rtdn.ts`, `api/billing/voided.ts` (shared code in `src/server/billing.ts`, `play-api.ts`, `play-state.ts`) | `lucento-website` (Vercel) | Yes: `LUCENTO_SUPABASE_SERVICE_ROLE_KEY`, `LUCENTO_PLAY_SERVICE_ACCOUNT_JSON`, `LUCENTO_RTDN_PUSH_TOKEN`, `CRON_SECRET` — Vercel env only, never the APK (CLAUDE.md) |
| `api/ask.ts`, `api/interpret.ts` | `lucento-website` (existing) | Switch from `consume_ai_quota()` to `consume_ai_request()` and return its `remaining` / `limited_by` |

The app never talks to the Play Developer API and never holds a service-role key. It reads `public.entitlements` under the
person's own JWT (RLS: own rows) and posts purchase tokens to `verify.ts` with that JWT.

## 2. Tables (as in the migration)

- **entitlements** — one row per `(user_id, source, product_id)`. `source` ∈ PLAY_SUBSCRIPTION · PLAY_LIFETIME ·
  PLAY_LIFETIME_SERVICES · PLAY_CLOUD_ADDON · GRANT. `status` is Play's lifecycle vocabulary. `grants` ⊆ {LOCAL_PRO, CLOUD,
  AI_PRO, SHARED}. `expires_at` null = never (only PLAY_LIFETIME; a GRANT must expire). Clients: `select` own only.
- **purchase_links** — `purchase_token_hash` (sha256 hex) → `user_id`, `obfuscated_account_id`, `relinked_count`. No client
  access at all.
- **plan_limits** — `ai_free_monthly` 10 · `ai_pro_daily` 50 · `ai_pro_monthly_ceiling` 400 · `max_partners_pro` 1. Private
  schema, read by the functions only; changing a quota is one `update`, no deploy.

## 3. `POST /api/billing/verify`

Request (app, `Authorization: Bearer <Supabase JWT>`):

```json
{ "purchaseToken": "…", "productId": "lucento_pro_lifetime", "packageName": "com.lucento.app" }
```

Steps:

1. Validate the JWT with the Supabase URL + publishable key (as `ask.ts` does) → `user_id`.
2. Call the Play Developer API with the service account: subscriptions → `purchases.subscriptionsv2.get`; one-time →
   `purchases.productsv2.getproductpurchasev2` (decided in Phase 4: it is the current resource, and every Lifetime token
   is new, so there is no legacy fallback). Its purchase time is `purchaseCompletionTime` (unset while pending). Play's
   answer is mapped by `src/server/play-state.ts` (unit-tested); a product id that doesn't match the request is `400`.
3. **Linking rule** on `purchase_links` (`hash = sha256(purchaseToken)`) — steps 3 and 4 are one call to
   `public.apply_play_purchase` (service role only, one transaction, so two verifications of the same purchase can't
   interleave):
   - no row → insert for `user_id`;
   - row for `user_id` → fine;
   - row for another account → allow a re-link only if the purchase's `obfuscatedExternalAccountId == sha256(user_id)`
     **and** `linked_at` is older than 30 days; then move the row (`relinked_count + 1`) and delete the previous account's
     entitlement rows for that `(source, product_id)`; otherwise `409 linked_elsewhere`;
   - a third move (`relinked_count >= 2`) → `409 review_required` (support path).
   A refunded purchase (`REVOKED`) stays refunded whatever a later verification says, and a stale purchase's ending never
   overwrites a newer live purchase of the same product.
4. Upsert `entitlements` rows (`verified_at = now()`):
   - `lucento_pro` → PLAY_SUBSCRIPTION, all four grants, `status` from `subscriptionState` + `autoRenewing`, `expires_at`
     from `lineItems[].expiryTime`, `base_plan_id` from `offerDetails.basePlanId`.
   - `lucento_pro_lifetime` → PLAY_LIFETIME (LOCAL_PRO, `expires_at` null) **and** PLAY_LIFETIME_SERVICES (CLOUD, AI_PRO,
     SHARED, `expires_at = purchaseTime + 365 days`; `starts_at = purchaseTime`). A purchase in `PENDING` → both rows
     `PENDING`.
   - `lucento_cloud` → PLAY_CLOUD_ADDON (CLOUD, AI_PRO, SHARED), like the subscription.
5. Acknowledge is the **app's** job (Billing Library); the endpoint reports `acknowledgementState` so the app can retry.
6. Response: the caller's rows (same shape the app reads from the table) so the app can apply them at once.

Errors: `401 sign_in_required`, `400 bad_request`, `404 purchase_not_found` (Play), `409 linked_elsewhere`,
`409 review_required`, `502 play_unavailable` / `store_unavailable` (the app keeps its Play-local reading and asks again
at its next refresh), `503 not_configured`. The app (`ProxyPurchaseVerifier`) posts to `<site>/api/billing/verify`, derived
from the AI proxy's address, only in release builds and only when signed in; it acknowledges the purchase with Play
afterwards whatever the answer (`EntitlementManager`).

## 4. `POST /api/billing/rtdn`

Pub/Sub push from Play's Real-Time Developer Notifications (topic configured in Play Console). The push endpoint is
`/api/billing/rtdn?token=<LUCENTO_RTDN_PUSH_TOKEN>`; a push without the secret is `401`. Decode `message.data`, then
**re-verify** the purchase
token exactly as §3 step 2–4 (never trust the notification's own fields for state). Handles `subscriptionNotification`
(renewed, cancelled, in grace, on hold, paused, revoked, expired, restarted, price-change) and
`oneTimeProductNotification` (purchased, cancelled) plus `voidedPurchaseNotification` → `status = REVOKED`.
Idempotent: the same message twice yields the same rows. Always `200` once stored (or when there is nothing to do: a
purchase no account has verified yet, another app's message, a test notification), so Pub/Sub stops retrying; `502` when
Play or the database didn't answer, so it retries. A subscription notification doesn't name its product: Pro is tried,
then the Cloud add-on.

## 5. `GET /api/billing/voided` (cron, daily)

Vercel cron (`vercel.json`, daily at 03:17 UTC, `Authorization: Bearer <CRON_SECRET>`). Calls the Voided Purchases API
(`type=1`, subscriptions included) for the last 30 days and sets `REVOKED` on matching
`purchase_token_hash` rows that are not already revoked. The belt to RTDN's braces (a missed push never leaves a
refunded purchase active for more than a day).

## 6. The proxy's AI calls — **written 2026-11-06** (`lucento-website` branch `feature/plan-aware-ai`, not deployed)

`ask.ts` / `interpret.ts`: after auth, `POST /rest/v1/rpc/consume_ai_request` with the user's JWT (replaces
`consume_ai_quota`). Refuse with `429 { error: 'allowance', limited_by, remaining, plan }` when `allowed` is false.
`interpret.ts` additionally refuses `403 pro_required` when `plan` is `FREE` (Smarter reading is Pro). Return `remaining`
and `limited_by` on success so the app's "x of y left" line is a display of the server's decision. `AI_DAILY_LIMIT`
becomes unused (the limits are `plan_limits`); keep it as a last-resort fallback for one release.

## 7. Applying to `lucento-test` (owner operation) — migration and grants in one go

The grants live in `public.entitlements`, which the migration creates, so the migration must run **first** (running the
insert alone fails with `42P01: relation "public.entitlements" does not exist`). One paste in the SQL editor does both,
atomically — `docs/monetization/LUCENTO_TEST_APPLY.sql` is that paste with the migration already inlined (regenerated
from the migration, never edited by hand). The dashboard's "potential issues" dialog flags the two `drop … if exists`
guards and asks about RLS; both are expected, choose *Run and enable RLS*. In outline:

```sql
begin;
-- 1. the whole of supabase/migrations/20261107090000_entitlements.sql, pasted here verbatim
-- 2. every account that exists on lucento-test keeps what it could do before plans existed (a test project; on
--    production this would be the founders' and testers' accounts only, by uuid)
insert into public.entitlements (user_id, source, status, grants, expires_at, note)
select u.id, 'GRANT', 'ACTIVE', array['LOCAL_PRO', 'CLOUD', 'AI_PRO', 'SHARED'], now() + interval '180 days', 'lucento-test account'
from auth.users u
on conflict do nothing;
commit;

-- verify
select u.email, e.source, e.status, e.grants, e.expires_at
from public.entitlements e join auth.users u on u.id = e.user_id order by u.email;
select lucento_private.max_partners(id) from auth.users limit 3;   -- 1 for each granted account
```

For one account later (a new tester):

```sql
insert into public.entitlements (user_id, source, status, grants, expires_at, note)
values ('<uuid from auth.users>', 'GRANT', 'ACTIVE', array['LOCAL_PRO','CLOUD','AI_PRO','SHARED'], now() + interval '180 days', 'founder');
```

Why together: `max_partners` becomes plan-aware in the migration, so an Owner without SHARED can no longer send new
invitations (existing Partners stay — D-16). Production gets the migration only with the gated release, with grant rows
for the founders' and testers' accounts by uuid, never for every user.

## 8. App ↔ server reading

The app's `EntitlementRemote` (Phase 2) does `GET /rest/v1/entitlements?select=…` under the person's JWT, maps rows to
`Entitlement(verifiedBy = SERVER)`, and hands them to `EntitlementManager.refresh()`, which applies
`EntitlementMerge.reconcileServer`. Null (signed out, offline, 5xx) changes nothing.

## 9. Phase 4 on `lucento-test` (owner, only for a staging check)

Debug builds never bill (the `.debug` id), so `lucento-test` needs `20261108090000_play_purchases.sql` only if a staging
release build or a Vercel preview is pointed at it. Paste the file in the SQL editor (it is additive: two functions and
their grants), then check:

```sql
select p.proname, has_function_privilege('authenticated', p.oid, 'execute') as client, has_function_privilege('service_role', p.oid, 'execute') as server
from pg_proc p where p.pronamespace = 'public'::regnamespace and p.proname in ('apply_play_purchase', 'revoke_play_purchase', 'lucento_write_play_row');
-- expected: apply_play_purchase f t · revoke_play_purchase f t · lucento_write_play_row f (service role: t only under stock defaults)
```

Production gets it with the gated release, right after `20261107090000_entitlements.sql` (PLAY_CONSOLE_SETUP.md "Going
live").
