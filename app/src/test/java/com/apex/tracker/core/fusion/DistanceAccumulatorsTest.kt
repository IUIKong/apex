package com.apex.tracker.core.fusion

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DistanceAccumulatorsTest {

    @Test
    fun testTripleAccumulatorsInvariantUnderStationaryJitter() {
        val accumulators = DistanceAccumulators()

        val baseLat = 37.7749
        val baseLon = -122.4194

        accumulators.addRawLocation(baseLat, baseLon)
        accumulators.addFilteredPosition(0.0, 0.0)

        // Stationary jitter: GPS fixes wandering back and forth across 20 iterations
        val jitterOffsets = listOf(
            Pair(0.00003, 0.00002),
            Pair(-0.00002, 0.00004),
            Pair(0.00001, -0.00003),
            Pair(-0.00003, -0.00002)
        )

        for (i in 0 until 20) {
            val (dLat, dLon) = jitterOffsets[i % jitterOffsets.size]
            accumulators.addRawLocation(baseLat + dLat, baseLon + dLon)
            // Filtered positions have smaller variance
            accumulators.addFilteredPosition((i % 2) * 0.5, (i % 3) * 0.4)
            // Accepted distance remains 0 because user is stopped
        }

        assertThat(accumulators.rawDistanceMeters).isGreaterThan(50.0)
        assertThat(accumulators.filteredDistanceMeters).isLessThan(accumulators.rawDistanceMeters)
        assertThat(accumulators.acceptedDistanceMeters).isEqualTo(0.0)

        // Invariant holds
        assertThat(accumulators.acceptedDistanceMeters).isAtMost(accumulators.filteredDistanceMeters)
        assertThat(accumulators.filteredDistanceMeters).isAtMost(accumulators.rawDistanceMeters)
    }

    @Test
    fun testTripleAccumulatorsInvariantUnderActiveLocomotion() {
        val accumulators = DistanceAccumulators()

        val baseLat = 37.7749
        val baseLon = -122.4194

        accumulators.addRawLocation(baseLat, baseLon)
        accumulators.addFilteredPosition(0.0, 0.0)

        // Run 500m straight North
        for (i in 1..50) {
            val lat = baseLat + (i * 10.0 / 111319.5) // ~10m per step
            accumulators.addRawLocation(lat, baseLon)
            val dFilt = accumulators.addFilteredPosition(0.0, i * 10.0)
            accumulators.addAcceptedDistance(dFilt)
        }

        assertThat(accumulators.filteredDistanceMeters).isWithin(0.01).of(500.0)
        assertThat(accumulators.acceptedDistanceMeters).isWithin(0.01).of(500.0)
        assertThat(accumulators.rawDistanceMeters).isAtLeast(498.0)

        assertThat(accumulators.acceptedDistanceMeters).isAtMost(accumulators.filteredDistanceMeters)
    }

    @Test
    fun testReanchorPreservesAccumulatedDistanceWithoutDoubleCounting() {
        val accumulators = DistanceAccumulators()
        accumulators.addFilteredPosition(0.0, 0.0)

        // Move 100m North
        for (i in 1..10) {
            val d = accumulators.addFilteredPosition(0.0, i * 10.0)
            accumulators.addAcceptedDistance(d)
        }
        assertThat(accumulators.filteredDistanceMeters).isWithin(1e-4).of(100.0)
        assertThat(accumulators.acceptedDistanceMeters).isWithin(1e-4).of(100.0)

        // Discontinuous teleport 200m away (e.g. failsafe soft re-anchor after tunnel)
        accumulators.reanchorFilteredPosition(150.0, 250.0)

        // Filtered and accepted distance must be strictly preserved (zero phantom jump added!)
        assertThat(accumulators.filteredDistanceMeters).isWithin(1e-4).of(100.0)
        assertThat(accumulators.acceptedDistanceMeters).isWithin(1e-4).of(100.0)

        // Next legitimate step: 5m North from the new anchor (150.0, 255.0)
        val nextStep = accumulators.addFilteredPosition(150.0, 255.0)
        assertThat(nextStep).isWithin(1e-4).of(5.0)
        accumulators.addAcceptedDistance(nextStep)

        // Zero step loss: distance is exactly 105.0m
        assertThat(accumulators.filteredDistanceMeters).isWithin(1e-4).of(105.0)
        assertThat(accumulators.acceptedDistanceMeters).isWithin(1e-4).of(105.0)
    }
}
