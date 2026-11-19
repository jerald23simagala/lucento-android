package com.lucento.app.domain.entitlement

import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletType

/**
 * The plan decisions that are not a count in a repository (docs/MONETIZATION.md §5.2): pure functions, so ViewModels
 * and the application container call one line and every rule is a JVM test. Each takes the person's [EntitlementState]
 * and the clock; none reads anything else.
 */
object ProGates {
    /**
     * Whether this phone may sync at all under its plan. A Partner in a Shared Space syncs on the Owner's plan (the
     * Partner needs no plan of their own; the Owner's lapse is lenient — docs §3.7, §6.4), so membership alone allows it.
     */
    fun syncAllowedByPlan(cloud: Boolean, partnerOfSpace: Boolean): Boolean = cloud || partnerOfSpace

    fun syncAllowedByPlan(state: EntitlementState, partnerOfSpace: Boolean, now: Long): Boolean =
        syncAllowedByPlan(state.has(Capability.CLOUD, now), partnerOfSpace)

    /**
     * The plan rows that can carry CLOUD, as a stable string: source, product, state and end date of each — never a
     * verification time, which changes on every re-read. Sync compares it to decide whether an upload the server refused
     * is worth trying again before the cooldown ends (security audit F-01): a renewal or a new purchase changes it.
     */
    fun cloudPlanFingerprint(state: EntitlementState): String =
        state.entitlements
            .filter { Capability.CLOUD in it.grants }
            .map { "${it.source}/${it.productId}/${it.status}/${it.expiresAtMillis}/${it.purchaseTokenHash}" }
            .sorted()
            .joinToString(";")

    /** Restoring from the account on a new phone: the same rule as sync (a Partner joining their space always may). */
    fun restoreAllowedByPlan(state: EntitlementState, partnerOfSpace: Boolean, now: Long): Boolean =
        syncAllowedByPlan(state, partnerOfSpace, now)

    /** Insights periods: three months are free; six and twelve are Pro (docs §3.4). */
    fun insightsPeriodAllowed(months: Int, state: EntitlementState, now: Long): Boolean =
        months <= FREE_INSIGHTS_MONTHS || state.has(Capability.LOCAL_PRO, now)

    fun pdfAllowed(state: EntitlementState, now: Long): Boolean = state.has(Capability.LOCAL_PRO, now)

    /**
     * Smarter reading (Gemini reads the composer's messages) is an online service: Pro, or Lifetime's included year
     * (docs §3.6). Without it Lucento's server refuses every message, so the app neither offers it nor asks: each
     * message would only wait for the refusal before being read on the phone anyway (owner, 2026-11-15).
     */
    fun smartReadingAllowed(state: EntitlementState, now: Long): Boolean = state.has(ProFeature.SMART_READING.requires, now)

    /**
     * How the AI adviser's allowance runs, for what the app says about it (the server counts and decides, docs §3.6): per
     * day with AI_PRO (Pro, Lifetime's included year, the Cloud add-on), per calendar month on the free plan.
     */
    fun aiAllowancePerDay(state: EntitlementState, now: Long): Boolean = state.has(ProFeature.AI_ADVISER.requires, now)

    /**
     * Sunrise and Midnight are free; Grove, Mono and Aurora are Pro (docs §3.8). Theme ids are strings here so the domain
     * stays free of the preferences layer; callers pass [com.lucento.app.data.preferences.ThemeId.name].
     */
    val PRO_THEME_IDS: Set<String> = setOf("GROVE", "MONO", "AURORA")

    fun themeIsPro(themeId: String): Boolean = themeId in PRO_THEME_IDS

    /** A locked theme already chosen stays until the person changes it (§6.3); this only decides a new choice. */
    fun themeAllowed(themeId: String, state: EntitlementState, now: Long): Boolean =
        !themeIsPro(themeId) || state.has(Capability.LOCAL_PRO, now)

    fun lockedThemeIds(state: EntitlementState, now: Long): Set<String> =
        if (state.has(Capability.LOCAL_PRO, now)) emptySet() else PRO_THEME_IDS

