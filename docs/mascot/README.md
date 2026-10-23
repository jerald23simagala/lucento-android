# Lucento Mascot Studio (`docs/mascot`)
Self-contained production workspace for Lucento's mascot: a golden-orange celestial orb with tousled hair, cream scarf, satchel and crystal torch, based on the **user-created concept sheet**. **No asset is approved yet** — approval needs human art-direction sign-off.

## Contents
| Path | What |
|---|---|
| `MASCOT_DESIGN_SPEC.md` | canonical identity, tiers, prohibited directions (ring rejected), provenance & commercial considerations |
| `MASCOT_ASSET_PIPELINE.md` | generation → key → decontaminate → crystal level → canvas → QA → package, real commands |
| `MASCOT_ANIMATION_GUIDE.md` | idle, greeting, thinking, guidance, success, caution (+ sleeping), transitions, reduced motion |
| `MASCOT_ASSET_QA.md` | automated vs visual checks |
| `MASCOT_ANDROID_HANDOFF.md` | naming, resources, selection, torch, performance (spec only) |
| `MASCOT_ASSET_MANIFEST.json` | machine-readable inventory (rebuilt by `scripts/build_manifest.py`) |
| `skills/lucento-mascot-production/SKILL.md` | reusable agent skill (local file; not globally installed) |
| `prompts/` | character, tiers, expressions, torch/glow prompts |
| `scripts/mascot_assets.py`, `build_v3.py`, `build_manifest.py`, `test_mascot_assets.py` | tooling + tests |
| `assets/source/` | concept sheet (original filename + named copy); raw generator outputs `generated_chroma/` (never modified); `rejected_generations/` |
| `assets/processed/` | v3 keyed RGBA at native 1024² |
| `assets/wellness_tiers/` | `lucento_<tier>_hero_neutral_v3.png` (5), 2048² RGBA |
| `assets/expressions/` | `lucento_<tier>_<pose>_v3.png` (30), 2048² RGBA |
| `assets/layers/torch_glow/` | 5 procedural glow layers |
| `assets/previews/` | `contact_sheet_v3_light/dark.png, contact_sheet_v3_concerned_overplan(_dark).png` (5×7 grid), `qa_faces_concerned_overplan_on_dark_v3.png`, `torch_glow_levels_v2_preview.png`, per-asset light/dark renders in `individual/` |
| `assets/backup_v1/` | superseded round-1 masters, processed files and previews (kept, not deleted) |
| `assets/backup_v2/` | round-2 masters, processed, previews, manifest (moved before v3) |
| `reports/` | `initial_audit.md`, `final_validation_report.md`, `build_v3_log.json` |

No root-level `SKILL.md` on purpose: the single skill lives in `skills/lucento-mascot-production/` to avoid conflicting copies.

## Status (v3)
- **v3 change (user request, design decision D-v3):** concerned = light stubble + teary eyes/single tear; over_plan = fuller ragged beard + tears on cheeks (all 7 poses each). getting_tight sleeping/success regenerated (messier hair; blush removed). v2 files moved to `assets/backup_v2/`.
- **prototype (33 characters + 5 glow):** pass automated checks and visual review; still 1024-native upscaled and flattened.
- **needs_cleanup (2):** over_plan_thinking (sleeve/hem fraying heavier than hero), over_plan_greeting_happy (scarf ends more frayed than hero).
- **missing:** all rig layers (body, hair, eyes, mouths, clothing, arms, torch handle, crystal, shadow). All 35 tier × pose images now exist.

## Inspect / rebuild
`python scripts/mascot_assets.py inspect assets/wellness_tiers/lucento_steady_hero_neutral_v3.png` · `python scripts/build_v3.py` · `python scripts/build_manifest.py` · `python scripts/test_mascot_assets.py`

## Reuse the skill
Tell an agent: "Read `docs/mascot/skills/lucento-mascot-production/SKILL.md` and follow it."

## Regenerate the ZIP
From `docs/mascot` (stays inside the folder; untested on Windows, equivalent tested on the box):
`python -c "import zipfile,os;n='lucento-mascot-studio.new.zip';z=zipfile.ZipFile(n,'w',zipfile.ZIP_DEFLATED);[z.write(os.path.join(r,f),os.path.relpath(os.path.join(r,f),'.')) for r,d,fs in os.walk('.') for f in fs if not f.startswith('lucento-mascot-studio') and not r.replace(os.sep,'/').startswith(('./assets/backup_v','./assets/previews/individual'))];z.close()"` then replace `lucento-mascot-studio.zip` with the `.new.zip` file.

Note: the shipped ZIP **excludes `assets/previews/individual/`** (70 per-asset light/dark renders, ~36 MB) and **`assets/backup_v1/`**, **`assets/backup_v2/`** (superseded files) to stay under the 100 MiB transfer limit; both are present in the folder; previews are regenerable with `python scripts/mascot_assets.py preview <png> assets/previews/individual`.
