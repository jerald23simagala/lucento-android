package com.lucento.app.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.domain.CardDue
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A card's payment reminder, opened from the bell, Home's Upcoming card or Scheduled (tester round 2026-11-24: card
 * payments were the one scheduled item with no Skip). "Pay" opens the payment transfer as the reminder did before;
 * "Skip this statement" stops reminding it and stops setting it aside from Safe to spend until the card's next statement
 * ([com.lucento.app.domain.SkippedStatement]); the caller offers Undo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardPaymentSheet(
    due: CardDue,
    today: LocalDate,
    onPay: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    val fmt = amountFormat()
    val ext = LucentoTheme.extendedColors
    val day = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    val cycle = due.cycle
    val dueWords = when {
        due.overdue -> "Was due ${cycle.dueDate.format(day)}"
        cycle.dueDate == today -> "Due today"
        else -> "Due ${cycle.dueDate.format(day)}"
    }
    LucentoBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SheetHeader(
                title = "${due.wallet.name} payment",
                subtitle = "Statement of ${cycle.statementDate.format(day)} · $dueWords",
                leading = { WalletIdentityTile(due.wallet, size = 40.dp) },
            )
            Text("Amount due", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MoneyText(money = cycle.amountDue, style = AmountStyles.headline, color = if (due.overdue) ext.negative else MaterialTheme.colorScheme.onSurface)
            Text(
                "Paying the card moves money from your account to the card: a transfer, never spending, since the purchases " +
                    "were counted when you made them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xs))
            LucentoPrimaryButton(text = "Pay ${fmt.format(cycle.amountDue)}", onClick = onPay)
            LucentoSecondaryButton(text = "Skip this statement", onClick = onSkip)
            Text(
                "Skipping stops this reminder and stops setting the amount aside in Safe to spend. The card's next statement " +
                    "reminds you again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.sm))
        }
    }
}
