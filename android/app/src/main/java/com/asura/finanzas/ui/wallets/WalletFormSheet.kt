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
import com.asura.finanzas.ui.components.FormField
import com.asura.finanzas.ui.components.MoneyField
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
import com.asura.finanzas.ui.parseAmountToCents
import com.asura.finanzas.ui.theme.Broke
import kotlinx.coroutines.launch

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
                (cents / 100).toString() + "." + (kotlin.math.abs(cents % 100)).toString().padStart(2, '0')
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
    var parent by remember { mutableStateOf(parentDefault) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Resolved here because the save coroutine is not a composable scope.
    val genericError = stringResource(R.string.common_error)
    val offlineError = stringResource(R.string.offline_banner)
    val parentNone = stringResource(R.string.wallets_parent_none)

    LaunchedEffect(Unit) {
        categories = runCatching { repository.walletCategories() }.getOrDefault(emptyList())
        currencies = runCatching { repository.currencies() }.getOrDefault(emptyList())
        category = categories.firstOrNull { it.id == existing?.categoryId } ?: categories.firstOrNull()
        currency = currencies.firstOrNull { it.code == (existing?.currencyCode ?: "MXN") }
            ?: currencies.firstOrNull()
        parent = wallets.firstOrNull { it.id == existing?.parentWalletId }
    }

    val canSave = !busy && name.isNotBlank() && category != null && currency != null

    fun save() {
        val chosenCategory = category ?: return
        val chosenCurrency = currency ?: return
        busy = true
        error = null
        scope.launch {
            runCatching {
                repository.saveWallet(
                    id = existing?.id,
                    name = name,
                    categoryId = chosenCategory.id,
                    currencyCode = chosenCurrency.code,
                    initialBalanceCents = parseAmountToCents(initial) ?: 0,
                    color = existing?.color,
                    skin = skinId,
                    notes = notes,
                    // Percent in the field, basis points on the wire.
                    yieldRateBps = if (earnsYield) {
                        yieldRate.replace(',', '.').toDoubleOrNull()?.let { Math.round(it * 100) }
                    } else {
                        null
                    },
                    yieldFrequency = if (earnsYield) existing?.yieldFrequency ?: "daily" else null,
                    parentWalletId = parent?.id,
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

            MoneyField(
                label = stringResource(R.string.wallets_initial_balance),
                value = initial,
                onValueChange = { initial = it },
                modifier = Modifier.fillMaxWidth(),
            )

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
                    FormField(
                        label = stringResource(R.string.wallets_yield_rate),
                        value = yieldRate,
                        onValueChange = { yieldRate = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        suffix = "%",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            }

        }
    }
}
