package com.lucento.app.domain

import com.lucento.app.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingFlowTest {

    @Test
    fun `a fresh install walks four screens and stops`() {
        assertEquals(FirstRunStep.entries.toList(), FirstRunStep.entries)
        assertEquals(4, FirstRunStep.COUNT)
        assertEquals(FirstRunStep.CURRENCY, OnboardingFlow.next(FirstRunStep.WELCOME))
        assertEquals(FirstRunStep.WALLET, OnboardingFlow.next(FirstRunStep.CURRENCY))
        assertEquals(FirstRunStep.FIRST_ENTRY, OnboardingFlow.next(FirstRunStep.WALLET))
        assertNull(OnboardingFlow.next(FirstRunStep.FIRST_ENTRY))
        assertEquals(FirstRunStep.WELCOME, OnboardingFlow.previous(FirstRunStep.CURRENCY))
        assertNull(OnboardingFlow.previous(FirstRunStep.WELCOME))
    }

    @Test
    fun `each screen announces its place in the four`() {
        assertEquals("Step 1 of 4", FirstRunStep.progressLabel(FirstRunStep.WELCOME))
        assertEquals("Step 2 of 4", FirstRunStep.progressLabel(FirstRunStep.CURRENCY))
        assertEquals("Step 3 of 4", FirstRunStep.progressLabel(FirstRunStep.WALLET))
        assertEquals("Step 4 of 4", FirstRunStep.progressLabel(FirstRunStep.FIRST_ENTRY))
    }

    @Test
    fun `finished onboarding is never shown again`() {
        assertFalse(OnboardingFlow.showOnboarding(onboardingComplete = true))
        assertTrue(OnboardingFlow.showOnboarding(onboardingComplete = false))
        assertNull(OnboardingFlow.resume(onboardingComplete = true, savedIndex = 0, shortFlow = false))
        assertNull(OnboardingFlow.resume(onboardingComplete = true, savedIndex = 7, shortFlow = true))
    }

    @Test
    fun `an unfinished short flow resumes the saved screen`() {
        assertEquals(FirstRunStep.WELCOME, OnboardingFlow.resume(false, 0, shortFlow = true))
        assertEquals(FirstRunStep.CURRENCY, OnboardingFlow.resume(false, 1, shortFlow = true))
        assertEquals(FirstRunStep.WALLET, OnboardingFlow.resume(false, 2, shortFlow = true))
        assertEquals(FirstRunStep.FIRST_ENTRY, OnboardingFlow.resume(false, 3, shortFlow = true))
    }

    @Test
    fun `an unfinished old install skips the removed screens`() {
        assertEquals(FirstRunStep.WELCOME, OnboardingFlow.resume(false, 0, shortFlow = false))
        // Old focus, features, categories, review and essentials all land on currency.
        for (index in 1..5) {
            assertEquals(FirstRunStep.CURRENCY, OnboardingFlow.resume(false, index, shortFlow = false))
        }
        // Old sync and confirmation already had a currency, so they open the wallet step.
        assertEquals(FirstRunStep.WALLET, OnboardingFlow.resume(false, 6, shortFlow = false))
        assertEquals(FirstRunStep.WALLET, OnboardingFlow.resume(false, 7, shortFlow = false))
    }

    @Test
    fun `defaults turn goals and recurring on and leave debts to one constant`() {
        val features = FirstRunDefaults.features()
        assertTrue(Feature.SAVINGS_GOALS in features)
        assertTrue(Feature.RECURRING in features)
        assertTrue(Feature.BUDGETS in features)
        assertTrue(Feature.MULTIPLE_WALLETS in features)
        assertEquals(FirstRunDefaults.DEBTS_ON_BY_DEFAULT, Feature.DEBTS_LOANS in features)
        assertTrue(FirstRunDefaults.DEBTS_ON_BY_DEFAULT)
        assertEquals("Cash", FirstRunDefaults.DEFAULT_WALLET_NAME)
        assertTrue(FirstRunDefaults.categoryPresetKeys().isNotEmpty())
        assertTrue("groceries" in FirstRunDefaults.categoryPresetKeys())
    }

    @Test
    fun `a blank starting balance is zero and a bad amount does not continue`() {
        assertEquals(Money.zero("PHP"), startingBalance("", "PHP", '.'))
        assertEquals(Money.zero("PHP"), startingBalance("   ", "PHP", '.'))
        assertEquals(Money(12_50, "PHP"), startingBalance("12.50", "PHP", '.'))
        assertNull(startingBalance("-5", "PHP", '.'))
        assertNull(startingBalance("nope", "PHP", '.'))
    }

    @Test
    fun `saving the first entry with defaults takes at most eight taps`() {
        assertTrue(OnboardingFlow.tapsToFirstSave() <= 8)
        assertEquals(5, OnboardingFlow.tapsToFirstSave())
    }

    @Test
    fun `composer privacy follows whether gemini is on`() {
        assertEquals(
            "Your entry is read on this phone and never sent anywhere.",
            ComposerPrivacy.line(geminiEnabled = false),
        )
        assertEquals(
            "Your entry is sent to Google Gemini through Lucento's server to be read.",
            ComposerPrivacy.line(geminiEnabled = true),
        )
    }

    @Test
    fun `first week steps tick off from real actions and the card stays dismissed`() {
        val fresh = FirstWeek.steps(realEntryCount = 1, hasGoal = false, hasBill = false, checkedInsights = false)
        assertEquals("0/3", fresh.first { it.id == "log3" }.progress)
        assertFalse(fresh.first { it.id == "log3" }.done)
        val done = FirstWeek.steps(realEntryCount = 4, hasGoal = true, hasBill = true, checkedInsights = true)
        assertTrue(done.all { it.done })
        assertEquals("3/3", done.first { it.id == "log3" }.progress)
        assertTrue(FirstWeek.show(dayOneHome = true, dismissed = false))
        assertFalse(FirstWeek.show(dayOneHome = true, dismissed = true))
        assertFalse(FirstWeek.show(dayOneHome = false, dismissed = false))
        assertFalse(FirstWeek.celebrate(dayOneHome = true, alreadyCelebrated = false, realEntryCount = 0))
        assertTrue(FirstWeek.celebrate(dayOneHome = true, alreadyCelebrated = false, realEntryCount = 1))
        assertFalse(FirstWeek.celebrate(dayOneHome = true, alreadyCelebrated = true, realEntryCount = 3))
        assertFalse(FirstWeek.celebrate(dayOneHome = false, alreadyCelebrated = false, realEntryCount = 5))
    }

    @Test
    fun `removed guide steps no longer block and a saved budget step moves on`() {
        val features = FeatureSet(FirstRunDefaults.features())
        assertFalse(GuideStep.BUDGETS in GuidedSetup.steps(features))
        assertFalse(GuideStep.DEBTS in GuidedSetup.steps(features))
        assertEquals(GuideStep.GOAL, GuidedSetup.resume(GuideStep.BUDGETS, features))
        assertEquals(GuideStep.GOAL, GuidedSetup.resume(GuideStep.DEBTS, features))
        assertTrue(GuidedSetup.canContinue(GuideStep.BUDGETS, assetWallets = 1, budgetedCategories = 0, expenseCategories = 5))
        assertEquals(GuideStep.WALLETS, GuidedSetup.resume(GuideStep.WALLETS, features))
    }
}
