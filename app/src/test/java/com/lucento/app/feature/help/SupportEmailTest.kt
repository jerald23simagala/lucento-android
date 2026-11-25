package com.lucento.app.feature.help

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contact support's email (tester report 2026-11-25): room to write first, then the details the person can delete. */
class SupportEmailTest {

    @Test fun `the subject names the version`() {
        assertEquals("Lucento 0.5.20", SupportEmail.subject("0.5.20"))
    }

    @Test fun `the details sit under space for the message, labelled as removable`() {
        val body = SupportEmail.body("0.5.20", 25, "15", "Google", "Pixel 8")
        assertTrue("the person writes above the details", body.startsWith("\n\n\n"))
        assertTrue(body.contains("you can delete this"))
        assertTrue(body.endsWith("Lucento 0.5.20 (25) · Android 15 · Google Pixel 8"))
    }

    @Test fun `a model that already names its maker isn't doubled`() {
        assertTrue(SupportEmail.body("0.5.20", 25, "14", "samsung", "Samsung Galaxy A15").endsWith("Android 14 · Samsung Galaxy A15"))
        assertTrue(SupportEmail.body("0.5.20", 25, "14", "Xiaomi", "Redmi Note 13").endsWith("Android 14 · Xiaomi Redmi Note 13"))
    }
}
