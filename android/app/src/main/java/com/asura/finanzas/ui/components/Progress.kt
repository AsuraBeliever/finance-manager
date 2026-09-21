package com.asura.finanzas.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.asura.finanzas.ui.theme.Broke

/**
 * Progress rail for goals and budgets. The fraction comes from the server's
 * `progressBps` — basis points are only turned into a width here, never
 * recomputed from the underlying amounts.
 */
@Composable
fun ProgressBar(
    progressBps: Long,
    modifier: Modifier = Modifier,
    over: Boolean = false,
    /** A goal paints its bar in its own colour, as on the web. */
    color: androidx.compose.ui.graphics.Color? = null,
    /**
     * Draw the rail as this many separate pips instead of one bar — how the web
     * shows an MSI plan, one pip per instalment. Ignored above 0 pips only.
     */
    segments: Int? = null,
) {
    val colors = Broke.colors
    val fraction = (progressBps / 10_000f).coerceIn(0f, 1f)
    val fill = if (over) colors.danger else color ?: colors.accent

    if (segments != null && segments > 0) {
        val filled = Math.round(fraction * segments)
        Row(
            modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(segments) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (i < filled) fill else colors.surfaceOverlay),
                )
            }
        }
        return
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(colors.surfaceOverlay),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                // A flat fill, like the web's: the gradient here read as a
                // different control next to the same bar in the browser.
                .background(fill),
        )
    }
}

/** Percentage text for a basis-point value: 7350 -> "73.50 %", as the web
 *  formats it (two decimals, with the space before the sign). */
fun formatBps(bps: Long): String = "%.2f %%".format(bps / 100.0)

/** Neutral track colour when a caller needs it outside the bar. */
val progressTrack: Color
    @Composable get() = Broke.colors.surfaceOverlay
