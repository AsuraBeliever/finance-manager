package com.asura.finanzas.ui.budgets

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
import com.asura.finanzas.data.Budget
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.ui.LocalAppSettings
import com.asura.finanzas.ui.components.BackHeader
import com.asura.finanzas.ui.components.Dot
import com.asura.finanzas.ui.components.EmptyState
import com.asura.finanzas.ui.components.ErrorBox
import com.asura.finanzas.ui.components.DialogAction
import com.asura.finanzas.ui.components.GlassCard
import com.asura.finanzas.ui.components.PrimaryButton
import com.asura.finanzas.ui.components.Load
import com.asura.finanzas.ui.components.LoadingBox
import com.asura.finanzas.ui.components.OfflineNotice
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
fun BudgetsScreen(
    repository: BrokeRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (key, reload) = rememberReloadKey()
    val state by loadSynced(key) { repository.budgets() }
    val scope = rememberCoroutineScope()

    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Budget?>(null) }
    var actionsFor by remember { mutableStateOf<Budget?>(null) }

    when (val current = state) {
        is Load.Loading -> LoadingBox(modifier)
        is Load.Failed -> ErrorBox(current.message, reload, modifier)
        is Load.Ready -> BudgetList(
            budgets = current.data,
            fromCache = current.fromCache,
            onBack = onBack,
            onNew = { creating = true },
            onLongPress = { actionsFor = it },
            modifier = modifier,
        )
    }

    if (creating || editing != null) {
        BudgetFormSheet(
            repository = repository,
            existing = editing,
            onDismiss = { creating = false; editing = null },
            onSaved = { creating = false; editing = null; reload() },
        )
    }

    actionsFor?.let { target ->
        AlertDialog(
            onDismissRequest = { actionsFor = null },
            containerColor = Broke.colors.surfaceOverlay,
            title = {
                Text(
                    target.categoryName ?: stringResource(R.string.budgets_overall),
                    color = Broke.colors.fg,
                )
            },
            text = {
                Column {
                    DialogAction(stringResource(R.string.common_edit)) {
                        actionsFor = null
                        editing = target
                    }
                    DialogAction(stringResource(R.string.common_delete), Broke.colors.danger) {
                        actionsFor = null
                        scope.launch {
                            runCatching { repository.deleteBudget(target.id) }
                            reload()
                        }
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
}

@Composable
private fun BudgetList(
    budgets: List<Budget>,
    fromCache: Boolean,
    onBack: () -> Unit,
    onNew: () -> Unit,
    onLongPress: (Budget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hide = LocalAppSettings.current.hideBalances

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            BackHeader(stringResource(R.string.budgets_title), onBack)
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                text = stringResource(R.string.budgets_new_budget),
                onClick = onNew,
                leadingIcon = Icons.Outlined.Add,
            )
        }

        if (fromCache) {
            item { OfflineNotice(stringResource(R.string.offline_banner), Modifier.fillMaxWidth()) }
        }

        if (budgets.isEmpty()) {
            item {
                EmptyState(
                    stringResource(R.string.budgets_empty_title),
                    stringResource(R.string.budgets_empty_description),
                )
            }
        }

        items(budgets, key = { it.id }) { budget -> BudgetCard(budget, hide, onLongPress) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BudgetCard(budget: Budget, hide: Boolean, onLongPress: (Budget) -> Unit) {
    val colors = Broke.colors
    // "Over budget" is a comparison of two server-computed figures, not a
    // recalculation of either.
    val over = budget.spentMxnCents > budget.limitCents

    GlassCard(
        Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = { onLongPress(budget) }),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Dot(parseHexColor(budget.color) ?: colors.accent, 12.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                budget.categoryName ?: stringResource(R.string.dashboard_uncategorized),
                style = MaterialTheme.typography.titleMedium,
                color = colors.fg,
                modifier = Modifier.weight(1f),
            )
            Text(
                formatBps(budget.progressBps),
                style = MaterialTheme.typography.labelLarge,
                color = if (over) colors.danger else colors.fgMuted,
            )
        }

        Spacer(Modifier.height(12.dp))
        ProgressBar(budget.progressBps, over = over)
        Spacer(Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                maskIfHidden(formatMoney(budget.spentMxnCents), hide),
                style = MaterialTheme.typography.bodyLarge,
                color = if (over) colors.danger else colors.fg,
            )
            Text(
                "  /  " + maskIfHidden(formatMoney(budget.limitCents), hide),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.fgSubtle,
            )
        }
    }
}
