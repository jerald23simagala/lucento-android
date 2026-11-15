package com.lucento.app.domain.support

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contact support's outbox (docs/SUPPORT.md): saved before sending, sent once, never into the wrong account. */
class SupportServiceTest {

    private class FakeStore(var failWrites: Boolean = false) : SupportOutboxStore {
        val waiting = mutableListOf<PendingSupportRequest>()
        val done = mutableListOf<SupportHistoryItem>()
        var installCalls = 0
        override fun pending() = waiting.toList()
        override fun add(request: PendingSupportRequest): Boolean {
            if (failWrites) return false
            waiting.removeAll { it.clientRef == request.clientRef }
            waiting += request
            return true
        }
        override fun remove(clientRef: String) = waiting.removeAll { it.clientRef == clientRef }.let { true }
        override fun history() = done.toList()
        override fun record(item: SupportHistoryItem) = done.add(item)
        override fun installId(): String {
            installCalls++
            return "install-1"
        }
    }

    private data class Call(val function: String, val body: JsonObject, val bearer: String?)

    private class FakeRpc(val answers: ArrayDeque<RpcAnswer>) : SupportRpc {
        val calls = mutableListOf<Call>()
        override suspend fun call(function: String, body: JsonObject, bearer: String?): RpcAnswer {
            calls += Call(function, body, bearer)
            return answers.removeFirstOrNull() ?: RpcAnswer(null, null)
        }
    }

    private fun sent(ticket: String, replyTo: String = "ana@example.com") = RpcAnswer(200, """{"ok":true,"ticket":"$ticket","reply_to":"$replyTo"}""")
    private fun error(code: String) = RpcAnswer(200, """{"ok":false,"error":"$code"}""")
    private val offline = RpcAnswer(null, null)

    private var clock = 1_000_000L
    private var retries = 0
    private var user: String? = null
    private var token: String? = "token-a"

    private fun service(store: SupportOutboxStore, rpc: SupportRpc?, refs: Iterator<String> = generateSequence(1) { it + 1 }.map { "ref-$it" }.iterator()) =
        SupportService(
            store = store,
            rpc = rpc,
            signedInUserId = { user },
            accessToken = { token },
            scheduleRetry = { retries++ },
            now = { clock },
            newClientRef = { refs.next() },
        )

    @Test fun `signed out, online — saved, sent without an account to the typed address, recorded with its ticket`() = runTest {
        val store = FakeStore()
        val rpc = FakeRpc(ArrayDeque(listOf(sent("LC-1001"))))
        val result = service(store, rpc).submit(SupportTopic.BILLING, "It says Free", " Ana@Example.com ", accountId = null, diagnostics = JsonObject(emptyMap()))
        assertEquals(SubmitOutcome.Sent("LC-1001", "ana@example.com"), result.outcome)
        assertFalse(result.queued)
        assertTrue(store.waiting.isEmpty())
        assertEquals("LC-1001", store.done.single().ticket)
        val call = rpc.calls.single()
        assertEquals("submit_support_request", call.function)
        assertNull(call.bearer)
        assertEquals("ana@example.com", call.body["p_email"]!!.jsonPrimitive.content)
        assertEquals("install-1", call.body["p_install_id"]!!.jsonPrimitive.content)
        assertEquals("ref-1", call.body["p_client_ref"]!!.jsonPrimitive.content)
    }

    @Test fun `offline — kept and handed to the worker, then sent later with the same request id`() = runTest {
        val store = FakeStore()
        val rpc = FakeRpc(ArrayDeque(listOf(offline, offline, sent("LC-1002"))))
        val support = service(store, rpc)
        val result = support.submit(SupportTopic.SYNC, "Missing transactions", "ana@example.com", null, JsonObject(emptyMap()))
        assertEquals(SubmitOutcome.TryLater, result.outcome)
        assertTrue(result.queued)
        assertEquals(1, retries)
        assertEquals(1, store.waiting.size)

        assertFalse(support.drain()) // still offline
        assertTrue(support.drain())
        assertTrue(store.waiting.isEmpty())
        assertEquals("LC-1002", store.done.single().ticket)
        // Every attempt carried the one id made when it was written: the server files one ticket, whatever got lost.
        assertEquals(setOf("ref-1"), rpc.calls.map { it.body["p_client_ref"]!!.jsonPrimitive.content }.toSet())
        assertEquals(3, rpc.calls.size)
    }

    @Test fun `signed in — sent with its own account, without repeating the email`() = runTest {
        user = "account-a"
        val store = FakeStore()
        val rpc = FakeRpc(ArrayDeque(listOf(sent("LC-1003"))))
        service(store, rpc).submit(SupportTopic.ACCOUNT, "Can't sign in on my tablet", "ana@example.com", accountId = "account-a", diagnostics = JsonObject(emptyMap()))
        val call = rpc.calls.single()
        assertEquals("token-a", call.bearer)
        assertEquals(JsonNull, call.body["p_email"])
    }

