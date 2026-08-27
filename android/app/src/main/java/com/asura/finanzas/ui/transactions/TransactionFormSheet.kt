package com.asura.finanzas.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.asura.finanzas.R
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.NetworkException
import com.asura.finanzas.data.TransactionCategory
import com.asura.finanzas.data.Wallet
import com.asura.finanzas.ui.components.DateField
import com.asura.finanzas.ui.components.PickerField
import com.asura.finanzas.ui.parseAmountToCents
import com.asura.finanzas.ui.theme.Broke
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

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
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val colors = Broke.colors

    val spendable = remember(wallets) { wallets.filter { !it.isArchived } }

    var kind by remember { mutableStateOf(TxKind.Expense) }
    var wallet by remember { mutableStateOf(spendable.firstOrNull()) }
    var toWallet by remember { mutableStateOf<Wallet?>(null) }
    var amount by remember { mutableStateOf("") }
    var amountTo by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<TransactionCategory?>(null) }
    var description by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var categories by remember { mutableStateOf<List<TransactionCategory>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

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

    // A cross-currency transfer needs both legs; same currency mirrors the amount.
    val crossCurrency = kind == TxKind.Transfer &&
        wallet != null && toWallet != null &&
        wallet!!.currencyCode != toWallet!!.currencyCode

    fun save() {
        val source = wallet
        val cents = parseAmountToCents(amount)
        if (busy) return
        if (source == null || cents == null || cents <= 0) {
            error = null
            return
        }
        busy = true
        error = null
        val clientId = UUID.randomUUID().toString()

        scope.launch {
            val result = runCatching {
                when (kind) {
                    TxKind.Income -> repository.addIncome(
                        source.id, cents, date.toString(), category?.id,
                        description, null, clientId,
                    )
                    TxKind.Expense -> repository.addExpense(
                        source.id, cents, date.toString(), category?.id,
                        description, null, clientId,
                    )
                    TxKind.Transfer -> {
                        val target = toWallet ?: error("sin destino")
                        val received = if (crossCurrency) {
                            parseAmountToCents(amountTo) ?: error("monto recibido inválido")
                        } else {
                            cents
                        }
                        repository.addTransfer(
                            source.id, target.id, cents, received,
                            date.toString(), description, null, clientId,
                        )
                    }
                }
            }
            result
                .onSuccess { onSaved() }
                .onFailure {
                    error = when (it) {
                        is NetworkException -> null
                        else -> it.message
                    } ?: "Sin conexión"
                    busy = false
                }
        }
    }

    val amountCents = parseAmountToCents(amount)
    val canSave = !busy &&
        wallet != null &&
        amountCents != null && amountCents > 0 &&
        (kind != TxKind.Transfer || (toWallet != null && toWallet?.id != wallet?.id &&
            (!crossCurrency || parseAmountToCents(amountTo).let { it != null && it > 0 })))

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceOverlay,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(R.string.transactions_new_transaction),
                style = MaterialTheme.typography.titleMedium,
                color = colors.fg,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KindChip(TxKind.Expense, kind, R.string.transactions_expense) { kind = it }
                KindChip(TxKind.Income, kind, R.string.transactions_income) { kind = it }
                KindChip(TxKind.Transfer, kind, R.string.transactions_transfer) { kind = it }
            }

            PickerField(
                label = stringResource(
                    if (kind == TxKind.Transfer) R.string.transactions_from_wallet
                    else R.string.transactions_wallet,
                ),
                options = spendable,
                selected = wallet,
                optionLabel = { "${it.name} · ${it.currencyCode}" },
                onSelect = { wallet = it },
                modifier = Modifier.fillMaxWidth(),
            )

            if (kind == TxKind.Transfer) {
                PickerField(
                    label = stringResource(R.string.transactions_to_wallet),
                    options = spendable.filter { it.id != wallet?.id },
                    selected = toWallet,
                    optionLabel = { "${it.name} · ${it.currencyCode}" },
                    onSelect = { toWallet = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it; error = null },
                label = { Text(stringResource(R.string.transactions_amount)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                suffix = { wallet?.let { Text(it.currencyCode, color = colors.fgSubtle) } },
                modifier = Modifier.fillMaxWidth(),
            )

            if (crossCurrency) {
                OutlinedTextField(
                    value = amountTo,
                    onValueChange = { amountTo = it; error = null },
                    label = { Text(stringResource(R.string.transactions_amount_received)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    suffix = { toWallet?.let { Text(it.currencyCode, color = colors.fgSubtle) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (kind != TxKind.Transfer) {
                PickerField(
                    label = stringResource(R.string.transactions_category),
                    options = categories,
                    selected = category,
                    optionLabel = { it.name },
                    onSelect = { category = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.transactions_description)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            DateField(
                label = stringResource(R.string.transactions_date),
                value = date,
                onChange = { date = it },
            )

            error?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.danger)
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { save() },
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(20.dp))
                } else {
                    Text(stringResource(R.string.common_save))
                }
            }
        }
    }
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
