package com.lucento.app.feature.home

import com.lucento.app.domain.PreviewTour
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletType

/**
 * The example tour Home shows after "See Lucento with example data".
 * Built only from [PreviewTour.example]. It does not read or write the ledger.
 */
fun openExampleTour(real: HomeUiState): HomeUiState {
    val currency = real.focusCurrency.ifBlank { real.primaryCurrency }
    val example = PreviewTour.example(currency)
    val wallet = Wallet(
        id = 1,
        name = "Cash",
        type = WalletType.CASH,
        balance = example.balance,
        createdAtEpochMillis = 0,
    )
    val entry = Transaction(
        id = 1,
        type = TransactionType.EXPENSE,
        amount = example.entry,
        walletId = wallet.id,
        categoryId = null,
        date = real.today,
        note = example.entryNote,
        createdAtEpochMillis = 0,
    )
    return HomeStateBuilder.build(
        all = listOf(entry),
        wallets = listOf(wallet),
        categories = emptyList(),
        primaryCurrency = currency,
        today = real.today,
        features = real.features,
    ).copy(examplePreviewOpened = true)
}
