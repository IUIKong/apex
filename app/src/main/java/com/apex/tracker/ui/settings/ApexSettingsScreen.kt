package com.apex.tracker.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apex.tracker.BuildConfig
import com.apex.tracker.ui.components.ApexLogoMark
import com.apex.tracker.ui.components.tactilePress
import com.apex.tracker.ui.sound.ApexAudioFeedback
import com.apex.tracker.ui.theme.ApexDimens
import com.apex.tracker.ui.theme.ApexTheme
import com.apex.tracker.update.UpdateStatus

/**
 * Editorial Athletic Settings Menu.
 *
 * Implements smooth custom sliders, toggle switches, and tactile feedback
 * matching Apex's dark editorial zero-elevation design system.
 */
@Composable
fun ApexSettingsScreen(
    onNavigateBack: () -> Unit,
    updateStatus: UpdateStatus = UpdateStatus.Idle,
    onCheckForUpdates: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val colors = ApexTheme.colors
    val typography = ApexTheme.typography
    val scrollState = rememberScrollState()

    // Preferences states
    var introFrequency by remember {
        mutableStateOf(AppSettings.getIntroFrequency(context))
    }
    var autoCheckUpdates by remember {
        mutableStateOf(AppSettings.isAutoCheckUpdatesEnabled(context))
    }
    var audioFeedback by remember {
        mutableStateOf(AppSettings.isAudioFeedbackEnabled(context))
    }
    var distanceUnit by remember {
        mutableStateOf(AppSettings.getDistanceUnit(context))
    }
    var showRawTrace by remember {
        mutableStateOf(AppSettings.isShowRawTraceEnabled(context))
    }

    val cardShape = remember { RoundedCornerShape(ApexDimens.RadiusCardStandard) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvasBackground)
            .padding(horizontal = ApexDimens.PaddingScreenHorizontal)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Screen Top Bar: Back Button, Title, Subtitle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceElevated)
                    .border(1.dp, colors.borderSubtle, CircleShape)
                    .tactilePress(pressedScale = 0.90f) {
                        ApexAudioFeedback.playClick(view)
                        onNavigateBack()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column {
                Text(
                    text = "SETTINGS",
                    style = typography.Headline.copy(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = colors.textPrimary,
                        letterSpacing = 1.5.sp
                    )
                )
                Text(
                    text = "PREFERENCES & TELEMETRY",
                    style = typography.LabelMicro.copy(
                        color = colors.slateMuted,
                        letterSpacing = 1.2.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Section: Intro Animation
            SettingsSectionHeader(title = "PRESENTATION & LAUNCH")

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(colors.surface)
                    .border(1.dp, colors.borderSubtle, cardShape)
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "OPENING ANIMATION FREQUENCY",
                        style = typography.LabelMicro.copy(
                            fontWeight = FontWeight.Black,
                            color = colors.electricCyan,
                            letterSpacing = 1.sp
                        )
                    )
                    Text(
                        text = "Control how often the kinetic bouncing dot & pixelated athletic gradient plays on startup.",
                        style = typography.BodyText.copy(
                            fontSize = 12.sp,
                            color = colors.slateSubtle,
                            lineHeight = 16.sp
                        )
                    )

                    val introOptions = remember {
                        listOf("EVERY LAUNCH", "EVERY 7 DAYS", "NEVER")
                    }
                    val currentIntroIndex = when (introFrequency) {
                        IntroFrequency.ALWAYS -> 0
                        IntroFrequency.WEEKLY -> 1
                        IntroFrequency.NEVER -> 2
                    }

                    ApexSmoothSegmentedSlider(
                        options = introOptions,
                        selectedIndex = currentIntroIndex,
                        onOptionSelected = { index ->
                            val newFreq = when (index) {
                                0 -> IntroFrequency.ALWAYS
                                1 -> IntroFrequency.WEEKLY
                                else -> IntroFrequency.NEVER
                            }
                            introFrequency = newFreq
                            AppSettings.setIntroFrequency(context, newFreq)
                        }
                    )
                }
            }

            // 2. Section: In-App Updates
            SettingsSectionHeader(title = "SYSTEM & APPLICATION UPDATES")

            // Auto-check updates toggle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(colors.surface)
                    .border(1.dp, colors.borderSubtle, cardShape)
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "AUTO CHECK FOR UPDATES",
                            style = typography.LabelMicro.copy(
                                fontWeight = FontWeight.Black,
                                color = colors.textPrimary,
                                letterSpacing = 1.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Silently check GitHub for new releases on startup and prompt for 1-tap in-app install.",
                            style = typography.BodyText.copy(
                                fontSize = 12.sp,
                                color = colors.slateSubtle,
                                lineHeight = 16.sp
                            )
                        )
                    }

                    ApexSmoothSwitch(
                        checked = autoCheckUpdates,
                        onCheckedChange = { checked ->
                            autoCheckUpdates = checked
                            AppSettings.setAutoCheckUpdates(context, checked)
                        }
                    )
                }
            }

            // Manual Check Card
            Box(
                modifier = Modifier
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
                        Column {
                            Text(
                                text = "CHECK FOR UPDATES NOW",
                                style = typography.LabelMicro.copy(
                                    fontWeight = FontWeight.Black,
                                    color = colors.textPrimary,
                                    letterSpacing = 1.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Installed version: v${BuildConfig.VERSION_NAME}",
                                style = typography.BodyText.copy(
                                    fontSize = 12.sp,
                                    color = colors.slateMuted
                                )
                            )
                        }

                        // Action trigger
                        val isChecking = updateStatus is UpdateStatus.Checking
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(ApexDimens.RadiusPillFull))
                                .background(colors.surfaceElevated)
                                .border(1.dp, colors.borderActive, RoundedCornerShape(ApexDimens.RadiusPillFull))
                                .tactilePress(pressedScale = 0.94f, enabled = !isChecking) {
                                    ApexAudioFeedback.playClick(view)
                                    onCheckForUpdates()
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isChecking) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = colors.electricCyan,
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        text = "CHECKING...",
                                        style = typography.LabelMicro.copy(
                                            color = colors.electricCyan,
                                            fontWeight = FontWeight.Black
                                        )
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Check",
                                        tint = colors.electricCyan,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "CHECK NOW",
                                        style = typography.LabelMicro.copy(
                                            color = colors.electricCyan,
                                            fontWeight = FontWeight.Black
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Status banner if checked
                    when (updateStatus) {
                        is UpdateStatus.Available -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.surfaceElevated)
                                    .border(1.dp, colors.electricCyan, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SystemUpdate,
                                        contentDescription = null,
                                        tint = colors.electricCyan,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Version ${updateStatus.updateInfo.versionName} available!",
                                        style = typography.LabelMicro.copy(
                                            color = colors.electricCyan,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }
                        is UpdateStatus.UpToDate -> {
                            Text(
                                text = "✓ App is up to date with GitHub releases.",
                                style = typography.LabelMicro.copy(
                                    color = colors.electricLime,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        is UpdateStatus.Error -> {
                            Text(
                                text = "Unable to check updates: ${updateStatus.message}",
                                style = typography.LabelMicro.copy(
                                    color = colors.laserAmber,
                                    fontSize = 10.sp
                                )
                            )
                        }
                        else -> Unit
                    }
                }
            }

            // 3. Section: Audio & Haptics
            SettingsSectionHeader(title = "AUDIO & TACTILE FEEDBACK")

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(colors.surface)
                    .border(1.dp, colors.borderSubtle, cardShape)
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "BUTTON CLICK SOUNDS",
                            style = typography.LabelMicro.copy(
                                fontWeight = FontWeight.Black,
                                color = colors.textPrimary,
                                letterSpacing = 1.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Play subtle, tasteful click audio feedback on tactical button taps.",
                            style = typography.BodyText.copy(
                                fontSize = 12.sp,
                                color = colors.slateSubtle,
                                lineHeight = 16.sp
                            )
                        )
                    }

                    ApexSmoothSwitch(
                        checked = audioFeedback,
                        onCheckedChange = { checked ->
                            audioFeedback = checked
                            AppSettings.setAudioFeedback(context, checked)
                        }
                    )
                }
            }

            // 4. Section: Units & Display
            SettingsSectionHeader(title = "MEASUREMENT & UNITS")

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(colors.surface)
                    .border(1.dp, colors.borderSubtle, cardShape)
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "DISTANCE & PACE UNITS",
                        style = typography.LabelMicro.copy(
                            fontWeight = FontWeight.Black,
                            color = colors.textPrimary,
                            letterSpacing = 1.sp
                        )
                    )
                    Text(
                        text = "Metric displays distance in kilometers and pace in min/km. Imperial uses miles and min/mi.",
                        style = typography.BodyText.copy(
                            fontSize = 12.sp,
                            color = colors.slateSubtle,
                            lineHeight = 16.sp
                        )
                    )

                    val unitOptions = remember { listOf("METRIC (KM)", "IMPERIAL (MI)") }
                    val currentUnitIndex = if (distanceUnit == DistanceUnit.METRIC) 0 else 1

                    ApexSmoothSegmentedSlider(
                        options = unitOptions,
                        selectedIndex = currentUnitIndex,
                        onOptionSelected = { index ->
                            val newUnit = if (index == 0) DistanceUnit.METRIC else DistanceUnit.IMPERIAL
                            distanceUnit = newUnit
                            AppSettings.setDistanceUnit(context, newUnit)
                        }
                    )
                }
            }

            // 5. Section: Map & GPS
            SettingsSectionHeader(title = "MAP & SENSOR TRACE")

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(colors.surface)
                    .border(1.dp, colors.borderSubtle, cardShape)
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "SHOW RAW GPS OVERLAY",
                            style = typography.LabelMicro.copy(
                                fontWeight = FontWeight.Black,
                                color = colors.textPrimary,
                                letterSpacing = 1.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Display unfiltered raw GNSS trace alongside the fused Kalman trajectory on the map.",
                            style = typography.BodyText.copy(
                                fontSize = 12.sp,
                                color = colors.slateSubtle,
                                lineHeight = 16.sp
                            )
                        )
                    }

                    ApexSmoothSwitch(
                        checked = showRawTrace,
                        onCheckedChange = { checked ->
                            showRawTrace = checked
                            AppSettings.setShowRawTrace(context, checked)
                        }
                    )
                }
            }

            // 6. Section: Architecture & System Info
            SettingsSectionHeader(title = "ABOUT APEX TRACKER")

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(colors.surface)
                    .border(1.dp, colors.borderSubtle, cardShape)
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ApexLogoMark(size = 28.dp)
                        Column {
                            Text(
                                text = "APEX ATHLETIC TRACKER",
                                style = typography.Headline.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    color = colors.textPrimary,
                                    letterSpacing = 1.sp
                                )
                            )
                            Text(
                                text = "VERSION ${BuildConfig.VERSION_NAME} (BUILD 4)",
                                style = typography.LabelMicro.copy(
                                    color = colors.electricCyan,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "• 15-State Extended Kalman Filter with Doppler Velocity Integration\n" +
                               "• Dynamic Dead Reckoning with Gyro Yaw & PDR Step Estimation\n" +
                               "• Stationary Agitation Suppression (0m False Distance Accumulation)\n" +
                               "• 100% Offline & Private Local Room Database Storage\n" +
                               "• Zero Elevation Strict Design System",
                        style = typography.BodyText.copy(
                            fontSize = 11.sp,
                            color = colors.slateSubtle,
                            lineHeight = 16.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * Section Header for Settings.
 */
@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = ApexTheme.typography.LabelMicro.copy(
            color = ApexTheme.colors.slateMuted,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.5.sp,
            fontSize = 10.sp
        ),
        modifier = Modifier.padding(start = 2.dp, top = 6.dp)
    )
}

/**
 * Custom Smooth Segmented Slider with animated indicator pill.
 */
@Composable
fun ApexSmoothSegmentedSlider(
    options: List<String>,
    selectedIndex: Int,
    onOptionSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ApexTheme.colors
    val view = LocalView.current
    val pillShape = remember { RoundedCornerShape(22.dp) }
    val thumbShape = remember { RoundedCornerShape(18.dp) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(pillShape)
            .background(colors.surfaceElevated)
            .border(1.dp, colors.borderSubtle, pillShape)
            .padding(3.dp)
    ) {
        val totalWidth = maxWidth
        val numSegments = options.size.coerceAtLeast(1)
        val segmentWidth = totalWidth / numSegments

        val animatedOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
            label = "segmented_slider_offset"
        )

        // Sliding Active Indicator Thumb
        Box(
            modifier = Modifier
                .width(segmentWidth)
                .height(36.dp)
                .offset(x = animatedOffset)
                .clip(thumbShape)
                .background(colors.surfaceHigh)
                .border(1.dp, colors.borderActive, thumbShape)
        )

        // Options Row
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = index == selectedIndex
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) colors.textPrimary else colors.slateMuted,
                    animationSpec = tween(durationMillis = 200),
                    label = "text_color"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (index != selectedIndex) {
                                ApexAudioFeedback.playClick(view)
                                onOptionSelected(index)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = option,
                        style = ApexTheme.typography.LabelMicro.copy(
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                            fontSize = 10.sp,
                            color = textColor,
                            letterSpacing = 0.8.sp
                        )
                    )
                }
            }
        }
    }
}

