package com.asura.finanzas.ui.investments

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import com.asura.finanzas.ui.components.Lucide
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.border
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import androidx.compose.ui.graphics.Color
import com.asura.finanzas.ui.components.MoneyField
import com.asura.finanzas.ui.components.PickerField
import com.asura.finanzas.ui.parseAmountToCents
import com.asura.finanzas.data.InvestmentProjection
import com.asura.finanzas.ui.components.OutlineButton
import com.asura.finanzas.R
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.InvestmentDetail
import com.asura.finanzas.data.InvestmentMovement
import com.asura.finanzas.ui.LocalAppSettings
import com.asura.finanzas.ui.components.ConfirmDialog
import com.asura.finanzas.ui.components.DialogAction
import com.asura.finanzas.ui.components.GlassCard
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import com.asura.finanzas.ui.components.PanelCard
import com.asura.finanzas.ui.components.GhostButton
import com.asura.finanzas.ui.components.FlexShrinkRow
import com.asura.finanzas.ui.components.WebPositive
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import com.asura.finanzas.ui.components.HairLine
import com.asura.finanzas.ui.components.HeroAmount
import com.asura.finanzas.ui.components.LoadingBox
import com.asura.finanzas.ui.components.LineChart
import com.asura.finanzas.ui.components.TooltipContent
import com.asura.finanzas.ui.components.TooltipItem
import com.asura.finanzas.ui.components.MicroLabel
import com.asura.finanzas.ui.components.PrimaryButton
import com.asura.finanzas.ui.formatDelta
import com.asura.finanzas.ui.formatMoney
import com.asura.finanzas.ui.maskIfHidden
import com.asura.finanzas.ui.theme.Broke
import com.asura.finanzas.ui.theme.tabular
import kotlinx.coroutines.launch

