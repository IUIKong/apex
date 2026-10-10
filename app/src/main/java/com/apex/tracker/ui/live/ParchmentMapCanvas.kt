package com.apex.tracker.ui.live

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.apex.tracker.ui.components.MapProjectionMath
import com.apex.tracker.ui.state.TrackPointDto
import com.apex.tracker.ui.theme.ApexDimens
import com.apex.tracker.ui.theme.ApexTheme

/**
 * Plain holder for route projection caches across Canvas redraw passes.
 * Zero MutableState snapshot writes inside DrawScope!
 */
private class RouteRenderCache {
    var cachedWidth: Float = 0f
    var cachedHeight: Float = 0f
    var cachedPointsSize: Int = -1
    var cachedLastTimestamp: Long = -1L
    var cachedZoomFactor: Float = 0f
    var cachedAutoFollow: Boolean = false
    var cachedShowRaw: Boolean = false
    var cachedEffectiveScale: Float = 0f
    var cachedCenterLat: Double = 0.0
    var cachedCenterLon: Double = 0.0
    var cachedStartPos: Offset = Offset.Zero
    var hasValidEkfPath: Boolean = false
    var hasValidRawPath: Boolean = false
    val rawPath = Path()
    val ekfPath = Path()
}

/**
 * Editorial Parchment Route Map Canvas.
 *
 * Implements a high-performance two-layer vector map surface:
 * 1. Static Route & Grid Canvas: Redrawn ONLY when route points, zoom, or viewport size change.
 *    Pans via GPU matrix translation without polyline rebuilding.
 * 2. Real-Time Animated Beacon Layer: Dedicated 60/120 Hz overlay for radar pulses, heading cone,
 *    and breathing location aura. Never recalculates or invalidates the underlying route polyline.
 * 3. Smooth Camera Auto-Follow: Centers and follows the runner continuously in real time.
 */
