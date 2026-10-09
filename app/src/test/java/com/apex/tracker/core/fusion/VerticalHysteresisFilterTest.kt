package com.apex.tracker.core.fusion

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.sin

class VerticalHysteresisFilterTest {

    @Test
    fun testIsaConversionAtSeaLevel() {
        val filter = VerticalHysteresisFilter()
        val altSeaLevel = filter.isaBarometricAltitude(1013.25f)
        assertThat(altSeaLevel).isWithin(0.01).of(0.0)
    }

    @Test
    fun testStationaryOscillationFor3600SecondsProducesStrictlyZeroGain() {
        val filter = VerticalHysteresisFilter()
        filter.initialize(100.0)

        // 3600 seconds of sinusoidal noise with 1.2m amplitude (below 1.5m deadband threshold)
        for (t in 0..3600) {
            val noiseAltitude = 100.0 + 1.2 * sin(t * 0.1)
            filter.updateAltitudeDirect(noiseAltitude)
        }

        // Must guarantee strictly 0.0m false climbing gain and loss
        assertThat(filter.elevationGain).isEqualTo(0.0)
        assertThat(filter.elevationLoss).isEqualTo(0.0)
        assertThat(filter.currentTrend).isEqualTo(VerticalHysteresisFilter.ElevationTrend.NEUTRAL)
    }

    @Test
    fun testClimbExceedingDeadbandAccruesExactGain() {
        val filter = VerticalHysteresisFilter()
        filter.initialize(100.0)

        // Step climb from 100.0m to 103.0m (+3.0m, exceeds 1.5m deadband)
        filter.updateAltitudeDirect(103.0)

        assertThat(filter.elevationGain).isEqualTo(3.0)
        assertThat(filter.elevationLoss).isEqualTo(0.0)
        assertThat(filter.currentTrend).isEqualTo(VerticalHysteresisFilter.ElevationTrend.CLIMBING)

        // Continuation of climb: 103.0m -> 105.5m (+2.5m)
        filter.updateAltitudeDirect(105.5)
        assertThat(filter.elevationGain).isEqualTo(5.5)
        assertThat(filter.elevationLoss).isEqualTo(0.0)
    }

    @Test
    fun testReversalRequiresFullDeadbandDrop() {
        val filter = VerticalHysteresisFilter()
        filter.initialize(100.0)

        // Climb to 110.0m (+10.0m gain)
        filter.updateAltitudeDirect(110.0)
        assertThat(filter.elevationGain).isEqualTo(10.0)
        assertThat(filter.currentTrend).isEqualTo(VerticalHysteresisFilter.ElevationTrend.CLIMBING)

        // Small dip of 1.0m (down to 109.0m, < 1.5m threshold): NO loss recorded
        filter.updateAltitudeDirect(109.0)
        assertThat(filter.elevationLoss).isEqualTo(0.0)
        assertThat(filter.currentTrend).isEqualTo(VerticalHysteresisFilter.ElevationTrend.CLIMBING)

        // Full drop to 108.0m (total 2.0m drop from peak 110.0m, >= 1.5m): Loss recorded and trend switches to DESCENDING!
        filter.updateAltitudeDirect(108.0)
        assertThat(filter.elevationLoss).isEqualTo(2.0)
        assertThat(filter.currentTrend).isEqualTo(VerticalHysteresisFilter.ElevationTrend.DESCENDING)
    }

    @Test
    fun testNaNAndInfiniteAltitudesHandledSafely() {
        val filter = VerticalHysteresisFilter()
        filter.initialize(100.0)
        filter.updateAltitudeDirect(Double.NaN)
        filter.updateAltitudeDirect(Double.POSITIVE_INFINITY)
        assertThat(filter.fusedAltitude).isEqualTo(100.0)
        assertThat(filter.elevationGain).isEqualTo(0.0)

        assertThat(filter.isaBarometricAltitude(Float.NaN)).isEqualTo(0.0)
        assertThat(filter.isaBarometricAltitude(-10f)).isEqualTo(0.0)
    }
}
