package com.asura.finanzas.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asura.finanzas.ui.theme.Broke

/** One arc of a donut: a label, an amount and the colour to draw it in. */
data class DonutSlice(
    val label: String,
    val valueCents: Long,
    val color: Color,
    val formatted: String,
)

/**
 * The ring itself, drawn to recharts' geometry: angles run anticlockwise from
 * three o'clock (`<Pie>` defaults to `startAngle=0`) and `paddingAngle={2}`
 * leaves a two-degree gap between neighbours. Compose's `drawArc` is the mirror
 * image — clockwise off the same origin — hence the negated angles.
 *
 * Radii are the web's, in dp: a CSS pixel and a dp are the same size, so the
 * chart comes out the same width on the phone as in the browser.
 */
@Composable
fun DonutRing(
    slices: List<DonutSlice>,
    innerRadius: Dp,
    outerRadius: Dp,
    modifier: Modifier = Modifier,
) {
    val empty = Broke.colors.surfaceOverlay
    val total = slices.sumOf { it.valueCents.coerceAtLeast(0) }
    Canvas(modifier.size(outerRadius * 2)) {
        val stroke = (outerRadius - innerRadius).toPx()
        val radius = outerRadius.toPx() - stroke / 2
        val topLeft = androidx.compose.ui.geometry.Offset(
            size.width / 2 - radius,
            size.height / 2 - radius,
        )
        val arcSize = Size(radius * 2, radius * 2)

        if (total <= 0) {
            drawArc(
                color = empty,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )
            return@Canvas
        }

        var start = 0f
        slices.forEach { slice ->
            val sweep = 360f * (slice.valueCents.coerceAtLeast(0).toFloat() / total)
            drawArc(
                color = slice.color,
                startAngle = -(start + sweep) + 1f,
                sweepAngle = (sweep - 2f).coerceAtLeast(0f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )
            start += sweep
        }
    }
}

/**
 * The category breakdown widget's chart: a 144 dp ring with the total in the
 * hole and the key *beside* it, one tappable row per slice with its amount and
 * share — the web's `BreakdownWidget`.
 */
@Composable
fun BreakdownDonut(
    slices: List<DonutSlice>,
    centerLabel: String,
    centerValue: String,
    modifier: Modifier = Modifier,
    /** Tapping a legend row drills into that slice; null leaves it read-only. */
    onSliceClick: ((Int) -> Unit)? = null,
) {
    val colors = Broke.colors
    val total = slices.sumOf { it.valueCents.coerceAtLeast(0) }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(Modifier.size(144.dp), contentAlignment = Alignment.Center) {
            DonutRing(slices, innerRadius = 48.dp, outerRadius = 68.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    centerLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.2.sp),
                    color = colors.fgSubtle,
                )
                Text(
                    centerValue,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                    color = colors.fg,
                )
            }
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            slices.forEachIndexed { index, slice ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .then(
                            if (onSliceClick != null) {
                                Modifier.clickable { onSliceClick(index) }
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Dot(slice.color)
                    Text(
                        slice.label,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = colors.fgMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // `truncate` then `ml-auto`: the name eats the slack and
                        // clips, the amount keeps its natural width.
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        slice.formatted,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = colors.fg,
                    )
                    Text(
                        // Share of a server-computed total: presentation, not money math.
                        if (total > 0) "%.0f%%".format(100.0 * slice.valueCents / total) else "0%",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.fgSubtle,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(36.dp),
                    )
                }
            }
        }
    }
}

/**
 * The "by wallet" / "by investment" chart: a wider ring with nothing in the
 * hole and the key wrapped in chips underneath, names only — the web's
 * `donutNode` on the dashboard.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun LegendBelowDonut(slices: List<DonutSlice>, modifier: Modifier = Modifier) {
    val colors = Broke.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(176.dp), contentAlignment = Alignment.Center) {
            DonutRing(slices, innerRadius = 46.dp, outerRadius = 74.dp)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            slices.forEach { slice ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // `rounded-sm`, not a circle, on this legend.
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(slice.color),
                    )
                    Text(
                        slice.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                        color = colors.fgMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 144.dp),
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

/**
 * The goals widget's ring — the web's `Gauge`: 156 px across, the bar 74%→100%
 * of the radius with a round cap, clockwise from twelve o'clock, over a
 * surface-overlay track. The amount sits in a hollow two thirds as wide.
 */
@Composable
fun RingGauge(
    progressBps: Long,
    centerValue: String,
    centerCaption: String,
    modifier: Modifier = Modifier,
    /** A goal paints its ring in its own colour, as on the web. */
    color: Color? = null,
) {
    val colors = Broke.colors
    val ring = color ?: colors.accent
    val fraction = (progressBps / 10_000f).coerceIn(0f, 1f)

    Box(
        modifier = modifier.fillMaxWidth().height(156.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(156.dp)) {
            val stroke = size.width * 0.13f
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
                color = ring,
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
            modifier = Modifier.width(103.dp),
        ) {
            Text(
                centerValue,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp),
                color = colors.fg,
                maxLines = 1,
            )
            Text(
                centerCaption,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.2.sp),
                color = colors.fgSubtle,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
