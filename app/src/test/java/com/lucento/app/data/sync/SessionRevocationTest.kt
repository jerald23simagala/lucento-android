package com.lucento.app.data.sync

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/** R10-01 / N-04: an ended session is revoked on the server even after a kill, offline, or with an expired token. */
class SessionRevocationTest {

    private fun jwt(sessionId: String?) =
        "x." + Base64.getUrlEncoder().withoutPadding().encodeToString(
            (if (sessionId == null) "{\"sub\":\"u\"}" else "{\"sub\":\"u\",\"session_id\":\"$sessionId\"}").toByteArray(),
        ) + ".sig"

    private fun record(id: String = "s1", created: Long = 1_000L) = PendingRevoke(id, jwt(id), "refresh-$id", created)

    private class FakeHttp(
        val logouts: MutableList<HttpOutcome>,
        val refresh: RefreshOutcome = RefreshOutcome.Token("fresh"),
    ) : RevokeHttp {
        val calls = mutableListOf<String>()
        override suspend fun logout(accessToken: String): HttpOutcome { calls += "logout:$accessToken"; return logouts.removeAt(0) }
        override suspend fun refresh(refreshToken: String): RefreshOutcome { calls += "refresh:$refreshToken"; return refresh }
    }

    private suspend fun revoke(http: FakeHttp, rec: PendingRevoke = record(), now: Long = 2_000L) = SessionRevoker.revoke(rec, http, now)

    @Test
    fun `a live access token is revoked directly`() = runTest {
        val http = FakeHttp(mutableListOf(HttpOutcome.Status(204)))
        assertEquals(SessionRevoker.Result.DONE, revoke(http))
        assertEquals(1, http.calls.size)
    }

    @Test
    fun `an expired access token is refreshed, then the session is revoked with the new one`() = runTest {
        val http = FakeHttp(mutableListOf(HttpOutcome.Status(401), HttpOutcome.Status(204)))
        assertEquals(SessionRevoker.Result.DONE, revoke(http))
        assertEquals(listOf("logout:${jwt("s1")}", "refresh:refresh-s1", "logout:fresh"), http.calls)
    }

    @Test
    fun `a refresh token already used, revoked or unknown means the session is already unusable`() = runTest {
        listOf("refresh_token_already_used", "refresh_token_not_found", "session_not_found").forEach { code ->
            val http = FakeHttp(mutableListOf(HttpOutcome.Status(401)), RefreshOutcome.Failed(HttpOutcome.Status(400, code)))
            assertEquals(code, SessionRevoker.Result.DONE, revoke(http))
        }
        val plain400 = FakeHttp(mutableListOf(HttpOutcome.Status(403)), RefreshOutcome.Failed(HttpOutcome.Status(400)))
        assertEquals(SessionRevoker.Result.DONE, revoke(plain400))
    }

    @Test
    fun `offline, rate-limited or a server error is retried later`() = runTest {
        assertEquals(SessionRevoker.Result.RETRY, revoke(FakeHttp(mutableListOf(HttpOutcome.NetworkError))))
        assertEquals(SessionRevoker.Result.RETRY, revoke(FakeHttp(mutableListOf(HttpOutcome.Status(429)))))
        assertEquals(SessionRevoker.Result.RETRY, revoke(FakeHttp(mutableListOf(HttpOutcome.Status(503)))))
        assertEquals(SessionRevoker.Result.RETRY, revoke(FakeHttp(mutableListOf(HttpOutcome.Status(401)), RefreshOutcome.Failed(HttpOutcome.NetworkError))))
        assertEquals(SessionRevoker.Result.RETRY, revoke(FakeHttp(mutableListOf(HttpOutcome.Status(401)), RefreshOutcome.Failed(HttpOutcome.Status(500)))))
        assertEquals(SessionRevoker.Result.RETRY, revoke(FakeHttp(mutableListOf(HttpOutcome.Status(401), HttpOutcome.NetworkError))))
    }

    @Test
    fun `a session already gone on the server counts as revoked`() = runTest {
        assertEquals(SessionRevoker.Result.DONE, revoke(FakeHttp(mutableListOf(HttpOutcome.Status(404)))))
        assertEquals(SessionRevoker.Result.DONE, revoke(FakeHttp(mutableListOf(HttpOutcome.Status(403, "session_not_found")))))
    }

    @Test
    fun `records older than the limit are dropped without a call`() = runTest {
        val http = FakeHttp(mutableListOf())
        assertEquals(SessionRevoker.Result.DONE, revoke(http, record(created = 0), now = SessionRevoker.MAX_AGE_MILLIS + 1))
        assertTrue(http.calls.isEmpty())
    }

    // ------------------------------------------------------------------ the session id and launch safety

    @Test
    fun `the session id comes from the access token`() {
        assertEquals("s1", jwtSessionId(jwt("s1")))
        assertNull(jwtSessionId(jwt(null)))
        assertNull(jwtSessionId("not-a-jwt"))
        assertNull(jwtSessionId(null))
    }

    @Test
    fun `a stored session that a killed sign-out had ended is dropped, a newer sign-in is kept`() {
        val ended = listOf(record("s1"))
        assertTrue(endedSessionSurvived("""{"access_token":"${jwt("s1")}","refresh_token":"rotated"}""", ended))
        assertFalse(endedSessionSurvived("""{"access_token":"${jwt("s2")}","refresh_token":"r2"}""", ended))
        assertFalse(endedSessionSurvived(null, ended))
        assertFalse(endedSessionSurvived("""{"access_token":"${jwt("s1")}"}""", emptyList()))
        assertFalse(endedSessionSurvived("garbage", ended))
    }

    // ------------------------------------------------------------------ draining

    private class MemoryStore : PendingRevokeStore {
        val list = mutableListOf<PendingRevoke>()
        override fun all() = list.toList()
        override fun add(record: PendingRevoke): Boolean { list.removeAll { it.sessionId == record.sessionId }; list += record; return true }
        override fun remove(record: PendingRevoke): Boolean = list.removeAll { it.sessionId == record.sessionId && it.createdAtMillis == record.createdAtMillis }
    }

    @Test
    fun `two offline sign-outs keep both sessions, and each is removed only when revoked`() = runTest {
        val store = MemoryStore()
        store.add(record("s1"))
        store.add(record("s2"))
        assertEquals(2, store.all().size)
        val http = FakeHttp(mutableListOf(HttpOutcome.Status(204), HttpOutcome.NetworkError))
        val drainer = PendingRevokeDrainer(store, http = { http }, now = { 2_000L })
        assertFalse(drainer.drain())
        assertEquals(listOf("s2"), store.all().map { it.sessionId })
        val http2 = FakeHttp(mutableListOf(HttpOutcome.Status(204)))
        assertTrue(PendingRevokeDrainer(store, http = { http2 }, now = { 2_000L }).drain())
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun `a build without sync configured has nothing to drain`() = runTest {
        val store = MemoryStore().apply { add(record()) }
        assertTrue(PendingRevokeDrainer(store, http = { null }).drain())
        assertEquals(1, store.all().size) // kept: nothing was revoked
    }

    @Test
    fun `a record never prints its tokens`() {
        val text = PendingRevoke("sid-1", "access-secret", "refresh-secret", 5L).toString()
        assertTrue(text.contains("sid-1"))
        assertFalse(text.contains("access-secret"))
        assertFalse(text.contains("refresh-secret"))
    }
}
