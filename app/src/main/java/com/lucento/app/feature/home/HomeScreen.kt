package com.lucento.app.feature.home

import androidx.compose.material.icons.outlined.Insights
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.SyncProblem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.outlined.Close
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.core.designsystem.component.AmountText
import com.lucento.app.core.designsystem.component.CardHeader
import com.lucento.app.core.designsystem.component.CategoryTile
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.IconTile
import com.lucento.app.core.designsystem.component.LucentoCard
import com.lucento.app.core.designsystem.component.LucentoMark
import com.lucento.app.core.designsystem.component.LucentoMascot
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.component.LucentoWordmark
import com.lucento.app.core.designsystem.component.MascotMood
import com.lucento.app.core.designsystem.component.MoneyText
import com.lucento.app.core.designsystem.component.ProfileButton
import com.lucento.app.core.designsystem.component.StatementPanel
import com.lucento.app.core.designsystem.component.StatusPill
import com.lucento.app.core.designsystem.component.StatusTones
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.Hierarchy
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Sizes
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.designsystem.theme.TonePair
import com.lucento.app.core.money.Money
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.domain.BudgetBurn
import com.lucento.app.domain.BudgetPlan
import com.lucento.app.domain.BudgetSuggestion
import com.lucento.app.domain.FirstWeek
import com.lucento.app.domain.Feature
import com.lucento.app.domain.GoalScene
import com.lucento.app.domain.GoalsOverview
import com.lucento.app.domain.NetWorthComposition
import com.lucento.app.domain.PacePosition
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.UpcomingPayment
import com.lucento.app.domain.RecurringMath
import java.time.LocalDate
import com.lucento.app.domain.WellnessTier
import com.lucento.app.feature.plan.illustration
import com.lucento.app.feature.plan.rememberGoalPhoto
import com.lucento.app.feature.wallets.WalletDraft
import com.lucento.app.feature.wallets.WalletEditorSheet
import com.lucento.app.feature.wallets.WalletsViewModel
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Callbacks Home needs from the app shell. Adding transactions happens through the dock's central + button. */
data class HomeActions(
    val onOpenBudget: () -> Unit,
    val onChooseCategories: () -> Unit,
    val onOpenInsights: () -> Unit,
    val onOpenGoals: () -> Unit,
    /** Opens Recurring payments; with a schedule id, straight to that payment's review / mark-paid sheet. */
    val onOpenRecurring: (Long?) -> Unit,
    val onOpenWallets: () -> Unit = {},
    val onOpenDebts: () -> Unit = {},
    /** Where the sync status leads: Settings, which explains and controls cloud sync. */
    val onOpenSettings: () -> Unit = {},
    /** Opens the composer to log another entry. */
    val onAddEntry: () -> Unit = {},
)

/**
 * The dashboard, top to bottom: the header (brand, sync status, notifications, profile) with the month pill; the
 * monthly budget card (budget used against month elapsed, with Lucento the mascot); net worth with quick access; the
 * top savings goal; and scheduled transactions. Every figure comes from [HomeUiState].
 */
@Composable
fun HomeScreen(
    actions: HomeActions,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
    walletsViewModel: WalletsViewModel = viewModel(factory = WalletsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editingWallet by remember { mutableStateOf<WalletDraft?>(null) }

    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize()) {
        // Pinned: the brand, sync status, notifications and profile stay put while the dashboard scrolls beneath.
        HomeHeader(
            status = state.cloudStatus,
            onOpenSyncSettings = actions.onOpenSettings,
            bell = if (state.features.isEnabled(Feature.RECURRING)) ({ PaymentsBell(state, actions.onOpenRecurring) }) else null,
            modifier = Modifier.padding(horizontal = Spacing.gutter),
        )
        if (scroll.value > 0) Hairline()

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(horizontal = Spacing.gutter),
        ) {
        AnimatedContent(
            targetState = when {
                state.loadError -> HomeContent.Error
                state.loading -> HomeContent.Loading
                !state.hasWallets -> HomeContent.Welcome
                else -> HomeContent.Overview
            },
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
            label = "home-content",
        ) { content ->
            when (content) {
                HomeContent.Loading -> HomeSkeleton()
                HomeContent.Error -> LoadError(onRetry = viewModel::retry)
                HomeContent.Welcome -> WelcomePanel(
                    onAddWallet = { editingWallet = WalletDraft(currencyCode = state.primaryCurrency) },
                )
                HomeContent.Overview -> Overview(
                    state = state,
                    actions = actions.copy(onOpenInsights = {
                        viewModel.markInsightsChecked()
                        actions.onOpenInsights()
                    }),
                    onSelectMonth = viewModel::selectMonth,
                    onDismissLocalDataNotice = viewModel::dismissLocalDataNotice,
                    onDismissFirstWeek = viewModel::dismissFirstWeek,
                    onSnoozeSuggestion = viewModel::snoozeBudgetSuggestion,
                    onUseSuggestion = viewModel::applyBudgetSuggestion,
                    onCelebrateShown = viewModel::markFirstEntryCelebrated,
                )
            }
        }
        // The dock's padding already clears the bar. This extra space keeps the last card off the raised +.
        Spacer(Modifier.height(Spacing.fabClearance))
        }
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

private enum class HomeContent { Loading, Error, Welcome, Overview }

// ------------------------------------------------------------------ Header

/** Brand on the left; sync status, the payments bell (when Scheduled is on) and the profile entry on the right. */
@Composable
private fun HomeHeader(
    status: CloudStatus,
    onOpenSyncSettings: () -> Unit,
    bell: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // On narrow phones the symbol alone carries the brand, leaving room for the status chip and actions.
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val showWordmark = maxWidth >= 360.dp
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Sizes.topBar)
                .padding(top = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) { heading() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LucentoMark(size = 28.dp, animate = false)
                if (showWordmark) {
                    Spacer(Modifier.width(Spacing.sm))
                    LucentoWordmark(height = 20.dp)
                }
            }
            SyncStatusChip(status, onClick = onOpenSyncSettings)
            bell?.invoke()
            ProfileButton()
        }
    }
}

