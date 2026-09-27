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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asura.finanzas.R
import com.asura.finanzas.ui.theme.Broke
import com.asura.finanzas.ui.theme.TrackingEyebrow
import com.asura.finanzas.ui.theme.tabular

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
    actions: @Composable () -> Unit = {},
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            // `items-end` for the title too: next to taller actions it sits on
            // their bottom edge, not at the top of the line.
            // The header's `gap-3`. `SpaceBetween` alone never reserves it, so
            // the actions rode the title's line at widths where flexbox had
            // already wrapped them — Movimientos was one line here and two in
            // the browser. Carried as end padding, it counts towards the wrap
            // and is swallowed by the free space when they do fit.
            modifier = Modifier.align(Alignment.Bottom).padding(end = 12.dp),
        ) {
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
                // No override: `displayLarge` is already the page title's
                // `text-[1.9rem] leading-none tracking-tight`, to the tenth.
                style = MaterialTheme.typography.displayLarge,
                color = colors.fg,
                modifier = Modifier
                    .padding(start = 12.dp)
                    // `leading-none`: the line box is the font size, 30.4.
                    .cssLineBox(30.4.dp),
            )
        }
        // `items-end`: the actions sit on the title's baseline, not at the top
        // of its line box. Among themselves they are the web's `flex
        // items-center gap-4`: one row that never wraps, where a crowded row
        // shrinks its items the way flexbox does — labels break onto a
        // second line rather than anything being pushed off.
        FlexShrinkRow(
            gap = actionGap,
            modifier = Modifier.align(Alignment.Bottom),
            content = actions,
        )
    }
}

/**
 * A `flex items-center` row with CSS's shrinking, exactly: children start at
 * their natural (max-content) width; if together they overflow, each gives up
 * room in proportion to that width, but never below its min-content width (a
 * label's longest word). Whatever the row cannot shrink away is left to
 * overflow, as in the browser.
 */
