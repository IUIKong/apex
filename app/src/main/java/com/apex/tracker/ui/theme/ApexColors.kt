package com.apex.tracker.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class ApexColorScheme(
    val canvasBackground: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceHigh: Color,
    val glassCard: Color,

    val borderSubtle: Color,
    val borderActive: Color,
    val borderGlowCyan: Color,
    val borderGlowLime: Color,
    val borderGlowAmber: Color,

    val electricCyan: Color,
    val electricCyanGlow: Color,
    val electricCyanDim: Color,

    val electricLime: Color,
    val electricLimeBright: Color,
    val electricLimeGlow: Color,
    val electricLimeDim: Color,

    val laserAmber: Color,
    val laserAmberGlow: Color,
    val laserAmberDim: Color,

    val punchyCrimson: Color,
    val punchyCrimsonGlow: Color,
    val punchyCrimsonDim: Color,

    val textPrimary: Color,
    val textPureWhite: Color,
    val slateSubtle: Color,
    val slateMuted: Color,

    val mapGridLine: Color,
    val mapRouteTrace: Color
) {
    val obsidian: Color get() = canvasBackground
}

val DarkApexColorScheme = ApexColorScheme(
    canvasBackground = Color(0xFF080B10), // True Black OLED canvas
    surface = Color(0xFF0F141C),          // Main card & cockpit surface
    surfaceElevated = Color(0xFF161D28),  // Action buttons & elevated cards
    surfaceHigh = Color(0xFF1E2736),      // Active states & pill containers
    glassCard = Color(0xE00F141C),        // Glassmorphic header (88% opacity)

    borderSubtle = Color(0xFF1E2736),
    borderActive = Color(0xFF2D3A4F),
    borderGlowCyan = Color(0x4D00F5D4),
    borderGlowLime = Color(0x4D00FF87),
    borderGlowAmber = Color(0x4DF59E0B),

    electricCyan = Color(0xFF00F5D4),     // Primary metric, active trace
    electricCyanGlow = Color(0x7300F5D4),
    electricCyanDim = Color(0x1F00F5D4),

    electricLime = Color(0xFF00FF87),     // Moving status, pace positive
    electricLimeBright = Color(0xFF70FF00),
    electricLimeGlow = Color(0x6600FF87),
    electricLimeDim = Color(0x1F00FF87),

    laserAmber = Color(0xFFF59E0B),       // Warning, pause
    laserAmberGlow = Color(0x66F59E0B),
    laserAmberDim = Color(0x26F59E0B),

    punchyCrimson = Color(0xFFEF4444),    // Stop, finish pin
    punchyCrimsonGlow = Color(0x66EF4444),
    punchyCrimsonDim = Color(0x26EF4444),

    textPrimary = Color(0xFFF8FAFC),
    textPureWhite = Color(0xFFFFFFFF),
    slateSubtle = Color(0xFF94A3B8),
    slateMuted = Color(0xFF64748B),

    mapGridLine = Color(0x0AFFFFFF),
    mapRouteTrace = Color(0xFF00F5D4)
)

val LightApexColorScheme = ApexColorScheme(
    canvasBackground = Color(0xFFFBFBF9), // Warm Alabaster / Milk base canvas
    surface = Color(0xFFF5F4F0),          // Ivory sheet card surface
    surfaceElevated = Color(0xFFFFFFFF),  // Pure Crisp White container
    surfaceHigh = Color(0xFFEDEAE3),      // Warm Selection / Active pill
    glassCard = Color(0xF2F5F4F0),        // Ivory card container

    borderSubtle = Color(0xFFE7E4DF),     // Parchment Rule 1px hairline
    borderActive = Color(0xFF78716C),     // Muted Stone tactile border
    borderGlowCyan = Color(0x33D9531E),
    borderGlowLime = Color(0x332D6A4F),
    borderGlowAmber = Color(0x33D97706),

    electricCyan = Color(0xFFD9531E),     // Runner Terracotta primary accent
    electricCyanGlow = Color(0x33D9531E),
    electricCyanDim = Color(0x1AD9531E),

    electricLime = Color(0xFF2D6A4F),     // Forest Sage Green (active moving)
    electricLimeBright = Color(0xFF2D6A4F),
    electricLimeGlow = Color(0x332D6A4F),
    electricLimeDim = Color(0x1A2D6A4F),

    laserAmber = Color(0xFFD97706),       // Warm Amber (warning / pause)
    laserAmberGlow = Color(0x33D97706),
    laserAmberDim = Color(0x1AD97706),

    punchyCrimson = Color(0xFFC2410C),    // Brick Rust (stop / finish pin)
    punchyCrimsonGlow = Color(0x33C2410C),
    punchyCrimsonDim = Color(0x1AC2410C),

    textPrimary = Color(0xFF1C1917),      // Deep Warm Espresso Ink
    textPureWhite = Color(0xFF1C1917),    // High-contrast numeral ink
    slateSubtle = Color(0xFF57534E),      // Warm Slate metadata
    slateMuted = Color(0xFF78716C),       // Muted Stone micro-labels

    mapGridLine = Color(0xFFEBE8E2),      // Parchment map contour/grid
    mapRouteTrace = Color(0xFFD9531E)     // Terracotta runner trail trace
)

@Immutable
object ApexColors {
    // Core OLED Backgrounds
    val Obsidian = Color(0xFF080B10)           // True Black OLED canvas
    val Surface = Color(0xFF0F141C)            // Main card & cockpit surface
    val SurfaceElevated = Color(0xFF161D28)    // Action buttons & elevated cards
    val SurfaceHigh = Color(0xFF1E2736)        // Active states & pill containers
    val GlassCard = Color(0xE00F141C)          // Glassmorphic header (88% opacity)

    // Borders
    val BorderSubtle = Color(0xFF1E2736)
    val BorderActive = Color(0xFF2D3A4F)
    val BorderGlowCyan = Color(0x4D00F5D4)
    val BorderGlowLime = Color(0x4D00FF87)
    val BorderGlowAmber = Color(0x4DF59E0B)

    // Precision Neon Accents
    val ElectricCyan = Color(0xFF00F5D4)       // Primary metric, active trace
    val ElectricCyanGlow = Color(0x7300F5D4)   // Glow drop-shadow (45%)
    val ElectricCyanDim = Color(0x1F00F5D4)    // Surface tint (12%)

    val ElectricLime = Color(0xFF00FF87)       // Moving status, pace positive, start pin
    val ElectricLimeBright = Color(0xFF70FF00) // High-contrast neon lime
    val ElectricLimeGlow = Color(0x6600FF87)   // 40% glow
    val ElectricLimeDim = Color(0x1F00FF87)    // 12% tint

    val LaserAmber = Color(0xFFF59E0B)         // Warning, pause, locked track
    val LaserAmberGlow = Color(0x66F59E0B)     // 40% glow
    val LaserAmberDim = Color(0x26F59E0B)      // 15% tint

    val PunchyCrimson = Color(0xFFEF4444)      // Stop, finish pin, outlier rejected
    val PunchyCrimsonGlow = Color(0x66EF4444)  // 40% glow
    val PunchyCrimsonDim = Color(0x26EF4444)   // 15% tint

    // High-Contrast Typography
    val TextPrimary = Color(0xFFF8FAFC)
    val TextPureWhite = Color(0xFFFFFFFF)
    val SlateSubtle = Color(0xFF94A3B8)
    val SlateMuted = Color(0xFF64748B)

    val MapGridLine = Color(0x0AFFFFFF)
    val MapRouteTrace = Color(0xFF00F5D4)
}

val LocalApexColors = staticCompositionLocalOf { LightApexColorScheme }
