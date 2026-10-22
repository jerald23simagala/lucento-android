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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.core.designsystem.component.AmountText
import com.lucento.app.core.designsystem.component.CategoryTile
import com.lucento.app.core.designsystem.component.Figure
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.LucentoMark
import com.lucento.app.core.designsystem.component.LucentoSecondaryButton
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.component.MoneyText
import com.lucento.app.core.designsystem.component.MonthlyBarChart
import com.lucento.app.core.designsystem.component.QuickActionButton
import com.lucento.app.core.designsystem.component.SectionHeader
import com.lucento.app.core.designsystem.component.ShareBar
import com.lucento.app.core.designsystem.component.StatementPanel
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.OverlineStyle
import com.lucento.app.core.designsystem.theme.Sizes
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.Money
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.domain.Insights
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import com.lucento.app.feature.transactions.TransactionRow
import com.lucento.app.feature.transactions.shortDayLabel
import com.lucento.app.feature.wallets.WalletDraft
import com.lucento.app.feature.wallets.WalletEditorSheet
import com.lucento.app.feature.wallets.WalletsViewModel
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenWallets: () -> Unit,
    onOpenTransactions: () -> Unit,
    onAddTransaction: (TransactionType) -> Unit,
    onEditTransaction: (Transaction) -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
    walletsViewModel: WalletsViewModel = viewModel(factory = WalletsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editingWallet by remember { mutableStateOf<WalletDraft?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter),
    ) {
        BrandBar(onOpenSettings = onOpenSettings)

        AnimatedContent(
            targetState = when {
                state.loading -> HomeContent.Loading
                !state.hasWallets -> HomeContent.Welcome
                else -> HomeContent.Overview
            },
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
            label = "home-content",
        ) { content ->
            when (content) {
                HomeContent.Loading -> Spacer(Modifier.height(1.dp))
                HomeContent.Welcome -> WelcomePanel(
                    onAddWallet = { editingWallet = WalletDraft(currencyCode = state.primaryCurrency) },
                )
                HomeContent.Overview -> Overview(
                    state = state,
                    onOpenWallets = onOpenWallets,
                    onOpenTransactions = onOpenTransactions,
                    onAddTransaction = onAddTransaction,
                    onEditTransaction = onEditTransaction,
                )
            }
        }
        Spacer(Modifier.height(Spacing.xxl))
    }

    editingWallet?.let { draft ->
        WalletEditorSheet(
            initial = draft,
            onSave = walletsViewModel::save,
            onDelete = walletsViewModel::delete,
            onDismiss = { editingWallet = null },
        )
    }
}

private enum class HomeContent { Loading, Welcome, Overview }

@Composable
private fun BrandBar(onOpenSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.topBar),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LucentoMark(size = 28.dp, animate = false)
        Spacer(Modifier.width(Spacing.sm))
        Text(
            "Lucento",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Outlined.Settings, contentDescription = "Settings")
        }
    }
}

