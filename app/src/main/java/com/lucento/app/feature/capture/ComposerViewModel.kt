package com.lucento.app.feature.capture

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucento.app.core.containerFactory
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.data.preferences.UserPreferencesRepository
import com.lucento.app.data.repository.CategoryRepository
import com.lucento.app.data.repository.TransactionRepository
import com.lucento.app.data.repository.WalletRepository
import com.lucento.app.data.repository.saveBatchOrNull
import com.lucento.app.domain.transactable
import com.lucento.app.domain.Category
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.capture.BatchReview
import com.lucento.app.domain.capture.BatchSummary
import com.lucento.app.domain.capture.CandidateFlag
import com.lucento.app.domain.capture.CaptureSource
import com.lucento.app.domain.capture.Interpretation
import com.lucento.app.domain.capture.InterpretationContext
import com.lucento.app.domain.capture.LocalTransactionInterpreter
import com.lucento.app.domain.capture.ReceiptParser
import com.lucento.app.domain.capture.ReviewRow
import com.lucento.app.domain.capture.ReviewSetup
import com.lucento.app.domain.capture.TransactionCandidate
import com.lucento.app.domain.capture.RowEdit
import com.lucento.app.domain.capture.RowStatus
import com.lucento.app.domain.capture.TransactionInterpreter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.util.UUID

/** One thing the user sent: a typed or spoken message, or a receipt photo. */
data class ComposerMessage(
    val id: Long,
    val source: CaptureSource,
    val text: String,
    val state: MessageState,
)

sealed interface MessageState {
    data object Reading : MessageState
    /** Interpreted; [found] rows came from it, [saved] of them have been saved since. */
    data class Read(val found: Int, val saved: Int = 0) : MessageState
    data class NothingFound(val message: String) : MessageState
}

data class ComposerState(
    val loaded: Boolean = false,
    val wallets: List<Wallet> = emptyList(),
    val categories: List<Category> = emptyList(),
    val messages: List<ComposerMessage> = emptyList(),
    val rows: List<ReviewRow> = emptyList(),
    val statuses: Map<String, RowStatus> = emptyMap(),
    val summary: BatchSummary = BatchSummary(0, 0, emptyList(), emptyList(), 0),
    val saving: Boolean = false,
    val saveError: String? = null,
    val transfersAllowed: Boolean = false,
    /** The message being corrected; sending replaces its rows but keeps the user's edits. */
    val editingMessageId: Long? = null,
    /** Where new rows go unless the words name an account: the chosen pill, the entry wallet, the default, the only one. */
    val activeWalletId: Long? = null,
    val defaultWalletId: Long? = null,
    /** Several accounts and none active yet: the wallet picker is (or will be) asked. */
    val needsWalletChoice: Boolean = false,
    val showWalletPicker: Boolean = false,
)

/**
 * The conversational composer: messages in, editable rows out, one save for the whole batch.
 *
 * Interpretation goes through [TransactionInterpreter] (on this phone by default); rows are checked with
 * [BatchReview]; saving goes through [TransactionRepository.saveBatch], the same validation and persistence as every
 * other transaction, written atomically with each row's own idempotency key. Nothing is saved without the user's tap.
 */
