package com.asura.finanzas.ui.investments

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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asura.finanzas.ui.components.FieldLabel
import com.asura.finanzas.ui.components.PlainSheet
import com.asura.finanzas.ui.components.formatBps
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
    // The web makes picking the instrument a step of its own before the fields.
    var step by remember { mutableStateOf(if (existing == null) "catalog" else "form") }
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

    if (step == "catalog") {
        CatalogSheet(
            catalog = catalog,
            onPick = {
                choice = it
                if (name.isBlank()) name = names[it.id].orEmpty()
                step = "form"
            },
            onDismiss = onDismiss,
        )
        return
    }

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
        // Which instrument this is, and a way back to the catalogue — the web
        // shows the same pair once you are past the picker.
        if (existing == null) {
            Column {
                FieldLabel(stringResource(R.string.investments_catalog_title))
                Text(
                    choice?.let { catalogLabel(it) }.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = Broke.colors.fg,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.investments_catalog_back),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                    color = Broke.colors.accent,
                    modifier = Modifier.clickable { step = "catalog" },
                )
            }
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

        FormField(
            label = stringResource(R.string.investments_name),
            value = name,
            onValueChange = { name = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        FormField(
            label = stringResource(R.string.investments_principal),
            value = principal,
            onValueChange = { principal = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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

/** The one-line explanation under each catalogue entry, as on the web. */
@Composable
private fun catalogDescription(item: CatalogItem): String = stringResource(
    when (item.id) {
        "cetes_28" -> R.string.investments_catalog_cetes_28_description
        "cetes_91" -> R.string.investments_catalog_cetes_91_description
        "cetes_182" -> R.string.investments_catalog_cetes_182_description
        "cetes_364" -> R.string.investments_catalog_cetes_364_description
        "bonddia" -> R.string.investments_catalog_bonddia_description
        "nu_cajita" -> R.string.investments_catalog_nu_cajita_description
        "crypto" -> R.string.investments_catalog_crypto_description
        "fixed_rate" -> R.string.investments_catalog_fixed_rate_description
        else -> R.string.investments_catalog_manual_description
    },
)

@Composable
private fun catalogLabel(item: CatalogItem): String {
    val name = catalogName(item)
    val rate = item.rateBps?.let { " · " + formatBps(it) }.orEmpty()
    return name + rate
}


/**
 * The catalogue step: one card per instrument with its description and the live
 * rate the server attached, exactly as the web lists them.
 */
@Composable
private fun CatalogSheet(
    catalog: List<CatalogItem>,
    onPick: (CatalogItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Broke.colors
    PlainSheet(
        title = stringResource(R.string.investments_catalog_title),
        onDismiss = onDismiss,
    ) {
        if (catalog.isEmpty()) {
            Text(
                stringResource(R.string.investments_catalog_loading),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = colors.fgSubtle,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                textAlign = TextAlign.Center,
            )
        }
        catalog.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.borderMuted, RoundedCornerShape(12.dp))
                    .clickable { onPick(item) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        catalogName(item),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = colors.fg,
                    )
                    Text(
                        catalogDescription(item),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                        color = colors.fgSubtle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(12.dp))
                val bps = item.rateBps
                if (bps != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            formatBps(bps),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            color = colors.accent,
                        )
                        item.rateDate?.let {
                            Text(
                                stringResource(R.string.investments_catalog_rate_as_of) + " " + it,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                                color = colors.fgSubtle,
                            )
                        }
                    }
                } else if (item.id == "nu_cajita" || item.id == "fixed_rate") {
                    Text(
                        stringResource(R.string.investments_catalog_no_rate),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                        color = colors.fgSubtle,
                    )
                }
            }
        }
    }
}
