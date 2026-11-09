package com.lucento.app.feature.awards

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lucento.app.core.designsystem.component.LucentoMascot
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.component.MascotMood
import com.lucento.app.core.designsystem.component.rememberHaptics
import com.lucento.app.core.designsystem.theme.AwardArtwork
import com.lucento.app.core.designsystem.theme.Elevation
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.domain.Achievement
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The award ceremony: Lucento presents a newly earned medal. Show it only after the write that earned the award has
 * been committed (or to replay one the user already holds, with [onSeeAll] null).
 *
 * Everything is on stage from the first frame, so a slow device never shows an empty card. The medal pops into place
 * on its ribbon and settles with a small swing, a soft sunburst turns behind it, confetti bursts once, and a highlight
 * sweeps across the gold; the mascot leans in and hops, its palm open towards the medal. A confirm haptic marks the
 * moment the medal settles. Under reduced motion everything is shown complete and still. It is a modal moment that
 * waits for the person — tap Thanks, outside the card, or Back — and never repeats on its own.
 */
@Composable
fun AwardCeremony(
    achievement: Achievement,
    earnedCount: Int,
    onDismiss: () -> Unit,
    onSeeAll: (() -> Unit)? = null,
    /** Awards earned at the same moment, shown small beneath the main one rather than as ceremonies of their own. */
    alsoEarned: List<Achievement> = emptyList(),
) {
    val dismiss by rememberUpdatedState(onDismiss)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // Tapping outside the card closes it (Back and Thanks do too). A gesture beneath the card, not a button, so
            // screen readers don't announce the backdrop; the card's Surface keeps its own taps from reaching it.
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { dismiss() } })
            val compact = maxHeight < 600.dp
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shadowElevation = Elevation.lg,
                modifier = Modifier
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.xl)
                    .fillMaxWidth()
                    .widthIn(max = 380.dp)
                    .semantics { paneTitle = "New award" },
            ) {
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(start = Spacing.xl, end = Spacing.xl, bottom = Spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CeremonyStage(achievement, stageHeight = if (compact) 176.dp else 236.dp)
                    Text(
                        if (onSeeAll != null) "New award" else "Your award",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        achievement.title,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        "You " + achievement.description.replaceFirstChar { it.lowercase() } + ".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    if (alsoEarned.isNotEmpty()) {
                        Spacer(Modifier.height(Spacing.md))
                        AlsoEarned(alsoEarned)
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        "$earnedCount of ${Achievement.entries.size} awards earned",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.lg))
                    LucentoPrimaryButton(text = "Thanks, Lucento", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
                    if (onSeeAll != null) {
                        Spacer(Modifier.height(Spacing.xs))
                        LucentoTextButton(text = "See all awards", onClick = onSeeAll, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

/** The other awards from the same moment: small medals in a row, named in words for everyone. */
@Composable
private fun AlsoEarned(awards: List<Achievement>) {
    val names = awards.joinToString(", ") { it.title }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(Spacing.md)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            awards.forEach { AwardMedal(it, earned = true, size = 36.dp) }
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "Also earned: $names",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Sunburst, medal, mascot and confetti, choreographed once on entry. */
@Composable
private fun CeremonyStage(achievement: Achievement, stageHeight: Dp) {
    val reduced = LucentoTheme.reducedMotion
    val haptics = rememberHaptics()
    val drop = remember { Animatable(if (reduced) 1f else 0f) }
    val swing = remember { Animatable(if (reduced) 0f else 9f) }
    val glow = remember { Animatable(if (reduced) 1f else 0.4f) }
    val burst = remember { Animatable(if (reduced) 1f else 0f) }
    val shine = remember { Animatable(-1f) }
    val arrive = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reduced) {
            haptics.confirm()
            return@LaunchedEffect
        }
        launch { glow.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
        launch { drop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)) }
        launch { swing.animateTo(0f, spring(dampingRatio = 0.22f, stiffness = Spring.StiffnessVeryLow)) }
        launch { arrive.animateTo(1f, tween(360, easing = FastOutSlowInEasing)) }
        delay(240) // the medal settles into place
        haptics.confirm()
        launch { burst.animateTo(1f, tween(ConfettiMillis, easing = LinearEasing)) }
        delay(420)
        shine.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        shine.snapTo(-1f)
    }
    val turn = if (reduced) {
        0f
    } else {
        val spin by rememberInfiniteTransition(label = "sunburst").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(36_000, easing = LinearEasing), RepeatMode.Restart),
            label = "sunburst-turn",
        )
        spin
    }
    val compact = stageHeight < 200.dp
    val medalWidth = if (compact) 92.dp else 118.dp
    val mascotSize = if (compact) 92.dp else 120.dp
    val medalShift = if (compact) (-30).dp else (-38).dp
    val medalTop = Spacing.md
    // The medal disc's centre within the stage, for the sunburst and the confetti origin.
    val discCenterY = medalTop + medalWidth * (MedalAspect - MedalRadius)
    val particles = remember(achievement) { confetti(Random(achievement.ordinal + 7)) }
    val sun = AwardArtwork.Gold
    Box(
        Modifier
            .fillMaxWidth()
            .height(stageHeight)
            .clearAndSetSemantics { contentDescription = "Lucento presents the ${achievement.title} medal" },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f + medalShift.toPx(), discCenterY.toPx())
            // Kept to the stage: the rays fade out before the text below, so they never sit behind words.
            val reach = size.height * 0.72f
            rotate(turn, pivot = c) {
                val rays = 14
                repeat(rays) { i ->
                    val a0 = 2 * PI * i / rays
                    val a1 = a0 + PI / rays * 0.9
                    val wedge = Path().apply {
                        moveTo(c.x, c.y)
                        lineTo(c.x + reach * cos(a0).toFloat(), c.y + reach * sin(a0).toFloat())
                        lineTo(c.x + reach * cos(a1).toFloat(), c.y + reach * sin(a1).toFloat())
                        close()
                    }
                    drawPath(
                        wedge,
                        Brush.radialGradient(
                            0f to sun.copy(alpha = 0.24f * glow.value),
                            0.55f to sun.copy(alpha = 0.08f * glow.value),
                            1f to sun.copy(alpha = 0f),
                            center = c,
                            radius = reach,
                        ),
                    )
                }
            }
            drawCircle(
                Brush.radialGradient(0f to sun.copy(alpha = 0.28f * glow.value), 1f to sun.copy(alpha = 0f), center = c, radius = reach * 0.55f),
                radius = reach * 0.55f,
                center = c,
            )
        }
        AwardMedal(
            achievement = achievement,
            earned = true,
            size = medalWidth,
            shine = shine.value,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(x = medalShift, y = medalTop)
                .graphicsLayer {
                    // Present from the first frame (a slow device never shows an empty stage): it pops up from a
                    // little smaller and higher, and swings from the top of the ribbon as it settles.
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    val pop = 0.8f + 0.2f * drop.value
                    scaleX = pop
                    scaleY = pop
                    translationY = -(1f - drop.value) * 18.dp.toPx()
                    rotationZ = swing.value
                },
        )
        // On stage from the first frame too: it leans in from the side while its own hop plays.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .offset(x = if (compact) 48.dp else 60.dp)
                .graphicsLayer { translationX = (1f - arrive.value) * size.width / 5f },
        ) {
            LucentoMascot(size = mascotSize, mood = MascotMood.Celebrate, animate = true)
        }
        if (!reduced && burst.value in 0.001f..0.999f) {
            Canvas(Modifier.fillMaxSize()) {
                val origin = Offset(size.width / 2f + medalShift.toPx(), discCenterY.toPx())
                val t = burst.value * ConfettiMillis / 1000f
                particles.forEach { p ->
                    val time = t - p.delay
                    if (time <= 0f) return@forEach
                    val x = origin.x + p.vx.dp.toPx() * time
                    val y = origin.y + p.vy.dp.toPx() * time + 0.5f * Gravity.dp.toPx() * time * time
                    val fade = (1f - (burst.value - 0.65f).coerceAtLeast(0f) / 0.35f).coerceIn(0f, 1f)
                    val color = p.color.copy(alpha = fade)
                    rotate(p.spin * time, pivot = Offset(x, y)) {
                        if (p.round) {
                            drawCircle(color, radius = p.size.dp.toPx() / 2.4f, center = Offset(x, y))
                        } else {
                            val wPx = p.size.dp.toPx()
                            drawRect(color, topLeft = Offset(x - wPx / 2f, y - wPx * 0.8f), size = Size(wPx, wPx * 1.6f))
                        }
                    }
                }
            }
        }
    }
}

private const val ConfettiMillis = 1_900
private const val Gravity = 620f // dp/s²

private class Particle(
    val vx: Float, // dp/s
    val vy: Float,
    val size: Float, // dp
    val spin: Float, // degrees/s
    val delay: Float, // s
    val round: Boolean,
    val color: Color,
)

/** A fixed, seeded burst (the same award always throws the same confetti), mostly upwards and outwards. */
private fun confetti(random: Random): List<Particle> = List(34) { i ->
    val angle = Math.toRadians(-90.0 + (random.nextDouble() - 0.5) * 150.0)
    val speed = 170f + random.nextFloat() * 230f
    Particle(
        vx = (cos(angle) * speed).toFloat(),
        vy = (sin(angle) * speed).toFloat(),
        size = 5f + random.nextFloat() * 4f,
        spin = (random.nextFloat() - 0.5f) * 720f,
        delay = random.nextFloat() * 0.12f,
        round = i % 4 == 0,
        color = AwardArtwork.Confetti[i % AwardArtwork.Confetti.size],
    )
}
