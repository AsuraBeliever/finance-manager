package com.asura.finanzas.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.DashboardSummary
import com.asura.finanzas.data.WalletBalance
import com.asura.finanzas.ui.components.ErrorBox
import com.asura.finanzas.ui.components.GlassCard
import com.asura.finanzas.ui.components.HeroAmount
import com.asura.finanzas.ui.components.Load
import com.asura.finanzas.ui.components.LoadingBox
import com.asura.finanzas.ui.components.OfflineNotice
import com.asura.finanzas.ui.components.SectionTitle
import com.asura.finanzas.ui.components.loadSynced
import com.asura.finanzas.ui.components.rememberReloadKey
import com.asura.finanzas.ui.formatDelta
import com.asura.finanzas.ui.formatMoney
import com.asura.finanzas.ui.parseHexColor
import com.asura.finanzas.ui.theme.Broke

@Composable
fun DashboardScreen(repository: BrokeRepository, modifier: Modifier = Modifier) {
    val (key, reload) = rememberReloadKey()
    val state by loadSynced(key) { repository.dashboard() }

    when (val current = state) {
        is Load.Loading -> LoadingBox(modifier)
        is Load.Failed -> ErrorBox(current.message, reload, modifier)
        is Load.Ready -> DashboardContent(current.data, current.fromCache, modifier)
    }
}

@Composable
private fun DashboardContent(
    summary: DashboardSummary,
    fromCache: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = Broke.colors
    // Every figure below arrives already computed by the server. The only
    // derivation here is the period delta, which is a subtraction of two totals
    // the API itself returns as a matched pair.
    val cashDelta = summary.totalEndMxnCents - summary.totalStartMxnCents
    val netWorth = summary.totalEndMxnCents + summary.investmentsTotalMxnCents

    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp, end = 20.dp, top = 24.dp, bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (fromCache) {
            item { OfflineNotice(Modifier.fillMaxWidth()) }
        }

        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Patrimonio",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.fgMuted,
                )
                Spacer(Modifier.height(6.dp))
                HeroAmount(formatMoney(netWorth))
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${formatDelta(cashDelta)} en efectivo este periodo",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (cashDelta >= 0) colors.positive else colors.danger,
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatCard("Efectivo", formatMoney(summary.totalEndMxnCents), Modifier.weight(1f))
                StatCard("Inversiones", formatMoney(summary.investmentsTotalMxnCents), Modifier.weight(1f))
            }
        }

        if (summary.missingRates.isNotEmpty()) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(
                        text = "Falta el tipo de cambio de ${summary.missingRates.joinToString(", ")}. " +
                            "Esos saldos no están sumados en pesos.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.fgMuted,
                    )
                }
            }
        }

        if (summary.wallets.isNotEmpty()) {
            item { SectionTitle("Carteras") }
            items(summary.wallets, key = { it.walletId }) { wallet ->
                WalletRow(wallet)
            }
        }

        if (summary.investments.isNotEmpty()) {
            item { SectionTitle("Inversiones") }
            items(summary.investments, key = { it.id }) { slice ->
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(slice.name, style = MaterialTheme.typography.bodyLarge, color = colors.fg)
                        Text(
                            formatMoney(slice.valueMxnCents),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.fg,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = Broke.colors
    GlassCard(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.fgMuted)
        Spacer(Modifier.height(6.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = colors.fg)
    }
}

@Composable
private fun WalletRow(wallet: WalletBalance) {
    val colors = Broke.colors
    GlassCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(parseHexColor(wallet.color) ?: colors.accent),
            )
            Spacer(Modifier.padding(horizontal = 6.dp))
            Column(Modifier.weight(1f)) {
                Text(wallet.name, style = MaterialTheme.typography.bodyLarge, color = colors.fg)
                if (wallet.currencyCode != "MXN") {
                    Text(
                        formatMoney(wallet.balanceCents, wallet.currencyCode),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.fgSubtle,
                    )
                }
            }
            Text(
                formatMoney(wallet.balanceMxnCents),
                style = MaterialTheme.typography.labelLarge,
                color = if (wallet.balanceMxnCents < 0) colors.danger else colors.fg,
            )
        }
    }
}
