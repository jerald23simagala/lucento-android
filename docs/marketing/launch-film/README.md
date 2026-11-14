# Lucento launch film

Lucento's first official product film: **93.3 seconds, 1920 × 1080, 30 fps**, plus a **vertical 1080 × 1920**
version. It is built in Remotion from real captures of Lucento 0.5.8 and carries an original synthesized score.
30-second and 15-second cuts, in both shapes, come from the same source.

| Deliverable | Where |
|---|---|
| Remotion project | `lucento-studio/video/` · film source `src/launch-film/` · compositions `LaunchFilm`, `LaunchFilm30`, `LaunchFilm15`, the vertical `LaunchFilmVertical`, `LaunchFilm30Vertical`, `LaunchFilm15Vertical`, plus each scene under `LaunchFilm-Scenes` |
| Rendered films | `lucento-studio/video/out/` (git-ignored); copies in `Downloads/lucento-launch-film/` |
| Feature discovery report | [`../LAUNCH_VIDEO_FEATURE_DISCOVERY.md`](../LAUNCH_VIDEO_FEATURE_DISCOVERY.md) |
| Feature-to-visual mapping (tiers 1–4) | [`FEATURE_VISUAL_MAP.md`](FEATURE_VISUAL_MAP.md) |
| Final storyboard | [`STORYBOARD.md`](STORYBOARD.md) |
| Voiceover script (timed) | [`VOICEOVER.md`](VOICEOVER.md) |
| Asset inventory, sources, rights, staging disclosures | [`ASSET_INVENTORY.md`](ASSET_INVENTORY.md) |
| 30 s and 15 s cuts; vertical versions | [`CUTDOWNS.md`](CUTDOWNS.md) |
| Future feature videos | [`FUTURE_VIDEOS.md`](FUTURE_VIDEOS.md) |

## Preview and render

```bash
cd lucento-studio/video
npm ci                                   # first time only
npm run studio                           # opens Remotion Studio; pick LaunchFilm
npm run render:film                      # → out/lucento-launch-film.mp4 (also render:film30, render:film15)
npm run render:vertical                  # → out/lucento-launch-film-vertical.mp4 (also render:vertical30, render:vertical15)
npm run score                            # regenerate the music (needs Python with numpy and scipy)
```

Rendering takes about 10 minutes on this PC.

- Rendering stills of the cut-downs needs `--muted`. Remotion's audio mixer fails on image-only renders of those
  compositions; video renders are fine.
- To check frames, use `npx remotion render src/index.ts LaunchFilm out/frames --frames=0,80,2640 --image-format=jpeg --props='{"withAudio":false}'`.

## Changing things

| To change | Edit |
|---|---|
| Any on-screen word | `src/launch-film/copy.ts` (the only place copy lives; re-check claims against the discovery report) |
| The CTA line or the Pro note | `copy.ts` → `finale` |
| Typeface | `FONT` in `src/launch-film/theme.ts` |
| Music | `tools/compose_score.py` (plans for all three lengths), then `python tools/compose_score.py`. To use a licensed track instead, replace `public/launch-film/audio/score.wav` |
| Narration | Record `VOICEOVER.md`, add the WAV to `public/launch-film/audio/`, and add an `<Audio>` at frame 0 in `src/launch-film/audio/Soundtrack.tsx` |
| Screens (after an app update) | Re-capture with `tools/capture/` (see its README), then `python tools/prepare_screens.py out/capture`. Crop boxes in the scenes assume the 0.5.8 layouts |
| Scene timing | `src/launch-film/timeline.ts` and the matching `SCENES` in `tools/compose_score.py` (keep cuts on bar lines: 80 frames) |
