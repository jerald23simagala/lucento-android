package com.lucento.app.feature.home

import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material.icons.outlined.Category
import com.lucento.app.core.designsystem.component.icon
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.History
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.ExperimentalMaterial3Api
import com.lucento.app.core.designsystem.component.Confirmation
import com.lucento.app.core.designsystem.component.ConfirmationHost
import com.lucento.app.core.designsystem.component.HeroCard
import com.lucento.app.core.designsystem.component.HeroPill
import com.lucento.app.core.designsystem.component.HeroTones
import com.lucento.app.core.designsystem.component.rememberHaptics
import kotlin.math.roundToInt
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
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material.icons.outlined.CloudSync
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Sizes
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.designsystem.theme.TonePair
import com.lucento.app.core.money.Money
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.domain.BudgetBurn
import com.lucento.app.domain.BudgetPlan
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
    val onOpenSettings: () -> Unit = {},
    /** Settings › Account & sync, from the sync details sheet (P2-2). */
    val onOpenAccountSettings: () -> Unit = onOpenSettings,
    /** P2-1: recent activity — the full list, and one entry's page. */
    val onOpenTransactions: () -> Unit = {},
    val onViewTransaction: (com.lucento.app.domain.Transaction) -> Unit = {},
    /** Part E quick actions: the composer (optionally as income), a transfer, and the receipt scanner (null when off). */
    val onAddTransaction: (TransactionType?) -> Unit = {},
    val onTransfer: () -> Unit = {},
    val onScanReceipt: (() -> Unit)? = null,
    /** A "top spending" tile: that category's transactions in the shown month. */
    val onOpenCategory: (Long, YearMonth) -> Unit = { _, _ -> },
)

/**
 * The dashboard, top to bottom: a pinned header ("Dashboard", sync status, payments bell, profile) and month selector;
 * the net wallet balance card; the month's budget with its top two categories; shortcuts; getting started (only until
 * the basics are in place); upcoming scheduled transactions; recent transactions. Every month-dependent section follows
 * the one selected month. Every figure comes from [HomeUiState].
 */
