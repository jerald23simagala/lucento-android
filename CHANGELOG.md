# Changelog

> Current through 0.5.19 (version code 24, 2026-11-23). Each release lists what changed for people using the app, with
> its pull request numbers; the tests and CI runs behind each change stay in `docs/handover/CURRENT_STATUS.md`. Work
> merged between 2026-11-01 and the first Google Play build, 0.5.0 (version code 5), such as Lucento Pro, Lucento
> Shared, wallets in other currencies, themes and Awards, is not itemised here; `CURRENT_STATUS.md` records it.

## Unreleased — tester round 2026-11-24

- **The Budget tab leads with what's spent**, as Home's budget card does: "₱22,979.90 spent of your ₱25,900.00
  budget", then the bar and "₱2,920.10 unspent" (or "over budget"). Testers couldn't match the two screens when one led
  with spent and the other with unspent.
- **Card payments can be skipped.** Tapping a card's payment reminder (the bell, Upcoming, Scheduled) opens its sheet:
  **Pay** opens the payment as before; **Skip this statement** stops the reminder and stops setting the amount aside in
  Safe to spend until the card's next statement, with Undo right after. Skips are kept on this phone.
- **Add transactions:** "Into" is one field that opens the wallet picker (grouped, with balances) instead of a row of
  chips, and every row shows its wallet, so rows from different wallets in one message can be set before saving.

## 0.5.19 (version code 24) — 2026-11-23

