package com.lucento.app.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.Group
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * What the profile button shows and does; provided once by the app shell for every primary screen. [sharedWith]: the
 * other person when this ledger is shared (Lucento Shared) — the button then carries the "Shared" pill. The Pro mark on
 * the button is the [proRing] alone (owner, 2026-11-14: one mark, the one Pro's welcome lands on).
 */
data class ProfileChrome(
    val displayName: String?,
    val photo: ImageBitmap? = null,
    val sharedWith: String? = null,
    /** Lucento Pro: the picture wears the Pro ring while Pro is active (the medal itself is kept for good). */
    val proRing: Boolean = false,
    /** Bumped when the Pro badge lands on the picture at the end of Pro's welcome: the ring answers with one pulse. */
    val proRingLanded: Int = 0,
    val onOpen: () -> Unit,
)

val LocalProfileChrome = compositionLocalOf<ProfileChrome?> { null }

/** Up to two initials from a display name ("Jerald Simagala" → "JS"), or null for a blank name. */
fun initialsOf(name: String?): String? {
    val words = name?.trim()?.split(Regex("\\s+"))?.filter { it.isNotEmpty() }.orEmpty()
    if (words.isEmpty()) return null
    val letters = (if (words.size == 1) listOf(words[0]) else listOf(words.first(), words.last()))
        .mapNotNull { w -> w.firstOrNull { it.isLetterOrDigit() } }
    return letters.joinToString("").uppercase(Locale.getDefault()).ifEmpty { null }
}

/** Circular avatar: the user's [photo] when they set one, else their initials (or a person glyph). Decorative; wrap it for semantics. */
@Composable
fun ProfileAvatar(name: String?, modifier: Modifier = Modifier, size: Dp = 36.dp, photo: ImageBitmap? = null) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier.size(size).clip(CircleShape).background(colors.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        val initials = initialsOf(name)
        if (photo != null) {
            Image(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else if (initials != null) {
            Text(
                initials,
                style = if (size >= 56.dp) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelLarge,
                color = colors.onPrimaryContainer,
                maxLines = 1,
            )
        } else {
            Icon(Icons.Filled.Person, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(size * 0.58f))
        }
    }
}

/**
 * The top-bar profile button on primary screens. Opens the profile and account menu (where Settings lives).
 * Renders nothing outside the app shell (e.g. in previews), so screens can always include it.
 */
@Composable
fun ProfileButton(modifier: Modifier = Modifier) {
    val chrome = LocalProfileChrome.current ?: return
    val haptics = rememberHaptics()
    Box(modifier.size(48.dp).spotlightTarget(SpotlightKeys.PROFILE)) {
        Box(
            Modifier
                .matchParentSize()
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = "Open profile and settings") {
                    haptics.tick()
                    chrome.onOpen()
                }
                .semantics {
                    contentDescription = chrome.sharedWith?.let { "Profile and settings. Shared with $it" } ?: "Profile and settings"
                    // The Pro ring in words: TalkBack reads "Profile and settings, Lucento Pro".
                    if (chrome.proRing) stateDescription = "Lucento Pro"
                },
            contentAlignment = Alignment.Center,
        ) {
            ProfileAvatar(chrome.displayName, photo = chrome.photo)
        }
        if (chrome.proRing) ProRing(chrome.proRingLanded, Modifier.matchParentSize())
        // Outside the circular clip, so the pill isn't cut; it takes no touches, so taps on it still open the menu.
        if (chrome.sharedWith != null) SharedPill(Modifier.align(Alignment.BottomCenter).offset(y = 3.dp))
    }
}

/**
 * Lucento Pro's ring: struck gold around the picture, with a small crest at its shoulder (so it isn't colour alone; the
 * button's state reads "Lucento Pro"). When the welcome's badge lands ([landed] goes up) it pulses once; under
 * reduced motion it is simply there. Takes no touches.
 */
@Composable
private fun ProRing(landed: Int, modifier: Modifier = Modifier) {
    val reduced = com.lucento.app.core.designsystem.theme.LucentoTheme.reducedMotion
    val pulse = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(landed) {
        if (landed > 0 && !reduced) {
            pulse.snapTo(1f)
            pulse.animateTo(0f, androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        }
    }
    val gold = com.lucento.app.core.designsystem.theme.AwardArtwork
    // On a light page the gold alone is faint (about 1.5:1): a hairline of the medal's deep edge outlines it there.
    val light = MaterialTheme.colorScheme.background.luminance() > 0.5f
    Box(modifier) {
        androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
            val c = center
            val ring = 19.5.dp.toPx()
            val glow = pulse.value
            if (glow > 0f) {
                drawCircle(gold.GoldLight.copy(alpha = 0.45f * glow), radius = ring + 6.dp.toPx() * (1f - glow) + 2.dp.toPx(), center = c)
            }
            drawCircle(
                androidx.compose.ui.graphics.Brush.sweepGradient(
                    listOf(gold.GoldHighlight, gold.GoldLight, gold.Gold, gold.GoldDeep, gold.GoldLight, gold.GoldHighlight),
                    center = c,
                ),
                radius = ring,
                center = c,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx() * (1f + 0.4f * glow)),
            )
            if (light) {
                val outline = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                drawCircle(gold.GoldEdge, radius = ring + 1.75.dp.toPx(), center = c, style = outline)
                drawCircle(gold.GoldEdge, radius = ring - 1.75.dp.toPx(), center = c, style = outline)
            }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .testTag("pro-badge")
                .size(16.dp)
                .clip(CircleShape)
                .background(if (light) gold.GoldEdge else MaterialTheme.colorScheme.background)
                .padding(1.5.dp)
                .clip(CircleShape)
                .background(gold.Gold),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.WorkspacePremium,
                contentDescription = null,
                tint = gold.Engraving,
                modifier = Modifier.size(10.dp),
            )
        }
    }
}

/**
 * "Shared" across the foot of the avatar (owner, 2026-11-05: a small pill by the profile, not a name — it stays short
 * however many people share). In the accent, ringed in the page colour so it stands apart from the photo. At large font
 * sizes it becomes the group glyph alone, so it never spills over the neighbouring buttons.
 */
@Composable
private fun SharedPill(modifier: Modifier = Modifier) {
    val ext = com.lucento.app.core.designsystem.theme.LucentoTheme.extendedColors
    val compact = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
    Box(
        modifier
            // May be a few dp wider than the 48dp button ("Shared" was cut to "Share" when held to its width).
            .wrapContentWidth(unbounded = true)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.background)
            .padding(1.5.dp)
            .clip(RoundedCornerShape(50))
            .background(ext.accent)
            .padding(horizontal = if (compact) 3.dp else 5.dp, vertical = 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (compact) {
            Icon(Icons.Outlined.Group, contentDescription = null, tint = ext.onAccent, modifier = Modifier.size(12.dp))
        } else {
            Text(
                "Shared",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = ext.onAccent,
                maxLines = 1,
            )
        }
    }
}
