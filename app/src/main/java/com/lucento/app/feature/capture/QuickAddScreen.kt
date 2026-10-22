package com.lucento.app.feature.capture

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.KeyboardVoice
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.core.containerFactory
import com.lucento.app.core.designsystem.component.ChoicePill
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoCard
import com.lucento.app.core.designsystem.component.LucentoTopBar
import com.lucento.app.core.designsystem.component.NavRow
import com.lucento.app.core.designsystem.component.SectionHeader
import com.lucento.app.core.designsystem.component.SegmentedToggle
import com.lucento.app.core.designsystem.component.icon
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.data.preferences.UserPreferencesRepository
import com.lucento.app.data.repository.CategoryRepository
import com.lucento.app.data.repository.TransactionRepository
import com.lucento.app.data.repository.WalletRepository
import com.lucento.app.domain.Category
import com.lucento.app.domain.Feature
import com.lucento.app.domain.FeatureSet
import com.lucento.app.domain.TransactionType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class QuickAddState(
    val features: FeatureSet = FeatureSet.ALL,
    val walletCount: Int = 0,
    /** Up to six categories used most recently, newest first — one tap to a pre-filled entry. */
    val recentCategories: List<Category> = emptyList(),
)

class QuickAddViewModel(
    transactions: TransactionRepository,
    wallets: WalletRepository,
    categories: CategoryRepository,
    preferences: UserPreferencesRepository,
) : ViewModel() {
    val state: StateFlow<QuickAddState> = combine(
        transactions.observeTransactions(),
        wallets.observeWallets(),
        categories.observeCategories(),
        preferences.preferences,
    ) { all, walletList, categoryList, prefs ->
        val byId = categoryList.associateBy { it.id }
        QuickAddState(
            features = prefs.features,
            walletCount = walletList.size,
            recentCategories = all.asSequence()
                .mapNotNull { t -> t.categoryId?.let { byId[it] } }
                .filter { !it.archived }
                .distinctBy { it.id }
                .take(6)
                .toList(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuickAddState())

    companion object {
        val Factory = containerFactory { QuickAddViewModel(it.transactions, it.wallets, it.categories, it.preferences) }
    }
}

/**
 * The Add tab: three ways to capture a transaction. Scanning and speaking only ever produce a
 * suggestion that opens in the normal editor for review — nothing is saved without confirmation.
 */
@Composable
fun QuickAddScreen(
    onManual: (TransactionType, Long?) -> Unit,
    onTransfer: () -> Unit,
    onScan: () -> Unit,
    onSpeak: () -> Unit,
    viewModel: QuickAddViewModel = viewModel(factory = QuickAddViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var type by rememberSaveable { mutableStateOf(TransactionType.EXPENSE) }
    val f = state.features
    val transfers = f.isEnabled(Feature.MULTIPLE_WALLETS)
    val ext = LucentoTheme.extendedColors

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter),
    ) {
        LucentoTopBar(title = "Add transaction")
        SegmentedToggle(
            options = listOf(TransactionType.EXPENSE to "Expense", TransactionType.INCOME to "Income") +
                (if (transfers) listOf(TransactionType.TRANSFER_OUT to "Transfer") else emptyList()),
            selected = type,
            onSelect = { type = it },
        )
        Spacer(Modifier.height(Spacing.lg))

        LucentoCard(Modifier.fillMaxWidth(), contentPadding = Spacing.sm) {
            if (f.isEnabled(Feature.RECEIPT_SCANNING) && type != TransactionType.TRANSFER_OUT) {
                NavRow(
                    icon = Icons.Outlined.DocumentScanner,
                    title = "Scan receipt",
                    summary = "Read the total, date and shop from a photo — on this device",
                    tone = ext.bank,
                    onClick = onScan,
                )
            }
            if (f.isEnabled(Feature.VOICE_ENTRY)) {
                NavRow(
                    icon = Icons.Outlined.KeyboardVoice,
                    title = "Speak or type it",
                    summary = "“Spent 250 on lunch from cash”",
                    tone = ext.savings,
                    onClick = onSpeak,
                )
            }
            NavRow(
                icon = Icons.Outlined.EditNote,
                title = "Manual entry",
                summary = "Enter the details yourself",
                tone = ext.cash,
                onClick = { if (type == TransactionType.TRANSFER_OUT) onTransfer() else onManual(type, null) },
            )
        }

        if (state.walletCount == 0) {
            Spacer(Modifier.height(Spacing.md))
            InfoNote("Add a wallet first — every transaction belongs to an account. Manual entry will take you there.")
        }

        if (state.recentCategories.isNotEmpty() && type != TransactionType.TRANSFER_OUT) {
            val shown = state.recentCategories.filter { it.kind.name == type.name }
            if (shown.isNotEmpty()) {
                SectionHeader("Recent categories", Modifier.padding(top = Spacing.lg))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    shown.forEach { c ->
                        ChoicePill(
                            label = c.name,
                            selected = false,
                            leadingIcon = c.icon,
                            onClick = { onManual(type, c.id) },
                        )
                    }
                }
            }
        }

        if (!f.isEnabled(Feature.RECEIPT_SCANNING) || !f.isEnabled(Feature.VOICE_ENTRY)) {
            Spacer(Modifier.height(Spacing.lg))
            Text(
                "Receipt scanning or voice entry is turned off. You can turn them on in Settings › Enabled features.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Spacing.xxl))
    }
}
