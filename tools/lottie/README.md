# Lucento animation sources

Every Lottie file in `app/src/main/res/raw/` is **original work generated from code in this folder** — no
third-party animation, illustration or template is used. See `docs/design/ANIMATION_ASSETS.md`.

```
cd tools/lottie/gen
python3 -c "import illustrations; illustrations.build('../../../app/src/main/res/raw')"
cd .. && npm install
node render.js sheet.png '#FFFDF8' 160 0,30,60,119 ../../app/src/main/res/raw/empty_goals.json
```

- `lot.py` — minimal Bodymovin 5.7 writer restricted to features lottie-android renders natively
  (shape layers, groups, ellipse/rect/path, fill, stroke, trim paths, transforms, markers). No expressions,
  effects, mattes, images, text layers or merge paths.
- The mascot is no longer a Lottie file: the approved character ships as WebP sprites (see `docs/mascot/`).
- `illustrations.py` — onboarding, four empty states, receipt scanning (day + night), saved check, milestone, loading dots.
- `render.js` — renders frames with lottie-web in headless Chromium for review. Rendering in lottie-web is a
  proxy: final appearance must still be checked on a device with lottie-android.
