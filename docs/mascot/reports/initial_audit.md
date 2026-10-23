# Initial Audit — docs/mascot (2026-10-23, 11:15 PDT)

## Method
`Get-ChildItem -Recurse -Force` on `C:\Users\Jerald\Desktop\Personal\lucento-android\docs\mascot` (Windows machine). Nothing outside this directory was listed or read.

## Findings
The directory **existed but was empty** (0 entries; created 2026-10-23 11:09 PDT).

| Audit item | Result |
|---|---|
| 1. Source artwork | None in the directory. The user then supplied an approved concept sheet as a chat attachment — **a user-created concept sheet (the user states it is their own original concept)** (`01M5MZKDTJ1NM8NA87K8DHBTQ7.png`, 1536×1024, PNG, mode RGB, **no alpha**). Copied to `assets/source/` (original filename kept, plus `lucento_concept_sheet_reference.png` copy). |
| 2. Existing individual assets | None. |
| 3. Damaged transparency | No previous extractions present to inspect. The concept sheet has no alpha channel; checkerboard detector score 0.030 (not suspected) — background is a cream gradient with panel labels/text, **not** a baked checkerboard. The earlier failed extraction / checkerboard sheet described in the brief was **not present** in this directory. |
| 4. Missing wellness tiers | All 5 (thriving, steady, getting_tight, concerned, over_plan) — none existed as individual assets. |
| 5. Missing expressions/poses | All 7 (hero_neutral, greeting_happy, thinking, pointing, success, caution, sleeping). |
| 6. Missing animation layers | All (body, hair, eyes, brows, mouths, clothing, scarf, satchel, arms, torch handle, crystal, glow, sparkles, shadow). |
| 7. Docs to preserve | None existed. |
| 8. Missing docs/tooling | Everything: README, design spec, pipeline, animation guide, manifest, QA, Android handoff, prompts, scripts, skill. |

## Source-sheet visual notes
Shows the approved identity: golden-orange orb, brown tousled hair, cream scarf/wrap, brown satchel with gold buttons + strap, wooden torch with glowing crystal. Labels map: Thriving→thriving, Stable→steady, Needs Attention→getting_tight, Struggling→concerned, Overwhelmed→over_plan. Off-brief elements in the sheet to tone down: rosy cheeks, fairly large eyes, sparkles, floating `?` and `Zzz`, tier-4 stubble/mustache (allowed only if subtle/non-shaming). No orbital ring (good).

## Recovery strategy (chosen)
Cut-outs from the sheet are not viable: figures are small (~150–300 px), overlap glow/gradient backgrounds and text. Upscaling them would be dishonest. Therefore: **use the sheet as a visual reference and regenerate individual characters** with the image generator on a flat chroma-key green (#00FF00-ish) background, then key with a conservative green-dominance key (never keys pale/cream/yellow/gray), un-mix edges, validate alpha, render on light/dark, and place all on a 2048² canvas with one identical transform.


_Update (round 2, 2026-10-23 11:30 PDT): provenance corrected to user-created concept sheet per the user._
