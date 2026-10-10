package com.apex.tracker.ui.summary

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apex.tracker.ui.components.ApexLogoMark
import com.apex.tracker.ui.components.MetricCard
import com.apex.tracker.ui.components.tactilePress
import com.apex.tracker.ui.state.UiFormatters
import com.apex.tracker.ui.state.WorkoutSummaryUiState
import com.apex.tracker.ui.theme.ApexDimens
import com.apex.tracker.ui.theme.ApexTheme
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * Editorial Post-Workout Summary Screen.
 *
 * Displays celebration animations, count-up telemetry, vector circuit route map,
 * and social share generation. Deletion of workouts is strictly confined to the Logbook.
 */
@Composable
fun ApexWorkoutSummaryScreen(
    summaryState: WorkoutSummaryUiState,
    onDoneClick: () -> Unit = {},
    onShareClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isSharing by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val colors = ApexTheme.colors
    val typography = ApexTheme.typography

    // Animated Count-up for stats on workout finish
    val countUpProgress = remember { Animatable(0f) }
    LaunchedEffect(summaryState.activityId) {
        countUpProgress.snapTo(0f)
        countUpProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 1100,
                easing = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f)
            )
        )
    }

    val progress = countUpProgress.value
    val animDistKmVal = (summaryState.totalDistanceMeters * progress) / 1000.0
    val animDistKmStr = String.format(Locale.US, "%.2f", animDistKmVal)

    val animMovingSecVal = (summaryState.movingTimeSeconds * progress).toLong()
    val animMovingTimeStr = UiFormatters.formatDuration(animMovingSecVal)
    val elapsedTimeStr = UiFormatters.formatDuration(summaryState.elapsedTimeSeconds)

    val avgPaceStr = UiFormatters.formatPace(summaryState.avgPaceSecPerKm)
    val animPaceStr = if (progress > 0.15f) avgPaceStr else "--:--"

    val fullAvgSpeedKmh = if (summaryState.movingTimeSeconds > 0) {
        (summaryState.totalDistanceMeters / summaryState.movingTimeSeconds) * 3.6
    } else 0.0
    val animSpeedKmh = fullAvgSpeedKmh * progress
    val animSpeedStr = String.format(Locale.US, "%.1f", animSpeedKmh)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvasBackground)
    ) {
        // Full-screen celebratory confetti & victory particle burst
        CelebratoryConfettiBurstCanvas(
            progress = progress,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(
                    horizontal = ApexDimens.PaddingScreenHorizontal,
                    vertical = ApexDimens.PaddingScreenVertical
                ),
            verticalArrangement = Arrangement.spacedBy(ApexDimens.SpacingCards)
        ) {
            // Celebratory Animated Header with Expanding Aura & Particle Bursts
            CelebratoryWorkoutHeader(
                title = summaryState.title,
                activityType = summaryState.activityType,
                progress = progress
            )

            // 1. 4-Grid Core Metrics Overview (Animated Count-Up)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ApexDimens.SpacingCards)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ApexDimens.SpacingCards)
                ) {
                    MetricCard(
                        label = "DISTANCE",
                        value = animDistKmStr,
                        unit = "km",
                        subtext = "Accepted trajectory",
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        label = "MOVING TIME",
                        value = animMovingTimeStr,
                        subtext = "Elapsed: $elapsedTimeStr",
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ApexDimens.SpacingCards)
                ) {
                    MetricCard(
                        label = "AVG PACE",
                        value = animPaceStr,
                        unit = "/km",
                        subtext = "Moving pace",
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        label = "AVG SPEED",
                        value = animSpeedStr,
                        unit = "km/h",
                        subtext = "GPS telemetry",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 2. Circuit Route Map Canvas
            CircuitRouteMapCanvas(
                trackPoints = summaryState.trackPoints
            )

            // 3. Kilometer Splits Breakdown Table
            SplitsTable(
                splits = summaryState.splits
            )

            // 4. Share Activity Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ApexDimens.HeightTacticalButton)
                    .clip(RoundedCornerShape(ApexDimens.RadiusButtonTactical))
                    .background(colors.surfaceElevated)
                    .border(1.5.dp, colors.electricCyan, RoundedCornerShape(ApexDimens.RadiusButtonTactical))
                    .tactilePress(pressedScale = 0.96f, enabled = !isSharing) {
                        if (onShareClick != null) {
                            onShareClick()
                        } else {
                            coroutineScope.launch {
                                isSharing = true
                                try {
                                    WorkoutShareHelper.shareWorkoutSummary(context, summaryState)
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        "Could not share workout: ${e.localizedMessage}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } finally {
                                    isSharing = false
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isSharing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .width(16.dp)
                                .height(16.dp),
                            color = colors.electricCyan,
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "PREPARING SHARE CARD...",
                            style = typography.LabelUppercase.copy(
                                fontSize = 12.sp,
                                color = colors.electricCyan,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share Activity",
                            tint = colors.electricCyan,
                            modifier = Modifier
                                .width(18.dp)
                                .height(18.dp)
                        )
                        Text(
                            text = "SHARE ACTIVITY",
                            style = typography.LabelUppercase.copy(
                                fontSize = 12.sp,
                                color = colors.electricCyan,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        )
                    }
                }
            }

            // Return / Done Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ApexDimens.HeightTacticalButton)
                    .clip(RoundedCornerShape(ApexDimens.RadiusButtonTactical))
                    .background(colors.textPrimary)
                    .tactilePress(pressedScale = 0.96f) { onDoneClick() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "BACK TO LOGBOOK",
                    style = typography.LabelUppercase.copy(
                        fontSize = 12.sp,
                        color = colors.canvasBackground,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * Celebratory Header Banner with particle fireworks and expanding concentric victory rings.
 */
@Composable
private fun CelebratoryWorkoutHeader(
    title: String,
    activityType: String,
    progress: Float
) {
    val colors = ApexTheme.colors
    val typography = ApexTheme.typography
    val cardShape = remember { RoundedCornerShape(ApexDimens.RadiusCardStandard) }
    val pillShape = remember { RoundedCornerShape(ApexDimens.RadiusPillFull) }

    val infiniteTransition = rememberInfiniteTransition(label = "victory_pulse")
    val victoryAuraScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "victory_scale"
    )

    // Pre-calculated spark angles and colors
    val sparkColors = remember {
        listOf(
            Color(0xFF00F5D4), // Electric Cyan
            Color(0xFFD9531E), // Terracotta
            Color(0xFF00FF87), // Electric Lime
            Color(0xFFF59E0B), // Laser Amber
            Color(0xFFFFFFFF), // Pure White
            Color(0xFF38BDF8)  // Sky Blue
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(colors.surface)
            .border(1.dp, colors.borderSubtle, cardShape)
            .padding(14.dp)
    ) {
        // Background animated celebration particles
        Canvas(modifier = Modifier.matchParentSize()) {
            val cx = 28.dp.toPx()
            val cy = size.height / 2f
            val maxDistance = size.width * 0.7f

            if (progress < 0.98f) {
                val p = progress.coerceIn(0f, 1f)
                val alpha = (1f - p).coerceIn(0f, 1f) * 0.75f

                for (i in 0 until 24) {
                    val angle = (i * (360.0 / 24.0) * (Math.PI / 180.0)).toFloat()
                    val dist = (p * (maxDistance * (0.4f + (i % 5) * 0.15f)))
                    val sparkX = cx + dist * cos(angle)
                    val sparkY = cy + dist * sin(angle)
                    val sparkColor = sparkColors[i % sparkColors.size].copy(alpha = alpha)
                    val sparkRadius = (2.5f + (i % 3) * 1.2f) * (1f - p * 0.5f)

                    drawCircle(
                        color = sparkColor,
                        radius = sparkRadius,
                        center = Offset(sparkX, sparkY)
                    )
                }
            }

            // Concentric expanding ring around emblem
            val ringRadius = 22.dp.toPx() * victoryAuraScale
            drawCircle(
                color = colors.electricCyan.copy(alpha = 0.15f),
                radius = ringRadius,
                center = Offset(cx, cy),
                style = Stroke(1.5f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier.size(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ApexLogoMark(size = 32.dp)
                }

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(colors.electricCyan)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SESSION COMPLETED",
                            style = typography.LabelMicro.copy(
                                color = colors.electricCyan,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.3.sp
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = title,
                        style = typography.Headline.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = colors.textPrimary
                        )
                    )
                }
            }

            // Activity Type Badge
            Box(
                modifier = Modifier
                    .clip(pillShape)
                    .background(colors.surfaceHigh)
                    .border(1.dp, colors.borderSubtle, pillShape)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = activityType.uppercase(),
                    style = typography.LabelMicro.copy(
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Black,
                        fontSize = 9.sp,
                        letterSpacing = 0.8.sp
                    )
                )
            }
        }
    }
}

/**
 * Full-screen kinetic celebratory confetti & victory particle burst.
 * Explodes outward from the top-center upon workout completion, fluttering downward.
 */
@Composable
private fun CelebratoryConfettiBurstCanvas(
    progress: Float,
    modifier: Modifier = Modifier
) {
    if (progress >= 1f) return // Fade out once celebration settling is complete

    val particles = remember {
        val colors = listOf(
            Color(0xFF00F5D4), // Electric Cyan
            Color(0xFF00FF87), // Electric Lime
            Color(0xFFF59E0B), // Laser Amber
            Color(0xFFD9531E), // Terracotta Crimson
            Color(0xFFFFFFFF), // Pure Platinum
            Color(0xFF38BDF8), // Sky Cyan
            Color(0xFFA855F7)  // Electric Violet
        )
        List(56) { i ->
            val angle = ((i * 137.5) % 360.0) * (Math.PI / 180.0)
            val speed = 250f + (i * 37 % 500)
            ConfettiParticle(
                initialXRatio = 0.5f + (((i % 7) - 3) * 0.05f),
                initialYRatio = 0.12f,
                velocityX = (cos(angle) * speed).toFloat(),
                velocityY = (sin(angle) * speed * 0.7f - 180f).toFloat(),
                size = 6f + (i % 5) * 2.5f,
                isRibbon = i % 3 != 0,
                rotationSpeed = 120f + (i * 29 % 360),
                color = colors[i % colors.size]
            )
        }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val p = progress.coerceIn(0f, 1f)
        val alpha = ((1f - p) * 1.2f).coerceIn(0f, 1f)
        val gravity = 900f * (p * p)

        particles.forEachIndexed { i, particle ->
            val originX = particle.initialXRatio * w
            val originY = particle.initialYRatio * h

            // Drift with flutter
            val flutter = sin((p * 10f) + i) * 22f
            val curX = originX + (particle.velocityX * p) + flutter
            val curY = originY + (particle.velocityY * p) + (gravity * 0.5f)

            if (curX in -50f..(w + 50f) && curY in -50f..(h + 50f)) {
                val currentRot = (p * particle.rotationSpeed * 3f) % 360f
                val pColor = particle.color.copy(alpha = alpha)

                rotate(degrees = currentRot, pivot = Offset(curX, curY)) {
                    if (particle.isRibbon) {
                        drawRect(
                            color = pColor,
                            topLeft = Offset(curX - particle.size, curY - particle.size * 0.4f),
                            size = Size(particle.size * 2f, particle.size * 0.8f)
                        )
                    } else {
                        drawCircle(
                            color = pColor,
                            radius = particle.size * 0.6f,
                            center = Offset(curX, curY)
                        )
                    }
                }
            }
        }
    }
}

private data class ConfettiParticle(
    val initialXRatio: Float,
    val initialYRatio: Float,
    val velocityX: Float,
    val velocityY: Float,
    val size: Float,
    val isRibbon: Boolean,
    val rotationSpeed: Float,
    val color: Color
)
