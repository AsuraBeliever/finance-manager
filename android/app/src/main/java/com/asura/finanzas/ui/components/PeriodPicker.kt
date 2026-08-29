package com.asura.finanzas.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.asura.finanzas.R
import com.asura.finanzas.ui.LocalAppSettings
import com.asura.finanzas.ui.text
import com.asura.finanzas.ui.theme.Broke
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * The date windows the web offers in `PeriodPicker`, mirroring
 * `finanzas_core::period::Period` (tagged by `kind`, camelCase). The server
 * resolves every one of them, so the app only names the choice — it never works
 * out what "last 3 months" means in dates.
 */
sealed interface Period {
    data object CurrentMonth : Period
    data class LastMonths(val months: Int) : Period
    data class Month(val year: Int, val month: Int) : Period
    data class Day(val date: LocalDate) : Period
    data class Range(val from: LocalDate, val to: LocalDate) : Period
    data object AllTime : Period

    fun toJson(): JsonObject = when (this) {
        is CurrentMonth -> buildJsonObject { put("kind", "currentMonth") }
        is LastMonths -> buildJsonObject { put("kind", "lastMonths"); put("months", months) }
        is Month -> buildJsonObject { put("kind", "month"); put("year", year); put("month", month) }
        is Day -> buildJsonObject { put("kind", "day"); put("date", date.toString()) }
        is Range -> buildJsonObject {
            put("kind", "range"); put("from", from.toString()); put("to", to.toString())
        }
        is AllTime -> buildJsonObject { put("kind", "allTime") }
    }
}

/** The six modes, in the order the web lists them. */
private enum class PeriodMode { AllTime, CurrentMonth, LastMonths, Month, Day, Range }

private val Period.mode: PeriodMode
    get() = when (this) {
        is Period.AllTime -> PeriodMode.AllTime
        is Period.CurrentMonth -> PeriodMode.CurrentMonth
        is Period.LastMonths -> PeriodMode.LastMonths
        is Period.Month -> PeriodMode.Month
        is Period.Day -> PeriodMode.Day
        is Period.Range -> PeriodMode.Range
    }

/** A sensible default when switching into a mode — same choices as the web. */
private fun defaultFor(mode: PeriodMode): Period {
    val today = LocalDate.now()
    return when (mode) {
        PeriodMode.AllTime -> Period.AllTime
        PeriodMode.CurrentMonth -> Period.CurrentMonth
        PeriodMode.LastMonths -> Period.LastMonths(6)
        PeriodMode.Month -> Period.Month(today.year, today.monthValue)
        PeriodMode.Day -> Period.Day(today)
        PeriodMode.Range -> Period.Range(today.withDayOfMonth(1), today)
    }
}

private fun String.capitalizeFirst(locale: Locale): String =
    if (isEmpty()) this else substring(0, 1).uppercase(locale) + substring(1)

@Composable
private fun appLocale(): Locale = Locale.forLanguageTag(LocalAppSettings.current.locale)

@Composable
private fun monthName(year: Int, month1: Int): String {
    val locale = appLocale()
    val name = java.time.Month.of(month1).getDisplayName(TextStyle.FULL, locale)
    return "${name.capitalizeFirst(locale)} $year"
}

@Composable
private fun dayName(date: LocalDate): String {
    val locale = appLocale()
    val month = date.month.getDisplayName(TextStyle.FULL, locale)
    return "${date.dayOfMonth} ${month.capitalizeFirst(locale)} ${date.year}"
}

/** Human label for the current selection, shown on the trigger. */
@Composable
fun PeriodLabel(period: Period): String = when (period) {
    is Period.AllTime -> stringResource(R.string.dashboard_period_all_time)
    is Period.CurrentMonth -> stringResource(R.string.dashboard_period_current_month)
    is Period.LastMonths -> text(R.string.dashboard_period_last_months_n, "n" to period.months)
    is Period.Month -> monthName(period.year, period.month)
    is Period.Day -> dayName(period.date)
    is Period.Range -> "${dayName(period.from)} – ${dayName(period.to)}"
}

