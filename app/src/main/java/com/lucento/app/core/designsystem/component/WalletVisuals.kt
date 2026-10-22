package com.lucento.app.core.designsystem.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Savings
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
    }

val WalletType.label: String
    get() = when (this) {
        WalletType.CASH -> "Cash"
        WalletType.BANK -> "Bank account"
        WalletType.E_WALLET -> "E-wallet"
        WalletType.SAVINGS -> "Savings"
        WalletType.CREDIT_CARD -> "Credit card"
    }

/** Plural group title used when wallets are listed by type. */
val WalletType.groupLabel: String
    get() = when (this) {
        WalletType.CASH -> "Cash"
        WalletType.BANK -> "Bank accounts"
        WalletType.E_WALLET -> "E-wallets"
        WalletType.SAVINGS -> "Savings"
        WalletType.CREDIT_CARD -> "Credit cards"
    }

/** Wallet type icon on its own restrained tone, so account kinds are recognisable at a glance. */
@Composable
fun WalletTypeTile(type: WalletType, modifier: Modifier = Modifier, size: Dp = Sizes.iconTile) {
    val tone = LucentoTheme.extendedColors.tone(type)
    IconTile(icon = type.icon, modifier = modifier, container = tone.container, content = tone.content, size = size)
}
