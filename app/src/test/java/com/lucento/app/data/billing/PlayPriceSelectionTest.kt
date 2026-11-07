package com.lucento.app.data.billing

import com.lucento.app.domain.entitlement.PlayProducts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Lucento Pro, Phase 4: which of Play's offers each plan shows (docs/MONETIZATION.md §4.1, §9). */
class PlayPriceSelectionTest {
    private fun phase(micros: Long, text: String, period: String, recurring: Boolean = true) = PricePhase(micros, text, period, recurring)

    private val annualBase = PlayOffer(PlayProducts.PRO_SUBSCRIPTION, "annual", null, "tok-annual", listOf(phase(1_190_000_000, "₱1,190.00", "P1Y")))
    private val annualTrial = PlayOffer(
        PlayProducts.PRO_SUBSCRIPTION, "annual", "trial", "tok-annual-trial",
        listOf(phase(0, "Free", "P7D", recurring = false), phase(1_190_000_000, "₱1,190.00", "P1Y")),
    )
    private val monthly = PlayOffer(PlayProducts.PRO_SUBSCRIPTION, "monthly", null, "tok-monthly", listOf(phase(149_000_000, "₱149.00", "P1M")))

    @Test fun the_trial_offer_wins_when_play_lists_it_and_its_price_is_the_recurring_one() {
        val chosen = PlayPriceSelection.choose(listOf(annualBase, annualTrial, monthly))
        assertEquals(listOf("tok-annual-trial", "tok-monthly"), chosen.map { it.offerToken })
        val annual = chosen.first()
        assertEquals("₱1,190.00", annual.formattedPrice)
        assertEquals("P1Y", annual.billingPeriodIso)
        assertEquals("P7D", annual.freeTrialPeriodIso)
        assertNull(chosen[1].freeTrialPeriodIso)
    }

    @Test fun someone_who_had_the_trial_sees_the_base_plan() {
        // Play stops listing the trial offer once the account has used it.
        val chosen = PlayPriceSelection.choose(listOf(monthly, annualBase))
        assertEquals(listOf("tok-annual", "tok-monthly"), chosen.map { it.offerToken })
        assertNull(chosen.first().freeTrialPeriodIso)
    }

    @Test fun an_offer_with_a_paid_introductory_phase_is_never_shown() {
        val intro = PlayOffer(
            PlayProducts.PRO_SUBSCRIPTION, "monthly", "intro", "tok-intro",
            listOf(phase(49_000_000, "₱49.00", "P1M", recurring = false), phase(149_000_000, "₱149.00", "P1M")),
        )
        assertEquals(listOf("tok-monthly"), PlayPriceSelection.choose(listOf(intro, monthly)).map { it.offerToken })
        assertEquals("only an intro offer: nothing to show", emptyList<ProductPrice>(), PlayPriceSelection.choose(listOf(intro)))
    }

    @Test fun lifetime_shows_its_lowest_option_the_founder_price_while_it_runs() {
        val full = PlayOffer(PlayProducts.PRO_LIFETIME, null, null, "tok-full", oneTimePrice = PricePhase(3_490_000_000, "₱3,490.00", null, false))
        val founder = PlayOffer(PlayProducts.PRO_LIFETIME, null, "founder", "tok-founder", oneTimePrice = PricePhase(2_990_000_000, "₱2,990.00", null, false))
        val chosen = PlayPriceSelection.choose(listOf(full, founder))
        assertEquals(1, chosen.size)
        assertEquals("tok-founder", chosen.single().offerToken)
        assertNull(chosen.single().billingPeriodIso)
    }

    @Test fun plans_come_in_page_order_and_unknown_products_are_ignored() {
        val cloud = PlayOffer(PlayProducts.CLOUD_ADDON, "monthly", null, "tok-cloud", listOf(phase(59_000_000, "₱59.00", "P1M")))
        val lifetime = PlayOffer(PlayProducts.PRO_LIFETIME, null, null, "tok-life", oneTimePrice = PricePhase(3_490_000_000, "₱3,490.00", null, false))
        val stranger = PlayOffer("someone_elses_sku", "monthly", null, "tok-x", listOf(phase(1, "x", "P1M")))
        val chosen = PlayPriceSelection.choose(listOf(cloud, stranger, lifetime, monthly, annualBase))
        assertEquals(listOf("tok-annual", "tok-monthly", "tok-life", "tok-cloud"), chosen.map { it.offerToken })
    }
}
