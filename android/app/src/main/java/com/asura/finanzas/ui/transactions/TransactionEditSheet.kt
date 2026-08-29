package com.asura.finanzas.ui.transactions

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.asura.finanzas.R
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.NetworkException
import com.asura.finanzas.data.Transaction
import com.asura.finanzas.data.TransactionCategory
import com.asura.finanzas.data.TransferDetail
import com.asura.finanzas.data.Wallet
import com.asura.finanzas.ui.components.DateField
import com.asura.finanzas.ui.components.FormSheet
import com.asura.finanzas.ui.components.PickerField
import com.asura.finanzas.ui.components.TimeField
import com.asura.finanzas.ui.formatMoney
import com.asura.finanzas.ui.parseAmountToCents
import com.asura.finanzas.ui.seedName
import com.asura.finanzas.ui.theme.Broke
import kotlinx.coroutines.launch
import java.time.LocalDate

/** True for the two legs of a transfer, which edit as a single linked pair. */
private val Transaction.isTransfer: Boolean
    get() = kind == "transfer_in" || kind == "transfer_out"

/**
 * A transfer leg with no group is the wallet side of an investment deposit or
 * withdrawal: the investment is not a wallet, so there is no sibling leg. It
 * needs the investment's own editor, because the amount has to move on both.
 */
private val Transaction.isInvestmentLeg: Boolean
    get() = isTransfer && transferGroupId == null

/**
 * Edit a movement. Income and expense edit in place; a transfer is two rows
 * sharing a group, so it loads both legs with `get_transfer` and saves them
 * together with `update_transfer` — the same split the web form makes.
 */
@Composable
fun TransactionEditSheet(
    repository: BrokeRepository,
    transaction: Transaction,
    wallets: List<Wallet>,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    if (transaction.isInvestmentLeg) {
        InvestmentLegEditSheet(repository, transaction, wallets, onDismiss, onSaved)
    } else if (transaction.isTransfer) {
        TransferEditSheet(repository, transaction, wallets, onDismiss, onSaved)
    } else {
        SimpleEditSheet(repository, transaction, wallets, onDismiss, onSaved)
    }
}

