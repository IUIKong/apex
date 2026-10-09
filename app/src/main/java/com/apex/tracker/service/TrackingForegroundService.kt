package com.apex.tracker.service

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import com.apex.tracker.core.fusion.EkfStateEstimator
import com.apex.tracker.core.fusion.StateEstimator
import com.apex.tracker.core.model.ActivityType
import com.apex.tracker.core.model.FusedState
import com.apex.tracker.core.model.SessionSnapshot
import com.apex.tracker.database.AppDatabase
import com.apex.tracker.database.RoomCheckpointRepository
import com.apex.tracker.database.TrackPointEntity
import com.apex.tracker.sensor.BatteryTelemetry
import com.apex.tracker.sensor.GnssTelemetry
import com.apex.tracker.sensor.ISensorCollector
import com.apex.tracker.sensor.LocationAvailabilityManager
import com.apex.tracker.sensor.SensorCollector
import com.apex.tracker.sensor.SensorDataListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.UUID
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

data class HardwareDiagnostics(
    val satelliteCount: Int = 0,
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
    val horizontalAccuracy: Float = 0f,
    val verticalAccuracy: Float = 0f,
    val atmosphericPressureHpa: Double = 0.0,
    val hasBarometer: Boolean = false,
    val hasAccelerometer: Boolean = false,
    val hasGyroscope: Boolean = false,
    val batteryLevelPercent: Int = -1,
    val isCharging: Boolean = false,
    val isPowerSaveMode: Boolean = false,
    val batteryProfile: String = "MAX_ACCURACY",
    val gnssFrequencyHz: Int = 0,
    val imuFrequencyHz: Int = 0,
    val isCollecting: Boolean = false,
    val hdop: Float = 0f,
    val vdop: Float = 0f
)

/**
 * Android Foreground Service managing real-time background workout recording.
 *
 * Capabilities:
 * - Operates under [ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION] with START_STICKY lifecycle.
 * - Continuous tracking while screen is turned off and locked via partial WakeLock.
 * - Ongoing sticky notification displaying real-time distance, pace, elapsed time, and controls.
 * - Completely offline-first operation without requiring network connectivity.
 * - Dead reckoning fallback during GNSS dropouts (<15s kinematic, 15-30s step-heading, >30s cutoff).
 * - Zero synthetic/mock data feeds in production recording mode.
 */
class TrackingForegroundService : Service() {

