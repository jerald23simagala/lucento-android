package com.lucento.app.feature.account

import com.lucento.app.data.sync.AuthRepository.EmailCheck
import com.lucento.app.data.sync.AuthRepository.ResendResult
import com.lucento.app.feature.account.EmailVerification.Notice
import com.lucento.app.feature.account.EmailVerification.State
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Check your inbox" (owner, 2026-11-06): notices the verification by itself, never in the background. */
@OptIn(ExperimentalCoroutinesApi::class)
class EmailVerificationTest {

    private class Harness(scope: TestScope, restored: String? = null) {
        var answer = EmailCheck.PENDING
        var resendAnswer = ResendResult.SENT
        val checks = mutableListOf<Pair<String, String>>()
        var resends = 0
        val verified = mutableListOf<String>()
        val persisted = mutableListOf<String?>()
        val verification = EmailVerification(
            scope = scope.backgroundScope,
            check = { email, password ->
                checks += email to password
                answer
            },
            resend = {
                resends++
                resendAnswer
            },
            onVerified = { verified += it },
            persist = { persisted += it },
            restoredEmail = restored,
            pollMillis = 15_000,
            beatMillis = 1_000,
            resendCooldownSeconds = 60,
        )
        val state get() = verification.state.value
    }

    @Test
    fun `waits after sign-up, and remembers only the address`() = runTest {
        val h = Harness(this)
        h.verification.start("ana@example.com", "s3cret-pass")
        runCurrent()
        val state = h.state as State.Waiting
        assertEquals("ana@example.com", state.email)
        assertEquals("the first email was just sent", 60, state.resendInSeconds)
        assertEquals(listOf<String?>("ana@example.com"), h.persisted)
    }

    @Test
    fun `coming back to Lucento checks at once, then every 15 seconds while on screen`() = runTest {
        val h = Harness(this)
        h.verification.start("ana@example.com", "pw-12345678")
        runCurrent()
        assertTrue("nothing is checked before Lucento is on screen", h.checks.isEmpty())
        h.verification.onVisible(true)
        runCurrent()
        assertEquals(1, h.checks.size)
        advanceTimeBy(15_001)
        assertEquals(2, h.checks.size)
        advanceTimeBy(15_000)
        assertEquals(3, h.checks.size)
        assertEquals("ana@example.com" to "pw-12345678", h.checks.last())
    }

    @Test
    fun `nothing is checked in the background`() = runTest {
        val h = Harness(this)
        h.verification.start("ana@example.com", "pw-12345678")
        h.verification.onVisible(true)
        runCurrent()
        h.verification.onVisible(false)
        val before = h.checks.size
        advanceTimeBy(120_000)
        assertEquals(before, h.checks.size)
    }

    @Test
    fun `once confirmed it says so, then carries on and stops checking`() = runTest {
        val h = Harness(this)
        h.verification.start("ana@example.com", "pw-12345678")
        h.verification.onVisible(true)
        h.answer = EmailCheck.CONFIRMED
        runCurrent()
        assertEquals(State.Verified("ana@example.com"), h.state)
        assertTrue("carries on only after the success has been seen", h.verified.isEmpty())
        advanceTimeBy(1_001)
        assertEquals(listOf("ana@example.com"), h.verified)
        assertNull(h.state)
        assertEquals("the saved address is cleared", null, h.persisted.last())
        val checks = h.checks.size
        advanceTimeBy(60_000)
        assertEquals(checks, h.checks.size)
    }

    @Test
    fun `offline keeps waiting and says so, and clears the note once the server answers`() = runTest {
        val h = Harness(this)
        h.verification.start("ana@example.com", "pw-12345678")
        h.answer = EmailCheck.UNREACHABLE
        h.verification.onVisible(true)
        runCurrent()
        assertEquals(Notice.OFFLINE, (h.state as State.Waiting).notice)
        h.answer = EmailCheck.PENDING
        advanceTimeBy(15_001)
        assertNull((h.state as State.Waiting).notice)
    }

    @Test
    fun `after a restart the password is asked for once, then checking resumes`() = runTest {
        val h = Harness(this, restored = "ana@example.com")
        h.verification.onVisible(true)
        runCurrent()
        assertTrue((h.state as State.Waiting).needsPassword)
        assertTrue("no password, no check", h.checks.isEmpty())
        h.verification.providePassword("pw-12345678")
        runCurrent()
        assertEquals(listOf("ana@example.com" to "pw-12345678"), h.checks)
        assertEquals(false, (h.state as State.Waiting).needsPassword)
    }

    @Test
    fun `a password that doesn't match stops checking and asks again`() = runTest {
        val h = Harness(this)
        h.verification.start("ana@example.com", "wrong-password")
        h.answer = EmailCheck.WRONG_PASSWORD
        h.verification.onVisible(true)
        runCurrent()
        val state = h.state as State.Waiting
        assertTrue(state.needsPassword)
        assertEquals(Notice.WRONG_PASSWORD, state.notice)
        val checks = h.checks.size
        advanceTimeBy(60_000)
        assertEquals(checks, h.checks.size)
    }

    @Test
    fun `send again waits out the minute, then sends and starts the minute again`() = runTest {
        val h = Harness(this)
        h.verification.start("ana@example.com", "pw-12345678")
        runCurrent()
        h.verification.resendEmail()
        runCurrent()
        assertEquals("still inside the first minute", 0, h.resends)
        advanceTimeBy(61_000)
        assertEquals(0, (h.state as State.Waiting).resendInSeconds)
        h.verification.resendEmail()
        runCurrent()
        assertEquals(1, h.resends)
        val state = h.state as State.Waiting
        assertEquals(Notice.SENT_AGAIN, state.notice)
        assertEquals(60, state.resendInSeconds)
    }

    @Test
    fun `an address that already had a recent email can be sent another at once`() = runTest {
        val h = Harness(this)
        h.verification.start("ana@example.com", "pw-12345678", emailJustSent = false)
        runCurrent()
        assertEquals(0, (h.state as State.Waiting).resendInSeconds)
        h.resendAnswer = ResendResult.TOO_SOON
        h.verification.resendEmail()
        runCurrent()
        assertEquals(Notice.TOO_SOON, (h.state as State.Waiting).notice)
    }

    @Test
    fun `using a different email stops everything and forgets the address`() = runTest {
        val h = Harness(this)
        h.verification.start("ana@example.com", "pw-12345678")
        h.verification.onVisible(true)
        runCurrent()
        h.verification.cancel()
        assertNull(h.state)
        assertNull(h.persisted.last())
        val checks = h.checks.size
        advanceTimeBy(60_000)
        assertEquals(checks, h.checks.size)
        assertTrue(h.verified.isEmpty())
    }
}
