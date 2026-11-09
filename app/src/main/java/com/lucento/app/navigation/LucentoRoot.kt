package com.lucento.app.navigation

import com.lucento.app.feature.guide.GuideScreen
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AccountBalanceWallet
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.outlined.Home
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
import com.lucento.app.core.designsystem.component.MascotScene
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
import com.lucento.app.feature.plan.RecurringEditorScreen
import com.lucento.app.feature.plan.GoalsScreen
import com.lucento.app.feature.plan.RecurringScreen
import com.lucento.app.feature.profile.ProfileDestination
import com.lucento.app.feature.profile.ProfileSheet
import com.lucento.app.feature.profile.ProfilePhotoState
import com.lucento.app.feature.settings.EnabledFeaturesScreen
import com.lucento.app.feature.settings.SettingsPage
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
import androidx.compose.foundation.layout.consumeWindowInsets

// Primary destinations: the five tabs of the navigation dock.
@Serializable data object HomeRoute
@Serializable data object TransactionsRoute
@Serializable data object WalletsRoute
@Serializable data object InsightsRoute
/** Ask Lucento: questions about the user's money and the preset reports (opened from Insights' hero). */
@Serializable data object AskRoute
@Serializable data object BudgetRoute
/** The guided setup after onboarding (wallets, debts, budget, goal, first transactions, welcome). */
@Serializable data object GuideRoute

// Secondary destinations, pushed on top of the current tab; the dock stays visible.
@Serializable data class WalletDetailRoute(val walletId: Long)
@Serializable data object GoalsRoute
@Serializable data object DebtsRoute
@Serializable data class RecurringRoute(val scheduleId: Long = NO_SCHEDULE)

/**
 * The full-page recurring payment editor: [scheduleId] edits one; [debtWalletId] + [firstDueEpochDay] start a debt
 * payment prefilled from that card or loan (after saving it with a payment reminder, see WalletEditorSheet).
 */
@Serializable data class RecurringEditRoute(
    val scheduleId: Long = NO_SCHEDULE,
    val debtWalletId: Long = NO_SCHEDULE,
    val firstDueEpochDay: Long = 0,
)
@Serializable data object CategoriesRoute

// Focused workflows: full screen, the dock is hidden.
@Serializable data object CategorySetupRoute
/** Settings: the hub, or one of its pages ([com.lucento.app.feature.settings.SettingsPage] key; empty = hub). */
@Serializable data class SettingsRoute(
    val page: String = "",
    /** Test builds: on the Account page, open Google's account picker straight away (profile menu › Switch account). */
    val switchAccount: Boolean = false,
)
@Serializable data object FeaturesRoute
/**
 * Settings › Lucento Shared (docs/shared): create a Shared Space, invite, join, remove, leave. [reviewInvite] (an
 * invitation id, from Home's card) opens straight on that invitation's review; empty = the page.
 */
@Serializable data class SharedSpaceRoute(val reviewInvite: String = "")
/** Lucento Pro: the plan, what each plan includes, restore (docs/MONETIZATION.md §5.5). [from] names the lock that led here. */
@Serializable data class ProRoute(val from: String = "")
/** Full-page editor for the transaction held in LucentoRoot's editor draft (opened from Edit on a row). */
@Serializable data object TransactionEditRoute
/** Read-only View Transaction page; Edit there opens [TransactionEditRoute] (or the transfer sheet). */
@Serializable data class TransactionViewRoute(val transactionId: Long)
/** Dashboard "top spending" tile: Transactions filtered to one category in one month ("yyyy-MM"). */
@Serializable data class CategoryTransactionsRoute(val categoryId: Long, val month: String)
/** The transaction composer: [start] is a [ComposerStart] name; [typeHint] a TransactionType name or empty. */
@Serializable data class ComposerRoute(val start: String = "TYPE", val walletId: Long = NO_WALLET, val typeHint: String = "")

const val NO_SCHEDULE = -1L
const val NO_WALLET = -1L

private data class Tab(val item: DockItem, val route: Any)

/** How tall the fade is where a tab's content meets the dock. */
private val DockFade = 20.dp

/**
 * The navigation dock: Home · Transactions · (+) · Budget · Wallets, with the transaction-entry button in the centre.
 * Budget leaves the dock when the user switches it off (its data is kept). Insights, goals, scheduled transactions
 * and debts open from Home's Quick links; categories from Budget.
 */
