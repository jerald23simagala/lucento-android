# Prompt blocks — Wellness Tiers (append to character_generation.md)
Use "Same exact character as the first reference image, same render style, same camera angle, same scale and placement" first.
- **thriving:** clean pristine scarf and satchel, neat lively hair with cowlick, bright warm torch, relaxed confident calm positive expression.
- **steady:** scarf clean but slightly softer/less crisp, satchel slightly less shiny, neat smooth swept hair with NO curl or loop on top (the cowlick is thriving-only), calm medium torch, friendly relaxed small smile.
- **getting_tight:** scarf slightly loosened and less polished, hair mildly messier with a few loose strands, torch moderately dimmer, thoughtful neutral expression. Body colour unchanged.
- **concerned:** visibly worn scarf with one small frayed edge, small patch on satchel, noticeably messier hair, torch crystal dim but clearly glowing, slightly subdued posture, gentle concerned brows. **v3 (user decision D-v3):** light scruffy stubble on jaw and upper lip, glossy teary eyes with a single small tear on one cheek (sleeping: one faint tear track). Sympathetic, not shameful. No blush.
- **over_plan:** worn but dignified — scarf intact and fully wrapping, faded and softly wrinkled with only two or three small frayed threads at the hem and one neat sewn patch (NOT ragged, NOT torn to shreds); satchel scuffed with one neat stitched patch; messiest hair but same hairstyle; crystal very dim; soft worried brows. **v3 (user decision D-v3):** fuller ragged unkempt beard, clearly heavier than concerned; visible tears running down both cheeks, soft not sobbing (sleeping: tear tracks). Keep fraying at hero_neutral level in every pose. Calm, sympathetic, not shameful.
Note: generators tend to keep the crystal bright; final brightness is driven by `assets/layers/torch_glow/` in-app.

Tip (learned round 2): for tier poses, attach ONLY the tier's hero_neutral as reference; adding a second pose reference from another tier leaks that tier's hair (e.g. the thriving cowlick).
Crystal brightness is normalised afterwards by `scripts/build_v2.py`; don't fight the generator for it.