@Composable
fun HomeScreen(
    actions: HomeActions,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
    walletsViewModel: WalletsViewModel = viewModel(factory = WalletsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editingWallet by remember { mutableStateOf<WalletDraft?>(null) }
    var showSync by remember { mutableStateOf(false) }
    var showMilestones by remember { mutableStateOf(false) }
    var celebration by remember { mutableStateOf<Confirmation?>(null) }

    // Part D: milestones. The first time they exist, what's already earned is recorded quietly; after that each new
    // one is acknowledged once — this runs only when the stored records change, i.e. after a committed write.
    LaunchedEffect(state.loading, state.needsMilestoneBaseline, state.earned) {
        if (state.loading) return@LaunchedEffect
        if (state.needsMilestoneBaseline) viewModel.baselineMilestones(state.earned)
    }
    // Anyone who has recorded something doesn't need the + spotlight (also clears it for people who record from elsewhere).
    LaunchedEffect(state.loading, state.recordedCount > 0) {
        if (!state.loading && state.recordedCount > 0) viewModel.dismissAddSpotlight()
    }
    LaunchedEffect(state.milestoneToShow) {
        val milestone = state.milestoneToShow ?: return@LaunchedEffect
        celebration = Confirmation("Milestone: ${milestone.title}", kind = Confirmation.Kind.Milestone)
        viewModel.acknowledgeMilestone(milestone)
    }

    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize()) {
        // Pinned: the brand, sync status, notifications and profile stay put while the dashboard scrolls beneath.
        HomeHeader(
            status = state.cloudStatus,
            onOpenSyncSettings = { showSync = true },
            bell = if (state.features.isEnabled(Feature.RECURRING)) ({ PaymentsBell(state, actions.onOpenRecurring) }) else null,
            modifier = Modifier.padding(horizontal = Spacing.gutter),
        )
        if (!state.loading && state.hasWallets) {
            // Pinned with the header, so the month every section describes is always in view.
            Row(
                Modifier.fillMaxWidth().padding(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Overview",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                MonthPill(state, viewModel::selectMonth)
            }
        }
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
                state.loading -> HomeContent.Loading
                !state.hasWallets -> HomeContent.Welcome
                else -> HomeContent.Overview
            },
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
            label = "home-content",
        ) { content ->
            when (content) {
                HomeContent.Loading -> HomeSkeleton()
                HomeContent.Welcome -> WelcomePanel(
                    onAddWallet = { editingWallet = WalletDraft(currencyCode = state.primaryCurrency) },
                )
                HomeContent.Overview -> Overview(
                    state = state,
                    actions = actions,
                    onSelectMonth = viewModel::selectMonth,
                    onDismissLocalDataNotice = viewModel::dismissLocalDataNotice,
                    onDismissOverridden = viewModel::dismissOverridden,
                    onTurnOn = viewModel::turnOn,
                    onHideOffers = viewModel::hideModuleOffers,
                    onAddWallet = { editingWallet = WalletDraft(currencyCode = state.primaryCurrency) },
                    onOpenMilestones = { showMilestones = true },
                    onDismissSpotlight = viewModel::dismissAddSpotlight,
                )
            }
        }
        // Breathing room above the dock and its raised + button.
        Spacer(Modifier.height(Spacing.xl))
        }
    }

    if (showSync) {
        SyncDetailsSheet(
            status = state.cloudStatus,
            details = state.syncDetails,
            onSyncNow = viewModel::syncNow,
            onOpenAccountSettings = {
                showSync = false
                actions.onOpenAccountSettings()
            },
            onDismissOverridden = viewModel::dismissOverridden,
            onDismiss = { showSync = false },
        )
    }

    editingWallet?.let { draft ->
        WalletEditorSheet(
            initial = draft,
            onSave = walletsViewModel::save,
            onDelete = walletsViewModel::delete,
            onDismiss = { editingWallet = null },
        )
    }
    if (showMilestones) MilestonesSheet(state.earned, onDismiss = { showMilestones = false })
    Box(Modifier.fillMaxSize()) {
        ConfirmationHost(confirmation = celebration, onDismissed = { celebration = null }, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

private enum class HomeContent { Loading, Welcome, Overview }

// ------------------------------------------------------------------ Header

/** "Dashboard" on the left; sync status, the payments bell (when Scheduled is on) and the profile entry on the right. */
@Composable
private fun HomeHeader(
    status: CloudStatus,
    onOpenSyncSettings: () -> Unit,
    bell: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Sizes.topBar)
                .padding(top = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Dashboard",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
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
                            Text(item.schedule.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

/** A compact chip: icon plus a short state, so the meaning never relies on colour. Opens the sync details sheet (P2-2). */
@Composable
private fun SyncStatusChip(status: CloudStatus, onClick: () -> Unit) {
    val open = onClick
    val tone: TonePair
    val icon: ImageVector
    when (status) {
        CloudStatus.LOCAL -> { tone = StatusTones.neutral; icon = Icons.Outlined.PhoneAndroid }
        CloudStatus.SYNCED -> { tone = StatusTones.positive; icon = Icons.Outlined.CloudDone }
        CloudStatus.PENDING -> { tone = StatusTones.warning; icon = Icons.Outlined.CloudUpload }
        CloudStatus.SYNCING -> { tone = StatusTones.brand; icon = Icons.Outlined.CloudSync }
        CloudStatus.OFFLINE -> { tone = StatusTones.neutral; icon = Icons.Outlined.CloudOff }
        CloudStatus.ERROR -> { tone = StatusTones.negative; icon = Icons.Outlined.SyncProblem }
    }
    Box(
        Modifier
            .heightIn(min = Sizes.minTouch)
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClickLabel = "Show sync details", onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = "Sync status: ${status.label}"
                role = Role.Button
                onClick(label = "Show sync details") { open(); true }
            },
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
            // At large text sizes the icon alone keeps the header readable; the status is still announced in full.
            if (!largeText()) {
                Spacer(Modifier.width(Spacing.xs))
                Text(status.label, style = MaterialTheme.typography.labelMedium, color = tone.content, maxLines = 1)
            }
        }
    }
}

/** The month selector: a compact white pill (calendar · month · chevron) with a menu of the months that have data. */
@Composable
private fun MonthPill(state: HomeUiState, onSelect: (YearMonth) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .heightIn(min = Sizes.minTouch)
                .clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClickLabel = "Choose month") { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .border(BorderStroke(1.dp, LucentoTheme.extendedColors.borderDefault), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(com.lucento.app.R.drawable.ic_dash_calendar_days), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(7.dp))
                Text(monthYearLabel(state.month), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Spacer(Modifier.width(7.dp))
                Icon(painterResource(com.lucento.app.R.drawable.ic_dash_chevron_down), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
            }
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
private fun Overview(
    state: HomeUiState,
    actions: HomeActions,
    onSelectMonth: (YearMonth) -> Unit,
    onDismissLocalDataNotice: () -> Unit,
    onDismissOverridden: () -> Unit = {},
    onTurnOn: (Feature) -> Unit = {},
    onHideOffers: () -> Unit = {},
    onAddWallet: () -> Unit = {},
    onOpenMilestones: () -> Unit = {},
    onDismissSpotlight: () -> Unit = {},
) {
    val f = state.features
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        // P2-6: said only when one of this phone's own changes lost to a newer one from another device.
        overriddenNotice(state.syncDetails.overriddenChanges)?.let { notice ->
            InfoNote(notice, actionLabel = "Got it", onAction = onDismissOverridden)
        }
        if (state.showLocalDataNotice) {
            InfoNote(
                "Your wallets, transactions, budgets and goals stay only on this phone unless cloud sync is on or you export a backup. " +
                    "Uninstalling Lucento or clearing its storage erases anything that isn’t saved that way.",
                actionLabel = "Dismiss",
                onAction = onDismissLocalDataNotice,
            )
        }
        if (state.showAddSpotlight) {
            AddSpotlightCard(onRecord = { actions.onAddTransaction(null) }, onDismiss = onDismissSpotlight)
        }

        state.monthComposition?.let { WalletBalanceCard(state, it, actions.onOpenWallets) }

        val plan = state.budget
        val burn = state.burn
        if (plan != null && burn != null) {
            MonthlyBudgetCard(
                state, plan, burn,
                open = if (state.expenseCategoryCount == 0) actions.onChooseCategories else actions.onOpenBudget,
                onOpenCategory = { actions.onOpenCategory(it, state.month) },
            )
        } else {
            CashFlowCard(state, onOpenInsights = if (f.isEnabled(Feature.INSIGHTS)) actions.onOpenInsights else null)
        }

        ShortcutRow(state, actions, onOpenMilestones, onTurnOn, onHideOffers)

        if (state.gettingStartedLeft.isNotEmpty()) {
            GettingStartedCard(
                left = state.gettingStartedLeft,
                onAddWallet = onAddWallet,
                onBudget = if (state.expenseCategoryCount == 0) actions.onChooseCategories else actions.onOpenBudget,
                onRecord = { actions.onAddTransaction(null) },
            )
        }

        if (f.isEnabled(Feature.RECURRING) && state.isCurrentMonth) UpcomingScheduledCard(state, actions.onOpenRecurring)

        RecentTransactionsCard(state, actions)
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

/** A thick bar with its percentage printed at the end of the fill (inside it when there is room). */
@Composable
private fun PercentBar(fraction: Float, label: String, color: Color, description: String) {
    val clamped = fraction.coerceIn(0f, 1f)
    // The bar grows with the label's line height, so large font sizes don't clip the percentage (P1-7).
    val labelLine = MaterialTheme.typography.labelSmall.lineHeight
    val height = if (labelLine.isSp) maxOf(16.dp, with(LocalDensity.current) { labelLine.toDp() }) else 16.dp
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(height)
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

/** True when the system font size is large enough (150%+) that side-by-side figures and one-line copy stop fitting. */
@Composable
private fun largeText(): Boolean = LocalDensity.current.fontScale >= 1.5f

@Composable
private fun BudgetStat(value: String, label: String, modifier: Modifier = Modifier, valueLines: Int = 1) {
    Column(modifier.semantics(mergeDescendants = true) {}.padding(horizontal = Spacing.xs)) {
        Text(value, style = AmountStyles.medium, maxLines = valueLines, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** A money figure that shrinks to fit a narrow column instead of being cut off. */
@Composable
private fun MoneyStat(money: Money, label: String, modifier: Modifier = Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier.semantics(mergeDescendants = true) {}.padding(horizontal = Spacing.xs)) {
        MoneyText(money = money, style = AmountStyles.medium, color = valueColor, fractionScale = 1f, minScale = 0.7f)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun StatDivider() {
    Box(
        Modifier
            .padding(horizontal = Spacing.xs)
            .width(1.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
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
                fractionScale = 1f,
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

// ------------------------------------------------------------------ Shortcuts

private fun quickLinkName(feature: Feature): String = when (feature) {
    Feature.SAVINGS_GOALS -> "Goals"
    Feature.RECURRING -> "Scheduled"
    Feature.DEBTS_LOANS -> "Debts"
    Feature.INSIGHTS -> "Insights"
    else -> feature.title
}

// ------------------------------------------------------------------ Dashboard: net wallet balance card

// The card's own face (Figma "Net wallet balance"): the same in light and dark themes, like a physical card.
private val CardBlue = Color(0xFF2768ED)
private val CardNavy = Color(0xFF162D88)
private val CardInk = Color.White
private val TrendUp = Color(0xFFA7F3C5)
private val TrendDown = Color(0xFFFFC2B8)
private val CardFigureStyle = AmountStyles.large.copy(fontSize = 20.sp, letterSpacing = (-0.5).sp)
private val CardInkMuted = Color(0xFFC9D7FF)

/**
 * The dashboard's focal point, drawn as a physical card: ID-1 proportions (it only grows for large text), a soft drop
 * shadow, a thin light edge, a glossy sheen across the top and a faint guilloché pattern like printed security lines.
 * Label and amount sit together at the top; Wallets and Debt & Loans along the bottom edge. Opens Wallets.
 */
@Composable
private fun WalletBalanceCard(state: HomeUiState, composition: NetWorthComposition, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val owed = -composition.totalLiabilities
    val label = if (state.isCurrentMonth) "Net Wallet Balance" else "Net Wallet Balance · end of ${monthLabel(state.month)}"
    val history = state.balanceHistory
    val spoken = buildString {
        append("$label ${MoneyFormatter.formatSigned(composition.netWorth)}. ")
        append("Wallets ${MoneyFormatter.formatSigned(composition.totalAssets)}. ")
        append("Debt and loans ${MoneyFormatter.formatSigned(owed)}. ")
        if (history.size > 1) {
            append("Compared with the end of ${monthLabel(history.first().month)}: ")
            append(MoneyFormatter.formatSigned(history.last().netWorth - history.first().netWorth, showPlus = true))
            state.balanceTrend?.let { append(", ${if (it >= 0) "up" else "down"} ${kotlin.math.abs(it)} percent") }
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cardWidth = maxWidth
        val cardHeight = cardWidth / CardRatioId1
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = cardHeight)
                .shadow(12.dp, shape, ambientColor = CardNavy.copy(alpha = 0.35f), spotColor = CardNavy.copy(alpha = 0.55f))
                .clip(shape)
                .background(Brush.linearGradient(listOf(CardBlue, CardNavy), start = Offset.Zero, end = Offset.Infinite))
                .clickable(role = Role.Button, onClickLabel = "Open wallets", onClick = onOpen),
        ) {
            // The Figma texture disc, then the printed surface. matchParentSize: neither makes the card taller.
            Box(Modifier.matchParentSize()) {
                Image(
                    painterResource(com.lucento.app.R.drawable.bg_dash_card_texture),
                    contentDescription = null,
                    modifier = Modifier
                        .wrapContentSize(Alignment.TopStart, unbounded = true)
                        .offset(x = cardWidth * 0.58f, y = (-90).dp)
                        .size(285.dp),
                )
            }
            Canvas(Modifier.matchParentSize()) { drawCardSurface() }
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = cardHeight)
                    // The header row is a 48dp touch target, so a small top inset reads the same as the bottom one.
                    .padding(start = Spacing.md, end = Spacing.md, top = 0.dp, bottom = Spacing.md),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Label and (i) together on the left; the brand stays on the right.
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = CardInkMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false).clearAndSetSemantics { contentDescription = spoken },
                            )
                            BalanceInfo()
                        }
                        Spacer(Modifier.width(Spacing.xs))
                        Image(painterResource(com.lucento.app.R.drawable.lucento_symbol), contentDescription = null, modifier = Modifier.height(22.dp))
                        Spacer(Modifier.width(5.dp))
                        Image(painterResource(com.lucento.app.R.drawable.lucento_wordmark_dark), contentDescription = null, modifier = Modifier.height(15.dp))
                    }
                    // Right under its label: one reading, "Net Wallet Balance −$2,339.35".
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MoneyText(
                            money = composition.netWorth,
                            style = AmountStyles.hero,
                            color = CardInk,
                            fractionScale = 1f,
                            minScale = 0.5f,
                            modifier = Modifier.weight(1f),
                        )
                        if (history.size > 1) {
                            Spacer(Modifier.width(Spacing.sm))
                            BalanceTrend(history, state.balanceTrend)
                        }
                    }
                }
                // Where a real card carries its chip: the chip and the contactless mark (decorative, no card number).
                Row(Modifier.padding(vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    com.lucento.app.core.designsystem.component.CardChip()
                    Spacer(Modifier.width(Spacing.sm))
                    Icon(Icons.Filled.Contactless, contentDescription = null, tint = CardInkMuted.copy(alpha = 0.85f), modifier = Modifier.size(22.dp))
                }
                Column {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(CardInk.copy(alpha = 0.14f)))
                    Spacer(Modifier.height(Spacing.sm))
                    val textScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
                    if (cardWidth >= 330.dp * textScale) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            CardFigure(com.lucento.app.R.drawable.ic_dash_wallet, 46.dp, "Wallets", composition.totalAssets, Modifier.weight(1f))
                            CardFigure(com.lucento.app.R.drawable.ic_dash_layers, 38.dp, "Debt & Loans", owed, Modifier.weight(1f))
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            CardFigure(com.lucento.app.R.drawable.ic_dash_wallet, 46.dp, "Wallets", composition.totalAssets, Modifier.fillMaxWidth())
                            CardFigure(com.lucento.app.R.drawable.ic_dash_layers, 38.dp, "Debt & Loans", owed, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

/** ISO/IEC 7810 ID-1 (85.60 × 53.98 mm): the proportions of a real bank card. */
private const val CardRatioId1 = 1.586f

/**
 * The printed surface of the card, all very faint so nothing competes with the figures: a guilloché fan of fine
 * concentric lines from the bottom-right corner, a glossy sheen across the top third, and a thin light edge.
 */
private fun DrawScope.drawCardSurface() {
    val corner = CornerRadius(20.dp.toPx())
    val line = 0.8.dp.toPx()
    val centre = Offset(size.width * 1.02f, size.height * 1.18f)
    for (i in 0 until 18) {
        drawCircle(
            Color.White.copy(alpha = 0.045f),
            radius = size.height * (0.42f + i * 0.065f),
            center = centre,
            style = Stroke(line),
        )
    }
    drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.13f), 0.42f to Color.Transparent))
    drawRoundRect(
        Color.White.copy(alpha = 0.18f),
        topLeft = Offset(line / 2, line / 2),
        size = Size(size.width - line, size.height - line),
        cornerRadius = corner,
        style = Stroke(line),
    )
}

/** The (i) beside the label: what the figure means, one tap away (tooltip; also on long-press). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BalanceInfo() {
    val tip = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text("What you have in your wallets minus what you owe on cards and loans. Nothing is converted between currencies.") } },
        state = tip,
    ) {
        IconButton(onClick = { scope.launch { tip.show() } }, modifier = Modifier.size(Sizes.minTouch)) {
            Icon(painterResource(com.lucento.app.R.drawable.ic_dash_info), contentDescription = "What net wallet balance means", tint = Color.Unspecified, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun CardFigure(@androidx.annotation.DrawableRes icon: Int, iconWidth: Dp, label: String, money: Money, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(width = iconWidth, height = 42.dp))
        Spacer(Modifier.width(7.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = CardInkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))
            MoneyText(money = money, style = CardFigureStyle, color = CardInk, fractionScale = 1f, minScale = 0.55f)
        }
    }
}

/** This month against last: two small bars, the change in percent (or amount), and "vs last month". */
@Composable
private fun BalanceTrend(history: List<com.lucento.app.domain.MonthBalance>, trend: Int?) {
    val values = history.map { it.netWorth.amountMinor }
    val min = values.min()
    val max = values.max()
    val barColors = listOf(Color(0xFF89C4FF), Color(0xFFC2E2FF))
    Column(horizontalAlignment = Alignment.End) {
        Row(Modifier.height(28.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
            values.forEachIndexed { i, v ->
                val share = if (max == min) 1f else (v - min).toFloat() / (max - min)
                Box(
                    Modifier
                        .size(width = 12.dp, height = 12.dp + 16.dp * share)
                        .clip(RoundedCornerShape(3.dp))
                        .background(barColors[(barColors.size - values.size + i).coerceIn(0, barColors.lastIndex)]),
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        if (trend != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(com.lucento.app.R.drawable.ic_dash_trending_up),
                    contentDescription = null,
                    tint = if (trend >= 0) TrendUp else TrendDown,
                    modifier = Modifier.size(14.dp).graphicsLayer { if (trend < 0) scaleY = -1f },
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    "${if (trend >= 0) "+" else "−"}${kotlin.math.abs(trend)}%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (trend >= 0) TrendUp else TrendDown,
                    maxLines = 1,
                )
            }
        } else {
            MoneyText(money = history.last().netWorth - history.first().netWorth, style = MaterialTheme.typography.labelLarge, color = CardInk, showPlus = true, fractionScale = 1f, minScale = 0.5f)
        }
        Text("vs last month", style = MaterialTheme.typography.labelSmall, color = CardInkMuted, maxLines = 1)
    }
}

@Composable
private fun CardFigure(icon: ImageVector, label: String, money: Money, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = CardInkMuted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Spacing.xs))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = CardInkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            MoneyText(money = money, style = AmountStyles.row, color = CardInk, fractionScale = 1f, minScale = 0.6f)
        }
    }
}





// ------------------------------------------------------------------ Dashboard: monthly budget

@Composable
private fun MonthlyBudgetCard(
    state: HomeUiState,
    plan: BudgetPlan,
    burn: BudgetBurn,
    open: () -> Unit,
    onOpenCategory: (Long) -> Unit,
) {
    val setupNeeded = state.expenseCategoryCount == 0
    val insight = remember(plan, burn, state.isCurrentMonth) { PaceCopy.describe(plan, burn, monthLabel(plan.month), state.isCurrentMonth) }
    val tones = paceTones(insight.position)
    val over = plan.remaining.isNegative
    LucentoCard(Modifier.fillMaxWidth(), onClick = open, contentPadding = Spacing.lg) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(7.dp))
            Text("Monthly Budget", style = Hierarchy.cardTitle, modifier = Modifier.weight(1f).semantics { heading() }, maxLines = 2)
            if (!setupNeeded && plan.hasBudget && !largeText()) VerdictChip(verdictLabel(plan, insight.position, state.isCurrentMonth), tones.banner)
        }
        if (!setupNeeded && plan.hasBudget && largeText()) {
            Spacer(Modifier.height(Spacing.xs))
            VerdictChip(verdictLabel(plan, insight.position, state.isCurrentMonth), tones.banner)
        }
        Spacer(Modifier.height(Spacing.md))
        when {
            setupNeeded -> {
                EmptyCardMessage("Choose the categories you spend on — your budget is built from them.")
                LucentoTextButton(text = "Choose categories", onClick = open)
            }
            !plan.hasBudget -> {
                EmptyCardMessage("No budget for ${monthLabel(plan.month)} yet. Give your categories an amount to see your pace.")
                LucentoTextButton(text = "Set amounts", onClick = open)
            }
            else -> {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        MoneyText(money = plan.spent, style = AmountStyles.hero, fractionScale = 1f, minScale = 0.55f)
                        Spacer(Modifier.height(Spacing.xxs))
                        Text("of ${MoneyFormatter.format(plan.total)} budget", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "${burn.consumedPercent ?: 0}%",
                            style = AmountStyles.large,
                            color = if (over) LucentoTheme.extendedColors.negative else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                        Text("used", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(10.dp))
                BudgetBar(burn.consumedFraction, null, tones.bar, "Budget used ${burn.consumedPercent ?: 0} percent")
            }
        }
        if (state.topCategories.isNotEmpty() && !setupNeeded) {
            Spacer(Modifier.height(Spacing.md))
            Text(
                "Top ${state.topCategories.size} spending ${if (state.topCategories.size == 1) "category" else "categories"}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                modifier = Modifier.padding(bottom = Spacing.xs),
            )
            val textScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth >= 300.dp * textScale) {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        state.topCategories.forEach { slice -> CategoryWatchTile(slice, state, onOpenCategory, Modifier.weight(1f).fillMaxHeight()) }
                        if (state.topCategories.size == 1) Spacer(Modifier.weight(1f))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        state.topCategories.forEach { slice -> CategoryWatchTile(slice, state, onOpenCategory, Modifier.fillMaxWidth()) }
                    }
                }
            }
        }
        if (state.otherBudgetCurrencies.isNotEmpty()) {
            Text(
                "Budgets in ${state.otherBudgetCurrencies.joinToString()} are on the Budget tab.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
    }
}

/** A card with nothing to show yet: Lucento on the left, one line of what to do on the right. */
@Composable
private fun EmptyCardMessage(text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        LucentoMascot(size = 56.dp, mood = MascotMood.Curious)
        Spacer(Modifier.width(Spacing.md))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
    }
}

/** The budget used as one 7dp bar; [monthGone] adds a thin marker where the month stands (unused on the dashboard). */
@Composable
private fun BudgetBar(used: Float, monthGone: Float?, color: Color, description: String) {
    val track = LucentoTheme.extendedColors.track
    val marker = MaterialTheme.colorScheme.onSurface
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(7.dp)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        val w = size.width * used.coerceIn(0f, 1f)
        if (w > 0f) drawRoundRect(color, size = Size(w.coerceAtLeast(size.height), size.height), cornerRadius = r)
        if (monthGone != null) {
            val x = size.width * monthGone.coerceIn(0f, 1f)
            drawLine(marker, Offset(x, -2.dp.toPx()), Offset(x, size.height + 2.dp.toPx()), strokeWidth = 2.dp.toPx())
        }
    }
}

