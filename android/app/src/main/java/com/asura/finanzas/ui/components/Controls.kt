package com.asura.finanzas.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asura.finanzas.ui.theme.Broke

/**
 * Which of the web's segmented controls this is. They are not one component
 * over there — three different shapes grew in three places — and collapsing
 * them into one here put the wrong tray under half the screens.
 */
enum class SegStyle {
    /** `rounded-lg bg-surface p-1`, chip `rounded-md`, raised on surface-overlay. */
    Tray,

    /** `rounded-xl bg-surface-overlay p-1 gap-1`, chip `rounded-lg`, raised. */
    Pill,

    /** The theme switch: bordered on surface, the choice marked in the accent. */
    Theme,

    /** The simulator's modes: bordered on surface-raised, accent chip. */
    Modes,
}

/**
 * A row of mutually exclusive options in a tray, the way the web draws them.
 * See [SegStyle] for which shape goes where.
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    // @Composable so callers can resolve the label from string resources.
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    icon: ((T) -> ImageVector?)? = null,
    /** Split the width evenly, as the web does wherever the row has space. */
    fillEqually: Boolean = false,
    style: SegStyle = SegStyle.Pill,
) {
    val colors = Broke.colors
    val trayRadius = if (style == SegStyle.Tray || style == SegStyle.Theme) 8.dp else 12.dp
    val chipRadius = if (style == SegStyle.Tray || style == SegStyle.Theme) 6.dp else 8.dp
    val tray = when (style) {
        SegStyle.Tray, SegStyle.Theme -> colors.surface
        SegStyle.Pill -> colors.surfaceOverlay
        SegStyle.Modes -> colors.surfaceRaised
    }
    val chipGap = if (style == SegStyle.Pill) 4.dp else 0.dp
    val chipPadding = when (style) {
        SegStyle.Theme -> 10.dp
        SegStyle.Modes -> 16.dp
        else -> 12.dp
    }
    val fontSize = if (style == SegStyle.Theme) 12.sp else 14.sp
    val accentChip = style == SegStyle.Theme || style == SegStyle.Modes

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(trayRadius))
            .background(tray)
            .then(
                if (style == SegStyle.Theme || style == SegStyle.Modes) {
                    Modifier.border(1.dp, colors.borderMuted, RoundedCornerShape(trayRadius))
                } else {
                    Modifier
                },
            )
            // Long labels would otherwise be squeezed until each one wrapped
            // down several lines, blowing the pill up into a tall block.
            .then(if (fillEqually) Modifier else Modifier.horizontalScroll(rememberScrollState()))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(chipGap),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Row(
                modifier = Modifier
                    .then(if (fillEqually) Modifier.weight(1f) else Modifier)
                    .clip(RoundedCornerShape(chipRadius))
                    .background(
                        when {
                            !isSelected -> Color.Transparent
                            style == SegStyle.Theme -> colors.accentDim.copy(alpha = 0.20f)
                            style == SegStyle.Modes -> colors.accent.copy(alpha = 0.15f)
                            style == SegStyle.Tray -> colors.surfaceOverlay
                            else -> colors.surfaceRaised
                        },
                    )
                    .clickable { onSelect(option) }
                    .padding(horizontal = chipPadding, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                icon?.invoke(option)?.let {
                    Icon(
                        it,
                        contentDescription = null,
                        tint = when {
                            !isSelected -> if (accentChip) colors.fgMuted else colors.fgSubtle
                            accentChip -> colors.accent
                            else -> colors.fg
                        },
                        modifier = Modifier.size(15.dp),
                    )
                }
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = fontSize,
                        // The tray shape only bolds the chosen option ("text-sm"
                        // vs "text-sm font-medium"); the others always do.
                        fontWeight = if (style == SegStyle.Tray && !isSelected) {
                            androidx.compose.ui.text.font.FontWeight.Normal
                        } else {
                            androidx.compose.ui.text.font.FontWeight.Medium
                        },
                    ),
                    color = when {
                        !isSelected -> if (accentChip) colors.fgMuted else colors.fgSubtle
                        accentChip -> colors.accent
                        else -> colors.fg
                    },
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/** The violet pill with the soft glow — the web's primary `Button`. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val colors = Broke.colors
    // Metrics copied from the web's Button: rounded-lg, px-4 py-2, text-sm, on
    // the dimmed accent. It shows up on nearly every screen, so a fatter pill
    // here is a difference the eye catches everywhere at once.
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            // `disabled:opacity-50` — the same pill, faded, not a grey one.
            .background(colors.accentDim.copy(alpha = if (enabled) 1f else 0.5f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        leadingIcon?.let {
            Icon(
                it,
                contentDescription = null,
                tint = Color.White.copy(alpha = if (enabled) 1f else 0.5f),
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
            color = Color.White.copy(alpha = if (enabled) 1f else 0.5f),
        )
    }
}

/**
 * The web's bordered secondary link (the simulator entry point): no fill, just
 * a hairline outline with muted text.
 */
@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    val colors = Broke.colors
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, colors.borderMuted, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        leadingIcon?.let {
            Icon(it, contentDescription = null, tint = colors.fgMuted, modifier = Modifier.size(16.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
            color = colors.fgMuted,
        )
    }
}

/**
 * The period picker's trigger, and the shape any other quiet chip takes: the
 * web's `rounded-lg border border-border-muted bg-surface px-3 py-1.5 text-sm`,
 * with a 15 px glyph in front and a 14 px chevron behind.
 */
@Composable
fun ChipButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
) {
    val colors = Broke.colors
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surface)
            .border(1.dp, colors.borderMuted, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        leadingIcon?.let {
            Icon(it, contentDescription = null, tint = colors.fgSubtle, modifier = Modifier.size(15.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = colors.fg,
        )
        trailingIcon?.let {
            Icon(it, contentDescription = null, tint = colors.fgSubtle, modifier = Modifier.size(14.dp))
        }
    }
}

/**
 * The browser's own checkbox, which is what the web app uses (`<input
 * type="checkbox" class="accent-accent">`) — a 13 px square, dark grey with a
 * light hairline when off, filled with the accent and a dark tick when on.
 * Material's `Checkbox` is half again as big and outlined, so side by side the
 * two screens did not look like the same app.
 */
@Composable
fun WebCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** 13 dp is the browser default; a couple of places ask for `h-4 w-4`. */
    boxSize: androidx.compose.ui.unit.Dp = 13.dp,
) {
    val accent = Broke.colors.accent
    // Chrome's dark-mode defaults, sampled off the rendered page.
    val off = Color(0xFF3B3B3B)
    val hairline = Color(0xFF858585)
    val tick = Color(0xFF3B3B3B)
    Canvas(
        modifier
            .size(boxSize)
            .clip(RoundedCornerShape(2.dp))
            .clickable { onCheckedChange(!checked) },
    ) {
        val r = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
        drawRoundRect(color = if (checked) accent else off, cornerRadius = r)
        if (checked) {
            val w = size.width
            val path = Path().apply {
                moveTo(w * 0.22f, w * 0.52f)
                lineTo(w * 0.42f, w * 0.72f)
                lineTo(w * 0.78f, w * 0.28f)
            }
            drawPath(path, tick, style = Stroke(width = w * 0.16f, cap = StrokeCap.Square))
        } else {
            drawRoundRect(
                color = hairline,
                cornerRadius = r,
                style = Stroke(width = 1.dp.toPx()),
            )
        }
    }
}

/**
 * Circular badge behind a small icon (the transaction row's kind mark). The
 * web fills it with the flat `bg-surface-overlay` and tints only the glyph; a
 * wash of the tint itself turned pink on the lighter surface of a dialog.
 */
@Composable
fun IconBadge(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 42.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(percent = 50))
            .background(Broke.colors.surfaceOverlay),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.48f))
    }
}

/** Row of a label on the left and arbitrary control on the right, inside a card. */
@Composable
fun SettingRow(
    label: String,
    modifier: Modifier = Modifier,
    /** A row nested inside a card is labelled small and muted, as on the web. */
    subtle: Boolean = false,
    control: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = if (subtle) {
                MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
            } else {
                MaterialTheme.typography.titleMedium
            },
            color = if (subtle) Broke.colors.fgMuted else Broke.colors.fg,
        )
        Spacer(Modifier.width(12.dp))
        control()
    }
}

/** Hairline separator matching the web's card dividers. */
@Composable
fun HairLine(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Broke.colors.borderMuted),
    )
}
