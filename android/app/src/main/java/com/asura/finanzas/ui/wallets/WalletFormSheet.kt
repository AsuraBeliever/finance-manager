package com.asura.finanzas.ui.wallets

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.asura.finanzas.ui.components.FormSheet
import com.asura.finanzas.ui.components.FieldHint
import com.asura.finanzas.ui.components.FieldLabel
import com.asura.finanzas.ui.components.FormField
import com.asura.finanzas.ui.components.MoneyField
import com.asura.finanzas.ui.LocalAppSettings
import java.time.format.TextStyle
import java.util.Locale
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import com.asura.finanzas.R
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.Currency
import com.asura.finanzas.data.NetworkException
import com.asura.finanzas.data.Wallet
import com.asura.finanzas.data.WalletCategory
import com.asura.finanzas.ui.components.PickerField
import com.asura.finanzas.ui.seedName
import com.asura.finanzas.ui.components.PrimaryButton
import com.asura.finanzas.ui.components.WebCheckbox
import com.asura.finanzas.ui.formatMoney
import com.asura.finanzas.ui.parseAmountToCents
import com.asura.finanzas.ui.theme.Broke
import kotlinx.coroutines.launch

/**
 * Canonical seed name of the credit-card category. Picking it is what reveals
 * the card fields — there is no separate toggle, exactly as on the web. Seed
 * rows are stored in Spanish whatever the app's language, and a user-made
 * category never matches.
 */
private const val CREDIT_CATEGORY = "Tarjeta de crédito"

/** Cents as the dot-decimal string `MoneyField` parses, locale be damned. */
private fun centsToField(cents: Long): String =
    java.math.BigDecimal(cents).movePointLeft(2).setScale(2).toPlainString()

/** Payout cadences the server accepts, in the web select's order. */
private val YIELD_FREQUENCIES = listOf("daily", "weekly", "biweekly", "monthly")

private fun yieldFrequencyLabel(frequency: String): Int = when (frequency) {
    "daily" -> R.string.wallets_yield_daily
    "biweekly" -> R.string.wallets_yield_biweekly
    "monthly" -> R.string.wallets_yield_monthly
    else -> R.string.wallets_yield_weekly
}

