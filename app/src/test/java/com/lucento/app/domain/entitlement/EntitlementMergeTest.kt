package com.lucento.app.domain.entitlement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The precedence rules of docs/MONETIZATION.md §5.1 and §5.3 (L-1 … L-5). */
class EntitlementMergeTest {
    private val day = 24 * 60 * 60 * 1000L
    private val now = 1_800_000_000_000L
    private val all = Capability.entries.toSet()

    private fun row(
        source: EntitlementSource,
        verifiedBy: Verifier,
        grants: Set<Capability> = all,
        status: EntitlementStatus = EntitlementStatus.ACTIVE,
        expiresAt: Long? = now + 30 * day,
        productId: String? = "p",
        verifiedAt: Long = now,
        tokenHash: String? = null,
    ) = Entitlement(
        source = source, status = status, grants = grants, startsAtMillis = now - day, expiresAtMillis = expiresAt,
        productId = productId, purchaseTokenHash = tokenHash, verifiedAtMillis = verifiedAt, verifiedBy = verifiedBy,
    )

    @Test fun a_successful_play_query_replaces_the_phones_own_rows_and_drops_what_play_no_longer_lists() {
        val oldSub = row(EntitlementSource.PLAY_SUBSCRIPTION, Verifier.PLAY_LOCAL, verifiedAt = now - day)
        val oldAddon = row(EntitlementSource.PLAY_CLOUD_ADDON, Verifier.PLAY_LOCAL, productId = "cloud")
        val freshSub = row(EntitlementSource.PLAY_SUBSCRIPTION, Verifier.PLAY_LOCAL)
        val merged = EntitlementMerge.reconcilePlay(listOf(oldSub, oldAddon), listOf(freshSub), now)
        assertEquals(listOf(freshSub), merged)
    }

    @Test fun play_never_touches_server_rows() {
        val serverGrant = row(EntitlementSource.GRANT, Verifier.SERVER, grants = setOf(Capability.LOCAL_PRO), productId = null)
        val serverLifetime = row(EntitlementSource.PLAY_LIFETIME, Verifier.SERVER, grants = setOf(Capability.LOCAL_PRO), expiresAt = null, productId = "life")
        // Another Google account is signed in to Play: the query succeeds and lists nothing.
        val merged = EntitlementMerge.reconcilePlay(listOf(serverGrant, serverLifetime), emptyList(), now)
        assertEquals(setOf(serverGrant, serverLifetime), merged.toSet())
    }

    @Test fun when_both_hold_a_key_the_servers_row_stands_while_it_is_current() {
        val server = row(EntitlementSource.PLAY_SUBSCRIPTION, Verifier.SERVER, expiresAt = now + 10 * day, verifiedAt = now - day)
        val play = row(EntitlementSource.PLAY_SUBSCRIPTION, Verifier.PLAY_LOCAL, expiresAt = now + 35 * day)
        assertEquals(listOf(server), EntitlementMerge.reconcilePlay(listOf(server), listOf(play), now))
    }

    @Test fun a_lapsed_server_row_gives_way_to_a_fresh_play_reading_so_a_renewal_shows_up_early() {
        val lapsed = row(EntitlementSource.PLAY_SUBSCRIPTION, Verifier.SERVER, expiresAt = now - 10 * day, verifiedAt = now - 12 * day)
        val play = row(EntitlementSource.PLAY_SUBSCRIPTION, Verifier.PLAY_LOCAL, expiresAt = now + 35 * day)
        assertEquals(listOf(play), EntitlementMerge.reconcilePlay(listOf(lapsed), listOf(play), now))
    }

    @Test fun a_revoked_server_row_with_no_purchase_to_match_gives_way_to_play_until_the_server_settles_it() {
        // A REVOKED row without a token can't be matched to Play's purchase (the same purchase is covered below).
        val revoked = row(EntitlementSource.PLAY_LIFETIME, Verifier.SERVER, grants = setOf(Capability.LOCAL_PRO), status = EntitlementStatus.REVOKED, expiresAt = null, productId = "life")
        val play = row(EntitlementSource.PLAY_LIFETIME, Verifier.PLAY_LOCAL, grants = setOf(Capability.LOCAL_PRO), expiresAt = null, productId = "life")
        // stillCurrent() is false for REVOKED, so the Play reading wins here — this is the one case where the phone can be
        // more generous than the server until the next server fetch, which reconcileServer settles. Pin the behaviour.
        val afterPlay = EntitlementMerge.reconcilePlay(listOf(revoked), listOf(play), now)
        assertEquals(listOf(play), afterPlay)
        val afterServer = EntitlementMerge.reconcileServer(afterPlay, listOf(revoked))
        assertEquals(listOf(revoked), afterServer)
        assertFalse(EntitlementState(afterServer).has(Capability.LOCAL_PRO, now))
    }

