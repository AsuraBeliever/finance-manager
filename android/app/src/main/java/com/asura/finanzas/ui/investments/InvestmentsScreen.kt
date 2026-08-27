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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
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
import com.asura.finanzas.ui.components.ErrorBox
import com.asura.finanzas.ui.components.GlassCard
import com.asura.finanzas.ui.components.HairLine
import com.asura.finanzas.ui.components.HeroAmount
import com.asura.finanzas.ui.components.Load
import com.asura.finanzas.ui.components.LoadingBox
import com.asura.finanzas.ui.components.MicroLabel
import com.asura.finanzas.ui.components.OfflineNotice
import com.asura.finanzas.ui.components.PageHeader
import com.asura.finanzas.ui.components.EmptyState
import com.asura.finanzas.ui.components.loadSynced
import com.asura.finanzas.ui.components.rememberReloadKey
import com.asura.finanzas.ui.formatDelta
import com.asura.finanzas.ui.formatMoney
import com.asura.finanzas.ui.maskIfHidden
import com.asura.finanzas.ui.theme.Broke

@Composable
fun InvestmentsScreen(repository: BrokeRepository, modifier: Modifier = Modifier) {
    val (key, reload) = rememberReloadKey()
    val state by loadSynced(key) { repository.investments() }
    val portfolio by produceState<Portfolio?>(initialValue = null, key) {
        value = runCatching { repository.portfolio().value }.getOrNull()
    }

    var openId by remember { mutableStateOf<Long?>(null) }

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
            onOpen = { openId = it.id },
            modifier = modifier,
        )
    }
}

@Composable
private fun InvestmentList(
    investments: List<Investment>,
    portfolio: Portfolio?,
    fromCache: Boolean,
    onOpen: (Investment) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Broke.colors
    val hide = LocalAppSettings.current.hideBalances
    val open = investments.filter { !it.isClosed }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { PageHeader(stringResource(R.string.investments_title)) }

        if (fromCache) {
            item { OfflineNotice(stringResource(R.string.offline_banner), Modifier.fillMaxWidth()) }
        }

        if (portfolio != null) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    MicroLabel(stringResource(R.string.investments_portfolio_value))
                    Spacer(Modifier.height(4.dp))
                    HeroAmount(
                        maskIfHidden(formatMoney(portfolio.totalValueCents), hide),
                        fontSize = 34.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                    HairLine()
                    Spacer(Modifier.height(12.dp))
                    StatLine(
                        stringResource(R.string.investments_portfolio_invested),
                        maskIfHidden(formatMoney(portfolio.totalInvestedCents), hide),
                    )
                    Spacer(Modifier.height(6.dp))
                    StatLine(
                        stringResource(R.string.investments_gain),
                        maskIfHidden(formatDelta(portfolio.totalGainCents), hide),
                        valueColor = if (portfolio.totalGainCents >= 0) colors.positive else colors.danger,
                    )
                    portfolio.annualizedReturnBps?.let { bps ->
                        Spacer(Modifier.height(6.dp))
                        StatLine(
                            stringResource(R.string.investments_annualized_return),
                            // Basis points to percent is presentation only.
                            "%.2f%%".format(bps / 100.0),
                        )
                    }
                }
            }
        }

        if (open.isEmpty()) {
            item {
                EmptyState(
                    stringResource(R.string.investments_empty_title),
                    stringResource(R.string.investments_empty_description),
                )
            }
        }

        items(open, key = { it.id }) { investment ->
            InvestmentCard(investment, hide, onOpen)
        }
    }
}

@Composable
private fun InvestmentCard(
    investment: Investment,
    hide: Boolean,
    onOpen: (Investment) -> Unit,
) {
    val colors = Broke.colors
    GlassCard(Modifier.fillMaxWidth().clickable { onOpen(investment) }) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(
                    investment.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.fg,
                )
                Text(
                    investment.currencyCode,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.fgSubtle,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    maskIfHidden(formatMoney(investment.currentValueCents, investment.currencyCode), hide),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.fg,
                )
                Text(
                    maskIfHidden(formatDelta(investment.gainCents, investment.currencyCode), hide),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (investment.gainCents >= 0) colors.positive else colors.danger,
                )
            }
        }
    }
}

@Composable
private fun StatLine(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color? = null,
) {
    val colors = Broke.colors
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.fgMuted,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodyLarge, color = valueColor ?: colors.fg)
    }
}
