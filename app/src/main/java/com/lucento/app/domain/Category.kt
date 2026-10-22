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

/** Categories created on first install and when upgrading from schema v1. Names contain no quotes (used in SQL seeding). */
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
}
