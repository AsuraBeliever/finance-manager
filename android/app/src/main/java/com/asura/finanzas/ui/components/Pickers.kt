package com.asura.finanzas.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.asura.finanzas.R
import com.asura.finanzas.ui.LocalAppSettings
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Locale

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

/**
 * Optional time of day for a movement, stored as 'HH:MM' in 24h exactly like the
 * web's `TimeInput`. The web types the digits because a native picker freezes
 * WebKitGTK; that constraint does not exist here, so this uses the platform
 * picker — but it honours the same 12/24h setting and the same "no time at all"
 * state, which is what actually has to match.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(
    label: String,
    /** 'HH:MM' (24h), or null for no time. */
    value: String?,
    onChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }
    val clock24 = LocalAppSettings.current.clock24

    OutlinedTextField(
        value = value?.let { formatClock(it, clock24) }.orEmpty(),
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        trailingIcon = {
            Row {
                if (value != null) {
                    TextButton(onClick = { onChange(null) }) {
                        Text(stringResource(R.string.common_clear))
                    }
                }
                TextButton(onClick = { showDialog = true }) {
                    Text(stringResource(R.string.transactions_time))
                }
            }
        },
    )

    if (showDialog) {
        val parsed = value?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        val state = rememberTimePickerState(
            initialHour = parsed?.hour ?: LocalTime.now().hour,
            initialMinute = parsed?.minute ?: LocalTime.now().minute,
            is24Hour = clock24,
        )
        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange(
                        "%02d:%02d".format(Locale.ROOT, state.hour, state.minute),
                    )
                    showDialog = false
                }) { Text(stringResource(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
            text = { TimePicker(state = state) },
        )
    }
}

/** 'HH:MM' rendered for the chosen clock — 24h verbatim, 12h with am/pm. */
fun formatClock(hhmm: String, clock24: Boolean): String {
    val time = runCatching { LocalTime.parse(hhmm) }.getOrNull() ?: return hhmm
    if (clock24) return hhmm
    val h12 = ((time.hour + 11) % 12) + 1
    val suffix = if (time.hour < 12) "a.m." else "p.m."
    return "%d:%02d %s".format(Locale.ROOT, h12, time.minute, suffix)
}
