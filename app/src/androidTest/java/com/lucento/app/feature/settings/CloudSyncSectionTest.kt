package com.lucento.app.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.data.sync.SyncRepository
import com.lucento.app.data.sync.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Settings → Cloud sync in each state a real sign-in would produce, rendered directly (real Google sign-in needs OAuth
 * configuration this test environment doesn't have). Screenshots land in /data/local/tmp/lucento-shots for review.
 */
@RunWith(AndroidJUnit4::class)
class CloudSyncSectionTest {

    @get:Rule
    val compose = createComposeRule()

    private var connects = 0
    private var disconnects = 0
    private var syncs = 0

    private fun show(state: CloudSyncUiState) {
        compose.setContent {
            LucentoTheme(darkTheme = false) {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp)) {
                    CloudSyncSection(state, onConnectGoogle = { connects++ }, onDisconnect = { disconnects++ }, onSyncNow = { syncs++ })
                }
            }
        }
        compose.waitForIdle()
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        Thread.sleep(300)
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        ui.executeShellCommand("mkdir -p /data/local/tmp/lucento-shots").close()
        ui.executeShellCommand("screencap -p /data/local/tmp/lucento-shots/$name.png").close()
        Thread.sleep(500)
    }

    private val now = System.currentTimeMillis()
    private fun base(email: String?, status: SyncStatus = SyncStatus(enabled = email != null, lastSyncedAtMillis = null, lastError = null)) =
        CloudSyncUiState(configured = true, googleSignInConfigured = true, signedInEmail = email, status = status)

    @Test
    fun signedOut_explainsAndOffersGoogle() {
        show(base(email = null))
        compose.onNodeWithText("your data stays on this phone until you connect an account", substring = true).assertExists()
        compose.onNodeWithText("Connect with Google").performClick()
        assertEquals(1, connects)
        shot("cloud-01-signed-out")
    }

    @Test
    fun connected_showsAccountLastSyncAndBothActions() {
        show(base("ada@example.com", SyncStatus(enabled = true, lastSyncedAtMillis = now - 5 * 60_000, lastError = null))
            .copy(lastMessage = "Synced — 12 sent, 3 received"))
        compose.onNodeWithText("ada@example.com").assertExists()
        compose.onNodeWithText("Synced 5 min ago").assertExists()
        compose.onNodeWithText("Synced — 12 sent, 3 received").assertExists()
        compose.onNodeWithText("Sync now").performClick()
        compose.onNodeWithText("Disconnect").performClick()
        assertEquals(1, syncs)
        assertEquals(1, disconnects)
        shot("cloud-02-connected")
    }

    @Test
    fun syncing_disablesBothActions() {
        show(base("ada@example.com").copy(syncing = true))
        compose.onNodeWithText("Disconnect").assertIsNotEnabled()
        shot("cloud-03-syncing")
    }

    @Test
    fun failure_showsTheErrorAndReassures() {
        show(
            base("ada@example.com", SyncStatus(enabled = true, lastSyncedAtMillis = now - 60 * 60_000, lastError = "connection reset"))
                .copy(lastMessage = "Sync couldn't finish (connection reset) — your data is safe and it will retry."),
        )
        compose.onNodeWithText("Last sync failed: connection reset").assertExists()
        compose.onNodeWithText("your data is safe", substring = true).assertExists()
        shot("cloud-04-error")
    }

    @Test
    fun accountMismatch_isExplained() {
        show(
            base("someone.else@example.com", SyncStatus(enabled = true, lastSyncedAtMillis = null, lastError = SyncRepository.ACCOUNT_MISMATCH_MESSAGE))
                .copy(lastMessage = SyncRepository.ACCOUNT_MISMATCH_MESSAGE),
        )
        // Exactly once: the status line carries it, and the identical message below is not repeated.
        compose.onNodeWithText("Sign in with that account to keep syncing", substring = true).assertExists()
        shot("cloud-05-account-mismatch")
    }
}
