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
    SECURED("secured", "Secured", "Locked Lucento with your phone’s screen lock", "Turn on Lock Lucento in Settings › Privacy & security"),

    // Progress awards (owner, 2026-11-23): real results rather than setup steps. Past progress counts, quietly.
    STEADY_MONTH("steady_month", "Steady Month", "Kept a whole month within its budget", "Keep a whole month within its budget"),
    GOAL_REACHED("goal_reached", "Goal Reached", "Reached one of your goals’ targets", "Reach one of your goals’ targets"),
    LOAN_CLEARED("loan_cleared", "Loan Cleared", "Paid a loan down to zero", "Pay one of your loans down to zero");

    companion object {
        fun fromKey(key: String): Achievement? = entries.firstOrNull { it.key == key }

        /**
         * The awards that existed before the progress awards (2026-11-23). A phone that acknowledged awards before then,
         * and has no record of which awards it knew ([Achievements.newToThisPhone]), knew exactly these.
         */
        val FIRST_SET: Set<Achievement> = setOf(MONEY_KEEPER, BUDGET_BUILDER, FIRST_STEP, GETTING_CONSISTENT, FINANCIAL_EXPLORER, GOAL_SETTER, SECURED)
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
    /** A finished month stayed within a budget that covered all of it ([BudgetMilestones.keptWithin]). */
    val steadyMonth: Boolean = false,
    /** Goals (archived ones too) whose savings reached the target at some point. */
    val goalsReached: Int = 0,
    /** Loans paid down to nothing owed, with at least one payment recorded. */
    val loansCleared: Int = 0,
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
            Achievement.STEADY_MONTH -> one(if (facts.steadyMonth) 1 else 0)
            Achievement.GOAL_REACHED -> one(facts.goalsReached)
            Achievement.LOAN_CLEARED -> one(facts.loansCleared)
        }
    }

    fun earned(facts: ProgressFacts): Set<Achievement> =
        Achievement.entries.filterTo(LinkedHashSet()) { progress(it, facts).earned }

    /**
     * What the award cabinet shows as earned: what the records earn now, plus every award already presented. An award
     * is never taken away: deleting the goal or reopening the loan behind it leaves it on the shelf.
     */
    fun shownEarned(earned: Set<Achievement>, acknowledged: Set<Achievement>?): Set<Achievement> =
        Achievement.entries.filterTo(LinkedHashSet()) { it in earned || acknowledged?.contains(it) == true }

    /**
     * The milestone to acknowledge now, if any: the first earned one (in display order) not acknowledged yet. A user
     * who has never had milestones ([acknowledged] null) is baselined silently instead, so nobody gets a burst of
     * congratulations for things they did before this existed. Awards in [newHere] (added by an update, [newToThisPhone])
     * wait until what they already earn has been recorded quietly too.
     */
    fun toAcknowledge(earned: Set<Achievement>, acknowledged: Set<Achievement>?, newHere: Set<Achievement> = emptySet()): Achievement? =
        toAcknowledgeAll(earned, acknowledged, newHere).firstOrNull()

    /**
     * Every earned award not acknowledged yet, in display order. When several land together (a restored backup, a
     * first sync, one write that earns two) they are presented in one ceremony, never one modal after another.
     */
    fun toAcknowledgeAll(earned: Set<Achievement>, acknowledged: Set<Achievement>?, newHere: Set<Achievement> = emptySet()): List<Achievement> =
        if (acknowledged == null) emptyList() else Achievement.entries.filter { it in earned && it !in acknowledged && it !in newHere }

    /**
     * Awards this phone has never known: added by an update after it began acknowledging awards. What they already earn is
     * recorded quietly, as on the first run, so an update brings no burst of ceremonies for the past. [known] holds the
     * keys this phone knew; null means it acknowledged awards before it kept that record, so it knew [Achievement.FIRST_SET].
     */
    fun newToThisPhone(known: Set<String>?): Set<Achievement> {
        val knew = known?.mapNotNull(Achievement::fromKey)?.toSet() ?: Achievement.FIRST_SET
        return Achievement.entries.toSet() - knew
    }

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
        /** Goal contributions, for Goal Reached. */
        contributions: List<GoalContribution> = emptyList(),
        /**
         * The translated ledger and its main currency, for Steady Month (the Budget tab's figures). Without them, as in
         * older tests, no month is judged.
         */
        ledger: com.lucento.app.domain.fx.TranslatedLedger? = null,
        primaryCurrency: String? = null,
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
            steadyMonth = ledger != null && primaryCurrency != null && steadyMonth(categories, budgets, ledger, primaryCurrency, today),
            goalsReached = goals.count { goal -> everReached(goal, contributions) },
            loansCleared = wallets.count { w ->
                w.type == WalletType.LOAN && w.balance.amountMinor <= 0L &&
                    all.any { it.walletId == w.id && it.type == TransactionType.TRANSFER_IN }
            },
        )
    }

    /** How far back Steady Month looks for a month kept within budget. */
    const val STEADY_LOOKBACK_MONTHS = 24L

    /** Whether any finished month (at most [STEADY_LOOKBACK_MONTHS] back) stayed within a budget that covered all of it. */
    fun steadyMonth(
        categories: List<Category>,
        budgets: List<Budget>,
        ledger: com.lucento.app.domain.fx.TranslatedLedger,
        currency: String,
        today: java.time.LocalDate,
    ): Boolean {
        val first = budgets.mapNotNull { it.createdOn }.minOrNull()?.let(java.time.YearMonth::from) ?: return false
        val last = java.time.YearMonth.from(today).minusMonths(1)
        val earliest = maxOf(first, last.minusMonths(STEADY_LOOKBACK_MONTHS - 1))
        return generateSequence(last) { it.minusMonths(1) }
            .takeWhile { it >= earliest }
            .any { BudgetMilestones.keptWithin(categories, budgets, ledger, currency, it) }
    }

    /** Whether [goal]'s savings, added up in date order, ever reached its target (a later withdrawal doesn't undo it). */
    fun everReached(goal: Goal, contributions: List<GoalContribution>): Boolean {
        var saved = 0L
        contributions
            .filter { it.goalId == goal.id && it.amount.currencyCode == goal.currencyCode }
            .sortedWith(compareBy<GoalContribution> { it.date }.thenBy { it.id })
            .forEach { c ->
                saved = Math.addExact(saved, c.amount.amountMinor)
                if (saved >= goal.target.amountMinor) return true
            }
        return false
    }

    /** Recorded entries that count as "using Lucento": expenses and income, not internal movements. */
    fun countsAsRecorded(type: TransactionType): Boolean = type == TransactionType.EXPENSE || type == TransactionType.INCOME
}
