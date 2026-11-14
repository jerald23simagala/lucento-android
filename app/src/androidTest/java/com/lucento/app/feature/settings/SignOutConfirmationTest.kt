package com.lucento.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.data.preferences.ThemeMode
import com.lucento.app.data.preferences.UserPreferences
import com.lucento.app.feature.account.SignOutCase
import com.lucento.app.feature.profile.ProfileDestination
import com.lucento.app.feature.profile.ProfileSheet
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Sign out (owner decision 2026-11-12, F-2): each case's confirmation, and the profile menu's row. Screenshots land in
 * /data/local/tmp/lucento-shots (signout-*) for review; run once with the phone's font size at 200 % too.
 */
@RunWith(AndroidJUnit4::class)
class SignOutConfirmationTest {

    @get:Rule
    val compose = createComposeRule()

    private var confirms = 0
    private var removes = 0
    private var dismisses = 0

    private fun show(case: SignOutCase, pending: Int = 0) {
        compose.setContent {
            LucentoTheme(darkTheme = false) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    SignOutConfirmation(
                        case = case,
                        email = "ada@example.com",
                        pendingChanges = pending,
                        onConfirm = { confirms++ },
                        onRemove = { removes++ },
                        onDismiss = { dismisses++ },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        Thread.sleep(300)
        val scale = Math.round(InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration.fontScale * 100)
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        ui.executeShellCommand("mkdir -p /data/local/tmp/lucento-shots").close()
        ui.executeShellCommand("screencap -p /data/local/tmp/lucento-shots/signout-$name-$scale.png").close()
        Thread.sleep(500)
    }

    @Test
    fun safeInAccount_oneSignOutButton() {
        show(SignOutCase.SAFE_IN_ACCOUNT)
        compose.onNodeWithText("Everything is safe in your account (ada@example.com)", substring = true).assertExists()
        compose.onNodeWithText("Sign out anyway").assertDoesNotExist()
        shot("1-safe")
        compose.onNodeWithText("Sign out").performClick()
        assertEquals(1, confirms)
        assertEquals(0, removes)
    }

    @Test
    fun unsyncedCanSync_syncFirstOrSignOutAnyway() {
        show(SignOutCase.UNSYNCED_CAN_SYNC, pending = 3)
        compose.onNodeWithText("3 changes haven’t reached", substring = true).assertExists()
        shot("2-sync-first")
        compose.onNodeWithText("Sync first").performClick()
        compose.onNodeWithText("Sign out anyway").performClick()
        assertEquals(1, confirms)
        assertEquals(1, removes)
    }

    @Test
    fun unsyncedOnAFreePlan_offersABackupNotASync() {
        show(SignOutCase.UNSYNCED_NEEDS_BACKUP, pending = 1)
        compose.onNodeWithText("Sync first").assertDoesNotExist()
        compose.onNodeWithText("backups are always free", substring = true).assertExists()
        shot("3-backup")
        compose.onNodeWithText("Save a backup").performClick()
        assertEquals(1, confirms)
    }

    @Test
    fun phoneOnly_keepOrRemove() {
        show(SignOutCase.PHONE_ONLY, pending = 214)
        compose.onNodeWithText("your records are only here", substring = true).assertExists()
        compose.onNodeWithText("download", substring = true).assertDoesNotExist()
        shot("4-phone-only")
        // Large text: the explanation scrolls inside the dialog, so its end is never out of reach.
        compose.onNodeWithText("your records are only here", substring = true).performTouchInput { swipeUp() }
        shot("4b-phone-only-scrolled")
        compose.onNodeWithText("Keep them here").performClick()
        compose.onNodeWithText("Remove and sign out").performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(1, confirms)
        assertEquals(1, removes)
        assertEquals(1, dismisses)
    }

    private fun profile(email: String?, onOpen: (ProfileDestination) -> Unit = {}) {
        compose.setContent {
            LucentoTheme(darkTheme = false) {
                ProfileSheet(
                    preferences = UserPreferences(onboardingComplete = true, themeMode = ThemeMode.LIGHT, primaryCurrency = "USD"),
                    onSaveName = {},
                    onTheme = {},
                    onThemeId = {},
                    onOpen = onOpen,
                    onDismiss = {},
                    signedInEmail = email,
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun profileMenu_offersSignOutOnlyWhileSignedIn() {
        var opened: ProfileDestination? = null
        profile("ada@example.com") { opened = it }
        compose.onNodeWithText("Signed in as ada@example.com").assertExists()
        compose.onNodeWithText("Sign out").performScrollTo()
        shot("5-profile")
        compose.onNodeWithText("Sign out").performClick()
        // The sheet slides away first; slow emulators take seconds over it.
        compose.waitUntil(15_000) { opened != null }
        assertEquals(ProfileDestination.SIGN_OUT, opened)
    }

    @Test
    fun profileMenu_hasNoSignOutWithoutAnAccount() {
        profile(null)
        compose.onNodeWithText("Sign out").assertDoesNotExist()
    }
}
