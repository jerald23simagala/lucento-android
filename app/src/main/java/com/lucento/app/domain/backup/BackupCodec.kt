package com.lucento.app.domain.backup

import com.lucento.app.core.money.CurrencyRules
import com.lucento.app.core.money.Money
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.DebtTerms
import com.lucento.app.domain.Feature
import com.lucento.app.domain.GuideStep
import com.lucento.app.domain.TransactionType
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException

/**
 * Reads and writes backup schema [SCHEMA_VERSION]. Older documents are migrated first
 * ([BackupMigrations]); a newer schemaVersion is refused and nothing is imported.
 */
object BackupCodec {
    const val SCHEMA_VERSION = BackupMigrations.CURRENT
    const val MAX_CHARS = 48 * 1024 * 1024
    private const val MAX_ROWS = 200_000

    private val rootKeys = setOf(
        "schemaVersion", "appVersion", "exportedAt", "wallets", "categories", "transactions",
        "budgets", "goals", "goalContributions", "recurring", "debtTerms", "deletions", "settings",
    )
    private val settingsKeys = setOf(
        "onboardingComplete", "themeMode", "primaryCurrency", "enabledFeatures",
        "acknowledgedBudgetMonth", "displayName", "categorySetupDone", "defaultWalletId",
        "guideStep", "smartReading",
    )

    fun encode(document: BackupDocument): String {
        val snapshot = document.snapshot.sorted()
        val root = jsonObject(
            "schemaVersion" to JsonValue.Num(SCHEMA_VERSION.toString()),
            "appVersion" to JsonValue.Str(document.appVersion),
            "exportedAt" to jsonInstant(document.exportedAt),
            "wallets" to JsonValue.Arr(snapshot.wallets.map(::walletJson)),
            "categories" to JsonValue.Arr(snapshot.categories.map(::categoryJson)),
            "transactions" to JsonValue.Arr(snapshot.transactions.map(::transactionJson)),
            "budgets" to JsonValue.Arr(snapshot.budgets.map(::budgetJson)),
            "goals" to JsonValue.Arr(snapshot.goals.map(::goalJson)),
            "goalContributions" to JsonValue.Arr(snapshot.goalContributions.map(::contributionJson)),
            "recurring" to JsonValue.Arr(snapshot.recurring.map(::recurringJson)),
            "debtTerms" to JsonValue.Arr(snapshot.debtTerms.map(::debtJson)),
            "deletions" to JsonValue.Arr(snapshot.deletions.map(::deletionJson)),
            "settings" to settingsJson(snapshot.settings),
        )
        return Json.stringify(root)
    }

    fun decode(json: String): BackupDocument {
        if (json.length > MAX_CHARS) {
            throw BackupFormatException(BackupFailure.TOO_LARGE, "This backup is too large to restore. Nothing was changed.")
        }
        val root = Json.parse(json.removePrefix("\uFEFF")).obj()
        rejectForbiddenTree(root)
        val version = root.long("schemaVersion")
        if (version > SCHEMA_VERSION) {
            throw BackupFormatException(
                BackupFailure.UNSUPPORTED_VERSION,
                "This backup is from a newer Lucento and can’t be restored. Nothing was changed.",
            )
        }
        if (version < 0) invalid()
        val current = BackupMigrations.toCurrent(root)
        return interpret(current)
    }

    private fun interpret(root: JsonValue.Obj): BackupDocument {
        requireKeys(root, rootKeys)
        if (root.long("schemaVersion") != SCHEMA_VERSION.toLong()) invalid()
        val appVersion = root.text("appVersion", 1..40)
        val exportedAt = root.instant("exportedAt")
        val wallets = root.rows("wallets", ::wallet) { it.id }
        val categories = root.rows("categories", ::category) { it.id }
        val transactions = root.rows("transactions", ::transaction) { it.id }
        val budgets = root.rows("budgets", ::budget) { it.id }
        val goals = root.rows("goals", ::goal) { it.id }
        val contributions = root.rows("goalContributions", ::contribution) { it.id }
        val recurring = root.rows("recurring", ::recurring) { it.id }
        val debts = root.rows("debtTerms", ::debt) { it.walletId }
        val deletions = root.rows("deletions", ::deletion) { "${it.table}:${it.id}" }
        val settings = settings(root.obj("settings"))
        val snapshot = BackupSnapshot(
            wallets = wallets,
            categories = categories,
            transactions = transactions,
            budgets = budgets,
            goals = goals,
            goalContributions = contributions,
            recurring = recurring,
            debtTerms = debts,
            deletions = deletions,
            settings = settings,
        ).sorted()
        validateGraph(snapshot)
        return BackupDocument(SCHEMA_VERSION, appVersion, exportedAt, snapshot)
    }

