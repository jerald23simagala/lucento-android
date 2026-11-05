package com.lucento.app.feature.wallets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lucento.app.core.banks.Institution
import com.lucento.app.core.banks.InstitutionRegistry
import com.lucento.app.core.designsystem.component.InstitutionAvatar
import com.lucento.app.core.designsystem.component.LocalInstitutions
import com.lucento.app.core.designsystem.component.LucentoBottomSheet
import com.lucento.app.core.designsystem.component.SearchField
import com.lucento.app.core.designsystem.component.SheetHeader
import com.lucento.app.core.designsystem.component.rememberHaptics
import com.lucento.app.core.designsystem.theme.OverlineStyle
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

private val kindOrder = listOf("traditional_bank", "digital_bank", "financial_platform")

private fun kindHeading(type: String): String = when (type) {
    "traditional_bank" -> "Banks"
    "digital_bank" -> "Digital banks"
    "financial_platform" -> "Payments & investing"
    else -> "Other"
}

/** What a group's heading stands for, so its rows don't repeat it. */
internal enum class GroupedBy { NOTHING, KIND, COUNTRY, HOME, ELSEWHERE }

/** One headed group of picker rows; a null [title] means the rows stand alone. */
internal data class PickerSection(val key: String, val title: String?, val groupedBy: GroupedBy, val items: List<Institution>)

/**
 * How the picker groups [results] (already ordered by [InstitutionRegistry.search]). Browsing one country groups it by
 * kind (banks, digital banks, payments); browsing every country groups by country; a search shows the chosen
 * country's matches first, then other countries'.
 */
internal fun pickerSections(
    results: List<Institution>,
    country: String?,
    searching: Boolean,
    countryLabel: (String) -> String = { countryName(it) },
): List<PickerSection> = when {
    searching && country != null -> {
        val (home, elsewhere) = results.partition { it.country == country }
        listOfNotNull(
            home.takeIf { it.isNotEmpty() }?.let { PickerSection("home", "In ${countryLabel(country)}", GroupedBy.HOME, it) },
            elsewhere.takeIf { it.isNotEmpty() }?.let { PickerSection("elsewhere", "Other countries", GroupedBy.ELSEWHERE, it) },
        )
    }
    searching -> if (results.isEmpty()) emptyList() else listOf(PickerSection("all", null, GroupedBy.NOTHING, results))
    country != null -> results.groupBy { it.type }.entries
        .sortedBy { (type, _) -> kindOrder.indexOf(type).takeIf { it >= 0 } ?: kindOrder.size }
        .map { (type, items) -> PickerSection(type, kindHeading(type), GroupedBy.KIND, items) }
    else -> results.groupBy { it.country }.entries.sortedBy { countryLabel(it.key) }
        .map { (code, items) -> PickerSection(code, countryLabel(code), GroupedBy.COUNTRY, items) }
}

/** A row's second line: why it matched (an alias), then whatever its group heading doesn't already say. */
internal fun pickerSubtitle(institution: Institution, groupedBy: GroupedBy, alias: String?, countryLabel: (String) -> String = { countryName(it) }): String? =
    listOfNotNull(
        alias?.let { "Also known as $it" },
        institutionKind(institution.type).takeIf { groupedBy != GroupedBy.KIND },
        countryLabel(institution.country).takeIf { groupedBy == GroupedBy.ELSEWHERE || groupedBy == GroupedBy.NOTHING },
    ).joinToString(" · ").ifEmpty { null }

private const val ALL_COUNTRIES = "*"

