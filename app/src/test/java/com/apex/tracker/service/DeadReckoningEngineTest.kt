package com.apex.tracker.service

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.PI

class DeadReckoningEngineTest {

    @Test
    fun testInitialStateIsNormalGnss() {
        val engine = DeadReckoningEngine()
        assertThat(engine.currentPhase).isEqualTo(DeadReckoningPhase.NORMAL_GNSS)

        val step = engine.update(currentTimestampMs = 1000L, dtSeconds = 1.0)
        assertThat(step.phase).isEqualTo(DeadReckoningPhase.NORMAL_GNSS)
        assertThat(step.stepDistanceMeters).isEqualTo(0.0)
        assertThat(step.speedMps).isEqualTo(0.0)
    }

    @Test
    fun testGpsFixAnchorsEngineAndComputesHeading() {
        val engine = DeadReckoningEngine()
        val t0 = 10_000L
        // Moving East: velEast = 4.0, velNorth = 0.0 -> heading = PI / 2
        engine.onGpsFix(timestampMs = t0, velEast = 4.0, velNorth = 0.0)

        // 500ms after GPS fix (within 1.0s normal window)
        val step = engine.update(currentTimestampMs = t0 + 500L, dtSeconds = 0.5)
        assertThat(step.phase).isEqualTo(DeadReckoningPhase.NORMAL_GNSS)
        assertThat(step.deltaEastMeters).isEqualTo(0.0)
        assertThat(step.deltaNorthMeters).isEqualTo(0.0)
        assertThat(step.headingRad).isWithin(0.01).of(PI / 2.0)
        assertThat(step.speedMps).isWithin(0.01).of(4.0)
    }

    @Test
    fun testKinematicExtrapolationUnder15Seconds() {
        val engine = DeadReckoningEngine()
        val t0 = 10_000L
        engine.onGpsFix(timestampMs = t0, velEast = 3.0, velNorth = 4.0)

        // 3.0s after GPS fix (in kinematic window: >1s and <=15s)
        val step = engine.update(currentTimestampMs = t0 + 3_000L, dtSeconds = 1.0)
        assertThat(step.phase).isEqualTo(DeadReckoningPhase.KINEMATIC_EXTRAPOLATION)
        assertThat(step.stepDistanceMeters).isGreaterThan(0.0)
        assertThat(step.speedMps).isGreaterThan(0.0)
        // Position delta should be positive in both East and North
        assertThat(step.deltaEastMeters).isGreaterThan(0.0)
        assertThat(step.deltaNorthMeters).isGreaterThan(0.0)
    }

    @Test
    fun testKinematicAccelerationDampingPreventsExplosion() {
        val engine = DeadReckoningEngine()
        val t0 = 10_000L
        // Extreme initial acceleration of 10.0 m/s^2 East
        engine.onGpsFix(timestampMs = t0, velEast = 2.0, velNorth = 0.0, accEast = 10.0, accNorth = 0.0)

        var totalEast = 0.0
        // Advance 10 steps of 1.0s each
        for (i in 1..10) {
            val step = engine.update(currentTimestampMs = t0 + (i * 1000L) + 1000L, dtSeconds = 1.0)
            assertThat(step.phase).isEqualTo(DeadReckoningPhase.KINEMATIC_EXTRAPOLATION)
            totalEast += step.deltaEastMeters
        }

        // Without damping, 10s of 10m/s^2 would produce > 500m drift!
        // With damping, position should remain reasonably bounded (< 150m)
        assertThat(totalEast).isLessThan(150.0)
        assertThat(totalEast).isGreaterThan(10.0)
    }

