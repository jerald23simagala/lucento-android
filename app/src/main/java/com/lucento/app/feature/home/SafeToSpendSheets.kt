package com.lucento.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.component.AmountText
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.LucentoBottomSheet
import com.lucento.app.core.designsystem.component.LucentoSecondaryButton
import com.lucento.app.core.designsystem.component.SheetHeader
import com.lucento.app.core.designsystem.component.WalletIdentityTile
import com.lucento.app.core.designsystem.component.amountFormat
import com.lucento.app.core.designsystem.component.label
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.OverlineStyle
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.Money
import com.lucento.app.domain.MoneyOnHand
import com.lucento.app.domain.SafeToSpend
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/*
 * Tester round (2026-11-17): Safe to spend is the lower of what the budget allows and what the spending money allows.
 * The (i) beside it opens [SafeToSpendSheet], which shows both, line by line, every figure traceable to a wallet or a
 * schedule; "Choose spending wallets" opens [SpendingWalletsSheet].
 */

/** Both figures behind Safe to spend for [safe]'s month, and which one is shown. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SafeToSpendSheet(safe: SafeToSpend, onChooseWallets: (() -> Unit)?, onDismiss: () -> Unit) {
    val month = safe.month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault())
    val money = safe.moneyOnHand
    val byMoney = safe.limitedByMoney
    LucentoBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            SheetHeader(
                title = "How Safe to spend is worked out",
                subtitle = if (safe.basis == SafeToSpend.Basis.BUDGET && money != null) {
                    "Two ways to look at $month. Lucento shows the smaller one."
                } else if (safe.basis == SafeToSpend.Basis.BUDGET) {
                    "What your budget allows for the rest of $month."
                } else {
                    "What your spending money allows for the rest of $month. An estimate: you have no budget this month."
                },
                icon = Icons.Outlined.Info,
            )
            if (safe.basis == SafeToSpend.Basis.BUDGET) {
                val budget = safe.budgetLimit
                val allows = safe.budgetAllows
                if (budget != null && allows != null) {
                    SectionTitle("Your budget allows", used = !byMoney)
                    ExplainLine("Budget", budget)
                    ExplainLine("Spent so far", -safe.spentThisMonth)
                    ExplainLine("Bills still due", -safe.committed)
                    TotalLine(allows, used = !byMoney)
                }
            }
            if (money != null) {
                SectionTitle("Your money allows", used = byMoney || safe.basis == SafeToSpend.Basis.BALANCES)
                money.sources.forEach { source ->
                    ExplainLine(if (source.credit) "${source.name} · credit you count" else source.name, source.amount)
                }
                money.deductions.forEach { deduction -> ExplainLine(deductionLabel(deduction), -deduction.amount) }
                TotalLine(money.allows, used = byMoney || safe.basis == SafeToSpend.Basis.BALANCES)
            } else {
                Text(
                    "None of your wallets in ${safe.currencyCode} counts as spending money, so only your budget is used.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
            Spacer(Modifier.size(Spacing.xs))
            Text(
                "Savings, investments, property and money you’re owed aren’t counted unless you say so. Income you expect " +
                    "isn’t added until it arrives. Below zero, Safe to spend shows 0.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (onChooseWallets != null) {
                Spacer(Modifier.size(Spacing.xs))
                LucentoSecondaryButton(text = "Choose spending wallets", onClick = onChooseWallets, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.size(Spacing.lg))
        }
    }
}

/**
 * Which wallets count as spending money, each with a switch (the same choice as the wallet editor's). A card's part of
 * its credit is set in the card's own editor, where its limit is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpendingWalletsSheet(wallets: List<Wallet>, onToggle: (Long, Boolean) -> Unit, onDismiss: () -> Unit) {
    val fmt = amountFormat()
    val accounts = wallets.filter { it.type.canBeSpendingMoney && it.type != WalletType.CREDIT_CARD }
    val cards = wallets.filter { it.type == WalletType.CREDIT_CARD }
    LucentoBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter)
                .navigationBarsPadding(),
        ) {
            SheetHeader(
                title = "Spending money",
                subtitle = "Choose which wallets Safe to spend counts.",
                icon = Icons.Outlined.AccountBalanceWallet,
            )
            accounts.forEach { wallet ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WalletIdentityTile(wallet, size = 36.dp)
                    Spacer(Modifier.width(Spacing.md))
                    Column(Modifier.weight(1f)) {
                        Text(wallet.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${wallet.kind?.label ?: wallet.type.label} · ${fmt.format(wallet.balance)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Switch(
                        checked = wallet.isSpendingMoney,
                        onCheckedChange = { onToggle(wallet.id, it) },
                        modifier = Modifier.semantics { contentDescription = "Count ${wallet.name} as spending money" },
                    )
                }
            }
            if (accounts.isEmpty()) {
                Text(
                    "No cash, bank, e-wallet, savings or investment accounts yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (cards.isNotEmpty()) {
                Spacer(Modifier.size(Spacing.sm))
                Text(
                    "To count part of a card’s credit, open the card in Wallets, tap Edit and say how much counts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(Spacing.lg))
        }
    }
}

@Composable
private fun SectionTitle(title: String, used: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = Spacing.md)
            .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title.uppercase(), style = OverlineStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        if (used) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = LucentoTheme.extendedColors.positive, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(Spacing.xxs))
            Text("Shown", style = MaterialTheme.typography.labelMedium, color = LucentoTheme.extendedColors.positive)
        }
    }
    Hairline(Modifier.padding(top = Spacing.xxs))
}

@Composable
private fun ExplainLine(label: String, money: Money) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 28.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // Never cut off: at large text sizes a line wraps, so its date and name stay readable.
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Spacing.sm))
        AmountText(money, style = AmountStyles.row)
    }
}

@Composable
private fun TotalLine(money: Money, used: Boolean) {
    val ext = LucentoTheme.extendedColors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = Spacing.xxs)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Allows", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        AmountText(
            money,
            style = AmountStyles.medium,
            color = when {
                money.isNegative -> ext.negative
                used -> ext.positive
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/** "Bills (3)", "Car loan · due Nov 25", "Visa payment · due Nov 20", "MP2 contribution · Nov 30". */
private fun deductionLabel(d: MoneyOnHand.Deduction): String {
    val date = d.date?.let { shortDate(it) }
    return when (d.kind) {
        MoneyOnHand.Kind.BILLS -> if (d.count == 1) "1 bill paid from them" else "${d.count} bills paid from them"
        MoneyOnHand.Kind.DEBT_PAYMENT, MoneyOnHand.Kind.CARD_PAYMENT ->
            listOfNotNull(d.name, date?.let { "due $it" }, d.count.takeIf { it > 1 }?.let { "× $it" }).joinToString(" · ")
        MoneyOnHand.Kind.TRANSFER ->
            listOfNotNull("Transfer: ${d.name}", date, d.count.takeIf { it > 1 }?.let { "× $it" }).joinToString(" · ")
    }
}

private fun shortDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()))
        .replace(Regex(",? \\d{4}$"), "")
