package com.lucento.app.feature.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.lucento.app.core.designsystem.component.LucentoBottomSheet
import com.lucento.app.core.designsystem.component.LucentoSecondaryButton
import com.lucento.app.core.designsystem.component.SheetHeader
import com.lucento.app.core.designsystem.theme.Spacing

/**
 * Settings › Home-screen widgets: one button per widget asks the home screen to place it (Android's pin request). A home
 * screen that can't, or doesn't, gets the long-press way in words.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun WidgetsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val manager = remember { AppWidgetManager.getInstance(context) }
    val canPin = remember { manager.isRequestPinAppWidgetSupported }
    var refused by remember { mutableStateOf(false) }
    fun pin(receiver: Class<*>) {
        val asked = canPin && runCatching { manager.requestPinAppWidget(ComponentName(context, receiver), null, null) }.getOrDefault(false)
        if (!asked) refused = true
    }
    LucentoBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter).navigationBarsPadding(),
        ) {
            SheetHeader(
                title = "Home-screen widgets",
                subtitle = "Add from your home screen, or see this month’s Safe to spend there. Both follow Hide amounts and App lock.",
                icon = Icons.Outlined.Widgets,
                modifier = Modifier.padding(bottom = Spacing.md),
            )
            WidgetOption(
                title = "Quick add",
                body = "Opens the composer to type, speak or scan a receipt. Nothing is saved until you tap Save.",
                button = "Add Quick add",
                onAdd = { pin(QuickAddWidgetReceiver::class.java) },
            )
            Spacer(Modifier.height(Spacing.lg))
            WidgetOption(
                title = "Safe to spend",
                body = "This month’s figure from Home, with Add. Never shown on the lock screen.",
                button = "Add Safe to spend",
                onAdd = { pin(SafeToSpendWidgetReceiver::class.java) },
            )
            Spacer(Modifier.height(Spacing.lg))
            Text(
                if (!canPin || refused) {
                    "Your home screen didn’t offer to add it here. Touch and hold an empty spot on your home screen, then choose Widgets › Lucento."
                } else {
                    "Or touch and hold an empty spot on your home screen, then choose Widgets › Lucento."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

@Composable
private fun WidgetOption(title: String, body: String, button: String, onAdd: () -> Unit) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(Spacing.sm))
    LucentoSecondaryButton(text = button, onClick = onAdd)
}
