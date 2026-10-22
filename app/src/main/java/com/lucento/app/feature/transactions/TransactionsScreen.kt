package com.lucento.app.feature.transactions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.core.designsystem.component.AmountText
import com.lucento.app.core.designsystem.component.MoneyText
import com.lucento.app.core.designsystem.component.ChoicePill
import com.lucento.app.core.designsystem.component.EmptyState
import com.lucento.app.core.designsystem.component.Figure
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.LucentoMark
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.LucentoSecondaryButton
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.component.LucentoTopBar
import com.lucento.app.core.designsystem.component.SearchField
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Sizes
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.domain.PeriodTotals
import com.lucento.app.domain.RangePreset
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionsScreen(
    onAddTransaction: () -> Unit,
    onEditTransaction: (Transaction) -> Unit,
    onAddWallet: () -> Unit,
    viewModel: TransactionsViewModel = viewModel(factory = TransactionsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val hasHistory = !state.loading && state.wallets.isNotEmpty() && state.totalCount > 0
    // Local text state keeps typing responsive; the ViewModel receives every change.
    var query by rememberSaveable { mutableStateOf(state.filter.query) }
    val clearFilters = {
        query = ""
        viewModel.clearFilters()
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.fabClearance),
        ) {
            item(key = "title") { LucentoTopBar(title = "Transactions") }

            when {
                state.loading -> Unit

                state.wallets.isEmpty() -> item(key = "no-wallets") {
                    EmptyState(
                        title = "Add a wallet first",
                        body = "Every transaction belongs to a wallet — cash, a bank account, a card. " +
                            "It only takes a moment, and stays on this device.",
                        illustration = { LucentoMark(size = 72.dp, animate = false) },
                        action = { LucentoPrimaryButton(text = "Add wallet", onClick = onAddWallet) },
                    )
                }

                state.totalCount == 0 -> item(key = "no-transactions") {
                    EmptyState(
                        title = "No transactions yet",
                        body = "Record what comes in and goes out. Wallet balances and your monthly picture update as you go.",
                        illustration = { LucentoMark(size = 72.dp, animate = false) },
                        action = { LucentoPrimaryButton(text = "Add transaction", onClick = onAddTransaction) },
                    )
                }

                else -> {
                    item(key = "controls") {
                        Column {
                            SearchField(
                                value = query,
                                onValueChange = {
                                    query = it.take(60)
                                    viewModel.setQuery(query)
                                },
                                placeholder = "Search notes, categories, amounts",
                            )
                            Spacer(Modifier.height(Spacing.sm))
                            FilterRow(state = state, viewModel = viewModel, onClear = clearFilters)
                            Spacer(Modifier.height(Spacing.md))
                            ShownSummary(state)
                        }
                    }

                    if (state.sections.isEmpty()) {
                        item(key = "no-matches") {
                            EmptyState(
                                title = "Nothing matches",
                                body = "No transactions fit this search and these filters.",
                                action = {
                                    LucentoSecondaryButton(text = "Clear filters", onClick = clearFilters)
                                },
                            )
                        }
                    }

                    state.sections.forEach { section ->
                        stickyHeader(key = "day-${section.group.date.toEpochDay()}") {
                            DayHeader(section)
                        }
                        itemsIndexed(section.group.items, key = { _, t -> "tx-${t.id}" }) { index, transaction ->
                            Column(Modifier.animateItem()) {
                                TransactionRow(
                                    transaction = transaction,
                                    category = transaction.categoryId?.let { state.categoriesById[it] },
                                    wallet = state.walletsById[transaction.walletId],
                                    onClick = { onEditTransaction(transaction) },
                                )
                                if (index < section.group.items.lastIndex) Hairline(inset = Sizes.iconTile + Spacing.md)
                            }
                        }
                    }
                }
            }
        }

        if (hasHistory) {
            ExtendedFloatingActionButton(
                onClick = onAddTransaction,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(Spacing.gutter),
            )
        }
    }
}

