package com.lucento.app.feature.reports

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucento.app.core.containerFactory
import com.lucento.app.data.preferences.UserPreferencesRepository
import com.lucento.app.data.reports.ReportPdfWriter
import com.lucento.app.data.repository.BudgetRepository
import com.lucento.app.data.repository.CategoryRepository
import com.lucento.app.data.repository.RecurringRepository
import com.lucento.app.data.repository.TransactionRepository
import com.lucento.app.data.repository.WalletRepository
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.ask.Report
import com.lucento.app.domain.ask.ReportBuilder
import com.lucento.app.domain.ask.ReportInputs
import com.lucento.app.domain.ask.ReportKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

data class ReportsUiState(
    val loading: Boolean = true,
    /** The main currency: the whole-picture reports are in it. */
    val currency: String = "USD",
    /** Lucento Pro: reports stay on screen; Save as PDF and Share wait for a plan that includes them. */
    val pdfLocked: Boolean = false,
    /** What a wallet statement can be made for: every wallet money moves through. */
    val statementWallets: List<Wallet> = emptyList(),
)

/**
 * The built-in reports (tester round 3: on Insights, below Ask Lucento, and a wallet's statement from its own page).
 * The whole-picture reports read every figure in the main currency, FX results included, as Ask Lucento's always did;
 * a wallet statement stays in the wallet's own currency, like a bank statement. All of it on the phone.
 */
class ReportsViewModel(
    transactions: TransactionRepository,
    wallets: WalletRepository,
    categories: CategoryRepository,
    budgets: BudgetRepository,
    recurring: RecurringRepository,
    private val preferences: UserPreferencesRepository,
    private val pdf: ReportPdfWriter,
    /** Lucento Pro: the plan this phone holds; null in tests (everything allowed). */
    private val entitlements: StateFlow<com.lucento.app.domain.entitlement.EntitlementState>? = null,
) : ViewModel() {

    /** Each wallet in its own currency: what a statement is made from. */
    private val raw: StateFlow<ReportInputs?> = combine(
        transactions.observeTransactions(),
        wallets.observeWallets(),
        categories.observeCategories(),
        budgets.observeBudgets(),
        recurring.observeSchedules(),
    ) { t, w, c, b, s -> ReportInputs(w, t, c, b, s, LocalDate.now()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Multicurrency: the whole picture in the main currency (docs/CALCULATIONS.md "Exchange rates"). */
    private val translated: StateFlow<ReportInputs?> = combine(
        raw,
        preferences.preferences.map { it.primaryCurrency }.distinctUntilChanged(),
    ) { base, main ->
        base?.let {
            val ledger = com.lucento.app.domain.fx.FxTranslation.translate(main, it.wallets, it.transactions)
            it.copy(wallets = ledger.wallets, transactions = ledger.withFxResults, categories = ledger.withFxCategories(it.categories))
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val uiState: StateFlow<ReportsUiState> = combine(raw, preferences.preferences, entitlements ?: flowOf(null)) { data, prefs, plan ->
        ReportsUiState(
            loading = data == null,
            currency = prefs.primaryCurrency,
            pdfLocked = plan != null && !com.lucento.app.domain.entitlement.ProGates.pdfAllowed(plan, System.currentTimeMillis()),
            statementWallets = data?.wallets.orEmpty().filter { it.type.acceptsTransactions },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsUiState())

    /**
     * [kind] for this month. A wallet statement is for [walletId], or else the default wallet, or else the first wallet
     * money moves through; null when there is none (or nothing has loaded yet).
     */
    fun open(kind: ReportKind, walletId: Long? = null): Report? {
        if (kind.needsWallet) {
            val data = raw.value ?: return null
            val choices = data.wallets.filter { it.type.acceptsTransactions }
            val id = walletId?.takeIf { w -> choices.any { it.id == w } }
                ?: preferredWallet(choices)
                ?: return null
            return ReportBuilder.walletStatement(data, id, YearMonth.from(data.today))
        }
        val data = translated.value ?: return null
        return ReportBuilder.build(kind, data, uiState.value.currency, YearMonth.from(data.today))
    }

    private var defaultWalletId: Long? = null

    init {
        viewModelScope.launch { preferences.preferences.collect { defaultWalletId = it.defaultWalletId } }
    }

    private fun preferredWallet(choices: List<Wallet>): Long? =
        defaultWalletId?.takeIf { id -> choices.any { it.id == id } } ?: choices.firstOrNull()?.id

    /** The same report for another month (the sheet's month stepper). */
    fun rebuild(report: Report, month: YearMonth): Report? {
        if (report.kind.needsWallet) {
            val data = raw.value ?: return null
            val id = report.walletId?.takeIf { w -> data.wallets.any { it.id == w } } ?: return null
            return ReportBuilder.walletStatement(data, id, month)
        }
        val data = translated.value ?: return null
        return ReportBuilder.build(report.kind, data, report.currencyCode, month)
    }

    /** A wallet statement for another wallet, same month. */
    fun switchWallet(report: Report, walletId: Long): Report? {
        val data = raw.value ?: return null
        if (data.wallets.none { it.id == walletId && it.type.acceptsTransactions }) return null
        return ReportBuilder.walletStatement(data, walletId, report.month)
    }

    private fun pdfAllowedNow(): Boolean =
        entitlements?.value?.let { com.lucento.app.domain.entitlement.ProGates.pdfAllowed(it, System.currentTimeMillis()) } ?: true

    /** Writes [report] as a PDF to the document the user picked. */
    fun savePdf(report: Report, uri: Uri, contentResolver: android.content.ContentResolver, done: (Boolean) -> Unit) {
        if (!pdfAllowedNow()) return done(false)
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { contentResolver.openOutputStream(uri)?.use { pdf.write(report, it); true } ?: false }.getOrDefault(false)
            }
            done(ok)
        }
    }

    /** Writes [report] to private cache for the share sheet. */
    fun pdfForSharing(report: Report, done: (File?) -> Unit) {
        if (!pdfAllowedNow()) return done(null)
        viewModelScope.launch {
            done(withContext(Dispatchers.IO) { runCatching { pdf.writeToCache(report) }.getOrNull() })
        }
    }

    companion object {
        val Factory = containerFactory {
            ReportsViewModel(
                it.transactions, it.wallets, it.categories, it.budgets, it.recurring, it.preferences,
                pdf = it.reportPdf,
                entitlements = it.entitlements.state,
            )
        }
    }
}
