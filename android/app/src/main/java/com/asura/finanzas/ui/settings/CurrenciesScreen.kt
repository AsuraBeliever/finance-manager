package com.asura.finanzas.ui.settings

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
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.asura.finanzas.R
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.ExchangeRate
import com.asura.finanzas.ui.components.BackHeader
import com.asura.finanzas.ui.components.ErrorBox
import com.asura.finanzas.ui.components.GlassCard
import com.asura.finanzas.ui.components.Load
import com.asura.finanzas.ui.components.LoadingBox
import com.asura.finanzas.ui.components.OfflineNotice
import com.asura.finanzas.ui.components.PrimaryButton
import com.asura.finanzas.ui.components.loadSynced
import com.asura.finanzas.ui.components.rememberReloadKey
import com.asura.finanzas.ui.theme.Broke
import kotlinx.coroutines.launch
import java.math.BigDecimal

@Composable
fun CurrenciesScreen(
    repository: BrokeRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (key, reload) = rememberReloadKey()
    val state by loadSynced(key) { repository.exchangeRates() }
    val scope = rememberCoroutineScope()

    when (val current = state) {
        is Load.Loading -> LoadingBox(modifier)
        is Load.Failed -> ErrorBox(current.message, reload, modifier)
        is Load.Ready -> RateList(
            rates = current.data,
            fromCache = current.fromCache,
            onBack = onBack,
            onRefresh = {
                scope.launch {
                    runCatching { repository.fetchExchangeRates() }
                    reload()
                }
            },
            modifier = modifier,
        )
    }
}

@Composable
private fun RateList(
    rates: List<ExchangeRate>,
    fromCache: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Broke.colors

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            BackHeader(stringResource(R.string.settings_currencies), onBack)
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                text = stringResource(R.string.common_refresh),
                onClick = onRefresh,
                leadingIcon = Icons.Outlined.Refresh,
            )
        }

        if (fromCache) {
            item { OfflineNotice(stringResource(R.string.offline_banner), Modifier.fillMaxWidth()) }
        }

        items(rates, key = { it.currencyCode }) { rate ->
            GlassCard(Modifier.fillMaxWidth(), padding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            rate.currencyCode,
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.fg,
                        )
                        Text(
                            listOfNotNull(
                                rate.asOf.takeIf { it.isNotBlank() },
                                rate.source.takeIf { it.isNotBlank() },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.fgSubtle,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        formatRate(rate.rateToMxnMicros),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.fg,
                    )
                }
            }
        }
    }
}

/**
 * Rates are stored in micros. This only moves the decimal point for display —
 * every conversion that touches money still happens server-side in Rust.
 */
private fun formatRate(micros: Long): String =
    BigDecimal.valueOf(micros, 6).stripTrailingZeros().toPlainString()
