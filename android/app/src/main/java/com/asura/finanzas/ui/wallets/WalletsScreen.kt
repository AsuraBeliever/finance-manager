package com.asura.finanzas.ui.wallets

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asura.finanzas.R
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.Wallet
import com.asura.finanzas.ui.LocalAppSettings
import com.asura.finanzas.ui.components.EmptyState
import com.asura.finanzas.ui.components.ErrorBox
import com.asura.finanzas.ui.components.GlassCard
import com.asura.finanzas.ui.components.Load
import com.asura.finanzas.ui.components.LoadingBox
import com.asura.finanzas.ui.components.MicroLabel
import com.asura.finanzas.ui.components.OfflineNotice
import com.asura.finanzas.ui.components.PageHeader
import com.asura.finanzas.ui.components.PrimaryButton
import com.asura.finanzas.ui.components.loadSynced
import com.asura.finanzas.ui.components.rememberReloadKey
import com.asura.finanzas.ui.formatMoney
import com.asura.finanzas.ui.maskIfHidden
import com.asura.finanzas.ui.theme.Broke
import kotlinx.coroutines.launch

@Composable
fun WalletsScreen(repository: BrokeRepository, modifier: Modifier = Modifier) {
    val (key, reload) = rememberReloadKey()
    val state by loadSynced(key) { repository.wallets() }
    val scope = rememberCoroutineScope()

    var editing by remember { mutableStateOf<Wallet?>(null) }
    var creating by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<Wallet?>(null) }
    var confirmDelete by remember { mutableStateOf<Wallet?>(null) }

    val all = (state as? Load.Ready)?.data.orEmpty()

    when (val current = state) {
        is Load.Loading -> LoadingBox(modifier)
        is Load.Failed -> ErrorBox(current.message, reload, modifier)
        is Load.Ready -> WalletList(
            wallets = current.data,
            fromCache = current.fromCache,
            onNew = { creating = true },
            onLongPress = { actionsFor = it },
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
private fun DialogAction(
    label: String,
    color: androidx.compose.ui.graphics.Color? = null,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = color ?: Broke.colors.fg,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    )
}

@Composable
private fun WalletList(
    wallets: List<Wallet>,
    fromCache: Boolean,
    onNew: () -> Unit,
    onLongPress: (Wallet) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hide = LocalAppSettings.current.hideBalances

    // Apartados hang off a parent wallet; listing them at the top level would
    // double-count what the user sees, so they nest under their parent.
    val visible = wallets.filter { !it.isArchived }
    val roots = visible.filter { it.parentWalletId == null }
    val pockets = visible.filter { it.parentWalletId != null }.groupBy { it.parentWalletId }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            PageHeader(stringResource(R.string.wallets_title)) {
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

        items(roots, key = { it.id }) { wallet ->
            Column {
                WalletCard(wallet, hide, onLongPress)
                val children = pockets[wallet.id].orEmpty()
                if (children.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    MicroLabel(
                        "${stringResource(R.string.wallets_apartados_label)} · ${children.size}",
                        Modifier.padding(start = 4.dp, bottom = 6.dp),
                    )
                    children.forEach { pocket ->
                        PocketRow(pocket, hide)
                        Spacer(Modifier.height(6.dp))
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
private fun WalletCard(wallet: Wallet, hide: Boolean, onLongPress: (Wallet) -> Unit) {
    val colors = Broke.colors
    val skin = walletSkin(wallet.skin, wallet.color, wallet.categoryName)
    // Available is what the server already reports minus what it already
    // reports as reserved — a presentation pairing of two given figures.
    val available = wallet.balanceCents - wallet.reservedCents

    Box(
        Modifier
            .fillMaxWidth()
            .height(196.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(skin.brush())
            .border(1.dp, colors.borderMuted, RoundedCornerShape(26.dp))
            .combinedClickable(onClick = {}, onLongClick = { onLongPress(wallet) }),
    ) {
        Icon(
            skinArtIcon(skin.art),
            contentDescription = null,
            tint = skin.fg.copy(alpha = 0.16f),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 18.dp)
                .size(132.dp),
        )

        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(
                    wallet.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = skin.fg,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    wallet.currencyCode,
                    style = MaterialTheme.typography.labelLarge,
                    color = skin.fg.copy(alpha = 0.85f),
                )
            }

            Spacer(Modifier.weight(1f))

            Text(
                maskIfHidden(formatMoney(wallet.balanceCents, wallet.currencyCode), hide),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 32.sp, lineHeight = 38.sp),
                color = skin.fg,
            )
            Text(
                buildString {
                    append(stringResource(R.string.wallets_available))
                    append(' ')
                    append(maskIfHidden(formatMoney(available, wallet.currencyCode), hide))
                    if (wallet.reservedCents > 0) {
                        append(" · ")
                        append(stringResource(R.string.wallets_reserved))
                        append(' ')
                        append(maskIfHidden(formatMoney(wallet.reservedCents, wallet.currencyCode), hide))
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = skin.fg.copy(alpha = 0.82f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun PocketRow(pocket: Wallet, hide: Boolean) {
    val colors = Broke.colors
    GlassCard(Modifier.fillMaxWidth(), padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(6.dp))
            Text(
                pocket.name,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.fgMuted,
                modifier = Modifier.weight(1f),
            )
            Text(
                maskIfHidden(formatMoney(pocket.balanceCents, pocket.currencyCode), hide),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.fg,
            )
        }
    }
}

/** The motif the web draws on each skin group. */
@Composable
private fun skinArtIcon(art: SkinArt) = when (art) {
    SkinArt.Wallet -> Icons.Outlined.AccountBalanceWallet
    SkinArt.Banknote -> Icons.Outlined.Payments
    SkinArt.Coins, SkinArt.Piggy -> Icons.Outlined.Savings
    else -> Icons.Outlined.CreditCard
}
