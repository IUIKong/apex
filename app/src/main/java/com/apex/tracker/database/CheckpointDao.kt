package com.apex.tracker.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface CheckpointDao {
    @Upsert
    suspend fun upsertCheckpoint(checkpoint: ActiveSessionCheckpointEntity)

    @Query("SELECT * FROM active_session_checkpoint WHERE id = 1 LIMIT 1")
    suspend fun getCheckpoint(): ActiveSessionCheckpointEntity?

    @Query("DELETE FROM active_session_checkpoint WHERE id = 1")
    suspend fun clearCheckpoint()

    @Query("DELETE FROM active_session_checkpoint WHERE workoutId = :workoutId")
    suspend fun deleteCheckpointByWorkoutId(workoutId: String)
}
