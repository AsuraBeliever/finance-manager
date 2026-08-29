package com.asura.finanzas.ui.investments

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
import com.asura.finanzas.data.InvestmentDetail
import com.asura.finanzas.data.InvestmentMovement
import com.asura.finanzas.data.NetworkException
import com.asura.finanzas.data.Wallet
import com.asura.finanzas.ui.components.DateField
import com.asura.finanzas.ui.components.FormSheet
import com.asura.finanzas.ui.components.PickerField
import com.asura.finanzas.ui.components.SegmentedControl
import com.asura.finanzas.ui.formatMoney
import com.asura.finanzas.ui.parseAmountToCents
import com.asura.finanzas.ui.theme.Broke
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Deposit into or withdraw from an investment. Picking a wallet makes the
 * server post the matching transfer leg; leaving it empty records an external
 * movement, exactly like the web form.
 */
@Composable
fun InvestmentMovementSheet(
    repository: BrokeRepository,
    investment: InvestmentDetail,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    /** Null adds a movement; non-null edits that one in place. */
    existing: InvestmentMovement? = null,
) {
    val scope = rememberCoroutineScope()
    var wallets by remember { mutableStateOf<List<Wallet>>(emptyList()) }
    var kind by remember { mutableStateOf(existing?.kind ?: "deposit") }
    var amount by remember {
        mutableStateOf(
            existing?.amountCents?.let { formatMoney(it, withSymbol = false) }.orEmpty(),
        )
    }
    var date by remember {
        mutableStateOf(
            existing?.occurredAt?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: LocalDate.now(),
        )
    }
    var wallet by remember { mutableStateOf<Wallet?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val genericError = stringResource(R.string.common_error)
    val offlineError = stringResource(R.string.offline_banner)
    val noneLabel = stringResource(R.string.investments_movement_wallet_none)

    LaunchedEffect(Unit) {
        wallets = runCatching { repository.wallets().value }.getOrDefault(emptyList())
        // The list shape carries no walletId, so an edit reads the wallet from
        // get_investment_movement; a new movement defaults to the linked one.
        val linked = if (existing == null) {
            investment.linkedWalletId
        } else {
            runCatching { repository.investmentMovement(existing.id).walletId }.getOrNull()
        }
        wallet = wallets.firstOrNull { it.id == linked }
    }

    val cents = parseAmountToCents(amount)
    val canSave = !busy && cents != null && cents > 0

    FormSheet(
        title = stringResource(R.string.investments_movements),
        busy = busy,
        error = error,
        canSave = canSave,
        onDismiss = onDismiss,
        onSave = {
            busy = true
            error = null
            scope.launch {
                runCatching {
                    if (existing != null) {
                        repository.updateInvestmentMovement(
                            id = existing.id,
                            kind = kind,
                            amountCents = cents ?: 0,
                            occurredAt = date.toString(),
                            walletId = wallet?.id,
                        )
                    } else {
                        repository.addInvestmentMovement(
                            investmentId = investment.id,
                            kind = kind,
                            amountCents = cents ?: 0,
                            occurredAt = date.toString(),
                            walletId = wallet?.id,
                        )
                    }
                }
                    .onSuccess { onSaved() }
                    .onFailure {
                        error = if (it is NetworkException) offlineError else it.message ?: genericError
                        busy = false
                    }
            }
        },
    ) {
        SegmentedControl(
            options = listOf("deposit", "withdrawal"),
            selected = kind,
            label = {
                stringResource(
                    if (it == "deposit") R.string.investments_deposit
                    else R.string.investments_withdrawal,
                )
            },
            onSelect = { kind = it },
        )
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it; error = null },
            label = { Text(stringResource(R.string.investments_movement_amount)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            suffix = { Text(investment.currencyCode, color = Broke.colors.fgSubtle) },
            modifier = Modifier.fillMaxWidth(),
        )
        DateField(
            label = stringResource(R.string.investments_movement_date),
            value = date,
            onChange = { date = it },
        )
        PickerField(
            label = stringResource(R.string.investments_movement_wallet),
            options = listOf<Wallet?>(null) + wallets.filter { !it.isArchived },
            selected = wallet,
            optionLabel = { it?.name ?: noneLabel },
            onSelect = { wallet = it },
            emptyLabel = noneLabel,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Record what the investment is actually worth on a given day. */
@Composable
fun SnapshotSheet(
    repository: BrokeRepository,
    investment: InvestmentDetail,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var value by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val genericError = stringResource(R.string.common_error)
    val offlineError = stringResource(R.string.offline_banner)

    val cents = parseAmountToCents(value)
    val canSave = !busy && cents != null && cents > 0

    FormSheet(
        title = stringResource(R.string.investments_add_snapshot),
        busy = busy,
        error = error,
        canSave = canSave,
        onDismiss = onDismiss,
        onSave = {
            busy = true
            error = null
            scope.launch {
                runCatching {
                    repository.addInvestmentSnapshot(investment.id, cents ?: 0, date.toString())
                }
                    .onSuccess { onSaved() }
                    .onFailure {
                        error = if (it is NetworkException) offlineError else it.message ?: genericError
                        busy = false
                    }
            }
        },
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it; error = null },
            label = { Text(stringResource(R.string.investments_snapshot_value)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            suffix = { Text(investment.currencyCode, color = Broke.colors.fgSubtle) },
            modifier = Modifier.fillMaxWidth(),
        )
        DateField(
            label = stringResource(R.string.investments_snapshot_date),
            value = date,
            onChange = { date = it },
        )
    }
}
