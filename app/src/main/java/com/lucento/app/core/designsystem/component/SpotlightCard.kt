package com.lucento.app.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Sizes
import com.lucento.app.core.designsystem.theme.Spacing
import kotlin.math.roundToInt

/**
 * The card for a moment that deserves attention — another person inviting you in — on the theme's hero panel, with the
 * theme's landscape along the bottom ([HeroHorizon]). A sunlight pill names what it is, a [visual] (e.g. [PeoplePair])
 * sits beside it, then the title, one line of context, an optional [meta] and [note], and one clear action in the
 * theme's accent. Routine notices stay [InfoNote]s: a page shows at most one of these at a time.
 */
@Composable
fun SpotlightCard(
    eyebrow: String,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    note: String? = null,
    visual: (@Composable () -> Unit)? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    dismissLabel: String? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val ext = LucentoTheme.extendedColors
    val haptics = rememberHaptics()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = ext.hero,
        contentColor = ext.onHero,
    ) {
        Column(Modifier.heroPanel()) {
            Column(Modifier.padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.lg, bottom = Spacing.xs)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    HeroPill(eyebrow, HeroTones.sunlight)
                    Spacer(Modifier.weight(1f))
                    visual?.invoke()
                }
                Spacer(Modifier.height(Spacing.md))
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = ext.onHero,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = ext.onHeroMuted)
                meta?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = ext.onHeroMuted)
                }
                note?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = ext.onHero)
                }
                if (actionLabel != null && onAction != null) {
                    Spacer(Modifier.height(Spacing.md))
                    Button(
                        onClick = {
                            haptics.confirm()
                            onAction()
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.button),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(containerColor = ext.accent, contentColor = ext.onAccent),
                    ) {
                        Text(actionLabel, style = MaterialTheme.typography.labelLarge)
                    }
                }
                if (dismissLabel != null && onDismiss != null) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.minTouch),
                        colors = ButtonDefaults.textButtonColors(contentColor = ext.onHero),
                    ) {
                        Text(dismissLabel, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            HeroHorizon()
        }
    }
}

/**
 * Two people side by side — the [first] in the accent, the [second] (usually "you") in the hero ink — for a
 * [SpotlightCard]. Initials, or a person glyph when a name is unknown. The second slides in beside the first once, unless
 * the system asks for reduced motion. Decorative: the card's text says who is involved.
 */
@Composable
fun PeoplePair(first: String?, second: String?, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val ext = LucentoTheme.extendedColors
    val reducedMotion = LucentoTheme.reducedMotion
    val slide = remember { Animatable(if (reducedMotion) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!reducedMotion) slide.animateTo(0f, tween(durationMillis = 450, easing = FastOutSlowInEasing))
    }
    val overlap = size * 0.3f
    Box(modifier) {
        PersonDisc(first, size, container = ext.accent, content = ext.onAccent, ring = ext.hero)
        PersonDisc(
            second,
            size,
            container = ext.onHero,
            content = ext.hero,
            ring = ext.hero,
            modifier = Modifier
                .padding(start = size - overlap)
                .offset { IntOffset((slide.value * 14.dp.toPx()).roundToInt(), 0) },
        )
    }
}

@Composable
private fun PersonDisc(name: String?, size: Dp, container: Color, content: Color, ring: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(container)
            .border(2.dp, ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val initials = initialsOf(name)
        if (initials != null) {
            Text(initials, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
        } else {
            Icon(Icons.Filled.Person, contentDescription = null, tint = content, modifier = Modifier.size(size * 0.55f))
        }
    }
}