private fun tabs(features: FeatureSet): List<Tab> = listOfNotNull(
    Tab(DockItem("home", "Home", Icons.Outlined.Home, Icons.Filled.Home), HomeRoute),
    Tab(DockItem("transactions", "Transactions", Icons.AutoMirrored.Outlined.ReceiptLong, Icons.AutoMirrored.Filled.ReceiptLong), TransactionsRoute),
    if (features.isEnabled(Feature.BUDGETS)) Tab(DockItem("budget", "Budget", Icons.Outlined.PieChart, Icons.Filled.PieChart), BudgetRoute) else null,
    Tab(DockItem("wallets", "Wallets", Icons.Outlined.AccountBalanceWallet, Icons.Filled.AccountBalanceWallet), WalletsRoute),
)

private val PrimaryRoutes: List<Pair<String, KClass<out Any>>> = listOf(
    "home" to HomeRoute::class,
    "transactions" to TransactionsRoute::class,
    "budget" to BudgetRoute::class,
    "wallets" to WalletsRoute::class,
)

/** Workflows that take the whole screen (capture, category setup, settings): the dock steps aside. */
private val FocusedRoutes: List<KClass<out Any>> = listOf(
    GuideRoute::class,
    CategorySetupRoute::class,
    SettingsRoute::class,
    FeaturesRoute::class,
    SharedSpaceRoute::class,
    ProRoute::class,
    ComposerRoute::class,
    TransactionEditRoute::class,
    TransactionViewRoute::class,
    RecurringEditRoute::class,
    AskRoute::class,
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
        if (onboarded) {
            MainScaffold(preferences)
        } else {
            // Onboarding has no Scaffold above it: this Surface sets the themed content colour, so text without an
            // explicit colour follows the theme instead of falling back to black (invisible in dark mode).
            // Keyed per setup generation: these ViewModels live as long as the Activity, so without the key a second
            // onboarding (after a sign-out wipe) would reuse the finished run's state (F-7).
            androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.background) {
                // The same profile photo actions as the profile menu, so a Google sign-in during setup can offer its picture.
                val appViewModel: AppViewModel = viewModel(key = "app-${preferences.setupGeneration}", factory = AppViewModel.Factory)
                val photo by appViewModel.profilePhoto.collectAsStateWithLifecycle()
                val googlePhoto by appViewModel.googlePhotoUrl.collectAsStateWithLifecycle()
                val photoBusy by appViewModel.photoBusy.collectAsStateWithLifecycle()
                val photoMessage by appViewModel.photoMessage.collectAsStateWithLifecycle()
                OnboardingScreen(
                    viewModel(
                        key = "onboarding-${preferences.setupGeneration}",
                        factory = com.lucento.app.feature.onboarding.OnboardingViewModel.Factory,
                    ),
                    photo = ProfilePhotoState(
                        image = photo,
                        googleAvailable = googlePhoto != null,
                        busy = photoBusy,
                        message = photoMessage,
                        onPick = appViewModel::setPhotoFromDevice,
                        onUseGoogle = appViewModel::useGooglePhoto,
                        onRemove = appViewModel::removePhoto,
                    ),
                )
            }
        }
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
    val generation = preferences.setupGeneration
    val appViewModel: AppViewModel = viewModel(key = "app-$generation", factory = AppViewModel.Factory)

    // One transaction editor for the whole app (the + sheet, wallet screens, history, scan, speak and type).
    val editorViewModel: TransactionEditorViewModel =
        viewModel(key = "editor-$generation", factory = TransactionEditorViewModel.Factory)
    val editorState by editorViewModel.state.collectAsStateWithLifecycle()
    var editorDraft by remember { mutableStateOf<TransactionDraft?>(null) }
    var transferDraft by remember { mutableStateOf<TransferDraft?>(null) }
    var transferNote by remember { mutableStateOf<String?>(null) }
    // Set only from a save callback that reported success, i.e. after the database write committed.
    var confirmation by remember { mutableStateOf<Confirmation?>(null) }

    var showProfile by rememberSaveable { mutableStateOf(false) }
    // The tab a pushed screen belongs to, so the dock keeps it highlighted (e.g. Goals opened from Home).
    var selectedTab by rememberSaveable { mutableStateOf("home") }
    // Each tap on Home in the dock asks Home to show its top (owner round 10); Back from a sub-page keeps the place.
    var homeToTop by rememberSaveable { mutableIntStateOf(0) }

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

    // A card or loan saved with a payment reminder opens a prefilled recurring payment (when that module is on).
    val planDebtPayment: ((Long, java.time.LocalDate) -> Unit)? = if (features.isEnabled(Feature.RECURRING)) {
        { walletId, firstDue -> push(RecurringEditRoute(debtWalletId = walletId, firstDueEpochDay = firstDue.toEpochDay())) }
    } else {
        null
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
        if (key == "budget" && !features.isEnabled(Feature.BUDGETS)) {
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

    /** A card's payment reminder: the transfer that pays it, filled in from the statement, for the user to check and save. */
    fun openCardPayment(due: com.lucento.app.domain.CardDue) {
        openTransfer(due.payFromWalletId, due.wallet.id)
        if (transferDraft == null) return
        transferDraft = editorViewModel.cardPaymentDraft(due)
        transferNote = "Amount due by ${due.cycle.dueDate.format(java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.getDefault()))}. " +
            if (due.payFromWalletId == null) "Choose the account you’re paying from, then save." else "Change the amount for a partial payment, or the account it comes from."
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

    // A new user's guided setup opens over Home and resumes there until it is finished.
    val guidePending = preferences.guideStep != null
    // Part D: after setup, the dock's + is ringed until it is used (Home clears this once anything is recorded).
    val spotlightAdd = !preferences.addSpotlightSeen && !guidePending && preferences.onboardingComplete
    // Keyed on the destination too: when a step's work (the composer) ends on a tab, the guide comes back over it.
    LaunchedEffect(guidePending, backStackEntry?.destination?.route) {
        val onGuide = navController.currentDestination?.hasRoute(GuideRoute::class) == true
        val onTab = navController.currentDestination.primaryKey() != null
        if (guidePending && !onGuide && (onTab || navController.currentDestination == null)) push(GuideRoute)
        // Finished elsewhere (or by a restore): the guide steps aside.
        if (!guidePending && onGuide) navController.popBackStack(GuideRoute, inclusive = true)
    }
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
                        if (item.key == "home") homeToTop++
                        navigateTo(tab.route)
                    },
                    onAdd = {
                        haptics.tick()
                        if (spotlightAdd) appViewModel.markAddSpotlightSeen()
                        openComposer()
                    },
                    highlightAdd = spotlightAdd && selectedTab == "home",
                )
            }
        },
    ) { innerPadding ->
        val profilePhoto by appViewModel.profilePhoto.collectAsStateWithLifecycle()
        val sharedAccess by appViewModel.sharedAccess.collectAsStateWithLifecycle()
        val people by appViewModel.people.collectAsStateWithLifecycle()
        val fxDisplay by appViewModel.fxDisplay.collectAsStateWithLifecycle()
        // Lucento Shared: an invitation sent while the app was in the background shows on Home when it comes back.
        androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_START) { appViewModel.refreshSharedSpace() }
        // Lucento Pro: every lock note links to the Pro page through this, naming the feature that led there.
        val openPro = remember(navController) {
            { feature: com.lucento.app.domain.entitlement.ProFeature? ->
                navController.navigate(ProRoute(feature?.name.orEmpty())) { launchSingleTop = true }
            }
        }
        CompositionLocalProvider(
            com.lucento.app.feature.fx.LocalFxDisplay provides fxDisplay,
            com.lucento.app.feature.pro.LocalOpenPro provides openPro,
            LocalProfileChrome provides ProfileChrome(preferences.displayName, profilePhoto, sharedWith = sharedAccess.sharedWith) {
                showProfile = true
            },
            com.lucento.app.feature.sharedspace.LocalSharedAccess provides sharedAccess,
            com.lucento.app.feature.sharedspace.LocalPeople provides people,
        ) {
            // The padding is consumed as insets too, so a screen's imePadding() adds only the keyboard above the navigation
            // bar the Scaffold already cleared, not the bar a second time (a gap above the keyboard in the composer).
            Box(Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding)) {
                NavHost(
                    navController = navController,
                    startDestination = HomeRoute,
                    modifier = Modifier.fillMaxSize(),
                    // Tab switches fade through; pushed screens slide in from the trailing edge.
                    enterTransition = { if (reducedMotion) fadeIn(tween(0)) else fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.985f) },
                    exitTransition = { fadeOut(tween(if (reducedMotion) 0 else 120)) },
                ) {
                    composable<GuideRoute> {
                        GuideScreen(
                            onRecordTransactions = { openComposer() },
                            onFinished = {
                                navController.popBackStack(GuideRoute, inclusive = true)
                                navigateTo(HomeRoute)
                            },
                        )
                    }
                    composable<HomeRoute> {
                        val spacePrompt by appViewModel.spacePrompt.collectAsStateWithLifecycle()
                        HomeScreen(
                            scrollToTop = homeToTop,
                            actions = HomeActions(
                                spacePrompt = spacePrompt,
                                onOpenSpacePrompt = {
                                    val invite = (spacePrompt as? com.lucento.app.data.sharedspace.SpacePrompt.Invitation)?.invitation?.id
                                    push(SharedSpaceRoute(reviewInvite = invite.orEmpty()))
                                },
                                onDismissSpacePrompt = { spacePrompt?.let { appViewModel.dismissSpacePrompt(it.key) } },                                onOpenBudget = { navigateTo(BudgetRoute) },
                                onChooseCategories = { push(CategorySetupRoute) },
                                onOpenInsights = { push(InsightsRoute) },
                                onOpenGoals = { push(GoalsRoute) },
                                onOpenRecurring = { id -> push(RecurringRoute(id ?: NO_SCHEDULE)) },
                                onOpenWallets = { navigateTo(WalletsRoute) },
                                onOpenWallet = { push(WalletDetailRoute(it)) },
                                onPayCard = { if (features.isEnabled(Feature.MULTIPLE_WALLETS)) openCardPayment(it) else push(WalletDetailRoute(it.wallet.id)) },
                                onOpenDebts = { push(DebtsRoute) },
                                onOpenSettings = { push(SettingsRoute()) },
                                onOpenAccountSettings = { push(SettingsRoute(SettingsPage.ACCOUNT.key)) },
                                onOpenTransactions = { navigateTo(TransactionsRoute) },
                                onViewTransaction = { view(it) },
                                onAddTransaction = { type -> openComposer(typeHint = type) },
                                onTransfer = { openTransfer() },
                                onScanReceipt = { openComposer(ComposerStart.SCAN) },
                                onOpenCategory = { id, month -> push(CategoryTransactionsRoute(id, month.toString())) },
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
                            onAddWallet = { navigateTo(WalletsRoute) },
                            onScanReceipt = if (features.isEnabled(Feature.RECEIPT_SCANNING)) ({ openComposer(ComposerStart.SCAN) }) else null,
                            onSpeak = if (features.isEnabled(Feature.VOICE_ENTRY)) ({ openComposer(ComposerStart.SPEAK) }) else null,
                        )
                    }
                    composable<CategoryTransactionsRoute> { entry ->
                        val route = entry.toRoute<CategoryTransactionsRoute>()
                        TransactionsScreen(
                            onAddTransaction = { openComposer() },
                            onViewTransaction = { view(it) },
                            onEditTransaction = { edit(it) },
                            onDeleteTransaction = { t ->
                                editorViewModel.delete(t.id) { notify(if (t.isTransfer) "Transfer deleted" else "Transaction deleted") }
                            },
                            onAddWallet = { navigateTo(WalletsRoute) },
                            onBack = { navController.popBackStack() },
                            initialCategoryId = route.categoryId,
                            initialMonth = runCatching { java.time.YearMonth.parse(route.month) }.getOrNull(),
                        )
                    }
                    composable<WalletsRoute> {
                        WalletsScreen(
                            onBack = null,
                            onOpenWallet = { push(WalletDetailRoute(it)) },
                            allowAdding = features.isEnabled(Feature.MULTIPLE_WALLETS),
                            onPlanDebtPayment = planDebtPayment,
                            onOpenDebts = if (features.isEnabled(Feature.DEBTS_LOANS)) ({ push(DebtsRoute) }) else null,
                            onScanReceipt = if (features.isEnabled(Feature.RECEIPT_SCANNING)) ({ openComposer(ComposerStart.SCAN) }) else null,
                        )
                    }
                    composable<BudgetRoute> {
                        Gated(Feature.BUDGETS, features, enableFeature, onBack = { navigateTo(HomeRoute) }) {
                            BudgetScreen(
                                onBack = null,
                                onChooseCategories = { push(CategorySetupRoute) },
                                onManageCategories = { push(CategoriesRoute) },
                            )
                        }
                    }
                    pushed<InsightsRoute>(reducedMotion) {
                        Gated(Feature.INSIGHTS, features, enableFeature, back) { InsightsScreen(onBack = back, onAsk = { push(AskRoute) }) }
                    }
                    pushed<AskRoute>(reducedMotion) {
                        Gated(Feature.INSIGHTS, features, enableFeature, back) { com.lucento.app.feature.ask.AskLucentoScreen(onBack = back) }
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
                            onPlanDebtPayment = planDebtPayment,
                            onPayCard = { openCardPayment(it) },
                        )
                    }
                    pushed<GoalsRoute>(reducedMotion) {
                        Gated(Feature.SAVINGS_GOALS, features, enableFeature, back) { GoalsScreen(onBack = back) }
                    }
                    pushed<DebtsRoute>(reducedMotion) {
                        Gated(Feature.DEBTS_LOANS, features, enableFeature, back) {
                            DebtsScreen(onBack = back, onOpenWallet = { push(WalletDetailRoute(it)) }, onPlanDebtPayment = planDebtPayment)
                        }
                    }
                    pushed<RecurringRoute>(reducedMotion) { entry ->
                        val route = entry.toRoute<RecurringRoute>()
                        Gated(Feature.RECURRING, features, enableFeature, back) {
                            RecurringScreen(
                                onBack = back,
                                openScheduleId = route.scheduleId.takeIf { it != NO_SCHEDULE },
                                onAddSchedule = { push(RecurringEditRoute()) },
                                onEditSchedule = { id -> push(RecurringEditRoute(scheduleId = id)) },
                                onOpenWallet = { id -> push(WalletDetailRoute(id)) },
                                onPayCard = { if (features.isEnabled(Feature.MULTIPLE_WALLETS)) openCardPayment(it) else push(WalletDetailRoute(it.wallet.id)) },
                            )
                        }
                    }
                    pushed<RecurringEditRoute>(reducedMotion) { entry ->
                        val route = entry.toRoute<RecurringEditRoute>()
                        Gated(Feature.RECURRING, features, enableFeature, back) {
                          com.lucento.app.feature.sharedspace.OwnerOnly("Scheduled payment", "scheduled payments", back) {
                            RecurringEditorScreen(
                                onBack = back,
                                scheduleId = route.scheduleId.takeIf { it != NO_SCHEDULE },
                                prefillDebt = route.debtWalletId.takeIf { it != NO_SCHEDULE }
                                    ?.let { it to java.time.LocalDate.ofEpochDay(route.firstDueEpochDay) },
                                onSaved = {
                                    back()
                                    notify("Recurring payment saved")
                                },
                            )
                          }
                        }
                    }
                    pushed<CategoriesRoute>(reducedMotion) {
                        com.lucento.app.feature.sharedspace.OwnerOnly("Categories", "categories", back) {
                            ManageCategoriesScreen(onBack = back, onChooseFromPresets = { push(CategorySetupRoute) })
                        }
                    }
                    pushed<CategorySetupRoute>(reducedMotion) {
                        com.lucento.app.feature.sharedspace.OwnerOnly("Categories", "categories", back) { CategorySetupScreen(onDone = back) }
                    }
                    pushed<SettingsRoute>(reducedMotion) { entry ->
                        // A page opened from the hub uses the hub's ViewModel (one account check, one prompt).
                        // Pages open straight from the hub, so the hub is the entry underneath (public API only).
                        val hub = remember(entry) {
                            navController.previousBackStackEntry?.takeIf {
                                entry.toRoute<SettingsRoute>().page.isNotEmpty() &&
                                    it.destination.hasRoute<SettingsRoute>() && it.toRoute<SettingsRoute>().page.isEmpty()
                            }
                        }
                        SettingsScreen(
                            onBack = back,
                            viewModelOwner = hub,
                            page = SettingsPage.fromKey(entry.toRoute<SettingsRoute>().page),
                            // Test builds: the profile menu's Switch account opens Google's account picker at once.
                            startAccountSwitch = com.lucento.app.BuildConfig.DEBUG && entry.toRoute<SettingsRoute>().switchAccount,
                            onOpenPage = { page ->
                                when {
                                    page == SettingsPage.FEATURES -> push(FeaturesRoute)
                                    page == SettingsPage.SHARED -> push(SharedSpaceRoute())
                                    page == SettingsPage.PRO -> push(ProRoute())
                                    // Not push(): launchSingleTop would replace the hub (same route type), so Back would skip
                                    // it. A repeated tap on the same row is ignored instead.
                                    navController.currentBackStackEntry?.arguments?.getString("page") != page.key ->
                                        navController.navigate(SettingsRoute(page.key))
                                }
                            },
                        )
                    }
                    pushed<FeaturesRoute>(reducedMotion) { EnabledFeaturesScreen(onBack = back) }
                    pushed<SharedSpaceRoute>(reducedMotion) { entry ->
                        com.lucento.app.feature.sharedspace.SharedSpaceScreen(
                            onBack = back,
                            onOpenAccount = { navController.navigate(SettingsRoute(SettingsPage.ACCOUNT.key)) },
                            onJoined = {
                                notify("You’re in. This phone now shows the Shared Space.")
                                navigateTo(HomeRoute)
                            },
                            reviewInviteId = entry.toRoute<SharedSpaceRoute>().reviewInvite.ifBlank { null },
                        )
                    }
                    pushed<ProRoute>(reducedMotion) { entry ->
                        val from = entry.toRoute<ProRoute>().from
                        com.lucento.app.feature.pro.ProScreen(
                            onBack = back,
                            from = com.lucento.app.domain.entitlement.ProFeature.entries.firstOrNull { it.name == from },
                        )
                    }
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
                if (!focused) {
                    // Content dissolves into the background above the dock instead of being cut by a hard line at the
                    // top of its + button. Every tab's scrolling content ends with at least Spacing.xl of padding, so
                    // nothing readable is faded at rest.
                    val page = MaterialTheme.colorScheme.background
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(DockFade)
                            .background(Brush.verticalGradient(listOf(page.copy(alpha = 0f), page))),
                    )
                }
                ConfirmationHost(confirmation = confirmation, onDismissed = { confirmation = null })
            }
        }
    }

    if (showProfile) {
        val profileEmail by appViewModel.signedInEmail.collectAsStateWithLifecycle()
        val photo by appViewModel.profilePhoto.collectAsStateWithLifecycle()
        val googlePhoto by appViewModel.googlePhotoUrl.collectAsStateWithLifecycle()
        val photoBusy by appViewModel.photoBusy.collectAsStateWithLifecycle()
        val photoMessage by appViewModel.photoMessage.collectAsStateWithLifecycle()
        // The sheet sits outside the screens' providers; its Lucento Shared row needs to know who this ledger is shared with.
        val sheetAccess by appViewModel.sharedAccess.collectAsStateWithLifecycle()
        val lockedThemes by appViewModel.lockedThemes.collectAsStateWithLifecycle()
        CompositionLocalProvider(
            com.lucento.app.feature.sharedspace.LocalSharedAccess provides sheetAccess,
            // The theme picker's lock note: the sheet closes first, then the Pro page opens.
            com.lucento.app.feature.pro.LocalOpenPro provides { feature ->
                showProfile = false
                push(ProRoute(feature?.name.orEmpty()))
            },
        ) {
            ProfileSheet(
                preferences = preferences,
                signedInEmail = profileEmail,
                lockedThemes = lockedThemes,
                photo = ProfilePhotoState(
                    image = photo,
                    googleAvailable = googlePhoto != null,
                    busy = photoBusy,
                    message = photoMessage,
                    onPick = appViewModel::setPhotoFromDevice,
                    onUseGoogle = appViewModel::useGooglePhoto,
                    onRemove = appViewModel::removePhoto,
                ),
                onSaveName = appViewModel::setDisplayName,
                onTheme = appViewModel::setThemeMode,
                onThemeId = appViewModel::setThemeId,
                onOpen = { destination ->
                    when (destination) {
                        ProfileDestination.SETTINGS -> push(SettingsRoute())
                        ProfileDestination.FEATURES -> push(FeaturesRoute)
                        ProfileDestination.SHARED -> push(SharedSpaceRoute())
                        ProfileDestination.PRO -> push(ProRoute())
                        ProfileDestination.SWITCH_ACCOUNT -> push(SettingsRoute(SettingsPage.ACCOUNT.key, switchAccount = true))
                    }
                },
                onDismiss = { showProfile = false },
            )
        }
    }

    // Lucento Pro: once, after a plan ends — what changed, and that nothing was removed.
    val planNotice by appViewModel.planNotice.collectAsStateWithLifecycle()
    planNotice?.let { notice ->
        com.lucento.app.feature.pro.PlanNoticeSheet(
            notice = notice,
            onSeePro = {
                appViewModel.dismissPlanNotice()
                push(ProRoute())
            },
            onDismiss = appViewModel::dismissPlanNotice,
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
            illustration = { MascotScene(mood = MascotMood.Curious, mascotSize = 96.dp) },
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
