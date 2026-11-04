package com.lucento.app.feature.wallets

import androidx.compose.material.icons.outlined.AccountBalance
import com.lucento.app.core.designsystem.component.SheetHeader
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Block
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lucento.app.core.banks.Institution
import com.lucento.app.core.designsystem.component.InstitutionAvatar
import com.lucento.app.core.designsystem.component.LocalInstitutions
import com.lucento.app.core.designsystem.component.LucentoBottomSheet
import com.lucento.app.core.designsystem.component.SearchField
import com.lucento.app.core.designsystem.component.rememberHaptics
import com.lucento.app.core.designsystem.theme.Spacing
import java.util.Locale

/** How the picker labels an institution's kind (registry `type`). */
internal fun institutionKind(type: String): String = when (type) {
    "traditional_bank" -> "Bank"
    "digital_bank" -> "Digital bank"
    "financial_platform" -> "Payments & investing"
    else -> "Institution"
}

/** "Philippines" for "PH", in the user's language; the code itself when the platform has no name for it. */
internal fun countryName(code: String, locale: Locale = Locale.getDefault()): String =
    Locale("", code).getDisplayCountry(locale).ifBlank { code }

/**
 * Choose the bank or platform a wallet belongs to, or none. The device's country comes first; search matches any
 * word of the name, in any country. Display only — it changes the wallet's look, never its money.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstitutionPickerSheet(
    selectedId: String?,
    onSelect: (Institution?) -> Unit,
    onDismiss: () -> Unit,
    country: String? = Locale.getDefault().country.takeIf { it.length == 2 },
) {
    val registry = LocalInstitutions.current
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query, registry, country) { registry.search(query, country) }
    val haptics = rememberHaptics()
    LucentoBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = Spacing.gutter).fillMaxHeight(0.9f)) {
            SheetHeader(
                title = "Choose bank or provider",
                subtitle = "Shows its logo and colours on this wallet. Lucento doesn’t connect to it.",
                icon = Icons.Outlined.AccountBalance,
                modifier = Modifier.padding(bottom = Spacing.xs),
            )
            SearchField(
                value = query,
                onValueChange = { query = it.take(40) },
                placeholder = "Search banks and wallets",
                modifier = Modifier.padding(bottom = Spacing.sm),
            )
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                if (query.isBlank()) {
                    item(key = "none") {
                        PickerRow(
                            selected = selectedId == null,
                            title = "No bank or provider",
                            subtitle = "Use the wallet type’s look",
                            leading = { Icon(Icons.Outlined.Block, contentDescription = null, modifier = Modifier.size(32.dp).padding(4.dp)) },
                            onClick = {
                                haptics.tick()
                                onSelect(null)
                            },
                        )
                    }
                }
                items(results, key = { it.id }) { institution ->
                    PickerRow(
                        selected = institution.id == selectedId,
                        title = institution.name,
                        subtitle = "${institutionKind(institution.type)} · ${countryName(institution.country)}",
                        leading = { InstitutionAvatar(institution, size = 32.dp) },
                        onClick = {
                            haptics.tick()
                            onSelect(institution)
                        },
                    )
                }
                if (results.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            "No match. Try another spelling, or leave the wallet without a bank — nothing else changes.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = Spacing.lg),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerRow(selected: Boolean, title: String, subtitle: String, leading: @Composable () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) { this.selected = selected }
            .padding(vertical = Spacing.sm, horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        if (selected) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
    }
}
