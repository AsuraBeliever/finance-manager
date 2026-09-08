package com.asura.finanzas.ui.subscriptions

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
import com.asura.finanzas.ui.components.MoneyField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.unit.dp
import com.asura.finanzas.ui.components.ColorPicker
import com.asura.finanzas.ui.components.FieldLabel
import com.asura.finanzas.R
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.NetworkException
import com.asura.finanzas.data.Subscription
import com.asura.finanzas.data.TransactionCategory
import com.asura.finanzas.data.Wallet
import com.asura.finanzas.ui.components.DateField
import com.asura.finanzas.ui.components.FormSheet
import com.asura.finanzas.ui.components.PickerField
import com.asura.finanzas.ui.seedName
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
    // A subscription can be charged in a currency of its own — the wallet is
    // only where the payment gets logged, and it may not even have one.
    var currencyCode by remember { mutableStateOf(existing?.currencyCode ?: "MXN") }
    var currencies by remember { mutableStateOf(listOf("MXN")) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // New subscriptions open on the first chart colour, like the web form
    // (picking a brand logo overwrites it with the brand's own).
    var color by remember { mutableStateOf(existing?.color ?: "#a855f7") }

    val genericError = stringResource(R.string.common_error)
    val offlineError = stringResource(R.string.offline_banner)
    // The web labels both empty options "None" here, not "Uncategorized".
    val noneLabel = stringResource(R.string.subscriptions_none)

    LaunchedEffect(Unit) {
        wallets = runCatching { repository.wallets().value }.getOrDefault(emptyList())
        categories = runCatching { repository.transactionCategories("expense") }
            .getOrDefault(emptyList())
        currencies = runCatching { repository.currencies().map { it.code } }
            .getOrDefault(emptyList())
            .ifEmpty { listOf("MXN") }
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
                        currencyCode = currencyCode,
                        cadence = cadence,
                        nextChargeDate = nextCharge.toString(),
                        walletId = wallet?.id,
                        categoryId = category?.id,
                        color = color,
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
        FormField(
            label = stringResource(R.string.subscriptions_name),
            value = name,
            onValueChange = { name = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            placeholder = stringResource(R.string.subscriptions_name_placeholder),
            singleLine = true,
        )

        // Web order: amount + currency, then frequency + next charge, both in
        // two-column rows.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MoneyField(
                label = stringResource(R.string.subscriptions_amount),
                value = amount,
                onValueChange = { amount = it; error = null },
                modifier = Modifier.weight(1f),
            )
            PickerField(
                label = stringResource(R.string.investments_currency),
                options = currencies,
                selected = currencyCode,
                optionLabel = { it },
                onSelect = { currencyCode = it },
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PickerField(
                label = stringResource(R.string.subscriptions_cadence),
                options = listOf("monthly", "yearly"),
                selected = cadence,
                optionLabel = {
                    stringResource(
                        if (it == "yearly") R.string.subscriptions_yearly
                        else R.string.subscriptions_monthly,
                    )
                },
                onSelect = { cadence = it },
                modifier = Modifier.weight(1f),
            )
            DateField(
                label = stringResource(R.string.subscriptions_next_charge),
                value = nextCharge,
                onChange = { nextCharge = it },
                modifier = Modifier.weight(1f),
            )
        }

        PickerField(
            label = stringResource(R.string.subscriptions_wallet),
            options = listOf<Wallet?>(null) + wallets.filter { !it.isArchived },
            selected = wallet,
            optionLabel = { it?.name ?: noneLabel },
            onSelect = { wallet = it },
            emptyLabel = noneLabel,
            modifier = Modifier.fillMaxWidth(),
        )
        PickerField(
            label = stringResource(R.string.subscriptions_category),
            options = listOf<TransactionCategory?>(null) + categories,
            selected = category,
            optionLabel = { c -> c?.let { seedName(it.name, it.isSystem) } ?: noneLabel },
            onSelect = { category = it },
            emptyLabel = noneLabel,
            modifier = Modifier.fillMaxWidth(),
        )

        Column {
            FieldLabel(stringResource(R.string.categories_color))
            ColorPicker(value = color, onChange = { color = it })
        }
    }
}
