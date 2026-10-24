# Adding transactions — the composer

Lucento has one way to add transactions: **describe what happened**. Type, speak or scan a receipt, and every
transaction in the message appears at once as an editable row. Fix anything that looks wrong in place and save the
batch with one tap. The old Add sheet (Manual · Scan · Speak · Type it), `VoiceEntryScreen` and `ReceiptScanScreen`
are gone. The editor sheet still opens for **editing** existing transactions and the transfer sheet for wallet
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
  that currency; otherwise the account last used for income/expense.
- **Receipts**: one expense for the total paid (never one transaction per item). A labelled total is pre-filled; an
  unlabelled one is offered as choices; items that don't add up and missing/unclear dates are flagged.

## Review and save

- Blocking (row stays until fixed): missing/invalid amount, too many decimals for the account's currency, no account,
  a mentioned currency the account doesn't use (until the user changes the account or confirms the amount), transfer
  without two distinct same-currency accounts, a category that no longer fits.
- Hints (row can be saved): no category, receipt date to check, items vs total, "a matching transaction is already
  saved for this day".
- The save button says exactly what happens: "Save 4 transactions", or "Save 3 ready" with "The other one stays here
  until it's fixed". Saving is all-or-nothing for those rows; each row's key is its idempotency key, so a retry or a
  double tap never duplicates. A failure says "Nothing was saved".
- "Edit message" puts the text back in the input; sending it again re-reads it but keeps every field the user changed.
- Leaving with unsaved rows asks before discarding.

## Speech and camera

Speech uses the system recogniser through `RecognizerIntent` (offline preferred). Lucento itself holds no microphone
or camera permission: the recogniser and camera apps ask for their own. Cancel, denial and "no recogniser" all leave
a one-line note and the keyboard. Photos are read with ML Kit's bundled on-device model and deleted afterwards. Speech
is auto-sent; the user can fix the wording with "Edit message" or fix rows directly.

## AI provider — what exists and what activation would need

There is **no AI provider** in Lucento and nothing in the UI claims one. Capture never touches the network: the only
network use is opt-in cloud sync (docs/SYNC.md), which never sends text to a model. `LocalTransactionInterpreter`
is deterministic rule-based parsing.

A remote model can be added later behind `TransactionInterpreter` without touching the composer, but only with:

1. A server you operate (a small proxy) that holds the model API key — never the APK, `BuildConfig` or resources.
2. A new opt-in module in `FeatureSet` ("Smarter reading, sends text to …"), off by default, with a consent screen that
   names what is sent (the message text or OCR text, plus category names) and where.
3. Network calls only while that module is on (INTERNET now exists for cloud sync), and a per-request fallback to the
   local interpreter when offline,
   declined or failing.
4. The model returning the `TransactionCandidate` shape (structured JSON), validated by the same `BatchReview` rules;
   its rows labelled with `InterpreterEngine.label` so the user knows what read them.
