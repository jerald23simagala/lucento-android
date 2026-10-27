package com.lucento.app.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lucento.app.R
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.domain.WellnessTier

/**
 * The seven poses of the approved Lucento character (docs/mascot). Facial and hand changes are separate artwork per
 * pose; switching pose cross-fades between them (the art is never morphed, redrawn or re-tinted).
 */
enum class MascotPose { HeroNeutral, GreetingHappy, Thinking, Pointing, Success, Caution, Sleeping }

/**
 * What the character is doing in a given moment; kept small on purpose — Lucento's friend, not a cartoon cast.
 * [idleCycles] is how many slow float cycles play before the character rests (0 = still).
 */
enum class MascotMood(val pose: MascotPose, val idleCycles: Int) {
    /** Default on dashboards: neutral pose, a slow float a few times, then still. */
    Happy(MascotPose.HeroNeutral, 3),
    /** Welcome moments (onboarding). */
    Greeting(MascotPose.GreetingHappy, 2),
    /** Empty states, "let's start", reading a receipt. */
    Curious(MascotPose.Thinking, 2),
    /** Something was done well. */
    Success(MascotPose.Success, 1),
    /** A meaningful milestone. */
    Celebrate(MascotPose.Success, 2),
    /** Supportive concern: the month's budget is nearly or fully used. Palm out, calm — never judgement. */
    Concerned(MascotPose.Caution, 1),
}

/**
 * Lucento, the approved mascot: a golden orb with a hair crest, cream scarf, satchel and a crystal torch whose light
 * reflects the month's [tier] (thriving → over plan). The artwork ships unchanged as WebP sprites
 * (`res/drawable-nodpi/mascot_<tier>_<pose>`, cropped only of transparent margin); the torch glow is a separate warm
 * light drawn behind the sprite at the tier's brightness, so dimming never darkens the character itself.
 *
 * Use it small and secondary on dashboards (56–84dp), larger in onboarding (140dp) and empty states (~96dp). Outside
 * budget contexts the default tier is [WellnessTier.THRIVING] (the character's clean, everyday look). When [animate] is
 * true and the system has not removed animations, it arrives with a small hop (greeting adds two gentle rocks),
 * floats for [MascotMood.idleCycles] and rests; pose and tier changes cross-fade. Decorative by default (no accessibility label) unless [contentDescription] is given.
 */
@Composable
fun LucentoMascot(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    animate: Boolean = true,
    mood: MascotMood = MascotMood.Happy,
    tier: WellnessTier = WellnessTier.THRIVING,
    contentDescription: String? = null,
) {
    val moving = animate && !LucentoTheme.reducedMotion
    val float = remember { Animatable(0f) }
    val hop = remember { Animatable(0f) }
    val rock = remember { Animatable(0f) }
    val small = size < 96.dp
    LaunchedEffect(moving, mood) {
        float.snapTo(0f)
        hop.snapTo(0f)
        rock.snapTo(0f)
        if (!moving) return@LaunchedEffect
        // Arrival: a small hop that settles with a soft spring, so the character reads as alive on screen entry.
        hop.animateTo(1f, tween(durationMillis = 260, easing = FastOutSlowInEasing))
        hop.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow))
        // Greeting: two gentle rocks of the whole sprite (the wave itself is in the artwork). Skipped below 96dp.
        if (mood.pose == MascotPose.GreetingHappy && !small) {
            repeat(2) {
                rock.animateTo(1f, tween(durationMillis = 180, easing = FastOutSlowInEasing))
                rock.animateTo(-1f, tween(durationMillis = 260, easing = FastOutSlowInEasing))
                rock.animateTo(0f, tween(durationMillis = 180, easing = FastOutSlowInEasing))
            }
        }
        repeat(mood.idleCycles) {
            float.animateTo(1f, tween(durationMillis = 1600, easing = FastOutSlowInEasing))
            float.animateTo(0f, tween(durationMillis = 1600, easing = FastOutSlowInEasing))
        }
    }
    // Float 4.5% of the height (3% below 96dp) — visible on a phone but still calm; hop 6% (4% small) with a 4% lift
    // in scale. The glow breathes a little with the float.
    val amplitude = if (small) 0.03f else 0.045f
    val hopHeight = if (small) 0.04f else 0.06f
    val semantics = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier.clearAndSetSemantics { }
    }
    Box(modifier.size(size).then(semantics)) {
        Crossfade(
            targetState = tier to mood.pose,
            animationSpec = tween(durationMillis = if (moving) 300 else 0),
            label = "mascot-pose",
        ) { (shownTier, shownPose) ->
            Image(
                painter = painterResource(spriteFor(shownTier, shownPose)),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0.5f, 0.8f) // the feet
                        translationY = -(float.value * amplitude + hop.value * hopHeight) * this.size.height
                        val lift = 1f + 0.04f * hop.value
                        scaleX = lift
                        scaleY = lift
                        rotationZ = rock.value * 4f
                    }
                    .drawBehind {
                        val glow = if (shownPose == MascotPose.Sleeping) GlowCenterSleeping else GlowCenter
                        val center = Offset(glow.x * this.size.width, glow.y * this.size.height)
                        val alpha = glowAlpha(shownTier) * (1f + 0.06f * float.value)
                        val stops = GlowFalloff.map { (stop, k) -> stop to TorchLight.copy(alpha = (alpha * k).coerceIn(0f, 1f)) }
                            .toTypedArray()
                        drawCircle(
                            brush = Brush.radialGradient(
                                *stops,
                                center = center,
                                radius = GlowRadius * this.size.width,
                            ),
                            radius = GlowRadius * this.size.width,
                            center = center,
                        )
                    },
            )
        }
    }
}

