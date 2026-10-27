# Adding transactions — the composer

Lucento has one way to add transactions: **describe what happened**. Type, speak or scan a receipt, and every
transaction in the message appears at once as an editable row. Fix anything that looks wrong in place and save the
batch with one tap. The old Add sheet (Manual · Scan · Speak · Type it), `VoiceEntryScreen` and `ReceiptScanScreen`
are gone. The full-page editor still opens for **editing** existing transactions and the transfer sheet for wallet
transfers.

```
type / speak / scan ──► TransactionInterpreter ──► ReviewRow[] ──► BatchReview.check ──► saveBatch (atomic)
   (feature/capture)        (domain/capture)        (editable)        (blocking vs hint)    (data/repository)
```

## Pieces

| File | Role |
|---|---|
| `domain/capture/TransactionInterpreter.kt` | The boundary: `interpretText`, `interpretReceipt` → `TransactionCandidate`s with `CandidateFlag`s. `LocalTransactionInterpreter` is the shipped implementation. |
| `domain/capture/BatchEntryParser.kt` | Splits one message into clauses and reads each one (amount, type, account, category, date, description). |
| `domain/capture/NumberWords.kt` | "two thousand five hundred" → 2500 for spoken amounts. |
| `domain/capture/QuickEntryParser.kt` | Single-clause rules (dates incl. "October 12", transfer roles, account mentions, category keywords). |
| `domain/capture/ReceiptCandidates.kt`, `ReceiptParser.kt` | Receipt text → one expense; `checkItems` compares items with the total. |
| `domain/capture/BatchReview.kt` | Editable `ReviewRow`, edits (`RowEdit`), validation (`RowStatus`), totals, carry-over of corrections, "already saved?" hint. |
| `data/repository/TransactionRepository.saveBatch` | Validates every entry with the normal rules, then writes them all in one DB transaction. |
| `feature/capture/ComposerViewModel.kt`, `TransactionComposerScreen.kt`, `ComposerRows.kt` | State and UI. |

## Interpretation rules (on device)

- **Clauses**: line breaks, sentence ends, `;`, list commas (never inside `1,200` or `October 12, 2026`) and the words
  *and, then, plus, also*. A clause with no amount joins its neighbour ("fish and chips 300"), unless it states a type
  and names a purpose ("…and paid the electric bill") — then it is its own row asking for an amount.
- **Unpunctuated lists**: "lunch 250 coffee 180" and "spent 250 on lunch 180 on coffee" split by word order.
- **Amount**: prices beat quantities ("2 shirts 1500" → 1500). Competing prices are offered as one-tap choices and
  nothing is pre-filled. Number words are converted; currencies are never converted.
- **Type**: what the words say (spent/paid/bought → expense; received/salary/refund → income; transfer/withdrew/
  "paid my card" → transfer). Otherwise the matched category's kind, then the previous clause, then the type hint
  (e.g. opened from a wallet's "Income"), then expense. Transfers stay transfers (two linked legs, never spending).
- **Category**: the user's own category names first, then keywords per icon key, specific before general ("coffee" →
  "Cafes and coffee" if present, else "Restaurants"/"Dining"). No match → the row says so; it can still be saved
  uncategorized. Categories are never created silently.
- **Date**: today, yesterday, N days ago, weekdays, "October 12", "12 Oct 2026". One date in a message applies to all
  its rows; several dates carry forward. Default is today.
- **Account**: a named account or a type word that identifies one account; a named currency picks the only account in
  that currency; otherwise the composer's **active wallet** (below). Rows remember whether the words named the account
  (`walletFromText`).
