package com.asura.finanzas.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import com.asura.finanzas.ui.components.ReorderHandle
import com.asura.finanzas.ui.components.rememberReorderState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import com.asura.finanzas.R
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.CategorySlice
import com.asura.finanzas.data.Wallet
import com.asura.finanzas.data.Budget
import com.asura.finanzas.data.CategoryBreakdown
import com.asura.finanzas.data.DashboardSummary
import com.asura.finanzas.data.SavingsGoal
import com.asura.finanzas.data.SubscriptionList
import com.asura.finanzas.data.SpendingTrends
import com.asura.finanzas.ui.LocalAppSettings
import com.asura.finanzas.ui.components.ChipButton
import com.asura.finanzas.ui.components.Dot
import com.asura.finanzas.ui.components.ErrorBox
import com.asura.finanzas.ui.components.GlassCard
import com.asura.finanzas.ui.components.HairLine
import com.asura.finanzas.ui.components.HeroAmount
import com.asura.finanzas.ui.components.Load
import com.asura.finanzas.ui.components.Lucide
import com.asura.finanzas.ui.components.LoadingBox
import com.asura.finanzas.ui.components.MicroLabel
import com.asura.finanzas.ui.components.OfflineNotice
import com.asura.finanzas.ui.components.PageHeader
import com.asura.finanzas.ui.components.PrivacyToggle
import com.asura.finanzas.ui.components.Period
import com.asura.finanzas.ui.components.PeriodLabel
import com.asura.finanzas.ui.components.PeriodPickerDialog
import com.asura.finanzas.ui.components.loadSynced
import com.asura.finanzas.ui.components.rememberReloadKey
import com.asura.finanzas.ui.formatMoney
import com.asura.finanzas.ui.maskIfHidden
import com.asura.finanzas.ui.parseHexColor
import com.asura.finanzas.ui.theme.Broke

private val rpcJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

/** Where a widget's "View all" sends you. */
enum class DashboardTarget { Budgets, Goals, Subscriptions }

