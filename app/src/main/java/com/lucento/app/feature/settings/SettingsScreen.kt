package com.lucento.app.feature.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.BuildConfig
import com.lucento.app.core.designsystem.component.CurrencyPickerSheet
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.IconTile
import com.lucento.app.core.designsystem.component.LucentoMark
import com.lucento.app.core.designsystem.component.LucentoTopBar
import com.lucento.app.core.designsystem.component.SectionHeader
import com.lucento.app.core.designsystem.component.rememberHaptics
import com.lucento.app.core.designsystem.theme.Sizes
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.data.preferences.ThemeMode
import com.lucento.app.data.preferences.UserPreferences

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val prefs by viewModel.uiState.collectAsStateWithLifecycle()
    var pickingCurrency by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter),
    ) {
        LucentoTopBar(title = "Settings", onBack = onBack)
        prefs?.let { current ->
            SettingsContent(
                current = current,
                onThemeMode = { mode ->
                    haptics.tick()
                    viewModel.setThemeMode(mode)
                },
                onPickCurrency = { pickingCurrency = true },
            )
        }
        Spacer(Modifier.height(Spacing.xxl))
    }

    val current = prefs
    if (pickingCurrency && current != null) {
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

@Composable
private fun SettingsContent(
    current: UserPreferences,
    onThemeMode: (ThemeMode) -> Unit,
    onPickCurrency: () -> Unit,
) {
    Column {
        SectionHeader("Appearance", Modifier.padding(top = Spacing.sm))
        Row(
            Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            ThemeMode.entries.forEach { mode ->
                ThemeOption(
                    mode = mode,
                    selected = current.themeMode == mode,
                    onSelect = { if (current.themeMode != mode) onThemeMode(mode) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        SectionHeader("Money", Modifier.padding(top = Spacing.section))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clip(MaterialTheme.shapes.medium)
                .clickable(role = Role.Button, onClickLabel = "Change main currency", onClick = onPickCurrency)
                .padding(vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(Sizes.iconTile)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    MoneyFormatter.currencySymbol(current.primaryCurrency).take(3),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text("Main currency", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${current.primaryCurrency} · ${MoneyFormatter.currencyName(current.primaryCurrency)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
        Text(
            "Used for net worth and Home insights. Each wallet keeps its own currency — Lucento never converts.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionHeader("Privacy & data", Modifier.padding(top = Spacing.section))
        Fact(Icons.Outlined.PhoneAndroid, "Stored only on this device", "Wallets, transactions and settings live in Lucento’s private storage.")
        Hairline(inset = Sizes.iconTile + Spacing.md)
        Fact(Icons.Outlined.WifiOff, "No internet access", "Lucento doesn’t request the internet permission, so nothing can be sent anywhere.")
        Hairline(inset = Sizes.iconTile + Spacing.md)
        Fact(Icons.Outlined.CloudOff, "Not in cloud backups", "Your financial data is excluded from Android’s automatic cloud backup.")
        Spacer(Modifier.height(Spacing.md))
        Text(
            "Export and backup aren’t available yet. Until they are, uninstalling Lucento or clearing its " +
                "storage permanently erases your data.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(Spacing.md),
        )

        SectionHeader("About", Modifier.padding(top = Spacing.section))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LucentoMark(size = 40.dp, animate = false)
            Spacer(Modifier.width(Spacing.md))
            Column {
                Text("Lucento ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Financial clarity without the complexity.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Text(
            "Set in Inter, licensed under the SIL Open Font License 1.1.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Theme choice with a miniature preview of the palette it selects. */
@Composable
private fun ThemeOption(mode: ThemeMode, selected: Boolean, onSelect: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val border by animateColorAsState(if (selected) colors.primary else colors.outlineVariant, tween(160), label = "theme-border")
    val light = Color(0xFFF6F3EB) to Color(0xFF1C4A42)
    val dark = Color(0xFF0D1211) to Color(0xFF8FCBBB)
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .border(BorderStroke(if (selected) 1.5.dp else 1.dp, border), MaterialTheme.shapes.medium)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(MaterialTheme.shapes.small)
                .border(BorderStroke(1.dp, colors.outlineVariant), MaterialTheme.shapes.small),
        ) {
            when (mode) {
                ThemeMode.LIGHT -> Swatch(light, Modifier.weight(1f))
                ThemeMode.DARK -> Swatch(dark, Modifier.weight(1f))
                ThemeMode.SYSTEM -> {
                    Swatch(light, Modifier.weight(1f))
                    Swatch(dark, Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            when (mode) {
                ThemeMode.SYSTEM -> "System"
                ThemeMode.LIGHT -> "Light"
                ThemeMode.DARK -> "Dark"
            },
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.onSurface else colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun Swatch(colors: Pair<Color, Color>, modifier: Modifier) {
    Box(
        modifier
            .fillMaxHeight()
            .background(colors.first)
            .padding(8.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Box(
            Modifier
                .width(22.dp)
                .height(6.dp)
                .background(colors.second, RoundedCornerShape(3.dp)),
        )
    }
}

/** A statement of how Lucento behaves — informational, not a toggle. */
@Composable
private fun Fact(icon: ImageVector, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.md), verticalAlignment = Alignment.Top) {
        IconTile(
            icon = icon,
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Spacing.xxs))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
