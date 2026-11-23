package com.lucento.app.domain.settings

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull
import java.time.YearMonth
import java.time.format.DateTimeParseException

/**
 * The settings and customisations that follow a person's account (docs/SYNC.md "Settings"). Cloud sync keeps them in one
 * row per account (`public.user_settings`) and merges them one setting at a time, so a restore on a new phone brings
 * them back and a change on one phone reaches the others. The JSON backup carries the same choices (docs/BACKUP.md).
 *
 * Two kinds of preference stay on each phone and are never here:
 * - consents to send something off the phone, and their choices: Smarter reading, online voice, crash reports, usage
 *   counts, and Ask Lucento's AI answers (on or off, and the agreement to what they send). Each phone asks for those
 *   itself, so neither a restore nor another phone ever switches sharing on or off here;
 * - security choices that rely on this phone's own lock: app lock, hiding Lucento in recent apps, hidden amounts
 *   (showing them again asks for this phone's fingerprint or screen lock, which another phone must never bypass).
 *
 * The main currency is not here either: it is the ledger's, set once on the server (`ledger_settings`).
 *
 * [ledger] settings point into the records on the phone rather than describing the person: a Partner's phone, which holds
 * an Owner's Shared Space, neither sends nor takes them.
 */
enum class AccountSetting(val key: String, val ledger: Boolean = false) {
    THEME_MODE("theme_mode"),
    THEME("theme"),
    FEATURES("features"),
    DISPLAY_NAME("display_name"),
    SHOW_CENTS("show_cents"),
    WALLETS_VIEW("wallets_view"),
    MODULE_OFFERS_HIDDEN("module_offers_hidden"),
    FX_RATES_ONLINE("fx_rates_online"),
    DEFAULT_WALLET("default_wallet", ledger = true),
    BUDGET_MONTH_SEEN("budget_month_seen", ledger = true),
    ;

