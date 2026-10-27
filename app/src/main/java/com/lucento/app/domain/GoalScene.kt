package com.lucento.app.domain

import java.util.Locale

/**
 * The picture a savings goal's card shows. Purely presentational: it never affects amounts, progress or ordering.
 * The user may choose one (stored as [key] on the goal); with no choice, [of] picks one from the goal's name.
 */
enum class GoalScene(val key: String) {
    /** Mount Fuji — a Japan trip. */
    JAPAN("japan_fuji"),

    /** Sunrise landscape — any other trip or holiday. */
    TRAVEL("sunrise"),

    /** Calm mountains — steady saving (an emergency fund, a cushion). Chosen only, never automatic. */
    MOUNTAINS("mountains"),

    /** The plain card, no picture. */
    NONE("none");

    companion object {
        private val japan = setOf("japan", "tokyo", "kyoto", "osaka", "fuji", "hokkaido", "okinawa", "nara", "日本")
        private val travel = setOf("trip", "travel", "vacation", "holiday", "holidays", "getaway", "journey", "flight", "flights")

        /** Automatic choice from the goal's name. */
        fun of(goalName: String): GoalScene {
            val lower = goalName.lowercase(Locale.ROOT)
            if ("日本" in lower) return JAPAN
            val words = lower.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }.toSet()
            return when {
                words.any { it in japan } -> JAPAN
                words.any { it in travel } -> TRAVEL
                else -> NONE
            }
        }

        /** The scene for a stored [key]; null for no choice or an unknown key. */
        fun fromKey(key: String?): GoalScene? = entries.firstOrNull { it.key == key }

        /** The user's choice wins; without one (or with an unknown key) the name decides. */
        fun forGoal(goalName: String, sceneKey: String?): GoalScene = fromKey(sceneKey) ?: of(goalName)
    }
}

/** An optional starting point for a new goal: it only fills in the name and picture; nothing is created by itself. */
data class GoalPreset(val name: String, val scene: GoalScene)

object GoalPresets {
    val all: List<GoalPreset> = listOf(
        GoalPreset("Trip to Japan", GoalScene.JAPAN),
        GoalPreset("Holiday", GoalScene.TRAVEL),
        GoalPreset("Emergency fund", GoalScene.MOUNTAINS),
        GoalPreset("New laptop", GoalScene.NONE),
    )
}
