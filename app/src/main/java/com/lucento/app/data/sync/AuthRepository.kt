package com.lucento.app.data.sync

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.CustomCredential
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.lucento.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Google Sign-In, or an email + password account, through Supabase Auth (see docs/SYNC.md "Authentication"). Entirely optional: Lucento is fully
 * usable before, during and after a failed sign-in, and no financial data is read or sent here — this only
 * establishes or clears a session. The session itself is persisted and refreshed by supabase-kt's Auth plugin
 * (stored on the device, refreshed automatically before it expires); a session that can no longer be refreshed
 * shows up as signed out, and sync stops until the user connects again.
 */
class AuthRepository(
    private val client: SupabaseClient?,
    private val passwords: PasswordPolicy = PasswordPolicy(),
    /** Synchronous, durable delete of the stored session ([SessionStore.clearNow]). */
    private val clearStoredSession: () -> Boolean = { true },
    /** Whether to try the server-side logout at all; offline it is skipped instead of waiting for a timeout. */
    private val isOnline: () -> Boolean = { true },
) {

    /**
     * Deletes the stored session on the calling thread before anything else in sign-out runs, so a force-stop right
     * after the confirm tap relaunches signed out (QA R6-01). The in-memory session is cleared by [signOut].
     */
    fun clearStoredSessionNow(): Boolean = runCatching { clearStoredSession() }.getOrDefault(false)

    sealed interface SignInResult {
        data object Success : SignInResult
        /** The user dismissed the account picker; not an error worth showing. */
        data object Cancelled : SignInResult
        /** Missing Supabase or Google OAuth configuration — see docs/SYNC.md "Setup required". */
        data object NotConfigured : SignInResult
        /** An email account was created but must be confirmed from the link Supabase emailed before signing in. */
        data class ConfirmEmail(val email: String) : SignInResult
        data class Failed(val message: String) : SignInResult
    }

    /**
     * The signed-in account's email, or null. Follows the session status, so it updates once the stored session
     * has been restored after a restart — a one-off read at startup would wrongly report "signed out".
     * While a refresh is failing (e.g. offline), the last session is kept and the account still shows.
     */
    fun observeSignedInEmail(): Flow<String?> {
        val auth = client?.auth ?: return flowOf(null)
        return auth.sessionStatus.map { status ->
            when (status) {
                is SessionStatus.Authenticated -> status.session.user?.email ?: ""
                is SessionStatus.RefreshFailure -> auth.currentUserOrNull()?.email ?: ""
                else -> null
            }
        }.distinctUntilChanged()
    }

    /**
     * Shows Google's account picker and exchanges the chosen account's ID token for a Supabase session (a new
     * Supabase user is created on first sign-in). [activity] must be an Activity: Credential Manager launches its
     * account sheet from it. It is used only for this call and not retained.
     */
    suspend fun signInWithGoogle(activity: Context): SignInResult {
        val auth = client?.auth ?: return SignInResult.NotConfigured
        if (!SupabaseModule.isGoogleSignInConfigured) return SignInResult.NotConfigured
        return try {
            // Supabase's documented Android flow: Google signs a token containing the SHA-256 of a one-time nonce,
            // and Supabase checks it against the raw nonce, so a captured ID token can't be replayed.
            val rawNonce = ByteArray(32).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
            val hashedNonce = MessageDigest.getInstance("SHA-256").digest(rawNonce.toByteArray())
                .joinToString("") { "%02x".format(it) }
            val option = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                // The Web client ID: the audience of the ID token, and the Client ID Supabase is configured to accept.
                .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                .setNonce(hashedNonce)
                .build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            // While the user picks an account, open the connection to the sync server so the token exchange is quick.
            prewarm()
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                return SignInResult.Failed(signInFailureMessage(IllegalStateException("Unexpected credential type"), BuildConfig.DEBUG))
            }
            val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
            retryOnTimeout {
                auth.signInWith(IDToken) {
                    idToken = googleCredential.idToken
                    provider = Google
                    nonce = rawNonce
                }
            }
            SignInResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            SignInResult.Cancelled
        } catch (e: Exception) {
            SignInResult.Failed(signInFailureMessage(e, BuildConfig.DEBUG))
        }
    }

    /**
     * Creates an email + password account. When the project requires email confirmation (Supabase's default) no
     * session exists yet and [SignInResult.ConfirmEmail] asks the user to open the link, then sign in.
     */
    suspend fun signUpWithEmail(email: String, password: String): SignInResult {
        val auth = client?.auth ?: return SignInResult.NotConfigured
        passwords.problem(password)?.let { return SignInResult.Failed(it) }
        return emailCall {
            retryOnTimeout {
                auth.signUpWith(Email) {
                    this.email = email.trim()
                    this.password = password
                }
            }
            if (auth.currentSessionOrNull() != null) SignInResult.Success else SignInResult.ConfirmEmail(email.trim())
        }
    }

    /**
     * Replaces the signed-in email account's password. The same length and breach checks as sign-up run first.
     * A breach lookup that cannot reach the network does not block the change.
     */
    suspend fun changePassword(password: String): SignInResult {
        val auth = client?.auth ?: return SignInResult.NotConfigured
        passwords.problem(password)?.let { return SignInResult.Failed(it) }
        return emailCall {
            auth.updateUser {
                this.password = password
            }
            SignInResult.Success
        }
    }

    suspend fun signInWithEmail(email: String, password: String): SignInResult {
        val auth = client?.auth ?: return SignInResult.NotConfigured
        return emailCall {
            retryOnTimeout {
                auth.signInWith(Email) {
                    this.email = email.trim()
                    this.password = password
                }
            }
            SignInResult.Success
        }
    }

    private suspend fun emailCall(block: suspend () -> SignInResult): SignInResult = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: AuthRestException) {
        SignInResult.Failed(emailFailureMessage(e.errorCode, BuildConfig.DEBUG, safeFailureDescription(e)))
    } catch (e: Exception) {
        SignInResult.Failed(signInFailureMessage(e, BuildConfig.DEBUG))
    }

    private val warmScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    /**
     * Opens the connection to the sync server ahead of sign-in (DNS, TLS), so the first real request isn't the slow
     * one. Called only once the user has chosen to connect an account — never before. Sends no personal data: an
     * unauthenticated health check carrying only the app's publishable key. Failures are ignored.
     */
    fun prewarm() {
        if (client == null || !SupabaseModule.isConfigured) return
        warmScope.launch {
            runCatching {
                val url = java.net.URL(BuildConfig.SUPABASE_URL.trimEnd('/') + "/auth/v1/health")
                (url.openConnection() as java.net.HttpURLConnection).run {
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    responseCode
                    disconnect()
                }
            }
        }
    }

    /** Tries a request once more after a timeout: the first call on a cold network often times out where the next succeeds. */
    private suspend fun <T> retryOnTimeout(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (!isTimeout(e)) throw e
        delay(1_000)
        block()
    }

    /** The signed-in session's access token, for Lucento's own proxy (Smarter reading); null when signed out. */
    suspend fun accessToken(): String? = runCatching { client?.auth?.currentAccessTokenOrNull() }.getOrNull()

    /** The signed-in account's id, or null. Used to tell an account switch from a return to the same account. */
    suspend fun currentUserId(): String? = runCatching {
        client?.auth?.awaitInitialization()
        client?.auth?.currentUserOrNull()?.id
    }.getOrNull()

    /** Access token of the session [signOut] ended, kept only in memory so [revokeOnServer] can run after the wipe. */
    @Volatile private var endedAccessToken: String? = null

    /**
     * Ends the session on this device, locally only: no network call (QA R9-01). The caller wipes the local ledger
     * afterwards and then calls [revokeOnServer]. The stored session is deleted first and durably, so a kill at any
     * later point relaunches signed out.
     */
    suspend fun signOut() {
        val auth = client?.auth ?: return
        endedAccessToken = runCatching { auth.currentAccessTokenOrNull() }.getOrNull()
        clearStoredSessionNow()
        endLocalSession(
            clearSession = {
                clearStoredSessionNow()
                auth.clearSession()
            },
        )
    }

    /**
     * Best-effort revoke of the session [signOut] ended (POST /auth/v1/logout, scope=local), capped at
     * [REVOKE_TIMEOUT_MS] and skipped offline. Called only after the local wipe has committed.
     */
    suspend fun revokeOnServer() {
        val token = endedAccessToken ?: return
        endedAccessToken = null
        if (client == null || !SupabaseModule.isConfigured || !isOnline()) return
        withTimeoutOrNull(REVOKE_TIMEOUT_MS) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val url = java.net.URL(BuildConfig.SUPABASE_URL.trimEnd('/') + "/auth/v1/logout?scope=local")
                    (url.openConnection() as java.net.HttpURLConnection).run {
                        requestMethod = "POST"
                        connectTimeout = REVOKE_TIMEOUT_MS.toInt()
                        readTimeout = REVOKE_TIMEOUT_MS.toInt()
                        setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
                        setRequestProperty("Authorization", "Bearer $token")
                        responseCode
                        disconnect()
                    }
                }
            }
        }
    }

    private companion object {
        const val REVOKE_TIMEOUT_MS = 5_000L
    }
}

