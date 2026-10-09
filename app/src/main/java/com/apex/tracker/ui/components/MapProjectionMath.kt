package com.apex.tracker.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import com.apex.tracker.ui.state.TrackPointDto
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max

@Immutable
data class BoundingBoxFit(
    val centerLat: Double,
    val centerLon: Double,
    val scale: Float
)

object MapProjectionMath {

    /**
     * Projects WGS84 (lat, lon) to 2D Canvas pixel coordinates using local Tangential
     * Equirectangular projection relative to a center anchor.
     */
    fun projectToCanvas(
        lat: Double,
        lon: Double,
        centerLat: Double,
        centerLon: Double,
        scale: Float,
        canvasWidth: Float,
        canvasHeight: Float,
        panOffsetX: Float = 0f,
        panOffsetY: Float = 0f
    ): Offset {
        val clampedLat = centerLat.coerceIn(-89.9, 89.9)
        val latRad = clampedLat * (PI / 180.0)
        val cosLat = max(0.01, cos(latRad))

        // Delta in degrees with antimeridian normalization
        val dLon = normalizeLongitudeDelta(lon - centerLon)
        val dLat = lat - clampedLat

        // Cartesian mapping (y inverted since Canvas origin is top-left)
        val x = (dLon * cosLat * scale).toFloat() + (canvasWidth / 2f) + panOffsetX
        val y = (-dLat * scale).toFloat() + (canvasHeight / 2f) + panOffsetY

        return Offset(x, y)
    }

    /**
     * Calculates the center anchor and auto-fitting scale to fit all coordinates
     * completely within the given Canvas dimensions with padding.
     */
    fun computeAutoFitBounds(
        coordinates: List<Pair<Double, Double>>,
        canvasWidth: Float,
        canvasHeight: Float,
        padding: Float = 32f,
        minSpanDegrees: Double = 0.0001
    ): BoundingBoxFit {
        val usableWidth = max(canvasWidth - 2f * padding, 10f)
        val usableHeight = max(canvasHeight - 2f * padding, 10f)

        if (coordinates.isEmpty()) {
            return BoundingBoxFit(centerLat = 0.0, centerLon = 0.0, scale = 1000f)
        }
        if (coordinates.size == 1) {
            val (lat, lon) = coordinates.first()
            val clampedLat = lat.coerceIn(-89.9, 89.9)
            val cosLat = max(0.01, cos(clampedLat * (PI / 180.0)))
            val scaleX = (usableWidth / (minSpanDegrees * cosLat)).toFloat()
            val scaleY = (usableHeight / minSpanDegrees).toFloat()
            return BoundingBoxFit(centerLat = lat, centerLon = lon, scale = minOf(scaleX, scaleY))
        }

        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE

        val anchorLon = coordinates.first().second
        var minUnwrappedLon = 0.0
        var maxUnwrappedLon = 0.0

        for (i in coordinates.indices) {
            val (lat, lon) = coordinates[i]
            if (lat < minLat) minLat = lat
            if (lat > maxLat) maxLat = lat

            val dLon = normalizeLongitudeDelta(lon - anchorLon)
            if (i == 0) {
                minUnwrappedLon = dLon
                maxUnwrappedLon = dLon
            } else {
                if (dLon < minUnwrappedLon) minUnwrappedLon = dLon
                if (dLon > maxUnwrappedLon) maxUnwrappedLon = dLon
            }
        }

        val centerLat = (minLat + maxLat) / 2.0
        val centerLonDelta = (minUnwrappedLon + maxUnwrappedLon) / 2.0
        val centerLon = normalizeLongitude(anchorLon + centerLonDelta)

        val latSpan = max(maxLat - minLat, minSpanDegrees)
        val lonSpan = max(maxUnwrappedLon - minUnwrappedLon, minSpanDegrees)

        val clampedLat = centerLat.coerceIn(-89.9, 89.9)
        val cosLat = max(0.01, cos(clampedLat * (PI / 180.0)))

        val scaleX = (usableWidth / (lonSpan * cosLat)).toFloat()
        val scaleY = (usableHeight / latSpan).toFloat()

        val optimalScale = minOf(scaleX, scaleY)

        return BoundingBoxFit(
            centerLat = centerLat,
            centerLon = centerLon,
            scale = optimalScale
        )
    }

