package com.lucento.app.domain.backup

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * One step from schema [fromVersion] to the next. Steps run in order until [BackupMigrations.CURRENT].
 * A new backup version adds a step here; older files keep importing.
 */
fun interface BackupMigrationStep {
    fun migrate(document: JsonValue.Obj): JsonValue.Obj
}

object BackupMigrations {
    const val CURRENT = 1

    private val steps: Map<Int, BackupMigrationStep> = mapOf(
        0 to Migrate0To1,
    )

    fun toCurrent(document: JsonValue.Obj): JsonValue.Obj {
        var current = document
        var version = intField(current, "schemaVersion")
        while (version < CURRENT) {
            val step = steps[version]
                ?: throw BackupFormatException(
                    BackupFailure.UNSUPPORTED_VERSION,
                    "This backup can’t be read (version $version). Nothing was changed.",
                )
            current = step.migrate(current)
            val next = intField(current, "schemaVersion")
            if (next != version + 1) {
                throw BackupFormatException(
                    BackupFailure.INVALID,
                    "This file isn’t a valid Lucento backup. Nothing was changed.",
                )
            }
            version = next
        }
        return current
    }
}

/**
 * Version 0 named wallets `accounts` and stored dates as numbers (epoch milliseconds for instants,
 * epoch days for calendar dates). Version 1 uses `wallets` and ISO-8601 strings, and adds scheduled
 * payments, debt terms, deletions and settings.
 */
private object Migrate0To1 : BackupMigrationStep {
    private val rootKeys = setOf(
        "schemaVersion", "appVersion", "exportedAt", "accounts",
        "categories", "transactions", "budgets", "goals", "goalContributions",
    )

    override fun migrate(document: JsonValue.Obj): JsonValue.Obj {
        rejectUnknown(document, rootKeys)
        if (document.fields.containsKey("wallets")) {
            throw BackupFormatException(BackupFailure.INVALID, "This file isn’t a valid Lucento backup. Nothing was changed.")
        }
        val accounts = arrayField(document, "accounts").map { convertWallet(it.obj()) }
        val fields = LinkedHashMap<String, JsonValue>()
        fields["schemaVersion"] = JsonValue.Num("1")
        fields["appVersion"] = document.fields["appVersion"] ?: JsonValue.Str("0")
        fields["exportedAt"] = asInstant(document.fields["exportedAt"], "exportedAt")
        fields["wallets"] = JsonValue.Arr(accounts)
        fields["categories"] = convertRows(arrayField(document, "categories"), ::convertCategory)
        fields["transactions"] = convertRows(arrayField(document, "transactions"), ::convertTransaction)
        fields["budgets"] = convertRows(arrayField(document, "budgets"), ::convertBudget)
        fields["goals"] = convertRows(arrayField(document, "goals"), ::convertGoal)
        fields["goalContributions"] = convertRows(arrayField(document, "goalContributions"), ::convertContribution)
        fields["recurring"] = JsonValue.Arr(emptyList())
        fields["debtTerms"] = JsonValue.Arr(emptyList())
        fields["deletions"] = JsonValue.Arr(emptyList())
        fields["settings"] = defaultSettings(accounts)
        return JsonValue.Obj(fields)
    }

    private fun convertRows(items: List<JsonValue>, convert: (JsonValue.Obj) -> JsonValue.Obj): JsonValue.Arr =
        JsonValue.Arr(items.map { convert(it.obj()) })

    private fun convertWallet(row: JsonValue.Obj): JsonValue.Obj = copyWithTimes(
        row,
        setOf("id", "name", "type", "currency", "openingBalanceMinor", "sortOrder", "archived", "createdAt", "updatedAt"),
    )

    private fun convertCategory(row: JsonValue.Obj): JsonValue.Obj = copyWithTimes(
        row,
        setOf("id", "name", "kind", "iconKey", "sortOrder", "archived", "createdAt", "updatedAt"),
    )

    private fun convertTransaction(row: JsonValue.Obj): JsonValue.Obj = copyWithTimes(
        row,
        setOf(
            "id", "type", "amountMinor", "currency", "walletId", "categoryId", "occurredOn",
            "note", "createdAt", "updatedAt", "transferGroup",
        ),
        dateFields = setOf("occurredOn"),
        nullIfMissing = setOf("categoryId", "transferGroup"),
    )

    private fun convertBudget(row: JsonValue.Obj): JsonValue.Obj = copyWithTimes(
        row,
        setOf("id", "categoryId", "currency", "limitMinor", "createdAt", "updatedAt"),
        nullIfMissing = setOf("categoryId"),
    )

    private fun convertGoal(row: JsonValue.Obj): JsonValue.Obj = copyWithTimes(
        row,
        setOf(
            "id", "name", "targetMinor", "currency", "targetDate", "walletId", "archived",
            "sceneKey", "createdAt", "updatedAt",
        ),
        dateFields = setOf("targetDate"),
        nullIfMissing = setOf("targetDate", "walletId", "sceneKey"),
    )

    private fun convertContribution(row: JsonValue.Obj): JsonValue.Obj = copyWithTimes(
        row,
        setOf("id", "goalId", "amountMinor", "occurredOn", "note", "createdAt"),
        dateFields = setOf("occurredOn"),
        instantFields = setOf("createdAt"),
    )

