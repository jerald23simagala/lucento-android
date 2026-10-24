package com.lucento.app.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.lucento.app.core.designsystem.component.Confirmation
import com.lucento.app.core.designsystem.component.ConfirmationHost
import com.lucento.app.core.designsystem.component.EmptyState
import com.lucento.app.core.designsystem.component.LocalProfileChrome
import com.lucento.app.core.designsystem.component.LucentoMascot
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.component.LucentoTopBar
import com.lucento.app.core.designsystem.component.MascotMood
import com.lucento.app.core.designsystem.component.ProfileChrome
import com.lucento.app.core.designsystem.component.rememberHaptics
import com.lucento.app.core.designsystem.theme.Elevation
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.data.preferences.UserPreferences
import com.lucento.app.domain.Feature
import com.lucento.app.domain.FeatureSet
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import com.lucento.app.feature.budget.BudgetScreen
import com.lucento.app.feature.budget.CategorySetupScreen
import com.lucento.app.feature.budget.ManageCategoriesScreen
import com.lucento.app.feature.capture.ComposerStart
import com.lucento.app.feature.capture.TransactionComposerScreen
import com.lucento.app.feature.home.HomeActions
import com.lucento.app.feature.home.HomeScreen
import com.lucento.app.feature.insights.InsightsScreen
import com.lucento.app.feature.more.MoreDestination
import com.lucento.app.feature.more.MoreSheet
import com.lucento.app.feature.onboarding.OnboardingScreen
import com.lucento.app.feature.plan.DebtsScreen
import com.lucento.app.feature.plan.GoalsScreen
import com.lucento.app.feature.plan.RecurringScreen
import com.lucento.app.feature.profile.ProfileDestination
import com.lucento.app.feature.profile.ProfileSheet
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
import kotlin.reflect.KClass

// Primary destinations: the five tabs of the navigation dock.
@Serializable data object HomeRoute
@Serializable data object TransactionsRoute
@Serializable data object WalletsRoute
@Serializable data object InsightsRoute
@Serializable data object BudgetRoute

// Secondary destinations, pushed on top of the current tab; the dock stays visible.
@Serializable data class WalletDetailRoute(val walletId: Long)
@Serializable data object GoalsRoute
@Serializable data object DebtsRoute
@Serializable data class RecurringRoute(val scheduleId: Long = NO_SCHEDULE)
@Serializable data object CategoriesRoute

// Focused workflows: full screen, the dock is hidden.
@Serializable data object CategorySetupRoute
@Serializable data object SettingsRoute
@Serializable data object FeaturesRoute
/** The transaction composer: [start] is a [ComposerStart] name; [typeHint] a TransactionType name or empty. */
@Serializable data class ComposerRoute(val start: String = "TYPE", val walletId: Long = NO_WALLET, val typeHint: String = "")

const val NO_SCHEDULE = -1L
const val NO_WALLET = -1L

private data class Tab(val item: DockItem, val route: Any)

/**
 * The navigation dock: Home · Transactions · Wallets · Insights · Budget, then a "More" menu. Insights and Budget leave
 * the dock when the user switches those modules off (their data is kept).
 */
private fun tabs(features: FeatureSet): List<Tab> = listOfNotNull(
    Tab(DockItem("home", "Home", Icons.Outlined.Home, Icons.Filled.Home), HomeRoute),
    Tab(DockItem("transactions", "Transactions", Icons.AutoMirrored.Outlined.ReceiptLong, Icons.AutoMirrored.Filled.ReceiptLong), TransactionsRoute),
    Tab(DockItem("wallets", "Wallets", Icons.Outlined.AccountBalanceWallet, Icons.Filled.AccountBalanceWallet), WalletsRoute),
    if (features.isEnabled(Feature.INSIGHTS)) Tab(DockItem("insights", "Insights", Icons.Outlined.Insights, Icons.Filled.Insights), InsightsRoute) else null,
    if (features.isEnabled(Feature.BUDGETS)) Tab(DockItem("budget", "Budget", Icons.Outlined.PieChart, Icons.Filled.PieChart), BudgetRoute) else null,
)

private val PrimaryRoutes: List<Pair<String, KClass<out Any>>> = listOf(
    "home" to HomeRoute::class,
    "transactions" to TransactionsRoute::class,
    "wallets" to WalletsRoute::class,
    "insights" to InsightsRoute::class,
    "budget" to BudgetRoute::class,
)

/** Workflows that take the whole screen (capture, category setup, settings): the dock steps aside. */
private val FocusedRoutes: List<KClass<out Any>> = listOf(
    CategorySetupRoute::class,
    SettingsRoute::class,
    FeaturesRoute::class,
    ComposerRoute::class,
)

private fun NavDestination?.primaryKey(): String? =
    this?.hierarchy?.firstNotNullOfOrNull { d -> PrimaryRoutes.firstOrNull { d.hasRoute(it.second) }?.first }

