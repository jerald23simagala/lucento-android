package com.lucento.app.data.ai

import com.lucento.app.BuildConfig
import com.lucento.app.domain.ask.AskSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Ask Lucento's AI answers, through Lucento's own proxy (lucento-website `api/ask`), which holds the Gemini key and the
 * per-user daily limit — the APK holds neither. Sent, and only once the user turned AI answers on (the v2 consent): the
 * question, the last few turns of the conversation, today's date, the main currency and [AskSnapshot]'s labelled
 * figures (summaries, projections, goals, transaction patterns and the last 60 days of transactions; never receipts or
 * photos). The reply's figures and calculations are references the app fills in.
 */
class AskLucentoClient(
    /** The session's token; `refresh = true` after the proxy refused one ([postToProxy]). */
    private val accessToken: suspend (refresh: Boolean) -> String?,
    private val endpoint: String = BuildConfig.AI_ASK_URL,
) {
    sealed interface Result {
        data class Answered(val answer: String, val report: String?, val month: String?, val followUps: List<String>, val remainingToday: Int?) : Result
        data object LimitReached : Result
        data object NotSignedIn : Result
        data class Failed(val reason: String) : Result
    }

    /** A turn of the conversation as sent: who said it and the text (an answer's figures already filled in). */
    data class Turn(val fromUser: Boolean, val text: String)

    val isConfigured: Boolean get() = endpoint.isNotBlank()

    suspend fun ask(question: String, history: List<Turn>, snapshot: AskSnapshot): Result = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext Result.Failed("not configured")
        val reply = postToProxy(endpoint, body(AskRequest.from(question, history, snapshot)), READ_TIMEOUT_MS, accessToken)
            ?: return@withContext Result.NotSignedIn
        when (reply.code) {
            200 -> {
                val answer = json.decodeFromString(AskResponse.serializer(), reply.body.orEmpty())
                if (answer.answer.isNullOrBlank()) Result.Failed("empty answer")
                else Result.Answered(answer.answer, answer.report, answer.month, answer.followUps.orEmpty(), answer.remaining)
            }
            401 -> Result.NotSignedIn
            429 -> Result.LimitReached
            else -> Result.Failed("HTTP ${reply.code}")
        }
    }

    internal companion object {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

        /** The proxy stops calling Gemini by about 22 s (lucento-website `server/gemini.ts`); this waits a little longer. */
        const val READ_TIMEOUT_MS = 25_000

        /** The request body exactly as sent. */
        fun body(request: AskRequest): String = json.encodeToString(AskRequest.serializer(), request)
    }
}

@Serializable
internal data class AskRequest(
    val question: String,
    val today: String,
    val currency: String,
    val history: List<AskTurn>,
    val figures: List<AskFigure>,
    /**
     * 2: the app works out `[[= … ]]` calculations and sends transaction-level figures (the proxy's adviser prompt).
     * 3 (owner round 10): its calculator also takes amounts with a currency code, percentages and powers, so the proxy's
     * adviser may project with the user's own amounts and assumed rates. No default value: the encoder leaves defaults
     * out, and a missing version reads as 1 on the proxy.
     */
    val version: Int,
) {
    companion object {
        const val MAX_QUESTION = 500
        const val MAX_TURNS = 6
        const val MAX_TURN_TEXT = 1_500
        const val VERSION = 3

        fun from(question: String, history: List<AskLucentoClient.Turn>, snapshot: AskSnapshot) = AskRequest(
            question = question.take(MAX_QUESTION),
            today = snapshot.today.toString(),
            currency = snapshot.currency,
            history = history.takeLast(MAX_TURNS).map { AskTurn(if (it.fromUser) "user" else "lucento", it.text.take(MAX_TURN_TEXT)) },
            figures = snapshot.figures.map { AskFigure(it.id, it.label, it.value) },
            version = VERSION,
        )
    }
}

@Serializable
internal data class AskTurn(val role: String, val text: String)

@Serializable
internal data class AskFigure(val id: String, val label: String, val value: String)

@Serializable
internal data class AskResponse(
    val answer: String? = null,
    val report: String? = null,
    val month: String? = null,
    val followUps: List<String>? = null,
    val remaining: Int? = null,
)
