package com.lucento.app.data.local

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lucento.app.data.preferences.AccountSetupStore
import com.lucento.app.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** F-7: the real sign-out wipe resets the account's setup, last, so the next account goes through onboarding. */
class LocalLedgerResetTest {

    private val onboarding = booleanPreferencesKey("onboarding_complete")
    private val generation = intPreferencesKey("setup_generation")
    private val theme = stringPreferencesKey("theme_mode")

    private class Marker(var value: Boolean = false) : WipeMarker {
        override fun set() = true.also { value = true }
        override fun clear() = true.also { value = false }
        override fun isSet() = value
    }

    /** The production reset applied to an in-memory preference set (DataStore's edit gives the same atomicity). */
    private class PrefsStore(val prefs: MutablePreferences, val steps: MutableList<String>) : AccountSetupStore {
        override suspend fun resetForNextAccount() {
            steps += "setup"
            UserPreferencesRepository.resetAccountSetup(prefs)
        }
    }

    private fun reset(marker: Marker, store: PrefsStore, steps: MutableList<String>, failTables: Boolean = false) = LocalLedgerReset(
        marker = marker,
        clearBinding = { steps += "binding" },
        clearTables = { steps += "tables"; if (failTables) error("killed") },
        deleteGoalPhotos = { steps += "photos" },
        accountSetup = store,
    )

    @Test
    fun `a sign-out wipe sends a set-up phone back to onboarding, after the ledger is gone`() = runTest {
        val steps = mutableListOf<String>()
        val store = PrefsStore(mutablePreferencesOf(onboarding to true, theme to "DARK"), steps)
        val marker = Marker()
        reset(marker, store, steps).wipe()

        assertEquals(listOf("binding", "tables", "photos", "setup"), steps)
        assertEquals(false, store.prefs[onboarding])
        assertEquals(1, store.prefs[generation])
        assertEquals("DARK", store.prefs[theme])
        assertFalse(marker.isSet())
    }

    @Test
    fun `a wipe started inside onboarding keeps the onboarding that is running`() = runTest {
        val steps = mutableListOf<String>()
        val store = PrefsStore(mutablePreferencesOf(onboarding to false), steps)
        reset(Marker(), store, steps).wipe()
        assertEquals(false, store.prefs[onboarding])
        assertNull(store.prefs[generation]) // no new generation, so no second onboarding beside the running one
    }

    @Test
    fun `a wipe killed before the setup reset is finished on the next launch, once`() = runTest {
        val steps = mutableListOf<String>()
        val store = PrefsStore(mutablePreferencesOf(onboarding to true), steps)
        val marker = Marker()
        assertTrue(runCatching { reset(marker, store, steps, failTables = true).wipe() }.isFailure)
        assertTrue(marker.isSet())
        assertEquals(true, store.prefs[onboarding]) // not reset yet: the UI still shows the (empty) app until relaunch

        assertTrue(reset(marker, store, steps).resumeIfInterrupted())
        assertEquals(false, store.prefs[onboarding])
        assertEquals(1, store.prefs[generation])
        assertFalse(marker.isSet())
        assertFalse(reset(marker, store, steps).resumeIfInterrupted()) // nothing left to finish
        assertEquals(1, store.prefs[generation])
    }
}
