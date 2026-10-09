package com.apex.tracker.ui.sound

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.SoundEffectConstants
import android.view.View

/**
 * Dedicated audio feedback utility for subtle, tasteful click audio.
 *
 * Design and architectural constraints:
 * - Triggered ONLY when a user explicitly taps a button or interactive control.
 * - ZERO automated sound, zero periodic sounds, zero sounds during background sensor tracking.
 * - Utilizes Android's [SoundEffectConstants.CLICK] via [View.playSoundEffect]
 *   with fallback to [AudioManager.playSoundEffect] (FX_KEY_CLICK).
 * - Debounced to 40ms to avoid unintentional double clicks.
 * - Respects the user's system sound settings and silent mode.
 */
object ApexAudioFeedback {

    private var lastClickTimeMs: Long = 0L
    private const val DEBOUNCE_MS = 40L

    /**
     * Plays a subtle, tasteful tactile click sound using the active [View].
     * Uses Android's [SoundEffectConstants.CLICK] via [View.playSoundEffect].
     */
    fun playClick(view: View?) {
        if (view == null) return
        val now = SystemClock.uptimeMillis()
        if (now - lastClickTimeMs < DEBOUNCE_MS) return
        lastClickTimeMs = now

        try {
            view.playSoundEffect(SoundEffectConstants.CLICK)
        } catch (_: Throwable) {
            try {
                val audioManager = view.context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                audioManager?.playSoundEffect(AudioManager.FX_KEY_CLICK, 0.4f)
            } catch (_: Throwable) {}
        }
    }

    /**
     * Plays a subtle click sound using [Context] / [AudioManager].
     */
    fun playClick(context: Context?) {
        if (context == null) return
        val now = SystemClock.uptimeMillis()
        if (now - lastClickTimeMs < DEBOUNCE_MS) return
        lastClickTimeMs = now

        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.playSoundEffect(AudioManager.FX_KEY_CLICK, 0.4f)
        } catch (_: Throwable) {}
    }
}
