package com.lucento.app.feature.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import com.lucento.app.core.designsystem.component.SheetHeader
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.lucento.app.BuildConfig
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.LucentoBottomSheet
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.component.NavRow
import com.lucento.app.core.designsystem.component.ProfileAvatar
import com.lucento.app.core.designsystem.component.FieldHeader
import com.lucento.app.core.designsystem.theme.Sizes
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.data.preferences.ThemeId
import com.lucento.app.data.preferences.ThemeMode
import com.lucento.app.data.preferences.UserPreferences
import com.lucento.app.data.preferences.UserPreferencesRepository
import com.lucento.app.domain.Feature
import kotlinx.coroutines.launch

/** Where the profile menu can take the user. */
enum class ProfileDestination {
    SETTINGS,
    FEATURES,

    /** Settings › Lucento Shared. */
    SHARED,

    /** Test builds only: Settings › Account & sync, opening Google's account picker straight away. */
    SWITCH_ACCOUNT,

    /** The Lucento Pro page. */
    PRO,
}

/**
 * The profile photo and what can be done with it: pick one from the phone, use the signed-in Google account's picture
 * ([googleAvailable] only then — it is downloaded once, on this tap), or remove it. [busy] while one is being saved.
 */
data class ProfilePhotoState(
    val image: ImageBitmap? = null,
    val googleAvailable: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val onPick: (Uri) -> Unit = {},
    val onUseGoogle: () -> Unit = {},
    val onRemove: () -> Unit = {},
)

