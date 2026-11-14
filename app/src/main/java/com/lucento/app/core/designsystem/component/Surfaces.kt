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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.Elevation
import com.lucento.app.core.designsystem.theme.Hierarchy
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.OverlineStyle
import com.lucento.app.core.designsystem.theme.Sizes
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.designsystem.theme.TonePair
import java.util.Locale

/**
 * Card: hairline border plus, by day only, the faint navy shadow-xs (night uses tonal separation). Use sparingly —
 * most lists sit directly on the background and are grouped by spacing and section labels, not boxes.
 */
@Composable
fun LucentoCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = Spacing.lg,
    shape: Shape = MaterialTheme.shapes.large,
    content: @Composable ColumnScope.() -> Unit,
) {
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
        Column(Modifier.heroPanel()) {
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
            HeroHorizon(height = 44.dp)
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
    /** 48dp by default, room for a trailing action; a header with only text beside it may hug its text. */
    minHeight: Dp = Sizes.minTouch,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = minHeight).padding(top = Spacing.xs),
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

/** On wide windows (large phones in landscape, foldables) a screen's content is held to this width and centred. */
val ContentMaxWidth = 560.dp

/**
 * The horizontal margin for a screen's content: the 20dp gutter on phones; from 600dp wide, whatever centres a
 * [ContentMaxWidth] column. Use it in place of `Spacing.gutter` for a screen's outer padding.
 */
@Composable
fun screenGutter(): Dp {
    val width = LocalConfiguration.current.screenWidthDp.dp
    return maxOf(Spacing.gutter, (width - ContentMaxWidth) / 2)
}

/**
 * The top of a dashboard card: a 28dp tonal icon tile, the card's name as a quiet caps overline
 * ([Hierarchy.cardOverline]) so the figure below stays the loudest thing on the card, and an optional trailing status
 * pill or text action. The caps are visual only: TalkBack (and tests) read the sentence-case [title] as a heading.
 */
@Composable
fun CardOverline(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: TonePair = StatusTones.brand,
    color: Color = MaterialTheme.colorScheme.onSurface,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            IconTile(icon, container = tone.container, content = tone.content, size = 28.dp, shape = RoundedCornerShape(8.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(
            title.uppercase(Locale.getDefault()),
            style = Hierarchy.cardOverline,
            color = color,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).clearAndSetSemantics {
                heading()
                text = AnnotatedString(title)
            },
        )
        if (trailing != null) {
            Spacer(Modifier.width(Spacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
        }
    }
}

/** How a [StatTile] arranges its icon, label and figure. */
enum class StatTileLayout {
    /** Icon above the caps label above the figure: three across (Transactions' summary). */
    Stacked,

    /** A larger icon beside the label and figure: two across (Wallets' assets and liabilities). */
    Inline,
}

/**
 * One labelled figure on its own small card: a tonal icon tile, a caps label ([OverlineStyle], uppercased here, visual
 * only) and the figure, usually a [MoneyText]. [description] is what TalkBack reads for the whole tile, figure included,
 * so it must carry the signed amount in words. Large text turns [StatTileLayout.Inline] into [StatTileLayout.Stacked].
 */
@Composable
fun StatTile(
    label: String,
    icon: ImageVector,
    tone: TonePair,
    description: String,
    modifier: Modifier = Modifier,
    layout: StatTileLayout = StatTileLayout.Stacked,
    value: @Composable () -> Unit,
) {
    val stacked = layout == StatTileLayout.Stacked || LocalDensity.current.fontScale >= 1.3f
    LucentoCard(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        contentPadding = if (stacked) Spacing.md else 14.dp,
        shape = RoundedCornerShape(16.dp),
    ) {
        if (stacked) {
            IconTile(icon, container = tone.container, content = tone.content, size = 30.dp, shape = RoundedCornerShape(9.dp))
            Spacer(Modifier.height(10.dp))
            StatLabel(label, MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.xxs))
            value()
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(icon, container = tone.container, content = tone.content, size = Sizes.iconTile, shape = RoundedCornerShape(12.dp))
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    StatLabel(label, MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Spacing.xxs))
                    value()
                }
            }
        }
    }
}

/**
 * A [StatTile] sitting on a [HeroCard]: a translucent white sub-card (14 % alpha) with a small icon and caps label on one
 * line and the figure beneath. [description] is what TalkBack reads for the tile.
 */
@Composable
fun HeroStatTile(
    label: String,
    icon: ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    value: @Composable () -> Unit,
) {
    val ext = LucentoTheme.extendedColors
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(ext.onHero.copy(alpha = 0.14f))
            .clearAndSetSemantics { contentDescription = description }
            .padding(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, container = ext.onHero.copy(alpha = 0.18f), content = ext.onHero, size = 26.dp, shape = RoundedCornerShape(8.dp))
            Spacer(Modifier.width(Spacing.sm))
            StatLabel(label, ext.onHeroMuted)
        }
        Spacer(Modifier.height(6.dp))
        value()
    }
}

/** A stat's caps label; at very large text it wraps to a second line rather than being cut short. */
@Composable
private fun StatLabel(label: String, color: Color) {
    Text(label.uppercase(Locale.getDefault()), style = OverlineStyle, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

/**
 * The header above a carded list (Budget's Budgeted / Not budgeted, Wallets' Assets / Liabilities): the title in
 * [Hierarchy.sectionTitle], a short caption beside it (what it holds, how many) or an [action] in its place, and the
 * group's subtotal at the end of the same line.
 */
@Composable
fun ListSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    subtotal: String? = null,
    action: @Composable (RowScope.() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 40.dp).padding(top = Spacing.lg, bottom = Spacing.xs, start = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = Hierarchy.sectionTitle, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.width(Spacing.sm))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            when {
                action != null -> action()
                caption != null -> Text(
                    caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (subtotal != null) {
            Spacer(Modifier.width(Spacing.sm))
            Text(
                subtotal,
                style = AmountStyles.small.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
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
