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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.CallMade
import androidx.compose.material.icons.outlined.CallReceived
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.asura.finanzas.R
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.NetworkException
import com.asura.finanzas.data.Outbox
import com.asura.finanzas.data.Transaction
import com.asura.finanzas.data.TransactionCategory
import com.asura.finanzas.data.TxTotals
import com.asura.finanzas.data.Wallet
import com.asura.finanzas.ui.LocalAppSettings
import com.asura.finanzas.ui.components.ChipButton
import com.asura.finanzas.ui.components.DateField
import com.asura.finanzas.ui.components.DialogAction
import com.asura.finanzas.ui.components.EmptyState
import com.asura.finanzas.ui.components.FormSheet
import com.asura.finanzas.ui.components.GlassCard
import com.asura.finanzas.ui.components.ErrorBox
import com.asura.finanzas.ui.components.HairLine
import com.asura.finanzas.ui.components.IconBadge
import com.asura.finanzas.ui.components.Load
import com.asura.finanzas.ui.components.LoadingBox
import com.asura.finanzas.ui.components.OfflineNotice
import com.asura.finanzas.ui.components.PageHeader
import com.asura.finanzas.ui.components.Period
import com.asura.finanzas.ui.components.PeriodLabel
import com.asura.finanzas.ui.components.PeriodPickerDialog
import com.asura.finanzas.ui.components.PickerField
import com.asura.finanzas.ui.components.PrimaryButton
import com.asura.finanzas.ui.components.SegmentedControl
import com.asura.finanzas.ui.components.loadSynced
import com.asura.finanzas.ui.components.rememberReloadKey
import com.asura.finanzas.ui.formatMoney
import com.asura.finanzas.ui.maskIfHidden
import com.asura.finanzas.ui.parseAmountToCents
import com.asura.finanzas.ui.seedName
import com.asura.finanzas.ui.theme.Broke
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A row to render: an ordinary movement, or — when both legs of a transfer are
 * in the list — the two folded into one line (origin → destination). `tx` is the
 * `transfer_out` leg, the canonical handle for edit and delete.
 */
private data class TxRow(val tx: Transaction, val toLeg: Transaction? = null)

/**
 * Folds each pair of transfer legs into a single row. A lone leg — the list is
 * filtered to one wallet, or the sibling fell off the page — stays as it is:
 * there its +/− is the whole story. Mirrors the web's `foldTransfers`.
 */
private fun foldTransfers(transactions: List<Transaction>): List<TxRow> {
    val byGroup = transactions
        .filter { it.transferGroupId != null }
        .groupBy { it.transferGroupId }
    val folded = mutableSetOf<Long>()
    val rows = mutableListOf<TxRow>()
    for (tx in transactions) {
        if (tx.id in folded) continue
        val legs = tx.transferGroupId?.let { byGroup[it] }
        val out = legs?.firstOrNull { it.kind == "transfer_out" }
        val into = legs?.firstOrNull { it.kind == "transfer_in" }
        if (legs?.size == 2 && out != null && into != null) {
            folded += out.id
            folded += into.id
            rows += TxRow(out, into)
        } else {
            rows += TxRow(tx)
        }
    }
    return rows
}

/**
 * Apartado moves ride the transactions list with a negative id that encodes the
 * goal_contributions row — that is how the web addresses them for edit/delete.
 */
val Transaction.isApartado: Boolean
    get() = kind == "reserve" || kind == "release"

/** The kind filter tabs the web shows above the list. */
private enum class KindFilter(val labelRes: Int, val wire: String?) {
    All(R.string.transactions_type_all, null),
    Income(R.string.transactions_income, "income"),
    Expense(R.string.transactions_expense, "expense"),
    Transfer(R.string.transactions_transfer, "transfer"),
}

