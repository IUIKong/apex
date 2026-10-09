package com.apex.tracker.core.math

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.abs

class EnuProjectionTest {

    private val originLat = 37.7749 // San Francisco
    private val originLon = -122.4194

    @Test
    fun testOriginMapsToZero() {
        val (pe, pn) = EnuProjection.forward(originLat, originLon, originLat, originLon)
        assertThat(abs(pe)).isLessThan(1e-9)
        assertThat(abs(pn)).isLessThan(1e-9)

        val (latRev, lonRev) = EnuProjection.inverse(0.0, 0.0, originLat, originLon)
        assertThat(abs(latRev - originLat)).isLessThan(1e-9)
        assertThat(abs(lonRev - originLon)).isLessThan(1e-9)
    }

    @Test
    fun testForwardAndInverseRoundTripPreservesCoordinates() {
        // Test offsets in all four quadrants
        val testOffsets = listOf(
            Pair(100.0, 250.0),
            Pair(-500.0, 300.0),
            Pair(-1200.0, -800.0),
            Pair(450.0, -950.0)
        )

        for ((targetEast, targetNorth) in testOffsets) {
            val (lat, lon) = EnuProjection.inverse(targetEast, targetNorth, originLat, originLon)
            val (pe, pn) = EnuProjection.forward(lat, lon, originLat, originLon)

            assertThat(abs(pe - targetEast)).isLessThan(1e-4) // < 0.1 mm error
            assertThat(abs(pn - targetNorth)).isLessThan(1e-4)
        }
    }

    @Test
    fun testPureNorthDisplacement() {
        // Move 1000m North
        val (lat1kmNorth, lon1kmNorth) = EnuProjection.inverse(0.0, 1000.0, originLat, originLon)
        val (pe, pn) = EnuProjection.forward(lat1kmNorth, lon1kmNorth, originLat, originLon)

        assertThat(pe).isWithin(0.01).of(0.0)
        assertThat(pn).isWithin(0.01).of(1000.0)

        // Haversine should also be approximately 1000m (ellipsoid vs mean sphere tolerance ~3m)
        val havDist = EnuProjection.haversineDistance(originLat, originLon, lat1kmNorth, lon1kmNorth)
        assertThat(havDist).isWithin(3.0).of(1000.0)
    }

    @Test
    fun testPureEastDisplacement() {
        // Move 1000m East
        val (lat1kmEast, lon1kmEast) = EnuProjection.inverse(1000.0, 0.0, originLat, originLon)
        val (pe, pn) = EnuProjection.forward(lat1kmEast, lon1kmEast, originLat, originLon)

        assertThat(pe).isWithin(0.01).of(1000.0)
        assertThat(pn).isWithin(0.01).of(0.0)

        val havDist = EnuProjection.haversineDistance(originLat, originLon, lat1kmEast, lon1kmEast)
        assertThat(havDist).isWithin(3.0).of(1000.0)
    }

    @Test
    fun testHaversineDistanceZeroForSamePoint() {
        val dist = EnuProjection.haversineDistance(originLat, originLon, originLat, originLon)
        assertThat(dist).isEqualTo(0.0)
    }

    @Test
    fun testEllipsoidalDistanceCalculation() {
        // Move 1000m North
        val (lat1kmNorth, lon1kmNorth) = EnuProjection.inverse(0.0, 1000.0, originLat, originLon)
        val ellipDist = EnuProjection.ellipsoidalDistance(originLat, originLon, lat1kmNorth, lon1kmNorth)
        // WGS-84 ellipsoidal distance should match 1000.0m within 0.1m
        assertThat(ellipDist).isWithin(0.1).of(1000.0)

        // Same point should return exactly 0.0
        assertThat(EnuProjection.ellipsoidalDistance(originLat, originLon, originLat, originLon)).isEqualTo(0.0)
    }

    @Test
    fun testPolarSingularitySafety() {
        // North Pole (+90.0°)
        val (pe, pn) = EnuProjection.forward(90.0, 0.0, 90.0, 0.0)
        assertThat(pe.isNaN()).isFalse()
        assertThat(pn.isNaN()).isFalse()

        // Inverse near North Pole
        val (latRev, lonRev) = EnuProjection.inverse(10.0, -10.0, 90.0, 0.0)
        assertThat(latRev.isNaN()).isFalse()
        assertThat(lonRev.isNaN()).isFalse()
        assertThat(latRev).isAtMost(90.0)
    }

    @Test
    fun testNaNAndInfiniteSafeguards() {
        val fwd = EnuProjection.forward(Double.NaN, originLon, originLat, originLon)
        assertThat(fwd.first.isNaN()).isFalse()
        assertThat(fwd.second.isNaN()).isFalse()

        val inv = EnuProjection.inverse(Double.POSITIVE_INFINITY, 0.0, originLat, originLon)
        assertThat(inv.first.isNaN()).isFalse()
        assertThat(inv.second.isNaN()).isFalse()
    }
}