@Composable
private fun SimpleEditSheet(
    repository: BrokeRepository,
    transaction: Transaction,
    wallets: List<Wallet>,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val colors = Broke.colors

    var categories by remember { mutableStateOf<List<TransactionCategory>>(emptyList()) }
    var wallet by remember {
        mutableStateOf(wallets.firstOrNull { it.id == transaction.walletId })
    }
    var amount by remember {
        mutableStateOf(formatMoney(transaction.amountCents, withSymbol = false))
    }
    var category by remember { mutableStateOf<TransactionCategory?>(null) }
    var description by remember { mutableStateOf(transaction.description.orEmpty()) }
    var date by remember {
        mutableStateOf(
            runCatching { LocalDate.parse(transaction.occurredAt.take(10)) }
                .getOrDefault(LocalDate.now()),
        )
    }
    var time by remember { mutableStateOf(transaction.occurredTime) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val genericError = stringResource(R.string.common_error)
    val offlineError = stringResource(R.string.offline_banner)
    val noCategory = stringResource(R.string.transactions_no_category)

    LaunchedEffect(Unit) {
        categories = runCatching { repository.transactionCategories(transaction.kind) }
            .getOrDefault(emptyList())
        category = categories.firstOrNull { it.id == transaction.categoryId }
    }

    val cents = parseAmountToCents(amount)
    val canSave = !busy && wallet != null && cents != null && cents > 0

    FormSheet(
        title = stringResource(R.string.transactions_edit_transaction),
        busy = busy,
        error = error,
        canSave = canSave,
        onDismiss = onDismiss,
        onSave = {
            val chosen = wallet ?: return@FormSheet
            busy = true
            error = null
            scope.launch {
                runCatching {
                    repository.updateTransaction(
                        id = transaction.id,
                        walletId = chosen.id,
                        amountCents = cents ?: 0,
                        categoryId = category?.id,
                        description = description,
                        occurredAt = date.toString(),
                        occurredTime = time,
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
        PickerField(
            label = stringResource(R.string.transactions_wallet),
            options = wallets.filter { !it.isArchived },
            selected = wallet,
            optionLabel = { "${it.name} · ${it.currencyCode}" },
            onSelect = { wallet = it },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it; error = null },
            label = { Text(stringResource(R.string.transactions_amount)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            suffix = { wallet?.let { Text(it.currencyCode, color = colors.fgSubtle) } },
            modifier = Modifier.fillMaxWidth(),
        )
        PickerField(
            label = stringResource(R.string.transactions_category),
            options = listOf<TransactionCategory?>(null) + categories,
            selected = category,
            optionLabel = { it?.let { c -> seedName(c.name, c.isSystem) } ?: noCategory },
            onSelect = { category = it },
            emptyLabel = noCategory,
            modifier = Modifier.fillMaxWidth(),
        )
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
        TimeField(
            label = stringResource(R.string.transactions_time),
            value = time,
            onChange = { time = it },
        )
    }
}

/**
 * Both legs edit as one. The received amount is only asked for when the two
 * wallets hold different currencies; otherwise it mirrors the sent amount,
 * exactly like capture does.
 */
@Composable
private fun TransferEditSheet(
    repository: BrokeRepository,
    transaction: Transaction,
    wallets: List<Wallet>,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val colors = Broke.colors
    val spendable = remember(wallets) { wallets.filter { !it.isArchived } }

    var detail by remember { mutableStateOf<TransferDetail?>(null) }
    var fromWallet by remember { mutableStateOf<Wallet?>(null) }
    var toWallet by remember { mutableStateOf<Wallet?>(null) }
    var amountFrom by remember { mutableStateOf("") }
    var amountTo by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var time by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val genericError = stringResource(R.string.common_error)
    val offlineError = stringResource(R.string.offline_banner)

    // Prefill from the pair, not from the single leg that was tapped.
    LaunchedEffect(transaction.id) {
        runCatching { repository.getTransfer(transaction.id) }
            .onSuccess { loaded ->
                detail = loaded
                fromWallet = wallets.firstOrNull { it.id == loaded.fromWalletId }
                toWallet = wallets.firstOrNull { it.id == loaded.toWalletId }
                amountFrom = formatMoney(loaded.amountFromCents, withSymbol = false)
                amountTo = formatMoney(loaded.amountToCents, withSymbol = false)
                description = loaded.description.orEmpty()
                date = runCatching { LocalDate.parse(loaded.occurredAt.take(10)) }
                    .getOrDefault(LocalDate.now())
                time = loaded.occurredTime
            }
            .onFailure {
                error = if (it is NetworkException) offlineError else it.message ?: genericError
            }
    }

    val crossCurrency = fromWallet != null && toWallet != null &&
        fromWallet!!.currencyCode != toWallet!!.currencyCode
    val centsFrom = parseAmountToCents(amountFrom)
    val centsTo = if (crossCurrency) parseAmountToCents(amountTo) else centsFrom

    val canSave = !busy && detail != null &&
        fromWallet != null && toWallet != null && fromWallet?.id != toWallet?.id &&
        centsFrom != null && centsFrom > 0 &&
        centsTo != null && centsTo > 0

    FormSheet(
        title = stringResource(R.string.transactions_edit_transaction),
        busy = busy,
        error = error,
        canSave = canSave,
        onDismiss = onDismiss,
        onSave = {
            val loaded = detail ?: return@FormSheet
            val source = fromWallet ?: return@FormSheet
            val target = toWallet ?: return@FormSheet
            busy = true
            error = null
            scope.launch {
                runCatching {
                    repository.updateTransfer(
                        // Any leg id identifies the group; use the one the
                        // server handed back rather than the row tapped.
                        id = loaded.id,
                        fromWalletId = source.id,
                        toWalletId = target.id,
                        amountFromCents = centsFrom ?: 0,
                        amountToCents = centsTo ?: 0,
                        description = description,
                        occurredAt = date.toString(),
                        occurredTime = time,
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
        PickerField(
            label = stringResource(R.string.transactions_from_wallet),
            options = spendable,
            selected = fromWallet,
            optionLabel = { "${it.name} · ${it.currencyCode}" },
            onSelect = { fromWallet = it },
            modifier = Modifier.fillMaxWidth(),
        )
        PickerField(
            label = stringResource(R.string.transactions_to_wallet),
            options = spendable.filter { it.id != fromWallet?.id },
            selected = toWallet,
            optionLabel = { "${it.name} · ${it.currencyCode}" },
            onSelect = { toWallet = it },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = amountFrom,
            onValueChange = { amountFrom = it; error = null },
            label = { Text(stringResource(R.string.transactions_amount)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            suffix = { fromWallet?.let { Text(it.currencyCode, color = colors.fgSubtle) } },
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
        TimeField(
            label = stringResource(R.string.transactions_time),
            value = time,
            onChange = { time = it },
        )
    }
}
