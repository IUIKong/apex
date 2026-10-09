package com.apex.tracker.core.model

enum class ActivityType(
    val displayName: String,
    val jerkSpectralDensity: Double, // q_j (m^2/s^5)
    val vStopMps: Double,
    val vMoveMps: Double,
    val eStopMps2: Double,
    val eMoveMps2: Double,
    val tStopDebounceSec: Double,
    val tMoveDebounceSec: Double
) {
    WALKING(
        displayName = "Walking",
        jerkSpectralDensity = 0.40,
        vStopMps = 0.50,
        vMoveMps = 0.85,
        eStopMps2 = 0.20,
        eMoveMps2 = 0.45,
        tStopDebounceSec = 4.0,
        tMoveDebounceSec = 2.0
    ),
    HIKING(
        displayName = "Hiking",
        jerkSpectralDensity = 0.25,
        vStopMps = 0.40,
        vMoveMps = 0.70,
        eStopMps2 = 0.20,
        eMoveMps2 = 0.45,
        tStopDebounceSec = 4.0,
        tMoveDebounceSec = 2.5
    ),
    RUNNING(
        displayName = "Running",
        jerkSpectralDensity = 2.00,
        vStopMps = 0.80,
        vMoveMps = 1.20,
        eStopMps2 = 0.35,
        eMoveMps2 = 0.70,
        tStopDebounceSec = 3.0,
        tMoveDebounceSec = 1.5
    ),
    CYCLING(
        displayName = "Cycling",
        jerkSpectralDensity = 6.50,
        vStopMps = 1.10,
        vMoveMps = 1.80,
        eStopMps2 = 0.15,
        eMoveMps2 = 0.40,
        tStopDebounceSec = 3.0,
        tMoveDebounceSec = 1.5
    )
}