@Composable
private fun DayHeader(section: DaySection) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .heightIn(min = 40.dp)
            .padding(top = Spacing.md, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            dayLabel(section.group.date),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f).semantics { heading() },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        section.net.forEachIndexed { index, money ->
            if (index > 0) Spacer(Modifier.width(Spacing.sm))
            AmountText(
                money = money,
                style = AmountStyles.small,
                showPlus = true,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** In / out for whatever is currently shown, so filters answer "how much did I spend on X?". */
@Composable
private fun ShownSummary(state: TransactionsUiState) {
    val primary: PeriodTotals = state.shownTotals.firstOrNull() ?: return
    val others = state.shownTotals.drop(1)
    val scope = when {
        state.filter.isActive -> "${state.matchCount} of ${state.totalCount} transactions"
        else -> "${state.totalCount} ${if (state.totalCount == 1) "transaction" else "transactions"}"
    }
    Column(Modifier.fillMaxWidth().padding(bottom = Spacing.xs)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
            Figure("In", Modifier.weight(1f)) {
                MoneyText(
                    money = primary.income,
                    style = AmountStyles.medium,
                    showPlus = true,
                    color = LucentoTheme.extendedColors.positive,
                    fractionScale = 1f,
                )
            }
            Figure("Out", Modifier.weight(1f)) {
                MoneyText(money = -primary.expense, style = AmountStyles.medium, fractionScale = 1f)
            }
            Figure("Net", Modifier.weight(1f)) {
                MoneyText(money = primary.net, style = AmountStyles.medium, showPlus = true, fractionScale = 1f)
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            buildString {
                append(scope)
                if (others.isNotEmpty()) append(" · also in ").append(others.joinToString(", ") { it.currencyCode })
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FilterRow(state: TransactionsUiState, viewModel: TransactionsViewModel, onClear: () -> Unit) {
    val f = state.filter
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        MenuPill(
            label = rangeLabel(f.preset),
            active = f.preset != RangePreset.ALL,
            options = RangePreset.entries.map { preset -> rangeLabel(preset) to { viewModel.setPreset(preset) } },
        )
        MenuPill(
            label = when (f.type) {
                null -> "All types"
                TransactionType.EXPENSE -> "Expenses"
                TransactionType.INCOME -> "Income"
            },
            active = f.type != null,
            options = listOf<Pair<String, () -> Unit>>(
                "All types" to { viewModel.setType(null) },
                "Expenses" to { viewModel.setType(TransactionType.EXPENSE) },
                "Income" to { viewModel.setType(TransactionType.INCOME) },
            ),
        )
        MenuPill(
            label = f.walletId?.let { state.walletsById[it]?.name } ?: "All wallets",
            active = f.walletId != null,
            options = listOf<Pair<String, () -> Unit>>("All wallets" to { viewModel.setWallet(null) }) +
                state.wallets.map { w -> w.name to { viewModel.setWallet(w.id) } },
        )
        MenuPill(
            label = f.categoryId?.let { state.categoriesById[it]?.name } ?: "All categories",
            active = f.categoryId != null,
            options = listOf<Pair<String, () -> Unit>>("All categories" to { viewModel.setCategory(null) }) +
                state.categories.filter { !it.archived }.map { c -> c.name to { viewModel.setCategory(c.id) } },
        )
        if (f.isActive) {
            LucentoTextButton(text = "Clear", onClick = onClear)
        }
    }
}

@Composable
private fun MenuPill(label: String, active: Boolean, options: List<Pair<String, () -> Unit>>) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ChoicePill(
            label = label,
            selected = active,
            trailingIcon = Icons.Filled.ArrowDropDown,
            role = Role.DropdownList,
            onClick = { expanded = true },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (text, action) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        expanded = false
                        action()
                    },
                )
            }
        }
    }
}

private fun rangeLabel(preset: RangePreset): String = when (preset) {
    RangePreset.ALL -> "Any date"
    RangePreset.THIS_MONTH -> "This month"
    RangePreset.LAST_MONTH -> "Last month"
    RangePreset.LAST_30_DAYS -> "Last 30 days"
}
