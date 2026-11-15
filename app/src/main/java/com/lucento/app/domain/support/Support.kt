package com.lucento.app.domain.support

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.time.Instant

/*
 * Settings › Help › Contact support (docs/SUPPORT.md; Mission Control FD-V4). A request goes to Lucento's server as
 * public.submit_support_request and becomes a ticket the owner answers by email. Pure rules, unit-tested; the network
 * and the outbox live in data/support.
 */

/** What a request is about. [key] is the server's value (submit_support_request's topics). */
enum class SupportTopic(val key: String, val label: String) {
    ACCOUNT("account", "Account and sign-in"),
    BILLING("billing", "Lucento Pro and payments"),
    SYNC("sync", "Cloud sync"),
    DATA_RECOVERY("data_recovery", "Missing or lost records"),
    BUG("bug", "Something isn’t working"),
    IDEA("idea", "An idea or a request"),
    OTHER("other", "Something else"),
    ;

    companion object {
        fun fromKey(key: String?): SupportTopic? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Everything the app may attach to a request, exactly as the person sees it before sending. Nothing from their records:
 * no names, amounts or notes.
 */
data class PhoneDetails(
    val appVersion: String,
    val versionCode: Int,
    val androidRelease: String,
    val androidSdk: Int,
    val manufacturer: String,
    val model: String,
    val syncEnabled: Boolean,
    val lastSyncAtMillis: Long?,
    /** The plan as the app shows it ("Free plan", "Lucento Pro"). */
    val plan: String,
    val signedIn: Boolean,
    val locale: String,
)

/** Why a form can't be sent yet. */
enum class FormProblem(val message: String) {
    NO_TOPIC("Choose what your request is about."),
    MESSAGE_EMPTY("Write a few words about what’s happening."),
    MESSAGE_TOO_LONG("That’s more than 5,000 characters. Shorten it a little, or email us instead."),
    EMAIL_INVALID("Enter the email address we should reply to, like name@example.com."),
}

/** What the server said no to. [finalForQueue]: a waiting request that gets this answer is given up, not retried. */
enum class Refusal(val message: String, val finalForQueue: Boolean) {
    INVALID_EMAIL("That email address doesn’t look right. Check it and send again.", true),
    MESSAGE_EMPTY("Write a few words about what’s happening.", true),
    MESSAGE_TOO_LONG("That’s more than 5,000 characters. Shorten it a little, or email us instead.", true),
    INVALID("Lucento couldn’t send this request. Email ${SupportRules.SUPPORT_EMAIL} instead.", true),
    RATE_LIMITED("You’ve sent several requests today. To add to them, email ${SupportRules.SUPPORT_EMAIL}.", false),
    BUSY("Support is getting a lot of requests right now. Try again in an hour, or email ${SupportRules.SUPPORT_EMAIL}.", false),
}

/** What became of one attempt to send a request. */
sealed interface SubmitOutcome {
    /** A ticket exists ([ticket] like LC-1042); replies go to [replyTo]. */
    data class Sent(val ticket: String, val replyTo: String?) : SubmitOutcome
    data class Refused(val refusal: Refusal) : SubmitOutcome
    /** Offline, the server didn't answer, or its answer said "later": nothing is lost, it goes again. */
    data object TryLater : SubmitOutcome
}

object SupportRules {
    const val MAX_MESSAGE = 5000
    const val MIN_MESSAGE = 2
    const val MAX_EMAIL = 254
    /** The server keeps attached text values up to this length (mc.clean_diagnostics). */
    const val MAX_DETAIL = 80
    const val SUPPORT_EMAIL = "lucentoapp@gmail.com"

    // The same shape the server accepts (submit_support_request): something@something.tld, no spaces.
    private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    fun normalizeEmail(raw: String): String = raw.trim().lowercase()

    fun isValidEmail(raw: String): Boolean {
        val email = normalizeEmail(raw)
        return email.length <= MAX_EMAIL && EMAIL.matches(email)
    }

    /** The first reason this form can't go, or null. An email is asked for only while signed out. */
    fun validate(topic: SupportTopic?, message: String, email: String, signedIn: Boolean): FormProblem? = when {
        topic == null -> FormProblem.NO_TOPIC
        message.trim().length < MIN_MESSAGE -> FormProblem.MESSAGE_EMPTY
        message.trim().length > MAX_MESSAGE -> FormProblem.MESSAGE_TOO_LONG
        !signedIn && !isValidEmail(email) -> FormProblem.EMAIL_INVALID
        else -> null
    }

    /**
     * The details sent with a request: only the keys the server keeps, as it types them, and nothing at all when the
     * person turned "Attach details" off. Text is cut to [MAX_DETAIL] characters so the server never drops a value.
     */
    fun diagnostics(details: PhoneDetails?, include: Boolean): JsonObject {
        if (!include || details == null) return JsonObject(emptyMap())
        fun text(value: String): String = value.trim().take(MAX_DETAIL)
        return buildJsonObject {
            put("app_version", text(details.appVersion))
            put("version_code", details.versionCode)
            put("android_release", text(details.androidRelease))
            put("android_sdk", details.androidSdk)
            put("device_manufacturer", text(details.manufacturer))
            put("device_model", text(details.model))
            put("sync_enabled", details.syncEnabled)
            details.lastSyncAtMillis?.let { put("last_sync_at", Instant.ofEpochMilli(it).toString()) }
            put("plan", text(details.plan))
            put("signed_in", details.signedIn)
            put("locale", text(details.locale))
        }
    }

    /** "Google Pixel 8", without repeating the maker when the model already starts with it ("SM-S921B" stays as is). */
    fun phoneName(manufacturer: String, model: String): String {
        val maker = manufacturer.trim().replaceFirstChar { it.titlecase() }
        val name = model.trim()
        return when {
            name.isEmpty() -> maker
            maker.isEmpty() || name.startsWith(maker, ignoreCase = true) -> name
            else -> "$maker $name"
        }.take(MAX_DETAIL)
    }

    /** What the person sees will be attached, line by line, in the words of the form's preview. */
    fun preview(details: PhoneDetails, nowMillis: Long): List<Pair<String, String>> = listOf(
        "App version" to "${details.appVersion} (${details.versionCode})",
        "Android" to "${details.androidRelease} (API ${details.androidSdk})",
        "Phone" to phoneName(details.manufacturer, details.model),
        "Cloud sync" to if (details.syncEnabled) "On" else "Off",
        "Last sync" to (details.lastSyncAtMillis?.let { ago(it, nowMillis) } ?: "Never"),
        "Plan shown" to details.plan,
        "Signed in" to if (details.signedIn) "Yes" else "No",
        "Language" to details.locale,
    )

    fun ago(thenMillis: Long, nowMillis: Long): String {
        val minutes = ((nowMillis - thenMillis).coerceAtLeast(0)) / 60_000
        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "$minutes min ago"
            minutes < 48 * 60 -> "${minutes / 60} h ago"
            else -> "${minutes / (24 * 60)} days ago"
        }
    }

    /** The JSON body of public.submit_support_request for one request. */
    fun requestBody(topic: String, message: String, email: String?, installId: String, clientRef: String, diagnostics: JsonObject): JsonObject =
        buildJsonObject {
            put("p_topic", topic)
            put("p_message", message.trim())
            put("p_email", email)
            put("p_install_id", installId)
            put("p_client_ref", clientRef)
            put("p_diagnostics", diagnostics)
        }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * What one HTTP answer means ([code] null: the request never got an answer). 200 carries the function's own verdict;
     * 401/403 (a token that expired on the way), 404 (the function isn't on this server yet), 408, 429 and 5xx mean
     * "later"; any other 4xx is a request the server will never take.
     */
    fun outcome(code: Int?, body: String?): SubmitOutcome {
        if (code == null) return SubmitOutcome.TryLater
        if (code == 200) {
            val answer = runCatching { json.parseToJsonElement(body.orEmpty()).jsonObject }.getOrNull() ?: return SubmitOutcome.TryLater
            if ((answer["ok"] as? JsonPrimitive)?.booleanOrNull == true) {
                val ticket = (answer["ticket"] as? JsonPrimitive)?.contentOrNull ?: return SubmitOutcome.TryLater
                return SubmitOutcome.Sent(ticket, (answer["reply_to"] as? JsonPrimitive)?.contentOrNull)
            }
            return when ((answer["error"] as? JsonPrimitive)?.contentOrNull) {
                "rate_limited" -> SubmitOutcome.Refused(Refusal.RATE_LIMITED)
                "busy" -> SubmitOutcome.Refused(Refusal.BUSY)
                "invalid_email" -> SubmitOutcome.Refused(Refusal.INVALID_EMAIL)
                "message_empty" -> SubmitOutcome.Refused(Refusal.MESSAGE_EMPTY)
                "message_too_long" -> SubmitOutcome.Refused(Refusal.MESSAGE_TOO_LONG)
                else -> SubmitOutcome.Refused(Refusal.INVALID)
            }
        }
        return when {
            code == 401 || code == 403 || code == 404 || code == 408 || code == 429 || code >= 500 -> SubmitOutcome.TryLater
            code in 400..499 -> SubmitOutcome.Refused(Refusal.INVALID)
            else -> SubmitOutcome.TryLater
        }
    }
}
