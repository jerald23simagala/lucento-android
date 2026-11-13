package com.lucento.app.feature.pro

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AllInclusive
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.core.designsystem.component.HeroCard
import com.lucento.app.core.designsystem.component.IconTile
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoCard
import com.lucento.app.core.designsystem.component.LucentoMascot
import com.lucento.app.core.designsystem.component.LucentoSecondaryButton
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.component.LucentoTopBar
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.MascotMood
import com.lucento.app.core.designsystem.component.SectionHeader
import com.lucento.app.core.designsystem.component.StatusPill
import com.lucento.app.core.designsystem.component.StatusTones
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Sizes
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.data.billing.ProductPrice
import com.lucento.app.data.billing.SimulatedPlan
import com.lucento.app.domain.entitlement.EntitlementSource
import com.lucento.app.domain.entitlement.PlanCatalog
import com.lucento.app.domain.entitlement.ProFeature

/**
 * The Lucento Pro page (docs/MONETIZATION.md §5.5, customer copy A–D): the plan this phone holds, what each plan
 * includes (generated from [PlanCatalog], so it can't disagree with the gates), the plans with Google Play's prices,
 * what Lifetime means, and Restore purchases. It is the only place that offers a plan; locks elsewhere link here.
 * Which plans it offers, and how each button reads, is [ProOffers] (pure, tested). Until Lucento Pro is on sale there are
 * no prices and no buy buttons — it says so instead.
 */