/**
 * The bell: scheduled payments that need attention (overdue, or due within a week) with a count badge, so they're
 * noticed without scrolling to the Scheduled card. Opens a short list; each item opens its review / mark-paid sheet.
 */
@Composable
private fun PaymentsBell(state: HomeUiState, onOpenRecurring: (Long?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val due = state.dueSoon
    val overdue = due.count { it.overdue }
    Box {
        IconButton(
            onClick = { open = true },
            modifier = Modifier.semantics {
                contentDescription = when {
                    due.isEmpty() -> "Upcoming payments: nothing due this week"
                    overdue > 0 -> "Upcoming payments: ${due.size} due, $overdue overdue"
                    else -> "Upcoming payments: ${due.size} due this week"
                }
            },
        ) {
            BadgedBox(
                badge = {
                    if (due.isNotEmpty()) {
                        Badge(
                            containerColor = if (overdue > 0) LucentoTheme.extendedColors.negative else MaterialTheme.colorScheme.primary,
                            contentColor = if (overdue > 0) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onPrimary,
                        ) { Text(if (due.size > 9) "9+" else "${due.size}") }
                    }
                },
            ) {
                Icon(if (due.isEmpty()) Icons.Outlined.Notifications else Icons.Filled.Notifications, contentDescription = null)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.widthIn(min = 260.dp, max = 320.dp)) {
            Text(
                "Upcoming payments",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm).semantics { heading() },
            )
            if (due.isEmpty()) {
                Text(
                    "Nothing due in the next ${RecurringMath.DUE_SOON_DAYS} days.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.md).padding(bottom = Spacing.sm),
                )
            }
            due.forEach { item ->
                val income = item.schedule.type == TransactionType.INCOME
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(item.schedule.name, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                            Text(
                                dueLabel(item, state.today),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (item.overdue) LucentoTheme.extendedColors.negative else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    trailingIcon = {
                        AmountText(
                            money = if (income) item.schedule.amount else -item.schedule.total,
                            showPlus = true,
                            style = AmountStyles.small,
                            color = if (income) LucentoTheme.extendedColors.positive else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = {
                        open = false
                        onOpenRecurring(item.schedule.id)
                    },
                )
            }
            Hairline()
            DropdownMenuItem(
                text = { Text("See all scheduled", color = MaterialTheme.colorScheme.primary) },
                onClick = {
                    open = false
                    onOpenRecurring(null)
                },
            )
        }
    }
}

/** "Overdue · Oct 1", "Due today", "Due tomorrow", "Due in 3 days". */
private fun dueLabel(item: UpcomingPayment, today: LocalDate): String {
    val days = ChronoUnit.DAYS.between(today, item.dueDate)
    val date = item.dueDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()))
    return when {
        item.overdue -> "Overdue · $date"
        days == 0L -> "Due today"
        days == 1L -> "Due tomorrow"
        else -> "Due in $days days · $date"
    }
}

/** A compact chip: icon plus a one-word state, so the meaning never relies on colour. Opens Settings › Cloud sync. */
@Composable
private fun SyncStatusChip(status: CloudStatus, onClick: () -> Unit) {
    val tone: TonePair
    val icon: ImageVector
    when (status) {
        CloudStatus.LOCAL -> { tone = StatusTones.neutral; icon = Icons.Outlined.PhoneAndroid }
        CloudStatus.SYNCED -> { tone = StatusTones.positive; icon = Icons.Outlined.CloudDone }
        CloudStatus.PENDING -> { tone = StatusTones.warning; icon = Icons.Outlined.CloudUpload }
        CloudStatus.OFFLINE -> { tone = StatusTones.neutral; icon = Icons.Outlined.CloudOff }
        CloudStatus.ERROR -> { tone = StatusTones.negative; icon = Icons.Outlined.SyncProblem }
    }
    Box(
        Modifier
            .heightIn(min = Sizes.minTouch)
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClickLabel = "Open cloud sync settings", onClick = onClick)
            .clearAndSetSemantics { contentDescription = "Sync status: ${status.label}. Opens cloud sync settings." },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .clip(CircleShape)
                .background(tone.container)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = tone.content, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(Spacing.xs))
            Text(status.label, style = MaterialTheme.typography.labelMedium, color = tone.content, maxLines = 1)
        }
    }
}

/** The month pill: which month the budget card describes, with a menu of the months that have data. */
@Composable
private fun MonthPill(state: HomeUiState, onSelect: (YearMonth) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .heightIn(min = Sizes.minTouch)
                .clip(MaterialTheme.shapes.medium)
                .border(BorderStroke(1.dp, LucentoTheme.extendedColors.borderDefault), MaterialTheme.shapes.medium)
                .background(if (LucentoTheme.isDark) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLowest)
                .clickable(role = Role.Button, onClickLabel = "Choose month") { open = true }
                .padding(horizontal = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.sm))
            Text(monthYearLabel(state.month), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.width(Spacing.xs))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            state.availableMonths.forEach { month ->
                DropdownMenuItem(
                    text = {
                        Text(
                            if (month == YearMonth.from(state.today)) "${monthYearLabel(month)} · this month" else monthYearLabel(month),
                            fontWeight = if (month == state.month) FontWeight.SemiBold else null,
                        )
                    },
                    onClick = {
                        open = false
                        onSelect(month)
                    },
                )
            }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Let’s set up your money",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                LucentoMascot(size = 56.dp, mood = MascotMood.Curious)
            }
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "Add the accounts you use. Lucento keeps your budget, cash flow and goals in view — all calculated on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onHeroMuted,
            )
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
private fun HomeSkeleton() {
    Column(Modifier.padding(top = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        repeat(3) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
            )
        }
    }
}

@Composable
private fun LoadError(onRetry: () -> Unit) {
    Column(Modifier.padding(top = Spacing.xl)) {
        Text("Couldn’t load your data. Try again", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Spacing.md))
        LucentoPrimaryButton(text = "Try again", onClick = onRetry)
    }
}

