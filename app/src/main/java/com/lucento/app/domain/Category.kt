package com.lucento.app.domain

enum class CategoryKind { EXPENSE, INCOME }

data class Category(
    val id: Long,
    val name: String,
    val kind: CategoryKind,
    /** Key resolved to an icon by the design system; unknown keys fall back to a generic icon. */
    val iconKey: String,
    val archived: Boolean = false,
)

/** Records that reference a category. Used to decide between deleting and archiving it. */
data class CategoryUsage(val transactionCount: Int, val budgetCount: Int, val scheduleCount: Int) {
    val isUsed: Boolean get() = transactionCount > 0 || budgetCount > 0 || scheduleCount > 0
}

/**
 * Seed categories. [all] is frozen: it is exactly what MIGRATION_1_2 inserts (and what installs before 0.5 created),
 * so it must never change. Since 0.5 a fresh install seeds only [freshInstall] (income categories); expense categories
 * come from the user's own choices in onboarding (see [CategoryPresets]). Names contain no quotes (SQL seeding).
 */
object DefaultCategories {
    data class Seed(val name: String, val kind: CategoryKind, val iconKey: String)

    val all: List<Seed> = listOf(
        Seed("Groceries", CategoryKind.EXPENSE, "groceries"),
        Seed("Dining", CategoryKind.EXPENSE, "dining"),
        Seed("Transport", CategoryKind.EXPENSE, "transport"),
        Seed("Housing", CategoryKind.EXPENSE, "housing"),
        Seed("Utilities", CategoryKind.EXPENSE, "utilities"),
        Seed("Health", CategoryKind.EXPENSE, "health"),
        Seed("Education", CategoryKind.EXPENSE, "education"),
        Seed("Entertainment", CategoryKind.EXPENSE, "entertainment"),
        Seed("Shopping", CategoryKind.EXPENSE, "shopping"),
        Seed("Travel", CategoryKind.EXPENSE, "travel"),
        Seed("Personal care", CategoryKind.EXPENSE, "personal"),
        Seed("Gifts & donations", CategoryKind.EXPENSE, "gift"),
        Seed("Subscriptions", CategoryKind.EXPENSE, "subscriptions"),
        Seed("Other", CategoryKind.EXPENSE, "other"),
        Seed("Salary", CategoryKind.INCOME, "work"),
        Seed("Business", CategoryKind.INCOME, "business"),
        Seed("Investments", CategoryKind.INCOME, "investments"),
        Seed("Gifts received", CategoryKind.INCOME, "gift"),
        Seed("Refunds", CategoryKind.INCOME, "refund"),
        Seed("Other income", CategoryKind.INCOME, "income"),
    )

    /** What a fresh install seeds: income categories only. Expense categories are chosen during onboarding. */
    val freshInstall: List<Seed> get() = all.filter { it.kind == CategoryKind.INCOME }
}
