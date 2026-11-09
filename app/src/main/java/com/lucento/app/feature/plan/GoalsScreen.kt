package com.lucento.app.feature.plan

import com.lucento.app.core.designsystem.component.WalletChoicePill
import com.lucento.app.core.designsystem.component.AmountsShown
import com.lucento.app.core.designsystem.component.AmountFormat
import com.lucento.app.core.designsystem.component.amountFormat
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
import androidx.compose.runtime.saveable.rememberSaveable
import com.lucento.app.core.designsystem.component.rememberHaptics
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.ui.semantics.contentDescription

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
internal fun NewGoalSheet(
    currencyCode: String,
    onDismiss: () -> Unit,
    viewModel: PlanViewModel = viewModel(factory = PlanViewModel.Factory),
    /** A starting name (the guided setup's suggestions); the person still sets the amount. */
    initialName: String = "",
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current.applicationContext
    val photos = remember(context) { GoalPhotos(context) }
    GoalEditorSheet(
        initial = GoalDraft(currencyCode = currencyCode, name = initialName),
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
 *
 * A reached goal can be archived (owner, 2026-11-04): it leaves the list and every total but keeps its history, and an
 * Active / Archived toggle at the top, shown once something is archived, lists them, each restorable.
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
    val archived = remember(state.archivedGoals, state.contributions, state.today) {
        state.archivedGoals.map { GoalCalculator.progress(it, state.contributions, state.today) }
    }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    // Restoring the last archived goal leaves nothing to show there: fall back to the active list.
    val viewingArchived = showArchived && archived.isNotEmpty()
    val haptics = rememberHaptics()
    // Lucento Shared: a Partner sees every goal and its progress; the Shared Space's Owner manages them.
    val canEdit = com.lucento.app.feature.sharedspace.LocalSharedAccess.current.canManageStructure
    val archive: (Long) -> Unit = { id ->
        viewModel.archiveGoal(id) { result -> if (result == GoalRepository.ArchiveResult.Archived) haptics.confirm() }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.fabClearance),
        ) {
            item(key = "bar") {
                LucentoTopBar(title = "Goals", onBack = onBack, actions = {
                    if (canEdit && (state.goals.isNotEmpty() || archived.isNotEmpty())) {
                        com.lucento.app.core.designsystem.component.AddPillButton(
                            "Add",
                            onClick = { editing = GoalDraft(currencyCode = state.primaryCurrency) },
                            contentDescription = "Add goal",
                        )
                    }
                })
            }
            if (archived.isNotEmpty()) {
                item(key = "toggle") {
                    SegmentedToggle(
                        options = listOf(false to "Active", true to "Archived · ${archived.size}"),
                        selected = viewingArchived,
                        onSelect = { showArchived = it },
                        modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.sm),
                    )
                }
            }
            if (!state.loading && state.goals.isEmpty() && !viewingArchived) {
                item(key = "empty") {
                    EmptyState(
                        title = if (archived.isEmpty()) "No goals yet" else "Every goal reached",
                        body = if (archived.isEmpty()) {
                            "An emergency fund, a trip, a new laptop — set a target and Lucento tracks progress and when you’ll get there."
                        } else {
                            "Your finished goals are under Archived. Set a new target whenever you’re ready."
                        },
                        illustration = { EmptyIllustration(LucentoAnimation.EmptyGoals) },
                        action = if (canEdit) ({ LucentoPrimaryButton(text = "Create a goal", onClick = { editing = GoalDraft(currencyCode = state.primaryCurrency) }) }) else null,
                    )
                }
            }
            if (viewingArchived) {
                item(key = "archived-head") {
                    ToneSectionHeader(
                        "Archived",
                        StatusTones.neutral,
                        caption = if (archived.size == 1) "1 goal · history kept" else "${archived.size} goals · history kept",
                    )
                }
                items(archived, key = { "goal-${it.goal.id}" }) { p ->
                    GoalCard(
                        p,
                        walletName = p.goal.linkedWalletId?.let { state.walletsById[it]?.name },
                        onClick = { openGoalId = p.goal.id },
                        archived = true,
                        modifier = Modifier.animateItem().padding(bottom = Spacing.sm),
                    )
                }
                item(key = "archived-note") {
                    InfoNote(
                        "Archived goals keep their history but aren’t counted in your goal totals or on Home. Open one to restore it.",
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
            }
            if (progress.isNotEmpty() && !viewingArchived) {
                item(key = "hero") { GoalsHero(progress, state.primaryCurrency) }
            }
            if (active.isNotEmpty() && !viewingArchived) {
                item(key = "active-head") {
                    ToneSectionHeader("In progress", StatusTones.brand, caption = if (active.size == 1) "1 goal" else "${active.size} goals")
                }
                items(active, key = { "goal-${it.goal.id}" }) { p ->
                    GoalCard(
                        p,
                        walletName = p.goal.linkedWalletId?.let { state.walletsById[it]?.name },
                        onClick = { openGoalId = p.goal.id },
                        modifier = Modifier.animateItem().padding(bottom = Spacing.sm),
                    )
                }
            }
            if (reached.isNotEmpty() && !viewingArchived) {
                item(key = "reached-head") {
                    ToneSectionHeader("Reached", StatusTones.positive, caption = if (reached.size == 1) "1 goal · well done" else "${reached.size} goals · well done")
                }
                items(reached, key = { "goal-${it.goal.id}" }) { p ->
                    GoalCard(
                        p,
                        walletName = p.goal.linkedWalletId?.let { state.walletsById[it]?.name },
                        onClick = { openGoalId = p.goal.id },
                        onArchive = if (canEdit) ({ archive(p.goal.id) }) else null,
                        modifier = Modifier.animateItem().padding(bottom = Spacing.sm),
                    )
                }
            }
            if (!canEdit) {
                item(key = "owner-manages") {
                    com.lucento.app.feature.sharedspace.OwnerManagesNote("goals", Modifier.padding(top = Spacing.sm))
                }
            }
            if (state.goals.isNotEmpty() && !viewingArchived) {
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

    val open = progress.firstOrNull { it.goal.id == openGoalId } ?: archived.firstOrNull { it.goal.id == openGoalId }
    var restoreLimit by remember(openGoalId) { mutableStateOf<GoalRepository.RestoreResult.LimitReached?>(null) }
    if (open != null) {
        GoalDetailSheet(
            progress = open,
            history = state.contributions.filter { it.goalId == open.goal.id },
            linkedWallet = open.goal.linkedWalletId?.let { state.walletsById[it]?.name },
            archived = archived.any { it.goal.id == open.goal.id },
            onArchive = {
                archive(open.goal.id)
                openGoalId = null
            },
            onRestore = {
                viewModel.restoreGoal(open.goal.id) { result ->
                    if (result is GoalRepository.RestoreResult.LimitReached) {
                        // Lucento Pro: the goal stays archived, history intact; the sheet says why and how.
                        haptics.reject()
                        restoreLimit = result
                    } else {
                        haptics.confirm()
                        restoreLimit = null
                        openGoalId = null
                        showArchived = false
                    }
                }
            },
            restoreNote = restoreLimit?.let {
                com.lucento.app.feature.pro.ProCopy.limitReachedLine(com.lucento.app.domain.entitlement.ProFeature.SAVINGS_GOALS, it.used, it.limit)
            },
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
    val fmt = amountFormat()
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
                MoneyText(money = saved, style = AmountStyles.hero, color = ext.onHero, fractionScale = 1f, minScale = 0.5f)
                Text(
                    "of ${fmt.format(target)} · ${mine.size} ${if (mine.size == 1) "goal" else "goals"}",
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
            HeroStatTile("Still to save", Icons.Outlined.Flag, "Still to save ${fmt.format(left)}", Modifier.weight(1f)) {
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
 * A reached goal's card offers Archive ([onArchive]); an [archived] one says so.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalCard(
    p: GoalProgress,
    walletName: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    archived: Boolean = false,
    onArchive: (() -> Unit)? = null,
) {
    val fmt = amountFormat()
    val ext = LucentoTheme.extendedColors
    LucentoCard(modifier.fillMaxWidth(), onClick = onClick, contentPadding = Spacing.md) {
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
                    "${fmt.format(p.saved)} of ${fmt.format(p.goal.target)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            if (archived) {
                StatusPill("Archived", StatusTones.neutral)
            } else if (p.reached) {
                StatusPill("Reached", StatusTones.positive)
            } else {
                Text("${p.percent}%", style = AmountStyles.row.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        ShareBar(fraction = p.fraction, color = if (archived) MaterialTheme.colorScheme.outline else ext.success, height = 8.dp)
        val facts = goalFacts(p, walletName, amountFormat())
        if (facts.isNotEmpty() || onArchive != null) {
            Spacer(Modifier.height(Spacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FlowRow(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    facts.forEach { (icon, text) -> FactChip(icon, text) }
                }
                if (onArchive != null) {
                    Spacer(Modifier.width(Spacing.xs))
                    ArchiveChip(goalName = p.goal.name, onClick = onArchive)
                }
            }
        }
    }
}

/** A reached goal's one-tap archive: a quiet pill, at least 48dp tall to touch. */
@Composable
private fun ArchiveChip(goalName: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.minimumInteractiveComponentSize(),
    ) {
        Row(Modifier.padding(horizontal = Spacing.sm + Spacing.xxs, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(Spacing.xxs + 2.dp))
            Text(
                "Archive",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.semantics { contentDescription = "Archive $goalName" },
            )
        }
    }
}

/** What a goal's card and sheet say about getting there, each with its icon. */
private fun goalFacts(p: GoalProgress, walletName: String?, fmt: AmountFormat): List<Pair<ImageVector, String>> = buildList {
    if (p.reached) {
        add(Icons.Outlined.CheckCircle to "Target reached")
    } else {
        p.goal.targetDate?.let { add(Icons.Outlined.CalendarMonth to "By ${formatDate(it)}") }
        p.requiredPerMonth?.let { add(Icons.Outlined.Payments to "${fmt.format(it)} a month") }
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
    /** Archived goals are read-only here: no money in or out and no edits; Restore brings them back. */
    archived: Boolean,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onContribute: (Money, (GoalRepository.ContributionResult) -> Unit) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDeleteContribution: (Long) -> Unit,
    onDismiss: () -> Unit,
    /** Lucento Pro: why Restore didn't bring the goal back (the plan's active goals are full). */
    restoreNote: String? = null,
) {
    val goal = progress.goal
    val ext = LucentoTheme.extendedColors
    var confirmingDelete by remember { mutableStateOf(false) }
    var confirmingContributionId by remember { mutableStateOf<Long?>(null) }
    // Lucento Shared: read-only on a Partner's phone (the Owner manages goals).
    val canEdit = com.lucento.app.feature.sharedspace.LocalSharedAccess.current.canManageStructure

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
                    if (canEdit) {
                        if (!archived) IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "Edit goal") }
                        IconButton(onClick = { confirmingDelete = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete goal", tint = MaterialTheme.colorScheme.error)
                        }
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
                goalFacts(progress, null, amountFormat()).forEach { (icon, text) -> FactChip(icon, text) }
            }

            if (!canEdit) {
                Spacer(Modifier.height(Spacing.lg))
                com.lucento.app.feature.sharedspace.OwnerManagesNote("goals")
            } else if (archived) {
                Spacer(Modifier.height(Spacing.lg))
                InfoNote(
                    "Archived — it isn’t counted in your goal totals or on Home. Restore it to add or take out money.",
                    icon = Icons.Outlined.Archive,
                )
                Spacer(Modifier.height(Spacing.sm))
                LucentoPrimaryButton(text = "Restore goal", onClick = onRestore)
                restoreNote?.let { note ->
                    Spacer(Modifier.height(Spacing.sm))
                    if (com.lucento.app.feature.pro.ProCopy.upgradeHelps(com.lucento.app.domain.entitlement.ProFeature.SAVINGS_GOALS,
                            com.lucento.app.domain.entitlement.PlanCatalog.limit(com.lucento.app.domain.entitlement.ProFeature.SAVINGS_GOALS,
                                com.lucento.app.feature.pro.LocalEntitlements.current, System.currentTimeMillis()))
                    ) {
                        com.lucento.app.feature.pro.ProNote(note, from = com.lucento.app.domain.entitlement.ProFeature.SAVINGS_GOALS)
                    } else {
                        InfoNote(note)
                    }
                }
            } else {
                if (progress.reached) {
                    Spacer(Modifier.height(Spacing.md))
                    InfoNote(
                        "You reached this goal. Archive it to keep your list focused — its history stays, and you can restore it any time.",
                        icon = Icons.Outlined.EmojiEvents,
                        container = StatusTones.positive.container,
                        content = StatusTones.positive.content,
                        actionLabel = "Archive goal",
                        onAction = onArchive,
                    )
                }
                ContributionForm(progress, onContribute, onDismiss)
            }

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
                        if (!archived && canEdit) {
                            IconButton(onClick = { confirmingContributionId = c.id }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Remove this entry", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
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

/** Add money to a goal or take some out, through MoneyParser; the goal's currency only. */
@Composable
private fun ContributionForm(
    progress: GoalProgress,
    onContribute: (Money, (GoalRepository.ContributionResult) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    val fmt = amountFormat()
    val goal = progress.goal
    val separator = remember { MoneyFormatter.decimalSeparator() }
    var amountText by remember { mutableStateOf("") }
    var withdrawing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column {
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
                        GoalRepository.ContributionResult.ExceedsSaved -> error = "That’s more than you’ve set aside (${fmt.format(progress.saved)})."
                        GoalRepository.ContributionResult.AmountZero -> error = "The amount must be greater than zero."
                        GoalRepository.ContributionResult.CurrencyMismatch -> error = "This goal is in ${goal.currencyCode}."
                        GoalRepository.ContributionResult.GoalNotFound -> onDismiss()
                        GoalRepository.ContributionResult.NotPermitted -> error = "Only the Shared Space’s owner can change this."
                    }
                }
            },
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
) = AmountsShown {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val separator = remember { MoneyFormatter.decimalSeparator() }
    var draft by remember { mutableStateOf(initial) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var planLimit by remember { mutableStateOf<GoalRepository.SaveResult.LimitReached?>(null) }
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
                    WalletChoicePill(w, selected = draft.walletId == w.id, onClick = { draft = draft.copy(walletId = w.id) })
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
            planLimit?.let { limit ->
                val note = com.lucento.app.feature.pro.ProCopy.limitReachedLine(
                    com.lucento.app.domain.entitlement.ProFeature.SAVINGS_GOALS, limit.used, limit.limit,
                )
                Spacer(Modifier.height(Spacing.sm))
                if (com.lucento.app.feature.pro.ProCopy.upgradeHelps(com.lucento.app.domain.entitlement.ProFeature.SAVINGS_GOALS, limit.limit)) {
                    val openPro = com.lucento.app.feature.pro.LocalOpenPro.current
                    com.lucento.app.feature.pro.ProNote(
                        note,
                        from = com.lucento.app.domain.entitlement.ProFeature.SAVINGS_GOALS,
                        open = { feature -> close(); openPro(feature) },
                    )
                } else {
                    InfoNote(note)
                }
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
                            GoalRepository.SaveResult.NotPermitted -> nameError = "Only the Shared Space’s owner can change this."
                            // Lucento Pro: not a mistake in the form; said by the button, with the way out.
                            is GoalRepository.SaveResult.LimitReached -> planLimit = result
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
