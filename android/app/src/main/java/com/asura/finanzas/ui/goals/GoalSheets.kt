package com.asura.finanzas.ui.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.asura.finanzas.R
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.NetworkException
import com.asura.finanzas.data.SavingsGoal
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

/** Create or edit a savings goal. */
@Composable
fun GoalFormSheet(
    repository: BrokeRepository,
    existing: SavingsGoal?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var wallets by remember { mutableStateOf<List<Wallet>>(emptyList()) }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var target by remember {
        mutableStateOf(existing?.targetCents?.let { formatMoney(it, withSymbol = false) }.orEmpty())
    }
    var wallet by remember { mutableStateOf<Wallet?>(null) }
    var kind by remember { mutableStateOf(existing?.goalKind ?: "purchase") }
    // A deadline is optional; turning it on defaults to a monthly cadence and a
    // year out, the same defaults the web form starts from.
    var hasDeadline by remember { mutableStateOf(existing?.targetDate != null) }
    var targetDate by remember {
        mutableStateOf(
            existing?.targetDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: LocalDate.now().plusYears(1),
        )
    }
    var cadence by remember { mutableStateOf(existing?.cadence ?: "monthly") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val genericError = stringResource(R.string.common_error)
    val offlineError = stringResource(R.string.offline_banner)
    val noneLabel = stringResource(R.string.goals_apartado_none)

    LaunchedEffect(Unit) {
        wallets = runCatching { repository.wallets().value }.getOrDefault(emptyList())
        wallet = wallets.firstOrNull { it.id == existing?.linkedWalletId }
    }

    val cents = parseAmountToCents(target)
    val canSave = !busy && name.isNotBlank() && cents != null && cents > 0

    FormSheet(
        title = stringResource(
            if (existing == null) R.string.goals_new_goal else R.string.goals_edit_goal,
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
                    repository.saveGoal(
                        id = existing?.id,
                        name = name,
                        currencyCode = wallet?.currencyCode ?: existing?.currencyCode ?: "MXN",
                        targetCents = cents ?: 0,
                        walletId = wallet?.id,
                        color = existing?.color,
                        // Clearing the deadline clears the cadence with it: the
                        // server only computes a plan when both are present.
                        targetDate = if (hasDeadline) targetDate.toString() else null,
                        cadence = if (hasDeadline) cadence else null,
                        goalKind = kind,
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
            label = { Text(stringResource(R.string.goals_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = target,
            onValueChange = { target = it; error = null },
            label = { Text(stringResource(R.string.goals_target)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        PickerField(
            label = stringResource(R.string.goals_apartado_wallet),
            options = listOf<Wallet?>(null) + wallets.filter { !it.isArchived },
            selected = wallet,
            optionLabel = { it?.name ?: noneLabel },
            onSelect = { wallet = it },
            emptyLabel = noneLabel,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
        ) {
            SegmentedControl(
                options = listOf("purchase", "fund"),
                selected = kind,
                label = {
                    stringResource(
                        if (it == "fund") R.string.goals_kind_fund else R.string.goals_kind_purchase,
                    )
                },
                onSelect = { kind = it },
            )
        }

        // Deadline + cadence: with both set the server returns a contribution
        // plan and the card starts telling you what to set aside each period.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.goals_enable_deadline),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Broke.colors.fg,
                )
                Text(
                    stringResource(R.string.goals_deadline_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = Broke.colors.fgSubtle,
                )
            }
            Switch(checked = hasDeadline, onCheckedChange = { hasDeadline = it })
        }

        if (hasDeadline) {
            DateField(
                label = stringResource(R.string.goals_deadline_date_label),
                value = targetDate,
                onChange = { targetDate = it },
            )
            PickerField(
                label = stringResource(R.string.goals_cadence_label),
                options = listOf("daily", "weekly", "monthly", "yearly"),
                selected = cadence,
                optionLabel = {
                    stringResource(
                        when (it) {
                            "daily" -> R.string.goals_cadence_daily
                            "weekly" -> R.string.goals_cadence_weekly
                            "yearly" -> R.string.goals_cadence_yearly
                            else -> R.string.goals_cadence_monthly
                        },
                    )
                },
                onSelect = { cadence = it },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Reserve into, or release from, a goal. */
@Composable
fun ContributeSheet(
    repository: BrokeRepository,
    goal: SavingsGoal,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var amount by remember { mutableStateOf("") }
    var release by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val genericError = stringResource(R.string.common_error)
    val offlineError = stringResource(R.string.offline_banner)

    val cents = parseAmountToCents(amount)
    val canSave = !busy && cents != null && cents > 0

    FormSheet(
        title = stringResource(R.string.goals_contribute_title),
        busy = busy,
        error = error,
        canSave = canSave,
        onDismiss = onDismiss,
        saveLabel = stringResource(
            if (release) R.string.goals_release_action else R.string.goals_reserve_action,
        ),
        onSave = {
            busy = true
            error = null
            scope.launch {
                // The sign is the whole instruction to the server: negative
                // releases. The clamp at zero is its job, not ours.
                val signed = if (release) -(cents ?: 0) else (cents ?: 0)
                runCatching { repository.contributeToGoal(goal.id, signed) }
                    .onSuccess { onSaved() }
                    .onFailure {
                        error = if (it is NetworkException) offlineError else it.message ?: genericError
                        busy = false
                    }
            }
        },
    ) {
        SegmentedControl(
            options = listOf(false, true),
            selected = release,
            label = {
                stringResource(if (it) R.string.goals_release_tab else R.string.goals_reserve_tab)
            },
            onSelect = { release = it },
        )
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it; error = null },
            label = { Text(stringResource(R.string.goals_amount)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            suffix = { Text(goal.currencyCode, color = Broke.colors.fgSubtle) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