@Composable
fun InvestmentDetailScreen(
    repository: BrokeRepository,
    investmentId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var detail by remember { mutableStateOf<InvestmentDetail?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    var addingMovement by remember { mutableStateOf<String?>(null) }
    var addingSnapshot by remember { mutableStateOf(false) }
    var editingMovement by remember { mutableStateOf<InvestmentMovement?>(null) }
    var editingInvestment by remember { mutableStateOf(false) }
    // Non-null while a "delete this?" confirmation is up. The web asks before
    // either deletion; the phone was doing both on a single tap.
    var deletingInvestment by remember { mutableStateOf(false) }
    var deletingMovement by remember { mutableStateOf<InvestmentMovement?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(investmentId, reloadKey) {
        detail = runCatching { repository.investmentDetail(investmentId) }.getOrNull()
    }

    val current = detail
    if (current == null) {
        LoadingBox(modifier)
        return
    }

    DetailContent(
        repository = repository,
        detail = current,
        onBack = onBack,
        onAddMovement = { addingMovement = it },
        onAddSnapshot = { addingSnapshot = true },
        onEdit = { editingInvestment = true },
        onToggleClosed = {
            scope.launch {
                runCatching { repository.closeInvestment(investmentId, !current.isClosed) }
                reloadKey++
            }
        },
        // Deleting takes its snapshots with it, so it asks first.
        onDelete = { deletingInvestment = true },
        onEditMovement = { editingMovement = it },
        onDeleteMovement = { deletingMovement = it },
        modifier = modifier,
    )

    addingMovement?.let { kind ->
        InvestmentMovementSheet(
            repository = repository,
            investment = current,
            defaultKind = kind,
            onDismiss = { addingMovement = null },
            onSaved = { addingMovement = null; reloadKey++ },
        )
    }

    editingMovement?.let { movement ->
        InvestmentMovementSheet(
            repository = repository,
            investment = current,
            existing = movement,
            onDismiss = { editingMovement = null },
            onSaved = { editingMovement = null; reloadKey++ },
        )
    }

    if (editingInvestment) {
        NewInvestmentSheet(
            repository = repository,
            existing = current,
            onDismiss = { editingInvestment = false },
            onSaved = { editingInvestment = false; reloadKey++ },
        )
    }

    if (addingSnapshot) {
        SnapshotSheet(
            repository = repository,
            investment = current,
            onDismiss = { addingSnapshot = false },
            onSaved = { addingSnapshot = false; reloadKey++ },
        )
    }

    if (deletingInvestment) {
        ConfirmDialog(
            title = stringResource(R.string.investments_delete_confirm_title),
            message = stringResource(R.string.investments_delete_confirm),
            onConfirm = {
                deletingInvestment = false
                scope.launch {
                    runCatching { repository.deleteInvestment(investmentId) }
                    onBack()
                }
            },
            onDismiss = { deletingInvestment = false },
        )
    }

    deletingMovement?.let { movement ->
        ConfirmDialog(
            title = stringResource(R.string.investments_movement_delete_title),
            message = stringResource(R.string.investments_movement_delete_confirm),
            onConfirm = {
                deletingMovement = null
                scope.launch {
                    runCatching { repository.deleteInvestmentMovement(movement.id) }
                    reloadKey++
                }
            },
            onDismiss = { deletingMovement = null },
        )
    }
}

@Composable
private fun DetailContent(
    repository: BrokeRepository,
    detail: InvestmentDetail,
    onBack: () -> Unit,
    /** "deposit" or "withdrawal": the sheet opens on the one that was asked for. */
    onAddMovement: (String) -> Unit,
    onAddSnapshot: () -> Unit,
    onEdit: () -> Unit,
    onToggleClosed: () -> Unit,
    onDelete: () -> Unit,
    onEditMovement: (InvestmentMovement) -> Unit,
    onDeleteMovement: (InvestmentMovement) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Broke.colors
    val hide = LocalAppSettings.current.hideBalances
    // Horizon in years, as the web's zoom control sets it.
    var years by remember { mutableStateOf(5) }
    // The what-if: money added on a cadence, projected onto this same chart.
    // Typing an amount is what arms it, not the toggle — the toggle only opens
    // the controls, exactly as on the web.
    var simEnabled by remember { mutableStateOf(false) }
    var simContribution by remember { mutableStateOf("") }
    var simCadence by remember { mutableStateOf("monthly") }
    val simContributionCents = if (simEnabled) parseAmountToCents(simContribution) ?: 0 else 0
    val simActive = simContributionCents > 0

    var projection by remember { mutableStateOf<InvestmentProjection?>(null) }
    LaunchedEffect(detail.id, years, simContributionCents, simCadence) {
        projection = runCatching {
            repository.projectInvestment(
                detail.id,
                years * 12,
                contributionCents = simContributionCents,
                cadence = if (simActive) simCadence else "none",
            )
        }.getOrNull()
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 28.dp),
    ) {
        item {
            // This page has no back link in the browser and no cyan rule
            // either: `mb-6 flex items-center justify-between`, the name as a
            // `text-2xl font-semibold` heading and three buttons. They share
            // one row that shrinks like flexbox, so a long name wraps.
            BackHandler(onBack = onBack)
            FlexShrinkRow(
                gap = 0.dp,
                spaceBetween = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            ) {
                Text(
                    detail.name,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 24.sp,
                        lineHeight = 32.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        letterSpacing = com.asura.finanzas.ui.theme.TrackingTight,
                    ),
                    color = colors.fg,
                )
                FlexShrinkRow(gap = 8.dp) {
                    GhostButton(stringResource(R.string.common_edit), onClick = onEdit, leadingIcon = Lucide.Pencil)
                    // One button that flips both ways, as on the web.
                    GhostButton(
                        stringResource(
                            if (detail.isClosed) R.string.investments_reopen
                            else R.string.investments_close,
                        ),
                        onClick = onToggleClosed,
                        leadingIcon = if (detail.isClosed) Lucide.LockOpen else Lucide.Lock,
                    )
                    GhostButton(
                        stringResource(R.string.common_delete),
                        onClick = onDelete,
                        leadingIcon = Lucide.Trash,
                        tint = colors.danger,
                    )
                }
            }
        }

        // Four figures in a `grid-cols-2 gap-4`, then `mb-4`.
        item {
            val crypto = cryptoHolding(detail)
            Column(Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    StatBox(
                        stringResource(R.string.investments_current_value),
                        maskIfHidden(formatMoney(detail.currentValueCents, detail.currencyCode), hide),
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    StatBox(
                        stringResource(R.string.investments_gain),
                        maskIfHidden(formatDelta(detail.gainCents, detail.currencyCode), hide),
                        valueColor = if (detail.gainCents >= 0) colors.accent else colors.danger,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    StatBox(
                        stringResource(R.string.investments_net_invested),
                        maskIfHidden(formatMoney(detail.netInvestedCents, detail.currencyCode), hide),
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    // Whichever fact the instrument has: how much crypto is
                    // held, else the maturity date, else the start date.
                    when {
                        crypto != null -> StatBox(
                            stringResource(R.string.investments_quantity),
                            crypto,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                        detail.maturityDate != null -> StatBox(
                            stringResource(R.string.investments_maturity),
                            detail.maturityDate,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                        else -> StatBox(
                            stringResource(R.string.investments_start_date),
                            detail.startDate,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                // `mb-4 flex flex-wrap items-center justify-between gap-3`: the
                // title with the rate on its baseline, then the zoom and the
                // what-if toggle as one group that wraps below at this width.
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(end = 12.dp).align(Alignment.CenterVertically),
                    ) {
                        Text(
                            stringResource(R.string.investments_projection),
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.fg,
                            modifier = Modifier.alignByBaseline(),
                        )
                        projection?.annualRateBps?.let { bps ->
                            Text(
                                stringResource(R.string.investments_projection_at_rate)
                                    .replace("{rate}", "%.2f".format(bps / 100.0)),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.fgSubtle,
                                modifier = Modifier.alignByBaseline(),
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // `rounded-lg border p-0.5 gap-0.5`: zoom in, the years,
                        // "años", zoom out.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, colors.borderMuted, RoundedCornerShape(8.dp))
                                .padding(3.dp),
                        ) {
                            Icon(
                                Lucide.ZoomIn,
                                contentDescription = null,
                                tint = colors.fgMuted.copy(alpha = if (years > 1) 1f else 0.3f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable(enabled = years > 1) { years -= 1 }
                                    .padding(4.dp)
                                    .size(15.dp),
                            )
                            Text(
                                "$years",
                                style = MaterialTheme.typography.bodySmall.tabular()
                                    .copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                                color = colors.fg,
                                modifier = Modifier.width(32.dp),
                            )
                            Text(
                                stringResource(R.string.investments_projection_years_short),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.fgSubtle,
                                modifier = Modifier.padding(end = 4.dp),
                            )
                            Icon(
                                Lucide.ZoomOut,
                                contentDescription = null,
                                tint = colors.fgMuted.copy(alpha = if (years < 50) 1f else 0.3f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable(enabled = years < 50) { years += 1 }
                                    .padding(4.dp)
                                    .size(15.dp),
                            )
                        }
                        // `px-3 py-1.5 gap-1.5 text-sm font-medium`, bordered.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .then(
                                    if (simEnabled) Modifier.background(colors.accent.copy(alpha = 0.15f))
                                    else Modifier,
                                )
                                .border(
                                    1.dp,
                                    if (simEnabled) colors.accent.copy(alpha = 0.4f) else colors.borderMuted,
                                    RoundedCornerShape(8.dp),
                                )
                                .clickable { simEnabled = !simEnabled }
                                .padding(horizontal = 13.dp, vertical = 7.dp),
                        ) {
                            val tint = if (simEnabled) colors.accent else colors.fgMuted
                            Icon(Lucide.SlidersHorizontal, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
                            Text(
                                stringResource(R.string.investments_projection_sim_toggle),
                                style = MaterialTheme.typography.labelLarge,
                                color = tint,
                            )
                        }
                    }
                }

                if (simEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MoneyField(
                            label = stringResource(R.string.investments_projection_contribution),
                            value = simContribution,
                            onValueChange = { simContribution = it },
                            modifier = Modifier.weight(1f),
                        )
                        PickerField(
                            label = stringResource(R.string.simulator_cadence),
                            options = SIM_CADENCES,
                            selected = simCadence,
                            optionLabel = { stringResource(simCadenceLabel(it)) },
                            onSelect = { simCadence = it },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // Only once there is money to add: with nothing typed these
                // three would just restate the plain projection.
                if (simActive) {
                    projection?.let { p ->
                        // One column at phone width — the web's grid only goes
                        // to three from `sm:` up.
                        MiniStat(
                            stringResource(R.string.investments_projection_final),
                            maskIfHidden(formatMoney(p.finalValueCents, detail.currencyCode), hide),
                            colors.accent,
                        )
                        Spacer(Modifier.height(12.dp))
                        MiniStat(
                            stringResource(R.string.investments_projection_contributed),
                            maskIfHidden(formatMoney(p.contributedCents, detail.currencyCode), hide),
                            colors.fg,
                        )
                        Spacer(Modifier.height(12.dp))
                        MiniStat(
                            stringResource(R.string.investments_projection_interest),
                            maskIfHidden(formatMoney(p.interestCents, detail.currencyCode), hide),
                            colors.cyan,
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                }

                val points = projection?.projection.orEmpty().ifEmpty { detail.projection }
                if (points.size >= 2) {
                    // Every point comes from finanzas-core; the chart only maps
                    // the given cents onto pixels.
                    val today = java.time.LocalDate.now().toString()
                    val currentValueName = stringResource(R.string.investments_current_value)
                    val projectionName = stringResource(R.string.investments_projection)
                    val withContribName = stringResource(R.string.investments_projection_with_contrib)
                    LineChart(
                        values = points.map { it.valueCents },
                        dates = points.map { it.date },
                        today = today,
                        // The web's SIM_GOLD while the what-if is on: a
                        // simulated curve should not look like the real one.
                        forecastColor = if (simActive) Color(0xFFC9A14A) else null,
                        maskTicks = hide,
                        // The web's tooltip: the raw date, then whichever of
                        // its three series has a value there — up to today the
                        // real value, from today on the projection (or the
                        // what-if). Today itself carries both.
                        tooltip = { i ->
                            val point = points[i]
                            val amount = maskIfHidden(formatMoney(point.valueCents, detail.currencyCode), hide)
                            TooltipContent(
                                label = point.date,
                                items = buildList {
                                    if (point.date <= today) add(TooltipItem(currentValueName, amount, WebPositive))
                                    if (point.date >= today) {
                                        add(
                                            if (simActive) {
                                                TooltipItem(withContribName, amount, Color(0xFFC9A14A))
                                            } else {
                                                TooltipItem(projectionName, amount, WebPositive)
                                            },
                                        )
                                    }
                                },
                            )
                        },
                    )
                }
            }
        }

        // Contributions and withdrawals, on every calculator but the manual one
        // — and always, empty or not: hiding it hid the only way in to log the
        // first one. The web's `movements` section, empty line and all.
        if (detail.calculator != "manual") {
            item {
                // `mb-4 rounded-xl border p-5`: a panel, not a glass card.
                PanelCard(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    // `mb-3 flex items-center justify-between`: heading and
                    // buttons shrink together, so a long heading wraps.
                    FlexShrinkRow(
                        gap = 0.dp,
                        spaceBetween = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    ) {
                        Text(
                            stringResource(R.string.investments_movements),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.fg,
                        )
                        FlexShrinkRow(gap = 8.dp) {
                            GhostButton(
                                stringResource(R.string.investments_deposit),
                                onClick = { onAddMovement("deposit") },
                                leadingIcon = Lucide.ArrowDownLeft,
                                tint = colors.accent,
                            )
                            GhostButton(
                                stringResource(R.string.investments_withdrawal),
                                onClick = { onAddMovement("withdrawal") },
                                leadingIcon = Lucide.ArrowUpRight,
                                tint = colors.danger,
                            )
                        }
                    }
                    if (detail.movements.isEmpty()) {
                        Text(
                            stringResource(R.string.investments_movements_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.fgSubtle,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    } else {
                        detail.movements.forEachIndexed { index, movement ->
                            if (index > 0) HairLine()
                            MovementRow(movement, detail.currencyCode, hide, onEditMovement, onDeleteMovement)
                        }
                    }
                }
            }
        }

        // Snapshots are the manual calculator's whole story, exactly as on the
        // web: a panel with its heading, an add button and one row per value.
        if (detail.calculator == "manual") {
            item {
                PanelCard(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.investments_snapshots),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.fg,
                            modifier = Modifier.weight(1f),
                        )
                        GhostButton(
                            stringResource(R.string.investments_add_snapshot),
                            onClick = onAddSnapshot,
                            leadingIcon = Lucide.Plus,
                        )
                    }
                    detail.snapshots.forEachIndexed { index, snapshot ->
                        if (index > 0) HairLine()
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(snapshot.asOf, style = MaterialTheme.typography.bodyMedium, color = colors.fgMuted)
                            Text(
                                maskIfHidden(formatMoney(snapshot.valueCents, detail.currencyCode), hide),
                                style = MaterialTheme.typography.bodyMedium.tabular(),
                                color = colors.fg,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MovementRow(
    movement: InvestmentMovement,
    currencyCode: String,
    hide: Boolean,
    onEdit: (InvestmentMovement) -> Unit,
    onDelete: (InvestmentMovement) -> Unit,
) {
    val colors = Broke.colors
    val deposit = movement.kind == "deposit"
    val tint = if (deposit) colors.accent else colors.danger
    // `flex items-center gap-3 py-2 text-sm`: badge, noun, date, the amount
    // pushed to the end, then edit and delete — which a touch screen shows
    // always (`touch-action-reveal`), there being no hover to reveal them.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(28.dp).clip(CircleShape).background(colors.surfaceOverlay),
        ) {
            Icon(
                if (deposit) Lucide.ArrowDownLeft else Lucide.ArrowUpRight,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(13.dp),
            )
        }
        Text(
            stringResource(
                if (deposit) R.string.investments_deposit_noun
                else R.string.investments_withdrawal_noun,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.fg,
        )
        Text(
            movement.occurredAt,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.fgSubtle,
            modifier = Modifier.weight(1f),
        )
        Text(
            (if (deposit) "+" else "−") + maskIfHidden(formatMoney(movement.amountCents, currencyCode), hide),
            style = MaterialTheme.typography.bodyMedium.tabular(),
            color = tint,
        )
        Icon(
            Lucide.Pencil,
            contentDescription = stringResource(R.string.common_edit),
            tint = colors.fgSubtle,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onEdit(movement) }.padding(4.dp).size(14.dp),
        )
        Icon(
            Lucide.Trash,
            contentDescription = stringResource(R.string.common_delete),
            tint = colors.fgSubtle,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onDelete(movement) }.padding(4.dp).size(14.dp),
        )
    }
}

@Composable
private fun Line(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color? = null) {
    val colors = Broke.colors
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.fgMuted,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodyLarge, color = valueColor ?: colors.fg)
    }
}


/** One of the web's `rounded-xl border p-4` figures in the 2×2 grid. */
@Composable
private fun StatBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color? = null,
) {
    val colors = Broke.colors
    PanelCard(modifier, padding = 16.dp) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.fgSubtle)
        Text(
            value,
            // `mt-1 text-xl font-semibold`, the body face.
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 20.sp,
                lineHeight = 28.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            ).tabular(),
            color = valueColor ?: colors.fg,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** A quiet header action (edit / close / delete). */
@Composable
private fun DetailAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color? = null,
    onClick: () -> Unit,
) {
    val colors = Broke.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint ?: colors.fg, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
            color = tint ?: colors.fg,
        )
    }
}


/**
 * Y-axis labels the way the chart library writes them: five even steps over a
 * rounded range, abbreviated with "k" once the numbers get long.
 */
private fun axisTicks(lowCents: Long, highCents: Long): List<String> {
    val low = lowCents / 100.0
    val high = highCents / 100.0
    val step = niceStep((high - low) / 4.0)
    val base = Math.floor(low / step) * step
    return (4 downTo 0).map { i ->
        val v = base + step * i
        if (Math.abs(v) >= 1000) "%.1fk".format(v / 1000.0) else "%.0f".format(v)
    }
}

/** Rounds a raw step up to 1/2/2.5/5 × 10ⁿ. */
private fun niceStep(raw: Double): Double {
    if (raw <= 0) return 1.0
    val magnitude = Math.pow(10.0, Math.floor(Math.log10(raw)))
    val n = raw / magnitude
    val step = when {
        n <= 1.0 -> 1.0
        n <= 2.0 -> 2.0
        n <= 2.5 -> 2.5
        n <= 5.0 -> 5.0
        else -> 10.0
    }
    return step * magnitude
}

/** Cadences the what-if offers, in the web's order. */
private val SIM_CADENCES = listOf("monthly", "biweekly", "weekly", "none")

private fun simCadenceLabel(cadence: String): Int = when (cadence) {
    "biweekly" -> R.string.simulator_cadence_options_biweekly
    "weekly" -> R.string.simulator_cadence_options_weekly
    "none" -> R.string.simulator_cadence_options_none
    else -> R.string.simulator_cadence_options_monthly
}

/**
 * One figure of the what-if, the web's `MiniStat`: a bordered tile with a small
 * caption over a coloured amount. Not `GlassCard` — the web uses a plain
 * `bg-surface` box here, one step flatter than the cards around it.
 */
@Composable
private fun MiniStat(label: String, value: String, color: Color) {
    val colors = Broke.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(1.dp, colors.borderMuted, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
            color = colors.fgSubtle,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 18.sp, lineHeight = 28.sp),
            color = color,
        )
    }
}

/**
 * "0.75 BTC" for a crypto holding, or null for anything else. The quantity is
 * stored as e8 units in the calculator's own params — read, not computed.
 */
private fun cryptoHolding(detail: InvestmentDetail): String? {
    if (detail.calculator != "crypto") return null
    return runCatching {
        val params = rpcJson.parseToJsonElement(detail.paramsJson).jsonObject
        val e8 = params["quantity_e8"]!!.jsonPrimitive.long
        val symbol = params["symbol"]!!.jsonPrimitive.content
        // Trailing zeros off, as JS's Number#toString gives it.
        val amount = java.math.BigDecimal(e8).movePointLeft(8).stripTrailingZeros()
        "${amount.toPlainString()} $symbol"
    }.getOrNull()
}

private val rpcJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
