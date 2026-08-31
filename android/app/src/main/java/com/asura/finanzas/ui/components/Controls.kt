package com.asura.finanzas.ui.components

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asura.finanzas.ui.theme.Broke

/**
 * The web's segmented control: a dark pill holding the options, the selected
 * one raised. Used for theme, language, clock format and the transaction kind
 * filter.
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
) {
    val colors = Broke.colors
    // The web's control: rounded-xl tray, p-1, gap-1; the selected option is a
    // rounded-lg raised chip with plain foreground text — not accent-coloured.
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceOverlay)
            // Long labels would otherwise be squeezed until each one wrapped
            // down several lines, blowing the pill up into a tall block.
            .then(if (fillEqually) Modifier else Modifier.horizontalScroll(rememberScrollState()))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Row(
                modifier = Modifier
                    .then(if (fillEqually) Modifier.weight(1f) else Modifier)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) colors.surfaceRaised else Color.Transparent)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                icon?.invoke(option)?.let {
                    Icon(
                        it,
                        contentDescription = null,
                        tint = if (isSelected) colors.fg else colors.fgSubtle,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                    color = if (isSelected) colors.fg else colors.fgSubtle,
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
            .background(if (enabled) colors.accentDim else colors.surfaceOverlay)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        leadingIcon?.let {
            Icon(
                it,
                contentDescription = null,
                tint = if (enabled) Color.White else colors.fgSubtle,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
            color = if (enabled) Color.White else colors.fgSubtle,
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

/** Quiet pill used for the period picker and other secondary chips. */
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
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceOverlay)
            .border(1.dp, colors.borderMuted, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        leadingIcon?.let {
            Icon(it, contentDescription = null, tint = colors.fgMuted, modifier = Modifier.size(17.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = colors.fg)
        trailingIcon?.let {
            Icon(it, contentDescription = null, tint = colors.fgMuted, modifier = Modifier.size(17.dp))
        }
    }
}

/** Circular tinted badge behind a small icon (the transaction row's kind mark). */
@Composable
fun IconBadge(icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(42.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** Row of a label on the left and arbitrary control on the right, inside a card. */
@Composable
fun SettingRow(
    label: String,
    modifier: Modifier = Modifier,
    control: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = Broke.colors.fg)
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
