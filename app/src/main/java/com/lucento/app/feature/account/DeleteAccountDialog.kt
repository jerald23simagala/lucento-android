package com.lucento.app.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.component.IconTile
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Sizes
import com.lucento.app.core.designsystem.theme.Spacing

/**
 * The one confirmation before an account is deleted (docs/ACCOUNT_DELETION.md): what goes, what a Google Play purchase
 * means, and a checkbox that must be ticked before the red button works. While the server call runs nothing can be
 * dismissed, so the person never wonders whether it happened.
 */
@Composable
fun DeleteAccountDialog(
    prompt: DeleteAccountPrompt,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onManageSubscription: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val ext = LucentoTheme.extendedColors
    var acknowledged by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!prompt.deleting) onDismiss() },
        containerColor = colors.surfaceContainerLowest,
        icon = {
            IconTile(
                icon = Icons.Outlined.DeleteForever,
                container = ext.negativeContainer,
                content = ext.negative,
                size = 52.dp,
                shape = CircleShape,
            )
        },
        title = {
            Text(AccountDeletionCopy.TITLE, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                AccountDeletionCopy.paragraphs(prompt.plan).forEach {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
                if (prompt.plan.subscribed) {
                    OutlinedButton(
                        onClick = onManageSubscription,
                        enabled = !prompt.deleting,
                        modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.minTouch),
                    ) {
                        Text(AccountDeletionCopy.MANAGE_SUBSCRIPTION, textAlign = TextAlign.Center)
                        Spacer(Modifier.width(Spacing.xs))
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .toggleable(
                            value = acknowledged,
                            enabled = !prompt.deleting,
                            role = Role.Checkbox,
                            onValueChange = { acknowledged = it },
                        )
                        .padding(vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = acknowledged,
                        onCheckedChange = null,
                        enabled = !prompt.deleting,
                        modifier = Modifier.size(Sizes.minTouch),
                    )
                    Text(AccountDeletionCopy.ACKNOWLEDGE, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
                prompt.error?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = acknowledged && !prompt.deleting,
                colors = ButtonDefaults.buttonColors(containerColor = colors.error, contentColor = colors.onError),
                shape = MaterialTheme.shapes.small,
            ) {
                if (prompt.deleting) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.onError)
                    Spacer(Modifier.width(Spacing.xs))
                    Text(AccountDeletionCopy.DELETING, style = MaterialTheme.typography.labelLarge)
                } else {
                    Text(AccountDeletionCopy.CONFIRM, style = MaterialTheme.typography.labelLarge)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !prompt.deleting) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}
