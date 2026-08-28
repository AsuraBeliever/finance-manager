package com.asura.finanzas.ui.wallets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asura.finanzas.R
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.CreditCardSummary
import com.asura.finanzas.data.MsiPlan
import com.asura.finanzas.data.Transaction
import com.asura.finanzas.data.Wallet
import com.asura.finanzas.ui.LocalAppSettings
import com.asura.finanzas.ui.components.BackHeader
import com.asura.finanzas.ui.components.Dot
import com.asura.finanzas.ui.components.GlassCard
import com.asura.finanzas.ui.components.HairLine
import com.asura.finanzas.ui.components.LoadingBox
import com.asura.finanzas.ui.components.MicroLabel
import com.asura.finanzas.ui.components.ProgressBar
import com.asura.finanzas.ui.components.formatBps
import com.asura.finanzas.ui.formatMoney
import com.asura.finanzas.ui.maskIfHidden
import com.asura.finanzas.ui.parseHexColor
import com.asura.finanzas.ui.text
import com.asura.finanzas.ui.theme.Broke
import kotlinx.coroutines.launch

/**
 * One wallet: what it holds, its credit-card panel when it is a card, and its
 * movements. Every figure here — debt, statement balance, utilisation, what an
 * MSI plan still owes — is computed by finanzas-core and read as-is.
 */
@Composable
fun WalletDetailScreen(
    repository: BrokeRepository,
    walletId: Long,
    onBack: () -> Unit,
    onEdit: (Wallet) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Broke.colors
    val hide = LocalAppSettings.current.hideBalances
    val scope = rememberCoroutineScope()

    var wallet by remember { mutableStateOf<Wallet?>(null) }
    var credit by remember { mutableStateOf<CreditCardSummary?>(null) }
    var movements by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(walletId, reloadKey) {
        wallet = runCatching { repository.wallet(walletId) }.getOrNull()
        movements = runCatching { repository.transactions(walletId = walletId).value }
            .getOrDefault(emptyList())
        credit = wallet?.takeIf { it.creditCutDay != null }
            ?.let { runCatching { repository.creditCardSummary(walletId) }.getOrNull() }
    }

    val current = wallet
    if (current == null) {
        LoadingBox(modifier)
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            BackHeader(current.name, onBack)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Action(Icons.Outlined.Edit, stringResource(R.string.common_edit)) { onEdit(current) }
                Action(
                    Icons.Outlined.Archive,
                    stringResource(
                        if (current.isArchived) R.string.wallets_unarchive
                        else R.string.wallets_archive,
                    ),
                ) {
                    scope.launch {
                        runCatching { repository.archiveWallet(current.id, !current.isArchived) }
                        reloadKey++
                    }
                }
                Action(
                    Icons.Outlined.Delete,
                    stringResource(R.string.common_delete),
                    colors.danger,
                ) {
                    scope.launch {
                        runCatching { repository.deleteWallet(current.id) }
                        onBack()
                    }
                }
            }
        }

        item { BalanceCard(current, credit, hide) }

        credit?.let { summary ->
            item {
                CreditPanel(
                    summary = summary,
                    hide = hide,
                    onDeletePlan = { plan ->
                        scope.launch {
                            runCatching { repository.deleteMsiPlan(plan.id) }
                            reloadKey++
                        }
                    },
                )
            }
        }

        if (movements.isNotEmpty()) {
            item { MicroLabel(stringResource(R.string.transactions_title)) }
            items(movements, key = { it.id }) { tx -> MovementRow(tx, hide) }
        }
    }
}

@Composable
private fun Action(
    icon: ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color? = null,
    onClick: () -> Unit,
) {
    val colors = Broke.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint ?: colors.fgMuted, modifier = Modifier.width(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = tint ?: colors.fg)
    }
}

@Composable
private fun BalanceCard(wallet: Wallet, credit: CreditCardSummary?, hide: Boolean) {
    val colors = Broke.colors
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(parseHexColor(wallet.color) ?: colors.fgSubtle)
            Spacer(Modifier.width(10.dp))
            Text(
                "${wallet.categoryName} · ${wallet.currencyCode}",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.fgMuted,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            // For a card the web leads with available credit, not the negative
            // balance, so the debt is only stated once — in its own panel.
            maskIfHidden(
                formatMoney(
                    credit?.availableCreditCents ?: wallet.balanceCents,
                    wallet.currencyCode,
                ),
                hide,
            ),
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 34.sp),
            color = colors.fg,
        )
        credit?.creditLimitCents?.let { limit ->
            Text(
                text(
                    R.string.wallets_available,
                ) + " " + maskIfHidden(formatMoney(limit, wallet.currencyCode), hide),
                style = MaterialTheme.typography.labelSmall,
                color = colors.fgSubtle,
            )
        }
        if (credit == null && wallet.reservedCents > 0) {
            Text(
                "${stringResource(R.string.wallets_reserved)} " +
                    maskIfHidden(formatMoney(wallet.reservedCents, wallet.currencyCode), hide),
                style = MaterialTheme.typography.labelSmall,
                color = colors.fgSubtle,
            )
        }
    }
}

