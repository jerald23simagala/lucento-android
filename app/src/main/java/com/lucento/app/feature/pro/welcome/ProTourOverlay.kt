package com.lucento.app.feature.pro.welcome

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.component.LucentoMascot
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.component.MascotMood
import com.lucento.app.core.designsystem.component.SpotlightRegistry
import com.lucento.app.core.designsystem.component.SpotlightScrim
import com.lucento.app.core.designsystem.theme.Elevation
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.designsystem.theme.ThemeSpec
import com.lucento.app.data.preferences.ThemeId
import com.lucento.app.domain.entitlement.ProTourStop
import kotlin.math.roundToInt

/** How dark the screen goes around what the tour lights up (the theme's scrim colour): darker at night, so the page
 * beneath recedes and the card stands apart. */
@Composable
internal fun tourScrimAlpha(): Float = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) 0.8f else 0.66f

@Composable
private fun scrimAlpha(): Float = tourScrimAlpha()

/** The tour's cards: a raised surface with a hairline, so they separate from the dimmed page in both themes. */
@Composable
private fun cardBorder() = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

/** Takes every touch, so nothing beneath the tour is changed by accident; only its own buttons respond. */
/** The even gap between a lit control and its ring. */
private val RingGap = 6.dp

private fun Modifier.blockTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent().changes.forEach { it.consume() }
    }
}

/**
 * The moment before the tour: the screen dims and Lucento says what just happened. It moves on to the first stop by
 * itself after a moment ([ProWelcomeHost]); "Show me" goes now, "Skip the tour" goes straight to the summary.
 */
@Composable
internal fun ProTourCurtain(title: String, body: String, celebrate: Boolean, onShowMe: () -> Unit, onSkip: () -> Unit) {
    val reduced = LucentoTheme.reducedMotion
    val appear = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
    Box(Modifier.fillMaxSize().blockTouches(), contentAlignment = Alignment.Center) {
        SpotlightScrim(
            hole = null,
            scrim = MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha() * appear.value),
            ring = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxSize(),
        )
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = cardBorder(),
            shadowElevation = Elevation.lg,
            modifier = Modifier
                .padding(horizontal = Spacing.gutter)
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = appear.value
                    val s = 0.94f + 0.06f * appear.value
                    scaleX = s
                    scaleY = s
                }
                .semantics { paneTitle = "Lucento Pro" },
        ) {
            Column(
                Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LucentoMascot(size = 104.dp, mood = if (celebrate) MascotMood.Celebrate else MascotMood.Greeting, animate = !reduced)
                Spacer(Modifier.height(Spacing.md))
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                    },
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Spacing.lg))
                LucentoPrimaryButton(text = "Show me", onClick = onShowMe)
                LucentoTextButton(text = "Skip the tour", onClick = onSkip)
            }
        }
    }
}

/**
 * One stop of the tour over the real screen: everything dims except the control Pro opened, ringed in the primary
 * colour, and a caption card beside it says what changed. The lit window glides from stop to stop; until the control has
 * been found (the screen is still arriving or scrolling it into view) the card waits in the middle.
 */
@Composable
internal fun ProTourStopOverlay(
    stop: ProTourStop,
    index: Int,
    total: Int,
    copy: ProWelcomeCopy.StopCopy,
    registry: SpotlightRegistry,
    onNext: () -> Unit,
    onBack: (() -> Unit)?,
    onSkip: () -> Unit,
    lastStop: Boolean,
) {
    val reduced = LucentoTheme.reducedMotion
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    // Where the control is right now: absent while its screen is still arriving, then following it as it scrolls into
    // view. (No timed wait: the window follows the control's own reports.)
    val raw = registry.boundsOf(stop.spotlightKey)?.translate(-origin)
    val hole = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
    // The stop whose control the window shows, and whether a window was showing at all.
    var holeFor by remember { mutableStateOf<ProTourStop?>(null) }
    var holeVisible by remember { mutableStateOf(false) }
    LaunchedEffect(raw, stop) {
        val target = raw
        if (target == null) {
            holeVisible = false
            return@LaunchedEffect
        }
        // From one control to the next on the same screen the window glides; after a new screen, or while a control
        // scrolls into view, it simply follows.
        val glide = !reduced && holeVisible && holeFor != null && holeFor != stop
        holeFor = stop
        if (glide) hole.animateTo(target, tween(320, easing = FastOutSlowInEasing)) else hole.snapTo(target)
        holeVisible = true
    }
    val lit = if (raw != null && (holeFor == stop || holeVisible)) hole.value else null
    val appear = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(300)) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.boundsInWindow().topLeft }
            .blockTouches(),
    ) {
        // Lit only where it can be seen: the window and its ring stay inside the screen, below the status bar and above
        // the navigation bar (a full-width control, like the composer's bar, is trimmed at the sides).
        val ringGap = with(density) { RingGap.toPx() }
        val safeTop = WindowInsets.safeDrawing.getTop(density).toFloat() + ringGap
        val safeBottom = with(density) { maxHeight.toPx() } - WindowInsets.safeDrawing.getBottom(density) - ringGap
        val safeRight = with(density) { maxWidth.toPx() } - ringGap
        val shown = lit?.let {
            Rect(maxOf(it.left, ringGap), maxOf(it.top, safeTop), minOf(it.right, safeRight), minOf(it.bottom, safeBottom))
        }?.takeIf { !it.isEmpty }
        SpotlightScrim(
            hole = shown,
            padding = RingGap,
            scrim = MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha() * appear.value),
            ring = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxSize(),
        )
        val height = with(density) { maxHeight.toPx() }
        val top = WindowInsets.safeDrawing.getTop(density) + with(density) { Spacing.md.toPx() }
        val bottom = height - WindowInsets.safeDrawing.getBottom(density) - with(density) { Spacing.md.toPx() }
        val gap = with(density) { Spacing.lg.toPx() }
        var cardHeight by remember { mutableIntStateOf(0) }
        val y = when {
            shown == null -> (height - cardHeight) / 2f
            // The control sits in the top half: the card goes under it; otherwise above it.
            shown.center.y < height / 2f -> (shown.bottom + gap).coerceAtMost(bottom - cardHeight)
            else -> (shown.top - gap - cardHeight).coerceAtLeast(top)
        }.coerceIn(top, (bottom - cardHeight).coerceAtLeast(top))
        val cardY by androidx.compose.animation.core.animateFloatAsState(
            targetValue = y,
            animationSpec = if (reduced) androidx.compose.animation.core.snap() else tween(320, easing = FastOutSlowInEasing),
            label = "caption",
        )
        CaptionCard(
            progress = ProWelcomeCopy.progress(stop, index, total),
            index = index,
            total = total,
            copy = copy,
            onNext = onNext,
            onBack = onBack,
            onSkip = onSkip,
            lastStop = lastStop,
            maxHeight = with(density) { (bottom - top).coerceAtLeast(0f).toDp() },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, cardY.roundToInt()) }
                .onSizeChanged { cardHeight = it.height }
                .graphicsLayer { alpha = appear.value },
        )
    }
}

