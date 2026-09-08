package com.asura.finanzas.ui.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.goals_wallet_section_title),
            style = MaterialTheme.typography.titleMedium,
            color = Broke.colors.fg,
        )
        linked.forEach { goal ->
            GoalCard(
                goal = goal,
                hide = hide,
                onLongPress = {},
                walletName = walletName,
                onContribute = { contributing = it },
                onUse = { target ->
                    scope.launch {
                        runCatching {
                            if (target.goalKind == "fund") {
                                repository.convertGoalToWallet(target.id, null, target.color, null)
                            } else {
                                repository.useGoal(target.id)
                            }
                        }
                        onChanged()
                    }
                },
                onAdjustDate = { editing = it },
                onEdit = { editing = it },
                onDelete = { target ->
                    scope.launch {
                        runCatching { repository.deleteGoal(target.id) }
                        onChanged()
                    }
                },
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
}