private fun NavDestination?.isFocused(): Boolean =
    this?.hierarchy?.any { d -> FocusedRoutes.any { d.hasRoute(it) } } == true

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
    val tabs = tabs(features)
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val reducedMotion = LucentoTheme.reducedMotion
    val haptics = rememberHaptics()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val appViewModel: AppViewModel = viewModel(factory = AppViewModel.Factory)

    // One transaction editor for the whole app (the + sheet, wallet screens, history, scan, speak and type).
    val editorViewModel: TransactionEditorViewModel = viewModel(factory = TransactionEditorViewModel.Factory)
    val editorState by editorViewModel.state.collectAsStateWithLifecycle()
    var editorDraft by remember { mutableStateOf<TransactionDraft?>(null) }
    var editorNote by remember { mutableStateOf<String?>(null) }
    var transferDraft by remember { mutableStateOf<TransferDraft?>(null) }
    var transferNote by remember { mutableStateOf<String?>(null) }
    // Set only from a save callback that reported success, i.e. after the database write committed.
    var confirmation by remember { mutableStateOf<Confirmation?>(null) }

    var showMore by rememberSaveable { mutableStateOf(false) }
    var showProfile by rememberSaveable { mutableStateOf(false) }
    // The tab a pushed screen belongs to, so the dock keeps it highlighted (e.g. Goals opened from Home).
    var selectedTab by rememberSaveable { mutableStateOf("home") }

    LaunchedEffect(currentDestination) {
        currentDestination.primaryKey()?.let { selectedTab = it }
    }

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

    // A tab whose module is switched off while it is showing returns to Home rather than show a hidden module.
    LaunchedEffect(features) {
        val key = navController.currentDestination.primaryKey()
        if ((key == "insights" && !features.isEnabled(Feature.INSIGHTS)) || (key == "budget" && !features.isEnabled(Feature.BUDGETS))) {
            navigateTo(HomeRoute)
        }
    }

    fun requireWallets(): Boolean {
        if (editorState.loaded && editorState.wallets.isEmpty()) {
            navigateTo(WalletsRoute)
            notify("Add a wallet first — every transaction belongs to one.")
            return false
        }
        return true
    }

    fun openTransfer(from: Long? = null, to: Long? = null) {
        if (!requireWallets()) return
        if (editorState.loaded && editorState.wallets.size < 2) {
            navigateTo(WalletsRoute)
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

    /** The one way to add transactions: the composer (typed, spoken or scanned, reviewed as a batch). */
    fun openComposer(start: ComposerStart = ComposerStart.TYPE, walletId: Long? = null, typeHint: TransactionType? = null) {
        if (!requireWallets()) return
        push(ComposerRoute(start.name, walletId ?: NO_WALLET, typeHint?.name.orEmpty()))
    }

    val enableFeature: (Feature) -> Unit = { appViewModel.setFeatureEnabled(it, true) }
    val back: () -> Unit = { navController.popBackStack() }
    val focused = currentDestination.isFocused()
    val onPrimary = currentDestination == null || currentDestination.primaryKey() != null

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
        floatingActionButton = {
            // The app-wide "+ Add transaction", on every primary screen.
            AnimatedVisibility(
                visible = onPrimary && !focused,
                enter = scaleIn(tween(if (reducedMotion) 0 else 180)) + fadeIn(),
                exit = scaleOut(tween(if (reducedMotion) 0 else 120)) + fadeOut(),
            ) {
                FloatingActionButton(
                    onClick = {
                        haptics.tick()
                        openComposer()
                    },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = Elevation.sm, pressedElevation = Elevation.md),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add transaction", modifier = Modifier.size(28.dp))
                }
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = !focused,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                NavigationDock(
                    items = tabs.map { it.item },
                    selectedKey = selectedTab,
                    onSelect = { item ->
                        val tab = tabs.first { it.item.key == item.key }
                        selectedTab = item.key
                        navigateTo(tab.route)
                    },
                    onMore = { showMore = true },
                )
            }
        },
    ) { innerPadding ->
        CompositionLocalProvider(LocalProfileChrome provides ProfileChrome(preferences.displayName) { showProfile = true }) {
            Box(Modifier.fillMaxSize().padding(innerPadding)) {
                NavHost(
                    navController = navController,
                    startDestination = HomeRoute,
                    modifier = Modifier.fillMaxSize(),
                    // Tab switches fade through; pushed screens slide in from the trailing edge.
                    enterTransition = { if (reducedMotion) fadeIn(tween(0)) else fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.985f) },
                    exitTransition = { fadeOut(tween(if (reducedMotion) 0 else 120)) },
                ) {
                    composable<HomeRoute> {
                        HomeScreen(
                            actions = HomeActions(
                                onOpenBudget = { navigateTo(BudgetRoute) },
                                onChooseCategories = { push(CategorySetupRoute) },
                                onOpenInsights = { navigateTo(InsightsRoute) },
                                onOpenGoals = { push(GoalsRoute) },
                                onOpenRecurring = { id -> push(RecurringRoute(id ?: NO_SCHEDULE)) },
                            ),
                        )
                    }
                    composable<TransactionsRoute> {
                        TransactionsScreen(
                            onAddTransaction = { openComposer() },
                            onEditTransaction = { edit(it) },
                            onAddWallet = { navigateTo(WalletsRoute) },
                            onScanReceipt = if (features.isEnabled(Feature.RECEIPT_SCANNING)) ({ openComposer(ComposerStart.SCAN) }) else null,
                            onSpeak = if (features.isEnabled(Feature.VOICE_ENTRY)) ({ openComposer(ComposerStart.SPEAK) }) else null,
                        )
                    }
                    composable<WalletsRoute> {
                        WalletsScreen(
                            onBack = null,
                            onOpenWallet = { push(WalletDetailRoute(it)) },
                            allowAdding = features.isEnabled(Feature.MULTIPLE_WALLETS),
                        )
                    }
                    composable<InsightsRoute> {
                        Gated(Feature.INSIGHTS, features, enableFeature, onBack = { navigateTo(HomeRoute) }) { InsightsScreen() }
                    }
                    composable<BudgetRoute> {
                        Gated(Feature.BUDGETS, features, enableFeature, onBack = { navigateTo(HomeRoute) }) {
                            BudgetScreen(
                                onChooseCategories = { push(CategorySetupRoute) },
                                onManageCategories = { push(CategoriesRoute) },
                            )
                        }
                    }

                    pushed<WalletDetailRoute>(reducedMotion) { entry ->
                        val route = entry.toRoute<WalletDetailRoute>()
                        WalletDetailScreen(
                            walletId = route.walletId,
                            onBack = back,
                            onAddTransaction = { type, walletId -> openComposer(walletId = walletId, typeHint = type) },
                            onTransfer = { from, to -> openTransfer(from, to) },
                            onEditTransaction = { edit(it) },
                            transfersEnabled = features.isEnabled(Feature.MULTIPLE_WALLETS),
                        )
                    }
                    pushed<GoalsRoute>(reducedMotion) {
                        Gated(Feature.SAVINGS_GOALS, features, enableFeature, back) { GoalsScreen(onBack = back) }
                    }
                    pushed<DebtsRoute>(reducedMotion) {
                        Gated(Feature.DEBTS_LOANS, features, enableFeature, back) {
                            DebtsScreen(onBack = back, onOpenWallet = { push(WalletDetailRoute(it)) })
                        }
                    }
                    pushed<RecurringRoute>(reducedMotion) { entry ->
                        val route = entry.toRoute<RecurringRoute>()
                        Gated(Feature.RECURRING, features, enableFeature, back) {
                            RecurringScreen(onBack = back, openScheduleId = route.scheduleId.takeIf { it != NO_SCHEDULE })
                        }
                    }
                    pushed<CategoriesRoute>(reducedMotion) {
                        ManageCategoriesScreen(onBack = back, onChooseFromPresets = { push(CategorySetupRoute) })
                    }
                    pushed<CategorySetupRoute>(reducedMotion) { CategorySetupScreen(onDone = back) }
                    pushed<SettingsRoute>(reducedMotion) { SettingsScreen(onBack = back) }
                    pushed<FeaturesRoute>(reducedMotion) { EnabledFeaturesScreen(onBack = back) }
                    pushed<ComposerRoute>(reducedMotion) { entry ->
                        val route = entry.toRoute<ComposerRoute>()
                        TransactionComposerScreen(
                            onBack = back,
                            onSaved = { message -> confirmation = Confirmation(message) },
                            onAddWallet = { navigateTo(WalletsRoute) },
                            voiceEnabled = features.isEnabled(Feature.VOICE_ENTRY),
                            scanEnabled = features.isEnabled(Feature.RECEIPT_SCANNING),
                            transfersEnabled = features.isEnabled(Feature.MULTIPLE_WALLETS),
                            start = runCatching { ComposerStart.valueOf(route.start) }.getOrDefault(ComposerStart.TYPE),
                            walletId = route.walletId.takeIf { it != NO_WALLET },
                            typeHint = route.typeHint.takeIf { it.isNotEmpty() }?.let { name -> runCatching { TransactionType.valueOf(name) }.getOrNull() },
                        )
                    }
                }
                ConfirmationHost(confirmation = confirmation, onDismissed = { confirmation = null })
            }
        }
    }

    if (showMore) {
        MoreSheet(
            onOpen = { destination ->
                when (destination) {
                    MoreDestination.GOALS -> push(GoalsRoute)
                    MoreDestination.RECURRING -> push(RecurringRoute())
                    MoreDestination.DEBTS -> push(DebtsRoute)
                    MoreDestination.CATEGORIES -> push(CategoriesRoute)
                }
            },
            onDismiss = { showMore = false },
        )
    }

    if (showProfile) {
        ProfileSheet(
            preferences = preferences,
            onSaveName = appViewModel::setDisplayName,
            onTheme = appViewModel::setThemeMode,
            onOpen = { destination ->
                when (destination) {
                    ProfileDestination.SETTINGS -> push(SettingsRoute)
                    ProfileDestination.FEATURES -> push(FeaturesRoute)
                }
            },
            onDismiss = { showProfile = false },
        )
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
                        confirmation = Confirmation(
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
                    if (error == null) confirmation = Confirmation(if (toSave.group != null) "Transfer updated" else "Transfer saved")
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
 * a back-stack entry, the + sheet) explains why and offers to turn it back on — its data is still there.
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
