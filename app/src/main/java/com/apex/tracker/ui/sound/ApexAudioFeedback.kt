package com.apex.tracker.ui.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.View
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Dedicated tactile audio and haptic feedback utility for crisp, subtle, tasteful click audio.
 *
 * Design constraints:
 * - Triggered ONLY when a user explicitly taps a button or interactive control.
 * - ZERO automated sound, zero periodic sounds, zero sounds during background sensor tracking.
 * - Warm, subtle mechanical transient click (~520Hz down to ~160Hz over 10ms with soft exponential decay).
 * - Gentle amplitude (~2400 out of 32767) to prevent harsh or metallic artifacts.
 * - Smooth Tukey cosine ramp-down to eliminate DC offset pops.
 * - Single playback per tap (no double-sound layering).
 * - Respects the user's silent mode (silent/vibrate) and the in-app audio feedback toggle.
 */
object ApexAudioFeedback {

    var isEnabled: Boolean = true

    private var lastClickTimeMs: Long = 0L
    private const val DEBOUNCE_MS = 40L

    @Volatile
    private var isInitialized = false
    private var clickAudioTrack: AudioTrack? = null

    private fun ensureAudioInitialized() {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            isInitialized = true
            try {
                val sampleRate = 44100
                val durationSec = 0.010 // 10 milliseconds
                val numSamples = (sampleRate * durationSec).toInt()
                val buffer = ShortArray(numSamples)

                // Synthesize a warm, subtle tactile chronometer micro-click:
                // Soft transient impulse (520Hz decaying to 160Hz) with rapid exponential decay
                // and a smooth window envelope to avoid square edges or high-frequency hiss.
                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val progress = i.toDouble() / numSamples
                    val decay = exp(-t * 460.0)
                    val freq = 520.0 * exp(-t * 120.0)
                    val wave = sin(2.0 * Math.PI * freq * t)

                    // Cosine window ramp-down on final 20% of samples
                    val window = if (progress > 0.8) {
                        0.5 * (1.0 + cos(Math.PI * (progress - 0.8) / 0.2))
                    } else {
                        1.0
                    }

                    // Gentle amplitude (2400) gives a satisfying subtle tactile tick
                    buffer[i] = (wave * decay * window * 2400.0).toInt().coerceIn(-32768, 32767).toShort()
                }

                val attributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .build()

                val format = AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()

                val track = AudioTrack(
                    attributes,
                    format,
                    buffer.size * 2,
                    AudioTrack.MODE_STATIC,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                )
                track.write(buffer, 0, buffer.size)
                clickAudioTrack = track
            } catch (_: Throwable) {
                // Safe in headless unit tests or restricted environments
            }
        }
    }

    /**
     * Plays a crisp tactile click sound and haptic tap using the active [View].
     */
    fun playClick(view: View?) {
        if (!isEnabled || view == null) return
        val now = SystemClock.uptimeMillis()
        if (now - lastClickTimeMs < DEBOUNCE_MS) return
        lastClickTimeMs = now

        try {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        } catch (_: Throwable) {}

        val audioManager = view.context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager != null && audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT) {
            return
        }

        playWarmTactileClick(view)
    }

    /**
     * Plays a subtle click sound using [Context] / [AudioManager].
     */
    fun playClick(context: Context?) {
        if (!isEnabled || context == null) return
        val now = SystemClock.uptimeMillis()
        if (now - lastClickTimeMs < DEBOUNCE_MS) return
        lastClickTimeMs = now

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager != null && audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT) {
            return
        }

        playWarmTactileClick(null)
    }

    private fun playWarmTactileClick(view: View?) {
        try {
            ensureAudioInitialized()
            val track = clickAudioTrack
            if (track != null && track.state == AudioTrack.STATE_INITIALIZED) {
                track.stop()
                track.reloadStaticData()
                track.play()
                return
            }
        } catch (_: Throwable) {}

        // Fallback: standard system click if custom AudioTrack is unavailable
        try {
            view?.playSoundEffect(SoundEffectConstants.CLICK)
        } catch (_: Throwable) {}
    }
}
