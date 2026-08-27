package com.asura.finanzas.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
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

val BrokeTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Sora,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 46.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Sora,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Sora,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 23.sp,
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
        fontWeight = FontWeight.SemiBold,
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
