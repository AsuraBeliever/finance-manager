package com.asura.finanzas.ui.wallets

import com.asura.finanzas.ui.components.SegmentedControl
import com.asura.finanzas.ui.components.PeriodPickerDialog
import com.asura.finanzas.ui.components.PeriodLabel
import com.asura.finanzas.ui.components.Period
import com.asura.finanzas.ui.components.ChipButton
import com.asura.finanzas.ui.transactions.TransactionTotal
import com.asura.finanzas.ui.transactions.KindFilter
import com.asura.finanzas.data.TxTotals
import com.asura.finanzas.ui.goals.WalletGoalsSection
import com.asura.finanzas.ui.transactions.TxKind
import com.asura.finanzas.data.CreditStatement
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.offset
import com.asura.finanzas.ui.components.PageHeader
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import com.asura.finanzas.data.SavingsGoal
import com.asura.finanzas.ui.components.Lucide
import com.asura.finanzas.ui.seedName
import com.asura.finanzas.ui.transactions.TransactionListCard
import com.asura.finanzas.ui.transactions.TransactionFormSheet
import com.asura.finanzas.ui.transactions.TransactionEditSheet
import com.asura.finanzas.ui.components.ConfirmDialog
import com.asura.finanzas.R
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.CreditCardSummary
import com.asura.finanzas.data.MsiPlan
import com.asura.finanzas.data.MsiSchedulePreview
import com.asura.finanzas.data.Transaction
import com.asura.finanzas.data.Wallet
import com.asura.finanzas.ui.LocalAppSettings
import com.asura.finanzas.ui.components.Dot
import com.asura.finanzas.ui.components.GlassCard
import com.asura.finanzas.ui.components.HairLine
import com.asura.finanzas.ui.components.LoadingBox
import com.asura.finanzas.ui.components.MicroLabel
import com.asura.finanzas.ui.components.ProgressBar
import com.asura.finanzas.ui.components.formatBps
import com.asura.finanzas.ui.formatMoney
import com.asura.finanzas.ui.maskIfHidden
import com.asura.finanzas.ui.parseHexColor
import com.asura.finanzas.ui.text
import com.asura.finanzas.ui.theme.Broke
import kotlinx.coroutines.launch

/**
 * One wallet: what it holds, its credit-card panel when it is a card, and its
 * movements. Every figure here — debt, statement balance, utilisation, what an
 * MSI plan still owes — is computed by finanzas-core and read as-is.
 */
