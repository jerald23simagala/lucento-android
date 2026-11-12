package com.lucento.app.feature.pro.welcome

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.min
import com.lucento.app.core.designsystem.component.LucentoMark
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.component.rememberHaptics
import com.lucento.app.core.designsystem.theme.AwardArtwork
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.domain.entitlement.ProTourStop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One card of the deck: what it is and its words. */
internal data class RevealCard(val stop: ProTourStop, val copy: ProWelcomeCopy.StopCopy)

/**
 * How dark the screen goes behind the whole welcome, the same for every moment (cards, collection, award), so it never
 * brightens or darkens in between; dark enough that nothing beneath (balances on Home) can be read.
 */
@Composable
internal fun welcomeScrimAlpha(): Float = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) 0.94f else 0.92f

/** Words on the dim: light in every theme, because the dim is dark in every theme. */
private val OnDim = Color(0xFFF8FAFF)
private val OnDimMuted = Color(0xDBF8FAFF)

/**
 * Takes every touch that lands on the dim, so nothing beneath the welcome is changed by accident. It goes on the dim
 * itself, beside the welcome's content, never on a box around it: a finger drifts a pixel or two between down and up,
 * and a parent that consumes that movement cancels every button inside it (KNOWN_ISSUES PW-1).
 */
private fun Modifier.blockTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent().changes.forEach { it.consume() }
    }
}

/**
 * The welcome's stage, shared by the deck and the collection: the dim, a top band that keeps one height through every
 * moment (so nothing below it shifts as its words change), the middle, and a row of buttons. [skip] sits at the band's
 * right end, beside the progress line; [skipShown] false keeps its room so nothing moves when it goes.
 */
@Composable
internal fun WelcomeStage(
    eyebrow: String,
    title: String?,
    sub: String?,
    pane: String,
    skip: (() -> Unit)? = null,
    skipShown: Boolean = skip != null,
    middle: @Composable BoxScope.() -> Unit,
    buttons: @Composable RowScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().semantics { paneTitle = pane }) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = welcomeScrimAlpha()))
                .blockTouches(),
        )
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Box(Modifier.fillMaxWidth().heightIn(min = 112.dp)) {
                // The words keep clear of Skip when it is there; otherwise they take the width (a long title at large
                // text needs it), breaking evenly and, as a last resort, with a hyphen.
                val side = if (skip != null) 72.dp else Spacing.xl
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(start = side, end = side, top = Spacing.lg, bottom = Spacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (eyebrow.isNotEmpty()) {
                        Text(
                            eyebrow,
                            style = MaterialTheme.typography.labelLarge.copy(lineBreak = LineBreak.Heading),
                            color = AwardArtwork.GoldLight,
                            textAlign = TextAlign.Center,
                        )
                    }
                    if (!title.isNullOrEmpty()) {
                        Text(
                            title,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold, lineBreak = LineBreak.Heading, hyphens = Hyphens.Auto),
                            color = OnDim,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.semantics { heading() },
                        )
                    }
                    if (!sub.isNullOrEmpty()) {
                        Spacer(Modifier.height(Spacing.xs))
                        Text(sub, style = MaterialTheme.typography.bodyMedium, color = OnDimMuted, textAlign = TextAlign.Center)
                    }
                }
                if (skip != null) {
                    LucentoTextButton(
                        text = ProWelcomeCopy.SKIP,
                        onClick = { if (skipShown) skip() },
                        color = OnDim,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = Spacing.xs)
                            .graphicsLayer { alpha = if (skipShown) 1f else 0f }
                            .then(if (skipShown) Modifier.semantics { contentDescription = ProWelcomeCopy.SKIP_SPOKEN } else Modifier.clearAndSetSemantics {}),
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), content = middle)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.sm, bottom = Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                content = buttons,
            )
        }
    }
}

/**
 * The deck (owner's pick, 2026-11-12: "Deck, unwrapped"): the cards in a gold band; one tap slides the band off and turns
 * the first card, with the only burst of the welcome; then one card at a time. Next (or a swipe to the left) moves on,
 * Previous (or a swipe to the right) goes back, Skip and the system Back key go to the collection ([onCollection]).
 * [index] is the card face up on top, -1 while the deck is closed; the host keeps it across rotation. Taps that land
 * while a card is still turning are ignored, so a double tap never skips a card. Reduced motion: nothing turns or
 * slides; faces cross-fade.
 */
