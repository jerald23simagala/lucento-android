package com.lucento.app.feature.plan

import com.lucento.app.core.designsystem.component.icon
import com.lucento.app.core.designsystem.component.MoneyText
import com.lucento.app.core.designsystem.component.HeroTones
import com.lucento.app.core.designsystem.component.HeroPill
import com.lucento.app.core.designsystem.component.HeroCard
import com.lucento.app.core.designsystem.component.Confirmation
import com.lucento.app.core.designsystem.component.ConfirmationHost
import com.lucento.app.domain.GoalMilestone
import com.lucento.app.core.designsystem.component.LucentoAnimation
import com.lucento.app.core.designsystem.component.EmptyIllustration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.core.designsystem.component.AmountText
import com.lucento.app.core.designsystem.component.ConfirmDialog
import com.lucento.app.core.designsystem.component.EmptyState
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.IconTile
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoBottomSheet
import com.lucento.app.core.designsystem.component.SheetHeader
import com.lucento.app.core.designsystem.component.LucentoCard
import com.lucento.app.core.designsystem.component.LucentoMascot
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.LucentoSecondaryButton
import com.lucento.app.core.designsystem.component.LucentoTopBar
import com.lucento.app.core.designsystem.component.MascotMood
import com.lucento.app.core.designsystem.component.SegmentedToggle
import com.lucento.app.core.designsystem.component.ShareBar
import com.lucento.app.core.designsystem.component.StatusPill
import com.lucento.app.core.designsystem.component.StatusTones
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.Money
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.data.repository.GoalRepository
import com.lucento.app.domain.GoalCalculator
import com.lucento.app.domain.GoalProgress
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import com.lucento.app.core.designsystem.component.IllustratedSurface
import com.lucento.app.core.designsystem.component.IllustrationScrims
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.BitmapFactory
import com.lucento.app.data.media.GoalPhotos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.launch
import java.time.LocalDate
import com.lucento.app.domain.GoalIcon
import com.lucento.app.domain.GoalSuggestions
import com.lucento.app.domain.WalletType
import com.lucento.app.feature.wallets.WalletDraft
import com.lucento.app.feature.wallets.WalletEditorSheet
import com.lucento.app.feature.wallets.WalletsViewModel
import com.lucento.app.core.designsystem.component.IconFieldHeader
import com.lucento.app.core.designsystem.component.ProgressRing
import com.lucento.app.core.designsystem.component.HeroStatTile
import com.lucento.app.core.designsystem.component.ToneSectionHeader
import com.lucento.app.core.designsystem.component.WalletIdentityTile
import com.lucento.app.core.designsystem.component.ChoicePill
import com.lucento.app.core.designsystem.theme.TonePair
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Surface
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.ChildFriendly
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.AddAPhoto

private data class GoalDraft(
    val id: Long? = null,
    val name: String = "",
    val currencyCode: String,
    val amountText: String = "",
    val targetDate: LocalDate? = null,
    val walletId: Long? = null,
    /** Chosen icon ([GoalIcon.key]; older goals may hold a scene key); null = automatic from the name. */
    val sceneKey: String? = null,
    /** The user's own card photo (private copy); when set it is the picture shown. */
    val photoPath: String? = null,
)

