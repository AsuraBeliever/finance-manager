package com.asura.finanzas.ui.transactions

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asura.finanzas.ui.components.FormSheet
import com.asura.finanzas.ui.components.SegStyle
import com.asura.finanzas.ui.components.SegmentedControl
import com.asura.finanzas.ui.components.WebCheckbox
import com.asura.finanzas.ui.components.FieldHint
import com.asura.finanzas.ui.components.FormField
import com.asura.finanzas.ui.components.MoneyField
import com.asura.finanzas.R
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.MsiSchedulePreview
import com.asura.finanzas.data.NetworkException
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

enum class TxKind { Income, Expense, Transfer }

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
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val colors = Broke.colors

    val spendable = remember(wallets) { wallets.filter { !it.isArchived } }

    var kind by remember { mutableStateOf(defaultKind) }
    var wallet by remember {
        // Never default the source to the card being paid.
        mutableStateOf(spendable.firstOrNull { it.id != defaultToWalletId })
    }
    var toWallet by remember {
        mutableStateOf(spendable.firstOrNull { it.id == defaultToWalletId })
    }
    var amount by remember { mutableStateOf(defaultAmountText) }
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
    var queued by remember { mutableStateOf(false) }
    var categories by remember { mutableStateOf<List<TransactionCategory>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val offlineError = stringResource(R.string.offline_banner)
    val needsDescription = stringResource(R.string.credit_msi_needs_description)
    val invalidMonths = stringResource(R.string.credit_msi_invalid_months)

    // Income and expense have separate category sets, same as the web form.
    LaunchedEffect(kind) {
        category = null
        categories = if (kind == TxKind.Transfer) {
            emptyList()
        } else {
            val wanted = if (kind == TxKind.Income) "income" else "expense"
            runCatching { repository.transactionCategories(wanted) }.getOrDefault(emptyList())
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
        if (source == null || cents == null || cents <= 0) {
            error = requiredError
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
                        // Queued rather than sent: say so instead of closing
                        // silently, or it looks like nothing happened.
                        wasQueued -> queued = true
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

    val amountCents = parseAmountToCents(amount)
    // The web's own guard, no stricter: an empty amount is caught on submit,
    // not by greying the button out — the two forms have to look the same the
    // moment they open.
    val canSave = !busy &&
        (!msiActive || (description.isNotBlank() && msiMonthsValid)) &&
        wallets.isNotEmpty() &&
        (kind != TxKind.Transfer || toWallet != null)

    // The shared dialog carries the title, the X, the divider and the
    // Cancel/Save pair, so this file is only its fields — same as the web,
    // where every form is a `<Modal>` with the fields inside.
    FormSheet(
        title = stringResource(
            if (defaultToWalletId != null && toWallet?.creditCutDay != null) {
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
            // The web leads with a full-width segmented control, not three pills.
            SegmentedControl(
                style = SegStyle.Tray,
                options = listOf(TxKind.Income, TxKind.Expense, TxKind.Transfer),
                selected = kind,
                label = {
                    stringResource(
                        when (it) {
                            TxKind.Income -> R.string.transactions_income
                            TxKind.Expense -> R.string.transactions_expense
                            else -> R.string.transactions_transfer
                        },
                    )
                },
                onSelect = { kind = it },
                modifier = Modifier.fillMaxWidth(),
                fillEqually = true,
            )

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
                PickerField(
                    label = stringResource(R.string.transactions_to_wallet),
                    options = spendable.filter { it.id != wallet?.id },
                    selected = toWallet,
                    optionLabel = { walletLabel(it, wallets) },
                    onSelect = { toWallet = it },
                    // Nothing is picked to start with, and an empty box says
                    // nothing; the web puts the hint in the closed select.
                    emptyLabel = stringResource(R.string.transactions_pick_to_wallet_hint),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            MoneyField(
                label = stringResource(R.string.transactions_amount),
                value = amount,
                onValueChange = { amount = it; error = null },
                modifier = Modifier.fillMaxWidth(),
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
                PickerField(
                    label = stringResource(R.string.transactions_category),
                    options = categories,
                    selected = category,
                    optionLabel = { seedName(it.name, it.isSystem).orEmpty() },
                    onSelect = { category = it },
                    emptyLabel = stringResource(R.string.transactions_no_category),
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

    if (queued) {
        AlertDialog(
            onDismissRequest = { queued = false; onSaved() },
            containerColor = colors.surfaceOverlay,
            title = { Text(stringResource(R.string.common_offline), color = colors.fg) },
            text = {
                Text(
                    stringResource(R.string.offline_saved_pending),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.fgMuted,
                )
            },
            confirmButton = {
                TextButton(onClick = { queued = false; onSaved() }) {
                    Text(stringResource(R.string.common_close), color = colors.fgMuted)
                }
            },
        )
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