    @Test fun `written signed in, sent after another account signed in — goes without an account, to the writer's address`() = runTest {
        user = "account-a"
        val store = FakeStore()
        val rpc = FakeRpc(ArrayDeque(listOf(offline, sent("LC-1004"))))
        val support = service(store, rpc)
        support.submit(SupportTopic.BUG, "Crash on Budget", "ana@example.com", accountId = "account-a", diagnostics = JsonObject(emptyMap()))
        user = "account-b"
        token = "token-b"
        assertTrue(support.drain())
        val later = rpc.calls.last()
        assertNull(later.bearer)
        assertEquals("ana@example.com", later.body["p_email"]!!.jsonPrimitive.content)
    }

    @Test fun `its own account signed in but no token yet — waits instead of sending it unlinked`() = runTest {
        user = "account-a"
        token = null
        val store = FakeStore()
        val rpc = FakeRpc(ArrayDeque())
        val result = service(store, rpc).submit(SupportTopic.SYNC, "Not syncing", "ana@example.com", accountId = "account-a", diagnostics = JsonObject(emptyMap()))
        assertEquals(SubmitOutcome.TryLater, result.outcome)
        assertTrue(rpc.calls.isEmpty())
        assertEquals(1, store.waiting.size)
    }

    @Test fun `a refusal is shown, not kept or retried`() = runTest {
        val store = FakeStore()
        val rpc = FakeRpc(ArrayDeque(listOf(error("rate_limited"))))
        val result = service(store, rpc).submit(SupportTopic.IDEA, "Dark mode", "ana@example.com", null, JsonObject(emptyMap()))
        assertEquals(SubmitOutcome.Refused(Refusal.RATE_LIMITED), result.outcome)
        assertTrue(store.waiting.isEmpty())
        assertTrue(store.done.isEmpty())
        assertEquals(0, retries)
    }

    @Test fun `in the queue, a limit is waited out, a bad request is given up`() = runTest {
        val store = FakeStore()
        val rpc = FakeRpc(ArrayDeque(listOf(offline, offline, error("busy"), error("invalid_email"))))
        val support = service(store, rpc)
        support.submit(SupportTopic.IDEA, "First", "ana@example.com", null, JsonObject(emptyMap()))
        clock += 1
        support.submit(SupportTopic.IDEA, "Second", "not-an-address@x", null, JsonObject(emptyMap()))
        assertFalse(support.drain())
        assertEquals(listOf("First"), store.waiting.map { it.message })
        assertFalse(store.done.single().sent)
    }

    @Test fun `a request that couldn't go for a month is given up without trying`() = runTest {
        val store = FakeStore()
        val rpc = FakeRpc(ArrayDeque(listOf(offline)))
        val support = service(store, rpc)
        support.submit(SupportTopic.OTHER, "Old", "ana@example.com", null, JsonObject(emptyMap()))
        clock += SupportService.MAX_AGE_MILLIS + 1
        assertTrue(support.drain())
        assertEquals(1, rpc.calls.size)
        assertFalse(store.done.single().sent)
    }

    @Test fun `nothing written down — never claims it will be sent`() = runTest {
        val store = FakeStore(failWrites = true)
        val rpc = FakeRpc(ArrayDeque(listOf(offline)))
        val result = service(store, rpc).submit(SupportTopic.OTHER, "Hello", "ana@example.com", null, JsonObject(emptyMap()))
        assertEquals(SubmitOutcome.Refused(Refusal.INVALID), result.outcome)
        assertFalse(result.queued)
        assertEquals(0, retries)
    }

    @Test fun `a build without Lucento's server offers email instead`() = runTest {
        val support = service(FakeStore(), rpc = null)
        assertFalse(support.available)
        assertTrue(support.drain())
    }

    @Test fun `the support install id is asked of the store, never invented per request`() = runTest {
        val store = FakeStore()
        val rpc = FakeRpc(ArrayDeque(listOf(sent("LC-1"), sent("LC-2"))))
        val support = service(store, rpc)
        support.submit(SupportTopic.BUG, "One", "ana@example.com", null, JsonObject(emptyMap()))
        support.submit(SupportTopic.BUG, "Two", "ana@example.com", null, JsonObject(emptyMap()))
        assertEquals(setOf("install-1"), rpc.calls.map { it.body["p_install_id"]!!.jsonPrimitive.content }.toSet())
        assertEquals(listOf("ref-1", "ref-2"), rpc.calls.map { it.body["p_client_ref"]!!.jsonPrimitive.content })
    }
}
