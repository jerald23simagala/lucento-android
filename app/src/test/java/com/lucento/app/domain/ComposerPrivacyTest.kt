package com.lucento.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ComposerPrivacyTest {
    @Test
    fun `composer privacy follows whether gemini is on`() {
        assertEquals("Your entry is read on this phone and never sent anywhere.", ComposerPrivacy.line(geminiEnabled = false))
        assertEquals("Your entry is sent to Google Gemini through Lucento's server to be read.", ComposerPrivacy.line(geminiEnabled = true))
    }
}