/**
 * Create or edit a wallet. Rates go over the wire as basis points and the
 * opening balance as integer cents; the resulting balance is the server's to
 * work out, as always.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletFormSheet(
    repository: BrokeRepository,
    existing: Wallet?,
    wallets: List<Wallet>,
    /** Pre-selected parent, for "add pocket" from a wallet's detail. */
    parentDefault: Wallet? = null,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val colors = Broke.colors

    var categories by remember { mutableStateOf<List<WalletCategory>>(emptyList()) }
    var currencies by remember { mutableStateOf<List<Currency>>(emptyList()) }

    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var category by remember { mutableStateOf<WalletCategory?>(null) }
    var currency by remember { mutableStateOf<Currency?>(null) }
    var initial by remember {
        mutableStateOf(
            existing?.initialBalanceCents?.let { cents ->
                // Editing shows the stored opening balance; formatting only.
                // A credit card keeps its debt as a negative balance, but the
                // field asks "what do you owe", so it is shown positive — the
                // same flip the web form does.
                val shown = if (existing.categoryName == CREDIT_CATEGORY) maxOf(0L, -cents) else cents
                // MoneyField reads its value back with toBigDecimal(), so the
                // string has to be dot-decimal regardless of device locale.
                centsToField(shown)
            }.orEmpty(),
        )
    }
    var notes by remember { mutableStateOf(existing?.notes.orEmpty()) }
    // Chosen card design; null means "let the category decide", as on the web.
    var skinId by remember { mutableStateOf(existing?.skin) }
    var earnsYield by remember { mutableStateOf(existing?.yieldRateBps != null) }
    var yieldRate by remember {
        mutableStateOf(existing?.yieldRateBps?.let { "%.2f".format(it / 100.0) }.orEmpty())
    }
    // Same default as the web form and as the server's own fallback.
    var yieldFrequency by remember { mutableStateOf(existing?.yieldFrequency ?: "weekly") }
    var cutDay by remember { mutableStateOf(existing?.creditCutDay?.toString().orEmpty()) }
    var dueDays by remember { mutableStateOf(existing?.creditDueDays?.toString().orEmpty()) }
    var creditLimit by remember {
        mutableStateOf(existing?.creditLimitCents?.let { centsToField(it) }.orEmpty())
    }
    // Anniversary is stored as "MM-DD"; the form splits it into a month picker
    // and a day field, and an empty month means "not tracked".
    var annivMonth by remember {
        mutableStateOf(existing?.creditAnniversary?.substringBefore('-')?.toIntOrNull())
    }
    var annivDay by remember {
        mutableStateOf(
            existing?.creditAnniversary?.substringAfter('-')?.toIntOrNull()?.toString().orEmpty(),
        )
    }
    var parent by remember { mutableStateOf(parentDefault) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Resolved here because the save coroutine is not a composable scope.
    val genericError = stringResource(R.string.common_error)
    val offlineError = stringResource(R.string.offline_banner)
    val parentNone = stringResource(R.string.wallets_parent_none)
    val invalidAmount = stringResource(R.string.wallets_invalid_amount)
    val cutDayNeeded = stringResource(R.string.credit_cut_day_needed)
    val invalidCutDay = stringResource(R.string.credit_invalid_cut_day)
    val invalidDueDays = stringResource(R.string.credit_invalid_due_days)
    val anniversaryNone = stringResource(R.string.credit_anniversary_none)

    LaunchedEffect(Unit) {
        categories = runCatching { repository.walletCategories() }.getOrDefault(emptyList())
        currencies = runCatching { repository.currencies() }.getOrDefault(emptyList())
        category = categories.firstOrNull { it.id == existing?.categoryId } ?: categories.firstOrNull()
        currency = currencies.firstOrNull { it.code == (existing?.currencyCode ?: "MXN") }
            ?: currencies.firstOrNull()
        parent = wallets.firstOrNull { it.id == existing?.parentWalletId }
    }

    val isCredit = category?.name == CREDIT_CATEGORY
    val canSave = !busy && name.isNotBlank() && category != null && currency != null

    fun save() {
        val chosenCategory = category ?: return
        val chosenCurrency = currency ?: return

        val cents = parseAmountToCents(initial) ?: 0
        // Percent in the field, basis points on the wire.
        val rateBps = if (earnsYield) {
            val bps = yieldRate.replace(',', '.').toDoubleOrNull()?.let { Math.round(it * 100) }
            if (bps == null || bps <= 0) {
                error = invalidAmount
                return
            }
            bps
        } else {
            null
        }

        // Credit fields only apply under the credit-card category, and the cut
        // day is what switches the card panel on. Typing any other card field
        // without it is a mistake worth naming instead of dropping the lot.
        var cut: Long? = null
        var due: Long? = null
        var limit: Long? = null
        var anniversary: String? = null
        if (isCredit) {
            if (cutDay.isBlank()) {
                if (dueDays.isNotBlank() || creditLimit.isNotBlank() || annivMonth != null) {
                    error = cutDayNeeded
                    return
                }
            } else {
                val day = cutDay.trim().toLongOrNull()
                if (day == null || day < 1 || day > 31) {
                    error = invalidCutDay
                    return
                }
                cut = day
                if (dueDays.isNotBlank()) {
                    val d = dueDays.trim().toLongOrNull()
                    if (d == null || d < 1 || d > 60) {
                        error = invalidDueDays
                        return
                    }
                    due = d
                }
                if (creditLimit.isNotBlank()) {
                    val l = parseAmountToCents(creditLimit)
                    if (l == null || l <= 0) {
                        error = invalidAmount
                        return
                    }
                    limit = l
                }
                val day2 = annivDay.trim().toIntOrNull()
                val month = annivMonth
                if (month != null && day2 != null && day2 in 1..31) {
                    anniversary = "%02d-%02d".format(java.util.Locale.US, month, day2)
                }
            }
        }

        busy = true
        error = null
        scope.launch {
            runCatching {
                repository.saveWallet(
                    id = existing?.id,
                    name = name,
                    categoryId = chosenCategory.id,
                    currencyCode = chosenCurrency.code,
                    // On a card the field reads "what I owe"; it is stored as a
                    // negative opening balance.
                    initialBalanceCents = if (isCredit) -cents else cents,
                    color = existing?.color,
                    skin = skinId,
                    notes = notes,
                    yieldRateBps = rateBps,
                    yieldFrequency = if (rateBps != null) yieldFrequency else null,
                    parentWalletId = parent?.id,
                    creditCutDay = cut,
                    creditDueDays = due,
                    creditLimitCents = limit,
                    creditAnniversary = anniversary,
                )
            }
                .onSuccess { onSaved() }
                .onFailure {
                    error = when (it) {
                        is NetworkException -> offlineError
                        else -> it.message ?: genericError
                    }
                    busy = false
                }
        }
    }

    FormSheet(
        title = stringResource(
            if (existing == null) R.string.wallets_new_wallet else R.string.wallets_edit_wallet,
        ),
        busy = busy,
        error = error,
        canSave = canSave,
        onSave = { save() },
        onDismiss = onDismiss,
    ) {
        run {
            FormField(
                label = stringResource(R.string.wallets_name),
                value = name,
                onValueChange = { name = it; error = null },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = stringResource(R.string.wallets_name_placeholder),
            )

            // Web order: name, what it nests under (with its hint), then the
            // category and currency side by side.
            PickerField(
                label = stringResource(R.string.wallets_parent_wallet),
                options = listOf<Wallet?>(null) + wallets.filter {
                    it.parentWalletId == null && it.id != existing?.id
                },
                selected = parent,
                optionLabel = { it?.name ?: parentNone },
                onSelect = { parent = it },
                // Nothing picked is not nothing: it is "a wallet of its own".
                emptyLabel = parentNone,
                modifier = Modifier.fillMaxWidth(),
            )
            FieldHint(
                stringResource(
                    if (parent == null) R.string.wallets_parent_none_hint
                    else R.string.wallets_parent_hint,
                ),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PickerField(
                    label = stringResource(R.string.wallets_category),
                    options = categories,
                    selected = category,
                    // Seed rows are stored in Spanish; the picker shows them in
                    // the app's language, as every other list does.
                    optionLabel = { seedName(it.name, it.isSystem).orEmpty() },
                    onSelect = { category = it },
                    modifier = Modifier.weight(1f),
                )
                PickerField(
                    label = stringResource(R.string.wallets_currency),
                    options = currencies,
                    selected = currency,
                    optionLabel = { "${it.code} — " + seedName(it.name).orEmpty() },
                    onSelect = { currency = it },
                    // Currency is fixed once there are movements.
                    enabled = existing == null,
                    modifier = Modifier.weight(1f),
                )
            }

            // On a card this box is "what do you owe", not an opening balance,
            // and it says so — the sign flip happens at save time.
            MoneyField(
                label = stringResource(
                    if (isCredit) R.string.credit_initial_debt
                    else R.string.wallets_initial_balance,
                ),
                value = initial,
                onValueChange = { initial = it },
                modifier = Modifier.fillMaxWidth(),
            )
            if (isCredit) {
                FieldHint(stringResource(R.string.credit_initial_debt_hint))
            }

            // Card design: the same catalogue the web offers, in its groups.
            // Without this the APK simply could not choose one.
            SkinPicker(
                selected = skinId,
                categoryName = category?.name,
                onSelect = { skinId = it },
            )

            FormField(
                label = stringResource(R.string.wallets_notes),
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier.fillMaxWidth(),
            )

            // The web frames this as a bordered card with a checkbox and a
            // paragraph explaining it, not a bare switch row.
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, colors.borderMuted, RoundedCornerShape(8.dp))
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WebCheckbox(
                        checked = earnsYield,
                        onCheckedChange = { earnsYield = it },
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.wallets_yield_enable),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = colors.fg,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.wallets_yield_hint),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                    color = colors.fgSubtle,
                )
                if (earnsYield) {
                    Spacer(Modifier.height(12.dp))
                    // Rate and cadence side by side, as in the web's 2-column grid.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        FormField(
                            label = stringResource(R.string.wallets_yield_rate),
                            value = yieldRate,
                            onValueChange = { yieldRate = it; error = null },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            suffix = "%",
                            placeholder = "3.0",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        PickerField(
                            label = stringResource(R.string.wallets_yield_frequency),
                            // Daily first: it's what the Nu cajitas do, and the
                            // cadence people reach for most.
                            options = YIELD_FREQUENCIES,
                            selected = yieldFrequency,
                            optionLabel = { stringResource(yieldFrequencyLabel(it)) },
                            onSelect = { yieldFrequency = it },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // Only when turning it on: an already-running wallet keeps
                    // its anchor, so the line would be a lie there.
                    if (existing?.yieldRateBps == null) {
                        Spacer(Modifier.height(8.dp))
                        FieldHint(stringResource(R.string.wallets_yield_starts_today))
                    }
                }
            }

            if (isCredit) {
                CreditCardSection(
                    cutDay = cutDay,
                    onCutDay = { cutDay = it; error = null },
                    dueDays = dueDays,
                    onDueDays = { dueDays = it; error = null },
                    creditLimit = creditLimit,
                    onCreditLimit = { creditLimit = it; error = null },
                    annivMonth = annivMonth,
                    onAnnivMonth = { annivMonth = it; error = null },
                    annivDay = annivDay,
                    onAnnivDay = { annivDay = it; error = null },
                    anniversaryNone = anniversaryNone,
                )
            }

            // What it holds right now, under everything — the opening balance
            // above is not the same number once there are movements, and the
            // web spells that out rather than leaving it to be inferred.
            existing?.let { w ->
                FieldHint(
                    stringResource(R.string.wallets_balance) + ": " +
                        formatMoney(w.balanceCents, w.currencyCode),
                )
            }
        }
    }
}

