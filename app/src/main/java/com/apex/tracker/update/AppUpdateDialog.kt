package com.apex.tracker.update

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.apex.tracker.ui.components.ApexLogoMark
import com.apex.tracker.ui.components.tactilePress
import com.apex.tracker.ui.sound.ApexAudioFeedback
import com.apex.tracker.ui.theme.ApexDimens
import com.apex.tracker.ui.theme.ApexTheme
import java.io.File
import java.util.Locale

@Composable
fun AppUpdateDialog(
    status: UpdateStatus,
    currentVersion: String,
    isManualCheck: Boolean = false,
    onStartUpdateClick: (UpdateInfo) -> Unit,
    onInstallClick: (File) -> Unit,
    onDismissRequest: () -> Unit
) {
    if (status is UpdateStatus.Idle) return

    // Under auto-check, ONLY show pop-up dialog if an update is available (or actively downloading / installing).
    // Never show "Checking...", "UpToDate", or "Error" pop-up dialogs during automated background checks.
    if (!isManualCheck) {
        if (status !is UpdateStatus.Available &&
            status !is UpdateStatus.Downloading &&
            status !is UpdateStatus.ReadyToInstall
        ) {
            return
        }
    }

    val context = LocalContext.current
    val colors = ApexTheme.colors
    val typography = ApexTheme.typography

    val dialogShape = remember { RoundedCornerShape(ApexDimens.RadiusCardGiant) }
    val cardShape = remember { RoundedCornerShape(ApexDimens.RadiusCardStandard) }
    val pillShape = remember { RoundedCornerShape(ApexDimens.RadiusPillFull) }

    Dialog(
        onDismissRequest = {
            if (status !is UpdateStatus.Downloading) {
                onDismissRequest()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = status !is UpdateStatus.Downloading,
            dismissOnClickOutside = status !is UpdateStatus.Downloading,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (status !is UpdateStatus.Downloading) onDismissRequest()
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* absorb taps inside card */ }
                    .clip(dialogShape)
                    .background(colors.surfaceElevated)
                    .border(1.dp, colors.borderSubtle, dialogShape)
                    .padding(22.dp)
            ) {
                AnimatedContent(
                    targetState = status,
                    transitionSpec = {
                        val ease = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)
                        (fadeIn(tween(260, easing = ease)) + scaleIn(tween(260, easing = ease), initialScale = 0.94f))
                            .togetherWith(fadeOut(tween(180, easing = ease)) + scaleOut(tween(180, easing = ease), targetScale = 0.94f))
                    },
                    label = "update_dialog_state"
                ) { activeStatus ->
                    when (activeStatus) {
                        is UpdateStatus.Checking -> {
                            CheckingUpdatesContent()
                        }

                        is UpdateStatus.Available -> {
                            UpdateAvailableContent(
                                updateInfo = activeStatus.updateInfo,
                                currentVersion = currentVersion,
                                onUpdateClick = {
                                    ApexAudioFeedback.playClick(context)
                                    onStartUpdateClick(activeStatus.updateInfo)
                                },
                                onDismiss = {
                                    ApexAudioFeedback.playClick(context)
                                    onDismissRequest()
                                }
                            )
                        }

                        is UpdateStatus.Downloading -> {
                            DownloadingUpdateContent(
                                status = activeStatus
                            )
                        }

                        is UpdateStatus.ReadyToInstall -> {
                            ReadyToInstallContent(
                                updateInfo = activeStatus.updateInfo,
                                onInstallClick = {
                                    ApexAudioFeedback.playClick(context)
                                    onInstallClick(activeStatus.apkFile)
                                }
                            )
                        }

                        is UpdateStatus.UpToDate -> {
                            UpToDateContent(
                                version = activeStatus.currentVersion,
                                onDismiss = {
                                    ApexAudioFeedback.playClick(context)
                                    onDismissRequest()
                                }
                            )
                        }

                        is UpdateStatus.Error -> {
                            UpdateErrorContent(
                                message = activeStatus.message,
                                onDismiss = {
                                    ApexAudioFeedback.playClick(context)
                                    onDismissRequest()
                                }
                            )
                        }

                        is UpdateStatus.Idle -> {
                            Spacer(modifier = Modifier.height(1.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckingUpdatesContent() {
    val colors = ApexTheme.colors
    val typography = ApexTheme.typography

    val infiniteTransition = rememberInfiniteTransition(label = "checking_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .size(54.dp)
                .graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                },
            contentAlignment = Alignment.Center
        ) {
            ApexLogoMark(size = 46.dp)
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = "CHECKING FOR UPDATES",
            style = typography.LabelUppercase.copy(
                fontSize = 13.sp,
                color = colors.textPrimary,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp
            )
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Connecting to GitHub Releases...",
            style = typography.BodyText.copy(
                fontSize = 12.sp,
                color = colors.slateMuted
            )
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun UpdateAvailableContent(
    updateInfo: UpdateInfo,
    currentVersion: String,
    onUpdateClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ApexTheme.colors
    val typography = ApexTheme.typography
    val pillShape = remember { RoundedCornerShape(ApexDimens.RadiusPillFull) }
    val cardShape = remember { RoundedCornerShape(ApexDimens.RadiusCardStandard) }
    val buttonShape = remember { RoundedCornerShape(ApexDimens.RadiusButtonTactical) }

    val formattedSize = remember(updateInfo.fileSizeEstimateBytes) {
        if (updateInfo.fileSizeEstimateBytes > 0) {
            String.format(Locale.US, "%.1f MB", updateInfo.fileSizeEstimateBytes / (1024.0 * 1024.0))
        } else {
            "APK Package"
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
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
                        text = "NEW VERSION AVAILABLE",
                        style = typography.LabelMicro.copy(
                            color = colors.electricCyan,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp
                        )
                    )
                    Text(
                        text = updateInfo.versionName,
                        style = typography.Headline.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = colors.textPrimary
                        )
                    )
                }
            }

            // Version diff pill
            Box(
                modifier = Modifier
                    .clip(pillShape)
                    .background(colors.surfaceHigh)
                    .border(1.dp, colors.borderSubtle, pillShape)
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "$currentVersion → ${updateInfo.versionName.removePrefix("v")}",
                    style = typography.LabelMicro.copy(
                        color = colors.slateSubtle,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Release notes card (scrollable preview)
        if (updateInfo.releaseNotes.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 140.dp)
                    .clip(cardShape)
                    .background(colors.surface)
                    .border(1.dp, colors.borderSubtle, cardShape)
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = updateInfo.releaseNotes,
                    style = typography.BodyText.copy(
                        fontSize = 11.5.sp,
                        color = colors.slateSubtle,
                        lineHeight = 16.sp
                    )
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DOWNLOAD SIZE",
                style = typography.LabelMicro.copy(color = colors.slateMuted)
            )
            Text(
                text = formattedSize,
                style = typography.LabelMicro.copy(
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // UPDATE NOW Action Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ApexDimens.HeightTacticalButton)
                .clip(buttonShape)
                .background(colors.textPrimary)
                .tactilePress(pressedScale = 0.96f) { onUpdateClick() },
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(colors.electricCyan)
                )
                Text(
                    text = "DOWNLOAD & UPDATE",
                    style = typography.LabelUppercase.copy(
                        fontSize = 12.sp,
                        color = colors.canvasBackground,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .tactilePress(pressedScale = 0.95f) { onDismiss() }
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "LATER",
                style = typography.LabelMicro.copy(
                    color = colors.slateMuted,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            )
        }
    }
}

@Composable
private fun DownloadingUpdateContent(
    status: UpdateStatus.Downloading
) {
    val colors = ApexTheme.colors
    val typography = ApexTheme.typography
    val pillShape = remember { RoundedCornerShape(ApexDimens.RadiusPillFull) }

    val animatedProgress by animateFloatAsState(
        targetValue = (status.progressPercent / 100f).coerceIn(0f, 1f),
        animationSpec = tween(180),
        label = "download_progress"
    )

    val formattedDownloaded = remember(status.downloadedBytes) {
        String.format(Locale.US, "%.1f", status.downloadedBytes / (1024.0 * 1024.0))
    }
    val formattedTotal = remember(status.totalBytes) {
        if (status.totalBytes > 0) {
            String.format(Locale.US, "%.1f MB", status.totalBytes / (1024.0 * 1024.0))
        } else "-- MB"
    }

    Column(modifier = Modifier.fillMaxWidth()) {
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
                        text = "DOWNLOADING UPDATE",
                        style = typography.LabelMicro.copy(
                            color = colors.electricCyan,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp
                        )
                    )
                    Text(
                        text = status.updateInfo.versionName,
                        style = typography.Headline.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = colors.textPrimary
                        )
                    )
                }
            }

            Text(
                text = "${status.progressPercent}%",
                style = typography.MetricLarge.copy(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = colors.textPrimary,
                    fontFamily = FontFamily.Monospace
                )
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Animated Smooth Progress Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(pillShape)
                .background(colors.surfaceHigh)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress)
                    .clip(pillShape)
                    .background(colors.electricCyan)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "STREAMING APK PACKAGE",
                style = typography.LabelMicro.copy(
                    color = colors.slateMuted,
                    letterSpacing = 0.8.sp
                )
            )
            Text(
                text = "$formattedDownloaded / $formattedTotal",
                style = typography.LabelMicro.copy(
                    color = colors.slateSubtle,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Package will install automatically once download finishes.",
            style = typography.BodyText.copy(
                fontSize = 11.sp,
                color = colors.slateMuted,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ReadyToInstallContent(
    updateInfo: UpdateInfo,
    onInstallClick: () -> Unit
) {
    val colors = ApexTheme.colors
    val typography = ApexTheme.typography
    val buttonShape = remember { RoundedCornerShape(ApexDimens.RadiusButtonTactical) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(colors.electricCyanDim),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(colors.electricCyan)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "DOWNLOAD COMPLETE",
            style = typography.LabelMicro.copy(
                color = colors.electricCyan,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Ready to Install ${updateInfo.versionName}",
            style = typography.Headline.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = colors.textPrimary
            )
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Launching Android package installer...",
            style = typography.BodyText.copy(
                fontSize = 12.sp,
                color = colors.slateMuted,
                textAlign = TextAlign.Center
            )
        )

        Spacer(modifier = Modifier.height(18.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ApexDimens.HeightTacticalButton)
                .clip(buttonShape)
                .background(colors.textPrimary)
                .tactilePress(pressedScale = 0.96f) { onInstallClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "CONTINUE INSTALLATION",
                style = typography.LabelUppercase.copy(
                    fontSize = 12.sp,
                    color = colors.canvasBackground,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            )
        }
    }
}

@Composable
private fun UpToDateContent(
    version: String,
    onDismiss: () -> Unit
) {
    val colors = ApexTheme.colors
    val typography = ApexTheme.typography
    val buttonShape = remember { RoundedCornerShape(ApexDimens.RadiusButtonTactical) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ApexLogoMark(size = 40.dp)
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "UP TO DATE",
            style = typography.LabelMicro.copy(
                color = colors.electricLime,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Apex $version",
            style = typography.Headline.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = colors.textPrimary
            )
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "You are currently running the latest available build.",
            style = typography.BodyText.copy(
                fontSize = 12.sp,
                color = colors.slateMuted,
                textAlign = TextAlign.Center
            )
        )
        Spacer(modifier = Modifier.height(18.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ApexDimens.HeightTacticalButton)
                .clip(buttonShape)
                .background(colors.surfaceHigh)
                .tactilePress(pressedScale = 0.96f) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "DISMISS",
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

@Composable
private fun UpdateErrorContent(
    message: String,
    onDismiss: () -> Unit
) {
    val colors = ApexTheme.colors
    val typography = ApexTheme.typography
    val buttonShape = remember { RoundedCornerShape(ApexDimens.RadiusButtonTactical) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "UPDATE CHECK FAILED",
            style = typography.LabelMicro.copy(
                color = colors.punchyCrimson,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = typography.BodyText.copy(
                fontSize = 12.sp,
                color = colors.slateSubtle,
                textAlign = TextAlign.Center
            )
        )
        Spacer(modifier = Modifier.height(18.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ApexDimens.HeightTacticalButton)
                .clip(buttonShape)
                .background(colors.surfaceHigh)
                .tactilePress(pressedScale = 0.96f) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "CLOSE",
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
