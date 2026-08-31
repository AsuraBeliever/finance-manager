package com.asura.finanzas.ui.categories

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import com.asura.finanzas.ui.components.CATEGORY_PALETTE
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.sp
import com.asura.finanzas.ui.components.HairLine
import com.asura.finanzas.ui.components.Lucide
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import com.asura.finanzas.ui.components.FormField
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

    var editing by remember { mutableStateOf<TransactionCategory?>(null) }
    var actionsFor by remember { mutableStateOf<TransactionCategory?>(null) }

    when (val current = state) {
        is Load.Loading -> LoadingBox(modifier)
        is Load.Failed -> ErrorBox(current.message, reload, modifier)
        is Load.Ready -> CategoryList(
            categories = current.data,
            fromCache = current.fromCache,
            onBack = onBack,
            onLongPress = { actionsFor = it },
            // The eye hides a category or restores it — the same single action
            // the web's row button offers.
            onToggleHidden = { target ->
                scope.launch {
                    runCatching {
                        if (target.isHidden) repository.restoreCategory(target.id)
                        else repository.deleteCategory(target.id)
                    }
                    reload()
                }
            },
            // Order is kept per kind, exactly like the web's two sortable lists.
            onReorder = { ids ->
                scope.launch { runCatching { repository.reorderTransactionCategories(ids) } }
            },
            onCreate = { name, kind, color ->
                scope.launch {
                    runCatching { repository.createCategory(name, kind, color) }
                    reload()
                }
            },
            modifier = modifier,
        )
    }

    if (editing != null) {
        CategoryFormSheet(
            repository = repository,
            existing = editing,
            onDismiss = { editing = null },
            onSaved = { editing = null; reload() },
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
    onLongPress: (TransactionCategory) -> Unit,
    onToggleHidden: (TransactionCategory) -> Unit,
    onReorder: (List<Long>) -> Unit,
    onCreate: (name: String, kind: String, color: String) -> Unit,
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
    // Income first, then expenses — the order the web lists them in.
    // Income first, then expenses — the order the web lists them in. Each block
    // is one label, its rows, then the inline add.
    val incomeStart = headerRows + 1
    val expenseStart = incomeStart + income.size + 2

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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 28.dp),
        // Rows inside a section butt together to form one card, so the spacing
        // between them is drawn by the section itself, not by the list.
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            BackHeader(stringResource(R.string.categories_title), onBack)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.categories_settings_hint),
                style = MaterialTheme.typography.bodySmall,
                color = Broke.colors.fgSubtle,
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

        // Both sections always render: the add row lives inside each one, so an
        // empty kind still needs somewhere to add to — same as the web's cards.
        item { Spacer(Modifier.height(16.dp)) }
        item {
            SectionTop(stringResource(R.string.categories_income))
        }
        itemsIndexed(income, key = { _, it -> "i-${it.id}" }) { index, category ->
            SectionBody {
                if (index > 0) HairLine()
                CategoryRow(
                    category = category,
                    onLongPress = onLongPress,
                    reorderState = incomeReorder,
                    rowKey = "i-${category.id}",
                    onToggleHidden = onToggleHidden,
                )
            }
        }
        item {
            SectionBottom {
                InlineAddCategory(kind = "income", existing = income, onCreate = onCreate)
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
        item {
            SectionTop(stringResource(R.string.categories_expense))
        }
        itemsIndexed(expense, key = { _, it -> "e-${it.id}" }) { index, category ->
            SectionBody {
                if (index > 0) HairLine()
                CategoryRow(
                    category = category,
                    onLongPress = onLongPress,
                    reorderState = expenseReorder,
                    rowKey = "e-${category.id}",
                    onToggleHidden = onToggleHidden,
                )
            }
        }
        item {
            SectionBottom {
                InlineAddCategory(kind = "expense", existing = expense, onCreate = onCreate)
            }
        }
    }
}

/**
 * A section renders as one card on the web, but its rows have to stay separate
 * lazy items or drag-to-reorder loses the layout it measures against. So the
 * card is drawn in three pieces that butt together: rounded top with the
 * heading, square-sided middles, rounded bottom with the add row.
 */
@Composable
private fun SectionTop(title: String) {
    val colors = Broke.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .background(colors.surfaceRaised)
            .sideBorders()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.fg)
    }
}

