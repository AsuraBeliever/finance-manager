package com.asura.finanzas.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.asura.finanzas.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Labelled single-choice dropdown, the native stand-in for the web's `Select`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> PickerField(
    label: String,
    options: List<T>,
    selected: T?,
    // @Composable so callers can label options from string resources.
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Shown when nothing is selected — e.g. "All wallets" for a filter. */
    emptyLabel: String = "",
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = if (selected != null) optionLabel(selected) else emptyLabel,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * Business dates are 'YYYY-MM-DD' with no timezone, so the picker is read in UTC
 * — converting through the device zone could shift the day across midnight.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    value: LocalDate,
    onChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value.toString(),
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        trailingIcon = {
            TextButton(onClick = { showDialog = true }) {
                Text(stringResource(R.string.common_pick_date))
            }
        },
    )

    if (showDialog) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = value.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDialog = false
                }) { Text(stringResource(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        ) {
            DatePicker(state = state)
        }
    }
}
