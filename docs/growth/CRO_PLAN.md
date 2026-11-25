# Conversion plan (CRO)

Started 2026-11-25. Claude runs it; the owner approves each change and does the Play Console steps.

**Goal:** more of the people who see Lucento install it, set it up, keep using it and choose Pro. Each step is
measured, changes are made one at a time, and none of it breaks the privacy promise or the truth rules.

## Owner decisions (2026-11-25)

1. **First focus: Google Play listing → install.** The other steps follow in this order: setup → first entry,
   Free → Pro, then the website.
2. **Four more anonymous usage counts for the Pro path.** They stay opt-in, are kept 90 days and never include amounts or
   typed text.
3. **Website: tagged Play links only.** The site gets no analytics.

This plan also keeps the owner's standing calls:
- no Pro card on Home;
- the plan-fact Pro dialog;
- no walkthrough, checklist or tab hints unless the counts show that a feature goes unfound;
- a ₱0 budget;
- TRUTH_RULES for every listing text and image;
- Testers Community testers are never asked to rate (T25).

## 1. The funnel

| # | Rate | Worked out as | Read in |
|---|---|---|---|
| R1 | Listing → install | Store listing acquisitions ÷ store listing visitors | Play Console's store performance reports (by country, traffic source, search term and UTM). Play's peer median is the first benchmark |
| R2 | Website → install | Installs whose `utm_medium` starts with `website` | Play Console, UTM view (needs CRO-3) |
| R3 | Setup finished | Install ids with `first_entry_saved` ÷ install ids with `onboarding_started` | `usage_events` (opt-in only) |
| R4 | Setup drop-off | `onboarding_step_viewed` counted by `step` | `usage_events` |
| R5 | Came back | `d1_return` and `d7_return` ÷ `onboarding_started`. Play's retained installers serve as a cross-check | `usage_events`, Play Console |
| R6 | Reached the plan page | `plan_page_viewed` by `from` ÷ active install ids. Wall → plan page, per feature = `plan_page_viewed{from=wall, feature=X}` ÷ `pro_dialog_shown{feature=X}` | `usage_events` (needs CRO-1 and CRO-2) |
| R7 | Bought | `purchase_completed` ÷ `purchase_started`. Also trial starts, trial → paid, cancellations, and Lifetime vs yearly | `usage_events`, Play's subscription reports, and the server's purchase rows (read-only) |
| R8 | Shares that install | Installs tagged `utm_source=app_share` | Play Console, UTM view (HF-5) |

**Opt-in share.** This is the number of install ids that sent `onboarding_started`, divided by Play's new installs for
the same days. Every R3–R7 figure is read with the opt-in share beside it. The people who opt in may be keener than
average, so a small share means a noisy, self-selected sample.

**Traffic.** Play's listing tests need volume. Suppose the install rate moves from 30 % to 36 % (a 20 % lift). To see
that at Play's 90 % confidence, each variant needs roughly 750 listing visitors. At 100 visitors a day, a two-variant
test takes about 15 days; at 30 a day, about 50 days. So the first tests are big, visible changes (screenshots, icon),
not small wording changes.

## 2. Phase 0: before launch (now → P-day, expected 1–8 Dec)

| # | Work | Who | Must be done by |
|---|---|---|---|
| CRO-1 | Database: allow the four new count names | Claude writes it; the owner applies it on lucento-test, then production; Claude checks read-only | Live on production before any build sends the names |
| CRO-2 | App: the four counts, plus a fix so a rejected count is dropped | Claude (android PR) | The launch build, with HF-1 |
| CRO-3 | Website: tagged Play links | Claude (website PR) | Goes live with `site.playListed` |
| CRO-4 | Listing review, as a ranked fix list | Claude → owner choices | Before P-day |
| CRO-5 | Review of setup → first entry and of the plan page, as a ranked fix list | Claude → owner choices | Before P-day; fixes may land after launch |
| CRO-6 | Funnel log and queries | Claude | Before the first week's read |
| CRO-7 | First listing experiment ready to upload | Claude prepares it, the owner uploads it | P-day + 7 |

### CRO-1: database

- Write a new migration in `supabase/migrations/`; never edit `20261028180000_usage_events.sql`. It replaces the
  `usage_events_event_allowlist` check with the eight current names plus `pro_dialog_shown`, `plan_page_viewed`,
  `purchase_started` and `purchase_completed`.
