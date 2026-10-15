package com.asura.finanzas.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asura.finanzas.ui.components.FormSheet
import com.asura.finanzas.ui.components.SegStyle
import com.asura.finanzas.ui.components.SegmentedControl
import com.asura.finanzas.ui.components.WebCheckbox
import com.asura.finanzas.data.CreditCardSummary
import com.asura.finanzas.ui.text
import com.asura.finanzas.ui.formatMoney
import com.asura.finanzas.ui.wallets.formatDayMonth
import com.asura.finanzas.ui.components.FieldHint
import com.asura.finanzas.ui.components.FormField
import com.asura.finanzas.ui.components.MoneyField
import com.asura.finanzas.R
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.MsiSchedulePreview
import com.asura.finanzas.data.NetworkException
import com.asura.finanzas.data.Transaction
import com.asura.finanzas.data.TransactionCategory
import com.asura.finanzas.data.Wallet
import com.asura.finanzas.ui.components.DateField
import com.asura.finanzas.ui.components.PickerField
import com.asura.finanzas.ui.components.walletLabel
import com.asura.finanzas.ui.LocalAppSettings
import com.asura.finanzas.ui.components.TimeField
import com.asura.finanzas.ui.parseAmountToCents
import com.asura.finanzas.ui.seedName
import com.asura.finanzas.ui.theme.Broke
import com.asura.finanzas.ui.wallets.MsiPreviewLines
import com.asura.finanzas.ui.wallets.MsiSavedInfo
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class TxKind { Income, Expense, Refund, Transfer }

