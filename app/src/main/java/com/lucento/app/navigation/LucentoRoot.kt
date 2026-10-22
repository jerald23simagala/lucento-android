package com.lucento.app.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.lucento.app.AppViewModel
import com.lucento.app.core.designsystem.component.EmptyState
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.LucentoMascot
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.component.LucentoTopBar
import com.lucento.app.core.designsystem.component.MascotMood
import com.lucento.app.core.designsystem.component.rememberHaptics
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.data.preferences.UserPreferences
import com.lucento.app.domain.Feature
import com.lucento.app.domain.FeatureSet
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.capture.QuickEntryProposal
import com.lucento.app.feature.capture.QuickAddScreen
import com.lucento.app.feature.capture.ReceiptResult
import com.lucento.app.feature.capture.ReceiptScanScreen
import com.lucento.app.feature.capture.VoiceEntryScreen
import com.lucento.app.feature.home.HomeActions
import com.lucento.app.feature.home.HomeScreen
import com.lucento.app.feature.insights.InsightsScreen
import com.lucento.app.feature.more.MoreScreen
import com.lucento.app.feature.onboarding.OnboardingScreen
import com.lucento.app.feature.plan.BudgetsScreen
import com.lucento.app.feature.plan.DebtsScreen
import com.lucento.app.feature.plan.GoalsScreen
import com.lucento.app.feature.plan.PlanScreen
import com.lucento.app.feature.plan.RecurringScreen
import com.lucento.app.feature.settings.EnabledFeaturesScreen
import com.lucento.app.feature.settings.SettingsScreen
import com.lucento.app.feature.transactions.TransactionDraft
import com.lucento.app.feature.transactions.TransactionEditorSheet
import com.lucento.app.feature.transactions.TransactionEditorViewModel
import com.lucento.app.feature.transactions.TransactionsScreen
import com.lucento.app.feature.transactions.TransferDraft
import com.lucento.app.feature.transactions.TransferEditorSheet
import com.lucento.app.feature.transactions.toDraft
import com.lucento.app.feature.wallets.WalletDetailScreen
import com.lucento.app.feature.wallets.WalletsScreen
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.LocalDate

// Primary destinations (bottom navigation).
@Serializable data object HomeRoute
@Serializable data object TransactionsRoute
@Serializable data object QuickAddRoute
@Serializable data object InsightsRoute
@Serializable data object MoreRoute

// Pushed destinations.
@Serializable data object WalletsRoute
@Serializable data class WalletDetailRoute(val walletId: Long)
@Serializable data object PlanRoute
@Serializable data object BudgetsRoute
@Serializable data object GoalsRoute
@Serializable data object DebtsRoute
@Serializable data object RecurringRoute
@Serializable data object SettingsRoute
@Serializable data object FeaturesRoute
@Serializable data object ScanReceiptRoute
@Serializable data object VoiceEntryRoute

private data class TopLevelDestination(
    val route: Any,
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
    val isAdd: Boolean = false,
)

/*
 * Five primary destinations: Home · Transactions · Add · Insights · More. Insights disappears from the
 * bar when the user turns it off; everything else secondary (Wallets, Plan and its screens, Settings)
 * lives behind More and is pushed on top, hiding the bar.
 */
private fun topLevelDestinations(features: FeatureSet): List<TopLevelDestination> = listOfNotNull(
    TopLevelDestination(HomeRoute, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    TopLevelDestination(TransactionsRoute, "Transactions", Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong),
    TopLevelDestination(QuickAddRoute, "Add", Icons.Filled.Add, Icons.Filled.Add, isAdd = true),
    if (features.isEnabled(Feature.INSIGHTS)) TopLevelDestination(InsightsRoute, "Insights", Icons.Filled.Insights, Icons.Outlined.Insights) else null,
    TopLevelDestination(MoreRoute, "More", Icons.Filled.MoreHoriz, Icons.Outlined.MoreHoriz),
)

private fun NavDestination?.isTopLevel(destinations: List<TopLevelDestination>): Boolean =
    this?.hierarchy?.any { d -> destinations.any { d.hasRoute(it.route::class) } } == true

/** Switches between first-run onboarding and the main app once preferences are known. */
@Composable
fun LucentoRoot(preferences: UserPreferences) {
    AnimatedContent(
        targetState = preferences.onboardingComplete,
        transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(200)) },
        label = "root",
    ) { onboarded ->
        if (onboarded) MainScaffold(preferences) else OnboardingScreen()
    }
}