@Composable
private fun CaptionCard(
    progress: String,
    index: Int,
    total: Int,
    copy: ProWelcomeCopy.StopCopy,
    onNext: () -> Unit,
    onBack: (() -> Unit)?,
    onSkip: () -> Unit,
    lastStop: Boolean,
    /** The room there is: beyond it the words scroll and the buttons stay in view (large text, small phones). */
    maxHeight: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp.Unspecified,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = cardBorder(),
        shadowElevation = Elevation.lg,
        modifier = modifier
            .padding(horizontal = Spacing.gutter)
            .widthIn(max = 440.dp)
            .fillMaxWidth()
            .semantics { paneTitle = "Lucento Pro tour" },
    ) {
        Column(
            Modifier
                .then(if (maxHeight != androidx.compose.ui.unit.Dp.Unspecified) Modifier.heightIn(max = maxHeight) else Modifier)
                .padding(start = Spacing.lg, end = Spacing.sm, top = Spacing.xs, bottom = Spacing.lg),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    progress,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f),
                )
                if (!lastStop) {
                    LucentoTextButton(text = ProWelcomeCopy.END_TOUR, onClick = onSkip)
                } else {
                    Spacer(Modifier.height(com.lucento.app.core.designsystem.theme.Sizes.minTouch))
                }
            }
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(end = Spacing.sm),
            ) {
                Segments(index, total)
                Spacer(Modifier.height(Spacing.md))
                // What it is and where it lives, announced together when the stop changes (TalkBack can't see the light).
                Column(
                    Modifier.semantics(mergeDescendants = true) {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                    },
                ) {
                    Text(copy.title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.height(Spacing.xs))
                    Text(copy.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Spacing.sm))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Outlined.Place,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 2.dp).size(16.dp),
                        )
                        Spacer(Modifier.width(Spacing.xs))
                        Text(copy.where, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                    }
                }
                if (copy.changes.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.md))
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) { copy.changes.forEach { ChangeRow(it) } }
                }
                if (copy.themes) {
                    Spacer(Modifier.height(Spacing.md))
                    ThemeSwatches()
                }
            }
            Column(Modifier.padding(end = Spacing.sm)) {
                Spacer(Modifier.height(Spacing.lg))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) {
                        LucentoTextButton(text = "Back", onClick = onBack)
                        Spacer(Modifier.width(Spacing.sm))
                    }
                    LucentoPrimaryButton(
                        text = if (lastStop) ProWelcomeCopy.LAST_STOP_BUTTON else "Next",
                        onClick = onNext,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** The tour's progress as a row of short bars, the current one and those before it filled. Decorative: the line above says it. */
@Composable
private fun Segments(index: Int, total: Int) {
    val filled = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant
    Row(
        Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        repeat(total) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i <= index) filled else track),
            )
        }
    }
}

/** "Money wallets   3 → Unlimited": the free plan's allowance, muted, then this plan's, in the primary colour. */
@Composable
private fun ChangeRow(change: ProWelcomeCopy.Change) {
    Row(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = "${change.label}: ${change.before} on the free plan, ${change.after} now" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(change.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(change.before, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.xs).size(14.dp),
        )
        Text(
            change.after,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Grove, Mono and Aurora as a legend: a small dot of each theme's colours beside its name (a picture, not buttons). */
@Composable
private fun ThemeSwatches() {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clearAndSetSemantics {},
    ) {
        listOf(ThemeId.GROVE, ThemeId.MONO, ThemeId.AURORA).forEach { id ->
            val spec = ThemeSpec.of(id)
            val colors = spec.colors(dark)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(12.dp).clip(CircleShape)) {
                    drawRect(colors.scheme.primary, size = size.copy(width = size.width / 2f))
                    drawRect(colors.extended.accent, topLeft = Offset(size.width / 2f, 0f), size = size.copy(width = size.width / 2f))
                }
                Spacer(Modifier.width(Spacing.xs))
                Text(spec.displayName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
