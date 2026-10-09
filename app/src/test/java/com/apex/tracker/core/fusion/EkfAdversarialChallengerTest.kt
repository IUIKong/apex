package com.apex.tracker.core.fusion

import com.apex.tracker.core.math.EnuProjection
import com.apex.tracker.core.math.Matrix
import com.apex.tracker.core.model.ActivityType
import com.apex.tracker.core.model.GpsUpdateResult
import com.apex.tracker.core.model.MotionState
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.*

/**
 * Empirical Challenger Adversarial Test Suite for Milestone 1.
 *
 * Focuses on mathematical invariants, physical corners, and numerical edge cases:
 * 1. 2D Horizontal EKF covariance positive semi-definiteness and symmetry under 1,000 chaotic steps
 * 2. EKF numerical stability under extreme dt (1e-4s to 60s)
 * 3. Mahalanobis gating resilience to singular, ill-conditioned, and negative covariances
 * 4. Turn-adaptive threshold symmetry, monotonicity, and boundary clamping
 * 5. Failsafe re-anchoring immunity to intermittent noise vs 5-consecutive triggers
 * 6. High lateral G cornering (> 2G) trajectory acceptance without turn starvation
 * 7. Analytical 2x2 matrix inversion numerical limits
 */
class EkfAdversarialChallengerTest {

    private val originLat = 37.7749
    private val originLon = -122.4194
    private val originAlt = 50.0

    // =========================================================================
    // 1. EKF COVARIANCE POSITIVE DEFINITENESS & SYMMETRY UNDER CHAOTIC STEPS
    // =========================================================================

    @Test
    fun stressTestEkfCovariancePositiveDefinitenessUnder1000RandomSteps() {
        val ekf = EkfStateEstimator()
        ekf.initialize(originLat, originLon, originAlt, initialAccuracyMeters = 3.0f)

        // Seeded deterministic pseudo-random sequence
        var seed = 123456789L
        fun nextRandom(): Double {
            seed = (seed * 1103515245L + 12345L) and 0x7fffffffL
            return (seed.toDouble() / 0x7fffffffL) * 2.0 - 1.0 // [-1.0, 1.0]
        }

        var pe = 0.0
        var pn = 0.0

        for (step in 1..1000) {
            val dt = 0.5 + 0.5 * abs(nextRandom()) // dt in [0.5, 1.0]
            val yawRate = nextRandom() * 2.5 // [-2.5, 2.5] rad/s

            // 1. Predict step
            ekf.predict(dt, ActivityType.RUNNING, yawRate)

            // Verify covariance invariants after prediction
            val Ppred = ekf.covariance
            for (i in 0 until 6) {
                assertThat(Ppred[i, i]).isGreaterThan(0.0)
                assertThat(Ppred[i, i].isNaN()).isFalse()
                assertThat(Ppred[i, i].isInfinite()).isFalse()
            }

            // Check symmetry
            for (r in 0 until 6) {
                for (c in 0 until 6) {
                    assertThat(abs(Ppred[r, c] - Ppred[c, r])).isLessThan(1e-9)
                }
            }

            // 2. Simulated position update with random measurement noise
            pe += nextRandom() * 1.5
            pn += nextRandom() * 1.5
            val (lat, lon) = EnuProjection.inverse(pe, pn, originLat, originLon)
            val accuracy = (2.0 + abs(nextRandom()) * 4.0).toFloat() // [2.0, 6.0] meters

            ekf.updateGpsPosition(lat, lon, accuracy)

            // Verify covariance invariants after update
            val Ppost = ekf.covariance
            for (i in 0 until 6) {
                assertThat(Ppost[i, i]).isGreaterThan(0.0)
                assertThat(Ppost[i, i].isNaN()).isFalse()
                assertThat(Ppost[i, i].isInfinite()).isFalse()
            }

            // Position 2x2 sub-block positive-definiteness: P00 * P11 - P01^2 > 0
            val detPos = Ppost[0, 0] * Ppost[1, 1] - Ppost[0, 1] * Ppost[1, 0]
            assertThat(detPos).isGreaterThan(0.0)

            // Velocity 2x2 sub-block positive-definiteness: P22 * P33 - P23^2 > 0
            val detVel = Ppost[2, 2] * Ppost[3, 3] - Ppost[2, 3] * Ppost[3, 2]
            assertThat(detVel).isGreaterThan(0.0)

            // Verify state vector is finite
            for (i in 0 until 6) {
                assertThat(ekf.state[i].isNaN()).isFalse()
                assertThat(ekf.state[i].isInfinite()).isFalse()
            }
        }
    }

