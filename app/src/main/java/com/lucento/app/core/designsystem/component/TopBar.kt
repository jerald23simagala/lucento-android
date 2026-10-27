package com.lucento.app.core.designsystem.component

import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.theme.Sizes
import com.lucento.app.core.designsystem.theme.Spacing

/**
 * Compact screen header: a modest title (the content is the hero, not the chrome), an optional
 * back affordance and trailing actions. Sits inside the screen's own horizontal gutter.
 */
@Composable
fun LucentoTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.topBar)
            .padding(top = Spacing.sm, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            BackPill(onClick = onBack)
            Spacer(Modifier.width(Spacing.md))
        }
        Column(Modifier.weight(1f)) {
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // A blank title leaves the heading to the screen's content (e.g. the wallet card).
            if (title.isNotEmpty()) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** The back affordance: a brand-tinted pill, so it reads as a control at a glance rather than a bare glyph. */
@Composable
fun BackPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .semantics {
                contentDescription = "Back"
                role = Role.Button
            },
    ) {
        Box(Modifier.width(52.dp).heightIn(min = 40.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(22.dp))
        }
    }
}

/**
 * A page's add action, always in its top bar's trailing corner (Wallets, Goals, Debts, Recurring): a pill with a plus
 * and a short label. Brand blue in the light theme; sunlight yellow at night, where it stands out on navy.
 */
@Composable
fun AddPillButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, contentDescription: String = label) {
    val (container, content) = addActionColors()
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = container,
        contentColor = content,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .semantics { this.contentDescription = contentDescription; role = Role.Button },
    ) {
        Row(Modifier.heightIn(min = 36.dp).padding(start = Spacing.sm, end = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(androidx.compose.material.icons.Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.xxs))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** The colours of add actions (the Add pill and the dock's + button): primary by day, sunlight by night. */
@Composable
fun addActionColors(): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> {
    val ext = com.lucento.app.core.designsystem.theme.LucentoTheme.extendedColors
    return if (com.lucento.app.core.designsystem.theme.LucentoTheme.isDark) {
        ext.accent to ext.onAccent
    } else {
        MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
    }
}
