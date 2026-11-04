package com.lucento.app.core.designsystem.component

import com.lucento.app.core.designsystem.theme.TonePair
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.border
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.theme.Sizes
import com.lucento.app.core.designsystem.theme.Spacing
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.heading
import com.lucento.app.core.designsystem.theme.Hierarchy

/** Filled, borderless search field. */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Sizes.minTouch)
                    .clip(MaterialTheme.shapes.medium)
                    .background(colors.surfaceContainer)
                    .padding(start = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(Sizes.icon),
                )
                Spacer(Modifier.width(Spacing.md))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
                if (value.isNotEmpty()) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = colors.onSurfaceVariant)
                    }
                } else {
                    Spacer(Modifier.width(Spacing.md))
                }
            }
        },
    )
}

/**
 * Two-to-four option switch with a sliding-feel selected tile. Used for Expense / Income and theme.
 * Each option is a radio-like selectable so TalkBack announces state.
 *
 * [selectedTone] fills the selected tile with a stronger colour pair (e.g. brand blue for history filters, the
 * income / expense tone in the editor) where the choice deserves emphasis; [compact] trims the row to 40dp for
 * dense, pinned headers (the tiles span the full width, so they stay easy to hit).
 */
@Composable
fun <T> SegmentedToggle(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    selectedTone: ((T) -> TonePair)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    val track = colors.surfaceContainerHigh
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(track)
            .then(if (selectedTone != null) Modifier.border(1.dp, colors.outlineVariant, MaterialTheme.shapes.medium) else Modifier)
            .padding(if (compact) 3.dp else 4.dp)
            .selectableGroup(),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            val tone = if (isSelected) selectedTone?.invoke(value) else null
            val container by animateColorAsState(
                when {
                    tone != null -> tone.container
                    isSelected -> colors.surfaceContainerLowest
                    else -> track
                },
                animationSpec = tween(180),
                label = "segment",
            )
            val content by animateColorAsState(
                when {
                    tone != null -> tone.content
                    isSelected -> colors.onSurface
                    else -> colors.onSurfaceVariant
                },
                animationSpec = tween(180),
                label = "segment-text",
            )
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = if (compact) 34.dp else 44.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(container)
                    .selectable(selected = isSelected, role = Role.RadioButton) {
                        if (!isSelected) {
                            haptics.tick()
                            onSelect(value)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = if (isSelected && tone != null) MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    else MaterialTheme.typography.labelLarge,
                    color = content,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Selectable pill used for wallets, date shortcuts and filters. Selected is solid primary; [tonal] makes it pale sky
 * with deep-blue text instead, for a filter that is applied but shouldn't shout (Transactions' date range).
 */
@Composable
fun ChoicePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    role: Role = Role.RadioButton,
    tonal: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val selectedContainer = if (tonal) colors.primaryContainer else colors.primary
    val container by animateColorAsState(
        if (selected) selectedContainer else colors.surfaceContainerLowest,
        tween(160),
        label = "pill",
    )
    // Selected: solid primary (or pale sky) with its label colour; unselected: a neutral pill with a hairline.
    val border = if (selected) selectedContainer else colors.outlineVariant
    Surface(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = 40.dp)
            .clip(MaterialTheme.shapes.small)
            .selectable(selected = selected, role = role, onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = container,
        contentColor = when {
            !selected -> colors.onSurface
            tonal -> colors.onPrimaryContainer
            else -> colors.onPrimary
        },
        border = BorderStroke(1.dp, border),
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.sm))
            }
            Text(
                label,
                style = if (selected && tonal) MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold) else MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (trailingIcon != null) {
                Spacer(Modifier.width(Spacing.xs))
                Icon(trailingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** One choice in a [ViewToggle]: its value, glyph and spoken name. */
data class ViewOption<T>(val value: T, val icon: ImageVector, val label: String)

/**
 * Icon buttons in one outlined pill (e.g. row view / card view). The selected one takes the pale-sky fill with an
 * on-primary-container glyph. The pill is drawn 40dp tall, but each button's touch target is the full 48dp row.
 */
@Composable
fun <T> ViewToggle(
    options: List<ViewOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    val outline = colors.outlineVariant
    val surface = colors.surfaceContainerLowest
    Row(
        modifier
            .height(Sizes.minTouch)
            .drawBehind {
                val inset = 4.dp.toPx()
                val radius = CornerRadius(13.dp.toPx())
                drawRoundRect(surface, topLeft = Offset(0f, inset), size = Size(size.width, size.height - inset * 2), cornerRadius = radius)
                drawRoundRect(
                    outline,
                    topLeft = Offset(0f, inset),
                    size = Size(size.width, size.height - inset * 2),
                    cornerRadius = radius,
                    style = Stroke(1.dp.toPx()),
                )
            }
            .padding(horizontal = Spacing.xs)
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { option ->
            val isSelected = option.value == selected
            val fill by animateColorAsState(if (isSelected) colors.primaryContainer else Color.Transparent, tween(160), label = "view-toggle")
            Box(
                Modifier
                    .width(40.dp)
                    .fillMaxHeight()
                    .selectable(selected = isSelected, role = Role.RadioButton) {
                        if (!isSelected) {
                            haptics.tick()
                            onSelect(option.value)
                        }
                    }
                    .semantics { contentDescription = option.label },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(width = 36.dp, height = 32.dp).clip(RoundedCornerShape(9.dp)).background(fill),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        option.icon,
                        contentDescription = null,
                        tint = if (isSelected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/**
 * Onboarding's field look, shared by forms that follow it (the wallet editor): a tinted, filled container with rounded
 * corners and no outline until focused (then brand blue), as in setup's currency search.
 */
object LucentoFields {
    val shape: Shape
        @Composable get() = MaterialTheme.shapes.medium

    @Composable
    fun filledColors(): TextFieldColors {
        val colors = MaterialTheme.colorScheme
        return OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = colors.surfaceContainer,
            focusedContainerColor = colors.surfaceContainer,
            errorContainerColor = colors.surfaceContainer,
            disabledContainerColor = colors.surfaceContainer,
            unfocusedBorderColor = Color.Transparent,
            disabledBorderColor = Color.Transparent,
            focusedBorderColor = colors.primary,
        )
    }
}

/**
 * A tappable field in the [LucentoFields] look — a picker that opens a menu or sheet: tinted, rounded, outlined in brand
 * blue only while [active] (its menu is open) and in the error colour when [isError].
 */
@Composable
fun Modifier.filledField(active: Boolean = false, isError: Boolean = false): Modifier {
    val colors = MaterialTheme.colorScheme
    val shape = LucentoFields.shape
    val edge by animateColorAsState(
        when {
            isError -> colors.error
            active -> colors.primary
            else -> Color.Transparent
        },
        tween(160),
        label = "field-edge",
    )
    return this
        .clip(shape)
        .background(colors.surfaceContainer)
        .border(BorderStroke(if (active || isError) 1.5.dp else 1.dp, edge), shape)
}

/** A form section's label with its icon in brand blue (onboarding's "Appearance", "Main currency"). */
@Composable
fun IconFieldHeader(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(bottom = Spacing.sm).semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Spacing.sm))
        Text(text, style = Hierarchy.fieldLabel, color = MaterialTheme.colorScheme.onSurface)
    }
}
