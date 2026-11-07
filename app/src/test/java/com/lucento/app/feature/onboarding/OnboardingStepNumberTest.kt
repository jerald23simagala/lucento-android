package com.lucento.app.feature.onboarding

import com.lucento.app.domain.Feature
import com.lucento.app.domain.FeatureSet
import com.lucento.app.domain.GuideStep
import com.lucento.app.domain.GuidedSetup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingStepNumberTest {
    @Test fun `usage step numbers run from welcome 1 to confirmation 8`() {
        assertEquals(1, onboardingStepNumber(OnboardingViewModel.STEP_WELCOME))
        assertEquals(8, onboardingStepNumber(OnboardingViewModel.STEP_CONFIRM))
        assertEquals((1..8).toList(), (0..OnboardingViewModel.LAST_STEP).map(::onboardingStepNumber).sorted())
    }

    @Test fun `numbers name screens, so moving keep your money safe to second place keeps old counts comparable`() {
        assertEquals(1, OnboardingViewModel.STEP_SYNC)
        assertEquals(7, onboardingStepNumber(OnboardingViewModel.STEP_SYNC))
        assertEquals(2, onboardingStepNumber(OnboardingViewModel.STEP_FOCUS))
        assertEquals(3, onboardingStepNumber(OnboardingViewModel.STEP_SPEND))
        assertEquals(4, onboardingStepNumber(OnboardingViewModel.STEP_CATEGORIES))
        assertEquals(5, onboardingStepNumber(OnboardingViewModel.STEP_REVIEW))
        assertEquals(6, onboardingStepNumber(OnboardingViewModel.STEP_ESSENTIALS))
    }

    @Test fun `welcome step 1 is queued once counts are on`() {
        val off = com.lucento.app.domain.UsageLedger(optedIn = false, installId = null, installedAtMillis = 0L)
        assertTrue(com.lucento.app.domain.UsageLedgerLogic.stepViewed(off, 1, now = 10L).queue.isEmpty())
        val on = off.copy(optedIn = true, installId = "id")
        assertEquals("1", com.lucento.app.domain.UsageLedgerLogic.stepViewed(on, 1, now = 10L).queue.single().props["step"])
    }

    @Test fun `out of range saved steps are clamped`() {
        assertEquals(1, onboardingStepNumber(-3))
        assertEquals(8, onboardingStepNumber(42))
    }

    @Test fun `the guided setup budget gate is back and needs two amounts`() {
        val all = FeatureSet(Feature.entries.toSet())
        assertTrue(GuideStep.BUDGETS in GuidedSetup.steps(all))
        assertFalse(GuidedSetup.canContinue(GuideStep.BUDGETS, assetWallets = 1, budgetedCategories = 1, expenseCategories = 5))
        assertTrue(GuidedSetup.canContinue(GuideStep.BUDGETS, assetWallets = 1, budgetedCategories = 2, expenseCategories = 5))
    }
}