    @Test
    fun testGyroYawHeadingIntegrationDuringDropout() {
        val engine = DeadReckoningEngine()
        val t0 = 10_000L
        // Heading strictly North (0 rad)
        engine.onGpsFix(timestampMs = t0, velEast = 0.0, velNorth = 5.0, bearingDegrees = 0.0f)

        // Rotate by 0.5 rad/s clockwise for 2 seconds
        engine.onGyroUpdate(yawRateRadPerSec = 0.5, dtSeconds = 2.0)

        val step = engine.update(currentTimestampMs = t0 + 3000L, dtSeconds = 1.0)
        assertThat(step.phase).isEqualTo(DeadReckoningPhase.KINEMATIC_EXTRAPOLATION)
        assertThat(step.headingRad).isWithin(0.05).of(1.0)
    }

    @Test
    fun testStepFrequencyHeadingBetween15And30Seconds() {
        val engine = DeadReckoningEngine(defaultStepLengthMeters = 0.8)
        val t0 = 10_000L
        // Heading East (PI / 2)
        engine.onGpsFix(timestampMs = t0, velEast = 3.0, velNorth = 0.0, bearingDegrees = 90.0f)

        // Advance to 20s of dropout (in [15s, 30s] step-heading window)
        val tCurrent = t0 + 20_000L

        // Simulate accelerometer footstrike (peak above 1.8 m/s^2 relative to gravity)
        val stepDetected = engine.onAccelerometerSample(ax = 0f, ay = 0f, az = 12.5f, timestampMs = tCurrent)
        assertThat(stepDetected).isTrue()

        val step = engine.update(currentTimestampMs = tCurrent, dtSeconds = 1.0)
        assertThat(step.phase).isEqualTo(DeadReckoningPhase.STEP_FREQUENCY_HEADING)
        assertThat(step.stepsDetected).isEqualTo(1)
        assertThat(step.stepDistanceMeters).isWithin(0.01).of(0.8)
        // With heading East (90 deg), deltaEast should be ~0.8m, deltaNorth ~0.0m
        assertThat(step.deltaEastMeters).isWithin(0.05).of(0.8)
        assertThat(step.deltaNorthMeters).isWithin(0.05).of(0.0)
    }

    @Test
    fun testCutoffAfter30SecondsEnforcesZeroMotion() {
        val engine = DeadReckoningEngine()
        val t0 = 10_000L
        engine.onGpsFix(timestampMs = t0, velEast = 5.0, velNorth = 5.0)

        // 35 seconds of GPS dropout (> 30s cutoff)
        val tCurrent = t0 + 35_000L
        val step = engine.update(currentTimestampMs = tCurrent, dtSeconds = 1.0)

        assertThat(step.phase).isEqualTo(DeadReckoningPhase.CUTOFF)
        assertThat(step.deltaEastMeters).isEqualTo(0.0)
        assertThat(step.deltaNorthMeters).isEqualTo(0.0)
        assertThat(step.stepDistanceMeters).isEqualTo(0.0)
        assertThat(step.speedMps).isEqualTo(0.0)
    }

    @Test
    fun testReacquisitionOfGpsFixRestoresNormalGnss() {
        val engine = DeadReckoningEngine()
        val t0 = 10_000L
        engine.onGpsFix(timestampMs = t0, velEast = 3.0, velNorth = 0.0)

        // Cutoff at 40s dropout
        val tDropout = t0 + 40_000L
        val cutoffStep = engine.update(currentTimestampMs = tDropout, dtSeconds = 1.0)
        assertThat(cutoffStep.phase).isEqualTo(DeadReckoningPhase.CUTOFF)

        // GPS signal recovers at t = 45,000L
        val tRecover = t0 + 45_000L
        engine.onGpsFix(timestampMs = tRecover, velEast = 3.5, velNorth = 0.0)

        // 200ms after recovery
        val recoveredStep = engine.update(currentTimestampMs = tRecover + 200L, dtSeconds = 0.2)
        assertThat(recoveredStep.phase).isEqualTo(DeadReckoningPhase.NORMAL_GNSS)
        assertThat(recoveredStep.speedMps).isWithin(0.01).of(3.5)
    }
}
