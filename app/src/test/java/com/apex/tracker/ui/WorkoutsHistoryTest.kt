package com.apex.tracker.ui

import com.apex.tracker.database.WorkoutEntity
import com.apex.tracker.ui.state.UiFormatters
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WorkoutsHistoryTest {

    private val sampleWorkouts = listOf(
        WorkoutEntity(
            id = "w1",
            activityType = "RUNNING",
            startTimeEpochMs = 1728000000000L,
            endTimeEpochMs = 1728002880000L,
            totalDistanceMeters = 10240.0,
            totalMovingTimeMs = 2900000L,
            totalElapsedTimeMs = 3000000L,
            totalElevationGainMeters = 142.0,
            status = "COMPLETED"
        ),
        WorkoutEntity(
            id = "w2",
            activityType = "CYCLING",
            startTimeEpochMs = 1728010000000L,
            endTimeEpochMs = 1728013600000L,
            totalDistanceMeters = 24500.0,
            totalMovingTimeMs = 3600000L,
            totalElapsedTimeMs = 3700000L,
            totalElevationGainMeters = 210.0,
            status = "COMPLETED"
        ),
        WorkoutEntity(
            id = "w3",
            activityType = "HIKING",
            startTimeEpochMs = 1728020000000L,
            endTimeEpochMs = 1728027200000L,
            totalDistanceMeters = 6800.0,
            totalMovingTimeMs = 7200000L,
            totalElapsedTimeMs = 7400000L,
            totalElevationGainMeters = 350.0,
            status = "COMPLETED"
        )
    )

    @Test
    fun testSportFiltering() {
        val all = sampleWorkouts
        assertThat(all).hasSize(3)

        val runsOnly = sampleWorkouts.filter { it.activityType.equals("RUNNING", ignoreCase = true) }
        assertThat(runsOnly).hasSize(1)
        assertThat(runsOnly.first().id).isEqualTo("w1")

        val ridesOnly = sampleWorkouts.filter { it.activityType.equals("CYCLING", ignoreCase = true) }
        assertThat(ridesOnly).hasSize(1)
        assertThat(ridesOnly.first().id).isEqualTo("w2")

        val walksOnly = sampleWorkouts.filter { it.activityType.equals("WALKING", ignoreCase = true) }
        assertThat(walksOnly).isEmpty()

        val hikesOnly = sampleWorkouts.filter { it.activityType.equals("HIKING", ignoreCase = true) }
        assertThat(hikesOnly).hasSize(1)
    }

    @Test
    fun testVolumeTotalsCalculation() {
        val totalDistanceKm = sampleWorkouts.sumOf { it.totalDistanceMeters } / 1000.0
        assertThat(totalDistanceKm).isEqualTo(41.54)

        val totalMovingSec = sampleWorkouts.sumOf { it.totalMovingTimeMs } / 1000L
        assertThat(totalMovingSec).isEqualTo(13700L) // 2900 + 3600 + 7200 = 13700s

        val totalElevationGain = sampleWorkouts.sumOf { it.totalElevationGainMeters }
        assertThat(totalElevationGain).isEqualTo(702.0)
    }

    @Test
    fun testAveragePaceFormattingForStoredWorkouts() {
        val run = sampleWorkouts.first()
        val avgPaceSec = (run.totalMovingTimeMs / 1000.0) / (run.totalDistanceMeters / 1000.0)
        val formattedPace = UiFormatters.formatPace(avgPaceSec)

        // 2900s / 10.24km = 283.2s/km -> 4:43 /km
        assertThat(formattedPace).isEqualTo("04:43")
    }

    @Test
    fun testWorkoutDeletionSimulation() {
        val mutableList = sampleWorkouts.toMutableList()
        val toDelete = "w2"

        mutableList.removeAll { it.id == toDelete }
        assertThat(mutableList).hasSize(2)
        assertThat(mutableList.any { it.id == toDelete }).isFalse()
    }
}
