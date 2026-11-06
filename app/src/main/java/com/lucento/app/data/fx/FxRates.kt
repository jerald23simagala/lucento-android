package com.lucento.app.data.fx

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lucento.app.core.money.FxRate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.Locale

/** A day's exchange rate: 1 [from] = ? [to] on [date], or null when it can't be had (offline, turned off, unknown). */
fun interface FxRateSource {
    suspend fun rate(from: String, to: String, date: LocalDate): BigDecimal?
}

/** Rates already fetched on this phone (never synced: what a movement was valued at is stored on the movement itself). */
interface FxRateCache {
    suspend fun get(from: String, to: String, date: LocalDate): BigDecimal?
    suspend fun put(from: String, to: String, date: LocalDate, rate: BigDecimal)
}

/**
 * Exchange rates from free services with no key (owner, 2026-11-05), only when the person has turned "Exchange rates
 * online" on — the request names two currencies and a date, never an amount (docs/MULTICURRENCY.md, Privacy):
 * 1. fawazahmed0's currency-api on jsDelivr (daily, 200+ currencies including the Gulf ones, history by date),
 * 2. the same data on its Cloudflare mirror,
 * 3. Frankfurter (the European Central Bank's reference rates; fewer currencies).
 * A future date asks for today's. Each answer is cached by currency pair and day.
 */
class OnlineFxRates(
    private val cache: FxRateCache,
    private val enabled: suspend () -> Boolean,
    private val isOnline: () -> Boolean,
    private val fetch: (String) -> String? = ::httpGet,
    private val today: () -> LocalDate = LocalDate::now,
) : FxRateSource {

    override suspend fun rate(from: String, to: String, date: LocalDate): BigDecimal? {
        if (from == to) return BigDecimal.ONE
        val day = if (date.isAfter(today())) today() else date
        cache.get(from, to, day)?.let { return it }
        if (!enabled() || !isOnline()) return null
        val rate = withContext(Dispatchers.IO) { lookUp(from, to, day) } ?: return null
        cache.put(from, to, day, rate)
        return rate
    }

    private fun lookUp(from: String, to: String, day: LocalDate): BigDecimal? {
        val latest = day == today()
        val base = from.lowercase(Locale.ROOT)
        val tag = if (latest) "latest" else day.toString()
        val currencyApi = listOf(
            "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@$tag/v1/currencies/$base.min.json",
            "https://$tag.currency-api.pages.dev/v1/currencies/$base.min.json",
        )
        currencyApi.forEach { url -> fetch(url)?.let { parseCurrencyApi(it, from, to) }?.let { return it } }
        val frankfurter = "https://api.frankfurter.dev/v1/${if (latest) "latest" else day.toString()}?base=$from&symbols=$to"
        return fetch(frankfurter)?.let { parseFrankfurter(it, to) }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** `{"date":"…","usd":{"php":63.69,…}}` → 63.69, read as an exact decimal from the text (never a double). */
        internal fun parseCurrencyApi(body: String, from: String, to: String): BigDecimal? = runCatching {
            val rates = json.parseToJsonElement(body).jsonObject[from.lowercase(Locale.ROOT)] as? JsonObject
            decimal(rates?.get(to.lowercase(Locale.ROOT)))
        }.getOrNull()

        /** `{"amount":1.0,"base":"USD","date":"…","rates":{"PHP":63.68}}` → 63.68. */
        internal fun parseFrankfurter(body: String, to: String): BigDecimal? = runCatching {
            val rates = json.parseToJsonElement(body).jsonObject["rates"] as? JsonObject
            decimal(rates?.get(to))
        }.getOrNull()

        private fun decimal(element: Any?): BigDecimal? {
            val primitive = element as? JsonPrimitive ?: return null
            if (primitive.isString) return null
            val value = primitive.content.toBigDecimalOrNull() ?: return null
            return value.takeIf { it.signum() > 0 }?.round(FxRate.PRECISION)
        }

        /** One GET over HTTPS (the app allows nothing else), 10 s each way; null on any failure. */
        private fun httpGet(url: String): String? = runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.setRequestProperty("Accept", "application/json")
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
                connection.inputStream.use { stream -> stream.readBytes().takeIf { it.size <= MAX_BODY }?.toString(Charsets.UTF_8) }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()

        private const val MAX_BODY = 512 * 1024
    }
}

private val Context.fxRateStore: DataStore<Preferences> by preferencesDataStore(name = "fx_rates")

/** [FxRateCache] in a small DataStore file: "USD>PHP@2026-11-05" → "63.69377614". Device-only, never backed up. */
class DataStoreFxRateCache(private val context: Context) : FxRateCache {
    override suspend fun get(from: String, to: String, date: LocalDate): BigDecimal? =
        FxRate.parse(context.fxRateStore.data.first()[key(from, to, date)])

    override suspend fun put(from: String, to: String, date: LocalDate, rate: BigDecimal) {
        context.fxRateStore.edit { prefs ->
            prefs[key(from, to, date)] = FxRate.encode(rate)
            // Bounded: the oldest days go once there are more than a year's worth for a handful of currencies.
            val keys = prefs.asMap().keys.map { it.name }.filter { it.contains('@') }
            if (keys.size > MAX_ENTRIES) {
                keys.sortedBy { it.substringAfter('@') }.take(keys.size - MAX_ENTRIES).forEach { prefs.remove(stringPreferencesKey(it)) }
            }
        }
    }

    private fun key(from: String, to: String, date: LocalDate) = stringPreferencesKey("$from>$to@$date")

    private companion object {
        const val MAX_ENTRIES = 2_000
    }
}

/** In memory, for tests and previews. */
class InMemoryFxRateCache : FxRateCache {
    private val map = HashMap<String, BigDecimal>()
    override suspend fun get(from: String, to: String, date: LocalDate): BigDecimal? = map["$from>$to@$date"]
    override suspend fun put(from: String, to: String, date: LocalDate, rate: BigDecimal) {
        map["$from>$to@$date"] = rate
    }
}
