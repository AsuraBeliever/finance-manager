package com.asura.finanzas.ui.categories

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.asura.finanzas.ui.components.FormField
import com.asura.finanzas.R
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.data.NetworkException
import com.asura.finanzas.data.TransactionCategory
import com.asura.finanzas.ui.components.ColorPickerRow
import com.asura.finanzas.ui.components.FormSheet
import com.asura.finanzas.ui.components.SegmentedControl
import kotlinx.coroutines.launch

/**
 * Create or rename a category. Seeded categories are shared across users and
 * cannot be edited server-side, so the kind selector only appears when creating.
 */
@Composable
fun CategoryFormSheet(
    repository: BrokeRepository,
    existing: TransactionCategory?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var kind by remember { mutableStateOf(existing?.kind ?: "expense") }
    var color by remember { mutableStateOf(existing?.color) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val genericError = stringResource(R.string.common_error)
    val offlineError = stringResource(R.string.offline_banner)

    FormSheet(
        title = stringResource(
            if (existing == null) R.string.categories_add else R.string.categories_rename,
        ),
        busy = busy,
        error = error,
        canSave = !busy && name.isNotBlank(),
        onDismiss = onDismiss,
        onSave = {
            busy = true
            error = null
            scope.launch {
                runCatching {
                    if (existing == null) {
                        repository.createCategory(name, kind, color)
                    } else {
                        repository.updateCategory(existing.id, name, color)
                    }
                }
                    .onSuccess { onSaved() }
                    .onFailure {
                        error = if (it is NetworkException) offlineError else it.message ?: genericError
                        busy = false
                    }
            }
        },
    ) {
        FormField(
            label = stringResource(R.string.categories_add_placeholder),
            value = name,
            onValueChange = { name = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        if (existing == null) {
            SegmentedControl(
                options = listOf("expense", "income"),
                selected = kind,
                label = {
                    stringResource(
                        if (it == "income") R.string.transactions_income
                        else R.string.transactions_expense,
                    )
                },
                onSelect = { kind = it },
            )
        }
        ColorPickerRow(selected = color, onSelect = { color = it })
    }
}
