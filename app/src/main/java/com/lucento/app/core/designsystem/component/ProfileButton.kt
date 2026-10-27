package com.lucento.app.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale

/** What the profile button shows and does; provided once by the app shell for every primary screen. */
data class ProfileChrome(val displayName: String?, val onOpen: () -> Unit)

val LocalProfileChrome = compositionLocalOf<ProfileChrome?> { null }

/** Up to two initials from a display name ("Jerald Simagala" → "JS"), or null for a blank name. */
fun initialsOf(name: String?): String? {
    val words = name?.trim()?.split(Regex("\\s+"))?.filter { it.isNotEmpty() }.orEmpty()
    if (words.isEmpty()) return null
    val letters = (if (words.size == 1) listOf(words[0]) else listOf(words.first(), words.last()))
        .mapNotNull { w -> w.firstOrNull { it.isLetterOrDigit() } }
    return letters.joinToString("").uppercase(Locale.getDefault()).ifEmpty { null }
}

/** Circular avatar with the user's initials (or a person glyph). Decorative; wrap it for semantics. */
@Composable
fun ProfileAvatar(name: String?, modifier: Modifier = Modifier, size: Dp = 36.dp) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier.size(size).clip(CircleShape).background(colors.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        val initials = initialsOf(name)
        if (initials != null) {
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
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = "Open profile and settings") {
                haptics.tick()
                chrome.onOpen()
            }
            .semantics { contentDescription = "Profile and settings" },
        contentAlignment = Alignment.Center,
    ) {
        ProfileAvatar(chrome.displayName)
    }
}
