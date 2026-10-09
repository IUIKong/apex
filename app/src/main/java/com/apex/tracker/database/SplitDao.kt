package com.apex.tracker.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SplitDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSplit(split: SplitEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSplits(splits: List<SplitEntity>)

    @Query("SELECT * FROM splits WHERE workoutId = :workoutId ORDER BY splitIndex ASC")
    suspend fun getSplitsForWorkout(workoutId: String): List<SplitEntity>

    @Query("SELECT * FROM splits WHERE workoutId = :workoutId ORDER BY splitIndex ASC")
    fun getSplitsForWorkoutFlow(workoutId: String): Flow<List<SplitEntity>>

    @Query("DELETE FROM splits WHERE workoutId = :workoutId")
    suspend fun deleteSplitsForWorkout(workoutId: String)

    @Query("DELETE FROM splits")
    suspend fun deleteAllSplits()
}