@Composable
fun DashboardScreen(
    repository: BrokeRepository,
    onViewAll: (DashboardTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val (key, reload) = rememberReloadKey()
    val scope = rememberCoroutineScope()
    var period by remember { mutableStateOf<Period>(Period.CurrentMonth) }
    var showPeriod by remember { mutableStateOf(false) }
    // Which breakdown slice is open, as (kind, target).
    var drillInto by remember { mutableStateOf<Pair<String, CategoryDetailTarget>?>(null) }
    // Widget order, shared with the web through the account.
    var savedOrder by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(key) {
        savedOrder = runCatching {
            repository.getSetting("dashboardOrder")
                ?.takeIf { it.isNotBlank() }
                ?.let { rpcJson.decodeFromString(ListSerializer(String.serializer()), it) }
                .orEmpty()
        }.getOrDefault(emptyList())
    }
    // Only needed to name each drill-down row's currency; failing is harmless.
    val walletsForDrill by produceState(initialValue = emptyList<Wallet>(), key) {
        value = runCatching { repository.wallets().value }.getOrDefault(emptyList())
    }

    val summaryState by loadSynced(key to period) { repository.dashboard() }
    val trendsState by loadSynced(key to period) { repository.spendingTrends(period.toJson()) }
    val expenseState by loadSynced(key to period) {
        repository.categoryBreakdown("expense", period.toJson())
    }
    val incomeState by loadSynced(key to period) {
        repository.categoryBreakdown("income", period.toJson())
    }

    val trends = (trendsState as? Load.Ready)?.data
    val expenseBreakdown = (expenseState as? Load.Ready)?.data
    val incomeBreakdown = (incomeState as? Load.Ready)?.data

    // The planning widgets are extras on this screen: if one fails to load the
    // dashboard still renders without it, same as the web.
    val budgets by produceState<List<Budget>>(emptyList(), key, period) {
        value = runCatching { repository.budgets().value }.getOrDefault(emptyList())
    }
    val goals by produceState<List<SavingsGoal>>(emptyList(), key, period) {
        value = runCatching { repository.savingsGoals().value }.getOrDefault(emptyList())
    }
    val subscriptions by produceState<SubscriptionList?>(null, key, period) {
        value = runCatching { repository.subscriptions().value }.getOrNull()
    }

    when (val current = summaryState) {
        is Load.Loading -> LoadingBox(modifier)
        is Load.Failed -> ErrorBox(current.message, reload, modifier)
        is Load.Ready -> DashboardContent(
            summary = current.data,
            trends = trends,
            expenseBreakdown = expenseBreakdown,
            incomeBreakdown = incomeBreakdown,
            budgets = budgets,
            goals = goals,
            subscriptions = subscriptions,
            fromCache = current.fromCache,
            period = period,
            onPickPeriod = { showPeriod = true },
            onViewAll = onViewAll,
            onResetLayout = {
                scope.launch {
                    runCatching { repository.setSetting("dashboardLayout", "") }
                    runCatching { repository.setSetting("dashboardOrder", "") }
                    savedOrder = emptyList()
                }
            },
            savedOrder = savedOrder,
            onReorder = { keys ->
                // Keys this build does not render (a widget only the web has, or
                // one whose data has not loaded) keep their place at the end
                // instead of being dropped from the shared order.
                val extra = savedOrder.filterNot { it in keys }
                val next = keys + extra
                savedOrder = next
                scope.launch {
                    runCatching {
                        repository.setSetting(
                            "dashboardOrder",
                            rpcJson.encodeToString(ListSerializer(String.serializer()), next),
                        )
                    }
                }
            },
            onSlice = { kind, slice ->
                drillInto = kind to CategoryDetailTarget(
                    categoryId = slice.categoryId,
                    name = slice.name,
                    mxnCents = slice.mxnCents,
                )
            },
            modifier = modifier,
        )
    }

    drillInto?.let { (kind, target) ->
        CategoryDetailDialog(
            repository = repository,
            kind = kind,
            period = period,
            target = target,
            wallets = walletsForDrill,
            onDismiss = { drillInto = null },
        )
    }

    if (showPeriod) {
        PeriodPickerDialog(
            selected = period,
            // Parameters are edited inline, so a pick applies without closing.
            onSelect = { period = it },
            onDismiss = { showPeriod = false },
            allowAll = true,
        )
    }
}

@Composable
private fun DashboardContent(
    summary: DashboardSummary,
    trends: SpendingTrends?,
    expenseBreakdown: CategoryBreakdown?,
    incomeBreakdown: CategoryBreakdown?,
    budgets: List<Budget>,
    goals: List<SavingsGoal>,
    subscriptions: SubscriptionList?,
    fromCache: Boolean,
    period: Period,
    onPickPeriod: () -> Unit,
    onViewAll: (DashboardTarget) -> Unit,
    onSlice: (String, CategorySlice) -> Unit,
    onResetLayout: () -> Unit,
    savedOrder: List<String>,
    onReorder: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Broke.colors
    val hide = LocalAppSettings.current.hideBalances

    // Both figures come from the API as a matched pair; the app only puts them
    // side by side. Nothing here recomputes a balance.
    val netStart = summary.totalStartMxnCents + summary.investmentsStartMxnCents
    val netEnd = summary.totalEndMxnCents + summary.investmentsTotalMxnCents

    // Keys, order and the condition for showing each one all match the web's
    // `DashboardPage` exactly: the arrangement is stored per account, so a key
    // means the same widget on both clients and the defaults line up.
    val available = buildList<DashWidget> {
        add(
            DashWidget("networth") { handle ->
                NetWorthCard(summary, trends, netStart, netEnd, hide, handle)
            },
        )
        if (trends != null && trends.buckets.isNotEmpty()) {
            add(DashWidget("flow") { handle -> FlowCard(trends, handle) })
        }
        if (budgets.isNotEmpty()) {
            add(
                DashWidget("budget") { handle ->
                    BudgetWidget(budgets, hide, handle) { onViewAll(DashboardTarget.Budgets) }
                },
            )
        }
        expenseBreakdown?.takeIf { it.slices.isNotEmpty() }?.let { breakdown ->
            add(
                DashWidget("breakdownExpense") { handle ->
                    BreakdownWidget(
                        stringResource(R.string.dashboard_expense_by_category),
                        breakdown,
                        hide,
                        onSlice = { onSlice("expense", it) },
                        handle = handle,
                    )
                },
            )
        }
        incomeBreakdown?.takeIf { it.slices.isNotEmpty() }?.let { breakdown ->
            add(
                DashWidget("breakdownIncome") { handle ->
                    BreakdownWidget(
                        stringResource(R.string.dashboard_income_by_category),
                        breakdown,
                        hide,
                        onSlice = { onSlice("income", it) },
                        handle = handle,
                    )
                },
            )
        }
        if (goals.isNotEmpty()) {
            add(
                DashWidget("goals") { handle ->
                    GoalsWidget(goals, hide, handle) { onViewAll(DashboardTarget.Goals) }
                },
            )
        }
        // Like the web: only when something actually charges in this period, so
        // browsing a quiet month drops the card instead of showing an empty one.
        subscriptions?.takeIf { list -> list.subscriptions.any { it.chargedInPeriod } }?.let { list ->
            add(
                DashWidget("subscriptions") { handle ->
                    SubscriptionsWidget(list.subscriptions, list.monthlyTotalMxnCents, hide, handle) {
                        onViewAll(DashboardTarget.Subscriptions)
                    }
                },
            )
        }
        if (summary.wallets.isNotEmpty()) {
            add(DashWidget("byWallet") { handle -> ByWalletWidget(summary, hide, handle) })
        }
        if (summary.investments.isNotEmpty()) {
            add(DashWidget("byInvestment") { handle -> ByInvestmentWidget(summary, hide, handle) })
        }
        if (trends != null && (trends.incomeMxnCents > 0 || trends.expenseMxnCents > 0)) {
            add(DashWidget("flowRange") { handle -> FlowRangeCard(trends, hide, handle) })
        }
    }

    val byKey = available.associateBy { it.key }
    // Only the KEY order lives in state. Holding the widgets themselves would
    // pin their lambdas — and with them the summary they closed over — so
    // switching period would redraw last period's figures whenever the set of
    // widgets happened not to change.
    val keys = available.map { it.key }
    val order = remember(keys, savedOrder) {
        mutableStateListOf<String>().apply { addAll(applyOrder(keys, savedOrder)) }
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val firstRow = 1 +
        (if (fromCache) 1 else 0) +
        (if (summary.missingRates.isNotEmpty()) 1 else 0)
    val reorderState = rememberReorderState(
        listState = listState,
        scope = scope,
        range = { firstRow until firstRow + order.size },
        onMove = { from, to -> order.add(to, order.removeAt(from)) },
        onDrop = { onReorder(order.toList()) },
    )

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            PageHeader(stringResource(R.string.dashboard_title)) {
                ChipButton(
                    text = PeriodLabel(period),
                    onClick = onPickPeriod,
                    leadingIcon = Lucide.Calendar,
                    trailingIcon = Lucide.ChevronDown,
                )
                // Clears both the phone order and the desktop grid layout, so
                // every device snaps back to the defaults together.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onResetLayout() }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Icon(
                        Lucide.RotateCcw,
                        contentDescription = null,
                        tint = colors.fg,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.dashboard_reset_layout),
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                        color = colors.fg,
                    )
                }
            }
        }

        if (fromCache) {
            item { OfflineNotice(stringResource(R.string.offline_banner), Modifier.fillMaxWidth()) }
        }

        // The rates warning is not one of the web's grid widgets, so it stays
        // put rather than joining the draggable stack.
        if (summary.missingRates.isNotEmpty()) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(
                        "${stringResource(R.string.dashboard_missing_rates)}: " +
                            summary.missingRates.joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.fgMuted,
                    )
                }
            }
        }

        itemsIndexed(order, key = { _, key -> key }) { _, key ->
            val widget = byKey[key]
            if (widget != null) {
                val dragging = reorderState.draggingKey == key
                Box(
                    Modifier
                        .fillMaxWidth()
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) reorderState.offsetY else 0f },
                ) {
                    // The grip goes in the card's own header row, next to
                    // "View all", so nothing inside the widget gets covered.
                    widget.content { ReorderHandle(state = reorderState, key = key) }
                }
            }
        }
    }
}

