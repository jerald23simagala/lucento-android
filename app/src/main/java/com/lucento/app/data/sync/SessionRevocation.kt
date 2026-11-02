package com.lucento.app.data.sync

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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64
import java.util.concurrent.TimeUnit

/*
 * Server-side sign-out that survives process death, offline sign-outs and expired access tokens (P0, R10-01, N-04).
 *
 * Sign-out records the session it ends ([PendingRevoke]) before the stored session is deleted, on the tap itself
 * ([AuthRepository.beginSignOutNow]). [SessionRevoker] then ends that session on the server — refreshing first when the
 * access token has expired — and the record is removed only once the server confirms, or the session can no longer be
 * used. Until then [RevokeWorker] retries with a network constraint and backoff, even if the app is never reopened.
 * Tokens live only in app-private SharedPreferences (excluded from cloud backup and device transfer, like the session
 * store they come from) and are never logged.
 */

/** One ended session still to be revoked on the server. [sessionId] is the access token's `session_id` claim. */
@Serializable
data class PendingRevoke(
    val sessionId: String,
    val accessToken: String,
    val refreshToken: String,
    val createdAtMillis: Long,
) {
    /** Never prints the tokens, so a stray log line or crash message can't leak them. */
    override fun toString(): String = "PendingRevoke(sessionId=$sessionId, createdAtMillis=$createdAtMillis, tokens=redacted)"
}

/** Durable list of [PendingRevoke]s; every write is synchronous. */
interface PendingRevokeStore {
    fun all(): List<PendingRevoke>
    fun add(record: PendingRevoke): Boolean
    /** Removes the record only if it is still the same one ([PendingRevoke.createdAtMillis] matches). */
    fun remove(record: PendingRevoke): Boolean
}

class PrefsPendingRevokeStore(private val prefs: SharedPreferences) : PendingRevokeStore {
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized override fun all(): List<PendingRevoke> =
        prefs.getString(KEY, null)?.let { runCatching { json.decodeFromString<List<PendingRevoke>>(it) }.getOrNull() }.orEmpty()

    @Synchronized override fun add(record: PendingRevoke): Boolean {
        val list = all().filterNot { it.sessionId == record.sessionId } + record
        return prefs.edit().putString(KEY, json.encodeToString(list)).commit()
    }

    @Synchronized override fun remove(record: PendingRevoke): Boolean {
        val list = all().filterNot { it.sessionId == record.sessionId && it.createdAtMillis == record.createdAtMillis }
        return prefs.edit().apply { if (list.isEmpty()) remove(KEY) else putString(KEY, json.encodeToString(list)) }.commit()
    }

    companion object {
        private const val KEY = "pending_revokes"
        fun forApp(context: Context) = PrefsPendingRevokeStore(context.getSharedPreferences("lucento_pending_revokes", Context.MODE_PRIVATE))
    }
}

/** The `session_id` claim of a Supabase access token, read without verifying it (it only identifies our own session). */
internal fun jwtSessionId(accessToken: String?): String? {
    val payload = accessToken?.split('.')?.getOrNull(1) ?: return null
    return runCatching {
        val text = String(Base64.getUrlDecoder().decode(payload.padEnd((payload.length + 3) / 4 * 4, '=')))
        (Json.parseToJsonElement(text) as JsonObject)["session_id"]?.jsonPrimitive?.content
    }.getOrNull()?.takeIf { it.isNotBlank() }
}

/**
 * True when the stored session (supabase-kt's JSON) is one that a sign-out already recorded for revoking: the app was
 * killed between recording it and deleting it, so it must not be loaded again. A newer sign-in has a new session id.
 */
internal fun endedSessionSurvived(storedSessionJson: String?, pending: List<PendingRevoke>): Boolean {
    if (storedSessionJson == null || pending.isEmpty()) return false
    val access = runCatching { (Json.parseToJsonElement(storedSessionJson) as JsonObject)["access_token"]?.jsonPrimitive?.content }.getOrNull()
    val id = jwtSessionId(access) ?: return false
    return pending.any { it.sessionId == id }
}

/** What the server said; [errorCode] is GoTrue's `error_code` when present. */
sealed interface HttpOutcome {
    data class Status(val code: Int, val errorCode: String? = null) : HttpOutcome
    data object NetworkError : HttpOutcome
}

sealed interface RefreshOutcome {
    data class Token(val accessToken: String) : RefreshOutcome
    data class Failed(val outcome: HttpOutcome) : RefreshOutcome
}

interface RevokeHttp {
    suspend fun logout(accessToken: String): HttpOutcome
    suspend fun refresh(refreshToken: String): RefreshOutcome
}

object SessionRevoker {
    enum class Result { DONE, RETRY }

    /** Records older than this are dropped: the session may outlive them only if the project has no session timebox. */
    const val MAX_AGE_MILLIS = 30L * 24 * 60 * 60 * 1000

