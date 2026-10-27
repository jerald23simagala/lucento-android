package com.lucento.app.feature.wallets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.core.designsystem.component.EmptyState
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.HeroCard
import com.lucento.app.core.designsystem.component.HeroPill
import com.lucento.app.core.designsystem.component.HeroTones
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoCard
import com.lucento.app.core.designsystem.component.LucentoMascot
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.LucentoTopBar
import com.lucento.app.core.designsystem.component.MascotMood
import com.lucento.app.core.designsystem.component.MoneyText
import com.lucento.app.core.designsystem.component.ProfileButton
import com.lucento.app.core.designsystem.component.ToneChip
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Sizes
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.designsystem.theme.TonePair
import com.lucento.app.core.money.Money
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.domain.Insights
import com.lucento.app.domain.NetWorthCalculator
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.balanceFlag

@Composable
fun WalletsScreen(
    /** Null when Wallets is a primary destination in the navigation dock. */
    onBack: (() -> Unit)?,
    onOpenWallet: (Long) -> Unit,
    /** False when Multiple wallets is off and one account already exists. */
    allowAdding: Boolean,
    /** Opens Debts & loans; null when that module is switched off. */
    onOpenDebts: (() -> Unit)? = null,
    /** Opens the composer on the receipt scanner; null when scanning is switched off. */
    onScanReceipt: (() -> Unit)? = null,
    /** Opens a prefilled recurring payment for a newly saved card or loan; null when recurring payments are off. */
    onPlanDebtPayment: ((Long, java.time.LocalDate) -> Unit)? = null,
    viewModel: WalletsViewModel = viewModel(factory = WalletsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<WalletDraft?>(null) }
    val newDraft = { WalletDraft(currencyCode = state.primaryCurrency) }
    val canAdd = allowAdding || state.wallets.isEmpty()

    // Assets by type order, then liabilities: the two halves of net worth.
    val assets = state.wallets.filterNot { it.type.isLiability }.sortedBy { it.type.ordinal }
    val liabilities = state.wallets.filter { it.type.isLiability }
    val flagged = state.wallets.count { it.balanceFlag != null }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.fabClearance),
    ) {
        item(key = "title") {
            LucentoTopBar(
                title = "Wallets",
                onBack = onBack,
                actions = {
                    if (state.wallets.isNotEmpty() && canAdd) {
                        com.lucento.app.core.designsystem.component.AddPillButton("Add", onClick = { editing = newDraft() }, contentDescription = "Add wallet")
                    }
                    if (onBack == null) ProfileButton()
                },
            )
        }

        if (!state.loading && state.wallets.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    title = "No wallets yet",
                    body = "Add the cash, bank accounts, e-wallets, savings and cards you use. " +
                        "Balances are entered by you and stay on this device — no bank connection needed.",
                    illustration = { LucentoMascot(size = 96.dp, mood = MascotMood.Curious) },
                    action = { LucentoPrimaryButton(text = "Add wallet", onClick = { editing = newDraft() }) },
                )
            }
        }

        state.summary?.let { summary ->
            item(key = "summary") {
                NetWorthCard(summary.totalFor(state.primaryCurrency) ?: summary.totals.first(), summary)
            }
        }

        walletSection(
            key = "assets",
            title = "Assets",
            caption = "What you have",
            tone = { positiveTone() },
            wallets = assets,
            primaryCurrency = state.primaryCurrency,
            onOpen = { onOpenWallet(it.id) },
        )
        walletSection(
            key = "liabilities",
            title = "Liabilities",
            caption = "What you owe",
            tone = { negativeTone() },
            wallets = liabilities,
            primaryCurrency = state.primaryCurrency,
            onOpen = { onOpenWallet(it.id) },
            // Payoff progress, payments and interest live in Debts & loans.
            link = onOpenDebts?.let { "Debts & loans" to it },
            footnote = "Cards and loans reduce your net worth; they’re never counted as money you have.",
        )

        if (!canAdd) {
            item(key = "single") {
                InfoNote(
                    "You’re tracking a single account. Turn on Multiple wallets in Settings › Enabled features to add more.",
                    modifier = Modifier.padding(top = Spacing.lg),
                )
            }
        }

        if (state.wallets.isNotEmpty()) {
            item(key = "reconcile") { ReconcileNote(flagged = flagged, onScanReceipt = onScanReceipt) }
        }
    }

    editing?.let { draft ->
        WalletEditorSheet(
            initial = draft,
            onSave = viewModel::save,
            onDelete = viewModel::delete,
            onDismiss = { editing = null },
            onPlanPayment = onPlanDebtPayment?.let { plan ->
                { id, date ->
                    editing = null
                    plan(id, date)
                }
            },
        )
    }
}

@Composable
private fun positiveTone(): TonePair = LucentoTheme.extendedColors.let { TonePair(it.positiveContainer, it.positive) }

@Composable
private fun negativeTone(): TonePair = LucentoTheme.extendedColors.let { TonePair(it.negativeContainer, it.negative) }

/**
 * The page's statement: a solid brand-blue card (navy at night) with net worth on the left and its two halves stacked
 * on the right — Assets on a sunlight pill above, Liabilities on a tangerine pill below. Net worth's own pill turns
 * tangerine when it is negative. Every figure is signed, so the colours only reinforce the meaning.
 */
