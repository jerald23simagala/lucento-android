package com.lucento.app.feature.wallets

import com.lucento.app.core.designsystem.component.AmountFormat
import com.lucento.app.core.designsystem.component.amountFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Edit
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
import com.lucento.app.core.designsystem.component.EmptyState
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.LucentoCard
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoTopBar
import com.lucento.app.core.designsystem.component.MoneyText
import com.lucento.app.core.designsystem.component.SectionHeader
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.WalletType
import com.lucento.app.domain.balanceFlag
import com.lucento.app.feature.transactions.TransactionRow
import com.lucento.app.feature.transactions.shortDayLabel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.lucento.app.core.designsystem.component.IconTile
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.component.rememberHaptics

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
    /** A card with an amount due: its payment transfer, filled in (the same as Home's reminder). */
    onPayCard: (com.lucento.app.domain.CardDue) -> Unit = { onTransfer(it.payFromWalletId, it.wallet.id) },
    viewModel: WalletDetailViewModel = viewModel(key = "wallet-$walletId", factory = WalletDetailViewModel.factory(walletId)),
    walletsViewModel: WalletsViewModel = viewModel(factory = WalletsViewModel.Factory),
) {
    val fmt = amountFormat()
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
                // The wallet's name and type live on the card itself; the bar says what the page is.
                title = "View wallet",
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
            WalletCard(
                wallet = wallet,
                movements = state.history.size,
                modifier = Modifier.padding(top = Spacing.xs),
                cycle = state.cardCycle,
                loan = state.loanProgress,
                today = state.today,
            )
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
            // Spending and income are added with the dock's +; an account's own page moves money (left) and reconciles
            // its balance with the real one (right, with how that works), so the row is never half empty.
            val loan = wallet.type == WalletType.LOAN
            val receivable = wallet.type == WalletType.RECEIVABLE
            Row(
                Modifier.fillMaxWidth().padding(top = Spacing.md).height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (transfersEnabled) {
                    when {
                        loan -> MoveMoneyCard("Repay loan", "From your bank or cash", Icons.Outlined.Payments, { onTransfer(state.payFromWalletId, wallet.id) }, Modifier.weight(0.38f).fillMaxHeight())
                        // An instalment they paid back: from this receivable into the user's account (owner round 10).
                        receivable -> MoveMoneyCard("Record payment", "Money they paid back", Icons.Outlined.Payments, { onTransfer(wallet.id, state.receiveIntoWalletId) }, Modifier.weight(0.38f).fillMaxHeight())
                        liability -> MoveMoneyCard(
                            "Payment",
                            "Pay this card",
                            Icons.Outlined.Payments,
                            { state.cardDue?.let(onPayCard) ?: onTransfer(state.payFromWalletId, wallet.id) },
                            Modifier.weight(0.38f).fillMaxHeight(),
                        )
                        else -> MoveMoneyCard("Transfer", "Between your wallets", Icons.Outlined.SwapHoriz, { onTransfer(wallet.id, null) }, Modifier.weight(0.38f).fillMaxHeight())
                    }
                }
                ReconcileCard(
                    title = when {
                        loan -> "Adjust loan"
                        receivable -> "Adjust amount"
                        else -> "Adjust balance"
                    },
                    body = when {
                        loan -> "Lender’s statement says otherwise? Enter what you owe on a date — the gap is recorded as an adjustment, not borrowing or spending."
                        receivable -> "Agreed on a different amount? Enter what they still owe you — the gap is recorded as an adjustment, not income or spending."
                        liability -> "Card statement says otherwise? Enter what you owe — the gap is recorded as an adjustment, not a purchase or a payment."
                        else -> "Bank says otherwise? Enter the real balance — the gap is recorded as an adjustment, not income or spending."
                    },
                    onClick = { adjusting = true },
                    modifier = Modifier.weight(0.62f).fillMaxHeight(),
                )
            }
            if (loan) {
                // A loan has no purchases; interest and fees are expenses recorded on it.
                LucentoTextButton(
                    text = "Record interest or a fee",
                    onClick = { onAddTransaction(TransactionType.EXPENSE, wallet.id) },
                    modifier = Modifier.padding(top = Spacing.xxs),
                )
            }
            if (receivable) {
                InfoNote(
                    "What they pay back is a transfer from this receivable into your account, so it isn’t counted as income " +
                        "again. Interest they pay is income. Lending the money was a transfer out of your account, not spending.",
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
            if (liability) {
                val cycle = state.cardCycle
                InfoNote(
                    when {
                        wallet.type == WalletType.LOAN ->
                            "Repayments are transfers from your bank or cash, so they’re not counted as spending again. " +
                                "Record interest and fees as expenses on this loan. Money you borrowed is not income."
                        cycle != null ->
                            "Amount due ${fmt.format(cycle.amountDue)} by ${shortDate(cycle.dueDate)}: you owed " +
                                "${fmt.format(cycle.statementBalance)} on the ${shortDate(cycle.statementDate)} statement, less payments since. " +
                                "It’s a reminder, not a bill in Safe to spend — the card’s purchases were already counted as spending."
                        else ->
                            "Add card purchases with + — they’re spending. Paying the card from another account is a transfer, " +
                                "so the same purchase is never counted twice."
                    },
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
        }

        if (liability && state.history.isNotEmpty()) {
            debtHistory(wallet = wallet, state = state, onOpen = onEditTransaction, fmt = fmt)
            return@LazyColumn
        }

        if (state.history.isEmpty()) {
            item(key = "history-header") { SectionHeader("History", Modifier.padding(top = Spacing.lg)) }
            item(key = "empty") {
                EmptyState(
                    title = "Nothing recorded yet",
                    body = "Movements in ${wallet.name} will appear here, and its balance updates as you go.",
                )
            }
        }
        // History by month, each on one card of compact rows (as on Transactions), newest first.
        state.history.groupBy { java.time.YearMonth.from(it.date) }.forEach { (month, rows) ->
            item(key = "month-head-$month") {
                SectionHeader(monthTitle(month, state.today), Modifier.padding(top = Spacing.lg)) {
                    Text(
                        if (rows.size == 1) "1 movement" else "${rows.size} movements",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            item(key = "month-$month") {
                LucentoCard(Modifier.fillMaxWidth(), contentPadding = 0.dp) {
                    rows.forEachIndexed { index, transaction ->
                        TransactionRow(
                            transaction = transaction,
                            category = transaction.categoryId?.let { state.categoriesById[it] },
                            wallet = wallet,
                            onClick = { onEditTransaction(transaction) },
                            trailingCaption = shortDayLabel(transaction.date),
                            transfer = transaction.transferGroup?.let { state.transfers[it] },
                            walletNames = state.walletNames,
                            perspectiveOfWallet = true,
                            dense = true,
                        )
                        // Inset to the row text (12dp padding + 36dp tile + 10dp gap), not under the tile.
                        if (index < rows.lastIndex) Hairline(inset = 58.dp)
                    }
                }
            }
        }
        item(key = "note") {
            Text(
                "Balance = ${when {
                    liability -> "owed at start"
                    wallet.type == WalletType.RECEIVABLE -> "owed to you at start"
                    else -> "opening balance"
                }} ${fmt.format(wallet.openingBalance)} " +
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

/** "November", or "November 2025" outside the current year. */
private fun monthTitle(month: java.time.YearMonth, today: java.time.LocalDate): String {
    val name = month.month.getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, java.util.Locale.getDefault())
    return if (month.year == today.year) name else "$name ${month.year}"
}

/** The page's money-moving action: a filled brand circle, the verb and what it does, as one compact card. */
@Composable
private fun MoveMoneyCard(label: String, caption: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    val colors = MaterialTheme.colorScheme
    LucentoCard(
        modifier = modifier,
        onClick = {
            haptics.tick()
            onClick()
        },
        contentPadding = Spacing.md,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(colors.primary), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.weight(1f).heightIn(min = Spacing.sm))
        Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(caption, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 2)
    }
}

/**
 * Reconciling, explained where it's done: a tonal tile, the action, one line on how an adjustment works, and the call to
 * act. The whole card is one touch target.
 */
@Composable
private fun ReconcileCard(title: String, body: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    val colors = MaterialTheme.colorScheme
    LucentoCard(
        modifier = modifier,
        onClick = {
            haptics.tick()
            onClick()
        },
        contentPadding = Spacing.md,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Filled.Tune, container = colors.primaryContainer, content = colors.onPrimaryContainer, size = 32.dp, shape = RoundedCornerShape(10.dp))
            Spacer(Modifier.width(Spacing.sm))
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.onSurfaceVariant)
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(body, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

/** "Nov 5": a day in the user's locale, for billing dates. */
internal fun shortDate(date: java.time.LocalDate): String =
    date.format(java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.getDefault()))

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
    fmt: AmountFormat,
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
                    fmt.signed(if (owed) owedChange(rows) else com.lucento.app.core.money.Money.sum(code, rows.map { it.signedAmount })),
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
        val start = fmt.format(wallet.openingBalance)
        val now = fmt.format(wallet.balance)
        val text = if (loan) {
            val repaid = wallet.openingBalance - wallet.balance
            buildString {
                append("Owed = $start at start ")
                append(if (repaid.isNegative) "+ ${fmt.format(-repaid)} added" else "− ${fmt.format(repaid)} principal repaid")
                append(" = $now.")
                if (!paidInterest.isZero) {
                    append(" Your payments also covered ${fmt.format(paidInterest)} of interest, paid on top of the principal, so it doesn’t change what you owe.")
                }
            }
        } else {
            "Owed = $start at start + charges ${fmt.format(owedChange(sections[1].second))} − payments " +
                "${fmt.format(-owedChange(sections[0].second))} = $now."
        }
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.md),
        )
    }
}
