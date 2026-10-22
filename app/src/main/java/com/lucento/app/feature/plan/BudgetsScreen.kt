package com.lucento.app.feature.plan

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
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import com.lucento.app.core.designsystem.component.ConfirmDialog
import com.lucento.app.core.designsystem.component.EmptyState
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoBottomSheet
import com.lucento.app.core.designsystem.component.LucentoCard
import com.lucento.app.core.designsystem.component.LucentoMascot
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.LucentoTopBar
import com.lucento.app.core.designsystem.component.MascotMood
import com.lucento.app.core.designsystem.component.MoneyText
import com.lucento.app.core.designsystem.component.ProgressRing
import com.lucento.app.core.designsystem.component.SectionHeader
import com.lucento.app.core.designsystem.component.ShareBar
import com.lucento.app.core.designsystem.component.StatusPill
import com.lucento.app.core.designsystem.component.StatusTones
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.OverlineStyle
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.data.repository.BudgetRepository
import com.lucento.app.domain.Budget
import com.lucento.app.domain.BudgetCalculator
import com.lucento.app.domain.BudgetProgress
import com.lucento.app.domain.BudgetStatus
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.TransactionType
import com.lucento.app.feature.home.monthLabel
import kotlinx.coroutines.launch
import java.time.YearMonth

private data class BudgetDraft(
    val id: Long? = null,
    val categoryId: Long? = null,
    val currencyCode: String,
    val amountText: String = "",
)

/** Monthly budgets: an overall limit and per-category limits, with real spending against each. */
@Composable
fun BudgetsScreen(onBack: () -> Unit, viewModel: PlanViewModel = viewModel(factory = PlanViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var monthOffset by rememberSaveable { mutableIntStateOf(0) }
    val month = YearMonth.from(state.today).minusMonths(monthOffset.toLong())
    var editing by remember { mutableStateOf<BudgetDraft?>(null) }
    val progress = remember(state.budgets, state.transactions, month) {
        BudgetCalculator.progressAll(state.budgets, state.transactions, month)
    }
    val overall = progress.filter { it.budget.isOverall }
    val byCategory = progress.filterNot { it.budget.isOverall }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.fabClearance),
        ) {
            item(key = "bar") { LucentoTopBar(title = "Budgets", onBack = onBack) }
            item(key = "month") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { monthOffset += 1 }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
                    }
                    Text(
                        "${monthLabel(month)} ${month.year}",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { if (monthOffset > 0) monthOffset -= 1 }, enabled = monthOffset > 0) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
                    }
                }
            }

            if (!state.loading && state.budgets.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = "No budgets yet",
                        body = "Set a monthly limit for everything you spend, or for categories like dining or transport. " +
                            "Home then shows what’s safe to spend.",
                        illustration = { LucentoMascot(size = 96.dp, mood = MascotMood.Curious) },
                        action = {
                            LucentoPrimaryButton(
                                text = "Create a monthly budget",
                                onClick = { editing = BudgetDraft(currencyCode = state.primaryCurrency) },
                            )
                        },
                    )
                }
            }

            items(overall, key = { "overall-${it.budget.id}" }) { p ->
                OverallBudgetCard(p, onClick = { editing = p.budget.toDraft() })
                Spacer(Modifier.height(Spacing.md))
            }

            if (byCategory.isNotEmpty()) {
                item(key = "cat-header") { SectionHeader("Categories") }
                items(byCategory, key = { "cat-${it.budget.id}" }) { p ->
                    val category = p.budget.categoryId?.let { state.categoriesById[it] }
                    CategoryBudgetRow(p, name = category?.name ?: "Category", tile = {
                        CategoryTile(category = category, type = TransactionType.EXPENSE)
                    }, onClick = { editing = p.budget.toDraft() })
                }
            }

            if (state.budgets.isNotEmpty()) {
                item(key = "rules") {
                    InfoNote(
                        "Budgets reset on the 1st of each month — unused amounts don’t roll over. Only expenses count; " +
                            "transfers between your accounts and refunds recorded as income don’t reduce spending.",
                        modifier = Modifier.padding(top = Spacing.lg),
                    )
                }
            }
        }

        if (state.budgets.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = { editing = BudgetDraft(currencyCode = state.primaryCurrency) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add budget") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.gutter),
            )
        }
    }

    editing?.let { draft ->
        BudgetEditorSheet(
            initial = draft,
            state = state,
            onSave = { categoryId, money, done ->
                viewModel.saveBudget(draft.id, categoryId, money) { result -> done(result) }
            },
            onDelete = draft.id?.let { id -> { viewModel.deleteBudget(id) } },
            onDismiss = { editing = null },
        )
    }
}

private fun Budget.toDraft() = BudgetDraft(
    id = id,
    categoryId = categoryId,
    currencyCode = currencyCode,
    amountText = MoneyFormatter.formatPlain(limit),
)

