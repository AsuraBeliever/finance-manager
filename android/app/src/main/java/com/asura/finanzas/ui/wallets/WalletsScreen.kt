package com.asura.finanzas.ui.wallets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asura.finanzas.R
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.Wallet
import com.asura.finanzas.ui.LocalAppSettings
import com.asura.finanzas.ui.parseHexColor
import com.asura.finanzas.ui.seedName
import com.asura.finanzas.ui.components.EmptyState
import com.asura.finanzas.ui.components.Lucide
import com.asura.finanzas.ui.components.ErrorBox
import com.asura.finanzas.ui.components.GlassCard
import com.asura.finanzas.ui.components.Load
import com.asura.finanzas.ui.components.LoadingBox
import com.asura.finanzas.ui.components.MicroLabel
import com.asura.finanzas.ui.components.OfflineNotice
import com.asura.finanzas.ui.components.PageHeader
import com.asura.finanzas.ui.components.DialogAction
import com.asura.finanzas.ui.components.PrimaryButton
import com.asura.finanzas.ui.components.PrivacyToggle
import com.asura.finanzas.ui.components.ReorderHandle
import com.asura.finanzas.ui.components.rememberReorderState
import com.asura.finanzas.ui.components.loadSynced
import com.asura.finanzas.ui.components.rememberReloadKey
import com.asura.finanzas.ui.formatMoney
import com.asura.finanzas.ui.maskIfHidden
import com.asura.finanzas.ui.theme.Broke
import kotlinx.coroutines.launch

