# Lucento channel kit

Everything to paste while setting up and running Lucento's channels. All of it passes `TRUTH_RULES.md`. Facebook
Page and Group texts live in `docs/community/facebook/README.md`.

## 1. Names, handles and pictures

| Channel | Display name | Handle | Picture |
|---|---|---|---|
| TikTok | Lucento · Budget App | `@lucentoapp` | `docs/community/facebook/profile-720x720.png` |
| Instagram | Lucento · Budget App | `@lucentoapp` | same file |
| YouTube | Lucento | `@lucentoapp` | same file |
| Facebook Page | Lucento | (Page username) `lucentoapp` | same file, plus `page-cover-1640x624.png` |

- One handle everywhere makes Lucento easy to find. If `lucentoapp` is taken somewhere, try `lucento.app`, then
  `lucentoapp.ph`, and use the same fallback on every channel that's still open.
- "Budget App" in the display name helps search on TikTok and Instagram, where the name field is searchable.
- Every account is created with **lucentoapp@gmail.com** (so the brand owns it) and has 2-step verification on.
- A YouTube banner is optional at the start. It comes with the first production batch.

## 2. Bios

**TikTok** (80 characters max; 70 used):
> Type your gastos like a text. A budget app for Android. Coming soon. ✨

After P-day (80 characters max; 65 used):
> Type your gastos like a text. A budget app for Android. Get it ⬇️

**Instagram** (150 characters max; 137 used, line breaks included):
> Everything about your money, in one clear view.
> Type what you spent like a text. Budgets, bills, goals.
> Android · Offline · No bank login

**YouTube** channel description:
> Lucento is a personal finance app for Android: wallets, budgets, bills, debts, goals and insights in one clear view.
> Type a purchase the way you'd text a friend ("lunch 250, jeep 13, load 100") and Lucento turns it into
> transactions you check before saving. It works offline and never asks for your bank login.
>
> Short tips and features every week.
> Website: https://www.lucentoapp.com · Email: lucentoapp@gmail.com

**Link in bio** (every channel):
- Before P-day: the website link for that channel (section 3).
- After P-day: the Play link for that channel.
- If TikTok doesn't offer a website field on the account yet, the bio text "lucentoapp.com" is enough.

## 3. Links (UTM tags)

UTM tags tell us which channel each visit or install came from. Never post an untagged link.

**Before P-day**, to the website:
```
https://www.lucentoapp.com/?utm_source=<source>&utm_medium=<medium>&utm_campaign=warmup
```

**After P-day**, to Google Play (Play Console reads the tags from `referrer`):
```
https://play.google.com/store/apps/details?id=com.lucento.app&referrer=utm_source%3D<source>%26utm_medium%3D<medium>%26utm_campaign%3Dlaunch
```

| Where | source | medium |
|---|---|---|
| TikTok bio | `tiktok` | `bio` |
| Instagram bio | `instagram` | `bio` |
| YouTube bio, descriptions | `youtube` | `bio` / `video` |
| Facebook Page posts | `facebook` | `social` |
| Lucento Community group | `fb_group` | `community` |
| Reddit, other communities | `reddit`, `community_<name>` | `community` |
| A creator's post | `creator_<handle>` | `creator` |
| Press | `press_<outlet>` | `press` |
| KlaroPH Page | `klaroph` | `klaro_page` |
| Klaro in-app banner | `klaroph` | `klaro_banner` |
| klaroph.com tools and guides card | `klaroph` | `klaro_tools` |
| Klaro email | `klaroph` | `klaro_email` |
| Product Hunt, AlternativeTo | `producthunt`, `alternativeto` | `directory` |
| Lucento Shared invite (in the app) | `shared_invite` | `referral` |
| "Tell a friend" (in the app) | `app_share` | `referral` |

Example, the TikTok bio before P-day:
`https://www.lucentoapp.com/?utm_source=tiktok&utm_medium=bio&utm_campaign=warmup`

## 4. Calls to action

| Phase | On screen (last 2–3 s) | Caption line |
|---|---|---|
| Warm-up | "Coming soon to Google Play" | "Coming soon to Google Play. Follow so you don't miss launch day." |
| Launch week and after | "Get it on Google Play" | "Free to download on Google Play. Link in bio." |
| KlaroPH channels | "From the maker of KlaroPH" | "KlaroPH on the web, Lucento on your phone." |

## 5. Hashtags (3–5 per post; mix one broad and two or three specific)

| Set | Tags |
|---|---|
| PH everyday | `#ipon` `#budgeting` `#gastos` `#sweldo` `#ipontips` `#personalfinanceph` `#moneytipsph` |
| Global | `#budgeting` `#budgetapp` `#moneytips` `#personalfinance` `#expensetracker` `#savingmoney` |
| By topic | OFW: `#ofw` `#ofwlife` · Couples: `#couplesfinance` · Android: `#androidapps` · Payday: `#payday` `#kinsenas` |
| YouTube Shorts | `#shorts` plus two from the sets above |

The analyst replaces tags that don't pull views after four weeks. No brand hashtags for banks, e-wallets or shops
(T21).

## 6. Caption shape

```
<Hook, the same words as on screen>
<One line on what it does for you>
<Taglish line, PH posts>
<Call to action for the phase>
<3–5 hashtags>
```

Example (week 1, Monday):
> One message. Three transactions. Done.
> Type your day the way you'd text a friend, check the rows, save once.
> I-type mo lang: "jeep 13, load 100, sweldo 25000."
> Coming soon to Google Play. Follow so you don't miss launch day.
> #budgeting #gastos #ipon #personalfinanceph

## 7. Saved replies (comments and messages)

Paste them as they are, or as a starting point. Before P-day, use the "coming soon" wording.

| They ask | Reply |
|---|---|
| Where can I get it? (before P-day) | "Coming soon to Google Play! Follow us and we'll post the moment it's live. 🙌" |
| Where can I get it? (after P-day) | "It's on Google Play: tap the link in our bio. 🙌" |
| Is it free? | "Yes, it's free to download, and the free plan has no time limit. Lucento Pro adds extras like more wallets, multicurrency and Shared." |
| iPhone? | "Right now Lucento is on Android only." |
| Does it connect to my bank or e-wallet? | "No. Lucento never asks for your bank login or connects to your bank or e-wallet. You type, say or scan what you spent, and it's read on your phone." |
| Is my data safe? | "Your entries stay on your phone, and the core features work offline. Cloud sync is optional (Pro). Full details: lucentoapp.com/privacy" |
| Is it AI? | "Typing, voice and scanning are read by Lucento on your phone, no AI needed. AI is optional: Smarter reading (Pro) and Ask Lucento's AI answers use Google's Gemini, only if you turn them on." |
| Tagalog? | "The app is in English, and it understands everyday Filipino money words like sweldo, palengke, load and jeep." |
| How much is Pro? | "Pro prices are shown in Google Play for your country, and the free plan works without it." |
| Is this like KlaroPH? | "Same maker! KlaroPH is on the web, and Lucento is the Android app: offline, on your phone, and you type your gastos like a text." |
| Feature request | "Love this, thank you! It's on our list. Join the Lucento Community group to see what's coming." |
| Something's broken | "Sorry about that! Please email lucentoapp@gmail.com with your phone model and what happened, and we'll look into it." |
| Rude or spam | Don't reply. Hide or delete it. |
| Ads? Price promises? Anything not in this table | Don't guess: leave it for the owner (T14, T15) |
