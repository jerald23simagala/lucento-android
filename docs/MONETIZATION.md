# Lucento Monetization — Free, Pro Subscription and Pro Lifetime (revised specification)

Status: **proposal v2 — decisions D-1 … D-18 approved by the owner on 2026-11-06, all as recommended. Nothing is implemented yet;** implementation begins with Phase 1 (§11) on `feature/lucento-pro-architecture` after an explicit go-ahead. Written 2026-11-06 against
`origin/main` at `15e7a46` (PR #21 multicurrency on top of PR #20 Lucento Shared). The local checkout was two merges
behind while this was written, so every code fact below was read from `origin/main`; it has since been fast-forwarded
(§1.1).

Supersedes [monetization/PROPOSAL_V1_2026-11-06.md](monetization/PROPOSAL_V1_2026-11-06.md) (the two-plan proposal;
its Phase 1 feature inventory with code locations still applies and is not repeated here). Customer-facing text —
the comparison matrix, the Lifetime promise, pricing-page copy, in-app lock copy and the FAQ — is in
[monetization/CUSTOMER_COPY.md](monetization/CUSTOMER_COPY.md).

Philosophy this is built on: **Your money. Your data. Your lifetime access.** A Lifetime owner must be able to use
every local Pro feature with the phone in airplane mode, with Supabase down, and with Google Play unreachable, for as
long as the phone runs the app.

---

## 0. Executive summary

**Three plans.**

| Plan | What it is | Price (working assumption, set in Play Console, never in code) |
|---|---|---|
| **Free** | A complete offline budgeting app with a small number of count limits | ₱0 |
| **Pro Subscription** | Everything Lucento does, including the online services (cloud sync, cloud restore, AI adviser, Lucento Shared), while subscribed | ₱149 / month · ₱1,190 / year (7-day trial on annual) |
| **Pro Lifetime** | Permanent ownership of every **local** Pro feature, plus 12 months of the online services; after that the online services are an optional add-on | **₱3,490 once** (recommended; see §4.3) + optional **Lucento Cloud** add-on ₱59 / month or ₱490 / year |

**The one idea that makes Lifetime honest**: entitlements are split into four groups, and Lifetime buys group A
outright.

| Group | Contents | Free | Pro Subscription | Pro Lifetime |
|---|---|---|---|---|
| **A. Local Pro features** (run on the phone, cost Lucento nothing per use) | unlimited money wallets, unlimited debt accounts, receivables, manually valued assets, 20 active goals, 12-month Insights and net-worth composition, wallets in other currencies, statements as PDF, all themes, every future local Pro feature | limits | ✔ while subscribed | **✔ forever, offline** |
| **B. Cloud services** (Supabase) | cloud sync, cloud restore on a new phone | — | ✔ | 12 months included, then Lucento Cloud add-on |
| **C. AI services** (Gemini via the Vercel proxy) | AI adviser allowance, Smarter reading | 10 questions / month | 50 / day | 12 months at Pro allowance, then Free allowance or Lucento Cloud add-on |
| **D. Lucento Shared** (needs B) | invite one Partner | — | ✔ | while B is active |

**What changed from v1 and why** (each is a decision in §7):

- Added **Pro Lifetime** as a first-class plan (owner direction) with the A/B/C/D split so it never promises
  unlimited servers for a one-time price.
- **Wallets become a Free limit** (owner direction). Recommended **3 money wallets**, not 2 (§3.2) — and debts,
  loans, receivables and manually valued assets are wallets in the code, so the count must exclude them or Free
  cannot track debts at all (§1.3, finding 2).
- **Savings goals** Free **2 active** (owner's number; v1 said 3), Pro and Lifetime **20** (owner's number).
- **Insights** split: Free sees the last 3 months; 6- and 12-month trends and net-worth composition are Pro (§3.4).
  v1 had all of Insights free. This is the only place v1's "never gate a focus feature" rule bends, and it bends on
  depth, not access.
- **Manually valued assets and receivables** move to Pro (the "detailed net worth, assets and liabilities" and
  "advanced debt tracking" rows in the owner's brief map to real, shipped features — §3.3).
- Lifetime **replaces** v1's "defer lifetime" with a priced recommendation and a service model (§4.3).

**Nothing else moves**: transactions, history, categories, budgets, scheduled payments, basic debts, on-phone Ask
Lucento, scanning, CSV, JSON backup and restore, app lock stay free and unlimited, for the reasons in v1 §2.

---

## 1. Repository findings for this revision

### 1.1 Branch and working tree

**Reconciled 2026-11-06 (owner-approved):** local `main` fast-forwarded from `398bd8b` to `origin/main` `15e7a46`
(PR #20 Lucento Shared, PR #21 multicurrency). Steps taken, nothing discarded: the untracked discovery-era
`docs/shared/` (6 files) and `docs/ui-ux-audit/` (121 files) were copied to `../lucento-local-backup-2026-11-06/`
and verified with `diff -rq`; the discovery `docs/shared/` was removed so the as-built docs could land; the two
tracked edits were stashed, the pull ran with `--ff-only`, the stash was re-applied and dropped.
`gradle.properties` (+`org.gradle.tooling.parallel=true`) is back as an uncommitted change; the local
`CURRENT_STATUS.md` row (the Shared discovery note) was superseded by upstream's as-built row and dropped. The
unrelated `stash@{0}` from 2026-10-30 is untouched. Untracked and kept: `docs/MONETIZATION.md`, `docs/monetization/`,
`docs/ui-ux-audit/`, `.claude/launch.json`, `.gitignore.before-stash-restore`, screenshots under `artifacts/`.

Next: `git checkout -b feature/lucento-pro-architecture` for Phase 1; this document and `docs/monetization/` become
its first commit (never on `main`).

### 1.2 What exists today (status vocabulary used throughout)

- **Shipped** — on `origin/main`, tested.
- **Partial** — exists but needs a change to be sold as described.
- **Planned** — in `docs/ROADMAP.md` or the owner's brief, not built. **Must not appear on a pricing page until built.**
- **Architecture** — requires the entitlement architecture of §5 (every gate does; listed only where more is needed).

Billing and entitlement code: **none** (no `com.android.billingclient`, no plan/entitlement types, no Play products;
confirmed again on `origin/main`). Server hooks that exist: `lucento_private.max_partners(owner)` returns `1`
(`20261105120000_lucento_shared.sql:32-39`); `public.consume_ai_quota()` counts per user per UTC day and the proxy
compares with `AI_DAILY_LIMIT` (default 50) for everyone (`lucento-website/src/pages/api/ask.ts:250-262`, returns
`remaining`); `domain/Features.kt` holds the user's module toggles (preferences, not entitlements).

### 1.3 Discrepancies between the brief, the code and the servers

1. **Role names.** The brief says wallets "may be labeled Shared, Manager Personal, or Companion Personal". The
   approved, implemented product uses **Owner / Partner** and the labels **Shared · Personal (Owner's) · Personal
   (Partner's)**; `docs/shared/PRODUCT_SPEC.md` §2 on `origin/main` says *"Never used anywhere: Manager, Companion"*
   and reserves "Lucento Companion" for a possible web product. This document uses Owner / Partner. If the owner now
   prefers Manager / Companion, that is a rename across six docs, `SharedSpace.kt` display strings and the server
   `role` values — a separate decision, not part of monetization (D-15).
2. **Debts are wallets.** `WalletType` has `CREDIT_CARD`, `LOAN`, `RECEIVABLE` and `OTHER_ASSET` alongside the
   four money types (`domain/Wallet.kt`). Net worth is computed from wallets (`NetWorthCalculator`). A Free cap of
   "2 wallets" counted naively would mean a person with cash and GCash could not add a credit card to track a debt,
   contradicting the brief's own Free row "Track basic debts and loans". The limit must count **money wallets**
   (CASH, BANK, E_WALLET, SAVINGS) separately from **debt accounts** (CREDIT_CARD, LOAN), with RECEIVABLE and
   OTHER_ASSET as their own Pro features (§3.2–3.3).
3. **Several "advanced" rows in the brief have no code behind them.** Advanced budgeting (forecasting, rollover,
   custom periods), advanced dashboard, advanced historical views, advanced recurring management, advanced category
   management: all **Planned**. The pricing page can only say what is true today; §3 marks each and
   `CUSTOMER_COPY.md` leaves them out. Where the brief says "advanced" and the code has a real depth split, the
   split is used (Insights periods, manually valued assets, receivables, PDF statements, other currencies, themes).
4. **Goals can only be archived once reached** (`GoalRepository.archive` → `ArchiveResult.NotReached`). So "archive
   a goal to free a slot" is only true for reached goals; otherwise the person deletes one or upgrades. Copy adjusted.
5. **Wallets are never archived in the UI today** (`WalletEntity.archived` exists; `WalletRepository` has no archive
   call; delete is refused while transactions or schedules reference the wallet). So a Free person over the wallet
   limit after a downgrade cannot "archive to get under the limit" — they simply keep the wallets. Fine under the
   data rules (§6.3), but it means the limit only ever bites on **creation**.
6. **Server environments.** The Lucento Shared and multicurrency migrations are applied on `lucento-test`
   (debug builds) and, per the handover notes, approved for production but **unconfirmed** there (NEXT_TASKS OT-9,
   OT-12). The entitlement migrations must go to `lucento-test` first and to production only together with a release
   build that reads them.
7. **AI limit is global**, not per plan, and lives in a Vercel env var. A plan-aware limit needs the proxy to read
   the caller's plan (§5.5).
8. **No release signing** (KNOWN_ISSUES CS-20) and `targetSdk 35`. Play Billing cannot be tested on a device until a
   signed build is on an internal testing track; one source claims Play now requires `targetSdk 36` for new
   submissions — verify in Play Console (v1 §7.5).
9. **Website** says only "A date and pricing haven't been announced" (`src/data/home.ts:181`) and describes sync as
   "if you want it" and the adviser without limits (`features.ts`). Both must change in the same release as the
   gates (§8).
10. **Preferences survive account changes, data does not.** `LocalLedgerReset` wipes Room, photos, the sync binding
    and the Shared cache but leaves "theme and other device preferences". The entitlement cache must live beside the
    theme (device-level DataStore), not beside the sync binding (§5.3). Android cloud backup and device transfer are
    fully excluded (`res/xml/data_extraction_rules.xml`), so a reinstall or new phone **always** starts with an empty
    entitlement cache and must recover through Play or the Lucento account (§5.6).

### 1.4 Can Lifetime be enforced without an online dependency? — Yes, with one honest caveat

Checked against the eight requirements in the brief:

| Requirement | Today | With the §5 design |
|---|---|---|
| 1. Opens without internet | Yes — Room is the source of truth; `AppViewModel` makes no network call on start; Shared refresh and launch sync are best-effort and only when enabled | Unchanged. Entitlement is read from DataStore first, then from Play's local cache (`queryPurchasesAsync` works offline for purchases already on the device) |
| 2. No validation on every open | n/a | Lifetime snapshot is honoured without re-verification. Re-verification is **opportunistic** (when Play or the server happens to be reachable) and only ever *adds* information; failure to verify never removes Lifetime (§5.3 rule L-2) |
| 3. Local records accessible | Yes | Entitlement never filters data reads; it only gates *creation* and *entry points* (§6.3) |
| 4. Budget calculations offline | Yes (`domain/`, pure) | Unchanged; no calculator reads entitlement |
| 5. Survives Supabase being down | Sync fails gracefully today (chip shows error) | Server entitlement is a *secondary* source; Play + cache are primary for group A. Group B/C stop working when the servers are down, which is true for everyone and is stated in the promise |
| 6. Play verification not a permanent runtime dependency | n/a | For Lifetime: Play is consulted when available; a cached, device-bound Lifetime snapshot stands on its own indefinitely. Play Store absent (sideload, custom ROM) → the Lucento-account path (§5.6) also restores it |
| 7. Reinstall / device migration documented | JSON backup; cloud restore | Two recovery paths, documented in the promise and FAQ: (a) same Google account → Play restores automatically; (b) Lucento account that the purchase was linked to → server mirror restores it. Data recovery is separate: JSON backup (free) or cloud restore (B) |
| 8. Never deletes or hides data on entitlement change | Nothing exists to do so | Rules §6.3; tests in §10 |

**Caveat (stated in the promise):** a Lifetime snapshot that has *never* been verified cannot be created offline; the
purchase itself needs Google Play once. After that, the phone needs no connection. The remaining theoretical hole —
copying a verified DataStore file to another phone, or editing it on a rooted phone — is mitigated with a device-bound
MAC (Android Keystore key) over the snapshot and accepted beyond that: Lucento is not DRM, and the servers (B/C/D)
are authoritative for everything that costs money.

---

## 2. Principles (unchanged from v1 unless marked)

1. Free is a complete, offline budgeting app. Limits are counts on *creation*, never on access.
2. Pro is paid for by server costs (B, C, D) and by depth (A). Lifetime buys A outright; B/C/D need a running
   service and are sold as a service. **(new)**
3. Never paywall safety, privacy or portability: JSON backup/restore, CSV export, app lock, hide amounts, history.
4. Limits restrict creation, never existence (§6.3).
5. Enforce on the server what the server pays for; enforce in repositories what the phone owns; composables only
   *display* entitlement state.
6. No prices in code. Play `ProductDetails` is the only source of a price string in the app.
7. Every customer-facing sentence names a concrete thing the person can do (no "advanced", "premium", "more").
8. A customer-facing list contains only shipped features. **(new)**

---

## 3. Feature-by-feature entitlement (the internal matrix)

Legend: **F** Free · **S** Pro Subscription · **L** Pro Lifetime · **L+C** Lifetime with Lucento Cloud active.
Status per §1.2. "Gate" names the one place enforcement would live.

### 3.1 Transactions, history, categories, budgets — unlimited for everyone

| Feature | Status | F | S | L | Offline | Gate |
|---|---|---|---|---|---|---|
| Add / edit / delete transactions; composer (type, speak, scan), several rows per message | Shipped | Unlimited | Unlimited | Unlimited | Yes | none |
| History, search, filters, detail, full editor | Shipped | All history | All history | All history | Yes | none |
| Transfers between wallets | Shipped | ✔ | ✔ | ✔ | Yes | none |
| Categories: presets, custom, rename, reorder, archive / restore | Shipped | Unlimited | Unlimited | Unlimited | Yes | none (D-7 offers a 10-custom cap; **not recommended**) |
| Monthly category budgets, total = sum, pace, Safe to spend, budget history, "onward" lifecycle | Shipped | ✔ | ✔ | ✔ | Yes | none |
| Home dashboard (balance card, pace, bills, goals, To watch) | Shipped | ✔ | ✔ | ✔ | Yes | none |
| Budget forecasting, rollover, custom periods | **Planned** | — | ✔ when built | ✔ when built | Yes | — |
| Reminder notifications | **Planned** | — | ✔ when built (or basic free) | ✔ when built | Yes | — |

Why no "advanced historical views" tier: nothing exists to sell; history is one list. Keep history unlimited.

### 3.2 Wallets (the owner's new Free lever)

| Feature | Status | F | S | L | Offline | Gate |
|---|---|---|---|---|---|---|
| **Money wallets** (cash, bank, e-wallet, savings; 409 institutions) | Shipped | **3 active** (recommended; owner proposed 2 — D-1) | Unlimited | Unlimited | Yes | `WalletRepository.create` when `type in {CASH, BANK, E_WALLET, SAVINGS}` → `SaveResult.LimitReached(used, limit)`; `update` that changes type *into* a money type counts too |
| **Debt accounts** (credit cards, loans) with terms, cycles, amount due, payment reminders | Shipped | **2 active** (D-2) | Unlimited | Unlimited | Yes | same call, `type in {CREDIT_CARD, LOAN}` |
| **Receivables** (money owed to you, instalments, interest) | Shipped | — | ✔ | ✔ | Yes | same call, `type == RECEIVABLE` → `ProRequired` |
| **Manually valued assets** (home, car, investments) | Shipped | — | ✔ | ✔ | Yes | same call, `type == OTHER_ASSET` → `ProRequired` |
| Adjust balance, institution logos and card faces, reorder | Shipped | ✔ | ✔ | ✔ | Yes | none |
| **Wallets in another currency** (FX valuation, realized gains, cross-currency transfers) | Shipped (v13) | — (main currency only) | ✔ | ✔ | Yes (rates optional) | `WalletRepository.create` with `currency != main`, `WalletChangePolicy` currency change → `ProRequired` |
| Month-end FX revaluation | **Planned** (stage 2) | — | ✔ when built | ✔ when built | Yes | — |

**Why 3 money wallets, not 2.** The guided setup requires at least one asset wallet; a Philippine user's day-one set
is cash + one e-wallet + one bank. At 2, the third of those triggers the upgrade sheet during setup — the worst moment
to sell, and it reads as the app failing the "track everyday spending" promise that every user picked as a focus. At
3, the lever stays real (a second bank, a second e-wallet, a savings account) and the conversion moment comes after the
person has data and a reason. Commercially, the difference between 2 and 3 is who you annoy, not how many you convert:
the people who need a fourth wallet are the same at either number. If the owner keeps 2, nothing else in this document
changes except the number in `PlanCatalog` and the copy.

**Why debts are a separate count.** Counting cards and loans against the money-wallet limit would make Free unable to
do what onboarding advertises ("Track debts and loans"). 2 debt accounts covers one card and one loan; more is a
Pro-sized life.

### 3.3 Net worth, debts, goals, recurring

| Feature | Status | F | S | L | Offline | Gate |
|---|---|---|---|---|---|---|
| Net worth = assets − liabilities, Home and Wallets heroes | Shipped | ✔ (from the wallets the person has) | ✔ | ✔ | Yes | none |
| Net worth including manually valued assets | Shipped | — (via OTHER_ASSET above) | ✔ | ✔ | Yes | as §3.2 |
| Debt progress, amortization, instalments, card cycles, "pays it" transfer from the bell | Shipped | ✔ on the 2 debt accounts | ✔ | ✔ | Yes | none |
| **Savings goals** (target, date, contributions, projection, scenes, photos, milestones) | Shipped | **2 active** (D-3) | **20 active** | **20 active** | Yes | `GoalRepository.save` (new goal) and `restore` (un-archive) → `LimitReached`; archived goals never count |
| Scheduled / recurring (bills, income, debt payments, collections; mark paid / skip / pause; outlook; bell) | Shipped | Unlimited | Unlimited | Unlimited | Yes | none (D-8 offers a 10-active cap; **not recommended**: "never miss a bill" is a focus and the cost of a schedule is zero) |

### 3.4 Insights and reports

| Feature | Status | F | S | L | Offline | Gate |
|---|---|---|---|---|---|---|
| Insights: income vs expenses, net flow, monthly comparison | Shipped | **Last 3 months** | 3 / 6 / 12 months | 3 / 6 / 12 months | Yes | `InsightsViewModel` period selection: 6 and 12 → `Locked`; chips stay visible with a Pro badge (D-4) |
| Insights: what your net worth is made of (by kind, shares) | Shipped | — | ✔ | ✔ | Yes | section renders a locked preview (totals hidden, structure shown) |
| Insights: FX gains and losses | Shipped (v13) | — (no foreign wallets on Free; existing ones still show) | ✔ | ✔ | Yes | follows the data |
| Ask Lucento: on-phone answers, four statements on screen (net worth, cash flow, budget vs actual, debts), top categories | Shipped | ✔ | ✔ | ✔ | Yes | none |
| Ask Lucento: **statements as PDF** (save / share) | Shipped | — | ✔ | ✔ | Yes | `AskLucentoViewModel` PDF actions → `ProRequired` |
| Category breakdown on Budget / Home | Shipped | ✔ | ✔ | ✔ | Yes | none |

Why this split and not "all Insights free" (v1): the owner wants analytics as a Pro reason, and the period selector is
a natural, honest depth line — a Free person still sees where this quarter's money went. Why not "all Insights Pro":
"Understand financial trends" is one of the six onboarding focuses; a focus that is entirely locked would be a
bait-and-switch in onboarding.

### 3.5 Data, backup and cloud (group B)

| Feature | Status | F | S | L | L+C | Offline | Gate |
|---|---|---|---|---|---|---|---|
| Everything stored on the phone (Room), no account needed | Shipped | ✔ | ✔ | ✔ | ✔ | Yes | none |
| JSON backup file and restore (merge or replace) | Shipped | ✔ | ✔ | ✔ | ✔ | Yes | none — portability is never sold |
| CSV export | Shipped | ✔ (D-9: recommended **yes**) | ✔ | ✔ | ✔ | Yes | none |
| Sign in (Google or email) | Shipped | ✔ | ✔ | ✔ | ✔ | Needs internet once | none — identity for the free AI allowance, Shared invitations, entitlement recovery |
| **Cloud sync** across phones, upload after every write | Shipped | — | ✔ | **first 12 months**, then — | ✔ | No (service) | `SyncPreferences.setEnabled(true)` and `backgroundSyncAllowed(enabled, onboarded, entitled)` |
| **Cloud restore** ("I already have Lucento" on a new phone) | Shipped | — (JSON file instead) | ✔ | first 12 months, then — | ✔ | No (service) | onboarding restore path checks entitlement **after** sign-in |
| Lapse behaviour | — | — | pause, keep everything, resume on renewal | same | same | — | §6.4 |

### 3.6 AI (group C) — enforced on the server

| Feature | Status | F | S | L | L+C | Offline | Gate |
|---|---|---|---|---|---|---|---|
| AI adviser (Gemini): plans, recommendations, projections over the person's own figures | Shipped | **10 questions / calendar month** (signed in, consent) | **50 / day**, soft monthly ceiling 400 (abuse guard, remote-configured) | first 12 months as S; then **10 / month** | as S | No | proxy reads plan from `entitlements`; app displays `remaining` |
| Smarter reading (Gemini reads the composer message) | Shipped | — (on-phone reading) | ✔ | first 12 months; then — | ✔ | No | proxy answers `403 pro_required`; consent switch shows the lock |
| On-phone reading, scanning (ML Kit), voice entry | Shipped | ✔ | ✔ | ✔ | ✔ | Yes | none |

### 3.7 Lucento Shared (group D; needs B)

| Feature | Status | F | S | L | L+C | Gate |
|---|---|---|---|---|---|---|
| Invite one Partner (Owner's plan) | Shipped (migrations on `lucento-test`) | — | ✔ | first 12 months; then — | ✔ | **server** `max_partners(owner)` → 1 when the Owner's cloud service is active, else 0 |
| Be a Partner | Shipped | ✔ (no plan needed) | ✔ | ✔ | ✔ | none — the Owner's plan carries the space |
| Wallet labels Shared / Personal (Owner's) / Personal (Partner's) | Shipped | — | ✔ | ✔ (while in a space) | ✔ | Owner-only structure, unchanged |

### 3.8 Appearance, privacy, misc

| Feature | Status | F | S | L | Offline | Gate |
|---|---|---|---|---|---|---|
| Themes | Shipped | Sunrise + Midnight (+ System / Light / Dark) | + Grove, Mono, Aurora | + Grove, Mono, Aurora | Yes | `AppViewModel.setTheme` persist refused for locked themes; live preview allowed |
| App lock, Hide amounts, Hide in recents | Shipped | ✔ | ✔ | ✔ | Yes | none — security is never sold |
| Enabled-features toggles, profile name and photo, achievements | Shipped | ✔ | ✔ | ✔ | Yes | none |

---

## 4. Pricing

### 4.1 Products in Play Console

| Product id | Type | Base plans / offers | Grants |
|---|---|---|---|
| `lucento_pro` | Subscription | `monthly` (P1M), `annual` (P1Y) + 7-day free trial offer on annual (once per account) | A + B + C + D while active |
| `lucento_pro_lifetime` | One-time, non-consumable | single purchase option | A forever; B + C + D for 12 months from first verification |
| `lucento_cloud` | Subscription | `monthly` (P1M), `annual` (P1Y); **only offered to Lifetime owners** (hidden otherwise) | B + C + D while active |

The app never shows two routes to the same thing: a Free user sees Pro Subscription and Pro Lifetime; a Lifetime owner
sees Lucento Cloud (and nothing about Pro Subscription, which would be a worse deal for them); a subscriber sees
"Switch to Lifetime" (buy Lifetime; the subscription is cancelled by the person in Play; Lifetime's 12 months start
from purchase so nothing overlaps badly — D-11 asks whether to credit the remaining subscription period, which Play
cannot do automatically).

### 4.2 Subscription prices (owner's working assumptions, kept)

₱149 / month, ₱1,190 / year (≈ ₱99 / month, 33% off), 7-day trial on annual. Reasoning as v1 §4.2; unchanged. USD
template $3.99 / $29.99.

### 4.3 Lifetime price — recommended ₱3,490 (USD $79.99)

Commercial reasoning:

- **Anchor to annual, not monthly.** Lifetime should cost 2.5–3.5 × annual so it is attractive to committed users
  without making annual look foolish. ₱3,490 = 2.93 × ₱1,190 and 23.4 × ₱149. Below ₱3,000 it cannibalises annual
  (a person planning two years already prefers it); above ₱4,000 it stops being an impulse in a PH consumer app.
- **It includes one year of services** (B/C/D), which on their own are worth ₱490 / year at the add-on price, so
  the local ownership is effectively ₱3,000 and the comparison with annual is honest.
- **Refund and chargeback risk** is bounded: Play's self-serve refund window for in-app purchases is short, and the
  Voided Purchases API / RTDN let the server revoke the mirror ([Voided Purchases API](https://developers.google.com/android-publisher/voided-purchases));
  the local snapshot follows at the next online contact (§5.4). The realistic exposure is one lost sale, not an
  ongoing cost — unlike a refunded subscription that kept using sync.
- **Lifetime is forever for group A only**, so the cost of honouring it is the cost of keeping the app working,
  which Lucento pays anyway.

Alternatives considered: ₱2,990 (too close to 2.5 × annual: at a 7-day trial on annual, a sizeable share of annual
buyers would switch, and the included services year makes it a near-giveaway); ₱4,490 (3.8 ×; converts fewer and
needs a launch promo to move at all). A **launch "founder" price** (₱2,990 for the first 60 days, then ₱3,490) is a
Play Console action, not an app change, and is recommended as the launch tactic (D-10).

### 4.4 Lucento Cloud add-on — ₱59 / month, ₱490 / year (USD $1.49 / $12.99)

For Lifetime owners after the included year (and, if the owner later wants, as a cheap on-ramp for Free users to sync
without Pro — not recommended at launch; D-12). Cost basis in §4.5: a sync + AI user costs roughly ₱15–₱40 a month at
realistic usage, so ₱59 covers it with margin and ₱490 / year reads as "less than the price of a coffee a month".
It is priced to be an easy yes, not a profit centre: the goal is that Lifetime owners never feel the servers were
withheld.

### 4.5 Cost model behind the AI and cloud numbers (verify against current price lists before launch)

| Item | Assumption | Per unit |
|---|---|---|
| Gemini Flash-class request (adviser): ~8k input tokens (figures, 60 days of transactions, system prompt) + ~600 output | $0.30 / M input, $2.50 / M output (verify) | ≈ **$0.004** (₱0.23) |
| Smarter reading request: ~1.5k in + 200 out | same | ≈ $0.001 |
| Free: 10 adviser questions / month | | ≤ $0.04 / user / month |
| Pro: 50 / day, soft ceiling 400 / month | worst case 400 | ≤ $1.60 / user / month (≈ ₱90 — survivable against ₱149; the realistic median is far lower) |
| Pro without a monthly ceiling | 1,500 / month | $6 / user / month — **more than the subscription**; hence the ceiling |
| Supabase sync storage and egress per user | tens of KB to a few MB | < $0.01 / user / month at current plan pricing; the fixed Supabase plan is the real cost |
| Vercel function invocations | per request | negligible at launch scale |

So: 50 / day is fine as the *daily* promise; the *monthly* soft ceiling (remote-configured, default 400, shown only
when hit: "You've used this month's adviser allowance") is what keeps a single heavy user from costing more than they
pay. Lifetime without Cloud gets the Free allowance (10 / month), which costs nothing meaningful and keeps the
adviser discoverable.

### 4.6 Promotions and future tiers

Play promo codes, introductory prices and the founder price are Play Console offers; no app change. Server `GRANT`
rows (with expiry) cover founders, testers, giveaways and goodwill. A future Pro Plus / Family is a new `Plan` and a
`max_partners` change (PRODUCT_SPEC §7); the matrix is data (`PlanCatalog`), not code paths.

---

## 5. Entitlement architecture

### 5.1 Model (`domain/entitlement/`, pure Kotlin, JVM-tested)

```kotlin
enum class Capability { LOCAL_PRO, CLOUD, AI_PRO, SHARED }          // groups A, B, C, D

enum class EntitlementSource { NONE, PLAY_SUBSCRIPTION, PLAY_LIFETIME, PLAY_CLOUD_ADDON, GRANT }

enum class EntitlementStatus { ACTIVE, TRIAL, GRACE, ON_HOLD, PAUSED, CANCELLED_ACTIVE, EXPIRED, PENDING, REVOKED }

data class Entitlement(                       // one row per source; a person can hold several
    val source: EntitlementSource,
    val status: EntitlementStatus,
    val grants: Set<Capability>,              // LIFETIME → {LOCAL_PRO}; SUBSCRIPTION → all four; CLOUD_ADDON → {CLOUD, AI_PRO, SHARED}
    val startsAtMillis: Long,
    val expiresAtMillis: Long?,               // null = never (LIFETIME's LOCAL_PRO)
    val productId: String?, val basePlanId: String?, val purchaseTokenHash: String?,
    val verifiedAtMillis: Long, val verifiedBy: Verifier,   // PLAY_LOCAL | SERVER | GRANT
    val autoRenewing: Boolean,
)

data class EntitlementState(val entitlements: List<Entitlement>, val evaluatedAtMillis: Long) {
    fun has(capability: Capability, now: Long): Boolean   // any ACTIVE/TRIAL/GRACE/CANCELLED_ACTIVE entitlement granting it, not past expiry (+ grace rules)
    val plan: DisplayPlan                                 // FREE | PRO | LIFETIME | LIFETIME_PLUS_CLOUD — for badges and copy only
}

enum class ProFeature(val requires: Capability) { MONEY_WALLETS, DEBT_ACCOUNTS, RECEIVABLES, VALUED_ASSETS, FOREIGN_CURRENCY_WALLETS,
    SAVINGS_GOALS, INSIGHTS_LONG_PERIODS, NET_WORTH_COMPOSITION, PDF_STATEMENTS, EXTRA_THEMES,
    CLOUD_SYNC, CLOUD_RESTORE, AI_ADVISER, SMART_READING, SHARED_SPACE }   // "ProFeature": domain/Features.kt already owns "Feature" (module toggles)

object PlanCatalog {                                      // data: the whole matrix in one tested object
    fun limit(feature: ProFeature, state: EntitlementState, now: Long): Limit   // Unlimited | None | Count(n) | PerMonth(n) | PerDay(n)
    // the required capability is ProFeature.requires
    fun comparisonRows(): List<ComparisonRow>             // feeds the in-app Free / Pro / Lifetime table
}

object EntitlementEvaluator {                             // pure: (state, feature, usage, now) -> Access
    sealed interface Access { object Allowed; data class Locked(val feature: ProFeature, val requires: Capability);
                              data class LimitReached(val feature: ProFeature, val used: Int, val limit: Limit, val withUpgrade: Limit) }
}
```

Why this and not `isPro`: a Lifetime owner after month 12 **has** `LOCAL_PRO` and **lacks** `CLOUD`; a subscriber in
account hold has everything until the hold's end; a Lifetime owner with Cloud active has all four from two sources.
One Boolean cannot say any of that. Why not an enum `FREE | PRO | LIFETIME`: the same person can hold two sources
(Lifetime + Cloud add-on, or Lifetime + a GRANT), and expiry applies per source.

`EntitlementManager` (in `AppContainer`) exposes `val state: StateFlow<EntitlementState>` and
`fun access(feature, usage: Flow<Int>): Flow<Access>`, combining three inputs in a fixed precedence:

1. **Server snapshot** (when signed in and reachable) — authoritative for B, C, D and for revocations.
2. **Play local reading** (`queryPurchasesAsync`, offline-capable, re-run on every process start and on
   `PurchasesUpdatedListener`) — authoritative for *adding* A and for PENDING.
3. **Cached snapshot** (DataStore) — what the UI shows in the first frame and whenever 1 and 2 are unavailable.

Rule: a newer verified reading replaces an older one **per source**; an unreachable source never downgrades anything
(see L-2). A `REVOKED` from the server or a purchase missing from Play's list *after a successful query* does.

### 5.2 Where gates live (and nowhere else)

| Gate | Enforced in (repository / ViewModel, tested on the JVM) | Shown by |
|---|---|---|
| Money wallets 3, debt accounts 2, receivables, valued assets, foreign currency | `WalletRepository.create` / `update` via an injected `EntitlementGate` (same pattern as `StructureAccess`) → `SaveResult.LimitReached` / `ProRequired` | Wallet editor, type picker badges |
| Goals 2 / 20 | `GoalRepository.save` (new) and `restore` | Goals, New goal |
| Insights 6 / 12 months, composition | `InsightsViewModel` | Period chips, section preview |
| PDF | `AskLucentoViewModel` | Report sheet buttons |
| Themes | `AppViewModel.setTheme` | Theme picker |
| Cloud sync on / allowed | `SettingsViewModel`, `backgroundSyncAllowed(enabled, onboarded, cloud)` (pure) | Settings › Account, sync chip, onboarding restore |
| Shared invite / accept | **Server** `max_partners(owner)`; app mirrors the space summary it already fetches | Shared settings |
| AI allowance, Smarter reading | **Server** proxy; app shows `remaining` and the 403 | Ask Lucento, consent switches |

Composables read `LocalEntitlements.current` (CompositionLocal, like `FxLocals`) **only** to render badges and copy.
`Feature` module toggles in `Features.kt` stay preferences; a module can be on and locked.

### 5.3 Local caching — the Lifetime rules

`EntitlementStore` = DataStore `entitlement_preferences`, **device-level** (survives `LocalLedgerReset` and account
switches, like `theme_id`; excluded from JSON backups; never synced). Each entry is the last verified `Entitlement`
plus a MAC computed with a key in the Android Keystore (`entitlement_mac`), so a file copied to another phone fails
verification and is ignored.

| Rule | Subscription sources | Lifetime (`LOCAL_PRO`) |
|---|---|---|
| L-1 Honoured offline | until `expiresAt` + 3 days (Play's own grace / hold states are already inside `expiresAt` once verified) | **indefinitely** — `expiresAt = null` |
| L-2 Verification failure | no change until expiry | **no change, ever**. Only a successful Play query that omits the purchase, or a server `REVOKED`, removes it |
| L-3 Re-verification | every start (Play), every 12 h open (server) | opportunistic; informational |
| L-4 Clock tampering | can extend a lapsed subscription offline by at most the grace; accepted | irrelevant |
| L-5 MAC failure (tampered / copied) | entry ignored → Free until re-verified | entry ignored → Free until Play or the account restores it (one tap, needs internet once) |

The Lifetime's **12-month services period** is a `PLAY_LIFETIME` entitlement granting `{CLOUD, AI_PRO, SHARED}` with
`expiresAt = firstVerifiedAt + 365 days`, written by the **server** at first verification (so a reinstall cannot
restart the year) and cached locally under subscription rules.

### 5.4 Server side (Supabase + Vercel)

New migration (append-only, RLS, `authenticated` may **select own** only; all writes by the Vercel endpoints with the
service-role key that exists only in Vercel env — never in the APK):

```sql
public.entitlements (
  id uuid pk, user_id uuid references auth.users, source text, status text,
  grants text[],                 -- {'LOCAL_PRO'} / {'LOCAL_PRO','CLOUD','AI_PRO','SHARED'} …
  product_id text, base_plan_id text, purchase_token_hash text,
  starts_at timestamptz, expires_at timestamptz null, verified_at timestamptz, updated_at timestamptz,
  unique (user_id, source, product_id))
public.purchase_links (          -- one Play purchase ↔ one Lucento account, ever
  purchase_token_hash text pk, order_id text, product_id text, user_id uuid references auth.users,
  obfuscated_account_id text null, linked_at timestamptz, relinked_count int default 0)
lucento_private.plan_limits (    -- remote configuration; read by security-definer functions and the proxy
  key text pk, value_int int, updated_at)   -- 'ai_free_monthly'=10, 'ai_pro_daily'=50, 'ai_pro_monthly_ceiling'=400, 'max_partners_pro'=1
```

Functions: `lucento_private.max_partners(owner)` → `1` when the owner has an active entitlement granting `SHARED`,
else `0`. `public.my_entitlements()` (security definer, returns the caller's rows — or plain RLS select).
`public.ai_allowance()` → `{plan, used_today, used_month, daily_limit, monthly_limit}` for the proxy and for the app's
"x of y left" line; `consume_ai_quota()` stays as is (counting), the proxy decides against the allowance.

Vercel (`lucento-website/src/pages/api/billing/`): `verify.ts` (POST `{purchaseToken, productId}` with the user's
Supabase JWT → Play Developer API `purchases.subscriptionsv2.get` or the one-time-product endpoint
(`purchases.productsv2` on current APIs, `purchases.products` legacy — verify which the Billing 9 purchase token
needs) → write `entitlements` + `purchase_links` → return state); `rtdn.ts` (Pub/Sub push: subscription and one-time
product notifications, voided purchases → re-verify, update status, `REVOKED` on refund with revoke); a daily
`voided.ts` cron (Voided Purchases API, last 30 days) as the belt to RTDN's braces.

**Linking rule (prevents purchase transfer between unrelated accounts):** at purchase, the app sets
`obfuscatedAccountId = sha256(supabaseUserId)` when signed in. `verify.ts` accepts a token for user U only if the row
is unlinked, or linked to U, or (re-link) the token's `obfuscatedAccountId` matches U *and* the previous link is older
than 30 days — otherwise `409 linked_elsewhere` with the support path. A purchase made while signed out links to the
first account that verifies it. Re-links are counted; more than 2 in a year flags the row for review. The Lucento
account that holds the link can always recover the purchase (§5.6), which is the legitimate case the rule protects.

### 5.5 Plan-aware AI on the proxy

`ask.ts` / `interpret.ts`: after auth, call `ai_allowance()`; Free → monthly limit; Pro / Cloud → daily limit plus
the monthly ceiling; Smarter reading → `403 pro_required` without `AI_PRO`. Limits come from `plan_limits` with env
vars as fallback, so quotas change with one SQL update, no deploy. Responses keep `remaining` and add `period`
(`day` | `month`) and `resetsAt` so the app's copy is a display of the server's decision.

### 5.6 Recovery after reinstall or a new phone (what the FAQ promises)

| Situation | What happens |
|---|---|
| Same Google account on the new phone, Play Store present | On first start `queryPurchasesAsync` returns the Lifetime (and any subscription); honoured immediately, offline-capable from then on. Server mirror refreshed when online |
| Different Google account, or no Play Store | Sign in to the Lucento account the purchase was linked to → `my_entitlements()` returns `PLAY_LIFETIME` → cached. Needs internet once |
| Neither (bought signed out, Google account lost) | Google Play order number → support re-links manually (`purchase_links` row). Rare; documented |
| Financial data | separate: JSON backup (free) or cloud restore (B active). Buying Lifetime never moves data by itself — the promise says so |

### 5.7 Debug, CI, tests

`FakePurchaseSource` for debug and CI builds (the `.debug` applicationId can never bill) with a test-build-only
"Simulate plan" section in Settings (Free / Pro / Pro cancelled / Pro on hold / Lifetime / Lifetime + Cloud / Lifetime
year ended / Pending / Billing unavailable). JVM tests drive every state and every gate through fakes; no Play
dependency in `app/src/test`. **No Room migration** is needed anywhere in this design: counts are live DAO queries,
entitlement is DataStore, server state is new Supabase tables.

---

## 6. Feature locking and downgrade behaviour

### 6.1 Tone and placement

No launch interstitials, no repeated popups, no countdowns, no red badges, no pre-checked boxes. The upgrade sheet
opens only from the tap that would have used the locked thing. "Not now" is remembered per place per session. A
dedicated **Lucento Pro** page lives in the profile menu and Settings; it is the only place that *initiates* selling.
The mascot appears once, on that page. Badges (`ProBadge`, "Pro") sit inline next to labels; a person who holds the
capability never sees them. All copy is in `CUSTOMER_COPY.md` D.

### 6.2 Plan indicator

Profile menu row and Settings header show one of: "Free plan" · "Lucento Pro · renews 6 Dec" · "Lucento Pro · ends
6 Dec" (cancelled) · "Lucento Pro · payment problem" (hold / grace) · "Pro Lifetime · you own it" · "Pro Lifetime ·
Cloud until 6 Nov 2027" · "Pro Lifetime · Cloud ended" · "Courtesy access until …" (grant).

### 6.3 Downgrade rules (every limited feature; tested in §10)

1. Nothing is deleted, hidden, archived or made read-only because an entitlement changed. Over-limit wallets, debt
   accounts, goals, foreign wallets, valued assets and receivables stay fully usable (edit, transact, contribute,
   reach, archive, delete).
2. Only **creation** (and un-archive, and type/currency changes into a gated kind) is refused, with the count and the
   way out shown.
3. Insights 6 / 12 months and composition become locked previews; the 3-month view keeps working.
4. Themes: a locked theme already selected stays until the person changes it; the picker shows it as Pro.
5. PDF: the on-screen statement stays; Save / Share show the lock.
6. Data paths stay alive: foreign wallets keep quoting and translating, FX results keep showing, AI consent stays stored.
7. The person is told **once**, on the first start after the change, which things are now restricted — one sheet,
   one list, one "OK" (`CUSTOMER_COPY.md` D-7).

### 6.4 Service lapse (B / C / D)

| Event | Sync | Shared | AI |
|---|---|---|---|
| Subscription expires; Lifetime's year ends; Cloud add-on expires | Uploads and pulls **pause**; chip says why; dirty rows wait in the existing queue; nothing on the server is deleted; renewing resumes within a cycle | **Lenient**: no new invitations; the existing Partner stays and keeps syncing (their own Pro, or the Owner's lapsed space, is the same lenient rule). Zero data risk; small revenue leak; stricter options later with data (v1 §7.3) | Drops to the Free allowance (10 / month); Smarter reading off, on-phone reading on |
| Account hold / grace (payment problem) | Keeps working through Play's grace; plain-words notice | unchanged | unchanged |
| Refund with revoke (Play) | `REVOKED` → as expired, immediately | as expired | as expired |

**Partner protection (brief §6):** a Partner's access is never interrupted by the Owner's plan change. Only the Owner
removing them or them leaving ends membership (shipped behaviour). A Partner's *own* Free limits apply only to their own
ledger if they leave.

---

## 7. Decisions (approved by the owner, 2026-11-06)

| # | Decision | Owner's brief | Decision (approved) | Why |
|---|---|---|---|---|
| D-1 | Free money-wallet limit | 2 | **3 active** (cash, bank, e-wallet, savings) | §3.2: 2 fires during guided setup for a typical PH user |
| D-2 | Free debt-account limit | "basic debts and loans" | **2 active** cards + loans; receivables Pro | debts are wallets; a separate count keeps Free able to track debt |
| D-3 | Goals | Free 2, Pro 20 | **Accept** (2 / 20 / 20) | archived goals never count; only reached goals can be archived — copy says so |
| D-4 | Insights split | "Advanced analytics: Pro" | Free 3 months; Pro 6 / 12 months + composition | a focus feature stays usable; depth is sold |
| D-5 | Manually valued assets and receivables | "Detailed net worth / advanced debts: Pro" | **Pro** (existing ones preserved) | real shipped depth; maps the brief to code honestly |
| D-6 | Multicurrency | Pro | **Pro / Lifetime** (local) | unchanged from v1 |
| D-7 | Custom-category cap for Free | "standard categories" | **No cap** | budgets are built from categories; the cost is zero |
| D-8 | Scheduled-payment cap for Free | "basic recurring" | **No cap** | "never miss a bill" focus; cost zero |
| D-9 | CSV export in Free | evaluate | **Yes** | portability is never sold; it is also the trust signal behind "your data" |
| D-10 | Lifetime price | — | **₱3,490** (USD 79.99); founder offer ₱2,990 for 60 days | §4.3 |
| D-11 | Subscriber buys Lifetime | — | allowed; the person cancels the subscription in Play; no automatic credit (Play has none). Offer a support-granted Cloud extension equal to the unused months, manually, on request | avoids building proration logic Play does not support |
| D-12 | Lucento Cloud add-on | — | **₱59 / ₱490**, offered to Lifetime owners only at launch | §4.4; a Free-tier Cloud SKU would undercut Pro |
| D-13 | Lifetime included services | evaluate | **12 months of B/C/D from first verification**, then Free AI allowance; Cloud add-on optional | the person experiences sync and the adviser; the promise stays bounded |
| D-14 | AI monthly soft ceiling for Pro | 50 / day | **400 / month**, remote-configured, shown only when hit | §4.5 cost model |
| D-15 | Role names | Manager / Companion | **Owner / Partner** (as approved and built) | PRODUCT_SPEC §2; a rename is a separate decision |
| D-16 | Shared lapse policy | "do not interrupt companion" | **Lenient** (no new invites; existing Partner stays) | §6.4 |
| D-17 | Where verification runs | — | Vercel + Play Developer API; Supabase stores | v1 D-10, unchanged |
| D-18 | Founders / testers | — | `GRANT` rows with expiry on `lucento-test` now, production before the gated build ships | v1 D-9 |

Decisions **already settled by the brief** and taken as given: three plans; monthly ₱149 / annual ₱1,190 / 7-day trial;
Lifetime = permanent ownership of local features; AI enforced server-side; never a lone `isPro`; never delete data.

---

## 8. Website and Play listing consistency

| Information | Single source | Mirrors |
|---|---|---|
| The matrix (what each plan can do) | `PlanCatalog` (Kotlin, tested so the table and the gates agree) ← this document §3 | `lucento-website/src/data/pricing.ts` (new), Play listing text, in-app comparison table, `CUSTOMER_COPY.md` A |
| Numeric limits | `PlanCatalog` for client counts; `plan_limits` for server-enforced AI / partners | website copy quotes them; a website unit test pins the numbers |
| Prices | Google Play Console | app via `ProductDetails`; website "from ₱149 / month · ₱3,490 once" marked *indicative, final price in Google Play* |
| The Lifetime promise and FAQ | `CUSTOMER_COPY.md` B and E | website `/pricing` and `/faq`; in-app Pro page "What Lifetime means" link |
| Legal | website `/terms` (new), `/privacy` | upgrade screen footer |

Website changes needed in the same release: pricing section, the FAQ line at `home.ts:181`, the "Sync, if you want
it" and adviser cards in `features.ts` (sync and the adviser allowance are Pro), a `/terms` page. The website PRs are
parked until the Android side is final (memory: website PRs deferred).

---

## 9. Google Play Billing requirements (Phase 4 of implementation)

| Requirement | How |
|---|---|
| Library | `com.android.billingclient:billing-ktx` **9.x** (v8+ mandatory for submissions since 31 Aug 2026; v9 shipped May 2026 — v1 §4.5 sources). `enablePendingPurchases`, `enableAutoServiceReconnection` |
| Subscriptions | `queryProductDetailsAsync(SUBS)` → base plan / offer token → `launchBillingFlow` with `obfuscatedAccountId` when signed in |
| One-time Lifetime | `queryProductDetailsAsync(INAPP)`; Billing 8+ exposes `getOneTimePurchaseOfferDetailsList()` per product — use the single purchase option's offer token ([Android Developers — one-time product purchase options and offers](https://developer.android.com/google/play/billing/one-time-product-multi-purchase-options-offers?hl=de), [RevenueCat — the purchase flow](https://www.revenuecat.com/guides/google-play-billing/the-purchase-flow)). Non-consumable: **acknowledge, never consume** |
| Verification | local `Purchase` signature check with the app's Play public key; server verification when signed in; RTDN + daily Voided Purchases check keep the server current |
| Acknowledgement | within 3 days after verification; retried on next start if it failed |
| Pending (PH cash payments) | `PENDING` → UI state; nothing granted until `PURCHASED`; completes hours later via `queryPurchasesAsync` on start or RTDN |
| Restore | `queryPurchasesAsync(SUBS)` + `(INAPP)` on every start and on "Restore purchases"; re-links the server mirror when signed in |
| Refunds / revocations | RTDN (subscription + one-time product), Voided Purchases API (30-day window — record as they arrive); server `REVOKED` → local at next contact |
| Grace, hold, pause, cancel | `subscriptionsv2` state on the server; `isAutoRenewing` + expiry locally; plain-words copy |
| Account identity | Play account ≠ Lucento account, handled by `purchase_links` (§5.4) |
| Errors | `BillingProblem` set: unavailable (no Play Store), network, user cancelled, already owned, item unavailable, developer error; plain copy + Retry |
| Testing | license testers, test cards, accelerated renewals, internal track; `FakePurchaseSource` for everything automatable |

---

## 10. Test plan (acceptance for the whole programme)

| Scenario | JVM (fakes) | Device / server |
|---|---|---|
| New Free user: every gate per §3; onboarding never shows Pro before Home; 3 wallets + 2 debts + 2 goals created without a sheet, the 4th / 3rd / 3rd shows the right count | ✔ | tour screenshots |
| Existing user with 6 wallets, 3 debts, 2 receivables, a valued asset, 5 goals, 2 foreign wallets, sync on, Owner of a Shared Space — before gating | all usable; create → LimitReached / ProRequired; sync pauses with reason; Shared membership intact | owner's phones after GRANT |
| Lifetime owner offline for 60 days, Play unreachable, Supabase down | `LOCAL_PRO` still true; no notice; all A features work | airplane mode soak |
| Lifetime snapshot copied to another device / edited | MAC fails → Free until restore | rooted emulator |
| Lifetime year ends | B/C/D off; A intact; one notice; Cloud add-on offered | accelerated via fake |
| Lifetime + Cloud add-on; add-on lapses; renews | B/C/D toggle; sync pauses and resumes; queue intact | test cards |
| Subscriber: trial → active → cancelled (active till end) → expired; hold; grace; pause | status copy; capabilities per state | accelerated renewals |
| Subscriber buys Lifetime | both entitlements present; Lifetime year starts; Pro page shows "cancel in Play" | internal track |
| Pending purchase (cash) | PENDING UI; nothing granted; completes later | slow test card |
| Reinstall, same Google account | Lifetime back without sign-in | reinstall |
| New phone, other Google account, Lucento sign-in | Lifetime back via server mirror | two accounts |
| Purchase token submitted from an unrelated account | `409 linked_elsewhere` | stub |
| Refund with revoke | server REVOKED → local Free on next contact; data intact | Play Console refund |
| Lucento account switch / sign-out / `LocalLedgerReset` | entitlement store survives; server link unchanged | two accounts |
| Partner when Owner lapses | Partner keeps full access; Owner cannot invite | two phones |
| AI: Free 10 / month boundary, Pro 50 / day and 400 / month, Lifetime-without-Cloud 10 / month, Smarter reading 403 | proxy unit tests | signed-in release build |
| Server | `tools/sql-verify`: RLS on `entitlements` / `purchase_links` (read own, no writes), `max_partners` per state, `ai_allowance` per plan, `plan_limits` not readable by clients | advisors |

---

## 11. Implementation roadmap

All on `feature/lucento-pro-*` branches from `origin/main` (after §1.1), PRs to `main`, nothing merged without
approval. Each phase reports files, tests run, decisions and the next step in `docs/handover/`.

### Phase 1 — Entitlement architecture (no behaviour change) — **built 2026-11-06 on `feature/lucento-pro-architecture`**

What landed (see docs/handover/CURRENT_STATUS.md for the test evidence):

- `domain/entitlement/`: `Capability`, `EntitlementSource` (+ `PLAY_LIFETIME_SERVICES` for Lifetime's included year),
  `EntitlementStatus`, `Verifier`, `Entitlement` (`covers` with the 3-day offline grace; Lifetime never expires),
  `EntitlementState` (`has`, `capabilities`, `displayPlan`, `pending`), `ProFeature` (named so because `Features.kt` owns
  `Feature`), `Limit`, `PlanCatalog` (every approved number; `comparisonRows()`), `EntitlementEvaluator` (`Access`),
  `EntitlementMerge` (Play vs server precedence, L-1…L-5), `PlayProducts`, `PurchaseRecord`, `PurchaseInterpreter`.
- `data/billing/PurchaseSource` + `FakePurchaseSource`; `data/entitlement/`: `EntitlementCache` (`SealedEntitlementCache`,
  `EntitlementSnapshotCodec`, `Sealer`/`HmacSealer`), `DataStoreSnapshotStore` (`entitlement_preferences`), `KeystoreSealer`
  (Android Keystore HMAC, SharedPreferences fallback), `EntitlementRemote` (interface only), `EntitlementManager`
  (`StateFlow<EntitlementState>`, `loadCached` → `refresh` → `applyPlayUpdate`, `access`, `check`).
- `data/repository/EntitlementGate` (`Permissive`) injected into `WalletRepository` (money / debt / receivable / valued-asset
  kinds counted apart; `SaveResult.LimitReached` / `ProRequired`) and `GoalRepository` (`SaveResult.LimitReached`,
  `restore(): RestoreResult`). The app wires `Permissive`, so **behaviour is unchanged**; the two UI result matches gained
  the branches they will need (never reached today).
- `feature/pro/LocalEntitlements`, provided by `MainActivity` from `AppContainer.entitlements` (display only).
- Server draft: `supabase/migrations/20261107090000_entitlements.sql` + `tools/sql-verify/entitlements_suite.sql` (38
  checks) and the GRANT fixture in the Shared and multicurrency suites; the endpoint design in
  `docs/monetization/SERVER_CONTRACT.md`. **Not applied anywhere.**
- Not in Phase 1 (by design): the foreign-currency wallet gate (needs the main currency — Phase 2), Insights / PDF / theme /
  sync gates, any UI, Play Billing, the Supabase `EntitlementRemote`.


- **Scope**: `domain/entitlement/` (`Capability`, `EntitlementSource`, `EntitlementStatus`, `Entitlement`,
  `EntitlementState`, `Feature`, `Limit`, `PlanCatalog`, `EntitlementEvaluator`); `data/billing/PurchaseSource` +
  `FakePurchaseSource`; `data/entitlement/EntitlementStore` (DataStore + Keystore MAC) and `EntitlementManager`
  (StateFlow) wired in `AppContainer`; `LocalEntitlements` CompositionLocal; `EntitlementGate` interface injected into
  `WalletRepository`, `GoalRepository` (no refusals yet: a `PermissiveGate` is wired so every evaluation is Allowed);
  Supabase migration **draft** for `entitlements`, `purchase_links`, `plan_limits`, plan-aware `max_partners`,
  `ai_allowance()` + `tools/sql-verify` cases (applied to `lucento-test` only on approval); design doc for the Vercel
  endpoints.
- **Files**: new packages above; `LucentoApplication.kt` (AppContainer), `WalletRepository.kt`, `GoalRepository.kt`
  (constructor only), `core/ViewModelFactory.kt` untouched; `supabase/migrations/2026MMDDhhmmss_entitlements.sql`;
  `docs/MONETIZATION.md` status line.
- **Migrations**: none for Room. One Supabase migration (new tables + functions; RLS; grants `select` to
  `authenticated` on `entitlements` only).
- **Risks**: Keystore MAC on older devices (minSdk 26 has `KeyGenParameterSpec`; fallback to a plain stored hash with
  a flag, never a crash); DataStore name collisions (none: `entitlement_preferences` is new).
- **Tests**: `PlanCatalogTest` (every row of §3 pinned), `EntitlementEvaluatorTest` (states × features × usage,
  Lifetime L-1…L-5 rules, precedence of sources, grace), `EntitlementStoreTest` (round trip, MAC failure, survives
  reset), `EntitlementManagerTest` (fake Play + fake server + cache precedence), sql-verify suite.
- **Acceptance**: `testDebugUnitTest assembleDebug` green; app behaviour identical to today (permissive gate);
  `lucento-architect` review of the model and server contract; owner approval of the migration before it is applied.

### Phase 2 — Gates and downgrade rules
Repository and ViewModel gates of §5.2 with real `Limit`s; `backgroundSyncAllowed(cloud)`; onboarding restore
check; usage queries (`WalletDao.countActiveOfTypes`, `GoalDao.countActive`); default plan **FREE** for everyone
except GRANT rows; proxy plan-aware limits (website PR); `lucento-qa` pass on §6.3; owner / tester GRANTs on
`lucento-test`.

### Phase 3 — UI
`ProBadge`, `ProLockedSheet`, `ProLimitSheet`, the Lucento Pro page (plans, comparison from `PlanCatalog`, Lifetime
explainer, restore, manage), profile row, Settings page, theme preview, Insights locked preview, first-start
restriction notice, test-build plan simulator; screenshot tour frames; font scale 2.0 / 320 dp / TalkBack.

### Phase 4 — Google Play Billing and server verification
`billing-ktx` 9.x, `PlayPurchaseSource` (SUBS + INAPP, pending, acknowledge, restore, `obfuscatedAccountId`);
Vercel `verify.ts`, `rtdn.ts`, `voided.ts`; Play Console products, base plans, offers (trial, founder price), RTDN
topic, license testers; release signing (CS-20) and `targetSdk` check first.

### Phase 5 — QA, website, launch
§10 matrix on devices; website pricing / FAQ / terms / feature copy; Play listing; production migrations
(`entitlements` and friends together with the gated release); docs (README, ROADMAP, this file → "implemented").

---

## 12. Risks that must be resolved before implementation starts

1. ~~Owner decisions D-1 … D-18~~ — **resolved 2026-11-06**: every recommendation in §7 accepted (wallets 3, Insights split, Lifetime
   ₱3,490 with founder offer, 12 months of services, Owner / Partner names).
2. ~~Branch reconciliation~~ — **done 2026-11-06** (§1.1); the checkout is on `origin/main` `15e7a46`.
3. **Release signing (CS-20) and `targetSdk`** — without a signed internal-track build, Phase 4 cannot be tested on a
   device; verify whether Play requires `targetSdk 36` for new submissions.
4. **Production Supabase state** — confirm the Lucento Shared and multicurrency migrations are applied to production
   (OT-9, OT-12) before stacking the entitlement migration on them.
5. **Play Developer API access** — a service account with "View financial data" (for Voided Purchases) and
   "Manage orders and subscriptions" linked to the Play Console; its JSON key goes in Vercel env only.
6. **Legal pages** — Terms of service (subscription terms, Lifetime definition, refund pointer to Google Play) must
   exist on the website before the first purchase is possible.
7. **Gemini price verification** — §4.5 numbers are estimates; confirm against the current price list and set
   `plan_limits` accordingly.
8. **Website / app drift** — the website cannot describe sync or the adviser as free after the gated build ships; the
   two releases must be coordinated.
9. **Existing testers** — grant Pro to the owner's and testers' accounts before the gated build reaches them, or their
   sync pauses mid-test.
