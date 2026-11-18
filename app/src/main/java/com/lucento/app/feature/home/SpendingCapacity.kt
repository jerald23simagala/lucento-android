package com.lucento.app.feature.home

import com.lucento.app.core.money.Money
import com.lucento.app.domain.MoneyOnHand
import com.lucento.app.domain.SafeToSpend

/**
 * The Monthly budget card's two spending limits and the verdict between them (owner, 2026-11-18), shared by Home's card
 * and the Budget tab so the two never disagree. Everything is read from one [SafeToSpend]; nothing is worked out again
 * here.
 *
 * - **Budget remaining** = budget − spent − upcoming expenses ([SafeToSpend.budgetAllows]), shown with its two parts:
 *   [remainingBeforeUpcoming] (budget − spent) and [upcomingExpenses] (unpaid bills due by the month's end).
 * - **Spendable wallets** = what the spending wallets hold, less everything due from them by the month's end
 *   ([MoneyOnHand]): [walletBills], the bills paid from them, which are among the upcoming expenses too, and
 *   [walletOutflows], loan and card payments and transfers into money that isn't for spending. The bills come off both
 *   limits on purpose. Each limit has to cover them on its own, and the verdict takes the lower of the two, never their
 *   sum, so they are taken once. Leaving them out of the wallets would promise money the bills are about to take
 *   (docs/CALCULATIONS.md "Home budget card and the Budget tab").
 * - **Safe to spend** = the lower of the two ([SafeToSpend.amount]), never shown below zero.
 */
data class SpendingCapacity(
    val remainingBeforeUpcoming: Money,
    val upcomingExpenses: Money,
    val budgetRemaining: Money,
    /** The spending wallets' figure; null when no wallet in the currency counts as spending money. */
    val wallets: MoneyOnHand?,
    /** Whether the wallets take part: money has been recorded in a spending wallet ([MoneyOnHand.recorded]). */
    val walletsCounted: Boolean,
    /** The signed result: below zero when more is due than there is. */
    val safeToSpend: Money,
    val perDay: Money?,
    val daysLeft: Int,
) {
    /** Which limit sets Safe to spend. */
    enum class Limit { BUDGET, WALLETS, BOTH, BUDGET_ONLY }

    /** Spending has passed the budget. */
    val overspent: Boolean get() = remainingBeforeUpcoming.isNegative

    val limit: Limit
        get() {
            val money = wallets?.takeIf { walletsCounted }?.allows ?: return Limit.BUDGET_ONLY
            return when {
                money < budgetRemaining -> Limit.WALLETS
                budgetRemaining < money -> Limit.BUDGET
                else -> Limit.BOTH
            }
        }

    /** What the card shows: never below zero (owner, 2026-11-03). */
    val shown: Money get() = if (safeToSpend.isNegative) Money.zero(safeToSpend.currencyCode) else safeToSpend

    val nothingSafe: Boolean get() = safeToSpend.amountMinor <= 0L

    /** The bills due from the spending wallets: they are among [upcomingExpenses] too. Null without spending wallets. */
    val walletBills: Money?
        get() = wallets?.let { money ->
            Money.sum(money.currencyCode, money.deductions.filter { it.kind == MoneyOnHand.Kind.BILLS }.map { it.amount })
        }

    /** The rest of what is due from them: loan and card payments, and transfers out of spending money. */
    val walletOutflows: Money? get() = wallets?.let { it.deducted - walletBills!! }

    /** The verdict in one sentence: which limit sets the figure, or at zero what took it. */
    val headline: String
        get() = when {
            limit == Limit.BOTH -> LIMITED_BY_BOTH
            nothingSafe && limit == Limit.WALLETS && (wallets?.total?.amountMinor ?: 0L) <= 0L -> WALLETS_EMPTY
            nothingSafe && limit == Limit.WALLETS -> WALLETS_TAKEN
            nothingSafe && overspent -> OVER_BUDGET
            // Nothing was left before the bills either (QA, 2026-11-18): the budget is spent, not taken by them.
            nothingSafe && remainingBeforeUpcoming.amountMinor <= 0L -> BUDGET_USED
            nothingSafe -> BILLS_TAKE_BUDGET
            limit == Limit.WALLETS -> LIMITED_BY_WALLETS
            else -> LIMITED_BY_BUDGET
        }

    /**
     * The line under it: how the figure was chosen, or why the wallets don't take part. At zero the shown figure isn't
     * the lower one (that may be below zero), so it says the floor instead (design review, 2026-11-18).
     */
    val detail: String
        get() = when {
            wallets == null -> NO_SPENDING_WALLETS
            !walletsCounted -> NOTHING_RECORDED
            nothingSafe -> NEVER_BELOW_ZERO
            else -> LOWER_OF_BOTH
        }

    companion object {
        const val LIMITED_BY_BUDGET = "Limited by your remaining budget."
        const val LIMITED_BY_WALLETS = "Limited by your available spending wallets."
        const val LIMITED_BY_BOTH = "Both your budget and wallet availability are limiting your spending."
        const val WALLETS_EMPTY = "Your spending wallets show no money."
        const val WALLETS_TAKEN = "What’s due takes everything in your spending wallets."
        const val OVER_BUDGET = "You’re over budget this month."
        const val BUDGET_USED = "This month’s budget is used up."
        const val BILLS_TAKE_BUDGET = "Bills due take what’s left of your budget."
        const val LOWER_OF_BOTH = "Lower of budget remaining and spendable wallets."
        const val NEVER_BELOW_ZERO = "Safe to spend never shows less than zero."
        const val NO_SPENDING_WALLETS = "No wallet counts as spending money, so only your budget is used."
        const val NOTHING_RECORDED = "No money is recorded in your spending wallets yet, so only your budget is used."

        /**
         * The capacity behind [safe], read from it alone, so its parts always add up to its figures (QA, 2026-11-18).
         * Null without a budget (the card then shows no limits).
         */
        fun of(safe: SafeToSpend): SpendingCapacity? {
            if (safe.basis != SafeToSpend.Basis.BUDGET) return null
            val budget = safe.budgetLimit ?: return null
            val budgetRemaining = safe.budgetAllows ?: return null
            val money = safe.moneyOnHand
            return SpendingCapacity(
                remainingBeforeUpcoming = budget - safe.spentThisMonth,
                upcomingExpenses = safe.committed,
                budgetRemaining = budgetRemaining,
                wallets = money,
                walletsCounted = money?.recorded == true,
                safeToSpend = safe.amount,
                perDay = safe.perDay,
                daysLeft = safe.daysLeft,
            )
        }
    }
}
