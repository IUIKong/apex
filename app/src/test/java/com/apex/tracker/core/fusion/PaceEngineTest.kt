package com.apex.tracker.core.fusion

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PaceEngineTest {

    @Test
    fun testNearZeroSpeedClampsToDashFormat() {
        val engine = PaceEngine()
        // Low velocity: 0.1 m/s
        engine.update(elapsedMovingTimeSec = 1.0, acceptedDistanceMeters = 0.1, dtSeconds = 1.0, isAutoPaused = false)

        val pace = engine.currentPaceSecPerKm()
        assertThat(pace.isNaN()).isTrue()
        assertThat(PaceEngine.formatPace(pace)).isEqualTo("--:--")
    }

    @Test
    fun testAutoPauseClampsToDashFormat() {
        val engine = PaceEngine()
        // Running at 3.0 m/s
        engine.update(elapsedMovingTimeSec = 5.0, acceptedDistanceMeters = 15.0, dtSeconds = 1.0, isAutoPaused = false)
        assertThat(engine.currentPaceSecPerKm().isNaN()).isFalse()

        // Auto pause engaged
        engine.update(elapsedMovingTimeSec = 5.0, acceptedDistanceMeters = 15.0, dtSeconds = 1.0, isAutoPaused = true)
        val pace = engine.currentPaceSecPerKm(isAutoPaused = true)
        assertThat(pace.isNaN()).isTrue()
        assertThat(PaceEngine.formatPace(pace)).isEqualTo("--:--")
    }

    @Test
    fun testPaceFormattingMetricAndImperial() {
        val engine = PaceEngine()

        // 3.3333 m/s = 12 km/h -> exactly 300 seconds per km (5:00 /km)
        for (i in 1..10) {
            engine.update(
                elapsedMovingTimeSec = i * 1.0,
                acceptedDistanceMeters = i * (10.0 / 3.0),
                dtSeconds = 1.0,
                isAutoPaused = false
            )
        }

        val paceKm = engine.currentPaceSecPerKm()
        assertThat(paceKm).isWithin(1.0).of(300.0)
        assertThat(PaceEngine.formatPace(paceKm)).isEqualTo("5:00")

        // In miles: 300 * 1.609344 = 482.8s = 8 min 2 sec (8:02 /mi)
        val paceMi = engine.currentPaceSecPerMile()
        assertThat(PaceEngine.formatPace(paceMi)).isEqualTo("8:02")
    }

    @Test
    fun testPaceRecoveryAfterAutoPauseDoesNotUseLifetimeAverageSpeed() {
        val engine = PaceEngine()
        // Fast runner: 5.0 m/s for 1000s (5,000m total)
        for (i in 1..20) {
            engine.update(
                elapsedMovingTimeSec = i * 50.0,
                acceptedDistanceMeters = i * 250.0,
                dtSeconds = 1.0,
                isAutoPaused = false
            )
        }
        assertThat(engine.smoothedVelocityMps).isGreaterThan(4.5)

        // Athlete pauses at water stop
        engine.update(
            elapsedMovingTimeSec = 1000.0,
            acceptedDistanceMeters = 5000.0,
            dtSeconds = 1.0,
            isAutoPaused = true
        )
        assertThat(engine.smoothedVelocityMps).isEqualTo(0.0)

        // Resumes running at an easy jog: 2.0 m/s (1001s, 5002.0m)
        val resumeSpeed = engine.update(
            elapsedMovingTimeSec = 1001.0,
            acceptedDistanceMeters = 5002.0,
            dtSeconds = 1.0,
            isAutoPaused = false
        )

        // Velocity must reflect the new 2.0 m/s jog, NOT the 5.0 m/s lifetime average!
        assertThat(resumeSpeed).isWithin(0.2).of(2.0)
    }
}