class ComposerViewModel(
    private val transactions: TransactionRepository,
    wallets: WalletRepository,
    categories: CategoryRepository,
    private val interpreter: TransactionInterpreter,
    private val preferences: UserPreferencesRepository? = null,
    private val separator: Char = MoneyFormatter.decimalSeparator(),
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    private data class Session(
        val messages: List<ComposerMessage> = emptyList(),
        val rows: List<ReviewRow> = emptyList(),
        val saving: Boolean = false,
        val saveError: String? = null,
        val editingMessageId: Long? = null,
        /** The wallet the composer was opened from (a wallet's own screen). */
        val entryWalletId: Long? = null,
        /** A pill the user tapped, or the picker's answer. Wins over everything else. */
        val chosenWalletId: Long? = null,
        val typeHint: TransactionType? = null,
        val transfersEnabled: Boolean = true,
        val showWalletPicker: Boolean = false,
        /** A message sent before any wallet was chosen; it goes out right after the picker answers. */
        val pending: Pair<String, CaptureSource>? = null,
    )

    private val session = MutableStateFlow(Session())
    private var nextMessageId = 1L

    /** Saved transactions, kept for choosing the usual account. */
    @Volatile private var latestHistory: List<Transaction> = emptyList()

    val state: StateFlow<ComposerState> = combine(
        wallets.observeWallets(),
        categories.observeCategories(),
        transactions.observeTransactions(),
        preferences?.preferences?.map { it.defaultWalletId } ?: flowOf(null),
        session,
    ) { allWallets, categoryList, history, savedDefault, s ->
        val walletList = allWallets.transactable()
        val ids = walletList.map { it.id }.toSet()
        val defaultId = savedDefault?.takeIf { it in ids }
        val active = listOf(s.chosenWalletId, s.entryWalletId, defaultId).firstOrNull { it != null && it in ids }
            ?: walletList.singleOrNull()?.id
        val transfersAllowed = s.transfersEnabled && walletList.size > 1
        val setup = ReviewSetup(walletList, categoryList, separator, transfersAllowed, history)
        val statuses = s.rows.associate { it.key to BatchReview.check(it, setup) }
        latestHistory = history
        ComposerState(
            loaded = true,
            wallets = walletList,
            categories = categoryList,
            messages = s.messages,
            rows = s.rows,
            statuses = statuses,
            summary = BatchReview.summarize(s.rows, statuses),
            saving = s.saving,
            saveError = s.saveError,
            transfersAllowed = transfersAllowed,
            editingMessageId = s.editingMessageId,
            activeWalletId = active,
            defaultWalletId = defaultId,
            needsWalletChoice = active == null && walletList.size > 1,
            showWalletPicker = s.showWalletPicker && active == null && walletList.size > 1,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ComposerState())

    /** Where the composer was opened from: a wallet pre-selects its account; "Income" makes income the default. */
    fun configure(walletId: Long?, typeHint: TransactionType?, transfersEnabled: Boolean) {
        session.update { it.copy(entryWalletId = walletId, typeHint = typeHint, transfersEnabled = transfersEnabled) }
    }

    // ------------------------------------------------------------------ the active wallet

    /** Opens the "Where should these go?" picker (shown only while several accounts exist and none is active). */
    fun askForWallet() = session.update { it.copy(showWalletPicker = true) }

    fun dismissWalletPicker() = session.update { it.copy(showWalletPicker = false, pending = null) }

    /**
     * Makes [walletId] the active wallet: later messages go there, and unsaved rows that were only following the
     * previous active wallet move with it. [remember] also makes it the default wallet (cash, bank or e-wallet only).
     */
    fun chooseWallet(walletId: Long, remember: Boolean = false) {
        val wallet = state.value.wallets.firstOrNull { it.id == walletId } ?: return
        val pending = session.value.pending
        session.update { s ->
            s.copy(
                chosenWalletId = walletId,
                rows = if (s.saving) s.rows else BatchReview.retarget(s.rows, walletId),
                showWalletPicker = false,
                pending = null,
            )
        }
        if (remember && wallet.type.isSpendable && preferences != null) {
            viewModelScope.launch { preferences.setDefaultWallet(walletId) }
        }
        pending?.let { (text, source) -> send(text, source, chosen = walletId) }
    }

    /** Starts a transfer from the active wallet as its own row: the user picks where it goes and types the amount. */
    fun startTransfer() {
        val current = state.value
        if (!current.loaded || !current.transfersAllowed || current.saving) return
        val from = current.wallets.firstOrNull { it.id == current.activeWalletId }
        val id = nextMessageId++
        val text = if (from != null) "New transfer from ${from.name}" else "New transfer"
        val candidate = TransactionCandidate(
            description = "",
            type = TransactionType.TRANSFER_OUT,
            amount = null,
            walletId = from?.id,
            toWalletId = null,
            categoryId = null,
            date = today(),
            sourceText = text,
            flags = setOf(CandidateFlag.AMOUNT_MISSING),
        )
        session.update { s ->
            s.copy(
                messages = s.messages + ComposerMessage(id, CaptureSource.TYPED, text, MessageState.Read(1)),
                rows = s.rows + ReviewRow.from(candidate, UUID.randomUUID().toString(), id, CaptureSource.TYPED, separator),
                saveError = null,
            )
        }
    }

    // ------------------------------------------------------------------ messages

    /** [chosen] is the wallet the picker just returned, before [state] has caught up with it. */
    fun send(text: String, source: CaptureSource, chosen: Long? = null) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        if (chosen == null && state.value.needsWalletChoice) {
            // Ask first, then send: the rows should land in the wallet the user means.
            session.update { it.copy(showWalletPicker = true, pending = trimmed to source) }
            return
        }
        val replacing = session.value.editingMessageId
        val id = nextMessageId++
        session.update { s ->
            val kept = if (replacing != null) s.messages.filterNot { it.id == replacing } else s.messages
            s.copy(messages = kept + ComposerMessage(id, source, trimmed, MessageState.Reading), editingMessageId = null, saveError = null)
        }
        viewModelScope.launch {
            val context = context()
            val result = runCatching { interpreter.interpretText(trimmed, context) }
                .getOrElse { e -> if (e is CancellationException) throw e else Interpretation.NothingFound("That couldn’t be read. Try rewording it.") }
            receive(id, source, result, replacing)
        }
    }

    /** Reads a receipt image on this phone (ML Kit, bundled model) and adds its row. [cleanup] is deleted afterwards. */
    fun readReceipt(appContext: Context, uri: Uri, cleanup: File?) {
        val id = nextMessageId++
        session.update { s -> s.copy(messages = s.messages + ComposerMessage(id, CaptureSource.RECEIPT, "Receipt photo", MessageState.Reading), saveError = null) }
        viewModelScope.launch {
            val result = try {
                val lines = ReceiptParser.mergeRows(ReceiptOcr.recognize(appContext, uri))
                interpreter.interpretReceipt(lines, context())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Interpretation.NothingFound("Lucento couldn’t read that image. Try another photo, or type the details instead.")
            } finally {
                // The photo was only needed for reading; remove it so receipts don't pile up on the device.
                cleanup?.delete()
            }
            receive(id, CaptureSource.RECEIPT, result, replacing = null)
        }
    }

    fun receiptUnavailable(message: String) {
        val id = nextMessageId++
        session.update { s -> s.copy(messages = s.messages + ComposerMessage(id, CaptureSource.RECEIPT, "Receipt photo", MessageState.NothingFound(message))) }
    }

    private suspend fun context(): InterpretationContext {
        val loaded = state.first { it.loaded }
        val s = session.value
        val default = s.chosenWalletId?.takeIf { id -> loaded.wallets.any { it.id == id } }
            ?: loaded.activeWalletId
            ?: lastUsedWallet(loaded.wallets)
        return InterpretationContext(loaded.wallets, loaded.categories, today(), default, s.typeHint)
    }

    /** The account of the most recently recorded income or expense, so the usual account is the default. */
    private fun lastUsedWallet(wallets: List<Wallet>): Long? {
        val ids = wallets.map { it.id }.toSet()
        return latestHistory.filter { !it.isTransfer && it.walletId in ids }.maxByOrNull { it.createdAtEpochMillis }?.walletId
            ?: wallets.firstOrNull()?.id
    }

    private fun receive(id: Long, source: CaptureSource, result: Interpretation, replacing: Long?) {
        session.update { s ->
            val (messageState, newRows) = when (result) {
                is Interpretation.Candidates -> {
                    val rows = result.items.map { ReviewRow.from(it, UUID.randomUUID().toString(), id, source, separator) }
                    MessageState.Read(rows.size) to rows
                }
                is Interpretation.NothingFound -> MessageState.NothingFound(result.message) to emptyList()
            }
            val previous = if (replacing != null) s.rows.filter { it.messageId == replacing } else emptyList()
            val categories = state.value.categories
            val merged = BatchReview.carryOver(previous, newRows, categories).map { it.copy(messageId = id) }
            val others = if (replacing != null) s.rows.filterNot { it.messageId == replacing } else s.rows
            s.copy(
                messages = s.messages.map { if (it.id == id) it.copy(state = messageState) else it },
                rows = others + merged,
            )
        }
    }

    /** Puts a sent message back into the input for correction; returns its text, or null for receipts. */
    fun startEditing(messageId: Long): String? {
        val message = session.value.messages.firstOrNull { it.id == messageId } ?: return null
        if (message.source == CaptureSource.RECEIPT || message.state is MessageState.Reading) return null
        session.update { it.copy(editingMessageId = messageId) }
        return message.text
    }

    fun cancelEditing() = session.update { it.copy(editingMessageId = null) }

    // ------------------------------------------------------------------ rows

    fun edit(key: String, edit: RowEdit) {
        if (session.value.saving) return
        session.update { s -> s.copy(rows = BatchReview.edit(s.rows, key, edit, state.value.categories, separator), saveError = null) }
    }

    fun remove(key: String) {
        if (session.value.saving) return
        session.update { s -> s.copy(rows = BatchReview.remove(s.rows, key)) }
    }

    fun discardAll() = session.update {
        Session(entryWalletId = it.entryWalletId, chosenWalletId = it.chosenWalletId, typeHint = it.typeHint, transfersEnabled = it.transfersEnabled)
    }

    fun dismissError() = session.update { it.copy(saveError = null) }

    // ------------------------------------------------------------------ saving

    /**
     * Saves the whole batch in one database transaction — only once every row is complete; an incomplete row blocks
     * the save (it is highlighted until fixed or removed). [onSaved] runs only after the write has committed.
     */
    fun save(onSaved: (count: Int) -> Unit) {
        val current = state.value
        if (session.value.saving || !current.loaded || !current.summary.canSave) return
        val ready = current.rows
        val entries = ready.map { row ->
            val money = current.statuses.getValue(row.key).money!!
            if (row.type.isTransfer) {
                TransactionRepository.BatchEntry.Move(
                    TransactionRepository.TransferInput(null, row.key, row.walletId!!, row.toWalletId!!, money, row.date, row.description)
                )
            } else {
                TransactionRepository.BatchEntry.Single(
                    TransactionRepository.Input(null, row.key, row.type, money, row.walletId!!, row.categoryId, row.date, row.description)
                )
            }
        }
        session.update { it.copy(saving = true, saveError = null) }
        viewModelScope.launch {
            when (val result = transactions.saveBatchOrNull(entries)) {
                is TransactionRepository.BatchResult.Saved -> {
                    val savedKeys = ready.map { it.key }.toSet()
                    session.update { s ->
                        val savedPerMessage = s.rows.filter { it.key in savedKeys }.groupingBy { it.messageId }.eachCount()
                        s.copy(
                            rows = s.rows.filterNot { it.key in savedKeys },
                            messages = s.messages.map { m ->
                                val st = m.state
                                val n = savedPerMessage[m.id] ?: 0
                                if (st is MessageState.Read && n > 0) m.copy(state = st.copy(saved = st.saved + n)) else m
                            },
                            saving = false,
                        )
                    }
                    onSaved(ready.size)
                }
                is TransactionRepository.BatchResult.Rejected -> {
                    val row = ready.getOrNull(result.index)
                    val name = row?.description?.takeIf { it.isNotBlank() }?.let { "“$it”" } ?: "One row"
                    session.update {
                        it.copy(saving = false, saveError = "Nothing was saved. $name no longer matches its account or category — check it and try again.")
                    }
                }
                null -> session.update {
                    it.copy(saving = false, saveError = "Nothing was saved — the phone couldn’t write it. Try again; nothing will be saved twice.")
                }
            }
        }
    }

    companion object {
        val Factory = containerFactory {
            ComposerViewModel(it.transactions, it.wallets, it.categories, LocalTransactionInterpreter(), it.preferences)
        }
    }
}
