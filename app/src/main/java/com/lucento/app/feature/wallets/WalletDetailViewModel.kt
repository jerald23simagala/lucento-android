package com.lucento.app.feature.wallets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucento.app.core.containerFactory
import com.lucento.app.core.money.MoneyParser
import com.lucento.app.data.repository.CategoryRepository
import com.lucento.app.data.repository.TransactionRepository
import com.lucento.app.data.repository.WalletRepository
import com.lucento.app.data.repository.adjustBalanceOrNull
import com.lucento.app.domain.CardCycle
import com.lucento.app.domain.Category
import com.lucento.app.domain.CreditCardMath
import com.lucento.app.domain.LoanMath
import com.lucento.app.domain.LoanProgress
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.Transfer
import com.lucento.app.domain.Wallet
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class WalletDetailUiState(
    val loading: Boolean = true,
    /** Null once loaded means the wallet no longer exists (deleted or archived). */
    val wallet: Wallet? = null,
    val wallets: List<Wallet> = emptyList(),
    /** This wallet's own movements, newest first, including its side of each transfer. */
    val history: List<Transaction> = emptyList(),
    val transfers: Map<String, Transfer> = emptyMap(),
    val categoriesById: Map<Long, Category> = emptyMap(),
    /** A credit card's current statement and amount due; null without billing days (CreditCardMath.cycle). */
    val cardCycle: CardCycle? = null,
    /** A card with an amount due: what its Payment button fills in (the same transfer as Home's reminder). */
    val cardDue: com.lucento.app.domain.CardDue? = null,
    /** For a card or loan: the account its payments usually come from ([CreditCardMath.payingWallet]). */
    val payFromWalletId: Long? = null,
    /** A receivable's "Record payment": the account the last instalment went into, or the likeliest one (round 10). */
    val receiveIntoWalletId: Long? = null,
    /** An instalment loan's principal, repaid and remaining, and instalments paid (LoanMath.progress). */
    val loanProgress: LoanProgress? = null,
    val today: LocalDate = LocalDate.now(),
) {
    val walletNames: Map<Long, String> = wallets.associate { it.id to it.name }
}

/** Why a balance override was refused; nothing is written when one is. */
sealed interface AdjustError {
    data object AmountInvalid : AdjustError
    data class AmountTooPrecise(val digits: Int) : AdjustError
    data object AmountTooLarge : AdjustError
    /** Ambiguous, or another currency's symbol: [message] says what to type (MoneyParser.clarification). */
    data class AmountUnclear(val message: String) : AdjustError
    data object FutureDate : AdjustError
    /** The actual balance already equals what Lucento tracked on that date. */
    data object Unchanged : AdjustError
    /** The wallet is gone, or the database refused the write. */
    data object Failed : AdjustError
}

class WalletDetailViewModel(
    private val walletId: Long,
    wallets: WalletRepository,
    private val transactions: TransactionRepository,
    categories: CategoryRepository,
) : ViewModel() {

    val uiState: StateFlow<WalletDetailUiState> = combine(
        wallets.observeWallets(),
        transactions.observeTransactions(),
        categories.observeCategories(),
    ) { walletList, all, categoryList ->
        val wallet = walletList.firstOrNull { it.id == walletId }
        val history = all.filter { it.walletId == walletId }
        val today = LocalDate.now()
        val cycle = wallet?.let { CreditCardMath.cycle(it, history, today) }
        val payFrom = wallet?.takeIf { it.type.isLiability }?.let { CreditCardMath.payingWallet(it, walletList, all)?.id }
        WalletDetailUiState(
            loading = false,
            wallet = wallet,
            wallets = walletList,
            history = history,
            transfers = Transfer.pair(all),
            categoriesById = categoryList.associateBy { it.id },
            cardCycle = cycle,
            cardDue = cycle?.takeIf { it.amountDue.amountMinor > 0L }?.let { com.lucento.app.domain.CardDue(wallet, it, it.isOverdue(today), payFrom) },
            payFromWalletId = payFrom,
            receiveIntoWalletId = wallet?.takeIf { it.type == com.lucento.app.domain.WalletType.RECEIVABLE }
                ?.let { CreditCardMath.receivingWallet(it, walletList, all)?.id },
            loanProgress = wallet?.let { LoanMath.progress(it, history) },
            today = today,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WalletDetailUiState())

    private var adjusting = false

    /**
     * Sets the wallet's balance at the end of [date] to [actualText] (the amount owed for a card or loan); the gap is
     * recorded as a balance adjustment. [clientUuid] is fixed per sheet, so a double tap can't record it twice.
     * [onResult] receives null on success.
     */
    fun adjust(actualText: String, decimalSeparator: Char, date: LocalDate, clientUuid: String, onResult: (AdjustError?) -> Unit) {
        if (adjusting) return
        val wallet = uiState.value.wallet ?: return onResult(AdjustError.Failed)
        val actual = when (val parsed = MoneyParser.parse(actualText, wallet.currencyCode, decimalSeparator)) {
            is MoneyParser.Result.Success -> parsed.money
            MoneyParser.Result.Empty -> com.lucento.app.core.money.Money.zero(wallet.currencyCode)
            MoneyParser.Result.Invalid -> return onResult(AdjustError.AmountInvalid)
            is MoneyParser.Result.TooManyDecimals -> return onResult(AdjustError.AmountTooPrecise(parsed.allowed))
            MoneyParser.Result.TooLarge -> return onResult(AdjustError.AmountTooLarge)
            is MoneyParser.Result.Ambiguous, is MoneyParser.Result.WrongCurrency ->
                return onResult(AdjustError.AmountUnclear(MoneyParser.clarification(parsed, decimalSeparator).orEmpty()))
        }
        adjusting = true
        viewModelScope.launch {
            val result = try {
                transactions.adjustBalanceOrNull(walletId, actual, date, clientUuid)
            } finally {
                adjusting = false
            }
            onResult(
                when (result) {
                    is TransactionRepository.AdjustResult.Adjusted -> null
                    TransactionRepository.AdjustResult.Unchanged -> AdjustError.Unchanged
                    TransactionRepository.AdjustResult.FutureDate -> AdjustError.FutureDate
                    else -> AdjustError.Failed
                }
            )
        }
    }

    companion object {
        fun factory(walletId: Long) = containerFactory {
            WalletDetailViewModel(walletId, it.wallets, it.transactions, it.categories)
        }
    }
}
