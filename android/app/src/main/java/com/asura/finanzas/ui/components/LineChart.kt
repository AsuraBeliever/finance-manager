package com.asura.finanzas.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.asura.finanzas.ui.theme.Broke

/**
 * An investment's value over time — the web's projection `LineChart`: 280 px,
 * `domain={["auto","auto"]}` ticks in "k" with one decimal, the dates along
 * the bottom thinned with `minTickGap={40}`, the past as a solid `monotone`
 * line up to today and the forecast dashed `5 4` from today on, with a `4 4`
 * reference rule at today when today is one of the points.
 *
 * Every point arrives already computed by `finanzas-core`; this only maps cents
 * to pixels (through the shared [RechartsFrame]).
 */
@Composable
fun LineChart(
    /** Y values in cents, in chronological order. */
    values: List<Long>,
    /** One 'YYYY-MM-DD' per point. */
    dates: List<String>,
    /** Today's 'YYYY-MM-DD': the solid past ends and the dashed forecast begins here. */
    today: String,
    modifier: Modifier = Modifier,
    /** The forecast's colour: the web's gold while a what-if is on. */
    forecastColor: androidx.compose.ui.graphics.Color? = null,
    /** The past's colour — the web's `POSITIVE`. */
    color: androidx.compose.ui.graphics.Color = WebPositive,
    /** Hide the tick labels (privacy mode masks figures). */
    maskTicks: Boolean = false,
    tooltip: ((Int) -> TooltipContent?)? = null,
) {
    if (values.size < 2 || dates.size != values.size) return
    val pesos = values.map { it / 100.0 }
    val scale = niceScale(pesos.min(), pesos.max(), fromZero = false)
    val past = values.indices.filter { dates[it] <= today }
    val future = values.indices.filter { dates[it] >= today }
    val forecast = forecastColor ?: color

    RechartsFrame(
        height = 280.dp,
        count = values.size,
        scale = scale,
        yLabel = { if (maskTicks) "" else thousandsTick(it, 1) },
        xCandidates = values.indices.toList(),
        xLabel = { dates[it] },
        minTickGap = 40.dp,
        modifier = modifier,
        referenceIndex = dates.indexOf(today).takeIf { it >= 0 && future.isNotEmpty() },
        tooltipAt = tooltip,
        dotsAt = { i ->
            buildList {
                if (dates[i] <= today) add(pesos[i] to color)
                if (dates[i] >= today) add(pesos[i] to forecast)
            }
        },
    ) { xOf, yOf ->
        val stroke = 2.dp.toPx()
        if (past.size >= 2) {
            drawPath(
                monotonePath(past.map { Offset(xOf(it), yOf(pesos[it])) }),
                color = color,
                style = Stroke(width = stroke),
            )
        }
        if (future.size >= 2) {
            drawPath(
                monotonePath(future.map { Offset(xOf(it), yOf(pesos[it])) }),
                color = forecast,
                style = Stroke(
                    width = stroke,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
                ),
            )
        }
    }
}

/** Kept for callers that lay out their own axis labels. */
@Composable
internal fun AxisLabels(labels: List<Pair<String, Float>>) {
    val colors = Broke.colors
    androidx.compose.ui.layout.Layout(
        content = {
            labels.forEach { (text, _) ->
                androidx.compose.material3.Text(
                    text,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                        .copy(fontSize = androidx.compose.ui.unit.TextUnit(11f, androidx.compose.ui.unit.TextUnitType.Sp)),
                    color = colors.fgSubtle,
                    maxLines = 1,
                )
            }
        },
        modifier = Modifier,
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(androidx.compose.ui.unit.Constraints()) }
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
