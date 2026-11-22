package com.lucento.app.domain.capture.merchant

import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletType

/**
 * The person's own account behind a name on a document: their GCash wallet for a GCash confirmation, their Amex card
 * for "AMEX ****1004", their BPI account for "Cash in via BPI". A wallet is known by its own name and, when it has a
 * bank logo, by that institution's name and aliases ([labels], built on the phone from the bundled registry). Words
 * that name a kind of account rather than one account ("Cash", "Savings", "Wallet") never match on their own.
 */
object WalletMatcher {

    private val GENERIC = setOf(
        "cash", "wallet", "bank", "card", "credit", "debit", "savings", "saving", "checking", "current", "main", "account", "my", "personal",
        "money", "ewallet", "e-wallet", "e", "the", "and", "of", "visa", "mastercard", "debit card", "credit card", "joint", "business",
    )

    /** Every name a wallet is known by, normalized, generic-only names left out. */
    private fun namesOf(wallet: Wallet, labels: Map<Long, List<String>>): List<String> =
        (listOf(wallet.name) + labels[wallet.id].orEmpty())
            .map { MerchantDirectory.normalize(it) }
            .filter { name -> name.length >= 3 && name.split(' ').any { it !in GENERIC } }
            .distinct()

    private fun containsWords(text: String, words: String): Boolean =
        Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(words) + "(?![\\p{L}\\p{N}])").containsMatchIn(text)

    /** Whether one of [wallet]'s names is [entity] (or the entity's alias appears as words of the name). */
    private fun isEntity(wallet: Wallet, entity: FinancialEntity, labels: Map<Long, List<String>>): Boolean {
        val names = namesOf(wallet, labels)
        val aliases = (entity.aliases + entity.weakAliases + entity.name).map { MerchantDirectory.normalize(it) }.filter { it.length >= 3 }
        return names.any { name -> aliases.any { alias -> name == alias || containsWords(name, alias) } }
    }

    private fun suits(wallet: Wallet, entity: FinancialEntity): Boolean = when (entity.kind) {
        EntityKind.EWALLET -> wallet.type == WalletType.E_WALLET || wallet.type == WalletType.BANK
        EntityKind.CARD_NETWORK -> wallet.type == WalletType.CREDIT_CARD || wallet.type == WalletType.BANK
        EntityKind.BANK -> wallet.type != WalletType.CASH && wallet.type != WalletType.E_WALLET
        else -> true
    }

    /**
     * The person's wallet for [entity], in [currency] when known; [last4] (printed on the receipt or statement) picks
     * between several cards. Null when none matches, or when several do and nothing tells them apart.
     */
    fun forEntity(
        entity: FinancialEntity,
        wallets: List<Wallet>,
        labels: Map<Long, List<String>>,
        currency: String? = null,
        last4: String? = null,
        type: WalletType? = null,
    ): Wallet? {
        val matching = wallets.filter { (currency == null || it.currencyCode == currency) && isEntity(it, entity, labels) && suits(it, entity) }
        if (matching.size <= 1) return matching.singleOrNull()
        last4?.let { digits ->
            matching.filter { w -> (listOf(w.name) + labels[w.id].orEmpty()).any { it.contains(digits) } }.singleOrNull()?.let { return it }
        }
        type?.let { t -> matching.filter { it.type == t }.singleOrNull()?.let { return it } }
        return null
    }

    /**
     * Another of the person's wallets named in [text] (a statement line, a confirmation): by its own name or its bank's,
     * or by a payment provider's name the [directory] recognises. [exclude] is the account the document belongs to.
     * The longest name wins; null when none, or when the words match two wallets equally.
     */
    fun named(
        text: String,
        wallets: List<Wallet>,
        labels: Map<Long, List<String>>,
        directory: MerchantDirectory,
        exclude: Long?,
        currency: String?,
    ): Wallet? {
        val s = MerchantDirectory.normalize(text)
            // Terms, not account names: "cash in", "cash back", "cash advance", "cash withdrawal".
            .replace(Regex("\\bcash[- ]?(in|out|back|advance|withdrawal)\\b"), " ")
        val pool = wallets.filter { it.id != exclude && (currency == null || it.currencyCode == currency) }
        val byName = pool.mapNotNull { w -> namesOf(w, labels).filter { containsWords(s, it) }.maxByOrNull { it.length }?.let { w to it.length } }
        val best = byName.maxOfOrNull { it.second }
        if (best != null) {
            val top = byName.filter { it.second == best }
            if (top.size == 1) return top.single().first
        }
        // A provider named on the line ("INSTAPAY TO BDO", "GCASH CASH IN") → the person's wallet with that provider.
        val providers = directory.mentions(s).filter { it.entity.kind.isInstrument && !it.weak }.map { it.entity }.distinct()
        return providers.firstNotNullOfOrNull { e -> forEntity(e, pool, labels) }
    }
}
