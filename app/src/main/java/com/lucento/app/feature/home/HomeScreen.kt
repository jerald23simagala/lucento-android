package com.lucento.app.feature.home

import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material.icons.outlined.Group
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
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.graphics.ColorFilter
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
import com.lucento.app.core.designsystem.component.CardOverline
import com.lucento.app.core.designsystem.component.LucentoBottomSheet
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.ProgressRing
import com.lucento.app.core.designsystem.component.ShareBar
import com.lucento.app.core.designsystem.theme.OverlineStyle
import com.lucento.app.domain.CategoryBudgetLine
import com.lucento.app.feature.budget.healthColor
import com.lucento.app.feature.budget.healthTone
import com.lucento.app.feature.budget.paceVerdict
import com.lucento.app.feature.budget.verdictTone
import androidx.compose.ui.graphics.PathEffect
import com.lucento.app.core.designsystem.component.CategoryTile
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.IconTile
import com.lucento.app.core.designsystem.component.LucentoCard
import com.lucento.app.core.designsystem.component.LucentoMark
import com.lucento.app.core.designsystem.component.LucentoMascot
import com.lucento.app.core.designsystem.component.MascotMessage
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.component.LucentoWordmark
import com.lucento.app.core.designsystem.component.MascotMood
import com.lucento.app.core.designsystem.component.MoneyText
import com.lucento.app.core.designsystem.component.ProfileButton
import com.lucento.app.core.designsystem.component.StatementPanel
import com.lucento.app.core.designsystem.component.StatusPill
import com.lucento.app.core.designsystem.component.StatusTones
import com.lucento.app.core.designsystem.component.silhouetteFilter
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.BalanceCardFace
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
import com.lucento.app.domain.GoalsOverview
import com.lucento.app.domain.NetWorthComposition
import com.lucento.app.domain.PacePosition
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.CardDue
import com.lucento.app.domain.ScheduledFlow
import com.lucento.app.domain.flow
import com.lucento.app.domain.signedOccurrence
import com.lucento.app.core.designsystem.component.scheduledFlowColor
import com.lucento.app.core.designsystem.component.SheetHeader
import com.lucento.app.domain.UpcomingPayment
import com.lucento.app.domain.RecurringMath
import java.time.LocalDate
import com.lucento.app.domain.WellnessTier
import com.lucento.app.feature.wallets.WalletDraft
import com.lucento.app.feature.wallets.WalletEditorSheet
import com.lucento.app.feature.wallets.WalletsViewModel
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.CompositionLocalProvider
import com.lucento.app.core.designsystem.component.LocalAmountsHidden
import com.lucento.app.core.designsystem.component.shownAmount
import com.lucento.app.core.designsystem.component.spokenAmount
import com.lucento.app.core.security.DeviceAuth
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.LocalContext
import com.lucento.app.core.designsystem.component.ConfirmDialog

/** Callbacks Home needs from the app shell. Adding transactions happens through the dock's central + button. */
data class HomeActions(
    val onOpenBudget: () -> Unit,
    val onChooseCategories: () -> Unit,
    val onOpenInsights: () -> Unit,
    val onOpenGoals: () -> Unit,
    /** Opens Recurring payments; with a schedule id, straight to that payment's review / mark-paid sheet. */
    val onOpenRecurring: (Long?) -> Unit,
    val onOpenWallets: () -> Unit = {},
    /** One wallet's page. */
    val onOpenWallet: (Long) -> Unit = {},
    /**
     * A card's payment reminder (the bell, Upcoming): the transfer sheet, filled in with the amount due, the card and the
     * account it's usually paid from, ready to check and save. Without transfers it opens the card instead.
     */
    val onPayCard: (CardDue) -> Unit = { onOpenWallet(it.wallet.id) },
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
    /** Lucento Shared: an invitation waiting for this account, or a membership this phone doesn't show yet. */
    val spacePrompt: com.lucento.app.data.sharedspace.SpacePrompt? = null,
    val onOpenSpacePrompt: () -> Unit = {},
    /** "Not now": the prompt goes; what it points to stays in Settings › Lucento Shared. */
    val onDismissSpacePrompt: () -> Unit = {},
    /** A "top spending" tile: that category's transactions in the shown month. */
    val onOpenCategory: (Long, YearMonth) -> Unit = { _, _ -> },
)

/**
 * The dashboard, top to bottom: a pinned header ("Dashboard", sync status, payments bell, profile) and month selector;
 * the net wallet balance card; the month's budget with its top two categories; shortcuts; getting started (only until
 * the basics are in place); upcoming scheduled transactions; recent transactions. Every month-dependent section follows
 * the one selected month. Every figure comes from [HomeUiState].
 *
 * With amounts hidden (owner, 2026-11-04) every amount shows as "₱••••" ([LocalAmountsHidden], provided app-wide); the
 * Hide / Show switch lives here. Percentages and words stay, so the dashboard still reads.
 */
@Composable
fun HomeScreen(
    actions: HomeActions,
    /**
     * Bumped each time Home is tapped in the dock: the dashboard then shows its top (owner round 10). Coming back with
     * Back from a page opened from Home keeps the place, because the request was already handled.
     */
    scrollToTop: Int = 0,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
    walletsViewModel: WalletsViewModel = viewModel(factory = WalletsViewModel.Factory),
) {
    // The app's root provides LocalAmountsHidden from the same saved choice; the dashboard is where it's switched.
    HomeDashboard(actions, scrollToTop, viewModel, walletsViewModel)
}

