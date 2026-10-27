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

    /**
     * The budget amount the keeper ends with when both copies had one in the same currency: the keeper's, unless it is
     * zero and the copy's is not — a set amount is never replaced by an unset one.
     */
    fun mergedLimit(keeperMinor: Long, copyMinor: Long): Long = if (keeperMinor == 0L) copyMinor else keeperMinor
}
