package com.lucento.app.data.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** F-7: a sign-out wipe resets the previous account's setup and keeps the phone's own choices. */
class AccountSetupResetTest {

    private val onboarding = booleanPreferencesKey("onboarding_complete")
    private val categorySetup = booleanPreferencesKey("category_setup_done")
    private val guide = stringPreferencesKey("guide_step")
    private val defaultWallet = longPreferencesKey("default_wallet_id")
    private val name = stringPreferencesKey("display_name")
    private val budgetMonth = stringPreferencesKey("acknowledged_budget_month")
    private val smart = booleanPreferencesKey("smart_reading")
    private val generation = intPreferencesKey("setup_generation")
    private val theme = stringPreferencesKey("theme_mode")
    private val currency = stringPreferencesKey("primary_currency")
    private val features = stringSetPreferencesKey("enabled_features")
    private val crash = booleanPreferencesKey("crash_reports")
    private val usage = booleanPreferencesKey("usage_counts")
    private val notice = booleanPreferencesKey("local_data_notice_seen")
    private val photo = longPreferencesKey("profile_photo_version")

    private fun accountASetUp() = mutablePreferencesOf(
        onboarding to true, categorySetup to true, guide to "budget", defaultWallet to 7L, name to "Alex", photo to 1_762_000_000_000L,
        budgetMonth to "2026-10", smart to true,
        theme to "DARK", currency to "EUR", features to setOf("budgets", "goals"), crash to true, usage to true, notice to true,
    )

    @Test
    fun `the previous account's setup is cleared so the next account goes through onboarding`() {
        val p = accountASetUp()
        UserPreferencesRepository.resetAccountSetup(p)
        assertEquals(false, p[onboarding])
        assertEquals(false, p[categorySetup])
        assertEquals(false, p[smart])
        assertNull(p[guide])
        assertNull(p[defaultWallet])
        assertNull(p[name])
        // The photo belongs to the account like the name (its file goes with the ledger wipe).
        assertNull(p[photo])
        assertNull(p[budgetMonth])
    }

    @Test
    fun `the phone's own choices and consents are kept`() {
        val p = accountASetUp()
        UserPreferencesRepository.resetAccountSetup(p)
        assertEquals("DARK", p[theme])
        assertEquals("EUR", p[currency])
        assertEquals(setOf("budgets", "goals"), p[features])
        assertEquals(true, p[crash])
        assertEquals(true, p[usage])
        assertEquals(true, p[notice])
    }

    @Test
    fun `a wipe after a finished onboarding starts a new setup generation, so that run is never reused`() {
        val p = accountASetUp()
        UserPreferencesRepository.resetAccountSetup(p)
        assertEquals(1, p[generation])
        // Set up again, then signed out again.
        p[onboarding] = true
        UserPreferencesRepository.resetAccountSetup(p)
        assertEquals(2, p[generation])
    }

    @Test
    fun `a wipe while onboarding is still running keeps its generation`() {
        val p = mutablePreferencesOf(onboarding to false, generation to 3)
        UserPreferencesRepository.resetAccountSetup(p)
        assertEquals(3, p[generation])
        assertEquals(false, p[onboarding])
    }
}
