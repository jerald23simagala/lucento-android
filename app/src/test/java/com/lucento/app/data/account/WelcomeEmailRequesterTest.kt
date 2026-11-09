package com.lucento.app.data.account

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WelcomeEmailRequesterTest {
    private class Memory : WelcomeMemory {
        val ids = mutableSetOf<String>()
        override fun has(userId: String) = userId in ids
        override fun add(userId: String) {
            ids += userId
        }
    }

    private fun requester(memory: WelcomeMemory, vararg replies: Int?, endpoint: String = "https://site/api/account/welcome"): Pair<WelcomeEmailRequester, () -> Int> {
        val queue = ArrayDeque(replies.toList())
        var calls = 0
        val r = WelcomeEmailRequester(endpoint, memory) {
            calls++
            queue.removeFirstOrNull()
        }
        return r to { calls }
    }

    @Test
    fun `only a 200 settles the account`() {
        assertTrue(WelcomeEmailRequester.settled(200))
        for (code in listOf(null, 401, 409, 500, 502, 503)) assertFalse("$code", WelcomeEmailRequester.settled(code))
    }

    @Test
    fun `an answered account is never asked about again`() = runTest {
        val memory = Memory()
        val (r, calls) = requester(memory, 200, 200)
        assertTrue(r.requestFor("a"))
        assertFalse(r.requestFor("a"))
        assertEquals(1, calls())
        assertEquals(setOf("a"), memory.ids)
    }

    @Test
    fun `not verified yet, offline or signed out asks again next time`() = runTest {
        val memory = Memory()
        val (r, calls) = requester(memory, 409, null, 502, 200)
        assertFalse(r.requestFor("a"))
        assertFalse(r.requestFor("a"))
        assertFalse(r.requestFor("a"))
        assertTrue(r.requestFor("a"))
        assertEquals(4, calls())
    }

    @Test
    fun `each account is asked about on its own`() = runTest {
        val memory = Memory()
        val (r, _) = requester(memory, 200, 200)
        r.requestFor("a")
        r.requestFor("b")
        assertEquals(setOf("a", "b"), memory.ids)
    }

    @Test
    fun `a build without the site never asks`() = runTest {
        val (r, calls) = requester(Memory(), 200, endpoint = "")
        assertFalse(r.requestFor("a"))
        assertEquals(0, calls())
    }

    @Test
    fun `a network failure is not fatal`() = runTest {
        val memory = Memory()
        val r = WelcomeEmailRequester("https://site/api/account/welcome", memory) { throw java.io.IOException("offline") }
        assertFalse(r.requestFor("a"))
        assertTrue(memory.ids.isEmpty())
    }
}
