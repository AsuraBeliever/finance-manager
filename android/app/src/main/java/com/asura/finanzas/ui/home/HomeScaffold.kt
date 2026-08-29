package com.asura.finanzas.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asura.finanzas.R
import com.asura.finanzas.data.AppPreferences
import com.asura.finanzas.data.AppearanceSync
import com.asura.finanzas.data.Outbox
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.ui.components.MeshBackground
import com.asura.finanzas.ui.dashboard.DashboardScreen
import com.asura.finanzas.ui.dashboard.DashboardTarget
import com.asura.finanzas.ui.components.UpdateNotice
import com.asura.finanzas.ui.investments.InvestmentsScreen
import com.asura.finanzas.ui.more.MoreDestination
import com.asura.finanzas.ui.more.MoreScreen
import com.asura.finanzas.ui.settings.SettingsScreen
import com.asura.finanzas.ui.theme.Broke
import com.asura.finanzas.ui.transactions.TransactionsScreen
import com.asura.finanzas.ui.wallets.WalletsScreen

/** The six tabs the web shows in its mobile bottom bar, in the same order. */
enum class Tab(val labelRes: Int, val icon: ImageVector) {
    Dashboard(R.string.nav_dashboard, Icons.Outlined.GridView),
    Wallets(R.string.nav_wallets, Icons.Outlined.CreditCard),
    Transactions(R.string.nav_transactions, Icons.AutoMirrored.Outlined.CompareArrows),
    Investments(R.string.nav_investments, Icons.Outlined.TrendingUp),
    Settings(R.string.nav_settings, Icons.Outlined.Settings),
    More(R.string.nav_more, Icons.Outlined.MoreHoriz),
}

@Composable
fun HomeScaffold(
    repository: BrokeRepository,
    preferences: AppPreferences,
    appearanceSync: AppearanceSync,
    outbox: Outbox,
    onSignedOut: () -> Unit,
) {
    var tab by remember { mutableStateOf(Tab.Dashboard) }
    var moreTarget by remember { mutableStateOf<MoreDestination?>(null) }

    MeshBackground {
        Column(Modifier.fillMaxSize()) {
            // Sits above every tab, like the web banner does above the router.
            Box(Modifier.statusBarsPadding()) { UpdateNotice(repository) }
            Box(Modifier.weight(1f)) {
                when (tab) {
                    Tab.Dashboard -> DashboardScreen(
                        repository = repository,
                        onViewAll = { target ->
                            moreTarget = when (target) {
                                DashboardTarget.Budgets -> MoreDestination.Budgets
                                DashboardTarget.Goals -> MoreDestination.Goals
                                DashboardTarget.Subscriptions -> MoreDestination.Subscriptions
                            }
                            tab = Tab.More
                        },
                    )
                    Tab.Wallets -> WalletsScreen(repository)
                    Tab.Transactions -> TransactionsScreen(repository, outbox)
                    Tab.Investments -> InvestmentsScreen(repository)
                    Tab.Settings -> SettingsScreen(
                        repository, preferences, appearanceSync, onSignedOut,
                    )
                    Tab.More -> MoreScreen(repository, moreTarget)
                }
            }
            BottomBar(selected = tab, onSelect = { moreTarget = null; tab = it })
        }
    }
}

/**
 * Custom bar rather than Material's `NavigationBar`: the web marks the active
 * tab with a cyan rule along the *top* edge of the item and tints it violet,
 * which Material's pill indicator cannot express.
 */
@Composable
private fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    val colors = Broke.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceOverlay.copy(alpha = 0.94f))
            .navigationBarsPadding()
            .height(58.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { entry ->
            val active = entry == selected
            val tint = if (active) colors.accent else colors.fgSubtle
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(entry) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier
                        .padding(bottom = 6.dp)
                        .width(34.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (active) colors.cyan else Color.Transparent),
                )
                Icon(
                    entry.icon,
                    contentDescription = stringResource(entry.labelRes),
                    tint = tint,
                    modifier = Modifier.size(21.dp),
                )
                Text(
                    text = stringResource(entry.labelRes),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = tint,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}
