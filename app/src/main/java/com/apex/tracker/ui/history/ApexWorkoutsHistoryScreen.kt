package com.apex.tracker.ui.history

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.apex.tracker.ui.components.tactilePress
import kotlinx.coroutines.delay
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalView
import com.apex.tracker.ui.sound.ApexAudioFeedback
import com.apex.tracker.database.WorkoutEntity
import com.apex.tracker.ui.state.UiFormatters
import com.apex.tracker.ui.theme.ApexDimens
import com.apex.tracker.ui.theme.ApexTheme
import com.apex.tracker.update.UpdateStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ApexWorkoutsHistoryScreen(
    workouts: List<WorkoutEntity>,
    onWorkoutSelected: (String) -> Unit,
    onDeleteWorkout: (String) -> Unit,
    onCheckForUpdates: (() -> Unit)? = null,
    updateStatus: UpdateStatus = UpdateStatus.Idle,
    onOpenSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    var workoutToDelete by remember { mutableStateOf<WorkoutEntity?>(null) }

    // Weekly calculations
    val totalDistanceMeters = workouts.sumOf { it.totalDistanceMeters }
    val totalMovingTimeMs = workouts.sumOf { it.totalMovingTimeMs }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ApexTheme.colors.canvasBackground)
            .padding(16.dp)
    ) {
        if (workouts.isEmpty()) {
            EmptyWorkoutsCard(modifier = Modifier.fillMaxSize())
        } else {
            // Weekly Volume Summary Card
            WeeklySummaryCard(
                totalDistanceKm = totalDistanceMeters / 1000.0,
                totalDurationSec = totalMovingTimeMs / 1000L,
                sessionsCount = workouts.size
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Workouts List - Clean, elegant stream of workouts
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(workouts, key = { _, workout -> workout.id }) { index, workout ->
                    WorkoutHistoryItemCard(
                        workout = workout,
                        onClick = { onWorkoutSelected(workout.id) },
                        onDeleteClick = { workoutToDelete = workout },
                        staggerIndex = index,
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }

    // Delete Confirmation Dialog
    workoutToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { workoutToDelete = null },
            title = {
                Text(
                    text = "Delete Activity?",
                    style = ApexTheme.typography.Headline.copy(fontSize = 18.sp, color = ApexTheme.colors.textPrimary)
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to permanently delete this ${target.activityType.lowercase()} session? This action cannot be undone.",
                    style = ApexTheme.typography.BodyText.copy(color = ApexTheme.colors.slateSubtle)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        ApexAudioFeedback.playClick(view)
                        onDeleteWorkout(target.id)
                        workoutToDelete = null
                    },
                    modifier = Modifier.tactilePress(pressedScale = 0.92f)
                ) {
                    Text(
                        text = "DELETE",
                        style = ApexTheme.typography.LabelUppercase.copy(color = ApexTheme.colors.punchyCrimson)
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        ApexAudioFeedback.playClick(view)
                        workoutToDelete = null
                    },
                    modifier = Modifier.tactilePress(pressedScale = 0.92f)
                ) {
                    Text(
                        text = "CANCEL",
                        style = ApexTheme.typography.LabelUppercase.copy(color = ApexTheme.colors.slateMuted)
                    )
                }
            },
            containerColor = ApexTheme.colors.surface,
            shape = remember { RoundedCornerShape(ApexDimens.RadiusCardStandard) }
        )
    }
}

@Composable
private fun WeeklySummaryCard(
    totalDistanceKm: Double,
    totalDurationSec: Long,
    sessionsCount: Int,
    modifier: Modifier = Modifier
) {
    val overallPaceSec = if (totalDistanceKm > 0.05) {
        (totalDurationSec / totalDistanceKm).toDouble()
    } else 0.0
    val overallPaceStr = if (overallPaceSec > 0.0) UiFormatters.formatPace(overallPaceSec) else "--:--"

    val cardShape = remember { RoundedCornerShape(ApexDimens.RadiusCardStandard) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(ApexTheme.colors.surface)
            .border(1.dp, ApexTheme.colors.borderSubtle, cardShape)
    ) {
        // Subtle athletic hairline gradient accent harmonized with intro palette
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF00F5D4), // Cyan
                            Color(0xFF00FF87), // Lime
                            Color(0xFF38BDF8), // Azure
                            Color(0xFFF59E0B), // Amber
                            Color(0xFFFF3B56)  // Crimson
                        )
                    )
                )
        )
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LIFETIME TOTALS",
                    style = ApexTheme.typography.LabelMicro.copy(
                        fontWeight = FontWeight.Black,
                        color = ApexTheme.colors.electricCyan,
                        letterSpacing = 1.2.sp
                    )
                )
                Text(
                    text = "$sessionsCount SESSIONS",
                    style = ApexTheme.typography.LabelMicro.copy(
                        color = ApexTheme.colors.slateMuted
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetricColumn(
                    value = String.format(Locale.US, "%.1f", totalDistanceKm),
                    unit = "KM",
                    label = "DISTANCE",
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start
                )
                MetricColumn(
                    value = UiFormatters.formatDuration(totalDurationSec),
                    unit = "",
                    label = "TIME",
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                )
                MetricColumn(
                    value = overallPaceStr,
                    unit = "/KM",
                    label = "AVG PACE",
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                )
            }
        }
    }
}

