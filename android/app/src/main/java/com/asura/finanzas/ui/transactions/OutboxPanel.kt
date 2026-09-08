package com.asura.finanzas.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.asura.finanzas.R
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.Outbox
import com.asura.finanzas.data.OutboxItem
import com.asura.finanzas.data.Wallet
import com.asura.finanzas.ui.LocalAppSettings
import com.asura.finanzas.ui.formatMoney
import com.asura.finanzas.ui.maskIfHidden
import com.asura.finanzas.ui.theme.Broke
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Captures made without signal, waiting to sync. They render apart from the
 * real list because server truth is untouched until they upload — folding them
 * into balances would be inventing money the server has never seen.
 */
@Composable
fun OutboxPanel(
    outbox: Outbox,
    repository: BrokeRepository,
    wallets: List<Wallet>,
    onSynced: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val items by outbox.items.collectAsState()
    if (items.isEmpty()) return

    val colors = Broke.colors
    val scope = rememberCoroutineScope()
    val hide = LocalAppSettings.current.hideBalances
    val currencyByWallet = wallets.associate { it.id to it.currencyCode }

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.warning.copy(alpha = 0.06f))
            .border(1.dp, colors.warning.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.CloudOff,
                contentDescription = null,
                tint = colors.warning,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "${stringResource(R.string.offline_pending_title)} (${items.size})",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.warning,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.offline_pending_hint),
            style = MaterialTheme.typography.labelSmall,
            color = colors.fgSubtle,
        )

        items.forEach { item ->
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        itemSummary(item, currencyByWallet, hide),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.fg,
                        maxLines = 1,
                    )
                    if (item.status == "error") {
                        Text(
                            "${stringResource(R.string.offline_sync_error)}: ${item.errorMsg.orEmpty()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.danger,
                            maxLines = 1,
                        )
                    }
                }
                if (item.status == "error") {
                    Icon(
                        Icons.Outlined.Refresh,
                        contentDescription = stringResource(R.string.offline_retry),
                        tint = colors.fgMuted,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable {
                                scope.launch {
                                    outbox.retry(item.id)
                                    if (repository.flushOutbox() > 0) onSynced()
                                }
                            },
                    )
                }
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.offline_discard),
                    tint = colors.fgMuted,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { scope.launch { outbox.discard(item.id) } },
                )
            }
        }
    }
}

/**
 * One queued capture in words. Amounts are read straight out of the stored args
 * — the same cents that will be sent — and only formatted here.
 */
@Composable
private fun itemSummary(
    item: OutboxItem,
    currencyByWallet: Map<Long, String>,
    hide: Boolean,
): String {
    fun long(key: String) = item.args[key]?.jsonPrimitive?.longOrNull
    fun text(key: String) = item.args[key]?.jsonPrimitive?.contentOrNull

    val kind = stringResource(
        when (item.command) {
            "add_income" -> R.string.transactions_income
            "add_transfer" -> R.string.transactions_transfer
            else -> R.string.transactions_expense
        },
    )
    val walletId = long("walletId") ?: long("fromWalletId")
    val cents = long("amountCents") ?: long("amountFromCents")
    val currency = currencyByWallet[walletId] ?: "MXN"
    val amount = cents?.let { maskIfHidden(formatMoney(it, currency), hide) }

    return listOfNotNull(kind, amount, text("occurredAt"), text("description"))
        .filter { it.isNotBlank() }
        .joinToString(" · ")
}
