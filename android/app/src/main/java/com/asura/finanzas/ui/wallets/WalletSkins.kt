package com.asura.finanzas.ui.wallets

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.asura.finanzas.ui.parseHexColor

/**
 * Wallet card art, ported from `src/lib/skins.ts`. Original gradients (no bank
 * marks). A wallet stores a catalog id, a `grad:<hex>` custom gradient, an
 * `img:` import, or nothing — and "nothing" does **not** mean "use the wallet
 * colour": the web falls back to a default per wallet category, which is why an
 * Efectivo wallet is leather-brown before anyone picks a skin.
 */
data class WalletSkin(
    val colors: List<Color>,
    /** Text colour that reads on this background. */
    val fg: Color,
    /** Motif drawn behind the figures. */
    val art: SkinArt,
)

/** Card skins get a chip; cash skins get a money illustration instead. */
enum class SkinArt { Chip, Banknote, Wallet, Coins, Piggy, None }

private fun skin(vararg hex: Long, fg: Long, art: SkinArt = SkinArt.Chip) =
    WalletSkin(hex.map { Color(it) }, Color(fg), art)

private val CATALOG: Map<String, WalletSkin> = mapOf(
    // color tones
    "azul" to skin(0xFF0A3D91, 0xFF1565D8, 0xFF0A3D91, fg = 0xFFEAF2FF),
    "marino" to skin(0xFF0B1F4D, 0xFF16357E, fg = 0xFFE8EEFC),
    "turquesa" to skin(0xFF0E7490, 0xFF22B8CF, fg = 0xFFE9FBFF),
    "rojo" to skin(0xFF9E1414, 0xFFE1232B, 0xFF9E1414, fg = 0xFFFFF0F0),
    "vino" to skin(0xFF5B0B22, 0xFF9C1F3D, fg = 0xFFFFEEF2),
    "morado" to skin(0xFF5B1EA6, 0xFF9333EA, 0xFF6D28D9, fg = 0xFFF6EFFE),
    "verde" to skin(0xFF065F46, 0xFF10B981, fg = 0xFFEAFFF4),
    // tiers
    "oro" to skin(0xFFB8860B, 0xFFF5D479, 0xFFCAA12F, fg = 0xFF3A2C06),
    "platino" to skin(0xFF9AA3AD, 0xFFE6EBF0, 0xFFAEB6C0, fg = 0xFF23262E),
    "black" to skin(0xFF0A0A0F, 0xFF23232E, 0xFF0A0A0F, fg = 0xFFECE9F5),
    "infinite" to skin(0xFF0B1026, 0xFF1E2A78, 0xFF0B1026, fg = 0xFFE7EEFF),
    // cash — money illustrations, no chip
    "efectivo" to skin(0xFF1B5E3A, 0xFF2F9E63, 0xFF1B5E3A, fg = 0xFFEAFFF0, art = SkinArt.Banknote),
    "cuero" to skin(0xFF5A3413, 0xFF8A5A2B, 0xFF4A2A0F, fg = 0xFFFBEEDE, art = SkinArt.Wallet),
    "monedas" to skin(0xFF7A5210, 0xFFD9A93A, 0xFF6B450C, fg = 0xFFFFF6E6, art = SkinArt.Coins),
    "ahorro" to skin(0xFF0B3B46, 0xFF0EA5A3, 0xFF0B3B46, fg = 0xFFEAFDFF, art = SkinArt.Piggy),
    // neon glass — matches the app
    "neon" to skin(0xFF7C3AED, 0xFFA855F7, 0xFF22D3EE, fg = 0xFFF4F0FF),
    "holo" to skin(0xFFA5F3FC, 0xFFC4B5FD, 0xFFFBCFE8, 0xFFFDE68A, fg = 0xFF1A1430),
    "noche" to skin(0xFF141228, 0xFF2A2350, fg = 0xFFE8E6F4),
)

/**
 * Default skin per wallet category, so each category looks distinct before the
 * user picks one. Matches `categoryDefaultSkin` in the web (seeded es-MX names).
 */
private fun categoryDefaultSkin(categoryName: String?): String {
    val c = (categoryName ?: "").lowercase()
    return when {
        c.contains("efectivo") -> "cuero"
        c.contains("crédito") || c.contains("credito") -> "morado"
        c.contains("débito") || c.contains("debito") -> "azul"
        c.contains("ahorro") -> "ahorro"
        else -> "neon"
    }
}

/** Resolve a wallet's stored skin (or its category default) to a gradient. */
fun walletSkin(skinValue: String?, walletColor: String?, categoryName: String?): WalletSkin {
    if (!skinValue.isNullOrBlank() && skinValue != "null") {
        if (skinValue.startsWith("grad:")) {
            val parts = skinValue.removePrefix("grad:").split(",")
            val from = parseHexColor(parts.getOrNull(0))
            val to = parseHexColor(parts.getOrNull(1)) ?: from?.let { mixWithBlack(it) }
            if (from != null && to != null) {
                return WalletSkin(listOf(from, to), Color.White, SkinArt.Chip)
            }
        }
        // `img:` skins are user-imported artwork; not supported yet, so fall
        // through to the category default rather than showing a broken card.
        CATALOG[skinValue]?.let { return it }
    }

    // An explicit colour still wins over the category default, matching the
    // web's resolveSkin(null, color) path.
    val explicit = parseHexColor(walletColor?.takeIf { it != "null" })
    if (explicit != null) {
        return WalletSkin(listOf(explicit, mixWithBlack(explicit)), Color.White, SkinArt.Chip)
    }

    return CATALOG.getValue(categoryDefaultSkin(categoryName))
}

/** The web's `color-mix(in oklab, c 52%, #000)`, close enough in sRGB. */
private fun mixWithBlack(color: Color, keep: Float = 0.52f) =
    Color(
        red = color.red * keep,
        green = color.green * keep,
        blue = color.blue * keep,
        alpha = color.alpha,
    )

/** 135° gradient, matching the web's `linear-gradient(135deg, …)`. */
fun WalletSkin.brush(): Brush {
    val stops = if (colors.size == 1) colors + colors else colors
    return Brush.linearGradient(
        colors = stops,
        start = Offset.Zero,
        end = Offset.Infinite,
    )
}
