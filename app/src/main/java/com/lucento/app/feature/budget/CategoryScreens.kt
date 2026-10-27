package com.lucento.app.feature.budget

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.core.designsystem.component.CategoryTile
import com.lucento.app.core.designsystem.component.ConfirmDialog
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.LucentoSecondaryButton
import com.lucento.app.core.designsystem.component.LucentoTopBar
import com.lucento.app.core.designsystem.component.SectionHeader
import com.lucento.app.core.designsystem.theme.Sizes
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.data.repository.CategoryRepository
import com.lucento.app.domain.Category
import com.lucento.app.domain.CategoryPresets
import com.lucento.app.domain.CategorySelection
import com.lucento.app.domain.TransactionType

/**
 * Manage spending categories: rename, reorder, add, deactivate (archive) and restore. Ids never change, so history,
 * amounts and schedules stay attached. A category with history is archived, never deleted; one nothing uses is removed.
 */
@Composable
fun ManageCategoriesScreen(
    onBack: () -> Unit,
    onChooseFromPresets: () -> Unit,
    viewModel: BudgetViewModel = viewModel(factory = BudgetViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf<Category?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var deactivating by remember { mutableStateOf<Category?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val active = state.activeExpense
    val archived = state.archivedExpense

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.xxl),
    ) {
        item(key = "bar") { LucentoTopBar(title = "Categories", subtitle = "Spending categories your budget is built from", onBack = onBack) }
        item(key = "actions") {
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.sm)) {
                LucentoSecondaryButton(text = "Add", onClick = { adding = true }, modifier = Modifier.weight(1f))
                LucentoSecondaryButton(text = "From presets", onClick = onChooseFromPresets, modifier = Modifier.weight(1f))
            }
            message?.let { InfoNote(it, modifier = Modifier.padding(top = Spacing.md)) }
        }
        item(key = "active-header") { SectionHeader("Active · ${active.size}", Modifier.padding(top = Spacing.lg)) }
        if (active.isEmpty()) {
            item(key = "none") {
                Text(
                    "No active categories. Add one, choose from presets, or restore an archived category.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        itemsIndexed(active, key = { _, c -> "c-${c.id}" }) { index, category ->
            CategoryManageRow(
                category = category,
                canMoveUp = index > 0,
                canMoveDown = index < active.lastIndex,
                onUp = { viewModel.move(category.id, -1) },
                onDown = { viewModel.move(category.id, +1) },
                onRename = { renaming = category },
                onDeactivate = { deactivating = category },
            )
            Hairline(inset = Sizes.iconTile + Spacing.md)
        }
        if (archived.isNotEmpty()) {
            item(key = "archived-header") {
                SectionHeader("Archived · ${archived.size}", Modifier.padding(top = Spacing.lg))
                Text(
                    "Archived categories keep labelling past transactions but aren’t offered for new ones and don’t count in the budget.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.sm),
                )
            }
            itemsIndexed(archived, key = { _, c -> "x-${c.id}" }) { _, category ->
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    CategoryTile(category = category, type = TransactionType.EXPENSE)
                    Spacer(Modifier.width(Spacing.md))
                    Text(category.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = {
                        viewModel.restore(category.id) { result ->
                            message = when (result) {
                                is CategoryRepository.SaveResult.Saved -> "${category.name} is active again."
                                CategoryRepository.SaveResult.DuplicateName -> "An active category is already called ${category.name}. Rename one of them first."
                                else -> null
                            }
                        }
                    }) {
                        Icon(Icons.Outlined.Unarchive, contentDescription = "Restore ${category.name}")
                    }
                }
            }
        }
        item(key = "rules") {
            InfoNote(
                "Income categories aren’t part of your budget and are managed when you record income.",
                modifier = Modifier.padding(top = Spacing.lg),
            )
        }
    }

    renaming?.let { category ->
        NameDialog(
            title = "Rename category",
            initial = category.name,
            confirmLabel = "Rename",
            onConfirm = { name, setError ->
                viewModel.rename(category.id, name) { result ->
                    when (result) {
                        is CategoryRepository.SaveResult.Saved -> renaming = null
                        CategoryRepository.SaveResult.BlankName -> setError("Enter a name.")
                        CategoryRepository.SaveResult.DuplicateName -> setError("You already have a category with this name.")
                        CategoryRepository.SaveResult.NotFound -> renaming = null
                    }
                }
            },
            onDismiss = { renaming = null },
        )
    }

    if (adding) {
        NameDialog(
            title = "New category",
            initial = "",
            confirmLabel = "Add",
            onConfirm = { name, setError ->
                viewModel.createCategory(name) { result ->
                    when (result) {
                        is CategoryRepository.SaveResult.Saved -> adding = false
                        CategoryRepository.SaveResult.BlankName -> setError("Enter a name.")
                        CategoryRepository.SaveResult.DuplicateName -> setError("You already have a category with this name.")
                        CategoryRepository.SaveResult.NotFound -> adding = false
                    }
                }
            },
            onDismiss = { adding = false },
        )
    }

    deactivating?.let { category ->
        ConfirmDialog(
            title = "Deactivate ${category.name}?",
            text = "If it has transactions, an amount or a recurring payment, it’s archived: history keeps its label and you " +
                "can restore it any time. If nothing uses it, it’s removed.",
            confirmLabel = "Deactivate",
            destructive = true,
            onConfirm = {
                deactivating = null
                viewModel.deactivate(category.id) { result ->
                    message = when (result) {
                        is CategoryRepository.DeleteResult.Archived -> "${category.name} was archived — its history is kept."
                        CategoryRepository.DeleteResult.Deleted -> "${category.name} was removed."
                        CategoryRepository.DeleteResult.NotFound -> null
                    }
                }
            },
            onDismiss = { deactivating = null },
        )
    }
}

