package com.lucento.app.feature.wallets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.WarningAmber
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
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoTopBar
import com.lucento.app.core.designsystem.component.MoneyText
import com.lucento.app.core.designsystem.component.SectionHeader
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Sizes
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.WalletType
import com.lucento.app.domain.balanceFlag
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
    /** After a balance adjustment is recorded (e.g. to confirm it in a snackbar). */
    onAdjusted: () -> Unit = {},
    viewModel: WalletDetailViewModel = viewModel(key = "wallet-$walletId", factory = WalletDetailViewModel.factory(walletId)),
    walletsViewModel: WalletsViewModel = viewModel(factory = WalletsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val walletsState by walletsViewModel.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<WalletDraft?>(null) }
    var adjusting by remember { mutableStateOf(false) }
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
                // The wallet's name and type live on the card itself.
                title = if (wallet == null) "Account" else "",
                onBack = onBack,
                actions = {
                    if (wallet != null) {
                        IconButton(onClick = { editing = wallet.toDraft(isDefault = wallet.id == walletsState.defaultWalletId) }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit account")
                        }
                    }
                },
            )
        }
        if (wallet == null) return@LazyColumn
        val liability = wallet.type.isLiability
        val valued = wallet.type.isValuedManually

        item(key = "balance") {
            WalletCard(wallet = wallet, movements = state.history.size, modifier = Modifier.padding(top = Spacing.xs))
            val ext = LucentoTheme.extendedColors
            val notes = Modifier.padding(top = Spacing.sm)
            when {
                valued -> InfoNote(
                    "A value you enter yourself — no money moves through it. Use the edit button when it changes. " +
                        "It counts towards your net worth as an asset.",
                    modifier = notes,
                )
                wallet.balanceFlag != null -> InfoNote(
                    wallet.balanceFlag!!.explanation() + " Compare it with the real account and add what’s missing, or adjust the balance.",
                    modifier = notes,
                    icon = Icons.Outlined.WarningAmber,
                    container = ext.warningContainer,
                    content = ext.warning,
                    actionLabel = "Adjust balance",
                    onAction = { adjusting = true },
                )
                wallet.type == WalletType.SAVINGS -> InfoNote(
                    "Savings aren’t counted in Safe to spend, so money you set aside stays set aside.",
                    modifier = notes,
                )
            }
        }

        // A manually valued asset has no movements: no actions, history or balance formula.
        if (valued) return@LazyColumn

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
            onAdjustBalance = {
                editing = null
                adjusting = true
            },
        )
    }

    if (adjusting && wallet != null && !wallet.type.isValuedManually) {
        AdjustBalanceSheet(
            wallet = wallet,
            history = state.history,
            onAdjust = viewModel::adjust,
            onAdjusted = {
                adjusting = false
                onAdjusted()
            },
            onDismiss = { adjusting = false },
        )
    }
}