@Composable
private fun modeLabel(mode: PeriodMode): String = stringResource(
    when (mode) {
        PeriodMode.AllTime -> R.string.dashboard_period_all_time
        PeriodMode.CurrentMonth -> R.string.dashboard_period_current_month
        PeriodMode.LastMonths -> R.string.dashboard_period_last_months
        PeriodMode.Month -> R.string.dashboard_period_specific_month
        PeriodMode.Day -> R.string.dashboard_period_specific_day
        PeriodMode.Range -> R.string.dashboard_period_range
    },
)

/**
 * Picking a mode selects it; the parameters for the selected mode are edited
 * inline underneath, exactly like the web dropdown. Choices apply immediately,
 * so the only button is "close".
 */
@Composable
fun PeriodPickerDialog(
    selected: Period,
    onSelect: (Period) -> Unit,
    onDismiss: () -> Unit,
    /** Offer "todo el tiempo" as the first choice. */
    allowAll: Boolean = false,
) {
    val colors = Broke.colors
    val modes = PeriodMode.entries.filter { allowAll || it != PeriodMode.AllTime }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceOverlay,
        title = { Text(stringResource(R.string.dashboard_period_label), color = colors.fg) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                modes.forEach { mode ->
                    val active = selected.mode == mode
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { if (!active) onSelect(defaultFor(mode)) }
                            .padding(vertical = 12.dp),
                    ) {
                        Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                            if (active) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                        Text(
                            text = modeLabel(mode),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (active) colors.fg else colors.fgMuted,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }

                    if (active) PeriodModeEditor(selected, onSelect)
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

/** The inline parameter editor for whichever mode is currently selected. */
@Composable
private fun PeriodModeEditor(selected: Period, onSelect: (Period) -> Unit) {
    val locale = appLocale()
    when (selected) {
        is Period.LastMonths -> OutlinedTextField(
            value = selected.months.toString(),
            onValueChange = { raw ->
                val n = raw.filter { it.isDigit() }.take(2).toIntOrNull() ?: 1
                onSelect(Period.LastMonths(n.coerceIn(1, 36)))
            },
            label = { Text(stringResource(R.string.dashboard_period_months_count)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 28.dp, bottom = 8.dp),
        )

        is Period.Month -> Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 28.dp, bottom = 8.dp),
        ) {
            PickerField(
                label = stringResource(R.string.dashboard_period_specific_month),
                options = (1..12).toList(),
                selected = selected.month,
                optionLabel = { m ->
                    java.time.Month.of(m).getDisplayName(TextStyle.FULL, locale)
                        .capitalizeFirst(locale)
                },
                onSelect = { onSelect(selected.copy(month = it)) },
                modifier = Modifier.weight(1f),
            )
            // Same eight-year window the web offers.
            val years = (0..7).map { LocalDate.now().year - it }
            PickerField(
                label = stringResource(R.string.common_year),
                options = years,
                selected = selected.year,
                optionLabel = { it.toString() },
                onSelect = { onSelect(selected.copy(year = it)) },
                modifier = Modifier.weight(1f),
            )
        }

        is Period.Day -> Box(modifier = Modifier.padding(start = 28.dp, bottom = 8.dp)) {
            DateField(
                label = stringResource(R.string.dashboard_period_specific_day),
                value = selected.date,
                onChange = { onSelect(Period.Day(it)) },
            )
        }

        is Period.Range -> Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 28.dp, bottom = 8.dp),
        ) {
            DateField(
                label = stringResource(R.string.dashboard_period_from),
                value = selected.from,
                // Keep the window valid: dragging "from" past "to" pushes "to" along.
                onChange = { from ->
                    onSelect(Period.Range(from, if (selected.to < from) from else selected.to))
                },
            )
            DateField(
                label = stringResource(R.string.dashboard_period_to),
                value = selected.to,
                onChange = { to ->
                    onSelect(Period.Range(if (to < selected.from) to else selected.from, to))
                },
            )
        }

        else -> Unit
    }
}
