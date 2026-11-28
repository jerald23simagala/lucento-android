package com.lucento.app.core.designsystem.component

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.Money

/**
 * One line of a small ledger, as in the budget card's parts. What it is sits on the left and its amount is aligned on
 * the right. A deduction shows in red, always with its minus sign, so colour is never the only cue. When the two can't
 * share a line, the description sits over the amount, which stays on the right, so a word is never broken to make room;
 * an amount wider than the whole row (very large text, a currency with many digits) shrinks to fit instead of being cut.
 * [emphasized] marks the line the others add up to. TalkBack hears each line as one: "[label], [amount]".
 */
@Composable
fun LedgerRow(label: String, amount: Money, modifier: Modifier = Modifier, emphasized: Boolean = false) {
    val fmt = amountFormat()
    val colors = MaterialTheme.colorScheme
    val ink = if (amount.isNegative) LucentoTheme.extendedColors.negative else colors.onSurface
    val labelStyle = MaterialTheme.typography.bodyMedium.let { if (emphasized) it.copy(fontWeight = FontWeight.SemiBold) else it }
    val baseAmountStyle = AmountStyles.small.copy(fontWeight = if (emphasized) FontWeight.Bold else FontWeight.SemiBold)
    val shown = fmt.signed(amount, showPlus = false)
    val measurer = rememberTextMeasurer()
    val gap = Spacing.md
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = "$label, ${fmt.spokenSigned(amount, showPlus = false)}" },
    ) {
        val room = constraints.maxWidth
        val amountStyle = remember(shown, baseAmountStyle, room) {
            val natural = measurer.measure(shown, baseAmountStyle, softWrap = false, maxLines = 1).size.width
            if (room in 1 until natural) baseAmountStyle.copy(fontSize = baseAmountStyle.fontSize * (room.toFloat() / natural)) else baseAmountStyle
        }
        Layout(
            content = {
                Text(label, style = labelStyle, color = if (emphasized) colors.onSurface else colors.onSurfaceVariant)
                Text(shown, style = amountStyle, color = ink, maxLines = 1, softWrap = false)
            },
            modifier = Modifier.fillMaxWidth(),
        ) { measurables, constraints ->
            val (labelMeasurable, amountMeasurable) = measurables
            val gapPx = gap.roundToPx()
            val amountPlaceable = amountMeasurable.measure(Constraints())
            val labelWidth = labelMeasurable.maxIntrinsicWidth(Constraints.Infinity)
            val width = if (constraints.hasBoundedWidth) constraints.maxWidth else labelWidth + gapPx + amountPlaceable.width
            if (labelWidth + gapPx + amountPlaceable.width <= width) {
                val labelPlaceable = labelMeasurable.measure(Constraints(maxWidth = width - gapPx - amountPlaceable.width))
                val height = maxOf(labelPlaceable.height, amountPlaceable.height)
                layout(width, height) {
                    labelPlaceable.placeRelative(0, (height - labelPlaceable.height) / 2)
                    amountPlaceable.placeRelative(width - amountPlaceable.width, (height - amountPlaceable.height) / 2)
                }
            } else {
                val labelPlaceable = labelMeasurable.measure(Constraints(maxWidth = width))
                layout(width, labelPlaceable.height + amountPlaceable.height) {
                    labelPlaceable.placeRelative(0, 0)
                    amountPlaceable.placeRelative(maxOf(0, width - amountPlaceable.width), labelPlaceable.height)
                }
            }
        }
    }
}
