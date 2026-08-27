package com.asura.finanzas.ui

import androidx.compose.ui.graphics.Color

/**
 * Wallet and category swatches arrive as CSS hex strings ("#a855f7") chosen in
 * the web app. Anything unparseable falls back to null so the caller can use the
 * theme accent instead of crashing on a value someone typed by hand.
 */
fun parseHexColor(hex: String?): Color? {
    val raw = hex?.trim()?.removePrefix("#") ?: return null
    val normalized = when (raw.length) {
        3 -> raw.map { "$it$it" }.joinToString("")
        6 -> raw
        8 -> raw // #rrggbbaa
        else -> return null
    }
    val value = normalized.toLongOrNull(16) ?: return null
    return if (normalized.length == 8) {
        // hex is RRGGBBAA; Compose wants AARRGGBB
        val rgb = value ushr 8
        val alpha = value and 0xFF
        Color((alpha shl 24 or rgb).toInt())
    } else {
        Color(value.toInt() or 0xFF000000.toInt())
    }
}
