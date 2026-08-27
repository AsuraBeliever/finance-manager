package com.asura.finanzas.ui.wallets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.Wallet
import com.asura.finanzas.ui.components.ErrorBox
import com.asura.finanzas.ui.components.GlassCard
import com.asura.finanzas.ui.components.Load
import com.asura.finanzas.ui.components.LoadingBox
import com.asura.finanzas.ui.components.OfflineNotice
import com.asura.finanzas.ui.components.SectionTitle
import com.asura.finanzas.ui.components.loadSynced
import com.asura.finanzas.ui.components.rememberReloadKey
import com.asura.finanzas.ui.formatMoney
import com.asura.finanzas.ui.parseHexColor
import com.asura.finanzas.ui.theme.Broke

@Composable
fun WalletsScreen(repository: BrokeRepository, modifier: Modifier = Modifier) {
    val (key, reload) = rememberReloadKey()
    val state by loadSynced(key) { repository.wallets() }

    when (val current = state) {
        is Load.Loading -> LoadingBox(modifier)
        is Load.Failed -> ErrorBox(current.message, reload, modifier)
        is Load.Ready -> WalletList(current.data, current.fromCache, modifier)
    }
}

@Composable
private fun WalletList(wallets: List<Wallet>, fromCache: Boolean, modifier: Modifier = Modifier) {
    // Apartados hang off a parent wallet; showing them as top-level rows would
    // double-count what the user sees, so they are nested under their parent.
    val visible = wallets.filter { !it.isArchived }
    val roots = visible.filter { it.parentWalletId == null }
    val pockets = visible.filter { it.parentWalletId != null }.groupBy { it.parentWalletId }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (fromCache) item { OfflineNotice(Modifier.fillMaxWidth()) }
        item { SectionTitle("Carteras") }

        items(roots, key = { it.id }) { wallet ->
            WalletCard(wallet, pockets[wallet.id].orEmpty())
        }

        if (roots.isEmpty()) {
            item {
                Text(
                    "Todavía no tienes carteras.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Broke.colors.fgMuted,
                )
            }
        }
    }
}

@Composable
private fun WalletCard(wallet: Wallet, pockets: List<Wallet>) {
    val colors = Broke.colors
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(parseHexColor(wallet.color) ?: colors.accent),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(wallet.name, style = MaterialTheme.typography.bodyLarge, color = colors.fg)
                Text(
                    buildString {
                        append(wallet.categoryName.ifBlank { wallet.currencyCode })
                        if (wallet.yieldRateBps != null) {
                            // basis points -> percent is presentation, not math
                            // on money: 1250 bps reads as 12.50%.
                            append(" · ${"%.2f".format(wallet.yieldRateBps / 100.0)}% anual")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.fgSubtle,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatMoney(wallet.balanceCents, wallet.currencyCode),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (wallet.balanceCents < 0) colors.danger else colors.fg,
                )
                if (wallet.reservedCents > 0) {
                    Text(
                        "${formatMoney(wallet.reservedCents, wallet.currencyCode)} apartado",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.fgSubtle,
                    )
                }
            }
        }

        pockets.forEach { pocket ->
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(22.dp))
                Text(
                    pocket.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.fgMuted,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatMoney(pocket.balanceCents, pocket.currencyCode),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.fgMuted,
                )
            }
        }
    }
}
