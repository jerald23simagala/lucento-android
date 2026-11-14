package com.lucento.app.feature.pro

import com.lucento.app.data.billing.BillingProblem
import com.lucento.app.data.billing.ProductPrice
import com.lucento.app.data.billing.PurchaseResult
import com.lucento.app.data.billing.VerifyOutcome
import com.lucento.app.data.entitlement.RefreshOutcome
import com.lucento.app.data.entitlement.ReleaseOutcome
import com.lucento.app.domain.entitlement.Capability
import com.lucento.app.domain.entitlement.ComparisonRow
import com.lucento.app.domain.entitlement.DisplayPlan
import com.lucento.app.domain.entitlement.Entitlement
import com.lucento.app.domain.entitlement.EntitlementSource
import com.lucento.app.domain.entitlement.EntitlementState
import com.lucento.app.domain.entitlement.EntitlementStatus
import com.lucento.app.domain.entitlement.Limit
import com.lucento.app.domain.entitlement.PlanCatalog
import com.lucento.app.domain.entitlement.PlanNotice
import com.lucento.app.domain.entitlement.PlayProducts
import com.lucento.app.domain.entitlement.ProFeature
import com.lucento.app.domain.entitlement.Verifier
import java.time.Instant
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Every customer-facing sentence about plans (docs/monetization/CUSTOMER_COPY.md), worked out without Compose so it is
 * unit-tested. Numbers come from [PlanCatalog]; prices only ever from Google Play ([ProductPrice]).
 */
object ProCopy {

    // ------------------------------------------------------------------------------------------------ features

    fun featureName(feature: ProFeature): String = when (feature) {
        ProFeature.MONEY_WALLETS -> "Money wallets"
        ProFeature.DEBT_ACCOUNTS -> "Cards and loans"
        ProFeature.RECEIVABLES -> "Money owed to you"
        ProFeature.VALUED_ASSETS -> "Things you own, at a value you set"
        ProFeature.FOREIGN_CURRENCY_WALLETS -> "Wallets in other currencies"
        ProFeature.SAVINGS_GOALS -> "Active savings goals"
        ProFeature.INSIGHTS_LONG_PERIODS -> "Insights over 6 and 12 months"
        ProFeature.PDF_STATEMENTS -> "Statements as PDF"
        ProFeature.EXTRA_THEMES -> "Themes"
        ProFeature.CLOUD_SYNC -> "Cloud sync between phones"
        ProFeature.CLOUD_RESTORE -> "Restore from your account"
        ProFeature.AI_ADVISER -> "AI adviser questions"
        ProFeature.SMART_READING -> "Smarter reading"
        ProFeature.SHARED_SPACE -> "Lucento Shared"
    }

    /** Features counted in items, where "no limit" reads better than "included". */
    private val COUNTED = setOf(ProFeature.MONEY_WALLETS, ProFeature.DEBT_ACCOUNTS, ProFeature.SAVINGS_GOALS)

    /** One plan's allowance for [feature], in words: "3", "No limit", "10 a month", "Last 3 months". */
    fun cell(feature: ProFeature, limit: Limit): String = when (feature) {
        ProFeature.INSIGHTS_LONG_PERIODS -> if (limit == Limit.None) "Last 3 months" else "Included"
        ProFeature.EXTRA_THEMES -> if (limit == Limit.None) "Sunrise and Midnight" else "All five"
        ProFeature.SHARED_SPACE -> when (limit) {
            Limit.None -> "Not included"
            is Limit.Count -> if (limit.max == 1) "1 Partner" else "${limit.max} Partners"
            else -> "Included"
        }
        else -> when (limit) {
            Limit.Unlimited -> if (feature in COUNTED) "No limit" else "Included"
            Limit.None -> "Not included"
            is Limit.Count -> "${limit.max}"
            is Limit.PerMonth -> "${limit.max} a month"
            is Limit.PerDay -> "${limit.max} a day"
        }
    }

    /**
     * The Lifetime column: what is owned outright for local features; for an online service, that it comes with the first
     * year and then with Lucento Cloud — never promised forever (docs/MONETIZATION.md §0).
     */
    fun lifetimeCell(row: ComparisonRow): String = when {
        row.feature.requires == Capability.LOCAL_PRO -> cell(row.feature, row.lifetime)
        row.feature == ProFeature.AI_ADVISER ->
            "${cell(row.feature, row.lifetimeWithServices)} the first year, then ${cell(row.feature, row.lifetime)}"
        else -> "First year, then with Lucento Cloud"
    }

