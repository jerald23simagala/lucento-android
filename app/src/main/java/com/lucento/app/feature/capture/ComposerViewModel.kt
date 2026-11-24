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
import com.lucento.app.domain.capture.ReviewRow
import com.lucento.app.domain.capture.ReviewSetup
import com.lucento.app.domain.capture.TransactionCandidate
import com.lucento.app.domain.capture.RowEdit
import com.lucento.app.domain.capture.RowStatus
import com.lucento.app.domain.capture.TransactionInterpreter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
    /** When it was sent, for the time under the bubble. */
    val sentAtEpochMillis: Long = System.currentTimeMillis(),
)

sealed interface MessageState {
    data object Reading : MessageState
    /** Interpreted; [found] rows came from it, [saved] of them have been saved since. */
    data class Read(
        val found: Int,
        val saved: Int = 0,
        val engine: com.lucento.app.domain.capture.InterpreterEngine? = null,
        /** What kind of scan it was and what to check ("Read as a statement: 7 transactions…"); null for messages. */
        val note: String? = null,
        /** Scanned lines saved already (or waiting among this batch's rows), not added; each can still be added. */
        val held: List<com.lucento.app.domain.capture.HeldLine> = emptyList(),
        /** Each statement in the scan, checked against the ledger. */
        val statements: List<com.lucento.app.domain.capture.StatementReport> = emptyList(),
    ) : MessageState
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
    /** Multicurrency: the main currency, and each row in another currency's value in it (by row key). */
    val mainCurrency: String? = null,
    val fx: Map<String, com.lucento.app.domain.fx.FxQuote> = emptyMap(),
    /**
     * Some row in another currency has no online rate for its day (offline, or "Exchange rates online" off): the batch
     * uses [fxOffline] — the wallet's last rate, or its average (no gain or loss). The person chooses (owner, 2026-11-05).
     */
    val fxNeedsChoice: Boolean = false,
    val fxOffline: com.lucento.app.domain.fx.FxSource = com.lucento.app.domain.fx.FxSource.LAST_RATE,
    /**
     * Smarter reading is on (the person's consent, given in Settings; docs/CAPTURE.md) and usable here (signed in, in a
     * build with Lucento's reading proxy, on a plan that includes it): typed and spoken messages go to Gemini first, so
     * each takes a few seconds.
     */
    val geminiReads: Boolean = false,
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
    /** Multicurrency: the day's exchange rates (null in tests: rows then use the offline choice). */
    private val fxRates: com.lucento.app.data.fx.FxRateSource? = null,
    /** Whether Smarter reading can be used on this phone (signed in, with a proxy, in the plan); false in tests. */
    smartReadingAvailable: Flow<Boolean> = flowOf(false),
    /** The bundled merchant dictionaries, for reading scans on this phone (none in tests unless given). */
    private val merchants: com.lucento.app.data.capture.MerchantDictionary? = null,
    /** The bundled bank registry: a wallet's bank's names, so a document naming the bank finds the wallet. */
    private val institutions: com.lucento.app.core.banks.InstitutionRepository? = null,
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
        /** Multicurrency: the offline choice, and the day rates asked for ("USD@2026-11-06" → rate, null = none to be had). */
        val fxOffline: com.lucento.app.domain.fx.FxSource = com.lucento.app.domain.fx.FxSource.LAST_RATE,
        val marketRates: Map<String, java.math.BigDecimal?> = emptyMap(),
    )

    private val session = MutableStateFlow(Session())
    private var nextMessageId = 1L

    /** Saved transactions, kept for choosing the usual account. */
    @Volatile private var latestHistory: List<Transaction> = emptyList()

    val state: StateFlow<ComposerState> = combine(
        wallets.observeWallets(),
        categories.observeCategories(),
        transactions.observeTransactions(),
        preferences?.preferences?.map { Triple(it.defaultWalletId, it.primaryCurrency, it.smartReading) } ?: flowOf(Triple(null, null, false)),
        combine(session, smartReadingAvailable) { s, available -> s to available },
    ) { allWallets, categoryList, history, (savedDefault, main, smart), (s, available) ->
        val walletList = allWallets.transactable()
        val ids = walletList.map { it.id }.toSet()
        val defaultId = savedDefault?.takeIf { it in ids }
        val active = listOf(s.chosenWalletId, s.entryWalletId, defaultId).firstOrNull { it != null && it in ids }
            ?: walletList.singleOrNull()?.id
        val transfersAllowed = s.transfersEnabled && walletList.size > 1
        val setup = ReviewSetup(walletList, categoryList, separator, transfersAllowed, history)
        val statuses = s.rows.associate { it.key to BatchReview.check(it, setup) }
        latestHistory = history
        // Multicurrency: each row in another currency is valued at its day's rate, or the offline choice.
        val book = if (main == null) null else com.lucento.app.domain.fx.FxLedger.build(main, allWallets, history)
        var needsChoice = false
        val fx = if (main == null || book == null) emptyMap() else s.rows.mapNotNull { row ->
            val wallet = walletList.firstOrNull { it.id == row.walletId } ?: return@mapNotNull null
            val money = statuses[row.key]?.money ?: return@mapNotNull null
            if (wallet.currencyCode == main) return@mapNotNull null
            val market = s.marketRates[marketKey(wallet.currencyCode, row.date)]
            if (market == null) needsChoice = true
            val last = com.lucento.app.domain.fx.FxLedger.lastRate(main, wallet, history)
            com.lucento.app.domain.fx.FxQuotes.quote(main, wallet, money, market, s.fxOffline, book, last)?.let { row.key to it }
        }.toMap()
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
            mainCurrency = main,
            fx = fx,
            fxNeedsChoice = needsChoice,
            fxOffline = s.fxOffline,
            geminiReads = smart && available,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ComposerState())

    init {
        // Multicurrency: ask once for each day rate the rows need (foreign currency × date). Declared after [state].
        viewModelScope.launch {
            state.map { st ->
                val main = st.mainCurrency ?: return@map emptySet()
                st.rows.mapNotNull { row ->
                    st.wallets.firstOrNull { it.id == row.walletId }?.currencyCode?.takeIf { it != main }?.let { marketKey(it, row.date) }
                }.toSet()
            }.distinctUntilChanged().collect { needed ->
                val main = state.value.mainCurrency ?: return@collect
                val source = fxRates ?: return@collect
                needed.filter { it !in session.value.marketRates }.forEach { key ->
                    val currency = key.substringBefore('@')
                    val day = LocalDate.parse(key.substringAfter('@'))
                    val rate = runCatching { source.rate(currency, main, day) }.getOrNull()
                    session.update { it.copy(marketRates = it.marketRates + (key to rate)) }
                }
            }
        }
    }

    /** Multicurrency: the batch's choice when a row has no online rate — the wallet's last rate, or its average. */
    fun setFxOffline(choice: com.lucento.app.domain.fx.FxSource) {
        if (choice != com.lucento.app.domain.fx.FxSource.LAST_RATE && choice != com.lucento.app.domain.fx.FxSource.AVERAGE) return
        session.update { it.copy(fxOffline = choice) }
    }

    private fun marketKey(currency: String, date: LocalDate) = "$currency@$date"

    // ------------------------------------------------------------------ speech

    /** Whether the person agreed to online voice ([SpeechRequest]). Read fresh: a widget can open the mic at once. */
    suspend fun onlineVoiceAllowed(): Boolean = preferences?.preferences?.first()?.onlineVoice ?: false

    /** The person agreed to [SpeechRequest.CONSENT_TEXT] after an offline attempt failed. */
    fun allowOnlineVoice() {
        val prefs = preferences ?: return
        viewModelScope.launch { prefs.setOnlineVoice(true) }
    }

    /** Where the composer was opened from: a wallet pre-selects its account; "Income" makes income the default. */
    fun configure(walletId: Long?, typeHint: TransactionType?, transfersEnabled: Boolean) {
        session.update { it.copy(entryWalletId = walletId, typeHint = typeHint, transfersEnabled = transfersEnabled) }
    }

    // ------------------------------------------------------------------ the active wallet

    /** Opens the "Select wallet to use" picker (shown only while several accounts exist and none is active). */
    fun askForWallet() = session.update { it.copy(showWalletPicker = true) }

    /**
     * Closes the picker without sending; returns the held message and where it came from, so the screen can put typed or
     * spoken words back in the field. Receipt text is not handed back as typing: sent again as typed it would lose the
     * receipt reading rules (QA note 10).
     */
    fun dismissWalletPicker(): Pair<String, CaptureSource>? {
        val held = session.value.pending
        session.update { it.copy(showWalletPicker = false, pending = null) }
        return held
    }

    /**
     * Makes [walletId] the active wallet: later messages go there, and unsaved rows that were only following the
     * previous active wallet move with it. [remember] also makes it the default wallet (cash, bank, e-wallet or credit card).
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
        if (remember && wallet.type.canBeDefault && preferences != null) {
            viewModelScope.launch { preferences.setDefaultWallet(walletId) }
        }
        pending?.let { (text, source) -> send(text, source, chosen = walletId) }
    }

    /** Starts a transfer from the active wallet as its own row: the user picks where it goes and types the amount. */
    fun startTransfer() {
        val current = state.value
        if (!current.loaded || !current.transfersAllowed || current.saving) return
        // A transfer starts from a money account when the active wallet is only a card default (owner, 2026-11-18).
        val from = current.wallets.firstOrNull { it.id == (moneyWalletFor(current) ?: current.activeWalletId) }
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

    /**
     * Reads a scanned image on this phone (ML Kit, bundled model): a receipt adds one row, a statement or transaction
     * history one row per line. [cleanup] is deleted afterwards.
     */
    fun readReceipt(appContext: Context, uri: Uri, cleanup: File?) =
        readImages(appContext, listOf(uri), com.lucento.app.domain.capture.PageOrigin.PHOTO, listOfNotNull(cleanup))

    /**
     * Reads images as the pages of one scan (Document Scanner pages, photos, picked images), on this phone: each page
     * by on-device OCR, then pages joined into the documents they make up — several receipts are several rows, a
     * statement's pages one statement checked against the ledger ([TransactionInterpreter.interpretDocument]). The
     * [cleanup] files (Lucento's own copies) are deleted once read.
     */
    fun readImages(appContext: Context, uris: List<Uri>, origin: com.lucento.app.domain.capture.PageOrigin, cleanup: List<File> = emptyList()) {
        if (uris.isEmpty()) return
        val id = nextMessageId++
        val label = if (uris.size == 1) SCAN_LABEL else "Scanned ${uris.size} pages"
        session.update { s -> s.copy(messages = s.messages + ComposerMessage(id, CaptureSource.RECEIPT, label, MessageState.Reading), saveError = null) }
        viewModelScope.launch {
            val result = try {
                val pages = uris.map { uri -> com.lucento.app.domain.capture.ScanPage.ofFragments(ReceiptOcr.recognize(appContext, uri), origin) }
                interpreter.interpretDocument(com.lucento.app.domain.capture.ScanDocument(pages), context())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Interpretation.NothingFound("Lucento couldn’t read that image. Try another photo, or type the details instead.")
            } finally {
                // The photos were only needed for reading; remove them so scans don't pile up on the device.
                cleanup.forEach { it.delete() }
            }
            receive(id, CaptureSource.RECEIPT, result, replacing = null)
        }
    }

    /**
     * Reads a PDF on this phone ([PdfDocumentReader]): its own text, or each page by OCR when it has none. A locked PDF
     * calls [onPassword] (true when a password was tried and was wrong) so the screen can ask; the password is used for
     * this read only and never kept.
     */
    fun readPdf(appContext: Context, uri: Uri, password: String? = null, onPassword: (wrong: Boolean) -> Unit) {
        val id = nextMessageId++
        session.update { s -> s.copy(messages = s.messages + ComposerMessage(id, CaptureSource.RECEIPT, PDF_LABEL, MessageState.Reading), saveError = null) }
        viewModelScope.launch {
            fun dropMessage() = session.update { s -> s.copy(messages = s.messages.filterNot { it.id == id }) }
            val result = try {
                when (val read = PdfDocumentReader.read(appContext, uri, password)) {
                    is PdfDocumentReader.Result.Pages -> {
                        val label = if (read.pages.size == 1) PDF_LABEL else "PDF, ${read.pages.size} pages"
                        session.update { s -> s.copy(messages = s.messages.map { if (it.id == id) it.copy(text = label) else it }) }
                        val interpreted = interpreter.interpretDocument(com.lucento.app.domain.capture.ScanDocument(read.pages), context())
                        if (read.total > read.pages.size && interpreted is Interpretation.Candidates) {
                            interpreted.copy(note = listOfNotNull(interpreted.note, "Only the first ${read.pages.size} of ${read.total} pages were read — import the rest as another file.").joinToString(" "))
                        } else {
                            interpreted
                        }
                    }
                    PdfDocumentReader.Result.NeedsPassword, PdfDocumentReader.Result.WrongPassword -> {
                        dropMessage()
                        onPassword(read == PdfDocumentReader.Result.WrongPassword)
                        return@launch
                    }
                    PdfDocumentReader.Result.LockedUnsupported -> Interpretation.NothingFound(
                        "This PDF is locked with a password, and this phone’s Android version can’t open locked PDFs here. " +
                            "Open it in your bank’s app or a PDF viewer, save or print an unlocked copy, and import that.",
                    )
                    PdfDocumentReader.Result.Unreadable -> Interpretation.NothingFound("Lucento couldn’t open that PDF. Try another file, or scan the paper copy instead.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Interpretation.NothingFound("Lucento couldn’t read that PDF. Try another file, or scan the paper copy instead.")
            }
            receive(id, CaptureSource.RECEIPT, result, replacing = null)
        }
    }

    /** Adds a held line (already saved, or already among the rows) as a row after all: the person decided it is new. */
    fun addHeld(messageId: Long, index: Int) {
        if (session.value.saving) return
        session.update { s ->
            val message = s.messages.firstOrNull { it.id == messageId } ?: return@update s
            val read = message.state as? MessageState.Read ?: return@update s
            val line = read.held.getOrNull(index) ?: return@update s
            val row = ReviewRow.from(line.candidate, UUID.randomUUID().toString(), messageId, message.source, separator)
            s.copy(
                rows = s.rows + row,
                messages = s.messages.map {
                    if (it.id == messageId) it.copy(state = read.copy(found = read.found + 1, held = read.held.filterIndexed { i, _ -> i != index })) else it
                },
                saveError = null,
            )
        }
    }

    fun receiptUnavailable(message: String) {
        val id = nextMessageId++
        session.update { s -> s.copy(messages = s.messages + ComposerMessage(id, CaptureSource.RECEIPT, SCAN_LABEL, MessageState.NothingFound(message))) }
    }

    private suspend fun context(): InterpretationContext {
        val loaded = state.first { it.loaded }
        val s = session.value
        val default = s.chosenWalletId?.takeIf { id -> loaded.wallets.any { it.id == id } }
            ?: loaded.activeWalletId
            ?: lastUsedWallet(loaded.wallets)
        val registry = institutions?.let { runCatching { it.registry() }.getOrNull() }
        return InterpretationContext(
            loaded.wallets, loaded.categories, today(), default, s.typeHint,
            decimalSeparator = separator,
            spaceGrouping = java.text.DecimalFormatSymbols.getInstance().groupingSeparator in setOf(' ', ' ', ' ', ' '),
            dayFirst = phoneDayFirst(),
            moneyWalletId = moneyWalletFor(loaded),
            defaultWalletChosen = listOf(s.chosenWalletId, s.entryWalletId).any { id -> id != null && loaded.wallets.any { it.id == id } },
            // Read on this phone only (the scan reader, and a typed row's category); Smarter reading never receives these.
            directory = merchants?.directory() ?: com.lucento.app.domain.capture.merchant.MerchantDirectory.EMPTY,
            history = latestHistory,
            walletLabels = loaded.wallets.associate { w ->
                w.id to registry?.get(w.institutionId)?.let { listOf(it.name) + it.aliases }.orEmpty()
            },
        )
    }

    /**
     * Where income and transfers go while the active wallet is the saved default and that default is a credit card: the
     * first cash, bank or e-wallet (owner, 2026-11-18). Null when the person picked the wallet (here, or by opening the
     * composer from it), when the default isn't a card, or when there is no money account to use instead.
     */
    private fun moneyWalletFor(state: ComposerState): Long? {
        val s = session.value
        val picked = listOf(s.chosenWalletId, s.entryWalletId).any { id -> id != null && state.wallets.any { it.id == id } }
        if (picked) return null
        val active = state.wallets.firstOrNull { it.id == state.activeWalletId } ?: return null
        if (active.type != com.lucento.app.domain.WalletType.CREDIT_CARD) return null
        return state.wallets.firstOrNull { it.type.isSpendable }?.id
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
                    // A scan never adds a row twice: lines repeating rows already waiting here are held, like saved ones.
                    val waiting = if (replacing != null) s.rows.filterNot { it.messageId == replacing } else s.rows
                    val repeats = if (source == CaptureSource.RECEIPT) BatchReview.repeatsInBatch(result.items, waiting, separator) else emptySet()
                    val rows = result.items.filterIndexed { i, _ -> i !in repeats }.map { ReviewRow.from(it, UUID.randomUUID().toString(), id, source, separator) }
                    val alsoHeld = result.items.filterIndexed { i, _ -> i in repeats }
                        .map { com.lucento.app.domain.capture.HeldLine(it, null, "Already among the rows above, waiting to be saved") }
                    MessageState.Read(rows.size, engine = result.engine, note = result.note, held = result.held + alsoHeld, statements = result.statements) to rows
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
            // Multicurrency: a row in another currency is saved with its value in the main currency.
            val fx = current.fx[row.key]?.asValue
            if (row.type.isTransfer) {
                val (outFx, inFx) = current.mainCurrency
                    ?.let { main -> com.lucento.app.domain.fx.FxTransfer.legValues(main, money, money, fx) } ?: (null to null)
                TransactionRepository.BatchEntry.Move(
                    TransactionRepository.TransferInput(
                        null, row.key, row.walletId!!, row.toWalletId!!, money, row.date, row.description, outFx = outFx, inFx = inFx,
                    )
                )
            } else {
                TransactionRepository.BatchEntry.Single(
                    TransactionRepository.Input(null, row.key, row.type, money, row.walletId!!, row.categoryId, row.date, row.description, fx = fx)
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
        /** The user's side of the chat for a scanned image (a receipt, a statement or an app's history). */
        const val SCAN_LABEL = "Scanned image"

        /** The user's side of the chat for an imported PDF. */
        const val PDF_LABEL = "PDF"

        val Factory = containerFactory {
            ComposerViewModel(
                it.transactions, it.wallets, it.categories, it.interpreter, it.preferences, fxRates = it.fxRates,
                merchants = it.merchants,
                institutions = it.institutions,
                // Signed in, a proxy in this build, and a plan that includes it: the interpreter's own rule.
                smartReadingAvailable = combine(it.auth.observeSignedInEmail(), it.entitlements.state) { email, plan ->
                    email != null && com.lucento.app.BuildConfig.AI_INTERPRET_URL.isNotBlank() &&
                        com.lucento.app.domain.entitlement.ProGates.smartReadingAllowed(plan, System.currentTimeMillis())
                },
            )
        }

        /** True when the phone writes short dates day first (03/11 for 3 November). */
        fun phoneDayFirst(locale: java.util.Locale = java.util.Locale.getDefault()): Boolean {
            val pattern = (java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT, locale) as? java.text.SimpleDateFormat)?.toPattern() ?: return false
            return pattern.indexOf('d').let { d -> d >= 0 && d < pattern.indexOf('M').let { if (it < 0) Int.MAX_VALUE else it } }
        }
    }
}
