package com.lucento.app.domain.entitlement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lucento Pro's welcome (owner, 2026-11-12): once per new plan, the medal once per person, never for a pending payment. */
class ProWelcomeTest {
    private val now = 1_800_000_000_000L
    private val day = 24L * 60 * 60 * 1000
    private val all = Capability.entries.toSet()

    private fun row(
        source: EntitlementSource = EntitlementSource.PLAY_SUBSCRIPTION,
        status: EntitlementStatus = EntitlementStatus.ACTIVE,
        grants: Set<Capability> = all,
        token: String? = "tok-a",
        expires: Long? = now + 30 * day,
        verifiedBy: Verifier = Verifier.PLAY_LOCAL,
        product: String? = "lucento_pro",
        startsAt: Long = now - day,
    ) = Entitlement(
        source = source,
        status = status,
        grants = grants,
        startsAtMillis = startsAt,
        expiresAtMillis = expires,
        productId = product,
        purchaseTokenHash = token,
        verifiedAtMillis = now,
        verifiedBy = verifiedBy,
    )

    private fun state(vararg rows: Entitlement) = EntitlementState(rows.toList())

    @Test fun nothing_on_the_free_plan() {
        assertNull(ProWelcome.toStart(EntitlementState.FREE, now, welcomed = emptySet(), memberSince = null))
    }

    @Test fun a_first_plan_gets_everything() {
        val run = ProWelcome.toStart(state(row()), now, welcomed = emptySet(), memberSince = null)!!
        assertEquals(ProWelcomeMode.FIRST, run.mode)
        assertEquals(setOf("PLAY_SUBSCRIPTION:tok-a"), run.planKeys)
        assertTrue(run.mode.reveal && run.mode.medal && run.mode.flight)
    }

    @Test fun a_plan_held_for_a_while_is_a_welcome_back_dated_from_its_start() {
        // A purchase made today: news. A plan restored on a new phone (bought months ago): a welcome back, and the award
        // keeps the real "member since".
        val today = ProWelcome.toStart(state(row(startsAt = now - 60_000)), now, welcomed = emptySet(), memberSince = null)!!
        assertEquals(false, today.restored)
        val restored = ProWelcome.toStart(state(row(startsAt = now - 90 * day)), now, welcomed = emptySet(), memberSince = null)!!
        assertEquals(true, restored.restored)
        assertEquals(now - 90 * day, restored.since)
        assertEquals(ProWelcomeMode.FIRST, restored.mode)
    }

    @Test fun a_pending_payment_is_never_welcomed() {
        val pending = row(status = EntitlementStatus.PENDING)
        assertNull(ProWelcome.toStart(state(pending), now, welcomed = emptySet(), memberSince = null))
    }

    @Test fun a_renewal_or_a_trial_turning_paid_keeps_the_purchase_and_stays_quiet() {
        val welcomed = ProWelcome.planKeys(state(row(status = EntitlementStatus.TRIAL)), now)
        val renewed = row(status = EntitlementStatus.ACTIVE, expires = now + 365 * day)
        assertNull(ProWelcome.toStart(state(renewed), now, welcomed = welcomed, memberSince = now - day))
    }

    @Test fun the_server_reading_of_the_same_purchase_is_the_same_plan() {
        val welcomed = ProWelcome.planKeys(state(row(verifiedBy = Verifier.PLAY_LOCAL)), now)
        assertNull(ProWelcome.toStart(state(row(verifiedBy = Verifier.SERVER)), now, welcomed = welcomed, memberSince = now))
    }

    @Test fun a_new_plan_for_someone_with_the_medal_skips_the_ceremony_but_lands_the_ring() {
        val welcomed = setOf("PLAY_SUBSCRIPTION:tok-a")
        val lifetime = row(source = EntitlementSource.PLAY_LIFETIME, token = "tok-b", grants = setOf(Capability.LOCAL_PRO), expires = null, product = "lucento_pro_lifetime")
        val run = ProWelcome.toStart(state(lifetime), now, welcomed = welcomed, memberSince = now - 90 * day)!!
        assertEquals(ProWelcomeMode.RETURNING, run.mode)
        assertEquals(setOf("PLAY_LIFETIME:tok-b"), run.planKeys)
        assertEquals(false, run.mode.medal)
        assertEquals(true, run.mode.flight)
    }

    @Test fun a_tour_closed_before_the_medal_brings_the_medal_alone_next_time() {
        val welcomed = ProWelcome.planKeys(state(row()), now)
        assertEquals(ProWelcomeMode.MEDAL_ONLY, ProWelcome.toStart(state(row()), now, welcomed = welcomed, memberSince = null)!!.mode)
        // But not twice in one run of the app, before the stored date has landed.
        assertNull(ProWelcome.toStart(state(row()), now, welcomed = welcomed, memberSince = null, presentedThisProcess = true))
    }

