package com.asura.finanzas.ui.goals

import com.asura.finanzas.ui.components.PrivacyToggle
import com.asura.finanzas.ui.components.PageHeader
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import com.asura.finanzas.ui.components.Lucide
import androidx.compose.ui.unit.sp
import com.asura.finanzas.R
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.ContributionPlan
import com.asura.finanzas.data.SavingsGoal
import com.asura.finanzas.data.Wallet
import com.asura.finanzas.ui.LocalAppSettings
import com.asura.finanzas.ui.components.Dot
import com.asura.finanzas.ui.components.EmptyState
import com.asura.finanzas.ui.components.ErrorBox
import com.asura.finanzas.ui.components.GlassCard
import com.asura.finanzas.ui.components.Load
import com.asura.finanzas.ui.components.LoadingBox
import com.asura.finanzas.ui.components.HairLine
import com.asura.finanzas.ui.components.MicroLabel
import com.asura.finanzas.ui.components.OfflineNotice
import com.asura.finanzas.ui.components.ReorderHandle
import com.asura.finanzas.ui.components.ReorderState
import com.asura.finanzas.ui.components.rememberReorderState
import com.asura.finanzas.ui.components.DialogAction
import com.asura.finanzas.ui.components.PrimaryButton
import com.asura.finanzas.ui.components.ProgressBar
import com.asura.finanzas.ui.components.formatBps
import com.asura.finanzas.ui.components.loadSynced
import com.asura.finanzas.ui.components.rememberReloadKey
import com.asura.finanzas.ui.formatMoney
import com.asura.finanzas.ui.maskIfHidden
import com.asura.finanzas.ui.parseHexColor
import com.asura.finanzas.ui.text
import com.asura.finanzas.ui.theme.Broke
import kotlinx.coroutines.launch

