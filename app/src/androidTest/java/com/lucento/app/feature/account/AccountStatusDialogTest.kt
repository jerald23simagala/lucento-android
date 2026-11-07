package com.lucento.app.feature.account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lucento.app.core.designsystem.theme.LucentoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * "Check your inbox" → "Email verified" (owner, 2026-11-06), rendered in each state: a real verification needs an inbox
 * this environment doesn't have. Screenshots land in /data/local/tmp/lucento-shots for review.
 */
@RunWith(AndroidJUnit4::class)
class AccountStatusDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private val taps = mutableListOf<String>()

    private fun show(waiting: EmailVerification.State?, ready: AccountReady? = null, dark: Boolean = false) {
        compose.setContent {
            LucentoTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize()) {
                    AccountStatusDialog(
                        waiting = waiting,
                        ready = ready,
                        onVisible = { taps += "visible:$it" },
                        onResend = { taps += "resend" },
                        onChangeEmail = { taps += "change" },
                        onPassword = { taps += "password:$it" },
                        onContinue = { taps += "continue" },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun waiting_saysWhereTheEmailWent_andCountsDownSendAgain() {
        show(EmailVerification.State.Waiting("ana@example.com", resendInSeconds = 42))
        compose.onNodeWithText("Check your inbox").assertExists()
        compose.onNodeWithText("ana@example.com", substring = true).assertExists()
        compose.onNodeWithText("Lucento carries on by itself", substring = true).assertExists()
        compose.onNodeWithText("Send again in 42s").assertIsNotEnabled()
        shot("account-01-check-inbox")
        assertTrue("checks while on screen", "visible:true" in taps)
        compose.onNodeWithText("Use a different email").performClick()
        assertEquals("change", taps.last())
    }

    @Test
    fun waiting_afterResend_saysItWasSentAgain() {
        show(EmailVerification.State.Waiting("ana@example.com", notice = EmailVerification.Notice.SENT_AGAIN))
        compose.onNodeWithText("Sent again", substring = true).assertExists()
        compose.onNodeWithText("Send the email again").performClick()
        assertEquals("resend", taps.last())
        shot("account-02-sent-again")
    }

    @Test
    fun afterARestart_asksForThePasswordOnce() {
        show(EmailVerification.State.Waiting("ana@example.com", needsPassword = true))
        compose.onNodeWithText("Lucento was closed while you were away", substring = true).assertExists()
        compose.onNodeWithText("Password").performTextInput("s3cret-pass")
        compose.onNodeWithText("Continue").performClick()
        assertEquals("password:s3cret-pass", taps.last())
        shot("account-03-password-again")
    }

    @Test
    fun verified_celebratesThenWaitsForTheAccountCheck() {
        show(EmailVerification.State.Verified("ana@example.com"))
        compose.onNodeWithText("Email verified").assertExists()
        compose.onNodeWithText("Getting things ready…").assertExists()
        compose.onNodeWithText("Continue").assertDoesNotExist()
        shot("account-04-verified-settling")
    }

    @Test
    fun ready_continuesToPersonalizing() {
        show(null, ready = AccountReady("ana@example.com", emailVerified = true))
        compose.onNodeWithText("Email verified").assertExists()
        shot("account-05-verified-ready")
        compose.onNodeWithText("Continue").performClick()
        assertEquals("continue", taps.last())
    }

    @Test
    fun google_saysConnected_inTheNightTheme() {
        show(null, ready = AccountReady("ana@gmail.com", emailVerified = false), dark = true)
        compose.onNodeWithText("You’re connected").assertExists()
        shot("account-06-google-connected-dark")
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        Thread.sleep(500)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf("mkdir -p /data/local/tmp/lucento-shots", "screencap -p /data/local/tmp/lucento-shots/$name.png").forEach {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(it)).use { s -> s.readBytes() }
        }
    }
}
