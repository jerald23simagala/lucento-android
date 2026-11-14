# Lucento launch film: cut-downs and vertical adaptation

The cut-downs reuse the master's scenes, so screens, timing and motion stay identical. Each has its own score from the
same synthesizer, so the music still lands both logo moments on a downbeat. Segment boundaries are given in master
frames (30 fps).

## 30-second cut: `LaunchFilm30` (29.3 s, 880 frames)

For YouTube pre-roll, Facebook and Instagram feed, and the Play listing's promo slot if you want it shorter.

| # | Master frames | Length | What it shows |
|---|---|---|---|
| A | 0–160 | 5.3 s | Fragments gather into the crest; "Your entire financial world." |
| B | 160–280 | 4.0 s | The Dashboard rises; "Everything, in one clear view." |
| C | 680–820 | 4.7 s | Say it: the Listening sheet, the spoken words, the result row |
| D | 900–1040 | 4.7 s | All at once: typed in one message, four rows, "4 transactions saved" |
| E | 1680–1800 | 4.0 s | Lucento Shared: your partner saves dinner, and it arrives in your list |
| F | 2600–2800 | 6.7 s | Sunrise, the logo, "See your money clearly.", CTA, Pro note |

The crest lands on bar 1 and the final logo on bar 9. Story: one view → effortless entry → together → clarity.
Voiceover, if used: the 0:06, 0:30.4 and 0:54 lines (first sentence) and the closing line from `VOICEOVER.md`.

## 15-second cut: `LaunchFilm15` (14.7 s, 440 frames)

For TikTok, Reels and Stories (pair it with the vertical layout below), and bumper-style placements.

| # | Master frames | Length | What it shows |
|---|---|---|---|
| A | 0–100 | 3.3 s | The crest forms on the downbeat |
| B | 900–1040 | 4.7 s | All at once: one message, four transactions, saved |
| C | 2560–2760 | 6.7 s | Sunrise into the logo; "See your money clearly."; "Discover Lucento." |

One idea only: "four transactions in one message" is the most surprising, demonstrable promise. The final logo lands
on bar 4.

## Vertical versions (9:16, 1080 × 1920): rendered

`LaunchFilmVertical` (93.3 s), `LaunchFilm30Vertical` and `LaunchFilm15Vertical`. These are not crops. Every scene
reads the composition size and switches to a portrait layout with the same timing, so the score and effects are the
same. What each scene does in portrait:

| Scene | Vertical layout |
|---|---|
| Sky | Panoramas centre-cropped (the ridges stay in the bottom fifth); the night-to-sunrise timing is unchanged |
| 01 | Fragments spread through the tall frame; horizontal lockup at 170 px; headline below at 60 px |
| 02 | Headline at the top (two lines, 96 px); one large phone (width ~620) below; the scroll is unchanged |
| 03 | Headline phrases stacked as three lines at the top; cards fan above and below the phone instead of left and right |
| 04 | Beat label and line at the top; a smaller phone higher up, so the composer's field stays clear of the caption area; a gentle zoom around the field while typing; lifted rows in a 2 × 2 grid over the phone |
| 05 | "82 ready-made categories" above the presets phone; the trio stays in a row of three smaller phones with labels |
| 06 | Headline and the question at the top; the Ask phone slides in over the Insights phone |
| 07 | Headline in two balanced lines; two phones side by side with "You" / "Your partner" chips above; the arc runs between the chips |
| 08 | Theme arc rotated into a vertical stack that fans out; chips in a 2 × 2 grid under the phone |
| 09 | The seven feature names, long ones on two lines |
| 10 | Stacked lockup (crest above the wordmark); headline in two lines; CTA, URL and the Pro note above the ridges |

**Rules:**
- Keep text inside the platform safe zones: 250 px clear at the top and 380 px at the bottom for TikTok and Reels UI.
- Minimum text height is 56 px for headlines and 36 px for supporting lines.
- Each scene's duration stays the same, so the master score fits as is.

Text stays inside the platform safe zones (about 250 px clear at the top and 380 px at the bottom for TikTok and
Reels), and the composer's input field sits above the bottom zone.

## Rendering

```bash
cd lucento-studio/video
npx remotion render src/index.ts LaunchFilm   out/lucento-launch-film.mp4     --codec=h264 --crf=16 --audio-bitrate=320k
npx remotion render src/index.ts LaunchFilm30 out/lucento-launch-film-30s.mp4 --codec=h264 --crf=16 --audio-bitrate=320k
npx remotion render src/index.ts LaunchFilm15 out/lucento-launch-film-15s.mp4 --codec=h264 --crf=16 --audio-bitrate=320k
# vertical: npm run render:vertical, render:vertical30, render:vertical15
```

The cut-downs' scores come from `python tools/compose_score.py` (all three are written in one run).
