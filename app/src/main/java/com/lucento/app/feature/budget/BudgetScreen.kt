package com.lucento.app.feature.budget

import com.lucento.app.core.designsystem.component.StatusTones
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import com.lucento.app.core.designsystem.component.CardOverline
import com.lucento.app.core.designsystem.component.ChoicePill
import com.lucento.app.core.designsystem.component.HeroStatTile
import com.lucento.app.core.designsystem.component.ListSectionHeader
import com.lucento.app.core.designsystem.theme.Hierarchy
import com.lucento.app.domain.BudgetBurn
import com.lucento.app.feature.home.daysLeftLabel
import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.layout.IntrinsicSize
import com.lucento.app.core.designsystem.component.CardHeader
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.AlertDialog
import com.lucento.app.core.designsystem.component.SegmentedToggle
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.core.designsystem.component.CategoryTile
import com.lucento.app.core.designsystem.component.Confirmation
import com.lucento.app.core.designsystem.component.ConfirmationHost
import com.lucento.app.core.designsystem.component.ConsumptionBar
import com.lucento.app.core.designsystem.component.EmptyIllustration
import com.lucento.app.core.designsystem.component.EmptyState
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoAnimation
import com.lucento.app.core.designsystem.component.LucentoBottomSheet
import com.lucento.app.core.designsystem.component.SheetHeader
import com.lucento.app.core.designsystem.component.LucentoCard
import com.lucento.app.core.designsystem.component.HeroCard
import com.lucento.app.core.designsystem.component.HeroFigure
import com.lucento.app.core.designsystem.component.HeroPill
import com.lucento.app.core.designsystem.component.HeroTones
import com.lucento.app.core.designsystem.component.ToneSectionHeader
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.LucentoSecondaryButton
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.component.LucentoTopBar
import com.lucento.app.core.designsystem.component.MoneyText
import com.lucento.app.core.designsystem.component.ProfileButton
import com.lucento.app.core.designsystem.component.SectionHeader
import com.lucento.app.core.designsystem.component.StatusPill
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Sizes
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.Money
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.data.repository.BudgetRepository
import com.lucento.app.domain.BudgetHealth
import com.lucento.app.domain.BudgetMilestones
import com.lucento.app.domain.BudgetPlan
import com.lucento.app.domain.BudgetPlanner
import com.lucento.app.domain.CategoryBudgetLine
import com.lucento.app.domain.TransactionType
import com.lucento.app.feature.home.monthLabel
import com.lucento.app.feature.plan.MoneyField
import com.lucento.app.feature.plan.PillRow
import com.lucento.app.feature.plan.message
import com.lucento.app.feature.plan.parsePositive
import kotlinx.coroutines.launch
import java.time.YearMonth

/**
 * The Budget tab. The total is derived — always the sum of the category amounts shown below it — so there is nothing
 * to keep in sync. Every figure comes from the same [BudgetPlan] as the Home hero card and burn-rate bar.
 */