@Composable
fun WalletDetailScreen(
    repository: BrokeRepository,
    walletId: Long,
    onBack: () -> Unit,
    onEdit: (Wallet) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Broke.colors
    val hide = LocalAppSettings.current.hideBalances
    val scope = rememberCoroutineScope()

    var wallet by remember { mutableStateOf<Wallet?>(null) }
    var credit by remember { mutableStateOf<CreditCardSummary?>(null) }
    var movements by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var pockets by remember { mutableStateOf<List<Wallet>>(emptyList()) }
    var goals by remember { mutableStateOf<List<SavingsGoal>>(emptyList()) }
    var addingPocket by remember { mutableStateOf(false) }
    var addingTx by remember { mutableStateOf(false) }
    var editingTx by remember { mutableStateOf<Transaction?>(null) }
    var deletingTx by remember { mutableStateOf<Transaction?>(null) }
    var allWallets by remember { mutableStateOf<List<Wallet>>(emptyList()) }
    var reloadKey by remember { mutableStateOf(0) }
    var addingMsi by remember { mutableStateOf(false) }
    // Non-null while the "pay the card" form is up.
    var paying by remember { mutableStateOf<CreditCardSummary?>(null) }
    // The schedule the server confirmed, shown once after saving: nothing
    // visible happens at save time otherwise, since instalments post later.
    var savedMsi by remember { mutableStateOf<MsiSchedulePreview?>(null) }
    // The web filters this page's ledger just like the movements page does.
    var txKind by remember { mutableStateOf<KindFilter>(KindFilter.All) }
    var txPeriod by remember { mutableStateOf<Period>(Period.AllTime) }
    var showPeriod by remember { mutableStateOf(false) }
    var totals by remember { mutableStateOf<TxTotals?>(null) }

    LaunchedEffect(walletId, reloadKey, txKind, txPeriod) {
        wallet = runCatching { repository.wallet(walletId) }.getOrNull()
        movements = runCatching {
            repository.transactions(
                walletId = walletId,
                kind = txKind.wire,
                period = txPeriod.toJson(),
            ).value
        }.getOrDefault(emptyList())
        // The web only totals a single-kind filter; "all" has nothing to add up.
        totals = txKind.wire?.takeIf { it == "income" || it == "expense" }?.let { kind ->
            runCatching {
                repository.transactionTotals(kind, walletId, null, txPeriod.toJson())
            }.getOrNull()
        }
        credit = wallet?.takeIf { it.creditCutDay != null }
            ?.let { runCatching { repository.creditCardSummary(walletId) }.getOrNull() }
        allWallets = runCatching { repository.wallets().value }.getOrDefault(emptyList())
        pockets = allWallets.filter { it.parentWalletId == walletId }
        // Goals whose apartado is reserved inside this wallet, as the web lists.
        goals = runCatching { repository.savingsGoals().value }
            .getOrDefault(emptyList())
            .filter { it.linkedWalletId == walletId }
    }

    val current = wallet
    if (current == null) {
        LoadingBox(modifier)
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            BackHandler(onBack = onBack)
            PageHeader(current.name)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Action(Icons.Outlined.Edit, stringResource(R.string.common_edit)) { onEdit(current) }
                Action(
                    Icons.Outlined.Archive,
                    stringResource(
                        if (current.isArchived) R.string.wallets_unarchive
                        else R.string.wallets_archive,
                    ),
                ) {
                    scope.launch {
                        runCatching { repository.archiveWallet(current.id, !current.isArchived) }
                        reloadKey++
                    }
                }
                Action(
                    Icons.Outlined.Delete,
                    stringResource(R.string.common_delete),
                    colors.danger,
                ) {
                    scope.launch {
                        runCatching { repository.deleteWallet(current.id) }
                        onBack()
                    }
                }
            }
        }

        item { BalanceCard(current, credit, pockets, hide) }

        credit?.let { summary ->
            item {
                CreditPanel(
                    summary = summary,
                    hide = hide,
                    onPay = { paying = summary },
                    onAddPlan = { addingMsi = true },
                    onDeletePlan = { plan ->
                        scope.launch {
                            runCatching { repository.deleteMsiPlan(plan.id) }
                            reloadKey++
                        }
                    },
                )
            }
        }

        // Pockets of this wallet, with the same card the list uses. Apartados
        // stay one level deep, so a pocket has none of its own.
        if (current.parentWalletId == null) {
            item {
                SectionHeader(
                    stringResource(R.string.wallets_apartados_label),
                    stringResource(R.string.wallets_add_apartado),
                ) { addingPocket = true }
            }
            items(pockets, key = { "pocket-${it.id}" }) { pocket ->
                WalletCard(pocket, hide, onOpen = {}, onLongPress = {})
            }
        }

        if (goals.isNotEmpty()) {
            item {
                WalletGoalsSection(
                    repository = repository,
                    walletId = current.id,
                    goals = goals,
                    wallets = allWallets,
                    onChanged = { reloadKey++ },
                )
            }
        }

        item {
            SectionHeader(
                stringResource(R.string.transactions_title),
                stringResource(R.string.transactions_new_transaction),
            ) { addingTx = true }
        }
        item {
            SegmentedControl(
                options = KindFilter.entries,
                selected = txKind,
                label = { stringResource(it.labelRes) },
                onSelect = { txKind = it },
                modifier = Modifier.fillMaxWidth(),
                fillEqually = true,
            )
        }
        item {
            ChipButton(
                text = PeriodLabel(txPeriod),
                onClick = { showPeriod = true },
                leadingIcon = Lucide.CalendarRange,
                trailingIcon = Lucide.ChevronDown,
            )
        }
        totals?.let { summary -> item { TransactionTotal(summary, hide) } }
        if (movements.isNotEmpty()) {
            item {
                TransactionListCard(
                    transactions = movements,
                    hide = hide,
                    onLongPress = {},
                    onEdit = { editingTx = it },
                    onDelete = { deletingTx = it },
                    // Every row here belongs to this wallet already.
                    showWallet = false,
                )
            }
        }
    }

    if (showPeriod) {
        PeriodPickerDialog(
            selected = txPeriod,
            onSelect = { txPeriod = it; showPeriod = false },
            onDismiss = { showPeriod = false },
            allowAll = true,
        )
    }

    if (addingPocket) {
        WalletFormSheet(
            repository = repository,
            existing = null,
            wallets = allWallets,
            parentDefault = current,
            onDismiss = { addingPocket = false },
            onSaved = { addingPocket = false; reloadKey++ },
        )
    }

    // Paying the card is a transfer into it, opened already pointing here with
    // what clears the statement (or the whole debt) prefilled.
    paying?.let { summary ->
        val owed = if (summary.statement.remainingCents > 0) {
            summary.statement.remainingCents
        } else {
            summary.debtCents
        }
        TransactionFormSheet(
            repository = repository,
            wallets = allWallets,
            onDismiss = { paying = null },
            onSaved = { paying = null; reloadKey++ },
            defaultKind = TxKind.Transfer,
            defaultToWalletId = current.id,
            defaultAmountText = if (owed > 0) {
                java.math.BigDecimal(owed).movePointLeft(2).setScale(2).toPlainString()
            } else {
                ""
            },
        )
    }

    if (addingTx) {
        TransactionFormSheet(
            repository = repository,
            wallets = allWallets,
            onDismiss = { addingTx = false },
            onSaved = { addingTx = false; reloadKey++ },
        )
    }

    editingTx?.let { target ->
        TransactionEditSheet(
            repository = repository,
            transaction = target,
            wallets = allWallets,
            onDismiss = { editingTx = null },
            onSaved = { editingTx = null; reloadKey++ },
        )
    }

    deletingTx?.let { target ->
        ConfirmDialog(
            title = stringResource(R.string.common_delete),
            message = stringResource(R.string.transactions_delete_confirm),
            onConfirm = {
                deletingTx = null
                scope.launch {
                    runCatching { repository.deleteTransaction(target.id) }
                    reloadKey++
                }
            },
            onDismiss = { deletingTx = null },
        )
    }

    if (addingMsi) {
        wallet?.let { card ->
            MsiPlanSheet(
                repository = repository,
                wallet = card,
                onDismiss = { addingMsi = false },
                onSaved = { schedule ->
                    addingMsi = false
                    savedMsi = schedule
                    reloadKey++
                },
            )
        }
    }

    savedMsi?.let { schedule ->
        AlertDialog(
            onDismissRequest = { savedMsi = null },
            containerColor = Broke.colors.surfaceOverlay,
            title = { Text(stringResource(R.string.credit_msi_saved_title), color = Broke.colors.fg) },
            text = {
                Column {
                    MsiSavedInfo(schedule, wallet?.currencyCode ?: "MXN")
                }
            },
            confirmButton = {
                TextButton(onClick = { savedMsi = null }) {
                    Text(stringResource(R.string.common_close), color = Broke.colors.fgMuted)
                }
            },
        )
    }
}

