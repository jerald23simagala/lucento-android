# Lucento Mascot — Animation Guide
Tone: polished, restrained, adult. Small amplitudes, soft easing, no bouncing cartoon physics. All values are recommendations for flattened sprites + transforms (current assets are not rigged; facial changes = swap to the matching pose PNG with a 120 ms cross-fade).

Global defaults: pivot = canvas centre (1024,1024 on 2048 canvas); float axis Y; standard easing = `FastOutSlowIn` (cubic-bezier 0.4,0,0.2,1); emphasised = cubic-bezier(0.2,0,0,1).

| Animation | Start pose | Key movement | Face | Hands | Torch | Duration | Easing | End pose | Back to idle | Context |
|---|---|---|---|---|---|---|---|---|---|---|
| **Idle** | hero_neutral (current tier) | float ±1.5% of height, sine loop; rotate ±1° | blink every 4–7 s (random), 150 ms (needs eyes_closed asset — else skip) | static | glow α breathes ±6% at tier level, 3.2 s loop | 3.2 s loop | sine in-out | hero_neutral | — | default on dashboards |
| **Greeting** | hero_neutral | cross-fade to greeting_happy; lift 2% + 2° tilt toward user | warm smile | free hand waves (via pose swap; 2 small ±4° rocks of whole sprite) | +10% glow pulse | 1.2 s | emphasised in, standard out | greeting_happy | 300 ms cross-fade | first open of day, onboarding |
| **Thinking** | hero_neutral | cross-fade to thinking; 1.5° tilt, slow 1% drift | gaze up/side | hand to chin | steady, no pulse | 1.6 s then hold while loading | standard | thinking (hold) | 300 ms when data ready | loading insights, analysing budget |
| **Guidance** | hero_neutral | cross-fade to pointing; translate 2% toward target UI | attentive | point toward element (mirror sprite horizontally if target is on the right — torch also mirrors; acceptable) | steady | 0.8 s in, hold ≤4 s | emphasised | pointing | 300 ms | tooltips, coach marks |
| **Success** | hero_neutral | cross-fade to success; rise 3% and settle, scale 1.0→1.03→1.0 | closed-eye smile | open palm lift | glow to thriving level +15% for 600 ms then back to tier level | 1.4 s | emphasised + gentle overshoot ≤3% | success | 400 ms | goal reached, bill paid, under budget |
| **Caution** | hero_neutral | cross-fade to caution; settle down 1%, no shake | empathetic brows | palm-out "one moment" | glow eases to tier level, no flicker | 1.0 s, hold while message visible | standard (decelerate) | caution | 400 ms | overspend warning, tier drop |
| Sleeping (bonus) | hero_neutral | settle 1%, slow float 5 s loop | eyes closed | resting | glow at 50% of tier | loop | sine | sleeping | 400 ms | app idle/night mode |

## Wellness-state transitions
Never jump more than one tier per visible transition; if data changes by several tiers, step through each with 600 ms per step (or cross-fade directly under reduced motion). Sequence: glow α tween (500 ms) → sprite cross-fade to new tier asset (400 ms). Downward transitions play Caution first; upward transitions may play Success.

## Clothing & hair changes
Currently baked into each tier sprite → change by cross-fade only (no morph). Never cut instantly. Clothing/hair must never change within a tier.

## Torch brightness
Glow layer α is the only brightness control (values in `prompts/torch_and_glow.md`). Body never tinted. Crystal must stay visible at over_plan.

## Reduced motion
When `Settings.Global.ANIMATOR_DURATION_SCALE == 0` or the app's reduce-motion setting is on: no float/rotate/scale; pose swaps are 150 ms cross-fades or instant; glow changes instant; no loops.

## Lifecycle
Start idle on `ON_RESUME`/when visible; stop all loops `ON_PAUSE`/off-screen; one-shot animations are queued (max 1 pending), never stacked; return to idle after each.

## Small screens
Below 96 dp height: disable rotation and blink; keep float ≤1%; prefer hero_neutral + glow; skip pointing (use UI highlight instead).

## Performance
Decode at display size (inSampleSize / Coil size) — never keep 2048² bitmaps in memory for a 120 dp view; ship WebP at 512/1024; animate with transforms on a single layer (graphicsLayer); target 60 fps, < 2 ms/frame mascot cost; pre-load the next likely pose.
