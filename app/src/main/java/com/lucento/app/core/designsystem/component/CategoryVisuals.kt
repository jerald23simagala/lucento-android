package com.lucento.app.core.designsystem.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CardMembership
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.ChildFriendly
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.Wifi
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

    @Suppress("DEPRECATION") // DirectionsBike moved to AutoMirrored in newer icon sets; the filled one still renders.
    fun forKey(key: String): ImageVector = when (key) {
        // Housing and utilities
        "housing" -> Icons.Filled.Home
        "mortgage", "loan", "bank_fees" -> Icons.Filled.AccountBalance
        "tax", "fees", "refund" -> Icons.Filled.Receipt
        "insurance", "emergency" -> Icons.Filled.Security
        "utilities" -> Icons.Filled.Bolt
        "water" -> Icons.Filled.Opacity
        "heating" -> Icons.Filled.Whatshot
        "internet" -> Icons.Filled.Wifi
        "phone" -> Icons.Filled.PhoneAndroid
        "maintenance", "repairs" -> Icons.Filled.Build
        "home_supplies" -> Icons.Filled.LocalLaundryService
        "furniture" -> Icons.Filled.Weekend
        "security" -> Icons.Filled.Lock
        // Food
        "groceries" -> Icons.Filled.ShoppingCart
        "dining" -> Icons.Filled.Restaurant
        "cafe" -> Icons.Filled.LocalCafe
        "takeout" -> Icons.Filled.Fastfood
        "work_meals" -> Icons.Filled.RestaurantMenu
        "convenience", "business" -> Icons.Filled.Storefront
        "business_travel" -> Icons.Filled.BusinessCenter
        // Transport
        "transport" -> Icons.Filled.DirectionsBus
        "fuel" -> Icons.Filled.LocalGasStation
        "taxi" -> Icons.Filled.LocalTaxi
        "parking" -> Icons.Filled.LocalParking
        "car" -> Icons.Filled.DirectionsCar
        "registration" -> Icons.Filled.Description
        "bicycle" -> Icons.Filled.DirectionsBike
        // Health
        "health" -> Icons.Filled.LocalHospital
        "dental" -> Icons.Filled.Healing
        "vision" -> Icons.Filled.Visibility
        "pharmacy" -> Icons.Filled.LocalPharmacy
        "wellbeing" -> Icons.Filled.Mood
        "fitness" -> Icons.Filled.FitnessCenter
        "sports" -> Icons.Filled.Pool
        // Personal and family
        "shopping" -> Icons.Filled.ShoppingBag
        "personal" -> Icons.Filled.Spa
        "grooming" -> Icons.Filled.ContentCut
        "childcare" -> Icons.Filled.ChildCare
        "education" -> Icons.Filled.School
        "tuition" -> Icons.Filled.LocalLibrary
        "baby" -> Icons.Filled.ChildFriendly
        "pets" -> Icons.Filled.Pets
        "family", "community" -> Icons.Filled.People
        "gift" -> Icons.Filled.CardGiftcard
        // Lifestyle
        "streaming" -> Icons.Filled.LiveTv
        "subscriptions" -> Icons.Filled.Repeat
        "hobbies" -> Icons.Filled.Palette
        "games" -> Icons.Filled.SportsEsports
        "books" -> Icons.Filled.Book
        "entertainment" -> Icons.Filled.Movie
        "travel" -> Icons.Filled.Flight
        "hotel" -> Icons.Filled.Hotel
        "social" -> Icons.Filled.LocalBar
        "outdoor" -> Icons.Filled.Terrain
        // Obligations, savings, work
        "credit_card" -> Icons.Filled.CreditCard
        "debt", "income" -> Icons.Filled.Payments
        "legal" -> Icons.Filled.Gavel
        "savings" -> Icons.Filled.Savings
        "retirement" -> Icons.Filled.BeachAccess
        "investing", "investments" -> Icons.AutoMirrored.Filled.TrendingUp
        "work", "work_expenses" -> Icons.Filled.Work
        "home_office" -> Icons.Filled.Computer
        "membership" -> Icons.Filled.CardMembership
        "certification" -> Icons.Filled.EmojiEvents
        "supplies" -> Icons.Filled.Print
        "donation" -> Icons.Filled.Favorite
        else -> Icons.Filled.Category
    }
}

val Category.icon: ImageVector get() = CategoryIcons.forKey(iconKey)

/**
 * Category icon tile: a tonal tile in the category's hue with a saturated glyph (see
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
    if (type.isAdjustment) {
        // Warning-toned: a balance override is where tracking and reality parted, worth noticing in history.
        IconTile(
            icon = Icons.Filled.Tune,
            modifier = modifier,
            container = LucentoTheme.extendedColors.warningContainer,
            content = LucentoTheme.extendedColors.warning,
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