    private fun copyWithTimes(
        row: JsonValue.Obj,
        allowed: Set<String>,
        dateFields: Set<String> = emptySet(),
        instantFields: Set<String> = setOf("createdAt", "updatedAt"),
        nullIfMissing: Set<String> = emptySet(),
    ): JsonValue.Obj {
        rejectUnknown(row, allowed)
        val fields = LinkedHashMap<String, JsonValue>()
        for (key in allowed) {
            val value = row.fields[key]
            fields[key] = when {
                value == null && key in nullIfMissing -> JsonValue.Null
                value == null -> throw BackupFormatException(
                    BackupFailure.INVALID,
                    "This file isn’t a valid Lucento backup. Nothing was changed.",
                )
                key in dateFields -> asDate(value, key)
                key in instantFields -> asInstant(value, key)
                else -> value
            }
        }
        return JsonValue.Obj(fields)
    }

    private fun defaultSettings(accounts: List<JsonValue.Obj>): JsonValue.Obj {
        val currency = accounts.firstOrNull()
            ?.fields?.get("currency")
            ?.let { (it as? JsonValue.Str)?.value }
            ?.takeIf { it.length == 3 }
            ?: "USD"
        return JsonValue.Obj(
            linkedMapOf(
                "onboardingComplete" to JsonValue.Bool(accounts.isNotEmpty()),
                "themeMode" to JsonValue.Str("SYSTEM"),
                "primaryCurrency" to JsonValue.Str(currency),
                "enabledFeatures" to JsonValue.Arr(emptyList()),
                "acknowledgedBudgetMonth" to JsonValue.Null,
                "displayName" to JsonValue.Null,
                "categorySetupDone" to JsonValue.Bool(false),
                "defaultWalletId" to JsonValue.Null,
                "guideStep" to JsonValue.Null,
                "smartReading" to JsonValue.Bool(false),
            ),
        )
    }
}

private fun rejectUnknown(obj: JsonValue.Obj, allowed: Set<String>) {
    val unknown = obj.fields.keys.firstOrNull { it !in allowed }
    if (unknown != null || obj.fields.keys.any(::forbiddenKey)) {
        throw BackupFormatException(BackupFailure.INVALID, "This file isn’t a valid Lucento backup. Nothing was changed.")
    }
}

internal fun forbiddenKey(key: String): Boolean {
    val normalized = key.lowercase().replace("_", "")
    return normalized in FORBIDDEN_KEYS
}

private val FORBIDDEN_KEYS = setOf(
    "accesstoken", "refreshtoken", "token", "apikey", "anonkey", "servicerole", "servicekey",
    "password", "session", "installid", "installationid", "crashreports", "crashlytics",
    "synceduserid", "authorization", "secret", "privatekey", "usage", "analytics",
)

private fun arrayField(obj: JsonValue.Obj, key: String): List<JsonValue> =
    (obj.fields[key] as? JsonValue.Arr)?.items
        ?: throw BackupFormatException(BackupFailure.INVALID, "This file isn’t a valid Lucento backup. Nothing was changed.")

private fun intField(obj: JsonValue.Obj, key: String): Int {
    val num = obj.fields[key] as? JsonValue.Num
        ?: throw BackupFormatException(BackupFailure.INVALID, "This file isn’t a valid Lucento backup. Nothing was changed.")
    val value = num.toLongExact()
    if (value !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
        throw BackupFormatException(BackupFailure.INVALID, "This file isn’t a valid Lucento backup. Nothing was changed.")
    }
    return value.toInt()
}

private fun asInstant(value: JsonValue?, field: String): JsonValue {
    if (value == null) {
        throw BackupFormatException(BackupFailure.INVALID, "This file isn’t a valid Lucento backup. Nothing was changed.")
    }
    return when (value) {
        is JsonValue.Str -> {
            parseInstant(value.value)
            value
        }
        is JsonValue.Num -> try {
            JsonValue.Str(Instant.ofEpochMilli(value.toLongExact()).toString())
        } catch (_: DateTimeException) {
            throw BackupFormatException(BackupFailure.INVALID, "This file isn’t a valid Lucento backup. Nothing was changed.")
        }
        else -> throw BackupFormatException(BackupFailure.INVALID, "This file isn’t a valid Lucento backup. Nothing was changed.")
    }
}

private fun asDate(value: JsonValue, field: String): JsonValue = when (value) {
    JsonValue.Null -> JsonValue.Null
    is JsonValue.Str -> {
        if (value.value.isNotEmpty()) parseDate(value.value)
        value
    }
    is JsonValue.Num -> try {
        JsonValue.Str(LocalDate.ofEpochDay(value.toLongExact()).toString())
    } catch (_: DateTimeException) {
        throw BackupFormatException(BackupFailure.INVALID, "This file isn’t a valid Lucento backup. Nothing was changed.")
    }
    else -> throw BackupFormatException(BackupFailure.INVALID, "This file isn’t a valid Lucento backup. Nothing was changed.")
}

private fun parseInstant(text: String): Instant = try {
    Instant.parse(text)
} catch (_: DateTimeParseException) {
    throw BackupFormatException(BackupFailure.INVALID, "This file isn’t a valid Lucento backup. Nothing was changed.")
}

private fun parseDate(text: String): LocalDate = try {
    LocalDate.parse(text)
} catch (_: DateTimeParseException) {
    throw BackupFormatException(BackupFailure.INVALID, "This file isn’t a valid Lucento backup. Nothing was changed.")
}