- The MCP declines `apply_migration` in these sessions, so this becomes an owner paste task with a read-only check
  afterwards.

### CRO-2: app

**Fix this first.** Today `UsageCountsService.upload` stops at the first count that fails and keeps it at the front of
the queue. A count the server rejects (check violation, 23514 or 22023) would therefore block every later count from
that phone. The fix:
- drop a rejected count;
- keep retrying only after a network error;
- add a JVM test for each case.

**The counts.** Each prop is a fixed short value, never free text.

| Count | Prop | Sent from |
|---|---|---|
| `pro_dialog_shown` | `feature`: the `ProFeature` the dialog names | `ProWallDialog` (`feature/pro/ProWall.kt`), the only Pro dialog. Repeat taps already show the snackbar instead, so it sends at most once per feature per session |
| `plan_page_viewed` | `from`: `settings`, `profile`, `plan_ended`, `onboarding`, `wall` (the Pro dialog, the short message's Unlock or a lock note) or `other`; plus `feature` when a lock led there | The plan page, once per visit. Its route carries the door (`ProRoute.via`) next to the feature it already named. The dialog and the message aren't told apart: that would have meant changing every lock's call site before launch, and `pro_dialog_shown` already counts the dialogs |
| `purchase_started` | `plan`: `monthly`, `annual`, `annual_trial` or `lifetime` | `ProViewModel.buy`, before Play's sheet opens |
| `purchase_completed` | Same `plan` values | Only when Play confirms a new purchase in this session. Never on Restore or a refresh |

`feature`, `from` and `plan` are added to `UsageCounts.PROP_KEYS`.

**Privacy.**
- **Privacy Policy:** add "or that the Lucento Plan page was opened or a plan bought" to the usage-counts line in
  `LegalDocuments.kt`. Then bump `LegalDocuments.VERSION` and regenerate the website's `/privacy` with
  `tools/legal/export_legal.py`.
- **Data safety:** "Purchase history" is declared for App functionality, Account management and Fraud prevention. Add
  Analytics as a purpose. This is an owner step in Play Console, and it gets recorded in `PLAY_STORE_SUBMISSION.md`.

**Tests:**
- the allowlist and prop filtering;
- each of the four call sites, through ViewModel tests;
- the drop-on-reject rule.

### CRO-3: website

- Every Play link on lucentoapp.com becomes
  `https://play.google.com/store/apps/details?id=com.lucento.app&referrer=<encoded tags>`, with these tags:
  - `utm_source` = the visitor's incoming `utm_source`, else `website`;
  - `utm_medium` = `website_<place>`, where the place is `hero`, `pricing`, `footer` or `faq`;
  - `utm_campaign` = the incoming campaign, else `site`.
- For example, "TikTok → site → hero button" reads `tiktok / website_hero` in Play.
- The incoming tags are read from the landing URL. They're kept in that tab's `sessionStorage` only, to build the
  link, and nothing is sent to Lucento.
- The change only applies once `site.playListed` is true.
- After launch, check it once with a test click (as in HF-5).

### CRO-4: listing review (both listings)

The evidence is the live listing captured at phone width, plus Play search results for the top terms in
`docs/play/ASO_KEYWORDS.md`. The review checks:
- **First three screenshots:** read on their own, do they say what Lucento is and why it's different (type it like a
  text, offline, Safe to spend)? Many visitors never swipe past them.
- **Icon:** at search-result size, beside the top five apps for "budget tracker" and "gastos tracker".
- **Title:** the Philippine listing's title is still an open brand call (`ASO_KEYWORDS.md`: "Lucento: Budget & Ipon
  Tracker"). The global listing keeps "Lucento: Budget Tracker".
- **Promo video:** Play's experiments can't test a video. If a cut of the launch film is added, it's a one-time change,
  recorded with its date.

The output is a ranked fix list, asked as multiple choice.

### CRO-5: setup and the plan page

