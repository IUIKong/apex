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
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apex.tracker.ui.sound.ApexAudioFeedback
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Editorial Athletic Opening Screen:
 * 1. Deep Obsidian ball drops from offscreen and physically BOUNCES into the frame
 *    (multi-phase drop, impact squash & rebound stretch physics).
 * 2. Ball settles at center, then expands SLOWLY and cinematically across 2600ms
 *    to blanket the entire screen in pure OLED obsidian.
 * 3. A full-screen smooth pixelated moving gradient pulses across the entire display.
 * 4. Strictly displays "R U N" in large, bold, athletic typography with optical tracking.
 * 5. Tap anywhere to skip instantly.
 */
@Composable
fun ApexSplashIntroScreen(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Physics Animation Controllers for Ball Bounce & Expansion
    val orbOffsetY = remember { Animatable(0f) }
    val orbScaleX = remember { Animatable(1f) }
    val orbScaleY = remember { Animatable(1f) }
    val orbOverallScale = remember { Animatable(1f) }
    val borderAlpha = remember { Animatable(1f) }
    val bgTransition = remember { Animatable(0f) } // 0f = subtle canvas, 1f = deep OLED #06080C
    val contentAlpha = remember { Animatable(0f) }
    val contentScale = remember { Animatable(0.92f) }

    // Infinite fluid phase for the full-screen pixelated gradient
    val infiniteTransition = rememberInfiniteTransition(label = "pixel_gradient_flow")
    val gradientPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase_continuous"
    )

    val currentBg = androidx.compose.ui.graphics.lerp(
        Color(0xFF0F141D),
        Color(0xFF06080C),
        bgTransition.value
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(currentBg)
            .pointerInput(Unit) {
                detectTapGestures {
                    onFinish() // Tap anywhere to skip instantly
                }
            }
    ) {
        val density = LocalDensity.current
        val screenHeightPx = with(density) { maxHeight.toPx() }

        LaunchedEffect(Unit) {
            // Ball starts completely off-screen at the top
            orbOffsetY.snapTo(-screenHeightPx * 1.15f)

            // =========================================================================
            // PHASE 1: GENUINE MULTI-BOUNCE PHYSICS (NOT A SLIDE)
            // =========================================================================

            // Drop 1: Accelerates downward from above screen with vertical stretch
            launch {
                orbScaleX.animateTo(0.84f, tween(360, easing = LinearEasing))
            }
            launch {
                orbScaleY.animateTo(1.26f, tween(360, easing = LinearEasing))
            }
            orbOffsetY.animateTo(
                targetValue = 0f,
                animationSpec = tween(360, easing = CubicBezierEasing(0.38f, 0.0f, 1.0f, 1.0f))
            )

            // Impact 1: Ground collision squash deformation
            ApexAudioFeedback.playClick(context)
            launch {
                orbScaleX.animateTo(1.45f, tween(85, easing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)))
            }
            launch {
                orbScaleY.animateTo(0.62f, tween(85, easing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)))
            }
            delay(85)

            // Rebound 1: Springs upward to ~28% screen height with elastic elongation
            launch {
                orbScaleX.animateTo(0.88f, tween(260, easing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)))
            }
            launch {
                orbScaleY.animateTo(1.16f, tween(260, easing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)))
            }
            orbOffsetY.animateTo(
                targetValue = -screenHeightPx * 0.27f,
                animationSpec = tween(260, easing = CubicBezierEasing(0.0f, 0.0f, 0.25f, 1.0f))
            )

            // Drop 2: Falls back down to center
            launch {
                orbScaleX.animateTo(0.93f, tween(220, easing = LinearEasing))
            }
            launch {
                orbScaleY.animateTo(1.09f, tween(220, easing = LinearEasing))
            }
            orbOffsetY.animateTo(
                targetValue = 0f,
                animationSpec = tween(220, easing = CubicBezierEasing(0.38f, 0.0f, 1.0f, 1.0f))
            )

            // Impact 2: Moderate ground squash
            launch {
                orbScaleX.animateTo(1.24f, tween(65, easing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)))
            }
            launch {
                orbScaleY.animateTo(0.80f, tween(65, easing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)))
            }
            delay(65)

            // Rebound 2: Small secondary bounce upward
            launch {
                orbScaleX.animateTo(0.97f, tween(150, easing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)))
            }
            launch {
                orbScaleY.animateTo(1.04f, tween(150, easing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)))
            }
            orbOffsetY.animateTo(
                targetValue = -screenHeightPx * 0.08f,
                animationSpec = tween(150, easing = CubicBezierEasing(0.0f, 0.0f, 0.25f, 1.0f))
            )

            // Drop 3: Final descent to rest at 0
            orbOffsetY.animateTo(
                targetValue = 0f,
                animationSpec = tween(120, easing = CubicBezierEasing(0.38f, 0.0f, 1.0f, 1.0f))
            )

            // Impact 3 & Settle: Gentle squash and restitution to resting sphere
            launch {
                orbScaleX.animateTo(1.06f, tween(50))
                orbScaleX.animateTo(1.0f, tween(80))
            }
            launch {
                orbScaleY.animateTo(0.94f, tween(50))
                orbScaleY.animateTo(1.0f, tween(80))
            }
            delay(130)

            // Natural settling pause at center
            delay(200)

            // =========================================================================
            // PHASE 2: SLOW CINEMATIC BALL EXPANSION (2600ms)
            // =========================================================================
            launch {
                borderAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(500, easing = CubicBezierEasing(0.2f, 1.0f, 0.3f, 1.0f))
                )
            }
            launch {
                bgTransition.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(2400, easing = CubicBezierEasing(0.20f, 0.0f, 0.20f, 1.0f))
                )
            }
            // Expands much slower and smoother across 2600ms to blanket the screen
            orbOverallScale.animateTo(
                targetValue = 75f,
                animationSpec = tween(
                    durationMillis = 2600,
                    easing = CubicBezierEasing(0.20f, 0.0f, 0.20f, 1.0f)
                )
            )

            // =========================================================================
            // PHASE 3: FULL SCREEN PIXELATED GRADIENT & BOLD "R U N" TYPOGRAPHY
            // =========================================================================
            launch {
                contentScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 180f)
                )
            }
            contentAlpha.animateTo(1f, tween(420))

            // Hold on the prominent "R U N" display with pixelated background moving
            delay(1350)

            // Fade out smoothly into the live cockpit
            contentAlpha.animateTo(0f, tween(280))

            onFinish()
        }

        // =========================================================================
        // FULL SCREEN PIXELATED MOVING GRADIENT BACKDROP
        // Covers the ENTIRE SCREEN smoothly behind the typography
        // =========================================================================
        if (contentAlpha.value > 0.005f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = contentAlpha.value }
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
        }

        // =========================================================================
        // BOUNCING & EXPANDING ORB
        // Bounces in from top with squash/stretch, then smoothly scales to 75x
        // =========================================================================
        if (bgTransition.value < 0.999f || orbOverallScale.value < 70f) {
            val orbBaseSize = 72.dp
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset(0, orbOffsetY.value.roundToInt()) }
                    .scale(
                        scaleX = orbScaleX.value * orbOverallScale.value,
                        scaleY = orbScaleY.value * orbOverallScale.value
                    )
                    .size(orbBaseSize)
                    .clip(CircleShape)
                    .background(Color(0xFF06080C)) // Pure OLED Obsidian
                    .then(
                        if (borderAlpha.value > 0.01f) {
                            Modifier.border(
                                2.dp,
                                Color(0xFF00F5D4).copy(alpha = borderAlpha.value * 0.85f),
                                CircleShape
                            )
                        } else Modifier
                    )
            )
        }

        // =========================================================================
        // STRICT "R U N" TYPOGRAPHY (ONLY R U N)
        // Big, bold, italic athletic typography centered over the full-screen canvas
        // =========================================================================
        if (contentAlpha.value > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = contentAlpha.value
                        scaleX = contentScale.value
                        scaleY = contentScale.value
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "R U N",
                    color = Color(0xFFFFFFFF),
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                    fontSize = 76.sp,
                    letterSpacing = 22.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(start = 22.dp) // Optical center balance for tracked italic letters
                )
            }
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
