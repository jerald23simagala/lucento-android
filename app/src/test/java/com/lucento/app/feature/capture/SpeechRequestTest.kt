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

    @Test
    fun `the microphone listens itself when it can, and falls back to the speech service's window`() {
        // The composer and Ask Lucento share this order (VoiceInput).
        assertEquals(MicRoute.LISTEN, SpeechRequest.route(listening = false, recognitionAvailable = true, microphoneAllowed = true))
        assertEquals(MicRoute.ASK_PERMISSION, SpeechRequest.route(listening = false, recognitionAvailable = true, microphoneAllowed = false))
        // No speech service to drive: its own window, which needs no permission, whatever the microphone's state.
        assertEquals(MicRoute.SERVICE_WINDOW, SpeechRequest.route(listening = false, recognitionAvailable = false, microphoneAllowed = true))
        assertEquals(MicRoute.SERVICE_WINDOW, SpeechRequest.route(listening = false, recognitionAvailable = false, microphoneAllowed = false))
    }

    @Test
    fun `a second tap while listening does nothing`() {
        assertEquals(MicRoute.BUSY, SpeechRequest.route(listening = true, recognitionAvailable = true, microphoneAllowed = true))
        assertEquals(MicRoute.BUSY, SpeechRequest.route(listening = true, recognitionAvailable = false, microphoneAllowed = false))
    }

    @Test
    fun `words that can't be sent yet go into an empty field as they are`() {
        assertEquals("How much did I spend this month?", SpeechRequest.intoField("", "How much did I spend this month?", 500))
        assertEquals("lunch 250", SpeechRequest.intoField("   ", "  lunch 250 ", 1_000))
    }

    @Test
    fun `they follow anything already typed instead of replacing it`() {
        assertEquals("Lunch 250 coffee 120", SpeechRequest.intoField("Lunch 250", "coffee 120", 1_000))
        assertEquals("What’s my net worth? And my debts", SpeechRequest.intoField("What’s my net worth?  ", "And my debts", 500))
    }

    @Test
    fun `nothing heard leaves the field alone, and the field's limit holds`() {
        assertEquals("Lunch 250", SpeechRequest.intoField("Lunch 250", "   ", 1_000))
        assertEquals("abc de", SpeechRequest.intoField("abc", "defgh", 6))
    }
}
