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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.PieChart
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
import com.lucento.app.feature.onboarding.OnboardingScreen
import com.lucento.app.feature.plan.DebtsScreen
import com.lucento.app.feature.plan.GoalsScreen
import com.lucento.app.feature.plan.RecurringScreen
import com.lucento.app.feature.profile.ProfileDestination
import com.lucento.app.feature.profile.ProfileSheet
import com.lucento.app.feature.settings.EnabledFeaturesScreen
import com.lucento.app.feature.settings.SettingsScreen
import com.lucento.app.feature.transactions.TransactionDraft
import com.lucento.app.feature.transactions.TransactionDetailScreen
import com.lucento.app.feature.transactions.TransactionEditorScreen
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
/** Full-page editor for the transaction held in LucentoRoot's editor draft (opened from Edit on a row). */
@Serializable data object TransactionEditRoute
/** Read-only View Transaction page; Edit there opens [TransactionEditRoute] (or the transfer sheet). */
@Serializable data class TransactionViewRoute(val transactionId: Long)
/** The transaction composer: [start] is a [ComposerStart] name; [typeHint] a TransactionType name or empty. */
@Serializable data class ComposerRoute(val start: String = "TYPE", val walletId: Long = NO_WALLET, val typeHint: String = "")

const val NO_SCHEDULE = -1L
const val NO_WALLET = -1L

private data class Tab(val item: DockItem, val route: Any)

/**
 * The navigation dock: Home · Transactions · (+) · Insights · Budgets, with the transaction-entry button in the centre.
 * Insights and Budgets leave the dock when the user switches those modules off (their data is kept). Wallets, goals,
 * scheduled transactions, debts and categories open from Home's Net worth card.
 */
private fun tabs(features: FeatureSet): List<Tab> = listOfNotNull(
    Tab(DockItem("home", "Home", Icons.Outlined.Home, Icons.Filled.Home), HomeRoute),
    Tab(DockItem("transactions", "Transactions", Icons.AutoMirrored.Outlined.ReceiptLong, Icons.AutoMirrored.Filled.ReceiptLong), TransactionsRoute),
    if (features.isEnabled(Feature.INSIGHTS)) Tab(DockItem("insights", "Insights", Icons.Outlined.Insights, Icons.Filled.Insights), InsightsRoute) else null,
    if (features.isEnabled(Feature.BUDGETS)) Tab(DockItem("budget", "Budgets", Icons.Outlined.PieChart, Icons.Filled.PieChart), BudgetRoute) else null,
)

private val PrimaryRoutes: List<Pair<String, KClass<out Any>>> = listOf(
    "home" to HomeRoute::class,
    "transactions" to TransactionsRoute::class,
    "insights" to InsightsRoute::class,
    "budget" to BudgetRoute::class,
)

