# Google Play submission — what to enter in Play Console

The owner's checklist for publishing `com.lucento.app`. Drafted 2026-11-07, after the app was created in Play Console
and the signing secrets were added to GitHub. Lucento Pro's products, licence key, service account and notifications
are in [../monetization/PLAY_CONSOLE_SETUP.md](../monetization/PLAY_CONSOLE_SETUP.md). This page covers everything
else Play asks for.

Every answer below describes what the code does on `feature/play-readiness`. Re-check it whenever a feature changes
what leaves the phone, and update the Privacy Policy (`feature/legal/LegalDocuments.kt`, then the website via
`tools/legal/export_legal.py`) in the same change.

## 0. Before the first upload

| Step | Where | Notes |
|---|---|---|
| Merge the Android PRs in order (#23 → #24 + #25 → #26 → #27 target SDK 36 → this branch's PR) | GitHub | Play refuses uploads below API 36 |
| Apply `20261109090000_account_deletion.sql` to lucento-test, then production (OT-15) | Supabase SQL editor | Without it, Delete account answers "couldn't delete… email us" |
| Deploy the website (`feature/site-refresh`) **after** the production migrations | Vercel | `/privacy`, `/terms` and `/delete-account` must be live before you submit; their URLs go into the forms below |
| Signed App Bundle | GitHub Actions › a run on the release branch › artifact `lucento-release` | CI signs with `LUCENTO_KEYSTORE_*` when the secrets exist; the run summary says "unsigned" otherwise |
| Version | `app/build.gradle.kts` `versionCode` / `versionName` | Every upload needs a higher `versionCode` than the last (today 5 / 0.5.0) |

## 1. Google sign-in in Play builds (easy to miss)

Play re-signs the app with the **app signing key**, so Google sign-in fails in every Play-installed copy until that
key's SHA-1 is registered:

1. Play Console › Test and release › App integrity › App signing: copy the SHA-1 of the **App signing key** and of the
   **Upload key**.
2. Google Cloud Console (the project that owns `LUCENTO_GOOGLE_WEB_CLIENT_ID`) › APIs & Services › Credentials: create
   an **Android** OAuth client for `com.lucento.app` with each SHA-1. Nothing needs to be copied into the app
   (docs/SYNC.md, "Setup required").
3. **OAuth consent screen**: publishing status *In production*, app name Lucento, support email lucentoapp@gmail.com,
   privacy policy `https://www.lucentoapp.com/privacy`, authorised domain `lucentoapp.com`.

## 2. App content (Policy › App content)

| Form | Answer |
|---|---|
| Privacy policy | `https://www.lucentoapp.com/privacy` |
| App access | **Some functionality is restricted.** Core features need no account. Give reviewers an email account on **production** that already holds Lucento Pro: create it in the app, then add a `GRANT` row with every capability and a 90-day expiry, as in SERVER_CONTRACT.md §7. Instructions: "Open Lucento › Get started (or I already have Lucento) › Use email instead › sign in with the account below. Pro features, cloud sync and Lucento Shared are enabled on this account." |
| Ads | **No**, the app contains no ads |
| Content rating | Category *Utility, Productivity, Communication or Other*. Answer no to violence, sexuality, language, controlled substances, gambling. **Users can interact or exchange content: Yes, limited.** Lucento Shared lets an invited Partner see and add records in the Owner's ledger; nothing is public. **Shares location: No. Digital purchases: Yes** (Lucento Pro) |
| Target audience | **18 and over.** It's a personal finance app with purchases; the Privacy Policy says it isn't directed at children |
| News app | No |
| COVID-19 apps | No |
| Data safety | Section 3 below |
| Government app | No |
| Financial features | Lucento records and budgets the person's own money. It doesn't hold or move money, lend, process payments, trade or connect to banks. Choose the personal finance / budgeting option if the form lists one; otherwise "My app doesn't provide any of these financial features" |
| Health apps | No |
| Account deletion | Covered in the Data safety form (section 3.4) |

## 3. Data safety

### 3.1 Data collection and security

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all of the user data collected by your app encrypted in transit? | **Yes** (HTTPS only; `network_security_config.xml` forbids cleartext) |
| Which account creation methods does your app support? | **Username and password** (email and password) and **OAuth** (Continue with Google) |
| Delete account URL | `https://www.lucentoapp.com/delete-account` |
| Do you provide a way for users to request that some or all of their data is deleted, without requiring them to delete their account? | **Yes.** Any record can be deleted in the app, and the deletion syncs. Turning off usage counts deletes what was sent |

### 3.2 Data types

"Collected" means it leaves the phone to Lucento's servers or a service provider working for Lucento. "Shared" means
handed to an independent third party. Lucento shares nothing: service providers (Supabase, Vercel, Google Gemini,
Resend) and transfers the user starts (Lucento Shared) don't count as sharing. Every type below is **optional**,
because accounts, sync, AI and usage counts are all opt-in.

| Category › type | Collected | Why (purposes) | Ephemeral? | Notes |
|---|---|---|---|---|
| Personal info › Email address | Yes | Account management, App functionality | No | Account email; a Lucento Shared invitation stores the invitee's email until it's accepted, declined or expires |
| Personal info › Name | Yes | Account management | No | Google sign-in's name in the account metadata; the Shared Space label a member chooses |
| Personal info › User IDs | Yes | Account management, App functionality | No | The Supabase account id |
| Financial info › Purchase history | Yes | App functionality, Account management | No | Product, order number, status, dates, purchase-token hash (Lucento Pro) |
| Financial info › Other financial info | Yes | App functionality | No | The synced ledger (wallets, transactions, budgets, goals, debts, schedules); figures sent to the AI features the person turns on |
| App activity › Other user-generated content | Yes | App functionality | **Yes** | Messages and questions typed or spoken into the AI features; the server keeps no copy |
| App activity › App interactions | Yes | Analytics | No | Usage counts, off by default: event names and timings, kept 90 days |
| App info and performance › Crash logs | **Only if the release is built with `app/google-services.json`** | Analytics | No | Crashlytics, off by default; stacks without amounts or notes. CI builds without the file today, so CI releases don't collect it |
| App info and performance › Diagnostics | Yes | App functionality | No | Google Play Billing Library's own diagnostics (LP-K13). Re-check against Google's current SDK guidance when filling the form |
| Device or other IDs | Yes | Analytics | No | The random install id attached to usage counts (and Crashlytics' installation id when it's built in) |

Not collected:

- **Location, contacts, calendar, SMS, call logs, files, audio, health, web history.**
- **Photos.** Receipt scans and profile photos stay on the phone.
- **Payment info.** Google Play handles cards.
- **Exchange-rate requests** send two currency codes and a date, and identify no one.

### 3.3 Security section answers

- Data is encrypted in transit: **Yes**.
- Users can request deletion: **Yes**.
- Committed to the Families policy: not applicable.
- Independent security review: **No**.

### 3.4 Account deletion evidence

Play may ask for screenshots: Settings › Cloud sync › **Delete account** and its confirmation (`DeleteAccountDialogTest`
saves them to `/data/local/tmp/lucento-shots/delete-0*.png`), plus the public page.

## 4. Store listing (Grow › Store presence › Main store listing)

| Field | Value |
|---|---|
| App name | Lucento |
| Short description (≤ 80) | Budget by typing what you spent. Offline, private, no bank login. |
| Category / tags | Finance; tags: Budget, Personal finance, Expense tracker |
| Contact email | lucentoapp@gmail.com |
| Website | `https://www.lucentoapp.com` |
| Privacy policy | `https://www.lucentoapp.com/privacy` |
| Graphics | Icon 512 × 512 (exported from the Rising Crest source in lucento-website `brand/logo`, never overwriting the original); feature graphic 1024 × 500; 4–8 phone screenshots. The latest `ScreenshotTourTest` frames (CI artifact `ui-screenshots`) are real screens with fictional data |

Full description (≤ 4000):

> Lucento is a personal finance app for people who want a clear picture of their money without the busywork.
>
> Write it the way you'd text it. Type or say "lunch 250, groceries 800, salary 35000" and Lucento turns it into
> transactions you check before anything is saved. Scan a receipt and it's read on your phone.
>
> Keep pace with the month. Set a budget for each category and Lucento shows what you've spent against how much of the
> month has passed, and what's safe to spend today.
>
> Everything in one place: cash, bank accounts, e-wallets, savings, credit cards and loans, with logos for 400+
> institutions. Schedule bills and salary, save toward goals, track what you owe and what others owe you, and see your
> net worth.
>
> Private by design. Lucento works fully offline, on your phone, with no bank login and no ads. An account is optional.
> Sign in only when you want cloud sync, to share with a partner, or to use the AI features.
>
> Lucento Pro (optional) adds:
> - unlimited wallets and goals;
> - wallets in other currencies;
> - 12-month Insights and PDF statements;
> - cloud sync between phones;
> - Lucento Shared, so a partner can record from their own phone;
> - an AI adviser that answers from your own figures.
>
> Choose monthly, yearly with a 7-day free trial, or a one-time Lifetime purchase. Subscriptions renew until you cancel
> in Google Play.
>
> Lucento records the money you tell it about. It doesn't connect to your bank, move money or give financial advice.

## 5. Testing tracks and production access

- **Internal testing first.** Upload the signed AAB, add testers by email list, and run the checks in
  PLAY_CONSOLE_SETUP.md "Checking it works": purchases with license testers, refunds, account deletion and sign-in on
  the Play-signed build.
- **Personal developer accounts created after 13 November 2023** must run a **closed test with at least 12 testers,
  opted in continuously for 14 days**, before Play grants production access. Organisation accounts are exempt. Start
  the closed track as soon as the first build passes internal testing; this is the longest wait in the schedule.
- **Pre-launch report.** Read it after each upload: crashes, accessibility, and the "Data safety" mismatch warnings.

## 6. Before each release

1. Raise `versionCode` (and `versionName`).
2. CI green on the release commit; download `lucento-release` and upload the `.aab`.
3. Release notes in plain words; no promises about features that aren't in the build.
4. If anything changed what leaves the phone: update `LegalDocuments.kt`, bump `VERSION`, regenerate the website legal
   data, redeploy the site, then update the Data safety form.
