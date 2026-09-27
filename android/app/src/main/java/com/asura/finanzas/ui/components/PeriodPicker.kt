package com.asura.finanzas.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

    companion object {
        /**
         * Reads back what `toJson` wrote — the same shape the web keeps in
         * localStorage, so a period chosen on either surface reads the same.
         * Anything unrecognisable falls back to the current month.
         */
        fun fromJson(text: String?): Period {
            val json = text?.let {
                runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull()
            } ?: return CurrentMonth
            fun str(key: String) = json[key]?.jsonPrimitive?.contentOrNull
            fun int(key: String) = json[key]?.jsonPrimitive?.content?.toIntOrNull()
            return runCatching {
                when (str("kind")) {
                    "lastMonths" -> LastMonths(int("months") ?: 3)
                    "month" -> Month(int("year")!!, int("month")!!)
                    "day" -> Day(LocalDate.parse(str("date")!!))
                    "range" -> Range(LocalDate.parse(str("from")!!), LocalDate.parse(str("to")!!))
                    "allTime" -> AllTime
                    else -> CurrentMonth
                }
            }.getOrDefault(CurrentMonth)
        }
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

// `Intl.DateTimeFormat` with `{ month: "long", year: "numeric" }`: "septiembre
// de 2026" / "September 2026", first letter raised like the web's `cap`.
@Composable
private fun monthName(year: Int, month1: Int): String {
    val locale = appLocale()
    val pattern = if (locale.language == "en") "MMMM yyyy" else "MMMM 'de' yyyy"
    return LocalDate.of(year, month1, 1)
        .format(DateTimeFormatter.ofPattern(pattern, locale))
        .capitalizeFirst(locale)
}

// `{ day: "numeric", month: "long", year: "numeric" }`: "26 de septiembre de
// 2026" / "September 26, 2026".
@Composable
private fun dayName(date: LocalDate): String {
    val locale = appLocale()
    val pattern = if (locale.language == "en") "MMMM d, yyyy" else "d 'de' MMMM 'de' yyyy"
    return date.format(DateTimeFormatter.ofPattern(pattern, locale))
}

/** Human label for the current selection, shown on the trigger. */
@Composable
fun PeriodLabel(period: Period): String = when (period) {
    is Period.AllTime -> stringResource(R.string.dashboard_period_all_time)
    is Period.CurrentMonth -> stringResource(R.string.dashboard_period_current_month)
    is Period.LastMonths -> text(R.string.dashboard_period_last_months_n, "n" to period.months)
    is Period.Month -> monthName(period.year, period.month)
    is Period.Day -> dayName(period.date).capitalizeFirst(appLocale())
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
 * The web's `PeriodPicker`: a bordered trigger and, under it, a dropdown panel
 * anchored to its left edge. Picking a mode selects it and its parameters are
 * edited inline underneath; every change applies at once, and a tap outside
 * closes the panel — there is no title and no "close" button.
 */
@Composable
fun PeriodPicker(
    value: Period,
    onChange: (Period) -> Unit,
    modifier: Modifier = Modifier,
    /** Offer "todo el tiempo" as the first choice. */
    allowAll: Boolean = false,
) {
    val colors = Broke.colors
    var open by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    Box(modifier) {
        ChipButton(
            text = PeriodLabel(value),
            onClick = { open = !open },
            leadingIcon = Lucide.CalendarRange,
            trailingIcon = Lucide.ChevronDown,
        )
        if (open) {
            Popup(
                popupPositionProvider = remember(density) { BelowLeft(density) },
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                val modes = PeriodMode.entries.filter { allowAll || it != PeriodMode.AllTime }
                // `w-72 max-w-[calc(100vw-2rem)] rounded-xl border
                // bg-surface-overlay p-2 shadow-2xl`
                PopupShadowBox(onDismiss = { open = false }) {
                Column(
                    Modifier
                        .widthIn(max = 288.dp)
                        .fillMaxWidth()
                        .shadow2xl(12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceOverlay)
                        .border(1.dp, colors.borderMuted, RoundedCornerShape(12.dp))
                        .padding(9.dp),
                    // `space-y-0.5`
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    modes.forEach { mode ->
                        val active = value.mode == mode
                        Column {
                            // `flex gap-2 rounded-lg px-2.5 py-1.5 text-sm`
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { if (!active) onChange(defaultFor(mode)) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                                    if (active) {
                                        Icon(
                                            Lucide.Check,
                                            contentDescription = null,
                                            tint = colors.accent,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                }
                                Text(
                                    text = modeLabel(mode),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (active) colors.fg else colors.fgMuted,
                                )
                            }
                            if (active) PeriodModeEditor(value, onChange)
                        }
                    }
                }
                }
            }
        }
    }
}

/**
 * `absolute left-0 mt-2`: the panel's left edge on the trigger's, 8 dp under
 * it, kept 16 dp inside the screen (`max-w-[calc(100vw-2rem)]`).
 */
private class BelowLeft(private val density: Density) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = with(density) {
        val room = PopupShadowRoom.start.roundToPx()
        val panelWidth = popupContentSize.width - room - PopupShadowRoom.end.roundToPx()
        val x = anchorBounds.left
            .coerceAtMost(windowSize.width - panelWidth - 16.dp.roundToPx())
            .coerceAtLeast(0)
        IntOffset(x - room, anchorBounds.bottom + 8.dp.roundToPx())
    }
}

/** `text-xs text-fg-subtle mb-1` over an inline control. */
@Composable
private fun EditorLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = Broke.colors.fgSubtle,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

/** The inline parameter editor for whichever mode is currently selected. */
@Composable
private fun PeriodModeEditor(selected: Period, onSelect: (Period) -> Unit) {
    val locale = appLocale()
    // `px-2.5 pb-2 pt-1`
    val pad = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 8.dp)
    when (selected) {
        is Period.LastMonths -> Column(pad) {
            EditorLabel(stringResource(R.string.dashboard_period_months_count))
            FormField(
                label = "",
                value = selected.months.toString(),
                onValueChange = { raw ->
                    val n = raw.filter { it.isDigit() }.take(2).toIntOrNull() ?: 1
                    onSelect(Period.LastMonths(n.coerceIn(1, 36)))
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }

        is Period.Month -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = pad) {
            ButtonSelect(
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
            ButtonSelect(
                options = years,
                selected = selected.year,
                optionLabel = { it.toString() },
                onSelect = { onSelect(selected.copy(year = it)) },
                modifier = Modifier.weight(1f),
            )
        }

        is Period.Day -> Box(pad) {
            DateField(
                label = "",
                value = selected.date,
                onChange = { onSelect(Period.Day(it)) },
            )
        }

        // `flex items-end gap-2`: from and to side by side, like the web.
        is Period.Range -> Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
            modifier = pad,
        ) {
            Column(Modifier.weight(1f)) {
                EditorLabel(stringResource(R.string.dashboard_period_from))
                DateField(
                    label = "",
                    value = selected.from,
                    onChange = { from -> onSelect(selected.copy(from = from)) },
                )
            }
            Column(Modifier.weight(1f)) {
                EditorLabel(stringResource(R.string.dashboard_period_to))
                DateField(
                    label = "",
                    value = selected.to,
                    min = selected.from,
                    onChange = { to -> onSelect(selected.copy(to = to)) },
                )
            }
        }

        else -> Unit
    }
}

/**
 * The web's own `Select` component — used only here, inside the period
 * dropdown — which is not the native `<select>` [PickerField] mirrors: a
 * button in `inputClass` at `text-sm` that truncates with "…", and a popover
 * list the width of the button with a check on the chosen row.
 */
@Composable
private fun <T> ButtonSelect(
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Broke.colors
    val density = LocalDensity.current
    var open by remember { mutableStateOf(false) }
    val position = remember(density) { SelectPosition(density) }
    var width by remember { mutableStateOf(0.dp) }
    Box(
        modifier.onGloballyPositioned { c ->
            width = with(density) { c.size.width.toDp() }
            val o = c.localToScreen(Offset.Zero) - c.localToWindow(Offset.Zero)
            position.windowOrigin = IntOffset(o.x.toInt(), o.y.toInt())
        },
    ) {
        // `inputClass flex items-center justify-between gap-2`; a clicked
        // button keeps focus, so the accent ring shows while the list is open.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .focusRing(open, colors.accent)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.surface)
                .border(1.dp, if (open) colors.accent else colors.borderMuted, RoundedCornerShape(8.dp))
                .clickable { open = !open }
                .padding(horizontal = 13.dp, vertical = 9.dp),
        ) {
            Text(
                optionLabel(selected),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(Lucide.ChevronDown, null, tint = colors.fgSubtle, modifier = Modifier.size(15.dp))
        }
        if (open) {
            Popup(
                popupPositionProvider = position,
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                PopupShadowBox(onDismiss = { open = false }) {
                    // `max-h-64 min-w-[7rem] overflow-y-auto rounded-xl border
                    // bg-surface-overlay p-1 shadow-2xl`, as wide as the button.
                    Column(
                        Modifier
                            .width(maxOf(width, 112.dp))
                            .heightIn(max = 256.dp)
                            .shadow2xl(12.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surfaceOverlay)
                            .border(1.dp, colors.borderMuted, RoundedCornerShape(12.dp))
                            .verticalScroll(rememberScrollState())
                            .padding(5.dp),
                    ) {
                        options.forEach { option ->
                            val active = option == selected
                            // `px-2.5 py-2 text-sm gap-2 rounded-lg`
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (active) colors.surfaceRaised else Color.Transparent)
                                    .clickable { onSelect(option); open = false }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                            ) {
                                Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                                    if (active) {
                                        Icon(Lucide.Check, null, tint = colors.accent, modifier = Modifier.size(14.dp))
                                    }
                                }
                                Text(
                                    optionLabel(option),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (active) colors.fg else colors.fgMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * `Select`'s `reposition()`: left-aligned to the button and kept 8 px inside
 * the screen, 4 px under it, flipped above when it does not fit. The anchor is
 * moved to screen space first — this select lives inside another popup.
 */
private class SelectPosition(private val density: Density) : PopupPositionProvider {
    var windowOrigin = IntOffset.Zero

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = with(density) {
        val a = anchorBounds.translate(windowOrigin)
        val roomStart = PopupShadowRoom.start.roundToPx()
        val w = popupContentSize.width - roomStart - PopupShadowRoom.end.roundToPx()
        val h = popupContentSize.height - PopupShadowRoom.bottom.roundToPx()
        val edge = 8.dp.roundToPx()
        val left = maxOf(edge, minOf(a.left, windowSize.width - w - edge))
        val below = a.bottom + 4.dp.roundToPx()
        val top = if (below + h > windowSize.height - edge && a.top - h - edge > edge) {
            a.top - h - edge
        } else {
            below
        }
        IntOffset(left - roomStart, top)
    }
}
