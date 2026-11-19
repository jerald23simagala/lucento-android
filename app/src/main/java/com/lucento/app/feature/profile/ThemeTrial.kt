package com.lucento.app.feature.profile

import androidx.compose.runtime.staticCompositionLocalOf
import com.lucento.app.data.preferences.ThemeId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A Pro theme tried on (owner, 2026-11-19): Lucento shows it while the person looks, and nothing is saved. In memory
 * only: it ends when they're done, pick a theme of their own, or close the profile sheet, and never survives a restart.
 * The saved theme is untouched throughout ([com.lucento.app.AppViewModel.setThemeId] never persists a locked one).
 */
class ThemeTrial {
    private val current = MutableStateFlow<ThemeId?>(null)

    /** The theme being tried on, or null. */
    val trying: StateFlow<ThemeId?> = current.asStateFlow()

    fun start(theme: ThemeId) {
        current.value = theme
    }

    fun end() {
        current.value = null
    }

    private val pending = MutableStateFlow<ThemeId?>(null)

    /**
     * A theme tried on and taken to the plan page (See Lucento Plan from the try-on note): applied once the plan includes
     * it (owner, 2026-11-19). In memory only, like the trial; picking a theme of one's own drops it.
     */
    val afterPurchase: StateFlow<ThemeId?> = pending.asStateFlow()

    fun keepAfterPurchase(theme: ThemeId) {
        pending.value = theme
    }

    fun clearAfterPurchase() {
        pending.value = null
    }
}

/** Provided by MainActivity from the app container; a fresh one in previews and tests. */
val LocalThemeTrial = staticCompositionLocalOf { ThemeTrial() }
