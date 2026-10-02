package com.trc.photobooth.ui.main

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trc.photobooth.theme.BgBase
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
            // Top App Bar / Header with status pill, controls, and telemetry
            Header(
                connectionStatus = connectionStatus,
                fps = fps,
                latencyMs = latencyMs,
                capturesCount = captures.size,
                showGuides = showGuides,
                isFlipped = cameraConfig.flipHorizontal ?: false,
                onToggleGuides = viewModel::toggleGuides,
                onToggleFlip = viewModel::toggleFlip,
                onOpenGallery = viewModel::openGallery,
                onOpenSettings = viewModel::openSettings,
                modifier = Modifier.statusBarsPadding()
            )

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
            }

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
            onClose = viewModel::closeSettings
        )
    }
}
