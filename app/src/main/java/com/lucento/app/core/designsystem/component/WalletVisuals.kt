package com.lucento.app.core.designsystem.component

import androidx.compose.material.icons.Icons
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.RequestQuote
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.HomeWork
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Sizes
import com.lucento.app.domain.WalletType

val WalletType.icon: ImageVector
    get() = when (this) {
        WalletType.CASH -> Icons.Outlined.Payments
        WalletType.BANK -> Icons.Outlined.AccountBalance
        WalletType.E_WALLET -> Icons.Outlined.PhoneAndroid
        WalletType.SAVINGS -> Icons.Outlined.Savings
        WalletType.CREDIT_CARD -> Icons.Outlined.CreditCard
        WalletType.LOAN -> Icons.Outlined.RequestQuote
        WalletType.OTHER_ASSET -> Icons.Outlined.HomeWork
    }

val WalletType.label: String
    get() = when (this) {
        WalletType.CASH -> "Cash"
        WalletType.BANK -> "Bank account"
        WalletType.E_WALLET -> "E-wallet"
        WalletType.SAVINGS -> "Savings"
        WalletType.CREDIT_CARD -> "Credit card"
        WalletType.LOAN -> "Loan"
        WalletType.OTHER_ASSET -> "Other asset"
    }

/** Plural group title used when wallets are listed by type. */
val WalletType.groupLabel: String
    get() = when (this) {
        WalletType.CASH -> "Cash"
        WalletType.BANK -> "Bank accounts"
        WalletType.E_WALLET -> "E-wallets"
        WalletType.SAVINGS -> "Savings"
        WalletType.CREDIT_CARD -> "Credit cards"
        WalletType.LOAN -> "Loans"
        WalletType.OTHER_ASSET -> "Other assets"
    }

/** Wallet type icon on its own restrained tone, so account kinds are recognisable at a glance. */
@Composable
fun WalletTypeTile(type: WalletType, modifier: Modifier = Modifier, size: Dp = Sizes.iconTile) {
    val tone = LucentoTheme.extendedColors.tone(type)
    IconTile(icon = type.icon, modifier = modifier, container = tone.container, content = tone.content, size = size)
}

/**
 * A compact wallet pill: type icon and name, 32dp tall (48dp touch target). The active wallet is navy-filled — never
 * the brand blue of the user's own message bubbles — and the default carries a small sun star.
 */
@Composable
fun WalletPill(
    label: String,
    icon: ImageVector,
    iconTint: Color?,
    selected: Boolean,
    isDefault: Boolean,
    role: Role,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val ext = LucentoTheme.extendedColors
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .selectable(selected = selected, role = role, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .height(32.dp)
                .background(if (selected) colors.inverseSurface else colors.surfaceContainerLowest, shape)
                .border(1.dp, if (selected) colors.inverseSurface else colors.outlineVariant, shape)
                .padding(start = 10.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val content = if (selected) colors.inverseOnSurface else colors.onSurface
            Icon(icon, contentDescription = null, tint = iconTint ?: content, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
            if (isDefault) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    tint = if (selected) ext.accent else ext.accentText,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
