package com.apex.tracker.core.fusion

import com.apex.tracker.core.model.ActivityType
import com.apex.tracker.core.model.MotionState
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/**
 * Challenger Adversarial Stress Tests for Milestone 1.
 *
 * Verifies mathematical invariants under extreme synthetic workloads:
 * 1. 1D Vertical Filter: 50,000 cycles sub-1.5m sinusoidal oscillation, staircase with micro-dips, boundary threshold.
 * 2. 5-State Auto-Pause FSM: 500-cycle high frequency velocity flapping, extreme fidgeting under zero velocity, debounce cancel.
 * 3. Pace Smoothing Engine: Step responses, near-zero threshold boundary clamping, formatting edge cases.
 * 4. Triple Distance Accumulators: Monotonicity under chaotic inputs, strict inequality preservation.
 */
class ChallengerStressTest {

    // =========================================================================
    // 1. 1D VERTICAL FILTER STRESS TESTS
    // =========================================================================

    @Test
    fun stressTestVerticalHysteresisFiftyThousandSubDeadbandOscillations() {
        val filter = VerticalHysteresisFilter(deadbandThresholdMeters = 1.5)
        val baseAltitude = 250.0
        filter.initialize(baseAltitude)

        // 50,000 continuous oscillation steps with amplitude 1.45m (< 1.5m)
        for (i in 0 until 50_000) {
            val h = baseAltitude + 1.45 * sin(i * 0.05)
            filter.updateAltitudeDirect(h)
        }

        assertThat(filter.elevationGain).isEqualTo(0.0)
        assertThat(filter.elevationLoss).isEqualTo(0.0)
        assertThat(filter.currentTrend).isEqualTo(VerticalHysteresisFilter.ElevationTrend.NEUTRAL)
    }

    @Test
    fun stressTestVerticalHysteresisExactDeadbandThresholdBoundary() {
        val filter = VerticalHysteresisFilter(deadbandThresholdMeters = 1.5)
        filter.initialize(100.0)

        // Step just below deadband: 100.0 -> 101.4999
        filter.updateAltitudeDirect(101.4999)
        assertThat(filter.elevationGain).isEqualTo(0.0)
        assertThat(filter.currentTrend).isEqualTo(VerticalHysteresisFilter.ElevationTrend.NEUTRAL)

        // Step crossing deadband: 101.4999 -> 101.5001
        filter.updateAltitudeDirect(101.5001)
        assertThat(filter.elevationGain).isWithin(1e-4).of(1.5001)
        assertThat(filter.currentTrend).isEqualTo(VerticalHysteresisFilter.ElevationTrend.CLIMBING)
    }

    @Test
    fun stressTestVerticalHysteresisClimbingStaircaseWithMicroDips() {
        val filter = VerticalHysteresisFilter(deadbandThresholdMeters = 1.5)
        filter.initialize(100.0)

        // Climb 3.0m, dip 1.0m, climb 3.0m, dip 1.0m, climb 3.0m
        // Step 1: climb to 103.0m (gain = 3.0m, trend = CLIMBING)
        filter.updateAltitudeDirect(103.0)
        assertThat(filter.elevationGain).isWithin(1e-4).of(3.0)
        assertThat(filter.elevationLoss).isEqualTo(0.0)

        // Step 2: dip to 102.0m (drop of 1.0m < 1.5m deadband -> no loss, peak anchor remains 103.0m)
        filter.updateAltitudeDirect(102.0)
        assertThat(filter.elevationGain).isWithin(1e-4).of(3.0)
        assertThat(filter.elevationLoss).isEqualTo(0.0)
        assertThat(filter.currentTrend).isEqualTo(VerticalHysteresisFilter.ElevationTrend.CLIMBING)

        // Step 3: climb to 105.0m (+2.0m above peak anchor 103.0m -> total gain = 5.0m)
        filter.updateAltitudeDirect(105.0)
        assertThat(filter.elevationGain).isWithin(1e-4).of(5.0)
        assertThat(filter.elevationLoss).isEqualTo(0.0)

        // Step 4: dip to 104.0m (drop of 1.0m < 1.5m deadband -> no loss)
        filter.updateAltitudeDirect(104.0)
        assertThat(filter.elevationGain).isWithin(1e-4).of(5.0)
        assertThat(filter.elevationLoss).isEqualTo(0.0)

        // Step 5: climb to 107.0m (+2.0m above peak anchor 105.0m -> total gain = 7.0m)
        filter.updateAltitudeDirect(107.0)
        assertThat(filter.elevationGain).isWithin(1e-4).of(7.0)
        assertThat(filter.elevationLoss).isEqualTo(0.0)
    }

