package com.trc.photobooth.ui.main

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trc.photobooth.data.models.ConnectionStatus
import com.trc.photobooth.theme.AmberGold
import com.trc.photobooth.theme.BgBase
import com.trc.photobooth.theme.BgSurface
import com.trc.photobooth.theme.BgSurfaceElevated
import com.trc.photobooth.theme.BorderMedium
import com.trc.photobooth.theme.BorderSubtle
import com.trc.photobooth.theme.CyberCyan
import com.trc.photobooth.theme.EmeraldGreen
import com.trc.photobooth.theme.NeonPink
import com.trc.photobooth.theme.PurpleNeon
import com.trc.photobooth.theme.TextMain
import com.trc.photobooth.theme.TextMuted
import com.trc.photobooth.theme.TextSubtle
import com.trc.photobooth.ui.components.CaptureControls
import com.trc.photobooth.ui.components.CountdownOverlay
import com.trc.photobooth.ui.components.FilterStrip
import com.trc.photobooth.ui.components.GallerySheet
import com.trc.photobooth.ui.components.Header
import com.trc.photobooth.ui.components.LightboxDialog
import com.trc.photobooth.ui.components.LivePreview
import com.trc.photobooth.ui.components.SettingsDialog
import kotlinx.coroutines.flow.collectLatest