@Composable
fun ProScreen(
    onBack: () -> Unit,
    /** The feature whose lock led here; its sentence is shown first. */
    from: ProFeature? = null,
    /** "See your Pro cards": the welcome's cards and collection again, without the medal (shown while Pro is active). */
    onReplayTour: (() -> Unit)? = null,
    viewModel: ProViewModel = viewModel(factory = ProViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val now = System.currentTimeMillis()
    val status = ProCopy.planStatus(state.entitlements, now)
    val ext = LucentoTheme.extendedColors
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter),
    ) {
        LucentoTopBar(title = "Lucento Pro", onBack = onBack)

        // The plan this phone holds, and the mascot: its one appearance in the Pro experience.
        HeroCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Your plan", style = MaterialTheme.typography.labelMedium, color = ext.onHeroMuted)
                    Text(
                        status.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = ext.onHero,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(status.detail, style = MaterialTheme.typography.bodyMedium, color = ext.onHeroMuted)
                }
                Spacer(Modifier.width(Spacing.md))
                LucentoMascot(size = 64.dp, mood = MascotMood.Happy, animate = false)
            }
        }
        // While Pro is active: the welcome's tour and summary again, without the medal.
        if (onReplayTour != null && state.entitlements.has(com.lucento.app.domain.entitlement.Capability.LOCAL_PRO, now)) {
            Spacer(Modifier.height(Spacing.md))
            LucentoSecondaryButton(text = "See your Pro cards", onClick = onReplayTour, icon = Icons.Outlined.Replay)
        }
        if (from != null && !state.entitlements.has(from.requires, now)) {
            Spacer(Modifier.height(Spacing.md))
            InfoNote(ProCopy.lockedLine(from), icon = Icons.Outlined.WorkspacePremium)
        }

        SectionHeader(
            if (state.entitlements.has(com.lucento.app.domain.entitlement.Capability.LOCAL_PRO, now)) "Plans" else "Choose your plan",
            Modifier.padding(top = Spacing.section),
        )
        // Lucento Cloud appears only for people who own Lucento; shown to anyone else it would undercut Pro (D-12).
        ProOffers.cards(state.entitlements, state.prices, now).forEachIndexed { i, card ->
            if (i > 0) Spacer(Modifier.height(Spacing.md))
            PlanCard(
                card = card,
                enabled = !state.purchasing,
                onBuy = { price -> context.findActivity()?.let { viewModel.buy(it, price) } },
            )
        }
        // How the purchase went, right under the button that started it (TalkBack reads it when it changes).
        val purchaseLine = if (state.purchasing) "Waiting for Google Play…" else state.purchaseMessage
        if (purchaseLine != null) {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                purchaseLine,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (state.prices.isEmpty()) {
            Spacer(Modifier.height(Spacing.md))
            InfoNote(
                "Lucento Pro isn’t on sale yet. When it is, you’ll buy it here through Google Play, with prices in your own " +
                    "currency. Nothing is charged before then.",
            )
        }

        SectionHeader(
            "What each plan includes",
            Modifier.padding(top = Spacing.section),
            supporting = "Recording, history, budgets, schedules, debts, Ask Lucento on this phone, backup files and CSV are " +
                "free on every plan, with no limits.",
        )
        PlanComparison(PlanCatalog.comparisonRows())

        LifetimePromise()

        SectionHeader("Your purchases", Modifier.padding(top = Spacing.section))
        LucentoSecondaryButton(
            text = if (state.restoring) "Checking…" else "Restore purchases",
            onClick = viewModel::restore,
            enabled = !state.restoring,
        )
        state.message?.let {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        state.verificationIssue?.let(ProCopy::verificationIssue)?.let {
            Spacer(Modifier.height(Spacing.sm))
            InfoNote(it)
        }
        val subscribed = state.entitlements.active(now).any {
            it.source == EntitlementSource.PLAY_SUBSCRIPTION || it.source == EntitlementSource.PLAY_CLOUD_ADDON
        }
        if (subscribed) {
            LucentoTextButton(
                text = "Manage subscription in Google Play",
                onClick = {
                    val url = "https://play.google.com/store/account/subscriptions?package=${context.packageName}"
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                },
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            "Subscriptions renew until you cancel in Google Play. Your data stays on this phone whatever you choose, and limits " +
                "only ever apply to adding new items.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.simulatorAvailable) {
            SimulatorSection(
                selected = state.simulated,
                onSelect = viewModel::simulate,
                testStore = state.testStore,
                onTestStore = viewModel::setTestStore,
            )
        }
        Spacer(Modifier.height(Spacing.xxl))
    }
}

/**
 * One plan: what it is in a line, what it brings as a short checklist ([ProCopy.pitch]), then how to buy it. A plan sold
 * by the year or the month shows both periods as a radio choice — Play's prices, a trial when Play lists one, and the
 * yearly saving worked out from Play's own amounts — above a single button for the chosen period.
 */
@Composable
private fun PlanCard(card: PlanCardModel, enabled: Boolean, onBuy: (ProductPrice) -> Unit) {
    val title = when (card.kind) {
        PlanCardKind.PRO -> "Lucento Pro"
        PlanCardKind.LIFETIME -> "Pro Lifetime"
        PlanCardKind.CLOUD -> "Lucento Cloud"
    }
    val icon = when (card.kind) {
        PlanCardKind.PRO -> Icons.Outlined.WorkspacePremium
        PlanCardKind.LIFETIME -> Icons.Outlined.AllInclusive
        PlanCardKind.CLOUD -> Icons.Outlined.Cloud
    }
    val pitch = ProCopy.pitch(card.kind)
    val picker = card.actions.size > 1 && card.actions.all { it.periodName != null }
    var selected by rememberSaveable(card.kind) { mutableIntStateOf(0) }
    val chosen = card.actions.getOrNull(selected) ?: card.actions.firstOrNull()

    LucentoCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, container = StatusTones.brand.container, content = StatusTones.brand.content)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.semantics { heading() },
                )
                Text(pitch.tagline, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (card.current) {
                Spacer(Modifier.width(Spacing.sm))
                StatusPill("Your plan", StatusTones.positive)
            }
        }
        Spacer(Modifier.height(Spacing.md))
        pitch.benefits.forEach { Benefit(it) }

        if (picker && chosen != null) {
            Spacer(Modifier.height(Spacing.lg))
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                card.actions.forEachIndexed { i, action ->
                    PeriodOption(action, selected = action == chosen, enabled = enabled) { selected = i }
                }
            }
            Spacer(Modifier.height(Spacing.md))
            LucentoPrimaryButton(text = chosen.label, onClick = { onBuy(chosen.price) }, enabled = enabled, modifier = Modifier.fillMaxWidth())
        } else {
            card.actions.forEach { action ->
                Spacer(Modifier.height(Spacing.lg))
                PriceBlock(action)
                Spacer(Modifier.height(Spacing.md))
                LucentoPrimaryButton(text = action.label, onClick = { onBuy(action.price) }, enabled = enabled, modifier = Modifier.fillMaxWidth())
            }
        }
        card.note?.let {
            Spacer(Modifier.height(Spacing.sm))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Benefit(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Rounded.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = Spacing.xxs).size(18.dp),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** A one-time price, large, with how often it is charged ("once") beside it. */
@Composable
private fun PriceBlock(action: OfferAction) {
    Row(Modifier.semantics(mergeDescendants = true) { contentDescription = action.priceLine }) {
        Text(
            action.price.formattedPrice,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.alignByBaseline(),
        )
        if (action.per.isNotEmpty()) {
            Spacer(Modifier.width(Spacing.xs))
            Text(
                action.per,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
    action.trial?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }
}

/** One period of a subscription in the plan picker: name, trial and saving on the left, Play's price on the right. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeriodOption(action: OfferAction, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant, shape)
            .background(if (selected) scheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .heightIn(min = 64.dp)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            // The saving wraps under the name when the text is large rather than being cut short.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                Text(
                    action.periodName.orEmpty(),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                action.saving?.let { StatusPill(it, StatusTones.positive, Modifier.align(Alignment.CenterVertically)) }
            }
            action.trial?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.primary) }
        }
        Spacer(Modifier.width(Spacing.sm))
        Column(horizontalAlignment = Alignment.End) {
            Text(action.price.formattedPrice, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Text(action.per, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** What Lifetime means (customer copy B), short by default; the whole promise on request. */
@Composable
private fun LifetimePromise() {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SectionHeader("What Lifetime means", Modifier.padding(top = Spacing.section))
    PromiseItem(
        "Pay once, own it",
        "Every Pro feature that runs on your phone is yours for as long as Lucento exists as an app: no renewal, no expiry, " +
            "no subscription to keep alive.",
    )
    PromiseItem(
        "Works offline",
        "Lucento doesn’t check your purchase every time you open it. Everything you own keeps working with no internet and " +
            "without Lucento’s servers.",
    )
    if (expanded) {
        PromiseItem(
            "Online services",
            "Cloud sync, restoring from your account, Lucento Shared and the AI adviser’s Pro allowance run on servers that cost " +
                "money every month, so they’re included for the first year. After that, keep them with Lucento Cloud, or carry on " +
                "without: nothing you own changes.",
        )
        PromiseItem(
            "A new phone",
            "Use the same Google account in Google Play and your purchase comes back by itself, or sign in to the Lucento " +
                "account you used before. Bring your data with a backup file, or restore it from your account while cloud " +
                "services are on.",
        )
        PromiseItem(
            "If Lucento’s servers are ever down",
            "Everything you own keeps working, because none of it depends on them. Only the online services would pause, for " +
                "everyone, until they’re back.",
        )
    }
    LucentoTextButton(
        text = if (expanded) "Show less" else "Read the whole promise",
        onClick = { expanded = !expanded },
    )
}

@Composable
private fun PromiseItem(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.sm).semantics(mergeDescendants = true) {}) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(Spacing.xxs))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Test builds only: switch plans without Google Play (the same purchases Play would report). Never in a release build. */
@Composable
private fun SimulatorSection(
    selected: SimulatedPlan?,
    onSelect: (SimulatedPlan?) -> Unit,
    testStore: Boolean,
    onTestStore: (Boolean) -> Unit,
) {
    SectionHeader(
        "Test builds only",
        Modifier.padding(top = Spacing.section),
        supporting = "Simulate a plan without Google Play. Release builds never show this.",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.minTouch)
            .toggleable(value = testStore, role = Role.Switch, onValueChange = onTestStore)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Test store", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Show test prices and buy instantly, without Google Play. Nothing is charged; a test purchase lasts until " +
                    "the app restarts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        Switch(checked = testStore, onCheckedChange = null)
    }
    Column(Modifier.selectableGroup()) {
        SimulatorOption("My account’s plan", "This account’s plan on the server, as a release build reads it.", selected == null) { onSelect(null) }
        SimulatedPlan.entries.forEach { plan ->
            SimulatorOption(plan.label, plan.detail, selected == plan) { onSelect(plan) }
        }
    }
}

@Composable
private fun SimulatorOption(label: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.minTouch)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
