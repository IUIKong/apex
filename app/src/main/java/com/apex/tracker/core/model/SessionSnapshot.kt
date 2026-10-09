package com.apex.tracker.core.model

data class SessionSnapshot(
    val workoutId: String,
    val activityType: ActivityType,
    val startTimeEpochMs: Long,
    val lastCheckpointEpochMs: Long,
    val originLat: Double,
    val originLon: Double,
    val originAlt: Double,
    val stateVector: DoubleArray, // 6 elements: [pe, pn, ve, vn, ae, an]
    val covarianceDiagonals: DoubleArray, // 6 elements: P diagonals
    val rawDistanceMeters: Double,
    val filteredDistanceMeters: Double,
    val acceptedDistanceMeters: Double,
    val elapsedTimeMs: Long,
    val movingTimeMs: Long,
    val fsmState: MotionState,
    val elevationGainMeters: Double,
    val elevationLossMeters: Double,
    val elevationAnchorMeters: Double,
    val elevationTrend: String,
    val currentSplitDistanceMeters: Double
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SessionSnapshot
        if (workoutId != other.workoutId) return false
        if (activityType != other.activityType) return false
        if (startTimeEpochMs != other.startTimeEpochMs) return false
        if (lastCheckpointEpochMs != other.lastCheckpointEpochMs) return false
        if (originLat != other.originLat) return false
        if (originLon != other.originLon) return false
        if (originAlt != other.originAlt) return false
        if (!stateVector.contentEquals(other.stateVector)) return false
        if (!covarianceDiagonals.contentEquals(other.covarianceDiagonals)) return false
        if (rawDistanceMeters != other.rawDistanceMeters) return false
        if (filteredDistanceMeters != other.filteredDistanceMeters) return false
        if (acceptedDistanceMeters != other.acceptedDistanceMeters) return false
        if (elapsedTimeMs != other.elapsedTimeMs) return false
        if (movingTimeMs != other.movingTimeMs) return false
        if (fsmState != other.fsmState) return false
        if (elevationGainMeters != other.elevationGainMeters) return false
        if (elevationLossMeters != other.elevationLossMeters) return false
        if (elevationAnchorMeters != other.elevationAnchorMeters) return false
        if (elevationTrend != other.elevationTrend) return false
        if (currentSplitDistanceMeters != other.currentSplitDistanceMeters) return false
        return true
    }

    override fun hashCode(): Int {
        var result = workoutId.hashCode()
        result = 31 * result + activityType.hashCode()
        result = 31 * result + startTimeEpochMs.hashCode()
        result = 31 * result + lastCheckpointEpochMs.hashCode()
        result = 31 * result + originLat.hashCode()
        result = 31 * result + originLon.hashCode()
        result = 31 * result + originAlt.hashCode()
        result = 31 * result + stateVector.contentHashCode()
        result = 31 * result + covarianceDiagonals.contentHashCode()
        result = 31 * result + rawDistanceMeters.hashCode()
        result = 31 * result + filteredDistanceMeters.hashCode()
        result = 31 * result + acceptedDistanceMeters.hashCode()
        result = 31 * result + elapsedTimeMs.hashCode()
        result = 31 * result + movingTimeMs.hashCode()
        result = 31 * result + fsmState.hashCode()
        result = 31 * result + elevationGainMeters.hashCode()
        result = 31 * result + elevationLossMeters.hashCode()
        result = 31 * result + elevationAnchorMeters.hashCode()
        result = 31 * result + elevationTrend.hashCode()
        result = 31 * result + currentSplitDistanceMeters.hashCode()
        return result
    }
}
