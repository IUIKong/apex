package com.apex.tracker.core.fusion

import com.apex.tracker.core.math.EnuProjection
import com.apex.tracker.core.model.ActivityType
import com.apex.tracker.core.model.GpsUpdateResult
import com.apex.tracker.core.model.MotionState
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.abs

class EkfStateEstimatorTest {

    private val originLat = 37.7749
    private val originLon = -122.4194
    private val originAlt = 50.0

    @Test
    fun testInitializationSetsOriginAndInitialCovariance() {
        val ekf = EkfStateEstimator()
        ekf.initialize(originLat, originLon, originAlt, initialAccuracyMeters = 3.0f)

        assertThat(ekf.originLat).isEqualTo(originLat)
        assertThat(ekf.originLon).isEqualTo(originLon)
        assertThat(ekf.originAlt).isEqualTo(originAlt)

        val state = ekf.state
        assertThat(state[0]).isEqualTo(0.0) // pe
        assertThat(state[1]).isEqualTo(0.0) // pn
        assertThat(state[2]).isEqualTo(0.0) // ve
        assertThat(state[3]).isEqualTo(0.0) // vn

        val diag = ekf.getDiagnostics()
        assertThat(diag.posVarEast).isWithin(0.01).of(9.0) // (3.0m)^2
        assertThat(diag.posVarNorth).isWithin(0.01).of(9.0)
    }



    @Test
    fun testConstantVelocityKinematicConvergence() {
        val ekf = EkfStateEstimator()
        ekf.initialize(originLat, originLon, originAlt, initialAccuracyMeters = 2.0f)

        val speed = 3.0 // 3.0 m/s North
        var currentLat = originLat
        var currentLon = originLon

        // Simulate 30 seconds of 1 Hz running North
        for (sec in 1..30) {
            // Predict 1 second step
            ekf.predict(dtSeconds = 1.0, activityType = ActivityType.RUNNING, gyroYawRateRadPerSec = 0.0)

            // Synthetic GPS fix at 1 Hz moving 3m North per second
            val targetPn = sec * speed
            val (measLat, measLon) = EnuProjection.inverse(0.0, targetPn, originLat, originLon)
            currentLat = measLat
            currentLon = measLon

            val res = ekf.updateGpsPosition(measLat, measLon, accuracyMeters = 2.0f)
            assertThat(res).isEqualTo(GpsUpdateResult.ACCEPTED)
        }

        val fusedState = ekf.getFusedState()
        val estimatedPn = ekf.state[1]
        val estimatedVn = ekf.state[3]

        // True distance North after 30s is 90.0m
        assertThat(estimatedPn).isWithin(1.0).of(90.0)
        // Velocity converges close to 3.0 m/s
        assertThat(estimatedVn).isWithin(0.4).of(3.0)
        assertThat(ekf.state[2]).isWithin(0.2).of(0.0) // ve ~ 0.0

        assertThat(fusedState.fsmState).isEqualTo(MotionState.MOVING)
        assertThat(fusedState.filteredDistance).isWithin(3.0).of(90.0)
    }

    @Test
    fun testRejectionOfMassiveMultipathSpikePreservesTrajectory() {
        val ekf = EkfStateEstimator()
        ekf.initialize(originLat, originLon, originAlt, initialAccuracyMeters = 2.0f)

        // Settle filter along North path for 10 seconds
        for (sec in 1..10) {
            ekf.predict(1.0, ActivityType.RUNNING, 0.0)
            val (lat, lon) = EnuProjection.inverse(0.0, sec * 3.0, originLat, originLon)
            ekf.updateGpsPosition(lat, lon, accuracyMeters = 2.0f)
        }

        val peBefore = ekf.state[0]
        val pnBefore = ekf.state[1]

        // Next second: Massive +60m East multipath spike!
        ekf.predict(1.0, ActivityType.RUNNING, 0.0)
        val (spikeLat, spikeLon) = EnuProjection.inverse(60.0, 33.0, originLat, originLon)
        val result = ekf.updateGpsPosition(spikeLat, spikeLon, accuracyMeters = 2.0f)

        // Must reject outlier
        assertThat(result).isEqualTo(GpsUpdateResult.REJECTED_OUTLIER)

        // Trajectory must NOT be dragged +60m East!
        val peAfter = ekf.state[0]
        assertThat(abs(peAfter - peBefore)).isLessThan(1.0)
    }

