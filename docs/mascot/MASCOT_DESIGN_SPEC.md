# Lucento Mascot — Design Specification

Canonical source: `assets/source/lucento_concept_sheet_reference.png` — **user-created concept sheet** (the user's own original concept). Production candidates (v2): `assets/wellness_tiers/*_v2.png`, `assets/expressions/*_v2.png`; superseded v1 files in `assets/backup_v1/`.

## Canonical identity
A living celestial orb: Lucento's companion "light" on the user's financial journey. Identity comes from **silhouette (round orb + tousled hair crest), cream scarf, brown satchel, wooden torch with crystal, and warm lighting** — not from the face alone.

## Proportions (at 2048² canvas, current assets)
- Orb body ≈ 860 px wide; total character height (hair tip → feet) ≈ 1330 px.
- Hair crest adds ~25% above orb. **Thriving only** has the single curled cowlick on top; steady has smooth swept hair with no cowlick; getting_tight → over_plan get progressively more tousled.
- Arms short, small rounded hands (≈ 1/8 body width). Two short stubby feet.
- Torch held in character's left hand (viewer's right); crystal top sits at ≈ hair height.
- Three-quarter view, camera at eye level, consistent across all states.

## Materials & colours
| Element | Colour (approx.) | Material |
|---|---|---|
| Body | #F5A623 → #E8891A gradient, warm highlight top-left | soft satin/glossy |
| Hair | #5A3A22 dark brown with #8A5A34 highlights | stylised strands |
| Scarf/wrap | #EDE3CC cream | soft cloth |
| Satchel & strap | #5B3A24 brown leather, #C99A3A gold buttons | leather/brass |
| Torch handle | #6B4226 wood, #B07A3A brass collar | wood/metal |
| Crystal | #FFF3C4 core → #FFCE78 glow | translucent faceted |

## Face
Small dark-brown eyes with one specular highlight; thin brows; small mouth line. **Not oversized.** No blush/rosy cheeks (v2 removed blush on thriving hero, concerned hero and success; v3 regenerated getting_tight_success without blush).

## Torch & lighting
Key light from upper-left; the crystal is a secondary warm light. Torch brightness is a **separate glow layer** (see `prompts/torch_and_glow.md`, `assets/layers/torch_glow/`). Dimming the torch must never darken the body.

## Wellness tiers (circumstance, never moral judgment)
| Tier | Clothing | Hair | Crystal (level → measured mean luma) / glow peak | Expression/posture |
|---|---|---|---|---|
| thriving | clean, crisp | neat, lively, cowlick | 1.00 → ~224 / 100% | calm confident smile |
| steady | clean, slightly softer | neat, smooth, no cowlick | 0.80 → ~195 / 80% | friendly relaxed |
| getting_tight | slightly loosened scarf | mildly messier | 0.60 → ~161 / 60% | thoughtful, flat mouth |
| concerned | small fray, satchel patch; **light scruffy stubble + teary eyes / single tear (v3)** | noticeably messier | 0.42 → ~133 / 42% | subdued, gentle concern |
| over_plan | worn but intact scarf, few frayed threads, neat stitched patch — dignified; **fuller ragged beard + visible tears on cheeks (v3)** | messiest, same silhouette | 0.28 → ~111 (visible, greyish-amber) / 28% | soft worry |
Crystal levels are applied in-pixel by `scripts/build_v2.py` (crystal pixels only; body untouched) so the baked crystal steps down monotonically; app-side glow remains a separate layer.
Never: dirt, grime, sickness, scary faces, shame cues, sobbing/wailing, snot, red swollen eyes.

### Design decision D-v3 (2026-10-23, requested by the user)
The user asked that the struggling (concerned) and overwhelmed (over_plan) tiers show unkempt facial hair and crying. This overrides the earlier "no stubble / no tears" rule for those two tiers only:
- **concerned (tier 4):** light scruffy stubble on jaw/upper lip; glossy teary eyes and/or a single small tear. Sleeping: one tear track.
- **over_plan (tier 5):** fuller, ragged, unkempt beard (clearly heavier than concerned); visible tears on the cheeks. Sleeping: tear tracks.
- thriving / steady / getting_tight: clean-shaven, no tears.
- Constraints kept: sympathetic and dignified (soft tears, no distress grimace); identity, hairstyle, scarf, satchel, torch and crystal-dimming order unchanged.
- Review note: crying on a finance mascot can read as shaming the user; consider product testing before shipping tier 5 widely.

## Approved directions
Premium soft-3D render, restrained warmth, subtle clothing progression, independent torch light.

## Prohibited (explicit)
- **Saturn-style orbital ring — REJECTED. Never reintroduce.**
- Emoji look, oversized eyes, rosy cheeks as a feature, muscular arms/big fists, excess sparkles, floating symbols (`?`, `Zzz`, `!`, `$`), inconsistent proportions/lighting, random clothing swaps.

## Small-size readability
At 48–96 dp: orb + hair crest + torch crystal must read. Do not rely on facial detail below 64 dp; tier communicated by torch glow + hair messiness. Test at 48/64/96/160 dp.

## Commercial & technical considerations (no legal advice)
- **Provenance:** the concept sheet is **user-created** — the user states they created it themselves as their own original concept. All character PNGs in this folder are **AI-generated** (2026-10-23) using that sheet as reference, then chroma-keyed and edited by script (edge decontamination, crystal dimming). Glow layers are procedurally generated by original code. The user's statement of authorship is recorded as given; it has not been independently verified or legally reviewed.
- **Commercial rights:** the derived character PNGs were produced with an AI image generator; their commercial-use terms depend on that service's terms — verify before release. If any tool was used in making the concept sheet, its terms apply too.
- **Copyright registrability:** AI-generated imagery may have limited or no copyright protection in some jurisdictions (e.g., the US Copyright Office requires human authorship); substantial human editing/redrawing may strengthen protection. Seek qualified legal advice; nothing here is a guarantee or clearance.
- **Third-party assets/fonts:** none used in the assets. Contact-sheet labels use Pillow's default bitmap font (not shipped with assets).
- **Trademark:** consider clearance searches before using the mascot as a brand mark.
- **Production suitability:** current assets are prototypes/needs_cleanup; flattened (no rig layers); resampled from 1024 px.
- No legal clearance has been obtained or is implied.
