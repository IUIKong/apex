package com.apex.tracker.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apex.tracker.core.model.ActivityType
import com.apex.tracker.core.model.FusedState
import com.apex.tracker.database.AppDatabase
import com.apex.tracker.database.RoomCheckpointRepository
import com.apex.tracker.database.SessionRecoveryManager
import com.apex.tracker.database.SessionRecoveryResult
import com.apex.tracker.database.SplitEntity
import com.apex.tracker.database.TrackPointEntity
import com.apex.tracker.database.WorkoutEntity
import com.apex.tracker.sensor.LocationAvailabilityManager
import com.apex.tracker.service.TrackingForegroundService
import com.apex.tracker.ui.state.DiagnosticsUiState
import com.apex.tracker.ui.state.LiveHudUiState
import com.apex.tracker.ui.state.SplitDto
import com.apex.tracker.ui.state.TrackPointDto
import com.apex.tracker.ui.state.WorkoutSummaryUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class ApexTrackerViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val checkpointRepo = RoomCheckpointRepository(database = db)
    private val recoveryManager = SessionRecoveryManager(
        checkpointRepository = checkpointRepo,
        workoutDao = db.workoutDao(),
        trackPointDao = db.trackPointDao()
    )

    private val _liveHudState = MutableStateFlow(LiveHudUiState(isDarkTheme = true))
    val liveHudState: StateFlow<LiveHudUiState> = _liveHudState.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(true)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private val _summaryState = MutableStateFlow(WorkoutSummaryUiState())
    val summaryState: StateFlow<WorkoutSummaryUiState> = _summaryState.asStateFlow()

    private val _diagnosticsState = MutableStateFlow(DiagnosticsUiState())
    val diagnosticsState: StateFlow<DiagnosticsUiState> = _diagnosticsState.asStateFlow()

    private val _allWorkouts = MutableStateFlow<List<WorkoutEntity>>(emptyList())
    val allWorkouts: StateFlow<List<WorkoutEntity>> = _allWorkouts.asStateFlow()

    private val _selectedWorkoutId = MutableStateFlow<String?>(null)
    val selectedWorkoutId: StateFlow<String?> = _selectedWorkoutId.asStateFlow()

    private val locationAvailabilityManager = LocationAvailabilityManager(application)
    val isLocationServicesEnabled: StateFlow<Boolean> = locationAvailabilityManager.isLocationServicesEnabled
    val isLocationPermissionGranted: StateFlow<Boolean> = locationAvailabilityManager.isLocationPermissionGranted

    private var currentWorkoutId: String = UUID.randomUUID().toString()
    private var workoutStartTimeMs: Long = 0L
    private var pauseStartTimeMs: Long = 0L
    private var totalPausedDurationMs: Long = 0L
    private val trackPointsBuffer = ArrayList<TrackPointDto>()

    init {
        initHardwareDiagnostics()
        observeTrackingService()
        observeWorkoutsHistory()
        observeLocationAvailability()
        checkOrRestoreSession()
    }

    private fun initHardwareDiagnostics() {
        val app = getApplication<Application>()
        val sm = app.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val hasBaro = sm?.getDefaultSensor(Sensor.TYPE_PRESSURE) != null
        val hasAcc = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
        val hasGyro = sm?.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null

        var bPct = -1
        var bCharging = false
        var bPowerSave = false
        var bProfile = "MAX_ACCURACY"

        try {
            val batteryIntent = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            bPct = if (level >= 0 && scale > 0) (level * 100) / scale else -1
            val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            bCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

            val pm = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
            bPowerSave = pm?.isPowerSaveMode ?: false
            bProfile = when {
                bPowerSave -> "ECO // POWER SAVE"
                bCharging -> "PERFORMANCE // CHARGING"
                else -> "MAX_ACCURACY"
            }
        } catch (_: Exception) {}

        _diagnosticsState.update { prev ->
            prev.copy(
                hasBarometer = hasBaro,
                hasAccelerometer = hasAcc,
                hasGyroscope = hasGyro,
                batteryLevelPercent = bPct,
                isCharging = bCharging,
                isPowerSaveMode = bPowerSave,
                batteryProfile = bProfile,
                lockQuality = "SEARCHING (NO FIX)",
                satelliteCount = 0,
                gpsSatellites = 0,
                glonassSatellites = 0,
                galileoSatellites = 0,
                beidouSatellites = 0,
                atmosphericPressureHpa = 0.0,
                gnssFrequencyHz = 0,
                imuFrequencyHz = 0,
                hdop = 0f,
                vdop = 0f
            )
        }
    }

    private fun observeLocationAvailability() {
        viewModelScope.launch {
            locationAvailabilityManager.isLocationServicesEnabled.collect { enabled ->
                _liveHudState.update { it.copy(isLocationServicesEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            locationAvailabilityManager.isLocationPermissionGranted.collect { granted ->
                _liveHudState.update { it.copy(isLocationPermissionGranted = granted) }
            }
        }
    }

    private fun checkOrRestoreSession() {
        viewModelScope.launch(Dispatchers.IO) {
            if (TrackingForegroundService.isRecording.value) {
                val activeCheckpoint = checkpointRepo.getActiveCheckpoint()
                if (activeCheckpoint != null) {
                    currentWorkoutId = activeCheckpoint.workoutId
                    workoutStartTimeMs = activeCheckpoint.startTimeEpochMs
                    val dbTrackPoints = db.trackPointDao().getTrackPointsForWorkout(activeCheckpoint.workoutId)
                    val dtos = dbTrackPoints.map { entity ->
                        TrackPointDto(
                            latitude = entity.latitude,
                            longitude = entity.longitude,
                            altitude = entity.altitudeMeters,
                            speedMps = entity.speedMps.toFloat(),
                            bearing = entity.bearingDegrees,
                            timestamp = entity.timestampEpochMs,
                            rawLatitude = entity.rawLatitude,
                            rawLongitude = entity.rawLongitude,
                            isOutlier = entity.isOutlier
                        )
                    }
                    synchronized(trackPointsBuffer) {
                        trackPointsBuffer.clear()
                        trackPointsBuffer.addAll(dtos)
                    }
                    _liveHudState.update { state ->
                        state.copy(
                            isRecording = true,
                            isPaused = TrackingForegroundService.isPaused.value,
                            activityType = activeCheckpoint.activityType.name,
                            acceptedDistanceMeters = activeCheckpoint.acceptedDistanceMeters,
                            rawDistanceMeters = activeCheckpoint.rawDistanceMeters,
                            filteredDistanceMeters = activeCheckpoint.filteredDistanceMeters,
                            movingTimeSeconds = activeCheckpoint.movingTimeMs / 1000L,
                            elapsedTimeSeconds = activeCheckpoint.elapsedTimeMs / 1000L,
                            elevationGainMeters = activeCheckpoint.elevationGainMeters,
                            trackPoints = dtos
                        )
                    }
                    return@launch
                }
            }
            checkForInterruptedSession()
        }
    }

    private fun observeWorkoutsHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            db.workoutDao().getAllWorkoutsFlow().collect { workouts ->
                _allWorkouts.value = workouts
            }
        }
    }

    private fun checkForInterruptedSession() {
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = recoveryManager.checkAndRecoverSession()) {
                is SessionRecoveryResult.Restored -> {
                    val snapshot = result.snapshot
                    val restoredTrackPoints = result.restoredTrackPoints.map { entity ->
                        TrackPointDto(
                            latitude = entity.latitude,
                            longitude = entity.longitude,
                            altitude = entity.altitudeMeters,
                            speedMps = entity.speedMps.toFloat(),
                            bearing = entity.bearingDegrees,
                            timestamp = entity.timestampEpochMs,
                            rawLatitude = entity.rawLatitude,
                            rawLongitude = entity.rawLongitude,
                            isOutlier = entity.isOutlier
                        )
                    }
                    synchronized(trackPointsBuffer) {
                        trackPointsBuffer.clear()
                        trackPointsBuffer.addAll(restoredTrackPoints)
                    }

                    _liveHudState.update { state ->
                        state.copy(
                            hasInterruptedSession = true,
                            interruptedWorkoutId = snapshot.workoutId,
                            acceptedDistanceMeters = snapshot.acceptedDistanceMeters,
                            rawDistanceMeters = snapshot.rawDistanceMeters,
                            filteredDistanceMeters = snapshot.filteredDistanceMeters,
                            movingTimeSeconds = snapshot.movingTimeMs / 1000L,
                            elapsedTimeSeconds = snapshot.elapsedTimeMs / 1000L,
                            elevationGainMeters = snapshot.elevationGainMeters,
                            trackPoints = restoredTrackPoints
                        )
                    }
                }
                else -> {
                    _liveHudState.update { it.copy(hasInterruptedSession = false) }
                }
            }
        }
    }

    private fun observeTrackingService() {
        viewModelScope.launch {
            TrackingForegroundService.isRecording.collect { recording ->
                _liveHudState.update { it.copy(isRecording = recording) }
            }
        }

        viewModelScope.launch {
            TrackingForegroundService.isPaused.collect { paused ->
                _liveHudState.update { it.copy(isPaused = paused) }
            }
        }

        viewModelScope.launch(Dispatchers.Default) {
            TrackingForegroundService.fusedState.collect { fused ->
                if (fused != null) {
                    processFusedState(fused)
                }
            }
        }

        viewModelScope.launch(Dispatchers.Default) {
            TrackingForegroundService.filterDiagnostics.collect { diag ->
                if (diag != null) {
                    _diagnosticsState.update { prev ->
                        prev.copy(
                            positionVarEast = diag.posVarEast,
                            positionVarNorth = diag.posVarNorth,
                            velocityVarEast = diag.velVarEast,
                            velocityVarNorth = diag.velVarNorth,
                            lastMahalanobisD2 = diag.mahalanobisD2,
                            outliersRejectedCount = diag.outliersRejectedCount,
                            isDeadbandActive = diag.isDeadbandActive
                        )
                    }
                }
            }
        }

        viewModelScope.launch(Dispatchers.Default) {
            TrackingForegroundService.hardwareDiagnostics.collect { hw ->
                _diagnosticsState.update { prev ->
                    prev.copy(
                        satelliteCount = hw.satelliteCount,
                        gpsSatellites = hw.gpsSatellites,
                        glonassSatellites = hw.glonassSatellites,
                        galileoSatellites = hw.galileoSatellites,
                        beidouSatellites = hw.beidouSatellites,
                        gpsUsedInFix = hw.gpsUsedInFix,
                        glonassUsedInFix = hw.glonassUsedInFix,
                        galileoUsedInFix = hw.galileoUsedInFix,
                        beidouUsedInFix = hw.beidouUsedInFix,
                        gpsAvgCn0DbHz = hw.gpsAvgCn0DbHz,
                        glonassAvgCn0DbHz = hw.glonassAvgCn0DbHz,
                        galileoAvgCn0DbHz = hw.galileoAvgCn0DbHz,
                        beidouAvgCn0DbHz = hw.beidouAvgCn0DbHz,
                        lockQuality = if (hw.isCollecting) hw.lockQuality else prev.lockQuality,
                        horizontalAccuracy = hw.horizontalAccuracy,
                        verticalAccuracy = hw.verticalAccuracy,
                        atmosphericPressureHpa = hw.atmosphericPressureHpa,
                        hasBarometer = if (hw.isCollecting) hw.hasBarometer else prev.hasBarometer,
                        hasAccelerometer = if (hw.isCollecting) hw.hasAccelerometer else prev.hasAccelerometer,
                        hasGyroscope = if (hw.isCollecting) hw.hasGyroscope else prev.hasGyroscope,
                        batteryLevelPercent = if (hw.batteryLevelPercent >= 0) hw.batteryLevelPercent else prev.batteryLevelPercent,
                        isCharging = if (hw.batteryLevelPercent >= 0) hw.isCharging else prev.isCharging,
                        isPowerSaveMode = if (hw.batteryLevelPercent >= 0) hw.isPowerSaveMode else prev.isPowerSaveMode,
                        batteryProfile = if (hw.batteryLevelPercent >= 0) hw.batteryProfile else prev.batteryProfile,
                        gnssFrequencyHz = hw.gnssFrequencyHz,
                        imuFrequencyHz = hw.imuFrequencyHz,
                        hdop = hw.hdop,
                        vdop = hw.vdop
                    )
                }
                _liveHudState.update { prev ->
                    prev.copy(
                        satelliteCount = hw.satelliteCount,
                        horizontalAccuracyMeters = hw.horizontalAccuracy
                    )
                }
            }
        }
    }

    private fun processFusedState(fused: FusedState) {
        val now = System.currentTimeMillis()
        val paceDelta = if (fused.avgPaceSecPerKm > 0 && fused.currentPaceSecPerKm > 0) {
            fused.currentPaceSecPerKm - fused.avgPaceSecPerKm
        } else 0.0

        val isValidCoord = !(fused.lat == 0.0 && fused.lon == 0.0)
        val newPoint = if (isValidCoord) {
            TrackPointDto(
                latitude = fused.lat,
                longitude = fused.lon,
                altitude = fused.altitude,
                speedMps = fused.speedMps.toFloat(),
                bearing = fused.bearingDegrees,
                timestamp = now,
                rawLatitude = fused.rawLat,
                rawLongitude = fused.rawLon
            )
        } else null

        // Generate Splits if distance crossed
        val currentDist = fused.acceptedDistance
        val splitCount = (currentDist / 1000.0).toInt()
        val currentSplits = _liveHudState.value.splits.toMutableList()

        while (currentSplits.size < splitCount) {
            val idx = currentSplits.size + 1
            val deltaVsAvg = fused.currentPaceSecPerKm - fused.avgPaceSecPerKm
            currentSplits.add(
                SplitDto(
                    splitIndex = idx,
                    distanceMeters = 1000.0,
                    durationSeconds = if (fused.currentPaceSecPerKm > 0) fused.currentPaceSecPerKm.toLong() else 300L,
                    averagePaceSecondsPerKm = fused.currentPaceSecPerKm,
                    elevationGainMeters = fused.elevationGain / (splitCount.coerceAtLeast(1)),
                    deltaSecondsVsAvg = deltaVsAvg
                )
            )
        }

        _liveHudState.update { prev ->
            val lastPoint = prev.trackPoints.lastOrNull()
            val isStationary = fused.fsmState == com.apex.tracker.core.model.MotionState.STOPPED
            val shouldAdd = if (newPoint == null) {
                false
            } else if (lastPoint == null) {
                true
            } else if (isStationary) {
                false // Prevent GPS drift points while stationary
            } else {
                val d = com.apex.tracker.core.math.EnuProjection.haversineDistance(
                    lastPoint.latitude, lastPoint.longitude, newPoint.latitude, newPoint.longitude
                )
                d >= 0.8 || (fused.fsmState == com.apex.tracker.core.model.MotionState.MOVING && now - lastPoint.timestamp >= 2500L && d >= 0.25)
            }

            val nextTrackPoints: List<TrackPointDto>
            synchronized(trackPointsBuffer) {
                if (shouldAdd && newPoint != null) {
                    trackPointsBuffer.add(newPoint)
                    nextTrackPoints = ArrayList(trackPointsBuffer)
                } else if (trackPointsBuffer.isNotEmpty() && newPoint != null && !isStationary) {
                    trackPointsBuffer[trackPointsBuffer.lastIndex] = newPoint
                    nextTrackPoints = ArrayList(trackPointsBuffer)
                } else {
                    nextTrackPoints = prev.trackPoints
                }
            }

            val currentPausedAdj = if (prev.isPaused && pauseStartTimeMs > 0L) (now - pauseStartTimeMs) else 0L
            val elapsedSec = if (workoutStartTimeMs > 0L) {
                ((now - workoutStartTimeMs - totalPausedDurationMs - currentPausedAdj) / 1000L).coerceAtLeast(0L)
            } else {
                (fused.timestampEpochMs / 1000L).coerceAtLeast(0L)
            }
            val movingSec = if (fused.movingTimeMs > 0L) {
                (fused.movingTimeMs / 1000L).coerceAtMost(elapsedSec)
            } else if (fused.fsmState == com.apex.tracker.core.model.MotionState.MOVING && !prev.isPaused) {
                (prev.movingTimeSeconds + 1L).coerceAtMost(elapsedSec)
            } else {
                prev.movingTimeSeconds.coerceAtMost(elapsedSec)
            }

            prev.copy(
                currentPaceSecPerKm = fused.currentPaceSecPerKm,
                averagePaceSecPerKm = fused.avgPaceSecPerKm,
                paceDeltaSec = paceDelta,
                acceptedDistanceMeters = fused.acceptedDistance,
                filteredDistanceMeters = fused.filteredDistance,
                rawDistanceMeters = fused.rawDistance,
                movingTimeSeconds = movingSec,
                elapsedTimeSeconds = elapsedSec,
                elevationGainMeters = fused.elevationGain,
                currentAltitudeMeters = fused.altitude,
                motionState = fused.fsmState.name,
                trackPoints = nextTrackPoints,
                splits = currentSplits
            )
        }

        // Update Diagnostics State
        val suppressed = (fused.rawDistance - fused.acceptedDistance).coerceAtLeast(0.0)
        val suppressionPct = if (fused.rawDistance > 0.0) (suppressed / fused.rawDistance) * 100.0 else 0.0

        _diagnosticsState.update {
            it.copy(
                acceptedDistanceMeters = fused.acceptedDistance,
                filteredDistanceMeters = fused.filteredDistance,
                rawDistanceMeters = fused.rawDistance,
                noiseSuppressedMeters = suppressed,
                noiseSuppressionPercentage = suppressionPct,
                fusedAltitude = fused.altitude
            )
        }
    }

    fun startWorkout(activityTypeStr: String = "RUNNING", forceStart: Boolean = false) {
        locationAvailabilityManager.refresh()
        val servicesEnabled = locationAvailabilityManager.checkLocationServicesEnabled()
        val permissionsGranted = locationAvailabilityManager.checkLocationPermissionGranted()

        if (!forceStart && (!servicesEnabled || !permissionsGranted)) {
            _liveHudState.update {
                it.copy(
                    isLocationServicesEnabled = servicesEnabled,
                    isLocationPermissionGranted = permissionsGranted,
                    showLocationDisabledPrompt = true
                )
            }
            return
        }

        _liveHudState.update { it.copy(showLocationDisabledPrompt = false) }

        currentWorkoutId = UUID.randomUUID().toString()
        workoutStartTimeMs = System.currentTimeMillis()
        pauseStartTimeMs = 0L
        totalPausedDurationMs = 0L
        synchronized(trackPointsBuffer) {
            trackPointsBuffer.clear()
        }
        val actType = runCatching { ActivityType.valueOf(activityTypeStr) }.getOrDefault(ActivityType.RUNNING)

        _liveHudState.update {
            it.copy(
                isRecording = true,
                isPaused = false,
                isControlsLocked = false,
                motionState = "READY",
                activityType = activityTypeStr,
                trackPoints = emptyList(),
                splits = emptyList(),
                acceptedDistanceMeters = 0.0,
                filteredDistanceMeters = 0.0,
                rawDistanceMeters = 0.0,
                movingTimeSeconds = 0L,
                elapsedTimeSeconds = 0L,
                currentPaceSecPerKm = Double.NaN,
                averagePaceSecPerKm = Double.NaN,
                elevationGainMeters = 0.0
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            val initialWorkout = WorkoutEntity(
                id = currentWorkoutId,
                activityType = activityTypeStr,
                startTimeEpochMs = workoutStartTimeMs,
                status = "RECORDING"
            )
            db.workoutDao().upsertWorkout(initialWorkout)
        }

        TrackingForegroundService.startTracking(getApplication(), actType, currentWorkoutId)
    }

    fun dismissLocationPrompt() {
        _liveHudState.update { it.copy(showLocationDisabledPrompt = false) }
    }

    fun refreshLocationStatus() {
        locationAvailabilityManager.refresh()
    }

    fun pauseWorkout() {
        TrackingForegroundService.pauseTracking(getApplication())
        pauseStartTimeMs = System.currentTimeMillis()
        _liveHudState.update { it.copy(isPaused = true) }
    }

    fun resumeWorkout() {
        val now = System.currentTimeMillis()
        if (pauseStartTimeMs > 0L) {
            totalPausedDurationMs += (now - pauseStartTimeMs)
            pauseStartTimeMs = 0L
        }
        TrackingForegroundService.resumeTracking(getApplication())
        _liveHudState.update { it.copy(isPaused = false) }
    }

    fun finishWorkout(): Boolean {
        TrackingForegroundService.stopTracking(getApplication())

        val current = _liveHudState.value
        val now = System.currentTimeMillis()
        val computedElapsed = if (workoutStartTimeMs > 0L) {
            ((now - workoutStartTimeMs) / 1000L).coerceAtLeast(0L)
        } else 0L
        val finalElapsedSeconds = if (current.elapsedTimeSeconds > 0L) current.elapsedTimeSeconds else computedElapsed
        val finalMovingSeconds = if (current.movingTimeSeconds > 0L) current.movingTimeSeconds else finalElapsedSeconds

        if (finalMovingSeconds < 5L && computedElapsed < 5L) {
            // Discard sessions under 5 seconds cleanly without saving
            viewModelScope.launch(Dispatchers.IO) {
                db.workoutDao().deleteWorkoutById(currentWorkoutId)
                db.trackPointDao().deleteTrackPointsForWorkout(currentWorkoutId)
                db.splitDao().deleteSplitsForWorkout(currentWorkoutId)
                recoveryManager.completeSession(currentWorkoutId, now)
            }
            resetLiveHud()
            return false
        }

        val activityFormatted = current.activityType.lowercase().replaceFirstChar { it.uppercase() }
        val summary = WorkoutSummaryUiState(
            activityId = currentWorkoutId,
            title = "$activityFormatted Session",
            activityType = current.activityType,
            startTime = workoutStartTimeMs,
            endTime = now,
            totalDistanceMeters = current.acceptedDistanceMeters,
            movingTimeSeconds = finalMovingSeconds,
            elapsedTimeSeconds = finalElapsedSeconds,
            avgPaceSecPerKm = current.averagePaceSecPerKm,
            elevationGainMeters = current.elevationGainMeters,
            trackPoints = current.trackPoints,
            splits = current.splits
        )

        val completedWorkoutId = currentWorkoutId
        _summaryState.value = summary
        _selectedWorkoutId.value = completedWorkoutId

        // Persist completed workout, track points, and splits to Room SQLite
        val workoutEntity = WorkoutEntity(
            id = completedWorkoutId,
            activityType = current.activityType,
            startTimeEpochMs = workoutStartTimeMs,
            endTimeEpochMs = now,
            totalElapsedTimeMs = finalElapsedSeconds * 1000L,
            totalMovingTimeMs = finalMovingSeconds * 1000L,
            totalDistanceMeters = current.acceptedDistanceMeters,
            rawDistanceMeters = current.rawDistanceMeters,
            filteredDistanceMeters = current.filteredDistanceMeters,
            totalElevationGainMeters = current.elevationGainMeters,
            totalElevationLossMeters = 0.0,
            status = "COMPLETED"
        )
        val trackPointEntities = current.trackPoints.map {
            TrackPointEntity(
                workoutId = completedWorkoutId,
                timestampEpochMs = it.timestamp,
                latitude = it.latitude,
                longitude = it.longitude,
                altitudeMeters = it.altitude,
                speedMps = it.speedMps.toDouble(),
                bearingDegrees = it.bearing,
                accuracyMeters = 3.0f,
                rawLatitude = it.rawLatitude,
                rawLongitude = it.rawLongitude,
                rawAltitude = it.altitude,
                rawSpeedMps = it.speedMps,
                fsmState = "MOVING"
            )
        }
        val splitEntities = current.splits.map {
            SplitEntity(
                workoutId = completedWorkoutId,
                splitIndex = it.splitIndex,
                distanceMeters = it.distanceMeters,
                elapsedTimeMs = it.durationSeconds * 1000L,
                movingTimeMs = it.durationSeconds * 1000L,
                paceSecondsPerKm = it.averagePaceSecondsPerKm,
                elevationGainMeters = it.elevationGainMeters,
                elevationLossMeters = 0.0
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            db.workoutDao().upsertWorkout(workoutEntity)
            if (trackPointEntities.isNotEmpty()) {
                db.trackPointDao().insertTrackPoints(trackPointEntities)
            }
            if (splitEntities.isNotEmpty()) {
                db.splitDao().insertSplits(splitEntities)
            }
            recoveryManager.completeSession(completedWorkoutId, now)
        }

        // Immediately reset live HUD to a fresh clean state
        resetLiveHud()

        return true
    }

    /**
     * Resets the Live HUD display and internal trackers to a fresh clean state.
     */
    fun resetLiveHud() {
        workoutStartTimeMs = 0L
        pauseStartTimeMs = 0L
        totalPausedDurationMs = 0L
        synchronized(trackPointsBuffer) {
            trackPointsBuffer.clear()
        }
        _liveHudState.update { prev ->
            prev.copy(
                isRecording = false,
                isPaused = false,
                isControlsLocked = false,
                motionState = "READY",
                acceptedDistanceMeters = 0.0,
                rawDistanceMeters = 0.0,
                filteredDistanceMeters = 0.0,
                movingTimeSeconds = 0L,
                elapsedTimeSeconds = 0L,
                currentPaceSecPerKm = Double.NaN,
                averagePaceSecPerKm = Double.NaN,
                paceDeltaSec = 0.0,
                elevationGainMeters = 0.0,
                currentAltitudeMeters = 0.0,
                currentGradePercent = 0.0,
                heartRateBpm = 0,
                cadence = 0,
                trackPoints = emptyList(),
                splits = emptyList()
            )
        }
    }

    fun loadWorkoutDetails(workoutId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val workout = db.workoutDao().getWorkoutById(workoutId) ?: return@launch
            val trackPoints = db.trackPointDao().getTrackPointsForWorkout(workoutId).map {
                TrackPointDto(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    altitude = it.altitudeMeters,
                    speedMps = it.speedMps.toFloat(),
                    bearing = it.bearingDegrees,
                    timestamp = it.timestampEpochMs,
                    rawLatitude = it.rawLatitude,
                    rawLongitude = it.rawLongitude,
                    isOutlier = it.isOutlier
                )
            }
            val avgPace = if (workout.totalDistanceMeters > 0 && workout.totalMovingTimeMs > 0) {
                (workout.totalMovingTimeMs / 1000.0) / (workout.totalDistanceMeters / 1000.0)
            } else 0.0
            val splits = db.splitDao().getSplitsForWorkout(workoutId).map {
                SplitDto(
                    splitIndex = it.splitIndex,
                    distanceMeters = it.distanceMeters,
                    durationSeconds = it.movingTimeMs / 1000L,
                    averagePaceSecondsPerKm = it.paceSecondsPerKm,
                    elevationGainMeters = it.elevationGainMeters,
                    deltaSecondsVsAvg = if (avgPace > 0.0) it.paceSecondsPerKm - avgPace else 0.0
                )
            }

            val activityFormatted = workout.activityType.lowercase().replaceFirstChar { it.uppercase() }
            _summaryState.value = WorkoutSummaryUiState(
                activityId = workout.id,
                title = "$activityFormatted Session",
                activityType = workout.activityType,
                startTime = workout.startTimeEpochMs,
                endTime = workout.endTimeEpochMs ?: workout.startTimeEpochMs,
                totalDistanceMeters = workout.totalDistanceMeters,
                movingTimeSeconds = workout.totalMovingTimeMs / 1000L,
                elapsedTimeSeconds = workout.totalElapsedTimeMs / 1000L,
                avgPaceSecPerKm = avgPace,
                elevationGainMeters = workout.totalElevationGainMeters,
                elevationLossMeters = workout.totalElevationLossMeters,
                trackPoints = trackPoints,
                splits = splits,
                status = workout.status
            )
            _selectedWorkoutId.value = workoutId
        }
    }

    fun deleteWorkout(workoutId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.workoutDao().deleteWorkoutById(workoutId)
            db.trackPointDao().deleteTrackPointsForWorkout(workoutId)
            db.splitDao().deleteSplitsForWorkout(workoutId)
            if (_summaryState.value.activityId == workoutId) {
                _summaryState.value = WorkoutSummaryUiState()
                _selectedWorkoutId.value = null
            }
            if (currentWorkoutId == workoutId) {
                resetLiveHud()
            }
        }
    }

    fun setControlsLocked(isLocked: Boolean) {
        _liveHudState.update { it.copy(isControlsLocked = isLocked) }
    }

    fun setActivityType(type: String) {
        _liveHudState.update { it.copy(activityType = type) }
    }

    fun setBatteryProfile(profile: String) {
        _liveHudState.update { it.copy(batteryProfile = profile) }
    }

    fun toggleRawTrace() {
        _liveHudState.update { it.copy(showRawTrace = !it.showRawTrace) }
    }

    fun toggleAutoFollow() {
        _liveHudState.update { it.copy(autoFollowMap = !it.autoFollowMap) }
    }

    fun toggleTheme() {
        val next = !_isDarkTheme.value
        _isDarkTheme.value = next
        _liveHudState.update { it.copy(isDarkTheme = next) }
    }

    fun setTheme(isDark: Boolean) {
        _isDarkTheme.value = isDark
        _liveHudState.update { it.copy(isDarkTheme = isDark) }
    }

    fun resumeInterruptedSession() {
        val workoutId = _liveHudState.value.interruptedWorkoutId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            currentWorkoutId = workoutId
            val activeCheckpoint = checkpointRepo.getActiveCheckpoint()
            if (activeCheckpoint != null) {
                workoutStartTimeMs = activeCheckpoint.startTimeEpochMs
                pauseStartTimeMs = 0L
                totalPausedDurationMs = 0L
            }
            _liveHudState.update { it.copy(hasInterruptedSession = false, isRecording = true) }
            val actType = runCatching { ActivityType.valueOf(_liveHudState.value.activityType) }
                .getOrDefault(ActivityType.RUNNING)
            TrackingForegroundService.startTracking(getApplication(), actType, currentWorkoutId)
        }
    }

    fun discardInterruptedSession() {
        val workoutId = _liveHudState.value.interruptedWorkoutId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            recoveryManager.discardSession(workoutId)
            _liveHudState.update { it.copy(hasInterruptedSession = false, interruptedWorkoutId = null) }
            resetLiveHud()
        }
    }

    override fun onCleared() {
        super.onCleared()
        locationAvailabilityManager.unregister()
    }
}
