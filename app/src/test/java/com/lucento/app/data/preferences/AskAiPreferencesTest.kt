package com.lucento.app.data.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ask Lucento's AI answers on this phone (owner, 2026-11-17): on by default, nothing sent until the person confirms once,
 * an earlier agreement still counts, and both the agreement and an "off" go with the account.
 */
class AskAiPreferencesTest {

    private val confirmed = booleanPreferencesKey("ask_lucento_ai_v2")
    private val v1 = booleanPreferencesKey("ask_lucento_ai")
    private val off = booleanPreferencesKey("ask_ai_off")

    @Test fun `a fresh install has AI answers on, not yet confirmed`() {
        assertTrue(UserPreferencesRepository.askAiOn(emptyPreferences()))
        assertFalse(UserPreferencesRepository.askAiConfirmed(emptyPreferences()))
        // The data class says the same, for code that builds preferences without the store.
        val defaults = UserPreferences(onboardingComplete = true, themeMode = ThemeMode.SYSTEM, primaryCurrency = "USD")
        assertTrue(defaults.askLucentoAi)
        assertFalse(defaults.askAiConfirmed)
    }

    @Test fun `an agreement given before AI answers became the default still counts`() {
        val p = mutablePreferencesOf(confirmed to true)
        assertTrue(UserPreferencesRepository.askAiOn(p))
        assertTrue(UserPreferencesRepository.askAiConfirmed(p))
    }

    @Test fun `an old summaries-only agreement is not a confirmation`() {
        val p = mutablePreferencesOf(v1 to true)
        assertTrue(UserPreferencesRepository.askAiOn(p))
        assertFalse(UserPreferencesRepository.askAiConfirmed(p))
    }

    @Test fun `turning AI answers off withdraws the agreement, and turning them on from a consent gives it`() {
        val p = mutablePreferencesOf(confirmed to true, v1 to true)
        UserPreferencesRepository.applyAskAi(p, enabled = false)
        assertFalse(UserPreferencesRepository.askAiOn(p))
        assertFalse(UserPreferencesRepository.askAiConfirmed(p))
        assertNull(p[v1])

        UserPreferencesRepository.applyAskAi(p, enabled = true)
        assertTrue(UserPreferencesRepository.askAiOn(p))
        assertTrue(UserPreferencesRepository.askAiConfirmed(p))
        assertNull(p[off])
    }

    @Test fun `the next account starts from the default, whatever the last one chose`() {
        listOf(
            mutablePreferencesOf(confirmed to true),
            mutablePreferencesOf(off to true, confirmed to false),
            mutablePreferencesOf(v1 to true),
        ).forEach { p ->
            UserPreferencesRepository.resetAccountSetup(p)
            assertTrue("on by default again", UserPreferencesRepository.askAiOn(p))
            assertFalse("the next account confirms for itself", UserPreferencesRepository.askAiConfirmed(p))
            assertNull(p[v1])
        }
    }

    @Test fun `forgetting the account's consents keeps nothing it agreed to`() {
        val p = mutablePreferencesOf(confirmed to true, off to true)
        UserPreferencesRepository.forgetAskAi(p)
        assertEquals(false, p[confirmed])
        assertNull(p[off])
    }
}
