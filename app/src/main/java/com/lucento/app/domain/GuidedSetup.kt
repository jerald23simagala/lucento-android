package com.lucento.app.domain

/**
 * The guided setup that follows onboarding: a short story from an empty app to a working one. Steps for modules the
 * user switched off are left out. Progress is saved after each step, so the story resumes where it was left.
 */
enum class GuideStep(val key: String) {
    /** Cash, bank, e-wallet and savings accounts. At least one is needed: every transaction belongs to a wallet. */
    WALLETS("wallets"),
    /**
     * Kept so an install that stopped here can resume. The step is no longer shown: cards and loans are added
     * later from Wallets, and [GuidedSetup.steps] skips this key.
     */
    DEBTS("debts"),
    /**
     * Kept so an install that stopped on the old budget gate can resume. The step is no longer shown and never
     * blocks. Budgets are suggested later from real spending.
     */
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
    /**
     * The steps this user walks through, in order. Debts and the budget gate are not part of the story:
     * a saved [GuideStep.DEBTS] or [GuideStep.BUDGETS] resumes at the next step that still applies.
     */
    fun steps(features: FeatureSet): List<GuideStep> = GuideStep.entries.filter { step ->
        when (step) {
            GuideStep.DEBTS, GuideStep.BUDGETS -> false
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

    /**
     * Whether [step] is complete enough to move on. Wallets need one asset account. The old budget step never
     * blocks, even if a saved install still names it.
     */
    fun canContinue(step: GuideStep, assetWallets: Int, budgetedCategories: Int, expenseCategories: Int): Boolean = when (step) {
        GuideStep.WALLETS -> assetWallets > 0
        else -> true
    }

    /** Steps the user may skip (and finish later from the app itself). */
    fun isSkippable(step: GuideStep): Boolean = step == GuideStep.DEBTS || step == GuideStep.GOAL || step == GuideStep.TRANSACTIONS
}
