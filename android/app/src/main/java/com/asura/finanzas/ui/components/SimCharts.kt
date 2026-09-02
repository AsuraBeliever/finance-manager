package com.asura.finanzas.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asura.finanzas.ui.theme.Broke
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * The simulator's two charts. The web draws them with recharts, so the
 * furniture is copied from it: a dashed `3 3` grid, 2 px strokes, a y-axis
 * labelled in thousands, and one x tick per year.
 *
 * Unlike [LineChart] — which spans min..max so a slow-growing balance still
 * reads as a curve — these start the vertical scale at **zero**, which is
 * recharts' default domain for a numeric axis. The stacked bands and the
 * compared curves have to keep their real proportions: the whole point is
 * seeing how much of the total is interest, or how far apart two rates land.
 *
 * No money is computed here. Every cent arrives from `finanzas-core`; this
 * only maps it to pixels.
 */
private val SIM_PLOT_HEIGHT = 240.dp

/** Five labels down the axis, matching recharts' default tick count. */
private const val Y_ROWS = 4

/** One curve of the comparison chart. */
data class SimSeries(val name: String, val color: Color, val values: List<Long>)

/**
 * Rounds the top of the scale up so every tick lands on a round number — what
 * recharts' "auto" domain does, and what turns "95k · 71k · 48k" into
 * "100k · 75k · 50k".
 *
 * The rule is recharts': divide the data into [Y_ROWS] intervals, then round
 * that rough step up to the next half of its own magnitude. A step of 23.5k
 * becomes 25k, one of 27.4k becomes 30k.
 */
private fun niceMax(max: Long): Long {
    if (max <= 0) return 1
    val rough = max.toDouble() / Y_ROWS
    val half = 10.0.pow(floor(log10(rough))) / 2
    return (ceil(rough / half) * half * Y_ROWS).roundToLong().coerceAtLeast(1)
}

/** Cents to the web's `(v / 1000).toFixed(0) + "k"` tick label. */
private fun thousandsLabel(cents: Long): String = "${(cents / 100_000.0).roundToInt()}k"

/**
 * A year tick every 12 months, thinned out until the labels stop crowding —
 * recharts' `minTickGap`.
 */
private fun yearTicks(months: Int): List<Pair<String, Float>> {
    if (months <= 0) return emptyList()
    val years = months / 12
    if (years < 1) return emptyList()
    val stride = ceil(years / 6.0).toInt().coerceAtLeast(1)
    return (stride..years step stride).map { y ->
        "${y}a" to (y * 12f / months)
    }
}

/**
 * The grid, axes and labels shared by both charts. [plot] receives the plot
 * size and a mapper from cents to a y pixel, and draws the data on top.
 */
