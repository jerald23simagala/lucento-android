package com.lucento.app.feature.pro

import androidx.compose.runtime.compositionLocalOf
import com.lucento.app.domain.entitlement.EntitlementState

/**
 * Lucento Pro: what this phone's plan holds, provided once by MainActivity so any screen can show a Pro badge, a plan row
 * or a locked sheet. Display only — repositories and the server decide access (docs/MONETIZATION.md §5.2). Free in
 * previews and tests.
 */
val LocalEntitlements = compositionLocalOf { EntitlementState.FREE }
