package com.lucento.app.feature.widget

import com.lucento.app.core.designsystem.component.HIDDEN_AMOUNT_DESCRIPTION
import com.lucento.app.core.money.Money
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.domain.BudgetHealth
import com.lucento.app.domain.Feature
import com.lucento.app.feature.budget.PaceVerdict
import com.lucento.app.feature.budget.paceVerdict
import com.lucento.app.feature.home.HomeStateBuilder
import com.lucento.app.feature.home.HomeUiState
import com.lucento.app.feature.home.monthLabel
import java.util.Locale

/** How a figure or status reads. The words always carry the meaning; colour only reinforces it. */
enum class WidgetTone { NEUTRAL, POSITIVE, WARNING, NEGATIVE }

/**
 * What the Safe to spend widget shows (docs/WIDGETS.md). Built only from [HomeUiState], with Home's own figures and
 * words, so the widget can never disagree with Home's Monthly budget card or its Cash flow card.
 */
sealed interface SafeToSpendWidgetModel {
    /** Onboarding isn't finished: nothing to show yet. */
    data object NotSetUp : SafeToSpendWidgetModel

    /** App lock is on: nothing about the month shows; Add still opens Lucento, behind its lock. */
    data class Locked(val masked: String) : SafeToSpendWidgetModel

    /** No expense categories yet: Home's own prompt. */
    data class ChooseCategories(val monthLabel: String) : SafeToSpendWidgetModel

    /** Categories but no amounts this month: Home's own prompt. */
    data class NoBudget(val monthLabel: String) : SafeToSpendWidgetModel

    /** Home shows its Cash flow card instead of the budget (Budgets switched off): the same Net flow. */
    data class NetFlow(
        val monthLabel: String,
        val figure: String,
        val tone: WidgetTone,
        val detail: String,
        /** Why this isn't Safe to spend; null when the Budgets module is on. */
        val note: String?,
        val description: String,
    ) : SafeToSpendWidgetModel

    /** Home's Monthly budget card in brief. */
    data class Budget(
        val monthLabel: String,
        /** "Safe to spend"; Home's "Remaining" or "Overspent" only when there is no safe-to-spend figure. */
        val label: String,
        val figure: String,
        val figureTone: WidgetTone,
        /** Home's caption when nothing is left ("Bills due take what’s left", "You’re over budget this month"). */
        val reason: String?,
        val perDay: String?,
        val bills: String?,
        val usedPercent: Int,
        val usedFraction: Float,
        val barTone: WidgetTone,
        val verdict: String,
        val verdictTone: WidgetTone,
        val description: String,
    ) : SafeToSpendWidgetModel