    /**
     * Calculates the center anchor and auto-fitting scale directly for a list of [TrackPointDto]s
     * without intermediate Pair/List object allocations.
     */
    fun computeAutoFitBoundsForPoints(
        trackPoints: List<TrackPointDto>,
        canvasWidth: Float,
        canvasHeight: Float,
        padding: Float = 32f,
        minSpanDegrees: Double = 0.0001
    ): BoundingBoxFit {
        val usableWidth = max(canvasWidth - 2f * padding, 10f)
        val usableHeight = max(canvasHeight - 2f * padding, 10f)

        if (trackPoints.isEmpty()) {
            return BoundingBoxFit(centerLat = 0.0, centerLon = 0.0, scale = 1000f)
        }
        if (trackPoints.size == 1) {
            val pt = trackPoints.first()
            val clampedLat = pt.latitude.coerceIn(-89.9, 89.9)
            val cosLat = max(0.01, cos(clampedLat * (PI / 180.0)))
            val scaleX = (usableWidth / (minSpanDegrees * cosLat)).toFloat()
            val scaleY = (usableHeight / minSpanDegrees).toFloat()
            return BoundingBoxFit(centerLat = pt.latitude, centerLon = pt.longitude, scale = minOf(scaleX, scaleY))
        }

        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE

        val anchorLon = trackPoints.first().longitude
        var minUnwrappedLon = 0.0
        var maxUnwrappedLon = 0.0

        for (i in trackPoints.indices) {
            val pt = trackPoints[i]
            val lat = pt.latitude
            val lon = pt.longitude
            if (lat < minLat) minLat = lat
            if (lat > maxLat) maxLat = lat

            val dLon = normalizeLongitudeDelta(lon - anchorLon)
            if (i == 0) {
                minUnwrappedLon = dLon
                maxUnwrappedLon = dLon
            } else {
                if (dLon < minUnwrappedLon) minUnwrappedLon = dLon
                if (dLon > maxUnwrappedLon) maxUnwrappedLon = dLon
            }
        }

        val centerLat = (minLat + maxLat) / 2.0
        val centerLonDelta = (minUnwrappedLon + maxUnwrappedLon) / 2.0
        val centerLon = normalizeLongitude(anchorLon + centerLonDelta)

        val latSpan = max(maxLat - minLat, minSpanDegrees)
        val lonSpan = max(maxUnwrappedLon - minUnwrappedLon, minSpanDegrees)

        val clampedLat = centerLat.coerceIn(-89.9, 89.9)
        val cosLat = max(0.01, cos(clampedLat * (PI / 180.0)))

        val scaleX = (usableWidth / (lonSpan * cosLat)).toFloat()
        val scaleY = (usableHeight / latSpan).toFloat()

        val optimalScale = minOf(scaleX, scaleY)

        return BoundingBoxFit(
            centerLat = centerLat,
            centerLon = centerLon,
            scale = optimalScale
        )
    }

