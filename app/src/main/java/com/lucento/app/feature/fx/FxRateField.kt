package com.lucento.app.feature.fx

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.lucento.app.core.designsystem.component.LucentoFields
import com.lucento.app.core.money.FxRate
import com.lucento.app.core.money.MoneyFormatter
import java.time.LocalDate

/**
 * "1 USD = ₱ [63.69]": the rate a wallet in another currency is valued at in the main currency. While empty it is filled
 * with [date]'s rate when "Exchange rates online" is on (and says so); otherwise the person types it — the rate they got,
 * or the day's. Validation is the caller's ([FxRate.parse]); [isError] shows the hint.
 */
@Composable
fun FxRateField(
    currency: String,
    mainCurrency: String,
    text: String,
    onTextChange: (String) -> Unit,
    isError: Boolean,
    explanation: String,
    modifier: Modifier = Modifier,
    date: LocalDate = LocalDate.now(),
) {
    val rates = LocalFxRates.current
    val current by rememberUpdatedState(text)
    val change by rememberUpdatedState(onTextChange)
    var filledOnline by remember(currency, mainCurrency, date) { mutableStateOf(false) }
    LaunchedEffect(currency, mainCurrency, date) {
        if (current.isNotBlank() || rates == null) return@LaunchedEffect
        val rate = runCatching { rates.rate(currency, mainCurrency, date) }.getOrNull() ?: return@LaunchedEffect
        if (current.isBlank()) {
            change(FxRate.displayRate(rate))
            filledOnline = true
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = {
            filledOnline = false
            onTextChange(it.take(MAX_LENGTH))
        },
        label = { Text("Exchange rate") },
        prefix = { Text("1 $currency = ${MoneyFormatter.currencySymbol(mainCurrency)} ") },
        placeholder = { Text("0") },
        singleLine = true,
        isError = isError,
        supportingText = {
            Text(
                when {
                    isError -> "Enter what 1 $currency is worth in $mainCurrency, like 63.69."
                    filledOnline -> "${if (date == LocalDate.now()) "Today’s" else "That day’s"} rate, filled in online. " +
                        "Change it to the rate you actually got."
                    else -> explanation
                },
            )
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        shape = LucentoFields.shape,
        colors = LucentoFields.filledColors(),
        modifier = modifier.fillMaxWidth(),
    )
}

private const val MAX_LENGTH = 20
