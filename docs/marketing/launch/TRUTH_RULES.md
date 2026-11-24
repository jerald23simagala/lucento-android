# Truth and policy rules for every Lucento post

Every caption, video, image, reply, pitch and listing passes this check before it reaches the owner's Launch Board. A
post that fails any rule is fixed or dropped, never "approved with a note". Rules come from the code-verified feature
inventory (`docs/marketing/LAUNCH_VIDEO_FEATURE_DISCOVERY.md` §3 and §5), the Play listing rules
(`docs/play/PLAY_STORE_SUBMISSION.md` §4), the Privacy Policy (`feature/legal/LegalDocuments.kt`, published at
/privacy) and owner decisions. When the app changes, re-check the inventory before claiming a feature. The inventory
was last re-verified on 0.5.14; for what 0.5.15–0.5.19 added (Lucento Scan, the monthly check-in, Milestones, the
monthly AI allowance) read `README.md`, `docs/SCAN.md`, `docs/ASK.md` and the release rows in
`docs/handover/CURRENT_STATUS.md`.

**Checked against Lucento 0.5.19 (version code 24) on 2026-11-24.** IDs never change: a new rule takes the next number
and sits in the section it belongs to (T34–T40 were added on 2026-11-24).

On a Launch Board card, cite failures by ID (for example "T4, T11").

## Product truth

