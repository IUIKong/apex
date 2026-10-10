package com.apex.tracker.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import kotlin.math.roundToInt

/**
 * Individual typographic frame for the Marvel-style "R U N" font flipbook reel.
 * Every frame strictly displays "R U N" with fancy italic styling and zero layout shifts.
 */
data class MarvelFontFrame(
    val fontFamily: FontFamily,
    val fontWeight: FontWeight,
    val fontStyle: FontStyle = FontStyle.Italic,
    val textColor: Color = Color(0xFF1C1917),
    val accentColor: Color = Color(0xFFD9531E),
    val subLabel: String
)

private const val INTRO_DISPLAY_TEXT = "R U N"

private val MARVEL_FONT_REEL = listOf(
    // Frame 0: Luxury Editorial Serif Italic (Light) - Crisp Platinum White
    MarvelFontFrame(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Light,
        fontStyle = FontStyle.Italic,
        textColor = Color(0xFFF8FAFC),
        accentColor = Color(0xFF00F5D4),
        subLabel = "PRECISION KINEMATICS"
    ),
    // Frame 1: High-Velocity Aerodynamic Sans Italic (Black) - Electric Cyan
    MarvelFontFrame(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontStyle = FontStyle.Italic,
        textColor = Color(0xFF00F5D4),
        accentColor = Color(0xFF00F5D4),
        subLabel = "VELOCITY STRIDE // 120HZ"
    ),
    // Frame 2: Flowing Calligraphic Athletic Cursive Italic (Normal) - Electric Lime
    MarvelFontFrame(
        fontFamily = FontFamily.Cursive,
        fontWeight = FontWeight.Normal,
        fontStyle = FontStyle.Italic,
        textColor = Color(0xFF00FF87),
        accentColor = Color(0xFF00FF87),
        subLabel = "ORGANIC MOTION CADENCE"
    ),
    // Frame 3: Chrono Racing Telemetry Monospace Italic (Bold) - Laser Amber
    MarvelFontFrame(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontStyle = FontStyle.Italic,
        textColor = Color(0xFFF59E0B),
        accentColor = Color(0xFFF59E0B),
        subLabel = "GNSS SATELLITE LOCK"
    ),
    // Frame 4: Classic Marathon Trophy Serif Italic (Bold) - Pure White
    MarvelFontFrame(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontStyle = FontStyle.Italic,
        textColor = Color(0xFFFFFFFF),
        accentColor = Color(0xFFD9531E),
        subLabel = "ENDURANCE PROTOCOL"
    ),
    // Frame 5: Minimalist Aerofoil Sans Italic (Light) - Sky Blue
    MarvelFontFrame(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontStyle = FontStyle.Italic,
        textColor = Color(0xFF38BDF8),
        accentColor = Color(0xFF38BDF8),
        subLabel = "AERODYNAMIC PROFILE"
    ),
    // Frame 6: Bold Flowing Cursive Italic (Bold) - Coral Red
    MarvelFontFrame(
        fontFamily = FontFamily.Cursive,
        fontWeight = FontWeight.Bold,
        fontStyle = FontStyle.Italic,
        textColor = Color(0xFFFB7185),
        accentColor = Color(0xFFFB7185),
        subLabel = "BIOMETRIC PULSE SYNC"
    ),
    // Frame 7: Precision Split Monospace Italic (Medium) - Solar Gold
    MarvelFontFrame(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontStyle = FontStyle.Italic,
        textColor = Color(0xFFFBBF24),
        accentColor = Color(0xFFFBBF24),
        subLabel = "SUB-METER EKF FUSION"
    ),
    // Frame 8: Championship Heavy Serif Italic (ExtraBold) - Terracotta Blaze
    MarvelFontFrame(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.ExtraBold,
        fontStyle = FontStyle.Italic,
        textColor = Color(0xFFFF6B4A),
        accentColor = Color(0xFFFF6B4A),
        subLabel = "CHAMPIONSHIP CIRCUIT"
    ),
    // Frame 9: Final Hero Marvel Lockup - Pure Athletic Apex Italic
    MarvelFontFrame(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontStyle = FontStyle.Italic,
        textColor = Color(0xFFFFFFFF),
        accentColor = Color(0xFFD9531E),
        subLabel = "APEX ATHLETIC TRACKER"
    )
)

/**
 * Buttery smooth, warm beige Marvel-style opening animation:
 * 1. Warm beige background (#FBFBF9) matching the light color scheme of the app.
 * 2. Contrast black/espresso orb bounces gracefully from the left to center with silky spring physics.
 * 3. Orb expands smoothly across the screen like an iris transition, seamlessly dissolving into the beige canvas.
 * 4. Fixed-dimension container displays strictly "R U N", cycling through fancy italic fonts without any layout shifting.
 * 5. Final hero frame locks into deep espresso "R U N" with terracotta Apex emblem, accent underline, and subtle click tone.
 * 6. Tap anywhere to skip instantly into the app.
 */