@Composable
private fun MainScaffold(preferences: UserPreferences, navController: NavHostController = rememberNavController()) {
    val features = preferences.features
    val destinations = topLevelDestinations(features)
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val haptics = rememberHaptics()
    val reducedMotion = LucentoTheme.reducedMotion
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val appViewModel: AppViewModel = viewModel(factory = AppViewModel.Factory)

    // One transaction editor for the whole app (Home, Add, account screens, history, scan and voice).
    val editorViewModel: TransactionEditorViewModel = viewModel(factory = TransactionEditorViewModel.Factory)
    val editorState by editorViewModel.state.collectAsStateWithLifecycle()
    var editorDraft by remember { mutableStateOf<TransactionDraft?>(null) }
    var editorNote by remember { mutableStateOf<String?>(null) }
    var transferDraft by remember { mutableStateOf<TransferDraft?>(null) }
    var transferNote by remember { mutableStateOf<String?>(null) }
    val separator = remember { MoneyFormatter.decimalSeparator() }

    fun navigateTo(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun push(route: Any) {
        navController.navigate(route) { launchSingleTop = true }
    }

    fun notify(message: String) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    // If Insights is turned off while it is showing, return Home rather than show a hidden module.
    LaunchedEffect(features) {
        val onInsights = navController.currentDestination?.hierarchy?.any { it.hasRoute(InsightsRoute::class) } == true
        if (onInsights && !features.isEnabled(Feature.INSIGHTS)) navigateTo(HomeRoute)
    }

    fun requireWallets(): Boolean {
        if (editorState.loaded && editorState.wallets.isEmpty()) {
            push(WalletsRoute)
            notify("Add a wallet first — every transaction belongs to one.")
            return false
        }
        return true
    }

    fun openNew(type: TransactionType, walletId: Long? = null, categoryId: Long? = null) {
        if (!requireWallets()) return
        editorNote = null
        editorDraft = editorViewModel.newDraft(type, walletId).copy(categoryId = categoryId)
    }

    fun openTransfer(from: Long? = null, to: Long? = null) {
        if (!requireWallets()) return
        if (editorState.loaded && editorState.wallets.size < 2) {
            push(WalletsRoute)
            notify("Add a second account to move money between them.")
            return
        }
        transferNote = null
        transferDraft = editorViewModel.newTransferDraft(from, to)
    }

    fun edit(transaction: Transaction) {
        val group = transaction.transferGroup
        if (group != null) {
            val transfer = editorState.transfers[group] ?: return
            transferNote = null
            transferDraft = transfer.toDraft()
        } else {
            editorNote = null
            editorDraft = transaction.toDraft()
        }
    }

    fun reviewProposal(p: QuickEntryProposal) {
        if (!requireWallets()) return
        val amountText = p.amount?.toPlainString()?.replace('.', separator).orEmpty()
        navController.popBackStack()
        if (p.type.isTransfer) {
            transferNote = "From what you said — check both accounts and the amount before saving."
            transferDraft = editorViewModel.newTransferDraft(p.walletId, p.toWalletId)
                .copy(amountText = amountText, date = p.date, note = p.note)
        } else {
            editorNote = "From what you said — check every field before saving."
            editorDraft = editorViewModel.newDraft(p.type, p.walletId)
                .copy(categoryId = p.categoryId, amountText = amountText, date = p.date, note = p.note)
        }
    }

    fun reviewReceipt(r: ReceiptResult) {
        if (!requireWallets()) return
        navController.popBackStack()
        editorNote = if (r.amount == null && r.merchant == null) null else "From your receipt — check the amount, date and account before saving."
        editorDraft = editorViewModel.newDraft(TransactionType.EXPENSE).copy(
            amountText = r.amount?.toPlainString()?.replace('.', separator).orEmpty(),
            date = r.date ?: LocalDate.now(),
            note = r.merchant.orEmpty(),
        )
    }

    val enableFeature: (Feature) -> Unit = { appViewModel.setFeatureEnabled(it, true) }
    val back: () -> Unit = { navController.popBackStack() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = MaterialTheme.shapes.medium,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = currentDestination == null || currentDestination.isTopLevel(destinations),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                Column {
                    Hairline()
                    NavigationBar(
                        containerColor = if (LucentoTheme.isDark) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerLowest,
                        tonalElevation = 0.dp,
                    ) {
                        destinations.forEach { destination ->
                            val selected = currentDestination?.hierarchy?.any { it.hasRoute(destination.route::class) } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (!selected) haptics.tick()
                                    navigateTo(destination.route)
                                },
                                icon = {
                                    if (destination.isAdd) {
                                        // The capture action stands out as a filled circle, as in the mockup.
                                        Box(
                                            Modifier
                                                .size(44.dp)
                                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                                        }
                                    } else {
                                        Icon(if (selected) destination.selectedIcon else destination.icon, contentDescription = null)
                                    }
                                },
                                label = { Text(destination.label, style = MaterialTheme.typography.labelMedium, maxLines = 1) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = if (destination.isAdd) Color.Transparent else MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            // Top-level switches fade through; pushed screens slide in from the trailing edge.
            enterTransition = { if (reducedMotion) fadeIn(tween(0)) else fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.985f) },
            exitTransition = { fadeOut(tween(if (reducedMotion) 0 else 120)) },
        ) {
            composable<HomeRoute> {
                HomeScreen(
                    actions = HomeActions(
                        onAddTransaction = { openNew(it) },
                        onTransfer = { openTransfer() },
                        onScanReceipt = { push(ScanReceiptRoute) },
                        onSpeak = { push(VoiceEntryRoute) },
                        onEditTransaction = { edit(it) },
                        onOpenTransactions = { navigateTo(TransactionsRoute) },
                        onOpenInsights = { navigateTo(InsightsRoute) },
                        onOpenBudgets = { push(BudgetsRoute) },
                        onOpenRecurring = { push(RecurringRoute) },
                    ),
                )
            }
            composable<TransactionsRoute> {
                TransactionsScreen(
                    onAddTransaction = { navigateTo(QuickAddRoute) },
                    onEditTransaction = { edit(it) },
                    onAddWallet = { push(WalletsRoute) },
                )
            }
            composable<QuickAddRoute> {
                QuickAddScreen(
                    onManual = { type, categoryId -> openNew(type, categoryId = categoryId) },
                    onTransfer = { openTransfer() },
                    onScan = { push(ScanReceiptRoute) },
                    onSpeak = { push(VoiceEntryRoute) },
                )
            }
            composable<InsightsRoute> {
                Gated(Feature.INSIGHTS, features, enableFeature, onBack = { navigateTo(HomeRoute) }) { InsightsScreen() }
            }
            composable<MoreRoute> {
                MoreScreen(
                    onOpenWallets = { push(WalletsRoute) },
                    onOpenPlan = { push(PlanRoute) },
                    onOpenSettings = { push(SettingsRoute) },
                    onOpenFeatures = { push(FeaturesRoute) },
                )
            }

            pushed<WalletsRoute>(reducedMotion) {
                WalletsScreen(
                    onBack = back,
                    onOpenWallet = { push(WalletDetailRoute(it)) },
                    allowAdding = features.isEnabled(Feature.MULTIPLE_WALLETS),
                )
            }
            pushed<WalletDetailRoute>(reducedMotion) { entry ->
                val route = entry.toRoute<WalletDetailRoute>()
                WalletDetailScreen(
                    walletId = route.walletId,
                    onBack = back,
                    onAddTransaction = { type, walletId -> openNew(type, walletId) },
                    onTransfer = { from, to -> openTransfer(from, to) },
                    onEditTransaction = { edit(it) },
                    transfersEnabled = features.isEnabled(Feature.MULTIPLE_WALLETS),
                )
            }
            pushed<PlanRoute>(reducedMotion) {
                PlanScreen(
                    onBack = back,
                    onOpenBudgets = { push(BudgetsRoute) },
                    onOpenGoals = { push(GoalsRoute) },
                    onOpenDebts = { push(DebtsRoute) },
                    onOpenRecurring = { push(RecurringRoute) },
                    onOpenSettings = { push(FeaturesRoute) },
                )
            }
            pushed<BudgetsRoute>(reducedMotion) {
                Gated(Feature.BUDGETS, features, enableFeature, back) { BudgetsScreen(onBack = back) }
            }
            pushed<GoalsRoute>(reducedMotion) {
                Gated(Feature.SAVINGS_GOALS, features, enableFeature, back) { GoalsScreen(onBack = back) }
            }
            pushed<DebtsRoute>(reducedMotion) {
                Gated(Feature.DEBTS_LOANS, features, enableFeature, back) {
                    DebtsScreen(onBack = back, onOpenWallet = { push(WalletDetailRoute(it)) })
                }
            }
            pushed<RecurringRoute>(reducedMotion) {
                Gated(Feature.RECURRING, features, enableFeature, back) { RecurringScreen(onBack = back) }
            }
            pushed<SettingsRoute>(reducedMotion) { SettingsScreen(onBack = back) }
            pushed<FeaturesRoute>(reducedMotion) { EnabledFeaturesScreen(onBack = back) }
            pushed<ScanReceiptRoute>(reducedMotion) {
                Gated(Feature.RECEIPT_SCANNING, features, enableFeature, back) {
                    ReceiptScanScreen(onBack = back, onReview = { reviewReceipt(it) })
                }
            }
            pushed<VoiceEntryRoute>(reducedMotion) {
                Gated(Feature.VOICE_ENTRY, features, enableFeature, back) {
                    VoiceEntryScreen(
                        wallets = editorState.wallets,
                        categories = editorState.categories,
                        onBack = back,
                        onReview = { reviewProposal(it) },
                    )
                }
            }
        }
    }

    editorDraft?.let { draft ->
        TransactionEditorSheet(
            initial = draft,
            wallets = editorState.wallets,
            categories = editorState.categories,
            onSave = { toSave, decimal, onResult ->
                editorViewModel.save(toSave, decimal) { error ->
                    onResult(error)
                    if (error == null) {
                        notify(
                            when {
                                toSave.id != null -> "Changes saved"
                                toSave.type == TransactionType.INCOME -> "Income added"
                                else -> "Expense added"
                            }
                        )
                    }
                }
            },
            onDelete = { id -> editorViewModel.delete(id) { notify("Transaction deleted") } },
            onDismiss = {
                editorDraft = null
                editorNote = null
            },
            onSwitchToTransfer = if (features.isEnabled(Feature.MULTIPLE_WALLETS) && editorState.wallets.size > 1) {
                {
                    editorDraft = null
                    openTransfer(draft.walletId)
                }
            } else {
                null
            },
            showWalletPicker = features.isEnabled(Feature.MULTIPLE_WALLETS),
            sourceNote = editorNote,
        )
    }

    transferDraft?.let { draft ->
        TransferEditorSheet(
            initial = draft,
            wallets = editorState.wallets,
            onSave = { toSave, decimal, onResult ->
                editorViewModel.saveTransfer(toSave, decimal) { error ->
                    onResult(error)
                    if (error == null) notify(if (toSave.group != null) "Transfer updated" else "Transfer saved")
                }
            },
            onDelete = draft.group?.let { group ->
                {
                    val leg = editorState.transfers[group]?.outLeg
                    if (leg != null) editorViewModel.delete(leg.id) { notify("Transfer deleted") }
                }
            },
            onDismiss = {
                transferDraft = null
                transferNote = null
            },
            sourceNote = transferNote,
        )
    }
}

/** A pushed screen: slides in from the trailing edge (instant when animations are off). */
private inline fun <reified T : Any> androidx.navigation.NavGraphBuilder.pushed(
    reducedMotion: Boolean,
    noinline content: @Composable (androidx.navigation.NavBackStackEntry) -> Unit,
) {
    composable<T>(
        enterTransition = { slideInHorizontally(tween(if (reducedMotion) 0 else 260)) { it / 4 } + fadeIn(tween(200)) },
        exitTransition = { fadeOut(tween(120)) },
        popEnterTransition = { fadeIn(tween(200)) },
        popExitTransition = { slideOutHorizontally(tween(if (reducedMotion) 0 else 220)) { it / 4 } + fadeOut(tween(180)) },
    ) { entry -> content(entry) }
}

/**
 * Shows [content] only when [feature] is usable. Reaching a module that is switched off (an old link,
 * a back-stack entry) explains why and offers to turn it back on — its data is still there.
 */
@Composable
private fun Gated(
    feature: Feature,
    features: FeatureSet,
    onEnable: (Feature) -> Unit,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (features.isEnabled(feature)) {
        content()
        return
    }
    val missing = features.missingRequirements(feature)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter),
    ) {
        LucentoTopBar(title = feature.title, onBack = onBack)
        EmptyState(
            title = "${feature.title} is turned off",
            body = if (features.isSwitchedOn(feature) && missing.isNotEmpty()) {
                "It needs ${missing.joinToString { it.title }} to work. Your saved data is safe and will be here when it’s on."
            } else {
                "Your saved data is safe — turning it back on brings everything back."
            },
            illustration = { LucentoMascot(size = 96.dp, mood = MascotMood.Curious) },
            action = {
                Column(Modifier.fillMaxWidth()) {
                    LucentoPrimaryButton(
                        text = "Turn on ${feature.title.lowercase()}",
                        onClick = {
                            missing.forEach(onEnable)
                            onEnable(feature)
                        },
                    )
                    LucentoTextButton(text = "Not now", onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            },
        )
    }
}