@Composable
private fun LegendRow(color: androidx.compose.ui.graphics.Color, label: String, amount: String) {
    val colors = Broke.colors
    // The web keeps these inline — dot, label, figure, all butted together —
    // rather than pushing the figure out to the right edge.
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(color, 8.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = colors.fgMuted,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            amount,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = colors.fg,
        )
    }
}

@Composable
private fun FlowRow(
    label: String,
    amount: String,
    previousCents: Long,
    trendBps: Long,
    upIsGood: Boolean,
) {
    val hide = LocalAppSettings.current.hideBalances
    val colors = Broke.colors
    // Label, figure, "before X" and the badge sit on one line, as on the web —
    // not with the badge pushed to the far edge and the comparison below it.
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = colors.fgMuted,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            amount,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = colors.fg,
        )
        // Nothing to compare against reads as noise, so the web hides it at zero.
        if (previousCents > 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.dashboard_previously) + " " +
                    maskIfHidden(formatMoney(previousCents), hide),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                color = colors.fgSubtle,
            )
        }
        if (trendBps != 0L) {
            Spacer(Modifier.width(8.dp))
            // Basis points to a percentage is presentation; the comparison
            // itself was computed by the server.
            val up = trendBps > 0
            val good = up == upIsGood
            val tint = if (good) colors.accent else colors.danger
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                // The web's chip carries the little trend arrow next to the
                // percentage; text alone read as a plain tag.
                Icon(
                    if (up) Lucide.TrendingUp else Lucide.TrendingDown,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = (if (up) "+" else "−") + "%.1f%%".format(kotlin.math.abs(trendBps) / 100.0),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                    color = tint,
                )
            }
        }
    }
}

