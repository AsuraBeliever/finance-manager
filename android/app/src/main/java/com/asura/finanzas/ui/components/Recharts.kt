package com.asura.finanzas.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asura.finanzas.ui.theme.Broke
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * The web draws every line and area chart with recharts, at its defaults: a
 * 5 px margin all round, a 60 px Y axis, a 30 px X axis, 6 px tick marks with
 * 2 px to the label, 11 px labels, a `3 3` dashed grid through every tick,
 * "nice" round ticks, `preserveEnd` thinning of the X labels and `monotone`
 * curves. This file is those rules, ported, so the phone's charts are the same
 * drawing rather than a look-alike: the plot lands on the same pixels, the
 * same ticks survive, and the curve bends the same way.
 *
 * Nothing here computes money: values arrive from finanzas-core and are only
 * mapped to pixels (in the web's unit, pesos, so the ticks come out the same).
 */

/** A value axis: its domain and the ticks drawn on it, bottom to top. */
data class NiceScale(val min: Double, val max: Double, val ticks: List<Double>)

/**
 * recharts' `getNiceTickValues` for `tickCount = 5`: a rough step rounded up
 * to a twentieth of its order of magnitude (a tenth for single digits),
 * centred on the data and grown until five ticks cover it. [fromZero] is the
 * numeric axis default `[0, 'auto']`; `["auto", "auto"]` passes false.
 */
fun niceScale(dataMin: Double, dataMax: Double, fromZero: Boolean, tickCount: Int = 5): NiceScale {
    var lo = if (fromZero) minOf(0.0, dataMin) else dataMin
    var hi = dataMax
    if (lo == hi) {
        // recharts widens a flat domain around its value.
        if (lo == 0.0) hi = 1.0 else { lo -= abs(lo) / 2; hi += abs(hi) / 2 }
    }
    fun formatStep(rough: Double, correction: Int): Double {
        if (rough <= 0) return 1.0
        val digits = floor(log10(rough)).toInt() + 1
        val digitValue = 10.0.pow(digits)
        val ratio = rough / digitValue
        val scale = if (digits != 1) 0.05 else 0.1
        return (ceil(ratio / scale) + correction) * scale * digitValue
    }
    var correction = 0
    while (true) {
        val step = formatStep((hi - lo) / (tickCount - 1), correction)
        val middle = if (lo <= 0 && hi >= 0) 0.0 else {
            val m = (lo + hi) / 2
            m - (m % step)
        }
        var below = ceil((middle - lo) / step - 1e-9).toInt()
        var up = ceil((hi - middle) / step - 1e-9).toInt()
        val count = below + up + 1
        if (count > tickCount && correction < 20) { correction++; continue }
        if (count < tickCount) {
            if (hi > 0) up += tickCount - count else below += tickCount - count
        }
        val tickMin = middle - below * step
        val ticks = (0 until tickCount).map { tickMin + it * step }
        return NiceScale(tickMin, middle + up * step, ticks)
    }
}

/**
 * recharts' `preserveEnd` interval: walking back from the last tick, keep one
 * whenever it fits inside the axis and clears the previously kept label by
 * [gap]; the last one is pulled in if it would hang off the end. Returns the
 * kept candidates and where each label is centred.
 */
fun preserveEndTicks(
    candidates: List<Int>,
    coordOf: (Int) -> Float,
    widthOf: (Int) -> Float,
    start: Float,
    end: Float,
    gap: Float,
): List<Pair<Int, Float>> {
    val kept = mutableListOf<Pair<Int, Float>>()
    var limit = end
    for (k in candidates.indices.reversed()) {
        val i = candidates[k]
        val size = widthOf(i)
        var coord = coordOf(i)
        if (k == candidates.lastIndex) {
            val over = coord + size / 2 - limit
            if (over > 0) coord -= over
        }
        if (coord - size / 2 >= start && coord + size / 2 <= limit) {
            kept += i to coord
            limit = coord - (size / 2 + gap)
        }
    }
    return kept.reversed()
}

/** d3's `curveMonotoneX` — what recharts draws for `type="monotone"`. */
fun monotonePath(points: List<Offset>, path: Path = Path(), moveFirst: Boolean = true): Path {
    if (points.isEmpty()) return path
    if (moveFirst) path.moveTo(points[0].x, points[0].y) else path.lineTo(points[0].x, points[0].y)
    if (points.size == 1) return path
    if (points.size == 2) { path.lineTo(points[1].x, points[1].y); return path }
    fun slope3(p0: Offset, p1: Offset, p2: Offset): Float {
        val h0 = p1.x - p0.x
        val h1 = p2.x - p1.x
        val s0 = if (h0 != 0f) (p1.y - p0.y) / h0 else if (h1 != 0f) 0f else 0f
        val s1 = if (h1 != 0f) (p2.y - p1.y) / h1 else 0f
        val p = (s0 * h1 + s1 * h0) / (h0 + h1)
        val r = (sign(s0) + sign(s1)) * min(min(abs(s0), abs(s1)), 0.5f * abs(p))
        return if (r.isNaN()) 0f else r
    }
    fun slope2(p0: Offset, p1: Offset, t: Float): Float {
        val h = p1.x - p0.x
        return if (h != 0f) (3 * (p1.y - p0.y) / h - t) / 2 else t
    }
    fun seg(p0: Offset, p1: Offset, t0: Float, t1: Float) {
        val dx = (p1.x - p0.x) / 3
        path.cubicTo(p0.x + dx, p0.y + dx * t0, p1.x - dx, p1.y - dx * t1, p1.x, p1.y)
    }
    val t = FloatArray(points.size)
    for (i in 1 until points.lastIndex) t[i] = slope3(points[i - 1], points[i], points[i + 1])
    t[0] = slope2(points[0], points[1], t[1])
    t[points.lastIndex] = slope2(points[points.lastIndex - 1], points.last(), t[points.lastIndex - 1])
    // d3 recomputes the first slope from the second segment's; the ends use
    // the one-sided estimate against their neighbour.
    for (i in 0 until points.lastIndex) seg(points[i], points[i + 1], t[i], t[i + 1])
    return path
}

/** The chart's colours: `chart.axis` and `chart.grid` from the web's palette. */
data class ChartChrome(val axis: Color, val grid: Color)

@Composable
fun chartChrome(): ChartChrome {
    val colors = Broke.colors
    return if (colors.isDark) {
        ChartChrome(axis = Color(0xFF6F6A8D), grid = Color.White.copy(alpha = 0.08f))
    } else {
        ChartChrome(axis = Color(0xFF9A93B5), grid = Color(0xFF7C3AED).copy(alpha = 0.12f))
    }
}

/**
 * The chart box every recharts chart on the web has: [height] tall, the plot
 * inset by the default margins and axes, grid and axes drawn, labels placed as
 * recharts places them. [plot] draws the data with the two mappers.
 */
@Composable
fun RechartsFrame(
    height: Dp,
    /** Points along the (category) X axis. */
    count: Int,
    scale: NiceScale,
    yLabel: (Double) -> String,
    /** Candidate X ticks (indices), before thinning. */
    xCandidates: List<Int>,
    xLabel: (Int) -> String,
    minTickGap: Dp,
    modifier: Modifier = Modifier,
    /** A dashed `4 4` rule at this index — the web's `ReferenceLine`. */
    referenceIndex: Int? = null,
    tooltipAt: ((Int) -> TooltipContent?)? = null,
    /** Active dots on a tapped point: value and colour of each series. */
    dotsAt: (Int) -> List<Pair<Double, Color>> = { emptyList() },
    plot: DrawScope.(xOf: (Int) -> Float, yOf: (Double) -> Float) -> Unit,
) {
    val chrome = chartChrome()
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        fontFamily = com.asura.finanzas.ui.theme.HankenGrotesk,
        fontSize = 11.sp,
        fontFeatureSettings = "ss01",
        color = chrome.axis,
    )
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val hit = rememberChartHit()
    val picked = hit.shown?.takeIf { it.index in 0 until count }
    val content = picked?.let { tooltipAt?.invoke(it.index) }

    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .then(
                if (tooltipAt == null || count < 2) {
                    Modifier
                } else {
                    Modifier.chartTap(hit, count to scale) { tap, size ->
                        val rect = plotRect(size.width.toFloat(), size.height.toFloat(), density)
                        val step = rect.width / (count - 1f)
                        ChartHit(((tap.x - rect.left) / step).roundToInt().coerceIn(0, count - 1), tap)
                    }
                },
            ),
    ) {
        Canvas(Modifier.matchParentSize()) {
            val rect = plotRect(size.width, size.height, density)
            val xOf: (Int) -> Float = { i ->
                if (count <= 1) rect.left else rect.left + rect.width * i / (count - 1f)
            }
            val span = (scale.max - scale.min).takeIf { it > 0 } ?: 1.0
            val yOf: (Double) -> Float = { v ->
                (rect.bottom - (v - scale.min) / span * rect.height).toFloat()
            }
            val px = density
            val dashed = PathEffect.dashPathEffect(floatArrayOf(3 * px, 3 * px))

            val labels = xCandidates.associateWith { measurer.measure(xLabel(it), labelStyle) }
            val shown = preserveEndTicks(
                candidates = xCandidates,
                coordOf = xOf,
                widthOf = { labels.getValue(it).size.width.toFloat() },
                start = rect.left,
                end = rect.right,
                gap = minTickGap.toPx(),
            )

            // CartesianGrid: a line through every Y tick and every shown X tick.
            scale.ticks.forEach { v ->
                val y = yOf(v)
                drawLine(chrome.grid, Offset(rect.left, y), Offset(rect.right, y), px, pathEffect = dashed)
            }
            shown.forEach { (i, _) ->
                val x = xOf(i)
                drawLine(chrome.grid, Offset(x, rect.top), Offset(x, rect.bottom), px, pathEffect = dashed)
            }

            // Axes: the line, 6 px ticks, and labels 2 px beyond them. recharts
            // hangs a label off `dy` — 0.355em to centre it on a Y tick, 0.71em
            // to sit it under an X tick — so each is placed by its baseline.
            drawLine(chrome.axis, Offset(rect.left, rect.top), Offset(rect.left, rect.bottom), px)
            drawLine(chrome.axis, Offset(rect.left, rect.bottom), Offset(rect.right, rect.bottom), px)
            val em = 11.sp.toPx()
            scale.ticks.forEach { v ->
                val y = yOf(v)
                drawLine(chrome.axis, Offset(rect.left - 6 * px, y), Offset(rect.left, y), px)
                val layout = measurer.measure(yLabel(v), labelStyle)
                val baseline = y + 0.355f * em
                drawText(
                    layout,
                    topLeft = Offset(
                        rect.left - 8 * px - layout.size.width,
                        baseline - layout.firstBaseline,
                    ),
                )
            }
            shown.forEach { (i, coord) ->
                val x = xOf(i)
                drawLine(chrome.axis, Offset(x, rect.bottom), Offset(x, rect.bottom + 6 * px), px)
                val layout = labels.getValue(i)
                val baseline = rect.bottom + 8 * px + 0.71f * em
                drawText(
                    layout,
                    topLeft = Offset(coord - layout.size.width / 2f, baseline - layout.firstBaseline),
                )
            }

            referenceIndex?.let { i ->
                val x = xOf(i)
                drawLine(
                    chrome.axis, Offset(x, rect.top), Offset(x, rect.bottom), px,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4 * px, 4 * px)),
                )
            }

            plot(xOf, yOf)

            if (picked != null && content != null) {
                val x = xOf(picked.index)
                // recharts' cursor: `#ccc`, 1 px, down the plot only.
                drawLine(Color(0xFFCCCCCC), Offset(x, rect.top), Offset(x, rect.bottom), px)
                dotsAt(picked.index).forEach { (v, color) -> activeDot(Offset(x, yOf(v)), color) }
            }
        }
        ChartTooltip(
            anchor = picked?.anchor,
            content = content,
            modifier = Modifier.matchParentSize(),
        )
    }
}

