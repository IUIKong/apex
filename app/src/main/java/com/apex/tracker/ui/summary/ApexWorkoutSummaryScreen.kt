package com.apex.tracker.ui.summary

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.apex.tracker.ui.sound.ApexAudioFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apex.tracker.ui.components.ApexLogoMark
import com.apex.tracker.ui.components.MetricCard
import com.apex.tracker.ui.components.tactilePress
import com.apex.tracker.ui.state.UiFormatters
import com.apex.tracker.ui.state.WorkoutSummaryUiState
import com.apex.tracker.ui.theme.ApexDimens
import com.apex.tracker.ui.theme.ApexTheme
import kotlinx.coroutines.launch

import java.util.Locale

@Composable
fun ApexWorkoutSummaryScreen(
    summaryState: WorkoutSummaryUiState,
    onDoneClick: () -> Unit = {},
    onDeleteClick: (() -> Unit)? = null,
    onShareClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isSharing by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val colors = ApexTheme.colors
    val typography = ApexTheme.typography
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val distKm = UiFormatters.formatDistanceKm(summaryState.totalDistanceMeters)
    val movingTimeStr = UiFormatters.formatDuration(summaryState.movingTimeSeconds)
    val elapsedTimeStr = UiFormatters.formatDuration(summaryState.elapsedTimeSeconds)
    val avgPaceStr = UiFormatters.formatPace(summaryState.avgPaceSecPerKm)
    val avgSpeedKmh = if (summaryState.movingTimeSeconds > 0) {
        (summaryState.totalDistanceMeters / summaryState.movingTimeSeconds) * 3.6
    } else 0.0
    val avgSpeedStr = String.format(Locale.US, "%.1f", avgSpeedKmh)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvasBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(
                    horizontal = ApexDimens.PaddingScreenHorizontal,
                    vertical = ApexDimens.PaddingScreenVertical
                ),
            verticalArrangement = Arrangement.spacedBy(ApexDimens.SpacingCards)
        ) {
            // Header: Clean Title & Sport Badge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(ApexDimens.RadiusCardStandard))
                    .background(colors.surface)
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(ApexDimens.RadiusCardStandard))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ApexLogoMark(size = 30.dp)
                        Column {
                            Text(
                                text = "WORKOUT SUMMARY",
                                style = typography.LabelMicro.copy(
                                    color = colors.electricCyan,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.2.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = summaryState.title,
                                style = typography.Headline.copy(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = colors.textPrimary
                                )
                            )
                        }
                    }

                    // Activity Type Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(ApexDimens.RadiusPillFull))
                            .background(colors.surface)
                            .border(1.dp, colors.borderSubtle, RoundedCornerShape(ApexDimens.RadiusPillFull))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = summaryState.activityType.uppercase(),
                            style = typography.LabelMicro.copy(
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            // 1. 4-Grid Core Metrics Overview
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ApexDimens.SpacingCards)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ApexDimens.SpacingCards)
                ) {
                    MetricCard(
                        label = "DISTANCE",
                        value = distKm,
                        unit = "km",
                        subtext = "Accepted trajectory",
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        label = "MOVING TIME",
                        value = movingTimeStr,
                        subtext = "Elapsed: $elapsedTimeStr",
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ApexDimens.SpacingCards)
                ) {
                    MetricCard(
                        label = "AVG PACE",
                        value = avgPaceStr,
                        unit = "/km",
                        subtext = "Moving pace",
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        label = "AVG SPEED",
                        value = avgSpeedStr,
                        unit = "km/h",
                        subtext = "GPS telemetry",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 2. Circuit Route Map Canvas
            CircuitRouteMapCanvas(
                trackPoints = summaryState.trackPoints
            )

            // 3. Kilometer Splits Breakdown Table
            SplitsTable(
                splits = summaryState.splits
            )

            // 4. Share Activity Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ApexDimens.HeightTacticalButton)
                    .clip(RoundedCornerShape(ApexDimens.RadiusButtonTactical))
                    .background(colors.surfaceElevated)
                    .border(1.5.dp, colors.electricCyan, RoundedCornerShape(ApexDimens.RadiusButtonTactical))
                    .tactilePress(pressedScale = 0.96f, enabled = !isSharing) {
                        if (onShareClick != null) {
                            onShareClick()
                        } else {
                            coroutineScope.launch {
                                isSharing = true
                                try {
                                    WorkoutShareHelper.shareWorkoutSummary(context, summaryState)
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        "Could not share workout: ${e.localizedMessage}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } finally {
                                    isSharing = false
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isSharing) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(16.dp).height(16.dp),
                            color = colors.electricCyan,
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "PREPARING SHARE CARD...",
                            style = typography.LabelUppercase.copy(
                                fontSize = 12.sp,
                                color = colors.electricCyan,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share Activity",
                            tint = colors.electricCyan,
                            modifier = Modifier.width(18.dp).height(18.dp)
                        )
                        Text(
                            text = "SHARE ACTIVITY",
                            style = typography.LabelUppercase.copy(
                                fontSize = 12.sp,
                                color = colors.electricCyan,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        )
                    }
                }
            }

            // Return / Done Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ApexDimens.HeightTacticalButton)
                    .clip(RoundedCornerShape(ApexDimens.RadiusButtonTactical))
                    .background(colors.textPrimary)
                    .tactilePress(pressedScale = 0.96f) { onDoneClick() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "BACK TO LOGBOOK",
                    style = typography.LabelUppercase.copy(
                        fontSize = 12.sp,
                        color = colors.canvasBackground,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                )
            }

            // Delete Workout Link if available
            if (onDeleteClick != null && summaryState.activityId.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .tactilePress(pressedScale = 0.95f) { showDeleteConfirmDialog = true }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "DELETE THIS WORKOUT",
                        style = typography.LabelMicro.copy(
                            color = colors.punchyCrimson,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )
                }
            }

            if (showDeleteConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirmDialog = false },
                    title = {
                        Text(
                            text = "Delete Workout?",
                            style = typography.Headline.copy(color = colors.textPrimary)
                        )
                    },
                    text = {
                        Text(
                            text = "This will permanently remove this recorded session from your device.",
                            style = typography.BodyText.copy(color = colors.slateSubtle)
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                ApexAudioFeedback.playClick(view)
                                showDeleteConfirmDialog = false
                                onDeleteClick?.invoke()
                            }
                        ) {
                            Text("DELETE", color = colors.punchyCrimson, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                ApexAudioFeedback.playClick(view)
                                showDeleteConfirmDialog = false
                            }
                        ) {
                            Text("CANCEL", color = colors.slateMuted)
                        }
                    },
                    containerColor = colors.surfaceElevated
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
