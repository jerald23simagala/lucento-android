# Lucento launch film: feature-to-visual map

How each feature becomes a picture. Screens are real Lucento 0.5.8 captures (`ASSET_INVENTORY.md` lists where each
came from). Animation means camera work on those captures, or a reveal of their own pixels. Nothing inside a screen
is drawn. Status and plan come from `../LAUNCH_VIDEO_FEATURE_DISCOVERY.md`.

## Tier 1: hero features (dedicated demonstrations)

| Feature | App screen | Interaction | Demonstration scenario | Asset | Animation | Message | Priority |
|---|---|---|---|---|---|---|---|
| Dashboard | Home ("Dashboard") | Scroll | Net wallet balance ₱258,108.10, budget 89% "Ahead of pace", Safe to spend, Upcoming | `dash`, `dash-lower`, `dash-lowest` → stitched `dash-strip` | Phone rises from the dark; smooth pan with header and dock pinned | "Everything, in one clear view." | 1 |
| Voice entry | Composer → Listening sheet | Tap mic, speak | "Add 500 pesos for lunch using cash." → Lunch −₱500 · Restaurants · Cash | `composer-empty`, `listening`, `voice-rows` | Sheet rises (real pixels); mic ring pulses; spoken words appear beside the phone | "Say it. Speak naturally. Lucento fills in the details." | 1 |
| Natural-language typing (on-device reading, not AI) | Composer | Type, send | "Paid 850 for groceries using my cash wallet" → Groceries −₱850 · Cash | `typed-field`, `typed-rows` | Push in on the field; real typed text revealed in order; reply slides up | "Type it. Write it the way you'd text it." | 1 |
| Bulk entry | Composer | One message, one save | "Coffee 180, lunch 250, Grab 240, load 300" → 4 rows → "4 transactions saved" | `bulk-field`, `bulk-rows`, `bulk-rows-end`, `bulk-saved` | Rows lift out into a grid and return; Save tap; confirmation | "All at once. Four in one message. One tap to save." | 1 |
| Lucento Shared (Pro) | Composer and Transactions on two phones | Partner adds; it appears for you | "Family dinner 1,280" saved by the partner, highlighted at the top of your list | `partner-field`, `partner-rows`, `partner-saved`, `tx-after-bulk`, `tx-after-partner` | Two phones, "You" / "Your partner" chips, an arc of light carrying the entry | "Better financial decisions, together. One shared financial picture." | 1 |
| Ask Lucento | Ask Lucento | Tap a suggested question | "What are my top 5 spending categories?" → on-phone answer, then the report | `ask`, `ask-answer`, `ask-report` | Answer slides in; highlight; "Open report" | "Answered on your phone." | 1 |
| Multicurrency wallets (Pro) | Wallets (cards) | — | ¥60,000, AED 4,200 and $1,850 with peso values; net worth ₱258,108.10 | `wallets-mc`, `wallet-usd` | Cards fan out; the foreign three step forward | "Every currency." | 1 |

## Tier 2: supporting features (meaningful moments)

| Feature | App screen | Scenario | Asset | Animation | Message | Priority |
|---|---|---|---|---|---|---|
| Built-in reports | Insights › Reports; report view | Top spending categories statement | `ask-report`, `insights-top` | Cross-fade after "Open report" | "Six built-in reports." | 2 |
| Insights | Insights | Money in/out bars; "Where your money went" | `insights-flow`, `insights-donut` | Bars grow from the baseline; ring clock-wipe (both reveals of real pixels) | "See where your money goes." | 2 |
| Budget | Budget | November, ₱2,920.10 left of ₱25,900 | `budget` | Slides in as the centre of a trio | "Plan today." | 2 |
| Preset categories | Onboarding "What do you spend on?" | 82 presets, 12 already ticked | `onb-categories` | Ticked chips glow in turn beside a large "82" | "82 ready-made categories. Twelve everyday ones are picked for you." | 2 |
| Savings goals | Goals | Emergency fund 42%, Japan trip 55% (Fuji card) | `goals` | Trio | "Feel confident tomorrow." | 2 |
| Scheduled transactions | Scheduled | Overdue Fibre internet; Phone plan, Visa payment, Car loan instalment | `scheduled` | Trio | label "Scheduled" | 2 |
| Wallet customisation | New wallet (live card preview); bank faces | — | `wallet-new` | Not used in the master (no colour picker exists; bank logos are trademarks) | — | 3 |
| App customisation | Wallets in five themes | Sunrise, Midnight, Aurora, Grove, Mono | `theme-*` (9 variants) | Theme arc that folds into one phone | "Your money. Your way. Five themes." | 2 |
| Assets and liabilities / net worth | Wallets net-worth hero | Net worth, "You owe 31% of what you have", split bar | `wallets-mc` | Highlight on the hero; slight push in | "Every asset." | 2 |
| Hide amounts | Dashboard | Tap Hide → ₱•••• everywhere | `dash`, `dash-hidden` | Tap ripple, cross-fade | chip "Hide amounts" | 2 |

## Tier 3: montage (quick beats)

| Feature | Where it appears |
|---|---|
| Income and expense tracking | Transactions cards (Income +₱42,000, Expenses, Net inflow) in S07; Insights in S06 |
| Credit cards (limit, available credit, amount due) | Visa card in the S03 card fan |
| Loans and debts | Car loan instalment in Scheduled (S05); Debt & Loans −₱114,400 on the Dashboard (S02) |
| Recurring transactions | Scheduled phone (S05) |
| Safe to spend | Dashboard pan (S02) |
| Offline-first, App lock, no bank login | Chips in S08 |
| Awards, the payments bell, wallet adjustments | Captured (`awards`, `bell`), not used in the master; future videos |

## Tier 4: future marketing content

These earn their own films (`FUTURE_VIDEOS.md`):

- statement scanning
- receipts
- widgets
- the purchase check ("Can I afford it?")
- AI advice with Gemini (Pro)
- Smarter reading (Pro)
- loans and credit cards in depth
- awards
- backup and restore
- the Philippine vocabulary
- privacy (Hide amounts, App lock, Hide in recent apps)
