package com.lucento.app.feature.capture

import com.lucento.app.domain.entitlement.PlanCatalog
import org.junit.Assert.assertTrue
import org.junit.Test

/** Owner, 2026-11-23: Settings says first that Smarter reading spends the AI questions Ask Lucento uses. */
class SmartReadingCopyTest {

    @Test fun `the caveat names the shared monthly allowance`() {
        assertTrue(SmartReadingCopy.ALLOWANCE, SmartReadingCopy.ALLOWANCE.contains("${PlanCatalog.PRO_AI_QUESTIONS_PER_MONTH} AI questions a month"))
        assertTrue(SmartReadingCopy.ALLOWANCE, SmartReadingCopy.ALLOWANCE.contains("Ask Lucento"))
    }

    @Test fun `the switch's note and the consent say it first`() {
        assertTrue(SmartReadingCopy.NOTE.startsWith(SmartReadingCopy.ALLOWANCE))
        assertTrue(SmartReadingCopy.CONSENT_TEXT.startsWith(SmartReadingCopy.ALLOWANCE))
        assertTrue(SmartReadingCopy.CONSENT_TEXT.contains(SmartReadingCopy.SLOWER))
        assertTrue(SmartReadingCopy.CONSENT_TEXT.contains("this month’s AI questions are used up"))
    }
}
