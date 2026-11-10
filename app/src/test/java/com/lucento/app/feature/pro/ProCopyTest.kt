package com.lucento.app.feature.pro

import com.lucento.app.data.billing.ProductPrice
import com.lucento.app.data.entitlement.RefreshOutcome
import com.lucento.app.domain.entitlement.Capability
import com.lucento.app.domain.entitlement.DisplayPlan
import com.lucento.app.domain.entitlement.Entitlement
import com.lucento.app.domain.entitlement.EntitlementSource
import com.lucento.app.domain.entitlement.EntitlementState
import com.lucento.app.domain.entitlement.EntitlementStatus
import com.lucento.app.domain.entitlement.Limit
import com.lucento.app.domain.entitlement.PlanCatalog
import com.lucento.app.domain.entitlement.PlanNotice
import com.lucento.app.domain.entitlement.ProFeature
import com.lucento.app.domain.entitlement.Verifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.util.Locale

/** The customer-facing words for plans (docs/monetization/CUSTOMER_COPY.md): every feature, plan state and message. */
class ProCopyTest {
    private val day = 24 * 60 * 60 * 1000L
    private val now = 1_800_000_000_000L // 2027-01-15 08:00 UTC
    private fun status(state: EntitlementState) = ProCopy.planStatus(state, now, ZoneOffset.UTC, Locale.US)
    private fun row(
        source: EntitlementSource,
        grants: Set<Capability>,
        status: EntitlementStatus = EntitlementStatus.ACTIVE,
        expiresAt: Long? = now + 30 * day,
        verifiedBy: Verifier = Verifier.SERVER,
    ) = Entitlement(source, status, grants, now - day, expiresAt, productId = null, verifiedAtMillis = now, verifiedBy = verifiedBy)

    @Test fun every_feature_has_a_distinct_name_and_a_locked_line_that_says_what_stays_free_or_the_numbers() {
        val names = ProFeature.entries.map(ProCopy::featureName)
        assertEquals(names.size, names.toSet().size)
        for (f in ProFeature.entries) {
            val line = ProCopy.lockedLine(f)
            assertTrue("$f", line.isNotBlank() && line.endsWith("."))
            assertFalse("$f never says 'premium' or 'advanced'", line.contains("premium", ignoreCase = true) || line.contains("advanced", ignoreCase = true))
        }
        assertTrue(ProCopy.lockedLine(ProFeature.MONEY_WALLETS).contains("${PlanCatalog.FREE_MONEY_WALLETS} money wallets"))
        assertTrue(ProCopy.lockedLine(ProFeature.SAVINGS_GOALS).contains("${PlanCatalog.PRO_ACTIVE_GOALS}"))
        assertTrue(ProCopy.lockedLine(ProFeature.CLOUD_SYNC).contains("backup file is always free"))
    }

    @Test fun cells_read_as_words() {
        assertEquals("3", ProCopy.cell(ProFeature.MONEY_WALLETS, Limit.Count(3)))
        assertEquals("No limit", ProCopy.cell(ProFeature.MONEY_WALLETS, Limit.Unlimited))
        assertEquals("Included", ProCopy.cell(ProFeature.RECEIVABLES, Limit.Unlimited))
        assertEquals("Not included", ProCopy.cell(ProFeature.CLOUD_SYNC, Limit.None))
        assertEquals("10 a month", ProCopy.cell(ProFeature.AI_ADVISER, Limit.PerMonth(10)))
        assertEquals("50 a day", ProCopy.cell(ProFeature.AI_ADVISER, Limit.PerDay(50)))
        assertEquals("Last 3 months", ProCopy.cell(ProFeature.INSIGHTS_LONG_PERIODS, Limit.None))
        assertEquals("Sunrise and Midnight", ProCopy.cell(ProFeature.EXTRA_THEMES, Limit.None))
        assertEquals("1 Partner", ProCopy.cell(ProFeature.SHARED_SPACE, Limit.Count(1)))
    }

    @Test fun lifetime_owns_local_features_and_never_promises_services_forever() {
        val rows = PlanCatalog.comparisonRows().associateBy { it.feature }
        assertEquals("No limit", ProCopy.lifetimeCell(rows.getValue(ProFeature.MONEY_WALLETS)))
        assertEquals("20", ProCopy.lifetimeCell(rows.getValue(ProFeature.SAVINGS_GOALS)))
        assertEquals("First year, then with Lucento Cloud", ProCopy.lifetimeCell(rows.getValue(ProFeature.CLOUD_SYNC)))
        assertEquals("First year, then with Lucento Cloud", ProCopy.lifetimeCell(rows.getValue(ProFeature.SHARED_SPACE)))
        assertEquals("50 a day the first year, then 10 a month", ProCopy.lifetimeCell(rows.getValue(ProFeature.AI_ADVISER)))
        for (row in rows.values) {
            if (row.feature.requires != Capability.LOCAL_PRO) assertFalse("${row.feature}", ProCopy.lifetimeCell(row).contains("forever"))
        }
    }