@Composable
fun FlexShrinkRow(
    gap: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    /** `justify-between`: the first child at the start, the last at the end. */
    spaceBetween: Boolean = false,
    content: @Composable () -> Unit,
) {
    val policy = remember(gap, spaceBetween) {
        object : androidx.compose.ui.layout.MeasurePolicy {
            override fun androidx.compose.ui.layout.MeasureScope.measure(
                measurables: List<androidx.compose.ui.layout.Measurable>,
                constraints: androidx.compose.ui.unit.Constraints,
            ): androidx.compose.ui.layout.MeasureResult {
        val gapPx = gap.roundToPx()
        val n = measurables.size
        val basis = measurables.map { it.maxIntrinsicWidth(androidx.compose.ui.unit.Constraints.Infinity) }
        val floor = measurables.map { it.minIntrinsicWidth(androidx.compose.ui.unit.Constraints.Infinity) }
        val widths = basis.toIntArray()
        if (constraints.hasBoundedWidth) {
            val avail = constraints.maxWidth - gapPx * (n - 1).coerceAtLeast(0)
            val frozen = BooleanArray(n)
            while (true) {
                val used = (0 until n).sumOf { widths[it] }
                val over = used - avail
                if (over <= 0) break
                val weight = (0 until n).filter { !frozen[it] }.sumOf { basis[it].toLong() }
                if (weight == 0L) break
                var froze = false
                val next = widths.copyOf()
                for (i in 0 until n) {
                    if (frozen[i]) continue
                    val target = widths[i] - Math.ceil(over.toDouble() * basis[i] / weight).toInt()
                    if (target <= floor[i]) {
                        next[i] = floor[i]; frozen[i] = true; froze = true
                    } else {
                        next[i] = target
                    }
                }
                for (i in 0 until n) widths[i] = next[i]
                if (!froze) break
            }
        }
        val placeables = measurables.mapIndexed { i, m ->
            m.measure(androidx.compose.ui.unit.Constraints(minWidth = widths[i], maxWidth = widths[i]))
        }
        val height = placeables.maxOfOrNull { it.height } ?: 0
        val natural = placeables.sumOf { it.width } + gapPx * (n - 1).coerceAtLeast(0)
        val width = if (spaceBetween && constraints.hasBoundedWidth) constraints.maxWidth
        else natural.coerceAtMost(if (constraints.hasBoundedWidth) constraints.maxWidth else natural)
        val extra = if (spaceBetween && n > 1) ((width - natural).coerceAtLeast(0)) / (n - 1) else 0
        return layout(width, height) {
            var x = 0
            placeables.forEach { p ->
                p.placeRelative(x, (height - p.height) / 2)
                x += p.width + gapPx + extra
            }
        }
                }

            // What the row can shrink to is every child at its min-content;
            // what it wants is every child at its max-content.
            override fun androidx.compose.ui.layout.IntrinsicMeasureScope.minIntrinsicWidth(
                measurables: List<androidx.compose.ui.layout.IntrinsicMeasurable>,
                height: Int,
            ) = measurables.sumOf { it.minIntrinsicWidth(height) } + gap.roundToPx() * (measurables.size - 1).coerceAtLeast(0)

            override fun androidx.compose.ui.layout.IntrinsicMeasureScope.maxIntrinsicWidth(
                measurables: List<androidx.compose.ui.layout.IntrinsicMeasurable>,
                height: Int,
            ) = measurables.sumOf { it.maxIntrinsicWidth(height) } + gap.roundToPx() * (measurables.size - 1).coerceAtLeast(0)

            override fun androidx.compose.ui.layout.IntrinsicMeasureScope.minIntrinsicHeight(
                measurables: List<androidx.compose.ui.layout.IntrinsicMeasurable>,
                width: Int,
            ) = measurables.maxOfOrNull { it.minIntrinsicHeight(androidx.compose.ui.unit.Constraints.Infinity) } ?: 0

            override fun androidx.compose.ui.layout.IntrinsicMeasureScope.maxIntrinsicHeight(
                measurables: List<androidx.compose.ui.layout.IntrinsicMeasurable>,
                width: Int,
            ) = measurables.maxOfOrNull { it.maxIntrinsicHeight(androidx.compose.ui.unit.Constraints.Infinity) } ?: 0
        }
    }
    androidx.compose.ui.layout.Layout(content = content, modifier = modifier, measurePolicy = policy)
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
    /** Painted over the card's fill and under its content — an absolutely
     *  positioned decoration inside the web's card. */
    decoration: (androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // rounded-2xl, like every card on the web. A rounder corner is one of the
    // first things that reads as "a different app" when the two sit together.
    Column(
        modifier = modifier
            .cardShadow()
            .clip(RoundedCornerShape(16.dp))
            .background(Broke.colors.surfaceRaised)
            .cardHighlight()
            .then(if (decoration != null) Modifier.drawBehind(decoration) else Modifier)
            .border(1.dp, Broke.colors.borderMuted, RoundedCornerShape(16.dp))
            // The browser counts the 1 px border inside the box and pads from
            // it; Compose draws the border over the padding. So `p-5` is 21.
            .padding(padding + 1.dp),
        content = content,
    )
}

/**
 * The web's plainer box: `rounded-xl border border-border-muted
 * bg-surface-raised p-5` — a wallet's balance, its card panel — with no
 * `shadow-card` and a tighter corner than [GlassCard].
 */
@Composable
fun PanelCard(
    modifier: Modifier = Modifier,
    padding: androidx.compose.ui.unit.Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Broke.colors.surfaceRaised)
            .border(1.dp, Broke.colors.borderMuted, RoundedCornerShape(12.dp))
            .padding(padding + 1.dp),
        content = content,
    )
}

/**
 * The drop half of `shadow-card`: `0 10px 34px -16px` violet at 30 % (dark:
 * `0 8px 36px -16px` at 45 %). The negative spread tucks it under the card, so
 * it only shows as a soft glow along the bottom edge.
 */
@Composable
fun Modifier.cardShadow(radius: androidx.compose.ui.unit.Dp = 16.dp): Modifier {
    val dark = Broke.colors.isDark
    return cssBoxShadow(
        offsetY = if (dark) 8.dp else 10.dp,
        blur = if (dark) 36.dp else 34.dp,
        spread = (-16).dp,
        color = androidx.compose.ui.graphics.Color(124, 58, 237).copy(alpha = if (dark) 0.45f else 0.30f),
        radius = radius,
    )
}