    private fun wallet(row: JsonValue.Obj): BackupWallet {
        requireKeys(row, setOf("id", "name", "type", "currency", "openingBalanceMinor", "sortOrder", "archived", "createdAt", "updatedAt"))
        val opening = row.long("openingBalanceMinor")
        if (opening < 0L) invalid()
        return BackupWallet(
            id = row.id("id"),
            name = row.name("name"),
            type = row.enum("type"),
            currency = row.currency("currency"),
            openingBalanceMinor = opening,
            sortOrder = row.int("sortOrder"),
            archived = row.bool("archived"),
            createdAt = row.instant("createdAt"),
            updatedAt = row.instant("updatedAt"),
        )
    }

    private fun category(row: JsonValue.Obj): BackupCategory {
        requireKeys(row, setOf("id", "name", "kind", "iconKey", "sortOrder", "archived", "createdAt", "updatedAt"))
        return BackupCategory(
            id = row.id("id"),
            name = row.name("name"),
            kind = row.enum("kind"),
            iconKey = row.token("iconKey", 1..64),
            sortOrder = row.int("sortOrder"),
            archived = row.bool("archived"),
            createdAt = row.instant("createdAt"),
            updatedAt = row.instant("updatedAt"),
        )
    }

    private fun transaction(row: JsonValue.Obj): BackupTransaction {
        requireKeys(
            row,
            setOf(
                "id", "type", "amountMinor", "currency", "walletId", "categoryId", "occurredOn",
                "note", "createdAt", "updatedAt", "transferGroup",
            ),
        )
        val amount = row.long("amountMinor")
        if (amount <= 0L) invalid()
        return BackupTransaction(
            id = row.id("id"),
            type = row.enum("type"),
            amountMinor = amount,
            currency = row.currency("currency"),
            walletId = row.id("walletId"),
            categoryId = row.idOrNull("categoryId"),
            occurredOn = row.date("occurredOn"),
            note = row.note("note"),
            createdAt = row.instant("createdAt"),
            updatedAt = row.instant("updatedAt"),
            transferGroup = row.idOrNull("transferGroup"),
        )
    }

    private fun budget(row: JsonValue.Obj): BackupBudget {
        requireKeys(row, setOf("id", "categoryId", "currency", "limitMinor", "createdAt", "updatedAt"))
        val limit = row.long("limitMinor")
        if (limit <= 0L) invalid()
        return BackupBudget(
            id = row.id("id"),
            categoryId = row.idOrNull("categoryId"),
            currency = row.currency("currency"),
            limitMinor = limit,
            createdAt = row.instant("createdAt"),
            updatedAt = row.instant("updatedAt"),
        )
    }

    private fun goal(row: JsonValue.Obj): BackupGoal {
        requireKeys(
            row,
            setOf("id", "name", "targetMinor", "currency", "targetDate", "walletId", "archived", "sceneKey", "createdAt", "updatedAt"),
        )
        val target = row.long("targetMinor")
        if (target <= 0L) invalid()
        return BackupGoal(
            id = row.id("id"),
            name = row.name("name"),
            targetMinor = target,
            currency = row.currency("currency"),
            targetDate = row.dateOrNull("targetDate"),
            walletId = row.idOrNull("walletId"),
            archived = row.bool("archived"),
            sceneKey = row.tokenOrNull("sceneKey", 1..64),
            createdAt = row.instant("createdAt"),
            updatedAt = row.instant("updatedAt"),
            photoPath = null,
        )
    }

    private fun contribution(row: JsonValue.Obj): BackupGoalContribution {
        requireKeys(row, setOf("id", "goalId", "amountMinor", "occurredOn", "note", "createdAt"))
        val amount = row.long("amountMinor")
        if (amount == 0L) invalid()
        return BackupGoalContribution(
            id = row.id("id"),
            goalId = row.id("goalId"),
            amountMinor = amount,
            occurredOn = row.date("occurredOn"),
            note = row.note("note"),
            createdAt = row.instant("createdAt"),
        )
    }

