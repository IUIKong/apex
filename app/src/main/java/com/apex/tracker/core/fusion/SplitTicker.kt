package com.apex.tracker.core.fusion

import com.apex.tracker.core.model.SplitRecord
import kotlin.math.max

/**
 * Real-time 1.0 km Interval Splits Ticker Engine.
 * Detects boundary crossings every 1000.0m of accepted activity distance,
 * computes split pace, elevation delta, and tracks real-time progress and pace delta vs average.
 */
class SplitTicker(
    val splitIntervalMeters: Double = 1000.0
) {

    private val completedSplits = mutableListOf<SplitRecord>()

    private var lastSplitDistanceMeters: Double = 0.0
    private var lastSplitElapsedMs: Long = 0L
    private var lastSplitMovingMs: Long = 0L
    private var lastSplitElevationGain: Double = 0.0
    private var lastSplitElevationLoss: Double = 0.0

    /**
     * Ingests current workout progress and checks for 1.0 km boundary crossings.
     * Returns newly generated SplitRecord if boundary crossed, null otherwise.
     */
    fun checkSplit(
        acceptedDistanceMeters: Double,
        elapsedTimeMs: Long,
        movingTimeMs: Long,
        elevationGainMeters: Double,
        elevationLossMeters: Double
    ): SplitRecord? {
        if (acceptedDistanceMeters.isNaN() || acceptedDistanceMeters.isInfinite() || acceptedDistanceMeters < 0.0) {
            return null
        }
        val nextSplitTarget = lastSplitDistanceMeters + splitIntervalMeters
        if (acceptedDistanceMeters >= nextSplitTarget) {
            val splitIndex = completedSplits.size + 1
            val deltaElapsed = max(0L, elapsedTimeMs - lastSplitElapsedMs)
            val deltaMoving = max(0L, movingTimeMs - lastSplitMovingMs)
            val deltaGain = max(0.0, elevationGainMeters - lastSplitElevationGain)
            val deltaLoss = max(0.0, elevationLossMeters - lastSplitElevationLoss)

            // Pace in seconds per kilometer for this split interval
            val intervalKm = splitIntervalMeters / 1000.0
            val splitPaceSecPerKm = if (intervalKm > 0.0) (deltaMoving / 1000.0) / intervalKm else 0.0

            val record = SplitRecord(
                splitIndex = splitIndex,
                distanceMeters = splitIntervalMeters,
                elapsedTimeMs = deltaElapsed,
                movingTimeMs = deltaMoving,
                paceSecondsPerKm = if (splitPaceSecPerKm.isNaN() || splitPaceSecPerKm.isInfinite() || splitPaceSecPerKm < 0.0) 0.0 else splitPaceSecPerKm,
                elevationGainMeters = deltaGain,
                elevationLossMeters = deltaLoss
            )

            completedSplits.add(record)
            lastSplitDistanceMeters = nextSplitTarget
            lastSplitElapsedMs = elapsedTimeMs
            lastSplitMovingMs = movingTimeMs
            lastSplitElevationGain = elevationGainMeters
            lastSplitElevationLoss = elevationLossMeters

            return record
        }
        return null
    }

    /**
     * Distance covered within the current in-progress kilometer interval.
     */
    fun currentSplitDistance(acceptedDistanceMeters: Double): Double {
        if (acceptedDistanceMeters.isNaN() || acceptedDistanceMeters.isInfinite() || acceptedDistanceMeters < 0.0) {
            return 0.0
        }
        return (acceptedDistanceMeters - lastSplitDistanceMeters).coerceAtLeast(0.0)
    }

    /**
     * Fraction [0.0, 1.0) of progress within current kilometer.
     */
    fun currentSplitProgressFraction(acceptedDistanceMeters: Double): Float {
        if (splitIntervalMeters <= 0.0) return 0.0f
        val currDist = currentSplitDistance(acceptedDistanceMeters)
        val frac = (currDist / splitIntervalMeters).toFloat()
        return if (frac.isNaN() || frac.isInfinite()) 0.0f else frac.coerceIn(0.0f, 1.0f)
    }

    /**
     * Pace delta vs workout average pace: currentPace - avgPace (negative means faster).
     */
    fun paceDeltaVsAverage(currentPaceSecPerKm: Double, avgPaceSecPerKm: Double): Double {
        if (currentPaceSecPerKm.isNaN() || currentPaceSecPerKm.isInfinite() ||
            avgPaceSecPerKm.isNaN() || avgPaceSecPerKm.isInfinite()
        ) {
            return 0.0
        }
        return currentPaceSecPerKm - avgPaceSecPerKm
    }

    fun getSplits(): List<SplitRecord> = completedSplits.toList()

    fun reset() {
        completedSplits.clear()
        lastSplitDistanceMeters = 0.0
        lastSplitElapsedMs = 0L
        lastSplitMovingMs = 0L
        lastSplitElevationGain = 0.0
        lastSplitElevationLoss = 0.0
    }
}
