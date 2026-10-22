package com.lucento.app.feature.wallets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.component.AmountText
import com.lucento.app.core.designsystem.component.WalletTypeTile
import com.lucento.app.core.designsystem.component.label
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.domain.Wallet

/**
 * A wallet line: type tile, name, kind and currency, and its effect on net worth. Liabilities show
 * the amount owed as a negative figure with an explicit "owed" caption, so meaning never relies on colour.
 */
@Composable
fun WalletRow(
    wallet: Wallet,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val owes = wallet.type.isLiability && !wallet.balance.isZero
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .let { if (onClick != null) it.clickable(role = Role.Button, onClickLabel = "Open account", onClick = onClick) else it }
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WalletTypeTile(wallet.type)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                wallet.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${wallet.type.label} · ${wallet.currencyCode}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(Spacing.md))
        Column(horizontalAlignment = Alignment.End) {
            AmountText(
                money = wallet.netWorthContribution,
                style = AmountStyles.row,
                color = if (owes) LucentoTheme.extendedColors.negative else MaterialTheme.colorScheme.onSurface,
            )
            if (wallet.type.isLiability) {
                Text(
                    if (owes) "owed" else "nothing owed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
