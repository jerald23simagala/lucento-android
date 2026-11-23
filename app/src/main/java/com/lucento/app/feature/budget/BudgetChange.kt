package com.lucento.app.feature.budget

import com.lucento.app.core.money.Money
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.domain.Budget
import com.lucento.app.domain.BudgetPlanner
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Who a budget change applies to (docs/CALCULATIONS.md, "Budget lifecycle"). */
/**
 * What a saved amount did, said once the sheet closes (critic r3, P2: TalkBack hears it too): "Restaurants: $235.00 for
 * December only. December’s budget: $2,775.00." With amounts hidden, it says what changed without them.
 */
internal fun savedAmountMessage(
    name: String,
    amount: com.lucento.app.core.money.Money?,
    scope: BudgetScope,
    month: YearMonth,
    total: com.lucento.app.core.money.Money?,
    fmt: com.lucento.app.core.designsystem.component.AmountFormat,
): String {
    val m = BudgetChangeContext.monthName(month)
    val what = when {
        amount == null && scope == BudgetScope.THIS_MONTH -> "no amount for $m"
        amount == null -> "no amount from $m on"
        fmt.hidden && scope == BudgetScope.THIS_MONTH -> "new amount for $m only"
        fmt.hidden -> "new monthly amount from $m on"
        scope == BudgetScope.THIS_MONTH -> "${fmt.format(amount)} for $m only"
        else -> "${fmt.format(amount)} a month from $m on"
    }
    return "$name: $what." + budgetAfter(m, total, fmt)
}

/** "Restaurants: back to the usual $200.00 for December. December’s budget: $2,740.00." */
internal fun usualAmountMessage(
    name: String,
    usual: com.lucento.app.core.money.Money,
    month: YearMonth,
    total: com.lucento.app.core.money.Money?,
    fmt: com.lucento.app.core.designsystem.component.AmountFormat,
): String {
    val m = BudgetChangeContext.monthName(month)
    return (if (fmt.hidden) "$name: back to the usual amount for $m." else "$name: back to the usual ${fmt.format(usual)} for $m.") +
        budgetAfter(m, total, fmt)
}

private fun budgetAfter(m: String, total: com.lucento.app.core.money.Money?, fmt: com.lucento.app.core.designsystem.component.AmountFormat): String =
    if (total == null || fmt.hidden) "" else " $m’s budget: ${fmt.format(total)}."

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
    /**
     * Opened from the monthly check-in's plan page: every change starts as that month's only, because planning a month is
     * about what is different in it (a birthday, an event). "Onward" is still one tap away.
     */
    val planning: Boolean = false,
) {
    val scopes: List<BudgetScope> get() = if (isPastMonth) listOf(BudgetScope.THIS_MONTH) else BudgetScope.entries

    /** A category with no amount at all starts as a monthly amount; any adjustment of an existing one is month-only. */
    val defaultScope: BudgetScope
        get() = if (!planning && !isPastMonth && usual == null && !hasOwnAmount) BudgetScope.ONWARD else BudgetScope.THIS_MONTH

    private val name get() = monthName(month)

    /** Short month names so both choices fit one line at large text sizes. */
    /** The choice in full, for TalkBack: "Only December", "December onward" (the buttons say "Only Dec"). */
    fun scopeDescription(scope: BudgetScope): String = when (scope) {
        BudgetScope.THIS_MONTH -> "Only ${monthName(month)}"
        BudgetScope.ONWARD -> "${monthName(month)} onward"
    }

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
            removedThisMonth && usual != null -> "Removed for $name only. Other months use ${MoneyFormatter.format(usual, wholeUnits = false)}."
            removedThisMonth -> null
            hasOwnAmount && usual != null -> "$name has its own amount. Other months use ${MoneyFormatter.format(usual, wholeUnits = false)}."
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
            else append("$categoryName becomes ${MoneyFormatter.format(amount, wholeUnits = false)} a month from $name on.")
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
            planning: Boolean = false,
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
                planning = planning,
            )
        }

        fun monthName(month: YearMonth, short: Boolean = false, locale: Locale = Locale.getDefault()): String =
            month.month.getDisplayName(if (short) TextStyle.SHORT else TextStyle.FULL, locale)
    }
}
