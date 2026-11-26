# Play listing review (CRO-4), 26 Nov 2026

This review covers the two listings as uploaded: the main one in US dollars and the Philippine custom listing in pesos.
It's part of the conversion plan (`CRO_PLAN.md`). The listing isn't public yet (closed testing, so the page is a 404), so
the evidence is:
- the graphics in `docs/play/graphics/{global,ph}`;
- the texts in `docs/play/PLAY_STORE_SUBMISSION.md` §4;
- Play's public search results for "gastos tracker" in the Philippines, read at phone width on 26 Nov.

The search capture is kept outside this public repo, in lucento-studio `qa/out/mcp/cro4-play-ph-gastos.png`.

## Findings, ranked

| # | Finding | Why it matters | Fix | Effort |
|---|---|---|---|---|
| L1 | Screenshot 3's headline says "Know what's safe to spend today", but its screen answers **$0.00 / ₱0.00**, with the budget remaining at −$1,272.98 / −₱12,729.80 and the red note "Bills due take what's left of your budget". Screenshot 1 and the feature graphic show the same month: 89 % spent with 26 % of the month gone | The screens most people see show the promise failing, and Lucento looking like an app that tells you off. The data is honest demo data, but it's an unlucky month to show | Recapture on a demo month that's on pace, so Safe to spend shows a positive amount and the budget sits below the day's pace. Re-render screenshots 1 and 3 and the feature graphic for both listings, then the owner re-uploads them | Medium: the demo data in the capture pipeline (`capture_marketing.sh`, `demoHistory`), then lucento-studio `lab/play-graphics` |
| L2 | The feature graphic is the large image above each result in Play's web search. The apps around Lucento put the search phrase in theirs ("Expense Tracker / Budget Planner") or a big brand mark. Lucento's says "Everything about your money, in one clear view." and shows the $0.00 card from L1 | It's the first thing a web searcher sees. A keyword-led line says "this is what you searched for" | With L1, lead with the search words, for example "The budget tracker you type like a text." Log it as one change; Play's experiments can test it again later | Small, done with L1 |
| L3 | The Philippine listing's text sells gastos, ipon, utang, sweldo, jeep and load, but its screenshots are the global ones in pesos ("Lunch 150, groceries 1200"), and none shows utang or ipon | Philippine visitors are the first audience (marketing is Philippine-first). The pictures don't speak to what the text promises | A Philippine set: the composer typing "jeep 13, load 100, sweldo 25000" (the example the description already uses), captions that say gastos, ipon and utang, and a debts screen ("listahan ng utang"). This is **variant B for the first experiment (E1)** against the current set, not a straight swap | Medium: new captures and captions |
| L4 | The Philippine listing's title is still the global "Lucento: Budget Tracker". The open brand call in `ASO_KEYWORDS.md` is "Lucento: Budget & Ipon Tracker" (30 characters) | The title weighs most in search. "ipon tracker" is a niche led by small local apps with the word in their title | The owner's call | Tiny: one field in the custom listing |
| L5 | The icon is the Rising Crest on white. In Play's light search list, the apps around it sit on solid colour (yellow, green) | A white tile has no edge on a white page, so it's easy to scroll past | An icon variant on a solid brand colour, as **E3** after the screenshot and short-description tests. The approved artwork stays untouched: the variant is a new asset | Small, later |
| L6 | No ratings at launch. The apps around Lucento show 4.8–5.0★ | Social proof is missing for the first weeks, whatever the listing says | Nothing now: the rating card (HF-1) asks after 7 days and 20 entries, and never asks testers. Revisit its timing after the baseline week if ratings lag. Never incentivise (T25, Play policy) | — |

## Owner's choices (26 Nov)

- **L1 and L2: recapture, with the keyword headline.**
  - Screenshots 1 and 3 and the feature graphic are rebuilt on an on-pace demo month, for both listings.
  - The feature graphic leads with "The budget tracker you type like a text."
  - The owner re-uploads them from a Mission Control task.
- **L3: withdrawn the same day.** A gastos/ipon/utang set was drawn (the composer reading "jeep 13, load 100, sweldo
  25000", an utang page, ipon goals). The owner then said: "Honestly i cringe with the pinoy slangs." The pictures are
  now plain English: the main listing's eight pages, in pesos. The Philippine **text** keeps gastos, ipon and utang as
  search words (the owner chose "Keep the words in the text only").
- **L4: keep "Lucento: Budget Tracker"** as the Philippine title.
- L5 and L6 are as above.

## What's already right

- Screenshot 1's three chips (No bank login · Works offline · Type it like a text) put the differences first.
- Screenshot 2 shows the one thing no leading app does: one typed sentence becomes three transactions to check.
- Pro features are marked in their captions (Multicurrency · Pro, the budget year), as the copy rules require.
- The short descriptions carry the evidenced search terms (`ASO_KEYWORDS.md`).

## Side note for CRO-5

The app's "Ahead of pace" chip means spending faster than the month. A reader can take "ahead" as good news. That's for
the setup and plan page review (CRO-5), not the listing. L1's recapture avoids showing it.
