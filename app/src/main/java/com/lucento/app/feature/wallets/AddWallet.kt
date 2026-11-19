package com.lucento.app.feature.wallets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletType
import com.lucento.app.domain.entitlement.Access
import com.lucento.app.domain.entitlement.ProGates
import com.lucento.app.feature.pro.LocalEntitlements
import com.lucento.app.feature.pro.LocalOpenPro
import com.lucento.app.feature.pro.ProWall
import com.lucento.app.feature.pro.ProWallDialog

/**
 * Add wallet, wherever it is offered (Wallets, Home): the editor on Cash while money wallets fit; when they are full but a
 * card or loan still fits, the type sheet first, so the person picks the kind (money types marked Pro, picking one opens
 * the dialog) instead of a form that silently starts as a card; the Pro dialog when nothing fits (tester round
 * 2026-11-18, [ProGates.addWalletStart]). [wallets] are the active wallets, null while still loading (the editor then
 * opens as before and the save decides); [mainCurrency] is the ledger's. Returns what the Add button calls; the sheet and
 * the dialog live here.
 */
@Composable
fun rememberAddWallet(
    wallets: List<Wallet>?,
    mainCurrency: String,
    onOpenEditor: (WalletDraft) -> Unit,
): () -> Unit {
    val plan = LocalEntitlements.current
    val openPro = LocalOpenPro.current
    var choosing by remember { mutableStateOf(false) }
    var wall by remember { mutableStateOf<ProWall?>(null) }

    fun access(type: WalletType): Access =
        ProGates.walletAccess(type, mainCurrency, null, mainCurrency, wallets.orEmpty(), plan.capabilities(System.currentTimeMillis()))

    if (choosing) {
        val locked = WalletType.entries.filter { access(it) != Access.Allowed }.toSet()
        WalletTypePickerSheet(
            selected = null,
            currencyCode = mainCurrency,
            proTypes = locked,
            proLabel = { type -> ProWall.of(access(type))?.let { com.lucento.app.feature.pro.ProCopy.wallTitle(it) } ?: "Lucento Pro feature" },
            onSelect = { type ->
                val met = ProWall.of(access(type))
                if (met != null) {
                    wall = met
                } else {
                    choosing = false
                    onOpenEditor(WalletDraft(type = type, currencyCode = mainCurrency))
                }
            },
            onDismiss = { choosing = false },
        )
    }
    wall?.let { met ->
        ProWallDialog(
            met,
            onDismiss = { wall = null },
            open = { feature ->
                choosing = false
                openPro(feature)
            },
        )
    }
    return {
        if (wallets == null) {
            onOpenEditor(WalletDraft(currencyCode = mainCurrency))
        } else {
            when (val start = ProGates.addWalletStart(mainCurrency, wallets, plan.capabilities(System.currentTimeMillis()))) {
                is ProGates.AddWalletStart.Editor -> onOpenEditor(WalletDraft(type = start.type, currencyCode = mainCurrency))
                ProGates.AddWalletStart.ChooseType -> choosing = true
                is ProGates.AddWalletStart.Wall ->
                    wall = ProWall.of(start.access)?.copy(alsoFull = start.alsoFull?.let { ProWall.of(it) })
            }
        }
    }
}
