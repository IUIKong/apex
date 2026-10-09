package com.apex.tracker.database

import com.apex.tracker.core.model.ActivityType
import com.apex.tracker.core.model.MotionState
import com.apex.tracker.core.model.SessionSnapshot
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class SessionRecoveryManagerTest {

    private lateinit var fakeCheckpointDao: FakeCheckpointDao
    private lateinit var fakeTrackPointDao: FakeTrackPointDao
    private lateinit var fakeWorkoutDao: FakeWorkoutDao
    private lateinit var repository: CheckpointRepository
    private lateinit var recoveryManager: SessionRecoveryManager

    @Before
    fun setUp() {
        fakeCheckpointDao = FakeCheckpointDao()
        fakeTrackPointDao = FakeTrackPointDao()
        fakeWorkoutDao = FakeWorkoutDao()
        repository = RoomCheckpointRepository(
            checkpointDao = fakeCheckpointDao,
            trackPointDao = fakeTrackPointDao
        )
        recoveryManager = SessionRecoveryManager(
            checkpointRepository = repository,
            workoutDao = fakeWorkoutDao,
            trackPointDao = fakeTrackPointDao
        )
    }

    private fun sampleSnapshot(
        workoutId: String = "test-workout-1",
        lastCheckpointEpochMs: Long = 1000000L
    ): SessionSnapshot {
        return SessionSnapshot(
            workoutId = workoutId,
            activityType = ActivityType.RUNNING,
            startTimeEpochMs = 900000L,
            lastCheckpointEpochMs = lastCheckpointEpochMs,
            originLat = 37.7749,
            originLon = -122.4194,
            originAlt = 10.0,
            stateVector = doubleArrayOf(1.0, 2.0, 3.0, 4.0, 0.1, 0.2),
            covarianceDiagonals = doubleArrayOf(0.5, 0.5, 0.2, 0.2, 0.01, 0.01),
            rawDistanceMeters = 1500.0,
            filteredDistanceMeters = 1490.0,
            acceptedDistanceMeters = 1485.0,
            elapsedTimeMs = 600000L,
            movingTimeMs = 580000L,
            fsmState = MotionState.MOVING,
            elevationGainMeters = 25.0,
            elevationLossMeters = 15.0,
            elevationAnchorMeters = 35.0,
            elevationTrend = "CLIMBING",
            currentSplitDistanceMeters = 485.0
        )
    }

    @Test
    fun testNoActiveSession_returnsNoActiveSession() = runTest {
        val result = recoveryManager.checkAndRecoverSession(currentTimeEpochMs = 2000000L)
        assertThat(result).isInstanceOf(SessionRecoveryResult.NoActiveSession::class.java)
    }

    @Test
    fun testSaveCheckpointAndInstantLosslessRecovery_withinAllowedGap() = runTest {
        val snapshot = sampleSnapshot(lastCheckpointEpochMs = 1000000L)
        val recentPoints = listOf(
            TrackPointEntity(
                id = 1L,
                workoutId = "test-workout-1",
                timestampEpochMs = 999000L,
                latitude = 37.7750,
                longitude = -122.4195,
                altitudeMeters = 12.0,
                speedMps = 2.8,
                bearingDegrees = 90.0f,
                accuracyMeters = 3.5f,
                rawLatitude = 37.77501,
                rawLongitude = -122.41952,
                rawAltitude = 11.5,
                rawSpeedMps = 2.9f,
                fsmState = "MOVING"
            )
        )
        fakeWorkoutDao.insertWorkout(
            WorkoutEntity(
                id = "test-workout-1",
                activityType = "RUNNING",
                startTimeEpochMs = 900000L,
                status = "RECORDING"
            )
        )

        repository.saveCheckpoint(snapshot, recentPoints)

        // 5 minutes after checkpoint (within 30 minute gap)
        val now = 1000000L + 5 * 60 * 1000L
        val result = recoveryManager.checkAndRecoverSession(currentTimeEpochMs = now)

        assertThat(result).isInstanceOf(SessionRecoveryResult.Restored::class.java)
        val restored = result as SessionRecoveryResult.Restored
        assertThat(restored.snapshot).isEqualTo(snapshot)
        assertThat(restored.workout).isNotNull()
        assertThat(restored.workout?.id).isEqualTo("test-workout-1")
        assertThat(restored.restoredTrackPoints).hasSize(1)
        assertThat(restored.restoredTrackPoints[0].latitude).isEqualTo(37.7750)
    }

    @Test
    fun testColdRestart_sessionOlderThan30Minutes_returnsExpired() = runTest {
        val snapshot = sampleSnapshot(lastCheckpointEpochMs = 1000000L)
        repository.saveCheckpoint(snapshot, emptyList())

        // 35 minutes after checkpoint (exceeds 30 minute gap)
        val now = 1000000L + 35 * 60 * 1000L
        val result = recoveryManager.checkAndRecoverSession(currentTimeEpochMs = now)

        assertThat(result).isInstanceOf(SessionRecoveryResult.Expired::class.java)
        val expired = result as SessionRecoveryResult.Expired
        assertThat(expired.workoutId).isEqualTo("test-workout-1")
        assertThat(expired.lastCheckpointEpochMs).isEqualTo(1000000L)
        assertThat(expired.elapsedSinceCheckpointMs).isEqualTo(35 * 60 * 1000L)
    }

    @Test
    fun testDiscardSession_clearsCheckpointAndMarksAborted() = runTest {
        val snapshot = sampleSnapshot()
        fakeWorkoutDao.insertWorkout(
            WorkoutEntity(
                id = snapshot.workoutId,
                activityType = "RUNNING",
                startTimeEpochMs = 900000L,
                status = "RECORDING"
            )
        )
        repository.saveCheckpoint(snapshot, emptyList())

        recoveryManager.discardSession(snapshot.workoutId)

        val active = repository.getActiveCheckpoint()
        assertThat(active).isNull()
        val workout = fakeWorkoutDao.getWorkoutById(snapshot.workoutId)
        assertThat(workout?.status).isEqualTo("ABORTED")
    }

    @Test
    fun testCompleteSession_clearsCheckpointAndMarksCompleted() = runTest {
        val snapshot = sampleSnapshot()
        fakeWorkoutDao.insertWorkout(
            WorkoutEntity(
                id = snapshot.workoutId,
                activityType = "RUNNING",
                startTimeEpochMs = 900000L,
                status = "RECORDING"
            )
        )
        repository.saveCheckpoint(snapshot, emptyList())

        recoveryManager.completeSession(snapshot.workoutId, endTimeEpochMs = 1500000L)

        val active = repository.getActiveCheckpoint()
        assertThat(active).isNull()
        val workout = fakeWorkoutDao.getWorkoutById(snapshot.workoutId)
        assertThat(workout?.status).isEqualTo("COMPLETED")
        assertThat(workout?.endTimeEpochMs).isEqualTo(1500000L)
    }
}

