package com.lucento.app.domain

/**
 * Light milestones that recognise real progress (redesign Part D). Each is worked out from the user's own records —
 * never from a counter that could be gamed — and none can be lost: no streaks to break, no points, no pressure, no
 * reminders that nag. A milestone is acknowledged once, after the write that earned it.
 */
enum class Achievement(val key: String, val title: String, val description: String) {
    MONEY_KEEPER("money_keeper", "Money Keeper", "Added the accounts you use"),
    BUDGET_BUILDER("budget_builder", "Budget Builder", "Gave your spending categories an amount"),
    FIRST_STEP("first_step", "First Step", "Recorded your first transaction"),
    GETTING_CONSISTENT("getting_consistent", "Getting Consistent", "Recorded transactions on ${Achievements.CONSISTENT_DAYS} different days"),
    FINANCIAL_EXPLORER("financial_explorer", "Financial Explorer", "Looked at where your money goes in Insights"),
    GOAL_SETTER("goal_setter", "Goal Setter", "Set a savings goal");

    companion object {
        fun fromKey(key: String): Achievement? = entries.firstOrNull { it.key == key }
    }
}

/** What the milestones are worked out from. All counts come from the database (or one local flag for Insights). */
data class ProgressFacts(
    val assetWallets: Int,
    val budgetedCategories: Int,
    /** Expenses and income the user recorded (transfers, adjustments and goal contributions don't count). */
    val recordedTransactions: Int,
    /** Distinct calendar days with a recorded transaction. */
    val activeDays: Int,
    val goals: Int,
    val visitedInsights: Boolean,
)

object Achievements {
    const val CONSISTENT_DAYS = 7

    fun earned(facts: ProgressFacts): Set<Achievement> = buildSet {
        if (facts.assetWallets > 0) add(Achievement.MONEY_KEEPER)
        if (facts.budgetedCategories > 0) add(Achievement.BUDGET_BUILDER)
        if (facts.recordedTransactions > 0) add(Achievement.FIRST_STEP)
        if (facts.activeDays >= CONSISTENT_DAYS) add(Achievement.GETTING_CONSISTENT)
        if (facts.visitedInsights) add(Achievement.FINANCIAL_EXPLORER)
        if (facts.goals > 0) add(Achievement.GOAL_SETTER)
    }

    /**
     * The milestone to acknowledge now, if any: the first earned one (in display order) not acknowledged yet. A user
     * who has never had milestones ([acknowledged] null) is baselined silently instead, so nobody gets a burst of
     * congratulations for things they did before this existed.
     */
    fun toAcknowledge(earned: Set<Achievement>, acknowledged: Set<Achievement>?): Achievement? =
        if (acknowledged == null) null else Achievement.entries.firstOrNull { it in earned && it !in acknowledged }

    /** The facts behind the milestones, from stored records (budgets with the lifecycle rules for this month). */
    fun facts(
        all: List<Transaction>,
        wallets: List<Wallet>,
        categories: List<Category>,
        budgets: List<Budget>,
        goals: List<Goal>,
        today: java.time.LocalDate,
        visitedInsights: Boolean,
    ): ProgressFacts {
        val recorded = all.filter { countsAsRecorded(it.type) }
        return ProgressFacts(
            assetWallets = wallets.count { !it.type.isLiability },
            budgetedCategories = BudgetPlanner.activeAllocations(budgets, categories, java.time.YearMonth.from(today)).size,
            recordedTransactions = recorded.size,
            activeDays = recorded.map { it.date }.distinct().size,
            goals = goals.size,
            visitedInsights = visitedInsights,
        )
    }

    /** Recorded entries that count as "using Lucento": expenses and income, not internal movements. */
    fun countsAsRecorded(type: TransactionType): Boolean = type == TransactionType.EXPENSE || type == TransactionType.INCOME
}
