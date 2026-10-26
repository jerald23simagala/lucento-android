package com.lucento.app.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.designsystem.theme.TonePair

/**
 * A page's statement figure on solid brand blue (elevated navy at night), e.g. Wallets' net worth and Recurring's
 * "due soon". Text inside uses `extendedColors.onHero` / `onHeroMuted`; labels sit on [HeroPill]s.
 */
@Composable
fun HeroCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val ext = LucentoTheme.extendedColors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = ext.hero,
        contentColor = ext.onHero,
    ) {
        Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md), content = content)
    }
}

/** A compact label pill on a [HeroCard]: white for the main figure, [HeroTones] sunlight / tangerine for the others. */
@Composable
fun HeroPill(label: String, tone: TonePair, modifier: Modifier = Modifier) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = tone.content,
        maxLines = 1,
        modifier = modifier
            .background(tone.container, RoundedCornerShape(50))
            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
    )
}

object HeroTones {
    val main: TonePair
        @Composable get() = LucentoTheme.extendedColors.let { TonePair(it.onHero, it.hero) }
    val sunlight: TonePair
        @Composable get() = LucentoTheme.extendedColors.let { TonePair(it.accent, it.onAccent) }
    val tangerine: TonePair
        @Composable get() = LucentoTheme.extendedColors.let { TonePair(it.warmAccent, it.onAccent) }
}
