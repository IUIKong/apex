package com.apex.tracker.core.fusion

import com.apex.tracker.core.model.ActivityType
import com.apex.tracker.core.model.MotionState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AutoPauseClassifierTest {

    @Test
    fun testInitializationToMoving() {
        val classifier = AutoPauseClassifier(ActivityType.RUNNING)
        assertThat(classifier.currentState).isEqualTo(MotionState.INITIALIZING)

        // Ingest dynamic running steps (ax oscillation around gravity)
        for (i in 0 until 75) {
            val az = if (i % 2 == 0) 12.0f else 7.6f
            classifier.addAccelerationSample(0.0f, 0.0f, az, i * 20L)
        }

        val res = classifier.update(
            vHorizMps = 3.0,
            dtSeconds = 1.0,
            incrementalDistanceMeters = 3.0,
            gpsAccuracyMeters = 4.0f
        )

        assertThat(res.currentState).isEqualTo(MotionState.MOVING)
        assertThat(res.stateChanged).isTrue()
        assertThat(res.committedDistanceMeters).isEqualTo(3.0)
    }

    @Test
    fun testStopDebounceCancellationWhenMotionResumesEarly() {
        val classifier = AutoPauseClassifier(ActivityType.RUNNING) // tStopDebounce = 3.0s
        classifier.setState(MotionState.MOVING)

        // Still condition: low speed and low accel energy
        val res1 = classifier.update(vHorizMps = 0.2, dtSeconds = 1.0, incrementalDistanceMeters = 0.2)
        assertThat(res1.currentState).isEqualTo(MotionState.POSSIBLY_STOPPED)

        // Another second of low speed (total 2.0s < 3.0s debounce)
        val res2 = classifier.update(vHorizMps = 0.2, dtSeconds = 1.0, incrementalDistanceMeters = 0.2)
        assertThat(res2.currentState).isEqualTo(MotionState.POSSIBLY_STOPPED)
        assertThat(classifier.pendingStopDistance).isWithin(1e-4).of(0.4)

        // Running resumes at t = 2.5s!
        val res3 = classifier.update(vHorizMps = 2.5, dtSeconds = 0.5, incrementalDistanceMeters = 1.25)
        assertThat(res3.currentState).isEqualTo(MotionState.MOVING)
        assertThat(res3.stateChanged).isTrue()
        // Committed distance must include buffered 0.4m plus new 1.25m = 1.65m!
        assertThat(res3.committedDistanceMeters).isWithin(1e-4).of(1.65)
    }

    @Test
    fun testStopDebounceExpiresAndEntersStopped() {
        val classifier = AutoPauseClassifier(ActivityType.RUNNING) // tStopDebounce = 3.0s
        classifier.setState(MotionState.MOVING)

        // Low motion for 3.5 seconds
        classifier.update(vHorizMps = 0.1, dtSeconds = 1.0, incrementalDistanceMeters = 0.1)
        classifier.update(vHorizMps = 0.1, dtSeconds = 1.0, incrementalDistanceMeters = 0.1)
        val res = classifier.update(vHorizMps = 0.1, dtSeconds = 1.5, incrementalDistanceMeters = 0.15)

        assertThat(res.currentState).isEqualTo(MotionState.STOPPED)
        assertThat(res.stateChanged).isTrue()
        // Debounce buffer must be discarded, zero committed distance
        assertThat(res.committedDistanceMeters).isEqualTo(0.0)
    }

    @Test
    fun testResumeDebounceConfirmsSustainedMovement() {
        val classifier = AutoPauseClassifier(ActivityType.RUNNING) // tMoveDebounce = 1.5s
        classifier.setState(MotionState.STOPPED)

        // Motion begins
        val res1 = classifier.update(vHorizMps = 2.5, dtSeconds = 1.0, incrementalDistanceMeters = 2.5)
        assertThat(res1.currentState).isEqualTo(MotionState.POSSIBLY_MOVING)
        assertThat(res1.committedDistanceMeters).isEqualTo(0.0)

        // Motion continues for another 1.0s (total 2.0s > 1.5s debounce)
        val res2 = classifier.update(vHorizMps = 2.5, dtSeconds = 1.0, incrementalDistanceMeters = 2.5)
        assertThat(res2.currentState).isEqualTo(MotionState.MOVING)
        assertThat(res2.stateChanged).isTrue()
        // Must retroactively commit full 5.0m
        assertThat(res2.committedDistanceMeters).isWithin(1e-4).of(5.0)
    }

    @Test
    fun testRingBufferWrapAroundAndSampleCount() {
        val classifier = AutoPauseClassifier(ActivityType.RUNNING)

        // Feed 200 samples spaced 10ms apart (total 2000ms)
        for (i in 0 until 200) {
            classifier.addAccelerationSample(0f, 0f, 9.80665f, i * 10L)
        }

        // Must be capped at 75 samples maximum
        assertThat(classifier.hasAccelerationSamples()).isTrue()
        val energy = classifier.computeAccelerometerEnergy()
        assertThat(energy).isWithin(1e-4).of(0.0)
    }

    @Test
    fun testEnergyCalculationDynamic() {
        val classifier = AutoPauseClassifier(ActivityType.RUNNING)
        // Stationary: 9.80665
        classifier.addAccelerationSample(0f, 0f, 9.80665f, 1000L)
        assertThat(classifier.computeAccelerometerEnergy()).isWithin(1e-4).of(0.0)

        // Dynamic motion (+2 m/s^2 above gravity)
        classifier.addAccelerationSample(0f, 0f, (9.80665 + 2.0).toFloat(), 1020L)
        val energy = classifier.computeAccelerometerEnergy()
        assertThat(energy).isWithin(1e-3).of(1.0)
    }
}
