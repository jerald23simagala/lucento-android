package com.lucento.app.core.designsystem.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.ui.graphics.vector.ImageVector
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
