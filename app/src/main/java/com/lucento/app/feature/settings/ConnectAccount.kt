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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.withLink
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.filled.CheckCircle

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
internal fun GoogleSignInButton(onClick: () -> Unit) {
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

/** Something@domain.tld, with no empty part in the domain ("name@gmail..com" is refused by Supabase too). */
private val EmailPattern = Regex("""[^@\s]+@[^@\s.]+(\.[^@\s.]+)+""")

/** Checks an email form before it reaches the server; null when it can be sent. */
fun emailFormProblem(email: String, password: String, mode: EmailMode): String? =
    emailProblem(email) ?: passwordProblem(password, mode)

/** The email field's own problem, or null. */
fun emailProblem(email: String): String? = when {
    email.isBlank() -> "Enter your email address."
    !EmailPattern.matches(email.trim()) -> "Enter a valid email address, like name@example.com."
    else -> null
}

/** The password field's own problem, or null. A new password needs [MIN_PASSWORD_LENGTH] characters. */
fun passwordProblem(password: String, mode: EmailMode): String? = when {
    password.isEmpty() -> if (mode == EmailMode.CREATE) "Choose a password." else "Enter your password."
    mode == EmailMode.CREATE && password.length < MIN_PASSWORD_LENGTH -> "Use at least $MIN_PASSWORD_LENGTH characters."
    else -> null
}

/** Shown under the button when a new account can be made: what agreeing means, before anyone types anything. */
const val CONSENT_REQUIRED = "Agree to the Terms of Use and Privacy Policy to create your account."

/**
 * Connecting a sync account: Google (one tap, no password), or an email and password. [emailModes] says what the email
 * form offers: creating an account, signing in, or both (with a switch). Used by onboarding's restore and invitation
 * paths and Settings › Cloud sync; a brand-new setup uses [EmailAccountForm] on its own page. Nothing is sent until the
 * user picks one.
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
    emailModes: List<EmailMode> = listOf(EmailMode.CREATE, EmailMode.SIGN_IN),
    /** The server's answer to the last attempt, shown with the form. */
    message: String? = null,
) {
    var usingEmail by rememberSaveable { mutableStateOf(!googleAvailable) }
    var legal by rememberSaveable { mutableStateOf<com.lucento.app.feature.legal.LegalDoc?>(null) }
    legal?.let { com.lucento.app.feature.legal.LegalSheet(it, onDismiss = { legal = null }) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (busy && !usingEmail) {
            // Never a silent screen while the account connects: the rising mark and what is happening.
            ConnectingIndicator(
                "Connecting your account…",
                Modifier.fillMaxWidth().padding(vertical = Spacing.lg).semantics { liveRegion = LiveRegionMode.Polite },
            )
            return@Column
        }
        if (!usingEmail) {
            if (googleAvailable) GoogleSignInButton(onClick = onGoogle)
            LucentoSecondaryButton(text = "Use email instead", onClick = {
                usingEmail = true
                onPrepareEmail()
            })
            if (googleAvailable && EmailMode.CREATE in emailModes) GoogleConsentNote(onOpen = { legal = it })
            return@Column
        }
        EmailAccountForm(
            modes = emailModes,
            busy = busy,
            message = message,
            onSubmit = onEmail,
            onOpenLegal = { legal = it },
        )
        if (googleAvailable && !busy) {
            LucentoTextButton(text = "Use Google instead", onClick = { usingEmail = false }, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * The email form: email, password (shown or hidden), and — when creating an account — the password rule as it is met
 * and agreement to the Terms of Use and Privacy Policy. Problems show on the field they belong to, after the person
 * leaves it or presses the button, never while they are still typing. The password lives in memory only.
 */
@Composable
fun EmailAccountForm(
    modes: List<EmailMode>,
    busy: Boolean,
    onSubmit: (email: String, password: String, mode: EmailMode) -> Unit,
    onOpenLegal: (com.lucento.app.feature.legal.LegalDoc) -> Unit,
    modifier: Modifier = Modifier,
    /** The server's answer to the last attempt. */
    message: String? = null,
    initialEmail: String = "",
) {
    var mode by rememberSaveable { mutableStateOf(modes.first()) }
    if (mode !in modes) mode = modes.first()
    var email by rememberSaveable { mutableStateOf(initialEmail) }
    // Kept in memory only (never saved state): a password must not survive into a saved instance bundle.
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var forgot by rememberSaveable { mutableStateOf(false) }
    var agreed by rememberSaveable { mutableStateOf(false) }
    var emailTouched by rememberSaveable { mutableStateOf(false) }
    var passwordTouched by rememberSaveable { mutableStateOf(false) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val creating = mode == EmailMode.CREATE
    val emailError = emailProblem(email).takeIf { emailTouched || submitted }
    val passwordError = passwordProblem(password, mode).takeIf { passwordTouched || submitted }
    val consentError = creating && submitted && !agreed
    // A new password is typed twice (owner test 2026-11-09); the second copy is checked once the first is acceptable.
    val confirmError = (creating && submitted && passwordError == null && confirm != password).takeIf { it }?.let { PASSWORDS_DIFFER }
    val colors = OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    )
    val submit = {
        submitted = true
        if (emailFormProblem(email, password, mode) == null && (!creating || (agreed && confirm == password)) && !busy) {
            onSubmit(email.trim(), password, mode)
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (modes.size > 1) {
            SegmentedToggle(
                options = modes.map { it to if (it == EmailMode.CREATE) "Create account" else "Sign in" },
                selected = mode,
                onSelect = {
                    mode = it
                    submitted = false
                },
                compact = true,
            )
        }
        OutlinedTextField(
            value = email,
            onValueChange = { email = it.take(254).replace("\n", "") },
            label = { Text("Email") },
            placeholder = { Text("name@example.com") },
            leadingIcon = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Email, contentDescription = null) },
            singleLine = true,
            enabled = !busy,
            isError = emailError != null,
            supportingText = emailError?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next, autoCorrectEnabled = false),
            shape = MaterialTheme.shapes.small,
            colors = colors,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusLeft { if (email.isNotEmpty()) emailTouched = true },
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it.take(128) },
            label = { Text(if (creating) "Create a password" else "Password") },
            leadingIcon = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Lock, contentDescription = null) },
            trailingIcon = {
                androidx.compose.material3.IconButton(onClick = { showPassword = !showPassword }) {
                    androidx.compose.material3.Icon(
                        if (showPassword) androidx.compose.material.icons.Icons.Outlined.VisibilityOff else androidx.compose.material.icons.Icons.Outlined.Visibility,
                        contentDescription = if (showPassword) "Hide password" else "Show password",
                    )
                }
            },
            singleLine = true,
            enabled = !busy,
            isError = passwordError != null && !creating,
            supportingText = if (!creating) passwordError?.let { { Text(it) } } else null,
            visualTransformation = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = if (creating) ImeAction.Next else ImeAction.Done,
                autoCorrectEnabled = false,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { submit() }),
            shape = MaterialTheme.shapes.small,
            colors = colors,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusLeft { if (password.isNotEmpty()) passwordTouched = true },
        )
        if (creating) {
            OutlinedTextField(
                value = confirm,
                onValueChange = { confirm = it.take(128) },
                label = { Text("Confirm password") },
                leadingIcon = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Lock, contentDescription = null) },
                singleLine = true,
                enabled = !busy,
                isError = confirmError != null,
                supportingText = confirmError?.let { { Text(it) } },
                visualTransformation = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { submit() }),
                shape = MaterialTheme.shapes.small,
                colors = colors,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (!creating && !busy) {
            LucentoTextButton(text = "Forgot password?", onClick = { forgot = true })
        }
        if (creating) {
            // The rule as a checklist that ticks as it is met, rather than an error after the fact.
            PasswordRule(
                met = password.length >= MIN_PASSWORD_LENGTH,
                failed = passwordError != null,
                text = "At least $MIN_PASSWORD_LENGTH characters",
            )
            PasswordsMatchRule(match = confirm.isNotEmpty() && confirm == password, failed = confirmError != null)
            Text(
                "We also check it against known data leaks — your password itself is never sent.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.xs),
            )
            Spacer(Modifier.height(Spacing.xs))
            TermsConsentRow(
                checked = agreed,
                onCheckedChange = { agreed = it },
                isError = consentError,
                enabled = !busy,
                onOpen = onOpenLegal,
            )
            if (consentError) {
                Text(
                    CONSENT_REQUIRED,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = Spacing.xs).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        if (message != null && !busy) {
            com.lucento.app.core.designsystem.component.InfoNote(
                message,
                icon = androidx.compose.material.icons.Icons.Outlined.ErrorOutline,
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        if (busy) {
            ConnectingIndicator(
                if (creating) "Creating your account…" else "Signing in…",
                Modifier.fillMaxWidth().padding(vertical = Spacing.sm).semantics { liveRegion = LiveRegionMode.Polite },
            )
        } else {
            LucentoPrimaryButton(text = if (creating) "Create account" else "Sign in", onClick = submit)
        }
    }
    if (forgot) {
        ForgotPasswordSheet(
            initialEmail = email,
            // The new password signs in through the form's own path, with every check a sign-in runs.
            onPasswordChanged = { resetEmail, newPassword ->
                forgot = false
                email = resetEmail
                password = newPassword
                onSubmit(resetEmail, newPassword, EmailMode.SIGN_IN)
            },
            onDismiss = { forgot = false },
        )
    }
}

/** Shown under a second password field that doesn't match the first. */
const val PASSWORDS_DIFFER = "The passwords don’t match."

/** The confirm-password check as it is met: a tick once both fields hold the same password. */
@Composable
internal fun PasswordsMatchRule(match: Boolean, failed: Boolean = false) {
    PasswordRule(met = match, failed = failed, text = "Passwords match")
}

/** Calls [onLeft] when the field loses focus after having had it. */
private fun Modifier.onFocusLeft(onLeft: () -> Unit): Modifier = composed {
    var had by remember { mutableStateOf(false) }
    onFocusChanged {
        if (it.isFocused) had = true else if (had) onLeft()
    }
}

/** One password requirement: a tick once it is met, red if the person tried to continue without it. */
@Composable
private fun PasswordRule(met: Boolean, failed: Boolean, text: String) {
    val tint = when {
        met -> LucentoTheme.extendedColors.positive
        failed -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        Modifier.padding(start = Spacing.xs).semantics(mergeDescendants = true) {
            stateDescription = if (met) "Done" else "Not yet"
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Icon(
            if (met) androidx.compose.material.icons.Icons.Filled.CheckCircle else androidx.compose.material.icons.Icons.Outlined.Circle,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(text, style = MaterialTheme.typography.bodySmall, color = if (met) MaterialTheme.colorScheme.onSurface else tint)
    }
}

/**
 * "I agree to the Terms of Use and Privacy Policy": a checkbox whose whole row toggles it, with each document's name a
 * link that opens it on the phone. Required before an email account is created.
 */
@Composable
fun TermsConsentRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onOpen: (com.lucento.app.feature.legal.LegalDoc) -> Unit,
    isError: Boolean = false,
    enabled: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Checkbox(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = if (isError) {
                androidx.compose.material3.CheckboxDefaults.colors(uncheckedColor = MaterialTheme.colorScheme.error)
            } else {
                androidx.compose.material3.CheckboxDefaults.colors()
            },
            modifier = Modifier.size(Sizes.minTouch),
        )
        Text(
            legalLinks("I agree to the ", " and the ", ".", onOpen),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Under "Continue with Google" where it can create an account: the same agreement, given by continuing. */
@Composable
fun GoogleConsentNote(onOpen: (com.lucento.app.feature.legal.LegalDoc) -> Unit, modifier: Modifier = Modifier) {
    Text(
        legalLinks("By continuing with Google, you agree to the ", " and the ", ".", onOpen),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(top = Spacing.xs),
    )
}

/** "[before]Terms of Use[middle]Privacy Policy[after]" with the two names as links that open the documents. */
@Composable
private fun legalLinks(
    before: String,
    middle: String,
    after: String,
    onOpen: (com.lucento.app.feature.legal.LegalDoc) -> Unit,
): androidx.compose.ui.text.AnnotatedString {
    val style = androidx.compose.ui.text.TextLinkStyles(
        style = androidx.compose.ui.text.SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
        ),
    )
    return androidx.compose.ui.text.buildAnnotatedString {
        append(before)
        withLink(androidx.compose.ui.text.LinkAnnotation.Clickable("terms", style) { onOpen(com.lucento.app.feature.legal.LegalDoc.TERMS) }) {
            append("Terms of Use")
        }
        append(middle)
        withLink(androidx.compose.ui.text.LinkAnnotation.Clickable("privacy", style) { onOpen(com.lucento.app.feature.legal.LegalDoc.PRIVACY) }) {
            append("Privacy Policy")
        }
        append(after)
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
