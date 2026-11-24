package com.lucento.app.feature.wallets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.component.IconTile
import com.lucento.app.core.designsystem.component.LucentoBottomSheet
import com.lucento.app.core.designsystem.component.SheetHeader
import com.lucento.app.core.designsystem.component.filledField
import com.lucento.app.core.designsystem.component.icon
import com.lucento.app.core.designsystem.component.label
import com.lucento.app.core.designsystem.component.rememberHaptics
import com.lucento.app.core.designsystem.component.vector
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.OverlineStyle
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.domain.WalletIcon
import com.lucento.app.domain.WalletKind
import com.lucento.app.domain.WalletFamily
import com.lucento.app.domain.WalletType
import com.lucento.app.domain.family

/*
 * The wallet editor's look and kind (tester round, 2026-11-17): a sectioned type picker in place of the flat dropdown,
 * the kind of an investment, owned thing or loan, an icon for a wallet without a bank logo, and whether it counts as
 * spending money for Safe to spend.
 */

/** The icon a draft wallet shows: the chosen one, else its kind's, else its type's. */
internal fun WalletDraft.icon(): ImageVector = WalletIcon.fromKey(iconKey)?.vector ?: kind?.defaultIcon?.vector ?: type.icon

/** [WalletDraft.icon] on the type's tone. */
@Composable
internal fun DraftIconTile(draft: WalletDraft, size: androidx.compose.ui.unit.Dp = com.lucento.app.core.designsystem.theme.Sizes.iconTile) {
    val tone = LucentoTheme.extendedColors.tone(draft.type)
    IconTile(draft.icon(), container = tone.container, content = tone.content, size = size)
}

/** One line under each type in the picker: what it is for (MP2, a Philippine scheme, only for wallets in pesos). */
internal fun WalletType.pickerHint(currencyCode: String): String =
    when (this) {
        WalletType.CASH -> "Notes and coins"
        WalletType.BANK -> "Payroll, checking or current account"
        WalletType.E_WALLET -> "Mobile wallets and payment apps"
        WalletType.SAVINGS -> "Money set aside"
        WalletType.INVESTMENT -> if (currencyCode == "PHP") "Stocks, funds, MP2, time deposits, crypto" else "Stocks, funds, time deposits, crypto"
        WalletType.OTHER_ASSET -> "A home, a car, jewellery — valued by you"
        WalletType.RECEIVABLE -> "Money someone owes you"
        WalletType.CREDIT_CARD -> "Buy now, pay the card later"
        WalletType.LOAN -> "Car, home, salary loans, buy now pay later"
    }

/** The picker's sections: the four families the account picker groups by too, so both read the same. */
private val TypeSections: List<Pair<String, List<WalletType>>> =
    WalletFamily.entries.map { family -> family.label to WalletType.entries.filter { it.family == family } }

