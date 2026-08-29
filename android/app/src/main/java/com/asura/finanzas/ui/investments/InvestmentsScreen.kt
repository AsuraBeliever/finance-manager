package com.asura.finanzas.ui.investments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Add
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asura.finanzas.R
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.Investment
import com.asura.finanzas.data.Portfolio
import com.asura.finanzas.ui.LocalAppSettings
import com.asura.finanzas.ui.components.DonutChart
import com.asura.finanzas.ui.components.DonutSlice
import com.asura.finanzas.ui.components.EmptyState
import com.asura.finanzas.ui.components.ErrorBox
import com.asura.finanzas.ui.components.GlassCard
import com.asura.finanzas.ui.components.Load
import com.asura.finanzas.ui.components.LoadingBox
import com.asura.finanzas.ui.components.OfflineNotice
import com.asura.finanzas.ui.components.PageHeader
import com.asura.finanzas.ui.components.PrivacyToggle
import com.asura.finanzas.ui.components.PrimaryButton
import com.asura.finanzas.ui.components.chartColor
import com.asura.finanzas.ui.components.loadSynced
import com.asura.finanzas.ui.components.rememberReloadKey
import com.asura.finanzas.ui.formatDelta
import com.asura.finanzas.ui.formatMoney
import com.asura.finanzas.ui.maskIfHidden
import com.asura.finanzas.ui.theme.Broke

@Composable
fun InvestmentsScreen(repository: BrokeRepository, modifier: Modifier = Modifier) {
    val (key, reload) = rememberReloadKey()
    var showClosed by remember { mutableStateOf(false) }
    val state by loadSynced(key to showClosed) { repository.investments(includeClosed = showClosed) }
    val portfolio by produceState<Portfolio?>(initialValue = null, key) {
        value = runCatching { repository.portfolio().value }.getOrNull()
    }

    var openId by remember { mutableStateOf<Long?>(null) }
    var creating by remember { mutableStateOf(false) }
    var simulating by remember { mutableStateOf(false) }

    if (simulating) {
        SimulatorScreen(
            repository = repository,
            onBack = { simulating = false },
            modifier = modifier,
        )
        return
    }

    val id = openId
    if (id != null) {
        InvestmentDetailScreen(
            repository = repository,
            investmentId = id,
            onBack = { openId = null; reload() },
            modifier = modifier,
        )
        return
    }

    when (val current = state) {
        is Load.Loading -> LoadingBox(modifier)
        is Load.Failed -> ErrorBox(current.message, reload, modifier)
        is Load.Ready -> InvestmentList(
            investments = current.data,
            portfolio = portfolio,
            fromCache = current.fromCache,
            showClosed = showClosed,
            onToggleClosed = { showClosed = !showClosed },
            onNew = { creating = true },
            onOpen = { openId = it.id },
            onSimulator = { simulating = true },
            modifier = modifier,
        )
    }

    if (creating) {
        NewInvestmentSheet(
            repository = repository,
            onDismiss = { creating = false },
            onSaved = { creating = false; reload() },
        )
    }
}

@Composable
private fun InvestmentList(
    investments: List<Investment>,
    portfolio: Portfolio?,
    fromCache: Boolean,
    showClosed: Boolean,
    onToggleClosed: () -> Unit,
    onNew: () -> Unit,
    onOpen: (Investment) -> Unit,
    onSimulator: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Broke.colors
    val hide = LocalAppSettings.current.hideBalances
    val shown = if (showClosed) investments else investments.filter { !it.isClosed }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            PageHeader(stringResource(R.string.investments_title)) {
                PrivacyToggle()
                PrimaryButton(
                    text = stringResource(R.string.investments_new_investment),
                    onClick = onNew,
                    leadingIcon = Icons.Outlined.Add,
                )
                Text(
                    stringResource(R.string.simulator_open),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.accent,
                    modifier = Modifier.clickable { onSimulator() },
                )
                Text(
                    stringResource(R.string.investments_show_closed),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (showClosed) colors.accent else colors.fgMuted,
                    modifier = Modifier.clickable { onToggleClosed() },
                )
            }
        }

        if (fromCache) {
            item { OfflineNotice(stringResource(R.string.offline_banner), Modifier.fillMaxWidth()) }
        }

        if (portfolio != null) {
            item { PortfolioCard(portfolio, hide) }
        }

        if (shown.isEmpty()) {
            item {
                EmptyState(
                    stringResource(R.string.investments_empty_title),
                    stringResource(R.string.investments_empty_description),
                )
            }
        }

        items(shown, key = { it.id }) { investment -> InvestmentCard(investment, hide, onOpen) }
    }
}

