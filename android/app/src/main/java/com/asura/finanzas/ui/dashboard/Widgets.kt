package com.asura.finanzas.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.asura.finanzas.R
import com.asura.finanzas.data.Budget
import com.asura.finanzas.data.CategoryBreakdown
import com.asura.finanzas.data.CategorySlice
import com.asura.finanzas.data.DashboardSummary
import com.asura.finanzas.data.SavingsGoal
import com.asura.finanzas.data.Subscription
import com.asura.finanzas.ui.components.Dot
import com.asura.finanzas.ui.components.BreakdownDonut
import com.asura.finanzas.ui.components.LegendBelowDonut
import com.asura.finanzas.ui.components.DonutSlice
import com.asura.finanzas.ui.components.GlassCard
import com.asura.finanzas.ui.components.ProgressBar
import com.asura.finanzas.ui.components.RingGauge
import com.asura.finanzas.ui.components.chartColor
import com.asura.finanzas.ui.formatMoney
import com.asura.finanzas.ui.maskIfHidden
import com.asura.finanzas.ui.seedName
import com.asura.finanzas.ui.parseHexColor
import com.asura.finanzas.ui.theme.Broke

/**
 * Card title with an optional "View all" affordance, as on the web.
 *
 * The end padding is the web's `pr-7`: it keeps "View all" clear of the drag
 * grip, which floats in the card's corner rather than sitting in this row. The
 * trailing gap is the header's own `mb-4`, so every widget spaces its content
 * the same way.
 */
@Composable
private fun WidgetHeader(title: String, onViewAll: (() -> Unit)? = null) {
    val colors = Broke.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(end = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            // A widget heading is `font-display text-lg font-medium`.
            style = MaterialTheme.typography.titleLarge,
            color = colors.fg,
            modifier = Modifier.weight(1f),
        )
        if (onViewAll != null) {
            Text(
                stringResource(R.string.dashboard_view_all),
                style = MaterialTheme.typography.labelLarge,
                color = colors.accent,
                modifier = Modifier.padding(start = 12.dp).clickable { onViewAll() },
            )
        }
    }
    Spacer(Modifier.height(16.dp))
}

/** Spending vs income split by category — the web's BreakdownWidget. */
@Composable
fun BreakdownWidget(
    title: String,
    breakdown: CategoryBreakdown,
    hide: Boolean,
    /** Tapping a slice drills into the movements behind it. */
    onSlice: ((CategorySlice) -> Unit)? = null,
) {
    // The web caps this one on the phone (`mobileMaxHeight`) and lets a long
    // category list scroll inside instead of growing the page.
    GlassCard(Modifier.fillMaxWidth().heightIn(max = 340.dp)) {
        WidgetHeader(title)
        BreakdownDonut(
            slices = breakdown.slices.mapIndexed { index, slice ->
                DonutSlice(
                    // The worker labels the null-category slice "Sin categoría"
                    // whatever the locale, so that bucket is named here instead.
                    label = if (slice.categoryId == null) {
                        stringResource(R.string.dashboard_uncategorized)
                    } else {
                        seedName(slice.name).orEmpty()
                    },
                    valueCents = slice.mxnCents,
                    color = parseHexColor(slice.color) ?: chartColor(index),
                    formatted = maskIfHidden(formatMoney(slice.mxnCents), hide),
                )
            },
            centerLabel = stringResource(R.string.dashboard_total),
            centerValue = maskIfHidden(formatMoney(breakdown.totalMxnCents), hide),
            onSliceClick = onSlice?.let { handler ->
                { index -> breakdown.slices.getOrNull(index)?.let(handler) }
            },
        )
    }
}

/** Balances split by wallet. */
@Composable
fun ByWalletWidget(
    summary: DashboardSummary,
    hide: Boolean,
) {
    GlassCard(Modifier.fillMaxWidth()) {
        WidgetHeader(stringResource(R.string.dashboard_by_wallet))
        LegendBelowDonut(
            // The web charts only wallets in the black; a card you owe money on
            // has no slice, and the colours are indexed after that filter.
            slices = summary.wallets
                .filter { it.balanceMxnCents > 0 }
                .mapIndexed { index, wallet ->
                    DonutSlice(
                        label = wallet.name,
                        valueCents = wallet.balanceMxnCents,
                        color = parseHexColor(wallet.color) ?: chartColor(index),
                        formatted = maskIfHidden(formatMoney(wallet.balanceMxnCents), hide),
                    )
                },
        )
    }
}

