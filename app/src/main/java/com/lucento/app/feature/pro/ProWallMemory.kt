package com.lucento.app.feature.pro

import androidx.compose.runtime.staticCompositionLocalOf
import com.lucento.app.domain.entitlement.ProFeature
import java.util.concurrent.ConcurrentHashMap

/**
 * Which Pro walls the person has met in this app session (owner, 2026-11-19): the first tap on a feature shows the
 * dialog, later taps a short message with Unlock. In memory only: it resets when Lucento restarts, and nothing is stored.
 * [onFirstShown] hears each feature's first dialog this session; the app container sends it as the opt-in usage count
 * `pro_dialog_shown` (docs/growth/CRO_PLAN.md, CRO-2), which does nothing while counts are off.
 */
class ProWallMemory(private val onFirstShown: (ProFeature) -> Unit = {}) {
    private val shown: MutableSet<ProFeature> = ConcurrentHashMap.newKeySet()

    /** Whether [feature]'s dialog has been shown this session. */
    fun met(feature: ProFeature): Boolean = feature in shown

    /** Remembers that [feature]'s dialog was shown (called once it is on screen, not while it is only composed). */
    fun markMet(feature: ProFeature) {
        if (shown.add(feature)) onFirstShown(feature)
    }
}

/** The session's memory, provided by MainActivity from the app container; a fresh one in previews and tests. */
val LocalProWallMemory = staticCompositionLocalOf { ProWallMemory() }
