package com.asura.finanzas.ui.transactions

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
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
import com.asura.finanzas.ui.components.FormField
import com.asura.finanzas.R
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.MovementDetail
import com.asura.finanzas.data.NetworkException
import com.asura.finanzas.data.Transaction
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
 * Fix a contribution or withdrawal already recorded against an investment, from
 * the movements list.
 *
 * The row here is only the *wallet* side of it: a transfer leg with no group,
 * because an investment is not a wallet and so has no sibling leg. Editing it as
 * an ordinary transfer would fail — the amount has to move on the investment
 * too, which is why the server has its own command for this pair.
 */
@Composable
fun InvestmentLegEditSheet(
    repository: BrokeRepository,
    transaction: Transaction,
    wallets: List<Wallet>,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val colors = Broke.colors

    var detail by remember { mutableStateOf<MovementDetail?>(null) }
    var kind by remember { mutableStateOf("deposit") }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var wallet by remember { mutableStateOf<Wallet?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val genericError = stringResource(R.string.common_error)
    val offlineError = stringResource(R.string.offline_banner)
    val noneLabel = stringResource(R.string.investments_movement_wallet_none)

    LaunchedEffect(transaction.id) {
        runCatching { repository.investmentMovementByTransaction(transaction.id) }
            .onSuccess { loaded ->
                detail = loaded
                kind = loaded.kind
                amount = formatMoney(loaded.amountCents, withSymbol = false)
                date = runCatching { LocalDate.parse(loaded.occurredAt.take(10)) }
                    .getOrDefault(LocalDate.now())
                wallet = wallets.firstOrNull { it.id == loaded.walletId }
            }
            .onFailure {
                error = if (it is NetworkException) offlineError else it.message ?: genericError
            }
    }

    val cents = parseAmountToCents(amount)
    val canSave = !busy && detail != null && cents != null && cents > 0

    FormSheet(
        title = stringResource(R.string.investments_movements),
        busy = busy,
        error = error,
        canSave = canSave,
        onDismiss = onDismiss,
        onSave = {
            val loaded = detail ?: return@FormSheet
            busy = true
            error = null
            scope.launch {
                runCatching {
                    repository.updateInvestmentMovement(
                        id = loaded.id,
                        kind = kind,
                        amountCents = cents ?: 0,
                        occurredAt = date.toString(),
                        walletId = wallet?.id,
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
        detail?.let { loaded ->
            Text(
                loaded.investmentName,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = colors.fgMuted,
            )
        }
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
            modifier = Modifier.fillMaxWidth(),
        )
        FormField(
            label = stringResource(R.string.investments_movement_amount),
            value = amount,
            onValueChange = { amount = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            suffix = detail?.currencyCode.orEmpty(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
