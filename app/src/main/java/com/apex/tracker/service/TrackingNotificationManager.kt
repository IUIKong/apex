package com.apex.tracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.Locale

/**
 * Manages the ongoing sticky notification for [TrackingForegroundService].
 *
 * Displays live workout telemetry (distance, pace, elapsed time) and provides
 * quick-access action buttons (Pause/Resume, Finish).
 */
class TrackingNotificationManager(
    private val context: Context
) {
    companion object {
        const val CHANNEL_ID = "apex_tracker_recording_channel"
        const val CHANNEL_NAME = "Apex Workout Recording"
        const val CHANNEL_WARNING_ID = "apex_tracker_warning_channel"
        const val CHANNEL_WARNING_NAME = "Apex Alerts"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PAUSE = "com.apex.tracker.action.PAUSE"
        const val ACTION_RESUME = "com.apex.tracker.action.RESUME"
        const val ACTION_STOP = "com.apex.tracker.action.STOP"

        fun formatDistance(distanceMeters: Double): String {
            val km = distanceMeters / 1000.0
            return String.format(Locale.US, "%.2f km", km)
        }

        fun formatPace(paceSecPerKm: Double): String {
            if (paceSecPerKm <= 0.0 || paceSecPerKm.isInfinite() || paceSecPerKm.isNaN() || paceSecPerKm > 3600.0) {
                return "--:-- /km"
            }
            val totalSec = paceSecPerKm.toInt()
            val minutes = totalSec / 60
            val seconds = totalSec % 60
            return String.format(Locale.US, "%d:%02d /km", minutes, seconds)
        }

        fun formatElapsedTime(elapsedSeconds: Long): String {
            val hours = elapsedSeconds / 3600
            val minutes = (elapsedSeconds % 3600) / 60
            val seconds = elapsedSeconds % 60
            return if (hours > 0) {
                String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.US, "%02d:%02d", minutes, seconds)
            }
        }
    }

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val recordingChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Live GPS workout recording and telemetry status"
                setShowBadge(false)
            }
            val warningChannel = NotificationChannel(
                CHANNEL_WARNING_ID,
                CHANNEL_WARNING_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority alerts for disabled location services and sensors"
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(recordingChannel)
            notificationManager.createNotificationChannel(warningChannel)
        }
    }

    /**
     * Builds the persistent notification for foreground service attachment and updates.
     */
    fun buildNotification(
        distanceMeters: Double,
        paceSecPerKm: Double,
        elapsedSeconds: Long,
        isPaused: Boolean,
        isLocationDisabled: Boolean = false
    ): Notification {
        val openAppIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent().apply {
                setClassName(context.packageName, "com.apex.tracker.MainActivity")
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val formattedDistance = formatDistance(distanceMeters)
        val formattedPace = formatPace(paceSecPerKm)
        val formattedTime = formatElapsedTime(elapsedSeconds)

        val statusText = if (isLocationDisabled) {
            "GPS DISABLED"
        } else if (isPaused) {
            "PAUSED"
        } else {
            "RECORDING"
        }

        val contentText = if (isLocationDisabled) {
            "GPS Disabled — Turn on location in settings to track activity."
        } else {
            "$formattedDistance | $formattedPace | $formattedTime"
        }

        val channelId = if (isLocationDisabled) CHANNEL_WARNING_ID else CHANNEL_ID
        val priority = if (isLocationDisabled) {
            NotificationCompat.PRIORITY_HIGH
        } else {
            NotificationCompat.PRIORITY_LOW
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Apex — $statusText")
            .setContentText(contentText)
            .setSmallIcon(if (isLocationDisabled) android.R.drawable.stat_notify_error else android.R.drawable.ic_menu_compass)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(priority)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)

        // When location is disabled, provide direct "Enable GPS" action
        if (isLocationDisabled) {
            val settingsIntent = Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val settingsPendingIntent = PendingIntent.getActivity(
                context,
                4,
                settingsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_menu_mylocation, "Enable GPS", settingsPendingIntent)
        }

        // Add Pause / Resume action button
        if (isPaused) {
            val resumeIntent = Intent(context, TrackingForegroundService::class.java).apply {
                action = ACTION_RESUME
            }
            val resumePendingIntent = PendingIntent.getService(
                context,
                1,
                resumeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_media_play, "Resume", resumePendingIntent)
        } else {
            val pauseIntent = Intent(context, TrackingForegroundService::class.java).apply {
                action = ACTION_PAUSE
            }
            val pausePendingIntent = PendingIntent.getService(
                context,
                2,
                pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_media_pause, "Pause", pausePendingIntent)
        }

        // Add Finish / Stop action button
        val stopIntent = Intent(context, TrackingForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            3,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Finish", stopPendingIntent)

        return builder.build()
    }

    /**
     * Updates the active ongoing notification.
     */
    fun updateNotification(
        distanceMeters: Double,
        paceSecPerKm: Double,
        elapsedSeconds: Long,
        isPaused: Boolean,
        isLocationDisabled: Boolean = false
    ): Notification {
        val notification = buildNotification(distanceMeters, paceSecPerKm, elapsedSeconds, isPaused, isLocationDisabled)
        notificationManager.notify(NOTIFICATION_ID, notification)
        return notification
    }
}
