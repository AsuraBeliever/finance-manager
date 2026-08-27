package com.asura.finanzas.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

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
    val fg: Color,
    val fgMuted: Color,
    val fgSubtle: Color,
    val isDark: Boolean,
)

private val DarkTokens = BrokeColors(
    surface = Color(0xFF0B0A16),
    surfaceRaised = Color(0x991A1730),
    surfaceOverlay = Color(0xFF1B1830),
    borderMuted = Color(0x1AFFFFFF),
    accent = Color(0xFFA855F7),
    accentDim = Color(0xFF7C3AED),
    accentBright = Color(0xFFC084FC),
    cyan = Color(0xFF22D3EE),
    danger = Color(0xFFFB7185),
    positive = Color(0xFF34D399),
    fg = Color(0xFFF2EFFF),
    fgMuted = Color(0xFFA8A2C8),
    fgSubtle = Color(0xFF6F6A8D),
    isDark = true,
)

private val LightTokens = BrokeColors(
    surface = Color(0xFFF4F2FF),
    surfaceRaised = Color(0xA8FFFFFF),
    surfaceOverlay = Color(0xFFECE9FB),
    borderMuted = Color(0x297C3AED),
    accent = Color(0xFF8B5CF6),
    accentDim = Color(0xFF7C3AED),
    accentBright = Color(0xFFA78BFA),
    cyan = Color(0xFF06B6D4),
    danger = Color(0xFFE5484D),
    positive = Color(0xFF10B981),
    fg = Color(0xFF211A3A),
    fgMuted = Color(0xFF6B6488),
    fgSubtle = Color(0xFF9A93B5),
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
    content: @Composable () -> Unit,
) {
    val tokens = if (darkTheme) DarkTokens else LightTokens
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

    CompositionLocalProvider(LocalBrokeColors provides tokens) {
        MaterialTheme(colorScheme = scheme, typography = BrokeTypography, content = content)
    }
}