@Composable
private fun Overview(
    state: HomeUiState,
    actions: HomeActions,
    onSelectMonth: (YearMonth) -> Unit,
    onDismissLocalDataNotice: () -> Unit,
    onDismissFirstWeek: () -> Unit,
    onSnoozeSuggestion: () -> Unit,
    onUseSuggestion: () -> Unit,
    onCelebrateShown: () -> Unit,
) {
    val f = state.features
    var showCelebration by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.celebrateFirstEntry) {
        if (state.celebrateFirstEntry) {
            showCelebration = true
            onCelebrateShown()
        }
    }
    Column {
        if (state.showLocalDataNotice) {
            InfoNote(
                "Your wallets, transactions, budgets and goals stay only on this phone unless cloud sync is on. " +
                    "Uninstalling Lucento or clearing its storage erases them.",
                actionLabel = "Dismiss",
                onAction = onDismissLocalDataNotice,
            )
            Spacer(Modifier.height(Spacing.sm))
        }
        if (showCelebration) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = Spacing.sm)) {
                LucentoMascot(size = 56.dp, mood = MascotMood.Celebrate, contentDescription = null)
                Spacer(Modifier.width(Spacing.md))
                Text(FirstWeek.REACTION, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            }
        }
        BalanceAndRecent(state, onAdd = actions.onAddEntry)
        if (state.showFirstWeekCard) {
            Spacer(Modifier.height(Spacing.md))
            FirstWeekCard(
                state = state,
                onDismiss = onDismissFirstWeek,
                onLog = actions.onAddEntry,
                onGoal = actions.onOpenGoals,
                onBill = { actions.onOpenRecurring(null) },
                onInsights = actions.onOpenInsights,
            )
        }
        state.budgetSuggestion?.let { suggestion ->
            Spacer(Modifier.height(Spacing.md))
            BudgetSuggestionCard(
                suggestion = suggestion,
                onUse = onUseSuggestion,
                onAdjust = actions.onOpenBudget,
                onNotNow = onSnoozeSuggestion,
            )
        }
        Spacer(Modifier.height(Spacing.md))
        val plan = state.budget
        val burn = state.burn
        if (plan != null && burn != null && plan.hasBudget) {
            // Lucento stands beside the month pill and leans over the budget card's corner, its torch lighting the card.
            // Its mood follows the month's pace; the words are read out through its label rather than a bubble.
            val mood = remember(plan, burn, state.isCurrentMonth, state.expenseCategoryCount) { budgetMood(state, plan, burn) }
            Box(Modifier.fillMaxWidth()) {
                Column {
                    MonthPill(state, onSelectMonth)
                    Spacer(Modifier.height(Spacing.sm))
                    MonthlyBudgetCard(state, plan, burn, actions)
                }
                if (!showCelebration) {
                    LucentoMascot(
                        size = MascotSize,
                        mood = mood.mood,
                        tier = mood.tier,
                        contentDescription = if (mood.mood == MascotMood.Concerned) "Lucento, gently concerned, says: ${mood.bubble}" else "Lucento says: ${mood.bubble}",
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            // Bottom of the mascot sinks [MascotOverlap] into the card, below the month pill's row.
                            .offset(y = Sizes.minTouch + Spacing.sm + MascotOverlap - MascotSize)
                            .padding(end = Spacing.xs),
                    )
                }
            }
        } else if (plan != null && burn != null) {
            MonthPill(state, onSelectMonth)
            Spacer(Modifier.height(Spacing.sm))
            SpendingThisMonthCard(spent = plan.spent, daysLeft = burn.daysLeft)
        } else {
            MonthPill(state, onSelectMonth)
            Spacer(Modifier.height(Spacing.sm))
            CashFlowCard(state, onOpenInsights = if (f.isEnabled(Feature.INSIGHTS)) actions.onOpenInsights else null)
        }

        state.netWorthComposition?.let { composition ->
            Spacer(Modifier.height(Spacing.md))
            NetWorthAndQuickLinks(state, composition, actions)
        }

        if (f.isEnabled(Feature.SAVINGS_GOALS)) {
            Spacer(Modifier.height(Spacing.md))
            TopGoalCard(state.goals, hasAnyGoals = state.hasAnyGoals, onOpen = actions.onOpenGoals)
        }

        if (f.isEnabled(Feature.RECURRING)) {
            Spacer(Modifier.height(Spacing.md))
            ScheduledCard(state, onOpen = actions.onOpenRecurring)
        }
    }
}

@Composable
private fun BalanceAndRecent(state: HomeUiState, onAdd: () -> Unit) {
    val balance = state.wallets.singleOrNull()?.balance ?: state.netWorthComposition?.netWorth
    if (balance != null) {
        Text("Balance", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoneyText(money = balance, style = AmountStyles.large, minScale = 0.6f)
        Spacer(Modifier.height(Spacing.sm))
    }
    Text("Recent", style = Hierarchy.sectionTitle, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(Spacing.xs))
    if (state.realEntryCount == 0) {
        Text("Nothing logged yet", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(Spacing.sm))
        LucentoPrimaryButton(text = "Add your first", onClick = onAdd)
    } else {
        state.recentEntries.forEach { entry ->
            val title = entry.note.ifBlank {
                entry.categoryId?.let { state.categoriesById[it]?.name }
                    ?: if (entry.type == com.lucento.app.domain.TransactionType.INCOME) "Income" else "Expense"
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = Sizes.minTouch).padding(vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 3)
                Spacer(Modifier.width(Spacing.sm))
                AmountText(
                    money = if (entry.type == com.lucento.app.domain.TransactionType.INCOME) entry.amount else -entry.amount,
                    showPlus = true,
                )
            }
        }
    }
}

