package com.asura.finanzas.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The handful of Lucide glyphs the web draws, as native vectors.
 *
 * Material's icons are close cousins, not the same drawing: side by side with
 * the web the wallet and piggy read as different symbols, which is exactly the
 * kind of difference this app is not allowed to have. These are the same 24×24
 * stroked outlines lucide-react ships, copied from its path data, so a wallet
 * card looks identical on both surfaces.
 *
 * Keep them in sync with `node_modules/lucide-react/dist/esm/icons/<name>.mjs`.
 */
object Lucide {
    val Wallet by lazy {
        icon(
            "M19 7V4a1 1 0 0 0-1-1H5a2 2 0 0 0 0 4h15a1 1 0 0 1 1 1v4h-3a2 2 0 0 0 0 4h3a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1",
            "M3 5v14a2 2 0 0 0 2 2h15a1 1 0 0 0 1-1v-4",
        )
    }

    val Banknote by lazy {
        icon(
            "M4 6h16a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z",
            "M14 12a2 2 0 1 1-4 0 2 2 0 1 1 4 0z",
            "M6 12h.01",
            "M18 12h.01",
        )
    }

    val Coins by lazy {
        icon(
            "M13.744 17.736a6 6 0 1 1-7.48-7.48",
            "M15 6h1v4",
            "m6.134 14.768.866-.5 2 3.464",
            "M22 8a6 6 0 1 1-12 0 6 6 0 1 1 12 0z",
        )
    }

    val PiggyBank by lazy {
        icon(
            "M11 17h3v2a1 1 0 0 0 1 1h2a1 1 0 0 0 1-1v-3a3.16 3.16 0 0 0 2-2h1a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1h-1a5 5 0 0 0-2-4V3a4 4 0 0 0-3.2 1.6l-.3.4H11a6 6 0 0 0-6 6v1a5 5 0 0 0 2 4v3a1 1 0 0 0 1 1h2a1 1 0 0 0 1-1z",
            "M16 10h.01",
            "M2 8v1a2 2 0 0 0 2 2h1",
        )
    }

    val CreditCard by lazy {
        icon(
            "M4 5h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z",
            "M2 10h20",
        )
    }
}

/**
 * Builds one Lucide glyph. They are all strokes on a 24×24 grid with round caps
 * and joins and no fill — a dot like `M16 10h.01` only shows up because of the
 * round cap, so those settings are not cosmetic.
 */
private fun icon(vararg pathData: String): ImageVector =
    ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        for (d in pathData) {
            addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()
