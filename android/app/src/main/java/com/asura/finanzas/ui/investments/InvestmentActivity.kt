package com.asura.finanzas.ui.investments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asura.finanzas.R
import com.asura.finanzas.data.ActivityRow
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.InvestmentActivity
import com.asura.finanzas.data.InvestmentMovement
import com.asura.finanzas.ui.LocalAppSettings
import com.asura.finanzas.ui.components.FlexShrinkRow
import com.asura.finanzas.ui.components.GhostButton
import com.asura.finanzas.ui.components.HairLine
import com.asura.finanzas.ui.components.Lucide
import com.asura.finanzas.ui.components.OutlineButton
import com.asura.finanzas.ui.components.PanelCard
import com.asura.finanzas.ui.components.Period
import com.asura.finanzas.ui.components.PeriodPicker
import com.asura.finanzas.ui.formatMoney
import com.asura.finanzas.ui.maskIfHidden
import com.asura.finanzas.ui.text
import com.asura.finanzas.ui.theme.Broke
import com.asura.finanzas.ui.theme.tabular

/** Rows per page; «Mostrar más» asks for this many more (web: PAGE). */
private const val PAGE = 100

/**
 * The investment's deposits, withdrawals and daily yield in one table
 * (FIN-12) — the web's `InvestmentActivity`: date window and «Rendimientos»
 * switch as in Transacciones, the filter's totals on top, every row below.
 * Rows and totals come from the worker; this only lays them out.
 */