@Composable
private fun Action(
    icon: ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color? = null,
    onClick: () -> Unit,
) {
    val colors = Broke.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint ?: colors.fgMuted, modifier = Modifier.width(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = tint ?: colors.fg)
    }
}

@Composable
private fun BalanceCard(
    wallet: Wallet,
    credit: CreditCardSummary?,
    pockets: List<Wallet>,
    hide: Boolean,
) {
    val colors = Broke.colors
    // Same reading as the card in the list, so the figure does not change when
    // you tap it: pocket money left the balance through a transfer, so it is
    // added back here too. Only same-currency pockets add up.
    val pocketsCents = pockets
        .filter { it.currencyCode == wallet.currencyCode }
        .sumOf { it.balanceCents }
    val reserved = wallet.reservedCents + pocketsCents

    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(parseHexColor(wallet.color) ?: colors.fgSubtle, 12.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                // Seeded category names arrive in Spanish whatever the app's
                // language, so they go through the shared translation.
                "${seedName(wallet.categoryName).orEmpty()} · ${wallet.currencyCode}",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = colors.fgMuted,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            // For a card the web leads with available credit, not the negative
            // balance, so the debt is only stated once — in its own panel.
            maskIfHidden(
                formatMoney(
                    credit?.availableCreditCents ?: (wallet.balanceCents + pocketsCents),
                    wallet.currencyCode,
                ),
                hide,
            ),
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 30.sp),
            color = colors.fg,
        )
        credit?.creditLimitCents?.let { limit ->
            Text(
                text(R.string.credit_available_of_limit).replace(
                    "{limit}",
                    maskIfHidden(formatMoney(limit, wallet.currencyCode), hide),
                ),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                color = colors.fgSubtle,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        // The web prints this on a card too, under the available credit.
        if (reserved > 0) {
            Text(
                stringResource(R.string.wallets_available) + " " +
                    maskIfHidden(
                        formatMoney(wallet.balanceCents - wallet.reservedCents, wallet.currencyCode),
                        hide,
                    ) + " · " + stringResource(R.string.wallets_reserved) + " " +
                    maskIfHidden(formatMoney(reserved, wallet.currencyCode), hide),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                color = colors.fgSubtle,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        // On a credit card the opening balance is registered debt, so calling it
        // "initial balance" would read wrong.
        if (wallet.creditCutDay != null) {
            if (wallet.initialBalanceCents < 0) {
                Text(
                    stringResource(R.string.credit_initial_debt_line) + ": " +
                        maskIfHidden(
                            formatMoney(-wallet.initialBalanceCents, wallet.currencyCode),
                            hide,
                        ),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                    color = colors.fgSubtle,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        } else {
            Text(
                stringResource(R.string.wallets_initial_balance) + ": " +
                    maskIfHidden(formatMoney(wallet.initialBalanceCents, wallet.currencyCode), hide),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                color = colors.fgSubtle,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        wallet.notes?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = colors.fgMuted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun CreditPanel(
    summary: CreditCardSummary,
    hide: Boolean,
    onPay: () -> Unit,
    onAddPlan: () -> Unit,
    onDeletePlan: (MsiPlan) -> Unit,
) {
    val colors = Broke.colors
    val st = summary.statement
    // The web colours this block red once the deadline is inside three days.
    val urgent = st.remainingCents > 0 && st.daysToDue <= 3

    GlassCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Lucide.CreditCard,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.credit_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.fg,
                )
            }
            Text(
                "${stringResource(R.string.credit_next_cut)}: " +
                    formatDayMonth(summary.nextCutDate) + " · " + inDays(summary.daysToCut),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                color = colors.fgSubtle,
            )
        }

        Spacer(Modifier.height(14.dp))
        MicroLabel(stringResource(R.string.credit_debt))
        Text(
            maskIfHidden(formatMoney(summary.debtCents), hide),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = if (summary.debtCents > 0) colors.danger else colors.fg,
        )
        if (summary.pendingMsiCents > 0) {
            Text(
                "${stringResource(R.string.credit_msi_pending_total)}: " +
                    maskIfHidden(formatMoney(summary.pendingMsiCents), hide),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                color = colors.fgSubtle,
            )
        }
        // Paying the card is a transfer into it. The web offers it here always,
        // even at zero debt — paying ahead of the cut is a fine move — and the
        // phone had no way to do it at all.
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .offset(x = (-16).dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onPay)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Icon(
                Lucide.ArrowDownToLine,
                contentDescription = null,
                tint = colors.fg,
                modifier = Modifier.size(15.dp),
            )
            Text(
                stringResource(R.string.credit_pay_action),
                style = MaterialTheme.typography.labelLarge,
                color = colors.fg,
            )
        }

        Spacer(Modifier.height(16.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (urgent) colors.danger.copy(alpha = 0.10f) else colors.surfaceOverlay)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                stringResource(R.string.credit_statement) + " (" +
                    text(R.string.credit_statement_of, "date" to formatDayMonth(st.cutDate)) +
                    "): " + maskIfHidden(formatMoney(st.balanceCents), hide) +
                    if (st.paidCents > 0 && st.balanceCents > 0) {
                        " · " + stringResource(R.string.credit_paid_so_far) + ": " +
                            maskIfHidden(formatMoney(st.paidCents), hide)
                    } else {
                        ""
                    },
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                color = colors.fgSubtle,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                statementLine(st, hide),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = if (urgent) colors.danger else colors.fgMuted,
            )
        }

        summary.utilizationBps?.let { bps ->
            val fraction = bps / 10_000f
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MicroLabel(stringResource(R.string.credit_utilization))
                Text(
                    "${Math.round(fraction * 100)}% · " + text(
                        R.string.credit_utilization_of,
                        "used" to maskIfHidden(
                            formatMoney(summary.debtCents + summary.pendingMsiCents),
                            hide,
                        ),
                        "limit" to maskIfHidden(
                            formatMoney(summary.creditLimitCents ?: 0),
                            hide,
                        ),
                    ),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                    color = colors.fgMuted,
                )
            }
            Spacer(Modifier.height(6.dp))
            ProgressBar(bps, color = usageColor(fraction))
        }

        summary.nextAnniversary?.let { day ->
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Lucide.CalendarClock,
                    contentDescription = null,
                    tint = colors.fgSubtle,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "${stringResource(R.string.credit_next_anniversary)}: " + formatDayMonth(day),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                    color = colors.fgSubtle,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        HairLine()
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.credit_msi_title),
                style = MaterialTheme.typography.labelLarge,
                color = colors.fg,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onAddPlan() }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(
                    Lucide.Plus,
                    contentDescription = null,
                    tint = colors.fg,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    stringResource(R.string.credit_msi_add),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.fg,
                )
            }
        }
        if (summary.msiPlans.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.credit_msi_empty),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                color = colors.fgSubtle,
            )
        }
        summary.msiPlans.forEach { plan ->
            Spacer(Modifier.height(8.dp))
            MsiRow(plan, hide) { onDeletePlan(plan) }
        }
    }
}