    // ------------------------------------------------------------------------------------------------ comparison table

    /** One cell of the Free / Pro / Lifetime table: a tick, a dash, or a short value. TalkBack reads the row's long form. */
    sealed interface TableCell {
        data object Included : TableCell
        data object NotIncluded : TableCell
        data class Value(val text: String) : TableCell
    }

    /** The table's short form of [cell]: "3", "No limit", "10/mo", "50/day", a tick or a dash. */
    fun tableCell(feature: ProFeature, limit: Limit): TableCell = when (feature) {
        ProFeature.EXTRA_THEMES -> TableCell.Value(if (limit == Limit.None) "2" else "All 5")
        ProFeature.SHARED_SPACE -> when (limit) {
            Limit.None -> TableCell.NotIncluded
            is Limit.Count -> TableCell.Value(if (limit.max == 1) "1 Partner" else "${limit.max} Partners")
            else -> TableCell.Included
        }
        else -> when (limit) {
            Limit.Unlimited -> if (feature in COUNTED) TableCell.Value("No limit") else TableCell.Included
            Limit.None -> TableCell.NotIncluded
            is Limit.Count -> TableCell.Value("${limit.max}")
            is Limit.PerMonth -> TableCell.Value("${limit.max}/mo")
            is Limit.PerDay -> TableCell.Value("${limit.max}/day")
        }
    }

    /** The Lifetime column: owned outright on the phone; online services are marked [FIRST_YEAR] and explained once below. */
    fun lifetimeTableCell(row: ComparisonRow): TableCell =
        if (row.feature.requires == Capability.LOCAL_PRO) tableCell(row.feature, row.lifetime) else TableCell.Value(FIRST_YEAR)

    const val FIRST_YEAR = "Year 1"

    /** The footnote under the table: what [FIRST_YEAR] means, never promising online services forever (§0). */
    val FIRST_YEAR_NOTE: String =
        "$FIRST_YEAR: included for your first year, then with the optional Lucento Cloud. Without it, the AI adviser keeps the " +
            "free plan’s ${PlanCatalog.FREE_AI_QUESTIONS_PER_MONTH} questions a month. Everything else in Lifetime is yours for good."

    /** The table's two groups: what runs on the phone, then the online services. */
    fun comparisonGroups(rows: List<ComparisonRow>): List<Pair<String, List<ComparisonRow>>> {
        val (local, online) = rows.partition { it.feature.requires == Capability.LOCAL_PRO }
        return listOf("On this phone" to local, "Online" to online).filter { it.second.isNotEmpty() }
    }

    /** What TalkBack reads for one table row: every plan's allowance in words. */
    fun comparisonDescription(row: ComparisonRow): String =
        "${featureName(row.feature)}. Free: ${cell(row.feature, row.free)}. Pro: ${cell(row.feature, row.pro)}. " +
            "Lifetime: ${lifetimeCell(row)}."

    // ------------------------------------------------------------------------------------------------ plan cards

    /** A plan card's promise: one line, then what it brings, in the order people care about. */
    data class PlanPitch(val tagline: String, val benefits: List<String>)

    fun pitch(kind: PlanCardKind): PlanPitch = when (kind) {
        PlanCardKind.PRO -> PlanPitch(
            "Everything in Lucento, on every phone you use.",
            listOf(
                "Cloud sync between your phones",
                "Lucento Shared with a Partner",
                "${PlanCatalog.PRO_AI_QUESTIONS_PER_DAY} AI adviser questions a day",
                "Unlimited wallets, cards and loans",
                "12-month Insights, PDF statements and all five themes",
            ),
        )
        PlanCardKind.LIFETIME -> PlanPitch(
            "Pay once. Yours for good.",
            listOf(
                "Every Pro feature that runs on your phone, for life",
                "No renewals, and it works offline",
                "Cloud sync, Lucento Shared and the AI adviser included for the first year",
            ),
        )
        PlanCardKind.CLOUD -> PlanPitch(
            "Keep your online services going.",
            listOf(
                "Cloud sync and restoring from your account",
                "Lucento Shared with a Partner",
                "${PlanCatalog.PRO_AI_QUESTIONS_PER_DAY} AI adviser questions a day",
            ),
        )
    }