/** First run with no wallets: one clear next step, and an honest description of what follows. */
@Composable
private fun WelcomePanel(onAddWallet: () -> Unit) {
    val colors = LucentoTheme.extendedColors
    Column {
        Spacer(Modifier.height(Spacing.sm))
        StatementPanel(Modifier.fillMaxWidth()) {
            Text(
                "Let’s set up your money",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "Lucento builds your net worth and monthly picture from the accounts you add. " +
                    "Everything is calculated and stored on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onHeroMuted,
            )
            Spacer(Modifier.height(Spacing.xl))
            Step(1, "Add a wallet", "Cash, a bank account, an e-wallet, savings or a credit card.", current = true)
            Step(2, "Record income and expenses", "A few seconds each — the amount comes first.", current = false)
            Step(3, "See where it goes", "Monthly cash flow, trends and top categories appear here.", current = false)
            Spacer(Modifier.height(Spacing.xl))
            Button(
                onClick = onAddWallet,
                modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.button),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = colors.onHero, contentColor = colors.hero),
            ) {
                Text("Add your first wallet", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun Step(number: Int, title: String, body: String, current: Boolean) {
    val colors = LucentoTheme.extendedColors
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.sm), verticalAlignment = Alignment.Top) {
        Text(
            "0$number",
            style = OverlineStyle,
            color = if (current) colors.accent else colors.onHeroMuted,
            modifier = Modifier.width(32.dp).padding(top = 3.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (current) colors.onHero else colors.onHeroMuted)
            Text(body, style = MaterialTheme.typography.bodySmall, color = colors.onHeroMuted)
        }
    }
}

@Composable
private fun Overview(
    state: HomeUiState,
    onOpenWallets: () -> Unit,
    onOpenTransactions: () -> Unit,
    onAddTransaction: (TransactionType) -> Unit,
    onEditTransaction: (Transaction) -> Unit,
) {
    Column {
        Spacer(Modifier.height(Spacing.sm))
        NetWorthPanel(state, onOpenWallets)

        Spacer(Modifier.height(Spacing.md))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            QuickActionButton(
                text = "Expense",
                icon = Icons.Filled.Remove,
                onClick = { onAddTransaction(TransactionType.EXPENSE) },
                modifier = Modifier.weight(1f),
            )
            QuickActionButton(
                text = "Income",
                icon = Icons.Filled.Add,
                onClick = { onAddTransaction(TransactionType.INCOME) },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(Spacing.section))
        MonthSection(state)

        Spacer(Modifier.height(Spacing.section))
        RecentSection(state, onOpenTransactions, onAddTransaction, onEditTransaction)

        if (state.hasTrend) {
            Spacer(Modifier.height(Spacing.section))
            TrendSection(state)
        }
        if (state.topCategories.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.section))
            CategoriesSection(state)
        }
    }
}

@Composable
private fun NetWorthPanel(state: HomeUiState, onOpenWallets: () -> Unit) {
    val summary = state.netWorth ?: return
    val colors = LucentoTheme.extendedColors
    val primary = summary.totalFor(state.primaryCurrency) ?: summary.totals.first()
    val others = summary.totals.filter { it.currencyCode != primary.currencyCode }
    val code = primary.currencyCode

    StatementPanel(Modifier.fillMaxWidth(), onClick = onOpenWallets, onClickLabel = "View wallets") {
        Text(
            "NET WORTH · $code",
            style = OverlineStyle,
            color = colors.onHeroMuted,
        )
        Spacer(Modifier.height(Spacing.sm))
        MoneyText(money = primary, style = AmountStyles.display, color = colors.onHero)
        Spacer(Modifier.height(Spacing.lg))
        HorizontalDivider(color = colors.heroRule)
        Spacer(Modifier.height(Spacing.md))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Figure("Assets", Modifier.weight(1f), labelColor = colors.onHeroMuted) {
                MoneyText(money = summary.assets[code] ?: Money.zero(code), style = AmountStyles.medium, color = colors.onHero, fractionScale = 1f)
            }
            Spacer(Modifier.width(Spacing.md))
            Figure("Owed", Modifier.weight(1f), labelColor = colors.onHeroMuted) {
                MoneyText(money = summary.liabilities[code] ?: Money.zero(code), style = AmountStyles.medium, color = colors.onHero, fractionScale = 1f)
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.onHeroMuted,
            )
        }
        if (others.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.md))
            Text(
                "Also held: " + others.joinToString(" · ") { MoneyFormatter.formatSigned(it, showPlus = false) },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onHeroMuted,
            )
            Text(
                "Kept separate — Lucento never converts with guessed rates.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onHeroMuted,
            )
        }
    }
}

