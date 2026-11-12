package com.lucento.app.feature.wallets

import com.lucento.app.core.designsystem.component.AmountFormat
import com.lucento.app.core.designsystem.component.amountFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.core.designsystem.component.CardOverline
import com.lucento.app.core.designsystem.component.ChoicePill
import com.lucento.app.core.designsystem.component.CompositionBar
import com.lucento.app.core.designsystem.component.EmptyState
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.HeroCard
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LegendKey
import com.lucento.app.core.designsystem.component.ListSectionHeader
import com.lucento.app.core.designsystem.component.LucentoCard
import com.lucento.app.core.designsystem.component.MascotScene
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.component.LucentoTopBar
import com.lucento.app.core.designsystem.component.MascotMood
import com.lucento.app.core.designsystem.component.MoneyText
import com.lucento.app.core.designsystem.component.ProfileButton
import com.lucento.app.core.designsystem.component.SpotlightKeys
import com.lucento.app.core.designsystem.component.spotlightTarget
import com.lucento.app.core.designsystem.component.SegmentedToggle
import com.lucento.app.core.designsystem.component.StatTile
import com.lucento.app.core.designsystem.component.StatTileLayout
import com.lucento.app.core.designsystem.component.TrendLine
import com.lucento.app.core.designsystem.component.ViewOption
import com.lucento.app.core.designsystem.component.ViewToggle
import com.lucento.app.core.designsystem.component.screenGutter
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.Hierarchy
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.OverlineStyle
import com.lucento.app.core.designsystem.theme.Sizes
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.designsystem.theme.TonePair
import com.lucento.app.core.money.Money
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.data.preferences.WalletsViewMode
import com.lucento.app.domain.Insights
import com.lucento.app.domain.NetWorthCalculator
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.balanceFlag
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

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
    val fmt = amountFormat()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<WalletDraft?>(null) }
    val newDraft = { WalletDraft(currencyCode = state.primaryCurrency) }
    val access = com.lucento.app.feature.sharedspace.LocalSharedAccess.current
    // A Partner sees every wallet but doesn't add them (the Shared Space's Owner does).
    val canAdd = (allowAdding || state.wallets.isEmpty()) && access.canManageStructure

    // Assets by type order, then liabilities: the two halves of net worth, after the side filter.
    val shown = state.shown
    val assets = shown.filterNot { it.type.isLiability }.sortedBy { it.type.ordinal }
    val liabilities = shown.filter { it.type.isLiability }
    val flagged = state.wallets.count { it.balanceFlag != null }
    val cards = state.viewMode == WalletsViewMode.CARDS
    val gutter = screenGutter()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = gutter, end = gutter, bottom = Spacing.fabClearance),
    ) {
        item(key = "title") {
            LucentoTopBar(
                title = "Wallets",
                onBack = onBack,
                actions = {
                    if (state.wallets.isNotEmpty() && canAdd) {
                        com.lucento.app.core.designsystem.component.AddPillButton(
                            "Add",
                            onClick = { editing = newDraft() },
                            contentDescription = "Add wallet",
                            modifier = Modifier.spotlightTarget(SpotlightKeys.WALLETS_ADD),
                        )
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
                    illustration = { MascotScene(mood = MascotMood.Curious, mascotSize = 96.dp) },
                    action = if (canAdd) ({
                        LucentoPrimaryButton(
                            text = "Add wallet",
                            onClick = { editing = newDraft() },
                            modifier = Modifier.spotlightTarget(SpotlightKeys.WALLETS_ADD),
                        )
                    }) else null,
                )
            }
        }

        val summary = state.summary
        val net = state.heroTotal
        if (summary != null && net != null) {
            item(key = "summary") { androidx.compose.foundation.layout.Box(Modifier.spotlightTarget(SpotlightKeys.NET_WORTH)) { NetWorthCard(net, summary, state.trend) } }
            item(key = "split") { AssetsLiabilitiesSplit(net.currencyCode, summary) }
        }

        if (state.wallets.size >= 2) {
            item(key = "filters") {
                WalletFilters(state, onSide = viewModel::setSide, onViewMode = viewModel::setViewMode)
            }
        }

        walletSection(
            key = "assets",
            title = "Assets",
            caption = "What you have",
            wallets = assets,
            primaryCurrency = state.primaryCurrency,
            cards = cards,
            onOpen = { onOpenWallet(it.id) },
            fmt = fmt,
        )
        walletSection(
            key = "liabilities",
            title = "Liabilities",
            caption = "What you owe",
            wallets = liabilities,
            primaryCurrency = state.primaryCurrency,
            cards = cards,
            onOpen = { onOpenWallet(it.id) },
            fmt = fmt,
            // Payoff progress, payments and interest live in Debts & loans.
            link = onOpenDebts?.let { "Debts & loans" to it },
            footnote = "Cards and loans reduce your net worth; they’re never counted as money you have.",
        )

        if (state.wallets.isNotEmpty() && shown.isEmpty()) {
            item(key = "no-match") {
                Column(Modifier.fillMaxWidth().padding(top = Spacing.lg), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No wallets match these filters.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LucentoTextButton(text = "Show all wallets", onClick = { viewModel.setSide(WalletSide.ALL) })
                }
            }
        }

        if (!access.canManageStructure) {
            item(key = "owner-manages") {
                com.lucento.app.feature.sharedspace.OwnerManagesNote("wallets", Modifier.padding(top = Spacing.lg))
            }
        } else if (!canAdd) {
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

/**
 * The page's statement on brand blue (navy at night): net worth with its change since last month and what is owed as a
 * share of what is held, and — once there are two months to compare — the last six months as a sunlight trend line.
 * The trend moves under the figure on narrow screens and at large text. Every figure is signed.
 */
@Composable
private fun NetWorthCard(net: Money, summary: NetWorthCalculator.Summary, trend: NetWorthTrend?) {
    val fmt = amountFormat()
    val ext = LucentoTheme.extendedColors
    val code = net.currencyCode
    val history = trend?.history.orEmpty()
    val fontScale = LocalDensity.current.fontScale
    HeroCard(Modifier.padding(top = Spacing.xs)) {
        Spacer(Modifier.height(Spacing.xs))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val showTrend = history.size >= 2
            if (showTrend && maxWidth >= 300.dp && fontScale < 1.3f) {
                Row(verticalAlignment = Alignment.Top) {
                    NetFigure(net, summary, trend, Modifier.weight(1f))
                    Spacer(Modifier.width(Spacing.md))
                    TrendBlock(history, fontScale)
                }
            } else {
                Column {
                    NetFigure(net, summary, trend, Modifier.fillMaxWidth())
                    if (showTrend) {
                        Spacer(Modifier.height(Spacing.md))
                        TrendBlock(history, fontScale, Modifier.align(Alignment.End))
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        val others = summary.totals.filter { it.currencyCode != code }
        if (others.isNotEmpty()) {
            Text(
                "Also: " + others.joinToString(" · ") { fmt.signed(it, showPlus = false) },
                style = MaterialTheme.typography.bodySmall,
                color = ext.onHeroMuted,
            )
            Spacer(Modifier.height(Spacing.xs))
        }
    }
}

@Composable
private fun NetFigure(net: Money, summary: NetWorthCalculator.Summary, trend: NetWorthTrend?, modifier: Modifier) {
    val ext = LucentoTheme.extendedColors
    val code = net.currencyCode
    val assets = summary.assets[code] ?: Money.zero(code)
    val owed = summary.liabilities[code] ?: Money.zero(code)
    Column(modifier) {
        CardOverline("Net worth · $code", color = ext.onHeroMuted)
        Spacer(Modifier.height(6.dp))
        MoneyText(money = net, style = AmountStyles.hero, color = ext.onHero, fractionScale = 1f, minScale = 0.5f)
        val change = trend?.monthOnMonth
        val previous = trend?.history?.let { if (it.size >= 2) it[it.size - 2].month else null }
        if (change != null && previous != null) {
            Spacer(Modifier.height(Spacing.sm))
            ChangePill(change, previous.month.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.getDefault()))
        }
        val percent = Insights.percentOf(owed, assets)
        if (!owed.isZero && percent != null) {
            Spacer(Modifier.height(10.dp))
            Text("You owe $percent% of what you have.", style = MaterialTheme.typography.bodySmall, color = ext.onHeroMuted)
        }
    }
}

/** "+3% vs Oct": the change in net worth since the end of last month, with an arrow that agrees with the sign. */
@Composable
private fun ChangePill(percent: Int, previousMonth: String) {
    val ext = LucentoTheme.extendedColors
    val sign = when {
        percent > 0 -> "+"
        percent < 0 -> "−"
        else -> ""
    }
    val icon = when {
        percent > 0 -> Icons.AutoMirrored.Filled.TrendingUp
        percent < 0 -> Icons.AutoMirrored.Filled.TrendingDown
        else -> Icons.AutoMirrored.Filled.TrendingFlat
    }
    val spoken = when {
        percent > 0 -> "Up $percent percent since the end of $previousMonth"
        percent < 0 -> "Down ${abs(percent)} percent since the end of $previousMonth"
        else -> "Unchanged since the end of $previousMonth"
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(ext.onHero.copy(alpha = 0.16f))
            .clearAndSetSemantics { contentDescription = spoken }
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = ext.onHero, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(Spacing.xs))
        Text("$sign${abs(percent)}% vs $previousMonth", style = AmountStyles.small.copy(fontWeight = FontWeight.SemiBold), color = ext.onHero, maxLines = 1)
    }
}

/** The last months of net worth: a caps caption, then a sunlight [TrendLine] with month names under it. */
@Composable
private fun TrendBlock(history: List<com.lucento.app.domain.MonthBalance>, fontScale: Float, modifier: Modifier = Modifier) {
    val fmt = amountFormat()
    val ext = LucentoTheme.extendedColors
    val locale = Locale.getDefault()
    // Three letters fit six months in 150dp; at large text only the initial does.
    val nameStyle = if (fontScale >= 1.3f) TextStyle.NARROW_STANDALONE else TextStyle.SHORT_STANDALONE
    val first = history.first()
    val last = history.last()
    val description = "Net worth over ${history.size} months, from ${fmt.spokenSigned(first.netWorth)} at the end of " +
        "${first.month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)} to ${fmt.spokenSigned(last.netWorth)} now"
    Column(modifier, horizontalAlignment = Alignment.End) {
        Text(
            "${history.size} MONTHS",
            style = OverlineStyle.copy(fontSize = 10.sp),
            color = ext.onHeroMuted,
            modifier = Modifier.clearAndSetSemantics { },
        )
        Spacer(Modifier.height(10.dp))
        TrendLine(
            points = history.map { it.netWorth.amountMinor },
            labels = history.map { it.month.month.getDisplayName(nameStyle, locale) },
            color = ext.accent,
            description = description,
            labelColor = ext.onHeroMuted,
            currentLabelColor = ext.onHero,
            // The ring around the latest point sits at the panel's trailing end (where Aurora's gradient ends).
            background = ext.heroEnd,
        )
    }
}

/**
 * Assets and liabilities as two stat cards (signed, coloured by side), then — when both sides hold something — how
 * everything tracked divides between them, with the formula that turns them into net worth.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssetsLiabilitiesSplit(code: String, summary: NetWorthCalculator.Summary) {
    val fmt = amountFormat()
    val ext = LucentoTheme.extendedColors
    val assets = summary.assets[code] ?: Money.zero(code)
    val owed = summary.liabilities[code] ?: Money.zero(code)
    val figureStyle = AmountStyles.medium.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold)
    val assetsTile: @Composable (Modifier) -> Unit = { m ->
        StatTile(
            "Assets",
            Icons.Outlined.AccountBalanceWallet,
            TonePair(ext.positiveContainer, ext.success),
            description = "Assets ${fmt.spokenSigned(assets, showPlus = false)}",
            layout = StatTileLayout.Inline,
            modifier = m,
        ) { MoneyText(money = assets, style = figureStyle, color = ext.positive, fractionScale = 1f) }
    }
    val liabilitiesTile: @Composable (Modifier) -> Unit = { m ->
        StatTile(
            "Liabilities",
            Icons.Outlined.CreditCard,
            TonePair(ext.negativeContainer, ext.negativeIndicator),
            description = "Liabilities ${fmt.spokenSigned(-owed)}",
            layout = StatTileLayout.Inline,
            modifier = m,
        ) { MoneyText(money = -owed, style = figureStyle, color = ext.negative, fractionScale = 1f) }
    }
    Column(Modifier.padding(top = Spacing.md)) {
        if (LocalDensity.current.fontScale >= 1.5f) {
            // Very large text: one card per row, so "Liabilities" never breaks mid-word.
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                assetsTile(Modifier.fillMaxWidth())
                liabilitiesTile(Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                assetsTile(Modifier.weight(1f).fillMaxHeight())
                liabilitiesTile(Modifier.weight(1f).fillMaxHeight())
            }
        }
        val share = WalletsOverview.assetShare(assets, owed)
        if (share != null) {
            Spacer(Modifier.height(Spacing.md))
            CompositionBar(
                weights = listOf(assets.amountMinor, owed.amountMinor),
                colors = listOf(ext.success, ext.negativeIndicator),
                scaleTo = Math.addExact(assets.amountMinor, owed.amountMinor),
                description = "Assets are $share percent and liabilities ${100 - share} percent of everything tracked",
                height = 10.dp,
                modifier = Modifier.padding(horizontal = Spacing.xxs),
            )
            Spacer(Modifier.height(Spacing.sm))
            if (LocalDensity.current.fontScale >= 1.5f) {
                // Very large text: one line would have to shrink below the user's own text size, so it may wrap here.
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.xxs),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    LegendKey("Assets $share%", ext.success)
                    LegendKey("Liabilities ${100 - share}%", ext.negativeIndicator)
                }
            } else {
                // Always one row (owner, 2026-11-03), shrinking to fit rather than wrapping. The "Net worth = assets −
                // liabilities" formula was dropped in owner round 10 to cut what there is to read.
                val muted = MaterialTheme.colorScheme.onSurfaceVariant
                val ink = MaterialTheme.colorScheme.onSurface
                val legend = remember(share, ext.success, ext.negativeIndicator, muted, ink) {
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = ext.success)) { append("●") }
                        withStyle(SpanStyle(color = ink)) { append(" Assets $share%") }
                        append("   ")
                        withStyle(SpanStyle(color = ext.negativeIndicator)) { append("●") }
                        withStyle(SpanStyle(color = ink)) { append(" Liabilities ${100 - share}%") }
                    }
                }
                com.lucento.app.core.designsystem.component.FittingText(
                    text = legend,
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                    minScale = 0.7f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.xxs)
                        .clearAndSetSemantics {
                            contentDescription = "Assets $share percent, liabilities ${100 - share} percent."
                        },
                )
            }
        }
    }
}

/**
 * All / Assets / Liabilities (only when both sides exist) and the row / card view toggle. Filtering is display-only;
 * nothing is recalculated. (Per-type chips were removed in owner round 10.)
 */
@Composable
private fun WalletFilters(
    state: WalletsUiState,
    onSide: (WalletSide) -> Unit,
    onViewMode: (WalletsViewMode) -> Unit,
) {
    val hasBothSides = state.wallets.any { it.type.isLiability } && state.wallets.any { !it.type.isLiability }
    val fontScale = LocalDensity.current.fontScale
    val sides: @Composable (Modifier) -> Unit = { m ->
        if (fontScale >= 1.5f) {
            // Very large text: three equal segments can't hold "Liabilities"; pills size to their words and scroll.
            Row(m.horizontalScroll(rememberScrollState()).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                WalletSide.entries.forEach { side ->
                    ChoicePill(label = side.label, selected = side == state.side, onClick = { onSide(side) })
                }
            }
        } else {
            SegmentedToggle(
                options = WalletSide.entries.map { it to it.label },
                selected = state.side,
                onSelect = onSide,
                compact = true,
                modifier = m,
            )
        }
    }
    val view: @Composable () -> Unit = {
        ViewToggle(
            options = listOf(
                ViewOption(WalletsViewMode.ROWS, Icons.AutoMirrored.Filled.List, "Row view"),
                ViewOption(WalletsViewMode.CARDS, Icons.Outlined.GridView, "Card view"),
            ),
            selected = state.viewMode,
            onSelect = onViewMode,
        )
    }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = Spacing.md)) {
        // "Liabilities" needs about 80dp at normal text: the side switch shares the row with the view toggle only when
        // all three labels fit beside it; otherwise it takes its own row (narrow phones, large text).
        val inline = !hasBothSides || maxWidth >= 96.dp + 240.dp * fontScale
        Column {
            if (inline) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hasBothSides) sides(Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
                    Spacer(Modifier.width(Spacing.sm))
                    view()
                }
            } else {
                sides(Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { view() }
            }
        }
    }
}

/**
 * Assets or liabilities: the section title with what it holds (or a link) and its subtotal in the main currency, then the
 * wallets as rows in one card or, in card view, as wallet faces — two to a row (one under 340dp, three from 600dp).
 */
private fun LazyListScope.walletSection(
    key: String,
    title: String,
    caption: String,
    wallets: List<Wallet>,
    primaryCurrency: String,
    cards: Boolean,
    onOpen: (Wallet) -> Unit,
    fmt: AmountFormat,
    /** A destination shown beside the title in place of the caption, e.g. Debts & loans. */
    link: Pair<String, () -> Unit>? = null,
    footnote: String? = null,
) {
    if (wallets.isEmpty()) return
    item(key = "header-$key") {
        // One subtotal in the main currency (owner, 2026-11-06): a wallet in another currency counts at its value in the
        // main one (the same average-method value its row shows). Only a foreign wallet with no rate yet stays in its own
        // currency, listed after — currencies are never added together without a rate.
        val fx = com.lucento.app.feature.fx.LocalFxDisplay.current
        val subtotals = wallets.groupBy { wallet ->
            if (wallet.currencyCode == primaryCurrency || fx.netWorthValue(wallet) != null) primaryCurrency else wallet.currencyCode
        }
            .map { (code, list) -> Money.sum(code, list.map { if (it.currencyCode == code) it.netWorthContribution else fx.netWorthValue(it)!! }) }
            .sortedWith(compareBy<Money> { it.currencyCode != primaryCurrency }.thenBy { it.currencyCode })
        ListSectionHeader(
            title,
            caption = "$caption · ${wallets.size}",
            subtotal = subtotals.joinToString(" · ") { fmt.signed(it, showPlus = false) },
            action = link?.let { (label, open) ->
                {
                    Row(
                        Modifier
                            .heightIn(min = Sizes.minTouch)
                            .clip(MaterialTheme.shapes.small)
                            .clickable(role = Role.Button, onClick = open)
                            .padding(horizontal = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            },
        )
    }
    item(key = "card-$key") {
        if (cards) {
            WalletFaceGrid(wallets, onOpen, Modifier.animateItem())
        } else {
            LucentoCard(modifier = Modifier.fillMaxWidth().animateItem(), contentPadding = 0.dp) {
                wallets.forEachIndexed { index, wallet ->
                    WalletRow(wallet = wallet, onClick = { onOpen(wallet) }, dense = true)
                    if (index < wallets.lastIndex) Hairline(strong = true)
                }
            }
        }
    }
    if (footnote != null) {
        item(key = "footnote-$key") {
            Text(
                footnote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm, start = Spacing.xxs),
            )
        }
    }
}

@Composable
private fun WalletFaceGrid(wallets: List<Wallet>, onOpen: (Wallet) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = when {
            maxWidth < 300.dp -> 1
            maxWidth >= 520.dp -> 3
            else -> 2
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            wallets.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { wallet ->
                        val owner = walletOwnership(wallet)
                        if (owner == null) {
                            CompactWalletFace(wallet, onClick = { onOpen(wallet) }, modifier = Modifier.weight(1f).fillMaxHeight())
                        } else {
                            Column(Modifier.weight(1f).fillMaxHeight()) {
                                CompactWalletFace(wallet, onClick = { onOpen(wallet) }, modifier = Modifier.fillMaxWidth().weight(1f))
                                Text(
                                    owner,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = Spacing.xs, start = Spacing.xxs),
                                )
                            }
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
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
            append("Can’t reconcile? Open the wallet › Adjust balance.")
        },
        modifier = Modifier.padding(top = Spacing.lg),
        icon = if (warn) Icons.Outlined.WarningAmber else Icons.Outlined.Info,
        container = if (warn) ext.warningContainer else MaterialTheme.colorScheme.surfaceContainer,
        content = if (warn) ext.warning else MaterialTheme.colorScheme.onSurfaceVariant,
        actionLabel = onScanReceipt?.let { "Scan a receipt" },
        onAction = onScanReceipt,
    )
}
