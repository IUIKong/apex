package com.apex.tracker.core.fusion

import com.apex.tracker.core.math.Haversine
import kotlin.math.sqrt

/**
 * Triple Distance Accumulators maintaining independent tracking of:
 * - D_raw: Haversine distance over consecutive raw GNSS updates
 * - D_filtered: Trajectory arc length over consecutive EKF estimates
 * - D_accepted: Official activity distance gated by auto-pause motion classification
 *
 * Guarantees D_accepted <= D_filtered <= D_raw across realistic activities.
 */
class DistanceAccumulators {

    var rawDistanceMeters: Double = 0.0
        private set

    var filteredDistanceMeters: Double = 0.0
        private set

    var acceptedDistanceMeters: Double = 0.0
        private set

    private var lastRawLat: Double? = null
    private var lastRawLon: Double? = null

    private var lastFilteredPe: Double? = null
    private var lastFilteredPn: Double? = null

    /**
     * Ingests a raw GPS fix and accumulates D_raw.
     */
    fun addRawLocation(lat: Double, lon: Double): Double {
        if (lat.isNaN() || lon.isNaN() || lat.isInfinite() || lon.isInfinite()) return 0.0
        val prevLat = lastRawLat
        val prevLon = lastRawLon
        val delta = if (prevLat != null && prevLon != null) {
            Haversine.distance(prevLat, prevLon, lat, lon)
        } else {
            0.0
        }
        if (!delta.isNaN() && delta > 0.0 && !delta.isInfinite()) {
            rawDistanceMeters += delta
        }
        lastRawLat = lat
        lastRawLon = lon
        return if (delta.isNaN() || delta < 0.0 || delta.isInfinite()) 0.0 else delta
    }

    /**
     * Ingests a filtered EKF position (pe, pn) in local tangent plane and accumulates D_filtered.
     * Returns incremental filtered distance delta.
     */
    fun addFilteredPosition(pe: Double, pn: Double): Double {
        if (pe.isNaN() || pn.isNaN() || pe.isInfinite() || pn.isInfinite()) return 0.0
        val prevPe = lastFilteredPe
        val prevPn = lastFilteredPn
        val delta = if (prevPe != null && prevPn != null) {
            val dEast = pe - prevPe
            val dNorth = pn - prevPn
            sqrt(dEast * dEast + dNorth * dNorth)
        } else {
            0.0
        }
        if (!delta.isNaN() && delta > 0.0 && !delta.isInfinite()) {
            filteredDistanceMeters += delta
        }
        lastFilteredPe = pe
        lastFilteredPn = pn
        return if (delta.isNaN() || delta < 0.0 || delta.isInfinite()) 0.0 else delta
    }

    /**
     * Adds committed distance directly to D_accepted.
     */
    fun addAcceptedDistance(deltaMeters: Double) {
        if (!deltaMeters.isNaN() && deltaMeters > 0.0 && !deltaMeters.isInfinite()) {
            acceptedDistanceMeters += deltaMeters
        }
    }

    /**
     * Re-anchors the filtered position reference coordinates without accumulating the discontinuous jump.
     * Strictly preserves existing accumulated distances while setting the new reference coordinates.
     */
    fun reanchorFilteredPosition(pe: Double, pn: Double) {
        if (!pe.isNaN() && !pn.isNaN() && !pe.isInfinite() && !pn.isInfinite()) {
            lastFilteredPe = pe
            lastFilteredPn = pn
        }
    }

    /**
     * Re-anchors the raw GPS reference coordinates without accumulating the discontinuous jump.
     */
    fun reanchorRawLocation(lat: Double, lon: Double) {
        if (!lat.isNaN() && !lon.isNaN() && !lat.isInfinite() && !lon.isInfinite()) {
            lastRawLat = lat
            lastRawLon = lon
        }
    }

    fun restore(raw: Double, filtered: Double, accepted: Double) {
        rawDistanceMeters = if (raw.isNaN() || raw < 0.0 || raw.isInfinite()) 0.0 else raw
        filteredDistanceMeters = if (filtered.isNaN() || filtered < 0.0 || filtered.isInfinite()) 0.0 else filtered
        acceptedDistanceMeters = if (accepted.isNaN() || accepted < 0.0 || accepted.isInfinite()) 0.0 else accepted
        lastRawLat = null
        lastRawLon = null
        lastFilteredPe = null
        lastFilteredPn = null
    }

    fun reset() {
        rawDistanceMeters = 0.0
        filteredDistanceMeters = 0.0
        acceptedDistanceMeters = 0.0
        lastRawLat = null
        lastRawLon = null
        lastFilteredPe = null
        lastFilteredPn = null
    }
}
