package com.asura.finanzas.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.asura.finanzas.R
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.ui.budgets.BudgetsScreen
import com.asura.finanzas.ui.categories.CategoriesScreen
import com.asura.finanzas.ui.components.GlassCard
import com.asura.finanzas.ui.components.PageHeader
import com.asura.finanzas.ui.goals.GoalsScreen
import com.asura.finanzas.ui.subscriptions.SubscriptionsScreen
import com.asura.finanzas.ui.theme.Broke

/**
 * The web keeps five items in the mobile bar and folds planning behind "More".
 * Same split here, so someone moving between the two apps finds things in the
 * place they already learned.
 */
private enum class MoreDestination(val labelRes: Int, val icon: ImageVector) {
    Goals(R.string.nav_goals, Icons.Outlined.Savings),
    Budgets(R.string.nav_budgets, Icons.Outlined.TrackChanges),
    Subscriptions(R.string.nav_subscriptions, Icons.Outlined.Repeat),
    Categories(R.string.categories_title, Icons.Outlined.Category),
}

@Composable
fun MoreScreen(repository: BrokeRepository, modifier: Modifier = Modifier) {
    var destination by remember { mutableStateOf<MoreDestination?>(null) }

    when (destination) {
        null -> MoreMenu(modifier) { destination = it }
        MoreDestination.Goals -> GoalsScreen(repository, onBack = { destination = null }, modifier = modifier)
        MoreDestination.Budgets -> BudgetsScreen(repository, onBack = { destination = null }, modifier = modifier)
        MoreDestination.Subscriptions ->
            SubscriptionsScreen(repository, onBack = { destination = null }, modifier = modifier)
        MoreDestination.Categories ->
            CategoriesScreen(repository, onBack = { destination = null }, modifier = modifier)
    }
}

@Composable
private fun MoreMenu(modifier: Modifier, onOpen: (MoreDestination) -> Unit) {
    val colors = Broke.colors
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageHeader(stringResource(R.string.nav_planning)) }

        items(MoreDestination.entries.size) { index ->
            val entry = MoreDestination.entries[index]
            GlassCard(
                Modifier.fillMaxWidth().clickable { onOpen(entry) },
                padding = 18.dp,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        entry.icon,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        stringResource(entry.labelRes),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.fg,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = colors.fgSubtle,
                    )
                }
            }
        }
    }
}
