package com.lucento.app.feature.pro

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.lucento.app.domain.entitlement.EntitlementState
import com.lucento.app.domain.entitlement.ProFeature

/**
 * Lucento Pro: what this phone's plan holds, provided once by MainActivity so any screen can show a Pro badge, a plan row
 * or a lock note. Display only — repositories and the server decide access (docs/MONETIZATION.md §5.2). Free in
 * previews and tests.
 */
val LocalEntitlements = compositionLocalOf { EntitlementState.FREE }

/**
 * Opens the Lucento Pro page; the [ProFeature] that led there (or null) is named at its top. Provided by LucentoRoot, so a
 * lock note anywhere can link to it without every screen taking a callback. A no-op only outside the app (previews).
 */
val LocalOpenPro = staticCompositionLocalOf<(ProFeature?) -> Unit> { {} }
