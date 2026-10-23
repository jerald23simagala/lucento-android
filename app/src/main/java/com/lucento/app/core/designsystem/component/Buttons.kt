package com.lucento.app.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Sizes
import com.lucento.app.core.designsystem.theme.Spacing

private val ButtonPadding = PaddingValues(horizontal = 24.dp)

@Composable
fun LucentoPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val haptics = rememberHaptics()
    Button(
        onClick = {
            haptics.confirm()
            onClick()
        },
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = Sizes.button),
        shape = MaterialTheme.shapes.medium,
        contentPadding = ButtonPadding,
        colors = ButtonDefaults.buttonColors(),
    ) {
        ButtonContent(text, icon)
    }
}

@Composable
fun LucentoSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    fillWidth: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = (if (fillWidth) modifier.fillMaxWidth() else modifier).heightIn(min = Sizes.button),
        shape = MaterialTheme.shapes.medium,
        contentPadding = ButtonPadding,
        // Sunrise secondary: a pale-sky (night: elevated navy) fill with ink text; no outline.
        border = null,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (LucentoTheme.isDark) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        ButtonContent(text, icon)
    }
}

@Composable
fun LucentoTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = Sizes.minTouch),
        colors = ButtonDefaults.textButtonColors(contentColor = color),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Compact tonal action used for the Home quick actions ("Expense", "Income"). */
@Composable
fun QuickActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    Button(
        onClick = {
            haptics.tick()
            onClick()
        },
        modifier = modifier.heightIn(min = Sizes.button),
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = Spacing.lg),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        ButtonContent(text, icon)
    }
}

/**
 * Round icon action with a label underneath (Home quick actions). The whole tile is one touch target.
 * [primary] fills the circle with the brand colour for the single most common action.
 */
@Composable
fun ActionTile(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
) {
    val haptics = rememberHaptics()
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClickLabel = label) {
                haptics.tick()
                onClick()
            }
            .padding(vertical = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(if (primary) colors.primary else colors.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (primary) colors.onPrimary else colors.onPrimaryContainer,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ButtonContent(text: String, icon: ImageVector?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(Sizes.icon))
            Spacer(Modifier.width(Spacing.sm))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}
