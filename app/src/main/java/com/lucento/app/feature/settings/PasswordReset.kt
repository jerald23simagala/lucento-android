package com.lucento.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.LockReset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.core.containerFactory
import com.lucento.app.core.designsystem.component.ConnectingIndicator
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoBottomSheet
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.component.SheetHeader
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.data.sync.AuthRepository
import com.lucento.app.data.sync.MIN_PASSWORD_LENGTH
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the forgot-password flow is: the email, the emailed code with a new password, or done. */
enum class ResetStep { EMAIL, CODE, DONE }

data class PasswordResetState(
    val step: ResetStep = ResetStep.EMAIL,
    val email: String = "",
    val busy: Boolean = false,
    val message: String? = null,
    /** A note that isn't an error ("We sent a new code"). */
    val info: String? = null,
)

/**
 * Forgot password (owner test 2026-11-09): Supabase emails a code ([AuthRepository.sendPasswordReset]); the person types
 * it with a new password ([AuthRepository.resetPasswordWithCode]); then the sign-in form signs in with that password.
 * One request at a time, so a double tap never sends two emails or two resets. Passwords are never kept in state.
 */
class PasswordResetViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(PasswordResetState())
    val state: StateFlow<PasswordResetState> = _state.asStateFlow()

    fun start(email: String) {
        _state.value = PasswordResetState(email = email.trim())
    }

    fun sendCode(email: String, again: Boolean = false) {
        if (_state.value.busy) return
        emailProblem(email)?.let { problem -> _state.update { it.copy(message = problem, info = null) }; return }
        _state.update { it.copy(busy = true, message = null, info = null, email = email.trim()) }
        viewModelScope.launch {
            val result = auth.sendPasswordReset(email)
            _state.update {
                when (result) {
                    AuthRepository.ResendResult.SENT -> it.copy(
                        busy = false,
                        step = ResetStep.CODE,
                        info = if (again) "We sent a new code to ${it.email}." else null,
                    )
                    AuthRepository.ResendResult.TOO_SOON -> it.copy(busy = false, message = "An email was sent a moment ago. Wait a minute, then try again.")
                    AuthRepository.ResendResult.FAILED -> it.copy(busy = false, message = "Couldn’t send the email. Check your connection and try again.")
                }
            }
        }
    }

    fun reset(code: String, password: String, onDone: (email: String) -> Unit) {
        val current = _state.value
        if (current.busy) return
        _state.update { it.copy(busy = true, message = null, info = null) }
        viewModelScope.launch {
            when (val result = auth.resetPasswordWithCode(current.email, code, password)) {
                AuthRepository.SignInResult.Success -> {
                    _state.update { it.copy(busy = false, step = ResetStep.DONE) }
                    onDone(current.email)
                }
                is AuthRepository.SignInResult.Failed -> _state.update { it.copy(busy = false, message = result.message) }
                else -> _state.update { it.copy(busy = false, message = "Couldn’t change your password. Try again.") }
            }
        }
    }

    fun backToEmail() {
        if (!_state.value.busy) _state.update { it.copy(step = ResetStep.EMAIL, message = null, info = null) }
    }

    companion object {
        val Factory = containerFactory { PasswordResetViewModel(it.auth) }

        /** Supabase's email codes are 6 digits by default and can be set up to 10. */
        fun codeLooksRight(code: String): Boolean = code.trim().length in 6..10 && code.trim().all(Char::isDigit)
    }
}