@Composable
private fun MetricColumn(
    value: String,
    unit: String,
    label: String,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start
) {
    Column(modifier = modifier, horizontalAlignment = horizontalAlignment) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = ApexTheme.typography.MetricLarge.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = ApexTheme.colors.textPrimary
                ),
                maxLines = 1,
                softWrap = false
            )
            if (unit.isNotEmpty()) {
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = unit,
                    style = ApexTheme.typography.LabelMicro.copy(
                        color = ApexTheme.colors.slateMuted,
                        fontSize = 9.sp
                    ),
                    modifier = Modifier.padding(bottom = 3.dp),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
        Text(
            text = label,
            style = ApexTheme.typography.LabelMicro.copy(
                fontSize = 8.5.sp,
                color = ApexTheme.colors.slateMuted,
                letterSpacing = 0.8.sp
            ),
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun WorkoutHistoryItemCard(
    workout: WorkoutEntity,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    staggerIndex: Int = 0,
    modifier: Modifier = Modifier
) {
    val animProgress = remember(workout.id) { Animatable(0f) }
    LaunchedEffect(workout.id) {
        delay(staggerIndex.coerceAtMost(8) * 45L)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    val dateFormatted = remember(workout.startTimeEpochMs) {
        val sdf = SimpleDateFormat("EEE, MMM d • h:mm a", Locale.US)
        sdf.format(Date(workout.startTimeEpochMs))
    }

    val sportName = when (workout.activityType.uppercase()) {
        "CYCLING" -> "RIDE"
        "RUNNING" -> "RUN"
        "WALKING" -> "WALK"
        "HIKING" -> "HIKE"
        else -> workout.activityType.uppercase()
    }

    val avgPaceSec = if (workout.totalDistanceMeters > 0 && workout.totalMovingTimeMs > 0) {
        (workout.totalMovingTimeMs / 1000.0) / (workout.totalDistanceMeters / 1000.0)
    } else 0.0

    val cardShape = remember { RoundedCornerShape(ApexDimens.RadiusCardStandard) }
    val badgeShape = remember { RoundedCornerShape(4.dp) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                val p = animProgress.value.coerceIn(0f, 1f)
                alpha = p
                translationY = (1f - p) * 50f
                scaleX = 0.96f + 0.04f * p
                scaleY = 0.96f + 0.04f * p
            }
            .clip(cardShape)
            .background(ApexTheme.colors.surface)
            .border(1.dp, ApexTheme.colors.borderSubtle, cardShape)
            .tactilePress(pressedScale = 0.97f) { onClick() }
            .padding(14.dp)
    ) {
            Column {
                // Header Row: Sport Badge + Date + Delete Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(badgeShape)
                                .background(ApexTheme.colors.surfaceHigh)
                                .border(1.dp, ApexTheme.colors.borderSubtle, badgeShape)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = sportName,
                                style = ApexTheme.typography.LabelMicro.copy(
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Black,
                                    color = ApexTheme.colors.electricCyan,
                                    letterSpacing = 1.sp
                                )
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = dateFormatted,
                            style = ApexTheme.typography.LabelMicro.copy(
                                color = ApexTheme.colors.slateMuted,
                                fontSize = 10.sp
                            )
                        )
                    }

                    // Delete icon pill with tactile feedback
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .tactilePress(pressedScale = 0.85f) { onDeleteClick() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "✕",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ApexTheme.colors.slateMuted
                        )
                    }
                }

            Spacer(modifier = Modifier.height(10.dp))

            // 3-Column Core Metrics: Distance, Time, Avg Pace
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricItem(
                    label = "DISTANCE",
                    value = UiFormatters.formatDistanceKm(workout.totalDistanceMeters),
                    unit = "km",
                    modifier = Modifier.weight(1f)
                )
                MetricItem(
                    label = "TIME",
                    value = UiFormatters.formatDuration(workout.totalMovingTimeMs / 1000L),
                    unit = "",
                    modifier = Modifier.weight(1f)
                )
                MetricItem(
                    label = "AVG PACE",
                    value = UiFormatters.formatPace(avgPaceSec),
                    unit = "/km",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MetricItem(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = ApexTheme.typography.LabelMicro.copy(
                fontSize = 8.sp,
                color = ApexTheme.colors.slateMuted,
                letterSpacing = 0.8.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = ApexTheme.typography.MetricMedium.copy(
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = ApexTheme.colors.textPrimary,
                    letterSpacing = (-0.3).sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (unit.isNotEmpty()) {
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = unit,
                    style = ApexTheme.typography.LabelMicro.copy(
                        fontSize = 8.sp,
                        color = ApexTheme.colors.slateMuted
                    ),
                    modifier = Modifier.padding(bottom = 1.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun EmptyWorkoutsCard(modifier: Modifier = Modifier) {
    val giantCardShape = remember { RoundedCornerShape(ApexDimens.RadiusCardGiant) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(giantCardShape)
            .background(ApexTheme.colors.surface)
            .border(1.dp, ApexTheme.colors.borderSubtle, giantCardShape)
            .padding(vertical = 44.dp, horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(ApexTheme.colors.surfaceHigh),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "◆",
                    style = ApexTheme.typography.Headline.copy(
                        fontSize = 16.sp,
                        color = ApexTheme.colors.slateMuted
                    )
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "No activities recorded yet",
                style = ApexTheme.typography.Headline.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = ApexTheme.colors.textPrimary,
                    letterSpacing = 0.5.sp
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Record your first session using the Record tab. All telemetry is stored privately on your device.",
                style = ApexTheme.typography.BodyText.copy(
                    fontSize = 12.sp,
                    color = ApexTheme.colors.slateMuted,
                    textAlign = TextAlign.Center
                ),
                lineHeight = 17.sp
            )
        }
    }
}