@Composable
fun BudgetScreen(
    onChooseCategories: () -> Unit,
    /** Budgets opens from Home's Quick links, so it has a way back. */
    onBack: (() -> Unit)? = null,
    onManageCategories: () -> Unit,
    viewModel: BudgetViewModel = viewModel(factory = BudgetViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var monthOffset by rememberSaveable { mutableIntStateOf(0) }
    val month = YearMonth.from(state.today).minusMonths(monthOffset.toLong())
    val currencies = remember(state.budgets, state.categories, state.primaryCurrency, month) {
        BudgetPlanner.currencies(state.budgets, state.categories, state.primaryCurrency, month)
    }
    var currency by rememberSaveable { mutableStateOf<String?>(null) }
    val shownCurrency = currency?.takeIf { it in currencies } ?: state.primaryCurrency
    val plan = remember(state.categories, state.budgets, state.transactions, shownCurrency, month) {
        BudgetPlanner.plan(state.categories, state.budgets, state.transactions, shownCurrency, month)
    }
    val isCurrentMonth = monthOffset == 0
    var editing by remember { mutableStateOf<CategoryBudgetLine?>(null) }
    var showAllBudgeted by rememberSaveable { mutableStateOf(false) }

    // A finished month kept within the category-based budget is acknowledged once (persisted), never for overspending.
    var celebration by remember { mutableStateOf<Confirmation?>(null) }
    var acknowledgedThisVisit by remember { mutableStateOf(false) }
    LaunchedEffect(state.loading, state.acknowledgedBudgetMonth, state.budgets, state.transactions) {
        if (state.loading || acknowledgedThisVisit) return@LaunchedEffect
        val kept = BudgetMilestones.monthToAcknowledge(
            categories = state.categories,
            budgets = state.budgets,
            transactions = state.transactions,
            currencyCode = state.primaryCurrency,
            today = state.today,
            lastAcknowledged = state.acknowledgedBudgetMonth,
        ) ?: return@LaunchedEffect
        acknowledgedThisVisit = true
        viewModel.acknowledgeBudgetMonth(kept.month)
        celebration = Confirmation("You stayed within ${monthLabel(kept.month)}’s budget", kind = Confirmation.Kind.Milestone)
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = com.lucento.app.core.designsystem.component.screenGutter(),
                end = com.lucento.app.core.designsystem.component.screenGutter(),
                bottom = Spacing.fabClearance,
            ),
        ) {
            item(key = "bar") {
                LucentoTopBar(
                    title = "Budget",
                    onBack = onBack,
                    actions = {
                        IconButton(onClick = onManageCategories) {
                            Icon(Icons.Outlined.Category, contentDescription = "Manage categories")
                        }
                        ProfileButton()
                    },
                )
            }

            if (state.loading) return@LazyColumn

            if (state.activeExpense.isEmpty()) {
                item(key = "finish-setup") {
                    EmptyState(
                        title = "Finish setting up your budget",
                        body = "Choose the categories you spend on. Your monthly budget is the sum of the amounts you give them.",
                        illustration = { EmptyIllustration(LucentoAnimation.EmptyBudgets) },
                        action = { LucentoPrimaryButton(text = "Choose categories", onClick = onChooseCategories) },
                    )
                }
                if (state.archivedExpense.isNotEmpty()) {
                    item(key = "restore-hint") {
                        LucentoTextButton(text = "Restore archived categories", onClick = onManageCategories)
                    }
                }
                return@LazyColumn
            }

            item(key = "month") {
                Row(Modifier.fillMaxWidth().padding(top = Spacing.xs, bottom = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    MonthSwitcher(
                        month = month,
                        canGoForward = monthOffset > 0,
                        onPrevious = { monthOffset += 1 },
                        onNext = { if (monthOffset > 0) monthOffset -= 1 },
                    )
                    // Each currency has its own budget; the choice sits beside the month only when there is one to make.
                    if (currencies.size > 1) {
                        Spacer(Modifier.width(Spacing.sm))
                        Row(
                            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.End),
                        ) {
                            currencies.forEach { code ->
                                ChoicePill(label = code, selected = code == shownCurrency, onClick = { currency = code })
                            }
                        }
                    }
                }
            }

            if (!state.categorySetupDone) {
                item(key = "personalise") {
                    PersonaliseCard(onChoose = onChooseCategories, onKeep = viewModel::keepCurrentCategories)
                    Spacer(Modifier.height(Spacing.md))
                }
            }

            plan.legacyOverall?.let { legacy ->
                item(key = "legacy") {
                    LegacyOverallCard(legacy.limit, plan, onRemove = viewModel::removeLegacyOverall)
                    Spacer(Modifier.height(Spacing.md))
                }
            }

            item(key = "summary") {
                val burn = remember(plan, state.today) { BudgetPlanner.burn(plan, state.today) }
                val daily = remember(plan, burn, state.transactions, state.wallets, state.budgets, state.schedules, state.categories) {
                    budgetDailyFigure(plan, burn, state.today, state.transactions, state.wallets, state.budgets, state.schedules, state.categories)
                }
                BudgetSummaryCard(plan, burn, isCurrentMonth = isCurrentMonth, daily = daily)
            }

            if (!plan.unbudgetedSpending.isZero) {
                item(key = "unbudgeted") {
                    InfoNote(
                        buildString {
                            append("${MoneyFormatter.format(plan.unbudgetedSpending)} unbudgeted: spent outside your budget categories this month")
                            if (!plan.uncategorizedSpending.isZero) {
                                append(" (${MoneyFormatter.format(plan.uncategorizedSpending)} has no category)")
                            }
                            append(". It still counts in Spent — give those categories an amount, or categorise the expenses, to plan for it.")
                        },
                        modifier = Modifier.padding(top = Spacing.md),
                    )
                }
            }

            if (plan.allocatedLines.isNotEmpty()) {
                item(key = "budgeted-header") {
                    val lines = plan.allocatedLines
                    ListSectionHeader(
                        "Budgeted",
                        caption = categoryCount(lines.size),
                        subtotal = "${MoneyFormatter.format(plan.budgetedSpending)} of ${MoneyFormatter.format(plan.total)}",
                    )
                }
                item(key = "budgeted-card") {
                    // Most-used first, so the categories closest to their limit are never the ones folded away.
                    val lines = plan.allocatedLines
                    val folded = !showAllBudgeted && lines.size > BUDGETED_FOLD + 1
                    val shown = if (folded) lines.take(BUDGETED_FOLD) else lines
                    LucentoCard(Modifier.fillMaxWidth(), contentPadding = 0.dp) {
                        shown.forEachIndexed { index, line ->
                            AllocationRow(line, onClick = { editing = line })
                            if (index < shown.lastIndex) Hairline(inset = RowTextInset)
                        }
                        if (lines.size > BUDGETED_FOLD + 1) {
                            Hairline(inset = RowTextInset)
                            LucentoTextButton(
                                text = if (folded) "Show ${categoryCount(lines.size - BUDGETED_FOLD)} more" else "Show fewer",
                                onClick = { showAllBudgeted = !showAllBudgeted },
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            )
                        }
                    }
                }
            }

            if (plan.unallocatedLines.isNotEmpty()) {
                item(key = "unallocated-header") {
                    ListSectionHeader(
                        if (plan.allocatedLines.isEmpty()) "Your categories" else "Not budgeted",
                        caption = "${categoryCount(plan.unallocatedLines.size)} without an amount",
                    )
                    if (plan.allocatedLines.isEmpty()) {
                        Text(
                            "Give each category a monthly amount. Your total budget is always their sum.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = Spacing.sm),
                        )
                    }
                }
                item(key = "unallocated-card") {
                    LucentoCard(Modifier.fillMaxWidth(), contentPadding = 0.dp) {
                        plan.unallocatedLines.forEachIndexed { index, line ->
                            UnallocatedRow(line, onClick = { editing = line })
                            if (index < plan.unallocatedLines.lastIndex) Hairline(inset = RowTextInset)
                        }
                    }
                }
            }

            item(key = "manage") {
                Spacer(Modifier.height(Spacing.lg))
                LucentoSecondaryButton(text = "Manage categories", icon = Icons.Outlined.Category, onClick = onManageCategories)
                InfoNote(
                    "Changing an amount affects the month you’re viewing; choose “onward” to change the months after it " +
                        "too. Earlier months always keep their amounts. " +
                        "Budgets run from the 1st to the last day of each month and don’t roll over. Only expenses count — " +
                        "transfers between your accounts, savings-goal contributions and income never do. Each currency " +
                        "has its own budget; nothing is converted.",
                    modifier = Modifier.padding(top = Spacing.lg),
                )
            }
        }
        ConfirmationHost(confirmation = celebration, onDismissed = { celebration = null })
    }

    editing?.let { line ->
        val change = remember(line, state.budgets, plan.currencyCode, month, state.today) {
            BudgetChangeContext.of(state.budgets, line.category.id, line.category.name, plan.currencyCode, month, YearMonth.from(state.today))
        }
        AllocationSheet(
            line = line,
            plan = plan,
            change = change,
            onSave = { limit, scope, done -> viewModel.saveAmount(line.category.id, plan.currencyCode, month, limit, scope, done) },
            onUseUsual = { done -> viewModel.useUsualAmount(line.category.id, plan.currencyCode, month, done) },
            onDismiss = { editing = null },
        )
    }
}

