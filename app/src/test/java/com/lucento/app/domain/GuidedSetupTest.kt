package com.lucento.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidedSetupTest {
    private val everything = FeatureSet.ALL
    private val minimal = FeatureSet(FeatureSet.ALL.enabled - setOf(Feature.DEBTS_LOANS, Feature.SAVINGS_GOALS, Feature.BUDGETS))

    @Test
    fun `the story runs wallets, debts, budgets, goal, transactions, welcome`() {
        assertEquals(GuideStep.entries.toList(), GuidedSetup.steps(everything))
        assertEquals(GuideStep.DEBTS, GuidedSetup.next(GuideStep.WALLETS, everything))
        assertEquals(null, GuidedSetup.next(GuideStep.WELCOME, everything))
    }

    @Test
    fun `switched-off modules leave the story, and a saved step for one resumes at the next`() {
        assertEquals(listOf(GuideStep.WALLETS, GuideStep.TRANSACTIONS, GuideStep.WELCOME), GuidedSetup.steps(minimal))
        assertEquals(GuideStep.TRANSACTIONS, GuidedSetup.next(GuideStep.WALLETS, minimal))
        assertEquals(GuideStep.TRANSACTIONS, GuidedSetup.resume(GuideStep.BUDGETS, minimal))
    }

    @Test
    fun `wallets need one account and budgets two amounts, or every category when there are fewer`() {
        assertFalse(GuidedSetup.canContinue(GuideStep.WALLETS, assetWallets = 0, budgetedCategories = 0, expenseCategories = 5))
        assertTrue(GuidedSetup.canContinue(GuideStep.WALLETS, assetWallets = 1, budgetedCategories = 0, expenseCategories = 5))
        assertFalse(GuidedSetup.canContinue(GuideStep.BUDGETS, assetWallets = 1, budgetedCategories = 1, expenseCategories = 5))
        assertTrue(GuidedSetup.canContinue(GuideStep.BUDGETS, assetWallets = 1, budgetedCategories = 2, expenseCategories = 5))
        assertTrue(GuidedSetup.canContinue(GuideStep.BUDGETS, assetWallets = 1, budgetedCategories = 1, expenseCategories = 1))
        assertTrue(GuidedSetup.canContinue(GuideStep.GOAL, assetWallets = 0, budgetedCategories = 0, expenseCategories = 0))
    }

    @Test
    fun `only debts, the goal and transactions can be skipped`() {
        assertEquals(setOf(GuideStep.DEBTS, GuideStep.GOAL, GuideStep.TRANSACTIONS), GuideStep.entries.filter(GuidedSetup::isSkippable).toSet())
    }
}