@Composable
private fun ChartFrame(
    maxCents: Long,
    xLabels: List<Pair<String, Float>>,
    modifier: Modifier = Modifier,
    plot: DrawScope.(Float, Float, (Long) -> Float) -> Unit,
) {
    val colors = Broke.colors
    val grid = colors.borderMuted
    val top = niceMax(maxCents)
    val ticks = (Y_ROWS downTo 0).map { thousandsLabel(top * it / Y_ROWS) }

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.height(SIM_PLOT_HEIGHT),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                ticks.forEach {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = colors.fgSubtle,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))

            Column(Modifier.weight(1f)) {
                Canvas(Modifier.fillMaxWidth().height(SIM_PLOT_HEIGHT)) {
                    val dashed = PathEffect.dashPathEffect(
                        floatArrayOf(3.dp.toPx(), 3.dp.toPx()),
                    )
                    for (i in 0..Y_ROWS) {
                        val y = size.height * i / Y_ROWS
                        drawLine(
                            color = grid,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = dashed,
                        )
                    }
                    xLabels.forEach { (_, at) ->
                        val x = at * size.width
                        drawLine(
                            color = grid,
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = dashed,
                        )
                    }

                    // The two solid axis lines, with recharts' outward ticks.
                    drawLine(
                        color = grid,
                        start = Offset(0f, 0f),
                        end = Offset(0f, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                    drawLine(
                        color = grid,
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                    for (i in 0..Y_ROWS) {
                        val y = size.height * i / Y_ROWS
                        drawLine(
                            color = grid,
                            start = Offset(-6.dp.toPx(), y),
                            end = Offset(0f, y),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }

                    // Canvas y grows downward, so the top of the scale is 0 px.
                    val toY: (Long) -> Float = { cents ->
                        size.height - (cents.toFloat() / top.toFloat()) * size.height
                    }
                    plot(size.width, size.height, toY)
                }
                AxisLabels(xLabels)
            }
        }
    }
}

/**
 * The projection: what was put in, and the interest stacked on top of it.
 *
 * The two bands are drawn in stacking order, each filled with the same fade
 * the web uses and stroked along its own top edge.
 */
@Composable
fun StackedAreaChart(
    /** Cumulative contributions, in cents, one per month. */
    contributed: List<Long>,
    /** Total value in cents; the band above [contributed] is the interest. */
    total: List<Long>,
    lowerColor: Color,
    upperColor: Color,
    modifier: Modifier = Modifier,
) {
    if (contributed.size < 2 || total.size != contributed.size) return
    val max = total.max()

    ChartFrame(
        maxCents = max,
        xLabels = yearTicks(contributed.size - 1),
        modifier = modifier,
    ) { width, height, toY ->
        val stepX = width / (contributed.size - 1)
        fun curve(values: List<Long>) = Path().apply {
            moveTo(0f, toY(values[0]))
            values.forEachIndexed { i, v -> if (i > 0) lineTo(i * stepX, toY(v)) }
        }

        // Lower band: the baseline up to what was contributed.
        val lower = curve(contributed).apply {
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        drawPath(
            path = lower,
            brush = Brush.verticalGradient(
                0f to lowerColor.copy(alpha = 0.5f),
                1f to lowerColor.copy(alpha = 0.08f),
            ),
        )

        // Upper band: from the contributions to the total — the interest.
        val upper = Path().apply {
            moveTo(0f, toY(total[0]))
            total.forEachIndexed { i, v -> if (i > 0) lineTo(i * stepX, toY(v)) }
            for (i in contributed.indices.reversed()) {
                lineTo(i * stepX, toY(contributed[i]))
            }
            close()
        }
        drawPath(
            path = upper,
            brush = Brush.verticalGradient(
                0f to upperColor.copy(alpha = 0.55f),
                1f to upperColor.copy(alpha = 0.1f),
            ),
        )

        drawPath(curve(contributed), color = lowerColor, style = Stroke(width = 2.dp.toPx()))
        drawPath(curve(total), color = upperColor, style = Stroke(width = 2.dp.toPx()))
    }
}

/** The comparison: one 2 px line per instrument, on a shared scale. */
@Composable
fun MultiLineChart(series: List<SimSeries>, modifier: Modifier = Modifier) {
    val drawable = series.filter { it.values.size >= 2 }
    if (drawable.isEmpty()) return
    val length = drawable.minOf { it.values.size }
    val max = drawable.maxOf { it.values.take(length).max() }

    ChartFrame(
        maxCents = max,
        xLabels = yearTicks(length - 1),
        modifier = modifier,
    ) { width, _, toY ->
        val stepX = width / (length - 1)
        drawable.forEach { s ->
            val path = Path().apply {
                moveTo(0f, toY(s.values[0]))
                for (i in 1 until length) lineTo(i * stepX, toY(s.values[i]))
            }
            drawPath(path, color = s.color, style = Stroke(width = 2.dp.toPx()))
        }
    }
}

/** The swatch-and-name row under a chart, as on the web. */
@Composable
fun ChartLegend(items: List<Pair<Color, String>>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { (color, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(
                    Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color),
                )
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                    color = Broke.colors.fgSubtle,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}
