package com.lucento.app.feature.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.SegmentedToggle
import com.lucento.app.core.designsystem.component.rememberHaptics
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.designsystem.theme.ThemeSpec
import com.lucento.app.core.designsystem.theme.isDark
import com.lucento.app.data.preferences.ThemeId
import com.lucento.app.data.preferences.ThemeMode

/**
 * Appearance: the curated themes as a row of previews, then light / dark. Each preview is drawn from that theme's own
 * tokens (its background, hero panel, primary and accent, over its landscape) in the mode the user would see, so the
 * choice is what you get. Picking applies at once — the whole app cross-fades, nothing restarts. A dark-only theme
 * (Midnight) sets the mode aside without changing it, and says so.
 */
@Composable
fun ThemePicker(
    selected: ThemeId,
    mode: ThemeMode,
    onTheme: (ThemeId) -> Unit,
    onMode: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
    /** Lucento Pro: themes this plan may not pick; marked, explained when tapped, never applied. */
    lockedThemes: Set<ThemeId> = emptySet(),
) {
    val haptics = rememberHaptics()
    val modeIsDark = mode.isDark()
    val current = ThemeSpec.of(selected)
    // A Pro theme tried on: Lucento shows it while the sheet is open, and nothing is saved (owner, 2026-11-19).
    val trial = LocalThemeTrial.current
    val trying = trial.trying.collectAsState().value
    // The trial lasts only while the picker is there (the sheet closed, signed out, Lucento left).
    androidx.compose.runtime.DisposableEffect(trial) { onDispose { trial.end() } }
    // A theme picked during a trial shows at once and stays until the saved value catches up: nothing flashes between.
    androidx.compose.runtime.LaunchedEffect(selected, trying) { if (trying != null && trying == selected) trial.end() }
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            ThemeSpec.all.forEach { spec ->
                ThemePreview(
                    spec = spec,
                    dark = spec.isDark(modeIsDark),
                    selected = spec.id == selected,
                    locked = spec.id in lockedThemes,
                    trying = spec.id == trying && spec.id in lockedThemes,
                    onSelect = {
                        when {
                            // The saved theme (one kept from Pro too) only ends a trial.
                            spec.id == selected -> trial.end()
                            spec.id in lockedThemes -> {
                                haptics.tick()
                                trial.start(spec.id)
                            }
                            else -> {
                                haptics.tick()
                                if (trying != null) trial.start(spec.id)
                                // A theme of one's own replaces one waiting for a purchase.
                                trial.clearAfterPurchase()
                                onTheme(spec.id)
                            }
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            // While a theme is tried on, its own line: the app is wearing it.
            (trying?.takeIf { it in lockedThemes }?.let { ThemeSpec.of(it) } ?: current).character,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val tried = trying?.takeIf { it in lockedThemes }
        val openPro = com.lucento.app.feature.pro.LocalOpenPro.current
        val reducedMotion = com.lucento.app.core.designsystem.theme.LucentoTheme.reducedMotion
        androidx.compose.animation.AnimatedVisibility(
            visible = tried != null,
            enter = if (reducedMotion) androidx.compose.animation.EnterTransition.None
            else androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
            exit = if (reducedMotion) androidx.compose.animation.ExitTransition.None
            else androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut(),
        ) {
            // The last theme tried stays named while the note folds away.
            val shown = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(tried) }
            if (tried != null) shown.value = tried
            shown.value?.let { id ->
                ThemeTrialNote(
                    theme = ThemeSpec.of(id).displayName,
                    saved = current.displayName,
                    onSeePlan = {
                        // Bought from here, Pro brings this theme with it (owner, 2026-11-19).
                        trial.keepAfterPurchase(id)
                        openPro(com.lucento.app.domain.entitlement.ProFeature.EXTRA_THEMES)
                    },
                    onDone = { trial.end() },
                )
            }
        }
        Spacer(Modifier.height(Spacing.md))
        if (current.darkOnly) {
            InfoNote("${current.displayName} is always dark. Your light or dark choice comes back when you pick another theme.")
        } else {
            SegmentedToggle(
                options = listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark"),
                selected = mode,
                onSelect = onMode,
            )
        }
    }
}

/** Under the themes while one is tried on: the plan fact, that nothing is saved, and the way to the plan page. */
@Composable
private fun ThemeTrialNote(theme: String, saved: String, onSeePlan: () -> Unit, onDone: () -> Unit) {
    val plan = com.lucento.app.feature.pro.LocalEntitlements.current.displayPlan(System.currentTimeMillis())
    com.lucento.app.core.designsystem.component.LucentoCard(
        Modifier
            .fillMaxWidth()
            .padding(top = Spacing.sm),
        contentPadding = Spacing.md,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.WorkspacePremium, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Spacing.sm))
            Text(
                com.lucento.app.feature.pro.ProCopy.wallTitle(
                    com.lucento.app.feature.pro.ProWall(com.lucento.app.domain.entitlement.ProFeature.EXTRA_THEMES, subject = theme),
                    plan,
                ),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .weight(1f)
                    .semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            com.lucento.app.feature.pro.ProCopy.themeTrialBody(saved),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.sm))
        com.lucento.app.core.designsystem.component.LucentoSecondaryButton(
            text = com.lucento.app.feature.pro.ProCopy.WALL_SEE_PLAN,
            onClick = onSeePlan,
            modifier = Modifier.fillMaxWidth(),
        )
        com.lucento.app.core.designsystem.component.LucentoTextButton(
            text = com.lucento.app.feature.pro.ProCopy.THEME_TRIAL_DONE,
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private val PreviewWidth = 104.dp
private val PreviewArtHeight = 72.dp

/** One theme as a small picture of itself: its landscape, a hero bar and two action swatches, then its name. */
@Composable
private fun ThemePreview(
    spec: ThemeSpec,
    dark: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
    locked: Boolean = false,
    /** Tried on, not saved (owner, 2026-11-19): ringed and marked "Trying"; the saved theme keeps its check. */
    trying: Boolean = false,
) {
    val colors = spec.colors(dark)
    val ui = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    val description = buildString {
        append(spec.displayName)
        append(" theme. ")
        if (trying) append("Trying, not saved. ") else if (locked) append("Lucento Pro feature. ")
        append(spec.character)
    }
    Column(
        Modifier
            .width(PreviewWidth)
            .clip(shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            // One radio item for TalkBack: "Grove theme. Sage paper, …, selected".
            .clearAndSetSemantics { contentDescription = description }
            .padding(bottom = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(PreviewArtHeight)
                .clip(shape)
                .background(colors.scheme.background)
                .border(
                    if (selected || trying) BorderStroke(2.dp, ui.primary) else BorderStroke(1.dp, ui.outlineVariant),
                    shape,
                ),
        ) {
            Image(
                painter = painterResource(spec.scenery),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = BiasAlignment(horizontalBias = 0.3f, verticalBias = 0.55f),
                alpha = if (dark && !spec.sceneryIsNight) 0.55f else 1f,
                modifier = Modifier.matchParentSize(),
            )
            if (trying) {
                com.lucento.app.core.designsystem.component.StatusPill(
                    text = "Trying",
                    tone = com.lucento.app.core.designsystem.component.StatusTones.brand,
                    modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.xs),
                )
            } else if (locked) {
                com.lucento.app.feature.pro.ProBadge(Modifier.align(Alignment.TopEnd).padding(Spacing.xs))
            }
            // A miniature of the theme: its hero panel with a figure bar, and its primary + accent as two dots.
            Column(Modifier.align(Alignment.BottomStart).padding(Spacing.xs)) {
                Box(
                    Modifier
                        .width(56.dp)
                        .height(18.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Brush.linearGradient(listOf(colors.extended.hero, colors.extended.heroEnd))),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(
                        Modifier
                            .padding(start = 6.dp)
                            .width(28.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(colors.extended.onHero),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Swatch(colors.scheme.primary)
                    Swatch(colors.extended.accent)
                }
            }
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(ui.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = ui.onPrimary, modifier = Modifier.size(14.dp))
                }
            }
            if (spec.darkOnly) {
                Icon(
                    Icons.Outlined.DarkMode,
                    contentDescription = null,
                    tint = colors.extended.accent,
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(14.dp),
                )
            }
        }
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            spec.displayName,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) ui.primary else ui.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Swatch(color: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f), CircleShape),
    )
}
