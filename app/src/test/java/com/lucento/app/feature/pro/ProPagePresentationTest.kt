package com.lucento.app.feature.pro

import com.lucento.app.data.billing.ProductPrice
import com.lucento.app.domain.entitlement.Capability
import com.lucento.app.domain.entitlement.EntitlementState
import com.lucento.app.domain.entitlement.PlanCatalog
import com.lucento.app.domain.entitlement.PlayProducts
import com.lucento.app.domain.entitlement.ProFeature
import com.lucento.app.feature.pro.ProCopy.TableCell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Pro page's presentation: the comparison table, the plan pitches and the yearly saving (all from Play or PlanCatalog). */
class ProPagePresentationTest {
    private val now = 1_800_000_000_000L

    private fun php(product: String, plan: String?, text: String, period: String?, micros: Long, trial: String? = null, currency: String = "PHP") =
        ProductPrice(product, plan, "t-$product-$plan", text, period, trial, priceMicros = micros, currencyCode = currency)

    private val yearly = php(PlayProducts.PRO_SUBSCRIPTION, "annual", "₱1,190.00", "P1Y", 1_190_000_000, trial = "P7D")
    private val monthly = php(PlayProducts.PRO_SUBSCRIPTION, "monthly", "₱149.00", "P1M", 149_000_000)
    private val lifetime = php(PlayProducts.PRO_LIFETIME, null, "₱3,490.00", null, 3_490_000_000)

    @Test fun the_yearly_saving_is_rounded_down_and_needs_both_amounts_in_one_currency() {
        // 12 × 149 = 1,788; (1,788 − 1,190) / 1,788 = 33.4 %.
        assertEquals(33, ProCopy.yearlySavingPercent(yearly, monthly))
        // Play's rounded ₱1,200: 32.9 % shows as 32, never 33.
        assertEquals(32, ProCopy.yearlySavingPercent(yearly.copy(priceMicros = 1_200_000_000), monthly))
        assertNull("no amounts, no claim", ProCopy.yearlySavingPercent(yearly.copy(priceMicros = null), monthly))
        assertNull("different currencies", ProCopy.yearlySavingPercent(yearly, monthly.copy(currencyCode = "USD")))
        assertNull("no saving", ProCopy.yearlySavingPercent(yearly.copy(priceMicros = 1_788_000_000), monthly))
        assertNull("under 5 % isn't worth a badge", ProCopy.yearlySavingPercent(yearly.copy(priceMicros = 1_750_000_000), monthly))
    }

    @Test fun a_free_person_sees_the_periods_with_trial_and_saving_and_lifetime_once() {
        val cards = ProOffers.cards(EntitlementState.FREE, listOf(yearly, monthly, lifetime), now).associateBy { it.kind }
        val (y, m) = cards.getValue(PlanCardKind.PRO).actions
        assertEquals("Yearly", y.periodName)
        assertEquals("a year", y.per)
        assertEquals("7-day free trial", y.trial)
        assertEquals("Save 33%", y.saving)
        assertEquals("Monthly", m.periodName)
        assertNull("the saving is shown on the yearly period only", m.saving)
        val life = cards.getValue(PlanCardKind.LIFETIME).actions.single()
        assertNull(life.periodName)
        assertEquals("once", life.per)
        assertNull(life.saving)
    }

    @Test fun the_table_uses_short_values_ticks_and_dashes() {
        val rows = PlanCatalog.comparisonRows().associateBy { it.feature }
        fun free(f: ProFeature) = ProCopy.tableCell(f, rows.getValue(f).free)
        fun pro(f: ProFeature) = ProCopy.tableCell(f, rows.getValue(f).pro)
        assertEquals(TableCell.Value("${PlanCatalog.FREE_MONEY_WALLETS}"), free(ProFeature.MONEY_WALLETS))
        assertEquals(TableCell.Value("No limit"), pro(ProFeature.MONEY_WALLETS))
        assertEquals(TableCell.NotIncluded, free(ProFeature.RECEIVABLES))
        assertEquals(TableCell.Included, pro(ProFeature.RECEIVABLES))
        assertEquals(TableCell.Value("${PlanCatalog.FREE_AI_QUESTIONS_PER_MONTH}/mo"), free(ProFeature.AI_ADVISER))
        assertEquals(TableCell.Value("${PlanCatalog.PRO_AI_QUESTIONS_PER_DAY}/day"), pro(ProFeature.AI_ADVISER))
        assertEquals(TableCell.Value("2"), free(ProFeature.EXTRA_THEMES))
        assertEquals(TableCell.Value("All 5"), pro(ProFeature.EXTRA_THEMES))
        assertEquals(TableCell.Value("1 Partner"), pro(ProFeature.SHARED_SPACE))
        // Lifetime owns what runs on the phone; online services are "Year 1", explained once under the table.
        assertEquals(TableCell.Value("No limit"), ProCopy.lifetimeTableCell(rows.getValue(ProFeature.MONEY_WALLETS)))
        rows.values.filter { it.feature.requires != Capability.LOCAL_PRO }.forEach {
            assertEquals("${it.feature}", TableCell.Value(ProCopy.FIRST_YEAR), ProCopy.lifetimeTableCell(it))
        }
        assertTrue(ProCopy.FIRST_YEAR_NOTE.startsWith("${ProCopy.FIRST_YEAR}:"))
        assertFalse(ProCopy.FIRST_YEAR_NOTE.contains("forever"))
    }

    @Test fun every_feature_appears_once_in_two_groups_phone_first() {
        val rows = PlanCatalog.comparisonRows()
        val groups = ProCopy.comparisonGroups(rows)
        assertEquals(listOf("On this phone", "Online"), groups.map { it.first })
        assertEquals(rows.map { it.feature }.toSet(), groups.flatMap { g -> g.second.map { it.feature } }.toSet())
        assertEquals(rows.size, groups.sumOf { it.second.size })
        assertTrue(groups[0].second.all { it.feature.requires == Capability.LOCAL_PRO })
        assertTrue(groups[1].second.none { it.feature.requires == Capability.LOCAL_PRO })
        // TalkBack still hears every plan in words.
        val wallets = rows.first { it.feature == ProFeature.MONEY_WALLETS }
        assertEquals("Money wallets. Free: 3. Pro: No limit. Lifetime: No limit.", ProCopy.comparisonDescription(wallets))
    }

    @Test fun pitches_use_the_catalog_numbers_and_never_promise_online_services_forever() {
        assertTrue(ProCopy.pitch(PlanCardKind.PRO).benefits.contains("${PlanCatalog.PRO_AI_QUESTIONS_PER_DAY} AI adviser questions a day"))
        val lifetime = ProCopy.pitch(PlanCardKind.LIFETIME).benefits
        assertTrue(lifetime.any { it.contains("Cloud sync") && it.contains("first year") })
        PlanCardKind.entries.forEach { kind ->
            val pitch = ProCopy.pitch(kind)
            assertTrue("$kind", pitch.tagline.isNotBlank() && pitch.benefits.isNotEmpty())
            assertFalse("$kind", pitch.benefits.any { it.contains("forever") })
        }
    }
}
