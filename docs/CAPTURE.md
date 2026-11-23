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
| `domain/capture/StatementParser.kt`, `StatementCandidates.kt` | Tells a statement from a receipt; a statement's lines → one row each (see "Scanning"). |
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
  "Cafes and coffee" if present, else "Restaurants"/"Dining"). Then, as for scans (owner, 2026-11-23: Gemini is for Ask
  Lucento, so the phone should know what scans know), the business the row names: the person's usual category for it,
  learned from their own recent entries (`MerchantMemory`: chosen at least twice and for most of them), else the
  merchant dictionary's ("Jollibee 285" → dining, "Meralco" → utilities; `MerchantRecognizer`, `CategoryIntents`; never
  for income). No match → the row asks for one ("Choose a category"):
  every income and expense needs a category before the batch can be saved (owner, 2026-11-04); transfers have none.
  Categories are never created silently.
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
- **Scans**: a receipt is one expense for the total (never one transaction per item); a statement or an app's
  transaction history is one row per transaction. See "Scanning" below.

## Active wallet

- One wallet can be the **default** ("Default wallet" switch in the wallet editor; cash, bank, e-wallets and credit cards,
  `WalletType.canBeDefault`). It is a device preference (`UserPreferences.defaultWalletId`), not a database column; a
  stale id is ignored. A **credit card default takes expenses only** (tester round, 2026-11-18, owner's choice): while
  the active wallet is that default and the person hasn't picked it, income, transfers and a new transfer's "from" go to
  the first cash, bank or e-wallet (`InterpretationContext.moneyWalletId` / `walletFor`). A card picked in the wallet bar,
  or the wallet the composer was opened from, takes every type.
- The active wallet is, in order: the pill the user tapped, the wallet the composer was opened from, the default, or the
  only account. With several accounts and none of these, the composer asks "Select wallet to use" (with "Remember as
  my default"), and a message sent before answering waits for the answer.
- With two or more accounts a pinned **wallet bar** sits under the banner: one pill per account, the active one filled.
  Tapping another moves the unsaved rows that were only following it (`BatchReview.retarget`); rows whose words named an
  account, or whose account the user picked, stay. A trailing **Transfer** pill adds a transfer row from the active
  wallet. A row shows its own account chip only when it differs from the active wallet; transfer rows read "A → B".

## Review and save

- Blocking (outlined in red; the batch can't be saved until it is fixed or removed): missing/invalid amount, too many decimals for the account's currency, no account,
  a mentioned currency the account doesn't use (until the user changes the account or confirms the amount), transfer
  without two distinct same-currency accounts, no category on an income or expense, a category that no longer fits.
- Hints (row can be saved): receipt date to check, items vs total, "a matching transaction is already
  saved for this day".
- The save button says exactly what happens: "Save 4 transactions", or — disabled — "Fix 1 highlighted to save"
  (`BatchSummary.canSave`). The whole batch saves at once, all-or-nothing; each row's key is its idempotency key, so a
  retry or a double tap never duplicates. A failure says "Nothing was saved". After a save the app opens Transactions.
- Layout: a pinned banner (Lucento + "Describe what happened"), the user's messages as brand-blue bubbles on the right,
  Lucento's replies on the left with its avatar, and each proposed transaction as its own three-row card under the reply (small date; description and amount; type and category, or From and To, sharing the width).
- "Edit message" puts the text back in the input; sending it again re-reads it but keeps every field the user changed.
- Leaving with unsaved rows asks before discarding.

## Scanning: receipts, bills, statements and PDFs

Owner, 2026-11-04: a scan tells a receipt (many lines, one payment) from a statement or a photo of a transaction list
(many payments). Owner, 2026-11-22: Lucento Scan reads any financial document, from the Philippines, the United States
or anywhere, checks statements against the ledger and knows merchants. Everything is read on this phone
(`LocalTransactionInterpreter.interpretDocument`), never presented as AI. The full rules are in **docs/SCAN.md**.

1. **Inputs** — the scan button's menu: **Scan pages** (ML Kit Document Scanner: several pages, flattened; the camera
   opens instead where it can't run), **Take a photo**, **Choose images** (up to 10, read as one scan), **Import a PDF**
   (its text layer, else OCR of each page; a locked PDF asks for its password once and never keeps it). The composer's
   Scan widget opens Scan pages.
2. **Pages → documents** (`DocumentAssembler`): receipts photographed together are split; a statement's pages join, with
   lines repeated by overlapping photos read once; a long receipt over two photos is one payment; sideways photos are
   turned upright and tilted ones straightened.
3. **A receipt** becomes one row for its total (labels in 20 languages, tips, refunds as income), dated by its bill or
   transaction date, in the document's currency, for the business paid — never the payment provider — in the account
   that paid when the person has it (their GCash wallet, their Amex card), with the person's usual category for that
   merchant when they have one.
4. **A statement** becomes one row per transaction in the account it belongs to (the wallet at the bank named in its
   heading, else the active one), direction from the running balance, columns, signs, sections or words; card refunds
   are income, card payments transfers, ATM withdrawals transfers to cash; merchants named ("Starbucks").
5. **Checked against the ledger**: lines already saved are held back and listed with **Add anyway**; near matches and
   lines printed twice are marked; the reply shows the statement's balance next to Lucento's, fees, a card's minimum due
   and limits, and saved transactions missing from the statement. Nothing saved is changed.
6. **The reply says what it read**: "Read as one receipt: one payment for the “Amount due” line, not each item.",
   "Read as a statement: 7 transactions, Oct 3 – Oct 31, each its own row. Money in and out matches the running
   balance.", "Read 2 receipts: each payment is its own row." Rows carry hints for what needs a look ("Check the
   currency", "Pending at the bank", "Possibly saved already …").

Definitions: docs/CALCULATIONS.md "Statement scans", "Receipt amounts" and "Statement check". Tests: the capture suites
in `app/src/test/.../domain/capture` (incl. `ScanCorpusBenchmarkTest` on the independent corpus) and, on a device,
`ScanOcrTest` and `DocumentIntelligenceDeviceTest` (real OCR on made-up images and a generated PDF).

## Speech and camera

Speech asks for English in the phone's own English region (or `en-US`; `SpeechRequest.languageTag`). Lucento holds no
camera permission (the camera is another app, and the Document Scanner uses Google Play services' own). Cancel, denial and "no recogniser" all leave a one-line note and the
keyboard.

**Lucento listens itself (owner 2026-11-13).** Google's voice window stopped listening before people had started
speaking, and the hints that ask a recogniser to wait longer (`EXTRA_SPEECH_INPUT_*_SILENCE_LENGTH_MILLIS`) are ignored
by many speech services. So the composer runs its own session on the phone's speech service (`SpeechRecognizer`):

| Piece | What it does |
|---|---|
| `VoiceSession` (pure, `VoiceSessionTest`) | Waits up to 10 s for the first words, listening again each time the service gives up early (some do within a fraction of a second). After words, listens again through pauses; 3.5 s of quiet ends it. Done ends it at once (mid-sentence, the service gets 2.5 s to hand back its last words). One minute and 30 restarts at most. Stretches are joined with ", " so the interpreter reads them as items. An error after words never loses them. |
| `VoiceCapture` | Drives `SpeechRecognizer` on the main thread: partial results shown live, a fresh recogniser per stretch (late callbacks from an old one are ignored), the permission checked before every stretch, audio buffers ignored. |
| `ListeningSheet` | "Listening…", the microphone's halo moving with the voice (still with reduced motion), the words so far, something to try saying (the screen's own example), whether it is offline or online, Done and Cancel. Dismissing is Cancel. |
| `VoiceInput` (`rememberVoiceInput`, `VoiceInputSheets`) | The whole microphone for a screen: the route (`SpeechRequest.route`: Lucento listens; no speech service → its window; microphone not yet allowed → ask, refused → window), the window's answer, the notes, "Try online voice" and its consent, the sheet, and stopping when the app is left. Used by the composer and by Ask Lucento (owner, 2026-11-15; docs/ASK.md "Speaking a question"). |

`RECORD_AUDIO` is asked the first time someone taps the microphone (also from the Speak widget) and is in the CI
release allow-list. The manifest declares `android.hardware.microphone` as not required, because the permission alone
would make Google Play hide Lucento from devices without a microphone (found in the 0.5.8 AAB, 2026-11-14). Refused, or a
phone with no `RecognitionService` (`SpeechRecognizer.isRecognitionAvailable`, which
needs the `<queries>` entry), the speech service's own window (`RecognizerIntent`) is used as before. Leaving the app
mid-sentence stops listening and puts what was heard into the message field, unsent, after anything already typed
there (`SpeechRequest.intoField`; until 2026-11-15 the words were dropped when the field wasn't empty). Not yet covered
by a device test: check on real phones that restarts don't clip first words or beep on every stretch. The "Voice entry"
feature switch shows or hides the microphone in both the composer and Ask Lucento.

**Online voice (opt-in, owner 2026-11-13).** By default both paths set `EXTRA_PREFER_OFFLINE`, which means offline
*only*: a phone without a matching offline speech pack can't listen (Google's window says "Voice search isn't
available"; found on a real phone 2026-11-13, and many phones no longer offer the download). When an offline attempt
fails, the note offers **Try online voice**; agreeing to `SpeechRequest.CONSENT_TEXT` saves
`UserPreferences.onlineVoice` and listens again without the offline flag, so the speech service (Google's on most
phones) may send the recording to Google. Lucento only receives the text. Settings › Privacy › Online voice shows the
same consent and turns it off. A device choice like crash reports: kept across sign-outs, never in backups. Both are in
the Privacy Policy ("Voice input").

Photos are read with ML Kit's bundled on-device model and deleted afterwards. Speech
is auto-sent; the user can fix the wording with "Edit message" or fix rows directly.

## Smarter reading (Gemini) — opt-in

Off by default, and turned on in one place only: **Settings › Privacy & security › Smarter reading** (shown when signed
in, in a build with the proxy). Its consent dialog says first that adding transactions gets slower — each message waits
a few seconds for Gemini's answer — then what is sent and where (`feature/capture/SmartReadingCopy.kt`); the switch's
row says the same under it. Then typed and spoken messages go to Gemini through Lucento's own proxy; everything else —
and everything while it is off — is read on this phone by `LocalTransactionInterpreter` (deterministic rules; never
presented as AI).

**No switch in the composer (owner, 2026-11-15).** On-device reading answers at once; Gemini made every message wait,
which new users read as the app being slow. The composer's own switch (added in owner test 2026-11-09) is gone. While
Smarter reading is on, the composer says so: the note under its examples ("Smarter reading is on: Gemini reads each
message, which takes a few seconds. You can turn it off in Settings › Privacy & security."; off: "Your messages are read
on this phone, so their rows appear straight away.") and the reply while waiting ("Gemini is reading…").

**Plan (owner, 2026-11-15).** Smarter reading is part of Lucento Pro (`Capability.AI_PRO`: Pro, or Lifetime's included
year; docs/MONETIZATION.md). Without it the proxy refuses every message (`403 pro_required`), so the app doesn't ask:
`ProGates.smartReadingAllowed` gates the interpreter (`LucentoApplication.interpreter`) and the composer's
`geminiReads`, and messages are read on this phone at once. In Settings the switch can't be turned on and a Pro note says
why ("Smarter reading is part of Lucento Pro. Reading on this phone is always free."). A consent given earlier (a
Lifetime's year ended, a subscription lapsed) is paused, not removed: the note says so, the switch can still be turned
off, and it works again when the plan includes it, as cloud sync does.

| Piece | Where |
|---|---|
| Opt-in flag | `UserPreferences.smartReading` (DataStore), set only from the consent dialog |
| Composer reader | `data/ai/HybridTransactionInterpreter`: Gemini when on and in the plan, else local; falls back to local per request (offline, signed out, daily limit used, error, nothing found) and labels the reply "Read by Gemini" / "Read on this phone — <reason>" |
| Client | `data/ai/RemoteTransactionInterpreter` → `BuildConfig.AI_INTERPRET_URL` (release: default `https://www.lucentoapp.com/api/interpret`, override with `LUCENTO_AI_INTERPRET_URL`; debug: only `LUCENTO_DEBUG_AI_INTERPRET_URL`, otherwise Smarter reading is hidden — debug never calls the production proxy) with the user's Supabase access token |
| Validation | `RemoteCandidates`: ids must be the user's own, amounts positive decimals, kinds must fit; doubts become review flags. Rows still go through `BatchReview` and the user's review |
| Proxy | `lucento-website/api/interpret.ts` (Vercel). Holds `GEMINI_API_KEY`; checks the token with Supabase Auth; counts the request with `public.consume_ai_quota()`; returns structured rows |
| Daily limit | `supabase/migrations/20261027090000_ai_usage_quota.sql` (`public.ai_usage`, RLS read-own, no write grants) + `AI_DAILY_LIMIT` on Vercel (default 50/user/UTC day) |

**Sent:** the message text, today's date, the main currency, category names/kinds and wallet names/types with their
local ids. **Never sent:** balances, history, scans (receipts and statements are always read on this phone), the Gemini
key (only on Vercel).
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
5. **App.** Sign in to a sync account, then Settings › Privacy & security › Smarter reading › on. The composer's
   replies say who read them.
