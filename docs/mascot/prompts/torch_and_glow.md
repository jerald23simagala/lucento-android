# Torch & Glow
## Torch-only generation (future layer work)
```
Isolated prop: short wooden torch with brass collar topped by a faceted pale-gold crystal, same design as the reference, three-quarter view, on flat chroma-key green (#00FF00), crystal unlit (no glow, no bloom), no shadow, no text.
```
Generate a lit crystal variant separately; never bake glow into the chroma stage.
## Procedural glow (implemented)
`python scripts/mascot_assets.py glow assets/layers/torch_glow --size 1024` → 5 PNGs, colour #FFCE78, radial falloff (1-d)^2.2, peak alpha thriving 1.00 / steady 0.80 / getting_tight 0.60 / concerned 0.42 / over_plan 0.28.
Placement on 2048 character canvas: centre (1620, 660) for standing poses, ≈ (1535, 830) for sleeping poses, size ≈ 700 px, drawn **behind** the character (so it never covers the face); an optional additive copy at ≤30% in front of the crystal only.
## Android recreation
RadialGradient(center=crystal, radius≈0.17×canvas, colors=[#FFCE78 @ α, transparent]) drawn behind the character; animate α only. Do not apply a ColorFilter to the character bitmap.

## Baked crystal levelling (implemented, round 2)
`set_crystal_level` in `scripts/mascot_assets.py`: detects crystal pixels (pale/amber, upper-right, largest component + closing), scales them so mean luminance = 230×(0.35+0.65×level) with an amber tint as it dims; never brightens; body pixels untouched (unit-tested). Measured v2 results: thriving ~224, steady ~195, getting_tight ~161, concerned ~133, over_plan ~111.
