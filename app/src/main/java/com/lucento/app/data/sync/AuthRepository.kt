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
import kotlinx.coroutines.CancellationException
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
class AuthRepository(private val client: SupabaseClient?) {

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
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                return SignInResult.Failed(signInFailureMessage(IllegalStateException("Unexpected credential type"), BuildConfig.DEBUG))
            }
            val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
            auth.signInWith(IDToken) {
                idToken = googleCredential.idToken
                provider = Google
                nonce = rawNonce
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
        return emailCall {
            auth.signUpWith(Email) {
                this.email = email.trim()
                this.password = password
            }
            if (auth.currentSessionOrNull() != null) SignInResult.Success else SignInResult.ConfirmEmail(email.trim())
        }
    }

    suspend fun signInWithEmail(email: String, password: String): SignInResult {
        val auth = client?.auth ?: return SignInResult.NotConfigured
        return emailCall {
            auth.signInWith(Email) {
                this.email = email.trim()
                this.password = password
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

    /** Clears the Supabase session only. Local financial data is never touched by signing out. */
    suspend fun signOut() {
        runCatching { client?.auth?.signOut() }
    }
}

/**
 * What the user is told when Google sign-in fails. Each cause gets an honest, actionable sentence rather than a
 * blanket "check your connection". In [debug] builds a short diagnostic is appended — the exception type plus the
 * server's own error description via [safeFailureDescription], never a token, header or raw exception message — so a
 * setup mistake (missing Android OAuth client, wrong Client ID in Supabase, provider disabled) is visible on screen.
 */
internal fun signInFailureMessage(e: Throwable, debug: Boolean): String {
    val message = when (e) {
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
