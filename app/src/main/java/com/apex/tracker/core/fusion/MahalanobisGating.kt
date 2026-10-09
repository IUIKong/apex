package com.apex.tracker.core.fusion

import com.apex.tracker.core.math.Matrix
import kotlin.math.abs

/**
 * Mahalanobis Distance Gating and Outlier Rejection with IMU Gyroscope Turn-Adaptive Widening.
 *
 * Implements Chi-square 2-DOF base gating (9.210 for 99% confidence),
 * dynamic gate widening and process noise boost based on angular yaw rate |omega_z|,
 * and a 5-consecutive-second outlier failsafe re-anchoring trigger.
 */
class MahalanobisGating(
    val baseThreshold: Double = BASE_THRESHOLD,
    val omegaDeadbandRadPerSec: Double = OMEGA_DEADBAND,
    val omegaMaxRadPerSec: Double = OMEGA_MAX,
    val turnScaleKappa: Double = KAPPA_TURN,
    val failsafeOutlierCount: Int = FAILSAFE_OUTLIER_COUNT
) {

    var consecutiveOutliersCount: Int = 0
        private set

    /**
     * Computes the adaptive gating threshold gamma_adaptive based on gyroscope yaw rate.
     */
    fun computeAdaptiveThreshold(gyroYawRateRadPerSec: Double): Double {
        if (gyroYawRateRadPerSec.isNaN() || gyroYawRateRadPerSec.isInfinite()) return baseThreshold
        val absYaw = abs(gyroYawRateRadPerSec)
        val span = omegaMaxRadPerSec - omegaDeadbandRadPerSec
        val effectiveRate = (absYaw - omegaDeadbandRadPerSec).coerceIn(0.0, span)
        val turnRatio = if (span > 0.0) effectiveRate / span else 0.0
        return baseThreshold * (1.0 + turnScaleKappa * (turnRatio * turnRatio))
    }

    /**
     * Computes the process noise boost multiplier (1.0 to 3.0) during turns.
     */
    fun computeProcessNoiseBoost(gyroYawRateRadPerSec: Double): Double {
        if (gyroYawRateRadPerSec.isNaN() || gyroYawRateRadPerSec.isInfinite()) return 1.0
        val absYaw = abs(gyroYawRateRadPerSec)
        val span = omegaMaxRadPerSec - omegaDeadbandRadPerSec
        val effectiveRate = (absYaw - omegaDeadbandRadPerSec).coerceIn(0.0, span)
        val turnRatio = if (span > 0.0) effectiveRate / span else 0.0
        return 1.0 + 2.0 * turnRatio
    }

    /**
     * Computes the squared Mahalanobis distance d_M^2 = y^T S^(-1) y for a 2D innovation.
     */
    fun computeSquaredDistance(innovation: DoubleArray, innovationCovariance: Matrix): Double {
        require(innovation.size >= 2) { "Innovation must have at least 2 components" }
        require(innovationCovariance.rows >= 2 && innovationCovariance.cols >= 2) {
            "Innovation covariance must be at least 2x2"
        }

        val y0 = innovation[0]
        val y1 = innovation[1]
        if (y0.isNaN() || y1.isNaN() || y0.isInfinite() || y1.isInfinite()) {
            return Double.POSITIVE_INFINITY
        }

        val s00 = innovationCovariance[0, 0]
        val s01 = innovationCovariance[0, 1]
        val s10 = innovationCovariance[1, 0]
        val s11 = innovationCovariance[1, 1]

        if (s00.isNaN() || s01.isNaN() || s10.isNaN() || s11.isNaN() ||
            s00.isInfinite() || s01.isInfinite() || s10.isInfinite() || s11.isInfinite()
        ) {
            return Double.POSITIVE_INFINITY
        }

        if (s00 <= 0.0 || s11 <= 0.0) {
            return Double.POSITIVE_INFINITY
        }

        val det = s00 * s11 - s01 * s10
        if (det.isNaN() || det.isInfinite() || det <= 1e-15) {
            // Ill-conditioned or non-positive definite; treat as outlier
            return Double.POSITIVE_INFINITY
        }

        // y^T * S^(-1) * y
        // S^(-1) = [ s11  -s01 ] / det
        //          [ -s10  s00 ]
        val d2 = (s11 * y0 * y0 - (s01 + s10) * y0 * y1 + s00 * y1 * y1) / det
        return if (d2.isNaN() || d2.isInfinite() || d2 < 0.0) {
            Double.POSITIVE_INFINITY
        } else {
            d2
        }
    }

    /**
     * Evaluates a 2D innovation against the adaptive threshold.
     * Returns GatingDecision with acceptance status, d_M^2, and whether failsafe reanchoring is triggered.
     */
    fun evaluate(
        innovation: DoubleArray,
        innovationCovariance: Matrix,
        gyroYawRateRadPerSec: Double
    ): GatingDecision {
        val d2 = computeSquaredDistance(innovation, innovationCovariance)
        val gamma = computeAdaptiveThreshold(gyroYawRateRadPerSec)
        val safeGamma = if (gamma.isNaN() || gamma.isInfinite() || gamma <= 0.0) baseThreshold else gamma

        val isOutlier = d2 > safeGamma || d2 < 0.0 || d2.isNaN() || d2.isInfinite()

        return if (isOutlier) {
            consecutiveOutliersCount++
            val shouldReanchor = consecutiveOutliersCount >= failsafeOutlierCount
            if (shouldReanchor) {
                consecutiveOutliersCount = 0
            }
            GatingDecision(
                accepted = false,
                squaredMahalanobisDistance = d2,
                thresholdUsed = safeGamma,
                failsafeReanchored = shouldReanchor
            )
        } else {
            consecutiveOutliersCount = 0
            GatingDecision(
                accepted = true,
                squaredMahalanobisDistance = d2,
                thresholdUsed = safeGamma,
                failsafeReanchored = false
            )
        }
    }

    fun reset() {
        consecutiveOutliersCount = 0
    }

    data class GatingDecision(
        val accepted: Boolean,
        val squaredMahalanobisDistance: Double,
        val thresholdUsed: Double,
        val failsafeReanchored: Boolean
    )

    companion object {
        const val BASE_THRESHOLD = 9.210 // chi-square 2-DOF 99% critical value
        const val OMEGA_DEADBAND = 0.25 // rad/s (~14.3 deg/s)
        const val OMEGA_MAX = 2.00 // rad/s (~114.6 deg/s)
        const val KAPPA_TURN = 3.5
        const val FAILSAFE_OUTLIER_COUNT = 5 // 5 consecutive outliers trigger soft re-anchor
    }
}
