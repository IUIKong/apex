package com.apex.tracker.core.model

data class FusedState(
    val lat: Double,
    val lon: Double,
    val altitude: Double,
    val speedMps: Double,
    val bearingDegrees: Float,
    val rawDistance: Double,
    val filteredDistance: Double,
    val acceptedDistance: Double,
    val fsmState: MotionState,
    val currentPaceSecPerKm: Double,
    val avgPaceSecPerKm: Double,
    val elevationGain: Double,
    val elevationLoss: Double,
    val timestampEpochMs: Long = 0L,
    val rawLat: Double = lat,
    val rawLon: Double = lon,
    val movingTimeMs: Long = 0L
)
