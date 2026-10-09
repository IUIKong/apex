package com.apex.tracker.ui.summary

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apex.tracker.ui.components.ElevationChartMath
import com.apex.tracker.ui.state.TrackPointDto
import com.apex.tracker.ui.theme.ApexDimens
import com.apex.tracker.ui.theme.ApexTheme
import kotlin.math.roundToInt

private class SummaryElevationRenderCache {
    var cachedWidth: Float = 0f
    var cachedHeight: Float = 0f
    var cachedAltitudesSize: Int = -1
    val strokePath = Path()
    val fillPath = Path()
}

@Composable
fun SummaryElevationChartCanvas(
    trackPoints: List<TrackPointDto>,
    totalElevationGainMeters: Double,
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
            drawProgress.snapTo(0.1f)
            drawProgress.animateTo(1f, tween(750, easing = FastOutSlowInEasing))
        } else {
            drawProgress.snapTo(1f)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ApexDimens.RadiusCardStandard))
            .background(colors.surface)
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(ApexDimens.RadiusCardStandard))
            .padding(12.dp)
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
                        text = "ELEVATION PROFILE & GRADE",
                        style = typography.LabelUppercase.copy(fontSize = 10.sp)
                    )
                }

                Text(
                    text = "GAIN: +${totalElevationGainMeters.roundToInt()}m",
                    style = typography.TelemetryMicro.copy(
                        color = colors.laserAmber,
                        fontSize = 10.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
            ) {
                val cache = remember { SummaryElevationRenderCache() }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    val vPadding = 8.dp.toPx()

                    if (altitudes.size < 2) {
                        val midY = height / 2f
                        drawLine(
                            color = colors.electricCyan.copy(alpha = 0.4f),
                            start = Offset(0f, midY),
                            end = Offset(width, midY),
                            strokeWidth = 2.dp.toPx()
                        )
                        return@Canvas
                    }

                    if (width != cache.cachedWidth || height != cache.cachedHeight || altitudes.size != cache.cachedAltitudesSize) {
                        cache.cachedWidth = width
                        cache.cachedHeight = height
                        cache.cachedAltitudesSize = altitudes.size

                        cache.strokePath.reset()
                        cache.fillPath.reset()

                        val stepX = width / (altitudes.size - 1).coerceAtLeast(1)
                        var lastX = 0f

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
                        }

                        cache.fillPath.lineTo(lastX, height)
                        cache.fillPath.close()
                    }

                    // Draw animated profile sweep revealed from left to right
                    clipRect(right = width * drawProgress.value) {
                        // Vertical gradient fill
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

                        // Profile stroke
                        drawPath(
                            path = cache.strokePath,
                            color = colors.electricCyan,
                            style = Stroke(
                                width = 2.5.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }

                // Min and Max callouts
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "MIN: ${bounds.minAltitude.roundToInt()}m",
                        style = typography.TelemetryMicro.copy(fontSize = 9.sp, color = colors.slateMuted)
                    )
                    Text(
                        text = "MAX: ${bounds.maxAltitude.roundToInt()}m",
                        style = typography.TelemetryMicro.copy(fontSize = 9.sp, color = colors.slateMuted)
                    )
                }
            }
        }
    }
}
