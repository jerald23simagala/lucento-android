package com.lucento.app.feature.ask

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucento.app.core.containerFactory
import com.lucento.app.data.ai.AskLucentoClient
import com.lucento.app.data.preferences.UserPreferencesRepository
import com.lucento.app.data.reports.ReportPdfWriter
import com.lucento.app.data.repository.BudgetRepository
import com.lucento.app.data.repository.CategoryRepository
import com.lucento.app.data.repository.RecurringRepository
import com.lucento.app.data.repository.TransactionRepository
import com.lucento.app.data.repository.WalletRepository
import com.lucento.app.domain.ask.AnswerCheck
import com.lucento.app.domain.ask.AskAiRoute
import com.lucento.app.domain.ask.AskRouter
import com.lucento.app.domain.ask.AskScope
import com.lucento.app.domain.ask.AskTopic
import com.lucento.app.domain.ask.AskSnapshotBuilder
import com.lucento.app.domain.ask.CheckedAnswer
import com.lucento.app.domain.ask.LocalAnswers
import com.lucento.app.domain.ask.PurchaseCheck
import com.lucento.app.domain.ask.Report
import com.lucento.app.domain.ask.ReportBuilder
import com.lucento.app.domain.ask.ReportInputs
import com.lucento.app.domain.ask.ReportKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

/** Who worked an answer out: the phone (reports, never presented as AI) or Gemini (once the person confirmed). */
enum class AnswerSource { PHONE, GEMINI }

sealed interface AskMessage {
    val id: Long

    data class Question(override val id: Long, val text: String) : AskMessage

    data class Answer(
        override val id: Long,
        val parts: List<CheckedAnswer.Part>,
        val source: AnswerSource,
        val report: Report? = null,
        /** A line under the answer: why it fell back to the phone, or that figures couldn't be checked. */
        val note: String? = null,
        val unverified: Boolean = false,
        val followUps: List<String> = emptyList(),
        /** The question needs AI answers, which the person turned off: the answer offers to turn them on. */
        val offersAi: Boolean = false,
        /** Gemini didn't answer [retry] (busy, too slow, unreachable): the answer offers to ask it again. */
        val retry: String? = null,
    ) : AskMessage {
        val text: String get() = parts.joinToString("") { it.text }
    }

    /**
     * AI answers are on (the default) but this account hasn't confirmed them yet (owner, 2026-11-17): before [question]
     * leaves the phone, Ask says what would be sent. "Ask Gemini" sends it; "Keep answers on this phone" turns AI answers
     * off and answers here what the phone can.
     */
    data class ConfirmAi(override val id: Long, val question: String) : AskMessage
}

data class AskUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val currency: String = "USD",
    val messages: List<AskMessage> = emptyList(),
    val thinking: Boolean = false,
    /** This build has an AI proxy (debug builds without a test proxy don't). */
    val aiConfigured: Boolean = false,
    /** Signed in to a Lucento account: the proxy counts the allowance per account. */
    val signedIn: Boolean = false,
    /** AI answers are on: by default, until the person turns them off (owner, 2026-11-17). */
    val aiOn: Boolean = true,
    /** The person confirmed what AI answers send; until then a question shows [AskMessage.ConfirmAi] first. */
    val aiConfirmed: Boolean = false,
    /** What is left of the AI allowance, once the server has said. */
    val remaining: Int? = null,
    /** The allowance runs per day (Lucento Pro) rather than per month (the free plan). */
    val aiPerDay: Boolean = false,
    val hasRecords: Boolean = false,
    /** Lucento Pro: statements as PDF are Pro; on screen they are always free (docs/MONETIZATION.md §3.4). */
    val pdfLocked: Boolean = false,
) {
    val aiAvailable: Boolean get() = aiConfigured && signedIn
    val aiActive: Boolean get() = aiAvailable && aiOn
}

/**
 * Ask Lucento (docs/ASK.md). Preset questions and the report tiles are answered on the phone by [ReportBuilder] — exact,
 * offline, labelled "Calculated on this phone". AI answers are on by default for a signed-in account, and a free-form
 * question goes to Gemini, with [AskSnapshotBuilder]'s summary, only once the person confirmed what is sent
 * ([AskAiRoute]); the reply's figures are filled in here ([AnswerCheck]), any report it asks for is built here, and
 * nothing it says can change data. Turned off, or without a sign-in or a proxy, Lucento says what it can do instead.
 * The conversation lives only in memory.
 */
