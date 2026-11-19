package com.lucento.app.core.designsystem.component

import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * A sheet is a window above the app, so a message asked for while one is open must show in it (owner, 2026-11-19: a
 * Pro wall met again is a short message). The newest open sheet wins; with none open, the app's snackbar.
 */
class MessageHostsTest {

    @Test fun `messages go to the newest open sheet, otherwise the app`() {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val app = MessageHost(SnackbarHostState(), scope)
        val editor = MessageHost(SnackbarHostState(), scope)
        val typePicker = MessageHost(SnackbarHostState(), scope)
        val hosts = MessageHosts()
        assertNull(hosts.top)

        hosts.app = app
        assertSame(app, hosts.top)
        hosts.enterSheet(editor)
        hosts.enterSheet(typePicker)
        assertSame(typePicker, hosts.top)
        hosts.leaveSheet(typePicker)
        assertSame(editor, hosts.top)
        hosts.leaveSheet(editor)
        assertSame(app, hosts.top)
    }
}
