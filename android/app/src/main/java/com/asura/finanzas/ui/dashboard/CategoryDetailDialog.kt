package com.asura.finanzas.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CallMade
import androidx.compose.material.icons.outlined.CallReceived
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.asura.finanzas.R
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.Transaction
import com.asura.finanzas.data.Wallet
import com.asura.finanzas.ui.LocalAppSettings
import com.asura.finanzas.ui.components.HairLine
import com.asura.finanzas.ui.components.IconBadge
import com.asura.finanzas.ui.components.Period
import com.asura.finanzas.ui.formatMoney
import com.asura.finanzas.ui.maskIfHidden
import com.asura.finanzas.ui.seedName
import com.asura.finanzas.ui.text
import com.asura.finanzas.ui.theme.Broke
import com.asura.finanzas.ui.transactions.transactionTimeLabel

/**
 * The category slice that was tapped: identifies the drill-down and supplies the
 * MXN total shown in the header, so it always matches the donut.
 */
data class CategoryDetailTarget(
    val categoryId: Long?,
    val name: String,
    val mxnCents: Long,
)

/**
 * Read-only list of the movements behind one breakdown slice, over the same
 * period the widget is showing. Amounts render in each row's own wallet
 * currency, exactly like the web modal.
 */
@Composable
fun CategoryDetailDialog(
    repository: BrokeRepository,
    kind: String,
    period: Period,
    target: CategoryDetailTarget,
    wallets: List<Wallet>,
    onDismiss: () -> Unit,
) {
    val colors = Broke.colors
    val settings = LocalAppSettings.current
    val hide = settings.hideBalances
    val currencyByWallet = wallets.associate { it.id to it.currencyCode }

    val rows by produceState<List<Transaction>?>(null, kind, target.categoryId, period) {
        value = runCatching {
            repository.categoryTransactions(kind, target.categoryId, period.toJson())
        }.getOrDefault(emptyList())
    }

    val loaded = rows
    // Same rule as the widget: the null-category bucket is named locally,
    // because the worker's label for it is Spanish whatever the locale.
    val title = if (target.categoryId == null) {
        stringResource(R.string.dashboard_uncategorized)
    } else {
        seedName(target.name).orEmpty()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceOverlay,
        title = { Text(title, color = colors.fg) },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        when {
                            loaded == null -> stringResource(R.string.common_loading)
                            loaded.size == 1 -> stringResource(R.string.dashboard_movement_one)
                            else -> text(
                                R.string.dashboard_movements_count,
                                "n" to loaded.size,
                            )
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.fgSubtle,
                    )
                    Text(
                        maskIfHidden(formatMoney(target.mxnCents), hide),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.fg,
                    )
                }
                Spacer(Modifier.height(8.dp))
                HairLine()

                if (loaded != null && loaded.isEmpty()) {
                    Text(
                        stringResource(R.string.dashboard_no_period_data),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.fgSubtle,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                } else if (loaded != null) {
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(loaded, key = { it.id }) { tx ->
                            CategoryDetailRow(
                                tx = tx,
                                kind = kind,
                                fallbackName = title,
                                currency = currencyByWallet[tx.walletId] ?: "MXN",
                                hide = hide,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close), color = colors.fgMuted)
            }
        },
    )
}

@Composable
private fun CategoryDetailRow(
    tx: Transaction,
    kind: String,
    fallbackName: String,
    currency: String,
    hide: Boolean,
) {
    val colors = Broke.colors
    val income = kind == "income"
    val tint = if (income) colors.accent else colors.danger
    val time = transactionTimeLabel(tx)

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(
            if (income) Icons.Outlined.CallReceived else Icons.Outlined.CallMade,
            tint = tint,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                tx.description?.takeIf { it.isNotBlank() } ?: fallbackName,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.fg,
                maxLines = 1,
            )
            Text(
                buildString {
                    append(tx.occurredAt)
                    if (time != null) append(" · $time")
                    append(" · ${tx.walletName}")
                },
                style = MaterialTheme.typography.labelSmall,
                color = colors.fgSubtle,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            (if (income) "+" else "−") +
                maskIfHidden(formatMoney(tx.amountCents, currency), hide),
            style = MaterialTheme.typography.bodyMedium,
            color = tint,
        )
    }
}
