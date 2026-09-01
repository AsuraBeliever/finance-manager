package com.asura.finanzas.ui.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
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
import com.asura.finanzas.ui.components.FormField
import com.asura.finanzas.ui.components.MoneyField
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asura.finanzas.ui.components.ColorPicker
import com.asura.finanzas.ui.components.FieldLabel
import androidx.compose.foundation.layout.padding
import com.asura.finanzas.R
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.NetworkException
import com.asura.finanzas.data.SavingsGoal
import com.asura.finanzas.data.Wallet
import com.asura.finanzas.ui.components.DateField
import com.asura.finanzas.ui.components.FormSheet
import com.asura.finanzas.ui.components.PickerField
import com.asura.finanzas.ui.components.SegmentedControl
import com.asura.finanzas.ui.components.WebCheckbox
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
    var color by remember { mutableStateOf(existing?.color) }
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
                        color = color,
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
        FormField(
            label = stringResource(R.string.goals_name),
            value = name,
            onValueChange = { name = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        // Web order: name · what it's for (+hint) · target and currency ·
        // wallet to reserve from (+hint) · deadline card · colour.
        Column {
            FieldLabel(stringResource(R.string.goals_kind_label))
            SegmentedControl(
                options = listOf("purchase", "fund"),
                selected = kind,
                label = {
                    stringResource(
                        if (it == "fund") R.string.goals_kind_fund else R.string.goals_kind_purchase,
                    )
                },
                onSelect = { kind = it },
                modifier = Modifier.fillMaxWidth(),
                fillEqually = true,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(
                    if (kind == "fund") R.string.goals_kind_fund_hint
                    else R.string.goals_kind_purchase_hint,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = Broke.colors.fgSubtle,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MoneyField(
                label = stringResource(R.string.goals_target),
                value = target,
                onValueChange = { target = it; error = null },
                modifier = Modifier.weight(1f),
            )
            FormField(
                label = stringResource(R.string.investments_currency),
                value = wallet?.currencyCode ?: existing?.currencyCode ?: "MXN",
                onValueChange = {},
                modifier = Modifier.weight(1f),
                enabled = false,
                readOnly = true,
            )
        }

        Column {
            PickerField(
                label = stringResource(R.string.goals_apartado_wallet),
                options = listOf<Wallet?>(null) + wallets.filter { !it.isArchived },
                selected = wallet,
                optionLabel = { it?.name ?: noneLabel },
                onSelect = { wallet = it },
                emptyLabel = noneLabel,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(
                    if (wallet == null) R.string.goals_apartado_none_hint
                    else R.string.goals_apartado_hint,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = Broke.colors.fgSubtle,
            )
        }

        // The deadline is a bordered card with a checkbox, as on the web.
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, Broke.colors.borderMuted, RoundedCornerShape(8.dp))
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WebCheckbox(
                    checked = hasDeadline,
                    onCheckedChange = { hasDeadline = it },
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.goals_enable_deadline),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = Broke.colors.fg,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.goals_deadline_hint),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                color = Broke.colors.fgSubtle,
            )
            if (hasDeadline) {
                Spacer(Modifier.height(12.dp))
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

        Column {
            FieldLabel(stringResource(R.string.categories_color))
            ColorPicker(value = color, onChange = { color = it })
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
        MoneyField(
            label = stringResource(R.string.goals_amount),
            value = amount,
            onValueChange = { amount = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            suffix = goal.currencyCode,
        )
    }
}
