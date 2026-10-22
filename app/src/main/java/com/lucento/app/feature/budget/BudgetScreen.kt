package com.lucento.app.feature.budget

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
import com.lucento.app.core.designsystem.component.LucentoCard
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
import com.lucento.app.core.designsystem.theme.OverlineStyle
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
    onManageCategories: () -> Unit,
    viewModel: BudgetViewModel = viewModel(factory = BudgetViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var monthOffset by rememberSaveable { mutableIntStateOf(0) }
    val month = YearMonth.from(state.today).minusMonths(monthOffset.toLong())
    val currencies = remember(state.budgets, state.categories, state.primaryCurrency) {
        BudgetPlanner.currencies(state.budgets, state.categories, state.primaryCurrency)
    }
    var currency by rememberSaveable { mutableStateOf<String?>(null) }
    val shownCurrency = currency?.takeIf { it in currencies } ?: state.primaryCurrency
    val plan = remember(state.categories, state.budgets, state.transactions, shownCurrency, month) {
        BudgetPlanner.plan(state.categories, state.budgets, state.transactions, shownCurrency, month)
    }
    val isCurrentMonth = monthOffset == 0
    var editing by remember { mutableStateOf<CategoryBudgetLine?>(null) }

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
            contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.fabClearance),
        ) {
            item(key = "bar") {
                LucentoTopBar(
                    title = "Budget",
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
                MonthSwitcher(
                    month = month,
                    canGoForward = monthOffset > 0,
                    onPrevious = { monthOffset += 1 },
                    onNext = { if (monthOffset > 0) monthOffset -= 1 },
                )
            }
            if (currencies.size > 1) {
                item(key = "currencies") {
                    PillRow(currencies.map { it to it }, shownCurrency, onSelect = { currency = it })
                    Spacer(Modifier.height(Spacing.md))
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
                BudgetSummaryCard(plan, today = state.today, isCurrentMonth = isCurrentMonth)
            }

            if (!plan.unbudgetedSpending.isZero) {
                item(key = "unbudgeted") {
                    InfoNote(
                        buildString {
                            append("${MoneyFormatter.format(plan.unbudgetedSpending)} spent outside your budget this month")
                            if (!plan.uncategorizedSpending.isZero) {
                                append(" (${MoneyFormatter.format(plan.uncategorizedSpending)} has no category)")
                            }
                            append(". It isn’t hidden or moved — give those categories an amount, or categorise the expenses, to include it.")
                        },
                        modifier = Modifier.padding(top = Spacing.md),
                    )
                }
            }

            if (plan.allocatedLines.isNotEmpty()) {
                item(key = "budgeted-header") { SectionHeader("Budgeted", Modifier.padding(top = Spacing.lg)) }
                items(plan.allocatedLines, key = { "a-${it.category.id}" }) { line ->
                    AllocationRow(line, onClick = { editing = line })
                    Hairline(inset = Sizes.iconTile + Spacing.md)
                }
            }

            if (plan.unallocatedLines.isNotEmpty()) {
                item(key = "unallocated-header") {
                    SectionHeader(
                        if (plan.allocatedLines.isEmpty()) "Your categories" else "Not budgeted yet",
                        Modifier.padding(top = Spacing.lg),
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
                items(plan.unallocatedLines, key = { "u-${it.category.id}" }) { line ->
                    UnallocatedRow(line, onClick = { editing = line })
                    Hairline(inset = Sizes.iconTile + Spacing.md)
                }
            }

            item(key = "manage") {
                Spacer(Modifier.height(Spacing.lg))
                LucentoSecondaryButton(text = "Manage categories", icon = Icons.Outlined.Category, onClick = onManageCategories)
                InfoNote(
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
        AllocationSheet(
            line = line,
            plan = plan,
            onSave = { limit, done -> viewModel.setAllocation(line.category.id, plan.currencyCode, limit, done) },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun MonthSwitcher(month: YearMonth, canGoForward: Boolean, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
        }
        Text(
            "${monthLabel(month)} ${month.year}",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext, enabled = canGoForward) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
        }
    }
}

@Composable
private fun BudgetSummaryCard(plan: BudgetPlan, today: java.time.LocalDate, isCurrentMonth: Boolean) {
    val burn = remember(plan, today) { BudgetPlanner.burn(plan, today) }
    LucentoCard(Modifier.fillMaxWidth(), contentPadding = Spacing.xl) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "MONTHLY BUDGET · ${plan.currencyCode}",
                style = OverlineStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (plan.hasBudget) StatusPill(healthLabel(plan.health, plan.remaining), healthTone(plan.health))
        }
        Spacer(Modifier.height(Spacing.sm))
        if (!plan.hasBudget) {
            MoneyText(money = plan.total, style = AmountStyles.hero, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "No amounts yet. Your total is the sum of your category amounts — set them below.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@LucentoCard
        }
        val negative = plan.remaining.isNegative
        MoneyText(
            money = if (negative) -plan.remaining else plan.remaining,
            style = AmountStyles.hero,
            color = if (negative) LucentoTheme.extendedColors.negative else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            if (negative) "over budget" else "left of ${MoneyFormatter.format(plan.total)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.md))
        ConsumptionBar(
            fraction = plan.fraction,
            color = burnColor(burn.level),
            markerFraction = if (isCurrentMonth) burn.elapsedPercent / 100f else null,
            description = "${plan.percentUsed ?: 0}% of the ${plan.currencyCode} budget used" +
                if (isCurrentMonth) ", ${burn.elapsedPercent}% of the month gone" else "",
        )
        Spacer(Modifier.height(Spacing.xs))
        Row(Modifier.fillMaxWidth()) {
            Text(
                "Spent ${MoneyFormatter.format(plan.spent)} · ${plan.percentUsed ?: 0}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (isCurrentMonth) {
                Text(
                    "Day ${burn.daysElapsed} of ${burn.daysInMonth}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (isCurrentMonth) {
            burn.dailyAllowance?.let {
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    "About ${MoneyFormatter.format(it)} a day keeps you within budget.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun AllocationRow(line: CategoryBudgetLine, onClick: () -> Unit) {
    val limit = line.limit ?: return
    val remaining = line.remaining ?: return
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "Change ${line.category.name} amount", onClick = onClick)
            .padding(vertical = Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryTile(category = line.category, type = TransactionType.EXPENSE)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(line.category.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${MoneyFormatter.format(line.spent)} of ${MoneyFormatter.format(limit)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (remaining.isNegative) "${MoneyFormatter.format(-remaining)} over" else "${MoneyFormatter.format(remaining)} left",
                    style = AmountStyles.row,
                    color = if (remaining.isNegative) LucentoTheme.extendedColors.negative else MaterialTheme.colorScheme.onSurface,
                )
                if (line.health != BudgetHealth.ON_TRACK) {
                    StatusPill(healthLabel(line.health, remaining), healthTone(line.health), Modifier.padding(top = 2.dp))
                }
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        ConsumptionBar(
            fraction = line.fraction,
            color = healthColor(line.health),
            description = "${line.category.name}: ${line.percentUsed ?: 0}% used",
            height = 6.dp,
            modifier = Modifier.padding(start = Sizes.iconTile + Spacing.md),
        )
    }
}

@Composable
private fun UnallocatedRow(line: CategoryBudgetLine, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClickLabel = "Set ${line.category.name} amount", onClick = onClick)
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryTile(category = line.category, type = TransactionType.EXPENSE)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(line.category.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

/** Sets, changes or removes one category's monthly amount, showing what the total becomes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllocationSheet(
    line: CategoryBudgetLine,
    plan: BudgetPlan,
    onSave: (Money?, (BudgetRepository.SaveResult) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val separator = remember { MoneyFormatter.decimalSeparator() }
    val currency = plan.currencyCode
    var text by remember { mutableStateOf(line.limit?.let { MoneyFormatter.formatPlain(it) }.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryTile(category = line.category, type = TransactionType.EXPENSE)
                Spacer(Modifier.width(Spacing.md))
                Text(line.category.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            }
            Text(
                "${MoneyFormatter.format(line.spent)} spent in ${monthLabel(plan.month)}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xs))
            MoneyField(
                value = text,
                onValueChange = {
                    text = it
                    error = null
                },
                label = "Monthly amount",
                currencyCode = currency,
                error = error,
                supporting = newTotal?.let { "Your monthly budget becomes ${MoneyFormatter.format(it)}." },
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
                    onSave(money) { result ->
                        when (result) {
                            is BudgetRepository.SaveResult.Saved -> close()
                            BudgetRepository.SaveResult.LimitNotPositive -> error = "The amount must be greater than zero."
                            BudgetRepository.SaveResult.InvalidCategory -> error = "This category is archived. Restore it in Manage categories first."
                            BudgetRepository.SaveResult.Duplicate, BudgetRepository.SaveResult.NotFound -> close()
                        }
                    }
                },
            )
            if (line.isAllocated) {
                LucentoTextButton(
                    text = "Remove amount",
                    color = MaterialTheme.colorScheme.error,
                    onClick = { onSave(null) { close() } },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}
