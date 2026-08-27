package com.asura.finanzas.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.Transaction
import com.asura.finanzas.ui.components.ErrorBox
import com.asura.finanzas.ui.components.GlassCard
import com.asura.finanzas.ui.components.Load
import com.asura.finanzas.ui.components.LoadingBox
import com.asura.finanzas.ui.components.OfflineNotice
import com.asura.finanzas.ui.components.SectionTitle
import com.asura.finanzas.ui.components.loadSynced
import com.asura.finanzas.ui.components.rememberReloadKey
import com.asura.finanzas.ui.formatMoney
import com.asura.finanzas.ui.theme.Broke
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFormat = DateTimeFormatter.ofPattern("d 'de' MMMM", Locale.forLanguageTag("es-MX"))

@Composable
fun TransactionsScreen(repository: BrokeRepository, modifier: Modifier = Modifier) {
    val (key, reload) = rememberReloadKey()
    val state by loadSynced(key) { repository.transactions() }

    when (val current = state) {
        is Load.Loading -> LoadingBox(modifier)
        is Load.Failed -> ErrorBox(current.message, reload, modifier)
        is Load.Ready -> TransactionList(current.data, current.fromCache, modifier)
    }
}

@Composable
private fun TransactionList(
    transactions: List<Transaction>,
    fromCache: Boolean,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (fromCache) item { OfflineNotice(Modifier.fillMaxWidth()) }
        item { SectionTitle("Movimientos") }

        if (transactions.isEmpty()) {
            item {
                Text(
                    "Todavía no hay movimientos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Broke.colors.fgMuted,
                )
            }
        }

        items(transactions, key = { it.id }) { tx -> TransactionRow(tx) }
    }
}

@Composable
private fun TransactionRow(tx: Transaction) {
    val colors = Broke.colors
    // The sign is the server's: "income" and "transfer_in" add, the rest subtract.
    val incoming = tx.kind == "income" || tx.kind == "transfer_in"
    val amountColor = if (incoming) colors.positive else colors.fg

    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = tx.description?.takeIf { it.isNotBlank() }
                        ?: tx.categoryName
                        ?: kindLabel(tx.kind),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.fg,
                )
                Text(
                    text = listOfNotNull(
                        tx.walletName.takeIf { it.isNotBlank() },
                        formatDay(tx.occurredAt),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.fgSubtle,
                )
            }
            Text(
                text = (if (incoming) "+" else "−") + formatMoney(tx.amountCents),
                style = MaterialTheme.typography.labelLarge,
                color = amountColor,
            )
        }
    }
}

private fun kindLabel(kind: String) = when (kind) {
    "income" -> "Ingreso"
    "expense" -> "Gasto"
    "transfer_in" -> "Transferencia recibida"
    "transfer_out" -> "Transferencia enviada"
    else -> kind
}

/** `occurredAt` is a business date ('YYYY-MM-DD'), not an instant. */
private fun formatDay(occurredAt: String): String? =
    runCatching { LocalDate.parse(occurredAt.take(10)).format(dayFormat) }.getOrNull()
