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
) {
    val colors = Broke.colors
    if (values.size < 2) return

    val min = values.min()
    val max = values.max()
    val span = (max - min).coerceAtLeast(1)

    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(maxLabel, style = MaterialTheme.typography.labelSmall, color = colors.fgSubtle)
        }

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(160.dp),
        ) {
            val stepX = size.width / (values.size - 1)
            fun pointAt(index: Int): Offset {
                val ratio = (values[index] - min).toFloat() / span.toFloat()
                // Canvas y grows downward, so a high value sits near the top.
                return Offset(index * stepX, size.height - ratio * size.height)
            }

            val line = Path().apply {
                moveTo(pointAt(0).x, pointAt(0).y)
                for (i in 1 until values.size) {
                    val p = pointAt(i)
                    lineTo(p.x, p.y)
                }
            }

            // Soft fill under the curve, then the curve itself on top.
            val area = Path().apply {
                addPath(line)
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
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
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
