package com.apex.tracker.update

import androidx.compose.runtime.Immutable
import java.io.File

@Immutable
data class UpdateInfo(
    val versionName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val fileSizeEstimateBytes: Long
)

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data class Available(val updateInfo: UpdateInfo) : UpdateStatus
    data class UpToDate(val currentVersion: String) : UpdateStatus
    data class Downloading(
        val updateInfo: UpdateInfo,
        val progressPercent: Int,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : UpdateStatus
    data class ReadyToInstall(
        val updateInfo: UpdateInfo,
        val apkFile: File
    ) : UpdateStatus
    data class Error(val message: String) : UpdateStatus
}