/** The chosen type as one filled field (its tile and name), opening [WalletTypePickerSheet]. */
@Composable
internal fun WalletTypeField(selected: WalletType, currencyCode: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .filledField()
            .clickable(role = Role.DropdownList, onClickLabel = "Change wallet type", onClick = onClick)
            .semantics { contentDescription = "Wallet type: ${selected.label}" }
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.lucento.app.core.designsystem.component.WalletTypeTile(selected, size = 32.dp)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(selected.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(selected.pickerHint(currencyCode), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Every wallet type in sections, each with what it is for; types the plan doesn't include carry the Pro mark. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WalletTypePickerSheet(
    /** The current type, checked; null when the sheet is choosing what to add. */
    selected: WalletType?,
    currencyCode: String,
    proTypes: Set<WalletType>,
    onSelect: (WalletType) -> Unit,
    onDismiss: () -> Unit,
    /** Types not offered at all (the guided setup leaves out what the plan doesn’t include, without mentioning Pro). */
    hidden: Set<WalletType> = emptySet(),
    /** What TalkBack reads for a marked type: the plan fact behind its mark. */
    proLabel: (WalletType) -> String = { "Lucento Pro feature" },
) {
    val haptics = rememberHaptics()
    LucentoBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            SheetHeader(title = "Type of wallet", subtitle = "It decides how its money is counted.", icon = Icons.Outlined.Category)
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).selectableGroup()) {
                TypeSections.forEach { (title, all) ->
                    val types = all.filter { it !in hidden || it == selected }
                    if (types.isEmpty()) return@forEach
                    item(key = "header-$title") { PickerSectionHeader(title) }
                    items(types, key = { it.name }) { type ->
                        val chosen = type == selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clip(MaterialTheme.shapes.small)
                                .selectable(selected = chosen, role = Role.RadioButton) {
                                    haptics.tick()
                                    onSelect(type)
                                }
                                .padding(vertical = Spacing.sm, horizontal = Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            com.lucento.app.core.designsystem.component.WalletTypeTile(type, size = 36.dp)
                            Spacer(Modifier.width(Spacing.md))
                            Column(Modifier.weight(1f)) {
                                Text(type.label, style = MaterialTheme.typography.bodyLarge, fontWeight = if (chosen) FontWeight.SemiBold else null)
                                Text(type.pickerHint(currencyCode), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (type in proTypes) {
                                Spacer(Modifier.width(Spacing.sm))
                                com.lucento.app.feature.pro.ProBadge(description = proLabel(type))
                            }
                            if (chosen) {
                                Spacer(Modifier.width(Spacing.xs))
                                Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                item(key = "bottom") { Spacer(Modifier.navigationBarsPadding().size(Spacing.md)) }
            }
        }
    }
}

/** The kinds a type offers, as chips; one is chosen at a time. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WalletKindChips(type: WalletType, currencyCode: String, selected: WalletKind?, onSelect: (WalletKind) -> Unit) {
    val haptics = rememberHaptics()
    // 48dp chips with room between them (design review, 2026-11-17: 40dp chips 4dp apart were too easy to miss).
    FlowRow(
        Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        WalletKind.of(type, currencyCode, selected).forEach { kind ->
            val chosen = kind == selected
            val colors = MaterialTheme.colorScheme
            Surface(
                shape = RoundedCornerShape(50),
                color = if (chosen) colors.primaryContainer else colors.surfaceContainerLowest,
                border = BorderStroke(if (chosen) 1.5.dp else 1.dp, if (chosen) colors.primary else LucentoTheme.extendedColors.borderDefault),
                modifier = Modifier
                    .selectable(selected = chosen, role = Role.RadioButton) {
                        haptics.tick()
                        onSelect(kind)
                    },
            ) {
                Row(
                    Modifier.heightIn(min = 48.dp).padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (chosen) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(Spacing.xxs))
                    }
                    Text(kind.labelFor(currencyCode), style = MaterialTheme.typography.labelLarge, color = if (chosen) colors.primary else colors.onSurface)
                }
            }
        }
    }
}

/** The wallet's icon as a filled field: its tile, its name and whether it is the suggested one; opens the picker. */
@Composable
internal fun WalletIconField(draft: WalletDraft, onClick: () -> Unit) {
    val chosen = WalletIcon.fromKey(draft.iconKey)
    val suggested = draft.kind?.defaultIcon
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .filledField()
            .clickable(role = Role.Button, onClickLabel = "Choose an icon", onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DraftIconTile(draft, size = 32.dp)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text("Icon", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                when {
                    chosen != null -> chosen.label
                    suggested != null -> "${suggested.label} · suggested for ${draft.kind!!.labelFor(draft.currencyCode)}"
                    else -> "The ${draft.type.label.lowercase()} icon"
                },
                style = MaterialTheme.typography.bodyLarge,
                // Wraps at large text sizes instead of cutting off what is suggested (design review, 2026-11-17).
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

/** Every [WalletIcon] in its group, on the wallet type's tone; the current one is outlined. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WalletIconPickerSheet(type: WalletType, current: WalletIcon?, suggested: WalletIcon?, onSelect: (WalletIcon) -> Unit, onDismiss: () -> Unit) {
    val haptics = rememberHaptics()
    val tone = LucentoTheme.extendedColors.tone(type)
    LucentoBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            SheetHeader(
                title = "Choose an icon",
                subtitle = "Shown on this wallet’s tile and card when it has no bank logo.",
                icon = Icons.Outlined.Category,
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 56.dp),
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                WalletIcon.Group.entries.forEach { group ->
                    item(key = "header-${group.name}", span = { GridItemSpan(maxLineSpan) }) { PickerSectionHeader(group.label) }
                    items(WalletIcon.entries.filter { it.group == group }, key = { it.key }) { icon ->
                        val isCurrent = icon == (current ?: suggested)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = tone.container,
                            border = if (isCurrent) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .selectable(selected = isCurrent, role = Role.RadioButton) {
                                    haptics.tick()
                                    onSelect(icon)
                                }
                                .semantics {
                                    contentDescription = icon.label
                                    if (icon == suggested) stateDescription = "Suggested"
                                },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(icon.vector, contentDescription = null, tint = tone.content, modifier = Modifier.size(26.dp))
                            }
                        }
                    }
                }
                item(key = "bottom", span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.navigationBarsPadding().size(Spacing.md)) }
            }
        }
    }
}

@Composable
private fun PickerSectionHeader(title: String) {
    Text(
        title.uppercase(),
        style = OverlineStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.md, bottom = Spacing.xxs, start = Spacing.xs)
            .clearAndSetSemantics {
                heading()
                contentDescription = title
            },
    )
}

/**
 * Whether this wallet counts towards Safe to spend's money on hand (tester round, 2026-11-17). An account: one switch,
 * on by default for cash, bank and e-wallet. A card: part of its credit, only when the person names how much — credit
 * is borrowed money, so it is off unless they choose it, and never more than the limit.
 */
@Composable
internal fun SpendingMoneySection(
    draft: WalletDraft,
    error: DraftError?,
    decimalSeparator: Char,
    fieldColors: androidx.compose.material3.TextFieldColors,
    onChange: (WalletDraft) -> Unit,
) {
    if (draft.type != WalletType.CREDIT_CARD) {
        com.lucento.app.core.designsystem.component.SwitchRow(
            title = "Spending money",
            description = "Counts towards Safe to spend. Leave it off for money you keep aside, like savings or investments.",
            checked = draft.spendable ?: draft.type.isSpendable,
            onCheckedChange = { onChange(draft.copy(spendable = it)) },
        )
        return
    }
    val limit = (com.lucento.app.core.money.MoneyParser.parse(draft.creditLimitText, draft.currencyCode, decimalSeparator)
        as? com.lucento.app.core.money.MoneyParser.Result.Success)?.money?.takeIf { it.amountMinor > 0L }
    val on = draft.spendable == true
    com.lucento.app.core.designsystem.component.SwitchRow(
        title = "Count credit as spending money",
        description = if (limit == null) {
            "Add the card’s credit limit above to use this."
        } else {
            "Credit is borrowed money. Turn this on only if you pay the card in full every month."
        },
        checked = on && limit != null,
        enabled = limit != null,
        onCheckedChange = { onChange(draft.copy(spendable = it)) },
    )
    if (on && limit != null) {
        Spacer(Modifier.size(Spacing.xs))
        val invalid = error == DraftError.SpendableCreditInvalid || error == DraftError.SpendableCreditTooHigh
        val fmt = com.lucento.app.core.designsystem.component.amountFormat()
        com.lucento.app.core.designsystem.component.AmountTextField(
            value = draft.spendableCreditText,
            onValueChange = { onChange(draft.copy(spendableCreditText = it)) },
            filter = { it.take(20) },
            label = { Text("Credit you count each month") },
            prefix = { Text(com.lucento.app.core.money.MoneyFormatter.currencySymbol(draft.currencyCode) + " ") },
            placeholder = { Text("0") },
            isError = invalid,
            textStyle = com.lucento.app.core.designsystem.theme.AmountStyles.medium,
            supportingText = {
                Text(
                    when (error) {
                        DraftError.SpendableCreditInvalid -> "Enter how much of the credit counts, like 5000${decimalSeparator}00."
                        DraftError.SpendableCreditTooHigh -> "That’s more than the card’s ${fmt.format(limit)} limit."
                        else -> "Safe to spend counts up to this much each month, less what you put on the card, and never more than is still available."
                    },
                )
            },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
            ),
            shape = com.lucento.app.core.designsystem.component.LucentoFields.shape,
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
