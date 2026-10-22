package com.lucento.app.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.BuildConfig
import com.lucento.app.core.designsystem.component.CurrencyPickerSheet
import com.lucento.app.core.designsystem.component.LucentoCard
import com.lucento.app.core.designsystem.component.SectionHeader
import com.lucento.app.core.designsystem.component.rememberHaptics
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.data.preferences.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val prefs by viewModel.uiState.collectAsStateWithLifecycle()
    var pickingCurrency by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val current = prefs ?: return

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter),
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.lg),
        )

        SectionHeader("Appearance")
        val modes = ThemeMode.entries
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            modes.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = current.themeMode == mode,
                    onClick = {
                        haptics.tick()
                        viewModel.setThemeMode(mode)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                ) {
                    Text(
                        when (mode) {
                            ThemeMode.SYSTEM -> "System"
                            ThemeMode.LIGHT -> "Light"
                            ThemeMode.DARK -> "Dark"
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(Spacing.xl))
        SectionHeader("Money")
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { pickingCurrency = true }
                .padding(vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Main currency", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${current.primaryCurrency} · ${MoneyFormatter.currencyName(current.primaryCurrency)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Change main currency")
        }

        Spacer(Modifier.height(Spacing.xl))
        SectionHeader("Privacy")
        LucentoCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(Spacing.md))
                Column {
                    Text("Your data stays on this device", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        "Lucento does not request internet access. Wallets and settings are stored locally " +
                            "and excluded from Android cloud backup.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(Spacing.xl))
        SectionHeader("About")
        Text(
            "Lucento ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Financial clarity without the complexity.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xxl))
    }

    if (pickingCurrency) {
        CurrencyPickerSheet(
            selected = current.primaryCurrency,
            onSelect = {
                viewModel.setPrimaryCurrency(it)
                pickingCurrency = false
            },
            onDismiss = { pickingCurrency = false },
        )
    }
}
