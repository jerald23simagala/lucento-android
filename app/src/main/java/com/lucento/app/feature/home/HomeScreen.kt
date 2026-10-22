package com.lucento.app.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.core.designsystem.component.AmountText
import com.lucento.app.core.designsystem.component.EmptyState
import com.lucento.app.core.designsystem.component.LucentoCard
import com.lucento.app.core.designsystem.component.LucentoMark
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.component.SectionHeader
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.Money
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.feature.wallets.WalletDraft
import com.lucento.app.feature.wallets.WalletEditorSheet
import com.lucento.app.feature.wallets.WalletRow
import com.lucento.app.feature.wallets.WalletsUiState
import com.lucento.app.feature.wallets.WalletsViewModel
import com.lucento.app.feature.wallets.toDraft
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val HOME_WALLET_LIMIT = 4

@Composable
fun HomeScreen(
    onManageWallets: () -> Unit,
    viewModel: WalletsViewModel = viewModel(factory = WalletsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<WalletDraft?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter),
    ) {
        Header()

        AnimatedContent(
            targetState = when {
                state.loading -> HomeContent.Loading
                state.wallets.isEmpty() -> HomeContent.Empty
                else -> HomeContent.Overview
            },
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
            label = "home-content",
        ) { content ->
            when (content) {
                HomeContent.Loading -> Spacer(Modifier.height(1.dp))
                HomeContent.Empty -> LucentoCard(Modifier.fillMaxWidth()) {
                    EmptyState(
                        title = "Start with a wallet",
                        body = "Add the accounts you actually use — cash, bank, e-wallet, savings or a credit card. " +
                            "Your net worth appears here, calculated on this device.",
                        illustration = { LucentoMark(size = 72.dp, animate = false) },
                        action = {
                            LucentoPrimaryButton(text = "Add wallet", onClick = {
                                editing = WalletDraft(currencyCode = state.primaryCurrency)
                            })
                        },
                    )
                }
                HomeContent.Overview -> Overview(
                    state = state,
                    onWalletClick = { editing = it },
                    onManageWallets = onManageWallets,
                )
            }
        }
        Spacer(Modifier.height(Spacing.xxl))
    }

    editing?.let { draft ->
        WalletEditorSheet(
            initial = draft,
            onSave = viewModel::save,
            onDelete = viewModel::delete,
            onDismiss = { editing = null },
        )
    }
}

private enum class HomeContent { Loading, Empty, Overview }

@Composable
private fun Header() {
    val greeting = remember {
        when (LocalTime.now().hour) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
    }
    val today = remember { LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)) }
    Column(Modifier.padding(top = Spacing.xl, bottom = Spacing.xl)) {
        Text(
            today,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            greeting,
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
private fun Overview(
    state: WalletsUiState,
    onWalletClick: (WalletDraft) -> Unit,
    onManageWallets: () -> Unit,
) {
    val summary = state.summary ?: return
    val primary = summary.totalFor(state.primaryCurrency) ?: summary.totals.first()
    val others = summary.totals.filter { it.currencyCode != primary.currencyCode }

    Column {
        LucentoCard(Modifier.fillMaxWidth(), contentPadding = Spacing.xl) {
            Text(
                "Net worth",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.sm))
            AmountText(
                money = primary,
                style = AmountStyles.hero,
                color = if (primary.isNegative) LucentoTheme.extendedColors.negative else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Spacing.lg))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(Spacing.lg))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                Figure("Assets", summary.assets[primary.currencyCode] ?: Money.zero(primary.currencyCode), Modifier.weight(1f))
                Figure("Owed", summary.liabilities[primary.currencyCode] ?: Money.zero(primary.currencyCode), Modifier.weight(1f))
            }
            if (others.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.lg))
                Text(
                    "Also held: " + others.joinToString(" · ") { MoneyFormatter.format(it) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Currencies are kept separate — Lucento never converts with guessed rates.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(Spacing.xl))
        SectionHeader(title = "Wallets") {
            LucentoTextButton(
                text = if (state.wallets.size > HOME_WALLET_LIMIT) "See all ${state.wallets.size}" else "Manage",
                onClick = onManageWallets,
            )
        }
        state.wallets.take(HOME_WALLET_LIMIT).forEachIndexed { index, wallet ->
            WalletRow(wallet = wallet, onClick = { onWalletClick(wallet.toDraft()) })
            if (index < minOf(state.wallets.size, HOME_WALLET_LIMIT) - 1) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun Figure(label: String, money: Money, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.Start) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.xxs))
        AmountText(money = money)
    }
}
