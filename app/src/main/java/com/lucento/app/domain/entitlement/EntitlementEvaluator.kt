package com.lucento.app.domain.entitlement

/** The answer a gate gets. Repositories act on it; composables only read it to show a badge or a sheet. */
sealed interface Access {
    data object Allowed : Access

    /** Not on this plan at all; [requires] is what unlocks it. */
    data class Locked(val feature: ProFeature, val requires: Capability) : Access

    /**
     * The plan allows [limit] and [used] are in use. [withUpgrade] is what the required capability would allow — the same
     * limit when no plan lifts it (20 goals is the most on any plan), so copy can tell the two apart.
     */
    data class LimitReached(val feature: ProFeature, val used: Int, val limit: Limit, val withUpgrade: Limit) : Access {
        val upgradeHelps: Boolean get() = withUpgrade != limit
    }

    val isAllowed: Boolean get() = this is Allowed
}

/**
 * Pure: (what the person holds, what they want to do, how much they use already, when) → [Access]. No I/O, no clock of
 * its own, so every state × feature × usage combination is a JVM test.
 */
object EntitlementEvaluator {
    fun access(state: EntitlementState, feature: ProFeature, used: Int, now: Long): Access =
        access(state.capabilities(now), feature, used)

    fun access(capabilities: Set<Capability>, feature: ProFeature, used: Int): Access {
        val limit = PlanCatalog.limit(feature, capabilities)
        if (limit.allows(used)) return Access.Allowed
        if (limit == Limit.None) return Access.Locked(feature, feature.requires)
        return Access.LimitReached(
            feature = feature,
            used = used,
            limit = limit,
            withUpgrade = PlanCatalog.limit(feature, capabilities + feature.requires),
        )
    }
}
