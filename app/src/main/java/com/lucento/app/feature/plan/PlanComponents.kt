package com.lucento.app.feature.plan

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.lucento.app.core.designsystem.component.AmountTextField
import com.lucento.app.core.designsystem.component.ChoicePill
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.MoneyFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.compose.ui.graphics.vector.ImageVector
import com.lucento.app.core.designsystem.component.LucentoFields
import com.lucento.app.core.designsystem.component.filledField

@Composable
internal fun fieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
)

/**
 * Amount input with the currency symbol as prefix. Text is parsed later, only through MoneyParser. [leadingIcon] and
 * [filled] (onboarding's tinted field, [LucentoFields]) are for forms that use that look.
 */
@Composable
internal fun MoneyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    currencyCode: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    supporting: String? = null,
    leadingIcon: ImageVector? = null,
    filled: Boolean = false,
) {
    AmountTextField(
        value = value,
        onValueChange = onValueChange,
        // Length only: MoneyParser judges the text (dropping characters here turned "۱۲٫۵" into 125, P0-3).
        filter = { it.take(20) },
        label = { Text(label) },
        prefix = { Text(MoneyFormatter.currencySymbol(currencyCode) + " ") },
        placeholder = { Text("0") },
        isError = error != null,
        textStyle = AmountStyles.medium,
        supportingText = if (error != null || supporting != null) {
            { Text(error ?: supporting.orEmpty()) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        shape = if (filled) LucentoFields.shape else MaterialTheme.shapes.small,
        colors = if (filled) LucentoFields.filledColors() else fieldColors(),
        leadingIcon = leadingIcon?.let { icon -> { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) } },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
internal fun TextInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null,
    maxLength: Int = 40,
    leadingIcon: ImageVector? = null,
    filled: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(maxLength)) },
        label = { Text(label) },
        placeholder = if (placeholder != null) {
            { Text(placeholder) }
        } else {
            null
        },
        singleLine = true,
        isError = error != null,
        supportingText = if (error != null) {
            { Text(error) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
        shape = if (filled) LucentoFields.shape else MaterialTheme.shapes.small,
        colors = if (filled) LucentoFields.filledColors() else fieldColors(),
        leadingIcon = leadingIcon?.let { icon -> { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) } },
        modifier = modifier.fillMaxWidth(),
    )
}

/** Horizontal pills for a small set of choices; [icon] gives a pill its leading glyph (wallet pills carry their type's). */
@Composable
internal fun <T> PillRow(
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
    icon: (T) -> androidx.compose.ui.graphics.vector.ImageVector? = { null },
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        options.forEach { (value, label) ->
            ChoicePill(label = label, selected = value == selected, leadingIcon = icon(value), onClick = { onSelect(value) })
        }
    }
}

internal fun formatDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

/** A tappable date row with an optional clear action and its own picker dialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateField(
    label: String,
    date: LocalDate?,
    onDate: (LocalDate?) -> Unit,
    emptyText: String = "Not set",
    clearable: Boolean = true,
    /** Onboarding's tinted field ([LucentoFields]) with the calendar in brand blue. */
    filled: Boolean = false,
) {
    var picking by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(
                if (filled) {
                    Modifier.filledField()
                } else {
                    Modifier.clip(MaterialTheme.shapes.medium).border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), MaterialTheme.shapes.medium)
                },
            )
            .clickable(role = Role.Button, onClickLabel = "Choose $label") { picking = true }
            .padding(start = if (filled) Spacing.md else Spacing.lg, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.CalendarMonth,
            contentDescription = null,
            tint = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f).padding(start = Spacing.md)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(date?.let(::formatDate) ?: emptyText, style = MaterialTheme.typography.bodyLarge)
        }
        if (clearable && date != null) {
            IconButton(onClick = { onDate(null) }) {
                Icon(Icons.Outlined.Close, contentDescription = "Clear $label")
            }
        }
    }
    if (picking) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (date ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = state)
        }
    }
}

/**
 * A picker in the dialogs' filled look ([filledField], like the wallet editor's type and onboarding's fields): the
 * chosen option's tile and name with [label] riding inside, and a trailing arrow; the menu lists every option the same
 * way and matches the field's width. Owner round 10: the schedule editor's accounts, category and "how often".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> FilledDropdown(
    label: String,
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    text: (T) -> String,
    leading: @Composable (option: T?, size: Dp) -> Unit,
    modifier: Modifier = Modifier,
    detail: (T) -> String? = { null },
    placeholder: String = "Choose",
    isError: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .filledField(active = expanded, isError = isError)
                .semantics {
                    role = Role.DropdownList
                    contentDescription = "$label, ${selected?.let(text) ?: "none chosen"}"
                }
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading(selected, 32.dp)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                // The detail rides with the label ("Paid from · Bank account · USD"), so the name keeps the full width
                // at large text sizes instead of being cut short.
                Text(
                    listOfNotNull(label, selected?.let(detail)).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    selected?.let(text) ?: placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selected != null) colors.onSurface else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
        }
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text(option),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (option == selected) FontWeight.SemiBold else null,
                            )
                            detail(option)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1) }
                        }
                    },
                    leadingIcon = { leading(option, 28.dp) },
                    trailingIcon = if (option == selected) ({ Icon(Icons.Filled.Check, contentDescription = "Selected", tint = colors.primary) }) else null,
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
