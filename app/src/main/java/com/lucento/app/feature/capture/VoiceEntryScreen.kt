package com.lucento.app.feature.capture

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardVoice
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.component.Figure
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoCard
import com.lucento.app.core.designsystem.component.LucentoMascot
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.component.LucentoTopBar
import com.lucento.app.core.designsystem.component.MascotMood
import com.lucento.app.core.designsystem.component.SectionHeader
import com.lucento.app.core.designsystem.component.rememberHaptics
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.domain.Category
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.capture.QuickEntryParser
import com.lucento.app.domain.capture.QuickEntryProposal
import com.lucento.app.feature.plan.formatDate
import java.time.LocalDate

/**
 * "Speak or type it": the system speech recogniser (when the device has one) or the keyboard turns a
 * sentence into text; Lucento's offline parser turns the text into a proposal; the proposal opens in
 * the normal editor for review. Nothing is saved from this screen and nothing is sent to an AI service.
 */
@Composable
fun VoiceEntryScreen(
    wallets: List<Wallet>,
    categories: List<Category>,
    onBack: () -> Unit,
    onReview: (QuickEntryProposal) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var proposal by remember { mutableStateOf<QuickEntryProposal?>(null) }
    var speechMessage by remember { mutableStateOf<String?>(null) }
    val haptics = rememberHaptics()

    fun interpret(input: String) {
        proposal = if (input.isBlank()) null else QuickEntryParser.parse(input, wallets, categories, LocalDate.now())
    }

    val recognizer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (heard.isNullOrBlank()) {
                speechMessage = "Nothing was recognised. Try again, or type it below."
            } else {
                text = heard
                speechMessage = null
                interpret(heard)
            }
        } else {
            speechMessage = "Speech was cancelled. You can type it instead."
        }
    }

    fun listen() {
        haptics.tick()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // The interpreter understands English; ask the recogniser for English and to stay on the device.
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say something like “Spent 250 on lunch from cash”")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            recognizer.launch(intent)
        } catch (e: ActivityNotFoundException) {
            speechMessage = "This device has no speech recogniser available. Type the sentence instead — it works the same way."
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter)
            .imePadding(),
    ) {
        LucentoTopBar(title = "Speak or type", onBack = onBack)

        Column(Modifier.fillMaxWidth().padding(vertical = Spacing.lg), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(onClick = ::listen, modifier = Modifier.size(88.dp)) {
                    Icon(
                        Icons.Outlined.KeyboardVoice,
                        contentDescription = "Speak a transaction",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            Text("Tap to speak", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        speechMessage?.let { InfoNote(it, modifier = Modifier.padding(bottom = Spacing.md)) }

        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it.take(200)
                proposal = null
            },
            label = { Text("Or type it") },
            placeholder = { Text("e.g. Grab 180 yesterday from GCash") },
            minLines = 2,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.md))
        LucentoPrimaryButton(text = "Understand it", onClick = { interpret(text) }, enabled = text.isNotBlank())

        val p = proposal
        if (p == null) {
            Spacer(Modifier.height(Spacing.lg))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LucentoMascot(size = 48.dp, mood = MascotMood.Curious)
                Text(
                    "Try: “Received 52,000 salary into Main Account”, “Transfer 5000 from bank to savings”, or “Coffee 4.50 yesterday”.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.md),
                )
            }
        } else {
            Spacer(Modifier.height(Spacing.lg))
            ProposalCard(p, wallets, categories)
            Spacer(Modifier.height(Spacing.md))
            LucentoPrimaryButton(text = "Review and save", onClick = { onReview(p) })
            LucentoTextButton(text = "Start over", onClick = {
                text = ""
                proposal = null
            })
        }

        Spacer(Modifier.height(Spacing.lg))
        InfoNote(
            "Speech is turned into text by your device’s speech service (Lucento asks it to work offline when it can). " +
                "Understanding the sentence happens entirely in Lucento on this phone — nothing is sent to an AI service.",
        )
        Spacer(Modifier.height(Spacing.xxl))
    }
}

@Composable
private fun ProposalCard(p: QuickEntryProposal, wallets: List<Wallet>, categories: List<Category>) {
    val walletName = { id: Long? -> wallets.firstOrNull { it.id == id }?.name ?: "Not chosen" }
    LucentoCard(Modifier.fillMaxWidth()) {
        SectionHeader("Lucento understood")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Figure("Type", Modifier.weight(1f)) {
                Text(
                    when (p.type) {
                        TransactionType.INCOME -> "Income"
                        TransactionType.EXPENSE -> "Expense"
                        else -> "Transfer"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Figure("Amount", Modifier.weight(1f)) {
                Text(
                    p.amount?.toPlainString()?.let { amount -> (p.mentionedCurrency?.let { "$it " } ?: "") + amount } ?: "Not found",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Figure(if (p.type.isTransfer) "From" else "Account", Modifier.weight(1f)) {
                Text(walletName(p.walletId), style = MaterialTheme.typography.bodyLarge)
            }
            if (p.type.isTransfer) {
                Figure("To", Modifier.weight(1f)) { Text(walletName(p.toWalletId), style = MaterialTheme.typography.bodyLarge) }
            } else {
                Figure("Category", Modifier.weight(1f)) {
                    Text(categories.firstOrNull { it.id == p.categoryId }?.name ?: "Not chosen", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Figure("Date", Modifier.weight(1f)) { Text(formatDate(p.date), style = MaterialTheme.typography.bodyLarge) }
            Figure("Note", Modifier.weight(1f)) { Text(p.note.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge) }
        }
        p.warnings.forEach { warning ->
            Spacer(Modifier.height(Spacing.sm))
            Text(warning, style = MaterialTheme.typography.bodySmall, color = LucentoTheme.extendedColors.accentText)
        }
    }
}
