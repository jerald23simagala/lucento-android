package com.lucento.app.core.designsystem.component

import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The short message (owner, 2026-11-19): one at a time, and Unlock runs only when the person taps it. */
class MessageHostTest {

    @Test fun `a newer message replaces the one showing, and only the tapped one's action runs`() {
        val state = SnackbarHostState()
        val host = MessageHost(state, CoroutineScope(Dispatchers.Unconfined))
        var unlocked = ""

        host.show("The Free plan includes 3 money wallets", "Unlock") { unlocked += "wallets" }
        val first = state.currentSnackbarData!!
        assertEquals("The Free plan includes 3 money wallets", first.visuals.message)
        assertEquals("Unlock", first.visuals.actionLabel)

        // A second message takes its place; the first one's action never runs.
        host.show("Cloud sync is part of Lucento Pro", "Unlock") { unlocked += "sync" }
        assertEquals("Cloud sync is part of Lucento Pro", state.currentSnackbarData!!.visuals.message)
        assertEquals("", unlocked)

        state.currentSnackbarData!!.performAction()
        assertEquals("sync", unlocked)
        assertNull(state.currentSnackbarData)
    }

    @Test fun `dismissing a message runs nothing`() {
        val state = SnackbarHostState()
        val host = MessageHost(state, CoroutineScope(Dispatchers.Unconfined))
        var unlocked = false
        host.show("PDF statements are part of Lucento Pro", "Unlock") { unlocked = true }
        state.currentSnackbarData!!.dismiss()
        assertEquals(false, unlocked)
        assertNull(state.currentSnackbarData)
    }
}