@Composable
private fun CategoryWatchTile(slice: SpendingSlice, state: HomeUiState, onOpen: (Long) -> Unit, modifier: Modifier) {
    val category = slice.categoryId?.let { state.categoriesById[it] }
    val tone = category?.let { LucentoTheme.extendedColors.categoryTone(it.iconKey) }
    val percent = (slice.fraction * 100).roundToInt()
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background((tone?.container ?: MaterialTheme.colorScheme.surfaceContainerHigh).copy(alpha = 0.6f))
            .clickable(role = Role.Button, onClickLabel = "Show ${slice.label} transactions") { slice.categoryId?.let(onOpen) }
            .semantics(mergeDescendants = true) {
                contentDescription = "${slice.label}, ${MoneyFormatter.formatSigned(-slice.amount)}, $percent percent of spending"
            }
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(category?.icon ?: Icons.Outlined.Category, contentDescription = null, tint = tone?.content ?: MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(slice.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            MoneyText(money = -slice.amount, style = AmountStyles.row, fractionScale = 1f, minScale = 0.6f, modifier = Modifier.weight(1f))
            Text("$percent%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

// ------------------------------------------------------------------ Dashboard: shortcuts

private data class Shortcut(val label: String, val icon: ImageVector, val tone: TonePair, val onClick: () -> Unit)

/** Five evenly spaced shortcuts: a soft-toned icon square with its label beneath (Figma "Quick access"). */
@Composable
private fun ShortcutRow(
    state: HomeUiState,
    actions: HomeActions,
    onOpenMilestones: () -> Unit,
    onTurnOn: (Feature) -> Unit,
    onHideOffers: () -> Unit,
) {
    val f = state.features
    val ext = LucentoTheme.extendedColors
    val items = buildList {
        if (f.isEnabled(Feature.SAVINGS_GOALS)) add(Shortcut("Goals", Icons.Outlined.TrackChanges, TonePair(ext.infoContainer, ext.info), actions.onOpenGoals))
        if (f.isEnabled(Feature.INSIGHTS)) add(Shortcut("Insights", Icons.Outlined.BarChart, TonePair(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary), actions.onOpenInsights))
        add(Shortcut("Milestones", Icons.Outlined.EmojiEvents, TonePair(ext.warningContainer, ext.warning), onOpenMilestones))
        if (f.isEnabled(Feature.DEBTS_LOANS)) add(Shortcut("Debts", Icons.Outlined.CreditCard, TonePair(ext.negativeContainer, ext.negative), actions.onOpenDebts))
        if (f.isEnabled(Feature.RECURRING)) add(Shortcut("Scheduled", Icons.Outlined.CalendarMonth, TonePair(ext.positiveContainer, ext.positive)) { actions.onOpenRecurring(null) })
    }
    val haptics = rememberHaptics()
    val textScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    Column {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val perRow = when {
                maxWidth >= 340.dp * textScale -> items.size.coerceAtLeast(1)
                textScale >= 1.5f -> 2 // large text: two across so labels never break mid-word
                else -> 3
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items.chunked(perRow).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        row.forEach { item ->
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable(role = Role.Button, onClickLabel = "Open ${item.label}") {
                                        haptics.tick()
                                        item.onClick()
                                    }
                                    .padding(vertical = Spacing.xxs),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    Modifier.size(width = 48.dp, height = 46.dp).clip(RoundedCornerShape(14.dp)).background(item.tone.container),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(item.icon, contentDescription = null, tint = item.tone.content, modifier = Modifier.size(24.dp))
                                }
                                Spacer(Modifier.height(Spacing.xs))
                                Text(item.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
                            }
                        }
                        repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        val offers = if (state.moduleOffersHidden) emptyList() else f.rediscoverable()
        if (offers.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().heightIn(min = Sizes.minTouch), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Off: ${offers.joinToString { quickLinkName(it) }}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                )
                if (offers.size == 1) {
                    LucentoTextButton(text = "Turn on", onClick = { onTurnOn(offers.single()) }, modifier = Modifier.semantics { contentDescription = "Turn on ${offers.single().title}" })
                } else {
                    var open by remember { mutableStateOf(false) }
                    Box {
                        LucentoTextButton(text = "Turn on…", onClick = { open = true })
                        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                            offers.forEach { feature ->
                                DropdownMenuItem(text = { Text(feature.title) }, onClick = {
                                    open = false
                                    onTurnOn(feature)
                                })
                            }
                        }
                    }
                }
                LucentoTextButton(text = "Hide", onClick = onHideOffers, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { contentDescription = "Hide modules that are off. They stay in Settings, Enabled features." })
            }
        }
    }
}

// ------------------------------------------------------------------ Dashboard: scheduled and recent

/** What's still due this month (overdue first, then by date). Empty months say so; past months don't show the section. */
@Composable
private fun UpcomingScheduledCard(state: HomeUiState, onOpen: (Long?) -> Unit) {
    LucentoCard(Modifier.fillMaxWidth(), contentPadding = Spacing.md) {
        SectionHeader("Upcoming scheduled transactions", Icons.Outlined.CalendarMonth, onSeeAll = { onOpen(null) })
        if (state.scheduledThisMonth.isEmpty()) {
            EmptyCardMessage(
                if (state.hasAnySchedules) "Nothing else due in ${monthLabel(state.month)}." else "Add rent, bills, subscriptions or salary and they show up here before they’re due.",
            )
            if (!state.hasAnySchedules) LucentoTextButton(text = "Schedule a transaction", onClick = { onOpen(null) })
            return@LucentoCard
        }
        state.scheduledThisMonth.forEachIndexed { index, item ->
            ScheduledRow(item, state, onClick = { onOpen(item.schedule.id) })
            if (index < state.scheduledThisMonth.lastIndex) Hairline(inset = Sizes.iconTile + Spacing.md)
        }
    }
}

/** The month's newest entries, in the same rows as the Transactions list. */
@Composable
private fun RecentTransactionsCard(state: HomeUiState, actions: HomeActions) {
    LucentoCard(Modifier.fillMaxWidth(), contentPadding = Spacing.md) {
        SectionHeader("Recent transactions", Icons.Outlined.History, onSeeAll = actions.onOpenTransactions)
        if (state.recentInMonth.isEmpty()) {
            EmptyCardMessage(
                if (state.isCurrentMonth) "Nothing recorded in ${monthLabel(state.month)} yet. Tap + to add what you spend."
                else "Nothing was recorded in ${monthLabel(state.month)}.",
            )
            return@LucentoCard
        }
        val names = remember(state.wallets) { state.wallets.associate { it.id to it.name } }
        state.recentInMonth.forEachIndexed { index, t ->
            com.lucento.app.feature.transactions.TransactionRow(
                transaction = t,
                category = t.categoryId?.let { state.categoriesById[it] },
                wallet = state.walletsById[t.walletId],
                onClick = { actions.onViewTransaction(t) },
                transfer = t.transferGroup?.let { state.recentTransfers[it] },
                walletNames = names,
                dense = true,
                onClickLabel = "View transaction",
            )
            if (index < state.recentInMonth.lastIndex) Hairline()
        }
    }
}

@Composable
private fun SectionHeader(title: String, icon: ImageVector, onSeeAll: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(Spacing.sm))
        Text(title, style = Hierarchy.cardTitle, modifier = Modifier.weight(1f).semantics { heading() }, maxLines = 2)
        LucentoTextButton(text = "See all", onClick = onSeeAll, modifier = Modifier.semantics { contentDescription = "See all ${title.lowercase()}" })
    }
}

