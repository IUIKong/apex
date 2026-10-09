package com.apex.tracker.core.fusion

import com.apex.tracker.core.math.Matrix
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MahalanobisGatingTest {

    private val gating = MahalanobisGating()

    @Test
    fun testBaseThresholdWhenStationaryOrStraight() {
        val gamma0 = gating.computeAdaptiveThreshold(0.0)
        assertThat(gamma0).isEqualTo(9.210)

        // Within deadband (< 0.25 rad/s)
        val gammaDeadband = gating.computeAdaptiveThreshold(0.20)
        assertThat(gammaDeadband).isEqualTo(9.210)
    }

    @Test
    fun testAdaptiveGateWideningDuringTurns() {
        // At max rate 2.0 rad/s (~114 deg/s)
        val gammaMax = gating.computeAdaptiveThreshold(2.0)
        val expectedMax = 9.210 * (1.0 + 3.5 * 1.0) // 9.210 * 4.5 = 41.445
        assertThat(gammaMax).isWithin(1e-4).of(expectedMax)

        // At intermediate rate 1.125 rad/s (halfway through span 0.25..2.0 -> ratio 0.5)
        val gammaMid = gating.computeAdaptiveThreshold(1.125)
        val expectedMid = 9.210 * (1.0 + 3.5 * 0.25) // 9.210 * 1.875 = 17.26875
        assertThat(gammaMid).isWithin(1e-4).of(expectedMid)
    }

    @Test
    fun testProcessNoiseBoostDuringTurns() {
        assertThat(gating.computeProcessNoiseBoost(0.1)).isEqualTo(1.0)
        assertThat(gating.computeProcessNoiseBoost(0.25)).isEqualTo(1.0)
        assertThat(gating.computeProcessNoiseBoost(2.0)).isWithin(1e-4).of(3.0)
    }

    @Test
    fun testRejectionOf50mMegamultipathSpike() {
        // P^- diag(4, 4), R diag(4, 4) -> S diag(8, 8)
        val S = Matrix.diag(8.0, 8.0)
        // 50m jump in East: innovation = [50.0, 0.0]
        val innovation = doubleArrayOf(50.0, 0.0)

        val decision = gating.evaluate(innovation, S, gyroYawRateRadPerSec = 0.0)
        // d_M^2 = 50^2 / 8 = 2500 / 8 = 312.5 >>> 9.210
        assertThat(decision.accepted).isFalse()
        assertThat(decision.squaredMahalanobisDistance).isWithin(0.1).of(312.5)
        assertThat(decision.failsafeReanchored).isFalse()
    }

    @Test
    fun testTurnPreservationWithGyroscopeYawRate() {
        // Suppose innovation = [12.0, 10.0] representing a sharp turn maneuver
        // S = diag(10.0, 10.0). d_M^2 = (144 + 100) / 10 = 24.4
        val S = Matrix.diag(10.0, 10.0)
        val innovation = doubleArrayOf(12.0, 10.0)

        // Scenario A: Straight line assumption (gyro = 0 rad/s)
        // gamma = 9.210 -> 24.4 > 9.210 -> falsely REJECTED!
        val decisionStraight = gating.evaluate(innovation, S, gyroYawRateRadPerSec = 0.0)
        assertThat(decisionStraight.accepted).isFalse()

        // Reset outlier counter for clean test
        gating.reset()

        // Scenario B: Gyro confirms sharp cornering (yaw rate = 1.6 rad/s)
        // gamma_adaptive should widen beyond 24.4 -> ACCEPTED!
        val decisionTurning = gating.evaluate(innovation, S, gyroYawRateRadPerSec = 1.6)
        assertThat(decisionTurning.accepted).isTrue()
        assertThat(decisionTurning.thresholdUsed).isGreaterThan(24.4)
    }

    @Test
    fun testFiveConsecutiveOutliersTriggerFailsafeReanchoring() {
        val S = Matrix.diag(4.0, 4.0)
        val spike = doubleArrayOf(40.0, 40.0)

        for (i in 1..4) {
            val d = gating.evaluate(spike, S, 0.0)
            assertThat(d.accepted).isFalse()
            assertThat(d.failsafeReanchored).isFalse()
            assertThat(gating.consecutiveOutliersCount).isEqualTo(i)
        }

        // 5th consecutive outlier must trigger soft re-anchoring
        val fifth = gating.evaluate(spike, S, 0.0)
        assertThat(fifth.accepted).isFalse()
        assertThat(fifth.failsafeReanchored).isTrue()
        assertThat(gating.consecutiveOutliersCount).isEqualTo(0) // reset after trigger
    }

    @Test
    fun testNaNAndInfiniteYawRateHandledSafely() {
        assertThat(gating.computeAdaptiveThreshold(Double.NaN)).isEqualTo(MahalanobisGating.BASE_THRESHOLD)
        assertThat(gating.computeAdaptiveThreshold(Double.POSITIVE_INFINITY)).isEqualTo(MahalanobisGating.BASE_THRESHOLD)
        assertThat(gating.computeProcessNoiseBoost(Double.NaN)).isEqualTo(1.0)
        assertThat(gating.computeProcessNoiseBoost(Double.POSITIVE_INFINITY)).isEqualTo(1.0)
    }

    @Test
    fun testNaNInnovationTreatedAsOutlier() {
        val S = Matrix.diag(4.0, 4.0)
        val nanInnovation = doubleArrayOf(Double.NaN, 0.0)
        val decision = gating.evaluate(nanInnovation, S, 0.0)
        assertThat(decision.accepted).isFalse()
    }
}
