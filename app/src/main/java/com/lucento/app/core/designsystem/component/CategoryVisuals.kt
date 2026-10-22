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
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector
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
