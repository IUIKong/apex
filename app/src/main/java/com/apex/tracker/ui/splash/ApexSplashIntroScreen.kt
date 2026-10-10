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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.apex.tracker.ui.components.ApexLogoMark
import com.apex.tracker.ui.sound.ApexAudioFeedback
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Editorial Athletic Opening Screen:
 * 1. Deep Black dot drops in from the top and BOUNCES naturally into center screen with bouncy physics.
 * 2. Dot expands smoothly and slowly like a cinematic iris, immersing the screen into obsidian black.
 * 3. Text displays strictly "R U N" in large, bold, razor-sharp athletic typography.
 * 4. Behind "R U N", a vibrant pixelated digital gradient grid moves continuously in a fluid athletic spectrum.
 * 5. Tap anywhere to skip instantly.
 */
@Composable
fun ApexSplashIntroScreen(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Animation Controllers
    val orbOffsetY = remember { Animatable(-1200f) }
    val orbScale = remember { Animatable(1f) }
    val orbAlpha = remember { Animatable(1f) }
    val borderAlpha = remember { Animatable(1f) }
    val bgTransition = remember { Animatable(0f) } // 0f = warm parchment (#FBFBF9), 1f = deep black (#080B10)
    val contentAlpha = remember { Animatable(0f) }
    val contentScale = remember { Animatable(0.92f) }

    // Infinite gradient shift for the pixelated gradient background
    val infiniteTransition = rememberInfiniteTransition(label = "pixel_gradient_shift")
    val gradientPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase_loop"
    )

    val currentBg = androidx.compose.ui.graphics.lerp(
        Color(0xFFFBFBF9),
        Color(0xFF080B10),
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
            // Orb starts off-screen at the top
            orbOffsetY.snapTo(-screenHeightPx * 0.75f)

            // Phase 1: Orb drops and BOUNCES into the center
            orbOffsetY.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.54f, // High bouncy elasticity for genuine bounce-in feel
                    stiffness = 140f
                )
            )

            // Phase 2: Natural settling pause at center (200ms)
            delay(200)

            // Phase 3: Orb expands slower and buttery smooth into deep obsidian black
            launch {
                borderAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(350, easing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f))
                )
            }
            launch {
                bgTransition.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(1200, easing = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f))
                )
            }
            orbScale.animateTo(
                targetValue = 65f, // Expands slower to fill entire screen
                animationSpec = tween(
                    durationMillis = 1350,
                    easing = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f)
                )
            )

            // Phase 4: Fade and spring in the prominent "R U N" lockup with pixelated colors moving behind
            ApexAudioFeedback.playClick(context)
            launch {
                contentScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.75f, stiffness = 220f)
                )
            }
            contentAlpha.animateTo(1f, tween(300))

            // Hold on the vibrant "R U N" display (1100ms)
            delay(1100)

            // Phase 5: Fade out smoothly into main app
            contentAlpha.animateTo(0f, tween(260))

            onFinish()
        }

        // =========================================================================
        // BLACK ORB: Bounces in from top, then expands slower into solid black
        // =========================================================================
        if (bgTransition.value < 0.999f || orbScale.value < 60f) {
            val orbSizeDp = 72.dp
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset(0, orbOffsetY.value.roundToInt()) }
                    .scale(orbScale.value)
                    .size(orbSizeDp)
                    .graphicsLayer { alpha = orbAlpha.value }
                    .clip(CircleShape)
                    .background(Color(0xFF080B10)) // Pure Deep Obsidian Black orb
                    .then(
                        if (borderAlpha.value > 0.01f) {
                            Modifier.border(
                                1.5.dp,
                                Color(0xFF00E5FF).copy(alpha = borderAlpha.value * 0.8f),
                                CircleShape
                            )
                        } else Modifier
                    )
            )
        }

        // =========================================================================
        // "R U N" DISPLAY: Big athletic typography with pixelated colors moving behind
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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    // Apex Logo Emblem
                    ApexLogoMark(
                        size = 44.dp,
                        accentColor = Color(0xFF00E5FF)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Container for "R U N" with pixelated moving gradient backdrop
                    Box(
                        modifier = Modifier
                            .width(320.dp)
                            .height(110.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // 1. Pixelated moving gradient background behind "R U N"
                        PixelatedMovingGradientBackdrop(
                            phase = gradientPhase,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(14.dp))
                        )

                        // 2. Translucent dark filter over pixels for maximum typographic contrast
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0x66080B10))
                                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp))
                        )

                        // 3. Bold, Large Athletic "R U N" Typography
                        Text(
                            text = "R U N",
                            color = Color(0xFFFFFFFF),
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Black,
                            fontStyle = FontStyle.Italic,
                            fontSize = 62.sp,
                            letterSpacing = 16.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(start = 16.dp) // Optical center offset for tracked letters
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Minimal athletic sub-label pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF141922))
                            .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "APEX ATHLETIC TRACKER",
                            color = Color(0xFF00E5FF),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.8.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/**
 * Pixelated Digital Gradient Backdrop:
 * Renders a matrix of chunky discrete pixel blocks whose colors are sampled from
 * a fluid, moving multi-color athletic gradient across time.
 */
@Composable
private fun PixelatedMovingGradientBackdrop(
    phase: Float,
    modifier: Modifier = Modifier
) {
    // Vibrant athletic color palette
    val palette = remember {
        listOf(
            Color(0xFF00E5FF), // Electric Cyan
            Color(0xFF76FF03), // Electric Lime
            Color(0xFFFFB300), // Laser Amber
            Color(0xFFFF1744), // Punchy Crimson
            Color(0xFF7C4DFF), // Vivid Violet
            Color(0xFF00E5FF)  // Wrap back to Cyan
        )
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val pixelSizePx = 11.dp.toPx()
        val gapPx = 2.dp.toPx()
        val step = pixelSizePx + gapPx

        val cols = (w / step).toInt() + 1
        val rows = (h / step).toInt() + 1

        val paletteSize = palette.size

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val x = c * step
                val y = r * step

                // Spatial coordinate + animated phase wave
                val wave = ((c.toFloat() / cols) * 0.7f + (r.toFloat() / rows) * 0.3f + phase) % 1.0f

                // Interpolate through athletic palette
                val paletteIndexF = (wave * (paletteSize - 1))
                val idx1 = paletteIndexF.toInt().coerceIn(0, paletteSize - 2)
                val idx2 = idx1 + 1
                val frac = paletteIndexF - idx1

                val baseColor = androidx.compose.ui.graphics.lerp(palette[idx1], palette[idx2], frac)

                // Subtle checkerboard brightness variation for authentic chunky digital pixel effect
                val checker = if ((c + r) % 2 == 0) 0.88f else 1.0f
                val pixelColor = baseColor.copy(alpha = 0.82f * checker)

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