@Composable
fun ApexSplashIntroScreen(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Animation Controllers
    val orbOffsetX = remember { Animatable(-1000f) }
    val orbScale = remember { Animatable(1f) }
    val orbAlpha = remember { Animatable(1f) }
    val borderAlpha = remember { Animatable(1f) }
    val bgTransition = remember { Animatable(0f) } // 0f = warm beige (#FBFBF9), 1f = cinematic black (#080B10)
    val reelAlpha = remember { Animatable(0f) }
    val reelScale = remember { Animatable(1f) }
    val heroAccentAlpha = remember { Animatable(0f) }

    var currentFrameIndex by remember { mutableIntStateOf(0) }

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
        val screenWidthPx = with(density) { maxWidth.toPx() }

        LaunchedEffect(Unit) {
            // Orb starts off-screen to the left
            orbOffsetX.snapTo(-screenWidthPx * 0.95f)

            // Phase 1: Orb bounces in smoothly from left to center (x = 0)
            orbOffsetX.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.78f, // Silky smooth deceleration with gentle natural settling
                    stiffness = 150f
                )
            )

            // Phase 2: Natural resting pause at center (160ms)
            delay(160)

            // Phase 3: Orb expands smoothly and buttery, turning the entire screen into deep obsidian black
            launch {
                borderAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = 220,
                        easing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)
                    )
                )
            }
            launch {
                bgTransition.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 800,
                        easing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)
                    )
                )
            }
            orbScale.animateTo(
                targetValue = 55f, // Fully covers entire screen into solid black
                animationSpec = tween(
                    durationMillis = 880,
                    easing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)
                )
            )

            // Phase 4: Fade in Marvel text reel smoothly on the dark canvas
            reelAlpha.animateTo(1f, tween(200))

            // Flip through fancy italic font frames (135ms per frame for cinematic Marvel tempo)
            for (i in 0 until MARVEL_FONT_REEL.size - 1) {
                currentFrameIndex = i
                delay(135)
            }

            // Phase 5: Lock onto the final Marvel Hero frame
            currentFrameIndex = MARVEL_FONT_REEL.size - 1
            ApexAudioFeedback.playClick(context)

            launch {
                heroAccentAlpha.animateTo(1f, tween(260))
            }
            reelScale.snapTo(1.05f)
            reelScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = 280f)
            )

            // Hold on the final hero lockup (700ms)
            delay(700)

            // Phase 6: Smooth fade transition into main app
            reelAlpha.animateTo(0f, tween(240))

            onFinish()
        }

        // =========================================================================
        // BLACK ORB: Bounces to center from the left, then buttery iris expansion
        // =========================================================================
        if (bgTransition.value < 0.999f || orbScale.value < 50f) {
            val orbSizeDp = 76.dp
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset(orbOffsetX.value.roundToInt(), 0) }
                    .scale(orbScale.value)
                    .size(orbSizeDp)
                    .graphicsLayer { alpha = orbAlpha.value }
                    .clip(CircleShape)
                    .background(Color(0xFF080B10)) // Pure Deep Obsidian Black orb
                    .then(
                        if (borderAlpha.value > 0.01f) {
                            Modifier.border(
                                1.5.dp,
                                Color(0xFFD9531E).copy(alpha = borderAlpha.value),
                                CircleShape
                            )
                        } else Modifier
                    )
            )
        }

        // =========================================================================
        // MARVEL INTRO REEL: Fixed-dimension container for zero layout shifting
        // =========================================================================
        if (reelAlpha.value > 0.01f) {
            val activeFrame = MARVEL_FONT_REEL[currentFrameIndex]
            val isFinalHeroFrame = currentFrameIndex == MARVEL_FONT_REEL.size - 1

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = reelAlpha.value
                        scaleX = reelScale.value
                        scaleY = reelScale.value
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    // Fixed-size header slot for Apex emblem (guarantees zero vertical shift)
                    Box(
                        modifier = Modifier.size(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ApexLogoMark(
                            size = 42.dp,
                            accentColor = Color(0xFFD9531E),
                            modifier = Modifier.graphicsLayer {
                                alpha = if (isFinalHeroFrame) heroAccentAlpha.value else 0f
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Main "R U N" animated typographic box with strictly fixed dimensions
                    Box(
                        modifier = Modifier
                            .width(320.dp)
                            .height(72.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = INTRO_DISPLAY_TEXT,
                            color = activeFrame.textColor,
                            fontFamily = activeFrame.fontFamily,
                            fontWeight = activeFrame.fontWeight,
                            fontStyle = activeFrame.fontStyle,
                            letterSpacing = 14.sp,
                            fontSize = 52.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Fixed-size sub-label badge container (zero horizontal/vertical shift)
                    Box(
                        modifier = Modifier
                            .width(260.dp)
                            .height(30.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF161D28))
                            .border(1.dp, activeFrame.accentColor.copy(alpha = 0.6f), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = activeFrame.subLabel,
                            color = activeFrame.accentColor,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.6.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Fixed-size bottom accent slot for clean hero underline
                    Box(
                        modifier = Modifier
                            .width(88.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(
                                Color(0xFFD9531E).copy(alpha = if (isFinalHeroFrame) heroAccentAlpha.value else 0f)
                            )
                    )
                }
            }
        }
    }
}