/** Torch light colour from the character spec (crystal glow #FFCE78) — artwork, not a UI token. */
private val TorchLight = Color(0xFFFFCE78)

/**
 * Glow geometry in sprite units (the sprites are the 2048² masters cropped to x 298–1778, y 272–1752): the crystal
 * sits at (1620, 660) — (1535, 830) when sleeping — with a radius of 0.17 × the master width.
 */
private val GlowCenter = Offset(0.893f, 0.262f)
private val GlowCenterSleeping = Offset(0.836f, 0.377f)
private const val GlowRadius = 0.235f

/** Radial falloff measured from the procedural glow layers in docs/mascot/assets/layers/torch_glow. */
private val GlowFalloff = listOf(
    0f to 1f, 0.1f to 0.8f, 0.2f to 0.63f, 0.3f to 0.48f, 0.4f to 0.35f, 0.5f to 0.25f,
    0.6f to 0.16f, 0.7f to 0.094f, 0.8f to 0.043f, 0.9f to 0.016f, 1f to 0f,
)

/** Peak glow per tier (MASCOT_DESIGN_SPEC: 100 / 80 / 60 / 42 / 28 %). */
private fun glowAlpha(tier: WellnessTier): Float = when (tier) {
    WellnessTier.THRIVING -> 1.00f
    WellnessTier.STEADY -> 0.80f
    WellnessTier.GETTING_TIGHT -> 0.60f
    WellnessTier.CONCERNED -> 0.42f
    WellnessTier.OVER_PLAN -> 0.28f
}

@DrawableRes
private fun spriteFor(tier: WellnessTier, pose: MascotPose): Int = when (tier) {
    WellnessTier.THRIVING -> when (pose) {
        MascotPose.HeroNeutral -> R.drawable.mascot_thriving_hero_neutral
        MascotPose.GreetingHappy -> R.drawable.mascot_thriving_greeting_happy
        MascotPose.Thinking -> R.drawable.mascot_thriving_thinking
        MascotPose.Pointing -> R.drawable.mascot_thriving_pointing
        MascotPose.Success -> R.drawable.mascot_thriving_success
        MascotPose.Caution -> R.drawable.mascot_thriving_caution
        MascotPose.Sleeping -> R.drawable.mascot_thriving_sleeping
    }
    WellnessTier.STEADY -> when (pose) {
        MascotPose.HeroNeutral -> R.drawable.mascot_steady_hero_neutral
        MascotPose.GreetingHappy -> R.drawable.mascot_steady_greeting_happy
        MascotPose.Thinking -> R.drawable.mascot_steady_thinking
        MascotPose.Pointing -> R.drawable.mascot_steady_pointing
        MascotPose.Success -> R.drawable.mascot_steady_success
        MascotPose.Caution -> R.drawable.mascot_steady_caution
        MascotPose.Sleeping -> R.drawable.mascot_steady_sleeping
    }
    WellnessTier.GETTING_TIGHT -> when (pose) {
        MascotPose.HeroNeutral -> R.drawable.mascot_getting_tight_hero_neutral
        MascotPose.GreetingHappy -> R.drawable.mascot_getting_tight_greeting_happy
        MascotPose.Thinking -> R.drawable.mascot_getting_tight_thinking
        MascotPose.Pointing -> R.drawable.mascot_getting_tight_pointing
        MascotPose.Success -> R.drawable.mascot_getting_tight_success
        MascotPose.Caution -> R.drawable.mascot_getting_tight_caution
        MascotPose.Sleeping -> R.drawable.mascot_getting_tight_sleeping
    }
    WellnessTier.CONCERNED -> when (pose) {
        MascotPose.HeroNeutral -> R.drawable.mascot_concerned_hero_neutral
        MascotPose.GreetingHappy -> R.drawable.mascot_concerned_greeting_happy
        MascotPose.Thinking -> R.drawable.mascot_concerned_thinking
        MascotPose.Pointing -> R.drawable.mascot_concerned_pointing
        MascotPose.Success -> R.drawable.mascot_concerned_success
        MascotPose.Caution -> R.drawable.mascot_concerned_caution
        MascotPose.Sleeping -> R.drawable.mascot_concerned_sleeping
    }
    WellnessTier.OVER_PLAN -> when (pose) {
        MascotPose.HeroNeutral -> R.drawable.mascot_over_plan_hero_neutral
        MascotPose.GreetingHappy -> R.drawable.mascot_over_plan_greeting_happy
        MascotPose.Thinking -> R.drawable.mascot_over_plan_thinking
        MascotPose.Pointing -> R.drawable.mascot_over_plan_pointing
        MascotPose.Success -> R.drawable.mascot_over_plan_success
        MascotPose.Caution -> R.drawable.mascot_over_plan_caution
        MascotPose.Sleeping -> R.drawable.mascot_over_plan_sleeping
    }
}
