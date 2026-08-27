package com.asura.finanzas.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.asura.finanzas.R
import com.asura.finanzas.ui.text
import com.asura.finanzas.ui.theme.Broke
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The date windows the web offers in `PeriodPicker`. The server resolves every
 * one of them (finanzas-core::period), so the app only names the choice — it
 * never works out what "last 3 months" means in dates.
 */
enum class PeriodChoice(val labelRes: Int) {
    CurrentMonth(R.string.dashboard_period_current_month),
    LastThreeMonths(R.string.dashboard_period_last_months),
    LastSixMonths(R.string.dashboard_period_last_months),
    AllTime(R.string.dashboard_period_all_time),
    ;

    fun toJson(): JsonObject = when (this) {
        CurrentMonth -> buildJsonObject { put("kind", "currentMonth") }
        LastThreeMonths -> buildJsonObject { put("kind", "lastMonths"); put("months", 3) }
        LastSixMonths -> buildJsonObject { put("kind", "lastMonths"); put("months", 6) }
        AllTime -> buildJsonObject { put("kind", "allTime") }
    }
}

@Composable
fun PeriodLabel(choice: PeriodChoice): String = when (choice) {
    // The dictionary interpolates with {n}, not Android's %d.
    PeriodChoice.LastThreeMonths -> text(R.string.dashboard_period_last_months_n, "n" to 3)
    PeriodChoice.LastSixMonths -> text(R.string.dashboard_period_last_months_n, "n" to 6)
    else -> stringResource(choice.labelRes)
}

@Composable
fun PeriodPickerDialog(
    selected: PeriodChoice,
    onSelect: (PeriodChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Broke.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceOverlay,
        title = { Text(stringResource(R.string.dashboard_period_label), color = colors.fg) },
        text = {
            Column {
                PeriodChoice.entries.forEach { choice ->
                    Text(
                        text = PeriodLabel(choice),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (choice == selected) colors.accent else colors.fg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(choice) }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close), color = colors.fgMuted)
            }
        },
    )
}
