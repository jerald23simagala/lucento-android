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
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.core.designsystem.component.ActionTile
import com.lucento.app.core.designsystem.component.EmptyState
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.LucentoCard
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
    /** Opens a prefilled recurring payment after a debt is saved with a reminder; null when recurring is off. */
    onPlanDebtPayment: ((Long, java.time.LocalDate) -> Unit)? = null,
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
                    "Savings aren’t counted as money you can spend, so money you set aside stays set aside.",
                    modifier = notes,
                )
            }
        }

        // A manually valued asset has no movements: no actions, history or balance formula.
        if (valued) return@LazyColumn

        item(key = "actions") {
            Row(Modifier.fillMaxWidth().padding(top = Spacing.md), horizontalArrangement = Arrangement.SpaceBetween) {
                if (liability) {
                    val loan = wallet.type == WalletType.LOAN
                    if (transfersEnabled) {
                        ActionTile(if (loan) "Repay loan" else "Payment", Icons.Outlined.Payments, { onTransfer(null, wallet.id) }, Modifier.weight(1f), primary = true)
                    }
                    if (loan) {
                        // A loan has no purchases: its balance is corrected with the as-of balance adjustment instead.
                        ActionTile("Adjust loan", Icons.Filled.Tune, { adjusting = true }, Modifier.weight(1f))
                    } else {
                        ActionTile("Purchase", Icons.Filled.Remove, { onAddTransaction(TransactionType.EXPENSE, wallet.id) }, Modifier.weight(1f))
                    }
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

        if (liability && state.history.isNotEmpty()) {
            debtHistory(wallet = wallet, state = state, onOpen = onEditTransaction)
            return@LazyColumn
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
            onPlanPayment = onPlanDebtPayment?.let { plan ->
                { id, date ->
                    editing = null
                    plan(id, date)
                }
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

/**
 * A card's or loan's history, in sections and as changes in what is owed (a payment reads "−", interest "+"; display
 * only — stored amounts and signs are unchanged). A loan splits into Principal (payments, borrowing, adjustments),
 * Interest paid (the interest part of each recorded payment) and Interest & fees (charges that add to the debt); a card
 * into Payments and Charges (purchases, interest, fees). A payment's row shows its principal only: the transfer from
 * the paying wallet was principal + interest, and the interest part is listed under Interest paid ([DebtPaymentLink]).
 */
private fun androidx.compose.foundation.lazy.LazyListScope.debtHistory(
    wallet: com.lucento.app.domain.Wallet,
    state: WalletDetailUiState,
    onOpen: (Transaction) -> Unit,
) {
    val loan = wallet.type == WalletType.LOAN
    val code = wallet.currencyCode
    val interestByPayment = if (loan) com.lucento.app.domain.DebtPaymentLink.interestByPayment(state.history, wallet.id) else emptyMap()
    val paidInterestKeys = interestByPayment.keys.mapTo(HashSet()) { com.lucento.app.domain.DebtPaymentLink.interestKey(it) }
    // What each row shows: a payment leg as its principal; everything else as stored.
    fun shown(t: Transaction): Transaction =
        if (t.type == TransactionType.TRANSFER_IN && t.transferGroup in interestByPayment) {
            t.copy(amount = com.lucento.app.domain.DebtPaymentLink.principalOf(t, interestByPayment))
        } else {
            t
        }
    val sections: List<Triple<String, List<Transaction>, Boolean>> = if (loan) {
        val (charges, principal) = state.history.partition { it.type == TransactionType.EXPENSE }
        val (paid, added) = charges.partition { it.clientUuid in paidInterestKeys }
        listOf(
            // Third value: rows read as changes in what is owed (true) or as money paid (false).
            Triple("Principal", principal.map(::shown), true),
            Triple("Interest paid", paid, false),
            Triple("Interest & fees", added, true),
        )
    } else {
        val (payments, charges) = state.history.partition { it.type.isInflow }
        listOf(Triple("Payments", payments, true), Triple("Charges", charges, true))
    }
    fun owedChange(list: List<Transaction>) = com.lucento.app.core.money.Money.sum(code, list.map { -it.signedAmount })
    val originals = state.history.associateBy { it.id }
    sections.forEach { (title, rows, owed) ->
        if (rows.isEmpty()) return@forEach
        item(key = "section-$title") {
            SectionHeader(title, Modifier.padding(top = Spacing.lg)) {
                Text(
                    MoneyFormatter.formatSigned(if (owed) owedChange(rows) else com.lucento.app.core.money.Money.sum(code, rows.map { it.signedAmount })),
                    style = AmountStyles.small,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        item(key = "card-$title") {
            LucentoCard(Modifier.fillMaxWidth(), contentPadding = 0.dp) {
                rows.forEachIndexed { index, transaction ->
                    TransactionRow(
                        transaction = transaction,
                        category = transaction.categoryId?.let { state.categoriesById[it] },
                        wallet = wallet,
                        onClick = { onOpen(originals[transaction.id] ?: transaction) },
                        trailingCaption = shortDayLabel(transaction.date),
                        transfer = transaction.transferGroup?.let { state.transfers[it] },
                        walletNames = state.walletNames,
                        perspectiveOfWallet = true,
                        owedPerspective = owed,
                        dense = true,
                    )
                    if (index < rows.lastIndex) Hairline(strong = true)
                }
            }
        }
    }
    val paidInterest = com.lucento.app.core.money.Money.sum(code, interestByPayment.values.toList())
    item(key = "note") {
        val start = MoneyFormatter.format(wallet.openingBalance)
        val now = MoneyFormatter.format(wallet.balance)
        val text = if (loan) {
            val repaid = wallet.openingBalance - wallet.balance
            buildString {
                append("Owed = $start at start ")
                append(if (repaid.isNegative) "+ ${MoneyFormatter.format(-repaid)} added" else "− ${MoneyFormatter.format(repaid)} principal repaid")
                append(" = $now.")
                if (!paidInterest.isZero) {
                    append(" Your payments also covered ${MoneyFormatter.format(paidInterest)} of interest, paid on top of the principal, so it doesn’t change what you owe.")
                }
            }
        } else {
            "Owed = $start at start + charges ${MoneyFormatter.format(owedChange(sections[1].second))} − payments " +
                "${MoneyFormatter.format(-owedChange(sections[0].second))} = $now."
        }
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.md),
        )
    }
}
