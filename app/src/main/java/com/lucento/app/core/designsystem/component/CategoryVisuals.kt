package com.lucento.app.core.designsystem.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Sizes
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Category

/** Maps a stored icon key to a Material icon. Unknown keys fall back to a neutral glyph. */
object CategoryIcons {
    val Uncategorized: ImageVector get() = Icons.Filled.Category

    fun forKey(key: String): ImageVector = when (key) {
        "groceries" -> Icons.Filled.ShoppingCart
        "dining" -> Icons.Filled.Restaurant
        "transport" -> Icons.Filled.DirectionsBus
        "housing" -> Icons.Filled.Home
        "utilities" -> Icons.Filled.Bolt
        "health" -> Icons.Filled.LocalHospital
        "education" -> Icons.Filled.School
        "entertainment" -> Icons.Filled.Movie
        "shopping" -> Icons.Filled.ShoppingBag
        "travel" -> Icons.Filled.Flight
        "personal" -> Icons.Filled.Spa
        "gift" -> Icons.Filled.CardGiftcard
        "subscriptions" -> Icons.Filled.Repeat
        "work" -> Icons.Filled.Work
        "business" -> Icons.Filled.Storefront
        "investments" -> Icons.AutoMirrored.Filled.TrendingUp
        "refund" -> Icons.Filled.Receipt
        "income" -> Icons.Filled.Payments
        else -> Icons.Filled.Category
    }
}

val Category.icon: ImageVector get() = CategoryIcons.forKey(iconKey)

/**
 * Category icon tile: a saturated tile in the category's hue with a white glyph (see
 * [com.lucento.app.core.designsystem.theme.LucentoExtendedColors.categoryTones]). Transfers use the brand tint;
 * uncategorised income uses the income hue so money coming in is recognisable at a glance (the amount still
 * carries a "+").
 */
@Composable
fun CategoryTile(
    category: Category?,
    type: TransactionType,
    modifier: Modifier = Modifier,
    size: Dp = Sizes.iconTile,
) {
    if (type.isTransfer) {
        IconTile(
            icon = Icons.Filled.SwapHoriz,
            modifier = modifier,
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
            size = size,
        )
        return
    }
    val colors = LucentoTheme.extendedColors
    val tone = when {
        category != null -> colors.categoryTone(category.iconKey)
        type == TransactionType.INCOME -> colors.categoryTone("income")
        else -> colors.categoryFallback
    }
    IconTile(
        icon = category?.icon ?: CategoryIcons.Uncategorized,
        modifier = modifier,
        container = tone.container,
        content = tone.content,
        size = size,
    )
}