    @Test
    fun testTurnPreservationWhenGyroConfirmsTurning() {
        val ekf = EkfStateEstimator()
        ekf.initialize(originLat, originLon, originAlt, initialAccuracyMeters = 2.0f)

        // Run North for 10s at 3.0 m/s
        for (sec in 1..10) {
            ekf.predict(1.0, ActivityType.RUNNING, 0.0)
            val (lat, lon) = EnuProjection.inverse(0.0, sec * 3.0, originLat, originLon)
            ekf.updateGpsPosition(lat, lon, accuracyMeters = 2.0f)
        }

        // At second 11: sharp 90-degree turn East with IMU confirming turn
        val gyroYawRate = 1.7 // rad/s (~97 deg/s)
        ekf.predict(1.0, ActivityType.RUNNING, gyroYawRateRadPerSec = gyroYawRate)

        // GPS fix turns 90 degrees East (+4m East, +0.5m North)
        val (turnLat, turnLon) = EnuProjection.inverse(4.0, 30.5, originLat, originLon)
        val result = ekf.updateGpsPosition(turnLat, turnLon, accuracyMeters = 2.5f)

        // Gating threshold was widened by gyro; turn measurement is ACCEPTED!
        assertThat(result).isEqualTo(GpsUpdateResult.ACCEPTED)
    }

    @Test
    fun testFiveConsecutiveOutliersTriggerSoftReanchor() {
        val ekf = EkfStateEstimator()
        ekf.initialize(originLat, originLon, originAlt, initialAccuracyMeters = 2.0f)

        // Run North for 5s
        for (sec in 1..5) {
            ekf.predict(1.0, ActivityType.RUNNING, 0.0)
            val (lat, lon) = EnuProjection.inverse(0.0, sec * 3.0, originLat, originLon)
            ekf.updateGpsPosition(lat, lon, accuracyMeters = 2.0f)
        }

        // Now simulate entering a new street after tunnel: 5 persistent outliers at (+80m East, +50m North)
        val (jumpLat, jumpLon) = EnuProjection.inverse(80.0, 50.0, originLat, originLon)

        for (i in 1..4) {
            ekf.predict(1.0, ActivityType.RUNNING, 0.0)
            val res = ekf.updateGpsPosition(jumpLat, jumpLon, 2.0f)
            assertThat(res).isEqualTo(GpsUpdateResult.REJECTED_OUTLIER)
        }

        // 5th fix must trigger re-anchor
        ekf.predict(1.0, ActivityType.RUNNING, 0.0)
        val fifthRes = ekf.updateGpsPosition(jumpLat, jumpLon, 2.0f)
        assertThat(fifthRes).isEqualTo(GpsUpdateResult.REANCHORED)

        // State vector should now be re-anchored to the new location
        assertThat(ekf.state[0]).isWithin(0.1).of(80.0)
        assertThat(ekf.state[1]).isWithin(0.1).of(50.0)

        // Distance accumulated during the initial 5 seconds was ~15m
        // Discontinuous re-anchor must NOT commit the ~87m teleport jump to accepted workout distance!
        val stateAfterReanchor = ekf.getFusedState()
        assertThat(stateAfterReanchor.acceptedDistance).isLessThan(25.0)

        // Next legitimate step: 3m North from new anchor (80.0, 53.0)
        val (nextLat, nextLon) = EnuProjection.inverse(80.0, 53.0, originLat, originLon)
        ekf.predict(1.0, ActivityType.RUNNING, 0.0)
        val nextRes = ekf.updateGpsPosition(nextLat, nextLon, 2.0f)
        assertThat(nextRes).isEqualTo(GpsUpdateResult.ACCEPTED)

        // Subsequent legitimate motion is cleanly accumulated without losing steps
        val stateAfterNext = ekf.getFusedState()
        assertThat(stateAfterNext.filteredDistance).isGreaterThan(stateAfterReanchor.filteredDistance)
    }