The review runs on the emulator at 360 dp and 411 dp, and at 130 % text. It checks:
- **Setup:** the taps and seconds from first open to the first saved entry, on the fresh-install path.
- **Plan page:**
  - is the 7-day trial visible beside the annual price?
  - which plan is selected first?
  - how does Lifetime read beside yearly (it's now about 2 × yearly)?
  - the Free plan's facts;
  - are prices shown in the buyer's own currency, as Play gives them?

The output is a ranked fix list, asked as multiple choice. It won't include walkthroughs.

### CRO-6: funnel log

- **Where:** lucento-studio `docs/growth/FUNNEL_LOG.md`. That repo is private; this one is public, and installs and
  purchases stay out of it.
- **What it holds:**
  - one row a week: listing visitors, R1–R8 and the opt-in share;
  - every change with its date (a listing edit, an app version, a price), so the numbers are read against what
    changed.
- **Queries:** `docs/growth/funnel.sql` holds read-only queries on `usage_events` for R3–R7. They count distinct
  install ids, never single installs, and run read-only on production.

### CRO-7: first experiment (E1)

- **What it tests:** the screenshots on the listing most visitors see. Marketing is Philippine-first, so this is
  probably the Philippine custom listing; the baseline week's country split decides.
- **Variant B:** built with the capture pipeline and the Play graphics generator (lucento-studio `lab/play-graphics`).
  Its first three screenshots carry type it like a text, Safe to spend and offline, with short captions.
- **One variant only** against the current listing, because traffic is low.
- **Upload:** the owner uploads it in Play Console, from a click-by-click Mission Control task created when it's due.

## 3. Phase 1: launch weeks 1–4

- **Launch day:**
  - HF-1 and `site.playListed` flip together;
  - one tagged website click and one `app_share` link show up in Play (HF-5);
  - the first counts reach `usage_events`.
- **Week 1 is the baseline.** No listing changes. Record visitors, R1 against the peer median, countries, top search
  terms and the opt-in share.
- **Week 2: start E1.**
  - It runs 50/50 against the current listing.
  - The target metric is retained first-time installers. That counts installs still kept a day later, so it doesn't
    reward installs that are removed at once.
  - It runs at least 14 days (two weekends) and stops at 6 weeks.
  - Apply the variant only if Play reports a winner at 90 %. Otherwise keep the current listing and move on; a tie is a
    result too.
- **Next tests:**
  - E2 tests the short description. It's a localized experiment, and the alternatives are already in
    `ASO_KEYWORDS.md`.
  - E3 tests the icon.
- **Search terms:** a search term that Play shows and we lose on feeds the next description edit.

## 4. Phase 2: month 2 on (setup, then Pro)

- **Setup:** if more than 1 in 5 people who see a step never reach the next one, that step gets a fix (the owner's
  choice). Only then would a hint be considered, under the owner's rule.
- **Pro:** once the counts hold a few hundred plan page views, read R6 and R7. The candidates are each an owner call:
  - where the dialog's "See Lucento Plan" lands;
  - the plan page's order;
  - a trial on monthly;
  - Play win-back offers for lapsed subscribers;
  - Lifetime vs yearly (RP-4).
- **In-app changes go in one at a time.** Each is compared over equal periods before and after. There are no in-app A/B
  tests, because there are too few purchases to split.

## 5. Rules

- One change at a time per step, entered in the funnel log with its date.
- Every listing text and image passes TRUTH_RULES, and screenshots are real screens.
- No dark patterns:
  - no countdowns or fake scarcity;
  - no pre-ticked trials;
  - no confirm-shaming;
  - no hidden cancel.
- Counts stay opt-in, anonymous and 90 days. They carry no free text, amounts, account ids or advertising ids, and the
  website stays free of tracking.
- Installs and purchases stay out of this public repo.

## 6. Timeline

| When | What |
|---|---|
| 25–29 Nov | CRO-1 migration file; CRO-2 counts and the drop-on-reject fix; CRO-3 website PR (held until `playListed`); CRO-4 and CRO-5 reviews → owner choices |
| By 30 Nov | Owner applies CRO-1 on lucento-test, then production, alongside OT-66 and OT-43 |
| P-day (1–8 Dec) | Launch build (HF-1 + CRO-2); website `playListed` + CRO-3; Data safety update |
| P-day + 7 | Read the baseline week; pick E1's listing |
| P-day + 8 → + 22 or later | E1 runs |
| Then | E2, E3; from month 2, setup and Pro |

## 7. Open owner calls

- **Philippine listing title:** keep "Lucento: Budget Tracker" or use "Lucento: Budget & Ipon Tracker"
  (`ASO_KEYWORDS.md`).
- **E1's listing:** Philippine custom or main. Decided after the baseline week.
- **Data safety:** add Analytics as a purpose of Purchase history (CRO-2).
