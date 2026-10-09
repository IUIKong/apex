package com.apex.tracker.database

import androidx.room.withTransaction
import com.apex.tracker.core.model.SessionSnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RoomCheckpointRepository(
    private val database: AppDatabase? = null,
    private val checkpointDao: CheckpointDao = database?.checkpointDao()
        ?: throw IllegalArgumentException("Either database or checkpointDao must be provided"),
    private val trackPointDao: TrackPointDao = database?.trackPointDao()
        ?: throw IllegalArgumentException("Either database or trackPointDao must be provided"),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : CheckpointRepository {

    constructor(
        checkpointDao: CheckpointDao,
        trackPointDao: TrackPointDao,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ) : this(
        database = null,
        checkpointDao = checkpointDao,
        trackPointDao = trackPointDao,
        ioDispatcher = ioDispatcher
    )

    override suspend fun saveCheckpoint(
        snapshot: SessionSnapshot,
        recentTrackPoints: List<TrackPointEntity>
    ): Unit = withContext(ioDispatcher) {
        val entity = ActiveSessionCheckpointEntity.fromSessionSnapshot(snapshot)
        val db = database
        if (db != null) {
            db.withTransaction {
                if (recentTrackPoints.isNotEmpty()) {
                    trackPointDao.insertTrackPoints(recentTrackPoints)
                }
                checkpointDao.upsertCheckpoint(entity)
            }
        } else {
            if (recentTrackPoints.isNotEmpty()) {
                trackPointDao.insertTrackPoints(recentTrackPoints)
            }
            checkpointDao.upsertCheckpoint(entity)
        }
    }

    override suspend fun getActiveCheckpoint(): SessionSnapshot? = withContext(ioDispatcher) {
        val entity = checkpointDao.getCheckpoint() ?: return@withContext null
        entity.toSessionSnapshot()
    }

    override suspend fun clearActiveCheckpoint(workoutId: String): Unit = withContext(ioDispatcher) {
        checkpointDao.deleteCheckpointByWorkoutId(workoutId)
    }
}
