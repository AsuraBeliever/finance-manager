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
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.res.stringResource
import com.asura.finanzas.R
import com.asura.finanzas.ui.formatMoney
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
/** One curve of the comparison chart. */
data class SimSeries(val name: String, val color: Color, val values: List<Long>)

/**
 * The web's `yearTicks`: a tick every `stride` years (so at most six), on the
 * month that closes each year. recharts then thins them with `minTickGap`.
 */
private fun yearTicks(months: Int): List<Int> {
    val years = months / 12
    if (years < 1) return emptyList()
    val stride = ceil(years / 6.0).toInt().coerceAtLeast(1)
    return (stride..years step stride).map { it * 12 }
}

/**
 * The projection: what was put in, and the interest stacked on top of it —
 * the web's `AreaChart` (320 px, `[0, auto]`, `monotone`, each band filled
 * with the same fade and stroked along its own top edge).
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
    /** The interest band per point, from finanzas-core, for the tooltip. */
    interest: List<Long> = emptyList(),
    /** Series names for the tooltip: contributed, then interest. */
    names: Pair<String, String>? = null,
) {
    if (contributed.size < 2 || total.size != contributed.size) return
    val years = stringResource(R.string.simulator_years)
    val low = contributed.map { it / 100.0 }
    val high = total.map { it / 100.0 }

    RechartsFrame(
        height = 320.dp,
        count = contributed.size,
        scale = niceScale(0.0, high.max(), fromZero = true),
        yLabel = { thousandsTick(it, 0) },
        xCandidates = yearTicks(contributed.size - 1),
        xLabel = { "${it / 12}a" },
        minTickGap = 28.dp,
        modifier = modifier,
        tooltipAt = if (names == null || interest.size != contributed.size) {
            null
        } else {
            { i ->
                TooltipContent(
                    label = yearsLabel(i, years),
                    items = listOf(
                        TooltipItem(names.first, formatMoney(contributed[i]), lowerColor),
                        TooltipItem(names.second, formatMoney(interest[i]), upperColor),
                    ),
                )
            }
        },
        // Stacked: the lower dot on the contributions, the upper one on top.
        dotsAt = { i -> listOf(low[i] to lowerColor, high[i] to upperColor) },
    ) { xOf, yOf ->
        val lowPts = low.indices.map { Offset(xOf(it), yOf(low[it])) }
        val highPts = high.indices.map { Offset(xOf(it), yOf(high[it])) }
        val base = yOf(0.0)

        // Lower band: the baseline up to what was contributed.
        val lower = monotonePath(lowPts).apply {
            lineTo(lowPts.last().x, base)
            lineTo(lowPts.first().x, base)
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
        val upper = monotonePath(highPts).apply {
            lineTo(lowPts.last().x, lowPts.last().y)
            monotonePath(lowPts.reversed(), this, moveFirst = false)
            close()
        }
        drawPath(
            path = upper,
            brush = Brush.verticalGradient(
                0f to upperColor.copy(alpha = 0.55f),
                1f to upperColor.copy(alpha = 0.1f),
            ),
        )
        drawPath(monotonePath(lowPts), color = lowerColor, style = Stroke(width = 2.dp.toPx()))
        drawPath(monotonePath(highPts), color = upperColor, style = Stroke(width = 2.dp.toPx()))
    }
}

/** The comparison: one 2 px `monotone` line per instrument, on a shared scale. */
@Composable
fun MultiLineChart(series: List<SimSeries>, modifier: Modifier = Modifier) {
    val drawable = series.filter { it.values.size >= 2 }
    if (drawable.isEmpty()) return
    val length = drawable.minOf { it.values.size }
    val max = drawable.maxOf { it.values.take(length).max() }
    val years = stringResource(R.string.simulator_years)

    RechartsFrame(
        height = 320.dp,
        count = length,
        scale = niceScale(0.0, max / 100.0, fromZero = true),
        yLabel = { thousandsTick(it, 0) },
        xCandidates = yearTicks(length - 1),
        xLabel = { "${it / 12}a" },
        minTickGap = 28.dp,
        modifier = modifier,
        tooltipAt = { i ->
            TooltipContent(
                label = yearsLabel(i, years),
                items = drawable.map { TooltipItem(it.name, formatMoney(it.values[i]), it.color) },
            )
        },
        dotsAt = { i -> drawable.map { it.values[i] / 100.0 to it.color } },
    ) { xOf, yOf ->
        drawable.forEach { s ->
            val pts = (0 until length).map { Offset(xOf(it), yOf(s.values[it] / 100.0)) }
            drawPath(monotonePath(pts), color = s.color, style = Stroke(width = 2.dp.toPx()))
        }
    }
}

/**
 * The tooltip heading at month `m`: the web's
 * `${(m / 12).toFixed(1)} ${es.simulator.years}`, "2.6 Plazo (años)". A month
 * count shown as years is presentation, not money.
 */
private fun yearsLabel(month: Int, years: String): String =
    "${java.math.BigDecimal(month / 12.0).setScale(1, java.math.RoundingMode.HALF_UP).toPlainString()} $years"

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
                    style = MaterialTheme.typography.bodySmall,
                    color = Broke.colors.fgSubtle,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}
