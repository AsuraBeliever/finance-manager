package com.asura.finanzas.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.asura.finanzas.ui.subscriptions.SubscriptionsScreen
import com.asura.finanzas.ui.goals.GoalsScreen
import com.asura.finanzas.ui.categories.CategoriesScreen
import com.asura.finanzas.ui.budgets.BudgetsScreen
import com.asura.finanzas.R
import com.asura.finanzas.ui.components.Lucide
import com.asura.finanzas.data.AppPreferences
import com.asura.finanzas.data.AppearanceSync
import com.asura.finanzas.data.Outbox
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.ui.components.MeshBackground
import com.asura.finanzas.ui.dashboard.DashboardScreen
import com.asura.finanzas.ui.dashboard.DashboardTarget
import com.asura.finanzas.ui.components.UpdateBanner
import com.asura.finanzas.ui.settings.WhatsNewAuto
import com.asura.finanzas.ui.investments.InvestmentsScreen
import com.asura.finanzas.ui.more.MoreDestination
import com.asura.finanzas.ui.more.MoreSheet
import com.asura.finanzas.ui.settings.SettingsScreen
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import com.asura.finanzas.ui.components.Period
import com.asura.finanzas.ui.theme.Broke
import com.asura.finanzas.ui.components.HairLine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.asura.finanzas.ui.transactions.TransactionsScreen
import com.asura.finanzas.ui.wallets.WalletsScreen

/** The six tabs the web shows in its mobile bottom bar, in the same order. */
enum class Tab(val labelRes: Int, val icon: ImageVector) {
    Dashboard(R.string.nav_dashboard, Lucide.LayoutDashboard),
    Wallets(R.string.nav_wallets, Lucide.Wallet),
    Transactions(R.string.nav_transactions, Lucide.ArrowLeftRight),
    Investments(R.string.nav_investments, Lucide.TrendingUp),
    Settings(R.string.nav_settings, Lucide.Settings),
    More(R.string.nav_more, Lucide.Ellipsis),
}

@Composable
fun HomeScaffold(
    repository: BrokeRepository,
    preferences: AppPreferences,
    appearanceSync: AppearanceSync,
    outbox: Outbox,
    onSignedOut: () -> Unit,
) {
    // Saveable: a language change recreates the activity (so dialogs, which
    // live in their own windows, pick it up too), and it must not drop you
    // back on the dashboard — the web keeps the route when it remounts.
    var tab by rememberSaveable { mutableStateOf(Tab.Dashboard) }
    var tabVisit by remember { mutableStateOf(0) }
    // A planning destination shown over the current tab, and whether the sheet
    // that offers them is open — the web navigates to a route and closes the
    // sheet, which is what these two together reproduce.
    var moreTarget by rememberSaveable { mutableStateOf<MoreDestination?>(null) }
    var moreOpen by remember { mutableStateOf(false) }
    // The dashboard's date window lives here, not inside the tab: switching
    // tabs tears the screen down, and the web remembers the choice.
    val scope = rememberCoroutineScope()
    var dashboardPeriod by remember { mutableStateOf<Period>(Period.CurrentMonth) }
    LaunchedEffect(Unit) {
        dashboardPeriod = Period.fromJson(preferences.dashboardPeriod.first())
    }

    MeshBackground {
      Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.statusBarsPadding())
            // Pops once after an update, like the web's WhatsNewAuto.
            WhatsNewAuto(preferences)
            Box(Modifier.weight(1f)) {
                // A planning page replaces the tab under it, the way the web
                // routes away from it; drawn over the top it let the dashboard
                // show through.
                if (moreTarget == null) {
                // Tapping the tab you are on goes back to its first page, the
                // way a NavLink to the route does on the web.
                key(tab, tabVisit) {
                when (tab) {
                    Tab.Dashboard -> DashboardScreen(
                        repository = repository,
                        period = dashboardPeriod,
                        onPeriodChange = { chosen ->
                            dashboardPeriod = chosen
                            scope.launch { preferences.setDashboardPeriod(chosen.toJson().toString()) }
                        },
                        onViewAll = { target ->
                            moreTarget = when (target) {
                                DashboardTarget.Budgets -> MoreDestination.Budgets
                                DashboardTarget.Goals -> MoreDestination.Goals
                                DashboardTarget.Subscriptions -> MoreDestination.Subscriptions
                            }
                        },
                    )
                    Tab.Wallets -> WalletsScreen(repository)
                    Tab.Transactions -> TransactionsScreen(repository, outbox)
                    Tab.Investments -> InvestmentsScreen(repository)
                    Tab.Settings -> SettingsScreen(
                        repository, preferences, appearanceSync, onSignedOut,
                        onOpenCategories = { moreTarget = MoreDestination.Categories },
                    )
                    Tab.More -> Unit
                }
                }
                }

                // The planning pages, reached from the sheet.
                when (moreTarget) {
                    null -> Unit
                    MoreDestination.Goals ->
                        GoalsScreen(repository, onBack = { moreTarget = null })
                    MoreDestination.Budgets ->
                        BudgetsScreen(repository, onBack = { moreTarget = null })
                    MoreDestination.Subscriptions ->
                        SubscriptionsScreen(repository, onBack = { moreTarget = null })
                    MoreDestination.Categories ->
                        CategoriesScreen(repository, onBack = { moreTarget = null })
                }

                // On top of every page, resting on the tab bar — the web's
                // `UpdateBanner bottomOnMobile`.
                UpdateBanner(repository, Modifier.align(Alignment.BottomCenter))

            }
            BottomBar(
                selected = if (moreTarget != null) Tab.More else tab,
                onSelect = {
                    if (it == Tab.More) {
                        moreOpen = true
                    } else {
                        if (it == tab && moreTarget == null) tabVisit++
                        moreTarget = null
                        tab = it
                    }
                },
            )
        }

        // `fixed inset-0` in the browser: the sheet covers the bar too.
        if (moreOpen) {
            MoreSheet(
                onDismiss = { moreOpen = false },
                onOpen = { moreOpen = false; moreTarget = it },
            )
        }
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
    // `h-[3.25rem] border-t bg-surface-raised/95 backdrop-blur`. There is no
    // cheap backdrop blur here, so the glass is laid over the page colour —
    // which is what the blur leaves behind it anyway.
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .background(colors.surfaceRaised.copy(alpha = colors.surfaceRaised.alpha * 0.95f)),
    ) {
        HairLine()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(51.dp),
        ) {
            Tab.entries.forEach { entry ->
                val active = entry == selected
                val tint = if (active) colors.accent else colors.fgSubtle
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(entry) },
                ) {
                    // `absolute top-0 h-[3px] w-8 rounded-b-full bg-gold`.
                    if (active) {
                        Box(
                            Modifier
                                .align(Alignment.TopCenter)
                                .width(32.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp))
                                .background(colors.cyan),
                        )
                    }
                    // `flex-col items-center gap-0.5 py-2 text-[10px]`.
                    Column(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            entry.icon,
                            contentDescription = stringResource(entry.labelRes),
                            tint = tint,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = stringResource(entry.labelRes),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 15.sp),
                            color = tint,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}
