package com.lucento.app.domain

/**
 * How someone pays, asked once in onboarding ("How do you pay?"). It replaces a list of feature switches with a
 * question people can answer: credit cards and loans turn Debts & loans on, and the guided setup suggests these
 * account types first. Every choice can be changed later; nothing here touches money.
 */
enum class SpendType(val title: String, val description: String, val walletType: WalletType, val features: Set<Feature>) {
    CASH("Cash", "Notes and coins", WalletType.CASH, emptySet()),
    BANK("Bank account or debit card", "Salary, transfers, card payments", WalletType.BANK, emptySet()),
    E_WALLET("E-wallet", "Mobile wallets and payment apps", WalletType.E_WALLET, emptySet()),
    CREDIT_CARD("Credit card", "Buy now, pay the card later", WalletType.CREDIT_CARD, setOf(Feature.DEBTS_LOANS)),
    LOAN("Loans", "Car, home, personal or student loans", WalletType.LOAN, setOf(Feature.DEBTS_LOANS));

    companion object {
        /** Preselected: most people pay with cash and a bank account or debit card. */
        val DEFAULT: Set<SpendType> = setOf(CASH, BANK)

        fun features(types: Set<SpendType>): Set<Feature> = types.flatMap { it.features }.toSet()

        /** The account the guided setup suggests first: bank, then e-wallet, then cash (bank when nothing fits). */
        fun firstAssetType(types: Set<SpendType>): WalletType =
            listOf(BANK, E_WALLET, CASH).firstOrNull { it in types }?.walletType ?: WalletType.BANK

        /** The debt the guided setup suggests first: a loan only when loans were chosen and credit cards weren't. */
        fun firstDebtType(types: Set<SpendType>): WalletType =
            if (LOAN in types && CREDIT_CARD !in types) WalletType.LOAN else WalletType.CREDIT_CARD

        fun fromKeys(keys: Collection<String>): Set<SpendType> = keys.mapNotNull { k -> entries.firstOrNull { it.name == k } }.toSet()
    }
}

/**
 * The three stages people see while getting started: shaping Lucento to them, adding their own accounts and amounts,
 * then using it for real. Each onboarding and guided-setup screen belongs to exactly one.
 */
enum class SetupStage(val label: String) {
    PERSONALIZE("Personalize"),
    SET_UP("Set up"),
    GET_STARTED("Get started"),
}
