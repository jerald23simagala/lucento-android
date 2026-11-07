package com.lucento.app.domain.entitlement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins every approved limit (docs/MONETIZATION.md §3, §7): changing one here is a conscious product change. */
class PlanCatalogTest {
    private val free = PlanCatalog.FREE_CAPABILITIES
    private val pro = PlanCatalog.PRO_CAPABILITIES
    private val lifetime = PlanCatalog.LIFETIME_CAPABILITIES

    @Test fun free_limits_are_the_approved_ones() {
        assertEquals(Limit.Count(3), PlanCatalog.limit(ProFeature.MONEY_WALLETS, free))
        assertEquals(Limit.Count(2), PlanCatalog.limit(ProFeature.DEBT_ACCOUNTS, free))
        assertEquals(Limit.Count(2), PlanCatalog.limit(ProFeature.SAVINGS_GOALS, free))
        assertEquals(Limit.PerMonth(10), PlanCatalog.limit(ProFeature.AI_ADVISER, free))
        for (f in listOf(
            ProFeature.RECEIVABLES, ProFeature.VALUED_ASSETS, ProFeature.FOREIGN_CURRENCY_WALLETS, ProFeature.INSIGHTS_LONG_PERIODS,
            ProFeature.NET_WORTH_COMPOSITION, ProFeature.PDF_STATEMENTS, ProFeature.EXTRA_THEMES, ProFeature.CLOUD_SYNC,
            ProFeature.CLOUD_RESTORE, ProFeature.SMART_READING, ProFeature.SHARED_SPACE,
        )) {
            assertEquals("$f is not on Free", Limit.None, PlanCatalog.limit(f, free))
        }
    }

    @Test fun pro_lifts_every_limit_except_the_goal_count_and_one_partner() {
        for (f in ProFeature.entries) {
            val limit = PlanCatalog.limit(f, pro)
            when (f) {
                ProFeature.SAVINGS_GOALS -> assertEquals(Limit.Count(20), limit)
                ProFeature.AI_ADVISER -> assertEquals(Limit.PerDay(50), limit)
                ProFeature.SHARED_SPACE -> assertEquals(Limit.Count(1), limit)
                else -> assertEquals("$f on Pro", Limit.Unlimited, limit)
            }
        }
    }

    @Test fun lifetime_without_services_owns_the_local_group_and_nothing_online() {
        for (f in ProFeature.entries) {
            val limit = PlanCatalog.limit(f, lifetime)
            when (f.requires) {
                Capability.LOCAL_PRO -> assertEquals("$f is owned", PlanCatalog.limit(f, pro), limit)
                else -> assertEquals("$f is a service", PlanCatalog.limit(f, free), limit)
            }
        }
    }

    @Test fun lifetime_with_services_equals_pro() {
        for (f in ProFeature.entries) {
            assertEquals(PlanCatalog.limit(f, pro), PlanCatalog.limit(f, PlanCatalog.LIFETIME_WITH_SERVICES_CAPABILITIES))
        }
    }

    @Test fun comparison_rows_cover_every_feature_once_and_agree_with_the_gates() {
        val rows = PlanCatalog.comparisonRows()
        assertEquals(ProFeature.entries.size, rows.size)
        assertEquals(ProFeature.entries.toSet(), rows.map { it.feature }.toSet())
        for (row in rows) {
            assertEquals(PlanCatalog.limit(row.feature, free), row.free)
            assertEquals(PlanCatalog.limit(row.feature, pro), row.pro)
            assertEquals(PlanCatalog.limit(row.feature, lifetime), row.lifetime)
        }
    }

    @Test fun limits_count_what_is_in_use() {
        assertTrue(Limit.Count(3).allows(2))
        assertTrue(!Limit.Count(3).allows(3))
        assertTrue(!Limit.Count(3).allows(7))
        assertTrue(Limit.Unlimited.allows(1_000_000))
        assertTrue(!Limit.None.allows(0))
        assertTrue(Limit.PerMonth(10).allows(9))
        assertTrue(!Limit.PerDay(50).allows(50))
        assertEquals(3, Limit.Count(3).maxOrNull)
        assertEquals(null, Limit.Unlimited.maxOrNull)
        assertEquals(null, Limit.None.maxOrNull)
    }

    @Test fun server_mirrored_numbers_are_the_approved_ones() {
        assertEquals(400, PlanCatalog.PRO_AI_MONTHLY_CEILING)
        assertEquals(1, PlanCatalog.PRO_PARTNERS)
    }
}