@Composable
private fun SpendingThisMonthCard(spent: Money, daysLeft: Int) {
    LucentoCard(Modifier.fillMaxWidth(), contentPadding = Spacing.lg) {
        Text("Spending this month", style = Hierarchy.sectionTitle, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Spacing.sm))
        MoneyText(money = spent, style = AmountStyles.large, minScale = 0.6f)
        Text(
            "${daysLeftLabel(daysLeft)} left",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FirstWeekCard(
    state: HomeUiState,
    onDismiss: () -> Unit,
    onLog: () -> Unit,
    onGoal: () -> Unit,
    onBill: () -> Unit,
    onInsights: () -> Unit,
) {
    LucentoCard(Modifier.fillMaxWidth(), contentPadding = Spacing.lg) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("First week", style = Hierarchy.sectionTitle, modifier = Modifier.weight(1f).semantics { heading() })
            IconButton(onClick = onDismiss) {
                Icon(Icons.Outlined.Close, contentDescription = "Dismiss first week tips")
            }
        }
        state.firstWeekSteps.forEach { step ->
            val open = when (step.id) {
                "log3" -> onLog
                "goal" -> onGoal
                "bill" -> onBill
                else -> onInsights
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Sizes.minTouch)
                    .clickable(role = Role.Button, onClickLabel = step.title, onClick = open)
                    .padding(vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (step.done) "Done" else "To do",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (step.done) LucentoTheme.extendedColors.positive else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(min = 56.dp),
                )
                Text(step.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 3)
                step.progress?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
            }
        }
    }
}

@Composable
private fun BudgetSuggestionCard(
    suggestion: BudgetSuggestion,
    onUse: () -> Unit,
    onAdjust: () -> Unit,
    onNotNow: () -> Unit,
) {
    val sentence = suggestion.lines.joinToString(", ") { "${MoneyFormatter.format(it.suggested)} for ${it.categoryName}" }
    LucentoCard(Modifier.fillMaxWidth(), contentPadding = Spacing.lg) {
        Text(
            "Based on your last ${suggestion.days} days, try $sentence.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(Spacing.sm))
        Column {
            LucentoTextButton(text = "Use these", onClick = onUse, modifier = Modifier.fillMaxWidth())
            LucentoTextButton(text = "Adjust", onClick = onAdjust, modifier = Modifier.fillMaxWidth())
            LucentoTextButton(text = "Not now", onClick = onNotNow, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** A card's title with a chevron, saying the whole card opens its page, and an optional trailing action. */
@Composable
private fun LinkedTitle(
    title: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    showChevron: Boolean = true,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = if (compact) Hierarchy.cardTitle else Hierarchy.sectionTitle,
            maxLines = 2,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (showChevron) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(if (compact) 18.dp else 22.dp))
        if (trailing != null) Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

// ------------------------------------------------------------------ Monthly budget (budget + burn rate)

/** Colours for a pace verdict: the card tint, the budget bar and the advice banner. Words carry the meaning too. */
private data class PaceTones(val card: Color, val bar: Color, val banner: TonePair, val icon: ImageVector)

@Composable
private fun paceTones(position: PacePosition?): PaceTones {
    val ext = LucentoTheme.extendedColors
    // By day the tint is deep enough for the mascot's torch light to show against it; by night the navy already does.
    val tintAlpha = if (LucentoTheme.isDark) 0.22f else 0.95f
    return when (position) {
        PacePosition.AHEAD -> PaceTones(
            card = ext.negativeContainer.copy(alpha = tintAlpha),
            bar = ext.negativeIndicator,
            banner = TonePair(ext.negativeContainer, ext.negative),
            icon = Icons.AutoMirrored.Filled.TrendingUp,
        )
        PacePosition.BEHIND -> PaceTones(
            card = ext.infoContainer.copy(alpha = tintAlpha),
            bar = ext.success,
            banner = TonePair(ext.infoContainer, ext.info),
            icon = Icons.Outlined.Info,
        )
        PacePosition.ALIGNED -> PaceTones(
            card = ext.positiveContainer.copy(alpha = tintAlpha),
            bar = ext.success,
            banner = TonePair(ext.positiveContainer, ext.positive),
            icon = Icons.Filled.Check,
        )
        null -> PaceTones(
            card = MaterialTheme.colorScheme.primaryContainer.copy(alpha = tintAlpha),
            bar = MaterialTheme.colorScheme.primary,
            banner = TonePair(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer),
            icon = Icons.Outlined.Info,
        )
    }
}

/**
 * Budget health and burn rate in one card. The primary figure is the share of the month's budget used; two bars
 * compare it with the share of the month elapsed; the figures beneath give spent, remaining and the calendar; the
 * banner says in words whether spending runs faster than, with, or slower than the month. Lucento (the mascot) leans
 * over the top-right corner (see Overview) and its torch lights the card from there.
 */
@Composable
private fun MonthlyBudgetCard(state: HomeUiState, plan: BudgetPlan, burn: BudgetBurn, actions: HomeActions) {
    val monthName = monthLabel(plan.month)
    val setupNeeded = state.expenseCategoryCount == 0
    val insight = remember(plan, burn, state.isCurrentMonth) { PaceCopy.describe(plan, burn, monthName, state.isCurrentMonth) }
    val tones = paceTones(insight.position)
    val open = if (setupNeeded) actions.onChooseCategories else actions.onOpenBudget
    val elapsedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)

    LucentoCard(Modifier.fillMaxWidth(), onClick = open, contentPadding = 0.dp) {
      Box(Modifier.background(tones.card)) {
        // The sunrise, barely there: a warm hint behind the figures, never competing with them.
        Image(
            painter = painterResource(com.lucento.app.core.designsystem.component.LucentoIllustration.SunriseLandscape.res),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.BottomCenter,
            alpha = if (LucentoTheme.isDark) 0.10f else 0.14f,
            modifier = Modifier.matchParentSize(),
        )
        TorchLight(Modifier.matchParentSize())
        // Compact by design: the dashboard should show everything at a glance with little scrolling.
        Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
            run {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Leaves the top-right corner to the mascot leaning over it.
                    Column(Modifier.weight(1f).padding(end = MascotClearance)) {
                        LinkedTitle("Monthly budget")
                        Spacer(Modifier.height(Spacing.xxs))
                        when {
                            setupNeeded -> Text("Finish your budget setup", style = MaterialTheme.typography.titleMedium)
                            !plan.hasBudget -> Text("No budget yet", style = MaterialTheme.typography.titleMedium)
                            else -> Column {
                                Text("${burn.consumedPercent ?: 0}%", style = AmountStyles.large, maxLines = 2)
                                Text(
                                    "of ${MoneyFormatter.format(plan.total)} used",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                )
                            }
                        }
                    }
                }
            }

            if (!setupNeeded) {
                Spacer(Modifier.height(Spacing.sm))
                // Each bar says what it is, so no legend row is needed.
                if (plan.hasBudget) {
                    PercentBar(burn.consumedFraction, "${burn.consumedPercent ?: 0}% used", tones.bar, "Budget used: ${burn.consumedPercent ?: 0} percent")
                    Spacer(Modifier.height(Spacing.xs))
                }
                PercentBar(burn.elapsedPercent / 100f, "${burn.elapsedPercent}% of month", elapsedColor, "Month elapsed: ${burn.elapsedPercent} percent")

                Spacer(Modifier.height(Spacing.sm))
                BudgetFigures(plan, burn)
            }

            Spacer(Modifier.height(Spacing.sm))
            AdviceBanner(
                text = if (setupNeeded) "Choose the categories you spend on" else insight.message,
                detail = if (setupNeeded) "Your budget is built from them" else insight.detail,
                tones = tones,
                onClick = open,
            )

            if (state.otherBudgetCurrencies.isNotEmpty()) {
                Text(
                    "Budgets in ${state.otherBudgetCurrencies.joinToString()} are on the Budgets tab.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
        }
      }
    }
}

/** Lucento's mood for the month's budget: the mascot's expression, wellness tier and speech bubble. */
private class BudgetMood(val mood: MascotMood, val tier: WellnessTier, val bubble: String)

private fun budgetMood(state: HomeUiState, plan: BudgetPlan, burn: BudgetBurn): BudgetMood {
    val setupNeeded = state.expenseCategoryCount == 0
    val insight = PaceCopy.describe(plan, burn, monthLabel(plan.month), state.isCurrentMonth)
    val ahead = insight.position == PacePosition.AHEAD
    val mood = when {
        setupNeeded || !plan.hasBudget -> MascotMood.Curious
        ahead || plan.health.isConcerning -> MascotMood.Concerned
        else -> MascotMood.Happy
    }
    val tier = if (plan.hasBudget) WellnessTier.of(plan.health, aheadOfPace = ahead, overspent = plan.remaining.isNegative) else WellnessTier.STEADY
    return BudgetMood(mood, tier, if (setupNeeded) "Pick your spending categories first." else insight.bubble)
}

/** The mascot beside the budget card: its size and how far it leans down over the card's top edge. */
private val MascotSize = 96.dp
private val MascotOverlap = 44.dp

/** Room the card's title keeps free at its end, so the mascot never covers the figures. */
private val MascotClearance = 72.dp

/**
 * The mascot's torch lighting the budget card: a warm sunlight glow from the top-right corner (where Lucento leans
 * in), fading across the card. It breathes slowly; with reduced motion it holds still. Purely decorative.
 */
@Composable
private fun TorchLight(modifier: Modifier = Modifier) {
    val ext = LucentoTheme.extendedColors
    val strength = if (LucentoTheme.isDark) 0.38f else 0.62f
    val pulse = if (LucentoTheme.reducedMotion) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "torch")
        val value by transition.animateFloat(
            initialValue = 0.82f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2400), RepeatMode.Reverse),
            label = "torch-pulse",
        )
        value
    }
    Box(
        modifier.drawBehind {
            // The torch sits just inside the corner, a little left of the edge where the mascot holds it.
            val center = Offset(size.width - 34.dp.toPx(), 6.dp.toPx())
            val radius = size.width * 0.78f
            drawRect(
                Brush.radialGradient(
                    0f to ext.accent.copy(alpha = strength * pulse),
                    0.18f to ext.warmAccent.copy(alpha = strength * 0.55f * pulse),
                    0.55f to ext.accent.copy(alpha = strength * 0.16f * pulse),
                    1f to Color.Transparent,
                    center = center,
                    radius = radius,
                ),
            )
        },
    )
}

