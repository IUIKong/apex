package com.apex.tracker.sensor

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Genuine hardware sensor ingestion collector.
 *
 * Captures real device sensor data using official Android APIs:
 * 1. High-accuracy GNSS updates via [FusedLocationProviderClient] (1 Hz interval, 0m min distance).
 * 2. Real GNSS constellation satellite health via [GnssStatus.Callback] (GPS, GLONASS, GALILEO, BEIDOU).
 * 3. 3-axis accelerometer at 50 Hz via [SensorManager] (Sensor.TYPE_ACCELEROMETER).
 * 4. 3-axis gyroscope at 50 Hz via [SensorManager] (Sensor.TYPE_GYROSCOPE).
 * 5. Atmospheric pressure via [SensorManager] (Sensor.TYPE_PRESSURE).
 * 6. Real device battery telemetry via [BatteryManager].
 *
 * Threading architecture:
 * All high-frequency (50 Hz) IMU and GNSS samples are dispatched onto a dedicated background
 * HandlerThread [HandlerThread("apex-sensor-pipeline")] to completely eliminate Main UI thread
 * starvation, frame drops, and sensor pipeline lag.
 *
 * Power conservation:
 * Uses hardware FIFO batching with [maxReportLatencyUs] = 1,000,000 (1 second) to allow the
 * application processor to sleep while the screen is locked and off.
 *
 * Zero synthetic or mock feeds in live recording mode.
 */