    /**
     * Projects and simplifies track points into a flat FloatArray [x0, y0, x1, y1, ...]
     * with minimal single-buffer allocation.
     */
    fun computeSimplifiedScreenCoords(
        trackPoints: List<TrackPointDto>,
        centerLat: Double,
        centerLon: Double,
        scale: Float,
        canvasWidth: Float,
        canvasHeight: Float,
        panOffsetX: Float = 0f,
        panOffsetY: Float = 0f,
        minDistancePx: Float = 2.5f,
        useRawCoordinates: Boolean = false
    ): FloatArray {
        if (trackPoints.size < 2) return FloatArray(0)

        val clampedLat = centerLat.coerceIn(-89.9, 89.9)
        val latRad = clampedLat * (PI / 180.0)
        val cosLat = max(0.01, cos(latRad))
        val halfW = (canvasWidth / 2f) + panOffsetX
        val halfH = (canvasHeight / 2f) + panOffsetY
        val minDistSq = minDistancePx * minDistancePx

        // Preallocate single buffer for max possible points: trackPoints.size * 2
        val buffer = FloatArray(trackPoints.size * 2)

        // First point
        val first = trackPoints.first()
        val fLat = if (useRawCoordinates) first.rawLatitude else first.latitude
        val fLon = if (useRawCoordinates) first.rawLongitude else first.longitude
        val dLon0 = normalizeLongitudeDelta(fLon - centerLon)
        val dLat0 = fLat - clampedLat
        buffer[0] = (dLon0 * cosLat * scale).toFloat() + halfW
        buffer[1] = (-dLat0 * scale).toFloat() + halfH
        var count = 1
        var lastX = buffer[0]
        var lastY = buffer[1]

        // Intermediate points simplified by min distance threshold
        for (i in 1 until trackPoints.size - 1) {
            val pt = trackPoints[i]
            val lat = if (useRawCoordinates) pt.rawLatitude else pt.latitude
            val lon = if (useRawCoordinates) pt.rawLongitude else pt.longitude
            val dLon = normalizeLongitudeDelta(lon - centerLon)
            val dLat = lat - clampedLat
            val x = (dLon * cosLat * scale).toFloat() + halfW
            val y = (-dLat * scale).toFloat() + halfH

            val dx = x - lastX
            val dy = y - lastY
            if (dx * dx + dy * dy >= minDistSq) {
                buffer[count * 2] = x
                buffer[count * 2 + 1] = y
                count++
                lastX = x
                lastY = y
            }
        }

        // Final point always preserved
        val last = trackPoints.last()
        val lLat = if (useRawCoordinates) last.rawLatitude else last.latitude
        val lLon = if (useRawCoordinates) last.rawLongitude else last.longitude
        val dLonN = normalizeLongitudeDelta(lLon - centerLon)
        val dLatN = lLat - clampedLat
        buffer[count * 2] = (dLonN * cosLat * scale).toFloat() + halfW
        buffer[count * 2 + 1] = (-dLatN * scale).toFloat() + halfH
        count++

        return buffer.copyOf(count * 2)
    }

