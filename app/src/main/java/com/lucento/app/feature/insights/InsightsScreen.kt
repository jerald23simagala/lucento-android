package com.lucento.app.feature.insights

import com.lucento.app.core.designsystem.component.amountFormat
import com.lucento.app.core.designsystem.component.HeroTones
import com.lucento.app.core.designsystem.component.HeroPill
import com.lucento.app.core.designsystem.component.HeroFigure
import com.lucento.app.core.designsystem.component.HeroCard
import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.layout.IntrinsicSize
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
import com.lucento.app.core.designsystem.component.MascotScene
import com.lucento.app.core.designsystem.component.LucentoTopBar
import com.lucento.app.core.designsystem.component.MascotMood
import com.lucento.app.core.designsystem.component.MoneyText
import com.lucento.app.core.designsystem.component.ProfileButton
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
import com.lucento.app.feature.ask.AskLucentoHero
import com.lucento.app.core.designsystem.component.IconTile
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material3.Icon
import androidx.compose.ui.text.font.FontWeight

private enum class TrendPeriod(val label: String, val months: Int) { THREE("3 months", 3), SIX("6 months", 6), TWELVE("12 months", 12) }

/**
 * Insights opens on Ask Lucento — questions about your money and the PDF statements — then answers two questions, from
 * the user's own records and in one currency at a time: how are my income and expenses trending, and what is my net
 * worth made of? Transfers between the user's own wallets are never income or expenses; savings goals are never added
 * to net worth (their money is already in the wallet balances). Every chart has its exact figures beside it and a
 * spoken summary.
 */
@Composable
fun InsightsScreen(
    /** Insights opens from Home's Quick links and net flow, on top of Home; back returns there. */
    onBack: (() -> Unit)? = null,
    /** Opens Ask Lucento. */
    onAsk: () -> Unit = {},
    viewModel: InsightsViewModel = viewModel(factory = InsightsViewModel.Factory),
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
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
        Spacer(Modifier.height(Spacing.lg))
        if (data.loading) return@Column
        if (currencies.size > 1) {
            PillRow(currencies.map { it to it }, currency, onSelect = { currencyChoice = it })
            Spacer(Modifier.height(Spacing.md))
        }

        TrendSection(data, currency, period, onPeriod = { chosenPeriod = it }, lockedChoice = lockedPeriodChosen)
        Spacer(Modifier.height(Spacing.section))
        if (data.compositionLocked) {
            SectionIntro("What your net worth is made of", "Your accounts and debts, by kind.")
            Spacer(Modifier.height(Spacing.sm))
            com.lucento.app.feature.pro.ProNote(
                com.lucento.app.feature.pro.ProCopy.lockedLine(com.lucento.app.domain.entitlement.ProFeature.NET_WORTH_COMPOSITION),
                from = com.lucento.app.domain.entitlement.ProFeature.NET_WORTH_COMPOSITION,
            )
        } else {
            NetWorthSection(data, currency)
        }
        Spacer(Modifier.height(Spacing.fabClearance))
    }
}

@Composable
private fun TrendSection(data: InsightsData, currency: String, period: TrendPeriod, onPeriod: (TrendPeriod) -> Unit, lockedChoice: Boolean = false) {
    val ext = LucentoTheme.extendedColors
    val endMonth = YearMonth.from(data.today)
    // Twice the period, so the hero can compare with the one before it.
    val both = remember(data.transactions, currency, endMonth, period) {
        Insights.incomeExpenseTrend(data.transactions, currency, endMonth, period.months * 2)
    }
    val months = both.takeLast(period.months)
    val earlier = both.dropLast(period.months)
    SectionIntro("Income and expenses", "See your money flow and track your progress.")
    val colors = MaterialTheme.colorScheme
    SegmentedToggle(
        options = TrendPeriod.entries.map { it to (if (data.longPeriodsLocked && it != TrendPeriod.THREE) "${it.label} · Pro" else it.label) },
        selected = period,
        onSelect = onPeriod,
        compact = true,
        selectedTone = { com.lucento.app.core.designsystem.theme.TonePair(colors.primary, colors.onPrimary) },
    )
    if (lockedChoice) {
        Spacer(Modifier.height(Spacing.sm))
        com.lucento.app.feature.pro.ProNote(
            com.lucento.app.feature.pro.ProCopy.lockedLine(com.lucento.app.domain.entitlement.ProFeature.INSIGHTS_LONG_PERIODS),
            from = com.lucento.app.domain.entitlement.ProFeature.INSIGHTS_LONG_PERIODS,
        )
    }
    Spacer(Modifier.height(Spacing.md))
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
    val income = Money.sum(currency, months.map { it.income })
    val expense = Money.sum(currency, months.map { it.expense })
    val net = income - expense
    // The period's statement, on brand blue (the Wallets / Recurring hero): net on the left, its two halves stacked.
    HeroCard {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1.15f)) {
                HeroPill("Net · last ${period.months} months", if (net.isNegative) HeroTones.tangerine else HeroTones.main)
                Spacer(Modifier.height(Spacing.xs))
                // Cents at full size, as on Home and Wallets (owner, 2026-11-06: the decimals read smaller than the figure).
                MoneyText(money = net, style = AmountStyles.hero, showPlus = true, color = ext.onHero, fractionScale = 1f, minScale = 0.5f)
                val change = if (hadData) Insights.changePercent(net, Money.sum(currency, earlier.map { it.net })) else null
                if (change != null) {
                    ChangeChip(change, "vs. prior ${period.months} months")
                } else {
                    Text(
                        if (net.isNegative) "More went out than came in" else "Money in minus money out",
                        style = MaterialTheme.typography.bodySmall,
                        color = ext.onHeroMuted,
                        maxLines = 1,
                    )
                }
            }
            VerticalDivider(color = ext.onHeroMuted.copy(alpha = 0.35f), modifier = Modifier.padding(horizontal = Spacing.md))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                HeroFigure("Income", HeroTones.sunlight) {
                    MoneyText(money = income, style = AmountStyles.row, showPlus = true, color = ext.onHero, fractionScale = 1f)
                }
                HeroFigure("Expenses", HeroTones.tangerine) {
                    MoneyText(money = -expense, style = AmountStyles.row, color = ext.onHero, fractionScale = 1f)
                }
            }
        }
    }
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
private fun ChangeChip(percent: Int, against: String) {
    val ext = LucentoTheme.extendedColors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.xxs)) {
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

@Composable
private fun NetWorthSection(data: InsightsData, currency: String) {
    val ext = LucentoTheme.extendedColors
    SectionIntro("What your net worth is made of", "Your accounts and debts, by kind.")
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
            MoneyText(money = net, style = AmountStyles.hero, color = if (net.isNegative) ext.negative else MaterialTheme.colorScheme.onSurface, fractionScale = 1f, minScale = 0.5f)
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
    val fmt = amountFormat()
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
        description = "$title ${fmt.spoken(total)}: " +
            components.joinToString("; ") { "${it.type.groupLabel} ${fmt.spoken(it.amount)}" },
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
