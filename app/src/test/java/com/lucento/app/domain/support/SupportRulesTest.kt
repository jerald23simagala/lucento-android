package com.lucento.app.domain.support

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contact support's rules (docs/SUPPORT.md): what's attached, what's valid, and what each server answer means. */
class SupportRulesTest {

    private val details = PhoneDetails(
        appVersion = "0.6.0",
        versionCode = 15,
        androidRelease = "15",
        androidSdk = 35,
        manufacturer = "Google",
        model = "Pixel 8",
        syncEnabled = true,
        lastSyncAtMillis = 1_763_000_000_000L,
        plan = "Lucento Pro",
        signedIn = true,
        locale = "en-PH",
    )

    /** The keys the server keeps (mc.clean_diagnostics in 20261116090200_mission_control_functions.sql). */
    private val serverKeys = setOf(
        "app_version", "android_release", "device_model", "device_manufacturer", "last_sync_at", "plan", "locale",
        "version_code", "android_sdk", "sync_enabled", "signed_in",
    )

    @Test fun `attached details use only the keys the server keeps, typed as it expects`() {
        val d = SupportRules.diagnostics(details, include = true)
        assertTrue(serverKeys.containsAll(d.keys))
        assertEquals(serverKeys, d.keys)
        assertEquals("0.6.0", d["app_version"]!!.jsonPrimitive.content)
        assertTrue(d["app_version"]!!.jsonPrimitive.isString)
        assertEquals(15, d["version_code"]!!.jsonPrimitive.content.toInt())
        assertFalse(d["version_code"]!!.jsonPrimitive.isString)
        assertEquals(JsonPrimitive(true), d["sync_enabled"])
        assertEquals("2025-11-13T02:13:20Z", d["last_sync_at"]!!.jsonPrimitive.content)
    }

    @Test fun `leaving details out sends nothing at all`() {
        assertTrue(SupportRules.diagnostics(details, include = false).isEmpty())
        assertTrue(SupportRules.diagnostics(null, include = true).isEmpty())
    }

    @Test fun `long values are cut so the server never drops them, and a never-synced phone sends no sync time`() {
        val d = SupportRules.diagnostics(details.copy(model = "M".repeat(200), lastSyncAtMillis = null), include = true)
        assertEquals(SupportRules.MAX_DETAIL, d["device_model"]!!.jsonPrimitive.content.length)
        assertNull(d["last_sync_at"])
    }

    @Test fun `the preview shows every attached item in words`() {
        val lines = SupportRules.preview(details, nowMillis = 1_763_000_000_000L + 5 * 60_000).toMap()
        assertEquals("0.6.0 (15)", lines["App version"])
        assertEquals("15 (API 35)", lines["Android"])
        assertEquals("Google Pixel 8", lines["Phone"])
        assertEquals("On", lines["Cloud sync"])
        assertEquals("5 min ago", lines["Last sync"])
        assertEquals("Lucento Pro", lines["Plan shown"])
        assertEquals("Yes", lines["Signed in"])
        assertEquals("Never", SupportRules.preview(details.copy(lastSyncAtMillis = null), 0).toMap()["Last sync"])
    }

    @Test fun `the phone's name doesn't repeat its maker`() {
        assertEquals("Google Pixel 8", SupportRules.phoneName("Google", "Pixel 8"))
        assertEquals("Samsung SM-S921B", SupportRules.phoneName("samsung", "SM-S921B"))
        assertEquals("Xiaomi Redmi Note 13", SupportRules.phoneName("Xiaomi", "Redmi Note 13"))
        assertEquals("OnePlus 12", SupportRules.phoneName("OnePlus", "OnePlus 12"))
        assertEquals("Google", SupportRules.phoneName("Google", " "))
    }

    @Test fun `email addresses are checked the way the server checks them`() {
        assertTrue(SupportRules.isValidEmail("ana@example.com"))
        assertTrue(SupportRules.isValidEmail("  Ana.Reyes+lucento@Example.COM "))
        assertEquals("ana.reyes+lucento@example.com", SupportRules.normalizeEmail("  Ana.Reyes+lucento@Example.COM "))
        assertFalse(SupportRules.isValidEmail("ana@example"))
        assertFalse(SupportRules.isValidEmail("ana example@example.com"))
        assertFalse(SupportRules.isValidEmail("@example.com"))
        assertFalse(SupportRules.isValidEmail(""))
        assertFalse(SupportRules.isValidEmail("a".repeat(250) + "@x.co"))
    }

