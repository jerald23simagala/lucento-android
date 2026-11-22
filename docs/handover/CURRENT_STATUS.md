# Lucento Android — Current Status

**Source:** `LUCENTO_FINAL_HANDOVER.pdf`, compiled October 30, 2026 (PT)

**Repository:** `jerald23simagala/lucento-android`

**Handover baseline:** `7928c63` (merge of PR #12)

**Current local branch:** `feature/ai-development-workflow`, based on `7928c63`

> This is a snapshot from the consolidated handover, not a fresh code audit. Verify all code and backend claims against the current checkout before acting.

## Update — 2026-11-01 (Claude), uncommitted on `feature/ai-development-workflow`

Verified this session unless noted. Details: `CLAUDE_INITIAL_AUDIT.md`, `P0_2_ONBOARDING_IMPLEMENTATION_REPORT.md`.

| Work | State | Evidence |
|---|---|---|
| **Feature comparison and lower Lifetime (2026-11-21, owner: "include features comparo and make reasonable adjustments… stay competitive vs apps with closest to similar features")**, branch `feature/penetration-pricing`. Feature research: 20 features across 8 Android apps, with sources. Closest to Lucento's 14 core features are Cashew 10.5, Money Lover 10, Wallet 9.5 and Bluecoins 9; none has in-app AI answers, debt interest or Android voice entry. The owner reviewed cheaper options (₱99 / ₱799) and kept the subscriptions; only Lifetime was lowered, from ₱3,490 / $79.99 to **₱2,490 / $59.99** (tier 2 $47.99, tier 4 $23.99, euro €59.99 / €47.99). A 'never above bank-sync apps' rule was set and then withdrawn. Price Check page updated (features chart and table). | Applied and verified | `plan`: only the 174 Lifetime prices change; `verify`: all 870 prices match; Lifetime option ACTIVE; `history/2026-11-21-lifetime-applied.json`; `docs/monetization/COMPETITOR_PRICING_2026-11.md` § Features |
| **Regional prices on Google Play (2026-11-21, owner: "proper regional pricing"; chose four income tiers, applied by API, one euro price per tier, no founder price)**, branch `feature/regional-pricing`. Before, Play converted the peso prices at the exchange rate (US $2.39 / $18.99 / $54.99). Now 174 regions are in four tiers: US $3.99 / $29.99 / $79.99, Cloud $1.49 / $12.99; the Philippines stays ₱149 / ₱1,190 / ₱3,490 / ₱59 / ₱490; India ₹170 / ₹1,300 / ₹3,500. `tools/play-pricing/` (tiers.json + pricing.mjs plan / apply / verify) set them through the Play Developer API, with a temporary Manage store presence permission on `lucento-play-billing` (OT-52 takes it back). No app change: the app shows Play's own prices. Competitor comparison: `docs/monetization/COMPETITOR_PRICING_2026-11.md` (subscription mid-pack, Lifetime high) | Applied and verified | `verify`: all 870 prices (5 × 174) match the plan; base plans, the 7-day trial and the Lifetime option still ACTIVE; before/after kept in `tools/play-pricing/history/`; table in `docs/monetization/REGIONAL_PRICES.md` |
| **Product issues from the launch film fixed (2026-11-19, owner: "Product Issues - we should fix these")**, merged as #116 (`6949bd1`). Home tooltip explains foreign wallets' main-currency value (no more "Nothing is converted") and TalkBack reads "Assets"; the Insights Ask hero asks "What are my top 5 spending categories?"; the onboarding summary says synced only when the plan includes sync (`StorageSummary`, existing `planAllowsCloud`); the Lucento Plan page no longer lists debts as unlimited; the composer says when a message held more than 30 transactions or 2,000 characters (`CaptureLimits`, on this phone and with Smarter reading); the transaction editor requires a category for income and expenses (owner rule 2026-11-04); README and CHANGELOG brought up to date. | Merged; CI green (unit tests, lint, builds, emulator tour); not yet on a device | `testDebugUnitTest` 1,669 tests, 0 failures, and `assembleDebug` OK (local, 2026-11-19). New: `StorageSummaryTest` (5), `CategoryRuleTest` (2), `BatchEntryParserTest` (+4), `AskHeroTypingTest` (+1), `RemoteCandidatesTest` (+1) |
| **Launch film refreshed with 0.5.14 screens (2026-11-19, owner: "We have updated repo - we need to update our launch film")**, branch `marketing/launch-film-0.5.14`. Same timing and score as the 2026-11-14 film; all 53 screens re-captured from 0.5.14 (vc19, `c4458d9`) on the API 35 shots AVD. Studio: `tools/remap_boxes.py` found every crop box that moved (Home's budget card redesigned, Insights charts +370 px, the Listening sheet +48 px, two bulk rows +25 px; the rest pixel-identical), scenes updated, Dashboard stitch seams moved off the app's 48 px dock fade, capture script AOT-compiles the app (startup ANR on a loaded PC), W47 shorts pinned to their approved 0.5.8 screens. Owner round 3 the same day: captions centred in the vertical version, "Every asset." became "Every asset and debt.", and "And there's more" shows twelve features at once (Installments tracking, Statement scanning, Receipt scanning, Home-screen widgets and Backups added, each checked in the code). Docs: discovery report re-verified on 0.5.14 (section 6 statuses, new section 7), asset inventory, storyboard, map, future topics, KNOWN_ISSUES. No app code changed. | Merged as #115 (`af11cbe`) with the owner's round 3 applied | Captured 2026-11-19 on 0.5.14: `ScreenshotTourTest` OK (82 shots), `WebsiteShowcaseTest` OK (16), 21 live stills. Rendered all six 2026-11-19 and probed each MP4 (ffprobe): master 1920 × 1080 and vertical 1080 × 1920, 2,800 frames, 93.33 s; 30 s cuts 880 frames; 15 s cuts 440 frames; AAC stereo in every file. Frames decoded from each MP4 reviewed; the soundtrack matches the approved render (peak −1.2 dBFS, RMS −17.0 dBFS, correlation 1.0000). Copies in `Downloads/lucento-launch-film/` (0.5.8 versions in `previous-0.5.8/`). Round 3: masters and vertical cuts re-rendered and probed again (same frame counts and audio; the landscape cuts hold no changed scene), frames decoded and reviewed. Studio commits `9ac447d`, `e800819`. Docs only here: no Gradle run |
| **AI answers on by default, confirmed once (2026-11-17, owner: "AI answers … should be enabled. Align everything"; Smarter reading stays Pro-only)**, branch `feature/ai-answers-on-by-default` (KNOWN_ISSUES AI-1…4). AI answers are on for every signed-in account (Free 10 a month, Pro 50 a day); the first question that would go to Gemini shows a card (`AskMessage.ConfirmAi`, `AiConfirmCard`) with what is sent, Google's free-tier sentence, the plan's allowance, **Ask Gemini** and **Keep answers on this phone**; "What's sent" opens the full sheet. Routing in `domain/ask/AskAiRoute.kt`; preferences `ask_ai_off` + the agreement `ask_lucento_ai_v2` (earlier agreements count; sign-out/reset back to the default); the one-time pop-up is gone. The allowance wording follows the plan (`feature/ask/AskAiCopy.kt`, the reply's `plan`, `ProGates.aiAllowancePerDay`): "N left this month" on Free. Settings shows the confirm note and a plan-aware dialog; its Privacy summary counts AI answers only once confirmed. Privacy Policy and Terms 2026-11-17; CLAUDE.md, ASK.md, MONETIZATION.md, SYNC.md, LEGAL.md and the Play submission notes updated. Website PR #36 (on hold until the release): regenerated `legal.ts`, homepage privacy table, FAQ, `PRODUCT.md` (to go live with the release) | Unit tests 1512/1512 (+20), `assembleDebug`, `lintDebug` 0 errors (20 warnings, as before), `compileDebugAndroidTestKotlin`. `AskAiConfirmTest` on API 35: 6 passing runs at 100 % and 200 % text; screenshots `artifacts/ai-answers-default/`. Added to CI's emulator job | No real Gemini answer yet (debug has no proxy): OT-36 in a release build |
| **Play Console edge-to-edge warnings on 0.5.10 (2026-11-16)**, branch `fix/edge-to-edge-deprecated-apis` (KNOWN_ISSUES E2E-1…3). The flagged `setStatusBarColor`, `setNavigationBarColor` and `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES` all came from androidx.activity's `enableEdgeToEdge()` (R8 map of the 0.5.10 AAB), and activity 1.13.0 still calls them. `MainActivity` now uses `Window.drawEdgeToEdge(darkTheme)` (`core/designsystem/theme/SystemBars.kt`: decor behind the bars before Android 15, no navigation-bar scrim, icon colours); the themes keep the bars transparent and set the cutout mode (`Base.Theme.Lucento` + `values-v28` shortEdges / `values-v30` always). CI's release inspection now fails if the release code calls those APIs again | Unit tests 1492/1492, `assembleDebug`, `assembleRelease`, `lintDebug` 0 errors (after the machine-local `local.properties` escape). `dexdump`: 0.5.10 had 2× `setStatusBarColor`, 2× `setNavigationBarColor`, 1× `setStatusBarContrastEnforced`, 2 cutout writes; the new release APK has none and no `EdgeToEdge` class; the CI grep flags the 0.5.10 dump and passes the new one. Before/after on API 28 and API 35 (`artifacts/edge-to-edge/`) | Play's re-scan needs the next upload. Android 11–14 seen only through CI's API 30 tour |
| **Launch film (2026-11-14, owner brief: "official product launch film")**. 93.3 s film (1920 × 1080, 30 fps; owner added an "And there's more" features scene) plus a vertical 1080 × 1920 version, and 30 s and 15 s cuts in both shapes, built in Remotion in `lucento-studio/video/src/launch-film/` from 53 real 0.5.8 captures (API 35, peso demo month) and an original synthesized score. Docs on branch `marketing/launch-film`: `docs/marketing/LAUNCH_VIDEO_FEATURE_DISCOVERY.md` (code-verified inventory, claims to avoid, 10 product issues) and `docs/marketing/launch-film/` (storyboard, feature-to-visual map, voiceover, asset inventory, cut-downs, future videos). No app code changed. | Rendered; awaiting owner review | MP4s in `lucento-studio/video/out/` and `Downloads/lucento-launch-film/`; frames reviewed scene by scene; Remotion Studio opens the project |
| **Sign-out takes the account's plan with it (2026-11-14, owner: "what the fix… then we do it")**, branch `fix/sign-out-takes-plan` (KNOWN_ISSUES PB-12). `AuthRepository.observeSignedOut` (Supabase `NotAuthenticated` only, never an offline `RefreshFailure`) → `EntitlementManager.signedOut`: the account's non-ended server rows leave the phone, Google Play's reading keeps the server's last confirmation (`EntitlementMerge.keepConfirmations`), ended rows stay (PB-6), and the plan it leaves is remembered first (`PlanMemory`), so no "plan ended" notice. MONETIZATION §5.4 and test matrix, NEXT_TASKS OT-26 | Code + JVM tests; not yet on a device | `SignOutPlanTest` (5); plan/billing/Pro tests 238 passed locally |
| **Signed-out purchase warnings (2026-11-14, owner: keep the rule, "simply add a warning")**, branch `fix/signed-out-purchase-warning`. A Pro purchase made while signed out goes to the first account that signs in on the phone, for good (the server's linking rule, unchanged; Terms of Use already say so). The Lucento Plan page now warns above the plans while signed out (`ProCopy.SIGNED_OUT_PURCHASE`), and the sign-in panel warns while Google Play holds such a purchase (`EntitlementManager.boughtWithoutAccount`, `ConnectAccountPanel(notice)`, Settings and onboarding). MONETIZATION §5.4, customer copy D-13, KNOWN_ISSUES PB-11 (accepted) and PB-12 (sign-out leaves the account's server plan on the phone: open), NEXT_TASKS OT-26 | Code + JVM tests; not yet on a device | See the PR for the test run |
| **Sign out (2026-11-12, owner request: "explore a logout button"; owner chose the full fix and "ask keep or remove")**. (1) **Profile menu › Sign out**, last row of "Account and app", only while signed in; opens Settings › Account & sync with the confirmation already open (`SettingsRoute(signOut = true)`). (2) **One name**: "Disconnect" is gone; Account & sync shows Sync now alone and a "This phone" section with **Sign out** just above the danger zone. (3) **The dialog follows where the records stand** (`feature/account/SignOut.kt`): safe in the account → Sign out; unsynced + plan can upload → Sync first / Sign out anyway; unsynced + plan can't upload (Pro lapsed) → **Save a backup** / Sign out anyway; **never claimed by an account or Shared Space → Keep them here / Remove and sign out** (F-2 fixed: free users signed in for Smarter reading lost everything, "Sync first" only closed the dialog, and the text promised a download). (4) `AccountBoundary.ledgerIsPhoneOnly` + `signOutKeepingLedger` (re-checked under the sync lock; ends the session with the revoke record, stops sync, forgets Shared cache and the AI consents; no wipe). (5) **Welcome-screen note** after a sign-out or account deletion (`SignOutNotice`, written before the wipe, cleared if it fails). (6) `ConfirmDialog`: text scrolls when it can't fit; with a second action the three buttons stack full width (they wrapped with a gap and cut Cancel off). CLAUDE.md privacy rule and docs/SYNC.md updated; Privacy Policy unchanged (still accurate) | Uncommitted on `feature/sign-out` (worktree `../lucento-sign-out`, from `origin/main` `50727be`). No Room, Supabase or legal-text change | **1370/1370** unit tests (`SignOutTest` 9 new, `AccountBoundaryTest` +5), `assembleDebug`, `compileDebugAndroidTestKotlin`. On the API 28 AVD: `SignOutConfirmationTest` (6, new) + `CloudSyncSectionTest` (5) **11/11 at 100 % and 11/11 at 200 %** font (shots: four dialogs, profile row, Account page; dialog text scrolls to its end at 200 %). `CloudSyncSectionTest`'s two signed-out/mismatch assertions were already stale on main ("Continue with Google", new mismatch text) and are fixed. **Not run**: the ViewModel paths with a real signed-in account (Keep, Remove, Save a backup, the welcome note), TalkBack, API 36, dark theme |
| **Pro badge and Pro card (2026-11-09, owner chose options A + E from the proposal page)**, same branch: Pro and Lifetime members get the gold crest coin on the profile button (2026-11-14, owner: dropped from the button for the Pro ring alone; the menu chip and the Free card stay) (`ProCrest`, top-right inside the 48dp button) and a Pro/Lifetime chip after their name in the profile menu (`MemberChip`, new `SheetHeader.titleBadge`), with a gold Lucento Plan tile; Free users get the Lucento Pro card in place of the plan row (`ProInviteCard`). `ProCopy.memberMark` / `offersPro` / `inviteLines`. Also: the Shared pill could be cut to "Share" by the 48dp button; it may now be a few dp wider. MONETIZATION §6.1 updated | Committed to PR #37 | `testDebugUnitTest` **1265/1265** (+3 `ProCopyTest`), `assembleDebug`; rendered on API 28 in Sunrise, Midnight and 1.5× text (`artifacts/pro-badge/`, from an uncommitted preview test). Not checked: the full profile sheet on a device with a real plan |
| **Owner test round 3 (2026-11-09)**, same branch: a Free account invited to a Shared Space chose "I already have Lucento" and went round in a loop: "You're signed in" offered only **See Lucento Plan** or **Use Lucento without this account**, which signed out and went back to the welcome screen. The plan was asked before the account was looked at. Now, on a plan without cloud restore, the account is checked first (read-only `probeAccount`): nothing saved → "Nothing to restore yet" with **Set up as new** (stays signed in; `restoreStageWithoutCloud`); data, or a check that couldn't finish → the plan page as before. A waiting invitation is offered on both pages (joining uses the Owner's plan). The empty page no longer promises "it syncs when you finish" (not true on Free) | Committed to PR #37 | `testDebugUnitTest` **1262/1262** (+2 `OnboardingDataSafetyTest`), `assembleDebug`, `compileDebugAndroidTestKotlin` passed locally. Not checked on a device |
| **Owner test round 2 (2026-11-09, release APK 0.5.0)**, branch `feature/owner-test-round-1`: (1) Ask Lucento: questions plainly not about money get a one-line answer on the phone (`AskScope.topic`, no AI allowance spent); purchase questions ("Should I buy…", "Can I afford…") get a phone-calculated **Purchase check** when Gemini is off or unreachable (`PurchaseCheck`, docs/CALCULATIONS.md); the failures were Gemini capacity (Vercel logs: every Flash model 503 or timed out), fixed in lucento-website #20 (11 s cap per call but the last, Flash-Lite third, 404s skipped; prompts refuse off-topic questions and answer purchases from the person's figures). (2) Forgot password: an address Supabase refuses ("name@gmail..com", `validation_failed`) said "check your connection" — now "doesn't look right", offline and send-failed are told apart, the form's own check refuses empty domain parts, and the sheet says "If … has a Lucento account, a code is on its way" (Supabase never says whether an account exists). (3) The link-only reset email and the verify link opening the homepage are production settings: OT-20 (Reset password template) and OT-21 (Site URL = `/auth/confirmed`, a page that already says "go back to Lucento"). (4) After a password reset on "I already have Lucento", a Free account landed back on "Sign in to your account": signing in worked, but restoring is Pro and the reason was small grey text under the sign-in form. Now its own page (`RestoreStage.PLAN_NEEDED`): "You're signed in", **See Lucento Plan** (the Plan page inside setup; back from it, a plan that now includes restoring carries on by itself, `planClosed`) and **Use Lucento without this account**; any restore message is a warning note. `RestoreFlowTest` +1, and its stale FAILED test updated (copy changed long ago; CI doesn't run this class): 6/6 on API 28 | Committed; owner to retest on the CI release APK after OT-20, OT-21 and website #20 | `testDebugUnitTest` **1260/1260** and `assembleDebug` passed locally; new `AskScopeTest` (11), `PasswordResetTest` +2. lucento-website: lint, `astro check` 0 errors, `test:unit` 31/31 (+4 `gemini.test.ts`), build. Probed Supabase `/recover`: unknown addresses get 200 `{}` on both projects; malformed ones 400 `validation_failed`. Not checked on a device |
| **Owner test round 1 (2026-11-09, release APK 0.5.0)**, branch `feature/owner-test-round-1`: (1) Lucento Shared checks the plan — Free shows the Pro note instead of an invite form the server refuses (`max_partners` 0 without SHARED); invite refusals show under the button; (2) "Once" in the schedule editor (end date = first date, no schema change); (3) Ask Lucento: a **Gemini** switch in the top bar and a one-time consent pop-up the first time a question needs AI (`ask_ai_offer_shown`); (4) a **Smarter reading** switch above the composer field (shared consent copy `SmartReadingCopy`); (5) the Pro page is **Lucento Plan** with a Feature · Free · Pro · Lifetime table (stacked at font scale > 1.3); (6) sign-up asks for the password twice ("Passwords match"); **Forgot password?** emails a code (`supabase/templates/reset-password.html`, OT-20) and signs in through the normal path; policy lists the reset email; (7) **Secured** award for turning on Lock Lucento (`app_lock_used`, kept once earned) | Built; owner to retest on the CI release APK | `testDebugUnitTest` 1247/1247 and `assembleDebug` passed locally; new tests `SecuredAwardTest`, `OneTimeScheduleTest`, `PasswordResetTest`. Emulator (API 28, debug): Plan table, Once editor and list label, Secured medal, confirm-password field, Forgot password sheet checked. Not checked on a device: the Gemini and Smarter reading switches and the Shared gate (need a signed-in release build), sending a real reset code |
| **Account emails (2026-11-08, owner request)**: welcome and Pro confirmation emails, plus the refreshed verify template. Android: `data/account/WelcomeEmail.kt` (one request per signed-in account, settled on 200; `ACCOUNT_WELCOME_URL` beside the billing URL), migration `20261110090000_account_emails.sql` (`account_emails` + service-role `claim_account_email`/`release_account_email`), privacy text updated (`LegalDocuments.VERSION` 2026-11-09, website legal regenerated), `confirm-signup.html` without "no ads", docs/EMAILS.md. Website `feature/account-emails`: `src/server/email.ts`, `email-templates.ts`, `POST /api/account/welcome`, Pro confirmation in `/api/billing/verify`, PNG mascots in `public/email/` | Committed on `feature/onboarding-sunrise-path` (android) and `feature/account-emails` (website). Needs OT-19 (migration, Resend key, service-role key) before any email goes out | sql-verify `account_emails_suite` **16/16** (both privilege defaults), `account_deletion_suite` 10/10; website lint, `astro check` 0 errors, **27/27** unit tests, build; Android `WelcomeEmailRequesterTest` (see PR). Rendered welcome and trial emails checked in a browser. **Not run**: a real send through Resend, a real Play purchase |
| **Onboarding redesign "Sunrise Path" (2026-11-08, owner-approved: header A, the three behaviour additions, the welcome link row, coloured group icons)**, branch `feature/onboarding-sunrise-path` (stacked on the uncommitted awards work). Mockups `artifacts/onboarding-redesign/proposal-2026-11-08/`. New `core/designsystem/component/SetupKit.kt`: `PathRail` (back · stage segments · "Personalize · 2 of 5" · Skip, replaces `StageIndicator` in onboarding and the guide), `SetupTitle`, `ActionDock`(+`DockCount`), `SelectChip`, `OptionRow`, `OptionTile`, `ChoiceCard`, `SummaryTile`, `CheckMark`/`RadioMark`, `SpotIcon`, `FactTick`; `Hierarchy.stepTitle/milestoneTitle/lead/groupLabel`; `LucentoPrimaryButton(trailingIcon)`; `MascotMood.Pointing`. Every onboarding step and guide page restyled (no icon tile over titles); welcome full-bleed landscape; summary 2×2 tiles that jump back (`goTo`), "Up next"; accounts quick-add rows open the editor preset to Cash/Bank/E-wallet (Credit card/Loan on cards & loans), starting-balance card (main currency only); budget hero with "x of 2 set", split bar + named shares; goal ideas prefill the name (`NewGoalSheet(initialName)`); composer example page. Category picker (also the Budget tab's) uses `SelectChip` + coloured group headers. Domain: `SetupProgress.screen/screens/caption/fill`, `GuideStep.stage`, `GuidedSetup.setUpScreens/screenOf/startingBalance/startingBalanceAccounts/sharePercents`. Tour/showcase/reconciliation texts updated | Committed 2026-11-08 (`221983f`) on `feature/onboarding-sunrise-path`, merged to `main` with the release-candidate PR | **1232/1232** unit tests (+4: `GuidedSetupTest` 3, `SetupProgressTest` 1), `assembleDebug`, `compileDebugAndroidTestKotlin`; lint: only the local `local.properties` path error, no warnings in changed files. **`ScreenshotTourTest` PASS on API 28 in dark mode** (`-e demoDark true`). Manual pass on API 28 (`artifacts/onboarding-redesign/implementation-2026-11-08/`, `COMPARISON.png` vs mockups): full fresh-install walk, backup states, quick-add preset, starting balance, budget hero live total/pill/split, dock above the keyboard, goal name prefill, Back on every page, 150% text. Found and fixed on device: dock fade dimming welcome text, muddy/oversized accounts scene on a 731dp screen, long budget lead, wrapped link-row separator. AVD data backed up and restored. **Not run**: TalkBack, API 36, 320dp width, the email account form restyle (kept the shared `EmailAccountForm`) |
| **Dock polish (2026-11-06)**: tab content no longer ends in a hard horizontal cut at the top of the dock's + (it sliced cards mid-line, e.g. Budget's "unbudgeted" note): it fades into the background over the last 20dp above the dock (`LucentoRoot`, only while the dock shows). `docs/DESIGN.md` corrected: every tab shows its label unless they don't fit (it said unselected tabs were icon-only). **Explored and declined:** a "Crest Dock" restyle (crest-shaped surface holding the +, gliding indicator, rising icon) — built and verified on device, but the owner prefers the existing dock (2026-11-06), so `NavigationDock.kt` is unchanged from `origin/main`. Screenshots of the exploration stay in `artifacts/crest-dock/` | Folded into the release branch on 2026-11-08 (from worktree `../lucento-crest-dock`). Files: `navigation/LucentoRoot.kt`, `docs/DESIGN.md`, handover docs. No Room / Supabase / backup change | On API 28 with this build: `ScreenshotTourTest` **PASS** (55 shots, `artifacts/dock-fade/tour/`; `15-budget.png` shows the fade where the cut was), dark Home + Budget (`artifacts/dock-fade/01-…`, `02-…`), `assembleDebug`, **1088/1088** unit tests (executed, not from cache). Lint was run on the Crest build (0 errors / 9 warnings, same set); not re-run since, as the only remaining source change is the 12-line fade. Host note: Gradle daemons crashed twice from low memory with the emulator running — use `--max-workers=2` / a 3 GB heap |
| **Awards — milestones reimagined as medals with a mascot ceremony (2026-11-08, owner request)**, branch `feature/awards-ceremony` from `origin/main` `39611f5`. New `feature/awards`: `AwardMedal` (Canvas medal on a neck ribbon — struck gold, milled rim, engraved emblem per award, ribbon dye from `AwardArtwork` in Color.kt; unearned = flat outline, dashed rim, progress arc), `AwardsSheet` (the cabinet: two medals per shelf, how to earn each, "3 of 7 days"; earned medals replay their ceremony), `AwardCeremony` (modal card: the mascot in its Success pose presents the medal; complete from the first frame, then pop + swing, sunburst, one confetti burst, shine; confirm haptic; still under reduced motion; Thanks, Lucento / See all awards). Home: the Milestone toast is replaced by the ceremony; awards earned together share one ceremony (`Achievements.toAcknowledgeAll`, "Also earned"); none over the open cabinet; shortcut renamed Awards; guide's "Awards earned" shows small medals. Domain: `Achievement.howTo`, `AwardProgress`, `Achievements.progress`. Tour + website showcase acknowledge awards before seeding; tour adds `07a`/`07b`. Goal and budget toasts unchanged | Committed 2026-11-08 (`221983f`) on `feature/onboarding-sunrise-path`, merged to `main` with the release-candidate PR | **1228/1228** unit tests (+2 new tests, +1 batching case), `assembleDebug`, `compileDebugAndroidTestKotlin`; lint: 1 error, only the machine-local `local.properties` path escape (not in git), nothing in new code. On the API 28 AVD (`artifacts/awards-ceremony/`): cabinet light + dark, unearned medals with 1-of-7 arc, replay ceremony (reduced motion still, and animated), a **real** First Step ceremony after saving a first transaction on a fresh install (3 of 6), guide "Awards earned", 150% font for cabinet + ceremony. Found and fixed on device: navy ribbon invisible in dark → plum; sunburst behind text; empty stage on slow frames → complete from the first frame; last shelf under the nav bar. AVD data backed up, wiped for the fresh-install pass, and restored. **Not run**: `ScreenshotTourTest` (CI), TalkBack, API 36 |
| **Google Play readiness (2026-11-07, owner request)**, branch `feature/play-readiness` from Phase 4: (1) the uncommitted account-onboarding work from `../lucento-onboarding` brought in (legal texts, email verification, account status); (2) **account deletion**: `public.delete_my_account()` (`20261109090000_account_deletion.sql`, cascades every user-owned table, frees Play purchases for a new account), Settings › Cloud sync › Delete account (`feature/account/AccountDeletion.kt`, `DeleteAccountDialog`, `AuthRepository.deleteAccount`, server first, then the sign-out wipe), docs/ACCOUNT_DELETION.md; (3) Terms and Privacy Policy cover Lucento Pro purchases, account deletion and the contact lucentoapp@gmail.com (`LegalDocuments.VERSION` 2026-11-07); (4) `tools/legal/export_legal.py` generates the website's legal data from the app; (5) docs/play/PLAY_STORE_SUBMISSION.md: Data safety answers, App content, listing text, Google sign-in SHA-1s, testing tracks. Website `feature/site-refresh` (from `feature/play-billing-verify`): /pricing, /terms, /delete-account, generated /privacy, home and guides brought up to date | Uncommitted | JVM 1226/1226; sql-verify account_deletion 10/10 (both defaults); website `npm run verify` green; emulator runs in the handover notes |
| **Lucento Pro — Phase 4, Google Play Billing (2026-11-07)**: Billing Library 9.1.0 (`PlayPurchaseSource`: subscriptions and Lifetime, pending payments, signature check against Play's licence key, full re-read on every Play callback, `obfuscatedAccountId` = sha256 of the Lucento user id); `EntitlementManager` confirms each purchase (server `/api/billing/verify` when signed in, then acknowledgement with Play, retried at the next refresh); buy buttons from `ProOffers` (trial first, Switch to Lifetime for subscribers, Lucento Cloud for Lifetime owners after their year, pending replaces buttons); test builds' **Test store** switch. Server: `20261108090000_play_purchases.sql` (`apply_play_purchase`, `revoke_play_purchase`, service role only). Website `feature/play-billing-verify`: `verify`, `rtdn`, daily `voided` cron. Owner checklist: `docs/monetization/PLAY_CONSOLE_SETUP.md` | Committed on `feature/lucento-pro-billing` (stacked on Phase 3) and `lucento-website` `feature/play-billing-verify` (stacked on `feature/plan-aware-ai`). **Not device-tested with Play**: no products exist in Play Console yet. No Room change. Target SDK 36 on its own branch `chore/target-sdk-36` | **1202/1202** unit tests (+31: `PurchaseConfirmationTest` 8, `ProOffersTest` 8, `PlayPriceSelectionTest` 5, `PurchaseSignatureTest` 4, `BillingProblemTest` 3, `TestStoreTest` 3), `assembleDebug`, `assembleRelease` (R8 keeps the billing library), `assembleDebugAndroidTest`, lint 0 errors / 9 warnings (same set); `tools/sql-verify` 154/154 under both privilege defaults (new `play_purchases_suite.sql` 30/30); website `npm run verify` (lint, astro check 0 errors, 9/9 unit tests, build); native libraries 16 KB-aligned (64-bit ABIs). `ScreenshotTourTest` **PASS** on the API 28 AVD (63 shots in `artifacts/lucento-pro/tour-phase4/`; new 24g: Pro page with test-store buttons, 24h: after a test Lifetime purchase, confirmation under the plans). The first run failed on the emulator's own "Pixel Launcher isn't responding" dialog (tapped Wait, re-ran) |
| **Lucento Pro — Phase 3, the Pro experience (2026-11-06)**: Lucento Pro page (`feature/pro/ProScreen`: plan status, the lock that led there, comparison from `PlanCatalog`, plan cards with Play prices or an honest "not on sale yet" note, the Lifetime promise, Restore purchases, Manage subscription, test-build plan simulator), entry points (profile menu, Settings hub, every lock), one lock pattern (`ProNote`: inline note + "See Lucento Pro") in the wallet editor, goal editor, goal restore, Insights, the report sheet, Settings › Account, the theme picker, the paused-sync sheet; `ProBadge` on locked themes and wallet types; the one-time plan-ended notice. Fixed from Phase 2: a refused goal restore confirmed and closed; the tour waited for "Save as PDF", which Free replaces with a note (it now runs on a simulated Pro plan) | **Uncommitted** on `feature/lucento-pro-ui` (branched from Phase 2 `2d925a7`). No Room or Supabase change | **1171/1171** unit tests (+15: `ProCopyTest` 8, `PlanNoticesTest` 3, `PlanSimulatorTest` 3, `EntitlementManagerTest` +1), `assembleDebug`, `assembleDebugAndroidTest`, lint 0 errors / 9 warnings (same set). `ScreenshotTourTest` **PASS** on the API 28 AVD (61 shots in `artifacts/lucento-pro/tour/`, new 24a–24f: plan ended, Pro page ×3, Insights on Free ×2); two earlier runs failed on the emulator's own "System UI isn't responding" dialog and two on tour steps of mine (fixed: the Pro page's top bar scrolls away, so the tour leaves with the system back) |
| **Lucento Pro — Phase 2, gates (2026-11-06)**: the plan is live in the app. `AppContainer.planGate` asks `EntitlementManager` (cache first); `SupabaseEntitlementRemote` reads `public.entitlements` (start, sign-in changes, every foreground); `ProGates` (pure) decides sync/restore by plan (a Partner syncs on the Owner's plan), Insights periods and composition, PDF, themes. Foreign-currency wallets gated on create and on a currency change (`mainCurrency` in `WalletRepository`); sync pauses by plan (`backgroundSyncAllowed` / `shouldSyncOnLaunch` gained `planAllows`; application, `SyncWorker`, `ForegroundSync`), Settings refuses to turn it on and says why, Home chip `PAUSED` with its own sheet copy; onboarding's cloud restore and end-of-setup sync-on re-read the server then ask the plan; Insights 6/12 months and the breakdown lock with a note; report sheet shows a note instead of Save/Share; theme picker marks and explains locked themes, nothing persisted; AI clients: `403 pro_required` → on-phone reading. Website `ask.ts`/`interpret.ts` → `consume_ai_request` (branch `feature/plan-aware-ai`, committed, not pushed, not deployed) | On `feature/lucento-pro-gates` (from `fix/plan-limits-rls`, PR #23 open). Not pushed yet. No Room or Supabase change | **1156/1156** unit tests (+12: `ProGatesTest` 4, `EntitlementRowsTest` 4, `SyncPausedByPlanTest` 3, `PlanGateRepositoryTest` +1 foreign-currency case), `assembleDebug`, lint 0 errors / 9 warnings (same set). **Not run**: `ScreenshotTourTest`; a device with a Free account (every tester on lucento-test holds a GRANT, so the gates are invisible there until Phase 3's simulator or a sign-out) |
| **Lucento Pro — Phase 1, entitlement architecture (2026-11-06)**: the owner approved monetization v2 (`docs/MONETIZATION.md`: Free / Pro Subscription / Pro Lifetime, decisions D-1…D-18 all as recommended; customer copy in `docs/monetization/CUSTOMER_COPY.md`). Built, with **no behaviour change**: `domain/entitlement` (`Capability` A/B/C/D, `Entitlement` rows with status, expiry and verifier — never a Boolean; Lifetime's `LOCAL_PRO` never expires; 3-day offline grace for dated rows; `PlanCatalog` holds every approved limit; `EntitlementEvaluator` → `Access`; `EntitlementMerge` = Play vs server precedence; `PurchaseInterpreter` incl. Lifetime's 12 months of services from the purchase time), `data/billing/PurchaseSource` + fake, `data/entitlement` (sealed DataStore cache `entitlement_preferences` with an Android Keystore HMAC, `EntitlementManager` StateFlow), `EntitlementGate` in `WalletRepository` (money / debt / receivable / valued-asset kinds counted apart) and `GoalRepository` (`restore()` now returns `RestoreResult`), `LocalEntitlements` provided by MainActivity. The app wires `EntitlementGate.Permissive`, so every check still answers Allowed. Server draft: `supabase/migrations/20261107090000_entitlements.sql` (entitlements, purchase_links, plan_limits, `has_capability`, plan-aware `max_partners`, `consume_ai_request`) — **not applied anywhere**; endpoint design `docs/monetization/SERVER_CONTRACT.md`. **Later the same day**: merged to `main` as PR #22 (`1bf0d5f`, owner: "Merge"); the owner applied the migration + grants to **`lucento-test`** (NEXT_TASKS LP-2, verified live) | Committed on `feature/lucento-pro-architecture` (from `main` `15e7a46`, after the owner-approved fast-forward; local `main` had been two merges behind). Not pushed, no PR. No Room change | **1144/1144** unit tests (+56: `PlanCatalogTest` 7, `EntitlementEvaluatorTest` 10, `EntitlementMergeTest` 8, `PurchaseInterpreterTest` 7, `EntitlementCacheTest` 7, `EntitlementManagerTest` 9 — incl. Lifetime 60 days offline with Play and server unreachable, revoke, pending, tampered cache — `PlanGateRepositoryTest` 8), `assembleDebug`, lint 0 errors / 9 warnings (same set as before). `tools/sql-verify`: `entitlements_suite.sql` **38/38**, Shared **69/69**, multicurrency **17/17**, under both privilege defaults (the two older suites gained a GRANT fixture because `max_partners` is now plan-aware). **Not run**: `ScreenshotTourTest`, a device (nothing visible changed) |
| **Account onboarding (2026-11-06, owner request)**. (1) **Keep your money safe comes right after Get started**: two cards, nothing preselected; choosing one opens what follows. *Sync with an account* shows Continue with Google, Use email instead and "By continuing with Google, you agree to the Terms of Use and the Privacy Policy". *Keep it on this phone only* says a backup file can be exported from Settings and shows Continue. "Back up to a file" is no longer a separate choice. No stage bar on this screen; usage step numbers keep their old meaning. (2) **Use email instead** opens a create-only page: email and password with icons, show/hide, a live "At least 8 characters" tick, the breach-check line, and a required "I agree to the Terms of Use and the Privacy Policy" checkbox. Problems show on their own field after it is left or submitted. "I already have Lucento" signs in only; "I was invited to share" can do either. (3) **Check your inbox** dialog (`feature/account/AccountStatusDialog.kt`, `EmailVerification.kt`): "We sent a welcome email to …", Open email app, Send again (60 s countdown), Use a different email. It detects verification by itself: it signs in with the new password on return to the app and every 15 s while on screen, never in the background, with the password in memory only (asked for again after a process death). Then **Email verified** (or **You're connected** after Google), "Getting things ready…" while the account is checked, Continue or automatic after 2.2 s (not with TalkBack), then Personalize. (4) "Create account" for an address that already has one tries the same password and carries on, otherwise explains. A failed email send now says so. (5) In-app **Terms of Use** and **Privacy Policy** (`feature/legal/`; drafts, docs/LEGAL.md), also under Settings › Privacy › Legal. A new account records `terms_version` and `terms_accepted_at`. Settings › Cloud sync uses the same form and dialog. (6) A summary that can't check the account now offers Try again or Finish without syncing in place, instead of sending the person back to step 1. (7) Branded "Welcome to Lucento" confirmation email: `supabase/templates/confirm-signup.html` (owner pastes it, NEXT_TASKS OT-13) | Uncommitted on `feature/account-onboarding` (worktree `../lucento-onboarding`, from `main` `15e7a46`). No Room or Supabase schema change | **1105/1105** unit tests (+17: `EmailVerificationTest` 10, `AccountFormTest` 6, step numbers +1), lint 0 errors / 9 warnings (same set), `assembleDebug`, `compileDebugAndroidTestKotlin`. **`AccountStatusDialogTest` 6/6 on API 28** (inbox with countdown, sent again, password after a restart, verified while checking, verified + Continue, Google connected in the night theme). On API 28 against lucento-test (`artifacts/account-onboarding/`): new order, both cards opening, create page, validation, Terms sheet, show/hide, consent, restore path sign-in only, invited path with both modes, Keep it on this phone only → "Step 1 of 3, Personalize". **Real sign-up blocked by Resend** (KNOWN_ISSUES AO-K1: the key isn't allowed to send from `mail.lucentoapp.com`; Supabase rolls the account back, so nothing was created). The verification itself and Google's "You're connected" on a new account are **not seen end to end**. Found and fixed on the device: the email error stayed visible after switching to "phone only". APK `artifacts/apk/lucento-debug-account-onboarding-2026-11-06.apk` |
| **Owner round 2 (2026-11-06)**: OT-8 and OT-9 "good for now", OT-12 approved. (1) Bank-branded wallet cards (wallet page, New wallet preview) cut the bank's name — "Bank of Co…": a spacer in the top row took half the free width; the mark and name now take the whole row beside the type chip and wrap to two lines. (2) Lucento Shared: an invitation for an account with its own ledger showed only in Settings (LS-K13); `AppViewModel.refreshSharedSpace` now also runs when the account changes or finishes onboarding and on every return to the foreground (`LifecycleEventEffect(ON_START)`, at most once a minute). (3) The guided setup's wallet rows show cents at full size | On `feature/multicurrency` (PR #21) | **1088/1088** unit tests (+1 `SharedSpaceRepositoryTest`), `assembleDebug`, `compileDebugAndroidTestKotlin`, lint 0 errors / 9 warnings (same set); APK `artifacts/apk/lucento-debug-multicurrency-2026-11-06-r3.apk`. On API 28 with the previous APK: the New wallet preview read "Bank / of Co…" (`shots`); after installing the fix over it: "Bank of Commerce" on one line beside "BANK ACCOUNT", and on two full lines at 130% font. **Not run**: the invitation path on two accounts |
| **Multicurrency — owner test round 1 (OT-11, 2026-11-06)**. (1) Welcome: "Get started" / "I already have Lucento" / "I was invited to share" (straight to choosing the invited account: "Sign in to join") / "Restore a backup file"; the returning-user sheet is gone; the artwork shrinks on short screens so nothing hides behind the buttons. (2) New wallet and Transfer sheets stay open however they're scrolled or pulled; only Back (or Cancel) closes them (`rememberStickySheetState`, `dragToDismiss = false`: Material 3 1.3 has no gesture switch). (3) Card faces give a wallet's name two lines ("Bank of Commerce"). (4) "≈ ₱…" under every amount in another currency — wallet rows, card faces, the wallet page card, transaction rows and details (`FxDisplay`, one app-wide average-method book via `LocalFxDisplay`); Assets / Liabilities subtotals are one ₱ figure (a wallet without a rate stays apart). (5) "Get exchange rates online" is on by default (owner decision; CLAUDE.md privacy section records the exception; requests only for a currency other than the main one). (6) Hero amounts show cents at full size on Insights, Budget, Goals, Debts and Ask, as on Home and Wallets | Committed on `feature/multicurrency` with stage 1 | **1087/1087** unit tests (+4 `FxDisplayTest`), `assembleDebug`, `compileDebugAndroidTestKotlin`, lint 0 errors / 9 warnings (same set); APK `artifacts/apk/lucento-debug-multicurrency-2026-11-06-r2.apk`. On API 28 (USD ledger): New wallet sheet survived hard flings and a long scroll + fling past the top; a PHP wallet got today's rate online by itself (1 PHP = $0.015845); row "PHP50,000.00 / ≈ $792.25", card face the same, Assets subtotal $18,184.26 (= $17,392.01 + $792.25); Insights hero "+$5,742.01" with full-size cents; welcome (after `pm clear`) shows the four ways in, and "I was invited to share" opens "Sign in to join". **Not seen**: a foreign transaction row, a foreign liability, dark theme, 200% font |
| **Multicurrency, stage 1 (2026-11-06)** (owner request 2026-11-05, recommendations accepted; docs/MULTICURRENCY.md, CALCULATIONS.md "Exchange rates"). Main currency locked (Settings shows it; onboarding says so) and kept on the server per ledger (`ledger_settings`, adopted by every phone and Partner). Foreign wallets need an opening rate; each movement stores its value in the main currency (`FxSource`: market / entered / last rate / average / transfer). Average method over every foreign wallet, cards and loans included (`FxLedger`), realized FX gains → income, losses → expenses (Insights, reports; never budgets). Totals in the main currency on Home, Wallets, Budget, Transactions, Insights and Ask (`FxTranslation`). Composer: "≈ ₱…" per foreign row, day rate online, else the "Last rate / Average rate" choice. Transfers between currencies ("They receive"). Wallet page: value in ₱, average, FX gains so far, or "Set rate". Free rates (currency-api on jsDelivr → mirror → Frankfurter/ECB), opt-in in Settings › Money, cached per day. Room v13, backup format v4, Supabase `20261106090000_multicurrency.sql`. Not built: month-end revaluation (MC-2) | Committed on `feature/multicurrency` (from `feature/lucento-shared` `3102038`) with the owner's round 1 (row above). Migration on `lucento-test` (OT-10, verified); production waits on approval (OT-12) | **1083/1083** unit tests, `assembleDebug`, `compileDebugAndroidTestKotlin`, lint 0 errors / 9 warnings (same set) — lint caught `BigInteger.longValueExact` (API 31; min is 26: it would have crashed converting on Android 8–11), replaced with BigDecimal's. APK `artifacts/apk/lucento-debug-multicurrency-2026-11-06.apk`. New: `FxLedgerTest` 13, `FxRateTest` 6, `OnlineFxRatesTest` 4, `MulticurrencyRepositoryTest` 7, `MulticurrencySyncTest` 7, `BackupTest` +1. `tools/check_migrations.py` 12 → 13 OK; `tools/sql-verify` multicurrency 17/17 and Shared 69/69 under both privilege setups (it caught a CHECK that let a value without its currency through; fixed). `MigrationTest.migrate12To13…` written, not run (CI). Not seen on a device |
| **Lucento Shared committed (2026-11-06)**: `3102038` on `feature/lucento-shared` (owner-authorised, not pushed) | Committed | 1045/1045 at commit |
| **Owner feedback round (2026-11-05, evening)**. (1) Indicator: the Home "Shared with …" chip and the initial badge are replaced by a small "Shared" pill on the profile button (every primary screen; group glyph alone above 130% font). (2) Profile photo: Google picture by default (`ProfilePhotoChoice`: GOOGLE / OWN / NONE) — fetched after sign-in when none is on the phone; a picked photo or a removal is respected; the account wipe resets to GOOGLE (it used to leave no photo after every sign-in). (3) Sign-in and account switch check the connection first (`offlineFailure`): offline now says so instead of "No Google account could be used". (4) Multicurrency with FX translation: design questions sent to the owner; not started | Uncommitted in worktree `../lucento-shared` | **1045/1045** unit tests (+5 `ProfilePhotoDefaultTest`), `assembleDebug`, lint 0 errors / 9 warnings (same set); APK `artifacts/apk/lucento-debug-shared-2026-11-05-r5.apk`. Onboarding's photo card is now a confirmation ("Getting your Google photo…" → "on your profile"), not an offer. Not run on a device |
| **Lucento Shared — "Shared with …" indicator (2026-11-05)** (superseded the same evening by the "Shared" pill, row above). Owner decided the workspace model stays A (one ledger = the Shared Space; partners' entitlements will follow the Owner's plan; RISKS_AND_DECISIONS §G.11). Once someone has joined, both phones show it: a "Shared with Jerald" / "Shared with Rachel" chip under Home's header (opens Lucento Shared), the other person's initial as a badge on the profile button on every primary screen (TalkBack: "Profile and settings. Shared with …"), and a Lucento Shared row in the profile menu. `SharedAccess.sharedWith` decides: none when solo or when only an invitation is out | Uncommitted in worktree `../lucento-shared` | **1040/1040** unit tests (+1 `SharedSpaceDomainTest`), `assembleDebug`, lint 0 errors / 9 warnings (same set); APK `artifacts/apk/lucento-debug-shared-2026-11-05-r4.apk`. **Not seen on a device** (needs a joined space) |
| **Test-build account switch + Shared cache fix (2026-11-05)**, for one-phone testing. Debug builds only (`BuildConfig.DEBUG`): profile menu › **Switch account** opens Google's account picker at once (`SettingsRoute(switchAccount)`), also a button in Settings › Account & sync. It reuses the ordinary account switch (`AccountBoundary`: confirmation when changes haven't synced, wipe, onboarding restores the chosen account and its Shared Space). Found while adding it: Lucento Shared's cache survived an account change (LS-K12); the account wipe now clears it (`SharedSpaceStore.clearAll`). Workspace model (A/B) awaits the owner (NEXT_TASKS LS-8) | Uncommitted in worktree `../lucento-shared` | **1039/1039** unit tests (+3 `SharedSpaceLedgerSwitchTest`), `assembleDebug`, lint 0 errors / 9 warnings (same set); APK `artifacts/apk/lucento-debug-shared-2026-11-05-r3.apk`. Not run on a device |
| **Lucento Shared — first two-device run follow-ups (2026-11-05)**. The owner's run stopped at joining (KNOWN_ISSUES LS-K10). The server log showed no accept, only a `create_shared_space` from the invited account. Fixed: Home's card opens the invitation's review directly (`SharedSpaceRoute(reviewInvite)`). A waiting invitation leads Settings › Lucento Shared in a new eye-catching `SpotlightCard` (hero panel, sunlight pill, `PeoplePair` initials, accent action, landscape strip; also atop the review). "Start my own Shared Space instead" is a link. An Owner of an empty space sees invitations to them and can join (the server closes the empty space; the card and review say so). Wallets reconcile note now says "Open the wallet › Adjust balance" (LS-K11). Reminder recorded: modernize the share message + Play Store link before release (LS-7) | Uncommitted in worktree `../lucento-shared`; no server change | **1036/1036** unit tests (+6 `InvitationEntryTest`), `assembleDebug`, lint 0 errors / 9 warnings (same set, none in new code); APK `artifacts/apk/lucento-debug-shared-2026-11-05-r2.apk`. **Not seen on a device yet**: the new card (needs a signed-in invited account) |
| **Lucento Shared — implementation (2026-11-05)**: one Owner's ledger shared with a Partner on their own account (docs/shared; database as built in `docs/shared/SECURITY_MODEL.md`). **Server:** `shared_spaces` / `space_members` (one row per Partner, `unique (partner_id)`) / `space_invites` (7 days, verified email), ten `security definer` RPCs under ordered advisory locks, limit `lucento_private.max_partners()` = 1 (the future entitlement hook; members + pending invitations count), Partner policies beside untouched owner policies (read all seven tables; insert/update `transactions` only), server-stamped `created_by`/`updated_by`, `wallets.classification` label, least-privilege grants. **App:** Room v12 (`MIGRATION_11_12`), sync binding `sharedSpaceOwnerId` + env seam, per-cycle membership check → `MembershipRevoked` → shared ledger removed and the person back to their own account, transactions-only Partner push, `StructureAccess` guards, Settings › Lucento Shared (create, invite + share sheet, withdraw, remove, close, leave, review/join/decline, "joined on another phone"), Home prompt, onboarding (adopts an existing membership, offers an invitation to a new account, notice after a space leaves the phone), Partner gating across Wallets/Budget/Goals/Scheduled/Debts/categories, "Added by / Edited by" in transaction details, wallet labels. Dev tool `tools/sql-verify` (PGlite) | Uncommitted on branch `feature/lucento-shared` in worktree `../lucento-shared` (from `main` `398bd8b`). **Migrations on `lucento-test`** (owner, 2026-11-05, receivables first) and verified there; production untouched (NEXT_TASKS OT-8, OT-9). Nothing committed, pushed or merged | **1030/1030** unit tests (+65 Shared: sync 19, repository 25, domain 12, guards 6, ledger switch 3), `assembleDebug`, `compileDebugAndroidTestKotlin`, `tools/check_migrations.py` 11 → 12 OK, schema `12.json` exported by the build. Database: `tools/sql-verify` **69/69** on Postgres 17 under lucento-test's and Supabase's stock default privileges. Live on `lucento-test` (read-only catalog check, SECURITY_MODEL.md §8): all 16 function bodies identical to the repo; grants, RLS, the 9 Partner policies, columns, constraints and triggers as designed; advisors show only expected findings. **Not run**: `MigrationTest.migrate11To12` (emulator, CI), `ScreenshotTourTest`, any device or two-device test (OT-8) |
| **Theme system — themed Home balance card (2026-11-05, owner follow-up)**: the net-balance card takes the theme (`ThemeSpec.balanceCard` / `BalanceCardFace`): face gradient, ink, muted ink (labels, line icons tinted, contactless), trend bars, gold or silver chip (`CardChip(metal)`), and the theme's landscape printed tone-on-tone (`silhouetteFilter`, darkening only). Layout and type sizes unchanged; gain/loss colours fixed. Sunrise start `#2768ED → #245BD8` and muted `#C9D7FF → #DCE5FF` to fix a 3.4:1 label contrast (KNOWN_ISSUES TS-K6). Owner confirmed the hero strip and no-backup decisions | Committed on `feature/theme-system` (owner-approved for `main`, 2026-11-05) and merged by PR | New `ThemeContrastTest` case (every face: ink + muted at start / middle / end, gain/loss text and bars past the middle, light-ink rule for the print) — **965/965** unit tests, lint 0 errors / 9 warnings (same set), `assembleDebug`. Not re-run: `ScreenshotTourTest` (it passed before this card change). On API 28: all five cards captured (`artifacts/theme-system/18-card-grove-dark.png`, `19-card-*.png`, `20-home-aurora-light-card.png`) |
| **Theme system (2026-11-05)**, from the Theme Studio exploration (https://claude.ai/artifact/3xambaCBLTQPEbwcb6bYnX): five curated themes — Sunrise (unchanged), Midnight (dark only), Grove, Mono, Aurora (hero gradient). `ThemeSpec` registry (`core/designsystem/theme/Themes.kt`), `BrandColors` + `withBrand` over fixed financial semantics (`Color.kt`), DataStore `theme_id` (`ThemeId`, unknown → Sunrise; device choice, not in backups/sync), `LucentoTheme(darkTheme, theme)` with a 250 ms cross-fade (snaps during launch and under reduced motion), MainActivity system bars follow Midnight. Picker in the profile menu (`feature/profile/ThemePicker.kt`: previews from each spec's tokens over its landscape, Midnight note instead of the mode toggle). Landscapes: 5 WebP panoramas (85 KB total, masters in `docs/design/illustrations/themes/`) as `HeroHorizon` (strip below every `HeroCard` / `StatementPanel`, never behind text), `MascotScene` (empty states), onboarding welcome (Sunrise keeps the approved art). Chart series: per-theme 7-slot categorical tokens; four Theme Studio series were in the colour-blind floor band and were fixed (see KNOWN_ISSUES TS-K2) | Committed on `feature/theme-system` (from `main` `44d0876`; owner-approved for `main`, 2026-11-05) and merged by PR. No Room / Supabase / backup-format change | **964/964** unit tests (+9: `ThemeContrastTest` now every theme × mode, `ThemeInvarianceTest` 6, `DataSeriesTest` 2 — a deliberately restored bad series order failed it with the expected pair), lint 0 errors / 9 warnings (same set), `assembleDebug`. On API 28 (`artifacts/theme-system/`): picker in Sunrise dark, Grove dark → light, Midnight (note shown, stored Light kept), Aurora light Home + Wallets (gradient hero + strip), Midnight Home + Wallets, Sunrise light Budget hero strip, Grove kept across a cold start (force-stop) with Grove night Home + Wallets; `uiautomator` shows each preview as one RadioButton with its description and checked state (~104×96dp). `ScreenshotTourTest` **PASS** (55 shots in `artifacts/theme-system/tour/`; first-run Home panel and night Budget hero show the strip; profile menu shows the picker) — it needs `pm clear` first on a seeded AVD (the first attempt failed at "Start fresh" because the app already had data). **Not verified**: TalkBack spoken output, 200% font in the picker, a large screen, Mono on device, the mascot vignette in an empty state (the tour's empty states use Lottie or weren't captured) |
| **Institution coverage (2026-11-04/05)**: audit (artifact https://claude.ai/artifact/JCUnuix8t28bn8y1NdNGX9) then implementation. Library 386 → 415 (bundled 380 → **409**): 21 Philippine institutions added (EastWest, PSBank, DBP, MariBank — **SeaBank renamed, BSP digital licence Jul 2026**, UNO, UnionDigital, OFBank, ShopeePay, Maybank PH, HSBC PH, AUB, Bank of Commerce, PBB, BDO Network Bank, BPI BanKo, City Savings, CARD Bank, Coins.ph, Sterling, PalawanPay, Veterans Bank → PH 36, all seven BSP digital banks), Taiwan (7) and MariBank SG; logo fixes for Security Bank, LandBank, Maybank, ING ×3 (Metrobank: 2025 colours, no vector exists). `aliases` end to end (library → `build_institutions.py` → `Institution.aliases`); search matches aliases and any script (中國信託), ranks name > alias > shorter name; picker: country chip (opens on the wallet's bank's country, else the device's; home + shown country pinned in the menu), groups by kind / by country, search "In {country}" then "Other countries", "Also known as SeaBank". Tooling: `tools/institutions/expansion/` (+ README); provenance in the library's `documentation/additions_2026-11-04.md` | Committed on `feature/institution-coverage` (`761d0ea`, owner-approved 2026-11-05; not pushed). Asset libraries (outside git) changed; backups in `Desktop/Personal/_lucento-asset-backups/2026-11-04-before-institution-expansion/`. No Room/Supabase change (`institution_id` is an opaque string) | Generator re-run on unchanged sources was byte-identical first; after: 17 new VectorDrawables, **0 new fallbacks**, untouched drawables byte-identical, JSON diff = 29 added + 17 changed (7 fixes, 10 alias-only), assets 2.90 → 3.14 MB uncompressed. **955/955** unit tests (+10: registry 5, picker 5), lint 0 errors / 9 warnings (same set), `assembleDebug`. On API 28 (`artifacts/institution-coverage/`): US default view, pinned country menu, PH grouped by kind, "seabank" → MariBank with alias line, "hsbc" home-first, EastWest / Security Bank / JKOPAY / BanKo / MariBank card faces, 130% font; no wallet saved (net worth unchanged). **Not run**: `ScreenshotTourTest`, light theme on device, Chinese-script typing on device (unit-tested) |
| **Owner review round 10 (2026-11-04)**: Receivable wallets (`WalletType.RECEIVABLE`, a loan in reverse: terms, amber card face with instalments received, Record payment, collections as INCOME schedules linked to the receivable — interest as income, principal as a transfer; `RecurringRepository.markCollected`, `CreditCardMath.receivingWallet`); Scheduled page card payment → filled-in transfer; Wallets without type chips or the net worth formula; Home in the dock opens at the top (`scrollToTop` request); Ask Lucento version 3: `AskCalc` takes `PHP 5000`, `7%`, `^`, the proxy's `SYSTEM_V3` projects with assumed market rates; rates no longer flagged as unchecked amounts | On `feature/owner-round-10` (app) and `lucento-website` PR #10 branch (`SYSTEM_V3`), not committed. No Room schema change; Supabase `20261105090000_receivable_wallets.sql` applied to production by the owner (on `lucento-test` since 2026-11-05); schedule editor restyled ("Schedule transaction", icon sections, filled dropdowns) | **945/945** unit tests (new `ReceivableTest` 7, `AdvisorTest` +3, `BackupTest` +1, `PulledRowRulesTest` +1 case; type-chip test removed); lint 0 errors / 9 warnings (same set); on API 28: `ScreenshotTourTest` **PASS** twice (55 shots, `artifacts/round10/tour/`; one earlier run was stopped by the emulator's own "System UI isn't responding" dialog), `ReconciliationTest` **PASS**; manual (`artifacts/round10/`): Scheduled card payment → $640.00 transfer, Home dock → top / Back keeps place / reselect → top, receivable created with suggested $500 × 12, Record payment → transfer from it into Everyday bank, collection created from the Scheduled page, marked received with $50 interest → owed $5,550, Received $450, 1 of 12. **Not verified**: a live v3 adviser answer (proxy not deployed), a receivable syncing (migration not applied) |
| **Owner review round 9 (2026-11-04)**: Ask Lucento as a real adviser — the proxy prompt gives direct recommendations and prioritised plans, no in-answer disclaimer; the app shows "AI advice can be inaccurate. Check it before you act on it." under every AI answer and in both consents; new advice chips. AI answers failing until Smarter reading was on: no coupling exists (app, proxy and DB checked); fixed the causes instead — `AuthRepository.accessToken` refreshes a stale token, `postToProxy` (Ask + Smarter reading) retries once after a 401, the proxy (`server/gemini.ts`) tries just-busy models last and caps each call; "Ask Gemini again" after a busy/slow answer; an ended session says so | App: `feature/owner-round-9`, PR to `main`. Website: `lucento-website` PR #10 (`feature/ask-real-adviser`; Vercel preview build passed), **not merged or deployed** (the merge was blocked by the session's permission check; the owner merges). No schema change | **935/935** unit tests (new `ProxyCallTest` 8, against a local socket server); lint 0 errors / 9 warnings (unchanged set); `assembleDebug` + `assembleDebugAndroidTest`; website: eslint clean, `tsc` clean on `gemini.ts`, busy-model ordering 5/5 (scratch Node check). **Not verified**: a live adviser answer in the new tone and the new note on a device (needs a signed-in release build and the proxy deployed); the stale-token path on a real phone |
| **Owner review round 8 (2026-11-04)**: a card's payment reminder (bell, Upcoming, the card's Payment) opens the transfer that pays it, filled in (`CreditCardMath.payingWallet`, `cardPaymentDraft`); scans tell a receipt from a statement (`StatementParser`, `StatementCandidates`: one row per line, direction from the running balance, CR/DR, +/− or words; receipts prefer "Amount due", read "Amount paid" less change); Ask Lucento as an adviser: `[[= … ]]` calculations worked out on the phone (`AskCalc`), projections, goals, debt interest, transaction patterns (`TransactionSignals`) and the last 60 days of transactions, under a new consent (`ask_lucento_ai_v2`); proxy adviser prompt on `lucento-website` `feature/ask-advisor` | Committed on `feature/owner-round-8` (app PR to `main`, not merged); the adviser prompt is `lucento-website` PR #9, merged (`901acf8`) and deployed to production (Vercel deployment succeeded; an unsigned POST to `/api/ask` returns 401). No schema change | **927/927** unit tests; lint 0 errors / 10 warnings (none from this round: the AutoboxingStateCreation one is round 7's `AmountText.kt`); on API 28: `ScanOcrTest` **3/3 PASS** (real OCR), `ReconciliationTest` **PASS**, `ScreenshotTourTest` **PASS** (55 shots, new 06c: the bell's card payment opens the filled-in transfer); manual scan in the composer: statement → 5 rows (ATM → transfer to Cash, payroll → Salary, Meralco → Electricity), receipt → one $369.00 payment. **Not verified**: live adviser answers (the proxy is deployed; trying one needs a signed-in production account), a real bank statement photo |
| **Owner review round 7 (2026-11-04)**: hidden amounts on every read/view page (root `LocalAmountsHidden`, `AmountFormat`, editors opt out with `AmountsShown`, Ask answers via `maskAmountsIn`, chart axis numbers hidden); Transfer sheet restyled like the other sheets (`WalletChoicePill`, icon headers, swap button); test-signed production-type APK; Account & sync password change deferred to the last stage | Uncommitted on `feature/core-screens-refinement` with round 6. No schema change | **900/900** unit tests, lint 0 errors / 9 warnings; `ReconciliationTest` and `ScreenshotTourTest` (with a hidden-amounts pass over nine pages) **PASS** on API 28; release APK built, verified (package, signer, production-only) and launched |
| **Owner review round 6 (2026-11-04)**: financial integrity across Home / Transactions / Budget (Transactions opens on This month — its Any-date default was the mismatch; Home says "Spent"; one focus currency for all three); sync chip says how fresh and what's waiting, syncs run in-app while open, no sync can hang; Dashboard "Hide" amounts with phone-unlock to show | Uncommitted on `feature/core-screens-refinement` (on `ffb11d7`). No schema change | **899/899** unit tests (+13: `MonthSpendingReconciliationTest` ×4, `SyncNeverHangsTest` ×4, `SyncChipAndPrivacyTest` ×5); `ReconciliationTest` (new, device) **PASS** and `ScreenshotTourTest` **PASS** on API 28; lint 0 errors / 9 warnings. Not verified: a real signed-in sync (no test account used) |
| **Owner review round 5 (2026-11-04)**: sheets keep a content fling inside (New wallet no longer closes mid-scroll); bars and rings grow in (`animatedFraction`); the composer requires a category on income and expenses; Wallets opens in card view; compact Budget rows; Budget page Safe to spend never below $0.00; **Goals redesigned** (hero ring, sections, fact chips) and **New savings goal** with preset icons (`GoalIcon`, stored in the existing scene key), wrapped suggestions with icons, asset-only "Kept in" + Add wallet, onboarding-style fields; then **goal archive** (reached goals only; Active / Archived toggle; restore) | Committed and pushed `d4ff162`; goal archive committed on top. No schema change (archive uses the existing `archived` column). lucento-website: PR #7 (model choice) deployed `fc5d5fe`; PR #8 (fallback on 503) merged and deployed `50c8379` | **883/883** unit tests (+7), lint 0 errors / 9 warnings (same types), `assembleDebug` + `assembleDebugAndroidTest`, `ScreenshotTourTest` **PASS** on API 28 (45 shots in `artifacts/core-screens/review6/`, new 17a–c Goals); device checks: fast flings in New wallet, goal icon picker and Kept in, Budget rows and Safe to spend, card view default. APK `artifacts/apk/lucento-debug-review6.apk`. Goal archive: **886/886** unit tests (+3); on the emulator (dark theme): reach a goal, archive from the sheet and from the card pill, Active / Archived toggle, read-only archived sheet, restore (`artifacts/goal-archive/`). Not run: AI answers end to end (OT-5 needs the owner's phone); archive not in the tour |
| **Owner review round 4 (2026-11-03)**: **Ask Lucento** (docs/ASK.md): Insights hero typing "What’s my top 5 spending categories?"; preset questions and four reports (net worth, cash flow, budget vs actual, debts & loans) calculated on the phone (`domain/ask`), Save as PDF / Share (`ReportPdfWriter`, Android `PdfDocument`); opt-in AI answers through the Gemini proxy (`AskLucentoClient` → `/api/ask`, summaries only, figures filled in by the phone, `AnswerCheck`); Scheduled page without hero and projection, transfers apart from net flow, summary in Transactions' `FlowStatTiles`; transaction view redesigned (tone hero, Route card, a line per field with icons) and editor wallet dropdown with real wallet tiles; wallet editor in onboarding's filled field style (`LucentoFields`, `IconFieldHeader`); wallet page "View wallet", Transfer + reconcile card, month cards; Insights restyled (no subtitle, section intros, change chip, gridline chart); composer chat restyle with shared `Chat.kt`; root Scaffold insets consumed (no double nav-bar gap above the keyboard) | Implemented, uncommitted on `feature/core-screens-refinement` (rounds 2–3 committed and pushed as `9021ed0`). No schema change. `lucento-website` branch `feature/ask-lucento` (new `api/ask.ts`) uncommitted there | **877/877** unit tests (+22), lint 0 errors / 9 warnings (same types; AutoboxingStateCreation now reported as information), `assembleDebug` + `assembleDebugAndroidTest`, `ScreenshotTourTest` **PASS** on API 28 (new shots 13a–c Ask Lucento); device checks: Ask (preset answer, free question without AI, report month stepper, Save as PDF to Downloads → PDFs rendered and checked), composer with keyboard, transfer view, 360dp @ 1.3, light + dark (`artifacts/core-screens/review5/`). Debug APK `artifacts/apk/lucento-debug-review5.apk`. Not run: AI answers (no proxy in debug builds, OT-4 / OT-5), Share sheet target apps |
| **Owner review round 3 (2026-11-03)**: Bills due counts every scheduled bill whatever its category (it read 0 when bills sat in unbudgeted or no categories); Safe to spend always shown, 0 in red with a reason when nothing is left; Wallets legend (Assets % · Liabilities % · Net worth formula) on one line; scheduled amounts coloured by flow (income green, expenses red, transfers neutral) on Home, the bell and Scheduled; **Scheduled page rebuilt** (`ScheduleOutlook`: month hero with income / expenses / transfers, 3-month projection, Upcoming agenda with Transactions-style filters and a From–To range, All schedules); category required on scheduled bills and income; bell redesigned; shared `SheetHeader`, sheet entrance motion and a new `ConfirmDialog` across every sheet and dialog; New wallet draws the real `WalletCard` live; profile photo (photo picker, or the Google picture on request — `ProfilePhotos`) | Implemented, uncommitted on `feature/core-screens-refinement` (with round 2). No schema change in this round | **855/855** unit tests (+12), lint 0 errors / 10 warnings (the same set as before), `assembleDebug` + `assembleDebugAndroidTest`, `ScreenshotTourTest` **PASS** on API 28 (new shots 12g new wallet, 18 / 18b / 18c Scheduled); device checks: budget card, Upcoming, bell, Scheduled (custom range applied), schedule editor (category required), profile photo pick + remove, 411dp and 360dp @ 1.3, light + dark (`artifacts/core-screens/review4/`). Debug APK `artifacts/apk/lucento-debug-review4.apk`. Not run: Google photo download (needs Google sign-in here, CS-11) |
| **Owner review round 2 (2026-11-03)**: Home budget formula without operators (Remaining green / Overspent red, Bills due red and signed, Safe to spend only above zero), To watch top 5 + "See all"; shared `MascotMessage` (Home cards, Scheduled empty tabs, bell); wallet page Transfer / Payment + Adjust balance for assets and cards (Adjust left the editor); rate in the card title; instalment loan face (Remaining, segmented bar, "x of N installments paid", Principal · Paid · Monthly, `LoanMath.progress`); credit cards: limit, statement day, due day (DB **v11** `MIGRATION_10_11`, sync columns, backup format 3, Supabase `20261103180000_credit_card_billing.sql`), Credit limit · Available · Amount due on the face, derived payment reminder on Home's Upcoming and the bell, excluded from Safe to spend (`CreditCardMath`) | Implemented, uncommitted on `feature/core-screens-refinement`. **Supabase migration not applied anywhere** (owner-run, `PRODUCTION_MIGRATION.md`) | **843/843** unit tests (+14), lint 0 errors / 10 pre-existing warnings (recorded as 9 at the time; the set was the same — a miscount), `assembleDebug` + `assembleDebugAndroidTest`, `tools/check_migrations.py` 10 → 11 OK, `ScreenshotTourTest` PASS on API 28 (new shots 12c loan, 12d card, 12e card editor, 12f bank); device checks 411dp and 360dp @ 1.3, day + night (`artifacts/core-screens/review3/`, `r3-*.png`). `MigrationTest.migrate10To11` written, not run (needs the emulator migration job in CI) |
| **Owner review round (2026-11-03)**: Home order hero → shortcuts → Monthly budget. Budget card: ring as wide as the formula under it; visible formula Budget − Used (= Budgeted + Unbudgeted) = Remaining − Bills due = Safe to spend (tinted, signed; past months end at Remaining) from `HomeStateBuilder.budgetFormula`; "To watch" is a panel as tall as the left half (`MatchedHeightPair`), up to 4 rows with "$x left/over". Budget hero always shows "Spent = budgeted + unbudgeted". Guide budget step: category tile + name left, amount right, live "Monthly budget" total (`GuidedSetup.draftTotal`). Onboarding: icon tiles on step titles, theme icon tiles, summary card, restyled shared currency list (Popular / All, symbol tiles, one scrolling list with pinned search). CALCULATIONS.md: spent = budgeted + unbudgeted, Home formula row, guide total row | Committed `b139646` on `feature/core-screens-refinement`, pushed | 829/829 unit tests, lint 0 errors, `ScreenshotTourTest` PASS on API 28 (new shots 03b, 04c); device checks 411dp, 360dp @ 1.0/1.3/1.5, day + night (`artifacts/core-screens/review2/`, `r2-*.png`). API 36 still blocked (CS-7) |
| **Onboarding stepper (2026-11-03)**: `StageIndicator` rebuilt as a connected stepper (Personalize · Set up · Get started with Tune / Wallet / Sparkles icons that turn into checks; active stage on a pale-sky pill with a halo; connecting lines fill with in-stage progress; inline, labels-under or single-caption layout by measured fit). State from `SetupProgress` (`domain/`): current stage, furthest stage reached (onboarding `furthest_step` saved state; guide ViewModel), progress. Skip moved under Continue. One outlined icon family on focus options, currency/appearance, backup choices and summary rows | Implemented, uncommitted on `feature/core-screens-refinement` | `SetupProgressTest` (7); on API 28: tour PASS, forward/back/Get started states, 360dp@1.3, 320dp@2.0, night (`artifacts/onboarding-stepper/`). API 36 not usable here (KNOWN_ISSUES CS-7) |
| **Core screens refinement, implementation (2026-11-03)**: Dashboard, Transactions, Budget, Wallets per `docs/design/CORE_SCREENS_REFINEMENT.md` (§6 "As built" lists decisions D1–D3, spec corrections and deviations). Design system: `CardOverline`, `StatTile` / `HeroStatTile`, `ListSectionHeader`, `TrendLine`, `ViewToggle`, tonal `ChoicePill`, `screenGutter()` (560dp column ≥ 600dp), Bold section titles app-wide. Home: safe to spend (current month, budget basis), To watch from `plan.allocatedLines`, off-module tiles + sheet. Budget: "Safe per day" (= Home's safe to spend ÷ days left, D1). Wallets: 6-month trend, month-on-month pill (`comparableTrendPercent`), split bar, filters, card view persisted (`wallets_view_mode`). Google "G" button (D3). CALCULATIONS.md safe-to-spend wording corrected (code unchanged) | Implemented, uncommitted on `feature/core-screens-refinement` (from `feature/unified-redesign`) | See the final gate in NEXT_TASKS "Core screens refinement"; API 28 emulator checks (`artifacts/core-screens/`); emulator-5556 (API 36.1) unusable this session (ENV-1 crash loop) |
| **Core screens visual refinement, design phase (2026-11-03)**: high-fidelity designs for Dashboard, Transactions, Budget and Wallets (plus the Wallets card view, the night Dashboard and a shared-patterns sheet) on the Design canvas; implementation spec in `docs/design/CORE_SCREENS_REFINEMENT.md` | Design only; no app code changed; spec uncommitted on `feature/unified-redesign` | Canvas https://claude.ai/artifact/MocjXVBtQG5uW38yUQm9kE (private until shared); every figure maps to an existing calculation (`BudgetBurn`, `BalanceHistory.lastMonths`, `CashflowCalculator.safeToSpend`) |
| Initial audit and Part II extension | Done | `CLAUDE_INITIAL_AUDIT.md` |
| **Redesign close-out (2026-11-03)**: fresh-install pass, read-only QA review (no blocker; 1 High documented by owner decision, 2 Medium-High/Medium + 5 Low fixed), production migration runbook, final 23-item report | Committed on `feature/unified-redesign` (owner-authorized), not pushed. Production migrations applied by the owner 2026-11-03; backup-file restore verified by the owner; trademark review skipped (owner) | **784/784** unit tests, lint 0 errors / 9 warnings, debug + release built (release 51.10 MB, +2.03 MB vs 2026-11-01); fresh install on emulator-5556 (`artifacts/fresh-install-2026-11-03/`, 0 crashes); QA B restored by sign-in, fingerprints match; `PRODUCTION_MIGRATION.md` + `production-migration-2026-11-03.sql`; report `REDESIGN_FINAL_REPORT.md` |
| Dashboard to owner reference + Figma (header, sticky month row, Net Wallet Balance card, budget card with top categories, shortcuts, scheduled, recent; filtered category route) | Done locally, uncommitted on `feature/unified-redesign` | **779/779** unit tests; emulator screenshots (`artifacts/dashboard-figma/`) |
| Redesign Parts D + E: milestones, + spotlight, guide completion, dashboard redesign | Done locally, uncommitted | **775/775** unit tests; emulator Home + milestones (`artifacts/dashboard-redesign/`) |
| Redesign Part B: onboarding (workspace choice, 3 stages, mandatory focus, "How do you pay?", 3 backup paths, file restore) | Done locally, uncommitted | **767/767** unit tests; emulator fresh + returning paths (`artifacts/onboarding-redesign/`) |
| Wallet card look (owner direction D3): drawn chip, 22sp balance | Done locally, uncommitted | Emulator (`artifacts/wallet-identity/card-chip-*.png`) |
| Redesign Part A: wallet/banking identity (bundled registry + art, picker, institution card face, avatars, sync/backup) | Done locally, uncommitted | **762/762** unit tests, emulator: picker/search/preview/list/detail/liability/200% (`artifacts/wallet-identity/`), server `institution_id` verified on `lucento-test`, APK +1.47 MB measured |
| Redesign Part C: month-specific budgets (DB v10, Budget sheet scope choice + confirmation, sync/backup scope) | Done locally, uncommitted; migration on `lucento-test` (owner), production pending (RD-C1); two-emulator PASS | **755/755** unit tests, lint 0 errors, debug + release built, `check_migrations.py` 9→10 OK, MigrationTest 13/13 on emulator-5556, sheet checked on emulator without saving |
| `lucento-test` access via the claude.ai Supabase connector | Working; the connector currently sees only org "Lucento Test" (production no longer visible to it) | Verified (MCP) |
| P0-1: debug → `lucento-test`, release → production (`LUCENTO_DEBUG_SUPABASE_*` vs `LUCENTO_SUPABASE_*`; build fails if debug points at production) | Done locally | Verified (BuildConfig and APK dex) |
| P0-1.1: TEST badge in debug builds (`EnvironmentBadge`) | Done locally | Verified (emulator; absent from release dex) |
| P0-2: safe onboarding, cloud-data detection, duplicate prevention | Done locally, awaiting acceptance | See P0-2.1 row |
| P0-2.1: account detection adds transactions, schedules and budgets (restore rule aligned, archived wallets count); accurate combine-dialog wording; P1-8 budget-conflict task recorded | Done locally (2026-11-01) | **620/620** unit tests (+9), lint 0 errors, debug and release built, debug → test / release → production verified in APK dex |
| P0-3 global amount parsing: one rule set in `MoneyParser` (locale decimal trusted, the other mark validated as thousands or decimal by the currency's precision, `Ambiguous` / `WrongCurrency` results with one shared message); composer, AI and receipt paths aligned; `12,50` fixed everywhere | Done locally. **PASS** recommended | **671/671** unit tests, lint 0 errors, debug and release built and scanned, device checks (wallet, guide, composer, de-DE); QA: FAIL with 4 blockers → fixed → re-check PASS WITH NOTES; `P0_3_AMOUNT_PARSING_REPORT.md` |
| P0-2 final acceptance: F-7 fixed (the next account after a sign-out goes through onboarding), 2 restore tests, process death during a restore, release secret-key guard, F-8 investigation | Done locally. Recommended: **CLOSED WITH DOCUMENTED VALIDATION LIMITATIONS** (physical-device background restore deferred; Settings failure and empty-account switch automated only). F-8 is a separate release blocker; Account B session cleanup awaits authorization | Re-run with `--rerun-tasks`: **634/634** unit tests, lint 0 errors, debug and release built and scanned, release key guard failure cases executed; closure QA review: no blocking findings; `P0_2_FINAL_ACCEPTANCE_REPORT.md` |
| Stabilization cycle: CI #108 verified, toolchain, two-emulator sync QA (2 defects fixed: offline delete vs newer edit, duplicate budgets), P2-1/2/4/5/6 done, P2-7/P2-8 partial, P2-11 REST probe + pgTAP suite, emulator accessibility audit, P3 copy fixes, independent QA (no critical; high/medium fixed) | Done locally, uncommitted on `main` | **732/732** unit tests (final lint/build: see NEXT_TASKS); evidence in `artifacts/sync-qa/`, `artifacts/p2-ux/`, `artifacts/security/`, `artifacts/accessibility/` |
| Master backlog run: session coordinator (R10-01/N-03/N-04, offline revoke), P0-1 CI release config + debug AI URL, P1-5 transactional pull, P1-8 latest-edit budget fold, P2-9 constraints on `lucento-test` + pulled/pushed row checks, P1-7 large text/narrow width, P2-3 budget split, P2-10 app lock + hide in recents, composer picker fix, QA notes 1–4 and 9–12 | Done locally; QA review **PASS WITH NOTES** | **700/700** unit tests, lint 0 errors, debug and release built, release permissions within the allow-list, no server keys in dex; device evidence in `artifacts/p0-session-revoke/`, `artifacts/p1-7-accessibility/`, `artifacts/p2-10-app-lock/`. Status table and decision register: `NEXT_TASKS.md` |
| P0-2 device QA on two new API 36 emulators against `lucento-test` | 12 of 13 onboarding scenarios PASS (O-10 added in the follow-up), O-13 FAIL (known N-02); E-1 and E-2 PASS; Settings combine flow PASS including both consent-bypass paths; pre-existing QA data fingerprints unchanged (re-checked). New findings F-7 and F-8 | `P0_2_DEVICE_QA_RESULTS.md`, evidence in `artifacts/p0-2-device-qa/` |

Not committed, pushed or merged. Recovery stash intact. Debug builds now sync only with `lucento-test`. QA accounts on
`lucento-test`: A `211a8bb2` (holds QA data, snapshot A2, plus one master-run check row "P29 check coffee" −$3.25) and B
`ae2e7a97` (confirmed; 18 categories from E-2; sessions signed out with approval). New AVDs
`Lucento_P0_QA_NewUser` and `Lucento_P0_QA_ReturningUser` exist; run them with `-gpu host` and 3-button navigation
(emulator GPU bug, see the QA results §1).

## 1. Executive status

The report records 585 passing `testDebugUnitTest` tests and a final QA device run passing selected sign-out, session-upgrade, upload-on-write, launch-sync, category-deduplication, backup-merge, and UI-baseline checks. These checks used **debug builds against `lucento-test`**, not the live Supabase project.

The release application (`app.lucento`) had **not** been validated against the live `lucento` project. Several device workflows remained untested. Open risks include interrupted sign-out/server-token revocation, offline sign-out, large-font and narrow-screen layout, first-sign-in data preservation, and unverified accessibility/device workflows.

## 2. Stack and architecture recorded in the report

- Native Android: Kotlin, Jetpack Compose, Material 3.
- Minimum SDK 26; compile/target SDK 35; JDK 17; Gradle 8.11.1.
- Room v10 (`MIGRATION_9_10`: `budgets.month`, `budgets.effective_from`, `wallets.institution_id`; redesign Part C/A).
- `supabase-kt` 3.1.4 (PostgREST and Auth). The report recommends pinning this version because session-key derivation changed during earlier work.
- Room/local persistence; Supabase sync; WorkManager; tombstones; last-write-wins timestamps.
- WorkManager: periodic sync every 30 minutes (`KEEP`); one-off manual sync for Sync now and launch; upload-after-local-write uses `APPEND_OR_REPLACE` and requires network.
- Integrations: Supabase Auth/PostgREST; direct GoTrue logout POST; Gemini interpret endpoint (opt-in); Crashlytics (opt-in); HIBP k-anonymity check; speech features.
- Live Supabase project: `lucento`, ref `hmtcuttadbgbldvzsfhz`, Seoul region, Free plan.
- Test Supabase project: `lucento-test`, ref `ogenvdopfgocjdkcrsny`.

Never put credentials, tokens, service-role keys, or passwords in source control, prompts, logs, or APKs.

## 3. Changes reported as merged by `7928c63`

| PR | Merge | Reported change |
|---|---|---|
| #4 | `988c79c` | Release-blocker fixes: days-left calculation, goal-contribution delete confirmation, recurring cap removal, sign-out wipe/unbind, data-retention notices, parser cues, amount-field select-all, Crashlytics opt-in, HIBP check. |
| #5 | `709d29b` | First-run redesign and related work; UI redesign later reverted, non-UI work retained as described. |
| #9 | `6a80c45` | Upload-on-local-write via `LocalWriteUploads.kt`, WorkManager scheduling, application hook. |
| #13 | `1974727` | Hand-merged UI restoration to `988c79c`, retaining non-UI work and usage-counts switch. |
| #7 | `ce6dc5d` | Durable session store, session-key migration, wipe marker/order, sign-out handling. |
| #11 | `7783777` | One-off launch sync when signed in with sync enabled. |
| #6 | `16f7345` | JSON backup/restore, Merge/Replace preview, settings backup section, DAO queries, related fixes. |
| #12 | `7928c63` | Pull-first first sign-in; remove never-synced unused local categories except kinds missing from account. |

The report states the net diff from `988c79c` to `7928c63` was 88 files, +6,495/-112 lines, with no Room schema or Supabase migration changes after the earlier baseline.

## 4. UI/UX baseline and current authority

The report identifies `988c79c` (shown in PR #7 as `cce97c0`) as the restored visual baseline after PR #5's UI redesign was reverted by PR #13.

**Policy precedence:** The PDF's historical “no UI/UX changes without approval” restriction is superseded by the owner's later explicit direction and current `CLAUDE.md`. Claude has broad creative authority to critique and improve screens, onboarding, navigation, information architecture, components, and interactions. Existing screens, screenshots, and `988c79c` are reference material, not immutable constraints.

Keep product coherence and functional completeness. Preserve engineering, financial-data, privacy, security, and offline-first requirements. Surface high-impact product-scope or core financial-concept decisions rather than making irreversible changes casually.

## 5. QA evidence recorded at `7928c63`

Final QA used emulators on API 36 and API 28, a debug build, and `lucento-test`.

Reported passed:
- 585 `testDebugUnitTest` tests.
- APK dex contained only the test project reference.
- Schema/migration diff was empty.
- Session upgrade (R6-13).
- Upload-on-write (R6-03).
- Launch sync without overlapping workers (R6-11).
- Sign-out kill safety (R6-01, twice).
- R9-01 timing checks at 150/400/600 ms.
- Restore after wipe (R6-15).
- First-sign-in category handling (R6-14).
- Backup merge (R6-02).
- Online sign-out deleted server session.
- UI comparison against `cce97c0`, with expected Settings differences for usage switch, footer, and backup section.

Earlier Phase 2 and R1–R5 checks were performed on earlier PR heads and were not rerun on `7928c63`. Some results were code-only or inferred, not device-verified. See `KNOWN_ISSUES.md`.

## 6. Current local workflow state

At handover preparation:
- Local branch: `feature/ai-development-workflow`.
- Branch base and `origin/main`: `7928c63`.
- Local workflow changes include `CLAUDE.md`, `.gitignore`, `.claude/`, `artifacts/`, `docs/handover/`, and `docs/ui-ux-audit/`.
- Recovery stash `backup-before-sync-origin-main-2026-10-30` is retained.
- `.claude/agents/lucento-architect.md` and `.claude/agents/lucento-qa.md` are read-only specialist agents. Main Claude Code session owns implementation.
- `.claude/launch.json` contains a local website dev configuration; preserve unless owner requests a change.

Do not drop the recovery stash, commit, push, merge, delete branches, or modify live Supabase data without explicit authorization.

## 7. Operating principles

1. Read `CLAUDE.md` before making changes.
2. Inspect actual code at current HEAD before accepting this report's claims.
3. Preserve evidence labels: Verified, Code-only, Inferred, Unverified.
4. Store money as integer minor units (`Long`) with ISO 4217 currency; no floating-point money arithmetic or implicit currency conversion.
5. Preserve offline-first behavior, Room migration safety, sync correctness, security, and privacy.
6. Never commit secrets or local credentials; `google-services.json` is not in the repo by design.
7. Make focused, testable changes and report exactly which tests were and were not executed.
8. Use Architect and QA agents for independent read-only reviews when useful.

## Security remediation — 2026-11-10 (`security/release-remediation`, website `security/release-hardening`)

All remediable audit findings fixed in code and tested offline: four migrations (`20261111090000`–`20261111120000`), app sync refusal handling and purchase account binding, website Astro 7 upgrade, headers/CSP, method guard, OIDC push check, per-account verify quota, CI and Dependabot. Results: SQL 258/258 (both privilege modes), concurrency 4/4 on Postgres 17.6, Android unit 1,253/1,253, release APK clean, OSV 0, website unit 44/44, e2e 55/55, `npm audit` high/critical 0. Nothing applied to Supabase, pushed or deployed. Decision **NOT APPROVED** until the owner steps in `docs/security/OWNER_ACTIONS_REQUIRED.md` are done. Report: `docs/security/REMEDIATION_REPORT.md`.

## Play purchases never granting Pro — 2026-11-11 (`fix/play-licence-key-check`)

Owner's internal-track test of 0.5.2 (vc 7): the 7-day trial and the yearly plan both completed in Google Play, but the
app stayed on Free. Cause (verified): CI's secret `LUCENTO_PLAY_LICENSE_KEY` holds a SHA-1 certificate fingerprint, not
Play's Base64 RSA licence key, so every purchase failed `PurchaseSignature` and the whole Play reading was discarded:
no Pro, no server verification, no acknowledgement (Google then refunds). Evidence: the fingerprint is the
`const-string` R8 propagated into `PlayPurchaseSource` in the 0.5.2 AAB; production has no `purchase_links` and no Play
`entitlements` rows. Fix: owner replaces the secret (NEXT_TASKS OT-23); `app/build.gradle.kts` now fails any build whose
key doesn't decode as an RSA public key (checked locally: fingerprint and split key fail, a real 2048-bit key and an
empty key pass). Version 0.5.3 (vc 8). KNOWN_ISSUES PB-1.

## Welcome to Pro — 2026-11-12 (`feature/pro-welcome`)

The owner found the upgrade "too silent" and approved a storyline: live tour on real screens, all eight stops, the
medal kept for good with the ring following the plan, starting straight after Play confirms. Built:

- `domain/entitlement/ProWelcome.kt` decides what plays and when. It plays once per plan and never for a pending
  payment, a renewal or a trial turning paid. A returning member gets no ceremony. If the medal was interrupted, the
  medal alone plays next time. It also picks the stops for the plan and modules, and lists the summary's privileges and
  online status.
- `feature/pro/welcome/` has the copy, curtain, tour overlay, summary dialog, state machine and badge flight.
  `core/designsystem/component/Spotlight.kt` holds `SpotlightRegistry` and `Modifier.spotlightTarget`, plus the scrim
  with a lit window.
- Awards: `ProMedalCeremony` shares the ceremony's stage, and the Membership shelf in the cabinet replays it on tap.
  ProfileButton has the Pro ring. ProScreen has **Replay the Pro tour**.
- Preferences `pro_welcomed` and `pro_member_since` are cleared by the account wipe and kept when returning to one's
  own ledger.
- Tests:
  - JVM: `ProWelcomeTest` (20), `ProWelcomeCopyTest` (16) and the account-reset case. Full suite: 1,316 tests, 0
    failures. `lintDebug`: 0 errors, and no warnings in the new code.
  - `ScreenshotTourTest` now captures `24h-pro-welcome`, `25a`–`25l` after the test-store purchase (`25g`, the sync
    stop, only in builds with cloud sync). That run and `WebsiteShowcaseTest` skip the welcome for their simulated
    plan (`DemoMonth.skipProWelcome`).
- Found and fixed on the local API 28 emulator:
  - The profile button keeps its label "Profile and settings"; Pro is its state description. Changing the label had
    broken the tour's lookups.
  - The tour no longer waits on a timer before lighting a control. A second stop on an unchanged screen stayed unlit
    under the test clock.
  - A control is brought into view clear of the dock, and the lit window stays out of the status bar.
  - The summary's button stays in view.
  - Builds without cloud sync leave out the sync stop and say the online services aren't available there.
- Design critic, round 1: **REJECT** (lucento-studio `critic/reports/2026-11-12-pro-welcome-r1.md`). The response is in
  `…-r2-response.md`. Fixed:
  - The summary claimed online services before the server confirmed them.
  - The AI stop offered a button that wasn't there.
  - Hiding the app from TalkBack under the tour had never actually applied.
  - The ring crossed the status bar.
  - The curtain moved on by itself even with TalkBack on.
  - The dock highlighted the wrong tab.
  - Copy, contrast and the replay button.
- Design critic, round 2: **REJECT** (`…-r2.md`); response in `…-r3-response.md`.
  - Investigated and guarded: the missing badge in 25k didn't reproduce with flight logging, and the test now waits for
    the crest to be drawn.
  - Every stop says where its feature lives.
  - Summary rows are two lines, the plan line no longer claims online services, and unconfirmed services are a plain
    list.
  - System Back matches the card's Back, and stray taps don't skip the award.
  - The award is dated from the plan's real start, and a restored plan is "Welcome back".
  - The statements stop lights the first report.
- Design critic, round 3: **REVISE** at 3.3 / 5, with all hard gates passing (`…-r3.md`). After round 3 (not
  re-reviewed; the workflow stops at three rounds and goes to the owner):
  - Back always leads out.
  - The caption card scrolls with its buttons pinned.
  - The AI allowance shows only once the server confirms.
  - Empty pages get the right button named, and there's no net-worth stop without a wallet.
  - The "where" line is announced.
  - "No limit", as on the Pro page.
  - An "Also yours" line in the summary.
  - The ring is outlined on light backgrounds.
  - The ceremony's sunburst rests after its entrance (WCAG 2.2.2).
  - The dialogs dim like the tour.
  - The Pro page says "Plans" for owners.
- Owner review of the frames (2026-11-12): Gemini and Lucento Shared were under-shown, because the test build has no
  sync, so those stops were absent, and even with sync Gemini wasn't named.
  - Added: a "Smarter reading, with Gemini" stop (the composer's input), Gemini named on "AI answers, with Gemini", and
    a "Share with a Partner" stop (the Lucento Shared page's main button).
  - Both show to signed-out people with a sign-in note.
  - Up to ten stops in a build with sync.
  - Captured from a debug build with lucento-test sync settings (local.properties, git-ignored).
- The owner chose "PR, merge when green". Version 0.5.4 (vc 9).

## Refund guards — 2026-11-12 (`fix/refund-guards`)

The owner refunded a test Lifetime in Play Console, and Lucento kept showing it. Google's documentation says a refund
without **Remove entitlement** leaves the buyer owning the purchase and stays out of the voided list (KNOWN_ISSUES PB-4).
The owner asked how refunds could leak revenue, and chose:
- switch on the server's refund checks together with the service-account fix (OT-24, PB-3);
- build the phone-side guards (PR #55, merge when green).

What changed:
- A purchase the server revoked stays revoked on the phone while Play's copy still lists it (same token). That holds
  offline, on Play's pushes, and after a restart. A new purchase counts at once (`EntitlementMerge`).
- Anything bought through Google Play needs one check a month, from Google Play or the server. That covers a Lifetime
  and a subscription; grants keep their own dates (`Entitlement.awaitsCheck`).
  - `checkedAtMillis` is stamped on each server fetch and kept in the cached snapshot.
  - Without a check, the plan is paused, not ended. It gets its own notice ("Lucento needs to check your Pro Lifetime" or
    "… your Lucento Pro") and a matching Restore message.
  - MONETIZATION §5.3 L-1/L-2 are rewritten. PB-5 (a server-verified annual plan on a phone that stops reaching the
    server) is fixed by this.
- Tests:
  - 14 new JVM tests, and three updated ones that encoded "a Lifetime never needs a check". Results are in the PR.

## Pro welcome buttons dead on real phones — 2026-11-12 (`fix/pro-tour-real-taps`)

On the owner's Galaxy S8 (Android 9, 0.5.4 vc 9), nothing on the Pro tour's card answered a tap: not Next, not End
tour. That held on the first run and on a replay; only the system Back key worked. The curtain's buttons were dead the
same way. They only looked fine because the curtain moves on by itself (KNOWN_ISSUES PW-1).

- **Cause:**
  - The overlay's touch blocker wrapped the cards and consumed every pointer change.
  - A finger always drifts a pixel or two between down and up, and Compose cancels a click when something else
    consumes that movement.
  - The emulator runs pressed with `performClick` or a still down-up. Neither moves, so they all passed.
- **Fix (`ProTourOverlay.kt`):** the blocker sits on the scrim, under the cards, never around them. A finger on a card
  reaches its buttons; a finger on the dimmed screen still stops there.
- **Tests,** through a shared `Finger` helper (down, two 1–2 px moves, up, injected into Android's input system):
  - `ProWelcomeRealTapTest` runs the tour over the real app. Next, Back and End tour answer, and a tap on the dimmed
    dock leaves the app on Wallets.
  - `ProTourCurtainFingerTest` runs the curtain on its own, away from its timer. Skip the tour and Show me answer, and
    taps on the scrim or the card's words don't reach the app beneath.
  - **Results:**
    - Old overlay (API 28 emulator, animations on): the tour stopped at Next, and the curtain counted 0 presses of
      Skip the tour.
    - Fixed overlay: both tests pass, and both are in CI's emulator job.
    - 1,330 JVM tests with 0 failures, and `lintDebug` with 0 errors.
    - `assembleDebug` and the instrumented-test compile pass.
- **Owner decision (2026-11-12):** retire the spotlight tour for an unboxing reveal. This fix is held, committed
  locally and not pushed, and ships with the reveal in one release (NEXT_TASKS).

## Pro welcome: the unboxing reveal — 2026-11-12 (`feature/pro-reveal`, from `fix/pro-tour-real-taps`)

The owner retired the spotlight tour, whose buttons failed on real phones (PW-1). The reveal had to keep the
excitement of the upgrade and the badge award.

**Design.** Three tappable prototypes in the studio went through two critic rounds:
- r1: REJECT; the Deck scored best at 3.0.
- r2: REJECT at 3.1; "Deck, unwrapped" recommended.

The owner then picked **Deck, unwrapped** with **Scenes** card art. Prototypes: the artifact "Lucento Pro Reveal" and
studio `lab/pro-reveal.html` / `lab/pro-reveal-art.html`. Reports: `critic/reports/2026-11-12-pro-reveal-r1/r2*`.

**What's built**, in `feature/pro/welcome/`:
- `ProReveal.kt`: the stage and the deck.
  - The stage is solid (it was a 0.92 dim until critic round 3) and takes stray touches. It lies beside the content,
    never around it. While it is up, the status and navigation bars draw light icons.
  - The deck: a gold band with the approved symbol; turns at 90°; one burst; swipe; Previous, Next and Skip. TalkBack
    hears a line per card from the counter, the welcome's one live region.
- `PerkCard.kt`: the two faces.
  - The back carries the approved `lucento_symbol` on an ivory disc.
  - The face: the scene, the title, what changed (a `FlowRow` that wraps at large text), the term, where it lives,
    and the sentence. The words take the height they need and the scene takes the rest (no gap); beyond the card the
    words scroll, with a "More" pill on a solid band.
- `PerkArt.kt`: ten scenes drawn in Compose, using new `AwardArtwork` card colours.
- `ProCollection.kt`: the plan's own line first, then each card with its gain or end date.
- `ProWelcomeHost.kt`: the phases REVEAL → COLLECTION → MEDAL → FLIGHT. The award is the existing ceremony.

**Copy** (`ProWelcomeCopy`, `ProCopy.serviceTerm`):
- No card says "here".
- Online services say "Included until Nov 12, 2027", "While you subscribe" or "With Lucento Cloud".
- The AI card says "50 a day until …, then 10 a month".
- Dates stay on one line.

**Retired:**
- `ProTourOverlay.kt` and `ProPrivilegesDialog.kt`;
- the screens' spotlight hooks (the profile picture keeps one, for the flight);
- `showTourStop`;
- `ProWelcome.privileges`.

Net worth and Smarter reading no longer wait for a wallet. The Pro page's button is now "See your Pro cards".

**Tests:**
- JVM: 1,330 tests, 0 failures. New: no "here", terms, AI term, collection tags, `serviceTerm`.
- `lintDebug`: 0 errors. `assembleDebug` and the instrumented-test compile pass.
- On the API 28 emulator, both passed:
  - `ProWelcomeRealTapTest`: the deck over the real app — open, Next, Previous, a tap on the dim, Skip, Back.
  - `ProRevealDeckFingerTest`: the band, Next, Previous and Skip, with a layer beneath that counted 0 leaks.
- `ProRevealCaptureTest` takes opt-in captures. ScreenshotTourTest's welcome frames were rewritten (25a–25l); the
  emulator CI runs them.

**Critic round 3** (on the app's captures): **REJECT, 3.1**. Two gates failed: the dark collection's plan title
(1.56 : 1), and the deck's controls at 200 % text ("Previou / s", "See your"). The loop's three rounds are spent, so the
owner decides next (NEXT_TASKS). Fixed since, all on `feature/pro-reveal`:
- **The dark title:** the plan panel is a `Surface`, so its title takes the theme's ink.
- **The buttons:**
  - `DeckButtons` measures the deck's own labels and stacks Next above Previous when one won't fit on a line.
  - Next keeps one place and one size from the closed deck to the last card.
  - App-wide, `ButtonContent` (primary, secondary and quick-action buttons) wraps a label that doesn't fit instead of
    cutting it off, with 8 dp of vertical padding. At default text nothing changes.
- **The band:** placed by layout under the crest and clamped inside the card; its label grows only to 1.3×. It speaks
  the count for TalkBack, and the line above no longer repeats it.
- **The card face:** the scene takes the height the words leave (one layout pass with `maxIntrinsicHeight`), so there
  is no gap. "More" sits on a solid band.
- **TalkBack:** one live region, the counter ("2 of 10. Title.").
- **The collection's rows:** the gain sits at the row's end while it fits beside the name, and drops under the name
  when it doesn't (a `FlowRow`; this replaced the fixed 130 % rule).
- **The stage:** solid, with light system-bar icons while it is up.
- **Copy:**
  - "Tap Add at the top right of …"; "Choose 6 or 12 months above the charts in Insights."; statements "In Insights,
    under Reports."; themes no longer repeat the three names.
  - A fifth chapter, *Beyond this phone*, for Sync and Lucento Shared (`AwardArtwork.GlowBeyond`).
- **Typography:** headlines grow only to 1.5×; the collection says "You're Pro" from 130 % text; counts and dates use
  no-break spaces, including the Pro page's `planStatus`.
- **Insights scene:** redrawn as up-and-down months in a widening window.
- **Taps during a turn:** kept and acted on once the card has turned; a double tap within 250 ms counts once. Haptics:
  the button's own, plus a tick for swipes only.
- **Tests:** 1,333 JVM tests, 0 failures. `lintDebug`: 0 errors.
- **Captures:** 15 emulator runs in studio `qa/out/reveal-app-r3/` (the opt-in capture test now names the width and
  reduced motion).
- **Not verified:** TalkBack, haptics and Android 14+ large text, which need a real phone; the "confirming" state,
  which needs a signed-in account (its copy is JVM-tested).
- **Owner (2026-11-12):**
  - "PR + review together": release 0.5.5 (version code 10), merged only if CI passes and critic round 4 finds no
    hard-gate failure;
  - keep "AI adviser" and the rosette medal;
  - large text "works to 200 %, polished to 130 %": cut-off or overlapping text, or an unusable control, up to 200 % is
    a hard fail; looks are judged at 100–130 %.

**Critic round 4** (PR #56 at 9f01196): **REVISE, 3.4**, with no hard gate failing. Fixed before merging, in
`a1c994e`:
- **The brand name:** the first headline broke it at 360 dp ("Lu- / cento Pro"). Stage headlines no longer hyphenate,
  and "Lucento Pro" never breaks.
- **The online-service cards:** each says where it stands under its title ("Off · needs your Lucento account" …
  "On"). The AI card states its allowance only once the server has confirmed it ("Included until …" before then).
- **The top band:** 64 dp once the deck is open, so the cards get the height.
- **Line breaks:** no-break spaces in counts, "side by side", menu paths (which break only between steps) and "Pro
  Lifetime ·"; card titles and chips break evenly.
- **Smaller fixes:** the band's second disc is gone; the hidden Skip is disabled; "one Partner" comes from
  `PlanCatalog`.
- **MONETIZATION:**
  - the welcome's rules updated;
  - D-13 and §5 now say the Lifetime's services year runs from the purchase, which is how the app (`PurchaseInterpreter`)
    and the server (`20261108090000_play_purchases.sql`) compute it. The docs had said "from first verification".
- **Tests:**
  - 1,336 JVM tests: 1,334 passed. The 2 that failed were `ProxyCallTest` socket timeouts while the emulator booted,
    in code untouched by this work; that class passed 8/8 when re-run alone.
  - `lintDebug`: 0 errors.
- **Not changed (owner's calls):** the black stage; the award's confetti and rays (the badge award is a must-keep);
  the "AI adviser" name; the rosette.

## Pro perk cards: new art — 2026-11-13 (`design/perk-card-art`)

The owner wasn't satisfied with the cards' drawn "Scenes" art. After three AI directions (critic r1, studio
`critic/reports/2026-11-13-perk-art-directions-r1*`) and a hand-drawn set with people, the owner chose: simpler, no
people, the mascot to make it livelier, faint doodles instead of plain blue, and as little empty blue as possible.

- **Built:** `PerkArt.kt` draws each card's band in `AwardArtwork.CardBlue` with code-drawn doodles (white at 10–16 %),
  one soft-3D prop (cut out, so the doodles run around it) and `LucentoMascot` (approved sprites, never redrawn)
  stepping a little in front of it, with a pose per card (`ProTourStop.mascot`: waves first, cheers last). The pair
  fills one art window per card (at most 0.55 of the card's width), the mascot's hand on the prop. `PerkArtCache` decodes the next card's
  prop while the current one shows. The collection rows show each card's prop instead of an icon. Props: ten images
  from GPT Image 2.5 (Figma), sources and prompts in `docs/design/illustrations/perks/`, exported by
  `tools/illustrations/export_perk_art.py` (Pillow, numpy, scipy). Scene-only colours and the drawn scenes are gone.
- **Verified:** the Pro unit tests (47, including `PerkArtTest`: one picture and one thumbnail per card; the mascot
  greets first, cheers last, never shows concern) pass; `assembleDebug` succeeds; `ProRevealCaptureTest` captured on
  the API 28 emulator at 411 dp light and dark, 360 dp, and 150 % text (studio `qa/out/perk-art-mascot-r3/`).
- **Critic rounds on the app:** r1 REJECT (doodles drew under the words in dark theme; fixed by clipping first),
  r2 REVISE 3.45, no hard gate. Then the owner chose one art window per card (at most 0.55 of the card's width); the
  pair now leaves 12–20 % of the band empty at 360/411 dp and 100/130 % (was 20–46 %). Reports: studio
  `critic/reports/2026-11-13-pro-cards-app-r1*.md`, `-r2*.md`.
- **Shipped:** critic r3 REVISE 3.45, no hard gate; CI green at `7120d69`; PR #59 merged as `af4aae4`, main CI
  green; the 0.5.6 (vc 11) AAB, upload key 56:F1:57…:95:C2, is with the owner for internal testing.
- **Polish after r3** (owner: "Polish only", branch `design/perk-card-polish`): one short float per card
  (`LucentoMascot(idleCycles)`), no doodle over or between the pair (`doodleSpots`, unit-tested), card 7's prop
  without the sparkle, the hand over the prop only when pointing (10 dp apart otherwise). Three critic polish rounds
  (REVISE 3.6, 3.7, 3.6; no hard gate); merged as #62 `f7fbaf6`, main CI green; ships in 0.5.7 (vc 12, PR #64)
  together with #61 and #63 (KNOWN_ISSUES PB-6).
- **Not verified:** a real phone (the hop as each card turns, OLED contrast of the doodles, prop sharpness).

## Home-screen widgets — 2026-11-12 (`feature/home-widgets`)

The owner chose widgets only, both free; the Quick Settings tile, the type-and-save notification and Share to Lucento
are parked. Spec: `docs/WIDGETS.md`.

- **Built:** Quick add (the composer's scan · mic · field bar) and Safe to spend (Home's figure, its reason or the
  per-day and bills lines, the bar and verdict; Net flow when Budgets is off). Jetpack Glance 1.2.0, Compose BOM
  2025.02.00. Settings › Home-screen widgets pins either one. Code in `feature/widget/`; the composer's entry fixes are
  in `TransactionComposerScreen` and `LucentoRoot`.
- **Verified on the API 28 emulator** (`Lucento_CoreScreens_API28`):
  - Pin dialogs work.
  - Every tap opens the right place with a single `MainActivity`: the field, Scan (camera), Speak (recogniser), Add,
    and the body (Home).
  - Speak tapped while the composer is already open starts the recogniser in it.
  - On a cold start the recogniser waits for the launch logo; under App lock it opened only after the PIN.
  - The locked state shows no amounts.
  - With amounts shown the widget matches Home: $0.00, "Bills due take what’s left", 99 %, Ahead of pace (Home:
    Remaining $32.01, Bills due −$1,564.99).
  - Text at 100, 115 and 130 % keeps the full card; at 200 % the compact card shows with nothing cut.
  - The widget redraws live inside an open Glance session when the text size changes, and Light/Dark switch at once.
- **Tests:** `testDebugUnitTest` ran 1,351 tests in 172 suites with 0 failures; the widget suites are 9 + 12 tests,
  including a 6×7×7 size sweep. `lintDebug` reports 0 errors and `assembleDebug` succeeds.
- **Not verified:**
  - Android 12+: rounded corners, the picker's preview layout, day/night colours switched by the launcher.
  - A Samsung One UI launcher, TalkBack reading the widgets, and landscape.

## Buying Lifetime again after a refund — 2026-11-13 (`fix/refunded-lifetime-rebuy`)

The owner's internal-track test of 0.5.7 (vc 12):
1. Monthly Pro, then a chargeback test. Restore purchases correctly went back to Free. Production: `400c3170…` was
   `REVOKED` at 22:53Z and is in `revoked_purchases`.
2. Buying Pro Lifetime: Google Play showed "Error: You already own this item".

**Cause** (verified, production read-only): the Lifetime test purchase of 2026-11-12 (`a322b4f2…`) was refunded in Play
Console without **Remove entitlement**.
- Google's server calls that order `CANCELLED`, which is stored as `EXPIRED`.
- The purchase check answered `EXPIRED` for it again at 22:41Z. The phone only sends that check for a purchase Play
  still lists.
- So Google Play still holds the purchase on that Google account while Lucento has ended it, and nothing in the app
  ever released it (KNOWN_ISSUES PB-8).

**Fix** (MONETIZATION §5.3 L-7):
- `domain/entitlement/LifetimeRelease` (pure) picks what may be released. That's a Lifetime Play lists whose Lifetime row
  Lucento's server ended (`REVOKED`/`EXPIRED`, same token; a server row or the purchase check's answer). Never released:
  a Lifetime that still counts, one paused for a check, a pending payment, a services-year ending, or a subscription.
- `PurchaseSource.consume`: Play's `consumeAsync`, the fake store, and `false` without billing.
- `EntitlementManager.releaseEndedLifetime()` asks the purchase check about each one again, then consumes it only on a
  fresh `REVOKED` or `EXPIRED`, then reads Play again. With no answer nothing is released. Builds without the check
  (debug) never consume.
- `ProViewModel.buy` runs it before Play's sheet opens for Lifetime. Never in the background, because it can't be undone.
- When Play still answers "already owned", the page reads Play and the server again, then releases or explains
  (`ProCopy.alreadyOwned`, "it's working" first). That happens once per purchase attempt, because the Billing Library
  can report it twice.
- `PlayPurchaseSource.consume` gives up after 15 s, because it runs under the manager's lock.
- `EntitlementMerge.reconcileServer` keeps Play's reading of a new purchase the server hasn't recorded yet when the
  server's row for that product is about an earlier one that no longer counts (PB-9).
- Restore purchases now says a refunded Lifetime was refunded, instead of "No purchase found".
- An independent QA review (lucento-qa, read-only) found no path that consumes a Lifetime that should still count. Its
  points 1–5 are fixed here. Point 4c and the 410 note are recorded as PB-10.
- Docs: MONETIZATION §5.1, §5.3 L-7, §9 and §10; CUSTOMER_COPY D-8 and D-8a; the PLAY_CONSOLE_SETUP refund runbook;
  KNOWN_ISSUES PB-4, PB-8, PB-9 and PB-10.

**Tests** (local, JBR 21, `--no-watch-fs`):
- `testDebugUnitTest`: 1,415/1,415 in 179 suites, 0 failures. `assembleDebug` succeeds. `lintDebug`: 0 errors, 24
  warnings.
- New: `LifetimeReleaseTest` 9, `LifetimeRebuyTest` 8, `ProRebuyFlowTest` 5, and new cases in `EntitlementMergeTest` and
  `ProCopyTest`.
- Red checks:
  - With the release before Buy disabled, `ProRebuyFlowTest`'s main case fails.
  - With the merge rule and the once-per-attempt guard disabled, exactly their three tests fail.
- Gradle's daemon once crashed in `native-platform-file-events.dll` (Windows file watching, not the code). Runs with
  `--no-watch-fs` are stable.

**Not verified:** a real phone. Google documents consuming as what makes a product buyable again. Whether Play consumes a
purchase it refunded is only reported by other developers, so the owner's test on the next build is the first real check
(NEXT_TASKS OT-25).

**Owner decision (2026-11-13):** "PR, merge when green, no bump". It ships with the next release; main stays 0.5.7
(vc 12) until then.

## Voice input that works without an offline pack — 2026-11-13 (`fix/voice-online-fallback`, PR #60)

On the owner's phone, the composer mic showed Google's "Voice search isn't available", on Free and Pro alike. The app asked
the speech service for offline only (`EXTRA_PREFER_OFFLINE`), and the phone has no offline English pack. Not a
permission problem.

**Owner decision 1, "Offline, then ask"** (`eb9434f`):
- Offline stays the default.
- After a failed offline try, the composer offers **Try online voice** with consent.
- The choice is remembered (`UserPreferences.onlineVoice`, kept across sign-outs, never in backups) and can be changed
  in Settings › Privacy › Online voice.

**Owner decision 2, "Lucento listens itself"** (`963d1e8`). Google's window stopped listening a fraction of a second into a
sentence, and its silence settings are only hints:
- `VoiceSession` (pure, 19 tests) waits 10 s for the first words and 3.5 s of quiet after them, ends on Done, and caps a
  session at 60 s.
- `VoiceCapture` (SpeechRecognizer) and `ListeningSheet` run it.
- `RECORD_AUDIO` is asked on the first mic tap. If it's refused, the speech service's own window is used.
- Also updated: the manifest's `<queries>`, the CI release-permission allow-list, the Privacy Policy "Voice input"
  paragraph and the Play docs. No audio is collected.

**Verified:**
- The owner confirmed decision 1 on their phone. On 2026-11-13 the decision 2 test build (0.5.5-debug) "worked", with
  no beep or cut-off word reported (KNOWN_ISSUES VI-1, VI-2).
- Locally, before main's #65 was merged in: `testDebugUnitTest` 1,412/1,412, `assembleDebug` OK, `lintDebug` 0 errors.

**Owner's call (2026-11-13):** "push and merge when green". Then 0.5.8 with #65. At that release the Privacy Policy
date goes to the release day and the website's `/privacy` is re-exported (KNOWN_ISSUES VI-3).

## Tester round: investments, spending money, transfers, Show cents, shortcut colours — 2026-11-17 (PRs #98–#101)

Testers asked for five things. The proposal and the owner's choices are at
https://claude.ai/artifact/BaDJo5SeufjU886Z4TSAUU (private). The owner chose every recommended option: Show cents
rounds everywhere you read, Safe to spend shows the lower of two figures, scheduled transfers now, one Investment type
with kinds, and shortcut palette B.

| PR | Branch | What | State |
|---|---|---|---|
| #98 | `feature/show-cents` | Settings › Money › **Show cents**, on by default. Off rounds every amount people read to whole units, half-up; inputs, editors, a transaction's own view and every calculation stay exact | Merged `110d50b` |
| #99 | `feature/shortcut-palette` | Home's five shortcut tiles take each theme's own hues (`ShortcutTones`); every glyph but Goals (or a tile carrying more colour) stays ≥ 15 OKLab ΔE from the status tones | Merged `d045577` |
| #100 | `feature/account-picker` | Transfer From/To, the editor's wallet and the composer's account chips open one account picker, grouped into four families with balances; search and Recent above six accounts; signed debts | Merged `94a8254` |
| #101 | `feature/wallets-v14` | Room v14: **Investment** wallets with kinds and icons; per-wallet **spending money** and a card's counted credit; Safe to spend = the lower of the budget and the spending money, with an (i) that explains both; **scheduled transfers**; backup v5; Supabase migration `20261119090000` | Merged `fd32c7b` (2026-11-18, after OT-38) |
| #102 | `fix/sheet-bar-icons` | Bottom sheets set their status and navigation bar icons from Lucento's theme, not the phone's dark setting (found by the design review; app-wide, pre-existing) | Open: CI |

- **Reviews.** QA (lucento-qa) audited #101: not ready, with 2 blockers and 6 should-fixes (KNOWN_ISSUES TR-9). Its
  re-check found both blockers fixed and three second-order gaps in the card-credit rule, which are now fixed too. The
  design critic (lucento-design-critic) rejected round 1 at 3.0/5: a balance cut off at 200 %, 40 dp chips, four names
  for one idea, a noisy picker, debts without a sign, and tiles close to status colours. Round 2 gave REVISE at 3.5/5:
  every hard gate passed and 13 of 15 items were fixed. Its remaining items are fixed: the dark Awards tile, Scheduled
  at 200 %, consistent pickers and labels, whole-row switches, a live figure while choosing wallets, and wording.
  Round 3 gave REJECT at 3.4/5 on new 360 dp evidence: the (i) button cut Home's "Safe to spend" to "Safe to". The
  whole panel is now the button and the label wraps; the remaining items were fixed too (a live line TalkBack
  announces, the card line, balanced lines, wording, and #102 for dark sheets). The loop stopped at three rounds;
  instead, the fixes were checked on screen: the label is whole at 360 dp (100 % and 115 %), 393 dp (115 %) and 411 dp
  (100 % and 130 %), dark sheets show white bar icons, and the card line shows what was used. The critic expected
  about 3.9/5 once these were fixed; whether that is enough to ship is the owner's call.
- **Tests.** Full local `testDebugUnitTest`, 0 failures in each: #98 1,516; #99 1,514; #100 1,516; #101 1,564.
  `assembleDebug` and `assembleDebugAndroidTest` passed on the combined branch. CI is green on #98–#100: unit tests,
  lint, debug and release builds, the SQL suites, the emulator migration tests and the tour.
- **Database.** `docs/handover/apply-v14-wallet-kinds.sql` is on #101, with a copy in `Downloads\lucento-v14-database.sql`.
  It was tested on PGlite on top of every earlier migration: pasted once and twice, with and without a history table,
  then with live rows. All 19 checks passed.
- **Device.** The API 35 emulator ran the combined build of all four PRs (local branch `test/tester-round-all`). The
  opt-in `TesterRoundShotsTest` captured 30 screens at 100 % and 200 % text and at 360 dp wide, including a counted
  card, the state with no spending wallets, and the new sheets in dark. The test and the screenshots
  (`artifacts/tester-round-2026-11-17/`; rounds 1 and 2 in `r1/` and `r2/`) are local only, not committed.
- **Not checked:** a real phone (OT-39), sync against a project with the migration (OT-38), and TalkBack.

## Release 0.5.14 (version code 19) — 2026-11-19 (`release/0.5.14`)

The owner chose "Yes, build 0.5.14" for OT-41. The app changes since 0.5.13 are #106–#110 and #112:
- The three-part Monthly budget card, with the Budget tab aligned to it.
- The credit card as the default wallet for expenses, with "Assets" on Home and "Select wallet to use" in the composer.
- Select mode in Scheduled › All schedules: change the day or amount, pause or resume, delete. A warning shows when a
  loan or card already has a payment plan.
- Free limits in setup, and one Pro dialog at the tap everywhere.
- The Pro experience: a short message with Unlock when a wall is met again in a session, the Insights 6- and 12-month
  preview from the person's own months, and the theme try-on.
- The owner's calls: "See Lucento Plan" as the one label, a tried theme applied after a purchase, and setup lines that
  promise nothing Free doesn't give.

#111 changed docs only. Room stays at v14, backups at format v5 and sync is untouched, so no Supabase change is needed.
Owner: upload to Internal testing, then OT-41.

**Checked on a phone (owner, 2026-11-19):** "All worked fine" on 0.5.14 vc 19. OT-41 (setup at the Free numbers,
walls met twice, the Insights preview, the Grove try-on, Scheduled Select, TalkBack), OT-39 (the 2026-11-17 tester
round), OT-36 (AI answers on by default) and OT-35 (Play's two edge-to-edge warnings gone) are done.

## Release 0.5.13 (version code 18) — 2026-11-18 (`release/0.5.13`)

The owner said "Proceed" on building the tester round for OT-39 after OT-38 was applied to both Supabase projects. The app
changes since 0.5.12 are #98–#102: Show cents, the shortcut palette, the account picker, investments with kinds and
icons, spending money and the two-figure Safe to spend, scheduled transfers, and bar icons on dark sheets. #94 changed no
app code. Room goes to v14 (migration 13 → 14 tested in CI) and backups to format v5. Both Supabase projects already
have the v14 columns, so a 0.5.13 phone syncs investments, kinds, spending choices and scheduled transfers. Older phones
on the same account skip what they can't read; KNOWN_ISSUES TR-2 explains the placeholder they may show. Owner: upload
to Internal testing, then OT-39.

## Tester round 2026-11-18, part A — 2026-11-19 (`feature/card-default-wallet`)

The owner relayed tester requests and chose the options (memory: tester round 2026-11-18).
- **A credit card can be the default wallet** (`WalletType.canBeDefault`). Expenses go to it. Income, transfers and a new
  transfer's "from" go to the first cash, bank or e-wallet, but only while the card is just the saved default
  (`InterpretationContext.moneyWalletId` and `walletFor`). A card picked in the wallet bar, or the wallet the composer
  opened from, takes every type. The wallet editor's switch says so on a card.
- **Wording.** Home's hero now reads "Assets" (was "Wallets"; the figure is total assets). The composer's picker now
  reads "Select wallet to use" (was "Where should these go?").
- **Tests.** `CardDefaultWalletTest` (6). Full local `testDebugUnitTest`: 1,600 tests, 0 failures. `assembleDebug`
  and `lintDebug` pass.
- **Emulator (API 35, local capture):** the hero's "Assets", and the switch on a card. In the composer, with the card as
  default, "Lunch 250" went to the card and "Salary 35000" to Everyday bank.
- **Since:** part B (Scheduled bulk edit, the duplicate warning) merged as #108; part C below.

## Tester round 2026-11-18, part B — 2026-11-19 (`feature/scheduled-bulk-edit`)

Owner's choices: bulk Change day, Change amount, Pause / resume and Delete with Select all; for a duplicate, "Warn +
Adjust existing".
- **Select mode** on Scheduled › All schedules (`ScheduleBulkUi.kt`, `RecurringScreen`): the Select pill or a long
  press, checks on rows, Select all / "N selected" / Done, and an action bar above the dock. Change day and Change
  amount open sheets with a "What changes" list; Delete confirms first. The selection follows the filters and survives
  rotation.
- **Rules** (`domain/ScheduleBulk.kt`; CALCULATIONS "Scheduled outlook"): a new day moves the occurrence still waiting
  within its month or week, so none is skipped or added; the 31st survives short months; an end date keeps the number of
  dates left. A new amount is the whole payment (interest part cleared). Ended plans and plans in another currency are
  skipped and counted.
- **Writes** (`RecurringRepository.moveDay`, `setAmount`, `setPaused`, `delete(ids)`, `BulkResult`): one database
  transaction each, refused on a Partner's phone; changed rows get a new `updated_at`, so sync sends them.
- **Duplicate warning** (`DuplicatePlanWarning`, `ScheduleBulk.paymentPlansFor`): the scheduled-payment editor (the one
  funnel for planning a debt payment, from Scheduled, Debts, a wallet page or the wallet editor's reminder) warns while
  the card or loan already has an unfinished plan. "Adjust existing" opens `RecurringRoute(selectWalletId)`: All
  schedules filtered to that debt, its plans selected.
- **Large text.** From 130 % a schedule row gives its name the whole width with the amount underneath (at 200 % on a
  360 dp phone names had been cut to a few letters, before this change too), and the selection bar and the sheets’
  "What changes" lines stack.
- **Editor fix found on the way:** switching "Pay to" left the name filled in from the first debt ("One-time Payment -
  Visa card" for Car loan). A name or amount filled in from one debt now follows the next; anything typed stays.
- **Tests.** `ScheduleBulkTest` (20) and `ScheduleBulkRepositoryTest` (12): a rollback when a write fails part-way, a
  plan already on the chosen day skipped rather than rewritten, Mark paid after a move to the 31st (Nov 30, then Dec 31),
  a debt payment's new amount stored without an interest part, missing plans counted alike by every change.
- **Reviews.** Design critic round 1: REJECT 3.1 on one gate ("Add another" at 4.37:1 on the amber box). QA:
  conditionally ready, no critical or high defect; M1, restoring after Android ends the app (the selection was wiped
  while loading and a delete confirmation stayed armed). Fixed since: the contrast (deep blue, 6.6:1); previews that say
  when the next date stays, mark overdue dates, show a moved end date and sign amounts; the warning under "Pay to",
  announced, with the same question at Save; Change amount counting its changes and off when none; a sticky selection
  bar, the dock hidden in Select mode, a partial Select all, named multi-delete, firmer check rings (shared `CheckMark`
  and `RadioMark`: full outline colour); M1 (no pruning while loading, no sheet or armed dialog without a selection);
  "nothing changed" and failed writes said in place; weekday moves in the phone's week; a one-date-left plan no longer
  turning into "Once"; one auto-filled payment rule for the reminder hand-off and "Pay to"; guarded navigation.
  Critic rounds 2 (REVISE 3.8) and 3 (REVISE 3.94), no gate failing: amounts kept whole (a word joiner after the sign,
  no-break spaces in dates and before arrows), pinned sheet buttons with a hairline edge and "N dates will be overdue",
  a note built from the selection, an opaque band under the action bar, the Save dialog naming the plan(s), and one
  list decision per composition (a live `state.loading` read inside the list drew the empty state beside a loaded
  page for one frame); the "Adjust existing" selection waits for the plans and is applied once. Round 4: **APPROVE
  4.1**, no gate failing; P3s left in NEXT_TASKS.
- **Frame check (emulator, animations on, `dumpsys gfxinfo`):** Select all over 40 plans › Change day › apply › Done:
  59–60 frames, median 53–57 ms, 90th 105–117 ms. Existing screens measured the same way (a plan's sheet, scrolling,
  Upcoming): median 97–101 ms, 90th 200–250 ms. Every screen reads as janky here (emulated GPU: "issue draw commands"
  dominate, GPU timings aren't real), so this shows no regression against the rest of the app, not smoothness; a phone
  check is in NEXT_TASKS.
- **Emulator (API 35, local capture `ScheduledBulkShotsTest`, not committed):** every step at 100 % (Select, Select all,
  long press, Change day to the 31st, Change amount, Pause / Resume, Delete confirm, the duplicate warning and Adjust
  existing), again at 200 % text on a 360 dp screen, and in the dark theme. The first pass caught the Change day preview
  promising 2 changes where 3 were made (Rent moving from the 30th to the 31st only from December); the preview now
  shows the first date that changes, and a plan already on the chosen day is skipped.

## Owner calls — 2026-11-19 (`feature/owner-calls-2026-11-19`)

The owner answered the open calls from the tester round and the Pro experience (multiple choice):
- **Kept as built:** an earlier Change day makes this month's date overdue; a half-filled form closes on the way to the
  plan page; the message's "Unlock".
- **One label:** every button to the plan page reads "See Lucento Plan" (the profile menu's Pro card and the plan-ended
  sheet changed from "See Lucento Pro"; `ProCopy.WALL_SEE_PLAN`).
- **A tried theme bought from its note** becomes the theme once Pro arrives (`ThemeTrial.afterPurchase`, in memory;
  `LucentoRoot` applies it only when the real plan includes it and lets go once the saved theme is that one).
- **Setup promises nothing Free doesn't give:** "You can edit these any time in Wallets." under the accounts; "Setup adds
  up to 3 accounts and 2 cards or loans. You can edit them any time in Wallets." and "Setup adds up to 2 goals. You can
  edit them any time in Goals." where a save in setup meets a count.
- **Next:** build 0.5.14 for internal testing (owner: yes), for OT-41.

## Pro experience: walls met again, previews — 2026-11-19 (`feature/pro-repeat-taps-previews`)

**Merged** as #110 (`df50cb5`, 2026-11-19) after all six CI checks passed (unit tests, lint, debug and release builds;
Supabase suites; Room migrations and the screenshot tour on the emulator). Not in a release build yet.

The owner's monetization brief, audited first (`docs/monetization/PRO_EXPERIENCE_AUDIT_2026-11-19.md`: matrix, gaps).
Then the owner decided: keep the #109 dialog (plan fact first; See Lucento Plan / Not now), a short message on repeat
taps per session, no Home card, previews for Insights' 6 and 12 months and for Pro themes. Billing, entitlements, limits
and the data model are unchanged.
- **A wall met again** in the same app session is a snackbar with the plan fact, **Unlock** and a close button
  (`ProWallDialog` decides; `ProWallMemory` in the container, in memory only). Every `LucentoBottomSheet` has its own
  snackbar, and `MessageHosts` sends a message to the newest open sheet, otherwise the app's, so it never sits behind a
  sheet. The keyboard closes first, Unlock ignores taps in its first 600 ms (a quick second tap on Save can't take it),
  the action goes under the message from 130 % text (`LucentoSnackbar`), and Unlock from a sheet inside a sheet (goal
  sheet → wallet editor) or from Ask's sheets closes them before the plan page opens.
- **Insights' 6 and 12 months** on the Free plan are a preview (`LongPeriodPreview`): the person's own income and
  expenses for the period with a legend, softened (blurred on Android 12+), the axis blank and one spoken line, under
  the plan fact, See Lucento Plan and Back to 3 months; buying from it lands on that period.
- **A Pro theme is tried on** in the profile sheet (`ThemeTrial`, in memory): Lucento recolours at once, a note says
  nothing is saved and what it goes back to; it ends with Done, a theme of one's own (no flash), or the sheet closing.
- **Tests.** `ProWallMemoryTest` (2), `ThemeTrialTest` (1), `MessageHostsTest` (1), `MessageHostTest` (2), `ProWallTest`
  +1. Full local `testDebugUnitTest`: **1,655 tests, 0 failures**; `assembleDebug`; `lintDebug` 0 errors.
  `ScreenshotTourTest` leaves the locked period with "Back to 3 months".
- **Reviews.** QA: not ready on H1 (a repeat with the keyboard up showed its message under it); fixed with M1 (Unlock
  from nested sheets), M2–M6 and R1/R3/R4/R5/R9; R6–R8 logged (KNOWN_ISSUES PX-1…PX-3). Design critic: REVISE 3.3 with
  no gate failing (the preview reveals no figures); fixed: a sheet's message moved to its top (it covered Save), 10 s
  with an action, the tried theme's "Trying" state and description line, the note's plan button secondary and its
  copy, the snackbar's edge margin, the legend matching the softened bars, the note's motion. Owner calls left in
  NEXT_TASKS (drafts across Unlock, a tried theme after purchase, one plan-page label, the word "Unlock").
- **Emulator (API 35, local capture `ProExperienceShotsTest`, not committed):** 10 steps at 100 %, at 200 % text on a
  360 dp screen and in the dark theme: the first tap's dialog and the second's message (on Wallets and in the wallet
  editor), Unlock to the plan page (also from the goal sheet, which closes), the Insights preview with seven months of
  real data, Grove tried on and gone when the sheet closes. Evidence: `artifacts/pro-experience-2026-11-19/`.

## Tester round 2026-11-18, part C — 2026-11-19 (`feature/free-limits-pro-dialog`)

**Merged** as #109 (`929e3a6`, 2026-11-19) after all six CI checks passed. Not in a release build yet.

Owner's choices: in setup, hide the add options at the Free numbers (3 money wallets, 2 cards or loans, 2 savings goals)
and never mention Pro; everywhere else, a Pro dialog when they tap, for every wall.
- **Setup** (`GuideScreen`): at the limit the "Add another" chips give way to "You can add more later, from Wallets.",
  the title to "Your accounts" / "Your cards and loans" and the header to "These are the … you’re starting with." The
  setup editor leaves locked types out; another currency gets "Setup uses your main currency, PHP."; the goal sheet's
  Kept in offers no Add wallet at three money wallets. From 130 % text an account row puts its amount under the name,
  and the guide's content scrolls clear of the dock's fade.
- **One Pro dialog** (`feature/pro/ProWall.kt`, `ProCopy.wallTitle` / `wallBody`): the plan fact as title, the
  person's own number and what stays free as body, See Lucento Plan / Not now. It replaced `ProNote` in the wallet editor
  (type, currency, Save), Wallets and Home Add (the type sheet when money wallets are full, one dialog with both counts
  when everything is), Debts Add, Goals Add / Restore / editor, Insights 6 and 12 months, statements ("Save as PDF · Pro",
  "Share · Pro"), themes, Sync now, Smarter reading, Lucento Shared create / invite and Ask's AI allowance. A Pro
  Lifetime owner without Lucento Cloud hears about Lucento Cloud, in the dialog and in the plain notes beside it.
  Dialog titles break in balanced lines (shared `ConfirmDialog`).
- **Plan-rule fixes found by QA:** retyping a wallet into another kind counts as a new one of it
  (`WalletRepository.update`, `ProGates.walletAccess`); before, cash into a receivable or a third card got around the
  limits. While the wallet list loads the screen gives no early answer (`planWallets`). Editing a default card from
  Home's or Budget's card no longer clears the default (`WalletsViewModel.draftFor`).
- **Tests.** `WalletAccessTest` (6), `ProWallTest` (9), `PlanGateRepositoryTest` +1 (retype), `SyncPausedByPlanTest`
  updated. Full local `testDebugUnitTest`: **1,648 tests, 0 failures**; `assembleDebug`; `lintDebug` 0 errors.
- **Reviews.** QA round 1: not ready (H1 the retype bypass, H2 Pro in setup's goal sheet, M1–M4, L1–L12); fixed or
  logged (FL-2, FL-3, FL-4); the re-check confirmed the blockers fixed, then its M4 notes and N1 (no early dialog for an
  edit across the balance side, which a wallet's history can block on any plan) were fixed too. Design critic round 1:
  REVISE 3.2 with no gate failing; fixed: the dark evidence, the combined wall, the step titles, the statement markers,
  TalkBack labels on marked types, copy (jewellery, goals, setup's currency line). Owner calls left: the setup line's
  wording, dialog composition (NEXT_TASKS).
- **Emulator (API 35, local capture `FreeLimitsShotsTest`, not committed):** 12 steps at 100 %, at 200 % text on a
  360 dp screen and in the dark theme (the phone in night mode), from the guide at its limits to the plan page that See
  Lucento Plan opens. Found on the way: FL-1 (the guide's effect navigating before the NavHost's graph, in the Compose
  test only) and FL-5 / FL-6 (older layout at 200 %).

## Monthly budget card redesign — 2026-11-18 (`feature/budget-card-constraints`)

The owner asked for Home's Monthly budget card in three parts (how much is spent; Budget remaining and Spendable
wallets side by side; Safe to spend as the conclusion) and for the Budget tab to match.
- **Calculation reused, not changed.** Budget remaining is `SafeToSpend.budgetAllows`, Spendable wallets is
  `MoneyOnHand.allows`, Safe to spend is `SafeToSpend.amount`; `SpendingCapacity.of` only reads them. The brief's wallet
  figure subtracted only transfers that aren't expenses. Lucento's also subtracts bills and loan and card payments,
  because the verdict takes the lower figure and leaving bills out would promise money they are about to take
  (CALCULATIONS "Home budget card and the Budget tab"; KNOWN_ISSUES BC-1).
- **Home.** `BudgetOverview` (half gauge, "spent of" the budget, % pill, Budgeted • Unbudgeted), then
  `SpendingCapacityBlock`: the two columns and the verdict, each opening its sheet. "To watch" is its own card below. A
  past month closes on Remaining / Overspent.
- **Budget tab.** The same block under the hero this month, with Safe per day in the verdict; the hero's two tiles and
  its set-aside line give way to "Spent ₱X = ₱Y budgeted + ₱Z unbudgeted". Past months are unchanged. A card's counted
  credit can be edited from there, and "How Safe to spend works" opens Scheduled, as on Home.
- **Tests.** `SpendingCapacityTest` (22) and a new `SafeToSpendCaptionTest` case. Full local `testDebugUnitTest`:
  1,594 tests, 0 failures; `assembleDebug`, `assembleDebugAndroidTest` and `lintDebug` pass, with no lint finding in
  the changed files. `ScreenshotTourTest` and `ReconciliationTest` (both run by CI) now look for the new words.
- **Reviews.** QA (lucento-qa) found no wrong figure. Fixed:
  - "—" for wallets not counted yet;
  - the Budget hero's "Remaining" now says "before upcoming expenses";
  - the widget said "Bills due take what’s left" with nothing left before the bills, and now uses the card's words;
  - the gauge's figures move under it when "spent of" would wrap;
  - one TalkBack sentence per button: a child's description had hidden the amounts;
  - the wallet sheet's currency on another budget currency;
  - a self-consistent `SpendingCapacity.of(safe)`.

  The design critic (lucento-design-critic) gave round 1 REVISE at 3.3/5, with every gate passing. Fixed:
  - one term per idea across the card, its sheets and the widget;
  - chevrons and "Show how…" click labels;
  - no icon tiles or tinted boxes in the limits, which share one figure size;
  - the verdict's line at 0;
  - a 600 ms gauge.

  The owner answered OT-40 the same day. Bills stay in both limits (KNOWN_ISSUES BC-1). The order stays, but "the
  result of both budget and wallet shown", with fewer words and lines that balance between the two columns. So:
  - each limit is now its figure and two short parts: "₱30,920.10 unspent" and "−₱15,649.90 upcoming", "₱31,064.30
    in wallets" and "−₱29,394.30 due";
  - each part sits on one line when all four fit, otherwise every amount sits over its word in both columns;
  - the captions are gone, and the verdict's second line appears only when the wallets don't take part;
  - the Budget hero reads "Unspent of your ₱X budget".

  A second round followed, also from the owner. They asked for smaller fonts if needed, the description before the
  amount, amounts aligned, proper spacing and section separation, and subtle grids. B is now:
  - a subtle grid of two cells (outline, a centre divider, a rule under the headers);
  - in each cell, the centred name and a smaller figure (18sp);
  - then a ledger: "Unspent ₱30,920.10", "Upcoming −₱15,649.90", "In wallets ₱31,064.30" and "Due −₱29,394.30", with
    the amounts aligned right at one size.

  The hairline between the overview and the grid is gone; space and the grid's outline separate them.

  Not re-reviewed by the critic after these changes.
- **Device.** On the API 35 emulator, the local `BudgetCardShotsTest` (not committed) captured:
  - 23 states each at 411dp and 360dp;
  - 54 shots at 360, 393 and 411dp × 115, 130 and 200 %, light and dark.

  Round 4, after the owner's answers, checked the same sets again: 411dp shows both limits and Safe to spend on one
  screen, and the two columns have equal line counts at every size. After round 2 there is no clipping and no broken
  word. An accessibility dump shows each limit and the verdict read as
  one sentence with all their figures. Screenshots: `artifacts/budget-card-2026-11-18/` (local).
- **Not checked:** a real phone, TalkBack's actual speech (only the accessibility tree), a Partner's phone, and windows
  600dp or wider.

## Release 0.5.12 (version code 17) — 2026-11-17 (`release/0.5.12`)

The owner chose "Build 0.5.12 + publish site" after #95. The only app change since 0.5.11 is #95: Ask Lucento's AI
answers are on by default for a signed-in account, Free included (10 questions a month), and the first question shows
what would be sent before anything leaves the phone; the free plan's allowance now says "this month" (KNOWN_ISSUES
AI-1…4). The Privacy Policy and Terms go to version **2026-11-17**, and the website's /privacy, privacy table and FAQ
are published with this build (lucento-website PR #36). Owner: upload to internal testing, then OT-36 (and OT-35 if
0.5.11 wasn't uploaded: 0.5.12 carries its edge-to-edge fix too).

## Release 0.5.11 (version code 16) — 2026-11-16 (`release/0.5.11`)

The owner asked for a build that clears Play Console's edge-to-edge warnings on 0.5.10 ("Proceed"). App changes since
0.5.10: #92, Lucento draws edge to edge without the window APIs Android 15 deprecated (KNOWN_ISSUES E2E-1…3; screens
look the same), and #90, the Budget tab's "Safe per day" and the "You stayed within <month>'s budget" milestone count
spending from wallets in other currencies (BF-1, BF-2). On main but not in the app: #91, Mission Control › Marketing
(database and docs; OT-34). The Privacy Policy stays version **2026-11-14** and the website needs nothing: no network,
permission or data change. Owner: upload to internal testing, then OT-35.

## Release 0.5.10 (version code 15) — 2026-11-15 (`release/0.5.10`)

The owner asked for 0.5.10 to test on the phone ("Build 0.5.10 so I can test on my phone"). The only app change since
0.5.9 is #83: Add transactions reads messages on the phone straight away (Smarter reading is turned on only in Settings,
which warns that it's slower, and is locked for Free), and Ask Lucento has the microphone. On main but not in the app:
#76, Mission Control's database migrations (lucento-test only; production is OT-28), and documentation (#78, #79, #81,
#82). The Privacy Policy stays version **2026-11-14** and the website needs nothing: its "Voice input" section covers
any microphone in the app, and a spoken question is sent the same way as a typed one. Owner: upload to internal
testing, then OT-32.

## Release 0.5.9 (version code 14) — 2026-11-14 (`release/0.5.9`)

The owner chose "Release 0.5.9 now", with the website's /privacy. Carries everything merged since 0.5.8:
- #58 sign-out redesign (profile menu Sign out, keep or remove this phone's own records);
- #37 owner test rounds 1–2 (Lucento Plan page name, Pro ring only, password reset by code);
- #69 microphone not required, so Play's 13 devices without one come back;
- #68 toolchain (AGP 9.5.1, Gradle 9.6, Kotlin 2.4) and #67 safe library updates;
- #71 warnings that a purchase made signed out goes to the first account that signs in (KNOWN_ISSUES PB-11);
- #73 sign-out takes the account's server plan off the phone (PB-12).
The Privacy Policy stays version **2026-11-14** (released the same day); its account-emails line gained the password
reset code (#37), and the website publishes the same text (KNOWN_ISSUES VI-4). Owner retest: NEXT_TASKS OT-25 (still on
0.5.8's items) and OT-26.

## Release 0.5.8 (version code 13) — 2026-11-14 (`release/0.5.8`)

Carries #65 (Pro Lifetime can be bought again after a refund, `360f2f7`) and #60 (voice input that works without an offline
pack, `391e7d8`). The owner chose "Release 0.5.8 now". #58, #37 and the dependency updates go into 0.5.9.
- The Privacy Policy is version **2026-11-14** ("Voice input": the microphone permission and online voice). The website
  publishes the same text in lucento-website #29, with an "Online voice" row in the homepage's short privacy list.
- Owner retest: NEXT_TASKS OT-25.

## Mission Control › Auto-posting for Marketing — 2026-11-17 (merged: android #94 2e310e1; console `lucento-mission-control` #12 5bb1daa, deployed to Vercel; database not applied yet: OT-37)

Owner: "I want an interface that when I approved it Claude will handle the posting. I can connect all my accounts as
needed." Owner choice: all four platforms, TikTok via drafts. Contract: `docs/mission-control/v1/MARKETING_AUTOPOST.md`.

- **Database** (`supabase/migrations/20261118090000_mission_control_autopost.sql`, one paste: `docs/mission-control/
  APPLY_AUTOPOST.sql`, check: `VERIFY_AUTOPOST.sql`): connections holding encrypted tokens only, destinations, one job
  per post, version and platform (created within its lead time, stepped under a 3-minute lease, re-checked at every
  step), events and the clock (pg_cron every minute → pg_net → the console's tick, with its own Vault secret). Ten
  owner functions behind `mc.gate`, three ingest functions for the console. An account whose Check refused (or whose
  renewal failed) posts nothing until reconnected; every stop records why (design review round 2); a two-platform
  label is asked about by the platform still the owner's, and the summary keeps an approved post's problems until
  handled, with the clock's state (round 3). Not applied anywhere yet (OT-37).
- **Tests:** `tools/sql-verify` 11 suites, **518 checks, 0 failed** under both default-privilege variants
  (`autopost_suite.sql` 62); the races **11/11** on a real Postgres (MC6: two posting runs claim at once, each job
  handed out once); `catch_up_check.mjs` passes, including APPLY_AUTOPOST pasted twice (part E).
- **Console** (`lucento-mission-control` PR #12, `feature/autopost`): Settings › Auto-posting (the switch, the posting
  clock with a stalled-clock warning, each platform's account with Connect / Reconnect / Check / Disconnect and each
  destination's kept Check), the platforms' sign-in (fresh code, signed state cookie, a callback page that only passes
  the answer on to the gated /finish), `/api/autopost/tick` (claims up to 4 steps, runs them side by side under a
  40-second deadline, saves each result with retries; Facebook Reels, Instagram Reels, Page posts, YouTube, TikTok
  drafts), and on each post the plan, each platform's result with its link, and the owner's ways (Try again or Post it
  now, I'll post it by hand, It's posted, Don't post it there, Not posted after all). **273 unit tests**, lint, build
  and the bundle check green (`npm run verify`, Next 16.4.6); end to end on a fresh local stack **26 passed, 1 skipped** (the optional tour;
  `settings-autopost.spec.ts` 4, both phone specs); no sideways scroll at 360/393 px with 115–200 % text; studio
  audit 0 FAIL. Not run against the real platforms (OT-37, MC-12).
- **Reviews:** a security review (no critical or high; M1 and L1–L5 fixed, MARKETING_AUTOPOST.md §7) and the design
  critic's round 1 (REJECT 2.9: a post already in the apps would be posted again, a launch post promised, a dead
  "Posting now…" while off, "Went out" while a platform failed, a Check its status contradicted; all fixed, with
  touch targets, the Settings layout and the words). Round 2 (REJECT 3.1: "On" while nothing could post, a stopped
  platform with no way back, the footer contradicting the results, the fix least visible in Settings, Page posts
  silent, no warning where the owner looks) all fixed: the switch says what will post, stopped posts say why with Post
  it now / I'll post it by hand, one notice per account with Reconnect first, a plan line on every post, a line on
  Marketing and Overview, 48 px fields. Round 3 (REJECT 3.2: Marketing scrolled sideways from 130 % text; a stalled
  posting clock hidden where the owner looks; a step stuck after "Post it now" shown as "Posting…" and counted done; a
  false "No other post waits for you"; by hand asked per label, risking a second Facebook post; plus plan sentences,
  TikTok's "connect it" with no Connect button, old failures off Overview, and copy): escalated; the owner chose "fix
  all, review again", a confirmation before posting a day or more late, failures kept until handled, Next 16.4.6
  (MC-13) and every marketing post moved one week. All fixed but the week picker's count (notes). Round 4 (REJECT
  3.0: after "I posted it" the card still said to delete the post in the apps; no truthful way to skip one platform;
  "I posted it" as the main button beside "Post it now"; a "clock stopped" alarm right after "Post it now"; "No other
  post waits" counting approvals only; plus seven medium items). The owner chose: fix all, a critic confirms only the
  round-4 list item by item, then merge; per-platform "Don't post it there"; "Post it now" as the main button on a
  missed post; Overview always shows auto-posting's lines. All fixed (MARKETING_AUTOPOST.md §6, round 4; database:
  per-platform answers, `not_posting` / `not_posted`, `work_due_since`, History with the automatic steps). Studio
  `critic/reports/2026-11-17-autopost-ui-r1.md` … `-r4.md`.

## Mission Control › Marketing approvals — 2026-11-16 (database: `feature/mc-marketing`; console: `lucento-mission-control` `feature/marketing`; tool: lucento-studio `6edd819`)

Owner: "I want my marketing approval workflow be integrated here … a section in mission control so I can handle approval
and change requests there", then "go ahead". Contract: `docs/mission-control/v1/MARKETING_APPROVALS.md` (v2).

- **Database** (`supabase/migrations/20261117090000_mission_control_marketing.sql`, one paste: `docs/mission-control/
  APPLY_MARKETING.sql`, check: `VERIFY_MARKETING.sql`): packs, posts, immutable versions, files, an append-only log,
  launch details and Sunday numbers in `mc`; admin functions behind `mc.gate` (approve, ask for a change, skip, undo,
  the apps steps, launch with a fresh code); four ingest functions for Claude's tool with their own secret. Not applied
  anywhere yet (OT-34).
- **Tests:** `tools/sql-verify` 10 suites, **456 checks, 0 failed** under both default-privilege variants
  (`marketing_suite.sql` 94); the races **10/10** on a real Postgres 17.11 (MC3 now detects any lock wait: two calls
  by one admin queue on the gate's counter row); `catch_up_check.mjs` passes, including APPLY_MARKETING pasted twice.
  Console: `npm run verify` green (lint, types, **174 unit tests**, production build, bundle scan); end to end on a
  freshly seeded local stack **22 passed** (every desktop spec, launch day included, and both phone specs: 360 px at
  100 % and 200 % text, every post open; the screenshot tour is opt-in). Tool: **103 tests**.
- **Design:** three critic rounds (REJECT 2.9 → REJECT 3.2 → REVISE 3.3, all hard gates passing in round 3), the
  launch-day findings of round 3 and a second code review fixed after it; escalated to the owner (NEXT_TASKS).
- **Review** (four code reviewers with skeptics, then the design critic): an open redirect in the sign-in return
  (`/login?next=/.//host`) fixed in `safeNext` and again in the proxy; the race harness; a decision switching weeks;
  client state carried across weeks; a post still in the apps after an undone approval; the budget counting upload
  slots that can never be used; `missed` on posts already in the apps; the tool's resume, probe, launch details,
  profiles, reused names, retries and dropped notes. All fixed with tests.

## Mission Control V1 — 2026-11-15 (database: PR #76, merged `889e636`; console: private repo `jerald23simagala/lucento-mission-control`, PR #1 merged `d24038f`)

Owner: "I need my minimalist mission control done … Lets start building now" (Phase 4 go-ahead for the lean V1 in
`docs/mission-control/v1/`, decisions FD-V1…V15). Phase 5 (production) is the owner's: DEPLOYMENT.md, NEXT_TASKS OT-27…OT-30.

- **Database** (`supabase/migrations/20261116090000…090300_mission_control_*.sql`, one paste:
  `docs/mission-control/APPLY_MISSION_CONTROL.sql`): schemas `mc` (19 tables, private) and `mc_api` (43 functions, each
  behind `mc.gate`: admin allowlist, aal2, fresh TOTP for sensitive actions, hourly limits, refusal logging, append-only
  audit log). Complimentary Pro derived from `mc.entitlement_grants` (comp Lifetime = paid Lifetime's two rows; FD-V5
  constraint change); suspension; deletion after a 24-hour cool-off through the same cascade as `delete_my_account`;
  moving a purchase; support tickets; `public.submit_support_request` and `public.report_client` for the app; records
  view gated by a setting + reason + step-up; Google Play orders with Google's reported net; Resend events (KlaroPH's
  counted, never named); four pg_cron jobs. Not applied anywhere yet.
- **Tests**: `tools/sql-verify` — 9 suites, **362 checks, 0 failed** under lucento-test's and Supabase's stock default
  privileges (`mission_control_suite.sql`: 90). The combined apply file passes the suite on its own. The existing
  `entitlements_suite` caught a NULL bug in the first version of the FD-V5 constraint (fixed with `coalesce`).
- **Console** (`../lucento-mission-control`, Next.js 16.4.3, React 19.3, Tailwind 4, Chart.js 4): every page of
  PRODUCT_SCOPE.md; no service-role key, no Supabase key in the browser; proxy gate with 30-min idle / 12-h absolute
  limits; CSP nonce; Resend webhook (Svix) and daily cron (Play orders, checks, Crashlytics). `npm run verify` green:
  lint, typecheck, **31 unit tests**, production build, bundle secret scan. CI + weekly encrypted backup workflows.
  **Not yet exercised against a running database** (KNOWN_ISSUES MC-1).
- **Independent security review** (same day, before anything was deployed), every finding fixed on both sides: auth
  cookies capped at 12 h and an expired database session signs the admin out (H1); scheduling a deletion emails the
  account's own address at once (M1); signed-out support requests are marked not verified, capped per day, install and
  IP, and replies to them use a neutral subject; Send again and Delete as spam go through the gate; one ingest secret per
  purpose (the backup's can only report the backup) and the backup's secrets in a main-only GitHub environment (M7a); no
  full-access Resend key (M7b); no buyer country from Play orders; deleting an account removes its email-log entries.
  **Extended data** (active days per account, Play order amounts, the recipients of Lucento's emails) is collected only
  after the owner turns on a step-up switch once the privacy policy that lists it is live (M6, DEPLOYMENT.md H); until
  then the pages say what is paused. Wiring that switch found two bugs, both fixed and tested: Lucento's email count and
  bounce list keyed on stored recipients (now on the sending domain), and "counting since" included paused activity
  runs. Console commit `82cbf89`.
- **Local end-to-end run** (owner allowed it 2026-11-15): the console against the local stack (real PostgreSQL 17,
  PostgREST v14, an Auth stand-in, fictional data), in both privacy states. Playwright: 6 tests pass (sign-in with
  authenticator enrolment, every page, a grant and its revoke, the extended-data switch with step-up and audit) and a
  72-screen tour per state (light/dark, desktop/phone). It found and fixed: the stack itself had never started (Windows
  encoding, PostgREST's DLLs, a seed older than the schema); **revenue net counted a fully refunded order** (net is now
  after refunds, pending and cancelled orders never count, Lifetime sales count every completed sale; suite V4,
  DATA_SOURCE_INVENTORY updated); refund totals shown as +; MRR ₱0.00 instead of "—" without amounts; labels that said
  "as Google reports it" while paused; the entitlement table and the ticket composer cut off at 1280 px. Repository
  `jerald23simagala/lucento-mission-control` (private) created; console PR #1 merged `d24038f` (owner, 2026-11-15).
- **On lucento-test** (owner, 2026-11-15): DEPLOYMENT.md parts A–D (catch-up SQL from #79, APPLY, VERIFY, `mc_api`
  exposed, admin + secrets, Vercel TEST, first sign-in). Grant and revoke work. Console PRs #2–#6: deployment-guide
  fixes, sign-in diagnostics, the Supabase URL taken as its origin.
- **UI pass** (owner asked for the Impeccable skill and better UI/UX): console PR #7 merged `df195fe`, live on TEST. IBM
  Plex, a rem type scale in five steps (works to 200 % text), plain-language captions (Gross before refunds, Net always
  EST), phone layouts (stacked tables, sideways rows with fades, 44 px targets), a composer with a draft per kind of
  message, visible metric definitions, focus never under the sticky bar, a skip link. Checks: Impeccable 0 findings on
  15 routes; studio audit 0 FAIL on every route (phone/desktop, light/dark); no overflow at 130/150/200 %. Critic rounds
  (studio `critic/reports/2026-11-15-mission-control-r{1,2,3}.md`): REVISE 3.2 → REJECT 2.9 → REJECT 3.3 (one gate,
  layout integrity, two causes, fixed after the round and checked by the gates, not re-critiqued); escalated, and the
  owner chose to merge. Then the owner's layout choices: Needs attention strip first, tickets message first, keep Plex
  (branch `design/attention-strip`).

## Fast capture by default, voice in Ask Lucento — 2026-11-15 (PR #83, `feature/fast-capture-ask-voice`)

Owner: speech in Add transactions is fast, Smarter reading made each message wait for Gemini, and new users expect fast
input. Remove the composer's Smarter reading switch, warn whoever turns it on in Settings that it slows adding
transactions, and give Ask Lucento the speech button. Built in the worktree `../lucento-ask-voice` as PR #83 (CI green);
the owner approved the merge the same day, after main's #82 was merged into the branch.

- **Composer:** the Smarter reading switch row and its consent dialog are gone (`TransactionComposerScreen.kt`;
  `ComposerViewModel` keeps one `geminiReads` flag, `setSmartReading` removed). The note under the examples now says who
  reads messages: off, "Your messages are read on this phone, so their rows appear straight away."; on, that Gemini
  takes a few seconds and where to turn it off. The old line ("Nothing is sent unless you turn on cloud sync, crash
  reports, or Smarter reading.") left out exchange rates (on by default), usage counts and online voice. While Gemini
  reads, the reply says "Gemini is reading…".
- **Settings › Privacy & security › Smarter reading**, now the only place to turn it on: the row's note and the first
  paragraph of the consent dialog say "Adding transactions gets slower: each message waits a few seconds for Gemini's
  answer." (`SmartReadingCopy.SLOWER`). The dialog calls the screen "Add transactions", not "the composer".
- **Ask Lucento:** a microphone beside the question field, shown while the Voice entry feature is on (its description
  now names Ask Lucento). The composer's speech code moved into `feature/capture/VoiceInput.kt` (`rememberVoiceInput`,
  `VoiceInputSheets`), so both screens run the same listening, notes, online-voice consent and sheet; the sheet's
  example and the speech window's prompt come from the screen ("How much did I spend this month?" in Ask, which the
  phone answers itself). A spoken question is asked when listening ends; one that can't be asked yet waits in the field.
  Ask's placeholder became "Ask about your money…" to fit beside the microphone, and Send no longer clears typed text
  while the records are still loading.
- **Words are no longer dropped** when someone leaves mid-sentence with text already in the field: they follow it
  (`SpeechRequest.intoField`, both screens).
- **Smarter reading needs the plan** (owner's answer the same day, OT-33): `ProGates.smartReadingAllowed` (`AI_PRO`)
  gates the interpreter and the composer's `geminiReads`, so on Free no message waits for the proxy's `403`. In
  Settings the switch can't be turned on without it and a Pro note says why; a consent given earlier is paused (the note
  says so) and can still be turned off. `ProGatesTest` +1. Spoken questions in Ask stay asked at once (owner).
- **Found and fixed while checking:** the first version of `rememberVoiceInput` handed the launchers to `VoiceInput` in
  an effect. After the activity is recreated with the microphone question open (a rotation), Android hands over the
  answer while the launcher registers, before that effect ran, so a refused microphone said "no speech recogniser"
  instead of opening the speech service's window. The launchers are now set while composing, as the old composer code
  effectively did.
- **Tests:** `SpeechRequestTest` +5 (the microphone's route, `intoField`), `ProGatesTest` +1. Final `testDebugUnitTest
  assembleDebug lintDebug` on the finished code, plan lock included: 1,489 tests, 0 failures; lint 0 errors (no
  findings in the changed files).
- **Device check** (API 35 emulator `Lucento_Shots_API35`, which now holds this branch's debug build, before the plan
  lock; log in `artifacts/fast-capture-ask-voice/device-log.txt`, screenshots beside it, untracked): Ask shows the microphone; the first tap asks for the
  microphone; the listening sheet shows Ask's example; offline, the "can't turn speech into text offline" note offers
  Try online voice, whose consent saves the setting and listens online at once; refusing the microphone opens the speech
  service's window, also after a rotation with the question open (logcat: the activity relaunched), and closing it says
  "Listening stopped. You can type it instead."; at 200 % text the bar still fits with no overlap; the new placeholder
  fits beside the microphone. The composer's microphone, through the shared code, still shows its own example and says
  "Didn't catch anything" after 10 s of silence. A typed question in Ask is still answered once. **Not checked:** a real
  spoken question or transaction (the emulator's speech service produced no text) and the Smarter reading row and dialog
  (debug builds have no proxy, so the section is hidden). KNOWN_ISSUES FC-1 and FC-2, NEXT_TASKS OT-32 and OT-33.

## Budget tab counts foreign-wallet spending everywhere — 2026-11-16 (`fix/budget-screen-multicurrency`)

Found by the read-only custom-budget-periods audit the same day (private report
https://claude.ai/artifact/1xgQTq65RzqaSgDQ2JM88C). The owner asked to fix its D1 and D2 and to record custom budget
periods as a future task (NEXT_TASKS › "Custom budget periods").

- **D1, Safe per day** (KNOWN_ISSUES BF-1): the Budget hero built its plan from the translated ledger but passed the
  raw transactions and wallets to `budgetDailyFigure`, whose `CashflowCalculator.safeToSpend` planned again without
  foreign-wallet spending. With ₱6,000 in pesos and ¥10,000 worth ₱3,800 against ₱10,000 on 16 November, the hero said
  ₱200 remaining while Safe per day said ₱266.66; it now says ₱13.33, as Home does.
- **D2, month kept** (BF-2): `BudgetMilestones.monthToAcknowledge` read the raw transactions too, so a month that
  foreign-wallet spending took over budget could still be celebrated.
- **Fix:** both functions take the `TranslatedLedger` instead of lists, so untranslated data no longer compiles at
  either call site; `BudgetScreen` passes the ledger it already remembered for the hero. Only these two call sites
  changed; Home, the widget and Ask Lucento already passed translated data.
- **Tests:** `BudgetDailyFigureTest` +2 (foreign spending within budget gives Home's safe to spend; over budget leaves
  nothing per day), `BudgetMilestonesTest` +1 (over → no celebration, under → celebrated); both helpers now build the
  ledger as the screen does. Local `./gradlew testDebugUnitTest assembleDebug`: BUILD SUCCESSFUL, 1,492 unit tests,
  0 failures. Lint, the release builds, the SQL suites and the emulator jobs run in CI on the PR.
- **Docs:** CALCULATIONS (Safe per day; Budget month kept, which compares every expense, as the code always has, not
  budgeted spending only; a pointer to the periods rules), NEXT_TASKS "Custom budget periods" (future Pro: rules,
  decisions, outline), ROADMAP M3, CLAUDE.md (who reads `BudgetPlan`; the `budgets` table rule), KNOWN_ISSUES BF-1/BF-2.
- **Not checked:** a phone (logic only; the screen's layout is unchanged).

## Mission Control › Tasks, the owner's to-do list — 2026-11-20 (android #118, console #14 and #15, merged; tool in `lucento-studio/mission-control`; lucento-test set up and the 18 tasks sent 2026-11-21: OT-42 done)

Owner: "start streamlining my to do list to be part of the mission control. That will be our main interface for you and
me to update status of." Contract: `docs/mission-control/v1/OWNER_TASKS.md`.

- **Database** (`supabase/migrations/20261120090000_mission_control_tasks.sql`; one paste `docs/mission-control/
  APPLY_TASKS.sql` = auto-posting's migration, then Tasks', since both change the ingest-secret purposes; check
  `VERIFY_TASKS.sql`): tasks with a structured write-up (checked field by field, no markup, https links only, nothing
  key-like), immutable versions, append-only history, both read positions. A fifth secret purpose `tasks` (the
  auto-posting migration now lists it too, so pasting that file again after Tasks keeps the key). Owner functions
  behind `mc.gate` (board, one task, act: done / ask / note / not needed / reopen with the row version, 300 an hour,
  audited; Overview's summary; `nav_badges` + `tasks`), four ingest functions for Claude's tool.
- **Console** (laptop-first, owner 2026-11-20):
  - Tasks is second in the menu, with the count to do. The list (To do with one "Start here", Asked Claude, Waiting,
    Can wait, Closed) sits beside the open task once the page is 47rem wide.
  - Each task opens to its steps (tick boxes kept in the browser, Copy for text to paste, link hosts shown, other tasks'
    codes linked), notes and history.
  - A bar keeps the next move in reach and sticks only while it fits.
  - One dialog serves every action. Drafts are kept per action, and a stale task, no connection or a sign-in never
    loses what was typed.
  - Overview: tasks on the owner's turn overdue or due within two days, then Claude's answers and new tasks, each named
    by its code, then the count.
  - Four routes for the tool (`/api/tasks/*`, exempt from the session gate, bearer checked first). DEPLOYMENT.md part
    K is the owner's setup.
- **Design review**:
  - Rounds 1–3 were REJECT (2.97, 3.2, 3.2; reports in lucento-studio `critic/reports/2026-11-20-tasks-*`). The owner
    chose "a critic confirms the round-3 list, then merge".
  - The confirming critic found every round-3 P0/P1 fixed and one new P1 in the session layer: MC-14, the global
    idle sign-out with a "No connection" message. That is fixed, and the re-check said **CONFIRMED** (console da5dcdb).
  - Leftovers: MC-19.
- **Tool** (`lucento-studio/mission-control/tasks.mjs`): setup / check / status / show / push / set; settings outside
  every repo; checks every write-up with the database's rules before sending. **18 task write-ups** in
  `owner-tasks/` (OT-16, 25, 26, 28–32, 34, 37, 43–50; OT-34 became a laptop check and OT-37 is priority now on
  2026-11-20; OT-50 installs the app), drafted from DEPLOYMENT.md, PLAY_STORE_SUBMISSION.md,
  OWNER_ACTIONS_REQUIRED.md and the app's own strings, then reviewed: the page's real labels, OT-48 without the call
  the owner already answered, OT-37 without a paste the Tasks setup already did, OT-28 with "tell Claude first" before
  the switch (MC-15).
- **Verified**: `tools/sql-verify` 12 suites, 550 checks, 0 failed under both default-privilege variants (new
  `tasks_suite.sql`, 32); `catch_up_check.mjs` parts A–F all ok; console lint, types, 334 unit tests; the whole
  console end-to-end suite on a fresh local stack, 33 passed and 1 skipped (the screenshot tour), Tasks' 7 among
  them; all 18 write-ups accepted by the local database through the tool.
- **Desktop app**: the owner chose the installable app (`docs/mission-control/v1/DESKTOP_APP.md`). It is built on
  console `feature/installable-app`:
  - a manifest with a jump list and one reused window;
  - its own icon (Lucento's crest on the console's navy tile);
  - a taskbar badge with the tasks to do;
  - the manifest and icons public;
  - DEPLOYMENT.md part L for installing; the owner's task is OT-50.

  Notifications wait for the owner to ask.

## Mission Control on production, and the owner's setup day — 2026-11-21/22

- **OT-28 done (2026-11-21, about 20:45 Manila):** the console runs on production at `mission.lucentoapp.com` (CNAME at
  GoDaddy). Preview deployments keep lucento-test. Blob stores: `lucento-marketing-production` (Production),
  `lucento-marketing-test` (Preview). Claude's tasks and marketing tools now default to PRODUCTION.
- **The task list moved:** a copy of TEST first (`%USERPROFILE%\.lucento\mc-tasks-copy-TEST-2026-11-21.json`), then 19
  tasks pushed to production with their states and TEST history as notes (KNOWN_ISSUES MC-15). Other sessions have
  added OT-52 and OT-53 there since.
- **OT-16 done:** the new Play signing key's Android client (AC-1), Purchase history also lists Fraud prevention, and
  the reviewer account has a 90-day GRANT (checked read-only on production).
- **OT-29 done:** the weekly encrypted backup ran green (2026-11-21 19:27 Manila); `MC_BACKUP_SECRET` is in the GitHub
  environment, so later runs report to the production console.
- **OT-37, parts 1–8 done on TEST:** the Meta, Google and TikTok apps (`docs/ACCOUNTS.md`), and all five accounts said
  Works. **Marketing is on hold until Lucento is on Google Play** (owner, OT-34): parts 9–10 wait, and auto-posting on
  production will need its own short setup. Setup-doc lessons (Meta's login configuration, Google's Branding step, the TikTok sandbox and DNS check, Supabase's
  new Connect window) are in console `DEPLOYMENT.md` B3 and J4–J6 (console PR #17).
- **Session timeout (console #16, merged 2026-11-22, `1e7e310`):** using a page counts as activity (a session check at
  most once a minute while the owner clicks, types or scrolls), and Settings › You chooses the idle limit per browser,
  30 minutes to 8 hours (12 hours absolute). Unit tests 344/344; full chromium e2e on a fresh local stack 37 passed,
  1 skipped (the screenshot tour). SECURITY_CONSIDERATIONS.md §2 updated.
