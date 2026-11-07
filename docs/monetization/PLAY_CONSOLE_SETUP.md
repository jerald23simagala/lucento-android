# Lucento Pro — Google Play setup (owner checklist)

Written with Phase 4 (2026-11-07). The code is ready: the app's Play Billing client (`data/billing/PlayPurchaseSource`),
the server functions (`supabase/migrations/20261108090000_play_purchases.sql`) and the website endpoints
(`lucento-website` branch `feature/play-billing-verify`: `api/billing/verify`, `rtdn`, `voided`). None of it can take a
real payment until the steps below are done, and they can only be done in Google's consoles, by the account owner.

Until then, a release build shows "Lucento Pro isn't on sale yet" (no licence key, no products). Test builds use the
plan simulator and its **Test store** switch on the Lucento Pro page.

## The order matters

1. **Target SDK 36.** Play has refused uploads below API 36 since 31 August 2026. The bump is on its own branch
   (`chore/target-sdk-36`, see `docs/handover/NEXT_TASKS.md`); merge it before the first upload.
2. **Release signing (CS-20).** Create an upload key once and keep it outside the repository:
   ```sh
   keytool -genkeypair -v -keystore lucento-upload.jks -alias lucento-upload -keyalg RSA -keysize 4096 -validity 10000
   ```
   Add these GitHub repository **secrets**: `LUCENTO_KEYSTORE_BASE64` (`base64 -w0 lucento-upload.jks`),
   `LUCENTO_KEYSTORE_PASSWORD`, `LUCENTO_KEY_ALIAS`, `LUCENTO_KEY_PASSWORD`. CI then signs the release AAB. Enrol in
   **Play App Signing** when Play Console asks: Google keeps the app signing key, and you keep only the upload key.
3. **Create the app** in Play Console (package `com.lucento.app`). Upload the first signed AAB to **Internal testing**.
   Play only lets you create products once a build that uses billing is uploaded.
4. **Products** (Monetize with Play › Products). Ids must match exactly, because the app and the server check them.

   | Product id | Type | Set up |
   |---|---|---|
   | `lucento_pro` | Subscription | Base plan `monthly`: auto-renewing, 1 month, ₱149. Base plan `annual`: auto-renewing, 1 year, ₱1,190, with an offer (id e.g. `trial`) of a **7-day free trial**, eligibility "new customer acquisition: never had this subscription" |
   | `lucento_pro_lifetime` | One-time product | One purchase option, ₱3,490. Founder price: a discounted offer at ₱2,990 for 60 days. The app shows the lowest offer Play lists |
   | `lucento_cloud` | Subscription | Base plans `monthly` (₱59) and `annual` (₱490). The app offers it only to Lifetime owners whose included year has ended |

   Let Play convert the PHP prices for other countries, or set them by hand. The app shows Play's own price strings,
   so no price is typed into the app.
5. **Licence key.** In Monetize with Play › Monetization setup › Licensing, copy the Base64 RSA public key into the GitHub
   repository **variable** `LUCENTO_PLAY_LICENSE_KEY`, and into `local.properties` for local release builds. It is
   public by design. The phone checks every purchase's signature with it, and without it a release doesn't bill.
6. **Service account** for the server:
   - In Google Cloud (the project linked to Play Console), enable the *Google Play Android Developer API*.
   - Create a service account and a JSON key for it.
   - In Play Console › Users and permissions, invite the service account's email with app permissions **View
     financial data** and **Manage orders and subscriptions**.
   - Paste the whole JSON key, on one line, into Vercel env `LUCENTO_PLAY_SERVICE_ACCOUNT_JSON` (Production).
7. **Supabase service-role key** of the **same** project the website's `LUCENTO_SUPABASE_URL` points to. Put it in Vercel
   env `LUCENTO_SUPABASE_SERVICE_ROLE_KEY` (Production). It is the only key allowed to call `apply_play_purchase` and
   `revoke_play_purchase`. It never goes into the APK, GitHub or the repository.
8. **Real-time developer notifications.**
   - In Google Cloud Pub/Sub, create a topic (e.g. `play-billing`) and give
     `google-play-developer-notifications@system.gserviceaccount.com` the *Pub/Sub Publisher* role on it.
   - Add a **push** subscription whose endpoint is `https://www.lucentoapp.com/api/billing/rtdn?token=<secret>`.
     `<secret>` is any long random string, also stored in Vercel env `LUCENTO_RTDN_PUSH_TOKEN`.
   - In Play Console › Monetization setup › Real-time developer notifications, enter the topic and choose **all
     notification types**, including voided purchases.
   - Press *Send test notification*. The endpoint answers 200 ("ignored").
9. **Daily refund check.** Set Vercel env `CRON_SECRET` to any long random string. `vercel.json` runs
   `/api/billing/voided` once a day on the production deployment.
10. **License testers.** In Play Console › Settings › License testing, add the testers' Google accounts. They buy with
    test cards, and subscriptions renew on Play's accelerated test schedule. Testers install from the internal-testing
    opt-in link.

## Going live (the gated release)

The server pieces have to land in this order, or AI answers and purchases fail:

1. **Production Supabase:** apply `20261107090000_entitlements.sql`, then `20261108090000_play_purchases.sql`, and the
   founders' and testers' `GRANT` rows by uuid (`SERVER_CONTRACT.md` §7). Shared and multicurrency must already be
   there (OT-9, OT-12).
2. **Website:** merge `feature/plan-aware-ai` and `feature/play-billing-verify` and deploy. Deploying before step 1 makes
   every AI call answer 502.
3. **App:** a release from `main` with Phases 2–4 to internal testing. Then run the device matrix
   (`MONETIZATION.md` §10) with license testers, and only then a production rollout.

## Checking it works (internal testing)

| Step | Expect |
|---|---|
| Open Lucento Pro as a free user | Play's prices and the trial on yearly Pro, with "Buy Lifetime" |
| Buy Lifetime with a test card | "Pro Lifetime is yours" on the page; Pro features unlock at once, offline afterwards |
| Supabase `public.entitlements` | Two rows for the signed-in account (`PLAY_LIFETIME`, `PLAY_LIFETIME_SERVICES`) |
| Play Console › Order management | The order is acknowledged; Play refunds an unacknowledged order after 3 days |
| Test card "slow, declines later" | "Waiting for Google Play to confirm your payment"; nothing unlocked until it completes |
| Refund the order with *revoke* | Within minutes (notification) or a day (daily check) both rows read `REVOKED`; the app drops to Free at its next check; nothing is deleted |
| Reinstall, same Google account | The Lifetime is back before signing in to Lucento |
