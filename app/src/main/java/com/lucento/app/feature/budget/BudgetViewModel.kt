package com.lucento.app.feature.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucento.app.core.containerFactory
import com.lucento.app.core.money.Money
import com.lucento.app.data.preferences.UserPreferencesRepository
import com.lucento.app.data.repository.BudgetRepository
import com.lucento.app.data.repository.CategoryRepository
import com.lucento.app.data.repository.TransactionRepository
import com.lucento.app.domain.Budget
import com.lucento.app.domain.Category
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.CategorySelection
import com.lucento.app.domain.FeatureSet
import com.lucento.app.domain.Transaction
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** Raw data for the Budget tab and its category screens. Calculations happen in [com.lucento.app.domain.BudgetPlanner]. */
data class BudgetUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val primaryCurrency: String = "USD",
    val features: FeatureSet = FeatureSet.ALL,
    val categories: List<Category> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val categorySetupDone: Boolean = true,
    val acknowledgedBudgetMonth: YearMonth? = null,
) {
    val expenseCategories: List<Category> get() = categories.filter { it.kind == CategoryKind.EXPENSE }
    val activeExpense: List<Category> get() = expenseCategories.filterNot { it.archived }
    val archivedExpense: List<Category> get() = expenseCategories.filter { it.archived }
    val categoriesById: Map<Long, Category> get() = categories.associateBy { it.id }
}

class BudgetViewModel(
    transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val budgets: BudgetRepository,
    private val preferences: UserPreferencesRepository,
) : ViewModel() {

    val uiState: StateFlow<BudgetUiState> = combine(
        transactions.observeTransactions(),
        categories.observeCategories(),
        budgets.observeBudgets(),
        preferences.preferences,
    ) { t, c, b, p ->
        BudgetUiState(
            loading = false,
            today = LocalDate.now(),
            primaryCurrency = p.primaryCurrency,
            features = p.features,
            categories = c,
            budgets = b,
            transactions = t,
            categorySetupDone = p.categorySetupDone,
            acknowledgedBudgetMonth = p.acknowledgedBudgetMonth,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetUiState())

    private var busy = false

    /**
     * One write at a time (double taps cannot duplicate). If the database refuses the write it is rolled back and
     * [onResult] is not called, so the form stays open and nothing is reported as saved.
     */
    private fun <T> write(block: suspend () -> T, onResult: (T) -> Unit = {}) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            val result = try {
                block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("Lucento", "Budget write failed and was rolled back", e)
                null
            } finally {
                busy = false
            }
            if (result != null) onResult(result)
        }
    }

    /** Sets (or with null, removes) a category's monthly amount; the overall budget follows automatically. */
    fun setAllocation(categoryId: Long, currencyCode: String, limit: Money?, onResult: (BudgetRepository.SaveResult) -> Unit) =
        write({ budgets.setAllocation(categoryId, currencyCode, limit) }, onResult)

    fun removeLegacyOverall() = write({ budgets.removeLegacyOverall() })

    /** Existing users who keep their categories as they are: the setup prompt is not shown again. */
    fun keepCurrentCategories() {
        viewModelScope.launch { runCatching { preferences.setCategorySetupDone(true) } }
    }

    fun applySelection(choices: List<CategorySelection.Choice>, onResult: (CategoryRepository.SelectionResult) -> Unit) =
        write({
            val result = categories.applySelection(choices)
            preferences.setCategorySetupDone(true)
            result
        }, onResult)

    fun createCategory(name: String, onResult: (CategoryRepository.SaveResult) -> Unit) =
        write({ categories.create(name, CategoryKind.EXPENSE) }, onResult)

    fun rename(id: Long, name: String, onResult: (CategoryRepository.SaveResult) -> Unit) =
        write({ categories.rename(id, name) }, onResult)

    fun move(id: Long, delta: Int) = write({ categories.move(id, delta) })

    /** Deactivates a category: archived when anything uses it (history kept), deleted only when nothing does. */
    fun deactivate(id: Long, onResult: (CategoryRepository.DeleteResult) -> Unit) = write({ categories.remove(id) }, onResult)

    fun restore(id: Long, onResult: (CategoryRepository.SaveResult) -> Unit) = write({ categories.restore(id) }, onResult)

    fun acknowledgeBudgetMonth(month: YearMonth) {
        viewModelScope.launch { runCatching { preferences.setAcknowledgedBudgetMonth(month) } }
    }

    companion object {
        val Factory = containerFactory { BudgetViewModel(it.transactions, it.categories, it.budgets, it.preferences) }
    }
}
