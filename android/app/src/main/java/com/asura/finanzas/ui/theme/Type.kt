package com.asura.finanzas.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.asura.finanzas.R

// Both faces ship as single variable fonts, so every weight costs no extra
// bytes — one axis setting per weight instead of one file per weight.
@OptIn(ExperimentalTextApi::class)
private fun variable(resId: Int, weight: FontWeight) =
    Font(
        resId = resId,
        weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )

/** UI type — Hanken Grotesk, same as the web app. */
val HankenGrotesk = FontFamily(
    variable(R.font.hanken_grotesk_variable, FontWeight.Normal),
    variable(R.font.hanken_grotesk_variable, FontWeight.Medium),
    variable(R.font.hanken_grotesk_variable, FontWeight.SemiBold),
    variable(R.font.hanken_grotesk_variable, FontWeight.Bold),
)

/** Display type — Sora, for headings and the money hero figures. */
val Sora = FontFamily(
    variable(R.font.sora_variable, FontWeight.Normal),
    variable(R.font.sora_variable, FontWeight.Medium),
    variable(R.font.sora_variable, FontWeight.SemiBold),
    variable(R.font.sora_variable, FontWeight.Bold),
)

// Every one of these is a class the web actually writes, so a heading here is
// the same face, size and weight as the same heading in the browser:
//
//   displayLarge     PageHeader h2 and modal titles — `font-display font-medium`
//   headlineMedium   money heroes and gauges       — `font-display font-semibold`
//   titleLarge       widget headings               — `font-display text-lg font-medium`
//   titleMedium      section h3                    — plain `font-medium`, sans
val BrokeTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Sora,
        fontWeight = FontWeight.Medium,
        fontSize = 30.sp,
        lineHeight = 34.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Sora,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Sora,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = HankenGrotesk,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = HankenGrotesk,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = HankenGrotesk,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = HankenGrotesk,
        // `font-medium`, which is what the web's Button, segmented chips and
        // transaction amounts all use. SemiBold here ran every label wider than
        // the browser's, enough that a header stopped fitting on one line.
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = HankenGrotesk,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

// ---- user-selectable type pairings ----
//
// The web offers six pairings and loads them from Google Fonts. Android fetches
// the same families through the downloadable-fonts provider rather than
// bundling eight more that most users will never pick; while a family is being
// fetched Compose falls back to the bundled faces, so text never disappears.

private val googleFontsProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private fun googleFamily(name: String) = FontFamily(
    Font(GoogleFont(name), googleFontsProvider, FontWeight.Normal),
    Font(GoogleFont(name), googleFontsProvider, FontWeight.Medium),
    Font(GoogleFont(name), googleFontsProvider, FontWeight.SemiBold),
    Font(GoogleFont(name), googleFontsProvider, FontWeight.Bold),
)

/** Display + UI families for one pairing key, mirroring the web's `FONTS`. */
private fun familiesFor(key: String): Pair<FontFamily, FontFamily> = when (key) {
    "editorial" -> googleFamily("Fraunces") to googleFamily("Hanken Grotesk")
    "modern" -> googleFamily("Space Grotesk") to googleFamily("Inter")
    "classic" -> googleFamily("Playfair Display") to googleFamily("Lora")
    "rounded" -> googleFamily("Baloo 2") to googleFamily("Nunito")
    "system" -> FontFamily.SansSerif to FontFamily.SansSerif
    // "default" and anything unknown keep the bundled pairing.
    else -> Sora to HankenGrotesk
}

/**
 * The base typography restyled for the chosen pairing: display sizes keep the
 * display family, everything else takes the UI family. Sizes and weights are
 * untouched, so only the faces change.
 */
@Composable
fun rememberBrokeTypography(fontKey: String): Typography = remember(fontKey) {
    if (fontKey == "default") return@remember BrokeTypography
    val (display, sans) = familiesFor(fontKey)
    fun TextStyle.on(family: FontFamily) = copy(fontFamily = family)
    BrokeTypography.copy(
        displayLarge = BrokeTypography.displayLarge.on(display),
        displayMedium = BrokeTypography.displayMedium.on(display),
        headlineMedium = BrokeTypography.headlineMedium.on(display),
        titleLarge = BrokeTypography.titleLarge.on(display),
        // A section heading is set in the UI face on the web, not the display one.
        titleMedium = BrokeTypography.titleMedium.on(sans),
        bodyLarge = BrokeTypography.bodyLarge.on(sans),
        bodyMedium = BrokeTypography.bodyMedium.on(sans),
        bodySmall = BrokeTypography.bodySmall.on(sans),
        labelLarge = BrokeTypography.labelLarge.on(sans),
        labelMedium = BrokeTypography.labelMedium.on(sans),
        labelSmall = BrokeTypography.labelSmall.on(sans),
    )
}
