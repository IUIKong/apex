package com.apex.tracker.core.fusion

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SplitTickerTest {

    @Test
    fun testRealTimeSplitProgressWithinKilometer() {
        val ticker = SplitTicker()

        // 450 meters into workout
        val progDist = ticker.currentSplitDistance(450.0)
        val progFrac = ticker.currentSplitProgressFraction(450.0)

        assertThat(progDist).isEqualTo(450.0)
        assertThat(progFrac).isWithin(1e-4f).of(0.45f)
    }

    @Test
    fun testCrossingKilometerBoundaryGeneratesSplits() {
        val ticker = SplitTicker()

        // Step 1: 950m (no split yet)
        val s0 = ticker.checkSplit(
            acceptedDistanceMeters = 950.0,
            elapsedTimeMs = 285_000L,
            movingTimeMs = 285_000L,
            elevationGainMeters = 10.0,
            elevationLossMeters = 2.0
        )
        assertThat(s0).isNull()

        // Step 2: Cross 1000m boundary (1050m)
        val s1 = ticker.checkSplit(
            acceptedDistanceMeters = 1050.0,
            elapsedTimeMs = 315_000L,
            movingTimeMs = 315_000L,
            elevationGainMeters = 12.0,
            elevationLossMeters = 2.0
        )
        assertThat(s1).isNotNull()
        assertThat(s1!!.splitIndex).isEqualTo(1)
        assertThat(s1.distanceMeters).isEqualTo(1000.0)
        assertThat(s1.movingTimeMs).isEqualTo(315_000L)
        assertThat(s1.paceSecondsPerKm).isEqualTo(315.0) // 5:15 /km
        assertThat(s1.elevationGainMeters).isEqualTo(12.0)

        // Step 3: Progress in 2nd km at 1400m
        assertThat(ticker.currentSplitDistance(1400.0)).isEqualTo(400.0)
        assertThat(ticker.currentSplitProgressFraction(1400.0)).isWithin(1e-4f).of(0.40f)

        // Step 4: Cross 2000m boundary (2020m)
        val s2 = ticker.checkSplit(
            acceptedDistanceMeters = 2020.0,
            elapsedTimeMs = 600_000L,
            movingTimeMs = 600_000L,
            elevationGainMeters = 25.0,
            elevationLossMeters = 5.0
        )
        assertThat(s2).isNotNull()
        assertThat(s2!!.splitIndex).isEqualTo(2)
        assertThat(s2.distanceMeters).isEqualTo(1000.0)
        assertThat(s2.movingTimeMs).isEqualTo(285_000L) // 600s - 315s = 285s
        assertThat(s2.paceSecondsPerKm).isEqualTo(285.0) // 4:45 /km
        assertThat(s2.elevationGainMeters).isEqualTo(13.0) // 25 - 12 = 13m
    }

    @Test
    fun testPaceDeltaVsAverageCalculation() {
        val ticker = SplitTicker()
        // Current pace 300s (5:00/km), average 315s (5:15/km) -> -15s (faster)
        val deltaFaster = ticker.paceDeltaVsAverage(300.0, 315.0)
        assertThat(deltaFaster).isEqualTo(-15.0)

        // Current pace 330s (5:30/km), average 315s -> +15s (slower)
        val deltaSlower = ticker.paceDeltaVsAverage(330.0, 315.0)
        assertThat(deltaSlower).isEqualTo(15.0)
    }

    @Test
    fun testNaNAndInfiniteInputsHandledSafely() {
        val ticker = SplitTicker()
        assertThat(ticker.checkSplit(Double.NaN, 1000L, 1000L, 0.0, 0.0)).isNull()
        assertThat(ticker.checkSplit(Double.POSITIVE_INFINITY, 1000L, 1000L, 0.0, 0.0)).isNull()
        assertThat(ticker.currentSplitDistance(Double.NaN)).isEqualTo(0.0)
        assertThat(ticker.currentSplitProgressFraction(Double.NaN)).isEqualTo(0.0f)
        assertThat(ticker.paceDeltaVsAverage(Double.NaN, 300.0)).isEqualTo(0.0)
    }
}
