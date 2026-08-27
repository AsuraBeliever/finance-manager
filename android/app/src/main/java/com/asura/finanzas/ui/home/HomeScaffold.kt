package com.asura.finanzas.ui.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.ui.dashboard.DashboardScreen
import com.asura.finanzas.ui.settings.SettingsScreen
import com.asura.finanzas.ui.transactions.TransactionsScreen
import com.asura.finanzas.ui.theme.Broke
import com.asura.finanzas.ui.wallets.WalletsScreen

private enum class Tab(val label: String, val icon: ImageVector) {
    Dashboard("Panel", Icons.Outlined.PieChart),
    Wallets("Carteras", Icons.Outlined.AccountBalanceWallet),
    Transactions("Movimientos", Icons.AutoMirrored.Outlined.ReceiptLong),
    Settings("Ajustes", Icons.Outlined.Settings),
}

@Composable
fun HomeScaffold(
    repository: BrokeRepository,
    onSignedOut: () -> Unit,
) {
    var tab by remember { mutableStateOf(Tab.Dashboard) }
    val colors = Broke.colors

    Scaffold(
        containerColor = colors.surface,
        bottomBar = {
            NavigationBar(containerColor = colors.surfaceOverlay) {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        icon = { Icon(entry.icon, contentDescription = entry.label) },
                        label = { Text(entry.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = colors.accentBright,
                            selectedTextColor = colors.accentBright,
                            unselectedIconColor = colors.fgSubtle,
                            unselectedTextColor = colors.fgSubtle,
                            indicatorColor = Color.Transparent,
                        ),
                    )
                }
            }
        },
    ) { inner ->
        val content = Modifier.fillMaxSize().padding(inner)
        when (tab) {
            Tab.Dashboard -> DashboardScreen(repository, content)
            Tab.Wallets -> WalletsScreen(repository, content)
            Tab.Transactions -> TransactionsScreen(repository, content)
            Tab.Settings -> SettingsScreen(repository, onSignedOut, content)
        }
    }
}
