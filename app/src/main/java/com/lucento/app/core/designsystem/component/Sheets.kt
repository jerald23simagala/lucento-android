package com.lucento.app.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.Alignment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Lucento-styled modal sheet: warm low surface, default drag handle, fully expanded. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LucentoBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        content = content,
    )
}

/** Confirmation dialog. [destructive] colours the confirm action with the error colour. */
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
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = { Text(text, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
        dismissButton = if (dismissLabel != null || secondaryLabel != null) {
            {
                Column(horizontalAlignment = Alignment.End) {
                    if (secondaryLabel != null && onSecondary != null) {
                        TextButton(onClick = onSecondary) {
                            Text(
                                secondaryLabel,
                                color = if (secondaryDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                    if (dismissLabel != null) {
                        TextButton(onClick = onDismiss) { Text(dismissLabel, style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }
        } else {
            null
        },
    )
}
