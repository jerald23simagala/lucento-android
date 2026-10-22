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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.core.designsystem.component.AmountText
import com.lucento.app.core.designsystem.component.CompositionBar
import com.lucento.app.core.designsystem.component.EmptyState
import com.lucento.app.core.designsystem.component.Figure
import com.lucento.app.core.designsystem.component.IncomeExpenseChart
import com.lucento.app.core.designsystem.component.LegendKey
import com.lucento.app.core.designsystem.component.LucentoCard
import com.lucento.app.core.designsystem.component.LucentoMascot
import com.lucento.app.core.designsystem.component.LucentoTopBar
import com.lucento.app.core.designsystem.component.MascotMood
import com.lucento.app.core.designsystem.component.MoneyText
import com.lucento.app.core.designsystem.component.ProfileButton
import com.lucento.app.core.designsystem.component.SectionHeader
import com.lucento.app.core.designsystem.component.SegmentedToggle
import com.lucento.app.core.designsystem.component.groupLabel
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.Money
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.domain.FlowMonth
import com.lucento.app.domain.Insights
import com.lucento.app.domain.NetWorthComponent
import com.lucento.app.domain.NetWorthComposer
import com.lucento.app.domain.NetWorthComposition
import com.lucento.app.feature.home.monthLabel
import com.lucento.app.feature.plan.PillRow
import java.time.YearMonth

private enum class TrendPeriod(val label: String, val months: Int) { THREE("3 months", 3), SIX("6 months", 6), TWELVE("12 months", 12) }

/**
 * Insights answers two questions, from the user's own records and in one currency at a time:
 * how are my income and expenses trending, and what is my net worth made of? Transfers between the user's own
 * wallets are never income or expenses; savings goals are never added to net worth (their money is already in the
 * wallet balances). Every chart has its exact figures beside it and a spoken summary.
 */
@Composable
fun InsightsScreen(viewModel: InsightsViewModel = viewModel(factory = InsightsViewModel.Factory)) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    var period by rememberSaveable { mutableStateOf(TrendPeriod.SIX) }
    var currencyChoice by rememberSaveable { mutableStateOf<String?>(null) }
    val currencies = remember(data.wallets, data.transactions, data.focusCurrency) {
        (listOf(data.focusCurrency) +
            NetWorthComposer.currencies(data.wallets, data.focusCurrency) +
            data.transactions.filterNot { it.isTransfer }.map { it.currencyCode }.distinct().sorted()).distinct()
    }
    val currency = currencyChoice?.takeIf { it in currencies } ?: data.focusCurrency

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter),
    ) {
        LucentoTopBar(title = "Insights", subtitle = "From your own records · $currency", actions = { ProfileButton() })
        if (data.loading) return@Column
        if (currencies.size > 1) {
            PillRow(currencies.map { it to it }, currency, onSelect = { currencyChoice = it })
            Spacer(Modifier.height(Spacing.md))
        }

        TrendSection(data, currency, period, onPeriod = { period = it })
        Spacer(Modifier.height(Spacing.section))
        NetWorthSection(data, currency)
        Spacer(Modifier.height(Spacing.fabClearance))
    }
}