/**
 * Profile and account menu, opened from the avatar on every primary screen. Lucento has no account: the "profile"
 * is an optional name and the user's preferences, all stored on this device. Settings lives here rather than in the
 * navigation dock.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSheet(
    preferences: UserPreferences,
    onSaveName: (String) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onThemeId: (ThemeId) -> Unit,
    onOpen: (ProfileDestination) -> Unit,
    onDismiss: () -> Unit,
    signedInEmail: String? = null,
    photo: ProfilePhotoState = ProfilePhotoState(),
    /** Lucento Pro: themes this plan may not pick (marked, explained, never applied). */
    lockedThemes: Set<ThemeId> = emptySet(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var editingName by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf(preferences.displayName.orEmpty()) }
    val inset = remember { Sizes.iconTile + Spacing.md + Spacing.xs }

    fun open(destination: ProfileDestination) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            onOpen(destination)
        }
    }

    fun saveName() {
        onSaveName(name)
        editingName = false
    }

    LucentoBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            val entitlements = com.lucento.app.feature.pro.LocalEntitlements.current
            val now = System.currentTimeMillis()
            val member = com.lucento.app.feature.pro.ProCopy.memberMark(entitlements, now)
            SheetHeader(
                title = preferences.displayName ?: "Your Lucento",
                subtitle = profileAccountLine(signedInEmail),
                leading = { PhotoAvatar(preferences.displayName, photo) },
                titleBadge = member?.let { { com.lucento.app.core.designsystem.component.MemberChip(it) } },
                trailing = if (!editingName) {
                    {
                        IconButton(onClick = { editingName = true }) {
                            Icon(Icons.Outlined.Edit, contentDescription = if (preferences.displayName == null) "Add your name" else "Edit your name")
                        }
                    }
                } else {
                    null
                },
            )
            photo.message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = Spacing.xs))
            }
            if (editingName) {
                Spacer(Modifier.height(Spacing.md))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(UserPreferencesRepository.MAX_DISPLAY_NAME) },
                    label = { Text("Your name (optional)") },
                    supportingText = { Text("Shown on your profile button. Leave empty to remove it.") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { saveName() }),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                    LucentoTextButton(text = "Cancel", onClick = {
                        name = preferences.displayName.orEmpty()
                        editingName = false
                    }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LucentoTextButton(text = "Save", onClick = { saveName() })
                }
            }

            FieldHeader("Appearance", Modifier.padding(top = Spacing.lg))
            ThemePicker(
                selected = preferences.themeId,
                mode = preferences.themeMode,
                onTheme = onThemeId,
                onMode = onTheme,
                lockedThemes = lockedThemes,
            )

            FieldHeader("Account and app", Modifier.padding(top = Spacing.lg))
            if (com.lucento.app.feature.pro.ProCopy.offersPro(entitlements, now)) {
                // Free plan: the Lucento Pro card takes the plan row's place (owner, 2026-11-09).
                com.lucento.app.feature.pro.ProInviteCard(onOpen = { open(ProfileDestination.PRO) }, modifier = Modifier.padding(vertical = Spacing.xs))
            } else {
                NavRow(
                    icon = Icons.Outlined.WorkspacePremium,
                    title = "Lucento Plan",
                    summary = com.lucento.app.feature.pro.ProCopy.planStatus(entitlements, now).title,
                    onClick = { open(ProfileDestination.PRO) },
                    // A member's plan tile is struck in the same gold as their coin.
                    tone = if (member != null) {
                        com.lucento.app.core.designsystem.theme.TonePair(
                            com.lucento.app.core.designsystem.theme.AwardArtwork.Gold,
                            com.lucento.app.core.designsystem.theme.AwardArtwork.Engraving,
                        )
                    } else {
                        null
                    },
                )
            }
            Hairline(inset = inset)
            NavRow(
                icon = Icons.Outlined.Settings,
                title = "Settings",
                summary = "Currency ${preferences.primaryCurrency} · export · privacy",
                onClick = { open(ProfileDestination.SETTINGS) },
            )
            Hairline(inset = inset)
            NavRow(
                icon = Icons.Outlined.ToggleOn,
                title = "Enabled features",
                summary = "${Feature.entries.count { preferences.features.isEnabled(it) }} of ${Feature.entries.size} on · turn modules on or off",
                onClick = { open(ProfileDestination.FEATURES) },
            )
            Hairline(inset = inset)
            val sharedWith = com.lucento.app.feature.sharedspace.LocalSharedAccess.current.sharedWith
            NavRow(
                icon = Icons.Outlined.Group,
                title = "Lucento Shared",
                summary = sharedWith?.let { "Shared with $it" } ?: "Share your finances with someone",
                onClick = { open(ProfileDestination.SHARED) },
            )
            // Test builds only: straight to Google's account picker (Settings › Account & sync › Switch account).
            if (BuildConfig.DEBUG) {
                Hairline(inset = inset)
                NavRow(
                    icon = Icons.Outlined.SwapHoriz,
                    title = "Switch account",
                    summary = "Test builds · pick another Google account on this phone",
                    onClick = { open(ProfileDestination.SWITCH_ACCOUNT) },
                )
            }
            // Lucento's public pages open in the browser only on a tap; sharing goes through the phone's own share sheet.
            val context = androidx.compose.ui.platform.LocalContext.current
            fun browse(url: String) {
                runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))) }
            }
            FieldHeader("Community", Modifier.padding(top = Spacing.lg))
            NavRow(
                icon = Icons.Outlined.Forum,
                title = "Lucento Community",
                summary = "Tips, questions and updates on Facebook",
                onClick = { browse(com.lucento.app.core.LucentoLinks.COMMUNITY) },
            )
            Hairline(inset = inset)
            NavRow(
                icon = Icons.Outlined.Language,
                title = "Lucento website",
                summary = "lucentoapp.com",
                onClick = { browse(com.lucento.app.core.LucentoLinks.WEBSITE) },
            )
            Hairline(inset = inset)
            NavRow(
                icon = Icons.Outlined.Share,
                title = "Share Lucento",
                summary = "Tell a friend about Lucento",
                onClick = {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, com.lucento.app.core.LucentoLinks.shareMessage())
                    }
                    runCatching { context.startActivity(android.content.Intent.createChooser(send, "Share Lucento")) }
                },
            )
            Spacer(Modifier.height(Spacing.lg))
            Text(
                "Lucento ${BuildConfig.VERSION_NAME} · Financial clarity without the complexity.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

/**
 * The profile avatar with a small camera badge. A tap opens the photo menu: choose from the phone's photos (the system
 * picker, no permission needed), use the Google account's picture when signed in with Google (downloaded once, now), or
 * remove the photo. Shows progress while a photo is being saved.
 */
@Composable
private fun PhotoAvatar(name: String?, photo: ProfilePhotoState) {
    var menu by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(photo.onPick) }
    val colors = MaterialTheme.colorScheme
    Box {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = "Change profile photo", enabled = !photo.busy) { menu = true }
                .semantics { contentDescription = if (photo.image != null) "Profile photo. Change it" else "Add a profile photo" },
            contentAlignment = Alignment.Center,
        ) {
            ProfileAvatar(name, size = 64.dp, photo = photo.image)
            if (photo.busy) {
                Box(Modifier.matchParentSize().background(colors.scrim.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = colors.onPrimary, strokeWidth = 2.5.dp)
                }
            }
        }
        // The badge says the picture can be changed; the whole avatar is the touch target.
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(24.dp)
                .clip(CircleShape)
                .background(colors.primary)
                .border(2.dp, colors.surfaceContainerLow, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.PhotoCamera, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(13.dp))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Choose from photos") },
                leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, contentDescription = null) },
                onClick = {
                    menu = false
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            )
            if (photo.googleAvailable) {
                DropdownMenuItem(
                    text = {
                        Column {
                            Text("Use my Google photo")
                            Text(
                                "Downloaded once from Google, kept on this phone",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    },
                    leadingIcon = { Icon(Icons.Outlined.AccountCircle, contentDescription = null) },
                    onClick = {
                        menu = false
                        photo.onUseGoogle()
                    },
                )
            }
            if (photo.image != null) {
                DropdownMenuItem(
                    text = { Text("Remove photo", color = colors.error) },
                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = colors.error) },
                    onClick = {
                        menu = false
                        photo.onRemove()
                    },
                )
            }
        }
    }
}

/** The line under the profile name: the signed-in account, or the local-only promise when there is none (QA R6-08). */
internal fun profileAccountLine(signedInEmail: String?): String = when {
    signedInEmail == null -> "No account needed · data stays on this phone unless you turn sync on"
    signedInEmail.isBlank() -> "Signed in · cloud sync account"
    else -> "Signed in as $signedInEmail"
}
