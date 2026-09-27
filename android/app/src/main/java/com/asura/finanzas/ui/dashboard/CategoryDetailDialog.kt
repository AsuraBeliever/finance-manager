package com.asura.finanzas.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.asura.finanzas.ui.components.Lucide
import com.asura.finanzas.ui.components.HairLine
import com.asura.finanzas.ui.components.PlainSheet
import com.asura.finanzas.ui.components.Period
import com.asura.finanzas.ui.formatMoney
import com.asura.finanzas.ui.maskIfHidden
import com.asura.finanzas.ui.seedName
import com.asura.finanzas.ui.text
import com.asura.finanzas.ui.theme.Broke
import com.asura.finanzas.ui.theme.tabular
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

    // The web's `Modal solid fixedHeight`: an opaque card locked to 80% of
    // the screen, its list scrolling under a pinned title.
    PlainSheet(title = title, onDismiss = onDismiss, solid = true, fixedHeight = true, spacing = 16.dp) {
        // `flex items-baseline justify-between border-b pb-3`
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    when {
                        loaded == null -> ""
                        loaded.size == 1 -> stringResource(R.string.dashboard_movement_one)
                        else -> text(R.string.dashboard_movements_count, "n" to loaded.size)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.fgSubtle,
                    modifier = Modifier.alignByBaseline(),
                )
                // `font-display text-lg font-semibold tabular-nums`
                Text(
                    maskIfHidden(formatMoney(target.mxnCents), hide),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.sp,
                    ).tabular(),
                    color = colors.fg,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            HairLine()
        }

        when {
            // `py-6 text-center text-sm text-fg-subtle`, for both.
            loaded == null || loaded.isEmpty() -> Text(
                stringResource(
                    if (loaded == null) R.string.common_loading else R.string.dashboard_no_period_data,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.fgSubtle,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            )
            // `divide-y divide-border-muted`
            else -> Column {
                loaded.forEachIndexed { i, tx ->
                    if (i > 0) HairLine()
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

    // `flex items-center gap-3 py-2.5`
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // `h-7 w-7 rounded-full bg-surface-overlay`, a 13 px arrow.
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(colors.surfaceOverlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (income) Lucide.ArrowDownLeft else Lucide.ArrowUpRight,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(13.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                tx.description?.takeIf { it.isNotBlank() } ?: fallbackName,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // `text-xs text-fg-subtle`, free to wrap.
            Text(
                buildString {
                    append(tx.occurredAt)
                    if (time != null) append(" · $time")
                    append(" · ${tx.walletName}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.fgSubtle,
            )
        }
        // `text-sm font-medium tabular-nums`
        Text(
            (if (income) "+" else "−") +
                maskIfHidden(formatMoney(tx.amountCents, currency), hide),
            style = MaterialTheme.typography.labelLarge.tabular(),
            color = tint,
        )
    }
}
