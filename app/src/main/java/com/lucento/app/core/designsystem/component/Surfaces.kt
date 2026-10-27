package com.lucento.app.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.theme.Elevation
import com.lucento.app.core.designsystem.theme.Hierarchy
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Sizes
import com.lucento.app.core.designsystem.theme.Spacing

/**
 * Card: hairline border plus, by day only, the faint navy shadow-xs (night uses tonal separation). Use sparingly —
 * most lists sit directly on the background and are grouped by spacing and section labels, not boxes.
 */
@Composable
fun LucentoCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = Spacing.lg,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    // Cards use the stronger border-default tone so their edges read clearly on the ivory / navy canvas.
    val border = BorderStroke(1.dp, LucentoTheme.extendedColors.borderDefault)
    // Cards lift off the canvas: white on ivory by day, a lighter navy by night.
    val dark = LucentoTheme.isDark
    val color = if (dark) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLowest
    val tint = LucentoTheme.extendedColors.shadow
    val cardModifier = if (dark) modifier else modifier.shadow(
        elevation = Elevation.xs,
        shape = shape,
        clip = false,
        ambientColor = tint.copy(alpha = 0.06f),
        spotColor = tint.copy(alpha = 0.12f),
    )
    if (onClick != null) {
        Surface(onClick = onClick, modifier = cardModifier, shape = shape, color = color, border = border) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        Surface(modifier = cardModifier, shape = shape, color = color, border = border) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}

/**
 * Lucento's "statement" panel: solid brand blue with white type and a short sun-yellow rule.
 * Reserved for the one message a screen is about (first-run setup). Content colour is set for children.
 */
@Composable
fun StatementPanel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LucentoTheme.extendedColors
    val shape = MaterialTheme.shapes.large
    val body: @Composable () -> Unit = {
        Column(Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.xl)) {
            Box(
                Modifier
                    .width(28.dp)
                    .height(3.dp)
                    .background(colors.accent, RoundedCornerShape(2.dp)),
            )
            Spacer(Modifier.height(Spacing.lg))
            CompositionLocalProvider(LocalContentColor provides colors.onHero) { content() }
        }
    }
    val clickable = if (onClick != null) {
        Modifier.clip(shape).clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    Surface(modifier = modifier.then(clickable), shape = shape, color = colors.hero, contentColor = colors.onHero) { body() }
}

/**
 * A section of a screen: a sentence-case title in [Hierarchy.sectionTitle], optional [supporting] context beneath
 * it, and an optional trailing action. See [Hierarchy] for the full scale.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = Sizes.minTouch).padding(top = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f, fill = false)) {
            Text(
                text = title,
                style = Hierarchy.sectionTitle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            if (supporting != null) {
                Text(supporting, style = Hierarchy.context, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) {
            Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
        }
    }
}

/** A label for a group of fields inside a form or sheet ("Category", "Date"): quieter than a [SectionHeader]. */
@Composable
fun FieldHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = Hierarchy.fieldLabel,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f, fill = false).semantics { heading() },
        )
        if (trailing != null) {
            Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
        }
    }
}

/**
 * The top of a card: what the card is about ([title], [Hierarchy.cardTitle]), its scope ([context], e.g.
 * "October · USD") and an optional trailing element (a status, an action or a chevron). Primary figures follow it.
 */
@Composable
fun CardHeader(
    title: String,
    modifier: Modifier = Modifier,
    context: String? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    contextColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Hierarchy.cardTitle, color = titleColor, modifier = Modifier.semantics { heading() })
            if (context != null) Text(context, style = Hierarchy.context, color = contextColor)
        }
        if (trailing != null) {
            Spacer(Modifier.width(Spacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
        }
    }
}

/**
 * Hairline separator between list rows. [inset] aligns it with row text rather than the icon; [strong] uses a more
 * visible tone for rows packed tightly inside a card.
 */
@Composable
fun Hairline(modifier: Modifier = Modifier, inset: Dp = 0.dp, strong: Boolean = false) {
    HorizontalDivider(
        modifier = modifier.padding(start = inset),
        thickness = 1.dp,
        color = if (strong) MaterialTheme.colorScheme.outline.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant,
    )
}

/** Leading icon on a tinted rounded tile. Purely decorative: the row's text carries the meaning. */
@Composable
fun IconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: Color = MaterialTheme.colorScheme.onSurface,
    size: Dp = Sizes.iconTile,
    shape: Shape = MaterialTheme.shapes.small,
) {
    Box(
        modifier = modifier.size(size).clip(shape).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = content, modifier = Modifier.size(size * 0.5f))
    }
}

/** Brand-tinted icon tile used for principles and informational rows. */
@Composable
fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier) {
    IconTile(
        icon = icon,
        modifier = modifier,
        container = MaterialTheme.colorScheme.primaryContainer,
        content = MaterialTheme.colorScheme.onPrimaryContainer,
        size = Sizes.iconTileLarge,
        shape = MaterialTheme.shapes.medium,
    )
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    illustration: @Composable (() -> Unit)? = null,
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        illustration?.let {
            it()
            Spacer(Modifier.size(Spacing.md))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        action?.let {
            Spacer(Modifier.size(Spacing.md))
            it()
        }
    }
}

/** A labelled figure: small muted label above a tabular value. */
@Composable
fun Figure(
    label: String,
    modifier: Modifier = Modifier,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    value: @Composable () -> Unit,
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = labelColor, maxLines = 1)
        Spacer(Modifier.height(Spacing.xs))
        value()
    }
}
