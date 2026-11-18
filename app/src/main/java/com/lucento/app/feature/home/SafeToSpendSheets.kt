package com.lucento.app.feature.home

import androidx.compose.foundation.clickable
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

/**
 * Both figures behind Safe to spend for [safe]'s month, and which one is shown. [onReviewDue] opens Scheduled, where what
 * is due is marked paid or skipped; [onChooseWallets] is null on a Partner's phone (the Owner chooses).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SafeToSpendSheet(
    safe: SafeToSpend,
    onChooseWallets: (() -> Unit)?,
    onDismiss: () -> Unit,
    onReviewDue: (() -> Unit)? = null,
) {
    val month = safe.month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault())
    val money = safe.moneyOnHand
    val byMoney = safe.limitedByMoney
    // With a budget, the spending money takes part once money has been recorded in a spending wallet (QA, 2026-11-17).
    val moneyCounts = money != null && (money.recorded || safe.basis == SafeToSpend.Basis.BALANCES)
    val today = safe.month.atEndOfMonth().minusDays((safe.daysLeft - 1).toLong())
    val fmt = amountFormat()
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
                subtitle = if (safe.basis == SafeToSpend.Basis.BUDGET && moneyCounts) {
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
                SectionTitle("Your spending money allows", used = byMoney || safe.basis == SafeToSpend.Basis.BALANCES)
                money.sources.forEach { source -> ExplainLine(sourceLabel(source, fmt::format), source.amount) }
                money.deductions.forEach { deduction -> ExplainLine(deductionLabel(deduction, today), -deduction.amount) }
                TotalLine(money.allows, used = byMoney || safe.basis == SafeToSpend.Basis.BALANCES)
                if (!moneyCounts) {
                    Text(
                        "Not counted yet: no money has been recorded in these wallets — no starting balance, income or " +
                            "transfer in — so Safe to spend follows your budget. Give a wallet its real balance with " +
                            "Adjust balance to count it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xxs),
                    )
                }
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
                "Savings and investments count only when you switch them on. Property, loans and money you’re owed never " +
                    "count, and income you expect isn’t added until it arrives. Below zero, Safe to spend shows 0.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // What's due comes from schedules (a card's own payment is on its page): mark paid or skip them there.
            if (onReviewDue != null && money?.deductions?.any { it.kind != MoneyOnHand.Kind.CARD_PAYMENT } == true) {
                com.lucento.app.core.designsystem.component.LucentoTextButton(
                    text = "Review what’s due in Scheduled",
                    onClick = onReviewDue,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (onChooseWallets != null) {
                Spacer(Modifier.size(Spacing.xs))
                LucentoSecondaryButton(text = "Choose spending wallets", onClick = onChooseWallets, modifier = Modifier.fillMaxWidth())
            } else {
                // A Partner in a Shared Space: the Owner chooses (nothing shows outside a Shared Space).
                com.lucento.app.feature.sharedspace.OwnerManagesNote("wallets", Modifier.padding(top = Spacing.xs))
            }
            Spacer(Modifier.size(Spacing.lg))
        }
    }
}

/**
 * Which wallets count as spending money, each with a switch (the same choice as the wallet editor's). Cards follow, each
 * opening its own editor through [onEditCard], where its limit and the credit it counts are set.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpendingWalletsSheet(
    wallets: List<Wallet>,
    onToggle: (Long, Boolean) -> Unit,
    onDismiss: () -> Unit,
    onEditCard: ((Wallet) -> Unit)? = null,
) {
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
                    // Both lines wrap at large text sizes: the balance is what the choice is made on (design review).
                    Column(Modifier.weight(1f)) {
                        Text(wallet.name, style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${wallet.kind?.labelFor(wallet.currencyCode) ?: wallet.type.label} · ${fmt.format(wallet.balance)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            if (cards.isNotEmpty() && onEditCard != null) {
                Text(
                    "CREDIT CARDS",
                    style = OverlineStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = Spacing.md, bottom = Spacing.xxs)
                        .semantics { heading() },
                )
                cards.forEach { card -> CardCreditRow(card, fmt::format) { onEditCard(card) } }
                Text(
                    "Credit is borrowed money: count some only if you pay the card in full every month.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            Spacer(Modifier.size(Spacing.lg))
        }
    }
}

/** A card in [SpendingWalletsSheet]: what of its credit counts, and the way to change it in the card's editor. */
@Composable
private fun CardCreditRow(card: Wallet, format: (Money) -> String, onClick: () -> Unit) {
    val limit = card.debt?.creditLimit
    val chosen = card.spendableCredit?.takeIf { card.isSpendingMoney && it.amountMinor > 0L }
    val action = if (chosen != null) "Change" else "Set amount"
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = "$action the credit ${card.name} counts", onClick = onClick)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WalletIdentityTile(card, size = 36.dp)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(card.name, style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    limit == null -> "Add its credit limit to count some of it"
                    chosen != null -> "Counts up to ${format(chosen)} of its credit"
                    else -> "Its credit isn’t counted"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        Text(action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

/** A source's line: a card shows what it counts and what this month has used of it, so the sum can be checked. */
private fun sourceLabel(source: MoneyOnHand.Source, format: (Money) -> String): String {
    if (!source.credit) return source.name
    val chosen = source.chosen
    val used = source.used
    return if (chosen != null && used != null && used.amountMinor > 0L) {
        "${source.name} · ${format(chosen)} of credit you count, less ${format(used)} used this month"
    } else {
        "${source.name} · credit you count"
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
/** A deduction's line; one first due before [today] says it is overdue, and repeats say how many payments. */
private fun deductionLabel(d: MoneyOnHand.Deduction, today: LocalDate): String {
    val date = d.date?.let { if (it.isBefore(today)) "overdue since ${shortDate(it)}" else "due ${shortDate(it)}" }
    val times = d.count.takeIf { it > 1 }?.let { "$it payments" }
    return when (d.kind) {
        MoneyOnHand.Kind.BILLS -> if (d.count == 1) "1 bill payment from them" else "${d.count} bill payments from them"
        MoneyOnHand.Kind.DEBT_PAYMENT, MoneyOnHand.Kind.CARD_PAYMENT -> listOfNotNull(d.name, times, date).joinToString(" · ")
        MoneyOnHand.Kind.TRANSFER -> listOfNotNull("Transfer: ${d.name}", times, date).joinToString(" · ")
    }
}

private fun shortDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()))
        .replace(Regex(",? \\d{4}$"), "")
