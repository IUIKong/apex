package com.apex.tracker.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey
    val id: String, // UUID
    val activityType: String, // RUNNING, CYCLING, WALKING, HIKING
    val startTimeEpochMs: Long,
    val endTimeEpochMs: Long? = null,
    val totalElapsedTimeMs: Long = 0L,
    val totalMovingTimeMs: Long = 0L,
    val totalDistanceMeters: Double = 0.0, // Accepted distance
    val rawDistanceMeters: Double = 0.0,
    val filteredDistanceMeters: Double = 0.0,
    val totalElevationGainMeters: Double = 0.0,
    val totalElevationLossMeters: Double = 0.0,
    val status: String // RECORDING, PAUSED, COMPLETED, ABORTED
)
