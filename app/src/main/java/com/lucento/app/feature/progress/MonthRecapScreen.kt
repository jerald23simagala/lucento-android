package com.lucento.app.feature.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallMade
import androidx.compose.material.icons.automirrored.outlined.CallReceived
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.core.designsystem.component.CardOverline
import com.lucento.app.core.designsystem.component.Confirmation
import com.lucento.app.core.designsystem.component.ConfirmationHost
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.HeroCard
import com.lucento.app.core.designsystem.component.HeroStatTile
import com.lucento.app.core.designsystem.component.IconTile
import com.lucento.app.core.designsystem.component.LucentoCard
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.component.LucentoTopBar
import com.lucento.app.core.designsystem.component.MoneyText
import com.lucento.app.core.designsystem.component.ProfileButton
import com.lucento.app.core.designsystem.component.amountFormat
import com.lucento.app.core.designsystem.component.screenGutter
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.Hierarchy
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.domain.BudgetMilestones
import com.lucento.app.domain.Category
import com.lucento.app.domain.MonthCheckIn
import com.lucento.app.domain.MonthRecap
import com.lucento.app.domain.MonthRecaps
import com.lucento.app.feature.budget.MonthSwitcher
import com.lucento.app.feature.home.monthLabel
import java.time.YearMonth

/**
 * A month's recap on its own page (owner, 2026-11-23: Milestones keeps the year on top; a month opens from its square and
 * from Home's check-in card): what was kept, the budget, where the money went, the biggest change, net worth, goals, debts
 * and days recorded, with arrows to step between months. Free on every plan. Figures come from [MonthRecaps], which
 * reuses the calculators Home, Budget, Wallets and Goals use, so they always agree.
 */
