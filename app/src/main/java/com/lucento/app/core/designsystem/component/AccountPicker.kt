package com.lucento.app.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.OverlineStyle
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.domain.AccountChoices
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletType

/*
 * Choosing an account (tester round, 2026-11-17: with many wallets, the transfer sheet's wrapping pills were a wall).
 * [AccountField] shows the choice like any other form field and opens [AccountPickerSheet]: the accounts grouped by
 * kind, each with its balance, the most recent first and a search box once there are more than six. An account that
 * can't be chosen stays listed, dimmed, with the reason. One picker for the transfer sheet, the transaction editor and
 * the composer's rows.
 */

/**
 * A chosen account as a form field: the wallet's tile, its name and beneath it its kind and balance, then ▾. [label]
 * sits inside the field when the form has no header of its own; TalkBack hears [spokenLabel] either way.
 */
@Composable
fun AccountField(
    wallet: Wallet?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    spokenLabel: String = label ?: "Account",
    placeholder: String = "Choose an account",
    isError: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val detail = wallet?.let { accountDetail(it) }
    val spokenDetail = wallet?.let { accountDetail(it, spoken = true) }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        color = colors.surfaceContainerLowest,
        border = BorderStroke(if (isError) 1.5.dp else 1.dp, if (isError) colors.error else colors.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clearAndSetSemantics {
                role = Role.DropdownList
                contentDescription = "$spokenLabel: " + (wallet?.let { "${it.name}, $spokenDetail" } ?: "none chosen")
                if (!enabled) disabled()
            },
    ) {
        Row(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            if (wallet != null) {
                WalletIdentityTile(wallet, size = 32.dp)
            } else {
                IconTile(Icons.Outlined.AccountBalanceWallet, size = 32.dp, content = colors.onSurfaceVariant, shape = RoundedCornerShape(8.dp))
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                if (label != null) Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1)
                // Two lines each at most, so a long name or the balance wraps at large text sizes instead of being cut off.
                Text(
                    wallet?.name ?: placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (wallet != null) colors.onSurface else colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (detail != null) {
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = if (isError) colors.error else colors.onSurfaceVariant)
        }
    }
}

/**
 * Every account to choose from, grouped into the four families with its balance (headings only when there are two or
 * more groups). Above [AccountChoices.SEARCH_FROM] accounts a search box appears and [recentIds] lead under "Recent"
 * (the transfer sheet passes where money last went). An account that [unavailable] gives a reason for stays listed,
 * dimmed, with that reason. Picking calls [onPick]; the caller closes the sheet.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AccountPickerSheet(
    title: String,
    wallets: List<Wallet>,
    selectedId: Long?,
    onPick: (Wallet) -> Unit,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    recentIds: List<Long> = emptyList(),
    unavailable: (Wallet) -> String? = { null },
) {
    val haptics = rememberHaptics()
    val institutions = LocalInstitutions.current
    var query by rememberSaveable { mutableStateOf("") }
    val searchable = wallets.size > AccountChoices.SEARCH_FROM
    val sections = remember(wallets, recentIds, query, institutions) {
        AccountChoices.sections(wallets, recentIds, query, typeLabel = { it.label }, institutionName = { institutions[it.institutionId]?.name })
    }
    LucentoBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            SheetHeader(
                title = title,
                subtitle = subtitle,
                icon = Icons.Outlined.AccountBalanceWallet,
                modifier = Modifier.padding(bottom = Spacing.xs),
            )
            if (searchable) {
                SearchField(value = query, onValueChange = { query = it.take(40) }, placeholder = "Search accounts")
                Spacer(Modifier.size(Spacing.xs))
            }
            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .selectableGroup(),
            ) {
                val headed = sections.size > 1
                sections.forEach { section ->
                    val recent = section.family == null
                    if (headed) {
                        item(key = "header-${section.family?.name ?: "recent"}") {
                            PickerGroupHeader(title = section.family?.label ?: "Recent")
                        }
                    }
                    items(section.wallets, key = { (if (recent) "recent-" else "account-") + it.id }) { wallet ->
                        AccountRow(
                            wallet = wallet,
                            selected = wallet.id == selectedId,
                            reason = unavailable(wallet),
                            onClick = {
                                haptics.tick()
                                onPick(wallet)
                            },
                        )
                    }
                }
                if (sections.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            if (query.isBlank()) "No accounts yet. Add one in Wallets." else "No account matches “${query.trim()}”.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = Spacing.lg),
                        )
                    }
                }
                item(key = "bottom") { Spacer(Modifier.navigationBarsPadding().size(Spacing.md)) }
            }
        }
    }
}

/** A family's heading: words only, since every row already carries its own tile. */
@Composable
private fun PickerGroupHeader(title: String) {
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

/** One account: its tile, name and what it is or why it can't be chosen, then its balance and a check when chosen. */
@Composable
private fun AccountRow(wallet: Wallet, selected: Boolean, reason: String?, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val enabled = reason == null
    val dim = if (enabled) Modifier else Modifier.alpha(0.45f)
    val meta = reason ?: rowMeta(wallet)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (enabled) Modifier.clickable(role = Role.RadioButton, onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) {
                this.selected = selected
                if (!enabled) {
                    disabled()
                    stateDescription = reason!!
                }
            }
            .padding(vertical = Spacing.sm, horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WalletIdentityTile(wallet, dim, size = 36.dp)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(wallet.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = dim)
            if (meta != null) {
                Text(meta, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(Spacing.sm))
        // As in Wallets: what it adds to net worth, so an amount owed reads −₱18,400.00 beside "Amount owed".
        AmountText(wallet.netWorthContribution, style = AmountStyles.row, color = colors.onSurfaceVariant, modifier = dim)
        if (selected) {
            Spacer(Modifier.width(Spacing.xs))
            Icon(Icons.Filled.Check, contentDescription = "Selected", tint = colors.primary)
        }
    }
}

/** Under an account's name: its bank or provider when the name doesn't already say it, and whose money it is. */
@Composable
@ReadOnlyComposable
private fun rowMeta(wallet: Wallet): String? {
    val institution = LocalInstitutions.current[wallet.institutionId]?.name?.takeUnless { wallet.name.contains(it, ignoreCase = true) }
    val owed = when {
        wallet.type.isLiability -> "Amount owed"
        wallet.type == WalletType.RECEIVABLE -> "Owed to you"
        else -> null
    }
    return listOfNotNull(institution, wallet.kind?.labelFor(wallet.currencyCode), owed).joinToString(" · ").ifEmpty { null }
}

/**
 * "Bank account · ₱6,210.40", "Credit card · −₱18,400.00": what the account is and what it adds to net worth. A debt's
 * minus sign says it is owed, so no second word repeats it (design review); money owed to you says so in words.
 */
@Composable
@ReadOnlyComposable
private fun accountDetail(wallet: Wallet, spoken: Boolean = false): String {
    val worth = wallet.netWorthContribution
    val amount = if (spoken) spokenAmount(worth, signed = true) else shownAmount(worth, signed = true)
    val held = if (wallet.type == WalletType.RECEIVABLE) "$amount owed to you" else amount
    // The kind where there is one ("Pag-IBIG MP2"), as the spending money sheet and Wallets say it (design review).
    return "${wallet.kind?.labelFor(wallet.currencyCode) ?: wallet.type.label} · $held"
}
