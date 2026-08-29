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
import com.asura.finanzas.data.CatalogItem
import com.asura.finanzas.data.InvestmentDetail
import com.asura.finanzas.data.NetworkException
import com.asura.finanzas.data.Wallet
import com.asura.finanzas.ui.components.DateField
import com.asura.finanzas.ui.components.FormSheet
import com.asura.finanzas.ui.components.PickerField
import com.asura.finanzas.ui.formatMoney
import com.asura.finanzas.ui.parseAmountToCents
import com.asura.finanzas.ui.theme.Broke
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * New investment, from the catalog the server publishes. Picking an entry
 * carries its `paramsJson` through untouched — those are the calculator's own
 * parameters (rate, term, ISR…), authored by finanzas-core, and the app has no
 * business editing them. That also means the live Banxico rate the server
 * attached comes along for free.
 */
@Composable
fun NewInvestmentSheet(
    repository: BrokeRepository,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    /**
     * Null creates from the catalog; non-null edits in place. Editing keeps the
     * calculator fixed — swapping it would revalue the whole history under
     * different rules, which the web does not allow either.
     */
    existing: InvestmentDetail? = null,
) {
    val scope = rememberCoroutineScope()
    val colors = Broke.colors

    var catalog by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var wallets by remember { mutableStateOf<List<Wallet>>(emptyList()) }
    var choice by remember { mutableStateOf<CatalogItem?>(null) }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var principal by remember {
        mutableStateOf(
            existing?.principalCents?.let { formatMoney(it, withSymbol = false) }.orEmpty(),
        )
    }
    var startDate by remember {
        mutableStateOf(
            existing?.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: LocalDate.now(),
        )
    }
    var wallet by remember { mutableStateOf<Wallet?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val genericError = stringResource(R.string.common_error)
    val offlineError = stringResource(R.string.offline_banner)
    val noWallet = stringResource(R.string.investments_movement_wallet_none)

    LaunchedEffect(Unit) {
        catalog = runCatching { repository.investmentCatalog() }.getOrDefault(emptyList())
        wallets = runCatching { repository.wallets().value }.getOrDefault(emptyList())
        choice = if (existing == null) {
            catalog.firstOrNull()
        } else {
            catalog.firstOrNull { it.calculator == existing.calculator }
        }
        wallet = wallets.firstOrNull { it.id == existing?.linkedWalletId }
    }

    // Names resolved in composition; the select callback is not a composable
    // scope, so it cannot call stringResource itself.
    val names = catalog.associate { it.id to catalogName(it) }

    val cents = parseAmountToCents(principal)
    val canSave = !busy && (existing != null || choice != null) &&
        name.isNotBlank() && cents != null && cents > 0

    FormSheet(
        title = stringResource(
            if (existing == null) R.string.investments_new_investment
            else R.string.investments_edit_investment,
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
                    if (existing != null) {
                        repository.updateInvestment(
                            id = existing.id,
                            name = name,
                            currencyCode = wallet?.currencyCode ?: existing.currencyCode,
                            principalCents = cents ?: 0,
                            startDate = startDate.toString(),
                            // Keep the calculator's own configuration as stored:
                            // its rate and term are not editable from this form.
                            paramsJson = existing.paramsJson,
                            linkedWalletId = wallet?.id,
                            notes = existing.notes,
                        )
                    } else {
                        val item = choice ?: error("sin instrumento")
                        repository.createInvestment(
                            calculator = item.calculator,
                            name = name,
                            currencyCode = wallet?.currencyCode ?: "MXN",
                            principalCents = cents ?: 0,
                            startDate = startDate.toString(),
                            paramsJson = item.paramsJson,
                            linkedWalletId = wallet?.id,
                            notes = null,
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
        // The instrument is only chosen at creation time.
        if (existing == null) {
            PickerField(
                label = stringResource(R.string.investments_catalog_title),
                options = catalog,
                selected = choice,
                optionLabel = { catalogLabel(it) },
                onSelect = {
                    choice = it
                    if (name.isBlank()) name = names[it.id].orEmpty()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        choice?.rateBps?.let { bps ->
            Text(
                // The rate the server fetched; shown so the choice is informed,
                // never edited here.
                "%.2f%%".format(bps / 100.0) +
                    (choice?.rateDate?.let { " · $it" } ?: ""),
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = colors.fgSubtle,
            )
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it; error = null },
            label = { Text(stringResource(R.string.investments_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = principal,
            onValueChange = { principal = it; error = null },
            label = { Text(stringResource(R.string.investments_principal)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        DateField(
            label = stringResource(R.string.investments_start_date),
            value = startDate,
            onChange = { startDate = it },
        )
        PickerField(
            label = stringResource(R.string.investments_movement_wallet),
            options = listOf<Wallet?>(null) + wallets.filter { !it.isArchived },
            selected = wallet,
            optionLabel = { it?.name ?: noWallet },
            onSelect = { wallet = it },
            emptyLabel = noWallet,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Catalog entries are identified by a stable id the dictionary names. */
@Composable
fun catalogName(item: CatalogItem): String = stringResource(
    when (item.id) {
        "cetes_28" -> R.string.investments_catalog_cetes_28_name
        "cetes_91" -> R.string.investments_catalog_cetes_91_name
        "cetes_182" -> R.string.investments_catalog_cetes_182_name
        "cetes_364" -> R.string.investments_catalog_cetes_364_name
        "bonddia" -> R.string.investments_catalog_bonddia_name
        "nu_cajita" -> R.string.investments_catalog_nu_cajita_name
        "crypto" -> R.string.investments_catalog_crypto_name
        "fixed_rate" -> R.string.investments_catalog_fixed_rate_name
        else -> R.string.investments_catalog_manual_name
    },
)

@Composable
private fun catalogLabel(item: CatalogItem): String {
    val name = catalogName(item)
    val rate = item.rateBps?.let { " · %.2f%%".format(it / 100.0) }.orEmpty()
    return name + rate
}
