package com.asura.finanzas.ui.goals

import com.asura.finanzas.ui.components.ConfirmDialog
import com.asura.finanzas.ui.wallets.WalletFormSheet
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.appendInlineContent
import com.asura.finanzas.ui.components.GhostButton
import com.asura.finanzas.ui.components.PrivacyToggle
import com.asura.finanzas.ui.components.PageHeader
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
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
import com.asura.finanzas.ui.components.EmptyState
import com.asura.finanzas.ui.components.ErrorBox
import com.asura.finanzas.ui.components.GlassCard
import com.asura.finanzas.ui.components.Load
import com.asura.finanzas.ui.components.LoadingBox
import com.asura.finanzas.ui.components.HairLine
import com.asura.finanzas.ui.components.ReorderHandle
import com.asura.finanzas.ui.components.ReorderState
import com.asura.finanzas.ui.components.rememberReorderState
import com.asura.finanzas.ui.components.PrimaryButton
import com.asura.finanzas.ui.components.ProgressBar
import com.asura.finanzas.ui.components.loadSynced
import com.asura.finanzas.ui.components.rememberReloadKey
import com.asura.finanzas.ui.formatMoney
import com.asura.finanzas.ui.maskIfHidden
import com.asura.finanzas.ui.parseHexColor
import com.asura.finanzas.ui.text
import com.asura.finanzas.ui.theme.Broke
import com.asura.finanzas.ui.theme.tabular
import kotlinx.coroutines.launch

