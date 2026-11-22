# Accounts and projects: who owns what

Audited read-only on 2026-11-21 in the owner's browser (a Claude-in-Chrome audit of Play Console and both Google
accounts' Cloud projects). Nothing was changed. Lucento's Google pieces are split across two Google accounts. That
works: each piece is linked to the app by identifiers (package name, signing fingerprints, a service account invited
into Play Console), not by which account owns it.

## The map

| Piece | Google account | Where | What it does |
|---|---|---|---|
| Play Console developer account "Lucento" | jerald23simagala@gmail.com (owner; contact address lucentoapp@gmail.com) | play.google.com/console | Publishes the app: releases, store listing, App content, products |
| Google sign-in | lucentoapp@gmail.com (jerald23simagala@gmail.com is an Owner too) | Google Cloud project **Lucento** (`lucento`) | Consent screen (**In production**, branding verified, no extra scopes), the web client Supabase uses, and an Android client per signing key |
| Firebase (Crashlytics) | lucentoapp@gmail.com | the same project **Lucento** | `google-services.json`; one Firebase service account, without keys |
| Gemini | jerald23simagala@gmail.com | Google Cloud project **Lucento AI** (`gen-lang-client-0799483675`) | One API key restricted to the Gemini API. Only Lucento's server proxy holds it, never the app (`app/build.gradle.kts`). No billing account yet: the paid tier is OT-31 |
| Purchase checks | jerald23simagala@gmail.com | project **Lucento AI**: service account `lucento-play-billing`, Play Developer API, Pub/Sub | Invited into Play Console with four Lucento permissions: view app information, app quality, financial data; manage orders and subscriptions. Its key (created 2026-11-12) lives only in Vercel's `LUCENTO_PLAY_SERVICE_ACCOUNT_JSON`. Working since 2026-11-13 (NEXT_TASKS OT-24) |
| Supabase | (Supabase account) | `lucento` (`hmtcuttadbgbldvzsfhz`) = production; `lucento-test` (`ogenvdopfgocjdkcrsny`) = test | Release builds use production, debug builds and Mission Control TEST use lucento-test. Both callback URLs are on the web client |

## Signing keys and Google sign-in

Google issues an ID token only to an app whose package and signing certificate SHA-1 are registered as an Android
client in the same project as the web client (project **Lucento**). Fingerprints are public (they ship in every APK):

| Certificate | SHA-1 | Android client in project Lucento |
|---|---|---|
| Play app signing key **in use** (Play Console marks it "Quantum-ready (beta)") | `3F:DC:90:EE:BB:AE:DC:FD:2E:9C:64:07:13:44:80:82:3D:0A:86:66` | "Lucento release (Play signing, new key)", added by the owner on 2026-11-21 (OT-16). Not yet proven by a Google sign-in on a copy installed from Play |
| Play app signing key, previous (first used 2026-11-07) | `37:CE:29:A8:6A:61:97:37:DC:4C:82:73:AA:93:F8:A8:3C:41:9E:CC` | "Lucento release (Play app signing)", 2026-11-10 |
| Upload key (CI signs the AAB with it) | `65:17:37:2B:3F:26:AC:68:D8:01:87:C9:EB:F4:72:4B:FF:F6:25:C1` | "Lucento upload key", 2026-11-09 |
| Debug keystore (this PC) | `49:AF:14:81:8D:53:F5:DF:68:42:03:3E:3C:1E:2E:DA:AA:AB:8C:75` | "Lucento Android Debug" (`com.lucento.app.debug`), and "Lucento Android" (`com.lucento.app`, a leftover: AC-2) |

**When Play's signing key changes** (Play Console › App integrity › App signing shows the key in use and earlier
ones), add an Android client for `com.lucento.app` with the new SHA-1 and keep the old clients: phones that still
get an earlier key need theirs. Until then **Continue with Google** fails (DEVELOPER_ERROR) on copies signed with
the new key.

The app uses no Android App Links and no Play Integrity, so neither the website (no `assetlinks.json`) nor Play
Console's Cloud-project link needs the signing keys.

## Not problems (checked)

- The yellow "configure the OAuth consent screen" banner in **Lucento AI**: nothing there signs users in, so it needs
  no consent screen.
- The Gemini key's "no application restriction": it is a server key, kept by Lucento's proxy and never in the app;
  the API restriction (Gemini API only) is the control that matters.
- Two Supabase projects on the web client: test and production, both in use.
- Data safety marks purchase history **optional**: correct, since nobody has to buy anything to use Lucento.
- `lucento_cloud` in Play's products: the app's entitlement code still recognises it (`Entitlement.kt`).

## Mission Control's own apps (auto-posting, OT-37)

Set up on 2026-11-21 for the TEST console's address (`lucento-mission-control.vercel.app`). Mission Control moved to
production (`mission.lucentoapp.com`, OT-28) the same evening, so auto-posting on production needs the production
return addresses added to all three and the accounts connected again (OT-37 waits for Marketing to restart). App ids
and client keys are public identifiers; the secrets live only in Vercel.

| App | Account | Where | Setup |
|---|---|---|---|
| Meta app **Lucento Mission Control** (App ID `1142971521993000`) | The Facebook account that manages the Lucento and KlaroPH Pages | developers.facebook.com | **Live**, no icon yet. Facebook Login for Business configuration "Mission Control" (user access token, the six permissions of `META_SCOPES`), so `META_LOGIN_CONFIG_ID` is required. Instagram through "API setup with Facebook login", which Meta now calls legacy (KNOWN_ISSUES MC-20) |
| Google Cloud project **Lucento Mission Control** | The Google account that owns Lucento's YouTube channel | console.cloud.google.com | YouTube Data API v3. Consent screen External, In production, unverified (no logo): Branding has lucentoapp.com links and the authorized domains `lucentoapp.com` and `lucento-mission-control.vercel.app` (vercel.app is a public suffix). Web client "Mission Control" |
| TikTok developer app **Lucento Mission Control** | Lucento's TikTok account (`lucentoapp`) | developers.tiktok.com | Individual, category Business. The main app is unsaved: TikTok demands a demo video, which belongs to its review. Sandbox **Mission Control** (saved with Apply changes): Login Kit with the callback, Content Posting API with Direct Post off, `user.info.basic` and `video.upload`, target user `lucentoapp`; its client key and secret are in Vercel |

## DNS records on lucentoapp.com (GoDaddy)

| Name | Type | What it is for |
|---|---|---|
| `lucentoapp.com` | TXT `google-site-verification=…` | Google Search Console |
| `lucentoapp.com` | TXT `tiktok-developers-site-verification=…` | TikTok's URL properties for the developer app's terms, privacy and web addresses (added 2026-11-21) |
| `_dmarc` | TXT | DMARC, reports to GoDaddy's address |
| `send.lucentoapp.com` | DKIM (`resend._domainkey.send`), SPF and MX (`send.send`) | Account emails through Resend (docs/EMAILS.md) |
| `mission` | CNAME to Vercel | Mission Control on production (OT-28, 2026-11-21) |

Checked from outside on 2026-11-21 after the TikTok record went in: the Google verification, DMARC and the Resend
records were unchanged.

Follow-ups are in `docs/handover/KNOWN_ISSUES.md` (AC-1 to AC-4, MC-20 to MC-22).