    @Test fun limit_lines_give_the_numbers_and_the_way_out_and_know_when_no_plan_helps() {
        val wallets = ProCopy.limitReachedLine(ProFeature.MONEY_WALLETS, used = 5, limit = Limit.Count(3))
        assertTrue(wallets.contains("allows 3 money wallets; you have 5"))
        assertTrue(wallets.contains("keeps working"))
        assertTrue(ProCopy.upgradeHelps(ProFeature.SAVINGS_GOALS, Limit.Count(2)))
        assertFalse(ProCopy.upgradeHelps(ProFeature.SAVINGS_GOALS, Limit.Count(20)))
        assertTrue(ProCopy.limitReachedLine(ProFeature.SAVINGS_GOALS, 2, Limit.Count(2)).contains("archive a goal you’ve reached"))
        assertEquals(
            "20 active goals is the most on any plan. Archive a goal you’ve reached to make room.",
            ProCopy.limitReachedLine(ProFeature.SAVINGS_GOALS, 20, Limit.Count(20)),
        )
        assertEquals(ProCopy.lockedLine(ProFeature.RECEIVABLES), ProCopy.limitReachedLine(ProFeature.RECEIVABLES, 0, Limit.None))
    }

    @Test fun plan_status_for_each_plan() {
        assertEquals("Free plan", status(EntitlementState.FREE).title)
        val pending = EntitlementState(listOf(row(EntitlementSource.PLAY_SUBSCRIPTION, Capability.entries.toSet(), EntitlementStatus.PENDING, verifiedBy = Verifier.PLAY_LOCAL)))
        assertEquals("Waiting for Google Play", status(pending).title)

        val serverSub = EntitlementState(listOf(row(EntitlementSource.PLAY_SUBSCRIPTION, Capability.entries.toSet())))
        assertEquals("Lucento Pro · renews Feb 14, 2027", status(serverSub).title)
        val localSub = EntitlementState(listOf(row(EntitlementSource.PLAY_SUBSCRIPTION, Capability.entries.toSet(), verifiedBy = Verifier.PLAY_LOCAL)))
        assertEquals("a Play-local horizon is never shown as a date", "Lucento Pro", status(localSub).title)
        val cancelled = EntitlementState(listOf(row(EntitlementSource.PLAY_SUBSCRIPTION, Capability.entries.toSet(), EntitlementStatus.CANCELLED_ACTIVE)))
        assertEquals("Lucento Pro · ends Feb 14, 2027", status(cancelled).title)
        val grace = EntitlementState(listOf(row(EntitlementSource.PLAY_SUBSCRIPTION, Capability.entries.toSet(), EntitlementStatus.GRACE)))
        assertEquals("Lucento Pro · payment problem", status(grace).title)
        val grant = EntitlementState(listOf(row(EntitlementSource.GRANT, Capability.entries.toSet())))
        assertEquals("Courtesy access until Feb 14, 2027.", status(grant).detail)

        val lifetime = row(EntitlementSource.PLAY_LIFETIME, setOf(Capability.LOCAL_PRO), expiresAt = null)
        assertEquals("Pro Lifetime · you own it", status(EntitlementState(listOf(lifetime))).title)
        val year = row(EntitlementSource.PLAY_LIFETIME_SERVICES, setOf(Capability.CLOUD, Capability.AI_PRO, Capability.SHARED), expiresAt = now + 100 * day, verifiedBy = Verifier.PLAY_LOCAL)
        assertTrue(status(EntitlementState(listOf(lifetime, year))).detail.contains("included until Apr 25, 2027"))
        val cloud = row(EntitlementSource.PLAY_CLOUD_ADDON, setOf(Capability.CLOUD, Capability.AI_PRO, Capability.SHARED))
        assertTrue(status(EntitlementState(listOf(lifetime, cloud))).detail.startsWith("With Lucento Cloud"))
    }

    @Test fun price_lines_use_plays_own_strings_and_periods() {
        assertEquals("₱149.00 a month", ProCopy.priceLine(ProductPrice("lucento_pro", "monthly", "t", "₱149.00", "P1M")))
        assertEquals("₱1,190.00 a year · 7-day free trial", ProCopy.priceLine(ProductPrice("lucento_pro", "annual", "t", "₱1,190.00", "P1Y", "P7D")))
        assertEquals("₱3,490.00 once", ProCopy.priceLine(ProductPrice("lucento_pro_lifetime", null, null, "₱3,490.00", null)))
        assertEquals("$9.99 every 3 months", ProCopy.priceLine(ProductPrice("x", "q", null, "$9.99", "P3M")))
        assertEquals("1-week trial reads as days", "$1.00 a month · 7-day free trial", ProCopy.priceLine(ProductPrice("x", "m", null, "$1.00", "P1M", "P1W")))
    }