/**
 * A CSS `box-shadow: 0 <offsetY> <blur> <spread> <color>` behind a box with
 * corner [radius]. Like the browser, it is never painted under the box itself
 * — these cards are see-through, and it glowed through them otherwise.
 */
fun Modifier.cssBoxShadow(
    offsetY: androidx.compose.ui.unit.Dp,
    blur: androidx.compose.ui.unit.Dp,
    spread: androidx.compose.ui.unit.Dp,
    color: androidx.compose.ui.graphics.Color,
    radius: androidx.compose.ui.unit.Dp,
): Modifier = drawBehind {
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        this.color = android.graphics.Color.argb(
            (color.alpha * 255).toInt(), (color.red * 255).toInt(),
            (color.green * 255).toInt(), (color.blue * 255).toInt(),
        )
        // CSS blur radius B is a Gaussian with σ = B / 2; Android's mask
        // radius r means σ ≈ 0.57735 r + 0.5.
        maskFilter = android.graphics.BlurMaskFilter(
            (((blur.toPx() / 2f) - 0.5f) / 0.57735f).coerceAtLeast(0.1f),
            android.graphics.BlurMaskFilter.Blur.NORMAL,
        )
    }
    val canvas = drawContext.canvas.nativeCanvas
    canvas.save()
    val r = radius.toPx()
    canvas.clipOutPath(
        android.graphics.Path().apply {
            addRoundRect(0f, 0f, size.width, size.height, r, r, android.graphics.Path.Direction.CW)
        },
    )
    val sp = spread.toPx()
    val oy = offsetY.toPx()
    val cr = (r + sp).coerceAtLeast(0f)
    canvas.drawRoundRect(-sp, oy - sp, size.width + sp, size.height + oy + sp, cr, cr, paint)
    canvas.restore()
}

/** The inset half of `shadow-card`: a 1 px highlight just inside the top border. */
@Composable
fun Modifier.cardHighlight(): Modifier {
    val dark = Broke.colors.isDark
    return drawBehind {
        drawRect(
            color = androidx.compose.ui.graphics.Color.White.copy(alpha = if (dark) 0.07f else 0.85f),
            topLeft = androidx.compose.ui.geometry.Offset(0f, density),
            size = androidx.compose.ui.geometry.Size(size.width, density),
        )
    }
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
    letterSpacing: androidx.compose.ui.unit.TextUnit = TrackingEyebrow,
    /**
     * `.eyebrow` is medium; the plainer `uppercase tracking-wide` captions the
     * web scatters around (a stat's name, a date) are regular. Same size, and
     * at 11 px the difference in weight is the whole difference.
     */
    fontWeight: androidx.compose.ui.text.font.FontWeight =
        androidx.compose.ui.text.font.FontWeight.Medium,
) {
    Text(
        text = text.uppercase(),
        // `.eyebrow`: 0.7rem over a 1.5 line box, tracked wide.
        style = MaterialTheme.typography.labelSmall.copy(
            letterSpacing = letterSpacing,
            fontWeight = fontWeight,
            fontSize = 11.2.sp,
            lineHeight = 16.8.sp,
        ),
        color = color ?: Broke.colors.fgMuted,
        modifier = modifier,
    )
}

/**
 * A single line of text laid out in a line box shorter than its font, as CSS
 * does with `leading-none` or `text-4xl`'s 2.5rem: the box is [height] and the
 * glyphs spill over it evenly. Compose never lays a line out shorter than the
 * font's own ascent + descent, so without this every such heading pushed the
 * rest of the page down (7.6 dp under a page title, 5.3 under a hero figure).
 */
fun Modifier.cssLineBox(height: androidx.compose.ui.unit.Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val box = height.roundToPx()
    if (placeable.height <= box) {
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    } else {
        layout(placeable.width, box) { placeable.place(0, (box - placeable.height) / 2) }
    }
}

/**
 * A negative top margin (`-mt-2`): the box is drawn [amount] higher and takes
 * that much less room, pulling itself into the gap above it.
 */
fun Modifier.pullUp(amount: androidx.compose.ui.unit.Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val px = amount.roundToPx()
    layout(placeable.width, (placeable.height - px).coerceAtLeast(0)) { placeable.place(0, -px) }
}

