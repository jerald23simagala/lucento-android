# Truth and policy rules for every Lucento post

Every caption, video, image, reply, pitch and listing passes this check before it reaches the owner's Launch Board. A
post that fails any rule is fixed or dropped, never "approved with a note". Rules come from the code-verified feature
inventory (`docs/marketing/LAUNCH_VIDEO_FEATURE_DISCOVERY.md` §3 and §5), the Play listing rules
(`docs/play/PLAY_STORE_SUBMISSION.md` §4), the Privacy Policy (`feature/legal/LegalDocuments.kt`, published at
/privacy) and owner decisions. When the app changes, re-check the inventory before claiming a feature.

On a Launch Board card, cite failures by ID (for example "T4, T11").

## Product truth

| ID | Rule | Say instead |
|---|---|---|
| T1 | Never call the composer, voice or scanning "AI". They are read by Lucento's own rules on the phone; ML Kit only extracts text from images | "Type it like a text." "Read on your phone." |
| T2 | AI is only **Smarter reading** (Pro, opt-in) and **Ask Lucento's AI answers** (opt-in, signed in). Any post about them names Google's Gemini, says it's optional and asks for consent, and links /privacy | "Optional AI answers by Gemini, only if you turn them on." |
| T3 | No automation claims: nothing is auto-logged, auto-categorised without review, or paid automatically. Scheduled items are never posted by themselves | "Lucento fills in the details; you confirm." |
| T4 | No bank or e-wallet connection, SMS import or bank sync exists. The 409 bank and e-wallet faces are display only | "No bank login." |
| T5 | No push notifications, alerts or reminder pop-ups exist. "Never miss a bill" is allowed for Scheduled transactions (owner, 2026-11-14), but never with notification imagery | "See every bill coming." |
| T6 | Mark every Pro feature "Pro": multicurrency, Lucento Shared (inviting), cloud sync and restore, money owed to you, things you own, 6/12-month Insights, PDF statements, 3 of the 5 themes (Grove, Mono, Aurora), Smarter reading, and going past Free's limits (more than 3 money wallets, 2 cards or loans, or 2 active goals) | "(Pro)" in the caption and on screen |
| T7 | Use exact limits: 30 rows per message, 60 lines per statement scan, one Partner in Shared, 82 preset categories, 409 providers in 48 countries | — |
| T8 | Lucento reads English, plus everyday Filipino money words (sweldo, palengke, merienda, load, jeepney). Never claim another language | "It understands sweldo, palengke and load." |
| T9 | No undo, custom dashboard, custom category icons or colours, budget rollover, live asset valuations or family groups. Don't imply any of them | — |

## Privacy truth

| ID | Rule | Say instead |
|---|---|---|
| T10 | Never "nothing leaves your phone", "100% private" or "your data never leaves". Exchange rates (on by default), Google Play billing diagnostics, purchase checks and every opt-in send something | "Your entries stay on your phone unless you turn on sync." "Works offline." |
| T11 | Any privacy line is scoped ("your entries", "your records") and, where the format allows, links www.lucentoapp.com/privacy | — |
| T12 | Voice: never "your voice never leaves the phone" (online voice is an opt-in) | "Lucento never records or keeps your voice." |
| T13 | Backups are not encrypted. Never imply that they are | "Back up to a file whenever you like." |

## Money, pricing and availability

| ID | Rule |
|---|---|
| T14 | No "no ads", "ad-free" or "free forever" promises (ads may come to Free). Don't mention ads at all; if someone asks, the owner answers |
| T15 | No prices or trial lengths in content unless the owner confirms them as live in Google Play that week. "Free to download, with optional Pro" is always fine |
| T16 | Before P-day (production access granted and the release live), never say "available on Google Play" or "download now". "Coming soon to Google Play" is allowed (the website's status since 2026-11-08). After P-day, use the UTM-tagged Play link |
| T17 | No "best", "#1", "top-rated" or "the only" claims, and no comparisons that name a competitor |
| T18 | No savings or outcome promises ("save ₱5,000 a month with Lucento"). General money education only, never personal financial, tax or legal advice |

## Visual truth

| ID | Rule |
|---|---|
| T19 | Real app screens only, captured from a real build (showcase or tour captures, or live adb stills). No mocked UI, no edited numbers |
| T20 | Demo data is demo data. The showcase world (₱258,108.10 net worth, budget at 89%) never appears as a real person's results, and no caption says "my finances" over it |
| T21 | No third-party brands in captions, hashtags, visuals or metadata: no bank, e-wallet, merchant or telco names or logos (GCash, Maya, Meralco, Jollibee, Grab…), even though the app understands them. Use the demo wallets ("Everyday bank", "Cash", "Savings") and generic words ("e-wallet", "electric bill") |
| T22 | The Rising Crest logo and mascot source files are never altered. The mascot's torch does not follow the budget, so never claim it does |
| T23 | Music is the original synthesized score (`lucento-studio/video/tools/compose_score.py`) or silence. No licensed or trending tracks added by us. The owner may add a sound inside TikTok only from its Commercial Music Library |

## Honest promotion

| ID | Rule |
|---|---|
| T24 | Never fake reviews, testimonials, quotes, user counts, comments or engagement. No buying followers, likes or reviews, and no engagement pods |
| T25 | Never offer anything for a rating or review, and never ask only happy users to rate (Google Play policy). "Tell us what you think on Google Play" to everyone is fine |
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
2. Look at every frame: real screen, no third-party brand, Pro marked, no notification art?
3. Links: before P-day → website with UTM; after → Play with UTM (`CHANNEL_KIT.md`).
4. Write "Truth: pass" with the rule IDs that were relevant, or the failures and the fix.
