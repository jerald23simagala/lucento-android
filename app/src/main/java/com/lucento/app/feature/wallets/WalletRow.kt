package com.lucento.app.feature.wallets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.component.AmountText
import com.lucento.app.core.designsystem.component.WalletIdentityTile
import com.lucento.app.core.designsystem.component.institution
import com.lucento.app.core.designsystem.component.WalletTypeTile
import com.lucento.app.core.designsystem.component.label
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Sizes
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.domain.BalanceFlag
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.balanceFlag
import kotlinx.coroutines.launch

/**
 * A wallet line: type tile, name, kind and currency, and its effect on net worth. Liabilities show
 * the amount owed as a negative figure with an explicit "owed" caption, so meaning never relies on colour.
 * A balance that looks wrong ([balanceFlag]) gets a warning icon whose tooltip says why and a "check" caption.
 */
@Composable
fun WalletRow(
    wallet: Wallet,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    /** Tighter height, a smaller tile and inner padding for rows that sit inside a card. */
    dense: Boolean = false,
) {
    val owes = wallet.type.isLiability && !wallet.balance.isZero
    val flag = wallet.balanceFlag
    val ext = LucentoTheme.extendedColors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (dense) 56.dp else 64.dp)
            .let { if (onClick != null) it.clickable(role = Role.Button, onClickLabel = "Open account", onClick = onClick) else it }
            .padding(horizontal = if (dense) Spacing.md else 0.dp, vertical = if (dense) Spacing.xs + Spacing.xxs else Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WalletIdentityTile(wallet, size = if (dense) 36.dp else Sizes.iconTile)
        Spacer(Modifier.width(if (dense) Spacing.md - Spacing.xxs else Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                wallet.name,
                style = if (dense) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(walletOwnership(wallet), wallet.institution()?.name, wallet.type.label, wallet.currencyCode).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (flag != null) BalanceFlagIcon(flag) else Spacer(Modifier.width(Spacing.md))
        Column(horizontalAlignment = Alignment.End) {
            AmountText(
                money = wallet.netWorthContribution,
                style = AmountStyles.row,
                color = when {
                    flag != null -> ext.warning
                    owes -> ext.negative
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            val caption = when {
                flag == BalanceFlag.OWED_TO_YOU -> "in credit · check"
                flag != null -> "below zero · check"
                wallet.type.isLiability -> if (owes) "owed" else "nothing owed"
                else -> null
            }
            if (caption != null) {
                Text(
                    caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (flag != null) ext.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Why a balance was flagged, in a sentence; shown in the row's tooltip and on the wallet page. */
fun BalanceFlag.explanation(): String = when (this) {
    BalanceFlag.BELOW_ZERO -> "This balance can’t really be below zero. A transaction may be missing or recorded twice."
    BalanceFlag.OVERDRAWN -> "Below zero. Fine if the account really is overdrawn; otherwise a deposit may be missing."
    BalanceFlag.OWED_TO_YOU -> "This shows you’re owed money — usually an overpayment or a purchase that wasn’t recorded."
}

/** A warning icon that explains the flag in a tooltip on tap (and on long-press), so the reason is one touch away. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BalanceFlagIcon(flag: BalanceFlag) {
    val state = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(flag.explanation()) } },
        state = state,
    ) {
        IconButton(onClick = { scope.launch { state.show() } }) {
            Icon(
                Icons.Outlined.WarningAmber,
                contentDescription = "Balance needs checking. ${flag.explanation()}",
                tint = LucentoTheme.extendedColors.warning,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Lucento Shared: whose wallet it is, in words — "Shared", "Yours", or a person's ("Olivia’s"). An organizational label
 * only: everyone in the space sees every wallet. Null outside a Shared Space, where nothing is shown.
 */
@Composable
fun walletOwnership(wallet: Wallet): String? {
    val access = com.lucento.app.feature.sharedspace.LocalSharedAccess.current
    if (!access.inSpace) return null
    return when (wallet.classification) {
        null, com.lucento.app.domain.WalletClassification.SHARED -> "Shared"
        com.lucento.app.domain.WalletClassification.OWNER ->
            if (access.role == com.lucento.app.domain.SpaceRole.OWNER) "Yours" else "${access.ownerDisplay}’s"
        com.lucento.app.domain.WalletClassification.PARTNER ->
            if (access.isPartner) "Yours" else "${access.partnerName ?: "Your partner"}’s"
    }
}

/**
 * Lucento Shared: "Belongs to: Shared" under a wallet. The Owner can change it (Shared, theirs, their Partner's); a
 * Partner reads it. It never changes who can see or use the wallet. Nothing is shown outside a Shared Space.
 */
@Composable
fun WalletOwnershipRow(wallet: Wallet, onChange: (com.lucento.app.domain.WalletClassification) -> Unit) {
    val access = com.lucento.app.feature.sharedspace.LocalSharedAccess.current
    val current = walletOwnership(wallet) ?: return
    var open by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val canChange = access.canManageStructure
    androidx.compose.foundation.layout.Box(Modifier.padding(top = Spacing.sm)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Sizes.minTouch)
                .let { if (canChange) it.clickable(role = Role.Button, onClickLabel = "Change who it belongs to") { open = true } else it }
                .padding(horizontal = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Icon(
                Icons.Outlined.Group,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Sizes.icon),
            )
            Spacer(Modifier.width(Spacing.sm))
            Text("Belongs to", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(current, style = MaterialTheme.typography.labelLarge, color = if (canChange) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(
                com.lucento.app.domain.WalletClassification.SHARED to "Shared",
                com.lucento.app.domain.WalletClassification.OWNER to "Yours",
                com.lucento.app.domain.WalletClassification.PARTNER to "${access.partnerName ?: "Your partner"}’s",
            ).forEach { (value, label) ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        open = false
                        onChange(value)
                    },
                )
            }
        }
    }
}
