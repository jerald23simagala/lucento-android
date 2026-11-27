# Lucento launch film

Lucento's first official product film: **98.7 seconds, 1920 × 1080, 30 fps**, plus a **vertical 1080 × 1920**
version. It is built in Remotion from real captures of Lucento `main` at `ecec362` (0.5.20 plus the wide-window split,
recaptured 2026-11-27 with scene 09 "Every screen" for tablets and foldables; earlier cuts used 0.5.8, 0.5.14 and
0.5.19) and carries an original synthesized score. Scene 09 shows the split, which reaches people only in the release
after 0.5.20, so the full film goes public once that release is on Play (TRUTH_RULES T42, Mission Control OT-69); the
30 s and 15 s cuts show phones only and can go out now. In the vertical version every caption is centred.
30-second and 15-second cuts, in both shapes, come from the same source. Since 2026-11-24 every version is narrated
(ElevenLabs; voice Laurel, American English, since 2026-11-25, after Rianne on the first renders), and the full film
also stays available music-led for muted feeds.

| Deliverable | Where |
|---|---|
| Remotion project | `lucento-studio/video/` · film source `src/launch-film/` · compositions `LaunchFilm` (music-led), `LaunchFilmNarrated`, `LaunchFilm30`, `LaunchFilm15`, the vertical `LaunchFilmVertical`, `LaunchFilmNarratedVertical`, `LaunchFilm30Vertical`, `LaunchFilm15Vertical`, plus each scene under `LaunchFilm-Scenes` |
| Rendered films | `lucento-studio/video/out/` (git-ignored); copies in `Downloads/lucento-launch-film/` |
| Feature discovery report | [`../LAUNCH_VIDEO_FEATURE_DISCOVERY.md`](../LAUNCH_VIDEO_FEATURE_DISCOVERY.md) |
| Feature-to-visual mapping (tiers 1–4) | [`FEATURE_VISUAL_MAP.md`](FEATURE_VISUAL_MAP.md) |
| Final storyboard | [`STORYBOARD.md`](STORYBOARD.md) |
| Voiceover: scripts, timings, voice and the AI labels for posting | [`VOICEOVER.md`](VOICEOVER.md) |
| Asset inventory, sources, rights, staging disclosures | [`ASSET_INVENTORY.md`](ASSET_INVENTORY.md) |
| 30 s and 15 s cuts; vertical versions | [`CUTDOWNS.md`](CUTDOWNS.md) |
| Future feature videos | [`FUTURE_VIDEOS.md`](FUTURE_VIDEOS.md) |

## Preview and render

```bash
cd lucento-studio/video
npm ci                                   # first time only
npm run studio                           # opens Remotion Studio; pick LaunchFilm
npm run render:film                      # → out/lucento-launch-film.mp4, music-led (also render:film-vo, render:film30, render:film15)
npm run render:vertical                  # → out/lucento-launch-film-vertical.mp4 (also render:vertical-vo, render:vertical30, render:vertical15)
npm run score                            # regenerate the music (needs Python with numpy and scipy)
```

On 2026-11-19 the master took 21 minutes to render on this PC with other apps open, the vertical 9 minutes and each cut
3–6 minutes. Shut the screenshot emulator down first: it holds about 2.5 GB of memory.

- Rendering stills of the cut-downs needs `--muted`. Remotion's audio mixer fails on image-only renders of those
  compositions; video renders are fine.
- To check frames, use `npx remotion render src/index.ts LaunchFilm out/frames --frames=0,80,2800 --image-format=jpeg --props='{"withAudio":false}'`.

## Changing things

| To change | Edit |
|---|---|
| Any on-screen word | `src/launch-film/copy.ts` (the only place copy lives; re-check claims against the discovery report) |
| The CTA line or the Pro note | `copy.ts` → `finale` |
| Typeface | `FONT` in `src/launch-film/theme.ts` |
| Music | `tools/compose_score.py` (plans for all three lengths), then `python tools/compose_score.py`. To use a licensed track instead, replace `public/launch-film/audio/score.wav` |
| Narration | A take through `tools/voiceover.py`, its line frames in `VO_AT` (`src/launch-film/audio/Voiceover.tsx`); one new line can be read in context and moved in with `tools/voiceover_insert.py` (`VOICEOVER.md`) |
| Screens (after an app update) | Copy `public/launch-film/screens` to `out/screens-before`, re-capture with `tools/capture/` (see its README), run `python tools/prepare_screens.py out/capture`, then `python tools/remap_boxes.py out/screens-before public/launch-film/screens`: it reports every crop box that moved or changed. The tablet and foldable screens: `tools/capture/film_wide.sh` on the tablet AVD (the same showcase state), then `python tools/prepare_wide.py <its out dir>` |
| Scene timing | `src/launch-film/timeline.ts` and the matching `SCENES` in `tools/compose_score.py` (keep cuts on bar lines: 80 frames) |