/** Value split by investment. */
@Composable
fun ByInvestmentWidget(
    summary: DashboardSummary,
    hide: Boolean,
) {
    GlassCard(Modifier.fillMaxWidth()) {
        WidgetHeader(stringResource(R.string.dashboard_by_investment))
        LegendBelowDonut(
            slices = summary.investments
                .filter { it.valueMxnCents > 0 }
                .mapIndexed { index, slice ->
                    DonutSlice(
                        label = slice.name,
                        valueCents = slice.valueMxnCents,
                        color = chartColor(index),
                        formatted = maskIfHidden(formatMoney(slice.valueMxnCents), hide),
                    )
                },
        )
    }
}

/** Budgets, shown as the web's "spending limit" list. */
@Composable
fun BudgetWidget(
    budgets: List<Budget>,
    hide: Boolean,
    onViewAll: () -> Unit,
) {
    val colors = Broke.colors
    GlassCard(Modifier.fillMaxWidth()) {
        WidgetHeader(stringResource(R.string.budgets_spending_limit), onViewAll)
        budgets.take(6).forEach { budget ->
            val over = budget.spentMxnCents > budget.limitCents
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Dot(parseHexColor(budget.color) ?: colors.accent)
                Spacer(Modifier.width(10.dp))
                Text(
                    seedName(budget.categoryName) ?: stringResource(R.string.budgets_overall),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.fgMuted,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    maskIfHidden(formatMoney(budget.spentMxnCents), hide) + " / " +
                        maskIfHidden(formatMoney(budget.limitCents), hide),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (over) colors.danger else colors.fgSubtle,
                )
            }
            Spacer(Modifier.height(6.dp))
            ProgressBar(budget.progressBps, over = over)
        }
    }
}

/** Top goal as a ring, the rest as slim bars — the web's GoalsWidget. */
@Composable
fun GoalsWidget(
    goals: List<SavingsGoal>,
    hide: Boolean,
    onViewAll: () -> Unit,
) {
    val colors = Broke.colors
    val lead = goals.firstOrNull() ?: return

    GlassCard(Modifier.fillMaxWidth()) {
        WidgetHeader(stringResource(R.string.goals_title), onViewAll)
        RingGauge(
            progressBps = lead.progressBps,
            centerValue = maskIfHidden(formatMoney(lead.savedCents, lead.currencyCode), hide),
            centerCaption = "${stringResource(R.string.goals_of)} " +
                maskIfHidden(formatMoney(lead.targetCents, lead.currencyCode), hide) +
                " · ${lead.name}",
            color = parseHexColor(lead.color),
        )
        // The web lists three runners-up, each in its own colour.
        goals.drop(1).take(3).forEachIndexed { index, goal ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    goal.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = colors.fgMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    maskIfHidden(formatMoney(goal.savedCents, goal.currencyCode), hide) +
                        " ${stringResource(R.string.goals_of)} " +
                        maskIfHidden(formatMoney(goal.targetCents, goal.currencyCode), hide),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = colors.fgSubtle,
                )
            }
            Spacer(Modifier.height(4.dp))
            ProgressBar(goal.progressBps, color = parseHexColor(goal.color) ?: chartColor(index + 1))
        }
    }
}

/** Upcoming charges — the web's SubscriptionsWidget. */
@Composable
fun SubscriptionsWidget(
    subscriptions: List<Subscription>,
    monthlyTotalMxnCents: Long,
    hide: Boolean,
    onViewAll: () -> Unit,
) {
    val colors = Broke.colors
    val active = subscriptions.filter { it.isActive }
    if (active.isEmpty()) return

    GlassCard(Modifier.fillMaxWidth()) {
        WidgetHeader(stringResource(R.string.subscriptions_title), onViewAll)
        Text(
            "${stringResource(R.string.subscriptions_monthly_total)}: " +
                maskIfHidden(formatMoney(monthlyTotalMxnCents), hide),
            style = MaterialTheme.typography.labelSmall,
            color = colors.fgSubtle,
        )
        active.take(6).forEach { subscription ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Dot(parseHexColor(subscription.color) ?: colors.accent)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        subscription.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.fgMuted,
                    )
                    Text(
                        subscription.nextChargeDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.fgSubtle,
                    )
                }
                Text(
                    maskIfHidden(
                        formatMoney(subscription.amountCents, subscription.currencyCode),
                        hide,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.fg,
                )
            }
        }
    }
}
