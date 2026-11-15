package com.lucento.app.domain.support

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** One request written on this phone and not yet answered by the server. Holds only what the person typed and chose to attach. */
@Serializable
data class PendingSupportRequest(
    /** Made once, when the request is written: sending it again (a retry) never files a second ticket. */
    val clientRef: String,
    val topic: String,
    val message: String,
    /**
     * Where a reply goes if the request has to go without an account: the address typed while signed out, or the
     * account's own address when it was written signed in.
     */
    val email: String?,
    /** The account signed in when it was written; null when written signed out. */
    val accountId: String?,
    val diagnostics: JsonObject,
    val createdAtMillis: Long,
)

/** A request that is finished on this phone: sent (with its ticket number) or given up. Shown under the form. */
@Serializable
data class SupportHistoryItem(
    val clientRef: String,
    val topic: String,
    val ticket: String?,
    val replyTo: String?,
    val sent: Boolean,
    val atMillis: Long,
)

/** Durable storage for waiting requests, the last few finished ones, and this install's support id. Writes are synchronous. */
interface SupportOutboxStore {
    fun pending(): List<PendingSupportRequest>
    fun add(request: PendingSupportRequest): Boolean
    fun remove(clientRef: String): Boolean
    fun history(): List<SupportHistoryItem>
    fun record(item: SupportHistoryItem): Boolean

    /**
     * A random id for this install, made the first time it's needed. Only the server's per-install limit on signed-out
     * requests uses it. It is never the usage-counts install id, so a request (which can carry an email address) can't be
     * tied to anonymous usage counts.
     */
    fun installId(): String
}

/** What a server answered; [code] null when no answer came (offline, timeout). */
data class RpcAnswer(val code: Int?, val body: String?)

/** Calls one public database function on Lucento's server. [bearer] null sends it without an account. */
fun interface SupportRpc {
    suspend fun call(function: String, body: JsonObject, bearer: String?): RpcAnswer
}

/**
 * Sends support requests (FD-V4, FD-V4b). A request is saved before any network call, so closing the app or losing the
 * connection never loses it; [drain] sends what's waiting when the phone is online again ([scheduleRetry] asks the
 * worker to run). Signed in, a request goes with the account; when the account that wrote it is no longer the one signed
 * in, it goes without an account to the address saved with it, never into someone else's account.
 */
