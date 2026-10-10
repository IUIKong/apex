package com.apex.tracker.ui.live

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apex.tracker.ui.components.AnimatedNumeralTicker
import com.apex.tracker.ui.components.ApexLogoMark
import com.apex.tracker.ui.components.CrashRecoveryBanner
import com.apex.tracker.ui.components.SlideToLockGuard
import com.apex.tracker.ui.components.TacticalControlsBar
import com.apex.tracker.ui.components.tactilePress
import com.apex.tracker.ui.state.DeltaCategory
import com.apex.tracker.ui.state.LiveHudUiState
import com.apex.tracker.ui.state.UiFormatters
import androidx.compose.ui.platform.LocalView
import com.apex.tracker.ui.sound.ApexAudioFeedback
import com.apex.tracker.ui.theme.ApexDimens
import com.apex.tracker.ui.theme.ApexTheme
import java.util.Locale

/**
 * Editorial Workout Recording HUD Screen.
 *
 * Designed with generous whitespace, crisp visual hierarchy, and warm white-beige
 * aesthetic tokens. Centers around a prominent, responsive [ParchmentMapCanvas] showing
 * the user's trajectory, accompanied by a clean editorial telemetry dashboard.
 */
@Composable
fun ApexLiveHudScreen(
    uiState: LiveHudUiState,
    onStartWorkout: () -> Unit,
    onPauseWorkout: () -> Unit,
    onResumeWorkout: () -> Unit,
    onFinishWorkout: () -> Unit,
    onLockChanged: (Boolean) -> Unit,
    onToggleTheme: () -> Unit = {},
    onActivityTypeChanged: (String) -> Unit = {},
    onBatteryProfileChanged: (String) -> Unit = {},
    onToggleRawTrace: () -> Unit = {},
    onToggleAutoFollow: () -> Unit = {},
    onResumeInterruptedSession: () -> Unit = {},
    onDiscardInterruptedSession: () -> Unit = {},
    onOpenLocationSettings: () -> Unit = {},
    onDismissLocationPrompt: () -> Unit = {},
    onStartWorkoutForce: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val colors = ApexTheme.colors
    val typography = ApexTheme.typography

    var countdownSeconds by remember { mutableIntStateOf(0) }
    var showFinishConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(countdownSeconds) {
        if (countdownSeconds > 0) {
            delay(1000L)
            if (countdownSeconds == 1) {
                countdownSeconds = 0
                onStartWorkout()
            } else {
                countdownSeconds -= 1
            }
        }
    }

    val isLocationAlertActive = !uiState.isLocationServicesEnabled || !uiState.isLocationPermissionGranted

    // If Location Alert Dialog is prompted, show modal
    if (uiState.showLocationDisabledPrompt) {
        LocationDisabledDialog(
            isLocationServicesEnabled = uiState.isLocationServicesEnabled,
            onEnableGpsClick = onOpenLocationSettings,
            onRecordAnywayClick = onStartWorkoutForce,
            onDismissClick = onDismissLocationPrompt
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvasBackground)
    ) {
        val isCompact = maxHeight < 780.dp
        val columnModifier = if (isCompact) {
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp)
        } else {
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        }

        val mapShape = remember { RoundedCornerShape(ApexDimens.RadiusCanvasMap) }

        Column(
            modifier = columnModifier,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val mapModifier = if (isCompact) {
                Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .heightIn(min = 160.dp)
                    .clip(mapShape)
                    .border(1.dp, colors.borderSubtle, mapShape)
            } else {
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .heightIn(min = 160.dp)
                    .clip(mapShape)
                    .border(1.dp, colors.borderSubtle, mapShape)
            }

            // 1. Top Section: Crash Recovery, Location Alert & Brand Header
            if (uiState.hasInterruptedSession) {
                CrashRecoveryBanner(
                    onResumeClick = onResumeInterruptedSession,
                    onDiscardClick = onDiscardInterruptedSession
                )
            }

            if (isLocationAlertActive) {
                ProminentLocationAlertBanner(
                    isLocationServicesEnabled = uiState.isLocationServicesEnabled,
                    isLocationPermissionGranted = uiState.isLocationPermissionGranted,
                    onEnableGpsClick = onOpenLocationSettings
                )
            }

            AtelierBrandHeader(
                uiState = uiState,
                onOpenSettings = onOpenSettings
            )

            // 2. Map Canvas: Dedicated flexible container that expands to fill remaining space
            // Clean, responsive, uncluttered. Never overlaps or glitches into the metrics card below.
            Box(
                modifier = mapModifier
            ) {
                ParchmentMapCanvas(
                    trackPoints = uiState.trackPoints,
                    currentBearingDegrees = uiState.trackPoints.lastOrNull()?.bearing ?: 0f,
                    currentAccuracyMeters = uiState.horizontalAccuracyMeters,
                    showRawTrace = uiState.showRawTrace,
                    autoFollow = uiState.autoFollowMap,
                    onToggleRawTrace = onToggleRawTrace,
                    onToggleAutoFollow = onToggleAutoFollow,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 3. Primary Telemetry Editorial Dashboard (Pace, Distance, Time, Elev, Avg)
            EditorialMetricsDashboard(state = uiState)

            // 1.0 km Splits Ticker (shown when splits exist)
            if (uiState.splits.isNotEmpty()) {
                RealtimeSplitTicker(
                    splits = uiState.splits,
                    acceptedDistanceMeters = uiState.acceptedDistanceMeters
                )
            }

            // 4. Tactical Action Controls Bar (Start, Pause, Resume, Finish) - ALWAYS VISIBLE, NEVER CLIPPED
            TacticalControlsBar(
                isRecording = uiState.isRecording,
                isPaused = uiState.isPaused,
                isControlsLocked = uiState.isControlsLocked,
                onStartClick = {
                    if (isLocationAlertActive) {
                        onStartWorkout()
                    } else {
                        countdownSeconds = 3
                    }
                },
                onPauseClick = onPauseWorkout,
                onResumeClick = onResumeWorkout,
                onFinishClick = {
                    if (uiState.acceptedDistanceMeters > 50.0 || uiState.elapsedTimeSeconds > 15L) {
                        showFinishConfirmDialog = true
                    } else {
                        onFinishWorkout()
                    }
                }
            )

            if (showFinishConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showFinishConfirmDialog = false },
                    title = {
                        Text(
                            text = "Finish Workout?",
                            style = typography.Headline.copy(color = colors.textPrimary)
                        )
                    },
                    text = {
                        Text(
                            text = "Complete your activity and review your session performance?",
                            style = typography.BodyText.copy(color = colors.slateSubtle)
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                ApexAudioFeedback.playClick(view)
                                showFinishConfirmDialog = false
                                onFinishWorkout()
                            }
                        ) {
                            Text("FINISH", color = colors.electricCyan, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                ApexAudioFeedback.playClick(view)
                                showFinishConfirmDialog = false
                            }
                        ) {
                            Text("CANCEL", color = colors.slateMuted)
                        }
                    },
                    containerColor = colors.surfaceElevated
                )
            }

            // Slide-to-Lock Safety Guard (active session only)
            if (uiState.isRecording || uiState.isControlsLocked) {
                SlideToLockGuard(
                    isLocked = uiState.isControlsLocked,
                    onLockChanged = onLockChanged
                )
            }
        }

        // 5. Pre-Run 3-Second Animated Countdown Overlay (3... 2... 1... GO!)
        if (countdownSeconds > 0) {
            PreRunCountdownOverlay(
                secondsRemaining = countdownSeconds,
                onSkip = {
                    ApexAudioFeedback.playClick(view)
                    countdownSeconds = 0
                    onStartWorkout()
                },
                onCancel = {
                    ApexAudioFeedback.playClick(view)
                    countdownSeconds = 0
                }
            )
        }
    }
}

