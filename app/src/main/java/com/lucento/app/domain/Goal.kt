package com.lucento.app.domain

import com.lucento.app.core.money.Money
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * A savings target. Progress comes from [GoalContribution]s, which *earmark* money — they do not move it.
 * Wallet balances (and therefore net worth) are unchanged by contributions, so savings are never counted
 * twice. [linkedWalletId] optionally names the account where the money is kept, for reference only.
 */
data class Goal(
    val id: Long,
    val name: String,
    val target: Money,
    val targetDate: LocalDate?,
    val linkedWalletId: Long?,
    val createdAtEpochMillis: Long,
    /** The card picture the user chose ([GoalScene.key]); null = automatic from the name. Presentation only. */
    val sceneKey: String? = null,
    /** The user's own photo for the card (a private copy in app storage). Takes precedence over [sceneKey]. */
    val photoPath: String? = null,
) {
    val currencyCode: String get() = target.currencyCode
}

/** Money set aside for a goal (positive) or taken back out (negative). */
data class GoalContribution(
    val id: Long,
    val goalId: Long,
    val amount: Money,
    val date: LocalDate,
    val note: String,
)

data class GoalProgress(
    val goal: Goal,
    val saved: Money,
    /** Remaining to reach the target; zero once reached. */
    val remaining: Money,
    val percent: Int,
    val fraction: Float,
    val reached: Boolean,
    /** Projected completion from the average monthly pace so far; null when there is no usable pace. */
    val projectedCompletion: LocalDate?,
    /** Amount per month needed to reach the target by its date; null without a future target date. */
    val requiredPerMonth: Money?,
)

object GoalCalculator {

    fun saved(goal: Goal, contributions: List<GoalContribution>): Money =
        Money.sum(goal.currencyCode, contributions.filter { it.goalId == goal.id }.map { it.amount })

    fun progress(goal: Goal, contributions: List<GoalContribution>, today: LocalDate): GoalProgress {
        val own = contributions.filter { it.goalId == goal.id }
        val saved = saved(goal, own)
        val reached = saved >= goal.target
        val remaining = if (reached) Money.zero(goal.currencyCode) else goal.target - saved
        val percent = (Insights.percentOf(saved.coerceAtLeastZero(), goal.target) ?: 0).coerceAtMost(100)
        return GoalProgress(
            goal = goal,
            saved = saved,
            remaining = remaining,
            percent = percent,
            fraction = Insights.fraction(saved, goal.target),
            reached = reached,
            projectedCompletion = if (reached) null else projectCompletion(own, remaining, today),
            requiredPerMonth = if (reached) null else requiredPerMonth(remaining, goal.targetDate, today),
        )
    }

    /**
     * Average net contribution per 30-day month since the first contribution (at least one month), then
     * the date the remaining amount would be covered at that pace. Null when the pace is not positive.
     */
    fun projectCompletion(contributions: List<GoalContribution>, remaining: Money, today: LocalDate): LocalDate? {
        if (contributions.isEmpty() || remaining.amountMinor <= 0L) return null
        val first = contributions.minOf { it.date }
        val net = contributions.fold(0L) { acc, c -> Math.addExact(acc, c.amount.amountMinor) }
        if (net <= 0L) return null
        val days = ChronoUnit.DAYS.between(first, today).coerceAtLeast(DAYS_PER_MONTH.toLong())
        // pacePerDay = net / days; daysNeeded = ceil(remaining / pacePerDay) = ceil(remaining * days / net)
        val daysNeeded = BigDecimal.valueOf(remaining.amountMinor)
            .multiply(BigDecimal.valueOf(days))
            .divide(BigDecimal.valueOf(net), 0, RoundingMode.CEILING)
        if (daysNeeded > BigDecimal.valueOf(MAX_PROJECTION_DAYS)) return null
        return today.plusDays(daysNeeded.toLong())
    }

