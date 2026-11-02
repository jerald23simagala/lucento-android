package com.lucento.app.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** P2-10: when the optional app lock asks again, and when it must not. */
class AppLockSessionTest {

    private val session = AppLockSession(graceMillis = 60_000)
    private val locked get() = AppLockSession.isLocked(lockEnabled = true, unlocked = session.unlocked.value)

    @Test
    fun `a cold start is locked, and nothing is locked while the setting is off`() {
        assertTrue(locked)
        assertFalse(AppLockSession.isLocked(lockEnabled = false, unlocked = false))
    }

    @Test
    fun `a short trip away keeps it unlocked, a long one locks again`() {
        session.unlock()
        session.onBackground(1_000)
        session.onForeground(61_000) // exactly the grace period
        assertFalse(locked)

        session.onBackground(100_000)
        session.onForeground(160_001)
        assertTrue(locked)
    }

    @Test
    fun `time in the system prompt never counts as being away`() {
        session.onBackground(0)
        session.onForeground(1_000)
        session.beginAuthentication()
        session.onBackground(2_000) // the device-credential screen is another activity on older Android
        session.unlock()
        session.onForeground(500_000)
        assertFalse(locked)
    }

    @Test
    fun `a cancelled prompt stays locked and later trips count again`() {
        session.beginAuthentication()
        session.endAuthentication()
        assertTrue(locked)
        session.unlock()
        session.onBackground(0)
        session.onForeground(120_000)
        assertTrue(locked)
    }

    @Test
    fun `foreground without a recorded background changes nothing`() {
        session.unlock()
        session.onForeground(10_000_000)
        assertFalse(locked)
    }
}
