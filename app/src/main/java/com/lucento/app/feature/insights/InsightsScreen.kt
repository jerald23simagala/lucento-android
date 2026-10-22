package com.lucento.app.feature.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.core.designsystem.component.AmountText
import com.lucento.app.core.designsystem.component.DonutChart
import com.lucento.app.core.designsystem.component.EmptyState
import com.lucento.app.core.designsystem.component.Figure
import com.lucento.app.core.designsystem.component.LucentoCard
import com.lucento.app.core.designsystem.component.LucentoMascot
import com.lucento.app.core.designsystem.component.LucentoTopBar
import com.lucento.app.core.designsystem.component.MascotMood
import com.lucento.app.core.designsystem.component.MoneyText
import com.lucento.app.core.designsystem.component.MonthlyBarChart
import com.lucento.app.core.designsystem.component.SectionHeader
import com.lucento.app.core.designsystem.component.SegmentedToggle
import com.lucento.app.core.designsystem.component.ShareBar
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.Money
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.domain.BudgetCalculator
import com.lucento.app.domain.BudgetStatus
import com.lucento.app.domain.CashflowCalculator
import com.lucento.app.domain.CategoryShare
import com.lucento.app.domain.DateRange
import com.lucento.app.domain.Feature
import com.lucento.app.domain.Insights
import com.lucento.app.domain.NetWorthCalculator
import com.lucento.app.domain.TransactionQueries
import com.lucento.app.domain.TransactionType
import com.lucento.app.feature.home.monthLabel
import java.time.LocalDate
import java.time.YearMonth

private enum class Period(val label: String) { THIS_MONTH("This month"), LAST_MONTH("Last month"), THREE_MONTHS("3 months") }
private enum class Breakdown(val label: String) { SPENDING("Spending"), INCOME("Income") }

private fun Period.range(today: LocalDate): DateRange = when (this) {
    Period.THIS_MONTH -> DateRange.monthOf(today)
    Period.LAST_MONTH -> DateRange.monthOf(today.minusMonths(1))
    Period.THREE_MONTHS -> DateRange(YearMonth.from(today).minusMonths(2).atDay(1), today.withDayOfMonth(today.lengthOfMonth()))
}

/**
 * Local analytics from recorded data only, in one currency at a time. Every chart has the exact
 * figures beside it and a spoken summary; transfers never appear as income or spending.
 */
@Composable
fun InsightsScreen(viewModel: InsightsViewModel = viewModel(factory = InsightsViewModel.Factory)) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    var period by rememberSaveable { mutableStateOf(Period.THIS_MONTH) }
    var breakdown by rememberSaveable { mutableStateOf(Breakdown.SPENDING) }
    val currency = data.focusCurrency

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter),
    ) {
        LucentoTopBar(title = "Insights", subtitle = "From your own records · $currency")
        when {
            data.loading -> Unit
            data.transactions.none { !it.isTransfer } -> EmptyState(
                title = "Insights need a little history",
                body = "Record a few incomes and expenses and Lucento will show where your money goes, month by month.",
                illustration = { LucentoMascot(size = 96.dp, mood = MascotMood.Curious) },
            )
            else -> InsightsContent(
                data = data,
                period = period,
                onPeriod = { period = it },
                breakdown = breakdown,
                onBreakdown = { breakdown = it },
            )
        }
        Spacer(Modifier.height(Spacing.xxl))
    }
}

