package com.apex.tracker.ui.state

import androidx.compose.runtime.Immutable

@Immutable
data class TrackPointDto(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val speedMps: Float = 0f,
    val bearing: Float = 0f,
    val timestamp: Long = 0L,
    val rawLatitude: Double = latitude,
    val rawLongitude: Double = longitude,
    val isOutlier: Boolean = false
)

@Immutable
data class SplitDto(
    val splitIndex: Int,
    val distanceMeters: Double = 1000.0,
    val durationSeconds: Long = 0L,
    val averagePaceSecondsPerKm: Double = 0.0,
    val elevationGainMeters: Double = 0.0,
    val deltaSecondsVsAvg: Double = 0.0
)

@Immutable
data class LiveHudUiState(
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val isControlsLocked: Boolean = false,
    val isDarkTheme: Boolean = true,
    val motionState: String = "READY", // READY, MOVING, STOPPED, PAUSED, INITIALIZING
    val activityType: String = "RUNNING", // RUNNING, CYCLING, WALKING, HIKING
    val batteryProfile: String = "MAX_ACCURACY", // MAX_ACCURACY, BALANCED, ECO

    // Primary & Secondary Telemetry Metrics
    val currentPaceSecPerKm: Double = 0.0,
    val averagePaceSecPerKm: Double = 0.0,
    val paceDeltaSec: Double = 0.0,
    val acceptedDistanceMeters: Double = 0.0,
    val filteredDistanceMeters: Double = 0.0,
    val rawDistanceMeters: Double = 0.0,
    val movingTimeSeconds: Long = 0L,
    val elapsedTimeSeconds: Long = 0L,
    val elevationGainMeters: Double = 0.0,
    val currentAltitudeMeters: Double = 0.0,
    val currentGradePercent: Double = 0.0,
    val heartRateBpm: Int = 0,
    val cadence: Int = 0,

    // Hardware & Sensor Status
    val satelliteCount: Int = 0,
    val horizontalAccuracyMeters: Float = 0f,
    val showRawTrace: Boolean = true,
    val autoFollowMap: Boolean = true,

    // Trackpoints and Splits History
    val trackPoints: List<TrackPointDto> = emptyList(),
    val splits: List<SplitDto> = emptyList(),
    val hasInterruptedSession: Boolean = false,
    val interruptedWorkoutId: String? = null,

    // Location Provider and Permissions Status
    val isLocationServicesEnabled: Boolean = true,
    val isLocationPermissionGranted: Boolean = true,
    val showLocationDisabledPrompt: Boolean = false
)

@Immutable
data class WorkoutSummaryUiState(
    val activityId: String = "",
    val title: String = "WORKOUT SUMMARY",
    val activityType: String = "RUNNING",
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val totalDistanceMeters: Double = 0.0,
    val movingTimeSeconds: Long = 0L,
    val elapsedTimeSeconds: Long = 0L,
    val avgPaceSecPerKm: Double = 0.0,
    val elevationGainMeters: Double = 0.0,
    val elevationLossMeters: Double = 0.0,
    val trackPoints: List<TrackPointDto> = emptyList(),
    val splits: List<SplitDto> = emptyList(),
    val status: String = "COMPLETED"
)

@Immutable
data class DiagnosticsUiState(
    val acceptedDistanceMeters: Double = 0.0,
    val filteredDistanceMeters: Double = 0.0,
    val rawDistanceMeters: Double = 0.0,
    val noiseSuppressedMeters: Double = 0.0,
    val noiseSuppressionPercentage: Double = 0.0,
    val positionVarEast: Double = 0.0,
    val positionVarNorth: Double = 0.0,
    val velocityVarEast: Double = 0.0,
    val velocityVarNorth: Double = 0.0,
    val lastMahalanobisD2: Double = 0.0,
    val outliersRejectedCount: Int = 0,
    val satelliteCount: Int = 0,
    val horizontalAccuracy: Float = 0f,
    val fusedAltitude: Double = 0.0,
    val imuEnergyVariance: Double = 0.0,
    val isDeadbandActive: Boolean = false,
    val gpsSatellites: Int = 0,
    val glonassSatellites: Int = 0,
    val galileoSatellites: Int = 0,
    val beidouSatellites: Int = 0,
    val gpsUsedInFix: Int = 0,
    val glonassUsedInFix: Int = 0,
    val galileoUsedInFix: Int = 0,
    val beidouUsedInFix: Int = 0,
    val gpsAvgCn0DbHz: Double = 0.0,
    val glonassAvgCn0DbHz: Double = 0.0,
    val galileoAvgCn0DbHz: Double = 0.0,
    val beidouAvgCn0DbHz: Double = 0.0,
    val lockQuality: String = "SEARCHING (NO FIX)",
    val verticalAccuracy: Float = 0f,
    val atmosphericPressureHpa: Double = 0.0,
    val hasBarometer: Boolean = false,
    val hasAccelerometer: Boolean = false,
    val hasGyroscope: Boolean = false,
    val accelDampFactor: Double = 0.985,
    val batteryProfile: String = "MAX_ACCURACY",
    val batteryLevelPercent: Int = -1,
    val isCharging: Boolean = false,
    val isPowerSaveMode: Boolean = false,
    val gnssFrequencyHz: Int = 0,
    val imuFrequencyHz: Int = 0,
    val hdop: Float = 0f,
    val vdop: Float = 0f
)
