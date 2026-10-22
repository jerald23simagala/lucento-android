package com.lucento.app.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.theme.Spacing
import java.util.Currency
import java.util.Locale

data class CurrencyOption(val code: String, val name: String, val symbol: String)

object CurrencyCatalog {
    /** Shown first: widely used currencies across Lucento's global audience. */
    private val featured = listOf(
        "USD", "EUR", "GBP", "JPY", "CNY", "INR", "PHP", "IDR", "SGD", "AUD",
        "CAD", "BRL", "MXN", "NGN", "ZAR", "KRW", "AED", "CHF",
    )

    fun all(locale: Locale = Locale.getDefault()): List<CurrencyOption> {
        val available = Currency.getAvailableCurrencies()
            .filter { it.defaultFractionDigits >= 0 } // drops precious metals, test & fund codes
            .associateBy { it.currencyCode }
        fun option(c: Currency) = CurrencyOption(c.currencyCode, c.getDisplayName(locale), c.getSymbol(locale))
        val top = featured.mapNotNull { available[it] }.map(::option)
        val rest = available.values
            .filter { it.currencyCode !in featured }
            .map(::option)
            .sortedBy { it.name.lowercase(locale) }
        return top + rest
    }
}

@Composable
fun CurrencyList(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val all = remember { CurrencyCatalog.all() }
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(query, all) {
        val q = query.trim()
        if (q.isEmpty()) all
        else all.filter { it.code.contains(q, ignoreCase = true) || it.name.contains(q, ignoreCase = true) }
    }
    val haptics = rememberHaptics()

    Column(modifier) {
        SearchField(
            value = query,
            onValueChange = { query = it.take(40) },
            placeholder = "Search currency or code",
            modifier = Modifier.padding(bottom = Spacing.sm),
        )
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(filtered, key = { it.code }) { option ->
                val isSelected = option.code == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.RadioButton) {
                            haptics.tick()
                            onSelect(option.code)
                        }
                        .semantics { this.selected = isSelected }
                        .padding(vertical = Spacing.md, horizontal = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = option.code,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(56.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(option.name, style = MaterialTheme.typography.bodyLarge)
                        if (option.symbol != option.code) {
                            Text(
                                option.symbol,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (isSelected) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

// LucentoBottomSheet exposes Material 3's experimental SheetState in its signature.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyPickerSheet(
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    LucentoBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = Spacing.gutter).fillMaxHeight(0.9f)) {
            Text(
                "Choose currency",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = Spacing.md),
            )
            CurrencyList(selected = selected, onSelect = onSelect)
        }
    }
}
