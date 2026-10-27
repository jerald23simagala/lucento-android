package com.lucento.app.feature.budget

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.component.CategoryIcons
import com.lucento.app.core.designsystem.component.ChoicePill
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.component.rememberHaptics
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.domain.Category
import com.lucento.app.domain.CategoryPresets
import com.lucento.app.domain.CategorySelection

/**
 * What the user has ticked: preset keys, custom names typed in, and (for someone who already had categories)
 * existing categories that match no preset, by id.
 */
data class CategoryPicks(
    val presetKeys: Set<String> = emptySet(),
    val customNames: List<String> = emptyList(),
    val existingIds: Set<Long> = emptySet(),
) {
    val count: Int get() = presetKeys.size + customNames.size + existingIds.size

    fun choices(existing: List<Category>): List<CategorySelection.Choice> =
        existingIds.mapNotNull { id -> existing.firstOrNull { it.id == id } }
            .map { CategorySelection.Choice.Existing(it.id, it.name, it.iconKey) } +
            CategoryPresets.all.filter { it.key in presetKeys }.map { CategorySelection.Choice.FromPreset(it) } +
            customNames.map { CategorySelection.Choice.Custom(it) }
}

/**
 * The grouped preset library with search, a custom-category field and (when [existingCustom] is not empty) the
 * user's own categories that match no preset. Groups holding a selection or a recommendation start expanded.
 * Category selection only — amounts are allocated later on the Budget screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPresetPicker(
    picks: CategoryPicks,
    onChange: (CategoryPicks) -> Unit,
    modifier: Modifier = Modifier,
    existingCustom: List<Category> = emptyList(),
) {
    val haptics = rememberHaptics()
    var query by rememberSaveable { mutableStateOf("") }
    var custom by rememberSaveable { mutableStateOf("") }
    var customError by rememberSaveable { mutableStateOf<String?>(null) }
    val normalizedQuery = CategoryPresets.normalize(query)

    fun togglePreset(key: String) {
        haptics.tick()
        onChange(picks.copy(presetKeys = if (key in picks.presetKeys) picks.presetKeys - key else picks.presetKeys + key))
    }

    fun addCustom() {
        val name = CategorySelection.cleanCustomName(custom)
        if (name == null) {
            customError = "Type a name first."
            return
        }
        val norm = CategoryPresets.normalize(name)
        val preset = CategoryPresets.all.firstOrNull { CategoryPresets.normalize(it.name) == norm }
        when {
            preset != null -> onChange(picks.copy(presetKeys = picks.presetKeys + preset.key)) // reuse the preset, no twin
            picks.customNames.any { CategoryPresets.normalize(it) == norm } ||
                existingCustom.any { CategoryPresets.normalize(it.name) == norm && it.id in picks.existingIds } -> {
                customError = "“$name” is already in your list."
                return
            }
            else -> {
                val existing = existingCustom.firstOrNull { CategoryPresets.normalize(it.name) == norm }
                onChange(
                    if (existing != null) picks.copy(existingIds = picks.existingIds + existing.id)
                    else picks.copy(customNames = picks.customNames + name)
                )
            }
        }
        haptics.confirm()
        custom = ""
        customError = null
    }

    Column(modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(40) },
            placeholder = { Text("Search categories") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = if (query.isNotEmpty()) {
                { IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "Clear search") } }
            } else {
                null
            },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.sm))

        if (existingCustom.isNotEmpty() && normalizedQuery.isEmpty()) {
            PickerGroup(
                title = "Your categories",
                selectedCount = existingCustom.count { it.id in picks.existingIds },
                initiallyExpanded = true,
            ) {
                existingCustom.forEach { c ->
                    val selected = c.id in picks.existingIds
                    ChoicePill(
                        label = c.name,
                        selected = selected,
                        leadingIcon = if (selected) Icons.Filled.Check else CategoryIcons.forKey(c.iconKey),
                        role = Role.Checkbox,
                        onClick = {
                            haptics.tick()
                            onChange(picks.copy(existingIds = if (selected) picks.existingIds - c.id else picks.existingIds + c.id))
                        },
                    )
                }
            }
        }

        CategoryPresets.groups.forEach { group ->
            val presets = if (normalizedQuery.isEmpty()) group.presets else group.presets.filter {
                CategoryPresets.normalize(it.name).contains(normalizedQuery)
            }
            if (presets.isEmpty()) return@forEach
            PickerGroup(
                title = group.title,
                selectedCount = group.presets.count { it.key in picks.presetKeys },
                initiallyExpanded = normalizedQuery.isNotEmpty() ||
                    group.presets.any { it.recommended || it.key in picks.presetKeys },
                forceExpanded = normalizedQuery.isNotEmpty(),
                key = group.key,
            ) {
                presets.forEach { preset ->
                    val selected = preset.key in picks.presetKeys
                    ChoicePill(
                        label = preset.name,
                        selected = selected,
                        leadingIcon = if (selected) Icons.Filled.Check else CategoryIcons.forKey(preset.iconKey),
                        role = Role.Checkbox,
                        onClick = { togglePreset(preset.key) },
                    )
                }
            }
        }

        if (picks.customNames.isNotEmpty()) {
            PickerGroup(title = "Custom", selectedCount = picks.customNames.size, initiallyExpanded = true) {
                picks.customNames.forEach { name ->
                    ChoicePill(
                        label = name,
                        selected = true,
                        leadingIcon = Icons.Filled.Check,
                        trailingIcon = Icons.Filled.Close,
                        role = Role.Checkbox,
                        onClick = {
                            haptics.tick()
                            onChange(picks.copy(customNames = picks.customNames - name))
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(Spacing.md))
        Text("Add your own", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(Spacing.xs))
        Row(verticalAlignment = Alignment.Top) {
            OutlinedTextField(
                value = custom,
                onValueChange = {
                    custom = it.take(CategorySelection.MAX_CUSTOM)
                    customError = null
                },
                placeholder = { Text("e.g. Boat fuel") },
                singleLine = true,
                isError = customError != null,
                supportingText = if (customError != null) {
                    { Text(customError.orEmpty()) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { addCustom() }),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Spacing.sm))
            IconButton(onClick = { addCustom() }, modifier = Modifier.padding(top = 4.dp)) {
                Icon(Icons.Filled.Add, contentDescription = "Add custom category")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickerGroup(
    title: String,
    selectedCount: Int,
    initiallyExpanded: Boolean,
    forceExpanded: Boolean = false,
    key: String = title,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable(key) { mutableStateOf(initiallyExpanded) }
    val open = expanded || forceExpanded
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClickLabel = if (open) "Collapse $title" else "Expand $title") { expanded = !open }
                .semantics { stateDescription = if (open) "Expanded" else "Collapsed" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).semantics { heading() },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (selectedCount > 0) {
                Text(
                    "$selectedCount selected",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(Spacing.xs))
            }
            Icon(
                if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(visible = open) {
            FlowRow(
                Modifier.fillMaxWidth().padding(bottom = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) { content() }
        }
        Hairline()
    }
}

/**
 * Review before confirming: every chosen category grouped as in the library, each removable, and a plain summary of
 * what confirming does to existing categories ([deactivating] names those that will be archived or removed).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryReview(
    picks: CategoryPicks,
    onChange: (CategoryPicks) -> Unit,
    modifier: Modifier = Modifier,
    existingCustom: List<Category> = emptyList(),
    deactivating: List<String> = emptyList(),
    onEdit: (() -> Unit)? = null,
) {
    val haptics = rememberHaptics()
    Column(modifier) {
        val ownPicked = existingCustom.filter { it.id in picks.existingIds }
        if (ownPicked.isNotEmpty()) {
            ReviewGroup("Your categories") {
                ownPicked.forEach { c ->
                    ChoicePill(
                        label = c.name, selected = true, leadingIcon = CategoryIcons.forKey(c.iconKey), trailingIcon = Icons.Filled.Close,
                        role = Role.Button,
                        onClick = { haptics.tick(); onChange(picks.copy(existingIds = picks.existingIds - c.id)) },
                    )
                }
            }
        }
        CategoryPresets.groups.forEach { group ->
            val chosen = group.presets.filter { it.key in picks.presetKeys }
            if (chosen.isEmpty()) return@forEach
            ReviewGroup(group.title) {
                chosen.forEach { preset ->
                    ChoicePill(
                        label = preset.name, selected = true, leadingIcon = CategoryIcons.forKey(preset.iconKey), trailingIcon = Icons.Filled.Close,
                        role = Role.Button,
                        onClick = { haptics.tick(); onChange(picks.copy(presetKeys = picks.presetKeys - preset.key)) },
                    )
                }
            }
        }
        if (picks.customNames.isNotEmpty()) {
            ReviewGroup("Custom") {
                picks.customNames.forEach { name ->
                    ChoicePill(
                        label = name, selected = true, leadingIcon = CategoryIcons.forKey("other"), trailingIcon = Icons.Filled.Close,
                        role = Role.Button,
                        onClick = { haptics.tick(); onChange(picks.copy(customNames = picks.customNames - name)) },
                    )
                }
            }
        }
        if (picks.count == 0) {
            Text(
                "Nothing selected yet. Choose at least one category to build your budget from.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (deactivating.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.md))
            InfoNote(
                "Not chosen: ${deactivating.joinToString()}. Categories with history are archived (never deleted) " +
                    "and can be restored from Categories; unused ones are removed.",
            )
        }
        if (onEdit != null) {
            LucentoTextButton(text = "Add or change categories", onClick = onEdit)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewGroup(title: String, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.sm).semantics { heading() })
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) { content() }
}
