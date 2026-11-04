package com.lucento.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucento.app.core.applicationContainerFactory
import com.lucento.app.core.network.observeOnline
import com.lucento.app.data.preferences.UserPreferencesRepository
import com.lucento.app.data.repository.BudgetRepository
import com.lucento.app.data.repository.CategoryRepository
import com.lucento.app.data.repository.GoalRepository
import com.lucento.app.data.repository.RecurringRepository
import com.lucento.app.data.repository.TransactionRepository
import com.lucento.app.data.repository.WalletRepository
import com.lucento.app.data.sync.SupabaseModule
import com.lucento.app.data.sync.SyncStatus
import com.lucento.app.domain.Budget
import com.lucento.app.domain.Category
import com.lucento.app.domain.UsageCountsService
import com.lucento.app.domain.Goal
import com.lucento.app.domain.GoalContribution
import com.lucento.app.domain.RecurringSchedule
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.Wallet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

class HomeViewModel(
    private val transactions: TransactionRepository,
    wallets: WalletRepository,
    categories: CategoryRepository,
    budgets: BudgetRepository,
    recurring: RecurringRepository,
    goals: GoalRepository,
    private val preferences: UserPreferencesRepository,
    /** Inputs for the header's sync status; all local (preferences, Room, the system's network state). */
    syncStatus: Flow<SyncStatus>,
    signedInEmail: Flow<String?>,
    pendingChanges: Flow<Int>,
    online: Flow<Boolean>,
    private val syncConfigured: Boolean,
    private val usage: UsageCountsService? = null,
    /** P2-2: true while this process syncs. */
    running: Flow<Boolean> = flowOf(false),
    /** P2-2: starts a sync now (WorkManager); the sheet's Sync now / Try again. */
    private val requestSync: () -> Unit = {},
    /** P2-6: the user has read the "changed on another device" notice, which showed [Int] changes. */
    private val clearOverridden: suspend (Int) -> Unit = {},
) : ViewModel() {

    private data class Core(
        val transactions: List<Transaction>,
        val wallets: List<Wallet>,
        val categories: List<Category>,
        val budgets: List<Budget>,
        val schedules: List<RecurringSchedule>,
    )

    private data class Savings(val goals: List<Goal>, val archived: List<Goal>, val contributions: List<GoalContribution>)

    /** The month the budget card describes; null follows the current month (so it rolls over at midnight). */
    private val selectedMonth = MutableStateFlow<YearMonth?>(null)

    private val cloud: Flow<Pair<CloudStatus, SyncDetails>> =
        combine(syncStatus, signedInEmail, pendingChanges, online, running) { status, email, pending, isOnline, isRunning ->
            CloudStatus.of(
                configured = syncConfigured,
                signedIn = email != null,
                enabled = status.enabled,
                hasError = status.lastError != null,
                everSynced = status.lastSyncedAtMillis != null,
                pendingChanges = pending,
                online = isOnline,
                running = isRunning,
            ) to SyncDetails(
                email = email,
                lastSyncedAtMillis = status.lastSyncedAtMillis,
                pendingChanges = pending,
                lastError = status.lastError,
                overriddenChanges = status.overriddenChanges,
                configured = syncConfigured,
            )
        }

    val uiState: StateFlow<HomeUiState> = combine(
        combine(
            transactions.observeTransactions(),
            wallets.observeWallets(),
            categories.observeCategories(),
            budgets.observeBudgets(),
            recurring.observeSchedules(),
        ) { all, walletList, categoryList, budgetList, scheduleList ->
            Core(all, walletList, categoryList, budgetList, scheduleList)
        },
        combine(goals.observeGoals(), goals.observeArchivedGoals(), goals.observeContributions()) { g, a, c -> Savings(g, a, c) },
        preferences.preferences,
        selectedMonth,
        cloud,
    ) { core, savings, prefs, month, (cloudStatus, syncDetails) ->
        val today = LocalDate.now()
        HomeStateBuilder.build(
            all = core.transactions,
            wallets = core.wallets,
            categories = core.categories,
            primaryCurrency = prefs.primaryCurrency,
            today = today,
            budgets = core.budgets,
            schedules = core.schedules,
            features = prefs.features,
            goals = savings.goals,
            archivedGoals = savings.archived,
            contributions = savings.contributions,
            displayName = prefs.displayName,
            month = month ?: YearMonth.from(today),
            cloudStatus = cloudStatus,
            visitedInsights = prefs.insightsVisited,
        ).let { built ->
            built.copy(
                showLocalDataNotice = showLocalDataNotice(prefs.onboardingComplete, prefs.localDataNoticeSeen),
                syncDetails = syncDetails,
                moduleOffersHidden = prefs.moduleOffersHidden,
                milestoneToShow = com.lucento.app.domain.Achievements.toAcknowledge(built.earned, prefs.achievementsSeen),
                needsMilestoneBaseline = prefs.achievementsSeen == null,
                showAddSpotlight = showAddSpotlight(prefs.addSpotlightSeen, prefs.guideStep != null, built.recordedCount),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        // Anonymous usage counts (opt-in): Home reports how many real entries exist. No budget suggestion here.
        val log = usage
        if (log != null) {
            viewModelScope.launch {
                transactions.observeCashFlowCount().collect { realEntries ->
                    log.onHome(
                        realEntries = realEntries,
                        suggestionDays = null,
                        doneSteps = emptySet(),
                        budgetedCategories = 0,
                        acceptSuggestion = false,
                        markAdjust = false,
                    )
                }
            }
        }
    }

    /** P2-5: turns a module back on from Home, with any module it needs. Data is never touched. */
    fun turnOn(feature: com.lucento.app.domain.Feature) {
        viewModelScope.launch {
            val current = preferences.preferences.first().features
            current.toTurnOn(feature).forEach { preferences.setFeatureEnabled(it, true) }
        }
    }

    /** P2-5: hides Home's offers for modules that are off (Settings › Enabled features still lists them). */
    fun hideModuleOffers() {
        viewModelScope.launch { preferences.setModuleOffersHidden(true) }
    }

    /** P2-2: the details sheet's Sync now / Try again. */
    fun syncNow() = requestSync()

    /** P2-6: hides the "changed on another device" notice until another change of this phone's is replaced. */
    fun dismissOverridden() {
        // Only what was on screen: a change counted by a sync that finished meanwhile still shows (QA L3).
        val seen = uiState.value.syncDetails.overriddenChanges
        viewModelScope.launch { clearOverridden(seen) }
    }

    /** Part D: the milestone was acknowledged on screen; it is never shown again. */
    fun acknowledgeMilestone(achievement: com.lucento.app.domain.Achievement) {
        viewModelScope.launch { preferences.acknowledgeAchievements(setOf(achievement)) }
    }

    /** Part D: first run with milestones — what's already earned is recorded silently. */
    fun baselineMilestones(earned: Set<com.lucento.app.domain.Achievement>) {
        viewModelScope.launch { preferences.acknowledgeAchievements(earned) }
    }

    /** Part D: the "+" spotlight was dismissed, or + was used. */
    fun dismissAddSpotlight() {
        viewModelScope.launch { preferences.setAddSpotlightSeen() }
    }

    fun dismissLocalDataNotice() {
        viewModelScope.launch { preferences.setLocalDataNoticeSeen() }
    }

    /** Shows [month] on the budget card; the current month follows the calendar (null). */
    fun selectMonth(month: YearMonth) {
        selectedMonth.value = if (month >= YearMonth.from(LocalDate.now())) null else month
    }

    companion object {
        val Factory = applicationContainerFactory { application, c ->
            HomeViewModel(
                c.transactions, c.wallets, c.categories, c.budgets, c.recurring, c.goals, c.preferences,
                syncStatus = c.syncPreferences.status,
                signedInEmail = if (SupabaseModule.isConfigured) c.auth.observeSignedInEmail() else flowOf(null),
                pendingChanges = c.syncRepository.observePendingChanges(),
                online = application.observeOnline(),
                syncConfigured = SupabaseModule.isConfigured,
                usage = c.usageCounts,
                running = c.syncRepository.running,
                requestSync = {
                    if (!c.syncRepository.running.value) com.lucento.app.data.sync.SyncScheduler.retryNow(application)
                },
                clearOverridden = { seen -> c.syncPreferences.clearOverriddenChanges(seen) },
            )
        }
    }
}
