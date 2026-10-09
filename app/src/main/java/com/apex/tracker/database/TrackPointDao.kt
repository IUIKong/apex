package com.apex.tracker.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackPointDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackPoint(trackPoint: TrackPointEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackPoints(trackPoints: List<TrackPointEntity>)

    @Query("SELECT * FROM track_points WHERE workoutId = :workoutId ORDER BY timestampEpochMs ASC")
    suspend fun getTrackPointsForWorkout(workoutId: String): List<TrackPointEntity>

    @Query("SELECT * FROM track_points WHERE workoutId = :workoutId ORDER BY timestampEpochMs ASC")
    fun getTrackPointsForWorkoutFlow(workoutId: String): Flow<List<TrackPointEntity>>

    @Query("SELECT COUNT(*) FROM track_points WHERE workoutId = :workoutId")
    suspend fun getTrackPointCount(workoutId: String): Int

    @Query("DELETE FROM track_points WHERE workoutId = :workoutId")
    suspend fun deleteTrackPointsForWorkout(workoutId: String)

    @Query("DELETE FROM track_points")
    suspend fun deleteAllTrackPoints()
}
