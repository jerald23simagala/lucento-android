package com.lucento.app.feature.account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lucento.app.core.designsystem.theme.LucentoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The delete-account confirmation (docs/ACCOUNT_DELETION.md), in each state. Deleting a real account needs a signed-in
 * test account, so the dialog is rendered on its own; screenshots land in /data/local/tmp/lucento-shots for review.
 */
@RunWith(AndroidJUnit4::class)
class DeleteAccountDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private val taps = mutableListOf<String>()

    private fun show(prompt: DeleteAccountPrompt, dark: Boolean = false) {
        compose.setContent {
            LucentoTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize()) {
                    DeleteAccountDialog(
                        prompt = prompt,
                        onConfirm = { taps += "confirm" },
                        onDismiss = { taps += "dismiss" },
                        onManageSubscription = { taps += "manage" },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun deleteStaysOff_untilTheCheckboxIsTicked() {
        show(DeleteAccountPrompt(DeletionPlan()))
        compose.onNodeWithText(AccountDeletionCopy.TITLE).assertExists()
        compose.onNodeWithText("permanently deletes your account", substring = true).assertExists()
        compose.onNodeWithText(AccountDeletionCopy.MANAGE_SUBSCRIPTION).assertDoesNotExist()
        compose.onNodeWithText(AccountDeletionCopy.CONFIRM).assertIsNotEnabled()
        shot("delete-01-confirm")
        compose.onNodeWithText(AccountDeletionCopy.ACKNOWLEDGE).performScrollTo().performClick()
        compose.onNodeWithText(AccountDeletionCopy.CONFIRM).assertIsEnabled().performClick()
        assertEquals("confirm", taps.last())
        compose.onNodeWithText("Cancel").performClick()
        assertEquals("dismiss", taps.last())
    }

    @Test
    fun aSubscriber_isToldToCancelInGooglePlay_withTheLinkThere() {
        show(DeleteAccountPrompt(DeletionPlan(subscribed = true, lifetime = true)))
        compose.onNodeWithText("doesn’t cancel your Lucento subscription", substring = true).performScrollTo().assertExists()
        compose.onNodeWithText("restore it later", substring = true).performScrollTo().assertExists()
        compose.onNodeWithText(AccountDeletionCopy.MANAGE_SUBSCRIPTION).performScrollTo().performClick()
        assertEquals("manage", taps.last())
        shot("delete-02-subscriber")
    }

    @Test
    fun whileDeleting_nothingCanBeTapped() {
        show(DeleteAccountPrompt(DeletionPlan(), deleting = true))
        compose.onNodeWithText(AccountDeletionCopy.DELETING).assertExists()
        compose.onNodeWithText("Cancel").assertIsNotEnabled()
        shot("delete-03-deleting")
        assertTrue(taps.isEmpty())
    }

    @Test
    fun aFailure_isShownInTheDialog_inTheNightTheme() {
        show(DeleteAccountPrompt(DeletionPlan(), error = DeleteAccountFailures.OFFLINE), dark = true)
        compose.onNodeWithText(DeleteAccountFailures.OFFLINE).performScrollTo().assertExists()
        compose.onNodeWithText(AccountDeletionCopy.CONFIRM).assertIsNotEnabled()
        shot("delete-04-offline-dark")
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
