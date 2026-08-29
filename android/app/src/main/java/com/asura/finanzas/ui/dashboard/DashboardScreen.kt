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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                scope.launch { runCatching { repository.setSetting("dashboardLayout", "") } }
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
    modifier: Modifier = Modifier,
) {
    val colors = Broke.colors
    val hide = LocalAppSettings.current.hideBalances

    // Both figures come from the API as a matched pair; the app only puts them
    // side by side. Nothing here recomputes a balance.
    val netStart = summary.totalStartMxnCents + summary.investmentsStartMxnCents
    val netEnd = summary.totalEndMxnCents + summary.investmentsTotalMxnCents

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            PageHeader(stringResource(R.string.dashboard_title)) {
                PrivacyToggle()
                // The web only lets you drag widgets at tablet width and up; on
                // a phone it stacks them in a fixed order, which is what this
                // screen already does. What the button still does from a phone
                // is clear the shared layout so every device snaps back.
                Text(
                    stringResource(R.string.dashboard_reset_layout),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.fgMuted,
                    modifier = Modifier.clickable { onResetLayout() },
                )
                ChipButton(
                    text = PeriodLabel(period),
                    onClick = onPickPeriod,
                    leadingIcon = Icons.Outlined.CalendarMonth,
                    trailingIcon = Icons.Outlined.ExpandMore,
                )
            }
        }

        if (fromCache) {
            item { OfflineNotice(stringResource(R.string.offline_banner), Modifier.fillMaxWidth()) }
        }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                MicroLabel(stringResource(R.string.dashboard_net_worth))
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

        if (trends != null && trends.buckets.isNotEmpty()) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.dashboard_income_vs_expense),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.fg,
                    )
                    Spacer(Modifier.height(16.dp))
                    FlowChart(trends)
                }
            }
        }

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

        if (budgets.isNotEmpty()) {
            item { BudgetWidget(budgets, hide) { onViewAll(DashboardTarget.Budgets) } }
        }

        expenseBreakdown?.takeIf { it.slices.isNotEmpty() }?.let { breakdown ->
            item {
                BreakdownWidget(
                    stringResource(R.string.dashboard_expense_by_category),
                    breakdown,
                    hide,
                    onSlice = { onSlice("expense", it) },
                )
            }
        }

        incomeBreakdown?.takeIf { it.slices.isNotEmpty() }?.let { breakdown ->
            item {
                BreakdownWidget(
                    stringResource(R.string.dashboard_income_by_category),
                    breakdown,
                    hide,
                    onSlice = { onSlice("income", it) },
                )
            }
        }

        if (goals.isNotEmpty()) {
            item { GoalsWidget(goals, hide) { onViewAll(DashboardTarget.Goals) } }
        }

        if (summary.wallets.isNotEmpty()) {
            item { ByWalletWidget(summary, hide) }
        }

        if (summary.investments.isNotEmpty()) {
            item { ByInvestmentWidget(summary, hide) }
        }

        subscriptions?.let {
            item {
                SubscriptionsWidget(it.subscriptions, it.monthlyTotalMxnCents, hide) {
                    onViewAll(DashboardTarget.Subscriptions)
                }
            }
        }
    }
}

@Composable
private fun LegendRow(color: androidx.compose.ui.graphics.Color, label: String, amount: String) {
    val colors = Broke.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Dot(color)
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.fgMuted,
            modifier = Modifier.weight(1f),
        )
        Text(amount, style = MaterialTheme.typography.bodyLarge, color = colors.fg)
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
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.fgMuted)
        Spacer(Modifier.width(10.dp))
        Text(amount, style = MaterialTheme.typography.bodyLarge, color = colors.fg)
        Spacer(Modifier.weight(1f))
        if (trendBps != 0L) {
            // Basis points to a percentage is presentation; the comparison
            // itself was computed by the server.
            val up = trendBps > 0
            val good = up == upIsGood
            val tint = if (good) colors.positive else colors.danger
            Text(
                text = (if (up) "+" else "−") + "%.1f%%".format(kotlin.math.abs(trendBps) / 100.0),
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(tint.copy(alpha = 0.14f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
    // Nothing to compare against reads as noise, so the web hides it at zero.
    if (previousCents > 0) {
        Text(
            text = stringResource(R.string.dashboard_previously) + " " +
                maskIfHidden(formatMoney(previousCents), hide),
            style = MaterialTheme.typography.labelSmall,
            color = colors.fgSubtle,
        )
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
    val max = buckets.maxOfOrNull { maxOf(it.incomeMxnCents, it.expenseMxnCents) }
        ?.coerceAtLeast(1) ?: 1

    Row(modifier = Modifier.fillMaxWidth().height(170.dp)) {
        // Vertical scale, hidden with the balances like every other figure.
        if (!hide) {
            Column(
                modifier = Modifier.height(150.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                listOf(1f, 0.5f, 0f).forEach { fraction ->
                    Text(
                        formatMoney((max * fraction).toLong(), withSymbol = false),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.fgSubtle,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
        }

        Row(
            modifier = Modifier.weight(1f),
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
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(colors.danger, 9.dp)
            Text(
                stringResource(R.string.dashboard_expenses),
                style = MaterialTheme.typography.labelSmall,
                color = colors.fgMuted,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(colors.positive, 9.dp)
            Text(
                stringResource(R.string.dashboard_incomes),
                style = MaterialTheme.typography.labelSmall,
                color = colors.fgMuted,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
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
