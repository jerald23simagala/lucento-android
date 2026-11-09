package com.lucento.app.domain

import com.lucento.app.core.money.Money
import com.lucento.app.core.money.MoneyParser

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

    /** The visible stage this page belongs to: adding accounts and amounts, then using Lucento. */
    val stage: SetupStage get() = if (this == TRANSACTIONS || this == WELCOME) SetupStage.GET_STARTED else SetupStage.SET_UP

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

    /**
     * Set up starts in onboarding with the "Lucento is personalized" summary, then continues with the guide's set-up
     * pages this person has (their modules decide which). The summary is screen 1 of [setUpScreens].
     */
    fun setUpScreens(features: FeatureSet): Int = 1 + steps(features).count { it.stage == SetupStage.SET_UP }

    /**
     * Where [step] sits in its stage for the path rail: "Set up · 3 of 4" (counting the onboarding summary first) or
     * "Get started · 1 of 2". Returns screen to screens.
     */
    fun screenOf(step: GuideStep, features: FeatureSet): Pair<Int, Int> {
        val inStage = steps(features).filter { it.stage == step.stage }
        val position = inStage.indexOf(step).coerceAtLeast(0) + 1
        return if (step.stage == SetupStage.SET_UP) (position + 1) to (inStage.size + 1) else position to inStage.size
    }

    /**
     * The accounts page's starting balance: the asset accounts in the main [currencyCode] added up (exact, overflow
     * throws). Accounts in other currencies are never converted here; [startingBalanceAccounts] says how many count.
     */
    fun startingBalance(wallets: List<Wallet>, currencyCode: String): Money =
        Money.sum(currencyCode, wallets.filter { !it.type.isLiability && it.balance.currencyCode == currencyCode }.map { it.balance })

    /** How many asset accounts [startingBalance] adds up. */
    fun startingBalanceAccounts(wallets: List<Wallet>, currencyCode: String): Int =
        wallets.count { !it.type.isLiability && it.balance.currencyCode == currencyCode }

    /**
     * Each amount's share of their total in whole percent, for the budget step's split bar and its legend. Shares are
     * rounded by largest remainder, so they always add up to exactly 100 (or are all 0 when nothing is positive).
     * Zero and negative amounts get 0.
     */
    fun sharePercents(amountsMinor: List<Long>): List<Int> {
        val positive = amountsMinor.map { it.coerceAtLeast(0L) }
        val total = positive.fold(0L) { acc, v -> Math.addExact(acc, v) }
        if (total == 0L) return positive.map { 0 }
        val exact = positive.map { java.math.BigDecimal(it).multiply(java.math.BigDecimal(100)).divide(java.math.BigDecimal(total), 6, java.math.RoundingMode.DOWN) }
        val floors = exact.map { it.toInt() }.toMutableList()
        var left = 100 - floors.sum()
        exact.indices.sortedByDescending { exact[it] - java.math.BigDecimal(floors[it]) }.forEach { i ->
            if (left > 0 && positive[i] > 0L) { floors[i] += 1; left -= 1 }
        }
        return floors
    }

    /** How many categories need an amount before the budget step can be left. */
    fun budgetsRequired(expenseCategories: Int): Int = minOf(MIN_BUDGETED, expenseCategories)

    /**
     * The budget step's running total while the user types: the amounts that parse ([MoneyParser], as saving does)
     * and are above zero, added up; blank or unclear entries add nothing yet. It previews the monthly budget
     * [BudgetPlanner] will make of the saved allocations — always their sum.
     */
    fun draftTotal(texts: Collection<String>, currencyCode: String, decimalSeparator: Char): Money =
        Money.sum(
            currencyCode,
            texts.mapNotNull { MoneyParser.parseOrNull(it, currencyCode, decimalSeparator)?.takeIf { m -> m.amountMinor > 0L } },
        )

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