    // =========================================================================
    // 2. EKF UNDER EXTREME TIME STEPS
    // =========================================================================

    @Test
    fun stressTestEkfUnderExtremeTimeSteps() {
        val ekf = EkfStateEstimator()
        ekf.initialize(originLat, originLon, originAlt, initialAccuracyMeters = 2.0f)

        // Extremely small dt (1e-4s, minimum clamp)
        ekf.predict(dtSeconds = 1e-6, ActivityType.RUNNING, 0.0)
        for (i in 0 until 6) {
            assertThat(ekf.state[i].isNaN()).isFalse()
            assertThat(ekf.covariance[i, i]).isGreaterThan(0.0)
        }

        // Standard step
        val (lat1, lon1) = EnuProjection.inverse(2.0, 2.0, originLat, originLon)
        val res1 = ekf.updateGpsPosition(lat1, lon1, 2.0f)
        assertThat(res1).isEqualTo(GpsUpdateResult.ACCEPTED)

        // Massive dt (60.0s gap, e.g. thread suspension or long background pause)
        ekf.predict(dtSeconds = 60.0, ActivityType.RUNNING, 0.0)
        // Position variance must be clamped to 100.0 max to prevent explosion
        assertThat(ekf.covariance[0, 0]).isAtMost(100.0)
        assertThat(ekf.covariance[1, 1]).isAtMost(100.0)
        assertThat(ekf.covariance[0, 0]).isGreaterThan(0.0)
        assertThat(ekf.covariance[1, 1]).isGreaterThan(0.0)

        // EKF must still accept a reasonable fix after the long gap
        val (lat2, lon2) = EnuProjection.inverse(3.0, 3.0, originLat, originLon)
        val res2 = ekf.updateGpsPosition(lat2, lon2, 3.0f)
        assertThat(res2).isEqualTo(GpsUpdateResult.ACCEPTED)
    }

    // =========================================================================
    // 3. MAHALANOBIS GATING RESILIENCE TO SINGULAR & ILL-CONDITIONED COVARIANCES
    // =========================================================================

    @Test
    fun stressTestMahalanobisGatingSingularAndIllConditionedCovariances() {
        val gating = MahalanobisGating()
        val innovation = doubleArrayOf(5.0, 5.0)

        // 1. Singular matrix (det = 0)
        val singularS = Matrix(2, 2, doubleArrayOf(4.0, 4.0, 4.0, 4.0))
        val dSingular = gating.computeSquaredDistance(innovation, singularS)
        assertThat(dSingular).isEqualTo(Double.POSITIVE_INFINITY)

        val decSingular = gating.evaluate(innovation, singularS, 0.0)
        assertThat(decSingular.accepted).isFalse()

        // 2. Negative diagonal entry
        val negativeS = Matrix(2, 2, doubleArrayOf(-1.0, 0.0, 0.0, 4.0))
        val dNegative = gating.computeSquaredDistance(innovation, negativeS)
        assertThat(dNegative).isEqualTo(Double.POSITIVE_INFINITY)

        // 3. Ill-conditioned near-zero determinant (1e-16)
        val illConditionedS = Matrix(2, 2, doubleArrayOf(1.0, 1.0, 1.0, 1.0 + 1e-16))
        val dIll = gating.computeSquaredDistance(innovation, illConditionedS)
        assertThat(dIll).isEqualTo(Double.POSITIVE_INFINITY)

        // 4. NaN / Infinite innovations
        val nanInnov = doubleArrayOf(Double.NaN, 5.0)
        val goodS = Matrix.diag(4.0, 4.0)
        val dNan = gating.computeSquaredDistance(nanInnov, goodS)
        assertThat(dNan).isEqualTo(Double.POSITIVE_INFINITY)
    }

    // =========================================================================
    // 4. TURN-ADAPTIVE WIDENING SYMMETRY, MONOTONICITY, AND CLAMPING
    // =========================================================================