class SupportService(
    private val store: SupportOutboxStore,
    private val rpc: SupportRpc?,
    private val signedInUserId: suspend () -> String?,
    private val accessToken: suspend () -> String?,
    private val scheduleRetry: () -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
    private val newClientRef: () -> String = { UUID.randomUUID().toString() },
) {
    /** False in a build without Lucento's server: the form offers email instead. */
    val available: Boolean get() = rpc != null

    fun pending(): List<PendingSupportRequest> = store.pending()

    fun history(): List<SupportHistoryItem> = store.history()

    /**
     * Writes the request down, then tries to send it once. [Submitted.queued] means it's saved and goes later. A refusal
     * is shown to the person and the request is not kept (they still have their text on screen).
     */
    suspend fun submit(topic: SupportTopic, message: String, email: String?, accountId: String?, diagnostics: JsonObject): Submitted = LOCK.withLock {
        val request = PendingSupportRequest(
            clientRef = newClientRef(),
            topic = topic.key,
            message = message.trim(),
            email = email?.let(SupportRules::normalizeEmail)?.takeIf { it.isNotEmpty() },
            accountId = accountId,
            diagnostics = diagnostics,
            createdAtMillis = now(),
        )
        val saved = store.add(request)
        when (val outcome = send(request)) {
            is SubmitOutcome.Sent -> {
                store.remove(request.clientRef)
                store.record(SupportHistoryItem(request.clientRef, request.topic, outcome.ticket, outcome.replyTo, sent = true, atMillis = now()))
                Submitted(outcome, queued = false)
            }
            is SubmitOutcome.Refused -> {
                store.remove(request.clientRef)
                Submitted(outcome, queued = false)
            }
            SubmitOutcome.TryLater -> if (saved) {
                scheduleRetry()
                Submitted(outcome, queued = true)
            } else {
                // Nothing could be written down: saying "it will be sent" would be untrue.
                Submitted(SubmitOutcome.Refused(Refusal.INVALID), queued = false)
            }
        }
    }

    /** Sends every waiting request, oldest first. True when none is left (the worker's success). */
    suspend fun drain(): Boolean = LOCK.withLock {
        if (rpc == null) return@withLock true
        for (request in store.pending().sortedBy { it.createdAtMillis }) {
            if (now() - request.createdAtMillis > MAX_AGE_MILLIS) {
                store.remove(request.clientRef)
                store.record(SupportHistoryItem(request.clientRef, request.topic, null, null, sent = false, atMillis = now()))
                continue
            }
            when (val outcome = send(request)) {
                is SubmitOutcome.Sent -> {
                    store.remove(request.clientRef)
                    store.record(SupportHistoryItem(request.clientRef, request.topic, outcome.ticket, outcome.replyTo, sent = true, atMillis = now()))
                }
                is SubmitOutcome.Refused -> if (outcome.refusal.finalForQueue) {
                    store.remove(request.clientRef)
                    store.record(SupportHistoryItem(request.clientRef, request.topic, null, null, sent = false, atMillis = now()))
                }
                // A limit (rate_limited, busy) or no answer: it stays and goes on a later run.
                SubmitOutcome.TryLater -> Unit
            }
        }
        store.pending().isEmpty()
    }

    private suspend fun send(request: PendingSupportRequest): SubmitOutcome {
        val client = rpc ?: return SubmitOutcome.TryLater
        return try {
            val current = signedInUserId()
            val bearer = if (request.accountId != null && current == request.accountId) {
                // Its own account is signed in: send with it, or wait for a token rather than send it unlinked.
                accessToken() ?: return SubmitOutcome.TryLater
            } else {
                null
            }
            if (bearer == null && request.email == null) return SubmitOutcome.Refused(Refusal.INVALID_EMAIL)
            val body = SupportRules.requestBody(
                topic = request.topic,
                message = request.message,
                email = if (bearer == null) request.email else null,
                installId = store.installId(),
                clientRef = request.clientRef,
                diagnostics = request.diagnostics,
            )
            val answer = client.call("submit_support_request", body, bearer)
            SupportRules.outcome(answer.code, answer.body)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            SubmitOutcome.TryLater
        }
    }

    data class Submitted(val outcome: SubmitOutcome, val queued: Boolean)

    companion object {
        /** A request that couldn't be sent in a month is given up (shown under the form as not sent). */
        const val MAX_AGE_MILLIS = 30L * 24 * 60 * 60 * 1000

        /** Process-wide: the form and the worker never send at the same moment. */
        private val LOCK = Mutex()
    }
}

/** What the app reports about itself for a signed-in account (FD-V7). */
data class ClientInfo(val appVersion: String, val versionCode: Int, val androidSdk: Int, val deviceModel: String)

/** The last day the app reported for an account (yyyy-MM-dd, in the phone's time zone). */
@Serializable
data class ClientReportMark(val userId: String, val day: String)

interface ClientReportStore {
    fun last(): ClientReportMark?
    fun mark(mark: ClientReportMark): Boolean
}

object ClientReportGate {
    /** At most once a calendar day per account; a new account signing in reports the same day. */
    fun due(last: ClientReportMark?, userId: String, today: String): Boolean =
        last == null || last.userId != userId || last.day != today
}

/**
 * Once a day, for a signed-in account only, tells Lucento's server the app version, Android's API level and the phone
 * model (public.report_client), so support can see which version someone runs. Never while signed out. The day is
 * marked before the call: a failure is silent and tried again the next day.
 */
class ClientReporter(
    private val store: ClientReportStore,
    private val rpc: SupportRpc?,
    private val accessToken: suspend () -> String?,
    private val info: () -> ClientInfo,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    /** True when it sent a report. */
    suspend fun reportIfDue(userId: String?): Boolean {
        if (userId == null || rpc == null) return false
        // atZone().toLocalDate(), not LocalDate.ofInstant: that one needs Android 14 (API 34) and Lucento runs from API 26.
        val today = Instant.ofEpochMilli(now()).atZone(zone()).toLocalDate().toString()
        if (!ClientReportGate.due(store.last(), userId, today)) return false
        val token = try {
            accessToken()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } ?: return false
        if (!store.mark(ClientReportMark(userId, today))) return false
        val i = info()
        try {
            rpc.call(
                "report_client",
                buildJsonObject {
                    put("p_app_version", i.appVersion.take(40))
                    put("p_version_code", i.versionCode)
                    put("p_android_sdk", i.androidSdk)
                    put("p_device_model", i.deviceModel.take(SupportRules.MAX_DETAIL))
                },
                token,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Silent: tomorrow's report tries again.
        }
        return true
    }
}
