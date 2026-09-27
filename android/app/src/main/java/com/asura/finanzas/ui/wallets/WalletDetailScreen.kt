package com.asura.finanzas.ui.wallets

import com.asura.finanzas.ui.components.SegmentedControl
import com.asura.finanzas.ui.components.PeriodPicker
import com.asura.finanzas.ui.components.PeriodLabel
import com.asura.finanzas.ui.components.Period
import com.asura.finanzas.ui.components.ChipButton
import com.asura.finanzas.ui.theme.tabular
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
import androidx.compose.ui.unit.em
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
import com.asura.finanzas.ui.components.EmptyState
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
import com.asura.finanzas.data.TX_LIST_LIMIT
import androidx.compose.ui.text.style.TextAlign
import com.asura.finanzas.ui.text
import com.asura.finanzas.ui.theme.Broke
import kotlinx.coroutines.launch
import com.asura.finanzas.ui.components.PanelCard
import com.asura.finanzas.ui.components.cssLineBox
import com.asura.finanzas.data.TransactionCategory
import com.asura.finanzas.ui.components.PickerField
import com.asura.finanzas.ui.components.GhostButton
import androidx.compose.foundation.lazy.itemsIndexed

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
    /** Opens another wallet's page — an apartado card here is a link. */
    onOpenWallet: (Long) -> Unit = {},
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
    // Non-null while the "delete this MSI plan?" confirmation is up.
    var deletingMsi by remember { mutableStateOf<MsiPlan?>(null) }
    // Non-null while the "pay the card" form is up.
    var paying by remember { mutableStateOf<CreditCardSummary?>(null) }
    // The schedule the server confirmed, shown once after saving: nothing
    // visible happens at save time otherwise, since instalments post later.
    var savedMsi by remember { mutableStateOf<MsiSchedulePreview?>(null) }
    // The web filters this page's ledger just like the movements page does.
    var txKind by remember { mutableStateOf<KindFilter>(KindFilter.All) }
    var txPeriod by remember { mutableStateOf<Period>(Period.AllTime) }
    var totals by remember { mutableStateOf<TxTotals?>(null) }
    // The shared filter bar's category select, for income/expense.
    var txCategory by remember { mutableStateOf<TransactionCategory?>(null) }
    var categories by remember { mutableStateOf<List<TransactionCategory>>(emptyList()) }
    var confirmingDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        categories = runCatching { repository.filterCategories() }.getOrDefault(emptyList())
    }

    LaunchedEffect(walletId, reloadKey, txKind, txPeriod, txCategory) {
        wallet = runCatching { repository.wallet(walletId) }.getOrNull()
        movements = runCatching {
            repository.transactions(
                walletId = walletId,
                kind = txKind.wire,
                categoryId = txCategory?.id,
                period = txPeriod.toJson(),
            ).value
        }.getOrDefault(emptyList())
        // The web only totals a single-kind filter; "all" has nothing to add up.
        totals = txKind.wire?.takeIf { it == "income" || it == "expense" }?.let { kind ->
            runCatching {
                repository.transactionTotals(kind, walletId, txCategory?.id, txPeriod.toJson())
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

    // No uniform gap: each block carries the web's own margin — the header's
    // mb-7, the balance card's and each section's mb-6, a heading's mb-3, the
    // filter column's gap-3 + mb-4, the total's mb-4.
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 28.dp),
    ) {
        item {
            BackHandler(onBack = onBack)
            // The three actions are the header's, not a row under it: the web
            // hands them to `PageHeader` as `actions`, so they ride the title's
            // line and only drop below when the name is long enough to push
            // them off it.
            PageHeader(current.name, Modifier.padding(bottom = 28.dp), actionGap = 8.dp) {
                GhostButton(
                    stringResource(R.string.common_edit),
                    onClick = { onEdit(current) },
                    leadingIcon = Lucide.Pencil,
                )
                GhostButton(
                    stringResource(
                        if (current.isArchived) R.string.wallets_unarchive
                        else R.string.wallets_archive,
                    ),
                    onClick = {
                        scope.launch {
                            runCatching { repository.archiveWallet(current.id, !current.isArchived) }
                            reloadKey++
                        }
                    },
                    leadingIcon = if (current.isArchived) Lucide.ArchiveRestore else Lucide.Archive,
                )
                // Deleting takes the whole history with it, so it asks first.
                GhostButton(
                    stringResource(R.string.common_delete),
                    onClick = { confirmingDelete = true },
                    leadingIcon = Lucide.Trash,
                    tint = colors.danger,
                )
            }
        }

        item { BalanceCard(current, credit, pockets, hide, Modifier.padding(bottom = 24.dp)) }

        credit?.let { summary ->
            item {
                CreditPanel(
                    summary = summary,
                    currency = current.currencyCode,
                    hide = hide,
                    onPay = { paying = summary },
                    onAddPlan = { addingMsi = true },
                    // Deleting a plan also deletes the instalments it already
                    // posted, so it asks first — the web does too.
                    onDeletePlan = { deletingMsi = it },
                    modifier = Modifier.padding(bottom = 24.dp),
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
                    Modifier.padding(bottom = if (pockets.isEmpty()) 24.dp else 12.dp),
                ) { addingPocket = true }
            }
            itemsIndexed(pockets, key = { _, it -> "pocket-${it.id}" }) { index, pocket ->
                Box(
                    Modifier.padding(bottom = if (index == pockets.lastIndex) 24.dp else 16.dp),
                ) {
                    // A link on the web: it opens that apartado's own page.
                    WalletCard(pocket, hide, onOpen = { onOpenWallet(it.id) }, onLongPress = {})
                }
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
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            }
        }

        item {
            SectionHeader(
                stringResource(R.string.transactions_title),
                stringResource(R.string.transactions_new_transaction),
                Modifier.padding(bottom = 12.dp),
            ) { addingTx = true }
        }
        item {
            SegmentedControl(
                options = KindFilter.entries,
                selected = txKind,
                label = { stringResource(it.labelRes) },
                onSelect = { txKind = it; txCategory = null },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                fillEqually = true,
            )
        }
        // Category only applies to income/expense, scoped to the chosen kind.
        if (txKind == KindFilter.Income || txKind == KindFilter.Expense) {
            item {
                PickerField(
                    label = "",
                    options = listOf<TransactionCategory?>(null) +
                        categories.filter { it.kind == txKind.wire },
                    selected = txCategory,
                    optionLabel = {
                        it?.let { c -> seedName(c.name, c.isSystem) }
                            ?: stringResource(R.string.transactions_all_categories)
                    },
                    onSelect = { txCategory = it },
                    emptyLabel = stringResource(R.string.transactions_all_categories),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                )
            }
        }
        item {
            PeriodPicker(
                value = txPeriod,
                onChange = { txPeriod = it },
                allowAll = true,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
        totals?.let { summary ->
            item {
                Box(Modifier.padding(bottom = 16.dp)) {
                    TransactionTotal(summary, hide, income = txKind.wire == "income")
                }
            }
        }
        if (movements.isEmpty()) {
            // The web swaps the list for an empty state here, and says a
            // different thing when a filter is what emptied it.
            item {
                val filtered = txKind != KindFilter.All || txPeriod != Period.AllTime
                EmptyState(
                    Lucide.ArrowLeftRight,
                    stringResource(
                        if (filtered) R.string.transactions_no_match_title
                        else R.string.transactions_empty_title,
                    ),
                    stringResource(
                        if (filtered) R.string.transactions_no_match_description
                        else R.string.transactions_empty_description,
                    ),
                )
            }
        } else {
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
            if (movements.size >= TX_LIST_LIMIT) {
                item {
                    Text(
                        text(R.string.transactions_list_capped, "n" to TX_LIST_LIMIT),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.fgSubtle,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                }
            }
        }
    }

    if (confirmingDelete) {
        ConfirmDialog(
            title = stringResource(R.string.wallets_delete_confirm_title),
            message = stringResource(R.string.wallets_delete_confirm_message),
            onConfirm = {
                confirmingDelete = false
                scope.launch {
                    runCatching { repository.deleteWallet(current.id) }
                    onBack()
                }
            },
            onDismiss = { confirmingDelete = false },
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
            // Opened from a wallet, the form starts on that wallet.
            defaultWalletId = current.id,
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

    deletingMsi?.let { plan ->
        ConfirmDialog(
            title = stringResource(R.string.credit_msi_delete_title),
            message = stringResource(R.string.credit_msi_delete_message),
            onConfirm = {
                deletingMsi = null
                scope.launch {
                    runCatching { repository.deleteMsiPlan(plan.id) }
                    reloadKey++
                }
            },
            onDismiss = { deletingMsi = null },
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
                // "Understood", not "Close": this is an acknowledgement of a
                // schedule that was just created, which is how the web words it.
                TextButton(onClick = { savedMsi = null }) {
                    Text(
                        stringResource(R.string.credit_understood),
                        color = Broke.colors.accent,
                    )
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
    // The web's ghost `Button`: `rounded-lg px-4 py-2 text-sm` with a 15 px
    // glyph and `gap-2`. Without its padding the three sat visibly tighter
    // together than the same three on the web.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint ?: colors.fg, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
            color = tint ?: colors.fg,
        )
    }
}

@Composable
private fun BalanceCard(
    wallet: Wallet,
    credit: CreditCardSummary?,
    pockets: List<Wallet>,
    hide: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = Broke.colors
    // Same reading as the card in the list, so the figure does not change when
    // you tap it: pocket money left the balance through a transfer, so it is
    // added back here too. Only same-currency pockets add up.
    val pocketsCents = pockets
        .filter { it.currencyCode == wallet.currencyCode }
        .sumOf { it.balanceCents }
    val reserved = wallet.reservedCents + pocketsCents

    // Not a GlassCard: `rounded-xl border bg-surface-raised p-5`, no shadow.
    PanelCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // `#a8a29e` when the wallet has no colour of its own.
            Dot(parseHexColor(wallet.color) ?: androidx.compose.ui.graphics.Color(0xFFA8A29E), 12.dp)
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
            // `text-3xl font-semibold` in the body face, not the display one.
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 30.sp,
                lineHeight = 36.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            ).tabular(),
            color = colors.fg,
            modifier = Modifier.cssLineBox(36.dp),
        )
        credit?.creditLimitCents?.let { limit ->
            Text(
                text(R.string.credit_available_of_limit).replace(
                    "{limit}",
                    maskIfHidden(formatMoney(limit, wallet.currencyCode), hide),
                ),
                style = MaterialTheme.typography.bodySmall.tabular(),
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
                style = MaterialTheme.typography.bodySmall.tabular(),
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
                    style = MaterialTheme.typography.bodySmall.tabular(),
                    color = colors.fgSubtle,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        } else {
            Text(
                stringResource(R.string.wallets_initial_balance) + ": " +
                    maskIfHidden(formatMoney(wallet.initialBalanceCents, wallet.currencyCode), hide),
                style = MaterialTheme.typography.bodySmall.tabular(),
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
    // Every figure below is in the card's own money, as on the web — a card in
    // dollars was reading as pesos.
    currency: String,
    hide: Boolean,
    onPay: () -> Unit,
    onAddPlan: () -> Unit,
    onDeletePlan: (MsiPlan) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Broke.colors
    val st = summary.statement
    // The web colours this block red once the deadline is inside three days.
    val urgent = st.remainingCents > 0 && st.daysToDue <= 3

    // `text-xs uppercase tracking-[0.12em] text-fg-subtle` — the panel's own
    // caption, lighter and tighter than the `.eyebrow`.
    val caption = MaterialTheme.typography.bodySmall.copy(letterSpacing = 0.12.em)
    val xs = MaterialTheme.typography.bodySmall
    PanelCard(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
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
                style = xs,
                color = colors.fgSubtle,
            )
        }

        Text(stringResource(R.string.credit_debt).uppercase(), style = caption, color = colors.fgSubtle)
        Text(
            maskIfHidden(formatMoney(summary.debtCents, currency), hide),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 24.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.SemiBold,
            ).tabular(),
            color = if (summary.debtCents > 0) colors.danger else colors.fg,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (summary.pendingMsiCents > 0) {
            Text(
                "${stringResource(R.string.credit_msi_pending_total)}: " +
                    maskIfHidden(formatMoney(summary.pendingMsiCents, currency), hide),
                style = xs.tabular(),
                color = colors.fgSubtle,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        // Paying the card is a transfer into it, offered here always — even at
        // zero debt, paying ahead of the cut is a fine move. `mt-2 -ml-4`.
        GhostButton(
            stringResource(R.string.credit_pay_action),
            onClick = onPay,
            leadingIcon = Lucide.ArrowDownToLine,
            modifier = Modifier.padding(top = 8.dp).offset(x = (-16).dp),
        )

        // `mt-4 rounded-lg px-3 py-2.5 text-sm`, red once it is urgent.
        Column(
            Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (urgent) colors.danger.copy(alpha = 0.10f) else colors.surfaceOverlay)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                stringResource(R.string.credit_statement) + " (" +
                    text(R.string.credit_statement_of, "date" to formatDayMonth(st.cutDate)) +
                    "): " + maskIfHidden(formatMoney(st.balanceCents, currency), hide) +
                    if (st.paidCents > 0 && st.balanceCents > 0) {
                        " · " + stringResource(R.string.credit_paid_so_far) + ": " +
                            maskIfHidden(formatMoney(st.paidCents, currency), hide)
                    } else {
                        ""
                    },
                style = xs.tabular(),
                color = colors.fgSubtle,
            )
            Text(
                statementLine(st, currency, hide),
                style = MaterialTheme.typography.labelLarge,
                color = if (urgent) colors.danger else colors.fgMuted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        summary.utilizationBps?.let { bps ->
            val fraction = bps / 10_000f
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.credit_utilization).uppercase(), style = caption, color = colors.fgSubtle)
                Text(
                    "${Math.round(fraction * 100)}% · " + text(
                        R.string.credit_utilization_of,
                        "used" to maskIfHidden(
                            formatMoney(summary.debtCents + summary.pendingMsiCents, currency),
                            hide,
                        ),
                        "limit" to maskIfHidden(
                            formatMoney(summary.creditLimitCents ?: 0, currency),
                            hide,
                        ),
                    ),
                    style = xs.tabular(),
                    color = colors.fgMuted,
                )
            }
            ProgressBar(bps, color = usageColor(fraction))
        }

        summary.nextAnniversary?.let { day ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Icon(
                    Lucide.CalendarClock,
                    contentDescription = null,
                    tint = colors.fgSubtle,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "${stringResource(R.string.credit_next_anniversary)}: " + formatDayMonth(day),
                    style = xs,
                    color = colors.fgSubtle,
                )
            }
        }

        // `mt-5 border-t pt-4`, then a `mb-2` heading row.
        Spacer(Modifier.height(20.dp))
        HairLine()
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.credit_msi_title),
                style = MaterialTheme.typography.labelLarge,
                color = colors.fg,
            )
            GhostButton(
                stringResource(R.string.credit_msi_add),
                onClick = onAddPlan,
                leadingIcon = Lucide.Plus,
            )
        }
        if (summary.msiPlans.isEmpty()) {
            Text(
                stringResource(R.string.credit_msi_empty),
                style = xs,
                color = colors.fgSubtle,
            )
        }
        summary.msiPlans.forEachIndexed { i, plan ->
            if (i > 0) Spacer(Modifier.height(8.dp))
            MsiRow(plan, currency, hide) { onDeletePlan(plan) }
        }
    }
}

