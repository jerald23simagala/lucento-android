---
name: lucento-mascot-production
description: Produce, recover, validate, document and package Lucento mascot artwork (golden-orange celestial orb with tousled hair, cream scarf, satchel and crystal torch) for the Lucento Android finance app. Use for any mascot generation, background removal, transparency repair, wellness-tier/expression variants, glow layers, animation prep, manifest or QA work in docs/mascot.
---
# Lucento Mascot Production (local skill — a Markdown file in this repo, not a globally installed skill)

Working root: `docs/mascot/` only. Do not touch other repo files, git, or global installs.

## 1. Know the character (read MASCOT_DESIGN_SPEC.md first)
Golden-orange glossy orb; brown tousled hair (top cowlick = thriving only; steady smooth; lower tiers progressively messier); small understated eyes; cream scarf; brown leather satchel with gold buttons + strap; small hands; wooden torch with brass collar and faceted pale-gold crystal held in its left hand (viewer's right); three-quarter view facing viewer-left; soft premium 3D render.
**Never:** orbital ring (rejected), emoji look, big eyes, blush as feature, muscles/fists, excess sparkles, floating `?`/`Zzz`/`!`, random outfit changes. Facial hair + tears only on concerned (light stubble, single tear) and over_plan (ragged beard, cheek tears) per design decision D-v3.
Tiers thriving→steady→getting_tight→concerned→over_plan express circumstance via clothing wear, hair messiness, torch glow, posture — never shame, dirt, fear.

## 2. Generate consistent variations
- For tier × pose: attach ONLY that tier's hero_neutral raw as reference (extra references from other tiers leak hair/clothing). For new tiers/designs: attach BOTH the concept sheet (`assets/source/lucento_concept_sheet_reference.png`) and the anchor (`assets/source/generated_chroma/thriving_hero_neutral_chroma.jpg`). Start prompt with "Same exact character as the first reference image, same render style, camera, scale and placement".
- Use prompt blocks in `prompts/`. One character per image, 1:1, flat #00FF00 (magenta if green appears in the design), no shadow/glow halo/text.
- Save raw output immediately to `assets/source/generated_chroma/` (never overwrite).
- Look at every output. Regenerate if: ring, symbols, cropped torch, wrong outfit, scale drift >5%.

## 3. Inspect before trusting anything
`python scripts/mascot_assets.py inspect <files>` → dims, mode, alpha stats, bbox, edge contact, checkerboard score.
- RGBA ≠ transparent: require alpha min 0 and real transparent area.
- Baked checkerboard: periodic two-tone neutral tiles in RGB (score ≥0.6). Don't repeatedly segment such sheets; use as reference and regenerate.

## 4. Remove backgrounds non-destructively
- Key by **key-hue dominance only** (`key` command). Never threshold by brightness/saturation: cream scarf, gold highlights, pale hair glints and crystal glow look like background to naive tools.
- Output to a new file; sources stay untouched (`safe_out` refuses overwrites).
- Then run `decontaminate` (edge colour propagation from the interior; alpha untouched). Resample in premultiplied RGBa (`normalize` does this) — straight-alpha LANCZOS creates light halos.
- Check edges on DARK backgrounds at 100% for every asset (build a crop grid): green/olive fringe → fix with decontamination; never by deleting pale pixels.
- Check hair tips, scarf frays, fingers, torch collar, crystal facets, eye highlights for holes.

## 5. Scale, canvas, pivot
2048² RGBA, pivot at canvas centre, feet ≈ y 1650, ≥200 px padding. Apply ONE identical transform to all assets from the same generator size. Record resampling honestly (current masters = 1024 native ×2 LANCZOS).

## 6. Glow & layers
The baked crystal is levelled per tier with `set_crystal_level` (monotonic luma ~224/195/161/133/111; body untouched); verify with `crystal_luma(img, mask_from_undimmed)`. Torch glow is a separate layer: `glow` command → `assets/layers/torch_glow/torch_glow_<tier>.png` (peak α 1.00/0.80/0.60/0.42/0.28), placed behind character at (1620,660), ~700 px. Never darken the body to dim the torch. Only claim layers that physically exist as files.

## 7. Animation prep
Pose swaps via cross-fade; transforms around pivot; durations/easing in MASCOT_ANIMATION_GUIDE.md; reduced-motion fallback mandatory.

## 8. Validate (MASCOT_ASSET_QA.md)
Automated: inspect + `python scripts/test_mascot_assets.py`. Visual: `preview` (light/dark) and `contact`; actually open the images. A contact sheet does not replace per-asset review.

## 8b. Batch build
`python scripts/build_v3.py` then `python scripts/build_manifest.py` (edit SRC / ISSUES tables inside them).

## 9. Manifest
`MASCOT_ASSET_MANIFEST.json`: statuses source/prototype/needs_cleanup/approved/missing. `approved` only after all QA + human sign-off. Refresh measurements with `manifest` command; record provenance (user-supplied / AI-regenerated / procedural / edited) and limitations.

## 10. Docs & packaging
Update docs in place (README, spec, pipeline, animation, QA, handoff, prompts, reports). Package `lucento-mascot-studio.zip` from `docs/mascot` excluding the zip itself; verify it lists the real files.

## 11. Honesty rules
Never claim success without evidence (command output, measured stats, viewed renders). Never call regenerated art an exact recovery. Never call upscaled output native hi-res. Mark untested commands as untested. No legal guarantees about AI-art rights.