    private fun recurring(row: JsonValue.Obj): BackupRecurring {
        requireKeys(
            row,
            setOf(
                "id", "name", "type", "amountMinor", "currency", "walletId", "categoryId", "frequency",
                "intervalCount", "startOn", "endOn", "nextIndex", "paused", "toWalletId", "interestMinor",
                "createdAt", "updatedAt",
            ),
        )
        val amount = row.long("amountMinor")
        val interest = row.long("interestMinor")
        val interval = row.int("intervalCount")
        val next = row.int("nextIndex")
        if (amount <= 0L || interest < 0L || interval !in 1..10_000 || next < 0) invalid()
        val start = row.date("startOn")
        val end = row.dateOrNull("endOn")
        if (end != null && end.isBefore(start)) invalid()
        return BackupRecurring(
            id = row.id("id"),
            name = row.name("name"),
            type = row.enum("type"),
            amountMinor = amount,
            currency = row.currency("currency"),
            walletId = row.id("walletId"),
            categoryId = row.idOrNull("categoryId"),
            frequency = row.enum("frequency"),
            intervalCount = interval,
            startOn = start,
            endOn = end,
            nextIndex = next,
            paused = row.bool("paused"),
            toWalletId = row.idOrNull("toWalletId"),
            interestMinor = interest,
            createdAt = row.instant("createdAt"),
            updatedAt = row.instant("updatedAt"),
        )
    }

    private fun debt(row: JsonValue.Obj): BackupDebtTerms {
        requireKeys(row, setOf("walletId", "interestRateBps", "repayment", "termMonths", "installmentMinor", "updatedAt"))
        return BackupDebtTerms(
            walletId = row.id("walletId"),
            interestRateBps = row.intOrNull("interestRateBps"),
            repayment = row.enum("repayment"),
            termMonths = row.intOrNull("termMonths"),
            installmentMinor = row.longOrNull("installmentMinor"),
            updatedAt = row.instant("updatedAt"),
        )
    }

    private fun deletion(row: JsonValue.Obj): BackupDeletion {
        requireKeys(row, setOf("table", "id", "deletedAt"))
        val table = row.text("table", 1..40)
        if (table !in BackupTables.ALL) invalid()
        return BackupDeletion(table = table, id = row.id("id"), deletedAt = row.instant("deletedAt"))
    }

    private fun settings(row: JsonValue.Obj): BackupSettings {
        requireKeys(row, settingsKeys)
        val theme = row.text("themeMode", 1..16)
        if (theme != "SYSTEM" && theme != "LIGHT" && theme != "DARK") invalid()
        val currency = row.currency("primaryCurrency")
        val features = (row.fields.getValue("enabledFeatures") as? JsonValue.Arr)?.items ?: invalid()
        if (features.size > Feature.entries.size) invalid()
        val keys = features.map { item ->
            val key = (item as? JsonValue.Str)?.value ?: invalid()
            if (Feature.fromKey(key) == null) invalid()
            key
        }
        if (keys.size != keys.toSet().size) invalid()
        val month = row.textOrNull("acknowledgedBudgetMonth", 7..7)
        if (month != null) {
            try {
                YearMonth.parse(month)
            } catch (_: DateTimeParseException) {
                invalid()
            }
        }
        val guide = row.textOrNull("guideStep", 1..32)
        if (guide != null && GuideStep.fromKey(guide) == null) invalid()
        val name = row.textOrNull("displayName", 1..40)
        return BackupSettings(
            onboardingComplete = row.bool("onboardingComplete"),
            themeMode = theme,
            primaryCurrency = currency,
            enabledFeatures = Feature.entries.map { it.key }.filter { it in keys },
            acknowledgedBudgetMonth = month,
            displayName = name,
            categorySetupDone = row.bool("categorySetupDone"),
            defaultWalletId = row.idOrNull("defaultWalletId"),
            guideStep = guide,
            smartReading = row.bool("smartReading"),
        )
    }

