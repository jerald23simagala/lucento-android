# Asset QA Checklist
Run for every PNG. Record results in `MASCOT_ASSET_MANIFEST.json` and `reports/final_validation_report.md`.

## Automated (`python scripts/mascot_assets.py inspect <png>`)
| Check | Pass criterion |
|---|---|
| Dimensions | 2048×2048 for character masters |
| Colour mode | RGBA |
| Transparency | `alpha_genuine` true; transparent_pct > 5%; alpha min 0 |
| Checkerboard | `checkerboard_score` < 0.6 |
| Clipping | `edge_touch` false; `min_padding_px` ≥ 200 |
| Canvas alignment | content_bbox within ±40 px of siblings |
| Glow levels | monotonic peak alpha, over_plan > 0 (unit test) |
| Crystal levels | mean crystal luma decreases thriving→over_plan per pose (`reports/build_v3_log.json`) |
| Key-colour fringe | count of pixels with g − max(r,b) > 8 (v2: 34–124 per 2048² asset; v1 had ~1,800 at >20) |
| Missing pixels (heuristic) | `interior_gap_px_sampled` comparable to siblings (gaps between torch & body are normal) |

## Visual (human or agent actually viewing renders — `preview` command)
| Check | How |
|---|---|
| Light & dark previews | open `assets/previews/individual/*__on_light.png` / `__on_dark.png` |
| Hair-edge quality | zoom 100% on dark bg; look for green/olive fringe, chopped strands (`assets/previews/qa_faces_concerned_overplan_on_dark_v3.png`) |
| Clothing integrity | scarf/satchel complete, cream not eaten away |
| Torch & crystal | handle, collar, crystal facets intact, not clipped |
| Glow preservation | crystal highlight present; glow layer composite reviewed |
| Colour spill | no green tint on edges/cream areas |
| Missing highlights/holes | eye speculars, body highlight present |
| Consistency | contact sheet: same scale, camera, design across states |
| Small-size readability | view at 64 px thumbnail |
| Design compliance | no ring, no symbols, eyes small, no blush; tiers 4–5 facial hair + tears per D-v3 (concerned lighter than over_plan), tiers 1–3 clean-shaven, no tears |

A contact sheet never replaces per-asset inspection. `approved` requires all checks pass + human art-direction sign-off.
