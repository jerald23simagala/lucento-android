# Lucento launch film: final storyboard

**Length:** 82.7 s (2,480 frames). **Format:** 1920 × 1080, 16:9, 30 fps, H.264 MP4 with AAC audio.
**Source:** `lucento-studio/video/src/launch-film/` (Remotion 4.0.539). Composition `LaunchFilm`.

## The idea: "First Light"

The film is one continuous dawn. It opens under the app's own Midnight panorama and ends in its Sunrise panorama.
Both are Lucento theme illustrations that share one composition, so the ridges cross-fade like light changing on a
single landscape.

At the end the sun rises behind those ridges and becomes the dot of the Rising Crest logo, which is itself a small sun
over three rising bars. "See your money clearly" happens literally: night turns to clear morning.

The emotional arc follows the light:

| Light | Feeling |
|---|---|
| Night | Curiosity |
| Night, focused light on the phone | Clarity and control |
| Blue hour | Intelligence |
| Dawn | Togetherness |
| Sunrise | Confidence |

## Grid

- The score is 90 BPM. One beat is 20 frames, one bar is 80 frames, and every scene starts on a bar line.
- The music lands the crest on bar 1 (frame 80) and the final logo on bar 29 (frame 2320).
- `src/launch-film/timeline.ts` and `tools/compose_score.py` hold the same numbers. Change both together.

## Scenes

Timecodes are mm:ss.f. "Real" means a capture of Lucento 0.5.8. Every screen in the film is real; the motion is
camera work on those captures, and nothing inside a screen was drawn.

### 01 · Introduction (0:00.0–0:05.3, bars 0–1)
- **Shot:**
  - Night sky over the Midnight ridges, with the moon.
  - Seven real UI fragments drift in depth, with blur on the far ones: the net wallet balance card, the monthly budget ring, the Japan trip goal, the yen wallet card, the income/expenses chart, an Ask Lucento answer and today's transactions.
  - They gather to the centre and become the three bars of the Rising Crest as the music lands (frame 80).
  - The dot rises, the wordmark draws in, and the logo lifts away.
- **Text:** "Your entire financial world." (appears at 0:00.5, before the logo forms).
- **Sound:** pad swell on B minor; a reverse swell into a soft impact and bell chord on the crest; shimmer on the dot.

### 02 · Financial clarity (0:05.3–0:13.3, bars 2–4)
- **Shot:**
  - The phone rises out of the dark with the real Dashboard: net wallet balance ₱258,108.10, shortcuts, monthly budget at 89% "Ahead of pace".
  - The Dashboard then scrolls through the budget detail, Safe to spend and Upcoming. The scroll is a pan over three stitched real captures, with the header and dock pinned as they are in the app.
  - The phone glides to centre and its screen changes to Wallets.
- **Text:** "Everything, / in one clear view." + "Know where you stand at a glance."
- **Sound:** arpeggio enters; whoosh as the phone rises.

### 03 · Your financial world (0:13.3–0:21.3, bars 5–7)
- **Shot:**
  - Real wallet cards fan out from behind the phone: Cash, Everyday bank, the Visa credit card, Japan trip yen (¥60,000 ≈ ₱22,950), Dubai account (AED 4,200 ≈ ₱66,108) and Dollar savings ($1,850 ≈ ₱106,930).
  - "Every asset": the net-worth hero and the assets/liabilities split light up on screen.
  - "Every currency": the three foreign wallets step forward while the others dim.
  - The phone moves left, and its screen becomes the composer.
- **Text:** "Every account." · "Every asset." · "Every currency." (one line, each phrase on its beat).
- **Sound:** hats and sub bass enter; shimmer on the currencies.

### 04 · Effortless entry (0:21.3–0:34.7, bars 8–12): the hero scene
1. **Title (0:21.3):** "Less input. / More control."
2. **Say it (0:23.3–0:26.9):**
   - Mic tap, and the real Listening sheet rises ("Listening… Take your time").
   - The mic ring pulses with the voice, as it does in the app.
   - Beside the phone, the spoken words appear one by one with a waveform: "Add 500 pesos for lunch using cash."
   - The sheet closes and the real result appears: Lunch −₱500.00 · Restaurants · Cash.
   - Line: "Speak naturally. Lucento fills in the details."
3. **Type it (0:27.5–0:29.9):**
   - The camera pushes onto the field, and the real typed message is revealed in order: "Paid 850 for groceries using my cash wallet".
   - Send, and the reply: Groceries −₱850.00 · Cash.
   - Line: "Write it the way you'd text it."