@Composable
private fun CategoryManageRow(
    category: Category,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRename: () -> Unit,
    onDeactivate: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        CategoryTile(category = category, type = TransactionType.EXPENSE)
        Spacer(Modifier.width(Spacing.md))
        Text(category.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        IconButton(onClick = onUp, enabled = canMoveUp) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move ${category.name} up") }
        IconButton(onClick = onDown, enabled = canMoveDown) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move ${category.name} down") }
        IconButton(onClick = onRename) { Icon(Icons.Outlined.Edit, contentDescription = "Rename ${category.name}") }
        IconButton(onClick = onDeactivate) { Icon(Icons.Outlined.Archive, contentDescription = "Deactivate ${category.name}") }
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String, (String) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it.take(CategoryRepository.MAX_NAME)
                    error = null
                },
                singleLine = true,
                isError = error != null,
                supportingText = if (error != null) {
                    { Text(error.orEmpty()) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                shape = MaterialTheme.shapes.small,
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) { error = it } }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Choose categories from the preset library after onboarding — for existing users personalising their budget and for
 * anyone finishing an incomplete setup. Current categories start ticked (by matching preset, or as themselves), so
 * confirming without changes changes nothing. Two steps, like onboarding: choose, then review and confirm.
 */
@Composable
fun CategorySetupScreen(
    onDone: () -> Unit,
    viewModel: BudgetViewModel = viewModel(factory = BudgetViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var reviewing by rememberSaveable { mutableStateOf(false) }
    var picks by remember { mutableStateOf<CategoryPicks?>(null) }
    var initialised by rememberSaveable { mutableStateOf(false) }
    var savedKeys by rememberSaveable { mutableStateOf(listOf<String>()) }
    var savedCustom by rememberSaveable { mutableStateOf(listOf<String>()) }
    var savedExisting by rememberSaveable { mutableStateOf(listOf<Long>()) }
    val existing = state.expenseCategories
    val (_, unmatched) = remember(existing) { CategorySelection.initialSelection(existing) }

    LaunchedEffect(state.loading) {
        if (state.loading || picks != null) return@LaunchedEffect
        picks = if (initialised) {
            CategoryPicks(savedKeys.toSet(), savedCustom, savedExisting.toSet())
        } else {
            initialised = true
            val (keys, own) = CategorySelection.initialSelection(existing)
            // Someone with no categories at all starts from the recommended essentials.
            if (keys.isEmpty() && own.isEmpty()) CategoryPicks(presetKeys = CategoryPresets.recommendedKeys)
            else CategoryPicks(presetKeys = keys, existingIds = own.map { it.id }.toSet())
        }
    }
    fun update(value: CategoryPicks) {
        picks = value
        savedKeys = value.presetKeys.toList()
        savedCustom = value.customNames
        savedExisting = value.existingIds.toList()
    }

    BackHandler(enabled = reviewing) { reviewing = false }
    val current = picks
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.gutter)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        LucentoTopBar(
            title = if (reviewing) "Review categories" else "Choose categories",
            subtitle = "Your budget is built from these",
            onBack = {
                if (reviewing) {
                    reviewing = false
                } else {
                    onDone()
                }
            },
        )
        if (current == null) return@Column
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (reviewing) {
                val chosen = current.choices(existing)
                val plan = remember(existing, chosen) { CategorySelection.plan(existing, chosen) }
                CategoryReview(
                    picks = current,
                    onChange = ::update,
                    existingCustom = unmatched + existing.filter { it.archived && it.id in current.existingIds },
                    deactivating = plan.deactivate.mapNotNull { id -> existing.firstOrNull { it.id == id }?.name },
                    onEdit = { reviewing = false },
                )
            } else {
                Text(
                    "Tick what you spend on. Your current categories are already ticked — nothing changes unless you change it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.md),
                )
                CategoryPresetPicker(picks = current, onChange = ::update, existingCustom = unmatched)
            }
            Spacer(Modifier.height(Spacing.lg))
        }
        Spacer(Modifier.height(Spacing.sm))
        LucentoPrimaryButton(
            text = when {
                current.count == 0 -> "Choose at least one category"
                reviewing -> "Confirm ${current.count} ${if (current.count == 1) "category" else "categories"}"
                else -> "Review ${current.count} ${if (current.count == 1) "category" else "categories"}"
            },
            enabled = current.count > 0,
            onClick = {
                if (!reviewing) {
                    reviewing = true
                } else {
                    viewModel.applySelection(current.choices(existing)) { onDone() }
                }
            },
        )
        Spacer(Modifier.height(Spacing.md))
    }
}
