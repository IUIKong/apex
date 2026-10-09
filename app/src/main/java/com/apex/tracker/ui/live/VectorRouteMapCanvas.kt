package com.apex.tracker.ui.live

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.apex.tracker.ui.state.TrackPointDto

/**
 * Backward-compatible wrapper delegating to [ParchmentMapCanvas].
 */
@Composable
fun VectorRouteMapCanvas(
    trackPoints: List<TrackPointDto>,
    currentBearingDegrees: Float,
    currentAccuracyMeters: Float,
    showRawTrace: Boolean = true,
    autoFollow: Boolean = true,
    onToggleRawTrace: () -> Unit = {},
    onToggleAutoFollow: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    ParchmentMapCanvas(
        trackPoints = trackPoints,
        currentBearingDegrees = currentBearingDegrees,
        currentAccuracyMeters = currentAccuracyMeters,
        showRawTrace = showRawTrace,
        autoFollow = autoFollow,
        onToggleRawTrace = onToggleRawTrace,
        onToggleAutoFollow = onToggleAutoFollow,
        modifier = modifier
    )
}
