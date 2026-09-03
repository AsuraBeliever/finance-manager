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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import com.asura.finanzas.ui.components.FieldHint
import com.asura.finanzas.ui.components.Lucide
import com.asura.finanzas.ui.components.WebCheckbox
import com.asura.finanzas.ui.components.FormField
import com.asura.finanzas.ui.components.MoneyField
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.math.BigDecimal
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
    // The calculator's own parameters. The catalogue prefills them and the form
    // asks for the ones it cannot know — the same fields, in the same order, as
    // the web's `InvestmentFormModal`.
    var currencyCode by remember { mutableStateOf(existing?.currencyCode ?: "MXN") }
    var currencies by remember { mutableStateOf(listOf("MXN")) }
    var rateText by remember { mutableStateOf("") }
    var plazo by remember { mutableStateOf(91) }
    var isrText by remember { mutableStateOf("0") }
    var reinvest by remember { mutableStateOf(false) }
    var compounding by remember { mutableStateOf("daily") }
    var symbol by remember { mutableStateOf("BTC") }
    var quantityText by remember { mutableStateOf("") }
    var titulosText by remember { mutableStateOf("") }
    var remanentesText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf(existing?.notes.orEmpty()) }
    var rateInfo by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val genericError = stringResource(R.string.common_error)
    val offlineError = stringResource(R.string.offline_banner)
    val noWallet = stringResource(R.string.investments_movement_wallet_none)
    val invalidRate = stringResource(R.string.investments_invalid_rate)
    val invalidAmount = stringResource(R.string.investments_invalid_amount)
    val invalidQuantity = stringResource(R.string.investments_invalid_quantity)

    LaunchedEffect(Unit) {
        catalog = runCatching { repository.investmentCatalog() }.getOrDefault(emptyList())
        wallets = runCatching { repository.wallets().value }.getOrDefault(emptyList())
        currencies = runCatching { repository.currencies().map { it.code } }
            .getOrDefault(emptyList())
            .ifEmpty { listOf("MXN") }
        choice = if (existing == null) {
            catalog.firstOrNull()
        } else {
            catalog.firstOrNull { it.calculator == existing.calculator }
        }
        wallet = wallets.firstOrNull { it.id == existing?.linkedWalletId }
        // Editing starts from what is stored, exactly as the web's effect does.
        existing?.let { inv ->
            val params = paramsOf(inv.paramsJson)
            rateText = params.bps("annual_rate_bps")?.let { fromBps(it) }.orEmpty()
            plazo = params.int("plazo_days") ?: 91
            isrText = params.bps("isr_rate_bps")?.let { fromBps(it) } ?: "0"
            reinvest = params.bool("reinvest") ?: false
            compounding = params.str("compounding") ?: "daily"
            symbol = params.str("symbol") ?: "BTC"
            quantityText = params.long("quantity_e8")?.let { fromE8(it) }.orEmpty()
            titulosText = params.int("titulos")?.toString().orEmpty()
            remanentesText = params.long("remanentes_cents")
                ?.let { formatMoney(it, withSymbol = false) }
                .orEmpty()
        }
    }

    // Which calculator the fields belong to: the picked catalogue entry, or the
    // one the edited investment already is.
    val calculator = existing?.calculator ?: choice?.calculator
    // bonddia reads the cached historical series and crypto the live price, so
    // neither asks for a rate; manual has none at all.
    val needsRate = calculator != null && calculator !in setOf("manual", "crypto", "bonddia")
    val banxicoKind = when (calculator) {
        "cetes" -> "cetes_$plazo"
        "fixed_rate" -> "objetivo"
        else -> null
    }
    val banxicoFetched = stringResource(R.string.investments_banxico_fetched)

    // Names resolved in composition; the select callback is not a composable
    // scope, so it cannot call stringResource itself.
    val names = catalog.associate { it.id to catalogName(it) }

    val cents = parseAmountToCents(principal)
    val canSave = !busy && (existing != null || choice != null) &&
        name.isNotBlank() && cents != null && cents > 0

    if (step == "catalog") {
        CatalogSheet(
            catalog = catalog,
            onPick = { item ->
                choice = item
                name = names[item.id].orEmpty()
                val params = paramsOf(item.paramsJson)
                rateText = params.bps("annual_rate_bps")?.let { fromBps(it) }.orEmpty()
                plazo = params.int("plazo_days") ?: 91
                isrText = params.bps("isr_rate_bps")?.let { fromBps(it) } ?: "0"
                reinvest = params.bool("reinvest") ?: false
                compounding = params.str("compounding") ?: "daily"
                symbol = params.str("symbol") ?: "BTC"
                quantityText = ""
                titulosText = ""
                remanentesText = ""
                rateInfo = item.rateDate?.let { date -> "$banxicoFetched $date" }
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
                    // The params the calculator will be revalued from, built
                    // exactly as the web builds them — same keys, same units.
                    val params = buildJsonObject {
                        if (needsRate) {
                            put("annual_rate_bps", parseBps(rateText) ?: error(invalidRate))
                        }
                        if (calculator == "cetes") {
                            put("plazo_days", plazo)
                            put("isr_rate_bps", parseBps(isrText) ?: error(invalidRate))
                            put("reinvest", reinvest)
                        }
                        if (calculator == "fixed_rate") {
                            put("compounding", compounding)
                        }
                        if (calculator == "bonddia") {
                            // Keep the live rate and the calibrated spread as
                            // the estimation fallback, like the web does.
                            val stored = existing?.let { paramsOf(it.paramsJson) }
                            val fallback = stored?.bps("annual_rate_bps") ?: parseBps(rateText)
                            fallback?.let { put("annual_rate_bps", it) }
                            stored?.bps("spread_bps")?.let { put("spread_bps", it) }
                            if (titulosText.isNotBlank()) {
                                val titulos = titulosText.filter { c -> c.isDigit() }.toIntOrNull()
                                if (titulos == null || titulos <= 0) error(invalidQuantity)
                                put("titulos", titulos)
                                put(
                                    "remanentes_cents",
                                    if (remanentesText.isBlank()) 0L
                                    else parseAmountToCents(remanentesText) ?: error(invalidAmount),
                                )
                            }
                        }
                        if (calculator == "crypto") {
                            put("symbol", symbol)
                            put("quantity_e8", parseQuantityE8(quantityText) ?: error(invalidQuantity))
                        }
                    }.toString()

                    if (existing != null) {
                        repository.updateInvestment(
                            id = existing.id,
                            name = name,
                            currencyCode = currencyCode,
                            principalCents = cents ?: 0,
                            startDate = startDate.toString(),
                            paramsJson = params,
                            linkedWalletId = wallet?.id,
                            notes = notes.ifBlank { null },
                        )
                    } else {
                        val item = choice ?: error("sin instrumento")
                        repository.createInvestment(
                            calculator = item.calculator,
                            name = name,
                            currencyCode = currencyCode,
                            principalCents = cents ?: 0,
                            startDate = startDate.toString(),
                            paramsJson = params,
                            linkedWalletId = wallet?.id,
                            notes = notes.ifBlank { null },
                        )
                    }
                    // A fresh coin has no price cached yet; pull one so the card
                    // does not open showing what was paid. Offline is fine.
                    if (calculator == "crypto") {
                        runCatching { repository.refreshMarketData() }
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
        // Editing keeps the type fixed and says so; creating offers the way
        // back to the catalogue. Same pair, same place, as on the web.
        if (existing == null) {
            Text(
                stringResource(R.string.investments_catalog_back),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                color = colors.accent,
                modifier = Modifier.clickable { step = "catalog" },
            )
        } else {
            PickerField(
                label = stringResource(R.string.investments_calculator),
                options = listOf(existing.calculator),
                selected = existing.calculator,
                optionLabel = { calculatorName(it) },
                onSelect = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        FormField(
            label = stringResource(R.string.investments_name),
            value = name,
            onValueChange = { name = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = stringResource(R.string.investments_name_placeholder),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MoneyField(
                label = stringResource(R.string.investments_principal),
                value = principal,
                onValueChange = { principal = it; error = null },
                modifier = Modifier.weight(1f),
            )
            DateField(
                label = stringResource(R.string.investments_start_date),
                value = startDate,
                onChange = { startDate = it },
                modifier = Modifier.weight(1f),
            )
        }
        if (calculator == "crypto") {
            FieldHint(stringResource(R.string.investments_crypto_principal_hint))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PickerField(
                label = stringResource(R.string.investments_currency),
                options = currencies,
                selected = currencyCode,
                optionLabel = { it },
                onSelect = { currencyCode = it },
                modifier = Modifier.weight(1f),
            )
            if (needsRate) {
                FormField(
                    label = stringResource(R.string.investments_annual_rate),
                    value = rateText,
                    onValueChange = { rateText = it; error = null },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = "15.0",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
        }

        // One tap fills the rate in from the source that publishes it.
        banxicoKind?.let { kind ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.clickable {
                        scope.launch {
                            runCatching { repository.banxicoRate(kind) }
                                .onSuccess { rate ->
                                    rateText = fromBps(rate.rateBps)
                                    rateInfo = "$banxicoFetched ${rate.date}"
                                    error = null
                                }
                                .onFailure {
                                    error = if (it is NetworkException) offlineError
                                    else it.message ?: genericError
                                }
                        }
                    },
                ) {
                    Icon(
                        Lucide.RotateCcw,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        if (calculator == "cetes") {
                            stringResource(R.string.investments_banxico_cetes) + " ($plazo)"
                        } else {
                            stringResource(R.string.investments_banxico_objetivo)
                        },
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                        color = colors.accent,
                    )
                }
                rateInfo?.let { FieldHint(it) }
            }
        }

        if (calculator == "cetes") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PickerField(
                    label = stringResource(R.string.investments_plazo),
                    options = listOf(28, 91, 182, 364),
                    selected = plazo,
                    optionLabel = { it.toString() },
                    onSelect = { plazo = it },
                    modifier = Modifier.weight(1f),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FormField(
                        label = stringResource(R.string.investments_isr_rate),
                        value = isrText,
                        onValueChange = { isrText = it; error = null },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    FieldHint(stringResource(R.string.investments_isr_hint))
                }
            }
        }

        if (calculator == "bonddia") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FormField(
                    label = stringResource(R.string.investments_bonddia_titulos),
                    value = titulosText,
                    onValueChange = { titulosText = it.filter { c -> c.isDigit() } },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = "2923",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                MoneyField(
                    label = stringResource(R.string.investments_bonddia_remanentes),
                    value = remanentesText,
                    onValueChange = { remanentesText = it },
                    modifier = Modifier.weight(1f),
                )
            }
            FieldHint(stringResource(R.string.investments_bonddia_hint))
        }

        if (calculator == "crypto") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PickerField(
                    label = stringResource(R.string.investments_crypto_symbol),
                    options = COINS,
                    selected = symbol,
                    optionLabel = { it },
                    onSelect = { symbol = it },
                    modifier = Modifier.weight(1f),
                )
                FormField(
                    label = stringResource(R.string.investments_crypto_quantity),
                    value = quantityText,
                    onValueChange = { quantityText = it; error = null },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = "0.05",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        }

        if (calculator == "cetes") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { reinvest = !reinvest },
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                WebCheckbox(
                    checked = reinvest,
                    onCheckedChange = { reinvest = it },
                    modifier = Modifier.padding(top = 4.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.investments_reinvest),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = colors.fg,
                    )
                    FieldHint(stringResource(R.string.investments_reinvest_hint))
                }
            }
        }

        if (calculator == "fixed_rate") {
            PickerField(
                label = stringResource(R.string.investments_compounding),
                options = listOf("daily", "monthly", "simple"),
                selected = compounding,
                optionLabel = { compoundingName(it) },
                onSelect = { compounding = it },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Android-only: the wallet the money comes out of. The web leaves the
        // link empty here, but dropping the picker would take away the only way
        // to tie an investment to a wallet from the phone.
        PickerField(
            label = stringResource(R.string.investments_movement_wallet),
            options = listOf<Wallet?>(null) + wallets.filter { !it.isArchived },
            selected = wallet,
            optionLabel = { it?.name ?: noWallet },
            onSelect = { wallet = it },
            emptyLabel = noWallet,
            modifier = Modifier.fillMaxWidth(),
        )

        FormField(
            label = stringResource(R.string.investments_notes),
            value = notes,
            onValueChange = { notes = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
    }
}

/** The ten coins the web's picker offers, in the same order. */
private val COINS =
    listOf("BTC", "ETH", "SOL", "XRP", "DOGE", "ADA", "USDT", "USDC", "BNB", "LTC")

/** A calculator's display name, for the disabled picker on the edit form. */
@Composable
private fun calculatorName(calculator: String): String = stringResource(
    when (calculator) {
        "nu_cajita" -> R.string.investments_calculators_nu_cajita
        "cetes" -> R.string.investments_calculators_cetes
        "bonddia" -> R.string.investments_calculators_bonddia
        "crypto" -> R.string.investments_calculators_crypto
        "fixed_rate" -> R.string.investments_calculators_fixed_rate
        else -> R.string.investments_calculators_manual
    },
)

@Composable
private fun compoundingName(id: String): String = stringResource(
    when (id) {
        "daily" -> R.string.investments_compounding_options_daily
        "monthly" -> R.string.investments_compounding_options_monthly
        else -> R.string.investments_compounding_options_simple
    },
)

/** Stored calculator params, read leniently: a missing key is just null. */
private fun paramsOf(json: String): JsonObject =
    runCatching { Json.parseToJsonElement(json).jsonObject }.getOrDefault(JsonObject(emptyMap()))

private fun JsonObject.bps(key: String): Long? =
    runCatching { this[key]?.jsonPrimitive?.long }.getOrNull()

private fun JsonObject.long(key: String): Long? =
    runCatching { this[key]?.jsonPrimitive?.long }.getOrNull()

private fun JsonObject.int(key: String): Int? =
    runCatching { this[key]?.jsonPrimitive?.int }.getOrNull()

private fun JsonObject.str(key: String): String? =
    runCatching { this[key]?.jsonPrimitive?.content }.getOrNull()

private fun JsonObject.bool(key: String): Boolean? =
    runCatching { this[key]?.jsonPrimitive?.boolean }.getOrNull()

/** 1250 bps -> "12.5", the way the web writes a rate into its box. */
private fun fromBps(bps: Long): String {
    val text = BigDecimal(bps).movePointLeft(2).stripTrailingZeros().toPlainString()
    return text
}

/** 5000000 -> "0.05": units ×1e8 back to what someone typed. */
private fun fromE8(units: Long): String =
    BigDecimal(units).movePointLeft(8).stripTrailingZeros().toPlainString()

/** Percent text ("12.5") -> basis points (1250), or null when it is not one. */
private fun parseBps(text: String): Long? {
    val value = text.replace(",", "").trim().toBigDecimalOrNull() ?: return null
    if (value.signum() < 0) return null
    return value.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toLong()
}

/** Quantity text ("0.05") -> integer units ×1e8, or null when invalid. */
private fun parseQuantityE8(text: String): Long? {
    val cleaned = text.replace(",", "").trim()
    if (!Regex("""^\d+(\.\d{1,8})?$""").matches(cleaned)) return null
    val units = BigDecimal(cleaned).movePointRight(8)
        .setScale(0, java.math.RoundingMode.HALF_UP).toLong()
    return units.takeIf { it > 0 }
}

private fun String.toBigDecimalOrNull(): BigDecimal? = runCatching { BigDecimal(this) }.getOrNull()

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
