package com.lucento.app.feature.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDataNoticeTest {

    @Test fun anExistingFinishedInstallSeesTheNoticeOnce() {
        assertTrue(showLocalDataNotice(onboardingComplete = true, noticeSeen = false))
        assertFalse(showLocalDataNotice(onboardingComplete = true, noticeSeen = true))
    }

    @Test fun setupThatIsStillInProgressDoesNotShowIt() {
        assertFalse(showLocalDataNotice(onboardingComplete = false, noticeSeen = false))
    }
}