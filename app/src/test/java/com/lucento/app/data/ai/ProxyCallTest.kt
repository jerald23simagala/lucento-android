package com.lucento.app.data.ai

import com.lucento.app.data.sync.TOKEN_REFRESH_MARGIN_MS
import com.lucento.app.data.sync.tokenNeedsRefresh
import com.lucento.app.domain.ask.AskSnapshot
import com.lucento.app.domain.ask.SnapshotFigure
import com.lucento.app.feature.ask.AskLucentoViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.time.LocalDate
import java.util.Collections
import kotlin.concurrent.thread

/**
 * Owner round 9: Ask Lucento's AI answers failed until something else woke the session. A stale token is refreshed
 * before sending, and a refused one (401) is refreshed and sent once more — against a real local HTTP server.
 */
class ProxyCallTest {

    private lateinit var server: ServerSocket
    /** The Authorization header of each request, in order. */
    private val seen: MutableList<String> = Collections.synchronizedList(mutableListOf())
    /** What the server answers, in order; the last one repeats. */
    @Volatile private var replies: List<Pair<Int, String>> = listOf(200 to "{}")

    private val endpoint get() = "http://127.0.0.1:${server.localPort}/api/ask"

    /** A one-connection-at-a-time HTTP/1.1 server: enough for HttpURLConnection's POST, without a JDK-only API. */
    @Before fun start() {
        server = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = try { server.accept() } catch (_: java.io.IOException) { break }
                socket.use { s ->
                    val input = s.getInputStream().buffered()
                    val headers = generateSequence { line(input).takeIf { it.isNotEmpty() } }.toList()
                    fun header(name: String) = headers.firstOrNull { it.startsWith("$name:", ignoreCase = true) }?.substringAfter(':')?.trim()
                    repeat(header("Content-Length")?.toInt() ?: 0) { input.read() }
                    val index = synchronized(seen) { seen += header("Authorization").orEmpty(); seen.size - 1 }
                    val (code, body) = replies[minOf(index, replies.lastIndex)]
                    val bytes = body.toByteArray()
                    val out = s.getOutputStream()
                    out.write("HTTP/1.1 $code Test\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    out.write(bytes)
                    out.flush()
                }
            }
        }
    }

    private fun line(input: java.io.InputStream): String {
        val bytes = java.io.ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1 || b == '\n'.code) break
            if (b != '\r'.code) bytes.write(b)
        }
        return bytes.toString(Charsets.UTF_8.name())
    }

    @After fun stop() = server.close()

    /** Hands out "old" first and "new" once asked to refresh; records each ask. */
    private class Tokens(private val fresh: String? = "new") {
        val asked = mutableListOf<Boolean>()
        suspend fun get(refresh: Boolean): String? {
            asked += refresh
            return if (refresh) fresh else "old"
        }
    }

    @Test fun aRefusedTokenIsRefreshedAndSentOnceMore() = runBlocking {
        replies = listOf(401 to "", 200 to """{"ok":true}""")
        val tokens = Tokens()
        val reply = postToProxy(endpoint, "{}", 5_000, tokens::get)
        assertEquals(ProxyReply(200, """{"ok":true}"""), reply)
        assertEquals(listOf("Bearer old", "Bearer new"), seen.toList())
        assertEquals(listOf(false, true), tokens.asked)
    }

    @Test fun aSessionThatReallyEndedIsTriedOnlyTwice() = runBlocking {
        replies = listOf(401 to "")
        val reply = postToProxy(endpoint, "{}", 5_000, Tokens()::get)
        assertEquals(401, reply?.code)
        assertEquals(2, seen.size)
    }

    @Test fun noFreshTokenMeansNoSecondRequest() = runBlocking {
        replies = listOf(401 to "")
        // The refresh failed (offline) and the same token came back, or the session is gone.
        assertEquals(401, postToProxy(endpoint, "{}", 5_000, Tokens(fresh = "old")::get)?.code)
        assertEquals(1, seen.size)
        assertEquals(401, postToProxy(endpoint, "{}", 5_000, Tokens(fresh = null)::get)?.code)
        assertEquals(2, seen.size)
    }

    @Test fun signedOutSendsNothing() = runBlocking {
        assertNull(postToProxy(endpoint, "{}", 5_000) { null })
        assertTrue(seen.isEmpty())
    }

    @Test fun otherFailuresAreNotRetried() = runBlocking {
        replies = listOf(502 to """{"error":"model_unavailable"}""")
        val tokens = Tokens()
        assertEquals(ProxyReply(502, null), postToProxy(endpoint, "{}", 5_000, tokens::get))
        assertEquals(listOf(false), tokens.asked)
        assertEquals(1, seen.size)
    }

    @Test fun askLucentoAnswersAfterTheTokenIsRefreshed() = runBlocking {
        replies = listOf(401 to "", 200 to """{"answer":"You've spent [[out]].","followUps":["And last month?"],"remaining":49}""")
        val snapshot = AskSnapshot("PHP", LocalDate.parse("2026-11-15"), listOf(SnapshotFigure("out", "Spent this month", "₱1,000.00")))
        val result = AskLucentoClient(Tokens()::get, endpoint).ask("How much have I spent?", emptyList(), snapshot)
        assertEquals(AskLucentoClient.Result.Answered("You've spent [[out]].", null, null, listOf("And last month?"), 49), result)
        assertEquals(listOf("Bearer old", "Bearer new"), seen.toList())
    }

    @Test fun aTokenIsRefreshedBeforeItExpires() {
        val now = 1_000_000_000L
        assertTrue(tokenNeedsRefresh(expiresAtMs = now - 1, nowMs = now))
        assertTrue(tokenNeedsRefresh(expiresAtMs = now + TOKEN_REFRESH_MARGIN_MS - 1, nowMs = now))
        assertFalse(tokenNeedsRefresh(expiresAtMs = now + TOKEN_REFRESH_MARGIN_MS, nowMs = now))
        assertFalse(tokenNeedsRefresh(expiresAtMs = now + 3_600_000, nowMs = now))
    }

    @Test fun anEndedSessionIsNotCalledAMissingAccount() {
        val refused = AskLucentoClient.Result.NotSignedIn
        assertEquals("AI answers need your sync account", AskLucentoViewModel.failureReason(refused, signedIn = false))
        assertTrue(AskLucentoViewModel.failureReason(refused, signedIn = true).startsWith("Your sign-in has expired"))
        assertEquals("Gemini took too long to answer", AskLucentoViewModel.failureReason(null, signedIn = true))
        assertEquals("Gemini couldn’t be reached", AskLucentoViewModel.failureReason(AskLucentoClient.Result.Failed("HTTP 502"), signedIn = true))
    }
}
