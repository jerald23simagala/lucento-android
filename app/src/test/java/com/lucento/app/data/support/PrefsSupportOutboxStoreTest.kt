package com.lucento.app.data.support

import android.content.SharedPreferences
import com.lucento.app.domain.UsageLedger
import com.lucento.app.domain.UsageLedgerLogic
import com.lucento.app.domain.support.ClientReportMark
import com.lucento.app.domain.support.PendingSupportRequest
import com.lucento.app.domain.support.SupportHistoryItem
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Contact support's store on the phone (docs/SUPPORT.md): its own random install id, an outbox that survives the app being
 * killed, and what signing out forgets. Each test's "file" outlives the store objects, as the real one outlives a process.
 */
class PrefsSupportOutboxStoreTest {
    private val file = mutableMapOf<String, Any?>()
    private fun store() = PrefsSupportOutboxStore(MemoryPrefs(file))

    private fun request(ref: String) = PendingSupportRequest(
        clientRef = ref, topic = "bug", message = "The budget screen is blank", email = "ana@example.com", accountId = null,
        diagnostics = JsonObject(mapOf("app_version" to JsonPrimitive("0.6.0"))), createdAtMillis = 1_000L,
    )

    @Test fun the_support_install_id_is_made_on_first_use_and_kept() {
        assertTrue(file.isEmpty())
        val first = store().installId()
        UUID.fromString(first) // a random UUID
        assertEquals(first, file["support_install_id"])
        assertEquals("the same after the app restarts", first, store().installId())
    }

    @Test fun the_support_install_id_is_never_the_usage_counts_install_id() {
        // Usage counts are anonymous and tickets carry an email: one shared id would tie the counts to a person.
        val usage = UsageLedgerLogic.optIn(UsageLedger(), now = 0L, installId = UUID.randomUUID().toString())
        // Even with the usage id sitting under its own key in the same file, support never reads or reuses it.
        file["usage_install_id"] = usage.installId
        val support = store().installId()
        assertNotEquals(usage.installId, support)
        assertEquals("the usage id is left alone", usage.installId, file["usage_install_id"])
        assertEquals(support, store().installId())
    }

    @Test fun a_waiting_request_survives_a_restart_and_is_kept_once_per_client_ref() {
        assertTrue(store().add(request("ref-1")))
        assertTrue(store().add(request("ref-1")))
        assertTrue(store().add(request("ref-2")))
        assertEquals(listOf("ref-1", "ref-2"), store().pending().map { it.clientRef })
        assertEquals("ana@example.com", store().pending().first().email)
        assertTrue(store().remove("ref-1"))
        assertEquals(listOf("ref-2"), store().pending().map { it.clientRef })
        assertTrue(store().remove("ref-2"))
        assertEquals(emptyList<PendingSupportRequest>(), store().pending())
    }

    @Test fun at_most_ten_wait_and_five_finished_are_remembered_oldest_going_first() {
        for (i in 1..12) store().add(request("ref-$i"))
        assertEquals((3..12).map { "ref-$it" }, store().pending().map { it.clientRef })
        for (i in 1..7) store().record(SupportHistoryItem("ref-$i", "bug", "LC-${1000 + i}", "ana@example.com", sent = true, atMillis = i.toLong()))
        assertEquals((3..7).map { "LC-${1000 + it}" }, store().history().map { it.ticket })
    }

    @Test fun signing_out_forgets_the_recent_requests_but_not_what_is_still_waiting() {
        val s = store()
        s.add(request("ref-waiting"))
        s.record(SupportHistoryItem("ref-sent", "billing", "LC-1042", "ana@example.com", sent = true, atMillis = 5L))
        val id = s.installId()
        s.mark(ClientReportMark("user-a", "2026-11-15"))

        assertTrue(s.forgetHistory())

        val after = store()
        assertEquals(emptyList<SupportHistoryItem>(), after.history())
        assertEquals("a waiting request still goes as promised", listOf("ref-waiting"), after.pending().map { it.clientRef })
        assertEquals(id, after.installId())
        assertEquals(ClientReportMark("user-a", "2026-11-15"), after.last())
    }

    @Test fun an_unreadable_store_reads_as_empty_rather_than_failing() {
        file["pending_requests"] = "not json"
        file["finished_requests"] = "[{"
        file["client_report"] = "{}"
        assertEquals(emptyList<PendingSupportRequest>(), store().pending())
        assertEquals(emptyList<SupportHistoryItem>(), store().history())
        assertNull(store().last())
    }

    /** SharedPreferences over a map: commit() and apply() both write at once. */
    private class MemoryPrefs(private val file: MutableMap<String, Any?>) : SharedPreferences {
        override fun getAll(): MutableMap<String, *> = file.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = file[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            file[key] as? MutableSet<String> ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = file[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = file[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = file[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = file[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = file.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            val puts = mutableMapOf<String, Any?>()
            val removes = mutableSetOf<String>()
            var clearAll = false
            override fun putString(key: String, value: String?) = apply { puts[key] = value }
            override fun putStringSet(key: String, values: MutableSet<String>?) = apply { puts[key] = values }
            override fun putInt(key: String, value: Int) = apply { puts[key] = value }
            override fun putLong(key: String, value: Long) = apply { puts[key] = value }
            override fun putFloat(key: String, value: Float) = apply { puts[key] = value }
            override fun putBoolean(key: String, value: Boolean) = apply { puts[key] = value }
            override fun remove(key: String) = apply { removes += key }
            override fun clear() = apply { clearAll = true }
            override fun commit(): Boolean {
                if (clearAll) file.clear()
                removes.forEach(file::remove)
                file.putAll(puts)
                return true
            }
            override fun apply() {
                commit()
            }
        }
    }
}
