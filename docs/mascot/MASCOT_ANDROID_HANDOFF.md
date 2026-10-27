# Android Handoff (specification only — no app code was written or modified)

## Naming
`mascot_<tier>_<pose>` e.g. `mascot_steady_greeting_happy`, `mascot_over_plan_hero_neutral`; glow: `mascot_torch_glow_<tier>` (or generate procedurally). Lowercase snake_case (Android resource rules).

## Resource organisation (suggested)
- Ship converted lossless/near-lossless WebP in `drawable-nodpi/` at 1024² (or density buckets 512/768/1024) — 2048² masters stay in `docs/mascot/assets/`.
- Keep masters out of the APK.

## Tier selection
Map app wellness enum → tier id: THRIVING→thriving, STEADY→steady, GETTING_TIGHT→getting_tight, CONCERNED→concerned, OVER_PLAN→over_plan. Thresholds are a product decision (not defined here).

## Expression selection
Lookup `(tier, pose)`. All 35 tier × pose combinations exist as v3 prototypes (`lucento_<tier>_<pose>_v3.png`); 2 are flagged needs_cleanup (over_plan thinking/greeting fraying) in the manifest. Fallback if a file is withheld: `(tier, hero_neutral)`.

## Independent torch brightness
Draw a radial gradient (or `mascot_torch_glow_<tier>`) **behind** the character sprite, centred at (1620/2048, 660/2048) of the sprite bounds, radius ≈ 0.17 × sprite width; animate alpha 1.00/0.80/0.60/0.42/0.28. Never tint/dim the character bitmap. The baked crystal is already pre-levelled per tier in v2 (monotonic: ~224/195/161/133/111 mean luma); the glow layer adds the light around it. Sleeping poses: crystal centre ≈ (1535/2048, 830/2048).

## Animation preparation
Pivot = sprite centre; feet baseline ≈ 0.805 × height. Pose changes = cross-fade. Durations/easing in `MASCOT_ANIMATION_GUIDE.md`. For future rigging (Lottie/Rive) separate layers are required — not yet available.

## Reduced motion
Honour system animator scale and an in-app toggle: static pose, instant glow.

## Performance
Downsample to view size, cache decoded bitmaps per tier, avoid 2048² decodes, single compositing layer, stop loops when off-screen.
