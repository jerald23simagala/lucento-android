# Lucento launch film: asset inventory

Everything in the film, where it came from, and the rights to it. Paths are in `lucento-studio/video/`
unless stated otherwise.

## App screens (53 + 1 stitched, and 3 on a tablet and a foldable)

**Source:** a Lucento debug build of `main` at `ecec362` (0.5.20, version code 25, plus the wide-window split, PR #158,
and the on-pace demo month, PR #154, which ship in the next release). The phone screens ran on the API 35 shots
emulator (`Lucento_Shots_API35`, 1080 × 2400, 420 dpi) and the tablet and foldable screens on the tablet emulator
(`Lucento_Tablet_API35`) on 2026-11-27, with the clock set to Sunday 2026-11-08 10:00 (Manila) to match the demo month
used on the website and Play listing. Earlier cuts used 0.5.8 (2026-11-14), 0.5.14 (2026-11-19) and 0.5.19
(2026-11-24); the discovery report's section 7 lists what changed on screen up to 0.5.14. The on-pace month (rent due
on the 10th) replaced a double rent that showed Safe to spend ₱0.00 and a budget over by ₱12,729.80 in the 0.5.19 cut.

**Processing:** `tools/prepare_screens.py` crops the emulator status bar (top 68 px) and the 3-button navigation bar
with the debug build's TEST badge (bottom 126 px). That's the same crop the website uses. It then saves WebP quality 92
to `public/launch-film/screens/` and writes `screens.json` plus `src/launch-film/screens.generated.ts`. Nothing inside
a screen is altered. `tools/prepare_wide.py` does the same for the tablet and foldable captures (status bar and gesture
bar cropped, into `screens/wide/`); behind the frames' drawn bars it stretches each screen's own outermost rows (a
per-column median, smoothed along the row so text doesn't streak), so each pane keeps its background there.

`tools/capture/run_test_capture.sh` compiles the app ahead of time after installing it. On a memory-starved PC the
instrumented debug build otherwise misses Android's startup timeout ("failed to complete startup", killed as a
background ANR). After an app update, `tools/remap_boxes.py` finds where each crop box in the scenes moved.

**Capture runs:**