    @Test fun the_cloud_add_on_alone_is_not_a_welcome_to_pro() {
        val cloud = row(source = EntitlementSource.PLAY_CLOUD_ADDON, grants = setOf(Capability.CLOUD, Capability.AI_PRO, Capability.SHARED), product = "lucento_cloud")
        assertTrue(ProWelcome.planKeys(state(cloud), now).isEmpty())
        assertNull(ProWelcome.toStart(state(cloud), now, welcomed = emptySet(), memberSince = null))
    }

    @Test fun a_grant_has_a_key_from_its_start() {
        val grant = row(source = EntitlementSource.GRANT, token = null, product = null, startsAt = 42L, verifiedBy = Verifier.SERVER)
        assertEquals(setOf("GRANT:@42"), ProWelcome.planKeys(state(grant), now))
    }

    @Test fun an_ended_plan_is_not_welcomed() {
        val ended = row(expires = now - 10 * day)
        assertTrue(ProWelcome.planKeys(state(ended), now).isEmpty())
    }

    private fun context(
        capabilities: Set<Capability> = all,
        wallets: Boolean = true,
        goals: Boolean = true,
        insights: Boolean = true,
        manages: Boolean = true,
    ) = ProTourContext(capabilities, wallets, goals, insights, manages)

    @Test fun the_full_deck_has_ten_cards_in_five_chapters() {
        val stops = ProWelcome.stops(context())
        assertEquals(ProTourStop.entries.toList(), stops)
        assertEquals(ProTourChapter.entries.toList(), stops.map { it.chapter }.distinct())
    }

    @Test fun stops_follow_the_modules_switched_on() {
        val stops = ProWelcome.stops(context(goals = false, insights = false))
        assertEquals(
            listOf(ProTourStop.WALLETS, ProTourStop.NET_WORTH, ProTourStop.SMART_READING, ProTourStop.SYNC, ProTourStop.SHARED, ProTourStop.THEMES),
            stops,
        )
    }

    @Test fun cards_do_not_wait_for_a_wallet() {
        // A card names what Pro opened; unlike the tour it replaced, it needs no control on screen to point at.
        val stops = ProWelcome.stops(context().copy(hasWallets = false))
        assertTrue(ProTourStop.WALLETS in stops)
        assertTrue(ProTourStop.NET_WORTH in stops)
        assertTrue(ProTourStop.SMART_READING in stops)
    }

    @Test fun a_partner_is_not_shown_adding_wallets() {
        val stops = ProWelcome.stops(context(manages = false))
        assertTrue(ProTourStop.WALLETS !in stops && ProTourStop.NET_WORTH !in stops)
        // A Partner's phone is already in a Shared Space: inviting is the Owner's.
        assertTrue(ProTourStop.SHARED !in stops)
        assertTrue(ProTourStop.GOALS in stops)
    }

    @Test fun a_build_without_cloud_sync_has_no_sync_or_ai_stop() {
        val stops = ProWelcome.stops(context().copy(syncAvailable = false))
        assertTrue(ProTourStop.SYNC !in stops)
        // Gemini and Lucento Shared need a Lucento account, which such a build can't have.
        assertTrue(ProTourStop.AI_ANSWERS !in stops && ProTourStop.SMART_READING !in stops && ProTourStop.SHARED !in stops)
        assertEquals(ProTourStop.THEMES, stops.last())
    }

    @Test fun lifetime_after_its_year_has_no_online_stops() {
        val stops = ProWelcome.stops(context(capabilities = setOf(Capability.LOCAL_PRO)))
        assertTrue(ProTourStop.AI_ANSWERS !in stops && ProTourStop.SYNC !in stops)
        assertTrue(ProTourStop.SMART_READING !in stops && ProTourStop.SHARED !in stops)
        assertEquals(ProTourStop.THEMES, stops.last())
    }

    @Test fun online_services_are_claimed_only_once_the_server_has_the_plan() {
        assertEquals(OnlineStatus.CONFIRMED, ProWelcome.onlineStatus(state(row(verifiedBy = Verifier.SERVER)), now, signedIn = true))
        assertEquals(OnlineStatus.CONFIRMING, ProWelcome.onlineStatus(state(row()), now, signedIn = true))
        assertEquals(OnlineStatus.SIGN_IN_NEEDED, ProWelcome.onlineStatus(state(row()), now, signedIn = false))
        val lifetimeOnly = row(source = EntitlementSource.PLAY_LIFETIME, grants = setOf(Capability.LOCAL_PRO), expires = null)
        assertEquals(OnlineStatus.NOT_IN_PLAN, ProWelcome.onlineStatus(state(lifetimeOnly), now, signedIn = true))
        assertEquals(OnlineStatus.NOT_IN_THIS_BUILD, ProWelcome.onlineStatus(state(row()), now, signedIn = false, syncAvailable = false))
    }
}
