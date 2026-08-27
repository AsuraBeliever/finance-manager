package com.asura.finanzas.ui.subscriptions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.asura.finanzas.ui.components.DialogAction
import com.asura.finanzas.ui.components.GlassCard
import com.asura.finanzas.ui.components.PrimaryButton
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
import kotlinx.coroutines.launch

@Composable
fun SubscriptionsScreen(
    repository: BrokeRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (key, reload) = rememberReloadKey()
    val state by loadSynced(key) { repository.subscriptions() }
    val scope = rememberCoroutineScope()

    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Subscription?>(null) }
    var actionsFor by remember { mutableStateOf<Subscription?>(null) }

    when (val current = state) {
        is Load.Loading -> LoadingBox(modifier)
        is Load.Failed -> ErrorBox(current.message, reload, modifier)
        is Load.Ready -> SubscriptionContent(
            data = current.data,
            fromCache = current.fromCache,
            onBack = onBack,
            onNew = { creating = true },
            onLongPress = { actionsFor = it },
            modifier = modifier,
        )
    }

    if (creating || editing != null) {
        SubscriptionFormSheet(
            repository = repository,
            existing = editing,
            onDismiss = { creating = false; editing = null },
            onSaved = { creating = false; editing = null; reload() },
        )
    }

    actionsFor?.let { target ->
        AlertDialog(
            onDismissRequest = { actionsFor = null },
            containerColor = Broke.colors.surfaceOverlay,
            title = { Text(target.name, color = Broke.colors.fg) },
            text = {
                Column {
                    // Posting the charge needs a wallet to take it from, so the
                    // action is only offered when one is set — same rule as the
                    // server's.
                    if (target.walletId != null) {
                        DialogAction(stringResource(R.string.subscriptions_register_payment)) {
                            actionsFor = null
                            scope.launch {
                                runCatching { repository.registerSubscriptionPayment(target.id) }
                                reload()
                            }
                        }
                    }
                    DialogAction(stringResource(R.string.common_edit)) {
                        actionsFor = null
                        editing = target
                    }
                    DialogAction(
                        stringResource(
                            if (target.isActive) R.string.subscriptions_pause
                            else R.string.subscriptions_resume,
                        ),
                    ) {
                        actionsFor = null
                        scope.launch {
                            runCatching {
                                repository.setSubscriptionActive(target.id, !target.isActive)
                            }
                            reload()
                        }
                    }
                    DialogAction(stringResource(R.string.common_delete), Broke.colors.danger) {
                        actionsFor = null
                        scope.launch {
                            runCatching { repository.deleteSubscription(target.id) }
                            reload()
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { actionsFor = null }) {
                    Text(stringResource(R.string.common_close), color = Broke.colors.fgMuted)
                }
            },
        )
    }
}

@Composable
private fun SubscriptionContent(
    data: SubscriptionList,
    fromCache: Boolean,
    onBack: () -> Unit,
    onNew: () -> Unit,
    onLongPress: (Subscription) -> Unit,
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
        item {
            BackHeader(stringResource(R.string.subscriptions_title), onBack)
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                text = stringResource(R.string.subscriptions_new_subscription),
                onClick = onNew,
                leadingIcon = Icons.Outlined.Add,
            )
        }

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

        items(active, key = { it.id }) { SubscriptionCard(it, hide, onLongPress) }

        if (paused.isNotEmpty()) {
            item {
                MicroLabel(
                    stringResource(R.string.subscriptions_paused),
                    Modifier.padding(top = 8.dp),
                )
            }
        }
        items(paused, key = { "paused-${it.id}" }) { SubscriptionCard(it, hide, onLongPress) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SubscriptionCard(
    subscription: Subscription,
    hide: Boolean,
    onLongPress: (Subscription) -> Unit,
) {
    val colors = Broke.colors
    GlassCard(
        Modifier.fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = { onLongPress(subscription) }),
    ) {
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
