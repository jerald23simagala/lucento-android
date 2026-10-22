package com.lucento.app.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardVoice
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.core.designsystem.component.ActionTile
import com.lucento.app.core.designsystem.component.AmountText
import com.lucento.app.core.designsystem.component.Figure
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoCard
import com.lucento.app.core.designsystem.component.LucentoMascot
import com.lucento.app.core.designsystem.component.LucentoSecondaryButton
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.component.MoneyText
import com.lucento.app.core.designsystem.component.ProgressRing
import com.lucento.app.core.designsystem.component.SectionHeader
import com.lucento.app.core.designsystem.component.StatementPanel
import com.lucento.app.core.designsystem.component.StatusPill
import com.lucento.app.core.designsystem.component.StatusTones
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.OverlineStyle
import com.lucento.app.core.designsystem.theme.Sizes
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.domain.BudgetStatus
import com.lucento.app.domain.Feature
import com.lucento.app.domain.SafeToSpend
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import com.lucento.app.feature.transactions.TransactionRow
import com.lucento.app.feature.transactions.shortDayLabel
import com.lucento.app.feature.wallets.WalletDraft
import com.lucento.app.feature.wallets.WalletEditorSheet
import com.lucento.app.feature.wallets.WalletsViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** Callbacks Home needs from the app shell. Feature gating happens here and in the shell. */
data class HomeActions(
    val onAddTransaction: (TransactionType) -> Unit,
    val onTransfer: () -> Unit,
    val onScanReceipt: () -> Unit,
    val onSpeak: () -> Unit,
    val onEditTransaction: (Transaction) -> Unit,
    val onOpenTransactions: () -> Unit,
    val onOpenInsights: () -> Unit,
    val onOpenBudgets: () -> Unit,
    val onOpenRecurring: () -> Unit,
)

@Composable
fun HomeScreen(
    actions: HomeActions,
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
        Greeting(today = state.today)

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
                HomeContent.Overview -> Overview(state = state, actions = actions)
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

/** Time-of-day greeting with the date, and the Sun (day) or Moon (night theme) beside it — small and secondary. */
@Composable
private fun Greeting(today: LocalDate) {
    val greeting = remember {
        when (LocalTime.now().hour) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
    }
    val dateLabel = remember(today) {
        today.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault()))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .padding(top = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(greeting, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            Text(
                dateLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        LucentoMascot(size = 60.dp)
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
                "Lucento works out what’s safe to spend from the accounts you add. " +
                    "Everything is calculated and stored on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onHeroMuted,
            )
            Spacer(Modifier.height(Spacing.xl))
            Step(1, "Add a wallet", "Cash, a bank account, an e-wallet, savings or a card.", current = true)
            Step(2, "Record income and expenses", "Type, speak or scan — you always review first.", current = false)
            Step(3, "See where it goes", "Safe to spend, burn rate and net flow appear here.", current = false)
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
private fun Overview(state: HomeUiState, actions: HomeActions) {
    val features = state.features
    Column {
        Spacer(Modifier.height(Spacing.xs))
        SafeToSpendCard(
            state = state,
            onOpenBudgets = if (features.isEnabled(Feature.BUDGETS)) actions.onOpenBudgets else null,
        )

        Spacer(Modifier.height(Spacing.md))
        QuickActions(state, actions)

        Spacer(Modifier.height(Spacing.md))
        FlowCard(state, onOpenInsights = if (features.isEnabled(Feature.INSIGHTS)) actions.onOpenInsights else null)

        if (state.budgetAlerts.isNotEmpty() || state.upcoming.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.section))
            AttentionSection(state, actions)
        }

        Spacer(Modifier.height(Spacing.section))
        RecentSection(state, actions)
    }
}

// ------------------------------------------------------------------ Safe to spend

@Composable
private fun SafeToSpendCard(state: HomeUiState, onOpenBudgets: (() -> Unit)?) {
    val safe = state.safeToSpend
    var explaining by rememberSaveable { mutableStateOf(false) }
    LucentoCard(Modifier.fillMaxWidth(), contentPadding = Spacing.xl) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "SAFE TO SPEND",
                style = OverlineStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { explaining = !explaining }) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = if (explaining) "Hide how this is calculated" else "How this is calculated",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (safe == null) {
            Text(
                "Add a cash, bank or e-wallet account" +
                    (if (onOpenBudgets != null) ", or set a monthly budget," else "") +
                    " to see what’s safe to spend in ${state.focusCurrency}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (onOpenBudgets != null) {
                LucentoTextButton(text = "Set a monthly budget", onClick = onOpenBudgets)
            }
        } else {
            SafeToSpendBody(safe, state, explaining, onOpenBudgets)
        }
    }
}