    /** Why [feature] isn't available on this plan, and what is still free: the sentence in every lock note. */
    fun lockedLine(feature: ProFeature): String = when (feature) {
        ProFeature.MONEY_WALLETS ->
            "The free plan has ${PlanCatalog.FREE_MONEY_WALLETS} money wallets. Lucento Pro and Pro Lifetime have no limit."
        ProFeature.DEBT_ACCOUNTS ->
            "The free plan tracks ${PlanCatalog.FREE_DEBT_ACCOUNTS} cards or loans. Pro and Pro Lifetime track as many as you have."
        ProFeature.RECEIVABLES -> "Tracking money someone owes you is part of Lucento Pro."
        ProFeature.VALUED_ASSETS -> "Adding things you own at a value you set is part of Lucento Pro."
        ProFeature.FOREIGN_CURRENCY_WALLETS ->
            "Wallets in other currencies are part of Lucento Pro. A wallet in your main currency is always free."
        ProFeature.SAVINGS_GOALS ->
            "The free plan has ${PlanCatalog.FREE_ACTIVE_GOALS} active savings goals. Pro and Pro Lifetime allow ${PlanCatalog.PRO_ACTIVE_GOALS}."
        ProFeature.INSIGHTS_LONG_PERIODS -> "Trends over 6 and 12 months are part of Lucento Pro. Your last 3 months are always free."
        ProFeature.PDF_STATEMENTS ->
            "Saving or sharing a statement as a PDF is part of Lucento Pro. Everything on this screen is always free."
        ProFeature.EXTRA_THEMES -> "Grove, Mono and Aurora are part of Lucento Pro. Sunrise and Midnight are always free."
        ProFeature.CLOUD_SYNC ->
            "Cloud sync is part of Lucento Pro. Your data stays on this phone whatever you choose, and a backup file is always free."
        ProFeature.CLOUD_RESTORE -> "Restoring from your account is part of Lucento Pro. Restoring a backup file is always free."
        ProFeature.AI_ADVISER ->
            "The free plan includes ${PlanCatalog.FREE_AI_QUESTIONS_PER_MONTH} AI adviser questions a month. Lucento Pro gives you " +
                "${PlanCatalog.PRO_AI_QUESTIONS_PER_DAY} a day."
        ProFeature.SMART_READING -> "Smarter reading is part of Lucento Pro. Reading on this phone is always free."
        ProFeature.SHARED_SPACE -> "Inviting a Partner is part of Lucento Pro. Being someone’s Partner is always free."
    }

    /** Whether a plan lifts [limit] for [feature]: false at the most any plan allows (20 goals). */
    fun upgradeHelps(feature: ProFeature, limit: Limit): Boolean =
        PlanCatalog.limit(feature, PlanCatalog.PRO_CAPABILITIES) != limit

    /** A count that stopped a new item: the numbers, the way out, and that what exists is untouched. */
    fun limitReachedLine(feature: ProFeature, used: Int, limit: Limit): String {
        val max = limit.maxOrNull ?: return lockedLine(feature)
        return when (feature) {
            ProFeature.MONEY_WALLETS ->
                "The free plan allows $max money wallets; you have $used. Pro and Pro Lifetime have no limit. " +
                    "Everything in the wallets you have keeps working."
            ProFeature.DEBT_ACCOUNTS ->
                "The free plan tracks $max cards or loans; you have $used. Pro and Pro Lifetime track as many as you have. " +
                    "Payments and reminders on the ones you have carry on as usual."
            ProFeature.SAVINGS_GOALS -> if (upgradeHelps(feature, limit)) {
                "The free plan allows $max active savings goals; you have $used. Pro and Pro Lifetime allow " +
                    "${PlanCatalog.PRO_ACTIVE_GOALS}. Or archive a goal you’ve reached to free a slot. Nothing happens to the goals you have."
            } else {
                "$max active goals is the most on any plan. Archive a goal you’ve reached to make room."
            }
            else -> lockedLine(feature)
        }
    }

    // ------------------------------------------------------------------------------------------------ plan status

    data class PlanStatus(val title: String, val detail: String)

    /** On what terms the online services are included ([serviceTerm]). */
    sealed interface ServiceTerm {
        /** Part of a Lucento Pro subscription. */
        data object Subscription : ServiceTerm