/** What the statement box says, branch for branch as the web writes it. */
@Composable
private fun statementLine(st: CreditStatement, currency: String, hide: Boolean): String = when {
    st.balanceCents == 0L -> stringResource(R.string.credit_no_statement_debt)
    st.remainingCents == 0L -> stringResource(R.string.credit_statement_paid)
    st.daysToDue < 0 -> text(R.string.credit_overdue_by, "days" to -st.daysToDue)
    st.daysToDue == 0 -> stringResource(R.string.credit_due_today)
    else -> text(
        R.string.credit_pay_by,
        "amount" to maskIfHidden(formatMoney(st.remainingCents, currency), hide),
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
private fun MsiRow(plan: MsiPlan, currency: String, hide: Boolean, onDelete: () -> Unit) {
    val colors = Broke.colors
    // `rounded-lg bg-surface-overlay px-3 py-2.5`.
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceOverlay)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                plan.description,
                style = MaterialTheme.typography.labelLarge,
                color = colors.fg,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            Text(
                text(
                    R.string.credit_msi_monthly,
                    "amount" to maskIfHidden(formatMoney(plan.monthlyCents, currency), hide),
                ),
                style = MaterialTheme.typography.bodyMedium.tabular(),
                color = colors.fgMuted,
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                Lucide.Trash,
                contentDescription = stringResource(R.string.common_delete),
                tint = colors.fgSubtle,
                modifier = Modifier.size(14.dp).clickable(onClick = onDelete),
            )
        }
        // Progress is billed months over total months — both the server's.
        ProgressBar(
            progressBps = if (plan.months > 0) {
                (plan.billedMonths.toLong() * 10_000L) / plan.months
            } else {
                0L
            },
            modifier = Modifier.padding(top = 8.dp),
            // One pip per instalment while they still fit; past two years the
            // web falls back to a plain bar, and so does this.
            segments = plan.months.takeIf { it in 1..24 },
        )

        // "3 of 12 instalments · next one on the 5th", or "· paid off" once the
        // last instalment has been billed. The web keeps it as one line.
        val done = plan.billedMonths >= plan.months
        val progress = text(
            R.string.credit_msi_progress,
            "billed" to plan.billedMonths,
            "months" to plan.months,
        )
        val tail = when {
            done -> stringResource(R.string.credit_msi_done)
            plan.nextChargeDate != null -> text(
                R.string.credit_msi_next_charge,
                "amount" to maskIfHidden(
                    formatMoney(plan.nextChargeCents ?: 0, currency),
                    hide,
                ),
                "date" to formatDayMonth(plan.nextChargeDate),
            )
            else -> null
        }
        Text(
            if (tail != null) "$progress · $tail" else progress,
            style = MaterialTheme.typography.bodySmall,
            color = colors.fgSubtle,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * Section heading with its action, the pair the web puts above each block:
 * `flex items-center justify-between`, an h3 in `font-medium` and a ghost
 * button with a plus.
 */
@Composable
private fun SectionHeader(
    title: String,
    action: String,
    modifier: Modifier = Modifier,
    onAction: () -> Unit,
) {
    val colors = Broke.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.fg)
        GhostButton(action, onClick = onAction, leadingIcon = Lucide.Plus)
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
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp).tabular(),
                color = colors.fgSubtle,
            )
        }
        Spacer(Modifier.height(8.dp))
        ProgressBar(goal.progressBps, color = tint)
    }
}