/**
 * Income vs expense bars, the native counterpart of the web's `FlowChart`.
 *
 * Carries the same furniture the web chart has: a vertical scale, a label under
 * each bucket and the legend in the same order (expenses first). The tick values
 * only divide a server-computed maximum — no money is worked out here.
 */
@Composable
private fun FlowChart(trends: SpendingTrends) {
    val colors = Broke.colors
    val hide = LocalAppSettings.current.hideBalances
    val buckets = trends.buckets.takeLast(24)
    val peak = buckets.maxOfOrNull { maxOf(it.incomeMxnCents, it.expenseMxnCents) }
        ?.coerceAtLeast(1) ?: 1
    // The chart library the web uses rounds the axis up to a friendly number and
    // labels four even steps; a raw peak gave ticks like "824.74".
    val max = niceCeiling(peak)
    val axisColor = colors.borderMuted
    val axisLabelHeight = 20.dp

    Row(modifier = Modifier.fillMaxWidth().height(170.dp)) {
        // Vertical scale, hidden with the balances like every other figure.
        if (!hide) {
            Column(
                modifier = Modifier.height(150.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                listOf(1f, 0.75f, 0.5f, 0.25f, 0f).forEach { fraction ->
                    Text(
                        ((max * fraction).toLong() / 100).toString(),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = colors.fgSubtle,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
        }

        // The chart draws its axes as hairlines; without them the bars floated.
        Box(
            Modifier
                .width(1.dp)
                .height(150.dp)
                .background(colors.borderMuted),
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .drawBehind {
                    val y = size.height - axisLabelHeight.toPx()
                    drawLine(
                        color = axisColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            buckets.forEach { bucket ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Row(
                        modifier = Modifier.height(150.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Bar(bucket.incomeMxnCents, max, colors.positive)
                        Bar(bucket.expenseMxnCents, max, colors.danger)
                    }
                    Text(
                        bucketLabel(bucket.key, trends.bucketUnit),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.fgSubtle,
                        maxLines = 1,
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    // Expenses first, the order the web's legend uses.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
    ) {
        ChartLegend(colors.danger, stringResource(R.string.dashboard_expenses))
        ChartLegend(colors.positive, stringResource(R.string.dashboard_incomes))
    }
}

/** One legend entry: the chart library marks series with a small square. */
@Composable
private fun ChartLegend(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = Broke.colors.fgMuted,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/**
 * Rounds a peak up to the next 1/2/2.5/5 × 10ⁿ, so the axis reads 250 · 500 ·
 * 750 · 1000 instead of quarters of whatever the tallest bar happened to be.
 * Works on whole cents; nothing here is money arithmetic, it is axis furniture.
 */
private fun niceCeiling(peak: Long): Long {
    val pesos = peak / 100.0
    if (pesos <= 0) return 100
    // Recharts rounds the *step*, not the top: the rough step (a quarter of the
    // range, for its five ticks) goes up to the next twentieth of its own order
    // of magnitude, and the axis ends at four of those. That is why the web
    // labels 550 · 1100 · 1650 · 2200 rather than a flat 2500.
    val rough = pesos / 4.0
    val digitCount = Math.floor(Math.log10(rough)).toInt() + 1
    val unit = Math.pow(10.0, digitCount.toDouble()) * if (digitCount != 1) 0.05 else 0.1
    val step = Math.ceil(rough / unit) * unit
    return Math.round(step * 4 * 100)
}

/**
 * Short label under a bar: the day for daily buckets, month + year for monthly
 * ones — the web's `bucketLabel` in its compact form.
 */
@Composable
private fun bucketLabel(key: String, unit: String): String {
    val locale = java.util.Locale.forLanguageTag(LocalAppSettings.current.locale)
    return if (unit == "month") {
        runCatching {
            val (y, m) = key.split("-").let { it[0].toInt() to it[1].toInt() }
            java.time.Month.of(m).getDisplayName(java.time.format.TextStyle.SHORT, locale) +
                " " + (y % 100)
        }.getOrDefault(key)
    } else {
        key.takeLast(2)
    }
}

@Composable
private fun Bar(value: Long, max: Long, color: androidx.compose.ui.graphics.Color) {
    val fraction = (value.toFloat() / max.toFloat()).coerceIn(0f, 1f)
    Box(
        Modifier
            .width(5.dp)
            .height((130 * fraction).dp.coerceAtLeast(if (value > 0) 3.dp else 0.dp))
            .clip(RoundedCornerShape(3.dp))
            .background(color),
    )
}

/**
 * One draggable card on the overview. The key is the web's widget key, so the
 * order saved on the account means the same thing in both clients. The card
 * draws the drag handle it is given inside its own header.
 */
private class DashWidget(
    val key: String,
    val content: @Composable (handle: @Composable () -> Unit) -> Unit,
)

/**
 * Apply a saved order: the keys it mentions first, in the order it lists them,
 * then anything it does not mention — a widget added since the order was saved,
 * or one that only shows up once its own query lands — appended at the end,
 * keeping the natural order among themselves.
 */
private fun applyOrder(keys: List<String>, order: List<String>): List<String> {
    if (order.isEmpty()) return keys
    val rank = order.withIndex().associate { (index, key) -> key to index }
    return keys.sortedBy { rank[it] ?: Int.MAX_VALUE }
}

/** Patrimonio: where the period started, where it ended, and the split. */
@Composable
private fun NetWorthCard(
    summary: DashboardSummary,
    trends: SpendingTrends?,
    netStart: Long,
    netEnd: Long,
    hide: Boolean,
    handle: @Composable () -> Unit,
) {
    val colors = Broke.colors
    GlassCard(Modifier.fillMaxWidth()) {
        // The eye lives here, beside the eyebrow, exactly as on the web — not
        // up in the page header.
        Row(verticalAlignment = Alignment.CenterVertically) {
            MicroLabel(stringResource(R.string.dashboard_net_worth))
            Spacer(Modifier.width(8.dp))
            PrivacyToggle()
            Spacer(Modifier.weight(1f))
            handle()
        }
        Spacer(Modifier.height(14.dp))

        MicroLabel(stringResource(R.string.dashboard_period_start), color = colors.fgSubtle)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                maskIfHidden(formatMoney(netStart), hide),
                style = MaterialTheme.typography.headlineMedium,
                color = colors.fgMuted,
            )
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = colors.fgSubtle,
                modifier = Modifier.padding(start = 12.dp).size(20.dp),
            )
        }

        Spacer(Modifier.height(14.dp))
        MicroLabel(stringResource(R.string.dashboard_period_end), color = colors.fgSubtle)
        Spacer(Modifier.height(2.dp))
        HeroAmount(maskIfHidden(formatMoney(netEnd), hide), fontSize = 40.sp)

        Spacer(Modifier.height(16.dp))
        LegendRow(
            color = colors.accent,
            label = stringResource(R.string.nav_wallets),
            amount = maskIfHidden(formatMoney(summary.totalEndMxnCents), hide),
        )
        Spacer(Modifier.height(6.dp))
        LegendRow(
            color = colors.cyan,
            label = stringResource(R.string.nav_investments),
            amount = maskIfHidden(formatMoney(summary.investmentsTotalMxnCents), hide),
        )

        if (trends != null) {
            Spacer(Modifier.height(16.dp))
            HairLine()
            Spacer(Modifier.height(14.dp))
            FlowRow(
                label = stringResource(R.string.dashboard_incomes),
                amount = maskIfHidden(formatMoney(trends.incomeMxnCents), hide),
                previousCents = trends.incomePrevMxnCents,
                trendBps = trends.incomeTrendBps,
                upIsGood = true,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                label = stringResource(R.string.dashboard_expenses),
                amount = maskIfHidden(formatMoney(trends.expenseMxnCents), hide),
                previousCents = trends.expensePrevMxnCents,
                trendBps = trends.expenseTrendBps,
                upIsGood = false,
            )
        }
    }
}

/** Income and expenses bucket by bucket — the web's "flow" widget. */
@Composable
private fun FlowCard(trends: SpendingTrends, handle: @Composable () -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.dashboard_flow),
                style = MaterialTheme.typography.titleMedium,
                color = Broke.colors.fg,
                modifier = Modifier.weight(1f),
            )
            handle()
        }
        Spacer(Modifier.height(16.dp))
        FlowChart(trends)
    }
}

/**
 * The period's income against its expenses, as two bars — the web's
 * `FlowRangeWidget`. Both figures come straight from `getSpendingTrends`; the
 * only arithmetic here is the fraction that sets each bar's height.
 */
@Composable
private fun FlowRangeCard(
    trends: SpendingTrends,
    hide: Boolean,
    handle: @Composable () -> Unit,
) {
    val colors = Broke.colors
    // Same rounded scale as the bucket chart, so both charts on the overview
    // label their axis the way the web's does.
    val max = niceCeiling(maxOf(trends.incomeMxnCents, trends.expenseMxnCents).coerceAtLeast(1))
    val axisColor = colors.borderMuted

    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.dashboard_income_vs_expense),
                style = MaterialTheme.typography.titleMedium,
                color = colors.fg,
                modifier = Modifier.weight(1f),
            )
            handle()
        }
        Spacer(Modifier.height(20.dp))

        // Same furniture as the bucket chart above it: a vertical scale on the
        // left, hairline axes, and the legend underneath, expenses first.
        Row(modifier = Modifier.fillMaxWidth().height(170.dp)) {
            if (!hide) {
                Column(
                    modifier = Modifier.height(150.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End,
                ) {
                    listOf(1f, 0.75f, 0.5f, 0.25f, 0f).forEach { fraction ->
                        Text(
                            ((max * fraction).toLong() / 100).toString(),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = colors.fgSubtle,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
            }

            Box(
                Modifier
                    .width(1.dp)
                    .height(150.dp)
                    .background(colors.borderMuted),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(150.dp)
                    .drawBehind {
                        drawLine(
                            color = axisColor,
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                            strokeWidth = 1.dp.toPx(),
                        )
                    },
                contentAlignment = Alignment.BottomCenter,
            ) {
                // `barGap={0}` puts the pair shoulder to shoulder in the middle
                // of the band, income on the left; together they take a little
                // over half the plot.
                Row(
                    modifier = Modifier.fillMaxWidth(0.55f).height(150.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    TotalsBar(
                        fraction = trends.incomeMxnCents.toFloat() / max.toFloat(),
                        color = colors.positive,
                        modifier = Modifier.weight(1f),
                    )
                    TotalsBar(
                        fraction = trends.expenseMxnCents.toFloat() / max.toFloat(),
                        color = colors.danger,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
        ) {
            ChartLegend(colors.danger, stringResource(R.string.dashboard_expenses))
            ChartLegend(colors.positive, stringResource(R.string.dashboard_incomes))
        }
    }
}

/** One bar of the totals chart: a plain column with a 4 px rounded top. */
@Composable
private fun TotalsBar(
    fraction: Float,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxHeight(fraction.coerceIn(0f, 1f))
            .heightIn(min = if (fraction > 0f) 2.dp else 0.dp)
            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
            .background(color),
    )
}