@Composable
private fun TrendSection(data: InsightsData, currency: String, period: TrendPeriod, onPeriod: (TrendPeriod) -> Unit) {
    val ext = LucentoTheme.extendedColors
    val endMonth = YearMonth.from(data.today)
    val months = remember(data.transactions, currency, endMonth, period) {
        Insights.incomeExpenseTrend(data.transactions, currency, endMonth, period.months)
    }
    SectionHeader("Income and expenses", Modifier.semantics { heading() })
    SegmentedToggle(options = TrendPeriod.entries.map { it to it.label }, selected = period, onSelect = onPeriod)
    Spacer(Modifier.height(Spacing.md))
    val hasData = months.any { !it.income.isZero || !it.expense.isZero }
    if (!hasData) {
        EmptyState(
            title = "No income or expenses yet",
            body = "Record a few incomes and expenses in $currency and you’ll see how they move month by month. " +
                "Transfers between your own accounts never count here.",
            illustration = { LucentoMascot(size = 88.dp, mood = MascotMood.Curious) },
        )
        return
    }
    val income = Money.sum(currency, months.map { it.income })
    val expense = Money.sum(currency, months.map { it.expense })
    val net = income - expense
    LucentoCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Figure("Income", Modifier.weight(1f)) {
                MoneyText(money = income, style = AmountStyles.medium, showPlus = true, color = ext.positive, fractionScale = 1f)
            }
            Figure("Expenses", Modifier.weight(1f)) {
                MoneyText(money = -expense, style = AmountStyles.medium, color = ext.negative, fractionScale = 1f)
            }
            Figure("Net", Modifier.weight(1f)) {
                MoneyText(
                    money = net,
                    style = AmountStyles.medium,
                    showPlus = true,
                    color = if (net.isNegative) ext.negative else MaterialTheme.colorScheme.onSurface,
                    fractionScale = 1f,
                )
            }
        }
        Spacer(Modifier.height(Spacing.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            LegendKey("Income", ext.positive)
            LegendKey("Expenses", ext.negative)
        }
        Spacer(Modifier.height(Spacing.md))
        IncomeExpenseChart(months = months, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Spacing.md))
        Text(
            trendSummary(months),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A plain-language reading of the trend, from exact figures only. */
private fun trendSummary(months: List<FlowMonth>): String {
    val current = months.last()
    val previous = months.getOrNull(months.size - 2)
    return buildString {
        append("${monthLabel(current.month)} so far: ${MoneyFormatter.format(current.income)} in, ${MoneyFormatter.format(current.expense)} out.")
        if (previous != null && (!previous.income.isZero || !previous.expense.isZero)) {
            append(" ${monthLabel(previous.month)}: ${MoneyFormatter.format(previous.income)} in, ${MoneyFormatter.format(previous.expense)} out.")
        }
        val positive = months.count { it.net.amountMinor > 0L }
        val recorded = months.count { !it.income.isZero || !it.expense.isZero }
        if (recorded > 1) append(" You kept money in $positive of $recorded months with records.")
    }
}

@Composable
private fun NetWorthSection(data: InsightsData, currency: String) {
    val ext = LucentoTheme.extendedColors
    SectionHeader("What your net worth is made of", Modifier.semantics { heading() })
    val composition = remember(data.wallets, currency) { NetWorthComposer.compose(data.wallets, currency) }
    if (composition.isEmpty) {
        Text(
            if (data.wallets.isEmpty()) "Add your accounts — cash, bank, e-wallets, savings, cards and loans — to see your net worth."
            else "None of your accounts use $currency.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val net = composition.netWorth
    LucentoCard(Modifier.fillMaxWidth()) {
        Figure("Net worth · $currency") {
            MoneyText(money = net, style = AmountStyles.hero, color = if (net.isNegative) ext.negative else MaterialTheme.colorScheme.onSurface)
        }
        Text(
            when {
                net.isNegative -> "You owe more than you have in $currency right now. Paying down debt raises this number."
                net.isZero -> "What you have and what you owe in $currency balance out."
                else -> "What you have minus what you owe, in $currency."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // One shared scale so the two bars compare honestly.
        val scale = maxOf(
            composition.assets.sumOf { it.amount.amountMinor.coerceAtLeast(0L) },
            composition.liabilities.sumOf { it.amount.amountMinor.coerceAtLeast(0L) },
        )
        Spacer(Modifier.height(Spacing.lg))
        CompositionGroup("Assets", composition.totalAssets, composition.assets, scale, composition)
        Spacer(Modifier.height(Spacing.lg))
        CompositionGroup("Liabilities", composition.totalLiabilities, composition.liabilities, scale, composition)
        Spacer(Modifier.height(Spacing.md))
        Text(
            "Savings goals aren’t added here — the money set aside for them is already in your account balances. " +
                "Other currencies are shown separately and never converted.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CompositionGroup(
    title: String,
    total: Money,
    components: List<NetWorthComponent>,
    scale: Long,
    composition: NetWorthComposition,
) {
    val ext = LucentoTheme.extendedColors
    val colors: List<Color> = components.map { ext.tone(it.type).content }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        AmountText(total, style = AmountStyles.row, signed = total.isNegative)
    }
    Spacer(Modifier.height(Spacing.sm))
    if (components.isEmpty()) {
        Text(
            if (title == "Liabilities") "Nothing owed in ${composition.currencyCode}." else "No assets in ${composition.currencyCode}.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    CompositionBar(
        weights = components.map { it.amount.amountMinor.coerceAtLeast(0L) },
        colors = colors,
        scaleTo = scale,
        description = "$title ${MoneyFormatter.format(total)}: " +
            components.joinToString("; ") { "${it.type.groupLabel} ${MoneyFormatter.format(it.amount)}" },
    )
    Spacer(Modifier.height(Spacing.sm))
    components.forEachIndexed { i, c ->
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(colors[i]))
            Spacer(Modifier.width(Spacing.sm))
            Text(
                "${c.type.groupLabel} · ${c.walletCount}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val share = Insights.percentOf(c.amount.coerceAtLeastZero(), total.coerceAtLeastZero())
            if (share != null) {
                Text("$share%", style = AmountStyles.small, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = Spacing.md))
            }
            AmountText(c.amount, style = AmountStyles.row, signed = c.amount.isNegative)
        }
    }
}

private fun Money.coerceAtLeastZero(): Money = if (isNegative) Money.zero(currencyCode) else this