/** A thick bar with its percentage printed at the end of the fill (inside it when there is room). */
@Composable
private fun PercentBar(fraction: Float, label: String, color: Color, description: String) {
    val clamped = fraction.coerceIn(0f, 1f)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(16.dp)
            .clip(CircleShape)
            .background(LucentoTheme.extendedColors.track)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        val fill = maxWidth * clamped
        val inside = fill >= 96.dp
        Box(
            Modifier
                .width(fill)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (inside) {
                Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(end = 6.dp))
            }
        }
        if (!inside) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = fill + 6.dp),
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(Spacing.xs))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BudgetStat(value: String, label: String, modifier: Modifier = Modifier, valueLines: Int = 1) {
    Column(modifier.semantics(mergeDescendants = true) {}.padding(horizontal = Spacing.xs)) {
        Text(value, style = AmountStyles.medium, maxLines = valueLines)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
    }
}

/** Spent, remaining and days left. Stacked when the card is narrow or type is large, so nothing is clipped. */
@Composable
private fun BudgetFigures(plan: BudgetPlan, burn: BudgetBurn) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stack = maxWidth < 360.dp || LocalDensity.current.fontScale >= 1.3f
        if (stack) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (plan.hasBudget) {
                    MoneyStat(plan.spent, "Spent", Modifier.fillMaxWidth())
                    MoneyStat(
                        if (plan.remaining.isNegative) -plan.remaining else plan.remaining,
                        if (plan.remaining.isNegative) "Over budget" else "Remaining",
                        Modifier.fillMaxWidth(),
                        valueColor = if (plan.remaining.isNegative) LucentoTheme.extendedColors.negative else MaterialTheme.colorScheme.onSurface,
                    )
                }
                BudgetStat("${burn.daysLeft}", "days left", Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                if (plan.hasBudget) {
                    MoneyStat(plan.spent, "Spent", Modifier.weight(1f))
                    MoneyStat(
                        if (plan.remaining.isNegative) -plan.remaining else plan.remaining,
                        if (plan.remaining.isNegative) "Over budget" else "Remaining",
                        Modifier.weight(1f),
                        valueColor = if (plan.remaining.isNegative) LucentoTheme.extendedColors.negative else MaterialTheme.colorScheme.onSurface,
                    )
                }
                BudgetStat("${burn.daysLeft}", "days left", Modifier.weight(0.8f))
            }
        }
    }
}

