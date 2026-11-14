# Lucento launch film: asset inventory

Everything in the film, where it came from, and the rights to it. Paths are in `lucento-studio/video/`
unless stated otherwise.

## App screens (53 + 1 stitched)

**Source:** Lucento 0.5.8 debug build, `main` at `1608e50` (the same UI as `0679ccf`). It ran on the API 35 shots
emulator (`Lucento_Shots_API35`, 1080 × 2400, 420 dpi) on 2026-11-14, with the clock set to 2026-11-08 10:00 to match
the demo month used on the website and Play listing.

**Processing:** `tools/prepare_screens.py` crops the emulator status bar (top 68 px) and the 3-button navigation bar
with the debug build's TEST badge (bottom 126 px). That's the same crop the website uses. It then saves WebP quality 92
to `public/launch-film/screens/` and writes `screens.json` plus `src/launch-film/screens.generated.ts`. Nothing inside
a screen is altered.

**Capture runs:**

| Run | How | Screens |
|---|---|---|
| `tour-dark` | `ScreenshotTourTest -e demoCurrency PHP -e demoDark true` (the existing CI tour, pesos ×10, dark) | onboarding presets and review, bell, Transactions, Visa card, car loan, new wallet, Insights top, Ask Lucento (question, answer, report), Budget, Categories, Goals, Scheduled, profile menu, Awards |
| `showcase-2` | `WebsiteShowcaseTest -e websiteShowcase true -e showcaseCurrency PHP` (the demo month plus wallets in USD, AED and JPY at fixed rates, on the test build's simulated Pro plan) | Wallets (cards, rows, net worth), Dollar savings, multicurrency Dashboard, the Wallets screen in all five themes |
| `live` | The showcase state, driven by adb (`tools/capture/`) | canonical Dashboard (₱258,108.10) and its two scroll positions, Hide amounts, Insights charts, composer states (empty, listening, typed, bulk, saved), partner entry, Transactions after each save |

**Fictional data:** everything comes from `DemoMonth.seed` (androidTest), the same world the website and Play listing
show:

- **Wallets:** Everyday bank, Cash, Savings, Visa card, Car loan; Dollar savings, Dubai account, Japan trip yen.
- **Money:** a ₱42,000 salary and nine budget categories.
- **Goals:** Emergency fund and Japan trip.
- **Schedules:** Fibre internet, Phone plan, Rent, Car loan instalment.

The film adds only what was entered on screen during the capture:

- Lunch 500 (not saved)
- Groceries 850 (not saved)
- Coffee 180, lunch 250, Grab 240, load 300 (saved)
- Family dinner 1,280 (saved)

**Staging disclosures:**

- **Voice (S04):** the Listening sheet is a real capture with online voice switched on. The emulator can't hear speech,
  so the phrase was then entered as text. The row card it produces is identical to the spoken result. The spoken words
  are a caption beside the phone, not text drawn into the app. In the app, a spoken message also carries a small
  "Spoken" label, which this capture doesn't show.
- **Typing (S04, S07):** each still already contains the real typed text. Covers in the field's own colour (#18233F)
  shrink left to right, so the words appear in the order typed.
- **Motion:** live screen recordings ran at about 18 fps on this machine, too choppy for the film. All motion is
  therefore camera work on full-resolution stills: pans, push-ins, cross-fades, slide-ups and reveals of the screens'
  own pixels. The bar and donut "draw" reveals use the real chart pixels. The mic ring pulse mirrors the app's own
  voice-level animation.
- **Dashboard scroll (S02):** three real captures stitched at offsets 972 px and 1,750 px (template match 0.9999 and
  0.9996). The header and dock are pinned, as they are in the app.
- **Shared (S07):** both phones show real screens of the same ledger, staged like the Play listing's Shared graphic. A
  real two-account capture (sign-in and sync on the test project) is listed as future work.

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
| `public/launch-film/audio/score.wav` | 90 BPM, D major ambient electronic: detuned-saw pads, FM bell arpeggio, sine sub bass, soft kick, hats and claps arriving scene by scene, risers into the two logo moments, a shared convolution reverb. Builds from about −21 dB RMS to −14 dB at the peak, peak −2.4 dBFS | 82.7 s |
| `sfx/tap.wav`, `tick.wav`, `pop.wav`, `mic.wav`, `confirm.wav`, `whoosh.wav`, `swell.wav`, `shimmer.wav` | UI and transition effects, placed on screen events in `src/launch-film/audio/Soundtrack.tsx` | 1–4 s |

To swap in a licensed track, replace `score.wav` (82.7 s, or trim it in `Soundtrack.tsx`). The edit points sit on a
90 BPM grid.

## Other

| Asset | Source |
|---|---|
| Grain tile `public/launch-film/fx/grain.png` | Generated (Gaussian noise, seed 7). It dithers the long dark gradients so the encode doesn't band |
| Phone mockup | Drawn in code (`components/Phone.tsx`): light titanium frame so dark screens keep a visible edge (owner preference from the website), drawn status bar "9:41", gesture bar |

## Outputs

| File | Where |
|---|---|
| Master film | `lucento-studio/video/out/lucento-launch-film.mp4` (git-ignored; copied to `Downloads/`) |
| Review frames | `lucento-studio/video/out/` (git-ignored) |
