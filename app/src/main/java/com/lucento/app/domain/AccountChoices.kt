package com.lucento.app.domain

import java.text.Normalizer
import java.util.Locale

/** The four families every account belongs to, in the order pickers list them (tester round, 2026-11-17). */
enum class WalletFamily(val label: String) {
    EVERYDAY("Everyday money"),
    SET_ASIDE("Set aside and growing"),
    OWNED("Things you own"),
    OWED("What you owe"),
}

/** Which family a type belongs to; a new type must say (the `when` is exhaustive on purpose). */
val WalletType.family: WalletFamily
    get() = when (this) {
        WalletType.CASH, WalletType.BANK, WalletType.E_WALLET -> WalletFamily.EVERYDAY
        WalletType.SAVINGS, WalletType.INVESTMENT -> WalletFamily.SET_ASIDE
        WalletType.OTHER_ASSET, WalletType.RECEIVABLE -> WalletFamily.OWNED
        WalletType.CREDIT_CARD, WalletType.LOAN -> WalletFamily.OWED
    }

/**
 * What the account picker lists (tester round, 2026-11-17: with many wallets the transfer sheet's wrapping pills were
 * overwhelming). Pure, so it is tested on the JVM; the sheet itself is `AccountPickerSheet`.
 */
object AccountChoices {

    /** One group of the picker: [family] null is "Recent". */
    data class Section(val family: WalletFamily?, val wallets: List<Wallet>)

    /** How many recent accounts lead the list. */
    const val RECENT = 3

    /** Above this many accounts the picker offers a search box and leads with Recent. */
    const val SEARCH_FROM = 6

    /**
     * [wallets] grouped by [WalletFamily], each family's accounts in the app's type order and otherwise in their given
     * order. With more than [SEARCH_FROM] accounts and nothing searched, [recentIds] that are listed (newest first, at
     * most [RECENT]) lead as "Recent"; a shorter list is quick to scan, and repeating rows would only lengthen it. A
     * search matches the name, the kind's [typeLabel] or the [institutionName], ignoring case and accents; Recent is
     * left out.
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
        val byFamily = shown.groupBy { it.type.family }
        val groups = WalletFamily.entries.mapNotNull { family ->
            byFamily[family]?.let { Section(family, it.sortedBy { w -> w.type.ordinal }) }
        }
        if (needle.isNotEmpty() || wallets.size <= SEARCH_FROM) return groups
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
