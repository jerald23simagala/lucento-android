package com.lucento.app.core.designsystem.component

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.money.Money
import com.lucento.app.core.money.MoneyFormatter

/**
 * Renders a [Money] value with tabular figures. Colour is neutral by default: Lucento only tints
 * amounts when the sign carries meaning (income vs expense), never decoratively.
 */
@Composable
fun AmountText(
    money: Money,
    modifier: Modifier = Modifier,
    style: TextStyle = AmountStyles.row,
    color: Color = LocalContentColor.current,
) {
    Text(
        text = MoneyFormatter.format(money),
        modifier = modifier,
        style = style,
        color = color,
        maxLines = 1,
    )
}