@Composable
fun InvestmentActivitySection(
    repository: BrokeRepository,
    investmentId: Long,
    /** Bumped after every write on the detail page, so the table reads again. */
    reloadKey: Int,
    /** Deposits and withdrawals can be logged (every calculator but manual). */
    canMove: Boolean,
    onAddMovement: (String) -> Unit,
    onEditMovement: (InvestmentMovement) -> Unit,
    onDeleteMovement: (InvestmentMovement) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Broke.colors
    val hide = LocalAppSettings.current.hideBalances
    var period by remember(investmentId) { mutableStateOf<Period>(Period.AllTime) }
    var showYield by remember(investmentId) { mutableStateOf(true) }
    // Pages belong to the filter they were raised under (as in Transacciones).
    var pages by remember(investmentId, period, showYield) { mutableStateOf(1) }
    val limit = pages * PAGE
    // Each answer remembers the filter it is for. A longer page of the same
    // filter keeps the rows up while it loads (web: keepPreviousData); another
    // filter shows nothing until its own rows arrive, never the old figures
    // under the new label.
    val filterKey = listOf(period, showYield)
    var activity by remember(investmentId) { mutableStateOf<Pair<Any, InvestmentActivity>?>(null) }
    var loadedFor by remember(investmentId) { mutableStateOf<Any?>(null) }
    var failed by remember(investmentId) { mutableStateOf<String?>(null) }
    val requestKey = listOf(period, showYield, limit, reloadKey)
    val genericError = stringResource(R.string.common_error)

    LaunchedEffect(investmentId, requestKey) {
        try {
            runCatching {
                repository.investmentActivity(
                    investmentId,
                    period.takeIf { it != Period.AllTime }?.toJson(),
                    showYield,
                    limit,
                )
            }.fold(
                onSuccess = { activity = filterKey to it; failed = null },
                onFailure = { failed = it.message ?: genericError },
            )
        } finally {
            loadedFor = requestKey
        }
    }
    val loadingMore = pages > 1 && loadedFor != requestKey

    PanelCard(modifier) {
        FlexShrinkRow(
            gap = 0.dp,
            spaceBetween = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        ) {
            Text(
                stringResource(R.string.investments_activity_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.fg,
            )
            if (canMove) {
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
        }
        if (activity?.second?.observed == true) {
            Text(
                stringResource(R.string.investments_yield_observed_hint),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                color = colors.fgSubtle,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        FlexShrinkRow(gap = 8.dp, modifier = Modifier.padding(bottom = 12.dp)) {
            PeriodPicker(value = period, onChange = { period = it }, allowAll = true)
            OutlineButton(
                text = stringResource(R.string.transactions_yield_toggle),
                onClick = { showYield = !showYield },
                leadingIcon = if (showYield) Lucide.Check else Lucide.TrendingUp,
                active = showYield,
            )
        }

        // The web's `text-sm text-danger` line when the read failed.
        failed?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.danger)
            return@PanelCard
        }
        val a = activity?.takeIf { it.first == filterKey }?.second ?: return@PanelCard
        val currency = a.currencyCode
        fun signed(cents: Long, negative: Boolean) =
            (if (negative) "−" else "+") + maskIfHidden(formatMoney(kotlin.math.abs(cents), currency), hide)

        if (a.count > 0) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                if (showYield) {
                    Text(
                        stringResource(R.string.investments_total_yield).uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                        color = colors.fgSubtle,
                    )
                    Text(
                        signed(a.yieldCents, a.yieldCents < 0),
                        style = MaterialTheme.typography.headlineSmall.tabular(),
                        color = if (a.yieldCents < 0) colors.danger else colors.accent,
                    )
                }
                Text(
                    stringResource(R.string.investments_total_deposited) + " " +
                        maskIfHidden(formatMoney(a.depositCents, currency), hide) + " · " +
                        stringResource(R.string.investments_total_withdrawn) + " " +
                        maskIfHidden(formatMoney(a.withdrawalCents, currency), hide),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp).tabular(),
                    color = colors.fgSubtle,
                )
                Text(
                    if (a.count == 1L) stringResource(R.string.dashboard_movement_one)
                    else text(R.string.dashboard_movements_count, "n" to a.count),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                    color = colors.fgSubtle,
                )
            }
        }

        if (a.rows.isEmpty()) {
            Text(
                stringResource(
                    when {
                        period != Period.AllTime || !showYield -> R.string.investments_activity_empty
                        canMove -> R.string.investments_movements_empty
                        else -> R.string.investments_yield_empty
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.fgSubtle,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            return@PanelCard
        }
        a.rows.forEachIndexed { i, row ->
            if (i > 0) HairLine()
            ActivityLine(
                row = row,
                investmentId = investmentId,
                signed = ::signed,
                onEdit = onEditMovement,
                onDelete = onDeleteMovement,
            )
        }
        if (a.hasMore || loadingMore) {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                GhostButton(
                    stringResource(
                        if (loadingMore) R.string.common_loading else R.string.transactions_show_more,
                    ),
                    onClick = { pages += 1 },
                    enabled = !loadingMore,
                )
            }
        }
    }
}

/** One row: badge, noun, date, signed amount, and edit/delete on movements. */
@Composable
private fun ActivityLine(
    row: ActivityRow,
    investmentId: Long,
    signed: (Long, Boolean) -> String,
    onEdit: (InvestmentMovement) -> Unit,
    onDelete: (InvestmentMovement) -> Unit,
) {
    val colors = Broke.colors
    val isYield = row.kind == "yield"
    val negative = row.kind == "withdrawal" || row.amountCents < 0
    val tint = if (negative) colors.danger else colors.accent
    val movement = row.movementId?.let {
        InvestmentMovement(it, investmentId, row.kind, row.amountCents, row.occurredAt)
    }
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
                when {
                    isYield -> Lucide.TrendingUp
                    row.kind == "deposit" -> Lucide.ArrowDownLeft
                    else -> Lucide.ArrowUpRight
                },
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(13.dp),
            )
        }
        Text(
            stringResource(
                when {
                    isYield -> R.string.transactions_yield_row
                    row.kind == "deposit" -> R.string.investments_deposit_noun
                    else -> R.string.investments_withdrawal_noun
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.fg,
        )
        Text(
            row.occurredAt,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.fgSubtle,
            modifier = Modifier.weight(1f),
        )
        Text(
            signed(row.amountCents, negative),
            style = MaterialTheme.typography.bodyMedium.tabular(),
            color = tint,
        )
        // Fixed slot, so amounts line up with or without buttons.
        Row(
            modifier = Modifier.width(52.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.End),
        ) {
            if (movement != null) {
                Icon(
                    Lucide.Pencil,
                    contentDescription = stringResource(R.string.common_edit),
                    tint = colors.fgSubtle,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onEdit(movement) }
                        .padding(4.dp).size(14.dp),
                )
                Icon(
                    Lucide.Trash,
                    contentDescription = stringResource(R.string.common_delete),
                    tint = colors.fgSubtle,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onDelete(movement) }
                        .padding(4.dp).size(14.dp),
                )
            }
        }
    }
}
