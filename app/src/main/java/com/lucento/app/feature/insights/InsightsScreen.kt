package com.lucento.app.feature.insights

import com.lucento.app.core.designsystem.component.SpotlightKeys
import com.lucento.app.core.designsystem.component.spotlightTarget
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.core.designsystem.component.AmountText
import com.lucento.app.core.designsystem.component.CardOverline
import com.lucento.app.core.designsystem.component.ConsumptionBar
import com.lucento.app.core.designsystem.component.DonutChart
import com.lucento.app.core.designsystem.component.EmptyState
import com.lucento.app.core.designsystem.component.HeroCard
import com.lucento.app.core.designsystem.component.HeroStatTile
import com.lucento.app.core.designsystem.component.IconTile
import com.lucento.app.core.designsystem.component.IncomeExpenseChart
import com.lucento.app.core.designsystem.component.LegendKey
import com.lucento.app.core.designsystem.component.LucentoCard
import com.lucento.app.core.designsystem.component.LucentoTopBar
import com.lucento.app.core.designsystem.component.MascotMood
import com.lucento.app.core.designsystem.component.MascotScene
import com.lucento.app.core.designsystem.component.MoneyText
import com.lucento.app.core.designsystem.component.ProfileButton
import com.lucento.app.core.designsystem.component.SegmentedToggle
import com.lucento.app.core.designsystem.component.TrendLine
import com.lucento.app.core.designsystem.component.amountFormat
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.Hierarchy
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.Money
import com.lucento.app.domain.FlowMonth
import com.lucento.app.domain.Insights
import com.lucento.app.domain.NetWorthComposer
import com.lucento.app.domain.SpendingSlice
import com.lucento.app.feature.ask.AskLucentoHero
import com.lucento.app.feature.home.monthLabel
import com.lucento.app.feature.plan.PillRow
import com.lucento.app.feature.wallets.WalletsOverview
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private enum class TrendPeriod(val label: String, val months: Int) { THREE("3 months", 3), SIX("6 months", 6), TWELVE("12 months", 12) }

/**
 * Insights opens on Ask Lucento — questions about your money and the PDF statements — then, under one set of filters
 * (currency and period) that every section follows: income and expenses (a hero in the Budget page's form, then month by
 * month), where the money went (a donut of expenses by category) and net worth over time (month ends). From the user's
 * own records, in one currency at a time; transfers between the user's own wallets are never income or expenses. Every
 * chart has its exact figures beside it and a spoken summary. (What net worth is made of lives on Wallets.)
 */
