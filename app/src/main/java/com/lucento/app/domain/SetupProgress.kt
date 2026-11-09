package com.lucento.app.domain

/** How one [SetupStage] reads in the getting-started stepper. */
enum class StageStatus {
    /** Finished: drawn with a check. */
    COMPLETED,

    /** The stage on screen. */
    ACTIVE,

    /** Reached earlier in this run but not finished: the person went back to an earlier stage. */
    REACHED,

    /** Not reached yet. */
    UPCOMING,
}

/**
 * Where a person stands in getting started (onboarding, then the guided setup), from the flow's own state: the stage on
 * screen ([current]), the furthest stage reached in this run ([furthest]) — so going back to an earlier screen never
 * un-completes a stage — and how far through [current] they are ([progress], 0..1, display only).
 */
data class SetupProgress(
    val current: SetupStage,
    val furthest: SetupStage = current,
    val progress: Float = 0f,
    /** Which screen of [current] this is (1-based), for the path rail's "2 of 5"; 0 when not counted. */
    val screen: Int = 0,
    /** How many screens [current] has for this person (steps for modules they switched off don't count). */
    val screens: Int = 0,
) {
    /** Whether the rail can say "[screen] of [screens]". */
    val counted: Boolean get() = screens > 0 && screen in 1..screens

    /** The rail's caption: "Personalize · 2 of 5", or just the stage when screens aren't counted. */
    val caption: String get() = if (counted) "${current.label} · $screen of $screens" else current.label

    /** How full the current stage's segment is: the screens reached so far, or [progress] when not counted. */
    val fill: Float get() = if (counted) screen.toFloat() / screens else progress.coerceIn(0f, 1f)

    /** The furthest stage reached, never behind [current]. */
    val reached: SetupStage get() = maxOf(current, furthest)

    /** A stage is done once a later one has been reached; it keeps its check when the person goes back to it. */
    fun isDone(stage: SetupStage): Boolean = stage < reached

    fun status(stage: SetupStage): StageStatus = when {
        stage == current -> StageStatus.ACTIVE
        isDone(stage) -> StageStatus.COMPLETED
        stage == reached -> StageStatus.REACHED
        else -> StageStatus.UPCOMING
    }

    /**
     * How much of the line after [stage] is drawn (0..1, display only): all of it once the next stage has been reached;
     * on the line leading out of the current stage, that stage's own progress; nothing beyond.
     */
    fun connectorFill(stage: SetupStage): Float {
        val next = SetupStage.entries.getOrNull(stage.ordinal + 1) ?: return 0f
        return when {
            next <= reached -> 1f
            stage == current -> progress.coerceIn(0f, 1f)
            else -> 0f
        }
    }

    /** The stepper as one sentence for TalkBack: "Step 2 of 3, Set up. Personalize done." */
    val description: String
        get() {
            val done = SetupStage.entries.filter { isDone(it) && it != current }.map { it.label }
            return buildString {
                append("Step ${current.ordinal + 1} of ${SetupStage.entries.size}, ${current.label}.")
                if (done.isNotEmpty()) append(" ${done.joinToString(" and ")} done.")
                if (counted) append(" Screen $screen of $screens.")
            }
        }
}
