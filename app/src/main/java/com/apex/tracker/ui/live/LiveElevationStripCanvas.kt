package com.apex.tracker.ui.live

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apex.tracker.ui.components.ElevationChartMath
import com.apex.tracker.ui.state.TrackPointDto
import com.apex.tracker.ui.theme.ApexDimens
import com.apex.tracker.ui.theme.ApexTheme
import java.util.Locale
import kotlin.math.roundToInt

private class ElevationStripRenderCache {
    var cachedWidth: Float = 0f
    var cachedHeight: Float = 0f
    var cachedAltitudesSize: Int = -1
    var lastPoint: Offset = Offset.Zero
    val strokePath = Path()
    val fillPath = Path()
}

@Composable
fun LiveElevationStripCanvas(
    trackPoints: List<TrackPointDto>,
    currentAltitudeMeters: Double,
    elevationGainMeters: Double,
    currentGradePercent: Double,
    modifier: Modifier = Modifier
) {
    val altitudes = remember(trackPoints) { trackPoints.map { it.altitude } }
    val bounds = remember(altitudes) { ElevationChartMath.computeBounds(altitudes, minSpanMeters = 2.0) }
    val colors = ApexTheme.colors
    val typography = ApexTheme.typography

    // Animated Chart Reveal Progress
    val drawProgress = remember { Animatable(0f) }
    LaunchedEffect(altitudes.size) {
        if (altitudes.size >= 2) {
            drawProgress.snapTo(0.2f)
            drawProgress.animateTo(1f, tween(550, easing = FastOutSlowInEasing))
        } else {
            drawProgress.snapTo(1f)
        }
    }

    // Cursor Breathing Pulse Animation
    val infiniteTransition = rememberInfiniteTransition(label = "elev_cursor")
    val cursorBreath by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_breath"
    )

    val cardShape = remember { RoundedCornerShape(ApexDimens.RadiusCardStandard) }
    val pillShape = remember { RoundedCornerShape(ApexDimens.RadiusPillFull) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(colors.surface)
            .border(1.dp, colors.borderSubtle, cardShape)
            .padding(10.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(10.dp)
                            .background(colors.electricCyan)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ELEVATION PROFILE",
                        style = typography.LabelUppercase.copy(fontSize = 10.sp)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Current Altitude Callout
                    Text(
                        text = String.format(Locale.US, "%.0f m", currentAltitudeMeters),
                        style = typography.TelemetryMicro.copy(
                            color = colors.textPureWhite,
                            fontWeight = FontWeight.Bold
                        )
                    )

                    // Grade % Pill
                    val gradeColor = when {
                        currentGradePercent > 3.0 -> colors.laserAmber
                        currentGradePercent < -3.0 -> colors.electricLime
                        else -> colors.slateSubtle
                    }
                    Box(
                        modifier = Modifier
                            .clip(pillShape)
                            .background(gradeColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = String.format(Locale.US, "%+.1f%%", currentGradePercent),
                            style = typography.TelemetryMicro.copy(color = gradeColor)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Canvas Area Chart (Height ~55dp inside 85dp total strip card)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp)
            ) {
                val cache = remember { ElevationStripRenderCache() }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    val vPadding = 6.dp.toPx()

                    if (altitudes.size < 2) {
                        // Draw horizontal center baseline
                        val baselineY = height / 2f
                        drawLine(
                            color = colors.electricCyan.copy(alpha = 0.4f),
                            start = Offset(0f, baselineY),
                            end = Offset(width, baselineY),
                            strokeWidth = 2.dp.toPx()
                        )
                        return@Canvas
                    }

                    // Rebuild paths only when canvas dimensions or altitudes data change
                    if (width != cache.cachedWidth || height != cache.cachedHeight || altitudes.size != cache.cachedAltitudesSize) {
                        cache.cachedWidth = width
                        cache.cachedHeight = height
                        cache.cachedAltitudesSize = altitudes.size

                        cache.strokePath.reset()
                        cache.fillPath.reset()

                        val stepX = width / (altitudes.size - 1).coerceAtLeast(1)
                        var lastX = 0f
                        var lastY = 0f

                        altitudes.forEachIndexed { index, alt ->
                            val x = index * stepX
                            val y = ElevationChartMath.altitudeToY(alt, bounds, height, vPadding)

                            if (index == 0) {
                                cache.strokePath.moveTo(x, y)
                                cache.fillPath.moveTo(x, height)
                                cache.fillPath.lineTo(x, y)
                            } else {
                                cache.strokePath.lineTo(x, y)
                                cache.fillPath.lineTo(x, y)
                            }

                            lastX = x
                            lastY = y
                        }

                        cache.fillPath.lineTo(lastX, height)
                        cache.fillPath.close()
                        cache.lastPoint = Offset(lastX, lastY)
                    }

                    // Draw animated elevation area and stroke revealed from left to right
                    clipRect(right = width * drawProgress.value) {
                        // Draw gradient fill under curve
                        drawPath(
                            path = cache.fillPath,
                            brush = Brush.verticalGradient(
                                listOf(
                                    colors.electricCyan.copy(alpha = 0.35f),
                                    colors.electricCyan.copy(alpha = 0.02f)
                                ),
                                startY = 0f,
                                endY = height
                            )
                        )

                        // Draw elevation stroke line
                        drawPath(
                            path = cache.strokePath,
                            color = colors.electricCyan,
                            style = Stroke(
                                width = 2.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }

                    // Draw animated current altitude cursor dot with breathing glow
                    drawCircle(
                        color = colors.electricCyanGlow,
                        radius = 6.dp.toPx() * cursorBreath,
                        center = cache.lastPoint
                    )
                    drawCircle(
                        color = colors.textPureWhite,
                        radius = 4.dp.toPx(),
                        center = cache.lastPoint
                    )
                    drawCircle(
                        color = colors.electricCyan,
                        radius = 2.dp.toPx(),
                        center = cache.lastPoint
                    )
                }

                // Min and Max Callout Overlay
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "min ${bounds.minAltitude.roundToInt()}m",
                        style = typography.TelemetryMicro.copy(fontSize = 8.sp, color = colors.slateMuted)
                    )
                    Text(
                        text = "max ${bounds.maxAltitude.roundToInt()}m",
                        style = typography.TelemetryMicro.copy(fontSize = 8.sp, color = colors.slateMuted)
                    )
                }
            }
        }
    }
}
