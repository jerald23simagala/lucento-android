package com.lucento.app.feature.wallets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.core.designsystem.component.ActionTile
import com.lucento.app.core.designsystem.component.EmptyState
import com.lucento.app.core.designsystem.component.Figure
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoCard
import com.lucento.app.core.designsystem.component.LucentoTopBar
import com.lucento.app.core.designsystem.component.MoneyText
import com.lucento.app.core.designsystem.component.SectionHeader
import com.lucento.app.core.designsystem.component.WalletTypeTile
import com.lucento.app.core.designsystem.component.label
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.OverlineStyle
import com.lucento.app.core.designsystem.theme.Sizes
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.WalletType
import com.lucento.app.feature.transactions.TransactionRow
import com.lucento.app.feature.transactions.shortDayLabel

/**
 * One account: what it is, its current balance (opening balance ± recorded movements), and its own
 * history. Liabilities read as "amount owed", with payment and interest actions that record the right
 * kind of movement (a payment is a transfer, interest is an expense).
 */
@Composable
fun WalletDetailScreen(
    walletId: Long,
    onBack: () -> Unit,
    onAddTransaction: (TransactionType, Long) -> Unit,
    /** Opens the transfer sheet; (from, to) pre-selects accounts. */
    onTransfer: (Long?, Long?) -> Unit,
    onEditTransaction: (Transaction) -> Unit,
    transfersEnabled: Boolean,
    viewModel: WalletDetailViewModel = viewModel(key = "wallet-$walletId", factory = WalletDetailViewModel.factory(walletId)),
    walletsViewModel: WalletsViewModel = viewModel(factory = WalletsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<WalletDraft?>(null) }
    val wallet = state.wallet

    // The account was deleted (from its editor): leave the screen.
    LaunchedEffect(state.loading, wallet) {
        if (!state.loading && wallet == null) onBack()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.xxl),
    ) {
        item(key = "bar") {
            LucentoTopBar(
                title = wallet?.name ?: "Account",
                subtitle = wallet?.let { "${it.type.label} · ${it.currencyCode}" },
                onBack = onBack,
                actions = {
                    if (wallet != null) {
                        IconButton(onClick = { editing = wallet.toDraft() }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit account")
                        }
                    }
                },
            )
        }
        if (wallet == null) return@LazyColumn
        val liability = wallet.type.isLiability

        item(key = "balance") {
            LucentoCard(Modifier.fillMaxWidth(), contentPadding = Spacing.xl) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WalletTypeTile(wallet.type, size = Sizes.iconTileLarge)
                    Spacer(Modifier.width(Spacing.md))
                    Text(
                        if (liability) "AMOUNT OWED" else "CURRENT BALANCE",
                        style = OverlineStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(Spacing.md))
                MoneyText(
                    money = wallet.balance,
                    style = AmountStyles.display,
                    color = if (liability && !wallet.balance.isZero) LucentoTheme.extendedColors.negative else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(Spacing.md))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Figure(if (liability) "Owed at start" else "Opening balance", Modifier.weight(1f)) {
                        MoneyText(money = wallet.openingBalance, style = AmountStyles.medium, fractionScale = 1f)
                    }
                    Figure("Recorded movements", Modifier.weight(1f)) {
                        Text("${state.history.size}", style = AmountStyles.medium)
                    }
                }
                if (wallet.type == WalletType.SAVINGS) {
                    Spacer(Modifier.height(Spacing.md))
                    InfoNote("Savings aren’t counted in Safe to spend, so money you set aside stays set aside.")
                }
            }
        }

        item(key = "actions") {
            Row(Modifier.fillMaxWidth().padding(top = Spacing.md), horizontalArrangement = Arrangement.SpaceBetween) {
                if (liability) {
                    if (transfersEnabled) {
                        ActionTile("Payment", Icons.Outlined.Payments, { onTransfer(null, wallet.id) }, Modifier.weight(1f), primary = true)
                    }
                    ActionTile("Purchase", Icons.Filled.Remove, { onAddTransaction(TransactionType.EXPENSE, wallet.id) }, Modifier.weight(1f))
                    ActionTile("Interest/fee", Icons.Outlined.Percent, { onAddTransaction(TransactionType.EXPENSE, wallet.id) }, Modifier.weight(1f))
                } else {
                    ActionTile("Expense", Icons.Filled.Remove, { onAddTransaction(TransactionType.EXPENSE, wallet.id) }, Modifier.weight(1f), primary = true)
                    ActionTile("Income", Icons.Filled.Add, { onAddTransaction(TransactionType.INCOME, wallet.id) }, Modifier.weight(1f))
                    if (transfersEnabled) {
                        ActionTile("Transfer", Icons.Outlined.SwapHoriz, { onTransfer(wallet.id, null) }, Modifier.weight(1f))
                    }
                }
            }
            if (liability) {
                InfoNote(
                    if (wallet.type == WalletType.LOAN) {
                        "Repayments are transfers from your bank or cash, so they’re not counted as spending again. " +
                            "Record interest and fees as expenses on this loan. Money you borrowed is not income."
                    } else {
                        "Card purchases are spending. Paying the card from another account is a transfer, " +
                            "so the same purchase is never counted twice."
                    },
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
        }

        item(key = "history-header") { SectionHeader("History", Modifier.padding(top = Spacing.lg)) }
        if (state.history.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    title = "Nothing recorded yet",
                    body = "Movements in ${wallet.name} will appear here, and its balance updates as you go.",
                )
            }
        }
        itemsIndexed(state.history, key = { _, t -> "tx-${t.id}" }) { index, transaction ->
            Column {
                TransactionRow(
                    transaction = transaction,
                    category = transaction.categoryId?.let { state.categoriesById[it] },
                    wallet = wallet,
                    onClick = { onEditTransaction(transaction) },
                    trailingCaption = shortDayLabel(transaction.date),
                    transfer = transaction.transferGroup?.let { state.transfers[it] },
                    walletNames = state.walletNames,
                    perspectiveOfWallet = true,
                )
                if (index < state.history.lastIndex) Hairline(inset = Sizes.iconTile + Spacing.md)
            }
        }
        item(key = "note") {
            Text(
                "Balance = ${if (liability) "owed at start" else "opening balance"} ${MoneyFormatter.format(wallet.openingBalance)} " +
                    "adjusted by everything above.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.md),
            )
        }
    }

    editing?.let { draft ->
        WalletEditorSheet(
            initial = draft,
            onSave = walletsViewModel::save,
            onDelete = walletsViewModel::delete,
            onDismiss = { editing = null },
        )
    }
}
