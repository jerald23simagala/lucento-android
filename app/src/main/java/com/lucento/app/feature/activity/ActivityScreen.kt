package com.lucento.app.feature.activity

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.core.designsystem.component.AmountText
import com.lucento.app.core.designsystem.component.CategoryIcons
import com.lucento.app.core.designsystem.component.EmptyState
import com.lucento.app.core.designsystem.component.IconBadge
import com.lucento.app.core.designsystem.component.LucentoMark
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.LucentoSecondaryButton
import com.lucento.app.core.designsystem.component.SectionHeader
import com.lucento.app.core.designsystem.component.icon
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.domain.Category
import com.lucento.app.domain.RangePreset
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ActivityScreen(
    onAddWallet: () -> Unit,
    viewModel: ActivityViewModel = viewModel(factory = ActivityViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<TransactionDraft?>(null) }
    var query by rememberSaveable { mutableStateOf("") }

    fun newDraft() = TransactionDraft(walletId = state.wallets.firstOrNull()?.id)

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = 96.dp),
        ) {
            item {
                Text(
                    "Activity",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.md),
                )
            }

            when {
                state.loading -> Unit

                state.wallets.isEmpty() -> item {
                    EmptyState(
                        title = "Add a wallet first",
                        body = "Every transaction belongs to a wallet — cash, a bank account, a card. " +
                            "It only takes a moment, and stays on this device.",
                        illustration = { LucentoMark(size = 72.dp, animate = false) },
                        action = { LucentoPrimaryButton(text = "Add wallet", onClick = onAddWallet) },
                    )
                }

                state.totalCount == 0 -> item {
                    EmptyState(
                        title = "No transactions yet",
                        body = "Record what comes in and goes out. Your wallet balances update as you go.",
                        illustration = { LucentoMark(size = 72.dp, animate = false) },
                        action = { LucentoPrimaryButton(text = "Add transaction", onClick = { editing = newDraft() }) },
                    )
                }

                else -> {
                    item {
                        OutlinedTextField(
                            value = query,
                            onValueChange = {
                                query = it.take(60)
                                viewModel.setQuery(query)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search notes, categories, amounts") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            trailingIcon = if (query.isNotEmpty()) {
                                {
                                    IconButton(onClick = {
                                        query = ""
                                        viewModel.setQuery("")
                                    }) { Icon(Icons.Filled.Close, contentDescription = "Clear search") }
                                }
                            } else {
                                null
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            shape = MaterialTheme.shapes.medium,
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        FilterRow(
                            state = state,
                            onClear = {
                                query = ""
                                viewModel.clearFilters()
                            },
                            viewModel = viewModel,
                        )
                    }

                    if (state.groups.isEmpty()) {
                        item {
                            EmptyState(
                                title = "Nothing matches",
                                body = "No transactions fit these filters.",
                                action = {
                                    LucentoSecondaryButton(text = "Clear filters", onClick = {
                                        query = ""
                                        viewModel.clearFilters()
                                    })
                                },
                            )
                        }
                    }

                    state.groups.forEach { group ->
                        item(key = "day-${group.date.toEpochDay()}") {
                            SectionHeader(dayLabel(group.date), Modifier.padding(top = Spacing.md))
                        }
                        items(group.items, key = { "tx-${it.id}" }) { transaction ->
                            TransactionRow(
                                transaction = transaction,
                                category = transaction.categoryId?.let { state.categoriesById[it] },
                                wallet = state.walletsById[transaction.walletId],
                                onClick = { editing = transaction.toDraft() },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }

        if (!state.loading && state.wallets.isNotEmpty() && state.totalCount > 0) {
            ExtendedFloatingActionButton(
                onClick = { editing = newDraft() },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add transaction") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(Spacing.gutter),
            )
        }
    }

    editing?.let { draft ->
        TransactionEditorSheet(
            initial = draft,
            wallets = state.wallets,
            categories = state.categories,
            onSave = viewModel::save,
            onDelete = viewModel::delete,
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun FilterRow(state: ActivityUiState, onClear: () -> Unit, viewModel: ActivityViewModel) {
    val f = state.filter
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MenuChip(
            label = if (f.preset == RangePreset.ALL) "Any date" else rangeLabel(f.preset),
            active = f.preset != RangePreset.ALL,
            options = RangePreset.entries.map { rangeLabel(it) to { viewModel.setPreset(it) } },
        )
        Spacer(Modifier.width(Spacing.sm))
        MenuChip(
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
        Spacer(Modifier.width(Spacing.sm))
        MenuChip(
            label = f.walletId?.let { state.walletsById[it]?.name } ?: "All wallets",
            active = f.walletId != null,
            options = listOf<Pair<String, () -> Unit>>("All wallets" to { viewModel.setWallet(null) }) +
                state.wallets.map { w -> w.name to { viewModel.setWallet(w.id) } },
        )
        Spacer(Modifier.width(Spacing.sm))
        MenuChip(
            label = f.categoryId?.let { state.categoriesById[it]?.name } ?: "All categories",
            active = f.categoryId != null,
            options = listOf<Pair<String, () -> Unit>>("All categories" to { viewModel.setCategory(null) }) +
                state.categories.filter { !it.archived }.map { c -> c.name to { viewModel.setCategory(c.id) } },
        )
        if (f.isActive) {
            Spacer(Modifier.width(Spacing.sm))
            androidx.compose.material3.TextButton(onClick = onClear) { Text("Clear") }
        }
    }
    if (f.isActive) {
        Text(
            "${state.matchCount} of ${state.totalCount} transactions",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

@Composable
private fun MenuChip(label: String, active: Boolean, options: List<Pair<String, () -> Unit>>) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = active,
            onClick = { expanded = true },
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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

@Composable
private fun TransactionRow(
    transaction: Transaction,
    category: Category?,
    wallet: Wallet?,
    onClick: () -> Unit,
) {
    val isIncome = transaction.type == TransactionType.INCOME
    val title = transaction.note.ifBlank { category?.name ?: "Uncategorized" }
    val subtitle = listOfNotNull(category?.name?.takeIf { transaction.note.isNotBlank() }, wallet?.name)
        .joinToString(" · ")

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(category?.icon ?: CategoryIcons.Uncategorized)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(Spacing.sm))
        // Sign carries the meaning (−/+); colour only reinforces it for income.
        AmountText(
            money = transaction.signedAmount,
            color = if (isIncome) LucentoTheme.extendedColors.positive else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun rangeLabel(preset: RangePreset): String = when (preset) {
    RangePreset.ALL -> "Any date"
    RangePreset.THIS_MONTH -> "This month"
    RangePreset.LAST_MONTH -> "Last month"
    RangePreset.LAST_30_DAYS -> "Last 30 days"
}

private fun dayLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
    }
}
