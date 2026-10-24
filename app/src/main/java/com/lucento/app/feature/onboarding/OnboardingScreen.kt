package com.lucento.app.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.core.designsystem.component.CurrencyList
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoMark
import com.lucento.app.core.designsystem.component.LucentoMascot
import com.lucento.app.core.designsystem.component.LucentoAnimation
import com.lucento.app.core.designsystem.component.LucentoLottieAnimation
import com.lucento.app.core.designsystem.component.MascotMood
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.component.SegmentedToggle
import com.lucento.app.core.designsystem.component.SwitchRow
import com.lucento.app.core.designsystem.component.rememberHaptics
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.data.preferences.ThemeMode
import com.lucento.app.domain.Feature
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.SolidColor
import com.lucento.app.core.designsystem.component.IllustratedSurface
import com.lucento.app.core.designsystem.component.LucentoIllustration
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.domain.FinancialFocus
import com.lucento.app.feature.budget.CategoryPicks
import com.lucento.app.feature.budget.CategoryPresetPicker
import com.lucento.app.feature.budget.CategoryReview

@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = viewModel(factory = OnboardingViewModel.Factory)) {
    val step by viewModel.step.collectAsStateWithLifecycle()
    val currency by viewModel.currency.collectAsStateWithLifecycle()
    val focuses by viewModel.focuses.collectAsStateWithLifecycle()
    val features by viewModel.features.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val picks by viewModel.picks.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    BackHandler(enabled = step > 0) { viewModel.back() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = Spacing.gutter, vertical = Spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepIndicator(current = step, count = OnboardingViewModel.LAST_STEP + 1, modifier = Modifier.weight(1f))
            // Focus and features are optional; spending categories are not, so Skip leads to them.
            if (step in OnboardingViewModel.STEP_FOCUS until OnboardingViewModel.STEP_CATEGORIES) {
                LucentoTextButton(text = "Skip", onClick = viewModel::skip)
            }
        }

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val forward = targetState > initialState
                (slideInHorizontally(tween(320)) { w -> if (forward) w / 6 else -w / 6 } + fadeIn(tween(320)))
                    .togetherWith(slideOutHorizontally(tween(220)) { w -> if (forward) -w / 6 else w / 6 } + fadeOut(tween(180)))
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "onboarding-step",
        ) { target ->
            when (target) {
                OnboardingViewModel.STEP_WELCOME -> WelcomeStep(onContinue = viewModel::next)
                OnboardingViewModel.STEP_FOCUS -> FocusStep(focuses, viewModel::toggleFocus, viewModel::next)
                OnboardingViewModel.STEP_FEATURES -> FeaturesStep(features, viewModel::setFeature, viewModel::next)
                OnboardingViewModel.STEP_CATEGORIES -> CategoriesStep(picks, viewModel::setPicks, viewModel::next)
                OnboardingViewModel.STEP_REVIEW -> CategoryReviewStep(
                    picks = picks,
                    onChange = viewModel::setPicks,
                    onEdit = { viewModel.goTo(OnboardingViewModel.STEP_CATEGORIES) },
                    onContinue = viewModel::next,
                )
                OnboardingViewModel.STEP_ESSENTIALS -> EssentialsStep(
                    currency = currency,
                    onCurrency = viewModel::selectCurrency,
                    theme = theme,
                    onTheme = viewModel::selectTheme,
                    onContinue = viewModel::next,
                )
                else -> ConfirmStep(currency, theme, features, picks.count, error, onFinish = viewModel::finish)
            }
        }
    }
}

@Composable
private fun StepIndicator(current: Int, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(top = Spacing.sm, bottom = Spacing.lg)
            .semantics { contentDescription = "Step ${current + 1} of $count" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(count) { index ->
            val color by animateColorAsState(
                targetValue = if (index <= current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                label = "step-color",
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .background(color, RoundedCornerShape(2.dp)),
            )
        }
    }
}

@Composable
private fun StepTitle(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(Spacing.sm))
    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(Spacing.lg))
}

@Composable
private fun WelcomeStep(onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // The mark assembles once (≈2.5 s, never blocks the button); reduced motion shows it complete.
            LucentoLottieAnimation(
                animation = LucentoAnimation.Welcome,
                modifier = Modifier.size(width = 132.dp, height = 110.dp),
                contentDescription = "Lucento",
                fallback = { LucentoMark(size = 96.dp, animate = false) },
            )
            Spacer(Modifier.height(Spacing.sm))
            Text("Lucento", style = MaterialTheme.typography.displayMedium, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "Financial clarity without the complexity.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.xl))
            // Lucento greets the user standing in its sunrise landscape (decorative artwork; the mascot keeps its label).
            IllustratedSurface(
                illustration = LucentoIllustration.SunriseLandscape,
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.background,
                overlay = if (LucentoTheme.isDark) SolidColor(MaterialTheme.colorScheme.background.copy(alpha = 0.45f)) else null,
                artworkAlignment = Alignment.BottomCenter,
                contentPadding = PaddingValues(top = Spacing.lg, bottom = Spacing.sm),
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    LucentoMascot(size = 140.dp, mood = MascotMood.Greeting, contentDescription = "Lucento, waving hello with a bright torch")
                }
            }
            Spacer(Modifier.height(Spacing.lg))
            Text(
                "Track what you spend, plan what’s next — privately, on this phone. No account, no bank login.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        LucentoPrimaryButton(text = "Get started", onClick = onContinue)
    }
}

