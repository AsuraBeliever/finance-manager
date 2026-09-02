package com.asura.finanzas.ui.settings

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
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.asura.finanzas.ui.components.FormField
import com.asura.finanzas.R
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.ExchangeRate
import com.asura.finanzas.data.NetworkException
import com.asura.finanzas.ui.components.BackHeader
import com.asura.finanzas.ui.components.ErrorBox
import com.asura.finanzas.ui.components.GlassCard
import com.asura.finanzas.ui.components.Load
import com.asura.finanzas.ui.components.LoadingBox
import com.asura.finanzas.ui.components.OfflineNotice
import com.asura.finanzas.ui.components.PrimaryButton
import com.asura.finanzas.ui.components.loadSynced
import com.asura.finanzas.ui.components.rememberReloadKey
import com.asura.finanzas.ui.text
import com.asura.finanzas.ui.theme.Broke
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

@Composable
fun CurrenciesScreen(
    repository: BrokeRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (key, reload) = rememberReloadKey()
    val state by loadSynced(key) { repository.exchangeRates() }
    val scope = rememberCoroutineScope()

    // Which currency is being pinned by hand, if any. MXN is excluded: the
    // server rejects it because it is always 1.0 by definition.
    var editing by remember { mutableStateOf<ExchangeRate?>(null) }

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
            onEdit = { editing = it },
            modifier = modifier,
        )
    }

    editing?.let { rate ->
        ManualRateDialog(
            repository = repository,
            rate = rate,
            onDismiss = { editing = null },
            onSaved = { editing = null; reload() },
        )
    }
}

@Composable
private fun RateList(
    rates: List<ExchangeRate>,
    fromCache: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onEdit: (ExchangeRate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Broke.colors

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 28.dp),
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
            GlassCard(
                Modifier
                    .fillMaxWidth()
                    .then(
                        // MXN is fixed at 1.0, so it is the one row that never opens.
                        if (rate.currencyCode == "MXN") Modifier
                        else Modifier.clickable { onEdit(rate) },
                    ),
                padding = 16.dp,
            ) {
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

/**
 * Pin one currency's rate by hand. The typed decimal is scaled to micros here
 * only to shape the request — the conversion of any actual money still happens
 * server-side in Rust.
 */
@Composable
private fun ManualRateDialog(
    repository: BrokeRepository,
    rate: ExchangeRate,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val colors = Broke.colors
    val scope = rememberCoroutineScope()
    var value by remember { mutableStateOf(formatRate(rate.rateToMxnMicros)) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val invalidMessage = text(R.string.settings_rate_manual_invalid)
    val genericError = stringResource(R.string.common_error)
    val offlineError = stringResource(R.string.offline_banner)

    val micros = runCatching {
        BigDecimal(value.trim().replace(',', '.'))
            .movePointRight(6)
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()
    }.getOrNull()
    val valid = micros != null && micros > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceOverlay,
        title = { Text(stringResource(R.string.settings_rate_manual_title), color = colors.fg) },
        text = {
            Column {
                Text(
                    text(R.string.settings_rate_manual_hint, "code" to rate.currencyCode),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.fgSubtle,
                )
                Spacer(Modifier.height(12.dp))
                FormField(
                    label = text(R.string.settings_rate_manual_label, "code" to rate.currencyCode),
                    value = value,
                    onValueChange = { value = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = value.isNotBlank() && !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = colors.danger)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid && !busy,
                onClick = {
                    val amount = micros ?: return@TextButton
                    busy = true
                    error = null
                    scope.launch {
                        runCatching { repository.setExchangeRate(rate.currencyCode, amount) }
                            .onSuccess { onSaved() }
                            .onFailure {
                                error = when (it) {
                                    is NetworkException -> offlineError
                                    else -> it.message ?: genericError
                                }
                                busy = false
                            }
                    }
                },
            ) { Text(stringResource(R.string.common_save), color = colors.accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel), color = colors.fgMuted)
            }
        },
    )
}
