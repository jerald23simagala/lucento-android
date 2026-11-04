# Ask Lucento

Ask Lucento answers questions about the user's own money and prepares statements as PDFs. It opens from the hero at
the top of Insights (`feature/ask`). Owner decisions (2026-11-03): reuse the Gemini proxy that Smarter reading uses;
the AI sees **summaries only**; the four preset reports are net worth, cash flow, budget vs actual, and debts & loans.

## Two ways an answer is made

| | On this phone | AI answers (opt-in) |
|---|---|---|
| What | The preset questions (chips), the report tiles, and any typed question `AskRouter` recognises (top categories, net worth, cash flow / spending, budget, debts; "last month", a month's name) | Any other money question |
| Who works it out | `ReportBuilder` — the same calculators as Home, Budget, Wallets and Insights | Gemini through Lucento's proxy, quoting figures the phone calculated |
| Needs | Nothing: works offline | A proxy in this build, a signed-in sync account, and the user's consent |
| Labelled | "Calculated on this phone" | "Answered by Gemini · bold figures from this phone" |

Local rules are never presented as AI (CLAUDE.md). When AI answers are on but Gemini can't answer (offline, daily limit,
timeout), a question the phone can route is answered there, with the reason; anything else says what went wrong.

## Reports

`domain/ask/Reports.kt` builds a `Report`: one currency, a headline figure, sections with columns and totals, and notes
on how it was calculated. The screen (`ReportSheet`) and the PDF (`ReportPdfWriter`) draw the same object, so they
can't disagree. Definitions are in `docs/CALCULATIONS.md` "Ask Lucento reports".

- **Net worth statement**: wallet by wallet at the end of a month (today for the current month), and the change since the
  previous month's end (`BalanceHistory.walletsAt`).
- **Cash flow statement**: income and expenses by category and their net flow. Transfers and balance adjustments are
  listed apart: they never change net flow.
- **Budget vs actual**: `BudgetPlanner.plan`, the Budget screen's own plan: each budgeted category's budget, spending and
  what's left, then spending without a budget.
- **Debts & loans summary**: each card and loan, with what's owed, the rate, a loan's instalments, a card's amount due,
  and the next scheduled payment.
- **Top spending categories** (answers, not a tile): the top N with their shares and "everything else".

PDFs: Android's `PdfDocument` (no library), A4, Plus Jakarta Sans, a brand header and a footer on every page ("Calculated
on this phone by Lucento from your own records. Not financial advice."). **Save as PDF** uses the system file picker;
**Share** writes to private cache (`cache/reports/`, replaced each time) and hands it to the share sheet through the
existing `FileProvider`.

## What the AI sees, and the checks on what it says

**Sent** (`AskSnapshotBuilder`, `AskLucentoClient`), only while AI answers are on: the question; the last six turns of
the conversation; today's date; the main currency; and up to 400 labelled figures, each with an id. The figures are net
worth, assets and amounts owed, each wallet's balance (main currency), income, expenses and net flow for each of the last
12 months, spending and income by category for this month and last, this month's budget by category, card amounts due
and rates, and what's still scheduled this month.

**Never sent**: transactions, notes, payees, receipts, photos, goal details, other currencies' wallets, the Gemini key.

**Safeguards**

- **Figures come from the phone.** The model must quote figures as `[[id]]`. `AnswerCheck` replaces each id with the
  phone's value (shown in bold), and replaces an unknown id with "—". If the model writes an amount itself, the answer
  carries a warning that it couldn't be checked.
- **Reports are rebuilt on the phone.** The model can only name a report kind and a month. The app checks both against
  an allow-list (no future months, nothing more than 24 months back) and builds the report itself.
- **Read-only.** Nothing in an answer can change data, open a link, or run anything. Links and markdown are stripped,
  answers are capped at 1,500 characters, and there are at most three follow-ups of up to 90 characters each.
- **Scope.** The system prompt limits answers to personal finance. It forbids personalised investment, tax, legal or
  credit advice and product recommendations, and says "not financial advice" when advice is asked for. It treats wallet
  and category names as data, not instructions.
- **Consent.** Off by default. It is turned on only from the consent sheet in Ask Lucento or the dialog in Settings ›
  Privacy, both of which say what is sent and what never is. It is never restored from a backup, and it is cleared when
  the account is reset.
- **Limits.** Each question counts against the same per-user daily allowance as Smarter reading
  (`public.consume_ai_quota`, `AI_DAILY_LIMIT`, default 50). There is one request at a time (an in-flight guard), with a
  30-second timeout.
- **No history kept.** The conversation lives in memory only (at most 60 messages). The proxy stores nothing and logs
  only status codes.

## Pieces

| Piece | Where |
|---|---|
| Hero on Insights | `feature/ask/AskLucentoHero.kt` (types "What’s my top 5 spending categories?"; still with reduced motion) |
| Screen, report sheet, consent sheet | `feature/ask/AskLucentoScreen.kt` |
| Conversation, routing, fallbacks | `feature/ask/AskLucentoViewModel.kt` |
| Reports, routing, snapshot, answer check | `domain/ask/Reports.kt`, `domain/ask/Ask.kt` (unit-tested) |
| Client | `data/ai/AskLucentoClient.kt` → `BuildConfig.AI_ASK_URL` (the Smarter reading URL with `/interpret` → `/ask`; debug builds only with a test proxy) |
| PDF | `data/reports/ReportPdfWriter.kt` |
| Consent | `UserPreferences.askLucentoAi` (`ask_lucento_ai`); Settings › Privacy › Ask Lucento |
| Proxy | `lucento-website/src/pages/api/ask.ts` (branch `feature/ask-lucento`) |

## Setting it up (owner)

1. The proxy uses the same environment as `/api/interpret` (`GEMINI_API_KEY`, `SUPABASE_URL` or
   `LUCENTO_SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY` or `LUCENTO_SUPABASE_PUBLISHABLE_KEY`, optional `AI_DAILY_LIMIT` /
   `GEMINI_MODEL`); see `CAPTURE.md` "Setting it up". There is nothing new to configure (Vercel production already has
   `GEMINI_API_KEY` and the `LUCENTO_`-prefixed Supabase names, checked 2026-11-03).
2. Merge `lucento-website` branch `feature/ask-lucento` and deploy. Check it with
   `curl -i -X POST -H "Content-Type: application/json" -d "{}" https://www.lucentoapp.com/api/ask`, which should answer
   `401 sign_in_required`.
3. The quota migration `20261027090000_ai_usage_quota.sql` must already be applied (Smarter reading needs it too).
4. In the app (a release build — debug builds have no proxy): sign in to a sync account, open Insights › Ask Lucento,
   ask a question of your own, and accept the consent sheet. Until release signing exists, a test-only release can be
   built locally with the debug key (`LUCENTO_KEYSTORE_FILE=~/.android/debug.keystore`, password `android`, alias
   `androiddebugkey`, then `./gradlew assembleRelease`); sign in with an email account, as Google sign-in needs the
   signing key registered for `com.lucento.app`.
