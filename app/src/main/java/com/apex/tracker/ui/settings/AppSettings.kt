package com.apex.tracker.ui.settings

import android.content.Context
import com.apex.tracker.ui.sound.ApexAudioFeedback

/**
 * Frequency options for playing the kinetic athletic intro animation.
 */
enum class IntroFrequency(val displayName: String, val shortLabel: String) {
    ALWAYS("EVERY LAUNCH", "ALWAYS"),
    WEEKLY("EVERY 7 DAYS", "7 DAYS"),
    NEVER("NEVER", "NEVER");

    companion object {
        fun fromString(value: String?): IntroFrequency {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: WEEKLY
        }
    }
}

/**
 * Distance measurement units.
 */
enum class DistanceUnit(val displayName: String, val unitLabel: String, val paceLabel: String) {
    METRIC("METRIC (KM)", "km", "/km"),
    IMPERIAL("IMPERIAL (MI)", "mi", "/mi");

    companion object {
        fun fromString(value: String?): DistanceUnit {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: METRIC
        }
    }
}

/**
 * Centralized preferences and settings manager for Apex Tracker.
 * Manages intro frequency, auto-update checks, audio feedback, unit preferences, and map options.
 */
object AppSettings {

    private const val PREFS_NAME = "apex_preferences"

    private const val KEY_INTRO_FREQUENCY = "key_intro_frequency"
    private const val KEY_LAST_INTRO_SHOWN_MS = "key_last_intro_shown_ms"
    private const val KEY_AUTO_CHECK_UPDATES = "key_auto_check_updates"
    private const val KEY_AUDIO_FEEDBACK = "key_audio_feedback"
    private const val KEY_DISTANCE_UNIT = "key_distance_unit"
    private const val KEY_SHOW_RAW_TRACE = "key_show_raw_trace"
    private const val KEY_DARK_THEME = "key_dark_theme"

    const val INTRO_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000L // 7 Days in milliseconds

    /**
     * Determines whether the kinetic intro animation should play on app launch.
     */
    fun shouldShowIntro(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val frequency = IntroFrequency.fromString(prefs.getString(KEY_INTRO_FREQUENCY, IntroFrequency.WEEKLY.name))

        return when (frequency) {
            IntroFrequency.ALWAYS -> true
            IntroFrequency.NEVER -> false
            IntroFrequency.WEEKLY -> {
                val lastShown = prefs.getLong(KEY_LAST_INTRO_SHOWN_MS, 0L)
                val now = System.currentTimeMillis()
                lastShown == 0L || (now - lastShown) >= INTRO_INTERVAL_MS
            }
        }
    }

    /**
     * Records that the intro animation was completed or skipped.
     */
    fun markIntroShown(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_INTRO_SHOWN_MS, System.currentTimeMillis()).apply()
    }

    fun getIntroFrequency(context: Context): IntroFrequency {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return IntroFrequency.fromString(prefs.getString(KEY_INTRO_FREQUENCY, IntroFrequency.WEEKLY.name))
    }

    fun setIntroFrequency(context: Context, frequency: IntroFrequency) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_INTRO_FREQUENCY, frequency.name).apply()
    }

    fun isAutoCheckUpdatesEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_CHECK_UPDATES, true)
    }

    fun setAutoCheckUpdates(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUTO_CHECK_UPDATES, enabled).apply()
    }

    fun isAudioFeedbackEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(KEY_AUDIO_FEEDBACK, true)
        ApexAudioFeedback.isEnabled = enabled
        return enabled
    }

    fun setAudioFeedback(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUDIO_FEEDBACK, enabled).apply()
        ApexAudioFeedback.isEnabled = enabled
    }

    fun getDistanceUnit(context: Context): DistanceUnit {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return DistanceUnit.fromString(prefs.getString(KEY_DISTANCE_UNIT, DistanceUnit.METRIC.name))
    }

    fun setDistanceUnit(context: Context, unit: DistanceUnit) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DISTANCE_UNIT, unit.name).apply()
    }

    fun isShowRawTraceEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SHOW_RAW_TRACE, false)
    }

    fun setShowRawTrace(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SHOW_RAW_TRACE, enabled).apply()
    }

    /**
     * Checks if the dark theme (OLED Obsidian) is selected.
     * Defaults to false (Warm Alabaster Beige-White palette).
     */
    fun isDarkThemeEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DARK_THEME, false)
    }

    /**
     * Persists the user's theme selection.
     */
    fun setDarkThemeEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DARK_THEME, enabled).apply()
    }
}
