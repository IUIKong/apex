package com.apex.tracker.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.apex.tracker.core.model.ActivityType
import com.apex.tracker.core.model.MotionState
import com.apex.tracker.core.model.SessionSnapshot

@Entity(tableName = "active_session_checkpoint")
data class ActiveSessionCheckpointEntity(
    @PrimaryKey
    val id: Int = SINGLETON_ID,
    val workoutId: String,
    val activityType: String,
    val startTimeEpochMs: Long,
    val lastCheckpointEpochMs: Long,
    val originLat: Double,
    val originLon: Double,
    val originAlt: Double,
    val stateVectorJson: String, // 6 doubles: [pe, pn, ve, vn, ae, an]
    val covarianceDiagJson: String, // 6 doubles: P diagonals
    val rawDistanceMeters: Double,
    val filteredDistanceMeters: Double,
    val acceptedDistanceMeters: Double,
    val elapsedTimeMs: Long,
    val movingTimeMs: Long,
    val fsmState: String,
    val elevationGainMeters: Double,
    val elevationLossMeters: Double,
    val elevationAnchorMeters: Double,
    val elevationTrend: String,
    val currentSplitDistanceMeters: Double
) {
    fun toSessionSnapshot(): SessionSnapshot {
        return SessionSnapshot(
            workoutId = workoutId,
            activityType = ActivityType.valueOf(activityType),
            startTimeEpochMs = startTimeEpochMs,
            lastCheckpointEpochMs = lastCheckpointEpochMs,
            originLat = originLat,
            originLon = originLon,
            originAlt = originAlt,
            stateVector = parseDoubleArray(stateVectorJson),
            covarianceDiagonals = parseDoubleArray(covarianceDiagJson),
            rawDistanceMeters = rawDistanceMeters,
            filteredDistanceMeters = filteredDistanceMeters,
            acceptedDistanceMeters = acceptedDistanceMeters,
            elapsedTimeMs = elapsedTimeMs,
            movingTimeMs = movingTimeMs,
            fsmState = MotionState.valueOf(fsmState),
            elevationGainMeters = elevationGainMeters,
            elevationLossMeters = elevationLossMeters,
            elevationAnchorMeters = elevationAnchorMeters,
            elevationTrend = elevationTrend,
            currentSplitDistanceMeters = currentSplitDistanceMeters
        )
    }

    companion object {
        const val SINGLETON_ID: Int = 1

        fun fromSessionSnapshot(snapshot: SessionSnapshot): ActiveSessionCheckpointEntity {
            return ActiveSessionCheckpointEntity(
                id = SINGLETON_ID,
                workoutId = snapshot.workoutId,
                activityType = snapshot.activityType.name,
                startTimeEpochMs = snapshot.startTimeEpochMs,
                lastCheckpointEpochMs = snapshot.lastCheckpointEpochMs,
                originLat = snapshot.originLat,
                originLon = snapshot.originLon,
                originAlt = snapshot.originAlt,
                stateVectorJson = formatDoubleArray(snapshot.stateVector),
                covarianceDiagJson = formatDoubleArray(snapshot.covarianceDiagonals),
                rawDistanceMeters = snapshot.rawDistanceMeters,
                filteredDistanceMeters = snapshot.filteredDistanceMeters,
                acceptedDistanceMeters = snapshot.acceptedDistanceMeters,
                elapsedTimeMs = snapshot.elapsedTimeMs,
                movingTimeMs = snapshot.movingTimeMs,
                fsmState = snapshot.fsmState.name,
                elevationGainMeters = snapshot.elevationGainMeters,
                elevationLossMeters = snapshot.elevationLossMeters,
                elevationAnchorMeters = snapshot.elevationAnchorMeters,
                elevationTrend = snapshot.elevationTrend,
                currentSplitDistanceMeters = snapshot.currentSplitDistanceMeters
            )
        }

        fun formatDoubleArray(array: DoubleArray): String {
            return array.joinToString(prefix = "[", postfix = "]", separator = ",") { it.toString() }
        }

        fun parseDoubleArray(json: String): DoubleArray {
            val trimmed = json.trim().removeSurrounding("[", "]").trim()
            if (trimmed.isEmpty()) return DoubleArray(0)
            return trimmed.split(",")
                .map { it.trim().toDouble() }
                .toDoubleArray()
        }
    }
}
