package com.lucento.app.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lucento.app.R
import com.lucento.app.core.designsystem.theme.LucentoTheme

/**
 * The Lucento symbol — Rising Crest: three leaf-cut bars (Lucento Blue, Sky Blue, Gold) and an orange dot
 * (`res/drawable/lucento_symbol.xml`, converted losslessly from the brand repo's `brand/logo/symbol/`). Approved
 * artwork: shown exactly as supplied, never recoloured to UI tokens; the same artwork works on light and dark grounds.
 * The brand guide asks for the small cut below 32 px; no screen uses the symbol that small.
 *
 * When [animate] is true it settles in once — a brief brand moment — unless reduced motion is on.
 */
@Composable
fun LucentoMark(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    animate: Boolean = true,
) {
    val play = animate && !LucentoTheme.reducedMotion
    val entrance = remember { Animatable(if (play) 0f else 1f) }
    LaunchedEffect(play) {
        if (play) entrance.animateTo(1f, tween(durationMillis = 520, easing = FastOutSlowInEasing))
    }
    Image(
        painter = painterResource(R.drawable.lucento_symbol),
        contentDescription = "Lucento",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                alpha = entrance.value
                val scale = 0.85f + 0.15f * entrance.value
                scaleX = scale
                scaleY = scale
            },
    )
}

/**
 * The "lucento" wordmark (outlined Manrope Bold), navy on light grounds and white on dark, as the brand files supply
 * it. [height] sets the size; the width follows the artwork's proportions.
 */
@Composable
fun LucentoWordmark(modifier: Modifier = Modifier, height: Dp = 36.dp) {
    Image(
        painter = painterResource(if (LucentoTheme.isDark) R.drawable.lucento_wordmark_dark else R.drawable.lucento_wordmark_light),
        contentDescription = "Lucento",
        contentScale = ContentScale.Fit,
        modifier = modifier.size(width = height * WORDMARK_ASPECT, height = height),
    )
}

/**
 * The stacked lockup — symbol above wordmark — with the proportions of the brand's
 * `lockup/lucento-lockup-stacked-*.svg`: in its coordinates the symbol box is 4726 units tall, the wordmark box 1541
 * and the gap between them 310, so both are derived from [symbolSize] rather than chosen by eye.
 */
@Composable
fun LucentoStackedLockup(
    modifier: Modifier = Modifier,
    symbolSize: Dp = 96.dp,
    animate: Boolean = true,
    wordmarkModifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        LucentoMark(size = symbolSize, animate = animate)
        androidx.compose.foundation.layout.Spacer(Modifier.size(symbolSize * LOCKUP_GAP_RATIO))
        LucentoWordmark(height = symbolSize * LOCKUP_WORDMARK_RATIO, modifier = wordmarkModifier)
    }
}

/** Width / height of the wordmark artwork's viewBox (7200 × 1541). */
private const val WORDMARK_ASPECT = 7200f / 1541f

/** Stacked lockup: wordmark box and gap as fractions of the symbol box (1541 / 4726 and 310 / 4726 units). */
private const val LOCKUP_WORDMARK_RATIO = 1541f / 4726f
private const val LOCKUP_GAP_RATIO = 310f / 4726f
