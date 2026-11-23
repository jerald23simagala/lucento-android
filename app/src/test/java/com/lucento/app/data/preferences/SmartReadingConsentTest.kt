package com.lucento.app.data.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner, 2026-11-23: "We turn off Smarter Reading". Its messages now use the AI questions Ask Lucento uses, so a consent
 * given before that no longer counts: everyone who had it on is asked again, with the new consent, in Settings.
 */
class SmartReadingConsentTest {

    private val firstConsent = booleanPreferencesKey("smart_reading")
    private val consent = booleanPreferencesKey("smart_reading_v2")

    @Test fun `it starts off`() {
        assertFalse(UserPreferencesRepository.smartReadingOn(preferencesOf()))
    }

    @Test fun `the first consent no longer turns it on`() {
        assertFalse(UserPreferencesRepository.smartReadingOn(preferencesOf(firstConsent to true)))
    }

    @Test fun `only the consent that names the shared allowance does`() {
        assertTrue(UserPreferencesRepository.smartReadingOn(preferencesOf(consent to true)))
        assertFalse(UserPreferencesRepository.smartReadingOn(preferencesOf(firstConsent to true, consent to false)))
    }
}
