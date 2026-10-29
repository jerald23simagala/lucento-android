package com.lucento.app.feature.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.core.designsystem.component.AmountText
import com.lucento.app.core.designsystem.component.CategoryTile
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoCard
import com.lucento.app.core.designsystem.component.LucentoTopBar
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Radii
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.designsystem.theme.TonePair
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType

/**
 * Read-only view of one transaction, opened by tapping a history row. Nothing here changes data: the Edit pill in the
 * top bar opens the editor (or the transfer sheet). If the transaction disappears (deleted from the editor), the page
 * steps back on its own.
 */
@Composable
fun TransactionDetailScreen(
    transactionId: Long,
    onBack: () -> Unit,
    onEdit: (Transaction) -> Unit,
    viewModel: TransactionDetailViewModel = viewModel(
        key = "transaction-$transactionId",
        factory = TransactionDetailViewModel.factory(transactionId),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val transaction = state.transaction
    LaunchedEffect(state.loading, transaction == null) {
        if (!state.loading && transaction == null) onBack()
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LucentoTopBar(
            title = when {
                transaction?.isTransfer == true -> "Transfer"
                transaction?.isAdjustment == true -> "Balance adjustment"
                else -> "Transaction"
            },
            onBack = onBack,
            modifier = Modifier.padding(horizontal = Spacing.gutter),
            actions = {
                // An adjustment isn't income or spending, so it has no editor: delete it from the list and adjust again.
                if (transaction != null && !transaction.isAdjustment) EditPill(onClick = { onEdit(transaction) })
            },
        )
        if (transaction == null) return@Column

        val ext = LucentoTheme.extendedColors
        val isTransfer = transaction.isTransfer
        val isIncome = transaction.type == TransactionType.INCOME
        val isAdjustment = transaction.isAdjustment
        val tone = when {
            isTransfer -> TonePair(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary)
            isAdjustment -> TonePair(ext.warningContainer, ext.warning)
            isIncome -> TonePair(ext.positiveContainer, ext.positive)
            else -> TonePair(ext.negativeContainer, ext.negative)
        }
        val categoryName = state.category?.name ?: "Uncategorized"
        val transfer = state.transfer
        val from = transfer?.let { state.walletNames[it.fromWalletId] } ?: "Account"
        val to = transfer?.let { state.walletNames[it.toWalletId] } ?: "Account"
        val title = when {
            transaction.note.isNotBlank() -> transaction.note
            isTransfer -> "Transfer"
            isAdjustment -> "Balance adjustment"
            else -> categoryName
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter)
                .padding(top = Spacing.xs, bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // ---- What happened
            LucentoCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    CategoryTile(category = state.category, type = transaction.type, size = 56.dp)
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    AmountText(
                        money = if (isTransfer) transaction.amount else transaction.signedAmount,
                        style = AmountStyles.hero,
                        signed = !isTransfer,
                        showPlus = true,
                        color = tone.content,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        when {
                            isTransfer -> "Transfer"
                            isAdjustment -> "Balance adjustment"
                            isIncome -> "Income"
                            else -> "Expense"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = tone.content,
                        modifier = Modifier
                            .background(tone.container, RoundedCornerShape(Radii.full))
                            .padding(horizontal = Spacing.md, vertical = Spacing.xxs),
                    )
                }
            }

            // ---- Details
            LucentoCard(modifier = Modifier.fillMaxWidth(), contentPadding = Spacing.lg) {
                if (isTransfer) {
                    DetailRow("From", from)
                    Hairline(modifier = Modifier.padding(vertical = Spacing.sm))
                    DetailRow("To", to)
                } else if (isAdjustment) {
                    DetailRow("Wallet", state.wallet?.name ?: "—")
                } else {
                    DetailRow("Category", categoryName)
                    Hairline(modifier = Modifier.padding(vertical = Spacing.sm))
                    DetailRow(if (isIncome) "Into wallet" else "From wallet", state.wallet?.name ?: "—")
                }
                Hairline(modifier = Modifier.padding(vertical = Spacing.sm))
                DetailRow("Date", dayLabel(transaction.date))
                Hairline(modifier = Modifier.padding(vertical = Spacing.sm))
                DetailRow("Currency", transaction.currencyCode)
                Hairline(modifier = Modifier.padding(vertical = Spacing.sm))
                DetailRow("Note", transaction.note.ifBlank { "No note" }, muted = transaction.note.isBlank())
            }
            if (isAdjustment) {
                InfoNote(
                    "Recorded when this wallet's balance was set by hand: the gap between what Lucento tracked and the " +
                        "actual balance. It isn't counted as income or spending. A large one usually means transactions " +
                        "weren't recorded — delete it and add them instead if you find them.",
                    icon = Icons.Outlined.WarningAmber,
                    container = ext.warningContainer,
                    content = ext.warning,
                )
            }
        }
    }
}

/** A label on the left, its value on the right. */
@Composable
private fun DetailRow(label: String, value: String, muted: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 32.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** The page's one action: a brand-tinted pill matching the Back pill opposite it. */
@Composable
private fun EditPill(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(Radii.full),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .semantics {
                contentDescription = "Edit"
                role = Role.Button
            },
    ) {
        Row(
            Modifier.heightIn(min = 40.dp).padding(start = Spacing.md, end = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.sm))
            Text("Edit", style = MaterialTheme.typography.labelLarge)
        }
    }
}
