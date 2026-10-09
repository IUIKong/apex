package com.apex.tracker.sensor

data class GnssTelemetry(
    val totalSatellites: Int = 0,
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
    val lockQuality: String = "SEARCHING (NO FIX)"
)

data class BatteryTelemetry(
    val levelPercent: Int = -1,
    val isCharging: Boolean = false,
    val isPowerSaveMode: Boolean = false,
    val batteryProfile: String = "MAX_ACCURACY"
)

/**
 * Interface contract for hardware sensor collection.
 */
interface ISensorCollector {
    /** Whether sensor collection is currently active. */
    val isCollecting: Boolean

    /** Whether the device hardware possesses a physical 3-axis accelerometer. */
    val hasAccelerometer: Boolean

    /** Whether the device hardware possesses a physical 3-axis gyroscope. */
    val hasGyroscope: Boolean

    /** Whether the device hardware possesses a physical barometric pressure sensor. */
    val hasBarometer: Boolean

    /**
     * Start hardware sensor collection and GNSS tracking.
     * @param listener Callback interface receiving real sensor events.
     * @return true if collection started successfully, false if permissions missing.
     */
    fun start(listener: SensorDataListener): Boolean

    /**
     * Stop hardware sensor collection, removing location updates and sensor listeners.
     */
    fun stop()

    /**
     * Explicitly flush hardware sensor FIFOs.
     */
    fun flush(): Boolean

    /** Query real device battery telemetry via BatteryManager. */
    fun queryBatteryTelemetry(): BatteryTelemetry = BatteryTelemetry()

    /** Get the latest real GNSS constellation telemetry. */
    fun getLatestGnssTelemetry(): GnssTelemetry = GnssTelemetry()

    /** Real IMU callback frequency (Hz) measured over last 1 second window. */
    fun getCurrentImuHz(): Int = 0

    /** Real GNSS fix frequency (Hz). */
    fun getCurrentGnssHz(): Int = 0
}
