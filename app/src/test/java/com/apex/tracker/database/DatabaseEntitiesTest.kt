package com.apex.tracker.database

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DatabaseEntitiesTest {

    @Test
    fun testWorkoutEntity_defaultValuesAndCopy() {
        val workout = WorkoutEntity(
            id = "w-test-1",
            activityType = "RUNNING",
            startTimeEpochMs = 1000L,
            status = "RECORDING"
        )

        assertThat(workout.endTimeEpochMs).isNull()
        assertThat(workout.totalElapsedTimeMs).isEqualTo(0L)
        assertThat(workout.totalMovingTimeMs).isEqualTo(0L)
        assertThat(workout.totalDistanceMeters).isEqualTo(0.0)
        assertThat(workout.rawDistanceMeters).isEqualTo(0.0)
        assertThat(workout.filteredDistanceMeters).isEqualTo(0.0)
        assertThat(workout.totalElevationGainMeters).isEqualTo(0.0)
        assertThat(workout.totalElevationLossMeters).isEqualTo(0.0)
        assertThat(workout.status).isEqualTo("RECORDING")

        val completed = workout.copy(
            status = "COMPLETED",
            endTimeEpochMs = 5000L,
            totalDistanceMeters = 1000.0,
            totalMovingTimeMs = 4000L
        )
        assertThat(completed.status).isEqualTo("COMPLETED")
        assertThat(completed.endTimeEpochMs).isEqualTo(5000L)
        assertThat(completed.totalDistanceMeters).isEqualTo(1000.0)
        assertThat(completed.totalMovingTimeMs).isEqualTo(4000L)
    }

    @Test
    fun testTrackPointEntity_defaultValuesAndOutlierFlag() {
        val pt = TrackPointEntity(
            workoutId = "w-test-1",
            timestampEpochMs = 2000L,
            latitude = 37.77,
            longitude = -122.41,
            altitudeMeters = 10.0,
            speedMps = 2.5,
            bearingDegrees = 180f,
            accuracyMeters = 3.0f,
            rawLatitude = 37.771,
            rawLongitude = -122.411,
            rawAltitude = 9.8,
            rawSpeedMps = 2.6f,
            fsmState = "MOVING"
        )

        assertThat(pt.id).isEqualTo(0L)
        assertThat(pt.isOutlier).isFalse()

        val outlierPt = pt.copy(isOutlier = true)
        assertThat(outlierPt.isOutlier).isTrue()
    }

    @Test
    fun testSplitEntity_values() {
        val split = SplitEntity(
            workoutId = "w-test-1",
            splitIndex = 1,
            distanceMeters = 1000.0,
            elapsedTimeMs = 300000L,
            movingTimeMs = 290000L,
            paceSecondsPerKm = 290.0,
            elevationGainMeters = 15.0,
            elevationLossMeters = 5.0
        )

        assertThat(split.splitIndex).isEqualTo(1)
        assertThat(split.distanceMeters).isEqualTo(1000.0)
        assertThat(split.elapsedTimeMs).isEqualTo(300000L)
        assertThat(split.movingTimeMs).isEqualTo(290000L)
        assertThat(split.paceSecondsPerKm).isEqualTo(290.0)
        assertThat(split.elevationGainMeters).isEqualTo(15.0)
        assertThat(split.elevationLossMeters).isEqualTo(5.0)
    }

    @Test
    fun testActiveSessionCheckpointEntity_singletonId() {
        assertThat(ActiveSessionCheckpointEntity.SINGLETON_ID).isEqualTo(1)
    }
}