class AskLucentoViewModel(
    transactions: TransactionRepository,
    wallets: WalletRepository,
    categories: CategoryRepository,
    budgets: BudgetRepository,
    recurring: RecurringRepository,
    goals: com.lucento.app.data.repository.GoalRepository?,
    private val preferences: UserPreferencesRepository,
    signedInEmail: Flow<String?>,
    private val client: AskLucentoClient,
    private val pdf: ReportPdfWriter,
    /** Lucento Pro: the plan this phone holds; null in tests (everything allowed). */
    private val entitlements: StateFlow<com.lucento.app.domain.entitlement.EntitlementState>? = null,
) : ViewModel() {

    private val conversation = MutableStateFlow(Conversation())

    private data class Conversation(
        val messages: List<AskMessage> = emptyList(),
        val thinking: Boolean = false,
        val remaining: Int? = null,
        /** How the server said the allowance runs (true: per day); null until it has answered. */
        val remainingPerDay: Boolean? = null,
        val nextId: Long = 1,
    )

    private val inputs: StateFlow<ReportInputs?> = combine(
        combine(
            transactions.observeTransactions(),
            wallets.observeWallets(),
            categories.observeCategories(),
            budgets.observeBudgets(),
            recurring.observeSchedules(),
        ) { t, w, c, b, s -> ReportInputs(w, t, c, b, s, LocalDate.now()) },
        goals?.observeGoals() ?: flowOf(emptyList()),
        goals?.observeContributions() ?: flowOf(emptyList()),
        preferences.preferences.map { it.primaryCurrency }.distinctUntilChanged(),
    ) { base, g, contributions, main ->
        // Multicurrency: reports and AI answers read every figure in the main currency, FX results included.
        val ledger = com.lucento.app.domain.fx.FxTranslation.translate(main, base.wallets, base.transactions)
        base.copy(
            wallets = ledger.wallets,
            transactions = ledger.withFxResults,
            categories = ledger.withFxCategories(base.categories),
            goals = g,
            contributions = contributions,
        )
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val uiState: StateFlow<AskUiState> = combine(inputs, preferences.preferences, signedInEmail, conversation, entitlements ?: flowOf(null)) { data, prefs, email, chat, plan ->
        AskUiState(
            loading = data == null,
            today = data?.today ?: LocalDate.now(),
            currency = prefs.primaryCurrency,
            messages = chat.messages,
            thinking = chat.thinking,
            aiConfigured = client.isConfigured,
            signedIn = email != null,
            aiOn = prefs.askLucentoAi,
            aiConfirmed = prefs.askAiConfirmed,
            remaining = chat.remaining,
            // The server's word once it has answered; before that, the plan this phone holds.
            aiPerDay = chat.remainingPerDay
                ?: (plan != null && com.lucento.app.domain.entitlement.ProGates.aiAllowancePerDay(plan, System.currentTimeMillis())),
            hasRecords = data != null && (data.transactions.isNotEmpty() || data.wallets.isNotEmpty()),
            pdfLocked = plan != null && !com.lucento.app.domain.entitlement.ProGates.pdfAllowed(plan, System.currentTimeMillis()),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AskUiState())

    private fun pdfAllowedNow(): Boolean =
        entitlements?.value?.let { com.lucento.app.domain.entitlement.ProGates.pdfAllowed(it, System.currentTimeMillis()) } ?: true

    /** Asks [question]. Ignored while an answer is on its way (one at a time, never duplicated). */
    fun ask(question: String) = send(question, echo = true, choice = AiChoice.AS_SET)

    /** How a question meets AI answers: as they are set, or right after the person agreed to or declined them. */
    private enum class AiChoice { AS_SET, AGREED, DECLINED }

    private fun send(question: String, echo: Boolean, choice: AiChoice) {
        val text = question.trim().take(QUESTION_LIMIT)
        if (text.isEmpty() || conversation.value.thinking) return
        val data = inputs.value ?: return
        val state = uiState.value
        val route = AskAiRoute.of(
            available = state.aiAvailable,
            on = when (choice) {
                AiChoice.AGREED -> true
                AiChoice.DECLINED -> false
                AiChoice.AS_SET -> state.aiOn
            },
            confirmed = choice == AiChoice.AGREED || state.aiConfirmed,
            offTopic = AskScope.topic(text) == AskTopic.OFF_TOPIC,
        )
        // One confirmation at a time: a new question replaces one still waiting for the person.
        conversation.update { c -> c.copy(messages = c.messages.filterNot { it is AskMessage.ConfirmAi }) }
        if (route == AskAiRoute.CONFIRM_FIRST) {
            // Nothing leaves the phone: the question waits under a card saying what would be sent.
            if (echo) add(AskMessage.Question(nextId(), text), thinking = false)
            add(AskMessage.ConfirmAi(nextId(), text), thinking = false)
            return
        }
        // Asked again after a confirmation, the question is already the last message: it isn't history as well.
        val history = conversation.value.messages.let { messages ->
            if (!echo && (messages.lastOrNull() as? AskMessage.Question)?.text == text) messages.dropLast(1) else messages
        }
        if (echo) add(AskMessage.Question(nextId(), text), thinking = true) else conversation.update { it.copy(thinking = true) }
        viewModelScope.launch {
            val answer = try {
                answer(text, data, state, history, aiActive = route == AskAiRoute.GEMINI, declined = choice == AiChoice.DECLINED)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AskMessage.Answer(0, plain("Something went wrong working that out. Nothing was changed — try again."), AnswerSource.PHONE)
            }
            add(answer.copy(id = nextId()), thinking = false)
        }
    }

    /** The same report for another month (the report sheet's month stepper). */
    fun rebuild(report: Report, month: YearMonth): Report? {
        val data = inputs.value ?: return null
        return ReportBuilder.build(report.kind, data, report.currencyCode, month)
    }

    /**
     * Turns AI answers on (only from the consent sheet, which the person accepted) or off. Turned on from an answer that
     * offered it, [thenAsk] is that question, asked again (without repeating it in the conversation) now that they agreed.
     */
    fun setAiAnswers(enabled: Boolean, thenAsk: String? = null) {
        viewModelScope.launch {
            preferences.setAskLucentoAi(enabled)
            if (enabled && thenAsk != null) send(thenAsk, echo = false, choice = AiChoice.AGREED)
        }
    }

    /** "Ask Gemini" on [AskMessage.ConfirmAi] [cardId]: AI answers are agreed to, and the waiting question is sent. */
    fun confirmAi(cardId: Long) = resolveConfirmation(cardId, agreed = true)

    /** "Keep answers on this phone": AI answers turn off, nothing is sent, and the phone answers what it can. */
    fun declineAi(cardId: Long) = resolveConfirmation(cardId, agreed = false)

    private fun resolveConfirmation(cardId: Long, agreed: Boolean) {
        if (conversation.value.thinking) return
        // The card goes at the first tap, so a second tap (or the other button) finds nothing to do.
        val card = conversation.value.messages.firstOrNull { it.id == cardId } as? AskMessage.ConfirmAi ?: return
        conversation.update { c -> c.copy(messages = c.messages.filterNot { it.id == cardId }) }
        viewModelScope.launch {
            preferences.setAskLucentoAi(agreed)
            send(card.question, echo = false, choice = if (agreed) AiChoice.AGREED else AiChoice.DECLINED)
        }
    }

    fun clear() {
        if (!conversation.value.thinking) conversation.update { it.copy(messages = emptyList()) }
    }

    // ------------------------------------------------------------------ speech (the composer's microphone, shared)

    /** Whether the person agreed to online voice ([com.lucento.app.feature.capture.SpeechRequest]); read fresh. */
    suspend fun onlineVoiceAllowed(): Boolean = preferences.preferences.first().onlineVoice

    /** The person agreed to online voice after an offline attempt failed: the same preference as the composer's. */
    fun allowOnlineVoice() {
        viewModelScope.launch { preferences.setOnlineVoice(true) }
    }

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

    private suspend fun answer(
        text: String,
        data: ReportInputs,
        state: AskUiState,
        history: List<AskMessage>,
        aiActive: Boolean,
        /** The person just chose "Keep answers on this phone" for this question. */
        declined: Boolean = false,
    ): AskMessage.Answer {
        // Owner test round 2: a question plainly not about money is answered here, without spending the AI allowance.
        val topic = AskScope.topic(text)
        if (topic == AskTopic.OFF_TOPIC) return AskMessage.Answer(0, plain(AskScope.OFF_TOPIC_REPLY), AnswerSource.PHONE)
        val route = AskRouter.route(text, data.today)
        suspend fun local(note: String? = null): AskMessage.Answer? {
            if (topic == AskTopic.PURCHASE) {
                // Whether a purchase fits, from the same figures the AI would see (PurchaseCheck); never presented as AI.
                val figures = withContext(Dispatchers.Default) { AskSnapshotBuilder.build(data, state.currency) }
                return AskMessage.Answer(0, PurchaseCheck.answer(figures, AskScope.price(text, state.currency)), AnswerSource.PHONE, note = note)
            }
            return route?.let {
                val report = ReportBuilder.build(it.kind, data, state.currency, it.month, it.top)
                AskMessage.Answer(0, plain(LocalAnswers.summary(report)), AnswerSource.PHONE, report = report, note = note)
            }
        }
        if (!aiActive) {
            return local(if (declined) AskAiCopy.DECLINED_NOTE else null) ?: when {
                declined -> AskMessage.Answer(0, plain(AskAiCopy.DECLINED), AnswerSource.PHONE)
                state.aiAvailable -> AskMessage.Answer(
                    0,
                    plain("I can answer that with AI. Turn on AI answers and I’ll send your figures and recent transactions to Gemini to work it out — you’ll see exactly what’s sent first."),
                    AnswerSource.PHONE,
                    offersAi = true,
                )
                state.aiConfigured -> AskMessage.Answer(0, plain(AskAiCopy.signInHint(state.aiPerDay)), AnswerSource.PHONE)
                else -> AskMessage.Answer(
                    0,
                    plain("On this phone I can prepare your net worth, cash flow, budget, debts and top categories — try one of those below."),
                    AnswerSource.PHONE,
                )
            }
        }
        val snapshot = withContext(Dispatchers.Default) { AskSnapshotBuilder.build(data, state.currency) }
        val turns = history.filterNot { it is AskMessage.ConfirmAi }.takeLast(AskLucentoClientTurns).mapNotNull {
            when (it) {
                is AskMessage.Question -> AskLucentoClient.Turn(fromUser = true, text = it.text)
                is AskMessage.Answer -> AskLucentoClient.Turn(fromUser = false, text = it.text)
                is AskMessage.ConfirmAi -> null
            }
        }
        val result = try {
            withTimeoutOrNull(REMOTE_TIMEOUT_MS) { client.ask(text, turns, snapshot) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AskLucentoClient.Result.Failed(e.javaClass.simpleName)
        }
        return when (result) {
            is AskLucentoClient.Result.Answered -> {
                conversation.update {
                    it.copy(
                        remaining = result.remaining,
                        remainingPerDay = when (result.plan) {
                            "PRO" -> true
                            "FREE" -> false
                            else -> it.remainingPerDay
                        },
                    )
                }
                val checked = AnswerCheck.check(result.answer, snapshot)
                val kind = ReportKind.entries.firstOrNull { it.name == result.report && !it.needsWallet }
                val current = YearMonth.from(data.today)
                val month = result.month?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
                    ?.takeIf { !it.isAfter(current) && !it.isBefore(current.minusMonths(MAX_MONTHS_BACK)) } ?: current
                AskMessage.Answer(
                    0,
                    checked.parts,
                    AnswerSource.GEMINI,
                    report = kind?.let { ReportBuilder.build(it, data, state.currency, month) },
                    note = if (checked.hasUnverifiedFigures) "This answer has amounts Lucento didn’t calculate — check them against your records." else null,
                    unverified = checked.hasUnverifiedFigures,
                    followUps = AnswerCheck.followUps(result.followUps),
                )
            }
            else -> {
                val reason = failureReason(result, state.signedIn)
                // Worth asking again: Gemini was busy, slow or unreachable. Not a used-up allowance or an ended session.
                val retry = text.takeIf { result is AskLucentoClient.Result.Failed || result == null }
                local("$reason, so this was calculated on this phone.")?.copy(retry = retry)
                    ?: AskMessage.Answer(
                        0,
                        plain("$reason. ${if (retry != null) "Try again in a moment" else "Try again later"}, or pick one of the reports below — they work offline."),
                        AnswerSource.PHONE,
                        retry = retry,
                    )
            }
        }
    }

    private fun plain(text: String) = listOf(CheckedAnswer.Part(text, isFigure = false))

    private fun nextId(): Long {
        var id = 0L
        conversation.update { id = it.nextId; it.copy(nextId = it.nextId + 1) }
        return id
    }

    private fun add(message: AskMessage, thinking: Boolean) {
        conversation.update { it.copy(messages = (it.messages + message).takeLast(MAX_MESSAGES), thinking = thinking) }
    }

    companion object {
        const val QUESTION_LIMIT = 500
        private const val AskLucentoClientTurns = 6
        private const val REMOTE_TIMEOUT_MS = 30_000L
        private const val MAX_MONTHS_BACK = 24L
        private const val MAX_MESSAGES = 60

        /**
         * Why Gemini didn't answer. Signed in on this phone yet refused even after a token refresh means the session
         * ended on the server (signed out elsewhere, password changed): say so, rather than "needs your sync account".
         */
        internal fun failureReason(result: AskLucentoClient.Result?, signedIn: Boolean): String = when (result) {
            AskLucentoClient.Result.LimitReached -> "Your AI adviser allowance is used up for now"
            AskLucentoClient.Result.NotSignedIn ->
                if (signedIn) "Your sign-in has expired — sign in again in Settings › Account & sync" else "AI answers need you to sign in"
            null -> "Gemini took too long to answer"
            else -> "Gemini couldn’t be reached"
        }

        val Factory = containerFactory {
            AskLucentoViewModel(
                it.transactions, it.wallets, it.categories, it.budgets, it.recurring, it.goals, it.preferences,
                signedInEmail = runCatching { it.auth.observeSignedInEmail() }.getOrElse { flowOf(null) },
                client = it.askClient,
                pdf = it.reportPdf,
                entitlements = it.entitlements.state,
            )
        }
    }
}