/**
 * Capture sheet for the three append-only commands. Amounts are parsed to
 * integer cents here and sent as-is; nothing about the resulting balance is
 * computed on the phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionFormSheet(
    repository: BrokeRepository,
    wallets: List<Wallet>,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    /** Paying a credit card opens straight on the transfer tab, pointing at it
     *  with what clears the statement already filled in — the web's
     *  `defaultTab` / `defaultToWalletId` / `defaultAmountText`. */
    defaultKind: TxKind = TxKind.Income,
    defaultToWalletId: Long? = null,
    defaultAmountText: String = "",
    /** The wallet the form opens on — a wallet's own page passes itself. */
    defaultWalletId: Long? = null,
    /** Opens as a new refund of this expense: same wallet and category, and
     *  what is still refundable as the amount — the web's `refundOf`. */
    refundOf: Transaction? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val colors = Broke.colors

    val spendable = remember(wallets) { wallets.filter { !it.isArchived } }

    var kind by remember { mutableStateOf(if (refundOf != null) TxKind.Refund else defaultKind) }
    var wallet by remember {
        // Never default the source to the card being paid.
        mutableStateOf(
            spendable.firstOrNull { it.id == refundOf?.walletId }
                ?: spendable.firstOrNull { it.id == defaultWalletId && it.id != defaultToWalletId }
                ?: spendable.firstOrNull { it.id != defaultToWalletId },
        )
    }
    var toWallet by remember {
        mutableStateOf(spendable.firstOrNull { it.id == defaultToWalletId })
    }
    var amount by remember {
        mutableStateOf(
            refundOf?.refundableCents?.takeIf { it > 0 }
                ?.let { formatMoney(it, withSymbol = false) }
                ?: defaultAmountText,
        )
    }
    var amountTo by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<TransactionCategory?>(null) }
    var description by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    // Optional, like the web: a movement without a time falls back to its
    // createdAt rendered in the chosen timezone.
    // The web opens the form with the current time already in the box, in the
    // account's timezone; leaving it blank here made the same form look like it
    // had one field fewer filled in.
    val timezone = LocalAppSettings.current.timezone
    var time by remember {
        mutableStateOf<String?>(
            runCatching {
                java.time.LocalTime.now(java.time.ZoneId.of(timezone))
                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
            }.getOrNull(),
        )
    }
    // A new expense on a configured credit card can be an MSI purchase: instead
    // of one expense the worker creates a plan and each instalment posts itself
    // on its own statement. Edits never convert to or from MSI.
    var msiEnabled by remember { mutableStateOf(false) }
    var msiMonths by remember { mutableStateOf("12") }
    var savedMsi by remember { mutableStateOf<MsiSchedulePreview?>(null) }
    var categories by remember { mutableStateOf<List<TransactionCategory>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val offlineError = stringResource(R.string.offline_banner)
    val needsDescription = stringResource(R.string.credit_msi_needs_description)
    val invalidMonths = stringResource(R.string.credit_msi_invalid_months)
    val pickToWallet = stringResource(R.string.transactions_pick_to_wallet)
    val invalidAmount = stringResource(R.string.transactions_invalid_amount)

    // Income and expense have separate category sets, same as the web form;
    // a refund files under the expense category it gives money back to.
    LaunchedEffect(kind) {
        category = null
        categories = if (kind == TxKind.Transfer) {
            emptyList()
        } else {
            val wanted = if (kind == TxKind.Income) "income" else "expense"
            runCatching { repository.transactionCategories(wanted) }.getOrDefault(emptyList())
        }
        if (kind == TxKind.Refund && refundOf != null) {
            category = categories.firstOrNull { it.id == refundOf.categoryId }
        }
    }

    val requiredError = stringResource(R.string.common_required)
    val isCreditWallet = wallet?.creditCutDay != null
    val msiActive = kind == TxKind.Expense && isCreditWallet && msiEnabled
    val msiMonthsValue = msiMonths.trim().toIntOrNull()
    val msiMonthsValid = msiMonthsValue != null && msiMonthsValue in 2..60

    // A cross-currency transfer needs both legs; same currency mirrors the amount.
    val crossCurrency = kind == TxKind.Transfer &&
        wallet != null && toWallet != null &&
        wallet!!.currencyCode != toWallet!!.currencyCode

    fun save() {
        val source = wallet
        val cents = parseAmountToCents(amount)
        if (busy) return
        // Save stays live, as it does in the browser, where the amount input is
        // `required` and the form simply refuses to submit with a complaint.
        // The browser splits that complaint in two and so does this: an empty
        // box is "fill this in", a box with 0 or junk in it is "bad amount".
        if (source == null || amount.isBlank()) {
            error = requiredError
            return
        }
        if (cents == null || cents <= 0) {
            error = invalidAmount
            return
        }
        // Same complaint the web throws, and for the same two cases.
        if (kind == TxKind.Transfer && (toWallet == null || toWallet?.id == source.id)) {
            error = pickToWallet
            return
        }
        busy = true
        error = null

        scope.launch {
            var wasQueued = false
            val result = runCatching {
                when (kind) {
                    TxKind.Income -> wasQueued = repository.addIncome(
                        source.id, cents, date.toString(), category?.id,
                        description, time,
                    )
                    TxKind.Expense -> if (msiActive) {
                        if (description.isBlank()) error(needsDescription)
                        if (!msiMonthsValid) error(invalidMonths)
                        savedMsi = repository.createMsiPlan(
                            walletId = source.id,
                            description = description,
                            totalCents = cents,
                            months = msiMonthsValue ?: 0,
                            purchasedAt = date.toString(),
                            categoryId = category?.id,
                        )
                    } else {
                        wasQueued = repository.addExpense(
                            source.id, cents, date.toString(), category?.id,
                            description, time,
                        )
                    }
                    TxKind.Refund -> wasQueued = repository.addRefund(
                        source.id, cents, date.toString(), category?.id,
                        description, time, refundOf?.id,
                    )
                    TxKind.Transfer -> {
                        val target = toWallet ?: error("sin destino")
                        val received = if (crossCurrency) {
                            parseAmountToCents(amountTo) ?: error("monto recibido inválido")
                        } else {
                            cents
                        }
                        wasQueued = repository.addTransfer(
                            source.id, target.id, cents, received,
                            date.toString(), description, time,
                        )
                    }
                }
            }
            result
                .onSuccess {
                    when {
                        savedMsi != null -> busy = false
                        // Queued or sent, the web just closes: the outbox
                        // panel behind it already lists the pending capture.
                        else -> onSaved()
                    }
                }
                .onFailure {
                    error = when (it) {
                        // Only a real refusal reaches here; a lost connection
                        // was already absorbed into the outbox.
                        is NetworkException -> offlineError
                        else -> it.message
                    } ?: offlineError
                    busy = false
                }
        }
    }

    // `autoFocus` on the web's amount input: the form opens ready to type,
    // with the box already outlined in the accent. Requested once, so moving
    // to another field does not snap back here.
    val amountFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { amountFocus.requestFocus() } }

    val amountCents = parseAmountToCents(amount)
    // The web's own guard, no stricter: an empty amount is caught on submit,
    // not by greying the button out — the two forms have to look the same the
    // moment they open.
    //
    // A transfer with nowhere to go is the exception, and it is the web's:
    // `disabled={… || (tab === "transfer" && toWalletId === null)}`. Leaving
    // Save live here meant the APK answered a tap with an error where the
    // browser had simply greyed the button out.
    val canSave = !busy &&
        (!msiActive || (description.isNotBlank() && msiMonthsValid)) &&
        (kind != TxKind.Transfer || toWallet != null) &&
        wallets.isNotEmpty()

    // The shared dialog carries the title, the X, the divider and the
    // Cancel/Save pair, so this file is only its fields — same as the web,
    // where every form is a `<Modal>` with the fields inside.
    FormSheet(
        title = stringResource(
            if (refundOf != null) {
                R.string.transactions_register_refund
            } else if (defaultToWalletId != null && toWallet?.creditCutDay != null) {
                R.string.credit_pay_title
            } else {
                R.string.transactions_new_transaction
            },
        ),
        busy = busy,
        error = error,
        canSave = canSave,
        onSave = { save() },
        onDismiss = onDismiss,
    ) {
        run {
            // The web leads with a full-width segmented control, not pills. A
            // refund of a known expense has no other kind to switch to.
            if (refundOf == null) SegmentedControl(
                style = SegStyle.Tray,
                options = listOf(TxKind.Income, TxKind.Expense, TxKind.Refund, TxKind.Transfer),
                selected = kind,
                label = {
                    stringResource(
                        when (it) {
                            TxKind.Income -> R.string.transactions_income
                            TxKind.Expense -> R.string.transactions_expense
                            TxKind.Refund -> R.string.transactions_refund
                            else -> R.string.transactions_transfer
                        },
                    )
                },
                onSelect = { kind = it },
                modifier = Modifier.fillMaxWidth(),
                fillEqually = true,
            )

            if (kind == TxKind.Refund) RefundInfo(refundOf, wallets)

            PickerField(
                label = stringResource(
                    if (kind == TxKind.Transfer) R.string.transactions_from_wallet
                    else R.string.transactions_wallet,
                ),
                options = spendable,
                selected = wallet,
                optionLabel = { walletLabel(it, wallets) },
                onSelect = { wallet = it },
                modifier = Modifier.fillMaxWidth(),
            )

            if (kind == TxKind.Transfer) {
                // Moving money between a wallet and its own pockets is the
                // common case, so the web lifts that family to the top of the
                // list under its own heading and files the rest under "other".
                val fromRootId = wallet?.let { it.parentWalletId ?: it.id }
                val destinations = spendable.filter { it.id != wallet?.id }
                val sameFamily = { w: Wallet ->
                    fromRootId != null && (w.id == fromRootId || w.parentWalletId == fromRootId)
                }
                val family = destinations.filter(sameFamily)
                val others = destinations.filterNot(sameFamily)
                val familyLabel = text(
                    R.string.transactions_to_wallet_same_family,
                    "wallet" to wallets.firstOrNull { it.id == fromRootId }?.name.orEmpty(),
                )
                val othersLabel = stringResource(R.string.transactions_to_wallet_others)

                PickerField(
                    label = stringResource(R.string.transactions_to_wallet),
                    options = family + others,
                    selected = toWallet,
                    optionLabel = { walletLabel(it, wallets) },
                    onSelect = { toWallet = it },
                    // Nothing is picked to start with, and an empty box says
                    // nothing; the web puts the hint in the closed select.
                    emptyLabel = stringResource(R.string.transactions_pick_to_wallet_hint),
                    // With no pockets to lift up it is one flat list, as it was.
                    optionGroup = if (family.isEmpty()) null else {
                        { w -> if (sameFamily(w)) familyLabel else othersLabel }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                // Paying a card: say what the last statement still owes and by
                // when, so the amount is not a guess. Every figure comes from
                // get_credit_card_summary; nothing is worked out here.
                toWallet?.takeIf { it.creditCutDay != null }?.let { card ->
                    val summary by produceState<CreditCardSummary?>(null, card.id) {
                        value = runCatching { repository.creditCardSummary(card.id) }.getOrNull()
                    }
                    summary?.let { s ->
                        FieldHint(
                            when {
                                s.statement.remainingCents > 0 -> text(
                                    R.string.credit_pay_context,
                                    "amount" to formatMoney(
                                        s.statement.remainingCents,
                                        card.currencyCode,
                                    ),
                                    "date" to formatDayMonth(s.statement.dueDate),
                                )
                                // Statement clear but the running cycle already
                                // owes (an MSI instalment, a fresh purchase).
                                s.debtCents > 0 -> text(
                                    R.string.credit_pay_context_debt,
                                    "debt" to formatMoney(s.debtCents, card.currencyCode),
                                )
                                else -> stringResource(R.string.credit_pay_context_paid)
                            },
                        )
                    }
                }
            }

            MoneyField(
                label = stringResource(R.string.transactions_amount),
                value = amount,
                onValueChange = { amount = it; error = null },
                modifier = Modifier.fillMaxWidth(),
                focusRequester = amountFocus,
            )

            // Date and time share a row, as on the web.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DateField(
                    label = stringResource(R.string.transactions_date),
                    value = date,
                    onChange = { date = it },
                    modifier = Modifier.weight(1f),
                )
                TimeField(
                    label = stringResource(R.string.transactions_time),
                    value = time,
                    onChange = { time = it },
                    modifier = Modifier.weight(1f),
                )
            }

            if (crossCurrency) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    MoneyField(
                        label = stringResource(R.string.transactions_amount_received) +
                            " (" + toWallet?.currencyCode.orEmpty() + ")",
                        value = amountTo,
                        onValueChange = { amountTo = it; error = null },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FieldHint(stringResource(R.string.transactions_transfer_hint))
                }
            }

            // Buying to months on a credit card. The web puts this between the
            // date and the category — the category below is what the monthly
            // charges file under — inside a bordered box, ticked with a
            // checkbox, and asks for the months in a half-width field.
            if (kind == TxKind.Expense && isCreditWallet) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, colors.borderMuted, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { msiEnabled = !msiEnabled },
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // The app's own tick box, drawn like the browser's:
                        // Material's is a different shape and 20 dp wide.
                        WebCheckbox(
                            checked = msiEnabled,
                            onCheckedChange = { msiEnabled = it },
                            boxSize = 16.dp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.credit_msi_toggle),
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                color = colors.fg,
                            )
                            FieldHint(stringResource(R.string.credit_msi_toggle_hint))
                        }
                    }

                    if (msiActive) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            FormField(
                                label = stringResource(R.string.credit_msi_months),
                                value = msiMonths,
                                onValueChange = { msiMonths = it.filter { c -> c.isDigit() }.take(2) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                isError = msiMonths.isNotBlank() && !msiMonthsValid,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )
                            Spacer(Modifier.weight(1f))
                        }
                        MsiPreview(repository, wallet, amountCents, msiMonthsValue, date, msiMonthsValid)
                    }
                }
            }

            if (kind != TxKind.Transfer) {
                val noCategory = stringResource(R.string.transactions_no_category)
                // "Uncategorized" is a choice in its own right, first in the
                // list, as the web's `<option value="">` is — so a pick can
                // be undone.
                PickerField(
                    label = stringResource(R.string.transactions_category),
                    options = listOf<TransactionCategory?>(null) + categories,
                    selected = category,
                    optionLabel = { c -> c?.let { seedName(it.name, it.isSystem) } ?: noCategory },
                    onSelect = { category = it },
                    emptyLabel = noCategory,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            FormField(
                label = stringResource(R.string.transactions_description),
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

        }
    }

    // Saving an MSI plan ends on a confirmation: nothing visible happens at
    // save time otherwise, since the instalments post later.
    savedMsi?.let { schedule ->
        AlertDialog(
            onDismissRequest = { savedMsi = null; onSaved() },
            containerColor = colors.surfaceOverlay,
            title = { Text(stringResource(R.string.credit_msi_saved_title), color = colors.fg) },
            text = {
                Column {
                    MsiSavedInfo(schedule, wallet?.currencyCode ?: "MXN")
                }
            },
            confirmButton = {
                TextButton(onClick = { savedMsi = null; onSaved() }) {
                    Text(stringResource(R.string.common_close), color = colors.fgMuted)
                }
            },
        )
    }
}

