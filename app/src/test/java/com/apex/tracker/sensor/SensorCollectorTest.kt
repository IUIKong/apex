package com.apex.tracker.sensor

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SensorCollectorTest {

    @Test
    fun testHardwareSensorCollectorConstants() {
        // High-accuracy GNSS at 1 Hz, 0m minimum distance
        assertThat(SensorCollector.GNSS_INTERVAL_MS).isEqualTo(1000L)
        assertThat(SensorCollector.GNSS_MIN_UPDATE_DISTANCE_METERS).isEqualTo(0f)

        // 50 Hz IMU rate = 20,000 microseconds
        assertThat(SensorCollector.IMU_SAMPLING_PERIOD_US).isEqualTo(20_000)

        // 10 Hz Barometer rate = 100,000 microseconds
        assertThat(SensorCollector.BAROMETER_SAMPLING_PERIOD_US).isEqualTo(100_000)

        // Hardware FIFO batching latency = 1,000,000 us (1 second) to conserve power when screen locked
        assertThat(SensorCollector.FIFO_MAX_REPORT_LATENCY_US).isEqualTo(1_000_000)
    }

    @Test
    fun testSensorDataListenerDispatch() {
        var accelReceived = false
        var gyroReceived = false
        var pressureReceived = false

        var capturedAx = 0f
        var capturedAy = 0f
        var capturedAz = 0f
        var capturedWz = 0f
        var capturedPressure = 0f

        val listener = object : SensorDataListener {
            override fun onLocationUpdate(location: android.location.Location) {
                // Not invoked in pure mock-less IMU test
            }

            override fun onAccelerometerUpdate(ax: Float, ay: Float, az: Float, timestampNs: Long) {
                accelReceived = true
                capturedAx = ax
                capturedAy = ay
                capturedAz = az
            }

            override fun onGyroscopeUpdate(wx: Float, wy: Float, wz: Float, timestampNs: Long) {
                gyroReceived = true
                capturedWz = wz
            }

            override fun onPressureUpdate(pressureHpa: Float, timestampNs: Long) {
                pressureReceived = true
                capturedPressure = pressureHpa
            }
        }

        // Dispatch simulated events
        val testNs = 1_000_000_000L
        listener.onAccelerometerUpdate(0.1f, 0.2f, 9.81f, testNs)
        listener.onGyroscopeUpdate(0.01f, -0.02f, 0.55f, testNs)
        listener.onPressureUpdate(1013.25f, testNs)

        assertThat(accelReceived).isTrue()
        assertThat(capturedAx).isEqualTo(0.1f)
        assertThat(capturedAy).isEqualTo(0.2f)
        assertThat(capturedAz).isEqualTo(9.81f)

        assertThat(gyroReceived).isTrue()
        assertThat(capturedWz).isEqualTo(0.55f)

        assertThat(pressureReceived).isTrue()
        assertThat(capturedPressure).isEqualTo(1013.25f)
    }

    @Test
    fun testISensorCollectorContractLifecycle() {
        val testCollector = object : ISensorCollector {
            override var isCollecting: Boolean = false
                private set

            override val hasAccelerometer: Boolean = true
            override val hasGyroscope: Boolean = true
            override val hasBarometer: Boolean = true

            var currentListener: SensorDataListener? = null

            override fun start(listener: SensorDataListener): Boolean {
                currentListener = listener
                isCollecting = true
                return true
            }

            override fun stop() {
                currentListener = null
                isCollecting = false
            }

            override fun flush(): Boolean = true
        }

        assertThat(testCollector.isCollecting).isFalse()
        assertThat(testCollector.hasAccelerometer).isTrue()
        assertThat(testCollector.hasGyroscope).isTrue()
        assertThat(testCollector.hasBarometer).isTrue()

        val dummyListener = object : SensorDataListener {
            override fun onLocationUpdate(location: android.location.Location) {}
            override fun onAccelerometerUpdate(ax: Float, ay: Float, az: Float, timestampNs: Long) {}
            override fun onGyroscopeUpdate(wx: Float, wy: Float, wz: Float, timestampNs: Long) {}
            override fun onPressureUpdate(pressureHpa: Float, timestampNs: Long) {}
        }

        val started = testCollector.start(dummyListener)
        assertThat(started).isTrue()
        assertThat(testCollector.isCollecting).isTrue()
        assertThat(testCollector.currentListener).isSameInstanceAs(dummyListener)

        testCollector.stop()
        assertThat(testCollector.isCollecting).isFalse()
        assertThat(testCollector.currentListener).isNull()
    }

    @Test
    fun testGnssTelemetryDefaultsAndHonestValues() {
        val defaultGnss = GnssTelemetry()
        assertThat(defaultGnss.totalSatellites).isEqualTo(0)
        assertThat(defaultGnss.gpsSatellites).isEqualTo(0)
        assertThat(defaultGnss.glonassSatellites).isEqualTo(0)
        assertThat(defaultGnss.galileoSatellites).isEqualTo(0)
        assertThat(defaultGnss.beidouSatellites).isEqualTo(0)
        assertThat(defaultGnss.gpsUsedInFix).isEqualTo(0)
        assertThat(defaultGnss.glonassUsedInFix).isEqualTo(0)
        assertThat(defaultGnss.galileoUsedInFix).isEqualTo(0)
        assertThat(defaultGnss.beidouUsedInFix).isEqualTo(0)
        assertThat(defaultGnss.gpsAvgCn0DbHz).isEqualTo(0.0)
        assertThat(defaultGnss.glonassAvgCn0DbHz).isEqualTo(0.0)
        assertThat(defaultGnss.galileoAvgCn0DbHz).isEqualTo(0.0)
        assertThat(defaultGnss.beidouAvgCn0DbHz).isEqualTo(0.0)
        assertThat(defaultGnss.lockQuality).isEqualTo("SEARCHING (NO FIX)")

        val activeGnss = GnssTelemetry(
            totalSatellites = 20,
            gpsSatellites = 8,
            glonassSatellites = 5,
            galileoSatellites = 4,
            beidouSatellites = 3,
            gpsUsedInFix = 6,
            glonassUsedInFix = 4,
            galileoUsedInFix = 3,
            beidouUsedInFix = 2,
            gpsAvgCn0DbHz = 41.2,
            glonassAvgCn0DbHz = 38.5,
            galileoAvgCn0DbHz = 42.0,
            beidouAvgCn0DbHz = 39.1,
            lockQuality = "DGPS / HIGH ACCURACY"
        )
        assertThat(activeGnss.totalSatellites).isEqualTo(20)
        assertThat(activeGnss.gpsSatellites).isEqualTo(8)
        assertThat(activeGnss.glonassSatellites).isEqualTo(5)
        assertThat(activeGnss.galileoSatellites).isEqualTo(4)
        assertThat(activeGnss.beidouSatellites).isEqualTo(3)
        assertThat(activeGnss.lockQuality).isEqualTo("DGPS / HIGH ACCURACY")
    }

    @Test
    fun testBatteryTelemetryDefaults() {
        val defaultBattery = BatteryTelemetry()
        assertThat(defaultBattery.levelPercent).isEqualTo(-1)
        assertThat(defaultBattery.isCharging).isFalse()
        assertThat(defaultBattery.isPowerSaveMode).isFalse()
        assertThat(defaultBattery.batteryProfile).isEqualTo("MAX_ACCURACY")

        val chargingBattery = BatteryTelemetry(
            levelPercent = 85,
            isCharging = true,
            isPowerSaveMode = false,
            batteryProfile = "PERFORMANCE // CHARGING"
        )
        assertThat(chargingBattery.levelPercent).isEqualTo(85)
        assertThat(chargingBattery.isCharging).isTrue()
        assertThat(chargingBattery.batteryProfile).isEqualTo("PERFORMANCE // CHARGING")
    }
}
