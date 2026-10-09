package com.apex.tracker.core.fusion

import com.apex.tracker.core.math.EnuProjection
import com.apex.tracker.core.model.ActivityType
import com.apex.tracker.core.model.GpsUpdateResult
import com.apex.tracker.core.model.MotionState
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.abs

class GpsReliabilityAndAccuracyBreakTest {

    private val originLat = 37.7749
    private val originLon = -122.4194
    private val originAlt = 50.0

    /**
     * FAILURE 1:
     * When Android does not report speed accuracy (hasSpeedAccuracy() == false),
     * TrackingForegroundService passes speedAccuracy = 1.0f.
     * The prior EKF required (lastGpsSpeedAccuracyMps ?: 1f) < 0.8f.
     * With speedAccuracy = 1.0f, Doppler speed = 0.0f is IGNORED,
     * allowing stationary GPS jitter to inflate accepted distance!
     */
    @Test
    fun testDopplerStationaryWithDefaultOneSpeedAccuracyMustSuppressDrift() {
        val ekf = EkfStateEstimator()
        ekf.initialize(originLat, originLon, originAlt, initialAccuracyMeters = 2.0f)

        // Run for 5 seconds at 3.0 m/s
        for (sec in 1..5) {
            ekf.predict(1.0, ActivityType.RUNNING, 0.0)
            ekf.updateGpsVelocity(3.0f, 0.0f, 0.2f)
            val (lat, lon) = EnuProjection.inverse(0.0, sec * 3.0, originLat, originLon)
            ekf.updateGpsPosition(lat, lon, 2.0f)
        }
        val distBeforeStop = ekf.getFusedState().acceptedDistance
        assertThat(distBeforeStop).isGreaterThan(10.0)

        // Stop at red light: speed = 0.0f, but speedAccuracy is 1.0f (Android default without hasSpeedAccuracy)
        for (sec in 6..20) {
            ekf.predict(1.0, ActivityType.RUNNING, 0.0)
            ekf.updateGpsVelocity(0.0f, 0.0f, 1.0f) // 1.0f speed accuracy!
            val jitter = if (sec % 2 == 0) 0.6 else -0.6
            val (jitLat, jitLon) = EnuProjection.inverse(jitter, 15.0 + jitter, originLat, originLon)
            ekf.updateGpsPosition(jitLat, jitLon, 2.0f)
        }

        val stateAfterStop = ekf.getFusedState()
        assertThat(stateAfterStop.fsmState).isEqualTo(MotionState.STOPPED)
        assertThat(stateAfterStop.acceptedDistance).isWithin(0.01).of(distBeforeStop)
    }

    /**
     * FAILURE 2:
     * Dead Reckoning Distance Duplication.
     * During GNSS dropout, if predict() adds distance AND applyDeadReckoningStep() adds distance,
     * distance is double-counted.
     */
    @Test
    fun testDeadReckoningDoesNotDoubleCountDistanceDuringGpsDropout() {
        val ekf = EkfStateEstimator()
        ekf.initialize(originLat, originLon, originAlt, initialAccuracyMeters = 2.0f)

        // Run for 5 seconds North
        for (sec in 1..5) {
            ekf.predict(1.0, ActivityType.RUNNING, 0.0)
            ekf.updateImuAcceleration(0.5f, 0.5f, 12.5f, sec * 1000L)
            val (lat, lon) = EnuProjection.inverse(0.0, sec * 3.0, originLat, originLon)
            ekf.updateGpsPosition(lat, lon, 2.0f)
        }
        val distAtTunnelEntry = ekf.getFusedState().acceptedDistance
        val movingTimeAtTunnelEntry = ekf.movingTimeMs

        // Enter tunnel for 5 seconds (dropout).
        // TrackingForegroundService calls both predict() and applyDeadReckoningStep() on each tick
        val deadReckoningEngine = com.apex.tracker.service.DeadReckoningEngine()
        deadReckoningEngine.onGpsFix(5000L, velEast = 0.0, velNorth = 3.0, bearingDegrees = 0.0f)

        for (i in 1..5) {
            val now = 5000L + i * 1000L
            ekf.updateImuAcceleration(0.5f, 0.5f, 12.5f, now)
            ekf.predict(1.0, ActivityType.RUNNING, 0.0)
            val drStep = deadReckoningEngine.update(now, 1.0)
            ekf.applyDeadReckoningStep(drStep, 1.0)
        }

        val stateAfterTunnel = ekf.getFusedState()
        val dropoutDistance = stateAfterTunnel.acceptedDistance - distAtTunnelEntry
        val dropoutMovingTimeSec = (stateAfterTunnel.movingTimeMs - movingTimeAtTunnelEntry) / 1000.0

        // 5 seconds at ~3.0 m/s should be ~15m (definitely NOT 30m from double counting!)
        assertThat(dropoutDistance).isLessThan(18.0)
        assertThat(dropoutMovingTimeSec).isWithin(0.1).of(5.0)
    }

