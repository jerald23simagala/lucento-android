package com.lucento.app.core.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether this process has been unlocked (P2-10). Pure Kotlin so the rules are unit-tested; one instance lives in the
 * AppContainer, so a rotation never re-locks and a cold start always does.
 *
 * - Lucento starts locked (when the lock is on).
 * - It locks again after more than [graceMillis] in the background, so a quick trip to the camera, a file picker or
 *   another app doesn't ask again.
 * - Time spent in the system unlock prompt itself never counts as background (its device-credential screen is another
 *   activity on older Android versions).
 */
class AppLockSession(private val graceMillis: Long = DEFAULT_GRACE_MILLIS) {

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    private var backgroundedAt: Long? = null
    private var authenticating = false

    fun onBackground(nowMillis: Long) {
        if (!authenticating) backgroundedAt = nowMillis
    }

    fun onForeground(nowMillis: Long) {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        if (nowMillis - since > graceMillis) _unlocked.value = false
    }

    /** The system prompt is about to show. */
    fun beginAuthentication() {
        authenticating = true
    }

    /** The prompt closed without unlocking (cancelled, too many attempts). */
    fun endAuthentication() {
        authenticating = false
    }

    /** The user proved it's them — from the lock screen, or when turning the lock on. */
    fun unlock() {
        authenticating = false
        backgroundedAt = null
        _unlocked.value = true
    }

    companion object {
        const val DEFAULT_GRACE_MILLIS = 60_000L

        /** What the UI shows: the lock only when the setting is on and this process hasn't been unlocked. */
        fun isLocked(lockEnabled: Boolean, unlocked: Boolean): Boolean = lockEnabled && !unlocked
    }
}
