package com.lucento.app.feature.wallets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.core.designsystem.component.EmptyState
import com.lucento.app.core.designsystem.component.LucentoMark
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.SectionHeader
import com.lucento.app.core.designsystem.component.label
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.domain.WalletType

@Composable
fun WalletsScreen(viewModel: WalletsViewModel = viewModel(factory = WalletsViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<WalletDraft?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = 96.dp),
        ) {
            item {
                Text(
                    "Wallets",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.md),
                )
            }
            if (!state.loading && state.wallets.isEmpty()) {
                item {
                    EmptyState(
                        title = "No wallets yet",
                        body = "Add the cash, bank accounts, e-wallets, savings and cards you use. Balances stay on this device.",
                        illustration = { LucentoMark(size = 72.dp, animate = false) },
                        action = {
                            LucentoPrimaryButton(text = "Add wallet", onClick = {
                                editing = WalletDraft(currencyCode = state.primaryCurrency)
                            })
                        },
                    )
                }
            }
            WalletType.entries.forEach { type ->
                val group = state.wallets.filter { it.type == type }
                if (group.isNotEmpty()) {
                    item(key = "header-$type") { SectionHeader(type.label, Modifier.padding(top = Spacing.md)) }
                    items(group, key = { it.id }) { wallet ->
                        WalletRow(wallet = wallet, onClick = { editing = wallet.toDraft() })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }

        if (state.wallets.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = { editing = WalletDraft(currencyCode = state.primaryCurrency) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add wallet") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(Spacing.gutter),
            )
        }
    }

    editing?.let { draft ->
        WalletEditorSheet(
            initial = draft,
            onSave = viewModel::save,
            onDelete = viewModel::delete,
            onDismiss = { editing = null },
        )
    }
}
