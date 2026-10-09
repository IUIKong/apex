package com.apex.tracker.core.math

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.abs

class HaversineTest {

    private val originLat = 37.7749
    private val originLon = -122.4194

    @Test
    fun testIdenticalCoordinatesReturnStrictlyZero() {
        val dist = Haversine.distance(originLat, originLon, originLat, originLon)
        assertThat(dist).isEqualTo(0.0)
    }

    @Test
    fun testHighFrequencySmallStepOneMeterUpdateHasZeroNumericalAttenuation() {
        // High frequency (1Hz) small step update: 1.0 meter North
        // 1 meter North in degrees latitude ~ 1.0 / 111139.0 ~ 8.9977e-6 deg
        val dLat = 1.0 / 111139.0
        val targetLat = originLat + dLat

        val dist = Haversine.distance(originLat, originLon, targetLat, originLon)
        // Should measure within 1 millimeter of 1.000m
        assertThat(dist).isWithin(0.005).of(1.0)
        assertThat(dist.isNaN()).isFalse()
        assertThat(dist).isGreaterThan(0.0)
    }

    @Test
    fun testHighFrequencyCentimeterStepPrecision() {
        // High frequency micro step: 10 cm (0.10 m)
        val dLat = 0.10 / 111139.0
        val targetLat = originLat + dLat

        val dist = Haversine.distance(originLat, originLon, targetLat, originLon)
        assertThat(dist).isWithin(0.002).of(0.10)
        assertThat(dist.isNaN()).isFalse()
    }

    @Test
    fun testAntimeridianCrossingContinuity() {
        // Cross 180th meridian from +179.999 to -179.999 along equator (0.002 deg ~ 222.39m)
        val dist = Haversine.distance(0.0, 179.999, 0.0, -179.999)
        assertThat(dist).isWithin(1.0).of(222.39)
        assertThat(dist.isNaN()).isFalse()
    }

    @Test
    fun testAntipodalStabilityWithoutNan() {
        // Exact opposite sides of Earth
        val dist = Haversine.distance(0.0, 0.0, 0.0, 180.0)
        assertThat(dist.isNaN()).isFalse()
        // Half circumference: PI * R = PI * 6371000 ~ 20,015,086 m
        assertThat(dist).isWithin(100.0).of(Math.PI * Haversine.EARTH_MEAN_RADIUS)
    }

    @Test
    fun testNaNAndInvalidCoordinatesFallbackGracefully() {
        assertThat(Haversine.distance(Double.NaN, originLon, originLat, originLon)).isEqualTo(0.0)
        assertThat(Haversine.distance(originLat, Double.NaN, originLat, originLon)).isEqualTo(0.0)
        assertThat(Haversine.distance(originLat, originLon, Double.NaN, originLon)).isEqualTo(0.0)
        assertThat(Haversine.distance(originLat, originLon, originLat, Double.NaN)).isEqualTo(0.0)
        assertThat(Haversine.distance(Double.POSITIVE_INFINITY, originLon, originLat, originLon)).isEqualTo(0.0)
        assertThat(Haversine.distance(originLat, originLon, Double.NEGATIVE_INFINITY, originLon)).isEqualTo(0.0)
    }

    @Test
    fun testNorthAndSouthPoleIdenticalPointsWithDifferentLongitudes() {
        // Both points at North Pole (+90°): distance should be exactly 0.0 regardless of longitude
        val northPoleDist = Haversine.distance(90.0, 0.0, 90.0, 120.0)
        assertThat(northPoleDist).isEqualTo(0.0)

        // Both points at South Pole (-90°): distance should be exactly 0.0
        val southPoleDist = Haversine.distance(-90.0, -45.0, -90.0, 85.0)
        assertThat(southPoleDist).isEqualTo(0.0)
    }

    @Test
    fun testClampingOfOutRangeLatitudesDoesNotCorruptCalculation() {
        // Corrupted GPS fix with latitude 95.0 should be clamped to 90.0 rather than causing cosine inversion
        val dist = Haversine.distance(95.0, 0.0, 90.0, 0.0)
        assertThat(dist).isEqualTo(0.0)
    }
}
