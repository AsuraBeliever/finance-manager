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
import com.asura.finanzas.ui.components.Lucide
import com.asura.finanzas.ui.components.HairLine
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

    // `rounded-xl border border-amber-500/30 bg-amber-500/5 p-4`, amber in
    // both themes as on the web.
    val amber = androidx.compose.ui.graphics.Color(0xFFF59E0B)
    val amberText = androidx.compose.ui.graphics.Color(0xFFFCD34D)
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(amber.copy(alpha = 0.05f))
            .border(1.dp, amber.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(17.dp),
    ) {
        // `flex items-center gap-2 text-sm font-medium text-amber-300`
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Lucide.CloudOff,
                contentDescription = null,
                tint = amberText,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "${stringResource(R.string.offline_pending_title)} (${items.size})",
                style = MaterialTheme.typography.labelLarge,
                color = amberText,
            )
        }
        // `mt-1 text-xs text-fg-subtle`
        Text(
            stringResource(R.string.offline_pending_hint),
            style = MaterialTheme.typography.bodySmall,
            color = colors.fgSubtle,
            modifier = Modifier.padding(top = 4.dp),
        )

        // `mt-3 divide-y divide-border-muted`, each row `py-2 gap-3 text-sm`.
        Column(Modifier.padding(top = 12.dp)) {
            items.forEachIndexed { index, item ->
                if (index > 0) HairLine()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            itemSummary(item, currencyByWallet, hide),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.fg,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                        if (item.status == "error") {
                            Text(
                                "${stringResource(R.string.offline_sync_error)}: ${item.errorMsg.orEmpty()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.danger,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (item.status == "error") {
                        // `rounded-md p-1.5`, a 15 px icon.
                        Icon(
                            Lucide.RotateCw,
                            contentDescription = stringResource(R.string.offline_retry),
                            tint = colors.fgMuted,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    scope.launch {
                                        outbox.retry(item.id)
                                        if (repository.flushOutbox() > 0) onSynced()
                                    }
                                }
                                .padding(6.dp)
                                .size(15.dp),
                        )
                    }
                    Icon(
                        Lucide.Trash,
                        contentDescription = stringResource(R.string.offline_discard),
                        tint = colors.fgMuted,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { scope.launch { outbox.discard(item.id) } }
                            .padding(6.dp)
                            .size(15.dp),
                    )
                }
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
