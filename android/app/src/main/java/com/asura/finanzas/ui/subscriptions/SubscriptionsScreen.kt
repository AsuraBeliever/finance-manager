package com.asura.finanzas.ui.subscriptions

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.asura.finanzas.ui.components.PrivacyToggle
import com.asura.finanzas.ui.components.PageHeader
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.asura.finanzas.ui.components.Lucide
import com.asura.finanzas.ui.components.chartColor
import com.asura.finanzas.R
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.Subscription
import com.asura.finanzas.data.SubscriptionList
import com.asura.finanzas.ui.LocalAppSettings
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
            onPay = { target ->
                scope.launch {
                    runCatching { repository.registerSubscriptionPayment(target.id) }
                    reload()
                }
            },
            onToggle = { target ->
                scope.launch {
                    runCatching { repository.setSubscriptionActive(target.id, !target.isActive) }
                    reload()
                }
            },
            onEdit = { editing = it },
            onDelete = { target ->
                scope.launch {
                    runCatching { repository.deleteSubscription(target.id) }
                    reload()
                }
            },
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
    onPay: (Subscription) -> Unit,
    onToggle: (Subscription) -> Unit,
    onEdit: (Subscription) -> Unit,
    onDelete: (Subscription) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hide = LocalAppSettings.current.hideBalances
    val active = data.subscriptions.filter { it.isActive }
    val paused = data.subscriptions.filter { !it.isActive }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            BackHandler(onBack = onBack)
            PageHeader(stringResource(R.string.subscriptions_title)) {
                PrivacyToggle()
                PrimaryButton(
                    text = stringResource(R.string.subscriptions_new_subscription),
                    onClick = onNew,
                    leadingIcon = Lucide.Plus,
                )
            }
        }

        if (fromCache) {
            item { OfflineNotice(stringResource(R.string.offline_banner), Modifier.fillMaxWidth()) }
        }

        item {
            // A quiet line on the web, not a hero card.
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    stringResource(R.string.subscriptions_monthly_total) + ": ",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = Broke.colors.fgMuted,
                )
                Text(
                    maskIfHidden(formatMoney(data.monthlyTotalMxnCents), hide),
                    style = MaterialTheme.typography.displayLarge
                        .copy(fontSize = 16.sp, lineHeight = 20.sp),
                    color = Broke.colors.fg,
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

        // One list in the server's order — the web dims the paused ones in
        // place instead of moving them under a heading of their own.
        itemsIndexed(data.subscriptions, key = { _, it -> it.id }) { index, subscription ->
            SubscriptionCard(
                subscription, hide, index,
                onLongPress = onLongPress,
                onPay = onPay,
                onToggle = onToggle,
                onEdit = onEdit,
                onDelete = onDelete,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun SubscriptionCard(
    subscription: Subscription,
    hide: Boolean,
    index: Int,
    onLongPress: (Subscription) -> Unit,
    onPay: (Subscription) -> Unit,
    onToggle: (Subscription) -> Unit,
    onEdit: (Subscription) -> Unit,
    onDelete: (Subscription) -> Unit,
) {
    val colors = Broke.colors
    val tint = parseHexColor(subscription.color) ?: chartColor(index)
    // A paused subscription is dimmed rather than hidden, as on the web.
    val alpha = if (subscription.isActive) 1f else 0.6f

    GlassCard(
        Modifier
            .fillMaxWidth()
            .alpha(alpha)
            .combinedClickable(onClick = {}, onLongClick = { onLongPress(subscription) }),
        padding = 16.dp,
    ) {
        // One wrapping row for the lot, as the web lays it out — the four
        // buttons belong beside the amount, not on a line of their own.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // The web's badge: a colour tile carrying the service's logo, and
            // only its initial when there is no logo for the name.
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tint),
                contentAlignment = Alignment.Center,
            ) {
                val logo = brandIcon(subscription.icon)
                if (logo != null) {
                    Icon(
                        logo,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                } else {
                    Text(
                        subscription.name.take(1).uppercase(),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = Color.White,
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    subscription.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    // Cadence and next charge share one line on the web.
                    stringResource(
                        if (subscription.cadence == "yearly") R.string.subscriptions_yearly
                        else R.string.subscriptions_monthly,
                    ) + " · " + stringResource(R.string.subscriptions_next_charge) +
                        ": " + subscription.nextChargeDate,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                    color = colors.fgSubtle,
                )
            }
            Text(
                maskIfHidden(
                    formatMoney(subscription.amountCents, subscription.currencyCode),
                    hide,
                ),
                style = MaterialTheme.typography.titleMedium,
                color = colors.fg,
            )
            // Register a payment, pause/resume, edit, delete.
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                RowAction(Lucide.Receipt, subscription.walletId != null) { onPay(subscription) }
                RowAction(if (subscription.isActive) Lucide.Pause else Lucide.Play) {
                    onToggle(subscription)
                }
                RowAction(Lucide.Pencil) { onEdit(subscription) }
                RowAction(Lucide.Trash) { onDelete(subscription) }
            }
        }
    }
}

/** One of the small icon buttons on a subscription row. */
@Composable
private fun RowAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Icon(
        icon,
        contentDescription = null,
        tint = Broke.colors.fgSubtle.copy(alpha = if (enabled) 1f else 0.4f),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(6.dp)
            .size(16.dp),
    )
}
