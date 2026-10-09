package com.apex.tracker.database

import com.apex.tracker.core.model.SessionSnapshot

interface CheckpointRepository {
    suspend fun saveCheckpoint(snapshot: SessionSnapshot, recentTrackPoints: List<TrackPointEntity>)
    suspend fun getActiveCheckpoint(): SessionSnapshot?
    suspend fun clearActiveCheckpoint(workoutId: String)
}
