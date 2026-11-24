package com.lucento.app.data.ai

import com.lucento.app.domain.capture.CaptureLimits
import com.lucento.app.domain.capture.Interpretation
import com.lucento.app.domain.capture.InterpretationContext
import com.lucento.app.domain.capture.InterpreterEngine
import com.lucento.app.domain.capture.TransactionInterpreter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The composer's interpreter: Gemini (through [remote]) when the user turned Smarter reading on, otherwise — and
 * whenever Gemini can't answer (offline, signed out, this month's AI questions used, an error) — [local], on this phone.
 * Each result names who read it, so local rules are never presented as AI, and a Gemini reply says how many of the AI
 * questions it shares with Ask Lucento are left (owner, 2026-11-23). Scans (receipts and statements) are always read
 * on this phone.
 */
class HybridTransactionInterpreter(
    private val local: TransactionInterpreter,
    private val remote: RemoteTransactionInterpreter,
    /** True only while the user's Smarter reading consent is on. */
    private val enabled: suspend () -> Boolean,
    private val currency: suspend () -> String,
) : TransactionInterpreter {
    override val engine: InterpreterEngine get() = local.engine

    override suspend fun interpretText(text: String, context: InterpretationContext): Interpretation {
        if (!enabled() || !remote.isConfigured) return local.interpretText(text, context)
        val result = try {
            withTimeoutOrNull(REMOTE_TIMEOUT_MS) { remote.interpret(text, context, currency()) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RemoteTransactionInterpreter.Result.Failed(e.javaClass.simpleName)
        }
        val fallbackReason = when (result) {
            is RemoteTransactionInterpreter.Result.Read ->
                if (result.items.isNotEmpty()) {
                    return Interpretation.Candidates(result.items, gemini(result.remaining), CaptureLimits.note(result.leftOut, result.inputCut))
                } else {
                    "Gemini found nothing"
                }
            RemoteTransactionInterpreter.Result.LimitReached -> "this month’s AI questions are used up"
            RemoteTransactionInterpreter.Result.ProRequired -> "Smarter reading is part of Lucento Pro"
            RemoteTransactionInterpreter.Result.NotSignedIn -> "Gemini needs your sync account"
            // A slow answer and an unreachable server need different fixes, so they read differently.
            null -> "Gemini took too long to answer"
            is RemoteTransactionInterpreter.Result.Failed ->
                if (result.reason == "SocketTimeoutException") "Gemini took too long to answer" else "Gemini couldn’t be reached"
        }
        return when (val fallback = local.interpretText(text, context)) {
            is Interpretation.Candidates -> fallback.copy(engine = local.engine.copy(label = "${local.engine.label} · $fallbackReason"))
            is Interpretation.NothingFound -> fallback
        }
    }

    override suspend fun interpretScan(lines: List<String>, context: InterpretationContext): Interpretation =
        local.interpretScan(lines, context)

    override suspend fun interpretDocument(document: com.lucento.app.domain.capture.ScanDocument, context: InterpretationContext): Interpretation =
        local.interpretDocument(document, context)

    companion object {
        val GEMINI = InterpreterEngine(label = "Gemini", onDevice = false)
        private const val REMOTE_TIMEOUT_MS = 20_000L

        /**
         * Who read a message, with what is left of the AI questions Smarter reading shares with Ask Lucento, as the server
         * counted them: shown as "Read by Gemini · 12 AI questions left this month". Just "Gemini" from an older proxy.
         */
        internal fun gemini(remaining: Int?): InterpreterEngine =
            if (remaining == null) GEMINI
            else GEMINI.copy(label = "${GEMINI.label} · $remaining AI ${if (remaining == 1) "question" else "questions"} left this month")
    }
}