    companion object {
        const val OVER_BUDGET_REASON = "You’re over budget this month"
        const val BILLS_REASON = "Bills due take what’s left"
        const val BUDGETS_OFF_NOTE = "Budgets is switched off, so this shows Net flow, as Home does."

        fun from(
            home: HomeUiState,
            onboarded: Boolean,
            appLock: Boolean,
            hideAmounts: Boolean,
            locale: Locale = Locale.getDefault(),
        ): SafeToSpendWidgetModel {
            if (!onboarded) return NotSetUp
            if (appLock) return Locked(MoneyFormatter.masked(home.focusCurrency, locale))
            val month = monthLabel(home.month, locale)
            fun shown(money: Money) =
                if (hideAmounts) MoneyFormatter.masked(money.currencyCode, locale) else MoneyFormatter.format(money, locale)
            fun spoken(money: Money) = if (hideAmounts) HIDDEN_AMOUNT_DESCRIPTION else MoneyFormatter.format(money, locale)

            val plan = home.budget
            val burn = home.burn
            if (plan == null || burn == null) {
                val zero = Money.zero(home.focusCurrency)
                val income = home.netFlow?.income ?: zero
                val expense = home.netFlow?.expense ?: zero
                val net = income - expense
                val figure = if (hideAmounts) {
                    MoneyFormatter.masked(net.currencyCode, locale)
                } else {
                    MoneyFormatter.formatSigned(net, showPlus = true, locale = locale)
                }
                val tone = when {
                    hideAmounts -> WidgetTone.NEUTRAL
                    net.amountMinor > 0L -> WidgetTone.POSITIVE
                    net.isNegative -> WidgetTone.NEGATIVE
                    else -> WidgetTone.NEUTRAL
                }
                val spokenNet = if (hideAmounts) HIDDEN_AMOUNT_DESCRIPTION else MoneyFormatter.formatSigned(net, showPlus = true, locale = locale)
                return NetFlow(
                    monthLabel = month,
                    figure = figure,
                    tone = tone,
                    detail = "In ${shown(income)} · Out ${shown(expense)}",
                    note = BUDGETS_OFF_NOTE.takeUnless { home.features.isEnabled(Feature.BUDGETS) },
                    description = "Net flow for $month: $spokenNet. Money in ${spoken(income)}, money out ${spoken(expense)}.",
                )
            }
            if (home.expenseCategoryCount == 0) return ChooseCategories(month)
            if (!plan.hasBudget) return NoBudget(month)

            val formula = HomeStateBuilder.budgetFormula(plan, home.safeToSpend)
            val safeShown = formula.safeToSpendShown
            val hasSafe = safeShown != null && formula.billsDue != null
            val label: String
            val figureMoney: Money
            val nothing: Boolean
            val reason: String?
            if (hasSafe) {
                label = "Safe to spend"
                figureMoney = safeShown!!
                nothing = formula.nothingSafe
                reason = when {
                    !nothing -> null
                    formula.overspent -> OVER_BUDGET_REASON
                    else -> BILLS_REASON
                }
            } else {
                // Home closes on Remaining (or Overspent) when there is no safe-to-spend figure.
                label = if (formula.overspent) "Overspent" else "Remaining"
                figureMoney = formula.remaining
                nothing = formula.overspent
                reason = null
            }
            val safe = home.safeToSpend
            val dayAmount = if (safe != null && hasSafe && !nothing) safe.perDay else null
            val days = safe?.daysLeft ?: 0
            val perDay = dayAmount?.let { "${shown(it)} a day · ${daysLeft(days)}" }
            val billsDue = formula.billsDue?.takeIf { hasSafe && !nothing && !it.isZero }
            val bills = billsDue?.let { "After ${shown(it)} bills due" }
            val used = burn.consumedPercent ?: 0
            val verdict = paceVerdict(plan, burn.pace, home.isCurrentMonth)
            val description = buildString {
                append("$label for $month: ${spoken(figureMoney)}.")
                if (reason != null) append(" $reason.")
                if (dayAmount != null) append(" ${spoken(dayAmount)} a day, ${daysLeft(days)}.")
                if (billsDue != null) append(" After ${spoken(billsDue)} bills due.")
                append(" $used percent of the budget spent. ${verdict.label}.")
            }
            return Budget(
                monthLabel = month,
                label = label,
                figure = shown(figureMoney),
                figureTone = when {
                    hideAmounts -> WidgetTone.NEUTRAL
                    nothing -> WidgetTone.NEGATIVE
                    else -> WidgetTone.POSITIVE
                },
                reason = reason,
                perDay = perDay,
                bills = bills,
                usedPercent = used,
                usedFraction = burn.consumedFraction.coerceIn(0f, 1f),
                barTone = toneOf(plan.health),
                verdict = verdict.label,
                verdictTone = toneOf(verdict),
                description = description,
            )
        }

        internal fun daysLeft(days: Int): String = if (days == 1) "1 day left" else "$days days left"

        /** Home's ring colour by budget health (feature/budget/BudgetVisuals.healthColor). */
        internal fun toneOf(health: BudgetHealth): WidgetTone = when (health) {
            BudgetHealth.NOT_SET -> WidgetTone.NEUTRAL
            BudgetHealth.ON_TRACK -> WidgetTone.POSITIVE
            BudgetHealth.APPROACHING, BudgetHealth.LOW -> WidgetTone.WARNING
            BudgetHealth.EXCEEDED -> WidgetTone.NEGATIVE
        }

        /** Home's verdict chip colours (feature/budget/BudgetVisuals.verdictTone). */
        internal fun toneOf(verdict: PaceVerdict): WidgetTone = when (verdict) {
            PaceVerdict.OVER -> WidgetTone.NEGATIVE
            PaceVerdict.AHEAD -> WidgetTone.WARNING
            PaceVerdict.ON_TRACK, PaceVerdict.UNDER -> WidgetTone.POSITIVE
        }
    }
}

/** The Quick add widget's arrangement for its size and the phone's text size (docs/WIDGETS.md). */
enum class QuickAddLayout {
    /** The "Add a transaction" field, with Scan and Speak while their modules are on. */
    FULL,

    /** The short label "Add", with Scan and Speak. */
    SHORT,

    /** One Add target. Speak and Scan stay a tap away inside the composer. */
    SINGLE;

    companion object {
        const val SINGLE_BELOW_DP = 180f
        const val SHORT_BELOW_DP = 344f
        const val SHORT_FROM_SCALE = 1.15f
        const val SINGLE_FROM_SCALE = 1.5f

        fun choose(widthDp: Float, fontScale: Float): QuickAddLayout = when {
            widthDp < SINGLE_BELOW_DP || fontScale >= SINGLE_FROM_SCALE -> SINGLE
            widthDp < SHORT_BELOW_DP || fontScale >= SHORT_FROM_SCALE -> SHORT
            else -> FULL
        }
    }
}

/** One minute past the next local midnight: when "days left" and "a day" turn over, the widget redraws. */
internal fun delayToNextWidgetDay(now: java.time.ZonedDateTime): java.time.Duration =
    java.time.Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(now.zone).plusMinutes(1))
