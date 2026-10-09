package com.apex.tracker.service

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Operating phase of the dead reckoning engine during GNSS dropout.
 */
enum class DeadReckoningPhase {
    /** Normal GNSS fix received within the last 1.0s. */
    NORMAL_GNSS,

    /** Kinematic extrapolation using last known velocity, damped acceleration, and gyro yaw (<15s). */
    KINEMATIC_EXTRAPOLATION,

    /** Step-frequency dead reckoning using accelerometer cadence and heading integration (15s - 30s). */
    STEP_FREQUENCY_HEADING,

    /** Complete motion cutoff after >30s of signal loss to prevent runaway drift. */
    CUTOFF
}

/**
 * Result of dead reckoning propagation step.
 */
data class DeadReckoningStep(
    val phase: DeadReckoningPhase,
    val deltaEastMeters: Double,
    val deltaNorthMeters: Double,
    val stepDistanceMeters: Double,
    val speedMps: Double,
    val headingRad: Double,
    val stepsDetected: Int = 0
)

/**
 * Dead Reckoning Engine handling temporary GNSS dropouts.
 *
 * Implements a 3-tier fallback architecture:
 * 1. Kinematic Extrapolation (< 15 seconds):
 *    Propagates state using velocity and heading from gyroscopic integration,
 *    with exponential acceleration damping to eliminate quadratic position explosion.
 * 2. Step-Frequency Heading (15 - 30 seconds):
 *    Switches from decaying velocity to biomechanical step detection from accelerometer energy,
 *    coupling step count with heading integration to accumulate realistic stride distance.
 * 3. Cutoff (> 30 seconds):
 *    Enforces total velocity and distance clamping to 0.0 to prevent unbounded trajectory drift.
 */
