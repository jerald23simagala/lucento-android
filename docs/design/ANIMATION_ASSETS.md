# Animation asset register

Every animation shipped in Lucento is listed here. **There are currently no third-party animation assets.**
All Lottie files are original to Lucento: they are generated from code in `tools/lottie/gen/` (Python, no
templates, no traced artwork) using Lucento's own shapes and palette. (The old L + leaf welcome animation was retired with the Rising Crest logo.) The mascot is not a
Lottie file: the approved character ships as WebP sprites with a code-drawn torch glow (`Mascot.kt`, `docs/mascot/`). No asset was downloaded from LottieFiles or any other
library: the authoring environment could not reach lottiefiles.com, and original work also keeps the brand coherent.

Runtime: [Airbnb Lottie for Android/Compose](https://github.com/airbnb/lottie-android) `com.airbnb.android:lottie-compose:6.6.7`,
Apache License 2.0 (credited in Settings › About). Files are bundled in `app/src/main/res/raw/`; nothing is fetched
at runtime and no network permission is involved.

Rendered previews (lottie-web, first → last frames): `animation-preview-day.png`, `animation-preview-night.png`,
`animation-preview-mascot.png`, `animation-preview-feedback.png`, `animation-preview-mascot-concerned.png`,
`animation-preview-netflow.png` in this folder. lottie-web is a proxy for
lottie-android; on-device appearance has **not yet been verified** (no emulator/device was available).

| File (`res/raw/`) | Purpose | Where it plays | Playback | Size (raw / gzip) | Source · licence |
|---|---|---|---|---|---|
| `net_flow_indicator.json` (+`_night`) | Trend glyph on a tinted tile; markers `up` (green, rising), `down` (red, falling), `flat` (neutral level line for exactly zero) | Home net-flow card (`NetFlowIndicator`), chosen from the calculated net flow | once per direction (≈0.6 s), holds the finished glyph; static icon under reduced motion | 9.1 KB / 1.0 KB each | Original (Lucento) |
| `empty_transactions.json` (+`_night`) | Ledger card with rows and a lens | Transactions: no transactions yet | once, 2 s, holds last frame | 11.1 KB / 1.0 KB each | Original (Lucento) |
| `empty_budgets.json` (+`_night`) | Gauge filling to 62 % with a coin | Budgets: none yet | once, 1.8 s | 5.9 KB / 0.9 KB each | Original (Lucento) |
| `empty_goals.json` (+`_night`) | Target with a sun-yellow flag | Savings goals: none yet | once, 1.8 s | 5.6 KB / 0.8 KB each | Original (Lucento) |
| `empty_recurring.json` (+`_night`) | Calendar with a repeat loop | Recurring: none yet | once, 2 s | 12.1 KB / 1.3 KB each | Original (Lucento) |
| `receipt_scanning.json` (+`_night`) | Receipt under a moving scan beam with viewfinder corners | Scan receipt, only while ML Kit is reading | loops **only while** recognition runs; replaced by results or an error | 8.1 KB / 0.9 KB each | Original (Lucento) |
| `feedback_saved.json` | Green disc, check drawn, soft halo | `ConfirmationHost` after a transaction, transfer or recurring payment **has been committed** | once, 1.2 s | 3.2 KB / 0.7 KB | Original (Lucento) |
| `feedback_milestone.json` | Ring completes, sun-yellow star, eight short rays | Goal halfway / reached (after the contribution is saved); a finished month kept within budget (shown once per month) | once, 2 s | 12.1 KB / 1.1 KB | Original (Lucento) |
| `loading_dots.json` | Three brand-blue dots | `LucentoWorkingIndicator` for genuine background work | loops while composed | 3.5 KB / 0.5 KB | Original (Lucento) |

The `concerned` expression is deliberately supportive — inner brows lift, the eyes glance down, the mouth softens to
a calm line (still turned up a touch), the rays settle and the head nods slowly. No frown, tears or red. Preview:
`animation-preview-mascot-concerned.png`; net-flow glyphs: `animation-preview-netflow.png` (lottie-web renders).

Total: 19 files, ≈200 KB uncompressed, ≈20 KB compressed (APK entries are deflated). Release APK/AAB size impact
including the Lottie library itself is **not yet measured** — the CI "Inspect release artifacts" step reports sizes.
Note: `lottie` declares `androidx.appcompat` as an API dependency, so AppCompat enters the release classpath; R8
removes what is unused.

## Art direction

- Palette only: brand blue `#2563EB` (night `#8AB0FF`), sun `#FBBF24`, cream/white paper by day, navy `#222E50`
  paper by night, green for positive outcomes, coral only as one of the ledger row dots. No gradients, glows,
  confetti, money or robots.
- Easing: soft decelerate (0.33, 0, 0.25, 1) for entrances, a restrained overshoot (y ≤ 1.42) for small pops,
  ease-in-out for loops. Entrances are 0.3–0.6 s; whole illustrations finish in ≤ 2.5 s.
- One-shot files end on a complete, static picture, so a held last frame (and the reduced-motion still) is finished.
- Only lottie-android–native features: shape layers, groups, ellipse/rect/path, fill, stroke, trim paths,
  transforms, parenting and markers. No expressions, effects, mattes, images, text or merge paths.

## Playback rules (enforced by `LucentoLottieAnimation`)

- Reduced motion (system "Remove animations" → `LucentoTheme.reducedMotion`): nothing animates. One-shots show their
  final frame; loops show their first; the mascot shows its still pose with its glow. Confirmations stay on screen for
  the same time, so a save never looks unfinished.
- Loops stop when their composable leaves the screen; one-shots fire `onFinished` once (immediately under reduced motion).
- A file that fails to parse shows a fallback (the mascot sprite, static mark or a progress indicator).
- Compositions are cached by Lottie per raw resource id; only the visible screen's animations are loaded.
- Animations never drive app logic: confirmations are triggered by repository results, after the write.

## Regenerating

See `tools/lottie/README.md`. Any change to a file must be re-rendered and reviewed, and this register updated.

## Third-party assets

None. If one is ever added, record: asset name, creator, source page, exact licence, whether commercial
distribution is permitted, attribution requirements (and add them to Settings › About), local path and modifications.