/** A compact white pill: previous · month · next. The chevrons are 48dp touch targets around the 40dp pill. */
@Composable
private fun MonthSwitcher(month: YearMonth, canGoForward: Boolean, onPrevious: () -> Unit, onNext: () -> Unit) {
    val fill = MaterialTheme.colorScheme.surfaceContainerLowest
    val edge = LucentoTheme.extendedColors.borderDefault
    Row(
        Modifier
            .heightIn(min = Sizes.minTouch)
            .drawBehind {
                val inset = 4.dp.toPx()
                val radius = CornerRadius(14.dp.toPx())
                val box = Size(size.width, size.height - inset * 2)
                drawRoundRect(fill, topLeft = Offset(0f, inset), size = box, cornerRadius = radius)
                drawRoundRect(edge, topLeft = Offset(0f, inset), size = box, cornerRadius = radius, style = Stroke(1.dp.toPx()))
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
        }
        Text(
            "${monthLabel(month)} ${month.year}",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.widthIn(min = 112.dp),
        )
        IconButton(onClick = onNext, enabled = canGoForward) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
        }
    }
}

/**
 * The month's budget as the page's statement, on brand blue (navy at night): what is left is the largest figure, with
 * the share spent beside the title; the utilisation bar with a white marker for today; the elapsed and spent shares in
 * words with the pace verdict; then Spent and the per-day figure ([DailyFigure]) as two translucent sub-cards.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BudgetSummaryCard(plan: BudgetPlan, burn: BudgetBurn, isCurrentMonth: Boolean, daily: DailyFigure?) {
    val ext = LucentoTheme.extendedColors
    HeroCard {
        Spacer(Modifier.height(Spacing.xs))
        if (!plan.hasBudget) {
            CardOverline("Monthly budget · ${plan.currencyCode}", color = ext.onHeroMuted)
            Spacer(Modifier.height(Spacing.xs))
            MoneyText(money = plan.total, style = AmountStyles.hero, color = ext.onHero)
            Text(
                "No amounts yet. Your total is the sum of your category amounts — set them below.",
                style = MaterialTheme.typography.bodySmall,
                color = ext.onHeroMuted,
            )
            Spacer(Modifier.height(Spacing.xs))
            return@HeroCard
        }
        val over = plan.remaining.isNegative
        val used = plan.percentUsed ?: 0
        val total = MoneyFormatter.format(plan.total)
        CardOverline("${monthLabel(plan.month)} budget", color = ext.onHeroMuted) {
            Text(
                "$used% SPENT",
                style = Hierarchy.cardOverline,
                color = ext.onHeroMuted,
                maxLines = 1,
                modifier = Modifier.clearAndSetSemantics { contentDescription = "$used% spent" },
            )
        }
        Spacer(Modifier.height(6.dp))
        // Signed: a negative remaining is the amount over budget.
        MoneyText(money = plan.remaining, style = AmountStyles.headline, color = ext.onHero, fractionScale = 1f, minScale = 0.5f)
        Text(
            if (over) "Over your $total budget" else "Remaining of $total budget",
            style = MaterialTheme.typography.bodyMedium,
            color = ext.onHeroMuted,
        )
        Spacer(Modifier.height(14.dp))
        ConsumptionBar(
            fraction = plan.fraction,
            color = if (over) ext.warmAccent else ext.accent,
            trackColor = ext.onHero.copy(alpha = 0.25f),
            markerFraction = if (isCurrentMonth) burn.elapsedPercent / 100f else null,
            markerColor = ext.onHero,
            markerGap = null,
            description = "$used% of the ${plan.currencyCode} budget spent" +
                if (isCurrentMonth) ", ${burn.elapsedPercent}% of the month elapsed" else "",
        )
        Spacer(Modifier.height(Spacing.sm))
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            if (isCurrentMonth) {
                HeroIndicator("${burn.elapsedPercent}%", "days elapsed", Modifier.align(Alignment.CenterVertically)) {
                    Box(Modifier.size(width = 2.dp, height = 12.dp).clip(RoundedCornerShape(1.dp)).background(ext.onHero))
                }
            }
            HeroIndicator("$used%", "budget spent", Modifier.align(Alignment.CenterVertically)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (over) ext.warmAccent else ext.accent))
            }
            Spacer(Modifier.weight(1f))
            val verdict = paceVerdict(plan, burn.pace, isCurrentMonth)
            StatusPill(verdict.label, verdictTone(verdict), Modifier.align(Alignment.CenterVertically))
        }
        Spacer(Modifier.height(14.dp))
        val spentTile: @Composable (Modifier) -> Unit = { m ->
            HeroStatTile(
                "Spent",
                Icons.Outlined.ShoppingBag,
                description = "Spent ${MoneyFormatter.format(plan.spent)}",
                modifier = m,
            ) {
                MoneyText(money = plan.spent, style = HeroFigureStyle, color = ext.onHero, fractionScale = 1f)
            }
        }
        if (LocalDensity.current.fontScale >= 1.3f) {
            // Large text: each sub-card takes the full width so its label and caption are never cut off.
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                spentTile(Modifier.fillMaxWidth())
                if (daily != null) DailyTile(daily, Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                spentTile(Modifier.weight(1f).fillMaxHeight())
                if (daily != null) DailyTile(daily, Modifier.weight(1f).fillMaxHeight())
            }
        }
        val setAside = when (daily) {
            is DailyFigure.SafePerDay -> daily.committed
            is DailyFigure.NothingLeft -> daily.committed
            else -> null
        }
        if (setAside != null && !setAside.isZero) {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "${MoneyFormatter.format(setAside)} of bills due by the end of ${monthLabel(plan.month)} is set aside first.",
                style = MaterialTheme.typography.bodySmall,
                color = ext.onHeroMuted,
            )
        }
        // D1 / P2-3: Spent is every expense, so say how it splits — budgeted + unbudgeted — and the figure adds up.
        // Same words and order as the Home budget card's formula.
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "Spent = ${MoneyFormatter.format(plan.budgetedSpending)} budgeted + " +
                "${MoneyFormatter.format(plan.unbudgetedSpending)} unbudgeted",
            style = MaterialTheme.typography.bodySmall,
            color = ext.onHero,
        )
        Spacer(Modifier.height(Spacing.xs))
    }
}

private val HeroFigureStyle = AmountStyles.medium.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)

/** One reading under the hero's bar: its key (marker or dot), the figure in white, what it measures in muted text. */
@Composable
private fun HeroIndicator(figure: String, what: String, modifier: Modifier = Modifier, key: @Composable () -> Unit) {
    val ext = LucentoTheme.extendedColors
    Row(modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        key()
        Spacer(Modifier.width(5.dp))
        Text(figure, style = AmountStyles.small.copy(fontWeight = FontWeight.SemiBold), color = ext.onHero, maxLines = 1)
        Spacer(Modifier.width(Spacing.xs))
        Text(what, style = MaterialTheme.typography.bodySmall, color = ext.onHeroMuted, maxLines = 1)
    }
}