/**
 * Custom Smooth Toggle Switch matching the zero-elevation aesthetic.
 */
@Composable
fun ApexSmoothSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ApexTheme.colors
    val view = LocalView.current
    val trackShape = remember { RoundedCornerShape(16.dp) }

    val trackColor by animateColorAsState(
        targetValue = if (checked) colors.electricCyan else colors.surfaceElevated,
        animationSpec = tween(220),
        label = "switch_track_color"
    )

    val borderColor by animateColorAsState(
        targetValue = if (checked) colors.electricCyan else colors.borderSubtle,
        animationSpec = tween(220),
        label = "switch_border_color"
    )

    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 24.dp else 4.dp,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 450f),
        label = "switch_thumb_offset"
    )

    val thumbColor by animateColorAsState(
        targetValue = if (checked) colors.canvasBackground else colors.slateSubtle,
        animationSpec = tween(200),
        label = "switch_thumb_color"
    )

    Box(
        modifier = modifier
            .width(52.dp)
            .height(30.dp)
            .clip(trackShape)
            .background(trackColor)
            .border(1.dp, borderColor, trackShape)
            .tactilePress(pressedScale = 0.92f) {
                ApexAudioFeedback.playClick(view)
                onCheckedChange(!checked)
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(22.dp)
                .clip(CircleShape)
                .background(thumbColor),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = colors.electricCyan,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}
