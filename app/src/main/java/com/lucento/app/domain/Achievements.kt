package com.lucento.app.domain

/**
 * Light milestones that recognise real progress (redesign Part D). Each is worked out from the user's own records —
 * never from a counter that could be gamed — and none can be lost: no streaks to break, no points, no pressure, no
 * reminders that nag. A milestone is acknowledged once, after the write that earned it.
 */
enum class Achievement(val key: String, val title: String, val description: String, val howTo: String) {
    MONEY_KEEPER("money_keeper", "Money Keeper", "Added the accounts you use", "Add the accounts you use"),
    BUDGET_BUILDER("budget_builder", "Budget Builder", "Gave your spending categories an amount", "Give a spending category an amount"),
    FIRST_STEP("first_step", "First Step", "Recorded your first transaction", "Record your first transaction"),
    GETTING_CONSISTENT(
        "getting_consistent",
        "Getting Consistent",
        "Recorded transactions on ${Achievements.CONSISTENT_DAYS} different days",
        "Record transactions on ${Achievements.CONSISTENT_DAYS} different days",
    ),
    FINANCIAL_EXPLORER("financial_explorer", "Financial Explorer", "Looked at where your money goes in Insights", "Open Insights to see where your money goes"),
    GOAL_SETTER("goal_setter", "Goal Setter", "Set a savings goal", "Create a savings goal for something you want"),
    /** Owner test 2026-11-09: a secured setup. Kept once earned, like every award (see [ProgressFacts.secured]). */
    SECURED("secured", "Secured", "Locked Lucento with your phone’s screen lock", "Turn on Lock Lucento in Settings › Privacy & security");

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
    /** App lock is on, or was turned on at some point (a local flag: an award is never taken away). */
    val secured: Boolean = false,
)

/** How far along an award is: [current] of [target], where [current] never exceeds [target]. */
data class AwardProgress(val current: Int, val target: Int) {
    val earned: Boolean get() = current >= target
    /** Worth showing as a count ("3 of 7 days") only when the award takes more than one step. */
    val isCounted: Boolean get() = target > 1
    val fraction: Float get() = if (target <= 0) 1f else current.toFloat() / target
}

object Achievements {
    const val CONSISTENT_DAYS = 7

    /** Progress towards [achievement] from the same facts [earned] uses, so the two can never disagree. */
    fun progress(achievement: Achievement, facts: ProgressFacts): AwardProgress {
        fun one(count: Int) = AwardProgress(count.coerceIn(0, 1), 1)
        return when (achievement) {
            Achievement.MONEY_KEEPER -> one(facts.assetWallets)
            Achievement.BUDGET_BUILDER -> one(facts.budgetedCategories)
            Achievement.FIRST_STEP -> one(facts.recordedTransactions)
            Achievement.GETTING_CONSISTENT -> AwardProgress(facts.activeDays.coerceIn(0, CONSISTENT_DAYS), CONSISTENT_DAYS)
            Achievement.FINANCIAL_EXPLORER -> one(if (facts.visitedInsights) 1 else 0)
            Achievement.GOAL_SETTER -> one(facts.goals)
            Achievement.SECURED -> one(if (facts.secured) 1 else 0)
        }
    }

    fun earned(facts: ProgressFacts): Set<Achievement> =
        Achievement.entries.filterTo(LinkedHashSet()) { progress(it, facts).earned }

    /**
     * The milestone to acknowledge now, if any: the first earned one (in display order) not acknowledged yet. A user
     * who has never had milestones ([acknowledged] null) is baselined silently instead, so nobody gets a burst of
     * congratulations for things they did before this existed.
     */
    fun toAcknowledge(earned: Set<Achievement>, acknowledged: Set<Achievement>?): Achievement? =
        toAcknowledgeAll(earned, acknowledged).firstOrNull()

    /**
     * Every earned award not acknowledged yet, in display order. When several land together (a restored backup, a
     * first sync, one write that earns two) they are presented in one ceremony, never one modal after another.
     */
    fun toAcknowledgeAll(earned: Set<Achievement>, acknowledged: Set<Achievement>?): List<Achievement> =
        if (acknowledged == null) emptyList() else Achievement.entries.filter { it in earned && it !in acknowledged }

    /** The facts behind the milestones, from stored records (budgets with the lifecycle rules for this month). */
    fun facts(
        all: List<Transaction>,
        wallets: List<Wallet>,
        categories: List<Category>,
        budgets: List<Budget>,
        goals: List<Goal>,
        today: java.time.LocalDate,
        visitedInsights: Boolean,
        secured: Boolean = false,
    ): ProgressFacts {
        val recorded = all.filter { countsAsRecorded(it.type) }
        return ProgressFacts(
            assetWallets = wallets.count { !it.type.isLiability },
            budgetedCategories = BudgetPlanner.activeAllocations(budgets, categories, java.time.YearMonth.from(today)).size,
            recordedTransactions = recorded.size,
            activeDays = recorded.map { it.date }.distinct().size,
            goals = goals.size,
            visitedInsights = visitedInsights,
            secured = secured,
        )
    }

    /** Recorded entries that count as "using Lucento": expenses and income, not internal movements. */
    fun countsAsRecorded(type: TransactionType): Boolean = type == TransactionType.EXPENSE || type == TransactionType.INCOME
}