// ------------------------------------------------------------------ Part E: budget and pace

/**
 * The budget status in a word or two: Under budget, On track or Over budget — plus "Ahead of pace" when spending runs
 * faster than the month but hasn't gone over yet (the early warning worth acting on).
 */
internal fun verdictLabel(plan: BudgetPlan, position: PacePosition?, isCurrentMonth: Boolean): String = when {
    plan.remaining.isNegative -> "Over budget"
    !isCurrentMonth -> "Under budget"
    position == PacePosition.AHEAD -> "Ahead of pace"
    position == PacePosition.BEHIND -> "Under budget"
    else -> "On track"
}

@Composable
private fun VerdictChip(label: String, tone: TonePair) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = tone.content,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(tone.container)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
    )
}

// ------------------------------------------------------------------ Part D: getting started, + spotlight, milestones

/**
 * Progressive disclosure: the few basics still missing (accounts, a budget, a first entry), each one tap away. The card
 * disappears for good once they're all in place.
 */
@Composable
private fun GettingStartedCard(
    left: List<com.lucento.app.domain.Achievement>,
    onAddWallet: () -> Unit,
    onBudget: () -> Unit,
    onRecord: () -> Unit,
) {
    LucentoCard(Modifier.fillMaxWidth(), contentPadding = Spacing.lg) {
        Text("Getting started", style = Hierarchy.cardTitle, modifier = Modifier.semantics { heading() })
        Text(
            "${3 - left.size} of 3 done",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xs))
        left.forEach { step ->
            val (title, action, onClick) = when (step) {
                com.lucento.app.domain.Achievement.MONEY_KEEPER -> Triple("Add the accounts you use", "Add", onAddWallet)
                com.lucento.app.domain.Achievement.BUDGET_BUILDER -> Triple("Give your categories an amount", "Set", onBudget)
                else -> Triple("Record your first transaction", "Record", onRecord)
            }
            Row(Modifier.fillMaxWidth().heightIn(min = Sizes.minTouch), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Circle, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                LucentoTextButton(text = action, onClick = onClick, modifier = Modifier.semantics { contentDescription = title })
            }
        }
    }
}