4. **All at once (0:30.3–0:34.7):**
   - "Coffee 180, lunch 250, Grab 240, load 300" is typed fast. Send, and "Got it! Here are 4 transactions I'll add".
   - The four real row cards lift out of the phone into a grid, then return.
   - Tap Save 4, and the real "4 transactions saved".
   - Line: "Four in one message. One tap to save."
- **Sound:** kick enters at the end of the scene; tap, mic, pop and keyboard ticks follow the screen; a confirm chime on save.
- **Truth:** the composer's reading runs on the phone with rules, so it is never called AI.

### 05 · Plan ahead (0:34.7–0:42.7, bars 13–15)
- **Shot:**
  - The real onboarding step "What do you spend on?" appears beside a large "82".
  - The five pre-ticked chips glow in turn: Rent, Electricity, Water, Internet, Mobile phone.
  - Then three phones slide in: Goals (Emergency fund 42%, Japan trip 55%), Budget (November, ₱2,920.10 left) and Scheduled (overdue and upcoming bills).
- **Text:** "Plan today. Feel confident tomorrow." · "82 / ready-made categories / Twelve everyday ones are picked for you." · labels "Goals", "Budget", "Scheduled".
- **Sound:** soft kick on 1 and 3; whoosh as the trio arrives.

### 06 · Financial intelligence (0:42.7–0:53.3, bars 16–19)
- **Shot:**
  - Insights' income/expense bars grow from their baseline, a reveal of the real chart.
  - "Where your money went" draws its ring with a clock wipe of the real donut.
  - The Ask Lucento phone steps in: a tap on "What are my top 5 spending categories?" brings up the real answer ("Rent leads your spending…", report card, "Calculated on this phone").
  - "Open report" leads to the real Top spending categories statement.
- **Text:** "Understand more. / Decide better." · "See where your money goes." → the question as a quote → "Answered on your phone." → "Six built-in reports."
- **Sound:** clap enters on beat 3; tap and pop on the answer.

### 07 · Lucento Shared (0:53.3–1:04.0, bars 20–23)
- **Shot:**
  - The dawn begins: violet sky, a warm horizon.
  - Two phones carry chips: "You" and "Your partner".
  - The partner types "Family dinner 1,280", gets the real reply and saves ("Transaction saved").
  - An arc of light carries it across, and the same row appears highlighted at the top of your Transactions.
  - The partner's phone then shows the same list: one ledger.
- **Text:** eyebrow "Lucento Shared" · "Better financial decisions, together." → "One shared financial picture."
- **Staging:** the same approach as the owner-approved Play listing graphic. Both phones show real screens of the same ledger; in the app, the partner's entry reaches the owner through sync.
- **Sound:** one bar of air as the drums drop out, then the shared progression (G – A – F♯m – Bm); confirm and shimmer on the hand-over.

### 08 · Make it yours (1:04.0–1:12.0, bars 24–26)
- **Shot:**
  - The real Wallets screen in five themes on a slow arc: Sunrise light, Midnight, Aurora, Grove light, Mono.
  - The arc folds into one phone showing the Dashboard.
  - A tap on Hide turns every amount into ₱••••.
- **Text:** "Your money. / Your way." · "Five themes" → chips "Hide amounts", "Works offline", "App lock", "No bank login".
- **Sound:** the peak of the track: four-on-the-floor kick and sixteenth-note bells; a riser into the finale.

### 09 · Final reveal (1:12.0–1:22.7, bars 27–30)
- **Shot:**
  - The sun rises behind the Sunrise ridges as the sky turns ivory.
  - On the last downbeat (1:17.3) it shrinks into the Rising Crest's dot while the three bars rise beneath it.
  - The navy wordmark draws in. The mark settles flat, in its own colours, with nothing on it.
- **Text:**
  - "See your money clearly."
  - "Financial clarity, within reach."
  - "Discover Lucento." with "lucentoapp.com" beneath.
  - "For Android. Some features require Lucento Pro." (small, bottom).
- **Sound:** drums out, a long riser, the final impact and bell chord on D major, and a reverb tail to silence.

## Pro features shown

Multicurrency wallets (S03), Lucento Shared (S07) and three of the five themes (S08) are Lucento Pro. The end card says
so, as the Play listing does. No prices appear, because they come from Google Play and aren't confirmed.

## Claims deliberately left out

These are true limits of 0.5.8 (see `../LAUNCH_VIDEO_FEATURE_DISCOVERY.md` §5):

- notifications or "never miss a bill"
- AI for everyday entry
- bank connections
- a customisable dashboard
- "nothing leaves your phone"
- real bank logos (trademarks)