- **Transfers**: "transfer/moved X from A to B", "withdrew 2000" (bank → cash), "sent 5000 to Savings" (only when the
  destination is one of the user's accounts; "sent 500 to mom" stays an expense). With one account named after "top up",
  "cash in", "paid my credit card" or "paid off my loan", that account is the destination and the source is the active
  wallet. A fee clause right after a transfer ("…, fee 15") becomes a "Transfer fee" expense on the transfer's source.
  Transfers need the Multiple wallets module and two same-currency accounts; amounts are never converted.
- **Receipts**: one expense for the total paid (never one transaction per item). A labelled total is pre-filled; an
  unlabelled one is offered as choices; items that don't add up and missing/unclear dates are flagged.

## Active wallet

- One wallet can be the **default** ("Default wallet" switch in the wallet editor; cash, bank and e-wallets only). It is a
  device preference (`UserPreferences.defaultWalletId`), not a database column; a stale id is ignored.
- The active wallet is, in order: the pill the user tapped, the wallet the composer was opened from, the default, or the
  only account. With several accounts and none of these, the composer asks "Where should these go?" (with "Remember as
  my default"), and a message sent before answering waits for the answer.
- With two or more accounts a pinned **wallet bar** sits under the banner: one pill per account, the active one filled.
  Tapping another moves the unsaved rows that were only following it (`BatchReview.retarget`); rows whose words named an
  account, or whose account the user picked, stay. A trailing **Transfer** pill adds a transfer row from the active
  wallet. A row shows its own account chip only when it differs from the active wallet; transfer rows read "A → B".

## Review and save

- Blocking (outlined in red; the batch can't be saved until it is fixed or removed): missing/invalid amount, too many decimals for the account's currency, no account,
  a mentioned currency the account doesn't use (until the user changes the account or confirms the amount), transfer
  without two distinct same-currency accounts, a category that no longer fits.
- Hints (row can be saved): no category, receipt date to check, items vs total, "a matching transaction is already
  saved for this day".
- The save button says exactly what happens: "Save 4 transactions", or — disabled — "Fix 1 highlighted to save"
  (`BatchSummary.canSave`). The whole batch saves at once, all-or-nothing; each row's key is its idempotency key, so a
  retry or a double tap never duplicates. A failure says "Nothing was saved". After a save the app opens Transactions.
- Layout: a pinned banner (Lucento + "Describe what happened"), the user's messages as brand-blue bubbles on the right,
  Lucento's replies on the left with its avatar, and each proposed transaction as its own three-row card under the reply (small date; description and amount; type and category, or From and To, sharing the width).
- "Edit message" puts the text back in the input; sending it again re-reads it but keeps every field the user changed.
- Leaving with unsaved rows asks before discarding.

## Speech and camera

Speech uses the system recogniser through `RecognizerIntent` (offline preferred). Lucento itself holds no microphone
or camera permission: the recogniser and camera apps ask for their own. Cancel, denial and "no recogniser" all leave
a one-line note and the keyboard. Photos are read with ML Kit's bundled on-device model and deleted afterwards. Speech
is auto-sent; the user can fix the wording with "Edit message" or fix rows directly.

## Smarter reading (Gemini) — opt-in

Off by default. When the user turns on **Settings › Smarter reading** (a dialog first says what is sent and where), typed
and spoken messages go to Gemini through Lucento's own proxy; everything else — and everything while it is off — is
read on this phone by `LocalTransactionInterpreter` (deterministic rules; never presented as AI).

| Piece | Where |
|---|---|
| Opt-in flag | `UserPreferences.smartReading` (DataStore), set only from the consent dialog |
| Composer reader | `data/ai/HybridTransactionInterpreter`: Gemini when on, else local; falls back to local per request (offline, signed out, daily limit used, error, nothing found) and labels the reply "Read by Gemini" / "Read on this phone — <reason>" |
| Client | `data/ai/RemoteTransactionInterpreter` → `BuildConfig.AI_INTERPRET_URL` (default `https://www.lucentoapp.com/api/interpret`, override with `LUCENTO_AI_INTERPRET_URL`) with the user's Supabase access token |
| Validation | `RemoteCandidates`: ids must be the user's own, amounts positive decimals, kinds must fit; doubts become review flags. Rows still go through `BatchReview` and the user's review |
| Proxy | `lucento-website/api/interpret.ts` (Vercel). Holds `GEMINI_API_KEY`; checks the token with Supabase Auth; counts the request with `public.consume_ai_quota()`; returns structured rows |
| Daily limit | `supabase/migrations/20261027090000_ai_usage_quota.sql` (`public.ai_usage`, RLS read-own, no write grants) + `AI_DAILY_LIMIT` on Vercel (default 50/user/UTC day) |

**Sent:** the message text, today's date, the main currency, category names/kinds and wallet names/types with their
local ids. **Never sent:** balances, history, receipts (always read on this phone), the Gemini key (only on Vercel).
A sync account is required: it identifies the user for the daily allowance.

### Setting it up

1. **Gemini key.** Open https://aistudio.google.com/apikey, sign in, *Create API key* (pick or create a Google Cloud
   project). For real users, turn on billing for that project (paid tier): on the free tier Google may use prompts to
   improve its products, and free-tier rate limits are low. Optionally set a budget alert in Google Cloud Billing.
2. **Database.** Apply `supabase/migrations/20261027090000_ai_usage_quota.sql` to the Supabase project.
3. **Vercel.** In the `lucento-website` project → Settings → Environment Variables (Production), add
   `GEMINI_API_KEY` (mark *Sensitive*), `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, and optionally `AI_DAILY_LIMIT`
   and `GEMINI_MODEL` (see `lucento-website/.env.example`). Redeploy so the function picks them up.
4. **Check.** `curl -i -X POST -H "Content-Type: application/json" -d "{}" https://www.lucentoapp.com/api/interpret`
   (in PowerShell: `curl.exe`) answers `401 sign_in_required` once deployed; `503 not_configured` means a variable is
   missing. Without the JSON content type Astro's form protection answers `403` instead.
5. **App.** Sign in to a sync account, then Settings › Smarter reading › on. The composer's replies say who read them.
