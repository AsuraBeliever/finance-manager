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
import com.asura.finanzas.data.InvestmentProjection
import com.asura.finanzas.ui.components.OutlineButton
import com.asura.finanzas.R
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.InvestmentDetail
import com.asura.finanzas.data.InvestmentMovement
import com.asura.finanzas.ui.LocalAppSettings
import com.asura.finanzas.ui.components.DialogAction
import com.asura.finanzas.ui.components.GlassCard
import com.asura.finanzas.ui.components.HairLine
import com.asura.finanzas.ui.components.HeroAmount
import com.asura.finanzas.ui.components.LoadingBox
import com.asura.finanzas.ui.components.LineChart
import com.asura.finanzas.ui.components.MicroLabel
import com.asura.finanzas.ui.components.PrimaryButton
import com.asura.finanzas.ui.formatDelta
import com.asura.finanzas.ui.formatMoney
import com.asura.finanzas.ui.maskIfHidden
import com.asura.finanzas.ui.theme.Broke
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
    var addingMovement by remember { mutableStateOf(false) }
    var addingSnapshot by remember { mutableStateOf(false) }
    var simulating by remember { mutableStateOf(false) }
    var actions by remember { mutableStateOf(false) }
    var movementActions by remember { mutableStateOf<InvestmentMovement?>(null) }
    var editingMovement by remember { mutableStateOf<InvestmentMovement?>(null) }
    var editingInvestment by remember { mutableStateOf(false) }
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
        onAddMovement = { addingMovement = true },
        onAddSnapshot = { addingSnapshot = true },
        onSimulate = { simulating = true },
        onEdit = { editingInvestment = true },
        onClose = {
            scope.launch {
                runCatching { repository.closeInvestment(investmentId, closed = true) }
                reloadKey++
            }
        },
        onDelete = {
            scope.launch {
                runCatching { repository.deleteInvestment(investmentId) }
                onBack()
            }
        },
        onMovementLongPress = { movementActions = it },
        modifier = modifier,
    )

    if (simulating) {
        SimulatorScreen(repository = repository, onBack = { simulating = false })
        return
    }

    if (addingMovement) {
        InvestmentMovementSheet(
            repository = repository,
            investment = current,
            onDismiss = { addingMovement = false },
            onSaved = { addingMovement = false; reloadKey++ },
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

    // Long press on a movement offers the same edit/delete the web keeps behind
    // the row's own controls.
    movementActions?.let { movement ->
        AlertDialog(
            onDismissRequest = { movementActions = null },
            containerColor = Broke.colors.surfaceOverlay,
            title = { Text(movement.occurredAt, color = Broke.colors.fg) },
            text = {
                Column {
                    DialogAction(stringResource(R.string.common_edit)) {
                        movementActions = null
                        editingMovement = movement
                    }
                    DialogAction(stringResource(R.string.common_delete), Broke.colors.danger) {
                        movementActions = null
                        scope.launch {
                            runCatching { repository.deleteInvestmentMovement(movement.id) }
                            reloadKey++
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { movementActions = null }) {
                    Text(stringResource(R.string.common_close), color = Broke.colors.fgMuted)
                }
            },
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

    if (actions) {
        AlertDialog(
            onDismissRequest = { actions = false },
            containerColor = Broke.colors.surfaceOverlay,
            title = { Text(current.name, color = Broke.colors.fg) },
            text = {
                Column {
                    DialogAction(stringResource(R.string.common_edit)) {
                        actions = false
                        editingInvestment = true
                    }
                    DialogAction(stringResource(R.string.investments_add_snapshot)) {
                        actions = false
                        addingSnapshot = true
                    }
                    if (!current.isClosed) {
                        DialogAction(stringResource(R.string.investments_close)) {
                            actions = false
                            scope.launch {
                                runCatching { repository.closeInvestment(current.id, !current.isClosed) }
                                reloadKey++
                            }
                        }
                    }
                    DialogAction(stringResource(R.string.common_delete), Broke.colors.danger) {
                        actions = false
                        scope.launch {
                            runCatching { repository.deleteInvestment(current.id) }
                            onBack()
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { actions = false }) {
                    Text(stringResource(R.string.common_close), color = Broke.colors.fgMuted)
                }
            },
        )
    }
}

@Composable
private fun DetailContent(
    repository: BrokeRepository,
    detail: InvestmentDetail,
    onBack: () -> Unit,
    onAddMovement: () -> Unit,
    onAddSnapshot: () -> Unit,
    onSimulate: () -> Unit,
    onEdit: () -> Unit,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    onMovementLongPress: (InvestmentMovement) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Broke.colors
    val hide = LocalAppSettings.current.hideBalances
    // Horizon in years, as the web's zoom control sets it.
    var years by remember { mutableStateOf(5) }
    var projection by remember { mutableStateOf<InvestmentProjection?>(null) }
    LaunchedEffect(detail.id, years) {
        projection = runCatching { repository.projectInvestment(detail.id, years * 12) }.getOrNull()
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            // This page has no back link in the browser and no cyan rule
            // either: just the name as a plain heading with the three quiet
            // actions beside it. The system gesture is what goes back.
            BackHandler(onBack = onBack)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    detail.name,
                    // `text-2xl font-semibold` — the UI face, not the display one.
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 24.sp,
                        lineHeight = 30.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    ),
                    color = colors.fg,
                    modifier = Modifier.weight(1f, fill = false),
                )
                DetailAction(Lucide.Pencil, stringResource(R.string.common_edit)) { onEdit() }
                DetailAction(Lucide.Lock, stringResource(R.string.investments_close)) { onClose() }
                DetailAction(
                    Lucide.Trash,
                    stringResource(R.string.common_delete),
                    colors.danger,
                ) { onDelete() }
            }
        }

        // Four figures in a 2×2 grid, as on the web — not one hero card.
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatBox(
                    stringResource(R.string.investments_current_value),
                    maskIfHidden(formatMoney(detail.currentValueCents, detail.currencyCode), hide),
                    modifier = Modifier.weight(1f),
                )
                StatBox(
                    stringResource(R.string.investments_gain),
                    maskIfHidden(formatDelta(detail.gainCents, detail.currencyCode), hide),
                    valueColor = if (detail.gainCents >= 0) colors.positive else colors.danger,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatBox(
                    stringResource(R.string.investments_net_invested),
                    maskIfHidden(formatMoney(detail.netInvestedCents, detail.currencyCode), hide),
                    modifier = Modifier.weight(1f),
                )
                StatBox(
                    stringResource(R.string.investments_start_date),
                    detail.startDate,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        detail.maturityDate?.let { maturity ->
            item {
                StatBox(
                    stringResource(R.string.investments_maturity),
                    maturity,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                // The web lays the whole header out as one wrapping row: the
                // title with the rate beside it on the same baseline, then the
                // zoom and the what-if toggle together on the right.
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.weight(1f, fill = false),
                    ) {
                        Text(
                            stringResource(R.string.investments_projection),
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.fg,
                        )
                        projection?.annualRateBps?.let { bps ->
                            Text(
                                stringResource(R.string.investments_projection_at_rate)
                                    .replace("{rate}", "%.2f".format(bps / 100.0)),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                                color = colors.fgSubtle,
                            )
                        }
                    }
                    // Horizon stepper, the web's zoom control.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, colors.borderMuted, RoundedCornerShape(8.dp))
                            .padding(2.dp),
                    ) {
                        Icon(
                            Lucide.ZoomIn,
                            contentDescription = null,
                            tint = colors.fgMuted,
                            modifier = Modifier
                                .clickable(enabled = years > 1) { years -= 1 }
                                .padding(4.dp)
                                .size(15.dp),
                        )
                        Text(
                            "$years",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                            color = colors.fg,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                        Text(
                            stringResource(R.string.investments_projection_years_short),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                            color = colors.fgSubtle,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                        Icon(
                            Lucide.ZoomOut,
                            contentDescription = null,
                            tint = colors.fgMuted,
                            modifier = Modifier
                                .clickable(enabled = years < 50) { years += 1 }
                                .padding(4.dp)
                                .size(15.dp),
                        )
                    }
                    OutlineButton(
                        text = stringResource(R.string.investments_projection_sim_toggle),
                        onClick = onSimulate,
                        leadingIcon = Lucide.SlidersHorizontal,
                    )
                }

                val points = projection?.projection.orEmpty().ifEmpty { detail.projection }
                if (points.size >= 2) {
                    Spacer(Modifier.height(12.dp))
                    // Every point comes from finanzas-core; the chart only maps
                    // the given cents onto pixels.
                    val lo = points.minOf { it.valueCents }
                    val hi = points.maxOf { it.valueCents }
                    val today = java.time.LocalDate.now().toString()
                    LineChart(
                        values = points.map { it.valueCents },
                        startLabel = points.first().date,
                        endLabel = points.last().date,
                        minLabel = maskIfHidden(formatMoney(lo, detail.currencyCode), hide),
                        maxLabel = maskIfHidden(formatMoney(hi, detail.currencyCode), hide),
                        forecastFrom = points.indexOfLast { it.date <= today }.takeIf { it >= 0 },
                        ticks = if (hide) emptyList() else axisTicks(lo, hi),
                        dates = points.map { it.date },
                    )
                }
            }
        }

        if (detail.movements.isNotEmpty()) {
            item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MicroLabel(
                    stringResource(R.string.investments_movements),
                    Modifier.weight(1f),
                )
                DetailAction(Lucide.ArrowDownLeft, stringResource(R.string.investments_deposit)) {
                    onAddMovement()
                }
                Spacer(Modifier.width(12.dp))
                DetailAction(
                    Lucide.ArrowUpRight,
                    stringResource(R.string.investments_withdrawal),
                    colors.danger,
                ) { onAddMovement() }
            }
        }
            items(detail.movements, key = { it.id }) { movement ->
                MovementRow(movement, detail.currencyCode, hide, onMovementLongPress)
            }
        }

        if (detail.snapshots.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                MicroLabel(stringResource(R.string.investments_snapshots))
            }
            items(detail.snapshots, key = { "s-${it.id}" }) { snapshot ->
                GlassCard(Modifier.fillMaxWidth(), padding = 14.dp) {
                    Line(
                        snapshot.asOf,
                        maskIfHidden(formatMoney(snapshot.valueCents, detail.currencyCode), hide),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MovementRow(
    movement: InvestmentMovement,
    currencyCode: String,
    hide: Boolean,
    onLongPress: (InvestmentMovement) -> Unit,
) {
    val colors = Broke.colors
    val deposit = movement.kind == "deposit"
    GlassCard(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = { onLongPress(movement) }),
        padding = 14.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(
                        if (deposit) R.string.investments_deposit_noun
                        else R.string.investments_withdrawal_noun,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.fg,
                )
                Text(
                    movement.occurredAt,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.fgSubtle,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                (if (deposit) "+" else "−") +
                    maskIfHidden(formatMoney(movement.amountCents, currencyCode), hide),
                style = MaterialTheme.typography.labelLarge,
                color = if (deposit) colors.accent else colors.danger,
            )
        }
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


/** One of the boxed figures the web's detail lays out in a 2×2 grid. */
@Composable
private fun StatBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color? = null,
) {
    val colors = Broke.colors
    GlassCard(modifier, padding = 16.dp) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.8.sp),
            color = colors.fgMuted,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 20.sp, lineHeight = 26.sp),
            color = valueColor ?: colors.fg,
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