@Composable
private fun SectionBody(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Broke.colors.surfaceRaised)
            .sideBorders()
            .padding(horizontal = 16.dp),
        content = content,
    )
}

@Composable
private fun SectionBottom(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
            .background(Broke.colors.surfaceRaised)
            .sideBorders(bottom = true)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        content = content,
    )
}

/** Hairlines down the sides (and optionally the bottom) of a card slice. */
@Composable
private fun Modifier.sideBorders(bottom: Boolean = false): Modifier {
    val colors = Broke.colors
    return drawBehind {
        val w = 1.dp.toPx()
        drawRect(colors.borderMuted, topLeft = Offset.Zero, size = Size(w, size.height))
        drawRect(
            colors.borderMuted,
            topLeft = Offset(size.width - w, 0f),
            size = Size(w, size.height),
        )
        if (bottom) {
            drawRect(
                colors.borderMuted,
                topLeft = Offset(0f, size.height - w),
                size = Size(size.width, w),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryRow(
    category: TransactionCategory,
    onLongPress: (TransactionCategory) -> Unit,
    reorderState: ReorderState,
    rowKey: String,
    onToggleHidden: (TransactionCategory) -> Unit,
) {
    val colors = Broke.colors
    val dragging = reorderState.draggingKey == "i-${category.id}" ||
        reorderState.draggingKey == "e-${category.id}"
    // A hidden category is dimmed rather than removed, the same signal the web
    // gives before you decide whether to restore it.
    val alpha = if (category.isHidden) 0.5f else 1f

    // A plain row inside the section's single card — the web separates them with
    // hairlines instead of giving each category a card of its own.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer { translationY = if (dragging) reorderState.offsetY else 0f }
            .combinedClickable(onClick = {}, onLongClick = { onLongPress(category) })
            .padding(horizontal = 4.dp, vertical = 10.dp),
    ) {
        // Grip on the left, as on the web.
        ReorderHandle(state = reorderState, key = rowKey)
        Spacer(Modifier.width(8.dp))
        Dot((parseHexColor(category.color) ?: colors.accent).copy(alpha = alpha), 10.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            seedName(category.name, category.isSystem).orEmpty(),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = colors.fg.copy(alpha = alpha),
            modifier = Modifier.weight(1f),
        )
        if (category.isHidden || category.isSystem) {
            Text(
                stringResource(
                    if (category.isHidden) R.string.categories_hidden_label
                    else R.string.categories_default_badge,
                ),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = colors.fgSubtle,
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(colors.surfaceOverlay)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        // Hiding a seeded category is what the web's eye offers here; a real
        // delete is only for your own, and stays on the long press.
        Icon(
            if (category.isHidden) Lucide.Eye else Lucide.EyeOff,
            contentDescription = null,
            tint = colors.fgSubtle,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { onToggleHidden(category) }
                .padding(6.dp)
                .size(15.dp),
        )
    }
}

/**
 * Add a category straight into its section, the way the web does it: type a
 * name and press add, with the colour picked from the palette rather than
 * asked for. The kind comes from the section, so it can never be wrong.
 */
@Composable
private fun InlineAddCategory(
    kind: String,
    existing: List<TransactionCategory>,
    onCreate: (name: String, kind: String, color: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val colors = Broke.colors
    // First unused swatch, else wrap around — same rule as the web's nextColor.
    val color = remember(existing) {
        val used = existing.mapNotNull { it.color }.toSet()
        CATEGORY_PALETTE.firstOrNull { it !in used }
            ?: CATEGORY_PALETTE[existing.size % CATEGORY_PALETTE.size]
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        FormField(
            // The web's add row shows only the placeholder, no caption.
            label = "",
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.weight(1f),
            placeholder = stringResource(R.string.categories_add_placeholder),
            singleLine = true,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(R.string.categories_add),
            style = MaterialTheme.typography.labelLarge,
            color = if (name.isBlank()) colors.fgSubtle else colors.accent,
            modifier = Modifier
                .clickable(enabled = name.isNotBlank()) {
                    onCreate(name.trim(), kind, color)
                    name = ""
                }
                .padding(8.dp),
        )
    }
}