// In-Memory Test Doubles for DAOs
class FakeCheckpointDao : CheckpointDao {
    private var checkpoint: ActiveSessionCheckpointEntity? = null

    override suspend fun upsertCheckpoint(checkpoint: ActiveSessionCheckpointEntity) {
        this.checkpoint = checkpoint
    }

    override suspend fun getCheckpoint(): ActiveSessionCheckpointEntity? {
        return checkpoint
    }

    override suspend fun clearCheckpoint() {
        checkpoint = null
    }

    override suspend fun deleteCheckpointByWorkoutId(workoutId: String) {
        if (checkpoint?.workoutId == workoutId) {
            checkpoint = null
        }
    }
}

class FakeTrackPointDao : TrackPointDao {
    private val points = mutableListOf<TrackPointEntity>()

    override suspend fun insertTrackPoint(trackPoint: TrackPointEntity): Long {
        points.add(trackPoint)
        return points.size.toLong()
    }

    override suspend fun insertTrackPoints(trackPoints: List<TrackPointEntity>) {
        points.addAll(trackPoints)
    }

    override suspend fun getTrackPointsForWorkout(workoutId: String): List<TrackPointEntity> {
        return points.filter { it.workoutId == workoutId }.sortedBy { it.timestampEpochMs }
    }

    override fun getTrackPointsForWorkoutFlow(workoutId: String): Flow<List<TrackPointEntity>> {
        return kotlinx.coroutines.flow.flow { emit(getTrackPointsForWorkout(workoutId)) }
    }

    override suspend fun getTrackPointCount(workoutId: String): Int {
        return points.count { it.workoutId == workoutId }
    }

    override suspend fun deleteTrackPointsForWorkout(workoutId: String) {
        points.removeAll { it.workoutId == workoutId }
    }

    override suspend fun deleteAllTrackPoints() {
        points.clear()
    }
}

class FakeWorkoutDao : WorkoutDao {
    private val workouts = mutableMapOf<String, WorkoutEntity>()

    override suspend fun insertWorkout(workout: WorkoutEntity): Long {
        workouts[workout.id] = workout
        return 1L
    }

    override suspend fun updateWorkout(workout: WorkoutEntity) {
        workouts[workout.id] = workout
    }

    override suspend fun upsertWorkout(workout: WorkoutEntity) {
        workouts[workout.id] = workout
    }

    override suspend fun getWorkoutById(workoutId: String): WorkoutEntity? {
        return workouts[workoutId]
    }

    override suspend fun getAllWorkouts(): List<WorkoutEntity> {
        return workouts.values.sortedByDescending { it.startTimeEpochMs }
    }

    override fun getAllWorkoutsFlow(): Flow<List<WorkoutEntity>> {
        return kotlinx.coroutines.flow.flow { emit(getAllWorkouts()) }
    }

    override suspend fun updateWorkoutStatus(workoutId: String, status: String, endTimeEpochMs: Long?) {
        val existing = workouts[workoutId]
        if (existing != null) {
            workouts[workoutId] = existing.copy(status = status, endTimeEpochMs = endTimeEpochMs)
        }
    }

    override suspend fun deleteWorkoutById(workoutId: String) {
        workouts.remove(workoutId)
    }

    override suspend fun deleteAllWorkouts() {
        workouts.clear()
    }
}
