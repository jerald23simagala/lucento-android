package com.lucento.app.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import com.lucento.app.core.designsystem.theme.OverlineStyle
import com.lucento.app.core.designsystem.theme.Sizes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.theme.Spacing
import java.util.Currency
import java.util.Locale

data class CurrencyOption(val code: String, val name: String, val symbol: String)

object CurrencyCatalog {
    /** Shown first: widely used currencies across Lucento's global audience. */
    val featured = listOf(
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

/**
 * Every currency, searchable: the popular ones first, then the rest A–Z, each under a small heading. A row shows the
 * symbol on a tile, the name and the code; the chosen one is tinted and outlined, and only one can be chosen.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CurrencyList(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Content above the search field that scrolls away with the list (a step's title and other settings). */
    header: (LazyListScope.() -> Unit)? = null,
    /** Behind the search field once it is pinned at the top: the colour of the surface the list sits on. */
    pinnedBackground: Color = MaterialTheme.colorScheme.background,
) {
    val all = remember { CurrencyCatalog.all() }
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(query, all) {
        val q = query.trim()
        if (q.isEmpty()) all
        else all.filter { it.code.contains(q, ignoreCase = true) || it.name.contains(q, ignoreCase = true) }
    }
    val searching = query.isNotBlank()
    val (popular, rest) = remember(filtered) { filtered.partition { it.code in CurrencyCatalog.featured } }
    val haptics = rememberHaptics()
    val select: (String) -> Unit = { code ->
        haptics.tick()
        onSelect(code)
    }

    // One scrolling list, so a short screen or large text never leaves the currencies without room: anything above
    // the search scrolls away, and the search stays pinned at the top once reached.
    LazyColumn(
        modifier.fillMaxWidth().then(if (header == null) Modifier.selectableGroup() else Modifier),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        header?.invoke(this)
        stickyHeader(key = "search") {
            Box(Modifier.fillMaxWidth().background(pinnedBackground).padding(bottom = Spacing.xs)) {
                SearchField(value = query, onValueChange = { query = it.take(40) }, placeholder = "Search currency or code")
            }
        }
        if (searching) {
            if (filtered.isEmpty()) {
                item(key = "none") {
                    Text(
                        "No currency matches “${query.trim()}”.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Spacing.md),
                    )
                }
            }
            items(filtered, key = { it.code }) { CurrencyRow(it, it.code == selected, select) }
        } else {
            item(key = "popular") { CurrencyGroupHeader(Icons.Outlined.StarOutline, "Popular") }
            items(popular, key = { it.code }) { CurrencyRow(it, it.code == selected, select) }
            item(key = "all") { CurrencyGroupHeader(Icons.Outlined.Public, "All currencies") }
            items(rest, key = { it.code }) { CurrencyRow(it, it.code == selected, select) }
        }
    }
}

@Composable
private fun CurrencyGroupHeader(icon: ImageVector, title: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = Spacing.md, bottom = Spacing.xxs, start = Spacing.xs)
            .clearAndSetSemantics { heading(); contentDescription = title },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(Spacing.sm))
        Text(title.uppercase(), style = OverlineStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CurrencyRow(option: CurrencyOption, isSelected: Boolean, onSelect: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val border by animateColorAsState(if (isSelected) scheme.primary else Color.Transparent, tween(160), label = "currency-border")
    val fill by animateColorAsState(
        if (isSelected) scheme.primaryContainer.copy(alpha = 0.55f) else Color.Transparent,
        tween(160),
        label = "currency-fill",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(fill)
            .border(BorderStroke(1.5.dp, border), MaterialTheme.shapes.medium)
            .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(option.code) }
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CurrencySymbolTile(option, isSelected)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(option.name, style = MaterialTheme.typography.bodyLarge, fontWeight = if (isSelected) FontWeight.SemiBold else null)
            Text(
                if (option.symbol != option.code) "${option.code} · ${option.symbol}" else option.code,
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        Icon(
            if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            contentDescription = null,
            tint = if (isSelected) scheme.primary else scheme.outlineVariant,
        )
    }
}

/** The currency's symbol on a 40dp tile (its code when the locale has no symbol for it). Decorative: the row says it. */
@Composable
private fun CurrencySymbolTile(option: CurrencyOption, isSelected: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val mark = if (option.symbol != option.code) option.symbol else option.code
    Box(
        Modifier
            .size(Sizes.iconTile)
            .clip(MaterialTheme.shapes.small)
            .background(if (isSelected) scheme.primary else scheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            mark,
            style = if (mark.length <= 2) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) scheme.onPrimary else scheme.onPrimaryContainer,
            maxLines = 1,
            softWrap = false,
        )
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
            CurrencyList(selected = selected, onSelect = onSelect, pinnedBackground = MaterialTheme.colorScheme.surfaceContainerLow)
        }
    }
}
