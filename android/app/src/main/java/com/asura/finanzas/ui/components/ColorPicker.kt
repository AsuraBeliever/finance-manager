package com.asura.finanzas.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

@Composable
fun ColorPickerRow(
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Broke.colors
    Column(modifier.fillMaxWidth()) {
        MicroLabel(stringResource(R.string.common_color))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CATEGORY_PALETTE.forEach { hex ->
                val swatch = parseHexColor(hex) ?: colors.accent
                val isSelected = selected.equals(hex, ignoreCase = true)
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(swatch)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) colors.fg else colors.borderMuted,
                            shape = CircleShape,
                        )
                        .clickable { onSelect(if (isSelected) null else hex) },
                )
            }
        }
    }
}
