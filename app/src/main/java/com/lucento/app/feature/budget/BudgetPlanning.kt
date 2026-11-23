package com.lucento.app.feature.budget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.core.designsystem.component.AmountFormat
import com.lucento.app.core.designsystem.component.CardOverline
import com.lucento.app.core.designsystem.component.CategoryTile
import com.lucento.app.core.designsystem.component.ChoicePill
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.HeroCard
import com.lucento.app.core.designsystem.component.IconTile
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.ListSectionHeader
import com.lucento.app.core.designsystem.component.LucentoBottomSheet
import com.lucento.app.core.designsystem.component.LucentoCard
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.LucentoSecondaryButton
import com.lucento.app.core.designsystem.component.LucentoTopBar
import com.lucento.app.core.designsystem.component.MoneyText
import com.lucento.app.core.designsystem.component.SheetHeader
import com.lucento.app.core.designsystem.component.amountFormat
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.Hierarchy
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.Money
import com.lucento.app.data.repository.BudgetRepository
import com.lucento.app.data.repository.CategoryRepository
import com.lucento.app.domain.Budget
import com.lucento.app.domain.BudgetPlan
import com.lucento.app.domain.BudgetPlanner
import com.lucento.app.domain.Category
import com.lucento.app.domain.CategoryBudgetLine
import com.lucento.app.domain.Frequency
import com.lucento.app.domain.MonthPlanHints
import com.lucento.app.domain.PlanHint
import com.lucento.app.domain.TransactionType
import com.lucento.app.feature.home.monthLabel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Where a planned month's amount for one category comes from: its own amount for that month, or the usual one. */
internal data class PlannedSource(val ownAmount: Boolean, val usual: Money?)

/**
 * [line]'s source in [month], from the stored rows ([BudgetPlanner]'s lifecycle rules). [currencyCode] is the plan's, for a
 * line without an amount; otherwise the amount's own.
 */
internal fun plannedSource(
    budgets: List<Budget>,
    line: CategoryBudgetLine,
    month: YearMonth,
    currencyCode: String? = null,
): PlannedSource {
    val currency = line.allocation?.currencyCode ?: currencyCode ?: return PlannedSource(ownAmount = false, usual = null)
    val rows = budgets.filter { it.categoryId == line.category.id && it.currencyCode == currency }
    return PlannedSource(
        ownAmount = rows.any { it.month == month },
        usual = BudgetPlanner.recurringAllocation(rows, month)?.limit?.takeIf { it.amountMinor > 0L },
    )
}

/**
 * A month's planned budget on brand blue: the total (always the sum of its categories) and how it compares with the month
 * before. For a month already running, what is spent so far too. Nothing here judges pace: the month hasn't happened yet.
 */
@Composable
internal fun PlannedSummaryCard(plan: BudgetPlan, previous: BudgetPlan?, today: LocalDate) {
    val fmt = amountFormat()
    val ext = LucentoTheme.extendedColors
    HeroCard {
        Spacer(Modifier.height(Spacing.xs))
        CardOverline("${monthLabel(plan.month)} budget · planned", color = ext.onHeroMuted)
        Spacer(Modifier.height(6.dp))
        MoneyText(money = plan.total, style = AmountStyles.headline, color = ext.onHero, fractionScale = 1f, minScale = 0.5f)
        Text(plannedComparison(plan, previous, fmt), style = MaterialTheme.typography.bodyMedium, color = ext.onHeroMuted)
        if (plan.month == YearMonth.from(today) && !plan.spent.isZero) {
            Spacer(Modifier.height(Spacing.xs))
            Text("${fmt.format(plan.spent)} spent so far", style = MaterialTheme.typography.bodySmall, color = ext.onHero)
        }
        Spacer(Modifier.height(Spacing.xs))
    }
}

/** One line on the planned total: how it compares with [previous] month's (both sums of their categories). */
internal fun plannedComparison(plan: BudgetPlan, previous: BudgetPlan?, fmt: AmountFormat): String = when {
    !plan.hasBudget -> "No amounts yet. Your total is the sum of your category amounts — set them below."
    previous == null || !previous.hasBudget -> "The sum of your category amounts for ${monthLabel(plan.month)}."
    plan.total == previous.total -> "The same as ${monthLabel(previous.month)}."
    plan.total > previous.total ->
        "${fmt.format(plan.total - previous.total)} more than ${monthLabel(previous.month)}’s ${fmt.format(previous.total)}."
    else -> "${fmt.format(previous.total - plan.total)} less than ${monthLabel(previous.month)}’s ${fmt.format(previous.total)}."
}

