package com.apex.tracker.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BroadcastReceiver triggered by the operating system when the application package is updated.
 * Automatically purges the cached installer APK file to free up device storage.
 */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED && context != null) {
            AppUpdateManager.cleanupDownloadedApks(context)
        }
    }
}
