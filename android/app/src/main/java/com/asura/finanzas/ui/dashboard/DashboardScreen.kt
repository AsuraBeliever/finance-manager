package com.asura.finanzas.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
// Aliased: this file already has its own FlowRow — the income/expense line of
// the net-worth card — which has nothing to do with the layout one.
import androidx.compose.foundation.layout.FlowRow as ComposeFlowRow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.fillMaxSize
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
import com.asura.finanzas.ui.components.EmptyState
import com.asura.finanzas.ui.components.GlassCard
import com.asura.finanzas.ui.components.GhostButton
import com.asura.finanzas.ui.components.HairLine
import com.asura.finanzas.ui.components.HeroAmount
import com.asura.finanzas.ui.components.Load
import com.asura.finanzas.ui.components.Lucide
import com.asura.finanzas.ui.components.LoadingBox
import com.asura.finanzas.ui.components.keepingPrevious
import com.asura.finanzas.ui.components.WebNegative
import com.asura.finanzas.ui.components.WebPositive
import com.asura.finanzas.ui.components.BarSeries
import com.asura.finanzas.ui.components.RechartsBarChart
import com.asura.finanzas.ui.components.MicroLabel
import com.asura.finanzas.ui.components.PageHeader
import com.asura.finanzas.ui.components.PrivacyToggle
import com.asura.finanzas.ui.components.Period
import com.asura.finanzas.ui.components.PeriodLabel
import com.asura.finanzas.ui.components.PeriodPicker
import com.asura.finanzas.ui.components.loadSynced
import com.asura.finanzas.ui.components.rememberReloadKey
import com.asura.finanzas.ui.components.ChartHit
import com.asura.finanzas.ui.components.ChartTooltip
import com.asura.finanzas.ui.components.TooltipContent
import com.asura.finanzas.ui.components.TooltipItem
import com.asura.finanzas.ui.components.chartTap
import com.asura.finanzas.ui.components.rememberChartHit
import com.asura.finanzas.ui.components.shown
import com.asura.finanzas.ui.formatMoney
import com.asura.finanzas.ui.maskIfHidden
import com.asura.finanzas.ui.parseHexColor
import com.asura.finanzas.ui.theme.Broke
import com.asura.finanzas.ui.theme.TrackingWide
import com.asura.finanzas.ui.theme.tabular

private val rpcJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

/** Where a widget's "View all" sends you. */
enum class DashboardTarget { Budgets, Goals, Subscriptions }

