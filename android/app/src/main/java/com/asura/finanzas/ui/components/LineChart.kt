package com.asura.finanzas.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asura.finanzas.ui.theme.Broke

/** How tall the plot is, matching the web chart's 280 px box minus its axis. */
private val PLOT_HEIGHT = 210.dp

/**
 * A value-over-time curve, for the investment projection and the simulator.
 *
 * Every point arrives already computed by `finanzas-core`; this only maps cents
 * to pixels. The vertical scale spans min..max rather than starting at zero, so
 * a slow-growing balance still reads as a curve instead of a flat line — the
 * same choice the web chart makes.
 *
 * Furniture copied from recharts, which is what the web draws with: a dashed
 * `3 3` grid through every tick, a 2 px line, `5 4` dashes on the forecast, a
 * `4 4` rule at today, and date labels spread along the axis rather than one
 * pinned to each end.
 */
@Composable
fun LineChart(
    /** Y values in cents, in chronological order. */
    values: List<Long>,
    /** Labels for the first and last point, drawn under the axis. */
    startLabel: String,
    endLabel: String,
    /** Formatted low and high, drawn as the vertical bounds. */
    minLabel: String,
    maxLabel: String,
    modifier: Modifier = Modifier,
    /**
     * Index where the modelled past ends and the forecast begins. The web draws
     * everything after it dashed, with a rule marking today; without that the
     * chart claims to know the future as firmly as the past.
     */
    forecastFrom: Int? = null,
    /** Axis tick labels, top to bottom, drawn down the left side. */
    ticks: List<String> = emptyList(),
    /** One date per point; the axis picks a few of these to label. */
    dates: List<String> = emptyList(),
) {
    val colors = Broke.colors
    if (values.size < 2) return

    val min = values.min()
    val max = values.max()
    val span = (max - min).coerceAtLeast(1)
    val grid = colors.borderMuted

    // recharts thins its ticks out from the far end until they stop crowding
    // each other, which at this width leaves three: the last point and two
    // steps back from it.
    val labelled: List<Int> = if (dates.size == values.size && dates.isNotEmpty()) {
        listOf(0.2f, 0.6f, 1f).map { (it * (values.size - 1)).toInt() }
    } else {
        emptyList()
    }

    Column(modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            if (ticks.isNotEmpty()) {
                Column(
                    modifier = Modifier.height(PLOT_HEIGHT),
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
            } else {
                Text(maxLabel, style = MaterialTheme.typography.labelSmall, color = colors.fgSubtle)
            }

            Column(Modifier.weight(1f)) {
                Canvas(Modifier.fillMaxWidth().height(PLOT_HEIGHT)) {
                    val stepX = size.width / (values.size - 1)
                    val dashed = PathEffect.dashPathEffect(
                        floatArrayOf(3.dp.toPx(), 3.dp.toPx()),
                    )

                    // The grid sits under everything, one line per tick in each
                    // direction — recharts' CartesianGrid.
                    val rows = (ticks.size - 1).coerceAtLeast(1)
                    for (i in 0..rows) {
                        val y = size.height * i / rows
                        drawLine(
                            color = grid,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = dashed,
                        )
                    }
                    labelled.forEach { index ->
                        val x = index * stepX
                        drawLine(
                            color = grid,
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = dashed,
                        )
                    }

                    // The two axis lines, solid, with recharts' 6 px tick marks.
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
                    for (i in 0..rows) {
                        val y = size.height * i / rows
                        drawLine(
                            color = grid,
                            start = Offset(-6.dp.toPx(), y),
                            end = Offset(0f, y),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }

                    fun pointAt(index: Int): Offset {
                        val ratio = (values[index] - min).toFloat() / span.toFloat()
                        // Canvas y grows downward, so a high value sits near the top.
                        return Offset(index * stepX, size.height - ratio * size.height)
                    }

                    // The curve is drawn in two pieces so the forecast can be dashed.
                    val split = forecastFrom?.coerceIn(0, values.lastIndex) ?: values.lastIndex
                    val line = Path().apply {
                        moveTo(pointAt(0).x, pointAt(0).y)
                        for (i in 1..split) {
                            val p = pointAt(i)
                            lineTo(p.x, p.y)
                        }
                    }

                    drawPath(path = line, color = colors.accent, style = Stroke(width = 2.dp.toPx()))
                    if (split < values.lastIndex) {
                        val forecast = Path().apply {
                            moveTo(pointAt(split).x, pointAt(split).y)
                            for (i in split + 1 until values.size) {
                                val p = pointAt(i)
                                lineTo(p.x, p.y)
                            }
                        }
                        drawPath(
                            path = forecast,
                            color = colors.accent,
                            style = Stroke(
                                width = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(5.dp.toPx(), 4.dp.toPx()),
                                ),
                            ),
                        )
                        // The rule marking "today", as on the web.
                        val x = split * stepX
                        drawLine(
                            color = grid,
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(4.dp.toPx(), 4.dp.toPx()),
                            ),
                        )
                    }
                }

                if (labelled.isEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            startLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = colors.fgSubtle,
                        )
                        Text(
                            endLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = colors.fgSubtle,
                        )
                    }
                } else {
                    AxisLabels(
                        labels = labelled.map { dates[it] to it / (values.size - 1f) },
                    )
                }
            }
        }

        if (ticks.isEmpty()) {
            Text(minLabel, style = MaterialTheme.typography.labelSmall, color = colors.fgSubtle)
        }
    }
}

/**
 * Date labels centred on their own point along the axis, nudged inward at the
 * ends so nothing hangs off the chart — what recharts does with its ticks.
 */
@Composable
private fun AxisLabels(labels: List<Pair<String, Float>>) {
    Layout(
        content = {
            labels.forEach { (text, _) ->
                Text(
                    text,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = Broke.colors.fgSubtle,
                    maxLines = 1,
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(Constraints()) }
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(constraints.maxWidth, height) {
            placeables.forEachIndexed { i, p ->
                val centre = (labels[i].second * constraints.maxWidth).toInt()
                val x = (centre - p.width / 2).coerceIn(0, constraints.maxWidth - p.width)
                p.placeRelative(x, 0)
            }
        }
    }
}
