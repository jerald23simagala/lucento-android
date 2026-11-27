# Lucento launch film: voiceover

**Every version has a narrated cut** (owner, 2026-11-24), in landscape and vertical: the 30-second and 15-second cuts
are narrated, and the full film (98.7 s since 2026-11-27, 93.3 s before) comes twice, **narrated** (`LaunchFilmNarrated`) and **music-led** (`LaunchFilm`,
for autoplaying, muted feeds and anywhere an AI label isn't wanted). The original score stays in every version
(TRUTH_RULES T23).

## Narration

**Voice:** Laurel ("Warm, Kind & Confident"), a native English (American) woman's voice from the ElevenLabs library,
made for brand films and product launches, on the owner's paid Creator plan, which allows commercial use. The owner
chose her on 2026-11-25 for a native English narrator, from 30 s samples of Laurel, Justin Case (American man), Marina
(American woman) and Charlotte (British woman). Rianne ("Warm & Friendly Filipino Creator") narrated the first renders
on 2026-11-24; her takes are kept and still render with `--props='{"voice":"rianne"}'`. **"Lucento" is said
loo-SEN-toh** (owner): the takes spell it `/luˈsɛntoʊ/` for the model. Model `eleven_v4`, one take per version that
speaks every line in order; the takes are on the ElevenLabs flow "Lucento launch film — voiceover (AS-1)" and in
lucento-studio `video/voiceover/takes/` (used: `30-laurel-a`, `15-laurel-a`, `93-laurel-a`, and the "Every screen" line
from `screens-laurel-b`; the others are alternates, Rianne's takes and the voice samples).

**How it's built** (lucento-studio `video/`):

1. The take (`voiceover/takes/<cut>-<voice>-<take>.mp3`) goes through `python tools/voiceover.py <take> <cut>-<voice>
   <lines>`. It finds the lines by their pauses, levels them together to −16 LUFS (the score sits near −14.5 LUFS) and
   writes `public/launch-film/audio/vo/<cut>-<voice>/01.wav …` and the line lengths in
   `src/launch-film/audio/voiceover.json`.
2. `src/launch-film/audio/Voiceover.tsx` places each line on its frame (`VO_AT`) and dips the score to 0.42 (about
   −7.5 dB) under it, 6 frames before a line and 12 after. The sound effects stay as they are.
3. `VOICE` picks the narrator. `--props='{"voice":"none"}'` renders a cut without narration, and another narrator
   renders with its own name. Under a line the sound effects dip about 3 dB too, and each line is held at −3 dBFS by
   a gentle limiter, so the mix keeps its headroom. With Laurel (ffmpeg ebur128): the 30 s cut −14.3 LUFS, peak
   −1.3 dBFS; the 15 s −15.3 LUFS, peak −0.7 dBFS (the closing line on the final hit); the narrated full film −15.0 LUFS,
   peak −1.1 dBFS (98.7 s since 2026-11-27; −1.0 at 93.3 s). The verticals measure the same.

A new line or a retake needs only a take, the script and a render. A single new line can be read in context instead
(2026-11-27, "Every screen"): one take of the new line between its two neighbours, split by `voiceover.py`, then
`python tools/voiceover_insert.py master-laurel 11 <that take's name> 2` moves the new reading into the cut and keeps
every other approved line (668 credits for two takes, against about 2,300 for a full 93-second take). Lines sit between the on-screen captions and off
the score's hits; times are in the cut's own seconds (frames at 30 fps).

### 30-second cut (`LaunchFilm30`, `LaunchFilm30Vertical`)

| Time | Frame | On screen | Line |
|---|---|---|---|
| 0:00 – 0:02.7 | – | Fragments gather into the crest | *(music: the crest lands on the downbeat)* |
| 0:02.9 | 87 | "Your entire financial world." | "All your money, in one place." |
| 0:05.7 | 171 | The Dashboard rises; "Know where you stand at a glance." | "See where you stand at a glance." |
| 0:09.5 | 284 | "Say it." and the Listening sheet | "Just say it…" |
| 0:10.5 – 0:12.1 | – | The spoken words appear beside the phone | *(no narration)* |
| 0:11.7 | 352 | The result row arrives | "…and Lucento fills in the details." |
| 0:14.0 | 421 | All at once: one message is typed, four rows appear | "Or type your whole day in one message." |
| 0:17.1 | 514 | Save, "4 transactions saved" | "One tap saves it all." |
| 0:19.0 | 570 | Lucento Shared: your partner's dinner arrives in your list | "With Lucento Pro, share it with your partner." |
| 0:22.7 – 0:24.0 | – | The sunrise | *(music builds to the final hit)* |
| 0:24.3 | 730 | The logo, "See your money clearly." | "Lucento. See your money clearly." |

### 15-second cut (`LaunchFilm15`, `LaunchFilm15Vertical`)

| Time | Frame | On screen | Line |
|---|---|---|---|
| 0:00 – 0:03.3 | – | The crest forms | *(music)* |
| 0:03.4 | 102 | All at once: one message is typed, four rows appear | "Type your whole day in one message." |
| 0:06.5 | 196 | Save, "4 transactions saved" | "One tap saves it all." |
| 0:08.0 – 0:10.7 | – | The sunrise | *(music builds to the final hit)* |
| 0:11.0 | 330 | The logo, "See your money clearly." | "Lucento. See your money clearly." |

### Full film (`LaunchFilmNarrated`, `LaunchFilmNarratedVertical`, 98.7 s)

The master script, updated for 0.5.19 and approved by the owner (2026-11-24): "net worth" for "balance" (T40), "with
Pro" on other currencies and on Shared (T6), and "scanning receipts and statements". The "Every screen" line was added
on 2026-11-27 with scene 09 (the owner's choice); "opens up" lands as the foldable lies flat.

| Time | Frame | On screen | Line |
|---|---|---|---|
| 0:00.7 | 20 | Fragments gather into the crest | "All the pieces of your financial life… together at last." |
| 0:06.1 | 182 | The Dashboard rises and scrolls | "Lucento shows you where you stand: your net worth, your budget, what's coming up, at a glance." |
| 0:13.4 | 402 | Wallet cards, net worth, currencies | "Cash, bank accounts, cards and loans, even other currencies with Pro. Every account, one clear picture." |
| 0:21.1 | 632 | "Less input. More control." | "And keeping it up to date is easy." |
| 0:23.2 – 0:27.0 | – | Say it | *(no narration: the spoken words play on screen)* |
| 0:27.1 | 812 | Type it | "Type it like a message…" |
| 0:29.9 | 896 | All at once | "…or add your whole day at once, and save it with one tap." |
| 0:35.1 | 1052 | Presets, then goals, budget, scheduled | "Start with ready-made categories, build a budget that adds up, save for what matters, and see every bill coming." |
| 0:43.3 | 1300 | Insights, then Ask Lucento | "See where your money goes. Ask a question in plain words, and get an answer worked out from your own records." |
| 0:54.1 | 1622 | Lucento Shared | "With Lucento Pro, share it with your partner: each on your own phone, both looking at the same numbers. Better decisions start together." |
| 1:04.7 | 1940 | Themes, Hide amounts | "Make it yours, with themes, hidden amounts, and an app that works even offline." |
| 1:12.0 | 2160 | A foldable opens into two panes, then a tablet joins it | "On a tablet or a foldable, Lucento opens up, with everything side by side." |
| 1:17.9 | 2336 | And there's more | "And so much more: exchange rates, scheduled bills, loans and installments, goals, scanning receipts and statements, offline or in the cloud." |
| 1:27.7 – 1:33.9 | – | The sun rises | *(music builds to the final hit)* |
| 1:34.0 | 2820 | The logo, "See your money clearly." | "Lucento. See your money clearly." |

### Truth check (TRUTH_RULES, checked against 0.5.19)

Truth: pass. T1–T6, T8, T10, T14–T18, T21, T23, T35, T40, T41 (the AI label below), T42.

- **"Just say it… and Lucento fills in the details"** and **"type your whole day in one message"** never call the
  composer AI (T1). The person still taps Save, so nothing is logged by itself (T3). A message holds up to 30 rows (T7).
- **"With Lucento Pro, share it with your partner"** names Shared as Pro (T6). The end card also says "Some features
  require Lucento Pro."
- **"All your money, in one place"** and **"every account, one clear picture"** are what the person records. No line
  suggests a bank connection (T4).
- **"Other currencies with Pro"** and **"With Lucento Pro, share it"** mark both Pro features (T6); cloud sync ("in
  the cloud") is covered by the end card's "Some features require Lucento Pro."
- **"An answer worked out from your own records"** is Ask Lucento's on-phone answer ("Calculated on this phone"); it
  doesn't claim AI or that nothing leaves the phone (T2, T10).
- **"On a tablet or a foldable, Lucento opens up, with everything side by side"** plays over a foldable open in
  landscape and a tablet in landscape, both showing the split: an upright Fold keeps one pane, so the words never sit
  beside one (T42).
- **"See every bill coming"** describes Scheduled and the in-app bell, never notifications (T5). **"Scanning receipts
  and statements"** is Lucento Scan, which is free and reviewed row by row (T35). **"Works even offline"** is true of
  every core feature.
- No prices, availability on Google Play, "best", savings promises, notifications or third-party names (T5,
  T14–T18, T21). The bulk message on screen is "Coffee 180, lunch 250, taxi 240, phone bill 300". Before 0.5.19 it named a
  ride-hailing brand, and until 2026-11-27 it said "load 300".

### Posting a narrated cut

Every narrated version (the 30 s and 15 s cuts and `lucento-launch-film-narrated*.mp4`) has an AI-generated voice,
so turn on each platform's AI label (owner, 2026-11-24):

- **TikTok:** after **Next** on the post screen, tap **More options** and turn on **AI-generated content**, then post
  ([TikTok help](https://support.tiktok.com/en/using-tiktok/creating-videos/ai-generated-content)).
- **Instagram and Facebook Reels (Meta):** on the last screen before **Share**, turn on **Label as made with AI**. Viewers
  see it as "AI info". Meta requires it for a reel with a realistic AI voiceover
  ([Instagram help](https://help.instagram.com/761121959519495)). Menu names can differ by app version.

The music-led full film (`lucento-launch-film.mp4`, `-vertical`) has no narration and needs no label.

## The 0.5.14 master script (history)

The first narration plan, written for a human voice before ElevenLabs was available. The narrated 93-second film above
uses its updated wording.

| Time (mm:ss) | Frames | On screen | Line |
|---|---|---|---|
| 0:00.8 – 0:04.5 | 24–135 | Fragments gather into the crest | "All the pieces of your financial life… together at last." |
| 0:06.0 – 0:11.5 | 180–345 | Dashboard rises and scrolls | "Lucento shows you where you stand — your balance, your budget, what's coming up — at a glance." |
| 0:14.0 – 0:20.0 | 420–600 | Wallet cards, net worth, currencies | "Cash, bank accounts, cards and loans… even money in other currencies. Every account, one clear picture." |
| 0:21.5 – 0:23.0 | 645–690 | "Less input. More control." | "And keeping it up to date is easy." |
| 0:23.3 – 0:27.0 | 700–810 | Say it | *(no narration: let the on-screen spoken words play)* |
| 0:27.7 – 0:29.2 | 830–875 | Type it | "Type it like a message…" |
| 0:30.4 – 0:33.8 | 912–1015 | All at once | "…or add your whole day at once, and save it with one tap." |
| 0:35.3 – 0:42.0 | 1060–1260 | Presets, then goals, budget, scheduled | "Start with ready-made categories, build a budget that adds up, save for what matters, and see every bill coming." |
| 0:43.5 – 0:52.5 | 1305–1575 | Insights, then Ask Lucento | "See where your money goes. Ask a question in plain words, and get an answer worked out from your own records." |
| 0:54.0 – 1:03.0 | 1620–1890 | Lucento Shared | "Share it with your partner: each on your own phone, both looking at the same numbers. Better decisions start together." |
| 1:04.5 – 1:11.0 | 1935–2130 | Themes, Hide amounts | "Make it yours, with themes, hidden amounts, and an app that works even offline." |
| 1:12.5 – 1:21.5 | 2175–2445 | And there's more | "And so much more: exchange rates, scheduled bills, loans and installments, goals, scanning — offline, or in the cloud." |
| 1:23.7 – 1:27.5 | 2510–2625 | The sun rises | *(silence: let the music build)* |
| 1:28.7 – 1:31.7 | 2660–2750 | Logo and headline | "Lucento. See your money clearly." |

Why the master's lines are safe to say (checked against the code, `../LAUNCH_VIDEO_FEATURE_DISCOVERY.md`):

- **"see every bill coming"** describes the Scheduled page and the in-app bell. It doesn't promise notifications,
  which don't exist.
- **"in the cloud"** is cloud sync, which is Pro (covered by the end card).
- **"worked out from your own records"** is how on-phone Ask Lucento answers work ("Calculated on this phone"). The
  line doesn't claim AI.
- **"works even offline"** is true: every core feature works offline.
- **Sharing and other currencies** are Pro. The end card says "Some features require Lucento Pro."
