package com.apex.tracker.core.fusion

import com.apex.tracker.core.model.ActivityType
import com.apex.tracker.core.model.FilterDiagnostics
import com.apex.tracker.core.model.FusedState
import com.apex.tracker.core.model.GpsUpdateResult
import com.apex.tracker.core.model.SessionSnapshot
import com.apex.tracker.service.DeadReckoningStep

/**
 * Authoritative Interface Contract for the Core State Estimator & Sensor Fusion Engine.
 * Decoupled from Android framework classes for pure JVM testability.
 */
interface StateEstimator {
    fun initialize(originLat: Double, originLon: Double, originAlt: Double, initialAccuracyMeters: Float)
    fun predict(dtSeconds: Double, activityType: ActivityType, gyroYawRateRadPerSec: Double)
    fun updateGpsPosition(lat: Double, lon: Double, accuracyMeters: Float): GpsUpdateResult
    fun updateGpsVelocity(speedMps: Float, bearingDegrees: Float, speedAccuracyMps: Float)
    fun updateBarometerPressure(pressureHpa: Float, timestampEpochMs: Long)
    fun updateGnssAltitude(altitudeMeters: Double, timestampEpochMs: Long) {}
    fun updateImuAcceleration(ax: Float, ay: Float, az: Float, timestampEpochMs: Long)
    fun getFusedState(): FusedState
    fun getDiagnostics(): FilterDiagnostics
    fun applyDeadReckoningStep(step: DeadReckoningStep, dtSeconds: Double) {}
    fun restore(snapshot: SessionSnapshot) {}
}
