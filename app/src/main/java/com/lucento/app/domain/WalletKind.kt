package com.lucento.app.domain

/**
 * What kind of investment, owned thing or loan a wallet is (tester round, 2026-11-17: "Investments in Stocks or MP2s and
 * the likes"). A label, a default icon and a group in Net worth — it never changes how money is counted. Stored as [key];
 * a key this version doesn't know (from a newer one), or one that belongs to another type, reads as no kind.
 */
enum class WalletKind(val key: String, val type: WalletType, val label: String, val defaultIcon: WalletIcon) {
    STOCKS("stocks", WalletType.INVESTMENT, "Stocks", WalletIcon.CHART),
    FUNDS("funds", WalletType.INVESTMENT, "Mutual fund or UITF", WalletIcon.PIE),
    MP2("mp2", WalletType.INVESTMENT, "Pag-IBIG MP2", WalletIcon.SPROUT),
    TIME_DEPOSIT("time_deposit", WalletType.INVESTMENT, "Time deposit", WalletIcon.LOCK),
    BONDS("bonds", WalletType.INVESTMENT, "Bonds", WalletIcon.CERTIFICATE),
    CRYPTO("crypto", WalletType.INVESTMENT, "Crypto", WalletIcon.CRYPTO),
    RETIREMENT("retirement", WalletType.INVESTMENT, "Retirement fund", WalletIcon.RETIREMENT),
    INSURANCE("insurance", WalletType.INVESTMENT, "Insurance with cash value", WalletIcon.SHIELD),
    COOP("coop", WalletType.INVESTMENT, "Cooperative shares", WalletIcon.GROUP),
    OTHER_INVESTMENT("other_investment", WalletType.INVESTMENT, "Other investment", WalletIcon.RISING),

    PROPERTY("property", WalletType.OTHER_ASSET, "Property", WalletIcon.HOUSE),
    VEHICLE("vehicle", WalletType.OTHER_ASSET, "Vehicle", WalletIcon.CAR),
    VALUABLES("valuables", WalletType.OTHER_ASSET, "Valuables", WalletIcon.GEM),
    BUSINESS("business", WalletType.OTHER_ASSET, "Business", WalletIcon.STORE),
    OTHER_OWNED("other_owned", WalletType.OTHER_ASSET, "Something else", WalletIcon.BOX),

    PERSONAL_LOAN("personal_loan", WalletType.LOAN, "Personal loan", WalletIcon.DOCUMENT),
    CAR_LOAN("car_loan", WalletType.LOAN, "Car loan", WalletIcon.CAR),
    HOME_LOAN("home_loan", WalletType.LOAN, "Home loan", WalletIcon.HOUSE),
    STUDENT_LOAN("student_loan", WalletType.LOAN, "Student loan", WalletIcon.SCHOOL),
    SALARY_LOAN("salary_loan", WalletType.LOAN, "Salary loan", WalletIcon.WORK),
    BNPL("bnpl", WalletType.LOAN, "Buy now, pay later", WalletIcon.BAG),
    BORROWED("borrowed", WalletType.LOAN, "Borrowed from someone", WalletIcon.HANDSHAKE),
    ;

    companion object {
        /** The kinds a wallet of [type] can be, in picker order; empty for types without kinds. */
        fun of(type: WalletType): List<WalletKind> = entries.filter { it.type == type }

        /** The kind stored as [key] when it belongs to [type]; null for none, an unknown key or another type's kind. */
        fun fromKey(key: String?, type: WalletType): WalletKind? = entries.firstOrNull { it.key == key && it.type == type }

        /** What a stored kind or icon key looks like (sync and backup refuse anything else). */
        val KEY_PATTERN = Regex("^[a-z0-9_]{1,32}$")
    }
}

/**
 * The icon a wallet without a bank or provider logo shows (tester round, 2026-11-17). Purely presentational; stored as
 * [key]. Without a choice a wallet shows its kind's [WalletKind.defaultIcon], or its type's own icon.
 */
enum class WalletIcon(val key: String, val label: String, val group: Group) {
    PIGGY("piggy", "Piggy bank", Group.SAVING),
    CHART("chart", "Chart", Group.SAVING),
    RISING("rising", "Rising line", Group.SAVING),
    PIE("pie", "Pie chart", Group.SAVING),
    SPROUT("sprout", "Sprout", Group.SAVING),
    LOCK("lock", "Lock", Group.SAVING),
    CERTIFICATE("certificate", "Certificate", Group.SAVING),
    CRYPTO("crypto", "Crypto", Group.SAVING),
    COIN("coin", "Coin", Group.SAVING),
    RETIREMENT("retirement", "Retirement", Group.SAVING),
    SHIELD("shield", "Shield", Group.SAVING),
    GROUP("group", "Group", Group.SAVING),
    BANK("bank", "Bank", Group.SAVING),

    HOUSE("house", "House", Group.OWNED),
    APARTMENT("apartment", "Apartment", Group.OWNED),
    LAND("land", "Land", Group.OWNED),
    FARM("farm", "Farm", Group.OWNED),
    CAR("car", "Car", Group.OWNED),
    MOTORBIKE("motorbike", "Motorbike", Group.OWNED),
    GEM("gem", "Gem", Group.OWNED),
    WATCH("watch", "Watch", Group.OWNED),
    LAPTOP("laptop", "Laptop", Group.OWNED),
    STORE("store", "Store", Group.OWNED),
    BOX("box", "Box", Group.OWNED),

    CASH("cash", "Cash", Group.EVERYDAY),
    WALLET("wallet", "Wallet", Group.EVERYDAY),
    CARD("card", "Card", Group.EVERYDAY),
    PHONE("phone", "Phone", Group.EVERYDAY),
    ATM("atm", "ATM", Group.EVERYDAY),
    GIFT("gift", "Gift", Group.EVERYDAY),
    FAMILY("family", "Family", Group.EVERYDAY),
    SCHOOL("school", "School", Group.EVERYDAY),
    TRAVEL("travel", "Travel", Group.EVERYDAY),
    WORK("work", "Work", Group.EVERYDAY),
    BAG("bag", "Shopping bag", Group.EVERYDAY),
    HANDSHAKE("handshake", "Handshake", Group.EVERYDAY),
    DOCUMENT("document", "Document", Group.EVERYDAY),
    ;

    enum class Group(val label: String) { SAVING("Saving and investing"), OWNED("Things you own"), EVERYDAY("Everyday") }

    companion object {
        fun fromKey(key: String?): WalletIcon? = entries.firstOrNull { it.key == key }
    }
}
