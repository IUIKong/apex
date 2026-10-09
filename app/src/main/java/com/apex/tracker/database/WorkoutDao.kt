package com.apex.tracker.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkout(workout: WorkoutEntity): Long

    @Update
    suspend fun updateWorkout(workout: WorkoutEntity)

    @Upsert
    suspend fun upsertWorkout(workout: WorkoutEntity)

    @Query("SELECT * FROM workouts WHERE id = :workoutId")
    suspend fun getWorkoutById(workoutId: String): WorkoutEntity?

    @Query("SELECT * FROM workouts ORDER BY startTimeEpochMs DESC")
    suspend fun getAllWorkouts(): List<WorkoutEntity>

    @Query("SELECT * FROM workouts ORDER BY startTimeEpochMs DESC")
    fun getAllWorkoutsFlow(): Flow<List<WorkoutEntity>>

    @Query("UPDATE workouts SET status = :status, endTimeEpochMs = :endTimeEpochMs WHERE id = :workoutId")
    suspend fun updateWorkoutStatus(workoutId: String, status: String, endTimeEpochMs: Long?)

    @Query("DELETE FROM workouts WHERE id = :workoutId")
    suspend fun deleteWorkoutById(workoutId: String)

    @Query("DELETE FROM workouts")
    suspend fun deleteAllWorkouts()
}
