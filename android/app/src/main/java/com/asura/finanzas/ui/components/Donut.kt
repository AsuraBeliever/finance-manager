package com.asura.finanzas.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.asura.finanzas.ui.theme.Broke

/** One arc of a donut: a label, an amount and the colour to draw it in. */
data class DonutSlice(
    val label: String,
    val valueCents: Long,
    val color: Color,
    val formatted: String,
)

/**
 * The dashboard's donut charts. Angles are proportions of a total the server
 * already computed — no money is derived here, only geometry.
 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    centerLabel: String,
    centerValue: String,
    modifier: Modifier = Modifier,
    /** Tapping a legend row drills into that slice; null leaves it read-only. */
    onSliceClick: ((Int) -> Unit)? = null,
) {
    val colors = Broke.colors
    val total = slices.sumOf { it.valueCents.coerceAtLeast(0) }

    Column(modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(190.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(170.dp)) {
                val stroke = 30.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                var start = -90f

                if (total <= 0) {
                    drawArc(
                        color = colors.surfaceOverlay,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke),
                    )
                } else {
                    slices.forEach { slice ->
                        val sweep = 360f * (slice.valueCents.coerceAtLeast(0).toFloat() / total)
                        drawArc(
                            color = slice.color,
                            startAngle = start,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(width = stroke),
                        )
                        start += sweep
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    centerLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.fgMuted,
                )
                Text(
                    centerValue,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.fg,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        slices.forEachIndexed { index, slice ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (onSliceClick != null) {
                            Modifier.clickable { onSliceClick(index) }
                        } else {
                            Modifier
                        },
                    )
                    .padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Dot(slice.color)
                Spacer(Modifier.width(10.dp))
                Text(
                    slice.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.fgMuted,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    slice.formatted,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.fg,
                )
                if (total > 0) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        // Share of a server-computed total: presentation, not money math.
                        "%.0f%%".format(100.0 * slice.valueCents / total),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.fgSubtle,
                    )
                }
            }
        }
    }
}

/**
 * Palette for chart slices, ported from `CHART_COLORS` in src/lib/palette.ts —
 * the same order, so a wallet keeps the same colour in both apps.
 */
val CHART_COLORS = listOf(
    Color(0xFFA855F7), Color(0xFFEC4899), Color(0xFF22D3EE), Color(0xFFF59E0B),
    Color(0xFF34D399), Color(0xFF60A5FA), Color(0xFFFB7185), Color(0xFFC084FC),
)

fun chartColor(index: Int): Color = CHART_COLORS[index % CHART_COLORS.size]

/** Ring gauge used by the goals widget. */
@Composable
fun RingGauge(
    progressBps: Long,
    centerValue: String,
    centerCaption: String,
    modifier: Modifier = Modifier,
) {
    val colors = Broke.colors
    val fraction = (progressBps / 10_000f).coerceIn(0f, 1f)

    Box(
        modifier = modifier.fillMaxWidth().height(190.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(168.dp)) {
            val stroke = 22.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = colors.surfaceOverlay,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke),
            )
            drawArc(
                color = colors.accent,
                startAngle = -90f,
                sweepAngle = 360f * fraction,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 40.dp),
        ) {
            Text(centerValue, style = MaterialTheme.typography.titleMedium, color = colors.fg)
            Text(
                centerCaption,
                style = MaterialTheme.typography.labelSmall,
                color = colors.fgMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}