/** Part D: the one-time pointer to the dock's + after setup, until the first entry. Dismissible; never repeats. */
@Composable
private fun AddSpotlightCard(onRecord: () -> Unit, onDismiss: () -> Unit) {
    LucentoCard(Modifier.fillMaxWidth(), contentPadding = Spacing.lg) {
        Row(verticalAlignment = Alignment.Top) {
            IconTile(icon = Icons.Filled.Add, size = 40.dp, container = MaterialTheme.colorScheme.primary, content = MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text("Record your first transaction", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(
                    "Tap + below and type it like a message — “coffee 4.50, lunch 12”. You check it before anything is saved.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            LucentoTextButton(text = "Not now", onClick = onDismiss, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LucentoTextButton(text = "Record now", onClick = onRecord)
        }
    }
}

/** Every milestone, earned or not yet. Worked out from the user's records; nothing to keep up and nothing to lose. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MilestonesSheet(earned: Set<com.lucento.app.domain.Achievement>, onDismiss: () -> Unit) {
    com.lucento.app.core.designsystem.component.LucentoBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = Spacing.gutter).padding(bottom = Spacing.xl).verticalScroll(rememberScrollState())) {
            Text("Milestones", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(
                "${earned.size} of ${com.lucento.app.domain.Achievement.entries.size} · from your own records. No streaks, nothing to lose.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xxs, bottom = Spacing.md),
            )
            com.lucento.app.domain.Achievement.entries.forEach { a ->
                val done = a in earned
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .semantics(mergeDescendants = true) {}
                        .padding(vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconTile(
                        icon = if (done) Icons.Filled.EmojiEvents else Icons.Outlined.EmojiEvents,
                        size = 40.dp,
                        container = if (done) LucentoTheme.extendedColors.accent else MaterialTheme.colorScheme.surfaceContainerHigh,
                        content = if (done) LucentoTheme.extendedColors.onAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(Spacing.md))
                    Column(Modifier.weight(1f)) {
                        Text(a.title, style = MaterialTheme.typography.titleMedium)
                        Text(a.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        if (done) "Earned" else "Not yet",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}


/** N-11: shapes where the dashboard will be, instead of a blank screen, while the first data loads. */
@Composable
private fun HomeSkeleton() {
    val tone = MaterialTheme.colorScheme.surfaceContainer
    Column(
        Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Loading your dashboard" },
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(Modifier.width(180.dp).height(Sizes.minTouch).clip(MaterialTheme.shapes.medium).background(tone))
        Box(Modifier.fillMaxWidth().height(220.dp).clip(MaterialTheme.shapes.large).background(tone))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Box(Modifier.weight(1.1f).height(180.dp).clip(MaterialTheme.shapes.large).background(tone))
            Box(Modifier.weight(1f).height(180.dp).clip(MaterialTheme.shapes.large).background(tone))
        }
    }
}

// ------------------------------------------------------------------ Scheduled transactions

/** A scheduled item in the Figma two-line layout: name and signed amount, then date · category and when it's due. */
@Composable
private fun ScheduledRow(item: UpcomingPayment, state: HomeUiState, onClick: () -> Unit) {
    val schedule = item.schedule
    val category = schedule.categoryId?.let { state.categoriesById[it] }
    val days = ChronoUnit.DAYS.between(state.today, item.dueDate)
    val relative = when {
        item.overdue -> "Overdue"
        days == 0L -> "Today"
        days == 1L -> "Tomorrow"
        else -> "In $days days"
    }
    val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()) }
    val income = schedule.type == TransactionType.INCOME
    val kind = category?.name ?: if (income) "Recurring income" else "Recurring expense"
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClickLabel = "Review ${schedule.name}", onClick = onClick)
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryTile(category = category, type = schedule.type, size = 38.dp)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(schedule.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(Spacing.sm))
                AmountText(
                    money = if (income) schedule.amount else -schedule.total,
                    showPlus = true,
                    color = if (income) LucentoTheme.extendedColors.positive else MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(Spacing.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${item.dueDate.format(dateFormat)} · $kind",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Spacing.sm))
                StatusPill(relative, if (item.overdue) StatusTones.negative else StatusTones.brand)
            }
        }
    }
}

internal fun monthLabel(month: YearMonth, locale: Locale = Locale.getDefault()): String =
    month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) }

private fun monthYearLabel(month: YearMonth, locale: Locale = Locale.getDefault()): String = "${monthLabel(month, locale)} ${month.year}"
