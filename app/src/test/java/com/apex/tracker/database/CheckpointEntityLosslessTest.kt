package com.apex.tracker.database

import com.apex.tracker.core.model.ActivityType
import com.apex.tracker.core.model.MotionState
import com.apex.tracker.core.model.SessionSnapshot
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CheckpointEntityLosslessTest {

    @Test
    fun testSessionSnapshotToEntityAndBack_losslessRestoration() {
        val originalSnapshot = SessionSnapshot(
            workoutId = "workout-uuid-1234",
            activityType = ActivityType.RUNNING,
            startTimeEpochMs = 1727956800000L,
            lastCheckpointEpochMs = 1727958600000L,
            originLat = 37.7749295,
            originLon = -122.4194155,
            originAlt = 45.2,
            stateVector = doubleArrayOf(12.3456789, -98.7654321, 3.456, 0.123, 0.05, -0.02),
            covarianceDiagonals = doubleArrayOf(1.23, 1.25, 0.45, 0.48, 0.05, 0.05),
            rawDistanceMeters = 5234.56,
            filteredDistanceMeters = 5210.12,
            acceptedDistanceMeters = 5195.80,
            elapsedTimeMs = 1800000L,
            movingTimeMs = 1750000L,
            fsmState = MotionState.MOVING,
            elevationGainMeters = 125.5,
            elevationLossMeters = 80.2,
            elevationAnchorMeters = 168.0,
            elevationTrend = "CLIMBING",
            currentSplitDistanceMeters = 234.56
        )

        val entity = ActiveSessionCheckpointEntity.fromSessionSnapshot(originalSnapshot)
        val restoredSnapshot = entity.toSessionSnapshot()

        assertThat(restoredSnapshot.workoutId).isEqualTo(originalSnapshot.workoutId)
        assertThat(restoredSnapshot.activityType).isEqualTo(originalSnapshot.activityType)
        assertThat(restoredSnapshot.startTimeEpochMs).isEqualTo(originalSnapshot.startTimeEpochMs)
        assertThat(restoredSnapshot.lastCheckpointEpochMs).isEqualTo(originalSnapshot.lastCheckpointEpochMs)
        assertThat(restoredSnapshot.originLat).isEqualTo(originalSnapshot.originLat)
        assertThat(restoredSnapshot.originLon).isEqualTo(originalSnapshot.originLon)
        assertThat(restoredSnapshot.originAlt).isEqualTo(originalSnapshot.originAlt)
        assertThat(restoredSnapshot.stateVector).usingTolerance(1e-9).containsExactly(originalSnapshot.stateVector)
        assertThat(restoredSnapshot.covarianceDiagonals).usingTolerance(1e-9).containsExactly(originalSnapshot.covarianceDiagonals)
        assertThat(restoredSnapshot.rawDistanceMeters).isEqualTo(originalSnapshot.rawDistanceMeters)
        assertThat(restoredSnapshot.filteredDistanceMeters).isEqualTo(originalSnapshot.filteredDistanceMeters)
        assertThat(restoredSnapshot.acceptedDistanceMeters).isEqualTo(originalSnapshot.acceptedDistanceMeters)
        assertThat(restoredSnapshot.elapsedTimeMs).isEqualTo(originalSnapshot.elapsedTimeMs)
        assertThat(restoredSnapshot.movingTimeMs).isEqualTo(originalSnapshot.movingTimeMs)
        assertThat(restoredSnapshot.fsmState).isEqualTo(originalSnapshot.fsmState)
        assertThat(restoredSnapshot.elevationGainMeters).isEqualTo(originalSnapshot.elevationGainMeters)
        assertThat(restoredSnapshot.elevationLossMeters).isEqualTo(originalSnapshot.elevationLossMeters)
        assertThat(restoredSnapshot.elevationAnchorMeters).isEqualTo(originalSnapshot.elevationAnchorMeters)
        assertThat(restoredSnapshot.elevationTrend).isEqualTo(originalSnapshot.elevationTrend)
        assertThat(restoredSnapshot.currentSplitDistanceMeters).isEqualTo(originalSnapshot.currentSplitDistanceMeters)
        assertThat(restoredSnapshot).isEqualTo(originalSnapshot)
    }

    @Test
    fun testDoubleArrayFormatAndParse_handlesEmptyAndNaN() {
        val emptyArr = DoubleArray(0)
        val json = ActiveSessionCheckpointEntity.formatDoubleArray(emptyArr)
        assertThat(json).isEqualTo("[]")
        val parsed = ActiveSessionCheckpointEntity.parseDoubleArray(json)
        assertThat(parsed).isEmpty()

        val arr = doubleArrayOf(0.0, -1.0, 3.141592653589793)
        val json2 = ActiveSessionCheckpointEntity.formatDoubleArray(arr)
        val parsed2 = ActiveSessionCheckpointEntity.parseDoubleArray(json2)
        assertThat(parsed2).usingTolerance(1e-12).containsExactly(arr)
    }
}