@Composable
fun GoalsScreen(
    repository: BrokeRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (key, reload) = rememberReloadKey()
    val state by loadSynced(key) { repository.savingsGoals() }
    val scope = rememberCoroutineScope()

    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SavingsGoal?>(null) }
    var contributing by remember { mutableStateOf<SavingsGoal?>(null) }
    var actionsFor by remember { mutableStateOf<SavingsGoal?>(null) }
    var confirmDelete by remember { mutableStateOf<SavingsGoal?>(null) }
    var confirmUse by remember { mutableStateOf<SavingsGoal?>(null) }
    var confirmConvert by remember { mutableStateOf<SavingsGoal?>(null) }
    // Only to name the source wallet in the confirmation; failing is harmless.
    val wallets by produceState(initialValue = emptyList<Wallet>(), key) {
        value = runCatching { repository.wallets().value }.getOrDefault(emptyList())
    }
    val walletName = { id: Long? -> wallets.firstOrNull { it.id == id }?.name }

    when (val current = state) {
        is Load.Loading -> LoadingBox(modifier)
        is Load.Failed -> ErrorBox(current.message, reload, modifier)
        is Load.Ready -> GoalList(
            goals = current.data,
            fromCache = current.fromCache,
            onBack = onBack,
            onNew = { creating = true },
            onLongPress = { actionsFor = it },
            onEdit = { editing = it },
            onDelete = { confirmDelete = it },
            walletName = walletName,
            onContribute = { contributing = it },
            onUse = { if (it.goalKind == "fund") confirmConvert = it else confirmUse = it },
            onAdjustDate = { editing = it },
            onReorder = { ids ->
                scope.launch { runCatching { repository.reorderSavingsGoals(ids) } }
            },
            modifier = modifier,
        )
    }

    if (creating || editing != null) {
        GoalFormSheet(
            repository = repository,
            existing = editing,
            onDismiss = { creating = false; editing = null },
            onSaved = { creating = false; editing = null; reload() },
        )
    }

    contributing?.let { goal ->
        ContributeSheet(
            repository = repository,
            goal = goal,
            onDismiss = { contributing = null },
            onSaved = { contributing = null; reload() },
        )
    }

    actionsFor?.let { target ->
        AlertDialog(
            onDismissRequest = { actionsFor = null },
            containerColor = Broke.colors.surfaceOverlay,
            title = { Text(target.name, color = Broke.colors.fg) },
            text = {
                Column {
                    DialogAction(stringResource(R.string.goals_contribute)) {
                        actionsFor = null
                        contributing = target
                    }
                    // With money set aside, a fund graduates into its own
                    // wallet and a purchase gets spent — same split as the web.
                    if (target.savedCents > 0) {
                        if (target.goalKind == "fund") {
                            DialogAction(stringResource(R.string.goals_convert_to_wallet)) {
                                actionsFor = null
                                confirmConvert = target
                            }
                        } else {
                            DialogAction(stringResource(R.string.goals_buy)) {
                                actionsFor = null
                                confirmUse = target
                            }
                        }
                    }
                    DialogAction(stringResource(R.string.common_edit)) {
                        actionsFor = null
                        editing = target
                    }
                    DialogAction(stringResource(R.string.common_delete), Broke.colors.danger) {
                        actionsFor = null
                        confirmDelete = target
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { actionsFor = null }) {
                    Text(stringResource(R.string.common_close), color = Broke.colors.fgMuted)
                }
            },
        )
    }

    confirmUse?.let { target ->
        val hide = LocalAppSettings.current.hideBalances
        AlertDialog(
            onDismissRequest = { confirmUse = null },
            containerColor = Broke.colors.surfaceOverlay,
            title = { Text(stringResource(R.string.goals_buy)) },
            text = {
                Text(
                    if (target.linkedWalletId != null) {
                        text(
                            R.string.goals_use_confirm_apartado,
                            "amount" to maskIfHidden(
                                formatMoney(target.savedCents, target.currencyCode),
                                hide,
                            ),
                            "wallet" to walletName(target.linkedWalletId).orEmpty(),
                        )
                    } else {
                        stringResource(R.string.goals_use_confirm_track)
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmUse = null
                    scope.launch {
                        runCatching { repository.useGoal(target.id) }
                        reload()
                    }
                }) { Text(stringResource(R.string.goals_buy), color = Broke.colors.accent) }
            },
            dismissButton = {
                TextButton(onClick = { confirmUse = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    confirmConvert?.let { target ->
        val hide = LocalAppSettings.current.hideBalances
        AlertDialog(
            onDismissRequest = { confirmConvert = null },
            containerColor = Broke.colors.surfaceOverlay,
            title = { Text(stringResource(R.string.goals_convert_to_wallet)) },
            text = {
                Text(
                    text(
                        R.string.goals_convert_moves,
                        "amount" to maskIfHidden(
                            formatMoney(target.savedCents, target.currencyCode),
                            hide,
                        ),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmConvert = null
                    scope.launch {
                        // No style overrides: the new wallet keeps the goal's
                        // own name and colour, which is the web's default too.
                        runCatching {
                            repository.convertGoalToWallet(target.id, null, target.color, null)
                        }
                        reload()
                    }
                }) {
                    Text(
                        stringResource(R.string.goals_convert_to_wallet),
                        color = Broke.colors.accent,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmConvert = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    confirmDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = Broke.colors.surfaceOverlay,
            title = { Text(target.name) },
            text = { Text(stringResource(R.string.goals_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    scope.launch {
                        runCatching { repository.deleteGoal(target.id) }
                        reload()
                    }
                }) { Text(stringResource(R.string.common_delete), color = Broke.colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun GoalList(
    goals: List<SavingsGoal>,
    fromCache: Boolean,
    onBack: () -> Unit,
    onNew: () -> Unit,
    onLongPress: (SavingsGoal) -> Unit,
    walletName: (Long?) -> String?,
    onContribute: (SavingsGoal) -> Unit,
    onUse: (SavingsGoal) -> Unit,
    onAdjustDate: (SavingsGoal) -> Unit,
    onEdit: (SavingsGoal) -> Unit,
    onDelete: (SavingsGoal) -> Unit,
    onReorder: (List<Long>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hide = LocalAppSettings.current.hideBalances

    // Order matters on the dashboard: the first goal renders as the ring, the
    // rest as bars — so dragging here changes what the summary highlights.
    val ordered = remember(goals) {
        mutableStateListOf<SavingsGoal>().apply { addAll(goals) }
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val firstRow = 1 + (if (fromCache) 1 else 0) + (if (goals.isEmpty()) 1 else 0)
    val reorderState = rememberReorderState(
        listState = listState,
        scope = scope,
        range = { firstRow until firstRow + ordered.size },
        onMove = { from, to -> ordered.add(to, ordered.removeAt(from)) },
        onDrop = { onReorder(ordered.map { it.id }) },
    )

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            BackHandler(onBack = onBack)
            PageHeader(stringResource(R.string.goals_title)) {
                PrivacyToggle()
                PrimaryButton(
                    text = stringResource(R.string.goals_new_goal),
                    onClick = onNew,
                    leadingIcon = Lucide.Plus,
                )
            }
            if (goals.size > 1) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.goals_reorder_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = Broke.colors.fgSubtle,
                )
            }
        }

        if (fromCache) {
            item { OfflineNotice(stringResource(R.string.offline_banner), Modifier.fillMaxWidth()) }
        }

        if (goals.isEmpty()) {
            item {
                EmptyState(
                    stringResource(R.string.goals_empty_title),
                    stringResource(R.string.goals_empty_description),
                )
            }
        }

        itemsIndexed(ordered, key = { _, it -> it.id }) { _, goal ->
            GoalCard(
                goal = goal,
                hide = hide,
                onLongPress = onLongPress,
                walletName = walletName,
                onContribute = onContribute,
                onUse = onUse,
                onAdjustDate = onAdjustDate,
                onEdit = onEdit,
                onDelete = onDelete,
                reorderState = reorderState,
            )
        }

    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GoalCard(
    goal: SavingsGoal,
    hide: Boolean,
    onLongPress: (SavingsGoal) -> Unit,
    walletName: (Long?) -> String?,
    onContribute: (SavingsGoal) -> Unit,
    onUse: (SavingsGoal) -> Unit,
    onAdjustDate: (SavingsGoal) -> Unit,
    onEdit: (SavingsGoal) -> Unit,
    onDelete: (SavingsGoal) -> Unit,
    /** Null on the wallet detail, where the cards are not reorderable. */
    reorderState: ReorderState? = null,
) {
    val colors = Broke.colors
    val dragging = reorderState?.draggingKey == goal.id
    GlassCard(
        Modifier
            .fillMaxWidth()
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer { translationY = if (dragging) reorderState?.offsetY ?: 0f else 0f }
            .combinedClickable(onClick = {}, onLongClick = { onLongPress(goal) }),
    ) {
        val tint = parseHexColor(goal.color) ?: colors.accent
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            // Grip first, then the badge — the order the web lays out.
            reorderState?.let { ReorderHandle(state = it, key = goal.id) }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tint.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Lucide.PiggyBank,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(17.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            // Neither the deadline nor the percentage repeats here: the plan
            // sentence already carries the date, and the progress line below
            // carries the percentage — the same split the web makes.
            Column(Modifier.weight(1f)) {
                Text(
                    goal.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.fg,
                )
                Text(
                    walletName(goal.linkedWalletId)
                        ?.let { "${stringResource(R.string.goals_apartado_in)} $it" }
                        ?: stringResource(R.string.goals_track_only),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.fgSubtle,
                )
            }
            Spacer(Modifier.width(8.dp))
            // The web offers edit and delete on the card itself.
            Icon(
                Lucide.Pencil,
                contentDescription = stringResource(R.string.common_edit),
                tint = colors.fgSubtle,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onEdit(goal) }
                    .padding(6.dp)
                    .size(15.dp),
            )
            Icon(
                Lucide.Trash,
                contentDescription = stringResource(R.string.common_delete),
                tint = colors.fgSubtle,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onDelete(goal) }
                    .padding(6.dp)
                    .size(15.dp),
            )
        }

        // Figure first, bar under it — the web's order; and the bar carries the
        // goal's own colour rather than the app accent.
        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                maskIfHidden(formatMoney(goal.savedCents, goal.currencyCode), hide),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 24.sp,
                    lineHeight = 28.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                ),
                color = colors.fg,
            )
            Text(
                " " + stringResource(R.string.goals_of) + " " +
                    maskIfHidden(formatMoney(goal.targetCents, goal.currencyCode), hide),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = colors.fgSubtle,
                modifier = Modifier.padding(start = 6.dp, bottom = 2.dp),
            )
        }

        Spacer(Modifier.height(12.dp))
        ProgressBar(goal.progressBps, color = tint)
        Spacer(Modifier.height(12.dp))
        val remaining = (goal.targetCents - goal.savedCents).coerceAtLeast(0)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (remaining == 0L) {
                    stringResource(R.string.goals_completed)
                } else {
                    "${Math.round(goal.progressBps / 100.0)}% · " +
                        stringResource(R.string.goals_remaining) + " " +
                        maskIfHidden(formatMoney(remaining, goal.currencyCode), hide)
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (remaining == 0L) colors.positive else colors.fgMuted,
                modifier = Modifier.weight(1f),
            )
            // Both of these are ghost Buttons on the web — plain foreground on
            // a rounded hit area, not accent links.
            Text(
                stringResource(R.string.goals_contribute),
                style = MaterialTheme.typography.labelLarge,
                color = colors.fg,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onContribute(goal) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            // Spending a purchase goal and graduating a fund are the same slot
            // on the web; which one shows depends on the goal's kind.
            if (goal.savedCents > 0) {
                Spacer(Modifier.width(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onUse(goal) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Icon(
                        if (goal.goalKind == "fund") Lucide.Wallet else Lucide.Check,
                        contentDescription = null,
                        tint = colors.fg,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(
                            if (goal.goalKind == "fund") R.string.goals_convert_to_wallet
                            else R.string.goals_buy,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.fg,
                    )
                }
            }
        }

        // The deadline plan, only once the goal actually has one and is unmet.
        val plan = goal.plan
        if (plan != null && goal.progressBps < 10_000) {
            Spacer(Modifier.height(12.dp))
            HairLine()
            Spacer(Modifier.height(10.dp))
            GoalPlanLines(goal, plan, hide)
            // Only offered when the plan is off track — the same condition the
            // web uses, so it does not nag an on-pace goal.
            if (plan.overdue || goal.isBehind) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.goals_adjust_date),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.accent,
                    modifier = Modifier.clickable { onAdjustDate(goal) },
                )
            }
        }
    }
}

/** Short, locale-aware date like "30 nov 2026" for the plan line. */
@Composable
private fun planDate(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    val locale = java.util.Locale.forLanguageTag(LocalAppSettings.current.locale)
    val date = runCatching { java.time.LocalDate.parse(iso) }.getOrNull() ?: return iso
    // The locale decides the order ("Dec 31, 2026" vs "31 dic 2026"); hand-
    // assembling it always produced the Spanish order, even in English.
    val formatter = java.time.format.DateTimeFormatter
        .ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
        .withLocale(locale)
    return runCatching { date.format(formatter) }.getOrDefault(iso)
}

/** Cadence adverb ("al mes") for the reserve sentence. */
@Composable
private fun cadenceAdverb(cadence: String?): String = stringResource(
    when (cadence) {
        "daily" -> R.string.goals_cadence_adv_daily
        "weekly" -> R.string.goals_cadence_adv_weekly
        "yearly" -> R.string.goals_cadence_adv_yearly
        else -> R.string.goals_cadence_adv_monthly
    },
)

/** Period noun ("Este mes") for the progress sentence. */
@Composable
private fun periodNoun(cadence: String?): String = stringResource(
    when (cadence) {
        "daily" -> R.string.goals_period_daily
        "weekly" -> R.string.goals_period_weekly
        "yearly" -> R.string.goals_period_yearly
        else -> R.string.goals_period_monthly
    },
)

/**
 * Which sentence the card shows, in the same order the web decides it: overdue
 * beats everything; otherwise nothing-yet, part-way, or covered. The quota is
 * frozen at the period start, so part-way reads "2,000 of 2,400" rather than a
 * re-spread plan.
 */
@Composable
private fun GoalPlanLines(goal: SavingsGoal, plan: ContributionPlan, hide: Boolean) {
    val colors = Broke.colors
    val remaining = (goal.targetCents - goal.savedCents).coerceAtLeast(0)
    fun money(cents: Long) = maskIfHidden(formatMoney(cents, goal.currencyCode), hide)

    if (plan.overdue) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Badge(stringResource(R.string.goals_overdue_badge), colors.danger)
            Spacer(Modifier.width(8.dp))
            Text(
                text(R.string.goals_overdue_hint, "amount" to money(remaining)),
                style = MaterialTheme.typography.labelSmall,
                color = colors.fgSubtle,
            )
        }
    } else {
        val line = when {
            plan.contributedThisPeriodCents <= 0 -> text(
                R.string.goals_plan_reserve,
                "amount" to money(plan.periodQuotaCents),
                "cadence" to cadenceAdverb(goal.cadence),
                "date" to planDate(goal.targetDate),
            )
            plan.periodMissingCents > 0 -> text(
                R.string.goals_plan_progress,
                "period" to periodNoun(goal.cadence),
                "done" to money(plan.contributedThisPeriodCents),
                "quota" to money(plan.periodQuotaCents),
                "missing" to money(plan.periodMissingCents),
            )
            else -> text(
                R.string.goals_plan_covered,
                "period" to periodNoun(goal.cadence),
                "date" to planDate(goal.targetDate),
            )
        }
        Text(line, style = MaterialTheme.typography.labelSmall, color = colors.fgMuted)

        if (goal.isBehind) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Badge(stringResource(R.string.goals_behind_badge), colors.warning)
                Spacer(Modifier.width(8.dp))
                Text(
                    text(R.string.goals_behind_hint, "amount" to money(plan.behindCents)),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.fgSubtle,
                )
            }
        }
    }
}

/** The small status pill the web puts before a behind/overdue explanation. */
@Composable
private fun Badge(label: String, tint: Color) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = tint,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(tint.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
