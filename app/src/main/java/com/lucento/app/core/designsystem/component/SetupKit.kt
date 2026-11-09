package com.lucento.app.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.theme.Hierarchy
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.designsystem.theme.TonePair
import com.lucento.app.domain.SetupProgress
import com.lucento.app.domain.SetupStage

/*
 * Getting started's shared parts ("Sunrise Path", owner-approved 2026-11-08): every onboarding and guided-setup step
 * is a path rail, one decision, and an action dock. Selection always reads the same way — pale sky, a blue edge and a
 * check — so solid blue is left for the one primary action.
 */

/**
 * The top of every getting-started step: back, the three stages as thin segments with the current one named and
 * counted ("Personalize · 2 of 5"), and Skip when the step is optional. One row, so the question starts right under it.
 * TalkBack hears the stages as one sentence ([SetupProgress.description]). [finished] fills every segment and replaces
 * the caption (the last page). Without [progress] only back (and skip) are shown.
 */
@Composable
fun PathRail(
    progress: SetupProgress?,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backDescription: String = "Back",
    onSkip: (() -> Unit)? = null,
    skipLabel: String = "Skip",
    skipDescription: String? = null,
    finished: String? = null,
) {
    Row(modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(48.dp), contentAlignment = Alignment.CenterStart) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = backDescription)
                }
            }
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = Spacing.xs)
                .then(if (progress != null) Modifier.clearAndSetSemantics { contentDescription = finished ?: progress.description } else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (progress != null) {
                StageSegments(progress, allDone = finished != null)
                Spacer(Modifier.height(7.dp))
                val ink = MaterialTheme.colorScheme.onSurface
                Text(
                    if (finished != null) {
                        buildAnnotatedString { withStyle(SpanStyle(color = ink, fontWeight = FontWeight.SemiBold)) { append(finished) } }
                    } else {
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = ink, fontWeight = FontWeight.SemiBold)) { append(progress.current.label) }
                            if (progress.counted) append(" · ${progress.screen} of ${progress.screens}")
                        }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
        Box(Modifier.widthIn(min = 48.dp), contentAlignment = Alignment.CenterEnd) {
            if (onSkip != null) {
                Text(
                    skipLabel,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button, onClick = onSkip)
                        .then(if (skipDescription != null) Modifier.semantics { contentDescription = skipDescription } else Modifier)
                        .heightIn(min = 48.dp)
                        .padding(horizontal = Spacing.sm, vertical = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun StageSegments(progress: SetupProgress, allDone: Boolean) {
    val track = LucentoTheme.extendedColors.track
    val fillColor = MaterialTheme.colorScheme.primary
    val reduced = LucentoTheme.reducedMotion
    val currentFill by animateFloatAsState(progress.fill, if (reduced) tween(0) else tween(320), label = "rail-fill")
    Row(Modifier.widthIn(max = 220.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        SetupStage.entries.forEach { stage ->
            val fraction = when {
                allDone || progress.isDone(stage) && stage != progress.current -> 1f
                stage == progress.current -> currentFill
                else -> 0f
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(track)
                    .drawBehind { drawRect(fillColor, size = Size(size.width * fraction, size.height)) },
            )
        }
    }
}

/** A step's title and the one line saying what it's for. The title is the screen's heading for TalkBack. */
@Composable
fun SetupTitle(title: String, lead: String?, modifier: Modifier = Modifier, milestone: Boolean = false, center: Boolean = false) {
    Column(modifier.fillMaxWidth().padding(top = Spacing.xs)) {
        Text(
            title,
            style = if (milestone) Hierarchy.milestoneTitle else Hierarchy.stepTitle,
            textAlign = if (center) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.fillMaxWidth().semantics { heading() },
        )
        if (lead != null) {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                lead,
                style = Hierarchy.lead,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = if (center) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** A quiet label over a group of choices, with an optional count or note at the end. */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = Hierarchy.groupLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(trailing, style = Hierarchy.groupLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The bottom of every getting-started step: the action(s), with a short fade above so content scrolling underneath
 * passes softly out of sight. It sits above the keyboard because the step's column is IME-padded.
 */
@Composable
fun ActionDock(
    modifier: Modifier = Modifier,
    /** The soft fade above the dock, for steps whose content scrolls; off where text sits right above it. */
    fade: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val background = MaterialTheme.colorScheme.background
    Column(
        modifier
            .fillMaxWidth()
            .drawBehind {
                if (!fade) return@drawBehind
                val fade = 16.dp.toPx()
                drawRect(
                    Brush.verticalGradient(listOf(background.copy(alpha = 0f), background), startY = -fade, endY = 0f),
                    topLeft = Offset(-size.width, -fade),
                    size = Size(size.width * 3, fade),
                )
            }
            .padding(top = Spacing.md, bottom = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        content = content,
    )
}

/** The running count beside a dock's button on steps that collect choices ("12 chosen · Across 6 groups"). */
@Composable
fun DockCount(title: String, detail: String?, modifier: Modifier = Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.Center) {
        Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), maxLines = 1)
        if (detail != null) {
            Text(
                detail,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A round check: filled blue with a white tick when [checked], an empty ring otherwise. Decorative (the row speaks). */
@Composable
fun CheckMark(checked: Boolean, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (checked) scheme.primary else Color.Transparent)
            .then(if (checked) Modifier else Modifier.border(2.dp, scheme.outline.copy(alpha = 0.7f), CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(Icons.Filled.Check, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(size * 0.66f))
    }
}

/** A radio: a blue ring with a dot when [selected]. Decorative (the card speaks). */
@Composable
fun RadioMark(selected: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier.size(24.dp).border(2.dp, if (selected) scheme.primary else scheme.outline.copy(alpha = 0.7f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(12.dp).clip(CircleShape).background(scheme.primary))
    }
}

/** A small round colour spot holding a glyph: names a group of categories at a glance. Decorative. */
@Composable
fun SpotIcon(icon: ImageVector, tone: TonePair, modifier: Modifier = Modifier, size: Dp = 30.dp) {
    Box(modifier.size(size).clip(CircleShape).background(tone.container), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tone.content, modifier = Modifier.size(size * 0.57f))
    }
}

/** One fact with a green tick ("No bank login"). */
@Composable
fun FactTick(text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = LucentoTheme.extendedColors.positive, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** What a [SelectChip] does when tapped. */
enum class SelectChipState {
    /** Not chosen: white with a "+". */
    ADD,

    /** Chosen: pale sky, blue edge, a check. Tapping unchooses it. */
    CHOSEN,

    /** In a review: white with its icon in blue and an "×" that removes it. */
    REMOVE,
}

/**
 * A category-style chip. [SelectChipState.ADD] and [SelectChipState.CHOSEN] are one checkbox (TalkBack hears
 * "checked"/"not checked"); [SelectChipState.REMOVE] is a button that says "Remove <label>".
 */
@Composable
fun SelectChip(
    label: String,
    icon: ImageVector?,
    state: SelectChipState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val chosen = state == SelectChipState.CHOSEN
    val container by animateColorAsState(if (chosen) scheme.primaryContainer else scheme.surfaceContainerLowest, tween(160), label = "chip-fill")
    val edge by animateColorAsState(if (chosen) scheme.primary else LucentoTheme.extendedColors.borderDefault, tween(160), label = "chip-edge")
    val shape = RoundedCornerShape(12.dp)
    val action = when (state) {
        SelectChipState.REMOVE -> Modifier.clickable(role = Role.Button, onClickLabel = "Remove $label", onClick = onClick)
        else -> Modifier.toggleable(value = chosen, role = Role.Checkbox, onValueChange = { onClick() })
    }
    Row(
        modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(container)
            .border(BorderStroke(if (chosen) 1.5.dp else 1.dp, edge), shape)
            .then(action)
            .padding(start = 11.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = when (state) {
                    SelectChipState.CHOSEN -> scheme.primary
                    SelectChipState.REMOVE -> scheme.onPrimaryContainer
                    SelectChipState.ADD -> scheme.onSurfaceVariant
                },
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(7.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (state == SelectChipState.ADD) FontWeight.Medium else FontWeight.SemiBold),
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(5.dp))
        Icon(
            when (state) {
                SelectChipState.ADD -> Icons.Filled.Add
                SelectChipState.CHOSEN -> Icons.Filled.Check
                SelectChipState.REMOVE -> Icons.Filled.Close
            },
            contentDescription = null,
            tint = if (chosen) scheme.primary else scheme.onSurfaceVariant,
            modifier = Modifier.size(17.dp),
        )
    }
}

/**
 * One choice in a grouped card: a toned icon tile, the title and a line of support, and a check. Checked rows take the
 * pale-sky fill. [enabled] false keeps it as information only.
 */
@Composable
fun OptionRow(
    icon: ImageVector,
    tone: TonePair,
    title: String,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    body: String? = null,
) {
    val haptics = rememberHaptics()
    val fill by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        tween(160),
        label = "option-fill",
    )
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .background(fill)
            .toggleable(value = checked, role = Role.Checkbox) {
                haptics.tick()
                onToggle()
            }
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, container = tone.container, content = tone.content, shape = MaterialTheme.shapes.small)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (body != null) Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(Spacing.sm))
        CheckMark(checked)
    }
}

/** A choice drawn as a tile (two per row): a toned icon, a check in the corner, the title and its support line. */
@Composable
fun OptionTile(
    icon: ImageVector,
    tone: TonePair,
    title: String,
    body: String,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    /** Laid out as one row (icon, words, check): for an odd last choice spanning the whole width. */
    wide: Boolean = false,
) {
    val haptics = rememberHaptics()
    val scheme = MaterialTheme.colorScheme
    val fill by animateColorAsState(if (checked) scheme.primaryContainer else scheme.surfaceContainerLowest, tween(160), label = "tile-fill")
    val edge by animateColorAsState(if (checked) scheme.primary else LucentoTheme.extendedColors.borderDefault, tween(160), label = "tile-edge")
    val shape = MaterialTheme.shapes.large
    if (wide) {
        Row(
            modifier
                .heightIn(min = 72.dp)
                .clip(shape)
                .background(fill)
                .border(BorderStroke(if (checked) 1.5.dp else 1.dp, edge), shape)
                .toggleable(value = checked, role = Role.Checkbox) {
                    haptics.tick()
                    onToggle()
                }
                .padding(horizontal = Spacing.lg, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(icon, container = tone.container, content = tone.content, size = 44.dp, shape = MaterialTheme.shapes.medium)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(Spacing.sm))
            CheckMark(checked)
        }
        return
    }
    Box(
        modifier
            .heightIn(min = 132.dp)
            .clip(shape)
            .background(fill)
            .border(BorderStroke(if (checked) 1.5.dp else 1.dp, edge), shape)
            .toggleable(value = checked, role = Role.Checkbox) {
                haptics.tick()
                onToggle()
            }
            .padding(Spacing.lg),
    ) {
        Column(Modifier.fillMaxWidth()) {
            IconTile(icon, container = tone.container, content = tone.content, size = 44.dp, shape = MaterialTheme.shapes.medium)
            Spacer(Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        CheckMark(checked, Modifier.align(Alignment.TopEnd))
    }
}

/**
 * One answer to a single-choice question, as a large card with a radio. Chosen, it takes a blue edge and opens to show
 * what comes next for that answer ([content]).
 */
@Composable
fun ChoiceCard(
    icon: ImageVector,
    tone: TonePair,
    title: String,
    body: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val haptics = rememberHaptics()
    val scheme = MaterialTheme.colorScheme
    val edge by animateColorAsState(if (selected) scheme.primary else LucentoTheme.extendedColors.borderDefault, tween(160), label = "choice-edge")
    val shape = MaterialTheme.shapes.large
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surfaceContainerLowest)
            .border(BorderStroke(if (selected) 1.5.dp else 1.dp, edge), shape),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .selectable(selected = selected, role = Role.RadioButton) {
                    if (!selected) haptics.tick()
                    onSelect()
                }
                .padding(Spacing.lg),
            verticalAlignment = Alignment.Top,
        ) {
            IconTile(icon, container = tone.container, content = tone.content, size = 44.dp, shape = MaterialTheme.shapes.medium)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (badge != null) {
                    Spacer(Modifier.height(Spacing.xxs))
                    Text(
                        badge,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = LucentoTheme.extendedColors.onHighlight,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(LucentoTheme.extendedColors.highlight)
                            .padding(horizontal = Spacing.sm, vertical = 2.dp),
                    )
                    Spacer(Modifier.height(Spacing.xxs))
                }
                Text(body, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(Spacing.sm))
            RadioMark(selected, Modifier.padding(top = 2.dp))
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = selected,
            enter = androidx.compose.animation.expandVertically(tween(240)) + androidx.compose.animation.fadeIn(tween(240, delayMillis = 60)),
            exit = androidx.compose.animation.shrinkVertically(tween(200)) + androidx.compose.animation.fadeOut(tween(120)),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                content = content,
            )
        }
    }
}

/** A tile in a 2×2 summary: a small uppercase label, the value, a line under it, and an edit (or lock) mark. */
@Composable
fun SummaryTile(
    label: String,
    value: String?,
    detail: String?,
    modifier: Modifier = Modifier,
    marker: ImageVector? = null,
    markerTint: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    extra: @Composable (() -> Unit)? = null,
) {
    val shape = MaterialTheme.shapes.large
    Box(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick) else Modifier.semantics(mergeDescendants = true) {})
            .padding(14.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(end = 20.dp)) {
            Text(label.uppercase(), style = Hierarchy.cardOverline, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { contentDescription = label })
            Spacer(Modifier.height(Spacing.xs))
            if (value != null) {
                Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (extra != null) {
                Spacer(Modifier.height(Spacing.xs))
                extra()
            }
            if (detail != null) {
                Spacer(Modifier.height(2.dp))
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (marker != null) {
            Icon(marker, contentDescription = null, tint = markerTint, modifier = Modifier.size(18.dp).align(Alignment.TopEnd))
        }
    }
}
