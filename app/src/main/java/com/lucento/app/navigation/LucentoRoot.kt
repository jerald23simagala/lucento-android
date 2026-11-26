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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavBackStackEntry
import com.lucento.app.feature.transactions.TransactionsViewModel
import com.lucento.app.feature.wallets.WalletsViewModel

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
/**
 * Scheduled: [scheduleId] opens one schedule’s review sheet; [selectWalletId] opens All schedules in Select mode with the
 * plans paying that card or loan selected (the editor’s “Adjust existing”).
 */
@Serializable data class RecurringRoute(val scheduleId: Long = NO_SCHEDULE, val selectWalletId: Long = NO_SCHEDULE)

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
    /** On the Account page, open the sign-out confirmation straight away (profile menu › Sign out). */
    val signOut: Boolean = false,
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
/** Milestones ("Progress" until 2026-11-23): the Awards card, then the budget year (owner, 2026-11-23: the year on top). */
@Serializable data object ProgressRoute
/** A month's recap ("yyyy-MM"; empty: the page's default month), from its square on Milestones or Home's check-in card. */
@Serializable data class MonthRecapRoute(val month: String = "")
/** The awards on their own page (owner, 2026-11-23): from Milestones' Awards button and a ceremony's "See all awards". */
@Serializable data object AwardsRoute
/** The monthly check-in's plan page for [month] ("yyyy-MM"). */
@Serializable data class MonthPlanRoute(val month: String)
/** The transaction composer: [start] is a [ComposerStart] name; [typeHint] a TransactionType name or empty. */
@Serializable data class ComposerRoute(val start: String = "TYPE", val walletId: Long = NO_WALLET, val typeHint: String = "")

const val NO_SCHEDULE = -1L
const val NO_WALLET = -1L

/**
 * True while the app lock or the launch logo covers the app (MainActivity). Overlays that would draw beneath or above
 * them (Lucento Pro's welcome, whose dialogs are windows of their own) wait until it is false.
 */
val LocalAppObscured = compositionLocalOf { false }

private data class Tab(val item: DockItem, val route: Any)

/** How tall the fade is where a tab's content meets the dock. */
private val DockFade = 20.dp

/** The pause between the "saved" confirmation leaving and Google Play's rating card being asked for, when it's due. */
private const val RATING_CARD_PAUSE_MILLIS = 600L

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

/**
 * On wide windows (rail and two panes, owner 2026-11-26) the focused workflows above open in the right-hand pane beside
 * the list, except these, which still take the whole window: setup, settings and the Lucento Plan page.
 */
private val FullScreenRoutes: List<KClass<out Any>> = listOf(
    GuideRoute::class,
    CategorySetupRoute::class,
    SettingsRoute::class,
    FeaturesRoute::class,
    SharedSpaceRoute::class,
    ProRoute::class,
)

private fun NavDestination?.primaryKey(): String? =
    this?.hierarchy?.firstNotNullOfOrNull { d -> PrimaryRoutes.firstOrNull { d.hasRoute(it.second) }?.first }

private fun NavDestination?.isFocused(): Boolean =
    this?.hierarchy?.any { d -> FocusedRoutes.any { d.hasRoute(it) } } == true

private fun NavDestination?.isFullScreen(): Boolean =
    this?.hierarchy?.any { d -> FullScreenRoutes.any { d.hasRoute(it) } } == true

/** Where something is being written that a stray tap beside it must not throw away (see HeldWhileEditing). */
private val EditorRoutes: List<KClass<out Any>> = listOf(
    ComposerRoute::class,
    TransactionEditRoute::class,
    RecurringEditRoute::class,
    MonthPlanRoute::class,
)

private fun NavDestination?.isEditor(): Boolean =
    this?.hierarchy?.any { d -> EditorRoutes.any { d.hasRoute(it) } } == true

/** A tab's root route, for the list pane and for clearing the detail pane back to it. */
private fun tabRoot(key: String): Any = when (key) {
    "transactions" -> TransactionsRoute
    "budget" -> BudgetRoute
    "wallets" -> WalletsRoute
    else -> HomeRoute
}