    /**
     * Whether [value] is a well-formed value of this setting, from this version of Lucento or a newer one: a theme this
     * build doesn't know is well formed, and whether it can be used is the app's call. [JsonNull] always is: no value of
     * its own, so the app's default applies.
     */
    fun accepts(value: JsonElement): Boolean {
        if (value is JsonNull) return true
        return when (this) {
            THEME_MODE -> value.text() in THEME_MODES
            THEME, WALLETS_VIEW -> value.text()?.matches(KEY) == true
            FEATURES -> (value as? JsonArray)?.let { list ->
                list.size <= MAX_FEATURES && list.all { it.text()?.matches(KEY) == true } && list.toSet().size == list.size
            } == true
            DISPLAY_NAME -> value.text()?.let { it.isNotBlank() && it.length <= MAX_NAME } == true
            SHOW_CENTS, MODULE_OFFERS_HIDDEN, FX_RATES_ONLINE ->
                (value as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull != null
            DEFAULT_WALLET -> value.text()?.let { it.isNotEmpty() && it.length <= MAX_ID } == true
            BUDGET_MONTH_SEEN -> value.text()?.let { isMonth(it) } == true
        }
    }

    companion object {
        fun fromKey(key: String): AccountSetting? = entries.firstOrNull { it.key == key }

        /** What a Partner's phone in someone else's Shared Space may send and take: everything about the person. */
        val PERSONAL: Set<AccountSetting> = entries.filterNot { it.ledger }.toSet()

        const val MAX_FEATURES = 32
        const val MAX_NAME = 40
        const val MAX_ID = 128
        private val KEY = Regex("^[a-z0-9_]{1,32}$")
        private val MONTH = Regex("^[0-9]{4}-[0-9]{2}$")
        private val THEME_MODES = setOf("SYSTEM", "LIGHT", "DARK")

        private fun isMonth(text: String): Boolean = MONTH.matches(text) && try {
            YearMonth.parse(text)
            true
        } catch (_: DateTimeParseException) {
            false
        }
    }
}

/** A string value's text, or null for anything else. */
private fun JsonElement.text(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

/**
 * One setting in the account's document: its value, and when it was chosen ([at], see [AccountSettingsMerge.changedAt]).
 * [at] 0 marks a value a phone already had before settings synced (filled in where the account had none).
 */
data class SettingEntry(val value: JsonElement, val at: Long)

/** What this phone holds for one setting. */
data class LocalSetting(
    /** The value as the account's document writes it; null when this phone has none of its own (the default applies). */
    val value: JsonElement?,
    /**
     * When this phone's value was chosen, or the [SettingEntry.at] of the account's entry it was taken from or sent as.
     * Null when the setting hasn't been through sync: a fresh install, or a choice made before settings synced.
     */
    val at: Long?,
    /** Changed on this phone since the account last accepted it. */
    val dirty: Boolean,
    /**
     * Left on the phone by an account that signed out, and not changed since: shown, but never sent to another account,
     * and replaced by whatever the next account has.
     */
    val inherited: Boolean = false,
)

/**
 * The account's settings document: `{"theme": {"v": "grove", "at": 1763800000000}, …}`, one entry per setting. An entry
 * this build can't read (a newer version's setting, or a damaged one) is carried through untouched, so no phone ever
 * erases what a newer phone wrote.
 */
object AccountSettingsDocument {
    const val VALUE = "v"
    const val AT = "at"

    /** [setting]'s entry in [document], or null when it has none with a readable time. Its value may still be unusable. */
    fun entry(document: JsonObject, setting: AccountSetting): SettingEntry? {
        val raw = document[setting.key] as? JsonObject ?: return null
        val at = (raw[AT] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull?.takeIf { it >= 0 } ?: return null
        return SettingEntry(raw[VALUE] ?: JsonNull, at)
    }

    fun encode(entry: SettingEntry): JsonObject = JsonObject(mapOf(VALUE to entry.value, AT to JsonPrimitive(entry.at)))
}

/**
 * How one phone's settings and the account's document come together (docs/SYNC.md "Settings"). Each setting is merged on
 * its own: two phones that changed different settings both keep their change. When the same setting changed on both
 * before either synced, the later change wins. A change made after seeing another phone's always wins over it, whatever
 * the clocks say ([changedAt]).
 */
object AccountSettingsMerge {

    /**
     * When a change made on this phone is dated: by the clock, but always after the value it replaces. A change made after
     * taking another phone's value therefore beats it even when this phone's clock runs behind.
     */
    fun changedAt(previous: Long?, now: Long): Long {
        val after = previous ?: 0L
        return maxOf(now, if (after == Long.MAX_VALUE) after else after + 1)
    }

    sealed interface Decision {
        /** Nothing to change here: this phone already holds the account's value, or keeps its own (see [decide]). */
        data object Keep : Decision

        /** The account's entry wins. [replacesLocalChange]: a different change made here, not yet synced, is replaced. */
        data class Take(val entry: SettingEntry, val replacesLocalChange: Boolean) : Decision
    }

    /**
     * One setting found in the account's document.
     *
     * [joining]: this phone is joining the account — its first sync with it since it was installed or signed out (a
     * restore, a new phone, or a phone set up on its own that now connects). The account's settings are used, even over
     * changes made here; settings the account has no entry for go up from this phone ([upload]).
     *
     * Otherwise, for a phone that already synced with the account:
     * - a value it had before settings synced is kept against another phone's value from before then (dated 0), so
     *   updating Lucento changes nothing on a phone; a change made since (dated later) replaces it;
     * - a change waiting here stays when it is at least as new as the account's (a tie keeps it), and gives way when the
     *   account's is newer;
     * - with nothing waiting, it takes what the account has.
     *
     * An [LocalSetting.inherited] value (another account's leftover) always gives way to the account's.
     */
    fun decide(local: LocalSetting, server: SettingEntry, joining: Boolean): Decision {
        val same = (local.value ?: JsonNull) == server.value
        return when {
            joining -> if (!local.dirty && same && local.at == server.at) Decision.Keep else Decision.Take(server, replacesLocalChange = false)
            server.at == 0L && local.at == null && !local.dirty && !local.inherited -> Decision.Keep
            local.dirty -> if (server.at > (local.at ?: 0L)) Decision.Take(server, replacesLocalChange = !same) else Decision.Keep
            same && local.at == server.at -> Decision.Keep
            else -> Decision.Take(server, replacesLocalChange = false)
        }
    }

    /** What goes up after the account's entries were taken: the whole document, with this phone's part written in. */
    data class Upload(
        val document: JsonObject,
        /** Changes made here that it carries, each with the time it was sent with. */
        val sent: Map<AccountSetting, Long>,
        /** Settings the account had no entry for, filled from this phone's values; they count as synced once accepted. */
        val seeded: Map<AccountSetting, Long>,
    )

    /**
     * [server]: the account's document as read (null: it has none yet). [local]: the settings this phone may send, read
     * after the account's entries were taken. Returns null when the account already has everything this phone would send.
     * A setting with no value of its own here is never seeded (the default isn't a choice), nor is an inherited one; an
     * entry this build can't read is only ever replaced by a change made here.
     */
    fun upload(server: JsonObject?, local: Map<AccountSetting, LocalSetting>): Upload? {
        val document = LinkedHashMap<String, JsonElement>(server ?: emptyMap())
        val sent = LinkedHashMap<AccountSetting, Long>()
        val seeded = LinkedHashMap<AccountSetting, Long>()
        for ((setting, here) in local) {
            when {
                here.dirty -> {
                    val at = here.at ?: 0L
                    document[setting.key] = AccountSettingsDocument.encode(SettingEntry(here.value ?: JsonNull, at))
                    sent[setting] = at
                }
                here.value != null && !here.inherited && server?.containsKey(setting.key) != true -> {
                    val at = here.at ?: 0L
                    document[setting.key] = AccountSettingsDocument.encode(SettingEntry(here.value, at))
                    seeded[setting] = at
                }
            }
        }
        if (sent.isEmpty() && seeded.isEmpty()) return null
        return Upload(JsonObject(document.toSortedMap()), sent, seeded)
    }

    /** What one merge did on this phone, and what it would send. */
    data class Result(
        val upload: Upload?,
        /** Settings whose stored value changed here to the account's. */
        val taken: Int,
        /** Of those, changes made here that a newer change from another phone replaced (P2-6, told to the person). */
        val replaced: Int,
    )
}
