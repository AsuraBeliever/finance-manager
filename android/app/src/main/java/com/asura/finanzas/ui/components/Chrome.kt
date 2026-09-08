package com.asura.finanzas.ui.components

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
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
                // The CSS pseudo-element is `inset: -20%`, i.e. a box 140% of the
                // viewport in both axes, and the blob positions are percentages of
                // *that* box — not of the screen.
                val boxW = size.width * 1.4f
                val boxH = size.height * 1.4f
                val boxX = -0.2f * size.width
                val boxY = -0.2f * size.height
                // mesh-drift: translate3d(-3%, 2%) scale(1.08), percentages of the
                // element's own size, the scale taken about its centre.
                val dx = -0.03f * boxW * drift
                val dy = 0.02f * boxH * drift
                val scale = 1f + 0.08f * drift
                val cx = boxX + boxW / 2f
                val cy = boxY + boxH / 2f

                fun blob(
                    color: androidx.compose.ui.graphics.Color,
                    fx: Float,
                    fy: Float,
                    remRadius: Float,
                    stop: Float,
                ) {
                    // `radial-gradient(R at c, color, transparent S)` ramps linearly
                    // from the colour at the centre to nothing at S of R, so the
                    // drawn circle is that shorter radius, not R.
                    val radius = remRadius * 16.dp.toPx() * stop * scale
                    val center = Offset(
                        cx + (boxX + boxW * fx - cx) * scale + dx,
                        cy + (boxY + boxH * fy - cy) * scale + dy,
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(color, androidx.compose.ui.graphics.Color.Transparent),
                            center = center,
                            radius = radius,
                        ),
                        radius = radius,
                        center = center,
                    )
                }

                // In a CSS `background` shorthand the first layer paints on top, so
                // these go on in reverse of the order they are listed there.
                blob(colors.mesh2, 0.75f, 0.88f, 40f, 0.62f)
                blob(colors.mesh3, 0.85f, 0.08f, 34f, 0.60f)
                blob(colors.mesh1, 0.18f, 0.12f, 38f, 0.60f)
            },
    ) {
        content()
    }
}

/**
 * Page title: a cyan rule followed by the name of the screen in the display
 * face — the web's `PageHeader`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    /** Gap between the actions themselves — the web's `gap-4`, or `gap-2`. */
    actionGap: androidx.compose.ui.unit.Dp = 16.dp,
    actions: @Composable FlowRowScope.() -> Unit = {},
) {
    val colors = Broke.colors
    // `flex flex-wrap items-end justify-between gap-3`: the actions ride the
    // title's line whenever they fit, and only drop below when they do not.
    // Stacking them unconditionally added a row to every screen.
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Same tab and title metrics as the web's PageHeader: a 4×28 rule
            // in the "gold" accent (cyan here) and a 1.9rem display title.
            Box(
                Modifier
                    .width(4.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.cyan),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 30.sp, lineHeight = 30.sp),
                color = colors.fg,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        FlowRow(
            // `items-end`: the actions sit on the title's baseline, not at the
            // top of its line box.
            modifier = Modifier.align(Alignment.Bottom),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(actionGap),
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
    // rounded-2xl, like every card on the web. A rounder corner is one of the
    // first things that reads as "a different app" when the two sit together.
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Broke.colors.surfaceRaised)
            .border(1.dp, Broke.colors.borderMuted, RoundedCornerShape(16.dp))
            .padding(padding),
        content = content,
    )
}

/**
 * All-caps, letter-spaced micro label ("NET WORTH", "POCKETS · 2") — the web's
 * `.eyebrow`, whose 0.18em on an 11.2px face is the 2 sp default here. The
 * web also has a second, barely-tracked variant (`tracking-wide`, 0.025em) for
 * the labels that sit right on top of a figure; those pass `letterSpacing`.
 */
@Composable
fun MicroLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color? = null,
    letterSpacing: androidx.compose.ui.unit.TextUnit = 2.sp,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            letterSpacing = letterSpacing,
            fontSize = 11.sp,
            // Compose's own leading would sit the glyphs ~8 px lower than the
            // browser's line box does, which throws off every gap below.
            lineHeight = 15.sp,
        ),
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
            // `font-semibold` on the money heroes; the headers stay medium.
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Defaults to a plain "Back"; settings passes its own wording. */
    backLabel: String? = null,
    actions: @Composable FlowRowScope.() -> Unit = {},
) {
    val colors = Broke.colors
    // These screens are pushed inside a tab rather than routed, so nothing was
    // listening for the system gesture: back closed the whole app.
    BackHandler(onBack = onBack)
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
        PageHeader(title, Modifier.padding(top = 6.dp), actions = actions)
    }
}