    /**
     * FAILURE 3:
     * Slow uphill hiking / walking (0.45 m/s) without footstrike accelerometer energy
     * must transition from INITIALIZING to MOVING and record distance.
     */
    @Test
    fun testSlowWalkingTransitionFromInitializingToMoving() {
        val classifier = AutoPauseClassifier(ActivityType.HIKING)
        assertThat(classifier.currentState).isEqualTo(MotionState.INITIALIZING)

        // Slow uphill walk: 0.45 m/s, dt = 1.0s, safeDist = 0.45m
        var totalCommitted = 0.0
        for (i in 1..10) {
            val res = classifier.update(vHorizMps = 0.45, dtSeconds = 1.0, incrementalDistanceMeters = 0.45, gpsAccuracyMeters = 3.0f)
            totalCommitted += res.committedDistanceMeters
        }

        // Must transition to MOVING and commit distance
        assertThat(classifier.currentState).isEqualTo(MotionState.MOVING)
        assertThat(totalCommitted).isGreaterThan(0.0)
    }

    /**
     * FAILURE 4:
     * Smooth cycling at 2.2 m/s on asphalt (low accelerometer energy) must be classified as MOVING.
     */
    @Test
    fun testSmoothCyclingLowAccelerometerEnergyClassifiedAsMoving() {
        val classifier = AutoPauseClassifier(ActivityType.CYCLING)
        classifier.setState(MotionState.STOPPED)

        // Add smooth rolling IMU samples (accel energy ~0.20 m/s^2, below eMove 0.40)
        for (i in 0 until 50) {
            classifier.addAccelerationSample(0.1f, 0.1f, 10.0f, i * 20L)
        }

        // Cycling at 2.2 m/s (> vMove 1.5 m/s)
        var totalCommitted = 0.0
        for (sec in 1..5) {
            val res = classifier.update(vHorizMps = 2.2, dtSeconds = 1.0, incrementalDistanceMeters = 2.2)
            totalCommitted += res.committedDistanceMeters
        }

        assertThat(classifier.currentState).isEqualTo(MotionState.MOVING)
        assertThat(totalCommitted).isGreaterThan(5.0)
    }

    /**
     * FAILURE 5:
     * GNSS Recovery after 15s dropout should immediately accept the re-acquired GPS fix
     * without 5 seconds of outlier rejections.
     */
    @Test
    fun testGpsRecoveryAfterDropoutImmediatelyAcceptsValidFix() {
        val ekf = EkfStateEstimator()
        ekf.initialize(originLat, originLon, originAlt, initialAccuracyMeters = 2.0f)

        // Run North for 5s
        for (sec in 1..5) {
            ekf.predict(1.0, ActivityType.RUNNING, 0.0)
            val (lat, lon) = EnuProjection.inverse(0.0, sec * 3.0, originLat, originLon)
            ekf.updateGpsPosition(lat, lon, 2.0f)
        }

        // 15 seconds of GPS blackout while running
        for (i in 1..15) {
            ekf.predict(1.0, ActivityType.RUNNING, 0.0)
        }

        // Emerging from blackout at true position (0, 60m). Dead reckoning was slightly off at (5m, 55m).
        val (emergeLat, emergeLon) = EnuProjection.inverse(5.0, 60.0, originLat, originLon)
        val res = ekf.updateGpsPosition(emergeLat, emergeLon, accuracyMeters = 3.0f)

        // Fix is accurate (3.0m). Must be ACCEPTED on recovery!
        assertThat(res).isNotEqualTo(GpsUpdateResult.REJECTED_OUTLIER)
    }
}