/** The new-goal sheet on its own, for the guided setup; [onDismiss] runs when it closes, saved or not. */
@Composable
internal fun NewGoalSheet(currencyCode: String, onDismiss: () -> Unit, viewModel: PlanViewModel = viewModel(factory = PlanViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current.applicationContext
    val photos = remember(context) { GoalPhotos(context) }
    GoalEditorSheet(
        initial = GoalDraft(currencyCode = currencyCode),
        state = state,
        photos = photos,
        onSave = { d, money, done -> viewModel.saveGoal(d.id, d.name, money, d.targetDate, d.walletId, d.sceneKey, d.photoPath, done) },
        onDismiss = onDismiss,
    )
}

/**
 * Savings goals (owner, 2026-11-04 redesign). A hero with everything saved toward goals in the main currency and the
 * share of all targets reached, then goals in progress and goals reached: each card shows its icon (or the user's
 * photo), the saved amount against the target, a progress bar that fills in, and what it takes to get there. Progress
 * comes from earmarked contributions; wallet balances never change.
 */
@Composable
fun GoalsScreen(onBack: () -> Unit, viewModel: PlanViewModel = viewModel(factory = PlanViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<GoalDraft?>(null) }
    var openGoalId by remember { mutableStateOf<Long?>(null) }
    val context = LocalContext.current.applicationContext
    val photos = remember(context) { GoalPhotos(context) }
    var celebration by remember { mutableStateOf<Confirmation?>(null) }
    val progress = remember(state.goals, state.contributions, state.today) {
        state.goals.map { GoalCalculator.progress(it, state.contributions, state.today) }
    }
    val (reached, active) = remember(progress) { progress.partition { it.reached } }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.fabClearance),
        ) {
            item(key = "bar") {
                LucentoTopBar(title = "Goals", onBack = onBack, actions = {
                    if (state.goals.isNotEmpty()) {
                        com.lucento.app.core.designsystem.component.AddPillButton(
                            "Add",
                            onClick = { editing = GoalDraft(currencyCode = state.primaryCurrency) },
                            contentDescription = "Add goal",
                        )
                    }
                })
            }
            if (!state.loading && state.goals.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = "No goals yet",
                        body = "An emergency fund, a trip, a new laptop — set a target and Lucento tracks progress and when you’ll get there.",
                        illustration = { EmptyIllustration(LucentoAnimation.EmptyGoals) },
                        action = { LucentoPrimaryButton(text = "Create a goal", onClick = { editing = GoalDraft(currencyCode = state.primaryCurrency) }) },
                    )
                }
            }
            if (progress.isNotEmpty()) {
                item(key = "hero") { GoalsHero(progress, state.primaryCurrency) }
            }
            if (active.isNotEmpty()) {
                item(key = "active-head") {
                    ToneSectionHeader("In progress", StatusTones.brand, caption = if (active.size == 1) "1 goal" else "${active.size} goals")
                }
                items(active, key = { "goal-${it.goal.id}" }) { p ->
                    GoalCard(p, walletName = p.goal.linkedWalletId?.let { state.walletsById[it]?.name }, onClick = { openGoalId = p.goal.id })
                    Spacer(Modifier.height(Spacing.sm))
                }
            }
            if (reached.isNotEmpty()) {
                item(key = "reached-head") {
                    ToneSectionHeader("Reached", StatusTones.positive, caption = if (reached.size == 1) "1 goal · well done" else "${reached.size} goals · well done")
                }
                items(reached, key = { "goal-${it.goal.id}" }) { p ->
                    GoalCard(p, walletName = p.goal.linkedWalletId?.let { state.walletsById[it]?.name }, onClick = { openGoalId = p.goal.id })
                    Spacer(Modifier.height(Spacing.sm))
                }
            }
            if (state.goals.isNotEmpty()) {
                item(key = "note") {
                    InfoNote(
                        "Adding money to a goal sets it aside on paper — your wallet balances and net worth don’t change, so savings are never counted twice.",
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
            }
        }
        ConfirmationHost(confirmation = celebration, onDismissed = { celebration = null })
    }

    val open = progress.firstOrNull { it.goal.id == openGoalId }
    if (open != null) {
        GoalDetailSheet(
            progress = open,
            history = state.contributions.filter { it.goalId == open.goal.id },
            linkedWallet = open.goal.linkedWalletId?.let { state.walletsById[it]?.name },
            onContribute = { amount, done ->
                val name = open.goal.name
                viewModel.contribute(open.goal.id, amount, state.today, "", PlanViewModel.newKey(), done) { milestone ->
                    // Close the sheet so the celebration is visible above the goal list (sheets are their own window).
                    openGoalId = null
                    celebration = Confirmation(
                        when (milestone) {
                            GoalMilestone.HALFWAY -> "Halfway to $name"
                            GoalMilestone.REACHED -> "$name: goal reached"
                        },
                        kind = Confirmation.Kind.Milestone,
                    )
                }
            },
            onEdit = {
                openGoalId = null
                editing = GoalDraft(
                    id = open.goal.id,
                    name = open.goal.name,
                    currencyCode = open.goal.currencyCode,
                    amountText = MoneyFormatter.formatPlain(open.goal.target),
                    targetDate = open.goal.targetDate,
                    walletId = open.goal.linkedWalletId,
                    sceneKey = open.goal.sceneKey,
                    photoPath = open.goal.photoPath,
                )
            },
            onDelete = {
                photos.delete(open.goal.photoPath)
                viewModel.deleteGoal(open.goal.id)
                openGoalId = null
            },
            onDeleteContribution = viewModel::deleteContribution,
            onDismiss = { openGoalId = null },
        )
    }

    editing?.let { draft ->
        GoalEditorSheet(
            initial = draft,
            state = state,
            photos = photos,
            onSave = { d, money, done -> viewModel.saveGoal(d.id, d.name, money, d.targetDate, d.walletId, d.sceneKey, d.photoPath, done) },
            onDismiss = { editing = null },
        )
    }
}

