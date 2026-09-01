package com.asura.finanzas.ui.wallets

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.asura.finanzas.ui.components.FormField
import com.asura.finanzas.ui.components.MoneyField
import com.asura.finanzas.R
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.MsiSchedulePreview
import com.asura.finanzas.data.NetworkException
import com.asura.finanzas.data.TransactionCategory
import com.asura.finanzas.data.Wallet
import com.asura.finanzas.ui.LocalAppSettings
import com.asura.finanzas.ui.components.DateField
import com.asura.finanzas.ui.components.FormSheet
import com.asura.finanzas.ui.components.PickerField
import com.asura.finanzas.ui.formatMoney
import com.asura.finanzas.ui.parseAmountToCents
import com.asura.finanzas.ui.seedName
import com.asura.finanzas.ui.text
import com.asura.finanzas.ui.theme.Broke
import kotlinx.coroutines.launch
import java.time.LocalDate

/** The web's own bounds; the server rejects anything outside them anyway. */
private const val MIN_MONTHS = 2
private const val MAX_MONTHS = 60

/**
 * Record an interest-free instalment purchase on a credit card. Instead of one
 * expense, the worker creates a plan whose instalments post themselves — one per
 * statement. Every figure in the preview comes from the server.
 */
@Composable
fun MsiPlanSheet(
    repository: BrokeRepository,
    wallet: Wallet,
    onDismiss: () -> Unit,
    onSaved: (MsiSchedulePreview) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val colors = Broke.colors

    var description by remember { mutableStateOf("") }
    var total by remember { mutableStateOf("") }
    var monthsText by remember { mutableStateOf("12") }
    var purchasedAt by remember { mutableStateOf(LocalDate.now()) }
    var category by remember { mutableStateOf<TransactionCategory?>(null) }
    var categories by remember { mutableStateOf<List<TransactionCategory>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val genericError = stringResource(R.string.common_error)
    val offlineError = stringResource(R.string.offline_banner)
    val needsDescription = stringResource(R.string.credit_msi_needs_description)
    val invalidMonths = stringResource(R.string.credit_msi_invalid_months)
    val noCategory = stringResource(R.string.transactions_no_category)

    LaunchedEffect(Unit) {
        categories = runCatching { repository.transactionCategories("expense") }
            .getOrDefault(emptyList())
    }

    val totalCents = parseAmountToCents(total)
    val months = monthsText.trim().toIntOrNull()
    val monthsValid = months != null && months in MIN_MONTHS..MAX_MONTHS
    val canSave = !busy && description.isNotBlank() &&
        totalCents != null && totalCents > 0 && monthsValid

    // Live schedule while typing; a failed preview just shows nothing.
    val preview by produceState<MsiSchedulePreview?>(null, totalCents, months, purchasedAt) {
        value = if (totalCents == null || totalCents <= 0 || !monthsValid) {
            null
        } else {
            runCatching {
                repository.previewMsiPlan(
                    walletId = wallet.id,
                    totalCents = totalCents,
                    months = months!!,
                    purchasedAt = purchasedAt.toString(),
                )
            }.getOrNull()
        }
    }

    FormSheet(
        title = stringResource(R.string.credit_msi_add),
        busy = busy,
        error = error,
        canSave = canSave,
        onDismiss = onDismiss,
        onSave = {
            if (description.isBlank()) {
                error = needsDescription
                return@FormSheet
            }
            if (!monthsValid) {
                error = invalidMonths
                return@FormSheet
            }
            busy = true
            error = null
            scope.launch {
                runCatching {
                    repository.createMsiPlan(
                        walletId = wallet.id,
                        description = description,
                        totalCents = totalCents ?: 0,
                        months = months ?: 0,
                        purchasedAt = purchasedAt.toString(),
                        categoryId = category?.id,
                    )
                }
                    .onSuccess { onSaved(it) }
                    .onFailure {
                        error = if (it is NetworkException) offlineError else it.message ?: genericError
                        busy = false
                    }
            }
        },
    ) {
        FormField(
            label = stringResource(R.string.transactions_description),
            value = description,
            onValueChange = { description = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        MoneyField(
            label = stringResource(R.string.credit_msi_total),
            value = total,
            onValueChange = { total = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            suffix = wallet.currencyCode,
        )
        FormField(
            label = stringResource(R.string.credit_msi_months),
            value = monthsText,
            onValueChange = { monthsText = it.filter { c -> c.isDigit() }.take(2); error = null },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = monthsText.isNotBlank() && !monthsValid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
        DateField(
            label = stringResource(R.string.credit_msi_purchased_at),
            value = purchasedAt,
            onChange = { purchasedAt = it },
        )
        Text(
            stringResource(R.string.credit_msi_backdated_hint),
            style = MaterialTheme.typography.labelSmall,
            color = colors.fgSubtle,
        )

        preview?.let { MsiPreviewLines(it, wallet.currencyCode) }
    }
}

/**
 * A business date as "5 sep", with the year only when it is not the current one
 * — the web's `formatDayMonth`, so both apps word a schedule the same way.
 */
@Composable
fun formatDayMonth(iso: String): String {
    val locale = java.util.Locale.forLanguageTag(LocalAppSettings.current.locale)
    val date = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return iso
    val month = date.month.getDisplayName(java.time.format.TextStyle.SHORT, locale)
        .removeSuffix(".")
    // English puts the month first ("Sep 4"), Spanish the day ("4 sept").
    val head = if (locale.language == "en") "$month ${date.dayOfMonth}" else "${date.dayOfMonth} $month"
    if (date.year == LocalDate.now().year) return head
    // "Jul 10, 2027" in English; "10 jul 2027" in Spanish.
    return if (locale.language == "en") "$head, ${date.year}" else "$head ${date.year}"
}

/**
 * The schedule in words, exactly as the web's `MsiPreviewLine` puts it: the
 * monthly amount, then one line about what joins the debt now.
 *
 * A purchase always drops its first instalment into the debt today, so the
 * "back-dated" wording only makes sense past the second one — which is why the
 * threshold is `> 1` and the two lines are alternatives, never both.
 */
@Composable
fun MsiPreviewLines(preview: MsiSchedulePreview, currency: String) {
    val colors = Broke.colors
    Text(
        text(
            R.string.credit_msi_preview_line,
            "monthly" to formatMoney(preview.monthlyCents, currency),
            "months" to preview.months,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = colors.fgMuted,
    )
    Text(
        if (preview.alreadyBilledMonths > 1) {
            text(
                R.string.credit_msi_preview_backdated,
                "n" to preview.alreadyBilledMonths,
                "amount" to formatMoney(preview.alreadyBilledCents, currency),
            )
        } else {
            text(
                R.string.credit_msi_preview_first,
                "amount" to formatMoney(preview.firstChargeCents, currency),
                "cut" to formatDayMonth(preview.firstCutDate),
            )
        },
        style = MaterialTheme.typography.labelSmall,
        color = colors.fgSubtle,
    )
}

/** The same schedule after saving, worded as a confirmation. */
@Composable
fun MsiSavedInfo(preview: MsiSchedulePreview, currency: String) {
    val colors = Broke.colors
    Text(
        text(
            R.string.credit_msi_saved_body,
            "amount" to formatMoney(preview.firstChargeCents, currency),
            "cut" to formatDayMonth(preview.firstCutDate),
            "last" to formatDayMonth(preview.lastChargeDate),
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = colors.fgMuted,
    )
    if (preview.alreadyBilledMonths > 1) {
        Text(
            text(
                R.string.credit_msi_saved_backdated,
                "n" to preview.alreadyBilledMonths,
                "amount" to formatMoney(preview.alreadyBilledCents, currency),
            ),
            style = MaterialTheme.typography.labelSmall,
            color = colors.fgSubtle,
        )
    }
}
