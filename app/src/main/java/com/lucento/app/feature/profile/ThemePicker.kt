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
) {
    val haptics = rememberHaptics()
    val modeIsDark = mode.isDark()
    val current = ThemeSpec.of(selected)
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
                    onSelect = {
                        if (spec.id != selected) {
                            haptics.tick()
                            onTheme(spec.id)
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            current.character,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

private val PreviewWidth = 104.dp
private val PreviewArtHeight = 72.dp

/** One theme as a small picture of itself: its landscape, a hero bar and two action swatches, then its name. */
@Composable
private fun ThemePreview(spec: ThemeSpec, dark: Boolean, selected: Boolean, onSelect: () -> Unit) {
    val colors = spec.colors(dark)
    val ui = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    val description = buildString {
        append(spec.displayName)
        append(" theme. ")
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
                    if (selected) BorderStroke(2.dp, ui.primary) else BorderStroke(1.dp, ui.outlineVariant),
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