@Composable
fun MonthRecapScreen(
    onBack: () -> Unit,
    /** The month to open on; null: last month in the first days of a new one, otherwise this month so far. */
    initialMonth: YearMonth? = null,
    /** Insights, when that module is on. */
    onOpenInsights: (() -> Unit)? = null,
    viewModel: ProgressViewModel = viewModel(factory = ProgressViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val today = state.today
    val current = YearMonth.from(today)
    var chosen by rememberSaveable { mutableStateOf(initialMonth?.toString()) }
    val month = chosen?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
        ?.coerceIn(state.earliestMonth, current)
        ?: MonthCheckIn.recapMonth(today)?.takeIf { it >= state.earliestMonth } ?: current
    val inputs = state.inputs
    val recap = remember(inputs, state.currency, month) { inputs?.let { MonthRecaps.month(it, state.currency, month) } }

    var celebration by remember { mutableStateOf<Confirmation?>(null) }
    var celebrated by remember { mutableStateOf(false) }

    // The check-in's recap, looked at: Home stops offering it.
    LaunchedEffect(state.loading, month) {
        if (!state.loading && month == MonthCheckIn.recapMonth(today)) viewModel.markRecapSeen(month)
    }
    // Last month kept within budget is celebrated once, here or on the Budget tab, whichever comes first (the same flag).
    LaunchedEffect(state.loading, month, state.acknowledgedBudgetMonth) {
        val ledger = state.ledger
        if (state.loading || celebrated || ledger == null || inputs == null || month != current.minusMonths(1)) return@LaunchedEffect
        val kept = BudgetMilestones.monthToAcknowledge(
            categories = inputs.categories,
            budgets = inputs.budgets,
            ledger = ledger,
            currencyCode = state.primaryCurrency,
            today = today,
            lastAcknowledged = state.acknowledgedBudgetMonth,
        ) ?: return@LaunchedEffect
        celebrated = true
        viewModel.acknowledgeBudgetMonth(kept.month)
        celebration = Confirmation("You stayed within ${monthLabel(kept.month)}’s budget", kind = Confirmation.Kind.Milestone)
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = screenGutter()),
        ) {
            LucentoTopBar(title = "Month recap", onBack = onBack, actions = { ProfileButton() })
            Text(
                "How the month went, from your own records.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.loading || recap == null) return@Column
            Spacer(Modifier.height(Spacing.md))
            MonthSwitcher(
                month = month,
                canGoForward = month < current,
                canGoBack = month > state.earliestMonth,
                onPrevious = { if (month > state.earliestMonth) chosen = month.minusMonths(1).toString() },
                onNext = { if (month < current) chosen = month.plusMonths(1).toString() },
            )
            Spacer(Modifier.height(Spacing.sm))
            RecapHero(recap)
            Spacer(Modifier.height(Spacing.md))
            RecapDetails(recap, state.categoriesById)
            if (onOpenInsights != null) {
                LucentoTextButton(
                    text = "See trends in Insights",
                    onClick = onOpenInsights,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            Spacer(Modifier.height(Spacing.fabClearance))
        }
        ConfirmationHost(confirmation = celebration, onDismissed = { celebration = null })
    }
}

/**
 * The month as one statement on brand blue: what was kept (signed: less is minus), the sentence that says what it means,
 * then money in and money out. A month still running says "so far".
 */
@Composable
private fun RecapHero(recap: MonthRecap) {
    val fmt = amountFormat()
    val ext = LucentoTheme.extendedColors
    HeroCard {
        Spacer(Modifier.height(Spacing.xs))
        // A polite live region: a month picked in the year (or with the arrows) is announced as it appears.
        CardOverline(
            "${monthLabel(recap.month)} ${recap.month.year}" + if (recap.finished) "" else " · so far",
            color = ext.onHeroMuted,
            modifier = Modifier.semantics {
                liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite
                contentDescription = "Showing ${monthLabel(recap.month)} ${recap.month.year}" + if (recap.finished) "" else ", so far"
            },
        )
        Spacer(Modifier.height(6.dp))
        if (!recap.hasRecords) {
            Text(
                "Nothing recorded in ${monthLabel(recap.month)}" + if (recap.finished) "." else " yet.",
                style = MaterialTheme.typography.titleMedium,
                color = ext.onHero,
            )
            Text(
                "Add what came in and went out, and this is where its story appears.",
                style = MaterialTheme.typography.bodySmall,
                color = ext.onHeroMuted,
            )
            Spacer(Modifier.height(Spacing.xs))
            return@HeroCard
        }
        MoneyText(money = recap.net, style = AmountStyles.headline, color = ext.onHero, showPlus = true, fractionScale = 1f, minScale = 0.5f)
        Text(RecapCopy.headline(recap), style = MaterialTheme.typography.bodyMedium, color = ext.onHeroMuted)
        Spacer(Modifier.height(14.dp))
        val inTile: @Composable (Modifier) -> Unit = { m ->
            HeroStatTile("Money in", Icons.AutoMirrored.Outlined.CallReceived, description = "Money in ${fmt.spoken(recap.income)}", modifier = m) {
                MoneyText(money = recap.income, style = HeroFigure, color = ext.onHero, fractionScale = 1f)
            }
        }
        val outTile: @Composable (Modifier) -> Unit = { m ->
            HeroStatTile("Money out", Icons.AutoMirrored.Outlined.CallMade, description = "Money out ${fmt.spoken(recap.spent)}", modifier = m) {
                MoneyText(money = recap.spent, style = HeroFigure, color = ext.onHero, fractionScale = 1f)
            }
        }
        if (LocalDensity.current.fontScale >= 1.3f) {
            // Large text: each tile takes the full width, so its label and figure are never cut off.
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                inTile(Modifier.fillMaxWidth())
                outTile(Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                inTile(Modifier.weight(1f).fillMaxHeight())
                outTile(Modifier.weight(1f).fillMaxHeight())
            }
        }
        Spacer(Modifier.height(Spacing.xs))
    }
}

private val HeroFigure = AmountStyles.medium.copy(fontWeight = FontWeight.Bold)

/** The month in a few lines: only those with something to say, each an icon, a statement and what it rests on. */
@Composable
private fun RecapDetails(recap: MonthRecap, categories: Map<Long, Category>) {
    val fmt = amountFormat()
    val lines = remember(recap, categories, fmt) { RecapCopy.lines(recap, { id -> id?.let { categories[it]?.name } }, fmt) }
    if (lines.isEmpty()) return
    LucentoCard(Modifier.fillMaxWidth(), contentPadding = 0.dp) {
        lines.forEachIndexed { index, line ->
            RecapRow(line)
            if (index < lines.lastIndex) Hairline(inset = Spacing.md + 34.dp + Spacing.sm + Spacing.xxs)
        }
    }
}

@Composable
private fun RecapRow(line: RecapLine) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = Spacing.md, vertical = Spacing.sm + Spacing.xxs),
        verticalAlignment = if (line.details.isEmpty()) Alignment.CenterVertically else Alignment.Top,
    ) {
        IconTile(
            icon = line.kind.icon(),
            size = 34.dp,
            container = MaterialTheme.colorScheme.surfaceContainerHigh,
            content = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(Spacing.sm + Spacing.xxs))
        Column(Modifier.weight(1f)) {
            Text(line.title, style = Hierarchy.cardTitle.copy(lineBreak = androidx.compose.ui.text.style.LineBreak.Heading))
            line.details.forEach { detail ->
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall.copy(lineBreak = androidx.compose.ui.text.style.LineBreak.Heading),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun RecapLine.Kind.icon(): ImageVector = when (this) {
    RecapLine.Kind.BUDGET -> Icons.Outlined.PieChart
    RecapLine.Kind.TOP -> Icons.Outlined.ShoppingBag
    RecapLine.Kind.MORE -> Icons.AutoMirrored.Outlined.TrendingUp
    RecapLine.Kind.LESS -> Icons.AutoMirrored.Outlined.TrendingDown
    RecapLine.Kind.NET_WORTH -> Icons.Outlined.AccountBalance
    RecapLine.Kind.GOALS -> Icons.Outlined.TrackChanges
    RecapLine.Kind.DEBTS -> Icons.Outlined.CreditCard
    RecapLine.Kind.DAYS -> Icons.Outlined.CalendarMonth
}