/** Workflows that take the whole screen (capture, category setup, settings): the dock steps aside. */
private val FocusedRoutes: List<KClass<out Any>> = listOf(
    CategorySetupRoute::class,
    SettingsRoute::class,
    FeaturesRoute::class,
    ComposerRoute::class,
    TransactionEditRoute::class,
    TransactionViewRoute::class,
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
    var transferDraft by remember { mutableStateOf<TransferDraft?>(null) }
    var transferNote by remember { mutableStateOf<String?>(null) }
    // Set only from a save callback that reported success, i.e. after the database write committed.
    var confirmation by remember { mutableStateOf<Confirmation?>(null) }

    var showProfile by rememberSaveable { mutableStateOf(false) }
    // The tab a pushed screen belongs to, so the dock keeps it highlighted (e.g. Goals opened from Home).
    var selectedTab by rememberSaveable { mutableStateOf("home") }

    LaunchedEffect(currentDestination) {
        currentDestination.primaryKey()?.let { selectedTab = it }
    }

    // A tab always opens at its own root: screens pushed on top of it (Goals, Wallets, the composer…) are cleared rather
    // than saved and restored, so tapping Home after wandering through sub-pages shows Home itself.
    fun navigateTo(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id)
            launchSingleTop = true
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
            push(WalletsRoute)
            notify("Add a wallet first — every transaction belongs to one.")
            return false
        }
        return true
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
            editorDraft = transaction.toDraft()
            push(TransactionEditRoute)
        }
    }

    fun view(transaction: Transaction) = push(TransactionViewRoute(transaction.id))

    /** The one way to add transactions: the composer (typed, spoken or scanned, reviewed as a batch). */
    fun openComposer(start: ComposerStart = ComposerStart.TYPE, walletId: Long? = null, typeHint: TransactionType? = null) {
        if (!requireWallets()) return
        push(ComposerRoute(start.name, walletId ?: NO_WALLET, typeHint?.name.orEmpty()))
    }

    val enableFeature: (Feature) -> Unit = { appViewModel.setFeatureEnabled(it, true) }
    val back: () -> Unit = { navController.popBackStack() }
    val focused = currentDestination.isFocused()

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
                    onAdd = {
                        haptics.tick()
                        openComposer()
                    },
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
                                onOpenWallets = { push(WalletsRoute) },
                                onOpenDebts = { push(DebtsRoute) },
                                onOpenSettings = { push(SettingsRoute) },
                            ),
                        )
                    }
                    composable<TransactionsRoute> {
                        TransactionsScreen(
                            onAddTransaction = { openComposer() },
                            onViewTransaction = { view(it) },
                            onEditTransaction = { edit(it) },
                            onDeleteTransaction = { t ->
                                editorViewModel.delete(t.id) { notify(if (t.isTransfer) "Transfer deleted" else "Transaction deleted") }
                            },
                            onAddWallet = { push(WalletsRoute) },
                            onScanReceipt = if (features.isEnabled(Feature.RECEIPT_SCANNING)) ({ openComposer(ComposerStart.SCAN) }) else null,
                            onSpeak = if (features.isEnabled(Feature.VOICE_ENTRY)) ({ openComposer(ComposerStart.SPEAK) }) else null,
                        )
                    }
                    pushed<WalletsRoute>(reducedMotion) {
                        WalletsScreen(
                            onBack = back,
                            onOpenWallet = { push(WalletDetailRoute(it)) },
                            allowAdding = features.isEnabled(Feature.MULTIPLE_WALLETS),
                            onOpenDebts = if (features.isEnabled(Feature.DEBTS_LOANS)) ({ push(DebtsRoute) }) else null,
                            onScanReceipt = if (features.isEnabled(Feature.RECEIPT_SCANNING)) ({ openComposer(ComposerStart.SCAN) }) else null,
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
                            onEditTransaction = { view(it) },
                            transfersEnabled = features.isEnabled(Feature.MULTIPLE_WALLETS),
                            onAdjusted = { notify("Balance adjusted") },
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
                    pushed<TransactionViewRoute>(reducedMotion) { entry ->
                        val route = entry.toRoute<TransactionViewRoute>()
                        TransactionDetailScreen(
                            transactionId = route.transactionId,
                            onBack = {
                                // Guarded: the page also steps back by itself once its transaction is deleted.
                                if (navController.currentBackStackEntry?.id == entry.id) navController.popBackStack()
                            },
                            onEdit = { edit(it) },
                        )
                    }
                    pushed<TransactionEditRoute>(reducedMotion) {
                        val draft = editorDraft
                        // The draft lives only in memory: after process death there is nothing to edit, so step back.
                        if (draft == null) {
                            LaunchedEffect(Unit) { navController.popBackStack() }
                        } else {
                            // The draft is left in place (the next Edit replaces it) so the exit transition still has content.
                            fun close() {
                                if (navController.currentDestination?.hasRoute(TransactionEditRoute::class) == true) navController.popBackStack()
                            }
                            TransactionEditorScreen(
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
                                onDelete = { id ->
                                    editorViewModel.delete(id) { notify("Transaction deleted") }
                                    // Leave both the editor and the View page of the transaction that no longer exists.
                                    navController.popBackStack<TransactionEditRoute>(inclusive = true)
                                    if (navController.currentDestination?.hasRoute(TransactionViewRoute::class) == true) {
                                        navController.popBackStack()
                                    }
                                },
                                onClose = ::close,
                                showWalletPicker = features.isEnabled(Feature.MULTIPLE_WALLETS),
                            )
                        }
                    }
                    pushed<ComposerRoute>(reducedMotion) { entry ->
                        val route = entry.toRoute<ComposerRoute>()
                        TransactionComposerScreen(
                            onBack = back,
                            onSaved = { message ->
                                confirmation = Confirmation(message)
                                // Land on the history, where the batch that was just saved is now listed.
                                selectedTab = "transactions"
                                navigateTo(TransactionsRoute)
                            },
                            onAddWallet = { push(WalletsRoute) },
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