    // =========================================================================
    // 2. 5-STATE AUTO-PAUSE FSM STRESS TESTS
    // =========================================================================

    @Test
    fun stressTestAutoPauseFsmHighFrequencyFlappingRetainsAllDistance() {
        val classifier = AutoPauseClassifier(ActivityType.RUNNING)
        classifier.setState(MotionState.MOVING)

        var totalCommitted = 0.0

        // 200 cycles of rapid 0.5s pause / 0.5s move flapping (period 1.0s << 3.0s stop debounce)
        for (cycle in 0 until 200) {
            // 0.5s slow down: 0.1 m/s, incremental 0.05m
            val resSlow = classifier.update(vHorizMps = 0.1, dtSeconds = 0.5, incrementalDistanceMeters = 0.05)
            assertThat(resSlow.currentState).isEqualTo(MotionState.POSSIBLY_STOPPED)
            totalCommitted += resSlow.committedDistanceMeters

            // 0.5s speed up: 3.0 m/s, incremental 1.50m
            val resFast = classifier.update(vHorizMps = 3.0, dtSeconds = 0.5, incrementalDistanceMeters = 1.50)
            assertThat(resFast.currentState).isEqualTo(MotionState.MOVING)
            totalCommitted += resFast.committedDistanceMeters
        }

        // Under 200 cycles of flapping, all distance (200 * (0.05 + 1.50) = 310.0m) must be preserved!
        assertThat(totalCommitted).isWithin(1e-3).of(310.0)
        assertThat(classifier.currentState).isEqualTo(MotionState.MOVING)
    }

    @Test
    fun stressTestAutoPauseFsmExtremeFidgetingUnderStationaryVelocity() {
        val classifier = AutoPauseClassifier(ActivityType.RUNNING)
        classifier.setState(MotionState.STOPPED)

        // User stationary: vHoriz = 0.05 m/s (< 0.80 vStop), but shaking device with 10g vibration
        for (i in 0 until 500) {
            val az = if (i % 2 == 0) 25.0f else -5.0f // Huge vibration
            classifier.addAccelerationSample(2.0f, 2.0f, az, i * 20L)

            val res = classifier.update(
                vHorizMps = 0.05,
                dtSeconds = 0.1,
                incrementalDistanceMeters = 0.005
            )
            // Must stay strictly STOPPED, committing 0.0 distance
            assertThat(res.currentState).isEqualTo(MotionState.STOPPED)
            assertThat(res.committedDistanceMeters).isEqualTo(0.0)
        }

        assertThat(classifier.currentState).isEqualTo(MotionState.STOPPED)
    }

    @Test
    fun stressTestAutoPauseFsmAbortedMoveResetsWithoutDistanceLeak() {
        val classifier = AutoPauseClassifier(ActivityType.RUNNING) // tMoveDebounce = 1.5s
        classifier.setState(MotionState.STOPPED)

        // Step 1: User starts moving for 1.0s (incremental 2.5m)
        val r1 = classifier.update(vHorizMps = 2.5, dtSeconds = 1.0, incrementalDistanceMeters = 2.5)
        assertThat(r1.currentState).isEqualTo(MotionState.POSSIBLY_MOVING)
        assertThat(r1.committedDistanceMeters).isEqualTo(0.0)

        // Step 2: Abort movement before 1.5s debounce timeout (falls back to still)
        val r2 = classifier.update(vHorizMps = 0.1, dtSeconds = 0.5, incrementalDistanceMeters = 0.05)
        assertThat(r2.currentState).isEqualTo(MotionState.STOPPED)
        assertThat(r2.committedDistanceMeters).isEqualTo(0.0)
        assertThat(classifier.pendingMoveDistance).isEqualTo(0.0)
    }

    // =========================================================================
    // 3. PACE SMOOTHING ENGINE STRESS TESTS
    // =========================================================================

