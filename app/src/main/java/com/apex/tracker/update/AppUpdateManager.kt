package com.apex.tracker.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-App Autonomous Update Manager.
 * Checks GitHub Releases API for new releases, downloads the APK with real-time progress,
 * and directly launches the system installer without manual intervention.
 */
class AppUpdateManager(
    private val repoOwner: String = "IUIKong",
    private val repoName: String = "apex"
) {
    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val status: StateFlow<UpdateStatus> = _status.asStateFlow()

    private val _isManualCheck = MutableStateFlow(false)
    val isManualCheck: StateFlow<Boolean> = _isManualCheck.asStateFlow()

    fun resetStatus() {
        _status.value = UpdateStatus.Idle
        _isManualCheck.value = false
    }

    /**
     * Checks GitHub API for the latest release and updates [_status].
     * @param isManual When true (e.g. user tapped "Check Updates"), sets Checking/UpToDate/Error so user gets explicit feedback.
     *                 When false (e.g. background on launch/resume), stays silent unless a new update is Available.
     */
    suspend fun checkForUpdates(
        currentVersion: String,
        isManual: Boolean = false
    ): UpdateInfo? = withContext(Dispatchers.IO) {
        _isManualCheck.value = isManual
        if (isManual) {
            _status.value = UpdateStatus.Checking
        } else {
            _status.value = UpdateStatus.Idle
        }
        try {
            val endpoint = "https://api.github.com/repos/$repoOwner/$repoName/releases/latest"
            val conn = openConnection(endpoint)
            val responseCode = conn.responseCode

            if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                if (isManual) {
                    _status.value = UpdateStatus.UpToDate(currentVersion)
                } else {
                    _status.value = UpdateStatus.Idle
                }
                return@withContext null
            }

            if (responseCode !in 200..299) {
                throw IOException("GitHub API returned HTTP $responseCode")
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)

            val tagName = json.optString("tag_name", "").trim()
            val releaseTitle = json.optString("name", tagName)
            val releaseNotes = json.optString("body", "").trim()
            val assets = json.optJSONArray("assets")

            var apkDownloadUrl = ""
            var apkSize = 0L

            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val assetName = asset.optString("name", "")
                    val downloadUrl = asset.optString("browser_download_url", "")
                    val size = asset.optLong("size", 0L)
                    if (assetName.endsWith(".apk", ignoreCase = true)) {
                        apkDownloadUrl = downloadUrl
                        apkSize = size
                        break
                    }
                }
            }

            if (apkDownloadUrl.isNotEmpty() && isNewerVersion(tagName, currentVersion)) {
                val info = UpdateInfo(
                    versionName = tagName,
                    releaseTitle = releaseTitle,
                    releaseNotes = releaseNotes,
                    downloadUrl = apkDownloadUrl,
                    fileSizeEstimateBytes = apkSize
                )
                _status.value = UpdateStatus.Available(info)
                return@withContext info
            } else {
                if (isManual) {
                    _status.value = UpdateStatus.UpToDate(currentVersion)
                } else {
                    _status.value = UpdateStatus.Idle
                }
                return@withContext null
            }
        } catch (e: Exception) {
            if (isManual) {
                _status.value = UpdateStatus.Error(e.message ?: "Failed to check for updates")
            } else {
                _status.value = UpdateStatus.Idle
            }
            return@withContext null
        }
    }

    /**
     * Downloads the APK file to cache with real-time byte tracking and launches installation.
     */
    suspend fun downloadAndInstall(
        context: Context,
        updateInfo: UpdateInfo
    ) = withContext(Dispatchers.IO) {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apkFile = File(updatesDir, "apex_${updateInfo.versionName.replace(".", "_")}.apk")

        try {
            _status.value = UpdateStatus.Downloading(
                updateInfo = updateInfo,
                progressPercent = 0,
                downloadedBytes = 0L,
                totalBytes = updateInfo.fileSizeEstimateBytes
            )

            val conn = openConnectionWithRedirects(updateInfo.downloadUrl)
            val contentLength = conn.contentLengthLong.let { if (it > 0) it else updateInfo.fileSizeEstimateBytes }

            conn.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var bytesRead: Int
                    var totalRead = 0L
                    var lastReportedPercent = -1

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead

                        val percent = if (contentLength > 0) {
                            ((totalRead * 100) / contentLength).toInt().coerceIn(0, 100)
                        } else 50

                        if (percent != lastReportedPercent) {
                            lastReportedPercent = percent
                            _status.value = UpdateStatus.Downloading(
                                updateInfo = updateInfo,
                                progressPercent = percent,
                                downloadedBytes = totalRead,
                                totalBytes = contentLength
                            )
                        }
                    }
                    output.flush()
                }
            }

            _status.value = UpdateStatus.ReadyToInstall(updateInfo, apkFile)

            // Auto-trigger installation on the main thread
            withContext(Dispatchers.Main) {
                installApk(context, apkFile)
            }
        } catch (e: Exception) {
            _status.value = UpdateStatus.Error(e.message ?: "Download failed")
        }
    }

    /**
     * Launches the system package installer intent for the downloaded APK via FileProvider.
     */
    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) {
            _status.value = UpdateStatus.Error("APK file not found")
            return
        }

        // Android 8.0+ unknown sources permission check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            }
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
    }

    private fun openConnection(urlString: String): HttpURLConnection {
        val url = URL(urlString)
        return (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 12000
            setRequestProperty("User-Agent", "Apex-Android-Tracker")
            setRequestProperty("Accept", "application/vnd.github.v3+json")
        }
    }

    private fun openConnectionWithRedirects(initialUrl: String, maxRedirects: Int = 6): HttpURLConnection {
        var currentUrl = initialUrl
        var redirects = 0

        while (redirects < maxRedirects) {
            val url = URL(currentUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("User-Agent", "Apex-Android-Tracker")
                instanceFollowRedirects = true
            }

            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (!location.isNullOrBlank()) {
                    currentUrl = location
                    redirects++
                    continue
                }
            }
            return conn
        }
        throw IOException("Too many HTTP redirects ($redirects) while downloading update")
    }

    companion object {
        /**
         * Compares two semantic version strings (e.g. "v1.0.1" vs "1.0.0").
         * Returns true if [latestVersion] is strictly greater than [currentVersion].
         */
        fun isNewerVersion(latestVersion: String, currentVersion: String): Boolean {
            val cleanLatest = latestVersion.trim().removePrefix("v").removePrefix("V")
            val cleanCurrent = currentVersion.trim().removePrefix("v").removePrefix("V")

            val latestTokens = cleanLatest.split(".", "-", "_").mapNotNull { it.toIntOrNull() }
            val currentTokens = cleanCurrent.split(".", "-", "_").mapNotNull { it.toIntOrNull() }

            if (latestTokens.isEmpty() || currentTokens.isEmpty()) {
                return cleanLatest != cleanCurrent && cleanLatest.isNotBlank()
            }

            val maxLen = maxOf(latestTokens.size, currentTokens.size)
            for (i in 0 until maxLen) {
                val l = latestTokens.getOrElse(i) { 0 }
                val c = currentTokens.getOrElse(i) { 0 }
                if (l > c) return true
                if (l < c) return false
            }
            return false
        }
    }
}
