package com.asura.finanzas.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.sp
import com.asura.finanzas.ui.theme.Broke

/**
 * A value-over-time curve, for the investment projection and the simulator.
 *
 * Every point arrives already computed by `finanzas-core`; this only maps cents
 * to pixels. The vertical scale spans min..max rather than starting at zero, so
 * a slow-growing balance still reads as a curve instead of a flat line — the
 * same choice the web chart makes.
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
) {
    val colors = Broke.colors
    if (values.size < 2) return

    val min = values.min()
    val max = values.max()
    val span = (max - min).coerceAtLeast(1)

    val axis = colors.borderMuted
    Column(modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            if (ticks.isNotEmpty()) {
                Column(
                    modifier = Modifier.height(160.dp),
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

        Canvas(
            Modifier
                .weight(1f)
                .height(160.dp),
        ) {
            val stepX = size.width / (values.size - 1)
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
            val forecast = Path().apply {
                if (split < values.lastIndex) {
                    moveTo(pointAt(split).x, pointAt(split).y)
                    for (i in split + 1 until values.size) {
                        val p = pointAt(i)
                        lineTo(p.x, p.y)
                    }
                }
            }

            // Soft fill under the curve, then the curve itself on top.
            val area = Path().apply {
                addPath(line)
                addPath(forecast)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(
                path = area,
                brush = Brush.verticalGradient(
                    listOf(colors.accent.copy(alpha = 0.22f), colors.accent.copy(alpha = 0f)),
                ),
            )
            drawPath(path = line, color = colors.accent, style = Stroke(width = 3.dp.toPx()))
            if (split < values.lastIndex) {
                drawPath(
                    path = forecast,
                    color = colors.accent,
                    style = Stroke(
                        width = 3.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(10.dp.toPx(), 7.dp.toPx()),
                        ),
                    ),
                )
                // The rule marking "today", as on the web.
                val x = split * stepX
                drawLine(
                    color = axis,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }
        }

        if (ticks.isEmpty()) {
            Text(minLabel, style = MaterialTheme.typography.labelSmall, color = colors.fgSubtle)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(startLabel, style = MaterialTheme.typography.labelSmall, color = colors.fgSubtle)
            Text(endLabel, style = MaterialTheme.typography.labelSmall, color = colors.fgSubtle)
        }
    }
}
