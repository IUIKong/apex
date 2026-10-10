package com.apex.tracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apex.tracker.ui.components.tactilePress
import com.apex.tracker.ui.history.ApexWorkoutsHistoryScreen
import com.apex.tracker.ui.live.ApexLiveHudScreen
import com.apex.tracker.ui.sound.ApexAudioFeedback
import com.apex.tracker.ui.splash.ApexSplashIntroScreen
import com.apex.tracker.ui.summary.ApexWorkoutSummaryScreen
import com.apex.tracker.ui.theme.ApexDimens
import com.apex.tracker.ui.theme.ApexTheme
import com.apex.tracker.ui.viewmodel.ApexTrackerViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ApexTrackerViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.refreshLocationStatus()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshLocationStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(R.color.canvas_background)
        requestRequiredPermissions()

        setContent {
            val liveState by viewModel.liveHudState.collectAsStateWithLifecycle()
            var showSplash by remember { mutableStateOf(true) }

            ApexTheme(isDark = liveState.isDarkTheme) {
                AnimatedContent(
                    targetState = showSplash,
                    transitionSpec = {
                        fadeIn(tween(480, easing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f))) togetherWith
                            fadeOut(tween(380, easing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)))
                    },
                    label = "splash_to_main_app_transition"
                ) { isSplash ->
                    if (isSplash) {
                        ApexSplashIntroScreen(
                            onFinish = { showSplash = false }
                        )
                    } else {
                        MainAppContent(
                            viewModel = viewModel,
                            onOpenLocationSettings = {
                                try {
                                    if (!viewModel.isLocationServicesEnabled.value) {
                                        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        startActivity(intent)
                                    } else {
                                        val uri = Uri.fromParts("package", packageName, null)
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        startActivity(intent)
                                    }
                                } catch (e: Exception) {
                                    Log.e("MainActivity", "Failed to open location settings", e)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }
}

@Composable
fun MainAppContent(
    viewModel: ApexTrackerViewModel,
    onOpenLocationSettings: () -> Unit = {}
) {
    // 2 Tabs: Left = 0 (Logbook), Right = 1 (RECORD)
    // Default to RECORD tab
    var selectedTab by remember { mutableIntStateOf(1) }
    var viewingSummary by remember { mutableStateOf(false) }

    // Intercept hardware Back button when reviewing a workout summary
    BackHandler(enabled = viewingSummary) {
        viewModel.resetLiveHud()
        viewingSummary = false
        selectedTab = 0
    }

    val liveState by viewModel.liveHudState.collectAsStateWithLifecycle()
    val summaryState by viewModel.summaryState.collectAsStateWithLifecycle()
    val allWorkouts by viewModel.allWorkouts.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = ApexTheme.colors.canvasBackground,
        contentWindowInsets = WindowInsets.statusBars,
        bottomBar = {
            if (!viewingSummary) {
                ApexBottomNavigationBar(
                    selectedTabIndex = selectedTab,
                    onTabSelected = { tab ->
                        if (tab == 1 && !liveState.isRecording) {
                            viewModel.resetLiveHud()
                        }
                        selectedTab = tab
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = viewingSummary,
                transitionSpec = {
                    val luxuryEase = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)
                    if (targetState) {
                        (slideInVertically(tween(380, easing = luxuryEase)) { it / 2 } + fadeIn(tween(280, easing = luxuryEase)))
                            .togetherWith(
                                slideOutVertically(tween(320, easing = luxuryEase)) { -it / 3 } + fadeOut(tween(220, easing = luxuryEase))
                            )
                    } else {
                        (slideInVertically(tween(380, easing = luxuryEase)) { -it / 3 } + fadeIn(tween(280, easing = luxuryEase)))
                            .togetherWith(
                                slideOutVertically(tween(320, easing = luxuryEase)) { it / 2 } + fadeOut(tween(220, easing = luxuryEase))
                            )
                    }
                },
                label = "summary_screen_transition"
            ) { isSummary ->
                if (isSummary) {
                    ApexWorkoutSummaryScreen(
                        summaryState = summaryState,
                        onDoneClick = {
                            viewModel.resetLiveHud()
                            viewingSummary = false
                            selectedTab = 0 // Switch to Logbook
                        },
                        onDeleteClick = {
                            viewModel.deleteWorkout(summaryState.activityId)
                            viewModel.resetLiveHud()
                            viewingSummary = false
                            selectedTab = 0 // Switch to Logbook
                        }
                    )
                } else {
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            val luxuryEase = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)
                            val forward = targetState > initialState
                            if (forward) {
                                (slideInHorizontally(tween(340, easing = luxuryEase)) { it / 3 } + fadeIn(tween(260, easing = luxuryEase)))
                                    .togetherWith(
                                        slideOutHorizontally(tween(300, easing = luxuryEase)) { -it / 3 } + fadeOut(tween(200, easing = luxuryEase))
                                    )
                            } else {
                                (slideInHorizontally(tween(340, easing = luxuryEase)) { -it / 3 } + fadeIn(tween(260, easing = luxuryEase)))
                                    .togetherWith(
                                        slideOutHorizontally(tween(300, easing = luxuryEase)) { it / 3 } + fadeOut(tween(200, easing = luxuryEase))
                                    )
                            }
                        },
                        label = "tab_content_transition"
                    ) { currentTab ->
                        when (currentTab) {
                            0 -> ApexWorkoutsHistoryScreen(
                                workouts = allWorkouts,
                                onWorkoutSelected = { workoutId ->
                                    viewModel.loadWorkoutDetails(workoutId)
                                    viewingSummary = true
                                },
                                onDeleteWorkout = { workoutId ->
                                    viewModel.deleteWorkout(workoutId)
                                }
                            )

                            1 -> ApexLiveHudScreen(
                                uiState = liveState,
                                onStartWorkout = { viewModel.startWorkout(liveState.activityType) },
                                onPauseWorkout = { viewModel.pauseWorkout() },
                                onResumeWorkout = { viewModel.resumeWorkout() },
                                onFinishWorkout = {
                                    val saved = viewModel.finishWorkout()
                                    if (saved) {
                                        viewingSummary = true // Open full workout summary only if workout saved
                                    }
                                },
                                onLockChanged = { locked -> viewModel.setControlsLocked(locked) },
                                onToggleTheme = { viewModel.toggleTheme() },
                                onActivityTypeChanged = { type -> viewModel.setActivityType(type) },
                                onBatteryProfileChanged = { profile -> viewModel.setBatteryProfile(profile) },
                                onToggleRawTrace = { viewModel.toggleRawTrace() },
                                onToggleAutoFollow = { viewModel.toggleAutoFollow() },
                                onResumeInterruptedSession = { viewModel.resumeInterruptedSession() },
                                onDiscardInterruptedSession = { viewModel.discardInterruptedSession() },
                                onOpenLocationSettings = onOpenLocationSettings,
                                onDismissLocationPrompt = { viewModel.dismissLocationPrompt() },
                                onStartWorkoutForce = { viewModel.startWorkout(liveState.activityType, forceStart = true) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Minimalist Swiss Vector Icons for Bottom Navigation Tabs.
 */
@Composable
private fun LogbookVectorIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

        // Left open page
        val pathLeft = Path().apply {
            moveTo(w * 0.5f, h * 0.22f)
            cubicTo(w * 0.36f, h * 0.16f, w * 0.22f, h * 0.16f, w * 0.12f, h * 0.20f)
            lineTo(w * 0.12f, h * 0.78f)
            cubicTo(w * 0.22f, h * 0.74f, w * 0.36f, h * 0.74f, w * 0.5f, h * 0.80f)
            close()
        }
        // Right open page
        val pathRight = Path().apply {
            moveTo(w * 0.5f, h * 0.22f)
            cubicTo(w * 0.64f, h * 0.16f, w * 0.78f, h * 0.16f, w * 0.88f, h * 0.20f)
            lineTo(w * 0.88f, h * 0.78f)
            cubicTo(w * 0.78f, h * 0.74f, w * 0.64f, h * 0.74f, w * 0.5f, h * 0.80f)
            close()
        }
        drawPath(pathLeft, color = color, style = stroke)
        drawPath(pathRight, color = color, style = stroke)
        drawLine(
            color = color,
            start = Offset(w * 0.5f, h * 0.22f),
            end = Offset(w * 0.5f, h * 0.80f),
            strokeWidth = 1.6.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun RecordVectorIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

        // Outer concentric target ring
        drawCircle(
            color = color,
            radius = w * 0.40f,
            center = Offset(w * 0.5f, h * 0.5f),
            style = stroke
        )
        // Center recording beacon dot
        drawCircle(
            color = color,
            radius = w * 0.18f,
            center = Offset(w * 0.5f, h * 0.5f)
        )
    }
}

/**
 * 2-Tab Consumer Bottom Navigation Bar:
 * - Standard Material navigation bar height with WindowInsets.navigationBars (bottom only)
 * - Generous 56dp+ touch targets across the entire height of the bar
 * - 50/50 proportional weight distribution preventing any squishing on all screen sizes
 * - Left: Logbook (Clean Swiss vector icon, label, active indicator dot)
 * - Right: Record (Clean Swiss record beacon icon, label, active indicator dot)
 */
@Composable
fun ApexBottomNavigationBar(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val colors = ApexTheme.colors
    val typography = ApexTheme.typography

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.surfaceElevated,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
        ) {
            HorizontalDivider(
                color = colors.borderSubtle,
                thickness = 1.dp
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tab 0: LOGBOOK (Past activities & history)
                val isLogbookSelected = selectedTabIndex == 0
                val logbookColor = if (isLogbookSelected) colors.textPrimary else colors.slateMuted

                val navPillShape = remember { RoundedCornerShape(ApexDimens.RadiusPillFull) }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(navPillShape)
                        .background(if (isLogbookSelected) colors.surfaceHigh else Color.Transparent)
                        .tactilePress(pressedScale = 0.95f) {
                            ApexAudioFeedback.playClick(view)
                            onTabSelected(0)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LogbookVectorIcon(color = logbookColor)
                        Text(
                            text = "LOGBOOK",
                            style = typography.LabelUppercase.copy(
                                fontSize = 11.sp,
                                fontWeight = if (isLogbookSelected) FontWeight.Black else FontWeight.Bold,
                                color = logbookColor,
                                letterSpacing = 1.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Tab 1: RECORD (Main live tracking cockpit)
                val isRecordSelected = selectedTabIndex == 1
                val recordColor = if (isRecordSelected) colors.textPrimary else colors.slateMuted

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(navPillShape)
                        .background(if (isRecordSelected) colors.surfaceHigh else Color.Transparent)
                        .tactilePress(pressedScale = 0.95f) {
                            ApexAudioFeedback.playClick(view)
                            onTabSelected(1)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RecordVectorIcon(color = if (isRecordSelected) colors.electricCyan else recordColor)
                        Text(
                            text = "RECORD",
                            style = typography.LabelUppercase.copy(
                                fontSize = 11.sp,
                                fontWeight = if (isRecordSelected) FontWeight.Black else FontWeight.Bold,
                                color = recordColor,
                                letterSpacing = 1.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
