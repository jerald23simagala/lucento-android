package com.lucento.app.domain

/**
 * The guided setup that follows onboarding: a short story from an empty app to a working one. Steps for modules the
 * user switched off are left out. Progress is saved after each step, so the story resumes where it was left.
 */
enum class GuideStep(val key: String) {
    /** Cash, bank, e-wallet and savings accounts. At least one is needed: every transaction belongs to a wallet. */
    WALLETS("wallets"),
    /** Credit cards and loans; optional. */
    DEBTS("debts"),
    /** Amounts for spending categories: at least [GuidedSetup.MIN_BUDGETED] (or every category, when fewer). */
    BUDGETS("budgets"),
    /** One savings goal; optional. */
    GOAL("goal"),
    /** A first few transactions through the composer; optional, and they can be deleted later. */
    TRANSACTIONS("transactions"),
    /** The end of the story: welcome to Lucento. */
    WELCOME("welcome");

    companion object {
        fun fromKey(key: String?): GuideStep? = entries.firstOrNull { it.key == key }
    }
}

object GuidedSetup {
    const val MIN_BUDGETED = 2

    /** The steps this user walks through, in order. */
    fun steps(features: FeatureSet): List<GuideStep> = GuideStep.entries.filter { step ->
        when (step) {
            GuideStep.DEBTS -> features.isEnabled(Feature.DEBTS_LOANS)
            GuideStep.BUDGETS -> features.isEnabled(Feature.BUDGETS)
            GuideStep.GOAL -> features.isEnabled(Feature.SAVINGS_GOALS)
            else -> true
        }
    }

    /** The step after [step]; a step that no longer applies (its module was switched off) moves on to the next one. */
    fun next(step: GuideStep, features: FeatureSet): GuideStep? {
        val steps = steps(features)
        return steps.firstOrNull { it.ordinal > step.ordinal }
    }

    /** Where to resume: [saved] if it still applies, else the next step that does. */
    fun resume(saved: GuideStep, features: FeatureSet): GuideStep =
        if (saved in steps(features)) saved else next(saved, features) ?: GuideStep.WELCOME

    /** How many categories need an amount before the budget step can be left. */
    fun budgetsRequired(expenseCategories: Int): Int = minOf(MIN_BUDGETED, expenseCategories)

    /**
     * Whether [step] is complete enough to move on. Optional steps always are; wallets need one asset account and
     * budgets [budgetsRequired] categories with an amount.
     */
    fun canContinue(step: GuideStep, assetWallets: Int, budgetedCategories: Int, expenseCategories: Int): Boolean = when (step) {
        GuideStep.WALLETS -> assetWallets > 0
        GuideStep.BUDGETS -> budgetedCategories >= budgetsRequired(expenseCategories)
        else -> true
    }

    /** Steps the user may skip (and finish later from the app itself). */
    fun isSkippable(step: GuideStep): Boolean = step == GuideStep.DEBTS || step == GuideStep.GOAL || step == GuideStep.TRANSACTIONS
}