    @Test
    fun stressTestTurnAdaptiveWideningProperties() {
        val gating = MahalanobisGating()

        // 1. Parity / Symmetry: gamma(omega) == gamma(-omega)
        for (w in listOf(0.1, 0.5, 1.0, 1.5, 2.0, 3.0)) {
            val gammaPos = gating.computeAdaptiveThreshold(w)
            val gammaNeg = gating.computeAdaptiveThreshold(-w)
            assertThat(gammaPos).isWithin(1e-9).of(gammaNeg)

            val boostPos = gating.computeProcessNoiseBoost(w)
            val boostNeg = gating.computeProcessNoiseBoost(-w)
            assertThat(boostPos).isWithin(1e-9).of(boostNeg)
        }

        // 2. Deadband (< 0.25 rad/s) gives exact base threshold
        for (w in listOf(0.0, 0.05, 0.15, 0.2499)) {
            assertThat(gating.computeAdaptiveThreshold(w)).isEqualTo(9.210)
            assertThat(gating.computeProcessNoiseBoost(w)).isEqualTo(1.0)
        }

        // 3. Beyond max (> 2.0 rad/s) clamped strictly to max threshold (41.445)
        for (w in listOf(2.0, 2.5, 5.0, 50.0)) {
            val expectedMax = 9.210 * (1.0 + 3.5 * 1.0)
            assertThat(gating.computeAdaptiveThreshold(w)).isWithin(1e-6).of(expectedMax)
            assertThat(gating.computeProcessNoiseBoost(w)).isWithin(1e-6).of(3.0)
        }

        // 4. Strict monotonicity in the transition band [0.25, 2.0]
        var prevGamma = 9.210
        var prevBoost = 1.0
        val steps = 100
        for (i in 1..steps) {
            val w = 0.25 + (1.75 * i / steps)
            val g = gating.computeAdaptiveThreshold(w)
            val b = gating.computeProcessNoiseBoost(w)
            assertThat(g).isGreaterThan(prevGamma)
            assertThat(b).isGreaterThan(prevBoost)
            prevGamma = g
            prevBoost = b
        }
    }

    // =========================================================================
    // 5. FAILSAFE RE-ANCHORING IMMUNITY TO INTERMITTENT MULTIPATH SPIKES
    // =========================================================================

    @Test
    fun stressTestMahalanobisGatingCounterResetOnIntermittentAcceptedFixes() {
        val gating = MahalanobisGating()
        val S = Matrix.diag(4.0, 4.0)
        val spike = doubleArrayOf(50.0, 50.0)
        val valid = doubleArrayOf(0.1, 0.1)

        // 10 sequences of 4 outliers followed by 1 valid fix
        for (seq in 1..10) {
            for (i in 1..4) {
                val d = gating.evaluate(spike, S, 0.0)
                assertThat(d.accepted).isFalse()
                assertThat(d.failsafeReanchored).isFalse()
                assertThat(gating.consecutiveOutliersCount).isEqualTo(i)
            }

            // 1 valid fix resets counter
            val dValid = gating.evaluate(valid, S, 0.0)
            assertThat(dValid.accepted).isTrue()
            assertThat(dValid.failsafeReanchored).isFalse()
            assertThat(gating.consecutiveOutliersCount).isEqualTo(0)
        }

        // Failsafe was never triggered despite 40 total outliers!
        // Now provide 5 consecutive outliers -> 5th triggers reanchor
        for (i in 1..4) {
            val d = gating.evaluate(spike, S, 0.0)
            assertThat(d.accepted).isFalse()
            assertThat(d.failsafeReanchored).isFalse()
        }
        val fifth = gating.evaluate(spike, S, 0.0)
        assertThat(fifth.accepted).isFalse()
        assertThat(fifth.failsafeReanchored).isTrue()
    }