/** The back stack entry of a tab's root, whose ViewModels the list pane shares; null when it isn't on the stack. */
private fun NavHostController.tabRootEntry(key: String): NavBackStackEntry? = runCatching {
    when (key) {
        "transactions" -> getBackStackEntry<TransactionsRoute>()
        "budget" -> getBackStackEntry<BudgetRoute>()
        "wallets" -> getBackStackEntry<WalletsRoute>()
        else -> getBackStackEntry<HomeRoute>()
    }
}.getOrNull()

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
    // Wide windows get the side rail, and two panes when there's room (owner, 2026-11-26); phones keep the dock.
    val windowConfiguration = LocalConfiguration.current
    val navLayout = navLayoutFor(windowConfiguration.screenWidthDp, windowConfiguration.screenHeightDp)
    val layoutState = rememberUpdatedState(navLayout)
    // Short messages (a Pro wall met again, owner 2026-11-19) show in the app's snackbar unless a sheet is open.
    val messageHosts = com.lucento.app.core.designsystem.component.LocalMessageHosts.current
    androidx.compose.runtime.DisposableEffect(messageHosts, snackbarHostState, scope) {
        val appHost = com.lucento.app.core.designsystem.component.MessageHost(snackbarHostState, scope)
        messageHosts?.app = appHost
        onDispose { if (messageHosts?.app === appHost) messageHosts.app = null }
    }
    val generation = preferences.setupGeneration
    val appViewModel: AppViewModel = viewModel(key = "app-$generation", factory = AppViewModel.Factory)

    // Lucento Pro's welcome (owner, 2026-11-12): a deck of cards for what Pro opened, the collection, the medal and the
    // badge's flight to the profile picture. The registry lets the flight find the profile picture.
    val spotlight = remember { com.lucento.app.core.designsystem.component.SpotlightRegistry() }
    val welcomeRun by appViewModel.proWelcome.collectAsStateWithLifecycle()
    val plan by appViewModel.plan.collectAsStateWithLifecycle()
    val welcome = com.lucento.app.feature.pro.welcome.rememberProWelcomeState(welcomeRun)
    var proRingLanded by rememberSaveable { mutableIntStateOf(0) }

    // One transaction editor for the whole app (the + sheet, wallet screens, history, scan, speak and type).
    val editorViewModel: TransactionEditorViewModel =
        viewModel(key = "editor-$generation", factory = TransactionEditorViewModel.Factory)
    val editorState by editorViewModel.state.collectAsStateWithLifecycle()
    var editorDraft by remember { mutableStateOf<TransactionDraft?>(null) }
    var transferDraft by remember { mutableStateOf<TransferDraft?>(null) }
    var transferNote by remember { mutableStateOf<String?>(null) }
    // Set only from a save callback that reported success, i.e. after the database write committed.
    var confirmation by remember { mutableStateOf<Confirmation?>(null) }
    // Counts the composer's committed saves in this session; each one checks whether Google Play's rating card is due
    // (the effect beside the plan notice below).
    var ratingCheck by remember { mutableIntStateOf(0) }

    var showProfile by rememberSaveable { mutableStateOf(false) }
    // Follow Lucento, opened from the profile menu once it has closed.
    var showFollow by rememberSaveable { mutableStateOf(false) }
    // A Pro theme tried on lasts while the profile sheet is open (owner, 2026-11-19).
    val themeTrial = com.lucento.app.feature.profile.LocalThemeTrial.current
    LaunchedEffect(showProfile) { if (!showProfile) themeTrial.end() }
    // A theme tried on and taken to the plan page becomes the theme once Pro arrives (owner, 2026-11-19). The real plan
    // decides (never the view model's first, empty list), and it is let go only once the saved theme is that one.
    val themeAfterPurchase by themeTrial.afterPurchase.collectAsStateWithLifecycle()
    val heldPlan = com.lucento.app.feature.pro.LocalEntitlements.current
    val lockedForSave by appViewModel.lockedThemes.collectAsStateWithLifecycle()
    LaunchedEffect(themeAfterPurchase, heldPlan, lockedForSave, preferences.themeId) {
        val theme = themeAfterPurchase ?: return@LaunchedEffect
        val unlocked = theme.name !in com.lucento.app.domain.entitlement.ProGates.lockedThemeIds(heldPlan, System.currentTimeMillis())
        when {
            preferences.themeId == theme -> themeTrial.clearAfterPurchase()
            unlocked -> appViewModel.setThemeId(theme)
        }
    }
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

    // Something picked in a tab's own screen. With two panes it replaces what the right-hand pane shows (Back then
    // returns to the tab's default there) instead of stacking up; elsewhere it's an ordinary push.
    fun open(route: Any) {
        if (layoutState.value == NavLayout.SPLIT) {
            // The tab the list pane shows: the root on top, else the tab the top screen was opened from.
            val tab = navController.currentDestination.primaryKey() ?: selectedTab
            navController.navigate(route) {
                popUpTo(tabRoot(tab))
                launchSingleTop = true
            }
        } else {
            push(route)
        }
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

    /** [fromList]: from the history's own rows, which on wide windows open beside the list (see [open]). */
    fun edit(transaction: Transaction, fromList: Boolean = false) {
        val group = transaction.transferGroup
        if (group != null) {
            val transfer = editorState.transfers[group] ?: return
            transferNote = null
            transferDraft = transfer.toDraft()
        } else {
            editorDraft = transaction.toDraft()
            if (fromList) open(TransactionEditRoute) else push(TransactionEditRoute)
        }
    }

    fun view(transaction: Transaction, fromList: Boolean = false) =
        if (fromList) open(TransactionViewRoute(transaction.id)) else push(TransactionViewRoute(transaction.id))

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

    // A home-screen widget tap (feature/widget, docs/WIDGETS.md): the composer, the budget, categories or Home — taken
    // once, and never over the guided setup. Under App lock it lands behind the lock screen, as the widget says.
    val widgetLaunch by com.lucento.app.feature.widget.WidgetLaunches.pending.collectAsStateWithLifecycle()
    // Speak or Scan asked of a composer that is already open: push() would only bring it back as it was.
    var composerRestart by remember { mutableStateOf<ComposerStart?>(null) }
    LaunchedEffect(widgetLaunch, guidePending) {
        val launch = widgetLaunch ?: return@LaunchedEffect
        if (!com.lucento.app.feature.widget.WidgetLaunches.consume(launch) || guidePending) return@LaunchedEffect
        when (launch) {
            is com.lucento.app.feature.widget.WidgetLaunch.Composer -> {
                // A widget drawn before Speak or Scan was switched off still offers it; the composer types instead.
                val start = when {
                    launch.start == ComposerStart.SPEAK && !features.isEnabled(Feature.VOICE_ENTRY) -> ComposerStart.TYPE
                    launch.start == ComposerStart.SCAN && !features.isEnabled(Feature.RECEIPT_SCANNING) -> ComposerStart.TYPE
                    else -> launch.start
                }
                // Already writing: what's there stays, and Speak or Scan starts in that composer.
                if (navController.currentDestination?.hasRoute(ComposerRoute::class) == true) composerRestart = start else openComposer(start)
            }
            com.lucento.app.feature.widget.WidgetLaunch.Budget ->
                navigateTo(if (features.isEnabled(Feature.BUDGETS)) BudgetRoute else HomeRoute)
            com.lucento.app.feature.widget.WidgetLaunch.Categories -> push(CategorySetupRoute)
            com.lucento.app.feature.widget.WidgetLaunch.Home -> navigateTo(HomeRoute)
        }
    }
    val back: () -> Unit = { navController.popBackStack() }
    val focused = currentDestination.isFocused()
    // A screen’s own bottom bar can stand in for the dock (Scheduled’s Select mode, LocalHideDock).
    var dockHidden by remember { mutableStateOf(false) }
    val hideDock: (Boolean) -> Unit = remember { { hidden -> dockHidden = hidden } }

    // The four tabs' own screens. Phones show them through the NavHost; with two panes they stay in the left-hand pane
    // and what they open shows on the right (owner, 2026-11-26), so their detail links go through open().
    val homeTab: @Composable () -> Unit = {
        val spacePrompt by appViewModel.spacePrompt.collectAsStateWithLifecycle()
        HomeScreen(
            scrollToTop = homeToTop,
            actions = HomeActions(
                spacePrompt = spacePrompt,
                onOpenSpacePrompt = {
                    val invite = (spacePrompt as? com.lucento.app.data.sharedspace.SpacePrompt.Invitation)?.invitation?.id
                    push(SharedSpaceRoute(reviewInvite = invite.orEmpty()))
                },
                onDismissSpacePrompt = { spacePrompt?.let { appViewModel.dismissSpacePrompt(it.key) } },
                onOpenBudget = { navigateTo(BudgetRoute) },
                onChooseCategories = { push(CategorySetupRoute) },
                onOpenInsights = { open(InsightsRoute) },
                onOpenGoals = { open(GoalsRoute) },
                onOpenRecurring = { id -> open(RecurringRoute(id ?: NO_SCHEDULE)) },
                onOpenWallets = { navigateTo(WalletsRoute) },
                onOpenWallet = { open(WalletDetailRoute(it)) },
                onPayCard = { if (features.isEnabled(Feature.MULTIPLE_WALLETS)) openCardPayment(it) else open(WalletDetailRoute(it.wallet.id)) },
                onOpenDebts = { open(DebtsRoute) },
                onOpenSettings = { push(SettingsRoute()) },
                onOpenAccountSettings = { push(SettingsRoute(SettingsPage.ACCOUNT.key)) },
                onOpenTransactions = { navigateTo(TransactionsRoute) },
                onViewTransaction = { view(it, fromList = true) },
                onAddTransaction = { type -> openComposer(typeHint = type) },
                onTransfer = { openTransfer() },
                onScanReceipt = { openComposer(ComposerStart.SCAN) },
                onOpenCategory = { id, month -> open(CategoryTransactionsRoute(id, month.toString())) },
                onOpenMilestones = { open(ProgressRoute) },
                onOpenMonthRecap = { month -> open(MonthRecapRoute(month.toString())) },
                onOpenAwards = { open(AwardsRoute) },
                onPlanMonth = { month -> open(MonthPlanRoute(month.toString())) },
            ),
        )
    }
    val transactionsTab: @Composable () -> Unit = {
        TransactionsScreen(
            onAddTransaction = { openComposer() },
            onViewTransaction = { view(it, fromList = true) },
            onEditTransaction = { edit(it, fromList = true) },
            onDeleteTransaction = { t ->
                editorViewModel.delete(t.id) { notify(if (t.isTransfer) "Transfer deleted" else "Transaction deleted") }
            },
            onAddWallet = { navigateTo(WalletsRoute) },
            onScanReceipt = if (features.isEnabled(Feature.RECEIPT_SCANNING)) ({ openComposer(ComposerStart.SCAN) }) else null,
            onSpeak = if (features.isEnabled(Feature.VOICE_ENTRY)) ({ openComposer(ComposerStart.SPEAK) }) else null,
        )
    }
    val walletsTab: @Composable () -> Unit = {
        WalletsScreen(
            onBack = null,
            onOpenWallet = { open(WalletDetailRoute(it)) },
            allowAdding = features.isEnabled(Feature.MULTIPLE_WALLETS),
            onPlanDebtPayment = planDebtPayment,
            onOpenDebts = if (features.isEnabled(Feature.DEBTS_LOANS)) ({ open(DebtsRoute) }) else null,
            onScanReceipt = if (features.isEnabled(Feature.RECEIPT_SCANNING)) ({ openComposer(ComposerStart.SCAN) }) else null,
        )
    }
    val budgetTab: @Composable () -> Unit = {
        Gated(Feature.BUDGETS, features, enableFeature, onBack = { navigateTo(HomeRoute) }) {
            BudgetScreen(
                onBack = null,
                onChooseCategories = { push(CategorySetupRoute) },
                onManageCategories = { open(CategoriesRoute) },
                onOpenScheduled = { open(RecurringRoute()) },
                onPlanMonth = { month -> open(MonthPlanRoute(month.toString())) },
            )
        }
    }
    val walletPane: @Composable (Long, (() -> Unit)?) -> Unit = { walletId, onBack ->
        WalletDetailScreen(
            walletId = walletId,
            onBack = onBack,
            onAddTransaction = { type, id -> openComposer(walletId = id, typeHint = type) },
            onTransfer = { from, to -> openTransfer(from, to) },
            onEditTransaction = { view(it) },
            transfersEnabled = features.isEnabled(Feature.MULTIPLE_WALLETS),
            onAdjusted = { notify("Balance adjusted") },
            onOutsideRecorded = { notify("${it.label} recorded") },
            onPlanDebtPayment = planDebtPayment,
            onPayCard = { openCardPayment(it) },
        )
    }
    val scheduledPane: @Composable () -> Unit = {
        RecurringScreen(
            onBack = null,
            onAddSchedule = { push(RecurringEditRoute()) },
            onEditSchedule = { id -> push(RecurringEditRoute(scheduleId = id)) },
            onOpenWallet = { id -> push(WalletDetailRoute(id)) },
            onPayCard = { if (features.isEnabled(Feature.MULTIPLE_WALLETS)) openCardPayment(it) else push(WalletDetailRoute(it.wallet.id)) },
        )
    }
    // What a tab's default pane is showing, so the list beside it can mark that row (set by the pane itself).
    var paneDefault by remember { mutableStateOf(com.lucento.app.core.designsystem.component.PaneSelection()) }
    // With two panes, what the right-hand one shows while nothing has been opened from the tab, so it is never empty
    // (owner, 2026-11-26: Insights beside Home; defaultPaneFor). It runs in the tab root's entry, so it reads the list's
    // ViewModels; the page it shows gets ViewModels of its own that end when it leaves the screen (PaneViewModels).
    val tabDefaultPane: @Composable (String) -> Unit = { tabKey ->
        val insightsPane: @Composable () -> Unit = {
            PaneViewModels {
                // Seeing Insights beside Home is not opening it: no Financial Explorer milestone for that.
                InsightsScreen(onBack = null, onAsk = { push(AskRoute) }, viewModel = viewModel(factory = com.lucento.app.feature.insights.InsightsViewModel.PaneFactory))
            }
        }
        when (defaultPaneFor(tabKey, insightsOn = features.isEnabled(Feature.INSIGHTS), scheduledOn = features.isEnabled(Feature.RECURRING))) {
            DefaultPane.INSIGHTS -> insightsPane()
            DefaultPane.SCHEDULED -> PaneViewModels { scheduledPane() }
            DefaultPane.NEWEST_TRANSACTION -> {
                val list: TransactionsViewModel = viewModel(factory = TransactionsViewModel.Factory)
                val listState by list.uiState.collectAsStateWithLifecycle()
                // The newest of what the list shows (its filters applied).
                val newest = listState.sections.firstOrNull()?.group?.items?.firstOrNull()
                androidx.compose.runtime.SideEffect {
                    paneDefault = com.lucento.app.core.designsystem.component.PaneSelection(transactionId = newest?.id)
                }
                when {
                    newest != null -> key(newest.id) {
                        PaneViewModels { TransactionDetailScreen(transactionId = newest.id, onBack = null, onEdit = { edit(it) }) }
                    }
                    listState.loading -> Unit
                    listState.totalCount > 0 -> PanePlaceholder(
                        title = "Nothing matches",
                        body = "No transactions fit the filters on the left. Change them and the newest one opens here.",
                    )
                    else -> PanePlaceholder(
                        title = "No transactions yet",
                        body = "Add one with the + button. Whatever you pick from the list opens here.",
                    )
                }
            }
            DefaultPane.DEFAULT_WALLET -> {
                val list: WalletsViewModel = viewModel(factory = WalletsViewModel.Factory)
                val listState by list.uiState.collectAsStateWithLifecycle()
                // The wallet new transactions go into, else the first one listed.
                val walletId = listState.defaultWalletId?.takeIf { id -> listState.wallets.any { it.id == id } }
                    ?: listState.shown.firstOrNull()?.id
                    ?: listState.wallets.firstOrNull()?.id
                androidx.compose.runtime.SideEffect {
                    paneDefault = com.lucento.app.core.designsystem.component.PaneSelection(walletId = walletId)
                }
                when {
                    walletId != null -> key(walletId) { PaneViewModels { walletPane(walletId, null) } }
                    !listState.loading -> PanePlaceholder(
                        title = "No wallets yet",
                        body = "Add your cash, bank or card on the left and it opens here.",
                    )
                }
            }
            DefaultPane.NOTHING_OPEN -> PanePlaceholder(title = "Nothing open", body = "Pick something on the left and it opens here.")
        }
    }
    // The list pane's remembered state (scroll position, search) per tab-root entry: a tab reopened after a switch starts
    // afresh, as on a phone, and its ViewModels with it (the entries it was kept for are let go below).
    val listPaneStates = rememberSaveableStateHolder()
    val listPaneEntries = remember { mutableMapOf<String, NavBackStackEntry>() }
    LaunchedEffect(backStackEntry) {
        val gone = listPaneEntries.filterValues { it.lifecycle.currentState == androidx.lifecycle.Lifecycle.State.DESTROYED }.keys.toList()
        gone.forEach { stateKey ->
            listPaneStates.removeState(stateKey)
            listPaneEntries.remove(stateKey)
        }
    }

    CompositionLocalProvider(
        com.lucento.app.core.designsystem.component.LocalSpotlight provides spotlight,
        com.lucento.app.core.designsystem.component.LocalHideDock provides hideDock,
    ) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                // While the welcome covers the app, only it is there for TalkBack and focus (like the lock screen):
                // nothing beneath it can be reached or activated.
                modifier = if (welcome.coversScreen) {
                    Modifier
                        .clearAndSetSemantics {}
                        .focusProperties { canFocus = false }
                } else {
                    Modifier
                },
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = {
                    SnackbarHost(snackbarHostState) { data -> com.lucento.app.core.designsystem.component.LucentoSnackbar(data) }
                },
                bottomBar = {
                    AnimatedVisibility(
                        // Wide windows use the side rail instead.
                        visible = navLayout == NavLayout.DOCK && !focused && !dockHidden,
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
                    LocalProfileChrome provides ProfileChrome(
                        preferences.displayName,
                        profilePhoto,
                        sharedWith = sharedAccess.sharedWith,
                        proRing = plan.has(com.lucento.app.domain.entitlement.Capability.LOCAL_PRO, System.currentTimeMillis()) && !welcome.ringHeld,
                        proRingLanded = proRingLanded,
                    ) {
                        showProfile = true
                    },
                    com.lucento.app.feature.sharedspace.LocalSharedAccess provides sharedAccess,
                    com.lucento.app.feature.sharedspace.LocalPeople provides people,
                ) {
                    // The padding is consumed as insets too, so a screen's imePadding() adds only the keyboard above the navigation
                    // bar the Scaffold already cleared, not the bar a second time (a gap above the keyboard in the composer).
                    Box(Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding)) {
                      BoxWithConstraints(Modifier.fillMaxSize()) {
                        // Wide windows (owner, 2026-11-26): the rail down the leading edge and, with two panes, the
                        // current tab's own screen on the left and what was opened from it on the right. Setup,
                        // settings and the Lucento Plan page still take the whole window. The NavHost keeps one place
                        // in the tree whatever shows beside it, so nothing in it restarts when the panes change.
                        val fullScreen = currentDestination.isFullScreen()
                        val showRail = navLayout != NavLayout.DOCK && !fullScreen
                        val showList = navLayout == NavLayout.SPLIT && !fullScreen
                        // While something is being written on the right (the composer, an editor), the rail and the
                        // list wait behind a veil, as the dock steps aside on a phone: a tap there is Back, so unsaved
                        // work goes through the editor's own "discard?" instead of vanishing.
                        val editing = currentDestination.isEditor()
                        val backDispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
                        val leaveEditor: () -> Unit = { backDispatcher?.onBackPressed() }
                        val besideRail = maxWidth - (if (showRail) RailWidth else 0.dp)
                        val listWidth = if (showList) listPaneWidthDp(besideRail.value.toInt()).dp else 0.dp
                        val detailWidth = besideRail - listWidth - (if (showList) PaneDividerWidth else 0.dp)
                        Row(Modifier.fillMaxSize()) {
                            if (showRail) {
                              HeldWhileEditing(held = editing, onTapWhileHeld = leaveEditor, modifier = Modifier.fillMaxHeight()) {
                                NavigationRailDock(
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
                                    modifier = Modifier.fillMaxHeight(),
                                )
                              }
                            }
                            if (showList) {
                                // On a tab's root, that tab at once (selectedTab follows a frame later); above it, the
                                // tab it was opened from.
                                val tabKey = currentDestination.primaryKey() ?: selectedTab
                                // The tab root's entry, so the list shares the ViewModels it has on a phone (and the
                                // default pane beside it). Its lifecycle stays the screen's: the list keeps updating
                                // while something else is open on the right.
                                val rootEntry = remember(backStackEntry, tabKey) { navController.tabRootEntry(tabKey) }
                                // The row open on the right (a transaction or a wallet), marked in the list.
                                val top = backStackEntry
                                val paneSelection = when {
                                    top == null -> null
                                    top.destination.hasRoute<TransactionViewRoute>() ->
                                        com.lucento.app.core.designsystem.component.PaneSelection(transactionId = top.toRoute<TransactionViewRoute>().transactionId)
                                    top.destination.hasRoute<TransactionEditRoute>() ->
                                        com.lucento.app.core.designsystem.component.PaneSelection(transactionId = editorDraft?.id)
                                    top.destination.hasRoute<WalletDetailRoute>() ->
                                        com.lucento.app.core.designsystem.component.PaneSelection(walletId = top.toRoute<WalletDetailRoute>().walletId)
                                    top.destination.primaryKey() != null -> paneDefault
                                    else -> null
                                }
                                PaneConfiguration(listWidth) {
                                    HeldWhileEditing(held = editing, onTapWhileHeld = leaveEditor, modifier = Modifier.width(listWidth).fillMaxHeight()) {
                                        // Only once the tab's entry is on the stack (not on the very first frame), and
                                        // afresh for each entry: a tab reopened after a switch starts over, as on a phone.
                                        if (rootEntry != null) {
                                            val stateKey = "list-${rootEntry.id}"
                                            androidx.compose.runtime.SideEffect { listPaneEntries[stateKey] = rootEntry }
                                            key(tabKey, rootEntry.id) {
                                                ListPaneOwners(rootEntry, stateKey, listPaneStates, paneSelection) {
                                                    PaneFadeIn(reducedMotion) {
                                                        when (tabKey) {
                                                            "transactions" -> transactionsTab()
                                                            "budget" -> budgetTab()
                                                            "wallets" -> walletsTab()
                                                            else -> homeTab()
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                VerticalDivider(thickness = PaneDividerWidth, color = LucentoTheme.extendedColors.borderDefault)
                            }
                            PaneConfiguration(if (navLayout == NavLayout.DOCK) null else detailWidth) {
                              // Beside the list pane, whose top bar already has the profile button, one is enough.
                              CompositionLocalProvider(LocalProfileChrome provides LocalProfileChrome.current.takeUnless { showList }) {
                              Box(Modifier.weight(1f).fillMaxHeight()) {
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
                            // A tab's root: its own screen, or with two panes (that screen sits on the left) the tab's
                            // default right-hand pane.
                            composable<HomeRoute> { if (layoutState.value == NavLayout.SPLIT) tabDefaultPane("home") else homeTab() }
                            composable<TransactionsRoute> {
                                if (layoutState.value == NavLayout.SPLIT) tabDefaultPane("transactions") else transactionsTab()
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
                            composable<WalletsRoute> { if (layoutState.value == NavLayout.SPLIT) tabDefaultPane("wallets") else walletsTab() }
                            composable<BudgetRoute> { if (layoutState.value == NavLayout.SPLIT) tabDefaultPane("budget") else budgetTab() }
                            pushed<ProgressRoute>(reducedMotion) {
                                com.lucento.app.feature.progress.ProgressScreen(
                                    onBack = back,
                                    onOpenMonth = { month -> push(MonthRecapRoute(month.toString())) },
                                    onOpenAwards = { push(AwardsRoute) },
                                )
                            }
                            pushed<MonthRecapRoute>(reducedMotion) { entry ->
                                val route = entry.toRoute<MonthRecapRoute>()
                                com.lucento.app.feature.progress.MonthRecapScreen(
                                    onBack = back,
                                    initialMonth = route.month.takeIf { it.isNotEmpty() }?.let { runCatching { java.time.YearMonth.parse(it) }.getOrNull() },
                                    onOpenInsights = if (features.isEnabled(Feature.INSIGHTS)) ({ push(InsightsRoute) }) else null,
                                )
                            }
                            pushed<AwardsRoute>(reducedMotion) {
                                com.lucento.app.feature.awards.AwardsScreen(onBack = back)
                            }
                            pushed<MonthPlanRoute>(reducedMotion) { entry ->
                                val route = entry.toRoute<MonthPlanRoute>()
                                val month = runCatching { java.time.YearMonth.parse(route.month) }.getOrNull()
                                Gated(Feature.BUDGETS, features, enableFeature, back) {
                                    if (month == null) {
                                        LaunchedEffect(Unit) { back() }
                                    } else {
                                        val fmt = com.lucento.app.core.designsystem.component.amountFormat()
                                        com.lucento.app.feature.budget.MonthPlanScreen(
                                            month = month,
                                            onBack = back,
                                            onDone = { total ->
                                                back()
                                                // With the total it now adds up to, unless amounts are hidden (critic r2, P3-8).
                                                val name = com.lucento.app.feature.home.monthLabel(month)
                                                notify(if (total == null || fmt.hidden) "$name is planned" else "$name is planned: ${fmt.format(total)}")
                                            },
                                        )
                                    }
                                }
                            }
                            pushed<InsightsRoute>(reducedMotion) {
                                Gated(Feature.INSIGHTS, features, enableFeature, back) { InsightsScreen(onBack = back, onAsk = { push(AskRoute) }) }
                            }
                            pushed<AskRoute>(reducedMotion) {
                                Gated(Feature.INSIGHTS, features, enableFeature, back) {
                                    // Voice entry turns the microphone on or off everywhere: here as in the composer.
                                    com.lucento.app.feature.ask.AskLucentoScreen(onBack = back, voiceEnabled = features.isEnabled(Feature.VOICE_ENTRY))
                                }
                            }

                            pushed<WalletDetailRoute>(reducedMotion) { entry ->
                                walletPane(entry.toRoute<WalletDetailRoute>().walletId, back)
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
                                        selectWalletId = route.selectWalletId.takeIf { it != NO_SCHEDULE },
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
                                        // The duplicate warning’s “Adjust existing”: leave the editor (and a Scheduled page under it, so
                                        // Back doesn’t land on a second one) for Scheduled with that debt’s plans selected.
                                        onAdjustExisting = { debtId ->
                                            // Only from the editor on top: a second tap during the exit does nothing.
                                            if (navController.currentBackStackEntry?.id == entry.id) {
                                                navController.popBackStack()
                                                if (navController.currentDestination?.hasRoute(RecurringRoute::class) == true) navController.popBackStack()
                                                push(RecurringRoute(selectWalletId = debtId))
                                            }
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
                                    startSignOut = entry.toRoute<SettingsRoute>().signOut,
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
                                    onReplayTour = appViewModel::replayProTour,
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
                                        if (layoutState.value == NavLayout.SPLIT) {
                                            // Two panes: the composer closes and the list beside it (the Dashboard, or the
                                            // history) shows the batch at once (owner, 2026-11-26).
                                            if (navController.currentBackStackEntry?.id == entry.id) navController.popBackStack()
                                        } else {
                                            // Land on the history, where the batch that was just saved is now listed.
                                            selectedTab = "transactions"
                                            navigateTo(TransactionsRoute)
                                        }
                                        ratingCheck++
                                    },
                                    onAddWallet = { navigateTo(WalletsRoute) },
                                    voiceEnabled = features.isEnabled(Feature.VOICE_ENTRY),
                                    scanEnabled = features.isEnabled(Feature.RECEIPT_SCANNING),
                                    transfersEnabled = features.isEnabled(Feature.MULTIPLE_WALLETS),
                                    start = runCatching { ComposerStart.valueOf(route.start) }.getOrDefault(ComposerStart.TYPE),
                                    walletId = route.walletId.takeIf { it != NO_WALLET },
                                    typeHint = route.typeHint.takeIf { it.isNotEmpty() }?.let { name -> runCatching { TransactionType.valueOf(name) }.getOrNull() },
                                    restart = composerRestart,
                                    onRestartHandled = { composerRestart = null },
                                    obscured = LocalAppObscured.current,
                                )
                            }
                        }
                              }
                              }
                            }
                        }
                      }
                        if (navLayout == NavLayout.DOCK && !focused) {
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

            val signedInEmail by appViewModel.signedInEmail.collectAsStateWithLifecycle()
            val hostAccess by appViewModel.sharedAccess.collectAsStateWithLifecycle()
            val hasWallets by appViewModel.hasWallets.collectAsStateWithLifecycle()
            val hasGoals by appViewModel.hasGoals.collectAsStateWithLifecycle()
            val welcomeCards = remember(plan, features, hostAccess, hasWallets, hasGoals) {
                val now = System.currentTimeMillis()
                com.lucento.app.domain.entitlement.ProWelcome.stops(
                    com.lucento.app.domain.entitlement.ProTourContext(
                        capabilities = plan.capabilities(now),
                        walletsEnabled = features.isEnabled(Feature.MULTIPLE_WALLETS),
                        goalsEnabled = features.isEnabled(Feature.SAVINGS_GOALS),
                        insightsEnabled = features.isEnabled(Feature.INSIGHTS),
                        managesStructure = hostAccess.canManageStructure,
                        syncAvailable = com.lucento.app.data.sync.SupabaseModule.isConfigured,
                        hasWallets = hasWallets,
                        hasGoals = hasGoals,
                    ),
                )
            }
            com.lucento.app.feature.pro.welcome.ProWelcomeHost(
                state = welcome,
                stops = welcomeCards,
                entitlements = plan,
                signedIn = signedInEmail != null,
                aiAnswersOn = preferences.askLucentoAi,
                syncAvailable = com.lucento.app.data.sync.SupabaseModule.isConfigured,
                hasWallets = hasWallets,
                hasGoals = hasGoals,
                smartReadingOn = preferences.smartReading,
                paused = LocalAppObscured.current,
                registry = spotlight,
                onGoHome = { navigateTo(HomeRoute) },
                onStarted = appViewModel::proWelcomeStarted,
                onMedalPresented = appViewModel::proMedalPresented,
                onLanded = { proRingLanded++ },
                onFinished = appViewModel::finishProWelcome,
            )
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
                        ProfileDestination.SIGN_OUT -> push(SettingsRoute(SettingsPage.ACCOUNT.key, signOut = true))
                        ProfileDestination.FOLLOW -> showFollow = true
                    }
                },
                onDismiss = { showProfile = false },
            )
        }
    }

    if (showFollow) {
        com.lucento.app.feature.profile.FollowSheet(onDismiss = { showFollow = false })
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

    // Tester report 2026-11-25 (docs/HELP_AND_FEEDBACK.md): a moment after the composer saved, Google Play's rating card
    // when ReviewPromptPolicy says it's due, never before the listing is public. It waits for the "saved" confirmation to
    // go, and doesn't come over the Pro welcome, a plan notice or the app lock; a later save checks again.
    val obscuredNow by rememberUpdatedState(LocalAppObscured.current)
    val ratingContext = LocalContext.current
    LaunchedEffect(ratingCheck) {
        if (ratingCheck == 0) return@LaunchedEffect
        snapshotFlow { confirmation }.first { it == null }
        delay(RATING_CARD_PAUSE_MILLIS)
        if (welcomeRun != null || planNotice != null || obscuredNow) return@LaunchedEffect
        if (appViewModel.claimRatingCard()) com.lucento.app.feature.help.showPlayRatingCard(ratingContext)
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
            recentTransfers = editorState.transfers.values,
        )
    }
}

/** The hairline between the list pane and the detail pane. */
private val PaneDividerWidth = 1.dp

/**
 * Tells the screens inside a pane how wide the pane is: they size themselves from the window's width
 * (`screenGutter()`, the compact layouts under 400dp), which beside the rail or another pane is not theirs. Null keeps
 * the window's own (phones: unchanged).
 */
@Composable
private fun PaneConfiguration(width: Dp?, content: @Composable () -> Unit) {
    val window = LocalConfiguration.current
    val pane = remember(window, width) {
        width?.let { android.content.res.Configuration(window).apply { screenWidthDp = it.value.toInt().coerceAtLeast(0) } }
    }
    // Phones: nothing is provided (the same call either way, so what's inside keeps its place in the tree).
    CompositionLocalProvider(values = if (pane == null) emptyArray() else arrayOf(LocalConfiguration provides pane), content = content)
}

/**
 * ViewModels for a page shown as a tab's default pane, that end when it leaves the screen. Without this they would live
 * in the tab root's entry for the whole session (Home's is never popped), still working after the window folds to one
 * pane. The application rides along so containerFactory can reach the app's container.
 */
@Composable
private fun PaneViewModels(content: @Composable () -> Unit) {
    val application = LocalContext.current.applicationContext as android.app.Application
    val owner = remember(application) { PaneViewModelOwner(application) }
    androidx.compose.runtime.DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}

private class PaneViewModelOwner(application: android.app.Application) :
    androidx.lifecycle.ViewModelStoreOwner,
    androidx.lifecycle.HasDefaultViewModelProviderFactory {
    override val viewModelStore = androidx.lifecycle.ViewModelStore()
    override val defaultViewModelProviderFactory: androidx.lifecycle.ViewModelProvider.Factory =
        androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(application)
    override val defaultViewModelCreationExtras: androidx.lifecycle.viewmodel.CreationExtras =
        androidx.lifecycle.viewmodel.MutableCreationExtras().apply {
            set(androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY, application)
        }
}

/**
 * The rail or the list pane while something is being written on the right: still visible (the Dashboard keeps
 * updating) but veiled and out of reach, TalkBack included; a tap on the veil is Back ([onTapWhileHeld]), so the editor
 * can ask before anything unsaved is lost.
 */
@Composable
private fun HeldWhileEditing(held: Boolean, onTapWhileHeld: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier) {
        Box(if (held) Modifier.clearAndSetSemantics {}.focusProperties { canFocus = false } else Modifier) { content() }
        if (held) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = onTapWhileHeld,
                    )
                    .semantics {
                        contentDescription = "Leave the editor"
                        role = androidx.compose.ui.semantics.Role.Button
                    },
            )
        }
    }
}

