package com.lucento.app.feature.support

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucento.app.BuildConfig
import com.lucento.app.core.containerFactory
import com.lucento.app.data.sync.AuthRepository
import com.lucento.app.data.sync.SyncPreferences
import com.lucento.app.domain.entitlement.EntitlementState
import com.lucento.app.domain.support.FormProblem
import com.lucento.app.domain.support.PhoneDetails
import com.lucento.app.domain.support.SubmitOutcome
import com.lucento.app.domain.support.SupportHistoryItem
import com.lucento.app.domain.support.SupportRules
import com.lucento.app.domain.support.SupportService
import com.lucento.app.domain.support.SupportTopic
import com.lucento.app.feature.pro.ProCopy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

/** Settings › Help › Contact support (docs/SUPPORT.md). */
class ContactSupportViewModel(
    private val support: SupportService,
    private val auth: AuthRepository,
    private val sync: SyncPreferences,
    private val entitlements: () -> EntitlementState,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    sealed interface Result {
        /** A ticket exists; replies go to [replyTo]. */
        data class Sent(val ticket: String, val replyTo: String) : Result
        /** Saved on this phone; it goes once there's a connection. */
        data object Queued : Result
        /** The server said no (the text stays on screen); [message] says what to do. */
        data class Refused(val message: String) : Result
    }

    data class UiState(
        /** False in a build without Lucento's server: only the email address is offered. */
        val available: Boolean,
        val topic: SupportTopic? = null,
        val message: String = "",
        val email: String = "",
        /** The signed-in account's address; null while signed out (an email field is shown). */
        val accountEmail: String? = null,
        val includeDetails: Boolean = true,
        val details: PhoneDetails? = null,
        val sending: Boolean = false,
        /** Shown only once someone has tried to send. */
        val problem: FormProblem? = null,
        val result: Result? = null,
        val history: List<SupportHistoryItem> = emptyList(),
        val waiting: Int = 0,
    ) {
        val signedIn: Boolean get() = !accountEmail.isNullOrBlank()
    }

    private val _state = MutableStateFlow(UiState(available = support.available, history = support.history(), waiting = support.pending().size))
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            auth.observeSignedInEmail().collect { email ->
                _state.update { it.copy(accountEmail = email?.takeIf { e -> e.isNotBlank() }) }
                loadDetails()
            }
        }
    }

    private suspend fun loadDetails() {
        val details = PhoneDetails(
            appVersion = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            androidRelease = Build.VERSION.RELEASE.orEmpty(),
            androidSdk = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            syncEnabled = runCatching { sync.isEnabled() }.getOrDefault(false),
            lastSyncAtMillis = runCatching { sync.lastSyncedAtMillis() }.getOrNull(),
            plan = runCatching { ProCopy.planStatus(entitlements(), now()).title }.getOrDefault("Unknown"),
            signedIn = _state.value.signedIn,
            locale = Locale.getDefault().toLanguageTag(),
        )
        _state.update { it.copy(details = details) }
    }

    fun setTopic(topic: SupportTopic) = _state.update { it.copy(topic = topic, problem = it.problem?.takeIf { p -> p != FormProblem.NO_TOPIC }) }

    fun setMessage(text: String) = _state.update {
        it.copy(message = text.take(SupportRules.MAX_MESSAGE + 200), problem = it.problem?.takeIf { p -> p != FormProblem.MESSAGE_EMPTY && p != FormProblem.MESSAGE_TOO_LONG })
    }

    fun setEmail(text: String) = _state.update { it.copy(email = text.take(SupportRules.MAX_EMAIL + 20), problem = it.problem?.takeIf { p -> p != FormProblem.EMAIL_INVALID }) }

    fun setIncludeDetails(include: Boolean) = _state.update { it.copy(includeDetails = include) }

    /** Back to an empty form, to write another request. */
    fun startAnother() = _state.update {
        it.copy(topic = null, message = "", problem = null, result = null, history = support.history(), waiting = support.pending().size)
    }

    fun send() {
        val s = _state.value
        if (s.sending || !s.available) return
        val problem = SupportRules.validate(s.topic, s.message, s.email, s.signedIn)
        if (problem != null) {
            _state.update { it.copy(problem = problem, result = null) }
            return
        }
        _state.update { it.copy(sending = true, problem = null, result = null) }
        viewModelScope.launch {
            val accountId = if (s.signedIn) runCatching { auth.currentUserId() }.getOrNull() else null
            val replyTo = if (s.signedIn) s.accountEmail!! else SupportRules.normalizeEmail(s.email)
            val submitted = support.submit(
                topic = s.topic!!,
                message = s.message,
                email = replyTo,
                accountId = accountId,
                diagnostics = SupportRules.diagnostics(s.details, s.includeDetails),
            )
            val result = when (val outcome = submitted.outcome) {
                is SubmitOutcome.Sent -> Result.Sent(outcome.ticket, outcome.replyTo ?: replyTo)
                is SubmitOutcome.Refused -> Result.Refused(outcome.refusal.message)
                SubmitOutcome.TryLater -> if (submitted.queued) Result.Queued else Result.Refused(com.lucento.app.domain.support.Refusal.INVALID.message)
            }
            _state.update { it.copy(sending = false, result = result, history = support.history(), waiting = support.pending().size) }
        }
    }

    companion object {
        val Factory = containerFactory { c ->
            ContactSupportViewModel(
                support = c.support,
                auth = c.auth,
                sync = c.syncPreferences,
                entitlements = { c.entitlements.state.value },
            )
        }
    }
}
