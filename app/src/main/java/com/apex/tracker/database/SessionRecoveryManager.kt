package com.apex.tracker.database

import com.apex.tracker.core.model.SessionSnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class SessionRecoveryResult {
    data class Restored(
        val snapshot: SessionSnapshot,
        val workout: WorkoutEntity?,
        val restoredTrackPoints: List<TrackPointEntity>
    ) : SessionRecoveryResult()

    data class Expired(
        val lastCheckpointEpochMs: Long,
        val elapsedSinceCheckpointMs: Long,
        val workoutId: String
    ) : SessionRecoveryResult()

    data object NoActiveSession : SessionRecoveryResult()
}

class SessionRecoveryManager(
    private val checkpointRepository: CheckpointRepository,
    private val workoutDao: WorkoutDao? = null,
    private val trackPointDao: TrackPointDao? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun checkAndRecoverSession(
        currentTimeEpochMs: Long = System.currentTimeMillis(),
        maxGapMs: Long = MAX_RECOVERY_GAP_MS
    ): SessionRecoveryResult = withContext(ioDispatcher) {
        val snapshot = checkpointRepository.getActiveCheckpoint() ?: return@withContext SessionRecoveryResult.NoActiveSession
        val gap = currentTimeEpochMs - snapshot.lastCheckpointEpochMs
        if (gap > maxGapMs) {
            return@withContext SessionRecoveryResult.Expired(
                lastCheckpointEpochMs = snapshot.lastCheckpointEpochMs,
                elapsedSinceCheckpointMs = gap,
                workoutId = snapshot.workoutId
            )
        }

        val workout = workoutDao?.getWorkoutById(snapshot.workoutId)
        val trackPoints = trackPointDao?.getTrackPointsForWorkout(snapshot.workoutId) ?: emptyList()

        SessionRecoveryResult.Restored(
            snapshot = snapshot,
            workout = workout,
            restoredTrackPoints = trackPoints
        )
    }

    suspend fun discardSession(workoutId: String): Unit = withContext(ioDispatcher) {
        checkpointRepository.clearActiveCheckpoint(workoutId)
        workoutDao?.updateWorkoutStatus(workoutId, "ABORTED", System.currentTimeMillis())
    }

    suspend fun completeSession(workoutId: String, endTimeEpochMs: Long = System.currentTimeMillis()): Unit = withContext(ioDispatcher) {
        checkpointRepository.clearActiveCheckpoint(workoutId)
        workoutDao?.updateWorkoutStatus(workoutId, "COMPLETED", endTimeEpochMs)
    }

    companion object {
        const val MAX_RECOVERY_GAP_MS: Long = 30 * 60 * 1000L // 30 minutes
    }
}
