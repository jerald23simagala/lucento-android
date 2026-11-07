package com.lucento.app.domain.entitlement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementEvaluatorTest {
    private val day = 24 * 60 * 60 * 1000L
    private val now = 1_800_000_000_000L

    private fun row(
        source: EntitlementSource,
        grants: Set<Capability>,
        status: EntitlementStatus = EntitlementStatus.ACTIVE,
        expiresAt: Long? = now + 30 * day,
        verifiedBy: Verifier = Verifier.SERVER,
    ) = Entitlement(
        source = source, status = status, grants = grants, startsAtMillis = now - day, expiresAtMillis = expiresAt,
        productId = null, verifiedAtMillis = now - 1000, verifiedBy = verifiedBy,
    )

    private val subscription = EntitlementState(listOf(row(EntitlementSource.PLAY_SUBSCRIPTION, Capability.entries.toSet())))
    private val lifetimeOnly = EntitlementState(listOf(row(EntitlementSource.PLAY_LIFETIME, setOf(Capability.LOCAL_PRO), expiresAt = null)))

    // ---- Free

    @Test fun free_user_within_counts_is_allowed() {
        assertEquals(Access.Allowed, EntitlementEvaluator.access(EntitlementState.FREE, ProFeature.MONEY_WALLETS, used = 2, now = now))
        assertEquals(Access.Allowed, EntitlementEvaluator.access(EntitlementState.FREE, ProFeature.DEBT_ACCOUNTS, used = 1, now = now))
        assertEquals(Access.Allowed, EntitlementEvaluator.access(EntitlementState.FREE, ProFeature.SAVINGS_GOALS, used = 1, now = now))
        assertEquals(Access.Allowed, EntitlementEvaluator.access(EntitlementState.FREE, ProFeature.AI_ADVISER, used = 9, now = now))
    }

    @Test fun free_user_at_a_count_gets_limit_reached_with_the_upgrade_limit() {
        val wallets = EntitlementEvaluator.access(EntitlementState.FREE, ProFeature.MONEY_WALLETS, used = 3, now = now)
        assertEquals(Access.LimitReached(ProFeature.MONEY_WALLETS, 3, Limit.Count(3), Limit.Unlimited), wallets)
        assertTrue((wallets as Access.LimitReached).upgradeHelps)

        val goals = EntitlementEvaluator.access(EntitlementState.FREE, ProFeature.SAVINGS_GOALS, used = 2, now = now)
        assertEquals(Access.LimitReached(ProFeature.SAVINGS_GOALS, 2, Limit.Count(2), Limit.Count(20)), goals)

        // Over the limit (data from before gating, or a downgrade): still LimitReached, never an error, never a deletion.
        val many = EntitlementEvaluator.access(EntitlementState.FREE, ProFeature.MONEY_WALLETS, used = 9, now = now)
        assertEquals(9, (many as Access.LimitReached).used)
    }

    @Test fun free_user_on_a_pro_only_feature_is_locked_with_the_capability_named() {
        assertEquals(
            Access.Locked(ProFeature.CLOUD_SYNC, Capability.CLOUD),
            EntitlementEvaluator.access(EntitlementState.FREE, ProFeature.CLOUD_SYNC, used = 0, now = now),
        )
        assertEquals(
            Access.Locked(ProFeature.RECEIVABLES, Capability.LOCAL_PRO),
            EntitlementEvaluator.access(EntitlementState.FREE, ProFeature.RECEIVABLES, used = 0, now = now),
        )
        assertEquals(
            Access.Locked(ProFeature.SHARED_SPACE, Capability.SHARED),
            EntitlementEvaluator.access(EntitlementState.FREE, ProFeature.SHARED_SPACE, used = 0, now = now),
        )
    }

    // ---- Pro subscription

    @Test fun subscriber_has_everything_but_the_goal_cap_still_applies() {
        for (f in ProFeature.entries) {
            if (f == ProFeature.SAVINGS_GOALS || f == ProFeature.SHARED_SPACE || f == ProFeature.AI_ADVISER) continue
            assertEquals("$f", Access.Allowed, EntitlementEvaluator.access(subscription, f, used = 1_000, now = now))
        }
        val goals = EntitlementEvaluator.access(subscription, ProFeature.SAVINGS_GOALS, used = 20, now = now)
        assertEquals(Access.LimitReached(ProFeature.SAVINGS_GOALS, 20, Limit.Count(20), Limit.Count(20)), goals)
        assertFalse("no plan lifts 20 goals", (goals as Access.LimitReached).upgradeHelps)
        assertEquals(Access.Allowed, EntitlementEvaluator.access(subscription, ProFeature.SHARED_SPACE, used = 0, now = now))
        assertTrue(EntitlementEvaluator.access(subscription, ProFeature.SHARED_SPACE, used = 1, now = now) is Access.LimitReached)
    }

    @Test fun statuses_decide_access() {
        fun state(status: EntitlementStatus) =
            EntitlementState(listOf(row(EntitlementSource.PLAY_SUBSCRIPTION, Capability.entries.toSet(), status = status)))
        for (s in listOf(EntitlementStatus.ACTIVE, EntitlementStatus.TRIAL, EntitlementStatus.GRACE, EntitlementStatus.CANCELLED_ACTIVE)) {
            assertTrue("$s grants", state(s).has(Capability.CLOUD, now))
        }
        for (s in listOf(EntitlementStatus.ON_HOLD, EntitlementStatus.PAUSED, EntitlementStatus.PENDING, EntitlementStatus.EXPIRED, EntitlementStatus.REVOKED)) {
            assertFalse("$s grants nothing", state(s).has(Capability.CLOUD, now))
        }
    }

    @Test fun an_expired_subscription_is_honoured_for_the_offline_grace_then_free() {
        val expiresAt = now - day
        val state = EntitlementState(listOf(row(EntitlementSource.PLAY_SUBSCRIPTION, Capability.entries.toSet(), expiresAt = expiresAt)))
        assertTrue("one day past expiry", state.has(Capability.LOCAL_PRO, now))
        assertTrue("just inside the grace", state.has(Capability.LOCAL_PRO, expiresAt + Entitlement.OFFLINE_GRACE_MILLIS - 1))
        assertFalse("grace over", state.has(Capability.LOCAL_PRO, expiresAt + Entitlement.OFFLINE_GRACE_MILLIS))
        assertEquals(DisplayPlan.FREE, state.displayPlan(expiresAt + Entitlement.OFFLINE_GRACE_MILLIS))
    }

    // ---- Lifetime

    @Test fun lifetime_owns_local_pro_forever_and_no_services() {
        val farFuture = now + 50L * 365 * day
        assertTrue(lifetimeOnly.has(Capability.LOCAL_PRO, farFuture))
        assertFalse(lifetimeOnly.has(Capability.CLOUD, now))
        assertFalse(lifetimeOnly.has(Capability.AI_PRO, now))
        assertFalse(lifetimeOnly.has(Capability.SHARED, now))
        assertEquals(Access.Allowed, EntitlementEvaluator.access(lifetimeOnly, ProFeature.FOREIGN_CURRENCY_WALLETS, 0, farFuture))
        assertEquals(Access.Allowed, EntitlementEvaluator.access(lifetimeOnly, ProFeature.MONEY_WALLETS, 40, farFuture))
        assertEquals(Access.Locked(ProFeature.CLOUD_SYNC, Capability.CLOUD), EntitlementEvaluator.access(lifetimeOnly, ProFeature.CLOUD_SYNC, 0, now))
        assertEquals(Limit.PerMonth(10), PlanCatalog.limit(ProFeature.AI_ADVISER, lifetimeOnly, now))
        assertEquals(DisplayPlan.LIFETIME, lifetimeOnly.displayPlan(farFuture))
    }

    @Test fun lifetime_with_its_included_year_or_the_cloud_addon_shows_as_lifetime_with_services() {
        val services = row(EntitlementSource.PLAY_LIFETIME_SERVICES, setOf(Capability.CLOUD, Capability.AI_PRO, Capability.SHARED), expiresAt = now + 300 * day)
        val state = EntitlementState(lifetimeOnly.entitlements + services)
        assertEquals(DisplayPlan.LIFETIME_WITH_SERVICES, state.displayPlan(now))
        assertEquals(Limit.PerDay(50), PlanCatalog.limit(ProFeature.AI_ADVISER, state, now))
        // The year ends: back to Lifetime, LOCAL_PRO intact.
        val later = now + 400 * day
        assertEquals(DisplayPlan.LIFETIME, state.displayPlan(later))
        assertTrue(state.has(Capability.LOCAL_PRO, later))
        assertFalse(state.has(Capability.CLOUD, later))

        val addon = row(EntitlementSource.PLAY_CLOUD_ADDON, setOf(Capability.CLOUD, Capability.AI_PRO, Capability.SHARED), expiresAt = later + 30 * day)
        assertEquals(DisplayPlan.LIFETIME_WITH_SERVICES, EntitlementState(state.entitlements + addon).displayPlan(later))
    }

    @Test fun a_revoked_lifetime_is_the_only_way_to_lose_it() {
        val revoked = EntitlementState(listOf(row(EntitlementSource.PLAY_LIFETIME, setOf(Capability.LOCAL_PRO), status = EntitlementStatus.REVOKED, expiresAt = null)))
        assertFalse(revoked.has(Capability.LOCAL_PRO, now))
        assertEquals(DisplayPlan.FREE, revoked.displayPlan(now))
    }

    // ---- several sources

    @Test fun capabilities_union_across_sources_and_a_pending_purchase_grants_nothing() {
        val grant = row(EntitlementSource.GRANT, setOf(Capability.LOCAL_PRO), expiresAt = now + 10 * day)
        val pending = row(EntitlementSource.PLAY_SUBSCRIPTION, Capability.entries.toSet(), status = EntitlementStatus.PENDING)
        val state = EntitlementState(listOf(grant, pending))
        assertEquals(setOf(Capability.LOCAL_PRO), state.capabilities(now))
        assertEquals(DisplayPlan.PRO, state.displayPlan(now))
        assertEquals(pending, state.pending())
        assertEquals(listOf(grant), state.active(now))
    }
}
