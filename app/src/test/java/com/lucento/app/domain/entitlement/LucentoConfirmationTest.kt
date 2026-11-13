package com.lucento.app.domain.entitlement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * docs/MONETIZATION.md §5.3 L-6 (owner decisions 2026-11-13). Google Play's word alone carries a purchase for 3 days from
 * its purchase time until Lucento's server confirms it, then for 90 days after each confirmation. It pauses, it never ends.
 */
class LucentoConfirmationTest {
    private val day = 24 * 60 * 60 * 1000L
    private val now = 1_800_000_000_000L

    private fun playLifetime(boughtAt: Long, confirmedAt: Long?, readAt: Long = now) = Entitlement(
        source = EntitlementSource.PLAY_LIFETIME, status = EntitlementStatus.ACTIVE, grants = setOf(Capability.LOCAL_PRO),
        startsAtMillis = boughtAt, expiresAtMillis = null, productId = PlayProducts.PRO_LIFETIME, purchaseTokenHash = "a".repeat(64),
        verifiedAtMillis = readAt, verifiedBy = Verifier.PLAY_LOCAL, confirmedAtMillis = confirmedAt,
    )

    @Test fun a_purchase_the_server_hasnt_confirmed_counts_for_three_days_from_its_purchase_time() {
        val bought = now - day
        val row = playLifetime(boughtAt = bought, confirmedAt = null)
        assertTrue(EntitlementState(listOf(row)).has(Capability.LOCAL_PRO, now))
        val justBefore = bought + Entitlement.FIRST_CONFIRMATION_MILLIS - 1
        assertTrue(EntitlementState(listOf(row.copy(verifiedAtMillis = justBefore, checkedAtMillis = justBefore))).has(Capability.LOCAL_PRO, justBefore))
        val at = bought + Entitlement.FIRST_CONFIRMATION_MILLIS
        val late = EntitlementState(listOf(row.copy(verifiedAtMillis = at, checkedAtMillis = at)))
        assertFalse("Play read it this very moment, but its word alone isn't enough", late.has(Capability.LOCAL_PRO, at))
        assertTrue("paused, not ended", late.awaitingCheck(at))
        assertEquals(DisplayPlan.FREE, late.displayPlan(at))
    }

    @Test fun an_old_purchase_restored_without_the_server_waits_for_it_however_fresh_plays_reading_is() {
        // A data clear, a reinstall or a Play profile switch: no confirmation on this install, and Google signs the purchase
        // time, so nothing restarts the three days.
        val state = EntitlementState(listOf(playLifetime(boughtAt = now - 200 * day, confirmedAt = null)))
        assertFalse(state.has(Capability.LOCAL_PRO, now))
        assertTrue(state.awaitingCheck(now))
    }

    @Test fun a_confirmed_purchase_counts_for_ninety_days_after_the_last_confirmation_while_play_keeps_reading_it() {
        val confirmed = now
        val readLater = confirmed + (Entitlement.CONFIRMATION_CAP_MILLIS - 1)
        val row = playLifetime(boughtAt = now - 400 * day, confirmedAt = confirmed, readAt = readLater)
        assertTrue(EntitlementState(listOf(row)).has(Capability.LOCAL_PRO, readLater))
        val capped = confirmed + Entitlement.CONFIRMATION_CAP_MILLIS
        val atCap = EntitlementState(listOf(row.copy(verifiedAtMillis = capped, checkedAtMillis = capped)))
        assertFalse("a phone kept offline after a refund loses it at the cap", atCap.has(Capability.LOCAL_PRO, capped))
        assertTrue(atCap.awaitingCheck(capped))
    }

    @Test fun the_monthly_word_from_play_or_the_server_still_applies_on_top() {
        val row = playLifetime(boughtAt = now - 400 * day, confirmedAt = now, readAt = now)
        val month = now + Entitlement.RECHECK_MILLIS
        assertFalse("Play unreadable for a month (L-2)", EntitlementState(listOf(row)).has(Capability.LOCAL_PRO, month))
    }

    @Test fun server_rows_and_owner_grants_are_the_servers_own_word() {
        val server = playLifetime(boughtAt = now - 400 * day, confirmedAt = null).copy(verifiedBy = Verifier.SERVER)
        assertFalse(server.awaitsLucento(now))
        assertTrue(EntitlementState(listOf(server)).has(Capability.LOCAL_PRO, now))
        val grant = Entitlement(
            source = EntitlementSource.GRANT, status = EntitlementStatus.ACTIVE, grants = Capability.entries.toSet(),
            startsAtMillis = now - 400 * day, expiresAtMillis = now + day, productId = null, verifiedAtMillis = now,
            verifiedBy = Verifier.SERVER, confirmedAtMillis = null,
        )
        assertTrue(EntitlementState(listOf(grant)).has(Capability.LOCAL_PRO, now))
    }

    @Test fun a_row_built_without_the_field_counts_as_confirmed_when_read() {
        // Builds without the purchase check (debug, the plan simulator) and snapshots cached before it: Play's word.
        val row = Entitlement(
            source = EntitlementSource.PLAY_LIFETIME, status = EntitlementStatus.ACTIVE, grants = setOf(Capability.LOCAL_PRO),
            startsAtMillis = now - 400 * day, expiresAtMillis = null, productId = PlayProducts.PRO_LIFETIME,
            verifiedAtMillis = now, verifiedBy = Verifier.PLAY_LOCAL,
        )
        assertEquals(now, row.confirmedAtMillis)
        assertTrue(EntitlementState(listOf(row)).has(Capability.LOCAL_PRO, now))
    }

    @Test fun an_ended_purchase_reads_as_ended_not_as_waiting_for_a_check() {
        val ended = playLifetime(boughtAt = now - 200 * day, confirmedAt = null).copy(status = EntitlementStatus.EXPIRED)
        val state = EntitlementState(listOf(ended))
        assertFalse(state.has(Capability.LOCAL_PRO, now))
        assertFalse("the notice says it ended, not that it needs a check", state.awaitingCheck(now))
    }
}