@Composable
fun DashboardScreen(
    repository: BrokeRepository,
    onViewAll: (DashboardTarget) -> Unit,
    /** Held above the tabs so it survives leaving the screen, as the web's
     *  localStorage-backed period does. */
    period: Period,
    onPeriodChange: (Period) -> Unit,
    modifier: Modifier = Modifier,
) {
    val (key, reload) = rememberReloadKey()
    val scope = rememberCoroutineScope()
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
    // Shares the wallets tab's cache entry, the way two web pages reading
    // `["wallets"]` share one query.
    val walletsState by loadSynced("wallets" to false, refetch = key) { repository.wallets() }
    val walletsForDrill = (walletsState as? Load.Ready)?.data ?: emptyList()

    val summaryState = loadSynced("dashboard" to period, refetch = key) {
        repository.dashboard(period.toJson())
    }.value.keepingPrevious()

    val trendsState = loadSynced("trends" to period, refetch = key) {
        repository.spendingTrends(period.toJson())
    }.value.keepingPrevious()

    val expenseState = loadSynced("breakdownExpense" to period, refetch = key) {
        repository.categoryBreakdown("expense", period.toJson())
    }.value.keepingPrevious()

    val incomeState = loadSynced("breakdownIncome" to period, refetch = key) {
        repository.categoryBreakdown("income", period.toJson())
    }.value.keepingPrevious()


    val trends = (trendsState as? Load.Ready)?.data
    val expenseBreakdown = (expenseState as? Load.Ready)?.data
    val incomeBreakdown = (incomeState as? Load.Ready)?.data

    // The planning widgets are extras on this screen: if one fails to load the
    // dashboard still renders without it, same as the web. They read the same
    // keys as their own screens, so opening Goals and coming back costs
    // nothing — and none of them depends on the period.
    val budgetsState by loadSynced("budgets", refetch = key) { repository.budgets() }
    val goalsState by loadSynced("goals", refetch = key) { repository.savingsGoals() }
    val subscriptionsState by loadSynced("subscriptions", refetch = key) {
        repository.subscriptions()
    }
    val budgets = (budgetsState as? Load.Ready)?.data ?: emptyList()
    val goals = (goalsState as? Load.Ready)?.data ?: emptyList()
    val subscriptions = (subscriptionsState as? Load.Ready)?.data

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
            onPeriodChange = onPeriodChange,
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
    onPeriodChange: (Period) -> Unit,
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
            DashWidget("networth") {
                NetWorthCard(summary, trends, netStart, netEnd, hide)
            },
        )
        if (trends != null && trends.buckets.isNotEmpty()) {
            add(DashWidget("flow") { FlowCard(trends) })
        }
        if (budgets.isNotEmpty()) {
            add(
                DashWidget("budget") {
                    BudgetWidget(budgets, hide) { onViewAll(DashboardTarget.Budgets) }
                },
            )
        }
        expenseBreakdown?.takeIf { it.slices.isNotEmpty() }?.let { breakdown ->
            add(
                DashWidget("breakdownExpense") {
                    BreakdownWidget(
                        stringResource(R.string.dashboard_expense_by_category),
                        breakdown,
                        hide,
                        onSlice = { onSlice("expense", it) },
                    )
                },
            )
        }
        incomeBreakdown?.takeIf { it.slices.isNotEmpty() }?.let { breakdown ->
            add(
                DashWidget("breakdownIncome") {
                    BreakdownWidget(
                        stringResource(R.string.dashboard_income_by_category),
                        breakdown,
                        hide,
                        onSlice = { onSlice("income", it) },
                    )
                },
            )
        }
        if (goals.isNotEmpty()) {
            add(
                DashWidget("goals") {
                    GoalsWidget(goals, hide) { onViewAll(DashboardTarget.Goals) }
                },
            )
        }
        // Like the web: only when something actually charges in this period, so
        // browsing a quiet month drops the card instead of showing an empty one.
        subscriptions?.takeIf { list -> list.subscriptions.any { it.chargedInPeriod } }?.let { list ->
            add(
                DashWidget("subscriptions") {
                    SubscriptionsWidget(list.subscriptions, list.monthlyTotalMxnCents, hide) {
                        onViewAll(DashboardTarget.Subscriptions)
                    }
                },
            )
        }
        if (summary.wallets.isNotEmpty()) {
            add(DashWidget("byWallet") { ByWalletWidget(summary, hide) })
        }
        if (summary.investments.isNotEmpty()) {
            add(DashWidget("byInvestment") { ByInvestmentWidget(summary, hide) })
        }
        if (trends != null && (trends.incomeMxnCents > 0 || trends.expenseMxnCents > 0)) {
            add(DashWidget("flowRange") { FlowRangeCard(trends, hide) })
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
            // The header's `mb-7`: 28, of which the list's gap gives 16.
            PageHeader(
                stringResource(R.string.dashboard_title),
                modifier = Modifier.padding(bottom = 12.dp),
                actionGap = 8.dp,
            ) {
                PeriodPicker(value = period, onChange = onPeriodChange, allowAll = true)
                // Clears both the phone order and the desktop grid layout, so
                // every device snaps back to the defaults together. A ghost
                // button on the web.
                GhostButton(
                    stringResource(R.string.dashboard_reset_layout),
                    onClick = onResetLayout,
                    leadingIcon = Lucide.RotateCcw,
                )
            }
        }


        // Nothing to summarise yet: the web swaps every widget for one line
        // saying so, rather than a column of empty cards and $0.00 donuts.
        if (summary.wallets.isEmpty() && summary.investmentsTotalMxnCents == 0L) {
            item {
                EmptyState(
                    Lucide.LayoutDashboard,
                    stringResource(R.string.dashboard_empty_title),
                    stringResource(R.string.dashboard_empty_description),
                )
            }
            return@LazyColumn
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
                    widget.content()
                    // The grip floats over the card's top-right corner, the
                    // web's `absolute right-2.5 top-2.5`. Every widget header
                    // keeps that corner clear, so it covers nothing.
                    ReorderHandle(
                        state = reorderState,
                        key = key,
                        modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                    )
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
            style = MaterialTheme.typography.bodyMedium.tabular().copy(fontSize = 14.sp),
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
            style = MaterialTheme.typography.bodyMedium.tabular().copy(fontSize = 14.sp),
            color = colors.fg,
        )
        // Nothing to compare against reads as noise, so the web hides it at zero.
        if (previousCents > 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.dashboard_previously) + " " +
                    maskIfHidden(formatMoney(previousCents), hide),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp).tabular(),
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
    val hide = LocalAppSettings.current.hideBalances
    val buckets = trends.buckets
    val incomes = stringResource(R.string.dashboard_incomes)
    val expenses = stringResource(R.string.dashboard_expenses)
    val labels = buckets.map { bucketLabel(it.key, trends.bucketUnit) }
    val longLabels = buckets.map { bucketLongLabel(it.key, trends.bucketUnit) }
    // Pesos, the unit the web hands recharts, so the ticks come out the same.
    RechartsBarChart(
        height = 214.dp,
        categories = buckets.size,
        series = listOf(
            BarSeries(incomes, WebPositive, buckets.map { it.incomeMxnCents / 100.0 }),
            BarSeries(expenses, WebNegative, buckets.map { it.expenseMxnCents / 100.0 }),
        ),
        xLabel = { labels[it] },
        hideValues = hide,
        tooltipAt = { i ->
            val bucket = buckets[i]
            TooltipContent(
                label = longLabels[i],
                items = listOf(
                    // recharts sorts the payload by name, as the legend: "Gastos" first.
                    TooltipItem(expenses, maskIfHidden(formatMoney(bucket.expenseMxnCents), hide), WebNegative),
                    TooltipItem(incomes, maskIfHidden(formatMoney(bucket.incomeMxnCents), hide), WebPositive),
                ),
            )
        },
    )
}