/**
 * The box atop a refund: what it is for (a linked expense and what is still
 * refundable, both from the worker), or what a refund means when unlinked —
 * the web's `rounded-lg bg-surface px-3 py-2 text-xs` note.
 */
@Composable
private fun RefundInfo(refundOf: Transaction?, wallets: List<Wallet>) {
    val colors = Broke.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (refundOf != null) {
            val currency = wallets.firstOrNull { it.id == refundOf.walletId }?.currencyCode ?: "MXN"
            Text(
                text(
                    R.string.transactions_refund_of,
                    "name" to (
                        refundOf.description?.takeIf { it.isNotBlank() }
                            ?: refundOf.categoryName?.let { seedName(it) }
                            ?: stringResource(R.string.transactions_expense)
                        ),
                ),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = colors.fg,
            )
            Text(
                text(
                    R.string.transactions_refund_remaining,
                    "amount" to formatMoney(refundOf.refundableCents, currency),
                    "total" to formatMoney(refundOf.amountCents, currency),
                ),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                color = colors.fgSubtle,
            )
        } else {
            Text(
                stringResource(R.string.transactions_refund_hint),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                color = colors.fgSubtle,
            )
        }
    }
}

/** Live schedule under the MSI fields; a failed preview simply shows nothing. */
@Composable
private fun MsiPreview(
    repository: BrokeRepository,
    wallet: Wallet?,
    totalCents: Long?,
    months: Int?,
    purchasedAt: LocalDate,
    valid: Boolean,
) {
    val preview by produceState<MsiSchedulePreview?>(
        null, wallet?.id, totalCents, months, purchasedAt, valid,
    ) {
        value = if (wallet == null || totalCents == null || totalCents <= 0 || !valid) {
            null
        } else {
            runCatching {
                repository.previewMsiPlan(wallet.id, totalCents, months!!, purchasedAt.toString())
            }.getOrNull()
        }
    }
    preview?.let { MsiPreviewLines(it, wallet?.currencyCode ?: "MXN") }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KindChip(value: TxKind, selected: TxKind, labelRes: Int, onSelect: (TxKind) -> Unit) {
    val colors = Broke.colors
    FilterChip(
        selected = value == selected,
        onClick = { onSelect(value) },
        label = { Text(stringResource(labelRes)) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = colors.accent,
            selectedLabelColor = colors.surface,
        ),
    )
}