    @Test
    fun testStationaryZeroVelocityUpdateSuppressesDriftAndFreezesDistance() {
        val ekf = EkfStateEstimator()
        ekf.initialize(originLat, originLon, originAlt, initialAccuracyMeters = 2.0f)

        // 1. Move North for 10s at 3.0 m/s
        var lastLat = originLat
        var lastLon = originLon
        for (sec in 1..10) {
            ekf.predict(1.0, ActivityType.RUNNING, 0.0)
            ekf.updateGpsVelocity(3.0f, 0.0f, 0.2f)
            val (lat, lon) = EnuProjection.inverse(0.0, sec * 3.0, originLat, originLon)
            lastLat = lat
            lastLon = lon
            ekf.updateGpsPosition(lat, lon, 2.0f)
        }

        val distanceBeforeStop = ekf.getFusedState().acceptedDistance
        assertThat(distanceBeforeStop).isGreaterThan(20.0)

        // 2. Stop at red light: stationary Doppler speed (0.0 m/s) with GPS jitter around (0, 30m)
        for (sec in 11..25) {
            ekf.predict(1.0, ActivityType.RUNNING, 0.0)
            ekf.updateGpsVelocity(0.0f, 0.0f, 0.1f)
            // GPS position wanders by +/- 0.5m
            val jitter = if (sec % 2 == 0) 0.5 else -0.5
            val (jitLat, jitLon) = EnuProjection.inverse(jitter, 30.0 + jitter, originLat, originLon)
            ekf.updateGpsPosition(jitLat, jitLon, 2.0f)
        }

        val stateAfterStop = ekf.getFusedState()
        assertThat(stateAfterStop.fsmState).isEqualTo(MotionState.STOPPED)
        assertThat(stateAfterStop.speedMps).isEqualTo(0.0)
        assertThat(ekf.state[2]).isEqualTo(0.0)
        assertThat(ekf.state[3]).isEqualTo(0.0)

        // Stationary jitter must NOT inflate accepted activity distance
        assertThat(stateAfterStop.acceptedDistance).isWithin(0.01).of(distanceBeforeStop)
    }

    @Test
    fun testMovingDopplerVelocityUpdateAnchorsKinematics() {
        val ekf = EkfStateEstimator()
        ekf.initialize(originLat, originLon, originAlt, initialAccuracyMeters = 2.0f)

        ekf.predict(1.0, ActivityType.RUNNING, 0.0)
        // Doppler reports 5.0 m/s East (bearing 90 deg)
        ekf.updateGpsVelocity(5.0f, 90.0f, 0.15f)

        assertThat(ekf.state[2]).isGreaterThan(3.0) // ve ~ 5.0
        assertThat(abs(ekf.state[3])).isLessThan(0.5) // vn ~ 0.0
    }

    @Test
    fun testNaNInputsRejectedGracefullyWithoutCorruptingFilter() {
        val ekf = EkfStateEstimator()
        ekf.initialize(originLat, originLon, originAlt, initialAccuracyMeters = 2.0f)

        // NaN predict dt
        ekf.predict(Double.NaN, ActivityType.RUNNING, Double.NaN)
        assertThat(ekf.state[0].isNaN()).isFalse()
        assertThat(ekf.covariance[0, 0].isNaN()).isFalse()

        // NaN GPS position rejected
        val res = ekf.updateGpsPosition(Double.NaN, originLon, 2.0f)
        assertThat(res).isEqualTo(GpsUpdateResult.REJECTED_OUTLIER)

        // NaN GPS accuracy rejected
        val resAcc = ekf.updateGpsPosition(originLat, originLon, Float.NaN)
        assertThat(resAcc).isEqualTo(GpsUpdateResult.REJECTED_OUTLIER)

        // NaN Velocity ignored without crash or corruption
        ekf.updateGpsVelocity(Float.NaN, 0f, 0.1f)
        assertThat(ekf.state[2].isNaN()).isFalse()

        // Fused state remains clean and valid
        val fused = ekf.getFusedState()
        assertThat(fused.lat.isNaN()).isFalse()
        assertThat(fused.lon.isNaN()).isFalse()
        assertThat(fused.speedMps.isNaN()).isFalse()
    }
}
