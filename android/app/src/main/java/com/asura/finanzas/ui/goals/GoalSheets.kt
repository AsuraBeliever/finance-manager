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
import com.asura.finanzas.ui.LocalAppSettings
import com.asura.finanzas.ui.maskIfHidden
import com.asura.finanzas.ui.components.FieldHint
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import com.asura.finanzas.ui.components.pullUp
import com.asura.finanzas.ui.components.FormField
import com.asura.finanzas.ui.components.MoneyField
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.asura.finanzas.ui.components.SegStyle
import com.asura.finanzas.ui.components.SegmentedControl
import com.asura.finanzas.ui.components.WebCheckbox
import com.asura.finanzas.ui.formatMoney
import com.asura.finanzas.ui.text
import com.asura.finanzas.ui.parseAmountToCents
import com.asura.finanzas.ui.theme.Broke
import com.asura.finanzas.ui.theme.tabular
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
    var tracksWallet by remember { mutableStateOf(existing?.tracksWallet ?: false) }
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
    // A new goal opens on the first chart colour, as the web form does; leaving
    // it unset showed a colour row with nothing chosen.
    var color by remember { mutableStateOf(existing?.color ?: "#a855f7") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val genericError = stringResource(R.string.common_error)
    val offlineError = stringResource(R.string.offline_banner)

    LaunchedEffect(Unit) {
        wallets = runCatching { repository.wallets().value }.getOrDefault(emptyList())
        // Every goal reserves from a wallet — the web made it mandatory when
        // goals became apartados — so it opens on one instead of on "none".
        wallet = wallets.firstOrNull { it.id == existing?.linkedWalletId }
            ?: wallets.firstOrNull { !it.isArchived }
    }

    val cents = parseAmountToCents(target)
    val canSave = !busy && name.isNotBlank() && cents != null && cents > 0 && wallet != null

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
                        tracksWallet = tracksWallet,
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
            // `flex gap-1 rounded-xl bg-surface-overlay p-1`, raised chip.
            SegmentedControl(
                style = SegStyle.Pill,
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
            Spacer(Modifier.height(4.dp))
            FieldHint(
                stringResource(
                    if (kind == "fund") R.string.goals_kind_fund_hint
                    else R.string.goals_kind_purchase_hint,
                ),
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
                // A disabled input keeps `text-fg` on the web.
                valueColor = Broke.colors.fg,
            )
        }

        Column {
            PickerField(
                label = stringResource(R.string.goals_goal_wallet),
                options = wallets.filter { !it.isArchived },
                selected = wallet,
                optionLabel = { "${it.name} (${it.currencyCode})" },
                onSelect = { wallet = it },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // How the money is kept: an apartado inside the wallet, or the whole
        // wallet (progress = its balance). Same pill toggle as the purpose.
        Column {
            FieldLabel(stringResource(R.string.goals_mode_label))
            SegmentedControl(
                style = SegStyle.Pill,
                options = listOf(false, true),
                selected = tracksWallet,
                label = {
                    stringResource(
                        if (it) R.string.goals_mode_wallet else R.string.goals_mode_apartado,
                    )
                },
                onSelect = { tracksWallet = it },
                modifier = Modifier.fillMaxWidth(),
                fillEqually = true,
            )
            Spacer(Modifier.height(4.dp))
            FieldHint(
                stringResource(
                    if (tracksWallet) R.string.goals_mode_wallet_hint
                    else R.string.goals_apartado_hint,
                ),
            )
        }

        // The deadline is a bordered card with a checkbox, as on the web:
        // `rounded-lg border p-3`, the label `items-start gap-2.5` with a
        // 16 px box and the hint indented under its words.
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, Broke.colors.borderMuted, RoundedCornerShape(8.dp))
                .padding(13.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.clickable { hasDeadline = !hasDeadline },
            ) {
                WebCheckbox(
                    checked = hasDeadline,
                    onCheckedChange = { hasDeadline = it },
                    boxSize = 16.dp,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Column {
                    Text(
                        stringResource(R.string.goals_enable_deadline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Broke.colors.fg,
                    )
                    Text(
                        stringResource(R.string.goals_deadline_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = Broke.colors.fgSubtle,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (hasDeadline) {
                // `mt-3 grid gap-3`; the date cannot be before today.
                Spacer(Modifier.height(12.dp))
                DateField(
                    label = stringResource(R.string.goals_deadline_date_label),
                    value = targetDate,
                    onChange = { targetDate = it },
                    min = java.time.LocalDate.now(),
                )
                Spacer(Modifier.height(12.dp))
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
    var wallets by remember { mutableStateOf<List<Wallet>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val genericError = stringResource(R.string.common_error)
    val offlineError = stringResource(R.string.offline_banner)
    val releaseTooMuch = text(
        R.string.goals_release_too_much,
        "amount" to formatMoney(goal.savedCents, goal.currencyCode),
    )
    val hide = LocalAppSettings.current.hideBalances

    LaunchedEffect(goal.id) {
        wallets = runCatching { repository.wallets().value }.getOrDefault(emptyList())
    }

    // The wallet the goal reserves from, and what is free to reserve in it —
    // the same two figures the web puts above the amount.
    val apartadoWallet = goal.linkedWalletId?.let { id -> wallets.firstOrNull { it.id == id } }
    val availableCents = apartadoWallet?.let { it.balanceCents - it.reservedCents }
    // Only a goal with something in it can give any back.
    val canRelease = goal.savedCents > 0
    // What is missing to cover this period; once covered, the next period's share.
    val suggested = if (!release) {
        goal.plan?.let { if (it.periodMissingCents > 0) it.periodMissingCents else it.perPeriodCents }
            ?: 0L
    } else {
        0L
    }

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
            // Giving back more than was set aside is the one case worth naming
            // before the round trip, the way the web names it.
            if (release && (cents ?: 0) > goal.savedCents) {
                error = releaseTooMuch
            } else {
                busy = true
                error = null
                scope.launch {
                    // The sign is the whole instruction to the server: negative
                    // releases. The clamp at zero is its job, not ours.
                    val signed = if (release) -(cents ?: 0) else (cents ?: 0)
                    runCatching { repository.contributeToGoal(goal.id, signed) }
                        .onSuccess { onSaved() }
                        .onFailure {
                            error = if (it is NetworkException) {
                                offlineError
                            } else {
                                it.message ?: genericError
                            }
                            busy = false
                        }
                }
            }
        },
    ) {
        // `flex gap-1 rounded-xl bg-surface-overlay p-1`, chips `py-2`.
        SegmentedControl(
            style = SegStyle.Pill,
            // Release only shows once there is something to give back, as on
            // the web — an empty goal has nothing to release.
            options = if (canRelease) listOf(false, true) else listOf(false),
            selected = release,
            label = {
                stringResource(if (it) R.string.goals_release_tab else R.string.goals_reserve_tab)
            },
            onSelect = { release = it },
            modifier = Modifier.fillMaxWidth(),
            fillEqually = true,
            chipVerticalPadding = 8.dp,
        )

        // Where the money comes from and how much of it there is; on release,
        // how much is reserved to give back. The names and figures are
        // `font-medium text-fg` inside the muted sentence.
        val fgStyle = SpanStyle(color = Broke.colors.fg, fontWeight = FontWeight.Medium)
        val context = when {
            !release && apartadoWallet != null && availableCents != null -> buildAnnotatedString {
                append(stringResource(R.string.goals_apartado_of) + " ")
                withStyle(fgStyle) { append(apartadoWallet.name) }
                append(" · " + stringResource(R.string.goals_available) + " ")
                withStyle(fgStyle) {
                    append(maskIfHidden(formatMoney(availableCents, apartadoWallet.currencyCode), hide))
                }
            }
            release -> buildAnnotatedString {
                append(stringResource(R.string.goals_reserved_label) + " ")
                withStyle(fgStyle) { append(maskIfHidden(formatMoney(goal.savedCents, goal.currencyCode), hide)) }
            }
            else -> null
        }
        context?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall.tabular(),
                color = Broke.colors.fgMuted,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Broke.colors.surfaceOverlay)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        // `autoFocus`, like the web's amount box.
        val amountFocus = remember { androidx.compose.ui.focus.FocusRequester() }
        androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { amountFocus.requestFocus() } }
        MoneyField(
            label = stringResource(R.string.goals_amount),
            value = amount,
            onValueChange = { amount = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            focusRequester = amountFocus,
        )

        // One tap fills in what this period asks for: `-mt-2 self-start
        // rounded-lg bg-accent/10 px-3 py-1.5 text-xs font-medium`.
        if (suggested > 0) {
            Text(
                stringResource(R.string.goals_suggested_chip) + " " +
                    maskIfHidden(formatMoney(suggested, goal.currencyCode), hide),
                style = MaterialTheme.typography.labelSmall.tabular(),
                color = Broke.colors.accent,
                modifier = Modifier
                    .pullUp(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Broke.colors.accent.copy(alpha = 0.1f))
                    .clickable {
                        amount = java.math.BigDecimal(suggested).movePointLeft(2).toPlainString()
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }

        // `-mt-1`.
        Box(Modifier.pullUp(4.dp)) {
        FieldHint(
            stringResource(
                when {
                    !release && apartadoWallet != null -> R.string.goals_reserve_hint
                    !release -> R.string.goals_reserve_track_hint
                    apartadoWallet != null -> R.string.goals_release_hint
                    else -> R.string.goals_release_track_hint
                },
            ),
        )
        }
    }
}