| Run | How | Screens |
|---|---|---|
| `tour-dark` | lucento-android `tools/screenshots/capture_marketing.sh <dir> tour-php`: `ScreenshotTourTest -e demoCurrency PHP -e demoDark true -e demoHistory true` (the existing CI tour, pesos ×10, dark, with the demo year's history) | onboarding presets and review, bell, Transactions, credit card, car loan, new wallet, Insights top, Ask Lucento (question, answer, report), Categories, Goals, Scheduled, profile menu, Awards |
| `showcase-2` | `capture_marketing.sh <dir> showcase-php`: `WebsiteShowcaseTest -e websiteShowcase true -e showcaseCurrency PHP -e demoHistory true` (the demo month plus wallets in USD, AED and JPY at fixed rates, on the test build's simulated Pro plan) | Wallets (cards, rows, net worth), Dollar savings, multicurrency Dashboard, the Wallets screen in all five themes (from a second run with the test's theme gallery moved after the foreign wallets, a capture-only change, so every theme shows the same net worth as Home) |
| `live` | The showcase state, driven by adb (`tools/capture/`) | canonical Dashboard (net worth ₱428,338.10 with the demo year's history) and its two scroll positions, Hide amounts, Budget (its Spendable wallets counts the foreign wallets, as Home does), Insights charts, composer states (empty, listening, typed, bulk, saved), partner entry, Transactions after each save |
| `wide` | `tools/capture/film_wide.sh` on the tablet emulator: the same showcase run at phone size, then gesture navigation, the simulated Pro Monthly plan and three screen shapes set with `wm size`/`wm density`: the Pixel Tablet (2560 × 1600 at 320 dpi, 1280 × 800 dp), Play's 4:3 foldable shape (2688 × 2016 at 420 dpi, 1024 × 768 dp) and its 10:16 cover screen (1260 × 2016 at 420 dpi) | Dashboard beside Insights on the foldable, the cover screen's Dashboard, the composer beside the Dashboard on the tablet (scene 09), with alternates (Milestones, Budget) |

**Fictional data:** everything comes from `DemoMonth.seed` (androidTest), the same world the website and Play listing
show:

- **Wallets:** Everyday bank, Cash, Savings, Credit card, Car loan; Dollar savings, Dubai account, Japan trip yen.
  The card was "Visa card" before 2026-11-27, which named a card brand (T21).
- **Money:** a ₱42,000 salary and nine budget categories.
- **Goals:** Emergency fund and Japan trip.
- **Schedules:** Fibre internet, Phone plan, Rent, Car loan instalment.

The film adds only what was entered on screen during the capture:

- Lunch 500 (not saved)
- Groceries 850 (not saved)
- Coffee 180, lunch 250, taxi 240, phone bill 300 (saved, into Everyday bank: the default wallet). Until 0.5.14 the
  message named a ride-hailing brand, which TRUTH_RULES T21 doesn't allow, and until 2026-11-27 it ended "load 300", a
  Filipino money word (T8) that the owner's plain-English rule for pictures (2026-11-26) leaves out
- Family dinner 1,280 (saved, into Everyday bank)
- Lunch 150, groceries 1200, taxi 240 (on the tablet and the foldable, not saved)

**Staging disclosures:**

- **Voice (S04):** the Listening sheet is a real capture. Since the 2026-11-27 capture it carries the on-phone line
  ("Your words are turned into text on this phone."), which the emulator showed without the online-voice opt-in; the
  0.5.19 cut showed the online-voice line instead. The sheet closes after about 1.5 s without speech, so the still is
  taken as it opens. The emulator can't hear speech, so
  the phrase was then entered as text. The row card it produces is identical to the spoken result. The spoken words
  are a caption beside the phone, not text drawn into the app. In the app, a spoken message also carries a small
  "Spoken" label, which this capture doesn't show. Behind the sheet the film draws the app's own scrim (black at 32 %,
  measured from the capture, within one level) over the real composer, so the sheet can slide away cleanly.
- **Typing (S04, S07):** each still already contains the real typed text. Covers in the field's own colour (#18233F)
  shrink left to right, so the words appear in the order typed.
- **Motion:** live screen recordings ran at about 18 fps on this machine, too choppy for the film. All motion is
  therefore camera work on full-resolution stills: pans, push-ins, cross-fades, slide-ups and reveals of the screens'
  own pixels. The bar and donut "draw" reveals use the real chart pixels. The mic ring pulse mirrors the app's own
  voice-level animation.
- **Dashboard scroll (S02):** three real captures stitched at offsets 998 px and 1,796 px (template match above 0.98),
  each seam halfway through the overlap. The header and dock are pinned, as they are in the app, and the app's 48 px
  fade into the dock (measured from the captures) is drawn at the dock edge.
- **Shared (S07):** both phones show real screens of the same ledger, staged like the Play listing's Shared graphic. A
  real two-account capture (sign-in and sync on the test project) is listed as future work.
- **Tablets and foldables (S09):** every screen is a real capture of the same showcase state as the phone screens, so
  the figures match. Play's foldable and tablet shapes are set on the tablet emulator (the app lays itself out by its
  window's width, as on a real foldable held open flat); a real Galaxy Z Fold8 unfolds landscape-first at 933 × 704 dp,
  and a Fold7 splits held in landscape (KNOWN_ISSUES WW-5). The frames, the hinge and the unfold are drawn: the cover
  screen and the inner screen are two captures, and the inner screen wakes from black as the cover opens.

## Brand

| Asset | Source | Notes |
|---|---|---|
| Rising Crest symbol | `lucento-website/brand/logo/symbol/lucento-symbol-light.svg` → `public/launch-film/brand/symbol.svg`; paths inlined in `src/launch-film/components/Crest.tsx` | Logo colours #154DFD, #61AEFD, #FECA45, dot #FDA81B; animated only by growing the bars from their base and lifting the dot; never recoloured, no gradients or effects on the mark |
| Wordmark (white, navy) | `lucento-website/brand/logo/wordmark/lucento-wordmark-{dark,light}.svg` | Outlined Manrope Bold, as supplied |
| Theme landscapes | `lucento-android/docs/design/illustrations/themes/scenery_{midnight,sunrise,grove,aurora,mono}_source.png` → WebP in `public/launch-film/scenery/` | Lucento's own theme art (generated 2026-11-05 with ChatGPT image generation for the app); originals untouched |
| Ridge layers | `tools/prepare_scenery.py` → `ridges-sunrise.png`, `ridges-midnight.png` | Derived masks; the ridge line comes from the Midnight panorama's horizon glow. The film draws its own sky gradient and sun |
| Mascot | Appears only inside real screens (composer, Ask, profile) | Supporting role, as the brand brief asks |
| Bank and e-wallet logos | **Not used** | Third-party trademarks; showing them in an ad can imply a partnership |

## Typography

Plus Jakarta Sans 400/500/600/700, the same static files the app ships (`app/src/main/res/font/`), SIL Open Font
License 1.1 (`public/launch-film/fonts/PlusJakartaSans-OFL.txt`). It's the brand typeface in the studio design system
and the face inside every screen. The website currently sets headlines in Inter; to match it, change `FONT` in
`src/launch-film/theme.ts`.

## Audio

All original, synthesized from code by `tools/compose_score.py`: no samples, no third-party audio, no licence needed.
The random seed is fixed, so it regenerates exactly.

| File | What | Length |
|---|---|---|
| `public/launch-film/audio/score.wav` (and `score-30.wav`, `score-15.wav` for the cuts) | 90 BPM, D major ambient electronic: detuned-saw pads, FM bell arpeggio, sine sub bass, soft kick, hats and claps arriving scene by scene, risers into the two logo moments, a shared convolution reverb. Builds from about −21 dB RMS to −14 dB at the peak, peak −3.9 dBFS (master, re-rendered 2026-11-27 with scene 09's two bars; the cut scores are unchanged) | 98.7 s |
| `sfx/tap.wav`, `tick.wav`, `pop.wav`, `mic.wav`, `confirm.wav`, `whoosh.wav`, `swell.wav`, `shimmer.wav` | UI and transition effects, placed on screen events in `src/launch-film/audio/Soundtrack.tsx` | 1–4 s |

To swap in a licensed track, replace `score.wav` (98.7 s, or trim it in `Soundtrack.tsx`). The edit points sit on a
90 BPM grid.

**Voiceover** (since 2026-11-24): generated with ElevenLabs (`eleven_v4`) on the owner's paid Creator plan, whose terms
allow commercial use, in the library voice Laurel ("Warm, Kind & Confident", American English; owner, 2026-11-25).
Rianne ("Warm & Friendly Filipino Creator") narrated the 2026-11-24 renders. It is an AI voice, so narrated posts carry
each platform's AI label (`VOICEOVER.md`).

| File | What |
|---|---|
| `voiceover/takes/*.mp3` | The ElevenLabs takes as downloaded (used: `30-laurel-a`, `15-laurel-a`, `93-laurel-a`, and line 2 of `screens-laurel-b` for scene 09; the rest are alternates, Rianne's takes and the 30 s voice samples of Justin, Marina and Charlotte) |
| `public/launch-film/audio/vo/<cut>-<voice>/NN.wav` | Each line, cut from its take by `tools/voiceover.py`: levelled to −16 LUFS, limited at −3 dBFS, 48 kHz mono |
| `src/launch-film/audio/Voiceover.tsx` | Where each line starts (`VO_AT`) and the score dip under it |

## Other

| Asset | Source |
|---|---|
| Grain tile `public/launch-film/fx/grain.png` | Generated (Gaussian noise, seed 7). It dithers the long dark gradients so the encode doesn't band |
| Phone mockup | Drawn in code (`components/Phone.tsx`): light titanium frame so dark screens keep a visible edge (owner preference from the website), drawn status bar "9:41", gesture bar |
| Tablet and foldable mockups | Drawn in code (`components/Wide.tsx`), in the phone's titanium: a tablet with its camera in the long bezel; a book-style foldable whose cover swings open round the hinge (CSS 3D, with the halves' edges as thickness), a seam where the hinge breaks the band, and a barely visible crease. The same drawn status bar and gesture bar |

## Outputs

| File | Where |
|---|---|
| Films | `lucento-studio/video/out/` (git-ignored), copied to `Downloads/lucento-launch-film/`: `lucento-launch-film.mp4` (98.7 s, music-led), `lucento-launch-film-narrated.mp4` (98.7 s), `-30s`, `-15s` (narrated), and each with `-vertical` (1080 × 1920). Earlier renders are kept in `previous-0.5.19/` (retired: they show "Visa card", T21), `previous-0.5.14/` and `previous-0.5.8/` |
| Review frames | `lucento-studio/video/out/` (git-ignored) |