    /** Which plan limit a wallet of [type] counts against (docs §3.2): money wallets and cards or loans are counted apart. */
    fun walletFeature(type: WalletType): ProFeature = when (type) {
        // An investment is money the person moves in and out, like savings (tester round, 2026-11-17).
        WalletType.CASH, WalletType.BANK, WalletType.E_WALLET, WalletType.SAVINGS, WalletType.INVESTMENT -> ProFeature.MONEY_WALLETS
        WalletType.CREDIT_CARD, WalletType.LOAN -> ProFeature.DEBT_ACCOUNTS
        WalletType.RECEIVABLE -> ProFeature.RECEIVABLES
        WalletType.OTHER_ASSET -> ProFeature.VALUED_ASSETS
    }

    /**
     * Whether the plan lets a wallet be [type] in [currencyCode], given the [active] wallets there are: a new one
     * ([editing] null), or an existing one changing kind (cash into a receivable; a card into a loan is the same kind), is
     * counted against the new kind; one moving into a currency other than [mainCurrency] is counted against wallets in
     * other currencies. The same rules as WalletRepository.create and update (docs/MONETIZATION.md §6.3), asked before the
     * form is filled in, so a limit meets the person at the tap that would cross it (owner, 2026-11-18) rather than at
     * Save. A null [mainCurrency] skips the currency check, as the repository does.
     */
    fun walletAccess(
        type: WalletType,
        currencyCode: String,
        editing: Wallet?,
        mainCurrency: String?,
        active: List<Wallet>,
        capabilities: Set<Capability>,
    ): Access {
        val kindChanges = editing == null || walletFeature(editing.type) != walletFeature(type)
        if (kindChanges) {
            val feature = walletFeature(type)
            val used = active.count { it.id != editing?.id && walletFeature(it.type) == feature }
            val byKind = EntitlementEvaluator.access(capabilities, feature, used)
            if (byKind != Access.Allowed) return byKind
        }
        val main = mainCurrency ?: return Access.Allowed
        val intoAnotherCurrency = currencyCode != main && (editing == null || editing.currencyCode != currencyCode)
        if (!intoAnotherCurrency) return Access.Allowed
        return EntitlementEvaluator.access(capabilities, ProFeature.FOREIGN_CURRENCY_WALLETS, active.count { it.currencyCode != main })
    }

    /** The wallet types a new wallet is offered as, in the order the editor lists the everyday ones first. */
    val NEW_WALLET_ORDER: List<WalletType> = listOf(
        WalletType.CASH, WalletType.BANK, WalletType.E_WALLET, WalletType.SAVINGS, WalletType.INVESTMENT,
        WalletType.CREDIT_CARD, WalletType.LOAN, WalletType.RECEIVABLE, WalletType.OTHER_ASSET,
    )

    /** The first of [order] a new wallet in [currencyCode] can be on the plan; null when none can (every kind is full). */
    fun firstAddableWalletType(
        order: List<WalletType>,
        currencyCode: String,
        mainCurrency: String?,
        active: List<Wallet>,
        capabilities: Set<Capability>,
    ): WalletType? = order.firstOrNull { walletAccess(it, currencyCode, null, mainCurrency, active, capabilities) == Access.Allowed }

    /** What Add wallet starts with (tester round 2026-11-18). */
    sealed interface AddWalletStart {
        /** The editor on this type: money wallets still fit, so the usual start. */
        data class Editor(val type: WalletType) : AddWalletStart

        /** Money wallets are full but another kind fits: the person picks the type, rather than a form that starts as a card. */
        data object ChooseType : AddWalletStart

        /** Nothing fits: the Pro dialog, with the money-wallet numbers and, [alsoFull], the cards-and-loans ones. */
        data class Wall(val access: Access, val alsoFull: Access? = null) : AddWalletStart
    }

    /** Add wallet in [mainCurrency] with the [active] wallets there are: the editor, the type sheet, or the wall. */
    fun addWalletStart(mainCurrency: String, active: List<Wallet>, capabilities: Set<Capability>): AddWalletStart =
        when (val first = firstAddableWalletType(NEW_WALLET_ORDER, mainCurrency, mainCurrency, active, capabilities)) {
            null -> AddWalletStart.Wall(
                access = walletAccess(NEW_WALLET_ORDER.first(), mainCurrency, null, mainCurrency, active, capabilities),
                alsoFull = walletAccess(WalletType.CREDIT_CARD, mainCurrency, null, mainCurrency, active, capabilities)
                    .takeIf { it != Access.Allowed },
            )
            NEW_WALLET_ORDER.first() -> AddWalletStart.Editor(first)
            else -> AddWalletStart.ChooseType
        }

    const val FREE_INSIGHTS_MONTHS = 3
}
