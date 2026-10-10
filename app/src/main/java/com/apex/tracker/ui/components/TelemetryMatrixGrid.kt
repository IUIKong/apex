package com.apex.tracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apex.tracker.ui.state.LiveHudUiState
import com.apex.tracker.ui.state.UiFormatters
import com.apex.tracker.ui.theme.ApexDimens
import com.apex.tracker.ui.theme.ApexTheme
import java.util.Locale

@Composable
fun MetricCard(
    label: String,
    value: String,
    unit: String? = null,
    subtext: String? = null,
    modifier: Modifier = Modifier
) {
    val colors = ApexTheme.colors
    val typography = ApexTheme.typography
    val cardShape = remember { RoundedCornerShape(ApexDimens.RadiusCardStandard) }
    Box(
        modifier = modifier
            .clip(cardShape)
            .background(colors.surface)
            .border(
                width = 1.dp,
                color = colors.borderSubtle,
                shape = cardShape
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Column {
            Text(
                text = label,
                style = typography.LabelUppercase.copy(fontSize = 10.sp),
                maxLines = 1,
                softWrap = false
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                AnimatedNumeralTicker(
                    text = value,
                    style = typography.MetricMedium
                )
                if (unit != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = unit,
                        style = typography.LabelUppercase.copy(
                            fontSize = 10.sp,
                            color = colors.slateSubtle
                        ),
                        modifier = Modifier.padding(bottom = 2.dp),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            if (subtext != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtext,
                    style = typography.TelemetryMicro,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
fun TelemetryMatrixGrid(
    state: LiveHudUiState,
    modifier: Modifier = Modifier
) {
    val distKm = UiFormatters.formatDistanceKm(state.acceptedDistanceMeters)
    val rawKm = UiFormatters.formatDistanceKm(state.rawDistanceMeters)
    val movingTimeStr = UiFormatters.formatDuration(state.movingTimeSeconds)
    val elapsedTimeStr = UiFormatters.formatDuration(state.elapsedTimeSeconds)
    val avgPaceStr = UiFormatters.formatPace(state.averagePaceSecPerKm)

    val avgSpeedKmh = if (state.movingTimeSeconds > 0) {
        (state.acceptedDistanceMeters / state.movingTimeSeconds) * 3.6
    } else 0.0

    val speedStr = String.format(Locale.US, "speed: %.1f km/h", avgSpeedKmh)
    val speedValue = String.format(Locale.US, "%.1f", avgSpeedKmh)

    val hrValue = if (state.heartRateBpm > 0) state.heartRateBpm.toString() else "--"
    val hrSubtext = if (state.heartRateBpm > 0) {
        when {
            state.heartRateBpm < 120 -> "Zone 1 Recovery"
            state.heartRateBpm < 145 -> "Zone 2 Aerobic"
            state.heartRateBpm < 165 -> "Zone 3 Tempo"
            state.heartRateBpm < 180 -> "Zone 4 Threshold"
            else -> "Zone 5 Neuromuscular"
        }
    } else "no sensor"

    val cadenceValue = if (state.cadence > 0) state.cadence.toString() else "--"

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ApexDimens.SpacingCards)
    ) {
        // Row 1: Distance, Moving Time, Average Speed
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ApexDimens.SpacingCards)
        ) {
            MetricCard(
                label = "DISTANCE",
                value = distKm,
                unit = "km",
                subtext = "raw: $rawKm km",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = "MOVING TIME",
                value = movingTimeStr,
                subtext = "elapsed: $elapsedTimeStr",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = "AVG SPEED",
                value = speedValue,
                unit = "km/h",
                subtext = "GPS telemetry",
                modifier = Modifier.weight(1f)
            )
        }

        // Row 2: Average Pace, Heart Rate, Cadence
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ApexDimens.SpacingCards)
        ) {
            MetricCard(
                label = "AVG PACE",
                value = avgPaceStr,
                unit = "/km",
                subtext = speedStr,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = "HEART RATE",
                value = hrValue,
                unit = if (state.heartRateBpm > 0) "bpm" else null,
                subtext = hrSubtext,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = "CADENCE",
                value = cadenceValue,
                unit = if (state.cadence > 0) "spm" else null,
                subtext = "rhythm cadence",
                modifier = Modifier.weight(1f)
            )
        }
    }
}
