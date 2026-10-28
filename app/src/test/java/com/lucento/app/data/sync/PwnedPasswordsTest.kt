package com.lucento.app.data.sync

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PwnedPasswordsTest {

    @Test fun onlyTheFirstFiveHashCharactersWouldBeSent() {
        val hash = PwnedPasswordRange.sha1Hex("password")
        assertEquals(40, hash.length)
        assertEquals("5BAA6", PwnedPasswordRange.prefix(hash))
        assertTrue(hash.startsWith("5BAA6"))
        assertFalse(hash.contains("password"))
    }

    @Test fun aListedSuffixWithACountIsBreachedAndPaddingIsNot() {
        val hash = PwnedPasswordRange.sha1Hex("password")
        val suffix = hash.drop(5)
        val body = """
            00000000000000000000000000000000000:0
            $suffix:42
            FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF:0
        """.trimIndent()
        assertTrue(PwnedPasswordRange.isBreached(hash, body))
        assertFalse(PwnedPasswordRange.isBreached(hash, "$suffix:0"))
        assertFalse(PwnedPasswordRange.isBreached(hash, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA:9"))
    }

    @Test fun shortPasswordsAreRejectedBeforeAnyLookup() = runTest {
        var lookedUp: String? = null
        val policy = PasswordPolicy { prefix ->
            lookedUp = prefix
            null
        }
        assertEquals(
            "Use at least $MIN_PASSWORD_LENGTH characters for the password.",
            policy.problem("short"),
        )
        assertNull(lookedUp)
    }

    @Test fun aBreachedPasswordIsRejected() = runTest {
        val policy = PasswordPolicy { prefix ->
            assertEquals(5, prefix.length)
            val hash = PwnedPasswordRange.sha1Hex("password1")
            assertEquals(PwnedPasswordRange.prefix(hash), prefix)
            "${hash.drop(5)}:3"
        }
        assertEquals(
            "This password has appeared in a data breach. Choose a different one.",
            policy.problem("password1"),
        )
    }

    @Test fun aNetworkFailureDoesNotBlockThePassword() = runTest {
        val policy = PasswordPolicy { throw java.io.IOException("offline") }
        assertNull(policy.problem("a-long-unique-passphrase"))
    }

    @Test fun anUnknownRangeDoesNotBlockThePassword() = runTest {
        val policy = PasswordPolicy { null }
        assertNull(policy.problem("a-long-unique-passphrase"))
    }
}