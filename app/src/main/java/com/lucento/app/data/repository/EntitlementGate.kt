package com.lucento.app.data.repository

import com.lucento.app.domain.entitlement.Access
import com.lucento.app.domain.entitlement.ProFeature

/**
 * Lucento Pro: whether this phone's plan allows one more of [ProFeature] when [used] are already active (docs/MONETIZATION.md
 * §5.2). Repositories ask before a *creation*; nothing existing is ever gated. Same shape as [StructureAccess], and like it,
 * the repository is the boundary — screens only read the plan to show a badge or a sheet.
 */
fun interface EntitlementGate {
    suspend fun check(feature: ProFeature, used: Int): Access

    companion object {
        /** Everything allowed: the default until the gates are switched on, and what tests use when the plan is not the point. */
        val Permissive = EntitlementGate { _, _ -> Access.Allowed }
    }
}
