# Illustrations — source and crops

`fuji_sunrise_panorama_source.png` (2172×724) is the user-supplied painterly panorama. The app's three backgrounds
are crops of it, exported as WebP (quality 88) to `app/src/main/res/drawable-nodpi/`:

| Resource | Crop (x0, y0, x1, y1) in source px | Size | Used for |
|---|---|---|---|
| `illustration_sunrise_landscape` | 0, 0, 1216, 724 (sun, mountains, lake, trees) | 1216×724 | onboarding welcome |
| `illustration_soft_mountains` | 260, 130, 1476, 724 (mountains, lake, island) | 1216×594 | Home budget hero (behind Lucento) |
| `illustration_japan_fuji` | 1050, 110, 2172, 724 (Mount Fuji) | 1122×614 | Japan travel goal cards |

Re-crop from the source rather than editing the WebPs. Keep text, numbers, logos and UI out of the artwork.

`themes/` holds the five theme landscapes (Sunrise, Midnight, Grove, Mono, Aurora), shipped as
`scenery_<theme>_panorama` — see `themes/README.md` and docs/DESIGN.md › Themes.

`perks/` holds the ten Pro perk-card props (soft-3D, on the cards' flat blue), shipped as `perk_art_<stop>` and
`perk_thumb_<stop>` by `tools/illustrations/export_perk_art.py` — see `perks/README.md`.