/**
 * The web's portfolio summary: four figures in a 2×2 grid over a donut of the
 * open positions. Every number is the server's; the annualised return in
 * particular is finanzas-core's XIRR, never recomputed here.
 */
@Composable
private fun PortfolioCard(portfolio: Portfolio, hide: Boolean) {
    val colors = Broke.colors

    GlassCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Stat(
                stringResource(R.string.investments_portfolio_value),
                maskIfHidden(formatMoney(portfolio.totalValueCents), hide),
                colors.accent,
                Modifier.weight(1f),
            )
            Stat(
                stringResource(R.string.investments_portfolio_invested),
                maskIfHidden(formatMoney(portfolio.totalInvestedCents), hide),
                colors.fg,
                Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth()) {
            Stat(
                stringResource(R.string.investments_portfolio_gain),
                maskIfHidden(formatDelta(portfolio.totalGainCents), hide),
                if (portfolio.totalGainCents >= 0) colors.positive else colors.danger,
                Modifier.weight(1f),
            )
            Stat(
                stringResource(R.string.investments_annualized_return),
                portfolio.annualizedReturnBps
                    // Basis points to a percentage is presentation only.
                    ?.let { (if (it >= 0) "+" else "") + "%.1f%%".format(it / 100.0) }
                    ?: "—",
                if ((portfolio.annualizedReturnBps ?: 0) >= 0) colors.positive else colors.danger,
                Modifier.weight(1f),
            )
        }

        if (portfolio.slices.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            DonutChart(
                slices = portfolio.slices.mapIndexed { index, slice ->
                    DonutSlice(
                        label = slice.name,
                        valueCents = slice.currentValueCents,
                        color = chartColor(index),
                        formatted = maskIfHidden(formatMoney(slice.currentValueCents), hide),
                    )
                },
                centerLabel = stringResource(R.string.dashboard_total),
                centerValue = maskIfHidden(formatMoney(portfolio.totalValueCents), hide),
                showValues = false,
            )
        }
    }
}

@Composable
private fun Stat(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Broke.colors.fgMuted)
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 22.sp),
            color = valueColor,
        )
    }
}

/**
 * Name, value, gain, then the calculator and maturity as a caption — the web's
 * card, which leads with the money rather than the currency code.
 */
@Composable
private fun InvestmentCard(investment: Investment, hide: Boolean, onOpen: (Investment) -> Unit) {
    val colors = Broke.colors

    GlassCard(Modifier.fillMaxWidth().clickable { onOpen(investment) }) {
        Text(investment.name, style = MaterialTheme.typography.titleMedium, color = colors.fg)
        Spacer(Modifier.height(8.dp))
        Text(
            maskIfHidden(formatMoney(investment.currentValueCents, investment.currencyCode), hide),
            style = MaterialTheme.typography.headlineMedium,
            color = colors.fg,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Outlined.TrendingUp,
                contentDescription = null,
                tint = if (investment.gainCents >= 0) colors.accent else colors.danger,
                modifier = Modifier.width(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                maskIfHidden(formatDelta(investment.gainCents, investment.currencyCode), hide),
                style = MaterialTheme.typography.bodyLarge,
                color = if (investment.gainCents >= 0) colors.accent else colors.danger,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            listOfNotNull(
                calculatorLabel(investment.calculator),
                investment.maturityDate?.let {
                    "${stringResource(R.string.investments_maturity)}: $it"
                },
                if (investment.isClosed) stringResource(R.string.investments_closed) else null,
            ).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = colors.fgSubtle,
        )
    }
}

/** The calculator's display name, from the shared dictionary. */
@Composable
private fun calculatorLabel(calculator: String): String = stringResource(
    when (calculator) {
        "nu_cajita" -> R.string.investments_calculators_nu_cajita
        "cetes" -> R.string.investments_calculators_cetes
        "bonddia" -> R.string.investments_calculators_bonddia
        "crypto" -> R.string.investments_calculators_crypto
        "fixed_rate" -> R.string.investments_calculators_fixed_rate
        else -> R.string.investments_calculators_manual
    },
)
