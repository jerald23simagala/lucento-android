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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Home
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.rememberHaptics
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.data.preferences.UserPreferences
import com.lucento.app.domain.TransactionType
import com.lucento.app.feature.home.HomeScreen
import com.lucento.app.feature.onboarding.OnboardingScreen
import com.lucento.app.feature.settings.SettingsScreen
import com.lucento.app.feature.transactions.TransactionDraft
import com.lucento.app.feature.transactions.TransactionEditorSheet
import com.lucento.app.feature.transactions.TransactionEditorViewModel
import com.lucento.app.feature.transactions.TransactionsScreen
import com.lucento.app.feature.transactions.toDraft
import com.lucento.app.feature.wallets.WalletsScreen
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object TransactionsRoute
@Serializable data object WalletsRoute
@Serializable data object SettingsRoute

private data class TopLevelDestination(
    val route: Any,
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
)

/*
 * Primary navigation holds only what exists today: Home, Transactions and Wallets. Budgets and Goals
 * will join as real destinations when they ship (bottom navigation comfortably fits five). Settings is
 * a secondary, pushed screen reached from Home's top bar.
 */
private val topLevelDestinations = listOf(
    TopLevelDestination(HomeRoute, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    TopLevelDestination(TransactionsRoute, "Transactions", Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong),
    TopLevelDestination(WalletsRoute, "Wallets", Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
)

private fun NavDestination?.isTopLevel(): Boolean =
    this?.hierarchy?.any { d -> topLevelDestinations.any { d.hasRoute(it.route::class) } } == true

/** Switches between first-run onboarding and the main app once preferences are known. */
@Composable
fun LucentoRoot(preferences: UserPreferences) {
    AnimatedContent(
        targetState = preferences.onboardingComplete,
        transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(200)) },
        label = "root",
    ) { onboarded ->
        if (onboarded) MainScaffold() else OnboardingScreen()
    }
}

@Composable
private fun MainScaffold(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val haptics = rememberHaptics()
    val reducedMotion = LucentoTheme.reducedMotion
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // One transaction editor for the whole app (Home quick actions, recent rows, Transactions list).
    val editorViewModel: TransactionEditorViewModel = viewModel(factory = TransactionEditorViewModel.Factory)
    val editorState by editorViewModel.state.collectAsStateWithLifecycle()
    var editorDraft by remember { mutableStateOf<TransactionDraft?>(null) }

    fun navigateTo(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun notify(message: String) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    val openNew: (TransactionType) -> Unit = { type ->
        if (editorState.loaded && editorState.wallets.isEmpty()) navigateTo(WalletsRoute) else editorDraft = editorViewModel.newDraft(type)
    }

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
                visible = currentDestination == null || currentDestination.isTopLevel(),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                Column {
                    Hairline()
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        tonalElevation = 0.dp,
                    ) {
                        topLevelDestinations.forEach { destination ->
                            val selected = currentDestination?.hierarchy?.any { it.hasRoute(destination.route::class) } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (!selected) haptics.tick()
                                    navigateTo(destination.route)
                                },
                                icon = {
                                    Icon(
                                        if (selected) destination.selectedIcon else destination.icon,
                                        contentDescription = null,
                                    )
                                },
                                label = { Text(destination.label, style = MaterialTheme.typography.labelMedium) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
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
            // Top-level switches fade through; nothing slides sideways between peers.
            enterTransition = { if (reducedMotion) fadeIn(tween(0)) else fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.985f) },
            exitTransition = { fadeOut(tween(if (reducedMotion) 0 else 120)) },
        ) {
            composable<HomeRoute> {
                HomeScreen(
                    onOpenSettings = { navController.navigate(SettingsRoute) { launchSingleTop = true } },
                    onOpenWallets = { navigateTo(WalletsRoute) },
                    onOpenTransactions = { navigateTo(TransactionsRoute) },
                    onAddTransaction = openNew,
                    onEditTransaction = { editorDraft = it.toDraft() },
                )
            }
            composable<TransactionsRoute> {
                TransactionsScreen(
                    onAddTransaction = { openNew(TransactionType.EXPENSE) },
                    onEditTransaction = { editorDraft = it.toDraft() },
                    onAddWallet = { navigateTo(WalletsRoute) },
                )
            }
            composable<WalletsRoute> { WalletsScreen() }
            // Settings is pushed on top of Home and slides in from the trailing edge.
            composable<SettingsRoute>(
                enterTransition = { slideInHorizontally(tween(if (reducedMotion) 0 else 260)) { it / 4 } + fadeIn(tween(200)) },
                exitTransition = { fadeOut(tween(120)) },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = { slideOutHorizontally(tween(if (reducedMotion) 0 else 220)) { it / 4 } + fadeOut(tween(180)) },
            ) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
        }
    }

    editorDraft?.let { draft ->
        TransactionEditorSheet(
            initial = draft,
            wallets = editorState.wallets,
            categories = editorState.categories,
            onSave = { toSave, separator, onResult ->
                editorViewModel.save(toSave, separator) { error ->
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
            onDismiss = { editorDraft = null },
        )
    }
}
