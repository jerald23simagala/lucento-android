package com.lucento.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class WellnessTierTest {

    @Test
    fun `on track at or below pace is thriving, ahead of pace is steady`() {
        assertEquals(WellnessTier.THRIVING, WellnessTier.of(BudgetHealth.ON_TRACK, aheadOfPace = false, overspent = false))
        assertEquals(WellnessTier.STEADY, WellnessTier.of(BudgetHealth.ON_TRACK, aheadOfPace = true, overspent = false))
    }

    @Test
    fun `no budget is neutral steady`() {
        assertEquals(WellnessTier.STEADY, WellnessTier.of(BudgetHealth.NOT_SET, aheadOfPace = false, overspent = false))
    }

    @Test
    fun `approaching and low map to getting tight and concerned`() {
        assertEquals(WellnessTier.GETTING_TIGHT, WellnessTier.of(BudgetHealth.APPROACHING, aheadOfPace = true, overspent = false))
        assertEquals(WellnessTier.CONCERNED, WellnessTier.of(BudgetHealth.LOW, aheadOfPace = true, overspent = false))
    }

    @Test
    fun `exactly used is concerned, overspent is over plan`() {
        assertEquals(WellnessTier.CONCERNED, WellnessTier.of(BudgetHealth.EXCEEDED, aheadOfPace = true, overspent = false))
        assertEquals(WellnessTier.OVER_PLAN, WellnessTier.of(BudgetHealth.EXCEEDED, aheadOfPace = true, overspent = true))
    }

    @Test
    fun `tiers only get darker as health worsens`() {
        val order = listOf(BudgetHealth.ON_TRACK, BudgetHealth.APPROACHING, BudgetHealth.LOW, BudgetHealth.EXCEEDED)
        val tiers = order.map { WellnessTier.of(it, aheadOfPace = false, overspent = it == BudgetHealth.EXCEEDED) }
        assertEquals(tiers.sortedBy { it.ordinal }, tiers)
    }
}
