package com.lucento.app.feature.fx

import androidx.compose.runtime.staticCompositionLocalOf
import com.lucento.app.data.fx.FxRateSource

/**
 * Multicurrency, for every screen that records money: the ledger's main currency (locked once chosen) and the day's
 * exchange rates (null in previews and tests; returns null itself while "Exchange rates online" is off or offline).
 * Provided once by MainActivity, so the wallet editor, the composer and the transfer sheet needn't be handed them by
 * each of the screens that open them.
 */
val LocalMainCurrency = staticCompositionLocalOf<String?> { null }
val LocalFxRates = staticCompositionLocalOf<FxRateSource?> { null }

/** Values in the main currency of every wallet and movement in another currency, for "≈ ₱…" lines (app-wide). */
val LocalFxDisplay = androidx.compose.runtime.compositionLocalOf { com.lucento.app.domain.fx.FxDisplay.NONE }