/** The plot area inside recharts' defaults: 5 px margins, 60 px Y axis, 30 px X axis. */
private fun plotRect(width: Float, height: Float, density: Float): Rect {
    val margin = 5 * density
    val left = margin + 60 * density
    return Rect(left, margin, width - margin, height - margin - 30 * density)
}

/** `(v / 1000).toFixed(digits) + "k"`, the web's tick formatter. */
fun thousandsTick(pesos: Double, digits: Int): String =
    "%.${digits}f".format(java.util.Locale.ROOT, pesos / 1000.0) + "k"

/** One `<Bar>`: its legend name, its fill and a value (pesos) per category. */
data class BarSeries(val name: String, val color: Color, val values: List<Double>)

/** A number as JavaScript prints it: "550", "0.5", never "550.0". */
fun jsNumber(v: Double): String =
    if (v == kotlin.math.floor(v) && abs(v) < 1e15) v.toLong().toString()
    else v.toBigDecimal().stripTrailingZeros().toPlainString()

/**
 * recharts' `BarChart` as the web configures it: 5 px margins, a 40 px Y axis
 * (8 px and no ticks with balances hidden), a 30 px category X axis, the
 * default `<Legend />` along the bottom — 24 px, series sorted by name, each a
 * 14 × 10.5 swatch and its name in its own colour at 16 px — and bars laid out
 * by `barCategoryGap` / `barGap` with `radius={[3, 3, 0, 0]}`. No grid.
 */
