package com.apex.tracker.ui

import androidx.compose.ui.graphics.Color
import com.apex.tracker.ui.state.LiveHudUiState
import com.apex.tracker.ui.theme.ApexTypography
import com.apex.tracker.ui.theme.DarkApexColorScheme
import com.apex.tracker.ui.theme.LightApexColorScheme
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.pow

class ThemeToggleTest {

    @Test
    fun testDarkApexColorSchemeTokens() {
        val dark = DarkApexColorScheme

        assertThat(dark.canvasBackground).isEqualTo(Color(0xFF080B10))
        assertThat(dark.surface).isEqualTo(Color(0xFF0F141C))
        assertThat(dark.surfaceElevated).isEqualTo(Color(0xFF161D28))
        assertThat(dark.surfaceHigh).isEqualTo(Color(0xFF1E2736))
        assertThat(dark.borderSubtle).isEqualTo(Color(0xFF1E2736))
        assertThat(dark.borderActive).isEqualTo(Color(0xFF2D3A4F))
        assertThat(dark.electricCyan).isEqualTo(Color(0xFF00F5D4))
        assertThat(dark.electricLime).isEqualTo(Color(0xFF00FF87))
        assertThat(dark.laserAmber).isEqualTo(Color(0xFFF59E0B))
        assertThat(dark.punchyCrimson).isEqualTo(Color(0xFFEF4444))
        assertThat(dark.textPrimary).isEqualTo(Color(0xFFF8FAFC))
        assertThat(dark.textPureWhite).isEqualTo(Color(0xFFFFFFFF))
        assertThat(dark.slateSubtle).isEqualTo(Color(0xFF94A3B8))
        assertThat(dark.slateMuted).isEqualTo(Color(0xFF64748B))
        assertThat(dark.mapRouteTrace).isEqualTo(Color(0xFF00F5D4))
    }

    @Test
    fun testLightApexColorSchemeTokens() {
        val light = LightApexColorScheme

        // Atelier Athletic / Minimalist White-Beige Tokens
        assertThat(light.canvasBackground).isEqualTo(Color(0xFFFBFBF9)) // Warm Alabaster Canvas
        assertThat(light.surface).isEqualTo(Color(0xFFF5F4F0))          // Ivory Sheet card
        assertThat(light.surfaceElevated).isEqualTo(Color(0xFFFFFFFF))  // Pure Crisp White container
        assertThat(light.surfaceHigh).isEqualTo(Color(0xFFEDEAE3))      // Warm Selection pill
        assertThat(light.borderSubtle).isEqualTo(Color(0xFFE7E4DF))     // Parchment Rule 1px hairline
        assertThat(light.borderActive).isEqualTo(Color(0xFF78716C))     // Muted Stone tactile border
        assertThat(light.electricCyan).isEqualTo(Color(0xFFD9531E))     // Runner Terracotta primary accent
        assertThat(light.electricLime).isEqualTo(Color(0xFF2D6A4F))     // Forest Sage Green (active moving)
        assertThat(light.laserAmber).isEqualTo(Color(0xFFD97706))       // Warm Amber (warning / pause)
        assertThat(light.punchyCrimson).isEqualTo(Color(0xFFC2410C))    // Brick Rust (stop / finish pin)
        assertThat(light.textPrimary).isEqualTo(Color(0xFF1C1917))      // Deep Warm Espresso Ink
        assertThat(light.textPureWhite).isEqualTo(Color(0xFF1C1917))    // High-contrast numeral ink
        assertThat(light.slateSubtle).isEqualTo(Color(0xFF57534E))      // Warm Slate metadata
        assertThat(light.slateMuted).isEqualTo(Color(0xFF78716C))       // Muted Stone micro-labels
        assertThat(light.mapGridLine).isEqualTo(Color(0xFFEBE8E2))      // Parchment map contour/grid
        assertThat(light.mapRouteTrace).isEqualTo(Color(0xFFD9531E))     // Terracotta runner trail trace
    }

    @Test
    fun testApexTypographyColorBinding() {
        val darkTypo = ApexTypography(DarkApexColorScheme)
        assertThat(darkTypo.MetricGiant.color).isEqualTo(Color(0xFF00F5D4))
        assertThat(darkTypo.MetricLarge.color).isEqualTo(Color(0xFFFFFFFF))
        assertThat(darkTypo.BodyText.color).isEqualTo(Color(0xFFF8FAFC))

        val lightTypo = ApexTypography(LightApexColorScheme)
        assertThat(lightTypo.MetricGiant.color).isEqualTo(Color(0xFFD9531E))
        assertThat(lightTypo.MetricLarge.color).isEqualTo(Color(0xFF1C1917))
        assertThat(lightTypo.BodyText.color).isEqualTo(Color(0xFF1C1917))

        // Backward-compatible companion object default
        assertThat(ApexTypography.MetricGiant.color).isEqualTo(DarkApexColorScheme.electricCyan)
    }

    @Test
    fun testLiveHudUiStateThemeTransitions() {
        val defaultState = LiveHudUiState()
        assertThat(defaultState.isDarkTheme).isFalse()

        val darkState = defaultState.copy(isDarkTheme = true)
        assertThat(darkState.isDarkTheme).isTrue()

        val toggledBackState = darkState.copy(isDarkTheme = false)
        assertThat(toggledBackState.isDarkTheme).isFalse()
    }

    @Test
    fun testWcagContrastAdherence() {
        // WCAG AAA requires >= 7.0:1 contrast for normal text, and >= 4.5:1 for large text
        val darkTextContrast = contrastRatio(DarkApexColorScheme.textPrimary, DarkApexColorScheme.canvasBackground)
        assertThat(darkTextContrast).isAtLeast(7.0)

        // Deep Warm Espresso on Warm Alabaster White-Beige Canvas: exceeds 13:1
        val lightTextContrast = contrastRatio(LightApexColorScheme.textPrimary, LightApexColorScheme.canvasBackground)
        assertThat(lightTextContrast).isAtLeast(7.0)

        // Terracotta Runner accent on Warm Alabaster Canvas (exceeds graphical element minimum 3.0:1)
        val terracottaContrast = contrastRatio(LightApexColorScheme.electricCyan, LightApexColorScheme.canvasBackground)
        assertThat(terracottaContrast).isAtLeast(3.0)
    }

    private fun luminance(color: Color): Double {
        fun channel(c: Float): Double {
            return if (c <= 0.03928f) {
                c / 12.92
            } else {
                ((c + 0.055) / 1.055).toDouble().pow(2.4)
            }
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    private fun contrastRatio(c1: Color, c2: Color): Double {
        val l1 = luminance(c1)
        val l2 = luminance(c2)
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }
}
