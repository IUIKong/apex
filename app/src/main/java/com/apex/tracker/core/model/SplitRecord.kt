package com.apex.tracker.core.model

data class SplitRecord(
    val splitIndex: Int,
    val distanceMeters: Double,
    val elapsedTimeMs: Long,
    val movingTimeMs: Long,
    val paceSecondsPerKm: Double,
    val elevationGainMeters: Double,
    val elevationLossMeters: Double
)