/** The hero money figure: display face with the violet→cyan gradient. */
@Composable
fun HeroAmount(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 40.sp,
) {
    val colors = Broke.colors
    // `--hero-gradient`: 110deg, the two violets swapping places between the
    // themes, and the cyan owning everything past the first fifth.
    val first = if (colors.isDark) colors.accentBright else colors.accent
    val second = if (colors.isDark) colors.accent else colors.accentBright
    val brush = remember(first, second, colors.cyan) { cssLinearGradient(110f, listOf(0f to first, 0.2f to second, 1f to colors.cyan)) }
    Text(
        text = text,
        style = MaterialTheme.typography.displayLarge.tabular().copy(
            fontSize = fontSize,
            // `text-4xl` is 2.25rem over a 2.5rem line box.
            lineHeight = fontSize * 1.1111f,
            // `font-semibold` on the money heroes; the headers stay medium.
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            brush = brush,
            // `--hero-glow`, the drop-shadow the web casts in the accent.
            shadow = androidx.compose.ui.graphics.Shadow(
                color = colors.accent.copy(alpha = if (colors.isDark) 0.45f else 0.28f),
                blurRadius = with(androidx.compose.ui.platform.LocalDensity.current) {
                    (if (colors.isDark) 19.dp else 13.dp).toPx()
                },
            ),
        ),
        maxLines = 1,
        modifier = modifier.cssLineBox(with(androidx.compose.ui.platform.LocalDensity.current) { (fontSize * 1.1111f).toDp() }),
    )
}

/**
 * A CSS `linear-gradient(<angle>deg, …)` over whatever box it paints: the line
 * runs through the centre at that angle (0 = up, 90 = right) and is exactly
 * long enough for the corners to land on the first and last stop.
 */
fun cssLinearGradient(angleDeg: Float, stops: List<Pair<Float, androidx.compose.ui.graphics.Color>>): Brush =
    object : androidx.compose.ui.graphics.ShaderBrush() {
        override fun createShader(size: androidx.compose.ui.geometry.Size): androidx.compose.ui.graphics.Shader {
            val a = Math.toRadians(angleDeg.toDouble())
            val dx = kotlin.math.sin(a).toFloat()
            val dy = -kotlin.math.cos(a).toFloat()
            val half = (kotlin.math.abs(size.width * dx) + kotlin.math.abs(size.height * dy)) / 2f
            val c = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
            return androidx.compose.ui.graphics.LinearGradientShader(
                from = c - androidx.compose.ui.geometry.Offset(dx * half, dy * half),
                to = c + androidx.compose.ui.geometry.Offset(dx * half, dy * half),
                colors = stops.map { it.second },
                colorStops = stops.map { it.first },
            )
        }
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

/**
 * The web's `EmptyState`: a dashed card with an accent badge above the title.
 *
 * It was two bare centred texts here, which on a screen with nothing else on
 * it read as a page that had failed to load rather than one with nothing in
 * it yet. Every measurement is the web's — `rounded-2xl`, `py-16`, `gap-3`, a
 * 56 px badge on `accent-dim/15` inside a `ring-1 ring-accent/20`, and a
 * 1 px dashed border whose 3 dp/2 dp rhythm is what the browser draws.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val colors = Broke.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceRaised.copy(alpha = 0.4f))
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawRoundRect(
                    color = colors.borderMuted,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = androidx.compose.ui.geometry.Size(
                        size.width - stroke,
                        size.height - stroke,
                    ),
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(
                        width = stroke,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(3.dp.toPx(), 2.dp.toPx()),
                        ),
                    ),
                )
            }
            .padding(vertical = 64.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(shape)
                .background(colors.accentDim.copy(alpha = 0.15f))
                .border(1.dp, colors.accent.copy(alpha = 0.2f), shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            title,
            // The only `font-display text-lg` in the app without
            // `tracking-tight`, so the role's has to be undone here.
            style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 0.sp),
            color = colors.fg,
            textAlign = TextAlign.Center,
        )
        if (description.isNotBlank()) {
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = colors.fgSubtle,
                textAlign = TextAlign.Center,
                // `max-w-sm`, so a long line breaks before the card edge.
                modifier = Modifier.widthIn(max = 384.dp),
            )
        }
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
    actions: @Composable () -> Unit = {},
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
                Lucide.ArrowLeft,
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
