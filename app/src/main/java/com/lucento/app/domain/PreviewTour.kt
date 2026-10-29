package com.lucento.app.domain

import com.lucento.app.core.money.CurrencyRules
import com.lucento.app.core.money.Money

/**
 * Example figures for the in-app preview. Built in memory only. Nothing here is written to Room,
 * DataStore, or the network. The tour is part of the app: it is not behind a switch that starts off.
 */
data class PreviewExample(
    val balance: Money,
    val entryNote: String,
    val entry: Money,
)

object PreviewTour {
    /** The invitation row. It leaves for good after the first real entry, or once the person opens it. */
    fun showRow(realEntryCount: Int, opened: Boolean): Boolean = realEntryCount == 0 && !opened

    fun example(currencyCode: String): PreviewExample {
        val digits = CurrencyRules.fractionDigits(currencyCode)
        fun major(units: Long): Long {
            var minor = units
            repeat(digits) { minor *= 10 }
            return minor
        }
        return PreviewExample(
            balance = Money(major(1_250), currencyCode),
            entryNote = "Coffee",
            entry = Money(major(4), currencyCode),
        )
    }
}
