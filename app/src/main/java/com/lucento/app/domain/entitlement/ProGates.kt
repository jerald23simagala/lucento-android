package com.lucento.app.domain.entitlement

/**
 * The plan decisions that are not a count in a repository (docs/MONETIZATION.md §5.2): pure functions, so ViewModels
 * and the application container call one line and every rule is a JVM test. Each takes the person's [EntitlementState]
 * and the clock; none reads anything else.
 */
object ProGates {
    /**
     * Whether this phone may sync at all under its plan. A Partner in a Shared Space syncs on the Owner's plan (the
     * Partner needs no plan of their own; the Owner's lapse is lenient — docs §3.7, §6.4), so membership alone allows it.
     */
    fun syncAllowedByPlan(cloud: Boolean, partnerOfSpace: Boolean): Boolean = cloud || partnerOfSpace

    fun syncAllowedByPlan(state: EntitlementState, partnerOfSpace: Boolean, now: Long): Boolean =
        syncAllowedByPlan(state.has(Capability.CLOUD, now), partnerOfSpace)

    /** Restoring from the account on a new phone: the same rule as sync (a Partner joining their space always may). */
    fun restoreAllowedByPlan(state: EntitlementState, partnerOfSpace: Boolean, now: Long): Boolean =
        syncAllowedByPlan(state, partnerOfSpace, now)

    /** Insights periods: three months are free; six and twelve are Pro (docs §3.4). */
    fun insightsPeriodAllowed(months: Int, state: EntitlementState, now: Long): Boolean =
        months <= FREE_INSIGHTS_MONTHS || state.has(Capability.LOCAL_PRO, now)

    fun netWorthCompositionAllowed(state: EntitlementState, now: Long): Boolean = state.has(Capability.LOCAL_PRO, now)

    fun pdfAllowed(state: EntitlementState, now: Long): Boolean = state.has(Capability.LOCAL_PRO, now)

    /**
     * Sunrise and Midnight are free; Grove, Mono and Aurora are Pro (docs §3.8). Theme ids are strings here so the domain
     * stays free of the preferences layer; callers pass [com.lucento.app.data.preferences.ThemeId.name].
     */
    val PRO_THEME_IDS: Set<String> = setOf("GROVE", "MONO", "AURORA")

    fun themeIsPro(themeId: String): Boolean = themeId in PRO_THEME_IDS

    /** A locked theme already chosen stays until the person changes it (§6.3); this only decides a new choice. */
    fun themeAllowed(themeId: String, state: EntitlementState, now: Long): Boolean =
        !themeIsPro(themeId) || state.has(Capability.LOCAL_PRO, now)

    fun lockedThemeIds(state: EntitlementState, now: Long): Set<String> =
        if (state.has(Capability.LOCAL_PRO, now)) emptySet() else PRO_THEME_IDS

    const val FREE_INSIGHTS_MONTHS = 3
}
