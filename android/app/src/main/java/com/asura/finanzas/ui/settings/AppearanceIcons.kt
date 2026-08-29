package com.asura.finanzas.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Rocket
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The ten brand glyphs the appearance settings offer. The web draws them with
 * lucide; these are the closest Material equivalents, keyed identically so a
 * choice made on either client resolves on the other.
 */
fun appearanceIcon(key: String): ImageVector = when (key) {
    "wallet" -> Icons.Outlined.AccountBalanceWallet
    "piggy-bank" -> Icons.Outlined.Savings
    "coins" -> Icons.Outlined.Payments
    "landmark" -> Icons.Outlined.AccountBalance
    "gem" -> Icons.Outlined.Diamond
    "sparkles" -> Icons.Outlined.AutoAwesome
    "rocket" -> Icons.Outlined.Rocket
    "leaf" -> Icons.Outlined.Spa
    "heart" -> Icons.Outlined.Favorite
    else -> Icons.Outlined.TrendingUp
}
