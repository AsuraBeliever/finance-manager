package com.asura.finanzas.ui.subscriptions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asura.finanzas.R
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.Subscription
import com.asura.finanzas.data.SubscriptionList
import com.asura.finanzas.ui.LocalAppSettings
import com.asura.finanzas.ui.components.BackHeader
import com.asura.finanzas.ui.components.Dot
import com.asura.finanzas.ui.components.EmptyState
import com.asura.finanzas.ui.components.ErrorBox
import com.asura.finanzas.ui.components.GlassCard
import com.asura.finanzas.ui.components.HeroAmount
import com.asura.finanzas.ui.components.Load
import com.asura.finanzas.ui.components.LoadingBox
import com.asura.finanzas.ui.components.MicroLabel
import com.asura.finanzas.ui.components.OfflineNotice
import com.asura.finanzas.ui.components.loadSynced
import com.asura.finanzas.ui.components.rememberReloadKey
import com.asura.finanzas.ui.formatMoney
import com.asura.finanzas.ui.maskIfHidden
import com.asura.finanzas.ui.parseHexColor
import com.asura.finanzas.ui.theme.Broke

@Composable
fun SubscriptionsScreen(
    repository: BrokeRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (key, reload) = rememberReloadKey()
    val state by loadSynced(key) { repository.subscriptions() }

    when (val current = state) {
        is Load.Loading -> LoadingBox(modifier)
        is Load.Failed -> ErrorBox(current.message, reload, modifier)
        is Load.Ready -> SubscriptionContent(current.data, current.fromCache, onBack, modifier)
    }
}

@Composable
private fun SubscriptionContent(
    data: SubscriptionList,
    fromCache: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hide = LocalAppSettings.current.hideBalances
    val active = data.subscriptions.filter { it.isActive }
    val paused = data.subscriptions.filter { !it.isActive }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { BackHeader(stringResource(R.string.subscriptions_title), onBack) }

        if (fromCache) {
            item { OfflineNotice(stringResource(R.string.offline_banner), Modifier.fillMaxWidth()) }
        }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                MicroLabel(stringResource(R.string.subscriptions_monthly_total))
                Spacer(Modifier.height(4.dp))
                HeroAmount(
                    maskIfHidden(formatMoney(data.monthlyTotalMxnCents), hide),
                    fontSize = 32.sp,
                )
            }
        }

        if (data.subscriptions.isEmpty()) {
            item {
                EmptyState(
                    stringResource(R.string.subscriptions_empty_title),
                    stringResource(R.string.subscriptions_empty_description),
                )
            }
        }

        items(active, key = { it.id }) { SubscriptionCard(it, hide) }

        if (paused.isNotEmpty()) {
            item {
                MicroLabel(
                    stringResource(R.string.subscriptions_paused),
                    Modifier.padding(top = 8.dp),
                )
            }
        }
        items(paused, key = { "paused-${it.id}" }) { SubscriptionCard(it, hide) }
    }
}

@Composable
private fun SubscriptionCard(subscription: Subscription, hide: Boolean) {
    val colors = Broke.colors
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Dot(parseHexColor(subscription.color) ?: colors.accent, 12.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    subscription.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (subscription.isActive) colors.fg else colors.fgSubtle,
                )
                Text(
                    "${stringResource(R.string.subscriptions_next_charge)}: ${subscription.nextChargeDate}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.fgSubtle,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    maskIfHidden(
                        formatMoney(subscription.amountCents, subscription.currencyCode),
                        hide,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.fg,
                )
                Text(
                    stringResource(
                        if (subscription.cadence == "yearly") {
                            R.string.subscriptions_yearly
                        } else {
                            R.string.subscriptions_monthly
                        },
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.fgSubtle,
                )
            }
        }
    }
}