/**
 * Short label under a bar: the day for daily buckets, month + year for monthly
 * ones — the web's `bucketLabel` in its compact form.
 */
@Composable
private fun bucketLabel(key: String, unit: String): String {
    // `Intl.DateTimeFormat` with `{ month: "short", year: "2-digit" }` in
    // es-MX / en-US, capitalised: "Sep 26". The JDK's own short Spanish month
    // is "sept.", which is not what the browser prints.
    val locale = java.util.Locale.forLanguageTag(
        if (LocalAppSettings.current.locale == "en") "en-US" else "es-MX",
    )
    return if (unit == "month") {
        runCatching {
            val date = java.time.LocalDate.parse("$key-01")
            val millis = date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
            val format = android.icu.text.DateFormat.getInstanceForSkeleton("MMMyy", locale)
            format.timeZone = android.icu.util.TimeZone.GMT_ZONE
            format.format(java.util.Date(millis)).replaceFirstChar { it.uppercase(locale) }
        }.getOrDefault(key)
    } else {
        key.takeLast(2)
    }
}

/**
 * The tooltip's heading for a bucket — the web's `bucketLabel` in its long
 * form: "Jueves, 10 de septiembre de 2026", or "Septiembre de 2026" for a
 * monthly bucket. ICU skeletons give the same words `Intl.DateTimeFormat` does.
 */
@Composable
private fun bucketLongLabel(key: String, unit: String): String {
    val locale = java.util.Locale.forLanguageTag(
        if (LocalAppSettings.current.locale == "en") "en-US" else "es-MX",
    )
    return runCatching {
        val (skeleton, date) = if (unit == "month") {
            "yMMMM" to java.time.LocalDate.parse("$key-01")
        } else {
            "yMMMMEEEEd" to java.time.LocalDate.parse(key)
        }
        val millis = date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        val format = android.icu.text.DateFormat.getInstanceForSkeleton(skeleton, locale)
        format.timeZone = android.icu.util.TimeZone.GMT_ZONE
        format.format(java.util.Date(millis)).replaceFirstChar { it.uppercase(locale) }
    }.getOrDefault(key)
}

/**
 * One draggable card on the overview. The key is the web's widget key, so the
 * order saved on the account means the same thing in both clients. The card
 * is drawn as its own card.
 */