@Composable
fun RechartsBarChart(
    height: Dp,
    categories: Int,
    series: List<BarSeries>,
    xLabel: (Int) -> String,
    hideValues: Boolean,
    modifier: Modifier = Modifier,
    xFontSize: androidx.compose.ui.unit.TextUnit = 11.sp,
    xTickLine: Boolean = true,
    /** Share of each band left clear at each end: `barCategoryGap`. */
    barCategoryGap: Float = 0.1f,
    barGap: Dp = 4.dp,
    tooltipAt: ((Int) -> TooltipContent?)? = null,
) {
    val chrome = chartChrome()
    val colors = Broke.colors
    val measurer = rememberTextMeasurer()
    fun style(size: androidx.compose.ui.unit.TextUnit, color: Color) = TextStyle(
        fontFamily = com.asura.finanzas.ui.theme.HankenGrotesk,
        fontSize = size,
        fontFeatureSettings = "ss01",
        color = color,
    )
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val yWidth = if (hideValues) 8f else 40f
    val legend = remember(series) { series.sortedBy { it.name } }
    val scale = remember(series) {
        niceScale(0.0, series.flatMap { it.values }.maxOrNull() ?: 0.0, fromZero = true)
    }
    val hit = rememberChartHit()
    val picked = hit.shown?.takeIf { it.index in 0 until categories }
    val content = picked?.let { tooltipAt?.invoke(it.index) }
    val cursor = colors.borderMuted.copy(alpha = colors.borderMuted.alpha * 0.4f)

    fun plot(w: Float, h: Float): Rect {
        val m = 5 * density
        return Rect(m + yWidth * density, m, w - m, h - m - 24 * density - 30 * density)
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .then(
                if (tooltipAt == null || categories == 0) {
                    Modifier
                } else {
                    Modifier.chartTap(hit, categories to series) { tap, size ->
                        val rect = plot(size.width.toFloat(), size.height.toFloat())
                        if (tap.x < rect.left || tap.y > rect.bottom) {
                            null
                        } else {
                            val band = rect.width / categories
                            ChartHit(((tap.x - rect.left) / band).toInt().coerceIn(0, categories - 1), tap)
                        }
                    }
                },
            ),
    ) {
        Canvas(Modifier.matchParentSize()) {
            val px = density
            val rect = plot(size.width, size.height)
            val span = (scale.max - scale.min).takeIf { it > 0 } ?: 1.0
            val yOf: (Double) -> Float = { v -> (rect.bottom - (v - scale.min) / span * rect.height).toFloat() }
            val band = rect.width / categories.coerceAtLeast(1)

            picked?.let {
                drawRect(cursor, Offset(rect.left + it.index * band, rect.top), androidx.compose.ui.geometry.Size(band, rect.height))
            }

            // Bars: recharts' getBarSizeList / getBarPosition.
            val k = series.size.coerceAtLeast(1)
            val gap = barGap.toPx()
            val offset = band * barCategoryGap
            val barW = ((band - 2 * offset - (k - 1) * gap) / k / px).roundToInt().coerceAtLeast(1) * px
            val sum = k * barW + (k - 1) * gap
            val start = (band - sum) / 2
            val r = 3 * px
            for (c in 0 until categories) {
                series.forEachIndexed { s, ser ->
                    val v = ser.values.getOrNull(c) ?: 0.0
                    if (v <= 0.0) return@forEachIndexed
                    val x = rect.left + c * band + start + s * (barW + gap)
                    val top = yOf(v)
                    val hgt = rect.bottom - top
                    val rr = minOf(r, barW / 2, hgt)
                    val path = Path().apply {
                        addRoundRect(
                            androidx.compose.ui.geometry.RoundRect(
                                left = x, top = top, right = x + barW, bottom = rect.bottom,
                                topLeftCornerRadius = androidx.compose.ui.geometry.CornerRadius(rr, rr),
                                topRightCornerRadius = androidx.compose.ui.geometry.CornerRadius(rr, rr),
                            ),
                        )
                    }
                    drawPath(path, ser.color)
                }
            }

            // Axes.
            drawLine(chrome.axis, Offset(rect.left, rect.top), Offset(rect.left, rect.bottom), px)
            drawLine(chrome.axis, Offset(rect.left, rect.bottom), Offset(rect.right, rect.bottom), px)
            val yStyle = style(11.sp, chrome.axis)
            if (!hideValues) {
                val em = 11.sp.toPx()
                scale.ticks.forEach { v ->
                    val y = yOf(v)
                    drawLine(chrome.axis, Offset(rect.left - 6 * px, y), Offset(rect.left, y), px)
                    val layout = measurer.measure(jsNumber(v), yStyle)
                    drawText(
                        layout,
                        topLeft = Offset(rect.left - 8 * px - layout.size.width, y + 0.355f * em - layout.firstBaseline),
                    )
                }
            }
            val xStyle = style(xFontSize, chrome.axis)
            val xLayouts = (0 until categories).associateWith { measurer.measure(xLabel(it), xStyle) }
            val shown = preserveEndTicks(
                candidates = (0 until categories).toList(),
                coordOf = { rect.left + (it + 0.5f) * band },
                widthOf = { xLayouts.getValue(it).size.width.toFloat() },
                start = rect.left,
                end = rect.right,
                gap = 5 * px,
            )
            val xem = xFontSize.toPx()
            shown.forEach { (i, coord) ->
                val x = rect.left + (i + 0.5f) * band
                if (xTickLine) drawLine(chrome.axis, Offset(x, rect.bottom), Offset(x, rect.bottom + 6 * px), px)
                val layout = xLayouts.getValue(i)
                drawText(
                    layout,
                    topLeft = Offset(coord - layout.size.width / 2f, rect.bottom + 8 * px + 0.71f * xem - layout.firstBaseline),
                )
            }

            // Legend: centred, each item `margin-right: 10px`, the trailing one included.
            val legendTop = size.height - 5 * px - 24 * px
            val items = legend.map { it to measurer.measure(it.name, style(16.sp, it.color)) }
            val total = items.sumOf { (_, l) -> (14 * px + 4 * px + l.size.width + 10 * px).toDouble() }.toFloat()
            var x = 5 * px + (size.width - 10 * px - total) / 2
            items.forEach { (ser, layout) ->
                drawRect(ser.color, Offset(x, legendTop + 7.85f * px), androidx.compose.ui.geometry.Size(14 * px, 10.5f * px))
                drawText(layout, topLeft = Offset(x + 18 * px, legendTop + px + (21 * px - layout.size.height) / 2))
                x += 18 * px + layout.size.width + 10 * px
            }
        }
        ChartTooltip(
            anchor = picked?.anchor,
            content = content,
            modifier = Modifier.matchParentSize(),
        )
    }
}