@Composable
fun WalletsScreen(repository: BrokeRepository, modifier: Modifier = Modifier) {
    val (key, reload) = rememberReloadKey()
    var showArchived by remember { mutableStateOf(false) }
    val state by loadSynced(key to showArchived) { repository.wallets(showArchived) }
    val scope = rememberCoroutineScope()

    var editing by remember { mutableStateOf<Wallet?>(null) }
    var creating by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<Wallet?>(null) }
    var openId by remember { mutableStateOf<Long?>(null) }
    var confirmDelete by remember { mutableStateOf<Wallet?>(null) }

    val all = (state as? Load.Ready)?.data.orEmpty()

    val detailId = openId
    if (detailId != null) {
        WalletDetailScreen(
            repository = repository,
            walletId = detailId,
            onBack = { openId = null; reload() },
            onEdit = { editing = it },
            modifier = modifier,
        )
        if (editing != null) {
            WalletFormSheet(
                repository = repository,
                existing = editing,
                wallets = all,
                onDismiss = { editing = null },
                onSaved = { editing = null; reload() },
            )
        }
        return
    }

    when (val current = state) {
        is Load.Loading -> LoadingBox(modifier)
        is Load.Failed -> ErrorBox(current.message, reload, modifier)
        is Load.Ready -> WalletList(
            wallets = current.data,
            fromCache = current.fromCache,
            onNew = { creating = true },
            onOpen = { openId = it.id },
            onLongPress = { actionsFor = it },
            showArchived = showArchived,
            onToggleArchived = { showArchived = !showArchived },
            // Only top-level wallets reorder; apartados follow their parent.
            onReorder = { ids -> scope.launch { runCatching { repository.reorderWallets(ids) } } },
            modifier = modifier,
        )
    }

    if (creating || editing != null) {
        WalletFormSheet(
            repository = repository,
            existing = editing,
            wallets = all,
            onDismiss = { creating = false; editing = null },
            onSaved = { creating = false; editing = null; reload() },
        )
    }

    // Long press opens the actions the web keeps behind the card's overflow.
    actionsFor?.let { target ->
        AlertDialog(
            onDismissRequest = { actionsFor = null },
            containerColor = Broke.colors.surfaceOverlay,
            title = { Text(target.name, color = Broke.colors.fg) },
            text = {
                Column {
                    DialogAction(stringResource(R.string.common_edit)) {
                        actionsFor = null
                        editing = target
                    }
                    DialogAction(
                        stringResource(
                            if (target.isArchived) R.string.wallets_unarchive
                            else R.string.wallets_archive,
                        ),
                    ) {
                        actionsFor = null
                        scope.launch {
                            runCatching { repository.archiveWallet(target.id, !target.isArchived) }
                            reload()
                        }
                    }
                    DialogAction(
                        stringResource(R.string.wallets_delete_wallet),
                        color = Broke.colors.danger,
                    ) {
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
            title = { Text(stringResource(R.string.wallets_delete_confirm_title)) },
            text = { Text(stringResource(R.string.wallets_delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    scope.launch {
                        runCatching { repository.deleteWallet(target.id) }
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
private fun WalletList(
    wallets: List<Wallet>,
    fromCache: Boolean,
    onNew: () -> Unit,
    onOpen: (Wallet) -> Unit,
    onLongPress: (Wallet) -> Unit,
    showArchived: Boolean,
    onToggleArchived: () -> Unit,
    onReorder: (List<Long>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hide = LocalAppSettings.current.hideBalances

    // Apartados hang off a parent wallet; listing them at the top level would
    // double-count what the user sees, so they nest under their parent. The
    // server already filters archived ones unless they were asked for.
    val visible = wallets
    val pockets = visible.filter { it.parentWalletId != null }.groupBy { it.parentWalletId }

    // Dragging rewrites this list as the finger moves so the rows shuffle live;
    // the server is told once, on drop. Rebuilt whenever a fresh load arrives.
    val roots = remember(wallets) {
        mutableStateListOf<Wallet>().apply {
            addAll(visible.filter { it.parentWalletId == null })
        }
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Header, and the offline notice when shown, sit above the draggable rows.
    val firstRow = 1 + (if (fromCache) 1 else 0)
    val reorderState = rememberReorderState(
        listState = listState,
        scope = scope,
        range = { firstRow until firstRow + roots.size },
        onMove = { from, to -> roots.add(to, roots.removeAt(from)) },
        onDrop = { onReorder(roots.map { it.id }) },
    )

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            PageHeader(stringResource(R.string.wallets_title)) {
                PrivacyToggle()
                // A real checkbox, as on the web — the label alone gave no clue
                // whether archived wallets were being shown.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onToggleArchived() },
                ) {
                    Checkbox(
                        checked = showArchived,
                        onCheckedChange = { onToggleArchived() },
                        colors = CheckboxDefaults.colors(checkedColor = Broke.colors.accent),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.wallets_show_archived),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Broke.colors.fgMuted,
                    )
                }
                PrimaryButton(
                    text = stringResource(R.string.wallets_new_wallet),
                    onClick = onNew,
                    leadingIcon = Icons.Outlined.Add,
                )
            }
        }

        if (fromCache) {
            item { OfflineNotice(stringResource(R.string.offline_banner), Modifier.fillMaxWidth()) }
        }

        if (roots.isEmpty()) {
            item {
                EmptyState(
                    stringResource(R.string.wallets_empty_title),
                    stringResource(R.string.wallets_empty_description),
                )
            }
        }

        itemsIndexed(roots, key = { _, it -> it.id }) { _, wallet ->
            val dragging = reorderState.draggingKey == wallet.id
            Column(
                Modifier
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer { translationY = if (dragging) reorderState.offsetY else 0f },
            ) {
                val children = pockets[wallet.id].orEmpty()
                WalletCard(
                    wallet, hide, onOpen, onLongPress,
                    pockets = children,
                    handle = { ReorderHandle(state = reorderState, key = wallet.id) },
                )
                if (children.isNotEmpty()) {
                    // Collapsed until asked for, like the web: a wallet with
                    // many apartados should not bury the next card.
                    var expanded by remember(wallet.id) { mutableStateOf(false) }
                    val turn by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { expanded = !expanded }
                            .padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
                    ) {
                        Icon(
                            Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            tint = Broke.colors.fgSubtle,
                            modifier = Modifier.size(14.dp).graphicsLayer { rotationZ = turn },
                        )
                        Spacer(Modifier.width(6.dp))
                        MicroLabel(
                            "${stringResource(R.string.wallets_apartados_label)} · ${children.size}",
                        )
                    }
                    AnimatedVisibility(expanded) {
                        // The pockets hang off a rail, the web's border-l-2.
                        Row(Modifier.padding(top = 4.dp)) {
                            Box(
                                Modifier
                                    .width(2.dp)
                                    .fillMaxHeight()
                                    .background(Broke.colors.borderMuted),
                            )
                            Column(Modifier.padding(start = 12.dp)) {
                                children.forEach { pocket ->
                                    PocketRow(pocket, hide, onOpen)
                                    Spacer(Modifier.height(6.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The big gradient card from the web: name and currency on top, a watermark
 * wallet glyph, then the balance with available/reserved underneath.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WalletCard(
    wallet: Wallet,
    hide: Boolean,
    onOpen: (Wallet) -> Unit,
    onLongPress: (Wallet) -> Unit,
    /** The wallet's apartados, which the headline folds back in — see below. */
    pockets: List<Wallet> = emptyList(),
    /** The drag grip, drawn in the corner; a tap anywhere else still opens. */
    handle: @Composable () -> Unit = {},
) {
    val skin = walletSkin(wallet.skin, wallet.color, wallet.categoryName)

    // Same pairing the web's WalletCard shows, on figures the server already
    // computed: money in a pocket left the parent through a transfer, so the
    // headline adds it back to show what the bank shows. Only same-currency
    // pockets add up.
    val pocketsCents = pockets
        .filter { it.currencyCode == wallet.currencyCode }
        .sumOf { it.balanceCents }
    val reserved = wallet.reservedCents + pocketsCents
    val total = wallet.balanceCents + pocketsCents
    val available = wallet.balanceCents - wallet.reservedCents

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            // Credit-card proportions, the web's aspect-[1.586/1].
            .aspectRatio(1.586f)
            .clip(RoundedCornerShape(16.dp))
            .background(skin.brush())
            .combinedClickable(onClick = { onOpen(wallet) }, onLongClick = { onLongPress(wallet) }),
    ) {
        // Crystalline gloss, then the soft top-left highlight, then the big
        // faint motif — the three layers the web stacks over the skin.
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.linearGradient(
                        0f to Color.White.copy(alpha = 0.30f),
                        0.22f to Color.White.copy(alpha = 0.06f),
                        0.46f to Color.Transparent,
                    ),
                ),
        )
        // The web sizes this highlight off the card itself (2/3 of it, pulled a
        // third up and a quarter left), so it scales with the card instead of
        // reading as a bright blob at one size and vanishing at another.
        Box(
            Modifier
                .size(width = maxWidth * 2 / 3, height = maxHeight * 2 / 3)
                .offset(x = -maxWidth / 4, y = -maxHeight / 3)
                .background(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.28f * 0.70f), Color.Transparent),
                    ),
                    CircleShape,
                ),
        )
        // Only the money skins get the big faint motif; a card skin shows its
        // chip and nothing else, exactly as the web's ART_ICON map decides.
        if (skin.art != SkinArt.None && skin.art != SkinArt.Chip) {
            Icon(
                skinArtIcon(skin.art),
                contentDescription = null,
                tint = skin.fg.copy(alpha = 0.14f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 2.dp)
                    .size(150.dp),
            )
        }
        // Hairline highlight around the edge (the web's ring-inset white/15).
        Box(
            Modifier
                .matchParentSize()
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp)),
        )

        Column(
            Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(
                    wallet.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = skin.fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                if (wallet.isArchived) {
                    Text(
                        stringResource(R.string.wallets_archived),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = skin.fg,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.30f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                } else {
                    Text(
                        wallet.currencyCode,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                        color = skin.fg.copy(alpha = 0.80f),
                    )
                }
            }

            // Middle slot: the chip for card skins, a small motif for the money
            // ones, and otherwise the same empty gap the web leaves.
            when {
                skin.art == SkinArt.Chip -> CardChip()
                skin.art != SkinArt.None -> Icon(
                    skinArtIcon(skin.art),
                    contentDescription = null,
                    tint = skin.fg,
                    modifier = Modifier.size(30.dp),
                )
                else -> Spacer(Modifier.height(28.dp))
            }

            Column {
                Text(
                    maskIfHidden(formatMoney(total, wallet.currencyCode), hide),
                    style = MaterialTheme.typography.displayLarge
                        .copy(fontSize = 24.sp, lineHeight = 30.sp),
                    color = skin.fg,
                )
                Text(
                    if (reserved > 0) {
                        stringResource(R.string.wallets_available) + " " +
                            maskIfHidden(formatMoney(available, wallet.currencyCode), hide) +
                            " · " + stringResource(R.string.wallets_reserved) + " " +
                            maskIfHidden(formatMoney(reserved, wallet.currencyCode), hide)
                    } else {
                        seedName(wallet.categoryName).orEmpty()
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                    color = skin.fg.copy(alpha = 0.80f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        // The grip sits in the bottom corner over a dark pill, exactly where the
        // web puts it, so it reads on any skin without covering the figures.
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.25f))
                .padding(6.dp),
        ) {
            handle()
        }
    }
}

/** The gold contact plate on card skins — the web's CSS chip. */
@Composable
private fun CardChip() {
    Column(
        Modifier
            .size(width = 40.dp, height = 28.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFFFEF3C6).copy(alpha = 0.95f), Color(0xFFFBBF24).copy(alpha = 0.85f)),
                ),
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        repeat(3) {
            Box(
                Modifier
                    .padding(top = 4.dp)
                    .size(width = 24.dp, height = 2.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.15f)),
            )
        }
    }
}

/** An apartado under its parent card: colour tab, piggy, name, amount. */
@Composable
private fun PocketRow(pocket: Wallet, hide: Boolean, onOpen: (Wallet) -> Unit) {
    val colors = Broke.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceRaised)
            .border(1.dp, colors.borderMuted, RoundedCornerShape(12.dp))
            .clickable { onOpen(pocket) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Box(
            Modifier
                .size(width = 4.dp, height = 24.dp)
                .clip(CircleShape)
                .background(parseHexColor(pocket.color) ?: colors.accent),
        )
        Spacer(Modifier.width(10.dp))
        Icon(
            Lucide.PiggyBank,
            contentDescription = null,
            tint = colors.fgSubtle,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            pocket.name,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            maskIfHidden(formatMoney(pocket.balanceCents, pocket.currencyCode), hide),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.fg,
        )
    }
}

/** The motif the web draws on each skin group. */
@Composable
private fun skinArtIcon(art: SkinArt) = when (art) {
    SkinArt.Wallet -> Lucide.Wallet
    SkinArt.Banknote -> Lucide.Banknote
    SkinArt.Coins -> Lucide.Coins
    SkinArt.Piggy -> Lucide.PiggyBank
    else -> Lucide.CreditCard
}
