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

    val ArrowDownLeft by lazy { icon("M17 7 7 17", "M17 17H7V7") }

    val ArrowUpRight by lazy { icon("M7 7h10v10", "M7 17 17 7") }

    val ArrowLeftRight by lazy {
        icon("M8 3 4 7l4 4", "M4 7h16", "m16 21 4-4-4-4", "M20 17H4")
    }

    val Pencil by lazy {
        icon(
            "M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z",
            "m15 5 4 4",
        )
    }

    val LayoutDashboard by lazy {
        icon(
            "M4 3h5a1 1 0 0 1 1 1v7a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z",
            "M15 3h5a1 1 0 0 1 1 1v3a1 1 0 0 1-1 1h-5a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z",
            "M15 12h5a1 1 0 0 1 1 1v7a1 1 0 0 1-1 1h-5a1 1 0 0 1-1-1v-7a1 1 0 0 1 1-1z",
            "M4 16h5a1 1 0 0 1 1 1v3a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-3a1 1 0 0 1 1-1z",
        )
    }

    val TrendingUp by lazy { icon("M16 7h6v6", "m22 7-8.5 8.5-5-5L2 17") }

    val Settings by lazy {
        icon(
            "M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 2.34 2.34 0 0 1-4.659 0 2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915",
            "M15 12a3 3 0 1 1-6 0 3 3 0 1 1 6 0z",
        )
    }

    val Target by lazy {
        icon(
            "M22 12a10 10 0 1 1-20 0 10 10 0 1 1 20 0z",
            "M18 12a6 6 0 1 1-12 0 6 6 0 1 1 12 0z",
            "M14 12a2 2 0 1 1-4 0 2 2 0 1 1 4 0z",
        )
    }

    val Ellipsis by lazy {
        icon(
            "M13 12a1 1 0 1 1-2 0 1 1 0 1 1 2 0z",
            "M20 12a1 1 0 1 1-2 0 1 1 0 1 1 2 0z",
            "M6 12a1 1 0 1 1-2 0 1 1 0 1 1 2 0z",
        )
    }

    val ChevronDown by lazy { icon("m6 9 6 6 6-6") }

    val Trash by lazy {
        icon(
            "M10 11v6",
            "M14 11v6",
            "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6",
            "M3 6h18",
            "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2",
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