    private fun validateGraph(snapshot: BackupSnapshot) {
        val wallets = snapshot.wallets.associateBy { it.id }
        val categories = snapshot.categories.associateBy { it.id }
        val goals = snapshot.goals.associateBy { it.id }
        for (tx in snapshot.transactions) {
            val wallet = wallets[tx.walletId] ?: invalid()
            if (!wallet.type.acceptsTransactions || wallet.currency != tx.currency) invalid()
            when (tx.type) {
                TransactionType.INCOME, TransactionType.EXPENSE -> {
                    if (tx.transferGroup != null) invalid()
                    val category = tx.categoryId?.let { categories[it] ?: invalid() }
                    if (category != null && category.kind.name != tx.type.name) invalid()
                }
                TransactionType.TRANSFER_IN, TransactionType.TRANSFER_OUT -> {
                    if (tx.transferGroup == null || tx.categoryId != null) invalid()
                }
                TransactionType.ADJUSTMENT_IN, TransactionType.ADJUSTMENT_OUT -> {
                    if (tx.transferGroup != null || tx.categoryId != null) invalid()
                }
            }
        }
        for (budget in snapshot.budgets) {
            val categoryId = budget.categoryId ?: continue
            val category = categories[categoryId] ?: invalid()
            if (category.kind != CategoryKind.EXPENSE) invalid()
        }
        for (goal in snapshot.goals) {
            val walletId = goal.walletId ?: continue
            if (wallets[walletId] == null) invalid()
        }
        for (contribution in snapshot.goalContributions) {
            if (goals[contribution.goalId] == null) invalid()
        }
        for (schedule in snapshot.recurring) {
            if (schedule.type != TransactionType.INCOME && schedule.type != TransactionType.EXPENSE) invalid()
            val wallet = wallets[schedule.walletId] ?: invalid()
            if (!wallet.type.acceptsTransactions || wallet.currency != schedule.currency) invalid()
            val category = schedule.categoryId?.let { categories[it] ?: invalid() }
            if (category != null && category.kind.name != schedule.type.name) invalid()
            val to = schedule.toWalletId
            if (to != null) {
                if (schedule.type != TransactionType.EXPENSE || to == schedule.walletId || wallets[to] == null) invalid()
                if (!wallets.getValue(to).type.isLiability) invalid()
            }
        }
        val debtsByWallet = HashSet<String>()
        for (debt in snapshot.debtTerms) {
            if (!debtsByWallet.add(debt.walletId)) invalid()
            val wallet = wallets[debt.walletId] ?: invalid()
            if (!wallet.type.isLiability) invalid()
            try {
                DebtTerms(
                    interestRateBps = debt.interestRateBps,
                    repayment = debt.repayment,
                    termMonths = debt.termMonths,
                    installment = debt.installmentMinor?.let { minor ->
                        if (minor <= 0L) invalid()
                        Money(minor, wallet.currency)
                    },
                )
            } catch (_: IllegalArgumentException) {
                invalid()
            }
        }
        val live = HashSet<String>()
        fun mark(table: String, id: String) {
            if (!live.add("$table:$id")) invalid()
        }
        snapshot.wallets.forEach { mark(BackupTables.WALLETS, it.id) }
        snapshot.categories.forEach { mark(BackupTables.CATEGORIES, it.id) }
        snapshot.transactions.forEach { mark(BackupTables.TRANSACTIONS, it.id) }
        snapshot.budgets.forEach { mark(BackupTables.BUDGETS, it.id) }
        snapshot.goals.forEach { mark(BackupTables.GOALS, it.id) }
        snapshot.goalContributions.forEach { mark(BackupTables.GOAL_CONTRIBUTIONS, it.id) }
        snapshot.recurring.forEach { mark(BackupTables.RECURRING, it.id) }
        for (deletion in snapshot.deletions) {
            if ("${deletion.table}:${deletion.id}" in live) invalid()
        }
        val defaultWallet = snapshot.settings.defaultWalletId
        if (defaultWallet != null && wallets[defaultWallet] == null) invalid()
    }

    private fun walletJson(row: BackupWallet) = jsonObject(
        "id" to JsonValue.Str(row.id),
        "name" to JsonValue.Str(row.name),
        "type" to JsonValue.Str(row.type.name),
        "currency" to JsonValue.Str(row.currency),
        "openingBalanceMinor" to jsonLong(row.openingBalanceMinor),
        "sortOrder" to jsonLong(row.sortOrder.toLong()),
        "archived" to JsonValue.Bool(row.archived),
        "createdAt" to jsonInstant(row.createdAt),
        "updatedAt" to jsonInstant(row.updatedAt),
    )

