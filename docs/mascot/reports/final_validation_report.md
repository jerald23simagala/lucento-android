# Final Validation Report — Round 3 (2026-10-23, ~14:30 PDT)

## Changes this round (user request: struggling/overwhelmed tiers get unkempt facial hair and crying)
1. **Design decision D-v3** recorded in `MASCOT_DESIGN_SPEC.md` and `prompts/wellness_tiers.md`.
2. **concerned, all 7 poses, regenerated:** light scruffy stubble plus glossy teary eyes / a single tear. Sleeping has messier hair and one tear track (this fixes the earlier "hair too neat" issue).
3. **over_plan, all 7 poses, regenerated:** fuller ragged beard (visibly heavier than concerned) plus tears on the cheeks. Sleeping has tear tracks.
4. **getting_tight_sleeping** regenerated with messier hair (acceptable, mild). **getting_tight_success** regenerated: the first retry still had blush and was rejected (`assets/source/rejected_generations/getting_tight_success_v3a_rejected_blush.jpg`); the second retry has no visible blush.
5. **over_plan_caution** fraying is now moderate and consistent. **over_plan_thinking** still has heavier sleeve/hem fraying, and **over_plan_greeting_happy** has more frayed scarf ends. Both stay needs_cleanup.
6. Same pipeline (`scripts/build_v3.py` reuses the build_v2 key → decontaminate → crystal level → premultiplied 2× upscale), same LEVEL table, 2048 canvas and centre pivot. The 19 unchanged combinations were reprocessed from their v2 raws (output identical in method). All v2 outputs were moved to `assets/backup_v2/`.
7. These are AI regenerations, not edits of the v2 images; small identity drift between poses is possible.

## Automated results (all 35 v3 masters)
- 2048×2048 RGBA, genuine alpha, no edge touch, no checkerboard: **35/35 pass**.
- Key-colour pixels (g−max(r,b)>8): 34–179 per asset (low, consistent with round 2).
- Crystal mean luma is monotonic thriving > steady > getting_tight > concerned > over_plan for **every pose**: thriving 221.9–226.9 · steady 194.5–194.6 · getting_tight 161.1–161.2 · concerned 132.5–132.6 · over_plan 110.5 (`reports/build_v3_log.json`).
- Unit tests: 13/13 OK.

## Visual review (viewed)
- `assets/previews/contact_sheet_v3_concerned_overplan.png` and `_dark.png`, plus `qa_faces_concerned_overplan_on_dark_v3.png` (face crops on dark). Tears and stubble read clearly, with no green fringe on beard or tear edges. Faces are soft and sympathetic, not grotesque.
- concerned stubble varies by pose: greeting_happy reads more like a defined moustache/goatee, while success and sleeping are faint. over_plan beards are consistently full.
- Tears are small pale-blue highlights. At small Android sizes (<96dp) they may be barely visible.

## Verdict
33 prototype + 2 needs_cleanup characters. Nothing is approved; human art-direction sign-off is still required. Flattened and 1024-native upscaled (not native hi-res). No legal clearance implied.

---
Round 2 report follows for history (the v2 files it references are now in `assets/backup_v2/`).

# Final Validation Report — Round 2 (2026-10-23, ~12:15 PDT)

