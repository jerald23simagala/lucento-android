package com.lucento.app.feature.more

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
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.BuildConfig
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.LucentoCard
import com.lucento.app.core.designsystem.component.LucentoMark
import com.lucento.app.core.designsystem.component.LucentoTopBar
import com.lucento.app.core.designsystem.component.NavRow
import com.lucento.app.core.designsystem.component.SectionHeader
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Sizes
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.domain.Feature
import com.lucento.app.feature.plan.PlanViewModel
import com.lucento.app.feature.plan.budgetSummary

/** The "More" staging screen: secondary destinations and app management, each with a real status line. */
@Composable
fun MoreScreen(
    onOpenWallets: () -> Unit,
    onOpenPlan: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFeatures: () -> Unit,
    viewModel: PlanViewModel = viewModel(factory = PlanViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val f = state.features
    val planModules = listOf(Feature.BUDGETS, Feature.SAVINGS_GOALS, Feature.DEBTS_LOANS, Feature.RECURRING).filter { f.isEnabled(it) }
    val walletCount = state.wallets.size
    val ext = LucentoTheme.extendedColors

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter),
    ) {
        LucentoTopBar(title = "More")

        SectionHeader("Money")
        LucentoCard(Modifier.fillMaxWidth(), contentPadding = Spacing.sm) {
            NavRow(
                icon = Icons.Outlined.AccountBalanceWallet,
                title = if (f.isEnabled(Feature.MULTIPLE_WALLETS)) "Wallets" else "Account",
                summary = when (walletCount) {
                    0 -> "Add the cash, bank and e-wallet accounts you use"
                    1 -> "1 account"
                    else -> "$walletCount accounts"
                },
                tone = ext.bank,
                onClick = onOpenWallets,
            )
            if (planModules.isNotEmpty()) {
                Hairline(inset = Sizes.iconTile + Spacing.md + Spacing.xs)
                NavRow(
                    icon = Icons.Outlined.Timeline,
                    title = "Plan",
                    summary = if (f.isEnabled(Feature.BUDGETS)) budgetSummary(state) else planModules.joinToString(" · ") { it.title },
                    tone = ext.savings,
                    onClick = onOpenPlan,
                )
            }
        }

        SectionHeader("App", Modifier.padding(top = Spacing.lg))
        LucentoCard(Modifier.fillMaxWidth(), contentPadding = Spacing.sm) {
            NavRow(
                icon = Icons.Outlined.ToggleOn,
                title = "Enabled features",
                summary = "${Feature.entries.count { f.isEnabled(it) }} of ${Feature.entries.size} on · turn modules on or off",
                onClick = onOpenFeatures,
            )
            Hairline(inset = Sizes.iconTile + Spacing.md + Spacing.xs)
            NavRow(
                icon = Icons.Outlined.Settings,
                title = "Settings",
                summary = "Appearance, currency, export and privacy",
                onClick = onOpenSettings,
            )
        }

        Spacer(Modifier.height(Spacing.xl))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
        Spacer(Modifier.height(Spacing.xxl))
    }
}
