# Asset Pipeline
All commands run from `docs/mascot/`. Requires Python 3.9+, Pillow, numpy (`python -m pip install --user pillow numpy` or a venv — never global installs in the repo machine without approval).
**Tested** = executed on the production box (Linux, Python 3, Pillow 12.3.0, numpy 2.5.4) on 2026-10-23. Not yet executed on Windows (**untested there**; paths use `/` which PowerShell accepts).

## 1. Source requirements
Reference sheet in `assets/source/` (never modified). Prefer genuine RGBA individual art; otherwise generator output on flat chroma key at ≥1024².

## 2. Inspect sources (tested)
```
python scripts/mascot_assets.py inspect assets/source/lucento_concept_sheet_reference.png
python scripts/mascot_assets.py checker assets/source/*.png
```
Score ≥0.6 ⇒ baked checkerboard: do NOT segment it repeatedly — regenerate.

## 3. Generate (manual, image tool)
Prompts in `prompts/`. Flat #00FF00 (or #FF00FF) background, no shadow/glow halo. Save raw output to `assets/source/generated_chroma/<tier>_<pose>_chroma.jpg` (raw outputs are sources: never overwrite).

## 4. Key (tested)
```
python scripts/mascot_assets.py key assets/source/generated_chroma/steady_thinking_chroma.jpg assets/processed/steady_thinking_native1024.png
```
Algorithm: alpha only from **key-hue dominance** (`g − max(r,b)`); pixels with dominance ≤10 are always opaque, so cream, white, gold, yellow and gray are never removed. Edge un-mixing `F=(C−(1−a)K)/a` + green despill cap. The v1 residue (olive fringe from JPEG chroma bleed) is fixed in v2 by `decontaminate` + premultiplied resampling.

## 4b. Round-2 batch build (tested)
```
python scripts/build_v3.py        # key -> decontaminate -> crystal level -> 2048 canvas, writes *_v3 files + reports/build_v3_log.json
python scripts/build_manifest.py  # rebuild manifest from disk + curated QA notes
```
- `decontaminate`: semi-transparent pixels and opaque pixels within 2 px of the soft edge get RGB propagated from solid interior colour (alpha untouched) — removes JPEG-bled olive/green rims.
- `set_crystal_level`: dims crystal pixels only to tier level (see prompts/torch_and_glow.md).
- Upscale is done in premultiplied RGBa space to avoid edge halos.

## 5. Canvas & pivot (tested)
```
python scripts/mascot_assets.py normalize assets/processed/steady_thinking_native1024.png assets/expressions/lucento_steady_thinking_2048.png --size 2048 --scale 2
```
Same uniform transform for all assets (no per-image re-fit) ⇒ generator placement preserved. `--scale 2` is a LANCZOS **upscale** of 1024 output; record it as resampled. Pivot = canvas centre.

## 6. Glow (tested)
```
python scripts/mascot_assets.py glow assets/layers/torch_glow --size 1024
```

## 7. Previews & contact sheets (tested)
```
python scripts/mascot_assets.py preview assets/expressions/lucento_steady_thinking_2048.png assets/previews/individual
python scripts/mascot_assets.py contact assets/previews/contact_sheet_dark.png assets/wellness_tiers/*.png assets/expressions/*.png --bg dark
python scripts/mascot_assets.py thumb assets/expressions/lucento_steady_thinking_2048.png assets/previews/thumb.png --size 256
```
(PowerShell does not expand `*.png` for Python; use `(Get-ChildItem assets\expressions\*.png).FullName` — untested.)

## 8. Manifest (tested)
```
python scripts/mascot_assets.py manifest MASCOT_ASSET_MANIFEST.json assets/expressions/lucento_steady_thinking_2048.png --root .
```
Refreshes measured fields only; status changes are manual.

## 9. Tests (tested: 13/13 pass, round 2)
`python scripts/test_mascot_assets.py`

## Naming
`lucento_<tier>_<pose>_2048.png` (masters), `<tier>_<pose>_native1024.png` (processed native), `<tier>_<pose>_chroma.jpg` (raw), `torch_glow_<tier>.png`.

## Export spec
PNG, RGBA, 8-bit, sRGB, 2048², ≥200 px padding, no text/scenery/shadow.

## Recovery from defective art
1 usable originals → 2 sheet with genuine alpha → 3 conservative key/removal → 4 light/dark review → 5 small repair → 6 regenerate. Never luminance/saturation-threshold removal. Never upscale an extraction and call it original.

## Package
See README "Regenerate the ZIP" (writes inside docs/mascot, excludes existing zips).
