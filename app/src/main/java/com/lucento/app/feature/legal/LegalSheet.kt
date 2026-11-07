package com.lucento.app.feature.legal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.lucento.app.core.designsystem.component.LucentoBottomSheet
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.SheetHeader
import com.lucento.app.core.designsystem.theme.Spacing

/** Which of the two documents a [LegalSheet] shows. */
enum class LegalDoc { TERMS, PRIVACY }

/** Reads a legal document on the phone (works offline). Back, a drag, or Done closes it. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LegalSheet(doc: LegalDoc, onDismiss: () -> Unit) {
    val document = when (doc) {
        LegalDoc.TERMS -> LegalDocuments.terms
        LegalDoc.PRIVACY -> LegalDocuments.privacy
    }
    LucentoBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.gutter)
                .navigationBarsPadding(),
        ) {
            SheetHeader(
                title = document.title,
                subtitle = "Updated ${LegalDocuments.UPDATED}",
                icon = if (doc == LegalDoc.TERMS) Icons.AutoMirrored.Outlined.Article else Icons.Outlined.PrivacyTip,
            )
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                Text(document.intro, style = MaterialTheme.typography.bodyMedium)
                document.sections.forEach { section ->
                    Spacer(Modifier.height(Spacing.lg))
                    Text(section.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    section.items.forEach { item ->
                        Spacer(Modifier.height(Spacing.sm))
                        Row {
                            Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(Spacing.sm))
                            Text(item, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.lg))
            }
            Spacer(Modifier.height(Spacing.sm))
            LucentoPrimaryButton(text = "Done", onClick = onDismiss)
            Spacer(Modifier.height(Spacing.md))
        }
    }
}
