package com.apex.tracker.ui.state

import androidx.compose.runtime.Immutable
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class DeltaCategory {
    AHEAD,   // Faster than average (electric-lime)
    BEHIND,  // Slower than average (laser-amber)
    EVEN     // Within deadband ±2s (slate-subtle)
}

@Immutable
data class SplitDeltaDisplay(
    val formattedText: String,
    val category: DeltaCategory
)

object UiFormatters {

    /**
     * Formats pace in seconds per kilometer into MM:SS tabular format.
     * Clamps non-moving or extreme values (0, negative, NaN, Infinite, >= 3600) to "--:--".
     */
    fun formatPace(paceSecPerKm: Double): String {
        if (paceSecPerKm <= 0.0 || paceSecPerKm.isNaN() || paceSecPerKm.isInfinite() || paceSecPerKm >= 3600.0) {
            return "--:--"
        }
        val totalSec = paceSecPerKm.roundToInt()
        val minutes = totalSec / 60
        val seconds = totalSec % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    /**
     * Formats duration into HH:MM:SS or MM:SS.
     */
    fun formatDuration(totalSeconds: Long): String {
        if (totalSeconds < 0L) return "00:00"
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L

        return if (hours > 0L) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    /**
     * Formats distance meters to kilometers with 2 decimal precision.
     */
    fun formatDistanceKm(meters: Double): String {
        val clamped = if (meters < 0.0 || meters.isNaN() || meters.isInfinite()) 0.0 else meters
        return String.format(Locale.US, "%.2f", clamped / 1000.0)
    }

    /**
     * Formats elevation gain in integer meters with a plus sign.
     */
    fun formatElevationGain(meters: Double): String {
        val clamped = if (meters < 0.0 || meters.isNaN() || meters.isInfinite()) 0.0 else meters
        return "+${clamped.roundToInt()} m"
    }

    /**
     * Formats delta seconds vs average pace.
     * Deadband of ±2.0 seconds displays "● EVEN".
     * Negative delta means faster than average (ahead -> Lime).
     * Positive delta means slower than average (behind -> Amber).
     */
    fun formatPaceDelta(deltaSec: Double): SplitDeltaDisplay {
        if (deltaSec.isNaN() || deltaSec.isInfinite() || abs(deltaSec) <= 2.0) {
            return SplitDeltaDisplay("● EVEN", DeltaCategory.EVEN)
        }

        val absSec = abs(deltaSec).roundToInt()
        val min = absSec / 60
        val sec = absSec % 60
        val timeFormatted = String.format(Locale.US, "%d:%02d", min, sec)

        return if (deltaSec < -2.0) {
            SplitDeltaDisplay("▲ -$timeFormatted", DeltaCategory.AHEAD)
        } else {
            SplitDeltaDisplay("▼ +$timeFormatted", DeltaCategory.BEHIND)
        }
    }
}