        /** Through the Lucento Cloud add-on. */
        data object Cloud : ServiceTerm

        /** Until [date] ("Nov 12, 2027"): a Lifetime's included year, or a courtesy grant's end. */
        data class Until(val date: String) : ServiceTerm

        /** Included, with no end set (a courtesy grant without an end date). */
        data object Included : ServiceTerm
    }

    fun planName(plan: DisplayPlan): String = when (plan) {
        DisplayPlan.FREE -> "Free plan"
        DisplayPlan.PRO -> "Lucento Pro"
        DisplayPlan.LIFETIME, DisplayPlan.LIFETIME_WITH_SERVICES -> "Pro Lifetime"
    }

    /** The plan row in the profile menu, Settings and the top of the Lucento Pro page (customer copy D-11). */
    fun planStatus(state: EntitlementState, now: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): PlanStatus {
        // The date never splits across lines ("Nov 12, / 2027": critic round 3), as on the welcome's cards.
        fun date(millis: Long): String = termDate(millis, zone, locale)
        val active = state.active(now)
        return when (state.displayPlan(now)) {
            DisplayPlan.FREE -> if (state.pending() != null) {
                PlanStatus("Waiting for Google Play", "Your plan starts the moment Google Play confirms your payment, even if Lucento is closed.")
            } else {
                PlanStatus("Free plan", "Everything you need to budget, on this phone. Limits only ever apply to adding new items.")
            }
            DisplayPlan.PRO -> {
                val sub = active.firstOrNull { it.source == EntitlementSource.PLAY_SUBSCRIPTION }
                val grant = active.firstOrNull { it.source == EntitlementSource.GRANT && Capability.LOCAL_PRO in it.grants }
                when {
                    sub != null -> subscriptionStatus(sub, ::date)
                    grant?.expiresAtMillis != null -> PlanStatus("Lucento Pro", "Courtesy access until ${date(grant.expiresAtMillis)}.")
                    else -> PlanStatus("Lucento Pro", "Every Lucento feature, on every phone you use.")
                }
            }
            DisplayPlan.LIFETIME -> PlanStatus(
                "Pro Lifetime\u00A0· you own it",
                "Every Pro feature that runs on this phone is yours. Cloud sync, Lucento Shared and the AI adviser’s Pro " +
                    "allowance come with Lucento Cloud.",
            )
            DisplayPlan.LIFETIME_WITH_SERVICES -> {
                val services = active.filter { Capability.CLOUD in it.grants }
                val year = services.firstOrNull { it.source == EntitlementSource.PLAY_LIFETIME_SERVICES }?.expiresAtMillis
                val cloud = services.firstOrNull { it.source == EntitlementSource.PLAY_CLOUD_ADDON }
                PlanStatus(
                    "Pro Lifetime\u00A0· you own it",
                    when {
                        cloud != null -> "With Lucento Cloud: cloud sync, Lucento Shared and the AI adviser’s Pro allowance."
                        year != null -> "Cloud sync, Lucento Shared and the AI adviser’s Pro allowance are included until ${date(year)}."
                        else -> "With cloud sync, Lucento Shared and the AI adviser’s Pro allowance."
                    },
                )
            }
        }
    }

    /**
     * On what terms this plan's online services (cloud sync, restore, Lucento Shared, the AI adviser's Pro allowance,
     * Smarter reading) are included: with a subscription, with the Lucento Cloud add-on, or until a date (a Lifetime's
     * included year, read from its entitlement; a courtesy grant's end). Null when this plan has none, so the welcome's
     * cards never make them sound owned for good (CUSTOMER_COPY: "not part of the one-time purchase").
     */
    fun serviceTerm(state: EntitlementState, now: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): ServiceTerm? {
        val services = state.active(now).filter { Capability.CLOUD in it.grants || Capability.AI_PRO in it.grants || Capability.SHARED in it.grants }
        if (services.any { it.source == EntitlementSource.PLAY_SUBSCRIPTION }) return ServiceTerm.Subscription
        if (services.any { it.source == EntitlementSource.PLAY_CLOUD_ADDON }) return ServiceTerm.Cloud
        val ending = services.firstOrNull { it.source == EntitlementSource.PLAY_LIFETIME_SERVICES }
            ?: services.firstOrNull { it.source == EntitlementSource.GRANT }
            ?: return null
        return ending.expiresAtMillis?.let { ServiceTerm.Until(termDate(it, zone, locale)) } ?: ServiceTerm.Included
    }

