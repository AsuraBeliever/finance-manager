package com.asura.finanzas.ui.goals

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.asura.finanzas.R
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.SavingsGoal
import com.asura.finanzas.ui.LocalAppSettings
import com.asura.finanzas.ui.components.BackHeader
import com.asura.finanzas.ui.components.Dot
import com.asura.finanzas.ui.components.EmptyState
import com.asura.finanzas.ui.components.ErrorBox
import com.asura.finanzas.ui.components.GlassCard
import com.asura.finanzas.ui.components.Load
import com.asura.finanzas.ui.components.LoadingBox
import com.asura.finanzas.ui.components.OfflineNotice
import com.asura.finanzas.ui.components.DialogAction
import com.asura.finanzas.ui.components.PrimaryButton
import com.asura.finanzas.ui.components.ProgressBar
import com.asura.finanzas.ui.components.formatBps
import com.asura.finanzas.ui.components.loadSynced
import com.asura.finanzas.ui.components.rememberReloadKey
import com.asura.finanzas.ui.formatMoney
import com.asura.finanzas.ui.maskIfHidden
import com.asura.finanzas.ui.parseHexColor
import com.asura.finanzas.ui.theme.Broke
import kotlinx.coroutines.launch

@Composable
fun GoalsScreen(
    repository: BrokeRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (key, reload) = rememberReloadKey()
    val state by loadSynced(key) { repository.savingsGoals() }
    val scope = rememberCoroutineScope()

    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SavingsGoal?>(null) }
    var contributing by remember { mutableStateOf<SavingsGoal?>(null) }
    var actionsFor by remember { mutableStateOf<SavingsGoal?>(null) }
    var confirmDelete by remember { mutableStateOf<SavingsGoal?>(null) }

    when (val current = state) {
        is Load.Loading -> LoadingBox(modifier)
        is Load.Failed -> ErrorBox(current.message, reload, modifier)
        is Load.Ready -> GoalList(
            goals = current.data,
            fromCache = current.fromCache,
            onBack = onBack,
            onNew = { creating = true },
            onLongPress = { actionsFor = it },
            modifier = modifier,
        )
    }

    if (creating || editing != null) {
        GoalFormSheet(
            repository = repository,
            existing = editing,
            onDismiss = { creating = false; editing = null },
            onSaved = { creating = false; editing = null; reload() },
        )
    }

    contributing?.let { goal ->
        ContributeSheet(
            repository = repository,
            goal = goal,
            onDismiss = { contributing = null },
            onSaved = { contributing = null; reload() },
        )
    }

    actionsFor?.let { target ->
        AlertDialog(
            onDismissRequest = { actionsFor = null },
            containerColor = Broke.colors.surfaceOverlay,
            title = { Text(target.name, color = Broke.colors.fg) },
            text = {
                Column {
                    DialogAction(stringResource(R.string.goals_contribute)) {
                        actionsFor = null
                        contributing = target
                    }
                    DialogAction(stringResource(R.string.common_edit)) {
                        actionsFor = null
                        editing = target
                    }
                    DialogAction(stringResource(R.string.common_delete), Broke.colors.danger) {
                        actionsFor = null
                        confirmDelete = target
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { actionsFor = null }) {
                    Text(stringResource(R.string.common_close), color = Broke.colors.fgMuted)
                }
            },
        )
    }

    confirmDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = Broke.colors.surfaceOverlay,
            title = { Text(target.name) },
            text = { Text(stringResource(R.string.goals_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    scope.launch {
                        runCatching { repository.deleteGoal(target.id) }
                        reload()
                    }
                }) { Text(stringResource(R.string.common_delete), color = Broke.colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun GoalList(
    goals: List<SavingsGoal>,
    fromCache: Boolean,
    onBack: () -> Unit,
    onNew: () -> Unit,
    onLongPress: (SavingsGoal) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hide = LocalAppSettings.current.hideBalances

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            BackHeader(stringResource(R.string.goals_title), onBack)
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                text = stringResource(R.string.goals_new_goal),
                onClick = onNew,
                leadingIcon = Icons.Outlined.Add,
            )
        }

        if (fromCache) {
            item { OfflineNotice(stringResource(R.string.offline_banner), Modifier.fillMaxWidth()) }
        }

        if (goals.isEmpty()) {
            item {
                EmptyState(
                    stringResource(R.string.goals_empty_title),
                    stringResource(R.string.goals_empty_description),
                )
            }
        }

        items(goals, key = { it.id }) { goal -> GoalCard(goal, hide, onLongPress) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GoalCard(goal: SavingsGoal, hide: Boolean, onLongPress: (SavingsGoal) -> Unit) {
    val colors = Broke.colors
    GlassCard(
        Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = { onLongPress(goal) }),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Dot(parseHexColor(goal.color) ?: colors.accent, 12.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(goal.name, style = MaterialTheme.typography.titleMedium, color = colors.fg)
                goal.targetDate?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (goal.isBehind) colors.danger else colors.fgSubtle,
                    )
                }
            }
            Text(
                formatBps(goal.progressBps),
                style = MaterialTheme.typography.labelLarge,
                color = colors.fgMuted,
            )
        }

        Spacer(Modifier.height(12.dp))
        ProgressBar(goal.progressBps)
        Spacer(Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                maskIfHidden(formatMoney(goal.savedCents, goal.currencyCode), hide),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.fg,
            )
            Text(
                "  /  " + maskIfHidden(formatMoney(goal.targetCents, goal.currencyCode), hide),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.fgSubtle,
            )
        }
    }
}
