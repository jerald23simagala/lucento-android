package com.lucento.app.data.sync

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.lucento.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Google Sign-In through Supabase Auth (see docs/SYNC.md "Authentication"). Entirely optional: Lucento is
 * fully usable before, during and after a failed sign-in, and no financial data is ever read or sent here —
 * this repository only ever establishes or clears an authenticated session.
 */
class AuthRepository(
    private val client: SupabaseClient?,
    private val context: Context,
) {

    sealed interface SignInResult {
        data object Success : SignInResult
        /** The user dismissed the account picker; not an error worth showing. */
        data object Cancelled : SignInResult
        /** Missing Supabase or Google OAuth configuration — see docs/SYNC.md "Setup required". */
        data object NotConfigured : SignInResult
        data class Failed(val message: String) : SignInResult
    }

    val currentUserEmail: String?
        get() = runCatching { client?.auth?.currentUserOrNull()?.email }.getOrNull()

    val isSignedIn: Boolean
        get() = runCatching { client?.auth?.currentUserOrNull() }.getOrNull() != null

    /** A missing/unconfigured client is reported as permanently signed out, never as an error. */
    fun observeSignedIn(): Flow<Boolean> {
        val auth = client?.auth ?: return flowOf(false)
        return auth.sessionStatus.map { it is SessionStatus.Authenticated }
    }

    suspend fun signInWithGoogle(): SignInResult {
        val auth = client?.auth ?: return SignInResult.NotConfigured
        if (!SupabaseModule.isGoogleSignInConfigured) return SignInResult.NotConfigured
        return try {
            val option = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                .build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val response = CredentialManager.create(context).getCredential(context, request)
            val googleCredential = GoogleIdTokenCredential.createFrom(response.credential.data)
            auth.signInWith(IDToken) {
                idToken = googleCredential.idToken
                provider = Google
            }
            SignInResult.Success
        } catch (e: GetCredentialCancellationException) {
            SignInResult.Cancelled
        } catch (e: GetCredentialException) {
            SignInResult.Failed(e.message ?: "Google sign-in failed")
        } catch (e: Exception) {
            SignInResult.Failed(e.message ?: "Sign-in failed")
        }
    }

    /** Clears the Supabase session only. Local financial data is never touched by signing out. */
    suspend fun signOut() {
        runCatching { client?.auth?.signOut() }
    }
}
