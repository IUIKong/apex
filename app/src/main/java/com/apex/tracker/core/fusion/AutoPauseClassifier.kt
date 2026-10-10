package com.apex.tracker.core.fusion

import com.apex.tracker.core.model.ActivityType
import com.apex.tracker.core.model.MotionState
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 5-State Auto-Pause Classifier FSM.
 * Fuses filtered horizontal velocity and rolling 1.5s accelerometer energy E_acc above gravity
 * with activity-specific debounce timers to eliminate stationary jitter without start-stop stutter.
 *
 * Performance-optimized: Zero-allocation primitive ring buffer eliminates 50 Hz
 * object allocations and GC churn during continuous tracking.
 */
class AutoPauseClassifier(
    var activityType: ActivityType = ActivityType.RUNNING
) {

    var currentState: MotionState = MotionState.INITIALIZING
        private set

    private var stopDebounceTimerSec: Double = 0.0
    private var moveDebounceTimerSec: Double = 0.0

    // Zero-allocation rolling accelerometer buffer for 1.5s window
    private val bufferCapacity = 128
    private val bufferMask = bufferCapacity - 1
    private val timestamps = LongArray(bufferCapacity)
    private val magnitudes = DoubleArray(bufferCapacity)
    private var head = 0
    private var sampleCount = 0

    // Pending distance and time buffers during debounce states
    var pendingStopDistance: Double = 0.0
        private set
    var pendingStopDurationMs: Long = 0L
        private set

    var pendingMoveDistance: Double = 0.0
        private set
    var pendingMoveDurationMs: Long = 0L
        private set

    /**
     * Ingests IMU acceleration sample (ax, ay, az in m/s^2).
     * Zero-allocation ring buffer insertion and time-window pruning.
     */
    fun addAccelerationSample(ax: Float, ay: Float, az: Float, timestampEpochMs: Long) {
        if (ax.isNaN() || ay.isNaN() || az.isNaN() || ax.isInfinite() || ay.isInfinite() || az.isInfinite()) return
        val x = ax.toDouble()
        val y = ay.toDouble()
        val z = az.toDouble()
        val norm = sqrt(x * x + y * y + z * z)
        if (norm.isNaN() || norm.isInfinite()) return

        val writeIdx = (head + sampleCount) and bufferMask
        timestamps[writeIdx] = timestampEpochMs
        magnitudes[writeIdx] = norm

        if (sampleCount < bufferCapacity) {
            sampleCount++
        } else {
            head = (head + 1) and bufferMask
        }

        // Evict samples older than 1500ms
        val cutoff = timestampEpochMs - 1500L
        while (sampleCount > 0 && timestamps[head] < cutoff) {
            head = (head + 1) and bufferMask
            sampleCount--
        }
    }

    /**
     * Computes the dynamic accelerometer energy E_acc above gravity (9.80665 m/s^2).
     * Single-pass primitive iteration without allocations or lambda closures.
     */
    fun computeAccelerometerEnergy(): Double {
        if (sampleCount == 0) return 0.0
        val newestIdx = (head + sampleCount - 1) and bufferMask
        val latestTime = timestamps[newestIdx]
        val recentWindowCutoff = latestTime - 800L

        var recentCount = 0
        var recentSumDev = 0.0
        var allSumDev = 0.0

        for (i in 0 until sampleCount) {
            val idx = (head + i) and bufferMask
            val dev = abs(magnitudes[idx] - GRAVITY)
            allSumDev += dev
            if (timestamps[idx] >= recentWindowCutoff) {
                recentSumDev += dev
                recentCount++
            }
        }

        val result = if (recentCount > 0) {
            recentSumDev / recentCount
        } else {
            allSumDev / sampleCount
        }
        return if (result.isNaN() || result.isInfinite() || result < 0.0) 0.0 else result
    }

    fun hasAccelerationSamples(): Boolean = sampleCount > 0

    /**
     * Advances the FSM given current horizontal velocity, elapsed step dt, and incremental distance.
     * Returns whether the distance increment should be committed immediately to accepted workout distance.
     */
    fun update(
        vHorizMps: Double,
        dtSeconds: Double,
        incrementalDistanceMeters: Double,
        gpsAccuracyMeters: Float = 5.0f
    ): FsmUpdateResult {
        val safeV = if (vHorizMps.isNaN() || vHorizMps.isInfinite() || vHorizMps < 0.0) 0.0 else vHorizMps
        val safeDt = if (dtSeconds.isNaN() || dtSeconds.isInfinite() || dtSeconds < 1e-4) 1e-4 else dtSeconds
        val safeDist = if (incrementalDistanceMeters.isNaN() || incrementalDistanceMeters.isInfinite() || incrementalDistanceMeters < 0.0) 0.0 else incrementalDistanceMeters
        val safeAccuracy = if (gpsAccuracyMeters.isNaN() || gpsAccuracyMeters.isInfinite() || gpsAccuracyMeters < 0f) 5.0f else gpsAccuracyMeters

        val hasAccel = sampleCount > 0
        val eAcc = computeAccelerometerEnergy()

        val vStop = activityType.vStopMps
        val vMove = activityType.vMoveMps
        val eStop = activityType.eStopMps2
        val eMove = activityType.eMoveMps2
        val tStopDebounce = activityType.tStopDebounceSec
        val tMoveDebounce = activityType.tMoveDebounceSec

        val isCycling = activityType == ActivityType.CYCLING
        val isSlowActivity = activityType == ActivityType.HIKING || activityType == ActivityType.WALKING

        val hasCadence = (hasAccel && eAcc >= eStop) || (isCycling && (safeV >= 0.35 || safeDist >= 0.25))
        val cStill = if (isCycling) {
            safeV < vStop
        } else {
            !hasCadence && (
                (hasAccel && eAcc < eStop && safeV < vMove) ||
                (safeV < vStop && (!hasAccel || eAcc < eStop))
            )
        }
        val cMotion = !cStill && (
            (safeV >= vMove && (isCycling || !hasAccel || eAcc >= eStop || safeV > 2.5)) ||
            (isCycling && safeV >= vStop && safeAccuracy < 20.0f) ||
            (hasAccel && safeV >= vStop && eAcc >= eMove) ||
            (isSlowActivity && safeV >= vStop && (!hasAccel || eAcc >= eStop) && safeAccuracy < 15.0f) ||
            (hasCadence && (safeV >= 0.25 || safeDist >= 0.20))
        )

        var committedDistance = 0.0
        var committedDurationMs = 0L
        var stateChanged = false
        val previousState = currentState

        when (currentState) {
            MotionState.INITIALIZING -> {
                pendingMoveDistance += safeDist
                pendingMoveDurationMs += (safeDt * 1000.0).toLong()

                val isInitialMotion = cMotion ||
                    (safeV >= 0.25 && (hasCadence || isCycling || safeAccuracy < 20.0f)) ||
                    (safeDist >= 0.20 && (hasAccel || isCycling || safeAccuracy < 15.0f))
                if (isInitialMotion && safeAccuracy < 25.0f) {
                    currentState = MotionState.MOVING
                    stateChanged = true
                    committedDistance = pendingMoveDistance
                    committedDurationMs = pendingMoveDurationMs
                    pendingMoveDistance = 0.0
                    pendingMoveDurationMs = 0L
                    stopDebounceTimerSec = 0.0
                } else if (cStill) {
                    stopDebounceTimerSec += safeDt
                    if (stopDebounceTimerSec >= tStopDebounce) {
                        currentState = MotionState.STOPPED
                        stateChanged = true
                        pendingMoveDistance = 0.0
                        pendingMoveDurationMs = 0L
                        stopDebounceTimerSec = 0.0
                    }
                }
            }

            MotionState.MOVING -> {
                if (cStill) {
                    currentState = MotionState.POSSIBLY_STOPPED
                    stateChanged = true
                    stopDebounceTimerSec = safeDt
                    pendingStopDistance = safeDist
                    pendingStopDurationMs = (safeDt * 1000.0).toLong()
                    if (stopDebounceTimerSec >= tStopDebounce) {
                        currentState = MotionState.STOPPED
                        pendingStopDistance = 0.0
                        pendingStopDurationMs = 0L
                        stopDebounceTimerSec = 0.0
                    }
                } else {
                    committedDistance = safeDist
                    committedDurationMs = (safeDt * 1000.0).toLong()
                }
            }

            MotionState.POSSIBLY_STOPPED -> {
                stopDebounceTimerSec += safeDt
                pendingStopDistance += safeDist
                pendingStopDurationMs += (safeDt * 1000.0).toLong()

                if (!cStill) {
                    // Motion resumed before timeout: commit pending buffer and return to MOVING
                    currentState = MotionState.MOVING
                    stateChanged = true
                    committedDistance = pendingStopDistance
                    committedDurationMs = pendingStopDurationMs
                    pendingStopDistance = 0.0
                    pendingStopDurationMs = 0L
                    stopDebounceTimerSec = 0.0
                } else if (stopDebounceTimerSec >= tStopDebounce) {
                    // Timeout reached: enter STOPPED and discard pending stop buffer
                    currentState = MotionState.STOPPED
                    stateChanged = true
                    pendingStopDistance = 0.0
                    pendingStopDurationMs = 0L
                    stopDebounceTimerSec = 0.0
                }
            }

            MotionState.STOPPED -> {
                if (cMotion) {
                    currentState = MotionState.POSSIBLY_MOVING
                    stateChanged = true
                    moveDebounceTimerSec = safeDt
                    pendingMoveDistance = safeDist
                    pendingMoveDurationMs = (safeDt * 1000.0).toLong()
                    if (moveDebounceTimerSec >= tMoveDebounce) {
                        currentState = MotionState.MOVING
                        committedDistance = pendingMoveDistance
                        committedDurationMs = pendingMoveDurationMs
                        pendingMoveDistance = 0.0
                        pendingMoveDurationMs = 0L
                        moveDebounceTimerSec = 0.0
                    }
                }
            }

            MotionState.POSSIBLY_MOVING -> {
                moveDebounceTimerSec += safeDt
                pendingMoveDistance += safeDist
                pendingMoveDurationMs += (safeDt * 1000.0).toLong()

                if (!cMotion) {
                    // Motion ceased before timeout: discard pending resume buffer and return to STOPPED
                    currentState = MotionState.STOPPED
                    stateChanged = true
                    pendingMoveDistance = 0.0
                    pendingMoveDurationMs = 0L
                    moveDebounceTimerSec = 0.0
                } else if (moveDebounceTimerSec >= tMoveDebounce) {
                    // Timeout reached: sustained movement confirmed! Commit retroactively
                    currentState = MotionState.MOVING
                    stateChanged = true
                    committedDistance = pendingMoveDistance
                    committedDurationMs = pendingMoveDurationMs
                    pendingMoveDistance = 0.0
                    pendingMoveDurationMs = 0L
                    moveDebounceTimerSec = 0.0
                }
            }
        }

        return FsmUpdateResult(
            previousState = previousState,
            currentState = currentState,
            stateChanged = stateChanged,
            committedDistanceMeters = committedDistance,
            committedDurationMs = committedDurationMs,
            isMoving = (currentState == MotionState.MOVING)
        )
    }

    fun setState(state: MotionState) {
        currentState = state
        stopDebounceTimerSec = 0.0
        moveDebounceTimerSec = 0.0
        pendingStopDistance = 0.0
        pendingStopDurationMs = 0L
        pendingMoveDistance = 0.0
        pendingMoveDurationMs = 0L
    }

    fun restore(fsmState: MotionState, activityType: ActivityType) {
        this.activityType = activityType
        setState(fsmState)
    }

    data class FsmUpdateResult(
        val previousState: MotionState,
        val currentState: MotionState,
        val stateChanged: Boolean,
        val committedDistanceMeters: Double,
        val committedDurationMs: Long = 0L,
        val isMoving: Boolean
    )

    companion object {
        const val GRAVITY = 9.80665 // Standard acceleration of gravity (m/s^2)
    }
}