    private fun categoryJson(row: BackupCategory) = jsonObject(
        "id" to JsonValue.Str(row.id),
        "name" to JsonValue.Str(row.name),
        "kind" to JsonValue.Str(row.kind.name),
        "iconKey" to JsonValue.Str(row.iconKey),
        "sortOrder" to jsonLong(row.sortOrder.toLong()),
        "archived" to JsonValue.Bool(row.archived),
        "createdAt" to jsonInstant(row.createdAt),
        "updatedAt" to jsonInstant(row.updatedAt),
    )

    private fun transactionJson(row: BackupTransaction) = jsonObject(
        "id" to JsonValue.Str(row.id),
        "type" to JsonValue.Str(row.type.name),
        "amountMinor" to jsonLong(row.amountMinor),
        "currency" to JsonValue.Str(row.currency),
        "walletId" to JsonValue.Str(row.walletId),
        "categoryId" to jsonNullable(row.categoryId),
        "occurredOn" to JsonValue.Str(row.occurredOn.toString()),
        "note" to JsonValue.Str(row.note),
        "createdAt" to jsonInstant(row.createdAt),
        "updatedAt" to jsonInstant(row.updatedAt),
        "transferGroup" to jsonNullable(row.transferGroup),
    )

    private fun budgetJson(row: BackupBudget) = jsonObject(
        "id" to JsonValue.Str(row.id),
        "categoryId" to jsonNullable(row.categoryId),
        "currency" to JsonValue.Str(row.currency),
        "limitMinor" to jsonLong(row.limitMinor),
        "createdAt" to jsonInstant(row.createdAt),
        "updatedAt" to jsonInstant(row.updatedAt),
    )

    private fun goalJson(row: BackupGoal) = jsonObject(
        "id" to JsonValue.Str(row.id),
        "name" to JsonValue.Str(row.name),
        "targetMinor" to jsonLong(row.targetMinor),
        "currency" to JsonValue.Str(row.currency),
        "targetDate" to (row.targetDate?.let { JsonValue.Str(it.toString()) } ?: JsonValue.Null),
        "walletId" to jsonNullable(row.walletId),
        "archived" to JsonValue.Bool(row.archived),
        "sceneKey" to jsonNullable(row.sceneKey),
        "createdAt" to jsonInstant(row.createdAt),
        "updatedAt" to jsonInstant(row.updatedAt),
    )

    private fun contributionJson(row: BackupGoalContribution) = jsonObject(
        "id" to JsonValue.Str(row.id),
        "goalId" to JsonValue.Str(row.goalId),
        "amountMinor" to jsonLong(row.amountMinor),
        "occurredOn" to JsonValue.Str(row.occurredOn.toString()),
        "note" to JsonValue.Str(row.note),
        "createdAt" to jsonInstant(row.createdAt),
    )

    private fun recurringJson(row: BackupRecurring) = jsonObject(
        "id" to JsonValue.Str(row.id),
        "name" to JsonValue.Str(row.name),
        "type" to JsonValue.Str(row.type.name),
        "amountMinor" to jsonLong(row.amountMinor),
        "currency" to JsonValue.Str(row.currency),
        "walletId" to JsonValue.Str(row.walletId),
        "categoryId" to jsonNullable(row.categoryId),
        "frequency" to JsonValue.Str(row.frequency.name),
        "intervalCount" to jsonLong(row.intervalCount.toLong()),
        "startOn" to JsonValue.Str(row.startOn.toString()),
        "endOn" to (row.endOn?.let { JsonValue.Str(it.toString()) } ?: JsonValue.Null),
        "nextIndex" to jsonLong(row.nextIndex.toLong()),
        "paused" to JsonValue.Bool(row.paused),
        "toWalletId" to jsonNullable(row.toWalletId),
        "interestMinor" to jsonLong(row.interestMinor),
        "createdAt" to jsonInstant(row.createdAt),
        "updatedAt" to jsonInstant(row.updatedAt),
    )

