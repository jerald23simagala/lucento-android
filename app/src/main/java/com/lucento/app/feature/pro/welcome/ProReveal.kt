package com.lucento.app.feature.pro.welcome

import android.os.SystemClock
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.min
import com.lucento.app.core.designsystem.component.LucentoMark
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.component.rememberHaptics
import com.lucento.app.core.designsystem.theme.AwardArtwork
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Sizes
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.domain.entitlement.ProTourStop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One card of the deck: what it is and its words. */
internal data class RevealCard(val stop: ProTourStop, val copy: ProWelcomeCopy.StopCopy)

/**
 * The welcome's stage, the same for every moment (cards, collection, award), so it never brightens or darkens in
 * between. Solid: nothing beneath (balances on Home) shows at all (critic round 3 could make out the app at 0.92, and
 * 0.97 still left a 1.04 : 1 ghost on the captures).
 */
internal const val WelcomeDim = 1f

/** Words on the dim: light in every theme, because the dim is dark in every theme. */
private val OnDim = Color(0xFFF8FAFF)
private val OnDimMuted = Color(0xDBF8FAFF)

/** The headline grows with the text size up to this much, as Android's own large text does; beyond it a long word breaks. */
private const val HeadlineScaleCap = 1.5f

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
 * The welcome's stage, shared by the deck and the collection: the dim, a top band (at least 112 dp, so its words can
 * change without the middle shifting; when large text makes it taller it eases to its new height), the middle, and a
 * row of buttons. [skip] sits at the band's right end, beside the progress line; [skipShown] false keeps its room so
 * nothing moves when it goes. [eyebrowSpoken], when set, makes the eyebrow the welcome's one live region: TalkBack hears
 * it each time it changes ("2 of 6. Count everything you have."), and nothing else is announced, so nothing is heard
 * twice.
 */
