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
| Play app signing key **in use** (Play Console marks it "Quantum-ready (beta)") | `3F:DC:90:EE:BB:AE:DC:FD:2E:9C:64:07:13:44:80:82:3D:0A:86:66` | **Missing on 2026-11-21**: OT-16 adds it |
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

Follow-ups are in `docs/handover/KNOWN_ISSUES.md` (AC-1 to AC-4).