@Composable
fun MainScreen(
    onNavigateToBooth: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel(),
) {
    val context = LocalContext.current

    // Observe ViewModel state flows
    val connectionStatus by viewModel.status.collectAsStateWithLifecycle()
    val lastFrame by viewModel.lastFrame.collectAsStateWithLifecycle()
    val fps by viewModel.fps.collectAsStateWithLifecycle()
    val latencyMs by viewModel.latencyMs.collectAsStateWithLifecycle()
    val cameraConfig by viewModel.cameraConfig.collectAsStateWithLifecycle()
    val systemStats by viewModel.systemStats.collectAsStateWithLifecycle()
    val countdown by viewModel.countdown.collectAsStateWithLifecycle()
    val gifRecording by viewModel.gifRecording.collectAsStateWithLifecycle()

    val activeFilter by viewModel.activeFilter.collectAsStateWithLifecycle()
    val showGuides by viewModel.showGuides.collectAsStateWithLifecycle()
    val captureMode by viewModel.captureMode.collectAsStateWithLifecycle()
    val countdownSetting by viewModel.countdownSetting.collectAsStateWithLifecycle()
    val gifFrames by viewModel.gifFrames.collectAsStateWithLifecycle()
    val gifIntervalMs by viewModel.gifIntervalMs.collectAsStateWithLifecycle()

    val captures by viewModel.captures.collectAsStateWithLifecycle()
    val isLoadingCaptures by viewModel.isLoadingCaptures.collectAsStateWithLifecycle()
    val hostAddress by viewModel.hostAddress.collectAsStateWithLifecycle()
    val printerName by viewModel.printerName.collectAsStateWithLifecycle()
    val printerColorMode by viewModel.printerColorMode.collectAsStateWithLifecycle()
    val printerCopies by viewModel.printerCopies.collectAsStateWithLifecycle()
    val isStreamPaused by viewModel.isStreamPaused.collectAsStateWithLifecycle()
    val actionLogs by viewModel.actionLogs.collectAsStateWithLifecycle()

    val isGalleryOpen by viewModel.isGalleryOpen.collectAsStateWithLifecycle()
    val isSettingsOpen by viewModel.isSettingsOpen.collectAsStateWithLifecycle()
    val lightboxCapture by viewModel.lightboxCapture.collectAsStateWithLifecycle()

    // Handle toast messages
    LaunchedEffect(Unit) {
        viewModel.toastMessage.collectLatest { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    val resString = "${cameraConfig.width ?: 640}x${cameraConfig.height ?: 480}"

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BgBase)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top App Bar / Island Header with status pill, controls, and telemetry
            Header(
                connectionStatus = connectionStatus,
                fps = fps,
                latencyMs = latencyMs,
                capturesCount = captures.size,
                showGuides = showGuides,
                isFlipped = cameraConfig.flipHorizontal ?: false,
                isStreamPaused = isStreamPaused,
                onToggleStreamPause = viewModel::toggleStreamPause,
                onToggleGuides = viewModel::toggleGuides,
                onToggleFlip = viewModel::toggleFlip,
                onOpenGallery = viewModel::openGallery,
                onOpenSettings = viewModel::openSettings,
                onNavigateToBooth = onNavigateToBooth,
                modifier = Modifier.statusBarsPadding()
            )

            // Reconnection Banner
            AnimatedVisibility(
                visible = connectionStatus != ConnectionStatus.CONNECTED,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFEF3C7))
                        .border(width = 1.dp, color = AmberGold.copy(alpha = 0.35f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = if (connectionStatus == ConnectionStatus.CONNECTING) Icons.Default.Wifi else Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = AmberGold,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = if (connectionStatus == ConnectionStatus.CONNECTING)
                                "Connecting to Pi ($hostAddress)..."
                            else
                                "Disconnected from Pi ($hostAddress)",
                            color = AmberGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(AmberGold.copy(alpha = 0.2f))
                            .border(1.dp, AmberGold.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                            .clickable { viewModel.repository.reconnect() }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "Retry",
                            color = AmberGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            // Middle Viewport Container: Live Viewfinder + Guides + Overlays
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                LivePreview(
                    lastFrameBitmap = lastFrame,
                    activeFilter = activeFilter,
                    showGuides = showGuides,
                    fps = fps,
                    resolution = resString,
                    connectionStatus = connectionStatus,
                    gifRecording = gifRecording,
                    modifier = Modifier.fillMaxSize()
                )

                // Countdown and Flash Overlay
                CountdownOverlay(
                    countdown = countdown,
                    flashEvent = viewModel.flashEvent,
                    modifier = Modifier.fillMaxSize()
                )

                // Camera Turned Off Overlay
                if (isStreamPaused && connectionStatus == ConnectionStatus.CONNECTED) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x99FFFFFF))
                            .clickable(onClick = viewModel::toggleStreamPause),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(BgSurface)
                                .border(1.dp, BorderMedium, RoundedCornerShape(18.dp))
                                .padding(horizontal = 24.dp, vertical = 20.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(AmberGold.copy(alpha = 0.15f))
                                    .border(1.5.dp, AmberGold, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VideocamOff,
                                    contentDescription = null,
                                    tint = AmberGold,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Text(
                                text = "CAMERA TURNED OFF",
                                color = TextMain,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            )
                            Text(
                                text = "Camera feed is currently turned off.\nTap button below or header to resume.",
                                color = TextMuted,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 16.sp,
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(EmeraldGreen)
                                    .clickable(onClick = viewModel::toggleStreamPause)
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Videocam,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "TURN CAMERA ON",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Action & Booth Logs Console
            ActionLogsCard(
                logs = actionLogs,
                onClear = viewModel::clearActionLogs,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )

            // Studio Presets Carousel
            FilterStrip(
                activeFilter = activeFilter,
                onSelectFilter = viewModel::selectFilter,
            )

            // Shutter Button & Capture Configuration Dock
            CaptureControls(
                captureMode = captureMode,
                countdownSec = countdownSetting,
                isCountingDown = countdown != null,
                isRecordingGif = gifRecording != null,
                connectionStatus = connectionStatus,
                recentCapture = captures.firstOrNull(),
                capturesCount = captures.size,
                hostAddress = hostAddress,
                gifFrames = gifFrames,
                gifIntervalMs = gifIntervalMs,
                onSelectMode = viewModel::setCaptureMode,
                onSelectCountdown = viewModel::setCountdownSetting,
                onChangeGifFrames = viewModel::setGifFrames,
                onChangeGifInterval = viewModel::setGifInterval,
                onShutterClick = viewModel::triggerShutter,
                onOpenGallery = viewModel::openGallery,
                modifier = Modifier.navigationBarsPadding()
            )
        }

        // Overlay Sheets & Dialogs
        GallerySheet(
            isOpen = isGalleryOpen,
            captures = captures,
            isLoading = isLoadingCaptures,
            hostAddress = hostAddress,
            onSelectCapture = viewModel::openLightbox,
            onRefresh = viewModel.repository::fetchCaptures,
            onClose = viewModel::closeGallery
        )

        LightboxDialog(
            capture = lightboxCapture,
            hostAddress = hostAddress,
            onDelete = viewModel::deleteCapture,
            onClose = viewModel::closeLightbox
        )

        SettingsDialog(
            isOpen = isSettingsOpen,
            currentHost = hostAddress,
            connectionStatus = connectionStatus,
            cameraConfig = cameraConfig,
            systemStats = systemStats,
            onSaveHost = viewModel::setHost,
            onStartNsdSearch = viewModel::startNsdSearch,
            onToggleFlip = viewModel::toggleFlip,
            onToggleSwapRb = viewModel::toggleSwapRb,
            onSetFps = viewModel::setFps,
            onSetQuality = viewModel::setQuality,
            onSetResolution = viewModel::setResolution,
            onRefreshStats = viewModel::refreshStats,
            onSetCameraDevice = viewModel::setCameraDevice,
            onRefreshDevices = viewModel::refreshDevices,
            currentPrinterName = printerName,
            currentColorMode = printerColorMode,
            currentCopies = printerCopies,
            onSavePrinterSettings = viewModel::setPrinterSettings,
            onClose = viewModel::closeSettings
        )
    }
}

@Composable
private fun ActionLogsCard(
    logs: List<com.trc.photobooth.data.ActionLog>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(false) }
    val latestLog = logs.lastOrNull()
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(BgSurface)
            .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = null,
                    tint = CyberCyan,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "STATUS LOGS",
                    color = TextMain,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
                if (latestLog != null && !isExpanded) {
                    Text(
                        text = "• ${latestLog.message}",
                        color = TextMuted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (logs.isNotEmpty()) {
                    Text(
                        text = "Clear",
                        color = TextSubtle,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable(onClick = onClear)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(BgSurfaceElevated)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
                        .clickable { isExpanded = !isExpanded }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isExpanded) "Hide" else "Logs (${logs.size})",
                        color = CyberCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }

        // Expanded log scroll list
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                Spacer(modifier = Modifier.height(6.dp))
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(BgSurfaceElevated)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (logs.isEmpty()) {
                        item {
                            Text(
                                text = "No recent action logs.",
                                color = TextSubtle,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(4.dp)
                            )
                        }
                    } else {
                        items(logs, key = { it.id }) { log ->
                            LogEntryRow(log)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogEntryRow(log: com.trc.photobooth.data.ActionLog) {
    val tagColor = when (log.type) {
        com.trc.photobooth.data.LogType.CAPTURE -> EmeraldGreen
        com.trc.photobooth.data.LogType.CAMERA -> AmberGold
        com.trc.photobooth.data.LogType.COMPLETE -> PurpleNeon
        com.trc.photobooth.data.LogType.ERROR -> NeonPink
        com.trc.photobooth.data.LogType.STATUS -> CyberCyan
        else -> TextMuted
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = log.timestamp,
            color = TextSubtle,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = "[${log.type.name}]",
            color = tagColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = log.message,
            color = TextMain,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
    }
}