    @Test fun a_purchase_the_server_revoked_never_comes_back_from_plays_copy_of_it() {
        // Refund with revoke: the server says REVOKED, while Play's copy on the phone still lists the same purchase.
        val revoked = row(EntitlementSource.PLAY_LIFETIME, Verifier.SERVER, grants = setOf(Capability.LOCAL_PRO), status = EntitlementStatus.REVOKED, expiresAt = null, productId = "life", tokenHash = "a".repeat(64))
        val stale = row(EntitlementSource.PLAY_LIFETIME, Verifier.PLAY_LOCAL, grants = setOf(Capability.LOCAL_PRO), expiresAt = null, productId = "life", tokenHash = "a".repeat(64))
        // Offline, or on a Play push, no server answer follows to settle it: the revocation has to hold by itself.
        val merged = EntitlementMerge.reconcilePlay(listOf(revoked), listOf(stale), now)
        assertEquals(listOf(revoked), merged)
        assertFalse(EntitlementState(merged).has(Capability.LOCAL_PRO, now))
    }

    @Test fun a_new_purchase_after_a_revoked_one_counts_at_once() {
        val revoked = row(EntitlementSource.PLAY_LIFETIME, Verifier.SERVER, grants = setOf(Capability.LOCAL_PRO), status = EntitlementStatus.REVOKED, expiresAt = null, productId = "life", tokenHash = "a".repeat(64))
        val bought = row(EntitlementSource.PLAY_LIFETIME, Verifier.PLAY_LOCAL, grants = setOf(Capability.LOCAL_PRO), expiresAt = null, productId = "life", tokenHash = "b".repeat(64))
        val merged = EntitlementMerge.reconcilePlay(listOf(revoked), listOf(bought), now)
        assertEquals(listOf(bought), merged)
        assertTrue(EntitlementState(merged).has(Capability.LOCAL_PRO, now))
    }

    @Test fun a_lifetime_refund_the_server_read_as_cancelled_never_comes_back_from_plays_copy_of_it() {
        // A Play Console refund of a one-time product: Play's server says CANCELLED, which the server stores as EXPIRED
        // (not REVOKED), while Play's copy on the phone still lists the purchase (owner's test, 2026-11-13).
        val ended = row(EntitlementSource.PLAY_LIFETIME, Verifier.SERVER, grants = setOf(Capability.LOCAL_PRO), status = EntitlementStatus.EXPIRED, expiresAt = null, productId = "life", tokenHash = "a".repeat(64))
        val stale = row(EntitlementSource.PLAY_LIFETIME, Verifier.PLAY_LOCAL, grants = setOf(Capability.LOCAL_PRO), expiresAt = null, productId = "life", tokenHash = "a".repeat(64))
        val merged = EntitlementMerge.reconcilePlay(listOf(ended), listOf(stale), now)
        assertEquals(listOf(ended), merged)
        assertFalse(EntitlementState(merged).has(Capability.LOCAL_PRO, now))
    }

    @Test fun an_expired_subscription_stays_off_while_plays_copy_lags_and_a_resubscription_counts_at_once() {
        // Google Play: buying again after a subscription expires is a new purchase with a new token, so the same token
        // never renews once Play has called it expired. Only Play's lagging copy can still list it.
        val expired = row(EntitlementSource.PLAY_SUBSCRIPTION, Verifier.SERVER, status = EntitlementStatus.EXPIRED, expiresAt = now - 10 * day, tokenHash = "c".repeat(64))
        val stale = row(EntitlementSource.PLAY_SUBSCRIPTION, Verifier.PLAY_LOCAL, expiresAt = now + 35 * day, tokenHash = "c".repeat(64))
        assertEquals(listOf(expired), EntitlementMerge.reconcilePlay(listOf(expired), listOf(stale), now))
        val resubscribed = stale.copy(purchaseTokenHash = "d".repeat(64))
        assertEquals(listOf(resubscribed), EntitlementMerge.reconcilePlay(listOf(expired), listOf(resubscribed), now))
    }

