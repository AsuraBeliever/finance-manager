package com.asura.finanzas.ui.components

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
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
    /**
     * The web's `<optgroup>`: the heading an option belongs under, or null for
     * an ungrouped one. A heading is drawn when it changes from the option
     * above, so the list must already be sorted by group.
     */
    optionGroup: (@Composable (T) -> String?)? = null,
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
                    // `px-3 py-2` plus the 1 px border the browser counts
                    // inside the box: 13 × 9 from the edge to the text.
                    .padding(horizontal = 13.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (selected != null) optionLabel(selected) else emptyLabel,
                    // The closed `<select>`, so the same 16 sp as an input.
                    style = MaterialTheme.typography.bodyMedium
                        .copy(fontSize = ControlFontSize, lineHeight = ControlLineHeight),
                    color = if (enabled) colors.fg else colors.fgSubtle,
                    maxLines = 1,
                    softWrap = false,
                    // A closed `<select>` clips its text at the chevron; no "…".
                    overflow = TextOverflow.Clip,
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
                var lastGroup: String? = null
                options.forEach { option ->
                    val group = optionGroup?.invoke(option)
                    if (group != null && group != lastGroup) {
                        Text(
                            group,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                            color = colors.fgSubtle,
                            modifier = Modifier.padding(
                                start = 12.dp,
                                end = 12.dp,
                                top = 10.dp,
                                bottom = 4.dp,
                            ),
                        )
                    }
                    lastGroup = group
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
 * The web's `DateInput`, not Material's date dialog: a box with the date
 * spelled out and a calendar glyph, and under it the app's own calendar card
 * (w-72, Monday first, a month/year grid behind the title, "Hoy · 26 sep").
 * Business dates are 'YYYY-MM-DD' with no timezone, so nothing here goes
 * through a zone.
 */
@Composable
fun DateField(
    label: String,
    value: LocalDate,
    onChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    /** Earliest selectable date, inclusive — the web's `min`. */
    min: LocalDate? = null,
) {
    var open by remember { mutableStateOf(false) }
    val lang = LocalAppSettings.current.locale.take(2)
    val locale = java.util.Locale.forLanguageTag(if (lang == "en") "en-US" else "es-MX")

    // Spelled out exactly as the web's DateInput writes it. `FormatStyle.LONG`
    // is close but not the same string in Spanish ("31 de agosto de 2026").
    val shown = remember(value, locale) {
        val pattern = if (lang == "en") "MMMM d, yyyy" else "d 'de' MMMM yyyy"
        runCatching {
            value.format(DateTimeFormatter.ofPattern(pattern, locale))
        }.getOrDefault(value.toString())
    }
    Box(modifier) {
        Box(Modifier.clickable { open = !open }) {
            FormField(
                label = label,
                value = shown,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                enabled = false,
                readOnly = true,
                // Not typeable, but not greyed either: the picker fills it in.
                valueColor = Broke.colors.fg,
                // The button keeps focus while its calendar is open.
                highlighted = open,
                // A `<button>` on the web, so it keeps `text-sm`.
                fontSize = ButtonControlFontSize,
                // …and a button's text wraps: in a half-width box the web breaks
                // "26 de septiembre 2026" onto two lines instead of cutting it.
                singleLine = false,
                trailing = {
                    Icon(
                        Lucide.Calendar,
                        contentDescription = null,
                        tint = Broke.colors.fgSubtle,
                        modifier = Modifier.size(15.dp),
                    )
                },
            )
        }
        if (open) {
            androidx.compose.ui.window.Popup(
                popupPositionProvider = remember { CalendarPosition() },
                onDismissRequest = { open = false },
                properties = androidx.compose.ui.window.PopupProperties(focusable = true),
            ) {
                CalendarCard(
                    value = value,
                    min = min,
                    lang = lang,
                    locale = locale,
                    onPick = { onChange(it); open = false },
                )
            }
        }
    }
}

/**
 * Where the web puts the calendar: right-aligned to the box, 8 px under it,
 * flipped above when it does not fit below, and kept 8 px inside the screen.
 */
private class CalendarPosition : androidx.compose.ui.window.PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: androidx.compose.ui.unit.IntRect,
        windowSize: androidx.compose.ui.unit.IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        popupContentSize: androidx.compose.ui.unit.IntSize,
    ): androidx.compose.ui.unit.IntOffset {
        // The anchor is the whole field, label included; the web anchors to
        // the box, which ends at the same bottom edge and right edge.
        val gap = (popupContentSize.width / 36f).toInt() // 8 of the card's 288
        val w = popupContentSize.width
        val h = popupContentSize.height
        val left = maxOf(gap, minOf(anchorBounds.right - w, windowSize.width - w - gap))
        val below = anchorBounds.bottom + gap
        val top = if (below + h > windowSize.height - gap && anchorBounds.top - h - gap > gap) {
            anchorBounds.top - h - gap
        } else {
            below
        }
        return androidx.compose.ui.unit.IntOffset(left, top)
    }
}

