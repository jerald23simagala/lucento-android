package com.lucento.app.domain

import java.util.Locale

/**
 * The library of expense-category presets offered when a user builds their budget (onboarding, or later from the
 * Budget screen). It is a menu, not a mandatory list: users pick what fits their life. Picking a preset creates an
 * ordinary category (or reuses a matching one, see [CategorySelection]) — presets carry no hidden behaviour.
 *
 * [Preset.key] is stable and never shown; names follow Lucento's sentence case. Icon keys are resolved by the design
 * system (unknown keys fall back to a neutral glyph).
 */
object CategoryPresets {

    data class Preset(val key: String, val name: String, val iconKey: String, val recommended: Boolean = false)

    data class Group(val key: String, val title: String, val presets: List<Preset>)

    private fun p(key: String, name: String, icon: String, recommended: Boolean = false) = Preset(key, name, icon, recommended)

    val groups: List<Group> = listOf(
        Group(
            "housing", "Housing and utilities",
            listOf(
                p("rent", "Rent", "housing", recommended = true),
                p("mortgage", "Mortgage", "mortgage"),
                p("property_taxes", "Property taxes", "tax"),
                p("home_insurance", "Home insurance", "insurance"),
                p("electricity", "Electricity", "utilities", recommended = true),
                p("water", "Water", "water", recommended = true),
                p("gas_heating", "Gas and heating", "heating"),
                p("internet", "Internet", "internet", recommended = true),
                p("mobile_phone", "Mobile phone", "phone", recommended = true),
                p("home_maintenance", "Home maintenance", "maintenance"),
                p("home_supplies", "Home supplies", "home_supplies"),
                p("furniture_appliances", "Furniture and appliances", "furniture"),
                p("security_services", "Security services", "security"),
            ),
        ),
        Group(
            "food", "Food and groceries",
            listOf(
                p("groceries", "Groceries", "groceries", recommended = true),
                p("restaurants", "Restaurants", "dining", recommended = true),
                p("cafes_coffee", "Cafes and coffee", "cafe"),
                p("takeout_delivery", "Takeout and delivery", "takeout"),
                p("work_meals", "Work meals", "work_meals"),
                p("snacks_convenience", "Snacks and convenience stores", "convenience"),
            ),
        ),
        Group(
            "transport", "Transportation",
            listOf(
                p("fuel", "Fuel", "fuel"),
                p("public_transport", "Public transportation", "transport", recommended = true),
                p("ride_hailing", "Ride-hailing and taxis", "taxi"),
                p("parking_tolls", "Parking and tolls", "parking"),
                p("vehicle_payments", "Vehicle payments", "car"),
                p("vehicle_insurance", "Vehicle insurance", "insurance"),
                p("vehicle_maintenance", "Vehicle maintenance and repairs", "repairs"),
                p("registration_licensing", "Registration and licensing", "registration"),
                p("micromobility", "Bicycle and micromobility", "bicycle"),
            ),
        ),
        Group(
            "health", "Health and wellness",
            listOf(
                p("health_insurance", "Health insurance", "insurance"),
                p("doctor_visits", "Doctor visits", "health"),
                p("dental", "Dental care", "dental"),
                p("vision", "Vision care", "vision"),
                p("prescriptions", "Prescription medication", "pharmacy"),
                p("medical_supplies", "Medical supplies", "pharmacy"),
                p("mental_wellness", "Therapy and mental wellness", "wellbeing"),
                p("fitness_gym", "Fitness and gym", "fitness"),
                p("sports_wellness", "Sports and wellness activities", "sports"),
            ),
        ),
        Group(
            "personal", "Personal and family",
            listOf(
                p("clothing_shoes", "Clothing and shoes", "shopping", recommended = true),
                p("personal_care", "Personal care", "personal", recommended = true),
                p("haircuts_grooming", "Haircuts and grooming", "grooming"),
                p("childcare", "Childcare", "childcare"),
                p("school_supplies", "Education and school supplies", "education"),
                p("tuition_courses", "Tuition and courses", "tuition"),
                p("baby_supplies", "Baby supplies", "baby"),
                p("pet_supplies", "Pet food and supplies", "pets"),
                p("veterinary", "Veterinary care", "pets"),
                p("family_support", "Family support", "family"),
                p("gifts_celebrations", "Gifts and celebrations", "gift"),
            ),
        ),
        Group(
            "lifestyle", "Lifestyle and entertainment",
            listOf(
                p("streaming", "Streaming services", "streaming", recommended = true),
                p("digital_subscriptions", "Software and digital subscriptions", "subscriptions"),
                p("hobbies", "Hobbies", "hobbies"),
                p("games", "Games", "games"),
                p("books", "Books", "books"),
                p("movies_events", "Movies and events", "entertainment"),
                p("travel_holidays", "Travel and holidays", "travel"),
                p("hotels", "Hotels and accommodation", "hotel"),
                p("social", "Dining and social activities", "social"),
                p("outdoor", "Sports and outdoor activities", "outdoor"),
            ),
        ),
        Group(
            "obligations", "Financial obligations",
            listOf(
                p("loan_payments", "Loan payments", "loan"),
                p("credit_card_payments", "Credit card payments", "credit_card"),
                p("debt_repayment", "Debt repayment", "debt"),
                p("bank_fees", "Bank fees", "bank_fees"),
                p("financial_service_fees", "Financial service fees", "fees"),
                p("taxes", "Taxes", "tax"),
                p("legal_professional", "Legal and professional services", "legal"),
            ),
        ),
        Group(
            "savings", "Savings and financial goals",
            listOf(
                p("emergency_fund", "Emergency fund contributions", "emergency"),
                p("general_savings", "General savings", "savings"),
                p("retirement", "Retirement contributions", "retirement"),
                p("investment_contributions", "Investment contributions", "investing"),
                p("education_savings", "Education savings", "education"),
                p("home_purchase_savings", "Home purchase savings", "housing"),
                p("travel_savings", "Travel savings", "travel"),
                p("other_savings", "Other savings goals", "savings"),
            ),
        ),
        Group(
            "work", "Work and business",
            listOf(
                p("work_expenses", "Work expenses", "work_expenses"),
                p("home_office", "Home office", "home_office"),
                p("professional_memberships", "Professional memberships", "membership"),
                p("certifications_training", "Certifications and training", "certification"),
                p("business_travel", "Business travel", "business_travel"),
                p("business_supplies", "Business supplies", "supplies"),
            ),
        ),
        Group(
            "other", "Other",
            listOf(
                p("donations", "Donations and charity", "donation"),
                p("religious_community", "Religious or community contributions", "community"),
                p("miscellaneous", "Miscellaneous", "other", recommended = true),
            ),
        ),
    )

