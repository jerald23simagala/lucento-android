package com.lucento.app.feature.pro

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.HeroCard
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoCard
import com.lucento.app.core.designsystem.component.LucentoMascot
import com.lucento.app.core.designsystem.component.LucentoSecondaryButton
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.component.LucentoTopBar
import com.lucento.app.core.designsystem.component.MascotMood
import com.lucento.app.core.designsystem.component.SectionHeader
import com.lucento.app.core.designsystem.component.StatusPill
import com.lucento.app.core.designsystem.component.StatusTones
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Sizes
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.data.billing.ProductPrice
import com.lucento.app.data.billing.SimulatedPlan
import com.lucento.app.domain.entitlement.DisplayPlan
import com.lucento.app.domain.entitlement.EntitlementSource
import com.lucento.app.domain.entitlement.PlanCatalog
import com.lucento.app.domain.entitlement.PlayProducts
import com.lucento.app.domain.entitlement.ProFeature

/**
 * The Lucento Pro page (docs/MONETIZATION.md §5.5, customer copy A–D): the plan this phone holds, what each plan
 * includes (generated from [PlanCatalog], so it can't disagree with the gates), the plans with Google Play's prices,
 * what Lifetime means, and Restore purchases. It is the only place that offers a plan; locks elsewhere link here.
 * Until Lucento Pro is on sale there are no prices and no buy buttons — it says so instead.
 */
@Composable
fun ProScreen(
    onBack: () -> Unit,
    /** The feature whose lock led here; its sentence is shown first. */
    from: ProFeature? = null,
    viewModel: ProViewModel = viewModel(factory = ProViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val now = System.currentTimeMillis()
    val plan = state.entitlements.displayPlan(now)
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
        if (from != null && !state.entitlements.has(from.requires, now)) {
            Spacer(Modifier.height(Spacing.md))
            InfoNote(ProCopy.lockedLine(from), icon = Icons.Outlined.WorkspacePremium)
        }

        SectionHeader(
            "What each plan includes",
            Modifier.padding(top = Spacing.section),
            supporting = "Recording, history, budgets, schedules, debts, Ask Lucento on this phone, backup files and CSV are " +
                "free on every plan, with no limits.",
        )
        PlanCatalog.comparisonRows().forEachIndexed { i, row ->
            if (i > 0) Hairline()
            ComparisonItem(row)
        }

        SectionHeader("Plans", Modifier.padding(top = Spacing.section))
        PlanCard(
            title = "Lucento Pro",
            body = "Everything in Lucento, including cloud sync, Lucento Shared and the AI adviser, while you subscribe. " +
                "Monthly or yearly; renews until you cancel in Google Play.",
            prices = state.prices.filter { it.productId == PlayProducts.PRO_SUBSCRIPTION },
            current = plan == DisplayPlan.PRO,
        )
        Spacer(Modifier.height(Spacing.md))
        PlanCard(
            title = "Pro Lifetime",
            body = "Pay once. Every Pro feature that runs on your phone is yours for life, offline, with no renewal. Cloud " +
                "sync, Lucento Shared and the AI adviser are included for the first year.",
            prices = state.prices.filter { it.productId == PlayProducts.PRO_LIFETIME },
            current = plan == DisplayPlan.LIFETIME || plan == DisplayPlan.LIFETIME_WITH_SERVICES,
        )
        // Lucento Cloud is only for people who own Lucento; shown to anyone else it would undercut Pro (D-12).
        if (plan == DisplayPlan.LIFETIME || plan == DisplayPlan.LIFETIME_WITH_SERVICES) {
            Spacer(Modifier.height(Spacing.md))
            PlanCard(
                title = "Lucento Cloud",
                body = "The online services for people who own Lucento: cloud sync, restoring from your account, Lucento " +
                    "Shared and the AI adviser’s Pro allowance. Optional; renews until you cancel in Google Play.",
                prices = state.prices.filter { it.productId == PlayProducts.CLOUD_ADDON },
                current = state.entitlements.active(now).any { it.source == EntitlementSource.PLAY_CLOUD_ADDON },
            )
        }
        if (state.prices.isEmpty()) {
            Spacer(Modifier.height(Spacing.md))
            InfoNote(
                "Lucento Pro isn’t on sale yet. When it is, you’ll buy it here through Google Play, with prices in your own " +
                    "currency. Nothing is charged before then.",
            )
        }

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
            SimulatorSection(selected = state.simulated, onSelect = viewModel::simulate)
        }
        Spacer(Modifier.height(Spacing.xxl))
    }
}

@Composable
private fun PlanCard(title: String, body: String, prices: List<ProductPrice>, current: Boolean) {
    LucentoCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (current) StatusPill("Your plan", StatusTones.positive)
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        prices.forEach { price ->
            Spacer(Modifier.height(Spacing.sm))
            Text(ProCopy.priceLine(price), style = MaterialTheme.typography.titleSmall)
        }
    }
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
private fun SimulatorSection(selected: SimulatedPlan?, onSelect: (SimulatedPlan?) -> Unit) {
    SectionHeader(
        "Test builds only",
        Modifier.padding(top = Spacing.section),
        supporting = "Simulate a plan without Google Play. Release builds never show this.",
    )
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