/**
 * Ends the session stored on this device. [signOutLocal] is supabase-kt `signOut(LOCAL)`, which only deletes the
 * stored session after the network call returns. A failed call must still run [clearSession], or the tokens
 * survive and the next launch is signed in again.
 */
internal suspend fun endLocalSession(signOutLocal: suspend () -> Unit = {}, clearSession: suspend () -> Unit) {
    try {
        signOutLocal()
    } catch (e: CancellationException) {
        runCatching { clearSession() }
        throw e
    } catch (_: Exception) {
        // Offline, or the server rejected the logout. The stored tokens still have to go.
    }
    // A failed first attempt is retried once; a second failure is thrown so the caller does not wipe the ledger
    // while a usable session survives.
    try {
        clearSession()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        clearSession()
    }
}

/**
 * What the user is told when Google sign-in fails. Each cause gets an honest, actionable sentence rather than a
 * blanket "check your connection". In [debug] builds a short diagnostic is appended — the exception type plus the
 * server's own error description via [safeFailureDescription], never a token, header or raw exception message — so a
 * setup mistake (missing Android OAuth client, wrong Client ID in Supabase, provider disabled) is visible on screen.
 */
internal fun isTimeout(e: Throwable): Boolean =
    generateSequence(e) { it.cause }.take(5).any {
        it is HttpRequestTimeoutException || it is ConnectTimeoutException || it is SocketTimeoutException
    }

