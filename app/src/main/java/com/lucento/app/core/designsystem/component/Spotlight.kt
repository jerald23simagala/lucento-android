package com.lucento.app.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Lets an overlay find controls on whatever screen is showing: Lucento Pro's tour lights them up one by one, and the
 * Pro badge flies to the profile picture. A screen marks a control with [spotlightTarget]; the overlay reads where it is
 * (window coordinates) with [boundsOf], and setting [active] to its key scrolls it into view. Several controls may share
 * a key (every primary screen has a profile button): the one positioned most recently wins, and one leaving the screen
 * takes only itself away.
 */
@Stable
class SpotlightRegistry {
    /** The key being shown: its control scrolls itself into view. */
    var active by mutableStateOf<String?>(null)

    private val entries = mutableStateMapOf<Long, Entry>()
    private var nextId = 0L
    private var order = 0L

    internal fun newId(): Long = nextId++

    internal fun report(id: Long, key: String, bounds: Rect) {
        val current = entries[id]
        if (current != null && current.key == key && current.bounds == bounds) return
        entries[id] = Entry(key, bounds, order++)
    }

    internal fun remove(id: Long) {
        entries.remove(id)
    }

    /** Where [key]'s control is now, in window coordinates; null when none is on screen. */
    fun boundsOf(key: String): Rect? =
        entries.values.filter { it.key == key && !it.bounds.isEmpty }.maxByOrNull { it.order }?.bounds

    private data class Entry(val key: String, val bounds: Rect, val order: Long)
}

/** Provided once by the app shell; null elsewhere (previews, tests), where [spotlightTarget] does nothing. */
val LocalSpotlight = staticCompositionLocalOf<SpotlightRegistry?> { null }

/** The controls the app's overlays point at. */
object SpotlightKeys {
    const val PROFILE = "profile"
    const val WALLETS_ADD = "wallets-add"
    const val NET_WORTH = "net-worth"
    const val GOALS_ADD = "goals-add"
    const val INSIGHTS_PERIOD = "insights-period"
    const val REPORTS = "reports"
    const val AI_STATUS = "ai-status"
    const val SYNC = "sync"
    const val COMPOSER = "composer"
    const val SHARED = "shared"
}

/** Marks this control as [key] for the app's overlays (see [SpotlightRegistry]). Changes nothing on its own. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.spotlightTarget(key: String): Modifier = composed {
    val registry = LocalSpotlight.current ?: return@composed Modifier
    val id = remember(registry) { registry.newId() }
    val requester = remember { BringIntoViewRequester() }
    DisposableEffect(registry, id) { onDispose { registry.remove(id) } }
    val shown = registry.active == key
    val density = androidx.compose.ui.platform.LocalDensity.current
    var size by remember { androidx.compose.runtime.mutableStateOf(Size.Zero) }
    LaunchedEffect(shown) {
        if (!shown) return@LaunchedEffect
        // Room around it, so a control near the bottom isn't left under the floating dock (and the caption fits).
        val above = with(density) { 72.dp.toPx() }
        val below = with(density) { 200.dp.toPx() }
        runCatching { requester.bringIntoView(Rect(0f, -above, size.width, size.height + below)) }
    }
    Modifier
        .bringIntoViewRequester(requester)
        .onGloballyPositioned {
            size = Size(it.size.width.toFloat(), it.size.height.toFloat())
            registry.report(id, key, it.boundsInWindow())
        }
}

/**
 * A dimmed layer over the whole screen with a lit window around [hole] (in this layer's own coordinates), ringed in
 * [ring]. With no hole the screen is simply dimmed. Decorative: the overlay above it says what is lit.
 */
@Composable
fun SpotlightScrim(
    hole: Rect?,
    scrim: Color,
    ring: Color,
    modifier: Modifier = Modifier,
    padding: Dp = 6.dp,
    corner: Dp = 18.dp,
) {
    Canvas(modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
        drawRect(scrim)
        if (hole == null || hole.isEmpty) return@Canvas
        val pad = padding.toPx()
        val topLeft = Offset(hole.left - pad, hole.top - pad)
        val size = Size(hole.width + pad * 2, hole.height + pad * 2)
        // Small controls (a pill, a round avatar) get a pill or a circle; larger ones the card corner.
        val r = CornerRadius(minOf(corner.toPx() + pad, minOf(size.width, size.height) / 2f))
        drawRoundRect(Color.Black, topLeft = topLeft, size = size, cornerRadius = r, blendMode = BlendMode.Clear)
        drawRoundRect(ring, topLeft = topLeft, size = size, cornerRadius = r, style = Stroke(width = 2.dp.toPx()))
    }
}