/** The hero's per-day sub-card: Safe per day this month, Safe to spend when nothing is left to spread, or a past month's daily average. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DailyTile(daily: DailyFigure, modifier: Modifier) {
    val ext = LucentoTheme.extendedColors
    val (label, money, caption) = when (daily) {
        is DailyFigure.SafePerDay -> Triple("Safe per day", daily.perDay, "${daysLeftLabel(daily.daysLeft)} left")
        is DailyFigure.NothingLeft -> Triple("Safe to spend", daily.safe, "Nothing left per day")
        is DailyFigure.DailyAverage -> Triple("Daily average", daily.perDay, "over ${daysLeftLabel(daily.days)}")
    }
    HeroStatTile(
        label,
        Icons.Outlined.CalendarToday,
        description = "$label ${MoneyFormatter.formatSigned(money)}, $caption",
        modifier = modifier,
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MoneyText(money = money, style = HeroFigureStyle, color = ext.onHero, fractionScale = 1f, modifier = Modifier.alignByBaseline())
            Text(caption, style = MaterialTheme.typography.labelSmall, color = ext.onHeroMuted, modifier = Modifier.alignByBaseline())
        }
    }
}

/** Category rows: 16dp padding, a 36dp tile and a 12dp gap; hairlines and bars line up with the text after them. */
private val RowTile = 36.dp
private val RowTextInset = Spacing.lg + RowTile + Spacing.md

