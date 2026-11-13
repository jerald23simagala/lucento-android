# Pro perk cards — prop sources

Each card of the Lucento Pro welcome deck shows Lucento, the approved mascot, beside one simple prop for the perk, on
the cards' blue (`PerkArt.kt`). The mascot is drawn by the app from its own sprites (`LucentoMascot`, docs/mascot) and
never by an image model. These are the ten props, one per card (`ProTourStop`, in deck order).

History: the owner wasn't satisfied with the cards' drawn "Scenes" art (2026-11-13). Three AI directions (painterly,
3D miniature worlds, gilded engraving) were set aside after critic round 1 (studio
`critic/reports/2026-11-13-perk-art-directions-r1*.md`); a flat hand-drawn 2D set with people followed, and the owner
answered "Too many people. Reimagine, make it simpler but we can add our mascot to make it livelier." Explorations
live in the Figma file "Pro perk art — explorations".

| Source | Card | Prop | Mascot |
|---|---|---|---|
| `wallets_source.webp` | Add every account you use | An open wallet with cards | Waves |
| `net_worth_source.webp` | Count everything you have | A cottage and a piggy bank | Points |
| `smart_reading_source.webp` | Smarter reading | A microphone whose words become a receipt | Smiles |
| `goals_source.webp` | Save for more at once | Three flags on a mound | Points |
| `insights_periods_source.webp` | Look further back | An hourglass, most of its sand run through | Thinks |
| `statements_source.webp` | Keep your statements | A clipped report with an up-and-down chart | Points |
| `ai_answers_source.webp` | AI answers | Two speech bubbles | Thinks |
| `sync_source.webp` | Your money on every phone | Two phones linked by an arc | Points |
| `shared_source.webp` | Share with a Partner | One notebook, two pencils | Waves |
| `themes_source.webp` | Three new themes | Paint pots in green, grey and violet | Smiles |

The mascot's cheer (its Success pose) is kept for the award that follows the cards.

Made 2026-11-13 with GPT Image 2.5 (`gpt-image-2.5-flare`) through Figma's image generation, 1024 × 1024. Each source
is the image exactly as generated, stored as lossless WebP. **Never edit a source**: to change a prop, generate a new
one with the same style and replace the file.

## Export

```
python tools/illustrations/export_perk_art.py --import <folder of 01-…png to 10-…png>   # new images
python tools/illustrations/export_perk_art.py                                         # re-export from the sources
```

The export cuts each prop out of its flat `#2F56E8` ground (`AwardArtwork.CardBlue`): the ground becomes transparent, a
soft shadow becomes a soft partly transparent shadow, and a ground-coloured patch walled in by the prop stays opaque,
so a prop never has a hole. It trims the prop to its visible pixels (shadow included) at the source's own
resolution, and writes `app/src/main/res/drawable-nodpi/perk_art_<stop>.webp` (with alpha, 21–40 KB) and
`perk_thumb_<stop>.webp` (192 px, centred on the blue). The app paints the band blue with faint doodles and lays the
pair out by what shows (`pairLayout` in `PerkArt.kt`): the mascot's hand over the prop, the torch's glow inside the
band. Needs Pillow, numpy and scipy.

## Prompts

Every prop uses one style, with only the object changing:

> A single simple object as a soft 3D render, in the style of a premium animated-film prop: smooth satin and matte
> materials, rounded friendly shapes, warm key light from the upper left, gentle soft shading, a soft contact shadow
> directly beneath it. The object: **{object}**. Centred, filling about 65 % of the frame. Background: one flat, plain,
> solid colour #2F56E8 (a medium electric blue) everywhere, with no gradient, no vignette, no floor line and no texture.
> Keep the object free of medium blues so it stands out from the background. No text, letters or numbers, no logos, no
> people.

(For a pair or a group, "A simple pair / group of objects" and "they stand out".)

1. an open tangerine-orange wallet standing upright, with four plain payment cards peeking out of it (sunlight yellow,
   leaf green, ivory and pale sky blue, each with a small gold chip and no writing)
2. a small cottage with a tangerine roof and a softly lit window, with a little ivory piggy bank sitting in front of
   it, grouped together
3. a small rounded retro desk microphone in ivory and brass on the left, and on the right a neat curled paper receipt
   standing upright with a few short grey lines and a small gold tick mark (no writing), with a little trail of tiny
   golden sparkles flowing from the microphone into the receipt, as if spoken words were becoming a tidy record
4. three small pennant flags on slim wooden poles of different heights, standing in one little rounded grassy mound;
   the flags are sunlight yellow, tangerine and ivory
5. a friendly rounded hourglass with a warm wooden frame and brass caps, its clear glass holding golden sand, most of
   it already in the lower bulb, as if a long stretch of time has passed
6. a neat little stack of ivory report pages standing slightly tilted, held together at the top by a gold binder clip;
   the front page shows a few short grey lines and a small simple bar chart of four bars whose heights go up and
   down unevenly (medium, tall, short, medium) in sunlight yellow and tangerine, with no writing

(Made again on 2026-11-13 after the critic's app rounds: card 6's bars now go up and down, since an earlier chart
climbed steadily, a growth cliché; card 3 was a speech bubble like card 7's, and card 5 a telescope beside another
bar chart like card 6's, so each card now has a motif of its own.)
7. two soft rounded speech bubbles in conversation, a smaller pale-sky-blue one behind on the left, and a larger warm
   sunlight-gold one in front on the right with three short ivory lines inside it. Nothing else: no sparkles, no
   stars, no extra symbols. (An earlier version had a sparkle beside the bubbles; next to the thinking mascot it read
   as a cartoon "idea!" mark, against the mascot's "no floating symbols" rule.)
8. two rounded smartphones with ivory frames standing upright side by side a little apart, their screens glowing
   softly in warm ivory with no writing, linked above by a small arc of glowing golden dots
9. one open ivory notebook lying flat with a few short grey lines on its pages (no writing), and two pencils resting
   across it, one sunlight yellow and one tangerine
10. three round open paint pots side by side, filled with forest-green, soft-grey and violet paint, with a small
    paintbrush with a wooden handle resting across them

## Checks before shipping a new prop

- At 200 % zoom: no letters, numbers or letter-like marks.
- No medium blues in the prop (they melt into the band); pale sky blue is fine.
- The prop doesn't touch the image's edge (the export warns).
- Money stays calm: no piles of coins, fanned hands of cards or rays behind money.
- Seen in the deck beside the mascot at 360 dp, light and dark, and at the 300 : 84 large-text band.
