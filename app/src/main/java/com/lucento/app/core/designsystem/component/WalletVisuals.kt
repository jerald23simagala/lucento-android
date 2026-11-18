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
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Sizes
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletIcon
import com.lucento.app.domain.WalletType
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Agriculture
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.CurrencyBitcoin
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Elderly
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.LocalAtm
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MonetizationOn
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.TwoWheeler
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material.icons.outlined.WorkspacePremium

val WalletType.icon: ImageVector
    get() = when (this) {
        WalletType.CASH -> Icons.Outlined.Payments
        WalletType.BANK -> Icons.Outlined.AccountBalance
        WalletType.E_WALLET -> Icons.Outlined.PhoneAndroid
        WalletType.SAVINGS -> Icons.Outlined.Savings
        WalletType.INVESTMENT -> Icons.AutoMirrored.Outlined.TrendingUp
        WalletType.CREDIT_CARD -> Icons.Outlined.CreditCard
        WalletType.LOAN -> Icons.Outlined.RequestQuote
        WalletType.RECEIVABLE -> Icons.Outlined.Handshake
        WalletType.OTHER_ASSET -> Icons.Outlined.HomeWork
    }

val WalletType.label: String
    get() = when (this) {
        WalletType.CASH -> "Cash"
        WalletType.BANK -> "Bank account"
        WalletType.E_WALLET -> "E-wallet"
        WalletType.SAVINGS -> "Savings"
        WalletType.INVESTMENT -> "Investment"
        WalletType.CREDIT_CARD -> "Credit card"
        WalletType.LOAN -> "Loan"
        WalletType.RECEIVABLE -> "Receivable"
        // Tester round (2026-11-17): investments have their own type now; this one is what is valued by hand.
        WalletType.OTHER_ASSET -> "Property & valuables"
    }

/** Plural group title used when wallets are listed by type. */
val WalletType.groupLabel: String
    get() = when (this) {
        WalletType.CASH -> "Cash"
        WalletType.BANK -> "Bank accounts"
        WalletType.E_WALLET -> "E-wallets"
        WalletType.SAVINGS -> "Savings"
        WalletType.INVESTMENT -> "Investments"
        WalletType.CREDIT_CARD -> "Credit cards"
        WalletType.LOAN -> "Loans"
        WalletType.RECEIVABLE -> "Receivables"
        WalletType.OTHER_ASSET -> "Property & valuables"
    }

/** Wallet type icon on its own restrained tone, so account kinds are recognisable at a glance. */
@Composable
fun WalletTypeTile(type: WalletType, modifier: Modifier = Modifier, size: Dp = Sizes.iconTile) {
    val tone = LucentoTheme.extendedColors.tone(type)
    IconTile(icon = type.icon, modifier = modifier, container = tone.container, content = tone.content, size = size)
}

/**
 * A wallet's own icon (tester round, 2026-11-17): the one the person chose, else its kind's, else its type's — always
 * on the type's tone, so a cash wallet stays yellow and a loan violet whatever icon it shows.
 */
val Wallet.displayIcon: ImageVector
    get() = WalletIcon.fromKey(iconKey)?.vector ?: kind?.defaultIcon?.vector ?: type.icon

/** [Wallet.displayIcon] on the wallet type's tone: the tile of a wallet without a bank or provider logo. */
@Composable
fun WalletIconTile(wallet: Wallet, modifier: Modifier = Modifier, size: Dp = Sizes.iconTile) {
    val tone = LucentoTheme.extendedColors.tone(wallet.type)
    IconTile(icon = wallet.displayIcon, modifier = modifier, container = tone.container, content = tone.content, size = size)
}

/** The drawing for each [WalletIcon]. */
val WalletIcon.vector: ImageVector
    get() = when (this) {
        WalletIcon.PIGGY -> Icons.Outlined.Savings
        WalletIcon.CHART -> Icons.AutoMirrored.Outlined.ShowChart
        WalletIcon.RISING -> Icons.AutoMirrored.Outlined.TrendingUp
        WalletIcon.PIE -> Icons.Outlined.PieChart
        WalletIcon.SPROUT -> Icons.Outlined.Spa
        WalletIcon.LOCK -> Icons.Outlined.Lock
        WalletIcon.CERTIFICATE -> Icons.Outlined.WorkspacePremium
        WalletIcon.CRYPTO -> Icons.Outlined.CurrencyBitcoin
        WalletIcon.COIN -> Icons.Outlined.MonetizationOn
        WalletIcon.RETIREMENT -> Icons.Outlined.Elderly
        WalletIcon.SHIELD -> Icons.Outlined.HealthAndSafety
        WalletIcon.GROUP -> Icons.Outlined.Groups
        WalletIcon.BANK -> Icons.Outlined.AccountBalance
        WalletIcon.HOUSE -> Icons.Outlined.Home
        WalletIcon.APARTMENT -> Icons.Outlined.Apartment
        WalletIcon.LAND -> Icons.Outlined.Landscape
        WalletIcon.FARM -> Icons.Outlined.Agriculture
        WalletIcon.CAR -> Icons.Outlined.DirectionsCar
        WalletIcon.MOTORBIKE -> Icons.Outlined.TwoWheeler
        WalletIcon.GEM -> Icons.Outlined.Diamond
        WalletIcon.WATCH -> Icons.Outlined.Watch
        WalletIcon.LAPTOP -> Icons.Outlined.Laptop
        WalletIcon.STORE -> Icons.Outlined.Storefront
        WalletIcon.BOX -> Icons.Outlined.Inventory2
        WalletIcon.CASH -> Icons.Outlined.Payments
        WalletIcon.WALLET -> Icons.Outlined.AccountBalanceWallet
        WalletIcon.CARD -> Icons.Outlined.CreditCard
        WalletIcon.PHONE -> Icons.Outlined.PhoneAndroid
        WalletIcon.ATM -> Icons.Outlined.LocalAtm
        WalletIcon.GIFT -> Icons.Outlined.CardGiftcard
        WalletIcon.FAMILY -> Icons.Outlined.FamilyRestroom
        WalletIcon.SCHOOL -> Icons.Outlined.School
        WalletIcon.TRAVEL -> Icons.Outlined.Flight
        WalletIcon.WORK -> Icons.Outlined.Work
        WalletIcon.BAG -> Icons.Outlined.ShoppingBag
        WalletIcon.HANDSHAKE -> Icons.Outlined.Handshake
        WalletIcon.DOCUMENT -> Icons.Outlined.RequestQuote
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
