package com.lucento.app.domain.capture.merchant

import com.lucento.app.domain.Category
import com.lucento.app.domain.CategoryKind

/**
 * A dictionary's category intent ("cafe", "fuel", "streaming": the same keys as category icons) → one of the person's
 * own categories. Categories are the person's choice (docs/CALCULATIONS.md "Categories"), so an intent finds the
 * category with that icon, then a broader one ("cafe" → Dining, "fuel" → Car → Transport), then one whose name says
 * it. Null when none fits: the row then asks, as before.
 */
object CategoryIntents {

    private val broader: Map<String, List<String>> = mapOf(
        "cafe" to listOf("dining", "takeout"),
        "takeout" to listOf("dining", "cafe"),
        "work_meals" to listOf("dining"),
        "convenience" to listOf("groceries", "dining", "shopping"),
        "groceries" to listOf("convenience", "home_supplies"),
        "dining" to listOf("takeout", "cafe"),
        "fuel" to listOf("car", "transport"),
        "taxi" to listOf("transport", "car"),
        "parking" to listOf("car", "transport"),
        "car" to listOf("transport", "fuel"),
        "transport" to listOf("taxi", "car", "travel"),
        "water" to listOf("utilities", "housing"),
        "internet" to listOf("utilities", "phone", "subscriptions"),
        "phone" to listOf("utilities", "internet"),
        "heating" to listOf("utilities", "housing"),
        "utilities" to listOf("housing"),
        "pharmacy" to listOf("health"),
        "dental" to listOf("health"),
        "vision" to listOf("health"),
        "fitness" to listOf("health", "personal", "sports"),
        "grooming" to listOf("personal"),
        "personal" to listOf("grooming", "shopping"),
        "pets" to listOf("other"),
        "baby" to listOf("family", "childcare", "shopping"),
        "tuition" to listOf("education"),
        "books" to listOf("education", "hobbies", "shopping"),
        "streaming" to listOf("subscriptions", "entertainment"),
        "subscriptions" to listOf("streaming", "entertainment"),
        "games" to listOf("entertainment", "hobbies"),
        "entertainment" to listOf("hobbies", "social"),
        "hotel" to listOf("travel"),
        "travel" to listOf("hotel", "transport"),
        "shopping" to listOf("home_supplies"),
        "home_supplies" to listOf("shopping", "housing", "repairs"),
        "furniture" to listOf("home_supplies", "shopping", "housing"),
        "repairs" to listOf("maintenance", "housing", "home_supplies"),
        "gift" to listOf("donation"),
        "donation" to listOf("gift", "community"),
        "bank_fees" to listOf("fees"),
        "fees" to listOf("bank_fees"),
        "tax" to listOf("fees"),
        "insurance" to listOf("health", "housing"),
        "interest" to listOf("fees", "bank_fees", "debt", "credit_card"),
    )

    /** Words a category's own name may use for an intent when no icon matches ("Coffee" for cafe). */
    private val nameWords: Map<String, List<String>> = mapOf(
        "cafe" to listOf("coffee", "cafe", "café"),
        "dining" to listOf("food", "dining", "eating out", "restaurants", "meals"),
        "takeout" to listOf("takeout", "delivery"),
        "groceries" to listOf("groceries", "grocery", "supermarket", "food"),
        "fuel" to listOf("fuel", "gas", "petrol"),
        "taxi" to listOf("taxi", "rides", "ride hailing"),
        "transport" to listOf("transport", "transportation", "commute"),
        "utilities" to listOf("utilities", "bills", "electricity"),
        "water" to listOf("water"),
        "internet" to listOf("internet", "wifi"),
        "phone" to listOf("phone", "mobile", "load"),
        "pharmacy" to listOf("pharmacy", "medicine", "medicines"),
        "health" to listOf("health", "medical"),
        "streaming" to listOf("streaming"),
        "subscriptions" to listOf("subscriptions", "subscription"),
        "shopping" to listOf("shopping"),
        "travel" to listOf("travel"),
        "hotel" to listOf("hotel", "accommodation", "lodging"),
        "bank_fees" to listOf("bank fees", "fees", "charges"),
        "fees" to listOf("fees", "charges"),
        "interest" to listOf("interest", "finance charges"),
        "insurance" to listOf("insurance"),
        "refund" to listOf("refunds", "refund"),
        "work" to listOf("salary", "payroll", "wages"),
        "investments" to listOf("interest", "investments", "dividends"),
    )

    /** The person's category for [intent], of [kind]; archived categories are never suggested. */
    fun resolve(intent: String?, kind: CategoryKind, categories: List<Category>): Long? {
        if (intent.isNullOrBlank()) return null
        val pool = categories.filter { it.kind == kind && !it.archived }
        if (pool.isEmpty()) return null
        pool.firstOrNull { it.iconKey == intent }?.let { return it.id }
        broader[intent].orEmpty().forEach { key -> pool.firstOrNull { it.iconKey == key }?.let { return it.id } }
        val words = nameWords[intent].orEmpty() + intent.replace('_', ' ')
        return pool.firstOrNull { c -> val name = MerchantDirectory.normalize(c.name); words.any { MerchantDirectory.normalize(it) == name } }?.id
    }
}