| ID | Rule | Say instead |
|---|---|---|
| T1 | Never call the composer, voice or scanning (Lucento Scan) "AI". They are read by Lucento's own rules on the phone; ML Kit only extracts text from images | "Type it like a text." "Read on your phone." |
| T2 | AI is only **Ask Lucento's AI answers** (on for a signed-in account, Free included; nothing is sent until the person confirms their first question) and **Smarter reading** (Pro, off until turned on in Settings). Never "AI is opt-in" or "off unless you turn it on" for Ask Lucento: that has been wrong since 0.5.12. Any post about them names Google's Gemini, says nothing is sent until the person says yes and that it can be turned off, and links /privacy | "Ask Lucento answers on your phone. AI answers by Google's Gemini are there when you sign in, and nothing is sent until you say yes." |
| T3 | No automation claims: nothing is auto-logged, auto-categorised without review, or paid automatically. Scheduled items are never posted by themselves | "Lucento fills in the details; you confirm." |
| T4 | No bank or e-wallet connection, SMS import or bank sync exists. The 409 bank and e-wallet faces are display only | "No bank login." |
| T5 | No push notifications, alerts or reminder pop-ups exist. "Never miss a bill" is allowed for Scheduled transactions (owner, 2026-11-14), but never with notification imagery | "See every bill coming." |
| T6 | Mark every Pro feature "Pro": multicurrency (a wallet in another currency), Lucento Shared (inviting), cloud sync and restore, money owed to you, things you own (property and valuables; investments are a money wallet), 6/12-month Insights, the Milestones year (T37), statements saved or shared as PDF (importing a PDF into Scan is free), 3 of the 5 themes (Grove, Mono, Aurora), Smarter reading, and going past Free's limits: more than 3 money wallets (cash, bank, e-wallet, savings and investments together), 2 cards or loans, 2 active goals (Pro: 20) or 5 AI questions a month (Pro: 45) | "(Pro)" in the caption and on screen |
| T7 | Use exact limits: 30 rows per message, 10 pages or images per scan, 20 pages per PDF, 200 lines per statement, 6 receipts per photo, one Partner in Shared, 82 preset categories, 409 providers in 48 countries, 10 awards | — |
| T8 | Typing and voice read English, plus everyday Filipino money words (sweldo, palengke, merienda, load, jeepney), and the app is in English. Never claim another language for them. Lucento Scan reads documents from the Philippines, the United States and 20 other countries; photos must be in Latin script (T35) | "It understands sweldo, palengke and load." |
| T9 | No undo of a saved, edited or deleted entry (the only Undo brings back a hidden check-in card), custom dashboard, custom category icons or colours, budget rollover, budget periods other than calendar months (no payday-to-payday budget), live asset valuations or family groups. Don't imply any of them | — |
| T34 | AI is counted per calendar month: **5 AI questions on Free, 45 on Pro** (and in Lifetime's included year). Never "unlimited AI" or a daily number, and never the old 10 a month or 50 a day. **Smarter reading** ("Read messages with Gemini" in Settings) is Pro, off until turned on in Settings (the composer has no switch), makes each message wait a few seconds for Gemini, spends the same monthly AI questions and never reads scans. Gemini is for Ask Lucento: never present Smarter reading as the way Lucento adds transactions | "5 AI questions a month free, 45 with Pro." "What you type is read on your phone, at once." |
| T35 | **Lucento Scan** reads receipts, bills, invoices and bank, card and e-wallet statements from the camera, photos or screenshots, or a PDF, on the phone, and it's free. Every row is reviewed before saving, statement lines already saved are held back, and nothing saved is changed: never "automatic", "upload your statement" or "imports from your bank". Never "learns you" or "gets smarter": the usual category comes from the person's own saved entries, and nothing else is kept. Japanese, Korean and Chinese photos aren't read (PDFs with a text layer are) | "Scan a receipt or a statement. It's read on your phone, and you check every row." |
| T36 | The **monthly check-in** is a card on Home from a month's last 3 days to the 7th of the next: plan the coming month (one-off amounts for that month only) and, from the 1st, see the month that ended. It is never a notification, reminder or alert; people see it when they open Lucento (T5). Never "Lucento reminds you to budget" | "At the end of each month, Home asks you to plan the next one." |
| T37 | **Milestones** (called Progress before 0.5.19) shows the calendar budget year as 12 colour squares with the year's totals. That year view is **Pro**; Free sees its own months in grey with the results hidden. Each month's recap and the Awards (10, shown as medals) are free. Years and months are calendar ones (T9) | "Your budget year, month by month (Pro). Every month's recap is free." |

## Privacy truth

| ID | Rule | Say instead |
|---|---|---|
| T10 | Never "nothing leaves your phone", "100% private" or "your data never leaves", and never name one feature as the only exception (sync alone is wrong: once the person confirms, Ask Lucento's AI answers send each question with a summary of their finances, including the last 60 days of transactions; Smarter reading sends the message). Exchange rates (on by default), Google Play billing diagnostics, purchase checks, the scanner's Google diagnostics (T38) and every opt-in send something | "Stored on your phone. Cloud sync (Pro) is off until you turn it on, and nothing goes to Gemini until you say yes." "Works offline." |
| T11 | Any privacy line is scoped ("your entries", "your records") and, where the format allows, links www.lucentoapp.com/privacy | — |
| T12 | Voice: never "your voice never leaves the phone" (online voice is an opt-in) and never "never records" (Lucento listens through the microphone while its listening screen is open). The Privacy Policy says it "never keeps or sends a recording" | "Lucento keeps no recording of your voice." |
| T13 | Backups are not encrypted. Never imply that they are | "Back up to a file whenever you like." |
| T38 | Scans and imported PDFs are read on the phone and aren't uploaded, but ML Kit and Google's document scanner may send Google technical diagnostics (phone model, timings, error codes; never the images or the text), so "scans never touch the internet" is wrong. While the Privacy Policy says Lucento uses Gemini's free service, never say Google doesn't keep or read what Gemini receives | "Scans are read on your phone and aren't uploaded." |

## Money, pricing and availability

| ID | Rule |
|---|---|
| T14 | No "no ads", "ad-free" or "free forever" promises (ads may come to Free). Don't mention ads at all; if someone asks, the owner answers |
| T15 | No prices or trial lengths in content unless the owner confirms them as live in Google Play that week. "Free to download, with optional Pro" is always fine |
| T16 | Before P-day (production access granted and the release live), never say "available on Google Play" or "download now". "Coming soon to Google Play" is allowed (the website's status since 2026-11-08). After P-day, use the UTM-tagged Play link |
| T17 | No "best", "#1", "top-rated" or "the only" claims, and no comparisons that name a competitor |
| T18 | No savings or outcome promises ("save ₱5,000 a month with Lucento"). General money education only, never personal financial, tax or legal advice |
| T39 | When the owner confirms prices (T15), quote the audience's own country's price as Google Play shows it, never a conversion: Play has had regional prices since 2026-11-21 (Philippines ₱149 a month, ₱1,190 a year, Lifetime ₱2,490; United States $3.99, $29.99, Lifetime $59.99). **Lifetime** is Pro's on-phone features for good plus 12 months of the online services (cloud sync, Shared and the Pro AI allowance), then the optional Lucento Cloud add-on: never "everything forever" or "lifetime sync" |

## Visual truth

| ID | Rule |
|---|---|
| T19 | Real app screens only, captured from a real build (showcase or tour captures, or live adb stills). No mocked UI, no edited numbers, no state the app never shows (a sheet sliding over its own header), and no shortcut the app doesn't allow (Show amounts asks for the phone's unlock, so never show amounts returning on a plain tap, and never say "only you can": a phone with no screen lock gets a confirm dialog). |
| T20 | Demo data is demo data. The showcase world (₱258,108.10 net worth, budget at 89%) never appears as a real person's results, and no caption says "my finances" over it. An on-screen "Demo data" label is optional: the owner considers it implied (2026-11-15); the week 47 shorts carry one |
| T21 | No third-party brands in captions, hashtags, visuals or metadata: no bank, e-wallet, merchant or telco names or logos (GCash, Maya, Meralco, Jollibee, Grab…), even though the app understands them. Use the demo wallets ("Everyday bank", "Cash", "Savings") and generic words ("e-wallet", "electric bill") |
| T22 | The Rising Crest logo and mascot source files are never altered. The mascot's torch does not follow the budget, so never claim it does |
| T23 | Music is the original synthesized score (`lucento-studio/video/tools/compose_score.py`) or silence. No licensed or trending tracks added by us. The owner may add a sound inside TikTok only from its Commercial Music Library |
| T40 | Words match the build on screen, and anything that changed since a capture is captured again on 0.5.19 or later. Home's card says "Net worth" since 0.5.17 ("Net Wallet Balance" before, as in the launch film's 0.5.14 screens and the week 47 shorts' 0.5.8 ones); Milestones replaced Progress in 0.5.19; Safe to spend's sentence sits beside the amount since 0.5.18; the budget card has three parts since 0.5.14. Captures older than 0.5.15 can't show Lucento Scan, and older than 0.5.17 can't show the check-in or the month recap |

## Honest promotion

| ID | Rule |
|---|---|
| T24 | Never fake reviews, testimonials, quotes, user counts, comments or engagement. No buying followers, likes or reviews, and no engagement pods |
| T25 | Never offer anything for a rating or review, and never ask only happy users to rate (Google Play policy). "Tell us what you think on Google Play" to everyone is fine. Never ask testers from a testing service or exchange (Testers Community) to rate or review: they joined through a deal, so their reviews would count as incentivised |
| T26 | In every community post or reply, the owner says Lucento is their app ("I made this", "I'm the developer") and follows that community's rules. No posting the same text across groups the same day |
| T27 | Creators who get free Pro disclose it ("gifted", or TikTok's branded-content toggle) and are free to say what they really think. We never script praise |
| T28 | No unsolicited bulk messages. Every pitch is personal, sent by the owner from lucentoapp@gmail.com, one at a time, and a "no" or no reply after one follow-up ends it |
| T29 | No real person's money, names or faces without written permission. The owner's own screenshots use Hide amounts or the demo data |

## KlaroPH bridge (owner decisions 2026-11-14)

| ID | Rule |
|---|---|
| T30 | Klaro and Lucento are presented as siblings from the same maker: "KlaroPH on the web, Lucento on your phone." Never call Klaro worse, outdated or ending (it keeps running). Lucento's own channels mention Klaro only as "from the maker of KlaroPH" |
| T31 | The Klaro email goes only to Klaro users who haven't unsubscribed (`marketing_emails_unsubscribed_at IS NULL`), is sent as KlaroPH, carries Klaro's unsubscribe link and is sent once. The owner approves the exact text |
| T32 | Klaro users' emails and data never enter Lucento's systems (Supabase, Resend audiences, lists, analytics). Lucento's privacy policy doesn't cover them |
| T33 | Lucento and Klaro share one Resend free tier (100 emails a day). Klaro's email starts on P-day + 1, at most 50 a day, so Lucento's own sign-up emails always have room |

## Check before it reaches the board

1. Read the caption aloud: is every claim in the rules above, or plainly true in the app today?
2. Look at every frame: real screen from a build that matches the words (T40), no third-party brand, Pro marked, no
   notification art?
3. Links: before P-day → website with UTM; after → Play with UTM (`CHANNEL_KIT.md`).
4. Write "Truth: pass" with the rule IDs that were relevant, or the failures and the fix.