    @Test fun restore_messages() {
        val reached = RefreshOutcome(playReached = true, serverReached = true)
        assertEquals("Welcome back: your Pro Lifetime is restored.", ProCopy.restoreMessage(DisplayPlan.FREE, DisplayPlan.LIFETIME, reached))
        assertEquals("Your plan is up to date: Lucento Pro.", ProCopy.restoreMessage(DisplayPlan.PRO, DisplayPlan.PRO, reached))
        assertTrue(ProCopy.restoreMessage(DisplayPlan.FREE, DisplayPlan.FREE, RefreshOutcome(false, false)).startsWith("Restoring needs an internet connection"))
        assertTrue(ProCopy.restoreMessage(DisplayPlan.FREE, DisplayPlan.FREE, reached).startsWith("No purchase found"))
    }

    @Test fun plan_ended_notices_say_nothing_was_removed_and_quote_the_catalog() {
        for (n in PlanNotice.entries) {
            val copy = ProCopy.notice(n)
            assertTrue("$n", copy.title.isNotBlank())
            assertTrue("$n", copy.body.contains("nothing", ignoreCase = true))
        }
        val ended = ProCopy.notice(PlanNotice.PRO_ENDED).body
        assertTrue(ended.contains("${PlanCatalog.FREE_MONEY_WALLETS} money wallets"))
        assertTrue(ended.contains("${PlanCatalog.FREE_AI_QUESTIONS_PER_MONTH} questions a month"))
        assertTrue(ProCopy.notice(PlanNotice.SERVICES_ENDED).body.contains("unchanged"))
    }

    @Test fun the_member_mark_shows_only_for_the_persons_own_pro_or_lifetime() {
        assertEquals(null, ProCopy.memberMark(EntitlementState.FREE, now))
        val pro = EntitlementState(listOf(row(EntitlementSource.PLAY_SUBSCRIPTION, setOf(Capability.LOCAL_PRO, Capability.CLOUD, Capability.AI_PRO, Capability.SHARED))))
        assertEquals("Pro", ProCopy.memberMark(pro, now))
        val lifetime = EntitlementState(listOf(row(EntitlementSource.PLAY_LIFETIME, setOf(Capability.LOCAL_PRO), expiresAt = null)))
        assertEquals("Lifetime", ProCopy.memberMark(lifetime, now))
        // The owner's 30-day test grant reads like Pro; after it ends (plus the 3-day offline grace every expiring plan
        // gets) the mark goes.
        val grant = EntitlementState(listOf(row(EntitlementSource.GRANT, setOf(Capability.LOCAL_PRO, Capability.CLOUD))))
        assertEquals("Pro", ProCopy.memberMark(grant, now))
        assertEquals("Pro", ProCopy.memberMark(grant, now + 31 * day))
        assertEquals(null, ProCopy.memberMark(grant, now + 34 * day))
    }

    @Test fun the_profile_menu_offers_pro_only_on_the_free_plan_with_nothing_waiting() {
        assertTrue(ProCopy.offersPro(EntitlementState.FREE, now))
        val pro = EntitlementState(listOf(row(EntitlementSource.PLAY_SUBSCRIPTION, setOf(Capability.LOCAL_PRO))))
        assertFalse(ProCopy.offersPro(pro, now))
        val pending = EntitlementState(listOf(row(EntitlementSource.PLAY_SUBSCRIPTION, setOf(Capability.LOCAL_PRO), status = EntitlementStatus.PENDING)))
        assertFalse(ProCopy.offersPro(pending, now))
    }

    @Test fun the_pro_card_lists_only_what_the_pro_plan_adds() {
        // Each line names something Free doesn't fully have (PlanCatalog): cloud, wallet/goal counts, foreign wallets,
        // long Insights, PDF, the AI allowance.
        fun free(f: ProFeature) = PlanCatalog.limit(f, PlanCatalog.FREE_CAPABILITIES)
        fun pro(f: ProFeature) = PlanCatalog.limit(f, PlanCatalog.PRO_CAPABILITIES)
        for (f in listOf(ProFeature.CLOUD_SYNC, ProFeature.CLOUD_RESTORE, ProFeature.FOREIGN_CURRENCY_WALLETS, ProFeature.INSIGHTS_LONG_PERIODS, ProFeature.PDF_STATEMENTS)) {
            assertEquals(Limit.None, free(f))
            assertEquals(Limit.Unlimited, pro(f))
        }
        assertEquals(Limit.Unlimited, pro(ProFeature.MONEY_WALLETS))
        assertTrue((pro(ProFeature.SAVINGS_GOALS) as Limit.Count).max > (free(ProFeature.SAVINGS_GOALS) as Limit.Count).max)
        assertTrue(free(ProFeature.AI_ADVISER) is Limit.PerMonth && pro(ProFeature.AI_ADVISER) is Limit.PerDay)
        assertEquals(4, ProCopy.inviteLines.size)
    }
}
