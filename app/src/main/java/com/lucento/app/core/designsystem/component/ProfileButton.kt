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
import androidx.compose.material.icons.outlined.Group
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * What the profile button shows and does; provided once by the app shell for every primary screen. [sharedWith]: the
 * other person when this ledger is shared (Lucento Shared) — the button then carries the "Shared" pill. [member]: the
 * person's own Lucento Pro plan ("Pro" or "Lifetime"), shown as the [ProCrest] coin; null on the free plan.
 */
data class ProfileChrome(
    val displayName: String?,
    val photo: ImageBitmap? = null,
    val sharedWith: String? = null,
    val member: String? = null,
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
    Box(modifier.size(48.dp)) {
        Box(
            Modifier
                .matchParentSize()
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = "Open profile and settings") {
                    haptics.tick()
                    chrome.onOpen()
                }
                .semantics {
                    contentDescription = profileButtonDescription(chrome.member, chrome.sharedWith)
                },
            contentAlignment = Alignment.Center,
        ) {
            ProfileAvatar(chrome.displayName, photo = chrome.photo)
        }
        // Outside the circular clip, so the pill isn't cut; it takes no touches, so taps on it still open the menu.
        if (chrome.sharedWith != null) SharedPill(Modifier.align(Alignment.BottomCenter).offset(y = 3.dp))
        // Lucento Pro: the member's coin at the avatar's top-right, inside the same 48dp box (nothing around it moves), and
        // clear of the Shared pill at the foot. A picture, not text, so it keeps its size at large font scales.
        if (chrome.member != null) RingedCrest(MaterialTheme.colorScheme.background, Modifier.align(Alignment.TopEnd).offset(x = (-1).dp, y = 2.dp))
    }
}

/** What TalkBack says for the profile button: the plan in words, so the coin never relies on colour or shape. */
internal fun profileButtonDescription(member: String?, sharedWith: String?): String = buildString {
    append("Profile and settings")
    when (member) {
        null -> Unit
        "Pro" -> append(". Lucento Pro")
        else -> append(". Pro ").append(member)
    }
    if (sharedWith != null) append(". Shared with ").append(sharedWith)
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
