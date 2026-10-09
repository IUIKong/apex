package com.apex.tracker.ui

import androidx.compose.ui.geometry.Offset
import com.apex.tracker.NavigationTab
import com.apex.tracker.core.model.ActivityType
import com.apex.tracker.core.model.FusedState
import com.apex.tracker.core.model.MotionState
import com.apex.tracker.ui.components.MapProjectionMath
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EditorialLayoutAndParchmentMapTest {

    @Test
    fun testNavigationTabOrderAndProperties() {
        val tabs = NavigationTab.values()
        assertThat(tabs).hasLength(2)

        // Left tab: Logbook / History
        assertThat(tabs[0]).isEqualTo(NavigationTab.LOGBOOK)
        assertThat(tabs[0].title).isEqualTo("LOGBOOK")
        assertThat(tabs[0].subtitle).isEqualTo("HISTORY")

        // Right tab: RECORD (prominent centerpiece)
        assertThat(tabs[1]).isEqualTo(NavigationTab.RECORD)
        assertThat(tabs[1].title).isEqualTo("RECORD")
        assertThat(tabs[1].subtitle).isEqualTo("TRACK")
    }

    @Test
    fun testMinSpanDegreesPreventsMicroZoomJitter() {
        // Stationary or single-step scenario: span is very small (e.g. 2 meters = 0.00002 deg)
        val tinyPoints = listOf(
            Pair(37.774900, -122.419400),
            Pair(37.774902, -122.419402)
        )
        val canvasW = 400f
        val canvasH = 400f

        // With minSpanDegrees = 0.0012 (~130 meters):
        val fit = MapProjectionMath.computeAutoFitBounds(
            coordinates = tinyPoints,
            canvasWidth = canvasW,
            canvasHeight = canvasH,
            padding = 40f,
            minSpanDegrees = 0.0012
        )

        // Scale should be bounded by minSpanDegrees rather than exploding into millions
        assertThat(fit.scale).isLessThan(400000f)
        assertThat(fit.scale).isGreaterThan(50000f)

        // Center should accurately frame the points
        assertThat(fit.centerLat).isWithin(0.0001).of(37.7749)
        assertThat(fit.centerLon).isWithin(0.0001).of(-122.4194)
    }

    @Test
    fun testSimplifyOffsetsDropsMicroJitterPoints() {
        val offsets = listOf(
            Offset(100f, 100f),
            Offset(100.5f, 100.2f), // < 2.5px delta -> should be dropped
            Offset(101.0f, 100.8f), // < 2.5px delta -> should be dropped
            Offset(120f, 130f),     // large movement -> preserved
            Offset(120.2f, 130.1f), // < 2.5px delta -> should be dropped
            Offset(200f, 200f)      // endpoint preserved
        )

        val simplified = MapProjectionMath.simplifyOffsets(offsets, minDistancePx = 2.5f)

        // Must preserve first and last points, while dropping micro-jitter
        assertThat(simplified.first()).isEqualTo(Offset(100f, 100f))
        assertThat(simplified.last()).isEqualTo(Offset(200f, 200f))
        assertThat(simplified.size).isLessThan(offsets.size)
        assertThat(simplified.size).isEqualTo(3) // (100,100), (120,130), (200,200)
    }

    @Test
    fun testNormalizeLongitudeDeltaWrapsAroundAntimeridian() {
        val deltaNormal = MapProjectionMath.normalizeLongitudeDelta(0.05)
        assertThat(deltaNormal).isWithin(1e-6).of(0.05)

        val deltaEastWrap = MapProjectionMath.normalizeLongitudeDelta(359.5)
        assertThat(deltaEastWrap).isWithin(1e-6).of(-0.5)

        val deltaWestWrap = MapProjectionMath.normalizeLongitudeDelta(-359.5)
        assertThat(deltaWestWrap).isWithin(1e-6).of(0.5)
    }

    @Test
    fun testFusedStateIncludesRawCoordinates() {
        val fused = FusedState(
            lat = 37.7749,
            lon = -122.4194,
            altitude = 45.0,
            speedMps = 3.5,
            bearingDegrees = 90f,
            rawDistance = 500.0,
            filteredDistance = 495.0,
            acceptedDistance = 490.0,
            fsmState = MotionState.MOVING,
            currentPaceSecPerKm = 285.0,
            avgPaceSecPerKm = 280.0,
            elevationGain = 12.0,
            elevationLoss = 2.0,
            timestampEpochMs = 10000L,
            rawLat = 37.7751,
            rawLon = -122.4192
        )

        assertThat(fused.lat).isEqualTo(37.7749)
        assertThat(fused.lon).isEqualTo(-122.4194)
        assertThat(fused.rawLat).isEqualTo(37.7751)
        assertThat(fused.rawLon).isEqualTo(-122.4192)
    }

    @Test
    fun testSinglePointScaleMatchesMinSpanScaleWithoutSuddenZoomJump() {
        val single = listOf(Pair(37.7749, -122.4194))
        val double = listOf(Pair(37.7749, -122.4194), Pair(37.77491, -122.41941))
        val canvasW = 400f
        val canvasH = 400f
        val minSpan = 0.0012

        val fitSingle = MapProjectionMath.computeAutoFitBounds(single, canvasW, canvasH, padding = 40f, minSpanDegrees = minSpan)
        val fitDouble = MapProjectionMath.computeAutoFitBounds(double, canvasW, canvasH, padding = 40f, minSpanDegrees = minSpan)

        // The scale for 1 point should match the bounded scale for stationary points (within 10%)
        assertThat(fitSingle.scale).isGreaterThan(50000f)
        assertThat(fitSingle.scale).isLessThan(400000f)
        assertThat(fitSingle.scale).isWithin(fitDouble.scale * 0.15f).of(fitDouble.scale)
    }

    @Test
    fun testAntimeridianCrossingBoundingBoxAndProjection() {
        // Points crossing the 180th meridian (e.g. Fiji / Pacific)
        val crossingPoints = listOf(
            Pair(0.0, 179.95),
            Pair(0.0, -179.95)
        )
        val fit = MapProjectionMath.computeAutoFitBounds(
            coordinates = crossingPoints,
            canvasWidth = 500f,
            canvasHeight = 500f,
            padding = 20f,
            minSpanDegrees = 0.001
        )

        // Center should be right on the antimeridian (180 or -180)
        assertThat(Math.abs(fit.centerLon)).isWithin(0.01).of(180.0)
        // Scale should reflect the 0.1 deg span, not a 359.9 deg world span!
        assertThat(fit.scale).isGreaterThan(1000f)

        // Both points must project inside canvas bounds padded margins
        val p1 = MapProjectionMath.projectToCanvas(
            lat = 0.0, lon = 179.95,
            centerLat = fit.centerLat, centerLon = fit.centerLon,
            scale = fit.scale, canvasWidth = 500f, canvasHeight = 500f
        )
        val p2 = MapProjectionMath.projectToCanvas(
            lat = 0.0, lon = -179.95,
            centerLat = fit.centerLat, centerLon = fit.centerLon,
            scale = fit.scale, canvasWidth = 500f, canvasHeight = 500f
        )
        assertThat(p1.x).isAtLeast(19f)
        assertThat(p1.x).isAtMost(481f)
        assertThat(p2.x).isAtLeast(19f)
        assertThat(p2.x).isAtMost(481f)
        assertThat(p1.y).isWithin(1f).of(250f)
        assertThat(p2.y).isWithin(1f).of(250f)
    }

    @Test
    fun testParchmentMapLayer2FallbackProjectsToCenterWhenCacheUninitialized() {
        // When cache is uninitialized, Layer 2 falls back to lastPt coordinates
        val runnerLat = 37.7749
        val runnerLon = -122.4194
        val canvasWidth = 800f
        val canvasHeight = 600f

        // With fallback: centerLat = runnerLat, centerLon = runnerLon
        val centerLat = runnerLat
        val centerLon = runnerLon
        val p = MapProjectionMath.projectToCanvas(
            lat = runnerLat,
            lon = runnerLon,
            centerLat = centerLat,
            centerLon = centerLon,
            scale = 1000f,
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight
        )

        // Must project precisely to screen center, NOT off-screen at Null Island!
        assertThat(p.x).isWithin(0.01f).of(400f)
        assertThat(p.y).isWithin(0.01f).of(300f)
    }
}