    @Test fun `the form says what's missing, and asks for an email only while signed out`() {
        assertEquals(FormProblem.NO_TOPIC, SupportRules.validate(null, "Hello there", "", signedIn = true))
        assertEquals(FormProblem.MESSAGE_EMPTY, SupportRules.validate(SupportTopic.BUG, "   ", "", signedIn = true))
        assertEquals(FormProblem.MESSAGE_TOO_LONG, SupportRules.validate(SupportTopic.BUG, "x".repeat(5001), "", signedIn = true))
        assertNull(SupportRules.validate(SupportTopic.BUG, "x".repeat(5000), "", signedIn = true))
        assertEquals(FormProblem.EMAIL_INVALID, SupportRules.validate(SupportTopic.BUG, "It crashes", "nope", signedIn = false))
        assertNull(SupportRules.validate(SupportTopic.BUG, "It crashes", "ana@example.com", signedIn = false))
        assertNull(SupportRules.validate(SupportTopic.BUG, "It crashes", "", signedIn = true))
    }

    @Test fun `topics match the server's values`() {
        assertEquals(
            listOf("account", "billing", "sync", "data_recovery", "bug", "idea", "other"),
            SupportTopic.entries.map { it.key },
        )
        assertEquals(SupportTopic.SYNC, SupportTopic.fromKey("sync"))
        assertNull(SupportTopic.fromKey("weather"))
    }

    @Test fun `server answers become sent, refused or later`() {
        assertEquals(SubmitOutcome.Sent("LC-1042", "ana@example.com"), SupportRules.outcome(200, """{"ok":true,"ticket":"LC-1042","reply_to":"ana@example.com"}"""))
        assertEquals(SubmitOutcome.Sent("LC-1042", "ana@example.com"), SupportRules.outcome(200, """{"ok":true,"ticket":"LC-1042","reply_to":"ana@example.com","duplicate":true}"""))
        assertEquals(SubmitOutcome.Refused(Refusal.RATE_LIMITED), SupportRules.outcome(200, """{"ok":false,"error":"rate_limited"}"""))
        assertEquals(SubmitOutcome.Refused(Refusal.BUSY), SupportRules.outcome(200, """{"ok":false,"error":"busy"}"""))
        assertEquals(SubmitOutcome.Refused(Refusal.INVALID_EMAIL), SupportRules.outcome(200, """{"ok":false,"error":"invalid_email"}"""))
        assertEquals(SubmitOutcome.Refused(Refusal.MESSAGE_EMPTY), SupportRules.outcome(200, """{"ok":false,"error":"message_empty"}"""))
        assertEquals(SubmitOutcome.Refused(Refusal.MESSAGE_TOO_LONG), SupportRules.outcome(200, """{"ok":false,"error":"message_too_long"}"""))
        assertEquals(SubmitOutcome.Refused(Refusal.INVALID), SupportRules.outcome(200, """{"ok":false,"error":"invalid_topic"}"""))
        assertEquals(SubmitOutcome.TryLater, SupportRules.outcome(200, "not json"))
        assertEquals(SubmitOutcome.TryLater, SupportRules.outcome(200, """{"ok":true}"""))
        assertEquals(SubmitOutcome.TryLater, SupportRules.outcome(null, null))
        listOf(401, 403, 404, 408, 429, 500, 503).forEach { assertEquals("HTTP $it", SubmitOutcome.TryLater, SupportRules.outcome(it, "")) }
        assertEquals(SubmitOutcome.Refused(Refusal.INVALID), SupportRules.outcome(400, """{"message":"bad"}"""))
    }

    @Test fun `limits are worth waiting out in the queue, bad requests are not`() {
        assertFalse(Refusal.RATE_LIMITED.finalForQueue)
        assertFalse(Refusal.BUSY.finalForQueue)
        assertTrue(Refusal.INVALID_EMAIL.finalForQueue)
        assertTrue(Refusal.INVALID.finalForQueue)
        assertTrue(Refusal.RATE_LIMITED.message.contains(SupportRules.SUPPORT_EMAIL))
        assertTrue(Refusal.BUSY.message.contains(SupportRules.SUPPORT_EMAIL))
    }

    @Test fun `the request body carries what the server function takes`() {
        val body = SupportRules.requestBody("bug", "  It crashes  ", "ana@example.com", "install-1", "ref-1", SupportRules.diagnostics(details, true))
        assertEquals(setOf("p_topic", "p_message", "p_email", "p_install_id", "p_client_ref", "p_diagnostics"), body.keys)
        assertEquals("It crashes", body["p_message"]!!.jsonPrimitive.content)
        assertEquals("ref-1", body["p_client_ref"]!!.jsonPrimitive.content)
    }
}