@Composable
internal fun ProRevealDeck(
    cards: List<RevealCard>,
    title: String,
    body: String,
    unwrap: Boolean,
    index: Int,
    onIndex: (Int) -> Unit,
    onCollection: () -> Unit,
) {
    val reduced = LucentoTheme.reducedMotion
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val count = cards.size
    var shown by remember { mutableIntStateOf(index.coerceIn(-1, count - 1)) }
    val turn = remember { Animatable(if (shown >= 0) 1f else 0f) }
    val enter = remember { Animatable(0f) }
    val leave = remember { Animatable(1f) }
    var leaving by remember { mutableStateOf<Int?>(null) }
    val band = remember { Animatable(if (unwrap && shown < 0) 0f else 1f) }
    val burst = remember { Animatable(1f) }
    var burstPlayed by rememberSaveable { mutableStateOf(shown >= 0) }
    val drag = remember { Animatable(0f) }
    var busy by remember { mutableStateOf(false) }
    var spoken by remember { mutableStateOf("") }

    LaunchedEffect(index) {
        val target = index.coerceIn(-1, count - 1)
        val from = shown
        if (target == from) return@LaunchedEffect
        busy = true
        if (target > from) {
            if (from < 0) {
                if (unwrap) {
                    haptics.tick()
                    if (reduced) band.snapTo(1f) else band.animateTo(1f, tween(420, easing = FastOutLinearInEasing))
                }
            } else {
                leaving = from
                launch {
                    leave.snapTo(0f)
                    leave.animateTo(1f, tween(if (reduced) 180 else 380, easing = FastOutLinearInEasing))
                    leaving = null
                }
                delay(if (reduced) 0 else 140)
            }
            drag.snapTo(0f)
            shown = target
            turn.snapTo(0f)
            haptics.tick()
            if (!burstPlayed && !reduced) {
                burstPlayed = true
                launch {
                    burst.snapTo(0f)
                    burst.animateTo(1f, tween(760, easing = LinearOutSlowInEasing))
                }
            }
            turn.animateTo(1f, tween(if (reduced) 180 else 560, easing = FastOutSlowInEasing))
        } else {
            // Back a card: it returns from the left, face up; the one it covered turns face down beneath it.
            drag.snapTo(0f)
            shown = target
            turn.snapTo(1f)
            if (target >= 0) {
                enter.snapTo(-1f)
                enter.animateTo(0f, tween(if (reduced) 180 else 380, easing = FastOutSlowInEasing))
            }
        }
        if (target >= 0) {
            val card = cards[target]
            spoken = "${ProWelcomeCopy.progress(target, count)}. ${card.copy.title}."
        }
        busy = false
    }

    fun next() {
        if (busy) return
        if (shown >= count - 1) onCollection() else onIndex(shown + 1)
    }

    fun previous() {
        if (busy || shown <= 0) return
        onIndex(shown - 1)
    }

    val last = shown == count - 1
    WelcomeStage(
        eyebrow = if (shown >= 0) ProWelcomeCopy.progress(shown, count) else "",
        title = if (shown < 0) title else null,
        sub = if (shown < 0) body else null,
        pane = title,
        skip = onCollection,
        skipShown = !last,
        middle = {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val width = min(380.dp, maxWidth - 32.dp)
                val height = min(480.dp, maxHeight - 28.dp)
                val density = LocalDensity.current
                // The burst: one gold glow behind the first card as it turns, and never again.
                if (burst.value < 1f) {
                    Canvas(Modifier.size(width * 1.3f)) {
                        val b = burst.value
                        drawCircle(
                            Brush.radialGradient(
                                listOf(AwardArtwork.GoldLight.copy(alpha = 0.9f * (1f - b)), AwardArtwork.RibbonTangerine.copy(alpha = 0.4f * (1f - b)), Color.Transparent),
                            ),
                            radius = size.minDimension / 2f * (0.35f + 1.25f * b),
                        )
                    }
                }
                val top = shown.coerceAtLeast(0)
                for (k in 2 downTo 0) {
                    val i = top + k
                    if (i >= count) continue
                    val isTop = k == 0
                    val up = isTop && shown >= 0
                    TurningCard(
                        card = cards[i],
                        turn = if (up) turn.value else 0f,
                        reduced = reduced,
                        modifier = Modifier
                            .size(width, height)
                            .graphicsLayer {
                                translationY = with(density) { (15 * k).dp.toPx() }
                                val s = 1f - 0.05f * k
                                scaleX = s
                                scaleY = s
                                if (isTop) {
                                    translationX = enter.value * size.width * 1.3f + drag.value
                                    rotationZ = drag.value / 28f / density.density
                                }
                            }
                            .then(if (up) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier.clearAndSetSemantics {})
                            .then(
                                if (up) {
                                    Modifier.pointerInput(shown) {
                                        val threshold = 72.dp.toPx()
                                        detectHorizontalDragGestures(
                                            onDragEnd = {
                                                val dx = drag.value
                                                when {
                                                    dx < -threshold && !busy -> next()
                                                    dx > threshold && shown > 0 && !busy -> previous()
                                                    else -> scope.launch { drag.animateTo(0f, tween(220)) }
                                                }
                                            },
                                            onDragCancel = { scope.launch { drag.animateTo(0f, tween(220)) } },
                                        ) { change, amount ->
                                            if (!busy) {
                                                change.consume()
                                                scope.launch { drag.snapTo(drag.value + amount) }
                                            }
                                        }
                                    }
                                } else {
                                    Modifier
                                },
                            ),
                    )
                }
                leaving?.let { l ->
                    TurningCard(
                        card = cards[l],
                        turn = 1f,
                        reduced = reduced,
                        modifier = Modifier
                            .size(width, height)
                            .clearAndSetSemantics {}
                            .graphicsLayer {
                                val p = leave.value
                                alpha = 1f - p
                                if (!reduced) {
                                    translationX = -1.3f * size.width * p
                                    rotationZ = -10f * p
                                }
                            },
                    )
                }
                if (unwrap && band.value < 1f) {
                    Band(
                        count = count,
                        modifier = Modifier
                            .width(width + 18.dp)
                            .graphicsLayer {
                                val p = band.value
                                translationY = with(density) { height.toPx() } * 0.22f
                                alpha = 1f - p
                                if (!reduced) {
                                    translationX = 0.7f * size.width * p
                                    rotationZ = 8f * p
                                }
                            },
                    )
                }
                // What TalkBack hears as each card arrives: where it is in the deck and its name.
                Box(Modifier.size(1.dp).semantics { liveRegion = LiveRegionMode.Polite; contentDescription = spoken })
            }
        },
        buttons = {
            if (shown < 0) {
                LucentoPrimaryButton(
                    text = if (unwrap) ProWelcomeCopy.OPEN_DECK else ProWelcomeCopy.REVEAL_FIRST,
                    onClick = { if (!busy) onIndex(0) },
                    modifier = Modifier.weight(1f),
                )
            } else {
                // Previous keeps its room from the first card on, so Next never moves under the thumb.
                Box(Modifier.width(120.dp), contentAlignment = Alignment.CenterStart) {
                    if (shown > 0) {
                        LucentoTextButton(text = ProWelcomeCopy.PREVIOUS, onClick = ::previous, color = OnDim)
                    }
                }
                LucentoPrimaryButton(
                    text = if (last) ProWelcomeCopy.TO_COLLECTION else ProWelcomeCopy.NEXT,
                    onClick = ::next,
                    modifier = Modifier.weight(1f),
                )
            }
        },
    )
}

