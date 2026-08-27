package com.asura.finanzas.ui.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.asura.finanzas.R
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.TransactionCategory
import com.asura.finanzas.ui.components.BackHeader
import com.asura.finanzas.ui.components.Dot
import com.asura.finanzas.ui.components.EmptyState
import com.asura.finanzas.ui.components.ErrorBox
import com.asura.finanzas.ui.components.GlassCard
import com.asura.finanzas.ui.components.Load
import com.asura.finanzas.ui.components.LoadingBox
import com.asura.finanzas.ui.components.MicroLabel
import com.asura.finanzas.ui.components.OfflineNotice
import com.asura.finanzas.ui.components.loadSynced
import com.asura.finanzas.ui.components.rememberReloadKey
import com.asura.finanzas.ui.parseHexColor
import com.asura.finanzas.ui.theme.Broke

@Composable
fun CategoriesScreen(
    repository: BrokeRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (key, reload) = rememberReloadKey()
    val state by loadSynced(key) { repository.manageCategories() }

    when (val current = state) {
        is Load.Loading -> LoadingBox(modifier)
        is Load.Failed -> ErrorBox(current.message, reload, modifier)
        is Load.Ready -> CategoryList(current.data, current.fromCache, onBack, modifier)
    }
}

@Composable
private fun CategoryList(
    categories: List<TransactionCategory>,
    fromCache: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = categories.filter { !it.isHidden }
    val income = visible.filter { it.kind == "income" }
    val expense = visible.filter { it.kind == "expense" }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { BackHeader(stringResource(R.string.categories_title), onBack) }

        if (fromCache) {
            item { OfflineNotice(stringResource(R.string.offline_banner), Modifier.fillMaxWidth()) }
        }

        if (visible.isEmpty()) {
            item {
                EmptyState(
                    stringResource(R.string.categories_title),
                    stringResource(R.string.categories_settings_hint),
                )
            }
        }

        if (expense.isNotEmpty()) {
            item { MicroLabel(stringResource(R.string.transactions_expense)) }
            items(expense, key = { "e-${it.id}" }) { CategoryRow(it) }
        }

        if (income.isNotEmpty()) {
            item { MicroLabel(stringResource(R.string.transactions_income), Modifier.padding(top = 8.dp)) }
            items(income, key = { "i-${it.id}" }) { CategoryRow(it) }
        }
    }
}

@Composable
private fun CategoryRow(category: TransactionCategory) {
    val colors = Broke.colors
    GlassCard(Modifier.fillMaxWidth(), padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Dot(parseHexColor(category.color) ?: colors.accent, 12.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                category.name,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.fg,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