/** The Budgeted card shows this many rows, then "Show n more", unless only one more would be hidden. */
private const val BUDGETED_FOLD = 5

private fun categoryCount(n: Int): String = "$n ${if (n == 1) "category" else "categories"}"

@Composable
private fun AllocationRow(line: CategoryBudgetLine, onClick: () -> Unit) {
    val limit = line.limit ?: return
    val remaining = line.remaining ?: return
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "Change ${line.category.name} amount", onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryTile(category = line.category, type = TransactionType.EXPENSE, size = RowTile)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(line.category.name, style = Hierarchy.cardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${MoneyFormatter.format(line.spent)} of ${MoneyFormatter.format(limit)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (remaining.isNegative) "${MoneyFormatter.format(-remaining)} over" else "${MoneyFormatter.format(remaining)} left",
                    style = AmountStyles.row.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    color = if (remaining.isNegative) LucentoTheme.extendedColors.negative else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                // Every row says its status in words, "On track" included, so the column reads consistently.
                StatusPill(healthLabel(line.health, remaining), healthTone(line.health), Modifier.padding(top = 3.dp))
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        ConsumptionBar(
            fraction = line.fraction,
            color = healthColor(line.health),
            description = "${line.category.name}: ${line.percentUsed ?: 0}% used",
            height = 6.dp,
            modifier = Modifier.padding(start = RowTile + Spacing.md),
        )
    }
}

@Composable
private fun UnallocatedRow(line: CategoryBudgetLine, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(role = Role.Button, onClickLabel = "Set ${line.category.name} amount", onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryTile(category = line.category, type = TransactionType.EXPENSE, size = RowTile)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(line.category.name, style = Hierarchy.cardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (line.spent.isZero) "No amount set" else "${MoneyFormatter.format(line.spent)} spent · no amount set",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("Set amount", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun PersonaliseCard(onChoose: () -> Unit, onKeep: () -> Unit) {
    LucentoCard(Modifier.fillMaxWidth()) {
        Text("Make your budget yours", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "Lucento now builds your budget from the categories you choose. Pick from a library of everyday categories, " +
                "or keep the ones you have — your history and amounts stay exactly as they are.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.md))
        LucentoPrimaryButton(text = "Choose categories", onClick = onChoose)
        LucentoTextButton(text = "Keep my categories", onClick = onKeep, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun LegacyOverallCard(oldLimit: Money, plan: BudgetPlan, onRemove: () -> Unit) {
    LucentoCard(Modifier.fillMaxWidth()) {
        Text("Your budget now adds up by category", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "Your earlier “everything” limit of ${MoneyFormatter.format(oldLimit)} is no longer used. Your monthly budget is " +
                "the sum of your category amounts (${MoneyFormatter.format(plan.total)} now), so the two can never disagree. " +
                "Your transactions are unaffected.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LucentoTextButton(text = "Remove the old limit", onClick = onRemove)
    }
}

/**
 * Sets, changes or removes one category's amount, showing what the month's total becomes. A change applies to the month
 * being viewed unless "onward" is chosen; earlier months are never touched (docs/CALCULATIONS.md, "Budget lifecycle").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllocationSheet(
    line: CategoryBudgetLine,
    plan: BudgetPlan,
    change: BudgetChangeContext,
    onSave: (Money?, BudgetScope, (BudgetRepository.SaveResult) -> Unit) -> Unit,
    onUseUsual: ((BudgetRepository.SaveResult) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val separator = remember { MoneyFormatter.decimalSeparator() }
    val currency = plan.currencyCode
    var text by rememberSaveable { mutableStateOf(line.limit?.let { MoneyFormatter.formatPlain(it) }.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var budgetScope by rememberSaveable { mutableStateOf(change.defaultScope) }
    /** An "onward" change waiting for confirmation: the amount (null when removing). */
    var confirming by remember { mutableStateOf<OnwardChange?>(null) }

    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    fun handle(result: BudgetRepository.SaveResult) {
        when (result) {
            is BudgetRepository.SaveResult.Saved -> close()
            BudgetRepository.SaveResult.LimitNotPositive -> error = "The amount must be greater than zero."
            BudgetRepository.SaveResult.InvalidCategory -> error = "This category is archived. Restore it in Manage categories first."
            BudgetRepository.SaveResult.Duplicate, BudgetRepository.SaveResult.NotFound -> close()
        }
    }

    fun submit(amount: Money?) {
        if (change.needsConfirmation(budgetScope, removing = amount == null)) confirming = OnwardChange(amount)
        else onSave(amount, budgetScope, ::handle)
    }

    val parsed = parsePositive(text, currency, separator).first
    val newTotal = runCatching { plan.total - (line.limit ?: Money.zero(currency)) + (parsed ?: Money.zero(currency)) }.getOrNull()

    LucentoBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SheetHeader(
                title = line.category.name,
                subtitle = "${MoneyFormatter.format(line.spent)} spent in ${monthLabel(plan.month)}.",
                leading = { CategoryTile(category = line.category, type = TransactionType.EXPENSE) },
            )
            change.status?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(Spacing.xs))
            MoneyField(
                value = text,
                onValueChange = {
                    text = it
                    error = null
                },
                label = "Amount",
                currencyCode = currency,
                error = error,
                supporting = newTotal?.let { "${monthLabel(plan.month)}’s budget becomes ${MoneyFormatter.format(it)}." },
            )
            if (change.scopes.size > 1) {
                Text(
                    "Apply to",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = Spacing.xs).semantics { heading() },
                )
                SegmentedToggle(
                    options = change.scopes.map { it to change.scopeLabel(it) },
                    selected = budgetScope,
                    onSelect = { budgetScope = it },
                )
            }
            Text(
                change.scopeExplanation(budgetScope),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.sm))
            LucentoPrimaryButton(
                text = if (line.isAllocated) "Save amount" else "Set amount",
                onClick = {
                    val (money, amountError) = parsePositive(text, currency, separator)
                    if (money == null) {
                        error = amountError?.message(currency, separator)
                        return@LucentoPrimaryButton
                    }
                    submit(money)
                },
            )
            val usual = change.usual
            if (change.hasOwnAmount && usual != null) {
                LucentoTextButton(
                    text = "Use the usual ${MoneyFormatter.format(usual)}",
                    onClick = { onUseUsual(::handle) },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            // A month removed on its own still has a usual amount that can be stopped from that month on.
            if (line.isAllocated || (budgetScope == BudgetScope.ONWARD && usual != null)) {
                LucentoTextButton(
                    text = change.removeLabel(budgetScope),
                    color = MaterialTheme.colorScheme.error,
                    onClick = { submit(null) },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }

    confirming?.let { pending ->
        val removing = pending.amount == null
        com.lucento.app.core.designsystem.component.ConfirmDialog(
            title = change.confirmTitle(removing),
            text = change.confirmBody(pending.amount, removing),
            confirmLabel = if (removing) "Stop budgeting" else "Update",
            destructive = removing,
            onConfirm = {
                confirming = null
                onSave(pending.amount, BudgetScope.ONWARD, ::handle)
            },
            onDismiss = { confirming = null },
        )
    }
}

private data class OnwardChange(val amount: Money?)