    private fun debtJson(row: BackupDebtTerms) = jsonObject(
        "walletId" to JsonValue.Str(row.walletId),
        "interestRateBps" to jsonNullableLong(row.interestRateBps?.toLong()),
        "repayment" to JsonValue.Str(row.repayment.name),
        "termMonths" to jsonNullableLong(row.termMonths?.toLong()),
        "installmentMinor" to jsonNullableLong(row.installmentMinor),
        "updatedAt" to jsonInstant(row.updatedAt),
    )

    private fun deletionJson(row: BackupDeletion) = jsonObject(
        "table" to JsonValue.Str(row.table),
        "id" to JsonValue.Str(row.id),
        "deletedAt" to jsonInstant(row.deletedAt),
    )

    private fun settingsJson(row: BackupSettings) = jsonObject(
        "onboardingComplete" to JsonValue.Bool(row.onboardingComplete),
        "themeMode" to JsonValue.Str(row.themeMode),
        "primaryCurrency" to JsonValue.Str(row.primaryCurrency),
        "enabledFeatures" to JsonValue.Arr(row.enabledFeatures.map { JsonValue.Str(it) }),
        "acknowledgedBudgetMonth" to jsonNullable(row.acknowledgedBudgetMonth),
        "displayName" to jsonNullable(row.displayName),
        "categorySetupDone" to JsonValue.Bool(row.categorySetupDone),
        "defaultWalletId" to jsonNullable(row.defaultWalletId),
        "guideStep" to jsonNullable(row.guideStep),
        "smartReading" to JsonValue.Bool(row.smartReading),
    )
}

private fun BackupSnapshot.sorted(): BackupSnapshot = copy(
    wallets = wallets.sortedBy { it.id },
    categories = categories.sortedBy { it.id },
    transactions = transactions.sortedWith(compareBy({ it.occurredOn }, { it.id })),
    budgets = budgets.sortedBy { it.id },
    goals = goals.sortedBy { it.id },
    goalContributions = goalContributions.sortedWith(compareBy({ it.occurredOn }, { it.id })),
    recurring = recurring.sortedBy { it.id },
    debtTerms = debtTerms.sortedBy { it.walletId },
    deletions = deletions.sortedWith(compareBy({ it.table }, { it.id })),
    settings = settings.copy(
        enabledFeatures = Feature.entries.map { it.key }.filter { it in settings.enabledFeatures },
    ),
)

private fun jsonObject(vararg pairs: Pair<String, JsonValue>): JsonValue.Obj =
    JsonValue.Obj(linkedMapOf(*pairs))

private fun jsonLong(value: Long): JsonValue.Num = JsonValue.Num(value.toString())

private fun jsonInstant(millis: Long): JsonValue.Str = try {
    JsonValue.Str(Instant.ofEpochMilli(millis).toString())
} catch (_: DateTimeException) {
    invalid()
}

private fun jsonNullable(value: String?): JsonValue = if (value == null) JsonValue.Null else JsonValue.Str(value)

private fun jsonNullableLong(value: Long?): JsonValue = if (value == null) JsonValue.Null else jsonLong(value)

private fun requireKeys(obj: JsonValue.Obj, allowed: Set<String>) {
    if (obj.fields.keys != allowed || obj.fields.keys.any(::forbiddenKey)) invalid()
}

private fun rejectForbiddenTree(value: JsonValue) {
    when (value) {
        is JsonValue.Obj -> {
            if (value.fields.keys.any(::forbiddenKey)) invalid()
            value.fields.values.forEach(::rejectForbiddenTree)
        }
        is JsonValue.Arr -> value.items.forEach(::rejectForbiddenTree)
        else -> Unit
    }
}

private fun <T> JsonValue.Obj.rows(key: String, read: (JsonValue.Obj) -> T, idOf: (T) -> String): List<T> {
    val array = fields[key] as? JsonValue.Arr ?: invalid()
    if (array.items.size > 200_000) {
        throw BackupFormatException(BackupFailure.TOO_LARGE, "This backup is too large to restore. Nothing was changed.")
    }
    val seen = HashSet<String>(array.items.size)
    return array.items.map { item ->
        val row = read(item.obj())
        if (!seen.add(idOf(row))) invalid()
        row
    }
}