@Composable
fun InsightsScreen(
    /** Insights opens from Home's Quick links and net flow, on top of Home; back returns there. */
    onBack: (() -> Unit)? = null,
    /** Opens Ask Lucento. */
    onAsk: () -> Unit = {},
    viewModel: InsightsViewModel = viewModel(factory = InsightsViewModel.Factory),
    reports: com.lucento.app.feature.reports.ReportsViewModel = viewModel(factory = com.lucento.app.feature.reports.ReportsViewModel.Factory),
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val reportsState by reports.uiState.collectAsStateWithLifecycle()
    var openReport by remember { mutableStateOf<com.lucento.app.domain.ask.Report?>(null) }
    var chosenPeriod by rememberSaveable { mutableStateOf<TrendPeriod?>(null) }
    // Lucento Pro: 6 and 12 months are Pro (docs/MONETIZATION.md §3.4). The free plan opens on its own 3 months, and the
    // note appears only when someone picks a locked period — never as a greeting.
    val locked = data.longPeriodsLocked
    val period = when (val chosen = chosenPeriod) {
        null -> if (locked) TrendPeriod.THREE else TrendPeriod.SIX
        else -> if (locked && chosen != TrendPeriod.THREE) TrendPeriod.THREE else chosen
    }
    val lockedPeriodChosen = locked && chosenPeriod != null && chosenPeriod != TrendPeriod.THREE
    var currencyChoice by rememberSaveable { mutableStateOf<String?>(null) }
    val currencies = remember(data.wallets, data.transactions, data.focusCurrency) {
        (listOf(data.focusCurrency) +
            NetWorthComposer.currencies(data.wallets, data.focusCurrency) +
            data.transactions.filter { it.type.isCashFlow }.map { it.currencyCode }.distinct().sorted()).distinct()
    }
    val currency = currencyChoice?.takeIf { it in currencies } ?: data.focusCurrency

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter),
    ) {
        LucentoTopBar(title = "Insights", onBack = onBack, actions = { ProfileButton() })
        AskLucentoHero(onOpen = onAsk, modifier = Modifier.padding(top = Spacing.xs))
        // Tester round 3: the built-in reports moved here from Ask Lucento, right under it.
        Spacer(Modifier.height(Spacing.section))
        SectionIntro("Reports", "Statements worked out on this phone. Save or share any of them as a PDF.")
        com.lucento.app.feature.reports.ReportList(
            onOpen = { kind -> reports.open(kind)?.let { openReport = it } },
            canOpen = { kind -> !kind.needsWallet || reportsState.statementWallets.isNotEmpty() },
            firstRowModifier = Modifier.spotlightTarget(SpotlightKeys.REPORTS),
        )
        Spacer(Modifier.height(Spacing.section))
        if (data.loading) return@Column

        // The filters, once, above everything they apply to.
        if (currencies.size > 1) {
            PillRow(currencies.map { it to it }, currency, onSelect = { currencyChoice = it })
            Spacer(Modifier.height(Spacing.sm))
        }
        val colors = MaterialTheme.colorScheme
        Box(Modifier.spotlightTarget(SpotlightKeys.INSIGHTS_PERIOD)) {
            SegmentedToggle(
                options = TrendPeriod.entries.map { it to (if (locked && it != TrendPeriod.THREE) "${it.label} · Pro" else it.label) },
                selected = period,
                onSelect = { chosenPeriod = it },
                compact = true,
                selectedTone = { com.lucento.app.core.designsystem.theme.TonePair(colors.primary, colors.onPrimary) },
            )
        }
        if (lockedPeriodChosen) {
            Spacer(Modifier.height(Spacing.sm))
            com.lucento.app.feature.pro.ProNote(
                com.lucento.app.feature.pro.ProCopy.lockedLine(com.lucento.app.domain.entitlement.ProFeature.INSIGHTS_LONG_PERIODS),
                from = com.lucento.app.domain.entitlement.ProFeature.INSIGHTS_LONG_PERIODS,
            )
        }
        Spacer(Modifier.height(Spacing.section))

        TrendSection(data, currency, period)
        Spacer(Modifier.height(Spacing.section))
        SpendingSection(data, currency, period)
        Spacer(Modifier.height(Spacing.section))
        NetWorthTrendSection(data, currency, period)
        Spacer(Modifier.height(Spacing.fabClearance))
    }
    openReport?.let { report ->
        com.lucento.app.feature.reports.ReportSheet(
            initial = report,
            rebuild = reports::rebuild,
            onSave = reports::savePdf,
            onShare = reports::pdfForSharing,
            onDismiss = { openReport = null },
            pdfLocked = reportsState.pdfLocked,
            statementWallets = reportsState.statementWallets,
            switchWallet = reports::switchWallet,
        )
    }
}

@Composable
private fun TrendSection(data: InsightsData, currency: String, period: TrendPeriod) {
    val ext = LucentoTheme.extendedColors
    val endMonth = YearMonth.from(data.today)
    // Twice the period, so the hero can compare with the one before it.
    val both = remember(data.transactions, currency, endMonth, period) {
        Insights.incomeExpenseTrend(data.transactions, currency, endMonth, period.months * 2)
    }
    val months = both.takeLast(period.months)
    val earlier = both.dropLast(period.months)
    SectionIntro("Income and expenses", "See your money flow and track your progress.")
    val hasData = months.any { !it.income.isZero || !it.expense.isZero }
    val hadData = earlier.any { !it.income.isZero || !it.expense.isZero }
    if (!hasData) {
        EmptyState(
            title = "No income or expenses yet",
            body = "Record a few incomes and expenses in $currency and you’ll see how they move month by month. " +
                "Transfers between your own accounts never count here.",
            illustration = { MascotScene(mood = MascotMood.Curious, mascotSize = 88.dp) },
        )
        return
    }
    val change = if (hadData) {
        Insights.changePercent(Money.sum(currency, months.map { it.net }), Money.sum(currency, earlier.map { it.net }))
    } else {
        null
    }
    FlowSummary(months, period, currency, change)
    Spacer(Modifier.height(Spacing.md))
    LucentoCard(Modifier.fillMaxWidth(), contentPadding = Spacing.md) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                LegendKey("Income", ext.positive)
                LegendKey("Expenses", ext.negative)
            }
            Text("Monthly", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(Spacing.md))
        IncomeExpenseChart(months = months, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Spacing.md))
        TrendSummary(months)
    }
}

