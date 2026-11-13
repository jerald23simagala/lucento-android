package com.lucento.app.feature.pro.welcome

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.lucento.app.core.designsystem.component.LucentoMark
import com.lucento.app.core.designsystem.theme.AwardArtwork
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.designsystem.theme.ThemeSpec
import com.lucento.app.data.preferences.ThemeId
import com.lucento.app.domain.entitlement.ProTourStop
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** A perk card's corners: the same on both faces, so the turn reads as one object. */
internal val PerkCardShape: Shape = RoundedCornerShape(22.dp)

/**
 * A card's back: the cards' blue, rings rising from below, and the approved Lucento symbol on an ivory disc (shown as
 * supplied, never recoloured), with "Lucento Pro" under it, except while the deck's band says it ([showLabel] false: at
 * large text the band would cover it). Decorative: TalkBack hears the cards only once they turn.
 */
@Composable
internal fun PerkCardBack(modifier: Modifier = Modifier, showLabel: Boolean = true) {
    Box(
        modifier
            .clip(PerkCardShape)
            .border(1.dp, AwardArtwork.GoldLight.copy(alpha = 0.4f), PerkCardShape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.linearGradient(listOf(AwardArtwork.CardBlue, AwardArtwork.CardBlueDeep), start = Offset.Zero, end = Offset(size.width * 0.6f, size.height)))
            drawRect(Brush.radialGradient(listOf(AwardArtwork.GlowMoney.copy(alpha = 0.34f), Color.Transparent), center = Offset(size.width / 2f, size.height * 1.08f), radius = size.width * 0.75f))
            rings(this)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 36.dp)) {
            Box(
                Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(AwardArtwork.Paper),
                contentAlignment = Alignment.Center,
            ) { LucentoMark(size = 52.dp, animate = false) }
            if (showLabel) {
                Spacer(Modifier.height(Spacing.md))
                Text(
                    "LUCENTO PRO",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.24.em),
                    color = AwardArtwork.GoldHighlight,
                )
            }
        }
    }
}

/** The art's band is never smaller than its large-text proportions (300 : 84). */
private const val ArtLeastAspect = 300f / 84f

/**
 * Nor taller than 0.55 of the card's width: every card gets the same art window, which Lucento and the prop fill
 * (owner, 2026-11-13: "Same-size art window", after the critic measured 31–45 % of taller bands left as empty blue).
 */
private const val ArtMostShare = 0.55f

/**
 * A card's face: its scene, then what Pro opened in the order a person needs it at any text size (the critic's round 2):
 * the title, what changed (free plan first), how long an online service is included, where it lives in the app, and
 * last the sentence that explains it. The art has one window on every card (at most 0.55 of the card's width, so
 * Lucento and the prop fill it); it gives up height only when the words need it (never below 300 : 84) and gives way at
 * the largest text. A card with few words ends with plain card under its words. Beyond the card's height the words
 * scroll, with a "More" pill on a solid band to say so.
 */
