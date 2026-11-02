package com.lucento.app.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.money.Money
import com.lucento.app.core.money.MoneyFormatter

/**
 * Renders a [Money] value with tabular figures. Colour is neutral by default: Lucento only tints
 * amounts when the sign carries meaning (income vs expense), and the sign is always shown too.
 *
 * @param signed show "−" for negative values (and "+" for positive ones when [showPlus]).
 */
@Composable
fun AmountText(
    money: Money,
    modifier: Modifier = Modifier,
    style: TextStyle = AmountStyles.row,
    color: Color = LocalContentColor.current,
    signed: Boolean = true,
    showPlus: Boolean = false,
) {
    val formatted = if (signed) MoneyFormatter.formatSigned(money, showPlus) else MoneyFormatter.format(money)
    FittingText(remember(formatted) { AnnotatedString(formatted) }, style, color, minScale = 0.6f, modifier = modifier)
}

/**
 * One line, shrunk to fit. Below [minScale] (large font sizes on narrow screens, P1-7) it keeps shrinking to
 * [FLOOR_SCALE] rather than wrapping: a number split over two lines ("1,234,5" / "67.89") reads as two amounts (QA
 * note 11). A money figure is never clipped either — a cut-off "$1,234,5" would show a different amount. The fit
 * restarts whenever the available width changes, so a figure grows back when there is room again.
 */
@Composable
private fun FittingText(
    text: AnnotatedString,
    style: TextStyle,
    color: Color,
    minScale: Float,
    modifier: Modifier = Modifier,
) {
    var scale by remember(text, style) { mutableFloatStateOf(1f) }
    var ready by remember(text, style) { mutableStateOf(false) }
    var fittedWidth by remember(text, style) { mutableStateOf(-1) }
    val scaled = if (scale == 1f || !style.fontSize.isSp) {
        style
    } else {
        style.copy(
            fontSize = style.fontSize * scale,
            lineHeight = if (style.lineHeight.isSp) style.lineHeight * scale else style.lineHeight,
        )
    }
    Text(
        text = text,
        style = scaled,
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Visible,
        modifier = modifier.drawWithContent { if (ready) drawContent() },
        onTextLayout = { result ->
            val width = result.layoutInput.constraints.maxWidth
            when {
                // New room (rotation, a resized pane, a font change): start again from full size.
                width != fittedWidth -> {
                    fittedWidth = width
                    if (scale != 1f) {
                        scale = 1f
                        ready = false
                    } else if (!result.hasVisualOverflow) {
                        ready = true
                    } else {
                        scale = (scale - 0.06f).coerceAtLeast(minScale)
                    }
                }
                result.hasVisualOverflow && scale > minScale -> scale = (scale - 0.06f).coerceAtLeast(minScale)
                result.hasVisualOverflow && scale > FLOOR_SCALE -> scale = (scale - 0.06f).coerceAtLeast(FLOOR_SCALE)
                else -> ready = true
            }
        },
    )
}

/** The smallest a figure ever gets, only when even [minScale] doesn't fit; still drawn whole, never wrapped. */
private const val FLOOR_SCALE = 0.35f

/**
 * A prominent money figure: minor units are typeset smaller than whole units ([fractionScale]),
 * the text shrinks to fit narrow screens instead of wrapping or truncating, and value changes
 * roll gently up or down (skipped when the system asks to remove animations).
 */
@Composable
fun MoneyText(
    money: Money,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    showPlus: Boolean = false,
    fractionScale: Float = 0.62f,
    minScale: Float = 0.5f,
    animate: Boolean = true,
) {
    val reducedMotion = LucentoTheme.reducedMotion
    if (!animate || reducedMotion) {
        ShrinkingMoney(money, style, color, showPlus, fractionScale, minScale, modifier)
        return
    }
    AnimatedContent(
        targetState = money,
        transitionSpec = {
            val rising = targetState.currencyCode == initialState.currencyCode &&
                targetState.amountMinor > initialState.amountMinor
            val enter = slideInVertically(tween(260)) { h -> if (rising) h / 3 else -h / 3 } + fadeIn(tween(260))
            val exit = slideOutVertically(tween(180)) { h -> if (rising) -h / 3 else h / 3 } + fadeOut(tween(140))
            (enter togetherWith exit).using(SizeTransform(clip = false))
        },
        contentAlignment = Alignment.CenterStart,
        modifier = modifier,
        label = "money",
    ) { value ->
        ShrinkingMoney(value, style, color, showPlus, fractionScale, minScale, Modifier)
    }
}

@Composable
private fun ShrinkingMoney(
    money: Money,
    style: TextStyle,
    color: Color,
    showPlus: Boolean,
    fractionScale: Float,
    minScale: Float,
    modifier: Modifier,
) {
    val formatted = MoneyFormatter.formatSigned(money, showPlus)
    val text = remember(formatted, style, fractionScale) { typeset(formatted, money.fractionDigits, style, fractionScale) }
    FittingText(text, style, color, minScale, modifier)
}

private fun typeset(formatted: String, fractionDigits: Int, style: TextStyle, fractionScale: Float): AnnotatedString {
    val span = MoneyFormatter.fractionSpan(formatted, fractionDigits)
    if (span == null || fractionScale >= 1f || !style.fontSize.isSp) return AnnotatedString(formatted)
    return buildAnnotatedString {
        append(formatted)
        // Em-relative so the ratio survives shrink-to-fit.
        addStyle(SpanStyle(fontSize = fractionScale.em), span.first, span.last + 1)
    }
}