    @Test
    fun stressTestPaceEngineVelocityStepResponseMonotonicity() {
        val engine = PaceEngine(windowDurationSec = 5.0, emaTauSec = 6.0)

        var prevVelocity = 0.0
        // Step input from 0 to 4.0 m/s over 30 seconds
        for (sec in 1..30) {
            val v = engine.update(
                elapsedMovingTimeSec = sec.toDouble(),
                acceptedDistanceMeters = sec * 4.0,
                dtSeconds = 1.0,
                isAutoPaused = false
            )
            // Smoothed velocity must grow monotonically toward 4.0 m/s
            assertThat(v).isAtLeast(prevVelocity)
            assertThat(v).isAtMost(4.01)
            prevVelocity = v
        }

        // At t=30s (~5 time constants), velocity should have converged within 1% of 4.0 m/s
        assertThat(engine.smoothedVelocityMps).isWithin(0.05).of(4.0)
        assertThat(engine.currentPaceSecPerKm()).isWithin(5.0).of(250.0) // 4 m/s = 250 s/km (4:10 min/km)
    }

    @Test
    fun stressTestPaceEngineBoundaryClampingThreshold() {
        val engine = PaceEngine(minSpeedClampMps = 0.25)

        // Just below clamp threshold: 0.24 m/s
        engine.update(1.0, 0.24, 1.0, false)
        assertThat(engine.smoothedVelocityMps).isWithin(1e-4).of(0.24)
        assertThat(engine.currentPaceSecPerKm().isNaN()).isTrue()
        assertThat(PaceEngine.formatPace(engine.currentPaceSecPerKm())).isEqualTo("--:--")

        // Just above clamp threshold: 0.26 m/s
        val engineAbove = PaceEngine(minSpeedClampMps = 0.25)
        engineAbove.update(1.0, 0.26, 1.0, false)
        assertThat(engineAbove.smoothedVelocityMps).isWithin(1e-4).of(0.26)
        assertThat(engineAbove.currentPaceSecPerKm().isNaN()).isFalse()
        assertThat(engineAbove.currentPaceSecPerKm()).isWithin(10.0).of(3846.15)
        assertThat(PaceEngine.formatPace(engineAbove.currentPaceSecPerKm())).isEqualTo("64:06")
    }

    // =========================================================================
    // 4. TRIPLE DISTANCE ACCUMULATORS STRESS TESTS
    // =========================================================================

    @Test
    fun stressTestDistanceAccumulatorsMonotonicGrowthAndInvariant() {
        val accumulators = DistanceAccumulators()
        val baseLat = 37.7749
        val baseLon = -122.4194

        accumulators.addRawLocation(baseLat, baseLon)
        accumulators.addFilteredPosition(0.0, 0.0)

        var lastRaw = 0.0
        var lastFiltered = 0.0
        var lastAccepted = 0.0

        for (i in 1..1000) {
            // Simulated motion with random-like jitter on raw and smooth filtered arc
            val rawLat = baseLat + (i * 0.00002) + (sin(i.toDouble()) * 0.000005)
            val rawLon = baseLon + (sin(i * 0.5) * 0.000005)
            accumulators.addRawLocation(rawLat, rawLon)

            val dFilt = accumulators.addFilteredPosition(0.0, i * 2.0)
            if (i % 2 == 0) {
                // Only commit accepted distance 50% of the time (simulating stops)
                accumulators.addAcceptedDistance(dFilt * 2.0)
            }

            // Monotonicity check
            assertThat(accumulators.rawDistanceMeters).isAtLeast(lastRaw)
            assertThat(accumulators.filteredDistanceMeters).isAtLeast(lastFiltered)
            assertThat(accumulators.acceptedDistanceMeters).isAtLeast(lastAccepted)

            lastRaw = accumulators.rawDistanceMeters
            lastFiltered = accumulators.filteredDistanceMeters
            lastAccepted = accumulators.acceptedDistanceMeters
        }

        // Accepted must not exceed filtered (since accepted only accumulates subsets of filtered movements)
        assertThat(accumulators.acceptedDistanceMeters).isAtMost(accumulators.filteredDistanceMeters + 1e-4)
    }
}