/**
 * The period's income and expenses at a glance (tester round 3: "distinct from the other heroes, not overwhelming, a
 * basic summary"). A quiet card, not another brand panel: Money in and Money out side by side, each with a bar on one
 * shared scale so the larger reads larger, then one strip saying what was kept — or how much more went out — and its
 * share of income. The change against the period before sits top-right. Signs carry the meaning, never colour alone.
 */
@Composable
private fun FlowSummary(months: List<FlowMonth>, period: TrendPeriod, currency: String, change: Int?) {
    val fmt = amountFormat()
    val ext = LucentoTheme.extendedColors
    val income = Money.sum(currency, months.map { it.income })
    val expense = Money.sum(currency, months.map { it.expense })
    val net = income - expense
    val larger = if (income.amountMinor >= expense.amountMinor) income else expense
    LucentoCard(Modifier.fillMaxWidth(), contentPadding = Spacing.lg) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "LAST ${period.months} MONTHS · $currency",
                style = Hierarchy.cardOverline,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (change != null) {
                val sign = if (change > 0) "+" else if (change < 0) "−" else ""
                com.lucento.app.core.designsystem.component.StatusPill(
                    "$sign${kotlin.math.abs(change)}% vs prior",
                    if (change >= 0) com.lucento.app.core.designsystem.component.StatusTones.positive
                    else com.lucento.app.core.designsystem.component.StatusTones.negative,
                )
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            FlowHalf(
                label = "Money in",
                amount = income,
                showPlus = true,
                icon = Icons.Outlined.SouthWest,
                tone = com.lucento.app.core.designsystem.component.StatusTones.positive,
                fraction = Insights.fraction(income, larger),
                modifier = Modifier.weight(1f),
            )
            androidx.compose.material3.VerticalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(horizontal = Spacing.md),
            )
            FlowHalf(
                label = "Money out",
                amount = -expense,
                showPlus = false,
                icon = Icons.Outlined.NorthEast,
                tone = com.lucento.app.core.designsystem.component.StatusTones.negative,
                fraction = Insights.fraction(expense, larger),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(Spacing.md))
        val kept = !net.isNegative
        val share = if (kept) Insights.percentOf(net, income) else Insights.percentOf(-net, income)
        Row(
            Modifier
                .fillMaxWidth()
                .background(
                    (if (kept) ext.positiveContainer else ext.negativeContainer).copy(alpha = 0.6f),
                    RoundedCornerShape(14.dp),
                )
                .padding(horizontal = Spacing.md, vertical = Spacing.sm + Spacing.xxs)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (kept) "You kept" else "More went out than came in",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                if (share != null) {
                    Text(
                        if (kept) "$share% of what came in" else "$share% more than came in",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            MoneyText(
                money = net,
                style = AmountStyles.row.copy(fontWeight = FontWeight.Bold),
                showPlus = true,
                color = MaterialTheme.colorScheme.onSurface,
                fractionScale = 1f,
                minScale = 0.6f,
            )
        }
        if (income.isZero) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "No income recorded in these months.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "Net = ${fmt.format(income)} in − ${fmt.format(expense)} out",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
}

/** One side of the summary: a small toned arrow, what it is, the amount signed, and its bar on the shared scale. */
@Composable
private fun FlowHalf(
    label: String,
    amount: Money,
    showPlus: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tone: com.lucento.app.core.designsystem.theme.TonePair,
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    val ext = LucentoTheme.extendedColors
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, container = tone.container, content = tone.content, size = 26.dp, shape = RoundedCornerShape(8.dp))
            Spacer(Modifier.width(Spacing.sm))
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(Spacing.sm))
        MoneyText(
            money = amount,
            style = AmountStyles.medium.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
            showPlus = showPlus,
            color = MaterialTheme.colorScheme.onSurface,
            fractionScale = 1f,
            minScale = 0.5f,
        )
        Spacer(Modifier.height(Spacing.sm))
        val animated = com.lucento.app.core.designsystem.component.animatedFraction(fraction)
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(ext.track)) {
            Box(Modifier.fillMaxWidth(animated).height(6.dp).clip(RoundedCornerShape(50)).background(tone.content))
        }
    }
}

