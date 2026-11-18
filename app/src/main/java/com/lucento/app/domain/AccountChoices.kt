package com.lucento.app.domain

import java.text.Normalizer
import java.util.Locale

/**
 * What the account picker lists (tester round, 2026-11-17: with many wallets the transfer sheet's wrapping pills were
 * overwhelming). Pure, so it is tested on the JVM; the sheet itself is `AccountPickerSheet`.
 */
object AccountChoices {

    /** One group of the picker: [type] null is "Recent". */
    data class Section(val type: WalletType?, val wallets: List<Wallet>)

    /** How many recent accounts lead the list. */
    const val RECENT = 3

    /** Above this many accounts the picker offers a search box. */
    const val SEARCH_FROM = 6

    /**
     * [wallets] grouped by kind in the app's own order ([WalletType] entries), keeping their order inside a group. While
     * nothing is searched, [recentIds] that are listed (newest first, at most [RECENT]) lead as "Recent". A search
     * matches the name, the kind's [typeLabel] or the [institutionName], ignoring case and accents; Recent is left out.
     */
    fun sections(
        wallets: List<Wallet>,
        recentIds: List<Long>,
        query: String,
        typeLabel: (WalletType) -> String,
        institutionName: (Wallet) -> String? = { null },
    ): List<Section> {
        val needle = fold(query.trim())
        val shown = if (needle.isEmpty()) wallets else wallets.filter { w ->
            listOfNotNull(w.name, typeLabel(w.type), institutionName(w)).any { needle in fold(it) }
        }
        val byType = shown.groupBy { it.type }
        val groups = WalletType.entries.mapNotNull { type -> byType[type]?.let { Section(type, it) } }
        if (needle.isNotEmpty()) return groups
        val byId = wallets.associateBy { it.id }
        val recent = recentIds.distinct().mapNotNull { byId[it] }.take(RECENT)
        return if (recent.isEmpty()) groups else listOf(Section(null, recent)) + groups
    }

    /**
     * Where money last went from [fromId] (from any account when null), newest first, without repeats or [fromId]
     * itself. Newest is the latest date, then the latest saved.
     */
    fun recentDestinations(transfers: Collection<Transfer>, fromId: Long?, limit: Int = RECENT): List<Long> =
        newestFirst(transfers)
            .filter { fromId == null || it.fromWalletId == fromId }
            .map { it.toWalletId }
            .filter { it != fromId }
            .distinct()
            .take(limit)

    /** Where money last came from, newest first, without repeats. */
    fun recentSources(transfers: Collection<Transfer>, limit: Int = RECENT): List<Long> =
        newestFirst(transfers).map { it.fromWalletId }.distinct().take(limit)

    private fun newestFirst(transfers: Collection<Transfer>): List<Transfer> =
        transfers.sortedWith(compareByDescending<Transfer> { it.date }.thenByDescending { it.outLeg.id })

    private val MARKS = Regex("\\p{Mn}+")

    private fun fold(text: String): String =
        MARKS.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase(Locale.ROOT)
}