// The web's own tables, not the JDK's: Java spells September "sept." in
// Spanish, and the calendar has to read the same on both.
private val MONTHS_SHORT = mapOf(
    "es" to listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"),
    "en" to listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"),
)
private val WEEKDAYS = mapOf(
    "es" to listOf("L", "M", "M", "J", "V", "S", "D"),
    "en" to listOf("M", "T", "W", "T", "F", "S", "S"),
)
private const val YEAR_FROM = 1970

@Composable
private fun CalendarCard(
    value: LocalDate,
    min: LocalDate?,
    lang: String,
    locale: Locale,
    onPick: (LocalDate) -> Unit,
) {
    val colors = Broke.colors
    var view by remember { mutableStateOf(value.withDayOfMonth(1)) }
    var months by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val shortMonths = MONTHS_SHORT[lang] ?: MONTHS_SHORT.getValue("es")

    Column(
        Modifier
            .width(288.dp)
            .shadow(24.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceOverlay)
            .border(1.dp, colors.borderMuted, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        if (months) {
            var yearText by remember(view.year) { mutableStateOf(view.year.toString()) }
            fun stepYear(delta: Int) {
                val y = (view.year + delta).coerceIn(YEAR_FROM, today.year + 100)
                view = view.withYear(y)
            }
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CalendarArrow(Lucide.ChevronLeft, stringResource(R.string.common_prev_year)) { stepYear(-1) }
                BasicTextField(
                    value = yearText,
                    onValueChange = { raw ->
                        val t = raw.filter { it.isDigit() }.take(4)
                        yearText = t
                        if (t.length == 4 && t.toInt() >= YEAR_FROM) view = view.withYear(t.toInt())
                    },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        fontFeatureSettings = "ss01, tnum",
                        color = colors.fg,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.accent),
                    modifier = Modifier
                        .width(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.borderMuted, RoundedCornerShape(8.dp))
                        .padding(vertical = 4.dp),
                )
                CalendarArrow(Lucide.ChevronRight, stringResource(R.string.common_next_year)) { stepYear(1) }
            }
            shortMonths.chunked(3).forEachIndexed { row, names ->
                Row(
                    Modifier.fillMaxWidth().padding(top = if (row == 0) 0.dp else 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    names.forEachIndexed { col, name ->
                        val month = row * 3 + col + 1
                        val isCurrent = month == view.monthValue && view.year == value.year
                        val lastDay = LocalDate.of(view.year, month, 1).plusMonths(1).minusDays(1)
                        val disabled = min != null && lastDay < min
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isCurrent && !disabled) colors.accentDim.copy(alpha = 0.15f)
                                    else androidx.compose.ui.graphics.Color.Transparent,
                                )
                                .clickable(enabled = !disabled) {
                                    view = LocalDate.of(view.year, month, 1)
                                    months = false
                                }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                name.replaceFirstChar { it.uppercase(locale) },
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp,
                                    fontWeight = if (isCurrent && !disabled) FontWeight.SemiBold else FontWeight.Normal,
                                ),
                                color = when {
                                    disabled -> colors.fgSubtle
                                    isCurrent -> colors.accent
                                    else -> colors.fg
                                },
                            )
                        }
                    }
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CalendarArrow(Lucide.ChevronLeft, null) { view = view.minusMonths(1) }
                val title = view.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
                    .replaceFirstChar { it.uppercase(locale) } + " ▾"
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = colors.fg,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { months = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
                CalendarArrow(Lucide.ChevronRight, null) { view = view.plusMonths(1) }
            }
            val weekdays = WEEKDAYS[lang] ?: WEEKDAYS.getValue("es")
            // Monday-first column of the 1st of the viewed month.
            val blanks = view.dayOfWeek.value - 1
            val cells: List<Int?> = List(blanks) { null } + (1..view.lengthOfMonth()).toList()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                weekdays.forEach { d ->
                    Text(
                        d,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        ),
                        color = colors.fgSubtle,
                        modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                    )
                }
            }
            cells.chunked(7).forEach { week ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for (i in 0 until 7) {
                        val day = week.getOrNull(i)
                        if (day == null) {
                            Spacer(Modifier.weight(1f))
                            continue
                        }
                        val date = view.withDayOfMonth(day)
                        val disabled = min != null && date < min
                        val isSelected = date == value
                        val isToday = date == today
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) colors.accentDim
                                    else androidx.compose.ui.graphics.Color.Transparent,
                                )
                                .clickable(enabled = !disabled) { onPick(date) }
                                // py-1.5 plus the transparent 1 px border.
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                day.toString(),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    fontFeatureSettings = "ss01, tnum",
                                    fontWeight = if (isSelected || (isToday && !disabled)) {
                                        FontWeight.SemiBold
                                    } else {
                                        FontWeight.Normal
                                    },
                                ),
                                color = when {
                                    isSelected -> colors.surface
                                    disabled -> colors.fgSubtle
                                    isToday -> colors.accent
                                    else -> colors.fg
                                },
                            )
                        }
                    }
                }
            }
            val todayLabel = stringResource(R.string.common_today) + " · " +
                today.dayOfMonth + " " + shortMonths[today.monthValue - 1]
            Text(
                todayLabel,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                ),
                color = colors.accent,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        if (min == null || today >= min) onPick(today)
                        else view = min.withDayOfMonth(1)
                    }
                    .padding(vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun CalendarArrow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String?,
    onClick: () -> Unit,
) {
    Icon(
        icon,
        contentDescription = description,
        tint = Broke.colors.fgMuted,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(6.dp)
            .size(16.dp),
    )
}