    /**
     * Builds a projected and smoothed/simplified polyline directly into [targetPath] with
     * ZERO intermediate List<Offset>, Pair, or FloatArray allocations inside Compose draw pipelines.
     *
     * @return true if targetPath was populated with at least 2 points.
     */
    fun buildSmoothedPolyline(
        trackPoints: List<TrackPointDto>,
        targetPath: Path,
        centerLat: Double,
        centerLon: Double,
        scale: Float,
        canvasWidth: Float,
        canvasHeight: Float,
        panOffsetX: Float = 0f,
        panOffsetY: Float = 0f,
        minDistancePx: Float = 2.5f,
        useRawCoordinates: Boolean = false
    ): Boolean {
        if (trackPoints.size < 2) return false

        val clampedLat = centerLat.coerceIn(-89.9, 89.9)
        val latRad = clampedLat * (PI / 180.0)
        val cosLat = max(0.01, cos(latRad))
        val halfW = (canvasWidth / 2f) + panOffsetX
        val halfH = (canvasHeight / 2f) + panOffsetY
        val minDistSq = minDistancePx * minDistancePx

        // First point
        val first = trackPoints.first()
        val fLat = if (useRawCoordinates) first.rawLatitude else first.latitude
        val fLon = if (useRawCoordinates) first.rawLongitude else first.longitude
        val p0x = (normalizeLongitudeDelta(fLon - centerLon) * cosLat * scale).toFloat() + halfW
        val p0y = (-(fLat - clampedLat) * scale).toFloat() + halfH

        targetPath.reset()
        targetPath.moveTo(p0x, p0y)

        // For raw coordinates: direct line segments without Bezier curves
        if (useRawCoordinates) {
            var lastX = p0x
            var lastY = p0y
            for (i in 1 until trackPoints.size) {
                val pt = trackPoints[i]
                val lat = pt.rawLatitude
                val lon = pt.rawLongitude
                val x = (normalizeLongitudeDelta(lon - centerLon) * cosLat * scale).toFloat() + halfW
                val y = (-(lat - clampedLat) * scale).toFloat() + halfH
                val dx = x - lastX
                val dy = y - lastY
                if (i == trackPoints.size - 1 || dx * dx + dy * dy >= minDistSq) {
                    targetPath.lineTo(x, y)
                    lastX = x
                    lastY = y
                }
            }
            return true
        }

        // For smoothed EKF polylines: quadratic Bezier streaming with zero allocations
        var prevX = p0x
        var prevY = p0y
        var isFirstMid = true
        var pointCount = 1

        for (i in 1 until trackPoints.size - 1) {
            val pt = trackPoints[i]
            val lat = pt.latitude
            val lon = pt.longitude
            val currX = (normalizeLongitudeDelta(lon - centerLon) * cosLat * scale).toFloat() + halfW
            val currY = (-(lat - clampedLat) * scale).toFloat() + halfH

            val dx = currX - prevX
            val dy = currY - prevY
            if (dx * dx + dy * dy >= minDistSq) {
                val midX = (prevX + currX) / 2f
                val midY = (prevY + currY) / 2f
                if (isFirstMid) {
                    targetPath.lineTo(midX, midY)
                    isFirstMid = false
                } else {
                    targetPath.quadraticTo(prevX, prevY, midX, midY)
                }
                prevX = currX
                prevY = currY
                pointCount++
            }
        }

        // Final point always connected
        val last = trackPoints.last()
        val lastX = (normalizeLongitudeDelta(last.longitude - centerLon) * cosLat * scale).toFloat() + halfW
        val lastY = (-(last.latitude - clampedLat) * scale).toFloat() + halfH
        if (pointCount > 1) {
            targetPath.quadraticTo(prevX, prevY, (prevX + lastX) / 2f, (prevY + lastY) / 2f)
        }
        targetPath.lineTo(lastX, lastY)

        return true
    }

    /**
     * Normalizes longitude into (-180, 180].
     */
    fun normalizeLongitude(lon: Double): Double {
        var l = lon
        while (l > 180.0) l -= 360.0
        while (l <= -180.0) l += 360.0
        return l
    }

    /**
     * Normalizes a delta angle or longitude difference into [-180, 180].
     */
    fun normalizeLongitudeDelta(dLon: Double): Double {
        var delta = dLon
        while (delta > 180.0) delta -= 360.0
        while (delta < -180.0) delta += 360.0
        return delta
    }

    /**
     * Simplifies canvas screen offsets by dropping consecutive points that are within [minDistancePx].
     * Prevents micro-segment rendering artifacts and improves canvas performance.
     */
    fun simplifyOffsets(offsets: List<Offset>, minDistancePx: Float = 2.5f): List<Offset> {
        if (offsets.size <= 2) return offsets
        val result = mutableListOf<Offset>()
        result.add(offsets.first())
        var last = offsets.first()
        val minDistSq = minDistancePx * minDistancePx

        for (i in 1 until offsets.size - 1) {
            val curr = offsets[i]
            val dx = curr.x - last.x
            val dy = curr.y - last.y
            if (dx * dx + dy * dy >= minDistSq) {
                result.add(curr)
                last = curr
            }
        }
        result.add(offsets.last())
        return result
    }
}
