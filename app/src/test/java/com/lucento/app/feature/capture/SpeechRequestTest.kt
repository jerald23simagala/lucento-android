package com.lucento.app.feature.capture

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class SpeechRequestTest {

    @Test
    fun `an English phone is asked in its own English`() {
        assertEquals("en-PH", SpeechRequest.languageTag(Locale.forLanguageTag("en-PH")))
        assertEquals("en-GB", SpeechRequest.languageTag(Locale.forLanguageTag("en-GB")))
        assertEquals("en-US", SpeechRequest.languageTag(Locale.US))
    }

    @Test
    fun `any other phone language, or English without a country, asks for US English`() {
        // The interpreter reads English; speech services name languages with a region, so never a bare "en".
        assertEquals("en-US", SpeechRequest.languageTag(Locale.forLanguageTag("fil-PH")))
        assertEquals("en-US", SpeechRequest.languageTag(Locale.forLanguageTag("de-DE")))
        assertEquals("en-US", SpeechRequest.languageTag(Locale.forLanguageTag("en")))
    }
}
