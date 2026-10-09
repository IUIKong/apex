package com.apex.tracker.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "track_points",
    indices = [Index(value = ["workoutId", "timestampEpochMs"])]
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val workoutId: String,
    val timestampEpochMs: Long,
    val latitude: Double, // Filtered WGS84
    val longitude: Double, // Filtered WGS84
    val altitudeMeters: Double, // Fused Baro+GNSS
    val speedMps: Double, // Filtered speed
    val bearingDegrees: Float,
    val accuracyMeters: Float,
    val rawLatitude: Double,
    val rawLongitude: Double,
    val rawAltitude: Double,
    val rawSpeedMps: Float,
    val fsmState: String, // MOVING, STOPPED, etc.
    val isOutlier: Boolean = false
)
