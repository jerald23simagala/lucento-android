package com.lucento.app.feature.onboarding

import com.lucento.app.domain.Feature
import com.lucento.app.domain.FeatureSet
import com.lucento.app.domain.FinancialFocus
import com.lucento.app.domain.GuideStep
import com.lucento.app.domain.GuidedSetup
import com.lucento.app.domain.SetupStage
import com.lucento.app.domain.SpendType
import com.lucento.app.domain.WalletType
import com.lucento.app.feature.guide.guideStage
import com.lucento.app.feature.guide.guideStageProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Redesign Part B: three visible stages, mandatory focus, and "How do you pay?" in place of feature switches. */
class OnboardingStagesTest {

    @Test fun `after welcome and keep your money safe, every onboarding screen belongs to Personalize or Set up`() {
        assertNull(onboardingStage(OnboardingViewModel.STEP_WELCOME))
        // Owner, 2026-11-06: the account question comes right after Get started, before the stages begin.
        assertNull(onboardingStage(OnboardingViewModel.STEP_SYNC))
        assertEquals(OnboardingViewModel.STEP_WELCOME + 1, OnboardingViewModel.STEP_SYNC)
        assertEquals(OnboardingViewModel.STEP_SYNC + 1, OnboardingViewModel.STEP_FOCUS)
        (OnboardingViewModel.STEP_FOCUS..OnboardingViewModel.STEP_ESSENTIALS).forEach {
            assertEquals(SetupStage.PERSONALIZE, onboardingStage(it))
        }
        assertEquals(SetupStage.SET_UP, onboardingStage(OnboardingViewModel.STEP_CONFIRM))
        assertEquals("the payment step keeps the features step's usage number", 3, onboardingStepNumber(OnboardingViewModel.STEP_SPEND))
    }

    @Test fun `the guided setup continues Set up and ends in Get started, with bars that only move forward`() {
        val steps = GuidedSetup.steps(FeatureSet(Feature.entries.toSet()))
        assertEquals(SetupStage.SET_UP, guideStage(GuideStep.WALLETS))
        assertEquals(SetupStage.SET_UP, guideStage(GuideStep.BUDGETS))
        assertEquals(SetupStage.GET_STARTED, guideStage(GuideStep.TRANSACTIONS))
        assertEquals(SetupStage.GET_STARTED, guideStage(GuideStep.WELCOME))
        val setUp = steps.filter { guideStage(it) == SetupStage.SET_UP }.map { guideStageProgress(it, steps) }
        assertTrue(setUp.first() > 0.3f && setUp.zipWithNext().all { (a, b) -> b > a } && setUp.last() == 1f)
        assertEquals(1f, guideStageProgress(steps.last(), steps))
    }

    @Test fun `tracking spending and budgeting are always part of the focus`() {
        assertEquals(setOf(FinancialFocus.EVERYDAY_SPENDING, FinancialFocus.BUDGETS), FinancialFocus.MANDATORY)
        assertTrue(Feature.BUDGETS in FinancialFocus.initialFeatures(FinancialFocus.MANDATORY))
        assertFalse(FinancialFocus.SAVINGS.mandatory)
    }

    @Test fun `credit cards and loans turn on Debts and loans, cash, bank and e-wallets need nothing extra`() {
        assertEquals(emptySet<Feature>(), SpendType.features(setOf(SpendType.CASH, SpendType.BANK, SpendType.E_WALLET)))
        assertEquals(setOf(Feature.DEBTS_LOANS), SpendType.features(setOf(SpendType.CREDIT_CARD)))
        assertEquals(setOf(Feature.DEBTS_LOANS), SpendType.features(setOf(SpendType.LOAN, SpendType.CASH)))
        assertEquals(setOf(SpendType.CASH, SpendType.BANK), SpendType.DEFAULT)
    }

    @Test fun `the guided setup suggests the account types the user pays with`() {
        assertEquals(WalletType.BANK, SpendType.firstAssetType(SpendType.DEFAULT))
        assertEquals(WalletType.E_WALLET, SpendType.firstAssetType(setOf(SpendType.E_WALLET, SpendType.CASH)))
        assertEquals(WalletType.CASH, SpendType.firstAssetType(setOf(SpendType.CASH, SpendType.CREDIT_CARD)))
        assertEquals(WalletType.BANK, SpendType.firstAssetType(emptySet())) // not asked (older installs)
        assertEquals(WalletType.LOAN, SpendType.firstDebtType(setOf(SpendType.LOAN)))
        assertEquals(WalletType.CREDIT_CARD, SpendType.firstDebtType(setOf(SpendType.LOAN, SpendType.CREDIT_CARD)))
        assertEquals(WalletType.CREDIT_CARD, SpendType.firstDebtType(emptySet()))
        assertEquals(setOf(SpendType.CASH), SpendType.fromKeys(listOf("CASH", "NOT_A_TYPE")))
    }
}
