package com.asura.finanzas.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.asura.finanzas.ui.theme.Broke
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Tap-to-read tooltips for every chart that has one on the web — recharts'
 * `<Tooltip>`, copied from what the browser actually renders (measured, not
 * guessed):
 *
 * - The box is `chart.tooltip` from src/lib/palette.ts: its own surface, a
 *   1 px border, 12 px corners and recharts' 10 px padding, text at the 16 px
 *   a phone gets (`pointer: coarse`) on a 24 px line.
 * - A label line on top when the chart has one (a date, "2.6 Plazo (años)");
 *   none at all on the donuts and the totals chart.
 * - One `name : value` row per series, 4 px above and below, in the series'
 *   colour unless the web pins them to the text colour. Rows are sorted by
 *   series name, because that is recharts' default `itemSorter` — why the web
 *   lists "Gastos" above "Ingresos".
 * - It sits 10 px right of and below the point it reads, flips to the other
 *   side when it would spill out of the plot, and never leaves the plot's
 *   top-left corner — `getTooltipTranslateXY`.
 *
 * Values arrive already formatted (and masked, where the web masks them); no
 * money is computed here.
 */
data class TooltipItem(val name: String, val value: String, val color: Color)

data class TooltipContent(
    val label: String?,
    val items: List<TooltipItem>,
    /** The web sets `itemStyle` to the text colour on some charts. */
    val itemsInTextColor: Boolean = false,
)

/** What the finger picked: the data index, and the point the tooltip hangs off. */
data class ChartHit(val index: Int, val anchor: Offset)

@Composable
fun rememberChartHit(): MutableState<ChartHit?> = remember { mutableStateOf(null) }

/**
 * The one chart showing a tooltip. On the web, touching another chart is a
 * mouseleave for the first, so only one tooltip is ever open; this is that.
 */
private val openChart = mutableStateOf<MutableState<ChartHit?>?>(null)

/** This chart's hit, or null while another chart has the open tooltip. */
val MutableState<ChartHit?>.shown: ChartHit?
    get() = if (openChart.value === this) value else null

/**
 * Taps pick a data index through [hitTest], which gets the tap and the size of
 * the tapped element (null = nothing there). Tapping the same index again puts
 * the tooltip away; there is no hover on a phone, so the tap is the only way
 * to dismiss it. [key] restarts the detector when the data behind it changes,
 * so it never hit-tests against a stale list.
 */
fun Modifier.chartTap(
    hit: MutableState<ChartHit?>,
    key: Any?,
    hitTest: (Offset, IntSize) -> ChartHit?,
): Modifier = pointerInput(key) {
    detectTapGestures { tap ->
        val next = hitTest(tap, size)
        hit.value = if (next == null || next.index == hit.shown?.index) null else next
        if (hit.value != null) openChart.value = hit
    }
}

private const val OFFSET_DP = 10

/** recharts' tooltip box, for the current theme. */
@Composable
private fun TooltipBox(content: TooltipContent) {
    val dark = Broke.colors.isDark
    val surface = if (dark) Color(0xFF1B1830) else Color(0xFFFFFFFF)
    val border = if (dark) Color.White.copy(alpha = 0.12f) else Color(0x297C3AED)
    val text = if (dark) Color(0xFFF2EFFF) else Color(0xFF211A3A)
    // The browser keeps the half-leading above and below every line; Compose
    // trims it by default, which left the box 12 px shorter than the web's.
    val style = MaterialTheme.typography.bodyLarge.copy(
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None,
        ),
    )

    Column(
        Modifier
            .background(surface, RoundedCornerShape(12.dp))
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .padding(10.dp),
    ) {
        if (!content.label.isNullOrBlank()) {
            Text(content.label, style = style, color = text, softWrap = false)
        }
        content.items.sortedBy { it.name }.forEach { item ->
            Text(
                "${item.name} : ${item.value}",
                style = style,
                color = if (content.itemsInTextColor) text else item.color,
                softWrap = false,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
    }
}

/**
 * The tooltip itself. Put it in a `Box` whose bounds are the plot (recharts'
 * view box) with `Modifier.matchParentSize()`: it takes no room of its own and
 * may overhang that box when it is wider than the plot, as on the web.
 */
@Composable
fun ChartTooltip(anchor: Offset?, content: TooltipContent?, modifier: Modifier = Modifier) {
    if (anchor == null || content == null) return
    Layout(content = { TooltipBox(content) }, modifier = modifier) { measurables, constraints ->
        val box = measurables.first().measure(Constraints())
        val offset = OFFSET_DP.dp.roundToPx()
        fun axis(at: Float, size: Int, room: Int): Int {
            val positive = at.roundToInt() + offset
            val negative = at.roundToInt() - size - offset
            return if (positive + size > room) maxOf(negative, 0) else maxOf(positive, 0)
        }
        val x = axis(anchor.x, box.width, constraints.maxWidth)
        val y = axis(anchor.y, box.height, constraints.maxHeight)
        layout(constraints.maxWidth, constraints.maxHeight) { box.place(x, y) }
    }
}

/** The line charts' cursor: recharts' `#ccc` 1 px rule down the whole plot. */
fun DrawScope.lineCursor(x: Float) {
    drawLine(
        color = Color(0xFFCCCCCC),
        start = Offset(x, 0f),
        end = Offset(x, size.height),
        strokeWidth = 1.dp.toPx(),
    )
}

/** recharts' `activeDot`: r = 4 in the series colour, ringed 2 px in white. */
fun DrawScope.activeDot(at: Offset, color: Color) {
    drawCircle(color = color, radius = 4.dp.toPx(), center = at)
    drawCircle(color = Color.White, radius = 4.dp.toPx(), center = at, style = Stroke(2.dp.toPx()))
}

/**
 * The bar charts' cursor: the tapped band shaded with `border-muted` at 40%,
 * the web's `cursor={{ fill: "color-mix(…40%, transparent)" }}`.
 */
fun DrawScope.bandCursor(left: Float, width: Float, height: Float, borderMuted: Color) {
    drawRect(
        color = borderMuted.copy(alpha = borderMuted.alpha * 0.4f),
        topLeft = Offset(left, 0f),
        size = androidx.compose.ui.geometry.Size(width, height),
    )
}

/**
 * Which donut slice a tap landed on, laid out the way [DonutRing] draws them —
 * anticlockwise from three o'clock — and the point recharts hangs a pie's
 * tooltip from: the middle of the slice's arc, halfway through the ring, not
 * the finger.
 */
fun donutHit(
    tap: Offset,
    center: Offset,
    innerRadius: Float,
    outerRadius: Float,
    values: List<Long>,
): ChartHit? {
    val dx = tap.x - center.x
    val dy = tap.y - center.y
    val r = hypot(dx, dy)
    if (r < innerRadius || r > outerRadius) return null
    val total = values.sumOf { it.coerceAtLeast(0) }
    if (total <= 0) return null
    // Screen y grows downward; recharts' angles grow anticlockwise.
    var angle = Math.toDegrees(atan2(-dy.toDouble(), dx.toDouble()))
    if (angle < 0) angle += 360.0
    var start = 0.0
    values.forEachIndexed { index, value ->
        val sweep = 360.0 * value.coerceAtLeast(0) / total
        if (angle >= start && angle < start + sweep) {
            val mid = Math.toRadians(start + sweep / 2)
            val midR = (innerRadius + outerRadius) / 2
            return ChartHit(
                index,
                Offset(
                    center.x + (midR * cos(mid)).toFloat(),
                    center.y - (midR * sin(mid)).toFloat(),
                ),
            )
        }
        start += sweep
    }
    return null
}