internal fun signInFailureMessage(e: Throwable, debug: Boolean): String {
    val message = when {
        isTimeout(e) -> "The sync server took too long to answer. Check your connection and try again — your data is safe on this phone."
        else -> when (e) {
        is NoCredentialException ->
            "No Google account could be used. Add a Google account in Android Settings, then try again."
        is GetCredentialProviderConfigurationException ->
            "Signing in with Google needs Google Play services, which isn't available on this device."
        is GetCredentialInterruptedException -> "Google sign-in was interrupted. Try again."
        is GetCredentialException -> "Google sign-in didn't finish. Try again."
        is GoogleIdTokenParsingException -> "Google returned an unexpected response. Try again."
        is HttpRequestException -> "Couldn't reach the sync server. Check your connection and try again."
        is RestException -> "The sync server didn't accept this Google sign-in. Try again later."
        else -> "Couldn't connect your account. Try again."
        }
    }
    if (!debug) return message
    val detail = when (e) {
        // Credential Manager's own text, e.g. "[28444] Developer console is not set up correctly"; it never holds tokens.
        is GetCredentialException -> "${e.type}: ${e.errorMessage ?: ""}"
        else -> safeFailureDescription(e)
    }
    return "$message (debug: $detail)"
}

/** At least this many characters; Supabase's own minimum (6) is too weak for an account that holds financial data. */
const val MIN_PASSWORD_LENGTH = 8

/** What the user is told when an email sign-up or sign-in is refused: one honest sentence per cause. */
internal fun emailFailureMessage(code: AuthErrorCode?, debug: Boolean, detail: String): String {
    val message = when (code) {
        AuthErrorCode.InvalidCredentials -> "That email and password don’t match an account. Check them, or create an account."
        AuthErrorCode.EmailNotConfirmed -> "Confirm your email first — open the link we sent you, then sign in."
        AuthErrorCode.UserAlreadyExists, AuthErrorCode.EmailExists -> "An account with this email already exists. Sign in instead."
        AuthErrorCode.WeakPassword -> "Choose a stronger password: at least $MIN_PASSWORD_LENGTH characters, mixing letters and numbers."
        AuthErrorCode.OverEmailSendRateLimit -> "Too many emails were sent just now. Wait a few minutes and try again."
        AuthErrorCode.SignupDisabled, AuthErrorCode.EmailProviderDisabled -> "Email accounts aren’t turned on for Lucento’s sync yet. Use Google instead."
        AuthErrorCode.ValidationFailed -> "Check the email address and password, then try again."
        else -> "Couldn’t connect your account. Try again."
    }
    return if (debug) "$message (debug: $detail)" else message
}