class SensorCollector(
    private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context),
    private val sensorManager: SensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager,
    private val locationManager: LocationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager,
    private val callbackLooper: Looper? = null
) : ISensorCollector {

    companion object {
        const val GNSS_INTERVAL_MS = 1000L
        const val GNSS_MIN_UPDATE_DISTANCE_METERS = 0f

        // 50 Hz sampling rate = 20,000 microseconds
        const val IMU_SAMPLING_PERIOD_US = 20_000

        // 10 Hz sampling rate for barometric pressure = 100,000 microseconds
        const val BAROMETER_SAMPLING_PERIOD_US = 100_000

        // Hardware FIFO batching latency = 1,000,000 us (1 second)
        const val FIFO_MAX_REPORT_LATENCY_US = 1_000_000
    }

    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val pressureSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

    override val hasAccelerometer: Boolean get() = accelerometer != null
    override val hasGyroscope: Boolean get() = gyroscope != null
    override val hasBarometer: Boolean get() = pressureSensor != null

    @Volatile
    private var _isCollecting: Boolean = false
    override val isCollecting: Boolean get() = _isCollecting

    private var activeListener: SensorDataListener? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    @Volatile
    private var latestGnssTelemetry: GnssTelemetry = GnssTelemetry()

    @Volatile
    private var latestLocationAccuracy: Float = 0f

    @Volatile
    private var lastLocationTimestampMs: Long = 0L

    @Volatile
    private var imuSampleCount: Int = 0

    @Volatile
    private var lastImuHzCalcTimeMs: Long = 0L

    @Volatile
    private var lastImuEventTimestampMs: Long = 0L

    @Volatile
    private var currentImuHz: Int = 0

    private val gnssStatusCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            val listener = activeListener ?: return
            var gpsCount = 0
            var glonassCount = 0
            var galileoCount = 0
            var beidouCount = 0

            var gpsUsed = 0
            var glonassUsed = 0
            var galileoUsed = 0
            var beidouUsed = 0

            var gpsCn0Sum = 0.0
            var glonassCn0Sum = 0.0
            var galileoCn0Sum = 0.0
            var beidouCn0Sum = 0.0

            var gpsCn0Count = 0
            var glonassCn0Count = 0
            var galileoCn0Count = 0
            var beidouCn0Count = 0

            val count = status.satelliteCount
            var totalUsedInFix = 0

            for (i in 0 until count) {
                val constellation = status.getConstellationType(i)
                val used = status.usedInFix(i)
                val cn0 = status.getCn0DbHz(i).toDouble()
                if (used) totalUsedInFix++

                when (constellation) {
                    GnssStatus.CONSTELLATION_GPS -> {
                        gpsCount++
                        if (used) gpsUsed++
                        if (cn0 > 0.0) {
                            gpsCn0Count++
                            gpsCn0Sum += cn0
                        }
                    }
                    GnssStatus.CONSTELLATION_GLONASS -> {
                        glonassCount++
                        if (used) glonassUsed++
                        if (cn0 > 0.0) {
                            glonassCn0Count++
                            glonassCn0Sum += cn0
                        }
                    }
                    GnssStatus.CONSTELLATION_GALILEO -> {
                        galileoCount++
                        if (used) galileoUsed++
                        if (cn0 > 0.0) {
                            galileoCn0Count++
                            galileoCn0Sum += cn0
                        }
                    }
                    GnssStatus.CONSTELLATION_BEIDOU -> {
                        beidouCount++
                        if (used) beidouUsed++
                        if (cn0 > 0.0) {
                            beidouCn0Count++
                            beidouCn0Sum += cn0
                        }
                    }
                }
            }

            val avgGpsCn0 = if (gpsCn0Count > 0) gpsCn0Sum / gpsCn0Count else 0.0
            val avgGlonassCn0 = if (glonassCn0Count > 0) glonassCn0Sum / glonassCn0Count else 0.0
            val avgGalileoCn0 = if (galileoCount > 0) galileoCn0Sum / galileoCount else 0.0
            val avgBeidouCn0 = if (beidouCn0Count > 0) beidouCn0Sum / beidouCn0Count else 0.0

            val lockQuality = when {
                totalUsedInFix == 0 && count == 0 -> "SEARCHING (NO FIX)"
                totalUsedInFix == 0 -> "ACQUIRING..."
                totalUsedInFix in 1..3 -> "2D FIX"
                latestLocationAccuracy > 0f && latestLocationAccuracy <= 2.5f -> "DGPS / HIGH ACCURACY"
                totalUsedInFix >= 4 -> "3D FIX"
                else -> "ACQUIRING..."
            }

            val telemetry = GnssTelemetry(
                totalSatellites = count,
                gpsSatellites = gpsCount,
                glonassSatellites = glonassCount,
                galileoSatellites = galileoCount,
                beidouSatellites = beidouCount,
                gpsUsedInFix = gpsUsed,
                glonassUsedInFix = glonassUsed,
                galileoUsedInFix = galileoUsed,
                beidouUsedInFix = beidouUsed,
                gpsAvgCn0DbHz = avgGpsCn0,
                glonassAvgCn0DbHz = avgGlonassCn0,
                galileoAvgCn0DbHz = avgGalileoCn0,
                beidouAvgCn0DbHz = avgBeidouCn0,
                lockQuality = lockQuality
            )
            latestGnssTelemetry = telemetry
            listener.onGnssTelemetryUpdate(telemetry)
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val listener = activeListener ?: return
            for (location in result.locations) {
                // Reject mock locations if flagged in production recording
                val isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    location.isMock
                } else {
                    @Suppress("DEPRECATION")
                    location.isFromMockProvider
                }
                if (isMock) {
                    continue
                }
                latestLocationAccuracy = location.accuracy
                lastLocationTimestampMs = SystemClock.elapsedRealtime()
                listener.onLocationUpdate(location)
            }
        }
    }

    private val sensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event == null) return
            val listener = activeListener ?: return

            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    imuSampleCount++
                    val now = SystemClock.elapsedRealtime()
                    lastImuEventTimestampMs = now
                    if (lastImuHzCalcTimeMs == 0L) {
                        lastImuHzCalcTimeMs = now
                    } else {
                        val elapsed = now - lastImuHzCalcTimeMs
                        if (elapsed >= 1000L) {
                            currentImuHz = ((imuSampleCount * 1000L) / elapsed).toInt()
                            imuSampleCount = 0
                            lastImuHzCalcTimeMs = now
                            listener.onImuFrequencyUpdate(currentImuHz)
                        }
                    }

                    listener.onAccelerometerUpdate(
                        ax = event.values[0],
                        ay = event.values[1],
                        az = event.values[2],
                        timestampNs = event.timestamp
                    )
                }
                Sensor.TYPE_GYROSCOPE -> {
                    listener.onGyroscopeUpdate(
                        wx = event.values[0],
                        wy = event.values[1],
                        wz = event.values[2],
                        timestampNs = event.timestamp
                    )
                }
                Sensor.TYPE_PRESSURE -> {
                    listener.onPressureUpdate(
                        pressureHpa = event.values[0],
                        timestampNs = event.timestamp
                    )
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            // Sensor accuracy changes (e.g., SENSOR_STATUS_ACCURACY_HIGH)
        }
    }

    @Synchronized
    override fun start(listener: SensorDataListener): Boolean {
        if (_isCollecting) {
            return true
        }

        // Verify location permission (supports fine location or coarse approximate fallback on Android 12+)
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFineLocation && !hasCoarseLocation) {
            return false
        }

        activeListener = listener

        // Dedicated background pipeline thread to avoid starving Main UI thread with 110Hz sensor callbacks
        val looper = callbackLooper ?: run {
            val ht = HandlerThread("apex-sensor-pipeline", Process.THREAD_PRIORITY_MORE_FAVORABLE)
            ht.start()
            backgroundThread = ht
            ht.looper
        }
        val handler = Handler(looper)
        backgroundHandler = handler

        val priority = if (hasFineLocation) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        val granularity = if (hasFineLocation) Granularity.GRANULARITY_FINE else Granularity.GRANULARITY_COARSE

        // 1. Build LocationRequest: 1 Hz, 0m min distance, immediate delivery (0ms max batch latency)
        val locationRequest = LocationRequest.Builder(priority, GNSS_INTERVAL_MS)
            .setMinUpdateDistanceMeters(GNSS_MIN_UPDATE_DISTANCE_METERS)
            .setMinUpdateIntervalMillis(500L)
            .setMaxUpdateDelayMillis(0L)
            .setGranularity(granularity)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, looper)
        } catch (_: SecurityException) {
            activeListener = null
            stopBackgroundThread()
            return false
        }

        // 2. Register IMU & Barometer sensors with hardware FIFO batching and background handler
        accelerometer?.let {
            sensorManager.registerListener(
                sensorEventListener,
                it,
                IMU_SAMPLING_PERIOD_US,
                FIFO_MAX_REPORT_LATENCY_US,
                handler
            )
        }

        gyroscope?.let {
            sensorManager.registerListener(
                sensorEventListener,
                it,
                IMU_SAMPLING_PERIOD_US,
                FIFO_MAX_REPORT_LATENCY_US,
                handler
            )
        }

        pressureSensor?.let {
            sensorManager.registerListener(
                sensorEventListener,
                it,
                BAROMETER_SAMPLING_PERIOD_US,
                FIFO_MAX_REPORT_LATENCY_US,
                handler
            )
        }

        // 3. Register real hardware GNSS satellite constellation telemetry callback
        if (hasFineLocation) {
            try {
                locationManager.registerGnssStatusCallback(gnssStatusCallback, handler)
            } catch (_: SecurityException) {
                // Fine location permission not available
            } catch (_: Exception) {
                // GnssStatus not supported by hardware/emulator
            }
        }

        _isCollecting = true
        return true
    }

    @Synchronized
    override fun stop() {
        if (!_isCollecting) return

        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (_: Exception) {
            // Ignore on teardown
        }

        try {
            sensorManager.unregisterListener(sensorEventListener)
        } catch (_: Exception) {
            // Ignore on teardown
        }

        try {
            locationManager.unregisterGnssStatusCallback(gnssStatusCallback)
        } catch (_: Exception) {
            // Ignore on teardown
        }

        imuSampleCount = 0
        lastImuHzCalcTimeMs = 0L
        lastImuEventTimestampMs = 0L
        currentImuHz = 0
        latestLocationAccuracy = 0f
        lastLocationTimestampMs = 0L
        latestGnssTelemetry = GnssTelemetry()

        stopBackgroundThread()
        activeListener = null
        _isCollecting = false
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        backgroundThread = null
        backgroundHandler = null
    }

    override fun flush(): Boolean {
        return if (_isCollecting) {
            sensorManager.flush(sensorEventListener)
        } else {
            false
        }
    }

    override fun queryBatteryTelemetry(): BatteryTelemetry {
        return try {
            val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val pct = if (level >= 0 && scale > 0) {
                (level * 100) / scale
            } else {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            }

            val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val isPowerSave = powerManager?.isPowerSaveMode ?: false

            val profile = when {
                isPowerSave -> "ECO // POWER SAVE"
                isCharging -> "PERFORMANCE // CHARGING"
                else -> "MAX_ACCURACY"
            }

            BatteryTelemetry(
                levelPercent = pct,
                isCharging = isCharging,
                isPowerSaveMode = isPowerSave,
                batteryProfile = profile
            )
        } catch (_: Exception) {
            BatteryTelemetry()
        }
    }

    override fun getLatestGnssTelemetry(): GnssTelemetry = latestGnssTelemetry

    override fun getCurrentImuHz(): Int {
        if (!_isCollecting) return 0
        val now = SystemClock.elapsedRealtime()
        return if (lastImuEventTimestampMs > 0L && now - lastImuEventTimestampMs <= 2000L) {
            currentImuHz
        } else {
            0
        }
    }

    override fun getCurrentGnssHz(): Int {
        if (!_isCollecting) return 0
        val now = SystemClock.elapsedRealtime()
        return if (lastLocationTimestampMs > 0L && now - lastLocationTimestampMs <= 2500L) 1 else 0
    }
}
