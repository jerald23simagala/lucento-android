package com.lucento.app.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.TonePair
import com.lucento.app.core.designsystem.theme.Spacing

/**
 * A navigational row: tinted icon tile, title, one line of real status ("3 active budgets"), chevron.
 * Used by hubs (More, Plan) so every destination says what it holds before it is opened.
 */
@Composable
fun NavRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    tone: TonePair? = null,
    trailing: (@Composable () -> Unit)? = null,
    /** When true the title wraps instead of ellipsizing, so a large font scale does not clip it. */
    wrapTitle: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Spacing.sm, horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(
            icon = icon,
            container = tone?.container ?: colors.primaryContainer,
            content = tone?.content ?: colors.onPrimaryContainer,
        )
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = if (wrapTitle) Int.MAX_VALUE else 1,
                overflow = if (wrapTitle) TextOverflow.Clip else TextOverflow.Ellipsis,
            )
            if (summary != null) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = if (wrapTitle) Int.MAX_VALUE else 2,
                    overflow = if (wrapTitle) TextOverflow.Clip else TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(Spacing.sm))
            trailing()
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.onSurfaceVariant)
    }
}

/** A setting with a switch. The whole row toggles, and TalkBack announces it as a switch. */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    note: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = {
                    haptics.tick()
                    onCheckedChange(it)
                },
            )
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconTile(icon = icon, container = colors.surfaceContainerHigh, content = colors.onSurface)
            Spacer(Modifier.width(Spacing.md))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) colors.onSurface else colors.onSurfaceVariant,
            )
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            if (note != null) {
                Text(note, style = MaterialTheme.typography.bodySmall, color = LucentoTheme.extendedColors.accentText)
            }
        }
        Spacer(Modifier.width(Spacing.md))
        // The row handles the toggle; the switch is visual only so there is a single focus target.
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = colors.primary),
        )
    }
}

/** A quiet, tinted explanation box: how a number is calculated, what's missing, what a choice does. */
@Composable
fun InfoNote(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Info,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    /** An optional follow-up ("Adjust balance") shown inside the note, under the text. */
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(container)
            .padding(start = Spacing.md, end = Spacing.md, top = Spacing.md, bottom = if (actionLabel != null) Spacing.xs else Spacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.padding(top = 1.dp).width(18.dp))
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodySmall, color = content)
            if (actionLabel != null && onAction != null) {
                Text(
                    actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .clip(MaterialTheme.shapes.small)
                        .clickable(role = Role.Button, onClick = onAction)
                        .padding(vertical = Spacing.sm),
                )
            }
        }
    }
}

/**
 * A tinted, rounded title for a figure on a summary card ("In", "Net worth", "Assets"): reads as the card's label at a
 * glance instead of a faint caption. The [label] carries the meaning; [tone] and [icon] only reinforce it.
 */
@Composable
fun ToneChip(label: String, tone: TonePair, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier
            .background(tone.container, RoundedCornerShape(50))
            .padding(start = if (icon != null) 6.dp else Spacing.sm, end = Spacing.sm, top = Spacing.xxs, bottom = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tone.content, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(Spacing.xs))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = tone.content, maxLines = 1)
    }
}

/** Small tinted status label ("Over budget", "Due in 3 days", "Paused"). Text always carries the meaning. */
@Composable
fun StatusPill(text: String, tone: TonePair, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = tone.content,
        maxLines = 1,
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(tone.container)
            .padding(horizontal = Spacing.sm, vertical = 3.dp),
    )
}

object StatusTones {
    val neutral: TonePair
        @Composable get() = TonePair(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant)
    val positive: TonePair
        @Composable get() = TonePair(LucentoTheme.extendedColors.positiveContainer, LucentoTheme.extendedColors.positive)
    val negative: TonePair
        @Composable get() = TonePair(LucentoTheme.extendedColors.negativeContainer, LucentoTheme.extendedColors.negative)
    val warning: TonePair
        @Composable get() = TonePair(LucentoTheme.extendedColors.warningContainer, LucentoTheme.extendedColors.warning)
    val brand: TonePair
        @Composable get() = TonePair(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
}
