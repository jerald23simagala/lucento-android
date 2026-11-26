package com.lucento.app.core.designsystem.component

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * On wide windows with two panes (owner, 2026-11-26), what the right-hand pane is showing, so the list beside it can
 * mark that row as the open one. Null everywhere else (phones, one pane): nothing is marked.
 */
data class PaneSelection(val transactionId: Long? = null, val walletId: Long? = null)

val LocalPaneSelection = compositionLocalOf<PaneSelection?> { null }

/** The open row's leading bar: the mark that doesn't rely on colour alone. */
private val OpenBarWidth = 3.dp

/**
 * A list row that is open in the pane beside it: a tint behind it ([tint], the theme's selected-container colour), a bar
 * along its leading edge ([bar], the theme's primary), and "selected" for TalkBack, so the link between the two panes
 * is seen, without relying on colour, and heard.
 */
fun Modifier.openInPane(open: Boolean, tint: Color, bar: Color): Modifier =
    if (!open) {
        this
    } else {
        this
            .drawBehind {
                drawRect(tint)
                val width = OpenBarWidth.toPx()
                val x = if (layoutDirection == LayoutDirection.Rtl) size.width - width else 0f
                drawRect(bar, topLeft = Offset(x, 0f), size = Size(width, size.height))
            }
            .semantics { selected = true }
    }