/**
 * The list pane's screen runs with its tab root's ViewModels ([entry], so it is the same screen as on a phone) and its
 * own remembered state ([stateKey], one per entry). Its lifecycle stays the screen's, not the entry's (which is only
 * CREATED under a detail): the list keeps updating while something is open beside it.
 */
@Composable
private fun ListPaneOwners(
    entry: NavBackStackEntry,
    stateKey: String,
    states: SaveableStateHolder,
    selection: com.lucento.app.core.designsystem.component.PaneSelection?,
    content: @Composable () -> Unit,
) {
    states.SaveableStateProvider(stateKey) {
        CompositionLocalProvider(
            LocalViewModelStoreOwner provides entry,
            com.lucento.app.core.designsystem.component.LocalPaneSelection provides selection,
            content = content,
        )
    }
}

/** A tab's screen arriving in the list pane fades in, as tabs do on a phone (at once when animations are off). */
@Composable
private fun PaneFadeIn(reducedMotion: Boolean, content: @Composable () -> Unit) {
    val alpha = remember { androidx.compose.animation.core.Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(Unit) { alpha.animateTo(1f, tween(220)) }
    Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha.value }) { content() }
}

/** The detail pane when there is nothing to show yet (no transactions, no wallets, those modules off). */
@Composable
private fun PanePlaceholder(title: String, body: String) {
    Box(Modifier.fillMaxSize().padding(horizontal = Spacing.gutter), contentAlignment = Alignment.Center) {
        EmptyState(
            title = title,
            body = body,
            illustration = { MascotScene(mood = MascotMood.Curious, mascotSize = 96.dp) },
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