    companion object {
        const val ACTION_START = "com.apex.tracker.action.START"
        const val ACTION_PAUSE = "com.apex.tracker.action.PAUSE"
        const val ACTION_RESUME = "com.apex.tracker.action.RESUME"
        const val ACTION_STOP = "com.apex.tracker.action.STOP"

        const val EXTRA_ACTIVITY_TYPE = "extra_activity_type"
        const val EXTRA_WORKOUT_ID = "extra_workout_id"

        private val _fusedState = MutableStateFlow<FusedState?>(null)
        val fusedState: StateFlow<FusedState?> = _fusedState.asStateFlow()

        private val _filterDiagnostics = MutableStateFlow<com.apex.tracker.core.model.FilterDiagnostics?>(null)
        val filterDiagnostics: StateFlow<com.apex.tracker.core.model.FilterDiagnostics?> = _filterDiagnostics.asStateFlow()

        private val _hardwareDiagnostics = MutableStateFlow(HardwareDiagnostics())
        val hardwareDiagnostics: StateFlow<HardwareDiagnostics> = _hardwareDiagnostics.asStateFlow()

        private val _isRecording = MutableStateFlow(false)
        val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

        private val _isPaused = MutableStateFlow(false)
        val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

        fun startTracking(context: Context, activityType: ActivityType = ActivityType.RUNNING, workoutId: String? = null) {
            val intent = Intent(context, TrackingForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ACTIVITY_TYPE, activityType.name)
                if (workoutId != null) {
                    putExtra(EXTRA_WORKOUT_ID, workoutId)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pauseTracking(context: Context) {
            val intent = Intent(context, TrackingForegroundService::class.java).apply {
                action = ACTION_PAUSE
            }
            context.startService(intent)
        }

        fun resumeTracking(context: Context) {
            val intent = Intent(context, TrackingForegroundService::class.java).apply {
                action = ACTION_RESUME
            }
            context.startService(intent)
        }

        fun stopTracking(context: Context) {
            val intent = Intent(context, TrackingForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var tickerJob: Job? = null

    private lateinit var wakeLockManager: WakeLockManager
    private lateinit var notificationManager: TrackingNotificationManager
    private lateinit var sensorCollector: ISensorCollector
    private lateinit var stateEstimator: StateEstimator
    private lateinit var deadReckoningEngine: DeadReckoningEngine
    private lateinit var locationAvailabilityManager: LocationAvailabilityManager
    private lateinit var checkpointRepo: RoomCheckpointRepository

    private var activeWorkoutId: String = UUID.randomUUID().toString()
    private var lastCheckpointTimeMs: Long = 0L
    private val unsavedTrackPoints = Collections.synchronizedList(mutableListOf<TrackPointEntity>())
    private var lastPredictionTimestampMs: Long = 0L

    private fun sensorTimeToEpochMs(timestampNs: Long): Long {
        val nowRealtimeNs = SystemClock.elapsedRealtimeNanos()
        val nowEpochMs = System.currentTimeMillis()
        val deltaMs = (timestampNs - nowRealtimeNs) / 1_000_000L
        val epochMs = nowEpochMs + deltaMs
        return if (epochMs > 946684800000L) epochMs else nowEpochMs
    }

    private fun isLocationDisabled(): Boolean {
        if (!::locationAvailabilityManager.isInitialized) return false
        return !locationAvailabilityManager.checkLocationServicesEnabled() ||
               !locationAvailabilityManager.checkLocationPermissionGranted()
    }

    private var activityType: ActivityType = ActivityType.RUNNING
    private var isEstimatorInitialized = false
    private var startTimeMs = 0L
    private var pauseStartTimeMs = 0L
    private var totalPausedDurationMs = 0L

    private val stateLock = Any()

    @Volatile
    private var latestGyroYawRate: Double = 0.0

    @Volatile
    private var lastGpsFixTimestampMs: Long = 0L

    private var lastGpsBearing: Float? = null
    private var lastLocationFixTimeMs: Long = 0L
    private var lastLocationLat: Double = 0.0
    private var lastLocationLon: Double = 0.0

    @Volatile
    private var latestGnssTelemetry = GnssTelemetry()

    @Volatile
    private var latestImuHz: Int = 0

    @Volatile
    private var lastImuUpdateTimestampMs: Long = 0L

    @Volatile
    private var latestHorizontalAccuracy: Float = 0f

    @Volatile
    private var latestVerticalAccuracy: Float = 0f

    @Volatile
    private var latestPressureHpa: Double = 0.0

    private fun updateHardwareDiagnostics() {
        val battery = if (::sensorCollector.isInitialized) sensorCollector.queryBatteryTelemetry() else BatteryTelemetry()
        val gnss = latestGnssTelemetry
        val now = System.currentTimeMillis()
        val timeSinceGps = now - lastGpsFixTimestampMs
        val isServiceActive = _isRecording.value && !_isPaused.value
        val gnssHz = if (isServiceActive && timeSinceGps <= 2500L && lastGpsFixTimestampMs > 0L) 1 else 0
        val isImuActive = isServiceActive && (now - lastImuUpdateTimestampMs <= 2500L && lastImuUpdateTimestampMs > 0L)
        val imuHz = if (isImuActive) latestImuHz else 0

        val lockQuality = when {
            _isPaused.value -> "PAUSED"
            !_isRecording.value -> "STANDBY // NO FIX"
            timeSinceGps > 3500L && lastGpsFixTimestampMs > 0L -> "SIGNAL LOST // DEAD RECKONING"
            else -> gnss.lockQuality
        }

        val calculatedHdop = if (isServiceActive && timeSinceGps <= 3500L && latestHorizontalAccuracy > 0f) {
            (latestHorizontalAccuracy / 4.0f).coerceIn(0.5f, 20.0f)
        } else 0f
        val calculatedVdop = if (isServiceActive && timeSinceGps <= 3500L && latestVerticalAccuracy > 0f) {
            (latestVerticalAccuracy / 4.0f).coerceIn(0.8f, 20.0f)
        } else 0f

        _hardwareDiagnostics.value = HardwareDiagnostics(
            satelliteCount = gnss.totalSatellites,
            gpsSatellites = gnss.gpsSatellites,
            glonassSatellites = gnss.glonassSatellites,
            galileoSatellites = gnss.galileoSatellites,
            beidouSatellites = gnss.beidouSatellites,
            gpsUsedInFix = gnss.gpsUsedInFix,
            glonassUsedInFix = gnss.glonassUsedInFix,
            galileoUsedInFix = gnss.galileoUsedInFix,
            beidouUsedInFix = gnss.beidouUsedInFix,
            gpsAvgCn0DbHz = gnss.gpsAvgCn0DbHz,
            glonassAvgCn0DbHz = gnss.glonassAvgCn0DbHz,
            galileoAvgCn0DbHz = gnss.galileoAvgCn0DbHz,
            beidouAvgCn0DbHz = gnss.beidouAvgCn0DbHz,
            lockQuality = lockQuality,
            horizontalAccuracy = if (timeSinceGps <= 3500L) latestHorizontalAccuracy else 0f,
            verticalAccuracy = if (timeSinceGps <= 3500L) latestVerticalAccuracy else 0f,
            atmosphericPressureHpa = latestPressureHpa,
            hasBarometer = if (::sensorCollector.isInitialized) sensorCollector.hasBarometer else false,
            hasAccelerometer = if (::sensorCollector.isInitialized) sensorCollector.hasAccelerometer else false,
            hasGyroscope = if (::sensorCollector.isInitialized) sensorCollector.hasGyroscope else false,
            batteryLevelPercent = battery.levelPercent,
            isCharging = battery.isCharging,
            isPowerSaveMode = battery.isPowerSaveMode,
            batteryProfile = battery.batteryProfile,
            gnssFrequencyHz = gnssHz,
            imuFrequencyHz = imuHz,
            isCollecting = isServiceActive,
            hdop = calculatedHdop,
            vdop = calculatedVdop
        )
    }

    private val sensorDataListener = object : SensorDataListener {
        override fun onLocationUpdate(location: Location) {
            if (_isPaused.value) return
            if (location.latitude == 0.0 && location.longitude == 0.0) return
            if (location.time > 0L && location.time <= lastLocationFixTimeMs &&
                location.latitude == lastLocationLat && location.longitude == lastLocationLon) {
                return
            }
            lastLocationFixTimeMs = location.time
            lastLocationLat = location.latitude
            lastLocationLon = location.longitude

            val now = System.currentTimeMillis()
            lastGpsFixTimestampMs = now
            latestHorizontalAccuracy = location.accuracy
            latestVerticalAccuracy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasVerticalAccuracy()) {
                location.verticalAccuracyMeters
            } else 0f

            val (fused, diag) = synchronized(stateLock) {
                if (!isEstimatorInitialized) {
                    stateEstimator.initialize(
                        originLat = location.latitude,
                        originLon = location.longitude,
                        originAlt = location.altitude,
                        initialAccuracyMeters = location.accuracy
                    )
                    isEstimatorInitialized = true
                    lastPredictionTimestampMs = now
                    if (location.hasBearing()) {
                        lastGpsBearing = location.bearing
                    }
                    if (location.hasSpeed()) {
                        val speedAccuracy = if (location.hasSpeedAccuracy()) location.speedAccuracyMetersPerSecond else 1.0f
                        val bearing = if (location.hasBearing()) location.bearing else (lastGpsBearing ?: 0.0f)
                        stateEstimator.updateGpsVelocity(location.speed, bearing, speedAccuracy)
                    }
                } else {
                    // Compute dt from last kinematic prediction timestamp to avoid double prediction
                    val dt = if (lastPredictionTimestampMs > 0L) {
                        ((now - lastPredictionTimestampMs) / 1000.0).coerceIn(0.01, 5.0)
                    } else {
                        1.0
                    }
                    lastPredictionTimestampMs = now

                    // Compute kinematic yaw rate from bearing delta with absolute magnitude to preserve turns
                    val currentBearing = if (location.hasBearing()) location.bearing else null
                    var effectiveYawRate = abs(latestGyroYawRate)
                    if (currentBearing != null && lastGpsBearing != null && dt > 0.0) {
                        var bearingDelta = (currentBearing - lastGpsBearing!!).toDouble()
                        while (bearingDelta > 180.0) bearingDelta -= 360.0
                        while (bearingDelta < -180.0) bearingDelta += 360.0
                        val kinematicYawRate = Math.toRadians(abs(bearingDelta)) / dt
                        effectiveYawRate = max(effectiveYawRate, kinematicYawRate)
                    }
                    if (currentBearing != null) {
                        lastGpsBearing = currentBearing
                    }

                    // 1. Synchronous kinematic prediction to current GPS timestamp
                    stateEstimator.predict(
                        dtSeconds = dt,
                        activityType = activityType,
                        gyroYawRateRadPerSec = effectiveYawRate
                    )

                    // 2. Velocity innovation update (anchors velocity before position update)
                    if (location.hasSpeed()) {
                        val speedAccuracy = if (location.hasSpeedAccuracy()) location.speedAccuracyMetersPerSecond else 1.0f
                        val bearing = if (location.hasBearing()) location.bearing else (lastGpsBearing ?: 0.0f)
                        stateEstimator.updateGpsVelocity(location.speed, bearing, speedAccuracy)
                    }

                    // 3. Innovation measurement update
                    stateEstimator.updateGpsPosition(
                        lat = location.latitude,
                        lon = location.longitude,
                        accuracyMeters = location.accuracy
                    )
                }

                if (location.hasAltitude()) {
                    stateEstimator.updateGnssAltitude(location.altitude, now)
                }

                // Extract actual estimated velocity (ve, vn) from EKF state for dead reckoning anchor
                val ekf = stateEstimator as? EkfStateEstimator
                val ekfVe = ekf?.state?.get(2) ?: 0.0
                val ekfVn = ekf?.state?.get(3) ?: 0.0
                val ekfAe = ekf?.state?.get(4) ?: 0.0
                val ekfAn = ekf?.state?.get(5) ?: 0.0
                val (ve, vn) = if (ekfVe != 0.0 || ekfVn != 0.0) {
                    Pair(ekfVe, ekfVn)
                } else if (location.hasSpeed() && location.hasBearing()) {
                    val bRad = Math.toRadians(location.bearing.toDouble())
                    Pair(location.speed.toDouble() * sin(bRad), location.speed.toDouble() * cos(bRad))
                } else {
                    Pair(0.0, 0.0)
                }

                deadReckoningEngine.onGpsFix(
                    timestampMs = now,
                    velEast = ve,
                    velNorth = vn,
                    accEast = ekfAe,
                    accNorth = ekfAn,
                    bearingDegrees = if (location.hasBearing()) location.bearing else null
                )

                Pair(stateEstimator.getFusedState(), stateEstimator.getDiagnostics())
            }

            // Buffer track point for crash recovery checkpointing
            val trackPt = TrackPointEntity(
                workoutId = activeWorkoutId,
                timestampEpochMs = now,
                latitude = fused.lat,
                longitude = fused.lon,
                altitudeMeters = fused.altitude,
                speedMps = fused.speedMps,
                bearingDegrees = fused.bearingDegrees,
                accuracyMeters = location.accuracy,
                rawLatitude = location.latitude,
                rawLongitude = location.longitude,
                rawAltitude = if (location.hasAltitude()) location.altitude else fused.altitude,
                rawSpeedMps = if (location.hasSpeed()) location.speed else fused.speedMps.toFloat(),
                fsmState = fused.fsmState.name,
                isOutlier = false
            )
            unsavedTrackPoints.add(trackPt)

            _fusedState.value = fused
            _filterDiagnostics.value = diag
            updateHardwareDiagnostics()
        }

        override fun onAccelerometerUpdate(ax: Float, ay: Float, az: Float, timestampNs: Long) {
            if (_isPaused.value) return
            val nowMs = sensorTimeToEpochMs(timestampNs)
            synchronized(stateLock) {
                stateEstimator.updateImuAcceleration(ax, ay, az, nowMs)
                deadReckoningEngine.onAccelerometerSample(ax, ay, az, nowMs)
            }
        }

        override fun onGyroscopeUpdate(wx: Float, wy: Float, wz: Float, timestampNs: Long) {
            if (_isPaused.value) return
            latestGyroYawRate = wz.toDouble()
            synchronized(stateLock) {
                deadReckoningEngine.onGyroUpdate(wz.toDouble(), 0.02)
            }
        }

        override fun onPressureUpdate(pressureHpa: Float, timestampNs: Long) {
            if (_isPaused.value) return
            val nowMs = sensorTimeToEpochMs(timestampNs)
            latestPressureHpa = pressureHpa.toDouble()
            synchronized(stateLock) {
                stateEstimator.updateBarometerPressure(pressureHpa, nowMs)
            }
            updateHardwareDiagnostics()
        }

        override fun onGnssTelemetryUpdate(telemetry: GnssTelemetry) {
            latestGnssTelemetry = telemetry
            updateHardwareDiagnostics()
        }

        override fun onImuFrequencyUpdate(actualHz: Int) {
            latestImuHz = actualHz
            lastImuUpdateTimestampMs = System.currentTimeMillis()
            updateHardwareDiagnostics()
        }
    }

    override fun onCreate() {
        super.onCreate()
        locationAvailabilityManager = LocationAvailabilityManager(this)
        wakeLockManager = WakeLockManager(this)
        notificationManager = TrackingNotificationManager(this)
        sensorCollector = SensorCollector(this)
        stateEstimator = EkfStateEstimator()
        deadReckoningEngine = DeadReckoningEngine()
        checkpointRepo = RoomCheckpointRepository(database = AppDatabase.getInstance(this))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_START -> {
                val typeName = intent?.getStringExtra(EXTRA_ACTIVITY_TYPE)
                val workoutIdExtra = intent?.getStringExtra(EXTRA_WORKOUT_ID)
                if (!workoutIdExtra.isNullOrBlank()) {
                    activeWorkoutId = workoutIdExtra
                }
                activityType = typeName?.let { runCatching { ActivityType.valueOf(it) }.getOrNull() }
                    ?: ActivityType.RUNNING
                startRecording()
            }
            ACTION_PAUSE -> {
                pauseRecording()
            }
            ACTION_RESUME -> {
                resumeRecording()
            }
            ACTION_STOP -> {
                stopRecording()
            }
        }

        return START_STICKY
    }

    private fun startRecording() {
        if (_isRecording.value) return

        startTimeMs = System.currentTimeMillis()
        totalPausedDurationMs = 0L
        lastCheckpointTimeMs = startTimeMs
        lastPredictionTimestampMs = startTimeMs
        unsavedTrackPoints.clear()
        _isRecording.value = true
        _isPaused.value = false

        // 1. Acquire Partial WakeLock with 12h safety timeout
        wakeLockManager.acquire()

        // 2. Attach ongoing sticky notification and promote to foreground service
        val isGpsOff = isLocationDisabled()
        val initialNotification = notificationManager.buildNotification(
            distanceMeters = 0.0,
            paceSecPerKm = 0.0,
            elapsedSeconds = 0L,
            isPaused = false,
            isLocationDisabled = isGpsOff
        )

        val hasFineLocation = androidx.core.content.ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation = androidx.core.content.ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasLocationPermission = hasFineLocation || hasCoarseLocation

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasLocationPermission) {
                startForeground(
                    TrackingNotificationManager.NOTIFICATION_ID,
                    initialNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(
                    TrackingNotificationManager.NOTIFICATION_ID,
                    initialNotification
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("TrackingService", "Failed to start foreground service", e)
        }

        // Attempt restoring any ongoing active session checkpoint
        serviceScope.launch(Dispatchers.IO) {
            try {
                val cp = checkpointRepo.getActiveCheckpoint()
                if (cp != null && (cp.workoutId == activeWorkoutId || activeWorkoutId.isBlank())) {
                    synchronized(stateLock) {
                        activeWorkoutId = cp.workoutId
                        activityType = cp.activityType
                        stateEstimator.restore(cp)
                        isEstimatorInitialized = true
                        startTimeMs = cp.startTimeEpochMs
                        lastCheckpointTimeMs = cp.lastCheckpointEpochMs
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("TrackingService", "Failed to restore active checkpoint", e)
            }
        }

        // 3. Connect real hardware sensor collector
        val sensorStarted = sensorCollector.start(sensorDataListener)
        if (!sensorStarted) {
            android.util.Log.w("TrackingService", "SensorCollector start failed; fine location permissions may be missing")
        }

        // 4. Launch 1 Hz background loop for EKF prediction, dead reckoning, checkpointing, and notification update
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            var lastTickTimeMs = System.currentTimeMillis()
            while (isActive) {
                delay(1000L)
                val now = System.currentTimeMillis()

                if (_isPaused.value) {
                    val pausedElapsedSeconds = (pauseStartTimeMs - startTimeMs - totalPausedDurationMs) / 1000L
                    val currentFused = synchronized(stateLock) { stateEstimator.getFusedState() }
                    val currentGpsOff = isLocationDisabled()
                    notificationManager.updateNotification(
                        distanceMeters = currentFused.acceptedDistance,
                        paceSecPerKm = 0.0,
                        elapsedSeconds = pausedElapsedSeconds,
                        isPaused = true,
                        isLocationDisabled = currentGpsOff
                    )
                    lastTickTimeMs = now
                    continue
                }

                val dt = ((now - lastTickTimeMs) / 1000.0).coerceIn(0.1, 5.0)
                lastTickTimeMs = now

                val timeSinceGps = now - lastGpsFixTimestampMs
                val (currentFused, currentDiag) = synchronized(stateLock) {
                    if (isEstimatorInitialized && timeSinceGps > 1200L) {
                        // Dead reckoning fallback during GNSS dropout (> 1.2s)
                        val dtPredict = if (lastPredictionTimestampMs > 0L) {
                            ((now - lastPredictionTimestampMs) / 1000.0).coerceIn(0.01, 5.0)
                        } else dt
                        lastPredictionTimestampMs = now

                        stateEstimator.predict(
                            dtSeconds = dtPredict,
                            activityType = activityType,
                            gyroYawRateRadPerSec = abs(latestGyroYawRate)
                        )
                        val drStep = deadReckoningEngine.update(now, dtPredict)
                        stateEstimator.applyDeadReckoningStep(drStep, dtPredict)
                    }

                    Pair(stateEstimator.getFusedState(), stateEstimator.getDiagnostics())
                }

                _fusedState.value = currentFused
                _filterDiagnostics.value = currentDiag

                // Background crash recovery atomic checkpointing every 2.5 seconds
                if (isEstimatorInitialized && (now - lastCheckpointTimeMs >= 2500L)) {
                    lastCheckpointTimeMs = now
                    val snapshot = synchronized(stateLock) {
                        val ekf = stateEstimator as? EkfStateEstimator
                        val fused = currentFused
                        val stVector = ekf?.state?.copyOf() ?: DoubleArray(6)
                        val covDiags = if (ekf != null) {
                            doubleArrayOf(
                                ekf.covariance[0, 0],
                                ekf.covariance[1, 1],
                                ekf.covariance[2, 2],
                                ekf.covariance[3, 3],
                                ekf.covariance[4, 4],
                                ekf.covariance[5, 5]
                            )
                        } else {
                            DoubleArray(6) { 1.0 }
                        }
                        val originLat = ekf?.originLat ?: fused.lat
                        val originLon = ekf?.originLon ?: fused.lon
                        val originAlt = ekf?.originAlt ?: fused.altitude
                        val movingMs = ekf?.movingTimeMs ?: 0L
                        val elapsedMs = now - startTimeMs - totalPausedDurationMs

                        SessionSnapshot(
                            workoutId = activeWorkoutId,
                            activityType = activityType,
                            startTimeEpochMs = startTimeMs,
                            lastCheckpointEpochMs = now,
                            originLat = originLat,
                            originLon = originLon,
                            originAlt = originAlt,
                            stateVector = stVector,
                            covarianceDiagonals = covDiags,
                            rawDistanceMeters = fused.rawDistance,
                            filteredDistanceMeters = fused.filteredDistance,
                            acceptedDistanceMeters = fused.acceptedDistance,
                            elapsedTimeMs = elapsedMs,
                            movingTimeMs = movingMs,
                            fsmState = fused.fsmState,
                            elevationGainMeters = fused.elevationGain,
                            elevationLossMeters = fused.elevationLoss,
                            elevationAnchorMeters = fused.altitude,
                            elevationTrend = ekf?.verticalFilter?.currentTrend?.name ?: "NEUTRAL",
                            currentSplitDistanceMeters = fused.acceptedDistance % 1000.0
                        )
                    }

                    val pointsToSave = mutableListOf<TrackPointEntity>()
                    synchronized(unsavedTrackPoints) {
                        pointsToSave.addAll(unsavedTrackPoints)
                        unsavedTrackPoints.clear()
                    }

                    serviceScope.launch(Dispatchers.IO) {
                        try {
                            checkpointRepo.saveCheckpoint(snapshot, pointsToSave)
                        } catch (e: Exception) {
                            android.util.Log.e("TrackingService", "Failed to save crash recovery checkpoint", e)
                        }
                    }
                }

                val elapsedSeconds = (now - startTimeMs - totalPausedDurationMs) / 1000L
                val currentGpsOff = isLocationDisabled()
                notificationManager.updateNotification(
                    distanceMeters = currentFused.acceptedDistance,
                    paceSecPerKm = currentFused.currentPaceSecPerKm,
                    elapsedSeconds = elapsedSeconds,
                    isPaused = false,
                    isLocationDisabled = currentGpsOff
                )
                updateHardwareDiagnostics()
            }
        }
    }

    private fun pauseRecording() {
        if (!_isRecording.value || _isPaused.value) return
        _isPaused.value = true
        pauseStartTimeMs = System.currentTimeMillis()
        lastPredictionTimestampMs = 0L

        // Power conservation on pause: Stop sensor polling (111Hz) & release WakeLock
        sensorCollector.stop()
        wakeLockManager.release()
        latestGyroYawRate = 0.0

        val currentFused = synchronized(stateLock) { stateEstimator.getFusedState() }
        val elapsedSeconds = (pauseStartTimeMs - startTimeMs - totalPausedDurationMs) / 1000L
        notificationManager.updateNotification(
            distanceMeters = currentFused.acceptedDistance,
            paceSecPerKm = 0.0,
            elapsedSeconds = elapsedSeconds,
            isPaused = true,
            isLocationDisabled = isLocationDisabled()
        )
        updateHardwareDiagnostics()
    }

    private fun resumeRecording() {
        if (!_isRecording.value || !_isPaused.value) return
        val now = System.currentTimeMillis()
        totalPausedDurationMs += (now - pauseStartTimeMs)
        _isPaused.value = false
        lastPredictionTimestampMs = now

        // Resume tracking: Re-acquire WakeLock and re-register sensor listeners
        wakeLockManager.acquire()
        val sensorStarted = sensorCollector.start(sensorDataListener)
        if (!sensorStarted) {
            android.util.Log.w("TrackingService", "SensorCollector resume failed; location permissions may have been revoked")
        }

        val currentFused = synchronized(stateLock) { stateEstimator.getFusedState() }
        val elapsedSeconds = (now - startTimeMs - totalPausedDurationMs) / 1000L
        val currentGpsOff = isLocationDisabled()
        notificationManager.updateNotification(
            distanceMeters = currentFused.acceptedDistance,
            paceSecPerKm = currentFused.currentPaceSecPerKm,
            elapsedSeconds = elapsedSeconds,
            isPaused = false,
            isLocationDisabled = currentGpsOff
        )
        updateHardwareDiagnostics()
    }

    private fun stopRecording() {
        if (!_isRecording.value) return

        tickerJob?.cancel()
        tickerJob = null

        sensorCollector.stop()
        wakeLockManager.release()

        _isRecording.value = false
        _isPaused.value = false
        _fusedState.value = null
        _filterDiagnostics.value = null

        latestGnssTelemetry = GnssTelemetry()
        latestImuHz = 0
        lastImuUpdateTimestampMs = 0L
        latestHorizontalAccuracy = 0f
        latestVerticalAccuracy = 0f
        isEstimatorInitialized = false
        lastPredictionTimestampMs = 0L
        unsavedTrackPoints.clear()
        updateHardwareDiagnostics()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
        if (::sensorCollector.isInitialized) {
            sensorCollector.stop()
        }
        if (::wakeLockManager.isInitialized) {
            wakeLockManager.release()
        }
        if (::locationAvailabilityManager.isInitialized) {
            locationAvailabilityManager.unregister()
        }
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
