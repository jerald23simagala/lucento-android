package com.lucento.app.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Sizes
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.designsystem.theme.TonePair
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity

/**
 * Lucento-styled modal sheet: warm low surface, default drag handle, fully expanded. Its content rises gently into place
 * as the sheet opens (a short fade and lift; none when the system asks to remove animations), so every sheet arrives
 * the same way.
 *
 * A fast fling through a long form keeps its leftover speed inside the sheet ([KeepFlingInSheet]): reaching the top
 * mid-fling no longer carries on into dismissing the sheet, which closed the New wallet form while it was being scrolled
 * (owner, 2026-11-04). Dragging the handle, or pulling down from the top, still closes it — unless [dragToDismiss] is
 * false: a long form (New wallet, Transfer) then stays put however it is scrolled or pulled, and only Back (the system
 * gesture, or the form's own Cancel) closes it (owner, 2026-11-06: a long scroll down still closed the New wallet form).
 * Such a sheet takes [rememberStickySheetState], which refuses to be dragged or tapped away; its content keeps every
 * leftover scroll and drag, so the sheet doesn't even move, and it has no drag handle to promise a gesture that does
 * nothing. (Material 3 1.3 has no switch for the sheet's gestures; Back and `hide()` still close it.)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LucentoBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    dragToDismiss: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reducedMotion = LucentoTheme.reducedMotion
    val appear = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reducedMotion) appear.animateTo(1f, tween(durationMillis = 320, delayMillis = 60, easing = FastOutSlowInEasing))
    }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        dragHandle = if (dragToDismiss) DefaultHandle else null,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(
                    if (dragToDismiss) {
                        Modifier.nestedScroll(KeepFlingInSheet)
                    } else {
                        Modifier
                            .padding(top = Spacing.md)
                            .nestedScroll(KeepAllInSheet)
                            // A drag on the form's still parts (not taken by a scrolling list) goes no further either.
                            .pointerInput(Unit) { detectVerticalDragGestures { change, _ -> change.consume() } }
                    },
                )
                .graphicsLayer {
                    alpha = 0.35f + 0.65f * appear.value
                    translationY = (1f - appear.value) * 20.dp.toPx()
                },
            content = content,
        )
    }
}

/** Consumes what a content fling has left, so it never reaches the sheet; drags (and the sheet's own settling) pass. */
private val KeepFlingInSheet = object : NestedScrollConnection {
    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
}

/** For a sheet that only Back closes: whatever scroll or fling the content leaves is kept, so the sheet never moves. */
private val KeepAllInSheet = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available
    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
}

@OptIn(ExperimentalMaterial3Api::class)
private val DefaultHandle: @Composable () -> Unit = { BottomSheetDefaults.DragHandle() }

/** The state for a [LucentoBottomSheet] with `dragToDismiss = false`: fully open, and never dragged or tapped away. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberStickySheetState(): SheetState =
    rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { it != SheetValue.Hidden })

/**
 * The top of every sheet, so they read as one family: what the sheet is about as a tile — [leading] (a category, a
 * wallet, an avatar) or a tonal [icon] — then the title (a heading for TalkBack), a short [subtitle], and optional
 * [trailing] actions. Sits inside the sheet's own horizontal padding.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    tone: TonePair = StatusTones.brand,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    /** A small mark after the title (the profile menu's Lucento Pro chip); it wraps under a long title. */
    titleBadge: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).padding(bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            leading != null -> {
                leading()
                Spacer(Modifier.width(Spacing.md))
            }
            icon != null -> {
                IconTile(icon = icon, container = tone.container, content = tone.content, shape = MaterialTheme.shapes.medium)
                Spacer(Modifier.width(Spacing.md))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            if (titleBadge == null) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
            } else {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.CenterVertically).semantics { heading() },
                    )
                    androidx.compose.foundation.layout.Box(Modifier.align(Alignment.CenterVertically)) { titleBadge() }
                }
            }
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing?.invoke(this)
    }
}

/**
 * Confirmation dialog: a tonal icon (a warning for [destructive] actions, a question otherwise), the title and text,
 * then the confirm action as a filled button — red when [destructive] — beside Cancel. With a [secondaryLabel] the three
 * actions stack full width: confirm, the second action, Cancel.
 */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    dismissLabel: String? = "Cancel",
    /** A second action, shown above Cancel. Used for a destructive alternative when [confirmLabel] is the safe one. */
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    secondaryDestructive: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val ext = LucentoTheme.extendedColors
    val tone = if (destructive) TonePair(ext.negativeContainer, ext.negative) else TonePair(colors.primaryContainer, colors.onPrimaryContainer)
    val stacked = secondaryLabel != null && onSecondary != null
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceContainerLowest,
        icon = {
            IconTile(
                icon = if (destructive) Icons.Outlined.WarningAmber else Icons.AutoMirrored.Outlined.HelpOutline,
                container = tone.container,
                content = tone.content,
                size = 52.dp,
                shape = CircleShape,
            )
        },
        title = { Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center) },
        // Scrolls when it can't fit (large text, a short screen): the dialog then shrinks the text, never the buttons,
        // and without this the end of a long explanation was cut off where nobody could read it.
        text = {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
            )
        },
        confirmButton = {
            val confirmColors = if (destructive) {
                ButtonDefaults.buttonColors(containerColor = colors.error, contentColor = colors.onError)
            } else {
                ButtonDefaults.buttonColors()
            }
            if (stacked) {
                // Three actions stack full width, the safe one first: side by side they wrapped unevenly (a gap above
                // the second action, Cancel cut off at the dialog's edge) once a label grew past a few words.
                Column(Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onConfirm,
                        colors = confirmColors,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.minTouch),
                    ) { Text(confirmLabel, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center) }
                    Spacer(Modifier.height(Spacing.xs))
                    TextButton(onClick = onSecondary!!, modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.minTouch)) {
                        Text(
                            secondaryLabel!!,
                            color = if (secondaryDestructive) colors.error else colors.onSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center,
                        )
                    }
                    if (dismissLabel != null) {
                        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.minTouch)) {
                            Text(dismissLabel, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                        }
                    }
                }
            } else {
                Button(
                    onClick = onConfirm,
                    colors = confirmColors,
                    shape = MaterialTheme.shapes.small,
                ) { Text(confirmLabel, style = MaterialTheme.typography.labelLarge) }
            }
        },
        dismissButton = if (!stacked && dismissLabel != null) {
            { TextButton(onClick = onDismiss) { Text(dismissLabel, style = MaterialTheme.typography.labelLarge) } }
        } else {
            null
        },
    )
}
