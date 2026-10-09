package com.apex.tracker.core.fusion

import java.util.Locale
import kotlin.math.exp

/**
 * Pace Engine providing dual-stage smoothing (5.0s rolling window + Exponential Moving Average tau=2.0s)
 * and near-zero velocity clamping.
 */
class PaceEngine(
    val windowDurationSec: Double = 5.0,
    val emaTauSec: Double = 2.0,
    val minSpeedClampMps: Double = 0.25,
    val paceUpdateIntervalSec: Double = 5.0
) {

    private val distanceTimeWindow = ArrayDeque<TimeDistancePoint>()

    data class TimeDistancePoint(val timeSec: Double, val acceptedDistanceMeters: Double)

    var smoothedVelocityMps: Double = 0.0
        private set

    private var hasInitializedEma = false
    private var lastRecordedDistanceMeters: Double? = null
    private var lastRecordedTimeSec: Double? = null
    private var lastPaceUpdateTimeSec: Double = 0.0
    private var displayedPaceSecPerKm: Double = Double.NaN

    /**
     * Updates velocity smoothing with new accepted distance at elapsed moving time.
     */
    fun update(
        elapsedMovingTimeSec: Double,
        acceptedDistanceMeters: Double,
        dtSeconds: Double,
        isAutoPaused: Boolean
    ): Double {
        if (isAutoPaused) {
            smoothedVelocityMps = 0.0
            hasInitializedEma = false
            distanceTimeWindow.clear()
            lastRecordedDistanceMeters = acceptedDistanceMeters
            lastRecordedTimeSec = elapsedMovingTimeSec
            displayedPaceSecPerKm = Double.NaN
            lastPaceUpdateTimeSec = 0.0
            return smoothedVelocityMps
        }

        val safeDt = if (dtSeconds.isNaN() || dtSeconds.isInfinite() || dtSeconds < 1e-4) 1e-4 else dtSeconds
        val safeMovingTime = if (elapsedMovingTimeSec.isNaN() || elapsedMovingTimeSec.isInfinite() || elapsedMovingTimeSec < 0.0) 0.0 else elapsedMovingTimeSec
        val safeAcceptedDist = if (acceptedDistanceMeters.isNaN() || acceptedDistanceMeters.isInfinite() || acceptedDistanceMeters < 0.0) 0.0 else acceptedDistanceMeters

        // Remove any points ahead in time (timeline rewind/restore protection)
        while (distanceTimeWindow.isNotEmpty() && distanceTimeWindow.last().timeSec >= safeMovingTime) {
            distanceTimeWindow.removeLast()
        }

        distanceTimeWindow.addLast(TimeDistancePoint(safeMovingTime, safeAcceptedDist))

        val cutoffTime = safeMovingTime - windowDurationSec
        while (distanceTimeWindow.size > 1 && distanceTimeWindow.first().timeSec < cutoffTime) {
            distanceTimeWindow.removeFirst()
        }

        val windowVelocity = if (distanceTimeWindow.size >= 2) {
            val oldest = distanceTimeWindow.first()
            val newest = distanceTimeWindow.last()
            val dt = newest.timeSec - oldest.timeSec
            val dd = newest.acceptedDistanceMeters - oldest.acceptedDistanceMeters
            if (dt > 0.001 && dd >= 0.0) dd / dt else 0.0
        } else {
            // Incremental step speed fallback rather than lifetime average speed
            val prevDist = lastRecordedDistanceMeters
            val prevTime = lastRecordedTimeSec
            if (prevDist != null && prevTime != null && safeMovingTime > prevTime) {
                val stepDt = safeMovingTime - prevTime
                val stepDd = safeAcceptedDist - prevDist
                if (stepDt > 0.001 && stepDd >= 0.0) stepDd / stepDt else 0.0
            } else if (safeMovingTime > 0.001 && safeAcceptedDist > 0.0) {
                safeAcceptedDist / safeMovingTime
            } else if (safeDt > 0.001 && safeAcceptedDist > 0.0) {
                safeAcceptedDist / safeDt
            } else {
                0.0
            }
        }

        lastRecordedDistanceMeters = safeAcceptedDist
        lastRecordedTimeSec = safeMovingTime

        val safeWindowVelocity = if (windowVelocity.isNaN() || windowVelocity.isInfinite() || windowVelocity < 0.0) 0.0 else windowVelocity

        if (!hasInitializedEma) {
            smoothedVelocityMps = safeWindowVelocity
            hasInitializedEma = true
        } else {
            val alpha = 1.0 - exp(-safeDt / emaTauSec)
            smoothedVelocityMps = alpha * safeWindowVelocity + (1.0 - alpha) * smoothedVelocityMps
        }

        // 5-second interval pace averaging: compute average pace over the 5-second window
        // and update the displayed pace every 5 seconds of moving time.
        val timeSincePaceUpdate = safeMovingTime - lastPaceUpdateTimeSec
        val candidatePace = if (smoothedVelocityMps >= minSpeedClampMps && !smoothedVelocityMps.isNaN() && !smoothedVelocityMps.isInfinite()) {
            1000.0 / smoothedVelocityMps
        } else {
            Double.NaN
        }

        if (displayedPaceSecPerKm.isNaN()) {
            if (!candidatePace.isNaN() && safeMovingTime >= 0.5) {
                displayedPaceSecPerKm = candidatePace
                lastPaceUpdateTimeSec = safeMovingTime
            }
        } else if (timeSincePaceUpdate >= paceUpdateIntervalSec) {
            displayedPaceSecPerKm = candidatePace
            lastPaceUpdateTimeSec = safeMovingTime
        }

        return smoothedVelocityMps
    }

    /**
     * Current pace in seconds per kilometer. Returns Double.NaN if clamped.
     * Uses 5-second rolling average updated on 5-second moving intervals to prevent constant jitter.
     */
    fun currentPaceSecPerKm(isAutoPaused: Boolean = false): Double {
        if (isAutoPaused || smoothedVelocityMps < minSpeedClampMps || smoothedVelocityMps.isNaN() || smoothedVelocityMps.isInfinite()) {
            return Double.NaN
        }
        return if (!displayedPaceSecPerKm.isNaN()) displayedPaceSecPerKm else (1000.0 / smoothedVelocityMps)
    }

    /**
     * Current pace in seconds per mile. Returns Double.NaN if clamped.
     */
    fun currentPaceSecPerMile(isAutoPaused: Boolean = false): Double {
        val paceKm = currentPaceSecPerKm(isAutoPaused)
        if (paceKm.isNaN() || paceKm.isInfinite() || paceKm <= 0.0) return Double.NaN
        val paceMi = paceKm * METERS_PER_MILE / 1000.0
        return if (paceMi.isNaN() || paceMi.isInfinite()) Double.NaN else paceMi
    }

    fun reset() {
        distanceTimeWindow.clear()
        smoothedVelocityMps = 0.0
        hasInitializedEma = false
        lastRecordedDistanceMeters = null
        lastRecordedTimeSec = null
        displayedPaceSecPerKm = Double.NaN
        lastPaceUpdateTimeSec = 0.0
    }

    companion object {
        const val METERS_PER_MILE = 1609.344

        /**
         * Formats pace in seconds per unit to "MM:SS" or "--:--".
         */
        fun formatPace(paceSeconds: Double): String {
            if (paceSeconds.isNaN() || paceSeconds.isInfinite() || paceSeconds > 4000.0 || paceSeconds <= 0.0) {
                return "--:--"
            }
            val totalSec = paceSeconds.toInt()
            val minutes = totalSec / 60
            val seconds = totalSec % 60
            return "%d:%02d".format(Locale.US, minutes, seconds)
        }
    }
}
