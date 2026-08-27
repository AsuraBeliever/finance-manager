package com.asura.finanzas.ui.transactions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.asura.finanzas.R
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.Transaction
import com.asura.finanzas.data.Wallet
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
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFormat = DateTimeFormatter.ofPattern("d 'de' MMMM", Locale.forLanguageTag("es-MX"))

@Composable
fun TransactionsScreen(repository: BrokeRepository, modifier: Modifier = Modifier) {
    val (key, reload) = rememberReloadKey()
    val state by loadSynced(key) { repository.transactions() }
    val wallets by produceState(initialValue = emptyList<Wallet>(), key) {
        value = runCatching { repository.wallets().value }.getOrDefault(emptyList())
    }

    var showForm by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Transaction?>(null) }
    val scope = rememberCoroutineScope()

    Box(modifier.fillMaxSize()) {
        when (val current = state) {
            is Load.Loading -> LoadingBox()
            is Load.Failed -> ErrorBox(current.message, reload)
            is Load.Ready -> TransactionList(
                transactions = current.data,
                fromCache = current.fromCache,
                onLongPress = { pendingDelete = it },
            )
        }

        FloatingActionButton(
            onClick = { showForm = true },
            containerColor = Broke.colors.accent,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = stringResource(R.string.transactions_new_transaction),
            )
        }
    }

    if (showForm) {
        TransactionFormSheet(
            repository = repository,
            wallets = wallets,
            onDismiss = { showForm = false },
            onSaved = {
                showForm = false
                reload()
            },
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.transactions_delete_confirm_title)) },
            text = { Text(stringResource(R.string.transactions_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        runCatching { repository.deleteTransaction(target.id) }
                        reload()
                    }
                }) { Text(stringResource(R.string.common_delete), color = Broke.colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun TransactionList(
    transactions: List<Transaction>,
    fromCache: Boolean,
    onLongPress: (Transaction) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (fromCache) item { OfflineNotice(Modifier.fillMaxWidth()) }
        item { SectionTitle(stringResource(R.string.transactions_title)) }

        if (transactions.isEmpty()) {
            item {
                Column {
                    Text(
                        stringResource(R.string.transactions_empty_title),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Broke.colors.fg,
                    )
                    Text(
                        stringResource(R.string.transactions_empty_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Broke.colors.fgMuted,
                    )
                }
            }
        }

        items(transactions, key = { it.id }) { tx ->
            TransactionRow(tx, onLongPress)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransactionRow(tx: Transaction, onLongPress: (Transaction) -> Unit) {
    val colors = Broke.colors
    // The sign is the server's: "income" and "transfer_in" add, the rest subtract.
    val incoming = tx.kind == "income" || tx.kind == "transfer_in"

    GlassCard(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = { onLongPress(tx) }),
    ) {
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
                color = if (incoming) colors.positive else colors.fg,
            )
        }
    }
}

@Composable
private fun kindLabel(kind: String): String = when (kind) {
    "income" -> stringResource(R.string.transactions_income)
    "expense" -> stringResource(R.string.transactions_expense)
    "transfer_in", "transfer_out" -> stringResource(R.string.transactions_transfer)
    else -> kind
}

/** `occurredAt` is a business date ('YYYY-MM-DD'), not an instant. */
private fun formatDay(occurredAt: String): String? =
    runCatching { LocalDate.parse(occurredAt.take(10)).format(dayFormat) }.getOrNull()
