package com.lucento.app.feature.onboarding

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lucento.app.core.designsystem.theme.LucentoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Onboarding's "I already have an account" flow in each stage, rendered directly (a real sign-in and download need a
 * live account this environment doesn't have). Screenshots land in /data/local/tmp/lucento-shots for review.
 */
@RunWith(AndroidJUnit4::class)
class RestoreFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private val taps = mutableListOf<String>()

    private fun show(stage: RestoreStage, email: String? = null, message: String? = null) {
        compose.setContent {
            LucentoTheme(darkTheme = false) {
                RestoreFlow(
                    stage = stage,
                    signedInEmail = email,
                    googleAvailable = true,
                    connecting = false,
                    message = message,
                    onContinue = { taps += "continue" },
                    onGoogle = { taps += "google" },
                    onEmail = { _, _, _ -> taps += "email" },
                    onPrepareEmail = {},
                    onRetry = { taps += "retry" },
                    onSetUpAsNew = { taps += "new" },
                    onBack = { taps += "back" },
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun warn_recommendsSyncingTheOldPhoneFirst() {
        show(RestoreStage.WARN)
        compose.onNodeWithText("Moving to a new phone?").assertExists()
        compose.onNodeWithText("won’t show up here", substring = true).assertExists()
        shot("restore-01-warning")
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Back").performClick()
        assertEquals(listOf("continue", "back"), taps)
    }

    @Test
    fun signIn_offersTheSameAccountOptions() {
        show(RestoreStage.SIGN_IN)
        compose.onNodeWithText("Sign in to your account").assertExists()
        compose.onNodeWithText("Continue with Google").performClick()
        assertEquals(listOf("google"), taps)
        shot("restore-02-sign-in")
    }

    @Test
    fun restoring_showsProgressAndNoActions() {
        show(RestoreStage.RESTORING, email = "ada@example.com")
        compose.onNodeWithText("Bringing your money across").assertExists()
        compose.onNodeWithText("Downloading ada@example.com", substring = true).assertExists()
        compose.onNodeWithText("Back").assertDoesNotExist()
        shot("restore-03-restoring")
    }

    @Test
    fun emptyAccount_continuesWithNormalSetup() {
        show(RestoreStage.EMPTY, email = "ada@example.com")
        compose.onNodeWithText("Nothing to restore yet").assertExists()
        compose.onNodeWithText("Set up as new").performClick()
        assertEquals(listOf("new"), taps)
        shot("restore-04-empty")
    }

    @Test
    fun failure_offersRetryOrNewSetup() {
        show(RestoreStage.FAILED, message = "Your data couldn’t be downloaded (timeout). Check your connection and try again.")
        compose.onNodeWithText("Nothing was changed on this phone.").assertExists()
        compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithText("Set up as new instead").performClick()
        assertEquals(listOf("retry", "new"), taps)
        shot("restore-05-failed")
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        Thread.sleep(300)
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        ui.executeShellCommand("mkdir -p /data/local/tmp/lucento-shots").close()
        ui.executeShellCommand("screencap -p /data/local/tmp/lucento-shots/$name.png").close()
        Thread.sleep(500)
    }
}
