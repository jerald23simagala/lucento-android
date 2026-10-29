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
) : ViewModel() {

    private data class Core(
        val transactions: List<Transaction>,
        val wallets: List<Wallet>,
        val categories: List<Category>,
        val budgets: List<Budget>,
        val schedules: List<RecurringSchedule>,
    )

    private data class Savings(val goals: List<Goal>, val contributions: List<GoalContribution>)

    /** The month the budget card describes; null follows the current month (so it rolls over at midnight). */
    private val selectedMonth = MutableStateFlow<YearMonth?>(null)

    private val cloud: Flow<CloudStatus> = combine(syncStatus, signedInEmail, pendingChanges, online) { status, email, pending, isOnline ->
        CloudStatus.of(
            configured = syncConfigured,
            signedIn = email != null,
            enabled = status.enabled,
            hasError = status.lastError != null,
            everSynced = status.lastSyncedAtMillis != null,
            pendingChanges = pending,
            online = isOnline,
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
        combine(goals.observeGoals(), goals.observeContributions()) { g, c -> Savings(g, c) },
        preferences.preferences,
        selectedMonth,
        cloud,
    ) { core, savings, prefs, month, cloudStatus ->
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
            contributions = savings.contributions,
            displayName = prefs.displayName,
            month = month ?: YearMonth.from(today),
            cloudStatus = cloudStatus,
        ).copy(showLocalDataNotice = showLocalDataNotice(prefs.onboardingComplete, prefs.localDataNoticeSeen))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        // Anonymous usage counts (opt-in): Home reports how many real entries exist. No budget suggestion here.
        val log = usage
        if (log != null) {
            viewModelScope.launch {
                transactions.observeTransactions().collect { all ->
                    log.onHome(
                        realEntries = all.count { it.type.isCashFlow },
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
            )
        }
    }
}
