package com.asura.finanzas.ui.budgets

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.asura.finanzas.R
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.Budget
import com.asura.finanzas.data.NetworkException
import com.asura.finanzas.data.TransactionCategory
import com.asura.finanzas.ui.components.FormSheet
import com.asura.finanzas.ui.components.PickerField
import com.asura.finanzas.ui.formatMoney
import com.asura.finanzas.ui.parseAmountToCents
import kotlinx.coroutines.launch

/**
 * Set a spending limit. `set_budget` is an upsert keyed by category, so editing
 * and creating are the same call — a null category is the overall budget.
 */
@Composable
fun BudgetFormSheet(
    repository: BrokeRepository,
    existing: Budget?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var categories by remember { mutableStateOf<List<TransactionCategory>>(emptyList()) }
    var category by remember { mutableStateOf<TransactionCategory?>(null) }
    var limit by remember {
        mutableStateOf(existing?.limitCents?.let { formatMoney(it, withSymbol = false) }.orEmpty())
    }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val genericError = stringResource(R.string.common_error)
    val offlineError = stringResource(R.string.offline_banner)
    val overallLabel = stringResource(R.string.budgets_overall)

    LaunchedEffect(Unit) {
        categories = runCatching { repository.transactionCategories("expense") }
            .getOrDefault(emptyList())
        category = categories.firstOrNull { it.id == existing?.categoryId }
    }

    val cents = parseAmountToCents(limit)
    val canSave = !busy && cents != null && cents > 0

    FormSheet(
        title = stringResource(R.string.budgets_new_budget),
        busy = busy,
        error = error,
        canSave = canSave,
        onDismiss = onDismiss,
        onSave = {
            busy = true
            error = null
            scope.launch {
                runCatching { repository.setBudget(category?.id, cents ?: 0) }
                    .onSuccess { onSaved() }
                    .onFailure {
                        error = if (it is NetworkException) offlineError else it.message ?: genericError
                        busy = false
                    }
            }
        },
    ) {
        PickerField(
            label = stringResource(R.string.budgets_category),
            options = listOf<TransactionCategory?>(null) + categories,
            selected = category,
            optionLabel = { it?.name ?: overallLabel },
            onSelect = { category = it },
            // Editing an existing budget keeps its category: the upsert is
            // keyed by it, so changing it would create a second budget.
            enabled = existing == null,
            emptyLabel = overallLabel,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = limit,
            onValueChange = { limit = it; error = null },
            label = { Text(stringResource(R.string.budgets_limit)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
