package com.lucento.app.feature.plan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import com.lucento.app.core.designsystem.component.EmptyState
import com.lucento.app.core.designsystem.component.Figure
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoCard
import com.lucento.app.core.designsystem.component.LucentoMascot
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.LucentoTopBar
import com.lucento.app.core.designsystem.component.MascotMood
import com.lucento.app.core.designsystem.component.MoneyText
import com.lucento.app.core.designsystem.component.SectionHeader
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Sizes
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.Money
import com.lucento.app.domain.WalletType
import com.lucento.app.feature.wallets.WalletDraft
import com.lucento.app.feature.wallets.WalletEditorSheet
import com.lucento.app.feature.wallets.WalletRow
import com.lucento.app.feature.wallets.WalletsViewModel

/**
 * Debts & loans are liability accounts (credit cards, loans). Each one opens its account screen, where
 * payments are recorded as transfers and interest or fees as expenses.
 */
@Composable
fun DebtsScreen(
    onBack: () -> Unit,
    onOpenWallet: (Long) -> Unit,
    /** Opens a prefilled recurring payment after a debt is saved with a payment reminder; null when recurring is off. */
    onPlanDebtPayment: ((Long, java.time.LocalDate) -> Unit)? = null,
    viewModel: PlanViewModel = viewModel(factory = PlanViewModel.Factory),
    walletsViewModel: WalletsViewModel = viewModel(factory = WalletsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf<WalletDraft?>(null) }
    val debts = state.wallets.filter { it.type.isLiability }.sortedBy { it.name }.sortedBy { it.type.ordinal }
    val totals = debts.groupBy { it.currencyCode }
        .map { (code, list) -> Money.sum(code, list.map { it.balance }) }
        .sortedWith(compareBy<Money> { it.currencyCode != state.primaryCurrency }.thenBy { it.currencyCode })
    val newDebt = { WalletDraft(type = WalletType.LOAN, currencyCode = state.primaryCurrency) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.fabClearance),
        ) {
            item(key = "bar") { LucentoTopBar(title = "Debts & loans", onBack = onBack) }
            if (!state.loading && debts.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = "No debts tracked",
                        body = "Add a credit card or a loan with what you owe today. Payments then lower the balance without " +
                            "being counted as spending twice.",
                        illustration = { LucentoMascot(size = 96.dp, mood = MascotMood.Curious) },
                        action = { LucentoPrimaryButton(text = "Add a debt", onClick = { adding = newDebt() }) },
                    )
                }
            }
            if (totals.isNotEmpty()) {
                item(key = "totals") {
                    LucentoCard(Modifier.fillMaxWidth(), contentPadding = Spacing.xl) {
                        Figure("Total owed · ${totals.first().currencyCode}") {
                            MoneyText(
                                money = totals.first(),
                                style = AmountStyles.hero,
                                color = if (totals.first().isZero) MaterialTheme.colorScheme.onSurface else LucentoTheme.extendedColors.negative,
                            )
                        }
                        totals.drop(1).forEach { other ->
                            Spacer(Modifier.height(Spacing.sm))
                            Figure("Also owed · ${other.currencyCode}") { MoneyText(money = other, style = AmountStyles.medium, fractionScale = 1f) }
                        }
                    }
                }
                item(key = "header") { SectionHeader("Accounts", Modifier.padding(top = Spacing.md)) }
            }
            if (debts.isNotEmpty()) {
                item(key = "accounts") {
                    LucentoCard(Modifier.fillMaxWidth(), contentPadding = 0.dp) {
                        debts.forEachIndexed { index, wallet ->
                            WalletRow(wallet = wallet, onClick = { onOpenWallet(wallet.id) }, dense = true)
                            if (index < debts.lastIndex) Hairline(strong = true)
                        }
                    }
                }
            }
            if (debts.isNotEmpty()) {
                item(key = "rules") {
                    InfoNote(
                        "How Lucento counts debt: borrowed money is not income; repaying principal is a transfer, not new spending; " +
                            "interest and fees are expenses. A recurring debt payment records the split you enter — interest under " +
                            "“Debt interest”, the rest against the debt. Lucento never guesses the split for you.",
                        modifier = Modifier.padding(top = Spacing.lg),
                    )
                }
            }
        }
        if (debts.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = { adding = newDebt() },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add debt") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.gutter),
            )
        }
    }

    adding?.let { draft ->
        WalletEditorSheet(
            initial = draft,
            onSave = walletsViewModel::save,
            onDelete = null,
            onDismiss = { adding = null },
            onPlanPayment = onPlanDebtPayment?.let { plan ->
                { id, date ->
                    adding = null
                    plan(id, date)
                }
            },
        )
    }
}
