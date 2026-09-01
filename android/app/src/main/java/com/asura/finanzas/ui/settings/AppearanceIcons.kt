package com.asura.finanzas.ui.settings

import androidx.compose.ui.graphics.vector.ImageVector
import com.asura.finanzas.ui.components.Lucide

/**
 * The ten brand glyphs the appearance settings offer, keyed exactly as the web
 * keys them so a choice made on either client resolves on the other. They are
 * the same lucide drawings too: the Material lookalikes that used to stand in
 * here made the picker read as a different set of icons.
 */
fun appearanceIcon(key: String): ImageVector = when (key) {
    "wallet" -> Lucide.Wallet
    "piggy-bank" -> Lucide.PiggyBank
    "coins" -> Lucide.Coins
    "landmark" -> Lucide.Landmark
    "gem" -> Lucide.Gem
    "sparkles" -> Lucide.Sparkles
    "rocket" -> Lucide.Rocket
    "leaf" -> Lucide.Leaf
    "heart" -> Lucide.Heart
    else -> Lucide.TrendingUp
}