@Composable
fun GoalsScreen(
    repository: BrokeRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (key, reload) = rememberReloadKey()
    val state by loadSynced("goals", refetch = key) { repository.savingsGoals() }
    val scope = rememberCoroutineScope()

    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SavingsGoal?>(null) }
    var contributing by remember { mutableStateOf<SavingsGoal?>(null) }
    var confirmDelete by remember { mutableStateOf<SavingsGoal?>(null) }
    var confirmUse by remember { mutableStateOf<SavingsGoal?>(null) }
    var confirmConvert by remember { mutableStateOf<SavingsGoal?>(null) }
    // Only to name the source wallet in the confirmation; failing is harmless.
    val walletsState by loadSynced("wallets" to false, refetch = key) { repository.wallets() }
    val wallets = (walletsState as? Load.Ready)?.data ?: emptyList()
    val walletName = { id: Long? -> wallets.firstOrNull { it.id == id }?.name }

    when (val current = state) {
        is Load.Loading -> LoadingBox(modifier)
        is Load.Failed -> ErrorBox(current.message, reload, modifier)
        is Load.Ready -> GoalList(
            goals = current.data,
            fromCache = current.fromCache,
            onBack = onBack,
            onNew = { creating = true },
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


    confirmUse?.let { target ->
        val hide = LocalAppSettings.current.hideBalances
        ConfirmDialog(
            title = stringResource(R.string.goals_buy),
            message = if (target.linkedWalletId != null) {
                text(
                    if (target.tracksWallet) R.string.goals_use_confirm_wallet
                    else R.string.goals_use_confirm_apartado,
                    "amount" to maskIfHidden(
                        formatMoney(target.savedCents, target.currencyCode),
                        hide,
                    ),
                    "wallet" to walletName(target.linkedWalletId).orEmpty(),
                )
            } else {
                stringResource(R.string.goals_use_confirm_track)
            },
            confirmLabel = stringResource(R.string.goals_buy),
            onConfirm = {
                confirmUse = null
                scope.launch {
                    runCatching { repository.useGoal(target.id) }
                    reload()
                }
            },
            onDismiss = { confirmUse = null },
        )
    }

    // Graduating a fund opens the wallet form in its convert mode, as on the
    // web: name, design, category, parent and notes, prefilled from the goal.
    confirmConvert?.let { target ->
        WalletFormSheet(
            repository = repository,
            existing = null,
            wallets = wallets,
            onDismiss = { confirmConvert = null },
            onSaved = { confirmConvert = null; reload() },
            convert = goalConvert(target, wallets),
        )
    }

    confirmDelete?.let { target ->
        ConfirmDialog(
            // "Delete", the way the web titles it — not the goal's own name.
            title = stringResource(R.string.common_delete),
            message = stringResource(R.string.goals_delete_confirm),
            onConfirm = {
                confirmDelete = null
                scope.launch {
                    runCatching { repository.deleteGoal(target.id) }
                    reload()
                }
            },
            onDismiss = { confirmDelete = null },
        )
    }
}

@Composable
private fun GoalList(
    goals: List<SavingsGoal>,
    fromCache: Boolean,
    onBack: () -> Unit,
    onNew: () -> Unit,
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
    // header + offline banner? + empty state? + the reorder hint, which is
    // always drawn.
    val firstRow = 2 + (if (fromCache) 1 else 0) + (if (goals.isEmpty()) 1 else 0)
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 28.dp),
    ) {
        item {
            BackHandler(onBack = onBack)
            // The header's `mb-7`, the hint's `mb-3`, the cards' `gap-4`.
            PageHeader(stringResource(R.string.goals_title), Modifier.padding(bottom = 28.dp)) {
                PrivacyToggle()
                PrimaryButton(
                    text = stringResource(R.string.goals_new_goal),
                    onClick = onNew,
                    leadingIcon = Lucide.Plus,
                )
            }
        }


        if (goals.isEmpty()) {
            item {
                EmptyState(
                    Lucide.PiggyBank,
                    stringResource(R.string.goals_empty_title),
                    stringResource(R.string.goals_empty_description),
                )
            }
        }

        // Always, and under the empty state — that is where the web prints it,
        // and hiding it below two goals meant the line simply was not there on
        // a screen where the web showed it.
        item {
            Text(
                stringResource(R.string.goals_reorder_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = Broke.colors.fgSubtle,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        itemsIndexed(ordered, key = { _, it -> it.id }) { index, goal ->
            if (index > 0) Spacer(Modifier.height(16.dp))
            GoalCard(
                goal = goal,
                hide = hide,
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
            ,
    ) {
        val tint = parseHexColor(goal.color) ?: colors.accent
        // `mb-4 flex items-center gap-2`: grip, badge, name over its wallet,
        // then edit and delete (`gap-1`, `p-1.5`) — shown on a touch screen.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        ) {
            reorderState?.let { ReorderHandle(state = it, key = goal.id) }
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tint.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Lucide.PiggyBank, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    goal.name,
                    // `font-display text-lg font-medium`, no tracking.
                    style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 0.sp),
                    color = colors.fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    walletName(goal.linkedWalletId)
                        ?.let {
                            if (goal.tracksWallet) text(R.string.goals_whole_wallet_of, "wallet" to it)
                            else "${stringResource(R.string.goals_apartado_in)} $it"
                        }
                        ?: stringResource(R.string.goals_track_only),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.fgSubtle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
        }

        // `font-display text-2xl font-semibold`, and its target in a `text-sm`
        // span (still the display face) `ml-1.5` along the same baseline.
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                maskIfHidden(formatMoney(goal.savedCents, goal.currencyCode), hide),
                style = MaterialTheme.typography.headlineMedium.tabular(),
                color = colors.fg,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                stringResource(R.string.goals_of) + " " +
                    maskIfHidden(formatMoney(goal.targetCents, goal.currencyCode), hide),
                style = MaterialTheme.typography.headlineMedium.tabular().copy(
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Normal,
                ),
                color = colors.fgSubtle,
                modifier = Modifier.padding(start = 6.dp).alignByBaseline(),
            )
        }

        ProgressBar(goal.progressBps, color = tint, modifier = Modifier.padding(top = 12.dp))
        val remaining = (goal.targetCents - goal.savedCents).coerceAtLeast(0)
        val done = goal.progressBps >= 10_000
        // `mt-3 flex items-center justify-between`.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = colors.accent, fontWeight = FontWeight.SemiBold)) {
                        append(
                            if (done) stringResource(R.string.goals_completed)
                            else "${Math.round(goal.progressBps / 100.0)}%",
                        )
                    }
                    if (!done) {
                        withStyle(SpanStyle(color = colors.fgSubtle)) {
                            append(" · ")
                            append(stringResource(R.string.goals_remaining))
                            append(" ")
                            append(maskIfHidden(formatMoney(remaining, goal.currencyCode), hide))
                        }
                    }
                },
                style = MaterialTheme.typography.bodyMedium.tabular(),
                modifier = Modifier.weight(1f),
            )
            // Ghost buttons, `gap-1`: contribute, then buy or graduate.
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // A whole-wallet goal grows with the wallet: nothing to
                // contribute and nothing to graduate (it already is a wallet).
                if (!goal.tracksWallet) {
                    GhostButton(stringResource(R.string.goals_contribute), onClick = { onContribute(goal) })
                }
                if (goal.savedCents > 0 && !(goal.tracksWallet && goal.goalKind == "fund")) {
                    GhostButton(
                        stringResource(
                            if (goal.goalKind == "fund") R.string.goals_convert_to_wallet
                            else R.string.goals_buy,
                        ),
                        onClick = { onUse(goal) },
                        leadingIcon = if (goal.goalKind == "fund") Lucide.Wallet else Lucide.Check,
                        iconSize = 14.dp,
                        iconGap = 6.dp,
                    )
                }
            }
        }

        // The deadline plan, only once the goal actually has one and is unmet:
        // `mt-3 border-t pt-3 text-xs`.
        val plan = goal.plan
        if (plan != null && !done) {
            Spacer(Modifier.height(12.dp))
            HairLine()
            Spacer(Modifier.height(12.dp))
            GoalPlanLines(goal, plan, hide)
            // Only offered when the plan is off track — the same condition the
            // web uses, so it does not nag an on-pace goal.
            if (plan.overdue || goal.isBehind) {
                Text(
                    stringResource(R.string.goals_adjust_date),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.accent,
                    modifier = Modifier.padding(top = 8.dp).clickable { onAdjustDate(goal) },
                )
            }
        }
    }
}