@Composable
private fun HomeDashboard(
    actions: HomeActions,
    scrollToTop: Int,
    viewModel: HomeViewModel,
    walletsViewModel: WalletsViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editingWallet by remember { mutableStateOf<WalletDraft?>(null) }
    var showSync by remember { mutableStateOf(false) }
    var showAwards by remember { mutableStateOf(false) }
    // The award being presented. Saved, so a ceremony isn't lost to rotation after its award was acknowledged.
    var ceremony by rememberSaveable { mutableStateOf<com.lucento.app.domain.Achievement?>(null) }
    var ceremonyAlso by rememberSaveable { mutableStateOf<List<com.lucento.app.domain.Achievement>>(emptyList()) }
    var replaying by remember { mutableStateOf<com.lucento.app.domain.Achievement?>(null) }

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
    // One ceremony for whatever was earned together; anything earned while one is open (or while the cabinet is, where
    // the person is already looking at their awards) is presented after it closes.
    LaunchedEffect(state.milestoneToShow, ceremony == null, showAwards) {
        val award = state.milestoneToShow ?: return@LaunchedEffect
        if (ceremony != null || showAwards) return@LaunchedEffect
        ceremony = award
        ceremonyAlso = ArrayList(state.milestonesAlsoNew)
        viewModel.acknowledgeMilestones(listOf(award) + state.milestonesAlsoNew)
    }

    val scroll = rememberScrollState()
    // Saved with the scroll position, so a request handled before leaving Home isn't handled again on the way back.
    var handledToTop by rememberSaveable { mutableIntStateOf(scrollToTop) }
    val shown = remember { mutableStateOf(false) }
    LaunchedEffect(scrollToTop) {
        if (scrollToTop != handledToTop) {
            // Arriving from another tab: jump, so the restored position never flashes. Tapped again on Home: glide up.
            if (shown.value) scroll.animateScrollTo(0) else scroll.scrollTo(0)
            handledToTop = scrollToTop
        }
        shown.value = true
    }
    // 20dp on phones; on wide windows the dashboard stays a 560dp column, centred.
    val gutter = com.lucento.app.core.designsystem.component.screenGutter()
    Column(Modifier.fillMaxSize()) {
        // Pinned: the brand, sync status, notifications and profile stay put while the dashboard scrolls beneath.
        HomeHeader(
            status = state.cloudStatus,
            syncDetails = state.syncDetails,
            onOpenSyncSettings = { showSync = true },
            bell = if (state.features.isEnabled(Feature.RECURRING) || state.cardDuesSoon.isNotEmpty()) {
                { PaymentsBell(state, actions.onOpenRecurring, actions.onPayCard) }
            } else {
                null
            },
            modifier = Modifier.padding(horizontal = gutter),
        )
        if (!state.loading && state.hasWallets) {
            // Pinned with the header, so the month every section describes is always in view. Left-aligned where the
            // thumb reaches it, and tight to the card below (the pill's 48dp touch box adds the rest of the gap).
            Row(
                Modifier.fillMaxWidth().padding(start = gutter, end = gutter, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MonthPill(state, viewModel::selectMonth)
                Spacer(Modifier.weight(1f))
                AmountsToggle(hidden = state.hideAmounts, onHide = { viewModel.setHideAmounts(true) }, onShow = { viewModel.setHideAmounts(false) })
            }
        }
        if (scroll.value > 0) Hairline()

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(horizontal = gutter),
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
                HomeContent.Welcome -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    actions.spacePrompt?.let { SpacePromptCard(it, actions) }
                    WelcomePanel(
                        // A Partner's phone: the Shared Space's Owner adds wallets (the panel then says so).
                        onAddWallet = if (com.lucento.app.feature.sharedspace.LocalSharedAccess.current.canManageStructure) {
                            { editingWallet = WalletDraft(currencyCode = state.primaryCurrency) }
                        } else {
                            null
                        },
                    )
                }
                HomeContent.Overview -> Overview(
                    state = state,
                    actions = actions,
                    onSelectMonth = viewModel::selectMonth,
                    onDismissLocalDataNotice = viewModel::dismissLocalDataNotice,
                    onDismissOverridden = viewModel::dismissOverridden,
                    onTurnOn = viewModel::turnOn,
                    onHideOffers = viewModel::hideModuleOffers,
                    onAddWallet = { editingWallet = WalletDraft(currencyCode = state.primaryCurrency) },
                    onOpenMilestones = { showAwards = true },
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
    if (showAwards) {
        com.lucento.app.feature.awards.AwardsSheet(
            earned = state.earned,
            facts = state.awardFacts,
            onDismiss = { showAwards = false },
            onReplay = { replaying = it },
        )
    }
    replaying?.let { award ->
        com.lucento.app.feature.awards.AwardCeremony(award, earnedCount = state.earned.size, onDismiss = { replaying = null })
    }
    ceremony?.let { award ->
        com.lucento.app.feature.awards.AwardCeremony(
            achievement = award,
            alsoEarned = ceremonyAlso,
            earnedCount = state.earned.size,
            onDismiss = { ceremony = null },
            onSeeAll = {
                ceremony = null
                showAwards = true
            },
        )
    }
}

private enum class HomeContent { Loading, Welcome, Overview }

// ------------------------------------------------------------------ Header

/**
 * "Dashboard" on the left; sync status, the payments bell (when Scheduled is on) and the profile entry on the right.
 * In a Shared Space someone has joined, the profile entry carries a "Shared" pill (ProfileButton).
 */
@Composable
private fun HomeHeader(
    status: CloudStatus,
    syncDetails: SyncDetails,
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
            SyncStatusChip(status, syncDetails, onClick = onOpenSyncSettings)
            bell?.invoke()
            ProfileButton()
        }
    }
}

/**
 * The bell: scheduled items and card payments that need attention (overdue, or due within a week) with a count badge,
 * so they're noticed without scrolling. Opens a small panel — Lucento with a one-line summary, what's still to pay,
 * then Overdue and This week sections — where a schedule opens its review sheet and a card payment opens the transfer
 * that pays it, already filled in.
 */
