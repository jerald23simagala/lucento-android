package com.lucento.app.domain.entitlement

/**
 * The one-time notice after a plan ends (docs/MONETIZATION.md §6.3 rule 7, customer copy D-7): told once, on the first
 * start after the change, what is now restricted — and that nothing was removed. Never for an upgrade, never twice.
 */
enum class PlanNotice {
    /** A subscription (or a grant) ended: back to the free plan. */
    PRO_ENDED,

    /** A Lifetime owner's online services ended (the included year, or Lucento Cloud); everything owned is unchanged. */
    SERVICES_ENDED,

    /** A Lifetime purchase is no longer active (refunded with revoke, or charged back). */
    LIFETIME_ENDED,

    /** A Pro Lifetime nobody could confirm for a month: paused until Google Play or the server answers (docs §5.3 L-2). */
    LIFETIME_NEEDS_CHECK,
}

object PlanNotices {
    /**
     * What to tell the person when the plan last seen on this phone was [previous] and it is now [current].
     * [lifetimeAwaitingCheck]: a Lifetime only paused for want of a check ([EntitlementState.lifetimeAwaitingCheck]).
     */
    fun after(previous: DisplayPlan?, current: DisplayPlan, lifetimeAwaitingCheck: Boolean = false): PlanNotice? {
        if (previous == null || previous == current) return null
        return when (previous) {
            DisplayPlan.FREE -> null
            DisplayPlan.PRO -> when (current) {
                DisplayPlan.FREE -> PlanNotice.PRO_ENDED
                DisplayPlan.LIFETIME -> PlanNotice.SERVICES_ENDED
                else -> null
            }
            DisplayPlan.LIFETIME -> when (current) {
                DisplayPlan.FREE -> lifetimeEnded(lifetimeAwaitingCheck)
                else -> null
            }
            DisplayPlan.LIFETIME_WITH_SERVICES -> when (current) {
                DisplayPlan.FREE -> lifetimeEnded(lifetimeAwaitingCheck)
                DisplayPlan.LIFETIME -> PlanNotice.SERVICES_ENDED
                else -> null
            }
        }
    }

    private fun lifetimeEnded(awaitingCheck: Boolean): PlanNotice =
        if (awaitingCheck) PlanNotice.LIFETIME_NEEDS_CHECK else PlanNotice.LIFETIME_ENDED
}
