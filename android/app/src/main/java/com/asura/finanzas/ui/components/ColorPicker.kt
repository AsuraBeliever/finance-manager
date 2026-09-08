package com.asura.finanzas.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.asura.finanzas.R
import com.asura.finanzas.ui.parseHexColor
import com.asura.finanzas.ui.theme.Broke

/**
 * The swatches offered for categories and wallets, ported from
 * `CATEGORY_PALETTE` in `src/lib/palette.ts` — the first eleven match the seeded
 * categories' defaults, so keep the order.
 */
val CATEGORY_PALETTE = listOf(
    "#34d399", "#f472b6", "#fbbf24", "#38bdf8",
    "#f97316", "#a855f7", "#22d3ee", "#ec4899",
    "#ef4444", "#8b5cf6", "#94a3b8", "#10b981",
    "#eab308", "#3b82f6", "#fb7185", "#14b8a6",
)

/** The rainbow disc the web paints on its custom-colour swatch. */
private val WHEEL = Brush.sweepGradient(
    listOf(
        Color(0xFFEF4444), Color(0xFFEAB308), Color(0xFF22C55E),
        Color(0xFF06B6D4), Color(0xFF3B82F6), Color(0xFFA855F7),
        Color(0xFFEF4444),
    ),
)

/**
 * The one shared colour picker — the web's `ColorPicker`: a wrapping row of
 * 24 px preset swatches, then a rainbow swatch that opens a free colour choice.
 * The chosen one wears a ring set off from the card behind it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(
    value: String?,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = value
    val onSelect: (String) -> Unit = onChange
    val colors = Broke.colors
    var custom by remember { mutableStateOf(false) }
    val isPreset = selected != null && CATEGORY_PALETTE.any { it.equals(selected, true) }
    val customActive = selected != null && !isPreset

    @Composable
    fun ringed(active: Boolean, content: @Composable () -> Unit) {
        Box(
            Modifier
                .size(if (active) 32.dp else 24.dp)
                .then(
                    if (active) {
                        // `ring-2 ring-accent ring-offset-2`: the ring floats
                        // clear of the swatch, it does not hug it.
                        Modifier
                            .clip(CircleShape)
                            .border(2.dp, colors.accent, CircleShape)
                            .padding(4.dp)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) { content() }
    }

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CATEGORY_PALETTE.forEach { hex ->
            val swatch = parseHexColor(hex) ?: colors.accent
            val active = selected.equals(hex, ignoreCase = true)
            ringed(active) {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(swatch)
                        .clickable { onSelect(hex) },
                )
            }
        }
        ringed(customActive) {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .then(
                        if (customActive) {
                            Modifier.background(parseHexColor(selected) ?: colors.accent)
                        } else {
                            Modifier.background(WHEEL)
                        },
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                    .clickable { custom = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Lucide.Palette,
                    contentDescription = stringResource(R.string.categories_custom_color),
                    tint = Color.White,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }

    if (custom) {
        CustomColorDialog(
            initial = parseHexColor(selected) ?: colors.accent,
            onDismiss = { custom = false },
            onPick = { hex -> onSelect(hex); custom = false },
        )
    }
}

/**
 * Free colour choice. The browser hands this off to the OS palette; Android has
 * no such dialog, so this is the smallest thing that does the same job — a hue
 * strip, a shade square, and the result in hex.
 */
@Composable
private fun CustomColorDialog(
    initial: Color,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(initial.toArgb(), hsv)
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    val picked = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)))

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Broke.colors.surfaceOverlay,
        title = { Text(stringResource(R.string.categories_custom_color), color = Broke.colors.fg) },
        text = {
            Column {
                // Saturation across, brightness down — the usual shade square.
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .pointerInput(hue) {
                            fun set(p: Offset) {
                                sat = (p.x / size.width).coerceIn(0f, 1f)
                                value = 1f - (p.y / size.height).coerceIn(0f, 1f)
                            }
                            detectTapGestures { set(it) }
                        }
                        .pointerInput(hue) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                sat = (change.position.x / size.width).coerceIn(0f, 1f)
                                value = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                            }
                        },
                ) {
                    val pure = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
                    drawRect(Brush.horizontalGradient(listOf(Color.White, pure)))
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                    drawCircle(
                        color = Color.White,
                        radius = 6.dp.toPx(),
                        center = Offset(sat * size.width, (1f - value) * size.height),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .pointerInput(Unit) {
                            detectTapGestures { hue = (it.x / size.width).coerceIn(0f, 1f) * 360f }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                hue = (change.position.x / size.width).coerceIn(0f, 1f) * 360f
                            }
                        },
                ) {
                    drawRect(
                        Brush.horizontalGradient(
                            (0..6).map {
                                Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f)))
                            },
                        ),
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 8.dp.toPx(),
                        center = Offset(hue / 360f * size.width, size.height / 2),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(picked),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(picked.toHex()) }) {
                Text(stringResource(R.string.common_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

private fun Color.toHex(): String = "#%06X".format(0xFFFFFF and this.toArgb())