/**
 * Optional time of day for a movement, stored as 'HH:MM' in 24h — the web's
 * `TimeInput`, keystroke for keystroke: a clock glyph, a box you type the
 * digits into ("1830", "6:30"), and on a 12-hour clock an AM/PM chip that
 * flips the meridiem. Emptying the box means "no time".
 */
@Composable
fun TimeField(
    label: String,
    /** 'HH:MM' (24h), or null for no time. */
    value: String?,
    onChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Broke.colors
    val clock24 = LocalAppSettings.current.clock24
    val (draftText, meridiem) = timeDraft(value.orEmpty(), clock24)
    var focused by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    val shown = if (focused) text else draftText

    fun commit(next: String, m: String) {
        onChange(parseTime(next, clock24, m).ifEmpty { null })
    }

    FormField(
        label = label,
        value = shown,
        onValueChange = { raw ->
            val next = raw.filter { it.isDigit() || it == ':' }.take(5)
            text = next
            commit(next, meridiem)
        },
        modifier = modifier.onFocusChanged { state ->
            if (state.hasFocus && !focused) {
                text = draftText
                focused = true
            } else if (!state.hasFocus && focused) {
                focused = false
                // Normalise what was typed to the canonical form.
                commit(text, meridiem)
            }
        },
        placeholder = if (clock24) "--:--" else "-:--",
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
        ),
        leading = {
            Icon(
                Lucide.Clock,
                contentDescription = null,
                tint = colors.fgSubtle,
                modifier = Modifier.size(15.dp),
            )
        },
        trailing = {
            if (!clock24) {
                Text(
                    meridiem,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = "ss01, tnum",
                    ),
                    color = colors.fg,
                    modifier = Modifier
                        .shadow(1.dp, RoundedCornerShape(6.dp))
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.surface)
                        // `border-border` is not a colour in the web's theme, so
                        // Tailwind falls back to `currentColor`: the text's own.
                        .border(1.dp, colors.fg, RoundedCornerShape(6.dp))
                        .clickable {
                            val next = if (meridiem == "AM") "PM" else "AM"
                            if (!value.isNullOrEmpty()) commit(if (focused) text else draftText, next)
                        }
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        },
    )
}

/** What the box shows for a stored 'HH:MM' on the given clock — the web's `toDraft`. */
private fun timeDraft(value: String, clock24: Boolean): Pair<String, String> {
    val parts = value.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull()
    val min = parts.getOrNull(1)?.toIntOrNull()
    if (value.isEmpty() || hour == null || min == null) return "" to "AM"
    val mm = min.toString().padStart(2, '0')
    if (clock24) return "${hour.toString().padStart(2, '0')}:$mm" to "AM"
    val h12 = ((hour + 11) % 12) + 1
    return "$h12:$mm" to if (hour < 12) "AM" else "PM"
}

/**
 * Whatever was typed, as a 24h 'HH:MM' ("" when blank) — the web's `parse`.
 * A colon anchors the split; without one the last two digits are the minute.
 */
private fun parseTime(text: String, clock24: Boolean, meridiem: String): String {
    val hourDigits: String
    val minDigits: String
    if (':' in text) {
        val left = text.substringBefore(':')
        val right = text.substringAfter(':')
        hourDigits = left.filter { it.isDigit() }
        minDigits = right.filter { it.isDigit() }
    } else {
        val digits = text.filter { it.isDigit() }.take(4)
        if (digits.length <= 2) {
            hourDigits = digits; minDigits = ""
        } else {
            hourDigits = digits.dropLast(2); minDigits = digits.takeLast(2)
        }
    }
    if (hourDigits.isEmpty() && minDigits.isEmpty()) return ""
    var hour = hourDigits.take(2).ifEmpty { "0" }.toInt()
    val min = minOf(59, minDigits.take(2).ifEmpty { "0" }.toInt())
    if (clock24) {
        hour = minOf(23, hour)
    } else {
        hour = hour.coerceIn(1, 12) % 12
        if (meridiem == "PM") hour += 12
    }
    return "%02d:%02d".format(Locale.ROOT, hour, min)
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