@Composable
internal fun PerkCardFront(stop: ProTourStop, copy: ProWelcomeCopy.StopCopy, modifier: Modifier = Modifier) {
    val withArt = LocalDensity.current.fontScale < 1.75f
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val surface = MaterialTheme.colorScheme.surfaceContainerLowest
    Surface(
        shape = PerkCardShape,
        color = surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Box {
            Layout(
                content = {
                    if (withArt) PerkArt(stop)
                    Box(Modifier.verticalScroll(scroll)) { CardWords(copy) }
                },
            ) { measurables, constraints ->
                val w = constraints.maxWidth
                val h = constraints.maxHeight
                val words = measurables.last()
                val art = if (withArt) {
                    val least = (w / ArtLeastAspect).roundToInt()
                    val most = (w * ArtMostShare).roundToInt().coerceAtLeast(least)
                    measurables.first().measure(Constraints.fixed(w, (h - words.maxIntrinsicHeight(w)).coerceIn(least, most)))
                } else {
                    null
                }
                val top = art?.height ?: 0
                val text = words.measure(Constraints(minWidth = w, maxWidth = w, minHeight = 0, maxHeight = (h - top).coerceAtLeast(0)))
                layout(w, h) {
                    art?.place(0, 0)
                    text.place(0, top)
                }
            }
            if (scroll.canScrollForward) {
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .background(Brush.verticalGradient(listOf(surface.copy(alpha = 0f), surface))),
                    )
                    // "More", on a solid band so it never sits over words (critic round 3): it scrolls the rest into
                    // view. TalkBack reads the whole card anyway, so it isn't spoken.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(surface)
                            .padding(bottom = Spacing.xs),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .clickable { scope.launch { scroll.animateScrollTo(scroll.maxValue) } }
                                .clearAndSetSemantics {}
                                .padding(horizontal = Spacing.md),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("More", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The card's words, in reading order: chapter, title, what changed, the term, where it lives, the sentence. */
@Composable
private fun CardWords(copy: ProWelcomeCopy.StopCopy) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.md, bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(copy.chapter, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            copy.title,
            // Even lines ("Add every account / you use", never a last word alone).
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, lineBreak = LineBreak.Heading),
            modifier = Modifier.semantics { heading() },
        )
        copy.status?.let { ServiceStatus(it) }
        if (copy.changes.isNotEmpty()) Changes(copy.changes)
        copy.term?.let { Term(it) }
        if (copy.themes) ThemeSwatches()
        Row(Modifier.padding(top = Spacing.xs), verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Outlined.Place,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp).size(18.dp),
            )
            Spacer(Modifier.width(Spacing.xs))
            Text(copy.where, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
        }
        Text(copy.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** What Pro changed, free plan first. At large text the values wrap under their name instead of running off the card. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Changes(changes: List<ProWelcomeCopy.Change>) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        changes.forEach { change ->
            FlowRow(
                Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) { contentDescription = ProWelcomeCopy.spokenChange(change) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                Text(change.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = Spacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(change.before, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.xs).size(14.dp),
                    )
                    Text(
                        change.after,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** How long an online service is included ("Included until Nov 12, 2027"), on a warm chip. */
@Composable
private fun Term(text: String) {
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Event, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(Spacing.xs))
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(lineBreak = LineBreak.Heading),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/**
 * Where an online service stands ("Off · needs your Lucento account", "On"), right under the card's title, so its
 * condition is seen before its numbers at any text size. Said in words; the icon only repeats them.
 */
@Composable
private fun ServiceStatus(state: ProWelcomeCopy.ServiceState) {
    val on = state == ProWelcomeCopy.ServiceState.ON
    val tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            when (state) {
                ProWelcomeCopy.ServiceState.ON -> Icons.Outlined.CheckCircle
                ProWelcomeCopy.ServiceState.WAITING -> Icons.Outlined.Schedule
                else -> Icons.Outlined.Info
            },
            contentDescription = null,
            tint = tint,
            modifier = Modifier.padding(top = 2.dp).size(16.dp),
        )
        Spacer(Modifier.width(Spacing.xs))
        // Even lines: "Off · needs your / Lucento account", never a last word alone.
        Text(state.text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, lineBreak = LineBreak.Heading), color = tint)
    }
}

/** Grove, Mono and Aurora as a legend: a dot of each theme's colours beside its name (a picture, not buttons). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeSwatches() {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = Modifier.clearAndSetSemantics {},
    ) {
        listOf(ThemeId.GROVE, ThemeId.MONO, ThemeId.AURORA).forEach { id ->
            val spec = ThemeSpec.of(id)
            val colors = spec.colors(dark)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(12.dp).clip(CircleShape)) {
                    drawRect(colors.scheme.primary, size = size.copy(width = size.width / 2f))
                    drawRect(colors.extended.accent, topLeft = Offset(size.width / 2f, 0f), size = size.copy(width = size.width / 2f))
                }
                Spacer(Modifier.width(Spacing.xs))
                Text(spec.displayName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
