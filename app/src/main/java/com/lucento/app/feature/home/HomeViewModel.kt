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
import com.lucento.app.domain.BudgetSuggestions
import com.lucento.app.domain.Category
import com.lucento.app.domain.FirstWeek
import com.lucento.app.domain.Goal
import com.lucento.app.domain.GoalContribution
import com.lucento.app.domain.RecurringSchedule
import com.lucento.app.domain.SpentDay
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val transactions: TransactionRepository,
    wallets: WalletRepository,
    categories: CategoryRepository,
    private val budgets: BudgetRepository,
    recurring: RecurringRepository,
    goals: GoalRepository,
    private val preferences: UserPreferencesRepository,
    /** Inputs for the header's sync status; all local (preferences, Room, the system's network state). */
    syncStatus: Flow<SyncStatus>,
    signedInEmail: Flow<String?>,
    pendingChanges: Flow<Int>,
    online: Flow<Boolean>,
    private val syncConfigured: Boolean,
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

    /** Bumped by [retry] so a failed read starts again. */
    private val reload = MutableStateFlow(0)

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

    val uiState: StateFlow<HomeUiState> = reload.flatMapLatest {
        combine(
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
            val built = HomeStateBuilder.build(
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
            )
            val names = core.categories.associate { it.id to it.name }
            val spent = core.transactions.filter { it.type == TransactionType.EXPENSE }.map { tx ->
                SpentDay(tx.date, tx.categoryId, tx.categoryId?.let { names[it] }, tx.amount.amountMinor, tx.currencyCode)
            }
            val budgeted = core.budgets.filter { it.limit.amountMinor > 0L }.mapNotNull { it.categoryId }.toSet()
            built.copy(
                showLocalDataNotice = showLocalDataNotice(prefs.onboardingComplete, prefs.localDataNoticeSeen),
                dayOneHome = prefs.dayOneHome,
                firstWeekDismissed = prefs.firstWeekDismissed,
                celebrateFirstEntry = FirstWeek.celebrate(prefs.dayOneHome, prefs.firstEntryCelebrated, built.realEntryCount),
                insightsChecked = prefs.insightsChecked,
                budgetSuggestion = BudgetSuggestions.suggest(
                    expenses = spent,
                    today = today,
                    currencyCode = built.focusCurrency,
                    budgetedCategoryIds = budgeted,
                    snoozedUntil = prefs.budgetSuggestionSnoozedUntil,
                ),
            )
        }.catch {
            emit(HomeUiState(loading = false, loadError = true))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun dismissLocalDataNotice() {
        viewModelScope.launch { preferences.setLocalDataNoticeSeen() }
    }

    fun dismissFirstWeek() {
        viewModelScope.launch { preferences.setFirstWeekDismissed() }
    }

    fun markFirstEntryCelebrated() {
        viewModelScope.launch { preferences.setFirstEntryCelebrated() }
    }

    fun markInsightsChecked() {
        viewModelScope.launch { preferences.setInsightsChecked() }
    }

    fun snoozeBudgetSuggestion() {
        viewModelScope.launch { preferences.setBudgetSuggestionSnoozedUntil(BudgetSuggestions.snoozeUntil(LocalDate.now())) }
    }

    fun applyBudgetSuggestion() {
        val suggestion = uiState.value.budgetSuggestion ?: return
        val currency = uiState.value.focusCurrency
        viewModelScope.launch {
            suggestion.lines.forEach { line ->
                budgets.setAllocation(line.categoryId, currency, line.suggested)
            }
        }
    }

    fun retry() {
        reload.value += 1
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
            )
        }
    }
}