/**
 * The forgot-password sheet. [onPasswordChanged] hands back the email and the new password so the sign-in form signs in
 * with them through its normal path; the password itself is only ever in memory.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordSheet(
    initialEmail: String,
    onPasswordChanged: (email: String, password: String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: PasswordResetViewModel = viewModel(factory = PasswordResetViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var started by rememberSaveable { mutableStateOf(false) }
    if (!started) {
        started = true
        viewModel.start(initialEmail)
    }
    LucentoBottomSheet(onDismissRequest = { if (!state.busy) onDismiss() }) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter)
                .imePadding()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            when (state.step) {
                ResetStep.EMAIL -> EmailStep(state, onSend = { viewModel.sendCode(it) })
                ResetStep.CODE, ResetStep.DONE -> CodeStep(
                    state = state,
                    onReset = { code, password -> viewModel.reset(code, password) { email -> onPasswordChanged(email, password) } },
                    onResend = { viewModel.sendCode(state.email, again = true) },
                    onChangeEmail = viewModel::backToEmail,
                )
            }
            state.info?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            state.message?.let {
                InfoNote(
                    it,
                    icon = Icons.Outlined.ErrorOutline,
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

@Composable
private fun EmailStep(state: PasswordResetState, onSend: (String) -> Unit) {
    var email by rememberSaveable { mutableStateOf(state.email) }
    SheetHeader(
        title = "Reset your password",
        subtitle = "We’ll email you a code to set a new one.",
        icon = Icons.Outlined.LockReset,
    )
    OutlinedTextField(
        value = email,
        onValueChange = { email = it.take(254).replace("\n", "") },
        label = { Text("Email") },
        singleLine = true,
        enabled = !state.busy,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send, autoCorrectEnabled = false),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { onSend(email) }),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    )
    if (state.busy) {
        ConnectingIndicator("Sending the email…", Modifier.fillMaxWidth().padding(vertical = Spacing.sm))
    } else {
        LucentoPrimaryButton(text = "Email me a code", onClick = { onSend(email) }, enabled = email.isNotBlank())
    }
}

@Composable
private fun CodeStep(
    state: PasswordResetState,
    onReset: (code: String, password: String) -> Unit,
    onResend: () -> Unit,
    onChangeEmail: () -> Unit,
) {
    var code by rememberSaveable { mutableStateOf("") }
    // In memory only: a password must not survive into a saved instance bundle.
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    val codeError = if (submitted && !PasswordResetViewModel.codeLooksRight(code)) "Enter the code from the email." else null
    val passwordError = if (submitted) passwordProblem(password, EmailMode.CREATE) else null
    val matchError = if (submitted && passwordError == null && confirm != password) PASSWORDS_DIFFER else null

    SheetHeader(
        title = "Check your email",
        subtitle = "We sent a code to ${state.email}. It can take a minute — check Spam too.",
        icon = Icons.Outlined.LockReset,
    )
    OutlinedTextField(
        value = code,
        onValueChange = { code = it.filter(Char::isDigit).take(10) },
        label = { Text("Code") },
        singleLine = true,
        enabled = !state.busy,
        isError = codeError != null,
        supportingText = codeError?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = password,
        onValueChange = { password = it.take(128) },
        label = { Text("New password") },
        singleLine = true,
        enabled = !state.busy,
        isError = passwordError != null,
        supportingText = { Text(passwordError ?: "At least $MIN_PASSWORD_LENGTH characters.") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next, autoCorrectEnabled = false),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = confirm,
        onValueChange = { confirm = it.take(128) },
        label = { Text("Confirm new password") },
        singleLine = true,
        enabled = !state.busy,
        isError = matchError != null,
        supportingText = matchError?.let { { Text(it) } },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    )
    if (confirm.isNotEmpty()) PasswordsMatchRule(match = confirm == password)
    if (state.busy || state.step == ResetStep.DONE) {
        ConnectingIndicator(
            if (state.step == ResetStep.DONE) "Password changed. Signing you in…" else "Changing your password…",
            Modifier.fillMaxWidth().padding(vertical = Spacing.sm).semantics { liveRegion = LiveRegionMode.Polite },
        )
    } else {
        LucentoPrimaryButton(
            text = "Set new password",
            onClick = {
                submitted = true
                if (PasswordResetViewModel.codeLooksRight(code) && passwordProblem(password, EmailMode.CREATE) == null && confirm == password) {
                    onReset(code, password)
                }
            },
        )
        LucentoTextButton(text = "Send a new code", onClick = onResend, modifier = Modifier.fillMaxWidth())
        LucentoTextButton(text = "Use a different email", onClick = onChangeEmail, modifier = Modifier.fillMaxWidth())
    }
}