private class DashWidget(
    val key: String,
    val content: @Composable () -> Unit,
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

/** Gauss error function (Abramowitz–Stegun 7.1.26), for the blurred disc. */
private fun erf(x: Float): Float {
    val t = 1f / (1f + 0.3275911f * kotlin.math.abs(x))
    val y = 1f - (((((1.061405429f * t - 1.453152027f) * t) + 1.421413741f) * t - 0.284496736f) * t + 0.254829592f) *
        t * kotlin.math.exp(-x * x)
    return if (x >= 0) y else -y
}

/** Patrimonio: where the period started, where it ended, and the split. */
@Composable
private fun NetWorthCard(
    summary: DashboardSummary,
    trends: SpendingTrends?,
    netStart: Long,
    netEnd: Long,
    hide: Boolean,
) {
    val colors = Broke.colors
    val accent = colors.accent
    val gold = colors.cyan
    // `p-6` on the web, not the `p-5` most cards use. Behind it, the web's two
    // decorations: a 256 px accent/10 disc blurred by 64 px hanging off the
    // top-right corner, and a 1 px gold/40 line fading in and out along the top.
    GlassCard(
        Modifier.fillMaxWidth(),
        decoration = {
                val px = density
                // A disc of radius 128 blurred with σ = 64 is, radially,
                // 0.1 × Φ((128 − r) / 64): sampled here as gradient stops.
                val center = androidx.compose.ui.geometry.Offset(size.width + 64 * px - 128 * px, -96 * px + 128 * px)
                val reach = 128 * px + 2.5f * 64 * px
                val stops = (0..10).map { i ->
                    val r = reach * i / 10f
                    val z = (128 * px - r) / (64 * px)
                    val phi = 0.5f * (1f + erf(z / kotlin.math.sqrt(2f)))
                    (i / 10f) to accent.copy(alpha = 0.10f * phi)
                }.toTypedArray()
                drawCircle(
                    brush = androidx.compose.ui.graphics.Brush.radialGradient(
                        colorStops = stops,
                        center = center,
                        radius = reach,
                    ),
                    radius = reach,
                    center = center,
                )
                drawRect(
                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                        listOf(
                            androidx.compose.ui.graphics.Color.Transparent,
                            gold.copy(alpha = 0.40f),
                            androidx.compose.ui.graphics.Color.Transparent,
                        ),
                    ),
                    // Inside the border, like any absolute child of the card.
                    topLeft = androidx.compose.ui.geometry.Offset(0f, 1 * px),
                    size = androidx.compose.ui.geometry.Size(size.width, 1 * px),
                )
        },
        padding = 24.dp,
    ) {
        // The eye lives here, beside the eyebrow, exactly as on the web — not
        // up in the page header. `flex items-center gap-2`.
        Row(verticalAlignment = Alignment.CenterVertically) {
            MicroLabel(stringResource(R.string.dashboard_net_worth))
            Spacer(Modifier.width(8.dp))
            PrivacyToggle()
        }

        // `mt-2 flex flex-wrap items-end gap-x-5 gap-y-3`: at phone width the
        // start figure and its arrow share the first line and the end figure
        // wraps under them.
        ComposeFlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.align(Alignment.Bottom)) {
                MicroLabel(
                    stringResource(R.string.dashboard_period_start),
                    color = colors.fgSubtle,
                    letterSpacing = TrackingWide,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
                )
                Text(
                    maskIfHidden(formatMoney(netStart), hide),
                    style = MaterialTheme.typography.headlineMedium.tabular(),
                    color = colors.fgMuted,
                )
            }
            Icon(
                Lucide.ArrowRight,
                contentDescription = null,
                tint = colors.fgSubtle,
                modifier = Modifier.align(Alignment.Bottom).padding(bottom = 6.dp).size(22.dp),
            )
            Column(Modifier.align(Alignment.Bottom)) {
                MicroLabel(
                    stringResource(R.string.dashboard_period_end),
                    color = colors.fgSubtle,
                    letterSpacing = TrackingWide,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
                )
                // `text-4xl` — 36 px, not the 40 sp the other heroes use.
                HeroAmount(maskIfHidden(formatMoney(netEnd), hide), fontSize = 36.sp)
            }
        }

        // One wrapping line with a middle dot between the two, the web's
        // `mt-3 flex flex-wrap gap-x-2 gap-y-1`.
        ComposeFlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            LegendRow(
                color = colors.accent,
                label = stringResource(R.string.nav_wallets),
                amount = maskIfHidden(formatMoney(summary.totalEndMxnCents), hide),
            )
            if (summary.investmentsTotalMxnCents > 0) {
                Text(
                    "\u00B7",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = colors.borderMuted,
                )
                LegendRow(
                    color = colors.cyan,
                    label = stringResource(R.string.investments_total),
                    amount = maskIfHidden(formatMoney(summary.investmentsTotalMxnCents), hide),
                )
            }
        }

        // Only when the period actually moved money. A quiet month otherwise
        // gets a rule and two "$0.00 · −100%" rows saying nothing, which is
        // why the web gates this block the same way.
        // `mt-4 border-t pt-4 flex flex-wrap gap-x-6 gap-y-2`.
        if (trends != null && (trends.incomeMxnCents > 0 || trends.expenseMxnCents > 0)) {
            Spacer(Modifier.height(16.dp))
            HairLine()
            Spacer(Modifier.height(16.dp))
            ComposeFlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FlowRow(
                    label = stringResource(R.string.dashboard_incomes),
                    amount = maskIfHidden(formatMoney(trends.incomeMxnCents), hide),
                    previousCents = trends.incomePrevMxnCents,
                    trendBps = trends.incomeTrendBps,
                    upIsGood = true,
                )
                FlowRow(
                    label = stringResource(R.string.dashboard_expenses),
                    amount = maskIfHidden(formatMoney(trends.expenseMxnCents), hide),
                    previousCents = trends.expensePrevMxnCents,
                    trendBps = trends.expenseTrendBps,
                    upIsGood = false,
                )
            }
        }

        // With a single currency the consolidated figure above already says
        // everything; the web only breaks it out past that, and flags the ones
        // whose MXN conversion is a guess.
        if (summary.byCurrency.size > 1) {
            Spacer(Modifier.height(12.dp))
            ComposeFlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                summary.byCurrency.forEach { sub ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            sub.currencyCode,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                            ),
                            color = colors.fg,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            maskIfHidden(formatMoney(sub.balanceCents, sub.currencyCode), hide),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp).tabular(),
                            color = colors.fgMuted,
                        )
                        if (!sub.hasRate) {
                            Text(
                                " · " + stringResource(R.string.dashboard_no_rate),
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                color = colors.danger,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Income and expenses bucket by bucket — the web's "flow" widget. */
@Composable
private fun FlowCard(trends: SpendingTrends) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.dashboard_flow),
                style = MaterialTheme.typography.titleLarge,
                color = Broke.colors.fg,
                modifier = Modifier.weight(1f).padding(end = 28.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        FlowChart(trends)
    }
}