/**
 * Where the period's money went (tester round 3): a donut of expenses by category with the total in its middle, the
 * largest category called out, and every slice's share and exact amount beneath. Small categories share one "Other".
 */
@Composable
private fun SpendingSection(data: InsightsData, currency: String, period: TrendPeriod) {
    val fmt = amountFormat()
    val ext = LucentoTheme.extendedColors
    SectionIntro("Where your money went", "Your expenses by category over the last ${period.months} months.")
    val range = remember(data.today, period) { Insights.periodRange(YearMonth.from(data.today), period.months, data.today) }
    val slices = remember(data.transactions, currency, range) {
        Insights.spendingSlices(Insights.categoryBreakdown(data.transactions, currency, range))
    }
    if (slices.isEmpty()) {
        Text(
            "No expenses in $currency in these months yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val total = Money.sum(currency, slices.map { it.amount })
    val palette = ext.categorical
    val otherColor = MaterialTheme.colorScheme.outline
    fun colorOf(i: Int, slice: SpendingSlice): Color = if (slice.isOther) otherColor else palette[i % palette.size]
    fun nameOf(slice: SpendingSlice): String = when {
        slice.isOther -> "Other categories"
        slice.categoryId == null -> "Uncategorized"
        else -> data.categoriesById[slice.categoryId]?.name ?: "Uncategorized"
    }
    LucentoCard(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            DonutChart(
                values = slices.map { Insights.fraction(it.amount, total) },
                colors = slices.mapIndexed { i, s -> colorOf(i, s) },
                description = "Expenses ${fmt.spoken(total)}: " +
                    slices.joinToString("; ") { "${nameOf(it)} ${fmt.spoken(it.amount)}" },
                size = 184.dp,
                strokeWidth = 28.dp,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Spent", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MoneyText(money = total, style = AmountStyles.row.copy(fontWeight = FontWeight.Bold), fractionScale = 1f, minScale = 0.6f)
                    Text("${period.months} months", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val top = slices.first()
        val topShare = Insights.percentOf(top.amount, total)
        if (!top.isOther && topShare != null) {
            Spacer(Modifier.height(Spacing.md))
            Text(
                "${nameOf(top)} took the biggest share: $topShare% of what you spent.",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(Spacing.md))
        slices.forEachIndexed { i, slice ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(RoundedCornerShape(4.dp)).background(colorOf(i, slice)))
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    nameOf(slice),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Insights.percentOf(slice.amount, total)?.let {
                    Text("$it%", style = AmountStyles.small, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = Spacing.md))
                }
                AmountText(slice.amount, style = AmountStyles.row)
            }
        }
    }
}

/**
 * Net worth at each month end of the period (tester round 3), in the chosen currency: what the person owns minus what
 * they owe, rebuilt from opening balances and dated transactions ([WalletsOverview.trend], the Wallets hero's history),
 * so the last point is today's. Months before any wallet existed are left out.
 */
@Composable
private fun NetWorthTrendSection(data: InsightsData, currency: String, period: TrendPeriod) {
    val fmt = amountFormat()
    val ext = LucentoTheme.extendedColors
    SectionIntro("Net worth over time", "What you own minus what you owe, at the end of each month.")
    val trend = remember(data.wallets, data.transactions, currency, data.today, period) {
        WalletsOverview.trend(data.wallets, data.transactions, currency, data.today, months = period.months)
    }
    val history = trend.history
    if (history.size < 2) {
        Text(
            if (data.wallets.none { it.currencyCode == currency }) "Add your accounts in $currency to follow your net worth."
            else "Your net worth line appears once your wallets have been here for two month ends.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val first = history.first()
    val last = history.last()
    val moved = last.netWorth - first.netWorth
    val lineColor = if (moved.isNegative) ext.negative else ext.positive
    LucentoCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("Now · $currency", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MoneyText(
                    money = last.netWorth,
                    style = AmountStyles.headline,
                    color = if (last.netWorth.isNegative) ext.negative else MaterialTheme.colorScheme.onSurface,
                    fractionScale = 1f,
                    minScale = 0.5f,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Since ${monthLabel(first.month)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MoneyText(money = moved, style = AmountStyles.row, showPlus = true, color = lineColor, fractionScale = 1f)
            }
        }
        Spacer(Modifier.height(Spacing.md))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            TrendLine(
                points = history.map { it.netWorth.amountMinor },
                labels = history.map { it.month.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) },
                color = lineColor,
                description = "Net worth from ${fmt.spoken(first.netWorth)} at the end of ${monthLabel(first.month)} " +
                    "to ${fmt.spoken(last.netWorth)} now.",
                width = maxWidth,
                height = 120.dp,
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            "Savings goals aren’t added here — the money set aside for them is already in your account balances. " +
                "A new account adds its opening balance in the month you added it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A plain-language reading of the trend, from exact figures only, on a soft tinted panel: this month so far, last month,
 * and — once there are two months with records — how many of them kept money.
 */
@Composable
private fun TrendSummary(months: List<FlowMonth>) {
    val fmt = amountFormat()
    val ext = LucentoTheme.extendedColors
    val current = months.last()
    val previous = months.getOrNull(months.size - 2)
    val positive = months.count { it.net.amountMinor > 0L }
    val recorded = months.count { !it.income.isZero || !it.expense.isZero }
    Column(
        Modifier
            .fillMaxWidth()
            .background(ext.positiveContainer.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
            .padding(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            IconTile(
                Icons.Outlined.CalendarMonth,
                container = MaterialTheme.colorScheme.surfaceContainerLowest,
                content = ext.success,
                size = 36.dp,
                shape = CircleShape,
            )
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    "${monthLabel(current.month)} so far: ${fmt.format(current.income)} in, ${fmt.format(current.expense)} out.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                if (previous != null && (!previous.income.isZero || !previous.expense.isZero)) {
                    Text(
                        "${monthLabel(previous.month)}: ${fmt.format(previous.income)} in, ${fmt.format(previous.expense)} out.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (recorded > 1) {
            Spacer(Modifier.height(Spacing.sm))
            Row(
                Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.7f), RoundedCornerShape(50))
                    .padding(horizontal = Spacing.sm + Spacing.xxs, vertical = Spacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (positive > 0) Icons.Filled.Check else Icons.Outlined.Info,
                    contentDescription = null,
                    tint = if (positive > 0) ext.success else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(Spacing.xs))
                Text("You kept money in $positive of $recorded months with records.", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** "+8% vs. prior 6 months" on the hero: a trend arrow and the signed change of net flow. */
@Composable
private fun ChangeChip(percent: Int, against: String, modifier: Modifier = Modifier) {
    val ext = LucentoTheme.extendedColors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.padding(top = Spacing.xxs)) {
        Row(
            Modifier
                .background(ext.onHero.copy(alpha = 0.16f), RoundedCornerShape(50))
                .padding(horizontal = Spacing.sm, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (percent >= 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                contentDescription = null,
                tint = ext.onHero,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(Spacing.xxs))
            Text((if (percent > 0) "+" else if (percent < 0) "−" else "") + "${kotlin.math.abs(percent)}%", style = MaterialTheme.typography.labelLarge, color = ext.onHero)
        }
        Spacer(Modifier.width(Spacing.xs))
        Text(against, style = MaterialTheme.typography.bodySmall, color = ext.onHeroMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A section's title in large type, with one line on what it shows (the reference's section intros). */
@Composable
private fun SectionIntro(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.semantics { heading() })
    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(Spacing.md))
}
