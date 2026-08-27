package com.asura.finanzas.ui.wallets

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.asura.finanzas.ui.parseHexColor

/**
 * Wallet card art, ported from `src/lib/skins.ts`. Original gradients (no bank
 * marks); a wallet stores either a catalog id, a `grad:<hex>` custom gradient,
 * or nothing — in which case the card is derived from the wallet's colour, the
 * same fallback the web uses.
 */
data class WalletSkin(
    val colors: List<Color>,
    /** Text colour that reads on this background. */
    val fg: Color,
)

private fun skin(vararg hex: Long, fg: Long) =
    WalletSkin(hex.map { Color(it) }, Color(fg))

private val CATALOG: Map<String, WalletSkin> = mapOf(
    "azul" to skin(0xFF0A3D91, 0xFF1565D8, 0xFF0A3D91, fg = 0xFFEAF2FF),
    "marino" to skin(0xFF0B1F4D, 0xFF16357E, fg = 0xFFE8EEFC),
    "turquesa" to skin(0xFF0E7490, 0xFF22B8CF, fg = 0xFFE9FBFF),
    "rojo" to skin(0xFF9E1414, 0xFFE1232B, 0xFF9E1414, fg = 0xFFFFF0F0),
    "vino" to skin(0xFF5B0B22, 0xFF9C1F3D, fg = 0xFFFFEEF2),
    "morado" to skin(0xFF5B1EA6, 0xFF9333EA, 0xFF6D28D9, fg = 0xFFF6EFFE),
    "verde" to skin(0xFF065F46, 0xFF10B981, fg = 0xFFEAFFF4),
    "oro" to skin(0xFFB8860B, 0xFFF5D479, 0xFFCAA12F, fg = 0xFF3A2C06),
    "platino" to skin(0xFF9AA3AD, 0xFFE6EBF0, 0xFFAEB6C0, fg = 0xFF23262E),
    "black" to skin(0xFF0A0A0F, 0xFF23232E, 0xFF0A0A0F, fg = 0xFFECE9F5),
    "infinite" to skin(0xFF0B1026, 0xFF1E2A78, 0xFF0B1026, fg = 0xFFE7EEFF),
    "efectivo" to skin(0xFF1B5E3A, 0xFF2F9E63, 0xFF1B5E3A, fg = 0xFFEAFFF0),
    "cuero" to skin(0xFF5A3413, 0xFF8A5A2B, 0xFF4A2A0F, fg = 0xFFFBEEDE),
    "monedas" to skin(0xFF7A5210, 0xFFD9A93A, 0xFF6B450C, fg = 0xFFFFF6E6),
    "ahorro" to skin(0xFF0B3B46, 0xFF0EA5A3, 0xFF0B3B46, fg = 0xFFEAFDFF),
)

/** Resolve a wallet's stored skin (or its colour) to a gradient. */
fun walletSkin(skinValue: String?, walletColor: String?, fallback: Color): WalletSkin {
    CATALOG[skinValue]?.let { return it }

    if (skinValue != null && skinValue.startsWith("grad:")) {
        val parts = skinValue.removePrefix("grad:").split(",")
        val from = parseHexColor(parts.getOrNull(0))
        val to = parseHexColor(parts.getOrNull(1)) ?: from?.let { darken(it) }
        if (from != null && to != null) return WalletSkin(listOf(from, to), Color.White)
    }

    // No skin: a gradient derived from the wallet's own colour, like the web.
    val base = parseHexColor(walletColor) ?: fallback
    return WalletSkin(listOf(darken(base), base), Color.White)
}

private fun darken(color: Color, factor: Float = 0.55f) =
    Color(
        red = color.red * factor,
        green = color.green * factor,
        blue = color.blue * factor,
        alpha = color.alpha,
    )

fun WalletSkin.brush(): Brush =
    Brush.linearGradient(if (colors.size == 1) colors + colors else colors)
