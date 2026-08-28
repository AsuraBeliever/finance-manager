package com.asura.finanzas.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asura.finanzas.R
import com.asura.finanzas.ui.theme.Broke

/**
 * The gradient-mesh canvas from `src/index.css` (`body::before`): three soft
 * colour blobs that drift slowly behind everything. It is what makes the app
 * read as "neon glass" rather than as a flat dark theme, so it sits under every
 * screen, exactly like on the web.
 */
@Composable
fun MeshBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = Broke.colors
    val transition = rememberInfiniteTransition(label = "mesh")
    // 26s there and back, matching the web's mesh-drift animation.
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 26_000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "drift",
    )

    Box(
        modifier
            .fillMaxSize()
            .background(colors.surface)
            .drawBehind {
                val dx = -0.03f * size.width * drift
                val dy = 0.02f * size.height * drift
                val scale = 1f + 0.08f * drift

                fun blob(color: androidx.compose.ui.graphics.Color, fx: Float, fy: Float, r: Float) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(color, androidx.compose.ui.graphics.Color.Transparent),
                            center = Offset(size.width * fx + dx, size.height * fy + dy),
                            radius = size.width * r * scale,
                        ),
                        radius = size.width * r * scale,
                        center = Offset(size.width * fx + dx, size.height * fy + dy),
                    )
                }

                blob(colors.mesh1, 0.18f, 0.12f, 1.05f)
                blob(colors.mesh3, 0.85f, 0.08f, 0.95f)
                blob(colors.mesh2, 0.75f, 0.88f, 1.10f)
            },
    ) {
        content()
    }
}

/**
 * Page title: a cyan rule followed by the name of the screen in the display
 * face — the web's `PageHeader`.
 */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = Broke.colors
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(5.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors.cyan),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 38.sp, lineHeight = 44.sp),
                color = colors.fg,
                modifier = Modifier.padding(start = 14.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = actions,
        )
    }
}

/**
 * Frosted card. Compose has no cheap backdrop blur on older devices, so the
 * glass here is the translucent fill plus the hairline border — the same read
 * at a glance, without the frame cost.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    padding: androidx.compose.ui.unit.Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Broke.colors.surfaceRaised)
            .border(1.dp, Broke.colors.borderMuted, RoundedCornerShape(24.dp))
            .padding(padding),
        content = content,
    )
}

/** All-caps, letter-spaced micro label ("NET WORTH", "POCKETS · 2"). */
@Composable
fun MicroLabel(text: String, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color? = null) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp, fontSize = 11.sp),
        color = color ?: Broke.colors.fgMuted,
        modifier = modifier,
    )
}

/** The hero money figure: display face with the violet→cyan gradient. */
@Composable
fun HeroAmount(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 40.sp,
) {
    val colors = Broke.colors
    Text(
        text = text,
        style = MaterialTheme.typography.displayLarge.copy(
            fontSize = fontSize,
            lineHeight = fontSize * 1.15f,
            brush = Brush.linearGradient(
                listOf(colors.accentBright, colors.accent, colors.cyan),
            ),
        ),
        modifier = modifier,
    )
}

/** Thin "sin conexión — datos del último sync" strip. */
@Composable
fun OfflineNotice(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Broke.colors.surfaceOverlay)
            .border(1.dp, Broke.colors.borderMuted, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Broke.colors.fgMuted,
        )
    }
}

/** Section heading inside a page, in the display face. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        color = Broke.colors.fg,
        modifier = modifier,
    )
}

/** Small round colour swatch used for wallets and categories. */
@Composable
fun Dot(color: androidx.compose.ui.graphics.Color, size: androidx.compose.ui.unit.Dp = 10.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(percent = 50))
            .background(color),
    )
}

/** Centred placeholder text for empty lists. */
@Composable
fun EmptyState(title: String, description: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = Broke.colors.fg,
            textAlign = TextAlign.Center,
        )
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = Broke.colors.fgMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Page header with a back affordance, for the screens reached from "More". */
@Composable
fun BackHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Defaults to a plain "Back"; settings passes its own wording. */
    backLabel: String? = null,
) {
    val colors = Broke.colors
    Column(modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onBack)
                .padding(vertical = 6.dp, horizontal = 2.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = null,
                tint = colors.fgMuted,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = backLabel ?: stringResource(R.string.common_back),
                style = MaterialTheme.typography.labelLarge,
                color = colors.fgMuted,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        PageHeader(title, Modifier.padding(top = 6.dp))
    }
}