/**
 * Choose the bank or platform a wallet belongs to, or none. Opens on the wallet's current institution's country, else
 * the device's ([country]); the country can be changed, or widened to every country. Search matches names and
 * aliases in any country and lists the chosen country's matches first. Display only — it changes the wallet's look,
 * never its money.
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
    // null until the person picks: the default follows the registry once it has loaded.
    var countryChoice by rememberSaveable { mutableStateOf<String?>(null) }
    val defaultCountry = remember(registry, selectedId, country) {
        registry[selectedId]?.country ?: country?.takeIf { it in registry.countryCounts }
    }
    val shownCountry = when (countryChoice) {
        null -> defaultCountry
        ALL_COUNTRIES -> null
        else -> countryChoice
    }
    val searching = query.isNotBlank()
    val results = remember(query, registry, shownCountry) { registry.search(query, shownCountry) }
    val sections = remember(results, shownCountry, searching) { pickerSections(results, shownCountry, searching) }
    val listState = rememberLazyListState()
    LaunchedEffect(shownCountry) { listState.scrollToItem(0) }
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
            )
            CountryChooser(
                registry = registry,
                home = country,
                shown = shownCountry,
                onChoose = {
                    haptics.tick()
                    countryChoice = it ?: ALL_COUNTRIES
                },
                modifier = Modifier.padding(vertical = Spacing.xs),
            )
            LazyColumn(Modifier.fillMaxWidth().weight(1f).selectableGroup(), state = listState) {
                if (!searching) {
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
                sections.forEach { section ->
                    section.title?.let { title ->
                        item(key = "header-${section.key}") { GroupHeader(sectionIcon(section), title) }
                    }
                    items(section.items, key = { it.id }) { institution ->
                        PickerRow(
                            selected = institution.id == selectedId,
                            title = institution.name,
                            subtitle = pickerSubtitle(institution, section.groupedBy, registry.matchedAlias(institution, query)),
                            leading = { InstitutionAvatar(institution, size = 32.dp) },
                            onClick = {
                                haptics.tick()
                                onSelect(institution)
                            },
                        )
                    }
                }
                if (searching && results.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            "Nothing matches “${query.trim()}” in any country. Choose “No bank or provider” and name the wallet " +
                                "after your bank — nothing else changes.",
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

private fun sectionIcon(section: PickerSection): ImageVector = when (section.groupedBy) {
    GroupedBy.HOME -> Icons.Outlined.Place
    GroupedBy.ELSEWHERE, GroupedBy.COUNTRY, GroupedBy.NOTHING -> Icons.Outlined.Public
    GroupedBy.KIND -> when (section.key) {
        "digital_bank" -> Icons.Outlined.Smartphone
        "financial_platform" -> Icons.Outlined.AccountBalanceWallet
        else -> Icons.Outlined.AccountBalance
    }
}

/**
 * The countries the chooser lists: "All countries" is separate; the device's country and the one on screen come
 * first ([pinned]), then every other country alphabetically.
 */
internal fun countryOptions(registry: InstitutionRegistry, home: String?, shown: String?, label: (String) -> String = { countryName(it) }): Pair<List<String>, List<String>> {
    val pinned = listOfNotNull(home, shown).distinct().filter { it in registry.countryCounts }
    return pinned to registry.countryCounts.keys.filter { it !in pinned }.sortedBy(label)
}

/** The country the list shows, as a chip that opens every country with its count (and "All countries"). */
@Composable
private fun CountryChooser(registry: InstitutionRegistry, home: String?, shown: String?, onChoose: (String?) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val (pinned, others) = remember(registry, home, shown) { countryOptions(registry, home, shown) }
    val label = shown?.let { countryName(it) } ?: "All countries"
    val count = shown?.let { registry.countryCounts[it] } ?: registry.all.size
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box {
            AssistChip(
                onClick = { open = true },
                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(if (shown == null) Icons.Outlined.Public else Icons.Outlined.Place, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.semantics { contentDescription = "Country: $label. Change country" },
            )
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 380.dp)) {
                CountryMenuItem("All countries", registry.all.size, selected = shown == null) {
                    open = false
                    onChoose(null)
                }
                pinned.forEach { code ->
                    CountryMenuItem(countryName(code), registry.countryCounts.getValue(code), selected = code == shown) {
                        open = false
                        onChoose(code)
                    }
                }
                if (pinned.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = Spacing.xs))
                others.forEach { code ->
                    CountryMenuItem(countryName(code), registry.countryCounts.getValue(code), selected = code == shown) {
                        open = false
                        onChoose(code)
                    }
                }
            }
        }
        Spacer(Modifier.width(Spacing.sm))
        Text(
            if (count == 1) "1 bank or provider" else "$count banks and providers",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CountryMenuItem(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        onClick = onClick,
        leadingIcon = { if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) else Spacer(Modifier.size(24.dp)) },
        trailingIcon = { Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = Modifier.semantics { this.selected = selected; contentDescription = "$label, $count" },
    )
}

@Composable
private fun GroupHeader(icon: ImageVector, title: String) {
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
private fun PickerRow(selected: Boolean, title: String, subtitle: String?, leading: @Composable () -> Unit, onClick: () -> Unit) {
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
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (selected) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
    }
}
