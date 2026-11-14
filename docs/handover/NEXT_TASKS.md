# Lucento Android — Prioritized Next Tasks

**Updated:** 2026-10-31 by Claude after the initial audit and its Part II extension
(`docs/handover/CLAUDE_INITIAL_AUDIT.md`). This replaces the PDF-derived task list; its open items are folded in below.
**Baseline:** `7928c63` (`origin/main`), working on `feature/ai-development-workflow`.
**Issue IDs** refer to `KNOWN_ISSUES.md` (N-xx = new in this audit, O-xx / S-xx = QA matrices in the audit §17–§18).

> Production writes, production deploys, production env changes, merges and destructive Git operations need explicit
> owner approval.

## Owner to-dos (open, for the owner to run)

| # | To-do | Status | Why it matters |
|---|---|---|---|
| OT-1 | Apply `supabase/migrations/20261103180000_credit_card_billing.sql` to **`lucento-test`** (SQL editor, one `begin … commit` block; verify query in `PRODUCTION_MIGRATION.md`, top section) | **Done** (owner, 2026-11-03) | A v11 build sends `credit_limit_minor`, `statement_day`, `due_day` on every wallet row; until the project has them, wallet sync from a v11 build stalls (rows stay pending, nothing is lost) |
| OT-2 | Apply the same migration to **production** before any v11 release | **Done** (owner, 2026-11-03) | Same as OT-1, for release builds |
| OT-3 | Re-check sync on a phone after OT-1 (add a card with a limit and billing days, sync, see it on a second device) | **Done** (owner, 2026-11-03) | The only end-to-end check of the v11 sync columns |
| OT-4 | Deploy Ask Lucento's `/api/ask` | **Done** (2026-11-04): owner confirmed `consume_ai_quota` exists in production; PR lucento-website#6 (`280a905` endpoint, `9471726` env-name fix) merged as `e90326c` and deployed. Verified on production: `POST /api/ask` and `/api/interpret` → `401 sign_in_required` (settings resolve from the `LUCENTO_` names), a fake token → 401, `GET /api/ask` → 405, canonical URL and sitemap present again | Release builds now get AI answers once the user consents |
| OT-6 | Apply `supabase/migrations/20261105090000_receivable_wallets.sql` to **`lucento-test`** (it is on production only) | **Done** (owner, 2026-11-05); verified live: `wallets_type_known` accepts `RECEIVABLE` | It must precede OT-7 (migrations apply in order), and receivables can't sync on test without it |
| OT-7 | Apply `20261105120000_lucento_shared.sql`, then `20261105120100_lucento_shared_privileges.sql`, to **`lucento-test`**: SQL editor, or an interactive Claude session where `apply_migration` can be approved. Then run the security and performance advisors, and check `select count(*) from pg_policies where policyname like '%\_partner'` = 9 | **Done** (owner, 2026-11-05). Verified live by Claude (read-only, `docs/shared/SECURITY_MODEL.md` §8): all 16 function bodies identical to the repo, grants and RLS as designed, `%\_partner` policies = 9; advisors show only expected findings (KNOWN_ISSUES LS-K9). The SQL editor doesn't record migrations, so `list_migrations` doesn't show them | Lucento Shared can't be tried until then; the app says "isn't available on the server yet". Verified offline: `tools/sql-verify` 69/69 |
| OT-8 | Two-device test on `lucento-test`: `docs/shared/TWO_DEVICE_TEST.md` | **Done** (owner, 2026-11-06: "good for now", one phone toggling two accounts). One finding, fixed the same day: after the space was closed and recreated, the invited standalone account saw the new invitation only in Settings (KNOWN_ISSUES LS-K13) | The only end-to-end check of joining, collaboration, removal and leaving |
| OT-9 | Production: apply OT-6's and OT-7's migrations before any release that includes Lucento Shared | **Approved** (owner, 2026-11-06); the owner applies them (SQL editor). Production isn't visible to Claude: confirm here once applied | A v12 build syncs like v11 without them, but Lucento Shared needs them |
| OT-10 | Apply `supabase/migrations/20261106090000_multicurrency.sql` to **`lucento-test`** | **Done** (owner, 2026-11-06). Verified live by Claude (read-only): FX columns, `transactions_fx_known` / `wallets_fx_basis_known` with the explicit not-null tests, `ledger_settings` with RLS, select/insert policies and `authenticated` select/insert only; advisors show nothing new | A ledger with a wallet in another currency can sync its FX values |
| OT-11 | Try multicurrency on a phone with `artifacts/apk/lucento-debug-multicurrency-2026-11-06-r3.apk` (worktree): wallets in other currencies (rate filled in online by default), spending online and offline, transfers between currencies, the "≈ ₱…" lines, the ₱ subtotals, the wallet page and Insights | **Round 2** (owner, 2026-11-06): the bank card's name was still cut ("Bank of Co…" — the institution card's top row, not the compact face fixed in round 1); fixed in r3 | The only end-to-end check of rates, transfers between currencies and the main-currency totals |
| OT-12 | Production: apply `20261106090000_multicurrency.sql`, after OT-9 and before any release that includes multicurrency (v13) | **Approved** (owner, 2026-11-06: "good with me"); the owner applies it after OT-9. Confirm here once applied | A v13 build with a foreign wallet can't sync its FX values without it (uploads stay pending; nothing lost) |
| OT-13 | Supabase, **both projects**: Authentication › Emails › Templates › **Confirm signup** — subject "Welcome to Lucento — verify your email", body = `supabase/templates/confirm-signup.html`. Check Site URL = `https://www.lucentoapp.com/auth/confirmed` | Open (owner, dashboard only) | The app's "Check your inbox" says to tap **Verify email**; the default template says "Confirm your mail" and isn't a welcome |
| OT-14 | Try the new account onboarding on a phone (`feature/account-onboarding` APK): Get started → Keep your money safe → Sync with an account → Use email instead → Create your account (terms) → Check your inbox → open the email → "Email verified" → personalize. Also Google, and "Keep it on this phone only" | Open | The only check with a real inbox and a real link |
| OT-5 | Try AI answers on a phone: install `artifacts/apk/lucento-release-TEST-SIGNED-ot5.apk` (a **test-only** release build: production Supabase and proxy, signed with this machine's Android debug key, SHA-1 `49:AF:14:81:8D:53:F5:DF:68:42:03:3E:3C:1E:2E:DA:AA:AB:8C:75`; never distribute it). Sign in with an **email** account (Google sign-in likely fails: that key isn't registered for `com.lucento.app`), Insights › Ask Lucento › ask a question of your own (e.g. "How did October compare with September?") › Turn on AI answers › check the bold figures and "Answered by Gemini". Uninstall afterwards | Waits on OT-4 | The AI path can't run here; no signing secrets exist (`gh secret list` empty), so CI's release APK is unsigned |
| OT-15 | Apply `supabase/migrations/20261109090000_account_deletion.sql` (one function, `delete_my_account`) to **`lucento-test`**, then **production** before the first Play release | **Done** (owner, 2026-11-07). lucento-test verified by Claude (read-only): body md5 matches the file, `authenticated` only, `search_path=""`. Production: the owner's word (not visible to Claude) | Google Play requires in-app account deletion; until it is applied, Delete account answers "couldn't delete… email us" and changes nothing. Verified offline: `tools/sql-verify` account_deletion_suite 10/10 |
| OT-16 | Play Console: work through `docs/play/PLAY_STORE_SUBMISSION.md` — register the Play app-signing and upload SHA-1s for Google sign-in, App content forms, Data safety, store listing, a reviewer account with a GRANT row, internal then closed testing (12 testers × 14 days for new personal accounts) | Open (2026-11-07) | Without the SHA-1s, Continue with Google fails in every Play-installed copy |
| OT-17 | **Production is behind** (checked read-only by Claude 2026-11-07 after the owner switched the Supabase access to production): it has receivables, credit-card billing and `delete_my_account`, but **not** Lucento Shared, multicurrency, entitlements or Play purchases (OT-9 and OT-12 were approved but never applied). Paste `docs/monetization/PRODUCTION_APPLY.sql` (generated by `tools/supabase/build_apply.py`; one transaction; ends with a read-only check that should say ok nine times). Then add GRANT rows for the founders' accounts by uuid (SERVER_CONTRACT.md §7) | **Done** (owner, 2026-11-07). Verified by Claude, read-only: all nine checks ok, all 29 function bodies match the migration files, security advisors show only the expected notices. Website #11–#13 merged and live the same day. GRANT rows: still the owner's choice | Blocks the website merges (#11–#13: AI calls would fail) and any release build from `main` (sync would stall on the missing columns; plans can't be read). Rehearsed offline on a production-like database: applies cleanly, all 164 security checks pass after |
| OT-18 | **GitHub secret `GOOGLE_SERVICES_JSON`** (crash reports in CI releases): `gh secret set GOOGLE_SERVICES_JSON < app/google-services.json`. Claude's attempt was blocked by the permission classifier (secret-store write), 2026-11-08. Then confirm Crashlytics in the Firebase console after an opted-in test crash, and that the first run with it uploads the R8 mapping | Open (owner) | Without it the release AAB has no Crashlytics. `LUCENTO_GOOGLE_WEB_CLIENT_ID` was also fixed on 2026-11-08: its value began with the variable's own name (`LUCENTO_GOOGLE_WEB_CLIENT_ID=4311…`), which broke Google sign-in in every CI release |
| OT-19 | **Account emails** (docs/EMAILS.md › Setup): apply `20261110090000_account_emails.sql` to lucento-test, then production; create a Resend key with sending access to `send.lucentoapp.com`; Vercel Production env `LUCENTO_RESEND_API_KEY` and `LUCENTO_SUPABASE_SERVICE_ROLE_KEY`; redeploy the site; then sign up on a phone and check the welcome arrives once. OT-13 (Confirm signup template, refreshed 2026-11-08 without the "no ads" line) and the SMTP sender fix belong to the same pass | Open (owner) | Until then `/api/account/welcome` answers 503 (the app retries at the next launch) and purchases get no confirmation email; nothing else changes |
| OT-20 | Supabase, **both projects**: Authentication › Emails › Templates › **Reset password** — subject "Your Lucento password reset code", body = `supabase/templates/reset-password.html` (it shows `{{ .Token }}`, no link). Check Authentication › Providers › Email › Email OTP Length is 6–10 | **Done** (owner, 2026-11-09: round 2) | The app's new "Forgot password?" sheet asks for the code from this email; the default template sends a link and no code, so the sheet can't be completed without it |
| OT-21 | Supabase **production** (and lucento-test): Authentication › URL Configuration › **Site URL** = `https://www.lucentoapp.com/auth/confirmed` (OT-13 asked for it). Round 2: the verify link opened the homepage, so production's Site URL is still the homepage | **Done** (owner, 2026-11-09: round 2) | The page already exists: "Your email is confirmed — go back to Lucento: it notices and carries on by itself", and it explains an expired link |
| OT-23 | **Replace the GitHub secret `LUCENTO_PLAY_LICENSE_KEY`** with Play's Base64 RSA licence key (Play Console › Monetize with Play › Monetization setup › Licensing; `PLAY_CONSOLE_SETUP.md` step 5). It holds a SHA-1 fingerprint today (KNOWN_ISSUES PB-1). Then build 0.5.3 (vc 8), upload it to internal testing and retest a trial and a yearly purchase: Pro should show within seconds and production should get a `purchase_links` row | **Done** (secret 2026-11-11 22:27Z; 0.5.3 built and checked; Restore granted Pro on the owner's phone 2026-11-12) | — |
| OT-24 | **Switch on purchase checks and refund checks together** (owner's choice, 2026-11-12): fix Vercel env `LUCENTO_PLAY_SERVICE_ACCOUNT_JSON` (KNOWN_ISSUES PB-2); add `LUCENTO_RTDN_PUSH_TOKEN` with the Pub/Sub topic, the push subscription and Play's real-time developer notifications; add `CRON_SECRET` (KNOWN_ISSUES PB-3); redeploy production; then Send test notification and Restore purchases (PLAY_CONSOLE_SETUP steps 6, 8 and 9) | **Done, working** (verified 2026-11-13, production read-only): the owner's monthly test purchase was recorded on their account and then marked `REVOKED` (22:46–22:53Z, also in `revoked_purchases`), and the purchase check answered for the refunded Lifetime (22:41Z). The service-account JSON was fixed by the owner on 2026-11-12 | — |
| OT-25 | **Retest 0.5.8 (vc 13) from internal testing**: (1) buying Lifetime after a refund (KNOWN_ISSUES PB-8). Open the plan page, tap Buy Lifetime, pay with a test card, and expect Play's sheet, not "You already own this item", then Pro Lifetime. (2) Voice in the composer in the Play build (PR #60). For later refund tests tick **Remove entitlement** in Play Console (PLAY_CONSOLE_SETUP "Refunding an order") | **Open**, 0.5.8 carries #65 and #60 | The first real check that Google Play releases a refunded purchase when Lucento consumes it |
| OT-26 | **Check the signed-out purchase warnings in 0.5.9** (KNOWN_ISSUES PB-11): signed out, open the Lucento Plan page and expect the amber note above the plans ("You're not signed in. If you buy now…"). With a purchase made signed out still on the phone's Google Play, open Settings › Cloud sync (signed out) and expect the note above "Continue with Google" | **Open**, ships in 0.5.9 | Needs a release build (debug builds use the test store, which never makes a purchase without an account unless bought signed out) |
| OT-22 | **Security remediation (2026-11-10)**: work through `docs/security/OWNER_ACTIONS_REQUIRED.md` §1–§14 — Gemini paid tier, Confirm email, plan decision, then apply the four `20261111*` migrations (lucento-test → production), deploy the website branch `security/release-hardening`, internal-track purchase and refund checks | **Open** | The release stays NOT APPROVED until §1, §2, §5/§6, §13 and §14 are done (`docs/security/REMEDIATION_REPORT.md`) |

## Home-screen widgets — follow-ups (2026-11-12, `feature/home-widgets`)

- Merged as `50727be` (PR #57, 2026-11-13); not released yet. Next, a device pass on Android 12+ (the S8 runs Android 9:
  square corners, no picker preview) covering rounded corners, the preview layout and the launcher's day/night switch.
- TalkBack pass on both widgets ("Add a transaction", "Scan a receipt", "Speak a transaction", the body's summary).
- Parked by the owner (2026-11-12): Quick Settings tile, type-and-save notification, Share to Lucento. Their mockups are
  on the "Lucento Quick Add" canvas.
- Optional: a critic round on real widget captures (design-review-loop). The mockup rounds stopped at r2 REVISE when
  the scope changed.

## Pro welcome: an unboxing reveal replaces the spotlight tour (owner, 2026-11-12)

The tour's buttons were found dead on real phones (KNOWN_ISSUES PW-1). The owner chose to retire the spotlight tour,
keeping the excitement of the upgrade and the badge award. The fix on `fix/pro-tour-real-taps` is committed locally and
**held**: it ships with the reveal in one release (owner's choice). Nothing is pushed until then.

**Status (2026-11-12):** steps 1–3 are built on `feature/pro-reveal`, which is local and not pushed. The owner
picked "Deck, unwrapped" with "Scenes" art.

Critic round 3 on the app's captures returned REJECT, 3.1. Two gates failed: the dark plan title, and the controls at
200 % text. Every P0 and P1 is fixed and re-captured (studio `critic/reports/2026-11-12-pro-reveal-r3*`,
`qa/out/reveal-app-r3/`). Three rounds without APPROVE end the loop, so it is with the owner.

**Owner (2026-11-12):** "PR + review together".
- Push and open the PR as release 0.5.5 (version code 10): #55's refund guards, the PW-1 fix and the reveal.
- A fresh critic runs round 4 while CI runs.
- Merge only if CI passes and round 4 finds no hard-gate failure, then the AAB built from main goes to Downloads for
  internal testing.
- Also decided: keep the "AI adviser" name and the medal's rosette. The solid stage and ten cards one at a time stay
  (the prototype pick).

Round 4: REVISE 3.4, with no hard gate failing. Its two P1s (the brand name broken across lines; the service cards'
conditions hidden at large text) and most P2/P3s are fixed in `a1c994e`. They are re-captured from one APK in studio
`qa/out/reveal-app-r4/` (with `BUILD.txt`).

**Shipped to main (2026-11-13):** PR #56 merged as `51fac6b`, main CI green; the 0.5.5 (vc 10) AAB went to the owner's
Downloads for internal testing.

Next:
- (a) done: merged, AAB delivered;
- (b) on the Galaxy S8 from internal testing, check:
  - TalkBack: Next three times, then Previous once; one announcement per card;
  - how dense the haptics feel;
  - the swipe;
  - large text, where Android 14+ scales differently from API 28;
- (c) done: the owner's calls from round 3 (see above).

## Pro perk cards: new art (owner, 2026-11-13, `design/perk-card-art`)

The owner wasn't satisfied with the cards' drawn "Scenes" art and asked for better images. Three AI-generated
directions (painterly, 3D miniature worlds, gilded engraving; critic r1: REVISE / REJECT, studio
`critic/reports/2026-11-13-perk-art-directions-r1*`) were set aside; a flat hand-drawn 2D set with people followed, and
the owner then asked: "Too many people. Reimagine, make it simpler but we can add our mascot to make it livelier."

Then: "add subtle doodles in the background instead of plain blue … reduce empty blue spaces as much as possible …
after that I am good." Built on the branch: each card's art band is CardBlue with faint code-drawn doodles, one
soft-3D prop cut out of its ground, and `LucentoMascot` stepping a little in front of it in a pose per card
(`PerkArt.kt`); ten props generated with GPT Image 2.5 (`docs/design/illustrations/perks/`, exported by
`tools/illustrations/export_perk_art.py`, 425 KB with thumbnails); the deck decodes the next card's prop ahead of its
turn; the collection rows show each card's prop. Shown to the owner: artifact "Lucento Pro Cards".

Shipping (owner: "PR + review, merge when green"): PR #59, release 0.5.6 (vc 11). Critic app r1 REJECT (doodles under
the words in dark: fixed), r2 REVISE 3.45 with no hard gate; the owner then chose a **same-size art window** (the band
at most 0.55 of the card's width, which the pair fills: 12–20 % empty, measured). Also from r2: the pair stays
together at every band shape, room for the hop, each prop's shape read before it decodes, new props for cards 3 (a
microphone) and 5 (an hourglass), and the cheer kept for the award. Next:
- (a) done: critic r3 REVISE 3.45 with no hard gate, CI green; merged as `af4aae4` (2026-11-13) and the 0.5.6
  (vc 11) AAB went to the owner's Downloads;
- (a2) owner chose "Polish only" from r3 (`design/perk-card-polish`): one short float per card, doodles kept off
  the pair, card 7 without its sparkle, the hand on the prop only when pointing. Not chosen: one window per deck with
  copy trims, plain card under short cards, the dark band colour;
- (a3) from the polish checks (critic polish r1 REVISE 3.6, r2 REVISE 3.7, no hard gate): doodles fill the free blue
  around the pair again; the gap beside a standing mascot is set from the prop's own edge (`PerkPropEdges.kt`); a
  leaving card's mascot holds still; going back never blends two poses (each card face keyed by its card);
- (a4) polish r3 REVISE 3.6, no hard gate; merged as #62 `f7fbaf6` (2026-11-13, main CI green) and ships in 0.5.7
  (vc 12, PR #64). Its fixes: the next card is in place under a leaving card at once and a swiped card leaves from
  where it was let go; each card's own scroll position; balanced last lines. Large text
  re-captured (130 % light/dark, 150 %, 200 %) after the emulator's System UI froze on text-size changes (capture
  with a settle step: studio scratch `capture_lt.sh` logic). Open for the owner (three polish rounds done): doodle
  spacing refinements (bare patches beside the pair), one art window for the deck with copy trims, centring wide pairs
  versus keeping the torch glow inside, the AI feature's name and card 7's chip wording; a phone recording of leaving,
  swiping and going back;
- (b) on a phone: the mascot's hop as each card turns, the doodles' contrast on OLED, prop sharpness at 560+ dpi;
- (c) later, if wanted: show the art at 200 % when there is room (today it gives way at 175 %).

1. Design, through the studio loop: three reveal styles as tappable prototypes, scored by the design critic. The owner
   picks one.
2. Build the pick in `feature/pro/welcome/`. Pro's perks flip open one by one as cards, each saying where it lives,
   then the award (`ProMedalCeremony`) and the badge flight. It uses the same perks and copy as the tour's stops
   (`ProWelcome.stops`, `ProWelcomeCopy.stop`), and the Pro page replays it. Retire `ProTourStopOverlay`, the curtain's
   auto-advance and the screens' spotlight targets; the badge flight keeps the profile picture's.
3. Finger-tap tests for the reveal (the shared `Finger` helper), ScreenshotTourTest frames, and docs (MONETIZATION
   §6.5, CUSTOMER_COPY). Then one release with the fix.

## Tester round 3 — follow-ups (2026-11-11, `feature/tester-round-3`)

- **Second pass done (owner comments 1–5)**: reports moved to Insights under Ask Lucento with the shared, restyled
  report sheet; a new wallet statement (Insights and each wallet's page); a quieter income and expenses summary;
  Transfer + Withdrawal stacked beside Adjust; cards and loans get Record payment (From wallet / External payment);
  "What for" required; the bank's card logo on gallery faces.
- **Decided 2026-11-11 (owner)**: Withdrawal stays on cash, bank, e-wallet and savings — not "Other asset" wallets
  (valued by hand, no movements) or receivables.

- **Item 3 visual polish done** (owner chose "visual polish only for now", 2026-11-11): `drawCardFinish` (light, sheen, glass edge) on every wallet face, a soft day-theme shadow, a smaller type pill in the gallery.
- **Later, card networks**: a second PR. Needs a wallet `card_network` column (Room v14 + migration test,
  Supabase migration applied to production *before* the app that writes it, PulledRowRules, backup). Logos from
  `lucento-bank-assets/Global-Banking-Assets/card-networks` (Visa, Mastercard, JCB, Amex, Discover, UnionPay; owner
  accepted trademark use 2026-11-01). **No BancNet logo in the library**: the owner supplies an official file or it
  shows as text. Don't copy another app's card designs (the tester's reference is a mood, not a template).
- **Website**: `src/data/plans.ts` still lists "What your net worth is made of" as Pro, and `index.astro` mentions it;
  update both to the new Insights (donut, net worth over time) when this merges.
- **Play listing public**: flip `LucentoLinks.PLAY_LISTING_PUBLIC` to true so "Share Lucento" sends the Play link.
- Ask Lucento PDFs print the website and Community links as text (PdfDocument can't add link annotations); viewers
  auto-link them.

## Onboarding "Sunrise Path" — follow-ups (2026-11-08, `feature/onboarding-sunrise-path`, committed `221983f`)

| # | Task | Priority | Notes |
|---|---|---|---|
| SP-1 | Owner review on a phone; commit awards and onboarding as separate commits/PRs (only `GuideScreen.kt` is touched by both) | High | Needs owner authorisation to commit |
| SP-2 | TalkBack pass: rail sentence ("Step 1 of 3, Personalize. Screen 2 of 5."), chips as checkboxes, "Remove <name>" buttons, summary tiles' "Change …" labels | Medium | Semantics written, not heard on a device |
| SP-3 | Restyle the shared `EmailAccountForm` (labels above fields, live checks) to match mockup 03 | Low | Shared with Settings › Cloud sync, so left as is |
| SP-4 | Check 320dp width and API 36; the tour's light run in CI | Medium | Verified on API 28 1080×1920 only |

## Awards ceremony — follow-ups (2026-11-08, `feature/awards-ceremony`, uncommitted)

| # | Task | Priority | Notes |
|---|---|---|---|
| AW-1 | Owner review of the medals, cabinet and ceremony; commit + PR so CI runs the tour (new `07a`/`07b`) | High | Evidence in `artifacts/awards-ceremony/` |
| AW-2 | TalkBack pass: ceremony pane title, medal description, cabinet cells ("Replay the award") | Medium | Semantics written, not heard on a device |
| AW-3 | Decide whether a reached goal and a kept month's budget also get a (lighter) ceremony instead of the toast | Low | Product call; they still use `ConfirmationHost` |
| AW-4 | Check motion pacing on a real phone or the API 36 AVD; the API 28 AVD ran the animations about 3–4× slow | Medium | The card is complete from the first frame, so slowness never shows an empty stage |

## Lucento Pro — Phases 2–5 (2026-11-06, `feature/lucento-pro-architecture`; plan in `docs/MONETIZATION.md` §11)

| # | Task | Priority | Notes |
|---|---|---|---|
| LP-1 | Owner: review Phase 1 (`lucento-architect` pass on the model and the server contract), then decide on a PR to `main` | P0 | Nothing is restricted yet; merging Phase 1 alone is safe |
| LP-2 | ~~Owner: apply `20261107090000_entitlements.sql` to **`lucento-test`** together with GRANT rows~~ | **Done** (owner, 2026-11-06, via `docs/monetization/LUCENTO_TEST_APPLY.sql`, "Run and enable RLS"). Verified live by Claude (read-only): `entitlements`, `purchase_links`, `plan_limits` all with RLS; one policy (`entitlements: read own`); `authenticated` holds SELECT on `entitlements` only; `has_capability`, `plan_limit`, `consume_ai_request` security definer; `max_partners` body identical to the repo and = 1 for all 7 accounts (GRANT rows to 2027-05-06); `plan_limits` 10 / 50 / 400 / 1. The migration file gained `enable row level security` on `plan_limits` to match (uncommitted on `fix/plan-limits-rls`) | Production only with the gated release, grants by uuid |
| LP-3 | ~~Phase 2 — gates~~ **Built 2026-11-06** on `feature/lucento-pro-gates` (see CURRENT_STATUS). Website: `lucento-website` branch `feature/plan-aware-ai` committed locally — **owner: push and open the PR; merge and deploy only with the production migration** | Done | **1156/1156** unit tests (+12: `ProGatesTest` 4, `EntitlementRowsTest` 4, `SyncPausedByPlanTest` 3, `PlanGateRepositoryTest` +1 foreign-currency case), `assembleDebug`, lint 0 errors / 9 warnings (same set) |
| LP-3a | Phase 2 — gates (original scope): `planGate` → `entitlements::check` in `AppContainer`; foreign-currency wallet gate (needs the main currency in `WalletRepository.create` / `WalletChangePolicy`); Insights 6/12 + composition; PDF; themes; `backgroundSyncAllowed(cloud)` + onboarding restore; `SupabaseEntitlementRemote`; proxy → `consume_ai_request` (website PR: `ask.ts`, `interpret.ts`); default plan Free | P1 | §6.3 rules are the acceptance; `lucento-qa` pass |
| LP-4 | ~~Phase 3 — UI~~ **Built 2026-11-06** on `feature/lucento-pro-ui` (CURRENT_STATUS). Owner: push `feature/lucento-pro-gates` and `feature/lucento-pro-ui` and open their PRs (the session's permission check refused pushes) | Done | |
| LP-4a | Phase 3 — UI (original scope): `ProBadge`, locked / limit sheets, Lucento Pro page (plans from Play, comparison from `PlanCatalog.comparisonRows()`, Lifetime explainer, restore, manage), profile row, Settings page, first-start restriction notice, test-build plan simulator (`FakePurchaseSource.setPurchases`) | P1 | Copy: `docs/monetization/CUSTOMER_COPY.md` D |
| LP-5 | ~~Phase 4~~ **Built 2026-11-07** on `feature/lucento-pro-billing` + website `feature/play-billing-verify` (CURRENT_STATUS). The Google side is the owner's: LP-5b…LP-5e | Done | |
| LP-5b | Owner: merge `chore/target-sdk-36` (AGP 8.10.1, compileSdk/targetSdk 36) after CI passes. Play refuses uploads below API 36 since 31 Aug 2026 | P0 before any upload | Built and checked locally (see its commit); Android 16 behaviour not seen on a device (the API 36 emulator here crash-loops) |
| LP-5c | Owner: release signing (CS-20) and the first internal-testing upload: `PLAY_CONSOLE_SETUP.md` steps 2–3 | P0 | Products can only be created after a billing build is uploaded |
| LP-5d | Owner: Play Console products, offers, licence key (`LUCENTO_PLAY_LICENSE_KEY` repository secret, added 2026-11-09), service account (Vercel `LUCENTO_PLAY_SERVICE_ACCOUNT_JSON`, added 2026-11-09), RTDN topic and push subscription, `CRON_SECRET`, license testers: `PLAY_CONSOLE_SETUP.md` steps 4–10 | P1 | Product ids must match `PlayProducts` exactly |
| LP-5e | Owner: the gated release order — production migrations `20261107090000` + `20261108090000` with GRANT rows by uuid, then deploy the website branches, then the app (`PLAY_CONSOLE_SETUP.md` "Going live") | P1 | Deploying the website first makes every AI call answer 502 |
| LP-5a | Phase 4 — Play Billing 9.x (original scope): `PlayPurchaseSource` (SUBS + INAPP, pending, acknowledge, restore, `obfuscatedAccountId`), Vercel `verify` / `rtdn` / `voided`, Play Console products and offers, license testers. **Prerequisites**: release signing (CS-20), `targetSdk` check | P1 | `SERVER_CONTRACT.md` §3–§5 |
| LP-6 | Phase 5 — device matrix (`docs/MONETIZATION.md` §10), website pricing / FAQ / terms / feature copy, Play listing, production migrations with the gated release | P2 | |
| LP-7 | `ScreenshotTourTest` after Phase 3 (Pro page, locked sheet frames) | P2 | |

## Account onboarding — follow-ups (2026-11-06, `feature/account-onboarding`)

| # | Task | Priority | Notes |
|---|---|---|---|
| AO-1 | ~~Website `/auth/confirmed` wording~~ **Done 2026-11-07** on lucento-website `feature/site-refresh`: "it notices the confirmation and carries on by itself" | Done | |
| AO-2 | ~~Contact address, in-app account deletion + web link~~ **Done 2026-11-07** on `feature/play-readiness`: contact lucentoapp@gmail.com (owner's choice); Settings › Cloud sync › Delete account (docs/ACCOUNT_DELETION.md); `www.lucentoapp.com/delete-account`. **Still open: legal review** of the Terms and Privacy Policy (docs/LEGAL.md), and the developer name the deletion page names ("by Lucento") must match the Play listing | P0 for release | OT-15 applies the migration |
| AO-3 | ~~Website `/privacy` out of date, no `/terms`~~ **Done 2026-11-07** on lucento-website `feature/site-refresh`: both pages are generated from the app's text (`tools/legal/export_legal.py`) | Done | Deploy after the production migrations |
| AO-4 | "Forgot password?" for email accounts (`resetPasswordForEmail` + a reset page or app link) | P1 | Not built; today a forgotten password means a new account |
| AO-5 | Optional: a 6-digit code in the confirmation email (`{{ .Token }}` + `verifyEmailOtp`) as an alternative to the link, for people reading email on another device | P3 | The link works on any device already; the app notices it |

## Lucento Shared — follow-ups (2026-11-05, `feature/lucento-shared`, uncommitted)

| # | Task | Priority | Notes |
|---|---|---|---|
| LS-1 | Owner: OT-8 (OT-6 and OT-7 done 2026-11-05), then decide on commit / PR | P0 | Everything is uncommitted in worktree `../lucento-shared` |
| LS-2 | Run `MigrationTest` (incl. `migrate11To12…`) and `ScreenshotTourTest` on an emulator; CI runs the migration tests | P1 | Compiled, not run here |
| LS-3 | Independent review of the two migrations (`lucento-architect`) and of the sync changes (`lucento-qa`) before production | P1 | Not done in this session |
| LS-4 | Backup format: carry `wallets.classification` and `transactions.created_by`/`updated_by` (with read-back of the previous format) | P2 | RISKS_AND_DECISIONS.md §G.6 |
| LS-5 | History-row attribution chip; conflict notice naming the other person; "Shared with" line in the sync sheet | P2 | UX_FLOW.md "Not built yet" |
| LS-6 | `ScreenshotTourTest`: Lucento Shared states reachable without a second account (sign-in prompt, not sharing, Owner ready) | P3 | |
| LS-8 | ~~Workspace model~~ **Decided 2026-11-05: model A stays** (RISKS_AND_DECISIONS.md §G.11): no switcher, no separate shared ledger. Built: the "Shared with …" indicator (Home chip, profile-button badge, profile menu row) | Done | Gating later: a Partner's entitlement follows the Owner's plan |
| MC-1 | **Multicurrency with FX translation (owner request 2026-11-05):** foreign-currency wallets translated into a locked main currency by the average-rate method, realized FX gain/loss vs the average (gains → income, losses → expenses), optional month-end revaluation (unrealized), offline rate choice (last transaction rate or average), a free rate API. Changes core money rules (CLAUDE.md "never convert", CALCULATIONS.md), needs Room v13 + a Supabase migration (rates per transaction, a synced ledger main currency) and cross-currency transfers (today transfers must stay in one currency). Design questions sent to the owner; build on its own branch after Lucento Shared is committed | P1 | **Stage 1 built 2026-11-06** and committed on `feature/multicurrency`; owner round 1 addressed: docs/MULTICURRENCY.md. OT-10 done; OT-11 re-check; production OT-12 |
| MC-2 | Multicurrency stage 2: month-end revaluation (unrealized FX gain/loss per wallet with `fx_revalue`, month-end rates shared by every phone of a ledger), "use that day's rate" for offline-valued movements, transfers between currencies in the composer, market rates for scheduled and debt payments, FX lines in Ask Lucento's reports and PDFs | P1 | docs/MULTICURRENCY.md "Not built yet" |
| MC-3 | Independent review of `20261106090000_multicurrency.sql` and the FX engine (`lucento-architect`, `lucento-qa`) before production; `MigrationTest.migrate12To13…` on an emulator (CI runs it) | P1 | Not done in this session |
| LS-7 | **Before release — remind the owner at the last stage:** rewrite the invitation's share message in a more modern, product voice, and add the Google Play link once Lucento is listed | P2 | Owner request 2026-11-05. The text is `shareInvitation` in `feature/sharedspace/SharedSpaceScreen.kt` (subject "Join my Shared Space in Lucento") |

## Institution coverage — follow-ups (2026-11-05)

| # | Task | Priority | Notes |
|---|---|---|---|
| IC-T1 | ~~Owner: authorise a commit of the institution work~~ **Done**: `761d0ea` on `feature/institution-coverage` (not pushed; push + PR need the owner's go-ahead). The asset libraries are outside git | P1 | See CURRENT_STATUS top row |
| IC-T2 | Official vector logos for the 19 institutions that show a monogram card: PSBank, DBP, Metrobank (2025 logo), UNO, UnionDigital, OFBank (2025 logo), AUB, Bank of Commerce, PBB, BDO Network Bank, City Savings, CARD Bank, Sterling, PalawanPay, Veterans Bank, ShopeePay, CTBC, Taishin, LINE Bank (+ UBS) | P2 | Sites publish rasters only or refuse automated requests; needs press kits or the owner asking the banks. Workflow: `tools/institutions/expansion/README.md` |
| IC-T3 | Researcher: regenerate the library's `coverage_report.md`, `missing_assets.md`, `validation_report.md` and `master_inventory.xlsx` (they predate the expansion; the CSV is current) | P3 | `additions_2026-11-04.md` is the interim record |
| IC-T4 | "Not listed" path: let a wallet carry a typed institution name with a monogram | P3 | Needs a Room column + migration, sync and backup fields — a product decision, not done here. Today the empty state suggests naming the wallet after the bank |
| IC-T5 | Optional `rank` (market prominence) per country so browsing lists big banks first | P3 | Search already prefers the shorter (parent-brand) name |
| IC-T6 | Run `ScreenshotTourTest` and a light-theme pass of the picker | P2 | Not run in this session |
| IC-T7 | Legal: replace community (Commons) files for the most visible brands with official press-kit files before a public release | P2 | All logos are `not_cleared`; nominative use only |

## Theme system — follow-ups (2026-11-05, `feature/theme-system` → `main`)

| # | Task | Priority | Notes |
|---|---|---|---|
| TS-1 | Owner: try the five themes on a phone (profile menu › Appearance) | P1 | Commit + merge to `main` owner-approved 2026-11-05. See CURRENT_STATUS top rows for what was verified |
| TS-2 | `ScreenshotTourTest`: add one themed pass (Midnight, and Grove in light) over Home, Wallets, Budget, an empty state and the picker | P2 | The tour still runs Sunrise only; the themed shots in `artifacts/theme-system/` were taken by hand |
| TS-3 | Offer the theme in onboarding's appearance step (today: System / Light / Dark only) | P3 | Reuse `ThemePicker`; a new user starts on Sunrise |
| TS-4 | Mascot torch glow taking the theme's accent (Theme Studio proposal) | P3 | Not done: the glow is the mascot's wellness signal; needs the owner's call |
| TS-5 | First categorical chart (e.g. spending by category donut) should use `extendedColors.categorical` in order | P3 | The validated series exist in every theme but no chart reads them yet |
| TS-6 | Lottie files keep the Sunrise palette in every theme; review them in Mono and Aurora | P3 | Approved assets; re-colouring them would be a new asset proposal |
| TS-7 | Bring the JVM harness up to date (stubs for `luminance`, `ColorScheme.copy`, `lerp`, `R.drawable`, and `ThemeId` in its own file) | P3 | Already stale before this work — see KNOWN_ISSUES TS-K1 |

## Stabilization cycle (2026-11-02, uncommitted on `main` from 01cfd04)

| Item | Status | Evidence |
|---|---|---|
| GitHub Actions #108 (01cfd04) | **PASS** — all four repository variables reached the release step, enforcement on, the CI release APK embeds the exact URL / publishable key / Web client id (production project, no test project, no server-only keys) | run logs + downloaded artifact |
| Toolchain (N-19) | **PASS** — foojay removed (not needed), JDK 17–21 documented, daemon file per machine (gitignored); CI on 17 | local builds |
| Two-emulator sync QA | **PASS WITH NOTES** — basic, offline, conflicts, isolation, restart, invalid row PASS; two defects found and fixed (offline delete beating a newer edit; duplicate budgets per device); F-3 expired session NOT TESTED | `artifacts/sync-qa/sync-log.txt` |
| P2-1 Home "Am I on track?" | **PASS WITH NOTES** — Recent activity, Spendable now, loading skeleton; pace card kept; Quick links enriched by P2-5 | `artifacts/p2-ux/`, unit |
| P2-2 Sync transparency | **PASS** — chip states incl. Syncing / Needs attention, details sheet with Sync now / Try again, account-mismatch route; follow-up SQ-F4 (held-back rows) | device + unit |
| P2-4 Settings IA | **PASS** — hub + pages, one theme control, privacy said once, Back to hub, shared ViewModel | device + unit |
| P2-5 Module rediscovery | **PASS** — offers with Turn on (with requirements) and Hide | unit, device (partial) |
| P2-6 "Changed on another device" | **PASS** — notice only when this phone's unsynced change lost | device + unit |
| P2-7 Strings / plurals | **PARTIAL** — plural defects fixed; strings not moved to resources | code audit |
| P2-8 Scoped queries / paging | **PARTIAL** — shared whole-table query + 3 scoped queries; paging and startup profile not done | unit, device |
| P2-11 RLS suite | **PASS WITH NOTES** — read-only cross-user REST probe PASS on test; pgTAP suite written (54 assertions) but not executed (no Docker); write probe declined by the permission system | `artifacts/security/rls-verification.txt` |
| TalkBack / accessibility | **PASS WITH NOTES (emulator only)** — 10-screen label/target audit clean; live spoken traversal not driven | `artifacts/accessibility/talkback-emulator.txt` |
| P3 | **Partly done** — N-15 last-day copy, F-3 copy, delete-dialog copy, stale sign-in error, N-26 closed (by design), N-19 resolved. Not done: undo after save, N-09 usage-events limits (needs a server change), unused-index cleanup (production decision), visual/motion polish | — |
| Independent QA | Done (read-only). Critical: none. H1, M1–M7 and L1–L4, L9 fixed; others in KNOWN_ISSUES | — |
| Owner-confirmed (physical Samsung) | Hide in recent apps **PASS**, App lock **PASS** (owner) | owner |
| Deferred by the owner | release keystore, signing secrets, release OAuth client, production migrations, N-07 | — |

## Next programme — Unified product experience redesign (requested 2026-11-02)
Wallet and banking identity (institution registry, BankCard, InstitutionAvatar, picker), onboarding and account lifecycle,
budget education and month-specific budgets (a budget change applies to the selected month only), first-transaction
activation with light achievements, dashboard cognitive-load redesign. Started after this cycle; see its own section.

| Part | Status (2026-11-02) |
|---|---|
| C — month-specific budgets | **Implemented, uncommitted.** DB v10 (`MIGRATION_9_10`, schema `10.json` exported, `check_migrations.py` OK, instrumented `migrate9To10` PASS on emulator), `BudgetPlanner.effectiveAllocation`, `BudgetRepository.setMonthAmount/clearMonth/setFrom/setRecurring` (atomic), sync DTO + pull validation + scope-keyed folds, backup codec/merge (older files still restore), Budget sheet "Only <month>" / "<month> onward" with confirmation, past months month-only, guide + Budget copy. Docs: CALCULATIONS.md "Budget lifecycle", SYNC.md. Tests: BudgetLifecycleTest, BudgetChangeTest, SyncEngineTest (2), BackupTest (2). Two-emulator check PASS on `lucento-test` (`artifacts/budget-lifecycle/`). Production migration pending (RD-C1) |
| A — wallet/banking identity | **Implemented, uncommitted.** Pipeline `tools/institutions/build_institutions.py` (Android's own Svg2Vector from the Gradle cache + svgo pre-pass; WebP 144px app icons; identical icons shared) → `assets/banks/institutions.json` (380 institutions; card networks excluded), 305 card logos + 70 vector icons + 302 WebP icons, generated `InstitutionArt.kt`, report `docs/design/INSTITUTION_ASSETS.md` (16 fallbacks with reasons). `Wallet.institutionId` through repository, sync (always sent; pull-validated), backup (older files restore). `InstitutionRegistry`/`InstitutionRepository`, `LocalInstitutions`, `InstitutionAvatar`, `WalletIdentityTile` (rows, guide, schedules, composer), institution card face in `WalletCard` (templates, plates, dashed chip + hatch for debts, signed amounts), picker sheet + editor field + live preview. APK +1.47 MB compressed (measured). Decision: official app icon over recoloured glyph (44 incl. Chase, N26, Robinhood). Not done: CardNetworkIndicator (needs a stored network; deferred), dark-theme device check |
| B — onboarding | **Implemented, uncommitted.** Welcome = workspace choice (Start fresh · I already use Lucento → sign in to an account, or restore a backup file offline; never combined with a new setup). Three visible stages (`SetupStage`, `StageIndicator`) across onboarding and the guided setup. Focus: tracking spending and budgeting always included. "How do you pay?" (`SpendType`) replaces the feature switches (cards/loans turn on Debts & loans; the guide suggests those account types first, stored as `spend_types`). Backups step with three paths (account / file / this phone only); summary "Lucento is personalized" → "Continue to set up". The guide reuses the production New wallet sheet (now with the bank picker) and reads budget amounts with the lifecycle rules. Offline-workspace reconciliation: unchanged `FirstSync.ASK_MERGE` (asks before joining). Device: fresh path + returning sign-in path on emulator (`artifacts/onboarding-redesign/`). Not on device: backup-file restore end to end |
| D — activation & milestones | **Implemented, uncommitted.** Guide completion "You’re ready" (checklist in words + milestones earned). One-time + spotlight after setup (Home card + ringed dock +, static under reduced motion), cleared by the first entry or "Not now". Six light milestones (`Achievement`, `Achievements.earned/facts/toAcknowledge`) from records only — no streaks, points or nags; baselined silently on first run; each acknowledged once after the committed write (Milestone confirmation); Milestones sheet from Shortcuts. Local prefs: `achievements_seen`, `insights_visited`, `add_spotlight_seen` |
| E2 — dashboard to owner reference + Figma (`MhsrRL6KNILGrMzH1bYdmr`, frame 3:1366) | **Implemented, uncommitted, branch `feature/unified-redesign`.** Header "Dashboard" (+ sync, bell, profile); pinned second row "Overview" + month pill; every month-dependent section follows the one selected month. Net Wallet Balance card (Figma gradient #2768ED→#162D88, texture disc, (i) tooltip, Lucento symbol + white wordmark, 40sp balance, 3-bar month-end history + trend % only from a positive start, Wallets / Debt & Loans with the Figma icons — downloaded and converted to VectorDrawables `ic_dash_*`, `bg_dash_card_texture`). Past months show end-of-month balances (`BalanceHistory`). Monthly Budget card: spent of budget, % used, 7dp bar, status (Under budget / On track / Ahead of pace / Over budget), top two categories → Transactions filtered to that category and month (`CategoryTransactionsRoute`, month filter in Transactions). Shortcuts Goals · Insights · Milestones · Debts · Scheduled (icon squares + labels). Upcoming Scheduled Transactions (current month: due by month end, overdue first; two-line rows with due pill). Recent Transactions (the month's newest, shared transaction rows). Removed from Home: quick actions (still on +), "Where it went", top-goal card. Figma MCP Starter-plan limit hit after 2 design-context calls: budget, shortcuts and lists follow the file's metadata (sizes/structure) and the owner's reference image, not a full Figma render |
| E3 — owner refinements (2026-11-02) | **Implemented, uncommitted.** Card amounts never shrink their decimals (`fractionScale = 1f` on dashboard and wallet cards). Net Wallet Balance card as a physical card: ID-1 proportions, drop shadow, light edge, top sheen, faint guilloché lines, chip + contactless mark in the chip position; balance 32sp (−20%) directly under its label; header raised to match the bottom inset; trend is "vs last month" (2 points). Monthly Budget: subtle italic "Top 2 spending categories" note. Empty cards (scheduled, recent, budget setup) show Lucento on the left. Screens: `artifacts/dashboard-figma/card-v2-*` |
| E — dashboard (first pass) | **Superseded by E2;** Home order: total balance hero → compact budget pace card (word verdict, spent of total, used vs month bars, left/over + days; mascot narration and advice banner removed) → quick actions (Expense, Income, Transfer, Scan receipt) → "Where it went" (top 4 + Other) → recent (5) → Getting started (until the basics exist) → Shortcuts (+ Milestones, module offers) → top goal / scheduled only when present. Device: `artifacts/dashboard-redesign/` |

### Redesign close-out (2026-11-03) and what comes next
Done: fresh-install pass (PASS, `artifacts/fresh-install-2026-11-03/`), read-only QA review (fixes RD-QA-1…5, 9),
production runbook (`PRODUCTION_MIGRATION.md`), final report (`REDESIGN_FINAL_REPORT.md`). Owner decision: RD-C2 is
documented (every phone on v10 before month-only/onward amounts), no server guard.

| Priority | Task | Owner |
|---|---|---|
| 1 | ~~Apply the three pending migrations to production~~ **Done by the owner 2026-11-03**; confirm `supabase migration repair` recorded the history | Owner |
| 2 | ~~Commit `feature/unified-redesign`~~ **Committed 2026-11-03** (owner-authorized). Next: push and open a PR so CI runs schema drift, emulator migration tests and the screenshot tour (needs authorization) | Owner → Claude |
| 3 | Release v10 with a note that every phone on an account must update before using "Only <month>" / "<month> onward" | Owner |
| 4 | Device checks still open: dark theme on wallet/dashboard cards (RD-A3), live TalkBack traversal, guide signed owed amount (RD-QA-9); backup-file restore done by the owner (RD-B1) | Claude |
| 5 | Milestone baseline after first pull (RD-QA-6); recurring-stop fold rule (RD-QA-8) | Claude |
| 6 | ~~Trademark/licensing review of bundled logos (RD-QA-7)~~ Skipped by owner decision 2026-11-03 | — |

## Execution status — master backlog run (2026-11-01, uncommitted on `feature/ai-development-workflow`)

Statuses below were verified against the code, tests and devices, not copied from older notes.

| Task | Status | Evidence |
|---|---|---|
| P0-1 debug → test, TEST badge | Done (earlier) | P0-1 notes |
| P0-1 CI release config + fail-if-missing | **Done locally**; variables added by the owner 2026-11-02; first Actions run pending | `app/build.gradle.kts` (`LUCENTO_REQUIRE_RELEASE_CONFIG`), `.github/workflows/android.yml` |
| P0-1 debug `AI_INTERPRET_URL` override | **Done locally**: debug uses only `LUCENTO_DEBUG_AI_INTERPRET_URL` (build fails if it's production); Smarter reading hidden when blank | `app/build.gradle.kts`, `SettingsScreen.kt` |
| P0-2 (original) sign-out and session coordinator (R10-01, N-03, N-04, offline revoke) | **Done locally, device-verified** (offline revoke, kill mid sign-out); Architect-reviewed; QA review interrupted by a usage limit, to resume | `data/sync/SessionRevocation.kt`, `SessionRevocationTest`, `artifacts/p0-session-revoke/` |
| P0-2 onboarding safety | Closed with documented limitations | `P0_2_FINAL_ACCEPTANCE_REPORT.md` |
| P0-3 amount parsing | PASS | `P0_3_AMOUNT_PARSING_REPORT.md` |
| P0-3 follow-up: ReceiptParser alignment | **Done**: parity test proves every receipt reading is one `MoneyParser` allows; the one deliberate receipt convention ("Rp 25.000" = 25,000) is documented | `ReceiptParserAlignmentTest` |
| P0-3 follow-up: device check of transfer, adjust, schedule, goal, debt | **Done** (all five on device); found and fixed a silent disabled button in Adjust balance | `artifacts/p0-3-amount-parsing/device-log.txt` |
| P0-3 historical balance review | **Cancelled by the owner** (QA-Cash is test data) | — |
| P0-4 / P1-2 / P1-4 onboarding detection, first-sign-in safety, onboarding QA | Done (P0-2 device QA O-01…O-15) | `P0_2_DEVICE_QA_RESULTS.md` |
| P1-5 transactional pull | **Done locally**: each pulled row's read-decide-write is one Room transaction; queued deletes re-read and skip a row edited since (D3) | `SyncRepository.kt`, P1-5 tests in `OnboardingDataSafetyTest` |
| P1-6 CI fails on screenshot-tour failure (N-18) | Already done on `main` (`exit 1`) | `android.yml` |
| P1-8 budget conflicts | **Done locally** with the owner's choice "keep the latest edit" | `CategoryDedup.mergedLimit`, `CALCULATIONS.md`, tests |
| QA-N1 sign-out marker failure crash | **Fixed** | `SettingsViewModel.confirmAccountPrompt` |
| QA review of the batch (P0 coordinator, P0-3 follow-ups, P1-5, P1-8, P2-9, P1-7, composer) | **PASS WITH NOTES** (no blockers). Fixed from its notes: 3+ copy budget fold (1), revoke queued at tap time (2), constraints for live rows only + outbound row check (3), Adjust live messages (4), redacted `PendingRevoke.toString` (9), receipt text not returned as typing (10), `FittingText` never wraps a number and refits on resize (11), CI requires the Google client id (12), non-vacuous receipt parity test. Rest logged as QA-R* in KNOWN_ISSUES | agent report; tests below |
| P1-1 Google sign-in | **Blocked on owner**: register the debug SHA-1 as an Android OAuth client (exact steps in KNOWN_ISSUES G-01 / P1-1) | device log |
| P1-3 two-device S-matrix | **Partly covered**: restore on a second device, F-7, O-15 convergence, live constraint write; the full S-01…S-20 matrix is not run | device logs |
| P1-6 migration drift (N-07), production smoke test | **Blocked**: needs production access and owner approval | — |
| P1-7 accessibility | **Done locally**: adaptive dock, `FittingText`, Home large-text layout. Device: 2.0x, 360dp at 1.0x/1.3x. TalkBack pass still open | `artifacts/p1-7-accessibility/` |
| P2-3 budget split (D1) | **Done locally**: summary says "Spent = in your plan + outside it" | `BudgetScreen.kt` |
| P2-9 server constraints | **Done on `lucento-test`** (two migrations: `20261101120000`, `20261101180000` live rows only); production waits for approval | SQL checks, live app write |
| P2-10 app lock + hide in recents (D8) | **Done locally**: new deps `androidx.biometric` 1.1.0 and `fragment-ktx` 1.8.5; CI allow-list adds `USE_BIOMETRIC`/`USE_FINGERPRINT` | `artifacts/p2-10-app-lock/`, `AppLockSessionTest` |
| Composer: dismissing the wallet picker lost the message | **Fixed** (typed/spoken text returns to the field; a receipt asks to rescan) | `ComposerQuickEntryTest` |
| P2-1, P2-2, P2-4…P2-8, P2-11, P3 | **Not started in this run** | — |

## Decision register

| ID | Decision | Answer | Date |
|---|---|---|---|
| P1-8 | Budget amount when categories combine | **Keep the latest edit** (set beats unset; tie → smaller sync id) | 2026-11-01 |
| D1 | Budget "Spent" | **All expenses, shown as in-plan + outside-plan** (P2-3) | 2026-11-01 |
| D2 | Conflict policy | **Whole-row latest wins**, plus a "changed on another device" notice (P2-6) | 2026-11-01 |
| D3 | Wallet card look | **Real-card feel**: drawn EMV-style chip on every face (overrides the design system's "no chip"); balance at 22sp, never oversized. Still no number, expiry, cardholder or network mark | 2026-11-02 |
| D3 | Delete vs. later edit | **Newer action wins** (current) | 2026-11-01 |
| D4 | Wallet dedup on first sign-in | **Never merge; keep the warning** (current behaviour) | 2026-11-01 |
| D5 | R10-01 / offline revoke before beta | **Required before beta**, done locally (session coordinator) | 2026-11-01 |
| D6 | Release application ID | **`com.lucento.app`** (old `app.lucento` references are stale) | 2026-11-01 |
| D7 | Supabase plan | **Stay on Free**; leaked-password protection stays unavailable; keep the in-app checks | 2026-11-01 |
| D8 | App lock | **Optional lock + hide in recents** (P2-10) | 2026-11-01 |
| D9 | PR #10 (restore original onboarding) | **Close as superseded** — done by the owner | 2026-11-01 |
| D10 | Merged remote branches | **Delete merged branches** — 15 deleted; `release/v0.4.0`, `cursor/visual-style-refresh-0c6a`, `restore/original-onboarding` kept (unmerged) | 2026-11-01 |
| CI config | Release config in CI | Owner added **repository variables** `LUCENTO_SUPABASE_URL`, `LUCENTO_SUPABASE_PUBLISHABLE_KEY`, `LUCENTO_GOOGLE_WEB_CLIENT_ID` and `LUCENTO_REQUIRE_RELEASE_CONFIG=true` (public identifiers, so variables not secrets; Vercel keeps its own copies; `LUCENTO_SITE_URL` and `GEMINI_API_KEY` stay Vercel-only). Workflow and Gradle now use `LUCENTO_SUPABASE_PUBLISHABLE_KEY` (was `..._ANON_KEY`); the debug key stays `LUCENTO_DEBUG_SUPABASE_ANON_KEY`. Not yet proven by a GitHub Actions run | 2026-11-02 |
| AUTH-B | Sign out QA account B's sessions | **Approved and done** (not a deletion) | 2026-11-01 |
| F-8 | Auth email redirect | Owner set Site URL/redirect to `https://www.lucentoapp.com/auth/confirmed` on both projects. **Not independently verified** (needs one QA email per project) | 2026-11-01 |
| P2-9 | CHECK constraints | **Test project only** — applied to `lucento-test`; app write accepted live (EXPENSE 325 USD) | 2026-11-01 |
| B4 / P1-1 | Google one-tap | "Re-establish what worked." Diagnosed: this machine's debug SHA-1 is not registered — **owner action** (see KNOWN_ISSUES P1-1) | 2026-11-01 |

## Blocking prerequisites (owner actions)

| # | Action | Unblocks |
|---|---|---|
| B1 | Give the claude.ai Supabase connector access to `lucento-test`: move it into the "Lucento" org, or reconnect including its org (audit §15) | All QA on test |
| B2 | Provide test-project URL and publishable key for `local.properties` and CI | P0-1 |
| B3 | Approve the Vercel team consent link for `jerald23simagalas-projects` (audit §16) | Vercel audit, preview validation |
| B4 | Clarify the "Google Sign-In webhook" behaviour that used to work (audit §14.5) | P1-1 |
| B5 | Dedicated QA accounts (Google and email) on `lucento-test`; a second Play-Store device | P1-3, P1-4 |
| B6 | Optional: allow read-only `execute_sql` on production for catalog verification | P1-6 |

## P0 — Financial integrity, auth/session reliability, environment separation, security boundaries

**P0-1 Environment separation** (N-01, N-24). **Debug → test done locally on 2026-10-31 (uncommitted, awaiting
review).** The TEST badge (`EnvironmentBadge`, debug only) is also done locally. Still open: the CI release config
and fail-if-missing (N-24), and a debug override for `AI_INTERPRET_URL`.
- *Problem:* debug builds hit production; CI release builds ship without sync config.
- *Plan:* per-build-type config (debug → `lucento-test`, release → production from CI secrets); fail the release
  job when the config is missing; a visible TEST badge in debug; document it in `docs/SYNC.md`.
- *Depends on:* B2.
- *Risk:* low.
- *Validation:* `BuildConfig` host checked per variant in CI; a debug sign-in appears in test logs only.

**P0-2 Sign-out and session coordinator** (R10-01, N-03, N-04, auto-refresh race, offline revoke)
- *Plan:* an app-scoped, non-cancellable coordinator. Clear the in-memory session and cancel WorkManager *before*
  taking the sync lock. The launch resume path clears the stored session. A persisted, token-free revoke-pending
  marker retried on launch and on connectivity.
- *Risk:* medium (safety-critical). Get architect and QA review.
- *Validation:* JVM kill-point, cancellation, expired-token and offline tests; device kill test on test.

**P0-3 Strict amount parsing** (N-02, O-13). **Done locally 2026-11-01 (uncommitted), PASS recommended** — see
`P0_3_AMOUNT_PARSING_REPORT.md`. Follow-ups done 2026-11-01: `ReceiptParser` alignment (parity test) and device runs of
the transfer, adjust, schedule, goal and debt screens. (The historical balance review was cancelled by the owner.)

The original plan follows for the record.
- *Plan:* validate grouping (groups of 3); reject or confirm an ambiguous single separator; currency-aware.
- *Risk:* low (pure function).
- *Validation:* table tests for en-US, de-DE, fr-CH, JPY, BHD; composer, wallet-balance, budget and adjust flows.

**P0-4 Onboarding cloud-data detection** (O-03, O-07). **Implemented as "P0-2" on 2026-11-01** (uncommitted, awaiting
review). It also covers P1-2 (first-sign-in data safety) and the sync-before-onboarding trigger. See
`P0_2_ONBOARDING_IMPLEMENTATION_REPORT.md`. Remaining: on-device run of O-01…O-13 once a QA account and a clean
install are available.
- *Problem:* signing in on the normal setup path into an account with data completes onboarding locally, then
  duplicates wallets and deletes freshly picked categories.
- *Plan:* after any sign-in during onboarding, probe the account (all tables, ignoring soft-deleted rows). If it
  has data, route to the restore flow ("This account already has your data — bring it here") instead of new setup.
  Never create guided-setup wallets before the first pull.
- *Validation:* O-03 and O-07 on device; unit tests on the routing decision.

## P1 — Google sign-in, first-sign-in integrity, two-device validation, conflicts, release readiness

**P1-1 Google sign-in restoration (diagnose per layer)** (audit §14)
- Git history shows **no removed webhook or sign-in code**. The chain worked on production on 2026-10-27.
- *Plan:* B4 clarification → reproduce on API 36 Play-Store AVD with a test build → fix the failing layer using
  existing configuration (SHA-1 registration, test-project Google provider, Vercel Preview env) → clear stale
  sign-in errors between onboarding steps.
- *Depends on:* P0-1, B1, B3, B4.
- *Validation:* G1–G9 (audit §14.6) on test.

**P1-2 First-sign-in data safety** (N-16, O-05, O-11, `remoteHasData`). **Done within P0-2**; device-verified
2026-11-01 (O-05, O-06, O-11 PASS).

**P0-2 close-out (remaining):**
**Recommendation: P0-2 — CLOSED WITH DOCUMENTED VALIDATION LIMITATIONS** (`P0_2_FINAL_ACCEPTANCE_REPORT.md`), pending
the owner's review.
- **Accepted limitations:**
  - Physical-device background restore: DEFERRED. Run it on the first physical device.
  - Settings account-check failure: automated coverage accepted.
  - Empty account after a switch: automated only.
- **Release blocker, separate from P0-2:** F-8.
  - Read production's Authentication → URL Configuration.
  - Set the Site URL and redirect to `https://www.lucentoapp.com/auth/confirmed` on both projects.
  - Verify with one QA email each. This needs owner approval.
- **Pending authorization:**
  - AUTH-B: sign out QA account B's sessions. This is not an account deletion.
  - Optional: a third QA account for an empty-account device run.
- **Follow-ups (non-blocking):** QA-N1 to QA-N4 in `KNOWN_ISSUES.md`.
- **Done:** O-10, E-2, F-7 fix, the two restore tests, process death during a restore (emulator), and the release key
  guard (failure cases executed).
6. ~~**F-2:** fix the never-synced sign-out copy, and consider disconnecting without a wipe when nothing was ever synced
   (owner decision).~~ **Done on `feature/sign-out`** (owner decision 2026-11-12: ask keep or remove). Remaining:
   a device pass of the four sign-out cases with a real account on lucento-test, and TalkBack on the new dialog.
7. **F-3:** fix the guided-setup welcome copy when sync is on.
- *Plan:* delete only same-name duplicate categories; upload uniquely named ones; probe all tables; record
  `syncedUserId` only after the first pull succeeds and re-run the pull-first branch on retry.
- *Validation:* unit tests plus O-04/O-05/O-11 on device.

**P1-3 Two-device sync program** (audit §17, S-01…S-20)
- Execute the matrix on test with two devices; record evidence; file defects.
- *Depends on:* P0-1, B1, B5.
- *Acceptance:* the global acceptance list in §17.

**P1-4 Onboarding QA program** (audit §18, O-01…O-13). Execute after P0-4 and P1-2.

**P1-5 Transactional pull and conflict predictability** (N-05, S-05, S-06, S-11)
- Wrap applies in Room transactions and re-read inside them; correct the docstring.
- Owner decisions on whole-row LWW and on edit-resurrects-delete (D2, D3).
- *Validation:* failure-injection tests; S-11 and S-12 on device.

**P1-6 Release readiness**
- Reconcile production migration version drift (N-07) with a CI migration-list check on test.
- Release smoke test on production with a dedicated account (owner-approved, after P0-1).
- Make CI fail on screenshot-tour failures (N-18).
- One PR at a time with a green `main`; resolve PR #10.

**P1-7 Accessibility at large text and narrow width** (N-25)
- Adaptive dock; amounts never truncate; `fontScale` breakpoints; TalkBack pass on the composer, review, budget
  and sign-out dialogs.

**P1-8 Budget conflicts when categories are combined** (from the P0-2 acceptance review, finding C-1). **Not started.**

*Problem (Code-only).* Sync folds same-name categories (`SyncRepository.mergeDuplicateCategories`, `CategoryDedup`).
- **Which copy survives means nothing to the user.** The keeper is deterministic, the copy with the smaller random
  `client_uuid` (`CategoryDedup.kt:23`), but the choice can tombstone the account's own category and budget on every
  device (device QA: the phone's Rent 500 replaced the account's Rent 900).
- **No month on budget rows.** Budget rows have no month (`BudgetDao`), so a replaced limit applies retroactively to
  every month's reports.
- **Other devices follow.** They pull the fold: references move to the keeper and the losing budget row is deleted.
  This converged correctly on device (QA O-15), but the losing value is gone everywhere.
- **One budget amount is discarded.** When both copies have a budget in the same currency, the copy's budget row is
  deleted and the keeper's amount wins unless it is 0 (`CategoryDedup.mergedLimit`). Which amount survives is therefore
  also arbitrary, and the other is lost without a trace.
- **It happens in every merge,** not only onboarding: two phones that set up the same category, a backup merge, or the
  P0-2 Settings combine flow. Since P0-2.1 the combine dialog says one amount may be kept; the behaviour is unchanged.

*Owner requirement.* Lucento must not silently discard financial information. Keep both original values where
possible. Any automatic resolution must be deterministic, documented in `docs/CALCULATIONS.md` and the same on every
device.

*Options to decide (design before code).*

| Option | Deterministic | Discards a value | Notes |
|---|---|---|---|
| **A. Keep the larger amount** | Yes | Yes (the smaller one) | Simple. A conservative plan can't be loosened unnoticed, but a deliberate cut is undone. |
| **B. Keep the latest edit** (`updated_at`, ties by id) | Yes, given the server's clock clamp | Yes (the older one) | Matches the user's last intent on one device. Clock skew is bounded by the server's 5-minute clamp. |
| **C. Ask the user** | — | No | A "budgets to review" item listing both amounts. Most honest, adds a decision; needs a place in the UI that survives sync. |
| **D. Keep both, flag the conflict** | Yes | No | Keep the keeper's amount *and* record the other (both values plus source) so nothing is lost. Show a review prompt; resolve to A/B only on user action. |

*Recommendation for the design review.* D, with B as the displayed default until the user reviews. It is
deterministic, loses nothing, and the decision is explicit.

*Implications to cover.*
- **Keeper choice:** already deterministic. Decide whether it should be *meaningful* instead (for example the account's
  existing row, or the oldest `created_at`), still identical on every device.
- **History:** transactions and schedules move to the keeper (no amounts change). Because budget rows carry no month,
  any replaced limit rewrites past months' budget-vs-spent views; decide whether limits need effective dates.
- **Storage:** recording the second value likely needs a new local column or table plus a Supabase migration (new
  file, RLS, grants). Room version bump with an explicit `Migration`, a migration test, and schema JSON.
- **Cross-device:** every device must reach the same keeper and amount from the same inputs, including when the
  conflict is seen by devices in a different order.
- **Backups:** `BackupEngine` merge uses the same fold; keep it consistent.

*Tests (to write with the change).*
- Two devices, same category name, different budgets in the same currency, in both orders: identical result.
- One budget 0, the other set; budgets in different currencies (no conflict).
- An archived category on one side.
- A three-way collision.
- Repeated syncs (idempotent).
- Backup merge with a conflicting budget.
- With option D: the record of the second value and the review state survive sync and reinstall.

## P2 — Product experience (cognitive load first)

- **P2-1 Home: "Am I on track?"** One pace visual and one sentence; available cash vs. net worth; recent activity
  and upcoming schedules; a skeleton loading state (N-11); remove the sparse Quick links card (N-23).
- **P2-2 Sync transparency and recovery UX.** One status chip (Synced · Syncing · Needs attention) with a details
  sheet; a first-sync "downloading / reconciling" state; a persistent failure state with Try again; no repeated
  toasts.
- **P2-3 Budget comprehension.** Planned / spent-in-plan / outside-plan (after decision D1).
- **P2-4 Settings information architecture.** Account & sync, Your data, Privacy and Modules as separate pages; one
  theme control; privacy copy said once (N-22).
- **P2-5 Module rediscovery.** "More" entries with an inline Turn on (N-21).
- **P2-6 Multi-device conflict experience.** Surface "changed on another device" only when a user edit was
  overridden.
- **P2-7 Strings to resources with plurals** (N-10).
- **P2-8 Scoped SQL queries and paging** instead of whole-table flows (N-12); measure release startup and add a
  baseline profile (N-13).
- **P2-9 Server CHECK constraints** (test first) and validation of pulled rows (N-06).
- **P2-10 Security options.** Leaked-password protection (N-08, plan-dependent); optional app lock and recents
  privacy (N-17).
- **P2-11** pgTAP RLS suite and cross-user REST probe on test.

## P3 — Refinement

Visual polish and a unified hero family; motion refinement; undo after save; copy fixes (N-15); usage-event
validation and rate limiting (N-09); toolchain alignment (N-19); dependency upgrades; remove the main-thread wipe
`runBlocking` (N-26); unused-index review.

## Owner decisions

| ID | Decision |
|---|---|
| D1 | Budget "Spent": all expenses vs. budgeted categories only (N-14) |
| D2 | Conflict policy: whole-row LWW (current) or field-level merge for concurrent edits (S-05) |
| D3 | Should a newer edit resurrect a deleted record (current design), or should deletes always win (S-06)? |
| D4 | Wallet dedup on first sign-in: never, prompt, or auto-match by name and type |
| D5 | Accept R10-01 / offline-revoke risk for beta, or require P0-2 first (recommended: require it) |
| D6 | Release application ID (`com.lucento.app` in code vs `app.lucento` in old docs) |
| D7 | Supabase plan for leaked-password protection and session controls |
| D8 | App lock as an optional feature |
| D9 | Fate of PR #10 (restore original onboarding) before onboarding redesign |
| D10 | Cleanup of qa1/qa2 residue and merged branches |

## Execution discipline

Main session implements. Architect and QA agents review P0-2, P0-4, P1-2 and P1-5 before merging. One coherent
change per branch, tests with every change, exact reporting of what ran, no production writes without approval.

## Core screens — owner review round 5 (2026-11-04, `feature/core-screens-refinement`: `d4ff162` + goal archive)

As built: `docs/design/CORE_SCREENS_REFINEMENT.md` "Owner review round 5"; composer rule in `docs/CAPTURE.md`.

| Next | Why |
|---|---|
| **Owner: retry OT-5** (Ask Lucento on the phone) | PR #8 (fallback to another Flash model on 503) is merged and deployed (`50c8379`); a real answer still needs a signed-in phone. If it fails, check Vercel runtime logs within the hour (Hobby keeps ~1 h) |
| Optional: add goal archive / restore to `ScreenshotTourTest` | Verified by unit tests and by hand on the emulator (`artifacts/goal-archive/`), not yet in the tour |
| Optional: delete the `com.lucento.app` Android OAuth client made for the OT-5 test once testing is done | It authorises this machine's debug key for the release package |

| Check | Result |
|---|---|
| Unit tests | **886/886** after goal archive (+3: `PlanRepositoriesTest` only a reached goal archives; archived goals leave the active list, keep history, refuse money, restore; `DashboardRedesignTest` an archived goal keeps Goal Setter but not Home's savings). Before it, **883/883** (+7: `GoalIconTest` ×6 keys, older scenes, normalising, names, choice, suggestions; `BatchReviewTest` category now blocks; `PlanRepositoriesTest` icon keys stored; `WalletsOverviewTest` card default) |
| Lint | 0 errors, 9 warnings (same types) |
| `ScreenshotTourTest` | **PASS** on API 28 (45 shots, `artifacts/core-screens/review6/`); wallets shot now in card view, 12b rows; new 17a Goals, 17b–c New savings goal |
| Device checks (API 28) | New wallet: 4 fast up-flings and a fast flick back to the top keep the sheet open (a deliberate pull down still closes it); goal icon picker, Kept in (asset wallets + Add wallet); compact Budget rows; Safe to spend $0.00 with its caption; Wallets opens on cards |
| Website | PR #7 merged / deployed (`fc5d5fe`): model chosen from the key's list — production then logged 503 for gemini-3.8-flash; PR #8 (fallback) open, mocked checks pass, preview READY |

## Core screens — owner review round 4 (2026-11-03, committed `38e11a6`)

Owner comments: Scheduled without projection or hero, transfers apart from net flow, summary in Transactions' style;
transaction view with icons and an editor wallet dropdown; wallet editor in onboarding's style, wallet page action row
with a reconcile note, "View wallet"; Insights restyled after a reference, an Ask Lucento hero and the new Ask Lucento
page (AI over the user's records, preset reports as PDFs, safeguards; owner answers: Gemini proxy, summaries only, all
four reports); composer chat styled after a reference with real wallet icons. As built:
`docs/design/CORE_SCREENS_REFINEMENT.md` "Owner review round 4"; `docs/ASK.md`; `docs/CALCULATIONS.md` ("Scheduled
outlook", "Ask Lucento reports").

| Next | Why |
|---|---|
| **Owner: OT-4 / OT-5** (deploy `/api/ask`, then try AI answers on a signed-in phone; top of this file) | AI answers can't run until the proxy endpoint is live; reports and PDFs work without it |
| Owner review on a real phone: Ask Lucento, Insights, composer, transaction view, wallet page | Visual sign-off; API 36 still blocked here (CS-7) |
| Optional: a Settings shortcut to Ask Lucento's reports, and a Home entry point | Today it opens from Insights only |
| Optional: compact the Insights hero's "Net · last N months" pill at large text (CS-17) | It truncates at 360dp @ 1.3 (predates round 4) |

| Check | Result |
|---|---|
| Unit tests | **877/877** (+22: `ReportBuilderTest` ×8 net worth incl. change and empty months, cash flow, budget vs actual, debts, top categories, answer sentences; `AskTest` ×9 routing, months, snapshot never carries notes or other currencies, figure fill-in, unknown ids, unverified amounts, links, follow-ups; `AskRequestTest` ×2; `AskHeroTypingTest`; `InsightsTest` +2 change percent and axis; `ScheduleOutlookTest` net without transfers; `AccountSetupResetTest` AI consent cleared) |
| Lint | 0 errors, 9 warnings — same types as before (AutoboxingStateCreation is now reported as information) |
| Build | `assembleDebug`, `assembleDebugAndroidTest`; APK `artifacts/apk/lucento-debug-review5.apk` |
| `ScreenshotTourTest` | **PASS** on API 28, final code (42 shots in `artifacts/core-screens/review5/`); new: 13a Ask Lucento, 13b preset answer, 13c report sheet |
| Device checks (API 28) | Ask: preset answer with report card; free question without AI → honest "what I can do"; report month stepper (an empty month now says no wallets existed); Save as PDF via the system picker → `Download/Lucento-Net-worth-statement-2026-11.pdf` and `…Budget-vs-actual…` rendered and inspected (fixed: right-aligned figures, word spacing via scaled text). Composer with keyboard (fixed: double nav-bar gap at the root; compact save bar while typing; greeting scrolls with the conversation); transfer view Route card; 360dp @ 1.3 (fixed: report card title, Transfers caption); light + dark |
| Website | `lucento-website/src/pages/api/ask.ts`: eslint clean, `astro check` no errors in it (the repo's 29 existing errors are in the e2e Playwright files) |
| Not run | AI answers end to end (no proxy in debug; OT-4 / OT-5); sharing to another app; API 36 (CS-7) |

## Core screens — owner review round 3 (2026-11-03, committed in `9021ed0`)

Owner comments: dialogs aligned and livelier (Add Wallet's card = the wallet being built), category required on scheduled
transactions, a livelier bell; budget card Safe to spend sticky (0 and red when over), Bills due always zero, Assets % /
Liabilities % / net worth formula on one row, Upcoming coloured by kind; Scheduled page refactor (month hero, filters with
From–To, 3-month projection, default to due today and this month); profile picture from Google or uploaded. As built:
`docs/design/CORE_SCREENS_REFINEMENT.md` "Owner review round 3"; rules in `docs/CALCULATIONS.md` ("Safe to spend",
"Scheduled outlook"); privacy in `docs/SYNC.md`.

| Next | Why |
|---|---|
| ~~Owner: OT-1 / OT-2~~ (credit card billing migration) | Done by the owner 2026-11-03, with OT-3 (sync checked on a phone) |
| Owner: try "Use my Google photo" on a phone signed in with Google (CS-11) | The only path not exercised here (no Google sign-in on this machine's debug key) |
| Owner review on a real phone: Scheduled page, bell, New wallet, budget card | Visual sign-off; API 36 still blocked here (CS-7) |
| Optional: a one-time prompt to give pre-round-3 schedules a category (CS-12) | They keep working; editing asks for one |

| Check | Result |
|---|---|
| Unit tests | **855/855** (+12: `ScheduleOutlookTest` ×7 flows, occurrences, overdue, paused / ended / paid, card payments, totals per currency, projection; `HomeBudgetCardTest` bills in any category + Safe to spend at 0; `CashflowCalculatorTest` updated; `PlanRepositoriesTest` category required; `ProfilePhotosTest` ×4 host check, size suffix, metadata, sampling; `AccountSetupResetTest` photo cleared) |
| Lint | 0 errors, 10 warnings — the same set as earlier runs (round 2's handover said 9: miscount) |
| Build | `assembleDebug`, `assembleDebugAndroidTest`; APK `artifacts/apk/lucento-debug-review4.apk` |
| `ScreenshotTourTest` | **PASS** on API 28, final code (39 shots in `artifacts/core-screens/review4/`); new: 12g new wallet, 18 / 18b / 18c Scheduled; seeds now give every scheduled bill a category and add a loan instalment |
| Device checks (API 28) | Budget card (Bills due −$1,594.98 = the Scheduled page's expenses; Safe to spend $0.00 red, "Bills due take what’s left"), Upcoming colours, bell, Scheduled hero / projection / agenda / All schedules, From–To picker applied (Nov 8 – Nov 29: summary and list follow), schedule editor refuses a bill without a category, profile photo picked from files and removed, Wallets legend one line at 411dp and 360dp @ 1.3, light + dark. Fixed on the way: a picked photo was refused (bounds-only decode returns null), hero pills cut at 360dp @ 1.3 (now wrap / one line per flow), kind filter clipped at 1.3 (pills), a later occurrence's "projected" note cut off, projection card too tall |
| Not run | Google photo download (CS-11); API 36 (CS-7) |

## Core screens — owner review round 2 (2026-11-03, uncommitted on `feature/core-screens-refinement`)

Owner comments: budget formula without + − = (Remaining green / Overspent red, Bills due red, Safe to spend green only
above zero, else hidden), To watch top 5 with "See all" bottom right; mascot on empty upcoming lists; wallet page
Transfer / Payment + Adjust balance (out of Edit wallet) for assets and cards; instalment loan card (Principal, Paid,
Remaining, segmented bar, "x of N installments paid"); rate in the card title; credit card editor with credit limit,
statement day and due day (no One time / Monthly); due day as a payment reminder excluded from Safe to spend; card
shows Credit limit, Amount due, Available. As built: `docs/design/CORE_SCREENS_REFINEMENT.md` "Owner review round 2".

| Next | Why |
|---|---|
| **Owner: apply `20261103180000_credit_card_billing.sql` to `lucento-test`, then production** (`PRODUCTION_MIGRATION.md`, top section) — tracked as OT-1 / OT-2 | A v11 build sends the new wallet columns; until a project has them, wallet sync from v11 stalls (rows stay pending) |
| Owner review of the new wallet faces and Home formula on a real phone | Visual sign-off; API 36 still blocked here (CS-7) |
| ~~Optional: list card payments on the Scheduled screen's Upcoming tab too~~ | Done in round 3 (the agenda lists them and they open the card) |

| Check | Result |
|---|---|
| Unit tests | **843/843** (+14: `DebtProgressTest` ×10 cycle, dues, available, loan progress, terms; card terms round trip in `DebtPaymentTest`; formula rules in `HomeBudgetCardTest`; card billing + v2 files in `BackupTest`; card fields across two devices in `SyncEngineTest`) |
| Lint | 0 errors, 10 warnings (pre-existing; recorded as 9 at the time — miscount, same set) |
| Build | `assembleDebug`, `assembleDebugAndroidTest`; schema `11.json` exported; `tools/check_migrations.py` 10 → 11 matches |
| `ScreenshotTourTest` | **PASS** on API 28; seeds a card with limit and billing days and a 24-instalment loan with 3 repayments; new shots 12c–12f; Home asserts "Bills due" (this month's Safe to spend is negative, so hidden) |
| Device checks (API 28) | Card, loan and bank pages, card editor, Home budget card, Upcoming with the card payment, bell — 411dp day/night and 360dp @ 1.3. Fixed on the way: card title cut to "Vis…" (now wraps), footer amounts misaligned when labels wrap (now a label/amount grid), card-due caption cut, bell listed card payments before overdue items, a v2 backup's debt rows were refused (codec now treats the new keys as optional) |
| Not run | `MigrationTest.migrate10To11` (instrumented; CI's emulator migration job runs it); API 36 (CS-7); sync against a real project (migration not applied) |

## Core screens — owner review round (done 2026-11-03, committed `b139646`)

Owner comments: guide budget rows (icon + category left, amount right); onboarding "alive" (icons on texts, styled rows,
e.g. currency); Home: shortcuts right after the Net Wallet Balance card, budget card resized (ring = formula width, To
watch as tall as the left half), formula Budget − Used = Remaining − Bills due = Safe to spend, and Used/Spent shown as
budgeted + unbudgeted in the formula and the docs. As built: `docs/design/CORE_SCREENS_REFINEMENT.md` §6 "Owner review round".

| Check | Result |
|---|---|
| Unit tests | **829/829** (825 before + 4: `HomeBudgetCardTest` formula adds up / past month / overspent signed; `GuidedSetupTest` draft total) |
| Lint | 0 errors, 9 warnings (pre-existing) |
| Build | `assembleDebug` and `assembleDebugAndroidTest` pass |
| `ScreenshotTourTest` | **PASS** on API 28 (`Lucento_CoreScreens_API28`), with two new shots: 03b currency step, 04c guide budget step with typed amounts |
| Device checks (API 28) | Home card 411dp and 360dp (side by side), 360dp @ 1.3 (stacked); currency step and guide budget step 360dp @ 1.5; day and night. Fixed on the way: watch names cut at 360dp (percent moved beside the bar), operator column now grows with text, currency step had no room for a single row at 360dp @ 1.5 (now one scrolling list) |
| Not done | API 36 (CS-7), TalkBack traversal, ≥ 600dp, physical phone |

Next: owner review of `artifacts/core-screens/review2/` and `r2-*.png`; authorize a commit.

## Core screens refinement — implementation (done 2026-11-03, uncommitted on `feature/core-screens-refinement`)

Owner decisions 2026-11-03: **D1** Budget's per-day figure is "Safe per day" (`CashflowCalculator.safeToSpend.perDay`),
**D2** Bold section titles app-wide, **D3** Google "G" button in scope. What was built, the spec corrections and every
deviation: `docs/design/CORE_SCREENS_REFINEMENT.md` §6.

| Check | Result |
|---|---|
| Unit tests | **818/818** (784 before + 34 new: `HomeBudgetCardTest`, `BudgetDailyFigureTest`, `WalletsOverviewTest`, `HistoryLabelsTest`, `TrendHeightsTest`, a `CashflowCalculatorTest` pin) |
| Lint | 0 errors, 9 warnings (all pre-existing, none in touched files) |
| Build | `assembleDebug` and the androidTest compile pass |
| `ScreenshotTourTest` | **PASS** end to end on API 28 emulator `Lucento_CoreScreens_API28` (repaired: it had been failing since Part B); 30 shots in `artifacts/core-screens/tour-411dp/` |
| Device checks (API 28, 411dp) | Day and night on all four screens; card view; **view mode survives a force-stop** (Card view `checked=true` after relaunch); off-module tile → sheet → Turn on restores Scheduled; with Recurring off, safe to spend = remaining (no bills); trend line + "0% vs Dec" pill with a 3-month history (clock moved on the test AVD) |
| Responsive | 360dp @ 1.3 and 320dp @ 2.0 on all four screens; seven layout defects found and fixed (§6) |
| Not done | ≥ 600dp window, live TalkBack traversal, a physical phone, CI run (see KNOWN_ISSUES CS-5) |

Next: owner review of the screens in `artifacts/core-screens/`; authorize a commit (and a push so CI runs the repaired
tour). Original plan kept below for the record.

Designs: canvas https://claude.ai/artifact/MocjXVBtQG5uW38yUQm9kE. Spec with file-level guidance:
`docs/design/CORE_SCREENS_REFINEMENT.md`. No calculation changes; UI only. Suggested order, each step shippable alone:

1. Design-system additions: `Hierarchy.cardOverline`, Bold `sectionTitle`, `CardOverline`, `StatTile`, `TrendLine`,
   `ViewToggle`, `ProgressRing` centre content, `CompositionBar` segment gap. Update `ThemeContrastTest` if a token pair is added.
2. Transactions: three stat cards (Income / Expenses / Net inflow or Net outflow), 16sp Bold day headers, day groups as
   cards, red expense amounts. Screenshot light + night.
3. Dashboard: drop "Overview", left month pill, two-half Monthly Budget card (donut + top-3 watch list, safe to spend),
   dashed off-module tiles with a Turn on / Hide sheet, caps card headers. `HomeState.spendingBreakdown(top = 3)`.
4. Budget: hero (remaining, % spent, utilisation with today marker, pace chip, Spent / Safe daily spend sub-cards),
   compact month switcher, 18sp Bold section titles, refined allocation rows.
5. Wallets: six-month trend in the hero (`BalanceHistory.lastMonths(count = 6)` in `WalletsViewModel`), Assets /
   Liabilities stat cards, distribution bar, All / Assets / Liabilities + type filters, row / card view (persist
   `wallets_view_mode` in DataStore), compact wallet faces (2 per row, 3 at ≥ 600dp).
6. Global: official Google "G" mark on "Continue with Google" (`ConnectAccount.kt`), caps card headers on the other
   screens that use `CardHeader` only if they are touched anyway.
7. Validation: 320dp and 430dp widths, font scale 1.3 and 2.0, night theme, TalkBack labels on the new tiles and charts,
   `ScreenshotTourTest` update, unit tests for any new pure helper (trend points, filter predicates).

## Owner review round 6 (2026-11-04, uncommitted on `feature/core-screens-refinement`)

As built: `docs/design/CORE_SCREENS_REFINEMENT.md` "Owner review round 6"; `docs/CALCULATIONS.md` (Spent, Focus currency);
`docs/SYNC.md` (nothing can hang, while Lucento is open, what the user sees).

| Next | Why |
|---|---|
| **Owner: install the new APK, open Lucento, check the chip** — it should read "Synced · now" within seconds, or "N to sync" / "Needs attention" with the reason | The account's last upload was 2026-11-03 21:17 UTC and last download 2026-11-04 05:13 UTC; anything entered since is only on the phone until a sync completes |
| If the chip shows Needs attention, send the sheet's message | The stall's cause wasn't reproducible from logs (no failed requests reached the server) |
| Optional: hide amounts on Wallets / Transactions / Budget too | Round 6 hides the Dashboard only, as asked |
| Optional: Home's budget card and "See all" could carry the month shown on Home into Budget / Transactions | Today they open the current month (each page names its month, so nothing reads wrong) |

| Check | Result |
|---|---|
| Unit tests | **899/899** (+13) |
| `ReconciliationTest` (API 28 emulator) | PASS: Home Spent = Transactions Expenses = Budget Spent = ₱6,567.00 on the owner-shaped ledger; Hide masks the dashboard; Show on a phone without a screen lock asks first |
| `ScreenshotTourTest` (API 28) | PASS, 45 shots in `artifacts/core-screens/review7/` |
| Lint | 0 errors, 9 warnings (same types) |
| CI | `ReconciliationTest` added to the emulator job (blocking) |
| APK | `artifacts/apk/lucento-debug-review7.apk`; device screenshots in `artifacts/financial-integrity/` |

## Owner review round 7 (2026-11-04, uncommitted on `feature/core-screens-refinement`)

As built: `docs/design/CORE_SCREENS_REFINEMENT.md` "Owner review round 7".

| Next | Why |
|---|---|
| **Owner: test the production-type build** `artifacts/apk/lucento-release-TEST-SIGNED-round7.apk` (test-only signing, never distribute): sign in, compare Home Spent with Transactions (This month) and Budget, check the sync chip, try Hide / Show | Round 6 was verified on debug builds, which use the test Supabase project; release uses production |
| **Last stage — Account & sync password:** show New / Confirm password only for email accounts, never for Google (OAuth) sign-ins, and ask for the current password first | Owner, 2026-11-04: revisit at the end of development |
| Optional: a Hide / Show switch on other pages too | Today it lives on the Dashboard; it applies everywhere |

| Check | Result |
|---|---|
| Unit tests | **900/900** (+1: amounts in an answer are hidden, dates, counts and percentages are not); lint 0 errors, 9 warnings |
| `ReconciliationTest` (API 28) | PASS: figures agree; Hide masks Home, Transactions, Budget and Wallets |
| `ScreenshotTourTest` (API 28) | PASS, 54 shots in `artifacts/core-screens/review8/`, including the hidden pass (23–23i) over Home, Transactions, a transaction, Budget, a wallet, Insights, Goals, Debts and Scheduled |
| Release build | `assembleRelease` with `LUCENTO_REQUIRE_RELEASE_CONFIG=true`: package `com.lucento.app`, signer SHA-1 `49:AF:14:81:…:8C:75` (debug key), dex references the production project only; launches on the emulator without a crash |

## Owner review round 8 (2026-11-04, `feature/owner-round-8`, PR to `main`)

As built: `docs/design/CORE_SCREENS_REFINEMENT.md` "Owner review round 8".

| Next | Why |
|---|---|
| **Owner: review and merge the app PR** from `feature/owner-round-8` once CI is green | Committed and pushed, not merged |
| ~~Owner: merge `lucento-website` PR #9~~ **Done 2026-11-04** (`901acf8`, production deployment succeeded) | The adviser prompt and `[[= … ]]` calculations live in the proxy. Old apps are unaffected (version 1 → old prompt) |
| **Owner: ask the adviser a few questions** on the test-signed release APK (signed in, AI answers re-accepted) | First live check of the adviser prompt: next steps, `[[= … ]]` results in bold, the closing guidance line |
| **Owner: re-accept AI answers** after installing | The consent now covers transactions, goals and projections (v2); the summaries-only consent was reset on purpose |
| Owner: try a real statement photo and an e-wallet screenshot | Only made-up images were read on a device (`ScanOcrTest`); real bank layouts vary (merged columns, two-line rows) |
| Later: let Smarter reading (Gemini) read scans, with its own consent | Scans are read on the phone only; a model would handle messier layouts, but statements are sensitive |
| **Last stage — Account & sync password** (unchanged) | Owner, 2026-11-04 |

| Check | Result |
|---|---|
| Unit tests | **927/927** unit tests (new: `StatementScanTest` 16, `AdvisorTest` 7, `DebtProgressTest` +3; `AskRequestTest` +1: the body always says `"version":2`; `AskTest` and `AccountSetupResetTest` updated for consent v2) |
| Lint | 0 errors / 10 warnings (none from this round: the AutoboxingStateCreation one is round 7's `AmountText.kt`) |
| `ScanOcrTest` (API 28) | **3/3 PASS**: ML Kit reads the made-up statement, e-wallet and receipt images; the statement and history are read line by line (directions from the balance and the signs; "₱" comes back as "P", handled), the receipt as one 369.00 payment |
| `ReconciliationTest` (API 28) | PASS |
| `ScreenshotTourTest` (API 28) | PASS, 55 shots in `artifacts/core-screens/review9/`; 06c shows the bell's Visa payment as a transfer of $640.00 from Everyday bank, today |
| Release build | `artifacts/apk/lucento-release-TEST-SIGNED-round8.apk`: `com.lucento.app`, signer SHA-1 `49:AF:14:81:…:8C:75` (debug key; test only, never distribute) |
| Manual (API 28) | `artifacts/round8/`: statement scan reply and rows, receipt scan, bell, filled-in payment |
| Website | `eslint` clean on `ask.ts`; `astro check` errors only in the Playwright files (unrelated, pre-existing); local `astro build` fails on a missing `@fontsource-variable/manrope` in local `node_modules` (environment; Vercel installs fresh); the Vercel preview build of PR #9 **passed**; merged as `901acf8`, production deployment **succeeded**, unsigned `POST /api/ask` → 401 |

## Owner review round 9 (2026-11-04, `feature/owner-round-9`, PR to `main`)

As built: `docs/design/CORE_SCREENS_REFINEMENT.md` "Owner review round 9".

| Next | Why |
|---|---|
| **Owner: merge `lucento-website` PR #10** (`feature/ask-real-adviser`; merging deploys it to Vercel production) | The adviser tone and the busy-model fallback live in the proxy. Version 2 apps (round 8 and 9) get the new tone once it's deployed; only round 9 shows the "AI advice can be inaccurate" note under answers |
| Owner: test the round 9 test-signed APK with Smarter reading **off** | Confirms AI answers no longer depend on it; if one still fails, note the exact message under the answer and the time (Vercel keeps logs for one hour) |
| Owner: decide whether investing advice should go further | The prompt now advises on investing in general terms but doesn't name stocks, coins or companies' products, or promise returns |
| **Last stage — Account & sync password** (unchanged) | Owner, 2026-11-04 |

| Check | Result |
|---|---|
| Unit tests | **935/935** (new `ProxyCallTest` 8: refresh-and-retry after 401, no retry loop, no request when signed out, Ask answer after refresh, refresh margin, failure wording) |
| Lint | 0 errors, 9 warnings (the same set as round 8) |
| Builds | `assembleDebug`, `assembleDebugAndroidTest`; test-signed `assembleRelease` → `artifacts/apk/lucento-release-TEST-SIGNED-round9.apk` (`com.lucento.app`, signer SHA-1 `49af…8c75`), installed on API 28 and launched to the welcome screen (`artifacts/round9/01-release-launch.png`) |
| Website | eslint clean (`ask.ts`, `gemini.ts`); `tsc --noEmit` clean on `gemini.ts`; `preferAvailable` checked with a scratch Node script (5/5); Vercel preview build of PR #10 **passed** |
| Device | Not run for the new note and retry pill: they appear only with live AI answers (release build, signed in) |

## Owner review round 10 (2026-11-04, `feature/owner-round-10`, not committed)

As built: `docs/design/CORE_SCREENS_REFINEMENT.md` "Owner review round 10".

| Next | Why |
|---|---|
| ~~Owner: approve committing, pushing, PRs and merging~~ **Approved 2026-11-04** ("proceed all the way to merge") | |
| ~~Owner: apply `20261105090000_receivable_wallets.sql` to production~~ **Done by the owner 2026-11-04**; still to apply on `lucento-test` before a debug build syncs a receivable there | Until a project has it, a receivable's wallet upload is refused there (it stays pending on the phone; everything else syncs) |
| **Owner: merge `lucento-website` PR #10** (deploys rounds 9 and 10 of the adviser) | Round 10 apps send version 3; until `SYSTEM_V3` is deployed, production answers them with the round 8 adviser prompt (version ≥ 2), so no assumed-rate projections and none of the new calculations |
| Owner: ask the MP2 question again on the round 10 APK | First live check of projections with assumed rates (low / high case, the rate named) |
| Owner decision: live market rates | Rates come from the model's own knowledge and can be out of date; Google Search grounding would fetch them but costs per search beyond a free allowance and sends the question to Search |
| Later: Record payment could pre-fill the monthly instalment | Today it opens the transfer with the accounts chosen and the amount empty (as a loan's Repay does) |
| **Last stage — Account & sync password** (unchanged) | Owner, 2026-11-04 |

| Check | Result |
|---|---|
| Unit tests | **945/945** (new `ReceivableTest` 7: terms kept, collection = interest income + transfer, no interest = transfer only, refused links, outlook/sign, progress, receiving account; `AdvisorTest` +3: amounts with a code, %, ^, MP2 future value, refusals, rates not flagged; `BackupTest` +1: receivable + collection round trip and refusals; `PulledRowRulesTest`: a RECEIVABLE row passes) |
| Lint | 0 errors, 9 warnings (unchanged set) |
| Builds | `assembleDebug`, `assembleDebugAndroidTest`; test-signed `assembleRelease` → `artifacts/apk/lucento-release-TEST-SIGNED-round10.apk` |
| `ScreenshotTourTest` (API 28) | **PASS** twice, 55 shots (`artifacts/round10/tour/`): Wallets without type chips, legend without the formula. A first attempt failed on stale app data from round 8 (two test classes in one run share an install), a second on the emulator's own "System UI isn't responding" dialog (06c under it was correct); `hide_error_dialogs` set on the AVD |
| `ReconciliationTest` (API 28) | PASS |
| Manual (API 28) | `artifacts/round10/`: 02 Scheduled card payment → transfer; 04a–d Home dock to top, Back keeps place, reselect to top; 05 receivable editor (preview, repayment details, suggestion "$500.00 a month pays back $6,000.00 in 12 months"); 06 receivable page and Record payment; 07 collection editor, actions sheet, interest split, list row "+$500.00 · Ana → Everyday bank", page after: Remaining $5,550, Received $450, 1 of 12. The four-kind switch first clipped "Debt payment"; now short labels (Bill · Income · Debt · Collect) |
| Website | eslint clean on `ask.ts`, `gemini.ts`; `SYSTEM_V3` for version ≥ 3, `SYSTEM_V2` unchanged for version 2 |
| Schedule editor (owner's follow-up) | On API 28 (`artifacts/round10/09*`): Bill and Collect layouts, the category menu, 130% and 150% text (names keep their width), ~310dp width (title wraps onto two lines) |