@Composable
private fun FocusStep(selected: Set<FinancialFocus>, onToggle: (FinancialFocus) -> Unit, onContinue: () -> Unit) {
    val haptics = rememberHaptics()
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            StepTitle("What’s your main focus?", "Choose one or more. This only decides what’s switched on at first.")
            FinancialFocus.entries.forEach { focus ->
                val isSelected = focus in selected
                val border by animateColorAsState(
                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    tween(160),
                    label = "focus-border",
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.xs)
                        .heightIn(min = 64.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .border(BorderStroke(if (isSelected) 1.5.dp else 1.dp, border), MaterialTheme.shapes.medium)
                        .toggleable(value = isSelected, role = Role.Checkbox) {
                            haptics.tick()
                            onToggle(focus)
                        }
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(focus.title, style = MaterialTheme.typography.titleMedium)
                        Text(focus.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(
                        if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.md))
        LucentoPrimaryButton(text = "Continue", onClick = onContinue)
    }
}

@Composable
private fun FeaturesStep(enabled: Set<Feature>, onSet: (Feature, Boolean) -> Unit, onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            StepTitle("Choose your features", "Based on your focus. Change any of these later in Settings — nothing is ever deleted.")
            Feature.entries.forEachIndexed { index, feature ->
                val missing = feature.requires.filterNot { it in enabled }
                SwitchRow(
                    title = feature.title,
                    description = feature.description,
                    note = if (feature in enabled && missing.isNotEmpty()) "Needs ${missing.joinToString { it.title }}" else null,
                    checked = feature in enabled,
                    onCheckedChange = { onSet(feature, it) },
                )
                if (index < Feature.entries.lastIndex) Hairline()
            }
        }
        Spacer(Modifier.height(Spacing.md))
        LucentoPrimaryButton(text = "Continue", onClick = onContinue)
    }
}

@Composable
private fun CategoriesStep(picks: CategoryPicks, onChange: (CategoryPicks) -> Unit, onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize().imePadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            StepTitle(
                "What do you spend on?",
                "Pick the categories that fit your life — your budget is built from them. We’ve started you with " +
                    "everyday essentials; add or remove anything. You’ll set amounts later, on the Budget tab.",
            )
            CategoryPresetPicker(picks = picks, onChange = onChange)
            Spacer(Modifier.height(Spacing.lg))
        }
        Spacer(Modifier.height(Spacing.md))
        LucentoPrimaryButton(
            text = if (picks.count == 0) "Choose at least one category" else "Review ${picks.count} ${if (picks.count == 1) "category" else "categories"}",
            onClick = onContinue,
            enabled = picks.count > 0,
        )
    }
}

@Composable
private fun CategoryReviewStep(
    picks: CategoryPicks,
    onChange: (CategoryPicks) -> Unit,
    onEdit: () -> Unit,
    onContinue: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            StepTitle(
                "Your spending categories",
                "Tap one to remove it. You can rename, reorder or add categories any time on the Budget tab.",
            )
            CategoryReview(picks = picks, onChange = onChange, onEdit = onEdit)
            Spacer(Modifier.height(Spacing.lg))
        }
        Spacer(Modifier.height(Spacing.md))
        LucentoPrimaryButton(
            text = if (picks.count == 0) "Choose at least one category" else "Confirm ${picks.count} ${if (picks.count == 1) "category" else "categories"}",
            onClick = onContinue,
            enabled = picks.count > 0,
        )
    }
}

@Composable
private fun EssentialsStep(
    currency: String,
    onCurrency: (String) -> Unit,
    theme: ThemeMode,
    onTheme: (ThemeMode) -> Unit,
    onContinue: () -> Unit,
) {
    Column(Modifier.fillMaxSize().imePadding()) {
        StepTitle("Currency and look", "Your main currency is used for totals. Each wallet can still use its own.")
        SegmentedToggle(
            options = listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark"),
            selected = theme,
            onSelect = onTheme,
        )
        Spacer(Modifier.height(Spacing.md))
        CurrencyList(selected = currency, onSelect = onCurrency, modifier = Modifier.weight(1f))
        Spacer(Modifier.height(Spacing.md))
        LucentoPrimaryButton(text = "Continue", onClick = onContinue)
    }
}

@Composable
private fun ConfirmStep(
    currency: String,
    theme: ThemeMode,
    features: Set<Feature>,
    categoryCount: Int,
    error: String?,
    onFinish: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("You’re all set", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
                    Text(
                        "Here’s how Lucento will start.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LucentoMascot(size = 72.dp, mood = MascotMood.Success)
            }
            Spacer(Modifier.height(Spacing.lg))
            SummaryRow("Main currency", "$currency · ${MoneyFormatter.currencyName(currency)}")
            SummaryRow(
                "Appearance",
                when (theme) {
                    ThemeMode.SYSTEM -> "Follows your phone"
                    ThemeMode.LIGHT -> "Light"
                    ThemeMode.DARK -> "Dark"
                },
            )
            SummaryRow("Spending categories", "$categoryCount chosen · set amounts on the Budget tab")
            SummaryRow("Turned on", Feature.entries.filter { it in features }.joinToString { it.title }.ifEmpty { "Just the essentials" })
            val off = Feature.entries.filterNot { it in features }
            if (off.isNotEmpty()) SummaryRow("Turned off", off.joinToString { it.title })
            Spacer(Modifier.height(Spacing.md))
            Text(
                "Next: add your first wallet on Home. Everything stays on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (error != null) {
                Spacer(Modifier.height(Spacing.md))
                InfoNote(error, container = MaterialTheme.colorScheme.errorContainer, content = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
        LucentoPrimaryButton(text = "Start using Lucento", onClick = onFinish)
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
    Hairline()
}