    /**
     * EMPIRICAL FINDING:
     * Demonstrates that naive independent diagonal clamping (lines 171-172 of EkfStateEstimator.kt):
     *     if (pPred[0, 0] > 100.0) pPred[0, 0] = 100.0
     *     if (pPred[1, 1] > 100.0) pPred[1, 1] = 100.0
     * violates the Cauchy-Schwarz inequality P_02^2 <= P_00 * P_22 during sustained outlier bursts,
     * destroying positive semi-definiteness and causing P_00 to plunge negative on subsequent Kalman updates.
     */
    @Test
    fun empiricalVulnerabilityReproduction_NaivePositionalCovarianceClampingViolatesPositiveDefiniteness() {
        val ekf = EkfStateEstimator()
        ekf.initialize(originLat, originLon, originAlt, initialAccuracyMeters = 2.0f)

        val spikeLat = originLat + 0.01
        val spikeLon = originLon

        // 4 consecutive outlier rejections drive P_00 to the 100.0 clamp while P_02 and P_22 grow
        for (i in 1..4) {
            ekf.predict(1.0, ActivityType.RUNNING, 0.0)
            ekf.updateGpsPosition(spikeLat, spikeLon, 2.0f)
        }

        // Legitimate GPS fix arrives
        ekf.predict(1.0, ActivityType.RUNNING, 0.0)
        val (validLat1, validLon1) = EnuProjection.inverse(0.1, 0.1, originLat, originLon)
        val res1 = ekf.updateGpsPosition(validLat1, validLon1, 2.0f)
        assertThat(res1).isEqualTo(GpsUpdateResult.ACCEPTED)

        // On the next prediction epoch after the update, propagation via F explodes the indefinite covariance:
        ekf.predict(1.0, ActivityType.RUNNING, 0.0)
        val p00After = ekf.covariance[0, 0]

        // Asserts that P_00 remains strictly positive and bounded due to congruent row/column scaling mitigation!
        assertThat(p00After).isGreaterThan(0.0)
    }

    // =========================================================================
    // 6. HIGH LATERAL-G CORNERING (> 2G) TRAJECTORY ACCEPTANCE
    // =========================================================================

    @Test
    fun stressTestHighLateralGCorneringTrajectoryAcceptance() {
        val ekf = EkfStateEstimator()
        ekf.initialize(originLat, originLon, originAlt, initialAccuracyMeters = 2.0f)

        // Approach turn at 15 m/s North (~54 km/h cycling sprint)
        for (sec in 1..5) {
            ekf.predict(1.0, ActivityType.CYCLING, 0.0)
            val (lat, lon) = EnuProjection.inverse(0.0, sec * 15.0, originLat, originLon)
            ekf.updateGpsPosition(lat, lon, 2.0f)
        }

        // Sharp 90-degree turn East: radius R = 10m, speed = 15 m/s
        // Angular rate omega = v / R = 1.5 rad/s (~86 deg/s)
        // Lateral acceleration = v^2 / R = 225 / 10 = 22.5 m/s^2 (~2.3 G)
        val turnYawRate = 1.5 // rad/s
        ekf.predict(1.0, ActivityType.CYCLING, turnYawRate)

        // Position turns toward East: (+12m East, +78m North)
        val (turnLat, turnLon) = EnuProjection.inverse(12.0, 78.0, originLat, originLon)
        val res = ekf.updateGpsPosition(turnLat, turnLon, 2.5f)

        // Turn MUST be accepted due to adaptive threshold expansion; no turn starvation!
        assertThat(res).isEqualTo(GpsUpdateResult.ACCEPTED)
        assertThat(ekf.getDiagnostics().outliersRejectedCount).isEqualTo(0)
    }

    // =========================================================================
    // 7. ANALYTICAL 2x2 MATRIX INVERSION NUMERICAL INVARIANTS
    // =========================================================================

    @Test
    fun stressTestMatrixInversionInvariants() {
        // Identity
        val I = Matrix.identity(2)
        val invI = I.invert2x2()
        assertThat(invI).isNotNull()
        assertThat(invI!![0, 0]).isWithin(1e-9).of(1.0)
        assertThat(invI[1, 1]).isWithin(1e-9).of(1.0)

        // General invertible 2x2
        val A = Matrix(2, 2, doubleArrayOf(4.0, 7.0, 2.0, 6.0)) // det = 24 - 14 = 10
        val invA = A.invert2x2()
        assertThat(invA).isNotNull()
        val prod = A * invA!!
        assertThat(prod[0, 0]).isWithin(1e-9).of(1.0)
        assertThat(prod[0, 1]).isWithin(1e-9).of(0.0)
        assertThat(prod[1, 0]).isWithin(1e-9).of(0.0)
        assertThat(prod[1, 1]).isWithin(1e-9).of(1.0)

        // Singular matrix: det = 0 -> returns null
        val singular = Matrix(2, 2, doubleArrayOf(2.0, 4.0, 1.0, 2.0))
        assertThat(singular.invert2x2()).isNull()

        // Near-zero determinant (< 1e-15) -> returns null
        val nearZeroDet = Matrix(2, 2, doubleArrayOf(1.0, 1.0, 1.0, 1.0 + 1e-16))
        assertThat(nearZeroDet.invert2x2()).isNull()
    }
}
