package com.lucento.app.feature.budget

import com.lucento.app.core.money.Money
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.domain.Budget
import com.lucento.app.domain.BudgetPlanner
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Who a budget change applies to (docs/CALCULATIONS.md, "Budget lifecycle"). */
enum class BudgetScope {
    /** The month being viewed, nothing else — the normal adjustment. */
    THIS_MONTH,
    /** The month being viewed and every month after; earlier months keep their amounts. */
    ONWARD,
}

/**
 * What one category's budget looks like around [month], so the sheet can say what a change will (and won't) touch.
 * Pure: built from the stored rows with [BudgetPlanner]'s resolution rules.
 */
data class BudgetChangeContext(
    val categoryName: String,
    val month: YearMonth,
    /** Months before the current one can only be corrected for themselves; "and later" would reach into today. */
    val isPastMonth: Boolean,
    /** The recurring amount that applies to [month] without a month-only change; null when there is none (or 0). */
    val usual: Money?,
    /** [month] has its own amount (a month-only change, 0 when removed for that month), which wins over [usual]. */
    val hasOwnAmount: Boolean,
    /** Months after [month] that have their own amount; an "onward" change leaves them as they are. */
    val laterOwnAmounts: Int,
    /** [month]'s own amount is 0: the category was removed for that month only. */
    val removedThisMonth: Boolean = false,
) {
    val scopes: List<BudgetScope> get() = if (isPastMonth) listOf(BudgetScope.THIS_MONTH) else BudgetScope.entries

    /** A category with no amount at all starts as a monthly amount; any adjustment of an existing one is month-only. */
    val defaultScope: BudgetScope
        get() = if (!isPastMonth && usual == null && !hasOwnAmount) BudgetScope.ONWARD else BudgetScope.THIS_MONTH

    private val name get() = monthName(month)

    /** Short month names so both choices fit one line at large text sizes. */
    fun scopeLabel(scope: BudgetScope): String = when (scope) {
        BudgetScope.THIS_MONTH -> "Only ${monthName(month, short = true)}"
        BudgetScope.ONWARD -> "${monthName(month, short = true)} onward"
    }

    /** One line under the choice, saying exactly what changes. */
    fun scopeExplanation(scope: BudgetScope): String = when (scope) {
        BudgetScope.THIS_MONTH -> "Changes apply to $name only. Other months keep their amounts."
        BudgetScope.ONWARD -> buildString {
            append("Becomes the monthly amount from $name on. Earlier months keep theirs.")
            if (laterOwnAmounts > 0) {
                append(" ")
                append(if (laterOwnAmounts == 1) "1 later month you changed on its own keeps its amount."
                else "$laterOwnAmounts later months you changed on their own keep their amounts.")
            }
        }
    }

    /** Where [month]'s amount comes from, when that isn't obvious. Null when nothing needs saying. */
    val status: String?
        get() = when {
            removedThisMonth && usual != null -> "Removed for $name only. Other months use ${MoneyFormatter.format(usual)}."
            removedThisMonth -> null
            hasOwnAmount && usual != null -> "$name has its own amount. Other months use ${MoneyFormatter.format(usual)}."
            hasOwnAmount -> "This amount is for $name only."
            else -> null
        }

    /**
     * An "onward" change replaces an amount people already rely on, so it is confirmed; giving a category its first
     * monthly amount is not. Removing from a month on is always confirmed.
     */
    fun needsConfirmation(scope: BudgetScope, removing: Boolean): Boolean =
        scope == BudgetScope.ONWARD && (removing || usual != null || laterOwnAmounts > 0 || hasOwnAmount)

    fun confirmTitle(removing: Boolean): String =
        if (removing) "Stop budgeting $categoryName from $name?" else "Update future months?"

    fun confirmBody(amount: Money?, removing: Boolean): String {
        val before = monthName(month.minusMonths(1))
        return buildString {
            if (removing || amount == null) append("$categoryName will have no amount from $name on.")
            else append("$categoryName becomes ${MoneyFormatter.format(amount)} a month from $name on.")
            append(" $before and earlier keep their amounts.")
            if (laterOwnAmounts > 0) append(" Months you changed on their own keep theirs.")
            if (removing) append(" Your transactions are not affected.")
        }
    }

    fun removeLabel(scope: BudgetScope): String = when (scope) {
        BudgetScope.THIS_MONTH -> "Remove for $name"
        BudgetScope.ONWARD -> "Remove from $name on"
    }

    companion object {
        fun of(
            budgets: List<Budget>,
            categoryId: Long,
            categoryName: String,
            currencyCode: String,
            month: YearMonth,
            currentMonth: YearMonth,
        ): BudgetChangeContext {
            val rows = budgets.filter { it.categoryId == categoryId && it.limit.currencyCode == currencyCode }
            return BudgetChangeContext(
                categoryName = categoryName,
                month = month,
                isPastMonth = month < currentMonth,
                usual = BudgetPlanner.recurringAllocation(rows, month)?.limit?.takeIf { it.amountMinor > 0L },
                hasOwnAmount = rows.any { it.month == month },
                laterOwnAmounts = rows.mapNotNull { it.month }.filter { it > month }.distinct().size,
                removedThisMonth = rows.any { it.month == month && it.limit.amountMinor == 0L },
            )
        }

        fun monthName(month: YearMonth, short: Boolean = false, locale: Locale = Locale.getDefault()): String =
            month.month.getDisplayName(if (short) TextStyle.SHORT else TextStyle.FULL, locale)
    }
}
