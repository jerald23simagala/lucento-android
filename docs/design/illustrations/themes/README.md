# Theme scenery — sources

Five painterly panoramas (each 1774×887, PNG), one per theme, generated 2026-11-05 with ChatGPT image generation from
the prompts in the "Lucento Theme Studio" exploration (https://claude.ai/artifact/3xambaCBLTQPEbwcb6bYnX —
"Scenery"). The approved "A Brighter Little World" panorama in the parent folder is untouched and still ships (the
onboarding welcome in Sunrise, goal pictures).

| Source | Theme | Scene | Shipped as |
|---|---|---|---|
| `scenery_sunrise_source.png` | Sunrise | Golden sun over hazy blue ridges, ivory-to-tangerine sky | `scenery_sunrise_panorama` |
| `scenery_midnight_source.png` | Midnight (dark only) | Champagne crescent moon, sparse stars, indigo ridges | `scenery_midnight_panorama` |
| `scenery_grove_source.png` | Grove | Pine treeline and sage hills at golden hour | `scenery_grove_panorama` |
| `scenery_mono_source.png` | Mono | Fog-layered slate ridges, no sun or moon | `scenery_mono_panorama` |
| `scenery_aurora_source.png` | Aurora | Teal and magenta aurora ribbons over violet ridges | `scenery_aurora_panorama` |

Export: the full frame (crop box 0, 0, 1774, 887), resized to 1200×600 with Lanczos, WebP quality 88, method 6, into
`app/src/main/res/drawable-nodpi/` (11–24 KB each). The app frames them in code (`HeroHorizon`, `MascotScene`, the
theme picker), so one export per theme serves every placement; all five share one composition (ridges in the lower
third), which is what lets one framing work for all of them. Re-export from these sources — never edit the WebPs.

Placements (docs/DESIGN.md › Illustrations): a horizon strip below hero-panel content, Lucento's empty-state vignette,
the onboarding welcome (non-Sunrise themes) and the theme picker. Never behind text, figures, lists, forms or
settings. Keep text, numbers, logos and UI out of the artwork; keep the lower third (the ridges) calm.
