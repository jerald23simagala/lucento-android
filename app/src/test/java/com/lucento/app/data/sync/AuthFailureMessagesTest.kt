package com.lucento.app.data.sync

import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.NoCredentialException
import io.github.jan.supabase.exceptions.HttpRequestException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.takeFrom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What users see when Google sign-in or a sync fails. The safety property: no message — debug or release — ever echoes
 * a request header, bearer token or request URL, which supabase-kt puts inside its exception messages.
 */
class AuthFailureMessagesTest {

    private val leaky = "HTTP request to https://x.supabase.co/rest/v1/transactions?user_id=eq.1111 failed. " +
        "Headers: [Authorization=[Bearer eyJhbGciOiJIUzI1NiJ9.secret-access-token]]"

    private fun assertNoLeak(text: String) {
        assertFalse(text, text.contains("Bearer"))
        assertFalse(text, text.contains("eyJ"))
        assertFalse(text, text.contains("supabase.co"))
        assertFalse(text, text.contains("user_id"))
    }

    @Test
    fun `each failure gets its own honest message`() {
        assertTrue(signInFailureMessage(NoCredentialException(), debug = false).startsWith("No Google account could be used"))
        assertTrue(signInFailureMessage(GetCredentialProviderConfigurationException(), debug = false).contains("Google Play services"))
        assertEquals("Google sign-in was interrupted. Try again.", signInFailureMessage(GetCredentialInterruptedException(), debug = false))
        assertEquals("Google sign-in didn't finish. Try again.", signInFailureMessage(GetCredentialUnknownException(), debug = false))
        assertTrue(signInFailureMessage(networkFailure(), debug = false).startsWith("Couldn't reach the sync server"))
    }

    @Test
    fun `release builds show no diagnostics`() {
        listOf(NoCredentialException("[28444] Developer console is not set up correctly."), networkFailure(), IllegalStateException(leaky))
            .forEach { e -> assertFalse(signInFailureMessage(e, debug = false).contains("debug:")) }
    }

    @Test
    fun `debug builds explain setup mistakes, like a missing Android OAuth client`() {
        val message = signInFailureMessage(GetCredentialUnknownException("[28444] Developer console is not set up correctly."), debug = true)
        assertTrue(message, message.contains("28444"))
    }

    @Test
    fun `no message ever echoes headers, tokens or URLs`() {
        listOf(true, false).forEach { debug ->
            assertNoLeak(signInFailureMessage(IllegalStateException(leaky), debug))
            assertNoLeak(signInFailureMessage(networkFailure(), debug))
        }
        assertNoLeak(safeFailureDescription(IllegalStateException(leaky)))
        assertNoLeak(safeFailureDescription(networkFailure()))
        assertEquals("Couldn't reach the server", safeFailureDescription(networkFailure()))
    }

    private fun networkFailure() = HttpRequestException(
        leaky,
        HttpRequestBuilder().apply { url.takeFrom("https://x.supabase.co/rest/v1/transactions?user_id=eq.1111") },
    )
}
