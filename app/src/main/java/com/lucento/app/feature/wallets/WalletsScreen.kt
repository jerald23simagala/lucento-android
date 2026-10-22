package com.lucento.app.feature.wallets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.core.designsystem.component.AmountText
import com.lucento.app.core.designsystem.component.EmptyState
import com.lucento.app.core.designsystem.component.Figure
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoMascot
import com.lucento.app.core.designsystem.component.MascotMood
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.LucentoTopBar
import com.lucento.app.core.designsystem.component.MoneyText
import com.lucento.app.core.designsystem.component.SectionHeader
import com.lucento.app.core.designsystem.component.ShareBar
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Sizes
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.Money
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.domain.Insights
import com.lucento.app.domain.Wallet

@Composable
fun WalletsScreen(
    onBack: () -> Unit,
    onOpenWallet: (Long) -> Unit,
    /** False when Multiple wallets is off and one account already exists. */
    allowAdding: Boolean,
    viewModel: WalletsViewModel = viewModel(factory = WalletsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<WalletDraft?>(null) }
    val newDraft = { WalletDraft(currencyCode = state.primaryCurrency) }
    val canAdd = allowAdding || state.wallets.isEmpty()

    // Assets by type order, then liabilities: the two halves of net worth.
    val assets = state.wallets.filterNot { it.type.isLiability }.sortedBy { it.type.ordinal }
    val liabilities = state.wallets.filter { it.type.isLiability }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.fabClearance),
        ) {
            item(key = "title") { LucentoTopBar(title = "Wallets", onBack = onBack) }

            if (!state.loading && state.wallets.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = "No wallets yet",
                        body = "Add the cash, bank accounts, e-wallets, savings and cards you use. " +
                            "Balances are entered by you and stay on this device — no bank connection needed.",
                        illustration = { LucentoMascot(size = 96.dp, mood = MascotMood.Curious) },
                        action = { LucentoPrimaryButton(text = "Add wallet", onClick = { editing = newDraft() }) },
                    )
                }
            }

            state.summary?.let { summary ->
                item(key = "summary") {
                    Summary(summary.totalFor(state.primaryCurrency) ?: summary.totals.first(), summary)
                }
            }

            walletSection(
                key = "assets",
                title = "Assets",
                wallets = assets,
                primaryCurrency = state.primaryCurrency,
                onEdit = { onOpenWallet(it.id) },
            )
            walletSection(
                key = "liabilities",
                title = "Liabilities",
                wallets = liabilities,
                primaryCurrency = state.primaryCurrency,
                onEdit = { onOpenWallet(it.id) },
                footnote = "Credit cards and loans show what you owe. They reduce your net worth and are never counted as money you have.",
            )
            if (!canAdd) {
                item(key = "single") {
                    InfoNote(
                        "You’re tracking a single account. Turn on Multiple wallets in Settings › Enabled features to add more.",
                        modifier = Modifier.padding(top = Spacing.lg),
                    )
                }
            }
        }

        if (state.wallets.isNotEmpty() && canAdd) {
            ExtendedFloatingActionButton(
                onClick = { editing = newDraft() },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add wallet") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(Spacing.gutter),
            )
        }
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

private fun androidx.compose.foundation.lazy.LazyListScope.walletSection(
    key: String,
    title: String,
    wallets: List<Wallet>,
    primaryCurrency: String,
    onEdit: (Wallet) -> Unit,
    footnote: String? = null,
) {
    if (wallets.isEmpty()) return
    item(key = "header-$key") {
        // Per-currency subtotal; currencies are never added together.
        val subtotals = wallets.groupBy { it.currencyCode }
            .map { (code, list) -> Money.sum(code, list.map { it.balance }) }
            .sortedWith(compareBy<Money> { it.currencyCode != primaryCurrency }.thenBy { it.currencyCode })
        SectionHeader(title, Modifier.padding(top = Spacing.lg)) {
            Text(
                subtotals.joinToString(" · ") { MoneyFormatter.format(it) },
                style = AmountStyles.small,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
    itemsIndexed(wallets, key = { _, w -> "wallet-${w.id}" }) { index, wallet ->
        Column(Modifier.animateItem()) {
            WalletRow(wallet = wallet, onClick = { onEdit(wallet) })
            if (index < wallets.lastIndex) Hairline(inset = Sizes.iconTile + Spacing.md)
        }
    }
    if (footnote != null) {
        item(key = "footnote-$key") {
            Text(
                footnote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
    }
}

/** Net worth split into what you have and what you owe, in the main currency. */
@Composable
private fun Summary(net: Money, summary: com.lucento.app.domain.NetWorthCalculator.Summary) {
    val code = net.currencyCode
    val assets = summary.assets[code] ?: Money.zero(code)
    val owed = summary.liabilities[code] ?: Money.zero(code)
    val colors = LucentoTheme.extendedColors
    Column(Modifier.fillMaxWidth().padding(top = Spacing.xs, bottom = Spacing.sm)) {
        Figure("Net worth · $code") {
            MoneyText(money = net, style = AmountStyles.hero, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(Spacing.lg))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Figure("You have", Modifier.weight(1f)) { MoneyText(money = assets, style = AmountStyles.medium, fractionScale = 1f) }
            Figure("You owe", Modifier.weight(1f)) {
                MoneyText(money = owed, style = AmountStyles.medium, fractionScale = 1f, color = if (owed.isZero) MaterialTheme.colorScheme.onSurface else colors.negative)
            }
        }
        val percent = Insights.percentOf(owed, assets)
        if (!owed.isZero && percent != null) {
            Spacer(Modifier.height(Spacing.md))
            ShareBar(fraction = Insights.fraction(owed, assets), color = colors.negative)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "What you owe is $percent% of what you have.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (summary.totals.size > 1) {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "Other currencies: " + summary.totals.filter { it.currencyCode != code }
                    .joinToString(" · ") { MoneyFormatter.formatSigned(it, showPlus = false) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