/**
 * The period's income against its expenses, as two bars — the web's
 * `FlowRangeWidget`: one blank category, `barGap={0}`, `barCategoryGap="22%"`.
 * Both figures come straight from `getSpendingTrends`.
 */
@Composable
private fun FlowRangeCard(
    trends: SpendingTrends,
    hide: Boolean,
) {
    val incomes = stringResource(R.string.dashboard_incomes)
    val expenses = stringResource(R.string.dashboard_expenses)

    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.dashboard_income_vs_expense),
                style = MaterialTheme.typography.titleLarge,
                color = Broke.colors.fg,
                modifier = Modifier.weight(1f).padding(end = 28.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        RechartsBarChart(
            height = 214.dp,
            categories = 1,
            series = listOf(
                BarSeries(incomes, WebPositive, listOf(trends.incomeMxnCents / 100.0)),
                BarSeries(expenses, WebNegative, listOf(trends.expenseMxnCents / 100.0)),
            ),
            xLabel = { " " },
            hideValues = hide,
            xFontSize = 12.sp,
            xTickLine = false,
            barCategoryGap = 0.22f,
            barGap = 0.dp,
            // No label line: the web's single category is named " ".
            tooltipAt = {
                TooltipContent(
                    label = null,
                    items = listOf(
                        TooltipItem(expenses, maskIfHidden(formatMoney(trends.expenseMxnCents), hide), WebNegative),
                        TooltipItem(incomes, maskIfHidden(formatMoney(trends.incomeMxnCents), hide), WebPositive),
                    ),
                )
            },
        )
    }
}