/** What the statement box says, branch for branch as the web writes it. */
@Composable
private fun statementLine(st: CreditStatement, hide: Boolean): String = when {
    st.balanceCents == 0L -> stringResource(R.string.credit_no_statement_debt)
    st.remainingCents == 0L -> stringResource(R.string.credit_statement_paid)
    st.daysToDue < 0 -> text(R.string.credit_overdue_by, "days" to -st.daysToDue)
    st.daysToDue == 0 -> stringResource(R.string.credit_due_today)
    else -> text(
        R.string.credit_pay_by,
        "amount" to maskIfHidden(formatMoney(st.remainingCents), hide),
        "date" to formatDayMonth(st.dueDate),
    )
}

/** "hoy" / "mañana" / "en N días", the web's `inDays`. */
@Composable
private fun inDays(days: Int): String = when {
    days <= 0 -> stringResource(R.string.credit_today)
    days == 1 -> stringResource(R.string.credit_tomorrow)
    else -> text(R.string.credit_in_days, "days" to days)
}

/** Green under 30%, amber under 70%, red past it — the web's `usageColor`. */
private fun usageColor(fraction: Float) = when {
    fraction < 0.3f -> androidx.compose.ui.graphics.Color(0xFF34D399)
    fraction < 0.7f -> androidx.compose.ui.graphics.Color(0xFFF59E0B)
    else -> androidx.compose.ui.graphics.Color(0xFFEF4444)
}