/** A category's planned amount in a month that hasn't been spent yet: where the amount comes from, then the amount. */
@Composable
internal fun PlannedRow(line: CategoryBudgetLine, source: PlannedSource, onClick: (() -> Unit)?) {
    val fmt = amountFormat()
    val limit = line.limit ?: return
    // From 130 % text the amount moves under the name, so neither is ever cut short (critic r1, P0-1).
    val stacked = LocalDensity.current.fontScale >= 1.3f
    val amount = @Composable {
        Text(fmt.format(limit), style = AmountStyles.row.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .let { if (onClick != null) it.clickable(role = Role.Button, onClickLabel = "Change ${line.category.name} amount", onClick = onClick) else it }
            .padding(horizontal = RowPadding, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryTile(category = line.category, type = TransactionType.EXPENSE, size = RowTile)
        Spacer(Modifier.width(RowGap))
        Column(Modifier.weight(1f)) {
            // The whole name, however long (40 characters at most), at any text size (critic r2).
            Text(line.category.name, style = Hierarchy.cardTitle)
            Text(
                plannedRowDetail(line, source, fmt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (stacked) amount()
        }
        if (!stacked) {
            Spacer(Modifier.width(Spacing.sm))
            amount()
        }
        // A planned amount can be changed: the chevron says so, as on the app's other rows that open something (critic r2, P2-6).
        if (onClick != null) {
            Spacer(Modifier.width(Spacing.xs))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "Only December · usually ₱2,000", or "Every month", and what is spent so far when there is something. */
internal fun plannedRowDetail(line: CategoryBudgetLine, source: PlannedSource, fmt: AmountFormat): String = buildString {
    val month = line.allocation?.month
    if (source.ownAmount && month != null) {
        append("Only ${monthLabel(month)}")
        source.usual?.let { append(" · usually ${fmt.format(it)}") }
    } else {
        append("Every month")
    }
    if (!line.spent.isZero) append(" · ${fmt.format(line.spent)} spent so far")
}

/**
 * The monthly check-in's plan page (owner, 2026-11-23): one month's budget, ready for what is different about it — a
 * birthday, an event, a yearly bill. Hints from the person's own records come first; every change starts as that month's
 * only ([BudgetChangeContext.planning]). The first saved change, or "Done planning", records the month as planned so Home
 * stops asking. The figures are the Budget tab's own (the same [BudgetPlanner] plan, from the translated ledger).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthPlanScreen(
    month: YearMonth,
    onBack: () -> Unit,
    /** Done planning: the month's planned total (null without a budget), for the confirmation. */
    onDone: (Money?) -> Unit,
    viewModel: BudgetViewModel = viewModel(factory = BudgetViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val fmt = amountFormat()
    val ledger = remember(state.primaryCurrency, state.wallets, state.transactions) {
        com.lucento.app.domain.fx.FxTranslation.translate(state.primaryCurrency, state.wallets, state.transactions)
    }
    val focus = remember(state.primaryCurrency, ledger) {
        com.lucento.app.domain.FocusCurrency.of(state.primaryCurrency, ledger.wallets, ledger.transactions)
    }
    val currencies = remember(state.budgets, state.categories, focus, month) {
        BudgetPlanner.currencies(state.budgets, state.categories, focus, month)
    }
    var currency by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = currency?.takeIf { it in currencies } ?: focus
    val plan = remember(state.categories, state.budgets, ledger, shown, month) {
        BudgetPlanner.plan(state.categories, state.budgets, ledger.transactions, shown, month)
    }
    val previous = remember(state.categories, state.budgets, ledger, shown, month) {
        BudgetPlanner.plan(state.categories, state.budgets, ledger.transactions, shown, month.minusMonths(1))
    }
    val hints = remember(state.categories, state.budgets, ledger, state.schedules, shown, month, state.today) {
        MonthPlanHints.hints(month, state.today, state.categories, state.budgets, ledger.transactions, state.schedules, shown)
    }
    val current = YearMonth.from(state.today)
    val canEdit = com.lucento.app.feature.sharedspace.LocalSharedAccess.current.canManageStructure
    var editing by remember { mutableStateOf<CategoryBudgetLine?>(null) }
    // A hint's amount, carried into the sheet it opens (critic r1, P1-3): never retyped from the line above it.
    var suggestion by remember { mutableStateOf<HintSuggestion?>(null) }
    // "Add something" on a category that already has an amount: the sheet asks for the extra (critic r2, P2-7).
    var extra by remember { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var naming by rememberSaveable { mutableStateOf(false) }
    // A category made from "Add something" opens its amount as soon as it is part of the plan.
    var created by rememberSaveable { mutableStateOf<Long?>(null) }
    var finishing by remember { mutableStateOf(false) }
    LaunchedEffect(created, plan) {
        val id = created ?: return@LaunchedEffect
        plan.lines.firstOrNull { it.category.id == id }?.let {
            suggestion = null
            extra = false
            editing = it
            created = null
        }
    }
    val lineOf: (Long?) -> CategoryBudgetLine? = { id -> plan.lines.firstOrNull { it.category.id == id } }
    // A saved change is planning the month: Home stops asking, even if the person leaves with Back (critic r2, P2-5).
    val plannedBy: (BudgetRepository.SaveResult) -> Unit = { if (it is BudgetRepository.SaveResult.Saved) viewModel.markPlanned(month) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = com.lucento.app.core.designsystem.component.screenGutter(),
            end = com.lucento.app.core.designsystem.component.screenGutter(),
            bottom = Spacing.fabClearance,
        ),
    ) {
        item(key = "bar") {
            // The title has the bar to itself and may take two lines, so "Plan September" is never cut (critic r2, P0-1).
            // Done planning ends the page, and the first saved change already counts as planning the month.
            LucentoTopBar(title = "Plan ${monthLabel(month)}", onBack = onBack, titleMaxLines = 2)
        }
        if (state.loading) return@LazyColumn

        item(key = "intro") {
            Text(
                planIntro(month, previous, state.today, fmt),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.md),
            )
        }
        if (currencies.size > 1) {
            item(key = "currencies") {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    currencies.forEach { code -> ChoicePill(label = code, selected = code == shown, onClick = { currency = code }) }
                }
            }
        }
        item(key = "summary") { PlannedSummaryCard(plan, previous = previous, today = state.today) }

        if (hints.isNotEmpty()) {
            item(key = "hints-header") { ListSectionHeader("Worth a look") }

            item(key = "hints") {
                LucentoCard(Modifier.fillMaxWidth(), contentPadding = 0.dp) {
                    hints.forEachIndexed { index, hint ->
                        val line = lineOf(hint.categoryId)
                        val source = line?.let { plannedSource(state.budgets, it, month, plan.currencyCode) }
                        // Acted on: the month has its own amount for this category, so the hint says what is set and the
                        // sheet opens at that amount, never at a figure worked out again from it (critic r2, P0-2).
                        val handled = if (line?.limit != null && source?.ownAmount == true) hintHandledText(hint, line, month, fmt) else null
                        HintRow(
                            hint,
                            state.categoriesById,
                            handled = handled,
                            onClick = if (canEdit && line != null) ({
                                suggestion = if (handled != null) null else hintSuggestion(hint, line, source?.usual, fmt)
                                extra = false
                                editing = line
                            }) else null,
                        )
                        if (index < hints.lastIndex) Hairline(inset = RowTextInset)
                    }
                }
            }
        }

        if (canEdit) {
            item(key = "add") {
                Spacer(Modifier.height(Spacing.lg))
                LucentoSecondaryButton(text = "Add something for ${monthLabel(month)}", icon = Icons.Outlined.Add, onClick = { adding = true })
            }
        }

        if (plan.allocatedLines.isNotEmpty()) {
            item(key = "budgeted-header") {
                ListSectionHeader("Budgeted", caption = categoryCount(plan.allocatedLines.size), subtotal = "${fmt.format(plan.total)} planned")
            }
            item(key = "budgeted") {
                LucentoCard(Modifier.fillMaxWidth(), contentPadding = 0.dp) {
                    plan.allocatedLines.forEachIndexed { index, line ->
                        PlannedRow(line, plannedSource(state.budgets, line, month), onClick = if (canEdit) ({ editing = line }) else null)
                        if (index < plan.allocatedLines.lastIndex) Hairline(inset = RowTextInset)
                    }
                }
            }
        }
        if (plan.unallocatedLines.isNotEmpty()) {
            item(key = "unallocated-header") {
                ListSectionHeader("Not budgeted", caption = "${categoryCount(plan.unallocatedLines.size)} without an amount")
            }
            item(key = "unallocated") {
                LucentoCard(Modifier.fillMaxWidth(), contentPadding = 0.dp) {
                    plan.unallocatedLines.forEachIndexed { index, line ->
                        UnallocatedRow(line, onClick = if (canEdit) ({ editing = line }) else null)
                        if (index < plan.unallocatedLines.lastIndex) Hairline(inset = RowTextInset)
                    }
                }
            }
        }

        item(key = "done") {
            Spacer(Modifier.height(Spacing.lg))
            if (canEdit) {
                LucentoPrimaryButton(
                    text = "Done planning ${monthLabel(month)}",
                    enabled = !finishing,
                    onClick = {
                        finishing = true
                        viewModel.markPlanned(month) { onDone(plan.total.takeIf { plan.hasBudget }) }
                    },
                )
            } else {
                com.lucento.app.feature.sharedspace.OwnerManagesNote("budgets and categories")
            }
            InfoNote(
                "Each change is saved as you make it. A change here is for ${monthLabel(month)} only, unless you choose " +
                    "“${BudgetChangeContext.monthName(month, short = true)} onward”. Other months keep their amounts, and your " +
                    "total is always the sum of your categories.",
                modifier = Modifier.padding(top = Spacing.lg),
            )
        }
    }

    editing?.let { line ->
        val change = remember(line, state.budgets, plan.currencyCode, month, current) {
            BudgetChangeContext.of(state.budgets, line.category.id, line.category.name, plan.currencyCode, month, current, planning = true)
        }
        AllocationSheet(
            line = line,
            plan = plan,
            change = change,
            isFuture = month > current,
            initialAmount = suggestion?.amount,
            note = suggestion?.note,
            addTo = if (extra) line.limit else null,
            onSave = { limit, scope, done ->
                viewModel.saveAmount(line.category.id, plan.currencyCode, month, limit, scope) { result ->
                    plannedBy(result)
                    done(result)
                }
            },
            onUseUsual = { done ->
                viewModel.useUsualAmount(line.category.id, plan.currencyCode, month) { result ->
                    plannedBy(result)
                    done(result)
                }
            },
            onDismiss = {
                editing = null
                suggestion = null
                extra = false
            },
        )
    }
    if (adding) {
        AddForMonthSheet(
            month = month,
            plan = plan,
            onPick = { line ->
                adding = false
                suggestion = null
                // A one-off on a budgeted category is extra on top of its amount, never a new total to work out.
                extra = line.isAllocated
                editing = line
            },
            onNew = {
                adding = false
                naming = true
            },
            onDismiss = { adding = false },
        )
    }
    if (naming) {
        NameDialog(
            title = "New category",
            initial = "",
            confirmLabel = "Add",
            onConfirm = { name, setError ->
                viewModel.createCategory(name) { result ->
                    when (result) {
                        is CategoryRepository.SaveResult.Saved -> {
                            naming = false
                            created = result.id
                        }
                        CategoryRepository.SaveResult.BlankName -> setError("Enter a name.")
                        CategoryRepository.SaveResult.DuplicateName -> setError("You already have a category with this name.")
                        CategoryRepository.SaveResult.NotFound -> naming = false
                        CategoryRepository.SaveResult.NotPermitted -> setError("Only the Shared Space’s owner can change this.")
                    }
                }
            },
            onDismiss = { naming = false },
        )
    }
}

/** The plan page's first words: how the month before went (or is going), then what planning a month is for. */
internal fun planIntro(month: YearMonth, previous: BudgetPlan, today: LocalDate, fmt: AmountFormat): String {
    val before = previous.month
    val head = if (!previous.hasBudget) "" else {
        val line = if (previous.remaining.isNegative) "${fmt.format(-previous.remaining)} over budget"
        else "${fmt.format(previous.spent)} of ${fmt.format(previous.total)} spent"
        if (before < YearMonth.from(today)) "${monthLabel(before)}: $line. " else "${monthLabel(before)} so far: $line. "
    }
    return head + "What’s different about ${monthLabel(month)}? A birthday, a trip or a yearly bill can have its own amount, " +
        "for ${monthLabel(month)} only."
}

/**
 * One hint on the plan page, with what it is about; a tap opens that category's amount when it has one. [handled] says
 * what the month already has for it, once the person has set it.
 */
@Composable
private fun HintRow(hint: PlanHint, categories: Map<Long, Category>, handled: String?, onClick: (() -> Unit)?) {
    val fmt = amountFormat()
    val (title, detail) = hintText(hint, { id -> id?.let { categories[it]?.name } }, fmt)
    // From 130 % text the action goes under the words, as on the other rows (critic r1, P0-1).
    val stacked = LocalDensity.current.fontScale >= 1.3f
    val action = if (handled != null) "Change" else "Set amount"
    val icon = when (hint) {
        is PlanHint.DueInMonth -> Icons.Outlined.EventRepeat
        is PlanHint.LastYear -> Icons.Outlined.History
        is PlanHint.WentOver -> Icons.AutoMirrored.Outlined.TrendingUp
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .let {
                if (onClick != null) it.clickable(role = Role.Button, onClickLabel = if (handled != null) "Change its amount" else "Set its amount", onClick = onClick)
                else it
            }
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = RowPadding, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(
            icon = icon,
            size = RowTile,
            container = MaterialTheme.colorScheme.surfaceContainerHigh,
            content = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(RowGap))
        Column(Modifier.weight(1f)) {
            // Balanced lines: "Dec 12" never breaks with the 12 alone (design review 2026-11-23).
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(lineBreak = LineBreak.Heading))
            if (handled != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text(handled, style = MaterialTheme.typography.bodySmall.copy(lineBreak = LineBreak.Heading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Text(detail, style = MaterialTheme.typography.bodySmall.copy(lineBreak = LineBreak.Heading), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (stacked && onClick != null) {
                Text(action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (!stacked && onClick != null) {
            Spacer(Modifier.width(Spacing.sm))
            Text(action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * What a hint's month already has for its category, once the person has set the month's own amount: "$235.00 set for
 * December" (a bill names its category, since the hint names the bill).
 */
internal fun hintHandledText(hint: PlanHint, line: CategoryBudgetLine, month: YearMonth, fmt: AmountFormat): String {
    val set = line.limit?.let { "${fmt.format(it)} set for ${monthLabel(month)}" } ?: "No amount for ${monthLabel(month)}"
    return if (hint is PlanHint.DueInMonth) "${line.category.name}: $set" else set
}

/** A hint's two lines, from its facts only. [name] gives a category's name. Pure, so the wording is pinned in tests. */
internal fun hintText(hint: PlanHint, name: (Long?) -> String?, fmt: AmountFormat, locale: Locale = Locale.getDefault()): Pair<String, String> =
    when (hint) {
        is PlanHint.DueInMonth -> {
            // A no-break space keeps the month and the day together on one line.
            val day = hint.date.format(DateTimeFormatter.ofPattern("MMM d", locale)).replace(' ', '\u00A0')
            "${hint.name}: ${fmt.format(hint.amount)} due $day" to listOfNotNull(frequencyLabel(hint.frequency, hint.interval), name(hint.categoryId))
                .joinToString(" · ")
        }
        is PlanHint.LastYear -> {
            val what = name(hint.categoryId) ?: "this category"
            "Last ${monthLabel(hint.month)}: ${fmt.format(hint.spent)} on $what" to
                (hint.planned?.let { "${fmt.format(it)} planned this time" } ?: "Nothing planned for it this time")
        }
        is PlanHint.WentOver -> {
            val what = name(hint.categoryId) ?: "A category"
            (if (hint.finished) "$what: ${fmt.format(hint.over)} over in ${monthLabel(hint.month)}"
            else "$what: ${fmt.format(hint.over)} over so far in ${monthLabel(hint.month)}") to "Worth a different amount this time?"
        }
    }

/** A hint's amount for the sheet it opens, and one line saying where that amount comes from. */
internal data class HintSuggestion(val amount: Money, val note: String)

/**
 * What a hint suggests for [line]'s amount in a month that has no amount of its own for it yet (critic r1, P1-3; r2, P0-2):
 * a bill due adds to [usual], the category's usual monthly amount for that month (never to an amount already raised for
 * it); last year's same month proposes what it cost then; last month's overspending proposes what was spent there. The
 * person sees the figure and its source and can change it before saving.
 */
internal fun hintSuggestion(
    hint: PlanHint,
    line: CategoryBudgetLine,
    usual: Money?,
    fmt: AmountFormat,
    locale: Locale = Locale.getDefault(),
): HintSuggestion {
    val name = line.category.name
    return when (hint) {
        is PlanHint.DueInMonth -> {
            // A no-break space keeps the month and the day together on one line.
            val due = hint.date.format(DateTimeFormatter.ofPattern("MMMM d", locale)).replace(' ', '\u00A0')
            val withUsual = usual?.let { runCatching { it + hint.amount }.getOrNull() }
            if (usual == null || withUsual == null) {
                HintSuggestion(hint.amount, "${hint.name}: ${fmt.format(hint.amount)}, due $due.")
            } else {
                HintSuggestion(withUsual, "Your usual ${fmt.format(usual)}, plus ${fmt.format(hint.amount)} for ${hint.name}, due $due.")
            }
        }
        is PlanHint.LastYear -> HintSuggestion(hint.spent, "Filled in with what $name cost last ${monthLabel(hint.month)}.")
        is PlanHint.WentOver ->
            HintSuggestion(hint.spent, "Filled in with what $name cost in ${monthLabel(hint.month)}" + if (hint.finished) "." else " so far.")
    }
}

/** "Yearly", "Every 2 years", "Every 3 months": how often a scheduled cost comes back. */
internal fun frequencyLabel(frequency: Frequency, interval: Int): String = when (frequency) {
    Frequency.YEARLY -> if (interval == 1) "Yearly" else "Every $interval years"
    Frequency.MONTHLY -> if (interval == 1) "Monthly" else "Every $interval months"
    Frequency.WEEKLY -> if (interval == 1) "Weekly" else "Every $interval weeks"
}

/**
 * "Add something for December": the category a one-off cost belongs to, chosen from the person's own (or a new one).
 * Its amount then opens set to that month only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddForMonthSheet(
    month: YearMonth,
    plan: BudgetPlan,
    onPick: (CategoryBudgetLine) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    val fmt = amountFormat()
    LucentoBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.85f).dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = Spacing.lg),
        ) {
            SheetHeader(
                title = "Add something for ${monthLabel(month)}",
                subtitle = "Pick the category it belongs to, or make a new one. Its amount is for ${monthLabel(month)} only.",
            )
            LucentoSecondaryButton(text = "New category", icon = Icons.Outlined.Add, onClick = onNew)
            Spacer(Modifier.height(Spacing.md))
            Text(
                "Your categories",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(bottom = Spacing.xs).semantics { heading() },
            )
            LucentoCard(Modifier.fillMaxWidth(), contentPadding = 0.dp) {
                val lines = plan.lines.sortedBy { it.category.name.lowercase(Locale.getDefault()) }
                lines.forEachIndexed { index, line ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable(role = Role.Button, onClickLabel = "Set its ${monthLabel(month)} amount") { onPick(line) }
                            .padding(horizontal = RowPadding, vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CategoryTile(category = line.category, type = TransactionType.EXPENSE, size = RowTile)
                        Spacer(Modifier.width(RowGap))
                        // The amount under the name, so a long name is never cut short at a large text size.
                        Column(Modifier.weight(1f)) {
                            Text(line.category.name, style = Hierarchy.cardTitle)
                            Text(
                                line.limit?.let { "${fmt.format(it)} planned" } ?: "No amount set",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (index < lines.lastIndex) Hairline(inset = RowTextInset)
                }
            }
        }
    }
}
