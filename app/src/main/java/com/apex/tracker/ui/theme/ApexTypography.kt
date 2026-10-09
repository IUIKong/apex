package com.apex.tracker.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
open class ApexTypography(colors: ApexColorScheme = DarkApexColorScheme) {
    // Tabular Monospace Numerical Readouts
    val MetricGiant: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Black,
        fontSize = 72.sp,
        letterSpacing = (-1.5).sp,
        fontFeatureSettings = "tnum",
        color = colors.electricCyan
    )

    val MetricLarge: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        letterSpacing = (-0.5).sp,
        fontFeatureSettings = "tnum",
        color = colors.textPureWhite
    )

    val MetricMedium: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        fontFeatureSettings = "tnum",
        color = colors.textPureWhite
    )

    val MetricSmall: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        fontFeatureSettings = "tnum",
        color = colors.textPureWhite
    )

    val TelemetryMicro: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        fontFeatureSettings = "tnum",
        color = colors.slateSubtle
    )

    // Uppercase Sans-Serif Labels
    val LabelUppercase: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 11.sp,
        letterSpacing = 1.2.sp,
        color = colors.slateMuted
    )

    val LabelMicro: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 9.sp,
        letterSpacing = 0.8.sp,
        color = colors.slateMuted
    )

    val Headline: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.8.sp,
        color = colors.textPureWhite
    )

    val BodyText: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = colors.textPrimary
    )

    companion object : ApexTypography(DarkApexColorScheme)
}

val LocalApexTypography = staticCompositionLocalOf { ApexTypography(DarkApexColorScheme) }
