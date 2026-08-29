package com.asura.finanzas.ui.categories

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import com.asura.finanzas.R
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.TransactionCategory
import com.asura.finanzas.ui.components.BackHeader
import com.asura.finanzas.ui.components.Dot
import com.asura.finanzas.ui.components.EmptyState
import com.asura.finanzas.ui.components.ErrorBox
import com.asura.finanzas.ui.components.DialogAction
import com.asura.finanzas.ui.components.GlassCard
import com.asura.finanzas.ui.components.PrimaryButton
import com.asura.finanzas.ui.components.Load
import com.asura.finanzas.ui.components.LoadingBox
import com.asura.finanzas.ui.components.MicroLabel
import com.asura.finanzas.ui.components.OfflineNotice
import com.asura.finanzas.ui.components.ReorderHandle
import com.asura.finanzas.ui.components.ReorderState
import com.asura.finanzas.ui.components.rememberReorderState
import com.asura.finanzas.ui.components.loadSynced
import com.asura.finanzas.ui.components.rememberReloadKey
import com.asura.finanzas.ui.parseHexColor
import com.asura.finanzas.ui.seedName
import com.asura.finanzas.ui.theme.Broke
import kotlinx.coroutines.launch

@Composable
fun CategoriesScreen(
    repository: BrokeRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (key, reload) = rememberReloadKey()
    val state by loadSynced(key) { repository.manageCategories() }
    val scope = rememberCoroutineScope()

    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TransactionCategory?>(null) }
    var actionsFor by remember { mutableStateOf<TransactionCategory?>(null) }

    when (val current = state) {
        is Load.Loading -> LoadingBox(modifier)
        is Load.Failed -> ErrorBox(current.message, reload, modifier)
        is Load.Ready -> CategoryList(
            categories = current.data,
            fromCache = current.fromCache,
            onBack = onBack,
            onNew = { creating = true },
            onLongPress = { actionsFor = it },
            // Order is kept per kind, exactly like the web's two sortable lists.
            onReorder = { ids ->
                scope.launch { runCatching { repository.reorderTransactionCategories(ids) } }
            },
            modifier = modifier,
        )
    }

    if (creating || editing != null) {
        CategoryFormSheet(
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
            title = { Text(seedName(target.name, target.isSystem).orEmpty(), color = Broke.colors.fg) },
            text = {
                Column {
                    // Seeded categories are shared across accounts: the server
                    // will not rename them, so only hiding is offered.
                    if (!target.isSystem) {
                        DialogAction(stringResource(R.string.categories_rename)) {
                            actionsFor = null
                            editing = target
                        }
                    }
                    if (target.isHidden) {
                        // A hidden seed comes back into the pickers; the web
                        // offers the same undo on the row itself.
                        DialogAction(stringResource(R.string.categories_restore)) {
                            actionsFor = null
                            scope.launch {
                                runCatching { repository.restoreCategory(target.id) }
                                reload()
                            }
                        }
                    } else {
                        DialogAction(
                            stringResource(
                                if (target.isSystem) R.string.categories_hide
                                else R.string.categories_delete,
                            ),
                            Broke.colors.danger,
                        ) {
                            actionsFor = null
                            scope.launch {
                                runCatching { repository.deleteCategory(target.id) }
                                reload()
                            }
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
private fun CategoryList(
    categories: List<TransactionCategory>,
    fromCache: Boolean,
    onBack: () -> Unit,
    onNew: () -> Unit,
    onLongPress: (TransactionCategory) -> Unit,
    onReorder: (List<Long>) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Hidden categories stay on the list, dimmed and badged, so they can be
    // restored — the web never drops them from view either.
    val expense = remember(categories) {
        mutableStateListOf<TransactionCategory>().apply {
            addAll(categories.filter { it.kind == "expense" })
        }
    }
    val income = remember(categories) {
        mutableStateListOf<TransactionCategory>().apply {
            addAll(categories.filter { it.kind == "income" })
        }
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Where each draggable block starts, given the header rows above it.
    val headerRows = 1 + (if (fromCache) 1 else 0) + (if (categories.isEmpty()) 1 else 0)
    val expenseStart = headerRows + 1
    val expenseSection = if (expense.isEmpty()) 0 else 1 + expense.size
    val incomeStart = headerRows + expenseSection + 1

    val expenseReorder = rememberReorderState(
        listState = listState,
        scope = scope,
        range = { expenseStart until expenseStart + expense.size },
        onMove = { from, to -> expense.add(to, expense.removeAt(from)) },
        onDrop = { onReorder(expense.map { it.id }) },
    )
    val incomeReorder = rememberReorderState(
        listState = listState,
        scope = scope,
        range = { incomeStart until incomeStart + income.size },
        onMove = { from, to -> income.add(to, income.removeAt(from)) },
        onDrop = { onReorder(income.map { it.id }) },
    )

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            BackHeader(stringResource(R.string.categories_title), onBack)
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                text = stringResource(R.string.categories_add),
                onClick = onNew,
                leadingIcon = Icons.Outlined.Add,
            )
        }

        if (fromCache) {
            item { OfflineNotice(stringResource(R.string.offline_banner), Modifier.fillMaxWidth()) }
        }

        if (categories.isEmpty()) {
            item {
                EmptyState(
                    stringResource(R.string.categories_title),
                    stringResource(R.string.categories_settings_hint),
                )
            }
        }

        if (expense.isNotEmpty()) {
            item { MicroLabel(stringResource(R.string.transactions_expense)) }
            itemsIndexed(expense, key = { _, it -> "e-${it.id}" }) { index, category ->
                CategoryRow(
                    category = category,
                    onLongPress = onLongPress,
                    reorderState = expenseReorder,
                    lazyIndex = expenseStart + index,
                    currentIndex = { expenseStart + expense.indexOfFirst { c -> c.id == category.id } },
                )
            }
        }

        if (income.isNotEmpty()) {
            item { MicroLabel(stringResource(R.string.transactions_income), Modifier.padding(top = 8.dp)) }
            itemsIndexed(income, key = { _, it -> "i-${it.id}" }) { index, category ->
                CategoryRow(
                    category = category,
                    onLongPress = onLongPress,
                    reorderState = incomeReorder,
                    lazyIndex = incomeStart + index,
                    currentIndex = { incomeStart + income.indexOfFirst { c -> c.id == category.id } },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryRow(
    category: TransactionCategory,
    onLongPress: (TransactionCategory) -> Unit,
    reorderState: ReorderState,
    lazyIndex: Int,
    currentIndex: () -> Int,
) {
    val colors = Broke.colors
    val dragging = reorderState.draggingIndex == lazyIndex
    // A hidden category is dimmed rather than removed, the same signal the web
    // gives before you decide whether to restore it.
    val alpha = if (category.isHidden) 0.5f else 1f

    GlassCard(
        Modifier
            .fillMaxWidth()
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer { translationY = if (dragging) reorderState.offsetY else 0f }
            .combinedClickable(onClick = {}, onLongClick = { onLongPress(category) }),
        padding = 16.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Dot((parseHexColor(category.color) ?: colors.accent).copy(alpha = alpha), 12.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                seedName(category.name, category.isSystem).orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.fg.copy(alpha = alpha),
                modifier = Modifier.weight(1f),
            )
            if (category.isHidden || category.isSystem) {
                MicroLabel(
                    stringResource(
                        if (category.isHidden) R.string.categories_hidden_label
                        else R.string.categories_default_badge,
                    ),
                    Modifier.padding(end = 8.dp),
                )
            }
            ReorderHandle(
                state = reorderState,
                key = category.id,
                index = currentIndex,
            )
        }
    }
}
