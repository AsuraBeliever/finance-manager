package com.asura.finanzas.ui.subscriptions

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
import com.asura.finanzas.data.Subscription
import com.asura.finanzas.data.TransactionCategory
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

@Composable
fun SubscriptionFormSheet(
    repository: BrokeRepository,
    existing: Subscription?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var wallets by remember { mutableStateOf<List<Wallet>>(emptyList()) }
    var categories by remember { mutableStateOf<List<TransactionCategory>>(emptyList()) }

    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var amount by remember {
        mutableStateOf(existing?.amountCents?.let { formatMoney(it, withSymbol = false) }.orEmpty())
    }
    var cadence by remember { mutableStateOf(existing?.cadence ?: "monthly") }
    var nextCharge by remember {
        mutableStateOf(
            existing?.nextChargeDate?.takeIf { it.isNotBlank() }
                ?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
                ?: LocalDate.now(),
        )
    }
    var wallet by remember { mutableStateOf<Wallet?>(null) }
    var category by remember { mutableStateOf<TransactionCategory?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val genericError = stringResource(R.string.common_error)
    val offlineError = stringResource(R.string.offline_banner)
    val noneLabel = stringResource(R.string.transactions_no_category)

    LaunchedEffect(Unit) {
        wallets = runCatching { repository.wallets().value }.getOrDefault(emptyList())
        categories = runCatching { repository.transactionCategories("expense") }
            .getOrDefault(emptyList())
        wallet = wallets.firstOrNull { it.id == existing?.walletId }
        category = categories.firstOrNull { it.id == existing?.categoryId }
    }

    val cents = parseAmountToCents(amount)
    val canSave = !busy && name.isNotBlank() && cents != null && cents > 0

    FormSheet(
        title = stringResource(
            if (existing == null) R.string.subscriptions_new_subscription
            else R.string.subscriptions_edit_subscription,
        ),
        busy = busy,
        error = error,
        canSave = canSave,
        onDismiss = onDismiss,
        onSave = {
            busy = true
            error = null
            scope.launch {
                runCatching {
                    repository.saveSubscription(
                        id = existing?.id,
                        name = name,
                        amountCents = cents ?: 0,
                        currencyCode = wallet?.currencyCode ?: existing?.currencyCode ?: "MXN",
                        cadence = cadence,
                        nextChargeDate = nextCharge.toString(),
                        walletId = wallet?.id,
                        categoryId = category?.id,
                        color = existing?.color,
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
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; error = null },
            label = { Text(stringResource(R.string.subscriptions_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it; error = null },
            label = { Text(stringResource(R.string.subscriptions_amount)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            suffix = { wallet?.let { Text(it.currencyCode, color = Broke.colors.fgSubtle) } },
            modifier = Modifier.fillMaxWidth(),
        )
        SegmentedControl(
            options = listOf("monthly", "yearly"),
            selected = cadence,
            label = {
                stringResource(
                    if (it == "yearly") R.string.subscriptions_yearly
                    else R.string.subscriptions_monthly,
                )
            },
            onSelect = { cadence = it },
        )
        DateField(
            label = stringResource(R.string.subscriptions_next_charge),
            value = nextCharge,
            onChange = { nextCharge = it },
        )
        PickerField(
            label = stringResource(R.string.subscriptions_wallet),
            options = wallets.filter { !it.isArchived },
            selected = wallet,
            optionLabel = { it.name },
            onSelect = { wallet = it },
            modifier = Modifier.fillMaxWidth(),
        )
        PickerField(
            label = stringResource(R.string.subscriptions_category),
            options = listOf<TransactionCategory?>(null) + categories,
            selected = category,
            optionLabel = { it?.name ?: noneLabel },
            onSelect = { category = it },
            emptyLabel = noneLabel,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