@Composable
private fun MonthSection(state: HomeUiState) {
    val monthName = remember(state.month) { monthLabel(state.month) }
    SectionHeader("This month · $monthName")
    val totals = state.monthTotals
    if (totals == null) {
        Text(
            "Nothing recorded in $monthName yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Figure("In", Modifier.weight(1f)) {
                MoneyText(money = totals.income, style = AmountStyles.large, showPlus = true, color = LucentoTheme.extendedColors.positive, fractionScale = 0.75f)
            }
            Figure("Out", Modifier.weight(1f)) {
                MoneyText(money = -totals.expense, style = AmountStyles.large, fractionScale = 0.75f)
            }
        }
        Spacer(Modifier.height(Spacing.md))
        val percent = Insights.percentOf(totals.expense, totals.income)
        if (percent != null) {
            val over = percent > 100
            ShareBar(
                fraction = Insights.fraction(totals.expense, totals.income),
                color = if (over) LucentoTheme.extendedColors.negative else LucentoTheme.extendedColors.chartBar,
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                if (over) "Spending is $percent% of this month’s income — ${MoneyFormatter.format(-totals.net)} more than came in."
                else "You’ve spent $percent% of this month’s income. ${MoneyFormatter.format(totals.net)} left over so far.",
                style = MaterialTheme.typography.bodySmall,
                color = if (over) LucentoTheme.extendedColors.negative else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                "No income recorded this month yet, so there’s nothing to compare spending against.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    state.otherMonthTotals.forEach { t ->
        Spacer(Modifier.height(Spacing.sm))
        Text(
            "${t.currencyCode}: ${MoneyFormatter.formatSigned(t.income, showPlus = true)} in · " +
                "${MoneyFormatter.formatSigned(-t.expense)} out",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecentSection(
    state: HomeUiState,
    onOpenTransactions: () -> Unit,
    onAddTransaction: (TransactionType) -> Unit,
    onEditTransaction: (Transaction) -> Unit,
) {
    SectionHeader("Recent") {
        if (state.recent.isNotEmpty()) {
            LucentoTextButton(
                text = if (state.transactionCount > state.recent.size) "See all ${state.transactionCount}" else "See all",
                onClick = onOpenTransactions,
            )
        }
    }
    if (state.recent.isEmpty()) {
        Text(
            "Your first transaction will appear here. Record a purchase or a payment to start your monthly picture.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.md))
        LucentoSecondaryButton(
            text = "Record a transaction",
            icon = Icons.Filled.Add,
            onClick = { onAddTransaction(TransactionType.EXPENSE) },
        )
        return
    }
    state.recent.forEachIndexed { index, transaction ->
        TransactionRow(
            transaction = transaction,
            category = transaction.categoryId?.let { state.categoriesById[it] },
            wallet = state.walletsById[transaction.walletId],
            onClick = { onEditTransaction(transaction) },
            trailingCaption = shortDayLabel(transaction.date),
        )
        if (index < state.recent.lastIndex) Hairline(inset = Sizes.iconTile + Spacing.md)
    }
}

@Composable
private fun TrendSection(state: HomeUiState) {
    val current = state.trend.last()
    val highest = state.trend.maxBy { it.amount.amountMinor }
    SectionHeader("Spending · last ${state.trend.size} months")
    MonthlyBarChart(months = state.trend, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(Spacing.md))
    Text(
        buildString {
            append("${monthLabel(current.month)} so far: ${MoneyFormatter.format(current.amount)}.")
            if (highest.month != current.month && !highest.amount.isZero) {
                append(" Highest: ${monthLabel(highest.month)}, ${MoneyFormatter.format(highest.amount)}.")
            }
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CategoriesSection(state: HomeUiState) {
    val total = state.monthSpend ?: return
    SectionHeader("Where it went · ${monthLabel(state.month)}")
    state.topCategories.forEachIndexed { index, share ->
        val category = share.categoryId?.let { state.categoriesById[it] }
        val percent = Insights.percentOf(share.amount, total) ?: 0
        Column(Modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryTile(category = category, type = TransactionType.EXPENSE, size = 32.dp)
                Spacer(Modifier.width(Spacing.md))
                Text(
                    category?.name ?: "Uncategorized",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(Spacing.sm))
                AmountText(share.amount, style = AmountStyles.row)
                Text(
                    "$percent%",
                    style = AmountStyles.small,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(44.dp).padding(start = Spacing.sm),
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            ShareBar(
                fraction = Insights.fraction(share.amount, total),
                color = if (index == 0) LucentoTheme.extendedColors.chartHighlight else LucentoTheme.extendedColors.chartBar,
                modifier = Modifier.padding(start = 32.dp + Spacing.md),
                height = 4.dp,
            )
        }
    }
}

private fun monthLabel(month: YearMonth, locale: Locale = Locale.getDefault()): String =
    month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) }