    @Test fun a_subscription_back_from_account_hold_shows_up_before_the_server_hears_of_it() {
        // Hold and pause keep the token: the same purchase can be live again, so Play's fresh reading wins.
        for (status in listOf(EntitlementStatus.ON_HOLD, EntitlementStatus.PAUSED)) {
            val held = row(EntitlementSource.PLAY_SUBSCRIPTION, Verifier.SERVER, status = status, expiresAt = now - day, tokenHash = "e".repeat(64))
            val back = row(EntitlementSource.PLAY_SUBSCRIPTION, Verifier.PLAY_LOCAL, expiresAt = now + 35 * day, tokenHash = "e".repeat(64))
            assertEquals(status.name, listOf(back), EntitlementMerge.reconcilePlay(listOf(held), listOf(back), now))
        }
    }

    @Test fun a_server_lifetime_unheard_of_for_a_month_gives_way_to_plays_fresh_reading() {
        // checkedAt defaults to verifiedAt: the phone last heard from the server 40 days ago, and Play has just confirmed it.
        val unheard = row(EntitlementSource.PLAY_LIFETIME, Verifier.SERVER, grants = setOf(Capability.LOCAL_PRO), expiresAt = null, productId = "life", verifiedAt = now - 40 * day)
        val play = row(EntitlementSource.PLAY_LIFETIME, Verifier.PLAY_LOCAL, grants = setOf(Capability.LOCAL_PRO), expiresAt = null, productId = "life")
        val merged = EntitlementMerge.reconcilePlay(listOf(unheard), listOf(play), now)
        assertEquals(listOf(play), merged)
        assertTrue(EntitlementState(merged).has(Capability.LOCAL_PRO, now))
    }

    @Test fun the_server_replaces_its_own_rows_and_the_phones_rows_for_keys_it_knows() {
        val playSub = row(EntitlementSource.PLAY_SUBSCRIPTION, Verifier.PLAY_LOCAL)
        val playLifetimeOffline = row(EntitlementSource.PLAY_LIFETIME, Verifier.PLAY_LOCAL, grants = setOf(Capability.LOCAL_PRO), expiresAt = null, productId = "life")
        val oldGrant = row(EntitlementSource.GRANT, Verifier.SERVER, productId = null, verifiedAt = now - 5 * day)
        val serverSub = row(EntitlementSource.PLAY_SUBSCRIPTION, Verifier.SERVER, expiresAt = now + 12 * day)
        val merged = EntitlementMerge.reconcileServer(listOf(playSub, playLifetimeOffline, oldGrant), listOf(serverSub))
        // The grant the server no longer lists is gone; the subscription is the server's reading; the Lifetime bought
        // offline and not yet verified stays as the phone's reading.
        assertEquals(setOf(serverSub, playLifetimeOffline), merged.toSet())
    }

    @Test fun an_empty_server_answer_removes_server_rows_but_keeps_play_rows() {
        val play = row(EntitlementSource.PLAY_SUBSCRIPTION, Verifier.PLAY_LOCAL)
        val grant = row(EntitlementSource.GRANT, Verifier.SERVER, productId = null)
        assertEquals(listOf(play), EntitlementMerge.reconcileServer(listOf(play, grant), emptyList()))
    }

    @Test fun readings_must_carry_their_verifier() {
        val wrong = row(EntitlementSource.PLAY_SUBSCRIPTION, Verifier.SERVER)
        var threw = false
        try { EntitlementMerge.reconcilePlay(emptyList(), listOf(wrong), now) } catch (e: IllegalArgumentException) { threw = true }
        assertTrue(threw)
        threw = false
        try { EntitlementMerge.reconcileServer(emptyList(), listOf(row(EntitlementSource.GRANT, Verifier.PLAY_LOCAL))) } catch (e: IllegalArgumentException) { threw = true }
        assertTrue(threw)
    }
}