@Composable
private fun CreditPanel(
    summary: CreditCardSummary,
    hide: Boolean,
    onDeletePlan: (MsiPlan) -> Unit,
) {
    val colors = Broke.colors

    GlassCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(R.string.credit_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.fg,
            )
            Text(
                "${stringResource(R.string.credit_next_cut)}: ${summary.nextCutDate}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.fgSubtle,
            )
        }

        Spacer(Modifier.height(14.dp))
        MicroLabel(stringResource(R.string.credit_debt))
        Text(
            maskIfHidden(formatMoney(summary.debtCents), hide),
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 30.sp),
            color = colors.fg,
        )
        if (summary.pendingMsiCents > 0) {
            Text(
                "${stringResource(R.string.credit_msi_pending_total)}: " +
                    maskIfHidden(formatMoney(summary.pendingMsiCents), hide),
                style = MaterialTheme.typography.labelSmall,
                color = colors.fgSubtle,
            )
        }

        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surfaceOverlay)
                .padding(14.dp),
        ) {
            Column {
                Text(
                    "${stringResource(R.string.credit_statement)} " +
                        "(${summary.statement.cutDate}): " +
                        maskIfHidden(formatMoney(summary.statement.balanceCents), hide),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.fgMuted,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (summary.statement.remainingCents <= 0) {
                        stringResource(R.string.credit_no_statement_debt)
                    } else {
                        "${stringResource(R.string.credit_pay_by)} ${summary.statement.dueDate}: " +
                            maskIfHidden(formatMoney(summary.statement.remainingCents), hide)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.fg,
                )
            }
        }

        summary.utilizationBps?.let { bps ->
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MicroLabel(stringResource(R.string.credit_utilization))
                Text(
                    formatBps(bps),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.fgSubtle,
                )
            }
            Spacer(Modifier.height(6.dp))
            ProgressBar(bps, over = bps > 8000)
        }

        if (summary.msiPlans.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            HairLine()
            Spacer(Modifier.height(12.dp))
            MicroLabel(stringResource(R.string.credit_msi_title))
            summary.msiPlans.forEach { plan ->
                Spacer(Modifier.height(10.dp))
                MsiRow(plan, hide) { onDeletePlan(plan) }
            }
        }
    }
}

@Composable
private fun MsiRow(plan: MsiPlan, hide: Boolean, onDelete: () -> Unit) {
    val colors = Broke.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceOverlay)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                plan.description,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.fg,
                modifier = Modifier.weight(1f),
            )
            Text(
                maskIfHidden(formatMoney(plan.monthlyCents), hide) + "/mo",
                style = MaterialTheme.typography.labelLarge,
                color = colors.fg,
            )
            Spacer(Modifier.width(10.dp))
            Icon(
                Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.common_delete),
                tint = colors.fgSubtle,
                modifier = Modifier.width(20.dp).clickable(onClick = onDelete),
            )
        }
        Spacer(Modifier.height(8.dp))
        // Progress is billed months over total months — both the server's.
        ProgressBar(
            progressBps = if (plan.months > 0) {
                (plan.billedMonths.toLong() * 10_000L) / plan.months
            } else {
                0L
            },
        )
        plan.nextChargeDate?.let { date ->
            Spacer(Modifier.height(6.dp))
            Text(
                text(
                    R.string.credit_msi_next_charge,
                    "amount" to maskIfHidden(formatMoney(plan.nextChargeCents ?: 0), hide),
                    "date" to date,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = colors.fgSubtle,
            )
        }
    }
}

@Composable
private fun MovementRow(tx: Transaction, hide: Boolean) {
    val colors = Broke.colors
    val incoming = tx.kind == "income" || tx.kind == "transfer_in"
    GlassCard(Modifier.fillMaxWidth(), padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(
                    // Fall back to the kind so a bare transfer is never a blank row.
                    tx.description?.takeIf { it.isNotBlank() }
                        ?: tx.categoryName
                        ?: kindLabel(tx.kind),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.fg,
                )
                Text(
                    tx.occurredAt.take(10),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.fgSubtle,
                )
            }
            Text(
                (if (incoming) "+" else "−") + maskIfHidden(formatMoney(tx.amountCents), hide),
                style = MaterialTheme.typography.labelLarge,
                color = if (incoming) colors.accent else colors.danger,
            )
        }
    }
}

@Composable
private fun kindLabel(kind: String): String = stringResource(
    when (kind) {
        "income" -> R.string.transactions_income
        "expense" -> R.string.transactions_expense
        else -> R.string.transactions_transfer
    },
)
