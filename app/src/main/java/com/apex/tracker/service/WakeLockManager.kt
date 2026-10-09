package com.apex.tracker.service

import android.content.Context
import android.os.PowerManager

/**
 * Manages CPU Partial WakeLock to guarantee continuous background execution
 * while the device screen is off and locked.
 *
 * Implements reference counting and a 12-hour maximum safety timeout to prevent
 * permanent battery drain in case of abnormal service state.
 */
class WakeLockManager(
    private val context: Context,
    private val tag: String = "ApexTracker:TrackingWakeLock"
) {
    companion object {
        // 12-hour safety timeout (12 * 60 * 60 * 1000 ms)
        const val WAKE_LOCK_TIMEOUT_MS = 12 * 60 * 60 * 1000L
    }

    private val powerManager: PowerManager? =
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private val wakeLock: PowerManager.WakeLock? =
        powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag)?.apply {
            setReferenceCounted(true)
        }

    val isHeld: Boolean
        get() = wakeLock?.isHeld == true

    /**
     * Acquires the partial WakeLock with a 12-hour safety timeout.
     */
    @Synchronized
    fun acquire(timeoutMs: Long = WAKE_LOCK_TIMEOUT_MS) {
        wakeLock?.let { lock ->
            if (!lock.isHeld) {
                lock.acquire(timeoutMs)
            }
        }
    }

    /**
     * Safely releases the partial WakeLock if held.
     */
    @Synchronized
    fun release() {
        wakeLock?.let { lock ->
            try {
                if (lock.isHeld) {
                    lock.release()
                }
            } catch (_: Exception) {
                // Ignore if already released by OS or under-locked
            }
        }
    }
}
