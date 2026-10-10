package com.apex.tracker.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * Editorial Athletic Opening Screen:
 * 1. Pure Full-Screen Smooth Pixelated Moving Gradient flowing continuously across the display.
 * 2. Strictly displays "R U N" in large, bold, athletic typography with optical tracking.
 * 3. 4-second cinematic duration before transitioning smoothly into the main app.
 * 4. Tap anywhere to skip instantly.
 */
@Composable
fun ApexSplashIntroScreen(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Animation Controllers for 4-second cinematic intro
    val contentAlpha = remember { Animatable(0f) }
    val contentScale = remember { Animatable(0.94f) }

    // Infinite fluid phase for the full-screen pixelated gradient
    val infiniteTransition = rememberInfiniteTransition(label = "pixel_gradient_flow")
    val gradientPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase_continuous"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFFBFBF9))
            .pointerInput(Unit) {
                detectTapGestures {
                    onFinish() // Tap anywhere to skip instantly
                }
            }
    ) {
        LaunchedEffect(Unit) {
            // Phase 1: Smooth 400ms entrance fade-in and scale spring
            launch {
                contentScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 160f)
                )
            }
            contentAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(400, easing = CubicBezierEasing(0.2f, 0.0f, 0.2f, 1.0f))
            )

            // Phase 2: Hold on the glowing "R U N" full-screen moving gradient for 3200ms (Total ~4.0s)
            delay(3200)

            // Phase 3: Smooth 400ms fade-out transition into live cockpit
            contentAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(400, easing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f))
            )

            onFinish()
        }

        // =========================================================================
        // FULL-SCREEN SMOOTH PIXELATED MOVING GRADIENT BACKDROP
        // Covers the entire display behind "R U N"
        // =========================================================================
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = contentAlpha.value }
                .background(Color(0xFF06080C))
        ) {
            // 1. Organic, fluid, multi-octave digital pixel matrix across full screen
            PixelatedMovingGradientBackdrop(
                phase = gradientPhase,
                modifier = Modifier.fillMaxSize()
            )

            // 2. High-contrast translucent dark glass scrim to ensure razor-sharp typography
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x7506080C))
            )
        }

        // =========================================================================
        // STRICT "R U N" TYPOGRAPHY & ENERGETIC ORB
        // Geometrically and virtually centered vertically and horizontally across all aspect ratios
        // =========================================================================
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .graphicsLayer {
                    alpha = contentAlpha.value
                    scaleX = contentScale.value
                    scaleY = contentScale.value
                },
            contentAlignment = Alignment.Center
        ) {
            // Energetic pulsing & bouncing athletic orb glow geometrically centered behind typography
            val orbBounce = sin(gradientPhase * 6.28318f)
            val orbBounceY = sin(gradientPhase * 6.28318f * 2f)
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .graphicsLayer {
                        val s = 1f + 0.12f * orbBounce
                        scaleX = s
                        scaleY = s
                        translationY = (-6f) * orbBounceY
                    }
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.radialGradient(
                            colors = listOf(
                                Color(0x6600F5D4), // Soft Electric Cyan glow
                                Color(0x3300FF87), // Soft Electric Lime glow
                                Color(0x1838BDF8), // Sky Azure hint
                                Color(0x00000000)
                            )
                        ),
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
            )

            // Strictly centered "R U N" typography:
            // Compensate for 22sp trailing letter-spacing after 'N' (+11.sp converted to Dp) and capital font descent (-3.dp)
            val density = LocalDensity.current
            val textOffsetX = with(density) { 11.sp.toDp() }
            Text(
                text = "R U N",
                color = Color(0xFFFFFFFF),
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                fontSize = 76.sp,
                letterSpacing = 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.offset(x = textOffsetX, y = (-3).dp)
            )
        }
    }
}

/**
 * Full-Screen Pixelated Digital Gradient Backdrop:
 * Covers the entire viewport with discrete chunky pixel tiles whose colors are computed
 * using smooth continuous 2D multi-octave waves across an athletic color spectrum.
 */
@Composable
private fun PixelatedMovingGradientBackdrop(
    phase: Float,
    modifier: Modifier = Modifier
) {
    // Athletic Electric Palette: Cyan, Lime, Azure, Amber, Crimson, Violet
    val palette = remember {
        listOf(
            Color(0xFF00F5D4), // Electric Cyan
            Color(0xFF00FF87), // Electric Lime
            Color(0xFF38BDF8), // Sky Azure
            Color(0xFFF59E0B), // Laser Amber
            Color(0xFFFF3B56), // Electric Crimson
            Color(0xFF8B5CF6), // Neon Violet
            Color(0xFF00F5D4)  // Wrap back to Cyan
        )
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val pixelSizePx = 16.dp.toPx()
        val gapPx = 2.dp.toPx()
        val step = pixelSizePx + gapPx

        val cols = (w / step).toInt() + 1
        val rows = (h / step).toInt() + 1
        val paletteSize = palette.size

        for (r in 0 until rows) {
            val ny = r.toFloat() / rows
            for (c in 0 until cols) {
                val nx = c.toFloat() / cols
                val x = c * step
                val y = r * step

                // Smooth multi-octave organic 2D wave equations
                val wave1 = sin(nx * 4.2f + phase * 6.28318f)
                val wave2 = cos(ny * 3.6f - phase * 5.02654f)
                val wave3 = sin((nx + ny) * 3.1f + phase * 3.14159f)

                // Normalized wave in [0.0, 1.0)
                val wave = (0.5f + 0.28f * wave1 + 0.20f * wave2 + 0.14f * wave3)
                    .coerceIn(0.0f, 0.999f)

                // Sample continuous athletic gradient
                val paletteIndexF = wave * (paletteSize - 1)
                val idx1 = paletteIndexF.toInt().coerceIn(0, paletteSize - 2)
                val idx2 = idx1 + 1
                val frac = paletteIndexF - idx1

                val baseColor = androidx.compose.ui.graphics.lerp(palette[idx1], palette[idx2], frac)

                // Checkerboard modulation for chunky digital aesthetic
                val checker = if ((c + r) % 2 == 0) 0.86f else 1.0f
                val pixelColor = baseColor.copy(alpha = 0.88f * checker)

                drawRoundRect(
                    color = pixelColor,
                    topLeft = Offset(x, y),
                    size = Size(pixelSizePx, pixelSizePx),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
            }
        }
    }
}