    /** A term's date, kept on one line: "Nov 12, 2027" with no-break spaces. */
    fun termDate(millis: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().format(DateTimeFormatter.ofPattern("MMM d, yyyy", locale)).replace(' ', '\u00A0')

    private fun subscriptionStatus(sub: Entitlement, date: (Long) -> String): PlanStatus {
        // A Play-local reading has no real expiry (docs §5.3); only the server's dates are shown as dates.
        val until = sub.expiresAtMillis?.takeIf { sub.verifiedBy == Verifier.SERVER }?.let(date)
        return when (sub.status) {
            EntitlementStatus.GRACE, EntitlementStatus.ON_HOLD -> PlanStatus(
                "Lucento Pro · payment problem",
                "Google Play couldn’t renew your Pro. Update your payment method in Google Play to keep it.",
            )
            EntitlementStatus.CANCELLED_ACTIVE -> PlanStatus(
                if (until != null) "Lucento Pro · ends $until" else "Lucento Pro · cancelled",
                "You keep Pro until the end of the period you paid for. Nothing you recorded changes afterwards.",
            )
            EntitlementStatus.TRIAL -> PlanStatus(
                "Lucento Pro · free trial",
                "Cancel in Google Play before the trial ends and you won’t be charged.",
            )
            else -> PlanStatus(
                if (until != null) "Lucento Pro · renews $until" else "Lucento Pro",
                "Renews until you cancel in Google Play.",
            )
        }
    }

    // ------------------------------------------------------------------------------------------------ prices

    /** "₱149.00 a month · 7-day free trial", from Google Play's own price string and periods. */
    fun priceLine(price: ProductPrice): String =
        listOf(price.formattedPrice, pricePer(price)).filter { it.isNotEmpty() }.joinToString(" ") +
            trialText(price)?.let { " · $it" }.orEmpty()

    /** How often [price] is charged: "a year", "a month", "once". */
    fun pricePer(price: ProductPrice): String {
        val period = price.billingPeriodIso?.let { runCatching { Period.parse(it) }.getOrNull() }
        return when {
            price.billingPeriodIso == null -> "once"
            period == null -> ""
            period.years == 1 && period.months == 0 -> "a year"
            period.years == 0 && period.months == 1 -> "a month"
            period.years == 0 && period.months > 1 -> "every ${period.months} months"
            period.years > 1 -> "every ${period.years} years"
            else -> ""
        }
    }

    /** "7-day free trial" when Play lists one on this offer. */
    fun trialText(price: ProductPrice): String? =
        price.freeTrialPeriodIso?.let { runCatching { Period.parse(it) }.getOrNull() }?.let { p ->
            when {
                p.days > 0 -> "${p.days}-day free trial"
                p.months > 0 -> "${p.months}-month free trial"
                else -> null
            }
        }

    /** The name of a subscription's period for the plan picker: "Yearly", "Monthly"; null for a one-time price. */
    fun periodName(price: ProductPrice): String? {
        val period = price.billingPeriodIso?.let { runCatching { Period.parse(it) }.getOrNull() } ?: return null
        return when {
            period.years == 1 && period.months == 0 -> "Yearly"
            period.years == 0 && period.months == 1 -> "Monthly"
            else -> null
        }
    }

    /**
     * How much a year costs less than twelve months, as a whole percentage rounded **down** so it never overstates,
     * from Play's own amounts. Null unless both are known in the same currency and the saving is at least 5 %.
     */
    fun yearlySavingPercent(yearly: ProductPrice, monthly: ProductPrice): Int? {
        val y = yearly.priceMicros ?: return null
        val m = monthly.priceMicros ?: return null
        if (yearly.currencyCode == null || yearly.currencyCode != monthly.currencyCode || y <= 0 || m <= 0) return null
        val twelve = Math.multiplyExact(m, 12L)
        if (y >= twelve) return null
        val percent = Math.multiplyExact(twelve - y, 100L) / twelve
        return percent.toInt().takeIf { it >= 5 }
    }

    // ------------------------------------------------------------------------------------------------ restore

    /** What the page says when Google Play couldn't do it (docs/MONETIZATION.md §9 "Errors"). Nothing about the plan changes. */
    fun billingProblem(problem: BillingProblem): String = when (problem) {
        BillingProblem.UNAVAILABLE ->
            "Google Play can’t take payments on this phone right now. Check that the Play Store is installed and signed in, then try again."
        BillingProblem.NETWORK -> "Google Play couldn’t be reached. Check your connection and try again. Nothing was charged."
        BillingProblem.ALREADY_OWNED -> "Your Google account already owns this. Tap Restore purchases to bring it back on this phone."
        BillingProblem.ITEM_UNAVAILABLE -> "Google Play doesn’t offer this plan here right now. Nothing was charged."
        BillingProblem.DEVELOPER, BillingProblem.OTHER -> "Something went wrong with Google Play. Nothing was charged; please try again."
    }

    /**
     * What the page says when Google Play refused a purchase because this Google account already owns [productId]. By
     * then Lucento has read Play and the server again ([plan]) and tried to release a Lifetime it had ended ([release],
     * MONETIZATION §5.3 L-7).
     */
    fun alreadyOwned(productId: String?, plan: DisplayPlan, release: ReleaseOutcome): String = when {
        // A plan that works says so first, whatever happened to an older purchase.
        ownedPlanShows(productId, plan) ->
            "Your Google account already owns this, and your ${planName(plan)} is working on this phone now."
        release.released > 0 ->
            "Google Play was still holding your earlier Pro Lifetime (refunded or cancelled), so it couldn’t sell you a new " +
                "one. Lucento has released it: you can buy Lifetime now."
        release.found > 0 ->
            "Google Play is still holding your earlier Pro Lifetime (refunded or cancelled), and Lucento couldn’t release it " +
                "just now. Check your connection and try again. If it keeps happening, contact support with your Google Play " +
                "order number."
        else -> billingProblem(BillingProblem.ALREADY_OWNED)
    }

    /** Whether [plan] is what owning [productId] gives, so the purchase Play refused is already working here. */
    private fun ownedPlanShows(productId: String?, plan: DisplayPlan): Boolean = when (productId) {
        PlayProducts.PRO_LIFETIME -> plan == DisplayPlan.LIFETIME || plan == DisplayPlan.LIFETIME_WITH_SERVICES
        PlayProducts.PRO_SUBSCRIPTION -> plan != DisplayPlan.FREE
        PlayProducts.CLOUD_ADDON -> plan == DisplayPlan.LIFETIME_WITH_SERVICES
        else -> false
    }

    /** What the page says after a purchase it started (customer copy D-9); null when there is nothing to say (cancelled). */
    fun purchaseResult(result: PurchaseResult, productId: String?): String? = when (result) {
        PurchaseResult.Completed -> when (productId) {
            PlayProducts.PRO_LIFETIME -> "Pro Lifetime is yours. Everything that runs on this phone is unlocked, for good."
            PlayProducts.CLOUD_ADDON -> "Lucento Cloud is on: cloud sync, Lucento Shared and the AI adviser’s Pro allowance."
            else -> "Welcome to Lucento Pro. Everything is unlocked on this phone now."
        }
        PurchaseResult.Pending -> ProOffers.PENDING_NOTE
        PurchaseResult.Cancelled -> null
        is PurchaseResult.Failed -> billingProblem(result.problem)
    }

    /** When the server wouldn't record a purchase on this account (§5.4 linking rule). The phone keeps its plan either way. */
    fun verificationIssue(outcome: VerifyOutcome): String? = when (outcome) {
        VerifyOutcome.LINKED_ELSEWHERE ->
            "This purchase is linked to a different Lucento account. It keeps working on this phone; cloud services follow the " +
                "account it’s linked to. To move it, contact support with your Google Play order number."
        VerifyOutcome.OTHER_ACCOUNT ->
            "This purchase was made with a different Lucento account on this Google Play account, so it stays with that " +
                "account. It keeps working on this phone. To use cloud services with it, sign in with the account that bought it."
        VerifyOutcome.TEST_PURCHASE_REFUSED ->
            "This is a Google Play test purchase, and Lucento’s server doesn’t accept test purchases right now."
        VerifyOutcome.REVIEW_REQUIRED ->
            "This purchase has moved between Lucento accounts several times, so support needs to look at it before it moves again. " +
                "It keeps working on this phone. Contact support with your Google Play order number."
        else -> null
    }

    /** What "Restore purchases" says afterwards (customer copy D-8). */
    fun restoreMessage(
        before: DisplayPlan,
        after: DisplayPlan,
        outcome: RefreshOutcome,
        awaitingCheck: Boolean = false,
        /** A Pro Lifetime on record that Google Play reports as refunded or cancelled ([EntitlementState.lifetimeEnded]). */
        lifetimeEnded: Boolean = false,
    ): String = when {
        after != DisplayPlan.FREE && before == DisplayPlan.FREE -> "Welcome back: your ${planName(after)} is restored."
        after != DisplayPlan.FREE -> "Your plan is up to date: ${planName(after)}."
        awaitingCheck -> "Lucento still can’t confirm your purchase. Connect to the internet and make sure the Google Play " +
            "Store app is on and signed in to the Google account you bought with, then try again."
        !outcome.playReached && !outcome.serverReached ->
            "Restoring needs an internet connection once. If you already restored on this phone, everything you own is still working."
        lifetimeEnded -> "Google Play reports your Pro Lifetime as refunded or cancelled, so it no longer unlocks Pro. You can " +
            "buy it again on this page."
        else -> "No purchase found. If you bought Lucento with another Google account, switch accounts in Google Play, or sign " +
            "in to the Lucento account you used before. Still stuck? Contact support with your Google Play order number."
    }

    // ------------------------------------------------------------------------------------------------ plan ended

    data class NoticeCopy(val title: String, val body: String)

    /** The one-time notice after a plan ends (customer copy D-7). */
    fun notice(notice: PlanNotice): NoticeCopy = when (notice) {
        PlanNotice.PRO_ENDED -> NoticeCopy(
            "Your Lucento Pro has ended",
            "Everything you recorded is still here, and nothing was removed. What’s changed: new wallets beyond " +
                "${PlanCatalog.FREE_MONEY_WALLETS} money wallets and ${PlanCatalog.FREE_DEBT_ACCOUNTS} cards or loans, new goals " +
                "beyond ${PlanCatalog.FREE_ACTIVE_GOALS}, Insights beyond 3 months, PDF statements, wallets in other currencies and " +
                "the extra themes wait for Pro. Cloud sync is paused, and the AI adviser is back to " +
                "${PlanCatalog.FREE_AI_QUESTIONS_PER_MONTH} questions a month.",
        )
        PlanNotice.SERVICES_ENDED -> NoticeCopy(
            "Your online services have ended",
            "Everything you own in Pro Lifetime is unchanged and keeps working offline. Cloud sync is paused and nothing in " +
                "your account was deleted; the AI adviser is back to ${PlanCatalog.FREE_AI_QUESTIONS_PER_MONTH} questions a month, " +
                "and Lucento Shared can’t send new invitations. Keep them with Lucento Cloud, or carry on without.",
        )
        PlanNotice.LIFETIME_ENDED -> NoticeCopy(
            "Your Pro Lifetime is no longer active",
            "Google Play reports this purchase as refunded or cancelled. Everything you recorded is still here, and nothing " +
                "was removed; the free plan’s limits apply to new items. If this looks wrong, restore your purchase from the " +
                "Lucento Pro page.",
        )
        PlanNotice.LIFETIME_NEEDS_CHECK -> NoticeCopy(
            "Lucento needs to check your Pro Lifetime",
            // Paused for either reason (MONETIZATION §5.3 L-2, L-6): no word for a month, or Lucento's server hasn't
            // confirmed Google Play's reading lately. The same steps settle both.
            "Pro is paused on this phone until Lucento can confirm your purchase again. Connect to the internet, make " +
                "sure the Google Play Store app is on and signed in to the Google account you bought with, then tap " +
                "Restore purchases on the Lucento Pro page. Everything you recorded is still here, and nothing was removed.",
        )
        PlanNotice.PRO_NEEDS_CHECK -> NoticeCopy(
            "Lucento needs to check your Lucento Pro",
            "Pro is paused on this phone until Lucento can confirm your subscription again. Connect to the internet, make " +
                "sure the Google Play Store app is on and signed in to the Google account you subscribed with, then tap " +
                "Restore purchases on the Lucento Pro page. Everything you recorded is still here, and nothing was removed.",
        )
    }
}
