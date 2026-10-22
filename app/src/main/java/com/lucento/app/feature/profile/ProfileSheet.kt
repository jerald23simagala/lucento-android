package com.lucento.app.feature.profile

import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.Settings
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
import com.lucento.app.core.designsystem.component.SectionHeader
import com.lucento.app.core.designsystem.component.SegmentedToggle
import com.lucento.app.core.designsystem.theme.Sizes
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.data.preferences.ThemeMode
import com.lucento.app.data.preferences.UserPreferences
import com.lucento.app.data.preferences.UserPreferencesRepository
import com.lucento.app.domain.Feature
import kotlinx.coroutines.launch

/** Where the profile menu can take the user. */
enum class ProfileDestination { SETTINGS, FEATURES }

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
    onOpen: (ProfileDestination) -> Unit,
    onDismiss: () -> Unit,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfileAvatar(preferences.displayName, size = 56.dp)
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        preferences.displayName ?: "Your Lucento",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        "No account needed · everything stays on this phone",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!editingName) {
                    IconButton(onClick = { editingName = true }) {
                        Icon(Icons.Outlined.Edit, contentDescription = if (preferences.displayName == null) "Add your name" else "Edit your name")
                    }
                }
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
                    shape = MaterialTheme.shapes.medium,
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

            SectionHeader("Appearance", Modifier.padding(top = Spacing.lg))
            SegmentedToggle(
                options = listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark"),
                selected = preferences.themeMode,
                onSelect = onTheme,
            )

            SectionHeader("Account and app", Modifier.padding(top = Spacing.lg))
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
