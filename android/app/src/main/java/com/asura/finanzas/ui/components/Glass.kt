package com.asura.finanzas.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.asura.finanzas.ui.theme.Broke

/**
 * The web app's frosted card: a translucent raised surface with a hairline
 * border. Compose can't blur what's behind a view cheaply on older devices, so
 * the "glass" here is the translucency plus the border, without a backdrop blur.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Broke.colors.surfaceRaised)
            .border(1.dp, Broke.colors.borderMuted, RoundedCornerShape(20.dp))
            .padding(18.dp),
        content = content,
    )
}

/** Section heading, in the display face. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
        color = Broke.colors.fg,
        modifier = modifier,
    )
}

/** The hero money figure: display face, violet→cyan gradient, like the web. */
@Composable
fun HeroAmount(text: String, modifier: Modifier = Modifier) {
    val colors = Broke.colors
    Text(
        text = text,
        style = androidx.compose.material3.MaterialTheme.typography.displayLarge.copy(
            brush = Brush.linearGradient(
                listOf(colors.accentBright, colors.accent, colors.cyan),
            ),
        ),
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

/** Thin "sin conexión — datos del último sync" strip. */
@Composable
fun OfflineNotice(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Broke.colors.surfaceOverlay)
            .border(1.dp, Broke.colors.borderMuted, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Sin conexión — estás viendo lo último que se sincronizó",
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = Broke.colors.fgMuted,
        )
    }
}
