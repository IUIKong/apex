package com.apex.tracker.sensor

import android.location.Location

/**
 * Callback interface receiving real-time hardware sensor updates.
 */
interface SensorDataListener {
    /**
     * High-accuracy GNSS fix callback from FusedLocationProviderClient.
     */
    fun onLocationUpdate(location: Location)

    /**
     * 3-axis accelerometer update (50 Hz).
     */
    fun onAccelerometerUpdate(ax: Float, ay: Float, az: Float, timestampNs: Long)

    /**
     * 3-axis gyroscope update (50 Hz).
     */
    fun onGyroscopeUpdate(wx: Float, wy: Float, wz: Float, timestampNs: Long)

    /**
     * Barometric atmospheric pressure update in hPa.
     */
    fun onPressureUpdate(pressureHpa: Float, timestampNs: Long)

    /**
     * Real-time hardware GNSS constellation telemetry update from GnssStatus.Callback.
     */
    fun onGnssTelemetryUpdate(telemetry: GnssTelemetry) {}

    /**
     * Measured IMU callback frequency (actual Hz over last 1 second window).
     */
    fun onImuFrequencyUpdate(actualHz: Int) {}
}
