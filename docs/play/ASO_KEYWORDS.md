# Google Play keywords (research 2026-11-25)

Why: the closed test's report (Testers Community, 25 Nov) said the listing lacked search keywords, and the owner asked
for a short description that covers "all possible keywords that users might use". The listing texts themselves live in
`PLAY_STORE_SUBMISSION.md` §4; this file keeps the evidence behind them.

**Method.** Read on 25 Nov 2026 from public pages only:
- 34 apps' Play listings (US and Philippine views): the short description is in each page's HTML.
- Play's search suggestions with `gl=US` and `gl=PH`. Google says they come from all Play users' search patterns. They
  show that a phrase is searched, not how often.
- Play search results in the Philippines, and Google web suggestions for the Philippines.
- Google Trends was rate-limited (HTTP 429), so there's no volume data. After launch, Play Console's search terms by
  country are the real measure.

## What people type (Play suggestions)

| Market | Evidenced terms |
|---|---|
| US | budget app (+ free offline, for couples), budget planner, budget tracker (+ free offline), **expense tracker**, expense manager (+ offline), money manager (+ budget planner, expense tracker offline), offline budget tracker/planner/app, spending tracker, money tracker, daily expense tracker, bills tracker and reminder, savings tracker, savings goal offline, debt tracker (+ offline), loan tracker "you owe me", net worth tracker, subscription tracker, personal finance app, monthly budget planner, voice expense tracker, multi currency expense tracker, shared budget app, manual budget app, budget widget |
| Philippines | **budget tracker** (first for "budget"), budget planner, budgeting app, **expense tracker** (+ offline), money manager and expenses, budget tracker philippines, **gastos tracker**, gastos ko, **ipon tracker** (+ offline), ipon app, ipon challenge, **utang tracker** (+ app, ph), **listahan ng utang** (+ offline), salary budget planner, allowance tracker, bill reminder and tracker |
| Avoid | "utang", "pera", "sweldo", "sahod", "tipid", "pautang", "peso" on their own, "wallet" and "cash": Play suggests loan, cash-advance or wallet apps for them |

## What the leading apps use

Count of the 21 leading budget apps (Money Manager, Wallet, Spendee, Monefy, 1Money, Money Lover, Goodbudget, Bluecoins,
Fast Budget, YNAB, Monarch, PocketGuard, Expense Manager, Cashew, Spending Tracker, Rocket Money, Toshl, EveryDollar,
MyMoney, Money Tracker, Money Manager by KTW) using each word in their title or short description:

| Word | Apps |
|---|---|
| budget | 19 |
| money | 17 |
| expense | 16 |
| track | 15 |
| manage | 15 |
| finance | 10 |
| spending | 8 |
| "expense tracker" | 8 |
| "money manager" | 7 |
| **offline** | **0** |

On broad English searches in the Philippines the big global apps rank first. On "ipon", "ipon tracker" and "utang
tracker", small local apps with the word in their title lead (IponPH, UTANG TRACKER PH, Sentimo, Kaya), so these are
niches with little competition.

## How Play uses the text

- **Google:** search uses the title, description and other details for relevance. Keywords added only to rank "will
  not impact ranking". Repetitive keywords can get an app suspended. No field weights are published.
- **ASO vendors** (Sensor Tower, AppTweak): the title weighs most, then the short description, then the full
  description.
- **Words split across title and short description:** no source confirms they combine. Apps rank for "expense tracker"
  with the words apart, but the exact phrase is the safer choice.
- **Custom store listings:** in the Philippines the custom listing's text is what shows in search results. Whether it
  changes ranking is disputed, so it carries the English core terms as well as the Taglish ones.

## Chosen (25 Nov) and alternatives

| Listing | Short description | Characters |
|---|---|---|
| Global | Expense tracker, budget planner & money manager. Type it like a text, offline. | 78 |
| Philippines | Expense tracker & budget planner for gastos, ipon and utang. Offline, in pesos. | 79 |

Alternatives worth an A/B test (Play Console › Store listing experiments) once there's traffic:

| Listing | Alternative | Characters |
|---|---|---|
| Global | Expense tracker and budget planner you type like a text. Offline, no bank login. | 80 |
| Global | Type, say or scan what you spend. Expense tracker and budgets that work offline. | 80 |
| Philippines | Expense tracker for gastos, ipon at utang. Parang text lang, offline, in pesos. | 79 |
| Philippines | Type your gastos like a text. Budget, ipon and utang tracker in pesos, offline. | 79 |

A Philippine title of 30 characters is possible in a custom listing, for example "Lucento: Budget & Ipon Tracker".
That is a brand call for the owner and has not been made.