@Composable
private fun SafeToSpendBody(safe: SafeToSpend, state: HomeUiState, explaining: Boolean, onOpenBudgets: (() -> Unit)?) {
    val colors = LucentoTheme.extendedColors
    Column {
        val negative = safe.amount.isNegative
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                MoneyText(
                    money = safe.amount,
                    style = AmountStyles.display,
                    color = if (negative) colors.negative else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    periodLine(safe, state.today),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val fraction = safe.budgetUsedFraction
            val percent = safe.budgetUsedPercent
            if (fraction != null && percent != null) {
                Spacer(Modifier.width(Spacing.md))
                ProgressRing(
                    fraction = fraction,
                    description = "$percent% of this month’s budget spent or committed",
                    color = when {
                        percent > 100 -> colors.negative
                        percent >= 80 -> colors.warning
                        else -> colors.success
                    },
                ) {
                    Text("$percent%", style = AmountStyles.small, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Text(
            contextLine(safe),
            style = MaterialTheme.typography.bodySmall,
            color = if (negative) colors.negative else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AnimatedVisibility(visible = explaining, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            InfoNote(explanation(safe), modifier = Modifier.padding(top = Spacing.md))
        }
        if (onOpenBudgets != null) {
            Spacer(Modifier.height(Spacing.xs))
            LucentoTextButton(
                text = if (safe.basis == SafeToSpend.Basis.BUDGET) "View budgets" else "Set a monthly budget for a sharper figure",
                onClick = onOpenBudgets,
            )
        }
    }
}

private fun periodLine(safe: SafeToSpend, today: LocalDate): String {
    val locale = Locale.getDefault()
    val end = safe.month.atEndOfMonth()
    val month = end.month.getDisplayName(TextStyle.SHORT, locale)
    val days = if (safe.daysLeft == 1) "today is the last day" else "${safe.daysLeft} days left"
    val perDay = safe.perDay?.let { " · ${MoneyFormatter.format(it)} a day" }.orEmpty()
    return "$month ${today.dayOfMonth}–${end.dayOfMonth} · $days$perDay"
}

private fun contextLine(safe: SafeToSpend): String = buildString {
    when (safe.basis) {
        SafeToSpend.Basis.BUDGET -> {
            append("Budget ${MoneyFormatter.format(safe.budgetLimit!!)} · spent ${MoneyFormatter.format(safe.spentThisMonth)}")
        }
        SafeToSpend.Basis.BALANCES -> {
            append("Estimate from spendable balances (${MoneyFormatter.format(safe.spendableBalance!!)})")
        }
    }
    if (!safe.committed.isZero) append(" · bills due ${MoneyFormatter.format(safe.committed)}")
    if (safe.amount.isNegative) append(". You’re over by ${MoneyFormatter.format(-safe.amount)}.")
}

private fun explanation(safe: SafeToSpend): String = when (safe.basis) {
    SafeToSpend.Basis.BUDGET ->
        "Your monthly budget, minus what you’ve spent this month, minus recurring bills still due before the month ends. " +
            "Transfers between your accounts don’t count. Only ${safe.currencyCode} is included — other currencies are never converted."
    SafeToSpend.Basis.BALANCES ->
        "You haven’t set a monthly budget, so this is an estimate: your cash, bank and e-wallet balances, minus recurring bills " +
            "still due this month. Savings and credit cards aren’t included. Only ${safe.currencyCode} is counted."
}

// ------------------------------------------------------------------ Quick actions

@Composable
private fun QuickActions(state: HomeUiState, actions: HomeActions) {
    val f = state.features
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        ActionTile("Expense", Icons.Filled.Remove, { actions.onAddTransaction(TransactionType.EXPENSE) }, Modifier.weight(1f), primary = true)
        ActionTile("Income", Icons.Filled.Add, { actions.onAddTransaction(TransactionType.INCOME) }, Modifier.weight(1f))
        if (f.isEnabled(Feature.MULTIPLE_WALLETS)) {
            ActionTile("Transfer", Icons.Outlined.SwapHoriz, actions.onTransfer, Modifier.weight(1f))
        }
        if (f.isEnabled(Feature.RECEIPT_SCANNING)) {
            ActionTile("Scan", Icons.Outlined.DocumentScanner, actions.onScanReceipt, Modifier.weight(1f))
        }
        if (f.isEnabled(Feature.VOICE_ENTRY)) {
            ActionTile("Speak", Icons.Outlined.KeyboardVoice, actions.onSpeak, Modifier.weight(1f))
        }
    }
}

// ------------------------------------------------------------------ Net flow + burn rate

@Composable
private fun FlowCard(state: HomeUiState, onOpenInsights: (() -> Unit)?) {
    val flow = state.netFlow ?: return
    val burn = state.burnRate
    val colors = LucentoTheme.extendedColors
    val monthName = monthLabel(flow.month)
    LucentoCard(Modifier.fillMaxWidth(), onClick = onOpenInsights) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "NET FLOW · ${monthName.uppercase(Locale.getDefault())}",
                style = OverlineStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (onOpenInsights != null) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        MoneyText(
            money = flow.net,
            style = AmountStyles.hero,
            showPlus = true,
            color = if (flow.net.isNegative) colors.negative else MaterialTheme.colorScheme.onSurface,
        )
        flow.changeVsPrevious?.let { change ->
            val previousName = monthLabel(flow.month.minusMonths(1))
            Text(
                when {
                    change.isZero -> "Same as $previousName"
                    change.isNegative -> "${MoneyFormatter.format(-change)} lower than $previousName"
                    else -> "${MoneyFormatter.format(change)} higher than $previousName"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Spacing.md))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Figure("Income", Modifier.weight(1f)) {
                MoneyText(money = flow.income, style = AmountStyles.medium, showPlus = true, color = colors.positive, fractionScale = 1f)
            }
            Figure("Expenses", Modifier.weight(1f)) {
                MoneyText(money = -flow.expense, style = AmountStyles.medium, fractionScale = 1f)
            }
        }
        if (burn != null) {
            Spacer(Modifier.height(Spacing.md))
            Hairline()
            Spacer(Modifier.height(Spacing.md))
            Text("BURN RATE", style = OverlineStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.xs))
            Row(verticalAlignment = Alignment.Bottom) {
                AmountText(burn.dailyAverage, style = AmountStyles.large, signed = false)
                Text(
                    " a day so far",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
            Text(
                buildString {
                    append("Actual average over ${burn.daysElapsed} ${if (burn.daysElapsed == 1) "day" else "days"} of $monthName.")
                    val prev = burn.previousDailyAverage
                    val change = burn.changePercent
                    if (prev != null && change != null) {
                        append(" Last month: ${MoneyFormatter.format(prev)} a day")
                        append(
                            when {
                                change > 0 -> " — $change% faster now."
                                change < 0 -> " — ${-change}% slower now."
                                else -> " — the same pace."
                            }
                        )
                    }
                    if (!burn.spentThisMonth.isZero) {
                        append(" Projected for the month at this pace: ${MoneyFormatter.format(burn.projectedMonthSpend)}.")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.otherMonthTotals.forEach { t ->
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "${t.currencyCode} this month: ${MoneyFormatter.formatSigned(t.income, showPlus = true)} in · " +
                    "${MoneyFormatter.formatSigned(-t.expense)} out (kept separate, never converted)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ------------------------------------------------------------------ Attention

@Composable
private fun AttentionSection(state: HomeUiState, actions: HomeActions) {
    SectionHeader("Needs attention")
    state.budgetAlerts.take(3).forEach { progress ->
        val name = progress.budget.categoryId?.let { state.categoriesById[it]?.name } ?: "Monthly budget"
        val over = progress.status == BudgetStatus.OVER_LIMIT
        AttentionRow(
            icon = Icons.Outlined.WarningAmber,
            title = name,
            subtitle = if (over) {
                "Over by ${MoneyFormatter.format(-progress.remaining)}"
            } else {
                "${MoneyFormatter.format(progress.remaining)} left of ${MoneyFormatter.format(progress.budget.limit)}"
            },
            pill = { StatusPill("${progress.percentUsed ?: 0}%", if (over) StatusTones.negative else StatusTones.warning) },
            onClick = actions.onOpenBudgets,
        )
    }
    state.upcoming.take(3).forEach { item ->
        val overdue = item.dueDate.isBefore(state.today)
        AttentionRow(
            icon = Icons.Outlined.Event,
            title = item.schedule.name,
            subtitle = (if (overdue) "Overdue since " else "Due ") + shortDayLabel(item.dueDate, state.today),
            pill = {
                AmountText(
                    money = if (item.schedule.type == TransactionType.INCOME) item.schedule.amount else -item.schedule.amount,
                    showPlus = true,
                    color = if (item.schedule.type == TransactionType.INCOME) LucentoTheme.extendedColors.positive else MaterialTheme.colorScheme.onSurface,
                )
            },
            onClick = actions.onOpenRecurring,
        )
    }
}

@Composable
private fun AttentionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    pill: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    com.lucento.app.core.designsystem.component.NavRow(
        icon = icon,
        title = title,
        summary = subtitle,
        onClick = onClick,
        tone = StatusTones.neutral,
        trailing = pill,
    )
}

// ------------------------------------------------------------------ Recent

@Composable
private fun RecentSection(state: HomeUiState, actions: HomeActions) {
    SectionHeader("Recent transactions") {
        if (state.recent.isNotEmpty()) {
            LucentoTextButton(
                text = if (state.transactionCount > state.recent.size) "See all ${state.transactionCount}" else "See all",
                onClick = actions.onOpenTransactions,
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
            onClick = { actions.onAddTransaction(TransactionType.EXPENSE) },
        )
        return
    }
    val names = remember(state.wallets) { state.wallets.associate { it.id to it.name } }
    state.recent.forEachIndexed { index, transaction ->
        TransactionRow(
            transaction = transaction,
            category = transaction.categoryId?.let { state.categoriesById[it] },
            wallet = state.walletsById[transaction.walletId],
            onClick = { actions.onEditTransaction(transaction) },
            trailingCaption = shortDayLabel(transaction.date, state.today),
            transfer = transaction.transferGroup?.let { state.transfers[it] },
            walletNames = names,
        )
        if (index < state.recent.lastIndex) Hairline(inset = Sizes.iconTile + Spacing.md)
    }
}

internal fun monthLabel(month: YearMonth, locale: Locale = Locale.getDefault()): String =
    month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) }
