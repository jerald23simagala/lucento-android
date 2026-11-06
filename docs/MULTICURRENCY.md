# Multicurrency — wallets in other currencies, totals in one

Status, 2026-11-06: stage 1 built on branch `feature/multicurrency` (from `feature/lucento-shared`), with the owner's
first test round addressed. App database v13; server migration `supabase/migrations/20261106090000_multicurrency.sql`
(applied to `lucento-test` — NEXT_TASKS OT-10; production waits on approval — OT-12). Definitions:
docs/CALCULATIONS.md "Exchange rates".

**Showing amounts.** Every amount in another currency shows its main-currency value under it in small type ("≈ ₱…":
wallet rows, card faces, transaction rows and details), from one app-wide book (`FxDisplay`, the same average method as
the totals). Wallets' Assets and Liabilities subtotals are a single main-currency figure; only a wallet with no rate yet
stays in its own currency beside it.

## What the owner asked for (2026-11-05) and the decisions

- Wallets may use other currencies; they work like any other wallet, and every total is in the **main currency**.
- The main currency is chosen once (onboarding) and can't be changed afterwards.
- Each movement in another currency is converted at its own rate; the wallet uses the **average method**. What leaves
  goes at the average; the difference from its own rate is a **realized FX gain or loss**.
- Rates come from a **free service**. Offline (or with online rates off), the person chooses the wallet's **last rate**
  (a gain or loss counts against the average) or its **average rate** (no gain or loss).
- FX results are **dynamic: gains are income, losses are expenses**.
- Month-end revaluation (unrealized gains and losses) — **stage 2**, not built yet.

Recommendations the owner accepted (2026-11-06):

1. FX results are calculated, not stored rows: they always match the history and update when it changes.
2. Foreign spending counts toward the main-currency budget at its own value; FX losses never count (not spending).
3. Average method for money-holding wallets. *Refined while building:* cards and loans use it too — charges add at
   their own rate, payments leave at the average with the difference realized — otherwise a paid-off foreign card
   could keep a leftover amount in net worth.
4. Wallets from before v13 start from the rate they're given (no gains for older history).
5. An offline value is kept as chosen; nothing is silently replaced later.

## How it works

**Rates.** `FxRate` is an exact decimal (12 significant digits); conversions round once, half-even. Sources, in order
(`OnlineFxRates`): fawazahmed0's currency-api on jsDelivr (daily, 200+ currencies incl. AED, SAR, KWD, QAR; history by
date), its Cloudflare mirror, then Frankfurter (the European Central Bank's reference rates). Rates are cached on the
phone per pair and day (`DataStoreFxRateCache`, never synced or backed up).

**Privacy.** Settings › Money › "Get exchange rates online" is on by default (owner decision, 2026-11-06) and can be
turned off. A rate is fetched only when something is recorded in another currency — someone who only uses the main
currency never makes a request. Each request names two currency codes and a date, never an amount, and nothing else
leaves the phone. Offline or off, rates are typed or taken from the wallet's own history.

**Saving a movement.** The composer values every row in another currency (`FxQuotes.quote`): the day's market rate if
it could be fetched, otherwise the batch's offline choice (shown above the save bar only when needed). Each row shows
"≈ ₱6,369.38 · 1 USD = 63.69 PHP, today's rate". The full editor keeps a movement's rate when it's edited.

**Transfers between currencies.** Any wallet can receive a transfer. Between two currencies the sheet asks "They
receive" (suggested from today's rate when online, shown as the rate the two amounts imply); the two real amounts are
the rate. The main-currency side is the value; between two foreign wallets both legs carry the source's value.

**Wallets.** A new wallet in another currency needs its rate (filled in online when allowed). A wallet from before v13
shows "Not in your ₱ totals yet" with **Set rate** on its page until it has one; meanwhile it stays in its own currency.
A foreign wallet's page shows what it is worth in the main currency, its average rate, and its FX gains or losses so far.

**Totals.** `FxTranslation.translate` re-expresses valued foreign wallets (at their carrying value) and their movements
(at their own value) in the main currency for Home, Wallets, Budget, the Transactions totals, Insights and Ask Lucento.
Lists of movements keep each one's own currency. FX results join Insights and reports as "FX gains" / "FX losses".

## Data

| Where | Added | Notes |
|---|---|---|
| Room v13 (`MIGRATION_12_13`) | `transactions.base_amount_minor`, `base_currency`, `fx_source`; `wallets.fx_basis_rate` (text), `fx_revalue` | All null / false: existing rows follow their wallet's average, nothing is re-uploaded |
| Supabase (`…_multicurrency.sql`) | the same columns with checks (all three or none; never in the row's own currency; a known source; a plain decimal rate); `ledger_settings (user_id, main_currency)` | Additive; checks NOT VALID and live rows only; `ledger_settings` readable by the account and its Partner, insert-only, no update or delete |
| Sync | the FX keys travel only in batches that hold a movement or wallet in another currency (nulls included there) | A one-currency ledger sends exactly what v12 sent, so it keeps syncing with a project without the migration |
| Main currency | set on the server by the ledger's first phone; every other phone (reinstall, second phone, Partner) takes it | `SyncRepository.settleMainCurrency`; never fails a sync |
| Backup | format v4: the same fields; v3 files still import | An older app refuses a v4 file as "from a newer Lucento" |

Verified: `tools/sql-verify` — `multicurrency_suite.sql` 17/17 and the Lucento Shared suite 69/69 on Postgres 17
(PGlite), under lucento-test's and Supabase's stock default privileges. It caught one bug before shipping: a value
missing its currency passed the check (a CHECK passes on NULL), now explicit.

## Rollout

1. Apply `20261106090000_multicurrency.sql` to `lucento-test` (after the two Lucento Shared migrations) — OT-10.
2. Try it on a phone (OT-11): a USD wallet, spending online and offline, a pesos → dollars transfer, Insights.
3. Production only with the owner's approval, before a release that includes multicurrency.

A ledger with a wallet in another currency needs the migration on its project before a v13 phone syncs it (its
upload would be refused and stay pending, nothing lost); a one-currency ledger doesn't.

## Not built yet (stage 2 and known gaps)

- Month-end revaluation and unrealized gains/losses (`fx_revalue` is stored and synced; no screen uses it yet).
- The composer can't transfer between currencies (the transfer sheet can).
- Scheduled payments, debt payments and balance adjustments in a foreign wallet follow its average (no market rate).
- Editing a transfer between two foreign wallets values it afresh rather than keeping its old value.
- Offline-valued movements aren't offered "use that day's rate" once back online.
- Ask Lucento's AI and the PDF reports read main-currency figures but don't explain FX yet.
