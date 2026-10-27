package com.lucento.app.data.ai

import com.lucento.app.domain.capture.Interpretation
import com.lucento.app.domain.capture.InterpretationContext
import com.lucento.app.domain.capture.InterpreterEngine
import com.lucento.app.domain.capture.TransactionInterpreter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The composer's interpreter: Gemini (through [remote]) when the user turned Smarter reading on, otherwise — and
 * whenever Gemini can't answer (offline, signed out, today's limit used, an error) — [local], on this phone. Each
 * result names who read it, so local rules are never presented as AI. Receipts are always read on this phone.
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
                if (result.items.isNotEmpty()) return Interpretation.Candidates(result.items, GEMINI) else "Gemini found nothing"
            RemoteTransactionInterpreter.Result.LimitReached -> "today’s Gemini limit is used up"
            RemoteTransactionInterpreter.Result.NotSignedIn -> "Gemini needs your sync account"
            is RemoteTransactionInterpreter.Result.Failed, null -> "Gemini couldn’t be reached"
        }
        return when (val fallback = local.interpretText(text, context)) {
            is Interpretation.Candidates -> fallback.copy(engine = local.engine.copy(label = "${local.engine.label} · $fallbackReason"))
            is Interpretation.NothingFound -> fallback
        }
    }

    override suspend fun interpretReceipt(lines: List<String>, context: InterpretationContext): Interpretation =
        local.interpretReceipt(lines, context)

    companion object {
        val GEMINI = InterpreterEngine(label = "Gemini", onDevice = false)
        private const val REMOTE_TIMEOUT_MS = 20_000L
    }
}