/** A money figure that shrinks to fit a narrow column instead of being cut off. */
@Composable
private fun MoneyStat(money: Money, label: String, modifier: Modifier = Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier.semantics(mergeDescendants = true) {}.padding(horizontal = Spacing.xs)) {
        MoneyText(money = money, style = AmountStyles.medium, color = valueColor, fractionScale = 1f, minScale = 0.6f)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
    }
}

/** The verdict as advice, with a glyph that matches it (up / check / info) and a way into the Budgets tab. */
@Composable
private fun AdviceBanner(text: String, detail: String?, tones: PaceTones, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.minTouch)
            .clip(MaterialTheme.shapes.medium)
            // By day the card's tint is deep, so the banner sits on a frosted light panel rather than the same tint.
            .background(if (LucentoTheme.isDark) tones.banner.container else MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.82f))
            .clickable(role = Role.Button, onClickLabel = "Open budget", onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(if (LucentoTheme.isDark) Color.Black.copy(alpha = 0.2f) else tones.banner.container),
            contentAlignment = Alignment.Center,
        ) {
            Icon(tones.icon, contentDescription = null, tint = tones.banner.content, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(Spacing.sm))
        // Two one-liners: the verdict, then the daily allowance that keeps the month on budget.
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 3)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
            }
        }
    }
}

