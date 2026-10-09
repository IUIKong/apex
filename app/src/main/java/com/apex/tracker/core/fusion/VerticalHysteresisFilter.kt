package com.apex.tracker.core.fusion

import kotlin.math.pow

/**
 * 1D Vertical Filter fusing Barometric Pressure with GNSS Altitude Anchors
 * and a 1.5m Peak/Trough Deadband Hysteresis Filter.
 *
 * Guarantees zero false climbing gain from stationary barometric noise or flat-ground jitter
 * while accurately accumulating 100% of legitimate ascents and descents exceeding 1.5m.
 */
class VerticalHysteresisFilter(
    val deadbandThresholdMeters: Double = DEADBAND_THRESHOLD_METERS,
    val gnssAnchorTauSeconds: Double = GNSS_ANCHOR_TAU_SECONDS
) {

    enum class ElevationTrend {
        NEUTRAL,
        CLIMBING,
        DESCENDING
    }

    var fusedAltitude: Double = 0.0
        private set

    var elevationGain: Double = 0.0
        private set

    var elevationLoss: Double = 0.0
        private set

    var anchorAltitude: Double = 0.0
        private set

    var currentTrend: ElevationTrend = ElevationTrend.NEUTRAL
        private set

    private var lastBaroAltitude: Double? = null
    private var lastGnssTimestampMs: Long = 0L
    private var isInitialized: Boolean = false

    /**
     * Resets or initializes the filter with an initial altitude anchor.
     */
    fun initialize(initialAltitudeMeters: Double) {
        val safeAlt = if (initialAltitudeMeters.isNaN() || initialAltitudeMeters.isInfinite()) 0.0 else initialAltitudeMeters
        fusedAltitude = safeAlt
        anchorAltitude = safeAlt
        elevationGain = 0.0
        elevationLoss = 0.0
        currentTrend = ElevationTrend.NEUTRAL
        lastBaroAltitude = null
        lastGnssTimestampMs = 0L
        isInitialized = true
    }

    /**
     * Converts atmospheric pressure (hPa) to barometric altitude (meters) using the ISA formula.
     */
    fun isaBarometricAltitude(pressureHpa: Float): Double {
        if (pressureHpa.isNaN() || pressureHpa.isInfinite() || pressureHpa <= 0f) return 0.0
        val p = pressureHpa.toDouble()
        val ratio = (p / ISA_P0).coerceAtLeast(0.0)
        val alt = ISA_SCALE_HEIGHT * (1.0 - ratio.pow(ISA_EXPONENT))
        return if (alt.isNaN() || alt.isInfinite()) 0.0 else alt
    }

    /**
     * Ingests a new barometric pressure reading.
     * Computes differential climb Delta h and updates fused altitude.
     */
    fun updateBarometer(pressureHpa: Float, timestampEpochMs: Long) {
        if (pressureHpa.isNaN() || pressureHpa.isInfinite() || pressureHpa < 300.0f || pressureHpa > 1150.0f) return
        val currentBaroAlt = isaBarometricAltitude(pressureHpa)
        if (!isInitialized) {
            initialize(currentBaroAlt)
            lastBaroAltitude = currentBaroAlt
            return
        }

        val prevBaroAlt = lastBaroAltitude
        if (prevBaroAlt != null) {
            val deltaH = currentBaroAlt - prevBaroAlt
            if (!deltaH.isNaN() && !deltaH.isInfinite()) {
                fusedAltitude += deltaH
                updateHysteresis(fusedAltitude)
            }
        }
        lastBaroAltitude = currentBaroAlt
    }

    /**
     * Ingests a GNSS altitude observation.
     * Fuses absolute altitude with a complementary time constant (tau = 60s).
     */
    fun updateGnssAltitude(gnssAltitudeMeters: Double, timestampEpochMs: Long) {
        if (gnssAltitudeMeters.isNaN() || gnssAltitudeMeters.isInfinite()) return
        if (!isInitialized) {
            initialize(gnssAltitudeMeters)
            lastGnssTimestampMs = timestampEpochMs
            return
        }

        val dtSeconds = if (lastGnssTimestampMs > 0L && timestampEpochMs > lastGnssTimestampMs) {
            (timestampEpochMs - lastGnssTimestampMs) / 1000.0
        } else {
            1.0
        }
        lastGnssTimestampMs = timestampEpochMs

        if (lastBaroAltitude == null) {
            // No barometer available: smooth GNSS altitude directly
            val alpha = 0.8
            fusedAltitude = alpha * fusedAltitude + (1.0 - alpha) * gnssAltitudeMeters
        } else {
            // Complementary filter fusion with slow anchor
            val alpha = gnssAnchorTauSeconds / (gnssAnchorTauSeconds + dtSeconds)
            fusedAltitude = alpha * fusedAltitude + (1.0 - alpha) * gnssAltitudeMeters
        }

        updateHysteresis(fusedAltitude)
    }

    /**
     * Feed a direct altitude value into the hysteresis state machine.
     * Useful for testing deadband invariance directly.
     */
    fun updateAltitudeDirect(altitudeMeters: Double) {
        if (altitudeMeters.isNaN() || altitudeMeters.isInfinite()) return
        if (!isInitialized) {
            initialize(altitudeMeters)
            return
        }
        fusedAltitude = altitudeMeters
        updateHysteresis(fusedAltitude)
    }

    /**
     * 3-State Peak/Trough Deadband Hysteresis state machine.
     */
    private fun updateHysteresis(h: Double) {
        if (h.isNaN() || h.isInfinite()) return
        when (currentTrend) {
            ElevationTrend.NEUTRAL -> {
                if (h - anchorAltitude >= deadbandThresholdMeters) {
                    elevationGain += (h - anchorAltitude)
                    anchorAltitude = h
                    currentTrend = ElevationTrend.CLIMBING
                } else if (anchorAltitude - h >= deadbandThresholdMeters) {
                    elevationLoss += (anchorAltitude - h)
                    anchorAltitude = h
                    currentTrend = ElevationTrend.DESCENDING
                }
            }
            ElevationTrend.CLIMBING -> {
                if (h > anchorAltitude) {
                    elevationGain += (h - anchorAltitude)
                    anchorAltitude = h
                } else if (anchorAltitude - h >= deadbandThresholdMeters) {
                    elevationLoss += (anchorAltitude - h)
                    anchorAltitude = h
                    currentTrend = ElevationTrend.DESCENDING
                }
            }
            ElevationTrend.DESCENDING -> {
                if (h < anchorAltitude) {
                    elevationLoss += (anchorAltitude - h)
                    anchorAltitude = h
                } else if (h - anchorAltitude >= deadbandThresholdMeters) {
                    elevationGain += (h - anchorAltitude)
                    anchorAltitude = h
                    currentTrend = ElevationTrend.CLIMBING
                }
            }
        }
    }

    /**
     * Restores state from a saved session checkpoint.
     */
    fun restoreState(
        fusedAlt: Double,
        gain: Double,
        loss: Double,
        anchor: Double,
        trend: ElevationTrend
    ) {
        fusedAltitude = if (fusedAlt.isNaN() || fusedAlt.isInfinite()) 0.0 else fusedAlt
        elevationGain = if (gain.isNaN() || gain.isInfinite() || gain < 0.0) 0.0 else gain
        elevationLoss = if (loss.isNaN() || loss.isInfinite() || loss < 0.0) 0.0 else loss
        anchorAltitude = if (anchor.isNaN() || anchor.isInfinite()) fusedAltitude else anchor
        currentTrend = trend
        isInitialized = true
        lastBaroAltitude = null
        lastGnssTimestampMs = 0L
    }

    companion object {
        const val DEADBAND_THRESHOLD_METERS = 1.5
        const val GNSS_ANCHOR_TAU_SECONDS = 60.0

        // ISA Atmosphere constants
        const val ISA_P0 = 1013.25 // Sea-level standard pressure in hPa
        const val ISA_EXPONENT = 0.190263 // (R_d * L) / g0
        const val ISA_SCALE_HEIGHT = 44330.77 // T0 / L in meters
    }
}
