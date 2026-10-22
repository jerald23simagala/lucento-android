package com.lucento.app.feature.capture

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.KeyboardVoice
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.component.IconTile
import com.lucento.app.core.designsystem.component.LucentoBottomSheet
import com.lucento.app.core.designsystem.component.NavRow
import com.lucento.app.core.designsystem.component.rememberHaptics
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.designsystem.theme.TonePair
import com.lucento.app.domain.Feature
import com.lucento.app.domain.FeatureSet
import kotlinx.coroutines.launch

/** The four ways to record a transaction, offered by the app-wide + button. */
enum class AddMethod { MANUAL, SCAN, SPEAK, TYPE }

/**
 * The Add Transaction sheet: Manual, Scan, Speak and Type It. Each option starts its real workflow; scanned, spoken
 * and typed entries always open in the normal editor for review before anything is saved. A module that is switched
 * off still appears, says so, and opens a screen that offers to turn it back on — so no option is a dead end.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionSheet(
    features: FeatureSet,
    onChoose: (AddMethod) -> Unit,
    onTransfer: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val ext = LucentoTheme.extendedColors
    val haptics = rememberHaptics()

    // Close the sheet first, then start the workflow, so the transition reads as one motion.
    fun choose(action: () -> Unit) {
        haptics.tick()
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            action()
        }
    }

    LucentoBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter)
                .navigationBarsPadding(),
        ) {
            Text("Add transaction", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(
                "Choose how you’d like to record it. You always review before saving.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                MethodCard(
                    title = "Manual",
                    hint = "Fill in the details",
                    icon = Icons.Outlined.EditNote,
                    tone = ext.cash,
                    onClick = { choose { onChoose(AddMethod.MANUAL) } },
                    modifier = Modifier.weight(1f),
                )
                MethodCard(
                    title = "Scan",
                    hint = if (features.isEnabled(Feature.RECEIPT_SCANNING)) "Read a receipt on this phone" else "Turned off · tap to turn on",
                    icon = Icons.Outlined.DocumentScanner,
                    tone = ext.bank,
                    onClick = { choose { onChoose(AddMethod.SCAN) } },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(Spacing.md))
            val voiceOn = features.isEnabled(Feature.VOICE_ENTRY)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                MethodCard(
                    title = "Speak",
                    hint = if (voiceOn) "“Spent 250 on lunch”" else "Turned off · tap to turn on",
                    icon = Icons.Outlined.KeyboardVoice,
                    tone = ext.savings,
                    onClick = { choose { onChoose(AddMethod.SPEAK) } },
                    modifier = Modifier.weight(1f),
                )
                MethodCard(
                    title = "Type it",
                    hint = if (voiceOn) "Describe it in a sentence" else "Turned off · tap to turn on",
                    icon = Icons.Outlined.Keyboard,
                    tone = ext.eWallet,
                    onClick = { choose { onChoose(AddMethod.TYPE) } },
                    modifier = Modifier.weight(1f),
                )
            }
            if (onTransfer != null) {
                Spacer(Modifier.height(Spacing.md))
                NavRow(
                    icon = Icons.Outlined.SwapHoriz,
                    title = "Transfer between accounts",
                    summary = "Moves money without counting it as spending",
                    onClick = { choose(onTransfer) },
                )
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

@Composable
private fun MethodCard(
    title: String,
    hint: String,
    icon: ImageVector,
    tone: TonePair,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 112.dp),
        shape = MaterialTheme.shapes.large,
        color = if (LucentoTheme.isDark) colors.surfaceContainer else colors.surfaceContainerLowest,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            IconTile(icon = icon, container = tone.container, content = tone.content)
            Spacer(Modifier.height(Spacing.md))
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
