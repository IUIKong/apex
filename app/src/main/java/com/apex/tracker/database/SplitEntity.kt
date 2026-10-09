package com.apex.tracker.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "splits",
    indices = [Index(value = ["workoutId", "splitIndex"])]
)
data class SplitEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val workoutId: String,
    val splitIndex: Int, // 1, 2, 3...
    val distanceMeters: Double, // 1000.0
    val elapsedTimeMs: Long,
    val movingTimeMs: Long,
    val paceSecondsPerKm: Double,
    val elevationGainMeters: Double,
    val elevationLossMeters: Double
)
