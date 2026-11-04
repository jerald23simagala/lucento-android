package com.lucento.app.domain

import java.util.Locale

/**
 * The icon a savings goal shows (owner, 2026-11-04: preset icons replace the scene pictures; the user's own photo stays
 * an option). Purely presentational. Stored in the goal's existing scene key as [key] ("icon_travel"), so nothing in
 * the database, sync or backup changes; the older scene keys map to the closest icon ([fromKey]). With no choice the
 * goal's name decides ([of]).
 */
enum class GoalIcon(val id: String, val label: String) {
    SAVINGS("savings", "Savings"),
    EMERGENCY("emergency", "Emergency"),
    TRAVEL("travel", "Travel"),
    HOME("home", "Home"),
    CAR("car", "Car"),
    EDUCATION("education", "Education"),
    GADGET("gadget", "Gadget"),
    WEDDING("wedding", "Wedding"),
    FAMILY("family", "Family"),
    HEALTH("health", "Health"),
    GIFT("gift", "Gifts"),
    INVEST("invest", "Investing"),
    ;

    /** What is stored in the goal's scene key. */
    val key: String get() = "$PREFIX$id"

    companion object {
        private const val PREFIX = "icon_"

        /** Words that point at each icon, checked in this order (a "honeymoon" is travel, a "wedding fund" a wedding). */
        private val words: List<Pair<GoalIcon, Set<String>>> = listOf(
            TRAVEL to setOf(
                "trip", "travel", "vacation", "holiday", "holidays", "getaway", "journey", "flight", "flights", "honeymoon",
                "japan", "tokyo", "kyoto", "osaka", "fuji", "europe", "paris", "beach", "tour", "abroad",
            ),
            WEDDING to setOf("wedding", "engagement", "ring", "marriage"),
            EMERGENCY to setOf("emergency", "rainy", "cushion", "buffer", "safety", "insurance"),
            HOME to setOf("house", "home", "apartment", "condo", "flat", "rent", "downpayment", "mortgage", "renovation", "furniture"),
            CAR to setOf("car", "auto", "vehicle", "motorcycle", "motorbike", "bike", "scooter"),
            EDUCATION to setOf("school", "tuition", "college", "university", "education", "course", "studies", "degree", "masters"),
            GADGET to setOf("laptop", "phone", "iphone", "computer", "pc", "camera", "tablet", "ipad", "console", "gadget", "tv", "macbook"),
            FAMILY to setOf("baby", "child", "children", "kid", "kids", "family", "parents"),
            HEALTH to setOf("health", "medical", "hospital", "dental", "dentist", "surgery", "doctor", "therapy"),
            GIFT to setOf("gift", "gifts", "christmas", "birthday", "present", "presents", "anniversary"),
            INVEST to setOf("invest", "investment", "investing", "retirement", "pension", "stocks", "future", "business"),
        )

        /** Automatic choice from the goal's name; [SAVINGS] when nothing matches. */
        fun of(goalName: String): GoalIcon {
            val lower = goalName.lowercase(Locale.ROOT)
            if ("日本" in lower) return TRAVEL
            val given = lower.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }.toSet()
            return words.firstOrNull { (_, set) -> given.any { it in set } }?.first ?: SAVINGS
        }

        /**
         * The icon for a stored key: an "icon_…" key, or one of the older scene keys (Mount Fuji and the sunrise were
         * trips, the mountains steady saving, "none" the plain card). Null for no choice or an unknown key.
         */
        fun fromKey(key: String?): GoalIcon? = when {
            key == null -> null
            key.startsWith(PREFIX) -> entries.firstOrNull { it.id == key.removePrefix(PREFIX) }
            else -> when (GoalScene.fromKey(key)) {
                GoalScene.JAPAN, GoalScene.TRAVEL -> TRAVEL
                GoalScene.MOUNTAINS -> EMERGENCY
                GoalScene.NONE -> SAVINGS
                null -> null
            }
        }

        /** A key worth storing: an icon key or a known older scene key; anything else is stored as automatic (null). */
        fun normalizeKey(key: String?): String? = key?.takeIf { fromKey(it) != null }

        /** The user's choice wins; without one (or with an unknown key) the name decides. */
        fun forGoal(goalName: String, key: String?): GoalIcon = fromKey(key) ?: of(goalName)
    }
}

/** An optional starting point for a new goal: it fills in the name and icon only; nothing is created by itself. */
data class GoalSuggestion(val name: String, val icon: GoalIcon)

object GoalSuggestions {
    val all: List<GoalSuggestion> = listOf(
        GoalSuggestion("Emergency fund", GoalIcon.EMERGENCY),
        GoalSuggestion("Holiday trip", GoalIcon.TRAVEL),
        GoalSuggestion("New laptop", GoalIcon.GADGET),
        GoalSuggestion("Home down payment", GoalIcon.HOME),
        GoalSuggestion("New car", GoalIcon.CAR),
        GoalSuggestion("Education", GoalIcon.EDUCATION),
        GoalSuggestion("Wedding", GoalIcon.WEDDING),
        GoalSuggestion("Gifts", GoalIcon.GIFT),
    )
}