- **Progress is now Milestones.** It opens with the Awards card, which shows three small medals: the latest one
  earned in gold, then outlines of those still to come (#145).
- **Your budget year** (Pro): January to December as 12 colour squares in the Budget tab's levels, each with its month
  and the share of the budget used, with a legend, a year switcher and the year's totals as a short bulleted list. The
  free plan sees its own months as grey squares with the results hidden (#145).
- A month's recap has its own page, with arrows to step between months. It opens from the month's square on every plan
  and from Home's check-in card (#145).
- **Awards** have their own page, opened from the Awards card or a ceremony's "See all awards" (#145).
- Follow Lucento's Facebook row opens the Page @lucentoapp (#144).

## 0.5.18 (version code 23) — 2026-11-23

- **Ask Lucento's AI answers** are counted per calendar month: 5 questions on the free plan and 45 with Pro, with no
  daily limit. Ask shows how many are left this month, on every plan (#140).
- **Smarter reading** (Pro) uses the same monthly questions, and Settings now says so first. It is off for everyone
  until they agree again, and a message Gemini read shows how many questions are left (#140).
- The Pro welcome no longer deals a Smarter reading card (#140).
- Typed and spoken messages know businesses as scans do: when your words name no category, Lucento uses the one you
  usually pick for that business, then the one its list of businesses suggests (never for income). An unknown business
  still asks (#142).
- The Safe to spend card puts its sentence beside the amount on phones, with the amount a little smaller and an
  information icon; with large text they stack as before (#141).

## 0.5.17 (version code 22) — 2026-11-23

- **Monthly check-in**: from a month's last 3 days to the 7th, once your budget is at least 14 days old, a card on Home
  opens **Plan <month>**, the coming month's own budget. Its hints come from your records (a yearly bill due that month,
  what the same month cost last year, last month's overspending), Add something covers the extras, and changes start
  as that month's only (#138).
- The Budget tab plans up to 12 months ahead, and offers the plan page for the running month from the 1st to the 7th
  (#138).
- **Progress** replaces the awards sheet: a month's recap (kept, budget, where it went, the biggest change, net worth,
  goals, debts, days recorded), the 12-month year (Pro; the free plan sees its own months with the figures hidden) and
  the award cabinet (#138).
- Three new awards: Steady Month, Goal Reached and Loan Cleared. What you had already achieved is recorded quietly,
  without ceremonies (#138).
- **Follow Lucento** in the profile menu: Facebook, Instagram, TikTok and YouTube. The links open outside the app, and
  Lucento sends nothing (#138).
- Home's balance card is labelled "Net worth", as in Insights, Reports and Ask (#138).

## 0.5.16 (version code 21) — 2026-11-23

- **Settings follow your account** with cloud sync (Pro): your theme and light or dark mode, enabled features, display
  name, Show cents, the Wallets view, exchange rates online, the default wallet and a few more sync between your phones
  and come back on a new phone or after a restore (#130).
- Backups carry these settings too; older versions of Lucento can't restore the new backup files (#130).
- Consents, Lock Lucento and hidden amounts stay on each phone, and a backup file can no longer switch Smarter reading
  on (#130).
- Pro themes and Sync now check your plan at the moment you tap them (#134).
- Google Play offers Lucento again on devices that only run in landscape, mostly Chromebooks; 0.5.15's document scanner
  had made a portrait screen required (#128).
- Pro cards: the mascot and its prop are centred on every card, and until the server confirms Pro, the AI answers card
  says "Pro allowance until" its date (#127).
- Includes everything in 0.5.15, which was built from its own branch (#124, #125, #126).
- Privacy Policy version 2026-11-23: the "Scanning documents" section and the paragraph on settings that sync, together
  (#125, #130, #135).

## 0.5.15 (version code 20) — 2026-11-22

- **Lucento Scan** reads more than receipts, on the phone: scan pages with the document scanner, choose several images
  or import a PDF (a locked one asks for its password, used once and never kept). Pages are joined into one document
  or split into several (#125).
- Each document keeps its own currency and dates. A bank, card or e-wallet statement is checked against what you've
  saved: lines already saved are held back (Add anyway adds them), near matches are marked, and the statement's balance
  is compared with Lucento's (#125).
- Scan suggests categories from lists of businesses for seven regions and from your own entries (#125).
- Tilted receipts are read straight, and the paid total is found on more receipts (AMT DUE, TTL, VAT inclusive, cash
  less change) (#124).
- Settings › Account & sync no longer shows Update password to accounts that sign in only with Google (#124).
- Smaller fixes: Home's tooltip explains a foreign wallet's value in your main currency, the setup summary says your
  data syncs only when your plan includes sync, the plan page no longer lists debts as unlimited, the composer says when
  a message held more than 30 transactions or 2,000 characters, and income and expenses need a category in the editor
  (#116).
- Privacy Policy version 2026-11-22, with a new "Scanning documents" section (#125).

## 0.5.14 (version code 19) — 2026-11-19

- Home's **Monthly budget card** comes in three parts: what's spent, then Budget remaining and Spendable wallets side by
  side, then Safe to spend as the conclusion. The Budget tab matches (#106).
- A credit card can be the default wallet for expenses; income and transfers still go to your first cash, bank or
  e-wallet. Home's hero reads "Assets", and the composer's wallet picker "Select wallet to use" (#107).
- **Select mode** in Scheduled › All schedules: change the day or the amount, pause or resume, or delete several at
  once. A warning shows when a loan or card already has a payment plan (#108).
- On the free plan, setup stops at the free numbers without mentioning Pro; anywhere else, tapping something the plan
  doesn't include opens one dialog with your own numbers and what stays free (#109).
- Meeting the same limit again in a session shows a short message with Unlock instead. On the free plan, Insights' 6
  and 12 months show a preview from your own months, and a Pro theme can be tried on (#110).
- "See Lucento Plan" is the one label for the plan page, a theme you tried is applied once you buy Pro, and setup
  promises nothing the free plan doesn't give (#112).

## 0.5.13 (version code 18) — 2026-11-18

- **Show cents** in Settings › Money, on by default. Turned off, every amount you read is rounded to whole units; what
  you type, a transaction's own page and every calculation stay exact (#98).
- Home's five shortcut tiles take each theme's own colours (#99).
- One account picker for transfers, the editor and the composer: four groups with balances, plus search and Recent once
  you have more than six accounts (#100).
- **Investment** wallets for stocks, funds, MP2, time deposits, crypto and the like, with Update value for changes in
  worth. Kinds and icons label investments, things you own and loans (#101).
- **Spending money**: choose the wallets you spend from, and part of a card's credit. With a budget, Safe to spend shows
  the lower of what the budget and the spending money allow, and an (i) explains both (#101).
- **Scheduled transfers** between your own accounts, for example into savings every month; marking one done records one
  transfer (#101).
- Backups carry the new wallet details; older versions of Lucento can't restore backups made from now on (#101).
- Bottom sheets' status and navigation bar icons follow Lucento's theme instead of the phone's dark setting (#102).

## 0.5.12 (version code 17) — 2026-11-17

- **Ask Lucento's AI answers** are on by default for every signed-in account, including the free plan's 10 questions a
  month (#95).
- Nothing is sent until you agree once: the first question that would go to Gemini shows what would be sent, with Ask
  Gemini or Keep answers on this phone (#95).
- The free plan's allowance now says "this month"; it wrongly said "today" (#95).
- Privacy Policy and Terms of Use version 2026-11-17 (#95).

## 0.5.11 (version code 16) — 2026-11-16

- The Budget tab's Safe per day and the "You stayed within <month>'s budget" moment now count spending from wallets in
  other currencies, as Home does (#90).
- Lucento draws behind the status and navigation bars without the system settings Android 15 retired, which Google Play
  had flagged on 0.5.10; screens look the same (#92).

## 0.5.10 (version code 15) — 2026-11-15

- **Add transactions** reads your message on the phone, so its rows appear straight away. The Smarter reading switch
  above the message field is gone (#83).
- **Smarter reading** (Pro) is turned on only in Settings › Privacy & security, which first says each message will then
  wait a few seconds for Gemini. On the free plan the switch is locked (#83).
- **Ask Lucento** has a microphone: speak a question and it's asked as soon as you stop talking (#83).
- Words heard as you leave the app mid-sentence now go after what you'd already typed instead of being lost (#83).

## 0.5.9 (version code 14) — 2026-11-14

- **Sign out** is in the profile menu, and "Disconnect" is now "Sign out" everywhere. The confirmation depends on where
  your records stand: the main button never loses anything (Sync first, Save a backup or Keep them here), and only the
  red one removes records. The welcome screen then says what happened (#58).
- The Pro page is now the **Lucento Plan** page. The profile menu shows a Pro or Lifetime chip after your name, and on
  the free plan a Lucento Pro card (#37).
- Sign-up asks for the password twice, and Forgot password? emails a code to set a new one. The Privacy Policy, still
  version 2026-11-14, now lists the reset email (#37).
- **Ask Lucento**: AI answers get a switch in the top bar; questions plainly not about money get a short answer on the
  phone; and "Should I buy…" or "Can I afford…" gets a Purchase check worked out on the phone when AI answers are off
  or Gemini can't be reached. The composer gets a Smarter reading switch (Pro) (#37).
- Also: Once as a schedule frequency; a Secured award for turning on Lock Lucento; on the free plan, Lucento Shared
  says inviting a Partner is part of Pro (being a Partner stays free), and signing in on a new phone with nothing to
  restore offers Set up as new instead of a loop (#37).
- A purchase made while signed out goes to the first account that signs in on the phone; the Lucento Plan page and the
  sign-in panel now warn about it. Signing out takes the account's plan off the phone (#71, #73).
- Google Play offers Lucento again on devices without a microphone (#69).
- Newer build tools and libraries, with nothing different on screen (#67, #68).

## 0.5.8 (version code 13) — 2026-11-14

- **Voice input** listens until you've finished: Lucento asks for the microphone on the first tap, waits up to 10
  seconds for your first words, and stops after 3.5 seconds of quiet or when you tap Done. If you don't allow the
  microphone, the phone's own speech window listens instead (#60).
- On phones that can't recognise speech offline, the composer offers Try online voice, with your consent; change it in
  Settings › Privacy › Online voice (#60).
- **Buying Pro Lifetime after a refund**: before Google Play's sheet opens, Lucento releases a Lifetime its server has
  ended, so Google Play can sell it again instead of saying "You already own this item" (#65).
- Restore purchases says a refunded Lifetime was refunded, instead of "No purchase found" (#65).
- Privacy Policy version 2026-11-14, with a new "Voice input" section on the microphone and online voice (#60, #66).

## 0.5.7 (version code 12) — 2026-11-13

- A refunded purchase stays off after you sign out, even while Google Play's copy on the phone still lists it (#61).
- Signed in or not, while you're online Lucento asks its server every few hours whether the Pro purchase on the phone is
  still valid, so a refund ends Pro on every phone, including one that was cleared or reinstalled (#63).
- Lucento's server needs to confirm a purchase within 3 days of buying it, then at least once every 90 days; until it
  can, Pro pauses on that phone, and your records are never touched (#63).
- Pro cards: the mascot floats once per card, the doodles stay clear of it and its prop, and its hand rests on the prop
  only when pointing (#62).
- Privacy Policy version 2026-11-13: the purchase check sends Google Play's signed record of the purchase, never your
  records (#63).

## 0.5.6 (version code 11) — 2026-11-13

- **Home-screen widgets**, on every plan: Quick add opens the composer's field, scan or microphone in one tap, and Safe
  to spend shows Home's figure with its reason. Add them from Settings › Home-screen widgets (#57).
- With Lock Lucento on, the Safe to spend widget shows no amounts (#57).
- **Pro cards get new art**: Lucento's mascot presents each perk with a prop, on a blue band with faint doodles, and the
  collection shows each card's prop (#59).

## 0.5.5 (version code 10) — 2026-11-12

- **The Pro welcome is a deck of cards**, one per perk: turn them with Next, Previous or a swipe, or Skip. It replaces
  the tour, whose buttons didn't answer a finger on real phones in 0.5.4 (#56).
- After the deck, a collection lists each perk with what it gives and until when, then come the Lucento Pro medal and
  the badge. The Lucento Pro page's button is now "See your Pro cards" (#56).
- App-wide, a button whose words don't fit now wraps them onto a second line instead of cutting them off (#56).
- A purchase Lucento's server revoked stays off on the phone while Google Play's copy still lists it (#55).
- Anything bought through Google Play needs one check a month, by Google Play or Lucento's server. Without one, the plan
  is paused, not ended, with its own note in the app and a matching Restore purchases message (#55).

## 0.5.4 (version code 9) — 2026-11-12

- **Welcome to Pro**: after Google Play confirms a purchase, a tour lights up what Pro opened on the real screens. A
  summary then shows the higher limits, and the online services once Lucento's server confirms them (#54).
- The Lucento Pro medal is awarded with a ceremony and kept on a new Membership shelf in Awards; while the plan is
  active, the profile picture wears a gold Pro ring (#54).
- It plays once per plan and never for a pending payment, a renewal or a trial turning paid; a restored plan gets
  "Welcome back to Pro" without a second ceremony. Replay the Pro tour is on the Lucento Pro page (#54).
- Every award's sunburst now turns once and then rests (#54).

## 0.5.3 (version code 8) — 2026-11-11

- Buying Lucento Pro, or starting its free trial, through Google Play now unlocks Pro. In 0.5.2 and earlier builds from
  Google Play the purchase went through but the app stayed on the free plan, and Google then refunded it (#53).
- Lucento can no longer be built with a Google Play key of the wrong kind, so this can't come back unnoticed (#53).

## 0.5.2 (version code 7) — 2026-11-11

- The **Lucento Pro page** puts the plans first, each with what it gives, and a Yearly or Monthly choice with one button
  (Start 7-day free trial or Subscribe monthly); Yearly shows what it saves. Free, Pro and Lifetime are compared in a
  table (#51).
- Wallets: **Withdrawal** for cash, bank, e-wallet and savings, and **Record payment** for cards and loans, from a
  wallet or as an external payment. Each asks what it was for and never counts as income, spending or net flow (#52).
- Cards: setup can take the amount due now from your latest statement, and card faces get a light sheen, with the
  bank's own card logo in the gallery (#52).
- A **wallet statement**, from Insights and each wallet's page: the start balance, income and expenses by category,
  transfers, withdrawals and payments, then the end balance (#52).
- Insights: filters at the top, a quiet Money in and Money out summary, an expense donut and net worth over time. The
  built-in reports moved here from Ask Lucento, and "What your net worth is made of" was removed (#52).
- Hidden amounts now hide report details too; they showed before, in Ask Lucento as well (#52).
- The profile menu has a Community section: Lucento Community on Facebook, the website and Share Lucento (#52).
- The account page has a Password card and a red Danger zone, where deleting the account needs the tick and the
  account's email typed (#52).

## 0.5.1 (version code 6) — 2026-11-10

- Signing in with Google works. In 0.5.0 (version code 5) it ended with "cancelled" because the build carried the wrong
  Google sign-in setting. A rebuild with no other change (#50).

## Shipped by 0.5.0 (version code 5) — JSON backup and restore

- **Settings → Your data**: export a versioned JSON backup (`schemaVersion`, app version, export time) and restore it.
  The file contains wallets, categories, transactions, budgets, goals and contributions, scheduled payments, debt terms,
  deletion tombstones, and the preferences that belong in a backup (theme, currency, enabled features, display name,
  default wallet, guide step, Smarter reading). Amounts are integer minor units; dates are ISO-8601. Stable ids are
  each row’s `client_uuid`.
- Restore shows counts and the transaction date range, then **Merge** (upsert by id; the newer `updatedAt` wins) or
  **Replace** (wipe the local ledger and import it inside one Room transaction). A malformed file, an unknown
  schema version, or a tampered field changes nothing. Older files migrate forward (version 0 used `accounts` and
  epoch numbers).
- Merge folds a category from another install into the local one when the normalized name and the kind both match,
  and points transactions, budgets and schedules at that category. A matching name with a different kind is shown in
  the preview and both categories are kept.
- Replace stamps restored `updatedAt` (and a contribution’s `createdAt`, which sync sends as `updated_at`) with the
  restore time, so the server’s concurrent last-write-wins rule does not skip the restore. Rows the replace removed
  are unpushed deletion tombstones and are uploaded on the next sync.
- Restored rows are marked dirty with no base revision, so they upload on the next sync and still restore when signed out.
  Sign-in, crash reports, sync cursors and goal photo files are not in the backup. CSV export is unchanged.
- Goal photos stay on the device. A goal restored from a file keeps its scene and not a custom photo.

## Reverted — Short first run

**Reverted on 2026-10-29:** onboarding kept its full steps, including choosing your spending categories. Kept here
for the record.

- First run is four screens: welcome (data stays on this phone; uninstalling erases it unless sync is on), main
  currency, a starting balance, then the composer. Theme stays System, sync stays off, and the recommended spending
  categories are applied without a picker. Goals, recurring payments and debts start on
  (`FirstRunDefaults.DEBTS_ON_BY_DEFAULT`). Categories, features and sync remain in Settings.
- The guided setup no longer blocks until two categories have a budget. After 7–14 days of real expenses, Home can
  suggest category amounts. Nothing sample is written. People who already finished onboarding are not sent through
  it again; a saved debts or budget step resumes at the next step that still exists.
- Day-one Home shows the real balance and recent entries, a dismissible first-week card, and one mascot reaction
  to the first entry. Home offers "See Lucento with example data" until the first real entry or until that row is
  opened. The sample stays in memory, is labelled Preview on every screen, and is not written to the ledger.
  Home layout wraps at font scale 1.3 and 2.0 and at 360dp.
- Anonymous usage counts stay off until the person opts in. Eight event names are queued on the device and inserted
  only while opted in. Opting out calls `delete_usage_events` for that install id before the id is forgotten; a failed
  delete is kept and retried next launch. The SQL is committed and not applied to a live project.

## Shipped by 0.5.0 (version code 5) — Optional cloud sync (Supabase + Google Sign-In)

- **Settings → Cloud sync**: connect a Google account to back up and sync wallets, categories, transactions, budgets,
  goals and goal contributions. Off by default; Lucento remains fully usable offline with no account. Signing out,
  and switching to a different account, removes that account’s data from this phone (unsynced changes ask first) so
  it is never uploaded to another account. Shows the connected account, last sync time / error, "Sync now" and "Disconnect". When the
  build has no Supabase or Google OAuth configuration the section says so instead of showing a button that fails.
- Offline-first engine (`data/sync`): Room stays the source of truth. Pushes dirty rows and deletion tombstones, pulls
  changes since the last cursor, merges by last-write-wins on `updated_at` (a newer unsynced local edit is never
  discarded), retries via WorkManager (periodic every 30 min on a network connection, plus "Sync now").
- **Database v6** (`MIGRATION_5_6` + 2 instrumented tests, run on an API 28 emulator): `client_uuid` for wallets,
  categories, budgets and goals (backfilled); `dirty`/`synced_at` on every synced table; `categories.updated_at`;
  `sync_tombstones` filled by `AFTER DELETE` triggers; an `AFTER UPDATE` trigger re-marks edited rows dirty. No
  repository's delete path changed. Categories now maintain `updated_at` on every edit.
- Supabase (`supabase/migrations/`, applied to project `lucento`): six tables, RLS owner-only policies, composite
  user-scoped foreign keys, server-side last-write-wins trigger, grants to `authenticated` only. Verified with a
  rolled-back cross-account test at the SQL level; Supabase security advisor clean.
- Manifest: INTERNET + ACCESS_NETWORK_STATE now present for sync only; HTTPS-only network security config; CI checks
  release permissions against an allow-list instead of forbidding network access. Privacy copy in Settings updated.
- Config via `local.properties` / CI env (`LUCENTO_SUPABASE_URL`, `LUCENTO_SUPABASE_PUBLISHABLE_KEY`,
  `LUCENTO_GOOGLE_WEB_CLIENT_ID`; see `local.properties.example`). Only the publishable key reaches the APK.
- Not yet: Google OAuth client setup (so real sign-in is untested), recurring-schedule sync. See docs/SYNC.md.

## Shipped by 0.5.0 (version code 5) — Sunrise Energy brand refresh

- **Transaction composer replaces the Add sheet.** The + button, the Transactions empty state and a wallet's
  Expense/Income tiles open one screen: type, speak or scan, and every transaction in the message appears as an
  editable row (type, category, date and account inferred; inline correction; competing amounts offered as choices;
  receipts produce one expense for the total). One tap saves the batch atomically with per-row idempotency keys
  (`TransactionRepository.saveBatch`). Removed `AddTransactionSheet`, `VoiceEntryScreen`, `ReceiptScanScreen` and their
  routes. On-device only — no AI service and no network (see docs/CAPTURE.md).
- Parser: month-name dates ("October 12", "12 Oct 2026"), spoken number words, specific-first category keywords for the
  onboarding presets, receipt items-vs-total check. "Voice & text entry" module renamed "Voice entry" (typing is core).
- 55 new JVM tests (batch parsing, review rules, receipts, atomic batch save with rollback, number words).

- Goals: optional suggestions (Japan trip, holiday, emergency fund, laptop) and a card picture picker.
- Goals: a chosen photo can be the card picture (private copy in app storage, database v5 `photo_path`).
  Photos are not in any backup because the app has no backup or restore yet.
- Palette: Sunlight Yellow `#FFC928`, Bright Tangerine `#FF8A24`, Electric Blue `#3563F5`, Soft Ivory `#FFFDF5`,
  Midnight Navy `#17233F`, Pale Sky `#EAF1FF`; new night theme on `#10182B` / `#17233F` / `#202E4D`.
- Separate semantic status tokens (positive, warning, negative, info) with text-safe variants; new `info`,
  `warmAccent` and `highlight` extended colours. Sunrise chart order for categorical series.
- UI Lottie illustrations (empty states, feedback, loading, net flow, receipt scan) recoloured to the palette. The
  logo (mark, launcher, splash, onboarding animation) and the mascot keep their original artwork and colours.
- Typography: Plus Jakarta Sans (bundled static 400/500/600/700, OFL) replaces Inter; Sunrise type scale.
- Radii 6/10/14/20/28 (`Radii`), `Elevation` scale with navy-tinted card shadow by day, border tokens
  (`outlineVariant` `#E8EAF0`, `borderDefault`), muted ink `#5E687D`, blue active dock icon, inputs on radius-sm.
- **Mascot:** the approved Lucento character replaces the interim vector/Lottie Sun and Moon. 35 WebP sprites
  (5 wellness tiers × 7 poses, ~1.7 MB) in `drawable-nodpi`, a code-drawn torch glow, gentle float and cross-fades.
  The Budget hero picks the tier from budget health via `WellnessTier` (unit-tested). Removed
  `lucento_mascot_sun/moon.json`, their generator and the vector drawing; mascot colour tokens dropped.
- Mascot motion made visible on a phone: arrival hop with a soft spring, greeting rocks, float 4.5 % of height.
- "A Brighter Little World" illustrations (crops of one painterly sunrise/Fuji panorama) with `IllustratedSurface` +
  scrims; on the Home budget hero, travel savings goals (`GoalScene`, unit-tested) and the onboarding welcome.
- Savings goals: optional suggestions in the new-goal form (Trip to Japan, Holiday, Emergency fund, New laptop) and a
  card-picture picker (Automatic / Mount Fuji / Sunrise / Mountains / No picture). **Database v4**: nullable
  `goals.scene_key` (MIGRATION_3_4 + MigrationTest); 4.json must be committed from the CI `room-schemas` artifact.
- `ThemeContrastTest` checks AA contrast for every text/background pair in both themes.
- Palette use aligned with the reference board: tonal category tiles (pale tint + saturated glyph; deep tint +
  light glyph at night), pale-sky secondary buttons, solid-blue selected chips, an elevated-navy night budget hero,
  and indicator colours (not text colours) for approaching/over budget bars.
- No behaviour, layout or dependency changes.

## 0.5.0 — 2026-10-22 (UI/UX refinement; JVM-tested locally, awaiting CI compile)

### Navigation
- Persistent dock: Home · Transactions · Wallets · Insights · Budget + More menu (goals, recurring, debts, categories).
  Hidden only for focused workflows (scan, speak/type, category setup, settings). Tab state preserved.
- Profile button on every primary screen → profile menu (optional device-only name, appearance, Settings, Enabled
  features). Settings is no longer a primary destination.
- App-wide + opens Add Transaction: Manual, Scan, Speak (listens on arrival), Type it (focuses the sentence field).
  Removed: Add tab, Quick Add screen, Plan hub, More screen.

### Home
- Budget hero (brand panel, ~30 % shorter) with remaining/total/spent and the Sun mascot: happy within budget,
  supportively concerned at ≤ 10 % left or exceeded (new `concerned` Lottie marker + vector still).
- Compact net-flow card with an animated trend glyph (green / red / neutral at zero — new `net_flow_indicator` Lottie).
- Burn-rate consumption bar with a today marker; same thresholds as the hero; no percentage without a budget.
- Savings goals level; Upcoming recurring payments replace Recent transactions (tap → review / mark paid).
- Action-button row removed.

### Budget
- The overall budget is derived: the sum of category allocations per currency and month (`BudgetPlanner`). No
  separate "budget for everything"; legacy rows are ignored, explained once and removable.
- New Budget tab: summary, budgeted / not-budgeted categories, spending outside the budget, amount sheet with a
  live new-total preview, category management (add, rename, reorder, deactivate, restore).
- Mandatory onboarding step: choose spending categories from a grouped preset library (≈90 presets, 10 groups,
  search, recommended defaults, custom names) and review before confirming. Fresh installs seed income categories
  only. Existing users are never sent back through onboarding: a one-time card offers the same picker or keeping
  their categories; users with no categories get "Finish setting up your budget".
- Budgets module on by default.

### Insights
- Focused on income/expense trends (3/6/12 months, transfers excluded) and net-worth composition (assets and
  liabilities by account type on a shared scale). Category donut and budget performance removed from Insights.

### Quality
- 38 new JVM tests (253 total, passing in `tools/jvm-harness/run.sh`, now committed).
- `ScreenshotTourTest` (emulator) + CI upload of screenshots; CI also runs on `feature/**` and `release/**` pushes.
- No Room schema change (database stays v3).

## 0.4.0 — 2026-10-22 (local, not yet built by CI)

### Financial integrity
- Wallets: switching between an asset and a liability type, or changing currency, is refused while transactions
  (incl. transfer legs) or recurring schedules use the wallet; the editor explains why (`WalletChangePolicy`).
- Categories: removing one that transactions, budgets or recurring schedules use archives it instead of deleting
  (previously a category with only a budget was deleted and the budget cascade-deleted). `restore()` added.
- Recurring: idempotency key now uses the occurrence's due date. Fixes a bug where, after editing a schedule's
  start/frequency, "Mark paid" silently matched an older payment and recorded nothing.
- Transactions/transfers: duplicate check repeated inside the database transaction; a unique-key race returns the
  winning row; failed writes return an error instead of crashing, and never show a success confirmation.
- CSV: formula neutralisation for account, category and note (leading whitespace/control/BOM/full-width aware),
  generated amounts untouched.
- Receipts: amounts read with 0/2/3 decimals by detected or expected currency, integer totals on total lines, dates,
  times, phones, IDs, percentages and quantities masked; detected currency picks a matching wallet, never converts.
- 69 new JVM tests (215 total, all passing in the local harness).

### Database and CI
- Authentic Room schemas 1, 2, 3 committed (from CI artifacts of `11e2f3d`, `b55d7a2`, `6eec35b`).
- `tools/check_migrations.py` replays migrations on SQLite against the schemas (both steps match).
- Instrumented `MigrationTest` seeds representative wallets/categories/transactions and checks FKs, uniqueness and
  that the real `LucentoDatabase` opens the migrated file.
- CI: lint errors block; schema drift check; release APK + App Bundle with `aapt2` checks (id, not debuggable, no
  network permission); optional signing from secrets; emulator migration job; no `continue-on-error`.

### Design and motion
- Reference mockup added (`docs/design-reference/lucento-mock.png`) and applied: category hues, Home quick actions
  (Add · Transfer · Scan · Speak · More) and split net-flow card, Transactions type tabs, empty states with actions.
- Lottie (`lottie-compose` 6.6.7) with 17 original assets: Sun/Moon mascot expressions, onboarding mark, four empty
  states, receipt scanning, saved check, milestone, loading dots. Reduced motion shows finished stills.
- Confirmations after committed saves (transactions, transfers, recurring payments); goal halfway/reached and a month
  kept within budget (once) get a calm milestone.
- Version 0.4.0 (code 4).
