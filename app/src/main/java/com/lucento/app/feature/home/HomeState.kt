package com.lucento.app.feature.home

import com.lucento.app.core.money.Money
import com.lucento.app.domain.Category
import com.lucento.app.domain.CategoryShare
import com.lucento.app.domain.DateRange
import com.lucento.app.domain.Insights
import com.lucento.app.domain.MonthAmount
import com.lucento.app.domain.NetWorthCalculator
import com.lucento.app.domain.PeriodTotals
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionQueries
import com.lucento.app.domain.Wallet
import java.time.LocalDate
import java.time.YearMonth

/** Everything the Home overview shows. All figures are derived from stored data; nothing is estimated. */
data class HomeUiState(
    val loading: Boolean = true,
    val primaryCurrency: String = "USD",
    val wallets: List<Wallet> = emptyList(),
    val netWorth: NetWorthCalculator.Summary? = null,
    /**
     * The currency the month and insight sections are shown in: the main currency when the user holds
     * or spends it, otherwise the currency of their first wallet. Never a conversion.
     */
    val focusCurrency: String = "USD",
    val month: YearMonth = YearMonth.now(),
    val monthTotals: PeriodTotals? = null,
    /** This month's totals in currencies other than [focusCurrency]. */
    val otherMonthTotals: List<PeriodTotals> = emptyList(),
    /** Spending per month in [focusCurrency], oldest first, ending with the current month. */
    val trend: List<MonthAmount> = emptyList(),
    /** This month's spending by category in [focusCurrency], largest first. */
    val topCategories: List<CategoryShare> = emptyList(),
    val recent: List<Transaction> = emptyList(),
    val transactionCount: Int = 0,
    val categoriesById: Map<Long, Category> = emptyMap(),
) {
    val walletsById: Map<Long, Wallet> = wallets.associateBy { it.id }
    val hasWallets: Boolean get() = wallets.isNotEmpty()
    val hasTrend: Boolean get() = trend.any { !it.amount.isZero }
    val monthSpend: Money? get() = monthTotals?.expense
}

/** Pure assembly of [HomeUiState] from repository data. No Android dependencies, so it is unit-tested. */
object HomeStateBuilder {
    const val TREND_MONTHS = 6
    const val RECENT_COUNT = 5
    const val TOP_CATEGORIES = 4

    /** Pure assembly of the Home state; [all] must be sorted newest first (as the DAO returns it). */
    fun build(
        all: List<Transaction>,
        wallets: List<Wallet>,
        categories: List<Category>,
        primaryCurrency: String,
        today: LocalDate,
    ): HomeUiState {
        val focus = when {
            wallets.any { it.currencyCode == primaryCurrency } -> primaryCurrency
            all.any { it.currencyCode == primaryCurrency } -> primaryCurrency
            else -> wallets.firstOrNull()?.currencyCode ?: primaryCurrency
        }
        val month = YearMonth.from(today)
        val monthRange = DateRange.monthOf(today)
        val monthTotals = TransactionQueries.totalsByCurrency(all, monthRange, focus)
        return HomeUiState(
            loading = false,
            primaryCurrency = primaryCurrency,
            wallets = wallets,
            netWorth = if (wallets.isEmpty()) null else NetWorthCalculator.summarize(wallets, primaryCurrency),
            focusCurrency = focus,
            month = month,
            monthTotals = monthTotals.firstOrNull { it.currencyCode == focus },
            otherMonthTotals = monthTotals.filter { it.currencyCode != focus },
            trend = Insights.monthlyTotals(all, focus, month, TREND_MONTHS),
            topCategories = Insights.categoryBreakdown(all, focus, monthRange).take(TOP_CATEGORIES),
            recent = all.take(RECENT_COUNT),
            transactionCount = all.size,
            categoriesById = categories.associateBy { it.id },
        )
    }
}