class DeadReckoningEngine(
    private val defaultStepLengthMeters: Double = 0.75
) {
    companion object {
        const val KINEMATIC_TIMEOUT_MS = 15_000L
        const val STEP_HEADING_TIMEOUT_MS = 30_000L
        const val ACCEL_STEP_THRESHOLD = 1.8f // m/s^2 peak threshold above gravity
    }

    private var lastGpsTimestampMs: Long = 0L
    private var currentHeadingRad: Double = 0.0
    private var lastVelEast: Double = 0.0
    private var lastVelNorth: Double = 0.0
    private var lastAccEast: Double = 0.0
    private var lastAccNorth: Double = 0.0

    // Step detector state
    private var stepCount: Int = 0
    private var lastReportedStepCount: Int = 0
    private var lastStepTimestampMs: Long = 0L
    private var lastAccelMagnitude: Float = 9.81f
    private var accelRising: Boolean = false

    var currentPhase: DeadReckoningPhase = DeadReckoningPhase.NORMAL_GNSS
        private set

    /**
     * Resets / anchors the dead reckoning engine when a valid GNSS fix is accepted.
     */
    @Synchronized
    fun onGpsFix(
        timestampMs: Long,
        velEast: Double,
        velNorth: Double,
        accEast: Double = 0.0,
        accNorth: Double = 0.0,
        bearingDegrees: Float? = null
    ) {
        lastGpsTimestampMs = timestampMs
        lastVelEast = velEast
        lastVelNorth = velNorth
        lastAccEast = accEast
        lastAccNorth = accNorth
        stepCount = 0
        lastReportedStepCount = 0

        if (bearingDegrees != null) {
            currentHeadingRad = Math.toRadians(bearingDegrees.toDouble())
        } else {
            val speed = sqrt(velEast * velEast + velNorth * velNorth)
            if (speed > 0.5) {
                // Bearing from North (clockwise)
                currentHeadingRad = Math.atan2(velEast, velNorth)
            }
        }
        currentPhase = DeadReckoningPhase.NORMAL_GNSS
    }

    /**
     * Updates gyroscope yaw rate (rad/s) for heading integration.
     */
    @Synchronized
    fun onGyroUpdate(yawRateRadPerSec: Double, dtSeconds: Double) {
        currentHeadingRad += yawRateRadPerSec * dtSeconds
        // Normalize heading to [-PI, PI]
        while (currentHeadingRad > PI) currentHeadingRad -= 2.0 * PI
        while (currentHeadingRad < -PI) currentHeadingRad += 2.0 * PI
    }

    /**
     * Processes accelerometer samples for step detection during the step-heading phase.
     */
    @Synchronized
    fun onAccelerometerSample(ax: Float, ay: Float, az: Float, timestampMs: Long): Boolean {
        val magnitude = sqrt(ax * ax + ay * ay + az * az)
        val gravityRemoved = abs(magnitude - 9.80665f)

        var detectedStep = false
        if (gravityRemoved > ACCEL_STEP_THRESHOLD && !accelRising && (timestampMs - lastStepTimestampMs > 250L)) {
            // Peak detected with minimum 250ms cadence interval (<= 240 steps/min)
            stepCount++
            lastStepTimestampMs = timestampMs
            detectedStep = true
            accelRising = true
        } else if (gravityRemoved < ACCEL_STEP_THRESHOLD * 0.5f) {
            accelRising = false
        }
        lastAccelMagnitude = magnitude
        return detectedStep
    }

    /**
     * Evaluates and advances dead reckoning state for a given timestamp.
     */
    @Synchronized
    fun update(currentTimestampMs: Long, dtSeconds: Double): DeadReckoningStep {
        val timeSinceGpsMs = if (lastGpsTimestampMs > 0L) currentTimestampMs - lastGpsTimestampMs else 0L

        currentPhase = when {
            lastGpsTimestampMs == 0L || timeSinceGpsMs < 1_000L -> DeadReckoningPhase.NORMAL_GNSS
            timeSinceGpsMs <= KINEMATIC_TIMEOUT_MS -> DeadReckoningPhase.KINEMATIC_EXTRAPOLATION
            timeSinceGpsMs <= STEP_HEADING_TIMEOUT_MS -> DeadReckoningPhase.STEP_FREQUENCY_HEADING
            else -> DeadReckoningPhase.CUTOFF
        }

        return when (currentPhase) {
            DeadReckoningPhase.NORMAL_GNSS -> {
                DeadReckoningStep(
                    phase = DeadReckoningPhase.NORMAL_GNSS,
                    deltaEastMeters = 0.0,
                    deltaNorthMeters = 0.0,
                    stepDistanceMeters = 0.0,
                    speedMps = sqrt(lastVelEast * lastVelEast + lastVelNorth * lastVelNorth),
                    headingRad = currentHeadingRad
                )
            }

            DeadReckoningPhase.KINEMATIC_EXTRAPOLATION -> {
                // Dampen acceleration exponentially (half-life ~ 1.4s) to prevent quadratic position explosion
                val dampingFactor = exp(-0.5 * dtSeconds)
                lastAccEast *= dampingFactor
                lastAccNorth *= dampingFactor

                // Extrapolate velocity
                lastVelEast += lastAccEast * dtSeconds
                lastVelNorth += lastAccNorth * dtSeconds

                // Velocity damping as dropout continues
                val velDecay = exp(-0.1 * dtSeconds)
                lastVelEast *= velDecay
                lastVelNorth *= velDecay

                val dEast = lastVelEast * dtSeconds
                val dNorth = lastVelNorth * dtSeconds
                val stepDist = sqrt(dEast * dEast + dNorth * dNorth)
                val speed = stepDist / dtSeconds

                DeadReckoningStep(
                    phase = DeadReckoningPhase.KINEMATIC_EXTRAPOLATION,
                    deltaEastMeters = dEast,
                    deltaNorthMeters = dNorth,
                    stepDistanceMeters = stepDist,
                    speedMps = speed,
                    headingRad = currentHeadingRad
                )
            }

            DeadReckoningPhase.STEP_FREQUENCY_HEADING -> {
                // Check if steps were registered in this epoch
                val stepsInEpoch = (stepCount - lastReportedStepCount).coerceAtLeast(0)
                lastReportedStepCount = stepCount
                val distanceInEpoch = stepsInEpoch * defaultStepLengthMeters

                val dEast = distanceInEpoch * sin(currentHeadingRad)
                val dNorth = distanceInEpoch * cos(currentHeadingRad)
                val speed = if (dtSeconds > 0.0) distanceInEpoch / dtSeconds else 0.0

                DeadReckoningStep(
                    phase = DeadReckoningPhase.STEP_FREQUENCY_HEADING,
                    deltaEastMeters = dEast,
                    deltaNorthMeters = dNorth,
                    stepDistanceMeters = distanceInEpoch,
                    speedMps = speed,
                    headingRad = currentHeadingRad,
                    stepsDetected = stepsInEpoch
                )
            }

            DeadReckoningPhase.CUTOFF -> {
                // Total motion clamp after >30s of GPS dropout
                DeadReckoningStep(
                    phase = DeadReckoningPhase.CUTOFF,
                    deltaEastMeters = 0.0,
                    deltaNorthMeters = 0.0,
                    stepDistanceMeters = 0.0,
                    speedMps = 0.0,
                    headingRad = currentHeadingRad
                )
            }
        }
    }
}
