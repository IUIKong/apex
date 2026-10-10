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
import androidx.compose.ui.graphics.graphicsLayer
import com.apex.tracker.ui.components.ApexLogoMark
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
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    var selectedFilter by remember { mutableStateOf("ALL") }
    var workoutToDelete by remember { mutableStateOf<WorkoutEntity?>(null) }

    val filteredWorkouts = remember(workouts, selectedFilter) {
        if (selectedFilter == "ALL") {
            workouts
        } else {
            workouts.filter { it.activityType.equals(selectedFilter, ignoreCase = true) }
        }
    }

    // Weekly calculations
    val totalDistanceMeters = workouts.sumOf { it.totalDistanceMeters }
    val totalMovingTimeMs = workouts.sumOf { it.totalMovingTimeMs }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ApexTheme.colors.canvasBackground)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Screen Header: Editorial Tracksmith Logbook with Brand Emblem & Update Action
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ApexLogoMark(size = 32.dp)
                Column {
                    Text(
                        text = "LOGBOOK",
                        style = ApexTheme.typography.Headline.copy(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = ApexTheme.colors.textPrimary,
                            letterSpacing = 1.sp
                        )
                    )
                    Text(
                        text = "${workouts.size} SESSIONS RECORDED",
                        style = ApexTheme.typography.LabelMicro.copy(
                            color = ApexTheme.colors.slateMuted,
                            letterSpacing = 1.2.sp
                        )
                    )
                }
            }

            // In-app Update Pill
            if (onCheckForUpdates != null) {
                val isUpdateAvailable = updateStatus is UpdateStatus.Available
                val pillBorder = if (isUpdateAvailable) ApexTheme.colors.electricCyan else ApexTheme.colors.borderSubtle
                val pillBg = if (isUpdateAvailable) ApexTheme.colors.surfaceHigh else ApexTheme.colors.surface

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(ApexDimens.RadiusPillFull))
                        .background(pillBg)
                        .border(1.dp, pillBorder, RoundedCornerShape(ApexDimens.RadiusPillFull))
                        .tactilePress(pressedScale = 0.94f) {
                            ApexAudioFeedback.playClick(view)
                            onCheckForUpdates()
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isUpdateAvailable) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(ApexTheme.colors.electricCyan)
                            )
                        }
                        Text(
                            text = if (isUpdateAvailable) "UPDATE AVAILABLE" else "CHECK UPDATES",
                            style = ApexTheme.typography.LabelMicro.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isUpdateAvailable) ApexTheme.colors.electricCyan else ApexTheme.colors.slateSubtle,
                                letterSpacing = 0.8.sp
                            )
                        )
                    }
                }
            }
        }

        if (workouts.isEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            EmptyWorkoutsCard(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(16.dp))
        } else {
            Spacer(modifier = Modifier.height(16.dp))

            // Weekly Volume Summary Card
            WeeklySummaryCard(
                totalDistanceKm = totalDistanceMeters / 1000.0,
                totalDurationSec = totalMovingTimeMs / 1000L,
                sessionsCount = workouts.size
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Sport Filter Chips (ALL, RUNNING, CYCLING, WALKING, HIKING)
            SportFilterChips(
                selectedFilter = selectedFilter,
                onFilterSelected = { selectedFilter = it },
                allCount = workouts.size,
                runCount = workouts.count { it.activityType.equals("RUNNING", ignoreCase = true) },
                rideCount = workouts.count { it.activityType.equals("CYCLING", ignoreCase = true) },
                walkCount = workouts.count { it.activityType.equals("WALKING", ignoreCase = true) },
                hikeCount = workouts.count { it.activityType.equals("HIKING", ignoreCase = true) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Workouts List
            if (filteredWorkouts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No $selectedFilter activities recorded",
                        style = ApexTheme.typography.LabelMicro.copy(
                            color = ApexTheme.colors.slateMuted,
                            fontSize = 11.sp
                        )
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(filteredWorkouts, key = { _, workout -> workout.id }) { index, workout ->
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
            .padding(14.dp)
    ) {
        Column {
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
private fun SportFilterChips(
    selectedFilter: String,
    onFilterSelected: (String) -> Unit,
    allCount: Int,
    runCount: Int,
    rideCount: Int,
    walkCount: Int,
    hikeCount: Int,
    modifier: Modifier = Modifier
) {
    val filters = listOf(
        "ALL" to allCount,
        "RUNNING" to runCount,
        "CYCLING" to rideCount,
        "WALKING" to walkCount,
        "HIKING" to hikeCount
    )

    val pillShape = remember { RoundedCornerShape(ApexDimens.RadiusPillFull) }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(filters, key = { it.first }) { (filter, count) ->
            val isSelected = selectedFilter.equals(filter, ignoreCase = true)
            val displayName = when (filter) {
                "RUNNING" -> "RUN"
                "CYCLING" -> "RIDE"
                "WALKING" -> "WALK"
                "HIKING" -> "HIKE"
                else -> "ALL"
            }

            val bg = if (isSelected) ApexTheme.colors.textPrimary else ApexTheme.colors.surface
            val textCol = if (isSelected) ApexTheme.colors.canvasBackground else ApexTheme.colors.slateMuted
            val borderCol = if (isSelected) ApexTheme.colors.textPrimary else ApexTheme.colors.borderSubtle

            Box(
                modifier = Modifier
                    .clip(pillShape)
                    .background(bg)
                    .border(1.dp, borderCol, pillShape)
                    .tactilePress(pressedScale = 0.92f) { onFilterSelected(filter) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "$displayName ($count)",
                    style = ApexTheme.typography.LabelMicro.copy(
                        fontSize = 9.5.sp,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                        color = textCol,
                        letterSpacing = 0.8.sp
                    )
                )
            }
        }
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
