package com.lucento.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When Google Play's rating card may be asked for (owner, 2026-11-25, after the closed test's tester report): a week after
 * install, 20 incomes and expenses, at most every 90 days, and never before the listing is public.
 */
class ReviewPromptPolicyTest {
    private val day = 86_400_000L
    private val installed = 1_800_000_000_000L
    private val now = installed + 30 * day

    private fun due(
        nowMillis: Long = now,
        installedAt: Long? = installed,
        recorded: Int = 25,
        lastAsked: Long? = null,
        public: Boolean = true,
    ) = ReviewPromptPolicy.isDue(nowMillis, installedAt, recorded, lastAsked, public)

    @Test fun `due once the week, the 20 records and the public listing are all there`() {
        assertTrue(due())
        assertTrue("exactly a week and exactly 20", due(nowMillis = installed + 7 * day, recorded = 20))
    }

    @Test fun `never while the listing isn't public, so closed testers are never asked`() {
        assertFalse(due(public = false))
    }

    @Test fun `not in the first week`() {
        assertFalse(due(nowMillis = installed + 7 * day - 1))
        assertFalse("install time unknown: never", due(installedAt = null))
        assertFalse("a clock behind the install time reads as too soon", due(nowMillis = installed - day))
    }

    @Test fun `not before 20 incomes and expenses`() {
        assertFalse(due(recorded = 19))
        assertFalse(due(recorded = 0))
    }

    @Test fun `at most once every 90 days`() {
        assertFalse(due(lastAsked = now - 89 * day))
        assertFalse(due(lastAsked = now - 90 * day + 1))
        assertTrue(due(lastAsked = now - 90 * day))
        assertFalse("a last ask in the future (clock moved back) reads as too soon", due(lastAsked = now + day))
    }
}
