package com.lucento.app.feature.guide

import com.lucento.app.domain.GuideStep
import com.lucento.app.domain.UsageCounts
import com.lucento.app.domain.UsageLedger
import com.lucento.app.domain.UsageLedgerLogic
import com.lucento.app.feature.onboarding.OnboardingViewModel
import com.lucento.app.feature.onboarding.onboardingStepNumber
import org.junit.Assert.assertEquals
import org.junit.Test

class GuideStepNumberTest {
    @Test fun `guided setup pages are numbered 9 to 14 in order`() {
        assertEquals(
            listOf(GuideStep.WALLETS to 9, GuideStep.DEBTS to 10, GuideStep.BUDGETS to 11, GuideStep.GOAL to 12, GuideStep.TRANSACTIONS to 13, GuideStep.WELCOME to 14),
            GuideStep.entries.map { it to guideStepNumber(it) },
        )
    }

    @Test fun `guide numbering follows straight on from onboarding`() {
        assertEquals(onboardingStepNumber(OnboardingViewModel.LAST_STEP) + 1, guideStepNumber(GuideStep.entries.first()))
    }

    @Test fun `a guide page is sent as onboarding_step_viewed with its number`() {
        val ledger = UsageLedger(optedIn = true, installId = "id", installedAtMillis = 0L, onboardingStartedAtMillis = 1_000L)
        val event = UsageLedgerLogic.stepViewed(ledger, guideStepNumber(GuideStep.BUDGETS), now = 4_000L).queue.single()
        assertEquals(UsageCounts.ONBOARDING_STEP_VIEWED, event.name)
        assertEquals("11", event.props["step"])
        assertEquals("3000", event.props["ms"])
    }
}
