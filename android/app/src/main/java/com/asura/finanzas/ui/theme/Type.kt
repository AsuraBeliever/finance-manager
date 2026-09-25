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
import androidx.compose.ui.unit.em
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

/**
 * Tailwind's `tabular-nums`, which the web puts on every figure it prints.
 *
 * Sora's and Hanken's tabular digits are a good deal wider than their
 * proportional ones — the net-worth hero came out 50 px short of the
 * browser's without this — and the whole point of them is that a column of
 * amounts lines up. Only digits change, so it is a no-op on prose.
 */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

/**
 * Tailwind's `tracking-tight`, which several display headings carry.
 *
 * In `em` on purpose: the browser's is relative too, so the same constant
 * stays right wherever the size is overridden.
 *
 * Not the literal −0.025: Android snaps letter spacing to a whole pixel, and
 * at the page title's 30.4 sp the browser's −2.28 px per gap snapped to −3,
 * laying the title out ~5 px tighter than flexbox measures it. On a header
 * that sits within a couple of pixels of wrapping — Movimientos, at this
 * width — that is the difference between one line and two. This is the value
 * that lands on the browser's width once the snap has happened.
 */
val TrackingTight = (-0.0219).em

/** `tracking-wide`, on the small uppercase captions. */
val TrackingWide = 0.025.em

/** The `.eyebrow` rule's own tracking — far wider than `tracking-widest`. */
val TrackingEyebrow = 0.18.em

// Every one of these is a class the web actually writes, so a heading here is
// the same face, size and weight as the same heading in the browser:
//
//   displayLarge     PageHeader h2 and modal titles — `font-display font-medium`
//   headlineMedium   money heroes and gauges       — `font-display font-semibold`
//   titleLarge       widget headings               — `font-display text-lg font-medium`
//   titleMedium      section h3                    — plain `font-medium`, sans
val BrokeTypography = Typography(
    // Every number here is `getComputedStyle` on the browser at phone width,
    // not the nearest round figure: Tailwind ships a line-height with each
    // `text-*` step and a few headings add `tracking-tight`, and Compose
    // carries neither for free. `.copy(fontSize = …)` does NOT rescale the
    // line height, so a role used at another size has to restate it.
    displayLarge = TextStyle(
        fontFamily = Sora,
        fontWeight = FontWeight.Medium,
        // `text-[1.9rem] leading-none tracking-tight` — the page title.
        fontSize = 30.4.sp,
        lineHeight = 30.4.sp,
        letterSpacing = TrackingTight,
    ),
    headlineMedium = TextStyle(
        fontFamily = Sora,
        fontWeight = FontWeight.SemiBold,
        // `text-2xl` — the money figure on a wallet card.
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Sora,
        fontWeight = FontWeight.Medium,
        // `text-lg tracking-tight` — modal titles and widget headings.
        fontSize = 18.sp,
        lineHeight = 28.sp,
        letterSpacing = TrackingTight,
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
        // `text-base`: 1.5, so 24 — not the 23 that was here.
        fontSize = 16.sp,
        lineHeight = 24.sp,
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
        lineHeight = 20.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = HankenGrotesk,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    // The three below are easy to forget because nothing in this file reads
    // them — but `FieldLabel` (labelMedium) and `FieldHint` (bodySmall) do, on
    // every form in the app. Left unset they keep Material's defaults, which
    // means Roboto *and* its 0.4–0.5 sp tracking: side by side with the
    // browser the labels were a different face and ran wider.
    labelMedium = TextStyle(
        fontFamily = HankenGrotesk,
        fontWeight = FontWeight.Medium,
        // `text-[0.8rem]`, whose line box is 1.5 of it.
        fontSize = 12.8.sp,
        lineHeight = 19.2.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = HankenGrotesk,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = Sora,
        fontWeight = FontWeight.Medium,
        fontSize = 40.sp,
        lineHeight = 46.sp,
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