@Composable
private fun OverallBudgetCard(p: BudgetProgress, onClick: () -> Unit) {
    val colors = LucentoTheme.extendedColors
    val percent = p.percentUsed ?: 0
    LucentoCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = Spacing.xl) {
        Text("OVERALL · ${p.budget.currencyCode}", style = OverlineStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                MoneyText(money = p.spent, style = AmountStyles.hero, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "spent of ${MoneyFormatter.format(p.budget.limit)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    if (p.remaining.isNegative) "Over by ${MoneyFormatter.format(-p.remaining)}" else "${MoneyFormatter.format(p.remaining)} left",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (p.remaining.isNegative) colors.negative else MaterialTheme.colorScheme.onSurface,
                )
            }
            ProgressRing(
                fraction = p.fraction,
                description = "$percent% of the monthly budget used",
                size = 84.dp,
                color = statusColor(p.status),
            ) {
                Text("$percent%", style = AmountStyles.medium)
            }
        }
    }
}

@Composable
private fun statusColor(status: BudgetStatus) = when (status) {
    BudgetStatus.ON_TRACK -> LucentoTheme.extendedColors.success
    BudgetStatus.NEAR_LIMIT -> LucentoTheme.extendedColors.warning
    BudgetStatus.OVER_LIMIT -> LucentoTheme.extendedColors.negative
}

@Composable
private fun CategoryBudgetRow(p: BudgetProgress, name: String, tile: @Composable () -> Unit, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "Edit budget", onClick = onClick)
            .padding(vertical = Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            tile()
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${MoneyFormatter.format(p.spent)} of ${MoneyFormatter.format(p.budget.limit)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (p.status) {
                BudgetStatus.OVER_LIMIT -> StatusPill("Over", StatusTones.negative)
                BudgetStatus.NEAR_LIMIT -> StatusPill("${p.percentUsed ?: 0}%", StatusTones.warning)
                BudgetStatus.ON_TRACK -> Text("${p.percentUsed ?: 0}%", style = AmountStyles.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        ShareBar(fraction = p.fraction, color = statusColor(p.status), modifier = Modifier.padding(start = 52.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetEditorSheet(
    initial: BudgetDraft,
    state: PlanUiState,
    onSave: (Long?, com.lucento.app.core.money.Money, (BudgetRepository.SaveResult) -> Unit) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val separator = remember { MoneyFormatter.decimalSeparator() }
    var draft by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmingDelete by remember { mutableStateOf(false) }
    val expenseCategories = state.categories.filter { it.kind == CategoryKind.EXPENSE && (!it.archived || it.id == draft.categoryId) }

    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    LucentoBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(
                    if (initial.id == null) "New monthly budget" else "Edit budget",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                if (onDelete != null) {
                    IconButton(onClick = { confirmingDelete = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete budget", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            SectionHeader("Covers")
            PillRow(
                options = listOf<Pair<Long?, String>>(null to "Everything") + expenseCategories.map { it.id to it.name },
                selected = draft.categoryId,
                onSelect = { draft = draft.copy(categoryId = it); error = null },
            )
            if (state.currencies.size > 1 && initial.id == null) {
                SectionHeader("Currency")
                PillRow(state.currencies.map { it to it }, draft.currencyCode, onSelect = { draft = draft.copy(currencyCode = it); error = null })
            }
            Spacer(Modifier.height(Spacing.md))
            MoneyField(
                value = draft.amountText,
                onValueChange = { draft = draft.copy(amountText = it); error = null },
                label = "Monthly limit",
                currencyCode = draft.currencyCode,
                error = error,
                supporting = "Resets on the 1st of every month.",
            )
            Spacer(Modifier.height(Spacing.xl))
            LucentoPrimaryButton(
                text = if (initial.id == null) "Create budget" else "Save changes",
                onClick = {
                    val (money, amountError) = parsePositive(draft.amountText, draft.currencyCode, separator)
                    if (money == null) {
                        error = amountError?.message(draft.currencyCode, separator)
                        return@LucentoPrimaryButton
                    }
                    onSave(draft.categoryId, money) { result ->
                        when (result) {
                            is BudgetRepository.SaveResult.Saved -> close()
                            BudgetRepository.SaveResult.Duplicate ->
                                error = if (draft.categoryId == null) "You already have an overall ${draft.currencyCode} budget." else "This category already has a ${draft.currencyCode} budget."
                            BudgetRepository.SaveResult.LimitNotPositive -> error = "The limit must be greater than zero."
                            BudgetRepository.SaveResult.InvalidCategory -> error = "Budgets can only cover expense categories."
                            BudgetRepository.SaveResult.NotFound -> close()
                        }
                    }
                },
            )
            Spacer(Modifier.height(Spacing.lg))
        }
    }

    if (confirmingDelete && onDelete != null) {
        ConfirmDialog(
            title = "Delete this budget?",
            text = "Only the limit is removed. Your transactions stay exactly as they are.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                confirmingDelete = false
                onDelete()
                close()
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}