@Composable
private fun MsiRow(plan: MsiPlan, hide: Boolean, onDelete: () -> Unit) {
    val colors = Broke.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceOverlay)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                plan.description,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.fg,
                modifier = Modifier.weight(1f),
            )
            Text(
                maskIfHidden(formatMoney(plan.monthlyCents), hide) + "/mo",
                style = MaterialTheme.typography.labelLarge,
                color = colors.fg,
            )
            Spacer(Modifier.width(10.dp))
            Icon(
                Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.common_delete),
                tint = colors.fgSubtle,
                modifier = Modifier.width(20.dp).clickable(onClick = onDelete),
            )
        }
        Spacer(Modifier.height(8.dp))
        // Progress is billed months over total months — both the server's.
        ProgressBar(
            progressBps = if (plan.months > 0) {
                (plan.billedMonths.toLong() * 10_000L) / plan.months
            } else {
                0L
            },
        )
        plan.nextChargeDate?.let { date ->
            Spacer(Modifier.height(6.dp))
            Text(
                text(
                    R.string.credit_msi_next_charge,
                    "amount" to maskIfHidden(formatMoney(plan.nextChargeCents ?: 0), hide),
                    "date" to date,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = colors.fgSubtle,
            )
        }
    }
}

/** Section heading with its action, the pair the web puts above each block. */
@Composable
private fun SectionHeader(title: String, action: String, onAction: () -> Unit) {
    val colors = Broke.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.fg)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onAction)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Icon(
                Lucide.Plus,
                contentDescription = null,
                tint = colors.fg,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                action,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                color = colors.fg,
            )
        }
    }
}

/** One goal reserved inside this wallet, as the web's section lists them. */
@Composable
private fun WalletGoalRow(goal: SavingsGoal, hide: Boolean) {
    val colors = Broke.colors
    val tint = parseHexColor(goal.color) ?: colors.accent
    GlassCard(Modifier.fillMaxWidth(), padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tint.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Lucide.PiggyBank,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(15.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                goal.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = colors.fg,
                modifier = Modifier.weight(1f),
            )
            Text(
                maskIfHidden(formatMoney(goal.savedCents, goal.currencyCode), hide) + " " +
                    stringResource(R.string.goals_of) + " " +
                    maskIfHidden(formatMoney(goal.targetCents, goal.currencyCode), hide),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                color = colors.fgSubtle,
            )
        }
        Spacer(Modifier.height(8.dp))
        ProgressBar(goal.progressBps, color = tint)
    }
}