/** A card that turns over: its back until halfway, then its face (each face drawn the right way round). */
@Composable
private fun TurningCard(card: RevealCard, turn: Float, reduced: Boolean, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    Box(
        modifier.graphicsLayer {
            if (!reduced) {
                rotationY = turn * 180f
                cameraDistance = 12f * density.density
            }
        },
    ) {
        if (reduced) {
            // Cross-fade: no turning for anyone who asked for less motion.
            if (turn < 1f) PerkCardBack(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - turn })
            if (turn > 0f) PerkCardFront(card.stop, card.copy, Modifier.fillMaxSize().graphicsLayer { alpha = turn })
        } else if (turn < 0.5f) {
            PerkCardBack(Modifier.fillMaxSize())
        } else {
            PerkCardFront(card.stop, card.copy, Modifier.fillMaxSize().graphicsLayer { rotationY = 180f })
        }
    }
}

/** The gold band around the closed deck: the approved symbol on a small ivory disc, and "Lucento Pro · 10 perks". */
@Composable
private fun Band(count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.verticalGradient(listOf(AwardArtwork.GoldHighlight, AwardArtwork.GoldLight, AwardArtwork.GoldDeep)))
            .clearAndSetSemantics {}
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(AwardArtwork.Paper), contentAlignment = Alignment.Center) {
            LucentoMark(size = 22.dp, animate = false)
        }
        Spacer(Modifier.width(Spacing.sm))
        Text(
            ProWelcomeCopy.band(count).uppercase(),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.12.em),
            color = AwardArtwork.StripeNavy,
        )
    }
}