    /**
     * Even monthly amount needed to cover [remaining] by [targetDate], counting started months
     * (a target 10 days away needs the full amount this month). Rounded up to the minor unit.
     */
    fun requiredPerMonth(remaining: Money, targetDate: LocalDate?, today: LocalDate): Money? {
        if (targetDate == null || remaining.amountMinor <= 0L) return null
        if (targetDate.isBefore(today)) return remaining
        val months = (ChronoUnit.MONTHS.between(today.withDayOfMonth(1), targetDate.withDayOfMonth(1)) + 1).coerceAtLeast(1)
        val perMonth = BigDecimal.valueOf(remaining.amountMinor)
            .divide(BigDecimal.valueOf(months), 0, RoundingMode.CEILING)
            .longValueExact()
        return Money(perMonth, remaining.currencyCode)
    }

    private fun Money.coerceAtLeastZero(): Money = if (isNegative) Money.zero(currencyCode) else this

    private const val DAYS_PER_MONTH = 30
    private const val MAX_PROJECTION_DAYS = 365L * 100
}

/** Progress points worth a (calm) celebration. Only ever triggered by money being set aside, never by spending. */
enum class GoalMilestone(val percent: Int) { HALFWAY(50), REACHED(100) }

object GoalMilestones {

    /**
     * The highest milestone crossed when a goal's saved amount moved from [before] to [after] — null when none
     * was crossed, when money was taken back out, or for a non-positive target. Exact integer comparison:
     * `saved × 100 ≥ target × percent`, so rounding can never trigger (or miss) a milestone.
     */
    fun crossed(target: Money, before: Money, after: Money): GoalMilestone? {
        if (target.amountMinor <= 0L || after <= before) return null
        fun reached(saved: Money, m: GoalMilestone) =
            java.math.BigInteger.valueOf(saved.amountMinor).multiply(java.math.BigInteger.valueOf(100)) >=
                java.math.BigInteger.valueOf(target.amountMinor).multiply(java.math.BigInteger.valueOf(m.percent.toLong()))
        return GoalMilestone.entries.reversed().firstOrNull { reached(after, it) && !reached(before, it) }
    }
}

/**
 * The dashboard's savings level for one currency: how far all active goals in [currencyCode] are toward their
 * targets together, and the goal closest to completion.
 *
 * Goal contributions only *earmark* money already counted in wallet balances, so this is a view of progress, never
 * an extra asset: nothing here feeds net worth, budgets or cash flow. Overall progress caps each goal at its target
 * and ignores negative balances (a goal saved past its target cannot hide another goal's shortfall).
 */
data class GoalsOverview(
    val currencyCode: String,
    val goalCount: Int,
    val reachedCount: Int,
    /** Sum over goals of min(max(saved, 0), target). */
    val saved: Money,
    val target: Money,
    val percent: Int,
    val fraction: Float,
    /** The unreached goal with the highest progress (ties: earliest target date, then oldest). */
    val closest: GoalProgress?,
    /** Goals kept in other currencies (not added in; never converted). */
    val otherCurrencyGoals: Int,
)

object GoalsSummary {

    fun overview(goals: List<Goal>, contributions: List<GoalContribution>, currencyCode: String, today: LocalDate): GoalsOverview? {
        val inCurrency = goals.filter { it.currencyCode == currencyCode }
        if (inCurrency.isEmpty()) return null
        val progress = inCurrency.map { GoalCalculator.progress(it, contributions, today) }
        val zero = Money.zero(currencyCode)
        val saved = Money.sum(
            currencyCode,
            progress.map { p ->
                when {
                    p.saved.isNegative -> zero
                    p.saved > p.goal.target -> p.goal.target
                    else -> p.saved
                }
            },
        )
        val target = Money.sum(currencyCode, inCurrency.map { it.target })
        val closest = progress.filterNot { it.reached }
            .sortedWith(
                compareByDescending<GoalProgress> { it.percent }
                    .thenBy { it.goal.targetDate ?: LocalDate.MAX }
                    .thenBy { it.goal.createdAtEpochMillis },
            )
            .firstOrNull()
        return GoalsOverview(
            currencyCode = currencyCode,
            goalCount = inCurrency.size,
            reachedCount = progress.count { it.reached },
            saved = saved,
            target = target,
            percent = (Insights.percentOf(saved, target) ?: 0).coerceIn(0, 100),
            fraction = Insights.fraction(saved, target),
            closest = closest,
            otherCurrencyGoals = goals.size - inCurrency.size,
        )
    }
}
