package com.apex.tracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

private fun createDarkMaterialTheme(colors: ApexColorScheme) = darkColorScheme(
    primary = colors.electricCyan,
    onPrimary = colors.canvasBackground,
    primaryContainer = colors.surfaceHigh,
    onPrimaryContainer = colors.electricCyan,
    secondary = colors.electricLime,
    onSecondary = colors.canvasBackground,
    tertiary = colors.laserAmber,
    error = colors.punchyCrimson,
    onError = colors.textPureWhite,
    background = colors.canvasBackground,
    onBackground = colors.textPrimary,
    surface = colors.surface,
    onSurface = colors.textPrimary,
    surfaceVariant = colors.surfaceElevated,
    onSurfaceVariant = colors.slateSubtle,
    outline = colors.borderSubtle,
    outlineVariant = colors.borderActive
)

private fun createLightMaterialTheme(colors: ApexColorScheme) = lightColorScheme(
    primary = colors.electricCyan,
    onPrimary = Color.White,
    primaryContainer = colors.surfaceHigh,
    onPrimaryContainer = colors.electricCyan,
    secondary = colors.electricLime,
    onSecondary = Color.White,
    tertiary = colors.laserAmber,
    error = colors.punchyCrimson,
    onError = Color.White,
    background = colors.canvasBackground,
    onBackground = colors.textPrimary,
    surface = colors.surface,
    onSurface = colors.textPrimary,
    surfaceVariant = colors.surfaceElevated,
    onSurfaceVariant = colors.slateSubtle,
    outline = colors.borderSubtle,
    outlineVariant = colors.borderActive
)

@Composable
fun ApexTheme(
    isDark: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = if (isDark) DarkApexColorScheme else LightApexColorScheme
    val typography = remember(isDark, colors) { ApexTypography(colors) }
    val materialScheme = remember(isDark, colors) {
        if (isDark) createDarkMaterialTheme(colors) else createLightMaterialTheme(colors)
    }

    CompositionLocalProvider(
        LocalApexColors provides colors,
        LocalApexTypography provides typography,
        LocalApexDimens provides ApexDimens
    ) {
        MaterialTheme(
            colorScheme = materialScheme,
            content = content
        )
    }
}

object ApexTheme {
    val colors: ApexColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalApexColors.current

    val typography: ApexTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalApexTypography.current

    val dimens: ApexDimens
        @Composable
        @ReadOnlyComposable
        get() = LocalApexDimens.current
}
