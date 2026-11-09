package com.lucento.app.data.account

import android.content.Context
import com.lucento.app.data.ai.postToProxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Asks Lucento's site for the welcome email once per signed-in account (docs/EMAILS.md). The site sends it only when the
 * account's address is verified and the account is new, and only once whichever phone asks; this side just stops asking
 * once the site has answered for good. Sent: the account's own Supabase token, nothing else. Nothing runs while signed
 * out, and a build without the site's address ([endpoint] blank) never asks.
 */
class WelcomeEmailRequester(
    private val endpoint: String,
    private val memory: WelcomeMemory,
    private val ask: suspend () -> Int?,
) {
    /** Watches the signed-in account; each new account (and each launch, until settled) asks once. */
    fun start(scope: CoroutineScope, userIds: Flow<String?>) {
        if (endpoint.isBlank()) return
        scope.launch { userIds.filterNotNull().collect { requestFor(it) } }
    }

    /** One attempt for [userId]. True when the site has answered for good and the account is remembered. */
    suspend fun requestFor(userId: String): Boolean {
        if (endpoint.isBlank() || memory.has(userId)) return false
        val code = runCatching { ask() }.getOrElse { if (it is kotlinx.coroutines.CancellationException) throw it else null }
        if (!settled(code)) return false
        memory.add(userId)
        return true
    }

    internal companion object {
        /**
         * 200: sent, sent before, or the account is too old for a welcome; never ask again. Anything else (signed out,
         * not verified yet, offline, the site not set up, a failed send) asks again at the next launch.
         */
        fun settled(code: Int?): Boolean = code == 200

        /** `POST /api/account/welcome` with the account's token; null when signed out. */
        fun proxyAsk(endpoint: String, accessToken: suspend (refresh: Boolean) -> String?): suspend () -> Int? = {
            withContext(Dispatchers.IO) { postToProxy(endpoint, "{}", READ_TIMEOUT_MS, accessToken = accessToken)?.code }
        }

        private const val READ_TIMEOUT_MS = 15_000
    }
}

/** The accounts this phone no longer needs to ask about. Device-level; holds account ids only. */
interface WelcomeMemory {
    fun has(userId: String): Boolean
    fun add(userId: String)
}

class PrefsWelcomeMemory(context: Context) : WelcomeMemory {
    private val prefs = context.getSharedPreferences("account_emails", Context.MODE_PRIVATE)

    override fun has(userId: String): Boolean = userId in prefs.getStringSet(KEY, emptySet()).orEmpty()

    override fun add(userId: String) {
        val ids = prefs.getStringSet(KEY, emptySet()).orEmpty() + userId
        prefs.edit().putStringSet(KEY, ids).apply()
    }

    private companion object {
        const val KEY = "welcome_settled"
    }
}