/**
 * The page's statement, on brand blue: what is saved toward goals in the main currency (other currencies are never
 * added in), the share of all targets it covers as a ring, then still to save and how many goals are reached.
 */
@Composable
private fun GoalsHero(progress: List<GoalProgress>, primaryCurrency: String) {
    val ext = LucentoTheme.extendedColors
    val code = progress.firstOrNull { it.goal.currencyCode == primaryCurrency }?.goal?.currencyCode ?: progress.first().goal.currencyCode
    val mine = progress.filter { it.goal.currencyCode == code }
    // Saved counts up to each target: money beyond a reached goal isn't "toward goals" any more.
    val saved = Money.sum(code, mine.map { p -> if (p.saved.amountMinor > p.goal.target.amountMinor) p.goal.target else p.saved.coerceNonNegative() })
    val target = Money.sum(code, mine.map { it.goal.target })
    val left = (target - saved).coerceNonNegative()
    val reached = mine.count { it.reached }
    val share = com.lucento.app.domain.Insights.percentOf(saved, target) ?: 0
    HeroCard(Modifier.padding(top = Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                HeroPill("Saved for goals · $code", HeroTones.main, Modifier.semantics { heading() })
                Spacer(Modifier.height(Spacing.xs))
                MoneyText(money = saved, style = AmountStyles.hero, color = ext.onHero, minScale = 0.5f)
                Text(
                    "of ${MoneyFormatter.format(target)} · ${mine.size} ${if (mine.size == 1) "goal" else "goals"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = ext.onHeroMuted,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(Spacing.md))
            ProgressRing(
                fraction = if (target.amountMinor > 0L) saved.amountMinor.toFloat() / target.amountMinor else 0f,
                description = "$share percent of all goal targets saved",
                size = 76.dp,
                strokeWidth = 8.dp,
                color = ext.onHero,
                trackColor = ext.onHero.copy(alpha = 0.22f),
            ) {
                Text("$share%", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = ext.onHero)
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            HeroStatTile("Still to save", Icons.Outlined.Flag, "Still to save ${MoneyFormatter.format(left)}", Modifier.weight(1f)) {
                MoneyText(money = left, style = AmountStyles.row, color = ext.onHero, fractionScale = 1f, minScale = 0.6f)
            }
            HeroStatTile("Reached", Icons.Outlined.EmojiEvents, "Reached $reached of ${mine.size}", Modifier.weight(1f)) {
                Text("$reached of ${mine.size}", style = AmountStyles.row, color = ext.onHero)
            }
        }
        if (mine.size < progress.size) {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "Goals in other currencies are listed below and never added in.",
                style = MaterialTheme.typography.bodySmall,
                color = ext.onHeroMuted,
            )
        }
    }
}

private fun Money.coerceNonNegative(): Money = if (isNegative) Money.zero(currencyCode) else this

/**
 * A goal's card: its icon (or photo), name, saved against the target and the percentage; a progress bar that fills in;
 * then small facts — the target date, what a month takes, the estimate at the current pace, where the money is kept.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalCard(p: GoalProgress, walletName: String?, onClick: () -> Unit) {
    val ext = LucentoTheme.extendedColors
    LucentoCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = Spacing.md) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GoalBadge(p.goal, size = 48.dp)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    p.goal.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${MoneyFormatter.format(p.saved)} of ${MoneyFormatter.format(p.goal.target)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            if (p.reached) {
                StatusPill("Reached", StatusTones.positive)
            } else {
                Text("${p.percent}%", style = AmountStyles.row.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        ShareBar(fraction = p.fraction, color = ext.success, height = 8.dp)
        val facts = goalFacts(p, walletName)
        if (facts.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.sm))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                facts.forEach { (icon, text) -> FactChip(icon, text) }
            }
        }
    }
}

/** What a goal's card and sheet say about getting there, each with its icon. */
private fun goalFacts(p: GoalProgress, walletName: String?): List<Pair<ImageVector, String>> = buildList {
    if (p.reached) {
        add(Icons.Outlined.CheckCircle to "Target reached")
    } else {
        p.goal.targetDate?.let { add(Icons.Outlined.CalendarMonth to "By ${formatDate(it)}") }
        p.requiredPerMonth?.let { add(Icons.Outlined.Payments to "${MoneyFormatter.format(it)} a month") }
        add(Icons.Outlined.Schedule to (p.projectedCompletion?.let { "At your pace: ${formatDate(it)}" } ?: "Add money to see when"))
    }
    walletName?.let { add(Icons.Outlined.AccountBalanceWallet to it) }
}

@Composable
private fun FactChip(icon: ImageVector, text: String) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs + 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(Spacing.xxs + 2.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** A goal's square badge: the user's photo, else its icon on the icon's tone. */
@Composable
internal fun GoalBadge(goal: com.lucento.app.domain.Goal, size: androidx.compose.ui.unit.Dp) {
    val photo = rememberGoalPhoto(goal.photoPath)
    val icon = remember(goal.name, goal.sceneKey) { GoalIcon.forGoal(goal.name, goal.sceneKey) }
    val shape = RoundedCornerShape(size * 0.28f)
    if (photo != null) {
        Image(bitmap = photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(shape))
    } else {
        val tone = icon.tone()
        IconTile(icon.vector, container = tone.container, content = tone.content, size = size, shape = shape)
    }
}

internal val GoalIcon.vector: ImageVector
    get() = when (this) {
        GoalIcon.SAVINGS -> Icons.Outlined.Savings
        GoalIcon.EMERGENCY -> Icons.Outlined.HealthAndSafety
        GoalIcon.TRAVEL -> Icons.Outlined.FlightTakeoff
        GoalIcon.HOME -> Icons.Outlined.Home
        GoalIcon.CAR -> Icons.Outlined.DirectionsCar
        GoalIcon.EDUCATION -> Icons.Outlined.School
        GoalIcon.GADGET -> Icons.Outlined.Laptop
        GoalIcon.WEDDING -> Icons.Outlined.Diamond
        GoalIcon.FAMILY -> Icons.Outlined.ChildFriendly
        GoalIcon.HEALTH -> Icons.Outlined.MedicalServices
        GoalIcon.GIFT -> Icons.Outlined.CardGiftcard
        GoalIcon.INVEST -> Icons.Outlined.Insights
    }

/** Each icon on one of the wallet palette's tones, so goals are colourful without new colour tokens. */
@Composable
internal fun GoalIcon.tone(): TonePair {
    val ext = LucentoTheme.extendedColors
    return when (this) {
        GoalIcon.TRAVEL, GoalIcon.EDUCATION -> ext.bank
        GoalIcon.HOME, GoalIcon.SAVINGS, GoalIcon.INVEST -> ext.savings
        GoalIcon.CAR, GoalIcon.GIFT -> ext.cash
        GoalIcon.EMERGENCY, GoalIcon.HEALTH -> ext.credit
        GoalIcon.GADGET, GoalIcon.FAMILY -> ext.eWallet
        GoalIcon.WEDDING -> ext.loan
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun GoalDetailSheet(
    progress: GoalProgress,
    history: List<com.lucento.app.domain.GoalContribution>,
    linkedWallet: String?,
    onContribute: (Money, (GoalRepository.ContributionResult) -> Unit) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDeleteContribution: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val goal = progress.goal
    val ext = LucentoTheme.extendedColors
    val separator = remember { MoneyFormatter.decimalSeparator() }
    var amountText by remember { mutableStateOf("") }
    var withdrawing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var confirmingContributionId by remember { mutableStateOf<Long?>(null) }

    LucentoBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            SheetHeader(
                title = goal.name,
                subtitle = linkedWallet?.let { "Kept in $it" },
                leading = { GoalBadge(goal, size = 48.dp) },
                trailing = {
                    IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "Edit goal") }
                    IconButton(onClick = { confirmingDelete = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete goal", tint = MaterialTheme.colorScheme.error)
                    }
                },
            )
            // The ring and the three figures that matter.
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(
                    fraction = progress.fraction,
                    description = "${progress.percent} percent saved",
                    size = 92.dp,
                    strokeWidth = 10.dp,
                ) {
                    Text("${progress.percent}%", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                }
                Spacer(Modifier.width(Spacing.lg))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    SheetFigure("Saved", progress.saved, ext.positive)
                    SheetFigure("Target", goal.target, MaterialTheme.colorScheme.onSurface)
                    SheetFigure("Still to save", progress.remaining, MaterialTheme.colorScheme.onSurface)
                }
            }
            Spacer(Modifier.height(Spacing.md))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                goalFacts(progress, null).forEach { (icon, text) -> FactChip(icon, text) }
            }

            Spacer(Modifier.height(Spacing.lg))
            SegmentedToggle(
                options = listOf(false to "Add money", true to "Take out"),
                selected = withdrawing,
                onSelect = { withdrawing = it; error = null },
            )
            Spacer(Modifier.height(Spacing.sm))
            MoneyField(
                value = amountText,
                onValueChange = { amountText = it; error = null },
                label = if (withdrawing) "Amount to take out" else "Amount to set aside",
                currencyCode = goal.currencyCode,
                error = error,
                leadingIcon = if (withdrawing) Icons.Outlined.Remove else Icons.Outlined.Add,
                filled = true,
            )
            Spacer(Modifier.height(Spacing.sm))
            LucentoPrimaryButton(
                text = if (withdrawing) "Take out" else "Add to goal",
                onClick = {
                    val (money, amountError) = parsePositive(amountText, goal.currencyCode, separator)
                    if (money == null) {
                        error = amountError?.message(goal.currencyCode, separator)
                        return@LucentoPrimaryButton
                    }
                    onContribute(if (withdrawing) -money else money) { result ->
                        when (result) {
                            GoalRepository.ContributionResult.Saved -> {
                                amountText = ""
                                error = null
                            }
                            GoalRepository.ContributionResult.ExceedsSaved -> error = "That’s more than you’ve set aside (${MoneyFormatter.format(progress.saved)})."
                            GoalRepository.ContributionResult.AmountZero -> error = "The amount must be greater than zero."
                            GoalRepository.ContributionResult.CurrencyMismatch -> error = "This goal is in ${goal.currencyCode}."
                            GoalRepository.ContributionResult.GoalNotFound -> onDismiss()
                        }
                    }
                },
            )

            if (history.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.lg))
                IconFieldHeader(Icons.Outlined.History, "History")
                history.forEachIndexed { index, c ->
                    val into = !c.amount.isNegative
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconTile(
                            if (into) Icons.Filled.SouthWest else Icons.Filled.NorthEast,
                            container = if (into) ext.positiveContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            content = if (into) ext.success else MaterialTheme.colorScheme.onSurfaceVariant,
                            size = 32.dp,
                            shape = RoundedCornerShape(9.dp),
                        )
                        Spacer(Modifier.width(Spacing.sm + Spacing.xxs))
                        Column(Modifier.weight(1f)) {
                            Text(if (into) "Set aside" else "Taken out", style = MaterialTheme.typography.bodyMedium)
                            Text(formatDate(c.date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        AmountText(
                            c.amount,
                            showPlus = true,
                            color = if (c.amount.isNegative) MaterialTheme.colorScheme.onSurface else ext.positive,
                        )
                        IconButton(onClick = { confirmingContributionId = c.id }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Remove this entry", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (index < history.lastIndex) Hairline(inset = 32.dp + Spacing.sm + Spacing.xxs)
                }
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }

    if (confirmingDelete) {
        ConfirmDialog(
            title = "Delete “${goal.name}”?",
            text = "The goal and its history are removed. Your wallets and transactions aren’t affected.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                confirmingDelete = false
                onDelete()
            },
            onDismiss = { confirmingDelete = false },
        )
    }
    confirmingContributionId?.let { id ->
        ConfirmDialog(
            title = "Remove this entry?",
            text = "This contribution is deleted on this phone and on every device that syncs this goal.",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = {
                confirmingContributionId = null
                onDeleteContribution(id)
            },
            onDismiss = { confirmingContributionId = null },
        )
    }
}

@Composable
private fun SheetFigure(label: String, money: Money, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        MoneyText(money = money, style = AmountStyles.row.copy(fontWeight = FontWeight.SemiBold), color = color, fractionScale = 1f, minScale = 0.6f)
    }
}

/**
 * New or edited goal (owner, 2026-11-04): compact, onboarding-style fields with icons; suggestions that wrap, each with
 * its icon; "Kept in" lists only wallets money can sit in (cash, bank, e-wallet, savings) plus Add wallet; and the
 * goal's look is a square icon from the presets — or the user's own photo.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun GoalEditorSheet(
    initial: GoalDraft,
    state: PlanUiState,
    photos: GoalPhotos,
    onSave: (GoalDraft, Money, (GoalRepository.SaveResult) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    walletsViewModel: WalletsViewModel = viewModel(factory = WalletsViewModel.Factory),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val separator = remember { MoneyFormatter.decimalSeparator() }
    var draft by remember { mutableStateOf(initial) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var amountError by remember { mutableStateOf<String?>(null) }
    var photoError by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    var addingWallet by remember { mutableStateOf<WalletDraft?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val path = withContext(Dispatchers.IO) { photos.import(uri) }
                if (path != null) {
                    draft = draft.copy(photoPath = path)
                    photoError = null
                } else {
                    photoError = "That picture couldn’t be opened. Try another one."
                }
            }
        }
    }

    fun dismiss() {
        // A photo copied during this edit but never saved is removed; the goal's own photo is left alone.
        if (!saved && draft.photoPath != initial.photoPath) photos.delete(draft.photoPath)
        onDismiss()
    }

    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { dismiss() }
    }

    fun chooseIcon(icon: GoalIcon) {
        if (draft.photoPath != initial.photoPath) photos.delete(draft.photoPath)
        draft = draft.copy(sceneKey = icon.key, photoPath = null)
    }

    val shownIcon = GoalIcon.forGoal(draft.name, draft.sceneKey)
    LucentoBottomSheet(onDismissRequest = { dismiss() }, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SheetHeader(
                title = if (initial.id == null) "New savings goal" else "Edit goal",
                subtitle = if (initial.id == null) "Name it, give it a target, and Lucento keeps count." else null,
                leading = {
                    val photo = rememberGoalPhoto(draft.photoPath)
                    val shape = RoundedCornerShape(14.dp)
                    if (photo != null) {
                        Image(bitmap = photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp).clip(shape))
                    } else {
                        val tone = shownIcon.tone()
                        IconTile(shownIcon.vector, container = tone.container, content = tone.content, size = 48.dp, shape = shape)
                    }
                },
            )
            if (initial.id == null) {
                // Optional starting points: a tap fills in the name and icon only; the user still sets the amount.
                IconFieldHeader(Icons.Outlined.Lightbulb, "Suggestions")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    GoalSuggestions.all.forEach { s ->
                        ChoicePill(
                            label = s.name,
                            selected = draft.name == s.name,
                            leadingIcon = s.icon.vector,
                            onClick = {
                                chooseIcon(s.icon)
                                draft = draft.copy(name = s.name)
                                nameError = null
                            },
                        )
                    }
                }
            }
            IconFieldHeader(Icons.Outlined.EditNote, "Details", Modifier.padding(top = Spacing.xs))
            TextInput(
                value = draft.name,
                onValueChange = { draft = draft.copy(name = it); nameError = null },
                label = "Name",
                placeholder = "e.g. Emergency fund",
                error = nameError,
                maxLength = GoalRepository.MAX_NAME,
                leadingIcon = Icons.AutoMirrored.Outlined.Label,
                filled = true,
            )
            if (state.currencies.size > 1 && initial.id == null) {
                PillRow(state.currencies.map { it to it }, draft.currencyCode, onSelect = { draft = draft.copy(currencyCode = it) })
            }
            MoneyField(
                value = draft.amountText,
                onValueChange = { draft = draft.copy(amountText = it); amountError = null },
                label = "Target amount",
                currencyCode = draft.currencyCode,
                error = amountError,
                leadingIcon = Icons.Outlined.Flag,
                filled = true,
            )
            DateField(label = "Target date (optional)", date = draft.targetDate, onDate = { draft = draft.copy(targetDate = it) }, filled = true)

            // Only wallets money can sit in: cash, bank, e-wallet and savings in the goal's currency.
            val keptIn = state.wallets.filter { it.type.acceptsTransactions && !it.type.isLiability && it.currencyCode == draft.currencyCode }
            IconFieldHeader(Icons.Outlined.AccountBalanceWallet, "Kept in (optional)", Modifier.padding(top = Spacing.xs))
            FlowRow(
                Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                ChoicePill(label = "Not linked", selected = draft.walletId == null, leadingIcon = Icons.Outlined.LinkOff, onClick = { draft = draft.copy(walletId = null) })
                keptIn.forEach { w ->
                    WalletChoice(w, selected = draft.walletId == w.id, onClick = { draft = draft.copy(walletId = w.id) })
                }
                ChoicePill(
                    label = "Add wallet",
                    selected = false,
                    leadingIcon = Icons.Filled.Add,
                    role = Role.Button,
                    onClick = { addingWallet = WalletDraft(type = WalletType.SAVINGS, currencyCode = draft.currencyCode) },
                )
            }

            IconFieldHeader(Icons.Outlined.Category, "Icon", Modifier.padding(top = Spacing.xs))
            GoalIconPicker(
                selected = shownIcon,
                automatic = draft.sceneKey == null,
                photoPath = draft.photoPath,
                onSelect = ::chooseIcon,
                onAddPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            )
            photoError?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(Spacing.xs))
            LucentoPrimaryButton(
                text = if (initial.id == null) "Create goal" else "Save changes",
                onClick = {
                    if (draft.name.isBlank()) {
                        nameError = "Give this goal a name."
                        return@LucentoPrimaryButton
                    }
                    val (money, error) = parsePositive(draft.amountText, draft.currencyCode, separator)
                    if (money == null) {
                        amountError = error?.message(draft.currencyCode, separator)
                        return@LucentoPrimaryButton
                    }
                    onSave(draft, money) { result ->
                        when (result) {
                            is GoalRepository.SaveResult.Saved -> {
                                saved = true
                                if (initial.photoPath != null && initial.photoPath != draft.photoPath) photos.delete(initial.photoPath)
                                close()
                            }
                            GoalRepository.SaveResult.BlankName -> nameError = "Give this goal a name."
                            GoalRepository.SaveResult.TargetNotPositive -> amountError = "The target must be greater than zero."
                            GoalRepository.SaveResult.CurrencyLocked -> amountError = "A goal’s currency can’t change."
                            GoalRepository.SaveResult.NotFound -> close()
                        }
                    }
                },
            )
            if (initial.id != null) {
                LucentoSecondaryButton(text = "Cancel", onClick = { close() })
            }
            Spacer(Modifier.height(Spacing.sm))
        }
    }

    // Add wallet from "Kept in": a savings wallet in the goal's currency by default; once saved it is the one chosen.
    addingWallet?.let { walletDraft ->
        WalletEditorSheet(
            initial = walletDraft,
            onSave = { d, sep, done ->
                walletsViewModel.save(d, sep) { err, id ->
                    done(err, id)
                    if (err == null && id != null) draft = draft.copy(walletId = id)
                }
            },
            onDelete = null,
            onDismiss = { addingWallet = null },
        )
    }
}

/** A wallet as a selectable chip with its own tile, like the composer's. */
@Composable
private fun WalletChoice(wallet: com.lucento.app.domain.Wallet, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) colors.primaryContainer else colors.surfaceContainerLowest,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) colors.primary else LucentoTheme.extendedColors.borderDefault),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .semantics {
                role = Role.RadioButton
                this.selected = selected
            },
    ) {
        Row(Modifier.padding(start = 5.dp, end = Spacing.md, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            WalletIdentityTile(wallet, size = 24.dp)
            Spacer(Modifier.width(Spacing.xs + 2.dp))
            Text(wallet.name, style = MaterialTheme.typography.labelLarge, color = if (selected) colors.primary else colors.onSurface, maxLines = 1)
        }
    }
}