private fun JsonValue.Obj.obj(key: String): JsonValue.Obj = (fields[key] as? JsonValue.Obj) ?: invalid()

private fun JsonValue.Obj.long(key: String): Long = (fields[key] as? JsonValue.Num)?.toLongExact() ?: invalid()

private fun JsonValue.Obj.longOrNull(key: String): Long? = when (val value = fields[key]) {
    JsonValue.Null -> null
    is JsonValue.Num -> value.toLongExact()
    else -> invalid()
}

private fun JsonValue.Obj.int(key: String): Int {
    val value = long(key)
    if (value !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) invalid()
    return value.toInt()
}

private fun JsonValue.Obj.intOrNull(key: String): Int? {
    val value = longOrNull(key) ?: return null
    if (value !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) invalid()
    return value.toInt()
}

private fun JsonValue.Obj.bool(key: String): Boolean = (fields[key] as? JsonValue.Bool)?.value ?: invalid()

private fun JsonValue.Obj.text(key: String, length: IntRange): String {
    val value = (fields[key] as? JsonValue.Str)?.value ?: invalid()
    if (value.length !in length || value.any { it < ' ' }) invalid()
    return value
}

private fun JsonValue.Obj.textOrNull(key: String, length: IntRange): String? = when (val value = fields[key]) {
    JsonValue.Null -> null
    is JsonValue.Str -> {
        if (value.value.isEmpty()) null
        else {
            if (value.value.length !in length || value.value.any { it < ' ' }) invalid()
            value.value
        }
    }
    else -> invalid()
}

private fun JsonValue.Obj.id(key: String): String {
    val value = text(key, 1..200)
    if (value.any { it.isWhitespace() }) invalid()
    return value
}

private fun JsonValue.Obj.idOrNull(key: String): String? = when (val value = fields[key]) {
    JsonValue.Null -> null
    is JsonValue.Str -> {
        if (value.value.length !in 1..200 || value.value.any { it.isWhitespace() || it < ' ' }) invalid()
        value.value
    }
    else -> invalid()
}

private fun JsonValue.Obj.name(key: String): String {
    val value = text(key, 1..300)
    if (value.isBlank()) invalid()
    return value
}

private fun JsonValue.Obj.note(key: String): String {
    val value = (fields[key] as? JsonValue.Str)?.value ?: invalid()
    if (value.length > 2_000 || value.any { it < ' ' && it != '\n' && it != '\t' }) invalid()
    return value
}

private fun JsonValue.Obj.token(key: String, length: IntRange): String {
    val value = text(key, length)
    if (value.any { it.isWhitespace() }) invalid()
    return value
}

private fun JsonValue.Obj.tokenOrNull(key: String, length: IntRange): String? {
    val value = textOrNull(key, length) ?: return null
    if (value.any { it.isWhitespace() }) invalid()
    return value
}

private fun JsonValue.Obj.currency(key: String): String {
    val value = text(key, 3..3)
    if (!CurrencyRules.isSupported(value)) invalid()
    return value
}

private fun JsonValue.Obj.instant(key: String): Long {
    val text = text(key, 20..40)
    val instant = try {
        Instant.parse(text)
    } catch (_: DateTimeParseException) {
        invalid()
    }
    val millis = instant.toEpochMilli()
    val year = instant.atZone(java.time.ZoneOffset.UTC).year
    if (year !in 1970..2200) invalid()
    return millis
}

private fun JsonValue.Obj.date(key: String): LocalDate = dateOrNull(key) ?: invalid()

private fun JsonValue.Obj.dateOrNull(key: String): LocalDate? = when (val value = fields[key]) {
    JsonValue.Null -> null
    is JsonValue.Str -> {
        val date = try {
            LocalDate.parse(value.value)
        } catch (_: DateTimeParseException) {
            invalid()
        }
        if (date.year !in 1900..2200) invalid()
        date
    }
    else -> invalid()
}

private inline fun <reified T : Enum<T>> JsonValue.Obj.enum(key: String): T {
    val text = text(key, 1..40)
    return try {
        enumValueOf<T>(text)
    } catch (_: IllegalArgumentException) {
        invalid()
    }
}

private fun invalid(): Nothing =
    throw BackupFormatException(BackupFailure.INVALID, "This file isn’t a valid Lucento backup. Nothing was changed.")
