package com.asura.finanzas.ui.categories

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import com.asura.finanzas.ui.components.CATEGORY_PALETTE
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.asura.finanzas.ui.components.ErrorBox
import com.asura.finanzas.ui.components.ConfirmDialog
import androidx.compose.ui.draw.alpha
import com.asura.finanzas.ui.components.ColorPicker
import com.asura.finanzas.ui.components.Load
import com.asura.finanzas.ui.components.LoadingBox
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
    val state by loadSynced("categories", refetch = key) { repository.manageCategories() }
    val scope = rememberCoroutineScope()

    var editing by remember { mutableStateOf<TransactionCategory?>(null) }
    // Hiding a seed row or deleting a user one asks first, as the web does;
    // restoring a hidden one is harmless and goes straight through.
    var deleting by remember { mutableStateOf<TransactionCategory?>(null) }

    when (val current = state) {
        is Load.Loading -> LoadingBox(modifier)
        is Load.Failed -> ErrorBox(current.message, reload, modifier)
        is Load.Ready -> CategoryList(
            categories = current.data,
            fromCache = current.fromCache,
            onBack = onBack,
            // The row's own buttons, as on the web: restore a hidden one,
            // ask before hiding a seed or deleting your own, rename in place.
            onRestore = { target ->
                scope.launch {
                    runCatching { repository.restoreCategory(target.id) }
                    reload()
                }
            },
            onAskDelete = { deleting = it },
            onRename = { target, name, color ->
                scope.launch {
                    runCatching { repository.updateCategory(target.id, name, color) }
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


    deleting?.let { target ->
        ConfirmDialog(
            title = stringResource(R.string.categories_delete_confirm_title),
            // A seed row is only ever hidden — it comes back with "restore" —
            // so it gets the gentler wording, exactly as on the web.
            message = stringResource(
                if (target.isSystem) R.string.categories_hide_confirm_message
                else R.string.categories_delete_confirm_message,
            ),
            confirmLabel = stringResource(
                if (target.isSystem) R.string.categories_hide else R.string.categories_delete,
            ),
            onConfirm = {
                deleting = null
                scope.launch {
                    runCatching { repository.deleteCategory(target.id) }
                    reload()
                }
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun CategoryList(
    categories: List<TransactionCategory>,
    fromCache: Boolean,
    onBack: () -> Unit,
    onRestore: (TransactionCategory) -> Unit,
    onAskDelete: (TransactionCategory) -> Unit,
    onRename: (TransactionCategory, String, String?) -> Unit,
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
            // The web labels this one "Volver a ajustes", not a bare "Atrás".
            BackHeader(
                stringResource(R.string.categories_title),
                onBack,
                backLabel = stringResource(R.string.settings_back),
            )
            // `mb-5 -mt-3 text-sm`: 16 under the header's `mb-7`.
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.categories_settings_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = Broke.colors.fgSubtle,
            )
        }


        // Both sections always render: the add row lives inside each one, so an
        // empty kind still needs somewhere to add to — same as the web's cards.
        item { Spacer(Modifier.height(20.dp)) }
        item {
            SectionTop(stringResource(R.string.categories_income))
        }
        // The web writes "no categories yet" inside the section that is
        // empty, not once for the screen: each kind has its own add row.
        if (income.isEmpty()) {
            item { SectionBody { EmptyLine(stringResource(R.string.categories_empty)) } }
        }
        itemsIndexed(income, key = { _, it -> "i-${it.id}" }) { index, category ->
            SectionBody {
                if (index > 0) HairLine()
                CategoryRow(
                    category = category,
                    reorderState = incomeReorder,
                    rowKey = "i-${category.id}",
                    onRestore = onRestore,
                    onAskDelete = onAskDelete,
                    onRename = onRename,
                )
            }
        }
        item {
            SectionBottom {
                InlineAddCategory(kind = "income", existing = income, onCreate = onCreate)
            }
        }

        // `flex flex-col gap-6` between the two cards.
        item { Spacer(Modifier.height(24.dp)) }
        item {
            SectionTop(stringResource(R.string.categories_expense))
        }
        // The web writes "no categories yet" inside the section that is
        // empty, not once for the screen: each kind has its own add row.
        if (expense.isEmpty()) {
            item { SectionBody { EmptyLine(stringResource(R.string.categories_empty)) } }
        }
        itemsIndexed(expense, key = { _, it -> "e-${it.id}" }) { index, category ->
            SectionBody {
                if (index > 0) HairLine()
                CategoryRow(
                    category = category,
                    reorderState = expenseReorder,
                    rowKey = "e-${category.id}",
                    onRestore = onRestore,
                    onAskDelete = onAskDelete,
                    onRename = onRename,
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
            .padding(start = 21.dp, end = 21.dp, top = 21.dp, bottom = 4.dp),
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
            .padding(horizontal = 21.dp),
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
            .padding(start = 21.dp, end = 21.dp, top = 12.dp, bottom = 21.dp),
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
    reorderState: ReorderState,
    rowKey: String,
    onRestore: (TransactionCategory) -> Unit,
    onAskDelete: (TransactionCategory) -> Unit,
    onRename: (TransactionCategory, String, String?) -> Unit,
) {
    val colors = Broke.colors
    val dragging = reorderState.draggingKey == "i-${category.id}" ||
        reorderState.draggingKey == "e-${category.id}"
    var editing by remember(category.id) { mutableStateOf(false) }
    var name by remember(category.id, category.name) { mutableStateOf(category.name) }
    var color by remember(category.id, category.color) { mutableStateOf(category.color) }

    if (editing) {
        // The web renames in place: the name box with a check and an ✕, and
        // the colour swatches under it (`flex flex-col gap-2 py-2.5`).
        Column(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val focus = remember { androidx.compose.ui.focus.FocusRequester() }
                androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                FormField(
                    label = "",
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    focusRequester = focus,
                )
                val canSave = name.isNotBlank()
                Icon(
                    Lucide.Check,
                    contentDescription = stringResource(R.string.categories_save),
                    tint = colors.accent.copy(alpha = if (canSave) 1f else 0.4f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = canSave) { onRename(category, name.trim(), color); editing = false }
                        .padding(6.dp)
                        .size(16.dp),
                )
                Icon(
                    Lucide.X,
                    contentDescription = stringResource(R.string.common_cancel),
                    tint = colors.fgMuted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { editing = false; name = category.name; color = category.color }
                        .padding(6.dp)
                        .size(16.dp),
                )
            }
            ColorPicker(value = color, onChange = { color = it })
        }
        return
    }

    // A hidden category is dimmed rather than removed (`opacity-50`).
    val alpha = if (category.isHidden) 0.5f else 1f
    // `flex items-center gap-2 py-2.5`: grip, dot, name, badge, buttons.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer { translationY = if (dragging) reorderState.offsetY else 0f }
            .alpha(alpha)
            
            .padding(vertical = 10.dp),
    ) {
        ReorderHandle(state = reorderState, key = rowKey)
        // `NEUTRAL_DOT` when the category has no colour of its own.
        Dot(parseHexColor(category.color) ?: androidx.compose.ui.graphics.Color(0xFF9A93B5), 10.dp)
        Text(
            seedName(category.name, category.isSystem).orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.fg,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (category.isSystem) {
            Text(
                stringResource(
                    if (category.isHidden) R.string.categories_hidden_label
                    else R.string.categories_default_badge,
                ),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 15.sp),
                color = colors.fgSubtle,
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(colors.surfaceOverlay)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        @Composable
        fun action(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
            Icon(
                icon,
                contentDescription = label,
                tint = colors.fgMuted,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onClick)
                    .padding(6.dp)
                    .size(15.dp),
            )
        }
        if (category.isHidden) {
            action(Lucide.RotateCcw, stringResource(R.string.categories_restore)) { onRestore(category) }
        } else {
            if (!category.isSystem) {
                action(Lucide.Pencil, stringResource(R.string.categories_rename)) { editing = true }
            }
            action(
                if (category.isSystem) Lucide.EyeOff else Lucide.Trash,
                stringResource(if (category.isSystem) R.string.categories_hide else R.string.categories_delete),
            ) { onAskDelete(category) }
        }
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

    // `mt-3 flex items-center gap-2` (the section's bottom slice adds the mt).
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
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
        Spacer(Modifier.width(8.dp))
        // A ghost Button with a plus in front, as the web draws it.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = name.isNotBlank()) {
                    onCreate(name.trim(), kind, color)
                    name = ""
                }
                // A ghost Button with `px-3 py-2`, no `font-medium`.
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(
                Lucide.Plus,
                contentDescription = null,
                tint = if (name.isBlank()) colors.fg.copy(alpha = 0.5f) else colors.fg,
                modifier = Modifier.size(15.dp),
            )
            Text(
                stringResource(R.string.categories_add),
                style = MaterialTheme.typography.bodyMedium,
                color = if (name.isBlank()) colors.fg.copy(alpha = 0.5f) else colors.fg,
            )
        }
    }
}

/** The one-line "nothing here yet" the web puts inside an empty section. */
@Composable
private fun EmptyLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
        color = Broke.colors.fgSubtle,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}
