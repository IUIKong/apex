package com.apex.tracker.ui

import androidx.compose.ui.graphics.Path
import com.apex.tracker.ui.components.MapProjectionMath
import com.apex.tracker.ui.state.TrackPointDto
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MapProjectionMathTest {

    @Test
    fun testCenterProjectsToCanvasCenter() {
        val centerLat = 37.7749
        val centerLon = -122.4194
        val canvasWidth = 400f
        val canvasHeight = 600f

        val projected = MapProjectionMath.projectToCanvas(
            lat = centerLat,
            lon = centerLon,
            centerLat = centerLat,
            centerLon = centerLon,
            scale = 1000f,
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight
        )

        assertThat(projected.x).isWithin(0.01f).of(200f)
        assertThat(projected.y).isWithin(0.01f).of(300f)
    }

    @Test
    fun testCardinalDirectionProjectionDirections() {
        val centerLat = 45.0
        val centerLon = 10.0
        val canvasWidth = 500f
        val canvasHeight = 500f
        val scale = 10000f

        // North: higher latitude -> lower Y on screen
        val north = MapProjectionMath.projectToCanvas(
            lat = 45.01, lon = 10.0,
            centerLat = centerLat, centerLon = centerLon,
            scale = scale, canvasWidth = canvasWidth, canvasHeight = canvasHeight
        )
        assertThat(north.y).isLessThan(250f)
        assertThat(north.x).isWithin(0.01f).of(250f)

        // South: lower latitude -> higher Y on screen
        val south = MapProjectionMath.projectToCanvas(
            lat = 44.99, lon = 10.0,
            centerLat = centerLat, centerLon = centerLon,
            scale = scale, canvasWidth = canvasWidth, canvasHeight = canvasHeight
        )
        assertThat(south.y).isGreaterThan(250f)
        assertThat(south.x).isWithin(0.01f).of(250f)

        // East: higher longitude -> higher X on screen
        val east = MapProjectionMath.projectToCanvas(
            lat = 45.0, lon = 10.01,
            centerLat = centerLat, centerLon = centerLon,
            scale = scale, canvasWidth = canvasWidth, canvasHeight = canvasHeight
        )
        assertThat(east.x).isGreaterThan(250f)
        assertThat(east.y).isWithin(0.01f).of(250f)

        // West: lower longitude -> lower X on screen
        val west = MapProjectionMath.projectToCanvas(
            lat = 45.0, lon = 9.99,
            centerLat = centerLat, centerLon = centerLon,
            scale = scale, canvasWidth = canvasWidth, canvasHeight = canvasHeight
        )
        assertThat(west.x).isLessThan(250f)
        assertThat(west.y).isWithin(0.01f).of(250f)
    }

    @Test
    fun testPanOffsetShiftsProjection() {
        val centerLat = 37.0
        val centerLon = -122.0
        val canvasWidth = 400f
        val canvasHeight = 400f

        val projected = MapProjectionMath.projectToCanvas(
            lat = centerLat,
            lon = centerLon,
            centerLat = centerLat,
            centerLon = centerLon,
            scale = 1000f,
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight,
            panOffsetX = 45f,
            panOffsetY = -30f
        )

        assertThat(projected.x).isWithin(0.01f).of(245f)
        assertThat(projected.y).isWithin(0.01f).of(170f)
    }

    @Test
    fun testComputeAutoFitBoundsEmptyAndSinglePoint() {
        val emptyBounds = MapProjectionMath.computeAutoFitBounds(emptyList(), 300f, 300f)
        assertThat(emptyBounds.scale).isGreaterThan(0f)

        val single = listOf(Pair(37.77, -122.42))
        val singleBounds = MapProjectionMath.computeAutoFitBounds(single, 300f, 300f)
        assertThat(singleBounds.centerLat).isEqualTo(37.77)
        assertThat(singleBounds.centerLon).isEqualTo(-122.42)
        assertThat(singleBounds.scale).isGreaterThan(0f)
    }

    @Test
    fun testComputeAutoFitBoundsFitsWithinCanvasPadding() {
        val points = listOf(
            Pair(37.7700, -122.4200),
            Pair(37.7800, -122.4200),
            Pair(37.7800, -122.4100),
            Pair(37.7700, -122.4100)
        )
        val canvasW = 500f
        val canvasH = 400f
        val padding = 30f

        val fit = MapProjectionMath.computeAutoFitBounds(points, canvasW, canvasH, padding)

        assertThat(fit.centerLat).isWithin(0.0001).of(37.7750)
        assertThat(fit.centerLon).isWithin(0.0001).of(-122.4150)
        assertThat(fit.scale).isGreaterThan(0f)

        // Verify every point projects within [padding - 1, canvas - padding + 1]
        for ((lat, lon) in points) {
            val p = MapProjectionMath.projectToCanvas(
                lat = lat,
                lon = lon,
                centerLat = fit.centerLat,
                centerLon = fit.centerLon,
                scale = fit.scale,
                canvasWidth = canvasW,
                canvasHeight = canvasH
            )
            assertThat(p.x).isAtLeast(padding - 1f)
            assertThat(p.x).isAtMost(canvasW - padding + 1f)
            assertThat(p.y).isAtLeast(padding - 1f)
            assertThat(p.y).isAtMost(canvasH - padding + 1f)
        }
    }

    @Test
    fun testComputeAutoFitBoundsForPointsMatchesPairs() {
        val dtos = listOf(
            TrackPointDto(latitude = 37.7700, longitude = -122.4200, altitude = 10.0, speedMps = 3f, bearing = 0f, timestamp = 1000L),
            TrackPointDto(latitude = 37.7800, longitude = -122.4200, altitude = 11.0, speedMps = 3f, bearing = 0f, timestamp = 2000L),
            TrackPointDto(latitude = 37.7800, longitude = -122.4100, altitude = 12.0, speedMps = 3f, bearing = 0f, timestamp = 3000L),
            TrackPointDto(latitude = 37.7700, longitude = -122.4100, altitude = 10.0, speedMps = 3f, bearing = 0f, timestamp = 4000L)
        )
        val pairs = dtos.map { Pair(it.latitude, it.longitude) }

        val fitDtos = MapProjectionMath.computeAutoFitBoundsForPoints(dtos, 500f, 400f, 30f)
        val fitPairs = MapProjectionMath.computeAutoFitBounds(pairs, 500f, 400f, 30f)

        assertThat(fitDtos.centerLat).isWithin(1e-6).of(fitPairs.centerLat)
        assertThat(fitDtos.centerLon).isWithin(1e-6).of(fitPairs.centerLon)
        assertThat(fitDtos.scale).isWithin(1e-3f).of(fitPairs.scale)
    }

    @Test
    fun testComputeSimplifiedScreenCoordsDirectProjection() {
        val dtos = listOf(
            TrackPointDto(latitude = 37.7700, longitude = -122.4200, altitude = 10.0, speedMps = 3f, bearing = 0f, timestamp = 1000L),
            TrackPointDto(latitude = 37.7750, longitude = -122.4150, altitude = 11.0, speedMps = 3f, bearing = 0f, timestamp = 2000L),
            TrackPointDto(latitude = 37.7800, longitude = -122.4100, altitude = 12.0, speedMps = 3f, bearing = 0f, timestamp = 3000L)
        )
        val fit = MapProjectionMath.computeAutoFitBoundsForPoints(dtos, 400f, 400f, 20f)

        // 1. EKF Path coordinates
        val ekfCoords = MapProjectionMath.computeSimplifiedScreenCoords(
            trackPoints = dtos,
            centerLat = fit.centerLat,
            centerLon = fit.centerLon,
            scale = fit.scale,
            canvasWidth = 400f,
            canvasHeight = 400f,
            minDistancePx = 2.5f,
            useRawCoordinates = false
        )
        assertThat(ekfCoords.size).isAtLeast(4) // At least 2 points (x,y pairs)
        // Verify points fall within canvas
        for (i in 0 until ekfCoords.size step 2) {
            assertThat(ekfCoords[i]).isAtLeast(0f)
            assertThat(ekfCoords[i]).isAtMost(400f)
            assertThat(ekfCoords[i + 1]).isAtLeast(0f)
            assertThat(ekfCoords[i + 1]).isAtMost(400f)
        }

        // 2. Raw Trace Path coordinates
        val rawCoords = MapProjectionMath.computeSimplifiedScreenCoords(
            trackPoints = dtos,
            centerLat = fit.centerLat,
            centerLon = fit.centerLon,
            scale = fit.scale,
            canvasWidth = 400f,
            canvasHeight = 400f,
            minDistancePx = 3f,
            useRawCoordinates = true
        )
        assertThat(rawCoords.size).isAtLeast(4)
    }

    @Test
    fun testPolarEquirectangularClampingStability() {
        // Extreme polar latitude near 89.9 degrees North
        val p = MapProjectionMath.projectToCanvas(
            lat = 89.9,
            lon = 0.0,
            centerLat = 89.9,
            centerLon = 0.0,
            scale = 1000f,
            canvasWidth = 400f,
            canvasHeight = 400f
        )
        assertThat(p.x).isWithin(0.01f).of(200f)
        assertThat(p.y).isWithin(0.01f).of(200f)
    }

    @Test
    fun testSouthernHemisphereProjection() {
        // Sydney, Australia (-33.8688, 151.2093)
        val centerLat = -33.8688
        val centerLon = 151.2093
        val p = MapProjectionMath.projectToCanvas(
            lat = centerLat,
            lon = centerLon,
            centerLat = centerLat,
            centerLon = centerLon,
            scale = 5000f,
            canvasWidth = 400f,
            canvasHeight = 400f
        )
        assertThat(p.x).isWithin(0.01f).of(200f)
        assertThat(p.y).isWithin(0.01f).of(200f)

        // Point to the north (towards equator, higher latitude algebraically e.g. -33.8588)
        val northPt = MapProjectionMath.projectToCanvas(
            lat = -33.8588,
            lon = centerLon,
            centerLat = centerLat,
            centerLon = centerLon,
            scale = 5000f,
            canvasWidth = 400f,
            canvasHeight = 400f
        )
        assertThat(northPt.y).isLessThan(200f)
    }

    @Test
    fun testZeroSpanIdenticalPointsBoundingBox() {
        val dtos = listOf(
            TrackPointDto(latitude = 51.5074, longitude = -0.1278, altitude = 20.0, timestamp = 1000L),
            TrackPointDto(latitude = 51.5074, longitude = -0.1278, altitude = 20.0, timestamp = 2000L),
            TrackPointDto(latitude = 51.5074, longitude = -0.1278, altitude = 20.0, timestamp = 3000L)
        )
        val fit = MapProjectionMath.computeAutoFitBoundsForPoints(
            trackPoints = dtos,
            canvasWidth = 300f,
            canvasHeight = 300f,
            minSpanDegrees = 0.001
        )
        assertThat(fit.scale).isGreaterThan(0f)
        assertThat(fit.scale.isFinite()).isTrue()
        assertThat(fit.centerLat).isWithin(1e-6).of(51.5074)
        assertThat(fit.centerLon).isWithin(1e-6).of(-0.1278)
    }

    @Test
    fun testComputeSimplifiedScreenCoordsStreaming() {
        val dtos = listOf(
            TrackPointDto(latitude = 37.7700, longitude = -122.4200, timestamp = 1000L),
            TrackPointDto(latitude = 37.7750, longitude = -122.4180, timestamp = 2000L),
            TrackPointDto(latitude = 37.7800, longitude = -122.4150, timestamp = 3000L),
            TrackPointDto(latitude = 37.7850, longitude = -122.4100, timestamp = 4000L)
        )
        val fit = MapProjectionMath.computeAutoFitBoundsForPoints(dtos, 400f, 400f)

        // EKF smoothed polyline coordinates
        val ekfCoords = MapProjectionMath.computeSimplifiedScreenCoords(
            trackPoints = dtos,
            centerLat = fit.centerLat,
            centerLon = fit.centerLon,
            scale = fit.scale,
            canvasWidth = 400f,
            canvasHeight = 400f,
            useRawCoordinates = false
        )
        assertThat(ekfCoords.size).isAtLeast(4)

        // Raw polyline coordinates
        val rawCoords = MapProjectionMath.computeSimplifiedScreenCoords(
            trackPoints = dtos,
            centerLat = fit.centerLat,
            centerLon = fit.centerLon,
            scale = fit.scale,
            canvasWidth = 400f,
            canvasHeight = 400f,
            useRawCoordinates = true
        )
        assertThat(rawCoords.size).isAtLeast(4)

        // Single point produces empty coordinates (no polyline to draw)
        val single = listOf(TrackPointDto(latitude = 37.7700, longitude = -122.4200, timestamp = 1000L))
        val singleCoords = MapProjectionMath.computeSimplifiedScreenCoords(
            trackPoints = single,
            centerLat = fit.centerLat,
            centerLon = fit.centerLon,
            scale = fit.scale,
            canvasWidth = 400f,
            canvasHeight = 400f
        )
        assertThat(singleCoords).isEmpty()
    }
}
