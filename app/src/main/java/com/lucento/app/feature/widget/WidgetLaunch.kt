package com.lucento.app.feature.widget

import com.lucento.app.feature.capture.ComposerStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where a home-screen widget tap lands (docs/WIDGETS.md). Widgets only open Lucento: nothing is read, written or saved
 * from the home screen, so a tap is just a shorter way into the composer, the Budget page or Home.
 */
sealed interface WidgetLaunch {
    /** The composer, ready to type, listening, or taking a receipt photo. */
    data class Composer(val start: ComposerStart) : WidgetLaunch

    /** The Budget page (the widget's "Set amounts"). */
    data object Budget : WidgetLaunch

    /** Choosing expense categories, before there are any (Home's "Choose categories"). */
    data object Categories : WidgetLaunch

    /** Home, whose Monthly budget card the Safe to spend widget summarises. */
    data object Home : WidgetLaunch

    /** The text form carried in a widget's intent: "composer:SCAN", "budget", "categories" or "home". */
    val key: String
        get() = when (this) {
            is Composer -> COMPOSER_PREFIX + start.name
            Budget -> "budget"
            Categories -> "categories"
            Home -> "home"
        }

    companion object {
        const val ACTION = "com.lucento.app.action.WIDGET"
        const val EXTRA_TARGET = "com.lucento.app.extra.WIDGET_TARGET"
        private const val COMPOSER_PREFIX = "composer:"

        /** Reads [key] back. Anything unknown opens Lucento as it is (null), never a guessed screen. */
        fun fromKey(key: String?): WidgetLaunch? = when {
            key == null -> null
            key.startsWith(COMPOSER_PREFIX) ->
                ComposerStart.entries.firstOrNull { it.name == key.removePrefix(COMPOSER_PREFIX) }?.let(::Composer)
            key == "budget" -> Budget
            key == "categories" -> Categories
            key == "home" -> Home
            else -> null
        }
    }
}

/**
 * Widget taps waiting for the app's navigation: MainActivity offers each one, the main scaffold takes it once.
 * Process-wide on purpose, because a tap can arrive before the scaffold (and its ViewModels) exist.
 */
object WidgetLaunches {
    private val _pending = MutableStateFlow<WidgetLaunch?>(null)
    val pending: StateFlow<WidgetLaunch?> = _pending.asStateFlow()

    fun offer(launch: WidgetLaunch) {
        _pending.value = launch
    }

    /** Takes [launch] if it is still the pending one; a newer tap stays for the next round. */
    fun consume(launch: WidgetLaunch): Boolean = _pending.compareAndSet(launch, null)
}
