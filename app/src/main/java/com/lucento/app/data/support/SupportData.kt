package com.lucento.app.data.support

import android.content.Context
import android.content.SharedPreferences
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lucento.app.LucentoApplication
import com.lucento.app.domain.support.ClientReportMark
import com.lucento.app.domain.support.ClientReportStore
import com.lucento.app.domain.support.PendingSupportRequest
import com.lucento.app.domain.support.RpcAnswer
import com.lucento.app.domain.support.SupportHistoryItem
import com.lucento.app.domain.support.SupportOutboxStore
import com.lucento.app.domain.support.SupportRpc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/*
 * Contact support's storage, network and background retry (domain/support has the rules). Everything is in app-private
 * SharedPreferences, written synchronously like the session-revoke queue (SessionRevocation.kt): a request written down
 * is there after the app is killed. Android's automatic backup is off for Lucento, so none of it leaves the phone that way.
 */

class PrefsSupportOutboxStore(private val prefs: SharedPreferences) : SupportOutboxStore, ClientReportStore {
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized override fun pending(): List<PendingSupportRequest> =
        prefs.getString(PENDING, null)?.let { runCatching { json.decodeFromString<List<PendingSupportRequest>>(it) }.getOrNull() }.orEmpty()

    @Synchronized override fun add(request: PendingSupportRequest): Boolean {
        val list = (pending().filterNot { it.clientRef == request.clientRef } + request).takeLast(MAX_PENDING)
        return prefs.edit().putString(PENDING, json.encodeToString(list)).commit()
    }

    @Synchronized override fun remove(clientRef: String): Boolean {
        val list = pending().filterNot { it.clientRef == clientRef }
        return prefs.edit().apply { if (list.isEmpty()) remove(PENDING) else putString(PENDING, json.encodeToString(list)) }.commit()
    }

    @Synchronized override fun history(): List<SupportHistoryItem> =
        prefs.getString(HISTORY, null)?.let { runCatching { json.decodeFromString<List<SupportHistoryItem>>(it) }.getOrNull() }.orEmpty()

    @Synchronized override fun record(item: SupportHistoryItem): Boolean {
        val list = (history().filterNot { it.clientRef == item.clientRef } + item).takeLast(MAX_HISTORY)
        return prefs.edit().putString(HISTORY, json.encodeToString(list)).commit()
    }

    /**
     * Signing out: the list of recent requests belongs to the account leaving, so the next person on this phone doesn't
     * see it. Waiting requests stay: they go as promised, unlinked from whoever signs in next ([SupportService.drain]).
     */
    @Synchronized fun forgetHistory(): Boolean = prefs.edit().remove(HISTORY).commit()

    @Synchronized override fun installId(): String =
        prefs.getString(INSTALL_ID, null) ?: UUID.randomUUID().toString().also { prefs.edit().putString(INSTALL_ID, it).commit() }

    @Synchronized override fun last(): ClientReportMark? =
        prefs.getString(CLIENT_REPORT, null)?.let { runCatching { json.decodeFromString<ClientReportMark>(it) }.getOrNull() }

    @Synchronized override fun mark(mark: ClientReportMark): Boolean =
        prefs.edit().putString(CLIENT_REPORT, json.encodeToString(mark)).commit()

    companion object {
        private const val PENDING = "pending_requests"
        private const val HISTORY = "finished_requests"
        private const val INSTALL_ID = "support_install_id"
        private const val CLIENT_REPORT = "client_report"
        /** More waiting than this is unlikely; the oldest go first so the store can't grow without bound. */
        private const val MAX_PENDING = 10
        private const val MAX_HISTORY = 5

        fun forApp(context: Context) = PrefsSupportOutboxStore(context.getSharedPreferences("lucento_support", Context.MODE_PRIVATE))
    }
}

/**
 * POST <Supabase>/rest/v1/rpc/<function>, with the publishable key and, for a signed-in request, the account's token.
 * Without a token the request is anonymous (the database's anon role). Bodies are never logged.
 */
class SupabaseSupportRpc(private val supabaseUrl: String, private val publishableKey: String) : SupportRpc {
    override suspend fun call(function: String, body: JsonObject, bearer: String?): RpcAnswer = withContext(Dispatchers.IO) {
        try {
            val connection = (java.net.URL("${supabaseUrl.trimEnd('/')}/rest/v1/rpc/$function").openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                setRequestProperty("apikey", publishableKey)
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                if (bearer != null) setRequestProperty("Authorization", "Bearer $bearer")
            }
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }
            connection.disconnect()
            RpcAnswer(code, text)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            RpcAnswer(null, null)
        }
    }

    private companion object {
        const val TIMEOUT_MS = 15_000
    }
}

/** Sends waiting support requests in the background: network constraint, exponential backoff, survives process death. */
class SupportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as LucentoApplication).container
        return if (container.support.drain()) Result.success() else Result.retry()
    }
}

object SupportScheduler {
    private const val WORK_NAME = "lucento-support-outbox"

    fun schedule(context: Context) {
        val request = OneTimeWorkRequestBuilder<SupportWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }
}
