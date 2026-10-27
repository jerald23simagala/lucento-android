package com.lucento.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.LucentoSecondaryButton
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.component.SegmentedToggle
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.data.sync.MIN_PASSWORD_LENGTH

/** What the user is doing with an email account: making one, or signing in to an existing one. */
enum class EmailMode { CREATE, SIGN_IN }

/** Checks an email form before it reaches the server; null when it can be sent. */
fun emailFormProblem(email: String, password: String, mode: EmailMode): String? = when {
    !Regex("""[^@\s]+@[^@\s]+\.[^@\s]+""").matches(email.trim()) -> "Enter a valid email address."
    mode == EmailMode.CREATE && password.length < MIN_PASSWORD_LENGTH -> "Use at least $MIN_PASSWORD_LENGTH characters for the password."
    password.isEmpty() -> "Enter your password."
    else -> null
}

/**
 * Connecting a sync account: Google (one tap, no password), or an email and password — create an account or sign
 * in to one. Used by onboarding and Settings › Cloud sync. Nothing is sent until the user picks one.
 */
@Composable
fun ConnectAccountPanel(
    busy: Boolean,
    googleAvailable: Boolean,
    onGoogle: () -> Unit,
    onEmail: (email: String, password: String, mode: EmailMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var usingEmail by rememberSaveable { mutableStateOf(!googleAvailable) }
    var mode by rememberSaveable { mutableStateOf(EmailMode.CREATE) }
    var email by rememberSaveable { mutableStateOf("") }
    // Kept in memory only (never saved state): a password must not survive into a saved instance bundle.
    var password by androidx.compose.runtime.remember { mutableStateOf("") }
    var problem by rememberSaveable { mutableStateOf<String?>(null) }
    val colors = OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    )

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (googleAvailable) {
            LucentoPrimaryButton(text = if (busy && !usingEmail) "Connecting…" else "Continue with Google", onClick = onGoogle, enabled = !busy)
        }
        if (!usingEmail) {
            LucentoSecondaryButton(text = "Use email instead", onClick = { usingEmail = true })
            return@Column
        }
        SegmentedToggle(
            options = listOf(EmailMode.CREATE to "Create account", EmailMode.SIGN_IN to "Sign in"),
            selected = mode,
            onSelect = {
                mode = it
                problem = null
            },
            compact = true,
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it.take(254); problem = null },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            shape = MaterialTheme.shapes.small,
            colors = colors,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it.take(128); problem = null },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            supportingText = if (mode == EmailMode.CREATE) {
                { Text("At least $MIN_PASSWORD_LENGTH characters.") }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            shape = MaterialTheme.shapes.small,
            colors = colors,
            modifier = Modifier.fillMaxWidth(),
        )
        problem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        if (busy) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            LucentoSecondaryButton(
                text = if (mode == EmailMode.CREATE) "Create account" else "Sign in",
                onClick = {
                    problem = emailFormProblem(email, password, mode)
                    if (problem == null) onEmail(email.trim(), password, mode)
                },
            )
        }
        if (googleAvailable) {
            LucentoTextButton(text = "Back to Google", onClick = { usingEmail = false })
        }
    }
}
