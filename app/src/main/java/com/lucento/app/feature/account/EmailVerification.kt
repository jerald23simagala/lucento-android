package com.lucento.app.feature.account

import com.lucento.app.data.sync.AuthRepository.EmailCheck
import com.lucento.app.data.sync.AuthRepository.ResendResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Waiting for a new email account to be confirmed (owner, 2026-11-06): after "Create account" the person is told to open
 * the link in the email we sent, and this notices on its own when they have — wherever they opened it (this phone or a
 * computer) — then shows "Email verified" and carries on.
 *
 * Supabase has no way to ask "is this address confirmed?" without a session, so it signs in with the password the
 * account was just created with: refused as `email_not_confirmed` until the link is opened, then a normal sign-in. It
 * looks when Lucento comes back to the front (the usual moment: back from the email app) and every [pollMillis] while it
 * is on screen — never in the background, and well under the project's sign-in limit (30 per 5 minutes per address).
 *
 * The password is held in memory only, never in saved state. If Android closed Lucento while the person was in their
 * email, the address comes back ([restoredEmail]) without it, and the dialog asks for the password once.
 */
class EmailVerification(
    private val scope: CoroutineScope,
    private val check: suspend (email: String, password: String) -> EmailCheck,
    private val resend: suspend (email: String) -> ResendResult,
    /** Runs after "Email verified" has been on screen for [beatMillis]; the person is signed in by then. */
    private val onVerified: suspend (email: String) -> Unit,
    /** Saves the address being verified (null when done), so a restarted app can pick the wait up again. */
    private val persist: (String?) -> Unit = {},
    restoredEmail: String? = null,
    private val pollMillis: Long = POLL_MILLIS,
    private val beatMillis: Long = SUCCESS_BEAT_MILLIS,
    private val resendCooldownSeconds: Int = RESEND_COOLDOWN_SECONDS,
) {
    sealed interface State {
        val email: String

        /** The link hasn't been opened yet (or we can't tell). */
        data class Waiting(
            override val email: String,
            /** Lucento was restarted meanwhile: the password is needed once to keep checking. */
            val needsPassword: Boolean = false,
            /** Seconds until "Send again" can be used; 0 when it can. */
            val resendInSeconds: Int = 0,
            /** A check is running right now. */
            val checking: Boolean = false,
            val notice: Notice? = null,
        ) : State

        /** Confirmed and signed in; [onVerified] runs next. */
        data class Verified(override val email: String) : State
    }

    /** Something worth a line in the dialog. */
    enum class Notice { SENT_AGAIN, TOO_SOON, SEND_FAILED, OFFLINE, WRONG_PASSWORD }

    private val _state = MutableStateFlow<State?>(restoredEmail?.let { State.Waiting(it, needsPassword = true) })
    val state: StateFlow<State?> = _state

    private var password: String? = null
    private var visible = false
    private var poller: Job? = null
    private var ticker: Job? = null
    private var attempt: Job? = null

    /** The account was just created and the first email sent: start waiting. */
    fun start(email: String, password: String, emailJustSent: Boolean = true) {
        stopJobs()
        this.password = password
        persist(email)
        _state.value = State.Waiting(email)
        if (emailJustSent) startCooldown()
        if (visible) startPolling()
    }

    /** Lucento came to the front ([visible] true) or left it. Coming back looks right away. */
    fun onVisible(visible: Boolean) {
        this.visible = visible
        if (!visible) {
            poller?.cancel()
            poller = null
            return
        }
        if (waiting() == null) return
        checkNow()
        startPolling()
    }

    /** Looks now (also "I've verified" if the person asks). */
    fun checkNow() {
        val current = waiting() ?: return
        val secret = password ?: return
        if (attempt?.isActive == true) return
        attempt = scope.launch {
            update { it.copy(checking = true) }
            val result = check(current.email, secret)
            update { it.copy(checking = false) }
            when (result) {
                EmailCheck.CONFIRMED -> verified(current.email)
                EmailCheck.PENDING -> update { if (it.notice == Notice.OFFLINE || it.notice == Notice.WRONG_PASSWORD) it.copy(notice = null) else it }
                EmailCheck.UNREACHABLE -> update { it.copy(notice = Notice.OFFLINE) }
                EmailCheck.WRONG_PASSWORD -> {
                    password = null
                    poller?.cancel()
                    update { it.copy(needsPassword = true, notice = Notice.WRONG_PASSWORD) }
                }
            }
        }
    }

    /** After a restart (or a password that didn't match): the person types it again and checking resumes. */
    fun providePassword(password: String) {
        val current = waiting() ?: return
        if (password.isEmpty()) return
        this.password = password
        _state.value = current.copy(needsPassword = false, notice = null)
        checkNow()
        if (visible) startPolling()
    }

    fun resendEmail() {
        val current = waiting() ?: return
        if (current.resendInSeconds > 0) return
        scope.launch {
            val result = resend(current.email)
            update {
                it.copy(
                    notice = when (result) {
                        ResendResult.SENT -> Notice.SENT_AGAIN
                        ResendResult.TOO_SOON -> Notice.TOO_SOON
                        ResendResult.FAILED, ResendResult.INVALID_EMAIL -> Notice.SEND_FAILED
                        ResendResult.OFFLINE -> Notice.OFFLINE
                    },
                )
            }
            if (result == ResendResult.SENT || result == ResendResult.TOO_SOON) startCooldown()
        }
    }

    /** "Use a different email", or Back: stop waiting. The account stays unconfirmed until its link is opened. */
    fun cancel() {
        stopJobs()
        password = null
        persist(null)
        _state.value = null
    }

    private fun verified(email: String) {
        stopJobs()
        password = null
        persist(null)
        _state.value = State.Verified(email)
        scope.launch {
            delay(beatMillis)
            try {
                onVerified(email)
            } finally {
                if (_state.value is State.Verified) _state.value = null
            }
        }
    }

    private fun startPolling() {
        if (poller?.isActive == true || password == null) return
        poller = scope.launch {
            while (isActive) {
                delay(pollMillis)
                if (waiting()?.needsPassword != false) break
                checkNow()
            }
        }
    }

    private fun startCooldown() {
        ticker?.cancel()
        ticker = scope.launch {
            var left = resendCooldownSeconds
            while (left > 0 && isActive) {
                update { it.copy(resendInSeconds = left) }
                delay(1_000)
                left--
            }
            update { it.copy(resendInSeconds = 0) }
        }
    }

    private fun waiting(): State.Waiting? = _state.value as? State.Waiting

    private inline fun update(change: (State.Waiting) -> State.Waiting) {
        val current = waiting() ?: return
        _state.value = change(current)
    }

    private fun stopJobs() {
        poller?.cancel()
        ticker?.cancel()
        attempt?.cancel()
        poller = null
        ticker = null
        attempt = null
    }

    companion object {
        /** Every 15 s while on screen: at most 20 sign-in attempts in 5 minutes, under the project's 30. */
        const val POLL_MILLIS = 15_000L
        /** How long "Email verified" stays before Lucento moves on. */
        const val SUCCESS_BEAT_MILLIS = 1_400L
        /** Supabase sends at most one email per address a minute. */
        const val RESEND_COOLDOWN_SECONDS = 60
    }
}
