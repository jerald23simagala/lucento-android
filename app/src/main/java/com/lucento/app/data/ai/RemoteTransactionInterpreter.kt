package com.lucento.app.data.ai

import com.lucento.app.BuildConfig
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.capture.CandidateFlag
import com.lucento.app.domain.capture.InterpretationContext
import com.lucento.app.domain.capture.TransactionCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/**
 * Reads a typed message with Gemini through Lucento's own proxy (lucento-website `api/interpret`), which holds the
 * Gemini key and a per-user daily limit — the APK holds neither. Sent: the message text, today's date, the main
 * currency, and the user's category and wallet names with their local ids (so rows can be matched back). Nothing else.
 * The caller ([HybridTransactionInterpreter]) only uses this when the user turned Smarter reading on.
 */
class RemoteTransactionInterpreter(
    private val accessToken: suspend () -> String?,
    private val endpoint: String = BuildConfig.AI_INTERPRET_URL,
) {
    sealed interface Result {
        data class Read(val items: List<TransactionCandidate>, val remainingToday: Int?) : Result
        /** The daily allowance for this account is used up; resets tomorrow. */
        data object LimitReached : Result
        /** No sync account is signed in; the proxy needs one to apply the per-user limit. */
        data object NotSignedIn : Result
        data class Failed(val reason: String) : Result
    }

    val isConfigured: Boolean get() = endpoint.isNotBlank()

    suspend fun interpret(text: String, context: InterpretationContext, currency: String): Result = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext Result.Failed("not configured")
        val token = accessToken() ?: return@withContext Result.NotSignedIn
        val body = json.encodeToString(InterpretRequest.serializer(), InterpretRequest.from(text, context, currency))
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            connection.outputStream.use { it.write(body.toByteArray()) }
            when (val code = connection.responseCode) {
                200 -> {
                    val reply = json.decodeFromString(InterpretResponse.serializer(), connection.inputStream.bufferedReader().readText())
                    Result.Read(RemoteCandidates.toCandidates(reply.items, context, text), reply.remaining)
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
internal data class InterpretRequest(
    val text: String,
    val today: String,
    val currency: String,
    val categories: List<NamedRef>,
    val wallets: List<NamedRef>,
    val defaultWalletId: Long? = null,
    val typeHint: String? = null,
) {
    companion object {
        fun from(text: String, context: InterpretationContext, currency: String) = InterpretRequest(
            text = text.take(MAX_TEXT),
            today = context.today.toString(),
            currency = currency,
            categories = context.categories.filter { !it.archived }.map { NamedRef(it.id, it.name, it.kind.name) },
            wallets = context.wallets.map { NamedRef(it.id, it.name, it.type.name, it.currencyCode) },
            defaultWalletId = context.defaultWalletId,
            typeHint = context.typeHint?.name,
        )

        const val MAX_TEXT = 2_000
    }
}

@Serializable
internal data class NamedRef(val id: Long, val name: String, val kind: String, val currency: String? = null)

@Serializable
internal data class InterpretResponse(val items: List<RemoteItem> = emptyList(), val remaining: Int? = null)

/** One row as the model returned it, before validation. */
@Serializable
internal data class RemoteItem(
    val description: String? = null,
    val type: String? = null,
    val amount: String? = null,
    val walletId: Long? = null,
    val toWalletId: Long? = null,
    val categoryId: Long? = null,
    val date: String? = null,
    val currency: String? = null,
)

/**
 * Turns the model's rows into [TransactionCandidate]s, trusting nothing: ids must be the user's own, amounts must parse
 * as positive decimals, types are limited to expense / income / transfer, and anything doubtful becomes a flag for the
 * review row (exactly as the local interpreter flags it). Nothing is saved without the user's review either way.
 */
internal object RemoteCandidates {
    fun toCandidates(items: List<RemoteItem>, context: InterpretationContext, source: String): List<TransactionCandidate> {
        val walletIds = context.wallets.map { it.id }.toSet()
        val categories = context.categories.filter { !it.archived }.associateBy { it.id }
        val written = writtenAmounts(source, context)
        return items.take(MAX_ROWS).mapNotNull { item ->
            val type = when (item.type?.uppercase()) {
                "INCOME" -> TransactionType.INCOME
                "TRANSFER", "TRANSFER_OUT" -> TransactionType.TRANSFER_OUT
                "EXPENSE", null -> TransactionType.EXPENSE
                else -> return@mapNotNull null
            }
            // P0-3: the model's amount is untrusted. Only a plain decimal ("1250.50") is accepted — never "1,250" or
            // "1.250", whose meaning depends on a convention — and it is re-checked against the numbers in the message.
            val parsed = item.amount?.trim()?.takeIf { CANONICAL_AMOUNT.matches(it) }?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
            val match = parsed?.let { p -> written.firstOrNull { t -> t.readings.ifEmpty { listOf(t.value) }.any { it.compareTo(p) == 0 } } }
            // The model picked one reading of a number the device reads two ways ("1.000"): the user picks, as locally.
            val readingChoice = match?.readings?.takeIf { it.size > 1 }
            val amount = if (readingChoice != null) null else parsed
            val wallet = item.walletId?.takeIf { it in walletIds }
            val toWallet = item.toWalletId?.takeIf { it in walletIds && it != wallet }
            if (type == TransactionType.TRANSFER_OUT && toWallet == null) return@mapNotNull null
            // A category only counts when its kind fits the row: no income category on spending.
            val category = item.categoryId?.let(categories::get)?.takeIf { c ->
                type != TransactionType.TRANSFER_OUT && c.kind.name == (if (type == TransactionType.INCOME) "INCOME" else "EXPENSE")
            }
            val date = item.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val flags = buildSet {
                when {
                    readingChoice != null -> add(CandidateFlag.AMOUNT_AMBIGUOUS)
                    amount == null -> add(CandidateFlag.AMOUNT_MISSING)
                    match == null -> add(CandidateFlag.AMOUNT_UNVERIFIED)
                }
                if (category == null && type != TransactionType.TRANSFER_OUT) add(CandidateFlag.CATEGORY_UNMATCHED)
                if (date == null && item.date != null) add(CandidateFlag.DATE_UNCERTAIN)
            }
            TransactionCandidate(
                description = item.description?.trim()?.take(MAX_DESCRIPTION).orEmpty().ifBlank { category?.name ?: "Transaction" },
                type = type,
                amount = amount,
                amountOptions = readingChoice.orEmpty(),
                walletId = wallet ?: context.defaultWalletId,
                toWalletId = toWallet,
                categoryId = category?.id,
                date = date ?: context.today,
                mentionedCurrency = item.currency?.uppercase()?.takeIf { it.length == 3 },
                sourceText = source,
                flags = flags,
                walletFromText = wallet != null,
            )
        }
    }

    /**
     * Every reading of every number in the message, read on the device the way the composer reads it (number words
     * like "two hundred" included), so a model's amount can be checked deterministically.
     */
    private fun writtenAmounts(source: String, context: InterpretationContext): List<com.lucento.app.domain.capture.QuickEntryParser.AmountToken> {
        val text = com.lucento.app.domain.capture.QuickEntryParser.asciiDigits(
            com.lucento.app.domain.capture.NumberWords.normalize(source), context.decimalSeparator,
        )
        val lower = " " + text.lowercase(java.util.Locale.ROOT) + " "
        return com.lucento.app.domain.capture.QuickEntryParser.amountTokens(lower, context.decimalSeparator, context.fractionDigits, context.spaceGrouping)
    }

    private val CANONICAL_AMOUNT = Regex("^\\d{1,13}(\\.\\d{1,3})?$")
    private const val MAX_ROWS = 30
    private const val MAX_DESCRIPTION = 80
}

private fun String.toBigDecimalOrNull(): BigDecimal? = runCatching { BigDecimal(this) }.getOrNull()