@Composable
fun ParchmentMapCanvas(
    trackPoints: List<TrackPointDto>,
    currentBearingDegrees: Float,
    currentAccuracyMeters: Float,
    showRawTrace: Boolean = true,
    autoFollow: Boolean = true,
    onToggleRawTrace: () -> Unit = {},
    onToggleAutoFollow: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }
    var zoomFactor by remember { mutableFloatStateOf(1f) }
    var isUserInteracting by remember { mutableStateOf(false) }

    // When auto-follow is re-enabled from outside, reset manual pan
    LaunchedEffect(autoFollow) {
        if (autoFollow) {
            panOffsetX = 0f
            panOffsetY = 0f
            isUserInteracting = false
        }
    }

    // Automatic 6-second inactivity reset timer to restore auto-follow
    LaunchedEffect(isUserInteracting, panOffsetX, panOffsetY, zoomFactor) {
        if (isUserInteracting) {
            delay(6000L)
            isUserInteracting = false
            panOffsetX = 0f
            panOffsetY = 0f
            zoomFactor = 1f
        }
    }

    val density = LocalDensity.current
    val gridStepPx = with(density) { 38.dp.toPx() }

    val colors = ApexTheme.colors
    val typography = ApexTheme.typography

    // Pulse & Breathing Animations for the Live Beacon Layer only
    val infiniteTransition = rememberInfiniteTransition(label = "parchment_radar")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_pulse"
    )
    val beaconBreath by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beacon_breath"
    )


    val cache = remember { RouteRenderCache() }
    val mapShape = remember { RoundedCornerShape(ApexDimens.RadiusCanvasMap) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(mapShape)
            .background(colors.canvasBackground)
            .border(1.dp, colors.borderSubtle, mapShape)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    isUserInteracting = true
                    panOffsetX += pan.x
                    panOffsetY += pan.y
                    zoomFactor = (zoomFactor * zoom).coerceIn(0.25f, 8f)
                }
            }
    ) {
        val effPanX = if (autoFollow && !isUserInteracting) 0f else panOffsetX
        val effPanY = if (autoFollow && !isUserInteracting) 0f else panOffsetY

        // LAYER 1: Static Route & Grid Canvas
        // Does NOT observe pulseProgress or beaconBreath. Zero 60/120 Hz redraw overhead!
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // 1. Subtle Parchment Coordinate Grid Lines (with wrap-safe modulo)
            val gridColor = colors.mapGridLine
            var curX = ((effPanX % gridStepPx) + gridStepPx) % gridStepPx
            while (curX < width) {
                drawLine(gridColor, Offset(curX, 0f), Offset(curX, height), 1f)
                curX += gridStepPx
            }
            var curY = ((effPanY % gridStepPx) + gridStepPx) % gridStepPx
            while (curY < height) {
                drawLine(gridColor, Offset(0f, curY), Offset(width, curY), 1f)
                curY += gridStepPx
            }

            if (trackPoints.isEmpty()) {
                val center = Offset(width / 2f + effPanX, height / 2f + effPanY)
                drawCircle(colors.borderActive.copy(alpha = 0.35f), radius = 3.dp.toPx(), center = center)
                return@Canvas
            }

            val lastPt = trackPoints.last()
            val firstPt = trackPoints.first()
            val lastTimestamp = lastPt.timestamp

            // Recompute projection & polylines ONLY when dimensions, data, zoom, autoFollow, or rawTrace change.
            // Pan changes are applied as GPU translations with ZERO polyline recalculation!
            val isNavActive = autoFollow && !isUserInteracting
            val needsRecompute = (width != cache.cachedWidth || height != cache.cachedHeight ||
                    trackPoints.size != cache.cachedPointsSize || lastTimestamp != cache.cachedLastTimestamp ||
                    zoomFactor != cache.cachedZoomFactor || isNavActive != cache.cachedAutoFollow ||
                    showRawTrace != cache.cachedShowRaw)

            if (needsRecompute) {
                cache.cachedWidth = width
                cache.cachedHeight = height
                cache.cachedPointsSize = trackPoints.size
                cache.cachedLastTimestamp = lastTimestamp
                cache.cachedZoomFactor = zoomFactor
                cache.cachedAutoFollow = isNavActive
                cache.cachedShowRaw = showRawTrace

                val fit = MapProjectionMath.computeAutoFitBoundsForPoints(
                    trackPoints = trackPoints,
                    canvasWidth = width,
                    canvasHeight = height,
                    padding = 52f,
                    minSpanDegrees = 0.0012
                )

                val centerLat: Double
                val centerLon: Double
                val effectiveScale: Float

                if (isNavActive) {
                    // Smooth Camera Auto-Follow: Keep runner centered on screen
                    centerLat = lastPt.latitude
                    centerLon = lastPt.longitude
                    // Focused navigation scale (~275m across display at 1.0x)
                    val navBaseScale = (width / 0.0025f)
                    effectiveScale = navBaseScale * zoomFactor
                } else {
                    // Full route overview / Fit bounds mode
                    centerLat = fit.centerLat
                    centerLon = fit.centerLon
                    effectiveScale = fit.scale * zoomFactor
                }

                cache.cachedCenterLat = centerLat
                cache.cachedCenterLon = centerLon
                cache.cachedEffectiveScale = effectiveScale

                // Start position screen coordinates (relative to pan=0)
                cache.cachedStartPos = MapProjectionMath.projectToCanvas(
                    lat = firstPt.latitude, lon = firstPt.longitude,
                    centerLat = centerLat, centerLon = centerLon,
                    scale = effectiveScale, canvasWidth = width, canvasHeight = height,
                    panOffsetX = 0f, panOffsetY = 0f
                )

                // Build Raw GPS Path (zero allocations)
                cache.hasValidRawPath = if (showRawTrace && trackPoints.size > 1) {
                    MapProjectionMath.buildSmoothedPolyline(
                        trackPoints = trackPoints,
                        targetPath = cache.rawPath,
                        centerLat = centerLat,
                        centerLon = centerLon,
                        scale = effectiveScale,
                        canvasWidth = width,
                        canvasHeight = height,
                        panOffsetX = 0f,
                        panOffsetY = 0f,
                        minDistancePx = 3f,
                        useRawCoordinates = true
                    )
                } else false

                // Build Filtered EKF Polyline with Bezier smoothing (zero allocations)
                cache.hasValidEkfPath = if (trackPoints.size > 1) {
                    MapProjectionMath.buildSmoothedPolyline(
                        trackPoints = trackPoints,
                        targetPath = cache.ekfPath,
                        centerLat = centerLat,
                        centerLon = centerLon,
                        scale = effectiveScale,
                        canvasWidth = width,
                        canvasHeight = height,
                        panOffsetX = 0f,
                        panOffsetY = 0f,
                        minDistancePx = 2.5f,
                        useRawCoordinates = false
                    )
                } else false
            }

            // Draw route and start pin with GPU matrix translation for buttery smooth 120 FPS panning
            withTransform({
                translate(effPanX, effPanY)
            }) {
                // 2. Draw Raw GPS Trace
                if (showRawTrace && cache.hasValidRawPath) {
                    drawPath(
                        path = cache.rawPath,
                        color = colors.slateMuted.copy(alpha = 0.45f),
                        style = Stroke(
                            width = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 10f))
                        )
                    )
                }

                // 3. Draw Filtered EKF Polyline
                if (cache.hasValidEkfPath) {
                    // Crisp white underlay halo to separate route from grid
                    drawPath(
                        path = cache.ekfPath,
                        color = Color.White.copy(alpha = 0.9f),
                        style = Stroke(
                            width = 5.5.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                    // Core Terracotta route line
                    drawPath(
                        path = cache.ekfPath,
                        color = colors.mapRouteTrace,
                        style = Stroke(
                            width = 3.5.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }

                // 4. Start Origin: Circular Blue Tiny orb
                val startPos = cache.cachedStartPos
                val orbBlue = Color(0xFF2563EB)
                val orbHalo = Color(0x332563EB)
                drawCircle(orbHalo, radius = 5.dp.toPx(), center = startPos)
                drawCircle(orbBlue, radius = 3.5.dp.toPx(), center = startPos)
                drawCircle(Color(0xFFDBEAFE), radius = 1.2.dp.toPx(), center = startPos)
            }
        }

        // LAYER 2: Real-Time Live Beacon & Heading Layer
        // Draws calm location aura, accuracy bound, and heading indicator
        if (trackPoints.isNotEmpty()) {
            val lastPt = trackPoints.last()
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height

                val effectiveScale = if (cache.cachedEffectiveScale > 0f) {
                    cache.cachedEffectiveScale
                } else {
                    (width / 0.0025f) * zoomFactor
                }
                val centerLat = if (cache.cachedCenterLat != 0.0) cache.cachedCenterLat else lastPt.latitude
                val centerLon = if (cache.cachedCenterLon != 0.0) cache.cachedCenterLon else lastPt.longitude

                val curPos = MapProjectionMath.projectToCanvas(
                    lat = lastPt.latitude,
                    lon = lastPt.longitude,
                    centerLat = centerLat,
                    centerLon = centerLon,
                    scale = effectiveScale,
                    canvasWidth = width,
                    canvasHeight = height,
                    panOffsetX = effPanX,
                    panOffsetY = effPanY
                )

                // Calm Accuracy Corridor (Subtle hairline contour, zero eye strain)
                val accRadiusPx = (currentAccuracyMeters * 2.0f).coerceIn(12f, 75f)
                drawCircle(colors.electricCyanDim, radius = accRadiusPx, center = curPos)
                drawCircle(colors.borderSubtle, radius = accRadiusPx, center = curPos, style = Stroke(1f))

                // 2. Calm Radar Pulse Ripple (Smooth periodic outward expanding wave)
                val pulseRadiusPx = 8.dp.toPx() + 18.dp.toPx() * pulseProgress
                val pulseAlpha = (1f - pulseProgress).coerceIn(0f, 1f) * 0.28f
                drawCircle(colors.electricCyan.copy(alpha = pulseAlpha), radius = pulseRadiusPx, center = curPos)

                // 3. Calm Breathing Cyan Outer Aura (soft glow pulsing smoothly)
                val auraRadiusPx = 10.dp.toPx() * beaconBreath
                drawCircle(colors.electricCyan.copy(alpha = 0.20f), radius = auraRadiusPx, center = curPos)

                // 4. Crisp circular white ring border
                drawCircle(Color.White, radius = 6.dp.toPx(), center = curPos)

                // 5. Core solid electric cyan dot
                drawCircle(colors.electricCyan, radius = 4.2.dp.toPx(), center = curPos)

                // 6. Centered obsidian pin core
                drawCircle(colors.canvasBackground, radius = 1.8.dp.toPx(), center = curPos)
            }
        }

        // Floating RECENTER pill when map is manually panned/pinched
        AnimatedVisibility(
            visible = isUserInteracting,
            enter = fadeIn(tween(220, easing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f))) +
                scaleIn(tween(220, easing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)), initialScale = 0.85f),
            exit = fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.85f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
        ) {
            val recenterShape = remember { RoundedCornerShape(16.dp) }
            Box(
                modifier = Modifier
                    .clip(recenterShape)
                    .background(colors.surfaceElevated.copy(alpha = 0.94f))
                    .border(1.dp, colors.electricCyan.copy(alpha = 0.7f), recenterShape)
                    .clickable {
                        isUserInteracting = false
                        panOffsetX = 0f
                        panOffsetY = 0f
                        zoomFactor = 1f
                        if (!autoFollow) {
                            onToggleAutoFollow()
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "RECENTER",
                    style = typography.LabelUppercase,
                    color = colors.electricCyan
                )
            }
        }
    }
}