@Composable
fun TransactionsScreen(
    repository: BrokeRepository,
    outbox: Outbox,
    modifier: Modifier = Modifier,
) {
    val (key, reload) = rememberReloadKey()
    var filter by remember { mutableStateOf(KindFilter.All) }
    var wallet by remember { mutableStateOf<Wallet?>(null) }
    // Category only applies to income/expense and is scoped to the chosen kind,
    // so switching kind clears it (web: TransactionFilters).
    var category by remember { mutableStateOf<TransactionCategory?>(null) }
    var period by remember { mutableStateOf<Period>(Period.AllTime) }
    var showPeriod by remember { mutableStateOf(false) }

    // Includes the reserved categories a capture form would not offer, because a
    // movement may already be filed under one.
    val filterCategories by produceState(initialValue = emptyList<TransactionCategory>(), key) {
        value = runCatching { repository.filterCategories() }.getOrDefault(emptyList())
    }

    // "Todo el tiempo" drops the date filter entirely rather than asking for the
    // allTime window — this is a ledger, so a movement dated in the future has
    // to stay visible (web: TransactionFilters). Sending null also lets the
    // unfiltered list hit the offline cache, which keys on period == null.
    val periodFilter = period.takeIf { it != Period.AllTime }?.toJson()

    val state by loadSynced(listOf(key, filter, wallet?.id, category?.id, period)) {
        repository.transactions(
            walletId = wallet?.id,
            kind = filter.wire,
            categoryId = category?.id,
            period = periodFilter,
        )
    }
    val wallets by produceState(initialValue = emptyList<Wallet>(), key) {
        value = runCatching { repository.wallets().value }.getOrDefault(emptyList())
    }

    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Transaction?>(null) }
    var actionsFor by remember { mutableStateOf<Transaction?>(null) }
    var pendingDelete by remember { mutableStateOf<Transaction?>(null) }
    var editingApartado by remember { mutableStateOf<Transaction?>(null) }

    // Totals only exist for a single-sided kind; the API rejects them otherwise.
    val totals by produceState<TxTotals?>(null, key, filter, wallet?.id, category?.id, period) {
        value = filter.wire
            ?.takeIf { it == "income" || it == "expense" }
            ?.let {
                runCatching {
                    repository.transactionTotals(it, wallet?.id, category?.id, periodFilter)
                }.getOrNull()
            }
    }
    val scope = rememberCoroutineScope()

    Box(modifier.fillMaxSize()) {
        when (val current = state) {
            is Load.Loading -> LoadingBox()
            is Load.Failed -> ErrorBox(current.message, reload)
            is Load.Ready -> TransactionList(
                outbox = outbox,
                repository = repository,
                onSynced = reload,
                transactions = current.data,
                filter = filter,
                onFilter = { filter = it; category = null },
                wallets = wallets,
                wallet = wallet,
                onWallet = { wallet = it },
                categories = filterCategories,
                category = category,
                onCategory = { category = it },
                period = period,
                onPickPeriod = { showPeriod = true },
                fromCache = current.fromCache,
                onNew = { showForm = true },
                totals = totals,
                onLongPress = { actionsFor = it },
            )
        }
    }

    if (showPeriod) {
        PeriodPickerDialog(
            selected = period,
            // Parameters are edited inline, so a pick applies without closing.
            onSelect = { period = it },
            onDismiss = { showPeriod = false },
            allowAll = true,
        )
    }

    if (showForm) {
        TransactionFormSheet(
            repository = repository,
            wallets = wallets,
            onDismiss = { showForm = false },
            onSaved = { showForm = false; reload() },
        )
    }

    editingApartado?.let { target ->
        ApartadoEditSheet(
            repository = repository,
            transaction = target,
            onDismiss = { editingApartado = null },
            onSaved = { editingApartado = null; reload() },
        )
    }

    editing?.let { target ->
        TransactionEditSheet(
            repository = repository,
            transaction = target,
            wallets = wallets,
            onDismiss = { editing = null },
            onSaved = { editing = null; reload() },
        )
    }

    actionsFor?.let { target ->
        AlertDialog(
            onDismissRequest = { actionsFor = null },
            containerColor = Broke.colors.surfaceOverlay,
            title = {
                Text(
                    target.description?.takeIf { it.isNotBlank() }
                        ?: target.categoryName.orEmpty(),
                    color = Broke.colors.fg,
                )
            },
            text = {
                Column {
                    // Transfers included: the edit sheet loads both legs and
                    // saves them together, so the pair can never unbalance.
                    DialogAction(stringResource(R.string.common_edit)) {
                        actionsFor = null
                        if (target.isApartado) editingApartado = target else editing = target
                    }
                    DialogAction(stringResource(R.string.common_delete), Broke.colors.danger) {
                        actionsFor = null
                        pendingDelete = target
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

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = Broke.colors.surfaceOverlay,
            title = {
                Text(
                    stringResource(
                        if (target.isApartado) R.string.transactions_apartado_delete_title
                        else R.string.transactions_delete_confirm_title,
                    ),
                )
            },
            text = {
                Text(
                    stringResource(
                        if (target.isApartado) R.string.transactions_apartado_delete_confirm
                        else R.string.transactions_delete_confirm,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        runCatching {
                            // An apartado row carries the contribution id negated.
                            if (target.isApartado) {
                                repository.deleteGoalContribution(-target.id)
                            } else {
                                repository.deleteTransaction(target.id)
                            }
                        }
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
    outbox: Outbox,
    repository: BrokeRepository,
    onSynced: () -> Unit,
    transactions: List<Transaction>,
    filter: KindFilter,
    onFilter: (KindFilter) -> Unit,
    wallets: List<Wallet>,
    wallet: Wallet?,
    onWallet: (Wallet?) -> Unit,
    categories: List<TransactionCategory>,
    category: TransactionCategory?,
    onCategory: (TransactionCategory?) -> Unit,
    period: Period,
    onPickPeriod: () -> Unit,
    fromCache: Boolean,
    onNew: () -> Unit,
    totals: TxTotals?,
    onLongPress: (Transaction) -> Unit,
) {
    val colors = Broke.colors
    val hide = LocalAppSettings.current.hideBalances

    // The server applies the filters; the list arrives ready to render.
    // Both legs of a transfer read as one line, like the web's list.
    val shown = foldTransfers(transactions)

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
            OutboxPanel(
                outbox = outbox,
                repository = repository,
                wallets = wallets,
                onSynced = onSynced,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            PickerField(
                label = stringResource(R.string.transactions_wallet),
                options = listOf<Wallet?>(null) + wallets.filter { !it.isArchived },
                selected = wallet,
                optionLabel = { it?.name ?: allWalletsLabel() },
                onSelect = onWallet,
                emptyLabel = stringResource(R.string.transactions_all_wallets),
                modifier = Modifier.fillMaxWidth(),
            )
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

        // Category only applies to income/expense, and only lists the ones that
        // belong to the chosen kind — same rule as the web.
        if (filter == KindFilter.Income || filter == KindFilter.Expense) {
            item {
                val ofKind = categories.filter { it.kind == filter.wire }
                PickerField(
                    label = stringResource(R.string.transactions_category),
                    options = listOf<TransactionCategory?>(null) + ofKind,
                    selected = category,
                    optionLabel = {
                        it?.let { c -> seedName(c.name, c.isSystem) }
                            ?: stringResource(R.string.transactions_all_categories)
                    },
                    onSelect = onCategory,
                    emptyLabel = stringResource(R.string.transactions_all_categories),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            ChipButton(
                text = PeriodLabel(period),
                onClick = onPickPeriod,
                leadingIcon = Icons.Outlined.CalendarMonth,
                trailingIcon = Icons.Outlined.ExpandMore,
            )
        }

        totals?.let { summary ->
            item {
                GlassCard(Modifier.fillMaxWidth(), padding = 16.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            stringResource(
                                if (filter.wire == "income") R.string.transactions_total_income
                                else R.string.transactions_total_expense,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.fgMuted,
                        )
                        Text(
                            maskIfHidden(formatMoney(summary.totalMxnCents), hide),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.fg,
                        )
                    }
                    if (summary.byCurrency.size > 1) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            summary.byCurrency.joinToString(" · ") {
                                maskIfHidden(formatMoney(it.cents, it.currencyCode), hide)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.fgSubtle,
                        )
                    }
                }
            }
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
                    shown.forEachIndexed { index, row ->
                        if (index > 0) HairLine()
                        TransactionRow(row.tx, row.toLeg, hide, onLongPress)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransactionRow(
    tx: Transaction,
    /** The `transfer_in` leg, only on folded transfer rows. */
    toLeg: Transaction?,
    hide: Boolean,
    onLongPress: (Transaction) -> Unit,
) {
    val colors = Broke.colors
    // The web colours by kind, not by sign: income violet, transfer cyan,
    // expense rose.
    val (icon: ImageVector, tint: Color) = when (tx.kind) {
        "income" -> Icons.Outlined.CallReceived to colors.accent
        "transfer_in", "transfer_out" -> Icons.AutoMirrored.Outlined.CompareArrows to colors.cyan
        // Apartado moves are information only: no money leaves the wallet, so
        // they read neutral with an arrow instead of a signed amount.
        "reserve", "release" -> Icons.Outlined.Savings to colors.fgMuted
        else -> Icons.Outlined.CallMade to colors.danger
    }
    val sign = when (tx.kind) {
        "income", "transfer_in" -> "+"
        "transfer_out" -> ""
        "reserve" -> "→"
        "release" -> "←"
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
                text = if (tx.isApartado) {
                    stringResource(
                        if (tx.kind == "reserve") R.string.transactions_reserved
                        else R.string.transactions_released,
                    ) + " · " + tx.description.orEmpty()
                } else {
                    tx.description?.takeIf { it.isNotBlank() }
                        ?: tx.categoryName?.let { seedName(it) }
                        ?: kindLabel(tx.kind)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = colors.fg,
            )
            Text(
                text = listOfNotNull(
                    // ISO date, same as the web's list.
                    tx.occurredAt.take(10),
                    transactionTimeLabel(tx),
                    // A folded transfer names both ends instead of one wallet.
                    if (toLeg != null) {
                        "${tx.walletName} → ${toLeg.walletName}"
                    } else {
                        tx.walletName.takeIf { it.isNotBlank() }
                    },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = colors.fgSubtle,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            // A folded transfer carries no sign — nothing entered or left the
            // books. Both amounts show only when the legs differ, which happens
            // across currencies.
            text = when {
                toLeg == null -> sign + maskIfHidden(formatMoney(tx.amountCents), hide)
                toLeg.amountCents != tx.amountCents -> maskIfHidden(
                    formatMoney(tx.amountCents),
                    hide,
                ) + " → " + maskIfHidden(formatMoney(toLeg.amountCents), hide)
                else -> maskIfHidden(formatMoney(tx.amountCents), hide)
            },
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
fun transactionTimeLabel(tx: Transaction): String? {
    val settings = LocalAppSettings.current
    val pattern = if (settings.clock24) "HH:mm" else "h:mm a"
    val formatter = remember(pattern) { DateTimeFormatter.ofPattern(pattern, Locale.US) }

    tx.occurredTime?.takeIf { it.isNotBlank() }?.let { own ->
        return runCatching { LocalTime.parse(own).format(formatter) }.getOrNull() ?: own
    }

    // Falls back to the insert stamp, converted to the timezone chosen in the
    // app's settings — not the device's, which is what the web does too.
    val created = tx.createdAt ?: return null
    return runCatching {
        LocalDateTime
            .parse(created.replace(" ", "T"))
            .atZone(ZoneId.of("UTC"))
            .withZoneSameInstant(ZoneId.of(settings.timezone))
            .format(formatter)
    }.getOrNull()
}

@Composable
private fun allWalletsLabel(): String = stringResource(R.string.transactions_all_wallets)

/**
 * Edit an apartado move straight from the history. The row's id is the goal
 * contribution's, negated; the sign of the amount is what tells reserve from
 * release, so it is rebuilt from the row's kind rather than typed.
 */
@Composable
private fun ApartadoEditSheet(
    repository: BrokeRepository,
    transaction: Transaction,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var amount by remember {
        mutableStateOf(formatMoney(transaction.amountCents, withSymbol = false))
    }
    var date by remember {
        mutableStateOf(
            runCatching { LocalDate.parse(transaction.occurredAt.take(10)) }
                .getOrDefault(LocalDate.now()),
        )
    }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val genericError = stringResource(R.string.common_error)
    val offlineError = stringResource(R.string.offline_banner)

    val cents = parseAmountToCents(amount)
    val canSave = !busy && cents != null && cents > 0

    FormSheet(
        title = stringResource(R.string.transactions_apartado_edit_title),
        busy = busy,
        error = error,
        canSave = canSave,
        onDismiss = onDismiss,
        onSave = {
            val amountCents = cents ?: return@FormSheet
            busy = true
            error = null
            scope.launch {
                runCatching {
                    repository.updateGoalContribution(
                        id = -transaction.id,
                        amountCents = if (transaction.kind == "release") -amountCents else amountCents,
                        occurredAt = date.toString(),
                    )
                }
                    .onSuccess { onSaved() }
                    .onFailure {
                        error = if (it is NetworkException) offlineError else it.message ?: genericError
                        busy = false
                    }
            }
        },
    ) {
        Text(
            stringResource(
                if (transaction.kind == "reserve") R.string.transactions_reserved
                else R.string.transactions_released,
            ) + " · " + transaction.description.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = Broke.colors.fgMuted,
        )
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it; error = null },
            label = { Text(stringResource(R.string.transactions_amount)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        DateField(
            label = stringResource(R.string.transactions_date),
            value = date,
            onChange = { date = it },
        )
    }
}