    val all: List<Preset> = groups.flatMap { it.presets }

    private val byKey: Map<String, Preset> = all.associateBy { it.key }

    fun preset(key: String): Preset? = byKey[key]

    fun groupOf(key: String): Group? = groups.firstOrNull { g -> g.presets.any { it.key == key } }

    /** A sensible starting selection: everyday essentials most people budget for. */
    val recommendedKeys: Set<String> = all.filter { it.recommended }.map { it.key }.toSet()

    /** Lower-case, trimmed, single-spaced, "&" read as "and" — so "Gifts & Celebrations" matches "Gifts and celebrations". */
    fun normalize(name: String): String =
        name.trim().lowercase(Locale.ROOT).replace("&", " and ").replace(Regex("\\s+"), " ").trim()
}

/**
 * What applying a category selection will do. Pure, so the rules are unit-tested:
 *
 * - [create]: selected presets / custom names that match no existing expense category (by normalised name).
 * - [restore]: archived categories whose name matches a selection — brought back with their id, history and budgets.
 * - [keep]: active categories that are selected (or match a selected preset) — untouched, ids unchanged.
 * - [deactivate]: active expense categories the user left unselected. The repository archives them when anything
 *   references them (transactions, budgets, schedules) and deletes them only when nothing does.
 */
data class CategorySelectionPlan(
    val create: List<DefaultCategories.Seed>,
    val restore: List<Long>,
    val keep: List<Long>,
    val deactivate: List<Long>,
) {
    val isEmpty: Boolean get() = create.isEmpty() && restore.isEmpty() && deactivate.isEmpty()
}

object CategorySelection {

    const val MAX_CUSTOM = 40

    /** A chosen category: either a preset (by key) or a custom name the user typed. */
    sealed interface Choice {
        val name: String
        val iconKey: String

        data class FromPreset(val preset: CategoryPresets.Preset) : Choice {
            override val name: String get() = preset.name
            override val iconKey: String get() = preset.iconKey
        }

        data class Custom(override val name: String) : Choice {
            override val iconKey: String get() = "other"
        }

        /** An existing category kept as-is (one that matches no preset). */
        data class Existing(val id: Long, override val name: String, override val iconKey: String) : Choice
    }

    /**
     * Which preset keys and existing custom categories should start ticked for someone who already has categories:
     * every active expense category is ticked (its matching preset when there is one, otherwise as itself).
     */
    fun initialSelection(existing: List<Category>): Pair<Set<String>, List<Category>> {
        val active = existing.filter { it.kind == CategoryKind.EXPENSE && !it.archived }
        val presetKeys = mutableSetOf<String>()
        val unmatched = mutableListOf<Category>()
        active.forEach { c ->
            val preset = CategoryPresets.all.firstOrNull { CategoryPresets.normalize(it.name) == CategoryPresets.normalize(c.name) }
            if (preset != null) presetKeys += preset.key else unmatched += c
        }
        return presetKeys to unmatched
    }

    /** Validates a custom name; returns the cleaned name or null when it is blank. Long names are shortened. */
    fun cleanCustomName(input: String): String? =
        input.trim().replace(Regex("\\s+"), " ").take(MAX_CUSTOM).trim().ifEmpty { null }

    fun plan(existing: List<Category>, choices: List<Choice>): CategorySelectionPlan {
        val expense = existing.filter { it.kind == CategoryKind.EXPENSE }
        val create = mutableListOf<DefaultCategories.Seed>()
        val restore = mutableListOf<Long>()
        val keep = mutableListOf<Long>()
        val seen = mutableSetOf<String>()

        for (choice in choices) {
            if (choice is Choice.Existing) {
                val match = expense.firstOrNull { it.id == choice.id } ?: continue
                seen += CategoryPresets.normalize(match.name)
                if (match.archived) restore += match.id else keep += match.id
                continue
            }
            val norm = CategoryPresets.normalize(choice.name)
            if (norm.isEmpty() || !seen.add(norm)) continue // duplicates in the selection collapse into one
            val active = expense.firstOrNull { !it.archived && CategoryPresets.normalize(it.name) == norm }
            val archived = expense.firstOrNull { it.archived && CategoryPresets.normalize(it.name) == norm }
            when {
                active != null -> keep += active.id
                archived != null -> restore += archived.id
                else -> create += DefaultCategories.Seed(choice.name.trim(), CategoryKind.EXPENSE, choice.iconKey)
            }
        }
        val kept = (keep + restore).toSet()
        val deactivate = expense.filter { !it.archived && it.id !in kept }.map { it.id }
        return CategorySelectionPlan(create, restore.distinct(), keep.distinct(), deactivate)
    }
}
