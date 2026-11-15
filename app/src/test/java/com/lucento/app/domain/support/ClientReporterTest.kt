package com.lucento.app.domain.support

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** FD-V7: the app reports its version once a day, signed in only; a failure waits for the next day. */
class ClientReporterTest {

    private class Store : ClientReportStore {
        var mark: ClientReportMark? = null
        override fun last() = mark
        override fun mark(mark: ClientReportMark): Boolean {
            this.mark = mark
            return true
        }
    }

    private class Rpc(var fail: Boolean = false) : SupportRpc {
        val calls = mutableListOf<Pair<JsonObject, String?>>()
        override suspend fun call(function: String, body: JsonObject, bearer: String?): RpcAnswer {
            assertEquals("report_client", function)
            calls += body to bearer
            if (fail) throw IllegalStateException("offline")
            return RpcAnswer(200, """{"ok":true,"recorded":true}""")
        }
    }

    private val manila = ZoneId.of("Asia/Manila")
    // 2026-11-15 10:00 in Manila.
    private var clock = java.time.ZonedDateTime.of(2026, 11, 15, 10, 0, 0, 0, manila).toInstant().toEpochMilli()
    private var token: String? = "token"

    private fun reporter(store: Store, rpc: Rpc) = ClientReporter(
        store = store,
        rpc = rpc,
        accessToken = { token },
        info = { ClientInfo("0.6.0", 15, 35, "Google Pixel 8") },
        now = { clock },
        zone = { manila },
    )

    @Test fun `never while signed out`() = runTest {
        val rpc = Rpc()
        assertFalse(reporter(Store(), rpc).reportIfDue(null))
        assertTrue(rpc.calls.isEmpty())
    }

    @Test fun `once a day per account, again the next day, and the same day for another account`() = runTest {
        val store = Store()
        val rpc = Rpc()
        val r = reporter(store, rpc)
        assertTrue(r.reportIfDue("account-a"))
        assertFalse(r.reportIfDue("account-a"))
        clock += 13 * 60 * 60 * 1000L // 23:00 the same day in Manila
        assertFalse(r.reportIfDue("account-a"))
        assertTrue(r.reportIfDue("account-b"))
        clock += 2 * 60 * 60 * 1000L // 01:00 the next day
        assertTrue(r.reportIfDue("account-b"))
        assertEquals(3, rpc.calls.size)
        val (body, bearer) = rpc.calls.first()
        assertEquals("token", bearer)
        assertEquals("0.6.0", body["p_app_version"]!!.jsonPrimitive.content)
        assertEquals(15, body["p_version_code"]!!.jsonPrimitive.content.toInt())
        assertEquals(35, body["p_android_sdk"]!!.jsonPrimitive.content.toInt())
        assertEquals("Google Pixel 8", body["p_device_model"]!!.jsonPrimitive.content)
    }

    @Test fun `a failure is silent and waits for the next day`() = runTest {
        val store = Store()
        val rpc = Rpc(fail = true)
        val r = reporter(store, rpc)
        assertTrue(r.reportIfDue("account-a"))
        assertFalse(r.reportIfDue("account-a"))
        assertEquals(1, rpc.calls.size)
    }

    @Test fun `no token yet — nothing sent and nothing marked`() = runTest {
        token = null
        val store = Store()
        val rpc = Rpc()
        assertFalse(reporter(store, rpc).reportIfDue("account-a"))
        assertTrue(rpc.calls.isEmpty())
        assertEquals(null, store.mark)
    }

    @Test fun `the gate is the calendar day, per account`() {
        assertTrue(ClientReportGate.due(null, "a", "2026-11-15"))
        assertFalse(ClientReportGate.due(ClientReportMark("a", "2026-11-15"), "a", "2026-11-15"))
        assertTrue(ClientReportGate.due(ClientReportMark("a", "2026-11-14"), "a", "2026-11-15"))
        assertTrue(ClientReportGate.due(ClientReportMark("b", "2026-11-15"), "a", "2026-11-15"))
    }
}
