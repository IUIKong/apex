package com.apex.tracker.ui.stats

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apex.tracker.database.WorkoutEntity
import com.apex.tracker.ui.components.tactilePress
import com.apex.tracker.ui.sound.ApexAudioFeedback
import com.apex.tracker.ui.state.UiFormatters
import com.apex.tracker.ui.theme.ApexDimens
import com.apex.tracker.ui.theme.ApexTheme
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Editorial Monthly Performance Analytics & Visual Statistics Screen:
 * - Interactive month navigation (explore current and any previous months).
 * - High-impact aggregated monthly metrics (Distance, Time, Sessions, Elevation, Avg Pace).
 * - Interactive daily distance distribution bar chart with animated bars and scrub/tap selection.
 * - Mini calendar heatmap matrix for active training day detection.
 * - Granular selected-day breakdown with individual session inspect links.
 * - Fully animated transitions matching the pure OLED obsidian design language.
 */
@Composable
fun ApexMonthlyStatsScreen(
    workouts: List<WorkoutEntity>,
    onWorkoutSelected: (String) -> Unit,
    onOpenSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val colors = ApexTheme.colors
    val typography = ApexTheme.typography

    val currentRealMonth = remember { YearMonth.now() }
    var selectedYearMonth by remember { mutableStateOf(currentRealMonth) }
    var selectedDay by remember {
        mutableIntStateOf(
            if (selectedYearMonth == currentRealMonth) LocalDate.now().dayOfMonth else 1
        )
    }

    // Keep selectedDay within valid range when month changes
    LaunchedEffect(selectedYearMonth) {
        val maxDays = selectedYearMonth.lengthOfMonth()
        if (selectedDay > maxDays) {
            selectedDay = maxDays
        }
    }

    // Filter workouts belonging to the selected YearMonth
    val zoneId = remember { ZoneId.systemDefault() }
    val monthlyWorkouts = remember(workouts, selectedYearMonth) {
        workouts.filter { entity ->
            val date = Instant.ofEpochMilli(entity.startTimeEpochMs).atZone(zoneId).toLocalDate()
            date.year == selectedYearMonth.year && date.monthValue == selectedYearMonth.monthValue
        }
    }

    // Precompute daily distances and workout maps
    val daysInMonth = selectedYearMonth.lengthOfMonth()
    val dailyWorkoutsMap = remember(monthlyWorkouts, daysInMonth) {
        val map = mutableMapOf<Int, MutableList<WorkoutEntity>>()
        for (day in 1..daysInMonth) {
            map[day] = mutableListOf()
        }
        for (w in monthlyWorkouts) {
            val d = Instant.ofEpochMilli(w.startTimeEpochMs).atZone(zoneId).toLocalDate().dayOfMonth
            map[d]?.add(w)
        }
        map
    }

    val dailyDistancesKm = remember(dailyWorkoutsMap, daysInMonth) {
        (1..daysInMonth).map { day ->
            val wList = dailyWorkoutsMap[day] ?: emptyList()
            wList.sumOf { it.totalDistanceMeters } / 1000.0
        }
    }

    val totalMonthlyDistanceKm = remember(monthlyWorkouts) {
        monthlyWorkouts.sumOf { it.totalDistanceMeters } / 1000.0
    }
    val totalMonthlyMovingSeconds = remember(monthlyWorkouts) {
        monthlyWorkouts.sumOf { it.totalMovingTimeMs } / 1000L
    }
    val totalMonthlyElevationGainMeters = remember(monthlyWorkouts) {
        monthlyWorkouts.sumOf { it.totalElevationGainMeters }
    }
    val monthlySessionsCount = monthlyWorkouts.size
    val monthlyAvgPaceSecPerKm = remember(totalMonthlyDistanceKm, totalMonthlyMovingSeconds) {
        if (totalMonthlyDistanceKm > 0.05 && totalMonthlyMovingSeconds > 0L) {
            totalMonthlyMovingSeconds.toDouble() / totalMonthlyDistanceKm
        } else {
            Double.NaN
        }
    }

    val pillShape = remember { RoundedCornerShape(ApexDimens.RadiusPillFull) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvasBackground)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(14.dp))

        // Top Control Bar: Month Switcher & Minimal Settings Gear
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Month Navigation Pill Group
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Previous Month Button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceElevated)
                        .border(1.dp, colors.borderSubtle, CircleShape)
                        .tactilePress(pressedScale = 0.88f) {
                            ApexAudioFeedback.playClick(view)
                            selectedYearMonth = selectedYearMonth.minusMonths(1)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous Month",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Month & Year Display Pill
                val monthTitle = remember(selectedYearMonth) {
                    val formatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)
                    selectedYearMonth.format(formatter).uppercase(Locale.US)
                }
                Box(
                    modifier = Modifier
                        .clip(pillShape)
                        .background(colors.surfaceElevated)
                        .border(1.dp, colors.borderSubtle, pillShape)
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = monthTitle,
                        style = typography.Headline.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = colors.textPrimary,
                            letterSpacing = 1.2.sp
                        )
                    )
                }

                // Next Month Button (disable if past current month)
                val canGoNext = selectedYearMonth.isBefore(currentRealMonth)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (canGoNext) colors.surfaceElevated else colors.surface)
                        .border(1.dp, if (canGoNext) colors.borderSubtle else colors.borderSubtle.copy(alpha = 0.4f), CircleShape)
                        .tactilePress(pressedScale = 0.88f, enabled = canGoNext) {
                            if (canGoNext) {
                                ApexAudioFeedback.playClick(view)
                                selectedYearMonth = selectedYearMonth.plusMonths(1)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next Month",
                        tint = if (canGoNext) colors.textPrimary else colors.slateMuted.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Quick Jump to Current Month if viewing past
                if (selectedYearMonth != currentRealMonth) {
                    Box(
                        modifier = Modifier
                            .clip(pillShape)
                            .background(colors.surfaceHigh)
                            .border(1.dp, colors.electricCyan.copy(alpha = 0.6f), pillShape)
                            .tactilePress(pressedScale = 0.90f) {
                                ApexAudioFeedback.playClick(view)
                                selectedYearMonth = currentRealMonth
                                selectedDay = LocalDate.now().dayOfMonth
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "NOW",
                            style = typography.LabelMicro.copy(
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Black,
                                color = colors.electricCyan,
                                letterSpacing = 1.sp
                            )
                        )
                    }
                }
            }

            // Top-Right Minimal Settings Action
            if (onOpenSettings != null) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceElevated)
                        .border(1.dp, colors.borderSubtle, CircleShape)
                        .tactilePress(pressedScale = 0.90f) {
                            ApexAudioFeedback.playClick(view)
                            onOpenSettings()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = colors.slateSubtle,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Main Animated Monthly Content Area
        AnimatedContent(
            targetState = selectedYearMonth,
            transitionSpec = {
                val luxuryEase = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)
                val forward = targetState.isAfter(initialState)
                if (forward) {
                    (slideInHorizontally(tween(320, easing = luxuryEase)) { it / 2 } + fadeIn(tween(240)))
                        .togetherWith(slideOutHorizontally(tween(280, easing = luxuryEase)) { -it / 2 } + fadeOut(tween(180)))
                } else {
                    (slideInHorizontally(tween(320, easing = luxuryEase)) { -it / 2 } + fadeIn(tween(240)))
                        .togetherWith(slideOutHorizontally(tween(280, easing = luxuryEase)) { it / 2 } + fadeOut(tween(180)))
                }
            },
            label = "month_content_slide",
            modifier = Modifier.weight(1f)
        ) { yearMonth ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // 1. Monthly High-Impact Aggregated Metrics Bento
                item(key = "monthly_metrics_bento_${yearMonth}") {
                    MonthlyOverviewBento(
                        totalDistanceKm = totalMonthlyDistanceKm,
                        totalMovingSeconds = totalMonthlyMovingSeconds,
                        sessionsCount = monthlySessionsCount,
                        elevationGainMeters = totalMonthlyElevationGainMeters,
                        avgPaceSecPerKm = monthlyAvgPaceSecPerKm
                    )
                }

                // 2. Interactive Daily Distance Bar Chart Card
                item(key = "daily_distance_chart_${yearMonth}") {
                    DailyDistanceBarChartCard(
                        yearMonth = yearMonth,
                        dailyDistancesKm = dailyDistancesKm,
                        selectedDay = selectedDay,
                        onDaySelected = { day ->
                            ApexAudioFeedback.playClick(view)
                            selectedDay = day
                        }
                    )
                }

                // 3. Mini Month Calendar Heatmap Strip
                item(key = "calendar_matrix_${yearMonth}") {
                    MonthCalendarMatrixCard(
                        yearMonth = yearMonth,
                        dailyDistancesKm = dailyDistancesKm,
                        selectedDay = selectedDay,
                        onDaySelected = { day ->
                            ApexAudioFeedback.playClick(view)
                            selectedDay = day
                        }
                    )
                }

                // 4. Granular Selected Day Performance & Workouts Detail
                item(key = "day_detail_${yearMonth}_$selectedDay") {
                    val workoutsOnDay = dailyWorkoutsMap[selectedDay] ?: emptyList()
                    SelectedDayDetailCard(
                        yearMonth = yearMonth,
                        day = selectedDay,
                        workouts = workoutsOnDay,
                        onWorkoutSelected = onWorkoutSelected
                    )
                }
            }
        }
    }
}

