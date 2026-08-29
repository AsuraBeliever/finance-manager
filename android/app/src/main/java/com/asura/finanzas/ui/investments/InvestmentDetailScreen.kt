package com.asura.finanzas.ui.investments

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
import com.asura.finanzas.R
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.InvestmentDetail
import com.asura.finanzas.data.InvestmentMovement
import com.asura.finanzas.ui.LocalAppSettings
import com.asura.finanzas.ui.components.BackHeader
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
        detail = current,
        onBack = onBack,
        onAddMovement = { addingMovement = true },
        onAddSnapshot = { addingSnapshot = true },
        onActions = { actions = true },
        onMovementLongPress = { movementActions = it },
        modifier = modifier,
    )

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
    detail: InvestmentDetail,
    onBack: () -> Unit,
    onAddMovement: () -> Unit,
    onAddSnapshot: () -> Unit,
    onActions: () -> Unit,
    onMovementLongPress: (InvestmentMovement) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Broke.colors
    val hide = LocalAppSettings.current.hideBalances

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            BackHeader(detail.name, onBack)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(
                    text = stringResource(R.string.investments_movements),
                    onClick = onAddMovement,
                    leadingIcon = Icons.Outlined.Add,
                )
                PrimaryButton(
                    text = stringResource(R.string.common_edit),
                    onClick = onActions,
                )
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                MicroLabel(stringResource(R.string.investments_current_value))
                Spacer(Modifier.height(4.dp))
                HeroAmount(
                    maskIfHidden(formatMoney(detail.currentValueCents, detail.currencyCode), hide),
                    fontSize = 34.sp,
                )
                Spacer(Modifier.height(14.dp))
                HairLine()
                Spacer(Modifier.height(12.dp))
                Line(
                    stringResource(R.string.investments_net_invested),
                    maskIfHidden(formatMoney(detail.netInvestedCents, detail.currencyCode), hide),
                )
                Spacer(Modifier.height(6.dp))
                Line(
                    stringResource(R.string.investments_gain),
                    maskIfHidden(formatDelta(detail.gainCents, detail.currencyCode), hide),
                    if (detail.gainCents >= 0) colors.positive else colors.danger,
                )
                detail.maturityDate?.let {
                    Spacer(Modifier.height(6.dp))
                    Line(stringResource(R.string.investments_maturity), it)
                }
            }
        }

        if (detail.projection.isNotEmpty()) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    MicroLabel(stringResource(R.string.investments_projection))
                    Spacer(Modifier.height(10.dp))
                    // Every point comes from finanzas-core; the chart only maps
                    // the given cents onto pixels.
                    val points = detail.projection
                    LineChart(
                        values = points.map { it.valueCents },
                        startLabel = points.first().date,
                        endLabel = points.last().date,
                        minLabel = maskIfHidden(
                            formatMoney(points.minOf { it.valueCents }, detail.currencyCode),
                            hide,
                        ),
                        maxLabel = maskIfHidden(
                            formatMoney(points.maxOf { it.valueCents }, detail.currencyCode),
                            hide,
                        ),
                    )
                    Spacer(Modifier.height(10.dp))
                    Line(
                        points.last().date,
                        maskIfHidden(
                            formatMoney(points.last().valueCents, detail.currencyCode),
                            hide,
                        ),
                    )
                }
            }
        }

        if (detail.movements.isNotEmpty()) {
            item { MicroLabel(stringResource(R.string.investments_movements)) }
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
