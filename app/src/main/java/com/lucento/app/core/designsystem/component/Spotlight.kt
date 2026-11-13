package com.lucento.app.core.designsystem.component

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Lets an overlay find a control on whatever screen is showing: Lucento Pro's badge flies from the award to the profile
 * picture. A screen marks a control with [spotlightTarget]; the overlay reads where it is (window coordinates) with
 * [boundsOf]. Several controls may share a key (every primary screen has a profile button): the one positioned most
 * recently wins, and one leaving the screen takes only itself away.
 */
@Stable
class SpotlightRegistry {
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
    /** The profile picture, where the Pro badge lands. */
    const val PROFILE = "profile"
}

/** Marks this control as [key] for the app's overlays (see [SpotlightRegistry]). Changes nothing on its own. */
fun Modifier.spotlightTarget(key: String): Modifier = composed {
    val registry = LocalSpotlight.current ?: return@composed Modifier
    val id = remember(registry) { registry.newId() }
    DisposableEffect(registry, id) { onDispose { registry.remove(id) } }
    Modifier.onGloballyPositioned { registry.report(id, key, it.boundsInWindow()) }
}
