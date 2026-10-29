package com.lucento.app.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import com.lucento.app.core.designsystem.theme.FontScaleCaps
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Motion
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.designsystem.theme.WithFontScaleCap
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
    Text(
        text = if (signed) MoneyFormatter.formatSigned(money, showPlus) else MoneyFormatter.format(money),
        modifier = modifier,
        style = style,
        color = color,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

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
    /** A 3dp gold rule under this figure. Used for the primary number on a hero card. */
    goldRule: Boolean = false,
) {
    val figure: @Composable (Modifier) -> Unit = { figureModifier ->
        val body: @Composable () -> Unit = {
            val reducedMotion = LucentoTheme.reducedMotion
            if (!animate || reducedMotion) {
                ShrinkingMoney(money, style, color, showPlus, fractionScale, minScale, figureModifier)
            } else {
                AnimatedContent(
                    targetState = money,
                    transitionSpec = {
                        val rising = targetState.currencyCode == initialState.currencyCode &&
                            targetState.amountMinor > initialState.amountMinor
                        val enter = slideInVertically(tween(Motion.slow)) { h -> if (rising) h / 3 else -h / 3 } + fadeIn(tween(Motion.slow))
                        val exit = slideOutVertically(tween(Motion.standard)) { h -> if (rising) -h / 3 else h / 3 } + fadeOut(tween(Motion.fast))
                        (enter togetherWith exit).using(SizeTransform(clip = false))
                    },
                    contentAlignment = Alignment.CenterStart,
                    modifier = figureModifier,
                    label = "money",
                ) { value ->
                    ShrinkingMoney(value, style, color, showPlus, fractionScale, minScale, Modifier)
                }
            }
        }
        if (style.fontSize == AmountStyles.hero.fontSize || style.fontSize == AmountStyles.display.fontSize) {
            WithFontScaleCap(FontScaleCaps.Hero, body)
        } else {
            body()
        }
    }
    if (!goldRule) {
        figure(modifier)
        return
    }
    Column(modifier) {
        figure(Modifier)
        Spacer(Modifier.height(Spacing.xs))
        GoldRule()
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
    var scale by remember(formatted) { mutableFloatStateOf(1f) }
    var ready by remember(formatted) { mutableStateOf(false) }
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
        modifier = modifier.drawWithContent { if (ready) drawContent() },
        onTextLayout = { result ->
            if (result.hasVisualOverflow && scale > minScale) {
                scale = (scale - 0.06f).coerceAtLeast(minScale)
            } else {
                ready = true
            }
        },
    )
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