    private val GONE = setOf("refresh_token_not_found", "refresh_token_already_used", "session_not_found", "session_expired")

    suspend fun revoke(record: PendingRevoke, http: RevokeHttp, nowMillis: Long): Result {
        if (nowMillis - record.createdAtMillis > MAX_AGE_MILLIS) return Result.DONE
        when (val first = http.logout(record.accessToken)) {
            HttpOutcome.NetworkError -> return Result.RETRY
            is HttpOutcome.Status -> when {
                first.code in 200..299 || first.code == 404 -> return Result.DONE
                first.code == 429 || first.code >= 500 -> return Result.RETRY
                first.errorCode in GONE -> return Result.DONE
                // 401/403/400: the access token expired or is unusable: refresh it and log out with the new one.
            }
        }
        return when (val refreshed = http.refresh(record.refreshToken)) {
            is RefreshOutcome.Token -> when (val second = http.logout(refreshed.accessToken)) {
                HttpOutcome.NetworkError -> Result.RETRY
                is HttpOutcome.Status -> if (second.code in 200..299 || second.code == 404 || second.errorCode in GONE) Result.DONE else Result.RETRY
            }
            is RefreshOutcome.Failed -> when (val f = refreshed.outcome) {
                HttpOutcome.NetworkError -> Result.RETRY
                is HttpOutcome.Status -> when {
                    f.code == 429 || f.code >= 500 -> Result.RETRY
                    // The refresh token is already used, revoked or unknown: the session can't be used any more.
                    f.errorCode in GONE || f.code in 400..499 -> Result.DONE
                    else -> Result.RETRY
                }
            }
        }
    }
}

/** Raw HTTP to Supabase Auth: never through supabase-kt, which would put the session back into the client and save it. */
class SupabaseRevokeHttp(private val supabaseUrl: String, private val publishableKey: String) : RevokeHttp {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun logout(accessToken: String): HttpOutcome =
        call("/auth/v1/logout?scope=local", bearer = accessToken, body = null).first

    override suspend fun refresh(refreshToken: String): RefreshOutcome {
        val (outcome, text) = call(
            "/auth/v1/token?grant_type=refresh_token", bearer = null,
            body = buildString { append("{\"refresh_token\":"); append(Json.encodeToString(refreshToken)); append('}') },
        )
        if (outcome is HttpOutcome.Status && outcome.code in 200..299) {
            val token = runCatching { (json.parseToJsonElement(text.orEmpty()) as JsonObject)["access_token"]?.jsonPrimitive?.content }.getOrNull()
            if (!token.isNullOrBlank()) return RefreshOutcome.Token(token)
        }
        return RefreshOutcome.Failed(outcome)
    }

    private suspend fun call(path: String, bearer: String?, body: String?): Pair<HttpOutcome, String?> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val connection = (java.net.URL(supabaseUrl.trimEnd('/') + path).openConnection() as java.net.HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    setRequestProperty("apikey", publishableKey)
                    setRequestProperty("Content-Type", "application/json")
                    if (bearer != null) setRequestProperty("Authorization", "Bearer $bearer")
                    if (body != null) {
                        doOutput = true
                        outputStream.use { it.write(body.toByteArray()) }
                    }
                }
                val code = connection.responseCode
                val text = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }
                connection.disconnect()
                val errorCode = if (code in 200..299) null else runCatching {
                    (json.parseToJsonElement(text.orEmpty()) as JsonObject)["error_code"]?.jsonPrimitive?.content
                }.getOrNull()
                HttpOutcome.Status(code, errorCode) to text
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                HttpOutcome.NetworkError to null
            }
        }

    private companion object {
        const val TIMEOUT_MS = 10_000
    }
}

/** Drains [store] one record at a time; process-wide so the launch, the worker and a sign-out never race. */
class PendingRevokeDrainer(
    private val store: PendingRevokeStore,
    private val http: () -> RevokeHttp?,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** True when nothing is left to revoke (or nothing can be done in this build). */
    suspend fun drain(): Boolean = LOCK.withLock {
        val client = http() ?: return@withLock true
        var allDone = true
        store.all().forEach { record ->
            when (SessionRevoker.revoke(record, client, now())) {
                SessionRevoker.Result.DONE -> store.remove(record)
                SessionRevoker.Result.RETRY -> allDone = false
            }
        }
        allDone
    }

    private companion object {
        val LOCK = Mutex()
    }
}

/** Retries pending revokes in the background: network constraint, exponential backoff, survives process death. */
class RevokeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as LucentoApplication).container
        return if (container.revokes.drain()) Result.success() else Result.retry()
    }
}

object RevokeScheduler {
    private const val WORK_NAME = "lucento-session-revoke"

    fun schedule(context: Context) {
        val request = OneTimeWorkRequestBuilder<RevokeWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }
}
