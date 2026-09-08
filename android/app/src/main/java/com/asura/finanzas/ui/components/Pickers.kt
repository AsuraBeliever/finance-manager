package com.asura.finanzas.ui.components

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asura.finanzas.data.Wallet
import com.asura.finanzas.ui.theme.Broke
import com.asura.finanzas.R
import com.asura.finanzas.ui.LocalAppSettings
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Locale

/**
 * Single-choice dropdown, drawn as the web's `<select>` rather than a Material
 * text field: a plain bordered box with the label sitting above it. Material's
 * floating label and filled underline are an instantly recognisable "this is
 * the Android one" tell, which is exactly what this app must not have.
 *
 * Pass a blank `label` where the web shows the control on its own (the filter
 * bars), and the row above is skipped.
 */
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
    val colors = Broke.colors

    Column(modifier) {
        if (label.isNotBlank()) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.8.sp),
                color = colors.fgMuted,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        ExposedDropdownMenuBox(
            expanded = expanded && enabled,
            onExpandedChange = { if (enabled) expanded = it },
        ) {
            Row(
                modifier = Modifier
                    .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.borderMuted, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (selected != null) optionLabel(selected) else emptyLabel,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = if (enabled) colors.fg else colors.fgSubtle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Lucide.ChevronDown,
                    contentDescription = null,
                    tint = colors.fgSubtle,
                    modifier = Modifier.size(16.dp),
                )
            }
            ExposedDropdownMenu(
                expanded = expanded && enabled,
                onDismissRequest = { expanded = false },
                containerColor = colors.surfaceOverlay,
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                optionLabel(option),
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                color = colors.fg,
                            )
                        },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        },
                    )
                }
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
    val locale = java.util.Locale.forLanguageTag(LocalAppSettings.current.locale)

    // The web shows the date spelled out with a calendar glyph inside the box,
    // and the whole box opens the picker — not a "Pick date" text button.
    // Spelled out exactly as the web's DateInput writes it. `FormatStyle.LONG`
    // is close but not the same string in Spanish ("31 de agosto de 2026"),
    // and the two sat side by side in the same account.
    val shown = remember(value, locale) {
        val pattern = if (locale.language == "en") "MMMM d, yyyy" else "d 'de' MMMM yyyy"
        runCatching {
            value.format(DateTimeFormatter.ofPattern(pattern, locale))
        }.getOrDefault(value.toString())
    }
    Box(modifier.clickable { showDialog = true }) {
        FormField(
            label = label,
            value = shown,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            enabled = false,
            readOnly = true,
            trailing = {
                Icon(
                    Lucide.Calendar,
                    contentDescription = null,
                    tint = Broke.colors.fgMuted,
                    modifier = Modifier.size(16.dp),
                )
            },
        )
    }

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

    // The web's TimeInput: clock glyph, the bare time, and — on a 12-hour
    // clock — a small AM/PM chip that toggles the meridiem. The time itself
    // carries no suffix; the chip is the suffix.
    val parsedNow = value?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
    val meridiem = if ((parsedNow?.hour ?: 0) < 12) "AM" else "PM"
    Box(modifier.clickable { showDialog = true }) {
        FormField(
            label = label,
            value = value?.let { bareClock(it, clock24) }.orEmpty(),
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            enabled = false,
            readOnly = true,
            leading = {
                Icon(
                    Lucide.Clock,
                    contentDescription = null,
                    tint = Broke.colors.fgSubtle,
                    modifier = Modifier.size(15.dp),
                )
            },
            trailing = {
                if (!clock24 && parsedNow != null) {
                    Text(
                        meridiem,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = Broke.colors.fg,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Broke.colors.surface)
                            .border(1.dp, Broke.colors.borderMuted, RoundedCornerShape(6.dp))
                            .clickable {
                                val shifted = if (parsedNow.hour < 12) {
                                    parsedNow.plusHours(12)
                                } else {
                                    parsedNow.minusHours(12)
                                }
                                onChange("%02d:%02d".format(Locale.ROOT, shifted.hour, shifted.minute))
                            }
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            },
        )
    }

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
                // Clearing lives here now: the box itself has no x, because the
                // web's has none either — there you just empty the text.
                TextButton(onClick = { onChange(null); showDialog = false }) {
                    Text(stringResource(R.string.common_clear))
                }
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
            text = { TimePicker(state = state) },
        )
    }
}

/**
 * 'HH:MM' for the chosen clock with no meridiem: 24h verbatim, 12h as "h:mm".
 * The web's box shows the suffix on its own AM/PM chip, not in the text.
 */
fun bareClock(hhmm: String, clock24: Boolean): String {
    val time = runCatching { LocalTime.parse(hhmm) }.getOrNull() ?: return hhmm
    if (clock24) return hhmm
    val h12 = ((time.hour + 11) % 12) + 1
    return "%d:%02d".format(Locale.ROOT, h12, time.minute)
}

/**
 * How a wallet reads inside a picker: `Parent › Name (CODE)`, the web's
 * `walletLabel`. A pocket and the wallet next to it in the list are otherwise
 * easy to mix up, and picking the wrong one sends real money somewhere else.
 */
fun walletLabel(wallet: Wallet, all: List<Wallet>): String {
    val parent = wallet.parentWalletId?.let { id -> all.firstOrNull { it.id == id }?.name }
    return (if (parent != null) "$parent › " else "") + "${wallet.name} (${wallet.currencyCode})"
}
