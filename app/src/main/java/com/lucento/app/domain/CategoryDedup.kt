package com.lucento.app.domain

/**
 * Two setups on one sync account (say an offline install, then a second one signed in) each create their chosen
 * categories with their own ids, so the account ends up with two "Rent"s and the Budget screen shows the copy as an
 * unset row. This plans folding such copies together: live categories of the same kind and name (ignoring case and
 * surrounding spaces) merge into one keeper.
 *
 * The keeper is the copy with the smallest sync id, so every device of the account picks the same one and no two
 * devices delete each other's keeper. Archived categories are left alone: an archived and a live "Rent" are
 * different on purpose.
 */
object CategoryDedup {
    data class Candidate(val id: Long, val kind: String, val name: String, val archived: Boolean, val clientUuid: String?)

    /** Loser id → keeper id. */
    fun plan(categories: List<Candidate>): Map<Long, Long> = categories
        .filter { !it.archived && it.clientUuid != null }
        .groupBy { it.kind to it.name.trim().lowercase() }
        .values
        .filter { it.size > 1 }
        .flatMap { copies ->
            val keeper = copies.minBy { it.clientUuid!! }
            copies.filter { it.id != keeper.id }.map { it.id to keeper.id }
        }
        .toMap()

    /** One copy's budget for the same category and currency, as the fold sees it. */
    data class Limit(val minor: Long, val updatedAt: Long, val clientUuid: String?)

    /**
     * The budget amount the keeper ends with when both copies had one in the same currency (P1-8, owner decision:
     * **keep the latest edit**). A set amount is never replaced by an unset (0) one; otherwise the amount edited most
     * recently wins, and an exact tie goes to the smaller sync id — the same answer on every device from the same rows.
     * See docs/CALCULATIONS.md "Budget when categories are combined".
     */
    fun mergedLimit(keeper: Limit, copy: Limit): Long = winner(keeper, copy).minor

    /**
     * The whole winning [Limit], so a fold over three or more copies compares each next copy with the winner's *original*
     * edit time, not the time of the merge (QA note 1). The rule is a pure selection, so the result doesn't depend on
     * the order the copies are folded in — devices that see them in a different order still agree.
     */
    fun winner(keeper: Limit, copy: Limit): Limit = when {
        keeper.minor == 0L -> copy
        copy.minor == 0L -> keeper
        copy.updatedAt != keeper.updatedAt -> if (copy.updatedAt > keeper.updatedAt) copy else keeper
        else -> listOf(keeper, copy).minWith(compareBy(nullsLast()) { it.clientUuid })
    }
}