Provenance: concept sheet is **user-created** (user's own original concept, as stated by the user; not legally reviewed). All 35 character masters are AI-generated from it, then processed by `scripts/build_v2.py`. v1 masters were moved (not deleted) to `assets/backup_v1/`.

## Changes this round
1. **Olive fringe fixed on all assets**: new `decontaminate` (edge colour propagated from interior; alpha untouched) + premultiplied (RGBa) LANCZOS upscale. Key-colour pixels (g−max(r,b)>8) per asset: 34–124 (v1: ~1,800 at the stricter >20). Dark-background hair-edge zoom grid for all 35: `assets/previews/qa_hair_edges_on_dark_v2.png` — viewed; no olive/green rim visible. over_plan hero additionally checked at 100% crop (`qa_crop_over_plan_hair_on_dark_v2.png`).
2. **Blush removed** by regeneration: thriving hero, concerned hero, thriving success (viewed: no blush).
3. **Steady distinct from thriving**: regenerated with smooth hair (no cowlick), slightly softer scarf; crystal levelled to 0.80. The v1 "steady" pose raws, which visually matched thriving (cowlick, pristine), were **reassigned to thriving** (greeting/thinking/pointing/caution).
4. **Crystal order fixed** (mean crystal luma, measured with the pre-dim mask, identical across poses within a tier): thriving 221.8–226.6 · steady 194.5–194.6 · getting_tight 161.1–161.2 · concerned 132.3–132.6 · over_plan 110.5. Monotonic for every pose; over_plan visible (viewed). Body pixels unchanged (unit test).
5. **over_plan clothing** regenerated: intact, faded scarf with a few frayed threads and neat stitched patches (hero, greeting, pointing, success, sleeping read dignified). thinking & caution came out more frayed → needs_cleanup.
6. **Sleeping**: all 5 tiers now sit settled on the ground, eyes closed, torch leaning low, no Zzz.
7. **All 35 tier × pose combinations generated** (24 new + regenerations). Rejected generations (cowlick leak on steady thinking/success) kept in `assets/source/rejected_generations/`.

## Automated results (all 35 masters)
All: 2048×2048 RGBA, genuine alpha (min 0/max 255), edge_touch false, min padding ≥ 310 px, checkerboard 0.0.

| tier | pose | dims/mode | transparent | content bbox | min pad | checker | key-px | crystal luma |
|---|---|---|---|---|---|---|---|---|
| thriving | hero_neutral | 2048×2048 RGBA | 73.08% | (404, 315, 1728, 1647) | 315 | 0.0 | 46 | 224.0→224.0 |
| thriving | greeting_happy | 2048×2048 RGBA | 73.11% | (398, 317, 1724, 1651) | 317 | 0.0 | 44 | 226.9→226.9 |
| thriving | thinking | 2048×2048 RGBA | 73.8% | (423, 323, 1722, 1659) | 323 | 0.0 | 52 | 225.3→225.3 |
| thriving | pointing | 2048×2048 RGBA | 73.01% | (371, 315, 1726, 1649) | 315 | 0.0 | 63 | 226.7→226.7 |
| thriving | success | 2048×2048 RGBA | 73.0% | (376, 317, 1726, 1655) | 317 | 0.0 | 62 | 223.9→223.9 |
| thriving | caution | 2048×2048 RGBA | 73.36% | (411, 317, 1721, 1649) | 317 | 0.0 | 59 | 225.3→225.3 |
| thriving | sleeping | 2048×2048 RGBA | 73.2% | (443, 363, 1638, 1697) | 351 | 0.0 | 35 | 221.9→221.9 |
| steady | hero_neutral | 2048×2048 RGBA | 74.38% | (482, 395, 1729, 1656) | 319 | 0.0 | 42 | 221.7→194.5 |
| steady | greeting_happy | 2048×2048 RGBA | 73.92% | (367, 391, 1730, 1663) | 318 | 0.0 | 49 | 219.9→194.6 |
| steady | thinking | 2048×2048 RGBA | 74.1% | (496, 395, 1726, 1686) | 322 | 0.0 | 47 | 217.6→194.6 |
| steady | pointing | 2048×2048 RGBA | 73.6% | (359, 373, 1732, 1669) | 316 | 0.0 | 34 | 221.4→194.5 |
| steady | success | 2048×2048 RGBA | 73.83% | (336, 393, 1730, 1663) | 318 | 0.0 | 54 | 219.8→194.6 |
| steady | caution | 2048×2048 RGBA | 74.48% | (485, 397, 1728, 1659) | 320 | 0.0 | 59 | 219.9→194.6 |
| steady | sleeping | 2048×2048 RGBA | 73.7% | (448, 417, 1630, 1705) | 343 | 0.0 | 60 | 220.9→194.5 |
| getting_tight | hero_neutral | 2048×2048 RGBA | 73.49% | (427, 333, 1727, 1649) | 321 | 0.0 | 83 | 220.6→161.1 |
| getting_tight | greeting_happy | 2048×2048 RGBA | 72.59% | (351, 328, 1740, 1665) | 308 | 0.0 | 116 | 218.1→161.2 |
| getting_tight | thinking | 2048×2048 RGBA | 73.13% | (424, 331, 1728, 1673) | 320 | 0.0 | 87 | 218.6→161.2 |
| getting_tight | pointing | 2048×2048 RGBA | 72.97% | (396, 332, 1728, 1661) | 320 | 0.0 | 94 | 218.5→161.2 |
| getting_tight | success | 2048×2048 RGBA | 73.01% | (359, 331, 1729, 1653) | 319 | 0.0 | 103 | 218.7→161.2 |
| getting_tight | caution | 2048×2048 RGBA | 73.24% | (424, 331, 1730, 1657) | 318 | 0.0 | 105 | 218.7→161.2 |
| getting_tight | sleeping | 2048×2048 RGBA | 73.61% | (448, 413, 1630, 1707) | 341 | 0.0 | 51 | 219.4→161.1 |
| concerned | hero_neutral | 2048×2048 RGBA | 73.16% | (407, 339, 1726, 1654) | 322 | 0.0 | 119 | 210.6→132.6 |
| concerned | greeting_happy | 2048×2048 RGBA | 72.78% | (392, 341, 1728, 1663) | 320 | 0.0 | 116 | 210.0→132.6 |
| concerned | thinking | 2048×2048 RGBA | 72.95% | (409, 339, 1725, 1669) | 323 | 0.0 | 82 | 210.8→132.6 |
| concerned | pointing | 2048×2048 RGBA | 72.53% | (375, 339, 1726, 1669) | 322 | 0.0 | 103 | 210.3→132.6 |
| concerned | success | 2048×2048 RGBA | 72.93% | (407, 339, 1728, 1660) | 320 | 0.0 | 107 | 209.7→132.7 |
| concerned | caution | 2048×2048 RGBA | 73.24% | (409, 339, 1726, 1661) | 322 | 0.0 | 124 | 211.5→132.6 |
| concerned | sleeping | 2048×2048 RGBA | 73.55% | (447, 415, 1634, 1707) | 341 | 0.0 | 53 | 218.7→132.3 |
| over_plan | hero_neutral | 2048×2048 RGBA | 72.72% | (391, 312, 1728, 1659) | 312 | 0.0 | 80 | 197.7→110.5 |
| over_plan | greeting_happy | 2048×2048 RGBA | 71.77% | (391, 310, 1729, 1683) | 310 | 0.0 | 101 | 197.5→110.5 |
| over_plan | thinking | 2048×2048 RGBA | 71.51% | (389, 311, 1730, 1717) | 311 | 0.0 | 123 | 198.1→110.5 |
| over_plan | pointing | 2048×2048 RGBA | 71.73% | (347, 311, 1732, 1681) | 311 | 0.0 | 78 | 198.0→110.5 |
| over_plan | success | 2048×2048 RGBA | 71.97% | (388, 310, 1727, 1675) | 310 | 0.0 | 97 | 198.9→110.5 |
| over_plan | caution | 2048×2048 RGBA | 72.24% | (389, 310, 1729, 1691) | 310 | 0.0 | 114 | 198.5→110.5 |
| over_plan | sleeping | 2048×2048 RGBA | 73.93% | (421, 375, 1623, 1695) | 353 | 0.0 | 94 | 201.3→110.5 |

Glow layers (5): 1024² RGBA, peak alpha 254/203/152/106/71, monotonic.

Unit tests: `python3 scripts/test_mascot_assets.py` → **13 tests, all OK** (adds decontamination keeps alpha & removes green, crystal dimming touches crystal only and stays visible, crystal levels monotonic).

## 12-point checks
| # | Check | Result |
|---|---|---|
| 1 dims | 35/35 pass (2048², resampled ×2 from 1024 native) |
| 2 mode | 35/35 RGBA |
| 3 transparency | 35/35 genuine |
| 4 light render | viewed: contact_sheet_v2_light + individual previews generated (70 files) |
| 5 dark render | viewed: contact_sheet_v2_dark + hair-edge zoom grid; fringe resolved |
| 6 hair/clothing edges | intact; cream scarf and frayed threads preserved |
| 7 body/face | intact, no holes seen |
| 8 torch/crystal | not clipped (bbox + visual) |
| 9 checkerboard | none |
| 10 holes/highlights | eye speculars & crystal facets present |
| 11 consistency | tier progression reads clearly (see contact sheets). Exceptions: getting_tight_sleeping & concerned_sleeping hair too neat; getting_tight_success faint blush; over_plan_thinking/caution heavier fraying |
| 12 manifest | rebuilt (`scripts/build_manifest.py`): 30 characters prototype, 5 needs_cleanup, 5 glow prototype, 16 rig layers missing, 2 source |

Visual review method: every generation viewed full-frame at generation time; all 35 in light/dark 5×7 contact sheets; all 35 hair edges in the dark zoom grid; selected 100% crops (over_plan hair, concerned_sleeping crystal). Not every asset was inspected at 100% over its entire area.

## Verdict
None approved. 30 character prototypes are usable for in-app prototyping; production still needs native-resolution art (current = 1024 upscaled), fixes for the 5 flagged assets, and real rig layers.
