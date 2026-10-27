package com.lucento.app.domain

/**
 * Lucento's light: how this month's budget is going, expressed as one of the mascot's five wellness tiers
 * (docs/mascot). It describes a circumstance, never a judgement. Derived only from the same [BudgetHealth] the
 * hero card, burn bar and Budget screen already use, so the character always agrees with the figures.
 */
enum class WellnessTier {
    /** Within budget and spending at or below the calendar's pace. */
    THRIVING,

    /** Within budget but running ahead of pace, or no budget set yet (neutral). */
    STEADY,

    /** At least [BudgetPlanner.APPROACHING_PERCENT]% used. */
    GETTING_TIGHT,

    /** [BudgetPlanner.LOW_REMAINING_PERCENT]% or less left, or the budget used exactly to the limit. */
    CONCERNED,

    /** Spending went past the month's budget. */
    OVER_PLAN;

    companion object {
        /**
         * [aheadOfPace] is [BudgetBurn.aheadOfPace]; [overspent] is true when the month's remaining amount is
         * negative (so a budget used exactly to the limit stays [CONCERNED], not [OVER_PLAN]).
         */
        fun of(health: BudgetHealth, aheadOfPace: Boolean, overspent: Boolean): WellnessTier = when (health) {
            BudgetHealth.NOT_SET -> STEADY
            BudgetHealth.ON_TRACK -> if (aheadOfPace) STEADY else THRIVING
            BudgetHealth.APPROACHING -> GETTING_TIGHT
            BudgetHealth.LOW -> CONCERNED
            BudgetHealth.EXCEEDED -> if (overspent) OVER_PLAN else CONCERNED
        }
    }
}