@Composable
private fun NetWorthCard(net: Money, summary: NetWorthCalculator.Summary) {
    val ext = LucentoTheme.extendedColors
    val code = net.currencyCode
    val assets = summary.assets[code] ?: Money.zero(code)
    val owed = summary.liabilities[code] ?: Money.zero(code)
    HeroCard(Modifier.padding(top = Spacing.xs)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1.15f)) {
                HeroPill(
                    "Net worth · $code",
                    if (net.isNegative) HeroTones.tangerine else HeroTones.main,
                    Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(Spacing.xs))
                MoneyText(money = net, style = AmountStyles.hero, color = ext.onHero)
            }
            VerticalDivider(color = ext.onHeroMuted.copy(alpha = 0.35f), modifier = Modifier.padding(horizontal = Spacing.md))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                HalfLine("Assets", HeroTones.sunlight, assets)
                HalfLine("Liabilities", HeroTones.tangerine, -owed)
            }
        }
        val percent = Insights.percentOf(owed, assets)
        val notes = listOfNotNull(
            if (!owed.isZero && percent != null) "You owe $percent% of what you have." else null,
            if (summary.totals.size > 1) {
                "Also: " + summary.totals.filter { it.currencyCode != code }
                    .joinToString(" · ") { MoneyFormatter.formatSigned(it, showPlus = false) }
            } else {
                null
            },
        )
        if (notes.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.sm))
            Text(notes.joinToString(" "), style = MaterialTheme.typography.bodySmall, color = ext.onHeroMuted)
        }
    }
}

@Composable
private fun HalfLine(label: String, tone: TonePair, money: Money) {
    Column(Modifier.fillMaxWidth()) {
        HeroPill(label, tone)
        Spacer(Modifier.height(Spacing.xxs))
        MoneyText(money = money, style = AmountStyles.row, color = LucentoTheme.extendedColors.onHero, fractionScale = 1f)
    }
}

/**
 * Assets or liabilities: a tinted header (title, what it means, how many, per-currency subtotal) above one card that
 * holds the rows, split by hairlines — the same card treatment as a day in Transactions.
 */
private fun LazyListScope.walletSection(
    key: String,
    title: String,
    caption: String,
    tone: @Composable () -> TonePair,
    wallets: List<Wallet>,
    primaryCurrency: String,
    onOpen: (Wallet) -> Unit,
    /** A destination shown beside the title in place of the caption, e.g. Debts & loans. */
    link: Pair<String, () -> Unit>? = null,
    footnote: String? = null,
) {
    if (wallets.isEmpty()) return
    item(key = "header-$key") {
        // Per-currency subtotal; currencies are never added together.
        val subtotals = wallets.groupBy { it.currencyCode }
            .map { (code, list) -> Money.sum(code, list.map { it.netWorthContribution }) }
            .sortedWith(compareBy<Money> { it.currencyCode != primaryCurrency }.thenBy { it.currencyCode })
        Row(
            Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(top = Spacing.md, bottom = Spacing.xs, start = Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToneChip(title, tone(), modifier = Modifier.semantics { heading() })
            Spacer(Modifier.width(Spacing.sm))
            if (link != null) {
                Box(Modifier.weight(1f)) {
                    Row(
                        Modifier
                            .heightIn(min = Sizes.minTouch)
                            .clip(MaterialTheme.shapes.small)
                            .clickable(role = Role.Button, onClick = link.second)
                            .padding(horizontal = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(link.first, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            } else {
                Text(
                    "$caption · ${wallets.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
            }
            Text(
                subtotals.joinToString(" · ") { MoneyFormatter.formatSigned(it, showPlus = false) },
                style = AmountStyles.small,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
    item(key = "card-$key") {
        LucentoCard(modifier = Modifier.fillMaxWidth().animateItem(), contentPadding = 0.dp) {
            wallets.forEachIndexed { index, wallet ->
                WalletRow(wallet = wallet, onClick = { onOpen(wallet) }, dense = true)
                if (index < wallets.lastIndex) Hairline(strong = true)
            }
        }
    }
    if (footnote != null) {
        item(key = "footnote-$key") {
            Text(
                footnote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs, start = Spacing.xxs),
            )
        }
    }
}

/**
 * Lucento never connects to a bank, so balances only change when something is recorded. A standing reminder to
 * reconcile, warning-toned (with the count) when a balance looks wrong, and a shortcut to the receipt scanner.
 */
@Composable
private fun ReconcileNote(flagged: Int, onScanReceipt: (() -> Unit)?) {
    val ext = LucentoTheme.extendedColors
    val warn = flagged > 0
    InfoNote(
        buildString {
            if (warn) append("${if (flagged == 1) "1 balance looks" else "$flagged balances look"} off. ")
            append("Lucento doesn’t sync with your bank: compare balances with your real accounts and add what’s missing. ")
            append("Can’t reconcile? Open the wallet › Edit › Adjust balance.")
        },
        modifier = Modifier.padding(top = Spacing.lg),
        icon = if (warn) Icons.Outlined.WarningAmber else Icons.Outlined.Info,
        container = if (warn) ext.warningContainer else MaterialTheme.colorScheme.surfaceContainer,
        content = if (warn) ext.warning else MaterialTheme.colorScheme.onSurfaceVariant,
        actionLabel = onScanReceipt?.let { "Scan a receipt" },
        onAction = onScanReceipt,
    )
}