/**
 * The card settings the web reveals the moment the credit-card category is
 * picked: a bordered block with its explanation, the cut day and grace days
 * side by side, the limit, and the annual-fee date as month + day.
 */
@Composable
private fun CreditCardSection(
    cutDay: String,
    onCutDay: (String) -> Unit,
    dueDays: String,
    onDueDays: (String) -> Unit,
    creditLimit: String,
    onCreditLimit: (String) -> Unit,
    annivMonth: Int?,
    onAnnivMonth: (Int?) -> Unit,
    annivDay: String,
    onAnnivDay: (String) -> Unit,
    anniversaryNone: String,
) {
    val colors = Broke.colors
    val locale = Locale.forLanguageTag(LocalAppSettings.current.locale)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, colors.borderMuted, RoundedCornerShape(8.dp))
            .padding(14.dp),
    ) {
        Text(
            stringResource(R.string.credit_title),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = colors.fg,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.credit_form_hint),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
            color = colors.fgSubtle,
        )

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Each column carries its own hint, so they have to align at the
            // top: the two hints are different lengths and wrap differently.
            Column(Modifier.weight(1f)) {
                FormField(
                    label = stringResource(R.string.credit_cut_day),
                    value = cutDay,
                    onValueChange = { onCutDay(it.filter(Char::isDigit).take(2)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = "15",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                FieldHint(stringResource(R.string.credit_cut_day_hint))
            }
            Column(Modifier.weight(1f)) {
                FormField(
                    label = stringResource(R.string.credit_due_days),
                    value = dueDays,
                    onValueChange = { onDueDays(it.filter(Char::isDigit).take(2)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = "20",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                FieldHint(stringResource(R.string.credit_due_days_hint))
            }
        }

        Spacer(Modifier.height(12.dp))
        MoneyField(
            label = stringResource(R.string.credit_limit),
            value = creditLimit,
            onValueChange = onCreditLimit,
            modifier = Modifier.fillMaxWidth(),
        )
        FieldHint(stringResource(R.string.credit_limit_hint))

        Spacer(Modifier.height(12.dp))
        FieldLabel(stringResource(R.string.credit_anniversary))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PickerField(
                label = "",
                // null is a real choice here — "not tracked" — so it leads the list.
                options = listOf<Int?>(null) + (1..12).toList(),
                selected = annivMonth,
                optionLabel = { month ->
                    month?.let {
                        java.time.Month.of(it).getDisplayName(TextStyle.FULL, locale)
                            .replaceFirstChar { c -> c.uppercase(locale) }
                    } ?: anniversaryNone
                },
                onSelect = { onAnnivMonth(it) },
                emptyLabel = anniversaryNone,
                modifier = Modifier.weight(1f),
            )
            FormField(
                label = "",
                value = annivDay,
                onValueChange = { onAnnivDay(it.filter(Char::isDigit).take(2)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = stringResource(R.string.credit_anniversary_day),
                // The day means nothing without a month, exactly as on the web.
                enabled = annivMonth != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
    }
}
