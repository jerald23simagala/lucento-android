package com.lucento.app.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucento.app.R
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Sizes
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.lucento.app.core.designsystem.component.ConnectingIndicator
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.LucentoSecondaryButton
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.component.SegmentedToggle
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.data.sync.MIN_PASSWORD_LENGTH

// Google's sign-in button colours (Google Identity branding guidelines), light and dark. Brand values, not Lucento tokens.
private val GoogleLightFill = Color(0xFFFFFFFF)
private val GoogleLightStroke = Color(0xFF747775)
private val GoogleLightText = Color(0xFF1F1F1F)
private val GoogleDarkFill = Color(0xFF131314)
private val GoogleDarkStroke = Color(0xFF8E918F)
private val GoogleDarkText = Color(0xFFE3E3E3)

/**
 * "Continue with Google" as Google's branding asks: the official multi-colour G (never retinted) on a white button with
 * a hairline by day, the dark variant by night, in the system font at 14sp Medium. Same height as Lucento's buttons.
 */
@Composable
private fun GoogleSignInButton(onClick: () -> Unit) {
    val dark = LucentoTheme.isDark
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.button),
        shape = MaterialTheme.shapes.medium,
        color = if (dark) GoogleDarkFill else GoogleLightFill,
        border = BorderStroke(1.dp, if (dark) GoogleDarkStroke else GoogleLightStroke),
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.md),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(painterResource(R.drawable.ic_google_g), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Spacing.md))
            Text(
                "Continue with Google",
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = if (dark) GoogleDarkText else GoogleLightText,
                maxLines = 1,
            )
        }
    }
}

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
    /** The user chose email: a chance to open the server connection while they type (see AuthRepository.prewarm). */
    onPrepareEmail: () -> Unit = {},
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
        if (busy) {
            // Never a silent screen while the account connects: the rising mark and what is happening.
            ConnectingIndicator(
                "Connecting your account…",
                Modifier.fillMaxWidth().padding(vertical = Spacing.lg).semantics { liveRegion = LiveRegionMode.Polite },
            )
            return@Column
        }
        if (googleAvailable) {
            GoogleSignInButton(onClick = onGoogle)
        }
        if (!usingEmail) {
            LucentoSecondaryButton(text = "Use email instead", onClick = {
                usingEmail = true
                onPrepareEmail()
            })
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
                { Text("At least $MIN_PASSWORD_LENGTH characters. Checked against known breaches by sending only the first 5 characters of its hash.") }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            shape = MaterialTheme.shapes.small,
            colors = colors,
            modifier = Modifier.fillMaxWidth(),
        )
        problem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        LucentoSecondaryButton(
            text = if (mode == EmailMode.CREATE) "Create account" else "Sign in",
            onClick = {
                problem = emailFormProblem(email, password, mode)
                if (problem == null) onEmail(email.trim(), password, mode)
            },
        )
        if (googleAvailable) {
            LucentoTextButton(text = "Back to Google", onClick = { usingEmail = false })
        }
    }
}

/** A new password for the signed-in email account. Checked for length here; the breach check runs on submit. */
@Composable
fun ChangePasswordForm(busy: Boolean, onSubmit: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }
    val colors = OutlinedTextFieldDefaults.colors()
    OutlinedTextField(
        value = password,
        onValueChange = { password = it.take(128); problem = null },
        label = { Text("New password") },
        singleLine = true,
        enabled = !busy,
        visualTransformation = PasswordVisualTransformation(),
        supportingText = { Text("At least $MIN_PASSWORD_LENGTH characters. Known breached passwords are refused.") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
        shape = MaterialTheme.shapes.small,
        colors = colors,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = confirm,
        onValueChange = { confirm = it.take(128); problem = null },
        label = { Text("Confirm password") },
        singleLine = true,
        enabled = !busy,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        shape = MaterialTheme.shapes.small,
        colors = colors,
        modifier = Modifier.fillMaxWidth(),
    )
    problem?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    LucentoSecondaryButton(
        text = "Update password",
        enabled = !busy,
        onClick = {
            problem = when {
                password.length < MIN_PASSWORD_LENGTH -> "Use at least $MIN_PASSWORD_LENGTH characters for the password."
                password != confirm -> "Those passwords don’t match."
                else -> null
            }
            if (problem == null) onSubmit(password)
        },
    )
}