/**
 * Top Bento Grid displaying high-glanceability aggregated statistics for the selected month.
 */
@Composable
private fun MonthlyOverviewBento(
    totalDistanceKm: Double,
    totalMovingSeconds: Long,
    sessionsCount: Int,
    elevationGainMeters: Double,
    avgPaceSecPerKm: Double,
    modifier: Modifier = Modifier
) {
    val colors = ApexTheme.colors
    val typography = ApexTheme.typography
    val cardShape = remember { RoundedCornerShape(ApexDimens.RadiusCardStandard) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Hero Card: Total Monthly Volume
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(colors.surface)
                .border(1.dp, colors.borderSubtle, cardShape)
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MONTHLY TOTAL DISTANCE",
                        style = typography.LabelMicro.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.slateSubtle,
                            letterSpacing = 1.sp
                        )
                    )
                    Text(
                        text = "$sessionsCount SESSIONS",
                        style = typography.LabelMicro.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.electricCyan,
                            letterSpacing = 0.8.sp
                        )
                    )
                }

                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = String.format(Locale.US, "%.1f", totalDistanceKm),
                        style = typography.MetricGiant.copy(
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Black,
                            color = colors.textPrimary,
                            letterSpacing = (-1).sp
                        )
                    )
                    Text(
                        text = "KM",
                        style = typography.LabelUppercase.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = colors.electricCyan,
                            letterSpacing = 1.sp
                        ),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }
        }

        // Secondary Telemetry Row: Time, Pace, Elevation Gain
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Moving Time Card
            MiniTelemetryCard(
                label = "TIME",
                value = UiFormatters.formatDuration(totalMovingSeconds),
                unit = "",
                accentColor = colors.electricLime,
                modifier = Modifier.weight(1f)
            )

            // Average Pace Card
            MiniTelemetryCard(
                label = "AVG PACE",
                value = UiFormatters.formatPace(avgPaceSecPerKm),
                unit = "/KM",
                accentColor = colors.electricCyan,
                modifier = Modifier.weight(1f)
            )

            // Elevation Gain Card
            MiniTelemetryCard(
                label = "GAIN",
                value = "+${elevationGainMeters.toInt()}",
                unit = "M",
                accentColor = colors.electricLime,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MiniTelemetryCard(
    label: String,
    value: String,
    unit: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val colors = ApexTheme.colors
    val typography = ApexTheme.typography
    val cardShape = remember { RoundedCornerShape(ApexDimens.RadiusCardStandard) }

    Box(
        modifier = modifier
            .clip(cardShape)
            .background(colors.surface)
            .border(1.dp, colors.borderSubtle, cardShape)
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = label,
                style = typography.LabelMicro.copy(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.slateMuted,
                    letterSpacing = 0.8.sp
                )
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = value,
                    style = typography.MetricLarge.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        color = colors.textPrimary
                    )
                )
                if (unit.isNotBlank()) {
                    Text(
                        text = unit,
                        style = typography.LabelMicro.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        ),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * Interactive Hardware-Accelerated Daily Distance Bar Chart Card.
 * Allows tapping on any individual bar to highlight that day's statistics.
 */
@Composable
private fun DailyDistanceBarChartCard(
    yearMonth: YearMonth,
    dailyDistancesKm: List<Double>,
    selectedDay: Int,
    onDaySelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ApexTheme.colors
    val typography = ApexTheme.typography
    val cardShape = remember { RoundedCornerShape(ApexDimens.RadiusCardStandard) }

    val daysInMonth = yearMonth.lengthOfMonth()
    val maxDistanceKm = remember(dailyDistancesKm) {
        val peak = dailyDistancesKm.maxOrNull() ?: 1.0
        maxOf(peak, 5.0)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(colors.surface)
            .border(1.dp, colors.borderSubtle, cardShape)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DAILY DISTANCE DISTRIBUTION",
                    style = typography.LabelMicro.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.slateSubtle,
                        letterSpacing = 1.sp
                    )
                )
                Text(
                    text = "MAX: ${String.format(Locale.US, "%.1f", maxDistanceKm)} KM",
                    style = typography.LabelMicro.copy(
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.slateMuted,
                        letterSpacing = 0.5.sp
                    )
                )
            }

            // Visual Interactive Bar Canvas (height 140dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                // Background Horizontal Gridlines (100%, 50%, 0%)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    val gridPaintColor = Color(0x1F1B2433)
                    drawLine(gridPaintColor, Offset(0f, 0f), Offset(w, 0f), strokeWidth = 1f)
                    drawLine(gridPaintColor, Offset(0f, h * 0.5f), Offset(w, h * 0.5f), strokeWidth = 1f)
                    drawLine(Color(0xFF1B2433), Offset(0f, h - 1f), Offset(w, h - 1f), strokeWidth = 1.5f)
                }

                // Interactive Bars
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    for (day in 1..daysInMonth) {
                        val distance = dailyDistancesKm.getOrElse(day - 1) { 0.0 }
                        val isSelected = (day == selectedDay)
                        val fraction = (distance / maxDistanceKm).coerceIn(0.0, 1.0).toFloat()

                        val animatedFraction by animateFloatAsState(
                            targetValue = fraction,
                            animationSpec = tween(durationMillis = 400, easing = CubicBezierEasing(0.2f, 1.0f, 0.3f, 1.0f)),
                            label = "bar_height_$day"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .pointerInput(day) {
                                    detectTapGestures {
                                        onDaySelected(day)
                                    }
                                },
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            if (distance > 0.05) {
                                // Active Activity Bar
                                val barColor = if (isSelected) colors.electricLime else colors.electricCyan
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.78f)
                                        .fillMaxHeight(maxOf(animatedFraction, 0.05f))
                                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(
                                                    barColor,
                                                    barColor.copy(alpha = 0.55f)
                                                )
                                            )
                                        )
                                        .then(
                                            if (isSelected) {
                                                Modifier.border(
                                                    1.5.dp,
                                                    Color.White,
                                                    RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                                                )
                                            } else Modifier
                                        )
                                )
                            } else {
                                // Rest Day Minimal Baseline Marker
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.5f)
                                        .height(if (isSelected) 8.dp else 3.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(if (isSelected) colors.slateSubtle else Color(0xFF161D28))
                                )
                            }
                        }
                    }
                }
            }

            // X-Axis Milestone Day Markers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val step = if (daysInMonth >= 30) 5 else 4
                for (marker in 1..daysInMonth step step) {
                    Text(
                        text = "$marker",
                        style = typography.TelemetryMicro.copy(
                            fontSize = 8.5.sp,
                            color = colors.slateMuted,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                Text(
                    text = "$daysInMonth",
                    style = typography.TelemetryMicro.copy(
                        fontSize = 8.5.sp,
                        color = colors.slateMuted,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

/**
 * Calendar Bento Matrix allowing date-by-date scrubbing across the month.
 */
@Composable
private fun MonthCalendarMatrixCard(
    yearMonth: YearMonth,
    dailyDistancesKm: List<Double>,
    selectedDay: Int,
    onDaySelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ApexTheme.colors
    val typography = ApexTheme.typography
    val cardShape = remember { RoundedCornerShape(ApexDimens.RadiusCardStandard) }

    val daysInMonth = yearMonth.lengthOfMonth()
    val firstDayOfWeek = yearMonth.atDay(1).dayOfWeek.value // 1 = Monday, 7 = Sunday
    val leadingEmptySlots = firstDayOfWeek - 1

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(colors.surface)
            .border(1.dp, colors.borderSubtle, cardShape)
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MONTH TRAINING CALENDAR",
                    style = typography.LabelMicro.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.slateSubtle,
                        letterSpacing = 1.sp
                    )
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(colors.electricCyan))
                    Text(
                        text = "WORKOUT LOGGED",
                        style = typography.LabelMicro.copy(
                            fontSize = 8.5.sp,
                            color = colors.slateMuted,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            // Weekday Initials Row
            val weekdayInitials = listOf("M", "T", "W", "T", "F", "S", "S")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                weekdayInitials.forEach { initial ->
                    Text(
                        text = initial,
                        style = typography.LabelMicro.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = colors.slateMuted,
                            textAlign = TextAlign.Center
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Calendar Tiles Grid (7 columns per row)
            val totalSlots = leadingEmptySlots + daysInMonth
            val totalRows = (totalSlots + 6) / 7

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (rowIndex in 0 until totalRows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        for (colIndex in 0 until 7) {
                            val slotIndex = rowIndex * 7 + colIndex
                            val dayNumber = slotIndex - leadingEmptySlots + 1

                            if (dayNumber in 1..daysInMonth) {
                                val distance = dailyDistancesKm.getOrElse(dayNumber - 1) { 0.0 }
                                val hasWorkout = distance > 0.05
                                val isSelected = (dayNumber == selectedDay)

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                        .padding(2.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            when {
                                                isSelected -> colors.surfaceHigh
                                                hasWorkout -> colors.surfaceElevated
                                                else -> Color.Transparent
                                            }
                                        )
                                        .then(
                                            when {
                                                isSelected -> Modifier.border(1.5.dp, colors.electricLime, RoundedCornerShape(8.dp))
                                                hasWorkout -> Modifier.border(1.dp, colors.electricCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                                else -> Modifier
                                            }
                                        )
                                        .clickable { onDaySelected(dayNumber) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "$dayNumber",
                                            style = typography.LabelMicro.copy(
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected || hasWorkout) FontWeight.Black else FontWeight.Normal,
                                                color = when {
                                                    isSelected -> colors.electricLime
                                                    hasWorkout -> colors.textPrimary
                                                    else -> colors.slateMuted.copy(alpha = 0.7f)
                                                }
                                            )
                                        )
                                        if (hasWorkout) {
                                            Box(
                                                modifier = Modifier
                                                    .size(3.5.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isSelected) colors.electricLime else colors.electricCyan)
                                            )
                                        }
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Detailed breakdown for the selected day:
 * Displays total distance, duration, pace, elevation, and individual workouts recorded on that day.
 */
@Composable
private fun SelectedDayDetailCard(
    yearMonth: YearMonth,
    day: Int,
    workouts: List<WorkoutEntity>,
    onWorkoutSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ApexTheme.colors
    val typography = ApexTheme.typography
    val cardShape = remember { RoundedCornerShape(ApexDimens.RadiusCardStandard) }
    val pillShape = remember { RoundedCornerShape(ApexDimens.RadiusPillFull) }

    val date = remember(yearMonth, day) {
        yearMonth.atDay(day)
    }
    val formattedDateHeader = remember(date) {
        val formatter = DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy", Locale.US)
        date.format(formatter).uppercase(Locale.US)
    }

    val dayTotalDistanceKm = remember(workouts) {
        workouts.sumOf { it.totalDistanceMeters } / 1000.0
    }
    val dayTotalMovingSeconds = remember(workouts) {
        workouts.sumOf { it.totalMovingTimeMs } / 1000L
    }
    val dayAvgPace = remember(dayTotalDistanceKm, dayTotalMovingSeconds) {
        if (dayTotalDistanceKm > 0.05 && dayTotalMovingSeconds > 0L) {
            dayTotalMovingSeconds.toDouble() / dayTotalDistanceKm
        } else {
            Double.NaN
        }
    }
    val dayElevationGain = remember(workouts) {
        workouts.sumOf { it.totalElevationGainMeters }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(colors.surface)
            .border(1.dp, colors.borderSubtle, cardShape)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Header Row: Selected Date Callout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (workouts.isNotEmpty()) colors.electricLime else colors.slateMuted)
                    )
                    Text(
                        text = formattedDateHeader,
                        style = typography.Headline.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = colors.textPrimary,
                            letterSpacing = 0.8.sp
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(pillShape)
                        .background(colors.surfaceElevated)
                        .border(1.dp, colors.borderSubtle, pillShape)
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${workouts.size} SESSIONS",
                        style = typography.LabelMicro.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (workouts.isNotEmpty()) colors.electricCyan else colors.slateMuted
                        )
                    )
                }
            }

            if (workouts.isNotEmpty()) {
                // Day Aggregate Telemetry Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceElevated)
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "DISTANCE", style = typography.LabelMicro.copy(fontSize = 9.sp, color = colors.slateMuted))
                        Text(
                            text = "${String.format(Locale.US, "%.2f", dayTotalDistanceKm)} km",
                            style = typography.Headline.copy(fontSize = 16.sp, fontWeight = FontWeight.Black, color = colors.electricCyan)
                        )
                    }
                    Column {
                        Text(text = "DURATION", style = typography.LabelMicro.copy(fontSize = 9.sp, color = colors.slateMuted))
                        Text(
                            text = UiFormatters.formatDuration(dayTotalMovingSeconds),
                            style = typography.Headline.copy(fontSize = 16.sp, fontWeight = FontWeight.Black, color = colors.textPrimary)
                        )
                    }
                    Column {
                        Text(text = "PACE", style = typography.LabelMicro.copy(fontSize = 9.sp, color = colors.slateMuted))
                        Text(
                            text = UiFormatters.formatPace(dayAvgPace),
                            style = typography.Headline.copy(fontSize = 16.sp, fontWeight = FontWeight.Black, color = colors.electricLime)
                        )
                    }
                    Column {
                        Text(text = "GAIN", style = typography.LabelMicro.copy(fontSize = 9.sp, color = colors.slateMuted))
                        Text(
                            text = "+${dayElevationGain.toInt()}m",
                            style = typography.Headline.copy(fontSize = 16.sp, fontWeight = FontWeight.Black, color = colors.textPrimary)
                        )
                    }
                }

                // Individual Workouts on this Date
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    workouts.forEach { workout ->
                        WorkoutOnDateRow(
                            workout = workout,
                            onWorkoutSelected = onWorkoutSelected
                        )
                    }
                }
            } else {
                // Empty Rest Day Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceElevated.copy(alpha = 0.5f))
                        .border(1.dp, colors.borderSubtle.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "REST DAY",
                            style = typography.Headline.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = colors.slateMuted,
                                letterSpacing = 1.2.sp
                            )
                        )
                        Text(
                            text = "No athletic sessions recorded on this date.",
                            style = typography.BodyText.copy(
                                fontSize = 11.sp,
                                color = colors.slateMuted.copy(alpha = 0.7f)
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual session row inside the selected day detail card.
 */
@Composable
private fun WorkoutOnDateRow(
    workout: WorkoutEntity,
    onWorkoutSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val colors = ApexTheme.colors
    val typography = ApexTheme.typography
    val rowShape = remember { RoundedCornerShape(10.dp) }

    val startTimeStr = remember(workout.startTimeEpochMs) {
        val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
        Instant.ofEpochMilli(workout.startTimeEpochMs).atZone(ZoneId.systemDefault()).format(timeFormatter)
    }

    val distKm = workout.totalDistanceMeters / 1000.0
    val durationSec = workout.totalMovingTimeMs / 1000L
    val avgPace = if (workout.totalDistanceMeters > 50.0 && workout.totalMovingTimeMs > 0L) {
        (workout.totalMovingTimeMs / 1000.0) / (workout.totalDistanceMeters / 1000.0)
    } else {
        Double.NaN
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(colors.surfaceElevated)
            .border(1.dp, colors.borderSubtle, rowShape)
            .tactilePress(pressedScale = 0.96f) {
                ApexAudioFeedback.playClick(view)
                onWorkoutSelected(workout.id)
            }
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Activity Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.surfaceHigh)
                    .border(1.dp, colors.electricCyan.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = workout.activityType.uppercase(),
                    style = typography.LabelMicro.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = colors.electricCyan
                    )
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = "${String.format(Locale.US, "%.2f", distKm)} km",
                    style = typography.Headline.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = colors.textPrimary
                    )
                )
                Text(
                    text = "$startTimeStr • ${UiFormatters.formatDuration(durationSec)}",
                    style = typography.BodyText.copy(
                        fontSize = 10.sp,
                        color = colors.slateSubtle
                    )
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "${UiFormatters.formatPace(avgPace)}/km",
                style = typography.TelemetryMicro.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Review Workout",
                tint = colors.slateMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
