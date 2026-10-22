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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.core.designsystem.component.CurrencyList
import com.lucento.app.core.designsystem.component.IconBadge
import com.lucento.app.core.designsystem.component.LucentoMark
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.theme.Spacing

@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = viewModel(factory = OnboardingViewModel.Factory)) {
    val step by viewModel.step.collectAsStateWithLifecycle()
    val currency by viewModel.currency.collectAsStateWithLifecycle()

    BackHandler(enabled = step > 0) { viewModel.back() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = Spacing.gutter, vertical = Spacing.lg),
    ) {
        StepIndicator(current = step, count = OnboardingViewModel.LAST_STEP + 1)

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
                0 -> WelcomeStep(onContinue = viewModel::next)
                1 -> PrinciplesStep(onContinue = viewModel::next)
                else -> CurrencyStep(
                    selected = currency,
                    onSelect = viewModel::selectCurrency,
                    onFinish = viewModel::finish,
                )
            }
        }
    }
}

@Composable
private fun StepIndicator(current: Int, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.sm, bottom = Spacing.lg)
            .semantics { contentDescription = "Step ${current + 1} of $count" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(count) { index ->
            val color by animateColorAsState(
                targetValue = if (index <= current) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                label = "step-color",
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(3.dp)
                    .background(color, RoundedCornerShape(2.dp)),
            )
        }
    }
}

@Composable
private fun WelcomeStep(onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            LucentoMark(size = 120.dp)
            Spacer(Modifier.height(Spacing.xl))
            Text(
                "Lucento",
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "Financial clarity without the complexity.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        LucentoPrimaryButton(text = "Get started", onClick = onContinue)
    }
}

@Composable
private fun PrinciplesStep(onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
            Text(
                "Your money, seen clearly",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(Spacing.xxl))
            Principle(
                icon = Icons.Outlined.Lock,
                title = "Private by design",
                body = "Everything is stored on this device. No account, no bank login, no tracking.",
            )
            Principle(
                icon = Icons.Outlined.CloudOff,
                title = "Works offline",
                body = "Every core feature runs without an internet connection.",
            )
            Principle(
                icon = Icons.Outlined.Tune,
                title = "Exact, not approximate",
                body = "Amounts are calculated precisely to the smallest unit of your currency.",
            )
        }
        LucentoPrimaryButton(text = "Continue", onClick = onContinue)
    }
}

@Composable
private fun Principle(icon: ImageVector, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.md), verticalAlignment = Alignment.Top) {
        IconBadge(icon)
        Spacer(Modifier.size(Spacing.lg))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CurrencyStep(selected: String, onSelect: (String) -> Unit, onFinish: () -> Unit) {
    Column(Modifier.fillMaxSize().imePadding()) {
        Text(
            "Choose your main currency",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            "Used for your totals. Each wallet can still use its own currency.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.lg))
        CurrencyList(selected = selected, onSelect = onSelect, modifier = Modifier.weight(1f))
        Spacer(Modifier.height(Spacing.lg))
        LucentoPrimaryButton(text = "Start using Lucento", onClick = onFinish)
    }
}