/** Shown in place of the budget card when Budgets is switched off: the month's money in and out. */
@Composable
private fun CashFlowCard(state: HomeUiState, onOpenInsights: (() -> Unit)?) {
    val flow = state.netFlow ?: return
    val ext = LucentoTheme.extendedColors
    LucentoCard(Modifier.fillMaxWidth(), onClick = onOpenInsights, contentPadding = Spacing.lg) {
        CardHeader(title = "Net flow", context = "${monthLabel(flow.month)} · ${state.focusCurrency}")
        Spacer(Modifier.height(Spacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MoneyText(
                money = flow.net,
                style = AmountStyles.large,
                showPlus = true,
                color = when (state.flowDirection) {
                    FlowDirection.POSITIVE -> ext.positive
                    FlowDirection.NEGATIVE -> ext.negative
                    FlowDirection.NEUTRAL -> MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f),
            )
            Column(horizontalAlignment = Alignment.End) {
                AmountText(flow.income, style = AmountStyles.small, showPlus = true, color = ext.positive)
                AmountText(-flow.expense, style = AmountStyles.small, color = ext.negative)
            }
        }
    }
}

// ------------------------------------------------------------------ Net worth and quick links

/**
 * Two cards side by side: **Net worth** (opens Wallets) and **Quick links**. Compact type keeps both readable at half
 * width; on very narrow screens (under ~300dp) they stack.
 */
@Composable
private fun NetWorthAndQuickLinks(state: HomeUiState, composition: NetWorthComposition, actions: HomeActions) {
    val f = state.features
    val links = buildList {
        if (f.isEnabled(Feature.SAVINGS_GOALS)) add(QuickLink("Goals", "Savings goals", Icons.Outlined.Savings, actions.onOpenGoals))
        if (f.isEnabled(Feature.RECURRING)) add(QuickLink("Bills", "Bills and subscriptions", Icons.Outlined.EventRepeat) { actions.onOpenRecurring(null) })
        if (f.isEnabled(Feature.DEBTS_LOANS)) add(QuickLink("Debts", "Debts and loans", Icons.Outlined.CreditCard, actions.onOpenDebts))
        // Budget and Wallets have their own places in the dock; Insights opens from here.
        if (f.isEnabled(Feature.INSIGHTS)) add(QuickLink("Insights", "Insights", Icons.Outlined.Insights, actions.onOpenInsights))
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 300.dp) {
            // The links card's share of the row (1 of 2.1 weights) minus its padding: too narrow for two tiles across
            // on small phones, where the links become a list. Worked out here because the row below measures its
            // children's intrinsic height, which a nested BoxWithConstraints cannot provide.
            val linksContentWidth = (maxWidth - Spacing.sm) / 2.1f - Spacing.md * 2
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.Top) {
                FlowAndWorthCard(state, composition, actions, Modifier.weight(1.1f))
                QuickLinksCard(links, asList = linksContentWidth < 140.dp || LocalDensity.current.fontScale >= 1.3f, modifier = Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                FlowAndWorthCard(state, composition, actions, Modifier.fillMaxWidth())
                QuickLinksCard(links, asList = false, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * One card, two tappable halves: **Net flow** for the shown month on top (opens Insights) and **Net worth** today below
 * (opens Wallets).
 */
@Composable
private fun FlowAndWorthCard(state: HomeUiState, composition: NetWorthComposition, actions: HomeActions, modifier: Modifier) {
    val ext = LucentoTheme.extendedColors
    val insightsOn = state.features.isEnabled(Feature.INSIGHTS)
    LucentoCard(modifier, contentPadding = 0.dp) {
        state.netFlow?.let { flow ->
            CardHalf(onClick = if (insightsOn) actions.onOpenInsights else null, clickLabel = "Open Insights") {
                LinkedTitle("Net flow", compact = true, showChevron = insightsOn)
                MoneyText(
                    money = flow.net,
                    style = AmountStyles.large,
                    showPlus = true,
                    fractionScale = 0.75f,
                    minScale = 0.55f,
                    color = when (state.flowDirection) {
                        FlowDirection.POSITIVE -> ext.positive
                        FlowDirection.NEGATIVE -> ext.negative
                        FlowDirection.NEUTRAL -> MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    "${monthLabel(flow.month)} · money in minus money out",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            Hairline()
        }
        CardHalf(onClick = actions.onOpenWallets, clickLabel = "Open wallets") {
            LinkedTitle("Net worth", compact = true)
            Column(
                Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "Net worth ${MoneyFormatter.format(composition.netWorth)}: assets ${MoneyFormatter.format(composition.totalAssets)} " +
                        "minus liabilities ${MoneyFormatter.format(composition.totalLiabilities)}"
                },
            ) {
                MoneyText(money = composition.netWorth, style = AmountStyles.large, fractionScale = 0.75f, minScale = 0.55f, color = MaterialTheme.colorScheme.primary)
                Text("Today · assets minus debts", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                state.netWorthMonthChange?.let { ChangeChip(it, Modifier.padding(top = Spacing.xs)) }
            }
            state.otherNetWorth.forEach { other ->
                Text(
                    "${other.currencyCode} ${MoneyFormatter.formatSigned(other)} (separate)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CardHalf(onClick: (() -> Unit)?, clickLabel: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = clickLabel, onClick = onClick) else Modifier)
            .padding(Spacing.md),
    ) { content() }
}

/** This month's movement from recorded income and spending: an arrow and a signed amount, never colour alone. */
@Composable
private fun ChangeChip(change: Money, modifier: Modifier = Modifier) {
    val up = !change.isNegative
    val tone = if (up) StatusTones.positive else StatusTones.negative
    Column(
        modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(tone.container)
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clearAndSetSemantics {
                contentDescription = "${MoneyFormatter.formatSigned(change, showPlus = true)} this month from recorded income and spending"
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (up) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward, contentDescription = null, tint = tone.content, modifier = Modifier.size(12.dp))
            Text(MoneyFormatter.formatSigned(change, showPlus = true), style = MaterialTheme.typography.labelSmall, color = tone.content, maxLines = 1)
        }
        Text("this month", style = MaterialTheme.typography.labelSmall, color = tone.content, maxLines = 1)
    }
}

private data class QuickLink(val label: String, val accessibilityLabel: String, val icon: ImageVector, val onClick: () -> Unit)

/**
 * Quick access to the pages around your money: a two-by-two grid of compact tiles, or — when the card is too narrow
 * for two tiles across (small phones) — a list of icon-and-label rows, so labels never break mid-word.
 */
@Composable
private fun QuickLinksCard(links: List<QuickLink>, asList: Boolean, modifier: Modifier) {
    LucentoCard(modifier, contentPadding = Spacing.md) {
        Text("Quick links", style = Hierarchy.cardTitle, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Spacing.sm))
        if (asList) {
            Column { links.forEach { QuickLinkRow(it) } }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                links.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.Top) {
                        pair.forEach { QuickLinkTile(it, Modifier.weight(1f)) }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** The raised, brand-tinted face shared by quick-link tiles and rows, so they read as buttons at a glance. */
@Composable
private fun Modifier.quickLinkFace(link: QuickLink, shape: androidx.compose.ui.graphics.Shape): Modifier {
    val colors = MaterialTheme.colorScheme
    return this
        .shadow(elevation = 2.dp, shape = shape, clip = false, ambientColor = LucentoTheme.extendedColors.shadow.copy(alpha = 0.08f), spotColor = LucentoTheme.extendedColors.shadow.copy(alpha = 0.16f))
        .clip(shape)
        .background(colors.primaryContainer)
        .border(BorderStroke(1.dp, colors.primary.copy(alpha = 0.22f)), shape)
        .clickable(role = Role.Button, onClickLabel = "Open ${link.accessibilityLabel}", onClick = link.onClick)
}

/** A solid brand-blue badge with a white glyph: the button's icon. */
@Composable
private fun QuickLinkBadge(link: QuickLink) {
    IconTile(
        icon = link.icon,
        size = 30.dp,
        container = MaterialTheme.colorScheme.primary,
        content = MaterialTheme.colorScheme.onPrimary,
    )
}

@Composable
private fun QuickLinkRow(link: QuickLink) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .heightIn(min = Sizes.minTouch)
            .quickLinkFace(link, MaterialTheme.shapes.small)
            .padding(horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QuickLinkBadge(link)
        Spacer(Modifier.width(Spacing.sm))
        Text(
            link.label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            modifier = Modifier.weight(1f).semantics { contentDescription = link.accessibilityLabel },
        )
    }
}

@Composable
private fun QuickLinkTile(link: QuickLink, modifier: Modifier = Modifier) {
    Column(
        modifier
            .heightIn(min = 72.dp)
            .quickLinkFace(link, MaterialTheme.shapes.medium)
            .padding(horizontal = 2.dp, vertical = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        QuickLinkBadge(link)
        Spacer(Modifier.height(Spacing.xs))
        Text(
            link.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            textAlign = TextAlign.Center,
            maxLines = 3,
            modifier = Modifier.semantics { contentDescription = link.accessibilityLabel },
        )
    }
}

// ------------------------------------------------------------------ Top savings goal

/** The goal closest to done (highest completion among goals still in progress): picture, name, %, bar, money, date. */
@Composable
private fun TopGoalCard(goals: GoalsOverview?, hasAnyGoals: Boolean, onOpen: () -> Unit) {
    val ext = LucentoTheme.extendedColors
    val top = goals?.top
    LucentoCard(Modifier.fillMaxWidth(), onClick = onOpen, contentPadding = Spacing.lg) {
        LinkedTitle("Top savings goal") {
            if (top != null) LucentoTextButton(text = "View all", onClick = onOpen)
        }
        if (top == null) {
            Text(
                if (hasAnyGoals) "Your goals are in other currencies — open Savings goals to see them."
                else "Set a target — a trip, a cushion, a new laptop — and watch it fill up.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!hasAnyGoals) LucentoTextButton(text = "Start a savings goal", onClick = onOpen)
            return@LucentoCard
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GoalThumbnail(top.goal.name, top.goal.sceneKey, top.goal.photoPath)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(top.goal.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, modifier = Modifier.weight(1f))
                    Text("${top.percent}%", style = AmountStyles.medium, color = ext.success)
                }
                Spacer(Modifier.height(Spacing.xs))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(ext.track)
                        .clearAndSetSemantics { contentDescription = "${top.goal.name}: ${top.percent}% saved" },
                ) {
                    Box(Modifier.fillMaxWidth(top.fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(ext.success))
                }
                Spacer(Modifier.height(Spacing.xs))
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        "${MoneyFormatter.format(top.saved)} of ${MoneyFormatter.format(top.goal.target)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    val formatter = remember { DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault()) }
                    val date = top.goal.targetDate
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            when {
                                top.reached -> "Reached"
                                date != null -> "Target"
                                top.projectedCompletion != null -> "At your pace"
                                else -> "No target date"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        (date ?: top.projectedCompletion)?.takeIf { !top.reached }?.let {
                            Text(it.format(formatter), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

/** The goal's own photo or card picture, small; a savings glyph when it has neither. Decorative. */
@Composable
private fun GoalThumbnail(name: String, sceneKey: String?, photoPath: String?) {
    val photo = rememberGoalPhoto(photoPath)
    val art = remember(name, sceneKey) { GoalScene.forGoal(name, sceneKey).illustration() }
    val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val modifier = Modifier
        .widthIn(min = 64.dp)
        .heightIn(min = 56.dp)
        .size(width = 64.dp * scale, height = 56.dp * scale)
        .clip(MaterialTheme.shapes.small)
    when {
        photo != null -> Image(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
        art != null -> Image(painterResource(art.res), contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
        else -> IconTile(icon = Icons.Outlined.Savings, size = 56.dp, container = MaterialTheme.colorScheme.primaryContainer, content = MaterialTheme.colorScheme.primary)
    }
}

// ------------------------------------------------------------------ Scheduled transactions

@Composable
private fun ScheduledCard(state: HomeUiState, onOpen: (Long?) -> Unit) {
    LucentoCard(Modifier.fillMaxWidth(), onClick = { onOpen(null) }, contentPadding = Spacing.lg) {
        LinkedTitle("Scheduled transactions") {
            if (state.hasAnySchedules) LucentoTextButton(text = "See all", onClick = { onOpen(null) })
        }
        if (state.upcoming.isEmpty()) {
            Spacer(Modifier.height(Spacing.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(icon = Icons.Outlined.EventRepeat)
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (state.hasAnySchedules) "Nothing due in the next 30 days" else "Nothing scheduled",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        if (state.hasAnySchedules) "You’re all caught up." else "Add rent, bills, subscriptions or salary and they show up here before they’re due.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!state.hasAnySchedules) LucentoTextButton(text = "Schedule a transaction", onClick = { onOpen(null) })
            return@LucentoCard
        }
        state.upcoming.forEachIndexed { index, item ->
            ScheduledRow(item, state, onClick = { onOpen(item.schedule.id) })
            if (index < state.upcoming.lastIndex) Hairline(inset = Sizes.iconTile + Spacing.md)
        }
        Text(
            "Nothing is recorded until you mark it as paid or received.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

@Composable
private fun ScheduledRow(item: UpcomingPayment, state: HomeUiState, onClick: () -> Unit) {
    val schedule = item.schedule
    val category = schedule.categoryId?.let { state.categoriesById[it] }
    val days = ChronoUnit.DAYS.between(state.today, item.dueDate)
    val relative = when {
        days == 0L -> "Today"
        days == 1L -> "Tomorrow"
        days in 2L..6L -> "In $days days"
        else -> null
    }
    val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()) }
    val income = schedule.type == TransactionType.INCOME
    val kind = category?.name ?: if (income) "Recurring income" else "Recurring expense"
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(role = Role.Button, onClickLabel = "Review ${schedule.name}", onClick = onClick)
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryTile(category = category, type = schedule.type)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(schedule.name, style = MaterialTheme.typography.titleSmall, maxLines = 2)
            Text(kind, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        Spacer(Modifier.width(Spacing.sm))
        Column(horizontalAlignment = Alignment.End) {
            when {
                item.overdue -> StatusPill("Overdue", StatusTones.negative)
                relative != null -> Text(relative, style = MaterialTheme.typography.labelMedium)
            }
            Text(item.dueDate.format(dateFormat), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(Spacing.md))
        AmountText(
            money = if (income) schedule.amount else -schedule.total,
            showPlus = true,
            color = if (income) LucentoTheme.extendedColors.positive else MaterialTheme.colorScheme.onSurface,
        )
    }
}

internal fun monthLabel(month: YearMonth, locale: Locale = Locale.getDefault()): String =
    month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) }

private fun monthYearLabel(month: YearMonth, locale: Locale = Locale.getDefault()): String = "${monthLabel(month, locale)} ${month.year}"

@Preview(name = "Home 1.3 × 360", fontScale = 1.3f, widthDp = 360, heightDp = 800)
@Preview(name = "Home 2.0 × 360", fontScale = 2.0f, widthDp = 360, heightDp = 900)
@Composable
private fun HomeFontScalePreview() {
    LucentoTheme {
        val today = LocalDate.of(2026, 10, 28)
        val wallet = com.lucento.app.domain.Wallet(1, "Cash", com.lucento.app.domain.WalletType.CASH, Money(120_00, "USD"), 0)
        val groceries = com.lucento.app.domain.Category(1, "Groceries", com.lucento.app.domain.CategoryKind.EXPENSE, "groceries")
        val entry = com.lucento.app.domain.Transaction(1, TransactionType.EXPENSE, Money(4_50, "USD"), 1, 1, today, "Coffee", 0)
        val budget = com.lucento.app.domain.Budget(1, 1, Money(80_00, "USD"))
        val goal = com.lucento.app.domain.Goal(1, "Emergency fund", Money(500_00, "USD"), null, 1, 0)
        val state = HomeStateBuilder.build(
            all = listOf(entry),
            wallets = listOf(wallet),
            categories = listOf(groceries),
            primaryCurrency = "USD",
            today = today,
            budgets = listOf(budget),
            goals = listOf(goal),
        ).copy(dayOneHome = true, celebrateFirstEntry = true)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter),
        ) {
            Overview(
                state = state,
                actions = HomeActions(
                    onOpenBudget = {},
                    onChooseCategories = {},
                    onOpenInsights = {},
                    onOpenGoals = {},
                    onOpenRecurring = {},
                ),
                onSelectMonth = {},
                onDismissLocalDataNotice = {},
                onDismissFirstWeek = {},
                onSnoozeSuggestion = {},
                onUseSuggestion = {},
                onCelebrateShown = {},
            )
        }
    }
}
