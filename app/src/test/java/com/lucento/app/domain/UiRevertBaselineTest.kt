package com.lucento.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The UI is back to 988c79c: Debts off by default, and the six-page guided setup with its gates. */
class UiRevertBaselineTest {
    @Test fun debts_are_off_for_a_new_install() {
        assertFalse(Feature.DEBTS_LOANS in FinancialFocus.initialFeatures(emptySet()))
        assertTrue(Feature.DEBTS_LOANS in FinancialFocus.initialFeatures(setOf(FinancialFocus.DEBTS)))
    }

    @Test fun guided_setup_has_six_pages_when_everything_is_on() {
        val all = FeatureSet(Feature.entries.toSet())
        assertEquals(GuideStep.entries.toList(), GuidedSetup.steps(all))
        assertEquals(6, GuidedSetup.steps(all).size)
    }

    @Test fun accounts_need_a_wallet_and_budgets_need_two_amounts() {
        assertFalse(GuidedSetup.canContinue(GuideStep.WALLETS, assetWallets = 0, budgetedCategories = 0, expenseCategories = 5))
        assertTrue(GuidedSetup.canContinue(GuideStep.WALLETS, assetWallets = 1, budgetedCategories = 0, expenseCategories = 5))
        assertFalse(GuidedSetup.canContinue(GuideStep.BUDGETS, assetWallets = 1, budgetedCategories = 1, expenseCategories = 5))
        assertTrue(GuidedSetup.canContinue(GuideStep.BUDGETS, assetWallets = 1, budgetedCategories = 2, expenseCategories = 5))
    }
}
