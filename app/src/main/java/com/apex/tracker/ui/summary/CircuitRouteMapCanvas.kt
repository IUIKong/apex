package com.apex.tracker.ui.summary

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apex.tracker.ui.components.MapProjectionMath
import com.apex.tracker.ui.state.TrackPointDto
import com.apex.tracker.ui.theme.ApexDimens
import com.apex.tracker.ui.theme.ApexTheme

private class CircuitRenderCache {
    var cachedWidth: Float = 0f
    var cachedHeight: Float = 0f
    var cachedPointsSize: Int = -1
    var screenCoords: FloatArray = FloatArray(0)
    var startPos: Offset = Offset.Zero
    var finishPos: Offset = Offset.Zero
    val circuitPath = Path()
}

@Composable
fun CircuitRouteMapCanvas(
    trackPoints: List<TrackPointDto>,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val gridStepPx = with(density) { 35.dp.toPx() }
    val colors = ApexTheme.colors
    val typography = ApexTheme.typography

    // Animated Circuit Route Trace Reveal
    val drawProgress = remember { Animatable(0f) }
    LaunchedEffect(trackPoints.size) {
        if (trackPoints.size > 1) {
            drawProgress.snapTo(0.05f)
            drawProgress.animateTo(1f, tween(950, easing = FastOutSlowInEasing))
        } else {
            drawProgress.snapTo(1f)
        }
    }

    val cache = remember { CircuitRenderCache() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(ApexDimens.RadiusCardStandard))
            .background(colors.canvasBackground)
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(ApexDimens.RadiusCardStandard))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // 1. Subtle 35dp Grid Lines
            val gridColor = colors.mapGridLine
            var curX = 0f
            while (curX < width) {
                drawLine(gridColor, Offset(curX, 0f), Offset(curX, height), 1f)
                curX += gridStepPx
            }
            var curY = 0f
            while (curY < height) {
                drawLine(gridColor, Offset(0f, curY), Offset(width, curY), 1f)
                curY += gridStepPx
            }

            if (trackPoints.isEmpty()) {
                val center = Offset(width / 2f, height / 2f)
                drawCircle(colors.borderActive.copy(alpha = 0.35f), radius = 3.dp.toPx(), center = center)
                return@Canvas
            }

            // 2. Precompute simplified screen projection only when dimensions or data change (ZERO allocations per frame)
            if (width != cache.cachedWidth || height != cache.cachedHeight || trackPoints.size != cache.cachedPointsSize) {
                cache.cachedWidth = width
                cache.cachedHeight = height
                cache.cachedPointsSize = trackPoints.size

                val fit = MapProjectionMath.computeAutoFitBoundsForPoints(
                    trackPoints = trackPoints,
                    canvasWidth = width,
                    canvasHeight = height,
                    padding = 36f
                )

                // Simplify points to screen threshold to prevent per-frame path overhead
                val coords = MapProjectionMath.computeSimplifiedScreenCoords(
                    trackPoints = trackPoints,
                    centerLat = fit.centerLat,
                    centerLon = fit.centerLon,
                    scale = fit.scale,
                    canvasWidth = width,
                    canvasHeight = height,
                    minDistancePx = 2.5f
                )
                cache.screenCoords = coords

                if (coords.isNotEmpty()) {
                    cache.startPos = Offset(coords[0], coords[1])
                    val lastIdx = coords.size - 2
                    cache.finishPos = Offset(coords[lastIdx], coords[lastIdx + 1])
                }
            }

            val coords = cache.screenCoords
            val totalPoints = coords.size / 2

            if (totalPoints >= 1) {
                // 3. Render animated circuit polyline up to current draw progress
                if (totalPoints >= 2) {
                    val maxIndex = (totalPoints * drawProgress.value).toInt().coerceIn(1, totalPoints)
                    cache.circuitPath.reset()
                    cache.circuitPath.moveTo(coords[0], coords[1])
                    for (index in 1 until maxIndex) {
                        cache.circuitPath.lineTo(coords[index * 2], coords[index * 2 + 1])
                    }

                    // Crisp white underlay halo lifting route cleanly from parchment
                    drawPath(
                        path = cache.circuitPath,
                        color = Color.White.copy(alpha = 0.9f),
                        style = Stroke(width = 5.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )

                    // Core Terracotta route line
                    drawPath(
                        path = cache.circuitPath,
                        color = colors.mapRouteTrace,
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }

                // 4. Start Pin (Forest Sage)
                val startPos = cache.startPos
                drawCircle(colors.electricLime.copy(alpha = 0.2f), radius = 8.dp.toPx(), center = startPos)
                drawCircle(colors.electricLime, radius = 4.5.dp.toPx(), center = startPos)
                drawCircle(Color.White, radius = 2.dp.toPx(), center = startPos)

                // 5. Finish Pin (Brick Rust) - Revealed when trace nears completion (for routes with >1 point)
                if (trackPoints.size > 1 && drawProgress.value >= 0.95f) {
                    val finishScale = ((drawProgress.value - 0.95f) / 0.05f).coerceIn(0f, 1f)
                    val finishPos = cache.finishPos
                    drawCircle(colors.punchyCrimson.copy(alpha = 0.2f), radius = 8.dp.toPx() * finishScale, center = finishPos)
                    drawCircle(colors.punchyCrimson, radius = 4.5.dp.toPx() * finishScale, center = finishPos)
                    drawCircle(Color.White, radius = 2.dp.toPx() * finishScale, center = finishPos)
                }
            }
        }

        // Header overlay
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(colors.electricCyan)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "ROUTE CIRCUIT TRAJECTORY",
                style = typography.LabelUppercase.copy(fontSize = 9.5.sp, color = colors.electricCyan, letterSpacing = 1.sp)
            )
        }

        // Legend overlay (Lime Start, Crimson Finish)
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "● START",
                style = typography.TelemetryMicro.copy(fontSize = 9.sp, color = colors.electricLime)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "● FINISH",
                style = typography.TelemetryMicro.copy(fontSize = 9.sp, color = colors.punchyCrimson)
            )
        }
    }
}
