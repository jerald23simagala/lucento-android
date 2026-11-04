package com.lucento.app.data.ai

import com.lucento.app.BuildConfig
import com.lucento.app.domain.ask.AskSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/**
 * Ask Lucento's AI answers, through Lucento's own proxy (lucento-website `api/ask`), which holds the Gemini key and the
 * per-user daily limit — the APK holds neither. Sent, and only once the user turned AI answers on: the question, the
 * last few turns of the conversation, today's date, the main currency and [AskSnapshot]'s labelled figures (totals and
 * balances; never transactions, notes, payees or receipts). The reply's figures are references the app fills in.
 */
class AskLucentoClient(
    private val accessToken: suspend () -> String?,
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
        val token = accessToken() ?: return@withContext Result.NotSignedIn
        val body = json.encodeToString(AskRequest.serializer(), AskRequest.from(question, history, snapshot))
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = 25_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            connection.outputStream.use { it.write(body.toByteArray()) }
            when (val code = connection.responseCode) {
                200 -> {
                    val reply = json.decodeFromString(AskResponse.serializer(), connection.inputStream.bufferedReader().readText())
                    if (reply.answer.isNullOrBlank()) Result.Failed("empty answer")
                    else Result.Answered(reply.answer, reply.report, reply.month, reply.followUps.orEmpty(), reply.remaining)
                }
                401 -> Result.NotSignedIn
                429 -> Result.LimitReached
                else -> Result.Failed("HTTP $code")
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    }
}

@Serializable
internal data class AskRequest(
    val question: String,
    val today: String,
    val currency: String,
    val history: List<AskTurn>,
    val figures: List<AskFigure>,
) {
    companion object {
        const val MAX_QUESTION = 500
        const val MAX_TURNS = 6
        const val MAX_TURN_TEXT = 1_500

        fun from(question: String, history: List<AskLucentoClient.Turn>, snapshot: AskSnapshot) = AskRequest(
            question = question.take(MAX_QUESTION),
            today = snapshot.today.toString(),
            currency = snapshot.currency,
            history = history.takeLast(MAX_TURNS).map { AskTurn(if (it.fromUser) "user" else "lucento", it.text.take(MAX_TURN_TEXT)) },
            figures = snapshot.figures.map { AskFigure(it.id, it.label, it.value) },
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