@Composable
private fun InsightsContent(
    data: InsightsData,
    period: Period,
    onPeriod: (Period) -> Unit,
    breakdown: Breakdown,
    onBreakdown: (Breakdown) -> Unit,
) {
    val currency = data.focusCurrency
    val range = period.range(data.today)
    Column {
        SegmentedToggle(options = Period.entries.map { it to it.label }, selected = period, onSelect = onPeriod)
        Spacer(Modifier.height(Spacing.md))

        val totals = remember(data.transactions, range, currency) {
            TransactionQueries.totalsByCurrency(data.transactions, range, currency).firstOrNull { it.currencyCode == currency }
        }
        LucentoCard(Modifier.fillMaxWidth()) {
            val income = totals?.income ?: Money.zero(currency)
            val expense = totals?.expense ?: Money.zero(currency)
            val net = income - expense
            Figure("Net flow") {
                MoneyText(money = net, style = AmountStyles.hero, showPlus = true, color = if (net.isNegative) LucentoTheme.extendedColors.negative else MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.height(Spacing.md))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Figure("Income", Modifier.weight(1f)) {
                    MoneyText(money = income, style = AmountStyles.medium, showPlus = true, color = LucentoTheme.extendedColors.positive, fractionScale = 1f)
                }
                Figure("Expenses", Modifier.weight(1f)) {
                    MoneyText(money = -expense, style = AmountStyles.medium, fractionScale = 1f)
                }
            }
            val percent = Insights.percentOf(expense, income)
            if (percent != null) {
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    if (percent > 100) "You spent $percent% of what came in." else "You kept ${100 - percent}% of what came in.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(Spacing.section))
        SectionHeader("Breakdown")
        SegmentedToggle(options = Breakdown.entries.map { it to it.label }, selected = breakdown, onSelect = onBreakdown)
        Spacer(Modifier.height(Spacing.md))
        val type = if (breakdown == Breakdown.SPENDING) TransactionType.EXPENSE else TransactionType.INCOME
        val shares = remember(data.transactions, range, currency, type) {
            Insights.categoryBreakdown(data.transactions, currency, range, type)
        }
        BreakdownCard(shares, data, currency)

        Spacer(Modifier.height(Spacing.section))
        val months = remember(data.transactions, currency, data.today) {
            Insights.monthlyTotals(data.transactions, currency, YearMonth.from(data.today), 6)
        }
        SectionHeader("Spending · last 6 months")
        if (months.any { !it.amount.isZero }) {
            MonthlyBarChart(months = months, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Spacing.sm))
            val current = months.last()
            val previous = months[months.size - 2]
            Text(
                buildString {
                    append("${monthLabel(current.month)} so far: ${MoneyFormatter.format(current.amount)}.")
                    if (!previous.amount.isZero) append(" ${monthLabel(previous.month)}: ${MoneyFormatter.format(previous.amount)}.")
                    val average = CashflowCalculator.divide(Money.sum(currency, months.dropLast(1).map { it.amount }), 5)
                    if (!average.isZero) append(" Five-month average: ${MoneyFormatter.format(average)}.")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text("No spending in $currency in the last six months.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (data.features.isEnabled(Feature.BUDGETS) && data.budgets.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.section))
            val month = YearMonth.from(range.endInclusive)
            SectionHeader("Budget performance · ${monthLabel(month)}")
            BudgetCalculator.progressAll(data.budgets, data.transactions, month).forEach { p ->
                val name = p.budget.categoryId?.let { data.categoriesById[it]?.name } ?: "Overall (${p.budget.currencyCode})"
                Column(Modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${MoneyFormatter.format(p.spent)} / ${MoneyFormatter.format(p.budget.limit)}",
                            style = AmountStyles.small,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(Spacing.xs))
                    ShareBar(
                        fraction = p.fraction,
                        color = when (p.status) {
                            BudgetStatus.ON_TRACK -> LucentoTheme.extendedColors.success
                            BudgetStatus.NEAR_LIMIT -> LucentoTheme.extendedColors.warning
                            BudgetStatus.OVER_LIMIT -> LucentoTheme.extendedColors.negative
                        },
                    )
                }
            }
        }

        if (data.wallets.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.section))
            SectionHeader("Net worth today")
            val summary = NetWorthCalculator.summarize(data.wallets, data.primaryCurrency)
            summary.totals.forEach { total ->
                val code = total.currencyCode
                Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(code, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Have ${MoneyFormatter.format(summary.assets[code] ?: Money.zero(code))} · owe ${MoneyFormatter.format(summary.liabilities[code] ?: Money.zero(code))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    AmountText(total, style = AmountStyles.medium)
                }
            }
            Text(
                "Each currency is shown separately — Lucento never converts with guessed rates.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

@Composable
private fun BreakdownCard(shares: List<CategoryShare>, data: InsightsData, currency: String) {
    if (shares.isEmpty()) {
        Text("Nothing recorded in this period.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val palette = LucentoTheme.extendedColors.categorical
    val total = Money.sum(currency, shares.map { it.amount })
    // Five largest, then everything else together, so the chart stays readable.
    val top = shares.take(5)
    val rest = shares.drop(5)
    val rows: List<Triple<String, Money, Color>> = top.mapIndexed { i, s ->
        Triple(s.categoryId?.let { data.categoriesById[it]?.name } ?: "Uncategorized", s.amount, palette[i % palette.size])
    } + if (rest.isEmpty()) emptyList<Triple<String, Money, Color>>() else listOf(Triple("Other", Money.sum(currency, rest.map { it.amount }), palette.last()))
    val description = rows.joinToString("; ") { (name, amount, _) ->
        "$name ${MoneyFormatter.format(amount)}, ${Insights.percentOf(amount, total) ?: 0}%"
    }
    LucentoCard(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            DonutChart(
                values = rows.map { Insights.fraction(it.second, total) },
                colors = rows.map { it.third },
                description = "Breakdown. Total ${MoneyFormatter.format(total)}. $description",
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AmountText(total, style = AmountStyles.large, signed = false)
                    Text("Total", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(Spacing.lg))
        rows.forEach { (name, amount, color) ->
            Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(Spacing.sm))
                Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${Insights.percentOf(amount, total) ?: 0}%",
                    style = AmountStyles.small,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = Spacing.md),
                )
                AmountText(amount, style = AmountStyles.row, signed = false)
            }
        }
    }
}