@Composable
private fun PaymentsBell(state: HomeUiState, onOpenRecurring: (Long?) -> Unit, onPayCard: (CardDue) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val due = state.dueSoon
    val cards = state.cardDuesSoon
    val count = due.size + cards.size
    val overdue = due.count { it.overdue } + cards.count { it.overdue }
    val haptics = rememberHaptics()
    Box {
        IconButton(
            onClick = {
                haptics.tick()
                open = true
            },
            modifier = Modifier.semantics {
                contentDescription = when {
                    count == 0 -> "Upcoming payments: nothing due this week"
                    overdue > 0 -> "Upcoming payments: $count due, $overdue overdue"
                    else -> "Upcoming payments: $count due this week"
                }
            },
        ) {
            BadgedBox(
                badge = {
                    if (count > 0) {
                        Badge(
                            containerColor = if (overdue > 0) LucentoTheme.extendedColors.negative else MaterialTheme.colorScheme.primary,
                            contentColor = if (overdue > 0) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onPrimary,
                        ) { Text(if (count > 9) "9+" else "$count") }
                    }
                },
            ) {
                Icon(if (count == 0) Icons.Outlined.Notifications else Icons.Filled.Notifications, contentDescription = null)
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(22.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            shadowElevation = 10.dp,
            modifier = Modifier.widthIn(min = 288.dp, max = 340.dp),
        ) {
            BellPanel(
                state = state,
                count = count,
                overdue = overdue,
                onOpenSchedule = { id ->
                    open = false
                    onOpenRecurring(id)
                },
                onPayCard = { due ->
                    open = false
                    onPayCard(due)
                },
                onSeeAll = {
                    open = false
                    onOpenRecurring(null)
                },
            )
        }
    }
}

/** One entry in the bell: a scheduled occurrence or a card payment, with what the panel needs to draw it. */
private data class BellEntry(
    val date: LocalDate,
    val overdue: Boolean,
    val title: String,
    val amount: Money,
    val flow: ScheduledFlow,
    val payment: UpcomingPayment? = null,
    val card: CardDue? = null,
)

@Composable
private fun BellPanel(
    state: HomeUiState,
    count: Int,
    overdue: Int,
    onOpenSchedule: (Long) -> Unit,
    onPayCard: (CardDue) -> Unit,
    onSeeAll: () -> Unit,
) {
    val ext = LucentoTheme.extendedColors
    val entries = (
        state.dueSoon.map { p ->
            val flow = p.schedule.flow
            BellEntry(p.dueDate, p.overdue, p.schedule.name, p.schedule.signedOccurrence, flow, payment = p)
        } + state.cardDuesSoon.map { c ->
            BellEntry(c.cycle.dueDate, c.overdue, "${c.wallet.name} payment", -c.cycle.amountDue, ScheduledFlow.TRANSFER, card = c)
        }
        ).sortedBy { it.date }
    // What leaves your accounts this week (overdue included), in the main currency: bills and transfers together. A
    // collection from a receivable comes in, so it isn't counted (owner round 10).
    val code = state.focusCurrency
    val outgoing = Money.sum(code, entries.filter { it.amount.isNegative && it.amount.currencyCode == code }.map { -it.amount })
    Column(Modifier.padding(bottom = Spacing.xs)) {
        // Header: Lucento and the week in one line.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = Spacing.md, end = Spacing.md, top = Spacing.xs, bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.lucento.app.core.designsystem.component.LucentoMascot(
                size = 46.dp,
                mood = when {
                    overdue > 0 -> com.lucento.app.core.designsystem.component.MascotMood.Concerned
                    count == 0 -> com.lucento.app.core.designsystem.component.MascotMood.Success
                    else -> com.lucento.app.core.designsystem.component.MascotMood.Happy
                },
            )
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text("Upcoming payments", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(
                    when {
                        count == 0 -> "You’re all clear for the next ${RecurringMath.DUE_SOON_DAYS} days."
                        overdue > 0 && overdue == count -> if (overdue == 1) "1 payment is overdue." else "$overdue payments are overdue."
                        overdue > 0 -> "$overdue overdue · ${count - overdue} due this week"
                        count == 1 -> "1 payment due this week"
                        else -> "$count payments due this week"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overdue > 0) ext.negative else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (entries.isNotEmpty() && !outgoing.isZero) {
            // The week's total going out, on a soft tinted strip.
            Row(
                Modifier
                    .padding(horizontal = Spacing.md)
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(if (overdue > 0) ext.negativeContainer else MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "To pay by ${state.today.plusDays(RecurringMath.DUE_SOON_DAYS).format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (overdue > 0) ext.negative else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                )
                MoneyText(
                    money = -outgoing,
                    style = AmountStyles.row,
                    color = if (overdue > 0) ext.negative else MaterialTheme.colorScheme.onPrimaryContainer,
                    fractionScale = 1f,
                    minScale = 0.6f,
                )
            }
            Spacer(Modifier.height(Spacing.xs))
        }
        val sections = listOf("Overdue" to entries.filter { it.overdue }, "This week" to entries.filterNot { it.overdue })
        sections.filter { it.second.isNotEmpty() }.forEach { (title, list) ->
            Text(
                title.uppercase(Locale.getDefault()),
                style = Hierarchy.cardOverline,
                color = if (title == "Overdue") ext.negative else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, top = Spacing.sm, bottom = Spacing.xxs).semantics { heading() },
            )
            list.forEach { entry ->
                BellRow(entry, state, onClick = {
                    when {
                        entry.payment != null -> onOpenSchedule(entry.payment.schedule.id)
                        entry.card != null -> onPayCard(entry.card)
                    }
                })
            }
        }
        if (entries.isEmpty()) {
            Text(
                "Scheduled bills, income and card payments show up here a week before they’re due.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        // A clear way on, rather than a bare menu item.
        Row(
            Modifier
                .padding(horizontal = Spacing.md)
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(role = Role.Button, onClick = onSeeAll)
                .padding(horizontal = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Spacing.sm))
            Text("See all scheduled", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** A bell row: the item's tile, its name and when it's due, and the signed amount in its flow's colour. */
@Composable
private fun BellRow(entry: BellEntry, state: HomeUiState, onClick: () -> Unit) {
    val ext = LucentoTheme.extendedColors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClickLabel = if (entry.card != null) "Pay ${entry.card.wallet.name}" else "Open ${entry.title}", onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val schedule = entry.payment?.schedule
        val debt = schedule?.toWalletId?.let { state.walletsById[it] }
        when {
            entry.card != null -> com.lucento.app.core.designsystem.component.WalletIdentityTile(entry.card.wallet, size = 36.dp)
            debt != null -> com.lucento.app.core.designsystem.component.WalletIdentityTile(debt, size = 36.dp)
            schedule != null -> CategoryTile(category = schedule.categoryId?.let { state.categoriesById[it] }, type = schedule.type, size = 36.dp)
        }
        Spacer(Modifier.width(Spacing.sm + Spacing.xxs))
        Column(Modifier.weight(1f)) {
            Text(entry.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                dueLabel(entry.date, entry.overdue, state.today) + if (entry.flow == ScheduledFlow.TRANSFER) " · transfer" else "",
                style = MaterialTheme.typography.labelSmall,
                color = if (entry.overdue) ext.negative else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        AmountText(money = entry.amount, showPlus = true, style = AmountStyles.small, color = scheduledFlowColor(entry.flow))
    }
}

/** "Overdue · Oct 1", "Due today", "Due tomorrow", "Due in 3 days". */
private fun dueLabel(item: UpcomingPayment, today: LocalDate): String = dueLabel(item.dueDate, item.overdue, today)

private fun dueLabel(dueDate: LocalDate, overdue: Boolean, today: LocalDate): String {
    val days = ChronoUnit.DAYS.between(today, dueDate)
    val date = dueDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()))
    return when {
        overdue -> "Overdue · $date"
        days == 0L -> "Due today"
        days == 1L -> "Due tomorrow"
        else -> "Due in $days days · $date"
    }
}

/**
 * A compact chip: icon plus a short state, so the meaning never relies on colour. Opens the sync details sheet (P2-2).
 * Synced says how long ago and Waiting how many changes (owner, 2026-11-04); the time is refreshed every half minute.
 * While syncing the icon turns (still with reduced motion).
 */
@Composable
private fun SyncStatusChip(status: CloudStatus, details: SyncDetails, onClick: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    val label = syncChipLabel(status, details, now)
    val description = syncChipDescription(status, details, now)
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
        // Lucento Pro: on but waiting for a plan — quiet, never a warning (nothing is wrong and nothing is lost).
        CloudStatus.PAUSED -> { tone = StatusTones.neutral; icon = Icons.Outlined.CloudOff }
    }
    Box(
        Modifier
            .heightIn(min = Sizes.minTouch)
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClickLabel = "Show sync details", onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
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
            val turning = status == CloudStatus.SYNCING && !LucentoTheme.reducedMotion
            val angle = if (turning) {
                val spin = androidx.compose.animation.core.rememberInfiniteTransition(label = "sync")
                spin.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                        androidx.compose.animation.core.tween(1_100, easing = androidx.compose.animation.core.LinearEasing),
                    ),
                    label = "sync-angle",
                ).value
            } else {
                0f
            }
            Icon(icon, contentDescription = null, tint = tone.content, modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = angle })
            // At large text sizes the icon alone keeps the header readable; the status is still announced in full.
            if (!largeText()) {
                Spacer(Modifier.width(Spacing.xs))
                Text(label, style = MaterialTheme.typography.labelMedium, color = tone.content, maxLines = 1)
            }
        }
    }
}

/**
 * Hide or show the dashboard's amounts (owner, 2026-11-04). Hiding is one tap. Showing them again asks for the phone's
 * own unlock — fingerprint, face, PIN, pattern or password, whatever it has (BiometricPrompt via [DeviceAuth]); a
 * cancelled prompt keeps them hidden. A phone without any screen lock can't check who's asking, so it says so and lets
 * the owner choose.
 */
@Composable
private fun AmountsToggle(hidden: Boolean, onHide: () -> Unit, onShow: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    var noScreenLock by remember { mutableStateOf(false) }
    val label = if (hidden) "Show amounts" else "Hide amounts"
    Box(
        Modifier
            .heightIn(min = Sizes.minTouch)
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Switch, onClickLabel = label) {
                if (!hidden) {
                    haptics.tick()
                    onHide()
                    return@clickable
                }
                val activity = context.findFragmentActivity() ?: return@clickable
                val lock = (context.applicationContext as com.lucento.app.LucentoApplication).container.appLock
                // The prompt's own screen must not count as leaving the app (which could re-lock it).
                lock.beginAuthentication()
                DeviceAuth.authenticate(activity, title = "Show amounts", subtitle = "Use your fingerprint, face or screen lock") { outcome ->
                    lock.endAuthentication()
                    when (outcome) {
                        DeviceAuth.Outcome.SUCCESS -> {
                            haptics.confirm()
                            onShow()
                        }
                        DeviceAuth.Outcome.CANCELLED -> Unit
                        DeviceAuth.Outcome.UNAVAILABLE -> noScreenLock = true
                    }
                }
            }
            .semantics {
                stateDescription = if (hidden) "Amounts hidden" else "Amounts shown"
                contentDescription = if (hidden) "Show amounts. Asks for your fingerprint, face or screen lock" else "Hide amounts"
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .clip(CircleShape)
                .background(if (hidden) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = Spacing.sm + Spacing.xxs, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tint = if (hidden) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            Icon(
                if (hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
            if (!largeText()) {
                Spacer(Modifier.width(Spacing.xs))
                Text(if (hidden) "Show" else "Hide", style = MaterialTheme.typography.labelLarge, color = tint, maxLines = 1)
            }
        }
    }
    if (noScreenLock) {
        ConfirmDialog(
            title = "This phone has no screen lock",
            text = "Lucento asks for your fingerprint, face or screen lock before showing hidden amounts, but this phone " +
                "has none set up. Add one in your phone’s Settings to protect them. Show the amounts now?",
            confirmLabel = "Show amounts",
            onConfirm = {
                noScreenLock = false
                onShow()
            },
            onDismiss = { noScreenLock = false },
        )
    }
}

/** The FragmentActivity hosting [this] context (BiometricPrompt needs one), or null. */
private fun android.content.Context.findFragmentActivity(): androidx.fragment.app.FragmentActivity? {
    var current: android.content.Context? = this
    while (current is android.content.ContextWrapper) {
        if (current is androidx.fragment.app.FragmentActivity) return current
        current = current.baseContext
    }
    return null
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
                    .heightIn(min = 40.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .border(BorderStroke(1.dp, LucentoTheme.extendedColors.borderDefault), MaterialTheme.shapes.medium)
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(com.lucento.app.R.drawable.ic_dash_calendar_days), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text(monthYearLabel(state.month), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Spacer(Modifier.width(Spacing.sm))
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
private fun WelcomePanel(onAddWallet: (() -> Unit)?) {
    val colors = LucentoTheme.extendedColors
    if (onAddWallet == null) {
        val owner = com.lucento.app.feature.sharedspace.LocalSharedAccess.current.ownerDisplay
        StatementPanel(Modifier.fillMaxWidth()) {
            Text("Nothing here yet", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "$owner hasn’t added any wallets to the Shared Space yet. Once they do, everything appears here.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onHeroMuted,
            )
        }
        return
    }
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
    val canEdit = com.lucento.app.feature.sharedspace.LocalSharedAccess.current.canManageStructure
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        actions.spacePrompt?.let { SpacePromptCard(it, actions) }
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

        ShortcutRow(state, actions, onOpenMilestones, onTurnOn, onHideOffers)

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

        // Setting up wallets and a budget is the Owner's part in a Shared Space.
        if (canEdit && state.gettingStartedLeft.isNotEmpty()) {
            GettingStartedCard(
                left = state.gettingStartedLeft,
                onAddWallet = onAddWallet,
                onBudget = if (state.expenseCategoryCount == 0) actions.onChooseCategories else actions.onOpenBudget,
                onRecord = { actions.onAddTransaction(null) },
            )
        }

        val recurringOn = f.isEnabled(Feature.RECURRING)
        if (state.isCurrentMonth && (recurringOn || state.cardDues.isNotEmpty())) {
            UpcomingScheduledCard(state, onOpen = if (recurringOn) actions.onOpenRecurring else null, onPayCard = actions.onPayCard)
        }

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

// The card's face (Figma "Net wallet balance") comes from the theme (LucentoTheme.spec.balanceCard): the same in light
// and dark mode, like a physical card.
private val CardFigureStyle = AmountStyles.large.copy(fontSize = 20.sp, letterSpacing = (-0.5).sp)

/**
 * The dashboard's focal point, drawn as a physical card: ID-1 proportions (it only grows for large text), a soft drop
 * shadow, a thin light edge, a glossy sheen across the top and a faint guilloché pattern like printed security lines.
 * Label and amount sit together at the top; Wallets and Debt & Loans along the bottom edge. Opens Wallets.
 */
@Composable
private fun WalletBalanceCard(state: HomeUiState, composition: NetWorthComposition, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val spec = LucentoTheme.spec
    val face = spec.balanceCard
    val owed = -composition.totalLiabilities
    val label = if (state.isCurrentMonth) "Net Wallet Balance" else "Net Wallet Balance · end of ${monthLabel(state.month)}"
    val history = state.balanceHistory
    val netWorthSpoken = spokenAmount(composition.netWorth, signed = true)
    val assetsSpoken = spokenAmount(composition.totalAssets, signed = true)
    val owedSpoken = spokenAmount(owed, signed = true)
    val changeSpoken = if (history.size > 1) spokenAmount(history.last().netWorth - history.first().netWorth, signed = true, showPlus = true) else ""
    val spoken = buildString {
        append("$label $netWorthSpoken. ")
        append("Wallets $assetsSpoken. ")
        append("Debt and loans $owedSpoken. ")
        if (history.size > 1) {
            append("Compared with the end of ${monthLabel(history.first().month)}: ")
            append(changeSpoken)
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
                .shadow(12.dp, shape, ambientColor = face.end.copy(alpha = 0.35f), spotColor = face.end.copy(alpha = 0.55f))
                .clip(shape)
                .background(Brush.linearGradient(listOf(face.start, face.end), start = Offset.Zero, end = Offset.Infinite))
                .clickable(role = Role.Button, onClickLabel = "Open wallets", onClick = onOpen),
        ) {
            // The theme's landscape printed tone-on-tone (it only darkens the face), the Figma texture disc, then the
            // printed surface. matchParentSize: none of them makes the card taller.
            Image(
                painterResource(spec.scenery),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = BiasAlignment(horizontalBias = 0.3f, verticalBias = 1f),
                colorFilter = silhouetteFilter(face.artThreshold),
                modifier = Modifier.matchParentSize(),
            )
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
                            // Caps like every other card's overline; TalkBack reads the spoken summary instead.
                            Text(
                                label.uppercase(Locale.getDefault()),
                                style = Hierarchy.cardOverline,
                                color = face.inkMuted,
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
                            color = face.ink,
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
                    com.lucento.app.core.designsystem.component.CardChip(metal = face.metal)
                    Spacer(Modifier.width(Spacing.sm))
                    Icon(Icons.Filled.Contactless, contentDescription = null, tint = face.inkMuted.copy(alpha = 0.85f), modifier = Modifier.size(22.dp))
                }
                Column {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(face.ink.copy(alpha = 0.14f)))
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
            Icon(painterResource(com.lucento.app.R.drawable.ic_dash_info), contentDescription = "What net wallet balance means", tint = LucentoTheme.spec.balanceCard.inkMuted, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun CardFigure(@androidx.annotation.DrawableRes icon: Int, iconWidth: Dp, label: String, money: Money, modifier: Modifier) {
    val face = LucentoTheme.spec.balanceCard
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        // Line icons drawn in the Sunrise muted ink; tinted to the theme's.
        Image(
            painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(face.inkMuted),
            modifier = Modifier.size(width = iconWidth, height = 42.dp),
        )
        Spacer(Modifier.width(7.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = face.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))
            MoneyText(money = money, style = CardFigureStyle, color = face.ink, fractionScale = 1f, minScale = 0.55f)
        }
    }
}

/** This month against last: two small bars, the change in percent (or amount), and "vs last month". */
@Composable
private fun BalanceTrend(history: List<com.lucento.app.domain.MonthBalance>, trend: Int?) {
    val values = history.map { it.netWorth.amountMinor }
    val min = values.min()
    val max = values.max()
    val face = LucentoTheme.spec.balanceCard
    val barColors = face.bars
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
                    tint = if (trend >= 0) BalanceCardFace.Gain else BalanceCardFace.Loss,
                    modifier = Modifier.size(14.dp).graphicsLayer { if (trend < 0) scaleY = -1f },
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    "${if (trend >= 0) "+" else "−"}${kotlin.math.abs(trend)}%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (trend >= 0) BalanceCardFace.Gain else BalanceCardFace.Loss,
                    maxLines = 1,
                )
            }
        } else {
            MoneyText(money = history.last().netWorth - history.first().netWorth, style = MaterialTheme.typography.labelLarge, color = face.ink, showPlus = true, fractionScale = 1f, minScale = 0.5f)
        }
        Text("vs last month", style = MaterialTheme.typography.labelSmall, color = face.inkMuted, maxLines = 1)
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
    val verdict = paceVerdict(plan, burn.pace, state.isCurrentMonth)
    val showVerdict = !setupNeeded && plan.hasBudget
    LucentoCard(Modifier.fillMaxWidth(), onClick = open, contentPadding = Spacing.lg) {
        CardOverline(
            "Monthly budget",
            icon = Icons.Outlined.DonutLarge,
            trailing = if (showVerdict && !largeText()) ({ VerdictChip(verdict.label, verdictTone(verdict)) }) else null,
        )
        if (showVerdict && largeText()) {
            Spacer(Modifier.height(Spacing.xs))
            VerdictChip(verdict.label, verdictTone(verdict))
        }
        Spacer(Modifier.height(14.dp))
        when {
            setupNeeded -> {
                MascotMessage("Choose the categories you spend on — your budget is built from them.")
                LucentoTextButton(text = "Choose categories", onClick = open)
            }
            !plan.hasBudget -> {
                MascotMessage("No budget for ${monthLabel(plan.month)} yet. Give your categories an amount to see your pace.")
                LucentoTextButton(text = "Set amounts", onClick = open)
            }
            else -> {
                val formula = HomeStateBuilder.budgetFormula(plan, state.safeToSpend)
                val textScale = LocalDensity.current.fontScale
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    // Side by side from a 360dp-wide phone up; stacked on narrower screens and at large text.
                    if (maxWidth >= 286.dp && textScale < 1.3f) {
                        // The ring is exactly as wide as the formula under it, and "To watch" is as tall as both.
                        val left = ((maxWidth - Spacing.lg) * 0.54f).coerceAtMost(184.dp)
                        MatchedHeightPair(
                            firstWidth = left,
                            gap = Spacing.lg,
                            first = { BudgetFigures(plan, burn, formula, ringSize = left) },
                            second = { WatchPanel(state, onOpenCategory, onSeeAll = open, fillHeight = true) },
                        )
                    } else {
                        // The ring grows with the text inside it, so "100%" never spills over the stroke.
                        val ring = (140.dp * textScale.coerceIn(1f, 1.4f)).coerceAtMost(maxWidth)
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                            BudgetFigures(plan, burn, formula, ringSize = ring)
                            WatchPanel(state, onOpenCategory, onSeeAll = open, fillHeight = false)
                        }
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

/** The budget used as one 7dp bar; [monthGone] adds a thin marker where the month stands (unused on the dashboard). */
@Composable
private fun BudgetBar(used: Float, monthGone: Float?, color: Color, description: String) {
    val track = LucentoTheme.extendedColors.track
    val marker = MaterialTheme.colorScheme.onSurface
    val shown = com.lucento.app.core.designsystem.component.animatedFraction(used.coerceIn(0f, 1f))
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(7.dp)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        val w = size.width * shown
        if (w > 0f) drawRoundRect(color, size = Size(w.coerceAtLeast(size.height), size.height), cornerRadius = r)
        if (monthGone != null) {
            val x = size.width * monthGone.coerceIn(0f, 1f)
            drawLine(marker, Offset(x, -2.dp.toPx()), Offset(x, size.height + 2.dp.toPx()), strokeWidth = 2.dp.toPx())
        }
    }
}

/**
 * Lays [first] out at [firstWidth] and [second] beside it in the rest of the width, stretched to the taller of the two,
 * so the "To watch" panel always ends level with the formula. Measured directly: the ring's figures animate and
 * shrink to fit, which intrinsic-height rows can't ask about.
 */
@Composable
private fun MatchedHeightPair(
    firstWidth: Dp,
    gap: Dp,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    Layout(contents = listOf(first, second)) { (firsts, seconds), constraints ->
        val firstPx = firstWidth.roundToPx().coerceAtMost(constraints.maxWidth)
        val secondPx = (constraints.maxWidth - firstPx - gap.roundToPx()).coerceAtLeast(0)
        val a = firsts.map { it.measure(Constraints.fixedWidth(firstPx)) }
        val aHeight = a.maxOfOrNull { it.height } ?: 0
        val height = maxOf(aHeight, seconds.maxOfOrNull { it.minIntrinsicHeight(secondPx) } ?: 0)
        val b = seconds.map { it.measure(Constraints.fixed(secondPx, height)) }
        layout(constraints.maxWidth, height) {
            a.forEach { it.placeRelative(0, 0) }
            b.forEach { it.placeRelative(constraints.maxWidth - secondPx, 0) }
        }
    }
}

/**
 * The budget card's left half: the share used as a ring exactly [ringSize] wide, then the month's formula as a short
 * statement — Budget, Spent (budgeted + unbudgeted), Remaining (or Overspent), Bills due, and Safe to spend on a tinted
 * line (0 in red once nothing is left). A past month closes on Remaining. Every figure is from [BudgetFormula].
 */
@Composable
private fun BudgetFigures(plan: BudgetPlan, burn: BudgetBurn, formula: BudgetFormula, ringSize: Dp) {
    val used = burn.consumedPercent ?: 0
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        ProgressRing(
            fraction = burn.consumedFraction,
            description = "$used percent of the ${monthLabel(plan.month)} budget spent",
            size = ringSize,
            strokeWidth = 12.dp,
            color = healthColor(plan.health),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$used%", style = AmountStyles.hero.copy(fontSize = 30.sp, lineHeight = 34.sp), maxLines = 1)
                Text("spent", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        BudgetFormulaLines(formula)
    }
}

@Composable
private fun BudgetFormulaLines(formula: BudgetFormula) {
    val ext = LucentoTheme.extendedColors
    val safe = formula.safeToSpend
    val bills = formula.billsDue
    // "Overspent" in red once spending passes the budget; "Remaining" in green otherwise. The word says it, not the colour.
    val remainingLabel = if (formula.overspent) "Overspent" else "Remaining"
    val remainingInk = if (formula.overspent) ext.negative else ext.positive
    Column(Modifier.fillMaxWidth()) {
        FormulaLine("Budget", formula.budget)
        FormulaLine("Spent", formula.used)
        // Spent is every expense: the part in budgeted categories plus the part outside them (as on the Budget page).
        Column(Modifier.padding(start = Spacing.sm, top = Spacing.xxs, bottom = Spacing.xs)) {
            FormulaLine("Budgeted", formula.budgeted, minor = true)
            FormulaLine("Unbudgeted", formula.unbudgeted, minor = true)
        }
        Hairline(Modifier.padding(vertical = Spacing.xs))
        if (safe != null && bills != null) {
            FormulaLine(remainingLabel, formula.remaining, ink = remainingInk)
            // A deduction, so it carries its minus sign as well as the red.
            FormulaLine("Bills due", -bills, ink = if (bills.isZero) null else ext.negative)
            Spacer(Modifier.height(Spacing.sm))
            // Always here for this month: 0 in red once bills or overspending take the rest, and the caption says why.
            FormulaResult(
                "Safe to spend",
                formula.safeToSpendShown ?: safe,
                warning = formula.nothingSafe,
                caption = when {
                    !formula.nothingSafe -> null
                    formula.overspent -> "You’re over budget this month"
                    else -> "Bills due take what’s left"
                },
            )
        } else {
            Spacer(Modifier.height(Spacing.xxs))
            FormulaResult(remainingLabel, formula.remaining, warning = formula.overspent)
        }
    }
}

/**
 * One line of the formula: the label and the figure, right-aligned and shrinking before it would wrap. [minor] lines
 * (the parts of Used) are smaller and muted; [ink] colours a line's label and figure (Remaining, Overspent, Bills due).
 * The sign is always in the figure itself.
 */
@Composable
private fun FormulaLine(label: String, money: Money, minor: Boolean = false, ink: Color? = null) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val color = ink ?: if (minor) muted else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (minor) 18.dp else 24.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = if (minor) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
            color = ink ?: muted,
            fontWeight = if (ink != null) FontWeight.SemiBold else null,
            maxLines = 1,
        )
        Spacer(Modifier.width(Spacing.sm))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            MoneyText(
                money = money,
                style = if (minor) AmountStyles.small else AmountStyles.row,
                color = color,
                fractionScale = 1f,
                minScale = 0.6f,
            )
        }
    }
}

/**
 * The formula's answer on a tinted line: the label over the figure, green while there's money left, red when not
 * ([warning]); [caption] says why in words, so the state never rests on colour.
 */
@Composable
private fun FormulaResult(label: String, money: Money, warning: Boolean = money.isNegative, caption: String? = null) {
    val ext = LucentoTheme.extendedColors
    val negative = warning
    val ink = if (negative) ext.negative else ext.positive
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(if (negative) ext.negativeContainer else ext.positiveContainer)
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = ink, maxLines = 1)
        MoneyText(
            money = money,
            style = AmountStyles.large,
            color = ink,
            fractionScale = 1f,
            minScale = 0.6f,
        )
        if (caption != null) Text(caption, style = MaterialTheme.typography.labelSmall, color = ink, maxLines = 2)
    }
}

/**
 * The budget card's right half, "To watch": up to five budgeted categories closest to their limit, each with its share
 * used (coloured by its own status, and always in words), a compact bar and what's left, then "See all" in the bottom
 * right corner (the Budget tab). A tap on a row opens that category's transactions for the month. [fillHeight]: beside
 * the formula, the rows spread over the panel's full height.
 */
@Composable
private fun WatchPanel(state: HomeUiState, onOpen: (Long) -> Unit, onSeeAll: () -> Unit, fillHeight: Boolean) {
    val panel = if (LucentoTheme.isDark) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(panel)
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
    ) {
        Text(
            "TO WATCH",
            style = OverlineStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clearAndSetSemantics { heading(); contentDescription = "To watch" },
        )
        Spacer(Modifier.height(Spacing.xs))
        val body = if (fillHeight) Modifier.weight(1f) else Modifier
        if (state.watchLines.isEmpty()) {
            Box(body.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                Text(
                    "Nothing spent in your budgeted categories in ${monthLabel(state.month)} yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(body.fillMaxWidth(), verticalArrangement = if (fillHeight) Arrangement.SpaceEvenly else Arrangement.Top) {
                state.watchLines.forEach { line -> WatchRow(line, onOpen) }
            }
        }
        LucentoTextButton(
            text = "See all",
            onClick = onSeeAll,
            modifier = Modifier.align(Alignment.End).semantics { contentDescription = "See all budget categories" },
        )
    }
}

@Composable
private fun WatchRow(line: CategoryBudgetLine, onOpen: (Long) -> Unit) {
    val percent = line.percentUsed ?: 0
    val name = line.category.name
    val left = line.remaining
    val leftShown = left?.let { shownAmount(if (it.isNegative) -it else it) }
    val leftText = left?.let { if (it.isNegative) "$leftShown over" else "$leftShown left" }
    val spentSpoken = spokenAmount(line.spent)
    val limitSpoken = line.limit?.let { spokenAmount(it) }.orEmpty()
    val leftSpoken = left?.let { val m = spokenAmount(if (it.isNegative) -it else it); if (it.isNegative) "$m over" else "$m left" }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.minTouch)
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable(role = Role.Button, onClickLabel = "Show $name transactions") { onOpen(line.category.id) }
            .clearAndSetSemantics {
                contentDescription = "$name, $percent percent of its budget used, " +
                    "$spentSpoken of $limitSpoken" +
                    leftSpoken?.let { ", $it" }.orEmpty()
                role = Role.Button
                onClick(label = "Show $name transactions") { onOpen(line.category.id); true }
            }
            .padding(vertical = Spacing.xs),
        verticalArrangement = Arrangement.Center,
    ) {
        // The name has the panel's full width (narrow phones would cut "Restaurants" beside a percentage).
        Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(Spacing.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShareBar(fraction = line.fraction, color = healthColor(line.health), modifier = Modifier.weight(1f))
            Spacer(Modifier.width(Spacing.sm))
            Text(
                "$percent%",
                style = AmountStyles.small.copy(fontWeight = FontWeight.Bold),
                color = healthTone(line.health).content,
                maxLines = 1,
            )
        }
        if (leftText != null) {
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                leftText,
                style = AmountStyles.small,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ------------------------------------------------------------------ Dashboard: shortcuts

/**
 * One shortcut tile. [spoken] is the full name TalkBack reads ("Debts and loans" for the "Debts" label). [off] marks a
 * module that is switched off: the tile is dashed and muted with an add badge, and a tap offers to turn it on.
 */
private data class Shortcut(
    val label: String,
    val spoken: String,
    val icon: ImageVector,
    val tone: TonePair,
    val onClick: () -> Unit,
    val off: Feature? = null,
)

private fun shortcutIcon(feature: Feature): ImageVector = when (feature) {
    Feature.SAVINGS_GOALS -> Icons.Outlined.TrackChanges
    Feature.INSIGHTS -> Icons.Outlined.BarChart
    Feature.DEBTS_LOANS -> Icons.Outlined.CreditCard
    else -> Icons.Outlined.CalendarMonth
}

private fun shortcutSpokenName(feature: Feature): String = when (feature) {
    Feature.SAVINGS_GOALS -> "Savings goals"
    Feature.INSIGHTS -> "Insights"
    Feature.DEBTS_LOANS -> "Debts and loans"
    Feature.RECURRING -> "Scheduled transactions"
    else -> feature.title
}

/**
 * Evenly spaced shortcuts: a soft-toned 54dp icon square with its label beneath. Modules that are switched off sit in
 * the same row as dashed tiles (unless the user hid them), each opening a small Turn on / Hide sheet.
 */
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
    var offering by remember { mutableStateOf<Feature?>(null) }
    val offers = if (state.moduleOffersHidden) emptySet() else f.rediscoverable().toSet()
    val brand = TonePair(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary)
    fun module(feature: Feature, tone: TonePair, open: () -> Unit): Shortcut? = when {
        f.isEnabled(feature) -> Shortcut(quickLinkName(feature), shortcutSpokenName(feature), shortcutIcon(feature), tone, open)
        feature in offers -> Shortcut(quickLinkName(feature), shortcutSpokenName(feature), shortcutIcon(feature), tone, { offering = feature }, off = feature)
        else -> null
    }
    val items = listOfNotNull(
        module(Feature.SAVINGS_GOALS, brand, actions.onOpenGoals),
        module(Feature.INSIGHTS, brand, actions.onOpenInsights),
        Shortcut("Awards", "Awards", Icons.Outlined.EmojiEvents, TonePair(ext.warningContainer, ext.warning), onOpenMilestones),
        module(Feature.DEBTS_LOANS, TonePair(ext.negativeContainer, ext.negative), actions.onOpenDebts),
        module(Feature.RECURRING, TonePair(ext.positiveContainer, ext.positive)) { actions.onOpenRecurring(null) },
    )
    val haptics = rememberHaptics()
    val textScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
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
                                .clickable(role = Role.Button, onClickLabel = if (item.off != null) "Turn on or hide" else "Open ${item.spoken}") {
                                    haptics.tick()
                                    item.onClick()
                                }
                                .semantics { contentDescription = if (item.off != null) "${item.spoken}, switched off" else item.spoken }
                                .padding(vertical = Spacing.xxs),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (item.off != null) OffModuleTile(item.icon) else ShortcutTile(item.icon, item.tone)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                item.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (item.off != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                    repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
    offering?.let { feature ->
        OffModuleSheet(
            feature = feature,
            onTurnOn = {
                offering = null
                onTurnOn(feature)
            },
            onHide = {
                offering = null
                onHideOffers()
            },
            onDismiss = { offering = null },
        )
    }
}

@Composable
private fun ShortcutTile(icon: ImageVector, tone: TonePair) {
    Box(
        Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(tone.container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tone.content, modifier = Modifier.size(26.dp))
    }
}

/** A switched-off module: a dashed, muted square with a small primary add badge on its corner. */
@Composable
private fun OffModuleTile(icon: ImageVector) {
    val colors = MaterialTheme.colorScheme
    val dash = colors.outline.copy(alpha = 0.55f)
    // Same 54dp footprint as an active tile, so labels line up; the badge overhangs the corner by 4dp.
    Box(Modifier.size(54.dp)) {
        Box(
            Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surfaceContainerLow)
                .drawBehind {
                    drawRoundRect(
                        dash,
                        cornerRadius = CornerRadius(16.dp.toPx()),
                        style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.size(26.dp))
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 4.dp, y = 4.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(colors.background)
                .padding(2.dp)
                .clip(CircleShape)
                .background(colors.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(12.dp))
        }
    }
}

/** What a switched-off module tile offers: turn it on (with anything it needs), or hide the switched-off tiles. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OffModuleSheet(feature: Feature, onTurnOn: () -> Unit, onHide: () -> Unit, onDismiss: () -> Unit) {
    LucentoBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.xl)) {
            SheetHeader(title = "${feature.title} is off", subtitle = feature.description, icon = shortcutIcon(feature))
            Spacer(Modifier.height(Spacing.lg))
            LucentoPrimaryButton(text = "Turn on ${quickLinkName(feature).lowercase(Locale.getDefault())}", onClick = onTurnOn)
            Spacer(Modifier.height(Spacing.xs))
            LucentoTextButton(
                text = "Hide switched-off modules",
                onClick = onHide,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Text(
                "Hidden modules stay in Settings › Enabled features.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ------------------------------------------------------------------ Dashboard: scheduled and recent

/**
 * What's still due this month (overdue first, then by date): scheduled items and credit card payments (from each card's
 * billing days; a tap opens the payment transfer, filled in). Empty months say so; past months don't show the section.
 * [onOpen] is null when Recurring is off and only card payments are listed.
 */
@Composable
private fun UpcomingScheduledCard(state: HomeUiState, onOpen: ((Long?) -> Unit)?, onPayCard: (CardDue) -> Unit) {
    LucentoCard(Modifier.fillMaxWidth(), contentPadding = Spacing.md) {
        if (onOpen != null) {
            CardHeaderWithSeeAll("Upcoming", "scheduled transactions", Icons.Outlined.CalendarMonth, onSeeAll = { onOpen(null) })
        } else {
            CardOverline("Upcoming", icon = Icons.Outlined.CalendarMonth)
            Spacer(Modifier.height(Spacing.sm))
        }
        val rows: List<Pair<LocalDate, Any>> = (state.scheduledThisMonth.map { it.dueDate to it } + state.cardDues.map { it.cycle.dueDate to it })
            .sortedBy { it.first }
        if (rows.isEmpty()) {
            MascotMessage(
                if (state.hasAnySchedules) "Nothing else due in ${monthLabel(state.month)}." else "Add rent, bills, subscriptions or salary and they show up here before they’re due.",
            )
            if (!state.hasAnySchedules && onOpen != null) LucentoTextButton(text = "Schedule a transaction", onClick = { onOpen(null) })
            return@LucentoCard
        }
        rows.forEachIndexed { index, (_, item) ->
            when (item) {
                is UpcomingPayment -> ScheduledRow(item, state, onClick = { onOpen?.invoke(item.schedule.id) })
                is CardDue -> CardDueRow(item, state.today, onClick = { onPayCard(item) })
            }
            if (index < rows.lastIndex) Hairline(inset = Sizes.iconTile + Spacing.md)
        }
    }
}

/**
 * A credit card's payment reminder in the scheduled-row layout: the card and its amount due, then the due date and
 * "Not in Safe to spend". A tap opens the payment transfer, filled in from the card's statement.
 */
@Composable
private fun CardDueRow(item: CardDue, today: LocalDate, onClick: () -> Unit) {
    val days = ChronoUnit.DAYS.between(today, item.cycle.dueDate)
    val relative = when {
        item.overdue -> "Overdue"
        days == 0L -> "Today"
        days == 1L -> "Tomorrow"
        else -> "In $days days"
    }
    val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClickLabel = "Pay ${item.wallet.name}", onClick = onClick)
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.lucento.app.core.designsystem.component.WalletIdentityTile(item.wallet, size = 38.dp)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${item.wallet.name} payment",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Spacing.sm))
                AmountText(money = -item.cycle.amountDue, showPlus = true, color = scheduledFlowColor(ScheduledFlow.TRANSFER))
            }
            Spacer(Modifier.height(Spacing.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${item.cycle.dueDate.format(dateFormat)} · Not in Safe to spend",
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

/** The month's newest entries, in the same rows as the Transactions list. */
@Composable
private fun RecentTransactionsCard(state: HomeUiState, actions: HomeActions) {
    LucentoCard(Modifier.fillMaxWidth(), contentPadding = Spacing.md) {
        CardHeaderWithSeeAll("Recent transactions", "transactions", Icons.Outlined.History, onSeeAll = actions.onOpenTransactions)
        if (state.recentInMonth.isEmpty()) {
            MascotMessage(
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

/** A card's caps overline with "See all" at its end; [what] completes the spoken action ("See all scheduled transactions"). */
@Composable
private fun CardHeaderWithSeeAll(title: String, what: String, icon: ImageVector, onSeeAll: () -> Unit) {
    CardOverline(title, icon = icon) {
        LucentoTextButton(text = "See all", onClick = onSeeAll, modifier = Modifier.semantics { contentDescription = "See all $what" })
    }
}

// ------------------------------------------------------------------ Part E: budget and pace
// The verdict words live in feature/budget/BudgetVisuals.kt (paceVerdict), shared with the Budget screen.

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
    val debt = schedule.toWalletId?.let { state.walletsById[it] }
    val kind = when {
        debt != null && schedule.isCollection -> "Collection from ${debt.name}"
        debt != null -> "Transfer to ${debt.name}"
        else -> category?.name ?: if (income) "Scheduled income" else "Scheduled expense"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClickLabel = "Review ${schedule.name}", onClick = onClick)
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (debt != null) {
            com.lucento.app.core.designsystem.component.WalletIdentityTile(debt, size = 38.dp)
        } else {
            CategoryTile(category = category, type = schedule.type, size = 38.dp)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(schedule.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(Spacing.sm))
                // Income green, expenses red, transfers to a card or loan in neutral ink (owner, 2026-11-03).
                AmountText(
                    money = schedule.signedOccurrence,
                    showPlus = true,
                    color = scheduledFlowColor(schedule.flow),
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

/**
 * Lucento Shared, on the theme's hero panel so it is noticed: "Olivia invited you to share finances" (the action opens the
 * invitation's review directly), or "You're in Olivia's Shared Space" when the account joined on another phone (the
 * action opens Settings › Lucento Shared to switch). Not now puts the prompt away; Settings › Lucento Shared keeps it.
 */
@Composable
private fun SpacePromptCard(prompt: com.lucento.app.data.sharedspace.SpacePrompt, actions: HomeActions) {
    when (prompt) {
        is com.lucento.app.data.sharedspace.SpacePrompt.Invitation ->
            com.lucento.app.feature.sharedspace.InvitationSpotlight(
                invitation = prompt.invitation,
                myName = null,
                actionLabel = "Review invitation",
                onAction = actions.onOpenSpacePrompt,
                dismissLabel = "Not now",
                onDismiss = actions.onDismissSpacePrompt,
            )
        is com.lucento.app.data.sharedspace.SpacePrompt.JoinedElsewhere ->
            com.lucento.app.core.designsystem.component.SpotlightCard(
                eyebrow = "Lucento Shared",
                title = "You’re in ${prompt.ownerName}’s Shared Space",
                body = "You joined on another phone. Switch this one to see ${prompt.ownerName}’s finances here too.",
                visual = { com.lucento.app.core.designsystem.component.PeoplePair(first = prompt.ownerName, second = null) },
                actionLabel = "Open",
                onAction = actions.onOpenSpacePrompt,
                dismissLabel = "Not now",
                onDismiss = actions.onDismissSpacePrompt,
            )
    }
}