@Composable
internal fun WelcomeStage(
    eyebrow: String,
    title: String?,
    sub: String?,
    pane: String,
    skip: (() -> Unit)? = null,
    skipShown: Boolean = skip != null,
    eyebrowSpoken: String? = null,
    middle: @Composable BoxScope.() -> Unit,
    buttons: @Composable RowScope.() -> Unit,
) {
    val density = LocalDensity.current
    val reduced = LucentoTheme.reducedMotion
    val headline = MaterialTheme.typography.headlineSmall.capped(density.fontScale, HeadlineScaleCap)
        .copy(fontWeight = FontWeight.ExtraBold, lineBreak = LineBreak.Heading, hyphens = Hyphens.Auto)
    // The words keep clear of Skip however large the text makes it (a fixed margin let them meet at 200 %).
    val measurer = rememberTextMeasurer()
    val skipStyle = MaterialTheme.typography.labelLarge
    val side = if (skip != null) {
        val label = with(density) { measurer.measure(ProWelcomeCopy.SKIP, skipStyle).size.width.toDp() }
        maxOf(72.dp, label + TextButtonPadding + Spacing.xs + Spacing.sm)
    } else {
        Spacing.xl
    }
    Box(Modifier.fillMaxSize().semantics { paneTitle = pane }) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = WelcomeDim))
                .blockTouches(),
        )
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .then(if (reduced) Modifier else Modifier.animateContentSize(tween(420, easing = FastOutSlowInEasing)))
                    .heightIn(min = 112.dp),
            ) {
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(start = side, end = side, top = Spacing.lg, bottom = Spacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (eyebrowSpoken != null) {
                        // Always there (no height while empty), so its words change in place and TalkBack hears them.
                        Text(
                            eyebrow,
                            style = MaterialTheme.typography.labelLarge.copy(lineBreak = LineBreak.Heading),
                            color = AwardArtwork.GoldLight,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .then(if (eyebrow.isEmpty()) Modifier.height(0.dp) else Modifier)
                                .clearAndSetSemantics {
                                    liveRegion = LiveRegionMode.Polite
                                    if (eyebrowSpoken.isNotEmpty()) contentDescription = eyebrowSpoken
                                },
                        )
                    } else if (eyebrow.isNotEmpty()) {
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
                            style = headline,
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

/** This style, grown with the text size only up to [cap] times. */
private fun TextStyle.capped(fontScale: Float, cap: Float): TextStyle {
    val k = cap / fontScale
    if (k >= 1f || !fontSize.isSpecified) return this
    return copy(fontSize = fontSize * k, lineHeight = if (lineHeight.isSpecified) lineHeight * k else lineHeight)
}

/**
 * The deck (owner's pick, 2026-11-12: "Deck, unwrapped"): the cards in a gold band; one tap slides the band off and turns
 * the first card, with the only burst of the welcome; then one card at a time. Next (or a swipe to the left) moves on,
 * Previous (or a swipe to the right) goes back, Skip and the system Back key go to the collection ([onCollection]).
 * [index] is the card face up on top, -1 while the deck is closed; the host keeps it across rotation. A tap that lands
 * while a card is still turning is kept and acted on when the card has turned (critic round 3: dropped taps still
 * buzzed), unless it comes within the turn's first moment, which is a double tap: a double tap never skips a card.
 * Reduced motion: nothing turns or slides; faces cross-fade.
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
    var stepStarted by remember { mutableLongStateOf(0L) }
    // A step asked for while a card was turning: +1 Next, -1 Previous.
    var queued by remember { mutableIntStateOf(0) }
    // The step came from a swipe: the deck gives the haptic itself (a button gives its own).
    var swiped by remember { mutableStateOf(false) }

    fun keep(step: Int) {
        if (SystemClock.uptimeMillis() - stepStarted >= DoubleTapMillis) queued = step
    }

    fun next() {
        if (busy) return keep(1)
        if (shown >= count - 1) onCollection() else onIndex(shown + 1)
    }

    fun previous() {
        if (shown <= 0) return
        if (busy) return keep(-1)
        onIndex(shown - 1)
    }

    LaunchedEffect(index) {
        val target = index.coerceIn(-1, count - 1)
        val from = shown
        if (target == from) return@LaunchedEffect
        busy = true
        stepStarted = SystemClock.uptimeMillis()
        if (swiped) haptics.tick()
        if (target > from) {
            if (from < 0) {
                if (unwrap) {
                    if (reduced) band.snapTo(1f) else launch { band.animateTo(1f, tween(420, easing = FastOutLinearInEasing)) }
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
        busy = false
        swiped = false
        val step = queued
        queued = 0
        when (step) {
            1 -> next()
            -1 -> previous()
        }
    }

    val last = shown == count - 1
    val bandOn = unwrap && band.value < 1f
    WelcomeStage(
        eyebrow = if (shown >= 0) ProWelcomeCopy.progress(shown, count) else "",
        title = if (shown < 0) title else null,
        // The band says how many perks (and tells TalkBack), so the words above don't repeat it while it is on.
        sub = if (shown < 0 && !bandOn) body else null,
        pane = title,
        skip = onCollection,
        skipShown = !last,
        eyebrowSpoken = if (shown >= 0) "${ProWelcomeCopy.progress(shown, count)}. ${cards[shown].copy.title}." else "",
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
                        // The band says "Lucento Pro" while it is on, so the backs don't say it beneath it too.
                        backLabel = !bandOn,
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
                            .then(if (up) Modifier else Modifier.clearAndSetSemantics {})
                            .then(
                                if (up) {
                                    Modifier.pointerInput(shown) {
                                        val threshold = 72.dp.toPx()
                                        detectHorizontalDragGestures(
                                            onDragEnd = {
                                                val dx = drag.value
                                                when {
                                                    dx < -threshold && !busy -> {
                                                        swiped = true
                                                        next()
                                                    }
                                                    dx > threshold && shown > 0 && !busy -> {
                                                        swiped = true
                                                        previous()
                                                    }
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
                if (bandOn) {
                    val middle = maxHeight
                    Band(
                        count = count,
                        spoken = body,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            // Placed by layout just under the crest on the top card's back (its disc ends 24 dp below
                            // the card's middle), and kept on the card when the card is short (large text, a small or
                            // turned phone), even if it then covers part of the crest.
                            .layout { measurable, constraints ->
                                val band = measurable.measure(constraints)
                                val centre = middle.roundToPx() / 2
                                val under = centre + 38.dp.roundToPx()
                                val lowest = centre + height.roundToPx() / 2 - band.height - 8.dp.roundToPx()
                                layout(band.width, band.height) { band.place(0, minOf(under, lowest).coerceAtLeast(0)) }
                            }
                            .width(width + 18.dp)
                            .graphicsLayer {
                                val p = band.value
                                alpha = 1f - p
                                if (!reduced) {
                                    translationX = 0.7f * size.width * p
                                    rotationZ = 8f * p
                                }
                            },
                    )
                }
            }
        },
        buttons = {
            DeckButtons(
                closed = shown < 0,
                closedLabel = if (unwrap) ProWelcomeCopy.OPEN_DECK else ProWelcomeCopy.REVEAL_FIRST,
                primary = when {
                    shown < 0 -> if (unwrap) ProWelcomeCopy.OPEN_DECK else ProWelcomeCopy.REVEAL_FIRST
                    last -> ProWelcomeCopy.TO_COLLECTION
                    else -> ProWelcomeCopy.NEXT
                },
                onPrimary = { if (shown < 0) { if (!busy) onIndex(0) } else next() },
                previousShown = shown > 0,
                onPrevious = ::previous,
                modifier = Modifier.weight(1f),
            )
        },
    )
}

/** A second tap sooner than this after a step began is the same tap twice, not a second step. */
private const val DoubleTapMillis = 250L

/** The room the shared buttons give their labels (Buttons.kt): a filled button 24 dp a side, a text button 12. */
private val ButtonPadding = 48.dp
private val TextButtonPadding = 24.dp
private val TextButtonMinWidth = 58.dp

private data class DeckButtonLayout(val stacked: Boolean, val slot: Dp, val primaryMin: Dp, val previousMin: Dp)

/**
 * The deck's buttons. Next keeps one place and one size from the first card to the last (and from the closed deck), so
 * it never moves under the thumb: Previous keeps its room from the start, and Next is as tall as its longest label
 * needs. Side by side while every label fits on one line; stacked, Next above Previous and each the full width, when
 * one doesn't (large text, a narrow phone: critic round 3 found "Previou / s" and "See your" at 200 %).
 */
@Composable
private fun DeckButtons(
    closed: Boolean,
    /** The closed deck's label: with Next and "See your collection", the labels this deck can show. */
    closedLabel: String,
    primary: String,
    onPrimary: () -> Unit,
    previousShown: Boolean,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = MaterialTheme.typography.titleMedium
    val previousStyle = MaterialTheme.typography.labelLarge
    BoxWithConstraints(modifier) {
        val full = maxWidth
        val layout = remember(full, density, labelStyle, previousStyle, closedLabel) {
            fun measure(text: String, style: TextStyle, width: Dp = Dp.Infinity) = with(density) {
                val constraints = if (width == Dp.Infinity) Constraints() else Constraints(maxWidth = width.roundToPx().coerceAtLeast(1))
                measurer.measure(text, style, constraints = constraints).size.let { it.width.toDp() to it.height.toDp() }
            }
            val previous = measure(ProWelcomeCopy.PREVIOUS, previousStyle)
            val slot = maxOf(TextButtonMinWidth, previous.first + TextButtonPadding + 2.dp)
            val previousMin = maxOf(Sizes.minTouch, previous.second + 16.dp)
            val beside = full - slot - Spacing.sm
            val onCards = listOf(ProWelcomeCopy.NEXT, ProWelcomeCopy.TO_COLLECTION)
            val onDeck = listOf(closedLabel)
            val fits = onCards.all { measure(it, labelStyle).first + ButtonPadding <= beside } &&
                onDeck.all { measure(it, labelStyle).first + ButtonPadding <= full }
            if (fits) {
                DeckButtonLayout(stacked = false, slot = slot, primaryMin = Sizes.button, previousMin = previousMin)
            } else {
                val tallest = (onCards + onDeck).maxOf { measure(it, labelStyle, full - ButtonPadding).second }
                DeckButtonLayout(stacked = true, slot = slot, primaryMin = maxOf(Sizes.button, tallest + 16.dp), previousMin = previousMin)
            }
        }
        if (!layout.stacked) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (!closed) {
                    Box(Modifier.width(layout.slot).heightIn(min = Sizes.button), contentAlignment = Alignment.CenterStart) {
                        if (previousShown) LucentoTextButton(text = ProWelcomeCopy.PREVIOUS, onClick = onPrevious, color = OnDim)
                    }
                    Spacer(Modifier.width(Spacing.sm))
                }
                LucentoPrimaryButton(text = primary, onClick = onPrimary, modifier = Modifier.weight(1f).heightIn(min = layout.primaryMin))
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                LucentoPrimaryButton(text = primary, onClick = onPrimary, modifier = Modifier.heightIn(min = layout.primaryMin))
                Box(Modifier.fillMaxWidth().heightIn(min = layout.previousMin), contentAlignment = Alignment.Center) {
                    if (previousShown) {
                        LucentoTextButton(text = ProWelcomeCopy.PREVIOUS, onClick = onPrevious, color = OnDim, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

/** A card that turns over: its back until halfway, then its face (each face drawn the right way round). */
@Composable
private fun TurningCard(card: RevealCard, turn: Float, reduced: Boolean, modifier: Modifier = Modifier, backLabel: Boolean = true) {
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
            if (turn < 1f) PerkCardBack(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - turn }, showLabel = backLabel)
            if (turn > 0f) PerkCardFront(card.stop, card.copy, Modifier.fillMaxSize().graphicsLayer { alpha = turn })
        } else if (turn < 0.5f) {
            PerkCardBack(Modifier.fillMaxSize(), showLabel = backLabel)
        } else {
            PerkCardFront(card.stop, card.copy, Modifier.fillMaxSize().graphicsLayer { rotationY = 180f })
        }
    }
}

/**
 * The gold band around the closed deck: the approved symbol on a small ivory disc, and "Lucento Pro · 10 perks". TalkBack
 * hears [spoken] ("Your Pro deck holds 10 perks."); the printed label is a wide, spaced-out mark, so it grows with the
 * text size only up to 1.3 times.
 */
@Composable
private fun Band(count: Int, spoken: String, modifier: Modifier = Modifier) {
    val fontScale = LocalDensity.current.fontScale
    Row(
        modifier
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.verticalGradient(listOf(AwardArtwork.GoldHighlight, AwardArtwork.GoldLight, AwardArtwork.GoldDeep)))
            .clearAndSetSemantics { contentDescription = spoken }
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(AwardArtwork.Paper), contentAlignment = Alignment.Center) {
            LucentoMark(size = 22.dp, animate = false)
        }
        Spacer(Modifier.width(Spacing.sm))
        Text(
            ProWelcomeCopy.band(count).uppercase(),
            style = MaterialTheme.typography.labelLarge.capped(fontScale, 1.3f)
                .copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.12.em, lineBreak = LineBreak.Heading),
            color = AwardArtwork.StripeNavy,
            textAlign = TextAlign.Center,
        )
    }
}
