package com.apex.tracker.database

import com.apex.tracker.core.model.ActivityType
import com.apex.tracker.core.model.MotionState
import com.apex.tracker.core.model.SessionSnapshot
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RoomCheckpointRepositoryIntegrationTest {

    @Test
    fun testRoomCheckpointRepository_saveAndRetrieveWithMockDaos() = runTest {
        val fakeCheckpointDao = FakeCheckpointDao()
        val fakeTrackPointDao = FakeTrackPointDao()

        val repo = RoomCheckpointRepository(
            checkpointDao = fakeCheckpointDao,
            trackPointDao = fakeTrackPointDao
        )

        val snapshot = SessionSnapshot(
            workoutId = "workout-wal-1",
            activityType = ActivityType.HIKING,
            startTimeEpochMs = 1000L,
            lastCheckpointEpochMs = 2000L,
            originLat = 45.0,
            originLon = 10.0,
            originAlt = 500.0,
            stateVector = doubleArrayOf(0.0, 0.0, 1.0, 1.0, 0.0, 0.0),
            covarianceDiagonals = doubleArrayOf(1.0, 1.0, 0.1, 0.1, 0.01, 0.01),
            rawDistanceMeters = 100.0,
            filteredDistanceMeters = 99.0,
            acceptedDistanceMeters = 98.0,
            elapsedTimeMs = 50000L,
            movingTimeMs = 45000L,
            fsmState = MotionState.MOVING,
            elevationGainMeters = 10.0,
            elevationLossMeters = 2.0,
            elevationAnchorMeters = 508.0,
            elevationTrend = "CLIMBING",
            currentSplitDistanceMeters = 98.0
        )

        val points = listOf(
            TrackPointEntity(
                id = 1L,
                workoutId = "workout-wal-1",
                timestampEpochMs = 1500L,
                latitude = 45.0001,
                longitude = 10.0001,
                altitudeMeters = 505.0,
                speedMps = 1.2,
                bearingDegrees = 30.0f,
                accuracyMeters = 4.0f,
                rawLatitude = 45.0001,
                rawLongitude = 10.0001,
                rawAltitude = 505.0,
                rawSpeedMps = 1.2f,
                fsmState = "MOVING"
            )
        )

        // Save checkpoint
        repo.saveCheckpoint(snapshot, points)

        // Retrieve active checkpoint
        val retrieved = repo.getActiveCheckpoint()
        assertThat(retrieved).isNotNull()
        assertThat(retrieved).isEqualTo(snapshot)

        // Verify points inserted
        val storedPoints = fakeTrackPointDao.getTrackPointsForWorkout("workout-wal-1")
        assertThat(storedPoints).hasSize(1)
        assertThat(storedPoints[0].altitudeMeters).isEqualTo(505.0)

        // Clear active checkpoint
        repo.clearActiveCheckpoint("workout-wal-1")
        assertThat(repo.getActiveCheckpoint()).isNull()
    }
}