/**
 * Luxury Atelier Brand Header Bar with dynamic GNSS lock beacon and activity badge.
 */
@Composable
private fun AtelierBrandHeader(
    uiState: LiveHudUiState,
    onOpenSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = ApexTheme.colors
    val typography = ApexTheme.typography
    val pillShape = remember { RoundedCornerShape(ApexDimens.RadiusPillFull) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ApexLogoMark(size = 24.dp)
            Text(
                text = "APEX",
                style = typography.Headline.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = colors.textPrimary,
                    letterSpacing = 2.sp
                )
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // GNSS Status Pill
            val isGpsActive = uiState.isLocationServicesEnabled && uiState.isLocationPermissionGranted
            val hasGoodFix = isGpsActive && uiState.horizontalAccuracyMeters > 0f && uiState.horizontalAccuracyMeters < 35f
            val gpsDotColor = if (hasGoodFix) colors.electricLime else colors.laserAmber
            val gpsLabel = if (!isGpsActive) {
                "NO GPS"
            } else if (hasGoodFix) {
                val accInt = uiState.horizontalAccuracyMeters.toInt()
                if (accInt > 0) "GPS ±${accInt}m" else "GPS 3D"
            } else {
                "ACQUIRING"
            }

            Box(
                modifier = Modifier
                    .clip(pillShape)
                    .background(colors.surfaceElevated)
                    .border(1.dp, colors.borderSubtle, pillShape)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(gpsDotColor)
                    )
                    Text(
                        text = gpsLabel,
                        style = typography.TelemetryMicro.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            letterSpacing = 0.5.sp
                        )
                    )
                }
            }

            // Settings Gear Icon Button
            if (onOpenSettings != null) {
                val view = LocalView.current
                Box(
                    modifier = Modifier
                        .size(28.dp)
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
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}


/**
 * Editorial Telemetry Dashboard: generous proportions, crisp monospace numerals,
 * high-contrast Espresso ink on pure white/ivory surface with hairline borders.
 */
@Composable
private fun EditorialMetricsDashboard(
    state: LiveHudUiState,
    modifier: Modifier = Modifier
) {
    val colors = ApexTheme.colors
    val typography = ApexTheme.typography

    val currentPaceFormatted = UiFormatters.formatPace(state.currentPaceSecPerKm)
    val distanceKm = state.acceptedDistanceMeters / 1000.0
    val distanceFormatted = if (distanceKm < 10.0) {
        String.format(Locale.US, "%.2f", distanceKm)
    } else {
        String.format(Locale.US, "%.1f", distanceKm)
    }

    val movingTimeFormatted = UiFormatters.formatDuration(state.movingTimeSeconds)
    val avgPaceFormatted = if (state.averagePaceSecPerKm > 0 && !state.averagePaceSecPerKm.isNaN()) {
        UiFormatters.formatPace(state.averagePaceSecPerKm)
    } else "--:--"

    val paceDelta = UiFormatters.formatPaceDelta(state.paceDeltaSec)
    val deltaColor = when (paceDelta.category) {
        DeltaCategory.AHEAD -> colors.electricLime
        DeltaCategory.BEHIND -> colors.laserAmber
        DeltaCategory.EVEN -> colors.slateMuted
    }
    val deltaBg = when (paceDelta.category) {
        DeltaCategory.AHEAD -> colors.electricLimeDim
        DeltaCategory.BEHIND -> colors.laserAmberDim
        DeltaCategory.EVEN -> colors.surfaceHigh
    }

    val cardShape = remember { RoundedCornerShape(ApexDimens.RadiusCardGiant) }
    val pillShape = remember { RoundedCornerShape(ApexDimens.RadiusPillFull) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(colors.surfaceElevated)
            .border(1.dp, colors.borderSubtle, cardShape)
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Row 1: Primary Metrics (Glanceable Pace & Distance)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // PACE Column
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "CURRENT PACE",
                            style = typography.LabelMicro.copy(
                                color = colors.slateMuted,
                                fontSize = 9.sp,
                                letterSpacing = 0.8.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (state.currentPaceSecPerKm > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(pillShape)
                                    .background(deltaBg)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = paceDelta.formattedText,
                                    style = typography.TelemetryMicro.copy(
                                        color = deltaColor,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(verticalAlignment = Alignment.Bottom) {
                        AnimatedNumeralTicker(
                            text = currentPaceFormatted,
                            style = typography.MetricGiant.copy(
                                fontSize = 32.sp,
                                letterSpacing = (-1).sp,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Black
                            )
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "/km",
                            style = typography.LabelMicro.copy(
                                color = colors.slateMuted,
                                fontSize = 10.sp
                            ),
                            modifier = Modifier.padding(bottom = 3.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // DISTANCE Column
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "DISTANCE",
                        style = typography.LabelMicro.copy(
                            color = colors.slateMuted,
                            fontSize = 9.sp,
                            letterSpacing = 0.8.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(verticalAlignment = Alignment.Bottom) {
                        AnimatedNumeralTicker(
                            text = distanceFormatted,
                            style = typography.MetricGiant.copy(
                                fontSize = 32.sp,
                                letterSpacing = (-1).sp,
                                color = colors.electricCyan,
                                fontWeight = FontWeight.Black
                            )
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "km",
                            style = typography.LabelMicro.copy(
                                color = colors.slateMuted,
                                fontSize = 10.sp
                            ),
                            modifier = Modifier.padding(bottom = 3.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = colors.borderSubtle, thickness = 0.75.dp)
            Spacer(modifier = Modifier.height(8.dp))

            // Row 2: Secondary Telemetry (Balanced 3-Column: Moving Time, Avg Speed, Avg Pace)
            val avgSpeedKmh = if (state.movingTimeSeconds > 0) {
                (state.acceptedDistanceMeters / state.movingTimeSeconds) * 3.6
            } else 0.0
            val avgSpeedFormatted = String.format(Locale.US, "%.1f", avgSpeedKmh)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // TIME
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "MOVING TIME",
                        style = typography.LabelMicro.copy(
                            fontSize = 8.5.sp,
                            color = colors.slateMuted,
                            letterSpacing = 0.6.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    AnimatedNumeralTicker(
                        text = movingTimeFormatted,
                        style = typography.MetricMedium.copy(
                            fontSize = 15.sp,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                // AVG SPEED
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "AVG SPEED",
                        style = typography.LabelMicro.copy(
                            fontSize = 8.5.sp,
                            color = colors.slateMuted,
                            letterSpacing = 0.6.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        AnimatedNumeralTicker(
                            text = avgSpeedFormatted,
                            style = typography.MetricMedium.copy(
                                fontSize = 15.sp,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "km/h",
                            style = typography.LabelMicro.copy(
                                fontSize = 9.sp,
                                color = colors.slateMuted
                            ),
                            modifier = Modifier.padding(bottom = 1.5.dp),
                            maxLines = 1
                        )
                    }
                }

                // AVG PACE
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "AVG PACE",
                        style = typography.LabelMicro.copy(
                            fontSize = 8.5.sp,
                            color = colors.slateMuted,
                            letterSpacing = 0.6.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        AnimatedNumeralTicker(
                            text = avgPaceFormatted,
                            style = typography.MetricMedium.copy(
                                fontSize = 15.sp,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "/km",
                            style = typography.LabelMicro.copy(
                                fontSize = 9.sp,
                                color = colors.slateMuted
                            ),
                            modifier = Modifier.padding(bottom = 1.5.dp),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * High-visibility, full-width in-app banner notifying the user that location services or
 * permissions are disabled, with prominent text and a direct tactile button to open settings.
 */
@Composable
private fun ProminentLocationAlertBanner(
    isLocationServicesEnabled: Boolean,
    isLocationPermissionGranted: Boolean,
    onEnableGpsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ApexTheme.colors
    val typography = ApexTheme.typography

    val title = if (!isLocationServicesEnabled) "GPS TURNED OFF" else "LOCATION ACCESS NEEDED"
    val message = if (!isLocationServicesEnabled) {
        "Turn on location services in Android settings to track route & pace."
    } else {
        "Grant precise location permission to enable activity tracking."
    }
    val actionText = if (!isLocationServicesEnabled) "TURN ON GPS" else "SETTINGS"

    val cardShape = remember { RoundedCornerShape(ApexDimens.RadiusCardStandard) }
    val pillShape = remember { RoundedCornerShape(ApexDimens.RadiusPillFull) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(colors.laserAmberDim)
            .border(1.5.dp, colors.laserAmber, cardShape)
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    // Solid Amber Alert Dot
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(colors.laserAmber.copy(alpha = 0.35f))
                        )
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(colors.laserAmber)
                        )
                    }
                    Text(
                        text = title,
                        style = typography.LabelUppercase.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = colors.textPrimary,
                            letterSpacing = 1.sp
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(pillShape)
                        .background(colors.laserAmber)
                        .tactilePress(pressedScale = 0.92f) { onEnableGpsClick() }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = actionText,
                        style = typography.LabelUppercase.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = 0.8.sp
                        )
                    )
                }
            }

            Text(
                text = message,
                style = typography.BodyText.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.slateSubtle
                )
            )
        }
    }
}

/**
 * 3-Second Animated Pre-Run Countdown Overlay.
 * Displays prominent Swiss numerals (3... 2... 1... GO!) with scaling springs before starting.
 */
@Composable
private fun PreRunCountdownOverlay(
    secondsRemaining: Int,
    onSkip: () -> Unit,
    onCancel: () -> Unit
) {
    val colors = ApexTheme.colors
    val typography = ApexTheme.typography

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvasBackground.copy(alpha = 0.94f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "PREPARE TO RUN",
                style = typography.LabelUppercase.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = colors.punchyCrimson,
                    letterSpacing = 3.sp
                )
            )

            Spacer(modifier = Modifier.height(28.dp))

            AnimatedContent(
                targetState = secondsRemaining,
                transitionSpec = {
                    (scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow), initialScale = 0.55f) + fadeIn(tween(140)))
                        .togetherWith(scaleOut(tween(180, easing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)), targetScale = 1.35f) + fadeOut(tween(140)))
                },
                label = "countdown_ticker"
            ) { count ->
                val display = if (count > 0) count.toString() else "GO!"
                Text(
                    text = display,
                    style = typography.MetricGiant.copy(
                        fontSize = 110.sp,
                        fontWeight = FontWeight.Black,
                        color = colors.textPrimary,
                        letterSpacing = (-2).sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.tactilePress(pressedScale = 0.92f)
                ) {
                    Text(
                        text = "CANCEL",
                        style = typography.LabelUppercase.copy(
                            fontSize = 12.sp,
                            color = colors.slateMuted,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )
                }

                val pillShape = remember { RoundedCornerShape(ApexDimens.RadiusPillFull) }
                Box(
                    modifier = Modifier
                        .clip(pillShape)
                        .background(colors.surfaceElevated)
                        .border(1.dp, colors.borderSubtle, pillShape)
                        .tactilePress(pressedScale = 0.92f) { onSkip() }
                        .padding(horizontal = 18.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = "START NOW",
                        style = typography.LabelUppercase.copy(
                            fontSize = 11.sp,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    )
                }
            }
        }
    }
}

/**
 * Immediate modal dialog prompting the user when workout tracking is attempted while location
 * services or permissions are disabled, preventing recording a blank 0.00 km session unwittingly.
 */
@Composable
private fun LocationDisabledDialog(
    isLocationServicesEnabled: Boolean,
    onEnableGpsClick: () -> Unit,
    onRecordAnywayClick: () -> Unit,
    onDismissClick: () -> Unit
) {
    val colors = ApexTheme.colors
    val typography = ApexTheme.typography
    val dialogShape = remember { RoundedCornerShape(ApexDimens.RadiusCardStandard) }

    val title = if (!isLocationServicesEnabled) "Location Services Disabled" else "Location Permission Needed"
    val text = if (!isLocationServicesEnabled) {
        "GPS is turned off in your device settings. Apex requires location services to record your route, distance, and pace.\n\nEnable GPS to avoid recording a blank 0.00 km session."
    } else {
        "Fine location permission is required for Apex to track your live GPS route and pace."
    }

    AlertDialog(
        onDismissRequest = onDismissClick,
        title = {
            Text(
                text = title,
                style = typography.Headline.copy(fontSize = 18.sp, color = colors.textPrimary, fontWeight = FontWeight.Black)
            )
        },
        text = {
            Text(
                text = text,
                style = typography.BodyText.copy(color = colors.slateSubtle, fontSize = 13.sp)
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onEnableGpsClick()
                    onDismissClick()
                },
                modifier = Modifier.tactilePress(pressedScale = 0.92f)
            ) {
                Text(
                    text = if (!isLocationServicesEnabled) "ENABLE GPS" else "OPEN SETTINGS",
                    style = typography.LabelUppercase.copy(color = colors.electricCyan, fontWeight = FontWeight.Black)
                )
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = {
                        onRecordAnywayClick()
                        onDismissClick()
                    },
                    modifier = Modifier.tactilePress(pressedScale = 0.92f)
                ) {
                    Text(
                        text = "RECORD ANYWAY",
                        style = typography.LabelUppercase.copy(color = colors.slateMuted, fontSize = 10.sp)
                    )
                }
                TextButton(
                    onClick = onDismissClick,
                    modifier = Modifier.tactilePress(pressedScale = 0.92f)
                ) {
                    Text(
                        text = "CANCEL",
                        style = typography.LabelUppercase.copy(color = colors.slateMuted)
                    )
                }
            }
        },
        containerColor = colors.surface,
        shape = dialogShape
    )
}

