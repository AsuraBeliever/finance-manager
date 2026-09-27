package com.asura.finanzas.ui.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import com.asura.finanzas.ui.maskIfHidden
import com.asura.finanzas.ui.formatMoney
import com.asura.finanzas.ui.text
import com.asura.finanzas.ui.components.ConfirmDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.asura.finanzas.R
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.SavingsGoal
import com.asura.finanzas.data.Wallet
import com.asura.finanzas.ui.LocalAppSettings
import com.asura.finanzas.ui.theme.Broke
import kotlinx.coroutines.launch

/**
 * Goals whose apartado lives in this wallet, on the wallet detail page — the
 * web's `WalletGoalsSection`. Same card and the same actions as the goals page;
 * the phone used to show a one-line summary instead, with nothing to tap.
 */
@Composable
fun WalletGoalsSection(
    repository: BrokeRepository,
    walletId: Long,
    goals: List<SavingsGoal>,
    wallets: List<Wallet>,
    onChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val linked = goals.filter { it.linkedWalletId == walletId }
    if (linked.isEmpty()) return

    val scope = rememberCoroutineScope()
    val hide = LocalAppSettings.current.hideBalances
    val walletName = { id: Long? -> wallets.firstOrNull { it.id == id }?.name }

    var editing by remember { mutableStateOf<SavingsGoal?>(null) }
    var contributing by remember { mutableStateOf<SavingsGoal?>(null) }
    // The web asks before each of these, as the goals page does.
    var confirmDelete by remember { mutableStateOf<SavingsGoal?>(null) }
    var confirmUse by remember { mutableStateOf<SavingsGoal?>(null) }
    var converting by remember { mutableStateOf<SavingsGoal?>(null) }

    // `mb-6`: an `h3 mb-3 font-medium`, then the cards `gap-4`.
    Column(modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.goals_wallet_section_title),
            style = MaterialTheme.typography.titleMedium,
            color = Broke.colors.fg,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        linked.forEachIndexed { index, goal ->
            if (index > 0) Spacer(Modifier.height(16.dp))
            GoalCard(
                goal = goal,
                hide = hide,
                walletName = walletName,
                onContribute = { contributing = it },
                onUse = { target -> if (target.goalKind == "fund") converting = target else confirmUse = target },
                onAdjustDate = { editing = it },
                onEdit = { editing = it },
                onDelete = { confirmDelete = it },
            )
        }
    }

    editing?.let { goal ->
        GoalFormSheet(
            repository = repository,
            existing = goal,
            onDismiss = { editing = null },
            onSaved = { editing = null; onChanged() },
        )
    }
    contributing?.let { goal ->
        ContributeSheet(
            repository = repository,
            goal = goal,
            onDismiss = { contributing = null },
            onSaved = { contributing = null; onChanged() },
        )
    }
    confirmDelete?.let { target ->
        ConfirmDialog(
            title = stringResource(R.string.common_delete),
            message = stringResource(R.string.goals_delete_confirm),
            onConfirm = {
                confirmDelete = null
                scope.launch {
                    runCatching { repository.deleteGoal(target.id) }
                    onChanged()
                }
            },
            onDismiss = { confirmDelete = null },
        )
    }
    confirmUse?.let { target ->
        ConfirmDialog(
            title = stringResource(R.string.goals_buy),
            message = if (target.linkedWalletId != null) {
                text(
                    R.string.goals_use_confirm_apartado,
                    "amount" to maskIfHidden(formatMoney(target.savedCents, target.currencyCode), hide),
                    "wallet" to walletName(target.linkedWalletId).orEmpty(),
                )
            } else {
                stringResource(R.string.goals_use_confirm_track)
            },
            confirmLabel = stringResource(R.string.goals_buy),
            onConfirm = {
                confirmUse = null
                scope.launch {
                    runCatching { repository.useGoal(target.id) }
                    onChanged()
                }
            },
            onDismiss = { confirmUse = null },
        )
    }
    converting?.let { target ->
        com.asura.finanzas.ui.wallets.WalletFormSheet(
            repository = repository,
            existing = null,
            wallets = wallets,
            onDismiss = { converting = null },
            onSaved = { converting = null; onChanged() },
            convert = goalConvert(target, wallets),
        )
    }
}
