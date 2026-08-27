package com.asura.finanzas.ui.transactions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CallMade
import androidx.compose.material.icons.outlined.CallReceived
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.asura.finanzas.R
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.Transaction
import com.asura.finanzas.data.Wallet
import com.asura.finanzas.ui.LocalAppSettings
import com.asura.finanzas.ui.components.EmptyState
import com.asura.finanzas.ui.components.ErrorBox
import com.asura.finanzas.ui.components.HairLine
import com.asura.finanzas.ui.components.IconBadge
import com.asura.finanzas.ui.components.Load
import com.asura.finanzas.ui.components.LoadingBox
import com.asura.finanzas.ui.components.OfflineNotice
import com.asura.finanzas.ui.components.PageHeader
import com.asura.finanzas.ui.components.PrimaryButton
import com.asura.finanzas.ui.components.SegmentedControl
import com.asura.finanzas.ui.components.loadSynced
import com.asura.finanzas.ui.components.rememberReloadKey
import com.asura.finanzas.ui.formatMoney
import com.asura.finanzas.ui.maskIfHidden
import com.asura.finanzas.ui.theme.Broke
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The kind filter tabs the web shows above the list. */
private enum class KindFilter(val labelRes: Int, val wire: String?) {
    All(R.string.transactions_type_all, null),
    Income(R.string.transactions_income, "income"),
    Expense(R.string.transactions_expense, "expense"),
    Transfer(R.string.transactions_transfer, "transfer"),
}

@Composable
fun TransactionsScreen(repository: BrokeRepository, modifier: Modifier = Modifier) {
    val (key, reload) = rememberReloadKey()
    var filter by remember { mutableStateOf(KindFilter.All) }
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
                filter = filter,
                onFilter = { filter = it },
                fromCache = current.fromCache,
                onNew = { showForm = true },
                onLongPress = { pendingDelete = it },
            )
        }
    }

    if (showForm) {
        TransactionFormSheet(
            repository = repository,
            wallets = wallets,
            onDismiss = { showForm = false },
            onSaved = { showForm = false; reload() },
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = Broke.colors.surfaceOverlay,
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
    filter: KindFilter,
    onFilter: (KindFilter) -> Unit,
    fromCache: Boolean,
    onNew: () -> Unit,
    onLongPress: (Transaction) -> Unit,
) {
    val colors = Broke.colors
    val hide = LocalAppSettings.current.hideBalances

    // Filtering a list that is already on the device; the amounts themselves
    // are untouched.
    val shown = when (filter.wire) {
        null -> transactions
        "transfer" -> transactions.filter { it.kind.startsWith("transfer") }
        else -> transactions.filter { it.kind == filter.wire }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            PageHeader(stringResource(R.string.transactions_title)) {
                PrimaryButton(
                    text = stringResource(R.string.transactions_new_transaction),
                    onClick = onNew,
                    leadingIcon = Icons.Outlined.Add,
                )
            }
        }

        if (fromCache) {
            item { OfflineNotice(stringResource(R.string.offline_banner), Modifier.fillMaxWidth()) }
        }

        item {
            SegmentedControl(
                options = KindFilter.entries,
                selected = filter,
                label = { stringResource(it.labelRes) },
                onSelect = onFilter,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (shown.isEmpty()) {
            item {
                EmptyState(
                    stringResource(R.string.transactions_empty_title),
                    stringResource(R.string.transactions_empty_description),
                )
            }
        } else {
            item {
                // One card holding every row, split by hairlines — the web's list.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(colors.surfaceRaised)
                        .border(1.dp, colors.borderMuted, RoundedCornerShape(24.dp)),
                ) {
                    shown.forEachIndexed { index, tx ->
                        if (index > 0) HairLine()
                        TransactionRow(tx, hide, onLongPress)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransactionRow(tx: Transaction, hide: Boolean, onLongPress: (Transaction) -> Unit) {
    val colors = Broke.colors
    // The web colours by kind, not by sign: income violet, transfer cyan,
    // expense rose.
    val (icon: ImageVector, tint: Color) = when (tx.kind) {
        "income" -> Icons.Outlined.CallReceived to colors.accent
        "transfer_in", "transfer_out" -> Icons.AutoMirrored.Outlined.CompareArrows to colors.cyan
        else -> Icons.Outlined.CallMade to colors.danger
    }
    val sign = when (tx.kind) {
        "income", "transfer_in" -> "+"
        "transfer_out" -> ""
        else -> "−"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = { onLongPress(tx) })
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, tint)
        Spacer(Modifier.width(14.dp))
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
                    // ISO date, same as the web's list.
                    tx.occurredAt.take(10),
                    transactionTime(tx),
                    tx.walletName.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = colors.fgSubtle,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = sign + maskIfHidden(formatMoney(tx.amountCents), hide),
            style = MaterialTheme.typography.labelLarge,
            color = tint,
        )
    }
}

@Composable
private fun kindLabel(kind: String): String = when (kind) {
    "income" -> stringResource(R.string.transactions_income)
    "expense" -> stringResource(R.string.transactions_expense)
    "transfer_in", "transfer_out" -> stringResource(R.string.transactions_transfer)
    else -> kind
}

/**
 * The time shown next to a movement: its own wall-clock time when it has one,
 * otherwise the insert stamp converted to local time — the web's
 * `transactionTime`. Honours the 12/24 h setting.
 */
@Composable
private fun transactionTime(tx: Transaction): String? {
    val clock24 = LocalAppSettings.current.clock24
    val pattern = if (clock24) "HH:mm" else "h:mm a"
    val formatter = remember(pattern) { DateTimeFormatter.ofPattern(pattern, Locale.US) }

    tx.occurredTime?.takeIf { it.isNotBlank() }?.let { own ->
        return runCatching { LocalTime.parse(own).format(formatter) }.getOrNull() ?: own
    }

    val created = tx.createdAt ?: return null
    return runCatching {
        LocalDateTime
            .parse(created.replace(" ", "T"))
            .atZone(ZoneId.of("UTC"))
            .withZoneSameInstant(ZoneId.systemDefault())
            .format(formatter)
    }.getOrNull()
}
