package com.lucento.app.core.designsystem.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Where a short message appears: the app's snackbar, or an open sheet's own. [show] runs in the host's [scope], so a
 * composable that asks and then steps aside (a Pro dialog met again, owner 2026-11-19) still leaves its message.
 */
class MessageHost(val state: SnackbarHostState, private val scope: CoroutineScope) {
    fun show(message: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
        scope.launch {
            state.currentSnackbarData?.dismiss()
            val result = state.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                withDismissAction = actionLabel != null,
                // Time to reach the action, with TalkBack too; the close button ends it sooner.
                duration = if (actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) onAction()
        }
    }
}

/**
 * The message hosts on screen: the app's, and each open [LucentoBottomSheet]'s. A sheet is a window above the app, so
 * the app's snackbar would sit behind it; [top] is the newest open sheet's host, otherwise the app's. Main thread only.
 */
class MessageHosts {
    /** The app's own snackbar host (LucentoRoot). */
    var app: MessageHost? = null
    private val sheets = ArrayList<MessageHost>()

    /** Where a message shows now: the newest open sheet, otherwise the app. */
    val top: MessageHost? get() = sheets.lastOrNull() ?: app

    fun enterSheet(host: MessageHost) {
        sheets += host
    }

    fun leaveSheet(host: MessageHost) {
        sheets -= host
    }
}

/** Provided once by MainActivity; null in previews and tests (callers then fall back to a dialog). */
val LocalMessageHosts = staticCompositionLocalOf<MessageHosts?> { null }

/**
 * Lucento's snackbar, in the app and in every sheet: inverse colours, the action under the message from 130 % text (the
 * message keeps the width), and an action that ignores taps in its first [ACTION_GUARD_MS], so a quick second tap meant
 * for the button underneath (Save at a limit) can't take Unlock and close a draft (QA 2026-11-19).
 */
@Composable
fun LucentoSnackbar(data: SnackbarData) {
    val shownAt = remember(data) { android.os.SystemClock.uptimeMillis() }
    val label = data.visuals.actionLabel
    val colors = MaterialTheme.colorScheme
    Snackbar(
        // The margin Material's own snackbar keeps from the screen's edges.
        modifier = androidx.compose.ui.Modifier.padding(12.dp),
        action = label?.let {
            {
                TextButton(
                    onClick = { if (android.os.SystemClock.uptimeMillis() - shownAt >= ACTION_GUARD_MS) data.performAction() },
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.inversePrimary),
                ) { Text(it) }
            }
        },
        dismissAction = if (data.visuals.withDismissAction) {
            {
                IconButton(onClick = { data.dismiss() }) {
                    Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = colors.inverseOnSurface)
                }
            }
        } else {
            null
        },
        actionOnNewLine = LocalDensity.current.fontScale >= 1.3f,
        shape = MaterialTheme.shapes.medium,
        containerColor = colors.inverseSurface,
        contentColor = colors.inverseOnSurface,
    ) {
        Text(
            data.visuals.message,
            style = androidx.compose.material3.LocalTextStyle.current.copy(lineBreak = androidx.compose.ui.text.style.LineBreak.Heading),
        )
    }
}

/** How long a new snackbar's action ignores taps. */
const val ACTION_GUARD_MS = 600L
