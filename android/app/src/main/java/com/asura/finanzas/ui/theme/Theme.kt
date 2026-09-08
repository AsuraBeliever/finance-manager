package com.asura.finanzas.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.asura.finanzas.data.Appearance
import com.asura.finanzas.ui.parseHexColor

/**
 * The web app's "neon glass" design system, ported to Compose: an anime-pop
 * palette (violet · pink · cyan) over a deep indigo canvas. Values mirror the
 * `--c-*` custom properties in `src/index.css` — when a token changes there, it
 * changes here too, or the two clients stop looking like the same product.
 */
data class BrokeColors(
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceOverlay: Color,
    val borderMuted: Color,
    val accent: Color,
    val accentDim: Color,
    val accentBright: Color,
    /** The cyan "pop" secondary (named `--c-gold` in CSS for historical reasons). */
    val cyan: Color,
    val danger: Color,
    val positive: Color,
    /** Amber, for the "queued, not synced yet" state. Matches the web panel. */
    val warning: Color,
    val fg: Color,
    val fgMuted: Color,
    val fgSubtle: Color,
    /** The three soft blobs of the gradient-mesh canvas (--mesh-1..3). */
    val mesh1: Color,
    val mesh2: Color,
    val mesh3: Color,
    val isDark: Boolean,
)

private val DarkTokens = BrokeColors(
    surface = Color(0xFF0B0A16),
    surfaceRaised = Color(0x991A1730),
    surfaceOverlay = Color(0xFF1B1830),
    borderMuted = Color(0x1AFFFFFF),
    warning = Color(0xFFF59E0B),
    accent = Color(0xFFA855F7),
    accentDim = Color(0xFF7C3AED),
    accentBright = Color(0xFFC084FC),
    cyan = Color(0xFF22D3EE),
    danger = Color(0xFFFB7185),
    positive = Color(0xFF34D399),
    fg = Color(0xFFF2EFFF),
    fgMuted = Color(0xFFA8A2C8),
    fgSubtle = Color(0xFF6F6A8D),
    mesh1 = Color(0x38A855F7),
    mesh2 = Color(0x24EC4899),
    mesh3 = Color(0x2422D3EE),
    isDark = true,
)

private val LightTokens = BrokeColors(
    surface = Color(0xFFF4F2FF),
    surfaceRaised = Color(0xA8FFFFFF),
    surfaceOverlay = Color(0xFFECE9FB),
    borderMuted = Color(0x297C3AED),
    warning = Color(0xFFD97706),
    accent = Color(0xFF8B5CF6),
    accentDim = Color(0xFF7C3AED),
    accentBright = Color(0xFFA78BFA),
    cyan = Color(0xFF06B6D4),
    danger = Color(0xFFE5484D),
    positive = Color(0xFF10B981),
    fg = Color(0xFF211A3A),
    fgMuted = Color(0xFF6B6488),
    fgSubtle = Color(0xFF9A93B5),
    mesh1 = Color(0x338B5CF6),
    mesh2 = Color(0x1FEC4899),
    mesh3 = Color(0x1F06B6D4),
    isDark = false,
)

val LocalBrokeColors: ProvidableCompositionLocal<BrokeColors> =
    staticCompositionLocalOf { DarkTokens }

/** Shorthand so screens read `Broke.colors.accent`. */
object Broke {
    val colors: BrokeColors
        @Composable get() = LocalBrokeColors.current
}

@Composable
fun BrokeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** User overrides from the appearance settings; defaults change nothing. */
    appearance: Appearance = Appearance(),
    content: @Composable () -> Unit,
) {
    val base = if (darkTheme) DarkTokens else LightTokens
    val tokens = remember(base, appearance) { base.withAppearance(appearance) }
    // Material components (text fields, dialogs, ripples) read the M3 scheme, so
    // it has to carry the same palette as our own tokens.
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = tokens.accent,
            onPrimary = Color(0xFF14061F),
            secondary = tokens.cyan,
            background = tokens.surface,
            onBackground = tokens.fg,
            surface = tokens.surfaceOverlay,
            onSurface = tokens.fg,
            surfaceVariant = tokens.surfaceOverlay,
            onSurfaceVariant = tokens.fgMuted,
            error = tokens.danger,
            outline = tokens.fgSubtle,
        )
    } else {
        lightColorScheme(
            primary = tokens.accent,
            onPrimary = Color.White,
            secondary = tokens.cyan,
            background = tokens.surface,
            onBackground = tokens.fg,
            surface = tokens.surfaceOverlay,
            onSurface = tokens.fg,
            surfaceVariant = tokens.surfaceOverlay,
            onSurfaceVariant = tokens.fgMuted,
            error = tokens.danger,
            outline = tokens.fgSubtle,
        )
    }

    val typography = rememberBrokeTypography(appearance.font)

    CompositionLocalProvider(LocalBrokeColors provides tokens) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

/**
 * Overlay the user's colour choices on a theme's tokens. Accent variants are
 * derived the same way the web's CSS `color-mix` does — darker for `dim`,
 * lighter for `bright` — and the background tint is mixed modestly over the
 * theme base so light/dark still dominates and text stays readable.
 */
private fun BrokeColors.withAppearance(a: Appearance): BrokeColors {
    val accent = parseHexColor(a.accent)
    val gold = parseHexColor(a.gold)
    val tint = parseHexColor(a.surface)
    if (accent == null && gold == null && tint == null) return this

    return copy(
        accent = accent ?: this.accent,
        accentDim = accent?.mix(Color.Black, 0.22f) ?: this.accentDim,
        accentBright = accent?.mix(Color.White, 0.28f) ?: this.accentBright,
        // CSS calls this token --c-gold; here it is the cyan "pop" secondary.
        cyan = gold ?: this.cyan,
        // Same 28% the web mixes, so both surfaces land on the same tint.
        surface = tint?.let { this.surface.mix(it, 0.28f) } ?: this.surface,
    )
}

/** Linear blend in sRGB; enough for a tint, and matches what the web produces. */
private fun Color.mix(other: Color, amount: Float): Color = Color(
    red = red + (other.red - red) * amount,
    green = green + (other.green - green) * amount,
    blue = blue + (other.blue - blue) * amount,
    alpha = alpha,
)