/**
 * The goal's look: the user's photo first, then a square tile per preset icon on its tone. With no choice yet the icon
 * the name points at is marked, and a line says it follows the name.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalIconPicker(
    selected: GoalIcon,
    automatic: Boolean,
    photoPath: String?,
    onSelect: (GoalIcon) -> Unit,
    onAddPhoto: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    FlowRow(
        Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        val photo = rememberGoalPhoto(photoPath)
        SquareChoice(selected = photoPath != null, label = if (photoPath != null) "Your photo" else "Add photo", onClick = onAddPhoto) {
            if (photo != null) {
                Image(bitmap = photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().background(colors.surfaceContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.AddAPhoto, contentDescription = null, tint = colors.primary)
                }
            }
        }
        GoalIcon.entries.forEach { icon ->
            val tone = icon.tone()
            SquareChoice(selected = photoPath == null && icon == selected, label = icon.label, onClick = { onSelect(icon) }) {
                Box(Modifier.fillMaxSize().background(tone.container), contentAlignment = Alignment.Center) {
                    Icon(icon.vector, contentDescription = null, tint = tone.content, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
    if (automatic && photoPath == null) {
        Text(
            "Picked from the name — tap another to change it.",
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun SquareChoice(selected: Boolean, label: String, onClick: () -> Unit, art: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(10.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(shape)
                .border(BorderStroke(if (selected) 2.5.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant), shape),
        ) { art() }
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) colors.primary else colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The user's own goal photo, decoded off the main thread; null while loading or when the file is gone. */
@Composable
internal fun rememberGoalPhoto(path: String?): ImageBitmap? {
    var bitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        bitmap = if (path == null) null else withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path)?.asImageBitmap() }
    }
    return bitmap
}
