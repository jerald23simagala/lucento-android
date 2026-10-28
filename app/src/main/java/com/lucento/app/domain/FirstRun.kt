package com.lucento.app.domain

import com.lucento.app.core.money.Money
import com.lucento.app.core.money.MoneyParser

/**
 * The short first run: welcome, currency, a starting balance, then the composer.
 * People who already finished onboarding never re-enter it.
 */
enum class FirstRunStep {
    WELCOME,
    CURRENCY,
    WALLET,
    FIRST_ENTRY,
    ;

    val number: Int get() = ordinal + 1

    companion object {
        const val COUNT = 4

        fun progressLabel(step: FirstRunStep): String = "Step ${step.number} of $COUNT"
    }
}

object OnboardingFlow {
    /** Finished installs stay in the app. Only an unfinished install sees the first run. */
    fun showOnboarding(onboardingComplete: Boolean): Boolean = !onboardingComplete

    fun next(step: FirstRunStep): FirstRunStep? = FirstRunStep.entries.getOrNull(step.ordinal + 1)

    fun previous(step: FirstRunStep): FirstRunStep? = FirstRunStep.entries.getOrNull(step.ordinal - 1)

    /**
     * Where an unfinished install resumes.
     *
     * [shortFlow] is set once the new four-step flow has saved a step, so a process death on the wallet
     * screen returns there. An older in-progress install (no [shortFlow]) never lands on the removed
     * focus, feature, category or sync screens: before currency was confirmed it opens currency, and
     * after that it opens the wallet step. A finished install returns null.
     */
    fun resume(onboardingComplete: Boolean, savedIndex: Int, shortFlow: Boolean): FirstRunStep? {
        if (onboardingComplete) return null
        if (shortFlow) {
            return FirstRunStep.entries.getOrElse(savedIndex.coerceAtLeast(0)) { FirstRunStep.WALLET }
        }
        return when (savedIndex) {
            0 -> FirstRunStep.WELCOME
            in 1..5 -> FirstRunStep.CURRENCY
            else -> FirstRunStep.WALLET
        }
    }

    /**
     * Taps to save the first entry when every default is accepted and an example chip fills the composer.
     * Typing is not counted. The welcome, currency and wallet continues are one tap each; the chip and Save
     * are the other two.
     */
    fun tapsToFirstSave(): Int = 5
}

/**
 * Defaults for everything the short first run no longer asks.
 * [DEBTS_ON_BY_DEFAULT] is the only switch for whether new installs start with Debts.
 * It is on. People who already finished setup keep the feature set they saved.
 */
object FirstRunDefaults {
    const val DEBTS_ON_BY_DEFAULT = true
    const val DEFAULT_WALLET_NAME = "Cash"
    const val THEME_SYSTEM = "SYSTEM"

    fun features(): Set<Feature> {
        val all = Feature.entries.toSet()
        return if (DEBTS_ON_BY_DEFAULT) all else all - Feature.DEBTS_LOANS
    }

    fun categoryPresetKeys(): Set<String> = CategoryPresets.recommendedKeys
}

/** The line under the composer chips, and the line after a message is read. */
object ComposerPrivacy {
    const val ON_DEVICE = "Your entry is read on this phone and never sent anywhere."
    const val GEMINI = "Your entry is sent to Google Gemini through Lucento's server to be read."

    fun line(geminiEnabled: Boolean): String = if (geminiEnabled) GEMINI else ON_DEVICE
}

/**
 * Opening balance for the first wallet. A blank field starts at zero. Anything the parser rejects
 * (negative, too many decimals, not a number) does not continue.
 */
fun startingBalance(text: String, currencyCode: String, decimalSeparator: Char): Money? =
    when (val parsed = MoneyParser.parse(text, currencyCode, decimalSeparator)) {
        is MoneyParser.Result.Success -> parsed.money
        MoneyParser.Result.Empty -> Money.zero(currencyCode)
        else -> null
    }

/** Real entries: income and expenses. Transfers, adjustments and opening balances are not entries. */
fun realEntries(transactions: List<Transaction>): List<Transaction> =
    transactions.filter { it.type.isCashFlow }

data class FirstWeekStep(
    val id: String,
    val title: String,
    val done: Boolean,
    /** "n/3" for the logging step; null when the step is only done or not. */
    val progress: String? = null,
)

object FirstWeek {
    const val MORE_ENTRIES = 3
    const val REACTION = "First one logged — nice."

    fun steps(
        realEntryCount: Int,
        hasGoal: Boolean,
        hasBill: Boolean,
        checkedInsights: Boolean,
    ): List<FirstWeekStep> {
        val more = (realEntryCount - 1).coerceAtLeast(0).coerceAtMost(MORE_ENTRIES)
        return listOf(
            FirstWeekStep("log3", "Log 3 more things", more >= MORE_ENTRIES, "$more/$MORE_ENTRIES"),
            FirstWeekStep("goal", "Set a savings goal", hasGoal),
            FirstWeekStep("bill", "Add a bill or subscription", hasBill),
            FirstWeekStep("insights", "Check your week", checkedInsights),
        )
    }

    /** Only the short first run opts in. Dismiss sticks. Older finished installs never see the card. */
    fun show(dayOneHome: Boolean, dismissed: Boolean): Boolean = dayOneHome && !dismissed

    /** The mascot reaction plays once, on the visit after the first real entry, for a day-one home. */
    fun celebrate(dayOneHome: Boolean, alreadyCelebrated: Boolean, realEntryCount: Int): Boolean =
        dayOneHome && !alreadyCelebrated && realEntryCount >= 1
}