/**
 * Short date like "30 nov 2026" for the plan line — `toLocaleDateString` with
 * `{ day, month: "short", year }`, as the browser writes it in es-MX / en-US.
 */
@Composable
internal fun planDate(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    val locale = java.util.Locale.forLanguageTag(
        if (LocalAppSettings.current.locale.startsWith("en")) "en-US" else "es-MX",
    )
    val date = runCatching { java.time.LocalDate.parse(iso) }.getOrNull() ?: return iso
    return runCatching {
        val format = android.icu.text.DateFormat.getInstanceForSkeleton("yMMMd", locale)
        format.timeZone = android.icu.util.TimeZone.GMT_ZONE
        format.format(java.util.Date(date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()))
    }.getOrDefault(iso)
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
    val warningText = if (colors.isDark) Color(0xFFFBBF24) else Color(0xFFD97706)
    val amber = Color(0xFFF59E0B)

    if (plan.overdue) {
        BadgeLine(
            badge = stringResource(R.string.goals_overdue_badge),
            badgeColor = colors.danger,
            badgeFill = colors.danger.copy(alpha = 0.12f),
            text = text(R.string.goals_overdue_hint, "amount" to money(remaining)),
        )
    } else {
        // A fixed-contribution plan names the projected date instead of a
        // deadline; part-way it keeps the progress line and adds the date.
        val fixed = plan.projectedDate
        val line = when {
            fixed != null && plan.contributedThisPeriodCents <= 0 -> text(
                R.string.goals_plan_fixed,
                "amount" to money(plan.perPeriodCents),
                "cadence" to cadenceAdverb(goal.cadence),
                "date" to planDate(fixed),
            )
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
                "date" to planDate(fixed ?: goal.targetDate),
            )
        }
        Text(line, style = MaterialTheme.typography.bodySmall, color = colors.fgMuted)
        if (fixed != null && plan.contributedThisPeriodCents > 0 && plan.periodMissingCents > 0) {
            Text(
                text(R.string.goals_plan_fixed_date, "date" to planDate(fixed)),
                style = MaterialTheme.typography.bodySmall,
                color = colors.fgMuted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (goal.isBehind) {
            BadgeLine(
                badge = stringResource(R.string.goals_behind_badge),
                badgeColor = warningText,
                badgeFill = amber.copy(alpha = 0.15f),
                text = text(R.string.goals_behind_hint, "amount" to money(plan.behindCents)),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * The web's `<p>` with a `BadgeTag` in it: an `inline-block` pill
 * (`rounded-md px-1.5 py-0.5 font-semibold`) sitting in the sentence, the rest
 * of the text flowing after it and wrapping under it — not a pill in a column
 * beside a paragraph.
 */
@Composable
private fun BadgeLine(
    badge: String,
    badgeColor: Color,
    badgeFill: Color,
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = Broke.colors
    val style = MaterialTheme.typography.bodySmall
    val badgeStyle = style.copy(fontWeight = FontWeight.SemiBold)
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val badgeWidth = with(density) {
        (measurer.measure(badge, badgeStyle).size.width.toDp() + 12.dp + 2.dp).toSp()
    }
    val content = mapOf(
        "badge" to androidx.compose.foundation.text.InlineTextContent(
            androidx.compose.ui.text.Placeholder(
                width = badgeWidth,
                height = 20.sp,
                placeholderVerticalAlign = androidx.compose.ui.text.PlaceholderVerticalAlign.TextCenter,
            ),
        ) {
            Box(Modifier.fillMaxWidth().padding(end = 2.dp)) {
                Text(
                    badge,
                    style = badgeStyle,
                    color = badgeColor,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeFill)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        },
    )
    Text(
        buildAnnotatedString {
            appendInlineContent("badge", badge)
            append(" ")
            append(text)
        },
        inlineContent = content,
        style = style,
        color = colors.fgSubtle,
        modifier = modifier,
    )
}

/** What the wallet form needs to graduate [goal] — the web's `convert` prop. */
fun goalConvert(goal: SavingsGoal, wallets: List<com.asura.finanzas.data.Wallet>) =
    com.asura.finanzas.ui.wallets.GoalConvert(
        goalId = goal.id,
        name = goal.name,
        color = goal.color,
        currencyCode = goal.currencyCode,
        savedCents = goal.savedCents,
        sourceCategoryId = wallets.firstOrNull { it.id == goal.linkedWalletId }?.categoryId,
        sourceWalletId = goal.linkedWalletId,
    )
